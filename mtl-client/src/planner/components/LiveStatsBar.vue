<template>
  <div class="live-stats-bar">
    <div class="stat">
      <span class="label">Distance</span>
      <span class="value">{{ formatKm(stats.distanceM) }}</span>
    </div>
    <div class="stat">
      <span class="label">Ascent</span>
      <span class="value">{{ formatM(stats.ascentM) }}</span>
    </div>
    <div class="stat">
      <span class="label">Descent</span>
      <span class="value">{{ formatM(stats.descentM) }}</span>
    </div>
    <div class="stat">
      <span class="label">Duration</span>
      <span class="value">{{ formatDuration(stats.durationSec) }}</span>
    </div>
    <div class="stat subtle">
      <span class="label">Legs</span>
      <span class="value">{{ stats.legCount }}<span v-if="stats.anyLegCached" class="cached-dot" title="Some legs served from cache">·</span></span>
    </div>
    <div v-if="computing" class="computing">computing…</div>
  </div>
</template>

<script setup lang="ts">
import type { LiveStats } from '@/planner/types';

defineProps<{ stats: LiveStats; computing: boolean }>();

const formatKm = (m: number) => (m / 1000).toFixed(2) + ' km';
const formatM = (m: number) => Math.round(m) + ' m';
const formatDuration = (s: number) => {
  if (!s || s <= 0) return '–';
  const h = Math.floor(s / 3600);
  const mm = Math.floor((s % 3600) / 60);
  return h > 0 ? `${h}h ${mm}m` : `${mm}m`;
};
</script>

<style scoped>
.live-stats-bar {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(104px, 1fr));
  gap: 0.65rem;
  align-items: stretch;
  padding: 0.75rem;
  background: linear-gradient(180deg, var(--surface-glass-heavy), var(--surface-glass));
  backdrop-filter: var(--blur-standard);
  -webkit-backdrop-filter: var(--blur-standard);
  color: var(--text-primary);
  border-radius: 16px;
  border: 1px solid var(--border-medium);
  box-shadow: var(--shadow-sm);
  font-size: 0.9rem;
}
.stat {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 0.2rem;
  line-height: 1.1;
  padding: 0.7rem 0.8rem;
  border-radius: 12px;
  background: color-mix(in srgb, var(--surface-glass-light) 80%, transparent);
  border: 1px solid var(--border-default);
}
.stat .label {
  font-size: 0.68rem;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.08em;
}
.stat .value {
  font-weight: 700;
  color: var(--text-primary);
  font-size: 1rem;
}
.stat.subtle {
  background: color-mix(in srgb, var(--accent-bg) 55%, var(--surface-glass-light));
}
.cached-dot { color: var(--success); margin-left: 2px; }
.computing {
  grid-column: 1 / -1;
  justify-self: end;
  font-size: 0.8rem;
  color: var(--text-muted);
  font-style: italic;
  padding: 0 0.25rem;
}

@media (max-width: 640px) {
  .live-stats-bar {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
