import { mount, flushPromises } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import MiniMap from '@/components/map/MiniMap.vue';
import SegmentReplay from '@/components/measure/SegmentReplay.vue';
import { attempt, core } from './fixtures/segmentAnalysis';
import type { MiniMapGeoJson } from '@/components/map/useMiniMap';
import { probeVectorMapArchive } from '@/utils/mapArchiveHealth';
vi.mock('@/utils/mapArchiveHealth', () => ({ probeVectorMapArchive: vi.fn() }));
import { resolveConfiguredMapStyle } from '@/components/map/mapStyleResolver';

const mock = vi.hoisted(() => ({
  events: new Map<string, (event?: unknown) => void>(),
  onceEvents: new Map<string, () => void>(),
  setStyle: vi.fn(),
  addSource: vi.fn(),
  getSource: vi.fn(),
  setData: vi.fn(),
  addLayer: vi.fn(),
  fitBounds: vi.fn(),
  easeTo: vi.fn(),
  canvas: { clientWidth: 900, clientHeight: 300, style: {} },
  resize: vi.fn(),
  popupHtml: vi.fn(),
  popupCoordinates: vi.fn(),
  popupAddTo: vi.fn(),
  popupRemove: vi.fn(),
}));
vi.mock('maplibre-gl', async (original) => ({
  ...(await original()),
  Map: class {
    on(event: string, ...args: unknown[]) {
      mock.events.set(event, args.at(-1) as (event?: unknown) => void);
    }
    once(event: string, listener: () => void) {
      mock.onceEvents.set(event, listener);
    }
    setStyle = mock.setStyle;
    getGlyphs() {
      return undefined;
    }
    isStyleLoaded() {
      return false;
    }
    addControl() {}
    getSource = mock.getSource;
    getLayer() {
      return undefined;
    }
    addSource = mock.addSource;
    addLayer = mock.addLayer;
    fitBounds = mock.fitBounds;
    easeTo = mock.easeTo;
    getMinZoom() {
      return 0;
    }
    getMaxZoom() {
      return 22;
    }
    getCanvas() {
      return mock.canvas;
    }
    resize = mock.resize;
    remove() {}
  },
  ScaleControl: class {
    setUnit() {}
  },
  NavigationControl: class {},
  Popup: class {
    setLngLat(value: unknown) {
      mock.popupCoordinates(value);
      return this;
    }
    setHTML(value: string) {
      mock.popupHtml(value);
      return this;
    }
    addTo() {
      mock.popupAddTo();
      return this;
    }
    remove = mock.popupRemove;
  },
}));
vi.mock('@/utils/mapConfigService', () => ({
  fetchMapConfig: vi.fn(async () => ({})),
  mainTileArchiveUrl: vi.fn(() => '/synthetic.pmtiles'),
}));
vi.mock('@/components/map/mapStyleResolver', () => ({
  resolveConfiguredMapStyle: vi.fn(() => ({
    style: { version: 8, sources: {}, layers: [] },
    styleMode: 'test-style',
    attributions: [],
  })),
}));
vi.mock('@/utils/maplibreStyleImages', () => ({ installMissingStyleImageResolver() {} }));
vi.mock('@/stores/mapSettingsStore', () => ({ useMapSettingsStore: () => ({ hydrate() {}, mapSourceMode: 'auto' }) }));
afterEach(() => {
  vi.clearAllTimers();
  vi.useRealTimers();
  vi.mocked(probeVectorMapArchive).mockReset();
  vi.unstubAllGlobals();
  vi.clearAllMocks();
  mock.getSource.mockReset();
  mock.events.clear();
  mock.onceEvents.clear();
});

