import { describe, expect, it } from 'vitest';
import {
  segmentChartX,
  segmentGeometrySlice,
  segmentGhostAt,
  segmentHoverPosition,
  segmentHoverSample,
  segmentSampleAt,
  type SegmentChartSeries,
} from '@/components/measure/segmentDisplay';
import {
  SegmentComparisonFromJSON,
  SegmentCoreAnalysisFromJSON,
  SegmentHealthAnalysisFromJSON,
} from 'x8ing-mtl-api-typescript-fetch';

describe('server segment values through generated conversion and display', () => {
  it('reads samples without extrapolating, crossing gaps, or replacing stop arrival with departure', () => {
    const series: SegmentChartSeries = {
      id: 'a',
      name: 'A',
      color: '#123456',
      data: [
        [0, 120],
        [0.2, 140],
        [0.4, null],
        [0.4, 160],
        [0.5, 170],
        [0.5, 190],
        [0.6, 200],
      ],
    };
    expect(segmentSampleAt(series, -0.1)).toBeNull();
    expect(segmentSampleAt(series, 0.7)).toBeNull();
    expect(segmentSampleAt(series, 0.1)?.value).toBe(120);
    expect(segmentSampleAt(series, 0.3)).toBeNull();
    expect(segmentSampleAt(series, 0.4)?.value).toBe(160);
    expect(segmentSampleAt(series, 0.5)?.value).toBe(170);
    expect(segmentSampleAt(series, 0.51)?.value).toBe(190);
  });
  it('preserves explicit missing metrics and health progress through the generated API', () => {
    const core = SegmentCoreAnalysisFromJSON({
      summary: { energyWh: null, maximumPowerWatts: 900 },
      points: [
        {
          distanceMeters: 200,
          elapsedSeconds: 10,
          powerWatts: null,
          slopePercent: null,
          energyWh: null,
          breakBefore: true,
        },
      ],
    });
    expect(core.summary?.energyWh).toBeNull();
    expect(core.summary?.maximumPowerWatts).toBe(900);
    expect(core.points?.[0]).toMatchObject({ powerWatts: null, slopePercent: null, energyWh: null, breakBefore: true });
    const health = SegmentHealthAnalysisFromJSON({
      points: [{ distanceMeters: null, elapsedSeconds: 8, averageBpm: 180 }],
    });
    expect(segmentChartX(health.points![0], 'distance')).toBeNull();
    expect(segmentChartX(health.points![0], 'time')).toBe(8000);
  });
  const geometry = [
    { distanceMeters: 0, elapsedSeconds: 0, longitude: 8, latitude: 47 },
    { distanceMeters: 100, elapsedSeconds: 10, longitude: 8.001, latitude: 47 },
    { distanceMeters: 200, elapsedSeconds: 20, longitude: 8.002, latitude: 47, breakBefore: true },
    { distanceMeters: 300, elapsedSeconds: 30, longitude: 8.003, latitude: 47 },
  ];
  it('R12 uses the actual hover domain, avoids recording gaps, and never extrapolates', () => {
    expect(segmentHoverPosition(geometry, { x: 0.05, axis: 'distance' })?.[0]).toBeCloseTo(8.0005);
    expect(segmentHoverPosition(geometry, { x: 5000, axis: 'time' })?.[0]).toBeCloseTo(8.0005);
    expect(segmentHoverPosition(geometry, { x: 0.15, axis: 'distance' })).toBeNull();
    expect(segmentHoverPosition(geometry, { x: -1, axis: 'time' })).toBeNull();
    expect(segmentHoverPosition(geometry, { x: 0.31, axis: 'distance' })).toBeNull();
  });
  it('uses arrival at a stopped distance and retains the stop on the time axis', () => {
    const stopped = [
      geometry[0],
      geometry[1],
      { ...geometry[1], elapsedSeconds: 310 },
      { ...geometry[3], elapsedSeconds: 330 },
    ];
    expect(segmentHoverPosition(stopped, { x: 0.1, axis: 'distance' })).toEqual([8.001, 47]);
    expect(segmentHoverPosition(stopped, { x: 100000, axis: 'time' })?.[0]).toBeCloseTo(8.001);
    expect(segmentHoverSample(stopped, { x: 0.1, axis: 'distance' })?.elapsedSeconds).toBe(10);
    expect(segmentHoverSample(stopped, { x: 0.2, axis: 'distance' })?.elapsedSeconds).toBe(320);
    expect(segmentHoverSample(stopped, { x: 100000, axis: 'time' })?.elapsedSeconds).toBe(100);
  });
  it('resolves replay display time only within supported geometry and preserves missing timing', () => {
    expect(segmentHoverSample(geometry, { x: 0.05, axis: 'distance' })?.elapsedSeconds).toBe(5);
    expect(segmentHoverSample(geometry, { x: 0.15, axis: 'distance' })).toBeNull();
    expect(segmentHoverSample(geometry, { x: 0.4, axis: 'distance' })).toBeNull();
    expect(segmentHoverSample(geometry, { x: NaN, axis: 'distance' })).toBeNull();
    const missingTime = [geometry[0], { ...geometry[1], elapsedSeconds: null }];
    expect(segmentHoverSample(missingTime, { x: 0.05, axis: 'distance' })).toMatchObject({ elapsedSeconds: null });
    const backwardsTime = [{ ...geometry[0], elapsedSeconds: 20 }, geometry[1]];
    expect(segmentHoverSample(backwardsTime, { x: 0.05, axis: 'distance' })?.elapsedSeconds).toBeNull();
  });
  it('wraps display interpolation across the antimeridian', () => {
    const points = [
      { ...geometry[0], longitude: 179 },
      { ...geometry[1], longitude: -179 },
    ];
    expect(Math.abs(segmentHoverPosition(points, { x: 0.05, axis: 'distance' })![0])).toBe(180);
  });
  it('reads server ghosts by reference distance, preferring arrival and never crossing breaks', () => {
    const ghosts = SegmentComparisonFromJSON({
      ghostPoints: [
        { referenceDistanceMeters: 0, distanceMeters: 0, leadMeters: 0, state: 'IN_PROGRESS', breakBefore: false },
        { referenceDistanceMeters: 400, distanceMeters: 450, leadMeters: 50, state: 'IN_PROGRESS', breakBefore: false },
        { referenceDistanceMeters: 400, distanceMeters: 480, leadMeters: 80, state: 'IN_PROGRESS', breakBefore: true },
        {
          referenceDistanceMeters: 800,
          distanceMeters: null,
          leadMeters: null,
          state: 'UNAVAILABLE',
          breakBefore: true,
        },
      ],
    }).ghostPoints;
    expect(segmentGhostAt(ghosts, 0.4)?.distanceMeters).toBe(450);
    expect(segmentGhostAt(ghosts, 0.1)?.leadMeters).toBe(0);
    expect(segmentGhostAt(ghosts, 0.3)?.leadMeters).toBe(50);
    expect(segmentGhostAt(ghosts, 0.7)).toBeNull();
    expect(segmentGhostAt(ghosts, 0.9)).toBeNull();
    expect(segmentGhostAt(undefined, 0.2)).toBeNull();
  });
  it('slices the route between two traveled distances in either order and splits at breaks', () => {
    const geometry = SegmentCoreAnalysisFromJSON({
      geometry: [0, 100, 200, 300].map((distanceMeters, index) => ({
        distanceMeters,
        elapsedSeconds: distanceMeters / 10,
        longitude: 8 + distanceMeters / 1000,
        latitude: 47,
        breakBefore: index === 3,
      })),
    }).geometry!;
    const forward = segmentGeometrySlice(geometry, 0.05, 0.15);
    expect(forward).toHaveLength(1);
    expect(forward[0].map(([longitude]) => longitude.toFixed(6))).toEqual(['8.050000', '8.100000', '8.150000']);
    expect(segmentGeometrySlice(geometry, 0.15, 0.05)).toEqual(forward);
    const broken = segmentGeometrySlice(geometry, 0.15, 0.29);
    expect(broken.map((line) => line.map(([longitude]) => longitude.toFixed(6)))).toEqual([['8.150000', '8.200000']]);
  });
});
