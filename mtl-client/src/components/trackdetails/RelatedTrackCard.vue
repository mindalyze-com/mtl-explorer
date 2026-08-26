<template>
  <button
    type="button"
    class="track-card"
    :class="`track-card--${variant}`"
    :aria-label="`Open track ${track.name || `#${track.id}`}`"
    :data-track-id="track.id"
    @click="emit('navigate', track.id)"
  >
    <TrackShapePreview :track-id="track.id!" :width="56" :height="40" class="track-card__shape" />
    <div class="track-dot"></div>
    <div class="track-card-body">
      <div class="track-name">
        <span v-if="variant === 'segment' && track.sourceSegmentIndex" class="seg-badge">
          Seg {{ track.sourceSegmentIndex }}
        </span>
        {{ track.name }}
      </div>
      <div v-if="track.startDate" class="track-date">{{ formatDateShort(track.startDate) }}</div>
      <div v-if="track.description" class="track-desc">{{ track.description }}</div>
    </div>
  </button>
</template>

<script setup lang="ts">
import type { RelatedTrackInfo } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import TrackShapePreview from '@/components/ui/TrackShapePreview.vue';
import { formatDateShort } from '@/utils/Utils';

defineProps<{
  track: RelatedTrackInfo;
  variant: 'previous' | 'next' | 'duplicate' | 'segment';
}>();

const emit = defineEmits<{
  navigate: [trackId: number | null | undefined];
}>();
</script>

<style scoped>
.track-card {
  width: 100%;
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 0.625rem;
  padding: 0.5rem 0.625rem;
  border: 1px solid transparent;
  border-radius: 8px;
  color: inherit;
  background: transparent;
  font: inherit;
  text-align: left;
  appearance: none;
  cursor: pointer;
  transition:
    background 0.15s ease,
    border-color 0.15s ease,
    transform 0.12s ease;
}

.track-card:hover {
  border-color: var(--border-default);
  background: var(--surface-hover);
  transform: translateX(2px);
}

.track-card:active {
  transform: translateX(2px) scale(0.995);
}

.track-card__shape {
  flex-shrink: 0;
  border-radius: 6px;
  opacity: 0.7;
  transition: opacity 0.15s ease;
}

.track-card:hover .track-card__shape {
  opacity: 1;
}

.track-dot {
  z-index: 1;
  flex-shrink: 0;
  width: 0.5rem;
  height: 0.5rem;
  margin-top: 0.4rem;
  margin-left: -1.3125rem;
  border: 2px solid var(--accent-text);
  border-radius: 50%;
  background: var(--surface-glass-heavy);
  transition:
    transform 0.15s ease,
    box-shadow 0.15s ease;
}

.track-card--duplicate .track-dot {
  border-color: var(--text-muted);
}

.track-card--segment .track-dot {
  border-color: var(--accent-text-light);
}

.track-card:hover .track-dot {
  box-shadow: 0 0 0 3px var(--accent-bg);
  transform: scale(1.25);
}

.track-card-body {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.track-name {
  max-width: 100%;
  overflow: hidden;
  color: var(--text-primary);
  font-size: var(--text-sm-size);
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color 0.15s ease;
}

.track-card--duplicate .track-name {
  color: var(--text-muted);
}

.track-card:hover .track-name {
  color: var(--accent-text);
}

.track-date {
  margin-top: 0.125rem;
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  font-variant-numeric: tabular-nums;
}

.track-desc {
  display: -webkit-box;
  margin-top: 0.1875rem;
  overflow: hidden;
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.seg-badge {
  display: inline-block;
  margin-right: 0.3125rem;
  padding: 0.0625rem 0.3125rem;
  border-radius: 4px;
  background: var(--accent-text-light);
  color: var(--text-inverse);
  font-size: var(--text-2xs-size);
  font-weight: 700;
  vertical-align: middle;
}
</style>
