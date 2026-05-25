<template>
  <section ref="eventsPanelEl" class="events-panel">
    <div v-if="eventRows.length === 0" class="events-empty">No track events</div>
    <template v-else>
      <div v-if="breakCount > 0" class="break-summary">
        <span>{{ breakCount }} {{ breakCount === 1 ? 'break' : 'breaks' }}</span>
        <span v-if="longestBreakLabel">Longest {{ longestBreakLabel }}</span>
      </div>

      <div class="events-list">
        <article
          v-for="row in eventRows"
          :key="row.key"
          :class="[
            'event-row',
            {
              'event-row--break': row.isBreakEvent,
              'event-row--longest': row.isLongestBreak,
              'event-row--selected': row.isSelected,
              'event-row--selectable': row.isSelectable,
            },
          ]"
          :tabindex="row.isSelectable ? 0 : undefined"
          :role="row.isSelectable ? 'button' : undefined"
          :aria-pressed="row.isSelectable ? row.isSelected : undefined"
          :data-event-key="String(row.key)"
          @click="selectRow(row)"
          @keydown.enter.prevent="selectRow(row)"
          @keydown.space.prevent="selectRow(row)"
        >
          <div class="event-marker" aria-hidden="true">
            <i :class="row.iconClass"></i>
          </div>

          <div class="event-body">
            <div class="event-header">
              <div class="event-title-block">
                <span class="event-type">{{ row.title }}</span>
                <span class="event-time">{{ row.timeLabel }}</span>
              </div>
              <span v-if="row.badgeLabel" class="event-badge">{{ row.badgeLabel }}</span>
            </div>

            <template v-if="row.isBreakEvent">
              <div class="break-metrics">
                <div v-if="row.durationLabel" class="break-metric">
                  <span class="break-metric__label"><i class="bi bi-stopwatch"></i> Duration</span>
                  <strong class="break-metric__value break-metric__value--duration">{{ row.durationLabel }}</strong>
                </div>
                <div v-if="row.positionLabel" class="break-metric">
                  <span class="break-metric__label"><i class="bi bi-signpost-split"></i> Position</span>
                  <strong class="break-metric__value">{{ row.positionLabel }}</strong>
                </div>
              </div>

              <div v-if="row.durationBarWidth" class="break-scale" role="img" :aria-label="row.durationAriaLabel">
                <span class="break-scale__fill" :style="{ width: row.durationBarWidth }"></span>
              </div>
            </template>

            <div v-else class="event-meta">
              <span v-if="row.durationLabel"><i class="bi bi-stopwatch"></i>{{ row.durationLabel }}</span>
              <span v-if="row.positionLabel"><i class="bi bi-signpost-split"></i>{{ row.positionLabel }}</span>
            </div>

            <p v-if="row.description" class="event-description">{{ row.description }}</p>
          </div>
        </article>
      </div>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import {
  GpsTrackEventEventTypeEnum,
  GpsTrackEventSourceEnum,
  type GpsTrackEvent,
} from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';

defineOptions({
  name: 'TrackDetailEvents',
});

const METERS_PER_KILOMETER = 1000;
const SECONDS_PER_MINUTE = 60;
const SECONDS_PER_HOUR = 60 * SECONDS_PER_MINUTE;
const MILLISECONDS_PER_SECOND = 1000;
const DISTANCE_RANGE_MIN_DELTA_M = 10;
const MIN_BREAK_BAR_PERCENT = 8;
const FULL_BREAK_BAR_PERCENT = 100;
const PERCENT_MULTIPLIER = 100;
const BREAK_DURATION_EQUALITY_EPSILON_SEC = 0.5;
const GPS_GAP_BREAK_DESCRIPTION_PREFIX = 'Low-displacement GPS recording gap';
const GPS_GAP_BREAK_BADGE_LABEL = 'GPS gap';

const BREAK_EVENT_TYPES = new Set<string>([GpsTrackEventEventTypeEnum.Stop, GpsTrackEventEventTypeEnum.PhotoStop]);

