import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useMapDataLoading } from '@/components/map/composables/useMapDataLoading';
import { rebuildMapLayers } from '@/components/map/composables/useMapLayerSettings';

const mocks = vi.hoisted(() => ({
  applyTrackFilter: vi.fn(),
  isTrackCachePopulated: vi.fn(),
  loadCachedTrackCollection: vi.fn(),
  loadTrackCollectionPaged: vi.fn(),
  reconcileTrackCache: vi.fn().mockResolvedValue(undefined),
  checkServerAuth: vi.fn(),
}));

vi.mock('@/layers/GeoDrawingOverlay', () => ({ GeoDrawingOverlay: vi.fn() }));
vi.mock('@/utils/ServiceHelper', () => ({
  checkServerAuth: mocks.checkServerAuth,
  fetchTrackCanonicalPoints: vi.fn(),
  fetchTrackPointsForRenderedShape: vi.fn(),
}));
vi.mock('@/utils/auth', () => ({
  getToken: vi.fn(),
  isAuthError: vi.fn(() => false),
  redirectToLoginAfterAuthFailure: vi.fn(),
}));
vi.mock('@/utils/tracks/trackCollectionLoader', () => ({
  ...mocks,
  clearTrackCache: vi.fn(),
  fetchDetailTrackAtPrecision: vi.fn(),
}));
vi.mock('@/utils/mapConfigService', () => ({ clearMapConfigCache: vi.fn() }));
vi.mock('@/components/map/mapGeometry', () => ({
  collectionPrecisionForZoom: vi.fn(() => 1000),
  haversineDistance: vi.fn(),
  isSameOrBetterPrecision: vi.fn(() => true),
  precisionForZoom: vi.fn(() => 1000),
}));
vi.mock('@/utils/startupDiagnostics', () => ({
  startStartupTimer: vi.fn(() => ({ success: vi.fn(), warn: vi.fn(), error: vi.fn() })),
  startupLog: vi.fn(),
  startupWarn: vi.fn(),
}));
vi.mock('@/utils/safeLogging', () => ({ warnSanitized: vi.fn() }));

function makeResult(count: number) {
  const ids = Array.from({ length: count }, (_, index) => index + 1);
  const features = ids.map((id) => ({
    type: 'Feature',
    properties: { id },
    geometry: {
      type: 'LineString',
      coordinates: [
        [8, 47],
        [8.01, 47],
      ],
    },
  }));
  return {
    geojson: { type: 'FeatureCollection', features },
    gpsTracksById: new Map(ids.map((id) => [id, { id }])),
    gpsTrackIdToFeature: new Map(features.map((feature) => [feature.properties.id, feature])),
    trackPrecisions: new Map(ids.map((id) => [id, 1000])),
    standardFilterCount: 1042,
    filterResult: {
      trackVersions: new Map(ids.map((id) => [id, 1])),
      filterGroups: new Map<number, string>(),
      standardFilterCount: 1042,
    },
  };
}

type TestResult = ReturnType<typeof makeResult>;

