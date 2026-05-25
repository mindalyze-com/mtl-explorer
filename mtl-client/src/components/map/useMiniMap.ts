import { onBeforeUnmount, onMounted, watch, type Ref } from 'vue';
import maplibregl from 'maplibre-gl';
import { fetchMapConfig } from '@/utils/mapConfigService';
import { TRACK_COLOR } from '@/utils/trackColors';
import { resolveConfiguredMapStyle } from '@/components/map/mapStyleResolver';

export type MiniMapBounds = [[number, number], [number, number]];
export type MiniMapGeoJson = GeoJSON.FeatureCollection<GeoJSON.Geometry, Record<string, unknown>>;

const MINI_MAP_SOURCE_ID = 'minimap-tracks';
const MINI_MAP_TRACKS_LAYER_ID = 'minimap-tracks-layer';
const MINI_MAP_POINTS_LAYER_ID = 'minimap-points-layer';
const MINI_MAP_LABELS_LAYER_ID = 'minimap-point-labels-layer';
const DEFAULT_CENTER: [number, number] = [8.505778, 47.5605];
const DEFAULT_ZOOM = 10;
const FIT_BOUNDS_PADDING = 20;

interface UseMiniMapOptions {
  container: Ref<HTMLElement | null>;
  tracksGeoJson: Ref<MiniMapGeoJson | null>;
  mapBounds: Ref<MiniMapBounds | null>;
  highlightedTrackIndex: Ref<number | null>;
  resizeSignal: Ref<unknown>;
  onHoverRacer: (trackIndex: number) => void;
  onLeaveRacer: () => void;
}

