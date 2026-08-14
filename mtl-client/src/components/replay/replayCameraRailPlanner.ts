import {
  sampleReplayPath,
  sanitizeReplayTargetDuration,
  type ReplayCameraPresetId,
  type ReplayPath,
  type ReplayPathSample,
} from '@/components/replay/trackReplayPath';
import {
  replayCameraPreset,
  replayCameraSmoothnessWeight,
  type ReplayCameraFrame,
  type ReplayCameraPreset,
} from '@/components/replay/trackReplayCamera';
import { shortestLongitudeDelta, unwrapLongitudeToReference } from '@/components/map/mapGeometry';
import { upperBoundClampedIndex } from '@/utils/sortedSearch';
import { screenContentShift } from '@/components/replay/replayCameraScreenGuard';

export type { ReplayCameraFrame } from '@/components/replay/trackReplayCamera';

export type ReplayCameraPlanOptions = {
  path: ReplayPath;
  durationSeconds: number;
  preset: ReplayCameraPresetId;
  smoothness: number;
  viewport?: ReplayCameraViewport;
};

export type ReplayCameraViewport = {
  width: number;
  height: number;
  paddingTop?: number;
  paddingRight?: number;
  paddingBottom?: number;
  paddingLeft?: number;
};

export type ReplayCameraRail = {
  durationSeconds: number;
  keyframeCount: number;
  sample(elapsedSeconds: number): ReplayCameraFrame | null;
};

export type CameraTargetKeyframe = ReplayCameraFrame & {
  timeSeconds: number;
};

type LocalPoint = {
  x: number;
  y: number;
};

type LocalCameraKeyframe = LocalPoint & {
  timeSeconds: number;
  bearingDegrees: number;
  pitch: number;
  zoom: number;
};

type CubicCameraField = keyof Pick<LocalCameraKeyframe, 'x' | 'y' | 'bearingDegrees' | 'pitch' | 'zoom'>;
type MonotoneCameraField = keyof Pick<LocalCameraKeyframe, 'pitch' | 'zoom'>;

type ReplayCameraLocalProjection = {
  bounds: {
    minX: number;
    maxX: number;
    minY: number;
    maxY: number;
  };
  latitudeScale: number;
  toLocal(sample: Pick<ReplayPathSample, 'lng' | 'lat'>): LocalPoint;
  toLngLat(point: LocalPoint): [number, number];
};

type ReplayCameraResolvedStyle = {
  preset: ReplayCameraPreset;
  pitch: number;
  zoom: number;
  centerLead: number;
  directionBackProgress: number;
  directionForwardProgress: number;
  targetWindowProgress: number;
  curvatureWindowProgress: number;
  curvatureZoomOut: number;
  routeFitZoom: number | null;
  sampleStepSeconds: number;
  smoothingRadiusSeconds: number;
  smoothingBlend: number;
  smoothingPasses: number;
  scalarSmoothingRadiusSeconds: number;
  scalarSmoothingBlend: number;
  scalarSmoothingPasses: number;
  maxZoomRatePerSecond: number;
  maxPitchRatePerSecond: number;
  maxPanRateMetersPerSecond: number;
  maxPanAccelerationMetersPerSecondSquared: number;
  maxBearingRateDegreesPerSecond: number;
  maxBearingAccelerationDegreesPerSecondSquared: number;
  windowSampleCount: number;
  visibilityBackProgress: number;
  visibilityForwardProgress: number;
  visibilitySampleCount: number;
  visibilityScreenMarginPx: number;
  visibilityZoomPadding: number;
  visibilityRelaxationPasses: number;
  visibilityCenterRelaxation: number;
  visibilityZoomRelaxation: number;
  visibilityPitchRelaxation: number;
  visibilityRouteCentering: number;
  visibilityRouteZoomOutCap: number;
  viewport: ResolvedReplayViewport | null;
};

type ReplayCameraStyleRange = {
  shortPitch: number;
  shortZoomOut: number;
  shortCenterLead: number;
  longCenterLead: number;
  shortDirectionBackProgress: number;
  longDirectionBackProgress: number;
  shortDirectionForwardProgress: number;
  longDirectionForwardProgress: number;
  shortTargetWindowProgress: number;
  longTargetWindowProgress: number;
  shortCurvatureWindowProgress: number;
  longCurvatureWindowProgress: number;
  shortCurvatureZoomOut: number;
  longCurvatureZoomOut: number;
  shortSmoothingRadiusSeconds: number;
  longSmoothingRadiusSeconds: number;
  minSmoothingScale: number;
  maxSmoothingScale: number;
  minSmoothingBlend: number;
  maxSmoothingBlend: number;
  windowSampleCount: number;
};

type RawTargetBuildOptions = ReplayCameraPlanOptions & {
  durationSeconds: number;
  projection: ReplayCameraLocalProjection;
  style: ReplayCameraResolvedStyle;
};

type LocalBounds = {
  minX: number;
  maxX: number;
  minY: number;
  maxY: number;
};

type ResolvedReplayViewport = {
  width: number;
  height: number;
  paddingTop: number;
  paddingRight: number;
  paddingBottom: number;
  paddingLeft: number;
  visibleWidth: number;
  visibleHeight: number;
};

type PlannerVisibleScreenRect = {
  left: number;
  right: number;
  top: number;
  bottom: number;
  width: number;
  height: number;
  centerX: number;
  centerY: number;
};

type PlannerScreenBounds = {
  minX: number;
  maxX: number;
  minY: number;
  maxY: number;
};

type PlannerVisibilitySample = {
  point: LocalPoint;
  live: boolean;
};

type PlannerVisibilityAdjustment = {
  zoomOut: number;
  pitchReduction: number;
  centerShift: LocalPoint;
};

type ReplayCameraPlanScore = {
  score: number;
  liveViolations: number;
  maxLiveOverflowPx: number;
  routeOverflowPx: number;
  minZoom: number;
  averageZoom: number;
  maxZoomDelta: number;
};

type ReplayCameraPlanIterationDiagnostic = ReplayCameraPlanScore & {
  stage: string;
  iteration: number;
};

type ReplayCameraPlanDiagnostics = {
  startedAtMs: number;
  durationSeconds: number;
  preset: ReplayCameraPresetId;
  smoothness: number;
  viewport: ResolvedReplayViewport | null;
  iterations: ReplayCameraPlanIterationDiagnostic[];
};

const EARTH_RADIUS_METERS = 6_371_000;
const WEB_MERCATOR_WORLD_CIRCUMFERENCE_METERS = 40_075_016.686;
const MAPLIBRE_TILE_SIZE_PIXELS = 512;
const MIN_CAMERA_KEYFRAMES = 2;
const MIN_DIRECTION_DISTANCE_METERS = 8;
const MIN_INTERPOLATION_SPAN_SECONDS = 0.001;
const MIN_LATITUDE_SCALE = 0.01;
const MIN_ROUTE_FIT_METERS_PER_PIXEL = 0.05;
const REPLAY_DURATION_SHORT_SECONDS = 15;
const REPLAY_DURATION_LONG_SECONDS = 90;
const SHORT_REPLAY_SAMPLE_STEP_SECONDS = 0.25;
const MEDIUM_REPLAY_SAMPLE_STEP_SECONDS = 0.35;
const LONG_REPLAY_SAMPLE_STEP_SECONDS = 0.5;
const MEDIUM_REPLAY_MAX_SECONDS = 45;
const SHORT_REPLAY_MAX_SECONDS = 20;
const MIN_CAMERA_STYLE_ZOOM = 10.5;
const MIN_CAMERA_VISIBILITY_ZOOM = 6.6;
const MAX_CAMERA_ZOOM = 16.5;
const MIN_CAMERA_PITCH = 35;
const MAX_CAMERA_PITCH = 68;
const MOBILE_VIEWPORT_WIDTH = 520;
const SHORT_VIEWPORT_HEIGHT = 560;
const MIN_VISIBLE_VIEWPORT_WIDTH = 180;
const MIN_VISIBLE_VIEWPORT_HEIGHT = 160;
const MAX_OCCLUSION_ZOOM_OUT = 1.35;
const OCCLUSION_ZOOM_OUT_RESPONSE = 0.9;
const MAX_OCCLUSION_PITCH_REDUCTION = 18;
const PITCH_OCCLUSION_START_RATIO = 0.18;
const PITCH_OCCLUSION_FULL_RATIO = 0.58;
const ROUTE_WINDOW_BASE_SAMPLE_WEIGHT = 1;
const ROUTE_WINDOW_FUTURE_SAMPLE_WEIGHT = 1;
const CURVATURE_FULL_AVERAGE_HEADING_DELTA_DEGREES = 90;
const SHORT_ROUTE_DISTANCE_METERS = 2_000;
const MEDIUM_ROUTE_DISTANCE_METERS = 8_000;
const LONG_ROUTE_DISTANCE_METERS = 25_000;
const MIN_SMOOTHNESS_WINDOW_SCALE = 0.45;
const MAX_SMOOTHNESS_WINDOW_SCALE = 2.4;
const MIN_SMOOTHNESS_DIRECTION_SCALE = 0.55;
const MAX_SMOOTHNESS_DIRECTION_SCALE = 4.2;
const MIN_SMOOTHING_PASSES = 1;
const MAX_SMOOTHING_PASSES = 8;
const MAX_TARGET_WINDOW_PROGRESS = 0.68;
const MAX_DIRECTION_WINDOW_PROGRESS = 0.72;
const MAX_CURVATURE_WINDOW_PROGRESS = 0.68;
const MAX_SMOOTHNESS_CENTER_LEAD_BOOST = 0.1;
const MAX_CENTER_LEAD = 0.75;
const MIN_SCALAR_SMOOTHING_RADIUS_SCALE = 1.8;
const MAX_SCALAR_SMOOTHING_RADIUS_SCALE = 4.4;
const MIN_SCALAR_SMOOTHING_BLEND = 0.72;
const MAX_SCALAR_SMOOTHING_BLEND = 0.995;
const MIN_SCALAR_SMOOTHING_PASSES = 3;
const MAX_SCALAR_SMOOTHING_PASSES = 14;
const FAST_ZOOM_RATE_PER_SECOND = 0.24;
const SLOW_ZOOM_RATE_PER_SECOND = 0.025;
const FAST_PITCH_RATE_PER_SECOND = 5;
const SLOW_PITCH_RATE_PER_SECOND = 0.45;
const MIN_PAN_RATE_METERS_PER_SECOND = 50;
const FAST_PAN_RATE_MULTIPLIER = 3.2;
const SLOW_PAN_RATE_MULTIPLIER = 1.35;
const MIN_PAN_ACCELERATION_METERS_PER_SECOND_SQUARED = 80;
const FAST_PAN_ACCELERATION_MULTIPLIER = 8;
const SLOW_PAN_ACCELERATION_MULTIPLIER = 2.4;
const FAST_BEARING_RATE_DEGREES_PER_SECOND = 120;
const SLOW_BEARING_RATE_DEGREES_PER_SECOND = 22;
const FAST_BEARING_ACCELERATION_DEGREES_PER_SECOND_SQUARED = 140;
const SLOW_BEARING_ACCELERATION_DEGREES_PER_SECOND_SQUARED = 18;
const ENDPOINT_SMOOTHING_BLEND_FLOOR = 0.65;
const VISIBILITY_MIN_RELAXATION_PASSES = 3;
const VISIBILITY_MAX_RELAXATION_PASSES = 8;
const VISIBILITY_MIN_SCREEN_MARGIN_PX = 14;
const VISIBILITY_MAX_SCREEN_MARGIN_PX = 74;
const VISIBILITY_MIN_ZOOM_PADDING = 0.08;
const VISIBILITY_MAX_ZOOM_PADDING = 0.34;
const VISIBILITY_MIN_SAMPLE_COUNT = 7;
const VISIBILITY_MAX_SAMPLE_COUNT = 15;
const VISIBILITY_MAX_FORWARD_PROGRESS = 0.86;
const VISIBILITY_MAX_BACK_PROGRESS = 0.42;
const VISIBILITY_FAST_REPLAY_METERS_PER_SECOND = 1_400;
const VISIBILITY_EXTREME_REPLAY_METERS_PER_SECOND = 4_000;
const VISIBILITY_MIN_WINDOW_SCALE = 0.82;
const VISIBILITY_MAX_WINDOW_SCALE = 1.18;
const VISIBILITY_MAX_SPEED_WINDOW_BOOST = 1.22;
const VISIBILITY_MIN_SOFT_ROUTE_ZOOM_OUT = 0.18;
const VISIBILITY_MAX_SOFT_ROUTE_ZOOM_OUT = 1.75;
const VISIBILITY_MAX_SPEED_ROUTE_ZOOM_CAP_SCALE = 0.78;
const VISIBILITY_LOW_SMOOTHNESS_CENTER_RELAXATION = 0.9;
const VISIBILITY_HIGH_SMOOTHNESS_CENTER_RELAXATION = 0.45;
const VISIBILITY_LOW_SMOOTHNESS_ZOOM_RELAXATION = 0.58;
const VISIBILITY_HIGH_SMOOTHNESS_ZOOM_RELAXATION = 1;
const VISIBILITY_LOW_SMOOTHNESS_PITCH_RELAXATION = 0.35;
const VISIBILITY_HIGH_SMOOTHNESS_PITCH_RELAXATION = 1;
const VISIBILITY_LOW_SMOOTHNESS_ROUTE_CENTERING = 0.82;
const VISIBILITY_HIGH_SMOOTHNESS_ROUTE_CENTERING = 0.5;
const VISIBILITY_MIN_PITCH_VERTICAL_SCALE = 0.62;
const VISIBILITY_MAX_PITCH_REDUCTION = 14;
const VISIBILITY_ZOOM_SCALE_EPSILON = 0.02;
const VISIBILITY_SMOOTHING_RADIUS_SCALE = 0.55;
const VISIBILITY_SMOOTHING_MAX_BLEND = 0.72;
const VISIBILITY_FINAL_SCREEN_MARGIN_PX = 2;
const VISIBILITY_FINAL_VERIFY_PASSES = 3;
const VISIBILITY_FINAL_VERIFY_OVERSAMPLE = 3;
const VISIBILITY_FINAL_VERIFY_MAX_SAMPLES = 420;
const VISIBILITY_FINAL_VERIFY_FEATHER_WEIGHTS = [1, 0.72, 0.36, 0.12] as const;
const PLAN_DIAGNOSTIC_MAX_SAMPLES = 240;
const PLAN_DIAGNOSTIC_MIN_SAMPLES = 24;
const PLAN_SCORE_LIVE_VIOLATION_WEIGHT = 10_000;
const PLAN_SCORE_LIVE_OVERFLOW_WEIGHT = 100;
const PLAN_SCORE_ROUTE_OVERFLOW_WEIGHT = 1;
const PLAN_SCORE_ZOOM_DELTA_WEIGHT = 25;
const ROUTE_FIT_SCREEN_RATIOS: Record<ReplayCameraPresetId, number> = {
  chase: 0.84,
  follow: 0.78,
  overview: 0.68,
};

