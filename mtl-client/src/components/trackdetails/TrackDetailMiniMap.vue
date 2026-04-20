<template>
  <div class="mini-map-wrapper" :class="{ collapsed: isCollapsed }">
    <!-- Collapsed strip: just a thin bar to re-expand -->
    <div v-if="isCollapsed" class="mini-map-collapsed-strip" @click="toggleCollapse" title="Expand map">
      <i class="bi bi-chevron-down"></i>
    </div>

    <!-- Map body with floating overlay controls -->
    <div v-show="!isCollapsed" class="mini-map-body" :style="{ height: mapHeight + 'px' }" ref="mapBodyEl">
      <div ref="mapEl" class="mini-map-container"></div>

      <!-- Floating collapse button overlaid on the map -->
      <button
        class="map-overlay-collapse-btn"
        @click.stop="toggleCollapse"
        :aria-label="'Collapse map'"
        title="Collapse map"
      >
        <i class="bi bi-chevron-up"></i>
      </button>
    </div>

    <!-- Bottom-sheet style resize handle -->
    <div
      v-show="!isCollapsed"
      ref="resizeHandleEl"
      class="resize-handle"
      title="Drag to resize"
    >
      <span class="resize-grip"></span>
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent, watch, ref, nextTick, onBeforeUnmount, markRaw } from 'vue';
import { usePointerDrag } from '@/composables/usePointerDrag';
import maplibregl from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import { useTrackMapSync, type TrackPoint } from '@/composables/useTrackMapSync';
import { useChartSync } from '@/composables/useChartSync';
import { fetchMapConfig } from '@/utils/mapConfigService';
import { buildLocalVectorStyle, buildRemoteRasterStyle } from '@/utils/mapStyle';
import { TRACK_COLOR } from '@/utils/trackColors';
import { USER_PREFS_KEYS, migrateLegacyKeys } from '@/utils/userPrefs';

const STORAGE_KEY = USER_PREFS_KEYS.trackMiniMapHeight;
const MIN_HEIGHT = 80;
const MAX_HEIGHT = 600;
const DEFAULT_HEIGHT = 220;
const DEFAULT_HEIGHT_MOBILE = 160;

const SOURCE_ID = 'detail-track';
const TRACK_LAYER = 'detail-track-layer';
const MARKER_SOURCE = 'detail-marker';
const MARKER_LAYER = 'detail-marker-layer';

function isMobile() {
  return window.innerWidth <= 768;
}

function loadStoredHeight(): number {
  try {
    migrateLegacyKeys();
    const v = localStorage.getItem(STORAGE_KEY);
    if (v) {
      const n = parseInt(v, 10);
      if (n >= MIN_HEIGHT && n <= MAX_HEIGHT) return n;
    }
  } catch { /* ignore */ }
  return isMobile() ? DEFAULT_HEIGHT_MOBILE : DEFAULT_HEIGHT;
}

