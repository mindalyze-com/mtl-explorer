<template>
  <Teleport to="body">
    <!-- Backdrop: hidden when noBackdrop is set (e.g. Measure tool needs map interaction) -->
    <transition name="sheet-fade">
      <div
        v-if="isOpen && !isPreparing && !noBackdrop"
        class="sheet-backdrop"
        :style="{ zIndex: stackedZIndex - 1 }"
        @click="close"
      ></div>
    </transition>

    <div
      v-if="showDragHalo"
      ref="haloEl"
      class="sheet-drag-halo"
      :style="[sheetHaloStyle, { zIndex: stackedZIndex }]"
      aria-hidden="true"
      @click="onDragZoneClick"
    ></div>

    <!-- Sheet -->
    <div
      ref="sheetEl"
      class="sheet"
      tabindex="-1"
      :inert="isPreparing ? true : undefined"
      :aria-hidden="isPreparing ? 'true' : undefined"
      :style="[sheetStyle, { zIndex: stackedZIndex }]"
      :class="[
        sheetClass,
        {
          'sheet--open': isOpen,
          'sheet--dragging': isDragging,
          'sheet--preparing': isPreparing,
          'sheet--hidden': !isOpen && !isAnimatingOut,
          'sheet--backgrounded': isBackgrounded,
          'sheet--fullscreen': isFullscreen,
          'sheet--native-fullscreen': isNativeFullscreen,
          'sheet--immersive': immersive && isNativeFullscreen,
          'sheet--controls-hidden': immersiveControls.hidden.value,
          'sheet--header-compact': headerMode === 'compact',
          'sheet--viewport-centered': layoutViewportCentered,
          'sheet--desktop-compact': layoutDesktopWidth === 'compact',
          'sheet--shared-layout': shareLayout || inheritedLayout !== null,
        },
      ]"
      @transitionend="onSheetTransitionEnd"
      @touchstart.capture.passive="onSheetTouchStart"
      @touchmove.capture="onSheetTouchMove"
      @touchend.capture.passive="clearSheetTouchGesture"
      @touchcancel.capture.passive="clearSheetTouchGesture"
      @pointermove="immersiveControls.onPointerMove"
      @pointerdown.capture="immersiveControls.onPointerDown"
      @focusin="immersiveControls.reveal"
      @focusout="immersiveControls.reveal"
    >
      <div
        v-if="immersive && isNativeFullscreen"
        class="sheet-toolbar-reveal"
        aria-hidden="true"
        @pointerenter="immersiveControls.reveal"
      ></div>
      <!-- Drag zone: handle + header combined so the entire top area is draggable -->
      <div
        ref="handleEl"
        class="sheet-drag-zone"
        :inert="isBackgrounded || immersiveControls.hidden.value ? true : undefined"
        :aria-hidden="isBackgrounded || immersiveControls.hidden.value ? 'true' : undefined"
        @click="onDragZoneClick"
      >
        <div class="sheet-handle-zone">
          <div class="sheet-handle"></div>
        </div>

        <!-- Header -->
        <div
          v-if="hasHeader || headerMode === 'compact'"
          :class="hasHeader ? 'sheet-header' : 'sheet-floating-actions'"
        >
          <button
            v-if="backLabel"
            type="button"
            class="sheet-back-btn"
            :aria-label="backLabel"
            :title="backLabel"
            data-sheet-drag-exclude
            @click.stop="emit('back')"
          >
            <i class="bi bi-arrow-left" aria-hidden="true"></i>
          </button>
          <div v-if="hasHeader" class="sheet-header-content">
            <slot name="title">
              <span data-test="sheet-title" class="sheet-title"><i v-if="icon" :class="icon"></i>{{ title }}</span>
            </slot>
          </div>
          <div class="sheet-window-actions" data-sheet-drag-exclude>
            <slot v-if="hasHeader" name="header-actions"></slot>
            <slot name="size-action" :maximized="isFullscreen" :restore="leaveFullscreenState">
              <button
                type="button"
                class="sheet-fullscreen-btn"
                :class="{ 'sheet-labeled-size-btn': fullscreenControlLabels }"
                :aria-label="
                  fullscreenControlLabels ? panelSizeLabel : isFullscreen ? 'Restore panel size' : 'Maximize panel'
                "
                :title="
                  fullscreenControlLabels ? panelSizeLabel : isFullscreen ? 'Restore panel size' : 'Maximize panel'
                "
                @click.stop="toggleFullscreen"
              >
                <i :class="isFullscreen ? 'bi bi-arrows-angle-contract' : 'bi bi-arrows-angle-expand'"></i>
                <span v-if="fullscreenControlLabels">{{ panelSizeLabel }}</span>
              </button>
            </slot>
            <button
              v-if="nativeFullscreen"
              type="button"
              class="sheet-native-fullscreen-btn"
              :disabled="nativeFullscreenPending"
              :class="{ 'sheet-labeled-size-btn': fullscreenControlLabels }"
              :aria-label="
                fullscreenControlLabels
                  ? browserFullscreenLabel
                  : isNativeFullscreen
                    ? 'Exit fullscreen'
                    : 'Enter fullscreen'
              "
              :title="
                fullscreenControlLabels
                  ? browserFullscreenLabel
                  : isNativeFullscreen
                    ? 'Exit fullscreen'
                    : 'Enter fullscreen'
              "
              @click.stop="toggleNativeFullscreen"
            >
              <i :class="isNativeFullscreen ? 'bi bi-fullscreen-exit' : 'bi bi-fullscreen'" aria-hidden="true"></i>
              <span v-if="fullscreenControlLabels">{{ browserFullscreenLabel }}</span>
            </button>
            <button class="sheet-close-btn" aria-label="Close" @click.stop="close">
              <i class="bi bi-x-lg"></i>
            </button>
          </div>
        </div>
      </div>

      <p v-if="nativeFullscreenError" class="sheet-fullscreen-status" role="status">{{ nativeFullscreenError }}</p>

      <!-- Pinned content stays visible above the scrolling body at every panel height. -->
      <div
        v-if="slots.pinned"
        ref="pinnedEl"
        class="sheet-pinned"
        :inert="isBackgrounded ? true : undefined"
        :aria-hidden="isBackgrounded ? 'true' : undefined"
      >
        <slot name="pinned"></slot>
      </div>

      <div ref="bodyFrameEl" class="sheet-body-frame">
        <!-- Scrollable content -->
        <div
          ref="bodyEl"
          class="sheet-body"
          :inert="isBackgrounded ? true : undefined"
          :aria-hidden="isBackgrounded ? 'true' : undefined"
          @scroll="updateScrollHint"
        >
          <slot></slot>
        </div>

        <!-- Scroll hint overlays the content region without changing its viewport height. -->
        <div
          v-if="hasScrollableOverflow && !noScrollHint"
          v-show="showScrollHint"
          class="sheet-scroll-hint"
          :style="scrollHintStyle"
          :inert="isBackgrounded ? true : undefined"
          :aria-hidden="isBackgrounded ? 'true' : undefined"
        >
          <button
            type="button"
            class="sheet-scroll-hint__button"
            :aria-label="`Scroll down: ${scrollHintLabel}`"
            @click="scrollDown"
          >
            <span class="sheet-scroll-hint__label">{{ scrollHintLabel }}</span>
            <i class="bi bi-chevron-down sheet-scroll-hint__icon" aria-hidden="true"></i>
          </button>
        </div>
      </div>

      <!-- Persistent controls stay outside the scroll hint and content region. -->
      <div
        v-if="slots.footer"
        ref="footerEl"
        class="sheet-footer"
        :inert="isBackgrounded ? true : undefined"
        :aria-hidden="isBackgrounded ? 'true' : undefined"
      >
        <slot name="footer"></slot>
      </div>

      <div v-if="isBackgrounded" class="sheet-stack-scrim" aria-hidden="true"></div>
    </div>
  </Teleport>
</template>

<script lang="ts">
import { shallowReactive, type ComputedRef, type InjectionKey } from 'vue';

// ── Module-level Escape-key stack ──────────────────────────────────────────
// Each open sheet instance pushes its close callback here.
// One shared keydown listener fires; only the topmost sheet (last in stack)
// is closed, so nested sheets collapse one at a time.
const _escapeStack: Array<() => void> = [];

const DEFAULT_SHEET_Z_INDEX = 5001;
const SHEET_SCROLL_TARGET_CLASS = 'sheet-scroll-target';
const STACK_HEIGHT_DETENT_ID = 'stack-height';
const ESCAPE_OWNER_SELECTOR = [
  '[data-pc-name="popover"][role="dialog"]',
  '[data-pc-name="dialog"][role="dialog"][aria-modal="true"]',
].join(',');

interface OpenSheetRecord {
  id: number;
  zIndex: number;
  openOrder: number;
  layout: ComputedRef<SharedSheetLayout | null>;
}

let _nextSheetId = 1;
let _nextSheetOpenOrder = 1;
const _openSheets = shallowReactive<OpenSheetRecord[]>([]);

function _onEscape(e: KeyboardEvent) {
  if (e.key !== 'Escape' || e.defaultPrevented) return;
  // PrimeVue registers its document listener after the sheet. Let a foreground overlay handle Escape.
  const hasOpenOverlay = Array.from(document.querySelectorAll<HTMLElement>(ESCAPE_OWNER_SELECTOR)).some(
    (element) => element.getAttribute('aria-hidden') !== 'true' && getComputedStyle(element).display !== 'none'
  );
  if (hasOpenOverlay) return;
  const topClose = _escapeStack[_escapeStack.length - 1];
  if (topClose) {
    e.preventDefault();
    topClose();
  }
}

function _pushEscape(closeFn: () => void) {
  _escapeStack.push(closeFn);
  if (_escapeStack.length === 1) document.addEventListener('keydown', _onEscape);
}

function _popEscape(closeFn: () => void) {
  const idx = _escapeStack.lastIndexOf(closeFn);
  if (idx !== -1) _escapeStack.splice(idx, 1);
  if (_escapeStack.length === 0) document.removeEventListener('keydown', _onEscape);
}

function _syncBodyOverflowLock() {
  document.body.style.overflow = _openSheets.length > 0 ? 'hidden' : '';
}

function _pushOpenSheet(id: number, zIndex: number, layout: ComputedRef<SharedSheetLayout | null>) {
  const existingIdx = _openSheets.findIndex((record) => record.id === id);
  if (existingIdx !== -1) _openSheets.splice(existingIdx, 1);
  _openSheets.push({ id, zIndex, openOrder: _nextSheetOpenOrder++, layout });
  _syncBodyOverflowLock();
}

function _updateOpenSheetZIndex(id: number, zIndex: number) {
  const existing = _openSheets.find((record) => record.id === id);
  if (existing) existing.zIndex = zIndex;
}

