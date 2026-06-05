import axios from 'axios';
import {
  TracksControllerApi,
  FilterControllerApi,
  ConfigControllerApi,
  EnergyControllerApi,
} from 'x8ing-mtl-api-typescript-fetch';
import {
  CrossingPointsResponseDtoFromJSONTyped,
  QueryResultEntryFromJSON,
  type CrossingPointsResponseDto,
  QueryResultFromJSONTyped,
  type QueryResult,
  ConfigEntityFromJSONTyped,
  type ConfigEntity,
  type FilterInfo,
  type GpsTrack,
  type GpsTrackDataPoint,
  type GpsTrackDataPointDto,
  type RelatedTracks,
  type EnergyWhatIfResponse,
  type GpsTrackStatistics,
  type StatisticsExclusionUpdateRequest,
  type ActivityTypeUpdateRequest,
  type StatisticsOverviewResponseDto,
  type QueryResultEntry,
  type TriggerPoint,
} from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';

import { type FilterParamsRequest, FilterService } from '@/components/filter/FilterService';
import type { FilterResult } from '@/types/filter';
import { useFilterStore, type ActiveFilterRequest } from '@/stores/filterStore';
import { apiClient } from '@/utils/apiClient';
import { getApiConfiguration } from '@/utils/openApiClient';
import { logSanitizedError } from '@/utils/safeLogging';
import {
  chartSeriesToTrackChartSeries,
  fetchChartSeries,
  XMode,
  type TrackChartSeries,
} from '@/utils/chartSeriesAdapter';
import {
  clampTrackDetailsChartPointCount,
  TRACK_DETAILS_CHART_POINTS_DEFAULT,
} from '@/utils/trackDetailsChartPointSettings';
export type { ChartPoint, TrackChartSeries } from '@/utils/chartSeriesAdapter';

// ─── Back-compat re-exports ─────────────────────────────────────────────────
// Admin / diagnostic API surface lives in serverAdminApi.ts. Re-exported here
// so existing import sites keep working — new code should import from there
// directly.
export {
  getServerBuildInfo,
  getDemoStatus,
  triggerGarminExport,
  getGarminToolStatus,
  installGcexport,
  installFitExport,
  getGpxUploadStatus,
  uploadGpxFile,
  getServerLog,
  getIndexerStatus,
  getJobStatus,
  getAdminOperationalTasks,
  checkServerAuth,
} from '@/utils/serverAdminApi';
export type {
  BuildInfo,
  DemoStatus,
  GarminToolStatus,
  GpxUploadStatus,
  GpxUploadResult,
  IndexSummaryDto,
  JobSummaryDto,
  IndexSummary,
  JobSummary,
  AdminOperationalTask,
  AuthCheckResult,
} from '@/utils/serverAdminApi';

