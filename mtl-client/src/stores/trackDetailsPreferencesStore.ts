import { defineStore } from 'pinia';
import { ref } from 'vue';
import { readJsonStorage, STORAGE_KEYS, writeJsonStorage } from '@/utils/appStorage';
import {
  roundToNiceTrackDetailsChartPointCount,
  TRACK_DETAILS_CHART_POINTS_DEFAULT,
  TRACK_DETAILS_CHART_POINTS_MAX,
  TRACK_DETAILS_CHART_POINTS_MIN,
  trackDetailsChartPointSliderValueToCount,
} from '@/utils/trackDetailsChartPointSettings';

export const TRACK_DETAIL_GRAPH_HEIGHT_MIN = 100;
export const TRACK_DETAIL_GRAPH_HEIGHT_MAX = 640;
export const TRACK_DETAIL_GRAPH_HEIGHT_STEP = 10;
export const TRACK_DETAIL_GRAPH_HEIGHT_DEFAULT = 240;
export const TRACK_DETAIL_MINI_MAP_HEIGHT_MIN = 80;
export const TRACK_DETAIL_MINI_MAP_HEIGHT_MAX = 600;
export const TRACK_DETAIL_MINI_MAP_HEIGHT_DEFAULT = 220;
export const TRACK_DETAIL_MINI_MAP_HEIGHT_MOBILE_DEFAULT = 160;

type PersistedTrackDetailsPreferences = {
  graphHeightPx?: unknown;
  showRangeBand?: unknown;
  chartPointCount?: unknown;
  miniMapHeight?: unknown;
};

export const useTrackDetailsPreferencesStore = defineStore('trackDetailsPreferences', () => {
  const hydrated = ref(false);
  const graphHeightPx = ref(TRACK_DETAIL_GRAPH_HEIGHT_DEFAULT);
  const showRangeBand = ref(true);
  const chartPointCount = ref(TRACK_DETAILS_CHART_POINTS_DEFAULT);
  const miniMapHeight = ref(TRACK_DETAIL_MINI_MAP_HEIGHT_DEFAULT);
  const hasMiniMapHeightPreference = ref(false);

  function hydrate(force = false): void {
    if (hydrated.value && !force) return;

    const stored = readJsonStorage<PersistedTrackDetailsPreferences>(
      STORAGE_KEYS.trackDetailsPreferences,
      {},
      (value) => (isRecord(value) ? value : {})
    );
    const hadStoredPreferences = Object.keys(stored).length > 0;

    graphHeightPx.value = sanitizeGraphHeight(stored.graphHeightPx);
    showRangeBand.value = sanitizeBoolean(stored.showRangeBand, true);
    chartPointCount.value = sanitizeChartPointCount(stored.chartPointCount);
    hasMiniMapHeightPreference.value = isValidMiniMapHeight(stored.miniMapHeight);
    miniMapHeight.value = hasMiniMapHeightPreference.value
      ? sanitizeMiniMapHeight(stored.miniMapHeight)
      : TRACK_DETAIL_MINI_MAP_HEIGHT_DEFAULT;

    hydrated.value = true;

    if (hadStoredPreferences) {
      persistPreferences();
    }
  }

  function setGraphHeight(value: number): number {
    graphHeightPx.value = sanitizeGraphHeight(value);
    persistPreferences();
    return graphHeightPx.value;
  }

  function setShowRangeBand(enabled: boolean): void {
    showRangeBand.value = enabled;
    persistPreferences();
  }

  function toggleRangeBand(): boolean {
    setShowRangeBand(!showRangeBand.value);
    return showRangeBand.value;
  }

  function setChartPointCount(value: number): number {
    chartPointCount.value = sanitizeChartPointCount(value);
    persistPreferences();
    return chartPointCount.value;
  }

  function setChartPointCountFromSliderValue(sliderValue: number): number {
    return setChartPointCount(trackDetailsChartPointSliderValueToCount(sliderValue));
  }

  function ensureMiniMapHeight(defaultHeight: number): number {
    if (!hasMiniMapHeightPreference.value) {
      miniMapHeight.value = sanitizeMiniMapHeight(defaultHeight);
    }
    return miniMapHeight.value;
  }

  function setMiniMapHeight(value: number): number {
    miniMapHeight.value = sanitizeMiniMapHeight(value);
    hasMiniMapHeightPreference.value = true;
    persistPreferences();
    return miniMapHeight.value;
  }

  function reset(): void {
    graphHeightPx.value = TRACK_DETAIL_GRAPH_HEIGHT_DEFAULT;
    showRangeBand.value = true;
    chartPointCount.value = TRACK_DETAILS_CHART_POINTS_DEFAULT;
    miniMapHeight.value = TRACK_DETAIL_MINI_MAP_HEIGHT_DEFAULT;
    hasMiniMapHeightPreference.value = false;
    persistPreferences();
  }

  function persistPreferences(): void {
    const preferences: {
      graphHeightPx: number;
      showRangeBand: boolean;
      chartPointCount: number;
      miniMapHeight?: number;
    } = {
      graphHeightPx: graphHeightPx.value,
      showRangeBand: showRangeBand.value,
      chartPointCount: chartPointCount.value,
    };

    if (hasMiniMapHeightPreference.value) {
      preferences.miniMapHeight = miniMapHeight.value;
    }

    writeJsonStorage(STORAGE_KEYS.trackDetailsPreferences, preferences);
  }

  hydrate();

  return {
    hydrated,
    graphHeightPx,
    showRangeBand,
    chartPointCount,
    miniMapHeight,
    hasMiniMapHeightPreference,
    hydrate,
    ensureMiniMapHeight,
    setGraphHeight,
    setShowRangeBand,
    toggleRangeBand,
    setChartPointCount,
    setChartPointCountFromSliderValue,
    setMiniMapHeight,
    reset,
  };
});

function sanitizeGraphHeight(value: unknown): number {
  return clampNumber(
    value,
    TRACK_DETAIL_GRAPH_HEIGHT_MIN,
    TRACK_DETAIL_GRAPH_HEIGHT_MAX,
    TRACK_DETAIL_GRAPH_HEIGHT_DEFAULT
  );
}

function sanitizeMiniMapHeight(value: unknown): number {
  return clampNumber(
    value,
    TRACK_DETAIL_MINI_MAP_HEIGHT_MIN,
    TRACK_DETAIL_MINI_MAP_HEIGHT_MAX,
    TRACK_DETAIL_MINI_MAP_HEIGHT_DEFAULT
  );
}

function sanitizeChartPointCount(value: unknown): number {
  const numeric = Number(value);
  if (
    !Number.isFinite(numeric) ||
    numeric < TRACK_DETAILS_CHART_POINTS_MIN ||
    numeric > TRACK_DETAILS_CHART_POINTS_MAX
  ) {
    return TRACK_DETAILS_CHART_POINTS_DEFAULT;
  }
  return roundToNiceTrackDetailsChartPointCount(numeric);
}

function sanitizeBoolean(value: unknown, fallback: boolean): boolean {
  return typeof value === 'boolean' ? value : fallback;
}

function isValidMiniMapHeight(value: unknown): boolean {
  const numeric = Number(value);
  return (
    Number.isFinite(numeric) &&
    numeric >= TRACK_DETAIL_MINI_MAP_HEIGHT_MIN &&
    numeric <= TRACK_DETAIL_MINI_MAP_HEIGHT_MAX
  );
}

function clampNumber(value: unknown, min: number, max: number, fallback: number): number {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) return fallback;
  return Math.min(max, Math.max(min, Math.round(numeric)));
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value != null && typeof value === 'object' && !Array.isArray(value);
}
