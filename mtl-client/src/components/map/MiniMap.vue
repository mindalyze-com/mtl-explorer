<script setup lang="ts">
import { computed, inject, toRef, useTemplateRef } from 'vue';
import 'maplibre-gl/dist/maplibre-gl.css';
import { EVENT_MEASURE_BETWEEN_POINTS_DIALOG_MAXIMIZED_EVENT } from '@/utils/Utils';
import { useMiniMap, type MiniMapBounds, type MiniMapGeoJson } from '@/components/map/useMiniMap';

const props = withDefaults(
  defineProps<{
    /** GeoJSON FeatureCollection of tracks to display. */
    tracksGeoJson?: MiniMapGeoJson | null;
    /** Map bounds as [[swLng, swLat], [neLng, neLat]]. */
    mapBounds?: MiniMapBounds | null;
    /** trackIndex of the currently highlighted racer (from legend hover or map hover). */
    highlightedTrackIndex?: number | null;
  }>(),
  {
    tracksGeoJson: null,
    mapBounds: null,
    highlightedTrackIndex: null,
  }
);

const emit = defineEmits<{
  'hover-racer': [trackIndex: number];
  'leave-racer': [];
}>();

const measureEvent = inject<unknown>(EVENT_MEASURE_BETWEEN_POINTS_DIALOG_MAXIMIZED_EVENT, null);
const mapContainer = useTemplateRef<HTMLElement>('mapContainer');
const resizeSignal = computed(() => {
  if (measureEvent && typeof measureEvent === 'object' && 'value' in measureEvent) {
    return (measureEvent as { value: unknown }).value;
  }
  return measureEvent;
});

const { invalidateMapSize } = useMiniMap({
  container: mapContainer,
  tracksGeoJson: toRef(props, 'tracksGeoJson'),
  mapBounds: toRef(props, 'mapBounds'),
  highlightedTrackIndex: toRef(props, 'highlightedTrackIndex'),
  resizeSignal,
  onHoverRacer: (trackIndex) => emit('hover-racer', trackIndex),
  onLeaveRacer: () => emit('leave-racer'),
});

// VirtualRace.vue calls $refs.minimapRef.invalidateMapSize() externally.
defineExpose({ invalidateMapSize });
</script>

<template>
  <div class="container">
    <div ref="mapContainer" class="map"></div>
  </div>
</template>

<style scoped>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

.container {
  display: flex;
  height: 100%;
  width: 100%;
  background-color: black;
  align-items: stretch;
}

.map {
  flex: 1;
  height: 100%;
  width: 100%;
  --nav-sheet-h: 0px;
}

.animation-info {
  position: fixed;
  z-index: 1000;
  left: 1rem;
  bottom: 1rem;
  display: flex;
  justify-content: space-between;
  flex-flow: column;
  font-size: 1em;
  font-weight: bold;
  background-color: var(--surface-glass);
  backdrop-filter: var(--blur-standard);
  -webkit-backdrop-filter: var(--blur-standard);
  border: 1px solid var(--border-medium);
  border-radius: 5px;
  box-shadow: var(--shadow-sm);
  padding: 10px;
  color: var(--text-secondary);
}
</style>
