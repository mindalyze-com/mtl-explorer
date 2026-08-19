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
      {{ tracks.length > 0 ? 'Refreshing track details…' : 'Loading track details…' }}
    </div>

    <div v-if="error" class="filter-review__error" role="alert">
      <span>{{ error }}</span>
      <button type="button" @click="emit('retry')">Retry</button>
    </div>

    <TrackBrowserView
      v-if="!loading || tracks.length > 0"
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
    error?: string;
    entries?: QueryResultEntry[];
  }>(),
  {
    title: 'Matching tracks',
    loading: false,
    error: '',
    entries: () => [],
  }
);

const emit = defineEmits<TrackSelectionEvents & { 'update:modelValue': [value: boolean]; retry: [] }>();

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

.filter-review__error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  margin: 0 1rem 0.5rem;
  padding: 0.55rem 0.7rem;
  border: 1px solid var(--warning-border, var(--border-default));
  border-radius: 0.5rem;
  background: var(--warning-soft, var(--surface-elevated));
  color: var(--text-primary);
  font-size: var(--text-sm-size);
}

.filter-review__error button {
  flex: 0 0 auto;
  border: 1px solid var(--border-default);
  border-radius: 999px;
  background: var(--surface-elevated);
  color: var(--accent);
  cursor: pointer;
  font: inherit;
  padding: 0.3rem 0.6rem;
}
</style>
