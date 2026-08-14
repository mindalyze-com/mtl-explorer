<template>
  <BottomSheet
    :model-value="modelValue"
    :title="title"
    icon="bi bi-table"
    :detents="FILTER_STANDARD_DETENTS"
    :no-backdrop="true"
    :z-index="5100"
    sheet-class="sheet--filter-detail sheet--filter-review"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-if="loading" class="filter-review__loading" role="status">
      <i class="pi pi-spin pi-spinner" aria-hidden="true"></i>
      Loading track details…
    </div>

    <TrackBrowserView
      v-else
      :tracks="tracks"
      @select-track="emit('select-track', $event)"
      @open-details="emit('open-details', $event)"
    />
  </BottomSheet>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { GpsTrack } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import type { TrackSelectionEvents } from '@/components/filter/filterEvents';
import type { QueryResultEntry } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/QueryResultEntry';
import BottomSheet from '@/components/ui/BottomSheet.vue';
import TrackBrowserView from '@/components/track-browser/TrackBrowserView.vue';
import { FILTER_STANDARD_DETENTS } from '@/components/filter/filterSheetLayout';

defineOptions({ name: 'FilterTrackReviewSheet' });

const props = withDefaults(
  defineProps<{
    modelValue: boolean;
    title?: string;
    loading?: boolean;
    entries?: QueryResultEntry[];
  }>(),
  {
    title: 'Matching tracks',
    loading: false,
    entries: () => [],
  }
);

const emit = defineEmits<TrackSelectionEvents & { 'update:modelValue': [value: boolean] }>();

const tracks = computed((): GpsTrack[] =>
  props.entries.flatMap((entry) => {
    if (!entry.gpsTrack) return [];
    if (entry.gpsTrack.id != null || entry.id == null) return [entry.gpsTrack];
    return [{ ...entry.gpsTrack, id: entry.id }];
  })
);
</script>

<style scoped>
.filter-review__loading {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem 1rem;
  color: var(--text-muted);
  font-size: var(--text-sm-size);
}
</style>
