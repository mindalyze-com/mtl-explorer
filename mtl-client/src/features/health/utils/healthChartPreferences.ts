import type { GHGroupedSeries, GHGroupedMetrics } from '../api/googleHealthApi';
import { readStorage, writeStorage, readJsonStorage, writeJsonStorage, STORAGE_KEYS } from '@/utils/appStorage';

export type HealthGrouping = NonNullable<GHGroupedMetrics['requestedGrouping']>;
export type HealthChartMode = 'SUMMARY' | 'RANGE';
export type HealthChartTimeWindow = { start: number; end: number };
export const HEALTH_GROUPINGS: { value: HealthGrouping; label: string }[] = [
  { value: 'AUTO', label: 'Auto' },
  { value: 'SECOND', label: 'Intraday' },
  { value: 'DAY', label: 'Day' },
  { value: 'WEEK', label: 'Week' },
  { value: 'MONTH', label: 'Month' },
  { value: 'YEAR', label: 'Year' },
];

export function readHealthGrouping(): HealthGrouping {
  const value = readStorage(STORAGE_KEYS.healthGrouping);
  return HEALTH_GROUPINGS.find((item) => item.value === value)?.value ?? 'AUTO';
}

export function saveHealthGrouping(value: HealthGrouping) {
  writeStorage(STORAGE_KEYS.healthGrouping, value);
}

export function readHealthModes(): Record<string, HealthChartMode> {
  return readJsonStorage(STORAGE_KEYS.healthChartModes, {}, (value) => {
    if (!value || typeof value !== 'object' || Array.isArray(value)) return {};
    return Object.fromEntries(Object.entries(value).filter(([, mode]) => mode === 'SUMMARY' || mode === 'RANGE'));
  });
}

export function saveHealthModes(value: Record<string, HealthChartMode>) {
  writeJsonStorage(STORAGE_KEYS.healthChartModes, value);
}

export function defaultHealthMode(series: GHGroupedSeries): HealthChartMode {
  return series.detailedHeartRate ? 'RANGE' : 'SUMMARY';
}
