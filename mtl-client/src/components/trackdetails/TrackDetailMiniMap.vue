<template>
  <div
    class="mini-map-wrapper"
    :class="{ collapsed: isCollapsed }"
    @mouseleave="clearMapInteraction"
    @pointerleave="clearMapInteraction"
  >
    <!-- Collapsed strip: just a thin bar to re-expand -->
    <div v-if="isCollapsed" class="mini-map-collapsed-strip" title="Expand map" @click="toggleCollapse">
      <i class="bi bi-chevron-down"></i>
    </div>

    <!-- Map body with floating overlay controls -->
    <div
      v-show="!isCollapsed"
      :id="mapBodyId"
      ref="mapBodyEl"
      class="mini-map-body"
      :style="{ height: mapHeight + 'px' }"
    >
      <div ref="mapEl" class="mini-map-container"></div>

      <!-- Floating collapse button overlaid on the map -->
      <button
        class="map-overlay-collapse-btn"
        :aria-label="'Collapse map'"
        title="Collapse map"
        @click.stop="toggleCollapse"
      >
        <i class="bi bi-chevron-up"></i>
      </button>

      <button
        class="map-overlay-events-btn"
        :class="{ active: showEvents }"
        :disabled="trackEvents.length === 0"
        aria-label="Toggle track events"
        title="Toggle track events"
        @click.stop="toggleEvents"
      >
        <i class="bi bi-pause-circle"></i>
        <span v-if="trackEvents.length > 0" class="event-count">{{ trackEvents.length }}</span>
      </button>

      <button
        class="map-overlay-replay-btn"
        type="button"
        :disabled="!replayEnabled"
        aria-label="Start 3D replay"
        title="Start 3D replay"
        @click.stop="emit('start-3d-replay')"
      >
        <i class="bi bi-badge-3d"></i>
        <span>3D Replay</span>
      </button>
    </div>

    <!-- Bottom-sheet style resize handle -->
    <div
      v-show="!isCollapsed"
      ref="resizeHandleEl"
      class="resize-handle"
      role="separator"
      tabindex="0"
      aria-label="Resize activity map"
      aria-orientation="horizontal"
      :aria-controls="mapBodyId"
      :aria-valuemin="MIN_HEIGHT"
      :aria-valuemax="MAX_HEIGHT"
      :aria-valuenow="Math.round(mapHeight)"
      :aria-valuetext="`${Math.round(mapHeight)} pixel map height`"
      title="Drag, tap, or use arrow keys to resize the map"
      @click="onResizeHandleClick"
      @keydown="onResizeHandleKeydown"
    >
      <span class="resize-grip"></span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { watch, ref, computed, nextTick, onBeforeUnmount, onMounted, markRaw } from 'vue';
import { useVerticalResizeDrag } from '@/composables/useVerticalResizeDrag';
import * as maplibregl from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import { useTrackMapSync, type TrackPoint } from '@/composables/useTrackMapSync';
import { useChartSync } from '@/composables/useChartSync';
import { fetchMapConfig } from '@/utils/mapConfigService';
import { resolveConfiguredMapStyle } from '@/components/map/mapStyleResolver';
import { installMissingStyleImageResolver } from '@/utils/maplibreStyleImages';
import { TRACK_COLOR } from '@/utils/trackColors';
import {
  TRACK_DETAIL_MINI_MAP_HEIGHT_DEFAULT,
  TRACK_DETAIL_MINI_MAP_HEIGHT_MAX,
  TRACK_DETAIL_MINI_MAP_HEIGHT_MIN,
  TRACK_DETAIL_MINI_MAP_HEIGHT_MOBILE_DEFAULT,
  useTrackDetailsPreferencesStore,
} from '@/stores/trackDetailsPreferencesStore';
import { useMapSettingsStore } from '@/stores/mapSettingsStore';
import {
  formatDateAndTimeWithSeconds,
  formatDistanceSmart,
  formatDurationSmart,
  formatElevation,
  formatNumber,
  formatSpeed,
} from '@/utils/Utils';
import type { GpsTrackEvent } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import { unwrapLngLatCoordinates } from '@/components/map/mapGeometry';
import {
  numericRangeForItems,
  projectClickToTrackLine,
  valueAtFraction,
  type TrackLineProjection,
} from '@/components/map/trackLineHitTest';
import { createTrackPointPopup, type TrackPointPopupRow } from '@/components/map/trackPointPopup';
import {
  formatTrackEventDateTime as eventTimeLabel,
  trackEventKey as eventKey,
  trackEventKeysEqual as eventKeysEqual,
  trackEventTimeMs as eventTimeMs,
  trackEventTypeLabel as eventTypeLabel,
} from '@/utils/trackEvents';
import { VIZ_ORANGE_COLOR, VIZ_ORANGE_STRONG_COLOR } from '@/utils/visualizationColors';
import type { TrackMediaDto } from '@/repositories/mediaRepository';
import { resolveMediaPositionMarkerStyle } from '@/components/map/mediaPositionMarkerStyle';
import { isVideoMedia } from '@/utils/mediaKind';

const MIN_HEIGHT = TRACK_DETAIL_MINI_MAP_HEIGHT_MIN;
const MAX_HEIGHT = TRACK_DETAIL_MINI_MAP_HEIGHT_MAX;
const DEFAULT_HEIGHT = TRACK_DETAIL_MINI_MAP_HEIGHT_DEFAULT;
const DEFAULT_HEIGHT_MOBILE = TRACK_DETAIL_MINI_MAP_HEIGHT_MOBILE_DEFAULT;
const MINI_MAP_RESIZE_ACTIVATION_THRESHOLD_PX = 0;
const MINI_MAP_RESIZE_KEYBOARD_STEP_PX = 20;
const MINI_MAP_RESIZE_PRESET_TOLERANCE_PX = 1;

