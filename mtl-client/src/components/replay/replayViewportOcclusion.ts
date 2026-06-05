export type ReplayViewportOcclusionLayout = {
  open?: boolean;
  detentId?: string;
  fullscreen?: boolean;
  dragging?: boolean;
  heightPx?: number;
  widthPx?: number;
  topPx?: number;
  rightPx?: number;
  bottomPx?: number;
  leftPx?: number;
};

export type ReplayViewportPadding = {
  paddingTop: number;
  paddingRight: number;
  paddingBottom: number;
  paddingLeft: number;
};

type RectLike = {
  top: number;
  right: number;
  bottom: number;
  left: number;
  width: number;
  height: number;
};

type ReplayViewportOcclusionOptions = {
  canvas: HTMLElement | null | undefined;
  baseMarginPx: number;
  minVisibleWidthPx: number;
  minVisibleHeightPx: number;
  layouts?: Array<ReplayViewportOcclusionLayout | null | undefined>;
  root?: ParentNode;
};

const BOTTOM_SHEET_SELECTOR = '.sheet';
const BOTTOM_EDGE_TOLERANCE_PX = 6;

export function computeReplayViewportPadding({
  canvas,
  baseMarginPx,
  minVisibleWidthPx,
  minVisibleHeightPx,
  layouts = [],
  root = typeof document !== 'undefined' ? document : undefined,
}: ReplayViewportOcclusionOptions): ReplayViewportPadding {
  const base = Math.max(0, baseMarginPx);
  const padding: ReplayViewportPadding = {
    paddingTop: base,
    paddingRight: base,
    paddingBottom: base,
    paddingLeft: base,
  };
  if (!canvas) return padding;

  const canvasRect = rectFromDomRect(canvas.getBoundingClientRect());
  if (!isUsableRect(canvasRect)) return padding;

  for (const rect of occlusionRects(root, layouts)) {
    if (!isBottomEdgeOcclusion(canvasRect, rect)) continue;
    const bottomOverlapPx = Math.max(0, canvasRect.bottom - Math.max(canvasRect.top, rect.top));
    padding.paddingBottom = Math.max(padding.paddingBottom, bottomOverlapPx + base);
  }

  return constrainReplayViewportPadding(padding, canvasRect.width, canvasRect.height, {
    minVisibleWidthPx,
    minVisibleHeightPx,
  });
}

export function observeReplayViewportOcclusion(onChange: () => void): { disconnect: () => void } {
  if (typeof window === 'undefined' || typeof document === 'undefined') {
    return { disconnect: () => undefined };
  }

  let frame: number | null = null;
  let resizeObserver: ResizeObserver | null = null;
  let mutationObserver: MutationObserver | null = null;

  const schedule = () => {
    if (frame !== null) return;
    frame = window.requestAnimationFrame(() => {
      frame = null;
      observeSheets();
      onChange();
    });
  };

  const observeSheets = () => {
    if (typeof ResizeObserver === 'undefined') return;
    if (!resizeObserver) {
      resizeObserver = new ResizeObserver(schedule);
    } else {
      resizeObserver.disconnect();
    }
    document.querySelectorAll(BOTTOM_SHEET_SELECTOR).forEach((sheet) => resizeObserver?.observe(sheet));
  };

  const onTransition = (event: Event) => {
    const target = event.target;
    if (target instanceof Element && target.closest(BOTTOM_SHEET_SELECTOR)) {
      schedule();
    }
  };

  observeSheets();

  if (typeof MutationObserver !== 'undefined' && document.body) {
    mutationObserver = new MutationObserver((mutations) => {
      if (!mutations.some(isSheetMutation)) return;
      observeSheets();
      schedule();
    });
    mutationObserver.observe(document.body, {
      attributes: true,
      attributeFilter: ['class', 'style'],
      childList: true,
      subtree: true,
    });
  }

  window.addEventListener('resize', schedule);
  window.addEventListener('orientationchange', schedule);
  document.addEventListener('transitionend', onTransition, true);
  document.addEventListener('transitioncancel', onTransition, true);

  return {
    disconnect() {
      if (frame !== null) {
        window.cancelAnimationFrame(frame);
        frame = null;
      }
      resizeObserver?.disconnect();
      mutationObserver?.disconnect();
      window.removeEventListener('resize', schedule);
      window.removeEventListener('orientationchange', schedule);
      document.removeEventListener('transitionend', onTransition, true);
      document.removeEventListener('transitioncancel', onTransition, true);
    },
  };
}

