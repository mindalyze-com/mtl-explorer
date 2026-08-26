<template>
  <div>
    <div
      v-if="active"
      class="measure-map-overlay"
      :class="{ 'measure-map-overlay--empty': !overlayZoneNodes.length }"
      role="status"
      aria-live="polite"
    >
      <div class="measure-map-guidance">
        <span class="measure-map-guidance-icon" :class="overlayGuidance.toneClass" aria-hidden="true">
          <i :class="overlayGuidance.icon"></i>
        </span>
        <span class="measure-map-guidance-copy">
          <strong>{{ overlayGuidance.title }}</strong>
          <span>{{ overlayGuidance.detail }}</span>
        </span>
      </div>

      <div v-if="overlayZoneNodes.length" class="measure-flow-rail">
        <template v-for="(node, idx) in overlayZoneNodes" :key="node.key">
          <div class="measure-flow-node" :class="node.toneClass">
            <div class="measure-flow-node-circle">{{ node.label }}</div>
            <div class="measure-flow-node-value">{{ node.value }}</div>
            <div class="measure-flow-node-detail">{{ node.detail }}</div>
          </div>
          <div
            v-if="idx < overlayZoneNodes.length - 1 || sharedFlowNode"
            class="measure-flow-connector"
            :class="node.connectorClass"
          ></div>
        </template>

        <div v-if="sharedFlowNode" class="measure-flow-node measure-flow-node--final" :class="sharedFlowNode.toneClass">
          <div class="measure-flow-node-target">
            <span>{{ sharedFlowNode.value }}</span>
          </div>
          <div class="measure-flow-node-value measure-flow-node-value--final">{{ sharedFlowNode.title }}</div>
          <div class="measure-flow-node-detail">{{ sharedFlowNode.detail }}</div>
        </div>
      </div>
    </div>

    <BottomSheet
      v-model="active"
      :detents="measureSheetDetents"
      title="Segment Analyzer"
      icon="bi bi-stopwatch"
      header-mode="compact"
      :no-backdrop="true"
      @closed="onMeasureClosed"
    >
      <div class="measure-sheet">
        <section class="measure-controls-card measure-controls-card--dock">
          <!-- Row 1: Undo · Clear All · Analyze -->
          <div class="measure-toolbar">
            <button
              class="measure-toolbar-btn"
              :disabled="triggerPoints.length < 1 || isLoading"
              title="Undo last point"
              aria-label="Undo last point"
              @click="onUndoLastPoint"
            >
              <i class="bi bi-arrow-left"></i>
              <span>Undo</span>
            </button>
            <button
              class="measure-toolbar-btn"
              :disabled="!triggerPoints.length || isLoading"
              title="Clear all"
              aria-label="Clear all"
              @click="onCancelSelection"
            >
              <i class="bi bi-trash3"></i>
              <span>Clear all</span>
            </button>
            <button
              class="measure-toolbar-btn measure-toolbar-btn--analyze"
              :class="{ 'measure-toolbar-btn--ready': !isAnalyzeDisabled }"
              :disabled="isAnalyzeDisabled"
              :title="analyzeButtonTitle"
              @click="onFinishSelection"
            >
              <i v-if="isLoading" class="bi bi-arrow-clockwise measure-spin"></i>
              <i v-else class="bi bi-graph-up-arrow"></i>
              <span>{{ isLoading ? 'Analyzing…' : 'Analyze' }}</span>
            </button>
          </div>

          <!-- Row 2: Detection radius -->
          <div class="measure-radius-control">
            <div class="measure-radius-header">
              <div class="measure-radius-copy">
                <span id="measure-radius-label" class="measure-radius-label">Detection radius</span>
                <span class="measure-radius-description">How close a track must pass to each zone</span>
              </div>
              <output class="measure-radius-value">{{ radiusDisplay }}</output>
            </div>
            <MtlSlider
              v-model="radiusSelector"
              class="measure-bar-slider"
              :min="1"
              :max="100"
              aria-labelledby="measure-radius-label"
              :aria-value-text="radiusDisplay"
            />
          </div>

          <!-- Row 3: Tap-map hint + explanation -->
          <div class="measure-placement-section">
            <div class="measure-placement-status">
              <span class="measure-placement-kicker">Tap map</span>
              <span class="measure-placement-text">{{ dockHintText }}</span>
            </div>
            <p class="measure-explanation">
              Place zones on the map where your routes pass through. The analyzer finds all tracks crossing every zone
              and compares sector times between them.
            </p>
          </div>
        </section>
      </div>
    </BottomSheet>

    <BottomSheet
      v-if="showResults && measureServiceResult"
      v-model="showResults"
      title="Segment Analyzer"
      icon="bi bi-stopwatch"
      header-mode="compact"
      :detents="[{ height: '88vh' }, { height: '98vh' }]"
      @closed="onResultsClosed"
    >
      <DisplayMeasureResults
        :measure-service-result="measureServiceResult"
        @show-track-details="emit('show-track-details', $event)"
      />
    </BottomSheet>
  </div>
</template>

<script setup lang="ts">
import { computed, inject, nextTick, onBeforeUnmount, onMounted, provide, ref, watch } from 'vue';
import type * as maplibregl from 'maplibre-gl';
import type { CrossingPointsResponseDto, TriggerPoint } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import { fetchTrackDetailsForCrossingPoints, fetchTrackIdsWithinDistanceOfPoint } from '@/utils/ServiceHelper';
import DisplayMeasureResults from '@/components/measure/DisplayMeasureResults.vue';
import BottomSheet from '@/components/ui/BottomSheet.vue';
import MtlSlider from '@/components/ui/MtlSlider.vue';
import { EVENT_MEASURE_BETWEEN_POINTS_DIALOG_MAXIMIZED_EVENT, formatRadius } from '@/utils/Utils';
import { isAbortLikeError } from '@/utils/errors';
import { createGeoJsonCircle } from '@/utils/geoJson';
import { VIZ_BLUE_COLOR, VIZ_ERROR_COLOR, VIZ_ORANGE_STRONG_COLOR, VIZ_SLATE_COLOR } from '@/utils/visualizationColors';
import type { ToastService } from '@/types/ui';

