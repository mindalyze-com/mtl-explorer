import { flushPromises, mount } from '@vue/test-utils';
import type { VueWrapper } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { nextTick } from 'vue';
import TrackDetailMiniMap from '@/components/trackdetails/TrackDetailMiniMap.vue';
import { useTrackMapSync } from '@/composables/useTrackMapSync';
import { resolveConfiguredMapStyle } from '@/components/map/mapStyleResolver';

const maplibreMock = vi.hoisted(() => {
  type Listener = (...args: unknown[]) => void;
  const MOCK_MAP_ZOOM = 12;

  class MockGeoJsonSource {
    data: GeoJSON.FeatureCollection;
    setData = vi.fn((data: GeoJSON.FeatureCollection) => {
      this.data = data;
    });

    constructor(data: GeoJSON.FeatureCollection) {
      this.data = data;
    }
  }

  class MockLngLatBounds {
    coordinates: [number, number][];

    constructor(sw: [number, number], ne: [number, number]) {
      this.coordinates = [sw, ne];
    }

    extend(coordinate: [number, number]) {
      this.coordinates.push(coordinate);
      return this;
    }
  }

  class MockPopup {
    static instances: MockPopup[] = [];

    options?: Record<string, unknown>;
    setLngLat = vi.fn(() => this);
    setHTML = vi.fn(() => this);
    setDOMContent = vi.fn(() => this);
    addTo = vi.fn(() => this);
    remove = vi.fn();

    constructor(options?: Record<string, unknown>) {
      this.options = options;
      MockPopup.instances.push(this);
    }
  }

  class MockMarker {
    static instances: MockMarker[] = [];

    element?: HTMLElement;
    setLngLat = vi.fn(() => this);
    addTo = vi.fn(() => this);
    remove = vi.fn();

    constructor(options?: { element?: HTMLElement }) {
      this.element = options?.element;
      MockMarker.instances.push(this);
    }
  }

  class MockMap {
    static instances: MockMap[] = [];
    static nextStyleLoaded = true;

    styleLoadedValue: boolean;
    loadedValue = true;
    sources = new Map<string, MockGeoJsonSource>();
    layers = new Map<string, unknown>();
    images = new Set<string>();
    listeners = new Map<string, Listener[]>();
    onceListeners = new Map<string, Listener[]>();
    canvas = { style: { cursor: '' }, addEventListener: vi.fn(), removeEventListener: vi.fn() };
    resize = vi.fn();
    fitBounds = vi.fn();
    easeTo = vi.fn();
    moveLayer = vi.fn();
    remove = vi.fn();
    setStyle = vi.fn(() => {
      this.sources.clear();
      this.layers.clear();
      this.images.clear();
      return this;
    });
    getZoom = vi.fn(() => MOCK_MAP_ZOOM);
    project = vi.fn((coordinate: [number, number]) => ({ x: coordinate[0] * 10_000, y: coordinate[1] * 10_000 }));
    queryRenderedFeatures = vi.fn(() => []);
    setMissingStyleImageResolver = vi.fn();

    constructor() {
      this.styleLoadedValue = MockMap.nextStyleLoaded;
      MockMap.instances.push(this);
    }

    loaded() {
      return this.loadedValue;
    }

    isStyleLoaded() {
      return this.styleLoadedValue;
    }

    on(event: string, layerOrHandler: string | Listener, maybeHandler?: Listener) {
      const key = typeof layerOrHandler === 'string' ? `${event}:${layerOrHandler}` : event;
      const handler = typeof layerOrHandler === 'string' ? maybeHandler : layerOrHandler;
      if (handler) this.addListener(this.listeners, key, handler);
      return this;
    }

    once(event: string, handler: Listener) {
      this.addListener(this.onceListeners, event, handler);
      return this;
    }

    off(event: string, handler: Listener) {
      this.removeListener(this.listeners, event, handler);
      this.removeListener(this.onceListeners, event, handler);
      return this;
    }

    emit(event: string, payload?: unknown) {
      for (const handler of this.listeners.get(event) ?? []) {
        handler(payload);
      }
      const once = this.onceListeners.get(event) ?? [];
      this.onceListeners.delete(event);
      for (const handler of once) {
        handler(payload);
      }
    }

    addSource(id: string, source: { data: GeoJSON.FeatureCollection }) {
      this.sources.set(id, new MockGeoJsonSource(source.data));
    }

    getSource(id: string) {
      return this.sources.get(id);
    }

    getGlyphs() {
      return undefined;
    }

    addLayer(layer: { id: string }) {
      this.layers.set(layer.id, layer);
    }

    getLayer(id: string) {
      return this.layers.get(id);
    }

    hasImage(id: string) {
      return this.images.has(id);
    }

    addImage(id: string) {
      this.images.add(id);
    }

    getCanvas() {
      return this.canvas;
    }

    private addListener(target: Map<string, Listener[]>, event: string, handler: Listener) {
      target.set(event, [...(target.get(event) ?? []), handler]);
    }

    private removeListener(target: Map<string, Listener[]>, event: string, handler: Listener) {
      const handlers = target.get(event);
      if (!handlers) return;
      target.set(
        event,
        handlers.filter((existing) => existing !== handler)
      );
    }
  }

  return {
    MockMap,
    MockLngLatBounds,
    MockMarker,
    MockPopup,
  };
});

const chartSyncMocks = vi.hoisted(() => ({
  clearChartCrosshairs: vi.fn(),
  showChartsAtPoint: vi.fn(),
}));

import { probeVectorMapArchive } from '@/utils/mapArchiveHealth';
vi.mock('@/utils/mapArchiveHealth', () => ({ probeVectorMapArchive: vi.fn() }));

vi.mock('maplibre-gl', () => ({
  Map: maplibreMock.MockMap,
  LngLatBounds: maplibreMock.MockLngLatBounds,
  Marker: maplibreMock.MockMarker,
  Popup: maplibreMock.MockPopup,
}));

