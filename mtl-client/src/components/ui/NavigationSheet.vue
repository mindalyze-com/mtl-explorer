<template>
  <Teleport to="body">
    <!-- ─── Mobile: bottom sheet with tool grid ─── -->
    <!-- The toolbar is fixed: cancel native touch movement as well as owning pointer events.
         Pointer-event cancellation alone does not prevent browser viewport movement. -->
    <div
      v-if="!isDesktop"
      ref="sheetEl"
      class="nav-sheet"
      :class="{
        'nav-sheet--dragging': isDragging,
        'nav-sheet--collapsed': currentSnap === 'collapsed',
        'nav-sheet--overflow': currentSnap === 'overflow',
      }"
      :style="sheetStyle"
      @touchstart.stop
      @touchmove.stop.prevent
      @touchend.stop
      @touchcancel.stop
    >
      <div class="nav-sheet__handle-zone" aria-hidden="true">
        <!-- Collapse affordance disabled: <div class="nav-sheet__handle"></div> -->
      </div>

      <!-- Mobile navigation content -->
      <div
        ref="gridEl"
        class="nav-sheet__grid"
        @pointerdown="onNavTouchPointerDown($event, 'grid')"
        @pointermove="onNavTouchPointerMove"
        @pointerup="onNavTouchPointerEnd"
        @pointercancel="onNavTouchPointerEnd"
      >
        <div class="nav-sheet__row nav-sheet__row--primary">
          <button
            v-for="tool in primaryTools"
            :key="tool.id"
            class="nav-sheet__tool"
            :class="toolStateClasses(tool.id)"
            :data-tool-id="tool.id"
            type="button"
            @click="onToolClick($event, tool.id)"
          >
            <AppIcon :icon="tool.icon" :status-dot="toolStatusDot(tool)" />
            <span class="nav-sheet__tool-label">{{ tool.label }}</span>
          </button>

          <button
            class="nav-sheet__tool nav-sheet__tool--more"
            :class="{ 'nav-sheet__tool--active': moreIsActive }"
            :data-tool-id="MORE_TOOL_ID"
            type="button"
            aria-controls="nav-sheet-more-tools"
            :aria-label="moreAccessibleLabel"
            :aria-expanded="currentSnap === 'overflow'"
            @click="onToolClick($event, MORE_TOOL_ID)"
          >
            <AppIcon
              :icon="currentSnap === 'overflow' ? 'bi bi-grid-fill' : 'bi bi-grid'"
              :status-dot="utilityBadge ? PENDING_UPDATE_DOT : undefined"
            />
            <span class="nav-sheet__tool-label">
              More<span v-if="moreBadge"> ({{ moreBadge }})</span>
            </span>
          </button>
        </div>
      </div>
    </div>

    <!-- ─── Desktop: left side panel ─── -->
    <div v-else class="nav-panel">
      <div class="nav-panel__grid">
        <button
          v-for="tool in desktopTools"
          :key="tool.id"
          class="nav-panel__tool"
          :class="desktopToolStateClasses(tool.id)"
          :title="tool.label"
          @click="$emit('select', tool.id)"
        >
          <AppIcon :icon="tool.icon" :status-dot="toolStatusDot(tool)" />
          <span class="nav-panel__tool-label">{{ tool.label }}</span>
        </button>
      </div>
      <div class="nav-panel__footer">
        <a
          class="nav-panel__tool nav-panel__guide"
          :href="APP_GUIDE_URL"
          target="_blank"
          rel="opener"
          aria-label="Open user guide"
        >
          <i class="bi bi-book" aria-hidden="true" />
          <span class="nav-panel__tool-label">Guide</span>
        </a>
        <button
          v-if="utilityTool"
          class="nav-panel__tool nav-panel__utility"
          :class="desktopToolStateClasses(utilityTool.id)"
          :title="utilityStatusLabel ? `${utilityTool.label}, ${utilityStatusLabel}` : utilityTool.label"
          :aria-label="utilityStatusLabel ? `${utilityTool.label}, ${utilityStatusLabel}` : utilityTool.label"
          @click="$emit('select', utilityTool.id)"
        >
          <AppIcon
            :icon="utilityTool.icon"
            :status-dot="utilityBadge ? PENDING_UPDATE_DOT : toolStatusDot(utilityTool)"
          />
          <span class="nav-panel__tool-label">{{ utilityTool.label }}</span>
        </button>
        <AppBrandButton class="nav-panel__brand" />
      </div>
    </div>
  </Teleport>

  <BottomSheet
    v-if="!isDesktop"
    v-model="moreVisible"
    :detents="MORE_DETENTS"
    :fit-content-initial="true"
    :no-backdrop="true"
    sheet-class="sheet--solid-over-map nav-more-sheet"
    @closed="onMoreClosed"
  >
    <template #title>
      <div class="nav-sheet__more-title">
        <AppIcon icon="bi bi-grid" />
        <h2 class="panel-title">More</h2>
      </div>
    </template>
    <section
      v-if="moreVisible"
      id="nav-sheet-more-tools"
      class="nav-sheet__overflow-panel"
      :style="morePanelStyle"
      aria-label="More tools"
      @touchstart.stop
      @touchmove.stop
      @touchend.stop
      @touchcancel.stop
    >
      <p class="nav-sheet__more-intro">Open another area of MTL Explorer.</p>

      <div class="nav-sheet__overflow-list">
        <a class="nav-sheet__overflow-tool" :href="APP_GUIDE_URL" target="_blank" rel="opener">
          <span class="nav-sheet__overflow-icon" aria-hidden="true"><i class="bi bi-book" /></span>
          <span class="nav-sheet__overflow-copy">
            <span class="nav-sheet__overflow-label">User guide</span>
            <span class="nav-sheet__overflow-description">Help for this installation.</span>
          </span>
          <AppIcon aria-hidden="true" icon="bi bi-box-arrow-up-right nav-sheet__overflow-chevron" />
        </a>
        <button
          v-for="tool in overflowTools"
          :key="tool.id"
          class="nav-sheet__overflow-tool"
          :class="toolStateClasses(tool.id)"
          :data-tool-id="tool.id"
          type="button"
          :aria-label="`Open ${tool.moreLabel ?? tool.label}`"
          @click="onToolClick($event, tool.id)"
        >
          <span class="nav-sheet__overflow-icon" aria-hidden="true">
            <AppIcon :icon="tool.icon" :status-dot="toolStatusDot(tool)" />
          </span>
          <span class="nav-sheet__overflow-copy">
            <span class="nav-sheet__overflow-label">{{ tool.moreLabel ?? tool.label }}</span>
            <span v-if="tool.description" class="nav-sheet__overflow-description">{{ tool.description }}</span>
          </span>
          <AppIcon aria-hidden="true" icon="bi bi-chevron-right nav-sheet__overflow-chevron" />
        </button>

        <button
          v-if="utilityTool"
          class="nav-sheet__utility"
          :class="toolStateClasses(utilityTool.id)"
          :data-tool-id="utilityTool.id"
          type="button"
          :aria-label="`Open ${utilityTool.moreLabel ?? utilityTool.label}${utilityStatusLabel ? `, ${utilityStatusLabel}` : ''}`"
          @click="onToolClick($event, utilityTool.id)"
        >
          <span class="nav-sheet__utility-icon" aria-hidden="true">
            <AppIcon :icon="utilityTool.icon" :status-dot="toolStatusDot(utilityTool)" />
          </span>
          <span class="nav-sheet__overflow-copy">
            <span class="nav-sheet__utility-label">
              {{ utilityTool.moreLabel ?? utilityTool.label }}
              <span v-if="utilityBadge" class="nav-update-badge nav-update-badge--inline">
                {{ utilityBadge }}
              </span>
            </span>
            <span v-if="utilityTool.description" class="nav-sheet__overflow-description">
              {{ utilityTool.description }}
            </span>
          </span>
          <AppIcon aria-hidden="true" icon="bi bi-chevron-right nav-sheet__overflow-chevron" />
        </button>
      </div>
    </section>
  </BottomSheet>