defineOptions({ name: 'MeasureBetweenPoints' });

type MapLike = maplibregl.Map;
type MeasureTriggerPoint = TriggerPoint & { name: string; coordinate: { x: number; y: number } };
type ZoneVisualState = 'loading' | 'error' | 'warning' | 'ok';
type MapClickEvent = { lngLat: { lng: number; lat: number } };
type MeasureNavigationState = { view: 'results'; result: CrossingPointsResponseDto };

const MINIMUM_SEGMENT_ZONE_COUNT = 2;
const DESKTOP_COMPACT_SHEET_HEIGHT_PX = 310;
const MOBILE_COMPACT_SHEET_HEIGHT_PX = 350;
const SHORT_VIEWPORT_MAX_HEIGHT_PX = 500;
const SHORT_VIEWPORT_COMPACT_SHEET_HEIGHT_PX = 230;
const DESKTOP_EXPANDED_SHEET_HEIGHT_VH = 82;
const MOBILE_EXPANDED_SHEET_HEIGHT_VH = 88;
const SHORT_VIEWPORT_EXPANDED_SHEET_HEIGHT_VH = 92;

const props = defineProps<{
  map: MapLike;
}>();

const emit = defineEmits<{
  'active-changed': [active: boolean];
  'tool-opened': [];
  'tool-closed': [];
  'show-track-details': [trackId: number | string];
}>();

const fakeEvent = ref('empty');
provide(EVENT_MEASURE_BETWEEN_POINTS_DIALOG_MAXIMIZED_EVENT, fakeEvent);

const toast = inject<ToastService>('toast');

const active = ref(false);
const radiusSelector = ref(40);
const triggerPoints = ref<MeasureTriggerPoint[]>([]);
const zoneTrackCounts = ref<Array<number | undefined>>([]);
const zoneTrackIds = ref<Array<number[] | null | undefined>>([]);
const zoneCountAbortControllers = ref<Array<AbortController | undefined>>([]);
const zoneCountRequestTokens = ref<Array<number | undefined>>([]);
let radiusCountDebounceTimer: ReturnType<typeof setTimeout> | null = null;
const measureServiceResult = ref<CrossingPointsResponseDto | null>(null);
const showResults = ref(false);
const isToolOpen = computed(() => active.value || showResults.value);
const numberOfCrossings = ref(0);
const measureSourceIds = ref<string[]>([]);
const measureLayerIds = ref<string[]>([]);
let clickHandler: ((e: MapClickEvent) => void) | null = null;
let clickHandlerMap: MapLike | null = null;
const isLoading = ref(false);
const viewportWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1024);
const viewportHeight = ref(typeof window !== 'undefined' ? window.innerHeight : 900);
let crossingFetchAbortController: AbortController | null = null;
let crossingFetchToken = 0;
let zoneCountRequestToken = 0;

function radius() {
  const val = Math.round(Math.pow(1.1, radiusSelector.value));
  if (val > 10000) {
    return Math.round(val / 1000) * 1000;
  } else if (val > 1000) {
    return Math.round(val / 100) * 100;
  } else if (val > 100) {
    return Math.round(val / 10) * 10;
  } else {
    return val;
  }
}

const radiusDisplay = computed(() => formatRadius(radius()));

const candidateTrackIdsIntersection = computed(() => {
  if (!triggerPoints.value.length) {
    return [];
  }

  const loadedTrackLists = zoneTrackIds.value.slice(0, triggerPoints.value.length);
  if (loadedTrackLists.some((trackIds) => !Array.isArray(trackIds))) {
    return null;
  }

  if (!loadedTrackLists.length) {
    return [];
  }

  let intersection = new Set(loadedTrackLists[0] as number[]);
  for (let i = 1; i < loadedTrackLists.length; i++) {
    const zoneIds = new Set(loadedTrackLists[i] as number[]);
    intersection = new Set([...intersection].filter((id) => zoneIds.has(id)));
  }
  return [...intersection];
});

const hasNoMatchingCandidates = computed(
  () =>
    triggerPoints.value.length >= MINIMUM_SEGMENT_ZONE_COUNT &&
    Array.isArray(candidateTrackIdsIntersection.value) &&
    candidateTrackIdsIntersection.value.length === 0
);

const isAnalyzeDisabled = computed(
  () => isLoading.value || triggerPoints.value.length < MINIMUM_SEGMENT_ZONE_COUNT || hasNoMatchingCandidates.value
);

const analyzeButtonTitle = computed(() => {
  if (isLoading.value) return 'Analysis in progress';
  if (!triggerPoints.value.length) return 'Place zones A and B first';
  if (triggerPoints.value.length < MINIMUM_SEGMENT_ZONE_COUNT) return 'Place zone B to complete the segment';
  if (hasNoMatchingCandidates.value) return 'No tracks cross every zone';
  return 'Compare tracks crossing every zone';
});

const overlayZoneNodes = computed(() => {
  return triggerPoints.value.map((point, idx) => {
    const count = zoneTrackCounts.value[idx];
    let value = '...';
    let detail = 'tracks';
    const tone = getZoneVisualState(idx);

    if (count === -1) {
      value = '?';
      detail = 'check';
    } else if (count === undefined) {
      value = '...';
      detail = 'tracks';
    } else {
      value = String(count);
      detail = count === 1 ? 'track' : 'tracks';
    }

    return {
      key: point.name,
      label: point.name,
      value,
      detail,
      toneClass: `measure-flow-node--${tone}`,
      connectorClass: `measure-flow-connector--${tone}`,
    };
  });
});

