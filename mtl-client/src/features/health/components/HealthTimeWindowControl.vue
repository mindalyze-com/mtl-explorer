<template>
  <div class="health-time-window">
    <div class="health-time-window__bar">
      <div class="health-time-window__presets" role="group" aria-label="Time window">
        <button
          v-for="preset in PRESETS"
          :key="preset.label"
          type="button"
          :aria-pressed="activePreset === preset.duration"
          @click="applyPreset(preset.duration)"
        >
          {{ preset.label }}
        </button>
      </div>
      <div class="health-time-window__range">
        <button type="button" aria-label="Earlier" :disabled="visibleWindow.start <= bounds.start" @click="shift(-1)">
          <i class="bi bi-chevron-left" aria-hidden="true" />
        </button>
        <button
          type="button"
          class="health-time-window__label"
          :aria-expanded="editing"
          aria-label="Set exact time range"
          @click="toggleEditing"
        >
          <i class="bi bi-clock" aria-hidden="true" />{{ formatTime(visibleWindow.start) }} –
          {{ formatEnd(visibleWindow.end) }}
        </button>
        <button type="button" aria-label="Later" :disabled="visibleWindow.end >= bounds.end" @click="shift(1)">
          <i class="bi bi-chevron-right" aria-hidden="true" />
        </button>
      </div>
      <span class="health-time-window__hint">{{ coarsePointer ? 'Pinch' : 'Drag' }} on a graph to zoom</span>
    </div>
    <form v-if="editing" class="health-time-window__form" aria-label="Exact time range" @submit.prevent="applyExact">
      <label>
        From
        <InputText ref="fromInput" v-model="fromTime" type="time" step="1" required aria-label="From time" />
      </label>
      <label>
        To
        <InputText v-model="toTime" type="time" step="1" required aria-label="To time" />
      </label>
      <div class="health-time-window__actions">
        <Button label="Cancel" severity="secondary" outlined size="small" @click="editing = false" />
        <Button label="Apply" type="submit" size="small" :disabled="!!exactError" />
      </div>
      <p v-if="exactError" class="health-time-window__error" role="alert">{{ exactError }}</p>
    </form>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref } from 'vue';
import Button from 'primevue/button';
import InputText from 'primevue/inputtext';
import { useMediaQuery } from '@/composables/useMediaQuery';
import { localDateBoundary } from '../utils/dateBoundary';
import type { HealthChartTimeWindow } from '../utils/healthChartPreferences';

const props = defineProps<{ day: string; modelValue?: HealthChartTimeWindow; focusTime?: number }>();
const emit = defineEmits<{ 'update:modelValue': [value: HealthChartTimeWindow | undefined] }>();
const SECOND_MS = 1_000;
const MINUTE_MS = 60 * SECOND_MS;
const HOUR_MS = 60 * MINUTE_MS;
const PRESETS = [
  { label: 'Full day', duration: undefined },
  { label: '6 h', duration: 6 * HOUR_MS },
  { label: '1 h', duration: HOUR_MS },
  { label: '15 min', duration: 15 * MINUTE_MS },
  { label: '1 min', duration: MINUTE_MS },
];
const coarsePointer = useMediaQuery('(pointer: coarse)');
const editing = ref(false);
const fromTime = ref('');
const toTime = ref('');
const fromInput = ref<{ $el: HTMLInputElement } | null>(null);

const bounds = computed(() => {
  const start = localDateBoundary(props.day, false)!;
  const end = new Date(start);
  end.setDate(end.getDate() + 1);
  return { start: start.getTime(), end: end.getTime() };
});
const visibleWindow = computed(() => props.modelValue ?? bounds.value);
const activePreset = computed(() => {
  const duration = visibleWindow.value.end - visibleWindow.value.start;
  if (duration >= bounds.value.end - bounds.value.start) return undefined;
  return PRESETS.find((preset) => preset.duration === duration)?.duration ?? null;
});

function update(start: number, end: number) {
  const { start: dayStart, end: dayEnd } = bounds.value;
  emit('update:modelValue', start <= dayStart && end >= dayEnd ? undefined : { start, end });
}
function applyPreset(duration?: number) {
  const { start, end } = bounds.value;
  if (!duration) return update(start, end);
  const focus = props.focusTime;
  const center =
    focus != null && focus >= visibleWindow.value.start && focus <= visibleWindow.value.end
      ? focus
      : (visibleWindow.value.start + visibleWindow.value.end) / 2;
  const from = Math.max(start, Math.min(Math.round((center - duration / 2) / SECOND_MS) * SECOND_MS, end - duration));
  update(from, from + duration);
}
function shift(direction: -1 | 1) {
  const { start, end } = bounds.value;
  const duration = visibleWindow.value.end - visibleWindow.value.start;
  const from = Math.max(start, Math.min(visibleWindow.value.start + direction * duration, end - duration));
  update(from, from + duration);
}

