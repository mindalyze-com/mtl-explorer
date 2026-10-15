<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue';
import type { TrackObservation, TrackQualityReport } from 'x8ing-mtl-api-typescript-fetch';
import { PROCESSED_TRACK_COLOR } from './colors';
import MiniMap from '@/components/map/MiniMap.vue';
import { useVerticalResizeDrag } from '@/composables/useVerticalResizeDrag';
import type { MiniMapBounds, MiniMapGeoJson, MiniMapFocus } from '@/components/map/useMiniMap';
import { reasonLabel } from './api';
import {
  FINDING_MAP_SPAN_METRES,
  belongsToFindingSection,
  findingPointRange,
  hasMapPosition,
  type FindingMapFocus,
} from './findingLocation';
const props = defineProps<{
  points: TrackObservation[];
  color: string;
  label: string;
  processed?: TrackQualityReport[];
  focus?: FindingMapFocus | null;
}>();
const emit = defineEmits<{ reset: []; center: [pointIndex: number] }>();
const figure = ref<HTMLElement | null>(null);
const miniMap = ref<InstanceType<typeof MiniMap> | null>(null);
const canvas = ref<HTMLElement | null>(null);
const resizeHandle = ref<HTMLElement | null>(null);
const canvasId = useId();
const MAP_HEIGHT_MIN = 120;
const MAP_HEIGHT_MAX = 600;
const MAP_HEIGHT_DEFAULT = 280;
const MAP_RESIZE_STEP = 20;
const MAP_RESIZE_THRESHOLD = 4;
const mapHeight = ref<number | null>(null);
const automaticHeight = ref(MAP_HEIGHT_DEFAULT);
const currentHeight = computed(() => mapHeight.value ?? automaticHeight.value);
let resizeStartHeight = MAP_HEIGHT_DEFAULT;
let heightObserver: ResizeObserver | undefined;

function measuredHeight() {
  return canvas.value?.getBoundingClientRect().height || currentHeight.value;
}

function setMapHeight(height: number) {
  mapHeight.value = Math.max(MAP_HEIGHT_MIN, Math.min(MAP_HEIGHT_MAX, height));
  void nextTick(() => miniMap.value?.invalidateMapSize());
}
function cycleMapHeight() {
  const presets = [MAP_HEIGHT_MIN, MAP_HEIGHT_DEFAULT, MAP_HEIGHT_MAX];
  setMapHeight(presets.find((height) => height > measuredHeight()) ?? MAP_HEIGHT_MIN);
}
function onResizeClick(event: MouseEvent) {
  if (!consumeClickAfterDrag(event)) cycleMapHeight();
}
function onResizeKeydown(event: KeyboardEvent) {
  switch (event.key) {
    case 'ArrowUp':
      setMapHeight(measuredHeight() - MAP_RESIZE_STEP);
      break;
    case 'ArrowDown':
      setMapHeight(measuredHeight() + MAP_RESIZE_STEP);
      break;
    case 'Home':
      setMapHeight(MAP_HEIGHT_MIN);
      break;
    case 'End':
      setMapHeight(MAP_HEIGHT_MAX);
      break;
    case 'Enter':
    case ' ':
      cycleMapHeight();
      break;
    default:
      return;
  }
  event.preventDefault();
}
const { consumeClickAfterDrag } = useVerticalResizeDrag(
  resizeHandle,
  {
    onStart: () => {
      resizeStartHeight = measuredHeight();
    },
    onResize: (deltaY) => setMapHeight(resizeStartHeight + deltaY),
    onEnd: () => void nextTick(() => miniMap.value?.invalidateMapSize()),
  },
  { activationThresholdPx: MAP_RESIZE_THRESHOLD }
);
onMounted(() => {
  const updateAutomaticHeight = () => {
    if (mapHeight.value == null) automaticHeight.value = measuredHeight();
  };
  updateAutomaticHeight();
  if (typeof ResizeObserver !== 'undefined' && canvas.value) {
    heightObserver = new ResizeObserver(updateAutomaticHeight);
    heightObserver.observe(canvas.value);
  }
});
onBeforeUnmount(() => heightObserver?.disconnect());
const showTransition = ref(false);
watch(
  () => props.focus,
  () => {
    showTransition.value = false;
  }
);
function reveal() {
  figure.value?.scrollIntoView({ block: 'nearest' });
  figure.value?.focus({ preventScroll: true });
}
async function refit() {
  await nextTick();
  miniMap.value?.refitView();
}
defineExpose({ reveal, refit });
const showOriginal = ref(true),
  showProcessed = ref(true);
