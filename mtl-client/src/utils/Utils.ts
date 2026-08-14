import colormap from 'colormap';
import _ from 'lodash';
import { getFormatLocale } from '@/composables/useLocale';
import { getMeasurementSystem } from '@/composables/useMeasurementSystem';
import {
  formatElevationM,
  formatLongDistanceM,
  formatMassKg,
  formatShortDistanceM,
  formatSpeedKmh,
  formatVerticalRateMPerH,
  MEASUREMENT_DISPLAY_PROFILES,
  METERS_PER_KILOMETER,
  METERS_PER_MILE,
} from '@/utils/units';

const SECONDS_PER_MINUTE = 60;
const SECONDS_PER_HOUR = 3600;
const MPS_TO_KMH = 3.6;

export const EVENT_MEASURE_BETWEEN_POINTS_DIALOG_MAXIMIZED_EVENT =
  'EVENT_MEASURE_BETWEEN_POINTS_DIALOG_MAXIMIZED_EVENT';

export function formatDuration(durationInMillis: number) {
  // seems to be tricky with date-fns... hence, do it manually
  // let duration = intervalToDuration({start: 0, end: durationInMillis / 1000});
  // let humanDuration = formatDuration(duration, {format: ["hours", "minutes", "seconds"]});
  // humanDuration = `${duration.hours.toString().padStart(2, "0")}:${duration.minutes.toString().padStart(2, "0")}:${duration.seconds.toString().padStart(2, "0")}`;
  const hours = Math.floor(durationInMillis / 3600000);
  const minutes = Math.floor((durationInMillis % 3600000) / 60000);
  const seconds = Math.floor((durationInMillis % 60000) / 1000);

  // Format the time string as "hh:mm:ss"
  return `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
}

export function formatDateAndTime(date: Date | string | number | null | undefined) {
  return formatDateValue(date, {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  });
}

export function formatDateAndTimeWithSeconds(date: Date | string | number | null | undefined) {
  return formatDateValue(date, {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  });
}

export function toValidDate(value: Date | string | number | null | undefined): Date | null {
  if (value == null) return null;
  const date = value instanceof Date ? value : new Date(value);
  return Number.isFinite(date.getTime()) ? date : null;
}

export function toValidDateMs(value: Date | string | number | null | undefined): number | null {
  return toValidDate(value)?.getTime() ?? null;
}

export function formatNumber(num: number | null | undefined, digits: number): string {
  return num == null ? '' : formatLocaleNumber(num, digits);
}

export function formatDate(date: Date | string | number | null | undefined) {
  return formatDateValue(date, {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  });
}

function formatDateValue(date: Date | string | number | null | undefined, options: Intl.DateTimeFormatOptions): string {
  if (!date) return '';
  if (!(date instanceof Date)) date = new Date(date);
  if (isNaN(date.getTime())) return '';
  return date.toLocaleString(getFormatLocale(), options);
}

/** Short date with abbreviated month: "4. Apr. 2026" / "Apr 4, 2026" depending on locale */
export function formatDateShort(date: Date | string | number | null | undefined) {
  if (!date) return '';
  if (!(date instanceof Date)) date = new Date(date);
  if (isNaN(date.getTime())) return '';
  return date.toLocaleDateString(getFormatLocale(), { year: 'numeric', month: 'short', day: 'numeric' });
}

/** Compact date for tight columns: "04.04.26" / "04/04/26" depending on locale */
export function formatDateCompact(date: Date | string | number | null | undefined) {
  if (!date) return '';
  if (!(date instanceof Date)) date = new Date(date);
  if (isNaN(date.getTime())) return '';
  return new Intl.DateTimeFormat(getFormatLocale(), {
    day: '2-digit',
    month: '2-digit',
    year: '2-digit',
  }).format(date);
}

/** Locale-aware number formatting: e.g. 12'345.67 (de-CH) or 12,345.67 (en-US) */
export function formatLocaleNumber(num: number, fractionDigits?: number): string {
  if (num == null || isNaN(num)) return '—';
  const opts: Intl.NumberFormatOptions = {};
  if (fractionDigits !== undefined) {
    opts.minimumFractionDigits = fractionDigits;
    opts.maximumFractionDigits = fractionDigits;
  }
  return num.toLocaleString(getFormatLocale(), opts);
}

export function formatBytes(bytes: number, decimals = 2) {
  if (bytes === 0) return '0 Bytes';

  const k = 1024;
  const dm = decimals < 0 ? 0 : decimals;
  const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB', 'PB', 'EB', 'ZB', 'YB'];

  const i = Math.floor(Math.log(bytes) / Math.log(k));

  return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i];
}

export function formatDistance(meters: number, decimals = 2) {
  if (!Number.isFinite(meters)) return undefined;
  const system = getMeasurementSystem();
  if (system === 'METRIC' && meters < 1) return formatLocaleNumber(meters * 100, decimals) + ' cm';
  if (meters < longDistanceThresholdM(system)) {
    return formatShortDistanceM(meters, system, { fractionDigits: decimals });
  }
  return formatLongDistanceM(meters, system, { fractionDigits: decimals });
}

export function formatRadius(meters: number): string {
  const system = getMeasurementSystem();
  return meters >= longDistanceThresholdM(system)
    ? formatLongDistanceM(meters, system, { fractionDigits: 1 })
    : formatShortDistanceM(meters, system);
}

export function formatCompactDistance(meters: number): string {
  if (!Number.isFinite(meters)) return '';
  const system = getMeasurementSystem();
  return meters < longDistanceThresholdM(system)
    ? formatShortDistanceM(meters, system)
    : formatLongDistanceM(meters, system, { fractionDigits: 2 });
}

export function formatElevation(meters: number, decimals = 0): string {
  return formatElevationM(meters, getMeasurementSystem(), { fractionDigits: decimals });
}

export function formatSpeed(kilometersPerHour: number, decimals = 1): string {
  return formatSpeedKmh(kilometersPerHour, getMeasurementSystem(), { fractionDigits: decimals });
}

export function formatVerticalRate(metersPerHour: number, decimals = 0): string {
  return formatVerticalRateMPerH(metersPerHour, getMeasurementSystem(), { fractionDigits: decimals });
}

export function formatMass(kilograms: number, decimals = 1): string {
  return formatMassKg(kilograms, getMeasurementSystem(), { fractionDigits: decimals });
}

export function currentMeasurementUnit(dimension: keyof (typeof MEASUREMENT_DISPLAY_PROFILES)['METRIC']): string {
  return MEASUREMENT_DISPLAY_PROFILES[getMeasurementSystem()][dimension];
}

export function formatCompactDurationSeconds(seconds: number): string {
  if (!Number.isFinite(seconds)) return '';

  const rounded = Math.round(seconds);
  const hours = Math.floor(rounded / SECONDS_PER_HOUR);
  const minutes = Math.floor((rounded % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE);
  const remainingSeconds = rounded % SECONDS_PER_MINUTE;
  if (hours > 0) return `${hours}h ${minutes.toString().padStart(2, '0')}m`;
  if (minutes <= 0) return `${remainingSeconds}s`;
  return `${minutes}m ${remainingSeconds.toString().padStart(2, '0')}s`;
}

export function metersPerSecondToKilometersPerHour(metersPerSecond: number): number {
  return metersPerSecond * MPS_TO_KMH;
}

/**
 * Smart distance formatter: precision is driven by the largest value in context
 * (pass maxMeters when formatting a table column so all cells share the same precision).
 * Numbers ≥ 1'000 km get a ' thousand separator (de-CH locale).
 *
 * @param meters     The value to format.
 * @param maxMeters  The largest value in the same context (defaults to meters itself).
 */
export function formatDistanceSmart(meters: number, maxMeters?: number): string {
  if (meters == null || isNaN(meters)) return '—';
  const system = getMeasurementSystem();
  const ref = maxMeters ?? meters;
  if (ref < longDistanceThresholdM(system)) {
    return formatShortDistanceM(meters, system, { fractionDigits: 2 });
  }
  const referenceLongDistance = system === 'METRIC' ? ref / METERS_PER_KILOMETER : ref / METERS_PER_MILE;
  const decimals = referenceLongDistance < 10 ? 2 : referenceLongDistance < 100 ? 1 : 0;
  return formatLongDistanceM(meters, system, { fractionDigits: decimals });
}

/**
 * Smart duration formatter: drops the smallest unit that doesn't matter given
 * the scale of values in context.
 *
 *   ref ≥ 24 h →  Xd Yh  (days + hours)
 *   ref ≥ 1 h  →  hh:mm  (seconds omitted)
 *   ref < 1 h  →  mm:ss
 *
 * When days or hours ≥ 1'000 the part gets a ' thousand separator (de-CH locale).
 *
 * @param millis     The value to format (milliseconds).
 * @param maxMillis  The largest value in the same context (defaults to millis itself).
 */
export function formatDurationSmart(millis: number, maxMillis?: number): string {
  if (millis == null || isNaN(millis)) return '—';
  const ref = maxMillis ?? millis;
  const pad = (n: number) => n.toString().padStart(2, '0');

  if (ref >= 86_400_000) {
    // Show days + hours
    const totalHours = Math.floor(millis / 3_600_000);
    const days = Math.floor(totalHours / 24);
    const hours = totalHours % 24;
    const daysStr = days >= 1000 ? days.toLocaleString(getFormatLocale()) : String(days);
    return `${daysStr}d ${pad(hours)}h`;
  } else if (ref >= 3_600_000) {
    // Show Xh Ym
    const totalMinutes = Math.floor(millis / 60_000);
    const hours = Math.floor(totalMinutes / 60);
    const minutes = totalMinutes % 60;
    const hoursStr = hours >= 1000 ? hours.toLocaleString(getFormatLocale()) : String(hours);
    return `${hoursStr}h ${pad(minutes)}m`;
  } else {
    // Show Xm Ys
    const totalSeconds = Math.floor(millis / 1000);
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    return `${minutes}m ${pad(seconds)}s`;
  }
}

/**
 * Human-readable duration for tooltips — always includes explicit unit labels
 * so the reader knows exactly what the numbers mean.
 *
 *   e.g. "14'044 h 27 min 0 s"  or  "32 min 15 s"  or  "45 s"
 */
export function formatDurationTooltip(millis: number): string {
  if (millis == null || isNaN(millis)) return '—';
  const totalSeconds = Math.floor(millis / 1000);
  const totalHours = Math.floor(totalSeconds / 3600);
  const days = Math.floor(totalHours / 24);
  const hours = totalHours % 24;
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;

  const daysStr = days >= 1000 ? days.toLocaleString(getFormatLocale()) : String(days);

  if (days > 0) return `${daysStr} d ${hours} h ${minutes} min`;
  if (totalHours > 0) return `${totalHours} h ${minutes} min ${seconds} s`;
  if (minutes > 0) return `${minutes} min ${seconds} s`;
  return `${seconds} s`;
}

/**
 * Human-readable distance for tooltips — full precision with explicit unit.
 *
 *   e.g. "12'345.678 km"  or  "845.50 m"  or  "12.00 cm"
 */
export function formatDistanceTooltip(meters: number): string {
  if (meters == null || isNaN(meters)) return '—';
  const system = getMeasurementSystem();
  if (system === 'METRIC' && meters < 1) return formatLocaleNumber(meters * 100, 2) + ' cm';
  if (meters < longDistanceThresholdM(system)) {
    return formatShortDistanceM(meters, system, { fractionDigits: 2 });
  }
  return formatLongDistanceM(meters, system, { fractionDigits: 3 });
}

function longDistanceThresholdM(system: ReturnType<typeof getMeasurementSystem>): number {
  return system === 'METRIC' ? METERS_PER_KILOMETER : METERS_PER_MILE;
}

export function generateColors(n: number) {
  // rainbow map requires at least 9 colors!
  const n2 = Math.max(n, 9);

  const options = {
    colormap: 'rainbow', // pick a builtin color map
    nshades: n2, // how many distinct colors you want
    format: 'hex', // the output format of the colors
    alpha: 1, // the alpha (opacity) of the colors
  };

  const colors = colormap(options);
  return colors.slice(0, n);
}

export function isEmptyOrNil(o: unknown) {
  return _.isNil(o) || _.isEmpty(o);
}