function _popOpenSheet(id: number) {
  const idx = _openSheets.findIndex((record) => record.id === id);
  if (idx !== -1) _openSheets.splice(idx, 1);
  _syncBodyOverflowLock();
}

// ── Detent types (exported for call-site type safety) ──
export type DetentPreset = 'small' | 'medium' | 'large';
export interface DetentDef {
  id?: string;
  height: string | number;
}
export type Detent = DetentPreset | DetentDef;

interface SharedSheetLayout {
  heightPx: number;
  detentId: string;
  detents: Detent[];
  fullscreen: boolean;
  desktopWidth: 'standard' | 'compact';
  viewportCentered: boolean;
}

const _sharedSheetLayoutKey: InjectionKey<ComputedRef<SharedSheetLayout | null>> = Symbol('shared-sheet-layout');

export interface BottomSheetLayoutState {
  open: boolean;
  detentId: string;
  fullscreen: boolean;
  dragging: boolean;
  heightPx: number;
  widthPx: number;
  topPx: number;
  rightPx: number;
  bottomPx: number;
  leftPx: number;
}
</script>

<script setup lang="ts">
import {
  ref,
  computed,
  watch,
  nextTick,
  onMounted,
  onBeforeUnmount,
  onUnmounted,
  useSlots,
  inject,
  provide,
} from 'vue';
import { isPointerDragExcluded, usePointerDrag, type DragState } from '@/composables/usePointerDrag';
import { observeElementResize } from '@/utils/observeElementResize';
import { sheetControlsKey, useImmersiveSheetControls } from './useImmersiveSheetControls';
import {
  boundedSheetHeight,
  sheetContentOverflow,
  sheetFitTargets,
  SHEET_OVERFLOW_TOLERANCE_PX,
  SHEET_CONTENT_KEY_ATTRIBUTE,
} from './bottomSheetSizing';

const DETENT_PRESETS: Record<DetentPreset, string> = {
  small: 'clamp(180px, 30vh, 280px)',
  medium: 'clamp(380px, 55vh, 560px)',
  large: 'clamp(600px, 92vh, 92vh)',
};

const slots = useSlots();

const props = withDefaults(
  defineProps<{
    modelValue: boolean;
    title?: string;
    icon?: string;
    /** Return from a section to its hub. Also handles Escape after fullscreen is exited. */
    backLabel?: string;
    /** Emit close-request and let the caller change modelValue after navigation succeeds. */
    deferClose?: boolean;
    /** Re-resolve scrolling and sizing for cached sections. Nested screens may
     * instead set data-sheet-content-key on their content root. */
    contentKey?: string | number;
    headerMode?: 'default' | 'compact';
    /** Detent positions the sheet can rest at, ordered low → high.
     *  Each entry is a named preset ('small'|'medium'|'large') or
     *  { id?, height } where height is a CSS length string or a 0–1 fraction. */
    detents?: Detent[];
    /** Which detent to open at — an id string or a 0-based index. */
    initialDetent?: string | number;
    /** When true, open at the content height even when detents are available. */
    fitContentInitial?: boolean;
    /** Fit opening and later content changes, until the user manually resizes. */
    fitContent?: boolean;
    /** Remove small overflow within a bounded allowance; manual sizing takes priority until reopening. */
    fitSmallOverflow?: boolean;
    /** Programmatically jump to a detent (by id or index). */
    selectedDetent?: string | number;
    /** Optional detent/height to use while this sheet is backgrounded by a higher sheet. */
    backgroundDetent?: Detent | string | number;
    /** When true, no backdrop is rendered and clicks pass through to the map. */
    noBackdrop?: boolean;
    /** When true, the scroll-hint chevron at the bottom is never shown. */
    noScrollHint?: boolean;
    /** Visible desktop label for the scroll hint. Hidden on mobile. */
    scrollHintLabel?: string;
    /** Optional CSS class for sheet-specific surface variants. */
    sheetClass?: string | string[] | Record<string, boolean>;
    /** Center against the full viewport instead of the desktop navigation-aware content area. */
    viewportCentered?: boolean;
    /** Use the shared compact width on desktop while keeping the mobile sheet full width. */
    desktopWidth?: 'standard' | 'compact';
    /** Let related nested sheets inherit this panel's size, width and anchor. */
    shareLayout?: boolean;
    /** Start at a sharing parent's current layout; explicit user resizing takes priority. */
    inheritParentLayout?: boolean;
    /** Cover the previous sheet on entry; disable for compact map workspace tools. */
    coverPreviousSheet?: boolean;
    /** Show a browser-fullscreen control independently from the panel maximize control. */
    nativeFullscreen?: boolean;
    /** Label app expansion and browser fullscreen as separate size choices. */
    fullscreenControlLabels?: boolean;
    /** Hide idle controls over fullscreen content until pointer or keyboard input. */
    immersive?: boolean;
    /** Open a workspace that needs the full viewport with the panel maximized. */
    initiallyMaximized?: boolean;
    /** Override the CSS z-index for stacking sheets on top of each other. */
    zIndex?: number;
    /** When false, dragging or flicking down stops at the lowest detent instead of closing. */
    dragToClose?: boolean;
    /** Always come to rest on a detent after dragging, including slow releases. */
    snapOnRelease?: boolean;
    /** Keep the released height, including fast drags, instead of snapping or applying momentum. */
    freeResize?: boolean;
  }>(),
  {
    title: '',
    icon: '',
    backLabel: '',
    deferClose: false,
    contentKey: undefined,
    headerMode: 'default',
    detents: () => [],
    initialDetent: 0,
    initiallyMaximized: false,
    fitContentInitial: false,
    fitContent: false,
    fitSmallOverflow: true,
    selectedDetent: undefined,
    backgroundDetent: undefined,
    noBackdrop: true,
    noScrollHint: false,
    scrollHintLabel: 'More content',
    sheetClass: '',
    viewportCentered: false,
    desktopWidth: 'standard',
    shareLayout: false,
    inheritParentLayout: false,
    coverPreviousSheet: true,
    nativeFullscreen: false,
    fullscreenControlLabels: false,
    immersive: false,
    zIndex: undefined,
    dragToClose: true,
    snapOnRelease: false,
    freeResize: false,
  }
);

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void;
  (e: 'closed'): void;
  (e: 'close-request'): void;
  (e: 'back'): void;
  (e: 'detent-change', id: string): void;
  (e: 'layout-change', value: BottomSheetLayoutState): void;
  (e: 'resize-end', heightPx: number): void;
}>();

const sheetId = _nextSheetId++;
const sheetEl = ref<HTMLElement | null>(null);
const bodyFrameEl = ref<HTMLElement | null>(null);
const bodyEl = ref<HTMLElement | null>(null);
const pinnedEl = ref<HTMLElement | null>(null);
const footerEl = ref<HTMLElement | null>(null);
const isDragging = ref(false);
const isAnimatingOut = ref(false);
const hasScrollableOverflow = ref(false);
const showScrollHint = ref(false);
const scrollHintStyle = ref<Record<string, string>>({});
const isFullscreen = ref(false);
const isNativeFullscreen = ref(false);
const nativeFullscreenPending = ref(false);
const nativeFullscreenError = ref('');
const activeDetentId = ref('');
const isPreparing = ref(props.modelValue);
const isEntering = ref(false);
const SHEET_OPEN_DURATION_MS = 450;
const SHEET_MOTION_EASING = 'cubic-bezier(0.22, 1, 0.36, 1)';
const SHEET_VIEWPORT_LIMIT = 0.98;
const SHEET_DRAG_VIEWPORT_FRACTION = 0.92;
let openRevision = 0;
let openingAnimation: Animation | null = null;
let manuallySized = false;
let minimumOpeningHeight = 0;
let fullscreenRestoreState: { heightPx: number; detentId: string } | null = null;
let nativeFullscreenRestoreExpanded = false;
let fitBaseline = 0;
let fittedDetent: ResolvedDetent | null = null;
let fitFrame: number | null = null;
let fitResetPending = false;
let fitObserver: ResizeObserver | null = null;
let fitObservedTargets = new Set<Element>();
let waitingForMotion: Animation | null = null;
let lastSheetWidth = 0;
let viewportHeight = window.innerHeight;
let viewportWidth = window.innerWidth;
let visibleViewportHeight = availableViewportHeight();
const parentLayout = inject(_sharedSheetLayoutKey, null);
const inheritedLayout = ref<SharedSheetLayout | null>(null);
const layoutDesktopWidth = computed(() => inheritedLayout.value?.desktopWidth ?? props.desktopWidth);
const layoutViewportCentered = computed(() => inheritedLayout.value?.viewportCentered ?? props.viewportCentered);

function availableViewportHeight(): number {
  return Math.min(window.innerHeight, window.visualViewport?.height ?? window.innerHeight);
}

/** A foreground sheet covers the previous sheet until the user chooses another size. */
function openingHeight(heightPx: number): number {
  return Math.min(
    Math.max(heightPx, manuallySized ? 0 : minimumOpeningHeight),
    availableViewportHeight() * SHEET_VIEWPORT_LIMIT
  );
}

function canFitSmallOverflow(): boolean {
  const viewportReduced = availableViewportHeight() < window.innerHeight - SHEET_OVERFLOW_TOLERANCE_PX;
  return (
    props.fitSmallOverflow &&
    !inheritedLayout.value &&
    !props.fitContentInitial &&
    !props.fitContent &&
    props.detents.length > 0 &&
    !manuallySized &&
    !viewportReduced
  );
}

function canFitContent(): boolean {
  return (
    props.fitContent &&
    !inheritedLayout.value &&
    !manuallySized &&
    availableViewportHeight() >= window.innerHeight - SHEET_OVERFLOW_TOLERANCE_PX
  );
}

function canFitAutomatically(): boolean {
  return canFitContent() || canFitSmallOverflow();
}

function cancelOpening() {
  openRevision += 1;
  openingAnimation?.cancel();
  openingAnimation = null;
  isEntering.value = false;
}

function suspendAutomaticFit() {
  manuallySized = true;
  fitResetPending = false;
  cancelOpening();
}

/** Measure a new baseline in one synchronous layout transaction. Restoring with
 * transitions disabled prevents a section/viewport change from exposing the probe height.
 */
function measureFittedHeight(height: number): number {
  height = openingHeight(height);
  const sheet = sheetEl.value;
  const body = bodyEl.value;
  if (!sheet || !body || !canFitSmallOverflow()) return height;
  const previousHeight = sheet.style.height;
  const previousTransition = sheet.style.transition;
  sheet.style.transition = 'none';
  sheet.style.height = `${height}px`;
  const fitted = boundedSheetHeight(height, height, sheetContentOverflow(body), availableViewportHeight());
  sheet.style.height = previousHeight;
  // Commit the restored layout before enabling transitions again.
  void sheet.offsetHeight;
  sheet.style.transition = previousTransition;
  return fitted;
}