const BOUNDS_MARGIN = 0.0002;
const valid = hasMapPosition;
const originalPoints = computed(() => {
  if (!props.focus) return props.points;
  const points = new Map(props.points.map((point) => [point.pointIndex, point]));
  for (const point of props.focus.points) points.set(point.pointIndex, point);
  return [...points.values()].sort((a, b) => (a.pointIndex ?? 0) - (b.pointIndex ?? 0));
});
const findingPoints = computed(() => {
  const focus = props.focus;
  const range = focus && findingPointRange(focus.finding);
  if (!focus || !range) return [];
  return focus.points.filter(
    (point) =>
      valid(point) &&
      belongsToFindingSection(point, focus.finding) &&
      (point.pointIndex === range.before || point.pointIndex === range.after)
  );
});
const centeredPoint = computed(() => {
  const focus = props.focus;
  if (!focus) return null;
  return focus.points.find((point) => point.pointIndex === focus.pointIndex && valid(point)) ?? null;
});
const mapFocus = computed<MiniMapFocus | null>(() =>
  centeredPoint.value && !showTransition.value
    ? { center: [centeredPoint.value.longitude!, centeredPoint.value.latitude!], spanMetres: FINDING_MAP_SPAN_METRES }
    : null
);
const markerPoints = computed(() => {
  const points = [...findingPoints.value];
  if (centeredPoint.value && !points.some((point) => point.pointIndex === centeredPoint.value?.pointIndex))
    points.push(centeredPoint.value);
  return points;
});
function features(points: TrackObservation[], properties: Record<string, unknown>): MiniMapGeoJson['features'] {
  const paths: number[][][] = [];
  let path: number[][] = [],
    section: number | undefined;
  for (const point of points) {
    if (point.sectionIndex !== section || !valid(point)) {
      if (path.length) paths.push(path);
      path = [];
      section = point.sectionIndex;
    }
    if (valid(point)) path.push([point.longitude!, point.latitude!]);
  }
  if (path.length) paths.push(path);
  return paths.map((points) => ({
    type: 'Feature',
    properties,
    geometry:
      points.length === 1 ? { type: 'Point', coordinates: points[0]! } : { type: 'LineString', coordinates: points },
  }));
}
const processedFeatures = computed<MiniMapGeoJson['features']>(() =>
  (props.processed ?? []).flatMap((report) => {
    const boundaries = new Set(report.canonicalBreakIndexes ?? []);
    const paths: number[][][] = [];
    let path: number[][] = [];
    (report.canonicalCoordinates ?? []).forEach((point, index) => {
      if (boundaries.has(index) || point[0] == null || point[1] == null) {
        if (path.length) paths.push(path);
        path = [];
      }
      if (point[0] != null && point[1] != null) path.push([point[0], point[1]]);
    });
    if (path.length) paths.push(path);
    return paths.map((points) => ({
      type: 'Feature',
      properties: { color: PROCESSED_TRACK_COLOR },
      geometry: points.every((point) => point[0] === points[0]![0] && point[1] === points[0]![1])
        ? { type: 'Point', coordinates: points[0]! }
        : { type: 'LineString', coordinates: points },
    }));
  })
);
const geojson = computed<MiniMapGeoJson>(() => ({
  type: 'FeatureCollection',
  features: [
    ...(showOriginal.value ? features(originalPoints.value, { color: props.color }) : []),
    ...(showProcessed.value ? processedFeatures.value : []),
    ...markerPoints.value.map((point): MiniMapGeoJson['features'][number] => ({
      type: 'Feature',
      properties: {
        type: 'locator',
        color: props.color,
        label: String(point.pointIndex! + 1),
        popupTitle: props.focus?.finding.reason ? reasonLabel(props.focus.finding.reason) : 'Selected finding',
        popupDetails: `Source point ${point.pointIndex! + 1}`,
      },
      geometry: { type: 'Point', coordinates: [point.longitude!, point.latitude!] },
    })),
  ],
}));
const bounds = computed<MiniMapBounds | null>(() => {
  const points = showTransition.value ? [...findingPoints.value] : props.points.filter(valid);
  if (!showTransition.value)
    for (const report of props.processed ?? [])
      for (const point of report.canonicalCoordinates ?? []) {
        const observation: TrackObservation = { longitude: point[0] ?? undefined, latitude: point[1] ?? undefined };
        if (valid(observation)) points.push(observation);
      }
  if (!points.length) return null;
  let west = Infinity,
    east = -Infinity,
    south = Infinity,
    north = -Infinity;
  for (const p of points) {
    west = Math.min(west, p.longitude!);
    east = Math.max(east, p.longitude!);
    south = Math.min(south, p.latitude!);
    north = Math.max(north, p.latitude!);
  }
  return [
    [west - BOUNDS_MARGIN, south - BOUNDS_MARGIN],
    [east + BOUNDS_MARGIN, north + BOUNDS_MARGIN],
  ];
});
</script>
<template>
  <figure ref="figure" class="review-map" tabindex="-1" aria-label="GPS review map">
    <figcaption>
      <span class="review-map__legend"
        ><span class="review-map__key" :style="{ background: color }"></span>{{ label }}</span
      >
      <span v-if="processed?.length" class="review-map__legend"
        ><span class="review-map__key" :style="{ background: PROCESSED_TRACK_COLOR }"></span>Processed path · blue</span
      >
    </figcaption>
    <div class="review-map__controls">
      <template v-if="processed?.length">
        <label><input v-model="showOriginal" type="checkbox" /> Original</label>
        <label><input v-model="showProcessed" type="checkbox" /> Processed</label>
      </template>
      <button
        type="button"
        @click="
          emit('reset');
          refit();
        "
      >
        Show whole track
      </button>
    </div>
    <div v-if="focus" class="review-map__focus" aria-live="polite">
      <span
        >{{ focus.finding.reason ? reasonLabel(focus.finding.reason) : 'Selected finding' }} · Point
        {{ focus.pointIndex + 1 }}</span
      >
      <span v-if="(focus.finding.affectedCount ?? 0) > 1">First reported occurrence.</span>
      <div class="review-map__controls">
        <button
          v-for="point in findingPoints"
          :key="point.pointIndex"
          type="button"
          @click="emit('center', point.pointIndex!)"
        >
          <i class="bi bi-crosshair" aria-hidden="true"></i> Center point {{ point.pointIndex! + 1 }}
        </button>
        <button
          v-if="findingPoints.length > 1"
          type="button"
          @click="
            showTransition = true;
            refit();
          "
        >
          Show transition
        </button>
      </div>
    </div>
    <div
      :id="canvasId"
      ref="canvas"
      class="review-map__canvas"
      :style="mapHeight == null ? undefined : { height: `${mapHeight}px` }"
    >
      <MiniMap
        v-if="bounds || mapFocus"
        ref="miniMap"
        :tracks-geo-json="geojson"
        :map-bounds="bounds"
        :map-focus="mapFocus"
        show-zoom-controls
      />
      <p v-else>No usable GPS positions to display.</p>
    </div>
    <div
      ref="resizeHandle"
      class="review-map__resize"
      role="separator"
      tabindex="0"
      aria-label="Resize GPS review map"
      aria-orientation="horizontal"
      :aria-controls="canvasId"
      :aria-valuemin="MAP_HEIGHT_MIN"
      :aria-valuemax="MAP_HEIGHT_MAX"
      :aria-valuenow="Math.round(currentHeight)"
      :aria-valuetext="`${Math.round(currentHeight)} pixel map height`"
      title="Drag, tap, or use arrow keys to resize the map"
      @click="onResizeClick"
      @keydown="onResizeKeydown"
    >
      <span></span>
    </div>
  </figure>