const SOURCE_ID = 'detail-track';
const TRACK_LAYER = 'detail-track-layer';
const EVENT_SOURCE = 'detail-events';
const EVENT_LAYER = 'detail-events-layer';
const SELECTED_EVENT_SOURCE = 'detail-selected-event';
const SELECTED_EVENT_HALO_LAYER = 'detail-selected-event-halo-layer';
const SELECTED_EVENT_CORE_LAYER = 'detail-selected-event-core-layer';
const EVENT_ICON_ID = 'detail-stop-event-diamond';
const EVENT_ICON_LOGICAL_SIZE = 20;
const EVENT_ICON_DIAMOND_SIZE = 13;
const EVENT_ICON_CORNER_RADIUS = 2.5;
const EVENT_ICON_STROKE_WIDTH = 1;
const DEFAULT_DEVICE_PIXEL_RATIO = 1;
const STOP_EVENT_MARKER_FILL = VIZ_ORANGE_COLOR;
const STOP_EVENT_MARKER_STROKE = '#7c2d12';
const SELECTED_EVENT_HALO_COLOR = STOP_EVENT_MARKER_FILL;
const SELECTED_EVENT_CORE_COLOR = '#fff7ed';
const SELECTED_EVENT_CORE_STROKE = VIZ_ORANGE_STRONG_COLOR;
const METERS_PER_KILOMETER = 1000;
const SEGMENT_CLICK_TOLERANCE_PX = 12;
const SEGMENT_CLICK_TOLERANCE_METERS = 120;
const TRACK_FIT_BOUNDS_PADDING_PX = 20;
const TRACK_FIT_BOUNDS_DURATION_MS = 0;
// Hover snap tolerance expressed in screen pixels. Converting this to meters at the
// current zoom keeps snapping equally forgiving whether zoomed in or out (a fixed
// metric radius becomes sub-pixel when zoomed out, so the cursor would almost never
// snap to the line).
const HOVER_SNAP_TOLERANCE_PX = 18;
// Web Mercator ground resolution (meters per pixel) at the equator, zoom 0.
const MERCATOR_METERS_PER_PIXEL_Z0 = 156543.03392804097;
// Visual style for the DOM hover marker (mirrors the previous circle layer).
const HOVER_MARKER_DIAMETER_PX = 14;
const HOVER_MARKER_FILL = '#e63946';
const HOVER_MARKER_STROKE = '#fff';
const PHOTO_MARKER_BORDER_RADIUS = '999px';
type EventFeatureProperties = {
  duration?: string;
  eventKey?: string | number;
  label?: string;
  time?: string;
};
type PendingMapHover = {
  lat: number;
  lng: number;
};
type ClickPointTarget = {
  point: TrackPoint;
  anchor: [number, number];
};

function isMobile() {
  return window.innerWidth <= 768;
}

defineOptions({
  name: 'TrackDetailMiniMap',
});

const props = withDefaults(
  defineProps<{
    gpsTrackId: number;
    replayEnabled?: boolean;
    trackEvents?: GpsTrackEvent[];
    trackMedia?: TrackMediaDto[];
    trackCoordinates?: number[][];
    mediaInteractionEnabled?: boolean;
    selectedEventKey?: string | number | null;
    selectedMediaId?: number | null;
    highlightedMediaId?: number | null;
  }>(),
  {
    trackEvents: () => [],
    trackMedia: () => [],
    trackCoordinates: () => [],
    mediaInteractionEnabled: false,
    selectedEventKey: null,
    selectedMediaId: null,
    highlightedMediaId: null,
  }
);

const emit = defineEmits<{
  'select-event': [key: string | number | null];
  'select-media': [mediaId: number];
  'clear-selection': [];
  'start-3d-replay': [];
}>();

const mapEl = ref<HTMLElement | null>(null);
const mapBodyEl = ref<HTMLElement | null>(null);
const isCollapsed = ref(false);
const preferencesStore = useTrackDetailsPreferencesStore();
const mapSettingsStore = useMapSettingsStore();
const mapHeight = ref(preferencesStore.ensureMiniMapHeight(isMobile() ? DEFAULT_HEIGHT_MOBILE : DEFAULT_HEIGHT));
const mapBodyId = computed(() => `track-detail-mini-map-${props.gpsTrackId}`);
const showEvents = ref(props.trackEvents.length > 0);

let map: maplibregl.Map | null = null;
let mapReady = false;
let hoverMarker: maplibregl.Marker | null = null;
let photoMarkers: maplibregl.Marker[] = [];
const photoMarkerElements = new Map<number, HTMLButtonElement>();

let eventLayerRetryScheduled = false;
let eventLayerRetryMap: maplibregl.Map | null = null;
let eventLayerRetryHandler: (() => void) | null = null;
let pendingMapHover: PendingMapHover | null = null;
let mapHoverFrame: number | null = null;
let lastSyncedHoverPointIndex: number | null = null;
let pointPopup: maplibregl.Popup | null = null;
let canvasLeaveHandler: (() => void) | null = null;

// ── Resize via native pointer drag ──
const resizeHandleEl = ref<HTMLElement | null>(null);
let resizeStartHeight = 0;

function clampMapHeight(height: number): number {
  return Math.min(MAX_HEIGHT, Math.max(MIN_HEIGHT, height));
}

function beginMiniMapResize(): void {
  resizeStartHeight = mapHeight.value;
}

function updateMiniMapResize(deltaY: number): void {
  mapHeight.value = clampMapHeight(resizeStartHeight + deltaY);
  // Keep the container and MapLibre canvas in the same pointer-move update.
  // Deferring or throttling this resize makes touch dragging visibly lag.
  map?.resize();
}

function commitMiniMapResize(): void {
  mapHeight.value = preferencesStore.setMiniMapHeight(mapHeight.value);
  nextTick(() => map?.resize());
}

function setMiniMapHeight(height: number): void {
  mapHeight.value = preferencesStore.setMiniMapHeight(clampMapHeight(height));
  nextTick(() => map?.resize());
}

function cycleMiniMapHeight(): void {
  const defaultHeight = isMobile() ? DEFAULT_HEIGHT_MOBILE : DEFAULT_HEIGHT;
  const presets = [MIN_HEIGHT, defaultHeight, MAX_HEIGHT];
  const nextHeight = presets.find((height) => height > mapHeight.value + MINI_MAP_RESIZE_PRESET_TOLERANCE_PX);
  setMiniMapHeight(nextHeight ?? presets[0]);
}

function onResizeHandleClick(event: MouseEvent): void {
  if (consumeResizeHandleClickAfterDrag(event)) return;
  cycleMiniMapHeight();
}

function onResizeHandleKeydown(event: KeyboardEvent): void {
  let nextHeight: number | null = null;
  if (event.key === 'ArrowUp') nextHeight = mapHeight.value - MINI_MAP_RESIZE_KEYBOARD_STEP_PX;
  else if (event.key === 'ArrowDown') nextHeight = mapHeight.value + MINI_MAP_RESIZE_KEYBOARD_STEP_PX;
  else if (event.key === 'Home') nextHeight = MIN_HEIGHT;
  else if (event.key === 'End') nextHeight = MAX_HEIGHT;
  else if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault();
    cycleMiniMapHeight();
    return;
  }

  if (nextHeight == null) return;
  event.preventDefault();
  setMiniMapHeight(nextHeight);
}

const { consumeClickAfterDrag: consumeResizeHandleClickAfterDrag } = useVerticalResizeDrag(
  resizeHandleEl,
  {
    onStart: beginMiniMapResize,
    onResize: updateMiniMapResize,
    onEnd: commitMiniMapResize,
  },
  {
    activationThresholdPx: MINI_MAP_RESIZE_ACTIVATION_THRESHOLD_PX,
  }
);