const sharedFlowNode = computed(() => {
  if (triggerPoints.value.length < MINIMUM_SEGMENT_ZONE_COUNT) {
    return null;
  }

  const relevantCounts = zoneTrackCounts.value.slice(0, triggerPoints.value.length);
  if (relevantCounts.some((count) => count === -1)) {
    return { value: '?', title: 'shared', detail: 'check', toneClass: 'measure-flow-node--error' };
  }
  if (relevantCounts.some((count) => count === undefined)) {
    return { value: '...', title: 'shared', detail: 'tracks', toneClass: 'measure-flow-node--loading' };
  }

  const candidateCount = Array.isArray(candidateTrackIdsIntersection.value)
    ? candidateTrackIdsIntersection.value.length
    : 0;
  if (candidateCount === 0) {
    return { value: '!', title: 'shared', detail: 'tracks', toneClass: 'measure-flow-node--warning' };
  }

  return {
    value: String(candidateCount),
    title: 'shared',
    detail: candidateCount === 1 ? 'track' : 'tracks',
    toneClass: 'measure-flow-node--ok',
  };
});

const overlayGuidance = computed(() => {
  const zoneCount = triggerPoints.value.length;
  if (zoneCount === 0) {
    return {
      title: 'Tap a track to place zone A',
      detail: 'Start where the segment should begin.',
      icon: 'bi bi-crosshair',
      toneClass: 'measure-map-guidance-icon--active',
    };
  }

  if (zoneCount < MINIMUM_SEGMENT_ZONE_COUNT) {
    const zoneCountResult = zoneTrackCounts.value[0];
    if (zoneCountResult === undefined) {
      return {
        title: 'Checking zone A',
        detail: 'You can place zone B while this finishes.',
        icon: 'bi bi-arrow-clockwise measure-spin',
        toneClass: 'measure-map-guidance-icon--loading',
      };
    }
    if (zoneCountResult === -1) {
      return {
        title: 'Zone A could not be checked',
        detail: 'Adjust the zone or try placing zone B.',
        icon: 'bi bi-exclamation-circle',
        toneClass: 'measure-map-guidance-icon--error',
      };
    }
    if (zoneCountResult === 0) {
      return {
        title: 'No tracks cross zone A',
        detail: 'Undo the zone or increase the detection radius.',
        icon: 'bi bi-exclamation-circle',
        toneClass: 'measure-map-guidance-icon--warning',
      };
    }
    return {
      title: 'Tap a track to place zone B',
      detail: 'This sets the end of the segment.',
      icon: 'bi bi-crosshair',
      toneClass: 'measure-map-guidance-icon--active',
    };
  }

  const relevantCounts = zoneTrackCounts.value.slice(0, zoneCount);
  if (relevantCounts.some((count) => count === -1)) {
    return {
      title: 'Some matches could not be checked',
      detail: 'Adjust a zone or try Analyze.',
      icon: 'bi bi-exclamation-circle',
      toneClass: 'measure-map-guidance-icon--error',
    };
  }
  if (relevantCounts.some((count) => count === undefined)) {
    return {
      title: 'Checking matching tracks',
      detail: 'You can add another zone while this finishes.',
      icon: 'bi bi-arrow-clockwise measure-spin',
      toneClass: 'measure-map-guidance-icon--loading',
    };
  }
  if (hasNoMatchingCandidates.value) {
    return {
      title: 'No tracks cross every zone',
      detail: 'Undo a zone or increase the detection radius.',
      icon: 'bi bi-exclamation-circle',
      toneClass: 'measure-map-guidance-icon--warning',
    };
  }

  const matchCount = (candidateTrackIdsIntersection.value as number[]).length;
  return {
    title: 'Ready to analyze',
    detail: `${matchCount} matching ${matchCount === 1 ? 'track' : 'tracks'} · add another zone or choose Analyze.`,
    icon: 'bi bi-check2-circle',
    toneClass: 'measure-map-guidance-icon--ready',
  };
});

const measureSheetDetents = computed(() => {
  const isMobile = viewportWidth.value <= 768;
  const isShortViewport = viewportHeight.value <= SHORT_VIEWPORT_MAX_HEIGHT_PX;
  const compactPx = isShortViewport
    ? SHORT_VIEWPORT_COMPACT_SHEET_HEIGHT_PX
    : isMobile
      ? MOBILE_COMPACT_SHEET_HEIGHT_PX
      : DESKTOP_COMPACT_SHEET_HEIGHT_PX;
  const expandedVh = isShortViewport
    ? SHORT_VIEWPORT_EXPANDED_SHEET_HEIGHT_VH
    : isMobile
      ? MOBILE_EXPANDED_SHEET_HEIGHT_VH
      : DESKTOP_EXPANDED_SHEET_HEIGHT_VH;
  return [
    { id: 'compact', height: `${compactPx}px` },
    { id: 'expanded', height: `${expandedVh}vh` },
  ];
});

const dockHintText = computed(() => {
  if (!triggerPoints.value.length) {
    return 'Zone A is next';
  }
  if (triggerPoints.value.length === 1) {
    const zoneCount = zoneTrackCounts.value[0];
    if (zoneCount === undefined) return 'Checking zone A';
    if (zoneCount === -1) return 'Zone A check unavailable';
    if (zoneCount === 0) return 'Adjust zone A or the radius';
    return 'Zone A ready · zone B is next';
  }
  const nextLabel = String.fromCharCode(65 + triggerPoints.value.length);
  return `${triggerPoints.value.length} zones ready · add ${nextLabel} or analyze`;
});

function onMaximizeSender() {
  fakeEvent.value = 'maximized' + new Date();
}

function onViewportResize() {
  viewportWidth.value = window.innerWidth;
  viewportHeight.value = window.innerHeight;
}

/**
 * Picks a detection radius proportional to the visible map extent.
 * Takes ~4% of the shorter (width/height) dimension, clamped to [10m, 2000m].
 *
 * Examples:
 *   extent 300m  → 12m  → 10m (floor)
 *   extent 500m  → 20m
 *   extent 1km   → 40m
 *   extent 2km   → 80m
 *   extent 5km   → 200m
 *   extent 50km  → 2000m (ceiling)
 */
