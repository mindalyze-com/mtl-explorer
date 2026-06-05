import { describe, expect, it } from 'vitest';
import {
  buildReplayPath,
  buildTimedReplayPath,
  distanceProgressForReplaySample,
  formatReplaySpeedFactor,
  interpolateBearing,
  replaySpeedFactor,
  sampleReplayPathAtElapsedSeconds,
  sampleReplayPath,
  sanitizeReplayTargetDuration,
  trackDurationSeconds,
} from '@/components/replay/trackReplayPath';
import type { ChartPoint } from '@/utils/chartSeriesAdapter';
import type { GpsTrackDataPoint } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';

describe('track replay path helpers', () => {
  it('resamples detail coordinates and samples start, middle, and end positions', () => {
    const path = buildReplayPath({
      coordinates: [
        [8, 47, 500],
        [8, 47, 500],
        [8.01, 47, 520],
      ],
      originalDurationSeconds: 3600,
    });

    expect(path.points.length).toBeGreaterThan(2);
    expect(path.totalDistanceMeters).toBeGreaterThan(700);
    expect(path.originalDurationSeconds).toBe(3600);

    const start = sampleReplayPath(path, 0);
    const middle = sampleReplayPath(path, 0.5);
    const end = sampleReplayPath(path, 1);

    expect(start?.lng).toBeCloseTo(8);
    expect(middle?.lng).toBeGreaterThan(8);
    expect(middle?.lng).toBeLessThan(8.01);
    expect(end?.lng).toBeCloseTo(8.01);
    expect(end?.elevation).toBeCloseTo(520);
  });

  it('assigns endpoint headings from the available segment', () => {
    const path = buildReplayPath({
      coordinates: [
        [8, 47, 500],
        [8.02, 47, 520],
      ],
    });

    expect(sampleReplayPath(path, 0)?.headingDegrees).toBeCloseTo(90, 0);
    expect(sampleReplayPath(path, 1)?.headingDegrees).toBeCloseTo(90, 0);
  });

  it('uses full timestamp span before moving duration', () => {
    expect(
      trackDurationSeconds({
        trackDurationInMotionSecs: 42,
        startDate: new Date('2026-01-01T10:00:00Z'),
        endDate: new Date('2026-01-01T10:30:00Z'),
      })
    ).toBe(1800);
    expect(trackDurationSeconds({ trackDurationInMotionSecs: 42 })).toBe(42);
    expect(trackDurationSeconds({ startDate: new Date('2026-01-01T10:00:00Z') })).toBeNull();
  });

  it('computes meaningful duration-based replay speed factors', () => {
    expect(sanitizeReplayTargetDuration(1)).toBe(5);
    expect(sanitizeReplayTargetDuration(120)).toBe(90);
    expect(replaySpeedFactor(3600, 45)).toBe(80);
    expect(formatReplaySpeedFactor(3600, 45)).toBe('80x');
    expect(formatReplaySpeedFactor(null, 45)).toBe('Distance based');
  });

  it('builds a timestamp-based replay path that preserves breaks', () => {
    const path = buildTimedReplayPath({
      chartPoints: [
        chartPoint(0, '2026-01-01T10:00:00Z', 0),
        chartPoint(10, '2026-01-01T10:00:10Z', 100),
        chartPoint(10, '2026-01-01T10:01:10Z', 100),
        chartPoint(20, '2026-01-01T10:01:20Z', 200),
      ],
      renderedShapePoints: [renderedPoint(0, 8, 47), renderedPoint(10, 8.001, 47), renderedPoint(20, 8.002, 47)],
    });

    expect(path.timingMode).toBe('time');
    expect(path.originalDurationSeconds).toBe(80);

    const moving = sampleReplayPathAtElapsedSeconds(path, 5);
    expect(moving?.distanceMeters).toBeCloseTo(50);
    expect(moving?.lng).toBeGreaterThan(8);
    expect(moving?.lng).toBeLessThan(8.001);

    const stopped = sampleReplayPathAtElapsedSeconds(path, 40);
    expect(stopped?.distanceMeters).toBeCloseTo(100);
    expect(stopped?.lng).toBeCloseTo(8.001);
    expect(stopped?.progress).toBeCloseTo(0.5);
    expect(distanceProgressForReplaySample(path, stopped)).toBeCloseTo(0.5);
  });

  it('interpolates bearings through north without long rotation jumps', () => {
    expect(interpolateBearing(350, 10, 0.5)).toBeCloseTo(0);
    expect(interpolateBearing(10, 350, 0.5)).toBeCloseTo(0);
  });
});

function chartPoint(pointIndex: number, timestamp: string, distanceMeters: number): ChartPoint {
  return {
    pointIndex,
    pointTimestamp: new Date(timestamp),
    distanceInMeterSinceStart: distanceMeters,
    metricStats: {},
    pointAltitude: null,
    speedInKmhWindow: null,
    speedBucketAvgKmh: null,
    elevationGainPerHourWindow: null,
    elevationLossPerHourWindow: null,
    powerWattsWindow: null,
    energyCumulativeWh: null,
  };
}

function renderedPoint(canonicalPointIndex: number, lng: number, lat: number): GpsTrackDataPoint {
  return {
    canonicalPointIndex,
    pointIndex: canonicalPointIndex,
    pointLongLat: { coordinates: [lng, lat] },
    pointAltitude: 500,
  } as GpsTrackDataPoint;
}