const CHASE_STYLE_RANGE: ReplayCameraStyleRange = {
  shortPitch: 57,
  shortZoomOut: 0.45,
  shortCenterLead: 0.18,
  longCenterLead: 0.06,
  shortDirectionBackProgress: 0.045,
  longDirectionBackProgress: 0.012,
  shortDirectionForwardProgress: 0.2,
  longDirectionForwardProgress: 0.035,
  shortTargetWindowProgress: 0.12,
  longTargetWindowProgress: 0.018,
  shortCurvatureWindowProgress: 0.18,
  longCurvatureWindowProgress: 0.04,
  shortCurvatureZoomOut: 0.7,
  longCurvatureZoomOut: 0.35,
  shortSmoothingRadiusSeconds: 2.3,
  longSmoothingRadiusSeconds: 0.8,
  minSmoothingScale: 0.2,
  maxSmoothingScale: 6.4,
  minSmoothingBlend: 0.04,
  maxSmoothingBlend: 0.98,
  windowSampleCount: 7,
};

const OVERVIEW_STYLE_RANGE: ReplayCameraStyleRange = {
  shortPitch: 45,
  shortZoomOut: 0.85,
  shortCenterLead: 0.62,
  longCenterLead: 0.42,
  shortDirectionBackProgress: 0.12,
  longDirectionBackProgress: 0.045,
  shortDirectionForwardProgress: 0.38,
  longDirectionForwardProgress: 0.16,
  shortTargetWindowProgress: 0.34,
  longTargetWindowProgress: 0.12,
  shortCurvatureWindowProgress: 0.36,
  longCurvatureWindowProgress: 0.16,
  shortCurvatureZoomOut: 0.9,
  longCurvatureZoomOut: 0.55,
  shortSmoothingRadiusSeconds: 3.4,
  longSmoothingRadiusSeconds: 1.5,
  minSmoothingScale: 0.25,
  maxSmoothingScale: 5.8,
  minSmoothingBlend: 0.08,
  maxSmoothingBlend: 0.99,
  windowSampleCount: 9,
};

const FOLLOW_STYLE_RANGE: ReplayCameraStyleRange = {
  shortPitch: 49,
  shortZoomOut: 0.6,
  shortCenterLead: 0.3,
  longCenterLead: 0.09,
  shortDirectionBackProgress: 0.07,
  longDirectionBackProgress: 0.018,
  shortDirectionForwardProgress: 0.26,
  longDirectionForwardProgress: 0.055,
  shortTargetWindowProgress: 0.2,
  longTargetWindowProgress: 0.035,
  shortCurvatureWindowProgress: 0.24,
  longCurvatureWindowProgress: 0.07,
  shortCurvatureZoomOut: 0.8,
  longCurvatureZoomOut: 0.45,
  shortSmoothingRadiusSeconds: 2.8,
  longSmoothingRadiusSeconds: 1,
  minSmoothingScale: 0.22,
  maxSmoothingScale: 6.8,
  minSmoothingBlend: 0.05,
  maxSmoothingBlend: 0.985,
  windowSampleCount: 8,
};

const RAIL_STYLE_RANGES: Record<ReplayCameraPresetId, ReplayCameraStyleRange> = {
  chase: CHASE_STYLE_RANGE,
  overview: OVERVIEW_STYLE_RANGE,
  follow: FOLLOW_STYLE_RANGE,
};

export class ReplayCameraRailPlanner {
  static build(options: ReplayCameraPlanOptions): ReplayCameraRail | null {
    const startedAtMs = plannerNowMs();
    const durationSeconds = sanitizeReplayTargetDuration(options.durationSeconds);
    const projection = buildLocalProjection(options.path);
    if (!projection) return null;

    const style = resolveReplayCameraStyle({ ...options, durationSeconds });
    const diagnostics = createReplayCameraPlanDiagnostics(options, durationSeconds, style.viewport, startedAtMs);
    const rawKeyframes = buildRawLocalTargetKeyframes({
      ...options,
      durationSeconds,
      projection,
      style,
    });
    if (rawKeyframes.length < MIN_CAMERA_KEYFRAMES) return null;
    recordReplayCameraPlanIteration(diagnostics, 'initial', rawKeyframes, {
      ...options,
      durationSeconds,
      projection,
      style,
    });

    const plannedKeyframes = planVisibilityAwareCameraKeyframes(
      rawKeyframes,
      {
        ...options,
        durationSeconds,
        projection,
        style,
      },
      diagnostics
    );
    const smoothedKeyframes = smoothLocalCameraKeyframes(plannedKeyframes, style);
    recordReplayCameraPlanIteration(diagnostics, 'smoothed', smoothedKeyframes, {
      ...options,
      durationSeconds,
      projection,
      style,
    });
    const keyframes = enforceFinalReplayHeadVisibility(
      smoothedKeyframes,
      {
        ...options,
        durationSeconds,
        projection,
        style,
      },
      diagnostics
    );
    recordReplayCameraPlanIteration(diagnostics, 'final', keyframes, {
      ...options,
      durationSeconds,
      projection,
      style,
    });
    emitReplayCameraPlanDiagnostics(diagnostics, keyframes);
    return new PlannedReplayCameraRail(durationSeconds, keyframes, projection);
  }
}

export function buildReplayCameraTargetKeyframes(options: ReplayCameraPlanOptions): CameraTargetKeyframe[] {
  const durationSeconds = sanitizeReplayTargetDuration(options.durationSeconds);
  const projection = buildLocalProjection(options.path);
  if (!projection) return [];

  const style = resolveReplayCameraStyle({ ...options, durationSeconds });
  const rawKeyframes = buildRawLocalTargetKeyframes({
    ...options,
    durationSeconds,
    projection,
    style,
  });
  return planVisibilityAwareCameraKeyframes(rawKeyframes, {
    ...options,
    durationSeconds,
    projection,
    style,
  }).map((keyframe) => localKeyframeToCameraTarget(keyframe, projection));
}

class PlannedReplayCameraRail implements ReplayCameraRail {
  readonly durationSeconds: number;
  readonly keyframeCount: number;
  private readonly keyframes: LocalCameraKeyframe[];
  private readonly projection: ReplayCameraLocalProjection;

  constructor(durationSeconds: number, keyframes: LocalCameraKeyframe[], projection: ReplayCameraLocalProjection) {
    this.durationSeconds = durationSeconds;
    this.keyframes = keyframes;
    this.keyframeCount = keyframes.length;
    this.projection = projection;
  }

  sample(elapsedSeconds: number): ReplayCameraFrame | null {
    const keyframe = sampleLocalCameraKeyframes(this.keyframes, this.durationSeconds, elapsedSeconds);
    return keyframe ? localKeyframeToCameraFrame(keyframe, this.projection) : null;
  }
}

function sampleLocalCameraKeyframes(
  keyframes: LocalCameraKeyframe[],
  durationSeconds: number,
  elapsedSeconds: number
): LocalCameraKeyframe | null {
  if (keyframes.length === 0) return null;
  const safeElapsedSeconds = clamp(Number.isFinite(elapsedSeconds) ? elapsedSeconds : 0, 0, durationSeconds);
  if (safeElapsedSeconds <= keyframes[0].timeSeconds) return keyframes[0];
  const last = keyframes[keyframes.length - 1];
  if (safeElapsedSeconds >= last.timeSeconds) return last;

  const nextIndex = firstKeyframeAfter(keyframes, safeElapsedSeconds);
  const currentIndex = Math.max(0, nextIndex - 1);
  const current = keyframes[currentIndex];
  const next = keyframes[nextIndex];
  const spanSeconds = Math.max(next.timeSeconds - current.timeSeconds, MIN_INTERPOLATION_SPAN_SECONDS);
  const t = clamp((safeElapsedSeconds - current.timeSeconds) / spanSeconds, 0, 1);
  const previous = keyframes[Math.max(0, currentIndex - 1)];
  const following = keyframes[Math.min(keyframes.length - 1, nextIndex + 1)];

  return {
    timeSeconds: safeElapsedSeconds,
    x: interpolateKeyframeCubic(previous, current, next, following, 'x', t, spanSeconds),
    y: interpolateKeyframeCubic(previous, current, next, following, 'y', t, spanSeconds),
    bearingDegrees: interpolateKeyframeCubic(previous, current, next, following, 'bearingDegrees', t, spanSeconds),
    pitch: interpolateKeyframeMonotoneScalar(previous, current, next, following, 'pitch', t, spanSeconds),
    zoom: interpolateKeyframeMonotoneScalar(previous, current, next, following, 'zoom', t, spanSeconds),
  };
}

