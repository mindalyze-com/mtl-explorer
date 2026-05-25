import { ref, type Ref } from 'vue';

export interface TrackPoint {
  lat: number;
  lng: number;
  altitude: number | null;
  timestamp: number;
  distanceKm: number;
  pointIndex: number;
  canonicalPointIndex?: number;
  chartX?: Partial<Record<TrackCursorXMode, number>>;
}

export type TrackCursorSource = 'chart' | 'map' | 'system';
export type TrackCursorXMode = 'time' | 'distance';

interface SortedTrackPointEntry {
  value: number;
  point: TrackPoint;
}

interface SpatialGrid {
  minLat: number;
  maxLat: number;
  minLng: number;
  maxLng: number;
  cellHeight: number;
  cellWidth: number;
  dimension: number;
  buckets: Map<string, TrackPoint[]>;
}

export interface TrackPointIndex {
  points: TrackPoint[];
  startTs: number;
  findByPointIndex(pointIndex: number): TrackPoint | null;
  findByCanonicalPointIndex(pointIndex: number): TrackPoint | null;
  findByChartX(xMode: TrackCursorXMode, chartX: number): TrackPoint | null;
  findByTimestamp(ts: number): TrackPoint | null;
  findByDistance(km: number): TrackPoint | null;
  findByLatLng(lat: number, lng: number): TrackPoint | null;
}

const EMPTY_INDEX: TrackPointIndex = {
  points: [],
  startTs: 0,
  findByPointIndex: () => null,
  findByCanonicalPointIndex: () => null,
  findByChartX: () => null,
  findByTimestamp: () => null,
  findByDistance: () => null,
  findByLatLng: () => null,
};

const MIN_SPATIAL_INDEX_POINTS = 800;
const SPATIAL_GRID_DIMENSION = 64;
const MIN_CELL_SIZE = 1e-12;
const MAX_MAP_CURSOR_SNAP_METERS = 120;
const METERS_PER_LAT_DEGREE = 111_320;

const pinnedPoint: Ref<TrackPoint | null> = ref(null);
const hoverPoint: Ref<TrackPoint | null> = ref(null);
const hoverSource: Ref<TrackCursorSource | null> = ref(null);
const pinnedSource: Ref<TrackCursorSource | null> = ref(null);
const trackPointVersion: Ref<number> = ref(0);

let trackPointIndex: TrackPointIndex = EMPTY_INDEX;
let currentXMode: TrackCursorXMode = 'time';
let lastHoverTimestamp: number | null = null;
let pendingHoverPoint: TrackPoint | null = null;
let pendingHoverSource: TrackCursorSource | null = null;
let hoverFrame: number | null = null;

function requestHoverFrame(callback: () => void): number {
  if (typeof window !== 'undefined' && typeof window.requestAnimationFrame === 'function') {
    return window.requestAnimationFrame(callback);
  }
  return setTimeout(callback, 16) as unknown as number;
}

function bumpTrackPointVersion(): void {
  trackPointVersion.value += 1;
}

function cancelHoverFrame(frame: number): void {
  if (typeof window !== 'undefined' && typeof window.cancelAnimationFrame === 'function') {
    window.cancelAnimationFrame(frame);
  } else {
    clearTimeout(frame);
  }
}

function applyHoverPoint(point: TrackPoint | null, source: TrackCursorSource): void {
  hoverPoint.value = point;
  hoverSource.value = point ? source : null;
}

function scheduleHoverPoint(point: TrackPoint | null, source: TrackCursorSource): void {
  pendingHoverPoint = point;
  pendingHoverSource = source;

  if (hoverFrame !== null) return;

  hoverFrame = requestHoverFrame(() => {
    hoverFrame = null;
    applyHoverPoint(pendingHoverPoint, pendingHoverSource ?? 'system');
    pendingHoverPoint = null;
    pendingHoverSource = null;
  });
}

function cancelPendingHover(): void {
  if (hoverFrame !== null) {
    cancelHoverFrame(hoverFrame);
    hoverFrame = null;
  }
  pendingHoverPoint = null;
  pendingHoverSource = null;
}

