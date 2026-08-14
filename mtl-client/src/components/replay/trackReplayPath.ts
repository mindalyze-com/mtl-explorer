import type { GpsTrack, GpsTrackDataPoint } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import { bearing, haversineDistance, shortestLongitudeDelta } from '@/components/map/mapGeometry';
import type { ChartPoint } from '@/utils/chartSeriesAdapter';
import { lowerBoundClampedIndex, nearestSortedIndex } from '@/utils/sortedSearch';
import { toValidDateMs } from '@/utils/Utils';
import {
  clamp01 as clampProgress,
  finiteNumberOrNull as finiteNumber,
  interpolationProgress,
  interpolateNullableNumber as interpolateNullable,
  lerpNumber as lerp,
} from '@/utils/numbers';

export const REPLAY_TARGET_DURATION_PRESETS_SECONDS = [10, 15, 30, 45, 60, 90] as const;
export const REPLAY_DEFAULT_TARGET_DURATION_SECONDS = 45;
export const REPLAY_MIN_TARGET_DURATION_SECONDS = 5;
export const REPLAY_MAX_TARGET_DURATION_SECONDS = 90;

const REPLAY_MIN_SAMPLE_SPACING_METERS = 4;
const REPLAY_TARGET_MAX_SAMPLED_POINTS = 1600;
const MIN_TRACK_DURATION_SECONDS = 1;
const MIN_HEADING_DISTANCE_METERS = 0.01;

export type ReplayCameraPresetId = 'follow' | 'chase' | 'overview';
export type ReplayPathTimingMode = 'distance' | 'time';

export type ReplayPathPoint = {
  lng: number;
  lat: number;
  elevation: number | null;
  distanceMeters: number;
  elapsedSeconds: number | null;
  headingDegrees: number;
  sourceIndex: number;
};

export type ReplayPathSample = ReplayPathPoint & {
  progress: number;
};

export type ReplayPath = {
  points: ReplayPathPoint[];
  totalDistanceMeters: number;
  originalDurationSeconds: number | null;
  timingMode: ReplayPathTimingMode;
};

type RawReplayPoint = {
  lng: number;
  lat: number;
  elevation: number | null;
  distanceMeters: number;
  elapsedSeconds: number | null;
  sourceIndex: number;
};

type IndexedRenderedPoint = {
  canonicalPointIndex: number;
  lng: number;
  lat: number;
  elevation: number | null;
  sourceIndex: number;
};

export function sanitizeReplayTargetDuration(seconds: unknown): number {
  const numeric = Number(seconds);
  if (!Number.isFinite(numeric)) return REPLAY_DEFAULT_TARGET_DURATION_SECONDS;
  return Math.max(REPLAY_MIN_TARGET_DURATION_SECONDS, Math.min(REPLAY_MAX_TARGET_DURATION_SECONDS, numeric));
}

export function replaySpeedFactor(
  originalDurationSeconds: number | null | undefined,
  targetDurationSeconds: number
): number | null {
  if (!Number.isFinite(originalDurationSeconds) || Number(originalDurationSeconds) < MIN_TRACK_DURATION_SECONDS) {
    return null;
  }
  const target = sanitizeReplayTargetDuration(targetDurationSeconds);
  return Number(originalDurationSeconds) / target;
}

export function formatReplaySpeedFactor(
  originalDurationSeconds: number | null | undefined,
  targetDurationSeconds: number
): string {
  const factor = replaySpeedFactor(originalDurationSeconds, targetDurationSeconds);
  if (factor == null) return 'Distance based';
  if (factor < 10) return `${factor.toFixed(1)}x`;
  return `${Math.round(factor)}x`;
}