type TrackEventRow = {
  key: string | number;
  title: string;
  description: string;
  timeLabel: string;
  durationLabel: string;
  positionLabel: string;
  badgeLabel: string;
  iconClass: string;
  isBreakEvent: boolean;
  isLongestBreak: boolean;
  isSelectable: boolean;
  isSelected: boolean;
  durationBarWidth: string;
  durationAriaLabel: string;
};

const props = withDefaults(
  defineProps<{
    events?: GpsTrackEvent[];
    selectedEventKey?: string | number | null;
  }>(),
  {
    events: () => [],
    selectedEventKey: null,
  }
);

const emit = defineEmits<{
  'select-event': [key: string | number | null];
}>();

const eventsPanelEl = ref<HTMLElement | null>(null);

const sortedEvents = computed<GpsTrackEvent[]>(() => {
  return [...props.events].sort((a, b) => {
    const aIndex = a.startPointIndex ?? Number.MAX_SAFE_INTEGER;
    const bIndex = b.startPointIndex ?? Number.MAX_SAFE_INTEGER;
    if (aIndex !== bIndex) return aIndex - bIndex;
    return toMillis(a.startTimestamp) - toMillis(b.startTimestamp);
  });
});

const maxBreakDurationSec = computed(() => {
  return sortedEvents.value.reduce((maxDuration, event) => {
    if (!isBreakEvent(event)) return maxDuration;
    return Math.max(maxDuration, normalizedDuration(event.durationInSec));
  }, 0);
});

const eventRows = computed<TrackEventRow[]>(() => {
  let breakNumber = 0;
  const totalBreakCount = sortedEvents.value.filter(isBreakEvent).length;

  return sortedEvents.value.map((event) => {
    const isBreak = isBreakEvent(event);
    if (isBreak) breakNumber += 1;

    const durationSec = normalizedDuration(event.durationInSec);
    const durationBarPercent = breakDurationPercent(durationSec, maxBreakDurationSec.value);
    const durationLabel = durationSec > 0 ? formatDuration(durationSec) : '';
    const title = eventTitle(event, breakNumber);

    return {
      key: eventKey(event),
      title,
      description: eventDescription(event),
      timeLabel: isBreak ? formatBreakTimeRange(event) : formatTime(event.startTimestamp),
      durationLabel,
      positionLabel: formatEventPosition(event),
      badgeLabel: eventBadgeLabel(event, durationSec, totalBreakCount),
      iconClass: eventIconClass(event),
      isBreakEvent: isBreak,
      isLongestBreak: isBreak && isLongestBreak(durationSec),
      isSelectable: isBreak,
      isSelected: isBreak && eventKeysEqual(props.selectedEventKey, eventKey(event)),
      durationBarWidth: durationBarPercent > 0 ? `${durationBarPercent}%` : '',
      durationAriaLabel: durationLabel ? `${title} duration ${durationLabel}` : title,
    };
  });
});

const breakCount = computed(() => eventRows.value.filter((row) => row.isBreakEvent).length);

const longestBreakLabel = computed(() => {
  return maxBreakDurationSec.value > 0 ? formatDuration(maxBreakDurationSec.value) : '';
});

watch(
  () => props.selectedEventKey,
  async (selectedKey) => {
    if (selectedKey == null) return;
    await nextTick();
    const key = String(selectedKey).replaceAll('\\', '\\\\').replaceAll('"', '\\"');
    const selectedRow = eventsPanelEl.value?.querySelector<HTMLElement>(`[data-event-key="${key}"]`);
    selectedRow?.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
  }
);

function toMillis(value: GpsTrackEvent['startTimestamp']): number {
  if (!value) return 0;
  if (typeof value === 'string') return new Date(value).getTime();
  return value.getTime();
}

function isBreakEvent(event: GpsTrackEvent): boolean {
  return event.eventType ? BREAK_EVENT_TYPES.has(event.eventType) : false;
}

function isRecordingGapBreak(event: GpsTrackEvent): boolean {
  return isBreakEvent(event) && event.description?.trim().startsWith(GPS_GAP_BREAK_DESCRIPTION_PREFIX) === true;
}

