<template>
  <button
    ref="trigger"
    type="button"
    class="health-date-trigger"
    :aria-expanded="open"
    aria-haspopup="dialog"
    aria-label="Select date range"
    :title="label"
    @click="show"
  >
    <i class="bi bi-calendar3" aria-hidden="true" /><span>{{ label }}</span
    ><i class="bi bi-chevron-down" aria-hidden="true" />
  </button>
  <component
    :is="mobile ? Drawer : Popover"
    ref="overlay"
    v-model:visible="open"
    position="bottom"
    :class="['health-date-overlay', { 'health-date-overlay--custom': custom, 'health-date-overlay--mobile': mobile }]"
    :show-close-icon="false"
    :block-scroll="mobile"
    :aria-label="custom ? 'Custom range' : 'Date range'"
    @hide="onHide"
    @keydown.esc.stop.prevent="close"
  >
    <div class="health-date-panel">
      <div v-if="mobile" class="health-date-handle" aria-hidden="true" />
      <header class="health-date-heading">
        <h3 ref="heading" tabindex="-1">{{ custom ? 'Custom range' : 'Date range' }}</h3>
        <Button
          v-if="mobile"
          icon="pi pi-times"
          aria-label="Close date range"
          autofocus
          rounded
          text
          severity="secondary"
          @click="close"
        />
      </header>
      <template v-if="!custom">
        <div class="health-date-shortcuts" role="group" aria-label="Date shortcuts">
          <button
            v-for="shortcut in HEALTH_DATE_SHORTCUTS"
            :key="shortcut.id"
            type="button"
            :aria-pressed="preset === shortcut.id"
            :autofocus="!mobile && (preset === shortcut.id || (preset === 'CUSTOM' && shortcut.id === '7D'))"
            @click="selectPreset(shortcut.id)"
          >
            <span
              >{{ shortcut.label }}<small v-if="mobile && preset === shortcut.id">{{ label }}</small></span
            >
            <i v-if="preset === shortcut.id" class="pi pi-check" aria-hidden="true" />
          </button>
        </div>
        <button type="button" class="health-date-custom-link" @click="showCustom">
          <i class="bi bi-calendar3" aria-hidden="true" /><span>Custom range…</span
          ><i class="pi pi-angle-right" aria-hidden="true" />
        </button>
        <p v-if="!mobile" class="health-date-current">{{ label }}</p>
      </template>
      <form v-else class="health-date-custom" aria-label="Custom health chart period" @submit.prevent="apply">
        <div class="health-date-inputs">
          <label for="health-custom-start"
            >Start date<InputText id="health-custom-start" v-model="start" type="date" required :max="end || today"
          /></label>
          <label for="health-custom-end"
            >End date<InputText
              id="health-custom-end"
              v-model="end"
              type="date"
              required
              :min="start || undefined"
              :max="today"
          /></label>
        </div>
        <DatePicker
          v-model="calendarRange"
          inline
          selection-mode="range"
          :number-of-months="mobile ? 1 : 2"
          :max-date="localDateBoundary(today, false)"
          :show-other-months="true"
          class="health-range-calendar"
        />
        <p v-if="error" class="health-date-error" role="alert">{{ error }}</p>
        <footer class="health-date-footer">
          <Button label="Back to shortcuts" text size="small" class="health-date-back" @click="showShortcuts" />
          <div class="health-date-actions">
            <Button label="Cancel" severity="secondary" outlined size="small" @click="close" />
            <Button label="Apply" type="submit" size="small" :disabled="!valid" />
          </div>
        </footer>
      </form>
    </div>
  </component>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import Button from 'primevue/button';
import DatePicker from 'primevue/datepicker';
import Drawer from 'primevue/drawer';
import InputText from 'primevue/inputtext';
import Popover from 'primevue/popover';
import { useMediaQuery } from '@/composables/useMediaQuery';
import { localDateBoundary } from '../utils/dateBoundary';
import { formatCalendarDate, HEALTH_DATE_SHORTCUTS, type HealthPreset } from '../utils/healthCalendarRange';