export function trackDurationSeconds(
  track: Pick<GpsTrack, 'trackDurationInMotionSecs' | 'startDate' | 'endDate'>
): number | null {
  const startMs = toValidDateMs(track.startDate);
  const endMs = toValidDateMs(track.endDate);
  if (startMs != null && endMs != null && endMs > startMs) {
    const seconds = (endMs - startMs) / 1000;
    if (seconds >= MIN_TRACK_DURATION_SECONDS) return seconds;
  }

  const moving = Number(track.trackDurationInMotionSecs);
  if (Number.isFinite(moving) && moving >= MIN_TRACK_DURATION_SECONDS) return moving;
  return null;
}

export function buildReplayPath(args: {
  coordinates: number[][];
  track?: Pick<GpsTrack, 'trackDurationInMotionSecs' | 'startDate' | 'endDate'> | null;
  originalDurationSeconds?: number | null;
}): ReplayPath {
  const rawPoints = buildRawReplayPoints(args.coordinates);
  if (rawPoints.length === 0) {
    return { points: [], totalDistanceMeters: 0, originalDurationSeconds: null, timingMode: 'distance' };
  }

  const totalDistanceMeters = rawPoints[rawPoints.length - 1].distanceMeters;
  const originalDurationSeconds =
    args.originalDurationSeconds ?? (args.track ? trackDurationSeconds(args.track) : null);

  if (rawPoints.length === 1 || totalDistanceMeters <= 0) {
    return {
      points: [
        {
          ...rawPoints[0],
          headingDegrees: 0,
        },
      ],
      totalDistanceMeters,
      originalDurationSeconds,
      timingMode: 'distance',
    };
  }

  const spacingMeters = Math.max(
    REPLAY_MIN_SAMPLE_SPACING_METERS,
    totalDistanceMeters / REPLAY_TARGET_MAX_SAMPLED_POINTS
  );
  const points: ReplayPathPoint[] = [];
  for (let distance = 0; distance < totalDistanceMeters; distance += spacingMeters) {
    points.push(sampleRawPointAtDistance(rawPoints, distance));
  }
  points.push(sampleRawPointAtDistance(rawPoints, totalDistanceMeters));
  assignHeadings(points);

  return {
    points,
    totalDistanceMeters,
    originalDurationSeconds,
    timingMode: 'distance',
  };
}

export function buildTimedReplayPath(args: {
  chartPoints: ChartPoint[];
  renderedShapePoints: GpsTrackDataPoint[];
}): ReplayPath {
  const renderedPoints = indexedRenderedPoints(args.renderedShapePoints);
  if (renderedPoints.length === 0 || args.chartPoints.length === 0) {
    return { points: [], totalDistanceMeters: 0, originalDurationSeconds: null, timingMode: 'time' };
  }

  const rawPoints = timedRawReplayPoints(args.chartPoints, renderedPoints);
  if (rawPoints.length === 0) {
    return { points: [], totalDistanceMeters: 0, originalDurationSeconds: null, timingMode: 'time' };
  }

  const sortedRawPoints = [...rawPoints].sort((a, b) => (a.elapsedSeconds ?? 0) - (b.elapsedSeconds ?? 0));
  const firstElapsed = sortedRawPoints[0].elapsedSeconds ?? 0;
  const normalized: ReplayPathPoint[] = sortedRawPoints.map((point) => ({
    ...point,
    elapsedSeconds: Math.max(0, (point.elapsedSeconds ?? firstElapsed) - firstElapsed),
    headingDegrees: 0,
  }));

  assignHeadings(normalized);
  const totalDistanceMeters = normalized[normalized.length - 1].distanceMeters;
  const originalDurationSeconds = normalized[normalized.length - 1].elapsedSeconds ?? null;

  return {
    points: normalized,
    totalDistanceMeters,
    originalDurationSeconds:
      originalDurationSeconds != null && originalDurationSeconds >= MIN_TRACK_DURATION_SECONDS
        ? originalDurationSeconds
        : null,
    timingMode: 'time',
  };
}

