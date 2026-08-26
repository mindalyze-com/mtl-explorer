import type { MeasurementSystem } from '@/utils/units';
import { getFormatLocale } from '@/composables/useLocale';
import {
  elevationCanonicalValue,
  elevationDisplayValue,
  KILOMETERS_PER_MILE,
  kilogramsToPounds,
  MEASUREMENT_DISPLAY_PROFILES,
  milesPerHourToKilometersPerHour,
  poundsToKilograms,
  speedDisplayValue,
  verticalRateCanonicalValue,
  verticalRateDisplayValue,
} from '@/utils/units';

type CanonicalFilterUnit = 'km' | 'm' | 'km/h' | 'm/h' | 'kg';

const FILTER_INPUT_SIGNIFICANT_DIGITS = 12;
const FILTER_SUMMARY_MAXIMUM_FRACTION_DIGITS = 2;

export function filterParamDisplayUnit(unit: string | undefined, system: MeasurementSystem): string | undefined {
  const canonicalUnit = supportedCanonicalUnit(unit);
  if (!canonicalUnit || system === 'METRIC') return unit;

  switch (canonicalUnit) {
    case 'km':
      return MEASUREMENT_DISPLAY_PROFILES[system].longDistance;
    case 'm':
      return MEASUREMENT_DISPLAY_PROFILES[system].elevation;
    case 'km/h':
      return MEASUREMENT_DISPLAY_PROFILES[system].speed;
    case 'm/h':
      return MEASUREMENT_DISPLAY_PROFILES[system].verticalRate;
    case 'kg':
      return MEASUREMENT_DISPLAY_PROFILES[system].mass;
  }
}

export function filterParamDisplayValue(
  canonicalValue: string,
  unit: string | undefined,
  system: MeasurementSystem
): string {
  if (system === 'METRIC') return canonicalValue;
  const value = finiteInputNumber(canonicalValue);
  const canonicalUnit = supportedCanonicalUnit(unit);
  if (value == null || !canonicalUnit) return canonicalValue;

  switch (canonicalUnit) {
    case 'km':
      return serializeInputNumber(value / KILOMETERS_PER_MILE);
    case 'm':
      return serializeInputNumber(elevationDisplayValue(value, system));
    case 'km/h':
      return serializeInputNumber(speedDisplayValue(value, system));
    case 'm/h':
      return serializeInputNumber(verticalRateDisplayValue(value, system));
    case 'kg':
      return serializeInputNumber(kilogramsToPounds(value));
  }
}

export function filterParamCanonicalValue(
  displayValue: string,
  unit: string | undefined,
  system: MeasurementSystem
): string {
  if (system === 'METRIC') return displayValue;
  const value = finiteInputNumber(displayValue);
  const canonicalUnit = supportedCanonicalUnit(unit);
  if (value == null || !canonicalUnit) return displayValue;

  switch (canonicalUnit) {
    case 'km':
      return serializeInputNumber(value * KILOMETERS_PER_MILE);
    case 'm':
      return serializeInputNumber(elevationCanonicalValue(value, system));
    case 'km/h':
      return serializeInputNumber(milesPerHourToKilometersPerHour(value));
    case 'm/h':
      return serializeInputNumber(verticalRateCanonicalValue(value, system));
    case 'kg':
      return serializeInputNumber(poundsToKilograms(value));
  }
}

export function formatFilterParamSummaryValue(
  canonicalValue: string,
  unit: string | undefined,
  system: MeasurementSystem,
  locale: string | undefined = getFormatLocale()
): string {
  const displayValue = filterParamDisplayValue(canonicalValue, unit, system);
  const value = finiteInputNumber(displayValue);
  if (value == null) return canonicalValue;

  const formatted = new Intl.NumberFormat(locale, {
    maximumFractionDigits: FILTER_SUMMARY_MAXIMUM_FRACTION_DIGITS,
  }).format(value);
  const displayUnit = filterParamDisplayUnit(unit, system)?.trim();
  return displayUnit ? `${formatted} ${displayUnit}` : formatted;
}

function supportedCanonicalUnit(unit: string | undefined): CanonicalFilterUnit | null {
  const normalized = unit?.trim().toLowerCase();
  switch (normalized) {
    case 'km':
    case 'm':
    case 'km/h':
    case 'm/h':
    case 'kg':
      return normalized;
    default:
      return null;
  }
}

function finiteInputNumber(value: string): number | null {
  if (!value.trim()) return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

function serializeInputNumber(value: number): string {
  return Number(value.toPrecision(FILTER_INPUT_SIGNIFICANT_DIGITS)).toString();
}
