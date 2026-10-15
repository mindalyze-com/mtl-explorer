<template>
  <div ref="card" class="cmp-chart-card">
    <div class="cmp-chart-header">
      <i class="bi" :class="icon" aria-hidden="true"></i>
      <span class="cmp-chart-title">{{ title }}</span>
      <slot name="actions" />
      <span v-if="subtitle" class="cmp-chart-subtitle">{{ subtitle }}</span>
    </div>
    <highcharts ref="highchartsEl" :options="chartOptions" class="cmp-chart"></highcharts>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import type Highcharts from 'highcharts';
import { observeElementResize } from '@/utils/observeElementResize';
import { formatDurationSmart, formatNumber } from '@/utils/Utils';
import { buildChartOptions, chartMeasurementUnit, hexToRgba, type ChartMeasurementDimension } from '@/utils/chartTheme';
import { useMeasurementSystem } from '@/composables/useMeasurementSystem';
import { useTheme } from '@/composables/useTheme';
import { TRACK_DETAIL_GRAPH_HEIGHT_DEFAULT } from '@/stores/trackDetailsPreferencesStore';
import { longDistanceDisplayValue, MEASUREMENT_DISPLAY_PROFILES } from '@/utils/units';
import { VIZ_ACCENT_COLOR } from '@/utils/visualizationColors';
import type { SegmentChartSeries } from './segmentDisplay';
import { segmentSampleAt } from './segmentDisplay';

defineOptions({ name: 'ComparisonChart' });

const props = withDefaults(
  defineProps<{
    title: string;
    subtitle?: string;
    icon?: string;
    series: SegmentChartSeries[];
    xMode?: 'distance' | 'time';
    unit?: string;
    measurementDimension?: ChartMeasurementDimension;
    decimals?: number;
    yMin?: number;
    xMax?: number;
    yZeroLine?: boolean;
    height?: number;
    activeAttempt?: string | null;
    referenceAttempt?: string | null;
    ghosts?: Array<{ seriesId: string; x: number; emphasized: boolean }>;
    linkedHover?: { x: number; axis: 'distance' | 'time' } | null;
  }>(),
  {
    subtitle: '',
    icon: 'bi-activity',
    xMode: 'distance',
    unit: '',
    measurementDimension: undefined,
    decimals: 1,
    yMin: undefined,
    xMax: undefined,
    yZeroLine: false,
    height: TRACK_DETAIL_GRAPH_HEIGHT_DEFAULT,
    activeAttempt: null,
    referenceAttempt: null,
    ghosts: () => [],
    linkedHover: null,
  }
);

const emit = defineEmits<{
  'hover-x': [hover: { x: number; axis: 'distance' | 'time' }];
  'pin-position': [hover: { x: number; axis: 'distance' | 'time' }];
  'scrub-position': [hover: { x: number; axis: 'distance' | 'time' }];
}>();

const card = ref<HTMLElement | null>(null);
let stopObservingResize: (() => void) | undefined;
const highchartsEl = ref<{ chart?: Highcharts.Chart } | null>(null);
const { measurementSystem } = useMeasurementSystem();
const { colorScheme } = useTheme();
let detachPositionListeners: (() => void) | undefined;

