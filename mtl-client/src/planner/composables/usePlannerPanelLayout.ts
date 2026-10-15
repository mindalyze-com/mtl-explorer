import { computed, onBeforeUnmount, ref, watch, type Ref } from 'vue';
import type { BottomSheetLayoutState, DetentDef } from '@/components/ui/BottomSheet.vue';
import { readJsonStorage, STORAGE_KEYS, writeJsonStorage } from '@/utils/appStorage';
import { useMediaQuery } from '@/composables/useMediaQuery';
import {
  PLANNER_BASE_FONT_PX,
  PLANNER_DEFAULT_CHROME_PX,
  PLANNER_DEFAULT_INSET_PX,
  PLANNER_DEFAULT_ROW_PX,
  PLANNER_DESKTOP_MIN_WIDTH_PX,
  PLANNER_DENSITY_BUFFER_PX,
  PLANNER_ENLARGED_VIEWPORT_FRACTION,
  PLANNER_FULL_TOOLS_MIN_PX,
  PLANNER_FULL_VIEWPORT_FRACTION,
  PLANNER_MIN_SIZE_GAP_PX,
  PLANNER_PROFILE_COMPACT_HEIGHT_PX,
  PLANNER_PROFILE_FULL_HEIGHT_PX,
  PLANNER_SUMMARY_GAP_PX,
  PLANNER_SUMMARY_PADDING_PX,
  PLANNER_SHORT_TOOLS_MIN_PX,
  PLANNER_SHORT_VIEWPORT_FRACTION,
  PLANNER_TIP_MS,
  PLANNER_WIDE_SUMMARY_MIN_PX,
} from '@/planner/constants/PlannerConstants';

export type PlannerPanelSize = 'totals' | 'compact' | 'full' | 'enlarged';
export type PlannerPanelDensity = 'totals' | 'compact' | 'full';
export type PlannerFullSize = Extract<PlannerPanelSize, 'full' | 'enlarged'>;
export type PlannerPanelTip = 'reduce' | 'return';

export const PLANNER_PANEL_SIZES: readonly PlannerPanelSize[] = ['totals', 'compact', 'full', 'enlarged'];
const DENSITY_ORDER: readonly PlannerPanelDensity[] = ['totals', 'compact', 'full'];

export interface PlannerSummaryMetrics {
  /** Totals row, including Undo and the status line. */
  rowPx: number;
  /** Notices stacked above the totals row; zero when none are shown. */
  noticePx: number;
  /** Which totals were rendered, so each row height is remembered separately. */
  statsDensity: PlannerStatsDensity;
  textScale?: number;
  essentialActionsOnly?: boolean;
  controlsPx?: number;
  fullProfilePx?: number;
  rowVisible?: boolean;
}

export type PlannerStatsDensity = 'full' | 'reduced';

export interface PlannerPanelMetrics {
  viewportHeightPx: number;
  wide: boolean;
  /** Sheet chrome above the summary: grabber and header. */
  chromePx: number;
  /** Sheet padding below the content, including the safe area. */
  insetPx: number;
  noticePx: number;
  /** Totals row heights differ because full panels show more totals. */
  reducedRowPx: number;
  fullRowPx: number;
  textScale?: number;
  desktop?: boolean;
  controlsPx?: number;
  fullProfilePx?: number;
}

export interface PlannerPanelSizeHeight {
  id: PlannerPanelSize;
  heightPx: number;
}

/** Smallest panel heights at which each layout shows its content without clipping. */
export interface PlannerContentMinimums {
  totals: number;
  compact: number;
  /** Full totals plus some visible tools, without the profile. */
  full: number;
  fullWithProfile: number;
}

export interface PlannerPanelPreferences {
  size: PlannerPanelSize | null;
  lastFullSize: PlannerFullSize;
  /** Manual heights as a share of the visible viewport, so they also fit after rotation. */
  heightFraction: number | null;
  lastFullHeightFraction: number | null;
  reduceTipDone: boolean;
  returnTipDone: boolean;
}

