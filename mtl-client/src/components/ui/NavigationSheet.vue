<template>
  <Teleport to="body">
    <!-- Small drag halo above the sheet for real-device finger tolerance. -->
    <div v-if="!isDesktop" ref="haloEl" class="nav-sheet__drag-halo" aria-hidden="true"></div>

    <!-- ─── Mobile: bottom sheet with tool grid ─── -->
    <div
      v-if="!isDesktop"
      ref="sheetEl"
      class="nav-sheet"
      :class="{
        'nav-sheet--dragging': isDragging,
        'nav-sheet--collapsed': currentSnap === 'collapsed',
      }"
      :style="sheetStyle"
    >
      <!-- Drag handle: also acts as click-to-expand when collapsed -->
      <div ref="handleHitEl" class="nav-sheet__handle-hit-zone" aria-hidden="true"></div>
      <div ref="handleTapEl" class="nav-sheet__handle-tap-zone" aria-hidden="true" @click="onDragTap"></div>
      <div class="nav-sheet__handle-zone" aria-hidden="true">
        <div class="nav-sheet__handle"></div>
      </div>

      <!-- Tool grid -->
      <div
        ref="gridEl"
        class="nav-sheet__grid"
        @pointerdown="onToolGridPointerDown"
        @pointermove="onToolGridPointerMove"
        @pointerup="onToolGridPointerEnd"
        @pointercancel="onToolGridPointerEnd"
      >
        <!-- Row 1: primary tools -->
        <div class="nav-sheet__row">
          <button
            v-for="tool in primaryTools"
            :key="tool.id"
            class="nav-sheet__tool"
            :class="{
              'nav-sheet__tool--active': activeTool === tool.id,
              'nav-sheet__tool--alert': alertSet.has(tool.id),
              'nav-sheet__tool--drifted': driftedSet.has(tool.id),
            }"
            @click="onToolClick($event, tool.id)"
          >
            <i :class="iconFor(tool)"></i>
            <span class="nav-sheet__tool-label">{{ tool.label }}</span>
          </button>
        </div>

        <!-- Row 2: secondary tools -->
        <div class="nav-sheet__row nav-sheet__row--secondary">
          <button
            v-for="tool in secondaryTools"
            :key="tool.id"
            class="nav-sheet__tool"
            :class="{
              'nav-sheet__tool--active': activeTool === tool.id,
              'nav-sheet__tool--alert': alertSet.has(tool.id),
              'nav-sheet__tool--drifted': driftedSet.has(tool.id),
            }"
            @click="onToolClick($event, tool.id)"
          >
            <i :class="iconFor(tool)"></i>
            <span class="nav-sheet__tool-label">{{ tool.label }}</span>
          </button>
        </div>
      </div>
    </div>

    <!-- ─── Desktop: left side panel ─── -->
    <div v-else class="nav-panel">
      <div class="nav-panel__grid">
        <button
          v-for="tool in tools"
          :key="tool.id"
          class="nav-panel__tool"
          :class="{
            'nav-panel__tool--active': activeTool === tool.id,
            'nav-panel__tool--alert': alertSet.has(tool.id),
            'nav-panel__tool--drifted': driftedSet.has(tool.id),
          }"
          :title="tool.label"
          @click="$emit('select', tool.id)"
        >
          <i :class="iconFor(tool)"></i>
          <span class="nav-panel__tool-label">{{ tool.label }}</span>
        </button>
      </div>
      <AppBrandButton class="nav-panel__brand" />
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue';
import { usePointerDrag, type DragState } from '@/composables/usePointerDrag';
import AppBrandButton from '@/components/info/AppBrandButton.vue';

export interface ToolDef {
  id: string;
  icon: string;
  /** Filled icon variant shown when tool has an alert badge */
  alertIcon?: string;
  /** Icon variant shown when tool is active but drifted (e.g. GPS on but not following) */
  driftedIcon?: string;
  label: string;
}

const props = defineProps<{
  tools: ToolDef[];
  activeTool?: string | null;
  primaryIds?: string[];
  /** Tool IDs that should show an alert badge (e.g. filter active) */
  alertToolIds?: string[];
  /** Tool IDs that are active but in a "drifted" state (e.g. GPS on but map not following) */
  driftedToolIds?: string[];
}>();

const emit = defineEmits<{
  (e: 'select', toolId: string): void;
}>();