defineExpose({
  beginMiniMapResize,
  updateMiniMapResize,
  commitMiniMapResize,
});

// ── Map init ──────────────────────────────────────────────────────────────
const {
  pinnedPoint,
  hoverPoint,
  trackPointVersion,
  getTrackPoints,
  findPointByDistance,
  findPointByIndex,
  findPointByCanonicalIndex,
  findPointByLatLng,
  findPointByTimestamp,
  setPinnedPoint,
  setHoverPoint,
  clearHover,
} = useTrackMapSync();

const { showChartsAtPoint, clearChartCrosshairs } = useChartSync();

async function initMap() {
  if (!mapEl.value) return;

  const config = await fetchMapConfig();
  mapSettingsStore.hydrate();
  const { style } = resolveConfiguredMapStyle({
    config,
    theme: 'light',
    mapSourceMode: mapSettingsStore.mapSourceMode,
  });

  map = markRaw(
    new maplibregl.Map({
      container: mapEl.value,
      style,
      center: [8.505778, 47.5605],
      zoom: 10,
      attributionControl: false,
      doubleClickZoom: false,
    })
  );

  installMissingStyleImageResolver(map);

  await new Promise<void>((resolve) => {
    if (map!.loaded()) resolve();
    else map!.on('load', () => resolve());
  });
  mapReady = true;

  // Force a resize in case the container was zero-sized during the bottom-sheet
  // open animation (MapLibre reads the container dimensions at construction time).
  map.resize();

  drawEvents();
  drawTrack();
  drawPhotoMarkers();

  // Restore any point that was set while the map was initializing.
  updateMarker(pinnedPoint.value ?? hoverPoint.value);
  drawEvents();

  map.on('click', (e: maplibregl.MapMouseEvent) => {
    if (clickedEventFeature(e)) return;
    if (!props.mediaInteractionEnabled) {
      const target = findClickPointTarget(e);
      if (target) {
        setPinnedPoint(target.point);
        showChartsAtPoint(target.point);
        showPointPopup(target.point, target.anchor);
        return;
      }
    }
    clearMapSelection();
  });

  map.on('mousemove', (e: maplibregl.MapMouseEvent) => {
    if (props.mediaInteractionEnabled) return;
    scheduleMapHover(e.lngLat.lat, e.lngLat.lng);
  });

  map.on('mouseout', clearMapInteraction);
  canvasLeaveHandler = clearMapInteraction;
  map.getCanvas().addEventListener('mouseleave', clearMapInteraction);

  map.on('dblclick', clearMapSelection);

  const onEventFeatureClick = (e: maplibregl.MapLayerMouseEvent) => {
    const feature = e.features?.[0];
    if (!feature || !map) return;
    const coordinates = (feature.geometry as GeoJSON.Point).coordinates.slice() as [number, number];
    const properties = (feature.properties ?? {}) as EventFeatureProperties;
    const featureEventKey = properties.eventKey;
    const label = properties.label ?? 'Event';
    const time = properties.time ?? '';
    const duration = properties.duration ?? '';
    emit('select-event', eventKeysEqual(props.selectedEventKey, featureEventKey) ? null : (featureEventKey ?? null));
    new maplibregl.Popup({ closeButton: true, closeOnClick: true })
      .setLngLat(coordinates)
      .setHTML(
        `<strong>${escapeHtml(label)}</strong><br>${escapeHtml(time)}${duration ? `<br>${escapeHtml(duration)}` : ''}`
      )
      .addTo(map);
  };

  map.on('click', EVENT_LAYER, onEventFeatureClick);
  map.on('click', SELECTED_EVENT_HALO_LAYER, onEventFeatureClick);
  map.on('click', SELECTED_EVENT_CORE_LAYER, onEventFeatureClick);

  map.on('mouseenter', EVENT_LAYER, () => {
    if (map) map.getCanvas().style.cursor = 'pointer';
  });

  map.on('mouseleave', EVENT_LAYER, () => {
    if (map) map.getCanvas().style.cursor = '';
  });
}

function clickedEventFeature(e: maplibregl.MapMouseEvent): boolean {
  if (!map) return false;
  const layers = [EVENT_LAYER, SELECTED_EVENT_HALO_LAYER, SELECTED_EVENT_CORE_LAYER].filter((layerId) =>
    map?.getLayer(layerId)
  );
  if (layers.length === 0) return false;
  return map.queryRenderedFeatures(e.point, { layers }).length > 0;
}

function clearPointPopup() {
  if (pointPopup) {
    pointPopup.remove();
    pointPopup = null;
  }
}

function clearMapSelection() {
  setPinnedPoint(null);
  clearMapInteraction();
  clearPointPopup();
  emit('select-event', null);
  emit('clear-selection');
}

function clearPhotoMarkers() {
  for (const marker of photoMarkers) marker.remove();
  photoMarkers = [];
  photoMarkerElements.clear();
}

function drawPhotoMarkers() {
  clearPhotoMarkers();
  if (!map || !mapReady) return;

  for (const item of props.trackMedia) {
    const coordinates = trackMediaCoordinates(item);
    if (!coordinates) continue;
    const label = trackMediaMarkerLabel(item);
    const button = document.createElement('button');
    button.type = 'button';
    button.className = ['mini-map-photo-marker', trackMediaMarkerClass(item)].filter(Boolean).join(' ');
    button.dataset.mediaId = String(item.id);
    button.title = label;
    button.setAttribute('aria-label', label);
    const markerStyle = resolveMediaPositionMarkerStyle(item.positionOrigin, item.estimatedPosition);
    button.style.setProperty('--media-marker-fill', markerStyle.fill);
    button.style.setProperty('--media-marker-color', markerStyle.foreground);
    button.style.setProperty('--media-marker-border', markerStyle.border);
    button.style.setProperty('--media-marker-border-radius', PHOTO_MARKER_BORDER_RADIUS);
    const icon = document.createElement('i');
    icon.className = isVideoMedia(item.fileName, item.mediaKind) ? 'bi bi-camera-video-fill' : 'bi bi-camera-fill';
    icon.setAttribute('aria-hidden', 'true');
    button.append(icon);
    button.addEventListener('click', (event) => {
      event.preventDefault();
      event.stopPropagation();
      if (!props.mediaInteractionEnabled) return;
      emit('select-media', item.id);
    });
    photoMarkerElements.set(item.id, button);
    photoMarkers.push(new maplibregl.Marker({ element: button }).setLngLat(coordinates).addTo(map));
  }
  syncPhotoMarkerInteractivity();
  syncPhotoMarkerStates();
}

