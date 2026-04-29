<template>
  <section class="events-panel">
    <div v-if="sortedEvents.length === 0" class="events-empty">No track events</div>
    <div v-else class="events-list">
      <div v-for="event in sortedEvents" :key="eventKey(event)" class="event-row">
        <div class="event-main">
          <span class="event-type">{{ formatType(event.eventType) }}</span>
          <span class="event-time">{{ formatTime(event.startTimestamp) }}</span>
        </div>
        <div class="event-meta">
          <span v-if="event.durationInSec != null">{{ formatDuration(event.durationInSec) }}</span>
          <span v-if="event.startDistanceInMeter != null">{{ formatDistance(event.startDistanceInMeter) }}</span>
          <span v-if="event.endDistanceInMeter != null && event.endDistanceInMeter !== event.startDistanceInMeter">
            to {{ formatDistance(event.endDistanceInMeter) }}
          </span>
        </div>
      </div>
    </div>
  </section>
</template>

<script lang="ts">
import { defineComponent, type PropType } from 'vue';
import type { GpsTrackEvent } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';

export default defineComponent({
  name: 'TrackDetailEvents',
  props: {
    events: {
      type: Array as PropType<GpsTrackEvent[]>,
      default: () => [],
    },
  },
  computed: {
    sortedEvents(): GpsTrackEvent[] {
      return [...this.events].sort((a, b) => {
        const aIndex = a.startPointIndex ?? Number.MAX_SAFE_INTEGER;
        const bIndex = b.startPointIndex ?? Number.MAX_SAFE_INTEGER;
        if (aIndex !== bIndex) return aIndex - bIndex;
        return this.toMillis(a.startTimestamp) - this.toMillis(b.startTimestamp);
      });
    },
  },
  methods: {
    toMillis(value: GpsTrackEvent['startTimestamp']): number {
      if (!value) return 0;
      if (typeof value === 'string') return new Date(value).getTime();
      return (value as Date).getTime();
    },
    formatType(value: string | undefined): string {
      if (!value) return 'Event';
      return value.toLowerCase().replaceAll('_', ' ').replace(/\b\w/g, c => c.toUpperCase());
    },
    eventKey(event: GpsTrackEvent): string | number {
      return event.id ?? `${event.startPointIndex ?? 'x'}-${this.toMillis(event.startTimestamp)}`;
    },
    formatTime(value: GpsTrackEvent['startTimestamp']): string {
      const ms = this.toMillis(value);
      if (!Number.isFinite(ms) || ms <= 0) return '';
      return new Intl.DateTimeFormat(undefined, {
        dateStyle: 'medium',
        timeStyle: 'short',
      }).format(new Date(ms));
    },
    formatDuration(seconds: number): string {
      if (!Number.isFinite(seconds)) return '';
      const rounded = Math.round(seconds);
      const mins = Math.floor(rounded / 60);
      const secs = rounded % 60;
      if (mins <= 0) return `${secs}s`;
      return `${mins}m ${secs.toString().padStart(2, '0')}s`;
    },
    formatDistance(meters: number): string {
      if (!Number.isFinite(meters)) return '';
      if (meters < 1000) return `${Math.round(meters)} m`;
      return `${(meters / 1000).toFixed(2)} km`;
    },
  },
});
</script>

<style scoped>
.events-panel {
  padding: 8px 0;
}

.events-empty {
  color: var(--text-muted);
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
  padding: 16px 8px;
}

.events-list {
  display: grid;
  gap: 1px;
  border-top: 1px solid var(--border-subtle);
  border-bottom: 1px solid var(--border-subtle);
}

.event-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
  min-height: 44px;
  padding: 6px 8px;
  background: var(--surface-glass-subtle);
}

.event-main {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.event-type {
  color: var(--text-primary);
  font-weight: 600;
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}

.event-time,
.event-meta {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}

.event-meta {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 0 6px;
  text-align: right;
}
</style>