</template>

<script setup lang="ts">
import AppIcon from '@/components/ui/AppIcon.vue';
import BottomSheet, { type Detent } from '@/components/ui/BottomSheet.vue';
import type { AppIconStatusDot } from '@/components/ui/appIconTypes';
import { ref, computed, nextTick, onMounted, onUnmounted, watch } from 'vue';
import AppBrandButton from '@/components/info/AppBrandButton.vue';
import { APP_GUIDE_URL } from '@/utils/documentation';

export interface ToolDef {
  id: string;
  icon: string;
  /** Filled icon variant shown when tool has an alert badge */
  alertIcon?: string;
  /** Icon variant shown when tool is active but drifted (e.g. GPS on but not following) */
  driftedIcon?: string;
  label: string;
  /** Expanded label shown in the mobile More list. */
  moreLabel?: string;
  /** Supporting copy shown under the label in the mobile More list. */
  description?: string;
}

const props = defineProps<{
  tools: ToolDef[];
  activeTool?: string | null;
  primaryIds?: string[];
  /** Tool rendered as a separate Settings-style utility action instead of a map tool. */
  utilityId?: string;
  utilityBadge?: string;
  /** Persistent count for an area in the collapsed More list. */
  moreBadge?: string;
  moreBadgeLabel?: string;
  /** Tool IDs that should show an alert badge (e.g. filter active) */
  alertToolIds?: string[];
  /** Tool IDs that are active but in a "drifted" state (e.g. GPS on but map not following) */
  driftedToolIds?: string[];
}>();

