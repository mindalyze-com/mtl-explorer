<template>
  <div class="chart-card">
    <div class="chart-header chart-section-header">
      <span class="chart-header__title">
        <i class="bi" :class="config.icon"></i>
        {{ config.title }}
      </span>
      <span v-if="sourceNote" class="track-data-note">{{ sourceNote }}</span>
    </div>
    <p v-if="!hasMetricData" class="chart-empty" role="status">
      {{ emptyMessage || `${config.title} data is unavailable for this track.` }}
    </p>
    <highcharts v-show="hasMetricData" ref="highchartsEl" :options="chartOptions" class="chart"></highcharts>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue';
import { useChartSync } from '@/composables/useChartSync';
import { buildChartOptions, hexToRgba } from '@/utils/chartTheme';
import type { ChartPoint } from '@/utils/chartSeriesAdapter';
import type Highcharts from 'highcharts';
import type { TrackGraphConfig } from './trackGraphConfigs';
import { useMeasurementSystem } from '@/composables/useMeasurementSystem';
import { useTheme } from '@/composables/useTheme';

const RANGE_BAND_ALPHA = 0.16;
const RANGE_BAND_LINE_WIDTH = 0;
const AVERAGE_LINE_WIDTH = 2;
const RANGE_BAND_Z_INDEX = 1;
const AVERAGE_LINE_Z_INDEX = 2;
const GRAPH_MARGIN_LEFT = 80;
const GRAPH_MARGIN_RIGHT = 16;

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
  xStartTimestamp?: Date | string | number | null;
  xMax?: number | null;
  sourceNote?: string;
  emptyMessage?: string;
};

type HighchartsEl = {
  chart?: Highcharts.Chart;
};
type SeriesWithPointEvents = Highcharts.SeriesOptionsType & {
  point?: {
    events?: Highcharts.PointEventsOptionsObject;
  };
};

defineOptions({
  name: 'TrackGraph',
});

const props = withDefaults(defineProps<TrackGraphProps>(), {
  xMode: 'time',
  syncEnabled: true,
  showRange: false,
  xStartTimestamp: null,
  xMax: null,
  sourceNote: '',
  emptyMessage: '',
});

const config = computed(() => props.config);
const hasMetricData = computed(() => props.trackDetails.some((point) => Number.isFinite(props.config.extractY(point))));
const highchartsEl = ref<HighchartsEl | null>(null);
const chartOptions = shallowRef<Highcharts.Options>(buildTrackGraphOptions(props.config, props.xMode, props.showRange));
const { bindChart, setChartXMode, syncPointHover } = useChartSync();
const { measurementSystem } = useMeasurementSystem();
const { colorScheme } = useTheme();
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

watch(hasMetricData, (available) => {
  if (available) nextTick(() => chartInstance()?.reflow());
});

