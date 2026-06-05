import { describe, expect, it } from 'vitest';
import {
  buildReplayCameraTargetKeyframes,
  ReplayCameraRailPlanner,
  type CameraTargetKeyframe,
  type ReplayCameraFrame,
} from '@/components/replay/replayCameraRailPlanner';
import { buildReplayPath, sampleReplayPath } from '@/components/replay/trackReplayPath';

describe('ReplayCameraRailPlanner', () => {
  it('builds and samples a stable rail for a straight route', () => {
    const path = buildReplayPath({
      coordinates: [
        [8, 47, 500],
        [8.02, 47, 520],
      ],
    });

    const rail = ReplayCameraRailPlanner.build({
      path,
      durationSeconds: 45,
      preset: 'follow',
      smoothness: 75,
    });

    expect(rail).not.toBeNull();
    expect(rail?.durationSeconds).toBe(45);
    expect(rail?.keyframeCount).toBeGreaterThan(30);

    const start = rail?.sample(0);
    const middle = rail?.sample(22.5);
    const end = rail?.sample(45);

    expect(start?.center[0]).toBeGreaterThanOrEqual(8);
    expect(middle?.center[0]).toBeGreaterThan(start?.center[0] ?? 8);
    expect(end?.center[0]).toBeGreaterThan(middle?.center[0] ?? 8);
    expect(start?.bearing).toBeCloseTo(90, 0);
    expect(middle?.bearing).toBeCloseTo(90, 0);
    expect(end?.bearing).toBeCloseTo(90, 0);
  });

  it('keeps dateline-crossing routes framed near the dateline', () => {
    const path = buildReplayPath({
      coordinates: [
        [179.9, 0, 10],
        [-179.9, 0, 20],
      ],
    });

    const rail = ReplayCameraRailPlanner.build({
      path,
      durationSeconds: 45,
      preset: 'follow',
      smoothness: 75,
      viewport: { width: 1280, height: 720 },
    });
    const middle = rail?.sample(22.5);

    expect(path.totalDistanceMeters).toBeLessThan(25_000);
    expect(Math.abs(middle?.center[0] ?? 0)).toBeGreaterThan(170);
    expect(middle?.bearing).toBeCloseTo(90, 0);
  });

  it('unwraps bearings across north without a 360 degree spin', () => {
    const path = buildReplayPath({
      coordinates: [
        [8, 47],
        [7.999, 47.01],
        [8.001, 47.02],
        [8, 47.03],
      ],
    });
    const rail = ReplayCameraRailPlanner.build({
      path,
      durationSeconds: 90,
      preset: 'follow',
      smoothness: 70,
    });

    const frames = sampleRail(rail, 90, 18);
    expect(frames.length).toBeGreaterThan(5);
    expect(maxBearingDelta(frames)).toBeLessThan(45);
  });

  it('produces smoother bearing changes than the raw target keyframes', () => {
    const path = buildReplayPath({
      coordinates: [
        [8, 47],
        [8.003, 47.003],
        [8.006, 47],
        [8.009, 47.003],
        [8.012, 47],
        [8.015, 47.003],
      ],
    });
    const options = {
      path,
      durationSeconds: 90,
      preset: 'chase' as const,
      smoothness: 100,
    };
    const raw = buildReplayCameraTargetKeyframes(options);
    const rail = ReplayCameraRailPlanner.build(options);
    const smoothed = raw
      .map((keyframe) => rail?.sample(keyframe.timeSeconds) ?? null)
      .filter((frame): frame is ReplayCameraFrame => frame != null);

    expect(raw.length).toBeGreaterThan(10);
    expect(maxBearingDelta(smoothed)).toBeLessThan(maxBearingDelta(raw));
  });

  it('keeps camera smoothing active through the end of the replay', () => {
    const path = buildReplayPath({
      coordinates: [
        [8, 47],
        [8.005, 47.002],
        [8.01, 47.006],
        [8.015, 47.004],
        [8.017, 47.011],
        [8.018, 47.018],
        [8.028, 47.018],
      ],
    });
    const durationSeconds = 60;
    const options = {
      path,
      durationSeconds,
      preset: 'follow' as const,
      smoothness: 100,
    };
    const raw = buildReplayCameraTargetKeyframes(options);
    const rail = ReplayCameraRailPlanner.build(options);
    const tailStartSeconds = durationSeconds * 0.8;
    const rawTail = raw.filter((keyframe) => keyframe.timeSeconds >= tailStartSeconds);
    const smoothedTail = rawTail
      .map((keyframe) => rail?.sample(keyframe.timeSeconds) ?? null)
      .filter((frame): frame is ReplayCameraFrame => frame != null);
    const rawBeforeEnd = raw[raw.length - 2];
    const rawEnd = raw[raw.length - 1];
    const smoothedBeforeEnd = rail?.sample(rawBeforeEnd.timeSeconds);
    const smoothedEnd = rail?.sample(rawEnd.timeSeconds);

    if (!smoothedBeforeEnd || !smoothedEnd) {
      throw new Error('Expected the replay rail to sample the final keyframes');
    }

    expect(rawTail.length).toBeGreaterThan(8);
    expect(smoothedTail.length).toBe(rawTail.length);
    expect(maxBearingDelta(smoothedTail)).toBeLessThan(maxBearingDelta(rawTail) * 0.7);
    const rawFinalDelta = Math.abs(shortestBearingDelta(rawBeforeEnd.bearing, rawEnd.bearing));
    const smoothedFinalDelta = Math.abs(shortestBearingDelta(smoothedBeforeEnd.bearing, smoothedEnd.bearing));
    if (rawFinalDelta > 0.5) {
      expect(smoothedFinalDelta).toBeLessThan(rawFinalDelta * 0.75);
    } else {
      expect(smoothedFinalDelta).toBeLessThan(0.5);
    }
  });

  it('makes the smoothness setting materially change the planned rail', () => {
    const path = buildReplayPath({
      coordinates: [
        [8, 47],
        [8.004, 47.004],
        [8.008, 47],
        [8.012, 47.004],
        [8.016, 47],
        [8.02, 47.004],
        [8.024, 47],
      ],
    });

    const lowSmoothRail = ReplayCameraRailPlanner.build({
      path,
      durationSeconds: 60,
      preset: 'follow',
      smoothness: 0,
    });
    const highSmoothRail = ReplayCameraRailPlanner.build({
      path,
      durationSeconds: 60,
      preset: 'follow',
      smoothness: 100,
    });

    const lowSmoothFrames = sampleRail(lowSmoothRail, 60, 30);
    const highSmoothFrames = sampleRail(highSmoothRail, 60, 30);

    expect(highSmoothFrames.length).toBe(lowSmoothFrames.length);
    expect(maxBearingDelta(highSmoothFrames)).toBeLessThan(maxBearingDelta(lowSmoothFrames) * 0.55);
    expect(maxZoomDelta(highSmoothFrames)).toBeLessThan(maxZoomDelta(lowSmoothFrames));
    expect(maxZoomDelta(highSmoothFrames)).toBeLessThan(0.12);
    expect(averageZoom(highSmoothFrames)).toBeLessThan(averageZoom(lowSmoothFrames));
  });

  it('bounds high-smoothness bearing movement across sharp turns', () => {
    const path = buildReplayPath({
      coordinates: [
        [8, 47],
        [8.003, 47.004],
        [8.006, 47],
        [8.009, 47.004],
        [8.012, 47],
        [8.015, 47.004],
        [8.018, 47],
      ],
    });

    const rail = ReplayCameraRailPlanner.build({
      path,
      durationSeconds: 45,
      preset: 'chase',
      smoothness: 100,
    });
    const frames = sampleRail(rail, 45, 90);

    expect(maxBearingRate(frames, 45)).toBeLessThan(45);
  });

  it('keeps the midpoint calm while preserving maximum-smoothness headroom', () => {
    const path = buildReplayPath({
      coordinates: [
        [8, 47],
        [8.003, 47.004],
        [8.008, 47.001],
        [8.01, 47.008],
        [8.017, 47.004],
        [8.019, 47.012],
        [8.026, 47.007],
      ],
    });

    const lowRail = ReplayCameraRailPlanner.build({
      path,
      durationSeconds: 45,
      preset: 'follow',
      smoothness: 0,
    });
    const middleRail = ReplayCameraRailPlanner.build({
      path,
      durationSeconds: 45,
      preset: 'follow',
      smoothness: 50,
    });
    const maxRail = ReplayCameraRailPlanner.build({
      path,
      durationSeconds: 45,
      preset: 'follow',
      smoothness: 100,
    });

    const lowFrames = sampleRail(lowRail, 45, 30);
    const middleFrames = sampleRail(middleRail, 45, 30);
    const maxFrames = sampleRail(maxRail, 45, 30);

    expect(middleFrames.length).toBe(lowFrames.length);
    expect(maxFrames.length).toBe(middleFrames.length);
    expect(maxBearingDelta(middleFrames)).toBeLessThan(maxBearingDelta(lowFrames) * 0.55);
    expect(maxBearingDelta(maxFrames)).toBeLessThan(maxBearingDelta(middleFrames) * 0.95);
    expect(maxZoomDelta(maxFrames)).toBeLessThan(maxZoomDelta(middleFrames));
  });

  it('uses more overview-like framing for short replays than long replays', () => {
    const path = buildReplayPath({
      coordinates: [
        [8, 47],
        [8.01, 47.005],
        [8.02, 47],
        [8.03, 47.005],
      ],
    });

    const shortRail = ReplayCameraRailPlanner.build({
      path,
      durationSeconds: 15,
      preset: 'follow',
      smoothness: 75,
    });
    const longRail = ReplayCameraRailPlanner.build({
      path,
      durationSeconds: 90,
      preset: 'follow',
      smoothness: 75,
    });

    const shortFrame = shortRail?.sample(7.5);
    const longFrame = longRail?.sample(45);

    expect(shortFrame?.zoom).toBeLessThan(longFrame?.zoom ?? 0);
    expect(shortFrame?.pitch).toBeLessThan(longFrame?.pitch ?? 0);
  });

  it('keeps zoom moving through smooth monotone keyframes', () => {
    const path = buildReplayPath({
      coordinates: [
        [8, 47],
        [8.01, 47.005],
        [8.02, 47],
        [8.03, 47.005],
        [8.04, 47],
      ],
    });
    const viewport = {
      width: 1280,
      height: 720,
      paddingTop: 32,
      paddingRight: 32,
      paddingBottom: 350,
      paddingLeft: 32,
    };
    const options = {
      path,
      durationSeconds: 45,
      preset: 'follow' as const,
      smoothness: 75,
      viewport,
    };
    const rail = ReplayCameraRailPlanner.build(options);
    const keyframeTimes = buildReplayCameraTargetKeyframes(options).map((keyframe) => keyframe.timeSeconds);

    expect(rail).not.toBeNull();
    expect(maxSmoothKeyframeZoomRate(rail, keyframeTimes, 0.005)).toBeGreaterThan(0.001);
  });

  it('keeps velocity continuous across a short final keyframe segment', () => {
    const durationSeconds = 45;
    const path = buildReplayPath({
      coordinates: [
        [8, 47],
        [8.08, 47],
      ],
    });
    const options = {
      path,
      durationSeconds,
      preset: 'follow' as const,
      smoothness: 75,
    };
    const rail = ReplayCameraRailPlanner.build(options);
    const keyframes = buildReplayCameraTargetKeyframes(options);
    const epsilonSeconds = 0.01;

    expect(keyframes.length).toBeGreaterThan(3);
    const penultimateTimeSeconds = keyframes[keyframes.length - 2].timeSeconds;
    const finalStepSeconds = keyframes[keyframes.length - 1].timeSeconds - penultimateTimeSeconds;
    const previousStepSeconds = penultimateTimeSeconds - keyframes[keyframes.length - 3].timeSeconds;

    expect(finalStepSeconds).toBeLessThan(previousStepSeconds);

    const before = requireFrame(rail, penultimateTimeSeconds - epsilonSeconds);
    const at = requireFrame(rail, penultimateTimeSeconds);
    const after = requireFrame(rail, penultimateTimeSeconds + epsilonSeconds);
    const leftRate = centerRateMetersPerSecond(before, at, epsilonSeconds);
    const rightRate = centerRateMetersPerSecond(at, after, epsilonSeconds);

    expect(Math.max(leftRate, rightRate) / Math.max(Math.min(leftRate, rightRate), 1)).toBeLessThan(1.25);
  });

  it('zooms out and lowers pitch when the replay controls cover the bottom of the map', () => {
    const path = buildReplayPath({
      coordinates: [
        [8, 47],
        [8.01, 47.005],
        [8.02, 47],
        [8.03, 47.005],
        [8.04, 47],
      ],
    });

    const clearRail = ReplayCameraRailPlanner.build({
      path,
      durationSeconds: 45,
      preset: 'follow',
      smoothness: 75,
      viewport: { width: 1280, height: 720, paddingTop: 32, paddingRight: 32, paddingBottom: 32, paddingLeft: 32 },
    });
    const occludedRail = ReplayCameraRailPlanner.build({
      path,
      durationSeconds: 45,
      preset: 'follow',
      smoothness: 75,
      viewport: { width: 1280, height: 720, paddingTop: 32, paddingRight: 32, paddingBottom: 350, paddingLeft: 32 },
    });

    const clearFrame = clearRail?.sample(22.5);
    const occludedFrame = occludedRail?.sample(22.5);

    expect(occludedFrame?.zoom).toBeLessThan(clearFrame?.zoom ?? 0);
    expect(occludedFrame?.pitch).toBeLessThan(clearFrame?.pitch ?? 0);
  });

  it('uses the remaining visible height for route-window fit decisions', () => {
    const path = buildReplayPath({
      coordinates: [
        [8, 47],
        [8, 47.012],
        [8, 47.024],
        [8, 47.036],
      ],
    });

    const tallRail = ReplayCameraRailPlanner.build({
      path,
      durationSeconds: 15,
      preset: 'chase',
      smoothness: 20,
      viewport: { width: 480, height: 840, paddingTop: 24, paddingRight: 24, paddingBottom: 80, paddingLeft: 24 },
    });
    const shortVisibleRail = ReplayCameraRailPlanner.build({
      path,
      durationSeconds: 15,
      preset: 'chase',
      smoothness: 20,
      viewport: { width: 480, height: 840, paddingTop: 24, paddingRight: 24, paddingBottom: 520, paddingLeft: 24 },
    });

    expect(shortVisibleRail?.sample(7.5)?.zoom).toBeLessThan(tallRail?.sample(7.5)?.zoom ?? 0);
  });

  it('plans visibility in screen space for long fast replays under bottom-sheet occlusion', () => {
    const path = buildLongFastReplayPath();
    const viewport = {
      width: 1280,
      height: 720,
      paddingTop: 32,
      paddingRight: 32,
      paddingBottom: 350,
      paddingLeft: 32,
    };

    const lowSmoothRail = ReplayCameraRailPlanner.build({
      path,
      durationSeconds: 15,
      preset: 'follow',
      smoothness: 0,
      viewport,
    });
    const highSmoothRail = ReplayCameraRailPlanner.build({
      path,
      durationSeconds: 15,
      preset: 'follow',
      smoothness: 100,
      viewport,
    });

    const lowSmoothFrames = sampleRail(lowSmoothRail, 15, 30);
    const highSmoothFrames = sampleRail(highSmoothRail, 15, 30);

    expect(path.totalDistanceMeters).toBeGreaterThan(70_000);
    expect(highSmoothFrames.length).toBe(lowSmoothFrames.length);
    expect(minZoom(highSmoothFrames)).toBeLessThan(10.5);
    expect(averageZoom(highSmoothFrames)).toBeLessThan(averageZoom(lowSmoothFrames));
    expect(maxZoomDelta(highSmoothFrames)).toBeLessThan(0.18);
    expect(replayHeadVisibilityViolations(highSmoothRail, path, 15, viewport, 120)).toBe(0);
  });

  it('keeps overview framing readable for short high-smoothing replays', () => {
    const path = buildReplayPath({
      coordinates: [
        [8, 47],
        [8.067, 47],
      ],
    });

    const overviewRail = ReplayCameraRailPlanner.build({
      path,
      durationSeconds: 15,
      preset: 'overview',
      smoothness: 100,
      viewport: { width: 1800, height: 1100 },
    });
    const followRail = ReplayCameraRailPlanner.build({
      path,
      durationSeconds: 15,
      preset: 'follow',
      smoothness: 100,
      viewport: { width: 1800, height: 1100 },
    });

    const overviewFrames = sampleRail(overviewRail, 15, 15);
    const followFrame = followRail?.sample(7.5);

    expect(overviewFrames.length).toBeGreaterThan(5);
    expect(minZoom(overviewFrames)).toBeGreaterThan(13);
    expect(averageZoom(overviewFrames)).toBeLessThan(followFrame?.zoom ?? 0);
  });

  it('is deterministic and does not mutate the replay path', () => {
    const path = buildReplayPath({
      coordinates: [
        [8, 47, 500],
        [8.01, 47.005, 510],
        [8.02, 47, 520],
      ],
    });
    const before = JSON.stringify(path);
    const options = {
      path,
      durationSeconds: 45,
      preset: 'overview' as const,
      smoothness: 60,
    };

    const first = ReplayCameraRailPlanner.build(options);
    const second = ReplayCameraRailPlanner.build(options);

    expect(JSON.stringify(path)).toBe(before);
    expect(first?.sample(0)).toEqual(second?.sample(0));
    expect(first?.sample(12.5)).toEqual(second?.sample(12.5));
    expect(first?.sample(45)).toEqual(second?.sample(45));
  });

  it('returns null for degenerate paths', () => {
    const path = buildReplayPath({
      coordinates: [[8, 47, 500]],
    });

    expect(
      ReplayCameraRailPlanner.build({
        path,
        durationSeconds: 45,
        preset: 'follow',
        smoothness: 75,
      })
    ).toBeNull();
  });
});

