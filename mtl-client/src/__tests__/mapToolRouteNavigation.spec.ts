import { enableAutoUnmount, flushPromises, shallowMount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { defineComponent, nextTick, ref } from 'vue';
import { createMemoryHistory, createRouter } from 'vue-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import Map2DRenderer from '@/components/map/Map2DRenderer.vue';
import { useMainMapController } from '@/components/map/useMainMapController';

vi.mock('@/components/map/useMainMapController', () => ({ useMainMapController: vi.fn() }));
enableAutoUnmount(afterEach);
afterEach(() => vi.restoreAllMocks());

const toolRoutes = {
  stats: '/stats',
  planner: '/plan',
  filter: '/filter',
  'map-settings': '/map-settings',
  animate: '/animate',
  segments: '/segments',
  health: '/health',
  review: '/review',
  admin: '/admin',
};

const NavigationStub = defineComponent({
  emits: ['select'],
  template: `<div><button v-for="tool in ['planner', 'stats', 'filter', 'map', 'animate', 'measure', 'health', 'review', 'admin']"
    :key="tool" @click="$emit('select', tool)">{{ tool }}</button></div>`,
});
const SheetStub = defineComponent({
  props: { modelValue: Boolean, deferClose: Boolean },
  emits: ['update:modelValue', 'closed', 'close-request'],
  template: `<div v-if="modelValue"><button aria-label="Close details"
    @click="deferClose ? $emit('close-request') : ($emit('update:modelValue', false), $emit('closed'))">Close</button><slot /></div>`,
});
const EmptyStub = defineComponent({ template: '<div />' });

async function settleNavigation() {
  await flushPromises();
  await nextTick();
  await flushPromises();
}

async function mountMap(directLink = false) {
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
    activeToolId: ref<string | null>(null),
    trackDetailsVisible: ref(false),
    trackDetailsId: ref<number | null>(null),
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
    mediaNavList: ref([]),
    mediaNavScope: ref('single'),
    mediaSheetVisible: ref(false),
    mediaSheetMediaId: ref(null),
    trackSelectionPurpose: ref('details'),
    selectionPopupTracks: ref([]),
    onToolSelect: vi.fn((toolId: string) => {
      controller.trackDetailsVisible.value = false;
      controller.trackDetailsId.value = null;
      controller.activeToolId.value = toolId;
    }),
    syncToolToRoute: vi.fn((toolId: string | null) => {
      controller.activeToolId.value = toolId;
      controller.trackDetailsVisible.value = false;
      controller.trackDetailsId.value = null;
    }),
    syncTrackDetailRoute: vi.fn((id: number) => {
      controller.activeToolId.value = null;
      controller.trackDetailsVisible.value = true;
      controller.trackDetailsId.value = id;
    }),
    onTrackDetailsSheetClosed: vi.fn(() => {
      controller.trackDetailsId.value = null;
    }),
  };
  vi.mocked(useMainMapController).mockReturnValue(controller as never);
  const routeComponent = defineComponent({ template: '<div />' });
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: routeComponent },
      { path: '/track/:id', name: 'track-detail', component: routeComponent },
      ...Object.entries(toolRoutes).map(([name, path]) => ({ name, path, component: routeComponent })),
    ],
  });
  await router.push(directLink ? '/track/42' : '/stats');
  await router.isReady();
  const back = vi.spyOn(router, 'back');
  const wrapper = shallowMount(Map2DRenderer, {
    global: {
      plugins: [router],
      stubs: {
        NavigationSheet: NavigationStub,
        BottomSheet: SheetStub,
        LocationSearchSheet: EmptyStub,
        MapSettingsPanel: EmptyStub,
        TrackReplayControls: EmptyStub,
        MapLegend: EmptyStub,
        MapDataStatusNotice: EmptyStub,
      },
    },
  });
  await settleNavigation();
  if (!directLink) await router.push('/track/42');
  window.history.replaceState({ back: directLink ? null : '/stats' }, '');
  await settleNavigation();
  return { wrapper, router, controller, back };
}

describe('tool navigation from track details', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  it.each([
    ['planner', 'planner'],
    ['stats', 'stats'],
    ['filter', 'filter'],
    ['map', 'map-settings'],
    ['animate', 'animate'],
    ['measure', 'segments'],
    ['health', 'health'],
    ['review', 'review'],
    ['admin', 'admin'],
  ])('opens %s on the first selection without returning to the previous tool', async (toolId, routeName) => {
    const { wrapper, router, controller, back } = await mountMap();
    await wrapper
      .findAll('button')
      .find((button) => button.text() === toolId)!
      .trigger('click');
    await settleNavigation();
    expect(router.currentRoute.value.name).toBe(routeName);
    expect(controller.activeToolId.value).toBe(toolId);
    expect(controller.trackDetailsVisible.value).toBe(false);
    expect(back).not.toHaveBeenCalled();
  });

  it('returns to the originating tool when the user closes details', async () => {
    const { wrapper, router, back } = await mountMap();
    await wrapper.get('[aria-label="Close details"]').trigger('click');
    await settleNavigation();
    expect(back).toHaveBeenCalledOnce();
    expect(router.currentRoute.value.name).toBe('stats');
  });

  it('returns to the map when closing a direct detail link without a previous overview', async () => {
    const { wrapper, router, back } = await mountMap(true);
    window.history.replaceState({ back: null }, '');
    await wrapper.get('[aria-label="Close details"]').trigger('click');
    await settleNavigation();
    expect(back).not.toHaveBeenCalled();
    expect(router.currentRoute.value.name).toBe('home');
  });
});
