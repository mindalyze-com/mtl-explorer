import { describe, expect, it, vi } from 'vitest';
import { useMapDataLoading } from '@/components/map/composables/useMapDataLoading';

const trackCollectionMocks = vi.hoisted(() => ({
  clearTrackCache: vi.fn(),
  loadTrackCollectionPaged: vi.fn(),
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
  applyTrackFilter: vi.fn(),
  clearTrackCache: trackCollectionMocks.clearTrackCache,
  fetchDetailTrackAtPrecision: vi.fn(),
  isTrackCachePopulated: vi.fn(),
  loadCachedTrackCollection: vi.fn(),
  loadTrackCollectionPaged: trackCollectionMocks.loadTrackCollectionPaged,
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

function makeMethods(filterStore: Record<string, unknown> = {}) {
  return useMapDataLoading({
    filterStore,
    freshnessStore: {
      snooze: vi.fn(),
      markAppliedToken: vi.fn(),
      clearSnooze: vi.fn(),
      setReloading: vi.fn(),
    },
  });
}

type FreshnessReloadContext = {
  onDataFreshnessReload: () => Promise<boolean>;
};
type BannerReloadMethod = (this: FreshnessReloadContext) => Promise<boolean>;
type AdminRefreshMethod = (this: FreshnessReloadContext, done: (success?: boolean) => void) => Promise<void>;

describe('map data freshness reload actions', () => {
  it('dismisses the warning by setting one token-independent snooze', () => {
    const snooze = vi.fn();
    const methods = useMapDataLoading({
      filterStore: {},
      freshnessStore: {
        snooze,
        markAppliedToken: vi.fn(),
        clearSnooze: vi.fn(),
        setReloading: vi.fn(),
      },
    });

    methods.onDataFreshnessDismiss.call({} as never);

    expect(snooze).toHaveBeenCalledOnce();
    expect(snooze).toHaveBeenCalledWith();
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
});
