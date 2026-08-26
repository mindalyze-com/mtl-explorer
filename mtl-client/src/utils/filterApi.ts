import { QueryResultFromJSONTyped, type QueryResult } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import type { FilterParamsRequest } from '@/components/filter/FilterService';
import type { FilterResult } from '@/types/filter';
import { apiClient } from '@/utils/apiClient';
import { logSanitizedError } from '@/utils/safeLogging';

/** A resolved filter result with the complete query result used by the filter UI. */
export interface ResolveFilterResult extends FilterResult {
  queryResult: QueryResult;
}

export async function fetchResolveFilter(
  filterConfigId: number,
  filterParams: FilterParamsRequest,
  includeGPSTrack: boolean = false
): Promise<ResolveFilterResult> {
  try {
    console.log('fetch resolveFilter for filterConfigId', filterConfigId, filterParams);

    // The generated getResolveById method cannot send filter parameters in the request body.
    const response = await apiClient.post(
      `api/filter/resolve/${filterConfigId}?includeGPSTrack=${includeGPSTrack}&includeGPSTrackFile=${includeGPSTrack}`,
      filterParams
    );
    const queryResult = QueryResultFromJSONTyped(response.data, false);

    const rawVersions = queryResult.trackVersions ?? {};
    const trackVersions = new Map<number, number>(
      Object.entries(rawVersions).map(([key, value]) => [Number(key), Number(value)])
    );
    const rawGroups = queryResult.filterGroups ?? {};
    const filterGroups = new Map<number, string>(Object.entries(rawGroups).map(([key, value]) => [Number(key), value]));
    const legendGroupOrder: string[] = [];
    const seenLegendGroups = new Set<string>();
    for (const entry of queryResult.resultEntries ?? []) {
      const group = entry.group;
      if (!group || seenLegendGroups.has(group)) continue;
      seenLegendGroups.add(group);
      legendGroupOrder.push(group);
    }

    return {
      queryResult,
      filterConfigId,
      trackVersions,
      filterGroups,
      legendGroupOrder,
      standardFilterCount: Number(queryResult.standardFilterCount ?? 0),
      groupingAvailable: queryResult.groupingAvailable,
      availableGroups: queryResult.availableGroups,
      preGroupSelectionCount: queryResult.preGroupSelectionCount,
    };
  } catch (error: unknown) {
    logSanitizedError('Error fetching filter resolve:', error);
    throw new Error(String(error));
  }
}
