import { getFormatLocale } from '@/composables/useLocale';
import { MeasurementSystem as ApiMeasurementSystem } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/MeasurementSystem';

export type MeasurementSystem = ApiMeasurementSystem;
export const MEASUREMENT_SYSTEMS: readonly MeasurementSystem[] = Object.values(ApiMeasurementSystem);
export type MeasurementDisplayUnit = 'km' | 'm' | 'km/h' | 'm/h' | 'kg' | 'mi' | 'ft' | 'mph' | 'ft/h' | 'lb';

export interface MeasurementDisplayProfile {
  longDistance: MeasurementDisplayUnit;
  shortDistance: MeasurementDisplayUnit;
  elevation: MeasurementDisplayUnit;
  speed: MeasurementDisplayUnit;
  verticalRate: MeasurementDisplayUnit;
  mass: MeasurementDisplayUnit;
}

export interface MeasurementFormatOptions {
  fractionDigits?: number;
  locale?: string;
}

export const METERS_PER_KILOMETER = 1000;
export const METERS_PER_MILE = 1609.344;
export const METERS_PER_FOOT = 0.3048;
export const KILOMETERS_PER_MILE = 1.609344;
export const KILOGRAMS_PER_POUND = 0.45359237;

export const MEASUREMENT_DISPLAY_PROFILES: Readonly<Record<MeasurementSystem, MeasurementDisplayProfile>> = {
  METRIC: {
    longDistance: 'km',
    shortDistance: 'm',
    elevation: 'm',
    speed: 'km/h',
    verticalRate: 'm/h',
    mass: 'kg',
  },
  US_CUSTOMARY: {
    longDistance: 'mi',
    shortDistance: 'ft',
    elevation: 'ft',
    speed: 'mph',
    verticalRate: 'ft/h',
    mass: 'lb',
  },
};

export function isMeasurementSystem(value: unknown): value is MeasurementSystem {
  return typeof value === 'string' && MEASUREMENT_SYSTEMS.includes(value as MeasurementSystem);
}

export function kilometersToMeters(kilometers: number): number {
  return kilometers * METERS_PER_KILOMETER;
}

export function metersToKilometers(meters: number): number {
  return meters / METERS_PER_KILOMETER;
}

export function milesToMeters(miles: number): number {
  return miles * METERS_PER_MILE;
}

export function metersToMiles(meters: number): number {
  return meters / METERS_PER_MILE;
}

export function feetToMeters(feet: number): number {
  return feet * METERS_PER_FOOT;
}

export function metersToFeet(meters: number): number {
  return meters / METERS_PER_FOOT;
}

export function milesPerHourToKilometersPerHour(milesPerHour: number): number {
  return milesPerHour * KILOMETERS_PER_MILE;
}

export function kilometersPerHourToMilesPerHour(kilometersPerHour: number): number {
  return kilometersPerHour / KILOMETERS_PER_MILE;
}

export function poundsToKilograms(pounds: number): number {
  return pounds * KILOGRAMS_PER_POUND;
}

export function kilogramsToPounds(kilograms: number): number {
  return kilograms / KILOGRAMS_PER_POUND;
}

export function longDistanceDisplayValue(distanceM: number, system: MeasurementSystem): number {
  return system === 'METRIC' ? metersToKilometers(distanceM) : metersToMiles(distanceM);
}

export function longDistanceCanonicalValue(displayValue: number, system: MeasurementSystem): number {
  return system === 'METRIC' ? kilometersToMeters(displayValue) : milesToMeters(displayValue);
}

export function shortDistanceDisplayValue(distanceM: number, system: MeasurementSystem): number {
  return system === 'METRIC' ? distanceM : metersToFeet(distanceM);
}

export function shortDistanceCanonicalValue(displayValue: number, system: MeasurementSystem): number {
  return system === 'METRIC' ? displayValue : feetToMeters(displayValue);
}

