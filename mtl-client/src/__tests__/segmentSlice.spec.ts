import { describe, expect, it } from 'vitest';
import {
  buildVisitSegmentOptions,
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