export const CONFIG_DOMAIN1_CLIENT = 'CLIENT';
const TRACK_SOURCE_FILENAME_FALLBACK = 'track-source';
const GPX_FILE_EXTENSION = '.gpx';
const INVALID_FILENAME_CHARS = /[\\/:*?"<>|]+/g;
const WHITESPACE_CHARS = /\s+/g;

function isAbortLikeError(error: unknown, signal?: AbortSignal): boolean {
  if (signal?.aborted) return true;
  if (error == null || typeof error !== 'object') return false;

  const candidate = error as { name?: unknown; code?: unknown; cause?: unknown };
  if (
    candidate.name === 'AbortError' ||
    candidate.name === 'CanceledError' ||
    candidate.code === 'ERR_CANCELED'
  ) {
    return true;
  }

  return candidate.cause !== error && isAbortLikeError(candidate.cause);
}

/**
 * Flatten a FilterParamsRequest into a simple {key: value} map.
 * Used for server endpoints that still accept Map<String, String>.
 */
function flattenFilterParams(params: FilterParamsRequest | undefined): Record<string, string> {
  if (!params) return {};
  const flat: Record<string, string> = {};
  if (params.stringParams) Object.assign(flat, params.stringParams);
  if (params.dateTimeParams) Object.assign(flat, params.dateTimeParams);
  return flat;
}

async function loadActiveFilterRequest(): Promise<ActiveFilterRequest> {
  try {
    const filterStore = useFilterStore();
    return await filterStore.getActiveFilterRequest();
  } catch {
    const clientFilterConfig = await FilterService.loadClientFilterConfig();
    return {
      filterName: clientFilterConfig.filterInfo?.filterConfig?.filterName ?? '',
      filterParams: clientFilterConfig.filterParams,
    };
  }
}

async function resolveActiveFilterRequest(filterRequest?: ActiveFilterRequest): Promise<ActiveFilterRequest> {
  return filterRequest ?? (await loadActiveFilterRequest());
}

// getApiConfiguration moved to @/utils/openApiClient (shared with serverAdminApi).

function getTracksApi() {
  return new TracksControllerApi(getApiConfiguration());
}

function getFilterApi() {
  return new FilterControllerApi(getApiConfiguration());
}

function getConfigApi() {
  return new ConfigControllerApi(getApiConfiguration());
}

function getEnergyApi() {
  return new EnergyControllerApi(getApiConfiguration());
}

export async function calculateEnergyWhatIf(
  gpsTrackId: number | string,
  riderWeightKg?: number
): Promise<EnergyWhatIfResponse> {
  return await getEnergyApi().calculateEnergyWhatIf({
    gpsTrackId: Number(gpsTrackId),
    riderWeightKg,
  });
}

export async function saveTrackEnergyRiderWeight(
  gpsTrackId: number | string,
  riderWeightKg: number
): Promise<GpsTrack> {
  return await getEnergyApi().saveTrackRiderWeight({
    gpsTrackId: Number(gpsTrackId),
    riderWeightKg,
  });
}

export async function downloadTrackSourceFile(gpsTrackId: number | string, fallbackName?: string): Promise<void> {
  const response = await getTracksApi().downloadTrackSourceFileRaw({ gpsTrackId: Number(gpsTrackId) });
  await downloadRawResponse(response.raw, sanitizeDownloadFileName(fallbackName, TRACK_SOURCE_FILENAME_FALLBACK));
}

export async function downloadTrackGpx(gpsTrackId: number | string, fallbackName?: string): Promise<void> {
  const response = await getTracksApi().downloadTrackGpxRaw({ gpsTrackId: Number(gpsTrackId) });
  await downloadRawResponse(response.raw, makeGpxFileName(fallbackName));
}

export async function updateTrackStatisticsExclusion(
  gpsTrackId: number | string,
  request: StatisticsExclusionUpdateRequest
): Promise<GpsTrack> {
  return await getTracksApi().updateTrackStatisticsExclusion({
    gpsTrackId: Number(gpsTrackId),
    statisticsExclusionUpdateRequest: request,
  });
}

export async function updateTrackActivityType(
  gpsTrackId: number | string,
  activityType: ActivityTypeUpdateRequest['activityType']
): Promise<GpsTrack> {
  return await getTracksApi().updateTrackActivityType({
    gpsTrackId: Number(gpsTrackId),
    activityTypeUpdateRequest: { activityType },
  });
}

export async function saveTrack(gpsTrack: GpsTrack): Promise<GpsTrack> {
  return await getTracksApi().saveTrack({ gpsTrack });
}

async function downloadRawResponse(response: Response, fallbackName: string): Promise<void> {
  const blob = await response.blob();
  const fileName = fileNameFromContentDisposition(response.headers.get('content-disposition')) ?? fallbackName;
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName;
  document.body.appendChild(a);
  a.click();
  a.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 0);
}

function fileNameFromContentDisposition(header: string | null): string | null {
  if (!header) return null;
  const encodedMatch = header.match(/filename\*=UTF-8''([^;]+)/i);
  if (encodedMatch?.[1]) {
    try {
      return decodeURIComponent(encodedMatch[1]);
    } catch {
      return encodedMatch[1];
    }
  }
  const quotedMatch = header.match(/filename="([^"]+)"/i);
  if (quotedMatch?.[1]) return quotedMatch[1];
  const plainMatch = header.match(/filename=([^;]+)/i);
  return plainMatch?.[1]?.trim() || null;
}