// ── Alert set for O(1) lookups ──
const alertSet = computed(() => new Set(props.alertToolIds ?? []));
// ── Drifted set for GPS-on-but-not-following state ──
const driftedSet = computed(() => new Set(props.driftedToolIds ?? []));

/** Resolve the correct icon class — use alertIcon/driftedIcon when tool is in a special state */
function iconFor(tool: ToolDef): string {
  if (alertSet.value.has(tool.id) && tool.alertIcon) return tool.alertIcon;
  if (driftedSet.value.has(tool.id) && tool.driftedIcon) return tool.driftedIcon;
  return tool.icon;
}

// ── Responsive breakpoint ──
const DESKTOP_BP = 1024;
const isDesktop = ref(window.innerWidth >= DESKTOP_BP);

function onResize() {
  isDesktop.value = window.innerWidth >= DESKTOP_BP;
  updateCssVar();
}

onMounted(() => window.addEventListener('resize', onResize));
onUnmounted(() => window.removeEventListener('resize', onResize));

// ── Tool rows ──
const primaryTools = computed(() => {
  if (!props.primaryIds) return props.tools.slice(0, 4);
  return props.primaryIds.map((id) => props.tools.find((t) => t.id === id)).filter(Boolean) as ToolDef[];
});

const secondaryTools = computed(() => {
  const primarySet = new Set(primaryTools.value.map((t) => t.id));
  return props.tools.filter((t) => !primarySet.has(t.id));
});

// ── Mobile sheet drag ──
const sheetEl = ref<HTMLElement | null>(null);
const gridEl = ref<HTMLElement | null>(null);
const handleHitEl = ref<HTMLElement | null>(null);
const handleTapEl = ref<HTMLElement | null>(null);
const haloEl = ref<HTMLElement | null>(null);
const isDragging = ref(false);
const sheetHeight = ref(0);
let dragStartHeight = 0;
let suppressNextDragTap = false;
let suppressToolClickUntil = 0;

// Snap points in px (computed after mount)
const HANDLE_HEIGHT = 26; // sheet chrome budget; CSS keeps the visible handle row tighter
const ROW_HEIGHT = 50; // each tool row height
const ROW_GAP = 2; // gap between rows
const BOTTOM_PAD = 4; // bottom padding
const TOOL_GRID_DRAG_THRESHOLD_PX = 10;
const TOOL_GRID_VERTICAL_DOMINANCE = 1.2;
const TOOL_GRID_CLICK_SUPPRESS_MS = 250;
const TOOL_GRID_VELOCITY_WINDOW_MS = 80;

interface ToolGridDragState {
  pointerId: number;
  startX: number;
  startY: number;
  dragging: boolean;
  trail: Array<{ x: number; y: number; t: number }>;
}

let toolGridDragState: ToolGridDragState | null = null;

const collapsedHeight = HANDLE_HEIGHT + 20; // handle + breathing room for safe-area
const expandedHeight = HANDLE_HEIGHT + ROW_HEIGHT + ROW_GAP + ROW_HEIGHT + BOTTOM_PAD; // handle + 2 rows

type SnapName = 'collapsed' | 'expanded';
const currentSnap = ref<SnapName>('expanded');

// Set initial height on mount
onMounted(() => {
  sheetHeight.value = expandedHeight;
  updateCssVar();
});

// Keep CSS variable in sync so other elements can position above the sheet.
// Note: --nav-panel-w is set purely via CSS media queries in main.css.
function updateCssVar() {
  document.documentElement.style.setProperty('--nav-sheet-h', isDesktop.value ? '0px' : `${sheetHeight.value}px`);
}

watch(sheetHeight, updateCssVar);
watch(isDesktop, updateCssVar);

onUnmounted(() => {
  document.documentElement.style.removeProperty('--nav-sheet-h');
});

// When activeTool changes and sheet is collapsed, pop open
watch(
  () => props.activeTool,
  (newVal) => {
    if (newVal && currentSnap.value === 'collapsed') {
      snapTo('expanded');
    }
  }
);

const sheetStyle = computed(() => {
  if (isDragging.value) {
    return {
      height: `${sheetHeight.value}px`,
      transition: 'none',
      paddingBottom: `max(env(safe-area-inset-bottom, 0px), 0.25rem)`,
    };
  }
  return {
    height: `${sheetHeight.value}px`,
    paddingBottom: `max(env(safe-area-inset-bottom, 0px), 0.25rem)`,
  };
});

function snapTo(snap: SnapName) {
  currentSnap.value = snap;
  switch (snap) {
    case 'collapsed':
      sheetHeight.value = collapsedHeight;
      break;
    case 'expanded':
      sheetHeight.value = expandedHeight;
      break;
  }
}

