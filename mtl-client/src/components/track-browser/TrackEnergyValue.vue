<template>
  <span ref="valueEl">
    <template v-if="energyWh != null">{{ formatNumber(energyWh, 0) }} Wh</template>
    <span v-else class="track-energy-state" :title="stateMessage" :aria-label="`Energy: ${stateMessage}`">
      {{ stateLabel }}
    </span>
    <button
      v-if="energyWh == null && calculation.canRetry.value"
      type="button"
      class="track-energy-retry"
      @click.stop="calculation.retry()"
    >
      Retry
    </button>
  </span>
</template>

<script setup lang="ts">
import { computed, inject, onMounted, onBeforeUnmount, ref, watch } from 'vue';
import type { GpsTrack } from 'x8ing-mtl-api-typescript-fetch';
import { ENERGY_STATUS_CACHE, useEnergyCalculationStatus } from '@/composables/useEnergyCalculationStatus';
import { formatNumber } from '@/utils/Utils';

const props = defineProps<{ track: GpsTrack; processingPending?: boolean }>();
const emit = defineEmits<{ 'track-updated': [track: GpsTrack] }>();
const valueEl = ref<HTMLElement | null>(null);
const visible = ref(typeof IntersectionObserver === 'undefined');
let observer: IntersectionObserver | undefined;
onMounted(() => {
  if (typeof IntersectionObserver === 'undefined' || !valueEl.value) return;
  observer = new IntersectionObserver(([entry]) => (visible.value = entry?.isIntersecting ?? false));
  observer.observe(valueEl.value);
});
onBeforeUnmount(() => observer?.disconnect());
const refreshedTrack = ref<GpsTrack | null>(null);
watch(
  () => props.track,
  () => (refreshedTrack.value = null)
);
const currentTrack = computed(() => refreshedTrack.value ?? props.track);
const energyWh = computed(() => currentTrack.value.energyNetTotalWh);
const waitingForActivity = computed(() => currentTrack.value.activityTypeSource == null);
// While the shared queue is active, use its status instead of starting a poll
// for every missing table value. Only rendered cells query individual outcomes.
const statusTrack = computed(() =>
  !visible.value || energyWh.value != null || currentTrack.value.timingUnreliable || props.processingPending
    ? null
    : currentTrack.value
);
const calculation = useEnergyCalculationStatus(
  statusTrack,
  (updated) => {
    refreshedTrack.value = updated;
    emit('track-updated', updated);
  },
  { cache: inject(ENERGY_STATUS_CACHE, undefined), maxErrorRetries: 3 }
);
const stateLabel = computed(() => {
  if (currentTrack.value.timingUnreliable) return 'Unavailable';
  if (waitingForActivity.value && !calculation.message.value) return 'Pending';
  if (props.processingPending) return 'Processing…';
  if (calculation.status.value?.state === 'RETRYING') return 'Retrying';
  if (calculation.pending.value) return 'Pending';
  if (calculation.status.value?.state === 'UNAVAILABLE') return 'Unavailable';
  return calculation.message.value ? 'Status unavailable' : 'Checking…';
});
const stateMessage = computed(() => {
  if (currentTrack.value.timingUnreliable) return 'Unavailable because source timing is unreliable. See Track review.';
  if (waitingForActivity.value && !calculation.message.value) return 'Waiting for activity classification.';
  if (props.processingPending)
    return 'Energy processing is in progress. Individual results will be checked when it finishes.';
  return calculation.message.value || 'Checking energy calculation status.';
});
</script>

<style scoped>
.track-energy-state {
  color: var(--text-muted);
  font-size: var(--text-sm-size);
}
.track-energy-retry {
  min-height: 44px;
  margin-left: 0.35rem;
  padding: 0 0.4rem;
  border: 0;
  border-radius: 0.35rem;
  background: transparent;
  color: var(--accent);
  font: inherit;
  cursor: pointer;
}
.track-energy-retry:focus-visible {
  outline: 2px solid var(--accent);
}
</style>
