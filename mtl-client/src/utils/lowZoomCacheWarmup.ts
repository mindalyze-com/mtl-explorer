import type { Map as MapLibreMap } from 'maplibre-gl';

const LOW_ZOOM_WARMUP_DELAY_MS = 3000;
const LOW_ZOOM_WARMUP_MAX_WAIT_MS = 15000;
const LOW_ZOOM_READINESS_CHECK_MS = 500;

type WarmupMap = Pick<MapLibreMap, 'on' | 'off' | 'loaded' | 'isMoving'>;

/** Prefer settled tiles after startup, but keep offline preparation bounded. */
export function scheduleLowZoomCacheWarmup(
  map: WarmupMap,
  start: () => void,
  isCurrent: () => boolean,
  isReady: () => boolean = () => true
): () => void {
  let pending = true;
  let delayElapsed = false;
  let deadlineElapsed = false;
  let readinessTimer: ReturnType<typeof setTimeout> | undefined;

  function cancel() {
    if (!pending) return;
    pending = false;
    clearTimeout(delayTimer);
    clearTimeout(deadlineTimer);
    clearTimeout(readinessTimer);
    map.off('idle', onIdle);
    map.off('remove', cancel);
  }

  function run() {
    if (!pending) return;
    cancel();
    if (isCurrent()) start();
  }

  function onIdle() {
    if (!pending || !delayElapsed) return;
    if (!isCurrent()) {
      cancel();
      return;
    }
    if (!isReady()) {
      clearTimeout(readinessTimer);
      readinessTimer = setTimeout(onIdle, LOW_ZOOM_READINESS_CHECK_MS);
      return;
    }
    if (deadlineElapsed || (map.loaded() && !map.isMoving())) run();
  }

  const delayTimer = setTimeout(() => {
    delayElapsed = true;
    onIdle();
  }, LOW_ZOOM_WARMUP_DELAY_MS);
  const deadlineTimer = setTimeout(() => {
    deadlineElapsed = true;
    onIdle();
  }, LOW_ZOOM_WARMUP_MAX_WAIT_MS);
  map.on('idle', onIdle);
  map.on('remove', cancel);
  return cancel;
}
