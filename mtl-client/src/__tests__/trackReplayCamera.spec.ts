import { describe, expect, it } from 'vitest';
import {
  REPLAY_DEFAULT_CAMERA_SMOOTHNESS,
  buildReplayCameraFrame,
  replayCameraSmoothnessLabel,
  replayCameraSmoothnessWeight,
} from '@/components/replay/trackReplayCamera';
import { buildReplayPath } from '@/components/replay/trackReplayPath';

describe('track replay camera', () => {
  it('builds preset-specific camera frames along the route', () => {
    const path = buildReplayPath({
      coordinates: [
        [8, 47, 500],
        [8.02, 47, 520],
      ],
    });

    const follow = buildReplayCameraFrame(path, 0.2, 'follow');
    const overview = buildReplayCameraFrame(path, 0.2, 'overview');

    expect(follow?.center[0]).toBeGreaterThan(8);
    expect(follow?.pitch).toBeGreaterThan(overview?.pitch ?? 90);
    expect(follow?.zoom).toBeGreaterThan(overview?.zoom ?? 99);
  });

  it('smooths camera changes from the previous frame', () => {
    const path = buildReplayPath({
      coordinates: [
        [8, 47, 500],
        [8.02, 47, 520],
      ],
    });
    const previous = { center: [8, 47] as [number, number], bearing: 0, pitch: 0, zoom: 10 };
    const smoothed = buildReplayCameraFrame(path, 0.8, 'follow', previous);
    const explicitDefault = buildReplayCameraFrame(path, 0.8, 'follow', previous, {
      smoothness: REPLAY_DEFAULT_CAMERA_SMOOTHNESS,
    });

    expect(smoothed).toEqual(explicitDefault);
    expect(smoothed?.center[0]).toBeGreaterThan(8);
    expect(smoothed?.center[0]).toBeLessThan(8.02);
    expect(smoothed?.pitch).toBeGreaterThan(0);
    expect(smoothed?.pitch).toBeLessThan(68);
  });

  it('applies stronger smoothing when requested', () => {
    const path = buildReplayPath({
      coordinates: [
        [8, 47, 500],
        [8.02, 47, 520],
      ],
    });
    const previous = { center: [8, 47] as [number, number], bearing: 0, pitch: 0, zoom: 10 };
    const light = buildReplayCameraFrame(path, 0.8, 'follow', previous, { smoothness: 0 });
    const heavy = buildReplayCameraFrame(path, 0.8, 'follow', previous, { smoothness: 100 });

    expect(heavy?.center[0]).toBeGreaterThan(8);
    expect(heavy?.center[0]).toBeLessThan(light?.center[0] ?? 8);
    expect(heavy?.pitch).toBeLessThan(light?.pitch ?? 0);
  });

  it('maps smoothness through one shaped response curve', () => {
    const quarter = replayCameraSmoothnessWeight(25);
    const middle = replayCameraSmoothnessWeight(REPLAY_DEFAULT_CAMERA_SMOOTHNESS);
    const high = replayCameraSmoothnessWeight(75);

    expect(replayCameraSmoothnessWeight(0)).toBe(0);
    expect(replayCameraSmoothnessWeight(100)).toBe(1);
    expect(quarter).toBeGreaterThan(0.25);
    expect(middle).toBeGreaterThan(0.6);
    expect(middle).toBeLessThan(0.7);
    expect(middle - quarter).toBeGreaterThan(0.2);
    expect(high - middle).toBeGreaterThan(0.15);
    expect(replayCameraSmoothnessWeight(undefined)).toBeCloseTo(middle, 6);
    expect(replayCameraSmoothnessLabel(0)).toBe('Direct');
    expect(replayCameraSmoothnessLabel(100)).toBe('Cinema');
  });
});