export const DEFAULT_PLANNER_PANEL_PREFERENCES: Readonly<PlannerPanelPreferences> = {
  size: null,
  lastFullSize: 'full',
  heightFraction: null,
  lastFullHeightFraction: null,
  reduceTipDone: false,
  returnTipDone: false,
};

export function isPlannerPanelSize(value: unknown): value is PlannerPanelSize {
  return typeof value === 'string' && (PLANNER_PANEL_SIZES as readonly string[]).includes(value);
}

export function plannerSizeDensity(size: PlannerPanelSize): PlannerPanelDensity {
  return size === 'enlarged' ? 'full' : size;
}

export function plannerTextScale(): number {
  return (
    (Number.parseFloat(getComputedStyle(document.documentElement).fontSize) || PLANNER_BASE_FONT_PX) /
    PLANNER_BASE_FONT_PX
  );
}

export function plannerProfileHeightPx(density: PlannerPanelDensity, textScale = 1): number {
  if (density === 'full') return PLANNER_PROFILE_FULL_HEIGHT_PX * textScale;
  return density === 'compact' ? PLANNER_PROFILE_COMPACT_HEIGHT_PX * textScale : 0;
}

export function plannerSummaryHeightPx(
  density: PlannerPanelDensity,
  wide: boolean,
  metrics: Omit<PlannerSummaryMetrics, 'statsDensity'>
) {
  const scale = metrics.textScale ?? 1;
  const notices = metrics.noticePx > 0 ? metrics.noticePx + PLANNER_SUMMARY_GAP_PX * scale : 0;
  const profile =
    density === 'full'
      ? (metrics.fullProfilePx ?? plannerProfileHeightPx(density, scale))
      : plannerProfileHeightPx(density, scale);
  const content =
    profile === 0
      ? metrics.rowPx
      : wide
        ? Math.max(metrics.rowPx, profile)
        : metrics.rowPx + PLANNER_SUMMARY_GAP_PX * scale + profile;
  return 2 * PLANNER_SUMMARY_PADDING_PX * scale + notices + content;
}

export function plannerContentMinimums(metrics: PlannerPanelMetrics): PlannerContentMinimums {
  const basePx = metrics.chromePx + metrics.insetPx;
  const scale = metrics.textScale ?? 1;
  const reduced = { rowPx: metrics.reducedRowPx, noticePx: metrics.noticePx, textScale: scale };
  const full = {
    rowPx: metrics.fullRowPx,
    noticePx: metrics.noticePx,
    textScale: scale,
    fullProfilePx: metrics.desktop ? metrics.fullProfilePx : undefined,
  };
  return {
    totals: basePx + plannerSummaryHeightPx('totals', metrics.wide, reduced),
    compact: basePx + plannerSummaryHeightPx('compact', metrics.wide, reduced),
    full: basePx + plannerSummaryHeightPx('totals', metrics.wide, full) + PLANNER_FULL_TOOLS_MIN_PX * scale,
    fullWithProfile: basePx + plannerSummaryHeightPx('full', false, full) + PLANNER_FULL_TOOLS_MIN_PX * scale,
  };
}

/** Named drawing sizes, low to high. Full always exists so tools stay reachable; reduced sizes yield on short viewports. */
export function plannerPanelSizeHeights(metrics: PlannerPanelMetrics): PlannerPanelSizeHeight[] {
  const minimums = plannerContentMinimums(metrics);
  const preferredCapPx = Math.round(metrics.viewportHeightPx * PLANNER_ENLARGED_VIEWPORT_FRACTION);
  const capPx =
    preferredCapPx < minimums.totals + PLANNER_SHORT_TOOLS_MIN_PX * (metrics.textScale ?? 1)
      ? Math.round(metrics.viewportHeightPx * PLANNER_SHORT_VIEWPORT_FRACTION)
      : preferredCapPx;
  const preferredFull = metrics.desktop
    ? minimums.fullWithProfile +
      Math.max(0, (metrics.controlsPx ?? 0) - PLANNER_FULL_TOOLS_MIN_PX * (metrics.textScale ?? 1))
    : Math.max(minimums.fullWithProfile, metrics.viewportHeightPx * PLANNER_FULL_VIEWPORT_FRACTION);
  const full = Math.round(Math.min(preferredFull, capPx));
  const sizes: PlannerPanelSizeHeight[] = [];
  for (const id of ['totals', 'compact'] as const) {
    const heightPx = Math.round(minimums[id]);
    const previous = sizes.at(-1);
    if (full - heightPx < PLANNER_MIN_SIZE_GAP_PX) continue;
    if (previous && heightPx - previous.heightPx < PLANNER_MIN_SIZE_GAP_PX) continue;
    sizes.push({ id, heightPx });
  }
  sizes.push({ id: 'full', heightPx: full });
  if (capPx - full >= PLANNER_MIN_SIZE_GAP_PX) sizes.push({ id: 'enlarged', heightPx: capPx });
  return sizes;
}