const emit = defineEmits<{
  (e: 'select', toolId: string): void;
}>();

const utilityStatusLabel = computed(() =>
  props.utilityBadge ? (props.utilityBadge === 'Reload' ? 'interface reload available' : 'update available') : undefined
);
const moreAccessibleLabel = computed(() => {
  const notices = [utilityStatusLabel.value, props.moreBadgeLabel].filter(Boolean);
  return notices.length ? `More, ${notices.join(', ')}` : undefined;
});

// ── Alert set for O(1) lookups ──
const alertSet = computed(() => new Set(props.alertToolIds ?? []));
// ── Drifted set for GPS-on-but-not-following state ──
const driftedSet = computed(() => new Set(props.driftedToolIds ?? []));
const PENDING_UPDATE_DOT: AppIconStatusDot = { tone: 'notice', pulse: true };

function toolStateClasses(toolId: string) {
  return {
    'nav-sheet__tool--active': props.activeTool === toolId,
  };
}

function desktopToolStateClasses(toolId: string) {
  return {
    'nav-panel__tool--active': props.activeTool === toolId,
  };
}

function toolStatusDot(tool: ToolDef): AppIconStatusDot | undefined {
  if (alertSet.value.has(tool.id)) return { tone: 'notice', pulse: true, icon: tool.alertIcon };
  if (driftedSet.value.has(tool.id)) return { tone: 'warning', icon: tool.driftedIcon };
  return undefined;
}

// ── Responsive breakpoint ──
const DESKTOP_BP = 1024;
const isDesktop = ref(window.innerWidth >= DESKTOP_BP);

function onResize() {
  isDesktop.value = window.innerWidth >= DESKTOP_BP;
  updateCssVar();
  if (isDesktop.value && currentSnap.value === 'overflow') snapTo('primary');
}

onMounted(() => window.addEventListener('resize', onResize));
onUnmounted(() => window.removeEventListener('resize', onResize));

// ── Responsive tool hierarchy ──
const MOBILE_PRIMARY_TOOL_COUNT = 4;
const MORE_TOOL_ID = '__more__';

const utilityTool = computed(() => {
  if (!props.utilityId) return null;
  return props.tools.find((tool) => tool.id === props.utilityId) ?? null;
});

const navigationTools = computed(() => props.tools.filter((tool) => tool.id !== utilityTool.value?.id));

const primaryTools = computed(() => {
  const requestedTools = (props.primaryIds ?? [])
    .map((id) => navigationTools.value.find((tool) => tool.id === id))
    .filter(Boolean) as ToolDef[];
  const selectedIds = new Set(requestedTools.map((tool) => tool.id));
  const fallbackTools = navigationTools.value.filter((tool) => !selectedIds.has(tool.id));
  return [...requestedTools, ...fallbackTools].slice(0, MOBILE_PRIMARY_TOOL_COUNT);
});

const overflowTools = computed(() => {
  const primarySet = new Set(primaryTools.value.map((t) => t.id));
  return navigationTools.value.filter((tool) => !primarySet.has(tool.id));
});

const desktopTools = computed(() => navigationTools.value);
const overflowToolIds = computed(() => new Set(overflowTools.value.map((tool) => tool.id)));