function radiusSelectorForExtent(map: MapLike) {
  const bounds = map.getBounds();
  const centerLat = (bounds.getNorth() + bounds.getSouth()) / 2;
  const metersPerDegLat = 111320;
  const metersPerDegLng = 111320 * Math.cos((centerLat * Math.PI) / 180);
  const widthM = Math.abs(bounds.getEast() - bounds.getWest()) * metersPerDegLng;
  const heightM = Math.abs(bounds.getNorth() - bounds.getSouth()) * metersPerDegLat;
  const extentM = Math.min(widthM, heightM);
  const targetM = Math.max(10, Math.min(2000, extentM * 0.04));
  return Math.round(Math.max(1, Math.min(100, Math.log(targetM) / Math.log(1.1))));
}

async function toggle() {
  if (isToolOpen.value) {
    close();
    return;
  }
  await open();
}

async function open() {
  if (isToolOpen.value) return;
  measureServiceResult.value = null;
  active.value = true;
  emit('active-changed', true);
  emit('tool-opened');
  attachMapClickHandler();
}

function close() {
  if (!isToolOpen.value) return;
  cancelCrossingFetch();
  active.value = false;
  showResults.value = false;
  measureServiceResult.value = null;
  numberOfCrossings.value = 0;
  emit('active-changed', false);
  detachMapClickHandler();
  resetZoneSelection();
}

function getNavigationState(): MeasureNavigationState | null {
  if (!showResults.value || !measureServiceResult.value) return null;
  return { view: 'results', result: measureServiceResult.value };
}

function restoreNavigationState(state: unknown) {
  const navigationState = state as MeasureNavigationState | null;
  if (navigationState?.view !== 'results' || !navigationState.result) return;
  const wasOpen = isToolOpen.value;
  cancelCrossingFetch();
  active.value = false;
  detachMapClickHandler();
  resetZoneSelection();
  measureServiceResult.value = navigationState.result;
  numberOfCrossings.value =
    navigationState.result.crossings != null ? Object.keys(navigationState.result.crossings).length : 0;
  showResults.value = true;
  if (!wasOpen) {
    emit('active-changed', true);
    emit('tool-opened');
  }
}

async function onCancelSelection() {
  cancelCrossingFetch();
  resetZoneSelection();
}

function onUndoLastPoint() {
  if (!triggerPoints.value.length) return;
  const idx = triggerPoints.value.length - 1;
  triggerPoints.value.pop();

  // Cancel pending count request for this zone
  if (zoneCountAbortControllers.value[idx]) {
    zoneCountAbortControllers.value[idx]?.abort();
    zoneCountAbortControllers.value.splice(idx, 1);
  }
  zoneCountRequestTokens.value.splice(idx, 1);
  zoneTrackCounts.value.splice(idx, 1);
  zoneTrackIds.value.splice(idx, 1);

  // Remove map layers for this zone (3 layers + 2 sources per zone)
  const layersToRemove = [
    `measure-circle-layer-${idx}`,
    `measure-circle-outline-layer-${idx}`,
    `measure-label-layer-${idx}`,
  ];
  const sourcesToRemove = [`measure-circle-${idx}`, `measure-label-${idx}`];
  if (props.map) {
    for (const layerId of layersToRemove) {
      if (props.map.getLayer(layerId)) props.map.removeLayer(layerId);
    }
    for (const sourceId of sourcesToRemove) {
      if (props.map.getSource(sourceId)) props.map.removeSource(sourceId);
    }
  }
  measureLayerIds.value = measureLayerIds.value.filter((id) => !layersToRemove.includes(id));
  measureSourceIds.value = measureSourceIds.value.filter((id) => !sourcesToRemove.includes(id));
}

function cleanupMapLayers() {
  if (!props.map) return;
  for (const layerId of measureLayerIds.value) {
    if (props.map.getLayer(layerId)) props.map.removeLayer(layerId);
  }
  for (const sourceId of measureSourceIds.value) {
    if (props.map.getSource(sourceId)) props.map.removeSource(sourceId);
  }
  measureLayerIds.value = [];
  measureSourceIds.value = [];
  cancelAllZoneCountRequests();
}

function detachMapClickHandler() {
  if (!clickHandler || !clickHandlerMap) return;
  clickHandlerMap.off('click', clickHandler);
  clickHandler = null;
  clickHandlerMap = null;
}

function attachMapClickHandler() {
  const map = props.map;
  if (!active.value || !map) return;
  if (clickHandler && clickHandlerMap === map) return;

  detachMapClickHandler();
  radiusSelector.value = radiusSelectorForExtent(map);
  clickHandler = (event) => onMapClick(event);
  clickHandlerMap = map;
  map.on('click', clickHandler);
}

function resetZoneSelection() {
  cleanupMapLayers();
  triggerPoints.value = [];
  zoneTrackCounts.value = [];
  zoneTrackIds.value = [];
  zoneCountRequestTokens.value = [];
}

function cancelAllZoneCountRequests() {
  for (const ac of zoneCountAbortControllers.value) {
    if (ac) ac.abort();
  }
  zoneCountAbortControllers.value = [];
  zoneCountRequestToken++;
  zoneCountRequestTokens.value = [];
  if (radiusCountDebounceTimer) {
    clearTimeout(radiusCountDebounceTimer);
    radiusCountDebounceTimer = null;
  }
}

function cancelCrossingFetch() {
  crossingFetchToken++;
  crossingFetchAbortController?.abort();
  crossingFetchAbortController = null;
  isLoading.value = false;
}

