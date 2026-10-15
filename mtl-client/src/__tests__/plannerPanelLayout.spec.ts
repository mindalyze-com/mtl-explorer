import { flushPromises, mount, shallowMount } from '@vue/test-utils';
import { defineComponent, nextTick, ref } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { BottomSheetLayoutState } from '@/components/ui/BottomSheet.vue';
import BottomSheet from '@/components/ui/BottomSheet.vue';
import PlannerSummary from '@/planner/components/PlannerSummary.vue';
import {
  availablePlannerSize,
  classifyPlannerDensity,
  parsePlannerPanelPreferences,
  plannerContentFits,
  plannerContentMinimums,
  plannerEntrySize,
  plannerPanelSizeHeights,
  plannerProfileHeightPx,
  usePlannerPanelLayout,
  DEFAULT_PLANNER_PANEL_PREFERENCES,
} from '@/planner/composables/usePlannerPanelLayout';
import { PLANNER_TIP_MS } from '@/planner/constants/PlannerConstants';
import { STORAGE_KEYS } from '@/utils/appStorage';

const phone = {
  viewportHeightPx: 844,
  wide: false,
  chromePx: 62,
  insetPx: 34,
  noticePx: 0,
  reducedRowPx: 44,
  fullRowPx: 80,
};

describe('planner panel sizes', () => {
  it('derives named phone sizes from measured chrome, totals and readable profiles', () => {
    expect(plannerPanelSizeHeights(phone)).toEqual([
      { id: 'totals', heightPx: 152 },
      { id: 'compact', heightPx: 244 },
      { id: 'full', heightPx: 462 },
      { id: 'enlarged', heightPx: 776 },
    ]);
  });

  it('places totals beside the profile on wide panels and drops sizes that cannot stay distinct', () => {
    const sizes = plannerPanelSizeHeights({ ...phone, viewportHeightPx: 390, wide: true, chromePx: 50, insetPx: 8 });
    expect(sizes).toEqual([
      { id: 'totals', heightPx: 114 },
      { id: 'compact', heightPx: 158 },
      { id: 'full', heightPx: 359 },
    ]);
    expect(availablePlannerSize('enlarged', sizes)).toBe('full');
    expect(availablePlannerSize('compact', sizes)).toBe('compact');
  });

  it('adds only the space a visible notice needs', () => {
    const sizes = plannerPanelSizeHeights({ ...phone, noticePx: 40 });
    expect(sizes.find((size) => size.id === 'totals')?.heightPx).toBe(152 + 44);
    expect(sizes.find((size) => size.id === 'compact')?.heightPx).toBe(244 + 44);
  });
  it('fits desktop controls and a readable chart instead of taking half a tall viewport', () => {
    const desktop = { ...phone, desktop: true, wide: true, controlsPx: 174, fullProfilePx: 120 };
    for (const viewportHeightPx of [1080, 2160]) {
      expect(
        plannerPanelSizeHeights({ ...desktop, viewportHeightPx }).find((size) => size.id === 'full')?.heightPx
      ).toBe(486);
    }
  });
  it('reserves only placeholder space before routing and removes profile space in POI mode', () => {
    const desktop = { ...phone, desktop: true, wide: true, controlsPx: 174, viewportHeightPx: 1080 };
    expect(
      plannerPanelSizeHeights({ ...desktop, fullRowPx: 32, fullProfilePx: 20 }).find((size) => size.id === 'full')
        ?.heightPx
    ).toBe(338);
    expect(
      plannerPanelSizeHeights({ ...desktop, fullRowPx: 0, controlsPx: 196, fullProfilePx: 0 }).find(
        (size) => size.id === 'full'
      )?.heightPx
    ).toBe(304);
  });

  it('keeps a full size with tools on very short viewports and drops reduced sizes first', () => {
    const sizes = plannerPanelSizeHeights({ ...phone, viewportHeightPx: 200 });
    expect(sizes).toEqual([
      { id: 'totals', heightPx: 152 },
      { id: 'full', heightPx: 196 },
    ]);
    expect(availablePlannerSize('enlarged', sizes)).toBe('full');
    expect(availablePlannerSize('full', sizes)).toBe('full');
    expect(availablePlannerSize('compact', sizes)).toBe('totals');
    expect(plannerPanelSizeHeights({ ...phone, viewportHeightPx: 120 }).map((size) => size.id)).toEqual(['full']);
  });

  it('switches density at measured content minimums, with the buffer only when growing', () => {
    const minimums = plannerContentMinimums(phone);
    expect(minimums).toEqual({ totals: 152, compact: 244, full: 338, fullWithProfile: 462 });
    expect(classifyPlannerDensity(244, minimums, 'compact')).toBe('compact');
    expect(classifyPlannerDensity(243, minimums, 'compact')).toBe('totals');
    expect(classifyPlannerDensity(255, minimums, 'totals')).toBe('totals');
    expect(classifyPlannerDensity(256, minimums, 'totals')).toBe('compact');
    expect(classifyPlannerDensity(349, minimums, 'compact')).toBe('compact');
    expect(classifyPlannerDensity(350, minimums, 'compact')).toBe('full');
    expect(classifyPlannerDensity(338, minimums, 'full')).toBe('full');
    expect(classifyPlannerDensity(337, minimums, 'full')).toBe('compact');
    expect(classifyPlannerDensity(400, minimums, 'totals')).toBe('full');
  });

  it('shows content at rest once it fits and waits for the buffer while dragging', () => {
    expect(plannerContentFits(462, 462, false, false)).toBe(true);
    expect(plannerContentFits(470, 462, false, true)).toBe(false);
    expect(plannerContentFits(474, 462, false, true)).toBe(true);
    expect(plannerContentFits(462, 462, true, true)).toBe(true);
    expect(plannerContentFits(460, 462, true, true)).toBe(false);
  });

  it('includes the readable chart and tool space at larger text sizes', () => {
    const minimums = plannerContentMinimums({ ...phone, reducedRowPx: 66, fullRowPx: 120, textScale: 1.5 });
    expect(plannerProfileHeightPx('compact', 1.5)).toBe(132);
    expect(minimums).toEqual({ totals: 180, compact: 318, full: 459, fullWithProfile: 645 });
  });
});

