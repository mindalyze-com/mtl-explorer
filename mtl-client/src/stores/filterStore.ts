import { defineStore } from 'pinia';
import { computed, markRaw, ref, shallowRef } from 'vue';
import { ClientFilterConfig, FilterService, type FilterParamsRequest } from '@/components/filter/FilterService';
import type { FilterResult } from '@/types/filter';
import { fetchResolveFilter, type ResolveFilterResult } from '@/utils/filterApi';

export type ActiveFilterRequest = {
  filterName: string;
  filterParams: FilterParamsRequest | undefined;
  resolvedTrackIds?: number[];
};

export type FilterStoreSaveOptions = {
  trackSetChanged?: boolean;
};

/**
 * Active filter state for the map and filter UI.
 *
 * Rationale:
 *   - `FilterService` is the persistence/hydration layer: localStorage +
 *     server fallback metadata.
 *   - This store owns the in-app active filter config and, when available, the
 *     resolved filter result that should be rendered on the map.
 *
 * Migration strategy:
 *   - Writes should go through this store, not directly through
 *     `FilterService.saveClientFilterConfig`.
 *   - Non-component data loaders should ask the store for
 *     `getActiveFilterRequest()` and only fall back to `FilterService` in test
 *     or bootstrap contexts where Pinia is not active yet.
 */
export const useFilterStore = defineStore('filter', () => {
  // shallowRef: the config object is large + nested but treated as immutable
  // (whole object swapped on each load/save), so deep reactivity is wasted.
  const config = shallowRef<ClientFilterConfig | null>(null);
  const activeResult = shallowRef<FilterResult | ResolveFilterResult | null>(null);
  const loading = ref<Promise<ClientFilterConfig> | null>(null);
  const trackSetRevision = ref(0);
  const dataFreshnessRevision = ref(0);

  /**
   * Resolve the current config. First call hits FilterService (localStorage +
   * server fallback). Subsequent calls return the cached value unless `force`
   * is set.
   */
  async function ensureLoaded(force = false): Promise<ClientFilterConfig> {
    if (!force && config.value) return config.value;
    if (!loading.value) {
      loading.value = FilterService.loadClientFilterConfig();
    }
    try {
      const cfg = await loading.value;
      config.value = cfg;
      return cfg;
    } finally {
      loading.value = null;
    }
  }

  /** Persist a new config and update the reactive ref atomically. */
  function save(cfg: ClientFilterConfig | null, options: FilterStoreSaveOptions = {}): void {
    FilterService.saveClientFilterConfig(cfg);
    config.value = cfg;
    if (options.trackSetChanged ?? true) {
      activeResult.value = null;
      markTrackSetChanged();
    }
  }

  /**
   * Persist the active config and remember the exact resolved result that the
   * map should render. This is the live-filter path used after a successful
   * preview resolve.
   */
  function applyResolvedFilter(cfg: ClientFilterConfig, result: FilterResult): void {
    FilterService.saveClientFilterConfig(cfg);
    config.value = cfg;
    activeResult.value = markRaw(result);
    markTrackSetChanged();
  }

  /**
   * Re-read the config from the persistence layer. This is mainly for startup
   * hydration and for any still-migrating legacy code paths that mutate
   * localStorage outside this store.
   */
  async function refresh(): Promise<ClientFilterConfig> {
    return ensureLoaded(true);
  }

  /** Re-resolve the active filter after the server-side track dataset changes. */
  async function refreshResolvedFilter(): Promise<ResolveFilterResult> {
    const cfg = await ensureLoaded();
    const filterId = cfg.filterInfo?.filterConfig?.id;
    if (filterId == null) {
      throw new Error('Cannot refresh the active filter without a filter configuration ID.');
    }

    // Remove stale IDs and UI rows while the replacement result is in flight.
    activeResult.value = null;
    const result = await fetchResolveFilter(filterId, cfg.filterParams ?? {}, false);
    activeResult.value = markRaw(result);
    dataFreshnessRevision.value += 1;
    markTrackSetChanged();
    return result;
  }

  function markTrackSetChanged(): void {
    trackSetRevision.value += 1;
  }

  /**
   * True if the current config is the default (standard) GPS filter with no
   * extra params applied. Reactive — recomputes when `config` changes.
   *
   * Returns `true` until the first load completes (mirrors the previous
   * "filterActive defaults to false" behavior).
   */
  const isStandard = computed(() =>
    config.value === null ? true : FilterService.isStandardFilterWithStandardParams(config.value)
  );

  /** True when the current config changes the visible track set or map coloring. */
  const isActive = computed(() => (config.value === null ? false : FilterService.hasActiveFilterConfig(config.value)));

  /** Convenience accessor for the current filterParams (or null). */
  const filterParams = computed<FilterParamsRequest | null>(() => config.value?.filterParams ?? null);
  const activeFilterRequest = computed<ActiveFilterRequest | null>(() =>
    config.value == null ? null : activeFilterRequestFromConfig(config.value, activeResult.value)
  );

  async function getActiveFilterRequest(): Promise<ActiveFilterRequest> {
    const cfg = await ensureLoaded();
    return activeFilterRequestFromConfig(cfg, activeResult.value);
  }

  return {
    config,
    activeResult,
    trackSetRevision,
    dataFreshnessRevision,
    isStandard,
    isActive,
    filterParams,
    activeFilterRequest,
    ensureLoaded,
    refresh,
    refreshResolvedFilter,
    save,
    applyResolvedFilter,
    getActiveFilterRequest,
  };
});

/**
 * Resolve the active filter outside Vue component setup. The fallback keeps
 * bootstrap code and isolated tests working before Pinia is active.
 */
export async function loadActiveFilterRequest(): Promise<ActiveFilterRequest> {
  try {
    return await useFilterStore().getActiveFilterRequest();
  } catch {
    const clientFilterConfig = await FilterService.loadClientFilterConfig();
    return activeFilterRequestFromConfig(clientFilterConfig);
  }
}

function activeFilterRequestFromConfig(
  clientFilterConfig: ClientFilterConfig,
  filterResult?: FilterResult | null
): ActiveFilterRequest {
  return {
    filterName: clientFilterConfig.filterInfo?.filterConfig?.filterName ?? '',
    filterParams: clientFilterConfig.filterParams,
    resolvedTrackIds: filterResult ? Array.from(filterResult.trackVersions.keys()) : undefined,
  };
}
