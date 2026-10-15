import { mount, flushPromises } from '@vue/test-utils';
import { defineComponent, effectScope, nextTick, shallowRef } from 'vue';
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { validateStyleMin, featureFilter } from '@maplibre/maplibre-gl-style-spec';
import type { Map as MapLibreMap, StyleSpecification } from 'maplibre-gl';
import { POI_CATEGORIES, defaultPoiCategories, poiCategoryForKind } from '@/components/map/poiCategories';
import { buildPoiLayers, POI_LAYER_PREFIX } from '@/components/map/poiLayers';
import MapPoiLayersSheet from '@/components/map/MapPoiLayersSheet.vue';
import { useMapPois } from '@/components/map/composables/useMapPois';
import { useMapSettingsStore } from '@/stores/mapSettingsStore';
import { STORAGE_KEYS } from '@/utils/appStorage';
import { installPoiImages } from '@/components/map/poiImages';
import { PROTOMAPS_GLYPHS_URL } from '@/utils/mapStyle';

vi.mock('@/components/map/poiImages', () => ({ installPoiImages: vi.fn(async (_map, current) => current()) }));

beforeEach(() => {
  localStorage.clear();
  setActivePinia(createPinia());
  vi.clearAllMocks();
});

describe('POI preferences and controls', () => {
  it('starts with every category enabled and persists selections independently of the master switch', () => {
    const store = useMapSettingsStore();
    expect(store.poiEnabled).toBe(true);
    expect(store.poiCategories).toEqual(defaultPoiCategories());
    store.setPoiCategory('water', false);
    store.setPoiEnabled(false);
    store.setPoiOpacity(42);
    setActivePinia(createPinia());
    const restored = useMapSettingsStore();
    expect(restored.poiEnabled).toBe(false);
    expect(restored.poiCategories.water).toBe(false);
    expect(restored.poiOpacity).toBe(42);
    restored.setPoiEnabled(true);
    expect(restored.poiCategories.water).toBe(false);
    restored.reset();
    expect(restored.poiEnabled).toBe(true);
    expect(restored.poiOpacity).toBe(100);
    expect(restored.poiCategories).toEqual(defaultPoiCategories());
  });

  it('sanitizes saved categories and enables newly introduced categories', () => {
    localStorage.setItem(
      STORAGE_KEYS.mapSettings,
      JSON.stringify({
        poiCategories: { water: false, views: 'false', unknown: true },
        poiOpacity: 250,
      })
    );
    const store = useMapSettingsStore();
    expect(store.poiCategories).toEqual({ ...defaultPoiCategories(), water: false });
    expect(store.poiOpacity).toBe(100);
    store.setPoiCategory('unknown', true);
    expect(store.poiCategories).not.toHaveProperty('unknown');
  });

  it('lets users change categories and the master switch from the sheet', async () => {
    const wrapper = mount(MapPoiLayersSheet, {
      props: { modelValue: true },
      global: {
        stubs: { BottomSheet: defineComponent({ template: '<section><slot /><slot name="footer" /></section>' }) },
      },
    });
    expect(wrapper.text()).toContain('21 of 21 categories enabled');
    await wrapper.get('[aria-label="Hide Drinking water"]').trigger('click');
    expect(wrapper.text()).toContain('20 of 21 categories enabled');
    await wrapper.get('[aria-label="Hide Points of interest"]').trigger('click');
    expect(useMapSettingsStore().poiEnabled).toBe(false);
    expect(wrapper.text()).toContain('Hidden; your category choices are saved');
    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Enable all')!
      .trigger('click');
    expect(useMapSettingsStore().poiCategories).toEqual(defaultPoiCategories());
    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Disable all')!
      .trigger('click');
    expect(wrapper.text()).toContain('0 of 21 categories enabled');
    wrapper.unmount();
  });
});

describe('POI tile styles', () => {
  it('produces valid MapLibre styles with collision handling', () => {
    const layers = buildPoiLayers('protomaps', true, defaultPoiCategories(), 100);
    const style: StyleSpecification = {
      version: 8,
      glyphs: 'https://example.com/{fontstack}/{range}.pbf',
      sources: {
        protomaps: { type: 'vector', tiles: ['https://example.com/{z}/{x}/{y}.pbf'] },
      },
      layers,
    };
    expect(validateStyleMin(style).map((error) => error.message)).toEqual([]);
    expect(layers.every((layer) => layer.layout?.['icon-allow-overlap'] === false)).toBe(true);
  });

  it('keeps fountains separate from drinking water and excludes incidental infrastructure', () => {
    expect(poiCategoryForKind('drinking_water')?.id).toBe('water');
    expect(poiCategoryForKind('fountain')?.id).toBe('fountains');
    expect(poiCategoryForKind('water_point')?.id).toBe('fountains');
    expect(poiCategoryForKind('street_lamp')).toBeUndefined();
    expect(poiCategoryForKind('switch')).toBeUndefined();
    const kinds = POI_CATEGORIES.flatMap((category) => [...category.kinds]);
    expect(new Set(kinds).size).toBe(kinds.length);
    const water = buildPoiLayers('protomaps', true, defaultPoiCategories(), 100).find(
      (layer) => layer.id === `${POI_LAYER_PREFIX}water`
    )!;
    const filter = featureFilter(water.filter, 'layers[0].filter').filter;
    expect(filter({ zoom: 15 }, { type: 1, properties: { kind: 'drinking_water' } } as never)).toBe(true);
    expect(filter({ zoom: 15 }, { type: 1, properties: { kind: 'fountain' } } as never)).toBe(false);
  });
});