describe('planner panel preferences', () => {
  it('accepts only known values', () => {
    expect(parsePlannerPanelPreferences('compact')).toEqual(DEFAULT_PLANNER_PANEL_PREFERENCES);
    expect(parsePlannerPanelPreferences({ size: 'huge', lastFullSize: 'giant', reduceTipDone: 'yes' })).toEqual(
      DEFAULT_PLANNER_PANEL_PREFERENCES
    );
    expect(
      parsePlannerPanelPreferences({
        size: 'totals',
        lastFullSize: 'enlarged',
        fullProfileCollapsed: true,
        reduceTipDone: true,
        returnTipDone: true,
      })
    ).toEqual({
      size: 'totals',
      lastFullSize: 'enlarged',
      heightFraction: null,
      lastFullHeightFraction: null,
      reduceTipDone: true,
      returnTipDone: true,
    });
  });

  it('opens with all tools on first use and restores the chosen size later', () => {
    expect(plannerEntrySize(DEFAULT_PLANNER_PANEL_PREFERENCES)).toBe('full');
    expect(plannerEntrySize({ ...DEFAULT_PLANNER_PANEL_PREFERENCES, size: 'totals' })).toBe('totals');
  });

  it('validates custom viewport fractions without accepting non-finite or out-of-range values', () => {
    expect(parsePlannerPanelPreferences({ heightFraction: 0.63, lastFullHeightFraction: 0.71 })).toMatchObject({
      heightFraction: 0.63,
      lastFullHeightFraction: 0.71,
    });
    for (const value of [0, -1, 2, Infinity, NaN, '0.5']) {
      expect(parsePlannerPanelPreferences({ heightFraction: value, lastFullHeightFraction: value })).toMatchObject({
        heightFraction: null,
        lastFullHeightFraction: null,
      });
    }
  });
});