export function plannerPanelDetents(sizes: readonly PlannerPanelSizeHeight[]): DetentDef[] {
  return sizes.map(({ id, heightPx }) => ({ id, height: `${heightPx}px` }));
}

/** Return the requested size, else one with the same density, else the closest smaller size. */
export function availablePlannerSize(
  requested: PlannerPanelSize,
  sizes: readonly PlannerPanelSizeHeight[]
): PlannerPanelSize {
  if (sizes.some((size) => size.id === requested)) return requested;
  const sameDensity = sizes.find((size) => plannerSizeDensity(size.id) === plannerSizeDensity(requested));
  if (sameDensity) return sameDensity.id;
  const requestedIndex = PLANNER_PANEL_SIZES.indexOf(requested);
  const smaller = [...sizes].reverse().find((size) => PLANNER_PANEL_SIZES.indexOf(size.id) <= requestedIndex);
  return smaller?.id ?? sizes[0]?.id ?? requested;
}

/** True when content fits; while dragging, newly shown content waits for the buffer so it does not flicker. */
export function plannerContentFits(
  heightPx: number,
  minimumPx: number,
  wasShown: boolean,
  dragging: boolean,
  bufferPx = PLANNER_DENSITY_BUFFER_PX
): boolean {
  return heightPx >= minimumPx + (dragging && !wasShown ? bufferPx : -1);
}

/** Live-drag density: each layout appears only once its content fits, plus the buffer when growing. */
export function classifyPlannerDensity(
  heightPx: number,
  minimums: PlannerContentMinimums,
  previous: PlannerPanelDensity,
  bufferPx = PLANNER_DENSITY_BUFFER_PX
): PlannerPanelDensity {
  const required = [minimums.compact, minimums.full];
  let level = DENSITY_ORDER.indexOf(previous);
  while (level < DENSITY_ORDER.length - 1 && heightPx >= required[level] + bufferPx) level++;
  while (level > 0 && heightPx < required[level - 1]) level--;
  return DENSITY_ORDER[level];
}

export function parsePlannerPanelPreferences(value: unknown): PlannerPanelPreferences {
  if (!value || typeof value !== 'object') return { ...DEFAULT_PLANNER_PANEL_PREFERENCES };
  const raw = value as Record<string, unknown>;
  return {
    size: isPlannerPanelSize(raw.size) ? raw.size : null,
    lastFullSize: raw.lastFullSize === 'enlarged' ? 'enlarged' : 'full',
    heightFraction: parseHeightFraction(raw.heightFraction),
    lastFullHeightFraction: parseHeightFraction(raw.lastFullHeightFraction),
    reduceTipDone: raw.reduceTipDone === true,
    returnTipDone: raw.returnTipDone === true,
  };
}

function parseHeightFraction(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) && value > 0 && value <= 1 ? value : null;
}

export function loadPlannerPanelPreferences(): PlannerPanelPreferences {
  return readJsonStorage(
    STORAGE_KEYS.plannerPanelPreferences,
    { ...DEFAULT_PLANNER_PANEL_PREFERENCES },
    parsePlannerPanelPreferences
  );
}

/** First use opens with all tools; later openings restore the last chosen size. */
export function plannerEntrySize(preferences: PlannerPanelPreferences): PlannerPanelSize {
  return preferences.size ?? 'full';
}

interface PlannerSheetHandle {
  snapTo(id: string): boolean;
  resizeTo(heightPx: number): boolean;
}

