// eslint-disable-next-line @typescript-eslint/ban-ts-comment
// @ts-nocheck
/* eslint-disable @typescript-eslint/no-explicit-any -- Renderer setup still crosses broad MapLibre/runtime config shapes. */
import { reportMapError, reportMapContextLoss } from '@/utils/mapDiagnostics';
import { warnSanitized } from '@/utils/safeLogging';
import { markRaw, watch } from 'vue';
import * as maplibregl from 'maplibre-gl';
import { getToken, redirectToLoginAfterAuthFailure } from '@/utils/auth';
import { MediaOverlay } from '@/layers/MediaOverlay';
import { HeatmapOverlay } from '@/layers/HeatmapOverlay';
import {
  fetchMapConfig,
  clearMapConfigCache,
  mainTileArchiveUrl,
  lowzoomTileArchiveUrl,
  MapConfigDtoTileModeEnum,
} from '@/utils/mapConfigService';
import {
  fetchMapStatus,
  invalidateMapStatus,
  shouldPollMapStatus,
  shouldTryVectorTiles,
} from '@/utils/mapStatusService';
import {
  APPLICATION_STATUS_SECTION,
  type ApplicationStatusPollingConsumer,
  useApplicationStatusStore,
} from '@/stores/applicationStatusStore';
import { STATUS_POLL_INTERVAL_MS, StatusRequestPausedError } from '@/utils/statusPolling';
import {
  buildLocalVectorStyleFromArchiveUrl,
  buildFallbackRasterStyle,
  TERRAIN_DEM_SOURCE_ID,
  HILLSHADE_DEM_SOURCE_ID,
} from '@/utils/mapStyle';
import { GlobeControl, computeGlobeMinZoom } from '@/components/map/GlobeControl';
import { TerrainViewControl } from '@/components/map/TerrainViewControl';
import { collectStyleAttributions, resolveConfiguredMapStyle } from '@/components/map/mapStyleResolver';
import { ensureLowZoomCached, loadLowZoomFromCache } from '@/utils/lowZoomCacheService';
import { scheduleLowZoomCacheWarmup } from '@/utils/lowZoomCacheWarmup';
import { startStartupTimer, startupLog, startupWarn } from '@/utils/startupDiagnostics';
import { ensurePMTilesProtocol, registerCachingPMTilesArchive } from '@/utils/maplibrePmtilesProtocol';
import { configureExternalAttributionLinks } from '@/utils/externalAttributionLinks';
import { mapScaleUnitForMeasurementSystem } from '@/components/map/mapScaleControl';
import { getMeasurementSystem } from '@/composables/useMeasurementSystem';
import type { MapControllerMethodDefinitions, MapRendererLifecycleMethods } from './mapControllerRuntime';
import { useMapStateStore } from '@/stores/mapStateStore';
import type { useMapSettingsStore } from '@/stores/mapSettingsStore';
import { isAbortLikeError } from '@/utils/errors';
import { installMissingStyleImageResolver } from '@/utils/maplibreStyleImages';
import { NAVIGATION_TOOL_ICONS } from '@/components/ui/navigationToolIcons';
import { rebuildMapLayers } from './useMapLayerSettings';
import { VectorBasemapRecovery } from '@/utils/vectorBasemapRecovery';
import { observeMapCamera } from '@/components/map/mapCameraObservation';
import { observeElementResize } from '@/utils/observeElementResize';

const GLOBE_ENTER_ZOOM = 3;
const GLOBE_EXIT_ZOOM = 3.8;
const MERCATOR_MIN_ZOOM = 1.0;
const MAP_PAN_DECELERATION = 1700;
const MAP_NAVIGATION_CONTROL_OPTIONS = Object.freeze({ showCompass: true, showZoom: true, visualizePitch: true });
const MAP_LOAD_WATCHDOG_MS = 7000;
const DEFAULT_MAP_ZOOM = 10;
const INITIAL_TRACK_BOUNDS_PADDING = 48;
const INITIAL_TRACK_BOUNDS_MAX_ZOOM = 13;
const LOCAL_VECTOR_STYLE_MODE = 'local-vector';
const MAPTERHORN_ATTRIBUTION_PATTERN = /mapterhorn/i;

