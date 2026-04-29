<template>
  <Teleport to="body">
    <!-- Backdrop: hidden when noBackdrop is set (e.g. Measure tool needs map interaction) -->
    <transition name="sheet-fade">
      <div
        v-if="isOpen && !noBackdrop"
        class="sheet-backdrop"
        :style="zIndex != null ? { zIndex: zIndex - 1 } : undefined"
        @click="close"
      ></div>
    </transition>

    <!-- Sheet -->
    <div
      ref="sheetEl"
      class="sheet"
      :style="[sheetStyle, zIndex != null ? { zIndex } : {}]"
      :class="{
        'sheet--open': isOpen,
        'sheet--dragging': isDragging,
        'sheet--hidden': !isOpen && !isAnimatingOut,
        'sheet--fullscreen': isFullscreen,
        'sheet--header-compact': headerMode === 'compact',
      }"
    >
      <!-- Drag zone: handle + header combined so the entire top area is draggable -->
      <div ref="handleEl" class="sheet-drag-zone">
        <div class="sheet-handle-zone">
          <div class="sheet-handle"></div>
        </div>

        <!-- Header -->
        <div v-if="hasHeader" class="sheet-header">
          <div class="sheet-header-content">
            <slot name="title">
              <span class="sheet-title"><i v-if="icon" :class="icon"></i>{{ title }}</span>
            </slot>
          </div>
          <div class="sheet-header-actions">
            <slot name="header-actions"></slot>
            <button class="sheet-fullscreen-btn" @click.stop="toggleFullscreen" :aria-label="isFullscreen ? 'Exit fullscreen' : 'Fullscreen'">
              <i :class="isFullscreen ? 'bi bi-fullscreen-exit' : 'bi bi-arrows-fullscreen'"></i>
            </button>
            <button class="sheet-close-btn" @click.stop="close" aria-label="Close">
              <i class="bi bi-x-lg"></i>
            </button>
          </div>
        </div>

        <div v-else-if="headerMode === 'compact'" class="sheet-floating-actions">
          <button class="sheet-fullscreen-btn" @click.stop="toggleFullscreen" :aria-label="isFullscreen ? 'Exit fullscreen' : 'Fullscreen'">
            <i :class="isFullscreen ? 'bi bi-fullscreen-exit' : 'bi bi-arrows-fullscreen'"></i>
          </button>
          <button class="sheet-close-btn" @click.stop="close" aria-label="Close">
            <i class="bi bi-x-lg"></i>
          </button>
        </div>
      </div>

      <!-- Content -->
      <div
        ref="bodyEl"
        class="sheet-body"
        @scroll="updateScrollHint"
      >
        <slot></slot>
      </div>

      <!-- Scroll fade hint -->
      <div v-if="showScrollHint && !noScrollHint" class="sheet-scroll-hint" @click="scrollDown">
        <i class="bi bi-chevron-down sheet-scroll-hint__icon"></i>
      </div>
    </div>
  </Teleport>
</template>

<script lang="ts">
// ── Module-level Escape-key stack ──────────────────────────────────────────
// Each open sheet instance pushes its close callback here.
// One shared keydown listener fires; only the topmost sheet (last in stack)
// is closed, so nested sheets collapse one at a time.
const _escapeStack: Array<() => void> = [];

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

// ── Detent types (exported for call-site type safety) ──
export type DetentPreset = 'small' | 'medium' | 'large';
export interface DetentDef { id?: string; height: string | number }
export type Detent = DetentPreset | DetentDef;
</script>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted, onUnmounted, useSlots } from 'vue';
import { usePointerDrag } from '@/composables/usePointerDrag';

const DETENT_PRESETS: Record<DetentPreset, string> = {
  small:  'clamp(180px, 30vh, 280px)',
  medium: 'clamp(380px, 55vh, 560px)',
  large:  'clamp(600px, 92vh, 92vh)',
};

const slots = useSlots();

const props = withDefaults(defineProps<{
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
  /** Programmatically jump to a detent (by id or index). */
  selectedDetent?: string | number;
  /** When true, no backdrop is rendered and clicks pass through to the map. */
  noBackdrop?: boolean;
  /** When true, the scroll-hint chevron at the bottom is never shown. */
  noScrollHint?: boolean;
  /** Override the CSS z-index for stacking sheets on top of each other. */
  zIndex?: number;
}>(), {
  headerMode: 'default',
  initialDetent: 0,
  noBackdrop: true,
  noScrollHint: false,
});

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void;
  (e: 'closed'): void;
  (e: 'detent-change', id: string): void;
}>();