function formatTime(value: number) {
  const time = new Date(value);
  const parts = [time.getHours(), time.getMinutes(), time.getSeconds()];
  return parts.map((part) => String(part).padStart(2, '0')).join(':');
}
function formatEnd(value: number) {
  // The day ends at the following midnight; show it as 24:00 rather than 00:00.
  return value >= bounds.value.end ? '24:00:00' : formatTime(value);
}
function parseTime(value: string): number | undefined {
  if (!/^\d{2}:\d{2}(:\d{2})?$/.test(value)) return;
  const [hours, minutes, seconds = 0] = value.split(':').map(Number);
  const time = new Date(`${props.day}T${value.length === 5 ? `${value}:00` : value}`);
  if (time.getHours() !== hours || time.getMinutes() !== minutes || time.getSeconds() !== seconds) return;
  return time.getTime();
}
const exactRange = computed(() => {
  const start = parseTime(fromTime.value);
  // 23:59:59 is the last input value; treat it as the end of the day.
  const end = toTime.value === '23:59:59' ? bounds.value.end : parseTime(toTime.value);
  return { start, end };
});
const exactError = computed(() => {
  const { start, end } = exactRange.value;
  if (start == null || end == null) return 'Enter valid times for this day.';
  return end <= start ? 'To must be after From.' : '';
});
async function toggleEditing() {
  editing.value = !editing.value;
  if (!editing.value) return;
  fromTime.value = formatTime(visibleWindow.value.start);
  toTime.value = visibleWindow.value.end >= bounds.value.end ? '23:59:59' : formatTime(visibleWindow.value.end);
  await nextTick();
  fromInput.value?.$el.focus();
}
function applyExact() {
  const { start, end } = exactRange.value;
  if (exactError.value || start == null || end == null) return;
  update(start, end);
  editing.value = false;
}
</script>

<style scoped>
.health-time-window {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}
.health-time-window__bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.5rem 0.75rem;
}
.health-time-window__presets {
  display: inline-flex;
  border: 1px solid var(--border-default);
  border-radius: 8px;
  overflow: hidden;
}
.health-time-window__presets button {
  min-height: 2.5rem;
  padding: 0.4rem 0.65rem;
  border: 0;
  border-right: 1px solid var(--border-default);
  background: transparent;
  color: var(--text-secondary);
  font: inherit;
  font-size: var(--text-xs-size);
  white-space: nowrap;
  cursor: pointer;
}
.health-time-window__presets button:last-child {
  border-right: 0;
}
.health-time-window__presets button[aria-pressed='true'] {
  background: var(--accent-bg);
  color: var(--accent-text);
}
.health-time-window__range {
  display: inline-flex;
  align-items: center;
  border: 1px solid var(--border-default);
  border-radius: 8px;
  overflow: hidden;
}
.health-time-window__range button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.45rem;
  min-width: 2.5rem;
  min-height: 2.5rem;
  padding: 0.4rem 0.6rem;
  border: 0;
  background: transparent;
  color: var(--text-secondary);
  font: inherit;
  font-size: var(--text-xs-size);
  cursor: pointer;
}
.health-time-window__range button:disabled {
  opacity: 0.4;
  cursor: default;
}
.health-time-window__range .health-time-window__label {
  border-inline: 1px solid var(--border-default);
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.health-time-window button:not(:disabled):hover {
  background: var(--surface-hover);
}
.health-time-window button:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: -2px;
}
.health-time-window__hint {
  color: var(--text-muted);
  font-size: var(--text-2xs-size);
}
.health-time-window__form {
  display: flex;
  align-items: flex-end;
  flex-wrap: wrap;
  gap: 0.5rem 0.75rem;
}
.health-time-window__form label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
}
.health-time-window__form input {
  min-height: 2.5rem;
  width: 9.5rem;
}
.health-time-window__actions {
  display: flex;
  gap: 0.5rem;
}
.health-time-window__error {
  flex-basis: 100%;
  margin: 0;
  color: var(--error-text);
  font-size: var(--text-xs-size);
}
@media (max-width: 700px) {
  .health-time-window__presets {
    display: grid;
    grid-template-columns: repeat(5, minmax(0, 1fr));
    width: 100%;
  }
  .health-time-window__presets button {
    min-height: 2.75rem;
    padding-inline: 0.3rem;
  }
  .health-time-window__range {
    display: grid;
    grid-template-columns: 2.75rem minmax(0, 1fr) 2.75rem;
    width: 100%;
  }
  .health-time-window__range button {
    min-height: 2.75rem;
  }
  .health-time-window__form label {
    flex: 1 1 8rem;
  }
  .health-time-window__form input {
    width: 100%;
  }
}
</style>
