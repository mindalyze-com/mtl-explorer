<template>
  <Teleport to="body">
    <div
      ref="root"
      v-bind="$attrs"
      class="planner-action-menu"
      :style="position"
      @keydown.esc.stop.prevent="dismiss(true)"
      @keydown.tab="onTab"
    >
      <div class="planner-action-menu__header">
        <strong>{{ title }}</strong>
        <button type="button" class="planner-action-menu__close" aria-label="Close actions" @click="dismiss(true)">
          <i class="bi bi-x-lg" aria-hidden="true" />
        </button>
      </div>
      <div v-if="hint && !message" class="planner-action-menu__hint">
        <i class="bi bi-arrows-move" aria-hidden="true" />
        <p :id="hintId">{{ hint }}</p>
        <button type="button" aria-label="Dismiss drag hint" @click="dismissHint">
          <i class="bi bi-x-lg" aria-hidden="true" />
        </button>
      </div>
      <p v-if="message" class="planner-action-menu__message" role="status">{{ message }}</p>
      <PrimeMenu
        :model="items"
        :aria-label="title"
        :pt="{ list: { 'aria-describedby': hint && !message ? hintId : undefined } }"
      />
    </div>
  </Teleport>
</template>

<script lang="ts">
export interface PlannerMenuAction {
  id: string;
  label: string;
  icon?: string;
  destructive?: boolean;
  disabled?: boolean;
}
export interface PlannerMenuBounds {
  left: number;
  top: number;
  right: number;
  bottom: number;
}
export interface PlannerMenuDismissal {
  event?: PointerEvent;
  restoreFocus: boolean;
}
</script>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useId } from 'vue';
import PrimeMenu from 'primevue/menu';
import { observeElementResize } from '@/utils/observeElementResize';
import { WAYPOINT_TOUCH_HIT_RADIUS_PX } from '@/planner/constants/PlannerConstants';

const MENU_EDGE_PX = 8;
const MENU_ANCHOR_GAP_PX = 12;
defineOptions({ inheritAttrs: false });
const props = defineProps<{
  title: string;
  hint?: string;
  message?: string;
  actions: PlannerMenuAction[];
  anchor: { x: number; y: number };
  bounds: PlannerMenuBounds;
  fallbackBounds?: PlannerMenuBounds;
  trigger?: HTMLElement | null;
  keepAnchorVisible?: boolean;
}>();
const emit = defineEmits<{
  action: [id: string];
  dismiss: [detail: PlannerMenuDismissal];
  'dismiss-hint': [];
}>();
const hintId = useId();
const root = ref<HTMLElement | null>(null);
const size = ref({ width: 0, height: 0 });
let stopObserving: (() => void) | undefined;
const items = computed(() =>
  props.actions.map((action) => ({
    label: action.label,
    icon: action.icon,
    disabled: action.disabled,
    class: action.destructive ? 'planner-action-menu__destructive' : undefined,
    command: () => emit('action', action.id),
  }))
);
const position = computed(() => {
  const gap = props.keepAnchorVisible ? WAYPOINT_TOUCH_HIT_RADIUS_PX : MENU_ANCHOR_GAP_PX;
  const sideRoom =
    Math.max(props.bounds.right - props.anchor.x, props.anchor.x - props.bounds.left) - MENU_EDGE_PX - gap;
  const verticalRoom =
    Math.max(props.bounds.bottom - props.anchor.y, props.anchor.y - props.bounds.top) - MENU_EDGE_PX - gap;
  const fitsPreferred =
    size.value.height <=
    (sideRoom >= size.value.width ? props.bounds.bottom - props.bounds.top - MENU_EDGE_PX * 2 : verticalRoom);
  const bounds =
    props.keepAnchorVisible && !fitsPreferred && props.fallbackBounds ? props.fallbackBounds : props.bounds;
  const left = bounds.left + MENU_EDGE_PX;
  const top = bounds.top + MENU_EDGE_PX;
  const right = bounds.right - MENU_EDGE_PX;
  const bottom = bounds.bottom - MENU_EDGE_PX;
  const width = Math.min(size.value.width, Math.max(0, right - left));
  // Leave the point's touch target reachable when its menu is open.
  const fitsRight = props.anchor.x + gap + width <= right;
  const fitsLeft = props.anchor.x - gap - width >= left;
  const insideBounds =
    props.anchor.x >= left && props.anchor.x <= right && props.anchor.y >= top && props.anchor.y <= bottom;
  const stackBesidePoint = props.keepAnchorVisible && insideBounds && !fitsRight && !fitsLeft;
  const below = Math.max(0, bottom - props.anchor.y - gap);
  const above = Math.max(0, props.anchor.y - top - gap);
  const maxHeight = stackBesidePoint ? Math.max(below, above) : Math.max(0, bottom - top);
  const height = Math.min(size.value.height, maxHeight);
  const x = stackBesidePoint
    ? props.anchor.x - width / 2
    : fitsRight
      ? props.anchor.x + gap
      : props.anchor.x - width - gap;
  const y = stackBesidePoint
    ? below >= above
      ? props.anchor.y + gap
      : props.anchor.y - gap - height
    : props.anchor.y + gap + height <= bottom
      ? props.anchor.y + gap
      : props.anchor.y - height - gap;
  return {
    left: `${Math.max(left, Math.min(x, right - width))}px`,
    top: `${Math.max(top, Math.min(y, bottom - height))}px`,
    maxWidth: `${Math.max(0, right - left)}px`,
    maxHeight: `${maxHeight}px`,
  };
});
function dismiss(restoreFocus: boolean) {
  emit('dismiss', { restoreFocus });
}
function dismissHint() {
  emit('dismiss-hint');
  root.value?.querySelector<HTMLElement>('[role="menu"]')?.focus();
}
function onTab(event: KeyboardEvent) {
  if (props.hint && !props.message) {
    const controls = Array.from(
      root.value?.querySelectorAll<HTMLElement>(
        '.planner-action-menu__close, .planner-action-menu__hint button, [role="menu"]'
      ) ?? []
    );
    const index = controls.findIndex((control) => control === event.target || control.contains(event.target as Node));
    const next = index + (event.shiftKey ? -1 : 1);
    if (index !== -1 && next >= 0 && next < controls.length) return;
  }
  event.preventDefault();
  dismiss(true);
}
function onOutsidePointer(event: PointerEvent) {
  if (!(event.target instanceof Node) || root.value?.contains(event.target) || props.trigger?.contains(event.target))
    return;
  emit('dismiss', { event, restoreFocus: false });
}
onMounted(async () => {
  const measure = () => {
    const rect = root.value?.getBoundingClientRect();
    if (rect) {
      const actions = root.value?.querySelector<HTMLElement>('.p-menu');
      const overflow = actions ? Math.max(0, actions.scrollHeight - actions.clientHeight) : 0;
      size.value = { width: rect.width, height: rect.height + overflow };
    }
  };
  if (root.value) stopObserving = observeElementResize(root.value, measure);
  measure();
  document.addEventListener('pointerdown', onOutsidePointer, true);
  await nextTick();
  root.value?.querySelector<HTMLElement>('[role="menu"]')?.focus();
});
onBeforeUnmount(() => {
  stopObserving?.();
  document.removeEventListener('pointerdown', onOutsidePointer, true);
});
</script>

