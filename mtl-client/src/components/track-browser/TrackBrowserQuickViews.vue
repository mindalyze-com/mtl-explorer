<template>
  <div class="track-browser-quick-views">
    <SelectButton
      class="segmented-control"
      :model-value="modelValue"
      :options="options"
      :allow-empty="false"
      option-label="label"
      option-value="value"
      aria-label="Track browser quick views"
      @update:model-value="onUpdate"
    />
  </div>
</template>

<script setup lang="ts">
import type { TrackBrowserOption, TrackBrowserPreset } from './trackBrowser.types';

defineProps<{
  modelValue: TrackBrowserPreset;
  options: TrackBrowserOption<TrackBrowserPreset>[];
}>();

const emit = defineEmits<{
  (event: 'update:modelValue', value: TrackBrowserPreset): void;
}>();

function onUpdate(value: TrackBrowserPreset) {
  emit('update:modelValue', value);
}
</script>

<style scoped>
.track-browser-quick-views :deep(.p-selectbutton) {
  flex-wrap: wrap;
}

@container track-browser (max-width: 360px) {
  .track-browser-quick-views :deep(.p-selectbutton) {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
