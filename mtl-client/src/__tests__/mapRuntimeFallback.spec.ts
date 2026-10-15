import { afterEach, describe, expect, it, vi } from 'vitest';
import { flushPromises } from '@vue/test-utils';
import { useMapRendererLifecycle } from '@/components/map/composables/useMapRendererLifecycle';
import { probeVectorMapArchive } from '@/utils/mapArchiveHealth';
import { getToken, redirectToLoginAfterAuthFailure } from '@/utils/auth';
import { reportMapError } from '@/utils/mapDiagnostics';
import { TERRAIN_DEM_SOURCE_ID, HILLSHADE_DEM_SOURCE_ID } from '@/utils/mapStyle';
import { nextTick, reactive } from 'vue';

const statusMocks = vi.hoisted(() => ({ store: null as Record<string, unknown> | null }));

vi.mock('@/utils/mapArchiveHealth', () => ({ probeVectorMapArchive: vi.fn() }));
afterEach(() => {
  vi.clearAllTimers();
  vi.useRealTimers();
  vi.mocked(probeVectorMapArchive).mockReset();
  vi.mocked(getToken).mockReset();
  vi.mocked(redirectToLoginAfterAuthFailure).mockReset();
  vi.mocked(reportMapError).mockReset();
  statusMocks.store = null;
});

vi.mock('maplibre-gl', () => ({}));
vi.mock('@/utils/auth', () => ({ getToken: vi.fn(), redirectToLoginAfterAuthFailure: vi.fn() }));
vi.mock('@/utils/mapDiagnostics', () => ({ reportMapError: vi.fn(), reportMapContextLoss: vi.fn() }));
vi.mock('@/utils/ServiceHelper', () => ({ fetchTrackIdsWithinDistanceOfPoint: vi.fn() }));
vi.mock('@/layers/MediaOverlay', () => ({ MediaOverlay: vi.fn() }));
vi.mock('@/layers/HeatmapOverlay', () => ({ HeatmapOverlay: vi.fn() }));
vi.mock('@/stores/applicationStatusStore', () => ({
  APPLICATION_STATUS_SECTION: { SERVICES: 'services' },
  useApplicationStatusStore: () => statusMocks.store ?? {},
}));
vi.mock('@/utils/startupDiagnostics', () => ({
  describeError: vi.fn((error) => String(error)),
  startStartupTimer: vi.fn(),
  startupLog: vi.fn(),
  startupWarn: vi.fn(),
}));

function makeContext() {
  return {
    baseMapRuntimeFallbackApplied: false,
    baseMapStyleMode: 'local-vector',
    overlayMap: { getSource: vi.fn(() => ({})) },
    mapConfig: {
      tileMode: 'local',
      tileBaseUrl: 'https://example.test',
      tilesetName: 'synthetic',
      remoteRasterStyles: { light: { url: 'https://example.test/{z}/{x}/{y}.png' } },
    },
    mapThemeSelected: 'light',
    geojson: { type: 'FeatureCollection', features: [{ properties: { id: 1 } }] },
    activeTrackFilterResult: { trackVersions: new Map([[1, 1]]) },
    selectedTrackId: 1,
    initialLoadDone: true,
    loadingTrackBatches: false,
    $emit: vi.fn(),
    showLoader: false,
    initMap: vi.fn().mockResolvedValue(undefined),
    addTracksToMap: vi.fn().mockResolvedValue(undefined),
    reloadMap: vi.fn(),
    fetchTracksAndFallback: vi.fn(),
  };
}

