import { describe, expect, it, vi } from 'vitest';
import { useMapDataLoading } from '@/components/map/composables/useMapDataLoading';

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
  clearTrackCache: vi.fn(),
  fetchDetailTrackAtPrecision: vi.fn(),
  isTrackCachePopulated: vi.fn(),
  loadCachedTrackCollection: vi.fn(),
  loadTrackCollectionPaged: vi.fn(),
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

function makeMethods() {
  return useMapDataLoading({
    filterStore: {},
    freshnessStore: {
      dismissToken: vi.fn(),
      markAppliedToken: vi.fn(),
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
