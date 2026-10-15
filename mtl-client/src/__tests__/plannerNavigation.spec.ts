import { DOMWrapper, enableAutoUnmount, flushPromises, shallowMount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { computed, defineComponent, nextTick } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import PlannerTool from '@/planner/components/PlannerTool.vue';
import PlannerPoiEditor from '@/planner/components/PlannerPoiEditor.vue';
import PlannerSummary from '@/planner/components/PlannerSummary.vue';
import PlannerActionMenu from '@/planner/components/PlannerActionMenu.vue';
import BottomSheet from '@/components/ui/BottomSheet.vue';
import { STORAGE_KEYS } from '@/utils/appStorage';
import { useBRouterSegmentStatus } from '@/planner/composables/useBRouterSegmentStatus';
import {
  PLANNER_POI_SOURCE_ID,
  PLANNER_POI_LABEL_LAYER_ID,
  PLANNER_SOURCE_ID,
  PLANNER_WAYPOINT_SOURCE_ID,
  PLANNER_WAYPOINT_LABEL_LAYER_ID,
} from '@/planner/constants/PlannerConstants';
import {
  deletePlannedTrack,
  fetchPlannerConfig,
  listPlannedTracks,
  loadPlannedTrack,
  savePlannedRoute,
} from '@/planner/repositories/plannerRepository';

vi.mock('@/planner/composables/useBRouterSegmentStatus', async () => {
  const { ref } = await import('vue');
  return { useBRouterSegmentStatus: vi.fn(() => ({ status: ref(null), refresh: vi.fn() })) };
});
vi.mock('@/planner/utils/plannerPoiIcons', () => ({ installPlannerPoiIcons: vi.fn(async () => true) }));

vi.mock('@/planner/repositories/plannerRepository', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/planner/repositories/plannerRepository')>()),
  fetchPlannerConfig: vi.fn(async () => ({
    profiles: ['trekking'],
    defaultProfile: 'trekking',
    maxWaypoints: 50,
    maxPointsOfInterest: 1000,
  })),
  listPlannedTracks: vi.fn(async () => []),
  loadPlannedTrack: vi.fn(),
  deletePlannedTrack: vi.fn(async () => undefined),
  savePlannedRoute: vi.fn(),
  prewarmForBbox: vi.fn(async () => undefined),
}));

enableAutoUnmount(afterEach);

const sheetSnapTo = vi.fn((_id: string) => true);
const toastAdd = vi.fn();
const BottomSheetStub = defineComponent({
  props: {
    backLabel: { type: String, default: '' },
    detents: { type: Array, default: () => [] },
    fitContent: Boolean,
    freeResize: Boolean,
  },
  emits: ['back', 'layout-change', 'detent-change', 'resize-end'],
  methods: {
    snapTo(id: string) {
      return sheetSnapTo(id);
    },
    resizeTo() {
      return true;
    },
  },
  template:
    '<div><button v-if="backLabel" :aria-label="backLabel" @click="$emit(\'back\')">Back</button><slot name="title" /><slot name="header-actions" /><slot name="size-action" /><slot name="pinned" /><slot /></div>',
});
const ButtonStub = defineComponent({
  props: { label: { type: String, default: '' } },
  template: '<button>{{ label }}<slot /></button>',
});
const MenuStub = defineComponent({
  props: { model: Array, ariaLabel: String },
  template:
    '<div role="menu" tabindex="0" :aria-label="ariaLabel"><button v-for="item in model" :key="item.label" :disabled="item.disabled" @click="item.command()">{{ item.label }}</button></div>',
});

async function clickMenuAction(menuClass: string, label: string) {
  const button = Array.from(document.querySelectorAll<HTMLButtonElement>(`${menuClass} button`)).find(
    (element) => element.textContent?.trim() === label
  );
  expect(button, `Menu action ${label}`).toBeDefined();
  await new DOMWrapper(button!).trigger('click');
  await nextTick();
}

function createMap(styleLoaded = true, glyphsUrl?: string) {
  const sources = new Map<string, { data: unknown; setData: (data: unknown) => void }>();
  const layers = new Map<string, unknown>();
  const listeners = new Map<string, Set<(event: unknown) => void>>();
  const canvas = document.createElement('canvas');
  canvas.tabIndex = 0;
  const map = {
    getGlyphs: vi.fn(() => glyphsUrl),
    getSource: (id: string) => sources.get(id),
    addSource: (id: string, source: { data: unknown }) => {
      sources.set(id, {
        data: source.data,
        setData(data) {
          this.data = data;
        },
      });
    },
    removeSource: (id: string) => sources.delete(id),
    getLayer: (id: string) => layers.get(id),
    addLayer: (layer: { id: string }) => layers.set(layer.id, layer),
    removeLayer: (id: string) => layers.delete(id),
    isStyleLoaded: () => styleLoaded,
    queryRenderedFeatures: vi.fn(() => [] as Array<{ properties: { legIndex: number } }>),
    project: ([lng, lat]: number[]) => ({ x: (lng - 8) * 1000, y: (lat - 47) * 1000 }),
    unproject: ([x, y]: number[]) => ({ lng: x / 1000 + 8, lat: y / 1000 + 47 }),
    dragPan: { enable: vi.fn(), disable: vi.fn() },
    touchZoomRotate: { enable: vi.fn(), disable: vi.fn() },
    getCanvas: () => canvas,
    getContainer: () => ({ clientHeight: 900 }),
    fitBounds: vi.fn(),
    getBounds: () => ({ getSouthWest: () => ({ lat: 47, lng: 8 }), getNorthEast: () => ({ lat: 47.1, lng: 8.1 }) }),
    on: (name: string, listener: (event: unknown) => void) => {
      const handlers = listeners.get(name) ?? new Set();
      handlers.add(listener);
      listeners.set(name, handlers);
    },
    off: (name: string, listener: (event: unknown) => void) => listeners.get(name)?.delete(listener),
    once: (name: string, listener: (event: unknown) => void) => map.on(name, listener),
  };
  return {
    map,
    layers,
    sources,
    listeners,
    fire: (name: string, event: unknown = {}) => listeners.get(name)?.forEach((listener) => listener(event)),
    waypointCount: () =>
      (sources.get(PLANNER_WAYPOINT_SOURCE_ID)?.data as { features: unknown[] } | undefined)?.features.length,
  };
}

async function mountPlanner(styleLoaded = true, glyphsUrl?: string, realSheet = false) {
  const map = createMap(styleLoaded, glyphsUrl);
  const wrapper = shallowMount(PlannerTool, {
    attachTo: document.body,
    props: { map: map.map },
    global: {
      provide: { toast: { add: toastAdd } },
      stubs: {
        Teleport: false,
        BottomSheet: realSheet ? BottomSheet : BottomSheetStub,
        PlannerHub: false,
        PlannerSummary: false,
        PlannerActionMenu: false,
        Menu: MenuStub,
        SectionHub: false,
        Button: ButtonStub,
        Dialog: defineComponent({
          props: { visible: Boolean },
          template: '<div v-if="visible" role="dialog"><slot /><slot name="footer" /></div>',
        }),
        InputText: defineComponent({
          props: { modelValue: String },
          emits: ['update:modelValue'],
          template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
        }),
      },
    },
  });
  await wrapper.vm.open();
  await nextTick();
  return { wrapper, ...map };
}

