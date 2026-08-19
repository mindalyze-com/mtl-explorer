<template>
  <Teleport to="body">
    <!-- Backdrop: hidden when noBackdrop is set (e.g. Measure tool needs map interaction) -->
    <transition name="sheet-fade">
      <div
        v-if="isOpen && !noBackdrop"
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
      :style="[sheetStyle, { zIndex: stackedZIndex }]"
      :class="[
        sheetClass,
        {
          'sheet--open': isOpen,
          'sheet--dragging': isDragging,
          'sheet--hidden': !isOpen && !isAnimatingOut,
          'sheet--backgrounded': isBackgrounded,
          'sheet--fullscreen': isFullscreen,
          'sheet--native-fullscreen': isNativeFullscreen,
          'sheet--header-compact': headerMode === 'compact',
          'sheet--viewport-centered': viewportCentered,
          'sheet--desktop-compact': desktopWidth === 'compact',
        },
      ]"
      @transitionend="onSheetTransitionEnd"
    >
      <!-- Drag zone: handle + header combined so the entire top area is draggable -->
      <div
        ref="handleEl"
        class="sheet-drag-zone"
        :inert="isBackgrounded ? true : undefined"
        :aria-hidden="isBackgrounded ? 'true' : undefined"
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
          <div v-if="hasHeader" class="sheet-header-content">
            <slot name="title">
              <span class="sheet-title"><i v-if="icon" :class="icon"></i>{{ title }}</span>
            </slot>
          </div>
          <div class="sheet-window-actions" data-sheet-drag-exclude>
            <slot v-if="hasHeader" name="header-actions"></slot>
            <button
              class="sheet-fullscreen-btn"
              :aria-label="isFullscreen ? 'Restore panel size' : 'Maximize panel'"
              :title="isFullscreen ? 'Restore panel size' : 'Maximize panel'"
              @click.stop="toggleFullscreen"
            >
              <i :class="isFullscreen ? 'bi bi-arrows-angle-contract' : 'bi bi-arrows-angle-expand'"></i>
            </button>
            <button
              v-if="nativeFullscreen"
              class="sheet-native-fullscreen-btn"
              :aria-label="isNativeFullscreen ? 'Exit fullscreen' : 'Enter fullscreen'"
              :title="isNativeFullscreen ? 'Exit fullscreen' : 'Enter fullscreen'"
              @click.stop="toggleNativeFullscreen"
            >
              <i :class="isNativeFullscreen ? 'bi bi-fullscreen-exit' : 'bi bi-fullscreen'"></i>
            </button>
            <button class="sheet-close-btn" aria-label="Close" @click.stop="close">
              <i class="bi bi-x-lg"></i>
            </button>
          </div>
        </div>
      </div>

      <div class="sheet-body-frame">
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
import { shallowReactive } from 'vue';

// ── Module-level Escape-key stack ──────────────────────────────────────────
// Each open sheet instance pushes its close callback here.
// One shared keydown listener fires; only the topmost sheet (last in stack)
// is closed, so nested sheets collapse one at a time.
const _escapeStack: Array<() => void> = [];

const DEFAULT_SHEET_Z_INDEX = 5001;
const SHEET_SCROLL_TARGET_CLASS = 'sheet-scroll-target';

interface OpenSheetRecord {
  id: number;
  zIndex: number;
  openOrder: number;
}

let _nextSheetId = 1;
let _nextSheetOpenOrder = 1;
const _openSheets = shallowReactive<OpenSheetRecord[]>([]);