describe('usePlannerPanelLayout', () => {
  const snapTo = vi.fn(() => true);
  const resizeTo = vi.fn(() => true);

  function mountLayout(options: { hasRoute?: boolean } = {}) {
    const drawing = ref(false);
    const hasRoute = ref(options.hasRoute ?? false);
    let layout!: ReturnType<typeof usePlannerPanelLayout>;
    const wrapper = mount(
      defineComponent({
        setup() {
          layout = usePlannerPanelLayout({ sheet: ref({ snapTo, resizeTo }), summaryEl: ref(null), drawing, hasRoute });
          return {};
        },
        template: '<div />',
      })
    );
    return { wrapper, drawing, hasRoute, layout: () => layout };
  }

  function layoutState(overrides: Partial<BottomSheetLayoutState>): BottomSheetLayoutState {
    return {
      open: true,
      detentId: 'full',
      fullscreen: false,
      dragging: false,
      heightPx: 462,
      widthPx: 390,
      topPx: 382,
      rightPx: 390,
      bottomPx: 844,
      leftPx: 0,
      ...overrides,
    };
  }

  beforeEach(() => {
    localStorage.clear();
    snapTo.mockClear();
    resizeTo.mockClear();
    Object.defineProperty(window, 'innerHeight', { configurable: true, value: 844 });
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 390 });
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('opens desktop at the fitted full size while retaining manual heights for Show tools', async () => {
    vi.stubGlobal(
      'matchMedia',
      vi.fn(() => ({ matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() }))
    );
    localStorage.setItem(
      STORAGE_KEYS.plannerPanelPreferences,
      JSON.stringify({
        size: 'enlarged',
        heightFraction: 0.8,
        lastFullHeightFraction: 0.8,
      })
    );
    const { wrapper, drawing, layout } = mountLayout();
    drawing.value = true;
    await nextTick();
    expect(layout().entrySize.value).toBe('full');
    layout().onLayoutChange(layoutState({ heightPx: 462 }));
    expect(resizeTo).not.toHaveBeenCalled();
    layout().onResizeEnd(600);
    layout().showMoreMap();
    layout().showTools();
    expect(resizeTo).toHaveBeenLastCalledWith(600);
    wrapper.unmount();
  });

  it('restores tools at the measured full minimum without drag hysteresis after resizing', async () => {
    const { wrapper, drawing, layout } = mountLayout({ hasRoute: true });
    drawing.value = true;
    await nextTick();
    layout().setSummaryMetrics({ rowPx: 80, noticePx: 0, statsDensity: 'full' });
    layout().onLayoutChange(layoutState({ detentId: 'compact', heightPx: 244 }));
    expect(layout().density.value).toBe('compact');
    // Default chrome 64 + inset 8 + summary padding 12 + row 80 + tools 150.
    layout().onLayoutChange(layoutState({ detentId: '', heightPx: 314, dragging: true }));
    expect(layout().density.value).toBe('compact');
    layout().onLayoutChange(layoutState({ detentId: '', heightPx: 314, dragging: false }));
    expect(layout().density.value).toBe('full');
    wrapper.unmount();
  });

  it('uses the fitted full size when a remembered desktop height cannot fit phone tools and profile', async () => {
    localStorage.setItem(
      STORAGE_KEYS.plannerPanelPreferences,
      JSON.stringify({ size: 'compact', lastFullHeightFraction: 0.3 })
    );
    const { wrapper, drawing, layout } = mountLayout({ hasRoute: true });
    drawing.value = true;
    await nextTick();
    layout().onLayoutChange(layoutState({ detentId: 'compact', heightPx: 244 }));
    snapTo.mockClear();
    resizeTo.mockClear();
    layout().showTools();
    expect(snapTo).toHaveBeenLastCalledWith('full');
    expect(resizeTo).not.toHaveBeenCalled();
    layout().setSummaryMetrics({ rowPx: 80, noticePx: 0, statsDensity: 'full' });
    layout().onLayoutChange(layoutState({ detentId: 'full', heightPx: 438 }));
    expect(layout().density.value).toBe('full');
    wrapper.unmount();
  });
  it('includes body padding when fitting all desktop controls and instructions', async () => {
    vi.stubGlobal(
      'matchMedia',
      vi.fn(() => ({ matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() }))
    );
    const originalRect = HTMLElement.prototype.getBoundingClientRect;
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(function () {
      return this.classList.contains('sheet-drag-zone')
        ? { x: 0, y: 0, top: 0, left: 0, right: 0, bottom: 62, width: 0, height: 62, toJSON() {} }
        : originalRect.call(this);
    });
    let layout!: ReturnType<typeof usePlannerPanelLayout>;
    const wrapper = mount(
      defineComponent({
        setup() {
          const summary = ref<HTMLElement | null>(null);
          layout = usePlannerPanelLayout({
            sheet: ref({ snapTo, resizeTo }),
            summaryEl: summary,
            drawing: ref(true),
            hasRoute: ref(false),
          });
          return { summary };
        },
        template:
          '<div class="sheet" style="padding-bottom:8px"><div class="sheet-drag-zone" /><div class="sheet-body" style="padding-bottom:14px"><div ref="summary" /></div></div>',
      }),
      { attachTo: document.body }
    );
    layout.setSummaryMetrics({ rowPx: 32, noticePx: 0, statsDensity: 'full', controlsPx: 174, fullProfilePx: 20 });
    layout.onLayoutChange(layoutState({ widthPx: 920 }));
    await nextTick();
    expect(layout.sizes.value.find((size) => size.id === 'full')?.heightPx).toBe(326);
    wrapper.unmount();
  });

  it('retains the workspace when entering drawing and restores it after reducing a maximized panel', async () => {
    const { wrapper, drawing, layout } = mountLayout();
    layout().onLayoutChange(layoutState({ detentId: 'hub', heightPx: 540 }));
    drawing.value = true;
    await nextTick();
    expect(snapTo).not.toHaveBeenCalled();
    expect(resizeTo).not.toHaveBeenCalled();
    layout().onLayoutChange(layoutState({ detentId: 'hub', heightPx: 844, fullscreen: true }));
    layout().showMoreMap();
    layout().onLayoutChange(layoutState({ detentId: 'compact', heightPx: 220 }));
    layout().showTools();
    expect(resizeTo).toHaveBeenLastCalledWith(540);
    wrapper.unmount();
  });

  it('keeps a full workspace when route, POI and notice measurements change', async () => {
    const { wrapper, drawing, layout } = mountLayout();
    drawing.value = true;
    await nextTick();
    layout().onLayoutChange(layoutState({ detentId: 'full', heightPx: 540 }));
    layout().setSummaryMetrics({
      rowPx: 0,
      noticePx: 0,
      statsDensity: 'full',
      controlsPx: 196,
      fullProfilePx: 0,
      rowVisible: false,
    });
    await nextTick();
    layout().setSummaryMetrics({ rowPx: 80, noticePx: 40, statsDensity: 'full', controlsPx: 174, fullProfilePx: 120 });
    await nextTick();
    expect(snapTo).not.toHaveBeenCalled();
    expect(resizeTo).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it('repeats More map and returns to the last full size', async () => {
    const { drawing, layout } = mountLayout();
    drawing.value = true;
    await nextTick();
    expect(layout().entrySize.value).toBe('full');

    layout().showMoreMap();
    layout().onDetentChange('compact');
    layout().onLayoutChange(layoutState({ dragging: true, detentId: 'compact', heightPx: 500 }));
    layout().onLayoutChange(layoutState({ detentId: 'enlarged', heightPx: 776 }));
    layout().onDetentChange('enlarged');
    layout().showMoreMap();
    layout().onDetentChange('compact');
    layout().showTools();
    await nextTick();

    expect(snapTo.mock.calls).toEqual([['compact'], ['compact'], ['enlarged']]);
    expect(JSON.parse(localStorage.getItem(STORAGE_KEYS.plannerPanelPreferences)!)).toMatchObject({
      size: 'enlarged',
      lastFullSize: 'enlarged',
      reduceTipDone: true,
    });
  });

  it('keeps saved choices when a shorter viewport resolves them to another size', async () => {
    const saved = { ...DEFAULT_PLANNER_PANEL_PREFERENCES, size: 'enlarged', lastFullSize: 'enlarged' };
    localStorage.setItem(STORAGE_KEYS.plannerPanelPreferences, JSON.stringify(saved));
    Object.defineProperty(window, 'innerHeight', { configurable: true, value: 390 });
    const { drawing, layout } = mountLayout();
    drawing.value = true;
    await nextTick();
    expect(layout().entrySize.value).toBe('full');
    layout().onDetentChange('full');
    layout().showTools();
    layout().onDetentChange('full');

    expect(snapTo.mock.calls).toEqual([['full']]);
    expect(JSON.parse(localStorage.getItem(STORAGE_KEYS.plannerPanelPreferences)!)).toMatchObject({
      size: 'enlarged',
      lastFullSize: 'enlarged',
    });
  });

  it('keeps a reduced choice on a viewport too short for it and still reaches the tools', async () => {
    localStorage.setItem(STORAGE_KEYS.plannerPanelPreferences, JSON.stringify({ size: 'compact' }));
    Object.defineProperty(window, 'innerHeight', { configurable: true, value: 200 });
    const { drawing, layout } = mountLayout();
    drawing.value = true;
    await nextTick();
    expect(layout().entrySize.value).toBe('totals');
    layout().onDetentChange('totals');
    expect(layout().preferences.value.size).toBe('compact');

    layout().showTools();
    expect(snapTo).toHaveBeenLastCalledWith('full');
  });

  it('resolves the saved size with text changes made while the planner was closed', async () => {
    localStorage.setItem(STORAGE_KEYS.plannerPanelPreferences, JSON.stringify({ size: 'compact' }));
    const { wrapper, drawing, layout } = mountLayout();
    const previous = document.documentElement.style.fontSize;
    try {
      document.documentElement.style.fontSize = '24px';
      drawing.value = true;
      await nextTick();
      expect(layout().entrySize.value).toBe('compact');
      expect(layout().summaryLayout.value.textScale).toBe(1.5);
      expect(layout().sizes.value.find((size) => size.id === 'compact')?.heightPx).toBe(294);
    } finally {
      wrapper.unmount();
      document.documentElement.style.fontSize = previous;
    }
  });

  it('drops the profile, then the extra totals, before tools lose their space', async () => {
    const { drawing, layout } = mountLayout();
    drawing.value = true;
    await nextTick();

    layout().onLayoutChange(layoutState({ detentId: 'full', heightPx: 420 }));
    expect(layout().summaryLayout.value).toMatchObject({ statsDensity: 'full', profileVisible: true });
    layout().onLayoutChange(layoutState({ detentId: 'full', heightPx: 300 }));
    expect(layout().summaryLayout.value).toMatchObject({ statsDensity: 'full', profileVisible: false });
    layout().onLayoutChange(layoutState({ detentId: 'full', heightPx: 250 }));
    expect(layout().summaryLayout.value).toMatchObject({
      statsDensity: 'reduced',
      profileVisible: false,
      essentialActionsOnly: true,
    });
    layout().onLayoutChange(layoutState({ detentId: 'compact', heightPx: 220 }));
    expect(layout().summaryLayout.value).toMatchObject({ statsDensity: 'reduced', profileVisible: true });
  });

  it('reserves a usable tools viewport and keeps short full rows out of compact measurements', async () => {
    const { wrapper, drawing, layout } = mountLayout();
    drawing.value = true;
    await nextTick();
    layout().onLayoutChange(layoutState({ detentId: 'full', heightPx: 184 }));
    expect(layout().summaryLayout.value).toMatchObject({ essentialActionsOnly: true });
    const before = layout().sizes.value.find((size) => size.id === 'compact')?.heightPx;
    layout().setSummaryMetrics({ rowPx: 44, noticePx: 0, statsDensity: 'reduced', essentialActionsOnly: true });
    expect(layout().sizes.value.find((size) => size.id === 'compact')?.heightPx).toBe(before);
    wrapper.unmount();
  });

  it('applies new measurements after the real sheet receives its updated detents', async () => {
    vi.stubGlobal(
      'ResizeObserver',
      class {
        observe() {}
        disconnect() {}
      }
    );
    vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => setTimeout(() => callback(0), 0));
    vi.stubGlobal('cancelAnimationFrame', clearTimeout);
    const drawing = ref(false);
    let panel!: ReturnType<typeof usePlannerPanelLayout>;
    const wrapper = mount(
      defineComponent({
        components: { BottomSheet },
        setup() {
          const sheet = ref(null);
          panel = usePlannerPanelLayout({ sheet, summaryEl: ref(null), drawing, hasRoute: ref(true) });
          return { sheet, panel, drawing };
        },
        template: `<BottomSheet ref="sheet" :model-value="true" title="Planner measurement test"
        :detents="[{ id: 'hub', height: '480px' }, ...panel.detents.value]"
        initial-detent="hub"
        :fit-small-overflow="false" @detent-change="panel.onDetentChange">Tools</BottomSheet>`,
      }),
      { attachTo: document.body }
    );
    try {
      await nextTick();
      await flushPromises();
      await nextTick();
      drawing.value = true;
      await nextTick();
      await flushPromises();
      await nextTick();
      panel.showMoreMap();
      await nextTick();
      await flushPromises();
      await nextTick();
      panel.onLayoutChange(layoutState({ detentId: 'compact', heightPx: 220 }));
      panel.setSummaryMetrics({ rowPx: 72, noticePx: 0, statsDensity: 'reduced' });
      await nextTick();
      await flushPromises();
      await nextTick();
      expect(panel.sizes.value.find((size) => size.id === 'compact')?.heightPx).toBe(248);
      expect(wrapper.findComponent(BottomSheet).props('detents')).toContainEqual({ id: 'compact', height: '248px' });
      expect(document.querySelector<HTMLElement>('.sheet')?.style.height).toBe('248px');
    } finally {
      wrapper.unmount();
      vi.unstubAllGlobals();
    }
  });

  it('follows height while dragging and the named size at rest', async () => {
    const { drawing, layout } = mountLayout();
    drawing.value = true;
    await nextTick();

    layout().onLayoutChange(layoutState({ dragging: true, heightPx: 250 }));
    expect(layout().density.value).toBe('compact');
    layout().onLayoutChange(layoutState({ dragging: true, heightPx: 219 }));
    expect(layout().density.value).toBe('totals');
    layout().onLayoutChange(layoutState({ detentId: 'compact', heightPx: 244 }));
    expect(layout().density.value).toBe('compact');
    layout().onLayoutChange(layoutState({ detentId: 'compact', heightPx: 844, fullscreen: true }));
    expect(layout().density.value).toBe('full');
  });

  it('keeps the real sheet at a custom height through measurements, compact mode, and reopening', async () => {
    vi.stubGlobal(
      'ResizeObserver',
      class {
        observe() {}
        disconnect() {}
      }
    );
    vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => setTimeout(() => callback(0), 0));
    vi.stubGlobal('cancelAnimationFrame', clearTimeout);
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(function () {
      const height = this.classList.contains('sheet') ? Number.parseFloat(this.style.height) || 0 : 0;
      return {
        x: 0,
        y: 844 - height,
        top: 844 - height,
        bottom: 844,
        left: 0,
        right: 390,
        width: 390,
        height,
        toJSON() {},
      };
    });
    localStorage.setItem(
      STORAGE_KEYS.plannerPanelPreferences,
      JSON.stringify({ size: 'full', heightFraction: 603 / 844, lastFullHeightFraction: 603 / 844 })
    );
    const drawing = ref(false);
    let panel!: ReturnType<typeof usePlannerPanelLayout>;
    const wrapper = mount(
      defineComponent({
        components: { BottomSheet },
        setup() {
          const sheet = ref(null);
          panel = usePlannerPanelLayout({ sheet, summaryEl: ref(null), drawing, hasRoute: ref(true) });
          return { sheet, panel, drawing };
        },
        template: `<BottomSheet ref="sheet" :model-value="true" title="Custom planner height"
        :detents="[{ id: 'hub', height: '603px' }, ...panel.detents.value]"
        initial-detent="hub"
        :fit-small-overflow="false" :drag-to-close="false" :free-resize="drawing"
        @layout-change="panel.onLayoutChange" @detent-change="panel.onDetentChange"
        @resize-end="panel.onResizeEnd">Tools</BottomSheet>`,
      }),
      { attachTo: document.body }
    );
    const settle = async () => {
      await nextTick();
      await flushPromises();
      await new Promise((resolve) => setTimeout(resolve, 15));
    };
    try {
      await settle();
      drawing.value = true;
      await settle();
      const sheet = document.querySelector<HTMLElement>('.sheet')!;
      expect(sheet.style.height).toBe('603px');
      panel.setSummaryMetrics({ rowPx: 70, noticePx: 30, statsDensity: 'full' });
      await settle();
      expect(sheet.style.height).toBe('603px');
      panel.showMoreMap();
      await settle();
      expect(panel.density.value).toBe('compact');
      panel.showTools();
      await settle();
      expect(sheet.style.height).toBe('603px');
      expect(panel.density.value).toBe('full');
      drawing.value = false;
      await settle();
      drawing.value = true;
      await settle();
      expect(sheet.style.height).toBe('603px');
    } finally {
      wrapper.unmount();
      vi.unstubAllGlobals();
    }
  });

  it('retains arbitrary heights, switches mini layouts by height, and restores the custom tools size', async () => {
    const { wrapper, drawing, layout } = mountLayout();
    drawing.value = true;
    await nextTick();
    layout().onLayoutChange(layoutState({ detentId: '', heightPx: 603 }));
    layout().onResizeEnd(603);
    expect(layout().density.value).toBe('full');
    expect(layout().preferences.value).toMatchObject({ heightFraction: 603 / 844, lastFullHeightFraction: 603 / 844 });

    layout().setSummaryMetrics({ rowPx: 52, noticePx: 0, statsDensity: 'full' });
    await nextTick();
    expect(snapTo).not.toHaveBeenCalled();
    expect(resizeTo).not.toHaveBeenCalled();

    layout().onLayoutChange(layoutState({ dragging: true, detentId: '', heightPx: 250 }));
    layout().onResizeEnd(250);
    layout().onLayoutChange(layoutState({ detentId: '', heightPx: 250 }));
    expect(layout().density.value).toBe('compact');
    layout().onLayoutChange(layoutState({ detentId: '', heightPx: 170 }));
    layout().onResizeEnd(170);
    expect(layout().density.value).toBe('totals');
    expect(layout().preferences.value.lastFullHeightFraction).toBe(603 / 844);

    layout().showTools();
    expect(resizeTo).toHaveBeenLastCalledWith(603);
    expect(layout().density.value).toBe('full');
    layout().showMoreMap();
    expect(layout().preferences.value.heightFraction).toBeNull();
    layout().showTools();
    expect(resizeTo).toHaveBeenLastCalledWith(603);
    wrapper.unmount();
  });

  it('restores custom heights on reopening and rotation without overwriting the saved fraction', async () => {
    localStorage.setItem(STORAGE_KEYS.plannerPanelPreferences, JSON.stringify({ size: 'full', heightFraction: 0.7 }));
    const { wrapper, drawing, layout } = mountLayout();
    drawing.value = true;
    await nextTick();
    layout().onDetentChange('full');
    layout().onLayoutChange(layoutState({ heightPx: 422 }));
    expect(resizeTo).toHaveBeenLastCalledWith(844 * 0.7);
    layout().onLayoutChange(layoutState({ detentId: '', heightPx: 844 * 0.7 }));
    Object.defineProperty(window, 'innerHeight', { configurable: true, value: 390 });
    layout().onLayoutChange(layoutState({ detentId: '', heightPx: 382 }));
    await nextTick();
    expect(resizeTo).toHaveBeenLastCalledWith(390 * 0.7);
    expect(layout().preferences.value.heightFraction).toBe(0.7);
    wrapper.unmount();
  });

  it('keeps tools reachable at the cap of a very short viewport', async () => {
    Object.defineProperty(window, 'innerHeight', { configurable: true, value: 200 });
    const { wrapper, drawing, layout } = mountLayout();
    drawing.value = true;
    await nextTick();
    layout().onLayoutChange(layoutState({ detentId: '', heightPx: 196 }));
    layout().onResizeEnd(196);
    expect(layout().density.value).toBe('full');
    expect(layout().summaryLayout.value.essentialActionsOnly).toBe(true);
    wrapper.unmount();
  });
  it('shows elevation automatically when space returns and ignores the removed collapse preference', async () => {
    localStorage.setItem(
      STORAGE_KEYS.plannerPanelPreferences,
      JSON.stringify({ size: 'full', fullProfileCollapsed: true })
    );
    const { wrapper, drawing, layout } = mountLayout({ hasRoute: true });
    drawing.value = true;
    await nextTick();
    layout().onLayoutChange(layoutState({ heightPx: 500 }));
    expect(layout().summaryLayout.value.profileVisible).toBe(true);
    layout().onLayoutChange(layoutState({ detentId: 'totals', heightPx: 140 }));
    expect(layout().summaryLayout.value.profileVisible).toBe(false);
    layout().onLayoutChange(layoutState({ heightPx: 500 }));
    expect(layout().summaryLayout.value.profileVisible).toBe(true);
    expect(layout().preferences.value).not.toHaveProperty('fullProfileCollapsed');
    wrapper.unmount();
  });

  it('restores a custom height after temporary tool expansion unless the user resizes', async () => {
    const { wrapper, drawing, layout } = mountLayout();
    drawing.value = true;
    await nextTick();
    layout().onLayoutChange(layoutState({ detentId: '', heightPx: 603 }));
    layout().onResizeEnd(603);
    layout().expandTemporarily();
    layout().onLayoutChange(layoutState({ detentId: 'enlarged', heightPx: 776 }));
    layout().endTemporaryExpansion();
    expect(resizeTo).toHaveBeenLastCalledWith(603);
    layout().onLayoutChange(layoutState({ detentId: '', heightPx: 603 }));
    layout().expandTemporarily();
    layout().onResizeEnd(550);
    layout().onLayoutChange(layoutState({ detentId: '', heightPx: 550 }));
    resizeTo.mockClear();
    layout().endTemporaryExpansion();
    expect(resizeTo).not.toHaveBeenCalled();
    expect(layout().preferences.value.heightFraction).toBe(550 / 844);
    wrapper.unmount();
  });

  it('ignores size changes outside drawing and restores a saved size on entry', async () => {
    localStorage.setItem(STORAGE_KEYS.plannerPanelPreferences, JSON.stringify({ size: 'totals' }));
    const { drawing, layout } = mountLayout();
    layout().onDetentChange('compact');
    expect(JSON.parse(localStorage.getItem(STORAGE_KEYS.plannerPanelPreferences)!).size).toBe('totals');

    drawing.value = true;
    await nextTick();
    expect(layout().entrySize.value).toBe('totals');
    expect(layout().density.value).toBe('totals');
  });

  it('enlarges for open tools temporarily and keeps the saved size', async () => {
    const { drawing, layout } = mountLayout();
    drawing.value = true;
    await nextTick();
    layout().onDetentChange('full');
    layout().onLayoutChange(layoutState({ detentId: 'full', heightPx: 422 }));

    layout().expandTemporarily();
    layout().onDetentChange('enlarged');
    layout().onLayoutChange(layoutState({ detentId: 'enlarged', heightPx: 776 }));
    layout().endTemporaryExpansion();
    layout().onDetentChange('full');

    expect(snapTo.mock.calls).toEqual([['enlarged'], ['full']]);
    expect(layout().preferences.value).toMatchObject({ size: null, lastFullSize: 'full' });

    layout().onLayoutChange(layoutState({ detentId: 'full', heightPx: 422 }));
    layout().expandTemporarily();
    layout().onDetentChange('compact');
    layout().onLayoutChange(layoutState({ detentId: 'compact', heightPx: 220 }));
    layout().endTemporaryExpansion();
    expect(snapTo).toHaveBeenCalledTimes(3);
    expect(layout().preferences.value.size).toBe('compact');
  });

  it('shows each tip once and lets both leave on their own', async () => {
    vi.useFakeTimers();
    const { drawing, layout } = mountLayout({ hasRoute: true });
    layout().preferences.value = { ...DEFAULT_PLANNER_PANEL_PREFERENCES, size: 'full' };
    drawing.value = true;
    await nextTick();
    expect(layout().activeTip.value).toBe('reduce');
    vi.advanceTimersByTime(PLANNER_TIP_MS);
    expect(layout().activeTip.value).toBeNull();

    layout().onDetentChange('compact');
    await nextTick();
    expect(layout().activeTip.value).toBe('return');
    layout().dismissTip();
    expect(layout().activeTip.value).toBeNull();

    layout().onDetentChange('full');
    await nextTick();
    layout().onDetentChange('totals');
    await nextTick();
    expect(layout().activeTip.value).toBeNull();
    expect(layout().preferences.value).toMatchObject({ reduceTipDone: true, returnTipDone: true });
  });

  it('keeps working when storage is unavailable', async () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('quota');
    });
    const { drawing, layout } = mountLayout();
    drawing.value = true;
    await nextTick();
    expect(() => layout().onDetentChange('compact')).not.toThrow();
    expect(layout().preferences.value.size).toBe('compact');
  });
});

