<template>
  <section class="position-panel" aria-label="Chart values and legend">
    <div class="position-controls">
      <header>
        <h3 tabindex="-1">
          {{ position ? `At ${positionText} ${axis === 'distance' ? 'traveled' : 'elapsed'}` : 'Attempts' }}
        </h3>
        <span v-if="position" class="position-state">{{ pinned ? 'Pinned' : 'Preview' }}</span>
      </header>
      <p class="position-help">
        <template v-if="position">{{
          axis === 'distance' ? 'Values near this distance; locations may differ. ' : 'Values near this elapsed time. '
        }}</template
        >Select a card to highlight the attempt. Select it again to clear the highlight.
      </p>
      <Teleport :to="mapTarget ?? 'body'" :disabled="!mapTarget">
        <SegmentPositionMap
          :geo-json="mapGeoJson"
          :bounds="mapBounds"
          :highlighted-track-index="highlightedTrackIndex"
          :position="position"
          :position-text="positionText"
          :axis="axis"
          :min="min"
          :max="max"
          :loading="entries.some((entry) => entry.loading)"
          :compact="!!mapTarget"
          :locator-label="locatorLabel"
          :locator-reference-color="locatorReferenceColor"
          :locator-status="locatorStatus"
          :ghosts-active="!!ghosts"
          @pin-position="emit('pin-position', $event)"
        />
      </Teleport>
      <label class="position-slider">
        <span>{{ axis === 'distance' ? 'Distance traveled' : 'Elapsed time' }}</span>
        <output>{{ positionText }}</output>
        <input
          type="range"
          aria-label="Inspect chart position"
          :min="min"
          :max="max"
          :step="(max - min) / SEGMENT_POSITION_CURSOR_STEPS || 1"
          :value="position?.x ?? min"
          :aria-valuetext="positionText"
          @input="emit('pin-position', { x: Number(($event.target as HTMLInputElement).value), axis })"
        />
      </label>
      <div class="position-actions">
        <button v-if="position" @click="emit('update:pinned', !pinned)">
          {{ pinned ? 'Follow pointer' : 'Pin position' }}
        </button>
        <button v-if="activeAttempt" @click="emit('update:activeAttempt', null)">Highlight all</button>
        <button
          v-if="position"
          :disabled="!!replayUnavailableReason"
          :aria-describedby="`${panelId}-replay-help`"
          @click="emit('open-replay')"
        >
          Open Replay here
        </button>
        <button class="back-to-charts" @click="emit('back-to-charts')">Back to charts</button>
      </div>
      <p v-if="position" :id="`${panelId}-replay-help`" class="position-help">
        {{
          replayUnavailableReason ??
          (axis === 'distance'
            ? 'Replay uses the reference’s arrival time.'
            : 'Replay opens paused at this elapsed time.')
        }}
      </p>
    </div>
    <ul class="position-attempts" role="list" aria-label="Track legend">
      <li v-for="(row, index) in rows" :key="row.id" class="position-attempt">
        <button
          class="position-card"
          type="button"
          :aria-pressed="activeAttempt === row.id"
          :aria-label="'Highlight ' + row.name + ' ' + row.visit + (row.date ? ' · ' + row.date : '')"
          :aria-describedby="position ? `${panelId}-values-${index}` : undefined"
          @click="emit('update:activeAttempt', activeAttempt === row.id ? null : row.id)"
        >
          <span class="position-identity">
            <SegmentReferenceMark v-if="row.reference" :color="row.color" />
            <span v-else class="position-color" :style="{ backgroundColor: row.color }" aria-hidden="true" />
            <span class="position-name" :title="row.name">{{ row.name }}</span>
            <ActivityTypeBadge :type="row.activityType" size="xs" />
            <small
              >{{ row.visit }}<template v-if="row.date"> · {{ row.date }}</template
              ><template v-if="row.reference"> · Reference</template></small
            >
          </span>
          <span v-if="activeAttempt === row.id" class="position-highlighted"
            ><i class="bi bi-check2" aria-hidden="true" /> Highlighted</span
          >
          <span v-if="position" :id="`${panelId}-values-${index}`" class="position-values">
            <span v-if="row.ghost" class="position-ghost">
              <span class="position-value-label"
                ><span class="position-ghost-ring" :style="{ borderColor: row.color }" aria-hidden="true" /> Ghost</span
              >
              <span class="position-value">{{ row.ghost }}</span>
            </span>
            <span v-for="value in row.values" :key="value.key">
              <span class="position-value-label">{{ value.label }}</span>
              <span class="position-value">
                <SegmentTimeDifference v-if="value.key === 'gap'" :seconds="value.raw" :reference="row.reference" />
                <template v-else>{{ value.text }}</template>
              </span>
              <small v-if="value.note">{{ value.note }}</small>
            </span>
          </span>
        </button>
      </li>
    </ul>
  </section>