// ── Mobile sheet drag ──
const sheetEl = ref<HTMLElement | null>(null);
const gridEl = ref<HTMLElement | null>(null);
const isDragging = ref(false);
const sheetHeight = ref(0);
let dragStartHeight = 0;
let dragDeltaY = 0;

// Mobile sheet dimensions. CSS consumes the same values through sheetStyle.
const HANDLE_HEIGHT = 12;
const COLLAPSED_BODY_HEIGHT = 20;
const PRIMARY_ROW_HEIGHT = 50;
const MORE_TOOL_HEIGHT = 56;
const MORE_DETENTS: Detent[] = ['small', 'large'];
const MORE_OPEN_DRAG_DISTANCE_PX = 48;
const BOTTOM_PAD = 12;
const SHEET_BORDER_WIDTH = 1;
const TOOL_GRID_VELOCITY_WINDOW_MS = 80;
const HANDLE_TOUCH_DRAG_THRESHOLD_PX = 6;
const TOUCH_TAP_TOLERANCE_PX = 10;
const NATIVE_CLICK_SUPPRESS_MS = 450;
const NATIVE_CLICK_SUPPRESS_RADIUS_PX = 32;

type NavTouchGestureZone = 'grid' | 'handle' | 'halo';

interface NavTouchGestureState {
  pointerId: number;
  zone: NavTouchGestureZone;
  startX: number;
  startY: number;
  startHeight: number;
  dragging: boolean;
  exceededTapTolerance: boolean;
  toolId: string | null;
  toolElement: HTMLElement | null;
  captureEl: HTMLElement | null;
  trail: Array<{ x: number; y: number; t: number }>;
}

interface NativeClickSuppression {
  until: number | null;
  allTargets: boolean;
  x: number;
  y: number;
}

let navTouchGestureState: NavTouchGestureState | null = null;
let nativeClickSuppression: NativeClickSuppression | null = null;

const collapsedHeight = HANDLE_HEIGHT + COLLAPSED_BODY_HEIGHT + BOTTOM_PAD + SHEET_BORDER_WIDTH;
const primaryHeight = HANDLE_HEIGHT + PRIMARY_ROW_HEIGHT + BOTTOM_PAD + SHEET_BORDER_WIDTH;
type SnapName = 'collapsed' | 'primary' | 'overflow';
const currentSnap = ref<SnapName>('primary');
const moreVisible = computed({
  get: () => currentSnap.value === 'overflow',
  set: (open: boolean) => snapTo(open ? 'overflow' : 'primary'),
});
const morePanelStyle = { '--nav-overflow-tool-h': `${MORE_TOOL_HEIGHT}px` };
const moreIsActive = computed(
  () =>
    currentSnap.value === 'overflow' ||
    Boolean(
      props.activeTool && (overflowToolIds.value.has(props.activeTool) || props.activeTool === utilityTool.value?.id)
    )
);

// Set initial height on mount
onMounted(() => {
  sheetHeight.value = primaryHeight;
  updateCssVar();
  document.addEventListener('click', onDocumentClickCapture, true);
  document.addEventListener('pointerdown', clearNativeClickSuppression, true);
  document.addEventListener('keydown', clearNativeClickSuppression, true);
});

// Keep CSS variable in sync so other elements can position above the sheet.
// Note: --nav-panel-w is set purely via CSS media queries in main.css.
function updateCssVar() {
  document.documentElement.style.setProperty(
    '--nav-sheet-h',
    isDesktop.value
      ? '0px'
      : `min(${sheetHeight.value}px, calc(100svh - var(--safe-top, 0px) - var(--nav-sheet-top-gap)))`
  );
}

watch(sheetHeight, updateCssVar);
watch(isDesktop, updateCssVar);

onUnmounted(() => {
  document.removeEventListener('click', onDocumentClickCapture, true);
  document.removeEventListener('pointerdown', clearNativeClickSuppression, true);
  document.removeEventListener('keydown', clearNativeClickSuppression, true);
  document.documentElement.style.removeProperty('--nav-sheet-h');
});

// Keep the primary destinations available when a tool opens from the handle-only state.
watch(
  () => props.activeTool,
  (newVal) => {
    if (newVal && currentSnap.value === 'collapsed') {
      snapTo('primary');
    }
  }
);