describe('Planner hub navigation', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'ResizeObserver',
      class {
        observe() {}
        disconnect() {}
      }
    );
    setActivePinia(createPinia());
    localStorage.clear();
    vi.clearAllMocks();
    vi.useFakeTimers();
    vi.mocked(useBRouterSegmentStatus).mockReturnValue({
      status: computed(() => null),
      loading: computed(() => false),
      refresh: vi.fn(async () => {}),
    });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.clearAllTimers();
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('uses Swiss Topo fonts for waypoint numbers and selected POI labels', async () => {
    const { wrapper, layers } = await mountPlanner(
      true,
      'https://vectortiles.geo.admin.ch/fonts/{fontstack}/{range}.pbf'
    );
    await wrapper.get('[data-section="draw"]').trigger('click');
    await flushPromises();
    for (const id of [PLANNER_WAYPOINT_LABEL_LAYER_ID, PLANNER_POI_LABEL_LAYER_ID]) {
      expect(layers.get(id)).toMatchObject({ layout: { 'text-font': ['Frutiger Neue Regular'] } });
    }
  });
  it('keeps one workspace across desktop entry sections and drawing', async () => {
    vi.stubGlobal(
      'matchMedia',
      vi.fn(() => ({ matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() }))
    );
    const { wrapper } = await mountPlanner();
    const detents = wrapper.findComponent(BottomSheetStub).props('detents');
    expect(wrapper.findComponent(BottomSheetStub).props('fitContent')).toBe(false);
    expect(detents).toContainEqual({ id: 'hub', height: 'min(60vh, 480px)' });
    await wrapper.get('[data-section="draw"]').trigger('click');
    await flushPromises();
    expect(wrapper.findComponent(BottomSheetStub).props('fitContent')).toBe(false);
    expect(wrapper.findComponent(BottomSheetStub).props('detents')).toEqual(detents);
    expect(wrapper.findComponent(BottomSheetStub).attributes('selected-detent')).toBeUndefined();
    expect(wrapper.findComponent(BottomSheetStub).props('freeResize')).toBe(true);
  });

  it.each([390, 1440])('retains the actual workspace through navigation and manual sizing at %i px', async (width) => {
    vi.useRealTimers();
    const initialWidth = window.innerWidth;
    const initialHeight = window.innerHeight;
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: width });
    Object.defineProperty(window, 'innerHeight', { configurable: true, value: 900 });
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(function () {
      const height = this.classList.contains('sheet') ? Number.parseFloat(this.style.height) || 0 : 0;
      return {
        x: 0,
        y: 900 - height,
        top: 900 - height,
        bottom: 900,
        left: 0,
        right: width,
        width,
        height,
        toJSON() {},
      };
    });
    // A saved custom height exercises the same real-sheet opening path on both viewports.
    localStorage.setItem(STORAGE_KEYS.plannerPanelPreferences, JSON.stringify({ heightFraction: 0.6 }));
    const { wrapper } = await mountPlanner(true, undefined, true);
    const sheet = wrapper.findComponent(BottomSheet);
    const element = document.querySelector<HTMLElement>('.planner-sheet')!;
    const panelDom = new DOMWrapper(element);
    const settle = async () => {
      await flushPromises();
      await nextTick();
      await new Promise<void>((resolve) => window.requestAnimationFrame(() => resolve()));
      await flushPromises();
    };
    try {
      await settle();
      expect(element.style.height).toBe('540px');
      await panelDom.get('[data-section="draw"]').trigger('click');
      await settle();
      expect(element.style.height).toBe('540px');
      expect(sheet.vm.resizeTo(620)).toBe(true);
      await settle();
      await new DOMWrapper(element.querySelector<HTMLButtonElement>('[aria-label="Back to Planner"]')!).trigger(
        'click'
      );
      await settle();
      expect(element.style.height).toBe('620px');
      await panelDom.get('[data-section="load"]').trigger('click');
      await settle();
      expect(element.style.height).toBe('620px');
      await new DOMWrapper(element.querySelector<HTMLButtonElement>('[aria-label="Back to Planner"]')!).trigger(
        'click'
      );
      await settle();
      await panelDom.get('.planner-hub__guide-summary button').trigger('click');
      await settle();
      expect(element.style.height).toBe('620px');
      await new DOMWrapper(element.querySelector<HTMLButtonElement>('[aria-label="Back to Planner"]')!).trigger(
        'click'
      );
      await settle();
      await panelDom.get('[data-section="draw"]').trigger('click');
      await settle();
      expect(element.style.height).toBe('620px');
      sheet.vm.resizeTo(500);
      sheet.vm.$emit('resize-end', 500);
      await settle();
      await panelDom.get('.planner-size-button').trigger('click');
      await settle();
      expect(Number.parseFloat(element.style.height)).toBeLessThan(500);
      await panelDom.get('.planner-size-button').trigger('click');
      await settle();
      expect(element.style.height).toBe('500px');
    } finally {
      wrapper.unmount();
      Object.defineProperty(window, 'innerWidth', { configurable: true, value: initialWidth });
      Object.defineProperty(window, 'innerHeight', { configurable: true, value: initialHeight });
    }
  });

  it('flags failed routing downloads and shows their causes', async () => {
    vi.mocked(useBRouterSegmentStatus).mockReturnValue({
      status: computed(() => ({
        available: true,
        brouterRunning: true,
        segmentsFailed: { 'E5_N45.rd5': 'No such file or directory: /segments4/E5_N45.rd5.part' },
      })),
      loading: computed(() => false),
      refresh: vi.fn(async () => {}),
    });
    const { wrapper } = await mountPlanner();
    const statusButton = wrapper.get('[aria-label="BRouter status: 1 routing-data download(s) failed"]');
    expect(statusButton.classes()).toContain('brouter-pill--danger');
    await statusButton.trigger('click');
    expect(wrapper.text()).toContain('E5_N45.rd5: No such file or directory: /segments4/E5_N45.rd5.part');
  });

  it('explains missing storage in the status panel', async () => {
    const reason = 'Routing data directory /segments4 is missing. Restore it and restart the routing service.';
    vi.mocked(useBRouterSegmentStatus).mockReturnValue({
      status: computed(() => ({ available: false, brouterRunning: true, reason })),
      loading: computed(() => false),
      refresh: vi.fn(async () => {}),
    });
    const { wrapper } = await mountPlanner();
    await wrapper.get('.brouter-pill').trigger('click');
    expect(wrapper.get('.brouter-detail-summary').text()).toBe(reason);
  });

  it('only handles map clicks while drawing and preserves a draft across hub and close/reopen', async () => {
    const { wrapper, listeners, fire, waypointCount } = await mountPlanner();
    expect(wrapper.get('h2').text()).toBe('Planner');
    expect(wrapper.get('[data-section="draw"]').text()).toContain('Start a new plan');
    expect(listeners.get('click')?.size ?? 0).toBe(0);
    expect(listPlannedTracks).not.toHaveBeenCalled();
    expect(wrapper.find('.planner-entry__primary').exists()).toBe(false);
    expect(wrapper.find('[data-section="guide"]').exists()).toBe(false);
    expect(wrapper.find('.settings-eyebrow').exists()).toBe(false);
    expect(wrapper.get('.planner-hub__guide-summary').element.parentElement).toBe(
      wrapper.get('.section-hub__description').element
    );

    await wrapper.get('[data-section="draw"]').trigger('click');
    expect(wrapper.get('h2').text()).toBe('Plan a map');
    expect(document.activeElement).toBe(wrapper.get('h2').element);
    expect(listeners.get('click')?.size).toBe(1);
    fire('click', { point: { x: 20, y: 20 }, lngLat: { lat: 47.01, lng: 8.01 }, originalEvent: { type: 'click' } });
    await nextTick();
    expect(waypointCount()).toBe(1);

    await wrapper.get('[aria-label="Back to Planner"]').trigger('click');
    expect(wrapper.get('[data-section="draw"]').text()).toContain('Continue your current plan');
    expect(document.activeElement).toBe(wrapper.get('[data-section="draw"]').element);
    expect(listeners.get('click')?.size).toBe(0);

    await wrapper.get('[data-section="draw"]').trigger('click');
    expect(waypointCount()).toBe(1);
    wrapper.vm.close();
    await nextTick();
    expect(listeners.get('click')?.size).toBe(0);
    await wrapper.vm.open();
    await nextTick();
    expect(wrapper.get('h2').text()).toBe('Planner');
    expect(wrapper.get('[data-section="draw"]').text()).toContain('Continue your current plan');
    await wrapper.get('[data-section="draw"]').trigger('click');
    expect(waypointCount()).toBe(1);
  });

  it.each(['saved', 'unsaved'] as const)(
    'keeps an empty %s plan reachable through Continue and preserves clear undo',
    async (origin) => {
      const { wrapper, fire, waypointCount } = origin === 'saved' ? await openSyntheticPlan() : await mountPlanner();
      if (origin === 'unsaved') {
        await wrapper.get('[data-section="draw"]').trigger('click');
        fire('click', pointEvent(20, 20));
        await nextTick();
      }
      const originalPointCount = waypointCount();
      await wrapper.get('[aria-label="Route tools"]').trigger('click');
      await clickMenuAction('.planner-route-menu', 'Clear route');
      expect(waypointCount()).toBe(0);

      await wrapper.get('[aria-label="Back to Planner"]').trigger('click');
      expect(wrapper.get('[data-section="draw"]').text()).toContain('Continue your current plan');
      await wrapper.get('[data-section="load"]').trigger('click');
      await flushPromises();
      expect(wrapper.get('.planner-return-draft').text()).toContain('Continue current plan');
      await wrapper.get('.planner-return-draft').trigger('click');
      expect(waypointCount()).toBe(0);
      await wrapper.get('[aria-label="Undo"]').trigger('click');
      expect(waypointCount()).toBe(originalPointCount);
      expect(wrapper.get('.planner-save-button').attributes('aria-label')).toBe(
        origin === 'saved' ? 'Save copy: Saved plan' : 'Save plan: Unsaved changes'
      );
    }
  );

  it('continues an empty plan with only redo history from the hub and empty saved-plans list', async () => {
    const { wrapper, fire, waypointCount } = await mountPlanner();
    await wrapper.get('[data-section="draw"]').trigger('click');
    fire('click', pointEvent(20, 20));
    await nextTick();
    await wrapper.get('[aria-label="Undo"]').trigger('click');
    expect(waypointCount()).toBe(0);
    expect(wrapper.get('[aria-label="Undo"]').attributes('disabled')).toBeDefined();
    expect(wrapper.get('[aria-label="Redo"]').attributes('disabled')).toBeUndefined();

    await wrapper.get('[aria-label="Back to Planner"]').trigger('click');
    expect(wrapper.get('[data-section="draw"]').text()).toContain('Continue your current plan');
    await wrapper.get('[data-section="load"]').trigger('click');
    await flushPromises();
    expect(wrapper.get('.planner-load-empty button').text()).toBe('Continue planning');
    await wrapper.get('.planner-load-empty button').trigger('click');
    expect(waypointCount()).toBe(0);
    await wrapper.get('[aria-label="Redo"]').trigger('click');
    expect(waypointCount()).toBe(1);
  });

  it('opens the guide without activating map edits, then starts planning', async () => {
    const { wrapper, listeners } = await mountPlanner();
    expect(wrapper.findComponent({ name: 'PlannerMapPreview' }).exists()).toBe(false);
    await wrapper.get('.planner-hub__guide-summary button').trigger('click');
    expect(wrapper.findComponent({ name: 'PlannerMapPreview' }).exists()).toBe(true);
    expect(wrapper.get('.planner-guide').text()).toContain('Route overlays');
    expect(wrapper.get('.planner-guide a').attributes('rel')).toBe('opener');
    expect(wrapper.find('.planner-guide button').exists()).toBe(false);
    expect(listeners.get('click')?.size ?? 0).toBe(0);
    await wrapper.get('[aria-label="Back to Planner"]').trigger('click');
    expect(wrapper.find('.planner-guide').exists()).toBe(false);
    expect(listeners.get('click')?.size ?? 0).toBe(0);
  });

  it('keeps failed saved-plan requests distinct from an empty list and supports retry and drawing', async () => {
    vi.mocked(listPlannedTracks).mockRejectedValueOnce(new Error('offline'));
    vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    const { wrapper, listeners } = await mountPlanner();
    await wrapper.get('[data-section="load"]').trigger('click');
    await flushPromises();
    expect(wrapper.get('[role="alert"]').text()).toContain('Could not load saved plans');
    expect(wrapper.find('.planner-load-empty').exists()).toBe(false);
    expect(listeners.get('click')?.size ?? 0).toBe(0);
    await wrapper.get('[role="alert"] button').trigger('click');
    await flushPromises();
    expect(wrapper.get('.planner-load-empty').text()).toContain('No saved plans yet');
    await wrapper.get('.planner-load-empty button').trigger('click');
    expect(wrapper.get('h2').text()).toBe('Plan a map');
  });

  it('opens a saved plan in the drawing view with its waypoints', async () => {
    vi.mocked(listPlannedTracks).mockResolvedValueOnce([
      { id: 1, name: 'Synthetic route', distanceM: 1000, centerLat: 47, centerLng: 8, createDate: '' },
    ]);
    vi.mocked(loadPlannedTrack).mockResolvedValueOnce({
      profile: 'trekking',
      stats: { distanceM: 1000, ascentM: 10, descentM: 0, legCount: 1 },
      coordinates: [
        [8, 47, 500],
        [8.01, 47.01, 510],
      ],
      waypoints: [
        { lat: 47, lng: 8 },
        { lat: 47.01, lng: 8.01 },
      ],
    });
    const { wrapper, waypointCount } = await mountPlanner();
    await wrapper.get('[data-section="load"]').trigger('click');
    await flushPromises();
    expect(wrapper.get('h2').text()).toBe('Saved plans');
    await wrapper.get('.planner-plan-open').trigger('click');
    await flushPromises();
    expect(loadPlannedTrack).toHaveBeenCalledWith(1);
    expect(wrapper.get('h2').text()).toBe('Plan a map');
    expect(waypointCount()).toBe(2);
  });

  it('pins totals, profile and Undo and switches tools with one labelled size control', async () => {
    const { wrapper } = await openSyntheticPlan();
    const sheet = wrapper.findComponent(BottomSheetStub);
    const summary = wrapper.findComponent(PlannerSummary);

    expect(sheet.attributes('selected-detent')).toBeUndefined();
    expect(summary.props('density')).toBe('full');
    expect(summary.props('result')?.stats.distanceM).toBe(1000);
    expect(wrapper.find('.planner-summary__controls [aria-label="Undo"]').exists()).toBe(true);
    expect(wrapper.get('.planner-panel--draw').isVisible()).toBe(true);
    expect(wrapper.get('.planner-size-button').text()).toBe('More map');

    await wrapper.get('.planner-size-button').trigger('click');
    sheet.vm.$emit('detent-change', 'compact');
    await nextTick();
    expect(wrapper.get('.planner-panel--draw').isVisible()).toBe(false);
    expect(wrapper.get('.planner-size-button').text()).toBe('Show tools');

    sheet.vm.$emit('detent-change', 'totals');
    await nextTick();
    const profile = summary.findComponent({ name: 'ElevationProfile' });
    expect(summary.get('#planner-summary-profile').isVisible()).toBe(false);
    expect(profile.exists()).toBe(true);

    await wrapper.get('.planner-size-button').trigger('click');
    sheet.vm.$emit('detent-change', 'full');
    await nextTick();
    expect(wrapper.get('.planner-panel--draw').isVisible()).toBe(true);
    expect(summary.findComponent({ name: 'ElevationProfile' }).vm).toBe(profile.vm);

    sheet.vm.$emit('detent-change', 'compact');
    sheet.vm.$emit('detent-change', 'full');
    await nextTick();
    await wrapper.get('.planner-size-button').trigger('click');

    expect(sheetSnapTo.mock.calls).toEqual([['compact'], ['full'], ['compact']]);
    expect(JSON.parse(localStorage.getItem('mtl.planner.panel-preferences')!)).toMatchObject({
      size: 'compact',
      lastFullSize: 'full',
      reduceTipDone: true,
    });
  });

  it('keeps the previous totals while a changed route recalculates and clears them with the route', async () => {
    const { wrapper, fire } = await openSyntheticPlan();
    await nextTick();

    fire('click', { point: { x: 40, y: 40 }, lngLat: { lat: 47.02, lng: 8.02 }, originalEvent: { type: 'click' } });
    await nextTick();
    const summary = wrapper.findComponent(PlannerSummary);
    expect(summary.props('updating')).toBe(true);
    expect(summary.props('result')?.stats.distanceM).toBe(1000);
    expect(summary.classes()).toContain('planner-summary--updating');
    expect(summary.get('.planner-summary__status').text()).toBe('Updating');

    await wrapper.get('[aria-label="Route tools"]').trigger('click');
    await clickMenuAction('.planner-route-menu', 'Clear route');
    await nextTick();
    expect(summary.props('result')).toBeNull();
    expect(summary.text()).toContain('Tap the map to place your start point.');
  });

  it('groups expanded actions above the totals and retains route feedback during POI editing', async () => {
    const { wrapper } = await openSyntheticPlan();
    const summary = wrapper.findComponent(PlannerSummary);
    const controls = wrapper.get('.planner-summary__controls');
    const totals = wrapper.get('.planner-summary__full-totals');
    const modes = controls.get('.planner-edit-mode');
    const actions = controls.get('.planner-utility-actions');
    expect(modes.element.compareDocumentPosition(actions.element) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(controls.find('[aria-label="Undo"]').exists()).toBe(true);
    expect(controls.find('[aria-label="Redo"]').exists()).toBe(true);
    expect(controls.find('[aria-label="Elevation profile"]').exists()).toBe(false);
    expect(controls.find('[aria-label="Saved plans"]').exists()).toBe(false);
    expect(summary.props('profileRequested')).toBe(true);
    expect(totals.find('button').exists()).toBe(false);
    expect(controls.element.compareDocumentPosition(totals.element) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    const route = summary.props('result');
    const profile = summary.findComponent({ name: 'ElevationProfile' }).vm;
    await controls
      .findAll('button')
      .find((button) => button.text() === 'Points of interest')!
      .trigger('click');
    expect(summary.props('showStats')).toBe(true);
    expect(summary.props('profileVisible')).toBe(false);
    expect(controls.find('[aria-label="Elevation profile"]').exists()).toBe(false);
    expect(summary.props('result')).toBe(route);
    expect(summary.findComponent({ name: 'ElevationProfile' }).vm).toBe(profile);
    expect(controls.get('.planner-poi-tools').text()).toContain('Tap the map to add a POI.');
    await modes.trigger('keydown', { key: 'ArrowLeft' });
    expect(document.activeElement).toBe(modes.get('[data-plan-mode="route"]').element);
    expect(modes.get('[data-plan-mode="route"]').attributes('tabindex')).toBe('0');
    expect(modes.get('[data-plan-mode="poi"]').attributes('tabindex')).toBe('-1');
    expect(summary.props('profileVisible')).toBe(true);
    expect(summary.findComponent({ name: 'ElevationProfile' }).vm).toBe(profile);
    expect(summary.props('result')).toBe(route);
    await modes.trigger('keydown', { key: 'End' });
    expect(summary.props('profileVisible')).toBe(false);
    await modes.trigger('keydown', { key: 'Home' });
    expect(summary.props('profileVisible')).toBe(true);
  });

  it.each(['success', 'failure', 'different plan'] as const)(
    'updates the active saved identity only after deleting that plan: %s',
    async (outcome) => {
      const plan = { id: 1, name: 'Synthetic route', distanceM: 1000, centerLat: 47, centerLng: 8, createDate: '' };
      vi.mocked(listPlannedTracks).mockResolvedValue([plan]);
      vi.mocked(loadPlannedTrack).mockResolvedValueOnce({
        name: plan.name,
        profile: 'trekking',
        coordinates: [
          [8, 47, 500],
          [8.01, 47.01, 510],
        ],
        waypoints: [
          { lat: 47, lng: 8 },
          { lat: 47.01, lng: 8.01 },
        ],
        stats: { distanceM: 1000, ascentM: 10, descentM: 0, legCount: 1 },
      });
      const { wrapper, waypointCount } = await mountPlanner();
      await wrapper.get('[data-section="load"]').trigger('click');
      await flushPromises();
      await wrapper.get('.planner-plan-open').trigger('click');
      await flushPromises();
      if (outcome === 'different plan') vi.mocked(listPlannedTracks).mockResolvedValueOnce([{ ...plan, id: 2 }]);
      if (outcome === 'failure') {
        vi.mocked(deletePlannedTrack).mockRejectedValueOnce(new Error('offline'));
        vi.spyOn(console, 'warn').mockImplementation(() => undefined);
      }
      await wrapper.get('[aria-label="Back to Planner"]').trigger('click');
      await wrapper.get('[data-section="load"]').trigger('click');
      await flushPromises();
      await wrapper.get('.planner-plan-delete').trigger('click');
      await wrapper
        .findAll('[role="dialog"] button')
        .find((button) => button.text() === 'Delete plan')!
        .trigger('click');
      await flushPromises();
      if (outcome === 'failure') {
        expect(wrapper.text()).toContain('Could not delete this saved plan');
        await wrapper
          .findAll('[role="dialog"] button')
          .find((button) => button.text() === 'Cancel')!
          .trigger('click');
      }
      await wrapper.get('[aria-label="Back to Planner"]').trigger('click');
      await wrapper.get('[data-section="draw"]').trigger('click');
      expect(waypointCount()).toBe(2);
      expect(wrapper.get('.planner-save-button').attributes('aria-label')).toBe(
        outcome === 'success' ? 'Save plan: Unsaved changes' : 'Save copy: Saved plan'
      );
      await wrapper.get('.planner-save-button').trigger('click');
      expect(wrapper.get('[role="dialog"]').text().includes('stays in your saved plans')).toBe(outcome !== 'success');
      vi.mocked(listPlannedTracks).mockResolvedValue([]);
    }
  );

  it('cancels pending map attachment when leaving drawing before the map loads', async () => {
    const { wrapper, sources, listeners, fire } = await mountPlanner(false);
    await wrapper.get('[data-section="draw"]').trigger('click');
    expect(listeners.get('load')?.size).toBe(1);
    await wrapper.get('[aria-label="Back to Planner"]').trigger('click');
    expect(listeners.get('load')?.size).toBe(0);
    fire('load');
    expect(sources.size).toBe(0);
  });

  it('does not activate drawing when a delayed config response arrives after closing', async () => {
    let finishConfig!: (value: Awaited<ReturnType<typeof fetchPlannerConfig>>) => void;
    vi.mocked(fetchPlannerConfig).mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          finishConfig = resolve;
        })
    );
    const map = createMap();
    const wrapper = shallowMount(PlannerTool, {
      props: { map: map.map },
      global: { stubs: { BottomSheet: BottomSheetStub } },
    });
    const opening = wrapper.vm.open();
    wrapper.vm.close();
    finishConfig({ profiles: ['trekking'], defaultProfile: 'trekking', maxWaypoints: 50, maxPointsOfInterest: 1000 });
    await opening;
    expect(wrapper.vm.isOpen()).toBe(false);
    expect(map.listeners.get('click')?.size ?? 0).toBe(0);
  });
  function pointEvent(x: number, y: number, touch = false) {
    const touches = { length: 1, item: () => ({ identifier: 1 }) };
    return {
      point: { x, y },
      lngLat: { lat: y / 1000 + 47, lng: x / 1000 + 8 },
      preventDefault: vi.fn(),
      originalEvent: touch
        ? { touches, changedTouches: touches, preventDefault: vi.fn(), stopPropagation: vi.fn() }
        : { button: 0, preventDefault: vi.fn(), stopPropagation: vi.fn() },
    };
  }

  async function openSyntheticPlan(pointCount = 2) {
    vi.mocked(listPlannedTracks).mockResolvedValueOnce([
      { id: 1, name: 'Test plan', distanceM: 1000, centerLat: 47, centerLng: 8, createDate: '' },
    ]);
    vi.mocked(loadPlannedTrack).mockResolvedValueOnce({
      name: 'Test plan',
      description: 'Original notes',
      profile: 'trekking',
      stats: { distanceM: 1000, ascentM: 100, descentM: 0, legCount: pointCount - 1 },
      waypoints: Array.from({ length: pointCount }, (_, i) => ({
        lat: 47 + (0.1 * i) / (pointCount - 1),
        lng: 8 + (0.1 * i) / (pointCount - 1),
      })),
      coordinates: Array.from({ length: pointCount }, (_, i) => [
        8 + (0.1 * i) / (pointCount - 1),
        47 + (0.1 * i) / (pointCount - 1),
        100 + (100 * i) / (pointCount - 1),
      ]),
    });
    const mounted = await mountPlanner();
    await mounted.wrapper.get('[data-section="load"]').trigger('click');
    await flushPromises();
    await mounted.wrapper.get('.planner-plan-open').trigger('click');
    await flushPromises();
    return mounted;
  }

  it.each([false, true])('moves a point without jumping or adding a point on release (touch=%s)', async (touch) => {
    const { wrapper, fire, sources, waypointCount, map } = await openSyntheticPlan();
    expect(map.fitBounds).toHaveBeenCalledOnce();
    expect(wrapper.get('.planner-save-button').attributes('aria-label')).toBe('Save copy: Saved plan');
    expect(wrapper.find('.planner-unsaved-dot').exists()).toBe(false);
    fire('click', pointEvent(0, 0));
    await nextTick();
    expect(document.querySelector('.planner-point-menu')).not.toBeNull();
    expect(document.querySelector('.planner-action-menu__hint')?.textContent).toContain('Drag this point to move it.');
    vi.advanceTimersByTime(400);
    fire(touch ? 'touchstart' : 'mousedown', pointEvent(12, 0, touch));
    fire(touch ? 'touchmove' : 'mousemove', pointEvent(52, 40, touch));
    fire(touch ? 'touchend' : 'mouseup', pointEvent(52, 40, touch));
    fire('click', pointEvent(52, 40));
    await nextTick();
    expect(document.querySelector('.planner-point-menu')).toBeNull();
    expect(waypointCount()).toBe(2);
    const features = (
      sources.get(PLANNER_WAYPOINT_SOURCE_ID)?.data as { features: { geometry: { coordinates: number[] } }[] }
    ).features;
    expect(features[0].geometry.coordinates).toEqual([8.04, 47.04]);
    expect(localStorage.getItem(STORAGE_KEYS.plannerPointDragHintDone)).toBe('true');
    expect(map.dragPan.enable).toHaveBeenCalled();
    expect(wrapper.get('.planner-save-button').attributes('disabled')).toBeDefined();
    expect(wrapper.find('.planner-unsaved-dot').exists()).toBe(true);
    await wrapper.get('[aria-label="Undo"]').trigger('click');
    expect(wrapper.get('.planner-save-button').attributes('aria-label')).toBe('Save copy: Saved plan');
    expect(wrapper.find('.planner-unsaved-dot').exists()).toBe(false);
    vi.advanceTimersByTime(1300);
    fire('click', pointEvent(0, 0));
    await nextTick();
    expect(document.querySelector('.planner-point-menu')).not.toBeNull();
    expect(document.querySelector('.planner-action-menu__hint')).toBeNull();
  });

  it('dismisses the drag hint without editing the route and remembers it on a new mount', async () => {
    const { wrapper, fire, sources } = await openSyntheticPlan();
    fire('click', pointEvent(0, 0));
    await nextTick();
    const before = JSON.stringify(sources.get(PLANNER_WAYPOINT_SOURCE_ID)?.data);
    await new DOMWrapper(document.querySelector<HTMLButtonElement>('[aria-label="Dismiss drag hint"]')!).trigger(
      'click'
    );
    expect(document.querySelector('.planner-point-menu')).not.toBeNull();
    expect(document.querySelector('.planner-action-menu__hint')).toBeNull();
    expect(localStorage.getItem(STORAGE_KEYS.plannerPointDragHintDone)).toBe('true');
    expect(JSON.stringify(sources.get(PLANNER_WAYPOINT_SOURCE_ID)?.data)).toBe(before);
    expect(wrapper.find('.planner-unsaved-dot').exists()).toBe(false);
    wrapper.unmount();
    const reopened = await openSyntheticPlan();
    reopened.fire('click', pointEvent(0, 0));
    await nextTick();
    expect(document.querySelector('.planner-point-menu')).not.toBeNull();
    expect(document.querySelector('.planner-action-menu__hint')).toBeNull();
  });

  it('keeps the drag hint after a cancelled touch drag', async () => {
    const { fire, sources } = await openSyntheticPlan();
    fire('touchstart', pointEvent(0, 0, true));
    fire('touchmove', pointEvent(50, 50, true));
    fire('touchcancel', pointEvent(50, 50, true));
    await nextTick();
    expect(localStorage.getItem(STORAGE_KEYS.plannerPointDragHintDone)).toBeNull();
    const features = (
      sources.get(PLANNER_WAYPOINT_SOURCE_ID)?.data as { features: { geometry: { coordinates: number[] } }[] }
    ).features;
    expect(features[0].geometry.coordinates).toEqual([8, 47]);
    vi.advanceTimersByTime(1300);
    fire('click', pointEvent(0, 0));
    await nextTick();
    expect(document.querySelector('.planner-action-menu__hint')?.textContent).toContain('Drag this point to move it.');
  });

  it('offers tap-to-move and cancelling does not change the point', async () => {
    const { fire, sources } = await openSyntheticPlan();
    fire('click', pointEvent(0, 0));
    await nextTick();
    await clickMenuAction('.planner-point-menu', 'Move to…');
    expect(document.querySelector('.planner-point-menu')?.textContent).toContain('Tap the map at the new position');
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    await nextTick();
    expect(document.querySelector('.planner-point-menu')).toBeNull();
    vi.advanceTimersByTime(400);
    fire('click', pointEvent(0, 0));
    await nextTick();
    await clickMenuAction('.planner-point-menu', 'Move to…');
    fire('click', pointEvent(50, 50));
    await nextTick();
    const features = (
      sources.get(PLANNER_WAYPOINT_SOURCE_ID)?.data as { features: { geometry: { coordinates: number[] } }[] }
    ).features;
    expect(features).toHaveLength(2);
    expect(features[0].geometry.coordinates).toEqual([8.05, 47.05]);
    expect(document.querySelector('.planner-point-menu')).toBeNull();
  });

  it.each([false, true])('opens point actions only after a completed tap (touch=%s)', async (touch) => {
    const { wrapper, fire, waypointCount } = await openSyntheticPlan(3);
    fire(touch ? 'touchstart' : 'mousedown', pointEvent(50, 50, touch));
    await nextTick();
    expect(document.querySelector('.planner-point-menu')).toBeNull();
    fire(touch ? 'touchend' : 'mouseup', pointEvent(50, 50, touch));
    await nextTick();
    expect(
      wrapper
        .findComponent(PlannerActionMenu)
        .props('actions')
        .map((action) => action.label)
    ).toEqual(['Start route here', 'End route here', 'Move to…', 'Remove point']);
    fire('click', pointEvent(50, 50));
    expect(waypointCount()).toBe(3);
    expect(sheetSnapTo).not.toHaveBeenCalled();
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', cancelable: true }));
    await nextTick();
    expect(document.querySelector('.planner-point-menu')).toBeNull();
  });

  it.each(['Start route here', 'End route here', 'Remove point'])(
    'edits directly at the selected point with Undo: %s',
    async (label) => {
      const { wrapper, fire, waypointCount, sources } = await openSyntheticPlan(3);
      fire('click', pointEvent(50, 50));
      await nextTick();
      await clickMenuAction('.planner-point-menu', label);
      expect(waypointCount()).toBe(2);
      expect(document.querySelector('.planner-point-menu')).toBeNull();
      const features = (
        sources.get(PLANNER_WAYPOINT_SOURCE_ID)?.data as { features: { geometry: { coordinates: number[] } }[] }
      ).features;
      if (label === 'Start route here') expect(features[0].geometry.coordinates).toEqual([8.05, 47.05]);
      if (label === 'End route here') expect(features.at(-1)?.geometry.coordinates).toEqual([8.05, 47.05]);
      await wrapper.get('[aria-label="Undo"]').trigger('click');
      expect(waypointCount()).toBe(3);
    }
  );

  it('dismisses on the map without adding a point or inserting one on the route', async () => {
    const { fire, waypointCount, map } = await openSyntheticPlan();
    fire('click', pointEvent(0, 0));
    await nextTick();
    vi.advanceTimersByTime(400);
    map.queryRenderedFeatures.mockReturnValue([{ properties: { legIndex: 0 } }]);
    fire('mousedown', pointEvent(50, 50));
    fire('mouseup', pointEvent(50, 50));
    fire('click', pointEvent(50, 50));
    await nextTick();
    expect(waypointCount()).toBe(2);
    expect(document.querySelector('.planner-point-menu')).toBeNull();
  });

  it('keeps route-wide actions in a menu without resizing and selects points from the keyboard', async () => {
    const { wrapper, fire, sources, map } = await openSyntheticPlan();
    await wrapper.get('[aria-label="Route tools"]').trigger('click');
    const menu = wrapper.findComponent(PlannerActionMenu);
    expect(menu.props('actions').map((action) => action.label)).toEqual(['Reverse route', 'Clear route']);
    expect(sheetSnapTo).not.toHaveBeenCalled();
    await clickMenuAction('.planner-route-menu', 'Reverse route');
    const features = (
      sources.get(PLANNER_WAYPOINT_SOURCE_ID)?.data as {
        features: { geometry: { coordinates: number[] }; properties: { id: string } }[];
      }
    ).features;
    expect(features[0].geometry.coordinates).toEqual([8.1, 47.1]);
    const canvas = map.getCanvas();
    document.body.appendChild(canvas);
    canvas.focus();
    canvas.dispatchEvent(
      new KeyboardEvent('keydown', { key: 'ArrowRight', altKey: true, bubbles: true, cancelable: true })
    );
    canvas.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }));
    await nextTick();
    expect(document.querySelector('.planner-point-menu')?.textContent).toContain('Move to…');
    expect(document.querySelector('.planner-point-menu')?.textContent).not.toContain('Start route here');
    fire('contextmenu', pointEvent(100, 100));
    await nextTick();
    expect(document.querySelector('.planner-point-menu')).not.toBeNull();
    expect(wrapper.find('.planner-point-list').exists()).toBe(false);
    expect(canvas.getAttribute('aria-keyshortcuts')).toContain('Alt+ArrowRight');
    wrapper.unmount();
    expect(canvas.hasAttribute('aria-keyshortcuts')).toBe(false);
    expect(canvas.hasAttribute('aria-describedby')).toBe(false);
    canvas.remove();
  });

  it.each([false, true])('dismisses route Tools on the map without editing the route (pointer=%s)', async (pointer) => {
    const { wrapper, fire, waypointCount, map } = await openSyntheticPlan();
    await wrapper.get('[aria-label="Route tools"]').trigger('click');
    map.queryRenderedFeatures.mockReturnValue([{ properties: { legIndex: 0 } }]);
    if (pointer) {
      fire('mousedown', pointEvent(50, 50));
      fire('mouseup', pointEvent(50, 50));
    }
    fire('click', pointEvent(50, 50));
    await nextTick();
    expect(document.querySelector('.planner-route-menu')).toBeNull();
    expect(waypointCount()).toBe(2);
    expect(wrapper.find('.planner-unsaved-dot').exists()).toBe(false);
  });

  it('guards an unsaved draft on unload and releases the guard after undo or unmount', async () => {
    const { wrapper, fire } = await openSyntheticPlan();
    const leaving = () => {
      const event = new Event('beforeunload', { cancelable: true });
      window.dispatchEvent(event);
      return event.defaultPrevented;
    };
    expect(leaving()).toBe(false);
    fire('click', pointEvent(50, 50));
    await nextTick();
    expect(leaving()).toBe(true);
    await wrapper.get('[aria-label="Undo"]').trigger('click');
    expect(leaving()).toBe(false);
    fire('click', pointEvent(60, 60));
    await nextTick();
    expect(leaving()).toBe(true);
    wrapper.unmount();
    expect(leaving()).toBe(false);
  });

  it('retains save input on failure, prevents duplicate submits, and reports a successful copy', async () => {
    vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    vi.mocked(savePlannedRoute).mockRejectedValueOnce(new Error('offline'));
    const { wrapper } = await openSyntheticPlan();
    expect(wrapper.get('.planner-save-button').text()).toContain('Save copy');
    await wrapper.get('.planner-save-button').trigger('click');
    await wrapper.get('input').setValue('Edited plan');
    await wrapper
      .get('[role="dialog"]')
      .findAll('button')
      .find((b) => b.text() === 'Save copy')!
      .trigger('click');
    await flushPromises();
    expect(wrapper.get('[role="dialog"]').text()).toContain('Could not save this plan');
    expect((wrapper.get('input').element as HTMLInputElement).value).toBe('Edited plan');
    let finish!: (value: Awaited<ReturnType<typeof savePlannedRoute>>) => void;
    vi.mocked(savePlannedRoute).mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          finish = resolve;
        })
    );
    const save = wrapper
      .get('[role="dialog"]')
      .findAll('button')
      .find((b) => b.text() === 'Save copy')!;
    await save.trigger('click');
    await wrapper.get('input').trigger('keyup.enter');
    expect(savePlannedRoute).toHaveBeenCalledTimes(2);
    finish({ id: 2, name: 'Edited plan', distanceM: 1000 });
    await flushPromises();
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
    expect(toastAdd).toHaveBeenCalledWith(
      expect.objectContaining({ severity: 'success', summary: 'Plan saved', detail: 'Edited plan', life: 4000 })
    );
    expect(toastAdd).toHaveBeenCalledTimes(1);
    expect(wrapper.find('.planner-notice').exists()).toBe(false);
    expect(wrapper.findAll('button').some((button) => button.text() === 'Download GPX')).toBe(false);
    expect(wrapper.get('h2').text()).toBe('Plan a map');
    expect(wrapper.vm.isOpen()).toBe(true);
    expect(wrapper.get('.planner-save-button').attributes('aria-label')).toBe('Save copy: Saved plan');
    expect(wrapper.find('.planner-unsaved-dot').exists()).toBe(false);
    expect(savePlannedRoute).toHaveBeenLastCalledWith(
      expect.objectContaining({ name: 'Edited plan', description: 'Original notes' })
    );
    await wrapper.get('[aria-label="Back to Planner"]').trigger('click');
    expect(wrapper.get('h2').text()).toBe('Planner');
    expect(wrapper.get('[data-section="draw"]').text()).toContain('Continue your current plan');
    await wrapper.get('[data-section="draw"]').trigger('click');
    expect(wrapper.get('h2').text()).toBe('Plan a map');
    expect(wrapper.find('.planner-notice').exists()).toBe(false);
    expect(toastAdd).toHaveBeenCalledTimes(1);
    wrapper.vm.close();
    expect(wrapper.vm.isOpen()).toBe(false);
  });

  it('keeps a draft until replacement is chosen and preserves it when loading fails', async () => {
    const { wrapper, fire, waypointCount } = await mountPlanner();
    await wrapper.get('[data-section="draw"]').trigger('click');
    fire('click', pointEvent(0, 0));
    await nextTick();
    vi.mocked(listPlannedTracks).mockResolvedValueOnce([
      { id: 1, name: 'Test plan', distanceM: 1000, centerLat: 47, centerLng: 8, createDate: '' },
    ]);
    await wrapper.get('[aria-label="Back to Planner"]').trigger('click');
    await wrapper.get('[data-section="load"]').trigger('click');
    await flushPromises();
    await wrapper.get('.planner-plan-open').trigger('click');
    expect(loadPlannedTrack).not.toHaveBeenCalled();
    expect(wrapper.get('[role="dialog"]').text()).toContain('unsaved changes');
    vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    vi.mocked(loadPlannedTrack).mockRejectedValueOnce(new Error('offline'));
    await wrapper
      .findAll('button')
      .find((b) => b.text() === 'Replace plan')!
      .trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('Could not open this saved plan');
    await wrapper.get('.planner-return-draft').trigger('click');
    expect(waypointCount()).toBe(1);
  });

  it('retries the failed saved-plan detail and preserves the draft until a request succeeds', async () => {
    const { wrapper, fire, waypointCount } = await mountPlanner();
    await wrapper.get('[data-section="draw"]').trigger('click');
    fire('click', pointEvent(0, 0));
    await nextTick();
    vi.mocked(listPlannedTracks).mockResolvedValueOnce([
      { id: 7, name: 'Retry plan', distanceM: 1000, centerLat: 47, centerLng: 8, createDate: '' },
    ]);
    await wrapper.get('[aria-label="Back to Planner"]').trigger('click');
    await wrapper.get('[data-section="load"]').trigger('click');
    await flushPromises();
    await wrapper.get('.planner-plan-open').trigger('click');
    vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    vi.mocked(loadPlannedTrack).mockRejectedValueOnce(new Error('offline'));
    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Replace plan')!
      .trigger('click');
    await flushPromises();
    vi.mocked(loadPlannedTrack).mockRejectedValueOnce(new Error('still offline'));
    await wrapper.get('[role="alert"] button').trigger('click');
    await flushPromises();
    expect(loadPlannedTrack).toHaveBeenLastCalledWith(7);
    expect(wrapper.get('[role="alert"]').text()).toContain('Retry');
    await wrapper.get('.planner-return-draft').trigger('click');
    expect(waypointCount()).toBe(1);
    vi.mocked(listPlannedTracks).mockResolvedValueOnce([
      { id: 7, name: 'Retry plan', distanceM: 1000, centerLat: 47, centerLng: 8, createDate: '' },
    ]);
    await wrapper.get('[aria-label="Back to Planner"]').trigger('click');
    await wrapper.get('[data-section="load"]').trigger('click');
    await flushPromises();
    await wrapper.get('.planner-plan-open').trigger('click');
    vi.mocked(loadPlannedTrack).mockRejectedValueOnce(new Error('offline again'));
    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Replace plan')!
      .trigger('click');
    await flushPromises();
    vi.mocked(loadPlannedTrack).mockResolvedValueOnce({
      name: 'Retry plan',
      profile: 'trekking',
      waypoints: [
        { lat: 47, lng: 8 },
        { lat: 47.1, lng: 8.1 },
      ],
      coordinates: [
        [8, 47],
        [8.1, 47.1],
      ],
    });
    await wrapper.get('[role="alert"] button').trigger('click');
    await flushPromises();
    expect(loadPlannedTrack).toHaveBeenCalledTimes(4);
    expect(loadPlannedTrack).toHaveBeenLastCalledWith(7);
    expect(listPlannedTracks).toHaveBeenCalledTimes(2);
    expect(waypointCount()).toBe(2);
    expect(wrapper.find('.planner-load-error').exists()).toBe(false);
    expect(wrapper.get('.panel-title').text()).toBe('Plan a map');
  });

  const syntheticPoi = {
    id: 'dc77b107-a507-4ead-81ae-3e61c567d73d',
    lat: 47,
    lng: 8,
    name: 'Meet here',
    iconType: 'viewpoint' as const,
    notes: 'Bring lunch',
  };

  async function openPoiPlan() {
    vi.mocked(listPlannedTracks).mockResolvedValueOnce([
      {
        id: 2,
        name: 'Places to visit',
        distanceM: 0,
        centerLat: 47,
        centerLng: 8,
        createDate: '',
        hasRoute: false,
        pointOfInterestCount: 1,
      },
    ]);
    vi.mocked(loadPlannedTrack).mockResolvedValueOnce({
      name: 'Places to visit',
      profile: 'trekking',
      waypoints: [],
      coordinates: [],
      pointsOfInterest: [{ ...syntheticPoi }],
      stats: { distanceM: 0, ascentM: 0, descentM: 0, legCount: 0 },
    });
    const mounted = await mountPlanner();
    await mounted.wrapper.get('[data-section="load"]').trigger('click');
    await flushPromises();
    expect(mounted.wrapper.get('.planner-plan-meta').text()).toContain('1 POI');
    expect(mounted.wrapper.find('.planner-plan-export').exists()).toBe(false);
    await mounted.wrapper.get('.planner-plan-edit').trigger('click');
    await flushPromises();
    expect(loadPlannedTrack).toHaveBeenCalledWith(2);
    return mounted;
  }

  function poiFeatures(sources: ReturnType<typeof createMap>['sources']) {
    return (
      sources.get(PLANNER_POI_SOURCE_ID)?.data as {
        features: {
          properties: { id: string; name: string; iconImage: string };
          geometry: { coordinates: number[] };
        }[];
      }
    ).features;
  }

  it.each([false, true])('moves POIs with one undo step and suppresses the release click (touch=%s)', async (touch) => {
    const { wrapper, fire, sources, map } = await openPoiPlan();
    fire(touch ? 'touchstart' : 'mousedown', pointEvent(12, 0, touch));
    fire(touch ? 'touchmove' : 'mousemove', pointEvent(52, 40, touch));
    fire(touch ? 'touchend' : 'mouseup', pointEvent(52, 40, touch));
    fire('click', pointEvent(52, 40));
    await nextTick();
    expect(poiFeatures(sources)).toHaveLength(1);
    expect(poiFeatures(sources)[0].geometry.coordinates).toEqual([8.04, 47.04]);
    expect(poiFeatures(sources)[0].properties.id).toBe(syntheticPoi.id);
    expect(poiFeatures(sources)[0].properties.iconImage).toBe('mtl-planner-poi-icon-viewpoint');
    expect(wrapper.findComponent(PlannerPoiEditor).props('point')).toBeNull();
    expect(map.dragPan.enable).toHaveBeenCalled();
    expect(wrapper.get('.planner-save-button').attributes('disabled')).toBeUndefined();
    await wrapper.get('[aria-label="Undo"]').trigger('click');
    expect(poiFeatures(sources)[0].geometry.coordinates).toEqual([8, 47]);
    expect(wrapper.get('.planner-save-button').attributes('aria-label')).toBe('Save copy: Saved plan');
  });

  it('cancels a POI touch drag without changing the saved point', async () => {
    const { wrapper, fire, sources, map } = await openPoiPlan();
    fire('touchstart', pointEvent(0, 0, true));
    fire('touchmove', pointEvent(50, 50, true));
    fire('touchcancel', pointEvent(50, 50, true));
    await nextTick();
    expect(poiFeatures(sources)[0].geometry.coordinates).toEqual([8, 47]);
    expect(wrapper.get('.planner-save-button').attributes('aria-label')).toBe('Save copy: Saved plan');
    expect(map.touchZoomRotate.enable).toHaveBeenCalled();
  });

  it('saves stable POI IDs and notes with a POI-only plan, without offering GPX', async () => {
    const { wrapper, fire, sources } = await openPoiPlan();
    fire('click', pointEvent(0, 0));
    await nextTick();
    await wrapper.get('[data-plan-mode="poi"]').trigger('click');
    expect(wrapper.get('.planner-poi-selected').text()).toContain(syntheticPoi.name);
    await wrapper
      .findAll('button')
      .find((b) => b.text() === 'Edit POI')!
      .trigger('click');
    wrapper
      .findComponent(PlannerPoiEditor)
      .vm.$emit('save', { ...syntheticPoi, name: 'Updated stop', notes: 'New notes' });
    await nextTick();
    vi.mocked(savePlannedRoute).mockResolvedValueOnce({ id: 3, name: 'Places to visit (copy)', distanceM: 0 });
    await wrapper.get('.planner-save-button').trigger('click');
    await wrapper
      .get('[role="dialog"]')
      .findAll('button')
      .find((b) => b.text() === 'Save copy')!
      .trigger('click');
    await flushPromises();
    expect(savePlannedRoute).toHaveBeenLastCalledWith(
      expect.objectContaining({
        coordinates: [],
        pointsOfInterest: [{ ...syntheticPoi, name: 'Updated stop', notes: 'New notes' }],
      })
    );
    expect(toastAdd).toHaveBeenCalledWith(
      expect.objectContaining({ severity: 'success', summary: 'Plan saved', detail: 'Places to visit (copy)' })
    );
    expect(wrapper.find('.planner-notice').exists()).toBe(false);
    expect(wrapper.findAll('button').some((b) => b.text() === 'Download GPX')).toBe(false);
    expect(poiFeatures(sources)[0].properties.name).toBe('Updated stop');
  });

  it('adds and cancels POIs without changing the loaded route, then clears them when another plan loads', async () => {
    const { wrapper, fire, sources } = await openSyntheticPlan();
    const route = structuredClone(sources.get(PLANNER_SOURCE_ID)!.data);
    await wrapper
      .findAll('button')
      .find((b) => b.text() === 'Points of interest')!
      .trigger('click');
    fire('click', pointEvent(50, 50));
    await nextTick();
    wrapper.findComponent(PlannerPoiEditor).vm.$emit('cancel');
    await nextTick();
    expect(poiFeatures(sources)).toHaveLength(0);
    expect(wrapper.get('.planner-save-button').attributes('aria-label')).toBe('Save copy: Saved plan');
    fire('click', pointEvent(50, 50));
    await nextTick();
    wrapper.findComponent(PlannerPoiEditor).vm.$emit('save', { ...syntheticPoi, id: '', lat: 47.05, lng: 8.05 });
    await nextTick();
    expect(poiFeatures(sources)).toHaveLength(1);
    expect(poiFeatures(sources)[0].properties.id).toMatch(/^[0-9a-f-]{36}$/);
    expect(sources.get(PLANNER_SOURCE_ID)!.data).toEqual(route);
    expect(wrapper.get('.planner-save-button').attributes('disabled')).toBeUndefined();
    vi.mocked(listPlannedTracks).mockResolvedValueOnce([
      {
        id: 4,
        name: 'Other plan',
        distanceM: 0,
        centerLat: 47,
        centerLng: 8,
        createDate: '',
        hasRoute: false,
        pointOfInterestCount: 0,
      },
    ]);
    await wrapper.get('[aria-label="Back to Planner"]').trigger('click');
    await wrapper.get('[data-section="load"]').trigger('click');
    await flushPromises();
    vi.mocked(listPlannedTracks).mockResolvedValue([]);
    vi.mocked(loadPlannedTrack).mockResolvedValueOnce({ coordinates: [], waypoints: [], pointsOfInterest: [] });
    // The second saved plan still prompts before replacing the current POI draft.
    await wrapper.get('.planner-plan-edit').trigger('click');
    expect(loadPlannedTrack).toHaveBeenCalledTimes(1);
    await wrapper
      .findAll('button')
      .find((b) => b.text() === 'Replace plan')!
      .trigger('click');
    await flushPromises();
    expect(poiFeatures(sources)).toHaveLength(0);
  });
});
