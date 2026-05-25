import { computed, isRef, markRaw, nextTick, onBeforeUnmount, onMounted, reactive, toRefs, watch } from 'vue';
import { useIndexerStatus } from '@/composables/useIndexerStatus';
import { useDataFreshness } from '@/composables/useDataFreshness';
import maplibregl from 'maplibre-gl';
import { formatDate, formatDateAndTimeWithSeconds } from '@/utils/Utils';
import 'maplibre-gl/dist/maplibre-gl.css';
import { Protocol } from 'pmtiles';
import { createCachingPMTiles, MAP_ARCHIVE_STALE_EVENT } from '@/utils/cachingPmtilesSource';

import axios from 'axios';
import { apiClient } from '@/utils/apiClient';
import {
  fetchTrackIdsWithinDistanceOfPoint,
  fetchTrackPointsForRenderedShape,
  fetchTrackCanonicalPoints,
  checkServerAuth,
} from '@/utils/ServiceHelper';
import { getToken, isAuthError, redirectToLoginAfterAuthFailure } from '@/utils/auth';
import { MediaOverlay } from '@/layers/MediaOverlay';
import { HeatmapOverlay } from '@/layers/HeatmapOverlay';
import { GeoDrawingOverlay } from '@/layers/GeoDrawingOverlay';

import { FilterService } from '@/components/filter/FilterService';
import { ColorPalette } from '@/components/filter/ColorPalette';
import {
  colorForFilterGroup,
  compareLegendEntries,
  formatFilterGroupLabel,
  gradientBucketCount,
  shouldUseCompactGradientLegend,
} from '@/utils/filterMetadata';
import {
  BACKGROUND_TRACK_PRECISION,
  DETAIL_TRACK_PRECISION,
  OVERVIEW_PRECISION,
  TRACK_LOAD_BATCH_SIZE,
} from '@/utils/tracks/trackConstants';
import {
  applyTrackFilter,
  fetchDetailTrackAtPrecision,
  isTrackCachePopulated,
  loadCachedTrackCollection,
  loadTrackCollectionPaged,
  clearTrackCache,
} from '@/utils/tracks/trackCollectionLoader';
import {
  fetchMapConfig,
  clearMapConfigCache,
  mainTileArchiveUrl,
  lowzoomTileArchiveUrl,
  MapConfigDtoTileModeEnum,
  MapConfigDtoTileSourceEnum,
} from '@/utils/mapConfigService';
import { buildLocalVectorStyleFromArchiveUrl, buildFallbackRasterStyle, MAP_OVERLAYS } from '@/utils/mapStyle';
import { TRACK_COLOR, TRACK_SELECTED_COLOR } from '@/utils/trackColors';
import { GlobeControl, computeGlobeMinZoom } from '@/components/map/GlobeControl';
import {
  bearing,
  buildTrackOverviewFeatures,
  collectionPrecisionForZoom,
  haversineDistance,
  isSameOrBetterPrecision,
  locationSearchTargetZoom as resolveLocationSearchTargetZoom,
  precisionForZoom,
} from '@/components/map/mapGeometry';
import { resolveConfiguredMapStyle } from '@/components/map/mapStyleResolver';
import { DEFAULT_LAYER_OPACITIES, useMapPreferences } from '@/components/map/useMapPreferences';
import { ensureLowZoomCached, loadLowZoomFromCache } from '@/utils/lowZoomCacheService';
import { describeError, startStartupTimer, startupError, startupLog, startupWarn } from '@/utils/startupDiagnostics';
import {
  clearDismissedDataFreshness,
  getAppliedDataFreshnessToken,
  getDismissedDataFreshness,
  setAppliedDataFreshnessToken,
  setDismissedDataFreshness,
} from '@/utils/dataFreshnessStorage';
import thumbOsmTopo from '@/assets/map-layer/osm_topo.jpg';
import thumbSwissColor from '@/assets/map-layer/swiss_color_contrast.jpg';
import thumbSwissLight from '@/assets/map-layer/swiss_topo_light.jpg';
import thumbOsmLight from '@/assets/map-layer/osm_light.jpg';
import thumbOsmDark from '@/assets/map-layer/osm_dark.jpg';

/** Detent positions for the track details bottom sheet. */
const TRACK_DETAILS_DETENTS = [
  { id: 'compact', height: '35vh' },
  { id: 'default', height: '75vh' },
  { id: 'expanded', height: '92vh' },
];
/** Compact detent used for map-originated opens so the highlighted track stays visible. */
const TRACK_DETAILS_MAP_DETENT = 'compact';
/** Mid detent used for normal detail opens when map context is less important. */
const TRACK_DETAILS_DEFAULT_DETENT = 'default';
/** Largest detent used for explicit "show me details" actions. */
const TRACK_DETAILS_EXPANDED_DETENT = 'expanded';

/** How much to expand the detail-fetch bounding box beyond the viewport. */
const DETAIL_BOUNDS_PADDING = 2;
const DETAIL_DEBOUNCE_MS = 500;
const DETAIL_MAX_CONCURRENT = 5;
/** 1m full-detail fetches are heavy — limit to 1 at a time to keep the UI responsive. */
const DETAIL_MAX_CONCURRENT_1M = 1;

/** Minimum zoom level to show individual GPS track points with direction arrows. */
const TRACK_POINTS_MIN_ZOOM = 16;
const TRACK_POINT_ARROW_ICON_SIZE = 24;
const TRACK_POINT_ARROW_COLOR = '#2563eb';
const TRACK_POINT_ARROW_BACKGROUND_COLOR = '#ffffff';
const DEFAULT_DEVICE_PIXEL_RATIO = 1;

/** Zoom threshold: entering globe — auto-activates when zooming OUT past this level. */
const GLOBE_ENTER_ZOOM = 3;

/**
 * Zoom threshold: leaving globe — auto-deactivates only when zooming IN past this level.
 * Higher than GLOBE_ENTER_ZOOM so there's a hysteresis band (no flicker at the boundary).
 */
const GLOBE_EXIT_ZOOM = 3.8;

/** Minimum zoom allowed in mercator mode — zoom 1 shows the full world. */
const MERCATOR_MIN_ZOOM = 1.0;
const MILLISECONDS_PER_MINUTE = 60 * 1000;
const MAP_LOAD_WATCHDOG_MS = 7000;
const DATA_FRESHNESS_DISMISS_MINUTES = 30;
const DATA_FRESHNESS_DISMISS_MS = DATA_FRESHNESS_DISMISS_MINUTES * MILLISECONDS_PER_MINUTE;
const LOCATION_SEARCH_FLY_DURATION_MS = 900;
const DEFAULT_MAP_ZOOM = 10;
const INITIAL_TRACK_BOUNDS_PADDING = 48;
const INITIAL_TRACK_BOUNDS_MAX_ZOOM = 13;
const MAP_STATUS_POLL_INTERVAL_MS = 5000;
const MAP_STATUS_POLL_TIMEOUT_MS = 8000;
const LOCAL_VECTOR_STYLE_MODE = 'local-vector';
const LOCAL_VECTOR_SOURCE_ID = 'protomaps';

let runtimeRasterFallbackLoggedThisSession = false;

function initialBoundsFromConfig(bounds) {
  if (!bounds) return null;
  const { minLng, minLat, maxLng, maxLat } = bounds;
  if (
    !Number.isFinite(minLng) ||
    !Number.isFinite(minLat) ||
    !Number.isFinite(maxLng) ||
    !Number.isFinite(maxLat) ||
    minLng >= maxLng ||
    minLat >= maxLat
  ) {
    return null;
  }
  return [
    [minLng, minLat],
    [maxLng, maxLat],
  ];
}

function centerFromBounds(bounds) {
  return [(bounds[0][0] + bounds[1][0]) / 2, (bounds[0][1] + bounds[1][1]) / 2];
}

function isAbortLikeError(error) {
  return (
    (error instanceof DOMException && error.name === 'AbortError') ||
    (error instanceof Error && error.name === 'AbortError') ||
    axios.isCancel(error)
  );
}

/**
 * Create a direction-arrow image (canvas-based) for use as a MapLibre icon.
 * The arrow points UP (north) — icon-rotate handles orientation.
 */
function createArrowImage(
  size = TRACK_POINT_ARROW_ICON_SIZE,
  color = TRACK_POINT_ARROW_COLOR,
  bgColor = TRACK_POINT_ARROW_BACKGROUND_COLOR
) {
  const canvas = document.createElement('canvas');
  const ratio =
    Number.isFinite(window.devicePixelRatio) && window.devicePixelRatio > 0
      ? window.devicePixelRatio
      : DEFAULT_DEVICE_PIXEL_RATIO;
  const px = Math.max(1, Math.round(size * ratio));
  const pixelRatio = px / size;
  canvas.width = px;
  canvas.height = px;
  const ctx = canvas.getContext('2d');
  ctx.scale(pixelRatio, pixelRatio);

  const cx = size / 2;
  const cy = size / 2;
  const r = size / 2 - 1;

  // White filled circle background
  ctx.beginPath();
  ctx.arc(cx, cy, r, 0, Math.PI * 2);
  ctx.fillStyle = bgColor;
  ctx.fill();
  ctx.strokeStyle = color;
  ctx.lineWidth = 1.5;
  ctx.stroke();

  // Arrow pointing up (chevron / arrowhead)
  const arrowH = size * 0.38;
  const arrowW = size * 0.36;
  const top = cy - arrowH * 0.55;

  ctx.beginPath();
  ctx.moveTo(cx, top); // tip
  ctx.lineTo(cx + arrowW / 2, top + arrowH); // bottom-right
  ctx.lineTo(cx, top + arrowH * 0.6); // inner notch
  ctx.lineTo(cx - arrowW / 2, top + arrowH); // bottom-left
  ctx.closePath();
  ctx.fillStyle = color;
  ctx.fill();

  const imgData = ctx.getImageData(0, 0, px, px);
  return { width: px, height: px, data: imgData.data, pixelRatio };
}

// Register PMTiles protocol once at module level
let pmtilesProtocol = null;
let pmtilesProtocolAdded = false;
function ensurePMTilesProtocol() {
  if (pmtilesProtocolAdded) return;
  pmtilesProtocol = new Protocol();
  maplibregl.addProtocol('pmtiles', pmtilesProtocol.tile);
  pmtilesProtocolAdded = true;
}

