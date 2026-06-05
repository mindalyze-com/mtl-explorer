import type maplibregl from 'maplibre-gl';

export type LngLatCoordinate = [number, number];

export type ScreenPoint = {
  x: number;
  y: number;
};

export type TrackLineProjection = {
  anchor: LngLatCoordinate;
  fraction: number;
  segmentIndex: number;
  segmentT: number;
};

type ProjectableMap = Pick<maplibregl.Map, 'project'>;

const DEFAULT_PIXEL_TOLERANCE = 12;
const DEFAULT_METER_TOLERANCE = 120;
const METERS_PER_LAT_DEGREE = 111_320;

export function projectClickToTrackLine({
  map,
  clickPoint,
  lngLat,
  coordinates,
  pixelTolerance = DEFAULT_PIXEL_TOLERANCE,
  meterTolerance = DEFAULT_METER_TOLERANCE,
}: {
  map?: ProjectableMap | null;
  clickPoint?: ScreenPoint | null;
  lngLat: { lng: number; lat: number };
  coordinates: LngLatCoordinate[];
  pixelTolerance?: number;
  meterTolerance?: number;
}): TrackLineProjection | null {
  if (coordinates.length < 2) return null;

  if (map && clickPoint) {
    const screenProjection = projectClickToTrackLineInScreenSpace(map, clickPoint, coordinates, pixelTolerance);
    if (screenProjection !== undefined) return screenProjection;
  }
  return projectClickToTrackLineInGeoSpace(lngLat, coordinates, meterTolerance);
}

export function nearestByNumericValue<T>(
  items: T[],
  target: number,
  valueForItem: (item: T) => number | null | undefined
): T | null {
  if (!Number.isFinite(target)) return null;

  let best: T | null = null;
  let bestDelta = Infinity;
  for (const item of items) {
    const value = valueForItem(item);
    if (value == null || !Number.isFinite(value)) continue;
    const delta = Math.abs(value - target);
    if (delta < bestDelta) {
      best = item;
      bestDelta = delta;
    }
  }
  return best;
}

export function numericRangeForItems<T>(
  items: T[],
  valueForItem: (item: T) => number | null | undefined
): { min: number; max: number } | null {
  let min = Infinity;
  let max = -Infinity;
  for (const item of items) {
    const value = valueForItem(item);
    if (value == null || !Number.isFinite(value)) continue;
    min = Math.min(min, value);
    max = Math.max(max, value);
  }
  return Number.isFinite(min) && Number.isFinite(max) ? { min, max } : null;
}

export function valueAtFraction(range: { min: number; max: number }, fraction: number): number {
  return range.min + (range.max - range.min) * clamp01(fraction);
}

export function clamp01(value: number): number {
  return Math.min(1, Math.max(0, value));
}

function projectClickToTrackLineInScreenSpace(
  map: ProjectableMap,
  clickPoint: ScreenPoint,
  coordinates: LngLatCoordinate[],
  tolerance: number
): TrackLineProjection | null | undefined {
  let projectedCoordinates: (ScreenPoint | null)[];
  try {
    projectedCoordinates = coordinates.map((coordinate) => asScreenPoint(map.project(coordinate)));
  } catch {
    return undefined;
  }
  if (projectedCoordinates.some((point) => point === null)) return undefined;

  return nearestSegmentProjection(
    coordinates,
    clickPoint,
    projectedCoordinates.filter((point): point is ScreenPoint => point !== null),
    tolerance
  );
}

function projectClickToTrackLineInGeoSpace(
  lngLat: { lng: number; lat: number },
  coordinates: LngLatCoordinate[],
  tolerance: number
): TrackLineProjection | null {
  const projectedClick = lngLatToMeters([lngLat.lng, lngLat.lat], lngLat.lat);
  const projectedCoordinates = coordinates.map((coordinate) => lngLatToMeters(coordinate, lngLat.lat));

  return nearestSegmentProjection(coordinates, projectedClick, projectedCoordinates, tolerance);
}

function nearestSegmentProjection(
  coordinates: LngLatCoordinate[],
  clickPoint: ScreenPoint,
  projectedCoordinates: ScreenPoint[],
  tolerance: number
): TrackLineProjection | null {
  let totalMeters = 0;
  const segmentMeters: number[] = [];
  for (let i = 0; i < coordinates.length - 1; i += 1) {
    const meters = segmentLengthMeters(coordinates[i], coordinates[i + 1]);
    segmentMeters.push(meters);
    totalMeters += meters;
  }

  let best: { distanceSq: number; segmentIndex: number; t: number; metersAtStart: number } | null = null;
  let metersAtStart = 0;
  for (let i = 0; i < projectedCoordinates.length - 1; i += 1) {
    const projection = projectPointToSegment(clickPoint, projectedCoordinates[i], projectedCoordinates[i + 1]);
    if (!best || projection.distanceSq < best.distanceSq) {
      best = { distanceSq: projection.distanceSq, segmentIndex: i, t: projection.t, metersAtStart };
    }
    metersAtStart += segmentMeters[i] ?? 0;
  }

  if (!best || best.distanceSq > tolerance * tolerance) return null;

  const segmentLength = segmentMeters[best.segmentIndex] ?? 0;
  const projectedMeters = best.metersAtStart + segmentLength * best.t;
  const fraction = totalMeters > 0 ? clamp01(projectedMeters / totalMeters) : 0;
  return {
    anchor: interpolateLngLat(coordinates[best.segmentIndex], coordinates[best.segmentIndex + 1], best.t),
    fraction,
    segmentIndex: best.segmentIndex,
    segmentT: best.t,
  };
}

function asScreenPoint(value: unknown): ScreenPoint | null {
  const point = value as Partial<ScreenPoint> | null | undefined;
  const x = Number(point?.x);
  const y = Number(point?.y);
  return Number.isFinite(x) && Number.isFinite(y) ? { x, y } : null;
}

function projectPointToSegment(
  point: ScreenPoint,
  start: ScreenPoint,
  end: ScreenPoint
): { distanceSq: number; t: number } {
  const dx = end.x - start.x;
  const dy = end.y - start.y;
  const lengthSq = dx * dx + dy * dy;
  const rawT = lengthSq > 0 ? ((point.x - start.x) * dx + (point.y - start.y) * dy) / lengthSq : 0;
  const t = clamp01(rawT);
  const x = start.x + dx * t;
  const y = start.y + dy * t;
  const distanceX = point.x - x;
  const distanceY = point.y - y;
  return { distanceSq: distanceX * distanceX + distanceY * distanceY, t };
}

function lngLatToMeters(coordinate: LngLatCoordinate, referenceLat: number): ScreenPoint {
  return {
    x: coordinate[0] * metersPerLngDegree(referenceLat),
    y: coordinate[1] * METERS_PER_LAT_DEGREE,
  };
}

function segmentLengthMeters(start: LngLatCoordinate, end: LngLatCoordinate): number {
  const referenceLat = (start[1] + end[1]) / 2;
  const dx = (end[0] - start[0]) * metersPerLngDegree(referenceLat);
  const dy = (end[1] - start[1]) * METERS_PER_LAT_DEGREE;
  return Math.sqrt(dx * dx + dy * dy);
}

function metersPerLngDegree(lat: number): number {
  return Math.max(1, METERS_PER_LAT_DEGREE * Math.cos((lat * Math.PI) / 180));
}

function interpolateLngLat(start: LngLatCoordinate, end: LngLatCoordinate, t: number): LngLatCoordinate {
  return [start[0] + (end[0] - start[0]) * t, start[1] + (end[1] - start[1]) * t];
}
