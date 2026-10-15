import { resolveTrackDetailsOpeningDetent } from '@/components/map/composables/useMapTools';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { useMapTools } from '@/components/map/composables/useMapTools';

const REGRESSION_CLICK_POINT = { x: 410, y: 118 };
type SwissMobilityPopupTestState = { visible: boolean; pos: { x: number; y: number }; routes: unknown[] };

type RouteToolSyncTestContext = {
  $refs: Record<
    string,
    {
      close?: () => void;
      open?: () => void;
      toggle?: () => void;
      getNavigationState?: () => unknown;
      restoreNavigationState?: (state: unknown) => void;
    }
  >;
  $nextTick: (callback: () => void) => void;
  activeToolId: string | null;
  _syncingToolRoute: boolean;
  trackReplayActive: boolean;
  trackDetailsVisible: boolean;
  trackDetailsId: number | null;
  selectedTrackId: number | null;
  trackDetailsSelectedDetent?: string;
  trackDetailsInfo: { id: number | null; name: string; description: string; activityType: string };
  trackDetailsReturnTarget: { toolId: string; state?: unknown } | null;
  mediaSheetVisible: boolean;
  trackSelectionSheetVisible: boolean;
  locationSearchVisible: boolean;
  locationSearchMarker: null;
  selectionPopupTrackIds: number[];
  swissMobilityPopup: SwissMobilityPopupTestState;
  geoDrawingParamDef: null;
  geoDrawingOverlay: null;
  endMapInteraction: ReturnType<typeof vi.fn>;
  closeMediaSelection: ReturnType<typeof vi.fn>;
  closeMediaSheet: ReturnType<typeof vi.fn>;
};

function bindMapToolsMethods<TContext extends object>(context: TContext) {
  const methods = useMapTools();
  for (const [name, method] of Object.entries(methods)) {
    (context as Record<string, unknown>)[name] = method.bind(context);
  }
  return context as TContext & typeof methods;
}

describe('route identification provider', () => {
  afterEach(() => vi.unstubAllGlobals());
  function context() {
    return bindMapToolsMethods({
      activeOverlays: ['wanderland'],
      layerOpacities: { wanderland: 100 },
      overlayMap: {
        getLayer: vi.fn(() => ({})),
        getBounds: () => ({ getWest: () => 6, getSouth: () => 45, getEast: () => 11, getNorth: () => 48 }),
        getCanvas: () => ({ width: 1354, height: 1996, clientWidth: 677, clientHeight: 998 }),
      },
    });
  }
  it('returns source-labelled candidates and passes cancellation without opening a popup', async () => {
    const fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        results: [
          {
            id: 'route-2',
            layerBodId: 'ch.astra.wanderland',
            layerName: 'Hiking routes',
            attributes: { chmobil_title: 'Synthetic trail', chmobil_route_number: 2 },
          },
        ],
      }),
    });
    vi.stubGlobal('fetch', fetch);
    const controller = new AbortController();
    const target = context();
    const candidates = await target.identifySwissMobilityRoutes(
      { lng: 7.1, lat: 46.8 },
      REGRESSION_CLICK_POINT,
      controller.signal,
      24
    );
    expect(candidates).toMatchObject([
      {
        id: 'route-2',
        kind: 'overlay',
        source: 'ch.astra.wanderland',
        label: 'Synthetic trail',
        sourceLabel: 'Hiking routes',
        route: { type: 'Hiking', name: 'Synthetic trail', number: 2 },
      },
    ]);
    expect(fetch.mock.calls[0][1]).toEqual({ signal: controller.signal });
    const params = new URL(fetch.mock.calls[0][0]).searchParams;
    expect(params.get('tolerance')).toBe('24');
    expect(params.get('imageDisplay')).toBe('677,998,96');
    expect(target).not.toHaveProperty('swissMobilityPopup');
  });
  it('preserves distinct object IDs and gives rendered copies the same semantic identity', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({
          results: [
            { id: 1, layerBodId: 'ch.astra.wanderland', attributes: { label: 'Same label' } },
            { id: 1, layerBodId: 'ch.astra.wanderland', attributes: { label: 'Same label' } },
            { id: 2, layerBodId: 'ch.astra.wanderland', attributes: { label: 'Same label' } },
          ],
        }),
      })
    );
    const candidates = await context().identifySwissMobilityRoutes({ lng: 7.1, lat: 46.8 }, REGRESSION_CLICK_POINT);
    expect(candidates.map((candidate) => candidate.id)).toEqual(['1', '1', '2']);
  });
  it('does not query a hidden or missing overlay', async () => {
    const fetch = vi.fn();
    vi.stubGlobal('fetch', fetch);
    const target = context();
    target.layerOpacities.wanderland = 0;
    expect(await target.identifySwissMobilityRoutes({ lng: 7.1, lat: 46.8 }, REGRESSION_CLICK_POINT)).toEqual([]);
    target.layerOpacities.wanderland = 100;
    target.overlayMap.getLayer.mockReturnValue(undefined);
    expect(await target.identifySwissMobilityRoutes({ lng: 7.1, lat: 46.8 }, REGRESSION_CLICK_POINT)).toEqual([]);
    expect(fetch).not.toHaveBeenCalled();
  });
  it('keeps HTTP failure distinct from a confirmed empty response', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 503 }));
    await expect(
      context().identifySwissMobilityRoutes({ lng: 7.1, lat: 46.8 }, REGRESSION_CLICK_POINT)
    ).rejects.toThrow('503');
  });
});