</template>

<script setup lang="ts">
import { computed, useId } from 'vue';
import ActivityTypeBadge from '@/components/ui/ActivityTypeBadge.vue';
import SegmentTimeDifference from './SegmentTimeDifference.vue';
import SegmentPositionMap from './SegmentPositionMap.vue';
import SegmentReferenceMark from './SegmentReferenceMark.vue';
import type { MiniMapBounds, MiniMapGeoJson } from '@/components/map/useMiniMap';
import {
  segmentSampleAt,
  segmentTrackName,
  SEGMENT_POSITION_CURSOR_STEPS,
  type SegmentAxis,
  type SegmentChartSeries,
  type SegmentHover,
} from './segmentDisplay';
import type { AttemptData } from './useSegmentAttempts';
import { useMeasurementSystem } from '@/composables/useMeasurementSystem';
import { chartMeasurementDisplayValue, chartMeasurementUnit, type ChartMeasurementDimension } from '@/utils/chartTheme';
import { formatDurationSmart, formatNumber } from '@/utils/Utils';
import { longDistanceDisplayValue, MEASUREMENT_DISPLAY_PROFILES } from '@/utils/units';

const props = defineProps<{
  entries: AttemptData[];
  colors: string[];
  reference: string | null;
  metrics: Array<{
    key: string;
    label: string;
    axis: SegmentAxis;
    unit?: string;
    dimension?: ChartMeasurementDimension;
    decimals?: number;
  }>;
  series: Record<string, SegmentChartSeries[]>;
  position: SegmentHover | null;
  pinned: boolean;
  activeAttempt: string | null;
  axis: SegmentAxis;
  min: number;
  max: number;
  mapGeoJson: MiniMapGeoJson;
  mapBounds: MiniMapBounds | null;
  mapTarget: HTMLElement | null;
  locatorLabel: string;
  locatorReferenceColor?: string | null;
  locatorStatus: string | null;
  ghosts?: Record<string, string> | null;
  replayUnavailableReason: string | null;
}>();
const emit = defineEmits<{
  'pin-position': [position: SegmentHover];
  'update:pinned': [pinned: boolean];
  'update:activeAttempt': [id: string | null];
  'back-to-charts': [];
  'open-replay': [];
}>();
const METERS_PER_KILOMETER = 1000;
const panelId = useId();
const { measurementSystem } = useMeasurementSystem();
const highlightedTrackIndex = computed(() => {
  const index = props.entries.findIndex((entry) => entry.attempt.identity.reference === props.activeAttempt);
  return index < 0 ? null : index;
});
const positionText = computed(() =>
  props.axis === 'distance'
    ? `${formatNumber(longDistanceDisplayValue((props.position?.x ?? props.min) * METERS_PER_KILOMETER, measurementSystem.value), 2)} ${MEASUREMENT_DISPLAY_PROFILES[measurementSystem.value].longDistance}`
    : formatDurationSmart(props.position?.x ?? props.min, props.max)
);
const rows = computed(() =>
  props.entries.map((entry, index) => {
    const identity = entry.attempt.identity;
    return {
      id: identity.reference,
      name: segmentTrackName(entry.track, identity.trackId),
      activityType: entry.track?.activityType,
      color: props.colors[index],
      visit: `${identity.fromZone}${identity.fromVisit} → ${identity.toZone}${identity.toVisit}`,
      date: identity.start?.toLocaleDateString(),
      reference: identity.reference === props.reference,
      ghost: props.ghosts?.[identity.reference] ?? null,
      values: props.metrics
        .filter((metric) => metric.axis === props.axis)
        .map((metric) => {
          const series = props.series[metric.key]?.find((series) => series.id === identity.reference);
          const sample = props.position ? segmentSampleAt(series, props.position.x) : null;
          const value = sample?.value;
          const unit = metric.dimension ? chartMeasurementUnit(metric.dimension, measurementSystem.value) : metric.unit;
          return {
            key: metric.key,
            label: metric.key === 'gap' ? 'Time vs. reference' : metric.label,
            raw: value,
            text:
              value == null
                ? 'Unavailable'
                : `${formatNumber(chartMeasurementDisplayValue(value, metric.dimension, measurementSystem.value), metric.decimals ?? 1)}${unit ? ' ' + unit : ''}`,
            note: value == null ? undefined : sample?.label,
          };
        }),
    };
  })
);
</script>

