import { computed, isReadonly, nextTick } from 'vue';
import { beforeEach, describe, expect, it } from 'vitest';
import {
  applyServerDefaultMeasurementSystem,
  detectMeasurementSystem,
  useMeasurementSystem,
} from '@/composables/useMeasurementSystem';
import { formatMeasurementPreview } from '@/utils/units';
import { STORAGE_KEYS } from '@/utils/appStorage';

const preference = useMeasurementSystem();

describe('measurement-system preference', () => {
  beforeEach(() => {
    localStorage.clear();
    applyServerDefaultMeasurementSystem(undefined);
    preference.useDefaultMeasurementSystem();
  });

  it('detects US customary from a US locale or timezone', () => {
    expect(detectMeasurementSystem({ languages: ['en-US'], timezone: 'Europe/Zurich' })).toBe('US_CUSTOMARY');
    expect(detectMeasurementSystem({ languages: ['en'], timezone: 'America/New_York' })).toBe('US_CUSTOMARY');
  });

  it('falls back to metric for other locales and unknown browser data', () => {
    expect(detectMeasurementSystem({ languages: ['de-CH'], timezone: 'Europe/Zurich' })).toBe('METRIC');
    expect(detectMeasurementSystem({ languages: [], timezone: '' })).toBe('METRIC');
  });

  it('does not let secondary US English override a region-specific primary language', () => {
    expect(detectMeasurementSystem({ languages: ['de-CH', 'de', 'en-US', 'en'], timezone: 'Europe/Zurich' })).toBe(
      'METRIC'
    );
  });

  it('uses the first secondary region when the primary language has no region', () => {
    expect(detectMeasurementSystem({ languages: ['en', 'en-US'], timezone: 'Europe/Berlin' })).toBe('US_CUSTOMARY');
    expect(detectMeasurementSystem({ languages: ['de', 'fr-CH', 'en-US'], timezone: 'Europe/Berlin' })).toBe('METRIC');
  });

  it('recognizes canonical US zones and backward aliases', () => {
    expect(detectMeasurementSystem({ languages: ['en'], timezone: 'America/North_Dakota/Beulah' })).toBe(
      'US_CUSTOMARY'
    );
    expect(detectMeasurementSystem({ languages: ['en'], timezone: 'America/Metlakatla' })).toBe('US_CUSTOMARY');
    expect(detectMeasurementSystem({ languages: ['en'], timezone: 'US/Eastern' })).toBe('US_CUSTOMARY');
    expect(detectMeasurementSystem({ languages: ['en'], timezone: 'America/Indianapolis' })).toBe('US_CUSTOMARY');
  });

  it('does not expose the writable explicit preference ref', () => {
    expect(isReadonly(preference.explicitMeasurementSystem)).toBe(true);
  });

  it('keeps the server default derived and does not persist it', () => {
    applyServerDefaultMeasurementSystem('US_CUSTOMARY');

    expect(preference.measurementSystem.value).toBe('US_CUSTOMARY');
    expect(preference.measurementPreferenceSource.value).toBe('server');
    expect(localStorage.getItem(STORAGE_KEYS.measurementSystem)).toBeNull();
  });

  it('persists an explicit choice ahead of the server default', () => {
    applyServerDefaultMeasurementSystem('METRIC');
    preference.setMeasurementSystem('US_CUSTOMARY');

    expect(preference.measurementSystem.value).toBe('US_CUSTOMARY');
    expect(preference.measurementPreferenceSource.value).toBe('explicit');
    expect(localStorage.getItem(STORAGE_KEYS.measurementSystem)).toBe('US_CUSTOMARY');
  });

  it('uses the current default again after removing the explicit choice', () => {
    applyServerDefaultMeasurementSystem('METRIC');
    preference.setMeasurementSystem('US_CUSTOMARY');

    preference.useDefaultMeasurementSystem();

    expect(preference.measurementSystem.value).toBe('METRIC');
    expect(preference.measurementPreferenceSource.value).toBe('server');
    expect(localStorage.getItem(STORAGE_KEYS.measurementSystem)).toBeNull();
  });

  it('reactively reformats canonical values without a reload', async () => {
    applyServerDefaultMeasurementSystem('METRIC');
    const preview = computed(() => formatMeasurementPreview(preference.measurementSystem.value, 'en-US'));
    expect(preview.value).toBe('25.0 km · 500 m · 80 kg');

    preference.setMeasurementSystem('US_CUSTOMARY');
    await nextTick();

    expect(preview.value).toBe('15.5 mi · 1,640 ft · 176 lb');
  });
});
