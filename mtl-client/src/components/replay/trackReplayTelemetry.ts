import type { GpsTrack } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import { MetricKey, type ChartPoint } from '@/utils/chartSeriesAdapter';
import { lowerBoundClampedIndex } from '@/utils/sortedSearch';
import {
  clamp01 as clampProgress,
  finiteNumberOrNull as finiteNumber,
  interpolationProgress,
  interpolateNullableNumber as interpolateNullable,
  maxFiniteOrNull as maxFinite,
} from '@/utils/numbers';

const REPLAY_TELEMETRY_MAX_SERIES_POINTS = 260;
const DEFAULT_REPLAY_SPEED_MAX_KMH = 40;

export type ReplayTelemetryPoint = {
  distanceMeters: number;
  elapsedSeconds: number | null;
  speedKmh: number | null;
  elevationMeters: number | null;
  ascentMeters: number | null;
  slopePercent: number | null;
};

export type ReplayTelemetry = {
  points: ReplayTelemetryPoint[];
  speedSeries: Array<[number, number | null]>;
  totalDistanceMeters: number;
  totalAscentMeters: number | null;
  maxElevationMeters: number | null;
  maxSpeedKmh: number;
  hasSpeedData: boolean;
};

export type ReplayTelemetrySample = ReplayTelemetryPoint & {
  progress: number;
};

export function buildReplayTelemetry(args: {
  chartPoints: ChartPoint[];
  track?: Pick<GpsTrack, 'trackLengthInMeter' | 'ascentInMeter' | 'maxAltitude' | 'speedInKmh30sMax'> | null;
  pathTotalDistanceMeters: number;
}): ReplayTelemetry {
  const fallbackDistance =
    finiteNumber(args.track?.trackLengthInMeter) ?? finiteNumber(args.pathTotalDistanceMeters) ?? 0;
  const points = normalizeTelemetryPoints(args.chartPoints, fallbackDistance);
  const lastPoint = points[points.length - 1] ?? null;
  const totalDistanceMeters = Math.max(
    0,
    finiteNumber(args.track?.trackLengthInMeter) ??
      finiteNumber(args.pathTotalDistanceMeters) ??
      finiteNumber(lastPoint?.distanceMeters) ??
      0
  );
  const totalAscentMeters = finiteNumber(args.track?.ascentInMeter) ?? finiteNumber(lastPoint?.ascentMeters) ?? null;
  const maxElevationMeters =
    finiteNumber(args.track?.maxAltitude) ?? maxFinite(points.map((point) => point.elevationMeters));
  const speedMaxFromPoints = maxFinite(points.map((point) => point.speedKmh));
  const maxSpeedKmh = finiteNumber(args.track?.speedInKmh30sMax) ?? speedMaxFromPoints ?? DEFAULT_REPLAY_SPEED_MAX_KMH;
  const speedSeries = downsample(points, REPLAY_TELEMETRY_MAX_SERIES_POINTS).map(
    (point) => [point.distanceMeters / 1000, point.speedKmh] as [number, number | null]
  );

  return {
    points,
    speedSeries,
    totalDistanceMeters,
    totalAscentMeters,
    maxElevationMeters,
    maxSpeedKmh,
    hasSpeedData: speedMaxFromPoints != null,
  };
}

export function sampleReplayTelemetry(telemetry: ReplayTelemetry, progress: number): ReplayTelemetrySample {
  const safeProgress = clampProgress(progress);
  const targetDistance = safeProgress * telemetry.totalDistanceMeters;
  const fallback: ReplayTelemetrySample = {
    distanceMeters: targetDistance,
    elapsedSeconds: null,
    speedKmh: null,
    elevationMeters: null,
    ascentMeters: null,
    slopePercent: null,
    progress: safeProgress,
  };

  if (telemetry.points.length === 0) return fallback;
  if (telemetry.points.length === 1 || telemetry.totalDistanceMeters <= 0) {
    return { ...telemetry.points[0], distanceMeters: targetDistance, progress: safeProgress };
  }

  const hi = firstPointAtOrAfterDistance(telemetry.points, targetDistance);
  if (hi <= 0) return { ...telemetry.points[0], distanceMeters: targetDistance, progress: safeProgress };
  if (hi >= telemetry.points.length) {
    return { ...telemetry.points[telemetry.points.length - 1], distanceMeters: targetDistance, progress: safeProgress };
  }

  const prev = telemetry.points[hi - 1];
  const next = telemetry.points[hi];
  const t = interpolationProgress(prev.distanceMeters, next.distanceMeters, targetDistance);

  return {
    distanceMeters: targetDistance,
    elapsedSeconds: interpolateNullable(prev.elapsedSeconds, next.elapsedSeconds, t),
    ...interpolateTelemetryMeasurements(prev, next, t),
    progress: safeProgress,
  };
}

