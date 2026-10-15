<template>
  <section class="position-map" :class="{ 'position-map--compact': compact }" aria-label="Compared attempt positions">
    <button
      class="position-map-toggle"
      type="button"
      :aria-expanded="open"
      :aria-controls="mapId"
      @click="open = !open"
    >
      <i class="bi bi-map" aria-hidden="true" />
      Position map
      <i class="bi" :class="open ? 'bi-chevron-up' : 'bi-chevron-down'" aria-hidden="true" />
    </button>
    <div v-if="open" :id="mapId" class="position-map-body">
      <MiniMap
        v-if="bounds"
        :tracks-geo-json="geoJson"
        :map-bounds="bounds"
        :highlighted-track-index="highlightedTrackIndex"
        fit-bounds-on-resize
        class="position-mini-map"
      />
      <p v-else role="status">{{ loading ? 'Loading attempt routes…' : 'Route geometry unavailable.' }}</p>
      <label v-if="compact" class="position-map-slider">
        <span>{{ axis === 'distance' ? 'Distance traveled' : 'Elapsed time' }}</span>
        <output>{{ positionText }}</output>
        <input
          type="range"
          aria-label="Inspect map position"
          :min="min"
          :max="max"
          :step="(max - min) / SEGMENT_POSITION_CURSOR_STEPS || 1"
          :value="position?.x ?? min"
          :aria-valuetext="positionText"
          @input="emit('pin-position', { x: Number(($event.target as HTMLInputElement).value), axis })"
        />
      </label>
      <p class="position-map-caption">
        {{
          axis === 'distance'
            ? 'Compare after traveling the same distance.'
            : 'Compare positions at the same elapsed time.'
        }}
      </p>
      <p v-if="axis === 'distance'" class="position-map-locator">
        <SegmentReferenceMark v-if="locatorReferenceColor" :color="locatorReferenceColor" />
        {{ locatorLabel }}<template v-if="position"> · {{ positionText }}</template>
      </p>
      <p v-if="axis === 'distance' && locatorStatus" class="position-map-status" role="status">{{ locatorStatus }}</p>
      <p v-if="bounds" class="position-map-help">
        {{
          position
            ? axis === 'distance'
              ? ghostsActive
                ? 'Dashed rings show where the others were when the reference reached this distance. Select a card to show its lead along the route.'
                : 'The locator shows one attempt. Select a card to locate another. Recording gaps have no marker.'
              : 'Colored dots show each attempt’s position. Recording gaps have no marker.'
            : 'Scrub a chart or use the position slider to inspect the map.'
        }}
      </p>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, useId } from 'vue';
import MiniMap from '@/components/map/MiniMap.vue';
import SegmentReferenceMark from './SegmentReferenceMark.vue';
import type { MiniMapBounds, MiniMapGeoJson } from '@/components/map/useMiniMap';
import { SEGMENT_POSITION_CURSOR_STEPS, type SegmentAxis, type SegmentHover } from './segmentDisplay';

const props = defineProps<{
  geoJson: MiniMapGeoJson;
  bounds: MiniMapBounds | null;
  highlightedTrackIndex: number | null;
  position: SegmentHover | null;
  positionText: string;
  axis: SegmentAxis;
  min: number;
  max: number;
  loading: boolean;
  compact: boolean;
  locatorLabel: string;
  locatorReferenceColor?: string | null;
  locatorStatus: string | null;
  ghostsActive?: boolean;
}>();
const emit = defineEmits<{ 'pin-position': [position: SegmentHover] }>();
const openOverride = ref<boolean | null>(null);
const open = computed({
  get: () => openOverride.value ?? !props.compact,
  set: (value: boolean) => {
    openOverride.value = value;
  },
});
const mapId = useId();
</script>

<style scoped>
.position-map {
  min-width: 0;
  margin-bottom: 0.65rem;
  border: 1px solid var(--border-subtle);
  border-radius: 0.5rem;
  overflow: hidden;
  background: var(--surface-sheet-solid);
}
.position-map .position-map-toggle {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  width: 100%;
  min-height: 44px;
  padding: 0.4rem 0.6rem;
  border: 0;
  border-radius: 0;
  background: var(--surface-sheet-solid);
  color: var(--text-primary);
  font: inherit;
  font-size: var(--text-sm-size);
  cursor: pointer;
}
.position-map-toggle i:last-child {
  margin-left: auto;
}
.position-map-toggle:focus-visible,
.position-map-slider input:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: -2px;
}
.position-mini-map {
  width: 100%;
  height: 220px;
}
.position-map p {
  margin: 0.4rem 0.6rem;
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
}
.position-map-slider {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 0.15rem;
  padding: 0.35rem 0.6rem 0;
  font-size: var(--text-xs-size);
}
.position-map-slider input {
  grid-column: 1 / -1;
  min-height: 32px;
  width: 100%;
  min-width: 0;
  margin: 0;
}
.position-map--compact {
  margin-bottom: 0;
  box-shadow: var(--shadow-sm);
}
.position-map--compact .position-mini-map {
  height: clamp(100px, 20dvh, 150px);
}
.position-map--compact .position-map-help {
  display: none;
}
@media (max-height: 500px) {
  .position-map--compact .position-mini-map {
    height: 80px;
  }
}
</style>