export default defineComponent({
  name: 'TrackDetailMiniMap',
  props: {
    gpsTrackId: { type: Number, required: true },
  },
  setup(props) {
    const mapEl = ref<HTMLElement | null>(null);
    const mapBodyEl = ref<HTMLElement | null>(null);
    const isCollapsed = ref(false);
    const mapHeight = ref(loadStoredHeight());

    let map: maplibregl.Map | null = null;
    let trackDrawn = false;

    // ── Resize via native pointer drag ──
    const resizeHandleEl = ref<HTMLElement | null>(null);
    let resizeStartHeight = 0;

    usePointerDrag(resizeHandleEl, ({ movement: [, my], dragging, first, last }) => {
      if (first) {
        resizeStartHeight = mapHeight.value;
      }
      if (dragging) {
        const newH = Math.min(MAX_HEIGHT, Math.max(MIN_HEIGHT, resizeStartHeight + my));
        mapHeight.value = newH;
        map?.resize();
      }
      if (last) {
        try { localStorage.setItem(STORAGE_KEY, String(mapHeight.value)); } catch { /* ignore */ }
        nextTick(() => map?.resize());
      }
    });

    // ── Map init ──────────────────────────────────────────────────────────────
    const {
      pinnedPoint, hoverPoint, getTrackPoints,
      findPointByLatLng, setPinnedPoint, setHoverPoint,
    } = useTrackMapSync();

    const { showChartsAtPoint, clearChartCrosshairs } = useChartSync();

    async function initMap() {
      if (!mapEl.value) return;

      const config = await fetchMapConfig();
      let style;
      if (config.tileMode === 'local') {
        style = buildLocalVectorStyle(config.tileBaseUrl, config.tilesetName, 'light');
      } else {
        style = buildRemoteRasterStyle(config.remoteTileUrl);
      }

      map = markRaw(new maplibregl.Map({
        container: mapEl.value,
        style,
        center: [8.505778, 47.5605],
        zoom: 10,
        attributionControl: false,
        doubleClickZoom: false,
      }));

      // Silently replace any missing sprite icons with a transparent 1×1 placeholder
      map.on('styleimagemissing', (e: { id: string }) => {
        if (!map!.hasImage(e.id)) {
          map!.addImage(e.id, { width: 1, height: 1, data: new Uint8ClampedArray(4) });
        }
      });

      await new Promise<void>(resolve => {
        if (map!.loaded()) resolve();
        else map!.on('load', resolve);
      });

      // Force a resize in case the container was zero-sized during the bottom-sheet
      // open animation (MapLibre reads the container dimensions at construction time).
      map.resize();

      drawTrack();

      // Restore any point that was set while the map was initializing.
      updateMarker(pinnedPoint.value ?? hoverPoint.value);

      map.on('click', (e: maplibregl.MapMouseEvent) => {
        const pt = findPointByLatLng(e.lngLat.lat, e.lngLat.lng);
        if (pt) {
          setPinnedPoint(pt);
          showChartsAtPoint(pt);
        }
      });

      map.on('mousemove', (e: maplibregl.MapMouseEvent) => {
        const pt = findPointByLatLng(e.lngLat.lat, e.lngLat.lng);
        if (pt) {
          setHoverPoint(pt);
          showChartsAtPoint(pt);
        }
      });

      map.on('mouseout', () => {
        setHoverPoint(null);
        clearChartCrosshairs();
      });

      map.on('dblclick', () => {
        setPinnedPoint(null);
      });
    }

    function drawTrack() {
      if (!map || !map.loaded()) return;
      const pts = getTrackPoints();
      if (pts.length === 0) return;

      const coordinates: [number, number][] = pts
        .map(p => [p.lng, p.lat] as [number, number])
        .filter(c => isFinite(c[0]) && isFinite(c[1]));
      if (coordinates.length === 0) return;

      const geojson: GeoJSON.FeatureCollection = {
        type: 'FeatureCollection',
        features: [{
          type: 'Feature',
          geometry: { type: 'LineString', coordinates },
          properties: {},
        }],
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
      map.fitBounds(bounds, { padding: 20 });
      trackDrawn = true;

      // Ensure marker layer renders on top of the track layer.
      // Bug: when initMap() runs before setTrackPoints() completes, updateMarker(null)
      // creates the MARKER_LAYER first and drawTrack() then adds TRACK_LAYER on top,
      // hiding the red dot.  moveLayer() after every drawTrack() call keeps the
      // marker always on top regardless of initialization order.
      if (map.getLayer(MARKER_LAYER)) {
        map.moveLayer(MARKER_LAYER);
      }
    }

    function updateMarker(point: TrackPoint | null) {
      // NOTE: do NOT guard with map.loaded() here.
      // MapLibre returns loaded() = false whenever any tiles are still being
      // fetched (e.g. while the user pans or zooms).  Guarding on it causes
      // every hover update to be silently dropped during tile loading, making
      // the marker appear frozen.  The GeoJSON source operations (addSource /
      // setData) are safe to call regardless of tile-loading state.
      if (!map) return;

      const markerGeojson: GeoJSON.FeatureCollection = {
        type: 'FeatureCollection',
        features: point ? [{
          type: 'Feature',
          geometry: { type: 'Point', coordinates: [point.lng, point.lat] },
          properties: {},
        }] : [],
      };

      const source = map.getSource(MARKER_SOURCE) as maplibregl.GeoJSONSource | undefined;
      if (source) {
        source.setData(markerGeojson);
      } else {
        map.addSource(MARKER_SOURCE, { type: 'geojson', data: markerGeojson });
        map.addLayer({
          id: MARKER_LAYER,
          type: 'circle',
          source: MARKER_SOURCE,
          paint: {
            'circle-radius': 7,
            'circle-color': '#e63946',
            'circle-stroke-width': 2,
            'circle-stroke-color': '#fff',
          },
        });
      }
    }

    watch([pinnedPoint, hoverPoint], ([pinned, hover]) => {
      // Hover takes priority over pinned while actively scrubbing.
      // When hover ends (mouse leaves), hoverPoint becomes null and pinned is shown.
      updateMarker(hover ?? pinned);
    }, { flush: 'sync' });

    const trackDataCheckInterval = setInterval(() => {
      if (getTrackPoints().length > 0 && map && !trackDrawn) {
        drawTrack();
      }
    }, 200);

    function toggleCollapse() {
      isCollapsed.value = !isCollapsed.value;
      if (!isCollapsed.value) {
        nextTick(() => {
          map?.resize();
          // Re-fit to track if we have one
          const pts = getTrackPoints();
          if (pts.length > 0) {
            const coordinates: [number, number][] = pts
              .map(p => [p.lng, p.lat] as [number, number])
              .filter(c => isFinite(c[0]) && isFinite(c[1]));
            if (coordinates.length === 0) return;
            const bounds = coordinates.reduce(
              (b, c) => b.extend(c as [number, number]),
              new maplibregl.LngLatBounds(coordinates[0], coordinates[0])
            );
            map?.fitBounds(bounds, { padding: 20 });
          }
        });
      }
    }

    const mountMap = () => {
      nextTick(() => {
        initMap();
      });
    };

    onBeforeUnmount(() => {
      clearInterval(trackDataCheckInterval);
      if (map) {
        map.remove();
        map = null;
      }
      trackDrawn = false;
    });

    function redrawTrack() {
      trackDrawn = false;
      drawTrack();
    }

    return {
      mapEl,
      mapBodyEl,
      resizeHandleEl,
      isCollapsed,
      mapHeight,
      toggleCollapse,
      mountMap,
      drawTrack,
      redrawTrack,
    };
  },
  mounted() {
    this.mountMap();
  },
  watch: {
    gpsTrackId() {
      this.redrawTrack();
    },
  },
});
</script>

<style scoped>
.mini-map-wrapper {
  width: 100%;
  border-bottom: 1px solid var(--border-default);
  background: var(--surface-elevated);
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
  font-size: 0.7rem;
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
  font-size: 0.8rem;
  color: var(--text-muted);
  line-height: 1.4;
  backdrop-filter: blur(2px);
}
.map-overlay-collapse-btn:hover {
  background: var(--accent-bg);
  border-color: var(--border-hover);
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

/* ── Resize handle (bottom-sheet style) ── */
.resize-handle {
  flex: 0 0 auto;
  height: 18px;
  cursor: ns-resize;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--surface-elevated);
  border-top: 1px solid var(--border-default);
  user-select: none;
  touch-action: none;
}

.resize-grip {
  width: 36px;
  height: 4px;
  border-radius: 9999px;
  background: var(--border-hover);
  transition: background 0.15s, width 0.15s;
}

.resize-handle:hover .resize-grip,
.resize-handle:active .resize-grip {
  width: 48px;
  background: var(--text-faint);
}
</style>
