<template>
  <div class="date-time-param">
    <div class="date-time-param__control date-time-param__control--date">
      <i class="bi bi-calendar3" aria-hidden="true"></i>
      <input
        :id="id"
        class="date-time-param__input"
        type="date"
        :value="dateValue"
        :aria-label="dateAriaLabel"
        @change="onDateInput"
      />
    </div>

    <div class="date-time-param__control date-time-param__control--time" :class="{ 'is-disabled': !dateValue }">
      <i class="bi bi-clock" aria-hidden="true"></i>
      <input
        class="date-time-param__input"
        type="time"
        :value="timeValue"
        :step="TIME_INPUT_STEP_SECONDS"
        :disabled="!dateValue"
        :aria-label="timeAriaLabel"
        @change="onTimeInput"
      />
    </div>

    <button
      type="button"
      class="date-time-param__clear"
      :disabled="!hasValue"
      :aria-label="clearAriaLabel"
      @click="emit('update:modelValue', null)"
    >
      <i class="bi bi-x-lg" aria-hidden="true"></i>
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';

defineOptions({ name: 'DateTimeParam' });

const DEFAULT_TIME_VALUE = '00:00';
const TIME_INPUT_STEP_SECONDS = 60;
const DATE_SEGMENT_COUNT = 3;
const DATE_YEAR_INDEX = 0;
const DATE_MONTH_INDEX = 1;
const DATE_DAY_INDEX = 2;
const TIME_HOUR_INDEX = 0;
const TIME_MINUTE_INDEX = 1;
const CALENDAR_MONTH_OFFSET = 1;
const MIN_DATE_PART = 1;

const props = defineProps<{
  id?: string;
  label: string;
  modelValue?: Date | Date[] | (Date | null)[] | null;
}>();

const emit = defineEmits<{
  (event: 'update:modelValue', value: Date | null): void;
}>();

const selectedDate = computed((): Date | null => {
  const value = Array.isArray(props.modelValue)
    ? props.modelValue.find((item) => item instanceof Date)
    : props.modelValue;
  return value instanceof Date && !Number.isNaN(value.getTime()) ? value : null;
});

const hasValue = computed((): boolean => selectedDate.value != null);
const dateValue = computed((): string => (selectedDate.value ? formatDateInputValue(selectedDate.value) : ''));
const timeValue = computed((): string =>
  selectedDate.value ? formatTimeInputValue(selectedDate.value) : DEFAULT_TIME_VALUE
);
const dateAriaLabel = computed((): string => `${props.label} date`);
const timeAriaLabel = computed((): string => `${props.label} time`);
const clearAriaLabel = computed((): string => `Clear ${props.label}`);

function onDateInput(event: Event): void {
  const datePart = inputValue(event);
  if (!datePart) {
    emit('update:modelValue', null);
    return;
  }
  emitMergedValue(datePart, timeValue.value);
}

function onTimeInput(event: Event): void {
  if (!dateValue.value) return;
  emitMergedValue(dateValue.value, inputValue(event) || DEFAULT_TIME_VALUE);
}

function emitMergedValue(datePart: string, timePart: string): void {
  const merged = mergeDateAndTime(datePart, timePart);
  if (merged) emit('update:modelValue', merged);
}

function mergeDateAndTime(datePart: string, timePart: string): Date | null {
  const dateSegments = datePart.split('-').map(Number);
  if (dateSegments.length !== DATE_SEGMENT_COUNT || dateSegments.some((segment) => !Number.isFinite(segment)))
    return null;

  const timeSegments = timePart.split(':').map(Number);
  const hours = Number.isFinite(timeSegments[TIME_HOUR_INDEX]) ? timeSegments[TIME_HOUR_INDEX] : 0;
  const minutes = Number.isFinite(timeSegments[TIME_MINUTE_INDEX]) ? timeSegments[TIME_MINUTE_INDEX] : 0;
  const monthIndex = dateSegments[DATE_MONTH_INDEX] - CALENDAR_MONTH_OFFSET;
  const date = new Date(dateSegments[DATE_YEAR_INDEX], monthIndex, dateSegments[DATE_DAY_INDEX], hours, minutes, 0, 0);

  if (
    date.getFullYear() !== dateSegments[DATE_YEAR_INDEX] ||
    date.getMonth() !== monthIndex ||
    date.getDate() !== dateSegments[DATE_DAY_INDEX] ||
    date.getDate() < MIN_DATE_PART
  ) {
    return null;
  }

  return date;
}

function formatDateInputValue(date: Date): string {
  return `${date.getFullYear()}-${pad2(date.getMonth() + CALENDAR_MONTH_OFFSET)}-${pad2(date.getDate())}`;
}

function formatTimeInputValue(date: Date): string {
  return `${pad2(date.getHours())}:${pad2(date.getMinutes())}`;
}

function inputValue(event: Event): string {
  return (event.target as HTMLInputElement | null)?.value ?? '';
}

function pad2(value: number): string {
  return String(value).padStart(2, '0');
}
</script>

<style scoped>
.date-time-param {
  display: grid;
  grid-template-columns: minmax(9rem, 1fr) minmax(6.6rem, 0.72fr) 2.35rem;
  gap: 0.45rem;
  align-items: center;
  width: 100%;
  min-width: 0;
}

.date-time-param__control {
  min-width: 0;
  min-height: 2.35rem;
  display: flex;
  align-items: center;
  gap: 0.45rem;
  border: 1px solid var(--border-default);
  border-radius: 0.5rem;
  padding: 0 0.6rem;
  background: var(--surface-glass-heavy);
  color: var(--text-muted);
  transition:
    border-color 0.15s,
    background 0.15s,
    box-shadow 0.15s;
}

.date-time-param__control:hover {
  border-color: var(--border-hover);
}

.date-time-param__control:focus-within {
  border-color: var(--accent-text);
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--accent-text) 35%, transparent);
}

.date-time-param__control.is-disabled {
  opacity: 0.58;
}

.date-time-param__control .bi {
  flex: 0 0 auto;
  font-size: var(--text-sm-size);
}

.date-time-param__input {
  width: 100%;
  min-width: 0;
  border: 0;
  padding: 0;
  background: transparent;
  color: var(--text-primary);
  font: inherit;
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
  outline: 0;
  color-scheme: light;
}

:global([data-theme='dark']) .date-time-param__input {
  color-scheme: dark;
}

.date-time-param__input:disabled {
  color: var(--text-muted);
  cursor: not-allowed;
}

.date-time-param__clear {
  width: 2.35rem;
  height: 2.35rem;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid transparent;
  border-radius: 0.5rem;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  transition:
    background 0.15s,
    color 0.15s,
    border-color 0.15s;
}

.date-time-param__clear:hover:not(:disabled),
.date-time-param__clear:focus-visible:not(:disabled) {
  border-color: color-mix(in srgb, var(--error) 30%, transparent);
  background: var(--error-bg);
  color: var(--error);
}

.date-time-param__clear:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

.date-time-param__clear:disabled {
  opacity: 0.28;
  cursor: default;
}

@media (max-width: 26rem) {
  .date-time-param {
    grid-template-columns: minmax(0, 1fr) minmax(5.9rem, 0.72fr) 2.35rem;
    gap: 0.35rem;
  }

  .date-time-param__control {
    padding-inline: 0.5rem;
  }

  .date-time-param__control .bi {
    display: none;
  }
}
</style>