function buildRawLocalTargetKeyframes(options: RawTargetBuildOptions): LocalCameraKeyframe[] {
  const { path, durationSeconds, projection, style } = options;
  if (path.points.length < MIN_CAMERA_KEYFRAMES || path.totalDistanceMeters <= 0) return [];

  const keyframes: LocalCameraKeyframe[] = [];
  const frameCount = Math.max(1, Math.ceil(durationSeconds / style.sampleStepSeconds));
  for (let index = 0; index <= frameCount; index += 1) {
    const timeSeconds = index === frameCount ? durationSeconds : index * style.sampleStepSeconds;
    const progress = durationSeconds > 0 ? clamp(timeSeconds / durationSeconds, 0, 1) : 0;
    const current = sampleReplayPath(path, progress);
    if (!current) continue;

    const currentLocal = projection.toLocal(current);
    const focus = averageRouteWindow(path, projection, progress, style.targetWindowProgress, style.windowSampleCount);
    const direction = stableRouteBearing(path, projection, current, progress, style);
    const curvatureScore = routeCurvatureScore(
      path,
      projection,
      progress,
      style.curvatureWindowProgress,
      style.windowSampleCount
    );
    const center = interpolatePoint(currentLocal, focus, style.centerLead);
    const baseZoom = clamp(
      Math.max(style.zoom - curvatureScore * style.curvatureZoomOut, style.routeFitZoom ?? MIN_CAMERA_STYLE_ZOOM),
      MIN_CAMERA_STYLE_ZOOM,
      MAX_CAMERA_ZOOM
    );
    const activeWindowFitZoom = resolveActiveRouteWindowFitZoom(path, projection, progress, style, baseZoom);
    const activeWindowZoom =
      activeWindowFitZoom == null
        ? baseZoom
        : Math.max(activeWindowFitZoom, baseZoom - style.visibilityRouteZoomOutCap);

    keyframes.push({
      timeSeconds,
      x: center.x,
      y: center.y,
      bearingDegrees: direction,
      pitch: style.pitch,
      zoom: clamp(Math.min(baseZoom, activeWindowZoom), MIN_CAMERA_VISIBILITY_ZOOM, MAX_CAMERA_ZOOM),
    });
  }

  return keyframes;
}

function resolveReplayCameraStyle(options: ReplayCameraPlanOptions): ReplayCameraResolvedStyle {
  const preset = replayCameraPreset(options.preset);
  const projection = buildLocalProjection(options.path);
  const viewport = resolveReplayViewport(options.viewport);
  const durationPosition = clamp(
    (options.durationSeconds - REPLAY_DURATION_SHORT_SECONDS) /
      (REPLAY_DURATION_LONG_SECONDS - REPLAY_DURATION_SHORT_SECONDS),
    0,
    1
  );
  const shortReplayWeight = 1 - durationPosition;
  const smoothnessWeight = replayCameraSmoothnessWeight(options.smoothness);
  const viewportZoomOut = resolveViewportZoomOut(viewport);
  const viewportPitchReduction = resolveViewportPitchReduction(viewport);
  const baseZoom = zoomForRailPreset(options.path.totalDistanceMeters, preset);
  const routeFitZoom = projection ? resolveRouteFitZoom(projection, viewport, preset.id, baseZoom) : null;
  const zoom = baseZoom - viewportZoomOut;
  const styleRange = RAIL_STYLE_RANGES[preset.id];
  const targetWindowScale = lerp(MIN_SMOOTHNESS_WINDOW_SCALE, MAX_SMOOTHNESS_WINDOW_SCALE, smoothnessWeight);
  const directionWindowScale = lerp(MIN_SMOOTHNESS_DIRECTION_SCALE, MAX_SMOOTHNESS_DIRECTION_SCALE, smoothnessWeight);
  const smoothingScale = lerp(styleRange.minSmoothingScale, styleRange.maxSmoothingScale, smoothnessWeight);
  const smoothingPasses = Math.round(lerp(MIN_SMOOTHING_PASSES, MAX_SMOOTHING_PASSES, smoothnessWeight));
  const routeMetersPerReplaySecond =
    options.durationSeconds > 0 ? options.path.totalDistanceMeters / options.durationSeconds : 0;
  const sampleStepSeconds =
    options.durationSeconds <= SHORT_REPLAY_MAX_SECONDS
      ? SHORT_REPLAY_SAMPLE_STEP_SECONDS
      : options.durationSeconds <= MEDIUM_REPLAY_MAX_SECONDS
        ? MEDIUM_REPLAY_SAMPLE_STEP_SECONDS
        : LONG_REPLAY_SAMPLE_STEP_SECONDS;
  const directionBackProgress = Math.min(
    lerp(styleRange.shortDirectionBackProgress, styleRange.longDirectionBackProgress, durationPosition) *
      directionWindowScale,
    MAX_DIRECTION_WINDOW_PROGRESS
  );
  const directionForwardProgress = Math.min(
    lerp(styleRange.shortDirectionForwardProgress, styleRange.longDirectionForwardProgress, durationPosition) *
      directionWindowScale,
    MAX_DIRECTION_WINDOW_PROGRESS
  );
  const targetWindowProgress = Math.min(
    lerp(styleRange.shortTargetWindowProgress, styleRange.longTargetWindowProgress, durationPosition) *
      targetWindowScale,
    MAX_TARGET_WINDOW_PROGRESS
  );
  const curvatureWindowProgress = Math.min(
    lerp(styleRange.shortCurvatureWindowProgress, styleRange.longCurvatureWindowProgress, durationPosition) *
      targetWindowScale,
    MAX_CURVATURE_WINDOW_PROGRESS
  );
  const replaySpeedWeight = clamp(
    (routeMetersPerReplaySecond - VISIBILITY_FAST_REPLAY_METERS_PER_SECOND) /
      (VISIBILITY_EXTREME_REPLAY_METERS_PER_SECOND - VISIBILITY_FAST_REPLAY_METERS_PER_SECOND),
    0,
    1
  );
  const visibilityWindowScale =
    lerp(VISIBILITY_MIN_WINDOW_SCALE, VISIBILITY_MAX_WINDOW_SCALE, smoothnessWeight) *
    lerp(1, VISIBILITY_MAX_SPEED_WINDOW_BOOST, replaySpeedWeight);
  const visibilityBackProgress = Math.min(
    Math.max(directionBackProgress, targetWindowProgress * 0.25) * visibilityWindowScale,
    VISIBILITY_MAX_BACK_PROGRESS
  );
  const visibilityForwardProgress = Math.min(
    Math.max(directionForwardProgress, targetWindowProgress, curvatureWindowProgress) * visibilityWindowScale,
    VISIBILITY_MAX_FORWARD_PROGRESS
  );

  return {
    preset,
    pitch: clamp(
      lerp(styleRange.shortPitch, preset.pitch, durationPosition) - viewportPitchReduction,
      MIN_CAMERA_PITCH,
      MAX_CAMERA_PITCH
    ),
    zoom: clamp(
      Math.max(zoom - shortReplayWeight * styleRange.shortZoomOut, routeFitZoom ?? MIN_CAMERA_STYLE_ZOOM),
      MIN_CAMERA_STYLE_ZOOM,
      MAX_CAMERA_ZOOM
    ),
    centerLead: Math.min(
      lerp(styleRange.shortCenterLead, styleRange.longCenterLead, durationPosition) +
        smoothnessWeight * MAX_SMOOTHNESS_CENTER_LEAD_BOOST,
      MAX_CENTER_LEAD
    ),
    directionBackProgress,
    directionForwardProgress,
    targetWindowProgress,
    curvatureWindowProgress,
    curvatureZoomOut: lerp(styleRange.shortCurvatureZoomOut, styleRange.longCurvatureZoomOut, durationPosition),
    routeFitZoom,
    sampleStepSeconds,
    smoothingRadiusSeconds: lerp(
      styleRange.shortSmoothingRadiusSeconds * smoothingScale,
      styleRange.longSmoothingRadiusSeconds * smoothingScale,
      durationPosition
    ),
    smoothingBlend: lerp(styleRange.minSmoothingBlend, styleRange.maxSmoothingBlend, smoothnessWeight),
    smoothingPasses,
    scalarSmoothingRadiusSeconds:
      lerp(styleRange.shortSmoothingRadiusSeconds, styleRange.longSmoothingRadiusSeconds, durationPosition) *
      smoothingScale *
      lerp(MIN_SCALAR_SMOOTHING_RADIUS_SCALE, MAX_SCALAR_SMOOTHING_RADIUS_SCALE, smoothnessWeight),
    scalarSmoothingBlend: lerp(MIN_SCALAR_SMOOTHING_BLEND, MAX_SCALAR_SMOOTHING_BLEND, smoothnessWeight),
    scalarSmoothingPasses: Math.round(lerp(MIN_SCALAR_SMOOTHING_PASSES, MAX_SCALAR_SMOOTHING_PASSES, smoothnessWeight)),
    maxZoomRatePerSecond: lerp(FAST_ZOOM_RATE_PER_SECOND, SLOW_ZOOM_RATE_PER_SECOND, smoothnessWeight),
    maxPitchRatePerSecond: lerp(FAST_PITCH_RATE_PER_SECOND, SLOW_PITCH_RATE_PER_SECOND, smoothnessWeight),
    maxPanRateMetersPerSecond: Math.max(
      MIN_PAN_RATE_METERS_PER_SECOND,
      routeMetersPerReplaySecond * lerp(FAST_PAN_RATE_MULTIPLIER, SLOW_PAN_RATE_MULTIPLIER, smoothnessWeight)
    ),
    maxPanAccelerationMetersPerSecondSquared: Math.max(
      MIN_PAN_ACCELERATION_METERS_PER_SECOND_SQUARED,
      routeMetersPerReplaySecond *
        lerp(FAST_PAN_ACCELERATION_MULTIPLIER, SLOW_PAN_ACCELERATION_MULTIPLIER, smoothnessWeight)
    ),
    maxBearingRateDegreesPerSecond: lerp(
      FAST_BEARING_RATE_DEGREES_PER_SECOND,
      SLOW_BEARING_RATE_DEGREES_PER_SECOND,
      smoothnessWeight
    ),
    maxBearingAccelerationDegreesPerSecondSquared: lerp(
      FAST_BEARING_ACCELERATION_DEGREES_PER_SECOND_SQUARED,
      SLOW_BEARING_ACCELERATION_DEGREES_PER_SECOND_SQUARED,
      smoothnessWeight
    ),
    windowSampleCount: styleRange.windowSampleCount,
    visibilityBackProgress,
    visibilityForwardProgress,
    visibilitySampleCount: Math.round(lerp(VISIBILITY_MIN_SAMPLE_COUNT, VISIBILITY_MAX_SAMPLE_COUNT, smoothnessWeight)),
    visibilityScreenMarginPx: lerp(VISIBILITY_MIN_SCREEN_MARGIN_PX, VISIBILITY_MAX_SCREEN_MARGIN_PX, smoothnessWeight),
    visibilityZoomPadding: lerp(VISIBILITY_MIN_ZOOM_PADDING, VISIBILITY_MAX_ZOOM_PADDING, smoothnessWeight),
    visibilityRelaxationPasses: Math.round(
      lerp(VISIBILITY_MIN_RELAXATION_PASSES, VISIBILITY_MAX_RELAXATION_PASSES, smoothnessWeight)
    ),
    visibilityCenterRelaxation: lerp(
      VISIBILITY_LOW_SMOOTHNESS_CENTER_RELAXATION,
      VISIBILITY_HIGH_SMOOTHNESS_CENTER_RELAXATION,
      smoothnessWeight
    ),
    visibilityZoomRelaxation: lerp(
      VISIBILITY_LOW_SMOOTHNESS_ZOOM_RELAXATION,
      VISIBILITY_HIGH_SMOOTHNESS_ZOOM_RELAXATION,
      smoothnessWeight
    ),
    visibilityPitchRelaxation: lerp(
      VISIBILITY_LOW_SMOOTHNESS_PITCH_RELAXATION,
      VISIBILITY_HIGH_SMOOTHNESS_PITCH_RELAXATION,
      smoothnessWeight
    ),
    visibilityRouteCentering: lerp(
      VISIBILITY_LOW_SMOOTHNESS_ROUTE_CENTERING,
      VISIBILITY_HIGH_SMOOTHNESS_ROUTE_CENTERING,
      smoothnessWeight
    ),
    visibilityRouteZoomOutCap:
      lerp(VISIBILITY_MIN_SOFT_ROUTE_ZOOM_OUT, VISIBILITY_MAX_SOFT_ROUTE_ZOOM_OUT, smoothnessWeight) *
      lerp(1, VISIBILITY_MAX_SPEED_ROUTE_ZOOM_CAP_SCALE, replaySpeedWeight),
    viewport,
  };
}