function mapStub(glyphs = PROTOMAPS_GLYPHS_URL) {
  let layers: Array<{ id: string }> = [{ id: 'pois' }, { id: 'tracks-layer' }];
  const listeners = new Map<string, () => Promise<void>>();
  const map = {
    getStyle: () => ({ layers }),
    getSource: vi.fn(() => ({})),
    getLayer: (id: string) => layers.find((layer) => layer.id === id),
    getGlyphs: () => glyphs,
    getZoom: vi.fn(() => 17),
    project: vi.fn((coordinates: number[]) => ({ x: coordinates[0], y: coordinates[1] })),
    queryRenderedFeatures: vi.fn(() => [] as unknown[]),
    addSource: vi.fn(),
    addLayer: vi.fn((layer: { id: string }) => layers.push(layer)),
    setLayoutProperty: vi.fn(),
    setPaintProperty: vi.fn(),
    isStyleLoaded: () => true,
    on: vi.fn((event: string, listener: () => Promise<void>) => listeners.set(event, listener)),
    off: vi.fn(),
  };
  return {
    map,
    listeners,
    resetStyle: (nextGlyphs = glyphs) => {
      glyphs = nextGlyphs;
      layers = [{ id: 'pois' }, { id: 'tracks-layer' }];
    },
  };
}

describe('POI map lifecycle', () => {
  it('selects provider fonts again when switching between Swiss Topo and OSM', async () => {
    const { map, listeners, resetStyle } = mapStub('https://vectortiles.geo.admin.ch/fonts/{fontstack}/{range}.pbf');
    const scope = effectScope();
    scope.run(() =>
      useMapPois(
        () => map as unknown as MapLibreMap,
        () => null,
        () => true
      )
    );
    await flushPromises();
    expect(map.addLayer.mock.calls).toHaveLength(POI_CATEGORIES.length);
    expect(
      map.addLayer.mock.calls.every(
        ([layer]) => (layer as { layout: { 'text-font': string[] } }).layout['text-font'][0] === 'Frutiger Neue Regular'
      )
    ).toBe(true);

    map.addLayer.mockClear();
    resetStyle(PROTOMAPS_GLYPHS_URL);
    await listeners.get('style.load')!();
    expect(map.addLayer.mock.calls).toHaveLength(POI_CATEGORIES.length);
    expect(
      map.addLayer.mock.calls.every(
        ([layer]) => (layer as { layout: { 'text-font': string[] } }).layout['text-font'][0] === 'Noto Sans Regular'
      )
    ).toBe(true);
    scope.stop();
  });

  it('reuses vector data, updates live, and restores category choices after a style switch', async () => {
    const { map, listeners, resetStyle } = mapStub();
    const scope = effectScope();
    scope.run(() =>
      useMapPois(
        () => map as unknown as MapLibreMap,
        () => null,
        () => true
      )
    );
    await flushPromises();
    expect(map.addSource).not.toHaveBeenCalled();
    expect(map.addLayer).toHaveBeenCalledTimes(POI_CATEGORIES.length);
    expect(map.setLayoutProperty).toHaveBeenCalledWith('pois', 'visibility', 'none');
    useMapSettingsStore().setPoiCategory('water', false);
    useMapSettingsStore().setPoiOpacity(37);
    await nextTick();
    expect(map.setLayoutProperty).toHaveBeenCalledWith('mtl-poi-water', 'visibility', 'none');
    expect(map.setPaintProperty).toHaveBeenCalledWith('mtl-poi-views', 'icon-opacity', 0.37);
    resetStyle();
    await listeners.get('style.load')!();
    expect(map.addLayer).toHaveBeenCalledTimes(POI_CATEGORIES.length * 2);
    expect(map.getStyle().layers.find((layer) => layer.id === 'mtl-poi-water')).toMatchObject({
      layout: { visibility: 'none' },
    });
    scope.stop();
    expect(map.off).toHaveBeenCalledWith('style.load', expect.any(Function));
    expect(map.on.mock.calls.some(([event]) => event === 'click')).toBe(false);
  });

  it('does not mutate a disposed map after image loading completes', async () => {
    const { map } = mapStub();
    let finish!: (value: boolean) => void;
    vi.mocked(installPoiImages).mockImplementationOnce(async (_map, current) => {
      await new Promise<boolean>((resolve) => {
        finish = resolve;
      });
      return current();
    });
    const value = shallowRef(map as unknown as MapLibreMap | undefined);
    const scope = effectScope();
    scope.run(() =>
      useMapPois(
        () => value.value,
        () => null,
        () => true
      )
    );
    value.value = undefined;
    finish(true);
    await flushPromises();
    expect(map.addLayer).not.toHaveBeenCalled();
    scope.stop();
  });
});