function rememberFittedDetent() {
  fittedDetent = sheetHeight.value > fitBaseline ? { id: activeDetentId.value, heightPx: sheetHeight.value } : null;
}

/** Probe natural content rather than retaining the previous scroll viewport height. */
function measureContentHeight(): number {
  const sheet = sheetEl.value;
  if (!sheet) return sheetHeight.value;
  const previousHeight = sheet.style.height;
  const previousTransition = sheet.style.transition;
  sheet.style.transition = 'none';
  sheet.style.height = '0px';
  const height = Math.min(computeInitialHeight(), availableViewportHeight() * SHEET_VIEWPORT_LIMIT);
  sheet.style.height = previousHeight;
  void sheet.offsetHeight;
  sheet.style.transition = previousTransition;
  return height;
}

function scheduleContentFit(reset = false) {
  fitResetPending ||= reset;
  if (!isOpen.value || isPreparing.value || isBackgrounded.value || !canFitAutomatically() || fitFrame !== null) return;
  fitFrame = window.requestAnimationFrame(() => {
    fitFrame = null;
    if (
      !isOpen.value ||
      isPreparing.value ||
      isBackgrounded.value ||
      isDragging.value ||
      isFullscreen.value ||
      isNativeFullscreen.value ||
      !canFitAutomatically()
    )
      return;
    const motion =
      openingAnimation ?? sheetEl.value?.getAnimations?.().find((animation) => animation.playState === 'running');
    if (motion) {
      if (waitingForMotion !== motion) {
        waitingForMotion = motion;
        const settled = () => {
          if (waitingForMotion !== motion) return;
          waitingForMotion = null;
          scheduleContentFit();
        };
        void motion.finished.then(settled, settled);
      }
      return;
    }
    if (canFitContent()) {
      fitResetPending = false;
      sheetHeight.value = measureContentHeight();
      fitBaseline = sheetHeight.value;
    } else if (fitResetPending) {
      fitResetPending = false;
      fitBaseline = getResolvedDetents().find((detent) => detent.id === activeDetentId.value)?.heightPx ?? fitBaseline;
      sheetHeight.value = measureFittedHeight(fitBaseline);
    } else if (bodyEl.value) {
      sheetHeight.value = boundedSheetHeight(
        fitBaseline,
        sheetHeight.value,
        sheetContentOverflow(bodyEl.value),
        availableViewportHeight()
      );
    }
    rememberFittedDetent();
    resolveScrollTarget();
    updateScrollHint();
  });
}

function observeFitContent() {
  const targets = new Set<Element>();
  // A scroll viewport can stay the same size while its natural content grows.
  if (bodyEl.value && canFitAutomatically()) {
    for (const target of sheetFitTargets(bodyEl.value)) {
      targets.add(target);
      for (const child of target.children) targets.add(child);
    }
    if (handleEl.value) targets.add(handleEl.value);
    if (pinnedEl.value) targets.add(pinnedEl.value);
    if (footerEl.value) targets.add(footerEl.value);
  }
  // Re-observing unchanged chart containers during a resize schedules another
  // initial notification and can loop through their style mutations.
  if (targets.size === fitObservedTargets.size && [...targets].every((target) => fitObservedTargets.has(target)))
    return;
  fitObserver?.disconnect();
  fitObservedTargets = targets;
  if (!targets.size) return;
  fitObserver ??= new ResizeObserver(() => scheduleContentFit());
  for (const target of targets) fitObserver.observe(target);
}

// Desktop detection for the mobile drag affordance.
const DESKTOP_BP = 769;
const SHEET_DRAG_HALO_PX = 10;
const SHEET_DRAG_ACTIVATION_THRESHOLD_PX = 6;
const SCROLL_OVERFLOW_TOLERANCE_PX = SHEET_OVERFLOW_TOLERANCE_PX;
const SHEET_TOUCH_EDGE_TOLERANCE_PX = 1;
const SHEET_TOUCH_DIRECTION_THRESHOLD_PX = 4;
const isDesktopWidth = ref(typeof window !== 'undefined' ? window.innerWidth >= DESKTOP_BP : false);
let viewportResizeFrame: number | null = null;

function onWindowResize() {
  isDesktopWidth.value = window.innerWidth >= DESKTOP_BP;
  const nextVisibleHeight = availableViewportHeight();
  const visualOnlyReduction =
    window.innerWidth === viewportWidth &&
    window.innerHeight === viewportHeight &&
    nextVisibleHeight < visibleViewportHeight;
  viewportWidth = window.innerWidth;
  viewportHeight = window.innerHeight;
  visibleViewportHeight = nextVisibleHeight;
  if (
    !isOpen.value ||
    isDragging.value ||
    isFullscreen.value ||
    isNativeFullscreen.value ||
    nativeFullscreenPending.value
  )
    return;
  if (viewportResizeFrame !== null) window.cancelAnimationFrame(viewportResizeFrame);
  viewportResizeFrame = window.requestAnimationFrame(() => {
    viewportResizeFrame = null;
    if (isPreparing.value || isEntering.value) {
      fitResetPending = true;
      scheduleContentFit();
      return;
    }
    if (inheritedLayout.value && !manuallySized) {
      followParentLayout(parentLayout?.value ?? inheritedLayout.value);
      return;
    }
    if (visualOnlyReduction || (manuallySized && !props.snapOnRelease)) {
      sheetHeight.value = Math.min(sheetHeight.value, availableViewportHeight() * SHEET_VIEWPORT_LIMIT);
      fitResetPending = false;
      scheduleLayoutEmit();
      return;
    }
    if (isBackgrounded.value && props.backgroundDetent != null) {
      const backgroundTarget = resolveDetentReference(props.backgroundDetent);
      if (backgroundTarget) {
        sheetHeight.value = Math.max(minHeight, backgroundTarget.heightPx);
        activeDetentId.value = backgroundTarget.id;
      }
      if (backgroundRestoreState) {
        const resolved = getResolvedDetents();
        const restoredDetent = resolved.find((detent) => detent.id === backgroundRestoreState?.activeDetentId);
        backgroundRestoreState.heightPx =
          restoredDetent?.heightPx ?? Math.min(backgroundRestoreState.heightPx, window.innerHeight * 0.98);
      }
    } else {
      const resolved = getResolvedDetents();
      if (resolved.length === 0 || activeDetentId.value === FIT_CONTENT_DETENT_ID) {
        sheetHeight.value = computeInitialHeight();
      } else {
        const activeDetent = resolved.find((detent) => detent.id === activeDetentId.value);
        fitBaseline = activeDetent?.heightPx ?? Math.min(sheetHeight.value, window.innerHeight * SHEET_VIEWPORT_LIMIT);
        sheetHeight.value = measureFittedHeight(fitBaseline);
        rememberFittedDetent();
      }
    }
    sheetHeight.value = Math.min(sheetHeight.value, availableViewportHeight() * SHEET_VIEWPORT_LIMIT);
    resolveScrollTarget();
    updateScrollHint();
    scheduleLayoutEmit();
  });
}
onMounted(() => {
  window.addEventListener('resize', onWindowResize);
  window.visualViewport?.addEventListener('resize', onWindowResize);
  document.addEventListener('fullscreenchange', onDocumentFullscreenChange);
});
onBeforeUnmount(exitOwnedBrowserFullscreen);
onUnmounted(() => {
  window.removeEventListener('resize', onWindowResize);
  window.visualViewport?.removeEventListener('resize', onWindowResize);
  document.removeEventListener('fullscreenchange', onDocumentFullscreenChange);
  if (viewportResizeFrame !== null) window.cancelAnimationFrame(viewportResizeFrame);
});

function exitOwnedBrowserFullscreen() {
  const element = sheetEl.value;
  if (!element || document.fullscreenElement !== element || typeof document.exitFullscreen !== 'function') return;
  try {
    void document.exitFullscreen().catch(() => undefined);
  } catch {
    // Some browsers reject fullscreen synchronously during teardown.
  }
}

function leaveFullscreenState() {
  if (!isFullscreen.value) return;
  isFullscreen.value = false;
  if (fullscreenRestoreState) {
    sheetHeight.value = Math.min(fullscreenRestoreState.heightPx, availableViewportHeight() * SHEET_VIEWPORT_LIMIT);
    activeDetentId.value = fullscreenRestoreState.detentId;
    fullscreenRestoreState = null;
  } else {
    sheetHeight.value = computeInitialHeight();
  }
  emit('detent-change', activeDetentId.value);
}

function toggleFullscreen() {
  nativeFullscreenError.value = '';
  suspendAutomaticFit();
  if (isFullscreen.value) {
    leaveFullscreenState();
    return;
  }

  if (isNativeFullscreen.value) leaveNativeFullscreen();
  fullscreenRestoreState = { heightPx: sheetHeight.value, detentId: activeDetentId.value };
  isFullscreen.value = true;
}

function leaveNativeFullscreenState() {
  isNativeFullscreen.value = false;
  if (nativeFullscreenRestoreExpanded) {
    nativeFullscreenRestoreExpanded = false;
    toggleFullscreen();
  }
}

function leaveNativeFullscreen() {
  leaveNativeFullscreenState();
  exitOwnedBrowserFullscreen();
}

async function toggleNativeFullscreen() {
  if (nativeFullscreenPending.value) return;
  suspendAutomaticFit();
  if (isNativeFullscreen.value) {
    leaveNativeFullscreen();
    return;
  }

  if (props.fullscreenControlLabels) {
    const element = sheetEl.value;
    nativeFullscreenError.value = '';
    if (!element?.requestFullscreen || document.fullscreenEnabled === false) {
      nativeFullscreenError.value = 'Full screen is unavailable in this browser. Use Expand.';
      return;
    }
    nativeFullscreenPending.value = true;
    try {
      // Invoke directly from the click; browser fullscreen requires user activation.
      await element.requestFullscreen({ navigationUI: 'hide' });
      if (!isOpen.value) {
        exitOwnedBrowserFullscreen();
        return;
      }
      if (document.fullscreenElement !== element) throw new Error('The browser did not enter fullscreen');
      nativeFullscreenRestoreExpanded = isFullscreen.value;
      if (isFullscreen.value) leaveFullscreenState();
      isNativeFullscreen.value = true;
    } catch {
      nativeFullscreenError.value = 'Full screen is unavailable in this browser. Use Expand.';
    } finally {
      nativeFullscreenPending.value = false;
    }
    return;
  }
  nativeFullscreenRestoreExpanded = false;
  if (isFullscreen.value) leaveFullscreenState();
  isNativeFullscreen.value = true;
  if (sheetEl.value?.requestFullscreen) {
    void sheetEl.value.requestFullscreen().catch(() => {
      // Keep the complete-viewport fallback when native fullscreen is unavailable.
    });
  }
}