describe('map route tool sync', () => {
  it('opens the route target even when activeToolId is stale', () => {
    const statsOpen = vi.fn();
    const filterClose = vi.fn();
    const context = bindMapToolsMethods<RouteToolSyncTestContext>({
      $refs: {
        statistics: { open: statsOpen },
        filterTool: { close: filterClose },
      },
      $nextTick: (callback) => callback(),
      activeToolId: 'stats',
      _syncingToolRoute: false,
      trackReplayActive: false,
      trackDetailsVisible: false,
      trackDetailsId: null,
      selectedTrackId: null,
      trackDetailsInfo: { id: null, name: '', description: '', activityType: '' },
      trackDetailsReturnTarget: null,
      mediaSheetVisible: false,
      trackSelectionSheetVisible: false,
      locationSearchVisible: false,
      locationSearchMarker: null,
      selectionPopupTrackIds: [],
      swissMobilityPopup: { visible: false, pos: { x: 0, y: 0 }, routes: [] },
      geoDrawingParamDef: null,
      geoDrawingOverlay: null,
      endMapInteraction: vi.fn(),
      closeMediaSelection: vi.fn(),
      closeMediaSheet: vi.fn(),
    });

    context.syncToolToRoute('stats');

    expect(statsOpen).toHaveBeenCalledOnce();
    expect(filterClose).toHaveBeenCalledOnce();
    expect(context.activeToolId).toBe('stats');
    expect(context._syncingToolRoute).toBe(false);
  });

  it('restores the originating tool subview after Track Details closes', () => {
    const reviewState = { screen: 'review', reviewGroup: null };
    const filterOpen = vi.fn();
    const restoreNavigationState = vi.fn();
    const context = bindMapToolsMethods<RouteToolSyncTestContext>({
      $refs: {
        filterTool: {
          open: filterOpen,
          getNavigationState: () => reviewState,
          restoreNavigationState,
        },
      },
      $nextTick: (callback) => callback(),
      activeToolId: 'filter',
      _syncingToolRoute: false,
      trackReplayActive: false,
      trackDetailsVisible: false,
      trackDetailsId: null,
      selectedTrackId: null,
      trackDetailsInfo: { id: null, name: '', description: '', activityType: '' },
      trackDetailsReturnTarget: null,
      mediaSheetVisible: false,
      trackSelectionSheetVisible: false,
      locationSearchVisible: false,
      locationSearchMarker: null,
      selectionPopupTrackIds: [],
      swissMobilityPopup: { visible: false, pos: { x: 0, y: 0 }, routes: [] },
      geoDrawingParamDef: null,
      geoDrawingOverlay: null,
      endMapInteraction: vi.fn(),
      closeMediaSelection: vi.fn(),
      closeMediaSheet: vi.fn(),
    });

    context.captureActiveToolNavigation();
    expect(context.trackDetailsReturnTarget).toEqual({ toolId: 'filter', state: reviewState });

    context.activeToolId = null;
    context.syncToolToRoute('filter');

    expect(filterOpen).toHaveBeenCalledOnce();
    expect(restoreNavigationState).toHaveBeenCalledWith(reviewState);
    expect(context.trackDetailsReturnTarget).toBeNull();
  });

  it('keeps the original return target while navigating through track-detail history', () => {
    const returnTarget = { toolId: 'filter', state: { screen: 'review', reviewGroup: null } };
    const context = bindMapToolsMethods({
      $refs: {},
      $nextTick: (callback: () => void) => callback(),
      activeToolId: null,
      trackReplayActive: false,
      trackDetailsVisible: false,
      trackDetailsId: null,
      trackDetailsInitialDetent: 'default',
      trackDetailsSelectedDetent: undefined,
      trackDetailsInfo: { id: null, name: '', description: '', activityType: '' },
      trackDetailsReturnTarget: returnTarget,
      selectedTrackId: null,
      selectedFeature: null,
      gpsTrackIdToFeature: new Map(),
      mediaSheetVisible: false,
      trackSelectionSheetVisible: false,
      locationSearchVisible: false,
      locationSearchMarker: null,
      selectionPopupTrackIds: [],
      swissMobilityPopup: { visible: false, pos: { x: 0, y: 0 }, routes: [] },
      endMapInteraction: vi.fn(),
      closeMediaSelection: vi.fn(),
      closeMediaSheet: vi.fn(),
      selectTrackById: vi.fn(),
    });

    context.syncTrackDetailRoute(42);

    expect(context.trackDetailsReturnTarget).toBe(returnTarget);
    expect(context.trackDetailsId).toBe(42);
    expect(context.trackDetailsVisible).toBe(true);
  });

  it.each([
    ['stats', 'statistics', 'route'],
    ['stats', 'statistics', 'tool'],
    ['filter', 'filterTool', 'route'],
    ['filter', 'filterTool', 'tool'],
  ])('keeps the originating %s panel open underneath GPS details on %s return', (toolId, refName, returnVia) => {
    const navigation = { tab: 'tracks', page: 3 };
    const close = vi.fn();
    const toggle = vi.fn();
    const restore = vi.fn();
    const context = bindMapToolsMethods({
      $refs: {
        [refName]: {
          close,
          toggle,
          open: vi.fn(),
          getNavigationState: () => navigation,
          restoreNavigationState: restore,
        },
      },
      $nextTick: (callback: () => void) => callback(),
      activeToolId: toolId,
      deselectTrack: vi.fn(),
      trackDetailsReturnTarget: null,
      trackReplayActive: false,
      trackDetailsVisible: false,
      trackDetailsId: null,
      trackDetailsSelectedDetent: undefined,
      trackDetailsInfo: { id: null, name: '', description: '', activityType: '' },
      selectedTrackId: null,
      selectedFeature: null,
      gpsTrackIdToFeature: new Map(),
      mediaSheetVisible: false,
      trackSelectionSheetVisible: false,
      locationSearchVisible: false,
      locationSearchMarker: null,
      selectionPopupTrackIds: [],
      swissMobilityPopup: { visible: false, pos: { x: 0, y: 0 }, routes: [] },
      endMapInteraction: vi.fn(),
      closeMediaSelection: vi.fn(),
      closeMediaSheet: vi.fn(),
      selectTrackById: vi.fn(),
    });
    context.openTrackDetails(42);
    context.syncTrackDetailRoute(42);
    expect(close).not.toHaveBeenCalled();
    expect(context.trackDetailsVisible).toBe(true);
    expect(context.trackDetailsReturnTarget).toEqual({ toolId, state: navigation });
    context.syncTrackDetailRoute(43);
    expect(close).not.toHaveBeenCalled();
    if (returnVia === 'tool') context.onToolSelect(toolId);
    else context.syncToolToRoute(toolId);
    expect(close).not.toHaveBeenCalled();
    expect(toggle).not.toHaveBeenCalled();
    expect(context.trackDetailsVisible).toBe(false);
    expect(restore).toHaveBeenCalledWith(navigation);
  });
});

describe('track detail opening height', () => {
  it.each([
    ['compact', 'expanded'],
    ['default', 'expanded'],
    ['desktop', 'expanded'],
    ['expanded', 'expanded'],
    ['replay-background', 'replay-background'],
  ])('opens %s as %s', (requested, expected) => {
    expect(resolveTrackDetailsOpeningDetent(requested)).toBe(expected);
  });
});
