import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { useLocale } from '@/composables/useLocale';
import {
  feetToMeters,
  formatElevationM,
  formatLongDistanceM,
  formatMassKg,
  formatMeasurementPreview,
  formatSpeedKmh,
  formatVerticalRateMPerH,
  KILOGRAMS_PER_POUND,
  KILOMETERS_PER_MILE,
  kilogramsToPounds,
  longDistanceCanonicalValue,
  longDistanceDisplayValue,
  massCanonicalValue,
  massDisplayValue,
  METERS_PER_FOOT,
  METERS_PER_KILOMETER,
  METERS_PER_MILE,
  milesToMeters,
  poundsToKilograms,
  shortDistanceCanonicalValue,
  shortDistanceDisplayValue,
  speedCanonicalValue,
  speedDisplayValue,
  verticalRateCanonicalValue,
  verticalRateDisplayValue,
} from '@/utils/units';

const { setLocale } = useLocale();

describe('measurement units', () => {
  beforeEach(() => {
    setLocale('en-US');
  });

  afterEach(() => {
    setLocale('');
  });

  it('uses the exact canonical conversion constants', () => {
    expect(METERS_PER_KILOMETER).toBe(1000);
    expect(METERS_PER_MILE).toBe(1609.344);
    expect(METERS_PER_FOOT).toBe(0.3048);
    expect(KILOMETERS_PER_MILE).toBe(1.609344);
    expect(KILOGRAMS_PER_POUND).toBe(0.45359237);

    expect(milesToMeters(100)).toBe(160_934.4);
    expect(feetToMeters(5000)).toBe(1524);
    expect(poundsToKilograms(180)).toBe(81.6466266);
  });

  it('round-trips canonical values through both display systems', () => {
    for (const system of ['METRIC', 'US_CUSTOMARY'] as const) {
      expect(longDistanceCanonicalValue(longDistanceDisplayValue(42_195, system), system)).toBeCloseTo(42_195, 10);
      expect(shortDistanceCanonicalValue(shortDistanceDisplayValue(123.45, system), system)).toBeCloseTo(123.45, 10);
      expect(speedCanonicalValue(speedDisplayValue(36, system), system)).toBeCloseTo(36, 10);
      expect(verticalRateCanonicalValue(verticalRateDisplayValue(750, system), system)).toBeCloseTo(750, 10);
      expect(massCanonicalValue(massDisplayValue(80, system), system)).toBeCloseTo(80, 10);
    }

    expect(kilogramsToPounds(poundsToKilograms(180))).toBeCloseTo(180, 10);
  });

  it('formats metric canonical values with locale-aware display units', () => {
    expect(formatLongDistanceM(25_000, 'METRIC', { fractionDigits: 1 })).toBe('25.0 km');
    expect(formatElevationM(500, 'METRIC')).toBe('500 m');
    expect(formatSpeedKmh(32.5, 'METRIC', { fractionDigits: 1 })).toBe('32.5 km/h');
    expect(formatVerticalRateMPerH(750, 'METRIC')).toBe('750 m/h');
    expect(formatMassKg(80, 'METRIC')).toBe('80 kg');
    expect(formatMeasurementPreview('METRIC')).toBe('25.0 km · 500 m · 80 kg');
  });

  it('formats the same canonical values in US customary units', () => {
    expect(formatLongDistanceM(25_000, 'US_CUSTOMARY', { fractionDigits: 1 })).toBe('15.5 mi');
    expect(formatElevationM(500, 'US_CUSTOMARY')).toBe('1,640 ft');
    expect(formatSpeedKmh(100, 'US_CUSTOMARY', { fractionDigits: 1 })).toBe('62.1 mph');
    expect(formatVerticalRateMPerH(750, 'US_CUSTOMARY')).toBe('2,461 ft/h');
    expect(formatMassKg(80, 'US_CUSTOMARY')).toBe('176 lb');
    expect(formatMeasurementPreview('US_CUSTOMARY')).toBe('15.5 mi · 1,640 ft · 176 lb');
  });
});