const props = defineProps<{ startDate: string; endDate: string; preset: HealthPreset | 'CUSTOM'; label: string }>();
const emit = defineEmits<{ preset: [value: HealthPreset]; custom: [value: { start: string; end: string }] }>();
const mobile = useMediaQuery('(max-width: 700px)');
const overlay = ref<InstanceType<typeof Popover> | null>(null);
const trigger = ref<HTMLButtonElement | null>(null);
const heading = ref<HTMLElement | null>(null);
const open = ref(false);
const custom = ref(false);
const start = ref('');
const end = ref('');
const today = formatCalendarDate(new Date());
const error = computed(() => {
  if (!start.value || !end.value) return '';
  if (start.value > end.value) return 'Start date must be on or before end date.';
  if (end.value > today) return 'End date cannot be in the future.';
  return '';
});
const valid = computed(() => Boolean(start.value && end.value) && !error.value);
const calendarRange = computed({
  get: () =>
    start.value ? [localDateBoundary(start.value, false) ?? null, localDateBoundary(end.value, false) ?? null] : [],
  set: (value: (Date | null)[] | Date | null | undefined) => {
    if (!Array.isArray(value)) return;
    start.value = value[0] ? formatCalendarDate(value[0]) : '';
    end.value = value[1] ? formatCalendarDate(value[1]) : '';
  },
});
function show(event: MouseEvent) {
  if (open.value) return close();
  custom.value = false;
  start.value = props.startDate;
  end.value = props.endDate;
  open.value = true;
  if (!mobile.value) overlay.value?.show(event);
}
function close() {
  if (!mobile.value) overlay.value?.hide();
  open.value = false;
  trigger.value?.focus({ preventScroll: true });
}
function onHide() {
  // Drawer already updates v-model; its leave event may arrive after reopening.
  if (!mobile.value) open.value = false;
}
async function resizeOverlay() {
  await nextTick();
  if (!mobile.value) overlay.value?.alignOverlay();
  heading.value?.focus({ preventScroll: true });
}
function showCustom() {
  custom.value = true;
  void resizeOverlay();
}
function showShortcuts() {
  custom.value = false;
  void resizeOverlay();
}
function selectPreset(value: HealthPreset) {
  emit('preset', value);
  close();
}
function apply() {
  if (!valid.value) return;
  emit('custom', { start: start.value, end: end.value });
  close();
}
watch(mobile, () => {
  open.value = false;
});
</script>

