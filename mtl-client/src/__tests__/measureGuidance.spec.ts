import { DOMWrapper, flushPromises, mount } from '@vue/test-utils';
import { defineComponent } from 'vue';
import { afterEach, describe, expect, it, vi } from 'vitest';
import MeasureBetweenPoints from '@/components/measure/MeasureBetweenPoints.vue';
import BottomSheet from '@/components/ui/BottomSheet.vue';
import { TrackProcessingStatusDtoStateEnum, type TrackProximityResponseDto } from 'x8ing-mtl-api-typescript-fetch';
import { fetchTrackDetailsForCrossingPoints, fetchTrackIdsWithinDistanceOfPoint } from '@/utils/ServiceHelper';

vi.mock('@/stores/filterStore', () => ({
  useFilterStore: () => ({
    trackSetRevision: 0,
  }),
}));

vi.mock('@/utils/ServiceHelper', () => ({
  fetchTrackDetailsForCrossingPoints: vi.fn(),
  fetchTrackIdsWithinDistanceOfPoint: vi.fn(),
}));

const fetchTrackDetailsMock = vi.mocked(fetchTrackDetailsForCrossingPoints);
const fetchZoneTrackIdsMock = vi.mocked(fetchTrackIdsWithinDistanceOfPoint);
function proximity(trackIds: number[]): TrackProximityResponseDto {
  return { trackIds, processing: { state: TrackProcessingStatusDtoStateEnum.Ready, stages: [], failedGpsFiles: 0 } };
}

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