function planVisibilityAwareCameraKeyframes(
  rawKeyframes: LocalCameraKeyframe[],
  options: RawTargetBuildOptions,
  diagnostics?: ReplayCameraPlanDiagnostics
): LocalCameraKeyframe[] {
  const { style } = options;
  if (!style.viewport || rawKeyframes.length <= MIN_CAMERA_KEYFRAMES) {
    return rawKeyframes;
  }

  let keyframes = unwrapBearingKeyframes(rawKeyframes);
  for (let pass = 0; pass < style.visibilityRelaxationPasses; pass += 1) {
    keyframes = keyframes.map((keyframe) => relaxVisibilityForKeyframe(keyframe, options));
    if (pass < style.visibilityRelaxationPasses - 1) {
      keyframes = smoothVisibilityRelaxation(keyframes, style);
    }
    recordReplayCameraPlanIteration(diagnostics, `relax-${pass + 1}`, keyframes, options);
  }

  return keyframes;
}

// Deterministic plan-time safety pass: simulate the planned frame in the
// non-occluded screen rect, then spend the smoothness budget on zoom, pitch, and
// center relaxation before the final camera-rail smoothing runs.
function relaxVisibilityForKeyframe(
  keyframe: LocalCameraKeyframe,
  options: RawTargetBuildOptions
): LocalCameraKeyframe {
  const { path, durationSeconds, projection, style } = options;
  const viewport = style.viewport;
  if (!viewport) return keyframe;

  const progress = durationSeconds > 0 ? clamp(keyframe.timeSeconds / durationSeconds, 0, 1) : 0;
  const samples = replayVisibilitySamples(path, projection, progress, style);
  if (samples.length === 0) return keyframe;

  const visibleRect = plannerVisibleScreenRect(viewport, style.visibilityScreenMarginPx);
  if (!visibleRect) return keyframe;

  const adjustment = measurePlannerVisibilityAdjustment(keyframe, projection, viewport, visibleRect, samples, style);
  if (
    adjustment.zoomOut <= 0 &&
    adjustment.pitchReduction <= 0 &&
    distanceBetweenLocalPoints(adjustment.centerShift, { x: 0, y: 0 }) <= 0
  ) {
    return keyframe;
  }

  return {
    ...keyframe,
    x: keyframe.x + adjustment.centerShift.x * style.visibilityCenterRelaxation,
    y: keyframe.y + adjustment.centerShift.y * style.visibilityCenterRelaxation,
    pitch: clamp(
      keyframe.pitch - adjustment.pitchReduction * style.visibilityPitchRelaxation,
      MIN_CAMERA_PITCH,
      MAX_CAMERA_PITCH
    ),
    zoom: clamp(
      keyframe.zoom - adjustment.zoomOut * style.visibilityZoomRelaxation,
      MIN_CAMERA_VISIBILITY_ZOOM,
      MAX_CAMERA_ZOOM
    ),
  };
}

function replayVisibilitySamples(
  path: ReplayPath,
  projection: ReplayCameraLocalProjection,
  progress: number,
  style: ReplayCameraResolvedStyle
): PlannerVisibilitySample[] {
  const samples: PlannerVisibilitySample[] = [];
  const liveSample = sampleReplayPath(path, progress);
  if (liveSample) {
    samples.push({ point: projection.toLocal(liveSample), live: true });
  }

  const start = clamp(progress - style.visibilityBackProgress, 0, 1);
  const end = clamp(progress + style.visibilityForwardProgress, 0, 1);
  const count = Math.max(2, style.visibilitySampleCount);
  for (let index = 0; index < count; index += 1) {
    const t = count <= 1 ? 0 : index / (count - 1);
    const sample = sampleReplayPath(path, lerp(start, end, t));
    if (!sample) continue;
    samples.push({
      point: projection.toLocal(sample),
      live: false,
    });
  }

  return samples;
}

function measurePlannerVisibilityAdjustment(
  keyframe: LocalCameraKeyframe,
  projection: ReplayCameraLocalProjection,
  viewport: ResolvedReplayViewport,
  visibleRect: PlannerVisibleScreenRect,
  samples: PlannerVisibilitySample[],
  style: ReplayCameraResolvedStyle
): PlannerVisibilityAdjustment {
  const liveVisibleRect = plannerVisibleScreenRect(viewport, VISIBILITY_FINAL_SCREEN_MARGIN_PX) ?? visibleRect;
  const routeBounds = emptyPlannerScreenBounds();
  let liveScreenPoint: LocalPoint | null = null;
  let routePointCount = 0;

  for (const sample of samples) {
    const screenPoint = projectLocalPointToPlannerScreen(keyframe, sample.point, projection, viewport, visibleRect);
    if (!Number.isFinite(screenPoint.x) || !Number.isFinite(screenPoint.y)) continue;
    if (sample.live && !liveScreenPoint) {
      liveScreenPoint = screenPoint;
    } else if (!sample.live) {
      extendPlannerScreenBounds(routeBounds, screenPoint);
      routePointCount += 1;
    }
  }

  if (!liveScreenPoint && routePointCount === 0) {
    return emptyPlannerVisibilityAdjustment();
  }

  const liveFitScale = liveScreenPoint ? screenPointFitScale(liveScreenPoint, liveVisibleRect) : 1;
  const routeFitScale = routePointCount > 0 ? screenBoundsFitScale(routeBounds, visibleRect) : 1;
  const liveZoomOut =
    liveFitScale > 1 + VISIBILITY_ZOOM_SCALE_EPSILON ? Math.log2(liveFitScale) + style.visibilityZoomPadding : 0;
  const softRouteZoomOut =
    routeFitScale > 1 + VISIBILITY_ZOOM_SCALE_EPSILON
      ? Math.min(style.visibilityRouteZoomOutCap, Math.log2(routeFitScale) + style.visibilityZoomPadding * 0.5)
      : 0;
  const zoomOut = Math.min(keyframe.zoom - MIN_CAMERA_VISIBILITY_ZOOM, Math.max(liveZoomOut, softRouteZoomOut));
  const routeShiftScale = zoomOut > 0 ? 0 : style.visibilityRouteCentering;
  const routeShiftX =
    routePointCount > 0
      ? screenContentShift(routeBounds.minX, routeBounds.maxX, visibleRect.left, visibleRect.right) * routeShiftScale
      : 0;
  const routeShiftY =
    routePointCount > 0
      ? screenContentShift(routeBounds.minY, routeBounds.maxY, visibleRect.top, visibleRect.bottom) * routeShiftScale
      : 0;
  const liveShiftX = liveScreenPoint
    ? screenPointShift(liveScreenPoint.x, liveVisibleRect.left, liveVisibleRect.right)
    : 0;
  const liveShiftY = liveScreenPoint
    ? screenPointShift(liveScreenPoint.y, liveVisibleRect.top, liveVisibleRect.bottom)
    : 0;
  const shiftX = Math.abs(liveShiftX) > 0 ? liveShiftX : routeShiftX;
  const shiftY = Math.abs(liveShiftY) > 0 ? liveShiftY : routeShiftY;
  const centerShift =
    Math.hypot(shiftX, shiftY) > 0
      ? screenShiftToCameraCenterDelta(keyframe, projection, viewport, shiftX, shiftY)
      : { x: 0, y: 0 };
  const pitchReduction = Math.min(
    VISIBILITY_MAX_PITCH_REDUCTION,
    Math.max(0, zoomOut - style.visibilityZoomPadding * 0.5) * VISIBILITY_MAX_PITCH_REDUCTION
  );

  return { zoomOut, pitchReduction, centerShift };
}

function smoothVisibilityRelaxation(
  keyframes: LocalCameraKeyframe[],
  style: ReplayCameraResolvedStyle
): LocalCameraKeyframe[] {
  const stepSeconds = medianKeyframeStepSeconds(keyframes);
  const radiusFrames = Math.max(
    1,
    Math.round((style.smoothingRadiusSeconds * VISIBILITY_SMOOTHING_RADIUS_SCALE) / stepSeconds)
  );
  const blend = Math.min(style.smoothingBlend, VISIBILITY_SMOOTHING_MAX_BLEND);

  return keyframes.map((keyframe, index) => {
    const average = weightedKeyframeAverage(keyframes, index, radiusFrames);
    const edgeBlend = endpointSmoothingBlend(index, keyframes.length, radiusFrames);
    const visibilityBlend = blend * edgeBlend;
    return {
      timeSeconds: keyframe.timeSeconds,
      x: lerp(keyframe.x, average.x, visibilityBlend),
      y: lerp(keyframe.y, average.y, visibilityBlend),
      bearingDegrees: lerp(keyframe.bearingDegrees, average.bearingDegrees, visibilityBlend),
      pitch: lerp(keyframe.pitch, average.pitch, visibilityBlend),
      zoom: lerp(keyframe.zoom, average.zoom, visibilityBlend),
    };
  });
}

function enforceFinalReplayHeadVisibility(
  keyframes: LocalCameraKeyframe[],
  options: RawTargetBuildOptions,
  diagnostics?: ReplayCameraPlanDiagnostics
): LocalCameraKeyframe[] {
  const { path, durationSeconds, projection, style } = options;
  const viewport = style.viewport;
  if (!viewport || keyframes.length <= MIN_CAMERA_KEYFRAMES) return keyframes;

  const visibleRect = plannerVisibleScreenRect(viewport, VISIBILITY_FINAL_SCREEN_MARGIN_PX);
  if (!visibleRect) return keyframes;

  const sampleCount = Math.min(
    VISIBILITY_FINAL_VERIFY_MAX_SAMPLES,
    Math.max(keyframes.length * VISIBILITY_FINAL_VERIFY_OVERSAMPLE, keyframes.length)
  );
  let adjusted = keyframes.map((keyframe) => ({ ...keyframe }));

  for (let pass = 0; pass < VISIBILITY_FINAL_VERIFY_PASSES; pass += 1) {
    let changed = false;
    for (let index = 0; index <= sampleCount; index += 1) {
      const elapsedSeconds = (durationSeconds * index) / sampleCount;
      const progress = durationSeconds > 0 ? clamp(elapsedSeconds / durationSeconds, 0, 1) : 0;
      const frame = sampleLocalCameraKeyframes(adjusted, durationSeconds, elapsedSeconds);
      const replaySample = sampleReplayPath(path, progress);
      if (!frame || !replaySample) continue;

      const headPoint = projection.toLocal(replaySample);
      const screenPoint = projectLocalPointToPlannerScreen(frame, headPoint, projection, viewport, visibleRect);
      const liveShiftX = screenPointShift(screenPoint.x, visibleRect.left, visibleRect.right);
      const liveShiftY = screenPointShift(screenPoint.y, visibleRect.top, visibleRect.bottom);
      const liveFitScale = screenPointFitScale(screenPoint, visibleRect);
      if (
        Math.abs(liveShiftX) <= VISIBILITY_ZOOM_SCALE_EPSILON &&
        Math.abs(liveShiftY) <= VISIBILITY_ZOOM_SCALE_EPSILON &&
        liveFitScale <= 1 + VISIBILITY_ZOOM_SCALE_EPSILON
      ) {
        continue;
      }

      const centerShift =
        Math.hypot(liveShiftX, liveShiftY) > 0
          ? screenShiftToCameraCenterDelta(frame, projection, viewport, liveShiftX, liveShiftY)
          : { x: 0, y: 0 };

      adjusted = applyFinalVisibilityAdjustment(adjusted, elapsedSeconds, centerShift);
      changed = true;
    }

    recordReplayCameraPlanIteration(diagnostics, `verify-${pass + 1}`, adjusted, options);
    if (!changed) break;
  }

  return adjusted;
}

function applyFinalVisibilityAdjustment(
  keyframes: LocalCameraKeyframe[],
  elapsedSeconds: number,
  centerShift: LocalPoint
): LocalCameraKeyframe[] {
  const nextIndex = clamp(firstKeyframeAfter(keyframes, elapsedSeconds), 0, keyframes.length - 1);
  const currentIndex = clamp(nextIndex - 1, 0, keyframes.length - 1);
  const weights = new Map<number, number>();
  for (let offset = 0; offset < VISIBILITY_FINAL_VERIFY_FEATHER_WEIGHTS.length; offset += 1) {
    addFinalVisibilityFeatherWeight(weights, currentIndex - offset, offset, keyframes.length);
    addFinalVisibilityFeatherWeight(weights, nextIndex + offset, offset, keyframes.length);
  }

  return keyframes.map((keyframe, index) => {
    const weight = weights.get(index);
    if (!weight) return keyframe;
    return {
      ...keyframe,
      x: keyframe.x + centerShift.x * weight,
      y: keyframe.y + centerShift.y * weight,
    };
  });
}