function suppressDragTapOnce() {
  suppressNextDragTap = true;
  window.setTimeout(() => {
    suppressNextDragTap = false;
  }, 0);
}

function onDragTap() {
  if (suppressNextDragTap) {
    suppressNextDragTap = false;
    return;
  }
  snapTo(currentSnap.value === 'collapsed' ? 'expanded' : 'collapsed');
}

function beginSheetDrag() {
  dragStartHeight = sheetHeight.value;
  isDragging.value = true;
}

function updateSheetDrag(my: number) {
  // During drag: my > 0 = finger moved down = sheet shrinks
  const delta = -my; // positive = dragging up = increasing height
  sheetHeight.value = Math.max(collapsedHeight * 0.5, Math.min(dragStartHeight + delta, expandedHeight));
}

function finishSheetDrag(vel: number, dy: number) {
  isDragging.value = false;
  // velocity in px/ms; dy: -1=up, 1=down
  // positive upVelocity = expand, negative = collapse
  const upVelocity = -(vel * dy);

  if (upVelocity < -0.3) {
    snapTo('collapsed');
  } else if (upVelocity > 0.3) {
    snapTo('expanded');
  } else {
    const h = sheetHeight.value;
    const snapPoints: Array<{ name: SnapName; h: number }> = [
      { name: 'collapsed', h: collapsedHeight },
      { name: 'expanded', h: expandedHeight },
    ];
    let best = snapPoints[0];
    for (const sp of snapPoints) {
      if (Math.abs(h - sp.h) < Math.abs(h - best.h)) best = sp;
    }
    snapTo(best.name);
  }
}

function onSheetDrag({ movement: [, my], velocity: vel, direction: [, dy], first, last }: DragState) {
  if (first) beginSheetDrag();
  if (!last) updateSheetDrag(my);

  if (last) {
    suppressDragTapOnce();
    finishSheetDrag(vel, dy);
  }
}

function suppressToolClickOnce() {
  suppressToolClickUntil = Date.now() + TOOL_GRID_CLICK_SUPPRESS_MS;
  window.setTimeout(() => {
    if (Date.now() >= suppressToolClickUntil) suppressToolClickUntil = 0;
  }, TOOL_GRID_CLICK_SUPPRESS_MS);
}

function onToolClick(event: MouseEvent, toolId: string) {
  if (Date.now() < suppressToolClickUntil) {
    suppressToolClickUntil = 0;
    event.preventDefault();
    event.stopPropagation();
    return;
  }
  emit('select', toolId);
}

function onToolGridPointerDown(event: PointerEvent) {
  if (event.button !== 0 || isDesktop.value) return;
  if (!(event.target instanceof Element) || !event.target.closest('.nav-sheet__tool')) return;

  toolGridDragState = {
    pointerId: event.pointerId,
    startX: event.clientX,
    startY: event.clientY,
    dragging: false,
    trail: [{ x: event.clientX, y: event.clientY, t: Date.now() }],
  };
  gridEl.value?.setPointerCapture(event.pointerId);
}

function onToolGridPointerMove(event: PointerEvent) {
  const state = toolGridDragState;
  if (!state || state.pointerId !== event.pointerId) return;

  const mx = event.clientX - state.startX;
  const my = event.clientY - state.startY;
  const absX = Math.abs(mx);
  const absY = Math.abs(my);
  const now = Date.now();

  state.trail.push({ x: event.clientX, y: event.clientY, t: now });
  while (state.trail.length > 2 && now - state.trail[0].t > TOOL_GRID_VELOCITY_WINDOW_MS) state.trail.shift();

  if (!state.dragging) {
    if (absY < TOOL_GRID_DRAG_THRESHOLD_PX) return;
    if (absY < absX * TOOL_GRID_VERTICAL_DOMINANCE) return;

    state.dragging = true;
    suppressToolClickOnce();
    beginSheetDrag();
  }

  event.preventDefault();
  updateSheetDrag(my);
}

function onToolGridPointerEnd(event: PointerEvent) {
  const state = toolGridDragState;
  if (!state || state.pointerId !== event.pointerId) return;
  toolGridDragState = null;

  if (!state.dragging) return;

  event.preventDefault();
  suppressToolClickOnce();

  const now = Date.now();
  state.trail.push({ x: event.clientX, y: event.clientY, t: now });
  const oldest = state.trail[0];
  const dt = now - oldest.t;
  const recentDx = event.clientX - oldest.x;
  const recentDy = event.clientY - oldest.y;
  const velocity = dt > 0 ? Math.sqrt(recentDx * recentDx + recentDy * recentDy) / dt : 0;
  finishSheetDrag(velocity, Math.sign(recentDy));
}