function sampleRail(
  rail: ReturnType<typeof ReplayCameraRailPlanner.build>,
  durationSeconds: number,
  sampleCount: number
): ReplayCameraFrame[] {
  const frames: ReplayCameraFrame[] = [];
  for (let index = 0; index <= sampleCount; index += 1) {
    const frame = rail?.sample((durationSeconds * index) / sampleCount);
    if (frame) frames.push(frame);
  }
  return frames;
}

function maxBearingDelta(frames: Array<ReplayCameraFrame | CameraTargetKeyframe>): number {
  let maxDelta = 0;
  for (let index = 1; index < frames.length; index += 1) {
    maxDelta = Math.max(maxDelta, Math.abs(shortestBearingDelta(frames[index - 1].bearing, frames[index].bearing)));
  }
  return maxDelta;
}

function averageZoom(frames: Array<ReplayCameraFrame | CameraTargetKeyframe>): number {
  return frames.reduce((sum, frame) => sum + frame.zoom, 0) / frames.length;
}

function maxZoomDelta(frames: Array<ReplayCameraFrame | CameraTargetKeyframe>): number {
  let maxDelta = 0;
  for (let index = 1; index < frames.length; index += 1) {
    maxDelta = Math.max(maxDelta, Math.abs(frames[index].zoom - frames[index - 1].zoom));
  }
  return maxDelta;
}

