import { type Ref, watchEffect } from 'vue';

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

/**
 * Composable that tracks pointer drag gestures on a target element.
 *
 * Uses native Pointer Events with `setPointerCapture` for reliable tracking.
 * Automatically re-binds when the target ref changes and cleans up on unmount.
 *
 * Taps (pointerdown → pointerup with no movement) are ignored so that click
 * handlers on child elements (e.g. buttons) are not intercepted.
 */
export function usePointerDrag(
  target: Ref<HTMLElement | null>,
  handler: (state: DragState) => void,
) {
  watchEffect((onCleanup) => {
    const raw = target.value;
    if (!raw) return;
    // Alias as non-nullable so closures below don't need redundant null-guards.
    const el: HTMLElement = raw;

    let active = false;
    let hasMoved = false;
    let startX = 0;
    let startY = 0;
    let lastX = 0;
    let lastY = 0;

    // Track recent positions to compute end-of-drag velocity from the last ~80ms
    // rather than the full drag span. This lets slow placement stick while fast
    // flicks trigger snap/fling.
    const VELOCITY_WINDOW = 80; // ms
    let trail: Array<{ x: number; y: number; t: number }> = [];

    function onPointerDown(e: PointerEvent) {
      if (e.button !== 0) return; // primary button only
      // Don't intercept clicks on interactive elements inside the drag zone
      if ((e.target as HTMLElement).closest('button, a, [role="button"], input, select, textarea')) return;
      active = true;
      hasMoved = false;
      startX = e.clientX;
      startY = e.clientY;
      lastX = e.clientX;
      lastY = e.clientY;
      trail = [{ x: e.clientX, y: e.clientY, t: Date.now() }];
      el.setPointerCapture(e.pointerId);
    }

    function onPointerMove(e: PointerEvent) {
      if (!active) return;

      const mx = e.clientX - startX;
      const my = e.clientY - startY;
      const now = Date.now();

      trail.push({ x: e.clientX, y: e.clientY, t: now });
      // Keep only recent entries
      while (trail.length > 2 && now - trail[0].t > VELOCITY_WINDOW) trail.shift();

      if (!hasMoved) {
        // First move event for this drag
        hasMoved = true;
        handler({
          movement: [mx, my],
          velocity: 0,
          direction: [Math.sign(mx), Math.sign(my)],
          dragging: true,
          first: true,
          last: false,
        });
      } else {
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
      if (!active) return;
      active = false;

      // Tap filter: no movement → don't fire, let click handlers work
      if (!hasMoved) return;

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
    el.addEventListener('pointermove', onPointerMove);
    el.addEventListener('pointerup', onPointerUp);
    el.addEventListener('pointercancel', onPointerUp);

    onCleanup(() => {
      el.removeEventListener('pointerdown', onPointerDown);
      el.removeEventListener('pointermove', onPointerMove);
      el.removeEventListener('pointerup', onPointerUp);
      el.removeEventListener('pointercancel', onPointerUp);
    });
  });
}