describe('POI exploration candidates', () => {
  it('collects all local objects, deduplicates tile copies, and does not open its own presentation', async () => {
    const { map } = mapStub();
    const scope = effectScope();
    const provider = scope.run(() =>
      useMapPois(
        () => map as unknown as MapLibreMap,
        () => null,
        () => true
      )
    )!;
    await flushPromises();
    const water = {
      id: 10,
      source: 'protomaps',
      sourceLayer: 'pois',
      geometry: { type: 'Point', coordinates: [8, 47] },
      properties: { kind: 'drinking_water', name: 'Village spring', elevation: 550 },
    };
    map.queryRenderedFeatures.mockReturnValue([
      water,
      { ...water, layer: { id: 'label-copy' } },
      { ...water, id: 11, properties: { kind: 'viewpoint', name: 'Village spring' } },
    ]);

    const candidates = provider.collectCandidates({ x: 8, y: 47 }, 12);
    expect(candidates).toHaveLength(2);
    expect(candidates[0]).toMatchObject({
      id: 'poi:protomaps:pois:10',
      kind: 'poi',
      label: 'Village spring',
      detail: 'drinking water',
      coordinates: [8, 47],
      payload: { kind: 'drinking_water', elevation: '550' },
    });
    expect(map.queryRenderedFeatures).toHaveBeenCalledWith(
      [
        [-4, 35],
        [20, 59],
      ],
      { layers: expect.arrayContaining(['mtl-poi-water', 'mtl-poi-views']) }
    );
    expect(map.on.mock.calls.some(([event]) => event === 'click')).toBe(false);
    scope.stop();
    expect(provider.collectCandidates({ x: 8, y: 47 }, 12)).toEqual([]);
  });

  it('uses one fallback identity for wrapped copies while retaining different objects', async () => {
    const { map } = mapStub();
    const scope = effectScope();
    const provider = scope.run(() =>
      useMapPois(
        () => map as unknown as MapLibreMap,
        () => null,
        () => true
      )
    )!;
    await flushPromises();
    const feature = {
      source: 'protomaps',
      sourceLayer: 'pois',
      geometry: { type: 'Point', coordinates: [8, 47] },
      properties: { kind: 'drinking_water' },
    };
    map.queryRenderedFeatures.mockReturnValue([
      feature,
      { ...feature, geometry: { type: 'Point', coordinates: [368, 47] } },
      { ...feature, geometry: { type: 'Point', coordinates: [9, 47] } },
    ]);
    expect(provider.collectCandidates({ x: 8, y: 47 }, 12)).toHaveLength(2);
    scope.stop();
  });

  it('respects tool ownership, opacity, category choices, and zoom', async () => {
    const { map } = mapStub();
    let canSelect = true;
    const scope = effectScope();
    const provider = scope.run(() =>
      useMapPois(
        () => map as unknown as MapLibreMap,
        () => null,
        () => canSelect
      )
    )!;
    await flushPromises();
    map.queryRenderedFeatures.mockReturnValue([
      {
        id: 10,
        source: 'protomaps',
        sourceLayer: 'pois',
        geometry: { type: 'Point', coordinates: [8, 47] },
        properties: { kind: 'drinking_water' },
      },
    ]);
    const settings = useMapSettingsStore();
    settings.setPoiCategory('water', false);
    expect(provider.collectCandidates({ x: 8, y: 47 }, 12)).toEqual([]);
    settings.setPoiCategory('water', true);
    settings.setPoiOpacity(0);
    expect(provider.collectCandidates({ x: 8, y: 47 }, 12)).toEqual([]);
    settings.setPoiOpacity(100);
    canSelect = false;
    expect(provider.collectCandidates({ x: 8, y: 47 }, 12)).toEqual([]);
    canSelect = true;
    map.getZoom.mockReturnValue(1);
    expect(provider.collectCandidates({ x: 8, y: 47 }, 12)).toEqual([]);
    scope.stop();
  });
});