function nearestBySortedValue(entries: SortedTrackPointEntry[], value: number): TrackPoint | null {
  if (entries.length === 0 || !Number.isFinite(value)) return null;

  let lo = 0;
  let hi = entries.length - 1;
  while (lo < hi) {
    const mid = (lo + hi) >> 1;
    if (entries[mid].value < value) lo = mid + 1;
    else hi = mid;
  }

  if (lo > 0 && Math.abs(entries[lo - 1].value - value) < Math.abs(entries[lo].value - value)) {
    lo -= 1;
  }

  return entries[lo].point;
}

function metersPerLngDegree(lat: number): number {
  return Math.max(1, METERS_PER_LAT_DEGREE * Math.cos((lat * Math.PI) / 180));
}

function squaredLatLngDistanceMeters(point: TrackPoint, lat: number, lng: number): number {
  const dlat = point.lat - lat;
  const dlng = point.lng - lng;
  const metersPerLng = metersPerLngDegree((point.lat + lat) / 2);
  const dLatMeters = dlat * METERS_PER_LAT_DEGREE;
  const dLngMeters = dlng * metersPerLng;
  return dLatMeters * dLatMeters + dLngMeters * dLngMeters;
}

function isWithinMapSnapDistance(distanceSqMeters: number): boolean {
  return distanceSqMeters <= MAX_MAP_CURSOR_SNAP_METERS * MAX_MAP_CURSOR_SNAP_METERS;
}

function squaredDistanceToBoundsMeters(
  lat: number,
  lng: number,
  minLat: number,
  maxLat: number,
  minLng: number,
  maxLng: number
): number {
  const closestLat = Math.min(maxLat, Math.max(minLat, lat));
  const closestLng = Math.min(maxLng, Math.max(minLng, lng));
  const metersPerLng = metersPerLngDegree((lat + closestLat) / 2);
  const dLatMeters = (lat - closestLat) * METERS_PER_LAT_DEGREE;
  const dLngMeters = (lng - closestLng) * metersPerLng;
  return dLatMeters * dLatMeters + dLngMeters * dLngMeters;
}

function squaredDistanceToOutsideBoundsMeters(
  lat: number,
  lng: number,
  minLat: number,
  maxLat: number,
  minLng: number,
  maxLng: number
): number {
  if (lat < minLat || lat > maxLat || lng < minLng || lng > maxLng) {
    return squaredDistanceToBoundsMeters(lat, lng, minLat, maxLat, minLng, maxLng);
  }

  const metersPerLng = metersPerLngDegree(lat);
  const dMinLatMeters = (lat - minLat) * METERS_PER_LAT_DEGREE;
  const dMaxLatMeters = (maxLat - lat) * METERS_PER_LAT_DEGREE;
  const dMinLngMeters = (lng - minLng) * metersPerLng;
  const dMaxLngMeters = (maxLng - lng) * metersPerLng;
  const nearestOutsideMeters = Math.min(dMinLatMeters, dMaxLatMeters, dMinLngMeters, dMaxLngMeters);

  return nearestOutsideMeters * nearestOutsideMeters;
}

function findNearestLinear(points: TrackPoint[], lat: number, lng: number): TrackPoint | null {
  if (points.length === 0 || !Number.isFinite(lat) || !Number.isFinite(lng)) return null;

  let best: TrackPoint | null = null;
  let bestDist = Infinity;
  for (const point of points) {
    const dist = squaredLatLngDistanceMeters(point, lat, lng);
    if (dist < bestDist) {
      best = point;
      bestDist = dist;
    }
  }
  return best && isWithinMapSnapDistance(bestDist) ? best : null;
}

function gridKey(x: number, y: number): string {
  return `${x}:${y}`;
}

function clampCell(value: number, dimension: number): number {
  return Math.min(dimension - 1, Math.max(0, value));
}

function cellForCoordinate(value: number, min: number, cellSize: number, dimension: number): number {
  return clampCell(Math.floor((value - min) / cellSize), dimension);
}