export function sampleReplayTelemetryAtElapsedSeconds(
  telemetry: ReplayTelemetry,
  elapsedSeconds: number
): ReplayTelemetrySample {
  const durationSeconds = maxFinite(telemetry.points.map((point) => point.elapsedSeconds));
  const safeElapsedSeconds = Math.max(0, finiteNumber(elapsedSeconds) ?? 0);
  const progress =
    durationSeconds != null && durationSeconds > 0 ? clampProgress(safeElapsedSeconds / durationSeconds) : 0;
  const fallback: ReplayTelemetrySample = {
    distanceMeters: progress * telemetry.totalDistanceMeters,
    elapsedSeconds: safeElapsedSeconds,
    speedKmh: null,
    elevationMeters: null,
    ascentMeters: null,
    slopePercent: null,
    progress,
  };

  const timedPoints = telemetry.points.filter((point) => point.elapsedSeconds != null);
  if (timedPoints.length === 0) return fallback;
  if (timedPoints.length === 1) return { ...timedPoints[0], progress };

  const hi = firstPointAtOrAfterElapsed(timedPoints, safeElapsedSeconds);
  if (hi <= 0) return { ...timedPoints[0], progress };
  if (hi >= timedPoints.length) return { ...timedPoints[timedPoints.length - 1], progress };

  const prev = timedPoints[hi - 1];
  const next = timedPoints[hi];
  const prevElapsed = prev.elapsedSeconds ?? 0;
  const nextElapsed = next.elapsedSeconds ?? prevElapsed;
  const t = interpolationProgress(prevElapsed, nextElapsed, safeElapsedSeconds);

  return {
    distanceMeters: interpolateNullable(prev.distanceMeters, next.distanceMeters, t) ?? fallback.distanceMeters,
    elapsedSeconds: safeElapsedSeconds,
    ...interpolateTelemetryMeasurements(prev, next, t),
    progress,
  };
}

function interpolateTelemetryMeasurements(
  previous: ReplayTelemetryPoint,
  next: ReplayTelemetryPoint,
  progress: number
): Pick<ReplayTelemetryPoint, 'speedKmh' | 'elevationMeters' | 'ascentMeters' | 'slopePercent'> {
  return {
    speedKmh: interpolateNullable(previous.speedKmh, next.speedKmh, progress),
    elevationMeters: interpolateNullable(previous.elevationMeters, next.elevationMeters, progress),
    ascentMeters: interpolateNullable(previous.ascentMeters, next.ascentMeters, progress),
    slopePercent: interpolateNullable(previous.slopePercent, next.slopePercent, progress),
  };
}

function normalizeTelemetryPoints(points: ChartPoint[], fallbackDistanceMeters: number): ReplayTelemetryPoint[] {
  if (!points.length) return [];
  const lastIndex = Math.max(1, points.length - 1);
  const firstTimestampMs = firstValidTimestampMs(points);
  const normalized = points
    .map((point, index) => {
      const distanceMeters =
        finiteNumber(point.distanceInMeterSinceStart) ?? (fallbackDistanceMeters * index) / lastIndex;
      const timestampMs = point.pointTimestamp instanceof Date ? point.pointTimestamp.getTime() : null;
      return {
        distanceMeters,
        elapsedSeconds:
          metricValue(point, MetricKey.DurationS, 'last') ??
          metricValue(point, MetricKey.DurationS) ??
          (firstTimestampMs != null && timestampMs != null && Number.isFinite(timestampMs)
            ? Math.max(0, (timestampMs - firstTimestampMs) / 1000)
            : null),
        speedKmh:
          finiteNumber(point.speedInKmhWindow) ??
          finiteNumber(point.speedBucketAvgKmh) ??
          metricValue(point, MetricKey.SpeedWindowKmh) ??
          metricValue(point, MetricKey.SpeedBucketAvgKmh) ??
          metricValue(point, MetricKey.SpeedMovingWindowKmh),
        elevationMeters: finiteNumber(point.pointAltitude) ?? metricValue(point, MetricKey.AltitudeM),
        ascentMeters: metricValue(point, MetricKey.AscentM, 'last') ?? metricValue(point, MetricKey.AscentM),
        slopePercent: metricValue(point, MetricKey.SlopePercent),
      };
    })
    .filter((point) => Number.isFinite(point.distanceMeters) && point.distanceMeters >= 0)
    .sort((a, b) => a.distanceMeters - b.distanceMeters);

  const deduped: ReplayTelemetryPoint[] = [];
  for (const point of normalized) {
    const previous = deduped[deduped.length - 1];
    if (previous && Math.abs(previous.distanceMeters - point.distanceMeters) < 0.01) {
      deduped[deduped.length - 1] = point;
    } else {
      deduped.push(point);
    }
  }
  return deduped;
}

function metricValue(point: ChartPoint, key: MetricKey, field: 'avg' | 'last' = 'avg'): number | null {
  const value = point.metricStats?.[key]?.[field];
  return finiteNumber(value) ?? null;
}

function downsample<T>(items: T[], maxItems: number): T[] {
  if (items.length <= maxItems) return items;
  const stride = Math.ceil(items.length / maxItems);
  const sampled = items.filter((_item, index) => index % stride === 0);
  const last = items[items.length - 1];
  if (sampled[sampled.length - 1] !== last) sampled.push(last);
  return sampled;
}

function firstPointAtOrAfterDistance(points: ReplayTelemetryPoint[], distanceMeters: number): number {
  return lowerBoundClampedIndex(points, distanceMeters, (point) => point.distanceMeters);
}

function firstPointAtOrAfterElapsed(points: ReplayTelemetryPoint[], elapsedSeconds: number): number {
  return lowerBoundClampedIndex(points, elapsedSeconds, (point) => point.elapsedSeconds ?? 0);
}

function firstValidTimestampMs(points: ChartPoint[]): number | null {
  for (const point of points) {
    const ms = point.pointTimestamp instanceof Date ? point.pointTimestamp.getTime() : null;
    if (ms != null && Number.isFinite(ms)) return ms;
  }
  return null;
}