function _onEscape(e: KeyboardEvent) {
  if (e.key !== 'Escape') return;
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

function _pushOpenSheet(id: number, zIndex: number) {
  const existingIdx = _openSheets.findIndex((record) => record.id === id);
  if (existingIdx !== -1) _openSheets.splice(existingIdx, 1);
  _openSheets.push({ id, zIndex, openOrder: _nextSheetOpenOrder++ });
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
import { ref, computed, watch, nextTick, onMounted, onUnmounted, useSlots } from 'vue';
import { isPointerDragExcluded, usePointerDrag, type DragState } from '@/composables/usePointerDrag';

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
    headerMode?: 'default' | 'compact';
    /** Detent positions the sheet can rest at, ordered low → high.
     *  Each entry is a named preset ('small'|'medium'|'large') or
     *  { id?, height } where height is a CSS length string or a 0–1 fraction. */
    detents?: Detent[];
    /** Which detent to open at — an id string or a 0-based index. */
    initialDetent?: string | number;
    /** When true, open at the content height even when detents are available. */
    fitContentInitial?: boolean;
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
    /** Show a browser-fullscreen control independently from the panel maximize control. */
    nativeFullscreen?: boolean;
    /** Override the CSS z-index for stacking sheets on top of each other. */
    zIndex?: number;
  }>(),
  {
    title: '',
    icon: '',
    headerMode: 'default',
    detents: () => [],
    initialDetent: 0,
    fitContentInitial: false,
    selectedDetent: undefined,
    backgroundDetent: undefined,
    noBackdrop: true,
    noScrollHint: false,
    scrollHintLabel: 'More content',
    sheetClass: '',
    viewportCentered: false,
    desktopWidth: 'standard',
    nativeFullscreen: false,
    zIndex: undefined,
  }
);

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void;
  (e: 'closed'): void;
  (e: 'detent-change', id: string): void;
  (e: 'layout-change', value: BottomSheetLayoutState): void;
}>();

const sheetId = _nextSheetId++;
const sheetEl = ref<HTMLElement | null>(null);
const bodyEl = ref<HTMLElement | null>(null);
const footerEl = ref<HTMLElement | null>(null);
const isDragging = ref(false);
const isAnimatingOut = ref(false);
const hasScrollableOverflow = ref(false);
const showScrollHint = ref(false);
const isFullscreen = ref(false);
const isNativeFullscreen = ref(false);
const activeDetentId = ref('');

// Desktop detection for the mobile drag affordance.
const DESKTOP_BP = 769;
const SHEET_DRAG_HALO_PX = 10;
const SHEET_DRAG_ACTIVATION_THRESHOLD_PX = 6;
const SCROLL_OVERFLOW_TOLERANCE_PX = 4;
const isDesktopWidth = ref(typeof window !== 'undefined' ? window.innerWidth >= DESKTOP_BP : false);
let viewportResizeFrame: number | null = null;

