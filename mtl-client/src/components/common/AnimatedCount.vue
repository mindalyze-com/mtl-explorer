<template>
  <span class="animated-count" :aria-label="latestValue === null ? 'Count unavailable' : formatCount(latestValue)">
    {{ displayValue === null ? '—' : formatCount(displayValue) }}
  </span>
</template>

<script setup lang="ts">
import { useAnimatedCount } from '@/composables/useAnimatedCount';
import type { CountAnimation } from '@/utils/countAnimation';
import { formatLocaleNumber } from '@/utils/Utils';

const props = defineProps<{
  value?: number | null;
  sample?: unknown;
  resetKey?: unknown;
  resetOnDecrease?: boolean;
  animation?: CountAnimation | null;
  format?: (value: number) => string;
}>();
const { displayValue, latestValue } = useAnimatedCount(() => props.value, {
  sample: () => props.sample,
  resetKey: () => props.resetKey,
  resetOnDecrease: () => props.resetOnDecrease,
  animation: () => props.animation,
});
function formatCount(value: number) {
  return props.format ? props.format(value) : formatLocaleNumber(value);
}
</script>

<style scoped>
.animated-count {
  font-variant-numeric: tabular-nums;
}
</style>
