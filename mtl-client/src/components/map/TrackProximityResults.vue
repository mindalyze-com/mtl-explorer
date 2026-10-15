<template>
  <div class="track-proximity-results">
    <TrackProcessingNotice :processing="processing" :empty="tracks.length === 0" />
    <NearbyTrackList v-if="tracks.length > 0" :tracks="tracks" @select="emit('select', $event)" />
  </div>
</template>

<script setup lang="ts">
import { type TrackProcessingStatusDto } from 'x8ing-mtl-api-typescript-fetch';
import NearbyTrackList from '@/components/map/NearbyTrackList.vue';
import TrackProcessingNotice from '@/components/map/TrackProcessingNotice.vue';
import type { TrackPopupMeta } from '@/components/map/composables/mapControllerRuntime';

defineProps<{ tracks: TrackPopupMeta[]; processing: TrackProcessingStatusDto | null }>();
const emit = defineEmits<{ select: [trackId: number] }>();
</script>

<style scoped>
.track-proximity-results {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}
</style>