const sheetEl = ref<HTMLElement | null>(null);
const bodyEl = ref<HTMLElement | null>(null);
const isDragging = ref(false);
const isAnimatingOut = ref(false);
const showScrollHint = ref(false);
const isFullscreen = ref(false);
const activeDetentId = ref('');

// Desktop detection for fullscreen button
const DESKTOP_BP = 769;
const isDesktopWidth = ref(typeof window !== 'undefined' ? window.innerWidth >= DESKTOP_BP : false);
function onWindowResize() { isDesktopWidth.value = window.innerWidth >= DESKTOP_BP; }
onMounted(() => window.addEventListener('resize', onWindowResize));
onUnmounted(() => window.removeEventListener('resize', onWindowResize));

function toggleFullscreen() {
  isFullscreen.value = !isFullscreen.value;
  if (!isFullscreen.value) {
    sheetHeight.value = computeInitialHeight();
    emit('detent-change', activeDetentId.value);
  }
}

// Free-form height in px — the sheet stays exactly where you drag it
const sheetHeight = ref(0);
const handleEl = ref<HTMLElement | null>(null);
let dragStartHeight = 0;

const isOpen = computed(() => props.modelValue);
const headerMode = computed(() => props.headerMode);
const hasHeader = computed(() => Boolean(props.title || slots.title));

const minHeight = 60; // px — below this we close

// ── Detent resolution ──
interface ResolvedDetent { id: string; heightPx: number }

function resolveCssLength(value: string | number, vh: number): number {
  if (typeof value === 'number') return vh * Math.min(Math.max(value, 0), 1);
  const el = document.createElement('div');
  el.style.cssText = `position:fixed;left:-9999px;top:-9999px;visibility:hidden;height:${value};pointer-events:none`;
  document.body.appendChild(el);
  const h = el.offsetHeight;
  el.remove();
  return h;
}

function resolveDetents(detents: Detent[], vh: number): ResolvedDetent[] {
  const maxPx = vh * 0.98; // hard cap — sheets must never exceed the screen
  return detents.map((d, i) => {
    if (typeof d === 'string') {
      return { id: d, heightPx: Math.min(resolveCssLength(DETENT_PRESETS[d], vh), maxPx) };
    }
    return { id: d.id ?? `detent-${i}`, heightPx: Math.min(resolveCssLength(d.height, vh), maxPx) };
  }).sort((a, b) => a.heightPx - b.heightPx);
}

function findDetentIndex(resolved: ResolvedDetent[], ref: string | number | undefined): number {
  if (ref == null) return 0;
  if (typeof ref === 'number') return Math.min(Math.max(Math.round(ref), 0), resolved.length - 1);
  const idx = resolved.findIndex(d => d.id === ref);
  return idx >= 0 ? idx : 0;
}

function getResolvedDetents(): ResolvedDetent[] {
  return resolveDetents(props.detents ?? [], window.innerHeight);
}

/** Compute the height the sheet should open at */
function computeInitialHeight(): number {
  const vh = window.innerHeight;
  const defs = props.detents;

  if (!defs || defs.length === 0) {
    return computeAutoFitHeight(vh);
  }

  const resolved = getResolvedDetents();
  const idx = findDetentIndex(resolved, props.initialDetent);
  activeDetentId.value = resolved[idx].id;
  return resolved[idx].heightPx;
}

/** Content-aware auto-fit when no detents are specified */
function computeAutoFitHeight(vh: number): number {
  const maxPx = vh * 0.92;

  if (!sheetEl.value) {
    return Math.min(vh * 0.35, maxPx);
  }

  const dragZone = sheetEl.value.querySelector('.sheet-drag-zone') as HTMLElement | null;
  const body = bodyEl.value;
  const chromeH = (dragZone?.offsetHeight ?? 0) + 12;
  const contentH = body ? body.scrollHeight : 200;
  const idealH = chromeH + contentH;

  const softCap = vh > 900 ? vh * 0.35 : maxPx;
  return Math.max(minHeight, Math.min(idealH, softCap, maxPx));
}

