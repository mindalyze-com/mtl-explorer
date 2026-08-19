import type { Ref } from 'vue';
import { usePointerDrag } from '@/composables/usePointerDrag';

export interface VerticalResizeDragCallbacks {
  onStart: () => void;
  onResize: (deltaY: number) => void;
  onEnd: () => void;
}

export interface VerticalResizeDragOptions {
  activationThresholdPx: number;
  allowFromInteractive?: boolean;
}

/**
 * Shares vertical resize gesture handling between a visible handle and any
 * larger drag-sensitive zones around it. Taps remain available to the caller.
 */
export function useVerticalResizeDrag(
  target: Ref<HTMLElement | null>,
  callbacks: VerticalResizeDragCallbacks,
  options: VerticalResizeDragOptions
) {
  let suppressNextClick = false;

  usePointerDrag(
    target,
    ({ movement: [, deltaY], dragging, first, last }) => {
      if (first) callbacks.onStart();
      if (dragging) callbacks.onResize(deltaY);
      if (last) {
        callbacks.onEnd();
        suppressNextClick = true;
        window.setTimeout(() => {
          suppressNextClick = false;
        }, 0);
      }
    },
    {
      activationThresholdPx: options.activationThresholdPx,
      // Resize uses vertical distance, but activation uses total movement. A
      // slightly horizontal first touch sample must not reject the complete
      // gesture before the user's vertical intent becomes clear.
      axis: 'both',
      allowFromInteractive: options.allowFromInteractive,
      preventDefaultOnDrag: true,
    }
  );

  function consumeClickAfterDrag(event?: Event): boolean {
    if (!suppressNextClick) return false;
    suppressNextClick = false;
    event?.preventDefault();
    event?.stopPropagation();
    return true;
  }

  return { consumeClickAfterDrag };
}
