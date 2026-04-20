<template>
  <div class="track-browser-quick-views">
    <span class="track-browser-quick-views__label">Views</span>
    <SelectButton
      :model-value="modelValue"
      :options="options"
      optionLabel="label"
      optionValue="value"
      @update:model-value="onUpdate"
      aria-label="Track browser quick views"
    />
  </div>
</template>

<script setup lang="ts">
import type { TrackBrowserOption, TrackBrowserPreset } from './trackBrowser.types';

const props = defineProps<{
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
.track-browser-quick-views {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
}

.track-browser-quick-views__label {
  font-size: 0.8rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--text-muted);
}

.track-browser-quick-views :deep(.p-selectbutton) {
  flex-wrap: wrap;
}

@media (max-width: 768px) {
  .track-browser-quick-views {
    align-items: stretch;
  }
}
</style>