async function onFinishSelection() {
  if (isAnalyzeDisabled.value) {
    return;
  }
  cancelCrossingFetch();
  const requestToken = ++crossingFetchToken;
  const requestTriggerPoints = triggerPoints.value.map((point) => ({
    ...point,
    coordinate: { ...point.coordinate },
  }));
  const requestRadius = radius();
  isLoading.value = true;
  const abortController = new AbortController();
  crossingFetchAbortController = abortController;
  try {
    const data = await fetchTrackDetailsForCrossingPoints(requestTriggerPoints, requestRadius, abortController.signal);
    if (requestToken !== crossingFetchToken || abortController.signal.aborted) return;
    measureServiceResult.value = data;
    numberOfCrossings.value =
      measureServiceResult.value.crossings != null ? Object.keys(measureServiceResult.value.crossings).length : 0;
    active.value = false;
    detachMapClickHandler();
    resetZoneSelection();
    nextTick(() => {
      showResults.value = true;
    });
  } catch (error) {
    if (requestToken !== crossingFetchToken) return;
    if (isAbortLikeError(error)) return;
    console.error('Measure fetch failed:', error);
    toast?.add({
      severity: 'warning',
      summary: 'Info',
      detail: 'Failed to fetch from server',
      life: 2000,
    });
  } finally {
    if (requestToken === crossingFetchToken && crossingFetchAbortController === abortController) {
      crossingFetchAbortController = null;
      isLoading.value = false;
    }
  }
}

function onMeasureClosed() {
  cancelCrossingFetch();
  active.value = false;
  showResults.value = false;
  measureServiceResult.value = null;
  numberOfCrossings.value = 0;
  emit('active-changed', false);
  emit('tool-closed');
  detachMapClickHandler();
  resetZoneSelection();
}

function onResultsClosed() {
  showResults.value = false;
  measureServiceResult.value = null;
  numberOfCrossings.value = 0;
  emit('active-changed', false);
  emit('tool-closed');
}

function onMaximize() {
  onMaximizeSender();
}

function onMapClick(e: MapClickEvent) {
  if (!active.value || !props.map) return;

  const lngLat = e.lngLat;
  const idx = triggerPoints.value.length;
  const labelText = String.fromCharCode(65 + idx);

  const triggerPoint: MeasureTriggerPoint = {
    name: labelText,
    coordinate: {
      x: lngLat.lng,
      y: lngLat.lat,
    },
  };
  triggerPoints.value.push(triggerPoint);

  zoneTrackCounts.value.push(undefined); // undefined = loading
  zoneTrackIds.value.push(undefined);
  zoneCountRequestTokens.value.push(undefined);

  const zoneColor = getZoneColor(idx);

  const circleSourceId = `measure-circle-${idx}`;
  const circleLayerId = `measure-circle-layer-${idx}`;
  const circleGeoJson = createGeoJsonCircle(lngLat.lng, lngLat.lat, radius());

  props.map.addSource(circleSourceId, {
    type: 'geojson',
    data: circleGeoJson,
  });
  props.map.addLayer({
    id: circleLayerId,
    type: 'fill',
    source: circleSourceId,
    paint: {
      'fill-color': zoneColor,
      'fill-opacity': 0.28,
      'fill-opacity-transition': { duration: 0, delay: 0 },
    },
  });
  const circleOutlineLayerId = `measure-circle-outline-layer-${idx}`;
  props.map.addLayer({
    id: circleOutlineLayerId,
    type: 'line',
    source: circleSourceId,
    paint: {
      'line-color': zoneColor,
      'line-width': 3,
      'line-opacity': 0.88,
      'line-opacity-transition': { duration: 0, delay: 0 },
    },
  });
  measureSourceIds.value.push(circleSourceId);
  measureLayerIds.value.push(circleLayerId);
  measureLayerIds.value.push(circleOutlineLayerId);

  const labelSourceId = `measure-label-${idx}`;
  const labelLayerId = `measure-label-layer-${idx}`;

  props.map.addSource(labelSourceId, {
    type: 'geojson',
    data: {
      type: 'FeatureCollection',
      features: [
        {
          type: 'Feature',
          geometry: { type: 'Point', coordinates: [lngLat.lng, lngLat.lat] },
          properties: { label: labelText },
        },
      ],
    },
  });
  props.map.addLayer({
    id: labelLayerId,
    type: 'symbol',
    source: labelSourceId,
    layout: {
      'text-field': ['get', 'label'],
      'text-size': 18,
      'text-font': ['Noto Sans Medium'],
      'text-anchor': 'center',
      'text-allow-overlap': true,
    },
    paint: {
      'text-color': '#10213a',
      'text-halo-color': 'rgba(255,255,255,0.94)',
      'text-halo-width': 2.4,
      'text-opacity-transition': { duration: 0, delay: 0 },
    },
  });
  measureSourceIds.value.push(labelSourceId);
  measureLayerIds.value.push(labelLayerId);

  // Fire async server count for this zone
  fetchZoneTrackCount(idx);
}

function updateAllCircles() {
  if (!props.map) return;
  triggerPoints.value.forEach((point, idx) => {
    const source = props.map.getSource(`measure-circle-${idx}`) as maplibregl.GeoJSONSource | undefined;
    if (source) {
      source.setData(createGeoJsonCircle(point.coordinate.x, point.coordinate.y, radius()));
    }
  });
}

function getZoneVisualState(idx: number): ZoneVisualState {
  const count = zoneTrackCounts.value[idx];
  if (count === undefined) {
    return 'loading';
  }
  if (count === -1) {
    return 'error';
  }
  if (count === 0) {
    return 'warning';
  }
  return 'ok';
}

function getZoneColor(idx: number) {
  const state = getZoneVisualState(idx);
  if (state === 'loading') {
    return VIZ_SLATE_COLOR;
  }
  if (state === 'error') {
    return VIZ_ERROR_COLOR;
  }
  if (state === 'warning') {
    return VIZ_ORANGE_STRONG_COLOR;
  }
  return VIZ_BLUE_COLOR;
}