watch(() => props.xStartTimestamp, updateChart);
watch(() => props.xMax, updateChart);

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
  [measurementSystem, colorScheme],
  () => {
    rebuildChartOptions();
    updateChart();
  },
  { flush: 'post' }
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
  const startTs =
    props.xStartTimestamp != null
      ? toMillis(props.xStartTimestamp)
      : props.trackDetails.length > 0
        ? toMillis(props.trackDetails[0].pointTimestamp)
        : 0;
  const isDistance = props.xMode === 'distance';
  const extractY = props.config.extractY;
  const filterNulls = props.config.filterNullY === true;
  const rangeMetricKey = props.config.rangeMetricKey;
  const extractRange = props.config.extractRange;
  const shouldShowRange = shouldRenderRangeBand(props.config, props.showRange);

  const sections: Array<{ data: TrackGraphDataPoint[]; rangeData: TrackGraphRangeDataPoint[] }> = [
    { data: [], rangeData: [] },
  ];
  let section = sections[0];
  let previousTimestamp: number | null = null;
  for (const item of props.trackDetails) {
    const absTs = toMillis(item.pointTimestamp);
    // Split before metric filtering: a missing speed/rate value must not hide
    // a clock reversal. Separate series also keep range fills and connectNulls
    // from joining sections, while each section remains sorted for Highcharts.
    if (!isDistance) {
      if (!Number.isFinite(absTs) || (previousTimestamp !== null && absTs < previousTimestamp)) {
        if (section.data.length > 0) {
          section = { data: [], rangeData: [] };
          sections.push(section);
        }
      }
      previousTimestamp = Number.isFinite(absTs) ? absTs : null;
      if (!Number.isFinite(absTs)) continue;
    }
    const y = extractY(item);
    if (filterNulls && (y === null || y === undefined)) continue;
    const x = isDistance ? (item.distanceInMeterSinceStart ?? 0) / 1000 : absTs - startTs;
    const point: TrackGraphDataPoint = { x, y, ts: absTs, canonicalPointIndex: item.pointIndex };

    if (shouldShowRange) {
      const extractedRange = extractRange?.(item) ?? null;
      const stats = rangeMetricKey ? item.metricStats?.[rangeMetricKey] : null;
      const rangeLow = extractedRange?.low ?? (typeof stats?.min === 'number' ? stats.min : null);
      const rangeHigh = extractedRange?.high ?? (typeof stats?.max === 'number' ? stats.max : null);
      if (rangeLow !== null && rangeHigh !== null) {
        const low = Math.min(rangeLow, rangeHigh);
        const high = Math.max(rangeLow, rangeHigh);
        point.rangeLow = low;
        point.rangeHigh = high;
        section.rangeData.push({ x, low, high, ts: absTs, canonicalPointIndex: item.pointIndex });
      }
    }

    section.data.push(point);
  }

  const templates = buildTrackGraphOptions(props.config, props.xMode, props.showRange).series ?? [];
  const series: Highcharts.SeriesOptionsType[] = [];
  for (const [index, section] of sections.entries()) {
    const valueSeriesId = `track-values-${index}`;
    series.push({
      ...templates[0],
      id: valueSeriesId,
      data: section.data,
      ...(index > 0 ? { linkedTo: 'track-values-0', showInLegend: false } : {}),
    } as Highcharts.SeriesOptionsType);
    if (shouldShowRange) {
      series.push({
        ...templates[1],
        id: `track-range-${index}`,
        linkedTo: valueSeriesId,
        data: section.rangeData,
      } as Highcharts.SeriesOptionsType);
    }
  }

  chartOptions.value = {
    ...chartOptions.value,
    xAxis: {
      ...chartOptions.value.xAxis,
      min: props.xMax != null ? 0 : undefined,
      max: props.xMax ?? undefined,
      minPadding: 0,
      maxPadding: 0,
      startOnTick: false,
      endOnTick: false,
    },
    series,
  };
}

function shouldRenderRangeBand(config: TrackGraphConfig, showRange: boolean): boolean {
  return showRange && (config.rangeMetricKey != null || config.extractRange != null);
}

function buildTrackGraphOptions(
  config: TrackGraphConfig,
  xMode: 'time' | 'distance',
  showRange: boolean
): Highcharts.Options {
  const baseOptions = buildChartOptions({ ...config, xMode });
  const options: Highcharts.Options = {
    ...baseOptions,
    chart: {
      ...baseOptions.chart,
      marginLeft: GRAPH_MARGIN_LEFT,
      marginRight: GRAPH_MARGIN_RIGHT,
    },
    tooltip: {
      ...baseOptions.tooltip,
      followTouchMove: false,
    },
  };
  if (!shouldRenderRangeBand(config, showRange)) {
    const series = (options.series ?? []) as Highcharts.SeriesOptionsType[];
    return {
      ...options,
      series: series.map((series, index) =>
        index === 0
          ? withTrackSyncPointEvents({
              ...series,
              type: series.type ?? config.chartType ?? 'area',
            } as Highcharts.SeriesOptionsType)
          : series
      ),
    };
  }

  const bandColor = hexToRgba(config.seriesColor, RANGE_BAND_ALPHA);
  return {
    ...options,
    chart: {
      ...options.chart,
      type: 'line',
    },
    series: [
      withTrackSyncPointEvents({
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
      }),
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

function withTrackSyncPointEvents(series: Highcharts.SeriesOptionsType): Highcharts.SeriesOptionsType {
  const pointSeries = series as SeriesWithPointEvents;
  return {
    ...pointSeries,
    point: {
      ...pointSeries.point,
      events: {
        ...pointSeries.point?.events,
        mouseOver(this: Highcharts.Point) {
          if (!props.syncEnabled) return;
          syncPointHover(this);
        },
      },
    },
  } as Highcharts.SeriesOptionsType;
}

function toMillis(ts: ChartPoint['pointTimestamp'] | string | number): number {
  if (!ts) return 0;
  if (typeof ts === 'number') return ts;
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
  padding: 1.25rem 1rem 0.1rem;
  flex-wrap: wrap;
  gap: 4px 12px;
}

.chart-header__title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.chart {
  width: 100%;
  height: var(--track-detail-graph-height, 220px);
  min-height: var(--track-detail-graph-height, 220px);
  touch-action: pan-y;
}

.chart-empty {
  margin: 0;
  padding: 1rem;
  color: var(--text-muted);
  font-size: 0.875rem;
}
</style>