function maxBearingRate(frames: Array<ReplayCameraFrame | CameraTargetKeyframe>, durationSeconds: number): number {
  let maxRate = 0;
  const stepSeconds = durationSeconds / Math.max(1, frames.length - 1);
  for (let index = 1; index < frames.length; index += 1) {
    maxRate = Math.max(
      maxRate,
      Math.abs(shortestBearingDelta(frames[index - 1].bearing, frames[index].bearing)) / stepSeconds
    );
  }
  return maxRate;
}

function maxSmoothKeyframeZoomRate(
  rail: ReturnType<typeof ReplayCameraRailPlanner.build>,
  keyframeTimes: number[],
  epsilonSeconds: number
): number {
  let maxRate = 0;
  for (let index = 2; index < keyframeTimes.length - 2; index += 1) {
    const elapsedSeconds = keyframeTimes[index];
    const before = requireFrame(rail, elapsedSeconds - epsilonSeconds);
    const at = requireFrame(rail, elapsedSeconds);
    const after = requireFrame(rail, elapsedSeconds + epsilonSeconds);
    const leftRate = (at.zoom - before.zoom) / epsilonSeconds;
    const rightRate = (after.zoom - at.zoom) / epsilonSeconds;
    if (Math.sign(leftRate) === Math.sign(rightRate)) {
      maxRate = Math.max(maxRate, Math.min(Math.abs(leftRate), Math.abs(rightRate)));
    }
  }
  return maxRate;
}

