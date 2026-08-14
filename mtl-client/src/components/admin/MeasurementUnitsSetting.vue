<template>
  <div class="measurement-setting" data-test="measurement-units-setting">
    <div class="measurement-setting__copy">
      <span class="measurement-setting__label">Measurement units</span>
      <span class="measurement-setting__hint">Controls distance, elevation, speed, and weight formatting.</span>
    </div>

    <div class="measurement-setting__controls">
      <SelectButton
        :model-value="measurementSystem"
        :options="options"
        option-label="label"
        option-value="value"
        :allow-empty="false"
        aria-label="Measurement units"
        data-test="measurement-system-select"
        @update:model-value="setMeasurementSystem"
      />
      <Button
        label="Use default"
        size="small"
        link
        data-test="measurement-system-use-default"
        :disabled="explicitMeasurementSystem == null"
        @click="useDefaultMeasurementSystem"
      />
    </div>

    <code class="panel-preview" data-test="measurement-system-preview">Preview: {{ preview }}</code>
    <span class="panel-caption" data-test="measurement-system-source">{{ sourceLabel }}</span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import Button from 'primevue/button';
import SelectButton from 'primevue/selectbutton';
import { useMeasurementSystem } from '@/composables/useMeasurementSystem';
import { formatMeasurementPreview, type MeasurementSystem } from '@/utils/units';

const options: Array<{ label: string; value: MeasurementSystem }> = [
  { label: 'Metric', value: 'METRIC' },
  { label: 'Imperial (US)', value: 'US_CUSTOMARY' },
];

const {
  explicitMeasurementSystem,
  measurementSystem,
  measurementPreferenceSource,
  setMeasurementSystem,
  useDefaultMeasurementSystem,
} = useMeasurementSystem();

const preview = computed(() => formatMeasurementPreview(measurementSystem.value));
const sourceLabel = computed(() => {
  if (measurementPreferenceSource.value === 'explicit') return 'Using your saved preference.';
  if (measurementPreferenceSource.value === 'server') return 'Using the server default.';
  return 'Using the browser default.';
});
</script>

<style scoped>
.measurement-setting {
  display: grid;
  gap: 0.75rem;
  width: 100%;
}

.measurement-setting__copy {
  display: grid;
  gap: 0.25rem;
}

.measurement-setting__label {
  font-size: var(--text-sm-size);
  font-weight: 600;
}

.measurement-setting__hint,
.panel-caption {
  color: var(--text-faint);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}

.measurement-setting__controls {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.panel-preview {
  width: fit-content;
  max-width: 100%;
  padding: 0.55rem 0.7rem;
  border: 1px solid var(--code-border);
  border-radius: 0.5rem;
  background: var(--code-bg);
  font-size: var(--text-sm-size);
  overflow-wrap: anywhere;
}
</style>