export function useMiniMap(options: UseMiniMapOptions) {
  let map: maplibregl.Map | null = null;
  let popup: maplibregl.Popup | null = null;
  let initStarted = false;
  let destroyed = false;

  async function initMap(): Promise<void> {
    if (!options.container.value || initStarted) return;
    initStarted = true;

    map?.remove();
    map = null;

    const config = await fetchMapConfig();
    if (destroyed || !options.container.value) return;

    const { style } = resolveConfiguredMapStyle({ config, theme: 'light' });
    map = new maplibregl.Map({
      container: options.container.value,
      style,
      center: DEFAULT_CENTER,
      zoom: DEFAULT_ZOOM,
      attributionControl: false,
    });

    map.addControl(new maplibregl.ScaleControl(), 'bottom-left');
    installMissingImageFallback(map);

    await waitForMapLoad(map);
    if (destroyed || !map) return;

    syncTrackSource();
    fitBounds(options.mapBounds.value);
    updateHighlight(options.highlightedTrackIndex.value);
  }

  function syncTrackSource(): void {
    if (!map || !options.tracksGeoJson.value) return;
    const source = map.getSource(MINI_MAP_SOURCE_ID) as maplibregl.GeoJSONSource | undefined;
    if (source) {
      source.setData(options.tracksGeoJson.value);
      ensureMiniMapLayers();
      return;
    }

    map.addSource(MINI_MAP_SOURCE_ID, {
      type: 'geojson',
      data: options.tracksGeoJson.value,
    });
    ensureMiniMapLayers();
    installPointInteractions();
  }

  function ensureMiniMapLayers(): void {
    if (!map) return;
    if (!map.getLayer(MINI_MAP_TRACKS_LAYER_ID)) {
      map.addLayer({
        id: MINI_MAP_TRACKS_LAYER_ID,
        type: 'line',
        source: MINI_MAP_SOURCE_ID,
        filter: ['==', ['geometry-type'], 'LineString'],
        layout: { 'line-join': 'round', 'line-cap': 'round' },
        paint: {
          'line-color': ['coalesce', ['get', 'color'], TRACK_COLOR],
          'line-width': 4,
          'line-opacity': 0.9,
        },
      });
    }

    if (!map.getLayer(MINI_MAP_POINTS_LAYER_ID)) {
      map.addLayer({
        id: MINI_MAP_POINTS_LAYER_ID,
        type: 'circle',
        source: MINI_MAP_SOURCE_ID,
        filter: ['==', ['geometry-type'], 'Point'],
        paint: {
          'circle-radius': ['case', ['==', ['get', 'type'], 'trigger'], 12, 8],
          'circle-color': ['coalesce', ['get', 'color'], TRACK_COLOR],
          'circle-opacity': 0.7,
          'circle-stroke-width': 1,
          'circle-stroke-color': '#fff',
        },
      });
    }

    if (!map.getLayer(MINI_MAP_LABELS_LAYER_ID)) {
      map.addLayer({
        id: MINI_MAP_LABELS_LAYER_ID,
        type: 'symbol',
        source: MINI_MAP_SOURCE_ID,
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
    }
  }

  function installPointInteractions(): void {
    if (!map || popup) return;
    popup = new maplibregl.Popup({ closeButton: false, closeOnClick: false, offset: 12 });

    map.on('mouseenter', MINI_MAP_POINTS_LAYER_ID, (event) => {
      if (!map || !popup) return;
      map.getCanvas().style.cursor = 'pointer';
      const feature = event.features?.[0];
      if (!feature || feature.geometry.type !== 'Point') return;
      const props = feature.properties ?? {};
      const html = popupHtml(props);
      if (!html) return;

      const coords = feature.geometry.coordinates.slice() as [number, number];
      popup.setLngLat(coords).setHTML(html).addTo(map);
      if (props.type === 'racer' && Number.isFinite(Number(props.trackIndex))) {
        options.onHoverRacer(Number(props.trackIndex));
      }
    });

    map.on('mouseleave', MINI_MAP_POINTS_LAYER_ID, () => {
      if (!map || !popup) return;
      map.getCanvas().style.cursor = '';
      popup.remove();
      options.onLeaveRacer();
    });
  }

  function popupHtml(props: Record<string, unknown>): string {
    if (props.type === 'trigger') {
      return `<strong>${escapeHtml(String(props.name || props.label || 'Trigger'))}</strong>`;
    }
    if (props.type === 'racer') {
      const color = escapeHtml(String(props.color || TRACK_COLOR));
      const label = escapeHtml(String(props.trackName || `Track ${props.trackIndex ?? ''}`));
      return `<span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${color};margin-right:4px"></span>${label}`;
    }
    return '';
  }

  function updateHighlight(idx: number | null): void {
    if (!map?.getLayer(MINI_MAP_POINTS_LAYER_ID)) return;
    if (idx == null) {
      map.setPaintProperty(MINI_MAP_POINTS_LAYER_ID, 'circle-radius', [
        'case',
        ['==', ['get', 'type'], 'trigger'],
        12,
        8,
      ]);
      map.setPaintProperty(MINI_MAP_POINTS_LAYER_ID, 'circle-opacity', 0.7);
      map.setPaintProperty(MINI_MAP_POINTS_LAYER_ID, 'circle-stroke-width', 1);
      return;
    }

    map.setPaintProperty(MINI_MAP_POINTS_LAYER_ID, 'circle-radius', [
      'case',
      ['==', ['get', 'type'], 'trigger'],
      12,
      ['==', ['get', 'trackIndex'], idx],
      11,
      8,
    ]);
    map.setPaintProperty(MINI_MAP_POINTS_LAYER_ID, 'circle-opacity', [
      'case',
      ['==', ['get', 'type'], 'trigger'],
      0.7,
      ['==', ['get', 'trackIndex'], idx],
      1.0,
      0.4,
    ]);
    map.setPaintProperty(MINI_MAP_POINTS_LAYER_ID, 'circle-stroke-width', [
      'case',
      ['==', ['get', 'type'], 'trigger'],
      1,
      ['==', ['get', 'trackIndex'], idx],
      2.5,
      1,
    ]);
  }

  function fitBounds(bounds: MiniMapBounds | null): void {
    if (map && bounds) {
      map.fitBounds(bounds, { padding: FIT_BOUNDS_PADDING });
    }
  }

  function invalidateMapSize(): void {
    map?.resize();
  }

  onMounted(() => {
    void initMap();
  });

  watch(options.container, (el) => {
    if (el && !map) void initMap();
  });

  watch(options.resizeSignal, () => {
    invalidateMapSize();
  });

  watch(options.mapBounds, fitBounds);

  watch(options.tracksGeoJson, (value) => {
    if (map && value) syncTrackSource();
  });

  watch(options.highlightedTrackIndex, updateHighlight);

  onBeforeUnmount(() => {
    destroyed = true;
    popup?.remove();
    popup = null;
    map?.remove();
    map = null;
    initStarted = false;
  });

  return { invalidateMapSize };
}

function waitForMapLoad(map: maplibregl.Map): Promise<void> {
  return new Promise((resolve) => {
    if (map.loaded()) resolve();
    else map.on('load', () => resolve());
  });
}

function installMissingImageFallback(map: maplibregl.Map): void {
  map.on('styleimagemissing', (event: { id: string }) => {
    if (!map.hasImage(event.id)) {
      map.addImage(event.id, { width: 1, height: 1, data: new Uint8ClampedArray(4) });
    }
  });
}

function escapeHtml(value: string): string {
  return value.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;');
}
