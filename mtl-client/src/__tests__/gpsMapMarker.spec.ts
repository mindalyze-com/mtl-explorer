import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises } from '@vue/test-utils';
import { useMapTools } from '@/components/map/composables/useMapTools';
import { useMapRendererLifecycle } from '@/components/map/composables/useMapRendererLifecycle';
import { rebuildMapLayers } from '@/components/map/composables/useMapLayerSettings';
import { StatusRequestPausedError } from '@/utils/statusPolling';

const mapLoad = vi.hoisted(() => ({ ready: true, callbacks: [] as Array<() => void> }));
const requests = vi.hoisted(() => ({ config: vi.fn(), status: vi.fn(), warn: vi.fn() }));

vi.mock('@/layers/GeoDrawingOverlay', () => ({ GeoDrawingOverlay: vi.fn() }));
vi.mock('@/utils/ServiceHelper', () => ({ fetchTrackIdsWithinDistanceOfPoint: vi.fn() }));
vi.mock('@/layers/MediaOverlay', () => ({ MediaOverlay: class {} }));
vi.mock('@/layers/HeatmapOverlay', () => ({ HeatmapOverlay: class {} }));
vi.mock('@/stores/applicationStatusStore', () => ({
  APPLICATION_STATUS_SECTION: {},
  useApplicationStatusStore: () => ({}),
}));
vi.mock('@/stores/mapStateStore', () => ({ useMapStateStore: () => ({ returnViewportCamera: null }) }));
vi.mock('@/utils/mapConfigService', () => ({
  fetchMapConfig: requests.config,
  MapConfigDtoTileModeEnum: { Local: 'local' },
}));
vi.mock('@/utils/mapStatusService', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/utils/mapStatusService')>()),
  fetchMapStatus: requests.status,
  shouldPollMapStatus: () => false,
}));
vi.mock('@/components/map/mapStyleResolver', () => ({
  resolveConfiguredMapStyle: () => ({ style: {}, styleMode: 'remote-raster', attributions: [] }),
}));
vi.mock('@/utils/maplibrePmtilesProtocol', () => ({ ensurePMTilesProtocol: vi.fn() }));
vi.mock('@/utils/maplibreStyleImages', () => ({ installMissingStyleImageResolver: vi.fn() }));
vi.mock('@/utils/externalAttributionLinks', () => ({ configureExternalAttributionLinks: () => vi.fn() }));
vi.mock('@/utils/startupDiagnostics', () => ({
  startStartupTimer: () => ({ success: vi.fn(), warn: requests.warn, error: vi.fn() }),
  startupLog: vi.fn(),
  startupWarn: requests.warn,
}));
vi.mock('@/components/map/GlobeControl', () => ({ GlobeControl: class {} }));
vi.mock('@/components/map/TerrainViewControl', () => ({
  TerrainViewControl: class {
    setActive() {}
  },
}));
vi.mock('maplibre-gl', () => ({
  Map: class {
    container: HTMLElement;
    canvas = document.createElement('canvas');
    flyTo = vi.fn();
    jumpTo = vi.fn();
    constructor({ container }: { container: HTMLElement }) {
      this.container = container;
    }
    getCanvasContainer() {
      return this.container;
    }
    getCanvas() {
      return this.canvas;
    }
    getPadding() {
      return { top: 0, right: 0, bottom: 0, left: 0 };
    }
    getCenter() {
      return { lng: 8, lat: 47 };
    }
    getZoom() {
      return 10;
    }
    getBearing() {
      return 0;
    }
    getPitch() {
      return 0;
    }
    loaded() {
      return mapLoad.ready;
    }
    addControl() {}
    once(event: string, callback: () => void) {
      if (event === 'load') mapLoad.callbacks.push(callback);
    }
    on() {}
    remove() {
      this.container.replaceChildren();
    }
  },
  Marker: class {
    element: HTMLElement;
    coordinates?: number[];
    constructor({ element }: { element: HTMLElement }) {
      this.element = element;
    }
    setLngLat(coordinates: number[]) {
      this.coordinates = coordinates;
      return this;
    }
    addTo(map: { getCanvasContainer(): HTMLElement }) {
      map.getCanvasContainer().append(this.element);
      return this;
    }
    remove() {
      this.element.remove();
    }
  },
  NavigationControl: class {},
  ScaleControl: class {},
  AttributionControl: class {},
}));

