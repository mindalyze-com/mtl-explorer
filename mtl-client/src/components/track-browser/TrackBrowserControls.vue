<template>
  <div class="track-browser-controls">
    <span class="track-browser-controls__search p-input-icon-left">
      <i class="pi pi-search track-browser-controls__search-icon" />
      <InputText
        :model-value="query"
        placeholder="Search name, activity, date, file, creator…"
        class="track-browser-controls__input"
        @update:model-value="(value) => emit('update:query', String(value || ''))"
      />
    </span>
    <div class="track-browser-controls__summary">
      <template v-if="query.trim()">
        <strong>{{ summary.count }}</strong> of {{ totalCount }} tracks
      </template>
      <template v-else>
        <strong>{{ totalCount }}</strong> tracks
      </template>
      <span class="track-browser-controls__summary-sep" aria-hidden="true">·</span>
      <span v-tooltip.top="{ value: formatDistanceTooltip(summary.totalDistanceMeters), showDelay: 400 }">{{ formatDistanceSmart(summary.totalDistanceMeters) }}</span>
      <span class="track-browser-controls__summary-sep" aria-hidden="true">·</span>
      <span v-tooltip.top="{ value: formatDurationTooltip(summary.totalDurationMillis), showDelay: 400 }">{{ formatDurationSmart(summary.totalDurationMillis) }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { formatDistance, formatDuration, formatDistanceSmart, formatDurationSmart, formatDistanceTooltip, formatDurationTooltip } from '@/utils/Utils';
import type { TrackBrowserSummary } from './trackBrowser.types';

defineProps<{
  query: string;
  summary: TrackBrowserSummary;
  totalCount: number;
}>();

const emit = defineEmits<{
  (event: 'update:query', value: string): void;
}>();
</script>

<style scoped>
.track-browser-controls {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
  padding: 0.75rem var(--dlg-padding) 0.6rem;
  border-bottom: 1px solid var(--border-default);
}

.track-browser-controls__search {
  display: flex;
  align-items: center;
  position: relative;
  width: 100%;
  max-width: 28rem;
}

.track-browser-controls__search-icon {
  position: absolute;
  left: 0.75rem;
  color: var(--text-muted);
  pointer-events: none;
  z-index: 1;
}

.track-browser-controls__input {
  width: 100%;
  padding-left: 2.25rem !important;
}

.track-browser-controls__summary {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.82rem;
  color: var(--text-muted);
  padding-left: 0.15rem;
}

.track-browser-controls__summary strong {
  color: var(--text-primary);
  font-weight: 600;
}

.track-browser-controls__summary-sep {
  color: var(--border-default);
  font-size: 0.7rem;
}
</style>
