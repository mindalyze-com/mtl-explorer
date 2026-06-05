// eslint-disable-next-line @typescript-eslint/ban-ts-comment
// @ts-nocheck
/* eslint-disable @typescript-eslint/no-unused-vars, @typescript-eslint/no-explicit-any -- Renderer setup still crosses broad MapLibre/runtime config shapes. */
import { markRaw } from 'vue';
import maplibregl from 'maplibre-gl';
import axios from 'axios';
import { apiClient } from '@/utils/apiClient';
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
  MapConfigDtoTileSourceEnum,
} from '@/utils/mapConfigService';
import { buildLocalVectorStyleFromArchiveUrl, buildFallbackRasterStyle, TERRAIN_DEM_SOURCE_ID } from '@/utils/mapStyle';
import { GlobeControl, computeGlobeMinZoom } from '@/components/map/GlobeControl';
import { TerrainViewControl } from '@/components/map/TerrainViewControl';
import { haversineDistance } from '@/components/map/mapGeometry';
import { collectStyleAttributions, resolveConfiguredMapStyle } from '@/components/map/mapStyleResolver';
import { ensureLowZoomCached, loadLowZoomFromCache } from '@/utils/lowZoomCacheService';
import { describeError, startStartupTimer, startupLog, startupWarn } from '@/utils/startupDiagnostics';
import { ensurePMTilesProtocol, registerCachingPMTilesArchive } from '@/utils/maplibrePmtilesProtocol';
import type { MapControllerMethodDefinitions, MapRendererLifecycleMethods } from './mapControllerRuntime';

const GLOBE_ENTER_ZOOM = 3;
const GLOBE_EXIT_ZOOM = 3.8;
const MERCATOR_MIN_ZOOM = 1.0;
const MAP_NAVIGATION_CONTROL_OPTIONS = Object.freeze({ showCompass: true, showZoom: true, visualizePitch: true });
const MAP_LOAD_WATCHDOG_MS = 7000;
const DEFAULT_MAP_ZOOM = 10;
const INITIAL_TRACK_BOUNDS_PADDING = 48;
const INITIAL_TRACK_BOUNDS_MAX_ZOOM = 13;
const TRACK_DETAILS_MAP_DETENT = 'compact';
const MAP_STATUS_POLL_INTERVAL_MS = 5000;
const MAP_STATUS_POLL_TIMEOUT_MS = 8000;
const LOCAL_VECTOR_STYLE_MODE = 'local-vector';
const LOCAL_VECTOR_SOURCE_ID = 'protomaps';

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