</template>
<style scoped>
.review-map {
  margin: 0;
  min-width: 0;
}
figcaption {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem 1.25rem;
  padding: 0.5rem 0;
  font-weight: 600;
}
.review-map__legend {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
.review-map__controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 1rem;
  padding: 0.25rem 0 0.5rem;
}
.review-map__focus {
  display: grid;
  gap: 0.25rem;
  font-size: var(--text-sm-size);
}
.review-map button {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  min-height: 2.75rem;
  padding: 0.3rem 0.5rem;
  border: 1px solid var(--border-default);
  border-radius: 0.35rem;
  background: var(--surface-hover);
  color: var(--text-primary);
  font: inherit;
  cursor: pointer;
}
.review-map:focus-visible,
.review-map button:focus-visible {
  outline: 2px solid var(--primary-color);
  outline-offset: 2px;
}
.review-map__key {
  width: 1.5rem;
  height: 0.25rem;
  border-radius: 2px;
}
.review-map__canvas {
  height: clamp(190px, 32vh, 420px);
  overflow: hidden;
  border: 1px solid var(--border-light);
  border-radius: 8px;
}
.review-map__canvas > p {
  padding: 1rem;
}
.review-map__resize {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 24px;
  cursor: ns-resize;
  touch-action: none;
  user-select: none;
}
.review-map__resize span {
  width: 36px;
  height: 4px;
  border-radius: 9999px;
  background: var(--border-hover);
}
.review-map__resize:hover span,
.review-map__resize:active span {
  width: 48px;
  background: var(--text-faint);
}
.review-map__resize:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: -2px;
}
@media (pointer: coarse) {
  .review-map__resize {
    height: 44px;
  }
}
</style>