function updateZoneColor(idx: number) {
  if (!props.map) return;
  const color = getZoneColor(idx);
  const fillLayerId = `measure-circle-layer-${idx}`;
  const outlineLayerId = `measure-circle-outline-layer-${idx}`;
  if (props.map.getLayer(fillLayerId)) {
    props.map.setPaintProperty(fillLayerId, 'fill-color', color);
    props.map.setPaintProperty(fillLayerId, 'fill-opacity', getZoneVisualState(idx) === 'loading' ? 0.18 : 0.28);
  }
  if (props.map.getLayer(outlineLayerId)) {
    props.map.setPaintProperty(outlineLayerId, 'line-color', color);
    props.map.setPaintProperty(outlineLayerId, 'line-width', getZoneVisualState(idx) === 'warning' ? 4 : 3.2);
    props.map.setPaintProperty(outlineLayerId, 'line-opacity', 0.9);
    props.map.setPaintProperty(
      outlineLayerId,
      'line-dasharray',
      getZoneVisualState(idx) === 'loading' ? [2, 2] : [1, 0]
    );
  }
}

/**
 * Fires a server-side count request for one zone using the existing endpoint.
 */
async function fetchZoneTrackCount(idx: number) {
  // Cancel any previous request for this index
  if (zoneCountAbortControllers.value[idx]) {
    zoneCountAbortControllers.value[idx]?.abort();
  }
  const ac = new AbortController();
  zoneCountAbortControllers.value[idx] = ac;

  const point = triggerPoints.value[idx];
  if (!point) return;
  const requestToken = ++zoneCountRequestToken;
  const requestRadius = radius();
  zoneCountRequestTokens.value[idx] = requestToken;

  const isCurrentZoneRequest = () =>
    zoneCountRequestTokens.value[idx] === requestToken &&
    triggerPoints.value[idx]?.name === point.name &&
    triggerPoints.value[idx]?.coordinate?.x === point.coordinate.x &&
    triggerPoints.value[idx]?.coordinate?.y === point.coordinate.y;

  try {
    const trackIds = await fetchTrackIdsWithinDistanceOfPoint(
      point.coordinate.x,
      point.coordinate.y,
      requestRadius,
      ac.signal
    );
    // Check zone still exists (user may have undone it)
    if (isCurrentZoneRequest()) {
      zoneTrackIds.value[idx] = trackIds;
      zoneTrackCounts.value[idx] = trackIds.length;
      updateZoneColor(idx);
      // Force Vue reactivity for the array
      zoneTrackCounts.value = [...zoneTrackCounts.value];
    }
  } catch (e) {
    if (!isCurrentZoneRequest()) return;
    if (isAbortLikeError(e, ac.signal)) return;
    // On error, mark as unknown
    if (isCurrentZoneRequest()) {
      zoneTrackIds.value[idx] = null;
      zoneTrackCounts.value[idx] = -1;
      updateZoneColor(idx);
      zoneTrackCounts.value = [...zoneTrackCounts.value];
    }
  }
}

function debouncedRefreshAllZoneCounts() {
  if (radiusCountDebounceTimer) {
    clearTimeout(radiusCountDebounceTimer);
  }
  radiusCountDebounceTimer = setTimeout(() => {
    triggerPoints.value.forEach((_, idx) => {
      zoneTrackIds.value[idx] = undefined;
      zoneTrackCounts.value[idx] = undefined; // reset to loading
      zoneCountRequestTokens.value[idx] = undefined;
      updateZoneColor(idx);
      fetchZoneTrackCount(idx);
    });
    zoneTrackCounts.value = [...zoneTrackCounts.value];
  }, 500);
}

watch(radiusSelector, () => {
  updateAllCircles();
  debouncedRefreshAllZoneCounts();
});

watch(
  () => props.map,
  () => {
    attachMapClickHandler();
  }
);

onMounted(() => {
  if (typeof window !== 'undefined') {
    window.addEventListener('resize', onViewportResize);
  }
});

onBeforeUnmount(() => {
  cancelCrossingFetch();
  cleanupMapLayers();
  detachMapClickHandler();
  if (typeof window !== 'undefined') {
    window.removeEventListener('resize', onViewportResize);
  }
});

defineExpose({
  open,
  toggle,
  close,
  getNavigationState,
  restoreNavigationState,
  onMaximize,
});
</script>

<style>
.measure-map-overlay {
  position: fixed;
  top: calc(env(safe-area-inset-top, 0px) + 0.75rem);
  left: 50%;
  transform: translateX(-50%);
  z-index: 1100;
  display: inline-flex;
  flex-direction: column;
  align-items: stretch;
  min-width: min(34rem, calc(100vw - 1.25rem));
  max-width: calc(100vw - 1.25rem);
  padding: 0.65rem 0.8rem;
  border-radius: 1.25rem;
  border: 1px solid var(--border-medium);
  background: var(--surface-glass);
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.14);
  backdrop-filter: var(--blur-light);
  -webkit-backdrop-filter: var(--blur-light);
  pointer-events: none;
}

.measure-map-overlay--empty {
  min-width: min(27rem, calc(100vw - 1.25rem));
}

.measure-map-guidance {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  min-width: 0;
}

.measure-map-guidance-icon {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 2.1rem;
  height: 2.1rem;
  border-radius: 999px;
  background: color-mix(in srgb, currentColor 12%, transparent);
  font-size: var(--text-base-size);
}

.measure-map-guidance-icon--active,
.measure-map-guidance-icon--ready {
  color: var(--viz-blue);
}

.measure-map-guidance-icon--loading {
  color: var(--viz-slate);
}

.measure-map-guidance-icon--warning {
  color: var(--viz-orange-strong);
}

.measure-map-guidance-icon--error {
  color: var(--viz-red);
}

.measure-map-guidance-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 0.08rem;
}

.measure-map-guidance-copy strong {
  color: var(--text-primary);
  font-size: var(--text-sm-size);
  font-weight: 750;
  line-height: var(--text-sm-lh);
}

.measure-map-guidance-copy span {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}