const panelSizeLabel = computed(() => (isFullscreen.value ? 'Restore' : 'Expand'));
const browserFullscreenLabel = computed(() => (isNativeFullscreen.value ? 'Exit full screen' : 'Full screen'));

function onDocumentFullscreenChange() {
  if (isNativeFullscreen.value && document.fullscreenElement !== sheetEl.value) leaveNativeFullscreenState();
}

function handleEscape() {
  if (isNativeFullscreen.value) {
    toggleNativeFullscreen();
    return;
  }
  if (isFullscreen.value) {
    toggleFullscreen();
    return;
  }
  if (props.backLabel) {
    emit('back');
    return;
  }
  close();
}

// Free-form height in px — the sheet stays exactly where you drag it
const sheetHeight = ref(0);
const handleEl = ref<HTMLElement | null>(null);
const haloEl = ref<HTMLElement | null>(null);
let dragStartHeight = 0;
let suppressNextDragZoneClick = false;
let backgroundRestoreState: { activeDetentId: string; fullscreen: boolean; heightPx: number } | null = null;

const isOpen = computed(() => props.modelValue);
const currentSheetLayout = computed<SharedSheetLayout | null>(() =>
  isOpen.value
    ? {
        heightPx: sheetHeight.value,
        detentId: activeDetentId.value,
        detents: inheritedLayout.value?.detents ?? props.detents,
        fullscreen: isFullscreen.value || isNativeFullscreen.value,
        desktopWidth: layoutDesktopWidth.value,
        viewportCentered: layoutViewportCentered.value,
      }
    : null
);
provide(
  _sharedSheetLayoutKey,
  computed(() => (props.shareLayout ? currentSheetLayout.value : null))
);

function followParentLayout(layout: SharedSheetLayout) {
  inheritedLayout.value = layout;
  sheetHeight.value = Math.min(layout.heightPx, availableViewportHeight() * SHEET_VIEWPORT_LIMIT);
  activeDetentId.value = layout.detentId;
  isFullscreen.value = layout.fullscreen;
  fullscreenRestoreState = layout.fullscreen ? { heightPx: sheetHeight.value, detentId: layout.detentId } : null;
  fitBaseline = sheetHeight.value;
  resolveScrollTarget();
  updateScrollHint();
  scheduleLayoutEmit();
}

if (parentLayout) {
  watch(parentLayout, (layout) => {
    if (
      layout &&
      inheritedLayout.value &&
      isOpen.value &&
      !isPreparing.value &&
      !manuallySized &&
      !isNativeFullscreen.value
    )
      followParentLayout(layout);
  });
}
const headerMode = computed(() => props.headerMode);
const hasHeader = computed(() => Boolean(props.title || slots.title || props.backLabel));
const effectiveZIndex = computed(() => props.zIndex ?? DEFAULT_SHEET_Z_INDEX);
const stackedZIndex = computed(() => {
  const current = _openSheets.find((record) => record.id === sheetId);
  if (!current) return effectiveZIndex.value;

  const equalLayerSheets = _openSheets
    .filter((record) => record.zIndex === current.zIndex)
    .sort((a, b) => a.openOrder - b.openOrder);
  const layerIndex = equalLayerSheets.findIndex((record) => record.id === sheetId);

  // Reserve one layer between sheets for each foreground sheet's backdrop.
  return effectiveZIndex.value + Math.max(layerIndex, 0) * 2;
});
const isBackgrounded = computed(() => {
  if (!isOpen.value) return false;
  const current = _openSheets.find((record) => record.id === sheetId);
  if (!current) return false;
  return _openSheets.some(
    (record) =>
      record.id !== sheetId &&
      (record.zIndex > current.zIndex || (record.zIndex === current.zIndex && record.openOrder > current.openOrder))
  );
});
const immersiveControls = useImmersiveSheetControls(
  sheetEl,
  computed(() => props.immersive && isOpen.value && !isBackgrounded.value && isNativeFullscreen.value)
);
provide(sheetControlsKey, { hidden: immersiveControls.hidden });
const showDragHalo = computed(
  () =>
    isOpen.value &&
    !isPreparing.value &&
    !isEntering.value &&
    !isBackgrounded.value &&
    !isFullscreen.value &&
    !isNativeFullscreen.value &&
    !isDesktopWidth.value
);
const sheetHaloStyle = computed<Record<string, string>>(() => ({
  '--sheet-current-height': `${sheetHeight.value}px`,
  '--sheet-drag-halo-height': `${SHEET_DRAG_HALO_PX}px`,
}));

const minHeight = 60; // px — below this we close
const FIT_CONTENT_DETENT_ID = 'fit-content';

// ── Detent resolution ──
interface ResolvedDetent {
  id: string;
  heightPx: number;
}

function resolveCssLength(value: string | number, vh: number): number {
  if (typeof value === 'number') return vh * Math.min(Math.max(value, 0), 1);
  const trimmed = value.trim();
  const pxMatch = /^([0-9]*\.?[0-9]+)px$/i.exec(trimmed);
  if (pxMatch) return Number(pxMatch[1]);
  const vhMatch = /^([0-9]*\.?[0-9]+)d?vh$/i.exec(trimmed);
  if (vhMatch) return (Number(vhMatch[1]) / 100) * vh;
  const el = document.createElement('div');
  el.style.cssText = `position:fixed;left:-9999px;top:-9999px;visibility:hidden;height:${value};pointer-events:none`;
  document.body.appendChild(el);
  const h = el.offsetHeight;
  el.remove();
  return h;
}

function resolveDetents(detents: Detent[], vh: number): ResolvedDetent[] {
  const maxPx = vh * 0.98; // hard cap — sheets must never exceed the screen
  return detents
    .map((d, i) => {
      if (typeof d === 'string') {
        return { id: d, heightPx: Math.min(resolveCssLength(DETENT_PRESETS[d], vh), maxPx) };
      }
      return { id: d.id ?? `detent-${i}`, heightPx: Math.min(resolveCssLength(d.height, vh), maxPx) };
    })
    .sort((a, b) => a.heightPx - b.heightPx);
}

function findDetentIndex(resolved: ResolvedDetent[], ref: string | number | undefined): number {
  if (ref == null) return 0;
  if (typeof ref === 'number') return Math.min(Math.max(Math.round(ref), 0), resolved.length - 1);
  const idx = resolved.findIndex((d) => d.id === ref);
  return idx >= 0 ? idx : 0;
}

function getResolvedDetents(): ResolvedDetent[] {
  return resolveDetents(inheritedLayout.value?.detents ?? props.detents, window.innerHeight);
}

function getInteractionDetents(): ResolvedDetent[] {
  const detents = getResolvedDetents()
    .map((detent) => ({
      ...detent,
      heightPx: Math.min(
        detent.id === fittedDetent?.id ? Math.max(detent.heightPx, fittedDetent.heightPx) : detent.heightPx,
        availableViewportHeight() * SHEET_VIEWPORT_LIMIT
      ),
    }))
    .sort((a, b) => a.heightPx - b.heightPx);
  const stackHeight = Math.min(minimumOpeningHeight, availableViewportHeight() * SHEET_VIEWPORT_LIMIT);
  // The raised opening height must also be reachable by dragging and resizeTo.
  if (detents.length && stackHeight > detents[detents.length - 1].heightPx) {
    detents.push({ id: STACK_HEIGHT_DETENT_ID, heightPx: stackHeight });
  }
  return detents;
}

function resolveDetentReference(ref: Detent | string | number | undefined): ResolvedDetent | null {
  if (ref == null) return null;
  const vh = window.innerHeight;

  if (typeof ref === 'object') {
    return resolveDetents([ref], vh)[0] ?? null;
  }

  const resolved = getResolvedDetents();
  if (typeof ref === 'number') {
    return resolved[findDetentIndex(resolved, ref)] ?? null;
  }

  const byId = resolved.find((detent) => detent.id === ref);
  if (byId) return byId;

  if (ref in DETENT_PRESETS) {
    return { id: ref, heightPx: resolveCssLength(DETENT_PRESETS[ref as DetentPreset], vh) };
  }

  return { id: ref, heightPx: resolveCssLength(ref, vh) };
}

/** Compute the height the sheet should open at */
function computeInitialHeight(): number {
  if (inheritedLayout.value) {
    activeDetentId.value = inheritedLayout.value.detentId;
    return openingHeight(inheritedLayout.value.heightPx);
  }
  const vh = window.innerHeight;
  const defs = props.detents;

  if (!defs || defs.length === 0) {
    return openingHeight(computeAutoFitHeight(vh));
  }

  const resolved = getResolvedDetents();
  if (props.fitContentInitial || props.fitContent) {
    const maxDetentHeight = resolved[resolved.length - 1]?.heightPx ?? vh * 0.92;
    const minDetentHeight = resolved[0]?.heightPx ?? minHeight;
    const fallbackDetentHeight = resolved[findDetentIndex(resolved, props.initialDetent)]?.heightPx ?? minDetentHeight;
    const measuredHeight = computeContentIdealHeight(fallbackDetentHeight);
    const idealHeight =
      !props.fitContent && measuredHeight <= minDetentHeight + 1 ? fallbackDetentHeight : measuredHeight;
    activeDetentId.value = FIT_CONTENT_DETENT_ID;
    return openingHeight(Math.max(minDetentHeight, Math.min(idealHeight, maxDetentHeight)));
  }

  const idx = findDetentIndex(resolved, props.selectedDetent ?? props.initialDetent);
  activeDetentId.value = resolved[idx].id;
  return openingHeight(resolved[idx].heightPx);
}

function computeContentIdealHeight(fallbackHeight = 200): number {
  if (!sheetEl.value) {
    return fallbackHeight;
  }

  const dragZone = sheetEl.value.querySelector('.sheet-drag-zone') as HTMLElement | null;
  const body = bodyEl.value;
  const sheetStyles = window.getComputedStyle(sheetEl.value);
  const verticalInsets =
    (Number.parseFloat(sheetStyles.paddingTop) || 0) +
    (Number.parseFloat(sheetStyles.paddingBottom) || 0) +
    (Number.parseFloat(sheetStyles.borderTopWidth) || 0) +
    (Number.parseFloat(sheetStyles.borderBottomWidth) || 0);
  const chromeH = (dragZone?.offsetHeight ?? 0) + verticalInsets;
  const contentH = body ? body.scrollHeight : 200;
  const pinnedH = pinnedEl.value?.offsetHeight ?? 0;
  const footerH = footerEl.value?.offsetHeight ?? 0;
  return chromeH + pinnedH + contentH + footerH;
}

