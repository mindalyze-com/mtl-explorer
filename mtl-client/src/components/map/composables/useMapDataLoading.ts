// eslint-disable-next-line @typescript-eslint/ban-ts-comment
// @ts-nocheck
/* eslint-disable @typescript-eslint/no-unused-vars, @typescript-eslint/no-explicit-any -- Data loading still crosses broad API/cache shapes. */
import { markRaw } from 'vue';
import { GeoDrawingOverlay } from '@/layers/GeoDrawingOverlay';
import { checkServerAuth, fetchTrackCanonicalPoints, fetchTrackPointsForRenderedShape } from '@/utils/ServiceHelper';
import { getToken, isAuthError, redirectToLoginAfterAuthFailure } from '@/utils/auth';
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
import { clearMapConfigCache } from '@/utils/mapConfigService';
import {
  collectionPrecisionForZoom,
  haversineDistance,
  isSameOrBetterPrecision,
  precisionForZoom,
} from '@/components/map/mapGeometry';
import { describeError, startStartupTimer, startupLog, startupWarn } from '@/utils/startupDiagnostics';
import { isAbortLikeError } from '@/utils/errors';
import type {
  MapControllerMethodDefinitions,
  MapControllerRuntime,
  MapDataLoadingMethods,
} from './mapControllerRuntime';

const DETAIL_BOUNDS_PADDING = 2;
const DETAIL_DEBOUNCE_MS = 500;
const DETAIL_MAX_CONCURRENT = 5;
const DETAIL_MAX_CONCURRENT_1M = 1;
const TRACK_POINTS_MIN_ZOOM = 16;
const DEFAULT_MAP_ZOOM = 10;

function loadAndMergeTrackCollection(runtime: MapControllerRuntime, precision: number) {
  return loadTrackCollectionPaged(precision, {
    onPage: (page) => runtime.mergeTrackPage(page),
    pageSize: TRACK_LOAD_BATCH_SIZE,
  });
}

async function applyFreshTrackCollection(runtime: MapControllerRuntime, serverData: any): Promise<void> {
  if (runtime.geojson) {
    await runtime.mergeTrackResult(serverData, { pruneMissing: true });
  } else {
    await runtime.loadMapData(serverData);
  }
  runtime.isOffline = false;
  runtime.maybeLoadBackgroundTracks(serverData.filterResult);
  runtime.scheduleDetailCheck();
  if (runtime.mediaOverlay?.isVisible()) {
    if (!runtime.mediaOverlay.refresh) {
      throw new Error('Visible media overlay cannot be refreshed');
    }
    await runtime.mediaOverlay.refresh();
  }
  // A focused marker is rendered outside the refreshed GeoJSON source and may remain
  // present while the media layer is hidden. Discard it after the fresh collection loads.
  runtime.clearFocusedMediaMarker();
  await runtime.captureAppliedFreshnessToken();
}