<style scoped>
.planner-action-menu {
  display: flex;
  flex-direction: column;
  position: fixed;
  z-index: calc(var(--z-bottom-sheet-content, 5002) + 1);
  width: 16rem;
  box-sizing: border-box;
  overflow: hidden;
  overscroll-behavior: contain;
  border: 1px solid var(--border-default);
  border-radius: var(--p-content-border-radius);
  background: var(--surface-sheet-solid);
  color: var(--text-primary);
  box-shadow: var(--shadow-sheet);
}
.planner-action-menu__header {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  padding-left: 0.75rem;
  gap: 0.5rem;
}
.planner-action-menu__close {
  min-width: 2.75rem;
  min-height: 2.75rem;
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
}
.planner-action-menu__message {
  flex-shrink: 0;
  padding: 0 0.75rem;
  font-size: var(--text-sm-size);
}
.planner-action-menu__hint {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 0.5rem;
  margin: 0 0.5rem 0.25rem;
  padding-left: 0.5rem;
  border: 1px solid var(--accent-muted);
  border-radius: 0.5rem;
  background: var(--accent-subtle);
  color: var(--text-primary);
}
.planner-action-menu__hint > i {
  flex-shrink: 0;
  color: var(--accent-text);
}
.planner-action-menu__hint p {
  flex: 1;
  min-width: 0;
  margin: 0;
  padding-block: 0.5rem;
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}
.planner-action-menu__hint button {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 2.75rem;
  height: 2.75rem;
  padding: 0;
  border: 0;
  border-radius: 0.5rem;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
}
.planner-action-menu__hint button:hover {
  background: var(--surface-hover);
  color: var(--text-primary);
}
.planner-action-menu__hint button:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: -2px;
}
.planner-action-menu :deep(.p-menu) {
  flex: 1 1 auto;
  min-width: 0;
  min-height: 0;
  overflow: auto;
  overscroll-behavior: contain;
  border: 0;
  border-radius: 0;
  background: transparent;
}
.planner-action-menu :deep(.p-menu-item-link) {
  min-height: 2.75rem;
  box-sizing: border-box;
}
.planner-action-menu :deep(.planner-action-menu__destructive .p-menu-item-link) {
  color: var(--error-text);
}
</style>
