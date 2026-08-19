import { flushPromises, mount } from '@vue/test-utils';
import { defineComponent } from 'vue';
import { afterEach, describe, expect, it, vi } from 'vitest';
import MeasureBetweenPoints from '@/components/measure/MeasureBetweenPoints.vue';
import { fetchTrackDetailsForCrossingPoints, fetchTrackIdsWithinDistanceOfPoint } from '@/utils/ServiceHelper';

vi.mock('@/utils/ServiceHelper', () => ({
  fetchTrackDetailsForCrossingPoints: vi.fn(),
  fetchTrackIdsWithinDistanceOfPoint: vi.fn(),
}));

const fetchTrackDetailsMock = vi.mocked(fetchTrackDetailsForCrossingPoints);
const fetchZoneTrackIdsMock = vi.mocked(fetchTrackIdsWithinDistanceOfPoint);

const BottomSheetStub = defineComponent({
  name: 'BottomSheet',
  props: {
    modelValue: Boolean,
  },
  emits: ['update:modelValue', 'closed'],
  template: '<section v-if="modelValue" data-test="bottom-sheet"><slot /></section>',
});

const MtlSliderStub = defineComponent({
  name: 'MtlSlider',
  inheritAttrs: false,
  props: {
    modelValue: Number,
    ariaLabelledby: String,
    ariaValueText: String,
  },
  emits: ['update:modelValue'],
  template: '<div data-test="radius-slider" :aria-labelledby="ariaLabelledby" :aria-valuetext="ariaValueText"></div>',
});

function mapStub() {
  const handlers = new Map<string, (event: unknown) => void>();
  const layers = new Set(['tracks-layer']);
  const sources = new Map<string, { setData: ReturnType<typeof vi.fn> }>();

  return {
    handlers,
    getBounds: () => ({
      getNorth: () => 47.51,
      getSouth: () => 47.49,
      getEast: () => 8.52,
      getWest: () => 8.48,
    }),
    on: vi.fn((event: string, handler: (payload: unknown) => void) => handlers.set(event, handler)),
    off: vi.fn((event: string) => handlers.delete(event)),
    project: vi.fn(([lng, lat]: [number, number]) => ({ x: lng * 100, y: lat * 100 })),
    queryRenderedFeatures: vi.fn(() => [{}]),
    addSource: vi.fn((id: string) => sources.set(id, { setData: vi.fn() })),
    addLayer: vi.fn((layer: { id: string }) => layers.add(layer.id)),
    getLayer: vi.fn((id: string) => (layers.has(id) ? { id } : undefined)),
    getSource: vi.fn((id: string) => sources.get(id)),
    setPaintProperty: vi.fn(),
    removeLayer: vi.fn((id: string) => layers.delete(id)),
    removeSource: vi.fn((id: string) => sources.delete(id)),
  };
}

function mountMeasure(map = mapStub()) {
  return {
    map,
    wrapper: mount(MeasureBetweenPoints, {
      props: { map: map as never },
      global: {
        stubs: {
          BottomSheet: BottomSheetStub,
          DisplayMeasureResults: true,
          MtlSlider: MtlSliderStub,
        },
      },
    }),
  };
}

function clickMap(map: ReturnType<typeof mapStub>, lng: number, lat: number) {
  map.handlers.get('click')?.({ lngLat: { lng, lat } });
}

afterEach(() => {
  vi.clearAllMocks();
});