<style scoped>
.position-panel {
  min-width: 0;
  border: 1px solid var(--border-subtle);
  border-radius: 10px;
  padding: 0.75rem;
  background: var(--surface-sheet-solid);
}
.position-panel button {
  font: inherit;
  padding: 0.4rem 0.55rem;
  border: 1px solid var(--border-default);
  border-radius: 0.4rem;
  color: var(--text-primary);
  background: var(--surface-sheet-solid);
  cursor: pointer;
}
.position-panel button[aria-pressed='true'] {
  border-color: var(--accent);
  background: var(--accent-bg);
}
.position-ghost-ring {
  display: inline-block;
  width: 0.6rem;
  height: 0.6rem;
  border: 2px dashed;
  border-radius: 50%;
  vertical-align: -0.05rem;
}
.position-panel button:focus-visible,
.position-panel input:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}
.position-panel button:disabled {
  opacity: 0.6;
  cursor: default;
}
header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.5rem;
}
h3 {
  margin: 0;
  font-size: var(--text-base-size);
}
.position-state,
.position-help {
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
}
.position-help {
  line-height: 1.5;
  margin: 0.4rem 0 0.7rem;
}
.position-slider {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 0.35rem;
  font-size: var(--text-sm-size);
}
.position-slider input {
  grid-column: 1/-1;
  min-width: 0;
  width: 100%;
  margin: 0;
  min-height: 32px;
}
.position-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
  margin: 0.5rem 0;
  font-size: var(--text-xs-size);
}
.position-controls {
  position: sticky;
  top: 0;
  z-index: 2;
  background: var(--surface-sheet-solid);
  padding-bottom: 0.2rem;
}
.position-attempts {
  padding: 0;
  margin: 0;
  list-style: none;
}
.position-attempt {
  margin-top: 0.5rem;
}
.position-panel .position-card {
  display: block;
  width: 100%;
  min-height: 44px;
  padding: 0.65rem;
  text-align: left;
  border-color: var(--border-subtle);
}
.position-card:hover {
  background: var(--surface-hover);
}
.position-highlighted {
  display: block;
  margin-top: 0.4rem;
  color: var(--accent-text);
  font-size: var(--text-xs-size);
}
.position-identity {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  width: 100%;
  align-items: center;
  gap: 0.3rem;
  text-align: left;
  font-size: var(--text-xs-size);
}
.position-color {
  width: 0.65rem;
  height: 0.65rem;
  border-radius: 50%;
}
.position-name {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  min-width: 0;
  overflow: hidden;
  overflow-wrap: anywhere;
}
.position-identity small {
  grid-column: 2/-1;
  font-size: inherit;
  color: var(--text-secondary);
}
.position-values {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.6rem;
  margin: 0.6rem 0.3rem 0.2rem;
}
.position-value-label,
.position-values small {
  display: block;
  font-size: var(--text-xs-size);
  color: var(--text-secondary);
}
.position-value {
  display: block;
  margin: 0.2rem 0 0;
  font-size: var(--text-sm-size);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
@container segment-workspace (min-width: 840px) {
  .back-to-charts {
    display: none;
  }
}
@media (pointer: coarse) {
  .position-actions button {
    min-height: 44px;
  }
}
</style>
