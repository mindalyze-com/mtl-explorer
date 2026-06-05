import { describe, expect, it } from 'vitest';
import { speedGraphConfigFor } from '@/components/trackdetails/trackGraphConfigs';
import { MetricKey } from '@/utils/chartSeriesAdapter';

describe('speedGraphConfigFor', () => {
  it('keeps the window-speed config when recommended', () => {
    const config = speedGraphConfigFor(MetricKey.SpeedWindowKmh);

    expect(config.title).toBe('Speed');
    expect(config.rangeMetricKey).toBe(MetricKey.SpeedWindowKmh);
  });

  it('uses bucket-average speed when recommended', () => {
    const config = speedGraphConfigFor(MetricKey.SpeedBucketAvgKmh);

    expect(config.title).toBe('Speed (bucket avg)');
    expect(config.rangeMetricKey).toBe(MetricKey.SpeedBucketAvgKmh);
    expect(config.extractY({
      pointIndex: 0,
      pointTimestamp: new Date(0),
      distanceInMeterSinceStart: 0,
      metricStats: {},
      pointAltitude: null,
      speedInKmhWindow: null,
      speedBucketAvgKmh: 12.5,
      elevationGainPerHourWindow: null,
      elevationLossPerHourWindow: null,
      powerWattsWindow: null,
      energyCumulativeWh: null,
    })).toBe(12.5);
  });

  it('falls back to window-speed config without a recommendation', () => {
    const config = speedGraphConfigFor(null);

    expect(config.title).toBe('Speed');
    expect(config.rangeMetricKey).toBe(MetricKey.SpeedWindowKmh);
  });
});
