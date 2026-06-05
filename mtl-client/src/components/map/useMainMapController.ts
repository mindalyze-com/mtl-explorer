import { computed, isRef, markRaw, nextTick, onBeforeUnmount, onMounted, reactive, toRefs, watch } from 'vue';
import type { ComputedRef } from 'vue';
import { useIndexerStatus } from '@/composables/useIndexerStatus';
import { useDataFreshness } from '@/composables/useDataFreshness';
import 'maplibre-gl/dist/maplibre-gl.css';
import { MAP_ARCHIVE_STALE_EVENT } from '@/utils/cachingPmtilesSource';
import { useFilterStore } from '@/stores/filterStore';
import { useMapSettingsStore } from '@/stores/mapSettingsStore';
import { ColorPalette } from '@/components/filter/ColorPalette';
import { REPLAY_DEFAULT_TARGET_DURATION_SECONDS } from '@/components/replay/trackReplayPath';
import { REPLAY_DEFAULT_CAMERA_PRESET, REPLAY_DEFAULT_CAMERA_SMOOTHNESS } from '@/components/replay/trackReplayCamera';
import { describeError, startupError, startupLog } from '@/utils/startupDiagnostics';
import thumbOsmTopo from '@/assets/map-layer/osm_topo.jpg';
import thumbSwissColor from '@/assets/map-layer/swiss_color_contrast.jpg';
import thumbSwissLight from '@/assets/map-layer/swiss_topo_light.jpg';
import thumbOsmLight from '@/assets/map-layer/osm_light.jpg';
import thumbOsmDark from '@/assets/map-layer/osm_dark.jpg';
import { useMapTools } from '@/components/map/composables/useMapTools';
import { useMapLayerSettings } from '@/components/map/composables/useMapLayerSettings';
import { useTerrainMode } from '@/components/map/composables/useTerrainMode';
import { useTrackLayers } from '@/components/map/composables/useTrackLayers';
import { useTrackPointLayer } from '@/components/map/composables/useTrackPointLayer';
import { useTrackReplay } from '@/components/map/composables/useTrackReplay';
import { useGeoDrawing } from '@/components/map/composables/useGeoDrawing';
import { useMediaAndHeatmap } from '@/components/map/composables/useMediaAndHeatmap';
import { useMapDataLoading } from '@/components/map/composables/useMapDataLoading';
import { useMapRendererLifecycle } from '@/components/map/composables/useMapRendererLifecycle';
import type {
  MainMapControllerReturn,
  MapControllerBoundMethods,
  MapControllerComputedDefinitions,
  MapControllerComputedRefs,
  MapControllerEmit,
  MapControllerMethods,
  MapControllerProps,
  MapControllerRuntime,
  MapControllerSetupBindings,
  MapControllerState,
  MapControllerTemplateRefs,
  ToastLike,
  TrackDetailsDetent,
} from '@/components/map/composables/mapControllerRuntime';
import { useMapStateStore } from '@/stores/mapStateStore';
import { storeToRefs } from 'pinia';
import { isRemoteRasterMapTheme } from '@/components/map/mapStyleResolver';

type ControllerMethod = (this: MapControllerRuntime, ...args: unknown[]) => unknown;
type BoundControllerMethod = (...args: unknown[]) => unknown;

/** Detent positions for the track details bottom sheet. */
const TRACK_DETAILS_DETENTS: TrackDetailsDetent[] = [
  { id: 'compact', height: '35vh' },
  { id: 'default', height: '75vh' },
  { id: 'expanded', height: '92vh' },
];

const TRACK_DETAILS_REPLAY_BACKGROUND_DETENT: TrackDetailsDetent = { id: 'replay-background', height: '76px' };
/** Mid detent used for normal detail opens when map context is less important. */
const TRACK_DETAILS_DEFAULT_DETENT = 'default';