function visibleViewportHeight(): number {
  return Math.min(window.innerHeight, window.visualViewport?.height ?? window.innerHeight);
}

export function usePlannerPanelLayout(options: {
  sheet: Ref<PlannerSheetHandle | null>;
  summaryEl: Ref<HTMLElement | null>;
  drawing: Ref<boolean>;
  hasRoute: Ref<boolean>;
}) {
  const preferences = ref<PlannerPanelPreferences>(loadPlannerPanelPreferences());
  const desktop = useMediaQuery(
    `(min-width: ${PLANNER_DESKTOP_MIN_WIDTH_PX}px)`,
    window.innerWidth >= PLANNER_DESKTOP_MIN_WIDTH_PX
  );
  const layout = ref<BottomSheetLayoutState | null>(null);
  const viewportHeightPx = ref(visibleViewportHeight());
  const widthPx = ref(window.innerWidth);
  const textScale = ref(plannerTextScale());
  const chromePx = ref(PLANNER_DEFAULT_CHROME_PX);
  const insetPx = ref(PLANNER_DEFAULT_INSET_PX);
  const noticePx = ref(0);
  const reducedRowPx = ref(PLANNER_DEFAULT_ROW_PX * textScale.value);
  const fullRowPx = ref(PLANNER_DEFAULT_ROW_PX * textScale.value);
  const controlsPx = ref(0);
  const fullProfilePx = ref<number | undefined>(undefined);
  const density = ref<PlannerPanelDensity>('full');
  const entrySize = ref<PlannerPanelSize>('full');
  const activeTip = ref<PlannerPanelTip | null>(null);
  let tipTimer: number | undefined;
  /** Size to return to after tools temporarily enlarged the panel. */
  let temporaryRestore: { size: PlannerPanelSize; heightFraction: number | null } | null = null;
  let pendingHeightFraction: number | null = null;
  let workspaceHeightFraction: number | null = null;

  const wide = computed(() => widthPx.value >= PLANNER_WIDE_SUMMARY_MIN_PX * textScale.value);
  const sizes = computed(() =>
    plannerPanelSizeHeights({
      viewportHeightPx: viewportHeightPx.value,
      wide: wide.value,
      chromePx: chromePx.value,
      insetPx: insetPx.value,
      noticePx: noticePx.value,
      reducedRowPx: reducedRowPx.value,
      fullRowPx: fullRowPx.value,
      textScale: textScale.value,
      desktop: desktop.value,
      controlsPx: controlsPx.value,
      fullProfilePx: fullProfilePx.value,
    })
  );
  const detents = computed(() => plannerPanelDetents(sizes.value));
  const minimums = computed(() =>
    plannerContentMinimums({
      viewportHeightPx: viewportHeightPx.value,
      wide: wide.value,
      chromePx: chromePx.value,
      insetPx: insetPx.value,
      noticePx: noticePx.value,
      reducedRowPx: reducedRowPx.value,
      fullRowPx: fullRowPx.value,
      textScale: textScale.value,
      desktop: desktop.value,
      controlsPx: controlsPx.value,
      fullProfilePx: fullProfilePx.value,
    })
  );
  const reduced = computed(() => density.value !== 'full');
  const dragging = computed(() => Boolean(layout.value?.dragging));
  // A full panel on a short viewport keeps its tools by dropping the profile, then the extra totals.
  const fullTotalsShown = ref(true);
  const fullProfileShown = ref(true);
  const summaryLayout = computed(() => {
    const full = density.value === 'full';
    return {
      statsDensity: (full && fullTotalsShown.value ? 'full' : 'reduced') as PlannerStatsDensity,
      profileVisible: density.value === 'compact' || (full && fullProfileShown.value),
      essentialActionsOnly: full && !fullTotalsShown.value,
      textScale: textScale.value,
    };
  });
  /** Detent change expected from an automatic command; it must not overwrite the user's saved choice. */
  let automaticSize: string | null = null;

  function savePreferences(next: Partial<PlannerPanelPreferences>) {
    preferences.value = { ...preferences.value, ...next };
    writeJsonStorage(STORAGE_KEYS.plannerPanelPreferences, preferences.value);
  }

  function saveSizeChoice(size: PlannerPanelSize) {
    savePreferences({
      size,
      heightFraction: null,
      ...(size === 'full' || size === 'enlarged' ? { lastFullSize: size, lastFullHeightFraction: null } : {}),
    });
  }

  function saveCustomHeight(heightFraction: number) {
    const size = density.value;
    savePreferences({
      size,
      heightFraction,
      ...(size === 'full' ? { lastFullSize: 'full', lastFullHeightFraction: heightFraction } : {}),
    });
  }

  function measureChrome() {
    const summary = options.summaryEl.value;
    const sheet = summary?.closest<HTMLElement>('.sheet');
    if (!summary || !sheet) return;
    const measuredChrome = sheet.querySelector<HTMLElement>('.sheet-drag-zone')?.getBoundingClientRect().height ?? 0;
    if (measuredChrome > 0) chromePx.value = Math.ceil(measuredChrome);
    const sheetStyle = getComputedStyle(sheet);
    const body = sheet.querySelector<HTMLElement>('.sheet-body');
    const bodyStyle = body ? getComputedStyle(body) : null;
    const measuredInset =
      (Number.parseFloat(sheetStyle.paddingBottom) || 0) +
      (Number.parseFloat(bodyStyle?.paddingTop ?? '') || 0) +
      (Number.parseFloat(bodyStyle?.paddingBottom ?? '') || 0);
    if (Number.isFinite(measuredInset) && measuredInset > 0) insetPx.value = Math.ceil(measuredInset);
  }

  function updateFullFit(state: BottomSheetLayoutState) {
    const heightPx = state.fullscreen ? visibleViewportHeight() : state.heightPx;
    if (heightPx <= 0) return;
    fullTotalsShown.value = plannerContentFits(heightPx, minimums.value.full, fullTotalsShown.value, state.dragging);
    fullProfileShown.value = plannerContentFits(
      heightPx,
      minimums.value.fullWithProfile,
      fullProfileShown.value,
      state.dragging
    );
  }

  function densityAtHeight(heightPx: number, dragging = false): PlannerPanelDensity {
    const fullHeight = sizes.value.find((size) => size.id === 'full')?.heightPx ?? minimums.value.full;
    // The largest size still exposes tools when a short viewport cannot fit the usual full minimum.
    if (fullHeight < minimums.value.full && heightPx >= fullHeight - 1) return 'full';
    // Restored heights at the content minimum must show tools; the buffer is for live dragging.
    return classifyPlannerDensity(heightPx, minimums.value, density.value, dragging ? PLANNER_DENSITY_BUFFER_PX : 0);
  }

  function onLayoutChange(state: BottomSheetLayoutState) {
    layout.value = state;
    if (
      state.open &&
      !state.fullscreen &&
      !state.dragging &&
      state.heightPx > 0 &&
      densityAtHeight(state.heightPx) === 'full' &&
      (state.detentId === 'hub' || state.detentId === '')
    ) {
      workspaceHeightFraction = state.heightPx / visibleViewportHeight();
    }
    if (!state.open || !options.drawing.value) return;
    viewportHeightPx.value = visibleViewportHeight();
    if (state.widthPx > 0) widthPx.value = state.widthPx;
    if (state.fullscreen) {
      density.value = 'full';
      updateFullFit(state);
      return;
    }
    if (state.dragging) {
      automaticSize = null;
      density.value = densityAtHeight(state.heightPx, true);
      updateFullFit(state);
      return;
    }
    measureChrome();
    if (pendingHeightFraction !== null && resizeAutomatically(pendingHeightFraction)) {
      pendingHeightFraction = null;
      return;
    }
    if (isPlannerPanelSize(state.detentId)) {
      const named = plannerSizeDensity(state.detentId);
      // Viewport clamping can leave a compact panel too short for its profile.
      density.value = named === 'compact' && state.heightPx < minimums.value.compact - 1 ? 'totals' : named;
    } else {
      density.value = densityAtHeight(state.heightPx);
    }
    updateFullFit(state);
  }

  function onDetentChange(id: string) {
    if (!options.drawing.value || !isPlannerPanelSize(id)) return;
    density.value = plannerSizeDensity(id);
    const expected = automaticSize;
    automaticSize = null;
    if (expected === id) return;
    if (temporaryRestore !== null && id === 'enlarged') return;
    temporaryRestore = null;
    saveSizeChoice(id);
  }

  function onResizeEnd(heightPx: number) {
    if (!options.drawing.value || !Number.isFinite(heightPx) || heightPx <= 0) return;
    temporaryRestore = null;
    automaticSize = null;
    pendingHeightFraction = null;
    density.value = densityAtHeight(heightPx);
    saveCustomHeight(heightPx / visibleViewportHeight());
  }

  function updateTextScale(next: number) {
    if (Number.isFinite(next) && next > 0 && next !== textScale.value) {
      textScale.value = next;
      reducedRowPx.value = fullRowPx.value = PLANNER_DEFAULT_ROW_PX * next;
    }
  }

  function setSummaryMetrics(metrics: PlannerSummaryMetrics) {
    if (metrics.textScale) updateTextScale(metrics.textScale);
    if (layout.value?.dragging || (metrics.rowPx <= 0 && !(desktop.value && metrics.rowVisible === false))) return;
    if (metrics.controlsPx !== undefined) controlsPx.value = metrics.controlsPx;
    if (metrics.fullProfilePx !== undefined) fullProfilePx.value = metrics.fullProfilePx;
    noticePx.value = metrics.noticePx;
    if (metrics.essentialActionsOnly) return;
    if (metrics.statsDensity === 'full') fullRowPx.value = metrics.rowPx;
    else reducedRowPx.value = metrics.rowPx;
  }

  /** Resolve a size for this viewport without treating the resolution as a user choice. */
  function snapAutomatically(size: PlannerPanelSize): boolean {
    const resolved = availablePlannerSize(size, sizes.value);
    automaticSize = resolved;
    const accepted = options.sheet.value?.snapTo(resolved) ?? false;
    if (!accepted) automaticSize = null;
    return accepted;
  }

  function resizeAutomatically(heightFraction: number, requireTools = false): boolean {
    const heightPx = heightFraction * visibleViewportHeight();
    const accepted =
      options.sheet.value?.resizeTo(requireTools ? Math.max(heightPx, minimums.value.full) : heightPx) ?? false;
    // Measurements can change before the sheet emits its next layout. Do not reapply the previous named size.
    if (accepted && layout.value) layout.value = { ...layout.value, detentId: '' };
    return accepted;
  }

  function chooseSize(size: PlannerPanelSize) {
    temporaryRestore = null;
    pendingHeightFraction = null;
    if (snapAutomatically(size)) saveSizeChoice(size);
  }

  function showMoreMap() {
    const state = layout.value;
    if (
      state?.open &&
      workspaceHeightFraction !== null &&
      (state.detentId === 'hub' ||
        state.fullscreen ||
        (state.detentId === '' && preferences.value.heightFraction === null))
    ) {
      savePreferences({ lastFullHeightFraction: workspaceHeightFraction });
    }
    chooseSize('compact');
  }

  function showTools() {
    const fraction = preferences.value.lastFullHeightFraction;
    if (fraction !== null) {
      const fittedFull = sizes.value.find((size) => size.id === 'full');
      // A desktop height may be too short after returning to a narrow viewport.
      // The named size keeps adapting while the full controls are measured.
      if (fittedFull && fraction * visibleViewportHeight() < fittedFull.heightPx) {
        chooseSize('full');
        return;
      }
      temporaryRestore = null;
      pendingHeightFraction = null;
      if (resizeAutomatically(fraction, true)) {
        density.value = 'full';
        saveCustomHeight(fraction);
      }
      return;
    }
    chooseSize(preferences.value.lastFullSize);
  }

  /** Give open tools more room without changing the saved drawing size. */
  function expandTemporarily() {
    const state = layout.value;
    const enlarged = sizes.value.find((size) => size.id === 'enlarged');
    if (
      !options.drawing.value ||
      !state ||
      density.value !== 'full' ||
      !enlarged ||
      state.heightPx >= enlarged.heightPx
    )
      return;
    temporaryRestore = {
      size: isPlannerPanelSize(state.detentId) ? state.detentId : 'full',
      heightFraction: state.detentId === '' ? state.heightPx / visibleViewportHeight() : null,
    };
    if (!snapAutomatically('enlarged')) temporaryRestore = null;
  }

  function endTemporaryExpansion() {
    const restore = temporaryRestore;
    temporaryRestore = null;
    if (restore && layout.value?.detentId === 'enlarged') {
      if (restore.heightFraction !== null) resizeAutomatically(restore.heightFraction);
      else snapAutomatically(restore.size);
    }
  }

  function clearTipTimer() {
    if (tipTimer === undefined) return;
    window.clearTimeout(tipTimer);
    tipTimer = undefined;
  }

  function dismissTip() {
    clearTipTimer();
    activeTip.value = null;
  }

  function showTip(tip: PlannerPanelTip) {
    clearTipTimer();
    activeTip.value = tip;
    savePreferences(tip === 'reduce' ? { reduceTipDone: true } : { returnTipDone: true });
    tipTimer = window.setTimeout(() => {
      tipTimer = undefined;
      if (activeTip.value === tip) activeTip.value = null;
    }, PLANNER_TIP_MS);
  }

  watch(options.drawing, (drawing) => {
    temporaryRestore = null;
    automaticSize = null;
    pendingHeightFraction = null;
    if (!drawing) return;
    viewportHeightPx.value = visibleViewportHeight();
    updateTextScale(plannerTextScale());
    const state = layout.value;
    if (state?.open && state.heightPx > 0) {
      // Moving between sections retains the current workspace, including manual sizing.
      density.value = state.fullscreen ? 'full' : densityAtHeight(state.heightPx);
      updateFullFit(state);
      return;
    }
    entrySize.value = availablePlannerSize(desktop.value ? 'full' : plannerEntrySize(preferences.value), sizes.value);
    automaticSize = entrySize.value;
    pendingHeightFraction = desktop.value ? null : preferences.value.heightFraction;
    density.value = plannerSizeDensity(entrySize.value);
  });

  // Keep the current named size accurate when its measured content height changes.
  watch(
    sizes,
    (next) => {
      const state = layout.value;
      if (!options.drawing.value || !state?.open || state.dragging || state.fullscreen || state.detentId === 'full')
        return;
      const current = next.find((size) => size.id === state.detentId);
      if (current && Math.abs(current.heightPx - state.heightPx) > 1) snapAutomatically(current.id);
      // A reduced size that no longer fits resolves to its fallback without changing the saved choice.
      else if (!current && isPlannerPanelSize(state.detentId)) snapAutomatically(state.detentId);
    },
    { flush: 'post' }
  );

  watch(
    viewportHeightPx,
    () => {
      const state = layout.value;
      const fraction = preferences.value.heightFraction;
      if (
        options.drawing.value &&
        state?.open &&
        !state.dragging &&
        !state.fullscreen &&
        state.detentId === '' &&
        fraction
      )
        resizeAutomatically(fraction);
    },
    { flush: 'post' }
  );

  watch([options.drawing, options.hasRoute, density, dragging], ([drawing, hasRoute, nextDensity, isDragging]) => {
    if (!drawing) {
      dismissTip();
      return;
    }
    if (isDragging) return;
    if (nextDensity === 'full') {
      if (activeTip.value === 'return') dismissTip();
      if (hasRoute && !preferences.value.reduceTipDone) showTip('reduce');
      return;
    }
    if (activeTip.value === 'reduce') dismissTip();
    if (!preferences.value.reduceTipDone) savePreferences({ reduceTipDone: true });
    if (!preferences.value.returnTipDone) showTip('return');
  });

  onBeforeUnmount(clearTipTimer);

  return {
    preferences,
    layout,
    detents,
    sizes,
    density,
    reduced,
    summaryLayout,
    wide,
    desktop,
    entrySize,
    activeTip,
    onLayoutChange,
    onDetentChange,
    onResizeEnd,
    setSummaryMetrics,
    showMoreMap,
    showTools,
    expandTemporarily,
    endTemporaryExpansion,
    dismissTip,
  };
}