function requireFrame(
  rail: ReturnType<typeof ReplayCameraRailPlanner.build>,
  elapsedSeconds: number
): ReplayCameraFrame {
  const frame = rail?.sample(elapsedSeconds);
  if (!frame) {
    throw new Error(`Expected replay rail frame at ${elapsedSeconds}s`);
  }
  return frame;
}

function centerRateMetersPerSecond(from: ReplayCameraFrame, to: ReplayCameraFrame, stepSeconds: number): number {
  return centerDistanceMeters(from, to) / stepSeconds;
}

function centerDistanceMeters(from: ReplayCameraFrame, to: ReplayCameraFrame): number {
  const averageLatitudeRadians = (((from.center[1] + to.center[1]) / 2) * Math.PI) / 180;
  const metersPerDegreeLongitude = 111_320 * Math.cos(averageLatitudeRadians);
  const metersPerDegreeLatitude = 111_320;
  return Math.hypot(
    (to.center[0] - from.center[0]) * metersPerDegreeLongitude,
    (to.center[1] - from.center[1]) * metersPerDegreeLatitude
  );
}

function minZoom(frames: Array<ReplayCameraFrame | CameraTargetKeyframe>): number {
  return frames.reduce((min, frame) => Math.min(min, frame.zoom), Number.POSITIVE_INFINITY);
}

