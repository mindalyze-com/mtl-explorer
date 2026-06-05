import { describe, expect, it } from 'vitest';
import { buildReplayTelemetry, sampleReplayTelemetry } from '@/components/replay/trackReplayTelemetry';
import { MetricKey, type ChartPoint } from '@/utils/chartSeriesAdapter';

describe('track replay telemetry', () => {
  it('samples speed and ascent by replay progress', () => {
    const telemetry = buildReplayTelemetry({
      chartPoints: [point(0, 0, 0, 0, 450, 5), point(500, 300, 40, 25, 500, 12), point(1000, 600, 80, 15, 530, 8)],
      track: {
        trackLengthInMeter: 1000,
        ascentInMeter: 80,
        maxAltitude: 530,
        speedInKmh30sMax: 30,
      },
      pathTotalDistanceMeters: 1000,
    });

    const sample = sampleReplayTelemetry(telemetry, 0.5);

    expect(sample.distanceMeters).toBe(500);
    expect(sample.speedKmh).toBe(12);
    expect(sample.ascentMeters).toBe(40);
    expect(telemetry.maxSpeedKmh).toBe(30);
    expect(telemetry.hasSpeedData).toBe(true);
  });

  it('keeps total metrics even before chart telemetry loads', () => {
    const telemetry = buildReplayTelemetry({
      chartPoints: [],
      track: {
        trackLengthInMeter: 2500,
        ascentInMeter: 300,
        maxAltitude: 1200,
      },
      pathTotalDistanceMeters: 2400,
    });

    const sample = sampleReplayTelemetry(telemetry, 0.25);

    expect(sample.distanceMeters).toBe(625);
    expect(sample.speedKmh).toBeNull();
    expect(telemetry.totalDistanceMeters).toBe(2500);
    expect(telemetry.totalAscentMeters).toBe(300);
    expect(telemetry.maxElevationMeters).toBe(1200);
    expect(telemetry.hasSpeedData).toBe(false);
  });
});

function point(
  distanceMeters: number,
  durationSeconds: number,
  ascentMeters: number,
  slopePercent: number,
  elevationMeters: number,
  speedKmh: number
): ChartPoint {
  return {
    pointIndex: 0,
    pointTimestamp: new Date(0),
    distanceInMeterSinceStart: distanceMeters,
    metricStats: {
      [MetricKey.DurationS]: { avg: durationSeconds, last: durationSeconds },
      [MetricKey.AscentM]: { avg: ascentMeters, last: ascentMeters },
      [MetricKey.SlopePercent]: { avg: slopePercent },
      [MetricKey.SpeedWindowKmh]: { avg: speedKmh },
      [MetricKey.AltitudeM]: { avg: elevationMeters },
    },
    pointAltitude: elevationMeters,
    speedInKmhWindow: speedKmh,
    speedBucketAvgKmh: null,
    elevationGainPerHourWindow: null,
    elevationLossPerHourWindow: null,
    powerWattsWindow: null,
    energyCumulativeWh: null,
  };
}
