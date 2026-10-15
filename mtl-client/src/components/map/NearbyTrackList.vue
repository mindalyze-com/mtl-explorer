<template>
  <ul class="track-selection-list">
    <li v-for="track in tracks" :key="track.id">
      <TrackChoiceRow
        :track-id="track.id"
        :label="track.displayName"
        :activity-type="track.activityType"
        :date="track.date"
        :recorded-at="track.recordedAt"
        :length-meters="track.lengthMeters"
        :ascent-meters="track.ascentMeters"
        :description="track.description"
        :matched-media-count="track.matchedMediaCount"
        :show-empty-media="showMediaStatus"
        @click="emit('select', track.id)"
      />
    </li>
  </ul>
</template>

<script setup lang="ts">
import TrackChoiceRow from './TrackChoiceRow.vue';
import type { TrackPopupMeta } from '@/components/map/composables/mapControllerRuntime';

defineOptions({ name: 'NearbyTrackList' });
withDefaults(defineProps<{ tracks: TrackPopupMeta[]; showMediaStatus?: boolean }>(), { showMediaStatus: false });
const emit = defineEmits<{ select: [trackId: number] }>();
</script>

<style scoped>
.track-selection-list {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  padding: 0;
  margin: 0;
  list-style: none;
  container: track-choice-results / inline-size;
}
</style>
