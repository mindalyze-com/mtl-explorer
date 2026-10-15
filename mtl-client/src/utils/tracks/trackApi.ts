import {
  GpsTrackFromJSON,
  TracksControllerApi,
  type GpsTrack,
  type TracksSimplifiedResponse,
} from 'x8ing-mtl-api-typescript-fetch';
import type { FilterParamsRequest } from '@/components/filter/FilterService';
import { loadActiveFilterRequest as loadStoredActiveFilterRequest } from '@/stores/filterStore';
import { isAbortLikeError } from '@/utils/errors';
import { getApiConfiguration } from '@/utils/openApiClient';
import { extractCoordinates } from '@/utils/lineStringDeserializer';
import { startStartupTimer, startupLog } from '@/utils/startupDiagnostics';
import { logSanitizedError } from '@/utils/safeLogging';
import { STARTUP_REQUEST_TIMEOUT_MS } from '@/utils/startupTiming';
import { DETAIL_TRACK_PRECISION } from '@/utils/tracks/trackConstants';
import type {
  ActiveTrackFilterRequest,
  TrackBatchPayload,
  TrackFilterResultWithRequest,
} from '@/utils/tracks/trackTypes';

const TRACKS_SIMPLIFIED_MODE_FULL = 'full';
const TRACKS_SIMPLIFIED_MODE_IDS = 'ids';
const TRACKS_SIMPLIFIED_MODE_ARCHIVE_IDS = 'archive-ids';

/** Complete archive membership, independent of saved filters and view scopes. */
export async function fetchArchiveTrackIds(): Promise<Set<number>> {
  const data = await getTracksApi().getTracksSimplified1(
    { mode: TRACKS_SIMPLIFIED_MODE_ARCHIVE_IDS },
    { cache: 'no-cache', signal: AbortSignal.timeout(STARTUP_REQUEST_TIMEOUT_MS) }
  );
  // Older servers treat unknown modes as full requests. Never use such a
  // filtered response (or a malformed snapshot) to delete persistent records.
  const ids = Object.keys(data.trackVersions ?? {}).map(Number);
  if (
    !data.trackVersions ||
    data.filteredTracks != null ||
    data.numberOfFilteredMatchedTracks !== ids.length ||
    data.standardFilterCount !== ids.length ||
    ids.some((id) => !Number.isSafeInteger(id) || id <= 0)
  ) {
    throw new Error('Complete track archive membership is unavailable.');
  }
  return new Set(ids);
}

function getTracksApi() {
  return new TracksControllerApi(getApiConfiguration());
}

export function normalizeTrackDates(rawTrack: GpsTrack): GpsTrack {
  // Raw track endpoints are used for geometry because the server emits
  // LineString as coordinate arrays, but model fields should still go through
  // the generated parser so OpenAPI date-time fields become Date objects.
  const track = GpsTrackFromJSON(rawTrack);
  // Temporary JTS Point parser workaround: the server sends numeric GeoJSON
  // coordinates, while the generated parser expects Coordinate objects. Keep
  // event locations intact until these endpoints expose a GeoJSON schema.
  track.gpsTracksData?.forEach((data, dataIndex) => {
    data.gpsTrackEvents?.forEach((event, eventIndex) => {
      const rawEvent = rawTrack.gpsTracksData?.[dataIndex]?.gpsTrackEvents?.[eventIndex];
      event.startPointLongLat = rawEvent?.startPointLongLat;
      event.endPointLongLat = rawEvent?.endPointLongLat;
    });
  });
  return track;
}

export async function loadActiveFilterRequest(): Promise<ActiveTrackFilterRequest> {
  return loadStoredActiveFilterRequest();
}

function addNumberEntries(target: Map<number, number>, entries: Record<string, number> | undefined): void {
  if (!entries) return;
  for (const [key, value] of Object.entries(entries)) {
    const id = Number(key);
    const version = Number(value);
    if (!Number.isFinite(id) || !Number.isFinite(version)) continue;
    target.set(id, version);
  }
}

function addStringEntries(target: Map<number, string>, entries: Record<string, string> | undefined): void {
  if (!entries) return;
  for (const [key, value] of Object.entries(entries)) {
    const id = Number(key);
    if (!Number.isFinite(id) || typeof value !== 'string') continue;
    target.set(id, value);
  }
}

function groupOrderFromAssignments(filterGroups: Map<number, string>): string[] {
  return Array.from(new Set(filterGroups.values()));
}