const NORMAL_LINE_WIDTH = 2;
const HIGHLIGHT_LINE_WIDTH = 3;
const MUTED_SERIES_OPACITY = 0.25;
// Keep plot bounds identical despite different y-axis units and label widths.
const PLOT_MARGIN_LEFT = 80;
const PLOT_MARGIN_RIGHT = 24;
const CURSOR_ID = 'segment-cursor';
const REFERENCE_MARK_HALF_SIZE_PX = 8;
const REFERENCE_MARK_STROKE_PX = 2.5;
const REFERENCE_MARK_FONT_SIZE = '9px';
// Moves the text baseline so the glyph sits centered in the diamond.
const REFERENCE_MARK_BASELINE_OFFSET_PX = 3;
const REFERENCE_MARK_Z_INDEX = 7;
const GHOST_BAND_ID = 'segment-ghost-band';
const GHOST_BAND_OPACITY = 0.14;
const GHOST_RADIUS_PX = 5;
const GHOST_STROKE_PX = 2;
const GHOST_FILL = 'rgba(255, 255, 255, 0.6)';
let cursorMarks: Highcharts.SVGElement | undefined;
const MAX_AXIS_LABEL_DECIMALS = 6;
const AXIS_STEP_TOLERANCE = 1e-6;
const TOUCH_DIRECTION_THRESHOLD_PX = 6;
const fullExtent = computed(() => {
  if (props.xMax != null) return props.xMax;
  let max = 0;
  for (const series of props.series) for (const point of series.data) max = Math.max(max, point[0]);
  return max;
});
const chartOptions = ref<Highcharts.Options>(buildOptions());
function attachHoverListeners() {
  const chart = highchartsEl.value?.chart;
  if (!chart) return;
  detachHoverListeners();
  const container = chart.container;
  const positionAt = (event: MouseEvent | Touch) => {
    const evt = chart.pointer.normalize(event as MouseEvent);
    if (!chart.isInsidePlot(evt.chartX - chart.plotLeft, evt.chartY - chart.plotTop)) return null;
    const x = (chart.xAxis[0] as Highcharts.Axis).toValue(evt.chartX);
    return { x: Math.max(0, Math.min(fullExtent.value, x)), axis: props.xMode };
  };
  const onMove = (e: MouseEvent) => {
    const position = positionAt(e);
    if (position) emit('hover-x', position);
  };
  let touchStart: { x: number; y: number; direction: 'horizontal' | 'vertical' | null } | null = null;
  const onTouchStart = (event: TouchEvent) => {
    touchStart = null;
    if (event.touches.length !== 1) return;
    const touch = event.touches[0];
    const position = positionAt(touch);
    if (!position) return;
    touchStart = { x: touch.clientX, y: touch.clientY, direction: null };
    // One finger inspects while vertical swipes remain available for scrolling.
    event.stopPropagation();
    emit('scrub-position', position);
  };
  const onTouchMove = (event: TouchEvent) => {
    if (!touchStart || event.touches.length !== 1) {
      touchStart = null;
      return;
    }
    event.stopPropagation();
    const touch = event.touches[0];
    const dx = Math.abs(touch.clientX - touchStart.x);
    const dy = Math.abs(touch.clientY - touchStart.y);
    if (!touchStart.direction && Math.max(dx, dy) >= TOUCH_DIRECTION_THRESHOLD_PX)
      touchStart.direction = dx >= dy ? 'horizontal' : 'vertical';
    // Allow the browser to scroll the workspace on a vertical swipe.
    if (touchStart.direction !== 'horizontal') return;
    event.preventDefault();
    const position = positionAt(touch);
    if (position) emit('scrub-position', position);
  };
  const onTouchEnd = (event: TouchEvent) => {
    if (touchStart && touchStart.direction !== 'vertical') {
      // Avoid the synthetic click jumping from the chart to the values on mobile.
      event.preventDefault();
      event.stopPropagation();
    }
    touchStart = null;
  };
  const onTouchCancel = () => {
    touchStart = null;
  };
  container.addEventListener('mousemove', onMove);
  const touchOptions = { capture: true, passive: false };
  container.addEventListener('touchstart', onTouchStart, touchOptions);
  container.addEventListener('touchmove', onTouchMove, touchOptions);
  container.addEventListener('touchend', onTouchEnd, touchOptions);
  container.addEventListener('touchcancel', onTouchCancel, touchOptions);
  detachPositionListeners = () => {
    container.removeEventListener('mousemove', onMove);
    container.removeEventListener('touchstart', onTouchStart, true);
    container.removeEventListener('touchmove', onTouchMove, true);
    container.removeEventListener('touchend', onTouchEnd, true);
    container.removeEventListener('touchcancel', onTouchCancel, true);
  };
}

