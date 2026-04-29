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

      <button
        class="map-overlay-events-btn"
        :class="{ active: showEvents }"
        :disabled="trackEvents.length === 0"
        @click.stop="toggleEvents"
        aria-label="Toggle track events"
        title="Toggle track events"
      >
        <i class="bi bi-pause-circle"></i>
        <span v-if="trackEvents.length > 0" class="event-count">{{ trackEvents.length }}</span>
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
import { defineComponent, watch, ref, nextTick, onBeforeUnmount, markRaw, type PropType } from 'vue';
import { usePointerDrag } from '@/composables/usePointerDrag';
import maplibregl from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import { useTrackMapSync, type TrackPoint } from '@/composables/useTrackMapSync';
import { useChartSync } from '@/composables/useChartSync';
import { fetchMapConfig } from '@/utils/mapConfigService';
import { buildLocalVectorStyle, buildRemoteRasterStyle } from '@/utils/mapStyle';
import { TRACK_COLOR } from '@/utils/trackColors';
import { USER_PREFS_KEYS, migrateLegacyKeys } from '@/utils/userPrefs';
import type { GpsTrackEvent } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';

const STORAGE_KEY = USER_PREFS_KEYS.trackMiniMapHeight;
const MIN_HEIGHT = 80;
const MAX_HEIGHT = 600;
const DEFAULT_HEIGHT = 220;
const DEFAULT_HEIGHT_MOBILE = 160;

const SOURCE_ID = 'detail-track';
const TRACK_LAYER = 'detail-track-layer';
const MARKER_SOURCE = 'detail-marker';
const MARKER_LAYER = 'detail-marker-layer';
const EVENT_SOURCE = 'detail-events';
const EVENT_LAYER = 'detail-events-layer';
const EVENT_ICON_ID = 'detail-stop-event-diamond';
const EVENT_ICON_LOGICAL_SIZE = 20;
const EVENT_ICON_DIAMOND_SIZE = 13;
const EVENT_ICON_CORNER_RADIUS = 2.5;
const EVENT_ICON_STROKE_WIDTH = 1;
const DEFAULT_DEVICE_PIXEL_RATIO = 1;
const STOP_EVENT_MARKER_FILL = '#f97316';
const STOP_EVENT_MARKER_STROKE = '#7c2d12';

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
    trackEvents: { type: Array as PropType<GpsTrackEvent[]>, default: () => [] },
  },
  setup(props) {
    const mapEl = ref<HTMLElement | null>(null);
    const mapBodyEl = ref<HTMLElement | null>(null);
    const isCollapsed = ref(false);
    const mapHeight = ref(loadStoredHeight());
    const showEvents = ref(props.trackEvents.length > 0);

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
      clearHover,
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
      drawEvents();

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
        clearHover();
        clearChartCrosshairs();
      });

      map.on('dblclick', () => {
        setPinnedPoint(null);
      });

      map.on('click', EVENT_LAYER, (e: maplibregl.MapLayerMouseEvent) => {
        const feature = e.features?.[0];
        if (!feature || !map) return;
        const coordinates = (feature.geometry as GeoJSON.Point).coordinates.slice() as [number, number];
        const label = feature.properties?.label ?? 'Event';
        const time = feature.properties?.time ?? '';
        const duration = feature.properties?.duration ?? '';
        new maplibregl.Popup({ closeButton: true, closeOnClick: true })
          .setLngLat(coordinates)
          .setHTML(`<strong>${escapeHtml(label)}</strong><br>${escapeHtml(time)}${duration ? `<br>${escapeHtml(duration)}` : ''}`)
          .addTo(map);
      });

      map.on('mouseenter', EVENT_LAYER, () => {
        if (map) map.getCanvas().style.cursor = 'pointer';
      });

      map.on('mouseleave', EVENT_LAYER, () => {
        if (map) map.getCanvas().style.cursor = '';
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
      if (map.getLayer(EVENT_LAYER)) {
        map.moveLayer(EVENT_LAYER);
      }
    }

    function eventPoint(event: GpsTrackEvent): [number, number] | null {
      const point = event.startPointLongLat as unknown;
      const lngLat = pointToLngLat(point);
      if (!lngLat) return null;
      const [lng, lat] = lngLat;
      if (!Number.isFinite(lng) || !Number.isFinite(lat)) return null;
      return [lng, lat];
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

    function eventTypeLabel(value: string | undefined): string {
      if (!value) return 'Event';
      return value.toLowerCase().replaceAll('_', ' ').replace(/\b\w/g, c => c.toUpperCase());
    }

    function eventTimeLabel(value: GpsTrackEvent['startTimestamp']): string {
      if (!value) return '';
      const date = typeof value === 'string' ? new Date(value) : value as Date;
      if (!Number.isFinite(date.getTime())) return '';
      return new Intl.DateTimeFormat(undefined, {
        dateStyle: 'medium',
        timeStyle: 'short',
      }).format(date);
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

    function drawEvents() {
      if (!map || !map.isStyleLoaded()) return;
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
            },
          });
        }
      }

      const geojson: GeoJSON.FeatureCollection<GeoJSON.Point> = {
        type: 'FeatureCollection',
        features,
      };

      const source = map.getSource(EVENT_SOURCE) as maplibregl.GeoJSONSource | undefined;
      if (source) {
        source.setData(geojson);
      } else {
        map.addSource(EVENT_SOURCE, { type: 'geojson', data: geojson });
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

      if (map.getLayer(EVENT_LAYER)) {
        map.moveLayer(EVENT_LAYER);
      }
    }

    function ensureEventIcon() {
      if (!map || map.hasImage(EVENT_ICON_ID)) return;
      const ratio = Number.isFinite(window.devicePixelRatio) && window.devicePixelRatio > 0
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
        EVENT_ICON_CORNER_RADIUS,
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
      drawEvents();
    }

    watch(() => props.trackEvents, () => {
      if (props.trackEvents.length === 0) {
        showEvents.value = false;
      } else {
        showEvents.value = true;
      }
      drawEvents();
    }, { deep: true });

    return {
      mapEl,
      mapBodyEl,
      resizeHandleEl,
      isCollapsed,
      mapHeight,
      showEvents,
      toggleCollapse,
      toggleEvents,
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
