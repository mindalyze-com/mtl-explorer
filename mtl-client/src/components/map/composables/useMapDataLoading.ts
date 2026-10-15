// eslint-disable-next-line @typescript-eslint/ban-ts-comment
// @ts-nocheck
/* eslint-disable @typescript-eslint/no-unused-vars, @typescript-eslint/no-explicit-any -- Data loading still crosses broad API/cache shapes. */
import { warnSanitized } from '@/utils/safeLogging';
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
  resetTrackLoadRequests,
} from '@/utils/tracks/trackCollectionLoader';
import { clearMapConfigCache } from '@/utils/mapConfigService';
import { buildTrackGeometry } from '@/utils/tracks/trackFeatureAdapter';
import {
  collectionPrecisionForZoom,
  haversineDistance,
  isSameOrBetterPrecision,
  precisionForZoom,
} from '@/components/map/mapGeometry';
import { startStartupTimer, startupLog, startupWarn } from '@/utils/startupDiagnostics';
import { isAbortLikeError, TrackLoadSupersededError } from '@/utils/errors';
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
const FRESHNESS_SERVER_ID_PREFIX = 'server_id:';

function freshnessServerIdentity(token: string | null | undefined): string | undefined {
  return token
    ?.split('|')
    .find((entry) => entry.startsWith(FRESHNESS_SERVER_ID_PREFIX))
    ?.slice(FRESHNESS_SERVER_ID_PREFIX.length);
}

function freshnessRevisionWentBackwards(serverToken: string, appliedToken: string | null | undefined): boolean {
  const revisions = (token: string | null | undefined) =>
    new Map(
      (token?.split('|') ?? []).flatMap((entry) => {
        const match = /^([^:]+):(\d+)$/.exec(entry);
        return match && match[1] !== 'server_id' ? [[match[1], BigInt(match[2])] as const] : [];
      })
    );
  const current = revisions(serverToken);
  return [...revisions(appliedToken)].some(([key, revision]) => {
    const value = current.get(key);
    return value != null && value < revision;
  });
}

function loadAndMergeTrackCollection(runtime: MapControllerRuntime, precision: number) {
  const generation = runtime._mapLoadGeneration;
  return loadTrackCollectionPaged(precision, {
    onPage: (page) => {
      if (runtime._mapUnmounted || runtime._mapLoadGeneration !== generation) return;
      return runtime.mergeTrackPage(page);
    },
    pageSize: TRACK_LOAD_BATCH_SIZE,
  });
}

async function applyFreshTrackCollection(
  runtime: MapControllerRuntime,
  serverData: any,
  loadedToken: string | null | undefined
): Promise<boolean> {
  const generation = runtime._mapLoadGeneration;
  const isCurrent = () => !runtime._mapUnmounted && runtime._mapLoadGeneration === generation;
  if (!isCurrent()) return false;
  runtime.totalTrackCount = serverData.standardFilterCount;
  if (runtime.geojson) {
    await runtime.mergeTrackResult(serverData, { pruneMissing: true });
  } else {
    await runtime.loadMapData(serverData);
  }
  if (!isCurrent() || !runtime.geojson) return false;
  runtime.isOffline = false;
  runtime.maybeLoadBackgroundTracks(serverData.filterResult);
  runtime.scheduleDetailCheck();
  if (runtime.mediaOverlay?.isVisible()) {
    if (!runtime.mediaOverlay.refresh) {
      throw new Error('Visible media overlay cannot be refreshed');
    }
    await runtime.mediaOverlay.refresh();
  }
  if (!isCurrent()) return false;
  // A focused marker is rendered outside the refreshed GeoJSON source and may remain
  // present while the media layer is hidden. Discard it after the fresh collection loads.
  runtime.clearFocusedMediaMarker();
  await runtime.captureAppliedFreshnessToken(loadedToken);
  return isCurrent();
}

