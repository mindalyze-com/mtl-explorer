<template>
  <div
    class="map-identification-status"
    role="status"
    aria-label="Map lookup status"
    aria-live="polite"
    :tabindex="visibleSources.length ? 0 : -1"
  >
    <span v-for="source in visibleSources" :key="source.id">
      {{ source.label }}:
      {{
        source.status === 'pending' ? 'loading…' : source.status === 'cancelled' ? 'lookup cancelled' : 'unavailable'
      }}
      <span v-if="source.message"> · {{ source.message }}</span>
    </span>
  </div>
</template>
<script setup lang="ts">
import { computed } from 'vue';
import type { MapInteractionState } from './mapInteractionCoordinator';
const props = defineProps<{ state: MapInteractionState }>();
const visibleSources = computed(() => props.state.sources.filter((source) => source.status !== 'complete'));
</script>
<style scoped>
.map-identification-status {
  min-height: 2.5rem;
  display: flex;
  flex-direction: column;
  justify-content: center;
  font-size: 0.8rem;
  color: var(--text-secondary);
}
</style>