/** Content-aware auto-fit when no detents are specified */
function computeAutoFitHeight(vh: number): number {
  const maxPx = vh * 0.92;
  const idealH = computeContentIdealHeight(Math.min(vh * 0.35, maxPx));

  const softCap = vh > 900 ? vh * 0.35 : maxPx;
  return Math.max(minHeight, Math.min(idealH, softCap, maxPx));
}

function collapseToBackgroundDetent() {
  const target = resolveDetentReference(props.backgroundDetent);
  if (!target) return;

  if (!backgroundRestoreState) {
    backgroundRestoreState = {
      activeDetentId: activeDetentId.value,
      fullscreen: isFullscreen.value,
      heightPx: sheetHeight.value,
    };
  }

  isFullscreen.value = false;
  sheetHeight.value = Math.max(minHeight, target.heightPx);
  activeDetentId.value = target.id;
  updateScrollHint();
}

function restoreFromBackgroundDetent() {
  if (!backgroundRestoreState) return;
  const restoreState = backgroundRestoreState;
  backgroundRestoreState = null;
  isFullscreen.value = restoreState.fullscreen;
  sheetHeight.value = Math.min(restoreState.heightPx, availableViewportHeight() * SHEET_VIEWPORT_LIMIT);
  activeDetentId.value = restoreState.activeDetentId;
  updateScrollHint();
  scheduleContentFit(true);
}

const sheetStyle = computed(() => {
  if (!isOpen.value && !isAnimatingOut.value) {
    return { height: '0px' };
  }
  if (isFullscreen.value || isNativeFullscreen.value) {
    return { height: '100dvh' }; // to work correct on iphone
  }
  if (isDragging.value) {
    return { height: `${sheetHeight.value}px`, transition: 'none' };
  }
  return { height: `${sheetHeight.value}px` };
});

// ── Scroll-hint: auto-detect the actual scrollable element ──
// The sheet body is always a neutral flex container; content manages its own
// scroll. Walk descendants to find the first element with overflow-y: auto/scroll
// that actually has overflowing content.
const scrollTarget = ref<HTMLElement | null>(null);
let scrollTargetListener: (() => void) | null = null;
let contentObserver: MutationObserver | null = null;
let stopObservingScrollResize: (() => void) | null = null;
let stopObservingSheetLayout: (() => void) | null = null;
let layoutFrame: number | null = null;

function findScrollableChild(root: HTMLElement): HTMLElement | null {
  const rootStyle = window.getComputedStyle(root);
  if (
    (rootStyle.overflowY === 'auto' || rootStyle.overflowY === 'scroll') &&
    root.scrollHeight > root.clientHeight + SCROLL_OVERFLOW_TOLERANCE_PX
  ) {
    return root;
  }

  const children = root.querySelectorAll('*');
  for (const child of children) {
    const style = window.getComputedStyle(child);
    if (style.overflowY === 'auto' || style.overflowY === 'scroll') {
      const el = child as HTMLElement;
      if (el.scrollHeight > el.clientHeight + SCROLL_OVERFLOW_TOLERANCE_PX) return el;
    }
  }
  return null;
}

function resolveScrollTarget() {
  if (!bodyEl.value) {
    scrollTarget.value = null;
    return;
  }
  const primary = sheetFitTargets(bodyEl.value).find((target) => target !== bodyEl.value);
  scrollTarget.value = primary ?? findScrollableChild(bodyEl.value);
}

type SheetTouchScrollTarget = { element: HTMLElement; horizontal: boolean; vertical: boolean; rtl: boolean };
let sheetTouchGesture: {
  identifier: number;
  startX: number;
  startY: number;
  lastX: number;
  lastY: number;
  axis: 'x' | 'y' | null;
  targets: SheetTouchScrollTarget[];
} | null = null;

function clearSheetTouchGesture() {
  sheetTouchGesture = null;
}

function isNativeRangeControl(target: EventTarget | null): boolean {
  // Native ranges own thumb dragging; canceling touchmove can suppress updates.
  return target instanceof HTMLInputElement && target.type === 'range' && !target.matches(':disabled');
}

function ownsSheetTouchGesture(target: EventTarget | null): boolean {
  return (
    isNativeRangeControl(target) ||
    (target instanceof Element && Boolean(target.closest('[data-sheet-native-gesture]')))
  );
}

function onSheetTouchStart(event: TouchEvent) {
  clearSheetTouchGesture();
  const root = bodyEl.value;
  if (event.touches.length !== 1 || !(event.target instanceof Node) || !root?.contains(event.target)) return;
  if (ownsSheetTouchGesture(event.target)) return;
  const touch = event.touches[0];
  const targets: SheetTouchScrollTarget[] = [];
  for (
    let element = event.target instanceof HTMLElement ? event.target : event.target.parentElement;
    element;
    element = element.parentElement
  ) {
    const style = getComputedStyle(element);
    const horizontal =
      /^(auto|scroll)$/.test(style.overflowX) &&
      element.scrollWidth > element.clientWidth + SCROLL_OVERFLOW_TOLERANCE_PX;
    const vertical =
      /^(auto|scroll)$/.test(style.overflowY) &&
      element.scrollHeight > element.clientHeight + SCROLL_OVERFLOW_TOLERANCE_PX;
    if (horizontal || vertical) targets.push({ element, horizontal, vertical, rtl: style.direction === 'rtl' });
    if (element === root) break;
  }
  sheetTouchGesture = {
    identifier: touch.identifier,
    startX: touch.clientX,
    startY: touch.clientY,
    lastX: touch.clientX,
    lastY: touch.clientY,
    axis: null,
    targets,
  };
}

function onSheetTouchMove(event: TouchEvent) {
  if (!event.cancelable || event.touches.length !== 1 || !(event.target instanceof Node)) return;
  if (!bodyEl.value?.contains(event.target)) return;
  if (ownsSheetTouchGesture(event.target)) return;
  const gesture = sheetTouchGesture;
  const touch = event.touches[0];
  if (!gesture || gesture.identifier !== touch.identifier) {
    event.preventDefault();
    return;
  }
  const dx = touch.clientX - gesture.lastX;
  const dy = touch.clientY - gesture.lastY;
  gesture.lastX = touch.clientX;
  gesture.lastY = touch.clientY;
  if (!gesture.axis) {
    const totalX = Math.abs(touch.clientX - gesture.startX);
    const totalY = Math.abs(touch.clientY - gesture.startY);
    if (Math.max(totalX, totalY) < SHEET_TOUCH_DIRECTION_THRESHOLD_PX) {
      if (!gesture.targets.length) event.preventDefault();
      return;
    }
    gesture.axis = totalX > totalY ? 'x' : 'y';
  }
  const horizontal = gesture.axis === 'x';
  const canScroll = gesture.targets.some(({ element, horizontal: scrollX, vertical: scrollY, rtl }) => {
    if (!element.isConnected || !(horizontal ? scrollX : scrollY)) return false;
    const maximum = horizontal
      ? element.scrollWidth - element.clientWidth
      : element.scrollHeight - element.clientHeight;
    const offset = horizontal ? (rtl ? -element.scrollLeft : element.scrollLeft) : element.scrollTop;
    const movement = horizontal ? (rtl ? -dx : dx) : dy;
    if (movement > 0) return offset > SHEET_TOUCH_EDGE_TOLERANCE_PX;
    if (movement < 0) return offset < maximum - SHEET_TOUCH_EDGE_TOLERANCE_PX;
    return true;
  });
  // Contain gestures that cannot move content, including movement past its edges.
  if (!canScroll) event.preventDefault();
}

function updateScrollHint() {
  const el = scrollTarget.value;
  if (!el || props.noScrollHint) {
    hasScrollableOverflow.value = false;
    showScrollHint.value = false;
    return;
  }
  const { scrollTop, scrollHeight, clientHeight } = el;
  const overflows = scrollHeight > clientHeight + SCROLL_OVERFLOW_TOLERANCE_PX;
  hasScrollableOverflow.value = overflows;
  showScrollHint.value = overflows && scrollTop < scrollHeight - clientHeight - SCROLL_OVERFLOW_TOLERANCE_PX;
  if (overflows && bodyFrameEl.value) {
    // A nested viewport may sit beside navigation or below persistent controls.
    // Keep its fade within that viewport, including after resizing or changing sections.
    const frame = bodyFrameEl.value.getBoundingClientRect();
    const target = el.getBoundingClientRect();
    scrollHintStyle.value = {
      left: `${Math.max(0, target.left - frame.left)}px`,
      right: `${Math.max(0, frame.right - target.right)}px`,
      bottom: `${Math.max(0, frame.bottom - target.bottom)}px`,
      maxHeight: `${Math.max(0, Math.min(frame.bottom, target.bottom) - Math.max(frame.top, target.top))}px`,
    };
  }
}

watch(
  () => props.contentKey,
  () => {
    resolveScrollTarget();
    updateScrollHint();
    observeFitContent();
    scheduleContentFit(true);
  },
  { flush: 'post' }
);

function scheduleLayoutEmit() {
  if (typeof window === 'undefined' || layoutFrame !== null) return;
  layoutFrame = window.requestAnimationFrame(() => {
    layoutFrame = null;
    emitLayoutChange();
    if (isEntering.value) scheduleLayoutEmit();
  });
}

function emitLayoutChange() {
  if (isPreparing.value) return;
  const vh = window.innerHeight;
  const vw = window.innerWidth;
  const rect = sheetEl.value?.getBoundingClientRect();
  const visible = isOpen.value || isAnimatingOut.value;
  const fallbackHeight = visible ? (isFullscreen.value || isNativeFullscreen.value ? vh : sheetHeight.value) : 0;
  const heightPx = Math.max(0, rect?.height ?? fallbackHeight);
  const widthPx = Math.max(0, rect?.width ?? vw);
  const topPx = visible ? (rect?.top ?? vh - heightPx) : vh;
  const rightPx = visible ? (rect?.right ?? vw) : vw;
  const bottomPx = visible ? (rect?.bottom ?? vh) : vh;
  const leftPx = visible ? (rect?.left ?? 0) : 0;

  emit('layout-change', {
    open: isOpen.value,
    detentId: activeDetentId.value,
    fullscreen: isFullscreen.value || isNativeFullscreen.value,
    dragging: isDragging.value,
    heightPx,
    widthPx,
    topPx,
    rightPx,
    bottomPx,
    leftPx,
  });
}

function onSheetTransitionEnd(event: TransitionEvent) {
  if (event.target !== sheetEl.value) return;
  if (event.propertyName === 'height') {
    scheduleLayoutEmit();
    scheduleContentFit();
  }
}

