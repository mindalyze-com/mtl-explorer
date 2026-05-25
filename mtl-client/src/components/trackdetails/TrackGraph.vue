<template>
  <div class="chart-card">
    <div class="chart-header">
      <i class="bi" :class="config.icon"></i>
      {{ config.title }}
    </div>
    <highcharts ref="highchartsEl" :options="chartOptions" class="chart"></highcharts>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue';
import { useChartSync } from '@/composables/useChartSync';
import { buildChartOptions, hexToRgba } from '@/utils/chartTheme';
import type { ChartPoint } from '@/utils/chartSeriesAdapter';
import type Highcharts from 'highcharts';
import type { TrackGraphConfig } from './trackGraphConfigs';

const RANGE_BAND_ALPHA = 0.16;
const RANGE_BAND_LINE_WIDTH = 0;
const AVERAGE_LINE_WIDTH = 2;
const RANGE_BAND_Z_INDEX = 1;
const AVERAGE_LINE_Z_INDEX = 2;

type TrackGraphDataPoint = {
  x: number;
  y: number | null | undefined;
  ts: number;
  canonicalPointIndex: number;
  rangeLow?: number;
  rangeHigh?: number;
};

type TrackGraphRangeDataPoint = {
  x: number;
  low: number;
  high: number;
  ts: number;
  canonicalPointIndex: number;
};

type TrackGraphProps = {
  trackDetails: ChartPoint[];
  config: TrackGraphConfig;
  xMode?: 'time' | 'distance';
  syncEnabled?: boolean;
  showRange?: boolean;
};

type HighchartsEl = {
  chart?: Highcharts.Chart;
};

defineOptions({
  name: 'TrackGraph',
});

const props = withDefaults(defineProps<TrackGraphProps>(), {
  xMode: 'time',
  syncEnabled: true,
  showRange: false,
});

const config = computed(() => props.config);
const highchartsEl = ref<HighchartsEl | null>(null);
const chartOptions = shallowRef<Highcharts.Options>(buildTrackGraphOptions(props.config, props.xMode, props.showRange));
const { bindChart, setChartXMode } = useChartSync();
let cleanupChartSync: (() => void) | undefined;

/**
 * Generic per-metric track-detail chart.
 *
 * Replaces TrackDetailDistance/Elevation/ElevationGain/Energy/Power/SpeedGraph.vue.
 * Each call site supplies a {@link TrackGraphConfig} from `trackGraphConfigs.ts`
 * that describes the icon, header, color, units, and y-extractor.
 */
onMounted(() => {
  if (props.trackDetails.length > 0) {
    updateChart();
  }
  nextTick(refreshChartSyncBinding);
});

onBeforeUnmount(() => {
  unbindChartSync();
});

watch(() => props.trackDetails, updateChart);

watch(
  () => props.xMode,
  () => {
    rebuildChartOptions();
    const chart = chartInstance();
    if (chart) setChartXMode(chart, props.xMode);
    updateChart();
  }
);

watch(
  () => props.showRange,
  () => {
    rebuildChartOptions();
    updateChart();
  }
);

watch(
  () => props.config,
  () => {
    rebuildChartOptions();
    updateChart();
  },
  { deep: true }
);

watch(
  () => props.syncEnabled,
  () => {
    nextTick(refreshChartSyncBinding);
  }
);

function chartInstance(): Highcharts.Chart | null {
  return highchartsEl.value?.chart ?? null;
}

function unbindChartSync() {
  if (cleanupChartSync) {
    cleanupChartSync();
    cleanupChartSync = undefined;
  }
}

function refreshChartSyncBinding() {
  if (!props.syncEnabled) {
    unbindChartSync();
    return;
  }

  if (cleanupChartSync) return;

  const chart = chartInstance();
  if (chart) {
    setChartXMode(chart, props.xMode);
    cleanupChartSync = bindChart(chart, props.xMode);
  }
}

function rebuildChartOptions() {
  chartOptions.value = buildTrackGraphOptions(props.config, props.xMode, props.showRange);
}

