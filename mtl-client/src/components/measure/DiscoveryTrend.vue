<template>
  <section class="discovery-trend" :aria-label="title">
    <h3>{{ title }}</h3>
    <p class="trend-description">{{ description }}</p>
    <highcharts :options="options" />
    <p class="trend-readout" aria-live="polite">{{ readout || 'Select a point or result to inspect its visits.' }}</p>
  </section>
</template>
<script setup lang="ts">
import { computed } from 'vue';
import type Highcharts from 'highcharts';
import { formatDateCompact, formatNumber, generateColors } from '@/utils/Utils';
import { getFormatLocale } from '@/composables/useLocale';
const MAX_AXIS_LABEL_DECIMALS = 6;
const DEFAULT_AXIS_LABEL_DECIMALS = 2;
export type TrendPoint = {
  key: string;
  segment: string;
  date: number;
  value: number | null;
  label: string;
  formatted: string;
};
const props = defineProps<{
  title: string;
  description: string;
  series: Array<{ key: string; label: string; points: TrendPoint[] }>;
  focused: string;
  axisLabel: string;
  displayValue: (value: number) => number;
}>();
const emit = defineEmits<{ focus: [point: TrendPoint] }>();
const readout = computed(() => {
  const point = props.series.flatMap((s) => s.points).find((p) => p.key === props.focused);
  return point ? `${formatDateCompact(point.date)} · ${point.label} · ${point.formatted}` : '';
});
const options = computed<Highcharts.Options>(() => ({
  time: { timezone: Intl.DateTimeFormat().resolvedOptions().timeZone },
  chart: { type: 'line', height: 260, backgroundColor: 'transparent', spacing: [12, 8, 12, 8] },
  title: { text: '' },
  credits: { enabled: false },
  accessibility: { enabled: false },
  xAxis: {
    type: 'datetime',
    labels: {
      style: { color: 'var(--text-secondary)' },
      formatter() {
        const value = Number(this.value);
        // Highcharts supplies unitName here, but its public typings omit it.
        const unit = (
          this.axis.tickPositions?.info as (Highcharts.TimeTicksInfoObject & { unitName?: string }) | undefined
        )?.unitName;
        if (!unit || ['day', 'week', 'month', 'year'].includes(unit)) return formatDateCompact(value);
        return new Intl.DateTimeFormat(getFormatLocale(), {
          ...(unit === 'hour' ? { month: 'short' as const, day: 'numeric' as const } : {}),
          hour: '2-digit',
          minute: '2-digit',
          ...(unit === 'second' || unit === 'millisecond' ? { second: '2-digit' as const } : {}),
        }).format(new Date(value));
      },
    },
  },
  yAxis: {
    title: { text: props.axisLabel, style: { color: 'var(--text-secondary)' } },
    labels: {
      style: { color: 'var(--text-secondary)' },
      formatter() {
        const ticks = this.axis.tickPositions ?? [];
        const step = ticks.length > 1 ? Math.abs(props.displayValue(ticks[1]) - props.displayValue(ticks[0])) : 0;
        const decimals =
          step > 0
            ? Math.min(MAX_AXIS_LABEL_DECIMALS, Math.max(0, Math.ceil(-Math.log10(step))))
            : DEFAULT_AXIS_LABEL_DECIMALS;
        return formatNumber(props.displayValue(Number(this.value)), decimals);
      },
    },
    gridLineColor: 'var(--border-subtle)',
  },
  legend: { enabled: props.series.length > 1, itemStyle: { color: 'var(--text-secondary)' } },
  tooltip: {
    formatter() {
      return this.options.custom?.formatted ?? '';
    },
  },
  plotOptions: {
    series: {
      animation: false,
      cursor: 'pointer',
      point: {
        events: {
          click() {
            const point = this.options.custom?.point as TrendPoint;
            if (point) emit('focus', point);
          },
        },
      },
    },
  },
  series: props.series.map((s, index) => ({
    type: 'line',
    name: s.label,
    color: generateColors(props.series.length)[index],
    turboThreshold: 0,
    data: [...s.points]
      .filter((p) => Number.isFinite(p.date))
      .sort((a, b) => a.date - b.date || a.key.localeCompare(b.key))
      .map((p) => ({
        x: p.date,
        y: p.value,
        custom: { point: p, formatted: p.formatted },
        marker: { enabled: true, radius: p.key === props.focused ? 7 : 3, lineWidth: p.key === props.focused ? 2 : 0 },
      })),
  })),
}));
</script>
<style scoped>
.discovery-trend {
  min-width: 0;
  padding: 0.5rem;
  border: 1px solid var(--border-subtle);
  border-radius: 0.7rem;
}
h3 {
  margin: 0.3rem 0.5rem;
  font-size: var(--text-base-size);
}
.trend-description {
  margin: 0.3rem 0.5rem 0.5rem;
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}
.trend-readout {
  min-height: 1.4em;
  margin: 0.25rem 0.5rem;
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
  overflow-wrap: anywhere;
}
</style>
