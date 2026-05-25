/**
 * Adapter for the on-demand chart-series endpoint
 * (`GET /api/tracks/{id}/chart-series`).
 *
 * Background — see `mtl-server/doc/issues/canonical_metric_lod_architecture.md`.
 * The server no longer pre-computes a SIMPLIFIED_FIXED_POINTS variant per
 * track. Charts are now driven by on-the-fly bucket aggregation over the
 * canonical RAW_OUTLIER_CLEANED stream.
 */
import { ChartSeriesControllerApi } from 'x8ing-mtl-api-typescript-fetch';
import { getApiConfiguration } from '@/utils/openApiClient';
import type {
  ChartBucket,
  ChartSeriesResponse,
  MetricDefinition,
  MetricBucketStats,
} from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import { ChartSeriesResponseXModeEnum } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/ChartSeriesResponse';
import type { ChartSeriesResponseAvailableMetricsEnum as GeneratedMetricKey } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/ChartSeriesResponse';

// Re-export generated types so call sites only need one import.
export type { ChartBucket, ChartSeriesResponse, MetricDefinition, MetricBucketStats };

/**
 * Metric-key string union — mirrors {@code MetricKey} on the server and is
 * identical to the generated {@code ChartSeriesResponseAvailableMetricsEnum}
 * value-set. Exposed as both a type and a runtime value-object so call sites
 * can use `MetricKey.AltitudeM` instead of magic strings.
 */
export type MetricKey = GeneratedMetricKey;
export const MetricKey = {
  AltitudeM: 'ALTITUDE_M',
  DistanceM: 'DISTANCE_M',
  DurationS: 'DURATION_S',
  AscentM: 'ASCENT_M',
  DescentM: 'DESCENT_M',
  SpeedMovingWindowKmh: 'SPEED_MOVING_WINDOW_KMH',
  SpeedWindowKmh: 'SPEED_WINDOW_KMH',
  ElevationGainPerHourWindow: 'ELEVATION_GAIN_PER_HOUR_WINDOW',
  ElevationLossPerHourWindow: 'ELEVATION_LOSS_PER_HOUR_WINDOW',
  SlopePercent: 'SLOPE_PERCENT',
  PowerWatts: 'POWER_WATTS',
  PowerWindowWatts: 'POWER_WINDOW_WATTS',
  EnergyCumulativeWh: 'ENERGY_CUMULATIVE_WH',
} as const satisfies Record<string, GeneratedMetricKey>;

export type XMode = ChartSeriesResponseXModeEnum;
export const XMode = ChartSeriesResponseXModeEnum;

export type ChartPointMetricStats = Partial<Record<MetricKey, MetricBucketStats>>;

// --- chart-point projection --------------------------------------------

/**
 * Per-bucket projection consumed by the Track Details charts. One
 * `ChartPoint` is emitted per `ChartBucket` and carries everything the
 * existing chart configs need to lay out a series.
 *
 * `pointTimestamp` is the bucket's real-world representative timestamp when
 * provided by the chart-series API. The fallback synthetic timestamp keeps the
 * projection compatible with older responses and tests.
 */
export interface ChartPoint {
  pointIndex: number;
  pointTimestamp: Date;
  distanceInMeterSinceStart: number | null;
  metricStats: ChartPointMetricStats;
  pointAltitude: number | null;
  /** Windowed speed (km/h), trailing window — see windowSec in response. */
  speedInKmhWindow: number | null;
  elevationGainPerHourWindow: number | null;
  elevationLossPerHourWindow: number | null;
  /** Windowed estimated mechanical power (W), trailing window. */
  powerWattsWindow: number | null;
  energyCumulativeWh: number | null;
}

export interface FetchChartSeriesOptions {
  xMode?: XMode;
  maxBuckets?: number;
  windowSec?: number;
  from?: number;
  to?: number;
  metrics?: MetricKey[];
}

function getChartSeriesApi() {
  return new ChartSeriesControllerApi(getApiConfiguration());
}

export async function fetchChartSeries(
  gpsTrackId: number | string,
  options: FetchChartSeriesOptions = {}
): Promise<ChartSeriesResponse> {
  const trackId = typeof gpsTrackId === 'number' ? gpsTrackId : Number(gpsTrackId);
  if (!Number.isFinite(trackId)) {
    throw new Error(`fetchChartSeries: invalid trackId ${gpsTrackId}`);
  }
  return getChartSeriesApi().getChartSeries({
    trackId,
    x: options.xMode,
    maxBuckets: options.maxBuckets,
    windowSec: options.windowSec,
    from: options.from,
    to: options.to,
    metrics: options.metrics,
  });
}

/**
 * Project a chart-series response into the flat `ChartPoint[]` shape the
 * existing TrackGraph configs consume.
 *
 * The generated OpenAPI client parses bucket date-time strings into `Date`
 * instances. If the response is missing a representative timestamp, we fall
 * back to the legacy synthetic timestamp derived from `xStart`.
 */
export function chartSeriesToPoints(response: ChartSeriesResponse): ChartPoint[] {
  const points: ChartPoint[] = [];
  const buckets = response.buckets ?? [];
  const xMode = response.xMode ?? XMode.Time;
  for (const b of buckets) {
    const metrics: ChartPointMetricStats = b.metrics ?? {};
    const get = (key: MetricKey, field: keyof MetricBucketStats = 'avg'): number | null => {
      const stats = metrics[key];
      if (!stats) return null;
      const v = stats[field];
      return typeof v === 'number' ? v : null;
    };

    points.push({
      pointIndex: b.representativePointIndex ?? 0,
      pointTimestamp: bucketRepresentativeTimestamp(b, xMode),
      distanceInMeterSinceStart: get(MetricKey.DistanceM, 'last') ?? get(MetricKey.DistanceM),
      metricStats: metrics,
      pointAltitude: get(MetricKey.AltitudeM),
      speedInKmhWindow: get(MetricKey.SpeedWindowKmh),
      elevationGainPerHourWindow: get(MetricKey.ElevationGainPerHourWindow),
      elevationLossPerHourWindow: get(MetricKey.ElevationLossPerHourWindow),
      powerWattsWindow: get(MetricKey.PowerWindowWatts),
      energyCumulativeWh: get(MetricKey.EnergyCumulativeWh, 'last') ?? get(MetricKey.EnergyCumulativeWh),
    });
  }
  return points;
}

function bucketRepresentativeTimestamp(bucket: ChartBucket, xMode: XMode): Date {
  const parsed = bucket.representativeTimestamp;
  if (parsed instanceof Date && Number.isFinite(parsed.getTime())) {
    return parsed;
  }

  const xStart = bucket.xStart ?? 0;
  return new Date(xMode === XMode.Time ? xStart * 1000 : xStart);
}