function sanitizeDownloadFileName(fileName: string | null | undefined, fallbackName: string): string {
  const baseName = (fileName ?? '').trim().replace(INVALID_FILENAME_CHARS, '-').replace(WHITESPACE_CHARS, ' ');
  return baseName || fallbackName;
}

function makeGpxFileName(fileName: string | null | undefined): string {
  const safeFileName = sanitizeDownloadFileName(fileName, TRACK_SOURCE_FILENAME_FALLBACK);
  if (safeFileName.toLowerCase().endsWith(GPX_FILE_EXTENSION)) return safeFileName;
  const dot = safeFileName.lastIndexOf('.');
  const baseName = dot > 0 ? safeFileName.slice(0, dot) : safeFileName;
  return `${baseName}${GPX_FILE_EXTENSION}`;
}

export async function getRelatedTracks(gpsTrackId: number | string): Promise<RelatedTracks> {
  try {
    const { filterName } = await loadActiveFilterRequest();

    const api = getTracksApi();
    const result = await api.getRelatedTracks({
      gpsTrackId: Number(gpsTrackId),
      filterName: filterName,
    });

    return result;
  } catch (error: unknown) {
    logSanitizedError('Error getting related tracks:', error);
    throw new Error(String(error));
  }
}

/**
 * Measure / race / crossing-points analysis — **ACCURACY-CRITICAL**.
 *
 * Server-side this endpoint always loads the canonical RAW_OUTLIER_CLEANED
 * variant (full GPS density, 1 Hz sampling preserved on straight sections),
 * which is required for accurate crossing time/speed calculations. See
 * `TrackTimeBetweenTwoPoints.processingCrossingForOneTrack` on the server.
 *
 * Never substitute `fetchTrackDetails()` here — that path is
 * chart-series buckets and is intended only for display on the Track Details
 * screen.
 */
export async function fetchTrackDetailsForCrossingPoints(
  triggerPoints: TriggerPoint[],
  radius: number,
  signal?: AbortSignal
): Promise<CrossingPointsResponseDto> {
  try {
    const { filterName, filterParams } = await loadActiveFilterRequest();

    const request = {
      triggerPoints: triggerPoints,
      radius: radius,
      filter: {
        filterName: filterName,
        params: flattenFilterParams(filterParams),
      },
    };

    // POST with complex body; axios lets us pass the abort signal through the
    // shared API client while still using the generated DTO converter below.
    const response = await apiClient.post(`api/tracks/get-track-details-for-tracks-crossing-points`, request, {
      signal,
    });

    return CrossingPointsResponseDtoFromJSONTyped(response.data, false);
  } catch (error: unknown) {
    if (axios.isCancel(error)) throw error;
    logSanitizedError('Error getting track details for crossing points:', error);
    throw new Error(String(error));
  }
}

type RawDatedPoint<T> = Omit<Partial<T>, 'createDate' | 'pointTimestamp'> & {
  createDate?: string | Date;
  pointTimestamp?: string | Date;
};

export async function fetchTrackIdsWithinDistanceOfPoint(
  longitude: number,
  latitude: number,
  distanceInMeter: number,
  signal?: AbortSignal
): Promise<number[]> {
  try {
    const { filterName, filterParams } = await loadActiveFilterRequest();

    const response = await apiClient.post(
      `api/tracks/get-track-ids-within-distance-of-point?filterName=${filterName}&longitude=${longitude}&latitude=${latitude}&distanceInMeter=${distanceInMeter}`,
      flattenFilterParams(filterParams),
      { signal }
    );

    return response.data as number[];
  } catch (error: unknown) {
    if (axios.isCancel(error)) throw error;
    logSanitizedError('Error getting track IDs within distance:', error);
    throw new Error(String(error));
  }
}

export async function fetchStatistics(
  grouping: string,
  filterRequest?: ActiveFilterRequest
): Promise<GpsTrackStatistics[]> {
  try {
    const { filterName, filterParams } = await resolveActiveFilterRequest(filterRequest);

    const response = await apiClient.post(
      `api/tracks/get-track-statistics?groupByDateFormat=${grouping}&filterName=${filterName}`,
      flattenFilterParams(filterParams)
    );

    return response.data;
  } catch (error: unknown) {
    logSanitizedError('Error fetching statistics:', error);
    throw new Error(String(error));
  }
}