vi.mock('@/utils/mapConfigService', () => ({
  MapConfigDtoTileModeEnum: { Local: 'local', Remote: 'remote' },
  fetchMapConfig: vi.fn(async () => ({
    tileMode: 'remote',
    remoteRasterStyles: {
      light: {
        url: 'https://example.test/{z}/{x}/{y}.png',
        attribution: '© Example Tiles',
      },
    },
  })),
  mainTileArchiveUrl: vi.fn(() => 'mock.pmtiles'),
}));

vi.mock('@/components/map/mapStyleResolver', () => ({
  resolveConfiguredMapStyle: vi.fn(() => ({
    style: { version: 8, sources: {}, layers: [] },
    styleMode: 'test-style',
  })),
}));

vi.mock('@/composables/useChartSync', () => ({
  useChartSync: () => ({
    clearChartCrosshairs: chartSyncMocks.clearChartCrosshairs,
    showChartsAtPoint: chartSyncMocks.showChartsAtPoint,
  }),
}));

vi.mock('@/utils/mapStyle', () => ({
  TOPO_CONTRAST_THEME: 'topo-contrast',
  buildLocalVectorStyleFromArchiveUrl: vi.fn(() => ({ version: 8, sources: {}, layers: [] })),
  buildRemoteRasterStyle: vi.fn(() => ({ version: 8, sources: {}, layers: [] })),
  normalizeMapTheme: vi.fn((theme, fallback = 'light') => theme ?? fallback),
}));

const mountedWrappers: VueWrapper[] = [];

function trackEvent(lng: number, lat: number) {
  return {
    id: lng + lat,
    eventType: 'STOP',
    startTimestamp: new Date('2026-01-01T10:00:00Z'),
    durationInSec: 120,
    startPointLongLat: { coordinates: [lng, lat] },
  };
}

function trackEventWithoutGeometry(id: number, startPointIndex: number, endPointIndex = startPointIndex) {
  return {
    id,
    eventType: 'STOP',
    startTimestamp: new Date('2026-01-01T10:00:00Z'),
    durationInSec: 120,
    startPointIndex,
    endPointIndex,
  };
}

async function mountMiniMap(
  trackEvents: Record<string, unknown>[] = [],
  trackCoordinates: number[][] = [],
  trackMedia: Record<string, unknown>[] = [],
  mediaInteractionEnabled = false
) {
  const wrapper = mount(TrackDetailMiniMap, {
    props: {
      gpsTrackId: 1,
      trackEvents,
      trackCoordinates,
      trackMedia,
      mediaInteractionEnabled,
    },
    attachTo: document.body,
  });
  mountedWrappers.push(wrapper);
  await nextTick();
  await flushPromises();
  await nextTick();
  return wrapper;
}

function dispatchPointer(
  target: EventTarget,
  type: string,
  init: { pointerId?: number; pointerType?: string; clientX: number; clientY: number }
): PointerEvent {
  const event = new MouseEvent(type, {
    bubbles: true,
    cancelable: true,
    button: 0,
    clientX: init.clientX,
    clientY: init.clientY,
  }) as PointerEvent;
  Object.defineProperty(event, 'pointerId', { value: init.pointerId ?? 1 });
  Object.defineProperty(event, 'pointerType', { value: init.pointerType ?? 'touch' });
  target.dispatchEvent(event);
  return event;
}

function detailEventSource() {
  return maplibreMock.MockMap.instances[0]?.getSource('detail-events');
}

function detailTrackSource() {
  return maplibreMock.MockMap.instances[0]?.getSource('detail-track');
}

function selectedEventSource() {
  return maplibreMock.MockMap.instances[0]?.getSource('detail-selected-event');
}

