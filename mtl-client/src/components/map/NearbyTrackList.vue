<template>
  <ul class="track-selection-list">
    <li v-for="track in tracks" :key="track.id">
      <button type="button" class="mtl-track-pick" @click="emit('select', track.id)">
        <TrackShapePreview :track-id="track.id" :width="48" :height="32" :padding="3" class="mtl-track-pick__shape" />
        <span class="mtl-track-pick__content">
          <span class="mtl-track-pick__primary">{{ track.displayName }}</span>
          <span class="mtl-track-pick__secondary">
            <ActivityTypeBadge v-if="track.activityType" :type="track.activityType" size="xs" />
            <span class="mtl-track-pick__date">{{ track.date }}</span>
            <span v-if="track.description" class="mtl-track-pick__description">{{ track.description }}</span>
          </span>
        </span>
        <span v-if="showMediaStatus || distanceLabel(track.distanceMeters)" class="mtl-track-pick__status">
          <span
            v-if="showMediaStatus"
            class="mtl-track-pick__media"
            :class="{ 'mtl-track-pick__media--empty': (track.matchedMediaCount ?? 0) === 0 }"
          >
            <i v-if="(track.matchedMediaCount ?? 0) > 0" class="bi bi-camera-fill" aria-hidden="true"></i>
            {{ mediaLabel(track.matchedMediaCount) }}
          </span>
          <span v-if="distanceLabel(track.distanceMeters)" class="mtl-track-pick__distance">
            {{ distanceLabel(track.distanceMeters) }}
          </span>
        </span>
        <i class="bi bi-chevron-right mtl-track-pick__chevron" aria-hidden="true"></i>
      </button>
    </li>
  </ul>
</template>

<script setup lang="ts">
import ActivityTypeBadge from '@/components/ui/ActivityTypeBadge.vue';
import TrackShapePreview from '@/components/ui/TrackShapePreview.vue';
import type { TrackPopupMeta } from '@/components/map/composables/mapControllerRuntime';

defineOptions({ name: 'NearbyTrackList' });

withDefaults(
  defineProps<{
    tracks: TrackPopupMeta[];
    showMediaStatus?: boolean;
  }>(),
  {
    showMediaStatus: false,
  }
);

const emit = defineEmits<{
  select: [trackId: number];
}>();

function mediaLabel(count: number | undefined): string {
  const normalizedCount = count ?? 0;
  if (normalizedCount === 0) return 'No matched photos';
  return `${normalizedCount.toLocaleString()} ${normalizedCount === 1 ? 'photo' : 'photos'}`;
}

function distanceLabel(distanceMeters: number | undefined): string {
  if (distanceMeters == null || !Number.isFinite(distanceMeters) || distanceMeters < 0) return '';
  if (distanceMeters < 1000) return `${Math.round(distanceMeters).toLocaleString()} m away`;
  return `${(distanceMeters / 1000).toLocaleString(undefined, { maximumFractionDigits: 1 })} km away`;
}
</script>

<style scoped>
.track-selection-list {
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
  padding: 0;
  margin: 0;
  list-style: none;
}

.mtl-track-pick {
  display: flex;
  align-items: center;
  width: 100%;
  gap: 0.75rem;
  padding: 0.55rem 0.75rem;
  color: var(--text-secondary);
  text-align: left;
  cursor: pointer;
  background: linear-gradient(135deg, var(--surface-glass-heavy), var(--surface-glass-subtle));
  border: 1px solid var(--border-subtle);
  border-radius: 0.95rem;
  transition:
    transform 0.15s,
    background 0.12s,
    border-color 0.12s,
    color 0.12s;
}

.mtl-track-pick:hover {
  color: var(--text-primary);
  background: color-mix(in srgb, var(--accent-bg) 65%, var(--surface-glass-heavy));
  border-color: color-mix(in srgb, var(--accent-muted) 55%, var(--border-default));
  transform: translateY(-1px);
}

.mtl-track-pick:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

.mtl-track-pick__content {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  gap: 0.18rem;
  min-width: 0;
}

.mtl-track-pick__shape {
  flex-shrink: 0;
  opacity: 0.7;
}

.mtl-track-pick:hover .mtl-track-pick__shape {
  opacity: 1;
}

.mtl-track-pick__primary {
  min-width: 0;
  overflow: hidden;
  color: var(--text-primary);
  font-size: var(--text-sm-size);
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mtl-track-pick__secondary {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 0.45rem;
  color: var(--text-muted);
  font-size: var(--text-xs-size);
}

.mtl-track-pick__date {
  flex: 0 0 auto;
  color: inherit;
  font-weight: 600;
  white-space: nowrap;
}

.mtl-track-pick__description {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mtl-track-pick__description::before {
  margin-right: 0.35rem;
  content: '•';
}

.mtl-track-pick__status {
  display: flex;
  flex: 0 0 auto;
  align-items: flex-end;
  flex-direction: column;
  gap: 0.2rem;
  font-size: var(--text-xs-size);
  white-space: nowrap;
}

.mtl-track-pick__media {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  color: var(--accent);
  font-weight: 700;
}

.mtl-track-pick__media--empty,
.mtl-track-pick__distance {
  color: var(--text-muted);
  font-weight: 500;
}

.mtl-track-pick__chevron {
  flex: 0 0 auto;
  color: var(--text-muted);
  font-size: var(--text-base-size);
}

@media (max-width: 600px) {
  .mtl-track-pick {
    gap: 0.55rem;
    padding-inline: 0.6rem;
  }

  .mtl-track-pick__status {
    align-items: flex-start;
  }
}
</style>