export function sampleReplayPath(path: ReplayPath, progress: number): ReplayPathSample | null {
  if (path.timingMode === 'time' && Number.isFinite(path.originalDurationSeconds)) {
    const safeProgress = clampProgress(progress);
    return sampleReplayPathAtElapsedSeconds(path, safeProgress * Number(path.originalDurationSeconds));
  }
  return sampleReplayPathByDistanceProgress(path, progress);
}

export function sampleReplayPathAtElapsedSeconds(
  path: ReplayPath,
  elapsedSeconds: number,
  fallbackDurationSeconds = 0
): ReplayPathSample | null {
  if (
    path.timingMode !== 'time' ||
    path.points.length === 0 ||
    !Number.isFinite(path.originalDurationSeconds) ||
    Number(path.originalDurationSeconds) <= 0
  ) {
    const duration = Number(path.originalDurationSeconds);
    const fallback = Number(fallbackDurationSeconds);
    const effectiveDuration =
      Number.isFinite(duration) && duration > 0 ? duration : Number.isFinite(fallback) && fallback > 0 ? fallback : 0;
    const progress = effectiveDuration > 0 ? elapsedSeconds / effectiveDuration : 0;
    return sampleReplayPathByDistanceProgress(path, progress);
  }

  const durationSeconds = Number(path.originalDurationSeconds);
  const targetElapsedSeconds = Math.max(0, Math.min(durationSeconds, Number(elapsedSeconds) || 0));
  if (path.points.length === 1) {
    return { ...path.points[0], progress: 0 };
  }

  const hi = firstPointAtOrAfterElapsed(path.points, targetElapsedSeconds);
  const progress = targetElapsedSeconds / durationSeconds;
  if (hi <= 0) return { ...path.points[0], progress };
  if (hi >= path.points.length) return { ...path.points[path.points.length - 1], progress };

  const prev = path.points[hi - 1];
  const next = path.points[hi];
  const prevElapsed = prev.elapsedSeconds ?? 0;
  const nextElapsed = next.elapsedSeconds ?? prevElapsed;
  const t = interpolationProgress(prevElapsed, nextElapsed, targetElapsedSeconds);

  return {
    ...interpolateReplayPoint(prev, next, t),
    distanceMeters: lerp(prev.distanceMeters, next.distanceMeters, t),
    elapsedSeconds: targetElapsedSeconds,
    progress,
  };
}

export function distanceProgressForReplaySample(
  path: ReplayPath,
  sample: Pick<ReplayPathSample, 'distanceMeters'> | null
): number {
  if (!sample || path.totalDistanceMeters <= 0) return 0;
  return clampProgress(sample.distanceMeters / path.totalDistanceMeters);
}

function sampleReplayPathByDistanceProgress(path: ReplayPath, progress: number): ReplayPathSample | null {
  if (path.points.length === 0) return null;
  if (path.points.length === 1 || path.totalDistanceMeters <= 0) {
    return { ...path.points[0], progress: 0 };
  }

  const safeProgress = clampProgress(progress);
  const targetDistance = safeProgress * path.totalDistanceMeters;
  const hi = path.points.findIndex((point) => point.distanceMeters >= targetDistance);
  if (hi <= 0) {
    const first = path.points[0];
    return { ...first, progress: safeProgress };
  }
  const prev = path.points[hi - 1];
  const next = path.points[hi];
  const t = interpolationProgress(prev.distanceMeters, next.distanceMeters, targetDistance);

  return {
    ...interpolateReplayPoint(prev, next, t),
    distanceMeters: targetDistance,
    progress: safeProgress,
  };
}

function interpolateReplayPoint(prev: ReplayPathPoint, next: ReplayPathPoint, t: number): ReplayPathPoint {
  return {
    lng: interpolateLongitude(prev.lng, next.lng, t),
    lat: lerp(prev.lat, next.lat, t),
    elevation: interpolateNullable(prev.elevation, next.elevation, t),
    distanceMeters: lerp(prev.distanceMeters, next.distanceMeters, t),
    elapsedSeconds: interpolateNullable(prev.elapsedSeconds, next.elapsedSeconds, t),
    headingDegrees: interpolateBearing(prev.headingDegrees, next.headingDegrees, t),
    sourceIndex: Math.round(lerp(prev.sourceIndex, next.sourceIndex, t)),
  };
}