function isSheetMutation(mutation: MutationRecord): boolean {
  const target = mutation.target;
  if (target instanceof Element && (target.matches(BOTTOM_SHEET_SELECTOR) || target.closest(BOTTOM_SHEET_SELECTOR))) {
    return true;
  }
  return [...mutation.addedNodes, ...mutation.removedNodes].some((node) => {
    return (
      node instanceof Element &&
      (node.matches(BOTTOM_SHEET_SELECTOR) || Boolean(node.querySelector(BOTTOM_SHEET_SELECTOR)))
    );
  });
}

function occlusionRects(
  root: ParentNode | undefined,
  layouts: Array<ReplayViewportOcclusionLayout | null | undefined>
): RectLike[] {
  const rects: RectLike[] = [];

  for (const layout of layouts) {
    const rect = rectFromLayout(layout);
    if (rect) rects.push(rect);
  }

  root?.querySelectorAll?.(BOTTOM_SHEET_SELECTOR).forEach((sheet) => {
    const rect = rectFromSheetElement(sheet);
    if (rect) rects.push(rect);
  });

  return rects;
}

function rectFromLayout(layout: ReplayViewportOcclusionLayout | null | undefined): RectLike | null {
  if (!layout?.open && !layout?.dragging && !layout?.fullscreen) return null;
  const top = Number(layout?.topPx);
  const right = Number(layout?.rightPx);
  const bottom = Number(layout?.bottomPx);
  const left = Number(layout?.leftPx);
  const width = Number.isFinite(Number(layout?.widthPx)) ? Number(layout?.widthPx) : right - left;
  const height = Number.isFinite(Number(layout?.heightPx)) ? Number(layout?.heightPx) : bottom - top;
  const rect = { top, right, bottom, left, width, height };
  return isUsableRect(rect) ? rect : null;
}

function rectFromSheetElement(sheet: Element): RectLike | null {
  if (sheet.classList.contains('sheet--hidden')) return null;
  const win = sheet.ownerDocument.defaultView;
  const style = win?.getComputedStyle(sheet);
  if (style?.display === 'none' || style?.visibility === 'hidden' || Number(style?.opacity) === 0) return null;
  const rect = rectFromDomRect(sheet.getBoundingClientRect());
  return isUsableRect(rect) ? rect : null;
}

function rectFromDomRect(rect: DOMRect): RectLike {
  return {
    top: rect.top,
    right: rect.right,
    bottom: rect.bottom,
    left: rect.left,
    width: rect.width,
    height: rect.height,
  };
}

function isUsableRect(rect: RectLike): boolean {
  return (
    [rect.top, rect.right, rect.bottom, rect.left, rect.width, rect.height].every(Number.isFinite) &&
    rect.width > 0 &&
    rect.height > 0
  );
}

function isBottomEdgeOcclusion(canvasRect: RectLike, rect: RectLike): boolean {
  const horizontalOverlap = Math.min(canvasRect.right, rect.right) - Math.max(canvasRect.left, rect.left);
  const verticalOverlap = Math.min(canvasRect.bottom, rect.bottom) - Math.max(canvasRect.top, rect.top);
  return horizontalOverlap > 0 && verticalOverlap > 0 && rect.bottom >= canvasRect.bottom - BOTTOM_EDGE_TOLERANCE_PX;
}

function constrainReplayViewportPadding(
  padding: ReplayViewportPadding,
  width: number,
  height: number,
  {
    minVisibleWidthPx,
    minVisibleHeightPx,
  }: {
    minVisibleWidthPx: number;
    minVisibleHeightPx: number;
  }
): ReplayViewportPadding {
  const next = { ...padding };
  const maxHorizontalPadding = Math.max(0, width - minVisibleWidthPx);
  const horizontalPadding = next.paddingLeft + next.paddingRight;
  if (horizontalPadding > maxHorizontalPadding && horizontalPadding > 0) {
    const scale = maxHorizontalPadding / horizontalPadding;
    next.paddingLeft *= scale;
    next.paddingRight *= scale;
  }

  const maxVerticalPadding = Math.max(0, height - minVisibleHeightPx);
  const verticalPadding = next.paddingTop + next.paddingBottom;
  if (verticalPadding > maxVerticalPadding && verticalPadding > 0) {
    const scale = maxVerticalPadding / verticalPadding;
    next.paddingTop *= scale;
    next.paddingBottom *= scale;
  }
  return next;
}
