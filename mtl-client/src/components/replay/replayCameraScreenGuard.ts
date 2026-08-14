import type { ReplayCameraFrame } from '@/components/replay/trackReplayCamera';
import { sampleReplayPath, type ReplayPath } from '@/components/replay/trackReplayPath';
import { clamp01 as clampProgress, lerpNumber as lerp } from '@/utils/numbers';

type ScreenPoint = {
  x: number;
  y: number;
};

type LngLatLike = {
  lng: number;
  lat: number;
};

type ReplayCameraMapPadding = {
  top: number;
  right: number;
  bottom: number;
  left: number;
};

type ReplayCameraView = ReplayCameraFrame & {
  padding: ReplayCameraMapPadding;
};

type ReplayCameraScreenGuardMap = {
  getCanvas?: () => HTMLElement;
  getCenter?: () => LngLatLike;
  getZoom?: () => number;
  project: (lngLat: [number, number] | LngLatLike) => ScreenPoint;
  unproject: (point: [number, number]) => LngLatLike;
};

type ReplayCameraScreenGuardOptions = {
  map: ReplayCameraScreenGuardMap;
  path: ReplayPath;
  progress: number;
  frame: ReplayCameraFrame;
  padding: ReplayCameraMapPadding;
  applyFrame: (view: ReplayCameraView) => void;
};

type ScreenBounds = {
  minX: number;
  maxX: number;
  minY: number;
  maxY: number;
};

type VisibleScreenRect = {
  left: number;
  right: number;
  top: number;
  bottom: number;
  width: number;
  height: number;
};

type GuardNeed = {
  zoomOut: number;
  offsetLng: number;
  offsetLat: number;
};

const SCREEN_GUARD_SAMPLE_COUNT = 7;
const SCREEN_GUARD_BACK_PROGRESS = 0.018;
const SCREEN_GUARD_FORWARD_PROGRESS = 0.09;
const SCREEN_GUARD_MIN_CORRECTION_PX = 2;
const SCREEN_GUARD_ZOOM_OUT_BUFFER = 0.12;
const SCREEN_GUARD_MIN_ZOOM = 2;
const SCREEN_GUARD_MAX_ZOOM_OUT = 4;

// Easing factors applied to the persistent residual correction. Corrections are
// "tightened" quickly (so the live head point never slips out of the visible map)
// but "released" slowly (so the camera does not pump/oscillate when the route opens
// back up). Reacting per-frame with a snap — as the previous implementation did —
// turned terrain-elevation projection noise into visible camera jitter.
const ZOOM_TIGHTEN_EASE = 0.32;
const ZOOM_RELEASE_EASE = 0.05;
const CENTER_TIGHTEN_EASE = 0.4;
const CENTER_RELEASE_EASE = 0.12;

/**
 * Stateful, temporally-smoothed safety guard that keeps the live replay head point
 * inside the visible (non-occluded) part of the map.
 *
 * The planned camera rail is already smooth; this guard only contributes the small
 * residual needed to bridge the gap between the planner's flat approximation and the
 * real pitched + terrain projection. That residual is low-pass filtered across frames
 * so the guard never injects high-frequency jitter into the otherwise smooth rail.
 */
export class ReplayCameraScreenGuard {
  private zoomOut = 0;
  private offsetLng = 0;
  private offsetLat = 0;
  private primed = false;

  /** Drop the eased residual so the next applied frame snaps to its target. */
  reset(): void {
    this.zoomOut = 0;
    this.offsetLng = 0;
    this.offsetLat = 0;
    this.primed = false;
  }

  apply(options: ReplayCameraScreenGuardOptions): ReplayCameraFrame {
    const { map, path, progress, frame, padding, applyFrame } = options;
    const safeProgress = clampProgress(progress);

    // Measure the correction the *plan* frame would need, on its own terms. Applying
    // the uncorrected plan frame first gives a stable reference projection so the
    // measured need varies smoothly instead of feeding back on our own correction.
    applyFrame({ ...frame, padding });
    const need = measureGuardNeed(map, path, safeProgress, frame, padding);

    if (!this.primed) {
      this.zoomOut = need.zoomOut;
      this.offsetLng = need.offsetLng;
      this.offsetLat = need.offsetLat;
      this.primed = true;
    } else {
      this.zoomOut = easeResidual(this.zoomOut, need.zoomOut, ZOOM_TIGHTEN_EASE, ZOOM_RELEASE_EASE);
      this.offsetLng = easeResidual(this.offsetLng, need.offsetLng, CENTER_TIGHTEN_EASE, CENTER_RELEASE_EASE);
      this.offsetLat = easeResidual(this.offsetLat, need.offsetLat, CENTER_TIGHTEN_EASE, CENTER_RELEASE_EASE);
    }

    const committed: ReplayCameraView = {
      ...frame,
      center: [frame.center[0] + this.offsetLng, frame.center[1] + this.offsetLat],
      zoom: Math.max(SCREEN_GUARD_MIN_ZOOM, frame.zoom - this.zoomOut),
      padding,
    };
    applyFrame(committed);
    return committed;
  }
}

