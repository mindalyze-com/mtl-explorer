import { defineStore } from 'pinia';
import { computed, ref, shallowRef } from 'vue';
import {
  ClientFilterConfig,
  FilterService,
  type FilterParamsRequest,
} from '@/components/filter/FilterService';

/**
 * Reactive cache around `FilterService`.
 *
 * Rationale:
 *   - `FilterService` is a static class that talks to localStorage + the
 *     server. Most call sites await `loadClientFilterConfig()` repeatedly and
 *     have to manage their own `active` flag.
 *   - This store keeps the resolved `ClientFilterConfig` in a reactive ref so
 *     consumers can subscribe (`storeToRefs`, `watch`) instead of polling.
 *
 * Migration strategy:
 *   - `FilterService` remains the I/O layer. Writes go through the service.
 *   - Components that need reactivity should call `useFilterStore()` and read
 *     `config` / `isStandard`.
 *   - When other code mutates state through `FilterService.saveClientFilterConfig`
 *     directly, callers should also call `store.refresh()` so subscribers see
 *     the change. (Eventually all writes should funnel through `store.save()`.)
 */
export const useFilterStore = defineStore('filter', () => {
  // shallowRef: the config object is large + nested but treated as immutable
  // (whole object swapped on each load/save), so deep reactivity is wasted.
  const config = shallowRef<ClientFilterConfig | null>(null);
  const loading = ref<Promise<ClientFilterConfig> | null>(null);

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
  function save(cfg: ClientFilterConfig | null): void {
    FilterService.saveClientFilterConfig(cfg);
    config.value = cfg;
  }

  /**
   * Re-read the config from FilterService — used when external code (legacy
   * call sites still calling FilterService.saveClientFilterConfig directly)
   * has mutated localStorage and we need subscribers to see the change.
   */
  async function refresh(): Promise<ClientFilterConfig> {
    return ensureLoaded(true);
  }

  /**
   * True if the current config is the default (standard) GPS filter with no
   * extra params applied. Reactive — recomputes when `config` changes.
   *
   * Returns `true` until the first load completes (mirrors the previous
   * "filterActive defaults to false" behavior).
   */
  const isStandard = computed(() =>
    config.value === null
      ? true
      : FilterService.isStandardFilterWithStandardParams(config.value)
  );

  /** Convenience inverse of `isStandard`. */
  const isActive = computed(() => !isStandard.value);

  /** Convenience accessor for the current filterParams (or null). */
  const filterParams = computed<FilterParamsRequest | null>(() => config.value?.filterParams ?? null);

  return {
    config,
    isStandard,
    isActive,
    filterParams,
    ensureLoaded,
    refresh,
    save,
  };
});
