import { useTrackCursorSync, type TrackPoint } from '@/composables/trackCursorSync';

export type { TrackPoint };

/**
 * Backwards-compatible map-facing facade for the track detail cursor sync.
 *
 * The shared state, point indexes, x-axis conversion, and throttling now live in
 * trackCursorSync.ts so chart and map interactions use one source of truth.
 */
export function useTrackMapSync() {
  const cursor = useTrackCursorSync();

  return {
    pinnedPoint: cursor.pinnedPoint,
    hoverPoint: cursor.hoverPoint,
    setTrackPoints: cursor.setTrackPoints,
    getTrackPoints: cursor.getTrackPoints,
    findPointByTimestamp: cursor.findPointByTimestamp,
    findPointByDistance: cursor.findPointByDistance,
    findPointByLatLng: cursor.findPointByLatLng,
    setHoverPoint: (point: TrackPoint | null): void => cursor.setHoverPoint(point, 'map'),
    setHoverByTimestamp: cursor.setHoverByTimestamp,
    setPinnedPoint: (point: TrackPoint | null): void => cursor.setPinnedPoint(point, 'map'),
    getStartTs: cursor.getStartTs,
    getActivePoint: cursor.getActivePoint,
    clearHover: cursor.clearHover,
    clearAll: cursor.clearAll,
  };
}