function syncPhotoMarkerInteractivity() {
  for (const element of photoMarkerElements.values()) {
    element.disabled = !props.mediaInteractionEnabled;
    element.classList.toggle('mini-map-photo-marker--interactive', props.mediaInteractionEnabled);
  }
}

function syncPhotoMarkerStates() {
  for (const [mediaId, element] of photoMarkerElements) {
    element.classList.toggle('mini-map-photo-marker--selected', mediaId === props.selectedMediaId);
    element.classList.toggle('mini-map-photo-marker--highlighted', mediaId === props.highlightedMediaId);
  }
}

function trackMediaCoordinates(item: TrackMediaDto): [number, number] | null {
  const usesExifPosition = item.positionOrigin === 'EXIF_EMBEDDED';
  const lat =
    item.resolvedLat ?? item.manualLat ?? (usesExifPosition ? item.originalLat : (item.routeLat ?? item.originalLat));
  const lng =
    item.resolvedLng ?? item.manualLng ?? (usesExifPosition ? item.originalLng : (item.routeLng ?? item.originalLng));
  if (lat == null || lng == null || !Number.isFinite(lat) || !Number.isFinite(lng)) return null;
  return [lng, lat];
}

function trackMediaMarkerClass(item: TrackMediaDto): string {
  if (item.positionOrigin === 'USER_ASSIGNED') return 'mini-map-photo-marker--manual';
  if (item.positionOrigin === 'TRACK_INTERPOLATED' || item.estimatedPosition) {
    return 'mini-map-photo-marker--estimated';
  }
  if (item.positionOrigin === 'EXIF_EMBEDDED') return 'mini-map-photo-marker--gps';
  return 'mini-map-photo-marker--unknown';
}

function trackMediaMarkerLabel(item: TrackMediaDto): string {
  const name = item.fileName?.trim() || `Media ${item.id}`;
  const source =
    item.positionOrigin === 'USER_ASSIGNED'
      ? 'Location set by you'
      : item.positionOrigin === 'TRACK_INTERPOLATED' || item.estimatedPosition
        ? `Estimated from ${isVideoMedia(item.fileName, item.mediaKind) ? 'video' : 'photo'} time and activity track`
        : item.positionOrigin === 'EXIF_EMBEDDED'
          ? isVideoMedia(item.fileName, item.mediaKind)
            ? 'Video GPS'
            : 'Photo GPS'
          : 'Position source unknown';
  return `${name}. ${source}${item.ambiguousMatch ? '. Multiple activities matched' : ''}`;
}

function showPointPopup(point: TrackPoint, anchor: [number, number] = [point.lng, point.lat]) {
  if (!map) return;
  clearPointPopup();
  pointPopup = createTrackPointPopup({
    map,
    lngLat: anchor,
    title: 'Track point',
    rows: pointPopupRows(point),
    closeOnClick: true,
  });
}

function findClickPointTarget(e: maplibregl.MapMouseEvent): ClickPointTarget | null {
  const directPoint = findPointByLatLng(e.lngLat.lat, e.lngLat.lng, currentHoverSnapMeters(e.lngLat.lat));
  if (directPoint) return { point: directPoint, anchor: [directPoint.lng, directPoint.lat] };

  // Sparse simplified tracks can draw a visible line with only start/end vertices.
  // On click, hit-test that rendered line and resolve the projected fraction back
  // to the nearest real chart/canonical point without densifying map state.
  const projection = findRenderedLineProjection(e);
  if (!projection) return null;

  const projectedPoint = findPointForTrackFraction(projection.fraction);
  if (!projectedPoint) return null;

  return {
    point: {
      ...projectedPoint,
      lng: projection.anchor[0],
      lat: projection.anchor[1],
    },
    anchor: projection.anchor,
  };
}

function findRenderedLineProjection(e: maplibregl.MapMouseEvent): TrackLineProjection | null {
  const coordinates = trackLineCoordinates();
  if (coordinates.length < 2) return null;

  return projectClickToTrackLine({
    map,
    clickPoint: e.point,
    lngLat: e.lngLat,
    coordinates,
    pixelTolerance: SEGMENT_CLICK_TOLERANCE_PX,
    meterTolerance: SEGMENT_CLICK_TOLERANCE_METERS,
  });
}

function findPointForTrackFraction(fraction: number): TrackPoint | null {
  const points = getTrackPoints();
  if (points.length === 0) return null;

  const distanceRange = numericRangeForItems(points, (point) => point.distanceKm);
  if (distanceRange && distanceRange.max > distanceRange.min) {
    return findPointByDistance(valueAtFraction(distanceRange, fraction));
  }

  const canonicalRange = numericRangeForItems(points, (point) => point.canonicalPointIndex);
  if (canonicalRange && canonicalRange.max > canonicalRange.min) {
    return findPointByCanonicalIndex(valueAtFraction(canonicalRange, fraction));
  }

  const pointIndexRange = numericRangeForItems(points, (point) => point.pointIndex);
  if (pointIndexRange) {
    return findPointByIndex(valueAtFraction(pointIndexRange, fraction));
  }

  return points[0] ?? null;
}

function pointPopupRows(point: TrackPoint): TrackPointPopupRow[] {
  return [
    { label: 'Point', value: formatNumber(displayPointIndex(point) + 1, 0) },
    { label: 'Time', value: formatPointTime(point.timestamp) },
    { label: 'Distance', value: formatDistanceSmart(point.distanceKm * METERS_PER_KILOMETER) },
    { label: 'Elevation', value: point.altitude == null ? '—' : formatElevation(point.altitude, 1) },
    { label: 'Speed', value: formatPointSpeed(point) },
    { label: 'Elapsed', value: formatElapsed(point) },
  ];
}

function formatPointTime(timestamp: number): string {
  if (!Number.isFinite(timestamp) || timestamp <= 0) return '—';
  return formatDateAndTimeWithSeconds(new Date(timestamp));
}

function displayPointIndex(point: TrackPoint): number {
  return Number.isFinite(point.canonicalPointIndex) ? (point.canonicalPointIndex as number) : point.pointIndex;
}

function formatElapsed(point: TrackPoint): string {
  const first = getTrackPoints()[0];
  if (!first || !Number.isFinite(first.timestamp) || !Number.isFinite(point.timestamp)) return '—';
  const elapsed = Math.max(0, point.timestamp - first.timestamp);
  return formatDurationSmart(elapsed);
}

