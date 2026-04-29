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

<script lang="ts">
import { defineComponent, type PropType } from 'vue';
import type Highcharts from 'highcharts';
import { formatDurationSmart } from '@/utils/Utils';

/**
 * Per-series data entry for ComparisonChart.
 *
 * Each x/y point carries an optional third tuple element (timestamp in ms)
 * used solely for the tooltip to show the wall-clock time of each sample.
 */
export interface ComparisonSeries {
  name: string;
  color: string;
  dashStyle?: 'Solid' | 'Dash' | 'ShortDash';
  data: Array<[number, number] | [number, number, number]>;
}

function hexToRgba(hex: string, alpha: number): string {
  if (!hex || !hex.startsWith('#') || hex.length < 7) return hex;
  const r = parseInt(hex.slice(1, 3), 16);
  const g = parseInt(hex.slice(3, 5), 16);
  const b = parseInt(hex.slice(5, 7), 16);
  return `rgba(${r},${g},${b},${alpha})`;
}

function compactNum(v: number): string {
  if (v === 0) return '0';
  if (Math.abs(v) >= 1000) return (v / 1000).toFixed(v % 1000 === 0 ? 0 : 1) + 'k';
  if (Math.abs(v) >= 10) return Math.round(v).toString();
  return parseFloat(v.toFixed(1)).toString();
}

export default defineComponent({
  name: 'ComparisonChart',
  props: {
    title: { type: String, required: true },
    subtitle: { type: String, default: '' },
    icon: { type: String, default: 'bi-activity' },
    series: { type: Array as PropType<ComparisonSeries[]>, required: true },
    xMode: { type: String as PropType<'distance' | 'time'>, default: 'distance' },
    unit: { type: String, default: '' },
    decimals: { type: Number, default: 1 },
    yMin: { type: Number as PropType<number | undefined>, default: undefined },
    yZeroLine: { type: Boolean, default: false },
    height: { type: Number, default: 240 },
  },
  emits: ['hover-x', 'hover-leave'],
  data(): { chartOptions: Highcharts.Options } {
    return { chartOptions: this.buildOptions() };
  },
  mounted() {
    this.$nextTick(() => this.attachHoverListeners());
  },
  beforeUnmount() {
    this.detachHoverListeners();
  },
  watch: {
    series: { deep: true, handler() { this.rebuild(); this.$nextTick(() => this.attachHoverListeners()); } },
    xMode() { this.rebuild(); this.$nextTick(() => this.attachHoverListeners()); },
    unit() { this.rebuild(); },
    yMin() { this.rebuild(); },
    yZeroLine() { this.rebuild(); },
    decimals() { this.rebuild(); },
  },
  methods: {
    attachHoverListeners() {
      const el = this.$refs.highchartsEl as { chart?: Highcharts.Chart } | undefined;
      const chart = el?.chart;
      if (!chart) return;
      this.detachHoverListeners();
      const container = chart.container;
      const onMove = (e: MouseEvent) => {
        const evt = chart.pointer.normalize(e);
        const x = (chart.xAxis[0] as Highcharts.Axis).toValue(evt.chartX);
        this.$emit('hover-x', x);
      };
      const onLeave = () => this.$emit('hover-leave');
      container.addEventListener('mousemove', onMove);
      container.addEventListener('mouseleave', onLeave);
      (this as unknown as { _hoverListeners: { container: HTMLElement; onMove: (e: MouseEvent) => void; onLeave: () => void } })._hoverListeners = { container, onMove, onLeave };
    },
    detachHoverListeners() {
      const h = (this as unknown as { _hoverListeners?: { container: HTMLElement; onMove: (e: MouseEvent) => void; onLeave: () => void } })._hoverListeners;
      if (h) {
        h.container.removeEventListener('mousemove', h.onMove);
        h.container.removeEventListener('mouseleave', h.onLeave);
        (this as unknown as { _hoverListeners?: unknown })._hoverListeners = undefined;
      }
    },
    rebuild() {
      this.chartOptions = this.buildOptions();
    },
    buildOptions(): Highcharts.Options {
      const styles = getComputedStyle(document.documentElement);
      const token = (name: string) => styles.getPropertyValue(name).trim();
      const textColor = token('--text-muted');
      const gridColor = token('--chart-grid');
      const zeroLineColor = token('--border-hover');
      const tooltipBg = token('--chart-tooltip-bg');
      const tooltipText = token('--chart-tooltip-text');
      const borderColor = token('--border-default');
      const isDistance = this.xMode === 'distance';
      const unit = this.unit;
      const decimals = this.decimals;

      return {
        chart: {
          type: 'line',
          height: this.height,
          backgroundColor: 'transparent',
          spacing: [6, 4, 10, 4],
          style: {
            fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif",
          },
        },
        title: { text: undefined },
        credits: { enabled: false },
        legend: { enabled: false },
        xAxis: {
          type: 'linear',
          crosshair: {
            width: 1,
            color: borderColor,
            dashStyle: 'Dash',
          },
          labels: {
            style: { color: textColor, fontSize: '12px' },
            formatter(this: Highcharts.AxisLabelsFormatterContextObject) {
              if (isDistance) {
                return parseFloat((this.value as number).toFixed(1)) + '\u202fkm';
              }
              return formatDurationSmart(this.value as number, (this.axis as Highcharts.Axis).max as number);
            },
          },
          lineColor: gridColor,
          tickColor: 'transparent',
          title: { text: undefined },
        },
        yAxis: {
          gridLineColor: gridColor,
          title: { text: undefined },
          labels: {
            style: { color: textColor, fontSize: '12px' },
            formatter(this: Highcharts.AxisLabelsFormatterContextObject) {
              const n = compactNum(this.value as number);
              return (this.isLast && unit) ? n + '\u202f' + unit : n;
            },
          },
          ...(this.yMin !== undefined ? { min: this.yMin } : {}),
          ...(this.yZeroLine ? { plotLines: [{ value: 0, color: zeroLineColor, width: 1, zIndex: 3 }] } : {}),
        },
        tooltip: {
          shared: true,
          backgroundColor: tooltipBg,
          borderColor,
          borderRadius: 8,
          borderWidth: 1,
          shadow: false,
          style: { color: tooltipText, fontSize: '12px' },
          useHTML: true,
          formatter(this: any) {
            const header = isDistance
              ? ((this.x as number).toFixed(2) + '\u202fkm')
              : formatDurationSmart(this.x as number, this.points?.[0]?.series?.xAxis?.max as number);
            const lines: string[] = [];
            lines.push('<span style="font-size:10px">' + header + '</span>');
            const points = (this.points || []) as Array<{ y: number | null; series: { name: string; color: string } }>;
            for (const p of points) {
              if (p.y == null) continue;
              const val = (p.y as number).toFixed(decimals);
              const unitStr = unit ? '\u202f' + unit : '';
              lines.push(
                '<span style="color:' + p.series.color + '">\u25CF</span> '
                + p.series.name + ': <b>' + val + unitStr + '</b>'
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
        series: this.series.map(s => ({
          type: 'line',
          name: s.name,
          color: s.color,
          dashStyle: s.dashStyle || 'Solid',
          data: s.data,
          // Subtle glow on hover for better track discrimination in overlays.
          states: { hover: { halo: { size: 6, attributes: { fill: hexToRgba(s.color, 0.25) } } } },
        })) as Highcharts.SeriesOptionsType[],
      };
    },
  },
});
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
