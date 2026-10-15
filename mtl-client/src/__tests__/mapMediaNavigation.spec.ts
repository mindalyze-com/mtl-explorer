import { enableAutoUnmount, flushPromises, shallowMount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { defineComponent, nextTick, ref } from 'vue';
import { createMemoryHistory, createRouter } from 'vue-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import Map2DRenderer from '@/components/map/Map2DRenderer.vue';
import { useMainMapController } from '@/components/map/useMainMapController';

vi.mock('@/components/map/useMainMapController', () => ({ useMainMapController: vi.fn() }));
enableAutoUnmount(afterEach);

const SheetStub = defineComponent({
  props: { modelValue: Boolean },
  emits: ['update:modelValue', 'closed'],
  template: `<div v-if="modelValue"><button aria-label="Close photos"
    @click="$emit('update:modelValue', false); $emit('closed')">Close</button><slot /></div>`,
});
const PreviewStub = defineComponent({
  props: { overviewBounds: Array },
  emits: ['open-on-map', 'select'],
  template: `<div>
    <button @click="$emit('select', 45)">Next photo</button>
    <button @click="$emit('open-on-map')">Open on main map</button>
  </div>`,
});
const EmptyStub = defineComponent({ template: '<div />' });
const photos = [
  { id: 44, lat: 41.23833, lng: -8.53928 },
  { id: 45, lat: 41.20759, lng: -8.58204 },
];

async function mountMap(points = photos) {
  const controller = {
    overlayMap: ref(undefined),
    mapInteraction: ref(null),
    mapInteractionDetailKind: ref(null),
    mapInteractionDetailLoading: ref(false),
    mapInteractionDetailError: ref(null),
    trackPointsPopupContext: ref(null),
    endMapInteraction: vi.fn(),
    selectMapTarget: vi.fn(),
    showMapTargetChooser: vi.fn(),
    backFromMapTargetChooser: vi.fn(),
    openMapInteractionTrackDetails: vi.fn(),
    tools: ref([]),
    activeToolId: ref(null),
    trackDetailsVisible: ref(false),
    trackDetailsId: ref(null),
    trackDetailsInfo: ref({ activityType: '' }),
    swissMobilityPopup: ref({ visible: false }),
    legendEntries: ref([]),
    legendCollapsed: ref(false),
    mapConfig: ref(null),
    mapServerStatus: ref(null),
    geoDrawingParamDef: ref(null),
    initialLoadDone: ref(true),
    visibleTrackCount: ref(1),
    totalTrackCount: ref(1),
    isOffline: ref(false),
    mediaNavList: ref([...points]),
    mediaNavScope: ref('cluster'),
    mediaSheetVisible: ref(true),
    mediaSheetMediaId: ref<number | null>(44),
    trackSelectionPurpose: ref('details'),
    selectionPopupTracks: ref([]),
    syncToolToRoute: vi.fn(),
    navigateMediaTo: vi.fn((id: number) => {
      controller.mediaSheetMediaId.value = id;
    }),
    closeMediaSheet: vi.fn(() => {
      controller.mediaSheetMediaId.value = null;
      controller.mediaNavList.value = [];
    }),
    focusMediaOnMainMap: vi.fn(),
  };
  vi.mocked(useMainMapController).mockReturnValue(controller as never);
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/', name: 'home', component: EmptyStub }],
  });
  await router.push('/');
  await router.isReady();
  const wrapper = shallowMount(Map2DRenderer, {
    global: {
      plugins: [router],
      stubs: {
        BottomSheet: SheetStub,
        MediaPreview: PreviewStub,
        LocationSearchSheet: EmptyStub,
        MapSettingsPanel: EmptyStub,
        TrackReplayControls: EmptyStub,
        MapLegend: EmptyStub,
        MapDataStatusNotice: EmptyStub,
      },
    },
  });
  await flushPromises();
  await nextTick();
  return { wrapper, controller };
}

describe('photo navigation to the main map', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  it.each([44, 45])(
    'closes the viewer and retains photo %s as the focus target after clearing its collection',
    async (id) => {
      const { wrapper, controller } = await mountMap();
      if (id === 45) await wrapper.getComponent(PreviewStub).get('button').trigger('click');
      await wrapper.getComponent(PreviewStub).findAll('button')[1]!.trigger('click');

      expect(controller.mediaSheetVisible.value).toBe(false);
      expect(controller.closeMediaSheet).toHaveBeenCalledOnce();
      expect(controller.mediaNavList.value).toEqual([]);
      expect(controller.focusMediaOnMainMap).toHaveBeenCalledExactlyOnceWith(photos.find((photo) => photo.id === id));
      expect(wrapper.findComponent(PreviewStub).exists()).toBe(false);
    }
  );

  it('does not focus a photo when the viewer is dismissed normally', async () => {
    const { wrapper, controller } = await mountMap();
    await wrapper.get('[aria-label="Close photos"]').trigger('click');

    expect(controller.mediaSheetVisible.value).toBe(false);
    expect(controller.closeMediaSheet).toHaveBeenCalledOnce();
    expect(controller.focusMediaOnMainMap).not.toHaveBeenCalled();
  });

  it('keeps the viewer open when the selected photo has no valid location', async () => {
    const { wrapper, controller } = await mountMap([{ id: 44, lat: NaN, lng: -8.53928 }]);
    await wrapper.getComponent(PreviewStub).findAll('button')[1]!.trigger('click');

    expect(controller.mediaSheetVisible.value).toBe(true);
    expect(controller.closeMediaSheet).not.toHaveBeenCalled();
    expect(controller.focusMediaOnMainMap).not.toHaveBeenCalled();
  });

  it('opens dispatcher photos with the captured overview and keeps the secondary chooser out of that surface', async () => {
    const { wrapper, controller } = await mountMap();
    const originalBounds = [
      [7.5, 47.4],
      [7.6, 47.5],
    ];
    controller.mediaSheetVisible.value = false;
    await nextTick();
    controller.mapInteraction.value = {
      id: 1,
      context: {
        lngLat: { lng: 7.55, lat: 47.45 },
        point: { x: 75, y: 60 },
        inputKind: 'pointer',
        trackSetRevision: 1,
        mediaScope: 'all',
        eligibility: { photos: true },
      },
      selected: {
        id: 'media-viewport',
        source: 'media',
        kind: 'photo',
        label: 'Map view at tap',
        collectionScope: 'viewport',
        count: 2,
        viewportBounds: originalBounds,
      },
      candidates: [],
      sources: [],
      processing: null,
      presentation: 'target',
    } as never;
    controller.mapInteractionDetailKind.value = 'photo' as never;
    controller.overlayMap.value = {
      on: vi.fn(),
      off: vi.fn(),
      getBounds: () => ({
        getSouthWest: () => ({ lng: -74.1, lat: 40.7 }),
        getNorthEast: () => ({ lng: -73.9, lat: 40.8 }),
      }),
    } as never;
    controller.mediaSheetVisible.value = true;
    await nextTick();

    expect(wrapper.getComponent(PreviewStub).props('overviewBounds')).toEqual(originalBounds);
    expect(wrapper.findComponent({ name: 'MediaCollectionChooser' }).exists()).toBe(false);
    expect(wrapper.findComponent({ name: 'NearbyTrackList' }).exists()).toBe(false);
  });
});