function formatPointSpeed(point: TrackPoint): string {
  const points = getTrackPoints();
  const index = points.findIndex((candidate) => sameTrackPoint(candidate, point));
  const prev = index > 0 ? points[index - 1] : null;
  const next = index >= 0 && index < points.length - 1 ? points[index + 1] : null;
  const a = prev ?? point;
  const b = next ?? point;
  const dtMs = b.timestamp - a.timestamp;
  const dKm = b.distanceKm - a.distanceKm;
  if (!Number.isFinite(dtMs) || dtMs <= 0 || !Number.isFinite(dKm) || dKm < 0) return '—';
  return formatSpeed(dKm / (dtMs / 3_600_000), 1);
}

function sameTrackPoint(a: TrackPoint, b: TrackPoint): boolean {
  if (a.canonicalPointIndex != null && b.canonicalPointIndex != null) {
    return a.canonicalPointIndex === b.canonicalPointIndex;
  }
  return a.pointIndex === b.pointIndex;
}

function drawTrack() {
  if (!map || !mapReady) return;
  const coordinates = trackLineCoordinates();
  if (coordinates.length === 0) {
    clearTrack();
    return;
  }

  const geojson: GeoJSON.FeatureCollection = {
    type: 'FeatureCollection',
    features: [
      {
        type: 'Feature',
        geometry: { type: 'LineString', coordinates },
        properties: {},
      },
    ],
  };

  const source = map.getSource(SOURCE_ID) as maplibregl.GeoJSONSource | undefined;
  if (source) {
    source.setData(geojson);
  } else {
    map.addSource(SOURCE_ID, { type: 'geojson', data: geojson });
    map.addLayer({
      id: TRACK_LAYER,
      type: 'line',
      source: SOURCE_ID,
      layout: { 'line-join': 'round', 'line-cap': 'round' },
      paint: { 'line-color': TRACK_COLOR, 'line-width': 4, 'line-opacity': 0.9 },
    });
  }

  // Fit to track bounds
  const bounds = coordinates.reduce(
    (b, c) => b.extend(c as [number, number]),
    new maplibregl.LngLatBounds(coordinates[0], coordinates[0])
  );
  map.fitBounds(bounds, { padding: TRACK_FIT_BOUNDS_PADDING_PX, duration: TRACK_FIT_BOUNDS_DURATION_MS });

  // The hover marker is a DOM overlay (maplibregl.Marker) and always renders on top
  // of the canvas, so it no longer needs explicit layer reordering after drawTrack().
  if (map.getLayer(EVENT_LAYER) && map.isStyleLoaded()) {
    map.moveLayer(EVENT_LAYER);
    moveSelectedEventLayersToTop();
  } else if (map.getLayer(EVENT_LAYER)) {
    scheduleEventLayerRetry();
  }
}

function clearTrack() {
  const source = map?.getSource(SOURCE_ID) as maplibregl.GeoJSONSource | undefined;
  if (source) {
    source.setData({ type: 'FeatureCollection', features: [] });
  }
}

function trackLineCoordinates(): [number, number][] {
  const propCoordinates = props.trackCoordinates
    .map(pointToLngLat)
    .filter((point): point is [number, number] => point !== null);
  if (propCoordinates.length > 0) return unwrapLngLatCoordinates(propCoordinates);

  const syncCoordinates = getTrackPoints()
    .map((p) => [p.lng, p.lat] as [number, number])
    .filter((c) => isFinite(c[0]) && isFinite(c[1]));
  return unwrapLngLatCoordinates(syncCoordinates);
}

function cancelMapHover() {
  pendingMapHover = null;
  if (mapHoverFrame !== null) {
    window.cancelAnimationFrame(mapHoverFrame);
    mapHoverFrame = null;
  }
}

// Ground resolution (meters per screen pixel) at the current map zoom/latitude.
function mapMetersPerPixel(lat: number): number {
  const zoom = map?.getZoom() ?? 0;
  const latRad = (lat * Math.PI) / 180;
  return (MERCATOR_METERS_PER_PIXEL_Z0 * Math.cos(latRad)) / Math.pow(2, zoom);
}

// Convert the fixed pixel hover tolerance to meters at the current zoom so snapping
// stays equally forgiving when zoomed out (where a fixed metric radius is sub-pixel).
function currentHoverSnapMeters(lat: number): number {
  return HOVER_SNAP_TOLERANCE_PX * mapMetersPerPixel(lat);
}

function syncMapHover(lat: number, lng: number) {
  const pt = findPointByLatLng(lat, lng, currentHoverSnapMeters(lat));
  if (!pt) {
    clearMapHoverArtifacts();
    return;
  }
  if (lastSyncedHoverPointIndex === pt.pointIndex) return;

  lastSyncedHoverPointIndex = pt.pointIndex;
  setHoverPoint(pt);
  showChartsAtPoint(pt);
}

function clearMapHoverArtifacts() {
  lastSyncedHoverPointIndex = null;
  clearHover();
  clearChartCrosshairs();
}

function clearMapInteraction() {
  cancelMapHover();
  clearMapHoverArtifacts();
}

function scheduleMapHover(lat: number, lng: number) {
  if (props.mediaInteractionEnabled) return;
  pendingMapHover = { lat, lng };
  if (mapHoverFrame !== null) return;

  mapHoverFrame = window.requestAnimationFrame(() => {
    mapHoverFrame = null;
    const nextHover = pendingMapHover;
    pendingMapHover = null;
    if (nextHover) {
      syncMapHover(nextHover.lat, nextHover.lng);
    }
  });
}

function eventPoint(event: GpsTrackEvent): [number, number] | null {
  const startPoint = pointToLngLat(event.startPointLongLat as unknown);
  if (startPoint) return startPoint;

  const endPoint = pointToLngLat(event.endPointLongLat as unknown);
  if (endPoint) return endPoint;

  const fallbackPoint = trackPointForEvent(event);
  return fallbackPoint ? [fallbackPoint.lng, fallbackPoint.lat] : null;
}

function trackPointForEvent(event: GpsTrackEvent): TrackPoint | null {
  const indexedPoint = trackPointByEventIndex(event);
  if (indexedPoint) return indexedPoint;

  const timestampMs = eventTimeMs(event.startTimestamp);
  if (timestampMs > 0) {
    const timestampPoint = findPointByTimestamp(timestampMs);
    if (timestampPoint) return timestampPoint;
  }

  const distanceM = event.startDistanceInMeter;
  if (distanceM != null && Number.isFinite(distanceM)) {
    return findPointByDistance(distanceM / METERS_PER_KILOMETER);
  }

  return null;
}

function trackPointByEventIndex(event: GpsTrackEvent): TrackPoint | null {
  const startIndex = event.startPointIndex;
  const endIndex = event.endPointIndex;
  const targetIndex =
    startIndex != null && endIndex != null && Number.isFinite(startIndex) && Number.isFinite(endIndex)
      ? (startIndex + endIndex) / 2
      : (startIndex ?? endIndex);
  if (targetIndex == null || !Number.isFinite(targetIndex)) return null;

  return findPointByIndex(targetIndex);
}