function updateChart() {
  const startTs = props.trackDetails.length > 0 ? toMillis(props.trackDetails[0].pointTimestamp) : 0;
  const isDistance = props.xMode === 'distance';
  const extractY = props.config.extractY;
  const filterNulls = props.config.filterNullY === true;
  const rangeMetricKey = props.config.rangeMetricKey;
  const shouldShowRange = shouldRenderRangeBand(props.config, props.showRange);

  const data: TrackGraphDataPoint[] = [];
  const rangeData: TrackGraphRangeDataPoint[] = [];
  for (const item of props.trackDetails) {
    const y = extractY(item);
    if (filterNulls && (y === null || y === undefined)) continue;
    const absTs = toMillis(item.pointTimestamp);
    const x = isDistance ? (item.distanceInMeterSinceStart ?? 0) / 1000 : absTs - startTs;
    const point: TrackGraphDataPoint = { x, y, ts: absTs, canonicalPointIndex: item.pointIndex };

    if (shouldShowRange && rangeMetricKey) {
      const stats = item.metricStats?.[rangeMetricKey];
      const rangeLow = typeof stats?.min === 'number' ? stats.min : null;
      const rangeHigh = typeof stats?.max === 'number' ? stats.max : null;
      if (rangeLow !== null && rangeHigh !== null) {
        point.rangeLow = rangeLow;
        point.rangeHigh = rangeHigh;
        rangeData.push({ x, low: rangeLow, high: rangeHigh, ts: absTs, canonicalPointIndex: item.pointIndex });
      }
    }

    data.push(point);
  }

  const currentSeries = (chartOptions.value.series ?? []) as Highcharts.SeriesOptionsType[];
  if (currentSeries.length === 0) return;

  chartOptions.value = {
    ...chartOptions.value,
    series: currentSeries.map((series, index): Highcharts.SeriesOptionsType => {
      if (index === 0) {
        return { ...series, data } as Highcharts.SeriesOptionsType;
      }
      if (shouldShowRange && index === 1) {
        return { ...series, data: rangeData } as Highcharts.SeriesOptionsType;
      }
      return series;
    }),
  };
}

function shouldRenderRangeBand(config: TrackGraphConfig, showRange: boolean): boolean {
  return showRange && config.rangeMetricKey != null;
}

function buildTrackGraphOptions(
  config: TrackGraphConfig,
  xMode: 'time' | 'distance',
  showRange: boolean
): Highcharts.Options {
  const options = buildChartOptions({ ...config, xMode });
  if (!shouldRenderRangeBand(config, showRange)) {
    return options;
  }

  const bandColor = hexToRgba(config.seriesColor, RANGE_BAND_ALPHA);
  return {
    ...options,
    chart: {
      ...options.chart,
      type: 'line',
    },
    series: [
      {
        type: 'line',
        name: config.seriesName,
        data: [],
        color: config.seriesColor,
        lineWidth: AVERAGE_LINE_WIDTH,
        zIndex: AVERAGE_LINE_Z_INDEX,
        connectNulls: config.connectNulls ?? false,
        marker: {
          enabled: false,
          states: { hover: { enabled: true, radius: 3, lineWidth: 0 } },
        },
        states: { hover: { lineWidthPlus: 0 } },
      },
      {
        type: 'arearange',
        name: `${config.seriesName} range`,
        data: [],
        color: bandColor,
        fillColor: bandColor,
        lineWidth: RANGE_BAND_LINE_WIDTH,
        linkedTo: ':previous',
        enableMouseTracking: false,
        showInLegend: false,
        zIndex: RANGE_BAND_Z_INDEX,
        marker: { enabled: false },
      },
    ] as Highcharts.SeriesOptionsType[],
  };
}

function toMillis(ts: ChartPoint['pointTimestamp']): number {
  if (!ts) return 0;
  if (typeof ts === 'string') return new Date(ts).getTime();
  return (ts as Date).getTime?.() ?? 0;
}
</script>

<style scoped>
.chart-card {
  display: flex;
  flex-direction: column;
  width: 100%;
  padding-bottom: 1rem;
  border-bottom: 1px solid var(--border-subtle);
}

.chart-card:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.chart-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: var(--text-xs-size);
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--text-secondary);
  padding: 1.25rem 1rem 0.1rem;
}

.chart-header i {
  font-size: var(--text-sm-size);
}

.chart {
  width: 100%;
  height: var(--track-detail-graph-height, 220px);
  min-height: var(--track-detail-graph-height, 220px);
  touch-action: pan-y;
}
</style>