const sheetStyle = computed(() => {
  return {
    '--nav-body-h': `${sheetHeight.value}px`,
    '--nav-handle-h': `${HANDLE_HEIGHT}px`,
    '--nav-primary-row-h': `${PRIMARY_ROW_HEIGHT}px`,
    '--nav-bottom-pad': `${BOTTOM_PAD}px`,
    '--nav-border-w': `${SHEET_BORDER_WIDTH}px`,
    ...(isDragging.value ? { transition: 'none' } : {}),
  };
});

function snapTo(snap: SnapName) {
  // Toolbar hiding is disabled, including calls from older consumers.
  if (snap === 'collapsed') return;
  currentSnap.value = snap;
  switch (snap) {
    // Toolbar collapse disabled; keep its original sizing code.
    // case 'collapsed':
    //   sheetHeight.value = collapsedHeight;
    //   break;
    case 'primary':
      sheetHeight.value = primaryHeight;
      break;
    case 'overflow':
      sheetHeight.value = primaryHeight;
      break;
  }
}

function beginSheetDrag(startHeight = sheetHeight.value) {
  dragStartHeight = startHeight;
  dragDeltaY = 0;
  isDragging.value = true;
}

function updateSheetDrag(my: number) {
  // During drag: my > 0 = finger moved down = sheet shrinks
  dragDeltaY = my;
  // Toolbar resizing/hiding disabled; retain the original drag sizing code.
  // const delta = -my; // positive = dragging up = increasing height
  // sheetHeight.value = Math.max(collapsedHeight * 0.5, Math.min(dragStartHeight + delta, primaryHeight));
}

function finishSheetDrag(vel: number, dy: number) {
  isDragging.value = false;
  // velocity in px/ms; dy: -1=up, 1=down
  // positive upVelocity = expand, negative = collapse
  const upVelocity = -(vel * dy);

  if (currentSnap.value === 'overflow' && dragDeltaY > 0) {
    snapTo('primary');
  } else if (dragStartHeight >= primaryHeight && dragDeltaY <= -MORE_OPEN_DRAG_DISTANCE_PX) {
    snapTo('overflow');
  } else if (upVelocity < -0.3) {
    // Toolbar collapse disabled: snapTo('collapsed');
    snapTo('primary');
  } else if (upVelocity > 0.3) {
    snapTo(dragStartHeight < primaryHeight ? 'primary' : 'overflow');
  } else {
    const h = sheetHeight.value;
    const snapPoints: Array<{ name: SnapName; h: number }> = [
      // Toolbar collapse snap disabled: { name: 'collapsed', h: collapsedHeight },
      { name: 'primary', h: primaryHeight },
    ];
    let best = snapPoints[0];
    for (const sp of snapPoints) {
      if (Math.abs(h - sp.h) < Math.abs(h - best.h)) best = sp;
    }
    snapTo(best.name);
  }
}

function onToolClick(event: MouseEvent, toolId: string) {
  if (consumeSuppressedNativeClick(event)) return;
  activateMobileItem(toolId);
}

function onMoreClosed() {
  void nextTick(() => {
    if (currentSnap.value !== 'primary') return;
    gridEl.value?.querySelector<HTMLButtonElement>('.nav-sheet__tool--more')?.focus({ preventScroll: true });
  });
}

function activateMobileItem(toolId: string) {
  if (toolId === MORE_TOOL_ID) {
    snapTo(currentSnap.value === 'overflow' ? 'primary' : 'overflow');
    return;
  }
  if (currentSnap.value === 'overflow') snapTo('primary');
  emit('select', toolId);
}

function onNavTouchPointerDown(event: PointerEvent, zone: NavTouchGestureZone) {
  if (event.button !== 0 || isDesktop.value) return;
  // Mouse and pen can drag handles; grid buttons retain their native click behavior.
  if (event.pointerType !== 'touch' && zone === 'grid') return;
  // More owns its content and margins; only the primary row can start a grid drag.
  if (
    zone === 'grid' &&
    event.target instanceof Element &&
    (event.target.closest('.nav-sheet__overflow-panel') ||
      (currentSnap.value === 'overflow' && !event.target.closest('.nav-sheet__row--primary')))
  ) {
    return;
  }

  stopOwnedTouchEvent(event);
  const captureEl = event.currentTarget instanceof HTMLElement ? event.currentTarget : null;
  const toolElement = zone === 'grid' ? toolElementFromTouchTarget(event.target) : null;
  navTouchGestureState = {
    pointerId: event.pointerId,
    zone,
    startX: event.clientX,
    startY: event.clientY,
    startHeight: sheetHeight.value,
    dragging: false,
    exceededTapTolerance: false,
    toolId: toolElement?.dataset.toolId ?? null,
    toolElement,
    captureEl,
    trail: [{ x: event.clientX, y: event.clientY, t: Date.now() }],
  };
  capturePointer(captureEl, event.pointerId);
}

