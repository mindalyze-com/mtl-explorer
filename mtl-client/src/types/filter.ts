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
  /** Track ID → server entity version, used for client-side cache invalidation */
  trackVersions: Map<number, number>;
  /** Track ID → group assignment (for colour coding on the map) */
  filterGroups: Map<number, string>;
  /** Total unfiltered track count (denominator for "N of M Tracks") */
  standardFilterCount: number;
}