export async function fetchStatisticsOverview(
  signal?: AbortSignal,
  filterRequest?: ActiveFilterRequest
): Promise<StatisticsOverviewResponseDto> {
  try {
    const { filterName, filterParams } = await resolveActiveFilterRequest(filterRequest);

    return await getTracksApi().getTrackOverview(
      {
        filterName: filterName || undefined,
        requestBody: flattenFilterParams(filterParams),
      },
      { signal }
    );
  } catch (error: unknown) {
    if (isAbortLikeError(error, signal)) throw error;
    logSanitizedError('Error fetching statistics overview:', error);
    throw error instanceof Error ? error : new Error(String(error));
  }
}

/**
 * Convert raw JSON data-point objects into generated data-point shapes.
 *
 * Some legacy endpoints still expose JTS Point fields, whose generated PointFromJSON
 * deserializer does not preserve numeric coordinate arrays. Fetching with axios keeps
 * those coordinate arrays intact; DTO endpoints already expose the same numeric shape.
 * The only shared conversion needed here is date strings to Date objects.
 */
function convertDataPointDates<T extends { createDate?: Date; pointTimestamp?: Date }>(raw: RawDatedPoint<T>[]): T[] {
  return raw.map((d) => ({
    ...d,
    pointTimestamp: d.pointTimestamp ? new Date(d.pointTimestamp) : undefined,
    createDate: d.createDate ? new Date(d.createDate) : undefined,
  })) as T[];
}

export async function fetchTrackSubTrackDetails(
  trackDataPointFrom: number,
  trackDataPointTo: number
): Promise<GpsTrackDataPointDto[]> {
  try {
    const response = await apiClient.get(
      `api/tracks/details/get-sub-track?trackDataPointFrom=${trackDataPointFrom}&trackDataPointTo=${trackDataPointTo}`
    );
    return convertDataPointDates<GpsTrackDataPointDto>(response.data);
  } catch (error: unknown) {
    logSanitizedError('Error fetching track sub track details:', error);
    throw new Error(String(error));
  }
}

/**
 * Fetch chart-friendly per-bucket data for the Track Details charts.
 *
 * Replaces the legacy SIMPLIFIED_FIXED_POINTS variant — see
 * `mtl-server/doc/issues/canonical_metric_lod_architecture.md`. The server
 * now computes equal-width buckets on demand from the canonical
 * RAW_OUTLIER_CLEANED stream and returns per-metric statistics per bucket.
 * The buckets are projected into chart metadata plus a flat `ChartPoint[]`
 * so the existing chart configs (`trackGraphConfigs.ts`) can render them.
 *
 * Authoritative track-level totals (energyNetTotalWh, powerWattsAvg/Max,
 * trackLengthInMeter, ascent/descent) are still read straight off the
 * `GpsTrack` entity.
 *
 * ⚠️ DO NOT USE FOR ACCURACY-CRITICAL FEATURES (measure, race, crossings,
 *    speed/time analysis, any per-point metric users will rely on as truth).
 *
 *    For those, use `fetchTrackDetailsForCrossingPoints()` which hits a
 *    dedicated server endpoint that always returns the canonical
 *    RAW_OUTLIER_CLEANED variant (full GPS density, 1 Hz sampling preserved
 *    on straight sections). See TrackTimeBetweenTwoPoints.java on the server.
 *
 * ⚠️ DO NOT USE FOR THE MAP TRACK-POINT POPUP either — that path renders
 *    SIMPLIFIED_SHAPE coordinates and tags each with its array index as
 *    `pointIndex`. Use `fetchTrackPointsForRenderedShape()` instead.
 */
export async function fetchTrackDetails(
  gpsTrackId: number | string,
  xMode: XMode = XMode.Time,
  chartPointCount: number = TRACK_DETAILS_CHART_POINTS_DEFAULT
): Promise<TrackChartSeries> {
  try {
    const maxBuckets = clampTrackDetailsChartPointCount(chartPointCount);
    console.log(`fetch chart series (${xMode}, maxBuckets=${maxBuckets}) for`, gpsTrackId);
    const response = await fetchChartSeries(gpsTrackId, { xMode, maxBuckets });
    return chartSeriesToTrackChartSeries(response);
  } catch (error: unknown) {
    logSanitizedError('Error fetching track chart series:', error);
    throw new Error(String(error));
  }
}