function onNavTouchPointerMove(event: PointerEvent) {
  const state = navTouchGestureState;
  if (!state || state.pointerId !== event.pointerId) return;
  stopOwnedTouchEvent(event);

  const mx = event.clientX - state.startX;
  const my = event.clientY - state.startY;
  const absY = Math.abs(my);
  const now = Date.now();
  state.exceededTapTolerance ||= Math.hypot(mx, my) > TOUCH_TAP_TOLERANCE_PX;

  // Toolbar buttons support taps only. Moving away cancels the tap rather than
  // opening or closing More; BottomSheet owns dragging of the More panel.
  if (state.zone === 'grid') return;

  state.trail.push({ x: event.clientX, y: event.clientY, t: now });
  while (state.trail.length > 2 && now - state.trail[0].t > TOOL_GRID_VELOCITY_WINDOW_MS) state.trail.shift();

  if (!state.dragging) {
    if (absY < HANDLE_TOUCH_DRAG_THRESHOLD_PX) {
      return;
    }

    state.dragging = true;
    beginSheetDrag(state.startHeight);
  }

  updateSheetDrag(my);
}

function onNavTouchPointerEnd(event: PointerEvent) {
  const state = navTouchGestureState;
  if (!state || state.pointerId !== event.pointerId) return;
  navTouchGestureState = null;
  stopOwnedTouchEvent(event);
  releasePointer(state.captureEl, event.pointerId);
  const isTap = event.type !== 'pointercancel' && !state.exceededTapTolerance && isTapGesture(state, event);
  suppressCompatibilityClicks(event, state.dragging || !isTap);

  if (!state.dragging) {
    if (event.type !== 'pointercancel' && isTap) {
      if (state.zone === 'grid') {
        if (state.toolId) activateMobileItem(state.toolId);
      } else {
        snapTo(currentSnap.value === 'collapsed' ? 'primary' : 'collapsed');
      }
    }
    return;
  }

  const now = Date.now();
  state.trail.push({ x: event.clientX, y: event.clientY, t: now });
  const oldest = state.trail[0];
  const dt = now - oldest.t;
  const recentDx = event.clientX - oldest.x;
  const recentDy = event.clientY - oldest.y;
  const velocity = dt > 0 ? Math.sqrt(recentDx * recentDx + recentDy * recentDy) / dt : 0;
  finishSheetDrag(velocity, Math.sign(recentDy));
}

function stopOwnedTouchEvent(event: PointerEvent) {
  event.preventDefault();
  event.stopPropagation();
}

function toolElementFromTouchTarget(target: EventTarget | null): HTMLElement | null {
  const toolElement = target instanceof Element ? (target.closest('[data-tool-id]') as HTMLElement | null) : null;
  if (!toolElement || !gridEl.value?.contains(toolElement)) return null;
  return toolElement;
}

function isTapGesture(state: NavTouchGestureState, event: PointerEvent): boolean {
  const dx = event.clientX - state.startX;
  const dy = event.clientY - state.startY;
  if (Math.hypot(dx, dy) > TOUCH_TAP_TOLERANCE_PX) return false;
  if (state.zone !== 'grid') return true;
  if (!state.toolElement?.isConnected) return false;

  // Pointer capture keeps event.target on the grid even after the finger leaves
  // the button. Use the original button's bounds to allow release-away cancellation.
  const bounds = state.toolElement.getBoundingClientRect();
  return (
    event.clientX >= bounds.left &&
    event.clientX < bounds.right &&
    event.clientY >= bounds.top &&
    event.clientY < bounds.bottom
  );
}

function capturePointer(element: HTMLElement | null, pointerId: number) {
  if (typeof element?.setPointerCapture !== 'function') return;
  try {
    element.setPointerCapture(pointerId);
  } catch {
    // The pointer may already be released by the browser on rapid cancel paths.
  }
}