function buildSpatialGrid(points: TrackPoint[]): SpatialGrid | null {
  const finitePoints = points.filter((p) => Number.isFinite(p.lat) && Number.isFinite(p.lng));
  if (finitePoints.length < MIN_SPATIAL_INDEX_POINTS) return null;

  let minLat = Infinity;
  let maxLat = -Infinity;
  let minLng = Infinity;
  let maxLng = -Infinity;
  for (const point of finitePoints) {
    minLat = Math.min(minLat, point.lat);
    maxLat = Math.max(maxLat, point.lat);
    minLng = Math.min(minLng, point.lng);
    maxLng = Math.max(maxLng, point.lng);
  }

  const cellHeight = Math.max((maxLat - minLat) / SPATIAL_GRID_DIMENSION, MIN_CELL_SIZE);
  const cellWidth = Math.max((maxLng - minLng) / SPATIAL_GRID_DIMENSION, MIN_CELL_SIZE);
  const buckets = new Map<string, TrackPoint[]>();

  for (const point of finitePoints) {
    const x = cellForCoordinate(point.lng, minLng, cellWidth, SPATIAL_GRID_DIMENSION);
    const y = cellForCoordinate(point.lat, minLat, cellHeight, SPATIAL_GRID_DIMENSION);
    const key = gridKey(x, y);
    const bucket = buckets.get(key);
    if (bucket) bucket.push(point);
    else buckets.set(key, [point]);
  }

  return {
    minLat,
    maxLat,
    minLng,
    maxLng,
    cellHeight,
    cellWidth,
    dimension: SPATIAL_GRID_DIMENSION,
    buckets,
  };
}

function findNearestInGrid(grid: SpatialGrid, lat: number, lng: number): TrackPoint | null {
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return null;

  const centerX = cellForCoordinate(lng, grid.minLng, grid.cellWidth, grid.dimension);
  const centerY = cellForCoordinate(lat, grid.minLat, grid.cellHeight, grid.dimension);
  let best: TrackPoint | null = null;
  let bestDist = Infinity;

  for (let ring = 0; ring < grid.dimension; ring += 1) {
    const minX = Math.max(0, centerX - ring);
    const maxX = Math.min(grid.dimension - 1, centerX + ring);
    const minY = Math.max(0, centerY - ring);
    const maxY = Math.min(grid.dimension - 1, centerY + ring);

    for (let y = minY; y <= maxY; y += 1) {
      for (let x = minX; x <= maxX; x += 1) {
        if (ring > 0 && x > minX && x < maxX && y > minY && y < maxY) continue;

        const bucket = grid.buckets.get(gridKey(x, y));
        if (!bucket) continue;

        for (const point of bucket) {
          const dist = squaredLatLngDistanceMeters(point, lat, lng);
          if (dist < bestDist) {
            best = point;
            bestDist = dist;
          }
        }
      }
    }

    if (best) {
      const rectMinLng = grid.minLng + minX * grid.cellWidth;
      const rectMaxLng = grid.minLng + (maxX + 1) * grid.cellWidth;
      const rectMinLat = grid.minLat + minY * grid.cellHeight;
      const rectMaxLat = grid.minLat + (maxY + 1) * grid.cellHeight;
      const minOutsideDistanceSq = squaredDistanceToOutsideBoundsMeters(
        lat,
        lng,
        rectMinLat,
        rectMaxLat,
        rectMinLng,
        rectMaxLng
      );

      if (bestDist <= minOutsideDistanceSq) {
        return isWithinMapSnapDistance(bestDist) ? best : null;
      }
    }
  }

  return best && isWithinMapSnapDistance(bestDist) ? best : null;
}