/**
 * Fetch per-point data for the map's track-point click popup.
 *
 * The map renders a track-points layer from the SIMPLIFIED_SHAPE LineString
 * loaded by the bulk fetcher and tags each emitted point with its array
 * index as `pointIndex`. The popup looks the clicked point up by that
 * index, so the per-point dataset MUST come from the same SIMPLIFIED_SHAPE
 * variant at the same `precisionInMeter` the map is currently rendering
 * for that track. SIMPLIFIED_FIXED_POINTS is unrelated here — its row
 * indices do not correspond to SIMPLIFIED_SHAPE coordinate indices.
 */
export async function fetchTrackPointsForRenderedShape(
  gpsTrackId: number | string,
  precisionInMeter: number
): Promise<GpsTrackDataPoint[]> {
  try {
    console.log(`fetch track points (SIMPLIFIED_SHAPE @ ${precisionInMeter}m, map popup) for`, gpsTrackId);
    const response = await apiClient.get(
      `api/tracks/get/${gpsTrackId}/details?trackType=SIMPLIFIED_SHAPE&precisionInMeter=${precisionInMeter}`
    );
    return convertDataPointDates<GpsTrackDataPoint>(response.data);
  } catch (error: unknown) {
    logSanitizedError('Error fetching track points for map popup:', error);
    throw new Error(String(error));
  }
}

/**
 * Fetch the full canonical RAW_OUTLIER_CLEANED per-point dataset for a track.
 *
 * This is the single source of truth for per-point derived metrics
 * (speed, slope, ascent/descent, energy, power, …) under the canonical-
 * metric-LOD architecture. Lookups are by `pointIndex`, which corresponds
 * 1:1 to the `canonicalPointIndex` back-pointer carried on SIMPLIFIED_SHAPE
 * point rows — so the map popup can resolve a clicked simplified vertex to
 * its canonical metric snapshot.
 *
 * Use sparingly: canonical density is full GPS rate (often >10k points).
 * Callers should cache per trackId.
 */
export async function fetchTrackCanonicalPoints(gpsTrackId: number | string): Promise<GpsTrackDataPoint[]> {
  try {
    console.log(`fetch canonical points (RAW_OUTLIER_CLEANED) for`, gpsTrackId);
    const response = await apiClient.get(
      `api/tracks/get/${gpsTrackId}/details?trackType=RAW_OUTLIER_CLEANED&precisionInMeter=0`
    );
    return convertDataPointDates<GpsTrackDataPoint>(response.data);
  } catch (error: unknown) {
    logSanitizedError('Error fetching canonical track points:', error);
    throw new Error(String(error));
  }
}

export async function fetchFilters(): Promise<FilterInfo[]> {
  try {
    const api = getFilterApi();
    console.log('fetch filters from server');

    const filters = await api.getFilters();
    // The API already returns FilterInfo[] objects, but ensure typing
    return filters;
  } catch (error: unknown) {
    logSanitizedError('Error fetching filters:', error);
    throw new Error(String(error));
  }
}

export async function fetchFilterInfo(filterDomain: string, filterName: string): Promise<FilterInfo> {
  try {
    const api = getFilterApi();
    console.log('fetch filter info for filterName', filterName);

    const filterInfo = await api.getFilterInfo({
      filterName: filterName,
      filterDomain: filterDomain,
    });

    return filterInfo;
  } catch (error: unknown) {
    logSanitizedError('Error fetching filter info:', error);
    throw new Error(String(error));
  }
}

/**
 * Extended result from filter/resolve that extends FilterResult with the full
 * QueryResult for UI display. This means it satisfies FilterResult directly and
 * can be passed to the track collection loader without conversion.
 */
export interface ResolveFilterResult extends FilterResult {
  /** Parsed QueryResult for UI display (entries, groups) */
  queryResult: QueryResult;
}