function addFinalVisibilityFeatherWeight(
  weights: Map<number, number>,
  index: number,
  featherOffset: number,
  keyframeCount: number
): void {
  if (index < 0 || index >= keyframeCount) return;
  const weight = VISIBILITY_FINAL_VERIFY_FEATHER_WEIGHTS[featherOffset];
  weights.set(index, Math.max(weights.get(index) ?? 0, weight));
}

function createReplayCameraPlanDiagnostics(
  options: ReplayCameraPlanOptions,
  durationSeconds: number,
  viewport: ResolvedReplayViewport | null,
  startedAtMs: number
): ReplayCameraPlanDiagnostics | undefined {
  if (!viewport) return undefined;
  return {
    startedAtMs,
    durationSeconds,
    preset: replayCameraPreset(options.preset).id,
    smoothness: options.smoothness,
    viewport,
    iterations: [],
  };
}

function recordReplayCameraPlanIteration(
  diagnostics: ReplayCameraPlanDiagnostics | null | undefined,
  stage: string,
  keyframes: LocalCameraKeyframe[],
  options: RawTargetBuildOptions
): void {
  if (!diagnostics) return;
  diagnostics.iterations.push({
    stage,
    iteration: diagnostics.iterations.length,
    ...scoreReplayCameraPlan(keyframes, options),
  });
}

function scoreReplayCameraPlan(
  keyframes: LocalCameraKeyframe[],
  options: RawTargetBuildOptions
): ReplayCameraPlanScore {
  const { path, durationSeconds, projection, style } = options;
  const viewport = style.viewport;
  const emptyScore: ReplayCameraPlanScore = {
    score: 0,
    liveViolations: 0,
    maxLiveOverflowPx: 0,
    routeOverflowPx: 0,
    minZoom: 0,
    averageZoom: 0,
    maxZoomDelta: 0,
  };
  if (!viewport || keyframes.length === 0) return emptyScore;

  const liveRect = plannerVisibleScreenRect(viewport, VISIBILITY_FINAL_SCREEN_MARGIN_PX);
  const routeRect = plannerVisibleScreenRect(viewport, style.visibilityScreenMarginPx);
  if (!liveRect || !routeRect) return emptyScore;

  const sampleCount = Math.min(
    PLAN_DIAGNOSTIC_MAX_SAMPLES,
    Math.max(PLAN_DIAGNOSTIC_MIN_SAMPLES, keyframes.length * 2)
  );
  let liveViolations = 0;
  let maxLiveOverflowPx = 0;
  let routeOverflowPx = 0;
  let zoomSum = 0;
  let minZoom = Number.POSITIVE_INFINITY;
  let maxZoomDelta = 0;
  let previousZoom: number | null = null;
  let scoredSamples = 0;

  for (let index = 0; index <= sampleCount; index += 1) {
    const elapsedSeconds = (durationSeconds * index) / sampleCount;
    const progress = durationSeconds > 0 ? clamp(elapsedSeconds / durationSeconds, 0, 1) : 0;
    const frame = sampleLocalCameraKeyframes(keyframes, durationSeconds, elapsedSeconds);
    const liveSample = sampleReplayPath(path, progress);
    if (!frame || !liveSample) continue;

    const livePoint = projectLocalPointToPlannerScreen(
      frame,
      projection.toLocal(liveSample),
      projection,
      viewport,
      liveRect
    );
    const liveOverflowPx = screenPointOverflowDistance(livePoint, liveRect);
    if (liveOverflowPx > VISIBILITY_ZOOM_SCALE_EPSILON) liveViolations += 1;
    maxLiveOverflowPx = Math.max(maxLiveOverflowPx, liveOverflowPx);

    const routeBounds = emptyPlannerScreenBounds();
    let routePointCount = 0;
    for (const sample of replayVisibilitySamples(path, projection, progress, style)) {
      if (sample.live) continue;
      const screenPoint = projectLocalPointToPlannerScreen(frame, sample.point, projection, viewport, routeRect);
      if (!Number.isFinite(screenPoint.x) || !Number.isFinite(screenPoint.y)) continue;
      extendPlannerScreenBounds(routeBounds, screenPoint);
      routePointCount += 1;
    }
    if (routePointCount > 0) {
      routeOverflowPx += screenBoundsOverflowDistance(routeBounds, routeRect) / (sampleCount + 1);
    }

    zoomSum += frame.zoom;
    minZoom = Math.min(minZoom, frame.zoom);
    if (previousZoom != null) {
      maxZoomDelta = Math.max(maxZoomDelta, Math.abs(frame.zoom - previousZoom));
    }
    previousZoom = frame.zoom;
    scoredSamples += 1;
  }

  if (scoredSamples === 0) return emptyScore;
  const averageZoom = zoomSum / scoredSamples;
  const score =
    liveViolations * PLAN_SCORE_LIVE_VIOLATION_WEIGHT +
    maxLiveOverflowPx * PLAN_SCORE_LIVE_OVERFLOW_WEIGHT +
    routeOverflowPx * PLAN_SCORE_ROUTE_OVERFLOW_WEIGHT +
    maxZoomDelta * PLAN_SCORE_ZOOM_DELTA_WEIGHT;

  return {
    score,
    liveViolations,
    maxLiveOverflowPx,
    routeOverflowPx,
    minZoom,
    averageZoom,
    maxZoomDelta,
  };
}

function emitReplayCameraPlanDiagnostics(
  diagnostics: ReplayCameraPlanDiagnostics | undefined,
  finalKeyframes: LocalCameraKeyframe[]
): void {
  if (!diagnostics || !isReplayCameraBrowserConsoleAvailable()) return;
  const initial = diagnostics.iterations[0];
  const final = diagnostics.iterations[diagnostics.iterations.length - 1];
  if (!initial || !final) return;

  const elapsedMs = plannerNowMs() - diagnostics.startedAtMs;
  const relaxIterations = diagnostics.iterations.filter((iteration) => iteration.stage.startsWith('relax-'));
  const verifyIterations = diagnostics.iterations.filter((iteration) => iteration.stage.startsWith('verify-'));
  const relaxationTrail = [initial, ...relaxIterations]
    .map((iteration) => `${iteration.stage}:${formatPlanMetric(iteration.score)}`)
    .join(' -> ');
  const postTrail = diagnostics.iterations
    .filter((iteration) => !iteration.stage.startsWith('relax-') && iteration.stage !== 'initial')
    .map((iteration) => `${iteration.stage}:${formatPlanMetric(iteration.score)}`)
    .join(' -> ');
  const lastRelaxation = relaxIterations.length > 0 ? relaxIterations[relaxIterations.length - 1] : initial;
  const summary = `[MTL Explorer replay camera] planned ${diagnostics.preset} ${formatPlanMetric(
    diagnostics.durationSeconds,
    0
  )}s smooth=${formatPlanMetric(diagnostics.smoothness, 0)} keyframes=${
    finalKeyframes.length
  } relax=${relaxIterations.length} verify=${verifyIterations.length} trend=${scoreTrend([
    initial,
    ...relaxIterations,
  ])} time=${formatPlanMetric(elapsedMs)}ms score initial=${formatPlanMetric(
    initial.score
  )} relax=${formatPlanMetric(lastRelaxation.score)} final=${formatPlanMetric(final.score)} live=${
    final.liveViolations
  } relaxTrail ${relaxationTrail}; post ${postTrail}`;

  console.info(summary, {
    iterations: diagnostics.iterations.map((iteration) => ({
      iteration: iteration.iteration,
      stage: iteration.stage,
      score: roundPlanMetric(iteration.score),
      liveViolations: iteration.liveViolations,
      maxLiveOverflowPx: roundPlanMetric(iteration.maxLiveOverflowPx),
      routeOverflowPx: roundPlanMetric(iteration.routeOverflowPx),
      minZoom: roundPlanMetric(iteration.minZoom),
      averageZoom: roundPlanMetric(iteration.averageZoom),
      maxZoomDelta: roundPlanMetric(iteration.maxZoomDelta),
    })),
    viewport: {
      width: diagnostics.viewport?.width,
      height: diagnostics.viewport?.height,
      visibleWidth: diagnostics.viewport?.visibleWidth,
      visibleHeight: diagnostics.viewport?.visibleHeight,
      paddingTop: diagnostics.viewport?.paddingTop,
      paddingRight: diagnostics.viewport?.paddingRight,
      paddingBottom: diagnostics.viewport?.paddingBottom,
      paddingLeft: diagnostics.viewport?.paddingLeft,
    },
  });
}

function screenPointOverflowDistance(point: LocalPoint, visibleRect: PlannerVisibleScreenRect): number {
  return Math.hypot(
    screenPointShift(point.x, visibleRect.left, visibleRect.right),
    screenPointShift(point.y, visibleRect.top, visibleRect.bottom)
  );
}

function screenBoundsOverflowDistance(bounds: PlannerScreenBounds, visibleRect: PlannerVisibleScreenRect): number {
  return Math.hypot(
    screenContentShift(bounds.minX, bounds.maxX, visibleRect.left, visibleRect.right),
    screenContentShift(bounds.minY, bounds.maxY, visibleRect.top, visibleRect.bottom)
  );
}

function plannerNowMs(): number {
  return typeof performance !== 'undefined' && typeof performance.now === 'function' ? performance.now() : Date.now();
}

function isReplayCameraBrowserConsoleAvailable(): boolean {
  if (typeof window === 'undefined' || typeof console === 'undefined' || typeof console.info !== 'function') {
    return false;
  }
  const userAgent = typeof navigator === 'undefined' ? '' : navigator.userAgent;
  return !/jsdom/i.test(userAgent);
}

function formatPlanMetric(value: number, fractionDigits = 1): string {
  return roundPlanMetric(value, fractionDigits).toFixed(fractionDigits);
}

function roundPlanMetric(value: number, fractionDigits = 1): number {
  if (!Number.isFinite(value)) return 0;
  const factor = 10 ** fractionDigits;
  return Math.round(value * factor) / factor;
}

function scoreTrend(iterations: ReplayCameraPlanIterationDiagnostic[]): 'down' | 'mixed' {
  for (let index = 1; index < iterations.length; index += 1) {
    if (iterations[index].score > iterations[index - 1].score + VISIBILITY_ZOOM_SCALE_EPSILON) {
      return 'mixed';
    }
  }
  return 'down';
}

function smoothLocalCameraKeyframes(
  rawKeyframes: LocalCameraKeyframe[],
  style: ReplayCameraResolvedStyle
): LocalCameraKeyframe[] {
  if (rawKeyframes.length <= MIN_CAMERA_KEYFRAMES) {
    return unwrapBearingKeyframes(rawKeyframes);
  }

  let keyframes = unwrapBearingKeyframes(rawKeyframes);
  const stepSeconds = medianKeyframeStepSeconds(keyframes);
  const radiusFrames = Math.max(1, Math.round(style.smoothingRadiusSeconds / stepSeconds));
  const passes = Math.max(MIN_SMOOTHING_PASSES, style.smoothingPasses);

  for (let pass = 0; pass < passes; pass += 1) {
    keyframes = keyframes.map((keyframe, index) => {
      const average = weightedKeyframeAverage(keyframes, index, radiusFrames);
      const edgeBlend = endpointSmoothingBlend(index, keyframes.length, radiusFrames);
      const blend = style.smoothingBlend * edgeBlend;

      return {
        timeSeconds: keyframe.timeSeconds,
        x: lerp(keyframe.x, average.x, blend),
        y: lerp(keyframe.y, average.y, blend),
        bearingDegrees: lerp(keyframe.bearingDegrees, average.bearingDegrees, blend),
        pitch: lerp(keyframe.pitch, average.pitch, blend),
        zoom: lerp(keyframe.zoom, average.zoom, blend),
      };
    });
  }

  return smoothCameraScalars(keyframes, style);
}

