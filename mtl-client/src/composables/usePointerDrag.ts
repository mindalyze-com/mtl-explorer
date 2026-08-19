import { type Ref, watchEffect } from 'vue';

export const POINTER_DRAG_EXCLUDE_SELECTOR = [
  'button',
  'a',
  '[role="button"]',
  'input',
  'select',
  'textarea',
  'label',
  'summary',
  '[contenteditable="true"]',
  '[data-drag-exclude]',
  '[data-sheet-drag-exclude]',
  '.drag-exclude',
  '.sheet-drag-exclude',
].join(', ');

export function isPointerDragExcluded(target: EventTarget | null): boolean {
  return target instanceof Element && Boolean(target.closest(POINTER_DRAG_EXCLUDE_SELECTOR));
}

export interface DragState {
  /** Delta from drag start: [dx, dy]. Positive Y = finger moved down on screen. */
  movement: [number, number];
  /** Absolute speed in px/ms (scalar, always >= 0). */
  velocity: number;
  /** Direction sign per axis: -1, 0, or 1.  Y: -1 = up, 1 = down. */
  direction: [number, number];
  /** True while the pointer is held and has moved (between first and last). */
  dragging: boolean;
  /** True on the very first move event of a drag. */
  first: boolean;
  /** True on pointerup / pointercancel (drag ended). */
  last: boolean;
}

export interface PointerDragOptions {
  /** Ignore movement until the pointer has travelled this many CSS pixels. */
  activationThresholdPx?: number;
  /** Lock activation to one axis. A dominant cross-axis movement rejects the drag. */
  axis?: 'both' | 'x' | 'y';
  /** Permit drag recognition when the gesture starts on a button or another interactive child. */
  allowFromInteractive?: boolean;
  /** Prevent the browser's default action after a drag has been activated. */
  preventDefaultOnDrag?: boolean;
}

/**
 * Composable that tracks pointer drag gestures on a target element.
 *
 * Uses native Pointer Events with `setPointerCapture` for reliable tracking.
 * Automatically re-binds when the target ref changes and cleans up on unmount.
 *
 * Mobile CSS contract: reserve the intended gesture with `touch-action`. If a
 * drag zone contains a scroll container, apply the policy to that inner
 * scroller too. Chrome resolves touch gestures only up to the nearest scroller
 * and may otherwise cancel the pointer before capture starts.
 *
 * Taps (pointerdown → pointerup with no movement) are ignored so that click
 * handlers on child elements (e.g. buttons) are not intercepted.
 */
