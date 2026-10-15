import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
  type ComputedRef,
  type InjectionKey,
  type Ref,
} from 'vue';

export interface SheetControlsState {
  hidden: ComputedRef<boolean>;
}

export const sheetControlsKey: InjectionKey<SheetControlsState> = Symbol('sheet-controls');
export const IMMERSIVE_CONTROLS_IDLE_MS = 2500;
export const IMMERSIVE_CONTROLS_REVEAL_EDGE_PX = 48;

/** Keep keyboard controls available while allowing an unobstructed idle photo. */
export function useImmersiveSheetControls(element: Ref<HTMLElement | null>, enabled: ComputedRef<boolean>) {
  const hidden = ref(false);
  let timer: ReturnType<typeof setTimeout> | undefined;
  let keyboardInteraction = false;
  let hoveringToolbar = false;

  function reveal() {
    clearTimeout(timer);
    hidden.value = false;
    if (!enabled.value) return;
    timer = setTimeout(() => {
      const focused = document.activeElement;
      const ownsFocus = focused instanceof Element && element.value?.contains(focused);
      const editing = ownsFocus && focused.matches('input, textarea, select, [contenteditable="true"]');
      const menuOpen = Boolean(element.value?.querySelector('.media-viewer-panel-menu[open]'));
      if (hoveringToolbar || menuOpen) {
        reveal();
        return;
      }
      if (!editing && !(keyboardInteraction && ownsFocus)) {
        // Move mouse focus within the fullscreen element before making its controls inert.
        if (ownsFocus && focused.closest('.sheet-drag-zone, [data-media-control]')) {
          element.value?.focus({ preventScroll: true });
          clearTimeout(timer);
        }
        hidden.value = true;
      }
    }, IMMERSIVE_CONTROLS_IDLE_MS);
  }

  function onPointerMove(event: PointerEvent) {
    if (!enabled.value) return;
    const toolbar = element.value?.querySelector('.sheet-drag-zone');
    const toolbarBottom = toolbar?.getBoundingClientRect().bottom ?? 0;
    hoveringToolbar = !hidden.value && event.clientY <= toolbarBottom;
    const top = element.value?.getBoundingClientRect().top ?? 0;
    if (hoveringToolbar || event.clientY <= top + IMMERSIVE_CONTROLS_REVEAL_EDGE_PX) reveal();
  }

  function onPointerDown(event: PointerEvent) {
    keyboardInteraction = false;
    if (event.pointerType === 'touch' || !hidden.value) reveal();
    else onPointerMove(event);
  }

  function onKeyDown(event: KeyboardEvent) {
    if (!enabled.value) return;
    keyboardInteraction = true;
    const restoreFocus = hidden.value && event.key === 'Tab';
    if (restoreFocus) event.preventDefault();
    reveal();
    if (restoreFocus)
      void nextTick(() =>
        element.value?.querySelector<HTMLElement>('.sheet-drag-zone button, .sheet-drag-zone summary')?.focus()
      );
  }

  watch(
    enabled,
    () => {
      hoveringToolbar = false;
      keyboardInteraction = false;
      reveal();
    },
    { immediate: true }
  );
  onMounted(() => document.addEventListener('keydown', onKeyDown, true));
  onBeforeUnmount(() => {
    clearTimeout(timer);
    document.removeEventListener('keydown', onKeyDown, true);
  });

  return { hidden: computed(() => enabled.value && hidden.value), reveal, onPointerMove, onPointerDown };
}