function onWindowResize() {
  isDesktopWidth.value = window.innerWidth >= DESKTOP_BP;
  if (!isOpen.value || isDragging.value || isFullscreen.value || isNativeFullscreen.value) return;
  if (viewportResizeFrame !== null) window.cancelAnimationFrame(viewportResizeFrame);
  viewportResizeFrame = window.requestAnimationFrame(() => {
    viewportResizeFrame = null;
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
        sheetHeight.value = activeDetent?.heightPx ?? Math.min(sheetHeight.value, window.innerHeight * 0.98);
      }
    }
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
onUnmounted(() => {
  window.removeEventListener('resize', onWindowResize);
  window.visualViewport?.removeEventListener('resize', onWindowResize);
  document.removeEventListener('fullscreenchange', onDocumentFullscreenChange);
  if (document.fullscreenElement === sheetEl.value) void document.exitFullscreen().catch(() => undefined);
  if (viewportResizeFrame !== null) window.cancelAnimationFrame(viewportResizeFrame);
});

function leaveFullscreenState() {
  if (!isFullscreen.value) return;
  isFullscreen.value = false;
  sheetHeight.value = computeInitialHeight();
  emit('detent-change', activeDetentId.value);
}

function toggleFullscreen() {
  if (isFullscreen.value) {
    leaveFullscreenState();
    return;
  }

  if (isNativeFullscreen.value) leaveNativeFullscreen();
  isFullscreen.value = true;
}

function leaveNativeFullscreenState() {
  isNativeFullscreen.value = false;
}

function leaveNativeFullscreen() {
  const ownsBrowserFullscreen = document.fullscreenElement === sheetEl.value;
  leaveNativeFullscreenState();
  if (ownsBrowserFullscreen) void document.exitFullscreen().catch(() => undefined);
}

function toggleNativeFullscreen() {
  if (isNativeFullscreen.value) {
    leaveNativeFullscreen();
    return;
  }

  if (isFullscreen.value) leaveFullscreenState();
  isNativeFullscreen.value = true;
  if (sheetEl.value?.requestFullscreen) {
    void sheetEl.value.requestFullscreen().catch(() => {
      // Keep the complete-viewport fallback when native fullscreen is unavailable.
    });
  }
}

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
const headerMode = computed(() => props.headerMode);
const hasHeader = computed(() => Boolean(props.title || slots.title));
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
const showDragHalo = computed(
  () =>
    isOpen.value && !isBackgrounded.value && !isFullscreen.value && !isNativeFullscreen.value && !isDesktopWidth.value
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
  return resolveDetents(props.detents ?? [], window.innerHeight);
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
  const vh = window.innerHeight;
  const defs = props.detents;

  if (!defs || defs.length === 0) {
    return computeAutoFitHeight(vh);
  }

  const resolved = getResolvedDetents();
  if (props.fitContentInitial) {
    const maxDetentHeight = resolved[resolved.length - 1]?.heightPx ?? vh * 0.92;
    const minDetentHeight = resolved[0]?.heightPx ?? minHeight;
    const fallbackDetentHeight = resolved[findDetentIndex(resolved, props.initialDetent)]?.heightPx ?? minDetentHeight;
    const measuredHeight = computeContentIdealHeight(fallbackDetentHeight);
    const idealHeight = measuredHeight <= minDetentHeight + 1 ? fallbackDetentHeight : measuredHeight;
    activeDetentId.value = FIT_CONTENT_DETENT_ID;
    return Math.max(minDetentHeight, Math.min(idealHeight, maxDetentHeight));
  }

  const idx = findDetentIndex(resolved, props.initialDetent);
  activeDetentId.value = resolved[idx].id;
  return resolved[idx].heightPx;
}

function computeContentIdealHeight(fallbackHeight = 200): number {
  if (!sheetEl.value) {
    return fallbackHeight;
  }

  const dragZone = sheetEl.value.querySelector('.sheet-drag-zone') as HTMLElement | null;
  const body = bodyEl.value;
  const chromeH = (dragZone?.offsetHeight ?? 0) + 12;
  const contentH = body ? body.scrollHeight : 200;
  const footerH = footerEl.value?.offsetHeight ?? 0;
  return chromeH + contentH + footerH;
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
  sheetHeight.value = restoreState.heightPx;
  activeDetentId.value = restoreState.activeDetentId;
  updateScrollHint();
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
let sizeObserver: ResizeObserver | null = null;
let sheetLayoutObserver: ResizeObserver | null = null;
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
  scrollTarget.value = findScrollableChild(bodyEl.value);
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
}

function scheduleLayoutEmit() {
  if (typeof window === 'undefined' || layoutFrame !== null) return;
  layoutFrame = window.requestAnimationFrame(() => {
    layoutFrame = null;
    emitLayoutChange();
  });
}

function emitLayoutChange() {
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
  if (event.propertyName === 'height') scheduleLayoutEmit();
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
  if (sizeObserver) {
    sizeObserver.disconnect();
    sizeObserver = null;
  }
  if (newEl) {
    newEl.classList.add(SHEET_SCROLL_TARGET_CLASS);
    scrollTargetListener = updateScrollHint;
    newEl.addEventListener('scroll', scrollTargetListener, { passive: true });
    sizeObserver = new ResizeObserver(() => updateScrollHint());
    sizeObserver.observe(newEl);
  }
});

// Observe DOM mutations inside bodyEl so dynamic content changes (e.g. tab
// switches, lazy-loaded content) re-resolve the scroll target.
function startContentObserver() {
  stopContentObserver();
  if (!bodyEl.value) return;
  contentObserver = new MutationObserver(() => {
    resolveScrollTarget();
    updateScrollHint();
  });
  contentObserver.observe(bodyEl.value, { childList: true, subtree: true });
}
function stopContentObserver() {
  if (contentObserver) {
    contentObserver.disconnect();
    contentObserver = null;
  }
}

