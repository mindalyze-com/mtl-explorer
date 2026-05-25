import type { ChartPoint, MetricKey } from '@/utils/chartSeriesAdapter';
import type { ChartThemeConfig } from '@/utils/chartTheme';

/**
 * Configuration for a single track-detail graph.
 *
 * Replaces the 6 near-identical TrackDetail*Graph.vue files with a single
 * <TrackGraph :config="..."> driven by these declarative configs.
 *
 * The graphs consume `ChartPoint`s produced by the on-demand chart-series
 * endpoint (see `chartSeriesAdapter.ts`) — each ChartPoint represents one
 * equal-width bucket over the canonical RAW_OUTLIER_CLEANED stream.
 */
export interface TrackGraphConfig extends ChartThemeConfig {
  /** Bootstrap-icon class (without the leading 'bi-'-prefix wrapper). */
  icon: string;
  /** Header label shown above the chart. */
  title: string;
  /** Pulls the y-value out of a chart point. Return null/undefined to skip the point. */
  extractY: (point: ChartPoint) => number | null | undefined;
  /** Metric bucket stats used for optional min/max range rendering. */
  rangeMetricKey?: MetricKey;
  /** When true, points where extractY returns null/undefined are filtered out
   *  rather than emitted as null. Matches the legacy Energy/Power behavior. */
  filterNullY?: boolean;
}

const elevationConfig: TrackGraphConfig = {
  icon: 'bi-graph-up-arrow',
  title: 'Elevation',
  seriesName: 'Elevation',
  seriesColor: '#6366f1',
  unit: 'm',
  decimals: 0,
  rangeMetricKey: 'ALTITUDE_M',
  extractY: (p) => p.pointAltitude,
};

const elevationGainConfig: TrackGraphConfig = {
  icon: 'bi-arrow-up-right-circle',
  title: 'Elevation Gain Rate',
  seriesName: 'Elevation Gain',
  seriesColor: '#16a34a',
  unit: 'm/h',
  decimals: 0,
  filterNullY: true,
  rangeMetricKey: 'ELEVATION_GAIN_PER_HOUR_WINDOW',
  extractY: (p) => p.elevationGainPerHourWindow,
};

const speedConfig: TrackGraphConfig = {
  icon: 'bi-speedometer2',
  title: 'Speed',
  seriesName: 'Speed',
  seriesColor: '#f97316',
  unit: 'km/h',
  decimals: 1,
  yMin: 0,
  connectNulls: true,
  filterNullY: true,
  rangeMetricKey: 'SPEED_WINDOW_KMH',
  extractY: (p) => p.speedInKmhWindow,
};

const distanceConfig: TrackGraphConfig = {
  icon: 'bi-signpost-split',
  title: 'Distance over Time',
  seriesName: 'Distance',
  seriesColor: '#7c3aed',
  unit: 'km',
  decimals: 2,
  yMin: 0,
  extractY: (p) => (p.distanceInMeterSinceStart ?? 0) / 1000,
};

const energyConfig: TrackGraphConfig = {
  icon: 'bi-battery-charging',
  title: 'Cumulative Mechanical Energy',
  seriesName: 'Cumulative Mechanical Energy',
  seriesColor: '#d97706',
  unit: 'Wh',
  decimals: 1,
  yMin: 0,
  filterNullY: true,
  extractY: (p) => (p.energyCumulativeWh != null ? Math.round(p.energyCumulativeWh * 10) / 10 : null),
};

const powerConfig: TrackGraphConfig = {
  icon: 'bi-lightning-charge',
  title: 'Estimated Power',
  seriesName: 'Estimated Power',
  seriesColor: '#ef4444',
  unit: 'W',
  decimals: 0,
  yMin: 0,
  filterNullY: true,
  rangeMetricKey: 'POWER_WINDOW_WATTS',
  extractY: (p) => (p.powerWattsWindow != null ? Math.round(p.powerWattsWindow) : null),
};

export const trackGraphConfigs = {
  elevation: elevationConfig,
  elevationGain: elevationGainConfig,
  speed: speedConfig,
  distance: distanceConfig,
  energy: energyConfig,
  power: powerConfig,
} as const;

export type TrackGraphConfigKey = keyof typeof trackGraphConfigs;