function releasePointer(element: HTMLElement | null, pointerId: number) {
  if (
    typeof element?.hasPointerCapture !== 'function' ||
    typeof element.releasePointerCapture !== 'function' ||
    !element.hasPointerCapture(pointerId)
  ) {
    return;
  }
  try {
    element.releasePointerCapture(pointerId);
  } catch {
    // Ignore stale pointer capture state on browser-driven cancellation.
  }
}

function clearNativeClickSuppression() {
  nativeClickSuppression = null;
}

function suppressCompatibilityClicks(event: PointerEvent, afterMovement: boolean) {
  nativeClickSuppression = {
    // A drag can reveal a different button under the finger. Block its delayed
    // compatibility clicks until a fresh pointer or keyboard action starts.
    until: afterMovement ? null : Date.now() + NATIVE_CLICK_SUPPRESS_MS,
    allTargets: afterMovement,
    x: event.clientX,
    y: event.clientY,
  };
  window.setTimeout(() => {
    if (nativeClickSuppression?.until != null && Date.now() >= nativeClickSuppression.until) {
      nativeClickSuppression = null;
    }
  }, NATIVE_CLICK_SUPPRESS_MS);
}

function consumeSuppressedNativeClick(event: MouseEvent): boolean {
  const suppression = nativeClickSuppression;
  if (!suppression) return false;
  if (suppression.until != null && Date.now() > suppression.until) {
    nativeClickSuppression = null;
    return false;
  }
  const dx = event.clientX - suppression.x;
  const dy = event.clientY - suppression.y;
  if (!suppression.allTargets && dx * dx + dy * dy > NATIVE_CLICK_SUPPRESS_RADIUS_PX * NATIVE_CLICK_SUPPRESS_RADIUS_PX)
    return false;

  if (!suppression.allTargets) nativeClickSuppression = null;
  event.preventDefault();
  event.stopPropagation();
  event.stopImmediatePropagation?.();
  return true;
}

function onDocumentClickCapture(event: MouseEvent) {
  consumeSuppressedNativeClick(event);
}

// ── Expose for parent ──
defineExpose({
  snapTo,
  isDesktop,
  currentSnap,
  expandedHeight: primaryHeight,
  collapsedHeight,
});
</script>

<style scoped>
.nav-update-badge {
  display: inline-flex;
  align-items: center;
  padding: 0.1rem 0.35rem;
  border-radius: 0.4rem;
  background: var(--accent-subtle);
  color: var(--accent);
  font-size: var(--text-xs-size);
  font-weight: var(--font-semibold);
  line-height: 1.4;
}
.nav-update-badge--inline {
  margin-left: 0.4rem;
  vertical-align: middle;
}
/* ═══════════════════════════════════════════════
   MOBILE: Bottom Sheet with Tool Grid
   ═══════════════════════════════════════════════ */

.nav-sheet {
  --nav-sheet-drag-halo-h: 10px;
  --nav-sheet-handle-bar-h: 0.2rem;
  position: fixed;
  z-index: var(--z-nav-sheet);
  left: 0;
  right: 0;
  bottom: 0;
  height: var(--nav-body-h);
  max-height: calc(100svh - var(--safe-top, 0px) - var(--nav-sheet-top-gap));
  box-sizing: border-box;
  padding-bottom: var(--nav-bottom-pad);
  overflow: hidden;
  background: var(--surface-sheet-solid);
  border-radius: 1rem 1rem 0 0;
  border-top: var(--nav-border-w) solid var(--border-medium);
  box-shadow: var(--shadow-md);
  transition: height 0.45s cubic-bezier(0.32, 0.72, 0, 1);
  will-change: height;
  display: flex;
  flex-direction: column;
  touch-action: none;
}

/* Private regression note (path relative to the application repository root):
   ../mtl-explorer-private/docs-my/verification/regression/mobile-more-map-drag.md
   Keep clipped labels and nested icons under their navigation region's gesture policy. */
.nav-sheet :deep(*) {
  touch-action: inherit;
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
  height: var(--nav-handle-h);
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
  height: var(--nav-handle-h);
  transform: translateX(-50%);
  cursor: grab;
  touch-action: none;
  -webkit-tap-highlight-color: transparent;
}

