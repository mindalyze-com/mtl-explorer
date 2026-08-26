import { describe, expect, it } from 'vitest';
import {
  filterParamCanonicalValue,
  filterParamDisplayUnit,
  filterParamDisplayValue,
  formatFilterParamSummaryValue,
} from '@/utils/filterParamUnits';

describe('filter parameter measurement units', () => {
  it('keeps metric request values and units unchanged', () => {
    expect(filterParamDisplayUnit('km', 'METRIC')).toBe('km');
    expect(filterParamDisplayValue('12.500', 'km', 'METRIC')).toBe('12.500');
    expect(filterParamCanonicalValue('12.500', 'km', 'METRIC')).toBe('12.500');
  });

  it('converts supported canonical units for US customary input', () => {
    expect(filterParamDisplayUnit('km', 'US_CUSTOMARY')).toBe('mi');
    expect(filterParamDisplayUnit('m', 'US_CUSTOMARY')).toBe('ft');
    expect(filterParamDisplayUnit('km/h', 'US_CUSTOMARY')).toBe('mph');
    expect(filterParamDisplayUnit('m/h', 'US_CUSTOMARY')).toBe('ft/h');
    expect(filterParamDisplayUnit('kg', 'US_CUSTOMARY')).toBe('lb');

    expect(Number(filterParamDisplayValue('1', 'km', 'US_CUSTOMARY'))).toBeCloseTo(0.621371, 6);
    expect(Number(filterParamDisplayValue('500', 'm', 'US_CUSTOMARY'))).toBeCloseTo(1640.42, 2);
    expect(Number(filterParamDisplayValue('16.09344', 'km/h', 'US_CUSTOMARY'))).toBeCloseTo(10, 10);
  });

  it('converts edited US customary values back to canonical request units', () => {
    expect(Number(filterParamCanonicalValue('10', 'km', 'US_CUSTOMARY'))).toBeCloseTo(16.09344, 10);
    expect(Number(filterParamCanonicalValue('5280', 'm', 'US_CUSTOMARY'))).toBeCloseTo(1609.344, 10);
    expect(Number(filterParamCanonicalValue('10', 'km/h', 'US_CUSTOMARY'))).toBeCloseTo(16.09344, 10);
  });

  it('preserves empty, invalid, and unrelated values', () => {
    expect(filterParamDisplayValue('', 'km', 'US_CUSTOMARY')).toBe('');
    expect(filterParamCanonicalValue('', 'km', 'US_CUSTOMARY')).toBe('');
    expect(filterParamDisplayValue('not-a-number', 'km', 'US_CUSTOMARY')).toBe('not-a-number');
    expect(filterParamDisplayUnit('kWh', 'US_CUSTOMARY')).toBe('kWh');
    expect(filterParamDisplayValue('12.5', 'kWh', 'US_CUSTOMARY')).toBe('12.5');
  });

  it('formats canonical filter values as localized measurement-aware summaries', () => {
    expect(formatFilterParamSummaryValue('16.09344', 'km', 'US_CUSTOMARY', 'de-DE')).toBe('10 mi');
    expect(formatFilterParamSummaryValue('16.09344', 'km', 'METRIC', 'de-DE')).toBe('16,09 km');
  });
});
