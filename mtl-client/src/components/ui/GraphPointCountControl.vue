<template>
  <div class="graph-point-count">
    <span
      >Points <strong>{{ modelValue }}</strong></span
    >
    <div class="graph-point-count-slider">
      <button
        type="button"
        :disabled="modelValue <= TRACK_DETAILS_CHART_POINTS_MIN"
        aria-label="Load fewer chart points"
        @click="nudge(-1)"
      >
        <i class="bi bi-dash-lg" aria-hidden="true" />
      </button>
      <MtlSlider
        :model-value="sliderValue"
        :min="TRACK_DETAILS_CHART_POINT_SLIDER_MIN"
        :max="TRACK_DETAILS_CHART_POINT_SLIDER_MAX"
        :step="TRACK_DETAILS_CHART_POINT_SLIDER_STEP"
        aria-label="Adjust chart point count"
        :aria-value-text="`${modelValue} points`"
        @change="setSliderValue"
      />
      <button
        type="button"
        :disabled="modelValue >= TRACK_DETAILS_CHART_POINTS_MAX"
        aria-label="Load more chart points"
        @click="nudge(1)"
      >
        <i class="bi bi-plus-lg" aria-hidden="true" />
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import MtlSlider from './MtlSlider.vue';
import {
  TRACK_DETAILS_CHART_POINTS_MIN,
  TRACK_DETAILS_CHART_POINTS_MAX,
  TRACK_DETAILS_CHART_POINT_SLIDER_MIN,
  TRACK_DETAILS_CHART_POINT_SLIDER_MAX,
  TRACK_DETAILS_CHART_POINT_SLIDER_STEP,
  trackDetailsChartPointCountToSliderValue,
  trackDetailsChartPointSliderValueToCount,
  nextDistinctChartPointSliderValue,
} from '@/utils/trackDetailsChartPointSettings';

const props = defineProps<{ modelValue: number }>();
const emit = defineEmits<{ 'update:modelValue': [value: number] }>();
const sliderValue = computed(() => trackDetailsChartPointCountToSliderValue(props.modelValue));
function setSliderValue(value: number | number[]) {
  emit('update:modelValue', trackDetailsChartPointSliderValueToCount(Array.isArray(value) ? value[0] : value));
}
function nudge(direction: number) {
  setSliderValue(nextDistinctChartPointSliderValue(sliderValue.value, props.modelValue, direction));
}
</script>

<style scoped>
.graph-point-count {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
}
.graph-point-count strong {
  margin-left: 0.35rem;
  font-variant-numeric: tabular-nums;
}
.graph-point-count-slider {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  flex: 1;
  min-width: 12rem;
  max-width: 24rem;
}
.graph-point-count-slider .mtl-slider {
  flex: 1;
}
.graph-point-count-slider button {
  flex: none;
}
</style>
