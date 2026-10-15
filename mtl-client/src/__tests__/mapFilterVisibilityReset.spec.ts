import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useMapDataLoading } from '@/components/map/composables/useMapDataLoading';
import { TrackLoadSupersededError } from '@/utils/errors';

const { applyTrackFilterMock, loadTrackCollectionPagedMock } = vi.hoisted(() => ({
  applyTrackFilterMock: vi.fn(),
  loadTrackCollectionPagedMock: vi.fn(),
}));

vi.mock('@/layers/GeoDrawingOverlay', () => ({
  GeoDrawingOverlay: vi.fn(),
}));

vi.mock('@/utils/ServiceHelper', () => ({
  checkServerAuth: vi.fn(),
  fetchTrackCanonicalPoints: vi.fn(),
  fetchTrackPointsForRenderedShape: vi.fn(),
}));

vi.mock('@/utils/auth', () => ({
  getToken: vi.fn(),
  isAuthError: vi.fn(() => false),
  redirectToLoginAfterAuthFailure: vi.fn(),
}));

vi.mock('@/utils/tracks/trackCollectionLoader', () => ({
  applyTrackFilter: applyTrackFilterMock,
  clearTrackCache: vi.fn(),
  fetchDetailTrackAtPrecision: vi.fn(),
  isTrackCachePopulated: vi.fn(),
  loadCachedTrackCollection: vi.fn(),
  loadTrackCollectionPaged: loadTrackCollectionPagedMock,
}));

vi.mock('@/utils/mapConfigService', () => ({
  clearMapConfigCache: vi.fn(),
}));

