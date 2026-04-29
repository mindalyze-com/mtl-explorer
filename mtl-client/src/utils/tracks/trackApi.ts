import { TracksControllerApi, type GpsTrack, type TracksSimplifiedResponse } from 'x8ing-mtl-api-typescript-fetch';
import { FilterService, type FilterParamsRequest } from '@/components/filter/FilterService';
import { getApiConfiguration } from '@/utils/openApiClient';
import { extractCoordinates } from '@/utils/lineStringDeserializer';
import { describeError, startStartupTimer, startupLog } from '@/utils/startupDiagnostics';
import { DETAIL_TRACK_PRECISION } from '@/utils/tracks/trackConstants';
import type {
  ActiveTrackFilterRequest,
  TrackBatchPayload,
  TrackFilterResultWithRequest,
} from '@/utils/tracks/trackTypes';

const TRACKS_SIMPLIFIED_MODE_FULL = 'full';
const TRACKS_SIMPLIFIED_MODE_IDS = 'ids';

function getTracksApi() {
  return new TracksControllerApi(getApiConfiguration());
}

function parseTrack(rawTrack: GpsTrack): GpsTrack {
  return {
    ...rawTrack,
    startDate: rawTrack.startDate ? new Date(rawTrack.startDate) : undefined,
    endDate: rawTrack.endDate ? new Date(rawTrack.endDate) : undefined,
    createDate: rawTrack.createDate ? new Date(rawTrack.createDate) : undefined,
  };
}

export async function loadActiveFilterRequest(): Promise<ActiveTrackFilterRequest> {
  const clientFilterConfig = await FilterService.loadClientFilterConfig();
  return {
    filterName: clientFilterConfig.filterInfo?.filterConfig?.filterName ?? '',
    filterParams: clientFilterConfig.filterParams,
  };
}

function isAbortError(error: unknown, signal?: AbortSignal): boolean {
  return (
    signal?.aborted === true ||
    (error instanceof DOMException && error.name === 'AbortError') ||
    (error instanceof Error && error.name === 'AbortError')
  );
}

function throwOriginalError(error: unknown): never {
  if (error instanceof Error) throw error;
  throw new Error(String(error));
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
      standardFilterCount: data.standardFilterCount ?? trackVersions.size,
      activeFilterRequest,
    };
  } catch (error: unknown) {
    if (isAbortError(error, signal)) throw error;
    console.error('Error fetching filtered track IDs:', error);
    throwOriginalError(error);
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
      const track = parseTrack(rawTrack);
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
    if (isAbortError(error, args.signal)) throw error;
    timer.error('Track batch fetch failed', describeError(error));
    console.error('Error fetching track batch:', error);
    throwOriginalError(error);
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
      { signal: args.signal }
    );
    const rawTrack = (await rawResponse.raw.json()) as GpsTrack;
    return {
      gpsTrack: parseTrack(rawTrack),
      coordinates: extractCoordinates(rawTrack.gpsTracksData),
    };
  } catch (error: unknown) {
    if (isAbortError(error, args.signal)) throw error;
    console.error('Error fetching detail track:', error);
    throwOriginalError(error);
  }
}
