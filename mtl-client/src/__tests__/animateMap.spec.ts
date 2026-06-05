import { mount } from '@vue/test-utils';
import { defineComponent, nextTick } from 'vue';
import { describe, expect, it, vi } from 'vitest';
import AnimateMap from '@/components/animate/AnimateMap.vue';

const BottomSheetStub = defineComponent({
  name: 'BottomSheet',
  props: {
    modelValue: Boolean,
  },
  template: '<div v-if="modelValue"><slot /></div>',
});

const MtlSliderStub = defineComponent({
  name: 'MtlSlider',
  template: '<div data-test="slider" />',
});

function feature(id: number, startDate: string) {
  return {
    type: 'Feature',
    properties: { id, startDate },
    geometry: {
      type: 'LineString',
      coordinates: [
        [8, 47],
        [8.1, 47.1],
      ],
    },
  };
}

describe('AnimateMap track source', () => {
  it('uses the explicit map geojson prop for its track timeline', async () => {
    const wrapper = mount(AnimateMap, {
      props: {
        geojson: {
          features: [feature(2, '2026-05-02T08:00:00Z'), feature(1, '2026-05-01T08:00:00Z')],
        },
      },
      global: {
        stubs: {
          BottomSheet: BottomSheetStub,
          MtlSlider: MtlSliderStub,
        },
      },
    });

    await (wrapper.vm as unknown as { toggle: () => Promise<void> }).toggle();
    await nextTick();

    expect(wrapper.text()).toContain('2 / 2');
  });

  it('does not reset track opacity when closed while inactive', () => {
    const map = {
      getLayer: vi.fn((id: string) => (id === 'tracks-layer' ? { id } : undefined)),
      getPaintProperty: vi.fn(() => 0.33),
      setPaintProperty: vi.fn(),
      removeLayer: vi.fn(),
      getSource: vi.fn(),
      removeSource: vi.fn(),
      addSource: vi.fn(),
      addLayer: vi.fn(),
    };
    const wrapper = mount(AnimateMap, {
      props: { map },
      global: {
        stubs: {
          BottomSheet: BottomSheetStub,
          MtlSlider: MtlSliderStub,
        },
      },
    });

    (wrapper.vm as unknown as { close: () => void }).close();

    expect(map.setPaintProperty).not.toHaveBeenCalled();
  });

  it('restores the previous track opacity after animation cleanup', async () => {
    let animationLayerVisible = false;
    const map = {
      getLayer: vi.fn((id: string) => {
        if (id === 'tracks-layer') return { id };
        if (id === 'animation-layer' && animationLayerVisible) return { id };
        return undefined;
      }),
      getPaintProperty: vi.fn(() => 0.33),
      setPaintProperty: vi.fn(),
      removeLayer: vi.fn((id: string) => {
        if (id === 'animation-layer') animationLayerVisible = false;
      }),
      getSource: vi.fn(() => undefined),
      removeSource: vi.fn(),
      addSource: vi.fn(),
      addLayer: vi.fn((layer: { id: string }) => {
        if (layer.id === 'animation-layer') animationLayerVisible = true;
      }),
    };
    const wrapper = mount(AnimateMap, {
      props: {
        map,
        geojson: {
          features: [feature(2, '2026-05-02T08:00:00Z'), feature(1, '2026-05-01T08:00:00Z')],
        },
      },
      global: {
        stubs: {
          BottomSheet: BottomSheetStub,
          MtlSlider: MtlSliderStub,
        },
      },
    });

    await (wrapper.vm as unknown as { toggle: () => Promise<void>; close: () => void }).toggle();
    await nextTick();
    await wrapper.find('.am-play-hero').trigger('click');
    (wrapper.vm as unknown as { close: () => void }).close();

    expect(map.setPaintProperty).toHaveBeenCalledWith('tracks-layer', 'line-opacity', 0);
    expect(map.setPaintProperty).toHaveBeenLastCalledWith('tracks-layer', 'line-opacity', 0.33);
  });

  it('hides and restores all track context layers during animation', async () => {
    const trackPaintProperties = [
      ['tracks-layer', 'line-opacity', 0.33],
      ['tracks-highlight-layer', 'line-opacity', 0.44],
      ['tracks-highlight-dash-layer', 'line-opacity', 0.55],
      ['tracks-dot-layer', 'circle-opacity', 0.66],
      ['tracks-dot-layer', 'circle-stroke-opacity', 0.77],
      ['tracks-overview-dots', 'circle-opacity', 0.85],
      ['tracks-overview-dots', 'circle-stroke-opacity', 0.88],
      ['tracks-highlight-circle-layer', 'circle-opacity', 0.99],
      ['track-points-layer', 'icon-opacity', 0.9],
    ] as const;
    const layers = new Set(trackPaintProperties.map(([layerId]) => layerId));
    const paintValues = new Map(
      trackPaintProperties.map(([layerId, property, value]) => [`${layerId}:${property}`, value])
    );
    const layoutValues = new Map(trackPaintProperties.map(([layerId]) => [layerId, 'visible']));
    let animationLayerVisible = false;
    const map = {
      getLayer: vi.fn((id: string) => {
        if (id === 'animation-layer') return animationLayerVisible ? { id } : undefined;
        return layers.has(id) ? { id } : undefined;
      }),
      getLayoutProperty: vi.fn((layerId: string) => layoutValues.get(layerId)),
      getPaintProperty: vi.fn((layerId: string, property: string) => paintValues.get(`${layerId}:${property}`)),
      setLayoutProperty: vi.fn((layerId: string, _property: string, value: unknown) => {
        layoutValues.set(layerId, value as string);
      }),
      setPaintProperty: vi.fn((layerId: string, property: string, value: unknown) => {
        paintValues.set(`${layerId}:${property}`, value as number);
      }),
      removeLayer: vi.fn((id: string) => {
        if (id === 'animation-layer') animationLayerVisible = false;
      }),
      getSource: vi.fn(() => undefined),
      removeSource: vi.fn(),
      addSource: vi.fn(),
      addLayer: vi.fn((layer: { id: string }) => {
        if (layer.id === 'animation-layer') animationLayerVisible = true;
      }),
    };
    const wrapper = mount(AnimateMap, {
      props: {
        map,
        geojson: {
          features: [feature(2, '2026-05-02T08:00:00Z'), feature(1, '2026-05-01T08:00:00Z')],
        },
      },
      global: {
        stubs: {
          BottomSheet: BottomSheetStub,
          MtlSlider: MtlSliderStub,
        },
      },
    });

    await (wrapper.vm as unknown as { toggle: () => Promise<void>; close: () => void }).toggle();
    await nextTick();

    for (const [layerId, property] of trackPaintProperties) {
      expect(map.setLayoutProperty).toHaveBeenCalledWith(layerId, 'visibility', 'none');
      expect(map.setPaintProperty).toHaveBeenCalledWith(layerId, property, 0);
      expect(layoutValues.get(layerId)).toBe('none');
      expect(paintValues.get(`${layerId}:${property}`)).toBe(0);
    }

    await wrapper.find('.am-play-hero').trigger('click');
    await wrapper.find('.am-stop-btn').trigger('click');

    expect(map.addLayer).toHaveBeenCalledWith(
      expect.objectContaining({
        id: 'animation-layer',
        layout: expect.objectContaining({ 'line-cap': 'butt' }),
      })
    );

    (wrapper.vm as unknown as { close: () => void }).close();

    for (const [layerId, property, value] of trackPaintProperties) {
      expect(map.setPaintProperty).toHaveBeenCalledWith(layerId, property, value);
      expect(map.setLayoutProperty).toHaveBeenCalledWith(layerId, 'visibility', 'visible');
      expect(layoutValues.get(layerId)).toBe('visible');
      expect(paintValues.get(`${layerId}:${property}`)).toBe(value);
    }
    expect(animationLayerVisible).toBe(false);
  });
});
