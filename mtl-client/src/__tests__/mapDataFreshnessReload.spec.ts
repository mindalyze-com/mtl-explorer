import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { ApplicationStatusControllerApi } from 'x8ing-mtl-api-typescript-fetch';
import { useMapDataLoading } from '@/components/map/composables/useMapDataLoading';
import { useMapTools } from '@/components/map/composables/useMapTools';
import { useDataFreshnessStore } from '@/stores/dataFreshnessStore';
import { APPLICATION_STATUS_SECTION, useApplicationStatusStore } from '@/stores/applicationStatusStore';
import { getAppliedDataFreshnessToken } from '@/utils/dataFreshnessStorage';
import { STATUS_POLL_INTERVAL_MS } from '@/utils/statusPolling';

const trackCollectionMocks = vi.hoisted(() => ({
  clearTrackCache: vi.fn(),
  reconcileTrackCache: vi.fn().mockResolvedValue(undefined),
  resetTrackLoadRequests: vi.fn(),
  isTrackCachePopulated: vi.fn(),
  loadCachedTrackCollection: vi.fn(),
  loadTrackCollectionPaged: vi.fn(),
}));
const checkServerAuthMock = vi.hoisted(() => vi.fn());

vi.mock('@/layers/GeoDrawingOverlay', () => ({
  GeoDrawingOverlay: vi.fn(),
}));

vi.mock('@/utils/ServiceHelper', () => ({
  checkServerAuth: checkServerAuthMock,
  fetchTrackCanonicalPoints: vi.fn(),
  fetchTrackPointsForRenderedShape: vi.fn(),
}));

vi.mock('@/utils/auth', () => ({
  getToken: vi.fn(),
  getAuthHeaderValue: vi.fn(),
  isAuthenticationFailureStatus: vi.fn(() => false),
  isAuthError: vi.fn(() => false),
  redirectToLoginAfterAuthFailure: vi.fn(),
}));

vi.mock('@/utils/tracks/trackCollectionLoader', () => ({
  applyTrackFilter: vi.fn(),
  clearTrackCache: trackCollectionMocks.clearTrackCache,
  reconcileTrackCache: trackCollectionMocks.reconcileTrackCache,
  resetTrackLoadRequests: trackCollectionMocks.resetTrackLoadRequests,
  fetchDetailTrackAtPrecision: vi.fn(),
  isTrackCachePopulated: trackCollectionMocks.isTrackCachePopulated,
  loadCachedTrackCollection: trackCollectionMocks.loadCachedTrackCollection,
  loadTrackCollectionPaged: trackCollectionMocks.loadTrackCollectionPaged,
}));

vi.mock('@/utils/mapConfigService', () => ({
  clearMapConfigCache: vi.fn(),
}));

vi.mock('@/components/map/mapGeometry', () => ({
  collectionPrecisionForZoom: vi.fn(() => 1000),
  haversineDistance: vi.fn(),
  isSameOrBetterPrecision: vi.fn((incoming: number, current: number) => incoming <= current),
  precisionForZoom: vi.fn(() => 1000),
}));

vi.mock('@/utils/startupDiagnostics', () => ({
  describeError: vi.fn((error) => String(error)),
  startStartupTimer: vi.fn(() => ({
    success: vi.fn(),
    warn: vi.fn(),
    error: vi.fn(),
  })),
  startupLog: vi.fn(),
  startupWarn: vi.fn(),
}));

function makeMethods(filterStore: Record<string, unknown> = {}) {
  return useMapDataLoading({
    filterStore,
    freshnessStore: useDataFreshnessStore(),
  });
}

type FreshnessReloadContext = {
  onDataFreshnessReload: () => Promise<boolean>;
};
type BannerReloadMethod = (this: FreshnessReloadContext) => Promise<boolean>;
type AdminRefreshMethod = (this: FreshnessReloadContext, done: (success?: boolean) => void) => Promise<void>;

