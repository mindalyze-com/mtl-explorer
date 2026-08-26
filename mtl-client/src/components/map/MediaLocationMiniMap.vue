<template>
  <div class="media-location-map" data-test="media-location-mini-map" role="group" :aria-label="accessibleLabel">
    <div ref="mapEl" class="media-location-map__canvas" aria-hidden="true"></div>
    <div class="media-location-map__controls" data-media-control aria-label="Location map controls">
      <button type="button" aria-label="Zoom in location map" title="Zoom in" @click="zoomIn">
        <i class="bi bi-plus-lg" aria-hidden="true"></i>
      </button>
      <button type="button" aria-label="Zoom out location map" title="Zoom out" @click="zoomOut">
        <i class="bi bi-dash-lg" aria-hidden="true"></i>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, markRaw, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import * as maplibregl from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import { fetchMapConfig } from '@/utils/mapConfigService';
import { resolveConfiguredMapStyle } from '@/components/map/mapStyleResolver';
import { installMissingStyleImageResolver } from '@/utils/maplibreStyleImages';
import { useMapSettingsStore } from '@/stores/mapSettingsStore';
import { resolveMediaPositionMarkerStyle } from '@/components/map/mediaPositionMarkerStyle';
import { TRACK_COLOR } from '@/utils/trackColors';

const MINI_MAP_DEFAULT_ZOOM = 15;
const MINI_MAP_MAX_FIT_ZOOM = 16;
const MINI_MAP_FIT_PADDING_PX = 20;
const MINI_MAP_OVERVIEW_PADDING_PX = 0;
const MINI_MAP_ZOOM_DURATION_MS = 160;
const TRACK_SOURCE_ID = 'media-location-track';
const TRACK_LAYER_ID = 'media-location-track-line';

const props = withDefaults(
  defineProps<{
    latitude: number;
    longitude: number;
    positionSource?: string | null;
    positionEstimated?: boolean;
    trackCoordinates?: number[][];
    overviewBounds?: [[number, number], [number, number]] | null;
  }>(),
  {
    positionSource: null,
    positionEstimated: false,
    trackCoordinates: () => [],
    overviewBounds: null,
  }
);

const mapEl = ref<HTMLElement | null>(null);
const mapSettingsStore = useMapSettingsStore();
let map: maplibregl.Map | null = null;
let photoMarker: maplibregl.Marker | null = null;
let markerElement: HTMLDivElement | null = null;
let resizeObserver: ResizeObserver | null = null;
let disposed = false;
let mapReady = false;

const accessibleLabel = computed(() => {
  const source =
    props.positionSource === 'USER_ASSIGNED'
      ? 'Location set by you'
      : props.positionSource === 'TRACK_INTERPOLATED' || props.positionEstimated
        ? 'Estimated location'
        : props.positionSource === 'EXIF_EMBEDDED'
          ? 'Photo GPS location'
          : 'Photo location';
  return `${source} at ${props.latitude.toFixed(5)}, ${props.longitude.toFixed(5)}`;
});

const validTrackCoordinates = computed<[number, number][]>(() =>
  props.trackCoordinates.flatMap((coordinate) => {
    const longitude = coordinate[0];
    const latitude = coordinate[1];
    return longitude != null && latitude != null && isValidCoordinate(latitude, longitude)
      ? [[longitude, latitude]]
      : [];
  })
);

async function initMap(): Promise<void> {
  if (!mapEl.value) return;
  const config = await fetchMapConfig();
  if (disposed || !mapEl.value) return;

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
      center: [props.longitude, props.latitude],
      zoom: MINI_MAP_DEFAULT_ZOOM,
      interactive: false,
      attributionControl: false,
      doubleClickZoom: false,
    })
  );
  installMissingStyleImageResolver(map);
  map.on('load', onMapLoad);

  resizeObserver = new ResizeObserver(() => map?.resize());
  resizeObserver.observe(mapEl.value);
}

function onMapLoad(): void {
  if (!map) return;
  mapReady = true;
  createPhotoMarker();
  syncTrackLayer();
  map.resize();
  fitLocationContext();
}

function createPhotoMarker(): void {
  if (!map) return;
  markerElement = document.createElement('div');
  markerElement.className = 'media-location-map-marker';
  const icon = document.createElement('i');
  icon.className = 'bi bi-camera-fill';
  markerElement.append(icon);
  updateMarkerStyle();
  photoMarker = new maplibregl.Marker({ element: markerElement })
    .setLngLat([props.longitude, props.latitude])
    .addTo(map);
  markerElement.setAttribute('role', 'img');
  markerElement.setAttribute('aria-label', accessibleLabel.value);
  markerElement.removeAttribute('tabindex');
}

function updateMarkerStyle(): void {
  if (!markerElement) return;
  const colors = resolveMediaPositionMarkerStyle(props.positionSource, props.positionEstimated);
  markerElement.style.setProperty('--media-location-marker-fill', colors.fill);
  markerElement.style.setProperty('--media-location-marker-color', colors.foreground);
  markerElement.style.setProperty('--media-location-marker-border', colors.border);
  markerElement.setAttribute('aria-label', accessibleLabel.value);
}

