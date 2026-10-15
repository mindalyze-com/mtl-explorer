<template>
  <section class="latest-activity-preview" aria-label="Latest activity">
    <i class="bi bi-clock-history latest-activity-preview__icon" aria-hidden="true"></i>
    <p v-if="state.loading" class="latest-activity-preview__state" role="status">Loading latest activity…</p>
    <div v-else-if="state.error" class="latest-activity-preview__state" role="status">
      <p>Latest activity could not be loaded.</p>
      <button type="button" class="latest-activity-preview__retry" @click="emit('retry')">Retry</button>
    </div>
    <button
      v-else-if="state.track"
      type="button"
      class="latest-activity-preview__track"
      :aria-label="`Open track: ${state.track.displayName}`"
      :title="`Open track: ${state.track.displayName}`"
      @click="emit('open-track', state.track.trackId)"
    >
      <span class="latest-activity-preview__name">Latest: {{ state.track.displayName }}</span>
      <span class="latest-activity-preview__meta">
        <span v-if="state.track.startDate">{{ formatDate(state.track.startDate) }}</span>
        <span>{{ distance }}</span>
        <span>{{ duration }}</span>
      </span>
    </button>
    <p v-else class="latest-activity-preview__state">No activities in the current filter.</p>
  </section>
</template>

<script lang="ts">
export interface LatestActivityPreviewTrack {
  trackId: number;
  displayName: string;
  activityType?: string;
  startDate?: Date;
  distanceM: number;
  durationMs: number | null;
}

export interface LatestActivityPreviewState {
  track: LatestActivityPreviewTrack | null;
  loading: boolean;
  error: boolean;
}
</script>

<script setup lang="ts">
import { computed } from 'vue';
import { formatDate, formatDistanceSmart, formatDurationSmart } from '@/utils/Utils';
import { useMeasurementSystem } from '@/composables/useMeasurementSystem';

const props = defineProps<{ state: LatestActivityPreviewState }>();
const emit = defineEmits<{ 'open-track': [id: number]; retry: [] }>();
const { measurementSystem } = useMeasurementSystem();
const distance = computed(() => {
  // Formatting helpers read the preference outside Vue's dependency tracking.
  void measurementSystem.value;
  return formatDistanceSmart(props.state.track?.distanceM ?? 0);
});
const duration = computed(() =>
  props.state.track?.durationMs == null ? 'Time unavailable' : formatDurationSmart(props.state.track.durationMs)
);
</script>

<style scoped>
.latest-activity-preview {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  min-width: 0;
  min-height: 2.75rem;
  margin-top: 0.75rem;
  color: var(--text-muted);
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}

.latest-activity-preview__icon {
  flex-shrink: 0;
  font-size: var(--text-lg-size);
}

.latest-activity-preview__track {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.15rem 0.4rem;
  min-width: 0;
  max-width: 100%;
  min-height: 2.75rem;
  padding: 0.3rem 0;
  border: 0;
  border-radius: 0.3rem;
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.latest-activity-preview__name {
  max-width: min(48ch, 100%);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.latest-activity-preview__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.15rem 0.4rem;
}

.latest-activity-preview__meta > span {
  white-space: nowrap;
}

.latest-activity-preview__meta > span::before {
  content: '·';
  margin-right: 0.4rem;
}

.latest-activity-preview__track:hover .latest-activity-preview__name,
.latest-activity-preview__retry:hover {
  text-decoration: underline;
}

.latest-activity-preview__track:focus-visible,
.latest-activity-preview__retry:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 4px;
}

.latest-activity-preview__state {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.4rem;
  margin: 0;
}

.latest-activity-preview__state > p {
  margin: 0;
}

.latest-activity-preview__retry {
  min-height: 2.75rem;
  padding: 0.3rem 0;
  border: 0;
  background: transparent;
  color: var(--accent-text);
  font: inherit;
  text-decoration: underline;
  cursor: pointer;
}

@container section-hub (max-width: 40rem) {
  .latest-activity-preview__name {
    flex-basis: 100%;
    max-width: 100%;
  }

  .latest-activity-preview__meta > span:first-child::before {
    content: none;
  }
}
</style>