function scrollDown() {
  const el = scrollTarget.value;
  if (!el) return;
  el.scrollBy({ top: el.clientHeight * 0.8, behavior: 'smooth' });
}

// Attach / detach scroll + resize listeners on the resolved target.
// ResizeObserver is critical: the sheet height animates via CSS transition,
// so at the moment we first call updateScrollHint() the clientHeight is still
// the pre-transition value. The observer fires as the element actually resizes,
// re-running the hint check so it reflects the final layout (and any content
// additions/removals, fullscreen toggle, orientation changes, window resize).
watch(scrollTarget, (newEl, oldEl) => {
  oldEl?.classList.remove(SHEET_SCROLL_TARGET_CLASS);
  if (oldEl && scrollTargetListener) {
    oldEl.removeEventListener('scroll', scrollTargetListener);
  }
  stopObservingScrollResize?.();
  stopObservingScrollResize = null;
  if (newEl) {
    newEl.classList.add(SHEET_SCROLL_TARGET_CLASS);
    scrollTargetListener = updateScrollHint;
    newEl.addEventListener('scroll', scrollTargetListener, { passive: true });
    stopObservingScrollResize = observeElementResize(newEl, updateScrollHint);
  }
});

// Observe DOM mutations inside bodyEl so dynamic content changes (e.g. tab
// switches, lazy-loaded content) re-resolve the scroll target.
function startContentObserver() {
  stopContentObserver();
  if (!bodyEl.value) return;
  contentObserver = new MutationObserver((records) => {
    resolveScrollTarget();
    updateScrollHint();
    observeFitContent();
    scheduleContentFit(records.some((record) => record.attributeName === SHEET_CONTENT_KEY_ATTRIBUTE));
  });
  contentObserver.observe(bodyEl.value, {
    childList: true,
    subtree: true,
    characterData: true,
    attributes: true,
    attributeFilter: ['style', 'hidden', SHEET_CONTENT_KEY_ATTRIBUTE],
  });
  observeFitContent();
}
function stopContentObserver() {
  if (contentObserver) {
    contentObserver.disconnect();
    contentObserver = null;
  }
  fitObserver?.disconnect();
  fitObservedTargets.clear();
  if (fitFrame !== null) window.cancelAnimationFrame(fitFrame);
  fitFrame = null;
  waitingForMotion = null;
}

async function prepareOpening() {
  cancelOpening();
  const revision = openRevision;
  manuallySized = false;
  const current = _openSheets.find((record) => record.id === sheetId);
  const background = _openSheets
    .filter(
      (record) =>
        record.id !== sheetId &&
        current &&
        (record.zIndex < current.zIndex || (record.zIndex === current.zIndex && record.openOrder < current.openOrder))
    )
    .sort((a, b) => b.zIndex - a.zIndex || b.openOrder - a.openOrder)[0]?.layout.value;
  minimumOpeningHeight = props.coverPreviousSheet ? (background?.heightPx ?? 0) : 0;
  inheritedLayout.value = props.inheritParentLayout ? (parentLayout?.value ?? null) : null;
  isFullscreen.value =
    inheritedLayout.value?.fullscreen ??
    (props.initiallyMaximized || (props.coverPreviousSheet && Boolean(background?.fullscreen)));
  fullscreenRestoreState = inheritedLayout.value?.fullscreen
    ? { heightPx: inheritedLayout.value.heightPx, detentId: inheritedLayout.value.detentId }
    : null;
  fitResetPending = false;
  fittedDetent = null;
  isPreparing.value = true;
  isAnimatingOut.value = false;
  // Fit-content modes retain their existing measurement semantics at zero height.
  sheetHeight.value = 0;
  await nextTick();
  if (!isOpen.value || revision !== openRevision) return;
  fitBaseline = Math.min(computeInitialHeight(), availableViewportHeight() * SHEET_VIEWPORT_LIMIT);
  sheetHeight.value = fitBaseline;
  await nextTick();
  if (!isOpen.value || revision !== openRevision) return;
  sheetHeight.value = measureFittedHeight(fitBaseline);
  rememberFittedDetent();
  await nextTick();
  if (!isOpen.value || revision !== openRevision) return;
  const sheet = sheetEl.value;
  // The real content is already laid out at its final size. Only its position
  // animates, so wrapping and scroll viewports stay stable throughout entry.
  if (sheet?.animate && !window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) {
    isEntering.value = true;
    const animation = sheet.animate([{ transform: 'translateY(100%)' }, { transform: 'translateY(0)' }], {
      duration: SHEET_OPEN_DURATION_MS,
      easing: SHEET_MOTION_EASING,
    });
    openingAnimation = animation;
    const finish = () => {
      if (revision !== openRevision) return;
      openingAnimation = null;
      isEntering.value = false;
      scheduleLayoutEmit();
      scheduleContentFit();
    };
    void animation.finished.then(finish, finish);
  }
  isPreparing.value = false;
  if (activeDetentId.value) emit('detent-change', activeDetentId.value);
  resolveScrollTarget();
  startContentObserver();
  updateScrollHint();
  scheduleLayoutEmit();
  if (isBackgrounded.value && props.backgroundDetent != null) collapseToBackgroundDetent();
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      _pushOpenSheet(sheetId, effectiveZIndex.value, currentSheetLayout);
      _pushEscape(handleEscape);
      void prepareOpening();
    } else {
      clearSheetTouchGesture();
      cancelOpening();
      isPreparing.value = false;
      _popEscape(handleEscape);
      _popOpenSheet(sheetId);
      backgroundRestoreState = null;
      hasScrollableOverflow.value = false;
      showScrollHint.value = false;
      scrollTarget.value = null;
      stopContentObserver();
      scheduleLayoutEmit();
    }
  },
  { immediate: true }
);

watch([sheetHeight, isOpen, isFullscreen, isNativeFullscreen, isDragging, activeDetentId], scheduleLayoutEmit, {
  flush: 'post',
});

watch(
  sheetEl,
  (newEl) => {
    stopObservingSheetLayout?.();
    stopObservingSheetLayout = null;
    if (newEl) {
      stopObservingSheetLayout = observeElementResize(newEl, () => {
        scheduleLayoutEmit();
        resolveScrollTarget();
        updateScrollHint();
        const width = newEl.clientWidth;
        if (width !== lastSheetWidth) {
          const changed = lastSheetWidth > 0;
          lastSheetWidth = width;
          scheduleContentFit(changed);
        }
      });
      scheduleLayoutEmit();
    }
  },
  { flush: 'post' }
);

watch(effectiveZIndex, (zIndex) => {
  if (isOpen.value) _updateOpenSheetZIndex(sheetId, zIndex);
});

watch(
  [isBackgrounded, () => props.backgroundDetent],
  ([backgrounded]) => {
    if (!isOpen.value || isPreparing.value) return;
    if (backgrounded && props.backgroundDetent != null) {
      collapseToBackgroundDetent();
    } else {
      restoreFromBackgroundDetent();
      if (!backgrounded) scheduleContentFit();
    }
  },
  { flush: 'post' }
);

function close() {
  if (props.deferClose) {
    emit('close-request');
    return;
  }
  cancelOpening();
  exitOwnedBrowserFullscreen();
  isAnimatingOut.value = !isPreparing.value;
  isPreparing.value = false;
  isFullscreen.value = false;
  isNativeFullscreen.value = false;
  sheetHeight.value = 0;
  nativeFullscreenRestoreExpanded = false;
  nativeFullscreenError.value = '';
  setTimeout(() => {
    isAnimatingOut.value = false;
  }, 350);
  emit('update:modelValue', false);
  emit('closed');
}

function suppressDragZoneClickOnce() {
  suppressNextDragZoneClick = true;
  window.setTimeout(() => {
    suppressNextDragZoneClick = false;
  }, 0);
}

function snapToDetent(detent: ResolvedDetent) {
  sheetHeight.value = detent.heightPx;
  activeDetentId.value = detent.id;
  emit('detent-change', activeDetentId.value);
  updateScrollHint();
}

function nearestDetentIndex(detents: ResolvedDetent[], heightPx: number): number {
  return detents.reduce(
    (closest, detent, index) =>
      Math.abs(detent.heightPx - heightPx) < Math.abs(detents[closest].heightPx - heightPx) ? index : closest,
    0
  );
}

function snapToAdjacentDetent() {
  if (isPreparing.value || isDragging.value || isBackgrounded.value || isFullscreen.value || isNativeFullscreen.value)
    return;
  suspendAutomaticFit();
  const resolved = getInteractionDetents();
  if (resolved.length === 0) return;

  const currentIdx = nearestDetentIndex(resolved, sheetHeight.value);
  const targetIdx =
    resolved.length === 1 ? currentIdx : currentIdx < resolved.length - 1 ? currentIdx + 1 : currentIdx - 1;
  snapToDetent(resolved[targetIdx]);
}

function onDragZoneClick(event: MouseEvent) {
  if (suppressNextDragZoneClick) {
    suppressNextDragZoneClick = false;
    return;
  }
  if (isPointerDragExcluded(event.target)) return;
  snapToAdjacentDetent();
}

function onSheetDrag({ movement: [, my], velocity: vel, direction: [, dy], dragging, first, last }: DragState) {
  if (isPreparing.value || isBackgrounded.value || isFullscreen.value || isNativeFullscreen.value) return;
  if (first) {
    suspendAutomaticFit();
    isDragging.value = true;
    dragStartHeight = sheetHeight.value;
  }

  if (dragging) {
    const resolved = getInteractionDetents();
    const maxPx = Math.min(
      resolved.length > 0 ? resolved[resolved.length - 1].heightPx : window.innerHeight * SHEET_DRAG_VIEWPORT_FRACTION,
      availableViewportHeight() * SHEET_VIEWPORT_LIMIT
    );
    const minPx = !props.dragToClose && resolved.length > 0 ? resolved[0].heightPx : minHeight * 0.5;
    // my < 0 when finger moves up → sheet grows
    sheetHeight.value = Math.max(minPx, Math.min(dragStartHeight - my, maxPx));
  }

  if (last) {
    isDragging.value = false;
    suppressDragZoneClickOnce();
    const velocity = -(vel * dy);

    if (props.dragToClose && (velocity < -0.5 || sheetHeight.value < minHeight)) {
      if (props.deferClose) {
        sheetHeight.value = dragStartHeight;
        updateScrollHint();
      }
      close();
      return;
    }

    const resolved = getInteractionDetents();
    if (props.freeResize) {
      activeDetentId.value = '';
      emit('resize-end', sheetHeight.value);
    } else if (resolved.length > 0) {
      if (vel > 0.1 || props.snapOnRelease) {
        let targetIdx: number;
        if (velocity > 0.3) {
          targetIdx = resolved.findIndex((d) => d.heightPx > sheetHeight.value + 8);
          if (targetIdx < 0) targetIdx = resolved.length - 1;
        } else if (velocity < -0.2) {
          const revIdx = [...resolved].reverse().findIndex((d) => d.heightPx < sheetHeight.value - 8);
          targetIdx = revIdx >= 0 ? resolved.length - 1 - revIdx : 0;
        } else {
          targetIdx = nearestDetentIndex(resolved, sheetHeight.value);
        }
        sheetHeight.value = resolved[targetIdx].heightPx;
        activeDetentId.value = resolved[targetIdx].id;
        emit('detent-change', activeDetentId.value);
      }
    } else {
      const maxPx = window.innerHeight * SHEET_DRAG_VIEWPORT_FRACTION;
      sheetHeight.value = Math.max(minHeight, Math.min(sheetHeight.value + velocity * 200, maxPx));
    }
    updateScrollHint();
    if (import.meta.env.DEV) {
      const vh = window.innerHeight;
      console.log(
        `[BottomSheet] drag-end height=${sheetHeight.value.toFixed(0)}px  ${((sheetHeight.value / vh) * 100).toFixed(1)}% of vh (vh=${vh}px)  detent=${activeDetentId.value}`
      );
    }
  }
}

