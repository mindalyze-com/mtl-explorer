import { onBeforeUnmount, onMounted, watch, type Ref } from 'vue';
import * as maplibregl from 'maplibre-gl';
import { fetchMapConfig, mainTileArchiveUrl } from '@/utils/mapConfigService';
import { VectorBasemapRecovery } from '@/utils/vectorBasemapRecovery';
import { reportMapError, reportMapContextLoss } from '@/utils/mapDiagnostics';
import { TRACK_COLOR } from '@/utils/trackColors';
import { mapLabelFonts } from '@/utils/mapLabelFonts';
import { resolveConfiguredMapStyle } from '@/components/map/mapStyleResolver';
import { installMissingStyleImageResolver } from '@/utils/maplibreStyleImages';
import { useMapSettingsStore } from '@/stores/mapSettingsStore';
import { useMeasurementSystem } from '@/composables/useMeasurementSystem';
import { mapScaleUnitForMeasurementSystem, syncMapScaleControlUnit } from '@/components/map/mapScaleControl';
import {
  MINI_MAP_DIAMOND_ICON_PREFIX,
  MINI_MAP_DIAMOND_PIXEL_RATIO,
  MINI_MAP_DIAMOND_SHAPE,
  miniMapDiamondIcon,
} from '@/components/map/miniMapDiamondIcon';

export type MiniMapBounds = [[number, number], [number, number]];
export type MiniMapGeoJson = GeoJSON.FeatureCollection<GeoJSON.Geometry, Record<string, unknown>>;
export interface MiniMapFocus {
  center: [number, number];
  spanMetres: number;
}

const MINI_MAP_SOURCE_ID = 'minimap-tracks';
const MINI_MAP_TRACKS_LAYER_ID = 'minimap-tracks-layer';
const MINI_MAP_POINTS_LAYER_ID = 'minimap-points-layer';
const MINI_MAP_DIAMONDS_LAYER_ID = 'minimap-diamonds-layer';
const MINI_MAP_LABELS_LAYER_ID = 'minimap-point-labels-layer';
const DEFAULT_CENTER: [number, number] = [8.505778, 47.5605];
const DEFAULT_ZOOM = 10;
const MINI_MAP_THEME = 'light';
const LOCAL_VECTOR_STYLE_MODE = 'local-vector';
const FIT_BOUNDS_PADDING = 20;
const MERCATOR_TILE_SIZE = 512;
const POPUP_MAX_WIDTH = 'min(320px, calc(100vw - 32px))';
const LOCATOR_POINT_TYPE = 'locator';
const LOCATOR_POINT_RADIUS = 9;
const LOCATOR_STROKE_WIDTH = 3;
const GHOST_POINT_TYPE = 'ghost';
const GHOST_POINT_RADIUS = 7;
const GHOST_HIGHLIGHT_RADIUS = 9;
const GHOST_STROKE_WIDTH = 2;
const isGhost: maplibregl.ExpressionSpecification = ['==', ['get', 'type'], GHOST_POINT_TYPE];
const isHollowMarker: maplibregl.ExpressionSpecification = [
  'any',
  ['==', ['get', 'type'], LOCATOR_POINT_TYPE],
  isGhost,
];
const onLightMarker: maplibregl.ExpressionSpecification = [
  'any',
  isHollowMarker,
  ['==', ['get', 'shape'], MINI_MAP_DIAMOND_SHAPE],
];

interface UseMiniMapOptions {
  container: Ref<HTMLElement | null>;
  tracksGeoJson: Ref<MiniMapGeoJson | null>;
  mapBounds: Ref<MiniMapBounds | null>;
  mapFocus?: Ref<MiniMapFocus | null>;
  showZoomControls?: Ref<boolean>;
  highlightedTrackIndex: Ref<number | null>;
  fitBoundsOnResize?: Ref<boolean>;
  resizeSignal: Ref<unknown>;
  onHoverRacer: (trackIndex: number) => void;
  onLeaveRacer: () => void;
}

