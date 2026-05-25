import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { nextTick } from 'vue';
import TrackDetailMiniMap from '@/components/trackdetails/TrackDetailMiniMap.vue';
import { useTrackMapSync } from '@/composables/useTrackMapSync';

const maplibreMock = vi.hoisted(() => {
  type Listener = (...args: unknown[]) => void;

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
    setLngLat = vi.fn(() => this);
    setHTML = vi.fn(() => this);
    addTo = vi.fn(() => this);
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
    canvas = { style: { cursor: '' } };
    resize = vi.fn();
    fitBounds = vi.fn();
    moveLayer = vi.fn();
    remove = vi.fn();

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
    MockPopup,
  };
});

vi.mock('maplibre-gl', () => ({
  default: {
    Map: maplibreMock.MockMap,
    LngLatBounds: maplibreMock.MockLngLatBounds,
    Popup: maplibreMock.MockPopup,
  },
}));

vi.mock('@/utils/mapConfigService', () => ({
  MapConfigDtoTileModeEnum: { Local: 'local', Remote: 'remote' },
  fetchMapConfig: vi.fn(async () => ({
    tileMode: 'remote',
    remoteTileUrl: 'https://example.test/{z}/{x}/{y}.png',
  })),
  mainTileArchiveUrl: vi.fn(() => 'mock.pmtiles'),
}));

vi.mock('@/utils/mapStyle', () => ({
  buildLocalVectorStyleFromArchiveUrl: vi.fn(() => ({ version: 8, sources: {}, layers: [] })),
  buildRemoteRasterStyle: vi.fn(() => ({ version: 8, sources: {}, layers: [] })),
}));

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

async function mountMiniMap(trackEvents: Record<string, unknown>[] = [], trackCoordinates: number[][] = []) {
  const wrapper = mount(TrackDetailMiniMap, {
    props: {
      gpsTrackId: 1,
      trackEvents,
      trackCoordinates,
    },
    attachTo: document.body,
  });
  await nextTick();
  await flushPromises();
  await nextTick();
  return wrapper;
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
    originalGetContext = HTMLCanvasElement.prototype.getContext;
    maplibreMock.MockMap.instances.length = 0;
    maplibreMock.MockMap.nextStyleLoaded = true;
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
    useTrackMapSync().clearAll();
    HTMLCanvasElement.prototype.getContext = originalGetContext;
    document.body.innerHTML = '';
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

    const movedLayerIds = map.moveLayer.mock.calls.map(([layerId]) => layerId);
    expect(movedLayerIds.slice(-3)).toEqual([
      'detail-events-layer',
      'detail-selected-event-halo-layer',
      'detail-selected-event-core-layer',
    ]);
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

  it('emits the clicked break key from the event marker layer', async () => {
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
});