function pointToLngLat(point: unknown): [number, number] | null {
  if (!point || typeof point !== 'object') return null;

  if (Array.isArray(point) && point.length >= 2) {
    const lng = Number(point[0]);
    const lat = Number(point[1]);
    return Number.isFinite(lng) && Number.isFinite(lat) ? [lng, lat] : null;
  }

  const p = point as {
    coordinates?: unknown;
    coordinate?: { x?: unknown; y?: unknown };
    x?: unknown;
    y?: unknown;
  };

  if (Array.isArray(p.coordinates) && p.coordinates.length >= 2) {
    const first = p.coordinates[0];
    const second = p.coordinates[1];
    if (typeof first === 'number' && typeof second === 'number') {
      return [first, second];
    }
    if (first && typeof first === 'object') {
      const c = first as { x?: unknown; y?: unknown };
      const lng = Number(c.x);
      const lat = Number(c.y);
      return Number.isFinite(lng) && Number.isFinite(lat) ? [lng, lat] : null;
    }
  }

  if (p.coordinate) {
    const lng = Number(p.coordinate.x);
    const lat = Number(p.coordinate.y);
    if (Number.isFinite(lng) && Number.isFinite(lat)) return [lng, lat];
  }

  const lng = Number(p.x);
  const lat = Number(p.y);
  return Number.isFinite(lng) && Number.isFinite(lat) ? [lng, lat] : null;
}

function eventDurationLabel(seconds: number | undefined): string {
  if (seconds == null || !Number.isFinite(seconds)) return '';
  const rounded = Math.round(seconds);
  const mins = Math.floor(rounded / 60);
  const secs = rounded % 60;
  if (mins <= 0) return `${secs}s`;
  return `${mins}m ${secs.toString().padStart(2, '0')}s`;
}

function escapeHtml(value: string): string {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;');
}

function buildEventGeojson(): GeoJSON.FeatureCollection<GeoJSON.Point> {
  const features: GeoJSON.Feature<GeoJSON.Point>[] = [];
  if (showEvents.value) {
    for (const event of props.trackEvents) {
      const point = eventPoint(event);
      if (!point) continue;
      features.push({
        type: 'Feature',
        geometry: { type: 'Point', coordinates: point },
        properties: {
          label: eventTypeLabel(event.eventType),
          time: eventTimeLabel(event.startTimestamp),
          duration: eventDurationLabel(event.durationInSec),
          eventKey: eventKey(event),
        },
      });
    }
  }

  return {
    type: 'FeatureCollection',
    features,
  };
}

function clearEventLayerRetry() {
  if (eventLayerRetryMap && eventLayerRetryHandler) {
    eventLayerRetryMap.off('idle', eventLayerRetryHandler);
    eventLayerRetryMap.off('load', eventLayerRetryHandler);
  }
  eventLayerRetryScheduled = false;
  eventLayerRetryMap = null;
  eventLayerRetryHandler = null;
}

function scheduleEventLayerRetry() {
  if (!map || eventLayerRetryScheduled) return;
  const scheduledMap = map;
  const retry = () => {
    if (!eventLayerRetryScheduled || map !== scheduledMap) return;
    clearEventLayerRetry();
    drawEvents();
  };
  eventLayerRetryScheduled = true;
  eventLayerRetryMap = scheduledMap;
  eventLayerRetryHandler = retry;
  scheduledMap.once('idle', retry);
  scheduledMap.once('load', retry);
}

function addEventLayer() {
  if (!map || map.getLayer(EVENT_LAYER)) return;
  ensureEventIcon();
  map.addLayer({
    id: EVENT_LAYER,
    type: 'symbol',
    source: EVENT_SOURCE,
    layout: {
      'icon-image': EVENT_ICON_ID,
      'icon-size': 0.78,
      'icon-allow-overlap': true,
      'icon-ignore-placement': true,
    },
  });
}

function selectedEventGeojson(): GeoJSON.FeatureCollection<GeoJSON.Point> {
  const selectedEvent = props.trackEvents.find((event) => eventKeysEqual(eventKey(event), props.selectedEventKey));
  const point = selectedEvent ? eventPoint(selectedEvent) : null;

  return {
    type: 'FeatureCollection',
    features: point
      ? [
          {
            type: 'Feature',
            geometry: { type: 'Point', coordinates: point },
            properties: {
              label: eventTypeLabel(selectedEvent?.eventType),
              time: eventTimeLabel(selectedEvent?.startTimestamp),
              duration: eventDurationLabel(selectedEvent?.durationInSec),
              eventKey: selectedEvent ? eventKey(selectedEvent) : undefined,
            },
          },
        ]
      : [],
  };
}

function addSelectedEventLayers() {
  if (!map || map.getLayer(SELECTED_EVENT_HALO_LAYER)) return;
  map.addLayer({
    id: SELECTED_EVENT_HALO_LAYER,
    type: 'circle',
    source: SELECTED_EVENT_SOURCE,
    paint: {
      'circle-radius': 17,
      'circle-color': SELECTED_EVENT_HALO_COLOR,
      'circle-opacity': 0.24,
      'circle-stroke-width': 2,
      'circle-stroke-color': SELECTED_EVENT_HALO_COLOR,
      'circle-stroke-opacity': 0.7,
    },
  });
  map.addLayer({
    id: SELECTED_EVENT_CORE_LAYER,
    type: 'circle',
    source: SELECTED_EVENT_SOURCE,
    paint: {
      'circle-radius': 8,
      'circle-color': SELECTED_EVENT_CORE_COLOR,
      'circle-stroke-width': 3,
      'circle-stroke-color': SELECTED_EVENT_CORE_STROKE,
    },
  });
}

function moveSelectedEventLayersToTop() {
  if (!map) return;
  if (map.getLayer(EVENT_LAYER)) map.moveLayer(EVENT_LAYER);
  if (map.getLayer(SELECTED_EVENT_HALO_LAYER)) map.moveLayer(SELECTED_EVENT_HALO_LAYER);
  if (map.getLayer(SELECTED_EVENT_CORE_LAYER)) map.moveLayer(SELECTED_EVENT_CORE_LAYER);
}

function drawSelectedEvent() {
  if (!map) return;
  const geojson = selectedEventGeojson();
  const source = map.getSource(SELECTED_EVENT_SOURCE) as maplibregl.GeoJSONSource | undefined;
  if (source) {
    source.setData(geojson);
    if (!map.getLayer(SELECTED_EVENT_HALO_LAYER)) {
      if (!map.isStyleLoaded()) {
        scheduleEventLayerRetry();
        return;
      }
      addSelectedEventLayers();
    }
  } else {
    if (!map.isStyleLoaded()) {
      scheduleEventLayerRetry();
      return;
    }
    map.addSource(SELECTED_EVENT_SOURCE, { type: 'geojson', data: geojson });
    addSelectedEventLayers();
  }
  moveSelectedEventLayersToTop();
}