function detachHoverListeners() {
  detachPositionListeners?.();
  detachPositionListeners = undefined;
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
    animation: false,
    responsive: false,
  });

  return {
    ...baseOptions,
    chart: {
      ...baseOptions.chart,
      marginLeft: PLOT_MARGIN_LEFT,
      marginRight: PLOT_MARGIN_RIGHT,
      zooming: { type: undefined, pinchType: undefined, singleTouch: false, mouseWheel: { enabled: false } },
      panning: { enabled: false },
      events: {
        render() {
          syncCursor(this);
        },
        click(event) {
          if (this.isInsidePlot(event.chartX - this.plotLeft, event.chartY - this.plotTop))
            emit('pin-position', { x: this.xAxis[0].toValue(event.chartX), axis: props.xMode });
        },
      },
    },
    xAxis: {
      ...(baseOptions.xAxis as Highcharts.XAxisOptions),
      title: {
        text: isDistance ? 'Distance traveled' : 'Elapsed time',
        style: { color: token('--text-muted'), fontSize: '12px' },
      },
      min: 0,
      max: fullExtent.value || undefined,
      zoomEnabled: false,
      minPadding: 0,
      maxPadding: 0,
      startOnTick: false,
      endOnTick: false,
      minRange: 0,
      // Use the exact linked position; a native crosshair snaps separately to each metric's samples.
      crosshair: false,
      labels: {
        ...(baseOptions.xAxis as Highcharts.XAxisOptions).labels,
        formatter() {
          if (!isDistance) return formatDurationSmart(Number(this.value), this.axis.max);
          const ticks = this.axis.tickPositions ?? [];
          const step =
            ticks.length > 1
              ? longDistanceDisplayValue(Math.abs(ticks[1] - ticks[0]) * 1000, measurementSystem.value)
              : 0;
          const basePrecision =
            step > 0 ? Math.min(MAX_AXIS_LABEL_DECIMALS, Math.max(0, Math.ceil(-Math.log10(step)))) : 1;
          // Preserve fractional tick steps such as 0.25, including after unit conversion.
          const precision = Math.min(
            MAX_AXIS_LABEL_DECIMALS,
            basePrecision + (Math.abs(Number(step.toFixed(basePrecision)) - step) > step * AXIS_STEP_TOLERANCE ? 1 : 0)
          );
          return (
            formatNumber(longDistanceDisplayValue(Number(this.value) * 1000, measurementSystem.value), precision) +
            '\u202f' +
            MEASUREMENT_DISPLAY_PROFILES[measurementSystem.value].longDistance
          );
        },
      },
    },
    yAxis: {
      ...(baseOptions.yAxis as Highcharts.YAxisOptions),
      zoomEnabled: false,
      ...(props.yZeroLine ? { plotLines: [{ value: 0, color: zeroLineColor, width: 1, zIndex: 3 }] } : {}),
    },
    tooltip: { enabled: false },
    plotOptions: {
      series: {
        animation: false,
        lineWidth: NORMAL_LINE_WIDTH,
        // Highcharts also requires tracking for keyboard point navigation.
        enableMouseTracking: true,
        point: {
          events: {
            mouseOver() {
              emit('hover-x', { x: this.x, axis: props.xMode });
            },
            click(event) {
              // Pointer clicks retain the exact position; keyboard activation uses the focused sample.
              const x =
                event instanceof MouseEvent && event.detail > 0
                  ? this.series.chart.xAxis[0].toValue(event.chartX)
                  : this.x;
              emit('pin-position', { x: Math.max(0, Math.min(fullExtent.value, x)), axis: props.xMode });
            },
          },
        },
        marker: {
          enabled: false,
          states: { hover: { enabled: false } },
        },
        states: { hover: { enabled: false }, inactive: { enabled: false } },
      },
    },
    series: props.series.map((s) => ({
      type: 'line',
      id: s.id,
      name: s.name,
      lineWidth: props.activeAttempt === s.id ? HIGHLIGHT_LINE_WIDTH : NORMAL_LINE_WIDTH,
      opacity: props.activeAttempt && props.activeAttempt !== s.id ? MUTED_SERIES_OPACITY : 1,
      color: s.color,
      dashStyle: s.dashStyle || 'Solid',
      data: s.data.map((point, index) => {
        const isolated = point[1] != null && s.data[index - 1]?.[1] == null && s.data[index + 1]?.[1] == null;
        // A sample surrounded by gaps has no line; render its marker instead.
        const positionLabel = s.pointLabels?.[index];
        return isolated || positionLabel
          ? {
              x: point[0],
              y: point[1],
              ...(isolated ? { marker: { enabled: true, radius: 3 } } : {}),
              ...(positionLabel ? { custom: { positionLabel } } : {}),
            }
          : point;
      }),
      connectNulls: false,
    })) as Highcharts.SeriesOptionsType[],
  };
}

onMounted(() => {
  nextTick(() => attachHoverListeners());
  if (card.value)
    stopObservingResize = observeElementResize(card.value, () => {
      const chart = highchartsEl.value?.chart;
      const width = card.value?.clientWidth;
      if (chart && width && card.value) {
        const styles = getComputedStyle(card.value);
        const padding = parseFloat(styles.paddingLeft) + parseFloat(styles.paddingRight);
        const chartWidth = Math.max(0, width - padding);
        if (chartWidth !== chart.chartWidth || props.height !== chart.chartHeight)
          chart.setSize(chartWidth, props.height, false);
      }
    });
});