function setup(cachePopulated = true) {
  const initial = makeResult(12);
  const filterStore = { activeResult: initial.filterResult };
  const methods = useMapDataLoading({ filterStore, freshnessStore: {} });
  let finish!: (result: TestResult) => void;
  let fail!: (error: Error) => void;
  const pending = new Promise<TestResult>((resolve, reject) => {
    finish = resolve;
    fail = reject;
  });
  mocks.checkServerAuth.mockResolvedValue('ok');
  mocks.isTrackCachePopulated.mockResolvedValue(cachePopulated);
  mocks.loadCachedTrackCollection.mockResolvedValue(initial);
  mocks.loadTrackCollectionPaged.mockReturnValue(pending);
  mocks.applyTrackFilter.mockImplementation(async ({ filterResult }: { filterResult: TestResult['filterResult'] }) =>
    makeResult(filterResult.trackVersions.size)
  );

  let backgroundSync: Promise<void> | undefined;
  const context = {
    ...methods,
    _mapLoadGeneration: 0,
    _mapUnmounted: false,
    _mapReadyPromise: null as Promise<boolean> | null,
    _mapReadyResolve: null as ((ready: boolean) => void) | null,
    overlayMap: {},
    cachedTracksLoaded: false,
    initialLoadDone: false,
    showLoader: true,
    loadingTrackBatches: false,
    hiddenGroups: new Set<string>(),
    activeToolId: null,
    selectedTrackId: null as number | null,
    selectedFeature: null as TestResult['geojson']['features'][number] | null,
    trackDetailsVisible: false,
    endMapInteraction: vi.fn(),
    ...makeResult(0),
    visibleTrackCount: 0,
    totalTrackCount: 0,
    clearTrackCacheWhenServerFreshnessChanged: vi.fn().mockResolvedValue(false),
    refreshDataFreshness: vi.fn().mockResolvedValue({ freshnessToken: 'unchanged' }),
    captureAppliedFreshnessToken: vi.fn().mockResolvedValue(undefined),
    currentCollectionPrecision: vi.fn(() => 1000),
    initMap: vi.fn(async (preserveTrackLoad = false) => {
      if (!preserveTrackLoad) context._mapLoadGeneration += 1;
      return true;
    }),
    addTracksToMap: vi.fn().mockResolvedValue(undefined),
    closeSelectionPopup: vi.fn(),
    updateTracksSource: vi.fn(),
    maybeLoadBackgroundTracks: vi.fn(),
    publishGpsTrackMetadataChanges: vi.fn(),
    scheduleDetailCheck: vi.fn(),
    scheduleRetry: vi.fn(),
    fitToTrackBounds: vi.fn(),
    $emit: vi.fn(),
    $toast: { add: vi.fn() },
    loadMapData: vi.fn(async (result: TestResult) => {
      await methods.loadMapData.call(context as never, result);
      context.visibleTrackCount = context.gpsTracksById.size;
    }),
    mergeTrackResult: vi.fn(async (result: TestResult) => applySnapshot(result)),
  };

  function applySnapshot(result: TestResult) {
    context.geojson = result.geojson;
    context.gpsTracksById = result.gpsTracksById;
    context.gpsTrackIdToFeature = result.gpsTrackIdToFeature;
    context.trackPrecisions = result.trackPrecisions;
    context.visibleTrackCount = result.gpsTracksById.size;
    context.totalTrackCount = result.standardFilterCount;
  }

  context._backgroundSync = (timer) => {
    backgroundSync = methods._backgroundSync.call(context as never, timer);
    return backgroundSync;
  };

  return {
    context,
    filterStore,
    initial,
    finish,
    fail,
    start: () => methods.fetchTracksAndFallback.call(context as never),
    applyFilter: () => methods.onFilterApplied.call(context as never),
    get backgroundSync() {
      return backgroundSync;
    },
  };
}