export function useMapRendererLifecycle(
  deps: Record<string, never> = {}
): MapControllerMethodDefinitions<MapRendererLifecycleMethods> {
  let mapStatusPollingActive = false;
  let mapStatusPollGeneration = 0;
  const methods: MapControllerMethodDefinitions<MapRendererLifecycleMethods> = {
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
      this.setOverlayAttributionControl(resolved.attributions);
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
      if (this._attributionControl && typeof this.overlayMap.removeControl === 'function') {
        try {
          this.overlayMap.removeControl(this._attributionControl);
        } catch {
          // The overlay map may already be tearing down during a reload.
        }
      }
      const customAttribution = Array.isArray(attributions) && attributions.length > 0 ? attributions : undefined;
      this._attributionControl = markRaw(
        new maplibregl.AttributionControl({
          compact: true,
          customAttribution,
        })
      );
      this.overlayMap.addControl(this._attributionControl, 'bottom-right');
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
      const reloadTimer = startStartupTimer('reload', 'Reloading map state', {
        loadMedia,
      });
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
        this._terrainControl = null;
        this._attributionControl = null;
      }
      if (this.map) {
        this.map.remove();
        this.map = undefined;
      }
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
      if (remoteRasterOverride) {
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
            this.mapConfig.tileMode === MapConfigDtoTileModeEnum.Local ? this.mapServerStatus?.ready === true : true,
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
        archiveId: this.mapServerStatus?.archive_id ?? this.mapConfig.archiveId ?? null,
      });
      this.baseMapStyleMode = styleMode;
      this.baseMapRuntimeFallbackApplied = false;

      // ── Base map: tiles, Swiss Mobility overlays, dim layer — passive ──
      startupLog('mapload', 'Creating base map instance', {
        styleMode,
      });
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
          this.map.addImage(e.id, {
            width: 1,
            height: 1,
            data: new Uint8ClampedArray(4),
          });
        }
      });
      this.map.once('load', () => {
        startupLog('mapload', 'Base map load event received', {
          styleMode,
        });
      });

      // Detect authentication failures from PMTiles / tile fetches and redirect to login.
      // PMTiles 401 errors bypass the axios interceptor, so this is the only recovery path.
      // All other map errors (blocked CDNs, CORS, DNS failures) are also logged for diagnostics.
      this.map.on('error', (e) => {
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

      // ── Overlay map: tracks, highlights, media — handles ALL user interaction ──
      startupLog('mapload', 'Creating overlay map instance');
      this.overlayMap = markRaw(
        new maplibregl.Map({
          container: this.$refs.mapOverlayContainer,
          style: {
            version: 8,
            sources: {},
            layers: [],
          },
          center: initialCenter,
          zoom: initialZoom,
          minZoom: MERCATOR_MIN_ZOOM,
          attributionControl: false,
          dragRotate: true,
          pitchWithRotate: true,
          touchPitch: true,
        })
      );
      this.overlayMap.once('load', () => {
        startupLog('mapload', 'Overlay map load event received');
      });

      // Controls live on overlay map (HTML elements — always clickable on top)
      this.overlayMap.addControl(new maplibregl.NavigationControl(MAP_NAVIGATION_CONTROL_OPTIONS), 'top-left');
      this._globeControl = markRaw(new GlobeControl(() => this.toggleGlobeMode()));
      this.overlayMap.addControl(this._globeControl, 'top-left');
      this._terrainControl = markRaw(new TerrainViewControl(() => this.onToggleTerrainMode()));
      this.overlayMap.addControl(this._terrainControl, 'top-left');
      this._terrainControl.setActive(this.terrainEnabled);
      this.overlayMap.addControl(
        new maplibregl.ScaleControl({
          maxWidth: 100,
        }),
        'bottom-left'
      );
      this.setOverlayAttributionControl(styleAttributions);

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
      startupLog('mapload', 'Both map instances finished loading', {
        styleMode,
      });
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
        this.map.fitBounds(initialBounds, fitOptions);
        startupLog('mapinit', 'Fitted initial viewport to stored track bounds', {
          minLng: initialBounds[0][0],
          minLat: initialBounds[0][1],
          maxLng: initialBounds[1][0],
          maxLat: initialBounds[1][1],
        });
      }
      initTimer.success('Map initialization completed', {
        styleMode,
      });

      // ── View sync: overlay map drives the base map ──
      const syncBase = () => this.syncBaseMapToOverlay();
      let terrainSyncFrame = null;
      const scheduleTerrainSync = () => {
        if (terrainSyncFrame != null) return;
        terrainSyncFrame = window.requestAnimationFrame(() => {
          terrainSyncFrame = null;
          syncBase();
        });
      };
      const syncBaseTerrain = (event) => {
        if (!this.terrainEnabled) return;
        if (!event?.sourceId || event.sourceId === TERRAIN_DEM_SOURCE_ID) scheduleTerrainSync();
      };
      this.overlayMap.on('move', syncBase);
      this.overlayMap.on('sourcedata', syncBaseTerrain);
      this.overlayMap.on('terrain', scheduleTerrainSync);
      syncBase();

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
        if (remoteRasterOverride) {
          startupLog('mapstatus', 'Skipping local map status polling because remote raster source is selected');
        } else if (this.mapServerStatus?.ready) {
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

    /** Set MapLibre projection on both maps and let MapLibre handle the morph. */
    applyGlobeProjection() {
      const proj = this.globeMode
        ? {
            type: 'globe',
          }
        : {
            type: 'mercator',
          };

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
