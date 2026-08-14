import { describe, expect, it, vi } from 'vitest';
import { useMapDataLoading } from '@/components/map/composables/useMapDataLoading';

const { applyTrackFilterMock } = vi.hoisted(() => ({
  applyTrackFilterMock: vi.fn(),
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

describe('map filter visibility reset', () => {
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
});