describe('map data freshness reload actions', () => {
  beforeEach(() => {
    localStorage.clear();
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  afterEach(() => {
    useDataFreshnessStore().$dispose();
    useApplicationStatusStore().$dispose();
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  it('retains versioned IndexedDB records while stopping stale geometry requests', async () => {
    const detailAbortController = new AbortController();
    const bulk10mController = new AbortController();
    const context = {
      serverFreshnessToken: 'hash-new__|server_id:same-server|tracks:2',
      appliedFreshnessToken: 'hash-old__|server_id:same-server|tracks:1',
      refreshDataFreshness: vi.fn().mockResolvedValue({ freshnessToken: 'hash-new__|server_id:same-server|tracks:2' }),
      trackPointsDetailsCache: new Map([['7_1', []]]),
      trackPointsCanonicalCache: new Map([[7, []]]),
      detailAbortController,
      bulk10mController,
    };

    await expect(makeMethods().clearTrackCacheWhenServerFreshnessChanged.call(context as never)).resolves.toBe(true);

    expect(detailAbortController.signal.aborted).toBe(true);
    expect(bulk10mController.signal.aborted).toBe(true);
    expect(trackCollectionMocks.resetTrackLoadRequests).toHaveBeenCalledOnce();
    expect(trackCollectionMocks.clearTrackCache).not.toHaveBeenCalled();
  });

  it.each([
    ['hash-old__|server_id:old-server|tracks:1', 'hash-new__|server_id:new-server|tracks:1'],
    ['', 'hash-new__|server_id:new-server|tracks:1'],
    ['legacy-token', 'unrecognised-token'],
    ['hash__|server_id:same|tracks:200|track_geometry:100', 'new__|server_id:same|tracks:150|track_geometry:100'],
    ['hash__|server_id:same|tracks:200|track_geometry:100', 'new__|server_id:same|tracks:201|track_geometry:50'],
  ])('invalidates cache when server identity cannot be matched, applied=%s', async (appliedToken, serverToken) => {
    const context = {
      appliedFreshnessToken: appliedToken,
      serverFreshnessToken: serverToken,
      refreshDataFreshness: vi.fn().mockResolvedValue({ freshnessToken: serverToken }),
      trackPointsDetailsCache: new Map(),
      trackPointsCanonicalCache: new Map(),
      trackPrecisions: new Map([[7, 1]]),
    };

    await makeMethods().clearTrackCacheWhenServerFreshnessChanged.call(context as never);

    expect(trackCollectionMocks.clearTrackCache).toHaveBeenCalledOnce();
    expect(trackCollectionMocks.resetTrackLoadRequests).not.toHaveBeenCalled();
    expect(context.trackPrecisions.size).toBe(0);
  });

  it('does not reset requests for a newer map after a late freshness response', async () => {
    let resolveFreshness!: (freshness: { freshnessToken: string }) => void;
    const context = {
      _mapLoadGeneration: 1,
      refreshDataFreshness: vi.fn(
        () =>
          new Promise((resolve) => {
            resolveFreshness = resolve;
          })
      ),
    };
    const probe = makeMethods().clearTrackCacheWhenServerFreshnessChanged.call(context as never);
    context._mapLoadGeneration++;
    resolveFreshness({ freshnessToken: 'new-server' });

    await expect(probe).resolves.toBe(false);
    expect(trackCollectionMocks.clearTrackCache).not.toHaveBeenCalled();
    expect(trackCollectionMocks.resetTrackLoadRequests).not.toHaveBeenCalled();
  });

  it.each(['unchanged', 'revised', 'metadata-refreshed'] as const)(
    'reuses finer geometry only for an unchanged track, state=%s',
    (state) => {
      const changed = state !== 'unchanged';
      const existingFeature = {
        properties: { id: 7, trackName: 'Old' },
        geometry: {
          type: 'LineString',
          coordinates: [
            [7, 46],
            [8, 47],
          ],
        },
      };
      const oldGeometry = existingFeature.geometry;
      const incomingFeature = {
        properties: { id: 7, trackName: 'Current' },
        geometry: {
          type: 'LineString',
          coordinates: [
            [9, 48],
            [10, 49],
          ],
        },
      };
      const incomingTrack = { id: 7, version: changed ? 2 : 1, trackName: 'Current' };
      const context = {
        geojson: { features: [existingFeature] },
        gpsTrackIdToFeature: new Map([[7, existingFeature]]),
        gpsTracksById: new Map([[7, { id: 7, version: 1 }]]),
        trackPrecisions: new Map([[7, 1]]),
        trackPointsCanonicalCache: new Map([
          [7, []],
          [8, []],
        ]),
        trackPointsDetailsCache: new Map([
          ['7_1', []],
          ['8_1', []],
        ]),
        publishGpsTrackMetadataChanges: vi.fn(),
      };

      if (state === 'metadata-refreshed') {
        useMapTools().onTrackMetadataUpdated.call(context as never, incomingTrack);
        expect([...context.trackPointsCanonicalCache.keys()]).toEqual([8]);
        expect([...context.trackPointsDetailsCache.keys()]).toEqual(['8_1']);
      }

      makeMethods().mergeTrackFeatures.call(context as never, {
        gpsTrackIdToFeature: new Map([[7, incomingFeature]]),
        gpsTracksById: new Map([[7, incomingTrack]]),
        trackPrecisions: new Map([[7, 1000]]),
      });

      expect(existingFeature.geometry).toBe(changed ? incomingFeature.geometry : oldGeometry);
      expect(existingFeature.properties.trackName).toBe('Current');
      expect(context.gpsTracksById.get(7)).toBe(incomingTrack);
      expect(context.trackPrecisions.get(7)).toBe(changed ? 1000 : 1);
    }
  );

  it('replaces outdated detail geometry when a changed background track arrives', () => {
    const feature = { properties: { id: 7 }, geometry: { type: 'Point', coordinates: [7, 46] } };
    const incomingFeature = {
      properties: { id: 7 },
      geometry: {
        type: 'LineString',
        coordinates: [
          [9, 48],
          [10, 49],
        ],
      },
    };
    const context = {
      gpsTrackIdToFeature: new Map([[7, feature]]),
      gpsTracksById: new Map([[7, { id: 7, version: 1 }]]),
      trackPrecisions: new Map([[7, 1]]),
      publishGpsTrackMetadataChanges: vi.fn(),
      updateTracksSource: vi.fn(),
    };

    makeMethods().applyBackgroundTrackPage.call(
      context as never,
      {
        gpsTrackIdToFeature: new Map([[7, incomingFeature]]),
        gpsTracksById: new Map([[7, { id: 7, version: 2 }]]),
      },
      new AbortController().signal
    );

    expect(feature.geometry).toBe(incomingFeature.geometry);
    expect(context.trackPrecisions.get(7)).toBe(10);
    expect(context.gpsTracksById.get(7)?.version).toBe(2);
  });

  it('does not clear the current background-loading state when a superseded request finishes', async () => {
    const methods = makeMethods();
    const resolves: Array<(data: unknown) => void> = [];
    const deferredCollection = () =>
      new Promise((resolve) => {
        resolves.push(resolve);
      });
    trackCollectionMocks.loadTrackCollectionPaged
      .mockImplementationOnce(deferredCollection)
      .mockImplementationOnce(deferredCollection);
    const context = {
      bulk10mController: null,
      loadingTracks10m: false,
      applyBackgroundTrackPage: vi.fn(),
    };

    const first = methods.loadAllTracksAt10m.call(context as never);
    const second = methods.loadAllTracksAt10m.call(context as never);
    resolves[0]!({});
    await first;
    expect(context.loadingTracks10m).toBe(true);
    resolves[1]!({});
    await second;
    expect(context.loadingTracks10m).toBe(false);
    expect(context.applyBackgroundTrackPage).toHaveBeenCalledOnce();
  });

  it('prunes tracks absent from the fresh result while keeping unchanged finer geometry', async () => {
    const methods = makeMethods();
    const keptFeature = {
      properties: { id: 7 },
      geometry: {
        type: 'LineString',
        coordinates: [
          [7, 46],
          [8, 47],
        ],
      },
    };
    const removedFeature = { properties: { id: 8 }, geometry: { type: 'Point', coordinates: [8, 47] } };
    const incomingFeature = { properties: { id: 7 }, geometry: { type: 'Point', coordinates: [7, 46] } };
    const context = {
      cachedTracksLoaded: true,
      geojson: { features: [keptFeature, removedFeature] },
      gpsTrackIdToFeature: new Map<number, typeof keptFeature | typeof removedFeature>([
        [7, keptFeature],
        [8, removedFeature],
      ]),
      gpsTracksById: new Map([
        [7, { id: 7, version: 1 }],
        [8, { id: 8, version: 1 }],
      ]),
      trackPrecisions: new Map([
        [7, 1],
        [8, 1000],
      ]),
      mergeTrackFeatures: methods.mergeTrackFeatures,
      publishGpsTrackMetadataChanges: vi.fn(),
      updateTrackStyle: vi.fn().mockResolvedValue(undefined),
      updateTracksSource: vi.fn(),
    };

    await methods.mergeTrackResult.call(
      context as never,
      {
        geojson: { features: [incomingFeature] },
        gpsTrackIdToFeature: new Map([[7, incomingFeature]]),
        gpsTracksById: new Map([[7, { id: 7, version: 1 }]]),
        trackPrecisions: new Map([[7, 1000]]),
        standardFilterCount: 1,
      },
      { pruneMissing: true }
    );

    expect(context.geojson.features).toEqual([keptFeature]);
    expect(keptFeature.geometry.type).toBe('LineString');
    expect([...context.gpsTracksById.keys()]).toEqual([7]);
    expect([...context.gpsTrackIdToFeature.keys()]).toEqual([7]);
    expect([...context.trackPrecisions.keys()]).toEqual([7]);
  });

  it.each([false, true])('refreshes successfully with the real freshness store, silent=%s', async (silent) => {
    vi.useFakeTimers();
    const freshnessStore = useDataFreshnessStore();
    freshnessStore.markAppliedToken('old-map');
    useApplicationStatusStore().freshness = { freshnessToken: 'new-map' };
    const statusApi = vi.spyOn(ApplicationStatusControllerApi.prototype, 'getApplicationStatus').mockResolvedValue({
      included: new Set([APPLICATION_STATUS_SECTION.FRESHNESS]),
      freshness: { freshnessToken: 'new-map' },
    });
    const refreshResolvedFilter = vi.fn().mockResolvedValue(undefined);
    const methods = useMapDataLoading({ filterStore: { refreshResolvedFilter }, freshnessStore });
    const data = {
      standardFilterCount: 1,
      geojson: { type: 'FeatureCollection', features: [] },
      gpsTracksById: new Map(),
      gpsTrackIdToFeature: new Map(),
      trackPrecisions: new Map(),
      filterResult: {},
    };
    let resolveCollection!: (result: typeof data) => void;
    trackCollectionMocks.loadTrackCollectionPaged.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveCollection = resolve;
        })
    );
    const context = {
      _mapLoadGeneration: 1,
      _mapReadyPromise: Promise.resolve(true),
      overlayMap: {},
      geojson: null,
      zoom: 10,
      trackPointsDetailsCache: new Map(),
      trackPointsCanonicalCache: new Map(),
      get freshnessReloading() {
        return freshnessStore.reloading;
      },
      get serverFreshnessToken() {
        return freshnessStore.serverFreshnessToken;
      },
      get appliedFreshnessToken() {
        return freshnessStore.appliedToken;
      },
      showLoader: false,
      loadingTrackBatches: false,
      initialLoadDone: false,
      cachedTracksLoaded: false,
      refreshDataFreshness: freshnessStore.refresh,
      clearTrackCacheWhenServerFreshnessChanged: methods.clearTrackCacheWhenServerFreshnessChanged,
      currentCollectionPrecision: methods.currentCollectionPrecision,
      captureAppliedFreshnessToken: methods.captureAppliedFreshnessToken,
      loadMapData: methods.loadMapData,
      onDataFreshnessReload(options?: { silent?: boolean }) {
        return methods.onDataFreshnessReload.call(this as never, options);
      },
      addTracksToMap: vi.fn().mockResolvedValue(undefined),
      maybeLoadBackgroundTracks: vi.fn(),
      scheduleDetailCheck: vi.fn(),
      clearFocusedMediaMarker: vi.fn(),
      $toast: { add: vi.fn() },
    };
    expect(freshnessStore.isOutOfSync).toBe(true);
    const reload = silent
      ? methods.onDataFreshnessReload.call(context as never, { silent: true })
      : methods.onMapFreshnessBrowserReload.call(context as never);
    await vi.advanceTimersByTimeAsync(0);
    expect(trackCollectionMocks.loadTrackCollectionPaged).toHaveBeenCalledOnce();
    expect(freshnessStore.reloading).toBe(true);
    expect(context.showLoader).toBe(true);
    resolveCollection(data);
    await vi.advanceTimersByTimeAsync(STATUS_POLL_INTERVAL_MS);
    await expect(reload).resolves.toBe(true);
    expect(context.geojson).toEqual(data.geojson);
    expect(context.addTracksToMap).toHaveBeenCalledOnce();
    expect(refreshResolvedFilter).toHaveBeenCalledOnce();
    expect(statusApi).toHaveBeenCalledTimes(2);
    expect(context.clearFocusedMediaMarker).toHaveBeenCalledOnce();
    expect(freshnessStore.appliedToken).toBe('new-map');
    expect(getAppliedDataFreshnessToken()).toBe('new-map');
    expect(freshnessStore.isOutOfSync).toBe(false);
    expect(freshnessStore.reloading).toBe(false);
    expect(context.showLoader).toBe(false);
    expect(context.loadingTrackBatches).toBe(false);
    expect(context.initialLoadDone).toBe(true);
    if (silent) expect(context.$toast.add).not.toHaveBeenCalled();
    else {
      expect(context.$toast.add).toHaveBeenCalledOnce();
      expect(context.$toast.add).toHaveBeenCalledWith(
        expect.objectContaining({ severity: 'success', summary: 'Map refreshed' })
      );
    }
  });

  it('does not apply track data when initialization was canceled', async () => {
    const context = {
      _mapReadyPromise: Promise.resolve(false),
      overlayMap: {},
      addTracksToMap: vi.fn(),
      geojson: null,
    };
    await makeMethods().loadMapData.call(context as never, { geojson: {} });
    expect(context.geojson).toBeNull();
    expect(context.addTracksToMap).not.toHaveBeenCalled();
  });

  it.each(['unmounted', 'new-generation'] as const)(
    'does not apply a late manual reload after %s or mark its freshness as displayed',
    async (cancellation) => {
      const freshnessStore = useDataFreshnessStore();
      freshnessStore.markAppliedToken('displayed-map');
      const methods = makeMethods({ refreshResolvedFilter: vi.fn().mockResolvedValue(undefined) });
      let resolveCollection!: (data: unknown) => void;
      trackCollectionMocks.loadTrackCollectionPaged.mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveCollection = resolve;
          })
      );
      const context = {
        _mapLoadGeneration: 1,
        _mapUnmounted: false,
        freshnessReloading: false,
        serverFreshnessToken: 'new-map',
        geojson: { features: [] },
        clearTrackCacheWhenServerFreshnessChanged: vi.fn().mockResolvedValue(false),
        currentCollectionPrecision: vi.fn(() => 1000),
        mergeTrackResult: vi.fn().mockResolvedValue(undefined),
        maybeLoadBackgroundTracks: vi.fn(),
        scheduleDetailCheck: vi.fn(),
        clearFocusedMediaMarker: vi.fn(),
        refreshDataFreshness: vi.fn().mockResolvedValue({ freshnessToken: 'new-map' }),
        captureAppliedFreshnessToken: methods.captureAppliedFreshnessToken,
        $toast: { add: vi.fn() },
      };
      const reload = methods.onDataFreshnessReload.call(context as never);
      await vi.waitFor(() => expect(trackCollectionMocks.loadTrackCollectionPaged).toHaveBeenCalledOnce());
      if (cancellation === 'unmounted') context._mapUnmounted = true;
      else context._mapLoadGeneration++;
      resolveCollection({ standardFilterCount: 1, geojson: { features: [] } });
      await expect(reload).resolves.toBe(false);
      expect(context.mergeTrackResult).not.toHaveBeenCalled();
      expect(freshnessStore.appliedToken).toBe('displayed-map');
      expect(context.clearFocusedMediaMarker).not.toHaveBeenCalled();
      expect(context.$toast.add).not.toHaveBeenCalled();
      expect(freshnessStore.reloading).toBe(false);
    }
  );

  it('does not mark freshness or report success after leaving while visible media refreshes', async () => {
    const freshnessStore = useDataFreshnessStore();
    freshnessStore.markAppliedToken('displayed-map');
    const methods = makeMethods({ refreshResolvedFilter: vi.fn().mockResolvedValue(undefined) });
    trackCollectionMocks.loadTrackCollectionPaged.mockResolvedValueOnce({
      standardFilterCount: 1,
      geojson: { features: [] },
    });
    let resolveMedia!: () => void;
    const refreshMedia = vi.fn(
      () =>
        new Promise<void>((resolve) => {
          resolveMedia = resolve;
        })
    );
    const context = {
      _mapLoadGeneration: 1,
      _mapUnmounted: false,
      freshnessReloading: false,
      serverFreshnessToken: 'new-map',
      geojson: { features: [] },
      clearTrackCacheWhenServerFreshnessChanged: vi.fn().mockResolvedValue(false),
      currentCollectionPrecision: vi.fn(() => 1000),
      mergeTrackResult: vi.fn().mockResolvedValue(undefined),
      maybeLoadBackgroundTracks: vi.fn(),
      scheduleDetailCheck: vi.fn(),
      mediaOverlay: { isVisible: vi.fn(() => true), refresh: refreshMedia },
      clearFocusedMediaMarker: vi.fn(),
      refreshDataFreshness: vi.fn().mockResolvedValue({ freshnessToken: 'new-map' }),
      captureAppliedFreshnessToken: methods.captureAppliedFreshnessToken,
      $toast: { add: vi.fn() },
    };
    const reload = methods.onDataFreshnessReload.call(context as never);
    await vi.waitFor(() => expect(refreshMedia).toHaveBeenCalledOnce());
    context._mapUnmounted = true;
    resolveMedia();
    await expect(reload).resolves.toBe(false);
    expect(freshnessStore.appliedToken).toBe('displayed-map');
    expect(context.clearFocusedMediaMarker).not.toHaveBeenCalled();
    expect(context.$toast.add).not.toHaveBeenCalled();
    expect(freshnessStore.reloading).toBe(false);
  });

  it('does not apply track data to a newer reload after waiting on the old map', async () => {
    let ready!: (value: boolean) => void;
    const context = {
      _mapReadyPromise: new Promise<boolean>((resolve) => {
        ready = resolve;
      }),
      overlayMap: {},
      addTracksToMap: vi.fn(),
      geojson: null,
    };
    const pending = makeMethods().loadMapData.call(context as never, { geojson: {} });
    context._mapReadyPromise = Promise.resolve(true);
    ready(true);
    await pending;
    expect(context.geojson).toBeNull();
    expect(context.addTracksToMap).not.toHaveBeenCalled();
  });

  it('does not start track loading after disposal during the authentication probe', async () => {
    let resolveAuth!: (status: string) => void;
    checkServerAuthMock.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveAuth = resolve;
        })
    );
    const context = {
      _mapLoadGeneration: 1,
      $emit: vi.fn(),
      clearTrackCacheWhenServerFreshnessChanged: vi.fn(),
    };
    const pending = makeMethods().fetchTracksAndFallback.call(context as never);
    context._mapLoadGeneration += 1;
    resolveAuth('ok');
    await pending;
    expect(context.clearTrackCacheWhenServerFreshnessChanged).not.toHaveBeenCalled();
  });

  it('checks freshness and displays cached tracks before starting background reconciliation and sync', async () => {
    const methods = makeMethods();
    const cached = { geojson: { features: [{ type: 'Feature', geometry: null, properties: {} }] } };
    checkServerAuthMock.mockResolvedValueOnce('ok');
    trackCollectionMocks.isTrackCachePopulated.mockResolvedValueOnce(true);
    trackCollectionMocks.loadCachedTrackCollection.mockResolvedValueOnce(cached);
    const context = {
      serverFreshnessToken: 'current',
      appliedFreshnessToken: 'current',
      refreshDataFreshness: vi.fn().mockResolvedValue({ freshnessToken: 'current' }),
      clearTrackCacheWhenServerFreshnessChanged: methods.clearTrackCacheWhenServerFreshnessChanged,
      loadMapData: vi.fn().mockResolvedValue(undefined),
      $emit: vi.fn(),
      _backgroundSync: vi.fn(),
    };

    await methods.fetchTracksAndFallback.call(context as never);

    expect(context.refreshDataFreshness).toHaveBeenCalledWith({ immediate: true });
    expect(context.refreshDataFreshness.mock.invocationCallOrder[0]).toBeLessThan(
      trackCollectionMocks.loadCachedTrackCollection.mock.invocationCallOrder[0]
    );
    expect(trackCollectionMocks.reconcileTrackCache).not.toHaveBeenCalled();
    expect(context.loadMapData.mock.invocationCallOrder[0]).toBeLessThan(
      context._backgroundSync.mock.invocationCallOrder[0]
    );
    expect(context.loadMapData).toHaveBeenCalledWith(cached);
    expect(context.$emit).toHaveBeenCalledWith('tracks-loaded');
  });

  it('acknowledges only the revision observed before loading when the server changes during reload', async () => {
    const freshnessStore = useDataFreshnessStore();
    const markAppliedToken = vi.spyOn(freshnessStore, 'markAppliedToken');
    const methods = useMapDataLoading({
      filterStore: { refreshResolvedFilter: vi.fn().mockResolvedValue(undefined) },
      freshnessStore,
    });
    const context = {
      freshnessReloading: false,
      serverFreshnessToken: 'before-load',
      geojson: { features: [] },
      clearTrackCacheWhenServerFreshnessChanged: vi.fn().mockResolvedValue(false),
      currentCollectionPrecision: vi.fn(() => 1000),
      mergeTrackResult: vi.fn().mockResolvedValue(undefined),
      maybeLoadBackgroundTracks: vi.fn(),
      scheduleDetailCheck: vi.fn(),
      clearFocusedMediaMarker: vi.fn(),
      refreshDataFreshness: vi.fn().mockResolvedValue({ freshnessToken: 'during-load' }),
      captureAppliedFreshnessToken: methods.captureAppliedFreshnessToken,
      isIndexing: true,
      $toast: { add: vi.fn() },
    };
    trackCollectionMocks.loadTrackCollectionPaged.mockImplementationOnce(async () => {
      context.serverFreshnessToken = 'during-load';
      return { standardFilterCount: 3, filterResult: {}, geojson: { features: [] } };
    });
    expect(await methods.onDataFreshnessReload.call(context as never)).toBe(true);
    expect(markAppliedToken).toHaveBeenCalledWith('before-load');
    expect(markAppliedToken).not.toHaveBeenCalledWith('during-load');
    expect(context.$toast.add).toHaveBeenCalledWith(
      expect.objectContaining({
        detail: 'Latest available data loaded. Preparation is still running.',
      })
    );
  });

  it('captures the revision before recovering cached offline data', async () => {
    const methods = makeMethods();
    const captureAppliedFreshnessToken = vi.fn();
    const context = {
      retryCount: 1,
      totalTrackCount: 0,
      geojson: { features: [] },
      refreshDataFreshness: vi.fn().mockResolvedValue({ freshnessToken: 'recovery-start' }),
      currentCollectionPrecision: vi.fn(() => 1000),
      mergeTrackResult: vi.fn().mockResolvedValue(undefined),
      maybeLoadBackgroundTracks: vi.fn(),
      scheduleDetailCheck: vi.fn(),
      clearFocusedMediaMarker: vi.fn(),
      captureAppliedFreshnessToken,
      scheduleRetry: vi.fn(),
      $toast: { add: vi.fn() },
    };
    trackCollectionMocks.loadTrackCollectionPaged.mockResolvedValueOnce({
      standardFilterCount: 3,
      filterResult: {},
      geojson: { features: [] },
    });
    await methods.performBackgroundRetry.call(context as never);
    expect(captureAppliedFreshnessToken).toHaveBeenCalledWith('recovery-start');
    expect(context.retryCount).toBe(0);
    expect(context.totalTrackCount).toBe(3);
    expect(context.scheduleRetry).not.toHaveBeenCalled();
  });

  it('refreshes the shared filter result before loading the fresh map collection', async () => {
    const refreshResolvedFilter = vi.fn().mockResolvedValue(undefined);
    const methods = makeMethods({ refreshResolvedFilter });
    trackCollectionMocks.loadTrackCollectionPaged.mockResolvedValueOnce({
      standardFilterCount: 3,
      filterResult: {
        trackVersions: new Map([
          [1, 1],
          [2, 1],
          [3, 1],
        ]),
        filterGroups: new Map(),
        standardFilterCount: 3,
      },
      geojson: { features: [] },
    });
    const context = {
      freshnessReloading: false,
      showLoader: false,
      loadingTrackBatches: false,
      cachedTracksLoaded: true,
      initialLoadDone: true,
      geojson: { features: [] },
      clearTrackCacheWhenServerFreshnessChanged: vi.fn().mockResolvedValue(true),
      currentCollectionPrecision: vi.fn(() => 1000),
      mergeTrackResult: vi.fn().mockResolvedValue(undefined),
      maybeLoadBackgroundTracks: vi.fn(),
      scheduleDetailCheck: vi.fn(),
      clearFocusedMediaMarker: vi.fn(),
      captureAppliedFreshnessToken: vi.fn().mockResolvedValue(undefined),
      $toast: { add: vi.fn() },
    };

    const result = await methods.onDataFreshnessReload.call(context as never);

    expect(result).toBe(true);
    expect(refreshResolvedFilter).toHaveBeenCalledTimes(1);
    expect(trackCollectionMocks.loadTrackCollectionPaged).toHaveBeenCalledTimes(1);
    expect(refreshResolvedFilter.mock.invocationCallOrder[0]).toBeLessThan(
      trackCollectionMocks.loadTrackCollectionPaged.mock.invocationCallOrder[0]
    );
  });

  it('refreshes visible media before marking the freshness token as applied', async () => {
    const methods = makeMethods({ refreshResolvedFilter: vi.fn().mockResolvedValue(undefined) });
    trackCollectionMocks.loadTrackCollectionPaged.mockResolvedValueOnce({
      standardFilterCount: 1,
      filterResult: { trackVersions: new Map(), filterGroups: new Map(), standardFilterCount: 1 },
      geojson: { features: [] },
    });
    const refreshMedia = vi.fn().mockResolvedValue(undefined);
    const clearFocusedMediaMarker = vi.fn();
    const captureAppliedFreshnessToken = vi.fn().mockResolvedValue(undefined);
    const context = {
      freshnessReloading: false,
      showLoader: false,
      loadingTrackBatches: false,
      cachedTracksLoaded: true,
      initialLoadDone: true,
      geojson: { features: [] },
      mediaOverlay: { isVisible: vi.fn(() => true), refresh: refreshMedia },
      clearFocusedMediaMarker,
      clearTrackCacheWhenServerFreshnessChanged: vi.fn().mockResolvedValue(true),
      currentCollectionPrecision: vi.fn(() => 1000),
      mergeTrackResult: vi.fn().mockResolvedValue(undefined),
      maybeLoadBackgroundTracks: vi.fn(),
      scheduleDetailCheck: vi.fn(),
      captureAppliedFreshnessToken,
      $toast: { add: vi.fn() },
    };

    const result = await methods.onDataFreshnessReload.call(context as never);

    expect(result).toBe(true);
    expect(refreshMedia).toHaveBeenCalledTimes(1);
    expect(clearFocusedMediaMarker).toHaveBeenCalledTimes(1);
    expect(refreshMedia.mock.invocationCallOrder[0]).toBeLessThan(clearFocusedMediaMarker.mock.invocationCallOrder[0]);
    expect(clearFocusedMediaMarker.mock.invocationCallOrder[0]).toBeLessThan(
      captureAppliedFreshnessToken.mock.invocationCallOrder[0]
    );
  });

  it('keeps freshness unapplied when visible media cannot be refreshed', async () => {
    const methods = makeMethods({ refreshResolvedFilter: vi.fn().mockResolvedValue(undefined) });
    trackCollectionMocks.loadTrackCollectionPaged.mockResolvedValueOnce({
      standardFilterCount: 1,
      filterResult: { trackVersions: new Map(), filterGroups: new Map(), standardFilterCount: 1 },
      geojson: { features: [] },
    });
    const captureAppliedFreshnessToken = vi.fn().mockResolvedValue(undefined);
    const clearFocusedMediaMarker = vi.fn();
    const context = {
      freshnessReloading: false,
      showLoader: false,
      loadingTrackBatches: false,
      cachedTracksLoaded: true,
      initialLoadDone: true,
      geojson: { features: [] },
      mediaOverlay: {
        isVisible: vi.fn(() => true),
        refresh: vi.fn().mockRejectedValue(new Error('media unavailable')),
      },
      clearFocusedMediaMarker,
      clearTrackCacheWhenServerFreshnessChanged: vi.fn().mockResolvedValue(true),
      currentCollectionPrecision: vi.fn(() => 1000),
      mergeTrackResult: vi.fn().mockResolvedValue(undefined),
      maybeLoadBackgroundTracks: vi.fn(),
      scheduleDetailCheck: vi.fn(),
      captureAppliedFreshnessToken,
      $toast: { add: vi.fn() },
    };

    const result = await methods.onDataFreshnessReload.call(context as never);

    expect(result).toBe(false);
    expect(clearFocusedMediaMarker).not.toHaveBeenCalled();
    expect(captureAppliedFreshnessToken).not.toHaveBeenCalled();
    expect(context.$toast.add).toHaveBeenCalledWith(expect.objectContaining({ summary: 'Reload failed' }));
  });

  it('does not fetch media when the media layer is hidden', async () => {
    const methods = makeMethods({ refreshResolvedFilter: vi.fn().mockResolvedValue(undefined) });
    trackCollectionMocks.loadTrackCollectionPaged.mockResolvedValueOnce({
      standardFilterCount: 1,
      filterResult: { trackVersions: new Map(), filterGroups: new Map(), standardFilterCount: 1 },
      geojson: { features: [] },
    });
    const refreshMedia = vi.fn().mockResolvedValue(undefined);
    const clearFocusedMediaMarker = vi.fn();
    const context = {
      freshnessReloading: false,
      showLoader: false,
      loadingTrackBatches: false,
      cachedTracksLoaded: true,
      initialLoadDone: true,
      geojson: { features: [] },
      mediaOverlay: { isVisible: vi.fn(() => false), refresh: refreshMedia },
      clearFocusedMediaMarker,
      clearTrackCacheWhenServerFreshnessChanged: vi.fn().mockResolvedValue(true),
      currentCollectionPrecision: vi.fn(() => 1000),
      mergeTrackResult: vi.fn().mockResolvedValue(undefined),
      maybeLoadBackgroundTracks: vi.fn(),
      scheduleDetailCheck: vi.fn(),
      captureAppliedFreshnessToken: vi.fn().mockResolvedValue(undefined),
      $toast: { add: vi.fn() },
    };

    const result = await methods.onDataFreshnessReload.call(context as never);

    expect(result).toBe(true);
    expect(refreshMedia).not.toHaveBeenCalled();
    expect(clearFocusedMediaMarker).toHaveBeenCalledTimes(1);
  });

  it('routes the banner Reload button through the in-app freshness reload', async () => {
    const methods = makeMethods();
    const context = {
      onDataFreshnessReload: vi.fn().mockResolvedValue(true),
    };
    const reloadFromBanner = methods.onMapFreshnessBrowserReload as unknown as BannerReloadMethod;

    const result = await reloadFromBanner.call(context);

    expect(result).toBe(true);
    expect(context.onDataFreshnessReload).toHaveBeenCalledTimes(1);
  });

  it('reports Admin Freshness refresh failures from the in-app freshness reload', async () => {
    const methods = makeMethods();
    const done = vi.fn();
    const context = {
      onDataFreshnessReload: vi.fn().mockResolvedValue(false),
    };
    const refreshFromAdmin = methods.onAdminRefreshFreshnessData as unknown as AdminRefreshMethod;

    await refreshFromAdmin.call(context, done);

    expect(context.onDataFreshnessReload).toHaveBeenCalledTimes(1);
    expect(done).toHaveBeenCalledWith(false);
  });

  it.each([true, false])(
    'Maintenance Reload uses the complete data refresh and reports success=%s',
    async (success) => {
      const methods = makeMethods();
      const done = vi.fn();
      const context = {
        onDataFreshnessReload: vi.fn().mockResolvedValue(success),
        fetchTracksAndFallback: vi.fn().mockResolvedValue(undefined),
      };

      await methods.onAdminReloadTracks.call(context as never, done);

      expect(context.onDataFreshnessReload).toHaveBeenCalledTimes(1);
      expect(context.fetchTracksAndFallback).not.toHaveBeenCalled();
      expect(done).toHaveBeenCalledWith(success);
    }
  );
});