export function useMapDataLoading(deps: {
  filterStore: any;
  freshnessStore: any;
}): MapControllerMethodDefinitions<MapDataLoadingMethods> {
  const { filterStore, freshnessStore } = deps;
  const methods: MapControllerMethodDefinitions<MapDataLoadingMethods> = {
    shouldAutoFreshenAfterLogin() {
      if (!this.fromLogin || this.freshLoginAutoFreshenDone || this.freshnessReloading) return false;
      const serverToken = this.serverFreshnessToken;
      const appliedToken = this.appliedFreshnessToken;
      return Boolean(this.initialLoadDone && serverToken && appliedToken && serverToken !== appliedToken);
    },

    maybeAutoFreshenAfterLogin() {
      if (!this.shouldAutoFreshenAfterLogin()) return;
      this.freshLoginAutoFreshenDone = true;
      startupLog('tracks', 'Freshness changed after login; refreshing map automatically');
      console.info('[MTL] Freshness changed after login; refreshing map automatically.');
      void this.onDataFreshnessReload({
        silent: true,
      });
    },

    async captureAppliedFreshnessToken() {
      const freshness = await this.refreshDataFreshness();
      const token = freshness?.freshnessToken ?? this.serverFreshnessToken;
      if (!token) return;
      freshnessStore.markAppliedToken(token);
    },

    async clearTrackCacheWhenServerFreshnessChanged() {
      const freshness = await this.refreshDataFreshness();
      const serverToken = freshness?.freshnessToken ?? this.serverFreshnessToken;
      if (!serverToken) return false;
      const appliedToken = this.appliedFreshnessToken;
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
      freshnessStore.setReloading(true);
      this.showLoader = true;
      this.loadingTrackBatches = true;
      try {
        await this.clearTrackCacheWhenServerFreshnessChanged();
        await filterStore.refreshResolvedFilter();
        const collectionPrecision = this.currentCollectionPrecision();
        const serverData = await loadAndMergeTrackCollection(this, collectionPrecision);
        this.totalTrackCount = serverData.standardFilterCount;
        await applyFreshTrackCollection(this, serverData);
        this.cachedTracksLoaded = true;
        this.initialLoadDone = true;
        if (!silent) {
          freshnessStore.clearSnooze();
          this.$toast.add({
            severity: 'success',
            summary: 'Map updated',
            detail: 'Fresh data loaded.',
            life: 2500,
          });
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
        freshnessStore.setReloading(false);
      }
    },

    onDataFreshnessDismiss() {
      freshnessStore.snooze();
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

    mergeTrackFeatures(fetchResult) {
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
        } else {
          this.geojson.features.push(feature);
          this.gpsTrackIdToFeature.set(numId, feature);
          this.trackPrecisions.set(numId, incomingPrecision);
        }
        changed = true;

        const gpsTrack = fetchResult.gpsTracksById.get(trackId) ?? fetchResult.gpsTracksById.get(numId);
        if (gpsTrack) {
          this.gpsTracksById.set(numId, gpsTrack);
          trackDataChanged = true;
        }
      }
      return { changed, incomingIds, trackDataChanged };
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
      const merged = this.mergeTrackFeatures(fetchResult);
      const incomingIds = merged.incomingIds;
      let changed = merged.changed;
      let trackDataChanged = merged.trackDataChanged;
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
      const { changed, trackDataChanged } = this.mergeTrackFeatures(fetchResult);
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

    async onMapFreshnessBrowserReload() {
      return this.onDataFreshnessReload();
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
      const success = await this.onDataFreshnessReload();
      done?.(success);
    },

    async showCachedTrackFallback(cached, loadMessage, emittedMessage) {
      startupLog('tracks', loadMessage, {
        featureCount: cached.geojson?.features?.length ?? 0,
      });
      await this.loadMapData(cached);
      this.isOffline = true;
      this.cachedTracksLoaded = true;
      this.showLoader = false;
      this.$emit('tracks-loaded');
      startupLog('tracks', emittedMessage);
      this.fitToTrackBounds(cached.geojson);
    },

    async fetchTracksAndFallback() {
      const timer = startStartupTimer('tracks', 'Resolving startup tracks and fallbacks');
      this.cachedTracksLoaded = false;
      this.initialLoadDone = false;
      const authStatus = await checkServerAuth();
      startupLog('tracks', 'Auth status checked before track load', {
        authStatus,
      });
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
          timer.success('Instant cache load completed', {
            featureCount: cached.geojson.features.length,
          });

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
            await this.showCachedTrackFallback(
              cached,
              'Using cached tracks after startup fallback timeout',
              'tracks-loaded emitted from cached fallback'
            );
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
        startupLog('tracks', 'Fetching startup tracks in batches', {
          precision: collectionPrecision,
        });
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
          await this.mergeTrackResult(serverData, {
            pruneMissing: true,
          });
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
            await this.mergeTrackResult(serverData, {
              pruneMissing: true,
            });
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
            await this.showCachedTrackFallback(
              cached,
              'Recovered from startup failure using cached tracks',
              'tracks-loaded emitted from cached recovery'
            );
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
        startupLog('sync', 'Background sync: fetching server data', {
          precision: collectionPrecision,
        });
        const serverData = await loadAndMergeTrackCollection(this, collectionPrecision);
        this.totalTrackCount = serverData.standardFilterCount;
        await this.mergeTrackResult(serverData, {
          pruneMissing: true,
        });
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

    applyBackgroundTrackPage(pageData, signal) {
      if (signal.aborted) return false;
      let changed = false;
      let trackDataChanged = false;
      for (const [trackId, feature] of pageData.gpsTrackIdToFeature) {
        if (signal.aborted) return false;
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
      if (trackDataChanged) this.publishGpsTrackMetadataChanges();
      if (changed) this.updateTracksSource();
      return changed;
    },

    async loadAllTracksAt10m(filterResult) {
      if (this.bulk10mController) this.bulk10mController.abort();
      this.bulk10mController = markRaw(new AbortController());
      const signal = this.bulk10mController.signal;
      this.loadingTracks10m = true;
      try {
        console.log('Background: loading all tracks at 10m…');
        const applyPage = (pageData) => this.applyBackgroundTrackPage(pageData, signal);
        const data10m = await loadTrackCollectionPaged(BACKGROUND_TRACK_PRECISION, {
          signal,
          filterResult,
          onPage: applyPage,
          pageSize: TRACK_LOAD_BATCH_SIZE,
        });
        if (signal.aborted) return;
        this.applyBackgroundTrackPage(data10m, signal);
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
        await applyFreshTrackCollection(this, serverData);
        this.retryCount = 0;
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

    async onFilterApplied() {
      if (!this.initialLoadDone) {
        console.log(
          '[Map] Suppressing filter event during initial load — track collection loader already applies the active filter'
        );
        return;
      }
      console.log('map got filter applied — using IDs-only fast path');
      // Legend visibility is a temporary map-only refinement. A global filter
      // change starts a new result and must not inherit hidden categories.
      this.hiddenGroups = new Set();
      this.showLoader = true;
      try {
        // Fast path: fetch only matching IDs from server, resolve data from local cache
        const filterResult = filterStore.activeResult ?? undefined;
        const result = await applyTrackFilter({
          filterResult,
        });

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
        await this.reloadMap();
      } finally {
        this.showLoader = false;
      }
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
  return methods;
}