function smoothCameraScalars(
  keyframes: LocalCameraKeyframe[],
  style: ReplayCameraResolvedStyle
): LocalCameraKeyframe[] {
  const stepSeconds = medianKeyframeStepSeconds(keyframes);
  const radiusFrames = Math.max(1, Math.round(style.scalarSmoothingRadiusSeconds / stepSeconds));
  let scalarKeyframes = keyframes;

  for (let pass = 0; pass < style.scalarSmoothingPasses; pass += 1) {
    scalarKeyframes = smoothScalarFields(scalarKeyframes, radiusFrames, style.scalarSmoothingBlend);
  }

  scalarKeyframes = limitScalarRate(scalarKeyframes, 'zoom', style.maxZoomRatePerSecond);
  scalarKeyframes = limitScalarRate(scalarKeyframes, 'pitch', style.maxPitchRatePerSecond);
  return constrainCameraMotion(scalarKeyframes, style);
}

function constrainCameraMotion(
  keyframes: LocalCameraKeyframe[],
  style: ReplayCameraResolvedStyle
): LocalCameraKeyframe[] {
  let constrained = limitVectorRate(keyframes, style.maxPanRateMetersPerSecond);
  constrained = limitVectorAcceleration(constrained, style.maxPanAccelerationMetersPerSecondSquared);
  constrained = limitVectorRate(constrained, style.maxPanRateMetersPerSecond);
  constrained = limitScalarRate(constrained, 'bearingDegrees', style.maxBearingRateDegreesPerSecond);
  constrained = limitScalarAcceleration(
    constrained,
    'bearingDegrees',
    style.maxBearingAccelerationDegreesPerSecondSquared
  );
  return limitScalarRate(constrained, 'bearingDegrees', style.maxBearingRateDegreesPerSecond);
}

function smoothScalarFields(
  keyframes: LocalCameraKeyframe[],
  radiusFrames: number,
  blend: number
): LocalCameraKeyframe[] {
  return keyframes.map((keyframe, index) => {
    const average = weightedKeyframeAverage(keyframes, index, radiusFrames);
    const edgeBlend = endpointSmoothingBlend(index, keyframes.length, radiusFrames);
    const scalarBlend = blend * edgeBlend;

    return {
      ...keyframe,
      pitch: lerp(keyframe.pitch, average.pitch, scalarBlend),
      zoom: lerp(keyframe.zoom, average.zoom, scalarBlend),
    };
  });
}

function limitScalarRate(
  keyframes: LocalCameraKeyframe[],
  field: 'bearingDegrees' | 'pitch' | 'zoom',
  maxRatePerSecond: number
): LocalCameraKeyframe[] {
  const forward = keyframes.map((keyframe) => ({ ...keyframe }));
  for (let index = 1; index < forward.length; index += 1) {
    const previous = forward[index - 1];
    const current = forward[index];
    const maxDelta = maxRatePerSecond * Math.max(current.timeSeconds - previous.timeSeconds, 0);
    current[field] = clamp(current[field], previous[field] - maxDelta, previous[field] + maxDelta);
  }

  for (let index = forward.length - 2; index >= 0; index -= 1) {
    const current = forward[index];
    const next = forward[index + 1];
    const maxDelta = maxRatePerSecond * Math.max(next.timeSeconds - current.timeSeconds, 0);
    current[field] = clamp(current[field], next[field] - maxDelta, next[field] + maxDelta);
  }

  return forward;
}

function limitVectorRate(keyframes: LocalCameraKeyframe[], maxRateMetersPerSecond: number): LocalCameraKeyframe[] {
  const forward = keyframes.map((keyframe) => ({ ...keyframe }));
  for (let index = 1; index < forward.length; index += 1) {
    const previous = forward[index - 1];
    const current = forward[index];
    const maxDistance = maxRateMetersPerSecond * Math.max(current.timeSeconds - previous.timeSeconds, 0);
    clampPointDistance(current, previous, maxDistance);
  }

  for (let index = forward.length - 2; index >= 0; index -= 1) {
    const current = forward[index];
    const next = forward[index + 1];
    const maxDistance = maxRateMetersPerSecond * Math.max(next.timeSeconds - current.timeSeconds, 0);
    clampPointDistance(current, next, maxDistance);
  }

  return forward;
}

function limitVectorAcceleration(
  keyframes: LocalCameraKeyframe[],
  maxAccelerationMetersPerSecondSquared: number
): LocalCameraKeyframe[] {
  const forward = keyframes.map((keyframe) => ({ ...keyframe }));
  let previousVelocity: LocalPoint | null = null;
  for (let index = 1; index < forward.length; index += 1) {
    const previous = forward[index - 1];
    const current = forward[index];
    const dt = current.timeSeconds - previous.timeSeconds;
    if (dt <= 0) continue;

    let velocity = { x: (current.x - previous.x) / dt, y: (current.y - previous.y) / dt };
    if (previousVelocity) {
      velocity = clampVelocityDelta(velocity, previousVelocity, maxAccelerationMetersPerSecondSquared * dt);
      current.x = previous.x + velocity.x * dt;
      current.y = previous.y + velocity.y * dt;
    }
    previousVelocity = velocity;
  }

  let nextVelocity: LocalPoint | null = null;
  for (let index = forward.length - 2; index >= 0; index -= 1) {
    const current = forward[index];
    const next = forward[index + 1];
    const dt = next.timeSeconds - current.timeSeconds;
    if (dt <= 0) continue;

    let velocity = { x: (next.x - current.x) / dt, y: (next.y - current.y) / dt };
    if (nextVelocity) {
      velocity = clampVelocityDelta(velocity, nextVelocity, maxAccelerationMetersPerSecondSquared * dt);
      current.x = next.x - velocity.x * dt;
      current.y = next.y - velocity.y * dt;
    }
    nextVelocity = velocity;
  }

  return forward;
}

function limitScalarAcceleration(
  keyframes: LocalCameraKeyframe[],
  field: 'bearingDegrees' | 'pitch' | 'zoom',
  maxAccelerationPerSecondSquared: number
): LocalCameraKeyframe[] {
  const forward = keyframes.map((keyframe) => ({ ...keyframe }));
  let previousVelocity: number | null = null;
  for (let index = 1; index < forward.length; index += 1) {
    const previous = forward[index - 1];
    const current = forward[index];
    const dt = current.timeSeconds - previous.timeSeconds;
    if (dt <= 0) continue;

    let velocity = (current[field] - previous[field]) / dt;
    if (previousVelocity != null) {
      const maxDeltaVelocity = maxAccelerationPerSecondSquared * dt;
      velocity = clamp(velocity, previousVelocity - maxDeltaVelocity, previousVelocity + maxDeltaVelocity);
      current[field] = previous[field] + velocity * dt;
    }
    previousVelocity = velocity;
  }

  let nextVelocity: number | null = null;
  for (let index = forward.length - 2; index >= 0; index -= 1) {
    const current = forward[index];
    const next = forward[index + 1];
    const dt = next.timeSeconds - current.timeSeconds;
    if (dt <= 0) continue;

    let velocity = (next[field] - current[field]) / dt;
    if (nextVelocity != null) {
      const maxDeltaVelocity = maxAccelerationPerSecondSquared * dt;
      velocity = clamp(velocity, nextVelocity - maxDeltaVelocity, nextVelocity + maxDeltaVelocity);
      current[field] = next[field] - velocity * dt;
    }
    nextVelocity = velocity;
  }

  return forward;
}

function clampPointDistance(point: LocalPoint, anchor: LocalPoint, maxDistance: number): void {
  if (maxDistance < 0) return;
  const dx = point.x - anchor.x;
  const dy = point.y - anchor.y;
  const distance = Math.hypot(dx, dy);
  if (distance <= maxDistance || distance <= 0) return;
  const scale = maxDistance / distance;
  point.x = anchor.x + dx * scale;
  point.y = anchor.y + dy * scale;
}

function clampVelocityDelta(velocity: LocalPoint, reference: LocalPoint, maxDelta: number): LocalPoint {
  if (maxDelta < 0) return velocity;
  const dx = velocity.x - reference.x;
  const dy = velocity.y - reference.y;
  const delta = Math.hypot(dx, dy);
  if (delta <= maxDelta || delta <= 0) return velocity;
  const scale = maxDelta / delta;
  return {
    x: reference.x + dx * scale,
    y: reference.y + dy * scale,
  };
}

function unwrapBearingKeyframes(keyframes: LocalCameraKeyframe[]): LocalCameraKeyframe[] {
  let previous = normalizeDegrees(keyframes[0]?.bearingDegrees ?? 0);
  return keyframes.map((keyframe, index) => {
    if (index === 0) {
      return { ...keyframe, bearingDegrees: previous };
    }

    const next = normalizeDegrees(keyframe.bearingDegrees);
    previous += shortestBearingDelta(previous, next);
    return { ...keyframe, bearingDegrees: previous };
  });
}

function weightedKeyframeAverage(
  keyframes: LocalCameraKeyframe[],
  index: number,
  radiusFrames: number
): Omit<LocalCameraKeyframe, 'timeSeconds'> {
  let totalWeight = 0;
  let x = 0;
  let y = 0;
  let bearingDegrees = 0;
  let pitch = 0;
  let zoom = 0;

  for (let candidateIndex = index - radiusFrames; candidateIndex <= index + radiusFrames; candidateIndex += 1) {
    const distance = Math.abs(candidateIndex - index);
    const weight = radiusFrames + 1 - distance;
    const candidate = keyframes[clampKeyframeIndex(candidateIndex, keyframes.length)];
    totalWeight += weight;
    x += candidate.x * weight;
    y += candidate.y * weight;
    bearingDegrees += candidate.bearingDegrees * weight;
    pitch += candidate.pitch * weight;
    zoom += candidate.zoom * weight;
  }

  return {
    x: x / totalWeight,
    y: y / totalWeight,
    bearingDegrees: bearingDegrees / totalWeight,
    pitch: pitch / totalWeight,
    zoom: zoom / totalWeight,
  };
}

function endpointSmoothingBlend(index: number, keyframeCount: number, radiusFrames: number): number {
  if (keyframeCount <= MIN_CAMERA_KEYFRAMES || radiusFrames <= 0) return 1;
  const edgeDistance = Math.min(index, keyframeCount - 1 - index);
  const edgeProgress = clamp(edgeDistance / radiusFrames, 0, 1);
  return lerp(ENDPOINT_SMOOTHING_BLEND_FLOOR, 1, edgeProgress);
}

function clampKeyframeIndex(index: number, keyframeCount: number): number {
  return clamp(index, 0, keyframeCount - 1);
}

function averageRouteWindow(
  path: ReplayPath,
  projection: ReplayCameraLocalProjection,
  progress: number,
  windowProgress: number,
  sampleCount: number
): LocalPoint {
  const start = clamp(progress - windowProgress * 0.25, 0, 1);
  const end = clamp(progress + windowProgress, 0, 1);
  let totalWeight = 0;
  let x = 0;
  let y = 0;

  for (let index = 0; index < sampleCount; index += 1) {
    const t = sampleCount <= 1 ? 0 : index / (sampleCount - 1);
    const sample = sampleReplayPath(path, lerp(start, end, t));
    if (!sample) continue;

    const local = projection.toLocal(sample);
    const weight = ROUTE_WINDOW_BASE_SAMPLE_WEIGHT + ROUTE_WINDOW_FUTURE_SAMPLE_WEIGHT * t;
    totalWeight += weight;
    x += local.x * weight;
    y += local.y * weight;
  }

  if (totalWeight <= 0) {
    const fallback = sampleReplayPath(path, progress);
    return fallback ? projection.toLocal(fallback) : { x: 0, y: 0 };
  }

  return { x: x / totalWeight, y: y / totalWeight };
}

function stableRouteBearing(
  path: ReplayPath,
  projection: ReplayCameraLocalProjection,
  current: ReplayPathSample,
  progress: number,
  style: ReplayCameraResolvedStyle
): number {
  const start = averageRouteWindow(
    path,
    projection,
    clamp(progress - style.directionBackProgress, 0, 1),
    style.targetWindowProgress,
    style.windowSampleCount
  );
  const end = averageRouteWindow(
    path,
    projection,
    clamp(progress + style.directionForwardProgress, 0, 1),
    style.targetWindowProgress,
    style.windowSampleCount
  );
  const distanceMeters = distanceBetweenLocalPoints(start, end);
  if (distanceMeters < MIN_DIRECTION_DISTANCE_METERS) return current.headingDegrees;
  return bearingFromLocalPoints(start, end);
}

