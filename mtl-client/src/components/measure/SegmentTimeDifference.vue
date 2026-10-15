<template>
  <span class="segment-time-difference" :class="'segment-time-difference--' + tone">
    <i
      v-if="rounded && !reference && !loading"
      class="bi"
      :class="rounded < 0 ? 'bi-arrow-up-right' : 'bi-arrow-down-right'"
      aria-hidden="true"
    />
    {{ label }}
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { formatNumber } from '@/utils/Utils';
const props = defineProps<{ seconds?: number | null; loading?: boolean; reference?: boolean }>();
const DISPLAY_PRECISION = 2;
const rounded = computed(() =>
  props.seconds == null || !Number.isFinite(props.seconds) ? null : Number(props.seconds.toFixed(DISPLAY_PRECISION))
);
const tone = computed(() =>
  props.reference || props.loading || rounded.value == null || rounded.value === 0
    ? 'neutral'
    : rounded.value < 0
      ? 'faster'
      : 'slower'
);
const label = computed(() => {
  if (props.reference) return 'Reference';
  if (props.loading) return 'Loading…';
  if (rounded.value == null) return 'Unavailable';
  if (rounded.value === 0) return 'Same time';
  const seconds = Math.abs(rounded.value);
  return `${formatNumber(seconds, Number.isInteger(seconds) ? 0 : DISPLAY_PRECISION)} sec ${rounded.value < 0 ? 'faster' : 'slower'}`;
});
</script>

<style scoped>
.segment-time-difference {
  display: inline-flex;
  align-items: baseline;
  gap: 0.25rem;
  font-variant-numeric: tabular-nums;
}
.segment-time-difference--faster {
  color: var(--success);
}
.segment-time-difference--slower {
  color: var(--error);
}
</style>
