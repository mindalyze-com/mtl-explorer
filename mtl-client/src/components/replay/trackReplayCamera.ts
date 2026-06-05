import {
  interpolateBearing,
  sampleReplayPath,
  type ReplayCameraPresetId,
  type ReplayPath,
  type ReplayPathSample,
} from '@/components/replay/trackReplayPath';
import { bearing, haversineDistance } from '@/components/map/mapGeometry';

export type ReplayCameraPreset = {
  id: ReplayCameraPresetId;
  label: string;
  icon: string;
  pitch: number;
  zoom: number;
  lookAheadMeters: number;
  smoothing: number;
};

export type ReplayCameraOptions = {
  smoothness?: number;
};

export type ReplayCameraFrame = {
  center: [number, number];
  bearing: number;
  pitch: number;
  zoom: number;
};

export const REPLAY_CAMERA_SMOOTHNESS_MIN = 0;
export const REPLAY_CAMERA_SMOOTHNESS_MAX = 100;
export const REPLAY_CAMERA_SMOOTHNESS_STEP = 5;
export const REPLAY_DEFAULT_CAMERA_SMOOTHNESS = 50;

const REPLAY_CAMERA_SMOOTHNESS_RESPONSE_EXPONENT = 0.7;
const REPLAY_CAMERA_MIN_INTERPOLATION_FACTOR_SCALE = 0.25;
const REPLAY_CAMERA_SMOOTHNESS_LABELS = [
  { max: 20, label: 'Direct' },
  { max: 40, label: 'Steady' },
  { max: 65, label: 'Smooth' },
  { max: 85, label: 'Glide' },
  { max: REPLAY_CAMERA_SMOOTHNESS_MAX, label: 'Cinema' },
] as const;

export const REPLAY_CAMERA_PRESETS: ReplayCameraPreset[] = [
  {
    id: 'follow',
    label: 'Follow',
    icon: 'bi bi-camera-video',
    pitch: 54,
    zoom: 14.1,
    lookAheadMeters: 20,
    smoothing: 0.18,
  },
  {
    id: 'chase',
    label: 'Chase',
    icon: 'bi bi-fast-forward',
    pitch: 62,
    zoom: 14.5,
    lookAheadMeters: 10,
    smoothing: 0.22,
  },
  {
    id: 'overview',
    label: 'Overview',
    icon: 'bi bi-badge-3d',
    pitch: 50,
    zoom: 13.7,
    lookAheadMeters: 260,
    smoothing: 0.13,
  },
];

export const REPLAY_DEFAULT_CAMERA_PRESET: ReplayCameraPresetId = 'follow';

export function replayCameraPreset(id: ReplayCameraPresetId | string | null | undefined): ReplayCameraPreset {
  return REPLAY_CAMERA_PRESETS.find((preset) => preset.id === id) ?? REPLAY_CAMERA_PRESETS[0];
}

export function clampReplayCameraSmoothness(smoothness: number | null | undefined): number {
  const value = Number(smoothness);
  if (!Number.isFinite(value)) return REPLAY_DEFAULT_CAMERA_SMOOTHNESS;
  return Math.max(REPLAY_CAMERA_SMOOTHNESS_MIN, Math.min(REPLAY_CAMERA_SMOOTHNESS_MAX, value));
}

export function replayCameraSmoothnessWeight(smoothness: number | null | undefined): number {
  const normalized =
    (clampReplayCameraSmoothness(smoothness) - REPLAY_CAMERA_SMOOTHNESS_MIN) /
    (REPLAY_CAMERA_SMOOTHNESS_MAX - REPLAY_CAMERA_SMOOTHNESS_MIN);
  return Math.pow(normalized, REPLAY_CAMERA_SMOOTHNESS_RESPONSE_EXPONENT);
}

export function replayCameraSmoothnessLabel(smoothness: number | null | undefined): string {
  const value = clampReplayCameraSmoothness(smoothness);
  return (
    REPLAY_CAMERA_SMOOTHNESS_LABELS.find(({ max }) => value <= max)?.label ??
    REPLAY_CAMERA_SMOOTHNESS_LABELS[REPLAY_CAMERA_SMOOTHNESS_LABELS.length - 1].label
  );
}

export function buildReplayCameraFrame(
  path: ReplayPath,
  progress: number,
  presetId: ReplayCameraPresetId,
  previous?: ReplayCameraFrame | null,
  options: ReplayCameraOptions = {}
): ReplayCameraFrame | null {
  const preset = replayCameraPreset(presetId);
  const current = sampleReplayPath(path, progress);
  if (!current) return null;

  const lookAheadProgress =
    path.totalDistanceMeters > 0 ? Math.min(1, progress + preset.lookAheadMeters / path.totalDistanceMeters) : progress;
  const lookAhead = sampleReplayPath(path, lookAheadProgress) ?? current;
  const center = preset.id === 'overview' ? lookAhead : current;
  const target: ReplayCameraFrame = {
    center: [center.lng, center.lat],
    bearing: smoothedPathBearing(current, lookAhead),
    pitch: preset.pitch,
    zoom: zoomForPreset(path.totalDistanceMeters, preset),
  };

  if (!previous) return target;

  const smoothing = replayCameraInterpolationFactor(preset.smoothing, options.smoothness);

  return {
    center: [
      interpolateNumber(previous.center[0], target.center[0], smoothing),
      interpolateNumber(previous.center[1], target.center[1], smoothing),
    ],
    bearing: interpolateBearing(previous.bearing, target.bearing, smoothing),
    pitch: interpolateNumber(previous.pitch, target.pitch, smoothing),
    zoom: interpolateNumber(previous.zoom, target.zoom, smoothing),
  };
}

function smoothedPathBearing(current: ReplayPathSample, lookAhead: ReplayPathSample): number {
  const distanceMeters = haversineDistance(current.lat, current.lng, lookAhead.lat, lookAhead.lng);
  if (distanceMeters < 10) return current.headingDegrees;
  return bearing(current.lng, current.lat, lookAhead.lng, lookAhead.lat);
}

function replayCameraInterpolationFactor(base: number, smoothness: number | null | undefined): number {
  const smoothnessWeight = replayCameraSmoothnessWeight(smoothness);
  return base * (1 - smoothnessWeight * (1 - REPLAY_CAMERA_MIN_INTERPOLATION_FACTOR_SCALE));
}

function zoomForPreset(totalDistanceMeters: number, preset: ReplayCameraPreset): number {
  if (preset.id === 'chase') {
    if (totalDistanceMeters < 2_000) return 15.7;
    if (totalDistanceMeters < 8_000) return 15;
    if (totalDistanceMeters < 25_000) return 14.5;
    return 13.9;
  }
  if (preset.id === 'follow') {
    if (totalDistanceMeters < 2_000) return 15.2;
    if (totalDistanceMeters < 8_000) return 14.7;
    if (totalDistanceMeters < 25_000) return 14.1;
    return 13.5;
  }
  if (totalDistanceMeters < 2_000) return 14.7;
  if (totalDistanceMeters < 8_000) return 14;
  if (totalDistanceMeters < 25_000) return 13.2;
  return 12.4;
}

function interpolateNumber(from: number, to: number, t: number): number {
  return from + (to - from) * t;
}