export function usePointerDrag(
  target: Ref<HTMLElement | null>,
  handler: (state: DragState) => void,
  options: PointerDragOptions = {}
) {
  watchEffect((onCleanup) => {
    const raw = target.value;
    if (!raw) return;
    // Alias as non-nullable so closures below don't need redundant null-guards.
    const el: HTMLElement = raw;

    let active = false;
    let hasMoved = false;
    let activePointerId: number | null = null;
    let startX = 0;
    let startY = 0;
    let lastX = 0;
    let lastY = 0;

    // Track recent positions to compute end-of-drag velocity from the last ~80ms
    // rather than the full drag span. This lets slow placement stick while fast
    // flicks trigger snap/fling.
    const VELOCITY_WINDOW = 80; // ms
    let trail: Array<{ x: number; y: number; t: number }> = [];
    const activationThresholdPx = Math.max(0, options.activationThresholdPx ?? 0);
    const axis = options.axis ?? 'both';

    function capturePointer(pointerId: number) {
      if (typeof el.setPointerCapture !== 'function') return;
      try {
        el.setPointerCapture(pointerId);
      } catch {
        // The browser may already have cancelled or reassigned this pointer.
      }
    }

    function releasePointer(pointerId: number) {
      if (
        typeof el.hasPointerCapture !== 'function' ||
        typeof el.releasePointerCapture !== 'function' ||
        !el.hasPointerCapture(pointerId)
      ) {
        return;
      }
      try {
        el.releasePointerCapture(pointerId);
      } catch {
        // Ignore stale capture state on rapid pointer cancellation.
      }
    }

    function dragActivation(mx: number, my: number): 'pending' | 'accepted' | 'rejected' {
      const absX = Math.abs(mx);
      const absY = Math.abs(my);
      if (axis === 'both') {
        return Math.sqrt(mx * mx + my * my) >= activationThresholdPx ? 'accepted' : 'pending';
      }

      const primary = axis === 'x' ? absX : absY;
      const cross = axis === 'x' ? absY : absX;
      if (primary >= activationThresholdPx && primary >= cross) return 'accepted';
      if (cross >= activationThresholdPx && cross > primary) return 'rejected';
      return 'pending';
    }

    function addPointerTrackingListeners() {
      window.addEventListener('pointermove', onPointerMove, { passive: false });
      window.addEventListener('pointerup', onPointerUp);
      window.addEventListener('pointercancel', onPointerUp);
    }

    function removePointerTrackingListeners() {
      window.removeEventListener('pointermove', onPointerMove);
      window.removeEventListener('pointerup', onPointerUp);
      window.removeEventListener('pointercancel', onPointerUp);
    }

    function onPointerDown(e: PointerEvent) {
      if (e.button !== 0 || active) return; // primary button only
      // Don't intercept clicks on interactive elements inside the drag zone
      if (!options.allowFromInteractive && isPointerDragExcluded(e.target)) return;
      active = true;
      hasMoved = false;
      activePointerId = e.pointerId;
      startX = e.clientX;
      startY = e.clientY;
      lastX = e.clientX;
      lastY = e.clientY;
      trail = [{ x: e.clientX, y: e.clientY, t: Date.now() }];
      // Follow the pointer before the drag threshold is crossed. A quick mouse
      // or touch movement can leave a narrow drag zone before it emits an
      // intermediate move there, so element-only listeners miss the gesture.
      addPointerTrackingListeners();
      if (activationThresholdPx === 0 && axis === 'both') capturePointer(e.pointerId);
    }

    function onPointerMove(e: PointerEvent) {
      if (!active || e.pointerId !== activePointerId) return;

      const mx = e.clientX - startX;
      const my = e.clientY - startY;
      const now = Date.now();

      trail.push({ x: e.clientX, y: e.clientY, t: now });
      // Keep only recent entries
      while (trail.length > 2 && now - trail[0].t > VELOCITY_WINDOW) trail.shift();

      if (!hasMoved) {
        const activation = dragActivation(mx, my);
        if (activation === 'pending') return;
        if (activation === 'rejected') {
          active = false;
          activePointerId = null;
          trail = [];
          removePointerTrackingListeners();
          return;
        }

        // First accepted move event for this drag
        hasMoved = true;
        capturePointer(e.pointerId);
        if (options.preventDefaultOnDrag) e.preventDefault();
        handler({
          movement: [mx, my],
          velocity: 0,
          direction: [Math.sign(mx), Math.sign(my)],
          dragging: true,
          first: true,
          last: false,
        });
      } else {
        if (options.preventDefaultOnDrag) e.preventDefault();
        const dx = e.clientX - lastX;
        const dy = e.clientY - lastY;
        handler({
          movement: [mx, my],
          velocity: 0,
          direction: [Math.sign(dx), Math.sign(dy)],
          dragging: true,
          first: false,
          last: false,
        });
      }

      lastX = e.clientX;
      lastY = e.clientY;
    }

    function onPointerUp(e: PointerEvent) {
      if (!active || e.pointerId !== activePointerId) return;
      active = false;
      activePointerId = null;
      releasePointer(e.pointerId);
      removePointerTrackingListeners();

      // Tap filter: no movement → don't fire, let click handlers work
      if (!hasMoved) {
        trail = [];
        return;
      }

      if (options.preventDefaultOnDrag) e.preventDefault();

      const mx = e.clientX - startX;
      const my = e.clientY - startY;

      // Compute velocity and direction from recent trail (last ~80ms), not full drag
      let velocity = 0;
      let recentDx = mx;
      let recentDy = my;
      const now = Date.now();
      trail.push({ x: e.clientX, y: e.clientY, t: now });
      const oldest = trail[0];
      const dt = now - oldest.t;
      if (dt > 0) {
        recentDx = e.clientX - oldest.x;
        recentDy = e.clientY - oldest.y;
        velocity = Math.sqrt(recentDx * recentDx + recentDy * recentDy) / dt;
      }

      handler({
        movement: [mx, my],
        velocity,
        direction: [Math.sign(recentDx), Math.sign(recentDy)],
        dragging: false,
        first: false,
        last: true,
      });

      trail = [];
    }

    el.addEventListener('pointerdown', onPointerDown);

    onCleanup(() => {
      el.removeEventListener('pointerdown', onPointerDown);
      removePointerTrackingListeners();
    });
  });
}