export function useMainMapController(props, emit, toast, templateRefs) {
  const { isIndexing, isJobPending } = useIndexerStatus();
  const {
    serverFreshnessToken,
    lastChecked: dataFreshnessLastChecked,
    refresh: refreshDataFreshness,
    isFreshnessPollingHealthy,
  } = useDataFreshness();
  const isAnyPending = computed(() => isIndexing.value || isJobPending.value);
  const mapPreferences = useMapPreferences();
  const initialDismissedFreshness = getDismissedDataFreshness();

  const state = reactive({
    map: undefined, // base map (tiles + mobility overlays)
    overlayMap: undefined, // overlay map (tracks, highlights, media)
    mapConfig: null,
    mapServerStatus: null,
    mapStatusPollTimer: null,
    mapArchiveStaleReloading: false,
    baseMapStyleMode: 'unknown',
    baseMapRuntimeFallbackApplied: false,
    zoom: 0,
    geojson: undefined,
    globeMode: false, // true when globe projection is active
    globeUserDisabled: false, // true when user explicitly turned off globe at low zoom
    mapCenter: [8.505778, 47.5605], // [lng, lat] for MapLibre — Glattfelden

    gpsMarker: null,
    gpsLocation: null, // [lat, lng] — last known GPS position
    gpsDeviceEnabledDisabled: false,
    gpsFollowing: false, // true = map auto-centres on GPS updates
    showLoader: false,
    loadingTrackBatches: false,
    loadingTracks10m: false,
    mapThemes: [
      { name: 'OSM Topo', code: 'light-topo', thumbnail: thumbOsmTopo, featured: true },
      { name: 'Swiss Color', code: 'swisstopo-color', thumbnail: thumbSwissColor, featured: true },
      { name: 'Swiss Light', code: 'swisstopo', thumbnail: thumbSwissLight },
      { name: 'OSM Light', code: 'light', thumbnail: thumbOsmLight },
      { name: 'OSM Dark', code: 'dark', thumbnail: thumbOsmDark },
    ],
    mapThemeSelected: mapPreferences.readTheme(),
    visibleTrackCount: 0,
    totalTrackCount: 0,
    basemapEnabled: true,
    tracksEnabled: true,
    layerOpacities: { ...DEFAULT_LAYER_OPACITIES },
    filterActive: false,
    colorPalette: markRaw(new ColorPalette()),
    legendEntries: [],
    legendMode: 'categorical',
    legendGradientColors: [],
    legendGradientBucketCount: 100,
    legendCollapsed: mapPreferences.readLegendCollapsed(),
    hiddenGroups: new Set(),
    gpsTracksById: markRaw(new Map()),
    gpsTrackIdToFeature: markRaw(new Map()),
    selectedTrackId: null,
    selectedFeature: null,
    trackSelectionSheetVisible: false,
    selectionPopupTrackIds: [],
    swissMobilityPopup: { visible: false, pos: { x: 0, y: 0 }, routes: [] },
    proximityAbortController: null,
    trackDetailsVisible: false,
    trackDetailsDetents: TRACK_DETAILS_DETENTS,
    trackDetailsInitialDetent: TRACK_DETAILS_DEFAULT_DETENT,
    trackDetailsId: null,
    trackDetailsInfo: { id: null, name: '', description: '', activityType: '' },
    locationSearchVisible: false,
    locationSearchMarker: null,
    measureToolActive: false,
    plannerToolActive: false,
    activeToolId: null,
    toolDefs: [
      // Row 1: primary (shown at peek)
      { id: 'stats', icon: 'bi bi-graph-up', label: 'Stats' },
      { id: 'filter', icon: 'bi bi-funnel', alertIcon: 'bi bi-funnel-fill', label: 'Filter' },
      // Row 2: secondary (shown when expanded)
      { id: 'map', icon: 'bi bi-map', label: 'Map' },
      { id: 'animate', icon: 'bi bi-play-circle', label: 'Animate' },
      { id: 'measure', icon: 'bi bi-stopwatch', label: 'Segments' },
      {
        id: 'gps',
        icon: 'bi bi-geo-fill',
        alertIcon: 'bi bi-geo-alt-fill',
        driftedIcon: 'bi bi-geo-alt-fill',
        label: 'GPS',
      },
      { id: 'admin', icon: 'bi bi-gear', label: 'Admin' },
    ],
    mediaOverlay: null,
    mediaVisible: false,
    mediaBusy: false,
    mediaSheetVisible: false,
    mediaSheetMediaId: null,
    mediaLoadedPoints: [],
    mediaNavList: [],
    heatmapOverlay: null,
    heatmapVisible: false,
    isOffline: false,
    cachedTracksLoaded: false,
    initialLoadDone: false,
    dismissedFreshnessToken: initialDismissedFreshness?.token ?? null,
    dismissedFreshnessExpiresAt: initialDismissedFreshness?.expiresAt ?? 0,
    freshnessDismissTimer: null,
    freshnessReloading: false,
    freshLoginAutoFreshenDone: false,
    retryTimeoutId: null,
    retryCount: 0,
    bulk10mController: null,
    trackPrecisions: markRaw(new Map()),
    activeTrackFilterResult: null,
    detailAbortController: null,
    detailDebounceTimer: null,
    activeOverlays: [],
    _syncingView: false, // guard to prevent recursive view-sync loops
    trackPointsVisible: true, // toggle for direction-arrow point markers
    // Key: `${trackId}|${precision}` — cache must invalidate when the
    // underlying SHAPE variant changes (precision upgrades from 10m → 1m),
    // because pointIndex only matches within the same precision level.
    trackPointsDetailsCache: markRaw(new Map()), // `${trackId}|${precision}` → GpsTrackDataPoint[]
    // Canonical RAW_OUTLIER_CLEANED per-point dataset cache for popup metric
    // lookups. Indexed by trackId — canonical density never changes for a
    // given track, so no precision component is needed.
    trackPointsCanonicalCache: markRaw(new Map()), // trackId → GpsTrackDataPoint[]
    trackPointsPopup: null, // active MapLibre popup for a clicked point
    trackPointLayerHandlers: null,
    // Geo drawing
    geoDrawingOverlay: null,
    geoDrawingParamDef: null,
    geoDrawPointCount: 0,
    // Map-ready gate: resolved when initMap() finishes (maps created + loaded).
    // fetchTracksAndFallback runs in parallel and awaits this before addTracksToMap.
    _mapReadyResolve: null,
    _mapReadyPromise: null,
  });

  const setupBindings = {
    isIndexing: isAnyPending,
    serverFreshnessToken,
    dataFreshnessLastChecked,
    refreshDataFreshness,
    isFreshnessPollingHealthy,
  };

  const refsProxy = new Proxy(
    {},
    {
      get(_target, key) {
        if (typeof key !== 'string') return undefined;
        return templateRefs[key]?.value;
      },
    }
  );
  let mapStatusPollingActive = false;
  let mapStatusPollGeneration = 0;

  const computedDefinitions = {
    selectionPopupTracks() {
      return this.selectionPopupTrackIds.map((id) => this.getTrackPopupMeta(id));
    },
    baseMapStyle() {
      // Basemap slider: combines desaturation, brightening, and opacity fade.
      //   slider 100 → normal map
      //   slider   0 → fully invisible
      if (!this.basemapEnabled) return { opacity: 0.08 };
      const pct = this.layerOpacities.basemap; // 0‒100
      if (pct >= 100) return {};
      const dim = (100 - pct) / 100; // 0‒1  (0 = normal, 1 = max dim)
      const brightness = 1 + 0.4 * dim;
      const opacity = pct / 100; // 1 at 100%, 0 at 0%
      return {
        filter: `grayscale(${dim}) brightness(${brightness})`,
        opacity,
      };
    },
    layerStatesForPanel() {
      return {
        basemap: { enabled: this.basemapEnabled, opacity: this.layerOpacities.basemap },
        tracks: { enabled: this.tracksEnabled, opacity: this.layerOpacities.tracks },
        media: { enabled: this.mediaVisible, opacity: this.layerOpacities.media },
        trackpoints: { enabled: this.trackPointsVisible, opacity: this.layerOpacities.trackpoints },
        heatmap: { enabled: this.heatmapVisible, opacity: this.layerOpacities.heatmap },
        wanderland: { enabled: this.activeOverlays.includes('wanderland'), opacity: this.layerOpacities.wanderland },
        veloland: { enabled: this.activeOverlays.includes('veloland'), opacity: this.layerOpacities.veloland },
        mountainbikeland: {
          enabled: this.activeOverlays.includes('mountainbikeland'),
          opacity: this.layerOpacities.mountainbikeland,
        },
        wanderwege: { enabled: this.activeOverlays.includes('wanderwege'), opacity: this.layerOpacities.wanderwege },
        'wmt-hiking': {
          enabled: this.activeOverlays.includes('wmt-hiking'),
          opacity: this.layerOpacities['wmt-hiking'],
        },
        'wmt-cycling': {
          enabled: this.activeOverlays.includes('wmt-cycling'),
          opacity: this.layerOpacities['wmt-cycling'],
        },
        'wmt-mtb': { enabled: this.activeOverlays.includes('wmt-mtb'), opacity: this.layerOpacities['wmt-mtb'] },
      };
    },
    isMediaVisible() {
      return this.mediaVisible;
    },
    mediaCurrentIndex() {
      if (!this.mediaSheetMediaId || !this.mediaNavList.length) return -1;
      return this.mediaNavList.findIndex((p) => p.id === this.mediaSheetMediaId);
    },
    mediaPrevId() {
      const i = this.mediaCurrentIndex;
      if (i <= 0) return null;
      return this.mediaNavList[i - 1].id;
    },
    mediaNextId() {
      const i = this.mediaCurrentIndex;
      if (i < 0 || i >= this.mediaNavList.length - 1) return null;
      return this.mediaNavList[i + 1].id;
    },
    showLocationSearchFab() {
      return (
        !this.locationSearchVisible &&
        !this.activeToolId &&
        !this.measureToolActive &&
        !this.plannerToolActive &&
        !this.geoDrawingParamDef &&
        !this.trackDetailsVisible &&
        !this.trackSelectionSheetVisible &&
        !this.mediaSheetVisible
      );
    },
    locationSearchMapCenter() {
      const center = this.overlayMap?.getCenter?.();
      if (center) {
        return { lon: center.lng, lat: center.lat };
      }
      return { lon: this.mapCenter[0], lat: this.mapCenter[1] };
    },
    trackBrowserTracks() {
      return markRaw(Array.from(this.gpsTracksById.values()));
    },
    alertToolIds() {
      const ids = [];
      if (this.filterActive) ids.push('filter');
      // GPS following: show as alert (blue pulse). Drifted state is handled via driftedToolIds.
      if (this.gpsDeviceEnabledDisabled && this.gpsFollowing) ids.push('gps');
      if (this.isIndexing) ids.push('admin');
      return ids;
    },
    driftedToolIds() {
      const ids = [];
      // GPS on but map not following user position
      if (this.gpsDeviceEnabledDisabled && !this.gpsFollowing) ids.push('gps');
      return ids;
    },
    geoDrawToolbarIcon() {
      const t = this.geoDrawingParamDef?.type;
      if (t === 'GEO_CIRCLE') return 'bi bi-circle';
      if (t === 'GEO_RECTANGLE') return 'bi bi-bounding-box';
      return 'bi bi-pentagon';
    },
    geoDrawToolbarLabel() {
      const t = this.geoDrawingParamDef?.type;
      if (t === 'GEO_CIRCLE') return 'Draw Circle';
      if (t === 'GEO_RECTANGLE') return 'Draw Rectangle';
      return 'Draw Polygon';
    },
    geoDrawToolbarHint() {
      if (!this.geoDrawingOverlay) return '';
      const t = this.geoDrawingParamDef?.type;
      const pts = this.geoDrawPointCount;
      if (t === 'GEO_CIRCLE') return pts === 0 ? 'Click to place center' : 'Click to set radius';
      if (t === 'GEO_RECTANGLE') return pts === 0 ? 'Click to place first corner' : 'Click to place opposite corner';
      if (pts === 0) return 'Click to place first point';
      if (pts < 3) return `${pts} point${pts > 1 ? 's' : ''} — need at least 3`;
      return `${pts} points — double-click or press Finish`;
    },
    geoDrawIsPolygon() {
      return this.geoDrawingParamDef?.type === 'GEO_POLYGON';
    },
    geoDrawCanUndo() {
      return this.geoDrawingOverlay?.canUndo() ?? false;
    },
    geoDrawCanFinish() {
      return this.geoDrawingOverlay?.canFinish() ?? false;
    },
    showDataFreshnessBanner() {
      if (!this.initialLoadDone || this.freshnessReloading) return false;
      if (this.shouldAutoFreshenAfterLogin()) return false;
      const serverToken = this.serverFreshnessToken;
      const appliedToken = getAppliedDataFreshnessToken();
      if (!serverToken || !appliedToken) return false;
      if (serverToken === appliedToken) return false;
      const dismissedToken = this.dismissedFreshnessExpiresAt > Date.now() ? this.dismissedFreshnessToken : null;
      return dismissedToken !== serverToken;
    },
  };

  const methodDefinitions = {
    openLocationSearch() {
      this.closeAllToolsExcept(null);
      this.closeTransientOverlaysForToolSwitch();
      this.closeSelectionPopup();
      this.closeSwissMobilityPopup();
      this.activeToolId = null;
      this.locationSearchVisible = true;
    },

    onLocationSearchSelect(result) {
      const lat = Number(result?.lat);
      const lon = Number(result?.lon);
      if (!Number.isFinite(lat) || !Number.isFinite(lon)) return;

      this.closeSelectionPopup();
      this.closeSwissMobilityPopup();
      this.deselectTrack();
      this.locationSearchVisible = false;
      this.mapCenter = [lon, lat];
      this.setLocationSearchMarker(lon, lat);

      const targetMap = this.overlayMap || this.map;
      targetMap?.flyTo({
        center: [lon, lat],
        zoom: this.locationSearchTargetZoom(result),
        duration: LOCATION_SEARCH_FLY_DURATION_MS,
        essential: true,
      });
    },

    setLocationSearchMarker(lon, lat) {
      if (!this.overlayMap) return;
      this.clearLocationSearchMarker();
      const el = document.createElement('div');
      el.className = 'mtl-location-search-marker';
      el.innerHTML = `
    <i class="bi bi-geo-alt-fill"></i>
    <button class="mtl-location-search-marker__clear" type="button" aria-label="Clear search marker" title="Clear">
      <i class="bi bi-x"></i>
    </button>
  `;
      el.querySelector('.mtl-location-search-marker__clear')?.addEventListener('click', (event) => {
        event.preventDefault();
        event.stopPropagation();
        this.clearLocationSearchMarker();
      });
      this.locationSearchMarker = markRaw(
        new maplibregl.Marker({ element: el, anchor: 'bottom' }).setLngLat([lon, lat]).addTo(this.overlayMap)
      );
    },

    clearLocationSearchMarker() {
      if (!this.locationSearchMarker) return;
      this.locationSearchMarker.remove();
      this.locationSearchMarker = null;
    },

    locationSearchTargetZoom(result) {
      return resolveLocationSearchTargetZoom(result);
    },

    shouldAutoFreshenAfterLogin() {
      if (!this.fromLogin || this.freshLoginAutoFreshenDone || this.freshnessReloading) return false;
      const serverToken = this.serverFreshnessToken;
      const appliedToken = getAppliedDataFreshnessToken();
      return Boolean(this.initialLoadDone && serverToken && appliedToken && serverToken !== appliedToken);
    },

    maybeAutoFreshenAfterLogin() {
      if (!this.shouldAutoFreshenAfterLogin()) return;
      this.freshLoginAutoFreshenDone = true;
      startupLog('tracks', 'Freshness changed after login; refreshing map automatically');
      console.info('[MTL] Freshness changed after login; refreshing map automatically.');
      void this.onDataFreshnessReload({ silent: true });
    },

    async captureAppliedFreshnessToken() {
      const freshness = await this.refreshDataFreshness();
      const token = freshness?.freshnessToken ?? this.serverFreshnessToken;
      if (!token) return;
      setAppliedDataFreshnessToken(token);
      this.clearFreshnessDismissal();
    },

    async clearTrackCacheWhenServerFreshnessChanged() {
      const freshness = await this.refreshDataFreshness();
      const serverToken = freshness?.freshnessToken ?? this.serverFreshnessToken;
      if (!serverToken) return false;

      const appliedToken = getAppliedDataFreshnessToken();
      if (serverToken === appliedToken) return false;

      startupLog('tracks', 'Server freshness token changed; clearing local track cache before load');
      this.trackPointsDetailsCache.clear();
      this.trackPointsCanonicalCache.clear();
      if (this.detailDebounceTimer) {
        clearTimeout(this.detailDebounceTimer);
        this.detailDebounceTimer = null;
      }
      if (this.detailAbortController) {
        this.detailAbortController.abort();
        this.detailAbortController = null;
      }
      if (this.bulk10mController) {
        this.bulk10mController.abort();
        this.bulk10mController = null;
      }
      await clearTrackCache();
      return true;
    },

    async onDataFreshnessReload(options = {}) {
      if (this.freshnessReloading) return false;
      const silent = options?.silent === true;
      this.freshnessReloading = true;
      this.showLoader = true;
      this.loadingTrackBatches = true;
      try {
        await this.clearTrackCacheWhenServerFreshnessChanged();
        const collectionPrecision = this.currentCollectionPrecision();
        const serverData = await loadTrackCollectionPaged(collectionPrecision, {
          onPage: (page) => this.mergeTrackPage(page),
          pageSize: TRACK_LOAD_BATCH_SIZE,
        });
        this.totalTrackCount = serverData.standardFilterCount;
        if (this.geojson) {
          await this.mergeTrackResult(serverData, { pruneMissing: true });
        } else {
          await this.loadMapData(serverData);
        }
        this.cachedTracksLoaded = true;
        this.initialLoadDone = true;
        this.isOffline = false;
        this.maybeLoadBackgroundTracks(serverData.filterResult);
        this.scheduleDetailCheck();
        await this.captureAppliedFreshnessToken();
        if (!silent) {
          this.$toast.add({ severity: 'success', summary: 'Map updated', detail: 'Fresh data loaded.', life: 2500 });
        }
        return true;
      } catch (error) {
        if (!silent) {
          this.$toast.add({
            severity: 'error',
            summary: 'Reload failed',
            detail: 'Fresh data could not be loaded.',
            life: 4000,
          });
        }
        startupWarn('tracks', 'Data freshness reload failed', describeError(error));
        return false;
      } finally {
        this.loadingTrackBatches = false;
        this.showLoader = false;
        this.freshnessReloading = false;
      }
    },

    onDataFreshnessDismiss() {
      const token = this.serverFreshnessToken;
      if (!token) return;
      const dismissed = setDismissedDataFreshness(token, DATA_FRESHNESS_DISMISS_MS);
      if (!dismissed) return;
      this.dismissedFreshnessToken = dismissed.token;
      this.dismissedFreshnessExpiresAt = dismissed.expiresAt;
      this.scheduleFreshnessDismissTimer(dismissed.token, dismissed.expiresAt);
    },

    clearFreshnessDismissal() {
      clearDismissedDataFreshness();
      this.dismissedFreshnessToken = null;
      this.dismissedFreshnessExpiresAt = 0;
      if (this.freshnessDismissTimer) {
        clearTimeout(this.freshnessDismissTimer);
        this.freshnessDismissTimer = null;
      }
    },

    scheduleFreshnessDismissTimer(token = this.dismissedFreshnessToken, expiresAt = this.dismissedFreshnessExpiresAt) {
      if (this.freshnessDismissTimer) {
        clearTimeout(this.freshnessDismissTimer);
        this.freshnessDismissTimer = null;
      }
      if (!token || !Number.isFinite(expiresAt)) return;

      const delayMs = expiresAt - Date.now();
      if (delayMs <= 0) {
        this.clearFreshnessDismissal();
        return;
      }

      this.freshnessDismissTimer = setTimeout(() => {
        if (this.dismissedFreshnessToken === token && this.dismissedFreshnessExpiresAt === expiresAt) {
          this.clearFreshnessDismissal();
        }
      }, delayMs);
    },

    currentCollectionPrecision() {
      const lastKnownZoom = Number.isFinite(this.zoom) && this.zoom > 0 ? this.zoom : DEFAULT_MAP_ZOOM;
      const zoom = this.overlayMap?.getZoom?.() ?? lastKnownZoom;
      return collectionPrecisionForZoom(zoom);
    },

    maybeLoadBackgroundTracks(filterResult = this.activeTrackFilterResult) {
      if (!this.geojson || this.loadingTracks10m) return false;
      if (this.currentCollectionPrecision() !== BACKGROUND_TRACK_PRECISION) return false;
      for (const feature of this.geojson.features) {
        const trackId = Number(feature.properties?.id);
        if (!Number.isFinite(trackId)) continue;
        const precision = this.trackPrecisions.get(trackId) ?? OVERVIEW_PRECISION;
        if (precision > BACKGROUND_TRACK_PRECISION) {
          this.loadAllTracksAt10m(filterResult);
          return true;
        }
      }
      return false;
    },

    async resolveTrackLineColor() {
      const clientFilterConfig = await FilterService.loadClientFilterConfig();
      const palette = clientFilterConfig?.palette;
      const filterInfo = clientFilterConfig?.filterInfo;
      const filterConfig = filterInfo?.filterConfig;
      const legendSortStrategy = clientFilterConfig?.legendSortStrategy;
      const hasPalette = Boolean(palette && !palette.isEmptyColorPalette());

      palette?.reset();
      if (!hasPalette) {
        this.filterActive = FilterService.hasActiveFilterConfig(clientFilterConfig);
        this.legendEntries = [];
        this.legendMode = 'categorical';
        this.legendGradientColors = [];
        this.legendGradientBucketCount = 100;
        return TRACK_COLOR;
      }

      this.filterActive = true;
      const isGradientLegend = shouldUseCompactGradientLegend(filterConfig, palette, legendSortStrategy);
      this.legendMode = isGradientLegend ? 'gradient' : 'categorical';
      this.legendGradientColors = isGradientLegend ? [...(palette.pColors ?? [])] : [];
      this.legendGradientBucketCount = isGradientLegend ? gradientBucketCount(filterInfo) : 100;

      const groupCounts = new Map();
      for (const feature of this.geojson?.features ?? []) {
        const group = feature.properties?.filterGroup;
        if (!group) continue;
        groupCounts.set(group, (groupCounts.get(group) || 0) + 1);
      }

      const legendEntries = Array.from(groupCounts.entries()).map(([group, count]) => ({ group, count }));
      const sortedEntries = legendSortStrategy
        ? legendEntries.sort((a, b) => compareLegendEntries(a, b, filterConfig, legendSortStrategy))
        : this.orderLegendEntriesByFilterResult(legendEntries, this.activeTrackFilterResult?.legendGroupOrder);
      const colorMap = new Map();
      for (const entry of sortedEntries) {
        colorMap.set(entry.group, colorForFilterGroup(palette, entry.group, filterInfo));
      }

      this.colorPalette = palette;
      this.legendEntries = sortedEntries.map(({ group, count }) => ({
        group,
        label: formatFilterGroupLabel(group, filterInfo),
        color: colorMap.get(group) ?? TRACK_COLOR,
        count,
      }));

      if (colorMap.size === 0) return TRACK_COLOR;
      const matchExpr = ['match', ['get', 'filterGroup']];
      for (const [group, color] of colorMap) {
        matchExpr.push(group, color);
      }
      matchExpr.push(TRACK_COLOR);
      return matchExpr;
    },

    async updateTrackStyle() {
      if (!this.overlayMap || !this.geojson) return;
      const lineColor = await this.resolveTrackLineColor();
      if (this.overlayMap.getLayer('tracks-layer')) {
        this.overlayMap.setPaintProperty('tracks-layer', 'line-color', lineColor);
      }
      if (this.overlayMap.getLayer('tracks-dot-layer')) {
        this.overlayMap.setPaintProperty('tracks-dot-layer', 'circle-color', lineColor);
      }
      if (this.overlayMap.getLayer('tracks-overview-dots')) {
        this.overlayMap.setPaintProperty('tracks-overview-dots', 'circle-color', lineColor);
      }
    },

    orderLegendEntriesByFilterResult(entries, groupOrder) {
      if (!groupOrder?.length) return entries;
      const byGroup = new Map(entries.map((entry) => [entry.group, entry]));
      const ordered = [];
      const seen = new Set();
      for (const group of groupOrder) {
        const entry = byGroup.get(group);
        if (!entry || seen.has(group)) continue;
        ordered.push(entry);
        seen.add(group);
      }
      for (const entry of entries) {
        if (!seen.has(entry.group)) ordered.push(entry);
      }
      return ordered;
    },

    /**
     * Compute raster paint properties for a route overlay at a given slider value.
     * Mirrors the basemap's smart-fade approach:
     *   slider 100 → full color, full opacity
     *   slider  50 → desaturated, slightly washed-out, still fully opaque
     *   slider   0 → fully grey, brightened, nearly invisible
     */
    _overlayPaintForSlider(slider, hueRotate) {
      const dim = Math.max(0, Math.min(1, (100 - slider) / 100));
      return {
        'raster-opacity': 1 - dim * 0.85, // 1.0 → 0.15
        'raster-saturation': -dim, // 0   → -1 (grayscale)
        'raster-brightness-max': 1 - dim * 0.25, // 1.0 → 0.75
        ...(hueRotate !== undefined ? { 'raster-hue-rotate': hueRotate } : {}),
      };
    },

    /**
     * Return the id of the first existing overlay layer that route overlays
     * should render below, to keep them at the bottom of the overlay stack
     * (above transparent background, below heatmap/tracks/media).
     */
    _overlayBeforeId() {
      if (!this.overlayMap) return undefined;
      for (const id of [
        'heatmap-layer',
        'tracks-layer',
        'tracks-overview-dots',
        'media-clusters',
        'media-unclustered',
      ]) {
        if (this.overlayMap.getLayer(id)) return id;
      }
      return undefined;
    },

    /** Add all currently-active overlay layers to the overlay map. */
    applyActiveOverlays() {
      if (!this.overlayMap) return;
      const beforeId = this._overlayBeforeId();
      for (const overlay of MAP_OVERLAYS) {
        if (!this.activeOverlays.includes(overlay.id)) continue;
        if (!this.overlayMap.getSource(overlay.id)) {
          this.overlayMap.addSource(overlay.id, {
            type: 'raster',
            tiles: [overlay.url],
            tileSize: 256,
            attribution: overlay.attribution,
          });
        }
        if (!this.overlayMap.getLayer(`${overlay.id}-overlay`)) {
          this.overlayMap.addLayer(
            {
              id: `${overlay.id}-overlay`,
              type: 'raster',
              source: overlay.id,
              minzoom: 0,
              maxzoom: 22,
              paint: this._overlayPaintForSlider(this.layerOpacities[overlay.id] ?? 100, overlay.hueRotate),
            },
            beforeId
          );
        }
      }
    },

    /** Remove all overlay layers and sources from the overlay map. */
    removeAllOverlays() {
      if (!this.overlayMap) return;
      for (const overlay of MAP_OVERLAYS) {
        if (this.overlayMap.getLayer(`${overlay.id}-overlay`)) this.overlayMap.removeLayer(`${overlay.id}-overlay`);
        if (this.overlayMap.getSource(overlay.id)) this.overlayMap.removeSource(overlay.id);
      }
    },

    /** Reset all map settings to their defaults. */
    onResetMapSettings() {
      // Theme
      this.onMapThemeChangeEvent('light-topo');
      // Basemap
      if (!this.basemapEnabled) {
        this.basemapEnabled = true;
      }
      // Tracks
      this.tracksEnabled = true;
      this.applyTracksVisibility();
      // Media
      if (this.mediaVisible) this.onToggleMediaLayer();
      // Heatmap
      if (this.heatmapVisible) this.onToggleHeatmapLayer();
      // Track points
      if (!this.trackPointsVisible) {
        this.trackPointsVisible = true;
        this.updateTrackPointsSource();
      }
      // Swiss Mobility overlays
      for (const id of [...this.activeOverlays]) {
        this.onToggleOverlay(id);
      }
      // Opacities
      this.layerOpacities = {
        basemap: 100,
        tracks: 100,
        media: 100,
        trackpoints: 100,
        heatmap: 100,
        wanderland: 100,
        veloland: 100,
        mountainbikeland: 100,
        wanderwege: 100,
        'wmt-hiking': 100,
        'wmt-cycling': 100,
        'wmt-mtb': 100,
      };
      this.applyAllLayerOpacities();
      this.persistLayerStates();
    },

    /** Persist all layer opacities and toggle states to localStorage. */
    persistLayerStates() {
      mapPreferences.writeLayerOpacities(this.layerOpacities);
      mapPreferences.writeBasemapEnabled(this.basemapEnabled);
      mapPreferences.writeTracksEnabled(this.tracksEnabled);
      mapPreferences.writeTrackPointsVisible(this.trackPointsVisible);
      mapPreferences.writeHeatmapVisible(this.heatmapVisible);
      mapPreferences.writeActiveOverlays(this.activeOverlays);
    },

    /** Unified handler for toggling any layer on/off. */
    onToggleLayer(layerId) {
      switch (layerId) {
        case 'basemap':
          this.basemapEnabled = !this.basemapEnabled;
          this.applyLayerOpacity('basemap');
          break;
        case 'tracks':
          this.tracksEnabled = !this.tracksEnabled;
          this.applyTracksVisibility();
          break;
        case 'media':
          this.onToggleMediaLayer();
          break;
        case 'trackpoints':
          this.onToggleTrackPoints();
          break;
        case 'heatmap':
          this.onToggleHeatmapLayer();
          break;
        case 'wanderland':
        case 'veloland':
        case 'mountainbikeland':
        case 'wanderwege':
        case 'wmt-hiking':
        case 'wmt-cycling':
        case 'wmt-mtb':
          this.onToggleOverlay(layerId);
          break;
      }
      this.persistLayerStates();
    },

    /** Unified handler for changing layer opacity. */
    onLayerOpacityChange(layerId, value) {
      this.layerOpacities[layerId] = value;
      this.applyLayerOpacity(layerId);
      this.persistLayerStates();
    },

    /** Apply opacity for a specific layer to the map. */
    applyLayerOpacity(layerId) {
      const opacity = this.layerOpacities[layerId] / 100;
      switch (layerId) {
        case 'basemap':
          // CSS filter handles visual dimming via `baseMapStyle` computed.
          // Hillshade also depends on basemap dim → update it.
          this._applyHillshade();
          break;
        case 'tracks':
          if (this.overlayMap) {
            if (this.overlayMap.getLayer('tracks-layer'))
              this.overlayMap.setPaintProperty('tracks-layer', 'line-opacity', opacity);
            if (this.overlayMap.getLayer('tracks-dot-layer')) {
              this.overlayMap.setPaintProperty('tracks-dot-layer', 'circle-opacity', opacity);
              this.overlayMap.setPaintProperty('tracks-dot-layer', 'circle-stroke-opacity', opacity);
            }
            if (this.overlayMap.getLayer('tracks-overview-dots')) {
              this.overlayMap.setPaintProperty('tracks-overview-dots', 'circle-opacity', opacity);
              this.overlayMap.setPaintProperty('tracks-overview-dots', 'circle-stroke-opacity', opacity);
            }
          }
          // Hillshade also depends on tracks opacity → update it.
          this._applyHillshade();
          break;
        case 'media':
          if (this.overlayMap) {
            if (this.overlayMap.getLayer('media-clusters'))
              this.overlayMap.setPaintProperty('media-clusters', 'circle-opacity', opacity);
            if (this.overlayMap.getLayer('media-cluster-count'))
              this.overlayMap.setPaintProperty('media-cluster-count', 'text-opacity', opacity);
            if (this.overlayMap.getLayer('media-unclustered'))
              this.overlayMap.setPaintProperty('media-unclustered', 'circle-opacity', opacity);
          }
          break;
        case 'trackpoints':
          if (this.overlayMap && this.overlayMap.getLayer('track-points-layer'))
            this.overlayMap.setPaintProperty('track-points-layer', 'icon-opacity', opacity);
          break;
        case 'heatmap':
          if (this.overlayMap && this.overlayMap.getLayer('heatmap-layer'))
            this.overlayMap.setPaintProperty('heatmap-layer', 'heatmap-opacity', opacity * 0.92);
          break;
        case 'wanderland':
        case 'veloland':
        case 'mountainbikeland':
        case 'wanderwege':
        case 'wmt-hiking':
        case 'wmt-cycling':
        case 'wmt-mtb': {
          const layerName = `${layerId}-overlay`;
          if (this.overlayMap && this.overlayMap.getLayer(layerName)) {
            const overlay = MAP_OVERLAYS.find((o) => o.id === layerId);
            const paint = this._overlayPaintForSlider(this.layerOpacities[layerId], overlay?.hueRotate);
            for (const [prop, val] of Object.entries(paint)) {
              this.overlayMap.setPaintProperty(layerName, prop, val);
            }
          }
          break;
        }
      }
    },

    /** Apply all current layer opacities after map init or reload. */
    applyAllLayerOpacities() {
      for (const layerId of Object.keys(this.layerOpacities)) {
        this.applyLayerOpacity(layerId);
      }
    },

    /**
     * Update terrain hillshade-exaggeration based on the basemap and tracks sliders.
     *
     * Hillshade is part of the basemap visual layer, so it should fade together
     * with the basemap slider. We also let the tracks slider dampen it slightly
     * so the terrain is less prominent when tracks are hidden.
     *
     *   basemap 100 % → 1.00 × base        (full relief)
     *   basemap   0 % → 0.00 × base        (no relief)
     *   tracks  100 % → ×  1.00            (no damping)
     *   tracks    0 % → ×  0.60            (extra soft)
     *
     * Base exaggeration is 0.55 at slider 100 %.
     */
    _applyHillshade() {
      if (!this.map || !this.map.getLayer('terrain-hillshade')) return;

      const basemapNorm = this.basemapEnabled ? Math.max(0, Math.min(1, this.layerOpacities.basemap / 100)) : 0;
      const tracksNorm = Math.max(0, Math.min(1, this.layerOpacities.tracks / 100));
      const tracksFactor = 0.6 + 0.4 * tracksNorm; // 0.6 … 1.0

      const exaggeration = 0.55 * basemapNorm * tracksFactor;

      this.map.setPaintProperty('terrain-hillshade', 'hillshade-exaggeration', exaggeration);
    },

    /** Show / hide all track-related layers on the overlay map. */
    applyTracksVisibility() {
      if (!this.overlayMap) return;
      const vis = this.tracksEnabled ? 'visible' : 'none';
      for (const id of ['tracks-layer', 'tracks-dot-layer', 'tracks-overview-dots']) {
        if (this.overlayMap.getLayer(id)) this.overlayMap.setLayoutProperty(id, 'visibility', vis);
      }
    },

    /** Toggle a Swiss Mobility overlay on/off without reinitialising the map. */
    onToggleOverlay(overlayId) {
      const idx = this.activeOverlays.indexOf(overlayId);
      if (idx === -1) {
        this.activeOverlays.push(overlayId);
        const overlay = MAP_OVERLAYS.find((o) => o.id === overlayId);
        if (overlay && this.overlayMap) {
          if (!this.overlayMap.getSource(overlay.id)) {
            this.overlayMap.addSource(overlay.id, {
              type: 'raster',
              tiles: [overlay.url],
              tileSize: 256,
              attribution: overlay.attribution,
            });
          }
          if (!this.overlayMap.getLayer(`${overlay.id}-overlay`)) {
            this.overlayMap.addLayer(
              {
                id: `${overlay.id}-overlay`,
                type: 'raster',
                source: overlay.id,
                minzoom: 0,
                maxzoom: 22,
                paint: this._overlayPaintForSlider(this.layerOpacities[overlayId], overlay.hueRotate),
              },
              this._overlayBeforeId()
            );
          }
        }
      } else {
        this.activeOverlays.splice(idx, 1);
        if (this.overlayMap) {
          if (this.overlayMap.getLayer(`${overlayId}-overlay`)) this.overlayMap.removeLayer(`${overlayId}-overlay`);
          if (this.overlayMap.getSource(overlayId)) this.overlayMap.removeSource(overlayId);
        }
      }
      mapPreferences.writeActiveOverlays(this.activeOverlays);
    },

    startMapStatusPolling() {
      this.stopMapStatusPolling();
      mapStatusPollingActive = true;
      const pollGeneration = ++mapStatusPollGeneration;
      const poll = async () => {
        if (!mapStatusPollingActive || pollGeneration !== mapStatusPollGeneration) return;
        try {
          const resp = await apiClient.get(`api/map/status`, {
            timeout: MAP_STATUS_POLL_TIMEOUT_MS,
          });
          const previousStatus = this.mapServerStatus;
          const wasReady = previousStatus?.ready;
          const previousSource = previousStatus?.tileSource;
          const previousArchive = previousStatus?.archive_id;
          this.mapServerStatus = resp.data;
          const currentSource = this.mapServerStatus?.tileSource;
          const currentArchive = this.mapServerStatus?.archive_id;
          const archiveChanged =
            Boolean(previousStatus) && (previousSource !== currentSource || previousArchive !== currentArchive);
          if (this.mapServerStatus?.ready) {
            if (currentSource !== MapConfigDtoTileSourceEnum.Public) {
              this.stopMapStatusPolling();
            }
            // Tiles just became ready, or the byte layout/source changed — rebuild
            // with a fresh server-provided PMTiles URL so browser range caches stay isolated.
            if (!wasReady || archiveChanged) {
              clearMapConfigCache();
              this.reloadMap(false);
            }
          }
        } catch {
          // ignore polling errors silently
        } finally {
          if (mapStatusPollingActive && pollGeneration === mapStatusPollGeneration) {
            this.mapStatusPollTimer = setTimeout(poll, MAP_STATUS_POLL_INTERVAL_MS);
          }
        }
      };
      poll();
    },

    stopMapStatusPolling() {
      mapStatusPollingActive = false;
      mapStatusPollGeneration += 1;
      if (this.mapStatusPollTimer) {
        clearTimeout(this.mapStatusPollTimer);
        this.mapStatusPollTimer = null;
      }
    },

    applyRuntimeRasterBasemapFallback(errorEvent, message, tileId) {
      if (this.baseMapRuntimeFallbackApplied || this.baseMapStyleMode !== LOCAL_VECTOR_STYLE_MODE || !this.map) {
        return false;
      }
      if (errorEvent?.sourceId !== LOCAL_VECTOR_SOURCE_ID) {
        return false;
      }

      const resolved = resolveConfiguredMapStyle({
        config: this.mapConfig,
        theme: this.mapThemeSelected,
        localTilesReady: false,
      });
      this.baseMapRuntimeFallbackApplied = true;
      this.baseMapStyleMode = resolved.styleMode;
      this.map.setStyle(resolved.style);
      startupWarn('mapload', 'Local vector basemap failed; switched to raster fallback', {
        message,
        sourceId: errorEvent?.sourceId ?? null,
        tile: tileId,
        fallbackStyleMode: resolved.styleMode,
      });
      if (!runtimeRasterFallbackLoggedThisSession) {
        runtimeRasterFallbackLoggedThisSession = true;
        console.warn(
          '[MTL] Local vector map tiles failed; switched base map to raster fallback for this session.',
          {
            message,
            sourceId: errorEvent?.sourceId ?? null,
            tile: tileId ?? null,
            fallbackStyleMode: resolved.styleMode,
          }
        );
      }
      return true;
    },

    async handleMapArchiveStale(event) {
      const staleUrl = event?.detail?.url;
      if (staleUrl && this.mapConfig && staleUrl !== mainTileArchiveUrl(this.mapConfig)) {
        return;
      }
      if (this.mapArchiveStaleReloading) {
        return;
      }
      this.mapArchiveStaleReloading = true;
      try {
        startupWarn('mapcache', 'PMTiles archive/source changed; reloading map config', event?.detail ?? {});
        this.stopMapStatusPolling();
        this.mapServerStatus = null;
        clearMapConfigCache();
        await this.reloadMap(false);
      } catch (error) {
        startupWarn('mapcache', 'Map reload after archive/source change failed', describeError(error));
      } finally {
        this.mapArchiveStaleReloading = false;
      }
    },

    async reloadMap(loadMedia) {
      const reloadTimer = startStartupTimer('reload', 'Reloading map state', { loadMedia });
      this.showLoader = true;

      // Reset state
      this.selectedTrackId = null;
      this.selectedFeature = null;
      this.closeSelectionPopup();
      this.closeSwissMobilityPopup();
      if (this.trackPointsPopup) {
        this.trackPointsPopup.remove();
        this.trackPointsPopup = null;
      }
      this.trackPointsDetailsCache.clear();
      this.trackPointsCanonicalCache.clear();
      if (this.detailDebounceTimer) clearTimeout(this.detailDebounceTimer);
      if (this.detailAbortController) this.detailAbortController.abort();
      if (this.bulk10mController) {
        this.bulk10mController.abort();
        this.bulk10mController = null;
      }
      this.loadingTrackBatches = false;
      this.trackPrecisions = markRaw(new Map());
      this.activeTrackFilterResult = null;

      // Clean up media overlay
      if (this.mediaOverlay && typeof this.mediaOverlay.destroy === 'function') {
        this.mediaOverlay.destroy();
      }
      this.mediaOverlay = null;
      this.mediaVisible = false;

      // Clean up heatmap overlay (keep heatmapVisible so it restores on next load)
      if (this.heatmapOverlay && typeof this.heatmapOverlay.destroy === 'function') {
        this.heatmapOverlay.destroy();
      }
      this.heatmapOverlay = null;

      // Clean up geo drawing overlay (it holds a reference to the old overlayMap)
      if (this.geoDrawingOverlay && typeof this.geoDrawingOverlay.destroy === 'function') {
        this.geoDrawingOverlay.destroy();
      }
      this.geoDrawingOverlay = null;

      // Clean up GPS marker (lives on overlay map which will be destroyed)
      if (this.gpsMarker) {
        this.gpsMarker.remove();
        this.gpsMarker = null;
      }
      this.clearLocationSearchMarker();

      try {
        // Phase 3: Start track fetch in parallel with map tile loading.
        // initMap() creates MapLibre instances and waits for their 'load' events.
        // fetchTracksAndFallback() hits cache/network for track data.
        // The actual addTracksToMap() (inside loadMapData) needs the map to be loaded,
        // so loadMapData awaits _mapReadyPromise before touching map sources.
        this._mapReadyPromise = new Promise((resolve) => {
          this._mapReadyResolve = resolve;
        });
        this.mapConfig = await fetchMapConfig();
        const trackDataPromise = this.fetchTracksAndFallback();
        await this.initMap();
        this._mapReadyResolve();
        // Map is ready — now wait for track data (may already be resolved from cache)
        await trackDataPromise;
        reloadTimer.success('Map reload completed');
      } catch (error) {
        reloadTimer.error('Map reload failed', describeError(error));
        throw error;
      }
    },

    async initMap() {
      const initTimer = startStartupTimer('mapinit', 'Initializing map');
      // Fetch map config from server (cached after first call)
      this.mapConfig = await fetchMapConfig();
      if (this.mapConfig.plannerEnabled && !this.toolDefs.some((t) => t.id === 'planner')) {
        const adminIdx = this.toolDefs.findIndex((t) => t.id === 'admin');
        const insertAt = adminIdx >= 0 ? adminIdx : this.toolDefs.length;
        this.toolDefs.splice(insertAt, 0, { id: 'planner', icon: 'bi bi-signpost-split', label: 'Planner' });
      }
      startupLog('mapinit', 'Map config resolved', {
        tileMode: this.mapConfig.tileMode,
        offline: this.mapConfig.offline ?? false,
        tileBaseUrl: this.mapConfig.tileBaseUrl,
        tileArchiveUrl: this.mapConfig.tileArchiveUrl,
        tileSource: this.mapConfig.tileSource,
        archiveId: this.mapConfig.archiveId,
      });

      // Preserve current viewport so theme switches don't jump the map position.
      // On first load, start from server-provided bounds and fit them after map load.
      const hadOverlayMap = !!this.overlayMap;
      const initialBounds = hadOverlayMap ? null : initialBoundsFromConfig(this.mapConfig.initialBounds);
      let initialCenter = initialBounds ? centerFromBounds(initialBounds) : this.mapCenter;
      let initialZoom = DEFAULT_MAP_ZOOM;
      if (hadOverlayMap) {
        initialCenter = [this.overlayMap.getCenter().lng, this.overlayMap.getCenter().lat];
        initialZoom = this.overlayMap.getZoom();
      }

      // Tear down previous maps
      if (this.overlayMap) {
        this.detachTrackPointLayerHandlers();
        this.overlayMap.remove();
        this.overlayMap = undefined;
      }
      if (this.map) {
        this.map.remove();
        this.map = undefined;
      }

      ensurePMTilesProtocol();
      // Pre-register PMTiles instances with force-cache fetch so Chrome serves
      // cached 206 responses from disk instead of revalidating every range request.
      if (pmtilesProtocol && this.mapConfig.tileBaseUrl && this.mapConfig.tilesetName) {
        const tileUrl = mainTileArchiveUrl(this.mapConfig);
        pmtilesProtocol.add(createCachingPMTiles(tileUrl));
      }
      startupLog('mapinit', 'PMTiles protocol ready');

      // When offline, skip the map-status probe and planet-file check entirely.
      // When local mode online, check once whether the planet file is already ready.
      if (
        !this.mapConfig.offline &&
        this.mapConfig.tileMode === MapConfigDtoTileModeEnum.Local &&
        !this.mapServerStatus
      ) {
        const statusTimer = startStartupTimer('mapstatus', 'Probing map server status');
        try {
          const resp = await apiClient.get(`api/map/status`, {
            timeout: 5000,
          });
          this.mapServerStatus = resp.data;
          statusTimer.success('Map server status received', {
            phase: resp.data?.phase,
            ready: resp.data?.ready,
            tileSource: resp.data?.tileSource,
            archiveId: resp.data?.archive_id,
          });
        } catch (error) {
          statusTimer.warn('Map server status probe failed', describeError(error));
          // Unreachable → treat as not ready, fall back to OSM
          this.mapServerStatus = { phase: 'unreachable', ready: false };
        }
      }

      // Build style based on config
      // If offline, use a lightweight raster fallback so the map 'load' event fires reliably.
      // If local but planet file not yet ready, use OSM raster temporarily.
      let style;
      let styleMode = 'unknown';
      if (this.mapConfig.offline) {
        // Try to use cached low-zoom PMTiles for a proper vector map background
        const lowzoomUrl = lowzoomTileArchiveUrl(this.mapConfig);
        const lowzoom = await loadLowZoomFromCache(lowzoomUrl);
        if (lowzoom && pmtilesProtocol) {
          pmtilesProtocol.add(lowzoom);
          style = buildLocalVectorStyleFromArchiveUrl(lowzoomUrl, this.mapThemeSelected, undefined, {
            hillshade: false,
          });
          styleMode = 'offline-lowzoom-vector';
          console.log('Offline: using cached low-zoom vector tiles as base map');
        } else {
          style = buildFallbackRasterStyle();
          styleMode = 'offline-raster-fallback';
          console.log('Offline: no cached low-zoom tiles, using OSM raster fallback');
        }
      } else {
        const resolved = resolveConfiguredMapStyle({
          config: this.mapConfig,
          theme: this.mapThemeSelected,
          localTilesReady:
            this.mapConfig.tileMode === MapConfigDtoTileModeEnum.Local ? this.mapServerStatus?.ready === true : true,
        });
        style = resolved.style;
        styleMode = resolved.styleMode;
      }
      startupLog('mapinit', 'Selected base-map style', {
        styleMode,
        tileMode: this.mapConfig.tileMode,
        offline: this.mapConfig.offline ?? false,
        mapServerReady: this.mapServerStatus?.ready ?? null,
        tileSource: this.mapServerStatus?.tileSource ?? this.mapConfig.tileSource ?? null,
        archiveId: this.mapServerStatus?.archive_id ?? this.mapConfig.archiveId ?? null,
      });
      this.baseMapStyleMode = styleMode;
      this.baseMapRuntimeFallbackApplied = false;

      // ── Base map: tiles, Swiss Mobility overlays, dim layer — passive ──
      startupLog('mapload', 'Creating base map instance', { styleMode });
      this.map = markRaw(
        new maplibregl.Map({
          container: this.$refs.mapBaseContainer,
          style,
          center: initialCenter,
          zoom: initialZoom,
          minZoom: MERCATOR_MIN_ZOOM,
          attributionControl: false,
          interactive: false, // base map just renders — overlay drives interaction
        })
      );

      this.map.on('styleimagemissing', (e) => {
        if (!this.map.hasImage(e.id)) {
          this.map.addImage(e.id, { width: 1, height: 1, data: new Uint8ClampedArray(4) });
        }
      });
      this.map.once('load', () => {
        startupLog('mapload', 'Base map load event received', { styleMode });
      });

      // Detect authentication failures from PMTiles / tile fetches and redirect to login.
      // PMTiles 401 errors bypass the axios interceptor, so this is the only recovery path.
      // All other map errors (blocked CDNs, CORS, DNS failures) are also logged for diagnostics.
      this.map.on('error', (e) => {
        const msg = e.error?.message || '';
        const tileId = e?.tile?.tileID ?? null;
        if (msg.includes('401')) {
          startupWarn('mapload', 'Map tile auth failure; redirecting to login', { message: msg });
          console.warn('Map tile auth failure (401) — redirecting to login');
          redirectToLoginAfterAuthFailure(!!getToken());
        } else if (this.applyRuntimeRasterBasemapFallback(e, msg, tileId)) {
          return;
        } else {
          startupWarn('mapload', 'MapLibre reported a base-map error', {
            message: msg,
            sourceId: e.sourceId ?? null,
            tile: tileId,
          });
          console.warn(
            '[MTL] MapLibre error:',
            msg,
            '| source:',
            e.sourceId ?? '(unknown)',
            '| tile:',
            tileId ?? '',
            e.error
          );
        }
      });

      // ── Overlay map: tracks, highlights, media — handles ALL user interaction ──
      startupLog('mapload', 'Creating overlay map instance');
      this.overlayMap = markRaw(
        new maplibregl.Map({
          container: this.$refs.mapOverlayContainer,
          style: { version: 8, sources: {}, layers: [] },
          center: initialCenter,
          zoom: initialZoom,
          minZoom: MERCATOR_MIN_ZOOM,
          attributionControl: false,
        })
      );
      this.overlayMap.once('load', () => {
        startupLog('mapload', 'Overlay map load event received');
      });

      // Controls live on overlay map (HTML elements — always clickable on top)
      this.overlayMap.addControl(new maplibregl.NavigationControl(), 'top-left');
      this._globeControl = markRaw(new GlobeControl(() => this.toggleGlobeMode()));
      this.overlayMap.addControl(this._globeControl, 'top-left');
      this.overlayMap.addControl(new maplibregl.ScaleControl({ maxWidth: 100 }), 'bottom-left');
      this.overlayMap.addControl(new maplibregl.AttributionControl({ compact: true }), 'bottom-right');

      // Initialize media overlay on the colourful overlay map
      this.mediaOverlay = markRaw(
        new MediaOverlay(
          this.overlayMap,
          (mediaId) => {
            this.mediaSheetMediaId = mediaId;
            this.mediaSheetVisible = true;
            this._buildMediaNavList(mediaId);
          },
          (points) => {
            this.mediaLoadedPoints = points;
          }
        )
      );

      // Initialize heatmap overlay
      this.heatmapOverlay = markRaw(new HeatmapOverlay(this.overlayMap));

      // Resize maps when container size changes (e.g. nav panel expand/collapse)
      if (this._resizeObserver) {
        this._resizeObserver.disconnect();
        this._resizeObserver = null;
      }
      this._resizeObserver = markRaw(
        new ResizeObserver(() => {
          this.map?.resize();
          this.overlayMap?.resize();
          // Recompute globe minZoom whenever the viewport size changes (phone vs desktop)
          if (this.globeMode) {
            const minZoom = computeGlobeMinZoom(this.$refs.mapOverlayContainer);
            this.map?.setMinZoom(minZoom);
            this.overlayMap?.setMinZoom(minZoom);
          }
        })
      );
      this._resizeObserver.observe(this.$refs.mapOverlayContainer);

      // Wait for both maps to be ready
      const waitForMapLoad = (mapInstance, label) =>
        new Promise((resolve) => {
          if (mapInstance.loaded()) {
            startupLog('mapload', `${label} already loaded`);
            resolve(true);
            return;
          }
          mapInstance.once('load', () => resolve(true));
        });

      const mapLoadWatchdog = window.setTimeout(() => {
        startupWarn('mapload', 'Map load watchdog exceeded', {
          styleMode,
          baseLoaded: this.map?.loaded() ?? false,
          overlayLoaded: this.overlayMap?.loaded() ?? false,
          tileMode: this.mapConfig?.tileMode,
          offline: this.mapConfig?.offline ?? false,
          mapServerReady: this.mapServerStatus?.ready ?? null,
        });
      }, MAP_LOAD_WATCHDOG_MS);

      try {
        await Promise.all([waitForMapLoad(this.map, 'Base map'), waitForMapLoad(this.overlayMap, 'Overlay map')]);
      } finally {
        clearTimeout(mapLoadWatchdog);
      }
      startupLog('mapload', 'Both map instances finished loading', { styleMode });
      if (initialBounds) {
        const fitOptions = {
          padding: INITIAL_TRACK_BOUNDS_PADDING,
          maxZoom: INITIAL_TRACK_BOUNDS_MAX_ZOOM,
          duration: 0,
        };
        this.overlayMap.fitBounds(initialBounds, fitOptions);
        this.map.fitBounds(initialBounds, fitOptions);
        startupLog('mapinit', 'Fitted initial viewport to stored track bounds', {
          minLng: initialBounds[0][0],
          minLat: initialBounds[0][1],
          maxLng: initialBounds[1][0],
          maxLat: initialBounds[1][1],
        });
      }
      initTimer.success('Map initialization completed', { styleMode });

      // ── View sync: overlay map drives the base map ──
      const syncBase = () => {
        if (this._syncingView) return;
        this._syncingView = true;
        this.map.jumpTo({
          center: this.overlayMap.getCenter(),
          zoom: this.overlayMap.getZoom(),
          bearing: this.overlayMap.getBearing(),
          pitch: this.overlayMap.getPitch(),
        });
        this._syncingView = false;
      };
      this.overlayMap.on('move', syncBase);

      // Break GPS follow mode when the user manually pans the map
      this.overlayMap.on('dragstart', () => {
        if (this.gpsFollowing) this.gpsFollowing = false;
      });

      // Apply any active Swiss Mobility overlays on the overlay map
      this.applyActiveOverlays();

      // Apply initial globe projection based on current zoom
      this.zoom = this.overlayMap.getZoom();
      this.updateGlobeState();

      // Click handler for track selection (on overlay map — it receives all interaction)
      this.overlayMap.on('click', (e) => {
        if (this.measureToolActive || this.plannerToolActive || this.geoDrawingParamDef) return;

        // Dismiss any open popups on every map click
        this.closeSelectionPopup();
        this.closeSwissMobilityPopup();

        // Skip if user clicked on an individual track point (handled by its own listener)
        const pointFeatures = this.overlayMap.queryRenderedFeatures(e.point, { layers: ['track-points-layer'] });
        if (pointFeatures && pointFeatures.length > 0) return;

        // Skip if user clicked on a media cluster or single media point (handled by MediaOverlay)
        const mediaLayers = ['media-clusters', 'media-unclustered'].filter((layerId) =>
          this.overlayMap.getLayer(layerId)
        );
        if (mediaLayers.length > 0) {
          const mediaFeatures = this.overlayMap.queryRenderedFeatures(e.point, { layers: mediaLayers });
          if (mediaFeatures && mediaFeatures.length > 0) return;
        }

        const lngLat = e.lngLat;

        // Identify Swiss Mobility routes at the clicked point (fire-and-forget, shown if overlay active)
        if (this.activeOverlays.length > 0) {
          this.identifySwissMobilityRoutes(lngLat, e.point);
        }

        const isTouchDevice = 'ontouchstart' in window;
        const tapRadiusPx = isTouchDevice ? 24 : 16;
        const point2 = this.overlayMap.unproject([e.point.x + tapRadiusPx, e.point.y]);
        const distanceInMeters = haversineDistance(lngLat.lat, lngLat.lng, point2.lat, point2.lng);

        if (this.proximityAbortController) this.proximityAbortController.abort();
        this.proximityAbortController = markRaw(new AbortController());
        const signal = this.proximityAbortController.signal;

        fetchTrackIdsWithinDistanceOfPoint(lngLat.lng, lngLat.lat, distanceInMeters, signal)
          .then((gpsTrackIds) => {
            if (!gpsTrackIds || gpsTrackIds.length === 0) {
              this.deselectTrack();
              return;
            }
            if (gpsTrackIds.length === 1) {
              this.selectTrackById(gpsTrackIds[0]);
              this.openTrackDetails(gpsTrackIds[0], TRACK_DETAILS_MAP_DETENT);
            } else {
              this.showTrackSelectionPopup(e.point, gpsTrackIds);
            }
          })
          .catch((err) => {
            if (err.name === 'AbortError' || axios.isCancel(err)) return;
            console.error('Track proximity query failed:', err);
          });
      });

      this.overlayMap.on('zoomend', () => {
        this.zoom = this.overlayMap.getZoom();
        this.updateGlobeState();
        this.updateTrackLineWidth();
        this.scheduleDetailCheck();
        console.log(`[zoom] ${this.zoom.toFixed(3)} | ${this.globeMode ? 'globe' : 'mercator'}`);
      });

      this.overlayMap.on('moveend', () => {
        this.scheduleDetailCheck();
      });

      // Background: cache the low-zoom PMTiles for offline use (only once ready, skip when already offline)
      if (!this.mapConfig.offline && this.mapConfig.tileMode === MapConfigDtoTileModeEnum.Local) {
        if (this.mapServerStatus?.ready) {
          ensureLowZoomCached(lowzoomTileArchiveUrl(this.mapConfig)).catch((e) => {
            startupWarn('mapcache', 'Low-zoom cache warmup failed', describeError(e));
            console.warn('Low-zoom cache failed:', e);
          });
          if (this.mapServerStatus?.tileSource === MapConfigDtoTileSourceEnum.Public) {
            startupLog('mapstatus', 'Hosted map service active; polling for local sidecar availability');
            this.startMapStatusPolling();
          }
        } else {
          startupLog('mapstatus', 'Starting map-status polling until local tiles are ready');
          // Planet file not yet ready — poll and auto-switch to vector when complete
          this.startMapStatusPolling();
        }
      }
    },

    /** Update globe projection state based on current zoom level (hysteresis). */
    updateGlobeState() {
      if (this.globeMode && this.zoom > GLOBE_EXIT_ZOOM) {
        // Zoomed in past the exit threshold — leave globe and reset user-override
        this.globeMode = false;
        this.globeUserDisabled = false;
        this.applyGlobeProjection();
      } else if (!this.globeMode && !this.globeUserDisabled && this.zoom < GLOBE_ENTER_ZOOM) {
        // Zoomed out past the enter threshold — auto-activate globe
        this.globeMode = true;
        this.applyGlobeProjection();
      }
      // Show the toggle button across the whole hysteresis band
      const inGlobeZone = this.zoom < GLOBE_EXIT_ZOOM;
      this._globeControl?.setVisible(inGlobeZone);
      this._globeControl?.setActive(this.globeMode);
    },

    /** Set MapLibre projection on both maps and let MapLibre handle the morph. */
    applyGlobeProjection() {
      const proj = this.globeMode ? { type: 'globe' } : { type: 'mercator' };

      // Clamp minZoom: dynamically fitted to viewport for globe, fixed floor for mercator
      const minZoom = this.globeMode ? computeGlobeMinZoom(this.$refs.mapOverlayContainer) : MERCATOR_MIN_ZOOM;
      this.map?.setMinZoom(minZoom);
      this.overlayMap?.setMinZoom(minZoom);

      // Apply in the same frame so both maps morph together without exposing the black background.
      this.map?.setProjection(proj);
      this.overlayMap?.setProjection(proj);
    },

    /** Toggle globe mode on/off (user action via the control button). */
    toggleGlobeMode() {
      if (this.globeMode) {
        this.globeUserDisabled = true;
        this.globeMode = false;
      } else {
        this.globeUserDisabled = false;
        this.globeMode = true;
      }
      this.applyGlobeProjection();
      this._globeControl?.setActive(this.globeMode);
    },

    /** Update the line width of highlight and dot layers based on current zoom. */
    updateTrackLineWidth() {
      if (!this.overlayMap) return;
      const zoom = this.overlayMap.getZoom();
      let lineWeight = 4;
      let correction = 14 - zoom * 1.3;
      if (correction < 0) correction = 0;
      lineWeight = Math.round(lineWeight + correction);

      // tracks-layer uses a GPU-native interpolate expression — no JS update needed.
      if (this.overlayMap.getLayer('tracks-highlight-layer')) {
        this.overlayMap.setPaintProperty('tracks-highlight-layer', 'line-width', lineWeight * 1.5);
      }
      if (this.overlayMap.getLayer('tracks-highlight-dash-layer')) {
        this.overlayMap.setPaintProperty('tracks-highlight-dash-layer', 'line-width', lineWeight * 1.5);
      }
      if (this.overlayMap.getLayer('tracks-dot-layer')) {
        this.overlayMap.setPaintProperty('tracks-dot-layer', 'circle-radius', Math.round(lineWeight / 2));
      }
      if (this.overlayMap.getLayer('tracks-highlight-circle-layer')) {
        this.overlayMap.setPaintProperty(
          'tracks-highlight-circle-layer',
          'circle-radius',
          Math.round((lineWeight / 2) * 1.5)
        );
      }
    },

    onLocationUpdate(geolocationPosition) {
      const latitude = geolocationPosition.coords.latitude;
      const longitude = geolocationPosition.coords.longitude;
      this.mapCenter = [longitude, latitude];
      this.gpsLocation = [latitude, longitude];

      // Only auto-centre when in follow mode — user panning breaks this (see dragstart listener)
      if (this.gpsFollowing) {
        this.overlayMap.flyTo({ center: [longitude, latitude], zoom: this.overlayMap.getZoom() });
      }

      // Always update (or create) the GPS marker regardless of follow state
      if (this.gpsMarker) {
        this.gpsMarker.setLngLat([longitude, latitude]);
      } else {
        const el = document.createElement('div');
        el.style.cssText =
          'width:16px;height:16px;border-radius:50%;background:red;border:2px solid white;box-shadow:0 0 4px rgba(0,0,0,0.4);';
        this.gpsMarker = markRaw(
          new maplibregl.Marker({ element: el }).setLngLat([longitude, latitude]).addTo(this.overlayMap)
        );
      }
    },

    onGPSDeviceEnabledDisabled(deviceEnabled) {
      this.gpsDeviceEnabledDisabled = deviceEnabled;
      if (deviceEnabled) {
        this.gpsFollowing = true; // always start in follow mode when GPS is turned on
      } else {
        this.gpsFollowing = false;
        if (this.gpsMarker) {
          this.gpsMarker.remove();
          this.gpsMarker = null;
        }
      }
    },

    onAnimationStartEvent() {
      // Timeline panel now self-manages its UI
    },

    onAnimationFinishedEvent() {
      console.log('map: onAnimationFinishedEvent');
    },

    onAnimationStopEvent() {
      // Timeline panel now self-manages its UI
    },

    onAnimateEvent(_event) {
      // available if needed for external integrations
    },

    async onMapThemeChangeEvent(themeCode) {
      if (themeCode && typeof themeCode === 'string') {
        this.mapThemeSelected = themeCode;
        mapPreferences.writeTheme(themeCode);
      }
      this.showLoader = true;
      await this.initMap();
      await this.addTracksToMap();
      this.showLoader = false;
    },

    async loadMapData(fetchResult) {
      // Wait for map to be ready (needed when track fetch runs in parallel with initMap)
      if (this._mapReadyPromise) await this._mapReadyPromise;
      this.geojson = markRaw(fetchResult.geojson);
      this.gpsTracksById = markRaw(fetchResult.gpsTracksById);
      this.gpsTrackIdToFeature = markRaw(fetchResult.gpsTrackIdToFeature);
      if (fetchResult.trackPrecisions) {
        this.trackPrecisions = markRaw(fetchResult.trackPrecisions);
      }
      this.activeTrackFilterResult = fetchResult.filterResult ?? this.activeTrackFilterResult;
      await this.addTracksToMap();
    },

    publishGpsTrackMetadataChanges() {
      this.gpsTracksById = markRaw(new Map(this.gpsTracksById));
    },

    async mergeTrackResult(fetchResult, { pruneMissing = false } = {}) {
      if (!fetchResult?.geojson?.features?.length) {
        if (pruneMissing && this.geojson) {
          this.geojson.features = [];
          this.gpsTrackIdToFeature.clear();
          this.gpsTracksById.clear();
          this.publishGpsTrackMetadataChanges();
          this.trackPrecisions.clear();
          this.totalTrackCount = fetchResult?.standardFilterCount ?? 0;
          this.visibleTrackCount = 0;
          this.activeTrackFilterResult = fetchResult?.filterResult ?? this.activeTrackFilterResult;
          await this.updateTrackStyle();
          this.updateTracksSource();
        }
        return;
      }
      if (!this.cachedTracksLoaded || !this.geojson) {
        await this.loadMapData(fetchResult);
        return;
      }

      this.activeTrackFilterResult = fetchResult.filterResult ?? this.activeTrackFilterResult;
      const incomingIds = new Set();
      let changed = false;
      let trackDataChanged = false;

      for (const [trackId, feature] of fetchResult.gpsTrackIdToFeature) {
        const numId = Number(trackId);
        incomingIds.add(numId);
        const existingFeature = this.gpsTrackIdToFeature.get(numId);
        const incomingPrecision = fetchResult.trackPrecisions?.get(numId) ?? OVERVIEW_PRECISION;
        const currentPrecision = this.trackPrecisions.get(numId) ?? OVERVIEW_PRECISION;

        if (existingFeature) {
          existingFeature.properties = feature.properties;
          if (isSameOrBetterPrecision(incomingPrecision, currentPrecision)) {
            existingFeature.geometry = feature.geometry;
            this.trackPrecisions.set(numId, incomingPrecision);
          }
          this.gpsTrackIdToFeature.set(numId, existingFeature);
          changed = true;
        } else {
          this.geojson.features.push(feature);
          this.gpsTrackIdToFeature.set(numId, feature);
          this.trackPrecisions.set(numId, incomingPrecision);
          changed = true;
        }

        const gpsTrack = fetchResult.gpsTracksById.get(trackId) ?? fetchResult.gpsTracksById.get(numId);
        if (gpsTrack) {
          this.gpsTracksById.set(numId, gpsTrack);
          trackDataChanged = true;
        }
      }

      if (pruneMissing) {
        const beforeLength = this.geojson.features.length;
        this.geojson.features = this.geojson.features.filter((feature) => {
          const trackId = Number(feature.properties?.id);
          return incomingIds.has(trackId);
        });
        if (this.geojson.features.length !== beforeLength) changed = true;

        for (const trackId of [...this.gpsTrackIdToFeature.keys()]) {
          if (!incomingIds.has(Number(trackId))) {
            this.gpsTrackIdToFeature.delete(trackId);
            this.gpsTracksById.delete(trackId);
            trackDataChanged = true;
            this.trackPrecisions.delete(trackId);
          }
        }
      }

      this.totalTrackCount = fetchResult.standardFilterCount;
      this.visibleTrackCount = this.geojson.features.length;
      if (changed) {
        await this.updateTrackStyle();
        this.updateTracksSource();
      }
      if (trackDataChanged) {
        this.publishGpsTrackMetadataChanges();
      }
    },

    async mergeTrackPage(fetchResult) {
      if (!fetchResult?.geojson?.features?.length) return;

      if (!this.cachedTracksLoaded) {
        this.totalTrackCount = fetchResult.standardFilterCount;
        await this.loadMapData(fetchResult);
        this.cachedTracksLoaded = true;
        this.showLoader = false;
        this.$emit('tracks-loaded');
        startupLog('tracks', 'tracks-loaded emitted from first track batch', {
          featureCount: fetchResult.geojson.features.length,
        });
        return;
      }

      if (!this.geojson) {
        await this.loadMapData(fetchResult);
        return;
      }

      let changed = false;
      let trackDataChanged = false;
      for (const [trackId, feature] of fetchResult.gpsTrackIdToFeature) {
        const numId = Number(trackId);
        const existingFeature = this.gpsTrackIdToFeature.get(numId);
        const precision = fetchResult.trackPrecisions?.get(numId) ?? OVERVIEW_PRECISION;
        const currentPrecision = this.trackPrecisions.get(numId) ?? OVERVIEW_PRECISION;
        if (existingFeature) {
          existingFeature.properties = feature.properties;
          if (isSameOrBetterPrecision(precision, currentPrecision)) {
            existingFeature.geometry = feature.geometry;
            this.trackPrecisions.set(numId, precision);
          }
          this.gpsTrackIdToFeature.set(numId, existingFeature);
          changed = true;
        } else {
          this.geojson.features.push(feature);
          this.gpsTrackIdToFeature.set(numId, feature);
          this.trackPrecisions.set(numId, precision);
          changed = true;
        }

        const gpsTrack = fetchResult.gpsTracksById.get(trackId) ?? fetchResult.gpsTracksById.get(numId);
        if (gpsTrack) {
          this.gpsTracksById.set(numId, gpsTrack);
          trackDataChanged = true;
        }
      }

      if (trackDataChanged) {
        this.publishGpsTrackMetadataChanges();
      }
      if (changed) {
        this.totalTrackCount = fetchResult.standardFilterCount;
        this.visibleTrackCount = this.geojson.features.length;
        this.activeTrackFilterResult = fetchResult.filterResult ?? this.activeTrackFilterResult;
        await this.updateTrackStyle();
        this.updateTracksSource();
      }
    },

    reloadBrowserForFreshness(done) {
      this.freshnessReloading = true;
      this.showLoader = true;
      done?.(true);
      window.location.reload();
    },

    onMapFreshnessBrowserReload() {
      this.reloadBrowserForFreshness();
    },

    /** Fit the overlay map viewport to the bounds of the given GeoJSON FeatureCollection. */
    fitToTrackBounds(geojson) {
      if (!this.overlayMap || !geojson?.features?.length) return;
      const bounds = new maplibregl.LngLatBounds();
      for (const f of geojson.features) {
        if (!f.geometry) continue;
        if (f.geometry.type === 'Point') {
          bounds.extend(f.geometry.coordinates);
        } else if (f.geometry.type === 'LineString') {
          for (const coord of f.geometry.coordinates) {
            bounds.extend(coord);
          }
        }
      }
      if (!bounds.isEmpty()) {
        this.overlayMap.fitBounds(bounds, { padding: 40, maxZoom: 14 });
      }
    },

    async onAdminReloadTracks(done) {
      this.cachedTracksLoaded = false;
      try {
        await this.fetchTracksAndFallback();
        done?.(true);
      } catch (error) {
        console.error('Admin track reload failed:', error);
        done?.(false, error instanceof Error ? error.message : String(error));
      }
    },

    async onAdminRefreshFreshnessData(done) {
      this.reloadBrowserForFreshness(done);
    },

    async fetchTracksAndFallback() {
      const timer = startStartupTimer('tracks', 'Resolving startup tracks and fallbacks');
      this.cachedTracksLoaded = false;
      this.initialLoadDone = false;

      const authStatus = await checkServerAuth();
      startupLog('tracks', 'Auth status checked before track load', { authStatus });
      if (authStatus === 'auth-error') {
        timer.warn('Auth check failed; redirecting to login');
        this.showLoader = false;
        redirectToLoginAfterAuthFailure(!!getToken());
        return;
      }

      await this.clearTrackCacheWhenServerFreshnessChanged();

      // ── Phase 2: Cache-first — show cached tracks instantly, then sync ──
      const cachePopulated = await isTrackCachePopulated();
      if (cachePopulated) {
        startupLog('tracks', 'Cache populated — loading cached tracks immediately');
        const cached = await loadCachedTrackCollection();
        if (cached && cached.geojson?.features?.length > 0) {
          await this.loadMapData(cached);
          this.cachedTracksLoaded = true;
          this.showLoader = false;
          this.$emit('tracks-loaded');
          startupLog('tracks', 'tracks-loaded emitted from instant cache path', {
            featureCount: cached.geojson.features.length,
          });
          timer.success('Instant cache load completed', { featureCount: cached.geojson.features.length });

          // Background sync: fetch fresh data from server and update in-place
          this.$emit('syncing', true);
          this._backgroundSync(timer);
          return;
        }
      }

      // ── No cache — original flow with reduced fallback timeout ──
      const CACHE_FALLBACK_TIMEOUT_MS = 3000;
      this.loadingTrackBatches = false;

      const fallbackTimer = setTimeout(async () => {
        if (!this.cachedTracksLoaded) {
          startupWarn('tracks', 'Server tracks still pending; attempting IndexedDB fallback', {
            fallbackAfterMs: CACHE_FALLBACK_TIMEOUT_MS,
          });
          const cached = await loadCachedTrackCollection();
          if (cached && !this.cachedTracksLoaded) {
            startupLog('tracks', 'Using cached tracks after startup fallback timeout', {
              featureCount: cached.geojson?.features?.length ?? 0,
            });
            await this.loadMapData(cached);
            this.isOffline = true;
            this.cachedTracksLoaded = true;
            this.showLoader = false;
            this.$emit('tracks-loaded');
            startupLog('tracks', 'tracks-loaded emitted from cached fallback');
            this.fitToTrackBounds(cached.geojson);
          }
        }
      }, CACHE_FALLBACK_TIMEOUT_MS);

      try {
        const collectionPrecision = this.currentCollectionPrecision();
        let receivedServerBatch = false;
        const onOverviewPage = async (page) => {
          receivedServerBatch = true;
          await this.mergeTrackPage(page);
        };
        startupLog('tracks', 'Fetching startup tracks in batches', { precision: collectionPrecision });
        this.loadingTrackBatches = true;
        const serverData = await loadTrackCollectionPaged(collectionPrecision, {
          onPage: onOverviewPage,
          pageSize: TRACK_LOAD_BATCH_SIZE,
        });
        this.loadingTrackBatches = false;
        clearTimeout(fallbackTimer);
        this.totalTrackCount = serverData.standardFilterCount;
        startupLog('tracks', 'Server tracks ready for startup render', {
          precision: collectionPrecision,
          featureCount: serverData.geojson?.features?.length ?? 0,
          standardFilterCount: serverData.standardFilterCount,
        });

        if (this.cachedTracksLoaded && !receivedServerBatch) {
          await this.mergeTrackResult(serverData, { pruneMissing: true });
          timer.success('Server tracks arrived after cached fallback was already shown');
          this.isOffline = false;
          this.$toast.add({
            severity: 'success',
            summary: 'Online',
            detail: 'Back online — tracks reloaded.',
            life: 3000,
          });
          this.maybeLoadBackgroundTracks(serverData.filterResult);
        } else {
          const wasAlreadyLoaded = this.cachedTracksLoaded;
          this.cachedTracksLoaded = true;
          if (wasAlreadyLoaded) {
            await this.mergeTrackResult(serverData, { pruneMissing: true });
          } else {
            await this.loadMapData(serverData);
          }
          this.isOffline = false;
          this.showLoader = false;
          if (!wasAlreadyLoaded) {
            this.$emit('tracks-loaded');
            startupLog('tracks', 'tracks-loaded emitted from server startup fetch');
          }
          timer.success('Startup tracks loaded from server');
          this.maybeLoadBackgroundTracks(serverData.filterResult);
        }
        this.scheduleDetailCheck();
        await this.captureAppliedFreshnessToken();
        this.initialLoadDone = true;
      } catch (e) {
        this.loadingTrackBatches = false;
        clearTimeout(fallbackTimer);
        timer.error('Startup track resolution failed', describeError(e));
        if (isAuthError(e)) {
          this.showLoader = false;
          redirectToLoginAfterAuthFailure(!!getToken());
          return;
        }
        if (!this.cachedTracksLoaded) {
          const cached = await loadCachedTrackCollection();
          if (cached) {
            startupLog('tracks', 'Recovered from startup failure using cached tracks', {
              featureCount: cached.geojson?.features?.length ?? 0,
            });
            await this.loadMapData(cached);
            this.isOffline = true;
            this.cachedTracksLoaded = true;
            this.showLoader = false;
            this.$emit('tracks-loaded');
            startupLog('tracks', 'tracks-loaded emitted from cached recovery');
            this.fitToTrackBounds(cached.geojson);
          } else {
            this.showLoader = false;
            startupWarn('tracks', 'No cached tracks available; emitting load-failed');
            this.$emit('load-failed');
          }
        } else {
          this.isOffline = true;
        }
        this.scheduleRetry();
        this.initialLoadDone = true;
      }
    },

    /**
     * Background sync after showing cached tracks.
     * Fetches fresh data from server, updates the map seamlessly, then loads 10m.
     */
    async _backgroundSync(timer) {
      try {
        const collectionPrecision = this.currentCollectionPrecision();
        startupLog('sync', 'Background sync: fetching server data', { precision: collectionPrecision });
        const serverData = await loadTrackCollectionPaged(collectionPrecision, {
          onPage: (page) => this.mergeTrackPage(page),
          pageSize: TRACK_LOAD_BATCH_SIZE,
        });
        this.totalTrackCount = serverData.standardFilterCount;

        await this.mergeTrackResult(serverData, { pruneMissing: true });
        this.isOffline = false;
        startupLog('sync', 'Background sync: map updated with server data', {
          precision: collectionPrecision,
          featureCount: serverData.geojson?.features?.length ?? 0,
        });
        timer.success('Background sync completed');

        this.$emit('syncing', false);
        this.maybeLoadBackgroundTracks(serverData.filterResult);
        this.scheduleDetailCheck();
        await this.captureAppliedFreshnessToken();
        this.initialLoadDone = true;
      } catch (e) {
        startupWarn('sync', 'Background sync failed — using cached data', describeError(e));
        if (isAuthError(e)) {
          redirectToLoginAfterAuthFailure(!!getToken());
          return;
        }
        this.isOffline = true;
        this.$emit('syncing', false);
        this.scheduleRetry();
        this.initialLoadDone = true;
      }
    },

    async loadAllTracksAt10m(filterResult) {
      if (this.bulk10mController) this.bulk10mController.abort();
      this.bulk10mController = markRaw(new AbortController());
      const signal = this.bulk10mController.signal;
      this.loadingTracks10m = true;

      try {
        console.log('Background: loading all tracks at 10m…');
        const applyPage = async (pageData) => {
          if (signal.aborted) return;
          let changed = false;
          let trackDataChanged = false;

          for (const [trackId, feature] of pageData.gpsTrackIdToFeature) {
            const numId = Number(trackId);
            const gpsTrack = pageData.gpsTracksById.get(trackId) ?? pageData.gpsTracksById.get(numId);
            if (gpsTrack) {
              this.gpsTracksById.set(numId, gpsTrack);
              trackDataChanged = true;
            }

            const currentPrecision = this.trackPrecisions.get(numId) ?? OVERVIEW_PRECISION;
            if (currentPrecision <= BACKGROUND_TRACK_PRECISION) continue;

            const existingFeature = this.gpsTrackIdToFeature.get(numId);
            if (existingFeature?.geometry && feature?.geometry) {
              existingFeature.geometry.coordinates = feature.geometry.coordinates;
              existingFeature.geometry.type = feature.geometry.type;
              changed = true;
            }
            this.trackPrecisions.set(numId, BACKGROUND_TRACK_PRECISION);
          }

          if (trackDataChanged) {
            this.publishGpsTrackMetadataChanges();
          }
          if (changed) {
            this.updateTracksSource();
          }
        };

        const data10m = await loadTrackCollectionPaged(BACKGROUND_TRACK_PRECISION, {
          signal,
          filterResult,
          onPage: applyPage,
          pageSize: TRACK_LOAD_BATCH_SIZE,
        });
        if (signal.aborted) return;

        let trackDataChanged = false;
        for (const [trackId, feature] of data10m.gpsTrackIdToFeature) {
          if (signal.aborted) return;
          const numId = Number(trackId);
          const gpsTrack = data10m.gpsTracksById.get(trackId) ?? data10m.gpsTracksById.get(numId);
          if (gpsTrack) {
            this.gpsTracksById.set(numId, gpsTrack);
            trackDataChanged = true;
          }

          const currentPrecision = this.trackPrecisions.get(numId) ?? OVERVIEW_PRECISION;
          if (currentPrecision <= BACKGROUND_TRACK_PRECISION) continue;

          // Update the in-memory feature coordinates
          const existingFeature = this.gpsTrackIdToFeature.get(numId);
          if (existingFeature?.geometry) {
            const newCoords = feature.geometry.coordinates;
            existingFeature.geometry.coordinates = newCoords;
            // Sync geometry type: handles both upgrade (Point→LineString for tracks that
            // were degenerate at overview precision but have real coords at 10m) and the
            // reverse (LineString→Point for tracks whose 10m data is still degenerate,
            // which would otherwise leave a LineString with flat [lng,lat] coordinates).
            existingFeature.geometry.type = feature.geometry.type;
          }
          this.trackPrecisions.set(numId, BACKGROUND_TRACK_PRECISION);
        }

        if (trackDataChanged) {
          this.publishGpsTrackMetadataChanges();
        }
        // Update the source data to reflect new coordinates
        this.updateTracksSource();
        console.log('Background 10m load complete');
      } catch (e) {
        if (signal.aborted || isAbortLikeError(e)) {
          this.loadingTracks10m = false;
          return;
        }
        console.warn('Background 10m load failed:', e);
      } finally {
        this.loadingTracks10m = false;
      }
    },

    /** Update the MapLibre 'tracks' source with the current geojson data. */
    updateTracksSource() {
      if (!this.overlayMap || !this.geojson) return;
      const source = this.overlayMap.getSource('tracks');
      if (source) {
        source.setData(this.geojson);
      }
      const overviewSource = this.overlayMap.getSource('tracks-overview');
      if (overviewSource) {
        overviewSource.setData({
          type: 'FeatureCollection',
          features: buildTrackOverviewFeatures(this.geojson),
        });
      }
      // Keep heatmap density in sync as track precision improves
      if (this.heatmapOverlay) {
        this.heatmapOverlay.updateData(this.geojson);
      }
      this.applyGroupFilter();
    },

    onBrowserOnline() {
      if (!this.isOffline) return;
      console.log('Browser online event detected — retrying immediately');
      if (this.retryTimeoutId) clearTimeout(this.retryTimeoutId);
      this.retryCount = 0;
      this.performBackgroundRetry();
    },

    scheduleRetry() {
      const MAX_RETRIES = 10;
      if (this.retryCount >= MAX_RETRIES) return;
      const backoffMs = Math.min(5_000 * Math.pow(1.5, this.retryCount), 60_000);
      if (this.retryTimeoutId) clearTimeout(this.retryTimeoutId);
      this.retryTimeoutId = setTimeout(() => this.performBackgroundRetry(), backoffMs);
    },

    async performBackgroundRetry() {
      this.retryCount++;
      try {
        const collectionPrecision = this.currentCollectionPrecision();
        const serverData = await loadTrackCollectionPaged(collectionPrecision);
        if (this.geojson) {
          await this.mergeTrackResult(serverData, { pruneMissing: true });
        } else {
          await this.loadMapData(serverData);
        }
        this.isOffline = false;
        this.retryCount = 0;
        this.maybeLoadBackgroundTracks(serverData.filterResult);
        this.scheduleDetailCheck();
        await this.captureAppliedFreshnessToken();
        // If the initial map style was the offline raster fallback (e.g. the
        // /api/map/config call timed out on first login), rebuild the style
        // now that connectivity is back — no page reload needed.
        if (this.mapConfig?.offline) {
          try {
            console.log('Recovered from offline fallback — rebuilding map style with real config');
            clearMapConfigCache();
            await this.initMap();
            await this.addTracksToMap();
          } catch (rebuildErr) {
            console.warn('Failed to rebuild map style after recovery:', rebuildErr);
          }
        }
        this.$toast.add({
          severity: 'success',
          summary: 'Online',
          detail: 'Back online — tracks reloaded.',
          life: 3000,
        });
      } catch (e) {
        if (isAuthError(e)) {
          redirectToLoginAfterAuthFailure(!!getToken());
          return;
        }
        this.scheduleRetry();
      }
    },

    onMeasureShowTrackDetails(id) {
      this.openTrackDetails(id, TRACK_DETAILS_EXPANDED_DETENT);
    },

    onMeasureActiveChanged(isActive) {
      this.measureToolActive = isActive;
    },

    onPlannerActiveChanged(isActive) {
      this.plannerToolActive = isActive;
    },

    closeAllToolsExcept(skipRefName) {
      const toolRefs = [
        'infoTool',
        'animateTool',
        'measureTool',
        'plannerTool',
        'statistics',
        'filterTool',
        'mapSettingsTool',
        'gpsLocate',
        'adminTool',
      ];
      for (const name of toolRefs) {
        if (name !== skipRefName && this.$refs[name]?.close) {
          this.$refs[name].close();
        }
      }
    },

    closeTransientOverlaysForToolSwitch() {
      const hadTrackDetails = this.trackDetailsVisible;
      this.trackDetailsVisible = false;
      this.trackDetailsId = null;
      this.trackDetailsInfo = { id: null, name: '', description: '' };
      if (hadTrackDetails) this.deselectTrack();
      this.mediaSheetVisible = false;
      this.closeMediaSheet();
      this.trackSelectionSheetVisible = false;
      this.locationSearchVisible = false;
      this.clearLocationSearchMarker();
      this.closeSelectionPopup();
      this.closeSwissMobilityPopup();
    },

    onToolSelect(toolId) {
      const toolMap = {
        animate: 'animateTool',
        measure: 'measureTool',
        planner: 'plannerTool',
        stats: 'statistics',
        filter: 'filterTool',
        map: 'mapSettingsTool',
        gps: 'gpsLocate',
        admin: 'adminTool',
      };

      const refName = toolMap[toolId];
      if (!refName) return;
      this.closeTransientOverlaysForToolSwitch();

      // ── GPS: 3-state cycle ──────────────────────────────────────────────────
      // OFF  → tap → ON + following (blue dot, map tracks position)
      // ON + following → drag map → drifted (amber dot, marker still visible)
      // ON + drifted  → tap → re-centre + re-engage following (blue dot again)
      // ON + following → tap → OFF (same as: turn off GPS)
      if (toolId === 'gps') {
        if (this.gpsDeviceEnabledDisabled && !this.gpsFollowing) {
          // Re-centre and re-engage following without toggling GPS off
          this.gpsFollowing = true;
          if (this.gpsLocation) {
            this.overlayMap.flyTo({
              center: [this.gpsLocation[1], this.gpsLocation[0]],
              zoom: this.overlayMap.getZoom(),
            });
          }
          this.activeToolId = null;
          return;
        }
        // Otherwise: toggle GPS on/off (GpsLocate handles the watchPosition lifecycle)
        this.closeAllToolsExcept(refName);
        this.$refs[refName]?.toggle();
        this.activeToolId = null;
        return;
      }
      // ── All other tools ─────────────────────────────────────────────────────

      const isTogglingOff = this.activeToolId === toolId;

      this.closeAllToolsExcept(refName);
      const ref = this.$refs[refName];
      if (ref?.toggle) {
        ref.toggle();
      }
      if (toolId === 'gps') {
        this.activeToolId = null;
      } else {
        this.activeToolId = isTogglingOff ? null : toolId;
      }
    },

    onToolOpened(refName) {
      this.closeTransientOverlaysForToolSwitch();
      this.closeAllToolsExcept(refName);
      const idMap = {
        animateTool: 'animate',
        measureTool: 'measure',
        plannerTool: 'planner',
        statistics: 'stats',
        filterTool: 'filter',
        mapSettingsTool: 'map',
        gpsLocate: 'gps',
        adminTool: 'admin',
      };
      this.activeToolId = idMap[refName] || null;
      // Show existing geo shapes when the filter panel opens
      if (refName === 'filterTool') {
        this.$nextTick(() => {
          if (!this.geoDrawingOverlay && this.overlayMap) {
            this.geoDrawingOverlay = markRaw(new GeoDrawingOverlay(this.overlayMap));
          }
          this.renderExistingGeoShapes();
        });
      }
    },

    onToolClosed() {
      this.activeToolId = null;
      // Clear geo shape overlays when filter sheet is closed (unless actively drawing)
      if (!this.geoDrawingParamDef && this.geoDrawingOverlay) {
        this.geoDrawingOverlay.clearAll();
      }
    },

    // ─── Progressive detail loading ──────────────────────────────────

    scheduleDetailCheck() {
      if (this.detailDebounceTimer) clearTimeout(this.detailDebounceTimer);
      this.detailDebounceTimer = setTimeout(() => this.checkViewportPrecision(), DETAIL_DEBOUNCE_MS);
    },

    checkViewportPrecision() {
      if (!this.overlayMap || !this.geojson || this.isOffline) return;

      const zoom = this.overlayMap.getZoom();

      // Update track points layer only when zoom is high enough to show them
      if (this.trackPointsVisible && zoom >= TRACK_POINTS_MIN_ZOOM) {
        this.updateTrackPointsSource();
      }

      const targetPrecision = precisionForZoom(zoom);

      if (targetPrecision === BACKGROUND_TRACK_PRECISION) {
        this.maybeLoadBackgroundTracks();
        return;
      }

      // Only the 1m tier uses the per-track detail queue. The cacheable
      // precisions (1000m overview, 10m background) are loaded exclusively
      // through the batched `tracks/get-simplified` endpoint via
      // fetchTracksAndFallback / loadAllTracksAt10m, so per-track upgrades
      // here would create a flood of `tracks/get/{id}` requests that scales
      // linearly with the dataset size.
      if (targetPrecision !== DETAIL_TRACK_PRECISION) return;

      const needsViewportFilter = targetPrecision === DETAIL_TRACK_PRECISION;
      let bounds;
      if (needsViewportFilter) {
        const mapBounds = this.overlayMap.getBounds();
        const sw = mapBounds.getSouthWest();
        const ne = mapBounds.getNorthEast();
        const latPad = (ne.lat - sw.lat) * DETAIL_BOUNDS_PADDING;
        const lngPad = (ne.lng - sw.lng) * DETAIL_BOUNDS_PADDING;
        bounds = {
          minLat: sw.lat - latPad,
          minLng: sw.lng - lngPad,
          maxLat: ne.lat + latPad,
          maxLng: ne.lng + lngPad,
        };
      }

      const tracksToAdjust = [];
      for (const [trackId] of this.gpsTrackIdToFeature) {
        const numId = Number(trackId);
        const currentPrecision = this.trackPrecisions.get(numId) ?? OVERVIEW_PRECISION;
        if (currentPrecision === targetPrecision) continue;

        if (needsViewportFilter) {
          const track = this.gpsTracksById.get(numId);
          if (track?.bboxMinLat != null) {
            if (
              track.bboxMaxLat < bounds.minLat ||
              track.bboxMinLat > bounds.maxLat ||
              track.bboxMaxLng < bounds.minLng ||
              track.bboxMinLng > bounds.maxLng
            )
              continue;
          }
        }
        tracksToAdjust.push(numId);
      }

      if (tracksToAdjust.length === 0) return;

      if (this.detailAbortController) this.detailAbortController.abort();
      this.detailAbortController = markRaw(new AbortController());

      const center = this.overlayMap.getCenter();
      tracksToAdjust.sort((a, b) => {
        const aT = this.gpsTracksById.get(a);
        const bT = this.gpsTracksById.get(b);
        const aD =
          aT?.centerLat != null ? haversineDistance(center.lat, center.lng, aT.centerLat, aT.centerLng) : Infinity;
        const bD =
          bT?.centerLat != null ? haversineDistance(center.lat, center.lng, bT.centerLat, bT.centerLng) : Infinity;
        return aD - bD;
      });

      console.log(`Adjusting ${tracksToAdjust.length} tracks to ${targetPrecision}m (zoom ${zoom})`);
      this.processDetailQueue(tracksToAdjust, targetPrecision, this.detailAbortController.signal);
    },

    async processDetailQueue(trackIds, targetPrecision, signal) {
      const queue = [...trackIds];
      let changed = false;
      let trackDataChanged = false;

      // At 1m precision each upgrade meaningfully changes what track-points
      // should be rendered on the map. Rather than waiting for the whole
      // queue to drain, flush the map sources progressively so the user
      // sees individual GPS points as they come in (otherwise tracks keep
      // showing coarse 10m spacing until every track is done).
      const progressive = targetPrecision === DETAIL_TRACK_PRECISION;
      let pendingFlush = false;
      const flush = () => {
        if (!pendingFlush || signal.aborted) return;
        pendingFlush = false;
        this.updateTracksSource();
        this.updateTrackPointsSource();
      };

      const fetchNext = async () => {
        while (queue.length > 0 && !signal.aborted) {
          const trackId = queue.shift();
          const currentPrecision = this.trackPrecisions.get(trackId) ?? OVERVIEW_PRECISION;
          if (currentPrecision === targetPrecision) continue;

          try {
            const { coordinates, gpsTrack } = await fetchDetailTrackAtPrecision(trackId, targetPrecision, signal);
            if (signal.aborted) return;

            // Update in-memory feature
            const feature = this.gpsTrackIdToFeature.get(trackId);
            if (feature?.geometry) {
              feature.geometry.coordinates = coordinates;
              // Restore geometry type: features may be Point (degenerate/empty at overview
              // precision) but now have real LineString coords at higher precision.
              if (feature.geometry.type === 'Point' && Array.isArray(coordinates) && coordinates.length > 1) {
                feature.geometry.type = 'LineString';
              }
              changed = true;
              pendingFlush = true;
            }
            this.trackPrecisions.set(trackId, targetPrecision);
            this.gpsTracksById.set(trackId, gpsTrack);
            trackDataChanged = true;

            if (progressive) flush();
          } catch (e) {
            if (signal.aborted || isAbortLikeError(e)) return;
            console.warn(`Detail fetch failed for track ${trackId}:`, e);
          }
        }
      };

      const workers = [];
      const concurrency = targetPrecision === DETAIL_TRACK_PRECISION ? DETAIL_MAX_CONCURRENT_1M : DETAIL_MAX_CONCURRENT;
      for (let i = 0; i < Math.min(concurrency, queue.length); i++) {
        workers.push(fetchNext());
      }
      await Promise.allSettled(workers);

      if (trackDataChanged && !signal.aborted) {
        this.publishGpsTrackMetadataChanges();
      }
      // Final flush (covers non-progressive path, and any trailing update
      // missed because progressive flush was skipped due to abort).
      if (changed && !signal.aborted) {
        this.updateTracksSource();
        this.updateTrackPointsSource();
      }
    },

    async onToggleMediaLayer() {
      if (!this.mediaOverlay || this.mediaBusy) return;
      this.mediaBusy = true;
      try {
        if (this.mediaOverlay.isVisible()) {
          this.mediaOverlay.hide();
          this.mediaVisible = false;
        } else {
          await this.mediaOverlay.show();
          this.mediaVisible = true;
        }
      } finally {
        this.mediaBusy = false;
      }
    },

    onToggleHeatmapLayer() {
      if (!this.heatmapOverlay || !this.geojson) return;
      if (this.heatmapOverlay.isVisible()) {
        this.heatmapOverlay.hide();
        this.heatmapVisible = false;
        mapPreferences.writeHeatmapVisible(false);
      } else {
        this.heatmapOverlay.show(this.geojson);
        this.applyLayerOpacity('heatmap');
        this.heatmapVisible = true;
        mapPreferences.writeHeatmapVisible(true);
      }
    },

    async addTracksToMap() {
      if (!this.overlayMap || !this.geojson) return;
      const startedAt = performance.now();
      this.visibleTrackCount = 0;
      // Reset group visibility when tracks are reloaded
      this.hiddenGroups = new Set();

      const lineColor = await this.resolveTrackLineColor();

      this.visibleTrackCount = this.geojson.features.length;
      this.detachTrackPointLayerHandlers();

      // Remove old layers and source on the overlay map
      for (const layerId of [
        'track-points-layer',
        'tracks-highlight-circle-layer',
        'tracks-dot-layer',
        'tracks-overview-dots',
        'tracks-highlight-dash-layer',
        'tracks-highlight-layer',
        'tracks-layer',
      ]) {
        if (this.overlayMap.getLayer(layerId)) this.overlayMap.removeLayer(layerId);
      }
      for (const sourceId of ['track-points', 'tracks-overview', 'tracks']) {
        if (this.overlayMap.getSource(sourceId)) this.overlayMap.removeSource(sourceId);
      }

      // Add GeoJSON source on overlay map
      this.overlayMap.addSource('tracks', {
        type: 'geojson',
        data: this.geojson,
      });

      // Main tracks layer
      this.overlayMap.addLayer({
        id: 'tracks-layer',
        type: 'line',
        source: 'tracks',
        layout: { 'line-join': 'round', 'line-cap': 'round' },
        paint: {
          'line-color': lineColor,
          'line-width': ['interpolate', ['linear'], ['zoom'], 0, 5, 7, 4, 14, 3.5, 22, 2],
          'line-opacity': 1,
        },
      });

      // Apply all layer opacities
      this.applyAllLayerOpacities();

      // Highlight layer (solid amber, only shows selected track)
      this.overlayMap.addLayer({
        id: 'tracks-highlight-layer',
        type: 'line',
        source: 'tracks',
        layout: { 'line-join': 'round', 'line-cap': 'round' },
        paint: { 'line-color': TRACK_SELECTED_COLOR, 'line-width': 6, 'line-opacity': 1 },
        filter: ['==', ['get', 'id'], -1],
      });

      // Highlight dash layer (white dashed overlay on selected)
      this.overlayMap.addLayer({
        id: 'tracks-highlight-dash-layer',
        type: 'line',
        source: 'tracks',
        layout: { 'line-join': 'round', 'line-cap': 'round' },
        paint: { 'line-color': TRACK_COLOR, 'line-width': 6, 'line-opacity': 1, 'line-dasharray': [2, 3] },
        filter: ['==', ['get', 'id'], -1],
      });

      // Circle layer for degenerate tracks (trackLengthInMeter < 50 m) stored as Point features
      this.overlayMap.addLayer({
        id: 'tracks-dot-layer',
        type: 'circle',
        source: 'tracks',
        filter: ['==', ['geometry-type'], 'Point'],
        paint: {
          'circle-color': lineColor,
          'circle-radius': 6,
          'circle-opacity': 1,
          'circle-stroke-color': this.mapThemeSelected === 'dark' ? '#ffffff' : '#1a1a1a',
          'circle-stroke-width': 1.5,
        },
      });

      // Highlight circle for a selected degenerate track
      this.overlayMap.addLayer({
        id: 'tracks-highlight-circle-layer',
        type: 'circle',
        source: 'tracks',
        filter: ['all', ['==', ['geometry-type'], 'Point'], ['==', ['get', 'id'], -1]],
        paint: { 'circle-color': '#FFB300', 'circle-radius': 9, 'circle-opacity': 1 },
      });

      // ── Overview dots: one circle per track centre, visible at low zoom ──────
      // At world view, short/medium tracks are sub-pixel as lines. This dedicated
      // Point source shows a dot so every track is visible at any zoom level.
      // Long tracks remain visible as lines; the dot sits beneath them harmlessly.
      const overviewFeatures = buildTrackOverviewFeatures(this.geojson);
      this.overlayMap.addSource('tracks-overview', {
        type: 'geojson',
        data: { type: 'FeatureCollection', features: overviewFeatures },
      });
      this.overlayMap.addLayer({
        id: 'tracks-overview-dots',
        type: 'circle',
        source: 'tracks-overview',
        maxzoom: 10,
        paint: {
          'circle-color': lineColor,
          'circle-radius': 5,
          'circle-opacity': 0.85,
          'circle-stroke-color': this.mapThemeSelected === 'dark' ? '#ffffff' : '#1a1a1a',
          'circle-stroke-width': 1,
        },
      });

      // ── Individual GPS track points with direction arrows (visible at zoom 18+) ──
      if (!this.overlayMap.hasImage('track-point-arrow')) {
        const arrow = createArrowImage();
        this.overlayMap.addImage('track-point-arrow', arrow, { pixelRatio: arrow.pixelRatio });
      }
      this.overlayMap.addSource('track-points', {
        type: 'geojson',
        data: { type: 'FeatureCollection', features: [] },
      });
      this.overlayMap.addLayer({
        id: 'track-points-layer',
        type: 'symbol',
        source: 'track-points',
        minzoom: TRACK_POINTS_MIN_ZOOM,
        layout: {
          'icon-image': 'track-point-arrow',
          'icon-size': 1,
          'icon-rotate': ['get', 'bearing'],
          'icon-rotation-alignment': 'map',
          'icon-allow-overlap': true,
          'icon-ignore-placement': true,
        },
        paint: {
          'icon-opacity': 0.9,
        },
      });

      this.attachTrackPointLayerHandlers();

      // Re-apply heatmap if it was active (persists across theme changes and reloads)
      if (this.heatmapVisible && this.heatmapOverlay && this.geojson) {
        this.heatmapOverlay.show(this.geojson);
        this.applyLayerOpacity('heatmap');
      }

      this.updateTrackLineWidth();
      this.applyTracksVisibility();
      this.scheduleDetailCheck();
      console.log('[tracks] Map layers rebuilt', {
        durationMs: Math.round(performance.now() - startedAt),
        trackCount: this.geojson.features.length,
        overviewDotCount: overviewFeatures.length,
        hasPalette: this.legendEntries.length > 0,
        legendGroupCount: this.legendEntries.length,
        heatmapVisible: this.heatmapVisible,
      });
    },

    detachTrackPointLayerHandlers() {
      if (!this.overlayMap || !this.trackPointLayerHandlers) return;
      const handlers = this.trackPointLayerHandlers;
      this.overlayMap.off('click', 'track-points-layer', handlers.click);
      this.overlayMap.off('mouseenter', 'track-points-layer', handlers.mouseenter);
      this.overlayMap.off('mouseleave', 'track-points-layer', handlers.mouseleave);
      this.trackPointLayerHandlers = null;
    },

    attachTrackPointLayerHandlers() {
      if (!this.overlayMap) return;
      this.detachTrackPointLayerHandlers();
      const handlers = {
        click: (e) => {
          if (!e.features || e.features.length === 0) return;
          e.originalEvent.stopPropagation();
          const f = e.features[0];
          const trackId = f.properties.trackId;
          const pointIndex = f.properties.pointIndex;
          this.showTrackPointPopup(e.lngLat, trackId, pointIndex);
        },
        mouseenter: () => {
          this.overlayMap.getCanvas().style.cursor = 'pointer';
        },
        mouseleave: () => {
          this.overlayMap.getCanvas().style.cursor = '';
        },
      };
      this.overlayMap.on('click', 'track-points-layer', handlers.click);
      this.overlayMap.on('mouseenter', 'track-points-layer', handlers.mouseenter);
      this.overlayMap.on('mouseleave', 'track-points-layer', handlers.mouseleave);
      this.trackPointLayerHandlers = handlers;
    },

    // ─── Individual GPS track points ─────────────────────────────────

    /** Rebuild the track-points GeoJSON source from visible track coordinates. */
    updateTrackPointsSource() {
      // Extract reactive refs to local vars once to avoid repeated Vue proxy access
      const overlayMap = this.overlayMap;
      const geojson = this.geojson;
      if (!overlayMap || !geojson) return;
      const zoom = overlayMap.getZoom();

      // Clear if disabled or below minimum zoom
      if (!this.trackPointsVisible || zoom < TRACK_POINTS_MIN_ZOOM) {
        const src = overlayMap.getSource('track-points');
        if (src) src.setData({ type: 'FeatureCollection', features: [] });
        return;
      }

      const trackPrecisions = this.trackPrecisions;
      const gpsTracksById = this.gpsTracksById;

      // Build viewport bounds for filtering
      const mapBounds = overlayMap.getBounds();
      const sw = mapBounds.getSouthWest();
      const ne = mapBounds.getNorthEast();
      const latPad = (ne.lat - sw.lat) * 0.1;
      const lngPad = (ne.lng - sw.lng) * 0.1;
      const minLat = sw.lat - latPad,
        maxLat = ne.lat + latPad;
      const minLng = sw.lng - lngPad,
        maxLng = ne.lng + lngPad;

      const features = geojson.features;
      const pointFeatures = [];
      for (let f = 0; f < features.length; f++) {
        const feature = features[f];
        if (feature.geometry.type !== 'LineString') continue;
        const trackId = feature.properties.id;

        // Include tracks at 10m or better precision (arrows are useful at any detail level)
        const precision = trackPrecisions.get(trackId) ?? OVERVIEW_PRECISION;
        if (precision > 10) continue;

        // Track-level bbox check: skip tracks entirely outside the viewport
        const track = gpsTracksById.get(trackId);
        if (track?.bboxMinLat != null) {
          if (
            track.bboxMaxLat < minLat ||
            track.bboxMinLat > maxLat ||
            track.bboxMaxLng < minLng ||
            track.bboxMinLng > maxLng
          )
            continue;
        }

        const coords = feature.geometry.coordinates;
        for (let i = 0; i < coords.length; i++) {
          const [lng, lat] = coords[i];
          // Viewport filter
          if (lat < minLat || lat > maxLat || lng < minLng || lng > maxLng) continue;

          // Compute bearing from this point toward the next (last point uses incoming bearing)
          let deg = 0;
          if (i < coords.length - 1) {
            deg = bearing(lng, lat, coords[i + 1][0], coords[i + 1][1]);
          } else if (i > 0) {
            deg = bearing(coords[i - 1][0], coords[i - 1][1], lng, lat);
          }

          pointFeatures.push({
            type: 'Feature',
            geometry: { type: 'Point', coordinates: [lng, lat] },
            properties: { trackId, pointIndex: i, bearing: Math.round(deg) },
          });
        }
      }

      const src = overlayMap.getSource('track-points');
      if (src) src.setData({ type: 'FeatureCollection', features: pointFeatures });
    },

    /** Show a popup with full data for a specific track point. */
    async showTrackPointPopup(lngLat, trackId, pointIndex) {
      // Close any existing popup
      if (this.trackPointsPopup) {
        this.trackPointsPopup.remove();
        this.trackPointsPopup = null;
      }

      // The track-points layer is emitted by updateTrackPointsSource() from
      // the SIMPLIFIED_SHAPE LineString currently loaded for this track, and
      // `pointIndex` is the array index into that coordinate list. Under the
      // canonical-metric-LOD architecture the simplified variant carries
      // GEOMETRY ONLY (lat/lng/alt/timestamp/canonicalPointIndex); all
      // derived per-point metrics (speed/slope/energy/…) live on the
      // canonical RAW_OUTLIER_CLEANED stream. So we:
      //   1. resolve the clicked SIMPLIFIED_SHAPE vertex by pointIndex
      //      (geometry + canonicalPointIndex back-pointer)
      //   2. look up the matching canonical point by canonicalPointIndex
      //      and read metric fields off that row.
      const precision = this.trackPrecisions.get(trackId) ?? OVERVIEW_PRECISION;
      const cacheKey = `${trackId}|${precision}`;

      // Fetch or use cached SIMPLIFIED_SHAPE rows (geometry-only).
      let details = this.trackPointsDetailsCache.get(cacheKey);
      if (!details) {
        try {
          details = await fetchTrackPointsForRenderedShape(trackId, precision);
          this.trackPointsDetailsCache.set(cacheKey, details);
        } catch (e) {
          console.warn('Failed to fetch simplified point details for track', trackId, e);
          return;
        }
      }

      const point = details.find((p) => p.pointIndex === pointIndex);
      if (!point) {
        console.warn(`Point index ${pointIndex} not found in details for track ${trackId} at precision ${precision}m`);
        return;
      }

      // Fetch or use cached canonical RAW_OUTLIER_CLEANED rows (with metrics).
      let canonicalPoints = this.trackPointsCanonicalCache.get(trackId);
      if (!canonicalPoints) {
        try {
          canonicalPoints = await fetchTrackCanonicalPoints(trackId);
          this.trackPointsCanonicalCache.set(trackId, canonicalPoints);
        } catch (e) {
          console.warn('Failed to fetch canonical points for track', trackId, e);
          // Continue with geometry-only popup — better than nothing.
          canonicalPoints = [];
        }
      }

      // Resolve the canonical metric row via the SIMPLIFIED_SHAPE back-pointer.
      // Fallback: nearest-by-pointIndex if back-pointer missing (very early
      // vertices or unmatched timestamps).
      const canonicalIndex = point.canonicalPointIndex;
      let canonical = null;
      if (canonicalIndex != null) {
        canonical = canonicalPoints.find((p) => p.pointIndex === canonicalIndex) ?? null;
      }

      // Build popup HTML
      const fmt = (v, decimals = 1) => (v != null ? Number(v).toFixed(decimals) : '—');
      const fmtTime = (v) => {
        if (!v) return '—';
        const d = new Date(v);
        return formatDateAndTimeWithSeconds(d);
      };
      const fmtDuration = (secs) => {
        if (secs == null) return '—';
        const h = Math.floor(secs / 3600);
        const m = Math.floor((secs % 3600) / 60);
        const s = Math.round(secs % 60);
        return h > 0 ? `${h}h ${m}m ${s}s` : m > 0 ? `${m}m ${s}s` : `${s}s`;
      };

      // Geometry from the SIMPLIFIED_SHAPE vertex (truth for the clicked
      // map position); metrics from the canonical row.
      const lat = point.pointLongLat?.coordinates?.[1] ?? lngLat.lat;
      const lng = point.pointLongLat?.coordinates?.[0] ?? lngLat.lng;
      const m = canonical ?? {};

      const rows = [
        ['Point', `${point.pointIndex + 1} / ${(point.pointIndexMax ?? 0) + 1}`],
        ['Time', fmtTime(point.pointTimestamp)],
        ['Lat / Lng', `${fmt(lat, 6)} / ${fmt(lng, 6)}`],
        ['Altitude', `${fmt(point.pointAltitude, 1)} m`],
        ['Speed', `${fmt(m.speedInKmhMovingWindow, 1)} km/h`],
        ['Dist from start', `${fmt((m.distanceInMeterSinceStart ?? 0) / 1000, 2)} km`],
        ['Dist prev point', `${fmt(m.distanceInMeterBetweenPoints, 1)} m`],
        ['Time prev point', `${fmt(m.durationBetweenPointsInSec, 1)} s`],
        ['Duration', fmtDuration(m.durationSinceStart)],
        ['Ascent', `${fmt(m.ascentInMeterSinceStart, 0)} m`],
        ['Descent', `${fmt(m.descentInMeterSinceStart, 0)} m`],
        ['Slope', `${fmt(m.slopePercentageInMovingWindow, 1)} %`],
        ['Elev gain/h', `${fmt(m.elevationGainPerHourMovingWindow, 0)} m/h`],
        ['Elev loss/h', `${fmt(m.elevationLossPerHourMovingWindow, 0)} m/h`],
      ];

      // Add energy fields if available
      if (m.energyTotalWh != null) {
        rows.push(['Est. energy (seg)', `${fmt(m.energyTotalWh, 2)} Wh`]);
        rows.push(['Est. energy (cum)', `${fmt(m.energyCumulativeWh, 1)} Wh`]);
        rows.push(['Est. power', `${fmt(m.powerWatts, 0)} W`]);
      }

      const html = `
    <div class="mtl-point-popup">
      <div class="mtl-point-popup-header">Track #${trackId}</div>
      <table class="mtl-point-popup-table">
        ${rows.map(([label, val]) => `<tr><td class="mtl-pp-label">${label}</td><td class="mtl-pp-value">${val}</td></tr>`).join('')}
      </table>
    </div>`;

      this.trackPointsPopup = markRaw(
        new maplibregl.Popup({ closeButton: true, maxWidth: '280px', className: 'mtl-point-popup-container' })
          .setLngLat(lngLat)
          .setHTML(html)
          .addTo(this.overlayMap)
      );
    },

    // --- Track highlight helpers ---

    selectTrackById(trackId) {
      const feature = this.gpsTrackIdToFeature.get(trackId);
      if (!feature) return;
      this.selectTrack(trackId, feature);
    },

    selectTrack(trackId, feature) {
      if (this.selectedTrackId === trackId) {
        this.deselectTrack();
        return;
      }
      this.deselectTrack();
      this.selectedTrackId = trackId;
      this.selectedFeature = feature;

      if (this.overlayMap?.getLayer('tracks-highlight-layer')) {
        this.overlayMap.setFilter('tracks-highlight-layer', ['==', ['get', 'id'], trackId]);
      }
      if (this.overlayMap?.getLayer('tracks-highlight-dash-layer')) {
        this.overlayMap.setFilter('tracks-highlight-dash-layer', ['==', ['get', 'id'], trackId]);
      }
      if (this.overlayMap?.getLayer('tracks-highlight-circle-layer')) {
        this.overlayMap.setFilter('tracks-highlight-circle-layer', [
          'all',
          ['==', ['geometry-type'], 'Point'],
          ['==', ['get', 'id'], trackId],
        ]);
      }
    },

    deselectTrack() {
      this.selectedTrackId = null;
      this.selectedFeature = null;

      if (this.overlayMap?.getLayer('tracks-highlight-layer')) {
        this.overlayMap.setFilter('tracks-highlight-layer', ['==', ['get', 'id'], -1]);
      }
      if (this.overlayMap?.getLayer('tracks-highlight-dash-layer')) {
        this.overlayMap.setFilter('tracks-highlight-dash-layer', ['==', ['get', 'id'], -1]);
      }
      if (this.overlayMap?.getLayer('tracks-highlight-circle-layer')) {
        this.overlayMap.setFilter('tracks-highlight-circle-layer', [
          'all',
          ['==', ['geometry-type'], 'Point'],
          ['==', ['get', 'id'], -1],
        ]);
      }
    },

    getTrackPopupMeta(id) {
      const track = this.gpsTracksById.get(id);
      const rawName = track?.trackName?.trim();
      const rawDescription = track?.trackDescription?.trim();
      const name = rawName || rawDescription || `Track ${id}`;
      const date = track?.startDate ? formatDate(new Date(track.startDate)) : '';
      const description = rawName && rawDescription && rawDescription !== rawName ? rawDescription : '';
      return {
        id,
        displayName: name,
        description,
        date: date || 'No date',
        activityType: track?.activityType || '',
      };
    },

    closeSwissMobilityPopup() {
      this.swissMobilityPopup = { visible: false, pos: { x: 0, y: 0 }, routes: [] };
    },

    async identifySwissMobilityRoutes(lngLat, point) {
      const OVERLAY_LAYER_MAP = {
        wanderland: { bodId: 'ch.astra.wanderland', type: 'Hiking', icon: 'bi bi-signpost-2' },
        veloland: { bodId: 'ch.astra.veloland', type: 'Bike', icon: 'bi bi-bicycle' },
        mountainbikeland: { bodId: 'ch.astra.mountainbikeland', type: 'Mountainbike', icon: 'bi bi-bicycle' },
        wanderwege: { bodId: 'ch.swisstopo.swisstlm3d-wanderwege', type: 'Trail', icon: 'bi bi-signpost' },
      };
      const activeLayers = this.activeOverlays.filter((id) => OVERLAY_LAYER_MAP[id]).map((id) => OVERLAY_LAYER_MAP[id]);
      if (!activeLayers.length) return;

      const layerParam = activeLayers.map((l) => l.bodId).join(',');
      const bounds = this.overlayMap.getBounds();
      const canvas = this.overlayMap.getCanvas();
      const mapExtent = `${bounds.getWest()},${bounds.getSouth()},${bounds.getEast()},${bounds.getNorth()}`;
      const imageDisplay = `${canvas.width},${canvas.height},96`;
      const url =
        `https://api3.geo.admin.ch/rest/services/api/MapServer/identify` +
        `?geometry=${lngLat.lng},${lngLat.lat}` +
        `&geometryType=esriGeometryPoint` +
        `&layers=all:${layerParam}` +
        `&mapExtent=${mapExtent}` +
        `&imageDisplay=${imageDisplay}` +
        `&tolerance=10&lang=en&returnGeometry=false&sr=4326`;

      try {
        const response = await fetch(url);
        if (!response.ok) return;
        const data = await response.json();
        if (!data.results || data.results.length === 0) return;
        const routes = data.results.map((r) => {
          const layerInfo = activeLayers.find((l) => l.bodId === r.layerBodId) || {};
          return {
            type: layerInfo.type || r.layerName,
            icon: layerInfo.icon || 'bi bi-map',
            name: r.attributes.chmobil_title || r.attributes.label || '—',
            number: r.attributes.chmobil_route_number,
          };
        });
        this.swissMobilityPopup = { visible: true, pos: { x: point.x + 12, y: point.y - 10 }, routes };
      } catch {
        // silently ignore — best-effort enrichment
      }
    },

    showTrackSelectionPopup(point, trackIds) {
      this.closeSelectionPopup();
      this.selectionPopupTrackIds = trackIds;
      this.trackSelectionSheetVisible = true;
    },

    closeSelectionPopup() {
      this.trackSelectionSheetVisible = false;
      this.selectionPopupTrackIds = [];
    },

    navigateMediaTo(id) {
      if (id != null) this.mediaSheetMediaId = id;
    },

    closeMediaSheet() {
      this.mediaSheetMediaId = null;
      this.mediaNavList = [];
    },

    _buildMediaNavList(originId) {
      if (!this.mediaLoadedPoints.length) {
        this.mediaNavList = [];
        return;
      }
      // Limit navigation to photos within the current visible viewport
      const viewBounds = this.overlayMap?.getBounds();
      const visiblePoints = viewBounds
        ? this.mediaLoadedPoints.filter(
            (p) =>
              p.lat >= viewBounds.getSouth() &&
              p.lat <= viewBounds.getNorth() &&
              p.lng >= viewBounds.getWest() &&
              p.lng <= viewBounds.getEast()
          )
        : [...this.mediaLoadedPoints];
      const origin = visiblePoints.find((p) => p.id === originId) ?? visiblePoints[0];
      if (!origin) {
        this.mediaNavList = visiblePoints;
        return;
      }
      const dist = (p) => {
        const dlat = p.lat - origin.lat,
          dlng = p.lng - origin.lng;
        return dlat * dlat + dlng * dlng;
      };
      this.mediaNavList = visiblePoints.sort((a, b) => dist(a) - dist(b));
    },

    onPopupTrackSelect(id) {
      this.closeSelectionPopup();
      this.selectTrackById(id);
      this.openTrackDetails(id, TRACK_DETAILS_MAP_DETENT);
    },

    openTrackDetails(trackId = this.selectedTrackId, initialDetent = TRACK_DETAILS_DEFAULT_DETENT) {
      if (trackId == null) return;

      const feature = this.gpsTrackIdToFeature.get(trackId) || this.selectedFeature;
      const p = feature?.properties;
      this.trackDetailsId = trackId;
      this.trackDetailsInfo = {
        id: trackId,
        name: p?.trackName || p?.trackDescription || '',
        description: '',
        activityType: p?.activityType || '',
      };
      this.trackDetailsInitialDetent = initialDetent;
      this.trackDetailsVisible = true;
    },

    onTrackDetailsSheetClosed() {
      this.trackDetailsId = null;
      this.trackDetailsInfo = { id: null, name: '', description: '', activityType: '' };
      this.deselectTrack();
    },

    onTrackDetailsLoaded({ id, name, description, activityType }) {
      this.trackDetailsInfo = { id, name, description, activityType: activityType || '' };
    },

    onTrackBrowserSelect(trackId) {
      this.selectTrackById(trackId);

      // Fit to track bounds
      const feature = this.gpsTrackIdToFeature.get(trackId);
      if (feature?.geometry?.coordinates?.length > 0) {
        const coords = feature.geometry.coordinates;
        const bounds = coords.reduce((b, c) => b.extend(c), new maplibregl.LngLatBounds(coords[0], coords[0]));
        this.overlayMap.fitBounds(bounds, { padding: 32, maxZoom: Math.max(this.overlayMap.getZoom(), 15) });
      }
    },

    onTrackBrowserOpenDetails(trackId) {
      this.selectTrackById(trackId);
      this.openTrackDetails(trackId, TRACK_DETAILS_EXPANDED_DETENT);
    },

    onToggleTrackPoints() {
      this.trackPointsVisible = !this.trackPointsVisible;
      mapPreferences.writeTrackPointsVisible(this.trackPointsVisible);
      this.updateTrackPointsSource();
    },

    onLegendCollapsed(val) {
      this.legendCollapsed = val;
      mapPreferences.writeLegendCollapsed(val);
    },

    onHiddenGroupsChanged(groups) {
      this.hiddenGroups = groups;
      this.applyGroupFilter();
    },

    /** Apply a Mapbox filter to hide deactivated legend groups on all track layers. */
    applyGroupFilter() {
      if (!this.overlayMap) return;
      const hidden = this.hiddenGroups;
      let filter = null;
      if (hidden.size > 0) {
        // Exclude features whose filterGroup is in the hidden set
        filter = ['!', ['in', ['get', 'filterGroup'], ['literal', [...hidden]]]];
      }
      for (const layerId of ['tracks-layer', 'tracks-dot-layer', 'tracks-overview-dots']) {
        if (this.overlayMap.getLayer(layerId)) {
          if (layerId === 'tracks-dot-layer') {
            // Dot layer already has a geometry-type filter; combine with group filter
            const baseFilter = ['==', ['geometry-type'], 'Point'];
            this.overlayMap.setFilter(layerId, filter ? ['all', baseFilter, filter] : baseFilter);
          } else {
            this.overlayMap.setFilter(layerId, filter);
          }
        }
      }
      // Update visible track count to reflect hidden groups
      if (this.geojson) {
        const total = this.geojson.features.length;
        if (hidden.size > 0) {
          this.visibleTrackCount = this.geojson.features.filter((f) => !hidden.has(f.properties.filterGroup)).length;
        } else {
          this.visibleTrackCount = total;
        }
      }
    },

    // ── Geo drawing ──
    onStartGeoDrawing(paramDef) {
      if (!this.overlayMap) return;
      // Create overlay if needed
      if (!this.geoDrawingOverlay) {
        this.geoDrawingOverlay = markRaw(new GeoDrawingOverlay(this.overlayMap));
      }
      this.geoDrawingParamDef = paramDef;
      this.geoDrawPointCount = 0;

      // Render any existing shapes from the filter so the user sees them while drawing
      this.renderExistingGeoShapes();

      // Close the filter sheet so the user can interact with the map
      if (this.$refs.filterTool?.close) this.$refs.filterTool.close();

      // Map from param type to drawing mode
      const modeMap = {
        GEO_CIRCLE: 'circle',
        GEO_RECTANGLE: 'rectangle',
        GEO_POLYGON: 'polygon',
      };
      const mode = modeMap[paramDef.type ?? ''];
      if (!mode) return;

      const onDrawingComplete = (shape) => {
        // Forward to FilterTool → CustomFilter (store first so renderExisting picks it up)
        const filterTool = this.$refs.filterTool;
        if (filterTool) filterTool.onGeoDrawingComplete(paramDef, shape);
        this.geoDrawingParamDef = null;
        this.geoDrawPointCount = 0;
        // Re-render all shapes (including the newly drawn one)
        this.renderExistingGeoShapes();
        // Reopen filter sheet to show the result
        if (filterTool?.toggle) filterTool.toggle();
      };

      const onStateChange = () => {
        this.geoDrawPointCount = this.geoDrawingOverlay?.getPointCount() ?? 0;
      };

      this.geoDrawingOverlay.startDrawing(mode, onDrawingComplete, onStateChange);
    },

    onGeoDrawUndo() {
      if (this.geoDrawingOverlay) {
        this.geoDrawingOverlay.undoLastPoint();
      }
    },

    onGeoDrawFinish() {
      if (this.geoDrawingOverlay) {
        this.geoDrawingOverlay.finishPolygon();
      }
    },

    onGeoDrawCancel() {
      if (this.geoDrawingOverlay) {
        this.geoDrawingOverlay.cancelDrawing();
      }
      this.geoDrawingParamDef = null;
      this.geoDrawPointCount = 0;
    },

    onClearGeoShape(_paramDef) {
      // Clear drawn shapes from the map overlay
      if (this.geoDrawingOverlay) {
        this.geoDrawingOverlay.clearAll();
      }
    },

    /** Render all existing geo shapes from the filter so they're visible on the map. */
    renderExistingGeoShapes() {
      if (!this.geoDrawingOverlay) return;
      this.geoDrawingOverlay.clearAll();
      const filterTool = this.$refs.filterTool;
      if (!filterTool?.getGeoShapes) return;
      const shapes = filterTool.getGeoShapes();
      const labels = shapes.labels ?? {};
      for (const [key, circle] of Object.entries(shapes.circles)) {
        if (circle) this.geoDrawingOverlay.renderCircle(circle, undefined, labels[key]);
      }
      for (const [key, rect] of Object.entries(shapes.rectangles)) {
        if (rect) this.geoDrawingOverlay.renderRectangle(rect, undefined, labels[key]);
      }
      for (const [key, polygon] of Object.entries(shapes.polygons)) {
        if (polygon) this.geoDrawingOverlay.renderPolygon(polygon, undefined, labels[key]);
      }
    },

    async onFilterApplied(filterResult) {
      if (!this.initialLoadDone) {
        console.log(
          '[Map] Suppressing filter event during initial load — track collection loader already applies the active filter'
        );
        return;
      }
      console.log('map got filter applied — using IDs-only fast path');
      this.showLoader = true;

      try {
        // Fast path: fetch only matching IDs from server, resolve data from local cache
        const result = await applyTrackFilter({ filterResult });

        // Update in-memory state
        this.geojson = markRaw(result.geojson);
        this.gpsTracksById = markRaw(result.gpsTracksById);
        this.publishGpsTrackMetadataChanges();
        this.gpsTrackIdToFeature = markRaw(result.gpsTrackIdToFeature);
        if (result.trackPrecisions) {
          this.trackPrecisions = markRaw(result.trackPrecisions);
        }
        this.activeTrackFilterResult = result.filterResult ?? this.activeTrackFilterResult;
        this.totalTrackCount = result.standardFilterCount;
        this.visibleTrackCount = result.gpsTracksById.size;

        // Clear selection state
        this.selectedTrackId = null;
        this.selectedFeature = null;
        this.closeSelectionPopup();

        // Re-render tracks on the map without destroying/recreating the map
        await this.addTracksToMap();
        this.updateTracksSource();
        this.maybeLoadBackgroundTracks(result.filterResult);
      } catch (e) {
        // Fallback to full reload if IDs-only path fails (e.g. cache miss)
        console.warn('IDs-only filter failed, falling back to full reload:', e);
        await this.reloadMap(false);
      } finally {
        this.showLoader = false;
      }

      if (this.$refs.statistics?.active) this.$refs.statistics.fetchStatistics();
      // Re-render geo shapes after map reload only if filter panel is still open
      if (this.activeToolId === 'filter') {
        this.$nextTick(() => {
          if (this.overlayMap) {
            if (!this.geoDrawingOverlay) {
              this.geoDrawingOverlay = markRaw(new GeoDrawingOverlay(this.overlayMap));
            }
            this.renderExistingGeoShapes();
          }
        });
      }
    },

    async onFilterStyleChanged() {
      if (!this.initialLoadDone) return;
      await this.updateTrackStyle();
    },
  };

  const computedRefs = {};
  const boundMethodCache = new Map();

  const ctx = new Proxy(
    {},
    {
      get(_target, key) {
        if (key === '$refs') return refsProxy;
        if (key === '$emit') return emit;
        if (key === '$toast') return toast;
        if (key === '$nextTick') return nextTick;
        if (typeof key !== 'string') return undefined;
        if (Object.prototype.hasOwnProperty.call(methodDefinitions, key)) {
          if (!boundMethodCache.has(key)) {
            boundMethodCache.set(key, methodDefinitions[key].bind(ctx));
          }
          return boundMethodCache.get(key);
        }
        if (Object.prototype.hasOwnProperty.call(computedRefs, key)) return computedRefs[key].value;
        if (Object.prototype.hasOwnProperty.call(setupBindings, key)) {
          const value = setupBindings[key];
          return isRef(value) ? value.value : value;
        }
        if (Object.prototype.hasOwnProperty.call(state, key)) return state[key];
        if (Object.prototype.hasOwnProperty.call(props, key)) return props[key];
        return undefined;
      },
      set(_target, key, value) {
        if (typeof key !== 'string') return false;
        state[key] = value;
        return true;
      },
    }
  );

  for (const [name, getter] of Object.entries(computedDefinitions)) {
    computedRefs[name] = computed(() => getter.call(ctx));
  }

  async function mounted() {
    startupLog('map', 'Map component mounted');
    this.scheduleFreshnessDismissTimer();
    this.activeOverlays = mapPreferences.readActiveOverlays();
    // Load layer opacities
    Object.assign(this.layerOpacities, mapPreferences.readLayerOpacities());
    const savedBasemap = mapPreferences.readBasemapEnabled();
    if (savedBasemap !== null) this.basemapEnabled = savedBasemap;
    const savedTracks = mapPreferences.readTracksEnabled();
    if (savedTracks !== null) this.tracksEnabled = savedTracks;
    if (mapPreferences.consumeLegacyAutoDimPreference(this.layerOpacities)) {
      mapPreferences.writeLayerOpacities(this.layerOpacities);
    }
    // Migrate from old system (mapEnhanced + dimOpacity + grayscaleAmount) — always start fresh at 100%
    if (mapPreferences.hasLegacyAutoDimPrefs()) {
      this.layerOpacities.basemap = 100; // Fresh start — do not carry over old dim value
      mapPreferences.clearLegacyAutoDimPrefs();
      mapPreferences.writeLayerOpacities(this.layerOpacities);
    }
    const savedTrackPoints = mapPreferences.readTrackPointsVisible();
    if (savedTrackPoints !== null) this.trackPointsVisible = savedTrackPoints;
    const savedHeatmap = mapPreferences.readHeatmapVisible();
    if (savedHeatmap !== null) this.heatmapVisible = savedHeatmap;
    window.addEventListener(MAP_ARCHIVE_STALE_EVENT, this.handleMapArchiveStale);
    try {
      await this.reloadMap(true);
      startupLog('map', 'Initial map reload completed');
    } catch (error) {
      startupError('map', 'Initial map reload failed', describeError(error));
      throw error;
    }
    this._onOnline = markRaw(() => this.onBrowserOnline());
    window.addEventListener('online', this._onOnline);
  }

  function beforeUnmount() {
    this.stopMapStatusPolling();
    if (this.retryTimeoutId) clearTimeout(this.retryTimeoutId);
    if (this.freshnessDismissTimer) clearTimeout(this.freshnessDismissTimer);
    if (this._onOnline) window.removeEventListener('online', this._onOnline);
    window.removeEventListener(MAP_ARCHIVE_STALE_EVENT, this.handleMapArchiveStale);
    if (this.detailDebounceTimer) clearTimeout(this.detailDebounceTimer);
    if (this.detailAbortController) this.detailAbortController.abort();
    if (this.bulk10mController) this.bulk10mController.abort();
    if (this.trackPointsPopup) {
      this.trackPointsPopup.remove();
      this.trackPointsPopup = null;
    }
    this.clearLocationSearchMarker();
    if (this._resizeObserver) this._resizeObserver.disconnect();
    if (this.heatmapOverlay) {
      this.heatmapOverlay.destroy();
      this.heatmapOverlay = null;
    }
    if (this.geoDrawingOverlay) {
      this.geoDrawingOverlay.destroy();
      this.geoDrawingOverlay = null;
    }
    if (this.overlayMap) {
      this.detachTrackPointLayerHandlers();
      this.overlayMap.remove();
      this.overlayMap = undefined;
    }
    if (this.map) {
      this.map.remove();
      this.map = undefined;
    }
  }

  onMounted(() => mounted.call(ctx));
  onBeforeUnmount(() => beforeUnmount.call(ctx));

  watch(serverFreshnessToken, () => ctx.maybeAutoFreshenAfterLogin());
  watch(
    () => state.initialLoadDone,
    () => ctx.maybeAutoFreshenAfterLogin()
  );

  const boundMethods = {};
  for (const name of Object.keys(methodDefinitions)) {
    boundMethods[name] = (...args) => ctx[name](...args);
  }

  return {
    ...toRefs(state),
    ...setupBindings,
    ...computedRefs,
    ...boundMethods,
  };
}
