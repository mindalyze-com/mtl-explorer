import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import MediaLocationMiniMap from '@/components/map/MediaLocationMiniMap.vue';

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

    options: Record<string, unknown>;
    listeners = new Map<string, Listener[]>();
    sources = new Map<string, MockGeoJsonSource>();
    layers = new Map<string, unknown>();
    fitBounds = vi.fn();
    jumpTo = vi.fn();
    easeTo = vi.fn();
    zoomOut = vi.fn();
    getZoom = vi.fn(() => 12);
    resize = vi.fn();
    remove = vi.fn();
    hasImage = vi.fn(() => false);
    addImage = vi.fn();
    setMissingStyleImageResolver = vi.fn();

    constructor(options: Record<string, unknown>) {
      this.options = options;
      MockMap.instances.push(this);
    }

    on(event: string, handler: Listener) {
      this.listeners.set(event, [...(this.listeners.get(event) ?? []), handler]);
      return this;
    }

    emit(event: string, payload?: unknown) {
      for (const handler of this.listeners.get(event) ?? []) handler(payload);
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
  }

  return { MockLngLatBounds, MockMap, MockMarker };
});

vi.mock('maplibre-gl', () => ({
  Map: maplibreMock.MockMap,
  Marker: maplibreMock.MockMarker,
  LngLatBounds: maplibreMock.MockLngLatBounds,
}));

vi.mock('@/utils/mapConfigService', () => ({
  fetchMapConfig: vi.fn(async () => ({ tileMode: 'remote', remoteRasterStyles: {} })),
}));

vi.mock('@/components/map/mapStyleResolver', () => ({
  resolveConfiguredMapStyle: vi.fn(() => ({
    style: { version: 8, sources: {}, layers: [] },
    styleMode: 'test-style',
  })),
}));

class MockResizeObserver {
  observe = vi.fn();
  disconnect = vi.fn();
}

describe('MediaLocationMiniMap', () => {
  beforeEach(() => {
    localStorage.clear();
    setActivePinia(createPinia());
    maplibreMock.MockMap.instances.length = 0;
    maplibreMock.MockMarker.instances.length = 0;
    vi.stubGlobal('ResizeObserver', MockResizeObserver);
  });

  afterEach(() => vi.unstubAllGlobals());

  it('fits the selected activity once and exposes only standard zoom controls', async () => {
    const trackCoordinates = [
      [8.5, 47.55],
      [8.51, 47.56],
      [8.52, 47.57],
    ];
    const wrapper = mount(MediaLocationMiniMap, {
      props: {
        latitude: 47.561,
        longitude: 8.512,
        positionSource: 'TRACK_INTERPOLATED',
        positionEstimated: true,
        trackCoordinates,
      },
      attachTo: document.body,
    });
    await flushPromises();

    const map = maplibreMock.MockMap.instances[0];
    expect(map.options).toMatchObject({
      center: [8.512, 47.561],
      zoom: 15,
      interactive: false,
    });

    map.emit('load');
    const source = map.getSource('media-location-track');
    expect(source?.data.features[0]?.geometry.coordinates).toEqual(trackCoordinates);
    expect(map.fitBounds).toHaveBeenCalledTimes(1);
    expect(map.fitBounds.mock.calls[0]?.[1]).toMatchObject({ padding: 20, maxZoom: 16, duration: 0 });

    const marker = maplibreMock.MockMarker.instances[0];
    expect(marker.setLngLat).toHaveBeenCalledWith([8.512, 47.561]);
    expect(marker.element?.classList.contains('media-location-map-marker')).toBe(true);
    expect(marker.element?.querySelector('i')?.className).toBe('bi bi-camera-fill');

    await wrapper.get('[aria-label="Zoom in location map"]').trigger('click');
    await wrapper.get('[aria-label="Zoom out location map"]').trigger('click');
    expect(map.easeTo).toHaveBeenCalledWith({
      center: [8.512, 47.561],
      zoom: 13,
      duration: 160,
    });
    expect(map.zoomOut).toHaveBeenCalledWith({ duration: 160 });
    expect(map.fitBounds).toHaveBeenCalledTimes(1);
    expect(wrapper.find('[aria-label="Fit selected activity"]').exists()).toBe(false);

    wrapper.unmount();
    expect(marker.remove).toHaveBeenCalledTimes(1);
    expect(map.remove).toHaveBeenCalledTimes(1);
  });

  it('fits the previous main-map area when no activity track is available', async () => {
    const overviewBounds: [[number, number], [number, number]] = [
      [8.4, 47.5],
      [8.6, 47.7],
    ];
    const wrapper = mount(MediaLocationMiniMap, {
      props: {
        latitude: 47.561,
        longitude: 8.512,
        overviewBounds,
      },
      attachTo: document.body,
    });
    await flushPromises();

    const map = maplibreMock.MockMap.instances[0];
    map.emit('load');

    expect(map.fitBounds).toHaveBeenCalledWith(overviewBounds, { padding: 0, duration: 0 });
    expect(map.jumpTo).not.toHaveBeenCalled();

    wrapper.unmount();
  });
});
