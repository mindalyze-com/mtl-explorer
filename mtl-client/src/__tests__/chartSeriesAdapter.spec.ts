import { describe, expect, it } from 'vitest';
import {
  chartSeriesToPoints,
  chartSeriesToTrackChartSeries,
  MetricKey,
  XMode,
  type ChartSeriesResponse,
} from '@/utils/chartSeriesAdapter';

describe('chartSeriesToPoints', () => {
  it('anchors missing representative timestamps to the response start time', () => {
    const points = chartSeriesToPoints({
      xMode: XMode.Time,
      startTimestamp: new Date('2012-04-30T07:32:58Z'),
      buckets: [
        {
          index: 0,
          xStart: 60,
          xEnd: 120,
          representativePointIndex: 12,
          metrics: {},
        },
      ],
    } as ChartSeriesResponse);

    expect(points).toHaveLength(1);
    expect(points[0].pointTimestamp.toISOString()).toBe('2012-04-30T07:34:28.000Z');
  });

  it('parses representative timestamp strings when a raw response is projected', () => {
    const points = chartSeriesToPoints({
      xMode: XMode.Time,
      startTimestamp: '2012-04-30T07:32:58Z',
      buckets: [
        {
          index: 0,
          xStart: 0,
          xEnd: 60,
          representativePointIndex: 12,
          representativeTimestamp: '2012-04-30T07:33:20Z',
          metrics: {},
        },
      ],
    } as unknown as ChartSeriesResponse);

    expect(points[0].pointTimestamp.toISOString()).toBe('2012-04-30T07:33:20.000Z');
  });

  it('maps bucket-average speed into chart points', () => {
    const points = chartSeriesToPoints({
      xMode: XMode.Time,
      buckets: [
        {
          index: 0,
          xStart: 0,
          xEnd: 60,
          representativePointIndex: 12,
          metrics: {
            [MetricKey.SpeedBucketAvgKmh]: {
              avg: 18.25,
              min: 10,
              max: 22,
              sampleCount: 3,
            },
          },
        },
      ],
    } as ChartSeriesResponse);

    expect(points[0].speedBucketAvgKmh).toBe(18.25);
  });

  it('returns chart points with recommendation metadata', () => {
    const series = chartSeriesToTrackChartSeries({
      xMode: XMode.Time,
      availableMetrics: [MetricKey.SpeedBucketAvgKmh],
      recommendedSpeedMetric: MetricKey.SpeedBucketAvgKmh,
      buckets: [
        {
          index: 0,
          xStart: 0,
          xEnd: 60,
          representativePointIndex: 12,
          metrics: {},
        },
      ],
    } as ChartSeriesResponse);

    expect(series.points).toHaveLength(1);
    expect(series.availableMetrics).toEqual([MetricKey.SpeedBucketAvgKmh]);
    expect(series.recommendedSpeedMetric).toBe(MetricKey.SpeedBucketAvgKmh);
  });
});