export async function fetchResolveFilter(
  filterConfigId: number,
  filterParams: FilterParamsRequest,
  includeGPSTrack: boolean = false
): Promise<ResolveFilterResult> {
  try {
    console.log('fetch resolveFilter for filterConfigId', filterConfigId, filterParams);

    // The getResolveById API doesn't accept a body with filter params,
    // so we POST manually with body.
    const response = await apiClient.post(
      `api/filter/resolve/${filterConfigId}?includeGPSTrack=${includeGPSTrack}`,
      filterParams
    );

    const queryResult = QueryResultFromJSONTyped(response.data, false);

    // Read VersionAware fields directly from raw JSON —
    // these are extra fields not in the generated TypeScript types.
    const rawVersions: Record<string, number> = response.data.trackVersions ?? {};
    const trackVersions = new Map<number, number>(Object.entries(rawVersions).map(([k, v]) => [Number(k), Number(v)]));
    const rawGroups: Record<string, string> = response.data.filterGroups ?? {};
    const filterGroups = new Map<number, string>(Object.entries(rawGroups).map(([k, v]) => [Number(k), v]));
    const legendGroupOrder: string[] = [];
    const seenLegendGroups = new Set<string>();
    for (const entry of queryResult.resultEntries ?? []) {
      const group = entry.group;
      if (!group || seenLegendGroups.has(group)) continue;
      seenLegendGroups.add(group);
      legendGroupOrder.push(group);
    }
    const standardFilterCount = Number(response.data.standardFilterCount ?? 0);

    return { queryResult, trackVersions, filterGroups, legendGroupOrder, standardFilterCount };
  } catch (error: unknown) {
    logSanitizedError('Error fetching filter resolve:', error);
    throw new Error(String(error));
  }
}

export async function fetchQueryResult(
  filterDomain: string,
  filterName: string,
  filterParams: FilterParamsRequest,
  includeGPSTrack: boolean = false
): Promise<QueryResult> {
  try {
    console.log('fetch queryResult for filterDomain', filterDomain, 'filterName', filterName);

    const response = await apiClient.post(
      `api/filter/resolve?filterDomain=${filterDomain}&filterName=${filterName}&includeGPSTrack=${includeGPSTrack}`,
      filterParams
    );

    return QueryResultFromJSONTyped(response.data, false);
  } catch (error: unknown) {
    logSanitizedError('Error fetching query result:', error);
    throw new Error(String(error));
  }
}

// Placeholder for fetching query result entries (not yet implemented in API)
export async function fetchQueryResultEntries(queryResultId: number): Promise<QueryResultEntry[]> {
  try {
    console.log('fetch queryResultEntries for queryResultId', queryResultId);

    const response = await apiClient.get(`api/filter/query-result/${queryResultId}/entries`);

    return (response.data as unknown[]).map(QueryResultEntryFromJSON);
  } catch (error: unknown) {
    logSanitizedError('Error fetching query result entries:', error);
    throw new Error(String(error));
  }
}

export async function getConfig(
  domain1: string = '',
  domain2: string = '',
  domain3: string = ''
): Promise<ConfigEntity[]> {
  try {
    const api = getConfigApi();
    console.log('fetch config for', domain1, domain2, domain3);

    const configs = await api.get({
      domain1: domain1 || undefined,
      domain2: domain2 || undefined,
      domain3: domain3 || undefined,
    });

    return configs;
  } catch (error: unknown) {
    logSanitizedError('Error fetching config:', error, { domain1, domain2, domain3 });
    throw new Error(String(error));
  }
}

// Placeholder for storing config (not yet implemented in generated API)
export async function postStoreConfig(configEntity: ConfigEntity): Promise<ConfigEntity> {
  try {
    console.log('store config', configEntity);

    const response = await apiClient.post(`api/config/save`, configEntity);

    return ConfigEntityFromJSONTyped(response.data, false);
  } catch (error: unknown) {
    logSanitizedError('Error storing config:', error);
    throw new Error(String(error));
  }
}

// Legacy alias
export async function fetchConfig(
  domain1: string = '',
  domain2: string = '',
  domain3: string = ''
): Promise<ConfigEntity[]> {
  return getConfig(domain1, domain2, domain3);
}

// Garmin export, GPX upload, server log, indexer/job/operational status, build info,
// demo status, and the auth probe live in @/utils/serverAdminApi and are
// re-exported from this module for back-compat (see header).
