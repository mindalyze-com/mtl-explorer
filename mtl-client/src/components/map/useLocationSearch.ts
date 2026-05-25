import { computed, nextTick, onBeforeUnmount, ref, watch, type Ref } from 'vue';
import { LocationSearchControllerApi } from 'x8ing-mtl-api-typescript-fetch';
import type {
  LocationSearchResponseDto,
  LocationSearchResultDto,
  LocationSearchStatusDto,
} from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import { getApiConfiguration } from '@/utils/openApiClient';
import { getToken, isAuthError, redirectToLoginAfterAuthFailure } from '@/utils/auth';

export type LocationSearchSort = 'importance' | 'distance';

export interface MapCenter {
  lat: number;
  lon: number;
}

export const SEARCH_QUERY_MIN_LENGTH = 2;
export const SEARCH_LIMIT = 20;
export const SEARCH_DEBOUNCE_MS = 300;
export const LOCATION_SEARCH_Z_INDEX = 5250;
export const SEARCH_DETENTS = [
  { id: 'compact', height: '44vh' },
  { id: 'medium', height: '66vh' },
  { id: 'expanded', height: '90vh' },
];

const SORT_IMPORTANCE: LocationSearchSort = 'importance';
const SORT_DISTANCE: LocationSearchSort = 'distance';

export const SORT_OPTIONS: Array<{ id: LocationSearchSort; label: string; icon: string }> = [
  { id: SORT_IMPORTANCE, label: 'Importance', icon: 'bi bi-sort-down' },
  { id: SORT_DISTANCE, label: 'Near map', icon: 'bi bi-crosshair' },
];

interface UseLocationSearchOptions {
  modelValue: Ref<boolean>;
  mapCenter: Ref<MapCenter | null>;
  onSelect: (result: LocationSearchResultDto) => void;
}

export function useLocationSearch(options: UseLocationSearchOptions) {
  const inputEl = ref<HTMLInputElement | null>(null);
  const query = ref('');
  const selectedSort = ref<LocationSearchSort>(SORT_IMPORTANCE);
  const loading = ref(false);
  const status = ref<LocationSearchStatusDto | null>(null);
  const errorMessage = ref('');
  const results = ref<LocationSearchResultDto[]>([]);

  let debounceTimer: ReturnType<typeof window.setTimeout> | null = null;
  let searchController: AbortController | null = null;
  let statusController: AbortController | null = null;
  let requestSequence = 0;

  const trimmedQuery = computed(() => query.value.trim());
  const stateMessage = computed(() => {
    if (errorMessage.value) return errorMessage.value;
    if (status.value && status.value.ready === false) {
      return status.value.message || status.value.phase || 'Search is not ready';
    }
    if (trimmedQuery.value.length > 0 && trimmedQuery.value.length < SEARCH_QUERY_MIN_LENGTH) {
      return 'Keep typing';
    }
    if (trimmedQuery.value.length >= SEARCH_QUERY_MIN_LENGTH && results.value.length === 0) {
      return 'No matches';
    }
    return '';
  });
  const stateIcon = computed(() =>
    errorMessage.value || status.value?.ready === false ? 'bi bi-exclamation-circle' : 'bi bi-search'
  );

  watch(options.modelValue, (open) => {
    if (!open) {
      cancelPendingSearch();
      return;
    }
    void nextTick(() => inputEl.value?.focus());
    void refreshStatus();
    scheduleSearch();
  });

  watch([trimmedQuery, selectedSort, options.mapCenter], () => {
    if (!options.modelValue.value) return;
    scheduleSearch();
  });

  onBeforeUnmount(() => {
    cancelPendingSearch();
    statusController?.abort();
  });

  function getLocationSearchApi(): LocationSearchControllerApi {
    return new LocationSearchControllerApi(getApiConfiguration());
  }

  async function refreshStatus() {
    statusController?.abort();
    statusController = new AbortController();
    try {
      status.value = await getLocationSearchApi().getStatus({ signal: statusController.signal });
    } catch (error: unknown) {
      if (isAbortError(error, statusController.signal)) return;
      if (isAuthError(error)) {
        redirectToLoginAfterAuthFailure(!!getToken());
        return;
      }
      status.value = null;
    }
  }

  function scheduleSearch() {
    if (debounceTimer) window.clearTimeout(debounceTimer);
    debounceTimer = window.setTimeout(() => {
      debounceTimer = null;
      void runSearch();
    }, SEARCH_DEBOUNCE_MS);
  }

  async function runSearch() {
    const currentQuery = trimmedQuery.value;
    if (currentQuery.length < SEARCH_QUERY_MIN_LENGTH) {
      cancelActiveRequest();
      loading.value = false;
      errorMessage.value = '';
      results.value = [];
      return;
    }

    if (status.value?.ready === false) {
      await refreshStatus();
      if (status.value?.ready === false) {
        cancelActiveRequest();
        loading.value = false;
        errorMessage.value = '';
        results.value = [];
        return;
      }
    }

    cancelActiveRequest();
    const sequence = ++requestSequence;
    searchController = new AbortController();
    loading.value = true;
    errorMessage.value = '';

    try {
      const center = validMapCenter(options.mapCenter.value) ? options.mapCenter.value : null;
      const response: LocationSearchResponseDto = await getLocationSearchApi().search(
        {
          q: currentQuery,
          limit: SEARCH_LIMIT,
          sort: center ? selectedSort.value : SORT_IMPORTANCE,
          lat: center?.lat,
          lon: center?.lon,
        },
        { signal: searchController.signal }
      );

      if (sequence !== requestSequence) return;
      status.value =
        response.ready === false ? { ready: false, phase: response.phase, message: response.message } : status.value;
      results.value = response.ready === false ? [] : (response.results ?? []);
    } catch (error: unknown) {
      if (isAbortError(error, searchController.signal)) return;
      if (isAuthError(error)) {
        redirectToLoginAfterAuthFailure(!!getToken());
        return;
      }
      if (sequence === requestSequence) {
        errorMessage.value = 'Search unavailable';
        results.value = [];
      }
    } finally {
      if (sequence === requestSequence) {
        loading.value = false;
      }
    }
  }

  function clearQuery() {
    query.value = '';
    results.value = [];
    errorMessage.value = '';
    void nextTick(() => inputEl.value?.focus());
  }

  function selectFirstResult() {
    const first = results.value[0];
    if (first) {
      options.onSelect(first);
    }
  }

  function cancelPendingSearch() {
    if (debounceTimer) {
      window.clearTimeout(debounceTimer);
      debounceTimer = null;
    }
    cancelActiveRequest();
  }

  function cancelActiveRequest() {
    searchController?.abort();
    searchController = null;
    requestSequence++;
  }

  return {
    inputEl,
    query,
    selectedSort,
    loading,
    results,
    trimmedQuery,
    stateMessage,
    stateIcon,
    clearQuery,
    selectFirstResult,
  };
}

