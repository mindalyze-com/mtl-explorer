import { computed, onBeforeUnmount, ref, watch, type Ref } from 'vue';

const MIN_SCALE = 1;
const MAX_SCALE = 6;
const WHEEL_SCALE_RATE = 0.002;
const SWIPE_THRESHOLD_PX = 56;
const SWIPE_HORIZONTAL_DOMINANCE = 1.25;
const VIDEO_CONTROLS_EXCLUSION_HEIGHT_PX = 56;
const INTERACTIVE_TARGET_SELECTOR =
  'button, a, input, textarea, select, [contenteditable="true"], [data-media-control]';

type Point = { x: number; y: number };

export function useMediaViewport(options: {
  mediaId: () => number | null;
  imageEl: Ref<HTMLImageElement | null>;
  viewportEl: Ref<HTMLElement | null>;
  canGoPrev: () => boolean;
  canGoNext: () => boolean;
  previous: () => void;
  next: () => void;
}) {
  const scale = ref(MIN_SCALE);
  const offsetX = ref(0);
  const offsetY = ref(0);
  const points = new Map<number, Point>();
  let gestureStart: Point | null = null;
  let panStart: Point | null = null;
  let panStartOffset: Point = { x: 0, y: 0 };
  let pinchStartDistance = 0;
  let pinchStartScale = MIN_SCALE;
  let pinchStartMidpoint: Point | null = null;
  let pinchStartOffset: Point = { x: 0, y: 0 };

  const transformStyle = computed(() => ({
    transform: `translate3d(${offsetX.value}px, ${offsetY.value}px, 0) scale(${scale.value})`,
    cursor: scale.value > MIN_SCALE ? 'grab' : 'zoom-in',
  }));
  const isZoomed = computed(() => scale.value > MIN_SCALE);

  function clampScale(value: number): number {
    return Math.max(MIN_SCALE, Math.min(MAX_SCALE, value));
  }

  function panBounds(nextScale = scale.value): Point {
    const image = options.imageEl.value;
    const viewport = options.viewportEl.value;
    if (!image || !viewport || nextScale <= MIN_SCALE) return { x: 0, y: 0 };
    return {
      x: Math.max(0, (image.clientWidth * nextScale - viewport.clientWidth) / 2),
      y: Math.max(0, (image.clientHeight * nextScale - viewport.clientHeight) / 2),
    };
  }

  function clampOffsets(nextScale = scale.value): void {
    const bounds = panBounds(nextScale);
    offsetX.value = Math.max(-bounds.x, Math.min(bounds.x, offsetX.value));
    offsetY.value = Math.max(-bounds.y, Math.min(bounds.y, offsetY.value));
  }

  function reset(): void {
    scale.value = MIN_SCALE;
    offsetX.value = 0;
    offsetY.value = 0;
    points.clear();
    gestureStart = null;
    panStart = null;
  }

  function zoomAt(nextScale: number, anchor: Point): void {
    const viewport = options.viewportEl.value;
    if (!viewport) return;
    const previousScale = scale.value;
    const normalizedScale = clampScale(nextScale);
    if (normalizedScale === previousScale) return;

    if (normalizedScale === MIN_SCALE) {
      reset();
      return;
    }

    const rect = viewport.getBoundingClientRect();
    const fromCenterX = anchor.x - rect.left - rect.width / 2;
    const fromCenterY = anchor.y - rect.top - rect.height / 2;
    const ratio = normalizedScale / previousScale;
    offsetX.value += (1 - ratio) * (fromCenterX - offsetX.value);
    offsetY.value += (1 - ratio) * (fromCenterY - offsetY.value);
    scale.value = normalizedScale;
    clampOffsets(normalizedScale);
  }

  function onWheel(event: WheelEvent): void {
    if (!options.imageEl.value || isInteractiveTarget(event.target)) return;
    event.preventDefault();
    const factor = Math.exp(-event.deltaY * WHEEL_SCALE_RATE);
    zoomAt(scale.value * factor, { x: event.clientX, y: event.clientY });
  }

  function onDoubleClick(event: MouseEvent): void {
    if (!options.imageEl.value || isInteractiveTarget(event.target)) return;
    if (scale.value > MIN_SCALE) reset();
    else zoomAt(2, { x: event.clientX, y: event.clientY });
  }

  function onPointerDown(event: PointerEvent): void {
    if (event.button !== 0 || isInteractiveTarget(event.target) || isVideoControlsPointer(event)) return;
    const viewport = options.viewportEl.value;
    if (!viewport) return;
    const eventTarget = event.target;
    const captureTarget = eventTarget instanceof Element ? eventTarget : viewport;
    captureTarget.setPointerCapture?.(event.pointerId);
    points.set(event.pointerId, { x: event.clientX, y: event.clientY });

    if (points.size === 1) {
      gestureStart = { x: event.clientX, y: event.clientY };
      panStart = gestureStart;
      panStartOffset = { x: offsetX.value, y: offsetY.value };
      return;
    }

    if (points.size === 2) {
      const [a, b] = [...points.values()];
      pinchStartDistance = distance(a, b);
      pinchStartScale = scale.value;
      pinchStartMidpoint = midpoint(a, b);
      pinchStartOffset = { x: offsetX.value, y: offsetY.value };
      gestureStart = null;
    }
  }

  function onPointerMove(event: PointerEvent): void {
    if (!points.has(event.pointerId)) return;
    points.set(event.pointerId, { x: event.clientX, y: event.clientY });

    if (points.size >= 2 && pinchStartDistance > 0 && pinchStartMidpoint) {
      const [a, b] = [...points.values()];
      const nextMidpoint = midpoint(a, b);
      scale.value = clampScale(pinchStartScale * (distance(a, b) / pinchStartDistance));
      offsetX.value = pinchStartOffset.x + nextMidpoint.x - pinchStartMidpoint.x;
      offsetY.value = pinchStartOffset.y + nextMidpoint.y - pinchStartMidpoint.y;
      clampOffsets();
      return;
    }

    if (scale.value > MIN_SCALE && panStart) {
      offsetX.value = panStartOffset.x + event.clientX - panStart.x;
      offsetY.value = panStartOffset.y + event.clientY - panStart.y;
      clampOffsets();
    }
  }

  function onPointerEnd(event: PointerEvent): void {
    const start = gestureStart;
    const wasSinglePointer = points.size === 1;
    points.delete(event.pointerId);

    if (wasSinglePointer && scale.value === MIN_SCALE && start) {
      const dx = event.clientX - start.x;
      const dy = event.clientY - start.y;
      if (Math.abs(dx) >= SWIPE_THRESHOLD_PX && Math.abs(dx) >= Math.abs(dy) * SWIPE_HORIZONTAL_DOMINANCE) {
        if (dx < 0 && options.canGoNext()) options.next();
        if (dx > 0 && options.canGoPrev()) options.previous();
      }
    }

    if (points.size === 1) {
      const remaining = [...points.values()][0];
      panStart = remaining;
      panStartOffset = { x: offsetX.value, y: offsetY.value };
    } else if (points.size === 0) {
      gestureStart = null;
      panStart = null;
      pinchStartDistance = 0;
      pinchStartMidpoint = null;
    }
  }

  function onResize(): void {
    clampOffsets();
  }

  watch(options.mediaId, reset);
  window.addEventListener('resize', onResize);
  onBeforeUnmount(() => window.removeEventListener('resize', onResize));

  return {
    isZoomed,
    scale,
    transformStyle,
    reset,
    onWheel,
    onDoubleClick,
    onPointerDown,
    onPointerMove,
    onPointerEnd,
  };
}

function distance(a: Point, b: Point): number {
  return Math.hypot(b.x - a.x, b.y - a.y);
}

function midpoint(a: Point, b: Point): Point {
  return { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 };
}

function isInteractiveTarget(target: EventTarget | null): boolean {
  return target instanceof Element && target.closest(INTERACTIVE_TARGET_SELECTOR) != null;
}

function isVideoControlsPointer(event: PointerEvent): boolean {
  if (!(event.target instanceof Element)) return false;
  const video = event.target.closest('video');
  if (!video) return false;
  const bounds = video.getBoundingClientRect();
  return bounds.height > 0 && event.clientY >= bounds.bottom - VIDEO_CONTROLS_EXCLUSION_HEIGHT_PX;
}