function routeCurvatureScore(
  path: ReplayPath,
  projection: ReplayCameraLocalProjection,
  progress: number,
  windowProgress: number,
  sampleCount: number
): number {
  const points: LocalPoint[] = [];
  const start = clamp(progress - windowProgress * 0.5, 0, 1);
  const end = clamp(progress + windowProgress * 0.5, 0, 1);
  for (let index = 0; index < sampleCount; index += 1) {
    const t = sampleCount <= 1 ? 0 : index / (sampleCount - 1);
    const sample = sampleReplayPath(path, lerp(start, end, t));
    if (sample) points.push(projection.toLocal(sample));
  }

  if (points.length < 3) return 0;

  let previousBearing: number | null = null;
  let headingDeltaSum = 0;
  let segmentCount = 0;
  for (let index = 1; index < points.length; index += 1) {
    if (distanceBetweenLocalPoints(points[index - 1], points[index]) < MIN_DIRECTION_DISTANCE_METERS) continue;
    const nextBearing = bearingFromLocalPoints(points[index - 1], points[index]);
    if (previousBearing != null) {
      headingDeltaSum += Math.abs(shortestBearingDelta(previousBearing, nextBearing));
      segmentCount += 1;
    }
    previousBearing = nextBearing;
  }

  if (segmentCount === 0) return 0;
  const averageHeadingDelta = headingDeltaSum / segmentCount;
  return clamp(averageHeadingDelta / CURVATURE_FULL_AVERAGE_HEADING_DELTA_DEGREES, 0, 1);
}

function buildLocalProjection(path: ReplayPath): ReplayCameraLocalProjection | null {
  if (path.points.length < MIN_CAMERA_KEYFRAMES || path.totalDistanceMeters <= 0) return null;

  let previousLng: number | null = null;
  let minUnwrappedLng = Number.POSITIVE_INFINITY;
  let maxUnwrappedLng = Number.NEGATIVE_INFINITY;
  let minLat = Number.POSITIVE_INFINITY;
  let maxLat = Number.NEGATIVE_INFINITY;
  for (const point of path.points) {
    const unwrappedLng: number =
      previousLng == null ? point.lng : previousLng + shortestLongitudeDelta(previousLng, point.lng);
    previousLng = unwrappedLng;
    minUnwrappedLng = Math.min(minUnwrappedLng, unwrappedLng);
    maxUnwrappedLng = Math.max(maxUnwrappedLng, unwrappedLng);
    minLat = Math.min(minLat, point.lat);
    maxLat = Math.max(maxLat, point.lat);
  }

  if (![minUnwrappedLng, maxUnwrappedLng, minLat, maxLat].every(Number.isFinite)) return null;

  const originLng = (minUnwrappedLng + maxUnwrappedLng) / 2;
  const originLat = (minLat + maxLat) / 2;
  const originLatRadians = degreesToRadians(originLat);
  const latitudeScale = Math.max(Math.cos(originLatRadians), MIN_LATITUDE_SCALE);

  const projection: ReplayCameraLocalProjection = {
    bounds: {
      minX: Number.POSITIVE_INFINITY,
      maxX: Number.NEGATIVE_INFINITY,
      minY: Number.POSITIVE_INFINITY,
      maxY: Number.NEGATIVE_INFINITY,
    },
    latitudeScale,
    toLocal(sample) {
      const sampleLng = unwrapLongitudeToReference(sample.lng, originLng);
      return {
        x: degreesToRadians(sampleLng - originLng) * EARTH_RADIUS_METERS * latitudeScale,
        y: degreesToRadians(sample.lat - originLat) * EARTH_RADIUS_METERS,
      };
    },
    toLngLat(point) {
      return [
        originLng + radiansToDegrees(point.x / (EARTH_RADIUS_METERS * latitudeScale)),
        originLat + radiansToDegrees(point.y / EARTH_RADIUS_METERS),
      ];
    },
  };

  for (const point of path.points) {
    const local = projection.toLocal(point);
    projection.bounds.minX = Math.min(projection.bounds.minX, local.x);
    projection.bounds.maxX = Math.max(projection.bounds.maxX, local.x);
    projection.bounds.minY = Math.min(projection.bounds.minY, local.y);
    projection.bounds.maxY = Math.max(projection.bounds.maxY, local.y);
  }

  return projection;
}

function localKeyframeToCameraTarget(
  keyframe: LocalCameraKeyframe,
  projection: ReplayCameraLocalProjection
): CameraTargetKeyframe {
  return {
    timeSeconds: keyframe.timeSeconds,
    ...localKeyframeToCameraFrame(keyframe, projection),
  };
}

function localKeyframeToCameraFrame(
  keyframe: LocalCameraKeyframe,
  projection: ReplayCameraLocalProjection
): ReplayCameraFrame {
  return {
    center: projection.toLngLat(keyframe),
    bearing: normalizeDegrees(keyframe.bearingDegrees),
    pitch: clamp(keyframe.pitch, MIN_CAMERA_PITCH, MAX_CAMERA_PITCH),
    zoom: clamp(keyframe.zoom, MIN_CAMERA_VISIBILITY_ZOOM, MAX_CAMERA_ZOOM),
  };
}

function firstKeyframeAfter(keyframes: LocalCameraKeyframe[], elapsedSeconds: number): number {
  return upperBoundClampedIndex(keyframes, elapsedSeconds, (keyframe) => keyframe.timeSeconds);
}

function medianKeyframeStepSeconds(keyframes: LocalCameraKeyframe[]): number {
  const steps: number[] = [];
  for (let index = 1; index < keyframes.length; index += 1) {
    const step = keyframes[index].timeSeconds - keyframes[index - 1].timeSeconds;
    if (step > 0) steps.push(step);
  }
  if (steps.length === 0) return LONG_REPLAY_SAMPLE_STEP_SECONDS;
  steps.sort((a, b) => a - b);
  return steps[Math.floor(steps.length / 2)];
}

function zoomForRailPreset(totalDistanceMeters: number, preset: ReplayCameraPreset): number {
  if (preset.id === 'chase') {
    if (totalDistanceMeters < SHORT_ROUTE_DISTANCE_METERS) return 15.7;
    if (totalDistanceMeters < MEDIUM_ROUTE_DISTANCE_METERS) return 15;
    if (totalDistanceMeters < LONG_ROUTE_DISTANCE_METERS) return 14.5;
    return 13.9;
  }
  if (preset.id === 'follow') {
    if (totalDistanceMeters < SHORT_ROUTE_DISTANCE_METERS) return 15.2;
    if (totalDistanceMeters < MEDIUM_ROUTE_DISTANCE_METERS) return 14.7;
    if (totalDistanceMeters < LONG_ROUTE_DISTANCE_METERS) return 14.1;
    return 13.5;
  }
  if (totalDistanceMeters < SHORT_ROUTE_DISTANCE_METERS) return 14.7;
  if (totalDistanceMeters < MEDIUM_ROUTE_DISTANCE_METERS) return 14.4;
  if (totalDistanceMeters < LONG_ROUTE_DISTANCE_METERS) return 13.2;
  return 12.4;
}

function resolveReplayViewport(viewport: ReplayCameraPlanOptions['viewport']): ResolvedReplayViewport | null {
  if (!viewport) return null;
  const width = Number(viewport.width);
  const height = Number(viewport.height);
  if (!Number.isFinite(width) || !Number.isFinite(height) || width <= 0 || height <= 0) return null;

  const paddingTop = clamp(Number(viewport.paddingTop ?? 0), 0, height - 1);
  const paddingBottom = clamp(Number(viewport.paddingBottom ?? 0), 0, height - 1);
  const paddingLeft = clamp(Number(viewport.paddingLeft ?? 0), 0, width - 1);
  const paddingRight = clamp(Number(viewport.paddingRight ?? 0), 0, width - 1);
  const visibleWidth = Math.max(MIN_VISIBLE_VIEWPORT_WIDTH, width - paddingLeft - paddingRight);
  const visibleHeight = Math.max(MIN_VISIBLE_VIEWPORT_HEIGHT, height - paddingTop - paddingBottom);

  return {
    width,
    height,
    paddingTop,
    paddingRight,
    paddingBottom,
    paddingLeft,
    visibleWidth,
    visibleHeight,
  };
}

function resolveViewportZoomOut(viewport: ResolvedReplayViewport | null): number {
  if (!viewport) return 0;
  const narrowViewportZoomOut = viewport.width < MOBILE_VIEWPORT_WIDTH ? 0.25 : 0;
  const shortViewportZoomOut = viewport.height < SHORT_VIEWPORT_HEIGHT ? 0.2 : 0;
  const widthScaleZoomOut = Math.log2(viewport.width / viewport.visibleWidth);
  const heightScaleZoomOut = Math.log2(viewport.height / viewport.visibleHeight);
  const occlusionZoomOut = clamp(
    Math.max(widthScaleZoomOut, heightScaleZoomOut, 0) * OCCLUSION_ZOOM_OUT_RESPONSE,
    0,
    MAX_OCCLUSION_ZOOM_OUT
  );
  return narrowViewportZoomOut + shortViewportZoomOut + occlusionZoomOut;
}

function resolveViewportPitchReduction(viewport: ResolvedReplayViewport | null): number {
  if (!viewport) return 0;
  const occludedHeightRatio = clamp(
    (viewport.paddingTop + viewport.paddingBottom) / Math.max(1, viewport.height),
    0,
    1
  );
  const progress = clamp(
    (occludedHeightRatio - PITCH_OCCLUSION_START_RATIO) / (PITCH_OCCLUSION_FULL_RATIO - PITCH_OCCLUSION_START_RATIO),
    0,
    1
  );
  return progress * MAX_OCCLUSION_PITCH_REDUCTION;
}

function resolveRouteFitZoom(
  projection: ReplayCameraLocalProjection,
  viewport: ResolvedReplayViewport | null,
  presetId: ReplayCameraPresetId,
  maxZoom: number
): number | null {
  return resolveLocalBoundsFitZoom(projection, projection.bounds, viewport, presetId, maxZoom);
}

function resolveActiveRouteWindowFitZoom(
  path: ReplayPath,
  projection: ReplayCameraLocalProjection,
  progress: number,
  style: ReplayCameraResolvedStyle,
  maxZoom: number
): number | null {
  const viewport = style.viewport;
  if (!viewport) return null;
  const windowEnd = Math.max(style.directionForwardProgress, style.targetWindowProgress, style.curvatureWindowProgress);
  const bounds = routeWindowBounds(
    path,
    projection,
    clamp(progress - style.directionBackProgress, 0, 1),
    clamp(progress + windowEnd, 0, 1),
    style.windowSampleCount
  );
  if (!bounds) return null;
  return resolveLocalBoundsFitZoom(projection, bounds, viewport, style.preset.id, maxZoom);
}

function routeWindowBounds(
  path: ReplayPath,
  projection: ReplayCameraLocalProjection,
  startProgress: number,
  endProgress: number,
  sampleCount: number
): LocalBounds | null {
  const bounds: LocalBounds = {
    minX: Number.POSITIVE_INFINITY,
    maxX: Number.NEGATIVE_INFINITY,
    minY: Number.POSITIVE_INFINITY,
    maxY: Number.NEGATIVE_INFINITY,
  };
  let pointCount = 0;
  const count = Math.max(2, sampleCount);
  for (let index = 0; index < count; index += 1) {
    const t = count <= 1 ? 0 : index / (count - 1);
    const sample = sampleReplayPath(path, lerp(startProgress, endProgress, t));
    if (!sample) continue;
    extendLocalBounds(bounds, projection.toLocal(sample));
    pointCount += 1;
  }
  return pointCount > 0 ? bounds : null;
}