function drawEvents() {
  if (!map) return;
  const geojson = buildEventGeojson();
  const source = map.getSource(EVENT_SOURCE) as maplibregl.GeoJSONSource | undefined;
  if (source) {
    source.setData(geojson);
    if (!map.getLayer(EVENT_LAYER)) {
      if (!map.isStyleLoaded()) {
        scheduleEventLayerRetry();
        return;
      }
      addEventLayer();
    }
  } else {
    if (!map.isStyleLoaded()) {
      scheduleEventLayerRetry();
      return;
    }
    clearEventLayerRetry();
    map.addSource(EVENT_SOURCE, { type: 'geojson', data: geojson });
    addEventLayer();
  }

  if (map.getLayer(EVENT_LAYER)) {
    map.moveLayer(EVENT_LAYER);
  }
  drawSelectedEvent();
}

function ensureEventIcon() {
  if (!map || map.hasImage(EVENT_ICON_ID)) return;
  const ratio =
    Number.isFinite(window.devicePixelRatio) && window.devicePixelRatio > 0
      ? window.devicePixelRatio
      : DEFAULT_DEVICE_PIXEL_RATIO;
  const pixelSize = Math.max(1, Math.round(EVENT_ICON_LOGICAL_SIZE * ratio));
  const pixelRatio = pixelSize / EVENT_ICON_LOGICAL_SIZE;
  const canvas = document.createElement('canvas');
  canvas.width = pixelSize;
  canvas.height = pixelSize;
  const ctx = canvas.getContext('2d');
  if (!ctx) return;

  ctx.scale(pixelRatio, pixelRatio);
  ctx.translate(EVENT_ICON_LOGICAL_SIZE / 2, EVENT_ICON_LOGICAL_SIZE / 2);
  ctx.rotate(Math.PI / 4);
  ctx.fillStyle = STOP_EVENT_MARKER_FILL;
  ctx.strokeStyle = STOP_EVENT_MARKER_STROKE;
  ctx.lineWidth = EVENT_ICON_STROKE_WIDTH;
  ctx.beginPath();
  ctx.roundRect(
    -EVENT_ICON_DIAMOND_SIZE / 2,
    -EVENT_ICON_DIAMOND_SIZE / 2,
    EVENT_ICON_DIAMOND_SIZE,
    EVENT_ICON_DIAMOND_SIZE,
    EVENT_ICON_CORNER_RADIUS
  );
  ctx.fill();
  ctx.stroke();

  map.addImage(EVENT_ICON_ID, ctx.getImageData(0, 0, pixelSize, pixelSize), { pixelRatio });
}

function toggleEvents() {
  if (props.trackEvents.length === 0) return;
  showEvents.value = !showEvents.value;
  drawEvents();
}

function ensureHoverMarker(): maplibregl.Marker | null {
  if (!map) return null;
  if (hoverMarker) return hoverMarker;

  const el = document.createElement('div');
  el.style.width = `${HOVER_MARKER_DIAMETER_PX}px`;
  el.style.height = `${HOVER_MARKER_DIAMETER_PX}px`;
  el.style.borderRadius = '50%';
  el.style.background = HOVER_MARKER_FILL;
  el.style.border = `2px solid ${HOVER_MARKER_STROKE}`;
  el.style.boxSizing = 'border-box';
  // The marker must never intercept pointer events, otherwise it would swallow the
  // map mousemove stream while scrubbing along the track.
  el.style.pointerEvents = 'none';

  hoverMarker = new maplibregl.Marker({ element: el });
  return hoverMarker;
}

function updateMarker(point: TrackPoint | null) {
  // The hover marker is a DOM overlay (maplibregl.Marker), not a GeoJSON circle
  // layer.  Moving it via setLngLat() only updates a CSS transform on the marker
  // element and does NOT trigger a WebGL repaint of the map.  The previous
  // implementation called GeoJSONSource.setData() on every hover, which forced
  // MapLibre to re-render the entire style — including the full track line — on
  // each animation frame.  That repaint dominated CPU time when zoomed out (the
  // whole large track is in view) and was the real cause of the sluggish scrub.
  if (!map) return;

  if (props.mediaInteractionEnabled || !point) {
    hoverMarker?.remove();
    return;
  }

  const marker = ensureHoverMarker();
  if (!marker) return;
  marker.setLngLat([point.lng, point.lat]).addTo(map);
}

watch(
  [pinnedPoint, hoverPoint],
  ([pinned, hover]) => {
    // Hover takes priority over pinned while actively scrubbing.
    // When hover ends (mouse leaves), hoverPoint becomes null and pinned is shown.
    updateMarker(hover ?? pinned);
  },
  { flush: 'sync' }
);

function toggleCollapse() {
  isCollapsed.value = !isCollapsed.value;
  if (!isCollapsed.value) {
    nextTick(() => {
      map?.resize();
      // Re-fit to track if we have one
      const coordinates = trackLineCoordinates();
      if (coordinates.length > 0) {
        const bounds = coordinates.reduce(
          (b, c) => b.extend(c as [number, number]),
          new maplibregl.LngLatBounds(coordinates[0], coordinates[0])
        );
        map?.fitBounds(bounds, { padding: TRACK_FIT_BOUNDS_PADDING_PX, duration: TRACK_FIT_BOUNDS_DURATION_MS });
      }
    });
  }
}

const mountMap = () => {
  nextTick(() => {
    initMap();
  });
};

function isTrackHoverSurface(target: EventTarget | null): boolean {
  if (!(target instanceof Element)) return false;
  return Boolean(mapBodyEl.value?.contains(target) || target.closest('.highcharts-container'));
}

function clearMapInteractionOutsideSurfaces(event: MouseEvent | PointerEvent) {
  if (isTrackHoverSurface(event.target)) return;
  clearMapInteraction();
}

onBeforeUnmount(() => {
  document.removeEventListener('mousemove', clearMapInteractionOutsideSurfaces, true);
  document.removeEventListener('pointermove', clearMapInteractionOutsideSurfaces, true);
  clearEventLayerRetry();
  cancelMapHover();
  clearPointPopup();
  if (map && canvasLeaveHandler) {
    map.getCanvas().removeEventListener('mouseleave', canvasLeaveHandler);
    canvasLeaveHandler = null;
  }
  if (hoverMarker) {
    hoverMarker.remove();
    hoverMarker = null;
  }
  clearPhotoMarkers();
  if (map) {
    map.remove();
    map = null;
  }
  mapReady = false;
});