export async function fetchFilteredTrackIds(signal?: AbortSignal): Promise<TrackFilterResultWithRequest> {
  try {
    const activeFilterRequest = await loadActiveFilterRequest();
    const { filterName, filterParams } = activeFilterRequest;
    const data = await getTracksApi().getTracksSimplified1(
      {
        mode: TRACKS_SIMPLIFIED_MODE_IDS,
        filterName: filterName || undefined,
        filterParamsRequest: filterParams,
      },
      { signal }
    );

    const trackVersions = new Map<number, number>();
    addNumberEntries(trackVersions, data.trackVersions);

    const filterGroups = new Map<number, string>();
    addStringEntries(filterGroups, data.filterGroups);

    return {
      trackVersions,
      filterGroups,
      legendGroupOrder: groupOrderFromAssignments(filterGroups),
      standardFilterCount: data.standardFilterCount ?? trackVersions.size,
      activeFilterRequest,
    };
  } catch (error: unknown) {
    if (isAbortLikeError(error, signal)) throw error;
    logSanitizedError('Error fetching filtered track IDs:', error, {
      topic1: 'tracks',
      topic2: 'trackApi',
      topic3: 'fetchFilteredTrackIds',
    });
    throw error;
  }
}

export async function fetchTrackBatch(args: {
  precision: number;
  trackIds: number[];
  filterRequest?: ActiveTrackFilterRequest;
  signal?: AbortSignal;
}): Promise<TrackBatchPayload> {
  if (args.trackIds.length === 0) {
    return {
      tracksById: new Map(),
      geometryByTrackId: new Map(),
      filterGroups: new Map(),
      standardFilterCount: 0,
    };
  }

  const timer = startStartupTimer('tracks', 'Fetching track batch', {
    precision: args.precision,
    trackCount: args.trackIds.length,
  });
  try {
    const { filterName, filterParams } = args.filterRequest ?? (await loadActiveFilterRequest());
    startupLog('tracks', 'Resolved active filter for track batch', {
      precision: args.precision,
      filterName,
      requestedTrackCount: args.trackIds.length,
    });

    const requestBody: FilterParamsRequest = {
      ...(filterParams ?? {}),
      trackIds: args.trackIds,
    };

    const rawResponse = await getTracksApi().getTracksSimplified1Raw(
      {
        precisionInMeter: args.precision,
        filterName: filterName || undefined,
        mode: TRACKS_SIMPLIFIED_MODE_FULL,
        filterParamsRequest: requestBody,
      },
      { signal: args.signal }
    );
    const envelope = (await rawResponse.raw.json()) as TracksSimplifiedResponse;
    const data = envelope.filteredTracks ?? [];
    const tracksById = new Map<number, GpsTrack>();
    const geometryByTrackId = new Map<number, number[][]>();
    const filterGroups = new Map<number, string>();

    for (const trackResponse of data) {
      const rawTrack = trackResponse.gpsTrack;
      if (!rawTrack) continue;
      const track = normalizeTrackDates(rawTrack);
      if (track.id == null) continue;

      tracksById.set(track.id, track);
      geometryByTrackId.set(track.id, extractCoordinates(rawTrack.gpsTracksData));

      const group = trackResponse.filterMapping?.group;
      if (group != null) {
        filterGroups.set(track.id, group);
      }
    }

    timer.success('Track batch fetched', {
      precision: args.precision,
      trackCount: tracksById.size,
      requestedTrackCount: args.trackIds.length,
      standardFilterCount: envelope.standardFilterCount ?? 0,
    });

    return {
      tracksById,
      geometryByTrackId,
      filterGroups,
      standardFilterCount: envelope.standardFilterCount ?? tracksById.size,
    };
  } catch (error: unknown) {
    if (isAbortLikeError(error, args.signal)) throw error;
    logSanitizedError('Error fetching track batch:', error, {
      topic1: 'tracks',
      topic2: 'trackApi',
      topic3: 'fetchTrackBatch',
    });
    throw error;
  }
}

export async function fetchDetailTrack(args: {
  trackId: number;
  precision: number;
  signal?: AbortSignal;
}): Promise<{ coordinates: number[][]; gpsTrack: GpsTrack }> {
  if (args.precision !== DETAIL_TRACK_PRECISION) {
    throw new Error(`Precision ${args.precision}m is not an allowed per-track detail precision`);
  }

  try {
    const rawResponse = await getTracksApi().getSingleTrackRaw(
      {
        gpsTrackId: args.trackId,
        precisionInMeter: args.precision,
      },
      // Mutable summaries can change through another endpoint, such as an energy save.
      { signal: args.signal, cache: 'no-cache' }
    );
    const rawTrack = (await rawResponse.raw.json()) as GpsTrack;
    return {
      gpsTrack: normalizeTrackDates(rawTrack),
      coordinates: extractCoordinates(rawTrack.gpsTracksData),
    };
  } catch (error: unknown) {
    if (isAbortLikeError(error, args.signal)) throw error;
    logSanitizedError('Error fetching detail track:', error, {
      topic1: 'tracks',
      topic2: 'trackApi',
      topic3: 'fetchDetailTrack',
    });
    throw error;
  }
}
