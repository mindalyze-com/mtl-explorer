import { describe, expect, it } from 'vitest';
import {
  buildVisitSegmentOptions,
  segmentSelectionKey,
  normalizeSegmentSlice,
  type SegmentSliceCrossing,
  type SegmentTrackPoint,
} from '@/components/measure/segmentSlice';

describe('buildVisitSegmentOptions', () => {
  it('counts numbered visit pairs across tracks', () => {
    const crossings = (names: string[]) => names.map((name) => ({ triggerPoint: { name } }));

    expect(buildVisitSegmentOptions([crossings(['A', 'B', 'A']), crossings(['A', 'B'])])).toEqual([
      {
        name: 'A1 - B1',
        count: 2,
        code: { point1: 'A', p1Visit: 1, point2: 'B', p2Visit: 1, consolidated: false },
      },
      {
        name: 'B1 - A2',
        count: 1,
        code: { point1: 'B', p1Visit: 1, point2: 'A', p2Visit: 2, consolidated: false },
      },
    ]);
  });
});

type TestPoint = SegmentTrackPoint & {
  speedInKmhMovingWindow?: number;
};

function point(
  id: number,
  durationSinceStart: number,
  distanceInMeterSinceStart: number,
  coordinates: [number, number]
): TestPoint {
  return {
    id,
    durationSinceStart,
    distanceInMeterSinceStart,
    pointLongLat: { coordinates },
    speedInKmhMovingWindow: 20,
  };
}

function crossing(
  gpsTrackDataPoint: TestPoint,
  timeInSecSinceLastTriggerPoint = 0,
  distanceInMeterSinceLastTriggerPoint = 0
): SegmentSliceCrossing<TestPoint> {
  return {
    gpsTrackDataPoint,
    timeInSecSinceLastTriggerPoint,
    distanceInMeterSinceLastTriggerPoint,
  };
}

describe('normalizeSegmentSlice', () => {
  it('retains chronological sub-track rebasing from the server when stored indexes and cumulative fields reverse', () => {
    const start = {
      ...point(10, 100, 1000, [8, 47]),
      pointIndex: 10,
      pointTimestamp: new Date('2026-01-01T00:00:00Z'),
    };
    const end = { ...point(12, 20, 200, [8.01, 47]), pointIndex: 2, pointTimestamp: new Date('2026-01-01T00:01:00Z') };
    const middle = {
      ...point(11, 130, 1500, [8.005, 47]),
      pointIndex: 1,
      pointTimestamp: new Date('2026-01-01T00:00:30Z'),
    };
    const result = normalizeSegmentSlice(
      [start, middle, { ...end, durationSinceStart: 160, distanceInMeterSinceStart: 2000 }],
      [
        { ...crossing(start), trackPointPosition: 10 },
        { ...crossing(end, 60, 1000), trackPointPosition: 2 },
      ]
    );
    expect(result.valid).toBe(true);
    expect(result.points.map((p) => p.durationSinceStart)).toEqual([100, 130, 160]);
    expect(result.durationSec).toBe(60);
    expect(result.distanceM).toBe(1000);
  });
  it('does not recreate an unavailable traversal from positive cumulative fields', () => {
    const start = point(1, 0, 0, [8, 47]);
    const end = point(2, 100, 500, [8.01, 47]);
    const result = normalizeSegmentSlice(
      [start, end],
      [
        crossing(start),
        {
          ...crossing(end),
          timingStatusSinceLastTriggerPoint: 'RECORDING_GAP',
          timeInSecSinceLastTriggerPoint: null,
          distanceInMeterSinceLastTriggerPoint: null,
        },
      ]
    );
    expect(result.valid).toBe(false);
    expect(result.reason).toBe('unavailable_timing');
  });

  it('uses fractional path positions to retain untimed interior geometry', () => {
    const start = { ...point(1, 0, 0, [8, 47]), pointIndex: 0 };
    const bend = { ...point(2, 50, 100, [8.001, 47.001]), pointIndex: 1, durationSinceStart: null };
    const end = { ...point(3, 100, 200, [8.002, 47]), pointIndex: 2 };
    const result = normalizeSegmentSlice(
      [start, bend, end],
      [
        { ...crossing(start), trackPointPosition: 0 },
        { ...crossing(end, 100, 200), trackPointPosition: 2, timingStatusSinceLastTriggerPoint: 'AVAILABLE' },
      ]
    );
    expect(result.points).toEqual([start, bend, end]);
  });

  it('keeps a valid A-to-A segment when the two visits have real extent', () => {
    const start = point(1, 10, 100, [8, 47]);
    const end = point(2, 70, 600, [8.01, 47.01]);

    const result = normalizeSegmentSlice([start, end], [crossing(start), crossing(end, 60, 500)], {
      requirePositiveDuration: true,
      requirePositiveDistanceOrGeometry: true,
    });

    expect(result.valid).toBe(true);
    expect(result.durationSec).toBe(60);
    expect(result.distanceM).toBe(500);
    expect(result.points).toHaveLength(2);
  });

  it('rejects a same-row segment with no time, distance, or coordinate extent', () => {
    const same = point(1, 10, 100, [8, 47]);

    const result = normalizeSegmentSlice([same], [crossing(same), crossing(same)], {
      requirePositiveDuration: true,
      requirePositiveDistanceOrGeometry: true,
    });

    expect(result.valid).toBe(false);
    expect(result.reason).toBe('not_enough_points');
  });

  it('can build a sparse valid slice from virtual endpoints that share one stored row', () => {
    const stored = point(1, 10, 100, [8, 47]);
    const virtualStart = { ...stored, pointLongLat: { coordinates: [8, 47] } };
    const virtualEnd = { ...stored, durationSinceStart: 70, pointLongLat: { coordinates: [8.01, 47.01] } };

    const result = normalizeSegmentSlice([stored], [crossing(virtualStart), crossing(virtualEnd, 60, 0)], {
      requirePositiveDuration: true,
      requirePositiveDistanceOrGeometry: true,
    });

    expect(result.valid).toBe(true);
    expect(result.points).toEqual([virtualStart, virtualEnd]);
  });

  it('rejects slices whose virtual endpoint coordinates are not numeric', () => {
    const start = {
      ...point(1, 10, 100, [8, 47]),
      pointLongLat: { coordinates: [{ x: undefined }, { y: undefined }] as unknown as number[] },
    };
    const end = point(2, 70, 600, [8.01, 47.01]);

    const result = normalizeSegmentSlice([start, end], [crossing(start), crossing(end, 60, 500)], {
      requirePositiveDuration: true,
      requirePositiveDistanceOrGeometry: true,
    });

    expect(result.valid).toBe(false);
    expect(result.reason).toBe('invalid_coordinates');
  });
});

describe('segment selection identity', () => {
  it('keeps names and visit numbers separate and excludes unavailable attempts', () => {
    expect(
      segmentSelectionKey({ code: { point1: 'A1', p1Visit: 1, point2: 'B', p2Visit: 1, consolidated: false } })
    ).not.toBe(
      segmentSelectionKey({ code: { point1: 'A', p1Visit: 11, point2: 'B', p2Visit: 1, consolidated: false } })
    );
    expect(
      buildVisitSegmentOptions([
        [
          { triggerPoint: { name: 'A' } },
          {
            triggerPoint: { name: 'B' },
            timingStatusSinceLastTriggerPoint: 'RECORDING_GAP',
          },
        ],
      ])
    ).toEqual([]);
  });
});
