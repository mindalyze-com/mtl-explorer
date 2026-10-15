<template>
  <article class="health-summary-card" :style="{ '--tile-accent': cardAccent }">
    <span class="health-summary-card__icon" aria-hidden="true"><i :class="icon" /></span>
    <div>
      <small>{{ series.title }}</small>
      <strong>{{ formattedValue }}</strong>
      <span>{{ latestLabel }}</span>
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { GHMetricSeries } from '../api/googleHealthApi';
import { sleepDays } from '../utils/sleepChart';

const props = defineProps<{ series: GHMetricSeries }>();

const latestPoint = computed(() => {
  if (props.series.dataType === 'sleep') {
    const day = sleepDays(props.series.points).at(-1);
    return day ? { time: day.time, value: day.total } : undefined;
  }
  return props.series.points?.at(-1);
});
const formattedValue = computed(() => {
  const value = latestPoint.value?.value;
  if (value == null) return '—';
  const decimals = props.series.decimals ?? 1;
  const formatted = new Intl.NumberFormat(undefined, {
    maximumFractionDigits: decimals,
    minimumFractionDigits: decimals,
  }).format(value);
  return `${formatted}${props.series.unit ? `\u202f${props.series.unit}` : ''}`;
});
const latestLabel = computed(() => {
  const time = latestPoint.value?.time;
  return time
    ? `${props.series.dataType === 'sleep' ? 'Latest daily total' : 'Latest'} · ${new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(time)}`
    : 'No recent value';
});
const icon = computed(() => {
  if (props.series.dataType === 'steps') return 'bi bi-person-walking';
  if (props.series.dataType === 'sleep') return 'bi bi-moon-stars';
  if (props.series.dataType?.includes('heart-rate')) return 'bi bi-heart-pulse';
  if (props.series.dataType === 'weight') return 'bi bi-speedometer2';
  if (props.series.category === 'ACTIVITY') return 'bi bi-activity';
  if (props.series.category === 'SLEEP') return 'bi bi-moon';
  if (props.series.category === 'BODY') return 'bi bi-person';
  return 'bi bi-clipboard2-pulse';
});
const cardAccent = computed(() => {
  if (props.series.dataType === 'steps') return 'var(--success)';
  if (props.series.dataType === 'sleep') return 'var(--chart-series-2)';
  if (props.series.dataType?.includes('heart-rate')) return '#e11d48';
  if (props.series.dataType === 'weight') return 'var(--chart-series-3)';
  if (props.series.category === 'ACTIVITY') return 'var(--success)';
  if (props.series.category === 'SLEEP') return 'var(--chart-series-2)';
  if (props.series.category === 'BODY') return 'var(--chart-series-3)';
  return 'var(--accent)';
});
</script>

<style scoped>
.health-summary-card {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 0.75rem;
  padding: 0.85rem 0.9rem;
  border: 1px solid var(--border-default);
  border-radius: 8px;
  background: color-mix(in srgb, var(--surface-elevated) 50%, transparent);
  transition:
    background 0.15s ease,
    border-color 0.15s ease;
}
.health-summary-card:hover {
  background: var(--surface-hover);
  border-color: var(--border-medium);
}
.health-summary-card__icon {
  display: flex;
  width: 2.25rem;
  height: 2.25rem;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: color-mix(in srgb, var(--tile-accent, var(--accent)) 12%, transparent);
  color: var(--tile-accent, var(--accent-text));
  font-size: var(--text-base-size);
}
.health-summary-card > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 0.08rem;
}
.health-summary-card small {
  overflow: hidden;
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  font-weight: 650;
  line-height: var(--text-xs-lh);
  text-overflow: ellipsis;
  white-space: nowrap;
}
.health-summary-card strong {
  color: var(--text-primary);
  font-size: var(--text-xl-size);
  font-variant-numeric: tabular-nums;
  line-height: var(--text-xl-lh);
}
.health-summary-card div > span {
  color: var(--text-faint);
  font-size: var(--text-2xs-size);
  line-height: var(--text-2xs-lh);
}
</style>