export function interpolateBearing(fromDegrees: number, toDegrees: number, t: number): number {
  const from = normalizeDegrees(fromDegrees);
  const to = normalizeDegrees(toDegrees);
  const delta = ((((to - from) % 360) + 540) % 360) - 180;
  return normalizeDegrees(from + delta * clampProgress(t));
}

function buildRawReplayPoints(coordinates: number[][]): RawReplayPoint[] {
  const rawPoints: RawReplayPoint[] = [];
  for (let i = 0; i < coordinates.length; i += 1) {
    const [lng, lat, elevation] = coordinates[i];
    if (!Number.isFinite(lng) || !Number.isFinite(lat)) continue;

    const previous = rawPoints[rawPoints.length - 1];
    const distanceFromPrevious = previous ? haversineDistance(previous.lat, previous.lng, lat, lng) : 0;
    if (previous && distanceFromPrevious <= 0.01) continue;

    rawPoints.push({
      lng,
      lat,
      elevation: Number.isFinite(elevation) ? elevation : null,
      distanceMeters: (previous?.distanceMeters ?? 0) + distanceFromPrevious,
      elapsedSeconds: null,
      sourceIndex: i,
    });
  }
  return rawPoints;
}

function sampleRawPointAtDistance(points: RawReplayPoint[], targetDistance: number): ReplayPathPoint {
  if (targetDistance <= 0) return { ...points[0], headingDegrees: 0 };
  const last = points[points.length - 1];
  if (targetDistance >= last.distanceMeters) return { ...last, headingDegrees: 0 };

  let hi = 1;
  while (hi < points.length && points[hi].distanceMeters < targetDistance) hi += 1;
  const prev = points[hi - 1];
  const next = points[hi];
  const span = Math.max(next.distanceMeters - prev.distanceMeters, Number.EPSILON);
  const t = (targetDistance - prev.distanceMeters) / span;
  return {
    ...interpolateReplayPoint({ ...prev, headingDegrees: 0 }, { ...next, headingDegrees: 0 }, t),
    distanceMeters: targetDistance,
    headingDegrees: 0,
  };
}

function assignHeadings(points: ReplayPathPoint[]): void {
  if (points.length < 2) return;
  for (let i = 0; i < points.length; i += 1) {
    const current = points[i];
    const prev = nearestDistinctPoint(points, i, -1);
    const next = nearestDistinctPoint(points, i, 1);
    if (prev && next && prev !== next) {
      points[i].headingDegrees = bearing(prev.lng, prev.lat, next.lng, next.lat);
    } else if (next) {
      points[i].headingDegrees = bearing(current.lng, current.lat, next.lng, next.lat);
    } else if (prev) {
      points[i].headingDegrees = bearing(prev.lng, prev.lat, current.lng, current.lat);
    } else {
      points[i].headingDegrees = i > 0 ? points[i - 1].headingDegrees : 0;
    }
  }
}

function indexedRenderedPoints(points: GpsTrackDataPoint[]): IndexedRenderedPoint[] {
  const indexed: IndexedRenderedPoint[] = [];
  for (const point of points) {
    const canonicalPointIndex = finiteNumber(point.canonicalPointIndex);
    if (canonicalPointIndex == null) continue;
    const coords = pointCoordinates(point.pointLongLat);
    if (!coords) continue;
    indexed.push({
      canonicalPointIndex,
      lng: coords[0],
      lat: coords[1],
      elevation: finiteNumber(point.pointAltitude),
      sourceIndex: finiteNumber(point.pointIndex) ?? canonicalPointIndex,
    });
  }
  return indexed.sort((a, b) => a.canonicalPointIndex - b.canonicalPointIndex);
}

