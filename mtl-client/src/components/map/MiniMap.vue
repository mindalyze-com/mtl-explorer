<script setup>
import { inject, onBeforeUnmount, onMounted, useTemplateRef, watch } from 'vue';
import maplibregl from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import { EVENT_MEASURE_BETWEEN_POINTS_DIALOG_MAXIMIZED_EVENT } from '@/utils/Utils';
import { fetchMapConfig } from '@/utils/mapConfigService';
import { buildLocalVectorStyle, buildRemoteRasterStyle } from '@/utils/mapStyle';
import { TRACK_COLOR } from '@/utils/trackColors';

const props = defineProps({
  /** GeoJSON FeatureCollection of tracks to display. */
  tracksGeoJson: { type: Object, default: null },
  /** Map bounds as [[swLng, swLat], [neLng, neLat]]. */
  mapBounds: { type: Array, default: null },
});

const measureEvent = inject(EVENT_MEASURE_BETWEEN_POINTS_DIALOG_MAXIMIZED_EVENT, null);

const mapContainer = useTemplateRef('mapContainer');

// Non-reactive runtime state — kept as plain refs of-instance via closure.
let map; // maplibregl.Map
let popup; // maplibregl.Popup

function updateTracksSource() {
  if (!map || !props.tracksGeoJson) return;
  const source = map.getSource('minimap-tracks');
  if (source) {
    source.setData(props.tracksGeoJson);
    return;
  }
  map.addSource('minimap-tracks', {
    type: 'geojson',
    data: props.tracksGeoJson,
  });
  // Line layer for track LineString features
  map.addLayer({
    id: 'minimap-tracks-layer',
    type: 'line',
    source: 'minimap-tracks',
    filter: ['==', ['geometry-type'], 'LineString'],
    layout: { 'line-join': 'round', 'line-cap': 'round' },
    paint: { 'line-color': ['coalesce', ['get', 'color'], TRACK_COLOR], 'line-width': 4, 'line-opacity': 0.9 },
  });
  // Circle layer for Point features (used by VirtualRace for trigger points and racers)
  map.addLayer({
    id: 'minimap-points-layer',
    type: 'circle',
    source: 'minimap-tracks',
    filter: ['==', ['geometry-type'], 'Point'],
    paint: {
      'circle-radius': ['case',
        ['==', ['get', 'type'], 'trigger'], 12,
        8
      ],
      'circle-color': ['coalesce', ['get', 'color'], TRACK_COLOR],
      'circle-opacity': 0.7,
      'circle-stroke-width': 1,
      'circle-stroke-color': '#fff',
    },
  });
  // Text labels for trigger points (only features with a 'label' property)
  map.addLayer({
    id: 'minimap-point-labels-layer',
    type: 'symbol',
    source: 'minimap-tracks',
    filter: ['all', ['==', ['geometry-type'], 'Point'], ['has', 'label']],
    layout: {
      'text-field': ['get', 'label'],
      'text-size': 11,
      'text-anchor': 'center',
      'text-allow-overlap': true,
      'text-ignore-placement': true,
      'text-font': ['Noto Sans Regular'],
    },
    paint: {
      'text-color': '#ffffff',
      'text-halo-color': 'rgba(0,0,0,0.6)',
      'text-halo-width': 1.5,
    },
  });

  // Hover tooltip for points
  popup = new maplibregl.Popup({ closeButton: false, closeOnClick: false, offset: 12 });

  map.on('mouseenter', 'minimap-points-layer', (e) => {
    map.getCanvas().style.cursor = 'pointer';
    if (!e.features || !e.features.length) return;
    const featProps = e.features[0].properties;
    const coords = e.features[0].geometry.coordinates.slice();
    let html = '';
    if (featProps.type === 'trigger') {
      html = `<strong>${featProps.name || featProps.label || 'Trigger'}</strong>`;
    } else if (featProps.type === 'racer') {
      html = `<span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${featProps.color};margin-right:4px"></span>${featProps.trackName || 'Track ' + featProps.trackIndex}`;
    }
    if (html) {
      popup.setLngLat(coords).setHTML(html).addTo(map);
    }
  });

  map.on('mouseleave', 'minimap-points-layer', () => {
    map.getCanvas().style.cursor = '';
    popup.remove();
  });
}

async function initMap() {
  if (!mapContainer.value) return;

  if (map) {
    map.remove();
    map = undefined;
  }

  const config = await fetchMapConfig();
  let style;
  if (config.tileMode === 'local') {
    style = buildLocalVectorStyle(config.tileBaseUrl, config.tilesetName, 'light');
  } else {
    style = buildRemoteRasterStyle(config.remoteTileUrl);
  }

  map = new maplibregl.Map({
    container: mapContainer.value,
    style,
    center: [8.505778, 47.5605],
    zoom: 10,
    attributionControl: false,
  });

  map.addControl(new maplibregl.ScaleControl(), 'bottom-left');

  // Silently handle any missing sprite icons with a transparent 1×1 placeholder
  map.on('styleimagemissing', (e) => {
    if (!map.hasImage(e.id)) {
      map.addImage(e.id, { width: 1, height: 1, data: new Uint8ClampedArray(4) });
    }
  });

  await new Promise((resolve) => {
    if (map.loaded()) resolve();
    else map.on('load', resolve);
  });

  updateTracksSource();

  if (props.mapBounds) {
    map.fitBounds(props.mapBounds, { padding: 20 });
  }
}

function invalidateMapSize() {
  if (map) map.resize();
}

onMounted(async () => {
  await initMap();
});

// If the container wasn't available at mount (e.g. lazy TabPanel), retry once it appears.
watch(mapContainer, async (el) => {
  if (el && !map) await initMap();
});

onBeforeUnmount(() => {
  if (popup) {
    popup.remove();
    popup = null;
  }
  if (map) {
    map.remove();
    map = undefined;
  }
});

watch(() => measureEvent?.value, () => {
  invalidateMapSize();
});

watch(() => props.mapBounds, (vNew) => {
  if (map && vNew) {
    map.fitBounds(vNew, { padding: 20 });
  }
});

watch(() => props.tracksGeoJson, (vNew) => {
  if (map && vNew) {
    updateTracksSource();
  }
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
  min-height: 100%;
  height: 120px;
  width: 100%;
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
