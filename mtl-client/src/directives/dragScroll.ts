import type {DirectiveBinding} from 'vue';

interface DragState {
  isDown: boolean;
  startX: number;
  scrollLeft: number;
  startY: number;
  scrollTop: number;
  moved: boolean;
}

function addDragScroll(el: HTMLElement) {
  const state: DragState = {isDown: false, startX: 0, scrollLeft: 0, startY: 0, scrollTop: 0, moved: false};

  const onPointerDown = (e: PointerEvent) => {
    // Only left button / primary touch
    if (e.button !== 0) return;
    state.isDown = true;
    state.startX = e.clientX;
    state.startY = e.clientY;
    state.scrollLeft = el.scrollLeft;
    state.scrollTop = el.scrollTop;
    state.moved = false;
    el.setPointerCapture(e.pointerId);
    el.classList.add('drag-scroll-active');
  };

  const onPointerMove = (e: PointerEvent) => {
    if (!state.isDown) return;
    const dx = e.clientX - state.startX;
    const dy = e.clientY - state.startY;
    if (Math.abs(dx) > 2 || Math.abs(dy) > 2) state.moved = true;
    // In horizontal wrappers prefer horizontal; panel-scroll wrapper may allow vertical
    el.scrollLeft = state.scrollLeft - dx;
    // Only adjust vertical if element is vertically scrollable
    if (el.scrollHeight > el.clientHeight) {
      el.scrollTop = state.scrollTop - dy;
    }
  };

  const onPointerUp = (e: PointerEvent) => {
    if (!state.isDown) return;
    state.isDown = false;
    el.releasePointerCapture(e.pointerId);
    el.classList.remove('drag-scroll-active');
  };

  el.addEventListener('pointerdown', onPointerDown);
  el.addEventListener('pointermove', onPointerMove);
  el.addEventListener('pointerup', onPointerUp);
  el.addEventListener('pointercancel', onPointerUp);

  // Store cleanup
  // @ts-ignore
  el.__dragScrollCleanup = () => {
    el.removeEventListener('pointerdown', onPointerDown);
    el.removeEventListener('pointermove', onPointerMove);
    el.removeEventListener('pointerup', onPointerUp);
    el.removeEventListener('pointercancel', onPointerUp);
  };
}

export const dragScroll = {
  mounted(el: HTMLElement, _binding: DirectiveBinding) {
    addDragScroll(el);
  },
  unmounted(el: HTMLElement) {
    // @ts-ignore
    if (el.__dragScrollCleanup) el.__dragScrollCleanup();
  }
};

export default dragScroll;
