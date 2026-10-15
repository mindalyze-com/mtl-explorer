import { computed, onScopeDispose, ref, watch } from 'vue';
import { readJsonStorage, STORAGE_KEYS, writeJsonStorage } from '@/utils/appStorage';

export const TREND_CHART_HEIGHT_DEFAULT = 190;
export const TREND_CHART_HEIGHT_MIN = 160;
export const TREND_CHART_HEIGHT_STEP = 10;
export const TREND_CHART_ORDER = [
  'duration',
  'distance',
  'activity',
  'energy',
  'intensity',
  'trainingLoad',
  'exploration',
  'media',
] as const;
export type TrendChartId = (typeof TREND_CHART_ORDER)[number];
export type TrendChartChoice = { id: TrendChartId; label: string; icon: string };
type TrendChartPreferences = { height: number; order: TrendChartId[] };

function defaults(): TrendChartPreferences {
  return { height: TREND_CHART_HEIGHT_DEFAULT, order: [...TREND_CHART_ORDER] };
}

function normalizeHeight(value: unknown): number {
  if (typeof value !== 'number' || !Number.isFinite(value)) return TREND_CHART_HEIGHT_DEFAULT;
  const stepped = Math.round(value / TREND_CHART_HEIGHT_STEP) * TREND_CHART_HEIGHT_STEP;
  return Math.max(TREND_CHART_HEIGHT_MIN, stepped);
}

function visibleChartHeightLimit(): number {
  const viewportHeight =
    typeof window === 'undefined'
      ? TREND_CHART_HEIGHT_DEFAULT
      : Math.min(window.innerHeight, window.visualViewport?.height ?? window.innerHeight);
  // Keep the slider's maximum reachable with its normal step size.
  return Math.max(
    TREND_CHART_HEIGHT_MIN,
    Math.floor(viewportHeight / TREND_CHART_HEIGHT_STEP) * TREND_CHART_HEIGHT_STEP
  );
}

function normalizePreferences(value: unknown): TrendChartPreferences {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return defaults();
  const stored = value as Record<string, unknown>;
  const order = Array.isArray(stored.order)
    ? stored.order.filter((id): id is TrendChartId => TREND_CHART_ORDER.includes(id))
    : [];
  return { height: normalizeHeight(stored.height), order: [...new Set([...order, ...TREND_CHART_ORDER])] };
}

export function useTrendChartPreferences() {
  const preferences = ref(readJsonStorage(STORAGE_KEYS.trendChartPreferences, defaults(), normalizePreferences));
  const maxHeight = ref(visibleChartHeightLimit());
  const height = computed(() => Math.min(preferences.value.height, maxHeight.value));
  const order = computed(() => preferences.value.order);
  const isDefault = computed(
    () =>
      preferences.value.height === TREND_CHART_HEIGHT_DEFAULT &&
      order.value.every((id, index) => id === TREND_CHART_ORDER[index])
  );

  function updateHeightLimit(): void {
    maxHeight.value = visibleChartHeightLimit();
  }

  if (typeof window !== 'undefined') {
    const viewport = window.visualViewport;
    window.addEventListener('resize', updateHeightLimit);
    viewport?.addEventListener('resize', updateHeightLimit);
    onScopeDispose(() => {
      window.removeEventListener('resize', updateHeightLimit);
      viewport?.removeEventListener('resize', updateHeightLimit);
    });
  }

  watch(preferences, (value) => writeJsonStorage(STORAGE_KEYS.trendChartPreferences, value), { deep: true });

  function setHeight(value: number | number[]): void {
    preferences.value.height = Math.min(normalizeHeight(value), maxHeight.value);
  }

  function moveChart(id: TrendChartId, direction: -1 | 1, visibleIds: TrendChartId[]): void {
    const index = visibleIds.indexOf(id);
    if (index < 0) return;
    const neighbor = visibleIds[index + direction];
    if (!neighbor) return;
    const next = [...order.value];
    const from = next.indexOf(id);
    const to = next.indexOf(neighbor);
    [next[from], next[to]] = [neighbor, id];
    preferences.value.order = next;
  }

  function reset(): void {
    preferences.value = defaults();
  }

  return { height, maxHeight, order, isDefault, setHeight, moveChart, reset };
}
