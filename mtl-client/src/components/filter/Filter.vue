<template>
  <CustomFilter
    ref="customFilter"
    v-model:show="showMenu"
    :palette="palette"
    :total-track-count="totalTrackCount"
    :visible-track-count="visibleTrackCount"
    @filter-applied-event="onClose"
    @filter-changed-event="onFilterChanged"
    @filter-style-changed="onFilterStyleChanged"
    @start-geo-drawing="onStartGeoDrawing"
    @clear-geo-shape="onClearGeoShape"
    @closed="onSheetClosed"
  />
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useFilterStore } from '@/stores/filterStore';
import CustomFilter from '@/components/filter/CustomFilter.vue';
import type { FilterResult } from '@/types/filter';
import type { ParamDefinition } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/ParamDefinition';

const EVENTS = {
  filterAppliedEvent: 'filterAppliedEvent',
  filterStyleChanged: 'filter-style-changed',
} as const;

defineOptions({ name: 'Filter' });

defineProps<{
  tileLayer?: unknown;
  palette?: unknown;
  totalTrackCount?: number | null;
  visibleTrackCount?: number | null;
}>();

const emit = defineEmits<{
  (event: 'tool-opened'): void;
  (event: 'tool-closed'): void;
  (event: 'filterAppliedEvent', filterResult?: FilterResult): void;
  (event: 'filter-style-changed'): void;
  (event: 'start-geo-drawing', paramDef: ParamDefinition): void;
  (event: 'clear-geo-shape', paramDef: ParamDefinition): void;
}>();

type GeoShapes = {
  circles: Record<string, unknown>;
  rectangles: Record<string, unknown>;
  polygons: Record<string, unknown>;
  labels?: Record<string, string>;
};

type CustomFilterPublic = {
  onGeoDrawingComplete: (paramDef: ParamDefinition, shape: unknown) => void;
  getGeoShapes: () => GeoShapes;
};

const showMenu = ref(false);
const customFilter = ref<CustomFilterPublic | null>(null);
const filterStore = useFilterStore();
// `active` was previously a local data field updated via activeFilterCheck().
// Now it's a reactive view onto the filterStore so any save through the
// store (CustomFilter.vue) automatically updates the toolbar chip.
const active = computed(() => filterStore.isActive);

onMounted(() => {
  console.log('Filter (main) mounted.');
  // Hydrate the store so `active` reflects persisted state on first render.
  filterStore.ensureLoaded().catch((e) => {
    console.warn('Filter: store ensureLoaded failed', e);
  });
});

async function toggle() {
  showMenu.value = !showMenu.value;
  if (showMenu.value) {
    emit('tool-opened');
  }
}

function close() {
  showMenu.value = false;
}

function resetFilter() {}

async function onSheetClosed() {
  // Force a re-read in case legacy code paths mutated FilterService directly.
  // Once all writes funnel through the store this can be removed.
  await filterStore.refresh();
  if (!active.value) {
    emit('tool-closed');
  }
}

function onClose() {
  showMenu.value = false;
  emit(EVENTS.filterAppliedEvent);
}

function onFilterChanged(filterResult?: FilterResult) {
  emit(EVENTS.filterAppliedEvent, filterResult);
}

function onFilterStyleChanged() {
  emit(EVENTS.filterStyleChanged);
}

function onStartGeoDrawing(paramDef: ParamDefinition) {
  emit('start-geo-drawing', paramDef);
}

function onClearGeoShape(paramDef: ParamDefinition) {
  emit('clear-geo-shape', paramDef);
}

/** Called by Map.vue when drawing completes */
function onGeoDrawingComplete(paramDef: ParamDefinition, shape: unknown) {
  customFilter.value?.onGeoDrawingComplete(paramDef, shape);
}

/** Returns all currently configured geo shapes for rendering on the map. */
function getGeoShapes(): GeoShapes {
  return customFilter.value?.getGeoShapes() ?? { circles: {}, rectangles: {}, polygons: {} };
}

defineExpose({
  toggle,
  close,
  resetFilter,
  onGeoDrawingComplete,
  getGeoShapes,
});
</script>

<style scoped></style>