function position(longitude = 8, latitude = 47): GeolocationPosition {
  return { coords: { longitude, latitude } } as GeolocationPosition;
}

function createRuntime() {
  const container = document.createElement('div');
  const context = {
    $refs: { mapContainer: container },
    overlayMap: undefined,
    gpsMarker: null,
    gpsLocation: null,
    gpsDeviceEnabledDisabled: false,
    gpsFollowing: false,
    mapCenter: [8, 47],
    toolDefs: [],
    mapSourceMode: 'remote',
    mapThemeSelected: 'light',
    endMapInteraction: vi.fn(),
    disposeMapInteractions: vi.fn(),
    installMapInteractions: vi.fn(),
    closeSelectionPopup: vi.fn(),
    closeSwissMobilityPopup: vi.fn(),
    closeTrackPointPopup: vi.fn(),
    clearFocusedMediaMarker: vi.fn(),
    clearLocationSearchMarker: vi.fn(),
    trackPointsDetailsCache: new Map(),
    trackPointsCanonicalCache: new Map(),
    detachTrackPointLayerHandlers: vi.fn(),
    captureBasemapLayers: vi.fn(),
    applyBasemapAppearance: vi.fn(),
    applyTerrainPreference: vi.fn(),
    restoreMediaLayerPreference: vi.fn(),
    applyActiveOverlays: vi.fn(),
    updateGlobeState: vi.fn(),
  };
  const methods = { ...useMapTools(), ...useMapRendererLifecycle({ mapSettingsStore: {} as never }) };
  for (const [name, method] of Object.entries(methods)) {
    if (!(name in context)) Object.assign(context, { [name]: method.bind(context as never) });
  }
  return context as unknown as typeof methods & {
    $refs: typeof context.$refs;
    overlayMap: { flyTo: ReturnType<typeof vi.fn>; jumpTo: ReturnType<typeof vi.fn> } | undefined;
    gpsMarker: { element: HTMLElement; coordinates: number[] } | null;
    gpsLocation: number[] | null;
    gpsDeviceEnabledDisabled: boolean;
    gpsFollowing: boolean;
  };
}