describe('incremental comparison map loading', () => {
  it('centers at a close ground scale and keeps it independent of track bounds and manual zoom', async () => {
    const wrapper = mount(MiniMap, {
      props: {
        tracksGeoJson: { type: 'FeatureCollection', features: [] },
        mapBounds: [
          [8, 47],
          [12, 47],
        ],
        mapFocus: { center: [8, 47], spanMetres: 50 },
        showZoomControls: true,
      },
    });
    await flushPromises();
    mock.events.get('style.load')!();
    await flushPromises();
    expect(mock.fitBounds).not.toHaveBeenCalled();
    expect(mock.easeTo).toHaveBeenLastCalledWith({
      center: [8, 47],
      zoom: expect.any(Number),
      bearing: 0,
      pitch: 0,
      duration: 0,
    });
    const desktopZoom = mock.easeTo.mock.lastCall![0].zoom;
    expect(desktopZoom).toBeGreaterThan(19);
    expect(desktopZoom).toBeLessThan(20);
    mock.easeTo.mockClear();
    await wrapper.setProps({
      mapBounds: [
        [8, 47],
        [50, 47],
      ],
      tracksGeoJson: { type: 'FeatureCollection', features: [] },
    });
    expect(mock.easeTo).not.toHaveBeenCalled();
    expect(mock.fitBounds).not.toHaveBeenCalled();
    mock.canvas.clientWidth = 450;
    (wrapper.vm as unknown as { refitView: () => void }).refitView();
    expect(mock.easeTo.mock.lastCall![0].zoom).toBeCloseTo(desktopZoom - 1);
    mock.canvas.clientWidth = 900;
    await wrapper.setProps({ mapFocus: null });
    expect(mock.fitBounds).toHaveBeenLastCalledWith(
      [
        [8, 47],
        [50, 47],
      ],
      { padding: 20 }
    );
    wrapper.unmount();
  });
  it('reports active map errors and graphics loss for non-vector styles and ignores disposed maps', async () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const wrapper = mount(MiniMap, { props: { tracksGeoJson: { type: 'FeatureCollection', features: [] } } });
    await flushPromises();
    const error = new Error('Synthetic renderer failure');
    mock.events.get('error')!({ sourceId: 'terrain', error });
    mock.events.get('webglcontextlost')!();
    expect(warn).toHaveBeenCalledTimes(2);
    expect(warn).toHaveBeenCalledWith(
      'Map rendering failed',
      expect.objectContaining({ stackTrace: error.stack, topic2: 'useMiniMap' })
    );
    wrapper.unmount();
    mock.events.get('error')!({ error });
    mock.events.get('webglcontextlost')!();
    expect(warn).toHaveBeenCalledTimes(2);
    warn.mockRestore();
  });
  it('restores the latest review geometry after local basemap failure without refitting a selected area', async () => {
    vi.mocked(resolveConfiguredMapStyle).mockReturnValueOnce({
      style: { version: 8, sources: {}, layers: [] },
      styleMode: 'local-vector',
      attributions: [],
    });
    const wrapper = mount(MiniMap, { props: { tracksGeoJson: { type: 'FeatureCollection', features: [] } } });
    await flushPromises();
    mock.events.get('style.load')!();
    await flushPromises();
    mock.addSource.mockClear();
    mock.fitBounds.mockClear();
    mock.events.get('error')!({ sourceId: 'minimap-tracks' });
    expect(mock.setStyle).not.toHaveBeenCalled();
    vi.useFakeTimers();
    vi.mocked(probeVectorMapArchive).mockRejectedValue(new Error('Bad response code: 503'));
    mock.events.get('error')!({ sourceId: 'protomaps', error: new Error('Bad response code: 503') });
    expect(mock.setStyle).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(4000);
    expect(mock.setStyle).toHaveBeenCalledOnce();
    expect(resolveConfiguredMapStyle).toHaveBeenLastCalledWith(expect.objectContaining({ localTilesReady: false }));
    const latest: MiniMapGeoJson = {
      type: 'FeatureCollection',
      features: [
        {
          type: 'Feature',
          geometry: { type: 'Point', coordinates: [8, 47] },
          properties: { label: 'Selected interval' },
        },
      ],
    };
    await wrapper.setProps({ tracksGeoJson: latest });
    expect(mock.addSource).not.toHaveBeenCalled();
    mock.onceEvents.get('style.load')!();
    expect(mock.addSource).toHaveBeenCalledWith('minimap-tracks', { type: 'geojson', data: latest });
    expect(mock.fitBounds).not.toHaveBeenCalled();
    mock.events.get('error')!({ sourceId: 'protomaps' });
    expect(mock.setStyle).toHaveBeenCalledOnce();
    wrapper.unmount();
  });

  it('shows escaped replay details, refreshes the hovered attempt, and closes during recording gaps', async () => {
    const point: MiniMapGeoJson['features'][number] = {
      type: 'Feature',
      id: 'attempt-one',
      geometry: { type: 'Point', coordinates: [8, 47] },
      properties: {
        type: 'racer',
        color: 'red',
        popupTitle: '#1 · Synthetic <img src=x>',
        popupDetails: 'A1 → B1 · 01/01/26\nElapsed time: 00:00:24 / 00:01:00\nDistance traveled: 400 m / 1 km',
      },
    };
    const wrapper = mount(MiniMap, {
      props: { tracksGeoJson: { type: 'FeatureCollection', features: [point] } },
    });
    await flushPromises();
    mock.events.get('style.load')!();
    await flushPromises();
    mock.events.get('mouseenter')!({ features: [point] });
    const popup = document.createElement('div');
    popup.innerHTML = mock.popupHtml.mock.lastCall![0];
    expect(popup.textContent).toContain('#1 · Synthetic <img src=x>');
    expect(popup.querySelector('img')).toBeNull();
    expect(Array.from(popup.querySelectorAll('div'), (row) => row.textContent)).toEqual([
      'A1 → B1 · 01/01/26',
      'Elapsed time: 00:00:24 / 00:01:00',
      'Distance traveled: 400 m / 1 km',
    ]);
    mock.getSource.mockReturnValue({ setData: mock.setData });
    const updated = {
      ...point,
      geometry: { type: 'Point' as const, coordinates: [8.01, 47] },
      properties: {
        ...point.properties,
        popupTitle: '#2 · Synthetic attempt',
        popupDetails: 'Finished\nElapsed time: 00:01:00 / 00:01:00',
      },
    };
    const other = { ...point, id: 'attempt-two', properties: { ...point.properties, popupTitle: 'Other attempt' } };
    await wrapper.setProps({ tracksGeoJson: { type: 'FeatureCollection', features: [other, updated] } });
    expect(mock.popupCoordinates).toHaveBeenLastCalledWith([8.01, 47]);
    expect(mock.popupHtml.mock.lastCall![0]).toContain('#2 · Synthetic attempt');
    expect(mock.popupHtml.mock.lastCall![0]).toContain('Finished');
    expect(mock.popupAddTo).toHaveBeenCalledOnce();
    await wrapper.setProps({ tracksGeoJson: { type: 'FeatureCollection', features: [other] } });
    expect(mock.popupRemove).toHaveBeenCalledOnce();
    wrapper.unmount();
  });
  it('keeps track, locator and trigger hover labels without replay details', async () => {
    const wrapper = mount(MiniMap, { props: { tracksGeoJson: { type: 'FeatureCollection', features: [] } } });
    await flushPromises();
    mock.events.get('style.load')!();
    await flushPromises();
    for (const properties of [
      { type: 'racer', trackName: 'Synthetic legacy race' },
      { type: 'locator', trackName: 'Reference attempt' },
      { type: 'trigger', name: 'A1' },
    ]) {
      mock.events.get('mouseenter')!({
        features: [{ type: 'Feature', geometry: { type: 'Point', coordinates: [8, 47] }, properties }],
      });
      const expected = properties.trackName ?? properties.name;
      expect(mock.popupHtml.mock.lastCall![0]).toContain(expected);
    }
    wrapper.unmount();
  });
  it('refits the comparison map after resizing, while cursor updates keep the viewport stable', async () => {
    vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => callback(0));
    const bounds = [
      [8, 47],
      [8.01, 47.005],
    ] as [[number, number], [number, number]];
    const wrapper = mount(MiniMap, {
      props: {
        tracksGeoJson: { type: 'FeatureCollection', features: [] },
        mapBounds: bounds,
        fitBoundsOnResize: true,
      },
    });
    await flushPromises();
    mock.events.get('style.load')!();
    await flushPromises();
    mock.fitBounds.mockClear();
    await wrapper.setProps({
      tracksGeoJson: {
        type: 'FeatureCollection',
        features: [
          { type: 'Feature', geometry: { type: 'Point', coordinates: [8.005, 47] }, properties: { type: 'racer' } },
        ],
      },
    });
    expect(mock.fitBounds).not.toHaveBeenCalled();
    (wrapper.vm as unknown as { invalidateMapSize: () => void }).invalidateMapSize();
    expect(mock.resize).toHaveBeenCalledOnce();
    expect(mock.fitBounds).toHaveBeenCalledWith(bounds, { padding: 20, animate: false });
    wrapper.unmount();
  });
  it('keeps the full replay route visible after container resizing without refitting each playback frame', async () => {
    let notifyResize: ResizeObserverCallback | undefined;
    vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => callback(0));
    vi.stubGlobal(
      'ResizeObserver',
      class {
        constructor(callback: ResizeObserverCallback) {
          notifyResize = callback;
        }
        observe() {}
        disconnect() {}
      }
    );
    const selected = attempt(1, 1, 60);
    const wrapper = mount(SegmentReplay, {
      props: {
        entries: [
          {
            attempt: selected,
            core: core(selected),
            loading: false,
            healthLoading: false,
            comparisonLoading: false,
            healthRequest: 0,
          },
        ],
        colors: ['red'],
      },
    });
    await flushPromises();
    mock.events.get('style.load')!();
    await flushPromises();
    const bounds = wrapper.findComponent(MiniMap).props('mapBounds');
    expect(bounds).toEqual([
      [8, 47],
      [8.01, 47],
    ]);
    mock.fitBounds.mockClear();
    mock.getSource.mockReturnValue({ setData: mock.setData });
    await wrapper.get('input[type="range"]').setValue(30);
    expect(mock.fitBounds).not.toHaveBeenCalled();
    notifyResize!([], {} as ResizeObserver);
    expect(mock.resize).toHaveBeenCalledOnce();
    expect(mock.fitBounds).toHaveBeenCalledWith(bounds, { padding: 20, animate: false });
    wrapper.unmount();
  });
  it('keeps the latest geometry while style is pending and draws without waiting for basemap tiles', async () => {
    const original = { type: 'FeatureCollection' as const, features: [] };
    const latest = {
      type: 'FeatureCollection' as const,
      features: [
        {
          type: 'Feature' as const,
          geometry: {
            type: 'LineString' as const,
            coordinates: [
              [8, 47],
              [8.01, 47],
            ],
          },
          properties: { type: 'track' },
        },
      ],
    };
    const wrapper = mount(MiniMap, {
      props: {
        tracksGeoJson: original,
        mapBounds: [
          [8, 47],
          [8.01, 47],
        ],
      },
    });
    await flushPromises();
    await wrapper.setProps({ tracksGeoJson: latest });
    expect(mock.addSource).not.toHaveBeenCalled();
    mock.events.get('style.load')!();
    await flushPromises();
    expect(mock.addSource).toHaveBeenCalledWith('minimap-tracks', { type: 'geojson', data: latest });
    expect(mock.fitBounds).toHaveBeenCalled();
    expect(mock.events.has('load')).toBe(false);
    wrapper.unmount();
  });
});