export function elevationDisplayValue(elevationM: number, system: MeasurementSystem): number {
  return shortDistanceDisplayValue(elevationM, system);
}

export function elevationCanonicalValue(displayValue: number, system: MeasurementSystem): number {
  return shortDistanceCanonicalValue(displayValue, system);
}

export function speedDisplayValue(speedKmh: number, system: MeasurementSystem): number {
  return system === 'METRIC' ? speedKmh : kilometersPerHourToMilesPerHour(speedKmh);
}

export function speedCanonicalValue(displayValue: number, system: MeasurementSystem): number {
  return system === 'METRIC' ? displayValue : milesPerHourToKilometersPerHour(displayValue);
}

export function verticalRateDisplayValue(verticalRateMPerH: number, system: MeasurementSystem): number {
  return system === 'METRIC' ? verticalRateMPerH : metersToFeet(verticalRateMPerH);
}

export function verticalRateCanonicalValue(displayValue: number, system: MeasurementSystem): number {
  return system === 'METRIC' ? displayValue : feetToMeters(displayValue);
}

export function massDisplayValue(massKg: number, system: MeasurementSystem): number {
  return system === 'METRIC' ? massKg : kilogramsToPounds(massKg);
}

export function massCanonicalValue(displayValue: number, system: MeasurementSystem): number {
  return system === 'METRIC' ? displayValue : poundsToKilograms(displayValue);
}

export function formatLongDistanceM(
  distanceM: number,
  system: MeasurementSystem,
  options: MeasurementFormatOptions = {}
): string {
  return formatWithUnit(
    longDistanceDisplayValue(distanceM, system),
    MEASUREMENT_DISPLAY_PROFILES[system].longDistance,
    options
  );
}

export function formatShortDistanceM(
  distanceM: number,
  system: MeasurementSystem,
  options: MeasurementFormatOptions = {}
): string {
  return formatWithUnit(
    shortDistanceDisplayValue(distanceM, system),
    MEASUREMENT_DISPLAY_PROFILES[system].shortDistance,
    options
  );
}

export function formatElevationM(
  elevationM: number,
  system: MeasurementSystem,
  options: MeasurementFormatOptions = {}
): string {
  return formatWithUnit(
    elevationDisplayValue(elevationM, system),
    MEASUREMENT_DISPLAY_PROFILES[system].elevation,
    options
  );
}

export function formatSpeedKmh(
  speedKmh: number,
  system: MeasurementSystem,
  options: MeasurementFormatOptions = {}
): string {
  return formatWithUnit(speedDisplayValue(speedKmh, system), MEASUREMENT_DISPLAY_PROFILES[system].speed, options);
}

export function formatVerticalRateMPerH(
  verticalRateMPerH: number,
  system: MeasurementSystem,
  options: MeasurementFormatOptions = {}
): string {
  return formatWithUnit(
    verticalRateDisplayValue(verticalRateMPerH, system),
    MEASUREMENT_DISPLAY_PROFILES[system].verticalRate,
    options
  );
}

export function formatMassKg(
  massKg: number,
  system: MeasurementSystem,
  options: MeasurementFormatOptions = {}
): string {
  return formatWithUnit(massDisplayValue(massKg, system), MEASUREMENT_DISPLAY_PROFILES[system].mass, options);
}

export function formatMeasurementPreview(system: MeasurementSystem, locale?: string): string {
  return [
    formatLongDistanceM(25_000, system, { fractionDigits: 1, locale }),
    formatElevationM(500, system, { fractionDigits: 0, locale }),
    formatMassKg(80, system, { fractionDigits: 0, locale }),
  ].join(' · ');
}

function formatWithUnit(value: number, unit: MeasurementDisplayUnit, options: MeasurementFormatOptions): string {
  if (!Number.isFinite(value)) return '—';
  const fractionDigits = options.fractionDigits ?? 0;
  const formatted = new Intl.NumberFormat(options.locale ?? getFormatLocale(), {
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
  }).format(value);
  return `${formatted} ${unit}`;
}
