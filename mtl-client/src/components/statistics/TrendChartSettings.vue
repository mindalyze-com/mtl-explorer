<template>
  <button
    ref="trigger"
    type="button"
    class="trend-settings-button"
    :class="{ 'trend-settings-button--active': open }"
    aria-label="Chart settings"
    title="Chart settings"
    :aria-expanded="open"
    :aria-controls="open ? panelId : undefined"
    aria-haspopup="dialog"
    @click="popover?.toggle($event)"
  >
    <i class="bi bi-sliders2" aria-hidden="true"></i>
  </button>
  <Popover
    :id="panelId"
    ref="popover"
    append-to="body"
    aria-label="Chart settings"
    @show="open = true"
    @hide="open = false"
  >
    <div class="trend-settings" @keydown.esc.stop.prevent="close">
      <div class="trend-settings__heading">
        <strong>Chart settings</strong>
        <button type="button" class="trend-settings__icon" aria-label="Close chart settings" autofocus @click="close">
          <i class="bi bi-x-lg" aria-hidden="true"></i>
        </button>
      </div>
      <div class="trend-settings__label">
        <span>Chart height</span>
        <output>{{ height }} px</output>
      </div>
      <div class="trend-settings__height">
        <button
          type="button"
          class="trend-settings__icon"
          aria-label="Make charts smaller"
          title="Make charts smaller"
          :disabled="height <= TREND_CHART_HEIGHT_MIN"
          @click="emit('height', height - TREND_CHART_HEIGHT_STEP)"
        >
          <i class="bi bi-arrows-collapse-vertical" aria-hidden="true"></i>
        </button>
        <MtlSlider
          :model-value="height"
          :min="TREND_CHART_HEIGHT_MIN"
          :max="maxHeight"
          :step="TREND_CHART_HEIGHT_STEP"
          aria-label="Chart height"
          :aria-value-text="`${height} pixels`"
          @update:model-value="emit('height', $event)"
        />
        <button
          type="button"
          class="trend-settings__icon"
          aria-label="Make charts taller"
          title="Make charts taller"
          :disabled="height >= maxHeight"
          @click="emit('height', height + TREND_CHART_HEIGHT_STEP)"
        >
          <i class="bi bi-arrows-expand-vertical" aria-hidden="true"></i>
        </button>
      </div>
      <p class="trend-settings__hint">Applies to all trend charts. Saved in this browser.</p>
      <details v-if="charts.length > 1" class="trend-settings__order">
        <summary>Chart order</summary>
        <ol aria-label="Chart order">
          <li v-for="(chart, index) in charts" :key="chart.id">
            <i :class="chart.icon" aria-hidden="true"></i>
            <span>{{ chart.label }}</span>
            <button
              type="button"
              class="trend-settings__icon"
              :aria-label="`Move ${chart.label} up`"
              :title="`Move ${chart.label} up`"
              :disabled="index === 0"
              @click="move(chart, -1)"
            >
              <i class="bi bi-arrow-up" aria-hidden="true"></i>
            </button>
            <button
              type="button"
              class="trend-settings__icon"
              :aria-label="`Move ${chart.label} down`"
              :title="`Move ${chart.label} down`"
              :disabled="index === charts.length - 1"
              @click="move(chart, 1)"
            >
              <i class="bi bi-arrow-down" aria-hidden="true"></i>
            </button>
          </li>
        </ol>
      </details>
      <span class="sr-only" role="status">{{ announcement }}</span>
      <button type="button" class="trend-settings__reset" :disabled="isDefault" @click="reset">Reset to default</button>
    </div>
  </Popover>
</template>

<script setup lang="ts">
import { ref, useId } from 'vue';
import Popover from 'primevue/popover';
import MtlSlider from '@/components/ui/MtlSlider.vue';
import {
  TREND_CHART_HEIGHT_MIN,
  TREND_CHART_HEIGHT_STEP,
  type TrendChartChoice,
  type TrendChartId,
} from './trendChartPreferences';

const props = defineProps<{ height: number; maxHeight: number; charts: TrendChartChoice[]; isDefault: boolean }>();
const emit = defineEmits<{
  height: [value: number | number[]];
  move: [id: TrendChartId, direction: -1 | 1];
  reset: [];
}>();
const popover = ref<InstanceType<typeof Popover> | null>(null);
const trigger = ref<HTMLButtonElement | null>(null);
const panelId = useId();
const open = ref(false);
const announcement = ref('');

function close(): void {
  popover.value?.hide();
  trigger.value?.focus({ preventScroll: true });
}

function move(chart: TrendChartChoice, direction: -1 | 1): void {
  const position = props.charts.findIndex((candidate) => candidate.id === chart.id) + direction + 1;
  emit('move', chart.id, direction);
  announcement.value = `${chart.label} moved to position ${position} of ${props.charts.length}.`;
}

function reset(): void {
  emit('reset');
  announcement.value = 'Chart height and order reset to default.';
}
</script>

<style scoped>
.trend-settings-button,
.trend-settings__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  width: 2rem;
  height: 2rem;
  border: 1px solid transparent;
  border-radius: 0.4rem;
  background: transparent;
  color: var(--text-secondary);
  font: inherit;
  cursor: pointer;
}
.trend-settings-button {
  align-self: center;
}
.trend-settings-button:hover,
.trend-settings-button--active,
.trend-settings__icon:hover:not(:disabled) {
  color: var(--accent);
  background: var(--accent-subtle);
}
button:focus-visible,
summary:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}
button:disabled {
  opacity: 0.35;
  cursor: default;
}
.trend-settings {
  width: min(19rem, calc(100vw - 4rem));
  max-height: min(36rem, 65dvh);
  overflow-y: auto;
  overscroll-behavior: contain;
  color: var(--text-primary);
  font-size: var(--text-sm-size);
}
.trend-settings__heading,
.trend-settings__label,
.trend-settings__height {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}
.trend-settings__heading {
  margin-bottom: 0.75rem;
}
.trend-settings__label output {
  color: var(--text-secondary);
  font-variant-numeric: tabular-nums;
}
.trend-settings__height {
  margin-top: 0.3rem;
}
.trend-settings__height .mtl-slider {
  flex: 1;
}
.trend-settings__hint {
  margin: 0.5rem 0 0.75rem;
  color: var(--text-secondary);
  line-height: var(--text-sm-lh);
}
.trend-settings__order {
  border-top: 1px solid var(--border-default);
  border-bottom: 1px solid var(--border-default);
}
.trend-settings__order summary {
  padding: 0.75rem 0;
  cursor: pointer;
}
.trend-settings__order ol {
  margin: 0;
  padding: 0 0 0.5rem;
  list-style: none;
}
.trend-settings__order li {
  display: flex;
  align-items: center;
  gap: 0.35rem;
}
.trend-settings__order li > i {
  color: var(--text-muted);
  margin-right: 0.25rem;
}
.trend-settings__order li > span {
  flex: 1;
}
.trend-settings__reset {
  margin-top: 0.75rem;
  padding: 0.35rem 0;
  border: 0;
  background: transparent;
  color: var(--accent);
  font: inherit;
  cursor: pointer;
}
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
  border: 0;
}
@media (pointer: coarse) {
  .trend-settings-button,
  .trend-settings__icon {
    width: 2.75rem;
    height: 2.75rem;
  }
}
</style>