function normalizedDuration(seconds: number | undefined): number {
  if (seconds == null || !Number.isFinite(seconds)) return 0;
  return Math.max(0, seconds);
}

function isLongestBreak(durationSec: number): boolean {
  return (
    maxBreakDurationSec.value > 0 &&
    Math.abs(durationSec - maxBreakDurationSec.value) <= BREAK_DURATION_EQUALITY_EPSILON_SEC
  );
}

function breakDurationPercent(durationSec: number, maxDurationSec: number): number {
  if (durationSec <= 0 || maxDurationSec <= 0) return 0;
  const rawPercent = (durationSec / maxDurationSec) * PERCENT_MULTIPLIER;
  const visiblePercent = Math.max(MIN_BREAK_BAR_PERCENT, rawPercent);
  return Math.min(FULL_BREAK_BAR_PERCENT, Math.round(visiblePercent));
}

function eventBadgeLabel(event: GpsTrackEvent, durationSec: number, totalBreakCount: number): string {
  const labels: string[] = [];
  if (isRecordingGapBreak(event)) labels.push(GPS_GAP_BREAK_BADGE_LABEL);
  if (isBreakEvent(event) && totalBreakCount > 1 && isLongestBreak(durationSec)) labels.push('Longest');
  return labels.join(' · ');
}

function eventKey(event: GpsTrackEvent): string | number {
  return event.id ?? `${event.startPointIndex ?? 'x'}-${toMillis(event.startTimestamp)}`;
}

function eventKeysEqual(a: string | number | null | undefined, b: string | number | null | undefined): boolean {
  return a != null && b != null && String(a) === String(b);
}

function selectRow(row: TrackEventRow): void {
  if (!row.isSelectable) return;
  emit('select-event', row.isSelected ? null : row.key);
}

function eventTitle(event: GpsTrackEvent, breakNumber: number): string {
  const customLabel = event.label?.trim();

  if (isBreakEvent(event) && event.source === GpsTrackEventSourceEnum.User && customLabel) {
    return customLabel;
  }

  if (event.eventType === GpsTrackEventEventTypeEnum.PhotoStop) {
    return `Photo break ${breakNumber}`;
  }

  if (isBreakEvent(event)) {
    return `Break ${breakNumber}`;
  }

  if (customLabel) return customLabel;

  return formatType(event.eventType);
}

function eventDescription(event: GpsTrackEvent): string {
  if (isBreakEvent(event) && event.source !== GpsTrackEventSourceEnum.User && !isRecordingGapBreak(event)) {
    return '';
  }

  return event.description?.trim() ?? '';
}

function eventIconClass(event: GpsTrackEvent): string {
  if (isBreakEvent(event)) return 'bi bi-pause-fill';
  if (event.eventType === GpsTrackEventEventTypeEnum.GpsGap) return 'bi bi-broadcast-pin';
  if (event.eventType === GpsTrackEventEventTypeEnum.DataIssue) return 'bi bi-exclamation-triangle';
  if (event.eventType === GpsTrackEventEventTypeEnum.ManualNote) return 'bi bi-pin-angle';
  return 'bi bi-record-fill';
}

function formatType(value: string | undefined): string {
  if (!value) return 'Event';
  return value
    .toLowerCase()
    .replaceAll('_', ' ')
    .replace(/\b\w/g, (c) => c.toUpperCase());
}

function formatTime(value: GpsTrackEvent['startTimestamp']): string {
  const ms = toMillis(value);
  if (!Number.isFinite(ms) || ms <= 0) return '';
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(ms));
}

function formatClockTime(value: GpsTrackEvent['startTimestamp']): string {
  const ms = toMillis(value);
  if (!Number.isFinite(ms) || ms <= 0) return '';
  return new Intl.DateTimeFormat(undefined, {
    timeStyle: 'short',
  }).format(new Date(ms));
}