export function useMainMapController(
  props: MapControllerProps,
  emit: MapControllerEmit,
  toast: ToastLike,
  templateRefs: MapControllerTemplateRefs
): MainMapControllerReturn {
  const { isIndexing, isJobPending } = useIndexerStatus();
  const dataFreshness = useDataFreshness();
  const {
    serverFreshnessToken,
    lastChecked: dataFreshnessLastChecked,
    refresh: refreshDataFreshness,
    isFreshnessPollingHealthy,
    appliedFreshnessToken,
    freshnessReloading,
    freshnessStore,
  } = dataFreshness;
  const isAnyPending = computed(() => isIndexing.value || isJobPending.value);
  const filterStore = useFilterStore();
  const mapSettingsStore = useMapSettingsStore();
  const mapStateStore = useMapStateStore();
  const mapStateRefs = storeToRefs(mapStateStore);

  const state = reactive<MapControllerState>({
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
      { name: 'OSM Gray', code: 'grayscale', thumbnail: thumbOsmLight },
      { name: 'OSM Dark', code: 'dark', thumbnail: thumbOsmDark },
    ],

    mapThemeSelected: mapSettingsStore.theme,
    mapSourceMode: mapSettingsStore.mapSourceMode,
    visibleTrackCount: 0,
    totalTrackCount: 0,
    basemapEnabled: mapSettingsStore.basemapEnabled,
    terrainEnabled: mapSettingsStore.terrainEnabled,
    terrainExaggeration: mapSettingsStore.terrainExaggeration,
    terrainTracksReady: false,
    terrainTrackDataDirty: true,
    flatTrackSourceDirty: true,
    flatTrackSourceReduced: false,
    tracksEnabled: mapSettingsStore.tracksEnabled,
    layerOpacities: { ...mapSettingsStore.layerOpacities } as MapControllerState['layerOpacities'],
    filterActive: false,
    colorPalette: markRaw(new ColorPalette()),
    legendEntries: [],
    legendMode: 'categorical',
    legendGradientColors: [],
    legendGradientBucketCount: 100,
    legendCollapsed: mapSettingsStore.legendCollapsed,
    hiddenGroups: new Set(),
    gpsTracksById: markRaw(new Map()),
    gpsTrackIdToFeature: markRaw(new Map()),
    selectedTrackId: mapStateStore.selectedTrackId,
    selectedFeature: null,
    trackSelectionSheetVisible: false,
    selectionPopupTrackIds: [],
    swissMobilityPopup: { visible: false, pos: { x: 0, y: 0 }, routes: [] },
    proximityAbortController: null,
    trackDetailsVisible: false,
    trackDetailsBackgroundDetent: TRACK_DETAILS_REPLAY_BACKGROUND_DETENT,
    trackDetailsDetents: TRACK_DETAILS_DETENTS,
    trackDetailsInitialDetent: TRACK_DETAILS_DEFAULT_DETENT,
    trackDetailsSelectedDetent: undefined,
    trackDetailsId: null,
    trackDetailsInfo: { id: null, name: '', description: '', activityType: '' },
    trackReplayActive: false,
    trackReplayLoading: false,
    trackReplayPlaying: false,
    trackReplayAutoFollow: true,
    trackReplayShowContextTracks: false,
    trackReplayShowTelemetry: true,
    trackReplayTrackId: null,
    trackReplayTrackLabel: '',
    trackReplayProgress: 0,
    trackReplayDurationSeconds: REPLAY_DEFAULT_TARGET_DURATION_SECONDS,
    trackReplaySpeedFactorLabel: '—',
    trackReplayCameraPreset: REPLAY_DEFAULT_CAMERA_PRESET,
    trackReplayCameraSmoothness: REPLAY_DEFAULT_CAMERA_SMOOTHNESS,
    trackReplayDistanceLabel: '0 m',
    trackReplayElapsedLabel: '0m 00s',
    trackReplayTotalLabel: '45s',
    _trackReplayPath: null,
    _trackReplayController: null,
    _trackReplayLayer: null,
    _trackReplayRestoreState: null,
    _trackReplayCameraRail: null,
    _trackReplayCameraViewportKey: '',
    _trackReplayCameraViewportFrame: null,
    _trackReplayViewportOcclusionObserver: null,
    _trackReplayInteractionHandlers: null,
    _trackReplayApplyingCamera: false,
    _trackReplayUserPointerActive: false,
    trackReplayControlsLayout: null,
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
    mediaVisible: mapSettingsStore.mediaVisible,
    mediaBusy: false,
    mediaSheetVisible: false,
    mediaSheetMediaId: null,
    mediaLoadedPoints: [],
    mediaNavList: [],
    heatmapOverlay: null,
    heatmapVisible: mapSettingsStore.heatmapVisible,
    isOffline: false,
    cachedTracksLoaded: false,
    initialLoadDone: false,
    freshLoginAutoFreshenDone: false,
    retryTimeoutId: null,
    retryCount: 0,
    bulk10mController: null,
    trackPrecisions: markRaw(new Map()),
    activeTrackFilterResult: null,
    detailAbortController: null,
    detailDebounceTimer: null,
    activeOverlays: [...mapSettingsStore.activeOverlays],
    _terrainControl: null,
    _terrainTrackLayer: null,
    _syncingView: false, // guard to prevent recursive view-sync loops
    trackPointsVisible: mapSettingsStore.trackPointsVisible, // toggle for direction-arrow point markers
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

  const setupBindings: MapControllerSetupBindings = {
    isIndexing: isAnyPending,
    serverFreshnessToken,
    dataFreshnessLastChecked,
    refreshDataFreshness,
    isFreshnessPollingHealthy,
    appliedFreshnessToken,
    freshnessReloading,
    mapMode: mapStateRefs.mapMode,
  };

  const refsProxy = new Proxy({} as MapControllerRuntime['$refs'], {
    get(_target, key) {
      if (typeof key !== 'string') return undefined;
      return templateRefs[key]?.value;
    },
  });

  const computedDefinitions: MapControllerComputedDefinitions = {
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
        terrain: { enabled: this.terrainEnabled, opacity: this.layerOpacities.terrain },
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
    mapThemesForPanel() {
      if (this.mapSourceMode !== 'remote') return this.mapThemes;
      return this.mapThemes.filter((theme) => isRemoteRasterMapTheme(theme.code));
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
      return this.mediaNavList[i - 1].id ?? null;
    },
    mediaNextId() {
      const i = this.mediaCurrentIndex;
      if (i < 0 || i >= this.mediaNavList.length - 1) return null;
      return this.mediaNavList[i + 1].id ?? null;
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
      if (!this.geoDrawingParamDef) return false;
      const t = this.geoDrawingParamDef.type;
      if (t === 'GEO_POLYGON') return this.geoDrawPointCount > 0;
      if (t === 'GEO_CIRCLE' || t === 'GEO_RECTANGLE') return this.geoDrawPointCount > 0;
      return this.geoDrawingOverlay?.canUndo?.() ?? false;
    },
    geoDrawCanFinish() {
      if (this.geoDrawingParamDef?.type === 'GEO_POLYGON') return this.geoDrawPointCount >= 3;
      return this.geoDrawingOverlay?.canFinish?.() ?? false;
    },
    showDataFreshnessBanner() {
      return freshnessStore.shouldShowBanner(this.initialLoadDone, this.shouldAutoFreshenAfterLogin());
    },
  };

  const methodDefinitions: MapControllerMethods = {
    ...useMapTools(),
    ...useMapLayerSettings({ filterStore, mapSettingsStore }),
    ...useTerrainMode({ mapSettingsStore }),
    ...useTrackLayers(),
    ...useTrackPointLayer(),
    ...useTrackReplay(),
    ...useGeoDrawing(),
    ...useMediaAndHeatmap({ mapSettingsStore }),
    ...useMapDataLoading({ filterStore, freshnessStore }),
    ...useMapRendererLifecycle(),
  };

  const computedRefs = {} as Partial<MapControllerComputedRefs>;
  const computedRecord = computedRefs as Record<string, ComputedRef<unknown>>;
  const methodRecord = methodDefinitions as unknown as Record<string, ControllerMethod>;
  const setupRecord = setupBindings as Record<string, unknown>;
  const stateRecord = state as Record<string, unknown>;
  const propsRecord = props as Record<string, unknown>;
  const boundMethodCache = new Map<string, BoundControllerMethod>();

  const ctx = new Proxy({} as MapControllerRuntime, {
    get(_target, key) {
      if (key === '$refs') return refsProxy;
      if (key === '$emit') return emit;
      if (key === '$toast') return toast;
      if (key === '$nextTick') return nextTick;
      if (typeof key !== 'string') return undefined;
      if (Object.prototype.hasOwnProperty.call(methodDefinitions, key)) {
        if (!boundMethodCache.has(key)) {
          boundMethodCache.set(key, methodRecord[key].bind(ctx));
        }
        return boundMethodCache.get(key);
      }
      if (Object.prototype.hasOwnProperty.call(computedRecord, key)) return computedRecord[key].value;
      if (Object.prototype.hasOwnProperty.call(setupBindings, key)) {
        const value = setupRecord[key];
        return isRef(value) ? value.value : value;
      }
      if (Object.prototype.hasOwnProperty.call(state, key)) return stateRecord[key];
      if (Object.prototype.hasOwnProperty.call(props, key)) return propsRecord[key];
      return undefined;
    },
    set(_target, key, value) {
      if (typeof key !== 'string') return false;
      stateRecord[key] = value;
      return true;
    },
  });

  for (const [name, getter] of Object.entries(computedDefinitions)) {
    computedRecord[name] = computed(() => getter.call(ctx));
  }

  function selectedTrackMetadataFromState() {
    const trackId = Number(state.selectedTrackId);
    if (!Number.isFinite(trackId)) return null;
    const featureProperties =
      state.selectedFeature?.properties ?? state.gpsTrackIdToFeature?.get?.(trackId)?.properties;
    const track = state.gpsTracksById?.get?.(trackId);
    if (!featureProperties && !track && mapStateStore.selectedTrackId === trackId) {
      return mapStateStore.selectedTrackMetadata;
    }
    return {
      id: trackId,
      name: featureProperties?.trackName || track?.trackName || featureProperties?.trackDescription || '',
      description: featureProperties?.trackDescription || track?.trackDescription || '',
      activityType: featureProperties?.activityType || track?.activityType || '',
    };
  }

  watch(
    () => [state.selectedTrackId, state.selectedFeature, state.gpsTracksById],
    () => {
      const metadata = selectedTrackMetadataFromState();
      if (!metadata) {
        mapStateStore.clearSelectedTrack();
        return;
      }
      mapStateStore.setSelectedTrack(metadata.id, metadata);
    },
    { immediate: true }
  );

  watch(
    () => state.activeToolId,
    (toolId) => mapStateStore.setActiveTool(toolId),
    { immediate: true }
  );

  watch(
    () => [
      state.locationSearchVisible,
      state.trackSelectionSheetVisible,
      state.trackDetailsVisible,
      state.mediaSheetVisible,
    ],
    ([locationSearchVisible, trackSelectionVisible, trackDetailsVisible, mediaVisible]) => {
      mapStateStore.setSheetState({
        locationSearchVisible,
        trackSelectionVisible,
        trackDetailsVisible,
        mediaVisible,
      });
    },
    { immediate: true }
  );

  watch(
    () =>
      [
        state.trackReplayActive,
        state.trackReplayLoading,
        state.trackReplayPlaying,
        state.trackReplayProgress,
        state.trackReplayDurationSeconds,
        state.trackReplayCameraPreset,
        state.trackReplayCameraSmoothness,
        state.trackReplayShowContextTracks,
        state.trackReplayShowTelemetry,
        state.trackReplayAutoFollow,
        state.trackReplayTrackId,
        state.trackReplayTrackLabel,
        state.trackReplayDistanceLabel,
        state.trackReplayElapsedLabel,
        state.trackReplayTotalLabel,
        state.trackReplaySpeedFactorLabel,
      ] as const,
    ([
      active,
      loading,
      playing,
      progress,
      durationSeconds,
      cameraPreset,
      cameraSmoothness,
      showContextTracks,
      showTelemetry,
      autoFollow,
      currentTrackId,
      trackLabel,
      distanceLabel,
      elapsedLabel,
      totalLabel,
      speedFactorLabel,
    ]) => {
      mapStateStore.patchReplayState({
        active,
        loading,
        playing,
        progress,
        durationSeconds,
        cameraPreset,
        cameraSmoothness,
        showContextTracks,
        showTelemetry,
        autoFollow,
        currentTrackId,
        trackLabel,
        distanceLabel,
        elapsedLabel,
        totalLabel,
        speedFactorLabel,
      });
    },
    { immediate: true }
  );

  async function mounted(this: MapControllerRuntime) {
    startupLog('map', 'Map component mounted');
    mapSettingsStore.hydrate();
    this.syncMapSettingsFromStore();
    window.addEventListener(MAP_ARCHIVE_STALE_EVENT, this.handleMapArchiveStale);
    try {
      await this.reloadMap(true);
      const selectedTrackId = mapStateStore.selectedTrackId;
      if (selectedTrackId != null && this.gpsTrackIdToFeature?.has?.(selectedTrackId)) {
        this.selectedTrackId = null;
        this.selectTrackById(selectedTrackId);
      }
      startupLog('map', 'Initial map reload completed');
    } catch (error) {
      startupError('map', 'Initial map reload failed', describeError(error));
      throw error;
    }
    this._onOnline = markRaw(() => this.onBrowserOnline());
    window.addEventListener('online', this._onOnline);
  }

  function beforeUnmount(this: MapControllerRuntime) {
    if (mapStateStore.mapMode !== '3d') {
      this.stop3dTrackReplay({ restore: false });
    }
    this.stopMapStatusPolling();
    if (this.retryTimeoutId) clearTimeout(this.retryTimeoutId);
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
      this._terrainControl = null;
      this._terrainTrackLayer = null;
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

  const boundMethods = {} as MapControllerBoundMethods;
  const boundMethodRecord = boundMethods as Record<string, BoundControllerMethod>;
  const ctxMethodRecord = ctx as unknown as Record<string, BoundControllerMethod>;
  for (const name of Object.keys(methodDefinitions) as Array<keyof MapControllerMethods>) {
    boundMethodRecord[name] = (...args: unknown[]) => ctxMethodRecord[name](...args);
  }

  const stateRefs = toRefs(stateRecord) as Record<string, unknown>;
  return {
    ...stateRefs,
    ...setupBindings,
    ...computedRefs,
    ...boundMethods,
  } as unknown as MainMapControllerReturn;
}