.measure-flow-rail {
  display: flex;
  align-items: center;
  width: 100%;
  min-width: 0;
  margin-top: 0.55rem;
  padding-top: 0.55rem;
  border-top: 1px solid var(--border-default);
}

.measure-flow-node {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.14rem;
  flex: 0 0 auto;
  min-width: 2.8rem;
}

.measure-flow-node-circle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 1.7rem;
  height: 1.7rem;
  border-radius: 999px;
  border: 1px solid currentColor;
  background: var(--surface-glass-heavy);
  font-size: var(--text-sm-size);
  font-weight: 800;
  line-height: var(--text-sm-lh);
}

.measure-flow-node-value {
  color: currentColor;
  font-size: var(--text-sm-size);
  font-weight: 800;
  line-height: var(--text-sm-lh);
}

.measure-flow-node-value--final {
  text-transform: uppercase;
  letter-spacing: 0.08em;
  font-size: var(--text-2xs-size);
}

.measure-flow-node-detail {
  color: rgba(15, 23, 42, 0.56);
  font-size: var(--text-2xs-size);
  font-weight: 700;
  line-height: var(--text-2xs-lh);
  text-transform: lowercase;
}

.measure-flow-node-target {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 1.95rem;
  height: 1.95rem;
  border-radius: 999px;
  border: 2px solid currentColor;
  background: var(--surface-glass-heavy);
  font-size: var(--text-sm-size);
  font-weight: 900;
  line-height: var(--text-sm-lh);
}

.measure-flow-node-target::after {
  content: '';
  position: absolute;
  inset: 0.22rem;
  border-radius: 999px;
  border: 1.5px solid currentColor;
  opacity: 0.32;
}

.measure-flow-connector {
  flex: 1 1 2rem;
  min-width: 1rem;
  height: 2px;
  margin: 0 0.3rem 1.05rem;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.35);
}

.measure-flow-node--ok {
  color: var(--viz-blue);
}

.measure-flow-node--loading {
  color: var(--viz-slate);
}

.measure-flow-node--warning {
  color: var(--viz-orange-strong);
}

.measure-flow-node--error {
  color: var(--viz-red);
}

.measure-flow-connector--ok {
  background: linear-gradient(
    90deg,
    color-mix(in srgb, var(--viz-blue) 55%, transparent),
    color-mix(in srgb, var(--viz-blue) 18%, transparent)
  );
}

.measure-flow-connector--loading {
  background: linear-gradient(
    90deg,
    color-mix(in srgb, var(--viz-slate) 46%, transparent),
    color-mix(in srgb, var(--viz-slate) 16%, transparent)
  );
}

.measure-flow-connector--warning {
  background: linear-gradient(
    90deg,
    color-mix(in srgb, var(--viz-orange-strong) 48%, transparent),
    color-mix(in srgb, var(--viz-orange-strong) 16%, transparent)
  );
}

.measure-flow-connector--error {
  background: linear-gradient(
    90deg,
    color-mix(in srgb, var(--viz-red) 48%, transparent),
    color-mix(in srgb, var(--viz-red) 16%, transparent)
  );
}
</style>

<style scoped>
.measure-sheet {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 0;
  gap: 0.7rem;
  padding: 0.35rem 0.75rem calc(0.75rem + var(--safe-bottom));
  color: var(--text-secondary);
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  overscroll-behavior-y: contain;
}

.measure-controls-card,
.measure-info-card {
  border-radius: 0;
}

.measure-stat-label,
.measure-control-label {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  font-weight: 600;
  letter-spacing: 0.02em;
  text-transform: uppercase;
}

.measure-stat-value {
  color: var(--text-primary);
  font-size: var(--text-base-size);
  line-height: var(--text-base-lh);
}

.measure-controls-card {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 0.5rem 0;
}

.measure-controls-card--dock {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 0.35rem 0 0.15rem;
}

/* ── Toolbar: Undo · Clear All · Analyze ── */
.measure-toolbar {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.measure-toolbar-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.38rem;
  height: 2.55rem;
  padding: 0 0.85rem;
  border-radius: 0.85rem;
  border: none;
  background: var(--accent);
  color: var(--text-inverse);
  font-size: var(--text-sm-size);
  font-weight: 700;
  cursor: pointer;
  transition:
    transform 0.15s ease,
    opacity 0.15s ease,
    box-shadow 0.15s ease;
  white-space: nowrap;
}

.measure-toolbar-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(37, 99, 235, 0.25);
}

.measure-toolbar-btn:disabled {
  opacity: 0.42;
  cursor: not-allowed;
}

.measure-toolbar-btn i {
  font-size: var(--text-base-size);
}

.measure-toolbar-btn--analyze {
  margin-left: auto;
  background: var(--text-muted);
  color: var(--surface-page);
  opacity: 0.52;
}

.measure-toolbar-btn--analyze.measure-toolbar-btn--ready {
  background: var(--accent);
  color: var(--text-inverse);
  opacity: 1;
}

.measure-toolbar-btn--analyze.measure-toolbar-btn--ready:hover:not(:disabled) {
  box-shadow: 0 2px 12px rgba(37, 99, 235, 0.35);
}

/* ── Detection radius ── */
.measure-radius-control {
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
  width: min(100%, 30rem);
  margin-inline: auto;
}

.measure-radius-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.measure-radius-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 0.08rem;
}

.measure-radius-label {
  color: var(--text-primary);
  font-size: var(--text-sm-size);
  font-weight: 700;
  line-height: var(--text-sm-lh);
}

.measure-radius-description {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}

.measure-radius-value {
  flex: 0 0 auto;
  min-width: 4rem;
  padding: 0.28rem 0.55rem;
  border: 1px solid var(--border-default);
  border-radius: 999px;
  background: var(--surface-glass-subtle);
  color: var(--text-primary);
  font-size: var(--text-sm-size);
  font-weight: 750;
  line-height: var(--text-sm-lh);
  text-align: center;
}

.measure-controls-header {
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
}

