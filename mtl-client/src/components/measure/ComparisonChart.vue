<template>
  <div class="cmp-chart-card">
    <div class="cmp-chart-header">
      <i class="bi" :class="icon"></i>
      <span class="cmp-chart-title">{{ title }}</span>
      <span v-if="subtitle" class="cmp-chart-subtitle">{{ subtitle }}</span>
    </div>
    <highcharts ref="highchartsEl" :options="chartOptions" class="cmp-chart"></highcharts>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import type Highcharts from 'highcharts';
import { formatDurationSmart } from '@/utils/Utils';
import {
  buildChartOptions,
  chartMeasurementDisplayValue,
  chartMeasurementUnit,
  hexToRgba,
  type ChartMeasurementDimension,
} from '@/utils/chartTheme';
import { useMeasurementSystem } from '@/composables/useMeasurementSystem';
import { longDistanceDisplayValue, MEASUREMENT_DISPLAY_PROFILES } from '@/utils/units';
import { VIZ_ACCENT_COLOR } from '@/utils/visualizationColors';

defineOptions({ name: 'ComparisonChart' });

/**
 * Per-series data entry for ComparisonChart.
 *
 * Each x/y point carries an optional third tuple element (timestamp in ms)
 * used solely for the tooltip to show the wall-clock time of each sample.
 */
interface ComparisonSeries {
  name: string;
  color: string;
  dashStyle?: 'Solid' | 'Dash' | 'ShortDash';
  data: Array<[number, number | null] | [number, number | null, number]>;
}

type ComparisonTooltipPoint = Highcharts.Point & {
  series: Highcharts.Series & { xAxis?: { max?: number } };
};

const props = withDefaults(
  defineProps<{
    title: string;
    subtitle?: string;
    icon?: string;
    series: ComparisonSeries[];
    xMode?: 'distance' | 'time';
    unit?: string;
    measurementDimension?: ChartMeasurementDimension;
    decimals?: number;
    yMin?: number;
    yZeroLine?: boolean;
    height?: number;
  }>(),
  {
    subtitle: '',
    icon: 'bi-activity',
    xMode: 'distance',
    unit: '',
    measurementDimension: undefined,
    decimals: 1,
    yMin: undefined,
    yZeroLine: false,
    height: 240,
  }
);

const emit = defineEmits<{
  'hover-x': [x: number];
  'hover-leave': [];
}>();

const highchartsEl = ref<{ chart?: Highcharts.Chart } | null>(null);
const { measurementSystem } = useMeasurementSystem();
const chartOptions = ref<Highcharts.Options>(buildOptions());
let hoverListeners: { container: HTMLElement; onMove: (e: MouseEvent) => void; onLeave: () => void } | null = null;

function attachHoverListeners() {
  const chart = highchartsEl.value?.chart;
  if (!chart) return;
  detachHoverListeners();
  const container = chart.container;
  const onMove = (e: MouseEvent) => {
    const evt = chart.pointer.normalize(e);
    const x = (chart.xAxis[0] as Highcharts.Axis).toValue(evt.chartX);
    emit('hover-x', x);
  };
  const onLeave = () => emit('hover-leave');
  container.addEventListener('mousemove', onMove);
  container.addEventListener('mouseleave', onLeave);
  hoverListeners = { container, onMove, onLeave };
}

function detachHoverListeners() {
  if (hoverListeners) {
    hoverListeners.container.removeEventListener('mousemove', hoverListeners.onMove);
    hoverListeners.container.removeEventListener('mouseleave', hoverListeners.onLeave);
    hoverListeners = null;
  }
}

function rebuild() {
  chartOptions.value = buildOptions();
}

