import {ref, type Ref} from 'vue';

export interface TrackPoint {
  lat: number;
  lng: number;
  altitude: number | null;
  timestamp: number;
  distanceKm: number;
  pointIndex: number;
}

// Module-level shared state — single position synced between all charts and the mini-map.
const pinnedPoint: Ref<TrackPoint | null> = ref(null);
const hoverPoint: Ref<TrackPoint | null> = ref(null);

// The resolved track points array, set once by the parent that loads detail data.
let trackPoints: TrackPoint[] = [];

// Sorted timestamps for binary search
let sortedTimestamps: number[] = [];

// Last timestamp requested via setHoverByTimestamp — re-resolved after setTrackPoints().
let lastHoverTimestamp: number | null = null;

// Sorted distances for binary search
let sortedDistances: number[] = [];

// Absolute timestamp of the first track point (used to convert elapsed→absolute).
let trackStartTs: number = 0;

export function useTrackMapSync() {

  function setTrackPoints(points: TrackPoint[]): void {
    trackPoints = points;
    sortedTimestamps = points.map(p => p.timestamp);
    sortedDistances = points.map(p => p.distanceKm);
    trackStartTs = points.length > 0 ? points[0].timestamp : 0;
    // Re-resolve the last hover timestamp now that sortedTimestamps is populated.
    // This fixes the race where charts hover before TrackDetails.load() finishes.
    if (lastHoverTimestamp !== null) {
      hoverPoint.value = findPointByTimestamp(lastHoverTimestamp);
    }
  }

  function getTrackPoints(): TrackPoint[] {
    return trackPoints;
  }

  /** Find the nearest TrackPoint for a given Highcharts timestamp (ms). */
  function findPointByTimestamp(ts: number): TrackPoint | null {
    if (sortedTimestamps.length === 0) return null;

    // Binary search for closest timestamp
    let lo = 0;
    let hi = sortedTimestamps.length - 1;
    while (lo < hi) {
      const mid = (lo + hi) >> 1;
      if (sortedTimestamps[mid] < ts) lo = mid + 1;
      else hi = mid;
    }
    // Check lo and lo-1 for closest
    if (lo > 0 && Math.abs(sortedTimestamps[lo - 1] - ts) < Math.abs(sortedTimestamps[lo] - ts)) {
      lo = lo - 1;
    }
    return trackPoints[lo] ?? null;
  }

  /** Find the nearest TrackPoint for a given distance in km (from charts in distance mode). */
  function findPointByDistance(km: number): TrackPoint | null {
    if (sortedDistances.length === 0) return null;

    let lo = 0;
    let hi = sortedDistances.length - 1;
    while (lo < hi) {
      const mid = (lo + hi) >> 1;
      if (sortedDistances[mid] < km) lo = mid + 1;
      else hi = mid;
    }
    if (lo > 0 && Math.abs(sortedDistances[lo - 1] - km) < Math.abs(sortedDistances[lo] - km)) {
      lo = lo - 1;
    }
    return trackPoints[lo] ?? null;
  }

  /** Find the nearest TrackPoint for a given lat/lng (simple Euclidean on degrees — fine for nearby points). */
  function findPointByLatLng(lat: number, lng: number): TrackPoint | null {
    if (trackPoints.length === 0) return null;
    let best: TrackPoint | null = null;
    let bestDist = Infinity;
    for (const p of trackPoints) {
      const dlat = p.lat - lat;
      const dlng = p.lng - lng;
      const d = dlat * dlat + dlng * dlng;
      if (d < bestDist) {
        bestDist = d;
        best = p;
      }
    }
    return best;
  }

  function setHoverPoint(point: TrackPoint | null): void {
    hoverPoint.value = point;
  }

  /**
   * Store the timestamp and resolve the nearest TrackPoint immediately.
   * If sortedTimestamps is still empty (race: chart rendered before setTrackPoints),
   * the timestamp is remembered and re-resolved when setTrackPoints() is called.
   */
  function setHoverByTimestamp(ts: number): void {
    lastHoverTimestamp = ts;
    hoverPoint.value = findPointByTimestamp(ts);
  }

  function setPinnedPoint(point: TrackPoint | null): void {
    pinnedPoint.value = point;
  }

  /** The active point: pinned takes priority over hover. */
  function getActivePoint(): Ref<TrackPoint | null> {
    // Return a computed-like reactive ref — consumers watch both
    return pinnedPoint.value ? pinnedPoint : hoverPoint;
  }

  function getStartTs(): number {
    return trackStartTs;
  }

  function clearAll(): void {
    pinnedPoint.value = null;
    hoverPoint.value = null;
    trackPoints = [];
    sortedTimestamps = [];
    sortedDistances = [];
    lastHoverTimestamp = null;
    trackStartTs = 0;
  }

  return {
    pinnedPoint,
    hoverPoint,
    setTrackPoints,
    getTrackPoints,
    findPointByTimestamp,
    findPointByDistance,
    findPointByLatLng,
    setHoverPoint,
    setHoverByTimestamp,
    setPinnedPoint,
    getStartTs,
    clearAll,
  };
}