vi.mock('@/components/map/mapGeometry', () => ({
  collectionPrecisionForZoom: vi.fn(() => 1000),
  haversineDistance: vi.fn(),
  isSameOrBetterPrecision: vi.fn(() => true),
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

describe('map filter visibility reset', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    applyTrackFilterMock.mockReset();
    loadTrackCollectionPagedMock.mockReset();
  });

  it('clears temporary hidden legend groups before rendering a global filter result', async () => {
    const result = {
      geojson: { features: [] },
      gpsTracksById: new Map(),
      gpsTrackIdToFeature: new Map(),
      trackPrecisions: new Map(),
      filterResult: { filterConfigId: 7 },
      standardFilterCount: 12,
    };
    applyTrackFilterMock.mockResolvedValueOnce(result);
    const methods = useMapDataLoading({
      filterStore: { activeResult: result.filterResult },
      freshnessStore: {},
    });
    let hiddenGroupsDuringRender: Set<string> | null = null;
    const context = {
      initialLoadDone: true,
      hiddenGroups: new Set(['Q1']),
      showLoader: false,
      geojson: { features: [] },
      gpsTracksById: new Map(),
      gpsTrackIdToFeature: new Map(),
      trackPrecisions: new Map(),
      activeTrackFilterResult: null,
      totalTrackCount: 0,
      visibleTrackCount: 0,
      selectedTrackId: 1,
      selectedFeature: {},
      activeToolId: null,
      publishGpsTrackMetadataChanges: vi.fn(),
      closeSelectionPopup: vi.fn(),
      addTracksToMap: vi.fn(async function (this: { hiddenGroups: Set<string> }) {
        hiddenGroupsDuringRender = new Set(this.hiddenGroups);
      }),
      updateTracksSource: vi.fn(),
      maybeLoadBackgroundTracks: vi.fn(),
      reloadMap: vi.fn(),
    };

    await methods.onFilterApplied.call(context as never);

    expect(hiddenGroupsDuringRender).toEqual(new Set());
    expect(context.hiddenGroups).toEqual(new Set());
    expect(context.visibleTrackCount).toBe(0);
  });

  function makeResult(ids: number[]) {
    return {
      geojson: { type: 'FeatureCollection', features: ids.map((id) => ({ properties: { id } })) },
      gpsTracksById: new Map(ids.map((id) => [id, { id }])),
      gpsTrackIdToFeature: new Map(ids.map((id) => [id, { properties: { id } }])),
      trackPrecisions: new Map(ids.map((id) => [id, 1000])),
      filterResult: { trackVersions: new Map(ids.map((id) => [id, 1])) },
      standardFilterCount: 12,
    };
  }

  function makeContext() {
    return {
      initialLoadDone: true,
      hiddenGroups: new Set(),
      showLoader: false,
      geojson: makeResult([1]).geojson,
      gpsTracksById: new Map(),
      gpsTrackIdToFeature: new Map(),
      trackPrecisions: new Map(),
      activeTrackFilterResult: null,
      totalTrackCount: 0,
      visibleTrackCount: 0,
      selectedTrackId: null,
      selectedFeature: null,
      activeToolId: null,
      publishGpsTrackMetadataChanges: vi.fn(),
      closeSelectionPopup: vi.fn(),
      addTracksToMap: vi.fn().mockResolvedValue(undefined),
      updateTracksSource: vi.fn(),
      maybeLoadBackgroundTracks: vi.fn(),
      reloadMap: vi.fn(),
    };
  }

  it('re-applies a superseded filter when the archive reload has already completed', async () => {
    const latest = makeResult([2]);
    applyTrackFilterMock.mockRejectedValueOnce(new TrackLoadSupersededError()).mockResolvedValueOnce(latest);
    const methods = useMapDataLoading({ filterStore: {}, freshnessStore: {} });
    const context = { ...makeContext(), onFilterApplied: methods.onFilterApplied, freshnessReloading: false };
    await methods.onFilterApplied.call(context as never);
    expect(applyTrackFilterMock).toHaveBeenCalledTimes(2);
    expect(context.geojson).toBe(latest.geojson);
    expect(context.reloadMap).not.toHaveBeenCalled();
  });

  it('re-applies a superseded filter after an active freshness reload and clears an excluded selection', async () => {
    let reloading = false;
    let finishReload!: (result: ReturnType<typeof makeResult>) => void;
    const latest = makeResult([2]);
    loadTrackCollectionPagedMock.mockReturnValueOnce(
      new Promise((resolve) => {
        finishReload = resolve;
      })
    );
    applyTrackFilterMock.mockRejectedValueOnce(new TrackLoadSupersededError()).mockResolvedValueOnce(latest);
    const methods = useMapDataLoading({
      filterStore: { refreshResolvedFilter: vi.fn().mockResolvedValue(undefined) },
      freshnessStore: { setReloading: (value: boolean) => (reloading = value) },
    });
    const context = {
      ...makeContext(),
      _mapLoadGeneration: 1,
      selectedTrackId: 1,
      trackDetailsVisible: true,
      get freshnessReloading() {
        return reloading;
      },
      clearTrackCacheWhenServerFreshnessChanged: vi.fn().mockResolvedValue(false),
      currentCollectionPrecision: vi.fn(() => 1000),
      mergeTrackResult: vi.fn().mockResolvedValue(undefined),
      scheduleDetailCheck: vi.fn(),
      clearFocusedMediaMarker: vi.fn(),
      captureAppliedFreshnessToken: vi.fn().mockResolvedValue(undefined),
      endMapInteraction: vi.fn(),
      onFilterApplied: methods.onFilterApplied,
      $toast: { add: vi.fn() },
    };
    const reload = methods.onDataFreshnessReload.call(context as never);
    await vi.waitFor(() => expect(loadTrackCollectionPagedMock).toHaveBeenCalledOnce());
    await methods.onFilterApplied.call(context as never);
    expect(applyTrackFilterMock).toHaveBeenCalledOnce();
    expect(context.selectedTrackId).toBe(1);

    finishReload(latest);
    await expect(reload).resolves.toBe(true);
    expect(applyTrackFilterMock).toHaveBeenCalledTimes(2);
    expect(context.geojson).toBe(latest.geojson);
    expect(context.selectedTrackId).toBeNull();
    expect(context.trackDetailsVisible).toBe(false);
    expect(context.endMapInteraction).toHaveBeenCalledWith(true);
    expect(context.reloadMap).not.toHaveBeenCalled();
  });

  it.each([false, true])(
    'replays a queued filter after a failed reload only while the map is current (unmounted=%s)',
    async (unmounted) => {
      let reloading = false;
      let failReload!: (error: Error) => void;
      const latest = makeResult([2]);
      loadTrackCollectionPagedMock.mockReturnValueOnce(new Promise((_, reject) => (failReload = reject)));
      applyTrackFilterMock.mockRejectedValueOnce(new TrackLoadSupersededError()).mockResolvedValueOnce(latest);
      const methods = useMapDataLoading({
        filterStore: { refreshResolvedFilter: vi.fn().mockResolvedValue(undefined) },
        freshnessStore: { setReloading: (value: boolean) => (reloading = value) },
      });
      const context = {
        ...makeContext(),
        _mapLoadGeneration: 1,
        _mapUnmounted: false,
        get freshnessReloading() {
          return reloading;
        },
        clearTrackCacheWhenServerFreshnessChanged: vi.fn().mockResolvedValue(false),
        currentCollectionPrecision: vi.fn(() => 1000),
        onFilterApplied: methods.onFilterApplied,
        $toast: { add: vi.fn() },
      };
      const reload = methods.onDataFreshnessReload.call(context as never);
      await vi.waitFor(() => expect(loadTrackCollectionPagedMock).toHaveBeenCalledOnce());
      await methods.onFilterApplied.call(context as never);
      context._mapUnmounted = unmounted;
      failReload(new Error('Server unavailable'));

      await expect(reload).resolves.toBe(false);
      expect(reloading).toBe(false);
      expect(applyTrackFilterMock).toHaveBeenCalledTimes(unmounted ? 1 : 2);
      if (!unmounted) expect(context.geojson).toBe(latest.geojson);
    }
  );

  it('does not replace a newer Activities result with an older empty filter response', async () => {
    let finishOld!: (value: ReturnType<typeof makeResult>) => void;
    applyTrackFilterMock.mockReturnValueOnce(
      new Promise((resolve) => {
        finishOld = resolve;
      })
    );
    const latest = makeResult([2, 3]);
    applyTrackFilterMock.mockResolvedValueOnce(latest);
    const methods = useMapDataLoading({ filterStore: {}, freshnessStore: {} });
    const context = makeContext();

    const oldLoad = methods.onFilterApplied.call(context as never);
    await methods.onFilterApplied.call(context as never);
    finishOld(makeResult([]));
    await oldLoad;

    expect(context.geojson).toBe(latest.geojson);
    expect(context.visibleTrackCount).toBe(2);
    expect(context.addTracksToMap).toHaveBeenCalledOnce();
  });

  it('does not reload the map for a superseded filter failure', async () => {
    let failOld!: (error: Error) => void;
    applyTrackFilterMock.mockReturnValueOnce(
      new Promise((_resolve, reject) => {
        failOld = reject;
      })
    );
    const latest = makeResult([2, 3]);
    applyTrackFilterMock.mockResolvedValueOnce(latest);
    const methods = useMapDataLoading({ filterStore: {}, freshnessStore: {} });
    const context = makeContext();

    const oldLoad = methods.onFilterApplied.call(context as never);
    await methods.onFilterApplied.call(context as never);
    failOld(new Error('Earlier request failed'));
    await oldLoad;

    expect(context.reloadMap).not.toHaveBeenCalled();
    expect(context.geojson).toBe(latest.geojson);
  });

  it('keeps the loader visible while the latest filter response is pending', async () => {
    let finishOld!: (value: ReturnType<typeof makeResult>) => void;
    let finishNew!: (value: ReturnType<typeof makeResult>) => void;
    applyTrackFilterMock
      .mockReturnValueOnce(
        new Promise((resolve) => {
          finishOld = resolve;
        })
      )
      .mockReturnValueOnce(
        new Promise((resolve) => {
          finishNew = resolve;
        })
      );
    const methods = useMapDataLoading({ filterStore: {}, freshnessStore: {} });
    const context = makeContext();

    const oldLoad = methods.onFilterApplied.call(context as never);
    const newLoad = methods.onFilterApplied.call(context as never);
    finishOld(makeResult([]));
    await oldLoad;
    expect(context.showLoader).toBe(true);
    finishNew(makeResult([2]));
    await newLoad;
    expect(context.showLoader).toBe(false);
  });
});