function formatBreakTimeRange(event: GpsTrackEvent): string {
  const startMs = toMillis(event.startTimestamp);
  if (!Number.isFinite(startMs) || startMs <= 0) return '';

  const startLabel = formatTime(event.startTimestamp);
  const endMs = breakEndMillis(event, startMs);
  if (!Number.isFinite(endMs) || endMs <= startMs) return startLabel;

  const endLabel = isSameLocalDate(startMs, endMs) ? formatClockTime(new Date(endMs)) : formatTime(new Date(endMs));
  return `${startLabel} - ${endLabel}`;
}

function breakEndMillis(event: GpsTrackEvent, startMs: number): number {
  const endMs = toMillis(event.endTimestamp);
  if (Number.isFinite(endMs) && endMs > 0) return endMs;

  const durationSec = normalizedDuration(event.durationInSec);
  if (durationSec <= 0) return 0;
  return startMs + durationSec * MILLISECONDS_PER_SECOND;
}

function isSameLocalDate(aMs: number, bMs: number): boolean {
  const a = new Date(aMs);
  const b = new Date(bMs);
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
}

function formatDuration(seconds: number): string {
  if (!Number.isFinite(seconds)) return '';

  const rounded = Math.round(seconds);
  const hours = Math.floor(rounded / SECONDS_PER_HOUR);
  const mins = Math.floor((rounded % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE);
  const secs = rounded % SECONDS_PER_MINUTE;

  if (hours > 0) {
    return `${hours}h ${mins.toString().padStart(2, '0')}m`;
  }

  if (mins <= 0) return `${secs}s`;
  return `${mins}m ${secs.toString().padStart(2, '0')}s`;
}

function formatEventPosition(event: GpsTrackEvent): string {
  const start = event.startDistanceInMeter;
  const end = event.endDistanceInMeter;
  const hasStart = start != null && Number.isFinite(start);
  const hasEnd = end != null && Number.isFinite(end);

  if (hasStart && hasEnd && Math.abs(end - start) >= DISTANCE_RANGE_MIN_DELTA_M) {
    return `${formatDistance(start)} - ${formatDistance(end)}`;
  }

  if (hasStart) return formatDistance(start);
  if (hasEnd) return formatDistance(end);
  return '';
}

function formatDistance(meters: number): string {
  if (!Number.isFinite(meters)) return '';
  if (meters < METERS_PER_KILOMETER) return `${Math.round(meters)} m`;
  return `${(meters / METERS_PER_KILOMETER).toFixed(2)} km`;
}
</script>

<style scoped>
.events-panel {
  --break-accent: #f97316;
  --break-accent-strong: #ea580c;
  --break-accent-soft: rgba(249, 115, 22, 0.13);
  --break-accent-line: rgba(249, 115, 22, 0.36);
  --break-scale-track: rgba(15, 23, 42, 0.08);

  padding: 0.75rem;
}

.events-empty {
  color: var(--text-muted);
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
  padding: 1rem 0.5rem;
}

.break-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  margin-bottom: 0.6rem;
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
  font-weight: 700;
  line-height: var(--text-xs-lh);
  letter-spacing: 0;
  text-transform: uppercase;
}

.events-list {
  display: grid;
  gap: 0.42rem;
}

.event-row {
  display: grid;
  grid-template-columns: 1.9rem minmax(0, 1fr);
  gap: 0.55rem;
  align-items: flex-start;
  padding: 0.5rem 0.6rem;
  border: 1px solid var(--border-default);
  border-radius: 8px;
  background: var(--surface-glass-subtle);
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.45) inset;
}

.event-row--break {
  border-color: var(--break-accent-line);
}

.event-row--longest {
  border-color: rgba(234, 88, 12, 0.55);
}

.event-row--selectable {
  cursor: pointer;
}

.event-row--selectable:hover {
  border-color: var(--break-accent-line);
  background: var(--surface-hover);
}

.event-row--selectable:focus-visible {
  outline: 2px solid var(--break-accent);
  outline-offset: 2px;
}

