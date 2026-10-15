<template>
  <div v-if="processing" class="track-processing-notice">
    <p v-if="processing?.state === TrackProcessingStatusDtoStateEnum.Running" class="processing-notice" role="status">
      {{
        empty
          ? 'No matching tracks found yet. Processing is still running, so results may be incomplete.'
          : 'Tracks are still being processed. Some nearby tracks may be missing.'
      }}
    </p>
    <p
      v-else-if="processing?.state === TrackProcessingStatusDtoStateEnum.Unavailable"
      class="processing-notice processing-notice--warning"
      role="status"
    >
      Processing status is unavailable. Nearby results may be incomplete.
    </p>
    <p
      v-if="processing && processing.failedGpsFiles > 0"
      class="processing-notice processing-notice--warning"
      role="status"
    >
      {{ processing.failedGpsFiles.toLocaleString() }}
      {{ processing.failedGpsFiles === 1 ? 'GPS file could' : 'GPS files could' }} not be processed. Nearby results may
      be incomplete.
    </p>
    <p v-if="empty && processing?.state !== TrackProcessingStatusDtoStateEnum.Running" class="empty-results">
      No matching tracks found.
    </p>
  </div>
</template>

<script setup lang="ts">
import { TrackProcessingStatusDtoStateEnum, type TrackProcessingStatusDto } from 'x8ing-mtl-api-typescript-fetch';
defineProps<{ processing: TrackProcessingStatusDto | null; empty?: boolean }>();
</script>

<style scoped>
.track-processing-notice {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}
.processing-notice {
  margin: 0;
  padding: 0.75rem;
  color: var(--text-primary);
  background: color-mix(in srgb, var(--info) 9%, var(--surface-sheet-solid));
  border: 1px solid color-mix(in srgb, var(--info) 24%, transparent);
  border-radius: 0.75rem;
}
.processing-notice--warning {
  background: color-mix(in srgb, var(--warning) 9%, var(--surface-sheet-solid));
  border-color: color-mix(in srgb, var(--warning) 24%, transparent);
}
.empty-results {
  margin: 0;
  color: var(--text-secondary);
}
</style>