// ── Drag via native pointer events ──
// This is also the reference path for the mini-map resize gesture. Keep mouse
// and touch in the shared pointer composable so capture and activation remain
// consistent; do not add a separate touch-only drag implementation here.
const sheetDragOptions = {
  activationThresholdPx: SHEET_DRAG_ACTIVATION_THRESHOLD_PX,
  axis: 'y' as const,
  preventDefaultOnDrag: true,
};
usePointerDrag(handleEl, onSheetDrag, sheetDragOptions);
usePointerDrag(haloEl, onSheetDrag, sheetDragOptions);

// ── Programmatic detent jump ──
/** Move to a detent on every call, even when it was already requested before. */
function snapTo(id: string): boolean {
  if (
    !isOpen.value ||
    isPreparing.value ||
    isDragging.value ||
    isBackgrounded.value ||
    isFullscreen.value ||
    isNativeFullscreen.value
  )
    return false;
  const target = getResolvedDetents().find((detent) => detent.id === id);
  if (!target) return false;
  minimumOpeningHeight = 0;
  cancelOpening();
  fitBaseline = target.heightPx;
  snapToDetent({
    id: target.id,
    heightPx: Math.min(target.heightPx, availableViewportHeight() * SHEET_VIEWPORT_LIMIT),
  });
  return true;
}

/** Restore an arbitrary height within the same bounds used by dragging. */
function resizeTo(heightPx: number): boolean {
  if (
    !Number.isFinite(heightPx) ||
    !isOpen.value ||
    isPreparing.value ||
    isDragging.value ||
    isBackgrounded.value ||
    isFullscreen.value ||
    isNativeFullscreen.value
  )
    return false;
  const resolved = getInteractionDetents();
  const lower = !props.dragToClose && resolved.length > 0 ? resolved[0].heightPx : minHeight;
  const upper = Math.min(
    resolved.at(-1)?.heightPx ?? window.innerHeight * SHEET_DRAG_VIEWPORT_FRACTION,
    availableViewportHeight() * SHEET_VIEWPORT_LIMIT
  );
  suspendAutomaticFit();
  sheetHeight.value = Math.min(upper, Math.max(lower, heightPx));
  activeDetentId.value = '';
  updateScrollHint();
  return true;
}

watch(
  () => props.selectedDetent,
  (val) => {
    if (
      val == null ||
      !isOpen.value ||
      isPreparing.value ||
      isDragging.value ||
      isFullscreen.value ||
      isNativeFullscreen.value
    )
      return;
    const resolved = getResolvedDetents();
    if (resolved.length === 0) return;
    const idx = findDetentIndex(resolved, val);
    activeDetentId.value = resolved[idx].id;
    fitBaseline = resolved[idx].heightPx;
    if (canFitAutomatically()) scheduleContentFit(true);
    else sheetHeight.value = Math.min(fitBaseline, availableViewportHeight() * SHEET_VIEWPORT_LIMIT);
    emit('detent-change', activeDetentId.value);
  },
  { flush: 'post' }
);

watch(
  () => props.fitContent,
  (fit) => {
    if (fit && !inheritedLayout.value) manuallySized = false;
    observeFitContent();
    scheduleContentFit(true);
  },
  { flush: 'post' }
);

watch(
  () => props.fitSmallOverflow,
  () => {
    observeFitContent();
    scheduleContentFit(true);
  }
);

onUnmounted(() => {
  cancelOpening();
  _popEscape(handleEscape);
  _popOpenSheet(sheetId);
  if (layoutFrame !== null) {
    window.cancelAnimationFrame(layoutFrame);
    layoutFrame = null;
  }
  if (scrollTarget.value && scrollTargetListener) {
    scrollTarget.value.removeEventListener('scroll', scrollTargetListener);
  }
  scrollTarget.value?.classList.remove(SHEET_SCROLL_TARGET_CLASS);
  stopObservingScrollResize?.();
  stopObservingScrollResize = null;
  stopObservingSheetLayout?.();
  stopObservingSheetLayout = null;
  stopContentObserver();
});

defineExpose({ snapTo, resizeTo });
</script>

<style scoped>
/* ─── Backdrop ─── */
.sheet-backdrop {
  position: fixed;
  inset: 0;
  z-index: var(--z-bottom-sheet);
  background: var(--backdrop);
  -webkit-tap-highlight-color: transparent;
}
.sheet-fade-enter-active,
.sheet-fade-leave-active {
  transition: opacity 0.3s ease;
}
.sheet-fade-enter-from,
.sheet-fade-leave-to {
  opacity: 0;
}

/* ─── Sheet ─── */
.sheet {
  /* ── Layout spacing tokens (single source of truth) ───────── */
  --bs-handle-pt: 0.6rem;
  --bs-handle-pb: 0.25rem;
  --bs-handle-w: 2.5rem;
  --bs-header-pt: 0.5rem;
  --bs-header-px: 1rem;
  --bs-header-pb: 0.4rem;
  --bs-header-gap: 0.75rem;
  --bs-actions-gap: 0.4rem;
  --bs-btn-size: 1.6rem;
  --bs-btn-close-icon: var(--text-xs-size);
  --bs-btn-fs-icon: var(--text-xs-size);
  --bs-body-pb: 0.75rem;
  --bs-drag-target-min-h: 0px;
  --bs-float-top: 0.45rem;
  --bs-float-right: 0.85rem;

  position: fixed;
  z-index: var(--z-bottom-sheet-content);
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  background: var(--surface-sheet-solid);
  border-top-left-radius: 1.25rem;
  border-top-right-radius: 1.25rem;
  box-shadow: var(--shadow-sheet);
  color: var(--text-secondary);
  font-family: var(--font-family-ui);
  transition: height 0.45s cubic-bezier(0.22, 1, 0.36, 1);
  overflow: hidden;
  padding-bottom: max(env(safe-area-inset-bottom, 0px), 0.5rem);
}

.sheet-stack-scrim {
  position: absolute;
  inset: 0;
  z-index: 4;
  background: rgba(15, 23, 42, 0.16);
  pointer-events: auto;
  -webkit-tap-highlight-color: transparent;
}

[data-theme='dark'] .sheet-stack-scrim {
  background: rgba(0, 0, 0, 0.28);
}

.sheet--solid-over-map {
  background: var(--surface-sheet-solid);
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
}

.sheet--hidden {
  pointer-events: none;
  visibility: hidden;
}

.sheet--preparing {
  visibility: hidden;
  pointer-events: none;
  transition: none !important;
}

@media (prefers-reduced-motion: reduce) {
  .sheet {
    transition: none;
  }
}

.sheet-drag-halo {
  position: fixed;
  z-index: var(--z-bottom-sheet-content);
  left: 0;
  right: 0;
  bottom: var(--sheet-current-height, 0px);
  height: var(--sheet-drag-halo-height, 10px);
  cursor: grab;
  touch-action: none;
  -webkit-tap-highlight-color: transparent;
}

.sheet--dragging {
  transition: none !important;
  cursor: grabbing;
}

/* ─── Drag zone (handle + header wrapper) ─── */
/* Grid overlay: handle bar and header share one row to save vertical space. */
.sheet-drag-zone {
  flex: 0 0 auto;
  display: grid;
  grid-template-columns: 1fr;
  min-height: var(--bs-drag-target-min-h);
  cursor: grab;
  touch-action: none;
  -webkit-user-select: none;
  user-select: none;
}

.sheet--dragging .sheet-drag-zone {
  cursor: grabbing;
}

/* ─── Handle ─── */
.sheet-handle-zone {
  grid-row: 1;
  grid-column: 1;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: var(--bs-handle-pt) 0 var(--bs-handle-pb);
  pointer-events: none; /* drag captured by parent */
}

.sheet-handle {
  width: var(--bs-handle-w);
  height: 0.25rem;
  border-radius: 2px;
  background: var(--border-hover);
  transition: background 0.2s;
}
.sheet-handle-zone:hover .sheet-handle {
  background: var(--border-hover);
}

/* ─── Header ─── */
.sheet-header {
  grid-row: 1;
  grid-column: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
  max-width: 100%;
  padding: var(--bs-header-pt) var(--bs-header-px) var(--bs-header-pb);
  gap: var(--bs-header-gap);
  min-height: 0;
}

.sheet-header-content {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
}

.sheet-back-btn {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 2.75rem;
  height: 2.75rem;
  padding: 0;
  border: 0;
  border-radius: 0.5rem;
  background: transparent;
  color: var(--text-primary);
  font-size: var(--text-lg-size);
  cursor: pointer;
}

.sheet-back-btn:hover {
  background: var(--surface-hover);
}

.sheet-back-btn:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