function buildLongFastReplayPath() {
  const coordinates: number[][] = [];
  for (let index = 0; index <= 240; index += 1) {
    const progress = index / 240;
    coordinates.push([
      8 + progress * 0.03 + Math.sin(progress * Math.PI * 9) * 0.012,
      47 + progress * 0.64,
      500 + Math.sin(progress * Math.PI * 6) * 70,
    ]);
  }
  return buildReplayPath({ coordinates, originalDurationSeconds: 3_810 });
}

function replayHeadVisibilityViolations(
  rail: ReturnType<typeof ReplayCameraRailPlanner.build>,
  path: ReturnType<typeof buildReplayPath>,
  durationSeconds: number,
  viewport: {
    width: number;
    height: number;
    paddingTop: number;
    paddingRight: number;
    paddingBottom: number;
    paddingLeft: number;
  },
  sampleCount = 30
): number {
  let violations = 0;
  for (let index = 0; index <= sampleCount; index += 1) {
    const elapsedSeconds = (durationSeconds * index) / sampleCount;
    const progress = elapsedSeconds / durationSeconds;
    const frame = rail?.sample(elapsedSeconds);
    const sample = sampleReplayPath(path, progress);
    if (!frame || !sample) {
      violations += 1;
      continue;
    }
    const point = projectReplaySampleForTest(path, frame, sample, viewport);
    if (
      point.x < viewport.paddingLeft ||
      point.x > viewport.width - viewport.paddingRight ||
      point.y < viewport.paddingTop ||
      point.y > viewport.height - viewport.paddingBottom
    ) {
      violations += 1;
    }
  }
  return violations;
}