// ── Drag via usePointerDrag composable ──
usePointerDrag(handleHitEl, onSheetDrag);
usePointerDrag(handleTapEl, onSheetDrag);
usePointerDrag(haloEl, onSheetDrag);

// ── Expose for parent ──
defineExpose({
  snapTo,
  isDesktop,
  currentSnap,
  expandedHeight,
  collapsedHeight,
});
</script>

<style scoped>
/* ═══════════════════════════════════════════════
   MOBILE: Bottom Sheet with Tool Grid
   ═══════════════════════════════════════════════ */

.nav-sheet {
  --nav-sheet-drag-halo-h: 10px;
  --nav-sheet-handle-hit-h: 34px;
  position: fixed;
  z-index: var(--z-nav-sheet);
  left: 0;
  right: 0;
  bottom: 0;
  overflow: hidden;
  background: var(--surface-glass);
  backdrop-filter: var(--blur-heavy);
  -webkit-backdrop-filter: var(--blur-heavy);
  border-top-left-radius: 1rem;
  border-top-right-radius: 1rem;
  border-top: 1px solid var(--border-medium);
  box-shadow: var(--shadow-md);
  transition: height 0.45s cubic-bezier(0.32, 0.72, 0, 1);
  will-change: height;
  display: flex;
  flex-direction: column;
}

.nav-sheet--dragging {
  transition: none !important;
}

.nav-sheet__handle-hit-zone {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  z-index: 3;
  height: var(--nav-sheet-handle-hit-h);
  cursor: grab;
  touch-action: none;
  -webkit-tap-highlight-color: transparent;
}

.nav-sheet__handle-tap-zone {
  position: absolute;
  top: 0;
  left: 50%;
  z-index: 4;
  width: 88px;
  height: 24px;
  transform: translateX(-50%);
  cursor: grab;
  touch-action: none;
  -webkit-tap-highlight-color: transparent;
}

/* ─── Handle ─── */
.nav-sheet__handle-zone {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 6px 0 4px;
  pointer-events: none;
}

.nav-sheet__handle {
  width: 2.5rem;
  height: 0.2rem;
  border-radius: 2px;
  background: var(--border-hover);
  transition: background 0.2s;
}
.nav-sheet__handle-zone:hover .nav-sheet__handle,
.nav-sheet--dragging .nav-sheet__handle {
  background: var(--border-hover);
}

/* ─── Grid ─── */
.nav-sheet__grid {
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 0 0.5rem;
  overflow: hidden;
  touch-action: none;
}

.nav-sheet__row {
  display: flex;
  justify-content: space-around;
  gap: 2px;
  min-height: 50px;
  flex-shrink: 0;
}

/* ─── Tool button (mobile) ─── */
.nav-sheet__tool {
  flex: 1 1 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  padding: 6px 4px;
  border: none;
  border-radius: 0.625rem;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  transition: all 0.15s;
  min-width: 0;
}
.nav-sheet__tool i {
  font-size: var(--text-lg-size);
}
.nav-sheet__tool-label {
  font-size: var(--text-xs-size);
  font-weight: 500;
  letter-spacing: 0.01em;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
}

.nav-sheet__tool:hover {
  background: var(--surface-hover);
  color: var(--text-secondary);
}
.nav-sheet__tool:active {
  transform: scale(0.93);
}

.nav-sheet__tool--active {
  background: var(--accent-subtle) !important;
  color: var(--text-primary) !important;
  box-shadow: 0 0 10px var(--accent-glow);
}

/* Alert dot: subtle status indicator — filter active on map */
.nav-sheet__tool--alert {
  position: relative;
}
.nav-sheet__tool--alert i {
  position: relative;
}
.nav-sheet__tool--alert i::after {
  content: '';
  position: absolute;
  top: -3px;
  right: -5px;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--alert-dot);
  box-shadow:
    0 0 6px var(--alert-dot-glow),
    0 0 2px var(--alert-dot);
  animation: alert-pulse 2s ease-in-out infinite;
}

@keyframes alert-pulse {
  0%,
  100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.5;
    transform: scale(0.75);
  }
}