onBeforeUnmount(() => {
  stopObservingResize?.();
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
watch(() => props.height, rebuild);
watch(() => props.measurementDimension, rebuild);
watch([measurementSystem, colorScheme], rebuild, { flush: 'post' });
watch(() => props.yMin, rebuild);
watch(() => props.xMax, rebuild);
watch(() => props.yZeroLine, rebuild);
watch(() => props.decimals, rebuild);
watch(() => props.activeAttempt, rebuild);
watch(
  () => [props.referenceAttempt, props.ghosts],
  () => syncCursor()
);
function syncCursor(chart = highchartsEl.value?.chart) {
  const axis = chart?.xAxis[0];
  if (!axis) return;
  axis.removePlotLine(CURSOR_ID);
  axis.removePlotBand(GHOST_BAND_ID);
  cursorMarks?.destroy();
  cursorMarks = undefined;
  const hover = props.linkedHover;
  if (hover?.axis !== props.xMode) return;
  axis.addPlotLine({
    id: CURSOR_ID,
    className: 'segment-cursor',
    value: hover.x,
    color: 'var(--text-muted)',
    width: 1,
    zIndex: 5,
  });
  cursorMarks = undefined;
  for (const ghost of props.ghosts) drawGhostMark(chart, ghost, hover.x);
  drawReferenceMark(chart, hover.x);
}
function markGroup(chart: Highcharts.Chart) {
  cursorMarks ??= chart.renderer.g('segment-cursor-marks').attr({ zIndex: REFERENCE_MARK_Z_INDEX }).add();
  return cursorMarks;
}
function plotPosition(chart: Highcharts.Chart, series: SegmentChartSeries | undefined, x: number) {
  const y = segmentSampleAt(series, x)?.value;
  if (!series || y == null) return null;
  const px = chart.xAxis[0].toPixels(x, false);
  const py = chart.yAxis[0].toPixels(y, false);
  return chart.isInsidePlot(px - chart.plotLeft, py - chart.plotTop) ? { px, py, color: series.color } : null;
}
function drawGhostMark(
  chart: Highcharts.Chart,
  ghost: { seriesId: string; x: number; emphasized: boolean },
  x: number
) {
  const series = props.series.find((candidate) => candidate.id === ghost.seriesId);
  if (series && ghost.emphasized)
    chart.xAxis[0].addPlotBand({
      id: GHOST_BAND_ID,
      from: Math.min(x, ghost.x),
      to: Math.max(x, ghost.x),
      color: hexToRgba(series.color, GHOST_BAND_OPACITY),
      zIndex: 1,
    });
  const position = plotPosition(chart, series, ghost.x);
  if (!position) return;
  chart.renderer
    .circle(position.px, position.py, GHOST_RADIUS_PX)
    .attr({ fill: GHOST_FILL, stroke: position.color, 'stroke-width': GHOST_STROKE_PX })
    .add(markGroup(chart));
}
function drawReferenceMark(chart: Highcharts.Chart, x: number) {
  const position = plotPosition(
    chart,
    props.series.find((candidate) => candidate.id === props.referenceAttempt),
    x
  );
  if (!position) return;
  const { px, py, color } = position;
  const size = REFERENCE_MARK_HALF_SIZE_PX;
  chart.renderer
    .symbol('diamond', px - size, py - size, 2 * size, 2 * size)
    .attr({ fill: '#fff', stroke: color, 'stroke-width': REFERENCE_MARK_STROKE_PX })
    .add(markGroup(chart));
  chart.renderer
    .text('R', px, py + REFERENCE_MARK_BASELINE_OFFSET_PX)
    .attr({ align: 'center' })
    .css({ color, fontSize: REFERENCE_MARK_FONT_SIZE, fontWeight: '800' })
    .add(markGroup(chart));
}
watch(
  () => props.linkedHover,
  () => syncCursor()
);
watch(
  fullExtent,
  (max) => {
    highchartsEl.value?.chart?.xAxis[0]?.setExtremes(0, max || undefined, true, false);
  },
  { flush: 'post' }
);
</script>

<style scoped>
.cmp-chart-card {
  min-width: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  padding: 0.55rem 0.55rem 0.4rem;
  background: var(--surface-glass);
  border: 1px solid var(--border-subtle);
  border-radius: 10px;
}

.cmp-chart-header {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: baseline;
  gap: 0.45rem;
  font-size: var(--text-sm-size);
  font-weight: 600;
  color: var(--text-secondary);
}

.cmp-chart-title {
  min-width: 0;
  letter-spacing: 0.01em;
}

.cmp-chart-subtitle {
  grid-column: 1 / -1;
  font-size: var(--text-xs-size);
  font-weight: 500;
  color: var(--text-muted);
}

.cmp-chart {
  min-width: 0;
  max-width: 100%;
  width: 100%;
  min-height: 0;
  touch-action: pan-y;
}
</style>