export function useMapDataLoading(deps: {
  filterStore: any;
  freshnessStore: any;
}): MapControllerMethodDefinitions<MapDataLoadingMethods> {
  const { filterStore, freshnessStore } = deps;
  let filterApplySequence = 0;
  let pendingFilterApply = false;

  async function completeInitialLoad(runtime: MapControllerRuntime): Promise<void> {
    runtime.initialLoadDone = true;
    if (!pendingFilterApply) return;
    pendingFilterApply = false;
    await runtime.onFilterApplied({ preserveSelection: true });
  }

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

    async captureAppliedFreshnessToken(loadedToken) {
      // Only acknowledge the revision observed before loading. A revision that
      // appears during a paged load has not necessarily reached this map.
      if (loadedToken) freshnessStore.markAppliedToken(loadedToken);
      await this.refreshDataFreshness();
    },

    async clearTrackCacheWhenServerFreshnessChanged(options) {
      const generation = this._mapLoadGeneration;
      const freshness = await this.refreshDataFreshness(options);
      if (this._mapUnmounted || this._mapLoadGeneration !== generation) return false;
      const serverToken = freshness?.freshnessToken ?? this.serverFreshnessToken;
      if (!serverToken) return false;
      const appliedToken = this.appliedFreshnessToken;
      if (serverToken === appliedToken) return false;
      const serverIdentity = freshnessServerIdentity(serverToken);
      const appliedServerIdentity = freshnessServerIdentity(appliedToken);
      const retainCache =
        serverIdentity &&
        serverIdentity === appliedServerIdentity &&
        !freshnessRevisionWentBackwards(serverToken, appliedToken);
      startupLog(
        'tracks',
        retainCache
          ? 'Server freshness changed; retaining versioned track cache for sync'
          : 'Server identity changed, is unknown, or revisions went backwards; clearing local track cache before sync'
      );
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
        this.loadingTracks10m = false;
      }
      if (retainCache) resetTrackLoadRequests();
      else {
        // Another installation can reuse the same track IDs and entity versions.
        // Its coarser fresh shapes must replace any finer shapes still on this map.
        this.trackPrecisions?.clear();
        await clearTrackCache();
      }
      return true;
    },

    async onDataFreshnessReload(options = {}) {
      if (this.freshnessReloading) return false;
      const generation = this._mapLoadGeneration;
      const isCurrent = () => !this._mapUnmounted && this._mapLoadGeneration === generation;
      if (!isCurrent()) return false;
      const silent = options?.silent === true;
      freshnessStore.setReloading(true);
      this.showLoader = true;
      this.loadingTrackBatches = true;
      try {
        await this.clearTrackCacheWhenServerFreshnessChanged();
        if (!isCurrent()) return false;
        const loadedToken = this.serverFreshnessToken;
        await filterStore.refreshResolvedFilter();
        if (!isCurrent()) return false;
        const collectionPrecision = this.currentCollectionPrecision();
        const serverData = await loadAndMergeTrackCollection(this, collectionPrecision);
        if (!isCurrent() || !(await applyFreshTrackCollection(this, serverData, loadedToken))) return false;
        this.cachedTracksLoaded = true;
        await completeInitialLoad(this);
        if (!isCurrent()) return false;
        if (!silent) {
          this.$toast.add({
            severity: 'success',
            summary: 'Map refreshed',
            detail: this.isIndexing
              ? 'Latest available data loaded. Preparation is still running.'
              : 'Latest available data loaded.',
            life: 2500,
          });
        }
        return true;
      } catch (error) {
        if (!isCurrent() || isAbortLikeError(error)) return false;
        if (!silent) {
          this.$toast.add({
            severity: 'error',
            summary: 'Reload failed',
            detail: 'Fresh data could not be loaded.',
            life: 4000,
          });
        }
        startupWarn('tracks', 'Data freshness reload failed', error);
        return false;
      } finally {
        this.loadingTrackBatches = false;
        this.showLoader = false;
        freshnessStore.setReloading(false);
        if (isCurrent() && this.initialLoadDone && pendingFilterApply) await completeInitialLoad(this);
      }
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
      const readyPromise = this._mapReadyPromise;
      if (readyPromise && (await readyPromise) === false) return;
      if (this._mapUnmounted || readyPromise !== this._mapReadyPromise || !this.overlayMap) return;
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
        const gpsTrack = fetchResult.gpsTracksById.get(trackId) ?? fetchResult.gpsTracksById.get(numId);
        const existingTrack = this.gpsTracksById.get(numId);
        const versionChanged = existingTrack && gpsTrack && existingTrack.version !== gpsTrack.version;
        const existingFeature = this.gpsTrackIdToFeature.get(numId);
        const incomingPrecision = fetchResult.trackPrecisions?.get(numId) ?? OVERVIEW_PRECISION;
        const currentPrecision = this.trackPrecisions.get(numId) ?? OVERVIEW_PRECISION;
        if (existingFeature) {
          existingFeature.properties = feature.properties;
          if (versionChanged || isSameOrBetterPrecision(incomingPrecision, currentPrecision)) {
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
      const generation = this._mapLoadGeneration;
      if (!this.cachedTracksLoaded) {
        this.totalTrackCount = fetchResult.standardFilterCount;
        await this.loadMapData(fetchResult);
        if (this._mapUnmounted || this._mapLoadGeneration !== generation) return;
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
        if (this._mapUnmounted || this._mapLoadGeneration !== generation) return;
        this.updateTracksSource();
      }
    },

    async onMapFreshnessBrowserReload() {
      return this.onDataFreshnessReload();
    },

    async onAdminReloadTracks(done) {
      const success = await this.onDataFreshnessReload();
      done?.(success);
    },

    async onAdminRefreshFreshnessData(done) {
      const success = await this.onDataFreshnessReload();
      done?.(success);
    },

    async showCachedTrackFallback(cached, loadMessage, emittedMessage) {
      const generation = this._mapLoadGeneration;
      startupLog('tracks', loadMessage, {
        featureCount: cached.geojson?.features?.length ?? 0,
      });
      await this.loadMapData(cached);
      if (this._mapUnmounted || this._mapLoadGeneration !== generation) return;
      this.isOffline = true;
      this.cachedTracksLoaded = true;
      this.showLoader = false;
      this.$emit('tracks-loaded');
      startupLog('tracks', emittedMessage);
      this.fitToTrackBounds(cached.geojson);
    },

    async fetchTracksAndFallback() {
      const generation = this._mapLoadGeneration;
      const requestToken = getToken();
      const isCurrent = () => !this._mapUnmounted && this._mapLoadGeneration === generation;
      const timer = startStartupTimer('tracks', 'Resolving startup tracks and fallbacks');
      this.cachedTracksLoaded = false;
      this.initialLoadDone = false;
      this.loadingTrackBatches = false;
      this.$emit('syncing', false);
      const authStatus = await checkServerAuth();
      if (!isCurrent()) return;
      startupLog('tracks', 'Auth status checked before track load', {
        authStatus,
      });
      if (authStatus === 'auth-error') {
        timer.success('Sign-in required; redirecting to login');
        this.showLoader = false;
        redirectToLoginAfterAuthFailure(!!requestToken, requestToken);
        return;
      }
      // Startup needs freshness now rather than at the next background polling deadline.
      await this.clearTrackCacheWhenServerFreshnessChanged({ immediate: true });
      if (!isCurrent()) return;
      const loadedToken = this.serverFreshnessToken;

      // ── Phase 2: Show cached tracks, then reconcile and sync in the background ──
      const cachePopulated = await isTrackCachePopulated();
      if (!isCurrent()) return;
      if (cachePopulated) {
        startupLog('tracks', 'Cache populated — loading cached tracks immediately');
        const cached = await loadCachedTrackCollection();
        if (!isCurrent()) return;
        if (cached && cached.geojson?.features?.length > 0) {
          await this.loadMapData(cached);
          if (!isCurrent()) return;
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
        if (!isCurrent()) return;
        if (!this.cachedTracksLoaded) {
          startupWarn('tracks', 'Server tracks still pending; attempting IndexedDB fallback', undefined, {
            fallbackAfterMs: CACHE_FALLBACK_TIMEOUT_MS,
          });
          const cached = await loadCachedTrackCollection();
          if (!isCurrent()) return;
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
          if (!isCurrent()) return;
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
        clearTimeout(fallbackTimer);
        if (!isCurrent()) return;
        this.loadingTrackBatches = false;
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
          if (!isCurrent()) return;
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
          if (!isCurrent()) return;
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
        await this.captureAppliedFreshnessToken(loadedToken);
        if (!isCurrent()) return;
        await completeInitialLoad(this);
      } catch (e) {
        clearTimeout(fallbackTimer);
        if (!isCurrent()) return;
        this.loadingTrackBatches = false;
        timer.error('Startup track resolution failed', e);
        if (isAuthError(e)) {
          this.showLoader = false;
          redirectToLoginAfterAuthFailure(!!requestToken, requestToken);
          return;
        }
        if (!this.cachedTracksLoaded) {
          const cached = await loadCachedTrackCollection();
          if (!isCurrent()) return;
          if (cached) {
            await this.showCachedTrackFallback(
              cached,
              'Recovered from startup failure using cached tracks',
              'tracks-loaded emitted from cached recovery'
            );
            if (!isCurrent()) return;
          } else {
            this.showLoader = false;
            startupWarn('tracks', 'No cached tracks available; emitting load-failed');
            this.$emit('load-failed');
          }
        } else {
          this.isOffline = true;
        }
        this.scheduleRetry();
        await completeInitialLoad(this);
      }
    },

    /**
     * Background sync after showing cached tracks.
     * Fetches fresh data from server, updates the map seamlessly, then loads 10m.
     */
    async _backgroundSync(timer) {
      const generation = this._mapLoadGeneration;
      const requestToken = getToken();
      const isCurrent = () => !this._mapUnmounted && this._mapLoadGeneration === generation;
      try {
        const freshness = await this.refreshDataFreshness();
        if (!isCurrent()) return;
        const loadedToken = freshness?.freshnessToken ?? this.serverFreshnessToken;
        const collectionPrecision = this.currentCollectionPrecision();
        startupLog('sync', 'Background sync: fetching server data', {
          precision: collectionPrecision,
        });
        const serverData = await loadAndMergeTrackCollection(this, collectionPrecision);
        if (!isCurrent()) return;
        this.totalTrackCount = serverData.standardFilterCount;
        await this.mergeTrackResult(serverData, {
          pruneMissing: true,
        });
        if (!isCurrent()) return;
        this.isOffline = false;
        startupLog('sync', 'Background sync: map updated with server data', {
          precision: collectionPrecision,
          featureCount: serverData.geojson?.features?.length ?? 0,
        });
        timer.success('Background sync completed');
        this.$emit('syncing', false);
        this.maybeLoadBackgroundTracks(serverData.filterResult);
        this.scheduleDetailCheck();
        await this.captureAppliedFreshnessToken(loadedToken);
        if (!isCurrent()) return;
        await completeInitialLoad(this);
      } catch (e) {
        if (!isCurrent()) return;
        startupWarn('sync', 'Background sync failed — using cached data', e);
        if (isAuthError(e)) {
          redirectToLoginAfterAuthFailure(!!requestToken, requestToken);
          return;
        }
        this.isOffline = true;
        this.$emit('syncing', false);
        this.scheduleRetry();
        await completeInitialLoad(this);
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
        const existingTrack = this.gpsTracksById.get(numId);
        const versionChanged = existingTrack && gpsTrack && existingTrack.version !== gpsTrack.version;
        if (gpsTrack) {
          this.gpsTracksById.set(numId, gpsTrack);
          trackDataChanged = true;
        }
        const currentFeature = this.gpsTrackIdToFeature.get(numId);
        if (currentFeature) {
          currentFeature.properties = feature.properties;
          changed = true;
        }
        const currentPrecision = this.trackPrecisions.get(numId) ?? OVERVIEW_PRECISION;
        if (!versionChanged && currentPrecision <= BACKGROUND_TRACK_PRECISION) continue;
        const existingFeature = this.gpsTrackIdToFeature.get(numId);
        if (existingFeature?.geometry && feature?.geometry) {
          existingFeature.geometry = feature.geometry;
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
      const controller = this.bulk10mController;
      const signal = controller.signal;
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
          return;
        }
        warnSanitized('Background 10m load failed:', e, {
          topic1: 'map',
          topic2: 'useMapDataLoading',
          topic3: 'loadAllTracksAt10m',
        });
      } finally {
        if (this.bulk10mController === controller) this.loadingTracks10m = false;
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
      const generation = this._mapLoadGeneration;
      const requestToken = getToken();
      const isCurrent = () => !this._mapUnmounted && this._mapLoadGeneration === generation;
      this.retryCount++;
      try {
        const freshness = await this.refreshDataFreshness();
        if (!isCurrent()) return;
        const loadedToken = freshness?.freshnessToken ?? this.serverFreshnessToken;
        const collectionPrecision = this.currentCollectionPrecision();
        const serverData = await loadTrackCollectionPaged(collectionPrecision);
        if (!isCurrent()) return;
        if (!(await applyFreshTrackCollection(this, serverData, loadedToken))) return;
        if (!isCurrent()) return;
        this.retryCount = 0;
        // If the initial map style was the offline raster fallback (e.g. the
        // /api/map/config call timed out on first login), rebuild the style
        // now that connectivity is back — no page reload needed.
        if (this.mapConfig?.offline) {
          try {
            console.log('Recovered from offline fallback — rebuilding map style with real config');
            clearMapConfigCache();
            if ((await this.initMap(true)) === false || !isCurrent()) return;
            await this.addTracksToMap();
          } catch (rebuildErr) {
            warnSanitized('Failed to rebuild map style after recovery:', rebuildErr, {
              topic1: 'map',
              topic2: 'useMapDataLoading',
              topic3: 'performBackgroundRetry',
            });
          }
        }
        this.$toast.add({
          severity: 'success',
          summary: 'Online',
          detail: 'Back online — tracks reloaded.',
          life: 3000,
        });
      } catch (e) {
        if (!isCurrent()) return;
        if (isAuthError(e)) {
          redirectToLoginAfterAuthFailure(!!requestToken, requestToken);
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
              // Detail indexes belong to this response's coordinates. Rebuild complete
              // geometry so both overview dots and disconnected lines retain their gaps.
              feature.geometry = buildTrackGeometry(gpsTrack, coordinates, { collapseShortTracks: false });
              changed = true;
              pendingFlush = true;
            }
            this.trackPrecisions.set(trackId, targetPrecision);
            this.gpsTracksById.set(trackId, gpsTrack);
            trackDataChanged = true;
            if (progressive) flush();
          } catch (e) {
            if (signal.aborted || isAbortLikeError(e)) return;
            warnSanitized('Track detail fetch failed', e, {
              topic1: 'map',
              topic2: 'useMapDataLoading',
              topic3: 'fetchNext',
            });
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

    async onFilterApplied(options) {
      const requestSequence = ++filterApplySequence;
      if (!this.initialLoadDone) {
        pendingFilterApply = true;
        console.log('[Map] Queuing the latest filter until startup loading completes');
        return;
      }
      pendingFilterApply = false;
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
        if (requestSequence !== filterApplySequence) return;

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

        // A queued startup filter can finish after a user selects a visible route.
        // Keep that choice when it still belongs to the resulting visible data.
        const selectedId = this.selectedTrackId;
        const selectedFeature = selectedId == null ? undefined : this.gpsTrackIdToFeature.get(selectedId);
        const retainSelection =
          options?.preserveSelection &&
          selectedFeature?.geometry &&
          !selectedFeature.properties?.reviewMapExcluded &&
          !this.gpsTracksById.get(selectedId!)?.reviewMapExcluded;
        if (retainSelection) {
          this.selectedFeature = selectedFeature;
        } else {
          this.selectedTrackId = null;
          this.selectedFeature = null;
          this.closeSelectionPopup();
          if (options?.preserveSelection && selectedId != null) {
            this.endMapInteraction(true);
            this.trackDetailsVisible = false;
          }
        }

        // Re-render tracks on the map without destroying/recreating the map
        await this.addTracksToMap();
        this.updateTracksSource();
        this.maybeLoadBackgroundTracks(result.filterResult);
      } catch (e) {
        if (requestSequence !== filterApplySequence) return;
        if (e instanceof TrackLoadSupersededError) {
          pendingFilterApply = true;
          // A fast reload may already have completed before the canceled
          // request settled. In that case there is no completion hook to wait for.
          if (this.initialLoadDone && !this.freshnessReloading) await completeInitialLoad(this);
          return;
        }
        if (isAbortLikeError(e)) return;
        // Fallback to full reload if IDs-only path fails (e.g. cache miss)
        warnSanitized('IDs-only filter failed, falling back to full reload:', e, {
          topic1: 'map',
          topic2: 'useMapDataLoading',
          topic3: 'onFilterApplied',
        });
        await this.reloadMap();
      } finally {
        if (requestSequence === filterApplySequence) {
          this.showLoader = false;
        }
      }
      if (requestSequence !== filterApplySequence) return;
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