.measure-control-value {
  color: var(--text-primary);
  font-size: var(--text-base-size);
  font-weight: 800;
}

.measure-control-note,
.measure-zone-hint {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
}

/* ── Placement section ── */
.measure-placement-section {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  padding-top: 0.15rem;
  border-top: 1px solid var(--border-default);
}

.measure-placement-status {
  display: flex;
  flex-direction: column;
  gap: 0.12rem;
}

.measure-placement-kicker {
  color: var(--text-muted);
  font-size: var(--text-2xs-size);
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.measure-placement-text {
  color: var(--text-primary);
  font-size: var(--text-sm-size);
  font-weight: 600;
  line-height: var(--text-sm-lh);
}

.measure-explanation {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  font-weight: 400;
  line-height: var(--text-xs-lh);
  margin: 0;
  opacity: 0.82;
}

.measure-bar-slider {
  width: 100%;
  align-self: center;
  --mtl-slider-handle-size-default: 20px;
  --mtl-slider-handle-size-coarse: 28px;
  --mtl-slider-track-height-default: 8px;
  --mtl-slider-track-height-coarse: 12px;
  --mtl-slider-handle-halo: 0 0 0 5px var(--accent-subtle);
  --mtl-slider-handle-halo-active: 0 0 0 5px var(--accent-subtle);
}

.measure-zone-section {
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
}

.measure-zone-list {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
}

.measure-zone-chip,
.measure-zone-empty {
  display: inline-flex;
  align-items: center;
  min-height: 2rem;
  padding: 0.35rem 0.75rem;
  border-radius: 999px;
  border: 1px solid var(--border-default);
  background: var(--surface-glass-subtle);
}

.measure-zone-chip {
  color: var(--text-primary);
  font-size: var(--text-sm-size);
  font-weight: 700;
}

.measure-zone-empty {
  color: var(--text-muted);
  font-size: var(--text-sm-size);
}

@keyframes measure-spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.measure-spin {
  animation: measure-spin 0.8s linear infinite;
}

@keyframes measure-overlay-pulse {
  0% {
    box-shadow: 0 10px 24px rgba(15, 23, 42, 0.1);
  }
  50% {
    box-shadow: 0 12px 28px rgba(100, 116, 139, 0.22);
  }
  100% {
    box-shadow: 0 10px 24px rgba(15, 23, 42, 0.1);
  }
}

@media screen and (max-width: 768px) {
  .measure-map-overlay {
    min-width: calc(100vw - 0.9rem);
    max-width: calc(100vw - 0.9rem);
    top: calc(env(safe-area-inset-top, 0px) + 0.5rem);
    padding: 0.55rem 0.6rem;
  }

  .measure-map-guidance {
    gap: 0.5rem;
  }

  .measure-map-guidance-icon {
    width: 1.85rem;
    height: 1.85rem;
    font-size: var(--text-sm-size);
  }

  .measure-map-guidance-copy strong {
    font-size: var(--text-xs-size);
  }

  .measure-map-guidance-copy span {
    font-size: var(--text-2xs-size);
  }

  .measure-flow-rail {
    margin-top: 0.45rem;
    padding-top: 0.45rem;
  }

  .measure-flow-node {
    min-width: 2.3rem;
  }

  .measure-flow-node-circle {
    width: 1.45rem;
    height: 1.45rem;
    font-size: var(--text-xs-size);
  }

  .measure-flow-node-target {
    width: 1.68rem;
    height: 1.68rem;
    font-size: var(--text-xs-size);
  }

  .measure-flow-node-value {
    font-size: var(--text-xs-size);
  }

  .measure-flow-node-value--final {
    font-size: var(--text-2xs-size);
  }

  .measure-flow-node-detail {
    font-size: var(--text-2xs-size);
  }

  .measure-flow-connector {
    margin: 0 0.18rem 0.94rem;
  }

  .measure-sheet {
    gap: 0.45rem;
    padding: 0.1rem 0.2rem calc(0.5rem + var(--safe-bottom));
  }

  .measure-sheet-header {
    min-height: 1.7rem;
  }

  .measure-sheet-header-copy {
    padding-right: 2.1rem;
  }

  .measure-toolbar {
    gap: 0.35rem;
  }

  .measure-toolbar-btn {
    height: 2.75rem;
    padding: 0 0.65rem;
    font-size: var(--text-xs-size);
    border-radius: 0.75rem;
  }

  .measure-radius-control {
    gap: 0.55rem;
    width: 100%;
  }

  .measure-radius-description {
    font-size: var(--text-2xs-size);
  }

  .measure-placement-section {
    gap: 0.4rem;
  }

  .measure-placement-text {
    font-size: var(--text-sm-size);
  }

  .measure-explanation {
    font-size: var(--text-2xs-size);
  }

  .measure-bar-slider {
    --mtl-slider-handle-halo: 0 0 0 8px var(--accent-subtle);
    --mtl-slider-handle-halo-active: 0 0 0 8px var(--accent-subtle);
  }
}

@media screen and (max-height: 500px) and (min-width: 769px) {
  .measure-map-overlay {
    flex-direction: row;
    align-items: center;
    gap: 0.8rem;
    padding: 0.55rem 0.75rem;
  }

  .measure-map-guidance {
    flex: 1 1 18rem;
  }

  .measure-flow-rail {
    flex: 1 1 12rem;
    margin-top: 0;
    padding-top: 0;
    padding-left: 0.8rem;
    border-top: 0;
    border-left: 1px solid var(--border-default);
  }

  .measure-sheet {
    padding-top: 0.1rem;
  }

  .measure-controls-card--dock {
    display: grid;
    grid-template-columns: auto minmax(18rem, 1fr);
    column-gap: 1rem;
    row-gap: 0.55rem;
    padding-top: 0.1rem;
  }

  .measure-toolbar {
    align-self: center;
  }

  .measure-radius-control {
    width: 100%;
  }

  .measure-placement-section {
    grid-column: 1 / -1;
  }
}
</style>
