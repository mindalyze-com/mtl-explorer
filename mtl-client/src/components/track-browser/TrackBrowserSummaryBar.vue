<template>
  <div class="track-browser-summary" :class="{ 'track-browser-summary--compact': compact }">
    <template v-if="compact">
      <div class="track-browser-summary__compact-row">
        <strong>{{ summary.count }} tracks</strong>
        <span v-tooltip.top="{ value: formatDistanceTooltip(summary.totalDistanceMeters || 0), showDelay: 400 }">{{ formatDistanceSmart(summary.totalDistanceMeters || 0) }}</span>
        <span v-tooltip.top="{ value: formatDurationTooltip(summary.totalDurationMillis || 0), showDelay: 400 }">{{ formatDurationSmart(summary.totalDurationMillis || 0) }}</span>
      </div>
    </template>
    <template v-else>
      <div class="track-browser-summary__intro">
        <div class="track-browser-summary__eyebrow">Tracks</div>
        <div class="track-browser-summary__title">{{ summary.count }} tracks in view</div>
        <div class="track-browser-summary__subtitle">
          {{ summary.dateRangeLabel }}
        </div>
      </div>

      <div class="track-browser-summary__stats">
        <div class="track-browser-summary__card">
          <span class="track-browser-summary__label">Total distance</span>
          <strong v-tooltip.top="{ value: formatDistanceTooltip(summary.totalDistanceMeters || 0), showDelay: 400 }">{{ formatDistanceSmart(summary.totalDistanceMeters || 0) }}</strong>
        </div>
        <div class="track-browser-summary__card">
          <span class="track-browser-summary__label">Total duration</span>
          <strong v-tooltip.top="{ value: formatDurationTooltip(summary.totalDurationMillis || 0), showDelay: 400 }">{{ formatDurationSmart(summary.totalDurationMillis || 0) }}</strong>
        </div>
        <div class="track-browser-summary__card track-browser-summary__card--wide">
          <span class="track-browser-summary__label">Newest track</span>
          <strong>{{ summary.newestTrackLabel }}</strong>
          <span class="track-browser-summary__muted">{{ summary.newestTrackDateLabel }}</span>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { formatDistance, formatDuration, formatDistanceSmart, formatDurationSmart, formatDistanceTooltip, formatDurationTooltip } from '@/utils/Utils';
import type { TrackBrowserSummary } from './trackBrowser.types';

defineProps<{
  summary: TrackBrowserSummary;
  compact?: boolean;
}>();
</script>

<style scoped>
.track-browser-summary {
  display: grid;
  grid-template-columns: minmax(16rem, 1.1fr) minmax(0, 2fr);
  gap: 1rem;
  padding: var(--dlg-padding);
  background: linear-gradient(135deg, var(--accent-bg), transparent);
  border-bottom: 1px solid var(--border-default);
}

.track-browser-summary__intro {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}

.track-browser-summary__eyebrow {
  font-size: 0.75rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--accent);
  font-weight: 700;
}

.track-browser-summary__title {
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--text-primary);
}

.track-browser-summary__subtitle {
  color: var(--text-secondary);
  font-size: 0.92rem;
}

.track-browser-summary__stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.75rem;
}

.track-browser-summary__card {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  padding: 0.85rem var(--dlg-padding);
  border-radius: 0.85rem;
  background: var(--surface-elevated);
  border: 1px solid var(--border-default);
}

.track-browser-summary__card--wide {
  min-width: 0;
}

.track-browser-summary__label {
  font-size: 0.75rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--text-muted);
}

.track-browser-summary__muted {
  color: var(--text-muted);
  font-size: 0.82rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 900px) {
  .track-browser-summary {
    grid-template-columns: 1fr;
  }

  .track-browser-summary__stats {
    grid-template-columns: 1fr;
  }
}

.track-browser-summary--compact {
  display: flex;
  padding: 0.6rem var(--dlg-padding);
}

.track-browser-summary__compact-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
  font-size: 0.88rem;
  color: var(--text-secondary);
}

.track-browser-summary__compact-row strong {
  color: var(--text-primary);
}
</style>