function buildOptions(): Highcharts.Options {
  const styles = getComputedStyle(document.documentElement);
  const token = (name: string) => styles.getPropertyValue(name).trim();
  const zeroLineColor = token('--border-hover');
  const isDistance = props.xMode === 'distance';
  const unit = props.measurementDimension
    ? chartMeasurementUnit(props.measurementDimension, measurementSystem.value)
    : props.unit;
  const decimals = props.decimals;
  const baseOptions = buildChartOptions({
    seriesName: props.series[0]?.name ?? '',
    seriesColor: props.series[0]?.color ?? VIZ_ACCENT_COLOR,
    unit,
    measurementDimension: props.measurementDimension,
    decimals,
    yMin: props.yMin,
    xMode: props.xMode,
    textColorToken: '--text-muted',
    chartType: 'line',
    height: props.height,
    spacing: [6, 4, 10, 4],
    responsive: false,
  });

  return {
    ...baseOptions,
    yAxis: {
      ...(baseOptions.yAxis as Highcharts.YAxisOptions),
      ...(props.yZeroLine ? { plotLines: [{ value: 0, color: zeroLineColor, width: 1, zIndex: 3 }] } : {}),
    },
    tooltip: {
      ...(baseOptions.tooltip as Highcharts.TooltipOptions),
      shared: true,
      formatter(this: Highcharts.Point) {
        const points = (this.points ?? []) as ComparisonTooltipPoint[];
        const header = isDistance
          ? longDistanceDisplayValue((this.x as number) * 1000, measurementSystem.value).toFixed(2) +
            '\u202f' +
            MEASUREMENT_DISPLAY_PROFILES[measurementSystem.value].longDistance
          : formatDurationSmart(this.x as number, points[0]?.series?.xAxis?.max as number);
        const lines: string[] = [];
        lines.push('<span style="font-size:10px">' + header + '</span>');
        for (const p of points) {
          if (p.y == null) continue;
          const val = chartMeasurementDisplayValue(
            p.y as number,
            props.measurementDimension,
            measurementSystem.value
          ).toFixed(decimals);
          const unitStr = unit ? '\u202f' + unit : '';
          lines.push(
            '<span style="color:' +
              String(p.series.color ?? '#999') +
              '">\u25CF</span> ' +
              p.series.name +
              ': <b>' +
              val +
              unitStr +
              '</b>'
          );
        }
        return lines.join('<br/>');
      },
    },
    plotOptions: {
      series: {
        animation: false,
        lineWidth: 2,
        marker: {
          enabled: false,
          states: { hover: { enabled: true, radius: 3, lineWidth: 0 } },
        },
        states: { hover: { lineWidthPlus: 0 } },
      },
    },
    series: props.series.map((s) => ({
      type: 'line',
      name: s.name,
      color: s.color,
      dashStyle: s.dashStyle || 'Solid',
      data: s.data,
      // Subtle glow on hover for better track discrimination in overlays.
      states: { hover: { halo: { size: 6, attributes: { fill: hexToRgba(s.color, 0.25) } } } },
    })) as Highcharts.SeriesOptionsType[],
  };
}

onMounted(() => {
  nextTick(() => attachHoverListeners());
});

onBeforeUnmount(() => {
  detachHoverListeners();
});

watch(
  () => props.series,
  () => {
    rebuild();
    nextTick(() => attachHoverListeners());
  },
  { deep: true }
);

watch(
  () => props.xMode,
  () => {
    rebuild();
    nextTick(() => attachHoverListeners());
  }
);

watch(() => props.unit, rebuild);
watch(() => props.measurementDimension, rebuild);
watch(measurementSystem, rebuild);
watch(() => props.yMin, rebuild);
watch(() => props.yZeroLine, rebuild);
watch(() => props.decimals, rebuild);
</script>

<style scoped>
.cmp-chart-card {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  padding: 0.55rem 0.55rem 0.4rem;
  background: var(--surface-glass);
  border: 1px solid var(--border-subtle);
  border-radius: 10px;
}

.cmp-chart-header {
  display: flex;
  align-items: baseline;
  gap: 0.45rem;
  font-size: var(--text-sm-size);
  font-weight: 600;
  color: var(--text-secondary);
}

.cmp-chart-title {
  letter-spacing: 0.01em;
}

.cmp-chart-subtitle {
  font-size: var(--text-xs-size);
  font-weight: 500;
  color: var(--text-muted);
}

.cmp-chart {
  width: 100%;
  min-height: 200px;
}
</style>
