import axios from 'axios';
import { TracksControllerApi, FilterControllerApi, ConfigControllerApi } from 'x8ing-mtl-api-typescript-fetch';
import {
  CrossingPointsResponseFromJSONTyped,
  CrossingsPerTrackFromJSON,
  QueryResultEntryFromJSON,
  type CrossingPointsResponse,
  QueryResultFromJSONTyped,
  type QueryResult,
  ConfigEntityFromJSONTyped,
  type ConfigEntity,
  type FilterInfo,
  type GpsTrack,
  type GpsTrackDataPoint,
  type RelatedTracks,
  type GpsTrackStatistics,
  type StatisticsExclusionUpdateRequest,
  type StatisticsOverviewResponseDto,
  type QueryResultEntry,
  type TriggerPoint,
} from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';

import { type FilterParamsRequest, FilterService } from '@/components/filter/FilterService';
import type { FilterResult } from '@/types/filter';
import { apiClient } from '@/utils/apiClient';
import { getApiConfiguration } from '@/utils/openApiClient';
import { logSanitizedError } from '@/utils/safeLogging';
import { chartSeriesToPoints, fetchChartSeries, XMode, type ChartPoint } from '@/utils/chartSeriesAdapter';
import {
  clampTrackDetailsChartPointCount,
  TRACK_DETAILS_CHART_POINTS_DEFAULT,
} from '@/utils/trackDetailsChartPointSettings';
export type { ChartPoint } from '@/utils/chartSeriesAdapter';

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
    const clientFilterConfig = await FilterService.loadClientFilterConfig();
    const filterName = clientFilterConfig.filterInfo?.filterConfig?.filterName ?? '';

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
): Promise<CrossingPointsResponse> {
  try {
    const clientFilterConfig = await FilterService.loadClientFilterConfig();
    const filterParams = clientFilterConfig.filterParams;
    const filterName = clientFilterConfig.filterInfo?.filterConfig?.filterName ?? '';

    const request = {
      triggerPoints: triggerPoints,
      radius: radius,
      filter: {
        filterName: filterName,
        params: flattenFilterParams(filterParams),
      },
    };

    // POST with complex body — bypasses the generated client because the
    // CrossingPointsResponse map fields aren't deserialized correctly.
    const response = await apiClient.post(`api/tracks/get-track-details-for-tracks-crossing-points`, request, {
      signal,
    });

    // Convert to typed object
    const data = CrossingPointsResponseFromJSONTyped(response.data, false);
    // Fix generator bug: map-typed fields are not deserialized by CrossingPointsResponseFromJSONTyped
    if (response.data.crossings) {
      data.crossings = Object.fromEntries(
        Object.entries(response.data.crossings).map(([k, v]) => [k, CrossingsPerTrackFromJSON(v)])
      );
    }
    // Pass through per-zone track counts (not in generated types yet)
    if (response.data.tracksPerZone) {
      data.tracksPerZone = response.data.tracksPerZone;
    }
    return data;
  } catch (error: unknown) {
    if (axios.isCancel(error)) throw error;
    logSanitizedError('Error getting track details for crossing points:', error);
    throw new Error(String(error));
  }
}

export async function fetchTrackIdsWithinDistanceOfPoint(
  longitude: number,
  latitude: number,
  distanceInMeter: number,
  signal?: AbortSignal
): Promise<number[]> {
  try {
    const clientFilterConfig = await FilterService.loadClientFilterConfig();
    const filterParams = clientFilterConfig.filterParams;
    const filterName = clientFilterConfig.filterInfo?.filterConfig?.filterName ?? '';

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

export async function fetchStatistics(grouping: string): Promise<GpsTrackStatistics[]> {
  try {
    const clientFilterConfig = await FilterService.loadClientFilterConfig();
    const filterParams = clientFilterConfig.filterParams;
    const filterName = clientFilterConfig.filterInfo?.filterConfig?.filterName ?? '';

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

export async function fetchStatisticsOverview(signal?: AbortSignal): Promise<StatisticsOverviewResponseDto> {
  try {
    const clientFilterConfig = await FilterService.loadClientFilterConfig();
    const filterParams = clientFilterConfig.filterParams;
    const filterName = clientFilterConfig.filterInfo?.filterConfig?.filterName ?? '';

    return await getTracksApi().getTrackOverview(
      {
        filterName: filterName || undefined,
        requestBody: flattenFilterParams(filterParams),
      },
      { signal }
    );
  } catch (error: unknown) {
    logSanitizedError('Error fetching statistics overview:', error);
    throw new Error(String(error));
  }
}

/**
 * Convert raw JSON data-point objects into a shape compatible with GpsTrackDataPoint.
 *
 * The generated PointFromJSON deserializer mangles JTS Point coordinates: the server
 * serialises them as GeoJSON ([lng, lat] number arrays) but PointFromJSON maps each
 * element through CoordinateFromJSON, producing {x: undefined, y: undefined, …}.
 * By fetching with axios we keep the raw GeoJSON intact, and we only need to convert
 * date strings to Date objects (which the generated client normally does for us).
 */
type RawGpsTrackDataPoint = GpsTrackDataPoint & {
  pointTimestamp?: string | Date;
  createDate?: string | Date;
};

function convertDataPointDates(raw: RawGpsTrackDataPoint[]): GpsTrackDataPoint[] {
  return raw.map((d) => ({
    ...d,
    pointTimestamp: d.pointTimestamp ? new Date(d.pointTimestamp) : undefined,
    createDate: d.createDate ? new Date(d.createDate) : undefined,
  }));
}

export async function fetchTrackSubTrackDetails(
  trackDataPointFrom: number,
  trackDataPointTo: number
): Promise<GpsTrackDataPoint[]> {
  try {
    const response = await apiClient.get(
      `api/tracks/details/get-sub-track?trackDataPointFrom=${trackDataPointFrom}&trackDataPointTo=${trackDataPointTo}`
    );
    return convertDataPointDates(response.data);
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
 * The buckets are projected into a flat `ChartPoint[]` so the existing
 * chart configs (`trackGraphConfigs.ts`) can render them unchanged.
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
): Promise<ChartPoint[]> {
  try {
    const maxBuckets = clampTrackDetailsChartPointCount(chartPointCount);
    console.log(`fetch chart series (${xMode}, maxBuckets=${maxBuckets}) for`, gpsTrackId);
    const response = await fetchChartSeries(gpsTrackId, { xMode, maxBuckets });
    return chartSeriesToPoints(response);
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
    return convertDataPointDates(response.data);
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
    return convertDataPointDates(response.data);
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