function mapStub(glyphsUrl?: string) {
  const handlers = new Map<string, (event: unknown) => void>();
  const layers = new Set(['tracks-layer']);
  const sources = new Map<string, { setData: ReturnType<typeof vi.fn> }>();

  return {
    handlers,
    getGlyphs: () => glyphsUrl,
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

function mountMeasure(map = mapStub(), realSheet = false) {
  return {
    map,
    wrapper: mount(MeasureBetweenPoints, {
      attachTo: realSheet ? document.body : undefined,
      props: { map: map as never },
      global: {
        stubs: {
          BottomSheet: realSheet ? BottomSheet : BottomSheetStub,
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
  it('uses Swiss Topo fonts for measurement zone labels', async () => {
    fetchZoneTrackIdsMock.mockResolvedValue(proximity([1]));
    const { wrapper, map } = mountMeasure(mapStub('https://vectortiles.geo.admin.ch/fonts/{fontstack}/{range}.pbf'));
    await wrapper.vm.open();
    clickMap(map, 8.5, 47.5);
    await flushPromises();
    expect(map.addLayer).toHaveBeenCalledWith(
      expect.objectContaining({
        type: 'symbol',
        layout: expect.objectContaining({ 'text-font': ['Frutiger Neue Medium'] }),
      })
    );
    wrapper.unmount();
  });

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

  it.each([[], [1]].map((trackIds) => ({ trackIds })))(
    'warns about incomplete zone results: $trackIds',
    async ({ trackIds }) => {
      fetchZoneTrackIdsMock.mockResolvedValue({
        trackIds,
        processing: { state: TrackProcessingStatusDtoStateEnum.Running, stages: [], failedGpsFiles: 0 },
      });
      const { wrapper, map } = mountMeasure();
      await wrapper.vm.open();
      clickMap(map, 8.5, 47.5);
      await flushPromises();
      expect(wrapper.text()).toContain('Tracks are still being processed. Some nearby tracks may be missing.');
      await wrapper.get('[aria-label="Clear all"]').trigger('click');
      expect(wrapper.text()).not.toContain('Tracks are still being processed.');
      wrapper.unmount();
    }
  );

  it('connects map taps when a direct route opens before the map is ready', async () => {
    fetchZoneTrackIdsMock.mockResolvedValue(proximity([1]));
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
    fetchZoneTrackIdsMock.mockResolvedValueOnce(proximity([1, 2])).mockResolvedValueOnce(proximity([2, 3]));
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
    expect(wrapper.get('.measure-map-guidance').text()).toContain('1 candidate track');
    expect(wrapper.get('.measure-flow-node--final').text()).toContain('1');
    expect(wrapper.get('.measure-toolbar-btn--analyze').attributes('disabled')).toBeUndefined();
    expect(wrapper.get('.measure-toolbar-btn--analyze').attributes('title')).toBe('Compare tracks crossing every zone');

    wrapper.unmount();
  });

  it('explains how to recover when the first zone has no tracks', async () => {
    fetchZoneTrackIdsMock.mockResolvedValueOnce(proximity([]));
    const { map, wrapper } = mountMeasure();
    await wrapper.vm.open();

    clickMap(map, 8.5, 47.5);
    await flushPromises();

    expect(wrapper.get('.measure-map-guidance').text()).toContain('No tracks cross zone A');
    expect(wrapper.get('.measure-map-guidance').text()).toContain('Undo the zone or increase the detection radius.');
    expect(wrapper.find('.measure-explanation').exists()).toBe(false);
    expect(wrapper.get('.measure-toolbar-btn--analyze').attributes('disabled')).toBeDefined();

    wrapper.unmount();
  });

  it('explains how to recover when no track crosses every zone', async () => {
    fetchZoneTrackIdsMock.mockResolvedValueOnce(proximity([1])).mockResolvedValueOnce(proximity([2]));
    const { map, wrapper } = mountMeasure();
    await wrapper.vm.open();

    clickMap(map, 8.5, 47.5);
    clickMap(map, 8.51, 47.51);
    await flushPromises();

    expect(wrapper.get('.measure-map-guidance').text()).toContain('No candidate tracks touch every zone');
    expect(wrapper.get('.measure-map-guidance').text()).toContain('Undo a zone or increase the detection radius.');
    expect(wrapper.get('.measure-toolbar-btn--analyze').attributes('disabled')).toBeDefined();
    expect(fetchTrackDetailsMock).not.toHaveBeenCalled();

    wrapper.unmount();
  });

  it('closes analyzed results before reopening a fresh selector', async () => {
    fetchZoneTrackIdsMock.mockResolvedValue(proximity([1]));
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
    fetchZoneTrackIdsMock.mockResolvedValue(proximity([1]));
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

  it.each(['radius', 'undo', 'add', 'clear'])(
    'requires re-analysis after editing %s instead of reopening stale results',
    async (edit) => {
      fetchZoneTrackIdsMock.mockResolvedValue(proximity([1]));
      fetchTrackDetailsMock.mockResolvedValue({ crossings: { 1: {} }, segmentsStats: [] } as never);
      const { map, wrapper } = mountMeasure();
      await wrapper.vm.open();
      clickMap(map, 8.5, 47.5);
      clickMap(map, 8.51, 47.51);
      clickMap(map, 8.52, 47.52);
      await flushPromises();
      await wrapper.get('.measure-toolbar-btn--analyze').trigger('click');
      await flushPromises();

      wrapper.getComponent({ name: 'DisplayMeasureResults' }).vm.$emit('edit-zones');
      await flushPromises();
      expect(wrapper.findAll('.measure-flow-node:not(.measure-flow-node--final)')).toHaveLength(3);
      expect(wrapper.find('.measure-return-control').exists()).toBe(true);

      if (edit === 'radius') {
        const slider = wrapper.getComponent(MtlSliderStub);
        slider.vm.$emit('update:modelValue', Number(slider.props('modelValue')) + 1);
      } else if (edit === 'undo') await wrapper.get('[aria-label="Undo last point"]').trigger('click');
      else if (edit === 'clear') await wrapper.get('[aria-label="Clear all"]').trigger('click');
      else clickMap(map, 8.53, 47.53);
      await flushPromises();

      expect(wrapper.find('.measure-return-control').exists()).toBe(false);
      expect(wrapper.find('display-measure-results-stub').exists()).toBe(false);
      expect(wrapper.vm.getNavigationState()).toBeNull();
      expect(fetchTrackDetailsMock).toHaveBeenCalledTimes(1);
      wrapper.unmount();
    }
  );

  it('returns to the retained analysis when the restored zones and radius are unchanged', async () => {
    fetchZoneTrackIdsMock.mockResolvedValue(proximity([1]));
    fetchTrackDetailsMock.mockResolvedValue({ crossings: { 1: {} }, segmentsStats: [] } as never);
    const { map, wrapper } = mountMeasure();
    await wrapper.vm.open();
    clickMap(map, 8.5, 47.5);
    clickMap(map, 8.51, 47.51);
    await flushPromises();
    await wrapper.get('.measure-toolbar-btn--analyze').trigger('click');
    await flushPromises();
    const original = wrapper.vm.getNavigationState();
    wrapper.getComponent({ name: 'DisplayMeasureResults' }).vm.$emit('edit-zones');
    await flushPromises();
    await wrapper.get('.measure-return-control').trigger('click');
    await flushPromises();
    expect(wrapper.vm.getNavigationState()?.result).toEqual(original?.result);
    expect(fetchTrackDetailsMock).toHaveBeenCalledTimes(1);
    wrapper.unmount();
  });

  it('reopens a compact zone editor after desktop results are resized to mobile', async () => {
    const originalWidth = window.innerWidth;
    const originalHeight = window.innerHeight;
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 1440 });
    Object.defineProperty(window, 'innerHeight', { configurable: true, value: 900 });
    vi.stubGlobal(
      'ResizeObserver',
      class {
        observe() {}
        disconnect() {}
      }
    );
    const rect = vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(function () {
      const height = this.classList.contains('sheet') ? Number.parseFloat(this.style.height) || 0 : 0;
      return new DOMRect(0, window.innerHeight - height, window.innerWidth, height);
    });
    fetchZoneTrackIdsMock.mockResolvedValue(proximity([1]));
    fetchTrackDetailsMock.mockResolvedValue({ crossings: { 1: {} }, segmentsStats: [] } as never);
    const { map, wrapper } = mountMeasure(mapStub(), true);
    try {
      await wrapper.vm.open();
      clickMap(map, 8.5, 47.5);
      clickMap(map, 8.51, 47.51);
      await flushPromises();
      await new DOMWrapper(document.body).get('.measure-toolbar-btn--analyze').trigger('click');
      await flushPromises();
      const original = wrapper.vm.getNavigationState();
      Object.defineProperty(window, 'innerWidth', { configurable: true, value: 390 });
      Object.defineProperty(window, 'innerHeight', { configurable: true, value: 844 });
      window.dispatchEvent(new Event('resize'));
      await flushPromises();
      wrapper.getComponent({ name: 'DisplayMeasureResults' }).vm.$emit('edit-zones');
      await flushPromises();

      const editor = document.querySelector('.measure-sheet')!.closest<HTMLElement>('.sheet')!;
      expect(editor.style.height).toBe('300px');
      expect(editor.classList.contains('sheet--fullscreen')).toBe(false);
      expect(editor.querySelectorAll('.measure-flow-node:not(.measure-flow-node--final)')).toHaveLength(2);
      await new DOMWrapper(editor).get('.measure-return-control').trigger('click');
      await flushPromises();
      expect(wrapper.vm.getNavigationState()?.result).toEqual(original?.result);
      expect(fetchTrackDetailsMock).toHaveBeenCalledTimes(1);
    } finally {
      wrapper.unmount();
      rect.mockRestore();
      vi.unstubAllGlobals();
      Object.defineProperty(window, 'innerWidth', { configurable: true, value: originalWidth });
      Object.defineProperty(window, 'innerHeight', { configurable: true, value: originalHeight });
    }
  });
  it('keeps mobile placement guidance and zone counts inside the sheet when the viewport changes', async () => {
    const originalWidth = window.innerWidth;
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 390 });
    fetchZoneTrackIdsMock.mockResolvedValue(proximity([1]));
    const { wrapper, map } = mountMeasure();
    try {
      await wrapper.vm.open();
      expect(wrapper.find('.measure-map-overlay').exists()).toBe(false);
      expect(wrapper.get('[data-test="bottom-sheet"] .measure-sheet-guidance').text()).toContain(
        'Tap a track to place zone A'
      );
      clickMap(map, 8.5, 47.5);
      await flushPromises();
      expect(wrapper.get('.measure-sheet-guidance').text()).toContain('Tap a track to place zone B');
      expect(wrapper.get('.measure-sheet-guidance .measure-flow-node-circle').text()).toBe('A');
      Object.defineProperty(window, 'innerWidth', { configurable: true, value: 1284 });
      window.dispatchEvent(new Event('resize'));
      await flushPromises();
      expect(wrapper.find('.measure-sheet-guidance').exists()).toBe(false);
      expect(wrapper.get('.measure-map-overlay').text()).toContain('Tap a track to place zone B');
    } finally {
      wrapper.unmount();
      Object.defineProperty(window, 'innerWidth', { configurable: true, value: originalWidth });
    }
  });
});