/* ─── Handle ─── */
.nav-sheet__handle-zone {
  flex: 0 0 var(--nav-handle-h);
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}

.nav-sheet__handle {
  width: 2.5rem;
  height: var(--nav-sheet-handle-bar-h);
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
  min-height: 0;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  overflow: clip;
  touch-action: none;
}

.nav-sheet__row {
  display: flex;
  gap: 2px;
  flex-shrink: 0;
}

.nav-sheet__row--primary {
  flex-basis: var(--nav-primary-row-h);
  min-height: var(--nav-primary-row-h);
  padding-inline: calc(var(--nav-sheet-inline-pad) + var(--safe-left, 0px))
    calc(var(--nav-sheet-inline-pad) + var(--safe-right, 0px));
  touch-action: none;
}

/* ─── More panel content; BottomSheet owns its chrome, sizing and scrolling. ─── */
.nav-sheet__more-title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.nav-sheet__overflow-panel {
  flex: 0 0 auto;
  padding: 0.75rem 1.25rem;
  touch-action: pan-y;
}

.nav-sheet__more-intro {
  margin: 0 0 0.5rem;
  color: var(--text-muted);
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}

.nav-sheet__overflow-list {
  overflow: clip;
}

.nav-sheet__overflow-tool,
.nav-sheet__utility {
  width: 100%;
  min-width: 0;
  min-height: var(--nav-overflow-tool-h);
  box-sizing: border-box;
  display: grid;
  grid-template-columns: 2.5rem minmax(0, 1fr) auto;
  align-items: center;
  gap: 0.75rem;
  padding: 0.5rem 0;
  border: 0;
  border-bottom: 1px solid var(--border-default);
  border-radius: 0;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  text-align: left;
  text-decoration: none;
  transition:
    background-color 0.15s ease,
    color 0.15s ease,
    transform 0.15s ease,
    box-shadow 0.15s ease;
}

.nav-sheet__overflow-list > :last-child {
  border-bottom: 0;
}

.nav-sheet__overflow-icon,
.nav-sheet__utility-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  border-radius: 0.625rem;
  background: var(--accent-bg);
  color: var(--accent-text);
}

.nav-sheet__overflow-icon {
  width: 2.5rem;
  height: 2.5rem;
  font-size: var(--text-lg-size);
}

.nav-sheet__overflow-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  justify-content: center;
}

.nav-sheet__overflow-label,
.nav-sheet__utility-label {
  min-width: 0;
  color: inherit;
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
  font-weight: 600;
  overflow-wrap: anywhere;
}

.nav-sheet__overflow-description {
  display: block;
  min-width: 0;
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
  overflow-wrap: anywhere;
}

.nav-sheet__utility-icon {
  width: 2.5rem;
  height: 2.5rem;
  font-size: var(--text-lg-size);
}

.nav-sheet__overflow-chevron {
  color: var(--accent-text);
  font-size: var(--text-sm-size);
}

.nav-sheet__overflow-chevron::after {
  content: none !important;
}

.nav-sheet__overflow-tool:hover,
.nav-sheet__utility:hover {
  background: var(--accent-bg);
}

.nav-sheet__overflow-tool:active,
.nav-sheet__utility:active {
  transform: scale(0.98);
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
  transition:
    background-color 0.15s ease,
    color 0.15s ease,
    transform 0.15s ease,
    box-shadow 0.15s ease;
  min-width: 0;
}
.nav-sheet__tool i {
  font-size: var(--text-lg-size);
  line-height: 1;
}
.nav-sheet__tool-label {
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
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

.nav-sheet__tool--active .nav-sheet__overflow-icon,
.nav-sheet__tool--active .nav-sheet__utility-icon {
  background: var(--accent-bg);
  color: var(--accent-text);
}

.nav-sheet__tool:focus-visible,
.nav-sheet__overflow-tool:focus-visible,
.nav-sheet__utility:focus-visible,
.nav-panel__tool:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
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
  bottom: var(--nav-sheet-h, 0px);
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
  background: var(--surface-sheet-solid);
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

.nav-panel__footer {
  margin-top: auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  width: 100%;
  padding-top: 0.5rem;
  border-top: 1px solid var(--border-subtle);
}

.nav-panel__utility,
.nav-panel__guide {
  color: var(--text-muted);
  text-decoration: none;
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