function validMapCenter(center: MapCenter | null): center is MapCenter {
  return Boolean(center) && Number.isFinite(center?.lat) && Number.isFinite(center?.lon);
}

function isAbortError(error: unknown, signal?: AbortSignal): boolean {
  return (
    signal?.aborted === true ||
    (error instanceof DOMException && error.name === 'AbortError') ||
    (error instanceof Error && error.name === 'AbortError')
  );
}

export function resultKey(result: LocationSearchResultDto): string {
  return [
    result.displayName,
    result.sourceLayer,
    result.kind,
    result.kindDetail,
    result.lat,
    result.lon,
    result.countryCode,
    result.admin1Code,
  ].join('|');
}

export function iconForResult(result: LocationSearchResultDto): string {
  const kind = (result.kindDetail || result.kind || '').toLowerCase();
  if (kind === 'country' || kind === 'region') return 'bi bi-globe2';
  if (kind === 'city' || kind === 'town') return 'bi bi-buildings';
  if (kind === 'village' || kind === 'hamlet') return 'bi bi-house';
  if (['peak', 'mountain', 'pass', 'ridge', 'hill'].includes(kind)) return 'bi bi-triangle-fill';
  if (kind === 'viewpoint') return 'bi bi-binoculars';
  if (kind.includes('hut') || kind === 'shelter' || kind === 'camp_site') return 'bi bi-signpost-split';
  if (kind === 'park' || kind === 'national_park' || kind === 'nature_reserve') return 'bi bi-tree';
  if (kind === 'neighbourhood' || kind === 'quarter') return 'bi bi-grid-3x3-gap';
  return 'bi bi-geo-alt';
}

export function kindLabel(result: LocationSearchResultDto): string {
  const value = result.kindDetail || result.kind || result.sourceLayer || 'place';
  return toTitleCase(value.replace(/_/g, ' '));
}

export function contextForResult(result: LocationSearchResultDto): string {
  const displayName = normalizeText(result.displayName || result.name || '');
  const parts = [result.admin1Name, normalizeText(result.countryName || '') === displayName ? '' : result.countryName]
    .filter((part): part is string => Boolean(part && part.trim()))
    .filter((part, index, all) => all.findIndex((other) => normalizeText(other) === normalizeText(part)) === index)
    .filter(
      (part) => normalizeText(part) !== displayName || normalizeText(part) === normalizeText(result.admin1Name || '')
    );
  return parts.join(', ') || result.sourceLayer || '';
}

export function zoomLabel(result: LocationSearchResultDto): string {
  if (result.minZoom == null && result.maxZoom == null) return '';
  if (result.minZoom != null && result.maxZoom != null && result.minZoom !== result.maxZoom) {
    return `z${result.minZoom}-${result.maxZoom}`;
  }
  return `z${result.minZoom ?? result.maxZoom}`;
}

export function distanceLabel(result: LocationSearchResultDto): string {
  const meters = result.distanceMeters;
  if (meters == null || !Number.isFinite(meters)) return '';
  if (meters < 1000) return `${Math.round(meters)} m`;
  const km = meters / 1000;
  if (km < 100) return `${km.toFixed(1)} km`;
  return `${Math.round(km)} km`;
}

function normalizeText(value: string): string {
  return value.trim().toLocaleLowerCase();
}

function toTitleCase(value: string): string {
  return value.replace(/\b\w/g, (char) => char.toLocaleUpperCase());
}