.event-row--selected {
  border-color: var(--break-accent);
  box-shadow:
    0 0 0 1px rgba(249, 115, 22, 0.16),
    0 1px 0 rgba(255, 255, 255, 0.45) inset;
}

.event-marker {
  display: grid;
  place-items: center;
  width: 1.9rem;
  height: 1.9rem;
  border-radius: 8px;
  color: var(--accent-text);
  background: var(--accent-bg);
}

.event-row--break .event-marker {
  color: #fff;
  background: linear-gradient(135deg, var(--break-accent), var(--break-accent-strong));
  box-shadow: 0 6px 16px rgba(249, 115, 22, 0.24);
}

.event-body {
  min-width: 0;
  display: grid;
  gap: 0.35rem;
}

.event-header {
  min-width: 0;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.65rem;
}

.event-title-block {
  min-width: 0;
  display: flex;
  align-items: baseline;
  gap: 0.45rem;
  flex-wrap: wrap;
}

.event-type {
  min-width: 0;
  color: var(--text-primary);
  font-weight: 750;
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
  overflow-wrap: anywhere;
}

.event-time {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}

.event-badge {
  flex: 0 0 auto;
  padding: 0.12rem 0.38rem;
  border-radius: 6px;
  color: var(--break-accent-strong);
  background: rgba(249, 115, 22, 0.12);
  font-size: var(--text-2xs-size);
  font-weight: 800;
  line-height: var(--text-xs-lh);
  letter-spacing: 0;
  text-transform: uppercase;
}

.break-metrics {
  display: flex;
  flex-wrap: wrap;
  gap: 0.3rem 0.65rem;
  align-items: baseline;
}

.break-metric {
  min-width: 0;
  display: inline-flex;
  align-items: baseline;
  gap: 0.32rem;
}

.break-metric__label {
  display: inline-flex;
  align-items: center;
  gap: 0.22rem;
  color: var(--text-faint);
  font-size: var(--text-2xs-size);
  font-weight: 750;
  line-height: var(--text-2xs-lh);
  letter-spacing: 0;
  text-transform: uppercase;
}

.break-metric__value {
  min-width: 0;
  color: var(--text-primary);
  font-size: var(--text-xs-size);
  font-weight: 750;
  line-height: var(--text-xs-lh);
  overflow-wrap: anywhere;
}

.break-metric__value--duration {
  color: var(--break-accent-strong);
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}

.break-scale {
  position: relative;
  height: 0.34rem;
  overflow: hidden;
  border-radius: 999px;
  background: linear-gradient(90deg, rgba(249, 115, 22, 0.1), rgba(99, 102, 241, 0.08)), var(--break-scale-track);
}

.break-scale__fill {
  display: block;
  height: 100%;
  min-width: 0.45rem;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--break-accent), #f59e0b);
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.18) inset;
}

.event-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem 0.7rem;
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}

.event-meta span {
  display: inline-flex;
  align-items: center;
  gap: 0.28rem;
  min-width: 0;
}

.event-description {
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
  line-height: var(--text-sm-lh);
  overflow-wrap: anywhere;
}

:global([data-theme='dark']) .events-panel {
  --break-accent: #fb923c;
  --break-accent-strong: #fdba74;
  --break-accent-soft: rgba(251, 146, 60, 0.16);
  --break-accent-line: rgba(251, 146, 60, 0.32);
  --break-scale-track: rgba(255, 255, 255, 0.1);
}

:global([data-theme='dark']) .event-row {
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.05) inset;
}

:global([data-theme='dark']) .event-badge {
  color: #fed7aa;
  background: rgba(251, 146, 60, 0.16);
}

@media (max-width: 520px) {
  .events-panel {
    padding: 0.6rem;
  }

  .break-summary {
    align-items: flex-start;
    flex-direction: column;
    gap: 0.15rem;
  }

  .event-row {
    grid-template-columns: 1.75rem minmax(0, 1fr);
    gap: 0.48rem;
    padding: 0.5rem;
  }

  .event-marker {
    width: 1.75rem;
    height: 1.75rem;
  }
}
</style>
