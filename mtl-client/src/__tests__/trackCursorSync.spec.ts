import { describe, expect, it } from 'vitest';
import {
  chartXForTrackPoint,
  createTrackPointIndex,
  resolveChartPointTrackPoint,
  type TrackPoint,
} from '@/composables/trackCursorSync';
import { getPrimaryChartInputEvent } from '@/composables/useChartSync';

function point(overrides: Partial<TrackPoint>): TrackPoint {
  return {
    lat: 47,
    lng: 8,
    altitude: null,
    timestamp: 0,
    distanceKm: 0,
    pointIndex: 0,
    ...overrides,
  };
}

describe('trackCursorSync point index', () => {
  const points = [
    point({ pointIndex: 0, timestamp: 1_000, distanceKm: 0, lat: 47.0, lng: 8.0 }),
    point({ pointIndex: 1, timestamp: 2_000, distanceKm: 1, lat: 47.1, lng: 8.1 }),
    point({ pointIndex: 2, timestamp: 3_000, distanceKm: 2, lat: 47.2, lng: 8.2 }),
  ];

  it('finds nearest points by timestamp and distance', () => {
    const index = createTrackPointIndex(points);

    expect(index.findByTimestamp(2_400)?.pointIndex).toBe(1);
    expect(index.findByTimestamp(2_600)?.pointIndex).toBe(2);
    expect(index.findByDistance(1.4)?.pointIndex).toBe(1);
    expect(index.findByDistance(1.6)?.pointIndex).toBe(2);
  });

  it('uses absolute timestamps in time mode and distance in distance mode', () => {
    const index = createTrackPointIndex(points);

    expect(chartXForTrackPoint(points[2], 'time', index.startTs)).toBe(2_000);
    expect(chartXForTrackPoint(points[2], 'distance', index.startTs)).toBe(2);
    expect(resolveChartPointTrackPoint(index, 'time', 1_000, 2_000)?.pointIndex).toBe(1);
    expect(resolveChartPointTrackPoint(index, 'time', 1_000, null)?.pointIndex).toBe(1);
    expect(resolveChartPointTrackPoint(index, 'distance', 1.9, 2_000)?.pointIndex).toBe(2);
  });

  it('finds nearest map points by lat/lng', () => {
    const index = createTrackPointIndex(points);

    expect(index.findByLatLng(47.1995, 8.1995)?.pointIndex).toBe(2);
  });

  it('does not snap map hover when the pointer is far from the track', () => {
    const index = createTrackPointIndex(points);

    expect(index.findByLatLng(46, 7)).toBeNull();
  });
});

describe('getPrimaryChartInputEvent', () => {
  it('passes mouse events through unchanged', () => {
    const event = new MouseEvent('mousemove');

    expect(getPrimaryChartInputEvent(event)).toBe(event);
  });

  it('uses the active touch while a finger is moving', () => {
    const activeTouch = { clientX: 10, clientY: 20 } as Touch;
    const changedTouch = { clientX: 30, clientY: 40 } as Touch;
    const event = {
      touches: [activeTouch],
      changedTouches: [changedTouch],
    } as unknown as TouchEvent;

    expect(getPrimaryChartInputEvent(event)).toBe(activeTouch);
  });

  it('falls back to changedTouches for touchend/touchcancel', () => {
    const changedTouch = { clientX: 30, clientY: 40 } as Touch;
    const event = {
      touches: [],
      changedTouches: [changedTouch],
    } as unknown as TouchEvent;

    expect(getPrimaryChartInputEvent(event)).toBe(changedTouch);
  });
});
