import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { useLocale } from '@/composables/useLocale';
import { useMeasurementSystem } from '@/composables/useMeasurementSystem';
import { useAsyncState } from '@/composables/useAsyncState';
import {
  formatCompactDistance,
  formatCompactDurationSeconds,
  formatDistanceSmart,
  formatNumber,
  formatRadius,
  metersPerSecondToKilometersPerHour,
} from '@/utils/Utils';

const { setLocale } = useLocale();
const measurementPreference = useMeasurementSystem();

describe('locale-aware formatting', () => {
  beforeEach(() => {
    measurementPreference.setMeasurementSystem('METRIC');
  });

  afterEach(() => {
    setLocale('');
  });

  it('uses the selected locale for fixed precision numbers', () => {
    setLocale('de-DE');

    expect(formatNumber(94.26, 2)).toBe('94,26');
  });

  it('uses the selected locale for smart distance decimals', () => {
    setLocale('de-DE');

    expect(formatDistanceSmart(3600)).toBe('3,60 km');
  });

  it('formats compact event distances, durations, and radii', () => {
    setLocale('en-US');

    expect(formatCompactDistance(950.4)).toBe('950 m');
    expect(formatCompactDistance(1500)).toBe('1.50 km');
    expect(formatCompactDurationSeconds(65)).toBe('1m 05s');
    expect(formatCompactDurationSeconds(3665)).toBe('1h 01m');
    expect(formatRadius(1500)).toBe('1.5 km');
  });

  it('formats shared application distances in US customary units', () => {
    setLocale('en-US');
    measurementPreference.setMeasurementSystem('US_CUSTOMARY');

    expect(formatDistanceSmart(3600)).toBe('2.24 mi');
    expect(formatCompactDistance(950.4)).toBe('3,118 ft');
    expect(formatRadius(1500)).toBe('4,921 ft');
  });

  it('converts metres per second to kilometres per hour', () => {
    expect(metersPerSecondToKilometersPerHour(10)).toBe(36);
  });

  it('provides reusable loading and error state', () => {
    const state = useAsyncState<string | null>(null);

    state.error.value = 'failed';
    state.begin();
    expect(state.loading.value).toBe(true);
    expect(state.error.value).toBeNull();

    state.error.value = 'failed';
    state.resetError();
    state.finish();
    expect(state.loading.value).toBe(false);
    expect(state.error.value).toBeNull();
  });
});