describe('PlannerSummary', () => {
  const result = {
    stats: { distanceM: 1000, ascentM: 10, descentM: 5, durationSec: 600, legCount: 1, anyLegCached: false },
    coordinates: [
      [8, 47, 500],
      [8.01, 47.01, 510],
    ] as [number, number, number][],
  };

  function mountSummary(props: Record<string, unknown>, slots: Record<string, string> = {}) {
    return shallowMount(PlannerSummary, {
      slots,
      props: {
        density: 'totals',
        wide: false,
        result,
        showStats: true,
        hint: 'Tap the map',
        updating: false,
        error: null,
        canRetry: true,
        viewportTooLarge: false,
        retryDisabled: false,
        canUndo: true,
        selectionAction: null,
        statsDensity: 'reduced',
        profileVisible: false,
        ...props,
      },
    });
  }

  it('labels a retained result visibly, separate from the error text', () => {
    const failed = mountSummary({ error: 'Routing service is unavailable.' });
    expect(failed.get('.planner-summary__status').text()).toBe('Previous result');
    expect(failed.get('[role="alert"]').text()).toBe('Routing service is unavailable.');
    expect(mountSummary({ updating: true }).get('.planner-summary__status').text()).toBe('Updating');
    expect(mountSummary({}).get('.planner-summary__status').text()).toBe('');
  });

  it('keeps one chart instance while the profile is hidden', async () => {
    const summary = mountSummary({ density: 'compact', profileVisible: true });
    const chart = summary.findComponent({ name: 'ElevationProfile' });
    await summary.setProps({ density: 'totals', profileVisible: false });
    expect(summary.findComponent({ name: 'ElevationProfile' }).vm).toBe(chart.vm);
    expect((summary.get('#planner-summary-profile').element as HTMLElement).style.display).toBe('none');
  });

  it('keeps expanded controls before totals and uses independent reduced markup', async () => {
    const summary = mountSummary(
      { density: 'full' },
      { controls: '<button aria-label="Undo">Undo</button><button aria-label="Redo">Redo</button>' }
    );
    expect(summary.get('.planner-summary__expanded').isVisible()).toBe(true);
    expect(summary.get('.planner-summary__compact').isVisible()).toBe(false);
    expect(summary.get('.planner-summary__controls [aria-label="Redo"]').exists()).toBe(true);
    expect(summary.get('.planner-summary__full-totals').find('button').exists()).toBe(false);
    expect(
      summary
        .get('.planner-summary__controls')
        .element.compareDocumentPosition(summary.get('.planner-summary__full-totals').element) &
        Node.DOCUMENT_POSITION_FOLLOWING
    ).toBeTruthy();
    await summary.setProps({ density: 'compact', profileVisible: true });
    expect((summary.get('.planner-summary__expanded').element as HTMLElement).style.display).toBe('none');
    expect((summary.get('.planner-summary__compact').element as HTMLElement).style.display).not.toBe('none');
    expect(summary.get('.planner-summary__compact [aria-label="Undo"]').exists()).toBe(true);
    expect(summary.get('.planner-summary__compact').find('[aria-label="Redo"]').exists()).toBe(false);
  });
});
