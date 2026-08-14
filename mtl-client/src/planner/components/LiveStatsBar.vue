<template>
  <div class="live-stats-bar">
    <div class="live-stats-bar__grid">
      <article class="stat-card stat-card--distance">
        <span class="stat-card__icon"><i class="bi bi-bezier2" /></span>
        <span class="stat-card__label">Distance</span>
        <strong class="stat-card__value">{{ formatDistanceSmart(stats.distanceM) }}</strong>
      </article>

      <article class="stat-card stat-card--ascent">
        <span class="stat-card__icon"><i class="bi bi-arrow-up-right" /></span>
        <span class="stat-card__label">Ascent</span>
        <strong class="stat-card__value">{{ formatElevation(stats.ascentM) }}</strong>
      </article>

      <article class="stat-card stat-card--descent">
        <span class="stat-card__icon"><i class="bi bi-arrow-down-right" /></span>
        <span class="stat-card__label">Descent</span>
        <strong class="stat-card__value">{{ formatElevation(stats.descentM) }}</strong>
      </article>

      <article class="stat-card stat-card--duration">
        <span class="stat-card__icon"><i class="bi bi-clock" /></span>
        <span class="stat-card__label">Duration</span>
        <strong class="stat-card__value">{{ formatDuration(stats.durationSec) }}</strong>
      </article>

      <article class="stat-card stat-card--legs">
        <span class="stat-card__icon"><i class="bi bi-signpost-2" /></span>
        <span class="stat-card__label">Legs</span>
        <strong class="stat-card__value">{{ stats.legCount }}</strong>
      </article>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { LiveStats } from '@/planner/types';
import { formatDistanceSmart, formatElevation } from '@/utils/Utils';

defineProps<{ stats: LiveStats }>();

const formatDuration = (s: number) => {
  if (!s || s <= 0) return '–';
  const h = Math.floor(s / 3600);
  const mm = Math.floor((s % 3600) / 60);
  return h > 0 ? `${h}h ${mm}m` : `${mm}m`;
};
</script>

<style scoped>
.live-stats-bar {
  display: flex;
  flex-direction: column;
}
.live-stats-bar__grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 0.5rem;
  width: 100%;
}
.stat-card {
  display: grid;
  grid-template-columns: auto 1fr;
  grid-template-rows: auto auto;
  column-gap: 0.5rem;
  row-gap: 0.1rem;
  padding: 0.6rem 0.65rem;
  border-radius: 12px;
  border: 1px solid var(--border-default);
  background: var(--surface-glass-subtle);
  align-items: center;
}
.stat-card__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.6rem;
  height: 1.6rem;
  border-radius: 999px;
  background: var(--surface-glass-heavy);
  border: 1px solid currentColor;
  grid-row: span 2;
}
.stat-card__icon i {
  font-size: var(--text-sm-size);
}
.stat-card__label {
  font-size: var(--text-2xs-size, 0.65rem);
  line-height: 1.2;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  font-weight: 700;
}
.stat-card__value {
  font-weight: 700;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
  font-size: var(--text-base-size);
  line-height: var(--text-base-lh);
}
.stat-card__hint {
  grid-column: 1 / -1;
  color: var(--text-muted);
  font-size: var(--text-2xs-size);
  line-height: var(--text-2xs-lh);
}
.stat-card--distance {
  color: var(--viz-indigo);
}
.stat-card--ascent {
  color: var(--viz-green);
}
.stat-card--descent {
  color: var(--viz-blue);
}
.stat-card--duration {
  color: var(--viz-orange-strong);
}
.stat-card--legs {
  color: var(--viz-magenta);
}
@media (max-width: 640px) {
  .live-stats-bar__grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 380px) {
  .live-stats-bar {
    padding: 0.6rem;
  }
  .live-stats-bar__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
