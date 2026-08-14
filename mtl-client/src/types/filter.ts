/**
 * Core filter result type — any object that carries matching track IDs/versions
 * and group assignments satisfies this interface.
 *
 * Both the lightweight `get-simplified?mode=ids` response (via fetchFilteredTrackIds)
 * and the richer `filter/resolve` response (via fetchResolveFilter /
 * ResolveFilterResult) implement this interface, so the track collection loader
 * can accept either without conversion.
 */
export interface FilterResult {
  /** Stable selected-view identity used for preserving map-only visibility across live updates. */
  filterConfigId?: number;
  /** Track ID → server entity version, used for client-side cache invalidation */
  trackVersions: Map<number, number>;
  /** Track ID → group assignment (for colour coding on the map) */
  filterGroups: Map<number, string>;
  /** First-seen group order from the filter result. Used when legend order is left as "Default". */
  legendGroupOrder?: string[];
  /** Total unfiltered track count (denominator for "N of M Tracks") */
  standardFilterCount: number;
  /** True when the dynamic SQL returned a grp column. */
  groupingAvailable?: boolean;
  /** Raw group catalog before the exact result-category selection is applied. */
  availableGroups?: import('x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterResultGroupSummary').FilterResultGroupSummary[];
  /** Dynamic SQL row count before the exact result-category selection is applied. */
  preGroupSelectionCount?: number;
}