export function useMiniMap(options: UseMiniMapOptions) {
  const mapSettingsStore = useMapSettingsStore();
  const { measurementSystem } = useMeasurementSystem();
  let map: maplibregl.Map | null = null;
  let scaleControl: maplibregl.ScaleControl | null = null;
  let popup: maplibregl.Popup | null = null;
  let hoveredFeatureId: GeoJSON.Feature['id'] | null = null;
  let resizeObserver: ResizeObserver | null = null;
  let initStarted = false;
  let destroyed = false;
  let styleReady = false;
  let vectorRecovery: VectorBasemapRecovery | null = null;

  async function initMap(): Promise<void> {
    if (!options.container.value || initStarted) return;
    initStarted = true;

    map?.remove();
    map = null;

    const config = await fetchMapConfig();
    if (destroyed || !options.container.value) return;

    mapSettingsStore.hydrate();
    const { style, styleMode } = resolveConfiguredMapStyle({
      config,
      theme: MINI_MAP_THEME,
      mapSourceMode: mapSettingsStore.mapSourceMode,
    });
    map = new maplibregl.Map({
      container: options.container.value,
      style,
      center: DEFAULT_CENTER,
      zoom: DEFAULT_ZOOM,
      attributionControl: false,
    });

    scaleControl = new maplibregl.ScaleControl({
      unit: mapScaleUnitForMeasurementSystem(measurementSystem.value),
    });
    map.addControl(scaleControl, 'bottom-left');
    if (options.showZoomControls?.value)
      map.addControl(new maplibregl.NavigationControl({ showCompass: false }), 'top-right');
    installMissingImageFallback(map);

    const initializedMap = map;
    if (styleMode === LOCAL_VECTOR_STYLE_MODE) {
      const replaceBasemap = (fallback: boolean) => {
        styleReady = false;
        const resolved = resolveConfiguredMapStyle({
          config,
          theme: MINI_MAP_THEME,
          mapSourceMode: mapSettingsStore.mapSourceMode,
          localTilesReady: !fallback,
        });
        initializedMap.once('style.load', () => {
          if (destroyed || map !== initializedMap) return;
          styleReady = true;
          syncTrackSource();
          updateHighlight(options.highlightedTrackIndex.value);
        });
        initializedMap.setStyle(resolved.style);
      };
      vectorRecovery = new VectorBasemapRecovery({
        map: initializedMap,
        archiveUrl: mainTileArchiveUrl(config),
        isCurrent: () => !destroyed && map === initializedMap,
        onUnavailable: () => replaceBasemap(true),
        onRestored: () => replaceBasemap(false),
      });
    }

    initializedMap.on('error', (event) => {
      if (destroyed || map !== initializedMap || vectorRecovery?.handleError(event)) return;
      reportMapError(event, 'useMiniMap');
    });
    initializedMap.on('webglcontextlost', () => {
      if (!destroyed && map === initializedMap) reportMapContextLoss('useMiniMap');
    });

    await waitForMapStyle(map);
    if (destroyed || !map) return;
    styleReady = true;

    syncTrackSource();
    refitView();
    updateHighlight(options.highlightedTrackIndex.value);
  }

  function ensureDiamondImages(): void {
    for (const feature of options.tracksGeoJson.value?.features ?? []) {
      if (feature.properties?.shape !== MINI_MAP_DIAMOND_SHAPE) continue;
      const color = String(feature.properties.color ?? TRACK_COLOR);
      const id = MINI_MAP_DIAMOND_ICON_PREFIX + color;
      if (map && !map.hasImage(id))
        map.addImage(id, miniMapDiamondIcon(color), { pixelRatio: MINI_MAP_DIAMOND_PIXEL_RATIO });
    }
  }

  function syncTrackSource(): void {
    if (!map || !styleReady || !options.tracksGeoJson.value) return;
    ensureDiamondImages();
    const source = map.getSource(MINI_MAP_SOURCE_ID) as maplibregl.GeoJSONSource | undefined;
    if (source) {
      source.setData(options.tracksGeoJson.value);
      ensureMiniMapLayers();
      refreshHoveredPopup();
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
          'line-width': ['coalesce', ['get', 'lineWidth'], 4],
          'line-opacity': ['coalesce', ['get', 'lineOpacity'], 0.9],
        },
      });
    }

    if (!map.getLayer(MINI_MAP_POINTS_LAYER_ID)) {
      map.addLayer({
        id: MINI_MAP_POINTS_LAYER_ID,
        type: 'circle',
        source: MINI_MAP_SOURCE_ID,
        filter: ['all', ['==', ['geometry-type'], 'Point'], ['!=', ['get', 'shape'], MINI_MAP_DIAMOND_SHAPE]],
        paint: {
          'circle-radius': [
            'coalesce',
            ['get', 'pointRadius'],
            [
              'case',
              ['==', ['get', 'type'], 'trigger'],
              12,
              ['==', ['get', 'type'], LOCATOR_POINT_TYPE],
              LOCATOR_POINT_RADIUS,
              isGhost,
              GHOST_POINT_RADIUS,
              8,
            ],
          ],
          'circle-color': ['case', isHollowMarker, '#fff', ['coalesce', ['get', 'color'], TRACK_COLOR]],
          'circle-opacity': 0.7,
          'circle-stroke-width': [
            'case',
            ['==', ['get', 'type'], LOCATOR_POINT_TYPE],
            LOCATOR_STROKE_WIDTH,
            isGhost,
            GHOST_STROKE_WIDTH,
            1,
          ],
          'circle-stroke-color': ['case', isHollowMarker, ['coalesce', ['get', 'color'], TRACK_COLOR], '#fff'],
        },
      });
    }

    if (!map.getLayer(MINI_MAP_DIAMONDS_LAYER_ID)) {
      map.addLayer(
        {
          id: MINI_MAP_DIAMONDS_LAYER_ID,
          type: 'symbol',
          source: MINI_MAP_SOURCE_ID,
          filter: ['all', ['==', ['geometry-type'], 'Point'], ['==', ['get', 'shape'], MINI_MAP_DIAMOND_SHAPE]],
          layout: {
            'icon-image': ['concat', MINI_MAP_DIAMOND_ICON_PREFIX, ['coalesce', ['get', 'color'], TRACK_COLOR]],
            'icon-allow-overlap': true,
            'icon-ignore-placement': true,
          },
        },
        map.getLayer(MINI_MAP_LABELS_LAYER_ID) ? MINI_MAP_LABELS_LAYER_ID : undefined
      );
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
          'text-font': mapLabelFonts(map.getGlyphs()),
        },
        paint: {
          'text-color': ['case', onLightMarker, ['coalesce', ['get', 'color'], TRACK_COLOR], '#ffffff'],
          'text-halo-color': ['case', onLightMarker, '#fff', 'rgba(0,0,0,0.6)'],
          'text-halo-width': 1.5,
        },
      });
    }
  }

  function installPointInteractions(): void {
    if (!map || popup) return;
    popup = new maplibregl.Popup({ closeButton: false, closeOnClick: false, offset: 12, maxWidth: POPUP_MAX_WIDTH });

    const onEnter = (event: maplibregl.MapLayerMouseEvent) => {
      if (!map || !popup) return;
      map.getCanvas().style.cursor = 'pointer';
      const feature = event.features?.[0];
      if (!feature || feature.geometry.type !== 'Point') return;
      const props = feature.properties ?? {};
      const html = popupHtml(props);
      if (!html) return;

      hoveredFeatureId = feature.id ?? null;
      const coords = feature.geometry.coordinates.slice() as [number, number];
      popup.setLngLat(coords).setHTML(html).addTo(map);
      if (props.type === 'racer' && Number.isFinite(Number(props.trackIndex))) {
        options.onHoverRacer(Number(props.trackIndex));
      }
    };

    const onLeave = () => {
      if (!map || !popup) return;
      map.getCanvas().style.cursor = '';
      popup.remove();
      hoveredFeatureId = null;
      options.onLeaveRacer();
    };

    for (const layerId of [MINI_MAP_POINTS_LAYER_ID, MINI_MAP_DIAMONDS_LAYER_ID]) {
      map.on('mouseenter', layerId, onEnter);
      map.on('mouseleave', layerId, onLeave);
    }
  }

  function refreshHoveredPopup(): void {
    if (!map || !popup || hoveredFeatureId == null) return;
    const feature = options.tracksGeoJson.value?.features.find((candidate) => candidate.id === hoveredFeatureId);
    if (!feature || feature.geometry.type !== 'Point') {
      popup.remove();
      hoveredFeatureId = null;
      map.getCanvas().style.cursor = '';
      options.onLeaveRacer();
      return;
    }
    popup.setLngLat(feature.geometry.coordinates as [number, number]).setHTML(popupHtml(feature.properties));
  }

  function popupHtml(props: Record<string, unknown>): string {
    if (props.type === 'trigger') {
      return `<strong>${escapeHtml(String(props.name || props.label || 'Trigger'))}</strong>`;
    }
    if (props.type === 'racer' || props.type === LOCATOR_POINT_TYPE || props.type === GHOST_POINT_TYPE) {
      const color = escapeHtml(String(props.color || TRACK_COLOR));
      const label = escapeHtml(
        String(props.popupTitle || props.trackName || props.name || `Track ${props.trackIndex ?? ''}`)
      );
      const details =
        typeof props.popupDetails === 'string'
          ? props.popupDetails
              .split('\n')
              .map((line) => `<div>${escapeHtml(line)}</div>`)
              .join('')
          : '';
      return `<span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${color};margin-right:4px"></span><strong>${label}</strong>${details}`;
    }
    return '';
  }

  function updateHighlight(idx: number | null): void {
    if (!map?.getLayer(MINI_MAP_POINTS_LAYER_ID)) return;
    if (idx == null) {
      map.setPaintProperty(MINI_MAP_POINTS_LAYER_ID, 'circle-radius', [
        'coalesce',
        ['get', 'pointRadius'],
        [
          'case',
          ['==', ['get', 'type'], 'trigger'],
          12,
          ['==', ['get', 'type'], LOCATOR_POINT_TYPE],
          LOCATOR_POINT_RADIUS,
          isGhost,
          GHOST_POINT_RADIUS,
          8,
        ],
      ]);
      map.setPaintProperty(MINI_MAP_POINTS_LAYER_ID, 'circle-opacity', 0.7);
      map.setPaintProperty(MINI_MAP_POINTS_LAYER_ID, 'circle-stroke-width', [
        'case',
        ['==', ['get', 'type'], LOCATOR_POINT_TYPE],
        LOCATOR_STROKE_WIDTH,
        isGhost,
        GHOST_STROKE_WIDTH,
        1,
      ]);
      return;
    }

    map.setPaintProperty(MINI_MAP_POINTS_LAYER_ID, 'circle-radius', [
      'case',
      ['==', ['get', 'type'], 'trigger'],
      12,
      ['==', ['get', 'type'], LOCATOR_POINT_TYPE],
      LOCATOR_POINT_RADIUS,
      ['all', isGhost, ['==', ['get', 'trackIndex'], idx]],
      GHOST_HIGHLIGHT_RADIUS,
      isGhost,
      GHOST_POINT_RADIUS,
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
      ['==', ['get', 'type'], LOCATOR_POINT_TYPE],
      LOCATOR_STROKE_WIDTH,
      isGhost,
      GHOST_STROKE_WIDTH,
      ['==', ['get', 'trackIndex'], idx],
      2.5,
      1,
    ]);
  }

  function fitBounds(bounds: MiniMapBounds | null): void {
    if (map && bounds && !options.mapFocus?.value) {
      map.fitBounds(bounds, { padding: FIT_BOUNDS_PADDING });
    }
  }

  function refitView(): void {
    if (!map) return;
    const focus = options.mapFocus?.value;
    if (!focus) {
      fitBounds(options.mapBounds.value);
      return;
    }
    const canvas = map.getCanvas();
    const pixels = Math.max(canvas.clientWidth, canvas.clientHeight);
    if (!pixels || !(focus.spanMetres > 0)) return;
    const metresPerWorld = 1 / maplibregl.MercatorCoordinate.fromLngLat(focus.center).meterInMercatorCoordinateUnits();
    const zoom = Math.log2((pixels * metresPerWorld) / (MERCATOR_TILE_SIZE * focus.spanMetres));
    map.easeTo({
      center: focus.center,
      zoom: Math.min(map.getMaxZoom(), Math.max(map.getMinZoom(), zoom)),
      bearing: 0,
      pitch: 0,
      duration: 0,
    });
  }

  function invalidateMapSize(): void {
    window.requestAnimationFrame(() => {
      if (destroyed) return;
      map?.resize();
      if (map && options.fitBoundsOnResize?.value && options.mapFocus?.value) refitView();
      else if (map && options.fitBoundsOnResize?.value && options.mapBounds.value)
        map.fitBounds(options.mapBounds.value, { padding: FIT_BOUNDS_PADDING, animate: false });
    });
  }

  function observeContainerSize(el: HTMLElement | null): void {
    resizeObserver?.disconnect();
    resizeObserver = null;
    if (!el || typeof ResizeObserver === 'undefined') return;
    resizeObserver = new ResizeObserver(() => invalidateMapSize());
    resizeObserver.observe(el);
  }

  onMounted(() => {
    observeContainerSize(options.container.value);
    void initMap();
  });

  watch(options.container, (el) => {
    observeContainerSize(el);
    if (el && !map) void initMap();
  });

  watch(options.resizeSignal, () => {
    invalidateMapSize();
  });

  watch(options.mapBounds, fitBounds);
  if (options.mapFocus) watch(options.mapFocus, refitView);

  watch(options.tracksGeoJson, (value) => {
    if (map && value) syncTrackSource();
  });

  watch(options.highlightedTrackIndex, updateHighlight);

  watch(measurementSystem, (system) => syncMapScaleControlUnit(scaleControl, system));

  onBeforeUnmount(() => {
    destroyed = true;
    vectorRecovery?.dispose();
    vectorRecovery = null;
    popup?.remove();
    popup = null;
    resizeObserver?.disconnect();
    resizeObserver = null;
    map?.remove();
    map = null;
    scaleControl = null;
    initStarted = false;
  });

  return { invalidateMapSize, refitView };
}

function waitForMapStyle(map: maplibregl.Map): Promise<void> {
  return new Promise((resolve) => {
    // Overlay geometry must not wait for remote basemap tiles to finish loading.
    if (map.isStyleLoaded()) resolve();
    else map.on('style.load', () => resolve());
  });
}

function installMissingImageFallback(map: maplibregl.Map): void {
  installMissingStyleImageResolver(map);
}

function escapeHtml(value: string): string {
  return value.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;');
}
