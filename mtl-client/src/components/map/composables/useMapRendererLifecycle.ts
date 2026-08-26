// eslint-disable-next-line @typescript-eslint/ban-ts-comment
// @ts-nocheck
/* eslint-disable @typescript-eslint/no-explicit-any -- Renderer setup still crosses broad MapLibre/runtime config shapes. */
import { markRaw } from 'vue';
import * as maplibregl from 'maplibre-gl';
import { fetchTrackIdsWithinDistanceOfPoint } from '@/utils/ServiceHelper';
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
  MAP_STATUS_POLL_INTERVAL_MS,
  shouldPollMapStatus,
} from '@/utils/mapStatusService';
import { buildLocalVectorStyleFromArchiveUrl, buildFallbackRasterStyle } from '@/utils/mapStyle';
import { GlobeControl, computeGlobeMinZoom } from '@/components/map/GlobeControl';
import { TerrainViewControl } from '@/components/map/TerrainViewControl';
import { haversineDistance } from '@/components/map/mapGeometry';
import { collectStyleAttributions, resolveConfiguredMapStyle } from '@/components/map/mapStyleResolver';
import { ensureLowZoomCached, loadLowZoomFromCache } from '@/utils/lowZoomCacheService';
import { describeError, startStartupTimer, startupLog, startupWarn } from '@/utils/startupDiagnostics';
import { ensurePMTilesProtocol, registerCachingPMTilesArchive } from '@/utils/maplibrePmtilesProtocol';
import { configureExternalAttributionLinks } from '@/utils/externalAttributionLinks';
import { mapScaleUnitForMeasurementSystem } from '@/components/map/mapScaleControl';
import { getMeasurementSystem } from '@/composables/useMeasurementSystem';
import type { MapControllerMethodDefinitions, MapRendererLifecycleMethods } from './mapControllerRuntime';
import { useMapStateStore } from '@/stores/mapStateStore';
import type { useMapSettingsStore } from '@/stores/mapSettingsStore';
import { isAbortLikeError } from '@/utils/errors';
import { installMissingStyleImageResolver } from '@/utils/maplibreStyleImages';

const GLOBE_ENTER_ZOOM = 3;
const GLOBE_EXIT_ZOOM = 3.8;
const MERCATOR_MIN_ZOOM = 1.0;
const MAP_PAN_DECELERATION = 1700;
const MAP_NAVIGATION_CONTROL_OPTIONS = Object.freeze({ showCompass: true, showZoom: true, visualizePitch: true });
const MAP_LOAD_WATCHDOG_MS = 7000;
const DEFAULT_MAP_ZOOM = 10;
const INITIAL_TRACK_BOUNDS_PADDING = 48;
const INITIAL_TRACK_BOUNDS_MAX_ZOOM = 13;
const TRACK_DETAILS_MAP_DETENT = 'compact';
const LOCAL_VECTOR_STYLE_MODE = 'local-vector';
const LOCAL_VECTOR_SOURCE_ID = 'protomaps';
const MAPTERHORN_ATTRIBUTION_PATTERN = /mapterhorn/i;

function warnExternalAttributionBlocked(toast: unknown, url: string) {
  toast?.add?.({
    severity: 'warn',
    summary: 'External link blocked',
    detail: `Open this link in your browser: ${url}`,
    life: 7000,
  });
}