.sheet-title {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  max-width: 100%;
  font-size: var(--text-sm-size);
  font-weight: 600;
  letter-spacing: 0.01em;
  text-transform: none;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sheet-window-actions {
  display: flex;
  align-items: center;
  gap: var(--bs-actions-gap);
  flex-shrink: 0;
}

.sheet-floating-actions {
  position: absolute;
  top: var(--bs-float-top);
  right: var(--bs-float-right);
  display: flex;
  align-items: center;
  gap: var(--bs-actions-gap);
  z-index: 1;
}

.sheet-fullscreen-btn,
.sheet-native-fullscreen-btn,
.sheet-close-btn {
  position: relative;
  background: var(--surface-hover);
  border: 1px solid var(--border-medium);
  color: var(--text-muted);
  width: var(--bs-btn-size);
  height: var(--bs-btn-size);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.15s;
  flex-shrink: 0;
}
.sheet-fullscreen-btn,
.sheet-native-fullscreen-btn {
  font-size: var(--bs-btn-fs-icon);
}
.sheet-close-btn {
  font-size: var(--bs-btn-close-icon);
}
.sheet-fullscreen-btn::after,
.sheet-native-fullscreen-btn::after,
.sheet-close-btn::after {
  content: '';
  position: absolute;
  inset: -0.55rem;
}
.sheet-fullscreen-btn:hover,
.sheet-native-fullscreen-btn:hover,
.sheet-close-btn:hover {
  color: var(--text-primary);
  background: var(--surface-active);
}

.sheet--header-compact {
  --bs-handle-pt: 0.45rem;
  --bs-handle-pb: 0.1rem;
  --bs-header-pt: 0.35rem;
  --bs-header-px: 0.85rem;
  --bs-header-pb: 0.3rem;
  --bs-header-gap: 0.55rem;
  --bs-btn-size: 1.5rem;
  --bs-btn-close-icon: var(--text-xs-size);
  --bs-btn-fs-icon: var(--text-xs-size);
  --bs-float-top: 0.38rem;
  --bs-float-right: 0.7rem;
  --bs-actions-gap: 0.35rem;
}

/* ─── Body ─── */
/* The sheet body is the safe fallback scroll container. Simple sheet content
   scrolls here automatically; structured screens may provide a nested target.

   !! SCROLL CHAIN CONTRACT !!
   For a NESTED target to scroll, every ancestor must propagate the height
   constraint. The required chain is:

     .sheet            → overflow: hidden, fixed px height
     .sheet-body-frame → flex: 1 1 auto; min-height: 0; overflow: clip
     .sheet-body       → flex: 1 1 auto; min-height: 0; overflow-y: auto
     [slot root]       → MUST have: display:flex; flex-direction:column;
                                     flex:1 1 auto; min-height:0; overflow:hidden
     [scroll target]   → flex: 1 1 auto; min-height: 0; overflow-y: auto

   If that nested chain is incomplete, .sheet-body remains scrollable instead
   of clipping the content. */
.sheet-body-frame {
  position: relative;
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  min-height: 0;
  /* This frame clips the scroll hint; only the body or its nested target should
     scroll. Hidden would also allow focus/scrollIntoView to shift this frame. */
  overflow: clip;
}

.sheet-body {
  flex: 1 1 auto;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior-y: contain;
  display: flex;
  flex-direction: column;
  padding: 0 0 var(--bs-body-pb);
  touch-action: pan-y;
}

.sheet-pinned {
  position: relative;
  z-index: 2;
  flex: 0 0 auto;
  min-width: 0;
  background: inherit;
}

.sheet-footer {
  position: relative;
  z-index: 2;
  flex: 0 0 auto;
  min-width: 0;
  background: inherit;
}

:global(.sheet-scroll-target) {
  overscroll-behavior-y: contain;
  touch-action: pan-y;
  -webkit-overflow-scrolling: touch;
}

@media (min-width: 769px) {
  .sheet {
    --bs-handle-pt: 0.7rem;
    --bs-header-pt: 0.6rem;
    --bs-header-pb: 0.5rem;
    --bs-body-pb: 0.85rem;
  }
}

@media (max-width: 768px) {
  .sheet {
    --bs-handle-pt: 0.4rem;
    --bs-handle-pb: 0.35rem;
    --bs-handle-w: 3rem;
    --bs-drag-target-min-h: 44px;
    --bs-header-pt: 1.1rem;
  }
  .sheet--header-compact {
    --bs-handle-pt: 0.35rem;
    --bs-handle-pb: 0.25rem;
    --bs-header-pt: 0.9rem;
    --bs-header-px: 0.7rem;
    --bs-header-pb: 0.25rem;
    --bs-actions-gap: 0.25rem;
    --bs-float-right: 0.55rem;
  }
}

/* ─── Scroll hint ─── */
.sheet-scroll-hint {
  position: absolute;
  inset: auto 0 0;
  height: 4.5rem;
  background: linear-gradient(to bottom, transparent 0%, var(--surface-glass-heavy) 60%);
  pointer-events: none;
  z-index: 1;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding-bottom: 0.5rem;
}

.sheet-scroll-hint__button {
  display: inline-flex;
  width: 2.25rem;
  height: 2.25rem;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: var(--text-secondary);
  font: inherit;
  pointer-events: auto;
  cursor: pointer;
}

.sheet-scroll-hint__label {
  display: none;
}

.sheet-scroll-hint__button:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 1px;
}

.sheet-scroll-hint__icon {
  color: inherit;
  font-size: var(--text-lg-size);
  animation: scroll-hint-bounce 1.6s ease-in-out infinite;
  opacity: 0.85;
  pointer-events: none;
}

@media (min-width: 769px) {
  .sheet-scroll-hint {
    height: 5.25rem;
    padding-bottom: 0.75rem;
  }

  .sheet-scroll-hint__button {
    width: auto;
    height: 2rem;
    gap: 0.4rem;
    padding: 0 0.75rem 0 0.85rem;
    border: 1px solid var(--border-medium);
    background: var(--surface-sheet-solid);
    box-shadow: var(--shadow-sm);
    font-size: var(--text-xs-size);
    font-weight: var(--font-semibold);
  }

  .sheet-scroll-hint__button:hover {
    border-color: var(--border-hover);
    background: var(--surface-hover);
    color: var(--text-primary);
  }

  .sheet-scroll-hint__label {
    display: inline;
  }

  .sheet-scroll-hint__icon {
    font-size: var(--text-sm-size);
  }
}

@keyframes scroll-hint-bounce {
  0%,
  100% {
    transform: translateY(0);
    opacity: 0.85;
  }
  50% {
    transform: translateY(4px);
    opacity: 0.55;
  }
}

/* ─── Desktop: limit max width, center ─── */
@media (min-width: 769px) {
  .sheet {
    left: 50%;
    right: auto;
    width: min(var(--sheet-desktop-max-width), calc(100vw - (2 * var(--sheet-inline-gap))));
    margin-left: calc(-0.5 * min(var(--sheet-desktop-max-width), calc(100vw - (2 * var(--sheet-inline-gap)))));
    border-top-left-radius: 1.25rem;
    border-top-right-radius: 1.25rem;
  }

  .sheet.sheet--desktop-compact {
    width: min(var(--sheet-compact-desktop-max-width), calc(100vw - (2 * var(--sheet-inline-gap))));
    margin-left: calc(-0.5 * min(var(--sheet-compact-desktop-max-width), calc(100vw - (2 * var(--sheet-inline-gap)))));
  }
}

/* ─── Large desktop: scale with screen width ─── */
@media (min-width: 1400px) {
  .sheet {
    width: min(var(--sheet-desktop-wide-max-width), var(--sheet-desktop-wide-width));
    margin-left: calc(-0.5 * min(var(--sheet-desktop-wide-max-width), var(--sheet-desktop-wide-width)));
  }

  .sheet.sheet--desktop-compact {
    width: min(var(--sheet-compact-desktop-wide-max-width), var(--sheet-compact-desktop-wide-width));
    margin-left: calc(-0.5 * min(var(--sheet-compact-desktop-wide-max-width), var(--sheet-compact-desktop-wide-width)));
  }
}

/* ─── Panel maximize and browser fullscreen ─── */
.sheet .sheet-labeled-size-btn {
  width: auto;
  height: auto;
  min-height: 2rem;
  padding: 0.25rem 0.65rem;
  gap: 0.35rem;
  font: inherit;
  font-size: var(--text-xs-size);
  white-space: nowrap;
  border-radius: 8px;
}

.sheet--immersive .sheet-drag-zone {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  z-index: 6;
  transform: translateY(0);
  transition:
    transform 0.2s ease,
    opacity 0.2s ease;
}

.sheet--controls-hidden .sheet-drag-zone {
  opacity: 0;
  transform: translateY(-100%);
  pointer-events: none;
}

.sheet-toolbar-reveal {
  position: absolute;
  inset: 0 0 auto;
  height: 48px;
  z-index: 5;
}
.sheet-fullscreen-status {
  margin: 0;
  padding: 0.4rem 1rem;
  color: var(--mp-text, var(--text-secondary));
  background: var(--mp-surface, var(--surface-sheet-solid));
  font-size: var(--text-sm-size);
}

@media (prefers-reduced-motion: reduce) {
  .sheet--immersive .sheet-drag-zone {
    transition: none;
  }
}

.sheet--fullscreen,
.sheet--native-fullscreen {
  border-radius: 0;
  left: 0 !important;
  right: 0 !important;
  width: 100% !important;
  margin-left: 0 !important;
  margin-right: 0 !important;
  padding-top: env(safe-area-inset-top, 0px);
}
.sheet--fullscreen .sheet-handle-zone,
.sheet--native-fullscreen .sheet-handle-zone {
  display: none;
}

/* ─── Desktop with nav panel: offset centering ─── */
.sheet--viewport-centered {
  --nav-panel-w: 0px;
}

@media (min-width: 1024px) {
  .sheet-backdrop {
    left: var(--nav-panel-w, 64px);
  }

  .sheet {
    left: var(--nav-panel-w, 64px);
    right: 0;
    width: min(var(--sheet-desktop-max-width), calc(100vw - var(--nav-panel-w, 64px) - (2 * var(--sheet-inline-gap))));
    margin-left: auto;
    margin-right: auto;
  }
  .sheet.sheet--desktop-compact {
    width: min(
      var(--sheet-compact-desktop-max-width),
      calc(100vw - var(--nav-panel-w, 64px) - (2 * var(--sheet-inline-gap)))
    );
    margin-left: auto;
    margin-right: auto;
  }
  .sheet--fullscreen {
    left: var(--nav-panel-w, 64px) !important;
    right: 0 !important;
    width: calc(100% - var(--nav-panel-w, 64px)) !important;
    margin-left: 0 !important;
    margin-right: 0 !important;
  }
}

/* ─── Large desktop with nav panel: scale with screen ─── */
@media (min-width: 1400px) {
  .sheet {
    left: var(--nav-panel-w, 64px);
    right: 0;
    width: min(var(--sheet-desktop-wide-max-width), calc(var(--sheet-desktop-wide-width) - var(--nav-panel-w, 64px)));
    margin-left: auto;
    margin-right: auto;
  }
  .sheet.sheet--desktop-compact {
    width: min(
      var(--sheet-compact-desktop-wide-max-width),
      calc(var(--sheet-compact-desktop-wide-width) - var(--nav-panel-w, 64px))
    );
    margin-left: auto;
    margin-right: auto;
  }
}
</style>