function timedRawReplayPoints(chartPoints: ChartPoint[], renderedPoints: IndexedRenderedPoint[]): RawReplayPoint[] {
  const rawPoints: RawReplayPoint[] = [];
  let previousDistanceMeters = 0;
  let fallbackDistanceMeters = 0;
  let previousRenderedPoint: IndexedRenderedPoint | null = null;

  for (const chartPoint of chartPoints) {
    const timestampMs = toValidDateMs(chartPoint.pointTimestamp);
    const canonicalPointIndex = finiteNumber(chartPoint.pointIndex);
    if (timestampMs == null || canonicalPointIndex == null) continue;

    const renderedPoint = nearestRenderedPoint(renderedPoints, canonicalPointIndex);
    if (!renderedPoint) continue;

    if (previousRenderedPoint) {
      fallbackDistanceMeters += haversineDistance(
        previousRenderedPoint.lat,
        previousRenderedPoint.lng,
        renderedPoint.lat,
        renderedPoint.lng
      );
    }
    previousRenderedPoint = renderedPoint;

    const distanceMeters = Math.max(
      previousDistanceMeters,
      finiteNumber(chartPoint.distanceInMeterSinceStart) ?? fallbackDistanceMeters
    );
    previousDistanceMeters = distanceMeters;

    rawPoints.push({
      lng: renderedPoint.lng,
      lat: renderedPoint.lat,
      elevation: renderedPoint.elevation ?? finiteNumber(chartPoint.pointAltitude),
      distanceMeters,
      elapsedSeconds: timestampMs / 1000,
      sourceIndex: renderedPoint.sourceIndex,
    });
  }

  return dedupeTimedRawPoints(rawPoints);
}

function dedupeTimedRawPoints(points: RawReplayPoint[]): RawReplayPoint[] {
  const out: RawReplayPoint[] = [];
  for (const point of points) {
    const previous = out[out.length - 1];
    if (previous && previous.elapsedSeconds === point.elapsedSeconds) {
      out[out.length - 1] = point;
    } else {
      out.push(point);
    }
  }
  return out;
}

function nearestRenderedPoint(
  points: IndexedRenderedPoint[],
  targetCanonicalPointIndex: number
): IndexedRenderedPoint | null {
  if (points.length === 0) return null;
  const index = nearestSortedIndex(points, targetCanonicalPointIndex, (point) => point.canonicalPointIndex, true);
  return points[index];
}

function firstPointAtOrAfterElapsed(points: ReplayPathPoint[], elapsedSeconds: number): number {
  return lowerBoundClampedIndex(points, elapsedSeconds, (point) => point.elapsedSeconds ?? 0);
}

function nearestDistinctPoint(points: ReplayPathPoint[], index: number, direction: -1 | 1): ReplayPathPoint | null {
  const current = points[index];
  for (let i = index + direction; i >= 0 && i < points.length; i += direction) {
    const candidate = points[i];
    if (haversineDistance(current.lat, current.lng, candidate.lat, candidate.lng) > MIN_HEADING_DISTANCE_METERS) {
      return candidate;
    }
  }
  return null;
}

function pointCoordinates(point: unknown): [number, number] | null {
  const coordinates = (point as { coordinates?: unknown } | null | undefined)?.coordinates;
  if (!Array.isArray(coordinates) || coordinates.length < 2) return null;
  const lng = finiteNumber(coordinates[0]);
  const lat = finiteNumber(coordinates[1]);
  return lng == null || lat == null ? null : [lng, lat];
}

function interpolateLongitude(fromLng: number, toLng: number, t: number): number {
  return fromLng + shortestLongitudeDelta(fromLng, toLng) * t;
}

function normalizeDegrees(degrees: number): number {
  return ((degrees % 360) + 360) % 360;
}