function redrawTrack() {
  drawTrack();
  drawEvents();
}

watch(
  () => props.trackEvents,
  () => {
    if (props.trackEvents.length === 0) {
      showEvents.value = false;
    } else {
      showEvents.value = true;
    }
    drawEvents();
  },
  { deep: true }
);

watch(
  () => props.trackMedia,
  () => drawPhotoMarkers(),
  { deep: true }
);

watch(
  () => props.mediaInteractionEnabled,
  (enabled) => {
    syncPhotoMarkerInteractivity();
    if (enabled) {
      clearMapInteraction();
      clearPointPopup();
    }
    updateMarker(enabled ? null : (hoverPoint.value ?? pinnedPoint.value));
  }
);

watch(
  () => props.selectedMediaId,
  () => syncPhotoMarkerStates()
);

watch(
  () => props.highlightedMediaId,
  () => syncPhotoMarkerStates()
);

watch(
  () => props.trackCoordinates,
  () => {
    drawTrack();
  }
);

watch(trackPointVersion, () => {
  if (props.trackCoordinates.length === 0) {
    drawTrack();
  }
  drawEvents();
});

watch(
  () => props.selectedEventKey,
  (selectedKey) => {
    const wasShowingEvents = showEvents.value;
    if (selectedKey != null && props.trackEvents.length > 0) {
      showEvents.value = true;
    }
    if (!wasShowingEvents && showEvents.value) {
      drawEvents();
      return;
    }
    drawSelectedEvent();
  }
);

onMounted(() => {
  document.addEventListener('mousemove', clearMapInteractionOutsideSurfaces, true);
  document.addEventListener('pointermove', clearMapInteractionOutsideSurfaces, true);
  mountMap();
});

watch(
  () => props.gpsTrackId,
  () => {
    redrawTrack();
  }
);
</script>

<style scoped>
.mini-map-wrapper {
  width: 100%;
  background: transparent;
  flex-shrink: 0;
}

.mini-map-collapsed-strip {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 14px;
  cursor: pointer;
  background: var(--surface-hover);
  border-bottom: 1px solid var(--border-default);
  color: var(--text-faint);
  font-size: var(--text-xs-size);
  user-select: none;
}
.mini-map-collapsed-strip:hover {
  background: var(--accent-bg);
  color: var(--text-secondary);
}

.map-overlay-collapse-btn {
  position: absolute;
  top: 6px;
  right: 6px;
  z-index: 1001;
  background: var(--surface-glass-light);
  border: 1px solid var(--border-medium);
  border-radius: 4px;
  padding: 2px 6px;
  cursor: pointer;
  font-size: var(--text-sm-size);
  color: var(--text-muted);
  line-height: var(--text-sm-lh);
  backdrop-filter: blur(2px);
}
.map-overlay-collapse-btn:hover {
  background: var(--accent-bg);
  border-color: var(--border-hover);
}

.map-overlay-events-btn {
  position: absolute;
  top: 6px;
  left: 6px;
  z-index: 1001;
  min-width: 32px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  background: var(--surface-glass-light);
  border: 1px solid var(--border-medium);
  border-radius: 4px;
  cursor: pointer;
  font-size: var(--text-sm-size);
  color: var(--text-muted);
  line-height: var(--text-sm-lh);
  backdrop-filter: blur(2px);
}

.map-overlay-events-btn:hover:not(:disabled),
.map-overlay-events-btn.active {
  background: var(--warning-bg);
  border-color: var(--warning);
  color: var(--warning-text);
}

.map-overlay-events-btn:disabled {
  opacity: 0.45;
  cursor: default;
}

.map-overlay-replay-btn {
  position: absolute;
  right: 12px;
  bottom: 12px;
  z-index: 1001;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 2.15rem;
  gap: 0.4rem;
  border: 1px solid var(--border-medium);
  border-radius: 999px;
  background: var(--surface-glass-heavy);
  color: var(--text-primary);
  cursor: pointer;
  font-size: var(--text-xs-size);
  font-weight: 700;
  line-height: var(--text-xs-lh);
  padding: 0.35rem 0.8rem;
  box-shadow: var(--shadow-sm);
  backdrop-filter: blur(2px);
}

.map-overlay-replay-btn:hover:not(:disabled) {
  border-color: var(--accent-muted);
  background: var(--accent-subtle);
}

.map-overlay-replay-btn:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.event-count {
  min-width: 1.25em;
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
  font-weight: 700;
}

.mini-map-body {
  position: relative;
  display: flex;
  flex-direction: column;
}

.mini-map-container {
  width: 100%;
  flex: 1 1 auto;
  min-height: 0;
}

:global(.mini-map-photo-marker) {
  display: inline-grid;
  place-items: center;
  width: 34px;
  height: 34px;
  padding: 0;
  color: var(--media-marker-color);
  background: var(--media-marker-fill);
  border: 3px solid var(--media-marker-border);
  border-radius: var(--media-marker-border-radius);
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.34);
  cursor: default;
  font-size: 0.8rem;
  transition:
    box-shadow 140ms ease,
    filter 140ms ease,
    outline-color 140ms ease;
}

:global(.mini-map-photo-marker--interactive) {
  cursor: pointer;
}

:global(.mini-map-photo-marker--selected) {
  outline: 4px solid color-mix(in srgb, var(--accent) 55%, transparent);
  outline-offset: 3px;
  z-index: 2;
}

:global(.mini-map-photo-marker--highlighted) {
  outline: 4px solid color-mix(in srgb, var(--accent) 82%, white);
  outline-offset: 4px;
  box-shadow:
    0 0 0 9px color-mix(in srgb, var(--accent) 20%, transparent),
    0 6px 18px rgba(15, 23, 42, 0.45);
  filter: brightness(1.08);
  z-index: 3;
}

:global(.mini-map-photo-marker--highlighted i) {
  transform: scale(1.15);
}

:global(.mini-map-photo-marker:focus-visible) {
  outline: 3px solid var(--accent);
  outline-offset: 3px;
}

/* ── Resize handle (bottom-sheet style) ── */
.resize-handle {
  flex: 0 0 auto;
  height: 18px;
  cursor: ns-resize;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  user-select: none;
  touch-action: none;
}

.resize-handle:focus-visible {
  outline: 2px solid var(--focus-ring, var(--accent));
  outline-offset: -2px;
}

.resize-grip {
  width: 36px;
  height: 4px;
  border-radius: 9999px;
  background: var(--border-hover);
  transition:
    background 0.15s,
    width 0.15s;
}

.resize-handle:hover .resize-grip,
.resize-handle:active .resize-grip {
  width: 48px;
  background: var(--text-faint);
}
</style>
