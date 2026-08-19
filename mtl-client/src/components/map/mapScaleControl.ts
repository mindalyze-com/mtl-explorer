import type { MeasurementSystem } from '@/utils/units';

export type MapScaleUnit = 'imperial' | 'metric';

export interface MapScaleControlLike {
  setUnit(unit: MapScaleUnit): void;
}

export function mapScaleUnitForMeasurementSystem(system: MeasurementSystem): MapScaleUnit {
  return system === 'US_CUSTOMARY' ? 'imperial' : 'metric';
}

export function syncMapScaleControlUnit(
  control: MapScaleControlLike | null | undefined,
  system: MeasurementSystem
): void {
  control?.setUnit(mapScaleUnitForMeasurementSystem(system));
}