/**
 * One-shot guard used by callers (and tests) that just want the corrected frame
 * applied immediately without temporal smoothing.
 */
export function applyReplayCameraFrameWithScreenGuard(options: ReplayCameraScreenGuardOptions): ReplayCameraFrame {
  return new ReplayCameraScreenGuard().apply(options);
}

function measureGuardNeed(
  map: ReplayCameraScreenGuardMap,
  path: ReplayPath,
  progress: number,
  frame: ReplayCameraFrame,
  padding: ReplayCameraMapPadding
): GuardNeed {
  const empty: GuardNeed = { zoomOut: 0, offsetLng: 0, offsetLat: 0 };
  const visibleRect = replayVisibleScreenRect(map, padding);
  if (!visibleRect) return empty;

  const bounds = projectedReplayBounds(map, path, progress);
  if (!bounds) return empty;

  const widthScale = (bounds.maxX - bounds.minX) / visibleRect.width;
  const heightScale = (bounds.maxY - bounds.minY) / visibleRect.height;
  const scale = Math.max(widthScale, heightScale);
  if (scale > 1) {
    return {
      zoomOut: Math.min(SCREEN_GUARD_MAX_ZOOM_OUT, Math.log2(scale) + SCREEN_GUARD_ZOOM_OUT_BUFFER),
      offsetLng: 0,
      offsetLat: 0,
    };
  }

  const shiftX = screenContentShift(bounds.minX, bounds.maxX, visibleRect.left, visibleRect.right);
  const shiftY = screenContentShift(bounds.minY, bounds.maxY, visibleRect.top, visibleRect.bottom);
  if (Math.hypot(shiftX, shiftY) < SCREEN_GUARD_MIN_CORRECTION_PX) return empty;

  const frameCenter: LngLatLike = { lng: frame.center[0], lat: frame.center[1] };
  const centerScreen = map.project(frameCenter);
  const nextCenter = map.unproject([centerScreen.x - shiftX, centerScreen.y - shiftY]);
  return {
    zoomOut: 0,
    offsetLng: nextCenter.lng - frame.center[0],
    offsetLat: nextCenter.lat - frame.center[1],
  };
}

function replayVisibleScreenRect(
  map: ReplayCameraScreenGuardMap,
  padding: ReplayCameraMapPadding
): VisibleScreenRect | null {
  const canvas = map.getCanvas?.();
  if (!canvas) return null;
  const width = Number(canvas.clientWidth || canvas.getBoundingClientRect().width);
  const height = Number(canvas.clientHeight || canvas.getBoundingClientRect().height);
  if (!Number.isFinite(width) || !Number.isFinite(height) || width <= 0 || height <= 0) return null;

  const left = clamp(Number(padding.left), 0, width - 1);
  const right = clamp(width - Number(padding.right), left + 1, width);
  const top = clamp(Number(padding.top), 0, height - 1);
  const bottom = clamp(height - Number(padding.bottom), top + 1, height);
  return {
    left,
    right,
    top,
    bottom,
    width: right - left,
    height: bottom - top,
  };
}

function projectedReplayBounds(
  map: ReplayCameraScreenGuardMap,
  path: ReplayPath,
  progress: number
): ScreenBounds | null {
  const bounds: ScreenBounds = {
    minX: Number.POSITIVE_INFINITY,
    maxX: Number.NEGATIVE_INFINITY,
    minY: Number.POSITIVE_INFINITY,
    maxY: Number.NEGATIVE_INFINITY,
  };
  let pointCount = 0;
  const start = Math.max(0, progress - SCREEN_GUARD_BACK_PROGRESS);
  const end = Math.min(1, progress + SCREEN_GUARD_FORWARD_PROGRESS);

  for (let index = 0; index < SCREEN_GUARD_SAMPLE_COUNT; index += 1) {
    const t = SCREEN_GUARD_SAMPLE_COUNT <= 1 ? 0 : index / (SCREEN_GUARD_SAMPLE_COUNT - 1);
    const sample = sampleReplayPath(path, lerp(start, end, t));
    if (!sample) continue;
    const point = map.project([sample.lng, sample.lat]);
    if (!Number.isFinite(point.x) || !Number.isFinite(point.y)) continue;
    bounds.minX = Math.min(bounds.minX, point.x);
    bounds.maxX = Math.max(bounds.maxX, point.x);
    bounds.minY = Math.min(bounds.minY, point.y);
    bounds.maxY = Math.max(bounds.maxY, point.y);
    pointCount += 1;
  }

  return pointCount > 0 ? bounds : null;
}

export function screenContentShift(
  boundsMin: number,
  boundsMax: number,
  visibleMin: number,
  visibleMax: number
): number {
  if (boundsMin < visibleMin) return visibleMin - boundsMin;
  if (boundsMax > visibleMax) return visibleMax - boundsMax;
  return 0;
}

function easeResidual(current: number, target: number, tightenEase: number, releaseEase: number): number {
  const ease = Math.abs(target) > Math.abs(current) ? tightenEase : releaseEase;
  return current + (target - current) * ease;
}

function clamp(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, value));
}