const sheetStyle = computed(() => {
  if (!isOpen.value && !isAnimatingOut.value) {
    return { height: '0px' };
  }
  if (isFullscreen.value) {
    return { height: '100dvh' } // to work correct on iphone
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

function findScrollableChild(root: HTMLElement): HTMLElement | null {
  const rootStyle = window.getComputedStyle(root);
  if ((rootStyle.overflowY === 'auto' || rootStyle.overflowY === 'scroll')
      && root.scrollHeight > root.clientHeight + 4) {
    return root;
  }

  const children = root.querySelectorAll('*');
  for (const child of children) {
    const style = window.getComputedStyle(child);
    if (style.overflowY === 'auto' || style.overflowY === 'scroll') {
      const el = child as HTMLElement;
      if (el.scrollHeight > el.clientHeight + 4) return el;
    }
  }
  return null;
}

function resolveScrollTarget() {
  if (!bodyEl.value) { scrollTarget.value = null; return; }
  scrollTarget.value = findScrollableChild(bodyEl.value);
}

function updateScrollHint() {
  const el = scrollTarget.value;
  if (!el) { showScrollHint.value = false; return; }
  const { scrollTop, scrollHeight, clientHeight } = el;
  showScrollHint.value = scrollHeight > clientHeight + 4
    && scrollTop < scrollHeight - clientHeight - 4;
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
  if (oldEl && scrollTargetListener) {
    oldEl.removeEventListener('scroll', scrollTargetListener);
  }
  if (sizeObserver) { sizeObserver.disconnect(); sizeObserver = null; }
  if (newEl) {
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
  if (contentObserver) { contentObserver.disconnect(); contentObserver = null; }
}

watch(() => props.modelValue, async (open) => {
  if (open) {
    isAnimatingOut.value = false;
    sheetHeight.value = 0; // start hidden, will animate
    await nextTick();
    document.body.style.overflow = 'hidden';
    // Measure after render, then animate to ideal
    await nextTick();
    sheetHeight.value = computeInitialHeight();
    if (activeDetentId.value) emit('detent-change', activeDetentId.value);
    if (import.meta.env.DEV) {
      const vh = window.innerHeight;
      console.log(`[BottomSheet] height=${sheetHeight.value.toFixed(0)}px  ${((sheetHeight.value / vh) * 100).toFixed(1)}% of vh (vh=${vh}px)  detent=${activeDetentId.value}`);
    }
    resolveScrollTarget();
    startContentObserver();
    updateScrollHint();
    _pushEscape(close);
  } else {
    _popEscape(close);
    document.body.style.overflow = '';
    showScrollHint.value = false;
    stopContentObserver();
  }
}, { immediate: true });

function close() {
  isAnimatingOut.value = true;
  isFullscreen.value = false;
  sheetHeight.value = 0;
  setTimeout(() => { isAnimatingOut.value = false; }, 350);
  emit('update:modelValue', false);
  emit('closed');
}

// ── Drag via native pointer events ──
usePointerDrag(handleEl, ({ movement: [, my], velocity: vel, direction: [, dy], dragging, first, last }) => {
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
    const velocity = -(vel * dy);

    if (velocity < -0.5) { close(); return; }
    if (sheetHeight.value < minHeight) { close(); return; }

    const resolved = getResolvedDetents();
    if (resolved.length > 0) {
      if (vel > 0.1) {
        let targetIdx: number;
        if (velocity > 0.3) {
          targetIdx = resolved.findIndex(d => d.heightPx > sheetHeight.value + 8);
          if (targetIdx < 0) targetIdx = resolved.length - 1;
        } else if (velocity < -0.2) {
          const revIdx = [...resolved].reverse().findIndex(d => d.heightPx < sheetHeight.value - 8);
          targetIdx = revIdx >= 0 ? resolved.length - 1 - revIdx : 0;
        } else {
          targetIdx = resolved.reduce((closest, _d, i) =>
            Math.abs(resolved[i].heightPx - sheetHeight.value) < Math.abs(resolved[closest].heightPx - sheetHeight.value) ? i : closest
          , 0);
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
      console.log(`[BottomSheet] drag-end height=${sheetHeight.value.toFixed(0)}px  ${((sheetHeight.value / vh) * 100).toFixed(1)}% of vh (vh=${vh}px)  detent=${activeDetentId.value}`);
    }
  }
});

// ── Programmatic detent jump ──
watch(() => props.selectedDetent, (val) => {
  if (val == null || !isOpen.value || isDragging.value || isFullscreen.value) return;
  const resolved = getResolvedDetents();
  if (resolved.length === 0) return;
  const idx = findDetentIndex(resolved, val);
  sheetHeight.value = resolved[idx].heightPx;
  activeDetentId.value = resolved[idx].id;
  emit('detent-change', activeDetentId.value);
});

onUnmounted(() => {
  _popEscape(close);
  document.body.style.overflow = '';
  if (scrollTarget.value && scrollTargetListener) {
    scrollTarget.value.removeEventListener('scroll', scrollTargetListener);
  }
  if (sizeObserver) { sizeObserver.disconnect(); sizeObserver = null; }
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
  --bs-float-top: 0.45rem;
  --bs-float-right: 0.85rem;

  --sheet-inline-gap: 1rem;
  --sheet-desktop-max-width: 920px;
  --sheet-desktop-wide-max-width: 1180px;
  --sheet-desktop-wide-width: 72vw;
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
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  transition: height 0.45s cubic-bezier(0.22, 1, 0.36, 1);
  will-change: height;
  overflow: hidden;
  padding-bottom: max(env(safe-area-inset-bottom, 0px), 0.5rem);
}

.sheet--hidden {
  pointer-events: none;
  visibility: hidden;
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
  padding: var(--bs-handle-pt) 0 0;
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

.sheet-header-actions {
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

.sheet-fullscreen-btn {
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
  font-size: var(--bs-btn-fs-icon);
  cursor: pointer;
  transition: all 0.15s;
  flex-shrink: 0;
}
.sheet-fullscreen-btn::after {
  content: '';
  position: absolute;
  inset: -0.55rem;
}
.sheet-fullscreen-btn:hover {
  color: var(--text-primary);
  background: var(--surface-active);
}

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
  font-size: var(--bs-btn-close-icon);
  cursor: pointer;
  transition: all 0.15s;
  flex-shrink: 0;
}
.sheet-close-btn::after {
  content: '';
  position: absolute;
  inset: -0.55rem;
}
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
/* The sheet body is a neutral flex column — no padding, no scrolling.
   Slot content is responsible for its own padding, overflow, and layout.

   !! SCROLL CHAIN CONTRACT !!
   For anything inside the slot to scroll, EVERY ancestor must propagate
   the height constraint. The required chain is:

     .sheet            → overflow: hidden, fixed px height
     .sheet-body       → flex: 1 1 auto; min-height: 0; overflow: hidden
     [slot root]       → MUST have: display:flex; flex-direction:column;
                                     flex:1 1 auto; min-height:0; overflow:hidden
     [scroll target]   → flex: 1 1 auto; min-height: 0; overflow-y: auto

   Missing `overflow: hidden` on the direct slot child (e.g. .admin-root,
   .stats-root) is the most common mistake — the child grows unbounded and
   overflow-y: auto on its descendants never activates. */
.sheet-body {
  flex: 1 1 auto;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  padding: 0 0 var(--bs-body-pb);
  touch-action: pan-y;
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

/* ─── Scroll fade hint ─── */
.sheet-scroll-hint {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 4.5rem;
  background: linear-gradient(to bottom, transparent 0%, var(--surface-glass-heavy) 60%);
  pointer-events: auto;
  cursor: pointer;
  z-index: 1;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding-bottom: 0.5rem;
}

.sheet-scroll-hint__icon {
  font-size: var(--text-lg-size);
  color: var(--text-secondary);
  animation: scroll-hint-bounce 1.6s ease-in-out infinite;
  opacity: 0.85;
}

@keyframes scroll-hint-bounce {
  0%, 100% { transform: translateY(0); opacity: 0.85; }
  50%       { transform: translateY(4px); opacity: 0.55; }
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
}

/* ─── Large desktop: scale with screen width ─── */
@media (min-width: 1400px) {
  .sheet {
    width: min(var(--sheet-desktop-wide-max-width), var(--sheet-desktop-wide-width));
    margin-left: calc(-0.5 * min(var(--sheet-desktop-wide-max-width), var(--sheet-desktop-wide-width)));
  }
}

/* ─── Fullscreen mode (desktop only) ─── */
.sheet--fullscreen {
  border-radius: 0;
  left: 0 !important;
  right: 0 !important;
  width: 100% !important;
  margin-left: 0 !important;
  margin-right: 0 !important;
  padding-top: env(safe-area-inset-top, 0px);
}
.sheet--fullscreen .sheet-handle-zone {
  display: none;
}

/* ─── Desktop with nav panel: offset centering ─── */
@media (min-width: 1024px) {
  .sheet {
    left: var(--nav-panel-w, 64px);
    right: 0;
    width: min(var(--sheet-desktop-max-width), calc(100vw - var(--nav-panel-w, 64px) - (2 * var(--sheet-inline-gap))));
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
}
</style>
