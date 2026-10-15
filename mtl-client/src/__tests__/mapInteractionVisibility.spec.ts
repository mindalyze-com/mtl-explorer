import { enableAutoUnmount, mount } from '@vue/test-utils';
import { defineComponent, shallowRef } from 'vue';
import type { Map as MapLibreMap } from 'maplibre-gl';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { BottomSheetLayoutState } from '@/components/ui/BottomSheet.vue';
import type { MapInteractionState } from '@/components/map/mapInteractionCoordinator';
import {
  MAP_INTERACTION_REVEAL_SETTLE_MS,
  useMapInteractionVisibility,
} from '@/components/map/composables/useMapInteractionVisibility';

enableAutoUnmount(afterEach);
beforeEach(() => vi.useFakeTimers());
afterEach(() => {
  vi.clearAllTimers();
  vi.useRealTimers();
});

function interaction(id = 1): MapInteractionState {
  return {
    id,
    context: {
      lngLat: { lng: 8, lat: 47 },
      point: { x: 600, y: 650 },
      inputKind: 'pointer',
      trackSetRevision: 1,
      mediaScope: 'allIndexed',
      eligibility: {},
    },
    presentation: 'chooser',
    selected: null,
    candidates: [],
    sources: [],
    processing: null,
  };
}
function layout(overrides: Partial<BottomSheetLayoutState> = {}): BottomSheetLayoutState {
  return {
    open: true,
    dragging: false,
    fullscreen: false,
    detentId: 'choice',
    topPx: 288,
    bottomPx: 720,
    leftPx: 218,
    rightPx: 1138,
    widthPx: 920,
    heightPx: 432,
    ...overrides,
  };
}
function setup(point = { x: 600, y: 650 }, rect = new DOMRect(0, 0, 1280, 720)) {
  const state = shallowRef<MapInteractionState | null>(interaction());
  const handlers = new Set<(event: { originalEvent?: Event }) => void>();
  const container = document.createElement('div');
  container.getBoundingClientRect = () => rect;
  const camera = {
    getContainer: vi.fn(() => container),
    project: vi.fn(() => point),
    panBy: vi.fn((offset: number[]) => {
      point.y -= offset[1];
      for (const handler of handlers) handler({});
    }),
    on: vi.fn((_type: string, handler: (event: { originalEvent?: Event }) => void) => handlers.add(handler)),
    off: vi.fn((_type: string, handler: (event: { originalEvent?: Event }) => void) => handlers.delete(handler)),
  };
  const map = shallowRef<MapLibreMap | undefined>(camera as unknown as MapLibreMap);
  let api!: ReturnType<typeof useMapInteractionVisibility>;
  const wrapper = mount(
    defineComponent({
      setup() {
        api = useMapInteractionVisibility({ map: () => map.value, interaction: () => state.value });
        return () => null;
      },
    })
  );
  return { api, wrapper, state, map, camera, handlers, point };
}
const settle = () => vi.advanceTimersByTimeAsync(MAP_INTERACTION_REVEAL_SETTLE_MS);