function warnExternalAttributionBlocked(toast: unknown, url: string) {
  toast?.add?.({
    severity: 'warn',
    summary: 'External link blocked',
    detail: `Open this link in your browser: ${url}`,
    life: 7000,
  });
}

function initialBoundsFromConfig(bounds: any) {
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

function centerFromBounds(bounds: any) {
  return [(bounds[0][0] + bounds[1][0]) / 2, (bounds[0][1] + bounds[1][1]) / 2];
}

export function useMapRendererLifecycle(deps: {
  mapSettingsStore: ReturnType<typeof useMapSettingsStore>;
}): MapControllerMethodDefinitions<MapRendererLifecycleMethods> {
  const { mapSettingsStore } = deps;
  const applicationStatusStore = useApplicationStatusStore();
  let mapStatusPollingActive = false;
  let mapStatusPollNow: ((force?: boolean) => void) | null = null;
  let mapStatusConsumer: ApplicationStatusPollingConsumer | null = null;
  let stopMapStatusWatch: (() => void) | null = null;
  let cancelLowZoomWarmup: (() => void) | null = null;
  let vectorRecovery: VectorBasemapRecovery | null = null;
  let initializationGeneration = 0;
  let cancelMapLoad: (() => void) | null = null;

  function ensureVectorRecovery(runtime) {
    if (vectorRecovery) return vectorRecovery;
    const map = runtime.overlayMap;
    const replaceBasemap = async (fallback: boolean) => {
      runtime.baseMapRuntimeFallbackApplied = fallback;
      const resolved = resolveConfiguredMapStyle({
        config: runtime.mapConfig,
        theme: runtime.mapThemeSelected,
        mapSourceMode: runtime.mapSourceMode,
        localTilesReady: !fallback,
      });
      runtime.baseMapStyleMode = resolved.styleMode;
      if (map.getSource('tracks')) {
        await rebuildMapLayers(runtime);
      } else {
        map.once('style.load', () => {
          if (runtime.overlayMap !== map) return;
          runtime.captureBasemapLayers();
          runtime.applyBasemapAppearance();
          runtime.applyTerrainPreference({ animate: false });
        });
        map.setStyle(resolved.style);
        runtime.setOverlayAttributionControl(resolved.attributions);
      }
    };
    vectorRecovery = new VectorBasemapRecovery({
      map,
      archiveUrl: mainTileArchiveUrl(runtime.mapConfig),
      isCurrent: () => runtime.overlayMap === map && !runtime.mapArchiveStaleReloading,
      fallbackApplied: runtime.baseMapRuntimeFallbackApplied,
      onUnavailable: async () => {
        warnSanitized(
          '[MTL] Vector archive unavailable after repeated checks; using temporary raster basemap.',
          undefined,
          { topic1: 'map', topic2: 'useMapRendererLifecycle', topic3: 'onUnavailable' }
        );
        await replaceBasemap(true);
      },
      onRestored: async () => {
        startupLog('mapload', 'Vector archive recovered; restoring selected basemap');
        await replaceBasemap(false);
      },
      onAuthFailure: () => redirectToLoginAfterAuthFailure(!!getToken()),
    });
    return vectorRecovery;
  }
  const methods: MapControllerMethodDefinitions<MapRendererLifecycleMethods> = {
    startMapStatusPolling() {
      this.stopMapStatusPolling();
      mapStatusPollingActive = true;
      // eslint-disable-next-line @typescript-eslint/no-this-alias -- The watcher outlives this method call.
      const runtime = this;
      mapStatusPollNow = (force = false) => {
        if (!mapStatusPollingActive) return;
        if (force) void applicationStatusStore.force(APPLICATION_STATUS_SECTION.SERVICES);
        else void applicationStatusStore.refresh([APPLICATION_STATUS_SECTION.SERVICES]);
      };
      mapStatusConsumer = applicationStatusStore.registerPollingConsumer({
        [APPLICATION_STATUS_SECTION.SERVICES]: STATUS_POLL_INTERVAL_MS,
      });
      stopMapStatusWatch = watch(
        () => applicationStatusStore.services?.map,
        (status) => {
          if (!mapStatusPollingActive || !status) return;
          const previousStatus = runtime.mapServerStatus;
          const wasReady = previousStatus?.ready;
          const previousSource = previousStatus?.tileSource ?? runtime.mapConfig?.tileSource;
          const previousArchive = previousStatus?.archiveId ?? runtime.mapConfig?.archiveId;
          runtime.mapServerStatus = status;
          const currentSource = status.tileSource;
          const currentArchive = status.archiveId;
          const archiveChanged =
            Boolean(previousStatus) && (previousSource !== currentSource || previousArchive !== currentArchive);
          if (!status.ready) return;
          if (
            !shouldPollMapStatus({
              tileMode: runtime.mapConfig?.tileMode,
              offline: runtime.mapConfig?.offline,
              remoteRasterOverride: runtime.mapSourceMode === 'remote',
              status,
            })
          ) {
            runtime.stopMapStatusPolling();
          }
          if (archiveChanged || (!wasReady && runtime.baseMapStyleMode !== LOCAL_VECTOR_STYLE_MODE)) {
            runtime.baseMapRuntimeFallbackApplied = false;
            clearMapConfigCache();
            runtime.reloadMap();
          }
        }
      );
    },

    refreshMapStatusPolling(force = false) {
      mapStatusPollNow?.(force);
    },

    stopMapStatusPolling() {
      mapStatusPollingActive = false;
      this.mapStatusPollTimer = null;
      mapStatusConsumer?.dispose();
      mapStatusConsumer = null;
      stopMapStatusWatch?.();
      stopMapStatusWatch = null;
      mapStatusPollNow = null;
    },

    disposeRendererMaps(preserveInitialization = false) {
      this.disposeMapInteractions();
      if (!preserveInitialization) {
        initializationGeneration += 1;
        this._mapLoadGeneration = (this._mapLoadGeneration ?? 0) + 1;
        this._mapReadyResolve?.(false);
      }
      cancelMapLoad?.();
      cancelMapLoad = null;
      this._stopObservingResize?.();
      this._stopObservingResize = null;
      vectorRecovery?.dispose();
      vectorRecovery = null;
      cancelLowZoomWarmup?.();
      cancelLowZoomWarmup = null;
      // Drawing handlers belong to the map instance being replaced, including
      // theme/source changes that rebuild layers without a full data reload.
      this.geoDrawingOverlay?.destroy();
      this.geoDrawingOverlay = null;
      this.geoDrawingParamDef = null;
      this.geoDrawPointCount = 0;
      this.gpsMarker?.remove();
      this.gpsMarker = null;
      if (this.overlayMap) {
        this.detachTrackPointLayerHandlers();
        this._attributionLinkCleanup?.();
        this._attributionLinkCleanup = null;
        this.overlayMap.remove();
        this.overlayMap = undefined;
      }
      this._terrainControl = null;
      this._scaleControl = null;
      this._terrainTrackLayer = null;
      this._attributionControl = null;
    },

    observeMapContainerResize() {
      this._stopObservingResize?.();
      const map = this.overlayMap;
      const container = this.$refs.mapContainer;
      this._stopObservingResize = observeElementResize(container, () => {
        if (this.overlayMap !== map) return;
        map.resize();
        if (this.globeMode) map.setMinZoom(computeGlobeMinZoom(container));
        map.redraw();
      });
    },

    handleMapError(e) {
      if (isAbortLikeError(e.error)) return;
      const msg = e.error?.message || '';
      const tileId = e?.tile?.tileID ?? null;
      const isExternalElevationSource = e.sourceId === TERRAIN_DEM_SOURCE_ID || e.sourceId === HILLSHADE_DEM_SOURCE_ID;
      if (!isExternalElevationSource && msg.includes('401')) {
        startupLog('mapload', 'Map tile auth failure; redirecting to login', {
          message: msg,
        });
        warnSanitized('Map tile auth failure (401) — redirecting to login', e.error ?? msg, {
          topic1: 'map',
          topic2: 'useMapRendererLifecycle',
          topic3: 'initMap',
        });
        redirectToLoginAfterAuthFailure(!!getToken());
      } else if (this.applyRuntimeRasterBasemapFallback(e, msg, tileId)) {
        return;
      } else {
        startupLog('mapload', 'MapLibre reported a base-map error', {
          message: msg,
          sourceId: e.sourceId ?? null,
          tile: tileId,
        });
        reportMapError(e, 'Map2DRenderer');
      }
    },

    applyRuntimeRasterBasemapFallback(errorEvent) {
      if (this.baseMapStyleMode !== LOCAL_VECTOR_STYLE_MODE || !this.overlayMap) return false;
      return ensureVectorRecovery(this).handleError(errorEvent ?? {});
    },

    setOverlayAttributionControl(attributions) {
      if (!this.overlayMap) return;
      this._attributionLinkCleanup?.();
      this._attributionLinkCleanup = null;
      if (this._attributionControl && typeof this.overlayMap.removeControl === 'function') {
        try {
          this.overlayMap.removeControl(this._attributionControl);
        } catch {
          // The map may already be tearing down during a reload.
        }
      }
      const visibleAttributions = Array.isArray(attributions)
        ? attributions.filter((attribution) => !MAPTERHORN_ATTRIBUTION_PATTERN.test(attribution))
        : [];
      const customAttribution = visibleAttributions.length > 0 ? visibleAttributions : undefined;
      this._attributionControl = markRaw(
        new maplibregl.AttributionControl({
          compact: false,
          customAttribution,
        })
      );
      this.overlayMap.addControl(this._attributionControl, 'bottom-right');
      const attributionLinkContainer =
        typeof this.overlayMap.getContainer === 'function' ? this.overlayMap.getContainer() : this.$refs.mapContainer;
      this._attributionLinkCleanup = markRaw(
        configureExternalAttributionLinks(attributionLinkContainer, {
          onBlocked: (url) => warnExternalAttributionBlocked(this.$toast, url),
        })
      );
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
        startupLog('mapcache', 'PMTiles archive/source changed; reloading map config', event?.detail ?? {});
        this.baseMapRuntimeFallbackApplied = false;
        this.stopMapStatusPolling();
        this.mapServerStatus = null;
        invalidateMapStatus();
        clearMapConfigCache();
        await this.reloadMap();
      } catch (error) {
        startupWarn('mapcache', 'Map reload after archive/source change failed', error);
      } finally {
        this.mapArchiveStaleReloading = false;
      }
    },

    async reloadMap() {
      if (this._mapUnmounted) return;
      initializationGeneration += 1;
      cancelMapLoad?.();
      this._mapReadyResolve?.(false);
      const generation = (this._mapLoadGeneration ?? 0) + 1;
      this._mapLoadGeneration = generation;
      const isCurrent = () => !this._mapUnmounted && this._mapLoadGeneration === generation;
      const reloadTimer = startStartupTimer('reload', 'Reloading map state');
      this.showLoader = true;

      // Reset state
      this.selectedTrackId = null;
      this.selectedFeature = null;
      this.closeSelectionPopup();
      this.closeTrackPointPopup();
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

      this.clearFocusedMediaMarker();
      this.clearLocationSearchMarker();
      let resolveReady: (ready: boolean) => void = () => undefined;
      let readyPromise: Promise<boolean> | null = null;
      try {
        // Phase 3: Start track fetch in parallel with map tile loading.
        // initMap() creates MapLibre instances and waits for their 'load' events.
        // fetchTracksAndFallback() hits cache/network for track data.
        // The actual addTracksToMap() (inside loadMapData) needs the map to be loaded,
        // so loadMapData awaits _mapReadyPromise before touching map sources.
        this._mapReadyPromise = new Promise((resolve) => {
          resolveReady = resolve;
        });
        this._mapReadyResolve = resolveReady;
        readyPromise = this._mapReadyPromise;
        const config = await fetchMapConfig();
        if (!isCurrent()) return;
        this.mapConfig = config;
        // Observe rejection immediately while initialization runs in parallel.
        const tracks = this.fetchTracksAndFallback().then(
          () => ({ error: undefined }),
          (error) => ({ error })
        );
        const ready = await this.initMap(true);
        resolveReady(ready !== false && isCurrent());
        // Map is ready — now wait for track data (may already be resolved from cache)
        const result = await tracks;
        if (!isCurrent() || ready === false) return;
        if (result.error) throw result.error;
        reloadTimer.success('Map reload completed');
      } catch (error) {
        if (!isCurrent()) return;
        reloadTimer.error('Map reload failed', error);
        throw error;
      } finally {
        resolveReady(false);
        if (this._mapReadyPromise === readyPromise) {
          this._mapReadyPromise = null;
          this._mapReadyResolve = null;
        }
      }
    },

    async initMap(preserveTrackLoad = false) {
      const container = this.$refs.mapContainer;
      if (this._mapUnmounted || !(container instanceof HTMLElement)) return false;
      if (!preserveTrackLoad) {
        this._mapLoadGeneration = (this._mapLoadGeneration ?? 0) + 1;
        this._mapReadyResolve?.(false);
      }
      const generation = ++initializationGeneration;
      cancelMapLoad?.();
      const isCurrent = () =>
        generation === initializationGeneration && !this._mapUnmounted && this.$refs.mapContainer === container;
      const initTimer = startStartupTimer('mapinit', 'Initializing map');
      // Fetch map config from server (cached after first call)
      const config = await fetchMapConfig();
      if (!isCurrent()) return false;
      this.mapConfig = config;
      if (this.mapConfig.plannerEnabled && !this.toolDefs.some((t) => t.id === 'planner')) {
        const adminIdx = this.toolDefs.findIndex((t) => t.id === 'admin');
        const insertAt = adminIdx >= 0 ? adminIdx : this.toolDefs.length;
        this.toolDefs.splice(insertAt, 0, {
          id: 'planner',
          icon: NAVIGATION_TOOL_ICONS.planner,
          label: 'Planner',
        });
      }
      startupLog('mapinit', 'Map config resolved', {
        tileMode: this.mapConfig.tileMode,
        mapSourceMode: this.mapSourceMode,
        offline: this.mapConfig.offline ?? false,
        tileBaseUrl: this.mapConfig.tileBaseUrl,
        tileArchiveUrl: this.mapConfig.tileArchiveUrl,
        tileSource: this.mapConfig.tileSource,
        archiveId: this.mapConfig.archiveId,
      });

      // Preserve current viewport so theme switches don't jump the map position.
      // On first load, start from server-provided bounds and fit them after map load.
      const hadOverlayMap = !!this.overlayMap;
      const mapStateStore = useMapStateStore();
      const returnViewportCamera = hadOverlayMap ? null : mapStateStore.returnViewportCamera;
      const gpsFollowCenter =
        this.gpsDeviceEnabledDisabled && this.gpsFollowing && this.gpsLocation
          ? [this.gpsLocation[1], this.gpsLocation[0]]
          : null;
      const initialBounds =
        hadOverlayMap || returnViewportCamera || gpsFollowCenter
          ? null
          : initialBoundsFromConfig(this.mapConfig.initialBounds);
      let initialCenter =
        returnViewportCamera?.center ?? (initialBounds ? centerFromBounds(initialBounds) : this.mapCenter);
      let initialZoom = DEFAULT_MAP_ZOOM;
      let initialBearing = returnViewportCamera?.bearing ?? 0;
      let initialPitch = returnViewportCamera?.pitch ?? 0;
      if (hadOverlayMap) {
        initialCenter = [this.overlayMap.getCenter().lng, this.overlayMap.getCenter().lat];
        initialZoom = this.overlayMap.getZoom();
        initialBearing = this.overlayMap.getBearing();
        initialPitch = this.overlayMap.getPitch();
      } else if (returnViewportCamera) {
        initialZoom = returnViewportCamera.zoom;
      }
      if (gpsFollowCenter) initialCenter = gpsFollowCenter;

      // Tear down previous maps
      this.disposeRendererMaps(true);
      ensurePMTilesProtocol();
      // Pre-register PMTiles instances with force-cache fetch so Chrome serves
      // cached 206 responses from disk instead of revalidating every range request.
      if (this.mapConfig.tileBaseUrl && this.mapConfig.tilesetName) {
        const tileUrl = mainTileArchiveUrl(this.mapConfig);
        if (this.mapSourceMode !== 'remote') {
          registerCachingPMTilesArchive(tileUrl);
        }
      }
      startupLog('mapinit', 'PMTiles protocol ready');
      const remoteRasterOverride = this.mapSourceMode === 'remote';
      if (
        this.mapConfig.offline ||
        remoteRasterOverride ||
        this.mapConfig.tileMode !== MapConfigDtoTileModeEnum.Local
      ) {
        this.stopMapStatusPolling();
        this.mapServerStatus = null;
      }

      // When offline, skip the map-status probe and planet-file check entirely.
      // When local mode online, check once whether the planet file is already ready.
      if (
        !this.mapConfig.offline &&
        !remoteRasterOverride &&
        this.mapConfig.tileMode === MapConfigDtoTileModeEnum.Local &&
        !this.mapServerStatus
      ) {
        const statusTimer = startStartupTimer('mapstatus', 'Probing map server status');
        try {
          const status = await fetchMapStatus();
          if (!isCurrent()) return false;
          this.mapServerStatus = status;
          statusTimer.success('Map server status received', {
            phase: status.phase,
            ready: status.ready,
            tileSource: status.tileSource,
            archiveId: status.archiveId,
          });
        } catch (error) {
          if (!isCurrent()) return false;
          if (!(error instanceof StatusRequestPausedError)) {
            statusTimer.warn('Map server status probe failed', error);
            // A failed status request does not prove an archive outage.
            this.mapServerStatus = { phase: 'unreachable', ready: false };
          }
        }
      }
      if (!isCurrent()) return false;

      // Build style based on config
      // If offline, use a lightweight raster fallback so the map 'load' event fires reliably.
      // If local but planet file not yet ready, use OSM raster temporarily.
      let style;
      let styleMode = 'unknown';
      let styleAttributions = [];
      if (this.mapConfig.offline) {
        // Try to use cached low-zoom PMTiles for a proper vector map background
        const lowzoomUrl = lowzoomTileArchiveUrl(this.mapConfig);
        const lowzoom = await loadLowZoomFromCache(lowzoomUrl);
        if (lowzoom) {
          ensurePMTilesProtocol().add(lowzoom);
          style = buildLocalVectorStyleFromArchiveUrl(lowzoomUrl, this.mapThemeSelected, undefined, {
            hillshade: false,
          });
          styleMode = 'offline-lowzoom-vector';
          styleAttributions = collectStyleAttributions(style);
          console.log('Offline: using cached low-zoom vector tiles as base map');
        } else {
          style = buildFallbackRasterStyle();
          styleMode = 'offline-raster-fallback';
          styleAttributions = collectStyleAttributions(style);
          console.log('Offline: no cached low-zoom tiles, using OSM raster fallback');
        }
      } else {
        const resolved = resolveConfiguredMapStyle({
          config: this.mapConfig,
          theme: this.mapThemeSelected,
          mapSourceMode: this.mapSourceMode,
          localTilesReady:
            this.mapConfig.tileMode === MapConfigDtoTileModeEnum.Local
              ? shouldTryVectorTiles(this.mapServerStatus) && !this.baseMapRuntimeFallbackApplied
              : true,
        });
        style = resolved.style;
        styleMode = resolved.styleMode;
        styleAttributions = resolved.attributions;
      }
      startupLog('mapinit', 'Selected base-map style', {
        styleMode,
        tileMode: this.mapConfig.tileMode,
        mapSourceMode: this.mapSourceMode,
        offline: this.mapConfig.offline ?? false,
        mapServerReady: this.mapServerStatus?.ready ?? null,
        tileSource: this.mapServerStatus?.tileSource ?? this.mapConfig.tileSource ?? null,
        archiveId: this.mapServerStatus?.archiveId ?? this.mapConfig.archiveId ?? null,
      });
      this.baseMapStyleMode = styleMode;

      // Basemap, tracks, media, and interactions share one MapLibre render cycle.
      startupLog('mapload', 'Creating map instance', {
        styleMode,
      });
      this.overlayMap = markRaw(
        new maplibregl.Map({
          container,
          style,
          center: initialCenter,
          zoom: initialZoom,
          bearing: initialBearing,
          pitch: initialPitch,
          minZoom: MERCATOR_MIN_ZOOM,
          attributionControl: false,
          dragPan: { deceleration: MAP_PAN_DECELERATION },
          dragRotate: true,
          pitchWithRotate: true,
          touchPitch: true,
          // This lifecycle schedules container resizing outside ResizeObserver delivery.
          trackResize: false,
        })
      );
      observeMapCamera(this.overlayMap);
      // The GPS marker is independent of tile readiness, including slow remote maps.
      this.updateGpsMarker();
      if (styleMode === LOCAL_VECTOR_STYLE_MODE || this.baseMapRuntimeFallbackApplied) {
        ensureVectorRecovery(this);
      }
      installMissingStyleImageResolver(this.overlayMap);
      this.overlayMap.once('load', () => {
        startupLog('mapload', 'Map load event received', {
          styleMode,
        });
      });

      // Detect authentication failures from PMTiles / tile fetches and redirect to login.
      // PMTiles 401 errors bypass the axios interceptor, so this is the only recovery path.
      // All other map errors (blocked CDNs, CORS, DNS failures) are also logged for diagnostics.
      const initializedMap = this.overlayMap;
      initializedMap.on('error', (e) => {
        if (this.overlayMap !== initializedMap) return;
        this.handleMapError(e);
      });
      initializedMap.on('webglcontextlost', () => {
        if (this.overlayMap === initializedMap) reportMapContextLoss('Map2DRenderer');
      });

      // Controls and all application layers live on the same map.
      this.overlayMap.addControl(new maplibregl.NavigationControl(MAP_NAVIGATION_CONTROL_OPTIONS), 'top-left');
      this._globeControl = markRaw(new GlobeControl(() => this.toggleGlobeMode()));
      this.overlayMap.addControl(this._globeControl, 'top-left');
      this._terrainControl = markRaw(new TerrainViewControl(() => this.onToggleTerrainMode()));
      this.overlayMap.addControl(this._terrainControl, 'top-left');
      this._terrainControl.setActive(this.terrainEnabled);
      this._scaleControl = markRaw(
        new maplibregl.ScaleControl({
          maxWidth: 100,
          unit: mapScaleUnitForMeasurementSystem(getMeasurementSystem()),
        })
      );
      this.overlayMap.addControl(this._scaleControl, 'bottom-left');
      this.setOverlayAttributionControl(styleAttributions);

      // Initialize media overlay.
      this.mediaOverlay = markRaw(
        new MediaOverlay(
          this.overlayMap,
          () => undefined,
          (points) => {
            this.mediaLoadedPoints = points;
          },
          () => !this.measureToolActive && !this.plannerToolActive && !this.geoDrawingParamDef
        )
      );

      // Initialize heatmap overlay
      this.heatmapOverlay = markRaw(new HeatmapOverlay(this.overlayMap));

      this.observeMapContainerResize();

      const waitForMapLoad = (mapInstance) =>
        new Promise((resolve) => {
          if (mapInstance.loaded()) {
            startupLog('mapload', 'Map already loaded');
            resolve(true);
            return;
          }
          const finish = (ready: boolean) => {
            mapInstance.off?.('load', onLoad);
            mapInstance.off?.('remove', onRemove);
            if (cancelMapLoad === onRemove) cancelMapLoad = null;
            resolve(ready);
          };
          const onLoad = () => finish(true);
          const onRemove = () => finish(false);
          cancelMapLoad = onRemove;
          mapInstance.once('load', onLoad);
          mapInstance.once('remove', onRemove);
        });
      const mapLoadWatchdog = window.setTimeout(() => {
        startupWarn('mapload', 'Map load watchdog exceeded', undefined, {
          styleMode,
          mapLoaded: this.overlayMap?.loaded() ?? false,
          tileMode: this.mapConfig?.tileMode,
          offline: this.mapConfig?.offline ?? false,
          mapServerReady: this.mapServerStatus?.ready ?? null,
        });
      }, MAP_LOAD_WATCHDOG_MS);
      try {
        const loaded = await waitForMapLoad(initializedMap);
        if (!loaded || !isCurrent() || this.overlayMap !== initializedMap) return false;
      } finally {
        clearTimeout(mapLoadWatchdog);
      }
      startupLog('mapload', 'Map finished loading', {
        styleMode,
      });
      this.captureBasemapLayers();
      this.applyBasemapAppearance();
      this.applyTerrainPreference({
        animate: false,
      });
      if (initialBounds) {
        const fitOptions = {
          padding: INITIAL_TRACK_BOUNDS_PADDING,
          maxZoom: INITIAL_TRACK_BOUNDS_MAX_ZOOM,
          duration: 0,
        };
        this.overlayMap.fitBounds(initialBounds, fitOptions);
        startupLog('mapinit', 'Fitted initial viewport to stored track bounds', {
          minLng: initialBounds[0][0],
          minLat: initialBounds[0][1],
          maxLng: initialBounds[1][0],
          maxLat: initialBounds[1][1],
        });
      }
      if (this.gpsDeviceEnabledDisabled && this.gpsFollowing && this.gpsLocation) {
        this.overlayMap.jumpTo({ center: [this.gpsLocation[1], this.gpsLocation[0]] });
      }
      await this.restoreMediaLayerPreference(mapSettingsStore.mediaVisible);
      if (!isCurrent() || this.overlayMap !== initializedMap) return false;
      initTimer.success('Map initialization completed', {
        styleMode,
      });

      // Break GPS follow mode when the user manually pans the map
      this.overlayMap.on('dragstart', () => {
        if (this.gpsFollowing) this.gpsFollowing = false;
      });

      // Apply any active Swiss Mobility overlays.
      this.applyActiveOverlays();

      // Apply initial globe projection based on current zoom
      this.zoom = this.overlayMap.getZoom();
      this.updateGlobeState();

      this.installMapInteractions();
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
        if (remoteRasterOverride) {
          startupLog('mapstatus', 'Skipping local map status polling because remote raster source is selected');
        } else {
          if (this.mapServerStatus?.ready) {
            const map = this.overlayMap;
            const archiveUrl = lowzoomTileArchiveUrl(this.mapConfig);
            cancelLowZoomWarmup = scheduleLowZoomCacheWarmup(
              map,
              () => {
                startupLog('mapcache', 'Starting deferred low-zoom cache warmup');
                void ensureLowZoomCached(archiveUrl).catch((e) => {
                  startupLog('mapcache', 'Low-zoom cache warmup failed', e);
                  warnSanitized('Low-zoom cache failed:', e, {
                    topic1: 'map',
                    topic2: 'useMapRendererLifecycle',
                    topic3: 'initMap',
                  });
                });
              },
              () => this.overlayMap === map && !this.mapConfig.offline && this.mapSourceMode !== 'remote',
              () => this.initialLoadDone && !this.loadingTrackBatches
            );
          }
          if (
            shouldPollMapStatus({
              tileMode: this.mapConfig.tileMode,
              offline: this.mapConfig.offline,
              remoteRasterOverride,
              status: this.mapServerStatus,
            })
          ) {
            startupLog(
              'mapstatus',
              this.mapServerStatus?.ready
                ? 'Hosted map service active; polling for local sidecar availability'
                : 'Starting map-status polling until local tiles are ready'
            );
            this.startMapStatusPolling();
          }
        }
      }
      return true;
    },

    /** Update globe projection state based on current zoom level (hysteresis). */
    updateGlobeState() {
      if (this.globeMode && this.zoom > GLOBE_EXIT_ZOOM) {
        // Zoomed in past the exit threshold — leave globe and reset user-override
        this.globeMode = false;
        this.globeUserDisabled = false;
        this.applyGlobeProjection();
      } else if (!this.terrainEnabled && !this.globeMode && !this.globeUserDisabled && this.zoom < GLOBE_ENTER_ZOOM) {
        // Zoomed out past the enter threshold — auto-activate globe
        this.globeMode = true;
        this.applyGlobeProjection();
      }
      // Show the toggle button across the whole hysteresis band
      const inGlobeZone = this.zoom < GLOBE_EXIT_ZOOM;
      this._globeControl?.setVisible(inGlobeZone);
      this._globeControl?.setActive(this.globeMode);
    },

    /** Set the MapLibre projection and let MapLibre handle the morph. */
    applyGlobeProjection() {
      const proj = this.globeMode
        ? {
            type: 'globe',
          }
        : {
            type: 'mercator',
          };

      // Clamp minZoom: dynamically fitted to viewport for globe, fixed floor for mercator
      const minZoom = this.globeMode ? computeGlobeMinZoom(this.$refs.mapContainer) : MERCATOR_MIN_ZOOM;
      this.overlayMap?.setMinZoom(minZoom);

      this.overlayMap?.setProjection(proj);
    },

    /** Toggle globe mode on/off (user action via the control button). */
    toggleGlobeMode() {
      if (!this.globeMode && this.terrainEnabled) {
        this.setTerrainModeEnabled(false, {
          animate: false,
        });
      }
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
  };
  return methods;
}
