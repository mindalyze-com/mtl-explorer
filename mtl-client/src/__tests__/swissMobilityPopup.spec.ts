import { afterEach, describe, expect, it, vi } from 'vitest';
import { resolveSwissMobilityPopupPosition, useMapTools } from '@/components/map/composables/useMapTools';

const REGRESSION_VIEWPORT_WIDTH_PX = 677;
const REGRESSION_VIEWPORT_HEIGHT_PX = 998;
const REGRESSION_CLICK_POINT = { x: 410, y: 118 };
const POPUP_WIDTH_PX = 300;
const EDGE_GAP_PX = 12;

type SwissMobilityPopupTestState = {
  visible: boolean;
  pos: { x: number; y: number };
  routes: Array<{
    icon: string;
    type: string;
    name: string;
    number?: string | number | null;
  }>;
};

type SwissMobilityPopupTestContext = {
  activeOverlays: string[];
  overlayMap: {
    getBounds: () => {
      getWest: () => number;
      getSouth: () => number;
      getEast: () => number;
      getNorth: () => number;
    };
    getCanvas: () => {
      width: number;
      height: number;
      clientWidth: number;
      clientHeight: number;
    };
  };
  swissMobilityPopup: SwissMobilityPopupTestState;
};

type RouteToolSyncTestContext = {
  $refs: Record<string, { close?: () => void; open?: () => void; toggle?: () => void }>;
  $nextTick: (callback: () => void) => void;
  activeToolId: string | null;
  _syncingView: boolean;
  trackReplayActive: boolean;
  trackDetailsVisible: boolean;
  trackDetailsId: number | null;
  selectedTrackId: number | null;
  trackDetailsSelectedDetent?: string;
  trackDetailsInfo: { id: number | null; name: string; description: string; activityType: string };
  mediaSheetVisible: boolean;
  trackSelectionSheetVisible: boolean;
  locationSearchVisible: boolean;
  locationSearchMarker: null;
  selectionPopupTrackIds: number[];
  swissMobilityPopup: SwissMobilityPopupTestState;
  geoDrawingParamDef: null;
  geoDrawingOverlay: null;
  closeMediaSheet: ReturnType<typeof vi.fn>;
};

function bindMapToolsMethods<TContext extends object>(context: TContext) {
  const methods = useMapTools();
  for (const [name, method] of Object.entries(methods)) {
    (context as Record<string, unknown>)[name] = method.bind(context);
  }
  return context as TContext & typeof methods;
}

describe('Swiss Mobility popup placement', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('keeps a right-edge route popup inside the map viewport', () => {
    const position = resolveSwissMobilityPopupPosition(REGRESSION_CLICK_POINT, {
      width: REGRESSION_VIEWPORT_WIDTH_PX,
      height: REGRESSION_VIEWPORT_HEIGHT_PX,
    });

    expect(position.x).toBeLessThan(REGRESSION_CLICK_POINT.x);
    expect(position.x).toBeGreaterThanOrEqual(EDGE_GAP_PX);
    expect(position.x + POPUP_WIDTH_PX + EDGE_GAP_PX).toBeLessThanOrEqual(REGRESSION_VIEWPORT_WIDTH_PX);
  });

  it('uses the clamped position after Swiss route identification succeeds', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: vi.fn().mockResolvedValue({
          results: [
            {
              layerBodId: 'ch.astra.wanderland',
              attributes: {
                chmobil_title: 'Trans Swiss Trail',
                chmobil_route_number: 2,
              },
            },
          ],
        }),
      })
    );
    const context = bindMapToolsMethods<SwissMobilityPopupTestContext>({
      activeOverlays: ['wanderland'],
      overlayMap: {
        getBounds: () => ({
          getWest: () => 6,
          getSouth: () => 45,
          getEast: () => 11,
          getNorth: () => 48,
        }),
        getCanvas: () => ({
          width: REGRESSION_VIEWPORT_WIDTH_PX * 2,
          height: REGRESSION_VIEWPORT_HEIGHT_PX * 2,
          clientWidth: REGRESSION_VIEWPORT_WIDTH_PX,
          clientHeight: REGRESSION_VIEWPORT_HEIGHT_PX,
        }),
      },
      swissMobilityPopup: { visible: false, pos: { x: 0, y: 0 }, routes: [] },
    });

    await context.identifySwissMobilityRoutes({ lng: 7.1, lat: 46.8 }, REGRESSION_CLICK_POINT);

    expect(context.swissMobilityPopup).toMatchObject({
      visible: true,
      pos: {
        x: expect.any(Number),
        y: expect.any(Number),
      },
      routes: [
        {
          icon: 'bi bi-signpost-2',
          type: 'Hiking',
          name: 'Trans Swiss Trail',
          number: 2,
        },
      ],
    });
    expect(context.swissMobilityPopup.pos.x + POPUP_WIDTH_PX + EDGE_GAP_PX).toBeLessThanOrEqual(
      REGRESSION_VIEWPORT_WIDTH_PX
    );
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
      _syncingView: false,
      trackReplayActive: false,
      trackDetailsVisible: false,
      trackDetailsId: null,
      selectedTrackId: null,
      trackDetailsInfo: { id: null, name: '', description: '', activityType: '' },
      mediaSheetVisible: false,
      trackSelectionSheetVisible: false,
      locationSearchVisible: false,
      locationSearchMarker: null,
      selectionPopupTrackIds: [],
      swissMobilityPopup: { visible: false, pos: { x: 0, y: 0 }, routes: [] },
      geoDrawingParamDef: null,
      geoDrawingOverlay: null,
      closeMediaSheet: vi.fn(),
    });

    context.syncToolToRoute('stats');

    expect(statsOpen).toHaveBeenCalledOnce();
    expect(filterClose).toHaveBeenCalledOnce();
    expect(context.activeToolId).toBe('stats');
    expect(context._syncingView).toBe(false);
  });
});