describe('runtime basemap fallback', () => {
  it('retains a working vector map when the status endpoint recovers for the same archive', async () => {
    const store = reactive({
      services: {
        map: { ready: false, tileSource: undefined as string | undefined, archiveId: undefined as string | undefined },
      },
      registerPollingConsumer: vi.fn(() => ({ dispose: vi.fn() })),
    });
    statusMocks.store = store;
    const methods = useMapRendererLifecycle({ mapSettingsStore: {} as never });
    const context = {
      ...makeContext(),
      mapServerStatus: { phase: 'unreachable', ready: false },
      mapConfig: { tileMode: 'local', tileSource: 'local', archiveId: 'synthetic' },
      stopMapStatusPolling: vi.fn(),
    };
    methods.startMapStatusPolling.call(context as never);
    store.services.map = { ready: true, tileSource: 'local', archiveId: 'synthetic' };
    await nextTick();
    expect(context.reloadMap).not.toHaveBeenCalled();
    expect(context.selectedTrackId).toBe(1);
    methods.stopMapStatusPolling.call(context as never);
  });

  it('disposes drawing before replacing its map so the next drawing binds to the new map', () => {
    const methods = useMapRendererLifecycle({ mapSettingsStore: {} as never });
    const destroy = vi.fn();
    const remove = vi.fn();
    const context = {
      overlayMap: { remove },
      geoDrawingOverlay: { destroy },
      geoDrawingParamDef: { name: 'AREA', type: 'GEO_CIRCLE' },
      geoDrawPointCount: 1,
      detachTrackPointLayerHandlers: vi.fn(),
      disposeMapInteractions: vi.fn(),
    };

    methods.disposeRendererMaps.call(context as never);

    expect(destroy).toHaveBeenCalledOnce();
    expect(destroy.mock.invocationCallOrder[0]).toBeLessThan(remove.mock.invocationCallOrder[0]);
    expect(context.geoDrawingOverlay).toBeNull();
    expect(context.geoDrawingParamDef).toBeNull();
    expect(context.geoDrawPointCount).toBe(0);
  });

  it('defers map layout writes, coalesces resize notifications, and cancels them when the map is replaced', () => {
    vi.useFakeTimers();
    let notify!: ResizeObserverCallback;
    const disconnect = vi.fn();
    vi.stubGlobal(
      'ResizeObserver',
      class {
        constructor(callback: ResizeObserverCallback) {
          notify = callback;
        }
        observe() {}
        disconnect = disconnect;
      }
    );
    const methods = useMapRendererLifecycle({ mapSettingsStore: {} as never });
    const container = document.createElement('div');
    Object.defineProperties(container, {
      clientWidth: { value: 390 },
      clientHeight: { value: 844 },
    });
    const map = { resize: vi.fn(), setMinZoom: vi.fn(), redraw: vi.fn(), remove: vi.fn() };
    const context = {
      overlayMap: map,
      globeMode: true,
      $refs: { mapContainer: container },
      detachTrackPointLayerHandlers: vi.fn(),
      disposeMapInteractions: vi.fn(),
    };
    try {
      methods.observeMapContainerResize.call(context as never);
      const entries = [{ target: container, contentRect: { width: 390 } }] as ResizeObserverEntry[];
      notify(entries, {} as ResizeObserver);
      notify(entries, {} as ResizeObserver);
      expect(map.resize).not.toHaveBeenCalled();
      expect(map.setMinZoom).not.toHaveBeenCalled();
      expect(map.redraw).not.toHaveBeenCalled();
      vi.advanceTimersToNextFrame();
      expect(map.resize).toHaveBeenCalledOnce();
      expect(map.setMinZoom).toHaveBeenCalledExactlyOnceWith(Math.log2((390 * 0.8 * Math.PI) / 512));
      expect(map.redraw).toHaveBeenCalledOnce();
      expect(map.resize.mock.invocationCallOrder[0]).toBeLessThan(map.setMinZoom.mock.invocationCallOrder[0]);
      expect(map.setMinZoom.mock.invocationCallOrder[0]).toBeLessThan(map.redraw.mock.invocationCallOrder[0]);
      notify(entries, {} as ResizeObserver);
      methods.disposeRendererMaps.call(context as never);
      vi.advanceTimersToNextFrame();
      expect(map.resize).toHaveBeenCalledOnce();
      expect(map.redraw).toHaveBeenCalledOnce();
      expect(disconnect).toHaveBeenCalledOnce();
    } finally {
      vi.unstubAllGlobals();
    }
  });

  it.each([TERRAIN_DEM_SOURCE_ID, HILLSHADE_DEM_SOURCE_ID])(
    'reports a provider 401 from %s without treating the application session as expired',
    (sourceId) => {
      const methods = useMapRendererLifecycle({ mapSettingsStore: {} as never });
      const context = { applyRuntimeRasterBasemapFallback: vi.fn(() => false) };
      const event = { sourceId, error: new Error('Detailed terrain tile failed: HTTP 401') };
      vi.mocked(getToken).mockReturnValue('valid-session');

      methods.handleMapError.call(context as never, event as never);

      expect(redirectToLoginAfterAuthFailure).not.toHaveBeenCalled();
      expect(reportMapError).toHaveBeenCalledExactlyOnceWith(event, 'Map2DRenderer');
    }
  );

  it('still redirects for an application tile authentication failure', () => {
    const methods = useMapRendererLifecycle({ mapSettingsStore: {} as never });
    const context = { applyRuntimeRasterBasemapFallback: vi.fn(() => false) };
    vi.mocked(getToken).mockReturnValue('expired-session');

    methods.handleMapError.call(
      context as never,
      { sourceId: 'protomaps', error: new Error('Bad response code: 401') } as never
    );

    expect(redirectToLoginAfterAuthFailure).toHaveBeenCalledExactlyOnceWith(true);
    expect(context.applyRuntimeRasterBasemapFallback).not.toHaveBeenCalled();
    expect(reportMapError).not.toHaveBeenCalled();
  });

  it('ignores canceled tile requests in the map error handler', () => {
    const methods = useMapRendererLifecycle({ mapSettingsStore: {} as never });
    const context = { applyRuntimeRasterBasemapFallback: vi.fn(() => false) };

    methods.handleMapError.call(context as never, { error: new DOMException('Aborted', 'AbortError') } as never);

    expect(redirectToLoginAfterAuthFailure).not.toHaveBeenCalled();
    expect(context.applyRuntimeRasterBasemapFallback).not.toHaveBeenCalled();
    expect(reportMapError).not.toHaveBeenCalled();
  });

  it('keeps local tiles when a vector request was cancelled', () => {
    const methods = useMapRendererLifecycle({ mapSettingsStore: {} as never });
    const context = makeContext();

    const applied = methods.applyRuntimeRasterBasemapFallback.call(
      context as never,
      { sourceId: 'protomaps', error: new DOMException('Aborted', 'AbortError') },
      'Aborted'
    );

    expect(applied).toBe(false);
    expect(context.baseMapRuntimeFallbackApplied).toBe(false);
    expect(context.reloadMap).not.toHaveBeenCalled();
    expect(context.initMap).not.toHaveBeenCalled();
  });

  it.each([true, false])(
    'preserves the Activities result and selection while rebuilding a failed basemap (startup complete: %s)',
    async (startupComplete) => {
      const methods = useMapRendererLifecycle({ mapSettingsStore: {} as never });
      const context = makeContext();
      context.initialLoadDone = startupComplete;
      context.loadingTrackBatches = !startupComplete;
      context.fetchTracksAndFallback.mockImplementation(async () => {
        context.initialLoadDone = true;
      });
      vi.useFakeTimers();
      vi.mocked(probeVectorMapArchive).mockRejectedValue(new Error('Bad response code: 503'));
      const tracks = context.geojson;
      const filter = context.activeTrackFilterResult;

      methods.applyRuntimeRasterBasemapFallback.call(
        context as never,
        { sourceId: 'protomaps', error: new Error('Tile fetch failed') },
        'Tile fetch failed'
      );
      await flushPromises();
      expect(context.initMap).not.toHaveBeenCalled();
      await vi.advanceTimersByTimeAsync(4000);

      expect(context.initMap).toHaveBeenCalledOnce();
      expect(context.addTracksToMap).toHaveBeenCalledOnce();
      expect(context.reloadMap).not.toHaveBeenCalled();
      expect(context.fetchTracksAndFallback).toHaveBeenCalledTimes(startupComplete ? 0 : 1);
      expect(context.geojson).toBe(tracks);
      expect(context.activeTrackFilterResult).toBe(filter);
      expect(context.selectedTrackId).toBe(1);
      expect(context.initialLoadDone).toBe(true);
      expect(context.loadingTrackBatches).toBe(false);
      expect(context.showLoader).toBe(false);
    }
  );
});
