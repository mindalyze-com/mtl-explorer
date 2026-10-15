<template>
  <div class="settings-field" data-test="measurement-units-setting">
    <div class="admin-action-copy">
      <span class="settings-field__label">Measurement units</span>
      <span class="settings-field__hint">Controls distance, elevation, speed, and weight formatting.</span>
    </div>

    <div class="settings-control-group">
      <SelectButton
        class="segmented-control"
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

    <code class="settings-preview" data-test="measurement-system-preview">Preview: {{ preview }}</code>
    <span class="settings-field__hint" data-test="measurement-system-source">{{ sourceLabel }}</span>
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