function projectReplaySampleForTest(
  path: ReturnType<typeof buildReplayPath>,
  frame: ReplayCameraFrame,
  sample: { lng: number; lat: number },
  viewport: {
    width: number;
    height: number;
    paddingTop: number;
    paddingRight: number;
    paddingBottom: number;
    paddingLeft: number;
  }
) {
  const projection = localProjectionForTest(path);
  const center = projection.toLocal({ lng: frame.center[0], lat: frame.center[1] });
  const point = projection.toLocal(sample);
  const metersPerPixel = (40_075_016.686 * projection.latitudeScale) / (512 * 2 ** frame.zoom);
  const bearingRadians = (frame.bearing * Math.PI) / 180;
  const cos = Math.cos(bearingRadians);
  const sin = Math.sin(bearingRadians);
  const dx = point.x - center.x;
  const dy = point.y - center.y;
  const rightMeters = dx * cos - dy * sin;
  const upMeters = dx * sin + dy * cos;
  const visibleLeft = viewport.paddingLeft;
  const visibleRight = viewport.width - viewport.paddingRight;
  const visibleTop = viewport.paddingTop;
  const visibleBottom = viewport.height - viewport.paddingBottom;
  const pitchT = Math.max(0, Math.min(1, (frame.pitch - 35) / (68 - 35)));
  const pitchScale = 1 + (0.62 - 1) * pitchT;
  const visibleHeightRatio = (visibleBottom - visibleTop) / viewport.height;
  const verticalScale = 1 + (pitchScale - 1) * Math.max(0, Math.min(1, visibleHeightRatio));
  return {
    x: (visibleLeft + visibleRight) / 2 + rightMeters / metersPerPixel,
    y: (visibleTop + visibleBottom) / 2 - (upMeters / metersPerPixel) * verticalScale,
  };
}

function localProjectionForTest(path: ReturnType<typeof buildReplayPath>) {
  let minLng = Number.POSITIVE_INFINITY;
  let maxLng = Number.NEGATIVE_INFINITY;
  let minLat = Number.POSITIVE_INFINITY;
  let maxLat = Number.NEGATIVE_INFINITY;
  for (const point of path.points) {
    minLng = Math.min(minLng, point.lng);
    maxLng = Math.max(maxLng, point.lng);
    minLat = Math.min(minLat, point.lat);
    maxLat = Math.max(maxLat, point.lat);
  }
  const originLng = (minLng + maxLng) / 2;
  const originLat = (minLat + maxLat) / 2;
  const latitudeScale = Math.max(Math.cos((originLat * Math.PI) / 180), 0.01);
  return {
    latitudeScale,
    toLocal(point: { lng: number; lat: number }) {
      return {
        x: (((point.lng - originLng) * Math.PI) / 180) * 6_371_000 * latitudeScale,
        y: (((point.lat - originLat) * Math.PI) / 180) * 6_371_000,
      };
    },
  };
}

function shortestBearingDelta(fromDegrees: number, toDegrees: number): number {
  return ((((toDegrees - fromDegrees) % 360) + 540) % 360) - 180;
}