function syncTrackLayer(): void {
  if (!map || !mapReady) return;
  const data = trackFeatureCollection();
  const source = map.getSource(TRACK_SOURCE_ID) as maplibregl.GeoJSONSource | undefined;
  if (source) {
    source.setData(data);
    return;
  }
  if (validTrackCoordinates.value.length < 2) return;
  map.addSource(TRACK_SOURCE_ID, { type: 'geojson', data });
  map.addLayer({
    id: TRACK_LAYER_ID,
    type: 'line',
    source: TRACK_SOURCE_ID,
    layout: { 'line-cap': 'round', 'line-join': 'round' },
    paint: {
      'line-color': TRACK_COLOR,
      'line-width': 3,
      'line-opacity': 0.9,
    },
  });
}

function trackFeatureCollection(): GeoJSON.FeatureCollection<GeoJSON.LineString> {
  const coordinates = validTrackCoordinates.value;
  return {
    type: 'FeatureCollection',
    features:
      coordinates.length < 2
        ? []
        : [
            {
              type: 'Feature',
              properties: {},
              geometry: { type: 'LineString', coordinates },
            },
          ],
  };
}

function fitLocationContext(): void {
  if (!map) return;
  const trackCoordinates = validTrackCoordinates.value;
  if (trackCoordinates.length >= 2) {
    const coordinates: [number, number][] = [...trackCoordinates, [props.longitude, props.latitude]];
    const bounds = coordinates
      .slice(1)
      .reduce(
        (result, coordinate) => result.extend(coordinate),
        new maplibregl.LngLatBounds(coordinates[0], coordinates[0])
      );
    map.fitBounds(bounds, {
      padding: MINI_MAP_FIT_PADDING_PX,
      maxZoom: MINI_MAP_MAX_FIT_ZOOM,
      duration: 0,
    });
    return;
  }

  if (props.overviewBounds) {
    map.fitBounds(props.overviewBounds, { padding: MINI_MAP_OVERVIEW_PADDING_PX, duration: 0 });
    return;
  }

  map.jumpTo({ center: [props.longitude, props.latitude], zoom: MINI_MAP_DEFAULT_ZOOM });
}

function zoomIn(): void {
  if (!map) return;
  map.easeTo({
    center: [props.longitude, props.latitude],
    zoom: map.getZoom() + 1,
    duration: MINI_MAP_ZOOM_DURATION_MS,
  });
}

function zoomOut(): void {
  map?.zoomOut({ duration: MINI_MAP_ZOOM_DURATION_MS });
}

watch(
  () => [props.longitude, props.latitude] as const,
  ([longitude, latitude]) => {
    photoMarker?.setLngLat([longitude, latitude]);
    updateMarkerStyle();
    fitLocationContext();
  }
);
watch(
  () => props.trackCoordinates,
  () => {
    syncTrackLayer();
    fitLocationContext();
  },
  { deep: true }
);
watch(() => props.overviewBounds, fitLocationContext, { deep: true });
watch(() => [props.positionSource, props.positionEstimated] as const, updateMarkerStyle);

onMounted(() => void initMap());
onBeforeUnmount(() => {
  disposed = true;
  resizeObserver?.disconnect();
  photoMarker?.remove();
  photoMarker = null;
  markerElement = null;
  map?.remove();
  map = null;
});

function isValidCoordinate(latitude: number, longitude: number): boolean {
  return (
    Number.isFinite(latitude) && Number.isFinite(longitude) && Math.abs(latitude) <= 90 && Math.abs(longitude) <= 180
  );
}
</script>

<style scoped>
.media-location-map {
  position: relative;
  width: 228px;
  height: 136px;
  overflow: hidden;
  border: 1px solid var(--border-medium);
  border-radius: 8px;
  background: var(--surface-elevated);
  box-shadow: var(--shadow-sm);
}

.media-location-map__canvas {
  width: 100%;
  height: 100%;
}

:global(.media-location-map-marker) {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  color: var(--media-location-marker-color);
  background: var(--media-location-marker-fill);
  border: 3px solid var(--media-location-marker-border);
  border-radius: 999px;
  box-shadow: 0 2px 7px rgba(15, 23, 42, 0.34);
  pointer-events: none;
  font-size: 0.7rem;
}

.media-location-map__controls {
  position: absolute;
  top: 6px;
  right: 6px;
  z-index: 2;
  display: grid;
  gap: 3px;
}

.media-location-map__controls button {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  padding: 0;
  color: var(--text-primary);
  background: color-mix(in srgb, var(--surface-glass-heavy) 94%, transparent);
  border: 1px solid var(--border-medium);
  border-radius: 6px;
  box-shadow: var(--shadow-sm);
  cursor: pointer;
  font-size: var(--text-sm-size);
}

.media-location-map__controls button:hover {
  color: var(--accent);
  border-color: var(--accent-muted);
}

.media-location-map__controls button:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 1px;
}

@media (max-width: 640px) {
  .media-location-map {
    width: 112px;
    height: 84px;
  }

  .media-location-map__controls {
    top: 4px;
    right: 4px;
    gap: 2px;
  }

  .media-location-map__controls button {
    width: 24px;
    height: 24px;
    font-size: var(--text-xs-size);
  }
}
</style>