export function createTrackPointIndex(points: TrackPoint[]): TrackPointIndex {
  if (points.length === 0) return EMPTY_INDEX;

  const indexedPoints = points.slice();
  const timestampEntries = indexedPoints
    .filter((point) => Number.isFinite(point.timestamp))
    .map((point) => ({ value: point.timestamp, point }))
    .sort((a, b) => a.value - b.value);
  const distanceEntries = indexedPoints
    .filter((point) => Number.isFinite(point.distanceKm))
    .map((point) => ({ value: point.distanceKm, point }))
    .sort((a, b) => a.value - b.value);
  const pointIndexEntries = indexedPoints
    .filter((point) => Number.isFinite(point.pointIndex))
    .map((point) => ({ value: point.pointIndex, point }))
    .sort((a, b) => a.value - b.value);
  const canonicalPointIndexEntries = indexedPoints
    .filter((point) => Number.isFinite(point.canonicalPointIndex))
    .map((point) => ({ value: point.canonicalPointIndex as number, point }))
    .sort((a, b) => a.value - b.value);
  const startTs = timestampEntries[0]?.point.timestamp ?? 0;
  const chartXEntries: Record<TrackCursorXMode, SortedTrackPointEntry[]> = {
    time: indexedPoints
      .map((point) => ({ value: chartXForTrackPoint(point, 'time', startTs), point }))
      .filter((entry) => Number.isFinite(entry.value))
      .sort((a, b) => a.value - b.value),
    distance: indexedPoints
      .map((point) => ({ value: chartXForTrackPoint(point, 'distance', startTs), point }))
      .filter((entry) => Number.isFinite(entry.value))
      .sort((a, b) => a.value - b.value),
  };
  const grid = buildSpatialGrid(indexedPoints);

  return {
    points: indexedPoints,
    startTs,
    findByPointIndex: (pointIndex: number) => nearestBySortedValue(pointIndexEntries, pointIndex),
    findByCanonicalPointIndex: (pointIndex: number) => nearestBySortedValue(canonicalPointIndexEntries, pointIndex),
    findByChartX: (xMode: TrackCursorXMode, chartX: number) => nearestBySortedValue(chartXEntries[xMode], chartX),
    findByTimestamp: (ts: number) => nearestBySortedValue(timestampEntries, ts),
    findByDistance: (km: number) => nearestBySortedValue(distanceEntries, km),
    findByLatLng: (lat: number, lng: number) =>
      grid ? findNearestInGrid(grid, lat, lng) : findNearestLinear(indexedPoints, lat, lng),
  };
}

export function chartXForTrackPoint(
  point: Pick<TrackPoint, 'timestamp' | 'distanceKm' | 'chartX'>,
  xMode: TrackCursorXMode,
  startTs: number
): number {
  const explicitChartX = point.chartX?.[xMode];
  if (typeof explicitChartX === 'number' && Number.isFinite(explicitChartX)) {
    return explicitChartX;
  }

  return xMode === 'distance' ? point.distanceKm : point.timestamp - startTs;
}

export function resolveChartPointTrackPoint(
  index: TrackPointIndex,
  xMode: TrackCursorXMode,
  chartX: number,
  absoluteTimestamp?: number | null,
  canonicalPointIndex?: number | null
): TrackPoint | null {
  if (canonicalPointIndex != null && Number.isFinite(canonicalPointIndex)) {
    const canonicalPoint = index.findByCanonicalPointIndex(canonicalPointIndex);
    if (canonicalPoint) return canonicalPoint;
  }

  const chartPoint = index.findByChartX(xMode, chartX);
  if (chartPoint) return chartPoint;

  const timestamp = absoluteTimestamp ?? index.startTs + chartX;
  return xMode === 'distance' ? index.findByDistance(chartX) : index.findByTimestamp(timestamp);
}

