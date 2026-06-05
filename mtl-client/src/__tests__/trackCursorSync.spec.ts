import { describe, expect, it, vi } from 'vitest';
import {
  chartXForTrackPoint,
  createTrackPointIndex,
  resolveChartPointTrackPoint,
  type TrackPoint,
} from '@/composables/trackCursorSync';
import { getPrimaryChartInputEvent, useChartSync } from '@/composables/useChartSync';
import type Highcharts from 'highcharts';

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

  it('finds nearest points by timestamp, distance, and point index', () => {
    const index = createTrackPointIndex(points);

    expect(index.findByTimestamp(2_400)?.pointIndex).toBe(1);
    expect(index.findByTimestamp(2_600)?.pointIndex).toBe(2);
    expect(index.findByDistance(1.4)?.pointIndex).toBe(1);
    expect(index.findByDistance(1.6)?.pointIndex).toBe(2);
    expect(index.findByPointIndex(1.4)?.pointIndex).toBe(1);
    expect(index.findByPointIndex(1.6)?.pointIndex).toBe(2);
  });

  it('uses absolute timestamps in time mode and distance in distance mode', () => {
    const index = createTrackPointIndex(points);

    expect(chartXForTrackPoint(points[2], 'time', index.startTs)).toBe(2_000);
    expect(chartXForTrackPoint(points[2], 'distance', index.startTs)).toBe(2);
    expect(resolveChartPointTrackPoint(index, 'time', 1_000, 2_000)?.pointIndex).toBe(1);
    expect(resolveChartPointTrackPoint(index, 'time', 1_000, null)?.pointIndex).toBe(1);
    expect(resolveChartPointTrackPoint(index, 'distance', 1.9, 2_000)?.pointIndex).toBe(2);
  });

  it('prefers explicit chart x values over derived time and distance values', () => {
    const index = createTrackPointIndex([
      point({ pointIndex: 1, timestamp: 10_000, distanceKm: 3, chartX: { time: 50, distance: 0.5 } }),
      point({ pointIndex: 2, timestamp: 11_000, distanceKm: 4, chartX: { time: 100, distance: 1 } }),
    ]);

    expect(chartXForTrackPoint(index.points[1], 'time', index.startTs)).toBe(100);
    expect(chartXForTrackPoint(index.points[1], 'distance', index.startTs)).toBe(1);
    expect(resolveChartPointTrackPoint(index, 'time', 52, null)?.pointIndex).toBe(1);
    expect(resolveChartPointTrackPoint(index, 'distance', 0.9, null)?.pointIndex).toBe(2);
  });

  it('resolves chart points by canonical bucket identity before x-mode fallback', () => {
    const index = createTrackPointIndex([
      point({
        pointIndex: 1,
        canonicalPointIndex: 100,
        timestamp: 10_000,
        distanceKm: 1,
        chartX: { time: 0, distance: 1 },
      }),
      point({
        pointIndex: 2,
        canonicalPointIndex: 200,
        timestamp: 20_000,
        distanceKm: 2,
        chartX: { time: 10_000, distance: 2 },
      }),
    ]);

    expect(resolveChartPointTrackPoint(index, 'distance', 1, null, 200)?.pointIndex).toBe(2);
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

describe('trackCursorSync spatial grid lookup (large tracks)', () => {
  // Above ~800 points createTrackPointIndex switches from a linear scan to the
  // spatial grid, so build a track large enough to exercise the grid path.
  function buildGridTrack(count: number): TrackPoint[] {
    const result: TrackPoint[] = [];
    for (let i = 0; i < count; i += 1) {
      const t = i / (count - 1);
      result.push(
        point({
          pointIndex: i,
          timestamp: 1_000 + i,
          distanceKm: t * 100,
          lat: 47 + t,
          lng: 8 + t,
        })
      );
    }
    return result;
  }

  const gridPoints = buildGridTrack(2_000);

  it('snaps to the nearest point when the pointer is on the track', () => {
    const index = createTrackPointIndex(gridPoints);
    const target = gridPoints[1_000];

    expect(index.findByLatLng(target.lat, target.lng)?.pointIndex).toBe(target.pointIndex);
  });

  it('returns null when the pointer is far outside the track bounds (zoomed out)', () => {
    const index = createTrackPointIndex(gridPoints);

    expect(index.findByLatLng(40, 2)).toBeNull();
  });

  it('returns null when the pointer is inside the track bounds but far from any point', () => {
    const index = createTrackPointIndex(gridPoints);

    // The track runs along the lat==lng diagonal; this corner is well inside the
    // bounding box but more than the snap radius away from the diagonal.
    expect(index.findByLatLng(47.1, 8.9)).toBeNull();
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

describe('useChartSync marker behavior', () => {
  it('keeps one active hover marker and skips duplicate point refreshes', () => {
    const firstSetState = vi.fn();
    const secondSetState = vi.fn();
    const firstPoint = { x: 10, setState: firstSetState } as unknown as Highcharts.Point;
    const secondPoint = { x: 20, setState: secondSetState } as unknown as Highcharts.Point;
    const drawCrosshair = vi.fn();
    const refresh = vi.fn();
    const searchPoint = vi
      .fn()
      .mockReturnValueOnce(firstPoint)
      .mockReturnValueOnce(firstPoint)
      .mockReturnValueOnce(secondPoint);
    const chart = {
      pointer: {
        normalize: vi.fn(() => ({ chartX: 10, chartY: 10 })),
      },
      series: [
        {
          points: [firstPoint, secondPoint],
          searchPoint,
        },
      ],
      tooltip: {
        hide: vi.fn(),
        refresh,
      },
      xAxis: [
        {
          drawCrosshair,
          hideCrosshair: vi.fn(),
        },
      ],
    } as unknown as Highcharts.Chart;
    const chartSync = useChartSync();

    chartSync.registerChart(chart);
    try {
      chartSync.syncMouseMove(new MouseEvent('mousemove'), chart);

      expect(firstSetState).toHaveBeenCalledWith('hover');
      expect(refresh).toHaveBeenCalledWith(firstPoint);
      expect(drawCrosshair).toHaveBeenCalledWith(undefined, firstPoint);

      chartSync.syncMouseMove(new MouseEvent('mousemove'), chart);

      expect(refresh).toHaveBeenCalledTimes(1);
      expect(drawCrosshair).toHaveBeenCalledTimes(1);
      expect(firstSetState).toHaveBeenCalledTimes(1);

      chartSync.syncMouseMove(new MouseEvent('mousemove'), chart);

      expect(firstSetState).toHaveBeenCalledWith('');
      expect(secondSetState).toHaveBeenCalledWith('hover');
      expect(refresh).toHaveBeenCalledWith(secondPoint);
      expect(drawCrosshair).toHaveBeenCalledWith(undefined, secondPoint);
    } finally {
      chartSync.clearChartCrosshairs();
      chartSync.unregisterChart(chart);
    }
  });
});