watch(
  () => props.modelValue,
  async (open) => {
    if (open) {
      _pushOpenSheet(sheetId, effectiveZIndex.value);
      isAnimatingOut.value = false;
      sheetHeight.value = 0; // start hidden, will animate
      await nextTick();
      // Measure after render, then animate to ideal
      await nextTick();
      sheetHeight.value = computeInitialHeight();
      if (activeDetentId.value) emit('detent-change', activeDetentId.value);
      if (import.meta.env.DEV) {
        const vh = window.innerHeight;
        console.log(
          `[BottomSheet] height=${sheetHeight.value.toFixed(0)}px  ${((sheetHeight.value / vh) * 100).toFixed(1)}% of vh (vh=${vh}px)  detent=${activeDetentId.value}`
        );
      }
      resolveScrollTarget();
      startContentObserver();
      updateScrollHint();
      scheduleLayoutEmit();
      _pushEscape(handleEscape);
    } else {
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
    sheetLayoutObserver?.disconnect();
    sheetLayoutObserver = null;
    if (newEl) {
      sheetLayoutObserver = new ResizeObserver(() => {
        scheduleLayoutEmit();
        resolveScrollTarget();
        updateScrollHint();
      });
      sheetLayoutObserver.observe(newEl);
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
    if (!isOpen.value) return;
    if (backgrounded && props.backgroundDetent != null) {
      collapseToBackgroundDetent();
    } else {
      restoreFromBackgroundDetent();
    }
  },
  { flush: 'post' }
);

function close() {
  if (document.fullscreenElement === sheetEl.value) {
    void document.exitFullscreen().catch(() => undefined);
  }
  isAnimatingOut.value = true;
  isFullscreen.value = false;
  isNativeFullscreen.value = false;
  sheetHeight.value = 0;
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
  if (isDragging.value || isBackgrounded.value || isFullscreen.value) return;
  const resolved = getResolvedDetents();
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
  if (first) {
    isDragging.value = true;
    dragStartHeight = sheetHeight.value;
  }

  if (dragging) {
    const resolved = getResolvedDetents();
    const maxPx = resolved.length > 0 ? resolved[resolved.length - 1].heightPx : window.innerHeight * 0.92;
    // my < 0 when finger moves up → sheet grows
    sheetHeight.value = Math.max(minHeight * 0.5, Math.min(dragStartHeight - my, maxPx));
  }

  if (last) {
    isDragging.value = false;
    suppressDragZoneClickOnce();
    const velocity = -(vel * dy);

    if (velocity < -0.5) {
      close();
      return;
    }
    if (sheetHeight.value < minHeight) {
      close();
      return;
    }

    const resolved = getResolvedDetents();
    if (resolved.length > 0) {
      if (vel > 0.1) {
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
      const maxPx = window.innerHeight * 0.92;
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
watch(
  () => props.selectedDetent,
  (val) => {
    if (val == null || !isOpen.value || isDragging.value || isFullscreen.value) return;
    const resolved = getResolvedDetents();
    if (resolved.length === 0) return;
    const idx = findDetentIndex(resolved, val);
    sheetHeight.value = resolved[idx].heightPx;
    activeDetentId.value = resolved[idx].id;
    emit('detent-change', activeDetentId.value);
  }
);

onUnmounted(() => {
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
  if (sizeObserver) {
    sizeObserver.disconnect();
    sizeObserver = null;
  }
  if (sheetLayoutObserver) {
    sheetLayoutObserver.disconnect();
    sheetLayoutObserver = null;
  }
  stopContentObserver();
});
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
  background: var(--surface-glass-heavy);
  backdrop-filter: var(--blur-sheet);
  -webkit-backdrop-filter: var(--blur-sheet);
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

.sheet-drag-halo {
  position: fixed;
  z-index: var(--z-bottom-sheet-content);
  left: 0;
  right: 0;
  bottom: calc(var(--sheet-current-height, 0px) + max(env(safe-area-inset-bottom, 0px), 0.5rem));
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
     .sheet-body-frame → flex: 1 1 auto; min-height: 0; overflow: hidden
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
  overflow: hidden;
}

.sheet-body {
  flex: 1 1 auto;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  padding: 0 0 var(--bs-body-pb);
  touch-action: pan-y;
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
