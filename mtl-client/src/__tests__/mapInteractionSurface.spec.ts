import { enableAutoUnmount, flushPromises, shallowMount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { defineComponent, nextTick, ref, shallowRef } from 'vue';
import { createMemoryHistory, createRouter } from 'vue-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import Map2DRenderer from '@/components/map/Map2DRenderer.vue';
import BottomSheet from '@/components/ui/BottomSheet.vue';
import MapTargetChooser from '@/components/map/MapTargetChooser.vue';
import MapInteractionStatus from '@/components/map/MapInteractionStatus.vue';
import TrackProcessingNotice from '@/components/map/TrackProcessingNotice.vue';
import MapOverlayDetails from '@/components/map/MapOverlayDetails.vue';
import MapPoiDetails from '@/components/map/MapPoiDetails.vue';
import MediaViewerThemeToggle from '@/components/map/MediaViewerThemeToggle.vue';
import MediaViewerViewToggle from '@/components/map/MediaViewerViewToggle.vue';
import { useMainMapController } from '@/components/map/useMainMapController';
import { useMapTools } from '@/components/map/composables/useMapTools';
import { mapTargetKey, type MapTargetCandidate } from '@/components/map/mapInteractionDecision';
import type { MapInteractionState } from '@/components/map/mapInteractionCoordinator';
import * as safeLogging from '@/utils/safeLogging';

vi.mock('@/components/map/useMainMapController', () => ({ useMainMapController: vi.fn() }));
enableAutoUnmount(afterEach);
afterEach(() => vi.restoreAllMocks());

const EmptyStub = defineComponent({ template: '<div />' });
const SheetStub = defineComponent({
  props: {
    modelValue: Boolean,
    title: String,
    icon: String,
    selectedDetent: String,
    initialDetent: String,
    initiallyMaximized: Boolean,
    zIndex: Number,
    fitContent: Boolean,
    desktopWidth: String,
    backLabel: String,
    deferClose: Boolean,
    fitSmallOverflow: Boolean,
    detents: Array,
    nativeFullscreen: Boolean,
    viewportCentered: Boolean,
    shareLayout: Boolean,
    backgroundDetent: [Object, String, Number],
    sheetClass: [Array, String],
  },
  emits: ['update:modelValue', 'closed', 'back', 'close-request'],
  template: `<section v-show="modelValue" :data-test="modelValue ? 'active-sheet' : 'inactive-sheet'">
    <button v-if="backLabel" type="button" :aria-label="backLabel" @click="$emit('back')">Back</button>
    <h2>{{ title }}</h2><slot name="title" /><slot name="header-actions" />
    <div data-test="sheet-pinned"><slot name="pinned" /></div>
    <button type="button" aria-label="Close interaction"
      @click="deferClose ? $emit('close-request') : $emit('update:modelValue', false)">Close</button>
    <slot />
  </section>`,
});
let mountedDetails = 0;
const TrackDetailsProbe = defineComponent({
  props: { gpsTrackId: Number, initialTab: String },
  emits: ['back', 'track-loaded', 'navigate-track', 'start-3d-replay', 'open-media-on-map'],
  setup() {
    return { instance: ++mountedDetails };
  },
  template: `<div data-test="track-content" :data-instance="instance">Track {{ gpsTrackId }}
    <button type="button">Track action</button></div>`,
});
const MediaPreviewProbe = defineComponent({
  props: { mediaId: Number, detailsVisible: Boolean, navTotal: Number, collectionLabel: String },
  emits: ['prev', 'next', 'select', 'request-page', 'update:details-visible', 'open-on-map'],
  template: '<div data-test="photo-content">Photo {{ mediaId }}<button type="button">Photo action</button></div>',
});
const CollectionProbe = defineComponent({
  props: { selection: Object },
  emits: ['choose-primary', 'choose-viewport', 'open-activities'],
  template: `<div data-test="photo-collections">
    <button type="button" @click="$emit('choose-primary')">This location</button>
    <button type="button" @click="$emit('choose-viewport')">Current map view</button>
    <button type="button" @click="$emit('open-activities')">Photos along a track</button>
  </div>`,
});

const track = (trackId: number): MapTargetCandidate => ({
  id: String(trackId),
  kind: 'track',
  source: 'tracks',
  sourceLabel: 'Your tracks',
  label: `Track ${trackId}`,
  trackId,
});
const photo: MapTargetCandidate = {
  id: '4',
  kind: 'photo',
  source: 'media',
  sourceLabel: 'Photos',
  label: 'Photo',
  mediaId: 4,
  count: 1,
};
const overlay: MapTargetCandidate = {
  id: '7',
  kind: 'overlay',
  source: 'ch.swisstopo.swisstlm3d-wanderwege',
  sourceLabel: 'Trail overlay',
  label: 'Trail 7',
  route: { name: 'Trail 7', type: 'Trail', icon: 'bi-map' },
};

function interaction(selected: MapTargetCandidate | null = track(1)): MapInteractionState {
  return {
    id: 12,
    context: {
      lngLat: { lng: 8, lat: 47 },
      point: { x: 120, y: 80 },
      inputKind: 'pointer',
      trackSetRevision: 4,
      mediaScope: 'allIndexed',
      eligibility: { tracks: true, overlay: true },
    },
    candidates: selected ? [selected, overlay] : [],
    sources: [{ id: 'routes', label: 'Trail overlay', kind: 'overlay', status: 'pending' }],
    selected,
    presentation: selected ? 'target' : 'chooser',
    processing: null,
  };
}

async function mountSurface(state: MapInteractionState | null = interaction(), initialPath = '/', realSheet = false) {
  const selectedKind = state?.selected?.kind;
  const initialDetailKind =
    selectedKind === 'photo'
      ? 'photo'
      : selectedKind === 'track-point'
        ? 'point'
        : selectedKind === 'poi' || selectedKind === 'overlay'
          ? 'simple'
          : 'track';
  const controller = {
    overlayMap: shallowRef(undefined),
    mapInteraction: shallowRef<MapInteractionState | null>(state),
    mapInteractionDetailKind: ref<'track' | 'photo' | 'simple' | 'point'>(initialDetailKind),
    mapInteractionDetailLoading: ref(false),
    mapInteractionDetailError: ref<string | null>(null),
    trackPointsPopupContext: ref(null),
    toolDefs: ref([]),
    activeToolId: ref<string | null>(null),
    trackDetailsVisible: ref(state != null || initialPath.startsWith('/track/')),
    trackDetailsId: ref<number | null>(
      state != null ? 1 : initialPath.startsWith('/track/') ? Number(initialPath.split('/')[2]) : null
    ),
    trackDetailsInfo: ref({ activityType: '' }),
    trackDetailsDetents: ref([
      { id: 'compact', height: '300px' },
      { id: 'expanded', height: '92vh' },
    ]),
    trackDetailsInitialDetent: ref('expanded'),
    trackDetailsInitialTab: ref('overview'),
    trackDetailsBackgroundDetent: ref({ id: 'replay-background', height: '76px' }),
    trackReplayActive: ref(false),
    swissMobilityPopup: ref({ visible: true, routes: [{ name: 'Old route' }] }),
    legendEntries: ref([]),
    legendCollapsed: ref(false),
    mapConfig: ref(null),
    mapServerStatus: ref(null),
    geoDrawingParamDef: ref(null),
    initialLoadDone: ref(true),
    visibleTrackCount: ref(1),
    totalTrackCount: ref(1),
    isOffline: ref(false),
    mediaNavList: ref([{ id: 4, lat: 47, lng: 8 }]),
    mediaNavScope: ref('location'),
    mediaSelectionSheetVisible: ref(state != null),
    mediaSheetVisible: ref(state != null),
    mediaSheetMediaId: ref<number | null>(4),
    mediaPendingSelection: ref<Record<string, unknown> | null>(null),
    mediaSelectionTrackOptions: ref([]),
    mediaSelectionTracksLoading: ref(false),
    trackSelectionSheetVisible: ref(state != null),
    trackSelectionPurpose: ref('details'),
    selectionPopupTracks: ref([]),
    selectionPopupProcessing: ref(null),
    mediaNavTotal: ref(1),
    mediaCurrentIndex: ref(0),
    mediaNavOffset: ref(0),
    mediaNavLoading: ref(false),
    mediaNavigationIds: ref([4]),
    syncToolToRoute: vi.fn(),
    syncTrackDetailRoute: vi.fn(),
    endMapInteraction: vi.fn(),
    selectMapTarget: vi.fn(),
    showMapTargetChooser: vi.fn(),
    backFromMapTargetChooser: vi.fn(),
    chooseMediaCollection: vi.fn(),
    openMediaSelectionActivities: vi.fn(),
    closeMediaSheet: vi.fn(),
    focusMediaOnMainMap: vi.fn(),
    onTrackDetailsSheetClosed: vi.fn(),
  };
  const tools = useMapTools();
  const statisticsTool = { open: vi.fn(), restoreNavigationState: vi.fn() };
  const toolRuntime = {
    get activeToolId() {
      return controller.activeToolId.value;
    },
    set activeToolId(value: string | null) {
      controller.activeToolId.value = value;
    },
    get trackDetailsId() {
      return controller.trackDetailsId.value;
    },
    set trackDetailsId(value: number | null) {
      controller.trackDetailsId.value = value;
    },
    get trackDetailsInfo() {
      return controller.trackDetailsInfo.value;
    },
    set trackDetailsInfo(value: { activityType: string }) {
      controller.trackDetailsInfo.value = value;
    },
    get trackDetailsInitialTab() {
      return controller.trackDetailsInitialTab.value;
    },
    set trackDetailsInitialTab(value: string) {
      controller.trackDetailsInitialTab.value = value;
    },
    trackDetailsReturnTarget: null as { toolId: string; state: unknown } | null,
    trackReplayActive: false,
    deselectTrack: vi.fn(),
    $refs: { statistics: statisticsTool, filterTool: { open: vi.fn() } },
    $nextTick: (callback: () => void) => nextTick(callback),
    closeAllToolsExcept: vi.fn(),
    closeTransientOverlaysForToolSwitch() {
      controller.trackDetailsVisible.value = false;
      controller.trackDetailsId.value = null;
    },
    restoreToolNavigation(toolId: string) {
      tools.restoreToolNavigation.call(toolRuntime as never, toolId);
    },
  };
  controller.syncToolToRoute.mockImplementation((toolId: string | null) => {
    tools.syncToolToRoute.call(toolRuntime as never, toolId);
  });
  controller.onTrackDetailsSheetClosed.mockImplementation(() =>
    tools.onTrackDetailsSheetClosed.call(toolRuntime as never)
  );
  controller.syncTrackDetailRoute.mockImplementation((id: number) => {
    controller.activeToolId.value = null;
    controller.trackDetailsVisible.value = true;
    controller.trackDetailsId.value = id;
  });
  controller.endMapInteraction.mockImplementation(() => {
    controller.mapInteraction.value = null;
    controller.trackDetailsVisible.value = false;
    controller.trackDetailsId.value = null;
    controller.mediaSelectionSheetVisible.value = false;
    controller.mediaSheetVisible.value = false;
    controller.trackSelectionSheetVisible.value = false;
    controller.mediaPendingSelection.value = null;
  });
  controller.showMapTargetChooser.mockImplementation(() => {
    controller.mapInteraction.value = { ...controller.mapInteraction.value!, presentation: 'chooser' };
  });
  controller.backFromMapTargetChooser.mockImplementation(() => {
    controller.mapInteraction.value = { ...controller.mapInteraction.value!, presentation: 'target' };
  });
  controller.selectMapTarget.mockImplementation((key: string) => {
    const selected = controller.mapInteraction.value!.candidates.find((candidate) => mapTargetKey(candidate) === key)!;
    controller.mapInteraction.value = { ...controller.mapInteraction.value!, selected, presentation: 'target' };
    if (selected.kind === 'track') {
      controller.trackDetailsId.value = selected.trackId;
      controller.mapInteractionDetailKind.value = 'track';
      controller.trackDetailsVisible.value = true;
    } else {
      controller.trackDetailsVisible.value = false;
      controller.trackDetailsId.value = null;
      controller.mapInteractionDetailKind.value =
        selected.kind === 'photo' ? 'photo' : selected.kind === 'track-point' ? 'point' : 'simple';
    }
  });
  vi.mocked(useMainMapController).mockReturnValue(controller as never);
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: EmptyStub },
      { path: '/track/:id', name: 'track-detail', component: EmptyStub },
      { path: '/stats', name: 'stats', component: EmptyStub },
      { path: '/filter', name: 'filter', component: EmptyStub },
    ],
  });
  await router.push(initialPath);
  await router.isReady();
  const wrapper = shallowMount(Map2DRenderer, {
    attachTo: document.body,
    global: {
      plugins: [router],
      stubs: {
        teleport: false,
        BottomSheet: realSheet ? false : SheetStub,
        TrackDetails: TrackDetailsProbe,
        TrackChoiceRow: false,
        TrackShapePreview: true,
        MediaPreview: MediaPreviewProbe,
        MediaCollectionChooser: CollectionProbe,
        MapTargetChooser,
        MapInteractionStatus,
        TrackProcessingNotice,
        MapOverlayDetails,
        MapPoiDetails,
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
  return { wrapper, controller, router, toolRuntime, statisticsTool };
}

describe('one map exploration surface', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
    mountedDetails = 0;
  });

  describe('GPS Close with the real sheet', () => {
    const originalWidth = window.innerWidth;
    const originalHeight = window.innerHeight;

    beforeEach(() => {
      vi.stubGlobal(
        'ResizeObserver',
        class {
          observe() {}
          disconnect() {}
        }
      );
      Object.defineProperty(window, 'innerHeight', { configurable: true, value: 900 });
    });

    afterEach(() => {
      vi.unstubAllGlobals();
      Object.defineProperty(window, 'innerWidth', { configurable: true, value: originalWidth });
      Object.defineProperty(window, 'innerHeight', { configurable: true, value: originalHeight });
    });

    it.each([
      ['rejected', 1440],
      ['thrown', 1440],
      ['rejected', 390],
      ['thrown', 390],
    ])('preserves GPS layout after a %s Close at width %i and permits retry', async (failure, width) => {
      Object.defineProperty(window, 'innerWidth', { configurable: true, value: width });
      vi.spyOn(safeLogging, 'warnRequestFailure').mockImplementation(() => undefined);
      const { wrapper, controller, router } = await mountSurface(interaction(), '/', true);
      await flushPromises();
      const foreground = document
        .querySelector<HTMLElement>('[data-test="track-content"]')!
        .closest<HTMLElement>('.sheet')!;
      const content = foreground.querySelector('[data-test="track-content"]');
      const chooser = wrapper.getComponent(MapTargetChooser).element;
      const dispatcher = chooser.closest<HTMLElement>('.sheet')!;
      const dispatcherHeight = dispatcher.style.height;
      if (width > 768) {
        wrapper
          .findAllComponents(BottomSheet)
          .find((sheet) => sheet.props('backLabel') === 'Back to results')!
          .vm.resizeTo(610);
        await nextTick();
      }
      const height = foreground.style.height;
      expect(height).toBe(width > 768 ? '610px' : '100dvh');
      const removeGuard = router.beforeEach((to) => {
        if (to.name !== 'home') return;
        if (failure === 'thrown') throw new Error('Close guard failed');
        return false;
      });
      foreground.querySelector<HTMLButtonElement>('.sheet-close-btn')!.click();
      await flushPromises();
      expect(router.currentRoute.value.fullPath).toBe('/track/1');
      expect(controller.mapInteraction.value?.presentation).toBe('target');
      expect(foreground.style.height).toBe(height);
      expect(foreground.classList.contains('sheet--open')).toBe(true);
      expect(foreground.querySelector('[data-test="track-content"]')).toBe(content);
      expect(dispatcher.classList.contains('sheet--backgrounded')).toBe(true);
      expect(dispatcher.style.height).toBe(dispatcherHeight);
      removeGuard();
      foreground.querySelector<HTMLButtonElement>('.sheet-close-btn')!.click();
      await flushPromises();
      expect(router.currentRoute.value.fullPath).toBe('/');
      expect(controller.mapInteraction.value?.presentation).toBe('chooser');
      expect(foreground.classList.contains('sheet--open')).toBe(false);
      expect(wrapper.getComponent(MapTargetChooser).element).toBe(chooser);
      expect(dispatcher.classList.contains('sheet--backgrounded')).toBe(false);
      expect(dispatcher.style.height).toBe(dispatcherHeight);
    });

    it('keeps GPS visible while Close navigation is pending', async () => {
      Object.defineProperty(window, 'innerWidth', { configurable: true, value: 1440 });
      const { controller, router } = await mountSurface(interaction(), '/', true);
      await flushPromises();
      const foreground = document
        .querySelector<HTMLElement>('[data-test="track-content"]')!
        .closest<HTMLElement>('.sheet')!;
      const height = foreground.style.height;
      let release!: (allowed: boolean) => void;
      router.beforeEach((to) =>
        to.name === 'home'
          ? new Promise<boolean>((resolve) => {
              release = resolve;
            })
          : undefined
      );
      foreground.querySelector<HTMLButtonElement>('.sheet-close-btn')!.click();
      await flushPromises();
      expect(router.currentRoute.value.fullPath).toBe('/track/1');
      expect(foreground.style.height).toBe(height);
      release(true);
      await flushPromises();
      expect(router.currentRoute.value.fullPath).toBe('/');
      expect(controller.mapInteraction.value?.presentation).toBe('chooser');
      expect(foreground.classList.contains('sheet--open')).toBe(false);
    });

    it.each(['rejected', 'thrown'])(
      'retains standalone GPS after a %s Close and returns to Statistics on retry',
      async (failure) => {
        Object.defineProperty(window, 'innerWidth', { configurable: true, value: 1440 });
        vi.spyOn(safeLogging, 'warnRequestFailure').mockImplementation(() => undefined);
        const { controller, router, toolRuntime, statisticsTool } = await mountSurface(null, '/stats', true);
        const navigation = { tab: 'tracks', page: 3 };
        toolRuntime.trackDetailsReturnTarget = { toolId: 'stats', state: navigation };
        controller.trackDetailsId.value = 42;
        controller.trackDetailsVisible.value = true;
        await flushPromises();
        const foreground = document
          .querySelector<HTMLElement>('[data-test="track-content"]')!
          .closest<HTMLElement>('.sheet')!;
        const height = foreground.style.height;
        expect(height).toBe('828px');
        const removeGuard = router.beforeEach((to) => {
          if (to.name !== 'stats') return;
          if (failure === 'thrown') throw new Error('Close guard failed');
          return false;
        });
        foreground.querySelector<HTMLButtonElement>('.sheet-close-btn')!.click();
        await flushPromises();
        expect(router.currentRoute.value.fullPath).toBe('/track/42');
        expect(controller.trackDetailsVisible.value).toBe(true);
        expect(foreground.style.height).toBe(height);
        expect(toolRuntime.trackDetailsReturnTarget).toEqual({ toolId: 'stats', state: navigation });
        removeGuard();
        foreground.querySelector<HTMLButtonElement>('.sheet-close-btn')!.click();
        await flushPromises();
        expect(router.currentRoute.value.fullPath).toBe('/stats');
        expect(controller.trackDetailsVisible.value).toBe(false);
        expect(controller.trackDetailsId.value).toBeNull();
        expect(statisticsTool.restoreNavigationState).toHaveBeenCalledExactlyOnceWith(navigation);
      }
    );
  });

  it('gives selected GPS details a URL and preserves the same chooser and detail instances through browser history', async () => {
    const state = interaction(null);
    state.candidates = [track(1), track(2), photo];
    const { wrapper, controller, router } = await mountSurface(state);
    expect(router.currentRoute.value.fullPath).toBe('/');
    const chooser = wrapper.getComponent(MapTargetChooser).element;
    await wrapper.get('[data-category-key="track"][data-test="map-category-tab"]').trigger('click');
    await wrapper.get(`[data-target-key='${mapTargetKey(track(1))}']`).trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/track/1');
    const detail = wrapper.get('[data-test="track-content"]').element;
    controller.mapInteraction.value = {
      ...controller.mapInteraction.value!,
      candidates: [...state.candidates, overlay],
      sources: [{ ...state.sources[0], status: 'complete' }],
    };
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/track/1');
    router.back();
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/');
    expect(controller.mapInteraction.value?.presentation).toBe('chooser');
    expect(wrapper.getComponent(MapTargetChooser).element).toBe(chooser);
    expect(wrapper.get('[data-category-key="track"][data-test="map-category-tab"]').attributes('aria-pressed')).toBe(
      'true'
    );
    expect(wrapper.get('[data-test="track-content"]').element).toBe(detail);
    expect(wrapper.get('[data-test="track-content"]').isVisible()).toBe(false);
    router.forward();
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/track/1');
    expect(controller.mapInteraction.value?.presentation).toBe('target');
    expect(wrapper.get('[data-test="track-content"]').element).toBe(detail);
    expect(wrapper.get('[data-test="track-content"]').isVisible()).toBe(true);
    expect(controller.selectMapTarget).toHaveBeenCalledOnce();
    await wrapper.get('[aria-label="Back to results"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/');
    expect(controller.mapInteraction.value?.presentation).toBe('chooser');
    expect(mountedDetails).toBe(1);
  });

  it('pushes GPS routes only for full details, including point parents and photos along a track', async () => {
    const point: MapTargetCandidate = { ...track(1), kind: 'track-point', trackId: 1, pointIndex: 4 };
    const state = interaction(point);
    const { controller, router } = await mountSurface(state);
    expect(router.currentRoute.value.fullPath).toBe('/');
    controller.trackDetailsId.value = 1;
    controller.trackDetailsVisible.value = true;
    controller.mapInteractionDetailKind.value = 'track';
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/track/1');
    controller.showMapTargetChooser();
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/');
    controller.mapInteraction.value = { ...state, selected: photo, presentation: 'target' };
    controller.trackDetailsId.value = 2;
    controller.mapInteractionDetailKind.value = 'track';
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/track/2');
  });

  it('drops the GPS URL when choosing a non-GPS result and does not reopen the previous detail view', async () => {
    const state = interaction();
    state.candidates.push(photo);
    const { wrapper, controller, router } = await mountSurface(state);
    expect(router.currentRoute.value.fullPath).toBe('/track/1');
    await wrapper.get('[aria-label="Back to results"]').trigger('click');
    await flushPromises();
    await wrapper.get(`[data-target-key='${mapTargetKey(photo)}']`).trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/');
    expect(controller.mapInteraction.value?.selected).toEqual(photo);
    expect(controller.mapInteraction.value?.presentation).toBe('target');
  });

  it('returns to the preserved chooser if opening a GPS URL is rejected', async () => {
    const state = interaction(null);
    state.candidates = [track(1)];
    const { wrapper, controller, router } = await mountSurface(state);
    router.beforeEach((to) => (to.name === 'track-detail' ? false : undefined));
    await wrapper.get(`[data-target-key='${mapTargetKey(track(1))}']`).trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/');
    expect(controller.mapInteraction.value?.presentation).toBe('chooser');
    expect(controller.mapInteraction.value?.selected).toEqual(track(1));
    expect(wrapper.get('[data-test="track-content"]').isVisible()).toBe(false);
  });

  it('preserves the base history entry when a new click replaces the current GPS detail URL', async () => {
    const { wrapper, controller, router } = await mountSurface();
    controller.mapInteraction.value = { ...interaction(track(2)), id: 13 };
    controller.trackDetailsId.value = 2;
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/track/2');
    await wrapper.get('[aria-label="Back to results"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/');
    expect(controller.mapInteraction.value?.id).toBe(13);
    expect(controller.mapInteraction.value?.presentation).toBe('chooser');
    router.forward();
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/track/2');
    expect(controller.mapInteraction.value?.presentation).toBe('target');
  });

  it('does not let a rejected old click to the same track roll back its newer selection', async () => {
    const state = interaction(null);
    state.candidates = [track(1)];
    const { wrapper, controller, router } = await mountSurface(state);
    const releases: Array<(value: boolean) => void> = [];
    router.beforeEach((to) =>
      to.name === 'track-detail' ? new Promise<boolean>((resolve) => releases.push(resolve)) : undefined
    );
    await wrapper.get(`[data-target-key='${mapTargetKey(track(1))}']`).trigger('click');
    await flushPromises();
    controller.endMapInteraction(true);
    controller.mapInteraction.value = { ...state, id: 13 };
    controller.selectMapTarget(mapTargetKey(track(1)));
    await flushPromises();
    expect(releases).toHaveLength(2);
    releases[0](false);
    await flushPromises();
    expect(controller.mapInteraction.value?.presentation).toBe('target');
    expect(controller.mapInteraction.value?.id).toBe(13);
    releases[1](true);
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/track/1');
    expect(controller.mapInteraction.value?.presentation).toBe('target');
  });

  it('keeps URL and selected details aligned when a browser Back navigation is rejected', async () => {
    const { wrapper, controller, router } = await mountSurface();
    router.beforeEach((to) => (to.name === 'home' ? false : undefined));
    router.back();
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/track/1');
    expect(controller.mapInteraction.value?.presentation).toBe('target');
    expect(wrapper.get('[data-test="track-content"]').isVisible()).toBe(true);
  });

  it('opens a different track URL through standalone routing rather than showing stale dispatcher details', async () => {
    const { controller, router } = await mountSurface();
    await router.push('/track/42');
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/track/42');
    expect(controller.mapInteraction.value).toBeNull();
    expect(controller.syncTrackDetailRoute).toHaveBeenCalledWith(42);
    expect(controller.trackDetailsId.value).toBe(42);
  });

  it('opens a direct track link with a generic Back fallback to the map', async () => {
    window.history.replaceState({ back: null }, '');
    const { wrapper, controller, router } = await mountSurface(null, '/track/42');
    expect(controller.syncTrackDetailRoute).toHaveBeenCalledWith(42);
    expect(wrapper.getComponent(TrackDetailsProbe).props('gpsTrackId')).toBe(42);
    await wrapper.get('[aria-label="Back"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.name).toBe('home');
    expect(controller.trackDetailsVisible.value).toBe(false);
  });

  it.each(['rejected', 'thrown'])(
    'keeps standalone GPS visible after a %s header Back and restores the tool on retry',
    async (kind) => {
      vi.spyOn(safeLogging, 'warnRequestFailure').mockImplementation(() => undefined);
      const { wrapper, controller, router, toolRuntime, statisticsTool } = await mountSurface(null, '/stats');
      const savedNavigation = { tab: 'activities', page: 3 };
      toolRuntime.trackDetailsReturnTarget = { toolId: 'stats', state: savedNavigation };
      controller.trackDetailsId.value = 42;
      controller.trackDetailsVisible.value = true;
      await flushPromises();
      const removeGuard = router.beforeEach((to) => {
        if (to.name !== 'stats') return;
        if (kind === 'thrown') throw new Error('Back guard failed');
        return false;
      });
      await wrapper.get('[aria-label="Back"]').trigger('click');
      await flushPromises();
      expect(router.currentRoute.value.fullPath).toBe('/track/42');
      expect(controller.trackDetailsVisible.value).toBe(true);
      expect(controller.trackDetailsId.value).toBe(42);
      expect(wrapper.get('[data-test="track-content"]').isVisible()).toBe(true);
      expect(toolRuntime.trackDetailsReturnTarget).toEqual({ toolId: 'stats', state: savedNavigation });
      removeGuard();
      await wrapper.get('[aria-label="Back"]').trigger('click');
      await flushPromises();
      expect(router.currentRoute.value.fullPath).toBe('/stats');
      expect(controller.trackDetailsVisible.value).toBe(false);
      expect(statisticsTool.restoreNavigationState).toHaveBeenCalledExactlyOnceWith(savedNavigation);
    }
  );

  it('keeps a direct-link load error Back usable after its map navigation throws', async () => {
    vi.spyOn(safeLogging, 'warnRequestFailure').mockImplementation(() => undefined);
    window.history.replaceState({ back: null }, '');
    const { wrapper, controller, router } = await mountSurface(null, '/track/42');
    const removeGuard = router.beforeEach((to) => {
      if (to.name === 'home') throw new Error('Back guard failed');
    });
    wrapper.getComponent(TrackDetailsProbe).vm.$emit('back');
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/track/42');
    expect(controller.trackDetailsVisible.value).toBe(true);
    removeGuard();
    wrapper.getComponent(TrackDetailsProbe).vm.$emit('back');
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/');
    expect(controller.trackDetailsVisible.value).toBe(false);
  });

  it('cleans the URL when the interaction is closed and restores a direct link on browser Forward', async () => {
    const { wrapper, controller, router } = await mountSurface();
    await wrapper.get('[aria-label="Close interaction"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/');
    expect(controller.mapInteraction.value).toBeNull();
    router.forward();
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/track/1');
    expect(controller.syncTrackDetailRoute).toHaveBeenCalledWith(1);
    expect(controller.mapInteraction.value).toBeNull();
  });

  it.each(['/', '/stats'])(
    'opens GPS detail media on the main map from %s without retaining its old click surface',
    async (origin) => {
      const { wrapper, controller, router } = await mountSurface(interaction(), origin);
      const target = { id: 77, lat: 46.31, lng: 8.42 };
      expect(router.currentRoute.value.fullPath).toBe('/track/1');
      const details = wrapper.getComponent(TrackDetailsProbe);
      details.vm.$emit('open-media-on-map', target);
      await flushPromises();
      expect(controller.mapInteraction.value).toBeNull();
      expect(controller.trackDetailsVisible.value).toBe(false);
      expect(controller.trackDetailsId.value).toBeNull();
      expect(router.currentRoute.value.fullPath).toBe('/');
      expect(controller.activeToolId.value).toBeNull();
      expect(controller.focusMediaOnMainMap).toHaveBeenCalledExactlyOnceWith(target);
      expect(controller.endMapInteraction.mock.invocationCallOrder[0]).toBeLessThan(
        controller.focusMediaOnMainMap.mock.invocationCallOrder[0]
      );
      expect(wrapper.findAll('[data-test="active-sheet"]')).toHaveLength(0);
      expect(wrapper.findComponent(MapTargetChooser).exists()).toBe(false);
    }
  );

  it('closes standalone GPS details on browser Back to the map and reopens them on Forward', async () => {
    const { wrapper, controller, router } = await mountSurface();
    await wrapper.get('[aria-label="Close interaction"]').trigger('click');
    await flushPromises();
    router.forward();
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/track/1');
    expect(controller.mapInteraction.value).toBeNull();
    expect(controller.trackDetailsVisible.value).toBe(true);
    router.back();
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/');
    expect(controller.trackDetailsVisible.value).toBe(false);
    expect(controller.trackDetailsId.value).toBeNull();
    expect(controller.onTrackDetailsSheetClosed).toHaveBeenCalledOnce();
    expect(wrapper.findAll('[data-test="active-sheet"]')).toHaveLength(0);
    router.forward();
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/track/1');
    expect(controller.trackDetailsVisible.value).toBe(true);
    expect(wrapper.getComponent(TrackDetailsProbe).props('gpsTrackId')).toBe(1);
  });

  it('closes unowned GPS details while restoring their originating tool navigation state', async () => {
    const { wrapper, controller, router, toolRuntime, statisticsTool } = await mountSurface(null, '/stats');
    const savedNavigation = { tab: 'activities', page: 3 };
    toolRuntime.trackDetailsReturnTarget = { toolId: 'stats', state: savedNavigation };
    controller.trackDetailsId.value = 42;
    controller.trackDetailsVisible.value = true;
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/track/42');
    router.back();
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/stats');
    expect(controller.trackDetailsVisible.value).toBe(false);
    expect(controller.trackDetailsId.value).toBeNull();
    expect(controller.activeToolId.value).toBe('stats');
    expect(statisticsTool.restoreNavigationState).toHaveBeenCalledExactlyOnceWith(savedNavigation);
    expect(toolRuntime.trackDetailsReturnTarget).toBeNull();
    expect(wrapper.findComponent(TrackDetailsProbe).exists()).toBe(false);
  });

  it('cancels a pending GPS URL when Back is pressed before navigation completes', async () => {
    const state = interaction(null);
    state.candidates = [track(1), photo];
    const { wrapper, controller, router } = await mountSurface(state);
    let release!: () => void;
    router.beforeEach((to) =>
      to.name === 'track-detail'
        ? new Promise<void>((resolve) => {
            release = resolve;
          })
        : undefined
    );
    await wrapper.get(`[data-target-key='${mapTargetKey(track(1))}']`).trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/');
    await wrapper.get('[aria-label="Back to results"]').trigger('click');
    await flushPromises();
    release();
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/');
    expect(controller.mapInteraction.value?.presentation).toBe('chooser');
    expect(controller.syncTrackDetailRoute).not.toHaveBeenCalled();
  });

  it('keeps a new map click authoritative while a previous track URL is pending', async () => {
    const state = interaction(null);
    state.candidates = [track(1)];
    const { wrapper, controller, router } = await mountSurface(state);
    let release!: () => void;
    router.beforeEach((to) =>
      to.name === 'track-detail'
        ? new Promise<void>((resolve) => {
            release = resolve;
          })
        : undefined
    );
    await wrapper.get(`[data-target-key='${mapTargetKey(track(1))}']`).trigger('click');
    await flushPromises();
    controller.endMapInteraction(true);
    const next = { ...interaction(null), id: 13, candidates: [photo] };
    controller.mapInteraction.value = next;
    await flushPromises();
    release();
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/');
    expect(controller.mapInteraction.value?.id).toBe(13);
    expect(controller.mapInteraction.value?.selected).toBeNull();
  });

  it('keeps the dispatcher beneath one foreground GPS sheet despite legacy presentation flags', async () => {
    const { wrapper } = await mountSurface();
    expect(wrapper.findAll('[data-test="active-sheet"]')).toHaveLength(2);
    const foreground = wrapper
      .findAllComponents(SheetStub)
      .find((sheet) => sheet.props('backLabel') === 'Back to results')!;
    expect(foreground.props()).toMatchObject({ initialDetent: 'expanded', desktopWidth: 'standard', zIndex: 5350 });
    expect(wrapper.getComponent(MapTargetChooser).attributes('inert')).toBeDefined();
    expect(wrapper.findAllComponents(TrackDetailsProbe)).toHaveLength(1);
    expect(wrapper.findComponent(MediaPreviewProbe).exists()).toBe(false);
    expect(wrapper.findComponent(CollectionProbe).exists()).toBe(false);
    expect(wrapper.text()).not.toContain('Old route');
  });

  it('closes foreground GPS details and reveals the same retained chooser', async () => {
    const { wrapper, controller } = await mountSurface();
    expect(wrapper.getComponent(MapTargetChooser).attributes('inert')).toBeDefined();
    expect(wrapper.get('[data-test="map-category-all"]').isVisible()).toBe(true);
    expect(wrapper.find('.map-interaction-navigation-host').exists()).toBe(false);
    expect(wrapper.getComponent(MapTargetChooser).props()).not.toHaveProperty('navigationOnly');
    const chooser = wrapper.getComponent(MapTargetChooser).element;
    await wrapper.get('[aria-label="Back to results"]').trigger('click');
    await flushPromises();
    expect(controller.showMapTargetChooser).toHaveBeenCalledOnce();
    expect(controller.mapInteraction.value?.selected).toEqual(track(1));
    expect(wrapper.getComponent(MapTargetChooser).element).toBe(chooser);
    expect(wrapper.get('[data-test="map-category-all"]').attributes('aria-pressed')).toBe('true');
    expect(wrapper.get('.map-target-navigation').isVisible()).toBe(true);
    expect(wrapper.find('[aria-label="Back to results"]').isVisible()).toBe(false);
    expect(wrapper.findAll('[data-test="active-sheet"]')).toHaveLength(1);
  });

  it.each([true, false])('shares GPS media layout only in map exploration (map: %s)', async (fromMap) => {
    const { wrapper, controller } = await mountSurface(fromMap ? interaction() : null, fromMap ? '/' : '/track/42');
    const gpsSheet = wrapper
      .findAllComponents(SheetStub)
      .find((sheet) => sheet.findComponent(TrackDetailsProbe).exists())!;
    expect(gpsSheet.props('shareLayout')).toBe(fromMap);
    expect(gpsSheet.props('backgroundDetent')).toBeUndefined();
    controller.trackReplayActive.value = true;
    await nextTick();
    expect(gpsSheet.props('backgroundDetent')).toEqual({ id: 'replay-background', height: '76px' });
    controller.trackReplayActive.value = false;
    await nextTick();
    expect(gpsSheet.props('backgroundDetent')).toBeUndefined();
  });

  it('lets the caller handle a track detail Back event with the same destination as the sheet header', async () => {
    const { wrapper, controller } = await mountSurface();
    wrapper.getComponent(TrackDetailsProbe).vm.$emit('back');
    await flushPromises();
    expect(controller.showMapTargetChooser).toHaveBeenCalledOnce();
    expect(controller.endMapInteraction).not.toHaveBeenCalled();
    expect(wrapper.get('[data-test="map-category-all"]').isVisible()).toBe(true);
    expect(wrapper.get('[data-test="track-content"]').isVisible()).toBe(false);
  });

  it('returns from selected details to a remembered category page through the central dispatcher', async () => {
    const state = interaction();
    state.candidates.push(...Array.from({ length: 25 }, (_, index) => track(index + 2)));
    const { wrapper, controller } = await mountSurface(state);
    const chooser = wrapper.getComponent(MapTargetChooser).element;
    await wrapper.get('[data-test="map-category-tab"][data-category-key="track"]').trigger('click');
    await wrapper.get('[aria-label="Next page"]').trigger('click');
    await wrapper.get(`[data-target-key='${mapTargetKey(track(25))}']`).trigger('click');
    expect(controller.mapInteraction.value?.selected).toEqual(track(25));
    expect(wrapper.get('[data-test="track-content"]').isVisible()).toBe(true);
    expect(wrapper.getComponent(MapTargetChooser).attributes('inert')).toBeDefined();
    await wrapper.get('[aria-label="Back to results"]').trigger('click');
    await flushPromises();
    await wrapper.get('[data-test="map-category-tab"][data-category-key="track"]').trigger('click');
    expect(wrapper.get('[data-test="map-category-page-range"]').text()).toBe('21–26 of 26');
    expect(wrapper.get(`[data-target-key='${mapTargetKey(track(25))}']`).attributes('aria-pressed')).toBe('true');
    expect(wrapper.getComponent(MapTargetChooser).element).toBe(chooser);
    expect(wrapper.findAll('[data-test="active-sheet"]')).toHaveLength(1);
  });

  it('closes only the foreground GPS sheet and reveals retained results', async () => {
    const { wrapper, controller, router } = await mountSurface();
    const chooser = wrapper.getComponent(MapTargetChooser);
    const foreground = wrapper
      .findAllComponents(SheetStub)
      .find((sheet) => sheet.props('backLabel') === 'Back to results')!;
    const dispatcher = wrapper.getComponent(SheetStub);
    const detents = dispatcher.props('detents');
    await foreground.get('[aria-label="Close interaction"]').trigger('click');
    await flushPromises();
    expect(controller.showMapTargetChooser).toHaveBeenCalledOnce();
    expect(controller.endMapInteraction).not.toHaveBeenCalled();
    expect(router.currentRoute.value.fullPath).toBe('/');
    expect(wrapper.getComponent(MapTargetChooser).element).toBe(chooser.element);
    expect(dispatcher.props('detents')).toBe(detents);
    expect(foreground.isVisible()).toBe(false);
    expect(wrapper.findAll('[data-test="active-sheet"]')).toHaveLength(1);
  });

  it('shows archive actions only in selected track content, not the shared chooser or trail details', async () => {
    const { wrapper, controller } = await mountSurface();
    expect(wrapper.findComponent({ name: 'TrackDetailActions' }).exists()).toBe(true);
    await wrapper.get('[aria-label="Back to results"]').trigger('click');
    await flushPromises();
    expect(wrapper.get('h2').text()).toBe('Choose what to see here');
    expect(wrapper.findComponent({ name: 'TrackDetailActions' }).exists()).toBe(false);
    controller.mapInteraction.value = {
      ...controller.mapInteraction.value!,
      selected: overlay,
      presentation: 'target',
    };
    controller.mapInteractionDetailKind.value = 'simple';
    await nextTick();
    expect(wrapper.get('h2').text()).toBe('Route details');
    expect(wrapper.getComponent(MapOverlayDetails).get('h2').text()).toBe('Trail 7');
    expect(wrapper.findComponent({ name: 'TrackDetailActions' }).exists()).toBe(false);
  });

  it('opens a single public route in its own detail layout within the existing sheet', async () => {
    const route: MapTargetCandidate = {
      ...overlay,
      source: 'waymarked:hiking',
      sourceLabel: 'Waymarked Trails · Hiking',
      label: 'Camino Portuguese',
      route: {
        name: 'Camino Portuguese',
        type: 'Hiking',
        icon: 'bi bi-signpost-2',
        network: 'Local',
        detailsUrl: 'https://hiking.waymarkedtrails.org/#route?id=7',
      },
    };
    const state = interaction(route);
    state.candidates = [route];
    state.sources = [];
    const { wrapper, controller } = await mountSurface(state);
    controller.mapInteractionDetailKind.value = 'simple';
    await nextTick();
    const panel = wrapper.getComponent(MapOverlayDetails);
    expect(wrapper.findAll('[data-test="active-sheet"]')).toHaveLength(1);
    expect(wrapper.getComponent(SheetStub).props()).toMatchObject({
      title: 'Route details',
      icon: 'bi bi-signpost-2',
      fitContent: false,
      desktopWidth: 'standard',
    });
    expect(panel.get('h2').text()).toBe('Camino Portuguese');
    expect(panel.get('a').attributes('href')).toBe('https://hiking.waymarkedtrails.org/#route?id=7');
    expect(wrapper.get('.map-target-navigation').isVisible()).toBe(false);
    expect(wrapper.get('[aria-label="Back to results"]').isVisible()).toBe(true);
    expect(wrapper.findComponent({ name: 'TrackDetailActions' }).exists()).toBe(false);
    expect(wrapper.findComponent(MapPoiDetails).exists()).toBe(false);

    controller.mapInteraction.value = { ...state, candidates: [route, { ...route, id: '8', label: 'Coastal path' }] };
    await nextTick();
    expect(wrapper.get('.map-target-navigation').isVisible()).toBe(false);
    const retainedPanel = panel.element;
    await wrapper.get('[aria-label="Back to results"]').trigger('click');
    await flushPromises();
    expect(panel.isVisible()).toBe(false);
    expect(wrapper.getComponent(SheetStub).props('desktopWidth')).toBe('standard');
    expect(wrapper.find('[data-test="map-target-back"]').exists()).toBe(false);
    await wrapper.get('[data-test="map-target-row"][aria-pressed="true"]').trigger('click');
    expect(wrapper.getComponent(MapOverlayDetails).element).toBe(retainedPanel);
    expect(panel.isVisible()).toBe(true);
    expect(wrapper.findAll('[data-test="active-sheet"]')).toHaveLength(1);
  });

  it('keeps selected details mounted through Back to results and row reopening, then restores useful keyboard focus', async () => {
    const { wrapper, controller } = await mountSurface();
    const selectedContent = wrapper.get('[data-test="track-content"]').element;
    const back = wrapper.get('[aria-label="Back to results"]');
    (back.element as HTMLButtonElement).focus();
    await back.trigger('click');
    await flushPromises();
    expect(controller.showMapTargetChooser).toHaveBeenCalledOnce();
    expect(wrapper.get('h2').text()).toBe('Choose what to see here');
    expect(wrapper.get('[data-test="track-content"]').element).toBe(selectedContent);
    expect(wrapper.get('[data-test="track-content"]').isVisible()).toBe(false);
    expect(wrapper.findAll('[data-test="active-sheet"]')).toHaveLength(1);
    expect(document.activeElement).toBe(wrapper.get('[data-test="map-category-all"]').element);
    expect(wrapper.getComponent(SheetStub).props('selectedDetent')).toBe('choice');
    expect(wrapper.getComponent(SheetStub).props('fitContent')).toBe(false);
    expect(wrapper.getComponent(SheetStub).props('nativeFullscreen')).toBe(false);
    expect(wrapper.getComponent(SheetStub).props('viewportCentered')).toBe(false);
    expect(wrapper.getComponent(SheetStub).props('sheetClass')).not.toContain('media-viewer-sheet');
    expect(wrapper.find('[data-test="map-target-back"]').exists()).toBe(false);
    await wrapper.get('[data-test="map-target-row"][aria-pressed="true"]').trigger('click');
    await flushPromises();
    expect(wrapper.get('[data-test="track-content"]').element).toBe(selectedContent);
    expect(wrapper.get('[data-test="track-content"]').isVisible()).toBe(true);
    expect(mountedDetails).toBe(1);
    expect(document.activeElement).toBe(wrapper.get('[data-test="track-content"] button').element);
    expect(wrapper.getComponent(SheetStub).props('selectedDetent')).toBe('choice');
  });

  it('keeps the focused chooser row and selected content instance when another source adds results', async () => {
    const { wrapper, controller } = await mountSurface();
    await wrapper.get('[aria-label="Back to results"]').trigger('click');
    await flushPromises();
    const row = wrapper.get('[data-test="map-target-row"]');
    (row.element as HTMLButtonElement).focus();
    const rowIdentity = row.element;
    const detailInstance = wrapper.get('[data-test="track-content"]').element;
    controller.mapInteraction.value = {
      ...controller.mapInteraction.value!,
      candidates: [...controller.mapInteraction.value!.candidates, track(2)],
      sources: [{ id: 'routes', label: 'Trail overlay', kind: 'overlay', status: 'complete' }],
    };
    await nextTick();
    expect(document.activeElement).toBe(rowIdentity);
    expect(wrapper.findAll('[data-test="map-target-row"]')).toHaveLength(3);
    expect(wrapper.get('[data-test="track-content"]').element).toBe(detailInstance);
    expect(wrapper.get('h2').text()).toBe('Choose what to see here');
  });

  it('replaces a selected track detail instance when the user explicitly chooses another track', async () => {
    const state = interaction();
    state.candidates.push(track(2));
    const { wrapper, controller } = await mountSurface(state);
    const oldInstance = wrapper.get('[data-test="track-content"]').element;
    await wrapper.get('[aria-label="Back to results"]').trigger('click');
    await flushPromises();
    const rows = wrapper.findAll('[data-test="map-target-row"]');
    await rows.find((row) => row.text().includes('Track 2'))!.trigger('click');
    expect(controller.selectMapTarget).toHaveBeenCalledExactlyOnceWith(mapTargetKey(track(2)));
    expect(wrapper.getComponent(TrackDetailsProbe).props('gpsTrackId')).toBe(2);
    expect(wrapper.get('[data-test="track-content"]').element).not.toBe(oldInstance);
    expect(wrapper.findAll('[data-test="active-sheet"]')).toHaveLength(2);
  });

  it('opens primary and map-view collections directly in one surface and restores the viewer through Back', async () => {
    const primary: MapTargetCandidate = { ...photo, label: 'At this location', collectionScope: 'primary' };
    const viewport: MapTargetCandidate = {
      ...photo,
      id: 'media-viewport',
      label: 'Map view at tap',
      collectionScope: 'viewport',
      count: 3,
    };
    const state = interaction(null);
    state.candidates = [primary, viewport];
    state.sources = [];
    const { wrapper, controller } = await mountSurface(state);
    controller.trackDetailsVisible.value = false;
    controller.trackDetailsId.value = null;
    controller.mediaSheetVisible.value = false;
    controller.mediaSelectionSheetVisible.value = false;
    controller.trackSelectionSheetVisible.value = false;
    const selectTarget = controller.selectMapTarget.getMockImplementation()!;
    controller.selectMapTarget.mockImplementation((key: string) => {
      const selected = controller.mapInteraction.value!.candidates.find(
        (candidate) => mapTargetKey(candidate) === key
      )!;
      const restoring =
        controller.mapInteraction.value!.selected && mapTargetKey(controller.mapInteraction.value!.selected!) === key;
      selectTarget(key);
      if (selected.kind !== 'photo' || restoring) return;
      const ids = selected.collectionScope === 'viewport' ? [4, 5, 6] : [4];
      controller.mediaNavList.value = ids.map((id) => ({ id, lat: 47, lng: 8 }));
      controller.mediaNavigationIds.value = ids;
      controller.mediaNavScope.value = selected.collectionScope === 'viewport' ? 'viewport' : 'photo';
      controller.mediaNavTotal.value = selected.count;
      controller.mediaSheetMediaId.value = ids[0];
      controller.mediaSheetVisible.value = true;
    });
    await nextTick();

    await wrapper.get(`[data-target-key='${mapTargetKey(primary)}']`).trigger('click');
    await flushPromises();
    expect(wrapper.getComponent(MediaPreviewProbe).props()).toMatchObject({
      mediaId: 4,
      navTotal: 1,
      collectionLabel: 'This photo',
    });
    expect(controller.mediaNavScope.value).toBe('photo');
    expect(controller.mediaNavList.value.map((point) => point.id)).toEqual([4]);
    expect(wrapper.findAll('[data-test="active-sheet"]')).toHaveLength(1);
    expect(wrapper.findComponent(CollectionProbe).exists()).toBe(false);
    expect(controller.mediaPendingSelection.value).toBeNull();
    expect(controller.chooseMediaCollection).not.toHaveBeenCalled();
    expect(wrapper.get('.map-target-navigation').isVisible()).toBe(false);
    const retainedViewer = wrapper.get('[data-test="photo-content"]').element;

    await wrapper.get('[aria-label="Back to results"]').trigger('click');
    await flushPromises();
    expect(controller.mapInteraction.value?.presentation).toBe('chooser');
    expect(wrapper.get('[data-test="photo-content"]').element).toBe(retainedViewer);
    expect(wrapper.get('[data-test="photo-content"]').isVisible()).toBe(false);
    await wrapper.get(`[data-target-key='${mapTargetKey(primary)}']`).trigger('click');
    await flushPromises();
    expect(wrapper.get('[data-test="photo-content"]').element).toBe(retainedViewer);
    expect(wrapper.get('[data-test="photo-content"]').isVisible()).toBe(true);
    expect(controller.mediaNavScope.value).toBe('photo');

    await wrapper.get('[aria-label="Back to results"]').trigger('click');
    await flushPromises();
    await wrapper.get(`[data-target-key='${mapTargetKey(viewport)}']`).trigger('click');
    await flushPromises();
    expect(wrapper.getComponent(MediaPreviewProbe).props()).toMatchObject({
      mediaId: 4,
      navTotal: 3,
      collectionLabel: 'Map view at tap',
    });
    expect(controller.mediaNavScope.value).toBe('viewport');
    expect(controller.mediaNavList.value.map((point) => point.id)).toEqual([4, 5, 6]);
    expect(controller.mapInteraction.value?.selected).toEqual(viewport);
    expect(wrapper.findAll('[data-test="active-sheet"]')).toHaveLength(1);
    expect(wrapper.findComponent(CollectionProbe).exists()).toBe(false);

    await wrapper.get('[aria-label="Back to results"]').trigger('click');
    await flushPromises();
    await wrapper.get(`[data-target-key='${mapTargetKey(viewport)}']`).trigger('click');
    await flushPromises();
    expect(wrapper.get('[data-test="photo-content"]').element).toBe(retainedViewer);
    expect(wrapper.getComponent(MediaPreviewProbe).props('collectionLabel')).toBe('Map view at tap');
    await wrapper.get('[aria-label="Close interaction"]').trigger('click');
    expect(controller.endMapInteraction).toHaveBeenCalledExactlyOnceWith(true);
  });

  it('retains the photo viewer while target choices are visible and restores it from its result row', async () => {
    const { wrapper, controller } = await mountSurface(interaction(photo));
    controller.mapInteractionDetailKind.value = 'photo';
    controller.trackSelectionSheetVisible.value = false;
    await nextTick();
    const viewer = wrapper.get('[data-test="photo-content"]').element;
    await wrapper.get('[aria-label="Back to results"]').trigger('click');
    await flushPromises();
    expect(wrapper.get('[data-test="photo-content"]').element).toBe(viewer);
    expect(wrapper.get('[data-test="photo-content"]').isVisible()).toBe(false);
    expect(wrapper.getComponent(SheetStub).props('selectedDetent')).toBe('choice');
    expect(wrapper.getComponent(SheetStub).props('fitContent')).toBe(false);
    expect(wrapper.getComponent(SheetStub).props('nativeFullscreen')).toBe(false);
    expect(wrapper.getComponent(SheetStub).props('viewportCentered')).toBe(false);
    expect(wrapper.getComponent(SheetStub).props('sheetClass')).not.toContain('media-viewer-sheet');
    expect(wrapper.find('[data-test="map-target-back"]').exists()).toBe(false);
    await wrapper.get('[data-test="map-target-row"][aria-pressed="true"]').trigger('click');
    expect(wrapper.get('[data-test="photo-content"]').element).toBe(viewer);
    expect(wrapper.get('[data-test="photo-content"]').isVisible()).toBe(true);
    expect(wrapper.getComponent(SheetStub).props('selectedDetent')).toBe('choice');
    expect(wrapper.getComponent(SheetStub).props('fitContent')).toBe(false);
    expect(wrapper.getComponent(SheetStub).props('nativeFullscreen')).toBe(true);
    expect(wrapper.getComponent(SheetStub).props('viewportCentered')).toBe(false);
  });

  it('retains the photo detail control and the dispatcher detent inside the existing sheet', async () => {
    const { wrapper, controller } = await mountSurface(interaction(photo));
    controller.mapInteractionDetailKind.value = 'photo';
    controller.trackSelectionSheetVisible.value = false;
    await nextTick();
    expect(wrapper.getComponent(SheetStub).props('selectedDetent')).toBe('choice');
    const initial = wrapper.getComponent(MediaPreviewProbe).props('detailsVisible');
    wrapper.getComponent(MediaViewerViewToggle).vm.$emit('update:modelValue', !initial);
    await nextTick();
    expect(wrapper.getComponent(MediaPreviewProbe).props('detailsVisible')).toBe(!initial);
    expect(wrapper.findAll('[data-test="active-sheet"]')).toHaveLength(1);
  });

  it('keeps the dispatcher size for labeled, padded POI details without reserving an empty lookup area', async () => {
    const poi: MapTargetCandidate = {
      id: 'candal',
      kind: 'poi',
      source: 'protomaps',
      sourceLabel: 'Points of interest',
      label: 'Candal',
      detail: 'bus stop',
      coordinates: [-8.63, 41.13],
      properties: { elevation: '42' },
    };
    const state = interaction(poi);
    state.candidates = [poi];
    state.sources = [];
    const { wrapper, controller } = await mountSurface(state);
    controller.mapInteractionDetailKind.value = 'simple';
    await nextTick();
    expect(wrapper.getComponent(SheetStub).props('fitContent')).toBe(false);
    expect(wrapper.get('.map-interaction-context').isVisible()).toBe(false);
    expect(wrapper.get('.map-interaction-content').classes()).toContain('map-interaction-content--compact');
    expect(wrapper.getComponent(SheetStub).props('icon')).toBe('bi bi-bus-front-fill');
    expect(wrapper.getComponent(SheetStub).props('desktopWidth')).toBe('standard');
    expect(wrapper.getComponent(MapPoiDetails).text()).toContain('Bus stop');
    expect(wrapper.getComponent(MapPoiDetails).text()).toContain('Elevation42 m');
    expect(wrapper.getComponent(MapPoiDetails).text()).toContain('Source: Points of interest');
    expect(wrapper.findComponent({ name: 'TrackDetailActions' }).exists()).toBe(false);
  });

  it('keeps pending lookup feedback for a POI and removes the area when it completes empty', async () => {
    const poi: MapTargetCandidate = {
      id: 'spring',
      kind: 'poi',
      source: 'protomaps',
      sourceLabel: 'Points of interest',
      label: 'Village spring',
      detail: 'drinking water',
      coordinates: [8, 47],
      properties: {},
    };
    const state = interaction(poi);
    state.candidates = [poi];
    const { wrapper, controller } = await mountSurface(state);
    controller.mapInteractionDetailKind.value = 'simple';
    await nextTick();
    expect(wrapper.get('.map-interaction-context').isVisible()).toBe(true);
    expect(wrapper.get('.map-interaction-context').text()).toContain('loading');
    controller.mapInteraction.value = { ...state, sources: [{ ...state.sources[0], status: 'complete' }] };
    await nextTick();
    expect(wrapper.get('.map-interaction-context').isVisible()).toBe(false);
    expect(wrapper.getComponent(MapPoiDetails).text()).not.toContain('Elevation');
  });

  it('shows a failed local source in the chooser without reporting a completed empty lookup', async () => {
    const state = interaction(null);
    state.sources = [{ id: 'pois', label: 'Points of interest', kind: 'local', status: 'failed' }];
    const { wrapper } = await mountSurface(state);
    expect(wrapper.getComponent(MapTargetChooser).text()).toContain('unavailable');
    expect(wrapper.findAll('[data-test="active-sheet"]')).toHaveLength(1);
    expect(wrapper.text()).not.toContain('Nothing found here');
  });

  it('keeps incomplete indexing information in results without adding it to GPS details', async () => {
    const { wrapper, controller } = await mountSurface();
    controller.mapInteraction.value = {
      ...controller.mapInteraction.value!,
      processing: {
        state: 'RUNNING',
        stages: [],
        failedGpsFiles: 2,
      },
    };
    await nextTick();
    expect(wrapper.get('.map-interaction-content').text()).toContain('still being processed');
    expect(wrapper.get('.map-interaction-content').text()).toContain('2 GPS files');
    const detailSheet = wrapper.get('[data-test="track-content"]').element.closest('section')!;
    expect(detailSheet.textContent).not.toMatch(/still being processed|Nearby results|GPS files/);
    expect(wrapper.findAll('[data-test="active-sheet"]')).toHaveLength(2);
    expect(mountedDetails).toBe(1);
    await wrapper.get('[aria-label="Back to results"]').trigger('click');
    await flushPromises();
    expect(wrapper.getComponent(MapTargetChooser).isVisible()).toBe(true);
    expect(wrapper.getComponent(MapTargetChooser).text()).toContain('2 GPS files');
  });

  it('opens dispatcher photos in light, retains an explicit dark choice through Back, and resets for a new tap', async () => {
    const { wrapper, controller } = await mountSurface(interaction(photo));
    const sheet = wrapper.getComponent(SheetStub);
    expect(sheet.props('sheetClass')).toContain('media-viewer-sheet--light');
    wrapper.getComponent(MediaViewerThemeToggle).vm.$emit('update:theme', 'dark');
    await nextTick();
    expect(sheet.props('sheetClass')).toContain('media-viewer-sheet--dark');
    await wrapper.get('[aria-label="Back to results"]').trigger('click');
    await flushPromises();
    await wrapper.get('[data-test="map-target-row"][aria-pressed="true"]').trigger('click');
    await nextTick();
    expect(sheet.props('sheetClass')).toContain('media-viewer-sheet--dark');
    controller.mapInteraction.value = { ...controller.mapInteraction.value!, id: 13 };
    await nextTick();
    expect(sheet.props('sheetClass')).toContain('media-viewer-sheet--light');
  });

  it('keeps one layout through all detail types, loading, errors and Back', async () => {
    const poi: MapTargetCandidate = {
      id: 'place',
      kind: 'poi',
      source: 'protomaps',
      label: 'Place',
      coordinates: [8, 47],
      properties: {},
    };
    const point: MapTargetCandidate = {
      id: 'point',
      kind: 'track-point',
      source: 'points',
      label: 'Track point',
      trackId: 1,
      pointIndex: 0,
    };
    const state = interaction(null);
    state.candidates = [track(1), overlay, photo, poi, point];
    const { wrapper, controller } = await mountSurface(state);
    const sheet = wrapper.getComponent(SheetStub);
    const layout = () => ({
      detents: sheet.props('detents'),
      selected: sheet.props('selectedDetent'),
      fit: sheet.props('fitContent'),
      overflow: sheet.props('fitSmallOverflow'),
      width: sheet.props('desktopWidth'),
      centered: sheet.props('viewportCentered'),
      shared: sheet.props('shareLayout'),
    });
    const initial = layout();
    expect(initial).toMatchObject({ fit: false, overflow: false, width: 'standard', centered: false, shared: true });
    for (const target of state.candidates) {
      controller.selectMapTarget(mapTargetKey(target));
      controller.mapInteractionDetailLoading.value = true;
      await nextTick();
      expect(layout()).toEqual(initial);
      controller.mapInteractionDetailLoading.value = false;
      controller.mediaSheetVisible.value = target.kind === 'photo';
      await nextTick();
      expect(layout()).toEqual(initial);
      controller.mapInteractionDetailError.value = 'Details unavailable';
      await nextTick();
      expect(layout()).toEqual(initial);
      controller.mapInteractionDetailError.value = null;
      await wrapper.get('[aria-label="Back to results"]').trigger('click');
      await flushPromises();
      expect(layout()).toEqual(initial);
      expect(wrapper.getComponent(SheetStub).element).toBe(sheet.element);
    }
  });

  it('keeps a constant shared chooser detent through chips, late hits and final empty states', async () => {
    const state = interaction(null);
    state.candidates = [track(1), photo];
    const { wrapper, controller } = await mountSurface(state);
    const sheet = wrapper.getComponent(SheetStub);
    const sizing = () => ({
      detents: sheet.props('detents'),
      fit: sheet.props('fitContent'),
      overflow: sheet.props('fitSmallOverflow'),
      selected: sheet.props('selectedDetent'),
    });
    const initial = sizing();
    expect(initial).toMatchObject({ fit: false, overflow: false, selected: 'choice' });
    await wrapper.get('[data-category-key="track"][data-test="map-category-tab"]').trigger('click');
    expect(sizing()).toEqual(initial);
    await wrapper.get('[data-category-key="swiss"][data-test="map-category-tab"]').trigger('click');
    expect(sizing()).toEqual(initial);
    controller.mapInteraction.value = {
      ...state,
      candidates: [...state.candidates, overlay],
      sources: [{ ...state.sources[0], status: 'complete' }],
    };
    await nextTick();
    expect(sizing()).toEqual(initial);
    await wrapper.get('[data-test="map-category-all"]').trigger('click');
    controller.mapInteraction.value = {
      ...state,
      candidates: [],
      sources: [{ ...state.sources[0], status: 'complete' }],
    };
    await nextTick();
    expect(wrapper.getComponent(MapTargetChooser).text()).toContain('Nothing found here');
    expect(sizing()).toEqual(initial);
    expect(wrapper.findAll('[data-test="active-sheet"]')).toHaveLength(1);
  });

  it('dismisses the active surface through its shared close control', async () => {
    const { wrapper, controller } = await mountSurface();
    await wrapper.get('[aria-label="Close interaction"]').trigger('click');
    expect(controller.endMapInteraction).toHaveBeenCalledExactlyOnceWith(true);
    expect(wrapper.findAll('[data-test="active-sheet"]')).toHaveLength(0);
    expect(wrapper.findComponent(TrackDetailsProbe).exists()).toBe(false);
  });
});