describe('segment analyzer guidance', () => {
  it('starts with a clear map instruction and a compact labeled radius control', async () => {
    const { wrapper } = mountMeasure();

    await wrapper.vm.open();

    expect(wrapper.get('.measure-map-guidance').text()).toContain('Tap a track to place zone A');
    expect(wrapper.get('.measure-map-guidance').text()).toContain('Start where the segment should begin.');
    expect(wrapper.get('.measure-radius-label').text()).toBe('Detection radius');
    expect(wrapper.get('.measure-radius-description').text()).toBe('How close a track must pass to each zone');
    expect(wrapper.get('[data-test="radius-slider"]').attributes('aria-labelledby')).toBe('measure-radius-label');
    expect(wrapper.get('[data-test="radius-slider"]').attributes('aria-valuetext')).toBe(
      wrapper.get('.measure-radius-value').text()
    );

    const analyzeButton = wrapper.get('.measure-toolbar-btn--analyze');
    expect(analyzeButton.attributes('disabled')).toBeDefined();
    expect(analyzeButton.attributes('title')).toBe('Place zones A and B first');

    wrapper.unmount();
  });

  it('connects map taps when a direct route opens before the map is ready', async () => {
    fetchZoneTrackIdsMock.mockResolvedValue([1]);
    const wrapper = mount(MeasureBetweenPoints, {
      props: { map: null as never },
      global: {
        stubs: {
          BottomSheet: BottomSheetStub,
          DisplayMeasureResults: true,
          MtlSlider: MtlSliderStub,
        },
      },
    });
    await wrapper.vm.open();

    expect(wrapper.get('.measure-map-guidance').text()).toContain('Tap a track to place zone A');

    const map = mapStub();
    await wrapper.setProps({ map: map as never });
    expect(map.on).toHaveBeenCalledWith('click', expect.any(Function));

    clickMap(map, 8.5, 47.5);
    await flushPromises();
    expect(wrapper.get('.measure-map-guidance').text()).toContain('Tap a track to place zone B');

    wrapper.unmount();
    expect(map.off).toHaveBeenCalledWith('click', expect.any(Function));
  });

  it('guides zone B placement and enables analysis for shared tracks', async () => {
    fetchZoneTrackIdsMock.mockResolvedValueOnce([1, 2]).mockResolvedValueOnce([2, 3]);
    const { map, wrapper } = mountMeasure();
    await wrapper.vm.open();

    clickMap(map, 8.5, 47.5);
    await flushPromises();

    expect(wrapper.get('.measure-map-guidance').text()).toContain('Tap a track to place zone B');
    expect(wrapper.find('.measure-flow-node--final').exists()).toBe(false);
    expect(wrapper.get('.measure-toolbar-btn--analyze').attributes('disabled')).toBeDefined();

    clickMap(map, 8.51, 47.51);
    await flushPromises();

    expect(wrapper.get('.measure-map-guidance').text()).toContain('Ready to analyze');
    expect(wrapper.get('.measure-map-guidance').text()).toContain('1 matching track');
    expect(wrapper.get('.measure-flow-node--final').text()).toContain('1');
    expect(wrapper.get('.measure-toolbar-btn--analyze').attributes('disabled')).toBeUndefined();
    expect(wrapper.get('.measure-toolbar-btn--analyze').attributes('title')).toBe('Compare tracks crossing every zone');

    wrapper.unmount();
  });

  it('explains how to recover when the first zone has no tracks', async () => {
    fetchZoneTrackIdsMock.mockResolvedValueOnce([]);
    const { map, wrapper } = mountMeasure();
    await wrapper.vm.open();

    clickMap(map, 8.5, 47.5);
    await flushPromises();

    expect(wrapper.get('.measure-map-guidance').text()).toContain('No tracks cross zone A');
    expect(wrapper.get('.measure-map-guidance').text()).toContain('Undo the zone or increase the detection radius.');
    expect(wrapper.get('.measure-placement-text').text()).toBe('Adjust zone A or the radius');
    expect(wrapper.get('.measure-toolbar-btn--analyze').attributes('disabled')).toBeDefined();

    wrapper.unmount();
  });

  it('explains how to recover when no track crosses every zone', async () => {
    fetchZoneTrackIdsMock.mockResolvedValueOnce([1]).mockResolvedValueOnce([2]);
    const { map, wrapper } = mountMeasure();
    await wrapper.vm.open();

    clickMap(map, 8.5, 47.5);
    clickMap(map, 8.51, 47.51);
    await flushPromises();

    expect(wrapper.get('.measure-map-guidance').text()).toContain('No tracks cross every zone');
    expect(wrapper.get('.measure-map-guidance').text()).toContain('Undo a zone or increase the detection radius.');
    expect(wrapper.get('.measure-toolbar-btn--analyze').attributes('disabled')).toBeDefined();
    expect(fetchTrackDetailsMock).not.toHaveBeenCalled();

    wrapper.unmount();
  });

  it('closes analyzed results before reopening a fresh selector', async () => {
    fetchZoneTrackIdsMock.mockResolvedValue([1]);
    fetchTrackDetailsMock.mockResolvedValue({
      crossings: { 1: {} },
      segmentsStats: [],
      tracksPerZone: { A: 1, B: 1 },
    } as never);
    const { map, wrapper } = mountMeasure();
    await wrapper.vm.open();

    clickMap(map, 8.5, 47.5);
    clickMap(map, 8.51, 47.51);
    await flushPromises();
    await wrapper.get('.measure-toolbar-btn--analyze').trigger('click');
    await flushPromises();

    expect(wrapper.find('.measure-sheet').exists()).toBe(false);
    expect(wrapper.find('display-measure-results-stub').exists()).toBe(true);
    expect(wrapper.emitted('active-changed')).toEqual([[true]]);

    await wrapper.vm.toggle();
    expect(wrapper.findAll('[data-test="bottom-sheet"]')).toHaveLength(0);
    expect(wrapper.emitted('active-changed')).toEqual([[true], [false]]);

    await wrapper.vm.toggle();
    expect(wrapper.findAll('[data-test="bottom-sheet"]')).toHaveLength(1);
    expect(wrapper.find('.measure-sheet').exists()).toBe(true);
    expect(wrapper.find('display-measure-results-stub').exists()).toBe(false);

    wrapper.unmount();
  });

  it('restores analyzed results after temporary track-detail navigation', async () => {
    fetchZoneTrackIdsMock.mockResolvedValue([1]);
    fetchTrackDetailsMock.mockResolvedValue({
      crossings: { 1: {} },
      segmentsStats: [],
      tracksPerZone: { A: 1, B: 1 },
    } as never);
    const { map, wrapper } = mountMeasure();
    await wrapper.vm.open();
    clickMap(map, 8.5, 47.5);
    clickMap(map, 8.51, 47.51);
    await flushPromises();
    await wrapper.get('.measure-toolbar-btn--analyze').trigger('click');
    await flushPromises();
    const navigationState = wrapper.vm.getNavigationState();

    wrapper.vm.close();
    await wrapper.vm.open();
    wrapper.vm.restoreNavigationState(navigationState);
    await flushPromises();

    expect(wrapper.findAll('[data-test="bottom-sheet"]')).toHaveLength(1);
    expect(wrapper.find('.measure-sheet').exists()).toBe(false);
    expect(wrapper.find('display-measure-results-stub').exists()).toBe(true);

    wrapper.unmount();
  });
});
