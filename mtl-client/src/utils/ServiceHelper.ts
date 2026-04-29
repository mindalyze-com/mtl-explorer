import axios from "axios";
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
  FilterInfoFromJSONTyped,
  type FilterInfo,
  type GpsTrackDataPoint,
  type RelatedTracks,
  type GpsTrackStatistics,
  type QueryResultEntry,
  type TriggerPoint,
} from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';

import { ClientFilterConfig, type FilterParams, type FilterParamsRequest, FilterService } from "@/components/filter/FilterService";
import type { FilterResult } from '@/types/filter';
import { apiClient } from '@/utils/apiClient';
import { getApiConfiguration } from '@/utils/openApiClient';

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
  AuthCheckResult,
} from '@/utils/serverAdminApi';

export const CONFIG_DOMAIN1_CLIENT = "CLIENT";

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

export async function getRelatedTracks(gpsTrackId: number | string): Promise<RelatedTracks> {
  try {
    let clientFilterConfig = await FilterService.loadClientFilterConfig();
    let filterName = clientFilterConfig.filterInfo?.filterConfig?.filterName ?? "";

    const api = getTracksApi();
    const result = await api.getRelatedTracks({
      gpsTrackId: Number(gpsTrackId),
      filterName: filterName,
    });

    return result;
  } catch (error: unknown) {
    console.error('Error getting related tracks:', error);
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
 * SIMPLIFIED_FIXED_POINTS @ 1500 and is intended only for display on the
 * Track Details screen.
 */
export async function fetchTrackDetailsForCrossingPoints(triggerPoints: TriggerPoint[], radius: number): Promise<CrossingPointsResponse> {
  try {
    let clientFilterConfig = await FilterService.loadClientFilterConfig();
    let filterParams = clientFilterConfig.filterParams;
    let filterName = clientFilterConfig.filterInfo?.filterConfig?.filterName ?? "";

    let request = {
      triggerPoints: triggerPoints,
      radius: radius,
      filter: {
        filterName: filterName,
        params: flattenFilterParams(filterParams)
      }
    };

    // POST with complex body — bypasses the generated client because the
    // CrossingPointsResponse map fields aren't deserialized correctly.
    const response = await apiClient.post(
      `api/tracks/get-track-details-for-tracks-crossing-points`,
      request,
    );

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
      (data as any).tracksPerZone = response.data.tracksPerZone;
    }
    return data;
  } catch (error: unknown) {
    console.error('Error getting track details for crossing points:', error);
    throw new Error(String(error));
  }
}

export async function fetchTrackIdsWithinDistanceOfPoint(longitude: number, latitude: number, distanceInMeter: number, signal?: AbortSignal): Promise<number[]> {
  try {
    let clientFilterConfig = await FilterService.loadClientFilterConfig();
    let filterParams = clientFilterConfig.filterParams;
    let filterName = clientFilterConfig.filterInfo?.filterConfig?.filterName ?? "";

    const response = await apiClient.post(
      `api/tracks/get-track-ids-within-distance-of-point?filterName=${filterName}&longitude=${longitude}&latitude=${latitude}&distanceInMeter=${distanceInMeter}`,
      flattenFilterParams(filterParams),
      { signal }
    );

    return response.data as number[];
  } catch (error: unknown) {
    if (axios.isCancel(error)) throw error;
    console.error('Error getting track IDs within distance:', error);
    throw new Error(String(error));
  }
}

export async function fetchStatistics(grouping: string): Promise<GpsTrackStatistics[]> {
  try {
    let clientFilterConfig = await FilterService.loadClientFilterConfig();
    let filterParams = clientFilterConfig.filterParams;
    let filterName = clientFilterConfig.filterInfo?.filterConfig?.filterName ?? "";

    const response = await apiClient.post(
      `api/tracks/get-track-statistics?groupByDateFormat=${grouping}&filterName=${filterName}`,
      flattenFilterParams(filterParams),
    );

    return response.data;
  } catch (error: unknown) {
    console.error('Error fetching statistics:', error);
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
function convertDataPointDates(raw: any[]): GpsTrackDataPoint[] {
  return raw.map((d: any) => ({
    ...d,
    pointTimestamp: d.pointTimestamp ? new Date(d.pointTimestamp) : undefined,
    createDate: d.createDate ? new Date(d.createDate) : undefined,
  }));
}

export async function fetchTrackSubTrackDetails(trackDataPointFrom: number, trackDataPointTo: number): Promise<GpsTrackDataPoint[]> {
  try {
    const response = await apiClient.get(
      `api/tracks/details/get-sub-track?trackDataPointFrom=${trackDataPointFrom}&trackDataPointTo=${trackDataPointTo}`,
    );
    return convertDataPointDates(response.data);
  } catch (error: unknown) {
    console.error('Error fetching track sub track details:', error);
    throw new Error(String(error));
  }
}

/**
 * Fetch per-point track data for **CHART / GRAPH DISPLAY** on the Track
 * Details screen (line chart, Overview summary, table).
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
 *    `pointIndex`. Those indices do NOT align with SIMPLIFIED_FIXED_POINTS
 *    row indices. Use `fetchTrackPointsForRenderedShape()` instead.
 *
 * Why SIMPLIFIED_FIXED_POINTS @ 1500 is the right default HERE:
 *   - Authoritative track-level totals (energyNetTotalWh, powerWattsAvg/Max,
 *     trackLengthInMeter, ascent/descent) are pre-computed on the GpsTrack
 *     entity at import time from RAW_OUTLIER_CLEANED — those numbers are
 *     already full-precision and are read directly from the entity.
 *   - For the per-point data we render in charts (chart series, Overview's
 *     computeSummary, table rows), the SIMPLIFIED_FIXED_POINTS variant is a
 *     time-uniform downsample of RAW_OUTLIER_CLEANED capped at 1500 points.
 *     All window metrics (elevation-gain rate, speed, slope, energy) are
 *     copied verbatim from the authoritative full-density series, so chart
 *     resolution is even across the whole track — no more flat-zero bands
 *     on straight climbs (which Douglas-Peucker'd SIMPLIFIED_SHAPE @ 10 m
 *     produced because it drops straight-line points regardless of their
 *     temporal spacing).
 *
 * If a "high precision display" mode is ever needed, it should be an
 * explicit user-toggled option, not the default.
 */
export async function fetchTrackDetails(gpsTrackId: number | string): Promise<GpsTrackDataPoint[]> {
  try {
    console.log("fetch track details (SIMPLIFIED_FIXED_POINTS @ 1500, chart-only) for", gpsTrackId);
    const response = await apiClient.get(
      `api/tracks/get/${gpsTrackId}/details?trackType=SIMPLIFIED_FIXED_POINTS&maxPoints=1500`,
    );
    return convertDataPointDates(response.data);
  } catch (error: unknown) {
    console.error('Error fetching track details:', error);
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
  precisionInMeter: number,
): Promise<GpsTrackDataPoint[]> {
  try {
    console.log(`fetch track points (SIMPLIFIED_SHAPE @ ${precisionInMeter}m, map popup) for`, gpsTrackId);
    const response = await apiClient.get(
      `api/tracks/get/${gpsTrackId}/details?trackType=SIMPLIFIED_SHAPE&precisionInMeter=${precisionInMeter}`,
    );
    return convertDataPointDates(response.data);
  } catch (error: unknown) {
    console.error('Error fetching track points for map popup:', error);
    throw new Error(String(error));
  }
}

export async function fetchFilters(): Promise<FilterInfo[]> {
  try {
    const api = getFilterApi();
    console.log("fetch filters from server");

    const filters = await api.getFilters();
    // The API already returns FilterInfo[] objects, but ensure typing
    return filters;
  } catch (error: unknown) {
    console.error('Error fetching filters:', error);
    throw new Error(String(error));
  }
}

export async function fetchFilterInfo(filterDomain: string, filterName: string): Promise<FilterInfo> {
  try {
    const api = getFilterApi();
    console.log("fetch filter info for filterName", filterName);

    const filterInfo = await api.getFilterInfo({
      filterName: filterName,
      filterDomain: filterDomain,
    });

    return filterInfo;
  } catch (error: unknown) {
    console.error('Error fetching filter info:', error);
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
  includeGPSTrack: boolean = false): Promise<ResolveFilterResult> {
  try {
    const api = getFilterApi();
    console.log("fetch resolveFilter for filterConfigId", filterConfigId, filterParams);

    // The getResolveById API doesn't accept a body with filter params,
    // so we POST manually with body.
    const response = await apiClient.post(
      `api/filter/resolve/${filterConfigId}?includeGPSTrack=${includeGPSTrack}`,
      filterParams,
    );

    const queryResult = QueryResultFromJSONTyped(response.data, false);

    // Read VersionAware fields directly from raw JSON —
    // these are extra fields not in the generated TypeScript types.
    const rawVersions: Record<string, number> = response.data.trackVersions ?? {};
    const trackVersions = new Map<number, number>(
      Object.entries(rawVersions).map(([k, v]) => [Number(k), Number(v)])
    );
    const rawGroups: Record<string, string> = response.data.filterGroups ?? {};
    const filterGroups = new Map<number, string>(
      Object.entries(rawGroups).map(([k, v]) => [Number(k), v])
    );
    const standardFilterCount = Number(response.data.standardFilterCount ?? 0);

    return { queryResult, trackVersions, filterGroups, standardFilterCount };
  } catch (error: unknown) {
    console.error('Error fetching filter resolve:', error);
    throw new Error(String(error));
  }
}

export async function fetchQueryResult(
  filterDomain: string,
  filterName: string,
  filterParams: FilterParamsRequest,
  includeGPSTrack: boolean = false): Promise<QueryResult> {
  try {
    console.log("fetch queryResult for filterDomain", filterDomain, "filterName", filterName);

    const response = await apiClient.post(
      `api/filter/resolve?filterDomain=${filterDomain}&filterName=${filterName}&includeGPSTrack=${includeGPSTrack}`,
      filterParams,
    );

    return QueryResultFromJSONTyped(response.data, false);
  } catch (error: unknown) {
    console.error('Error fetching query result:', error);
    throw new Error(String(error));
  }
}

// Placeholder for fetching query result entries (not yet implemented in API)
export async function fetchQueryResultEntries(queryResultId: number): Promise<QueryResultEntry[]> {
  try {
    console.log("fetch queryResultEntries for queryResultId", queryResultId);

    const response = await apiClient.get(
      `api/filter/query-result/${queryResultId}/entries`,
    );

    return (response.data as any[]).map(QueryResultEntryFromJSON);
  } catch (error: unknown) {
    console.error('Error fetching query result entries:', error);
    throw new Error(String(error));
  }
}

export async function getConfig(domain1: string = "", domain2: string = "", domain3: string = ""): Promise<ConfigEntity[]> {
  try {
    const api = getConfigApi();
    console.log("fetch config for", domain1, domain2, domain3);

    const configs = await api.get({
      domain1: domain1 || undefined,
      domain2: domain2 || undefined,
      domain3: domain3 || undefined,
    });

    return configs;
  } catch (error: unknown) {
    console.error('Error fetching config:', domain1, domain2, domain3, error);
    throw new Error(String(error));
  }
}

// Placeholder for storing config (not yet implemented in generated API)
export async function postStoreConfig(configEntity: ConfigEntity): Promise<ConfigEntity> {
  try {
    console.log("store config", configEntity);

    const response = await apiClient.post(
      `api/config/save`,
      configEntity,
    );

    return ConfigEntityFromJSONTyped(response.data, false);
  } catch (error: unknown) {
    console.error('Error storing config:', error);
    throw new Error(String(error));
  }
}

// Legacy alias
export async function fetchConfig(domain1: string = "", domain2: string = "", domain3: string = ""): Promise<ConfigEntity[]> {
  return getConfig(domain1, domain2, domain3);
}

// Garmin export, GPX upload, server log, indexer/job status, build info,
// demo status, and the auth probe live in @/utils/serverAdminApi and are
// re-exported from this module for back-compat (see header).
