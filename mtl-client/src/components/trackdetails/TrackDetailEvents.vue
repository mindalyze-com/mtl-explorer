<template>
  <section ref="eventsPanelEl" class="events-panel">
    <div v-if="eventRows.length === 0" class="events-empty">
      <i class="bi bi-signpost" aria-hidden="true"></i> No track events
    </div>
    <template v-else>
      <div class="events-summary">{{ summaryLabel }}</div>
      <div class="events-list">
        <template v-for="row in eventRows" :key="row.key">
          <h3 v-if="row.dateLabel" class="event-day">{{ row.dateLabel }}</h3>
          <article
            :class="[
              'event-row',
              {
                'event-row--break': row.isBreakEvent,
                'event-row--gap': row.isGapEvent,
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
            <div class="event-marker" aria-hidden="true"><i :class="row.iconClass"></i></div>
            <div class="event-body">
              <div class="event-header">
                <div class="event-title-block">
                  <span class="event-type">{{ row.title }}</span>
                  <span class="event-time">{{ row.timeLabel }}</span>
                </div>
                <div v-if="row.durationLabel" class="event-duration">
                  <span class="event-duration__value">{{ row.durationLabel }}</span>
                  <div class="event-duration-scale" role="img" :aria-label="row.durationAriaLabel">
                    <span class="event-duration-scale__fill" :style="{ width: row.durationBarWidth }"></span>
                  </div>
                </div>
              </div>
              <span v-if="row.positionLabel" class="event-position">
                <i class="bi bi-signpost-split" aria-hidden="true"></i> {{ row.positionLabel }}
              </span>
              <p v-if="row.description" class="event-description">{{ row.description }}</p>
              <p v-if="row.isSelected && row.detailLabel" class="event-detail">{{ row.detailLabel }}</p>
            </div>
          </article>
        </template>
      </div>
      <p class="events-help">Select an event to locate it on the map.</p>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import type { GpsTrackEvent } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import { formatCompactDistance } from '@/utils/Utils';
import {
  formatTrackEventDate,
  formatTrackEventDuration,
  formatTrackEventTimeRange,
  isTrackEventBreak,
  isTrackEventRecordingGap,
  trackEventDurationSeconds,
  trackEventKey,
  trackEventKeysEqual,
  trackEventTimeMs,
  trackEventTitle,
} from '@/utils/trackEvents';

defineOptions({ name: 'TrackDetailEvents' });
const DISTANCE_RANGE_MIN_DELTA_M = 10;
const FULL_DURATION_BAR_PERCENT = 100;
const MILLISECONDS_PER_SECOND = 1000;
const BOUNDARY_TIME_FORMAT = new Intl.DateTimeFormat(undefined, {
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hourCycle: 'h23',
});
type TrackEventRow = {
  key: string | number;
  title: string;
  dateLabel: string;
  timeLabel: string;
  durationLabel: string;
  positionLabel: string;
  description: string;
  detailLabel: string;
  iconClass: string;
  isBreakEvent: boolean;
  isGapEvent: boolean;
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
  { events: () => [], selectedEventKey: null }
);
const emit = defineEmits<{ 'select-event': [key: string | number | null] }>();
const eventsPanelEl = ref<HTMLElement | null>(null);
const sortedEvents = computed(() =>
  [...props.events].sort((a, b) => {
    const aIndex = a.startPointIndex ?? Number.MAX_SAFE_INTEGER;
    const bIndex = b.startPointIndex ?? Number.MAX_SAFE_INTEGER;
    return aIndex - bIndex || trackEventTimeMs(a.startTimestamp) - trackEventTimeMs(b.startTimestamp);
  })
);
const maxDurationSec = computed(() =>
  sortedEvents.value.reduce((longest, event) => Math.max(longest, trackEventDurationSeconds(event)), 0)
);
const summaryLabel = computed(() => {
  const breaks = sortedEvents.value.filter(isTrackEventBreak).length;
  const gaps = sortedEvents.value.filter(isTrackEventRecordingGap).length;
  const labels = [];
  if (breaks > 0) labels.push(`${breaks} ${breaks === 1 ? 'break' : 'breaks'}`);
  if (gaps > 0) labels.push(`${gaps} ${gaps === 1 ? 'recording gap' : 'recording gaps'}`);
  return labels.join(' · ') || `${sortedEvents.value.length} ${sortedEvents.value.length === 1 ? 'event' : 'events'}`;
});
const eventRows = computed<TrackEventRow[]>(() => {
  let breakNumber = 0;
  let previousDate = '';
  return sortedEvents.value.map((event) => {
    const isBreak = isTrackEventBreak(event);
    const isGap = isTrackEventRecordingGap(event);
    if (isBreak) breakNumber += 1;
    const isSelectable =
      isBreak ||
      isGap ||
      event.startPointLongLat != null ||
      event.endPointLongLat != null ||
      event.startPointIndex != null;
    const durationSec = trackEventDurationSeconds(event);
    const durationLabel = formatTrackEventDuration(durationSec);
    const title = trackEventTitle(event, breakNumber);
    const date = formatTrackEventDate(event.startTimestamp ?? event.endTimestamp);
    const dateLabel = date !== previousDate ? date : '';
    previousDate = date;
    return {
      key: trackEventKey(event),
      title,
      dateLabel,
      timeLabel: formatTrackEventTimeRange(event),
      durationLabel,
      positionLabel: formatEventPosition(event),
      description: isGap
        ? 'No recorded positions in this interval.'
        : isBreak && event.source !== 'USER'
          ? ''
          : (event.description?.trim() ?? ''),
      detailLabel: eventDetailLabel(event),
      iconClass: isGap
        ? 'bi bi-broadcast-pin'
        : isBreak
          ? event.eventType === 'PHOTO_STOP'
            ? 'bi bi-camera-fill'
            : 'bi bi-pause-fill'
          : event.eventType === 'DATA_ISSUE'
            ? 'bi bi-exclamation-triangle'
            : 'bi bi-pin-angle',
      isBreakEvent: isBreak,
      isGapEvent: isGap,
      isSelectable,
      isSelected: isSelectable && trackEventKeysEqual(props.selectedEventKey, trackEventKey(event)),
      durationBarWidth:
        maxDurationSec.value > 0 ? `${(durationSec / maxDurationSec.value) * FULL_DURATION_BAR_PERCENT}%` : '',
      durationAriaLabel: `${title}: ${durationLabel}, relative to the longest interval (${formatTrackEventDuration(maxDurationSec.value)}).`,
    };
  });
});
watch(
  () => props.selectedEventKey,
  async (selectedKey) => {
    if (selectedKey == null) return;
    await nextTick();
    const key = String(selectedKey).replaceAll('\\', '\\\\').replaceAll('"', '\\"');
    const row = eventsPanelEl.value?.querySelector<HTMLElement>(`[data-event-key="${key}"]`);
    const viewport = eventsPanelEl.value?.closest<HTMLElement>('.p-tabpanels');
    if (!row || !viewport) return;
    const rowBounds = row.getBoundingClientRect();
    const viewportBounds = viewport.getBoundingClientRect();
    const delta =
      rowBounds.top < viewportBounds.top
        ? rowBounds.top - viewportBounds.top
        : rowBounds.bottom > viewportBounds.bottom
          ? rowBounds.bottom - viewportBounds.bottom
          : 0;
    if (delta !== 0) viewport.scrollBy({ top: delta, behavior: 'smooth' });
  }
);
function selectRow(row: TrackEventRow): void {
  if (row.isSelectable) emit('select-event', row.isSelected ? null : row.key);
}
function eventDetailLabel(event: GpsTrackEvent): string {
  if (event.detectionEvidence !== 'OBSERVED_STATIONARY') return '';
  const boundaryLabel = (label: string, boundary: GpsTrackEvent['startBoundary']): string => {
    if (!boundary) return '';
    if (boundary.status === 'TRACK_EDGE') return `${label} reaches the recorded track edge.`;
    if (boundary.status === 'RECORDING_EDGE') return `${label} reaches a recording gap.`;
    if (boundary.earliestTimeS == null || boundary.latestTimeS == null) return '';
    const from = BOUNDARY_TIME_FORMAT.format(new Date(boundary.earliestTimeS * MILLISECONDS_PER_SECOND));
    const to = BOUNDARY_TIME_FORMAT.format(new Date(boundary.latestTimeS * MILLISECONDS_PER_SECOND));
    return `${label} estimated between ${from} and ${to}.`;
  };
  return [boundaryLabel('Start', event.startBoundary), boundaryLabel('End', event.endBoundary)]
    .filter(Boolean)
    .join(' ');
}
function formatEventPosition(event: GpsTrackEvent): string {
  const start = event.startDistanceInMeter;
  const end = event.endDistanceInMeter;
  const hasStart = start != null && Number.isFinite(start);
  const hasEnd = end != null && Number.isFinite(end);
  if (hasStart && hasEnd && Math.abs(end - start) >= DISTANCE_RANGE_MIN_DELTA_M)
    return `${formatCompactDistance(start)}–${formatCompactDistance(end)}`;
  return hasStart ? formatCompactDistance(start) : hasEnd ? formatCompactDistance(end) : '';
}
</script>

<style scoped>
.events-panel {
  padding: 0.75rem;
}
.events-summary,
.events-help {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}
.events-summary {
  margin-bottom: 0.35rem;
}
.events-help {
  margin: 0.75rem 0 0;
}
.events-list {
  display: grid;
  gap: 0.42rem;
}
.event-day {
  margin: 0.4rem 0 0.15rem;
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
  font-weight: 600;
}
.event-row {
  display: grid;
  grid-template-columns: 1.9rem minmax(0, 1fr);
  gap: 0.55rem;
  align-items: start;
  padding: 0.6rem;
  border: 1px solid var(--border-default);
  border-radius: 8px;
  background: var(--surface-glass-subtle);
}
.event-row--selectable {
  cursor: pointer;
}
.event-row--selectable:hover {
  background: var(--surface-hover);
}
.event-row--selectable:focus-visible {
  outline: 2px solid var(--accent-text);
  outline-offset: 2px;
}
.event-row--selected {
  border-color: var(--accent-text);
  background: var(--accent-bg);
}
.event-marker {
  display: grid;
  place-items: center;
  width: 1.9rem;
  height: 1.9rem;
  border-radius: 8px;
  color: var(--text-secondary);
  background: var(--surface-hover);
}
.event-body {
  min-width: 0;
  display: grid;
  gap: 0.25rem;
}
.event-header {
  display: flex;
  align-items: start;
  justify-content: space-between;
  gap: 0.5rem;
}
.event-title-block {
  min-width: 0;
  display: grid;
  gap: 0.15rem;
}
.event-type {
  color: var(--text-primary);
  font-weight: 600;
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
  overflow-wrap: anywhere;
}
.event-time,
.event-position,
.event-description,
.event-detail {
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
  overflow-wrap: anywhere;
}
.event-time {
  font-variant-numeric: tabular-nums;
}
.event-duration {
  flex: 0 0 auto;
  display: grid;
  justify-items: end;
  gap: 0.35rem;
}
.event-duration__value {
  color: var(--text-primary);
  font-weight: 600;
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}
.event-duration-scale {
  width: 4.5rem;
  height: 3px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--border-default);
}
.event-duration-scale__fill {
  display: block;
  height: 100%;
  min-width: 1px;
  border-radius: inherit;
  background: var(--text-muted);
}
.event-position {
  display: flex;
  align-items: center;
  gap: 0.25rem;
}
.event-description,
.event-detail {
  margin: 0;
}
.event-detail {
  margin-top: 0.2rem;
}
.events-empty {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 0.5rem;
  padding: 2rem 1rem;
  color: var(--text-muted);
  font-size: var(--text-sm-size);
}
@container track-details (max-width: 520px) {
  .events-panel {
    padding: 0.6rem;
  }
  .event-row {
    grid-template-columns: 1.75rem minmax(0, 1fr);
    gap: 0.45rem;
    padding: 0.5rem;
  }
  .event-marker {
    width: 1.75rem;
    height: 1.75rem;
  }
}
</style>