function plannerVisibleScreenRect(
  viewport: ResolvedReplayViewport,
  requestedMarginPx: number
): PlannerVisibleScreenRect | null {
  const visibleLeft = clamp(viewport.paddingLeft, 0, viewport.width - 1);
  const visibleRight = clamp(viewport.width - viewport.paddingRight, visibleLeft + 1, viewport.width);
  const visibleTop = clamp(viewport.paddingTop, 0, viewport.height - 1);
  const visibleBottom = clamp(viewport.height - viewport.paddingBottom, visibleTop + 1, viewport.height);
  const maxMarginX = Math.max(0, (visibleRight - visibleLeft - 1) / 2);
  const maxMarginY = Math.max(0, (visibleBottom - visibleTop - 1) / 2);
  const marginX = Math.min(Math.max(0, requestedMarginPx), maxMarginX);
  const marginY = Math.min(Math.max(0, requestedMarginPx), maxMarginY);
  const left = visibleLeft + marginX;
  const right = visibleRight - marginX;
  const top = visibleTop + marginY;
  const bottom = visibleBottom - marginY;
  if (right <= left || bottom <= top) return null;

  return {
    left,
    right,
    top,
    bottom,
    width: right - left,
    height: bottom - top,
    centerX: (visibleLeft + visibleRight) / 2,
    centerY: (visibleTop + visibleBottom) / 2,
  };
}

function projectLocalPointToPlannerScreen(
  keyframe: LocalCameraKeyframe,
  point: LocalPoint,
  projection: ReplayCameraLocalProjection,
  viewport: ResolvedReplayViewport,
  visibleRect: PlannerVisibleScreenRect
): LocalPoint {
  const metersPerPixel = metersPerPixelForZoom(projection, keyframe.zoom);
  const pitchScale = plannerPitchVerticalScale(keyframe.pitch);
  const bearingRadians = degreesToRadians(keyframe.bearingDegrees);
  const cos = Math.cos(bearingRadians);
  const sin = Math.sin(bearingRadians);
  const dx = point.x - keyframe.x;
  const dy = point.y - keyframe.y;
  const rightMeters = dx * cos - dy * sin;
  const upMeters = dx * sin + dy * cos;

  return {
    x: visibleRect.centerX + rightMeters / metersPerPixel,
    y: visibleRect.centerY - (upMeters / metersPerPixel) * pitchScaleForViewport(pitchScale, viewport),
  };
}

function screenShiftToCameraCenterDelta(
  keyframe: LocalCameraKeyframe,
  projection: ReplayCameraLocalProjection,
  viewport: ResolvedReplayViewport,
  shiftX: number,
  shiftY: number
): LocalPoint {
  const metersPerPixel = metersPerPixelForZoom(projection, keyframe.zoom);
  const pitchScale = pitchScaleForViewport(plannerPitchVerticalScale(keyframe.pitch), viewport);
  const bearingRadians = degreesToRadians(keyframe.bearingDegrees);
  const cos = Math.cos(bearingRadians);
  const sin = Math.sin(bearingRadians);
  const centerRightMeters = -shiftX * metersPerPixel;
  const centerUpMeters = (shiftY * metersPerPixel) / pitchScale;

  return {
    x: centerRightMeters * cos + centerUpMeters * sin,
    y: -centerRightMeters * sin + centerUpMeters * cos,
  };
}

function metersPerPixelForZoom(projection: ReplayCameraLocalProjection, zoom: number): number {
  return (WEB_MERCATOR_WORLD_CIRCUMFERENCE_METERS * projection.latitudeScale) / (MAPLIBRE_TILE_SIZE_PIXELS * 2 ** zoom);
}

function plannerPitchVerticalScale(pitch: number): number {
  const t = clamp((pitch - MIN_CAMERA_PITCH) / (MAX_CAMERA_PITCH - MIN_CAMERA_PITCH), 0, 1);
  return lerp(1, VISIBILITY_MIN_PITCH_VERTICAL_SCALE, t);
}

function pitchScaleForViewport(pitchScale: number, viewport: ResolvedReplayViewport): number {
  const visibleHeightRatio = viewport.visibleHeight / Math.max(1, viewport.height);
  return lerp(1, pitchScale, clamp(visibleHeightRatio, 0, 1));
}

function emptyPlannerScreenBounds(): PlannerScreenBounds {
  return {
    minX: Number.POSITIVE_INFINITY,
    maxX: Number.NEGATIVE_INFINITY,
    minY: Number.POSITIVE_INFINITY,
    maxY: Number.NEGATIVE_INFINITY,
  };
}

function extendPlannerScreenBounds(bounds: PlannerScreenBounds, point: LocalPoint): void {
  bounds.minX = Math.min(bounds.minX, point.x);
  bounds.maxX = Math.max(bounds.maxX, point.x);
  bounds.minY = Math.min(bounds.minY, point.y);
  bounds.maxY = Math.max(bounds.maxY, point.y);
}

function screenBoundsFitScale(bounds: PlannerScreenBounds, visibleRect: PlannerVisibleScreenRect): number {
  return Math.max((bounds.maxX - bounds.minX) / visibleRect.width, (bounds.maxY - bounds.minY) / visibleRect.height, 1);
}

function screenPointFitScale(point: LocalPoint, visibleRect: PlannerVisibleScreenRect): number {
  return Math.max(
    Math.abs(point.x - visibleRect.centerX) / Math.max(1, visibleRect.width / 2),
    Math.abs(point.y - visibleRect.centerY) / Math.max(1, visibleRect.height / 2),
    1
  );
}

function screenPointShift(value: number, visibleMin: number, visibleMax: number): number {
  if (value < visibleMin) return visibleMin - value;
  if (value > visibleMax) return visibleMax - value;
  return 0;
}

function emptyPlannerVisibilityAdjustment(): PlannerVisibilityAdjustment {
  return {
    zoomOut: 0,
    pitchReduction: 0,
    centerShift: { x: 0, y: 0 },
  };
}

function resolveLocalBoundsFitZoom(
  projection: ReplayCameraLocalProjection,
  bounds: LocalBounds,
  viewport: ResolvedReplayViewport | null,
  presetId: ReplayCameraPresetId,
  maxZoom: number
): number | null {
  if (!viewport) return null;
  const routeWidthMeters = bounds.maxX - bounds.minX;
  const routeHeightMeters = bounds.maxY - bounds.minY;
  if (![routeWidthMeters, routeHeightMeters].every(Number.isFinite)) return null;

  const screenRatio = ROUTE_FIT_SCREEN_RATIOS[presetId];
  const targetWidthPixels = Math.max(1, viewport.visibleWidth * screenRatio);
  const targetHeightPixels = Math.max(1, viewport.visibleHeight * screenRatio);
  const metersPerPixel = Math.max(
    routeWidthMeters / targetWidthPixels,
    routeHeightMeters / targetHeightPixels,
    MIN_ROUTE_FIT_METERS_PER_PIXEL
  );
  const zoom = Math.log2(
    (WEB_MERCATOR_WORLD_CIRCUMFERENCE_METERS * projection.latitudeScale) / (MAPLIBRE_TILE_SIZE_PIXELS * metersPerPixel)
  );
  return clamp(zoom, MIN_CAMERA_VISIBILITY_ZOOM, maxZoom);
}

function extendLocalBounds(bounds: LocalBounds, point: LocalPoint): void {
  bounds.minX = Math.min(bounds.minX, point.x);
  bounds.maxX = Math.max(bounds.maxX, point.x);
  bounds.minY = Math.min(bounds.minY, point.y);
  bounds.maxY = Math.max(bounds.maxY, point.y);
}

function interpolatePoint(from: LocalPoint, to: LocalPoint, t: number): LocalPoint {
  return {
    x: lerp(from.x, to.x, t),
    y: lerp(from.y, to.y, t),
  };
}

function distanceBetweenLocalPoints(from: LocalPoint, to: LocalPoint): number {
  return Math.hypot(to.x - from.x, to.y - from.y);
}

function bearingFromLocalPoints(from: LocalPoint, to: LocalPoint): number {
  return normalizeDegrees(radiansToDegrees(Math.atan2(to.x - from.x, to.y - from.y)));
}

function shortestBearingDelta(fromDegrees: number, toDegrees: number): number {
  return ((((toDegrees - fromDegrees) % 360) + 540) % 360) - 180;
}

function normalizeDegrees(degrees: number): number {
  return ((degrees % 360) + 360) % 360;
}

function interpolateKeyframeCubic(
  previous: LocalCameraKeyframe,
  current: LocalCameraKeyframe,
  next: LocalCameraKeyframe,
  following: LocalCameraKeyframe,
  field: CubicCameraField,
  t: number,
  spanSeconds: number
): number {
  return cubicHermite(
    current[field],
    next[field],
    keyframeCubicTangent(previous, current, next, field) * spanSeconds,
    keyframeCubicTangent(current, next, following, field) * spanSeconds,
    t
  );
}

function interpolateKeyframeMonotoneScalar(
  previous: LocalCameraKeyframe,
  current: LocalCameraKeyframe,
  next: LocalCameraKeyframe,
  following: LocalCameraKeyframe,
  field: MonotoneCameraField,
  t: number,
  spanSeconds: number
): number {
  if (current[field] === next[field]) return current[field];

  const interpolated = cubicHermite(
    current[field],
    next[field],
    keyframeMonotoneTangent(previous, current, next, field) * spanSeconds,
    keyframeMonotoneTangent(current, next, following, field) * spanSeconds,
    t
  );
  return clamp(interpolated, Math.min(current[field], next[field]), Math.max(current[field], next[field]));
}

function keyframeCubicTangent(
  previous: LocalCameraKeyframe,
  current: LocalCameraKeyframe,
  next: LocalCameraKeyframe,
  field: CubicCameraField
): number {
  const leftSpan = current.timeSeconds - previous.timeSeconds;
  const rightSpan = next.timeSeconds - current.timeSeconds;
  const hasLeftSpan = leftSpan > MIN_INTERPOLATION_SPAN_SECONDS;
  const hasRightSpan = rightSpan > MIN_INTERPOLATION_SPAN_SECONDS;

  if (hasLeftSpan && hasRightSpan) {
    return (next[field] - previous[field]) / (leftSpan + rightSpan);
  }
  if (hasRightSpan) {
    return (next[field] - current[field]) / rightSpan;
  }
  if (hasLeftSpan) {
    return (current[field] - previous[field]) / leftSpan;
  }
  return 0;
}

function keyframeMonotoneTangent(
  previous: LocalCameraKeyframe,
  current: LocalCameraKeyframe,
  next: LocalCameraKeyframe,
  field: MonotoneCameraField
): number {
  const leftSpan = current.timeSeconds - previous.timeSeconds;
  const rightSpan = next.timeSeconds - current.timeSeconds;
  const hasLeftSpan = leftSpan > MIN_INTERPOLATION_SPAN_SECONDS;
  const hasRightSpan = rightSpan > MIN_INTERPOLATION_SPAN_SECONDS;

  if (hasLeftSpan && hasRightSpan) {
    const leftSlope = keyframeSlope(previous, current, field, leftSpan);
    const rightSlope = keyframeSlope(current, next, field, rightSpan);
    if (leftSlope * rightSlope <= 0) return 0;

    const leftWeight = 2 * rightSpan + leftSpan;
    const rightWeight = rightSpan + 2 * leftSpan;
    return (leftWeight + rightWeight) / (leftWeight / leftSlope + rightWeight / rightSlope);
  }
  if (hasRightSpan) {
    return keyframeSlope(current, next, field, rightSpan);
  }
  if (hasLeftSpan) {
    return keyframeSlope(previous, current, field, leftSpan);
  }
  return 0;
}

function keyframeSlope(
  from: LocalCameraKeyframe,
  to: LocalCameraKeyframe,
  field: MonotoneCameraField,
  spanSeconds: number
): number {
  return (to[field] - from[field]) / spanSeconds;
}

function cubicHermite(from: number, to: number, fromTangentDelta: number, toTangentDelta: number, t: number): number {
  const t2 = t * t;
  const t3 = t2 * t;
  return (
    (2 * t3 - 3 * t2 + 1) * from +
    (t3 - 2 * t2 + t) * fromTangentDelta +
    (-2 * t3 + 3 * t2) * to +
    (t3 - t2) * toTangentDelta
  );
}

function lerp(from: number, to: number, t: number): number {
  return from + (to - from) * t;
}

function clamp(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, value));
}

function degreesToRadians(degrees: number): number {
  return (degrees * Math.PI) / 180;
}

function radiansToDegrees(radians: number): number {
  return (radians * 180) / Math.PI;
}