/* Drifted dot: GPS on but map not following — amber, no animation */
.nav-sheet__tool--drifted {
  position: relative;
}
.nav-sheet__tool--drifted i {
  position: relative;
}
.nav-sheet__tool--drifted i::after {
  content: '';
  position: absolute;
  top: -3px;
  right: -5px;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--warning);
  box-shadow:
    0 0 6px var(--alert-dot-glow),
    0 0 2px var(--warning);
}

/* Collapsed: hide everything but handle */
.nav-sheet--collapsed .nav-sheet__grid {
  opacity: 0;
  pointer-events: none;
}

/* ─── Small drag halo above the sheet ─── */
.nav-sheet__drag-halo {
  position: fixed;
  z-index: var(--z-nav-sheet);
  left: 0;
  right: 0;
  bottom: calc(var(--nav-sheet-h, 0px) + max(env(safe-area-inset-bottom, 0px), 0.25rem));
  height: var(--nav-sheet-drag-halo-h, 10px);
  cursor: grab;
  touch-action: none;
  -webkit-tap-highlight-color: transparent;
}

/* ═══════════════════════════════════════════════
   DESKTOP: Left Side Panel
   ═══════════════════════════════════════════════ */

.nav-panel {
  position: fixed;
  z-index: var(--z-nav-sheet);
  top: 0;
  left: 0;
  bottom: 0;
  width: var(--nav-panel-w);
  box-sizing: border-box;
  background: var(--surface-glass-heavy);
  backdrop-filter: var(--blur-heavy);
  -webkit-backdrop-filter: var(--blur-heavy);
  border-right: 1px solid var(--border-default);
  display: flex;
  flex-direction: column;
  padding: calc(0.75rem + var(--safe-top, 0px)) 0 calc(0.75rem + var(--safe-bottom, 0px));
  transition: width 0.3s ease;
}

.nav-panel__grid {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 0.25rem 0;
}

.nav-panel__brand {
  margin-top: auto;
}

/* ─── Tool button (desktop) ─── */
.nav-panel__tool {
  width: 58px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  padding: 10px 4px;
  border: none;
  border-radius: 0.625rem;
  background: transparent;
  color: var(--text-faint);
  cursor: pointer;
  transition: all 0.15s;
}
.nav-panel__tool i {
  font-size: var(--text-xl-size);
}
.nav-panel__tool-label {
  /* SPECIAL CASE: same 11px intent as nav-sheet__tool-label — between scale steps */
  font-size: 0.6875rem; /* 11px */
  font-weight: 500;
  letter-spacing: 0.01em;
  white-space: nowrap;
}

/* Bigger icons on large screens (panel width comes from --nav-panel-w in main.css) */
@media (min-width: 1280px) {
  .nav-panel__tool {
    width: 64px;
    padding: 11px 4px;
    gap: 4px;
  }
  .nav-panel__tool i {
    font-size: var(--text-2xl-size);
  }
  .nav-panel__tool-label {
    font-size: var(--text-xs-size);
  }
}
.nav-panel__tool:hover {
  background: var(--surface-hover);
  color: var(--text-secondary);
}

.nav-panel__tool--active {
  background: var(--accent-subtle) !important;
  color: var(--text-primary) !important;
  box-shadow: 0 0 10px var(--accent-glow);
}

/* Alert dot: subtle status indicator — filter active on map */
.nav-panel__tool--alert {
  position: relative;
}
.nav-panel__tool--alert i {
  position: relative;
}
.nav-panel__tool--alert i::after {
  content: '';
  position: absolute;
  top: -3px;
  right: -5px;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--alert-dot);
  box-shadow:
    0 0 6px var(--alert-dot-glow),
    0 0 2px var(--alert-dot);
  animation: alert-pulse 2s ease-in-out infinite;
}

/* Drifted dot: GPS on but map not following — amber, no animation */
.nav-panel__tool--drifted {
  position: relative;
}
.nav-panel__tool--drifted i {
  position: relative;
}
.nav-panel__tool--drifted i::after {
  content: '';
  position: absolute;
  top: -3px;
  right: -5px;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--warning);
  box-shadow:
    0 0 6px var(--alert-dot-glow),
    0 0 2px var(--warning);
}

/* ═══════════════════════════════════════════════
   HIDE BASED ON VIEWPORT (safety net)
   ═══════════════════════════════════════════════ */
@media (min-width: 1024px) {
  .nav-sheet {
    display: none;
  }
}
@media (max-width: 1023px) {
  .nav-panel {
    display: none;
  }
}
</style>