describe('filter changes during map startup', () => {
  beforeEach(() => vi.clearAllMocks());

  it('applies only the latest filter after a cached reload finishes', async () => {
    const probe = setup();
    await probe.start();
    await vi.waitFor(() => expect(mocks.loadTrackCollectionPaged).toHaveBeenCalledOnce());
    expect(probe.context.visibleTrackCount).toBe(12);
    expect(probe.context.initialLoadDone).toBe(false);

    for (const count of [15, 23]) {
      probe.filterStore.activeResult = makeResult(count).filterResult;
      await probe.applyFilter();
    }
    expect(mocks.applyTrackFilter).not.toHaveBeenCalled();
    probe.finish(probe.initial);
    await probe.backgroundSync;

    expect(probe.context.initialLoadDone).toBe(true);
    expect(probe.context.visibleTrackCount).toBe(23);
    expect(probe.context.geojson.features).toHaveLength(23);
    expect(mocks.applyTrackFilter).toHaveBeenCalledExactlyOnceWith({ filterResult: probe.filterStore.activeResult });
  });

  it('retains a route selected while a startup filter is waiting, using its refreshed feature', async () => {
    const probe = setup(false);
    const startup = probe.start();
    await vi.waitFor(() => expect(mocks.loadTrackCollectionPaged).toHaveBeenCalledOnce());
    await probe.applyFilter();
    probe.context.selectedTrackId = 1;
    probe.context.selectedFeature = probe.initial.gpsTrackIdToFeature.get(1)!;
    probe.context.trackDetailsVisible = true;
    probe.finish(probe.initial);
    await startup;

    expect(probe.context.selectedTrackId).toBe(1);
    expect(probe.context.selectedFeature).toBe(probe.context.gpsTrackIdToFeature.get(1));
    expect(probe.context.trackDetailsVisible).toBe(true);
    expect(probe.context.endMapInteraction).not.toHaveBeenCalled();
  });

  it('closes a startup selection when the resulting filter excludes its route', async () => {
    const probe = setup(false);
    const startup = probe.start();
    await vi.waitFor(() => expect(mocks.loadTrackCollectionPaged).toHaveBeenCalledOnce());
    probe.filterStore.activeResult = makeResult(0).filterResult;
    await probe.applyFilter();
    probe.context.selectedTrackId = 1;
    probe.context.selectedFeature = probe.initial.gpsTrackIdToFeature.get(1)!;
    probe.context.trackDetailsVisible = true;
    probe.finish(probe.initial);
    await startup;

    expect(probe.context.selectedTrackId).toBeNull();
    expect(probe.context.selectedFeature).toBeNull();
    expect(probe.context.trackDetailsVisible).toBe(false);
    expect(probe.context.endMapInteraction).toHaveBeenCalledWith(true);
  });

  it('still clears a selection for an explicit filter applied after startup', async () => {
    const probe = setup();
    probe.context.initialLoadDone = true;
    probe.context.selectedTrackId = 1;
    probe.context.selectedFeature = probe.initial.gpsTrackIdToFeature.get(1)!;
    await probe.applyFilter();
    expect(probe.context.selectedTrackId).toBeNull();
    expect(probe.context.selectedFeature).toBeNull();
  });

  it('applies a queued filter after background synchronization fails', async () => {
    const probe = setup();
    await probe.start();
    await vi.waitFor(() => expect(mocks.loadTrackCollectionPaged).toHaveBeenCalledOnce());
    probe.filterStore.activeResult = makeResult(23).filterResult;
    await probe.applyFilter();
    probe.fail(new Error('Synthetic startup sync failure'));
    await probe.backgroundSync;

    expect(probe.context.visibleTrackCount).toBe(23);
    expect(probe.context.geojson.features).toHaveLength(23);
    expect(mocks.applyTrackFilter).toHaveBeenCalledOnce();
  });

  it('preserves a filter change during startup without cache', async () => {
    const probe = setup(false);
    const startup = probe.start();
    await vi.waitFor(() => expect(mocks.loadTrackCollectionPaged).toHaveBeenCalledOnce());
    probe.filterStore.activeResult = makeResult(23).filterResult;
    await probe.applyFilter();
    probe.finish(probe.initial);
    await startup;

    expect(probe.context.visibleTrackCount).toBe(23);
    expect(probe.context.geojson.features).toHaveLength(23);
    expect(mocks.applyTrackFilter).toHaveBeenCalledOnce();
  });

  it('applies a queued filter after startup falls back to cached tracks', async () => {
    const probe = setup(false);
    const startup = probe.start();
    await vi.waitFor(() => expect(mocks.loadTrackCollectionPaged).toHaveBeenCalledOnce());
    probe.filterStore.activeResult = makeResult(23).filterResult;
    await probe.applyFilter();
    probe.fail(new Error('Synthetic startup load failure'));
    await startup;

    expect(probe.context.visibleTrackCount).toBe(23);
    expect(probe.context.geojson.features).toHaveLength(23);
    expect(mocks.applyTrackFilter).toHaveBeenCalledOnce();
  });

  it.each([true, false])(
    'finishes startup and applies a queued filter after rebuilding layers (cache: %s)',
    async (cached) => {
      const probe = setup(cached);
      const startup = probe.start();
      await vi.waitFor(() => expect(mocks.loadTrackCollectionPaged).toHaveBeenCalledOnce());
      probe.filterStore.activeResult = makeResult(23).filterResult;
      await probe.applyFilter();

      const rebuild = rebuildMapLayers(probe.context as never);
      await vi.waitFor(() => expect(mocks.loadTrackCollectionPaged).toHaveBeenCalledTimes(2));
      probe.finish(probe.initial);
      await startup;
      await rebuild;
      await probe.backgroundSync;

      expect(probe.context.initialLoadDone).toBe(true);
      expect(probe.context.loadingTrackBatches).toBe(false);
      expect(probe.context.$emit.mock.calls.filter(([event]) => event === 'syncing').at(-1)).toEqual([
        'syncing',
        false,
      ]);
      expect(probe.context.visibleTrackCount).toBe(23);
      expect(mocks.applyTrackFilter).toHaveBeenCalledExactlyOnceWith({ filterResult: probe.filterStore.activeResult });
    }
  );

  it('clears batch loading when partial pages populate the cache before the rebuild', async () => {
    const probe = setup(false);
    const startup = probe.start();
    await vi.waitFor(() => expect(mocks.loadTrackCollectionPaged).toHaveBeenCalledOnce());
    expect(probe.context.loadingTrackBatches).toBe(true);
    mocks.isTrackCachePopulated.mockResolvedValue(true);

    const rebuild = rebuildMapLayers(probe.context as never);
    await vi.waitFor(() => expect(mocks.loadTrackCollectionPaged).toHaveBeenCalledTimes(2));
    probe.finish(probe.initial);
    await Promise.all([startup, rebuild]);
    await probe.backgroundSync;

    expect(probe.context.loadingTrackBatches).toBe(false);
    expect(probe.context.initialLoadDone).toBe(true);
    expect(probe.context.$emit.mock.calls.filter(([event]) => event === 'syncing').at(-1)).toEqual(['syncing', false]);
  });

  it('replaces a canceled readiness gate before rendering the restarted cache load', async () => {
    const probe = setup();
    probe.context._mapReadyPromise = Promise.resolve(false);
    const rebuild = rebuildMapLayers(probe.context as never);
    await vi.waitFor(() => expect(mocks.loadTrackCollectionPaged).toHaveBeenCalledOnce());
    expect(probe.context.gpsTracksById.size).toBe(12);
    probe.finish(probe.initial);
    await rebuild;
    await probe.backgroundSync;
    expect(probe.context._mapReadyPromise).toBeNull();
  });

  it('does not let a superseded rebuild restart loading or hide the current loader', async () => {
    const probe = setup(false);
    let finishObsolete!: (ready: boolean) => void;
    let finishCurrent!: (ready: boolean) => void;
    probe.context.initMap
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            finishObsolete = resolve;
          })
      )
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            finishCurrent = resolve;
          })
      );
    const obsolete = rebuildMapLayers(probe.context as never);
    const current = rebuildMapLayers(probe.context as never);
    const currentGate = probe.context._mapReadyPromise;

    finishObsolete(false);
    await obsolete;
    expect(probe.context.showLoader).toBe(true);
    expect(probe.context._mapReadyPromise).toBe(currentGate);
    expect(mocks.checkServerAuth).not.toHaveBeenCalled();

    finishCurrent(true);
    await vi.waitFor(() => expect(mocks.loadTrackCollectionPaged).toHaveBeenCalledOnce());
    probe.finish(probe.initial);
    await current;
    expect(probe.context.initialLoadDone).toBe(true);
    expect(probe.context._mapReadyPromise).toBeNull();
  });

  it('does not restart loading after unmounting during a rebuild', async () => {
    const probe = setup();
    let finish!: (ready: boolean) => void;
    probe.context.initMap.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          finish = resolve;
        })
    );
    const rebuild = rebuildMapLayers(probe.context as never);
    const gate = probe.context._mapReadyPromise;
    probe.context._mapUnmounted = true;
    finish(false);
    await rebuild;
    await expect(gate).resolves.toBe(false);
    expect(mocks.checkServerAuth).not.toHaveBeenCalled();
    expect(probe.context.addTracksToMap).not.toHaveBeenCalled();
  });

  it('does not publish a canceled first page after its readiness wait', async () => {
    const probe = setup(false);
    let ready!: (value: boolean) => void;
    probe.context._mapReadyPromise = new Promise((resolve) => {
      ready = resolve;
    });
    const page = probe.context.mergeTrackPage(probe.initial);
    probe.context._mapLoadGeneration += 1;
    ready(false);
    await page;
    expect(probe.context.cachedTracksLoaded).toBe(false);
    expect(probe.context.showLoader).toBe(true);
    expect(probe.context.$emit).not.toHaveBeenCalled();
  });

  it('does not publish a canceled cached fallback after its readiness wait', async () => {
    const probe = setup(false);
    let ready!: (value: boolean) => void;
    probe.context._mapReadyPromise = new Promise((resolve) => {
      ready = resolve;
    });
    const fallback = probe.context.showCachedTrackFallback(probe.initial, 'Synthetic fallback', 'Synthetic render');
    probe.context._mapLoadGeneration += 1;
    ready(false);
    await fallback;
    expect(probe.context.cachedTracksLoaded).toBe(false);
    expect(probe.context.showLoader).toBe(true);
    expect(probe.context.$emit).not.toHaveBeenCalled();
    expect(probe.context.fitToTrackBounds).not.toHaveBeenCalled();
  });
});