export function useTrackCursorSync() {
  function setTrackPoints(points: TrackPoint[]): void {
    trackPointIndex = createTrackPointIndex(points);
    bumpTrackPointVersion();
    if (lastHoverTimestamp !== null) {
      setHoverPoint(trackPointIndex.findByTimestamp(lastHoverTimestamp), 'system');
    }
  }

  function getTrackPoints(): TrackPoint[] {
    return trackPointIndex.points;
  }

  function setXMode(mode: TrackCursorXMode): void {
    currentXMode = mode;
  }

  function getXMode(): TrackCursorXMode {
    return currentXMode;
  }

  function getStartTs(): number {
    return trackPointIndex.startTs;
  }

  function findPointByTimestamp(ts: number): TrackPoint | null {
    return trackPointIndex.findByTimestamp(ts);
  }

  function findPointByIndex(pointIndex: number): TrackPoint | null {
    return trackPointIndex.findByPointIndex(pointIndex);
  }

  function findPointByCanonicalIndex(pointIndex: number): TrackPoint | null {
    return trackPointIndex.findByCanonicalPointIndex(pointIndex);
  }

  function findPointByDistance(km: number): TrackPoint | null {
    return trackPointIndex.findByDistance(km);
  }

  function findPointByLatLng(lat: number, lng: number): TrackPoint | null {
    return trackPointIndex.findByLatLng(lat, lng);
  }

  function setHoverPoint(point: TrackPoint | null, source: TrackCursorSource = 'system'): void {
    lastHoverTimestamp = null;
    scheduleHoverPoint(point, source);
  }

  function setHoverByTimestamp(ts: number, source: TrackCursorSource = 'chart'): void {
    lastHoverTimestamp = ts;
    scheduleHoverPoint(trackPointIndex.findByTimestamp(ts), source);
  }

  function setHoverByChartPoint(
    chartX: number,
    absoluteTimestamp?: number | null,
    source: TrackCursorSource = 'chart',
    canonicalPointIndex?: number | null
  ): void {
    const point = resolveChartPointTrackPoint(
      trackPointIndex,
      currentXMode,
      chartX,
      absoluteTimestamp,
      canonicalPointIndex
    );
    if (absoluteTimestamp != null) {
      lastHoverTimestamp = absoluteTimestamp;
    } else {
      lastHoverTimestamp = null;
    }
    scheduleHoverPoint(point, source);
  }

  function setPinnedPoint(point: TrackPoint | null, source: TrackCursorSource = 'system'): void {
    pinnedPoint.value = point;
    pinnedSource.value = point ? source : null;
  }

  function setPinnedByChartPoint(
    chartX: number,
    absoluteTimestamp?: number | null,
    source: TrackCursorSource = 'chart',
    canonicalPointIndex?: number | null
  ): void {
    setPinnedPoint(
      resolveChartPointTrackPoint(trackPointIndex, currentXMode, chartX, absoluteTimestamp, canonicalPointIndex),
      source
    );
  }

  function chartXForPoint(point: Pick<TrackPoint, 'timestamp' | 'distanceKm' | 'chartX'>): number {
    return chartXForTrackPoint(point, currentXMode, trackPointIndex.startTs);
  }

  function getActivePoint(): Ref<TrackPoint | null> {
    return pinnedPoint.value ? pinnedPoint : hoverPoint;
  }

  function clearHover(): void {
    lastHoverTimestamp = null;
    cancelPendingHover();
    applyHoverPoint(null, 'system');
  }

  function clearHoverBySource(source: TrackCursorSource): void {
    if (pendingHoverSource === source) {
      cancelPendingHover();
      lastHoverTimestamp = null;
    }
    if (hoverSource.value === source) {
      lastHoverTimestamp = null;
      applyHoverPoint(null, 'system');
    }
  }

  function clearPinned(): void {
    setPinnedPoint(null, 'system');
  }

  function clearPinnedBySource(source: TrackCursorSource): void {
    if (pinnedSource.value === source) {
      setPinnedPoint(null, 'system');
    }
  }

  function clearAll(): void {
    cancelPendingHover();
    pinnedPoint.value = null;
    hoverPoint.value = null;
    hoverSource.value = null;
    pinnedSource.value = null;
    trackPointIndex = EMPTY_INDEX;
    lastHoverTimestamp = null;
    bumpTrackPointVersion();
  }

  return {
    pinnedPoint,
    hoverPoint,
    hoverSource,
    pinnedSource,
    trackPointVersion,
    setTrackPoints,
    getTrackPoints,
    setXMode,
    getXMode,
    getStartTs,
    findPointByIndex,
    findPointByCanonicalIndex,
    findPointByTimestamp,
    findPointByDistance,
    findPointByLatLng,
    setHoverPoint,
    setHoverByTimestamp,
    setHoverByChartPoint,
    setPinnedPoint,
    setPinnedByChartPoint,
    chartXForPoint,
    getActivePoint,
    clearHover,
    clearHoverBySource,
    clearPinned,
    clearPinnedBySource,
    clearAll,
  };
}