let runtimeRasterFallbackLoggedThisSession = false;
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
  let mapStatusPollingActive = false;
  let mapStatusPollGeneration = 0;
  let mapStatusPollNow: ((force?: boolean) => void) | null = null;
  let mapStatusVisibilityHandler: (() => void) | null = null;
  const methods: MapControllerMethodDefinitions<MapRendererLifecycleMethods> = {
    startMapStatusPolling() {
      this.stopMapStatusPolling();
      mapStatusPollingActive = true;
      const poll = async (pollGeneration, force = false) => {
        if (!mapStatusPollingActive || pollGeneration !== mapStatusPollGeneration) return;
        try {
          const status = await fetchMapStatus({ force });
          if (!mapStatusPollingActive || pollGeneration !== mapStatusPollGeneration) return;
          const previousStatus = this.mapServerStatus;
          const wasReady = previousStatus?.ready;
          const previousSource = previousStatus?.tileSource;
          const previousArchive = previousStatus?.archiveId;
          this.mapServerStatus = status;
          const currentSource = this.mapServerStatus?.tileSource;
          const currentArchive = this.mapServerStatus?.archiveId;
          const archiveChanged =
            Boolean(previousStatus) && (previousSource !== currentSource || previousArchive !== currentArchive);
          if (this.mapServerStatus?.ready) {
            if (
              !shouldPollMapStatus({
                tileMode: this.mapConfig?.tileMode,
                offline: this.mapConfig?.offline,
                remoteRasterOverride: this.mapSourceMode === 'remote',
                status: this.mapServerStatus,
              })
            ) {
              this.stopMapStatusPolling();
            }
            // Tiles just became ready, or the byte layout/source changed — rebuild
            // with a fresh server-provided PMTiles URL so browser range caches stay isolated.
            if (!wasReady || archiveChanged) {
              this.baseMapRuntimeFallbackApplied = false;
              clearMapConfigCache();
              this.reloadMap();
            }
          }
        } catch {
          // ignore polling errors silently
        } finally {
          if (mapStatusPollingActive && pollGeneration === mapStatusPollGeneration) {
            this.mapStatusPollTimer = setTimeout(() => mapStatusPollNow?.(), MAP_STATUS_POLL_INTERVAL_MS);
          }
        }
      };

      mapStatusPollNow = (force = false) => {
        if (!mapStatusPollingActive || document.visibilityState === 'hidden') return;
        if (this.mapStatusPollTimer !== null) {
          clearTimeout(this.mapStatusPollTimer);
          this.mapStatusPollTimer = null;
        }
        const pollGeneration = ++mapStatusPollGeneration;
        void poll(pollGeneration, force);
      };
      mapStatusVisibilityHandler = () => {
        if (document.visibilityState === 'hidden') {
          mapStatusPollGeneration += 1;
          if (this.mapStatusPollTimer !== null) {
            clearTimeout(this.mapStatusPollTimer);
            this.mapStatusPollTimer = null;
          }
          return;
        }
        mapStatusPollNow?.();
      };
      document.addEventListener('visibilitychange', mapStatusVisibilityHandler);
      mapStatusPollNow();
    },

    refreshMapStatusPolling(force = false) {
      mapStatusPollNow?.(force);
    },

    stopMapStatusPolling() {
      mapStatusPollingActive = false;
      mapStatusPollGeneration += 1;
      if (this.mapStatusPollTimer !== null) {
        clearTimeout(this.mapStatusPollTimer);
        this.mapStatusPollTimer = null;
      }
      if (mapStatusVisibilityHandler) {
        document.removeEventListener('visibilitychange', mapStatusVisibilityHandler);
        mapStatusVisibilityHandler = null;
      }
      mapStatusPollNow = null;
    },

    disposeRendererMaps() {
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

    applyRuntimeRasterBasemapFallback(errorEvent, message, tileId) {
      if (this.baseMapRuntimeFallbackApplied || this.baseMapStyleMode !== LOCAL_VECTOR_STYLE_MODE || !this.overlayMap) {
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
      const hadTrackLayers = Boolean(this.overlayMap.getSource('tracks'));
      if (hadTrackLayers) {
        void this.reloadMap();
      } else {
        this.overlayMap.once('style.load', () => {
          this.captureBasemapLayers();
          this.applyBasemapAppearance();
          this.applyTerrainPreference({ animate: false });
        });
        this.overlayMap.setStyle(resolved.style);
        this.setOverlayAttributionControl(resolved.attributions);
      }
      startupWarn('mapload', 'Local vector basemap failed; switched to raster fallback', {
        message,
        sourceId: errorEvent?.sourceId ?? null,
        tile: tileId,
        fallbackStyleMode: resolved.styleMode,
      });
      if (!runtimeRasterFallbackLoggedThisSession) {
        runtimeRasterFallbackLoggedThisSession = true;
        console.warn('[MTL] Local vector map tiles failed; switched base map to raster fallback for this session.', {
          message,
          sourceId: errorEvent?.sourceId ?? null,
          tile: tileId ?? null,
          fallbackStyleMode: resolved.styleMode,
        });
      }
      return true;
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
        startupWarn('mapcache', 'PMTiles archive/source changed; reloading map config', event?.detail ?? {});
        this.baseMapRuntimeFallbackApplied = false;
        this.stopMapStatusPolling();
        this.mapServerStatus = null;
        invalidateMapStatus();
        clearMapConfigCache();
        await this.reloadMap();
      } catch (error) {
        startupWarn('mapcache', 'Map reload after archive/source change failed', describeError(error));
      } finally {
        this.mapArchiveStaleReloading = false;
      }
    },

    async reloadMap() {
      const reloadTimer = startStartupTimer('reload', 'Reloading map state');
      this.showLoader = true;

      // Reset state
      this.selectedTrackId = null;
      this.selectedFeature = null;
      this.closeSelectionPopup();
      this.closeSwissMobilityPopup();
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

      // Clean up geo drawing overlay (it holds a reference to the old overlayMap)
      if (this.geoDrawingOverlay && typeof this.geoDrawingOverlay.destroy === 'function') {
        this.geoDrawingOverlay.destroy();
      }
      this.geoDrawingOverlay = null;

      // Clean up the GPS marker before destroying the map.
      if (this.gpsMarker) {
        this.gpsMarker.remove();
        this.gpsMarker = null;
      }
      this.clearFocusedMediaMarker();
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
        this.toolDefs.splice(insertAt, 0, {
          id: 'planner',
          icon: 'bi bi-signpost-split',
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
      const initialBounds =
        hadOverlayMap || returnViewportCamera ? null : initialBoundsFromConfig(this.mapConfig.initialBounds);
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

      // Tear down previous maps
      this.disposeRendererMaps();
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
          this.mapServerStatus = status;
          statusTimer.success('Map server status received', {
            phase: status.phase,
            ready: status.ready,
            tileSource: status.tileSource,
            archiveId: status.archiveId,
          });
        } catch (error) {
          statusTimer.warn('Map server status probe failed', describeError(error));
          // Unreachable → treat as not ready, fall back to OSM
          this.mapServerStatus = {
            phase: 'unreachable',
            ready: false,
          };
        }
      }

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
              ? this.mapServerStatus?.ready === true && !this.baseMapRuntimeFallbackApplied
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
          container: this.$refs.mapContainer,
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
        })
      );
      installMissingStyleImageResolver(this.overlayMap);
      this.overlayMap.once('load', () => {
        startupLog('mapload', 'Map load event received', {
          styleMode,
        });
      });

      // Detect authentication failures from PMTiles / tile fetches and redirect to login.
      // PMTiles 401 errors bypass the axios interceptor, so this is the only recovery path.
      // All other map errors (blocked CDNs, CORS, DNS failures) are also logged for diagnostics.
      this.overlayMap.on('error', (e) => {
        const msg = e.error?.message || '';
        const tileId = e?.tile?.tileID ?? null;
        if (msg.includes('401')) {
          startupWarn('mapload', 'Map tile auth failure; redirecting to login', {
            message: msg,
          });
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
          (selection) => {
            void this.openMediaSelection(selection);
          },
          (points) => {
            this.mediaLoadedPoints = points;
          },
          () => !this.measureToolActive && !this.plannerToolActive && !this.geoDrawingParamDef
        )
      );

      // Initialize heatmap overlay
      this.heatmapOverlay = markRaw(new HeatmapOverlay(this.overlayMap));

      // Resize the map when its container changes (e.g. nav panel expand/collapse).
      if (this._resizeObserver) {
        this._resizeObserver.disconnect();
        this._resizeObserver = null;
      }
      this._resizeObserver = markRaw(
        new ResizeObserver(() => {
          this.overlayMap?.resize();
          // Recompute globe minZoom whenever the viewport size changes (phone vs desktop)
          if (this.globeMode) {
            const minZoom = computeGlobeMinZoom(this.$refs.mapContainer);
            this.overlayMap?.setMinZoom(minZoom);
          }
        })
      );
      this._resizeObserver.observe(this.$refs.mapContainer);

      const waitForMapLoad = (mapInstance) =>
        new Promise((resolve) => {
          if (mapInstance.loaded()) {
            startupLog('mapload', 'Map already loaded');
            resolve(true);
            return;
          }
          mapInstance.once('load', () => resolve(true));
        });
      const mapLoadWatchdog = window.setTimeout(() => {
        startupWarn('mapload', 'Map load watchdog exceeded', {
          styleMode,
          mapLoaded: this.overlayMap?.loaded() ?? false,
          tileMode: this.mapConfig?.tileMode,
          offline: this.mapConfig?.offline ?? false,
          mapServerReady: this.mapServerStatus?.ready ?? null,
        });
      }, MAP_LOAD_WATCHDOG_MS);
      try {
        await waitForMapLoad(this.overlayMap);
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
      await this.restoreMediaLayerPreference(mapSettingsStore.mediaVisible);
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

      // Click handler for track selection.
      this.overlayMap.on('click', (e) => {
        this.clearFocusedMediaMarker();
        if (this.measureToolActive || this.plannerToolActive || this.geoDrawingParamDef) return;

        // Dismiss any open popups on every map click
        this.closeSelectionPopup();
        this.closeSwissMobilityPopup();

        // Skip if user clicked on an individual track point (handled by its own listener)
        const pointFeatures = this.overlayMap.queryRenderedFeatures(e.point, {
          layers: ['track-points-layer'],
        });
        if (pointFeatures && pointFeatures.length > 0) return;

        // Skip if user clicked on a media cluster or single media point (handled by MediaOverlay)
        const mediaLayers = ['media-clusters', 'media-unclustered'].filter((layerId) =>
          this.overlayMap.getLayer(layerId)
        );
        if (mediaLayers.length > 0) {
          const mediaFeatures = this.overlayMap.queryRenderedFeatures(e.point, {
            layers: mediaLayers,
          });
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
          .then(async (gpsTrackIds) => {
            if (!gpsTrackIds || gpsTrackIds.length === 0) {
              this.deselectTrack();
              return;
            }
            if (gpsTrackIds.length === 1) {
              const trackId = gpsTrackIds[0];
              const pointPopupShown = await this.showTrackLinePointPopup(e, trackId);
              if (pointPopupShown) {
                if (this.selectedTrackId !== trackId) this.selectTrackById(trackId);
                return;
              }
              this.selectTrackById(trackId);
              this.openTrackDetails(trackId, TRACK_DETAILS_MAP_DETENT);
            } else {
              this.showTrackSelectionPopup(e.point, gpsTrackIds);
            }
          })
          .catch((err) => {
            if (isAbortLikeError(err)) return;
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
        if (remoteRasterOverride) {
          startupLog('mapstatus', 'Skipping local map status polling because remote raster source is selected');
        } else {
          if (this.mapServerStatus?.ready) {
            ensureLowZoomCached(lowzoomTileArchiveUrl(this.mapConfig)).catch((e) => {
              startupWarn('mapcache', 'Low-zoom cache warmup failed', describeError(e));
              console.warn('Low-zoom cache failed:', e);
            });
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