describe('GPS map marker lifecycle', () => {
  beforeEach(() => {
    requests.config.mockReset().mockResolvedValue({ tileMode: 'remote', offline: false });
    requests.status.mockReset();
    requests.warn.mockClear();
    mapLoad.ready = true;
    mapLoad.callbacks = [];
    vi.stubGlobal(
      'ResizeObserver',
      class {
        observe() {}
        disconnect() {}
      }
    );
  });

  afterEach(() => vi.unstubAllGlobals());

  it('renders and moves one marker after GPS positions arrive', async () => {
    const runtime = createRuntime();
    await runtime.initMap();
    runtime.onGPSDeviceEnabledDisabled(true);
    runtime.onLocationUpdate(position());
    const marker = runtime.gpsMarker!;
    expect(marker.element.parentElement).toBe(runtime.$refs.mapContainer);
    expect(marker.coordinates).toEqual([8, 47]);

    runtime.onLocationUpdate(position(9, 48));
    expect(runtime.gpsMarker).toBe(marker);
    expect(marker.coordinates).toEqual([9, 48]);
    expect(runtime.$refs.mapContainer.childElementCount).toBe(1);
  });

  it('restores the marker after replacing the map without waiting for another GPS fix', async () => {
    const runtime = createRuntime();
    await runtime.initMap();
    runtime.onGPSDeviceEnabledDisabled(true);
    runtime.onLocationUpdate(position());
    const oldMarker = runtime.gpsMarker!;
    const oldMap = runtime.overlayMap;

    await runtime.initMap();

    expect(runtime.overlayMap).not.toBe(oldMap);
    expect(runtime.gpsMarker).not.toBe(oldMarker);
    expect(runtime.$refs.mapContainer.childElementCount).toBe(1);
    expect(runtime.gpsMarker?.coordinates).toEqual([8, 47]);
    expect(runtime.overlayMap?.jumpTo).toHaveBeenCalledWith({ center: [8, 47] });
    runtime.onLocationUpdate(position(9, 48));
    expect(runtime.gpsMarker?.coordinates).toEqual([9, 48]);
    expect(runtime.$refs.mapContainer.childElementCount).toBe(1);
  });

  it('restores a position received before the map was created', async () => {
    const runtime = createRuntime();
    runtime.onGPSDeviceEnabledDisabled(true);
    runtime.onLocationUpdate(position());
    expect(runtime.gpsMarker).toBeNull();

    await runtime.initMap();

    expect(runtime.$refs.mapContainer.childElementCount).toBe(1);
    expect(runtime.gpsMarker?.coordinates).toEqual([8, 47]);
  });

  it('restores the marker while remote tiles are still loading', async () => {
    const runtime = createRuntime();
    await runtime.initMap();
    runtime.onGPSDeviceEnabledDisabled(true);
    runtime.onLocationUpdate(position());
    mapLoad.ready = false;

    const rebuilding = runtime.initMap();
    await flushPromises();

    try {
      expect(runtime.$refs.mapContainer.childElementCount).toBe(1);
      expect(runtime.gpsMarker?.coordinates).toEqual([8, 47]);
    } finally {
      mapLoad.ready = true;
      mapLoad.callbacks.splice(0).forEach((callback) => callback());
      await rebuilding;
    }
  });

  it('keeps a drifted marker visible after a rebuild without recentering the map', async () => {
    const runtime = createRuntime();
    await runtime.initMap();
    runtime.onGPSDeviceEnabledDisabled(true);
    runtime.onLocationUpdate(position());
    runtime.gpsFollowing = false;

    await runtime.initMap();
    runtime.onLocationUpdate(position(9, 48));

    expect(runtime.$refs.mapContainer.childElementCount).toBe(1);
    expect(runtime.overlayMap?.flyTo).not.toHaveBeenCalled();
    expect(runtime.overlayMap?.jumpTo).not.toHaveBeenCalled();
    expect(runtime.gpsFollowing).toBe(false);
  });

  it('removes the marker when GPS stops and does not restore a disabled cached position', async () => {
    const runtime = createRuntime();
    await runtime.initMap();
    runtime.onGPSDeviceEnabledDisabled(true);
    runtime.onLocationUpdate(position());

    runtime.onGPSDeviceEnabledDisabled(false);
    await runtime.initMap();

    expect(runtime.gpsMarker).toBeNull();
    expect(runtime.$refs.mapContainer.childElementCount).toBe(0);
  });

  it('clears the marker reference when the renderer is disposed', async () => {
    const runtime = createRuntime();
    await runtime.initMap();
    runtime.onGPSDeviceEnabledDisabled(true);
    runtime.onLocationUpdate(position());

    runtime.disposeRendererMaps();

    expect(runtime.gpsMarker).toBeNull();
    expect(runtime.$refs.mapContainer.childElementCount).toBe(0);
  });

  it('does not create a map after disposal during the configuration request', async () => {
    let resolveConfig!: (config: unknown) => void;
    requests.config.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveConfig = resolve;
        })
    );
    const runtime = createRuntime();
    const pending = runtime.initMap();
    runtime.disposeRendererMaps();
    runtime.$refs.mapContainer = null as unknown as HTMLElement;
    resolveConfig({ tileMode: 'remote' });
    await expect(pending).resolves.toBe(false);
    expect(runtime.overlayMap).toBeUndefined();
  });

  it('keeps the newer map when an older configuration request finishes last', async () => {
    let resolveConfig!: (config: unknown) => void;
    requests.config.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveConfig = resolve;
        })
    );
    const runtime = createRuntime();
    const obsolete = runtime.initMap();
    await expect(runtime.initMap()).resolves.toBe(true);
    const currentMap = runtime.overlayMap;
    resolveConfig({ tileMode: 'remote' });
    await expect(obsolete).resolves.toBe(false);
    expect(runtime.overlayMap).toBe(currentMap);
  });

  it('settles initialization when a map is removed before its load event', async () => {
    mapLoad.ready = false;
    const runtime = createRuntime();
    const pending = runtime.initMap();
    await flushPromises();
    runtime.disposeRendererMaps();
    await expect(pending).resolves.toBe(false);
    expect(runtime.overlayMap).toBeUndefined();
  });

  it('does not create a map after disposal during the status request', async () => {
    requests.config.mockResolvedValue({ tileMode: 'local', offline: false });
    let resolveStatus!: (status: unknown) => void;
    requests.status.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveStatus = resolve;
        })
    );
    const runtime = createRuntime();
    Object.assign(runtime, { mapSourceMode: 'local' });
    const pending = runtime.initMap();
    await flushPromises();
    runtime.disposeRendererMaps();
    resolveStatus({ ready: true });
    await expect(pending).resolves.toBe(false);
    expect(runtime.overlayMap).toBeUndefined();
  });

  it('keeps a hidden-page pause out of failure diagnostics and unreachable status', async () => {
    requests.config.mockResolvedValue({ tileMode: 'local', offline: false });
    requests.status.mockRejectedValue(new StatusRequestPausedError());
    const runtime = createRuntime();
    Object.assign(runtime, { mapSourceMode: 'local' });
    await expect(runtime.initMap()).resolves.toBe(true);
    expect(requests.warn).not.toHaveBeenCalled();
    expect((runtime as unknown as { mapServerStatus?: unknown }).mapServerStatus).toBeUndefined();
  });

  it('releases parallel track loading when initialization fails', async () => {
    const runtime = createRuntime();
    const outcomes: boolean[] = [];
    Object.assign(runtime, {
      closeSelectionPopup: vi.fn(),
      closeSwissMobilityPopup: vi.fn(),
      closeTrackPointPopup: vi.fn(),
      clearFocusedMediaMarker: vi.fn(),
      clearLocationSearchMarker: vi.fn(),
      trackPointsDetailsCache: new Map(),
      trackPointsCanonicalCache: new Map(),
      fetchTracksAndFallback: async () => {
        outcomes.push(await (runtime as unknown as { _mapReadyPromise: Promise<boolean> })._mapReadyPromise);
      },
      initMap: vi.fn().mockRejectedValue(new Error('initialization failed')),
    });
    await expect(runtime.reloadMap()).rejects.toThrow('initialization failed');
    await flushPromises();
    expect(outcomes).toEqual([false]);
  });

  it('keeps an overlapping reload from settling the newer readiness gate', async () => {
    let resolveConfig!: (config: unknown) => void;
    requests.config.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveConfig = resolve;
        })
    );
    const runtime = createRuntime();
    const outcomes: boolean[] = [];
    Object.assign(runtime, {
      fetchTracksAndFallback: async () => {
        outcomes.push(await (runtime as unknown as { _mapReadyPromise: Promise<boolean> })._mapReadyPromise);
      },
    });
    const obsolete = runtime.reloadMap();
    const gate = (runtime as unknown as { _mapReadyPromise: Promise<boolean> })._mapReadyPromise;
    const newer = runtime.reloadMap();
    await expect(gate).resolves.toBe(false);
    await newer;
    const currentMap = runtime.overlayMap;
    resolveConfig({ tileMode: 'remote' });
    await obsolete;
    expect(outcomes).toEqual([true]);
    expect(runtime.overlayMap).toBe(currentMap);
    expect((runtime as unknown as { _mapReadyPromise: unknown })._mapReadyPromise).toBeNull();
  });

  it('cancels old parallel tracks when a renderer rebuild supersedes a reload', async () => {
    mapLoad.ready = false;
    const runtime = createRuntime();
    const outcomes: boolean[] = [];
    Object.assign(runtime, {
      fetchTracksAndFallback: async () => {
        outcomes.push(await (runtime as unknown as { _mapReadyPromise: Promise<boolean> })._mapReadyPromise);
      },
    });
    const obsolete = runtime.reloadMap();
    await flushPromises();
    mapLoad.ready = true;
    await expect(runtime.initMap()).resolves.toBe(true);
    const currentMap = runtime.overlayMap;
    await obsolete;
    expect(outcomes).toEqual([false]);
    expect(runtime.overlayMap).toBe(currentMap);
  });

  it('restarts startup on the new map when a layer rebuild supersedes a parallel reload', async () => {
    mapLoad.ready = false;
    const runtime = createRuntime();
    const outcomes: boolean[] = [];
    const context = Object.assign(runtime, {
      initialLoadDone: false,
      _mapReadyPromise: null as Promise<boolean> | null,
      $emit: vi.fn(),
      addTracksToMap: vi.fn(),
      fetchTracksAndFallback: vi.fn(async () => {
        const ready = await context._mapReadyPromise;
        outcomes.push(ready === true);
        if (ready) context.initialLoadDone = true;
      }),
    });
    const obsolete = runtime.reloadMap();
    await flushPromises();
    mapLoad.ready = true;
    await rebuildMapLayers(context as never);
    await obsolete;

    expect(outcomes).toEqual([false, true]);
    expect(context.fetchTracksAndFallback).toHaveBeenCalledTimes(2);
    expect(context.initialLoadDone).toBe(true);
    expect(context._mapReadyPromise).toBeNull();
  });
});