describe('TrackDetailMiniMap event layer', () => {
  let originalGetContext: typeof HTMLCanvasElement.prototype.getContext;

  beforeEach(() => {
    vi.stubGlobal('Path2D', class MockPath2D {});
    originalGetContext = HTMLCanvasElement.prototype.getContext;
    maplibreMock.MockMap.instances.length = 0;
    maplibreMock.MockMarker.instances.length = 0;
    maplibreMock.MockPopup.instances.length = 0;
    maplibreMock.MockMap.nextStyleLoaded = true;
    chartSyncMocks.clearChartCrosshairs.mockReset();
    chartSyncMocks.showChartsAtPoint.mockReset();
    localStorage.clear();
    setActivePinia(createPinia());
    useTrackMapSync().clearAll();
    HTMLCanvasElement.prototype.getContext = vi.fn(() => ({
      scale: vi.fn(),
      translate: vi.fn(),
      rotate: vi.fn(),
      beginPath: vi.fn(),
      roundRect: vi.fn(),
      fill: vi.fn(),
      stroke: vi.fn(),
      getImageData: vi.fn(() => ({ data: new Uint8ClampedArray(4), width: 1, height: 1 })),
      fillStyle: '',
      strokeStyle: '',
      lineWidth: 1,
    })) as unknown as typeof HTMLCanvasElement.prototype.getContext;
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    for (const wrapper of mountedWrappers.splice(0)) {
      wrapper.unmount();
    }
    useTrackMapSync().clearAll();
    vi.clearAllTimers();
    vi.useRealTimers();
    vi.mocked(probeVectorMapArchive).mockReset();
    HTMLCanvasElement.prototype.getContext = originalGetContext;
    document.body.innerHTML = '';
  });

  it('falls back only for local basemap errors and restores the route and break markers', async () => {
    vi.mocked(resolveConfiguredMapStyle).mockReturnValueOnce({
      style: { version: 8, sources: {}, layers: [] },
      styleMode: 'local-vector',
      attributions: [],
    });
    await mountMiniMap(
      [trackEvent(7.45, 46.95)],
      [
        [7.44, 46.94],
        [7.45, 46.95],
      ]
    );
    const map = maplibreMock.MockMap.instances[0];
    map.emit('error', { sourceId: 'detail-track', error: new Error('Unrelated overlay error') });
    expect(map.setStyle).not.toHaveBeenCalled();

    vi.useFakeTimers();
    vi.mocked(probeVectorMapArchive).mockRejectedValue(new Error('Bad response code: 503'));
    map.emit('error', { sourceId: 'protomaps', error: new Error('Bad response code: 503') });
    expect(map.setStyle).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(4000);
    expect(map.setStyle).toHaveBeenCalledOnce();
    expect(resolveConfiguredMapStyle).toHaveBeenLastCalledWith(expect.objectContaining({ localTilesReady: false }));
    map.emit('style.load');
    await flushPromises();
    expect(detailTrackSource()?.data.features).toHaveLength(1);
    expect(detailEventSource()?.data.features).toHaveLength(1);

    map.emit('error', { sourceId: 'protomaps', error: new Error('Late local tile error') });
    expect(map.setStyle).toHaveBeenCalledOnce();
  });

  it('draws break markers after a deferred style-ready retry', async () => {
    maplibreMock.MockMap.nextStyleLoaded = false;
    const wrapper = await mountMiniMap();
    const map = maplibreMock.MockMap.instances[0];

    await wrapper.setProps({ trackEvents: [trackEvent(8.5, 47.4)] });

    expect(detailEventSource()).toBeUndefined();

    map.styleLoadedValue = true;
    map.emit('idle');
    await nextTick();

    const source = detailEventSource();
    expect(source?.data.features).toHaveLength(1);
    expect(source?.data.features[0].geometry.coordinates).toEqual([8.5, 47.4]);
    expect(map.getLayer('detail-events-layer')).toBeTruthy();
  });

  it('labels the toggle Events and includes gap endpoints and duration in the marker popup data', async () => {
    const gap = {
      ...trackEvent(8.5, 47.4),
      eventType: 'GPS_GAP',
      label: 'Lost signal',
      startTimestamp: new Date(2026, 9, 12, 11, 15),
      endTimestamp: new Date(2026, 9, 12, 12, 33),
      durationInSec: undefined,
    };
    const wrapper = await mountMiniMap([gap]);
    const toggle = wrapper.get('.map-overlay-events-btn');
    expect(toggle.text()).toContain('Events');
    expect(toggle.attributes('title')).toBe('Hide events on map');
    expect(detailEventSource()?.data.features[0].properties).toMatchObject({
      label: 'Recording gap',
      duration: '1 h 18 min',
      icon: 'detail-recording-gap-diamond',
    });
    expect(detailEventSource()?.data.features[0].properties?.time).toContain('11:15–12:33');
    const feature = detailEventSource()?.data.features[0];
    maplibreMock.MockMap.instances[0].listeners.get('click:detail-events-layer')?.[0]?.({ features: [feature] });
    const popup = maplibreMock.MockPopup.instances[0];
    await toggle.trigger('click');
    expect(detailEventSource()?.data.features).toHaveLength(0);
    expect(popup.remove).toHaveBeenCalledOnce();
    expect(toggle.attributes('title')).toBe('Show events on map');
    await toggle.trigger('click');
    expect(detailEventSource()?.data.features).toHaveLength(1);
  });

  it('shows all breaks arriving after mount and preserves an explicit hide through refresh and selection', async () => {
    const wrapper = await mountMiniMap();
    const events = [trackEvent(8.5, 47.4), trackEvent(8.6, 47.5)];
    const toggle = wrapper.get('.map-overlay-events-btn');
    await wrapper.setProps({ trackEvents: events });
    expect(toggle.attributes('aria-pressed')).toBe('true');
    expect(detailEventSource()?.data.features.map((feature) => feature.geometry)).toEqual([
      { type: 'Point', coordinates: [8.5, 47.4] },
      { type: 'Point', coordinates: [8.6, 47.5] },
    ]);

    await wrapper.setProps({ selectedEventKey: events[0].id });
    await toggle.trigger('click');
    expect(detailEventSource()?.data.features).toHaveLength(0);
    expect(selectedEventSource()?.data.features).toHaveLength(0);

    await wrapper.setProps({ trackEvents: [] });
    await wrapper.setProps({ trackEvents: [...events], selectedEventKey: events[1].id });
    expect(toggle.attributes('aria-pressed')).toBe('false');
    expect(detailEventSource()?.data.features).toHaveLength(0);
    expect(selectedEventSource()?.data.features).toHaveLength(0);
    expect(maplibreMock.MockMap.instances[0].easeTo).toHaveBeenLastCalledWith(
      expect.objectContaining({ center: [8.6, 47.5] })
    );

    await toggle.trigger('click');
    expect(detailEventSource()?.data.features).toHaveLength(2);
    expect(selectedEventSource()?.data.features).toHaveLength(1);
  });

  it('updates an existing event source even while the style is temporarily busy', async () => {
    const wrapper = await mountMiniMap([trackEvent(8.5, 47.4)]);
    const map = maplibreMock.MockMap.instances[0];
    const source = detailEventSource();

    expect(source?.data.features).toHaveLength(1);

    source?.setData.mockClear();
    map.styleLoadedValue = false;
    await wrapper.setProps({ trackEvents: [trackEvent(8.6, 47.5)] });

    expect(source?.setData).toHaveBeenCalledTimes(1);
    expect(source?.data.features).toHaveLength(1);
    expect(source?.data.features[0].geometry.coordinates).toEqual([8.6, 47.5]);
  });

  it('draws and fits the track line from provided coordinates before shared track points are loaded', async () => {
    await mountMiniMap(
      [],
      [
        [8.4, 47.3, 410, 1_700_000_000],
        [8.5, 47.4, 420, 1_700_000_010],
        [8.6, 47.5, 430, 1_700_000_020],
      ]
    );

    const map = maplibreMock.MockMap.instances[0];
    const source = detailTrackSource();

    expect(useTrackMapSync().getTrackPoints()).toHaveLength(0);
    expect(source?.data.features[0].geometry).toEqual({
      type: 'LineString',
      coordinates: [
        [8.4, 47.3],
        [8.5, 47.4],
        [8.6, 47.5],
      ],
    });
    expect(map.fitBounds).toHaveBeenCalledTimes(1);
    expect(map.fitBounds.mock.calls[0]?.[1]).toMatchObject({
      padding: { top: 20, right: 20, bottom: 20, left: 20 },
      duration: 0,
    });
  });

  it('clears the active map selection when empty map space is clicked', async () => {
    const wrapper = await mountMiniMap();
    const map = maplibreMock.MockMap.instances[0];

    map.emit('click', { lngLat: { lat: 47.4, lng: 8.5 }, point: { x: 20, y: 20 } });
    await nextTick();

    expect(wrapper.emitted('select-event')).toEqual([[null]]);
    expect(wrapper.emitted('clear-selection')).toEqual([[]]);
  });

  it.each([36, 78])(
    'fits tracks below the %s pixel marker controls, including wrapped controls',
    async (controlsHeight) => {
      const measure = vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(function () {
        if (this.classList.contains('mini-map-body')) return { top: 100, bottom: 340, height: 240 } as DOMRect;
        if (this.classList.contains('map-overlay-filters'))
          return { top: 106, bottom: 106 + controlsHeight, height: controlsHeight } as DOMRect;
        return { top: 0, bottom: 0, height: 0 } as DOMRect;
      });
      try {
        const wrapper = await mountMiniMap(
          [],
          [
            [8.4, 47.3],
            [8.6, 47.5],
          ]
        );
        const map = maplibreMock.MockMap.instances[0];
        const expectedPadding = { top: 26 + controlsHeight, right: 20, bottom: 20, left: 20 };
        expect(map.fitBounds.mock.calls[0]?.[1]).toMatchObject({ padding: expectedPadding });
        await wrapper.get('[aria-label="Collapse map"]').trigger('click');
        await wrapper.get('.mini-map-collapsed-strip').trigger('click');
        await nextTick();
        expect(map.fitBounds.mock.lastCall?.[1]).toMatchObject({ padding: expectedPadding });
      } finally {
        measure.mockRestore();
      }
    }
  );

  it('does not clear the active selection while the map is panned', async () => {
    const wrapper = await mountMiniMap();
    const map = maplibreMock.MockMap.instances[0];

    map.emit('dragstart');
    map.emit('move');
    map.emit('dragend');
    await nextTick();

    expect(wrapper.emitted('clear-selection')).toBeUndefined();
  });

  it('draws track coordinates after initial map load even while tiles are still loading', async () => {
    maplibreMock.MockMap.nextStyleLoaded = false;

    await mountMiniMap(
      [],
      [
        [8.4, 47.3],
        [8.5, 47.4],
      ]
    );

    const map = maplibreMock.MockMap.instances[0];

    expect(detailTrackSource()?.data.features[0].geometry).toEqual({
      type: 'LineString',
      coordinates: [
        [8.4, 47.3],
        [8.5, 47.4],
      ],
    });
    expect(map.fitBounds).toHaveBeenCalledTimes(1);
    expect(map.fitBounds.mock.calls[0]?.[1]).toMatchObject({
      padding: { top: 20, right: 20, bottom: 20, left: 20 },
      duration: 0,
    });
  });

  it('uses only the matched route position for estimated photo markers', async () => {
    const wrapper = await mountMiniMap(
      [],
      [],
      [
        {
          id: 41,
          fileName: 'estimated.jpg',
          mediaKind: 'IMAGE',
          positionOrigin: 'TRACK_INTERPOLATED',
          estimatedPosition: true,
          originalLat: 1,
          originalLng: 2,
          routeLat: 47.4,
          routeLng: 8.5,
          resolvedLat: 47.4,
          resolvedLng: 8.5,
        },
      ],
      true
    );

    const marker = maplibreMock.MockMarker.instances[0];
    expect(marker.setLngLat).toHaveBeenCalledWith([8.5, 47.4]);
    expect(marker.element?.classList.contains('mini-map-photo-marker--estimated')).toBe(true);
    expect(marker.element?.getAttribute('aria-label')).toContain('Estimated from photo time and activity track');

    marker.element?.click();
    await nextTick();
    expect(wrapper.emitted('select-media')).toEqual([[41]]);
  });

  it('keeps media markers clickable outside the Media tab', async () => {
    const wrapper = await mountMiniMap(
      [],
      [],
      [
        {
          id: 45,
          fileName: 'activity.mp4',
          mediaKind: 'VIDEO',
          positionOrigin: 'TRACK_INTERPOLATED',
          estimatedPosition: true,
          resolvedLat: 47.4,
          resolvedLng: 8.5,
        },
      ]
    );
    const marker = maplibreMock.MockMarker.instances[0];
    const button = marker.element as HTMLButtonElement;

    expect(button.disabled).toBe(false);
    button.click();
    expect(wrapper.emitted('select-media')).toEqual([[45]]);

    await wrapper.setProps({ mediaInteractionEnabled: true });
    expect(button.disabled).toBe(false);
    button.click();
    expect(wrapper.emitted('select-media')).toEqual([[45], [45]]);

    await wrapper.setProps({ mediaInteractionEnabled: false });
    button.click();
    expect(wrapper.emitted('select-media')).toEqual([[45], [45], [45]]);
  });

  it('hides and restores media independently of breaks and retains the filter across media updates', async () => {
    const item = { id: 45, resolvedLat: 47.4, resolvedLng: 8.5 };
    const wrapper = await mountMiniMap([trackEvent(8.5, 47.4)], [], [item]);
    const marker = maplibreMock.MockMarker.instances[0];
    const filter = wrapper.get('[aria-label="Toggle activity media"]');
    await filter.trigger('click');
    expect(filter.attributes('aria-pressed')).toBe('false');
    expect(marker.remove).toHaveBeenCalled();
    expect(detailEventSource()?.data.features).toHaveLength(1);

    const markerCount = maplibreMock.MockMarker.instances.length;
    await wrapper.setProps({ trackMedia: [item, { id: 46, resolvedLat: 47.5, resolvedLng: 8.6 }] });
    expect(maplibreMock.MockMarker.instances).toHaveLength(markerCount);
    await filter.trigger('click');
    expect(filter.attributes('aria-pressed')).toBe('true');
    expect(maplibreMock.MockMarker.instances).toHaveLength(markerCount + 2);

    await filter.trigger('click');
    await wrapper.setProps({ gpsTrackId: 2 });
    expect(filter.attributes('aria-pressed')).toBe('true');
  });

  it('keeps photo markers visible but disables selection while scrubbing graphs', async () => {
    const wrapper = await mountMiniMap([], [], [{ id: 45, resolvedLat: 47.4, resolvedLng: 8.5 }]);
    const marker = maplibreMock.MockMarker.instances[0];
    const button = marker.element as HTMLButtonElement;

    await wrapper.setProps({ markerInteractionEnabled: false });
    expect(button.disabled).toBe(true);
    button.click();
    expect(wrapper.emitted('select-media')).toBeUndefined();
    expect(marker.remove).not.toHaveBeenCalled();

    await wrapper.setProps({ trackMedia: [{ id: 46, resolvedLat: 47.5, resolvedLng: 8.6 }] });
    const refreshedButton = maplibreMock.MockMarker.instances[1].element as HTMLButtonElement;
    expect(refreshedButton.disabled).toBe(true);
    await wrapper.setProps({ markerInteractionEnabled: true });
    expect(refreshedButton.disabled).toBe(false);
    refreshedButton.click();
    expect(wrapper.emitted('select-media')).toEqual([[46]]);
  });

  it('labels partial media pages and exposes loading and retry states', async () => {
    const wrapper = await mountMiniMap([], [], [{ id: 45, resolvedLat: 47.4, resolvedLng: 8.5 }]);
    await wrapper.setProps({ mediaTotalItems: 101 });
    const filter = wrapper.get('[aria-label="Toggle activity media"]');
    expect(filter.text()).toContain('1 / 101');
    expect(filter.attributes('title')).toContain('Open Media to browse all 101 items');

    await wrapper.setProps({ mediaLoading: true });
    expect(filter.attributes('disabled')).toBeDefined();
    expect(filter.attributes('aria-busy')).toBe('true');
    await wrapper.setProps({ mediaLoading: false, mediaError: 'Could not load media' });
    await wrapper.get('[aria-label="Retry activity media"]').trigger('click');
    expect(wrapper.emitted('retry-media')).toEqual([[]]);
  });

  it('hides the track-point marker in media mode and restores it after leaving', async () => {
    const wrapper = await mountMiniMap();
    useTrackMapSync().setPinnedPoint({
      lat: 47.4,
      lng: 8.5,
      altitude: null,
      timestamp: 0,
      distanceKm: 0,
      pointIndex: 0,
    });
    await nextTick();

    const trackPointMarker = maplibreMock.MockMarker.instances[0];
    expect(trackPointMarker.addTo).toHaveBeenCalledTimes(1);

    await wrapper.setProps({ mediaInteractionEnabled: true });
    expect(trackPointMarker.remove).toHaveBeenCalledTimes(1);

    await wrapper.setProps({ mediaInteractionEnabled: false });
    expect(trackPointMarker.addTo).toHaveBeenCalledTimes(2);
  });

  it('highlights a photo marker without rebuilding the marker collection', async () => {
    const wrapper = await mountMiniMap(
      [],
      [],
      [
        {
          id: 41,
          fileName: 'estimated.jpg',
          mediaKind: 'IMAGE',
          positionOrigin: 'TRACK_INTERPOLATED',
          estimatedPosition: true,
          resolvedLat: 47.4,
          resolvedLng: 8.5,
        },
      ]
    );
    const marker = maplibreMock.MockMarker.instances[0];

    await wrapper.setProps({ highlightedMediaId: 41 });
    expect(marker.element?.classList.contains('mini-map-photo-marker--highlighted')).toBe(true);
    expect(maplibreMock.MockMarker.instances).toHaveLength(1);

    await wrapper.setProps({ highlightedMediaId: null, selectedMediaId: 41 });
    expect(marker.element?.classList.contains('mini-map-photo-marker--highlighted')).toBe(false);
    expect(marker.element?.classList.contains('mini-map-photo-marker--selected')).toBe(true);
    expect(maplibreMock.MockMarker.instances).toHaveLength(1);
  });

  it('uses only original EXIF coordinates for GPS photo markers', async () => {
    await mountMiniMap(
      [],
      [],
      [
        {
          id: 42,
          fileName: 'gps.jpg',
          mediaKind: 'IMAGE',
          positionOrigin: 'EXIF_EMBEDDED',
          estimatedPosition: false,
          originalLat: 47.41,
          originalLng: 8.51,
          routeLat: 47.4,
          routeLng: 8.5,
          resolvedLat: 47.41,
          resolvedLng: 8.51,
        },
      ]
    );

    const marker = maplibreMock.MockMarker.instances[0];
    expect(marker.setLngLat).toHaveBeenCalledWith([8.51, 47.41]);
    expect(marker.element?.classList.contains('mini-map-photo-marker--gps')).toBe(true);
    expect(marker.element?.getAttribute('title')).toContain('Photo GPS');
  });

  it('uses the resolved user position while retaining a distinct manual marker', async () => {
    await mountMiniMap(
      [],
      [],
      [
        {
          id: 43,
          fileName: 'manual.jpg',
          mediaKind: 'IMAGE',
          positionOrigin: 'USER_ASSIGNED',
          estimatedPosition: false,
          originalLat: 47.41,
          originalLng: 8.51,
          routeLat: 47.4,
          routeLng: 8.5,
          resolvedLat: 47.5,
          resolvedLng: 8.6,
        },
      ]
    );

    const marker = maplibreMock.MockMarker.instances[0];
    expect(marker.setLngLat).toHaveBeenCalledWith([8.6, 47.5]);
    expect(marker.element?.classList.contains('mini-map-photo-marker--manual')).toBe(true);
    expect(marker.element?.getAttribute('title')).toContain('Location set by you');
  });

  it('does not claim GPS provenance when the origin is missing', async () => {
    await mountMiniMap(
      [],
      [],
      [
        {
          id: 44,
          fileName: 'unknown.jpg',
          mediaKind: 'IMAGE',
          positionOrigin: undefined,
          estimatedPosition: false,
          routeLat: 47.4,
          routeLng: 8.5,
        },
      ]
    );

    const marker = maplibreMock.MockMarker.instances[0];
    expect(marker.setLngLat).toHaveBeenCalledWith([8.5, 47.4]);
    expect(marker.element?.classList.contains('mini-map-photo-marker--unknown')).toBe(true);
    expect(marker.element?.getAttribute('title')).toContain('Position source unknown');
  });

  it('uses one circular photo symbol and changes only provenance colors', async () => {
    await mountMiniMap(
      [],
      [],
      [
        {
          id: 51,
          mediaKind: 'IMAGE',
          positionOrigin: 'EXIF_EMBEDDED',
          originalLat: 47.41,
          originalLng: 8.51,
        },
        {
          id: 52,
          mediaKind: 'IMAGE',
          positionOrigin: 'TRACK_INTERPOLATED',
          estimatedPosition: true,
          routeLat: 47.42,
          routeLng: 8.52,
        },
        {
          id: 53,
          mediaKind: 'IMAGE',
          positionOrigin: 'USER_ASSIGNED',
          resolvedLat: 47.43,
          resolvedLng: 8.53,
        },
        {
          id: 54,
          mediaKind: 'IMAGE',
          routeLat: 47.44,
          routeLng: 8.54,
        },
      ]
    );

    const markerElements = maplibreMock.MockMarker.instances.map((marker) => marker.element!);
    expect(markerElements).toHaveLength(4);
    expect(new Set(markerElements.map((element) => element.querySelector('i')?.className))).toEqual(
      new Set(['bi bi-camera-fill'])
    );
    expect(
      new Set(markerElements.map((element) => element.style.getPropertyValue('--media-marker-border-radius')))
    ).toEqual(new Set(['999px']));
    expect(new Set(markerElements.map((element) => element.style.getPropertyValue('--media-marker-fill'))).size).toBe(
      4
    );
  });

  it('opens a point popup from a projected line segment when simplified points are sparse', async () => {
    useTrackMapSync().setTrackPoints([
      {
        lat: 0,
        lng: 0,
        altitude: 126,
        timestamp: 1_700_000_000_000,
        distanceKm: 0,
        pointIndex: 0,
        canonicalPointIndex: 0,
      },
      {
        lat: 0,
        lng: 0,
        altitude: 110,
        timestamp: 1_700_001_800_000,
        distanceKm: 1.8,
        pointIndex: 50,
        canonicalPointIndex: 50,
      },
      {
        lat: 0,
        lng: 0.03234,
        altitude: 97,
        timestamp: 1_700_003_600_000,
        distanceKm: 3.6,
        pointIndex: 100,
        canonicalPointIndex: 100,
      },
    ]);
    const wrapper = await mountMiniMap(
      [],
      [
        [0, 0],
        [0.03234, 0],
      ]
    );

    const map = maplibreMock.MockMap.instances[0];
    const clickHandler = map.listeners.get('click')?.[0];

    clickHandler?.({
      lngLat: { lng: 0.01617, lat: 0 },
      point: { x: 161.7, y: 0 },
    });

    const popup = maplibreMock.MockPopup.instances.at(-1);
    const content = popup?.setDOMContent.mock.calls[0]?.[0] as HTMLDivElement;
    expect(content.querySelector('.mtl-point-popup-header')?.textContent).toBe('Track point');
    expect(content.textContent).toContain('51');
    expect(popup?.options).toMatchObject({
      closeButton: true,
      closeOnClick: true,
      className: 'mtl-point-popup-container',
    });
    expect(popup?.setLngLat).toHaveBeenCalledWith([expect.closeTo(0.01617, 10), 0]);
    await wrapper.setProps({ pointDetailsEnabled: false });
    expect(popup?.remove).toHaveBeenCalledTimes(1);
    const popupCount = maplibreMock.MockPopup.instances.length;
    clickHandler?.({ lngLat: { lng: 0.01617, lat: 0 }, point: { x: 161.7, y: 0 } });
    expect(maplibreMock.MockPopup.instances).toHaveLength(popupCount);
    expect(useTrackMapSync().pinnedPoint.value?.canonicalPointIndex).toBe(50);
  });

  it('draws a selected break highlight when the selected event key changes', async () => {
    const event = trackEvent(8.5, 47.4);
    const wrapper = await mountMiniMap([event]);
    const detailSource = detailEventSource();

    expect(selectedEventSource()?.data.features).toHaveLength(0);
    detailSource?.setData.mockClear();

    await wrapper.setProps({ selectedEventKey: event.id });

    const selectedSource = selectedEventSource();
    const map = maplibreMock.MockMap.instances[0];
    expect(detailSource?.setData).not.toHaveBeenCalled();
    expect(selectedSource?.data.features).toHaveLength(1);
    expect(selectedSource?.data.features[0].geometry.coordinates).toEqual([8.5, 47.4]);
    expect(map.getLayer('detail-selected-event-halo-layer')).toBeTruthy();
    expect(map.getLayer('detail-selected-event-core-layer')).toBeTruthy();
    expect(map.easeTo).toHaveBeenLastCalledWith({ center: [8.5, 47.4], zoom: map.getZoom(), duration: 300 });

    const movedLayerIds = map.moveLayer.mock.calls.map(([layerId]) => layerId);
    expect(movedLayerIds.slice(-3)).toEqual([
      'detail-selected-event-halo-layer',
      'detail-selected-event-core-layer',
      'detail-events-layer',
    ]);
  });

  it('moves the highlight between recording gaps and removes it when cleared', async () => {
    const first = { ...trackEvent(7.453, 46.95), eventType: 'GPS_GAP', durationInSec: 0 };
    const second = { ...trackEvent(7.453, 46.952), eventType: 'GPS_GAP', durationInSec: 0 };
    const wrapper = await mountMiniMap([first, second]);
    const map = maplibreMock.MockMap.instances[0];

    for (const event of [first, second]) {
      await wrapper.setProps({ selectedEventKey: event.id });
      expect(selectedEventSource()?.data.features).toHaveLength(1);
      expect(selectedEventSource()?.data.features[0].geometry.coordinates).toEqual(event.startPointLongLat.coordinates);
      expect(map.easeTo).toHaveBeenLastCalledWith({
        center: event.startPointLongLat.coordinates,
        zoom: map.getZoom(),
        duration: 300,
      });
    }

    map.easeTo.mockClear();
    await wrapper.setProps({ selectedEventKey: null });
    expect(selectedEventSource()?.data.features).toHaveLength(0);
    expect(detailEventSource()?.data.features).toHaveLength(2);
    expect(map.easeTo).not.toHaveBeenCalled();
  });

  it.each([8, 12, 18])('keeps zoom %s when selecting breaks and leaves the view when deselected', async (zoom) => {
    const first = trackEvent(8.5, 47.4);
    const second = trackEvent(8.6, 47.5);
    const wrapper = await mountMiniMap([first, second]);
    const map = maplibreMock.MockMap.instances[0];
    map.getZoom.mockReturnValue(zoom);
    await wrapper.setProps({ selectedEventKey: first.id });
    expect(map.easeTo).toHaveBeenLastCalledWith(expect.objectContaining({ center: [8.5, 47.4], zoom }));
    await wrapper.setProps({ selectedEventKey: second.id });
    expect(map.easeTo).toHaveBeenLastCalledWith(expect.objectContaining({ center: [8.6, 47.5], zoom }));

    map.easeTo.mockClear();
    await wrapper.setProps({ selectedEventKey: null });
    expect(map.easeTo).not.toHaveBeenCalled();
    expect(selectedEventSource()?.data.features).toHaveLength(0);
    expect(detailEventSource()?.data.features).toHaveLength(2);
  });

  it('expands a collapsed map when selecting a break and retains focus after track geometry arrives', async () => {
    const event = trackEvent(8.5, 47.4);
    const wrapper = await mountMiniMap([event]);
    const map = maplibreMock.MockMap.instances[0];
    await wrapper.get('.map-overlay-collapse-btn').trigger('click');
    expect(wrapper.get('.mini-map-wrapper').classes()).toContain('collapsed');

    await wrapper.setProps({ selectedEventKey: event.id });
    await nextTick();
    expect(wrapper.get('.mini-map-wrapper').classes()).not.toContain('collapsed');
    expect(map.easeTo).toHaveBeenLastCalledWith(expect.objectContaining({ center: [8.5, 47.4] }));

    await wrapper.setProps({
      trackCoordinates: [
        [8.4, 47.3],
        [8.6, 47.5],
      ],
    });
    expect(map.fitBounds).not.toHaveBeenCalled();
    expect(map.easeTo).toHaveBeenLastCalledWith(expect.objectContaining({ center: [8.5, 47.4] }));
  });

  it('resolves canonical break indexes when fallback points arrive after selection', async () => {
    const event = trackEventWithoutGeometry(12, 180, 220);
    const wrapper = await mountMiniMap([event]);
    const map = maplibreMock.MockMap.instances[0];
    await wrapper.setProps({ selectedEventKey: event.id });
    expect(map.easeTo).not.toHaveBeenCalled();

    useTrackMapSync().setTrackPoints([
      { lat: 47.3, lng: 8.4, altitude: null, timestamp: 0, distanceKm: 0, pointIndex: 10, canonicalPointIndex: 100 },
      { lat: 47.41, lng: 8.51, altitude: null, timestamp: 0, distanceKm: 1, pointIndex: 20, canonicalPointIndex: 200 },
      { lat: 47.5, lng: 8.6, altitude: null, timestamp: 0, distanceKm: 2, pointIndex: 30, canonicalPointIndex: 300 },
    ]);
    await nextTick();
    expect(detailEventSource()?.data.features[0].geometry.coordinates).toEqual([8.51, 47.41]);
    expect(map.easeTo).toHaveBeenLastCalledWith(expect.objectContaining({ center: [8.51, 47.41] }));
  });

  it('falls back to loaded track points when selected event geometry is missing', async () => {
    useTrackMapSync().setTrackPoints([
      { lat: 47.3, lng: 8.4, altitude: null, timestamp: 0, distanceKm: 0, pointIndex: 10 },
      { lat: 47.41, lng: 8.51, altitude: null, timestamp: 0, distanceKm: 1, pointIndex: 20 },
      { lat: 47.5, lng: 8.6, altitude: null, timestamp: 0, distanceKm: 2, pointIndex: 30 },
    ]);
    const event = trackEventWithoutGeometry(12, 18, 22);
    const wrapper = await mountMiniMap([event]);

    await wrapper.setProps({ selectedEventKey: event.id });

    expect(detailEventSource()?.data.features[0].geometry.coordinates).toEqual([8.51, 47.41]);
    expect(selectedEventSource()?.data.features[0].geometry.coordinates).toEqual([8.51, 47.41]);
  });

  it('opens a clicked break and closes its popup when graph scrubbing begins', async () => {
    const event = trackEvent(8.5, 47.4);
    const wrapper = await mountMiniMap([event]);
    const map = maplibreMock.MockMap.instances[0];
    const clickHandler = map.listeners.get('click:detail-events-layer')?.[0];

    clickHandler?.({
      features: [
        {
          geometry: { type: 'Point', coordinates: [8.5, 47.4] },
          properties: {
            eventKey: event.id,
            label: 'Break',
            time: '',
            duration: '2m 00s',
          },
        },
      ],
    });

    expect(wrapper.emitted('select-event')).toEqual([[event.id]]);
    const popup = maplibreMock.MockPopup.instances[0];
    await wrapper.setProps({ markerInteractionEnabled: false });
    expect(popup.remove).toHaveBeenCalledTimes(1);
  });

  it('emits null when clicking the already selected break highlight', async () => {
    const event = trackEvent(8.5, 47.4);
    const wrapper = await mountMiniMap([event]);
    await wrapper.setProps({ selectedEventKey: event.id });

    const map = maplibreMock.MockMap.instances[0];
    const clickHandler = map.listeners.get('click:detail-selected-event-core-layer')?.[0];

    clickHandler?.({
      features: [
        {
          geometry: { type: 'Point', coordinates: [8.5, 47.4] },
          properties: {
            eventKey: event.id,
            label: 'Break',
            time: '',
            duration: '2m 00s',
          },
        },
      ],
    });

    expect(wrapper.emitted('select-event')).toEqual([[null]]);
  });

  it('passes clicks on break locations to the track while graph scrubbing is active', async () => {
    const event = trackEvent(8.5, 47.4);
    const wrapper = await mountMiniMap([event]);
    const map = maplibreMock.MockMap.instances[0];
    const point = { lat: 47.4, lng: 8.5, altitude: null, timestamp: 0, distanceKm: 0, pointIndex: 0 };
    useTrackMapSync().setTrackPoints([point]);
    await wrapper.setProps({ markerInteractionEnabled: false, pointDetailsEnabled: false });
    const click = {
      point: { x: 85_000, y: 474_000 },
      lngLat: { lng: 8.5, lat: 47.4 },
      features: [{ geometry: { type: 'Point', coordinates: [8.5, 47.4] }, properties: { eventKey: event.id } }],
    };
    map.queryRenderedFeatures.mockReturnValue(click.features);
    for (const layer of [
      'detail-events-layer',
      'detail-selected-event-halo-layer',
      'detail-selected-event-core-layer',
    ]) {
      map.listeners.get(`click:${layer}`)?.[0]?.(click);
    }
    map.listeners.get('mouseenter:detail-events-layer')?.[0]?.();
    expect(map.getCanvas().style.cursor).not.toBe('pointer');
    expect(wrapper.emitted('select-event')).toBeUndefined();
    expect(detailEventSource()?.data.features).toHaveLength(1);

    map.listeners.get('click')?.[0]?.(click);
    expect(useTrackMapSync().pinnedPoint.value?.pointIndex).toBe(0);
    expect(chartSyncMocks.showChartsAtPoint).toHaveBeenCalledWith(point);
    expect(map.easeTo).not.toHaveBeenCalled();
    expect(maplibreMock.MockPopup.instances).toHaveLength(0);

    await wrapper.setProps({ markerInteractionEnabled: true });
    map.listeners.get('click:detail-events-layer')?.[0]?.(click);
    expect(wrapper.emitted('select-event')).toEqual([[event.id]]);
  });

  it('clears chart hover artifacts when the pointer leaves the mini-map wrapper', async () => {
    const wrapper = await mountMiniMap();

    await wrapper.find('.mini-map-wrapper').trigger('pointerleave');
    await wrapper.find('.mini-map-wrapper').trigger('mouseleave');
    document.dispatchEvent(new MouseEvent('mousemove', { bubbles: true }));

    expect(chartSyncMocks.clearChartCrosshairs).toHaveBeenCalledTimes(3);
  });

  it('exposes an accessible resize separator with tap and keyboard alternatives', async () => {
    const wrapper = await mountMiniMap();
    const handle = wrapper.get('.resize-handle');
    const mapBody = wrapper.get('.mini-map-body');

    expect(handle.attributes('role')).toBe('separator');
    expect(handle.attributes('tabindex')).toBe('0');
    expect(handle.attributes('aria-orientation')).toBe('horizontal');
    expect(handle.attributes('aria-controls')).toBe(mapBody.attributes('id'));
    expect(handle.attributes('aria-valuenow')).toBe('220');

    await handle.trigger('keydown', { key: 'ArrowDown' });
    expect(handle.attributes('aria-valuenow')).toBe('240');

    await handle.trigger('keydown', { key: 'Home' });
    expect(handle.attributes('aria-valuenow')).toBe('80');

    await handle.trigger('click');
    expect(handle.attributes('aria-valuenow')).toBe('220');
  });

  it('keeps the MapLibre canvas synchronized throughout a touch resize gesture', async () => {
    const wrapper = await mountMiniMap();
    const handle = wrapper.get('.resize-handle');
    const map = maplibreMock.MockMap.instances[0];
    map.resize.mockClear();

    dispatchPointer(handle.element, 'pointerdown', { clientX: 100, clientY: 100 });
    dispatchPointer(window, 'pointermove', { clientX: 101, clientY: 130 });
    dispatchPointer(window, 'pointermove', { clientX: 102, clientY: 160 });
    await nextTick();

    expect(handle.attributes('aria-valuenow')).toBe('280');
    expect(map.resize).toHaveBeenCalledTimes(2);

    dispatchPointer(window, 'pointerup', { clientX: 102, clientY: 160 });
    await nextTick();

    expect(map.resize).toHaveBeenCalledTimes(3);
  });
});