<style>
.health-date-trigger {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.65rem;
  min-height: 2.5rem;
  padding: 0.45rem 0.75rem;
  border: 1px solid var(--border-default);
  border-radius: 8px;
  background: var(--surface-sheet-solid);
  color: var(--text-primary);
  font: inherit;
  font-size: var(--text-xs-size);
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
}
.health-date-trigger:hover {
  background: var(--surface-hover);
}
.health-date-trigger:focus-visible,
.health-date-panel button:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}
.health-date-overlay {
  --p-popover-content-padding: 0;
  color: var(--text-primary);
}
.health-date-overlay .p-popover-content {
  padding: 0;
}
.health-date-panel {
  width: 21rem;
  max-width: calc(100vw - 1.5rem);
  font-size: var(--text-sm-size);
}
.health-date-overlay--custom .health-date-panel {
  width: 40rem;
}
.health-date-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 1rem 0.65rem;
}
.health-date-heading h3 {
  margin: 0;
  font-size: var(--text-base-size);
  line-height: var(--text-base-lh);
}
.health-date-shortcuts {
  display: grid;
  grid-template-rows: repeat(4, auto);
  grid-auto-flow: column;
  grid-template-columns: 1fr 1fr;
  padding: 0 0.5rem 0.65rem;
  gap: 0.15rem;
}
.health-date-shortcuts button,
.health-date-custom-link {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  border: 0;
  background: transparent;
  color: var(--text-secondary);
  font: inherit;
  font-size: var(--text-sm-size);
  text-align: left;
  cursor: pointer;
}
.health-date-shortcuts button {
  min-height: 2.5rem;
  padding: 0.5rem;
  border-radius: 6px;
}
.health-date-shortcuts button[aria-pressed='true'] {
  color: var(--accent-text);
  background: var(--accent-bg);
}
.health-date-shortcuts button:hover,
.health-date-custom-link:hover {
  background: var(--surface-hover);
}
.health-date-shortcuts small {
  display: block;
  margin-top: 0.3rem;
  font-size: var(--text-xs-size);
}
.health-date-custom-link {
  width: 100%;
  padding: 0.85rem 1rem;
  border-block: 1px solid var(--border-default);
}
.health-date-custom-link span {
  flex: 1;
}
.health-date-current {
  margin: 0;
  padding: 0.8rem 1rem;
  color: var(--text-muted);
  font-size: var(--text-xs-size);
}
.health-date-custom {
  padding: 0.5rem 1rem 1rem;
}
.health-date-inputs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
  margin-bottom: 0.6rem;
}
.health-date-inputs label {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  min-width: 0;
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
}
.health-date-inputs input {
  width: 100%;
  min-width: 0;
  min-height: 2.5rem;
  font-size: var(--text-sm-size);
  color-scheme: light dark;
}
.health-range-calendar {
  display: block !important;
  width: 100%;
  z-index: auto !important;
}
.health-range-calendar .p-datepicker-panel {
  width: 100%;
  border: 0;
  padding: 0;
  background: transparent;
}
.health-range-calendar .p-datepicker-calendar-container {
  gap: 1rem;
}
.health-range-calendar .p-datepicker-calendar {
  flex: 1;
  min-width: 0;
}
.health-range-calendar .p-datepicker-day-view {
  width: 100%;
  font-size: var(--text-xs-size);
}
.health-range-calendar .p-datepicker-day-cell {
  padding: 0.15rem 0;
}
.health-range-calendar .p-datepicker-day {
  width: 100%;
  border-radius: 0;
  height: 2rem;
}
.health-range-calendar td:first-child .p-datepicker-day {
  border-radius: 1rem 0 0 1rem;
}
.health-range-calendar td:last-child .p-datepicker-day {
  border-radius: 0 1rem 1rem 0;
}
.health-range-calendar .p-datepicker-day-selected {
  border-radius: 1rem !important;
}
.health-date-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  margin-top: 0.8rem;
}
.health-date-back {
  padding-left: 0 !important;
}
.health-date-actions {
  display: flex;
  gap: 0.5rem;
}
.health-date-error {
  color: var(--error);
  font-size: var(--text-xs-size);
}
.health-date-overlay.health-date-overlay--mobile.p-drawer {
  height: auto;
  max-height: calc(100dvh - 1rem);
  border-radius: 20px 20px 0 0;
  background: var(--surface-sheet-solid);
  color: var(--text-primary);
}
.health-date-overlay--mobile .p-drawer-header {
  display: none;
}
.health-date-overlay--mobile .p-drawer-content {
  padding: 0 0.75rem max(0.75rem, env(safe-area-inset-bottom));
}
.health-date-overlay--mobile .health-date-panel {
  width: 100%;
  max-width: none;
}
.health-date-handle {
  width: 2.5rem;
  height: 4px;
  border-radius: 2px;
  background: var(--border-medium);
  margin: 0.6rem auto 0;
}
.health-date-overlay--mobile .health-date-heading {
  padding: 0.5rem 0.25rem;
}
.health-date-overlay--mobile .health-date-shortcuts {
  display: flex;
  flex-direction: column;
  padding: 0;
  gap: 0;
  border: 1px solid var(--border-default);
  border-radius: 10px 10px 0 0;
  overflow: hidden;
}
.health-date-overlay--mobile .health-date-shortcuts button {
  min-height: 2.9rem;
  padding: 0.65rem 0.85rem;
  border-radius: 0;
  border-bottom: 1px solid var(--border-default);
}
.health-date-overlay--mobile .health-date-shortcuts button:last-child {
  border-bottom: 0;
}
.health-date-overlay--mobile .health-date-custom-link {
  min-height: 3rem;
  border: 1px solid var(--border-default);
  border-top: 0;
  border-radius: 0 0 10px 10px;
}
.health-date-overlay--mobile .health-date-custom {
  padding: 0.5rem 0.25rem 0;
}
.health-date-overlay--mobile .health-date-inputs {
  gap: 0.65rem;
}
.health-date-overlay--mobile .health-date-footer {
  flex-wrap: wrap;
}
.health-date-overlay--mobile .health-date-back {
  width: 100%;
  justify-content: flex-start;
}
.health-date-overlay--mobile .health-date-actions {
  width: 100%;
  padding-top: 0.65rem;
  border-top: 1px solid var(--border-default);
}
.health-date-overlay--mobile .health-date-actions button {
  flex: 1;
  min-height: 2.75rem;
}
.health-date-overlay--mobile .p-datepicker-day {
  height: 2.35rem;
}
</style>
