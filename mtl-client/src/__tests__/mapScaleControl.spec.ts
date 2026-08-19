import { describe, expect, it, vi } from 'vitest';
import { mapScaleUnitForMeasurementSystem, syncMapScaleControlUnit } from '@/components/map/mapScaleControl';

describe('map scale control units', () => {
  it('maps the effective measurement preference to MapLibre units', () => {
    expect(mapScaleUnitForMeasurementSystem('METRIC')).toBe('metric');
    expect(mapScaleUnitForMeasurementSystem('US_CUSTOMARY')).toBe('imperial');
  });

  it('updates an existing scale control', () => {
    const control = { setUnit: vi.fn() };

    syncMapScaleControlUnit(control, 'US_CUSTOMARY');

    expect(control.setUnit).toHaveBeenCalledWith('imperial');
  });
});