describe('map exploration click visibility', () => {
  it('pans only enough to reveal the marker and supplies no zoom or camera orientation changes', async () => {
    const test = setup();
    test.api.onLayoutChange(layout());
    await settle();
    expect(test.camera.panBy).toHaveBeenCalledExactlyOnceWith([0, 394], { duration: 180 });
    expect(test.point).toEqual({ x: 600, y: 256 });
    expect(test.camera.project).toHaveBeenCalledWith({ lng: 8, lat: 47 });
    expect(test.state.value?.context.point).toEqual({ x: 600, y: 650 });
  });

  it.each([
    { x: 600, y: 100 },
    { x: 180, y: 650 },
    { x: 1170, y: 650 },
    { x: 600, y: 270 },
  ])('does not move a visible point at $x,$y', async (point) => {
    const test = setup({ ...point });
    test.api.onLayoutChange(layout());
    await settle();
    expect(test.camera.panBy).not.toHaveBeenCalled();
    test.api.onLayoutChange(layout({ topPx: 80, heightPx: 640 }));
    await settle();
    expect(test.camera.panBy).not.toHaveBeenCalled();
  });

  it('uses viewport coordinates for an offset map and reprojects at execution time', async () => {
    const test = setup({ x: 400, y: 500 }, new DOMRect(100, 80, 900, 600));
    test.api.onLayoutChange(layout({ leftPx: 300, rightPx: 950, topPx: 420, bottomPx: 680 }));
    test.point.y = 540;
    await settle();
    expect(test.camera.panBy).toHaveBeenCalledExactlyOnceWith([0, 232], { duration: 180 });
  });

  it('waits for animated sheet geometry to settle before evaluating visibility', async () => {
    const test = setup();
    test.api.onLayoutChange(layout({ topPx: 700 }));
    await vi.advanceTimersByTimeAsync(60);
    test.api.onLayoutChange(layout({ topPx: 550 }));
    await vi.advanceTimersByTimeAsync(60);
    expect(test.camera.panBy).not.toHaveBeenCalled();
    test.api.onLayoutChange(layout());
    await settle();
    expect(test.camera.panBy).toHaveBeenCalledExactlyOnceWith([0, 394], { duration: 180 });
  });

  it('reveals a second click without another layout event and does not repeat on results, chips or resizing', async () => {
    const test = setup();
    test.api.onLayoutChange(layout());
    await settle();
    test.state.value = {
      ...test.state.value!,
      sources: [{ id: 'tracks', label: 'GPS', kind: 'tracks', status: 'complete' }],
    };
    test.api.onLayoutChange(layout({ topPx: 180 }));
    await settle();
    expect(test.camera.panBy).toHaveBeenCalledOnce();
    test.point.y = 650;
    test.state.value = interaction(2);
    await settle();
    expect(test.camera.panBy).toHaveBeenCalledTimes(2);
    expect(test.point.y).toBe(148);
  });

  it('ignores a previous detail detent until the chooser reports its own geometry', async () => {
    const test = setup();
    test.api.onLayoutChange(layout({ detentId: 'details', topPx: 620, heightPx: 100 }));
    await settle();
    expect(test.camera.panBy).not.toHaveBeenCalled();
    test.state.value = interaction(2);
    await settle();
    expect(test.camera.panBy).not.toHaveBeenCalled();
    test.api.onLayoutChange(layout());
    await settle();
    expect(test.point.y).toBe(256);
  });

  it.each(['manual movement', 'selection', 'close', 'unmount'] as const)(
    'cancels a pending reveal on %s',
    async (action) => {
      const test = setup();
      test.api.onLayoutChange(layout());
      if (action === 'manual movement')
        for (const handler of test.handlers) handler({ originalEvent: new Event('mousedown') });
      if (action === 'selection') test.state.value = { ...test.state.value!, presentation: 'target' };
      if (action === 'close') test.state.value = null;
      if (action === 'unmount') test.wrapper.unmount();
      await settle();
      expect(test.camera.panBy).not.toHaveBeenCalled();
      if (action === 'manual movement' || action === 'selection') {
        test.state.value = { ...test.state.value!, presentation: 'chooser' };
        test.api.onLayoutChange(layout());
        await settle();
        expect(test.camera.panBy).not.toHaveBeenCalled();
      }
    }
  );

  it('supersedes an old pending click and ignores programmatic movement', async () => {
    const test = setup();
    test.api.onLayoutChange(layout());
    test.state.value = interaction(2);
    for (const handler of test.handlers) handler({});
    await settle();
    expect(test.camera.panBy).toHaveBeenCalledOnce();
  });

  it.each(['dragging', 'medium'] as const)(
    'leaves the camera alone after an early sheet resize: %s',
    async (change) => {
      const test = setup();
      test.api.onLayoutChange(layout());
      test.api.onLayoutChange(layout(change === 'dragging' ? { dragging: true } : { detentId: 'medium' }));
      test.api.onLayoutChange(layout());
      await settle();
      expect(test.camera.panBy).not.toHaveBeenCalled();
    }
  );

  it.each([{ fullscreen: true }, { topPx: 40 }, { dragging: true }, { open: false }])(
    'does not pan when there is no usable exposed map: %j',
    async (overrides) => {
      const test = setup();
      test.api.onLayoutChange(layout(overrides));
      await settle();
      expect(test.camera.panBy).not.toHaveBeenCalled();
    }
  );

  it('does not pull an anchor back after it has moved outside the map', async () => {
    const test = setup({ x: 600, y: 750 });
    test.api.onLayoutChange(layout());
    await settle();
    expect(test.camera.panBy).not.toHaveBeenCalled();
  });

  it('releases listeners and timers when the map changes or the component closes', async () => {
    const test = setup();
    test.api.onLayoutChange(layout());
    test.map.value = undefined;
    expect(test.handlers.size).toBe(0);
    await settle();
    expect(test.camera.panBy).not.toHaveBeenCalled();
    test.map.value = test.camera as unknown as MapLibreMap;
    expect(test.handlers.size).toBe(1);
    test.wrapper.unmount();
    expect(test.handlers.size).toBe(0);
    expect(vi.getTimerCount()).toBe(0);
  });
});
