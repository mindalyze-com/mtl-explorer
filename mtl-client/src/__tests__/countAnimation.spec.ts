import { describe, expect, it } from 'vitest';
import {
  COUNT_PLAYBACK_DELAY_MS,
  countAnimationHasPendingChanges,
  countAnimationValueAt,
  latestCountSample,
  observeCountAnimation,
} from '@/utils/countAnimation';

describe('timestamped count playback', () => {
  it('keeps the real rate after a ten-minute gap and the following regular update', () => {
    const baseline = observeCountAnimation(undefined, 1_000, 0)!;
    const returned = observeCountAnimation(baseline, 61_000, 600_000)!;
    expect(countAnimationValueAt(returned, 600_000)).toBe(60_200);
    expect(countAnimationValueAt(returned, 604_000)).toBe(60_600);
    const next = observeCountAnimation(returned, 61_800, 608_000)!;
    expect(countAnimationValueAt(next, 608_000)).toBe(61_000);
    expect(countAnimationValueAt(next, 612_000)).toBe(61_400);
    expect(countAnimationValueAt(next, 616_000)).toBe(61_800);
    expect(countAnimationValueAt(next, 1_000_000)).toBe(61_800);
    expect(countAnimationHasPendingChanges(next, 1_000_000)).toBe(false);
  });

  it('uses actual sample intervals when a delayed update overlaps subsequent updates', () => {
    let history = observeCountAnimation(undefined, 100, 0)!;
    history = observeCountAnimation(history, 500, 40_000)!;
    history = observeCountAnimation(history, 520, 42_000)!;
    history = observeCountAnimation(history, 560, 46_000)!;
    expect(countAnimationValueAt(history, 46_000)).toBe(480);
    expect(countAnimationValueAt(history, 48_000)).toBe(500);
    expect(countAnimationValueAt(history, 49_000)).toBe(510);
    expect(countAnimationValueAt(history, 52_000)).toBe(540);
  });

  it('records unchanged samples rather than extending an earlier growth interval', () => {
    let history = observeCountAnimation(undefined, 100, 0)!;
    history = observeCountAnimation(history, 100, 8_000)!;
    history = observeCountAnimation(history, 200, 16_000)!;
    expect(history.samples.map((sample) => sample.value)).toEqual([100, 100, 200]);
    expect(countAnimationValueAt(history, 16_000)).toBe(100);
    expect(countAnimationValueAt(history, 20_000)).toBe(150);
    history = observeCountAnimation(history, 200, 20_000)!;
    expect(countAnimationValueAt(history, 20_000)).toBe(150);
    expect(countAnimationValueAt(history, 24_000)).toBe(200);
    expect(countAnimationHasPendingChanges(history, 24_000)).toBe(false);
  });

  it('retains enough history to play decreasing and nonmonotonic counters', () => {
    let history = observeCountAnimation(undefined, 100, 0)!;
    history = observeCountAnimation(history, 50, 4_000)!;
    history = observeCountAnimation(history, 100, 8_000)!;
    expect(countAnimationValueAt(history, 8_000)).toBe(100);
    expect(countAnimationHasPendingChanges(history, 8_000)).toBe(true);
    expect(countAnimationValueAt(history, 10_000)).toBe(75);
    expect(countAnimationValueAt(history, 12_000)).toBe(50);
    expect(countAnimationValueAt(history, 14_000)).toBe(75);
  });

  it('settles terminal snapshots and resets new series or cumulative decreases immediately', () => {
    const baseline = observeCountAnimation(undefined, 100, 0, { resetKey: 'first' })!;
    const growing = observeCountAnimation(baseline, 200, 8_000, { resetKey: 'first' })!;
    for (const options of [{ animate: false, resetKey: 'first' }, { resetKey: 'second' }]) {
      const next = observeCountAnimation(growing, 300, 10_000, options)!;
      expect(countAnimationValueAt(next, 10_000)).toBe(300);
      expect(countAnimationHasPendingChanges(next, 10_000)).toBe(false);
    }
    const reset = observeCountAnimation(growing, 0, 10_000, { resetKey: 'first', resetOnDecrease: true })!;
    expect(countAnimationValueAt(reset, 10_000)).toBe(0);
  });

  it('ignores older observations and replaces duplicate timestamps without dividing by zero', () => {
    let history = observeCountAnimation(undefined, 100, 0)!;
    history = observeCountAnimation(history, 200, 8_000)!;
    expect(observeCountAnimation(history, 1_000, 4_000)).toBe(history);
    history = observeCountAnimation(history, 300, 8_000)!;
    expect(history.samples).toHaveLength(2);
    expect(countAnimationValueAt(history, 12_000)).toBe(200);
    expect(countAnimationValueAt(history, 16_000)).toBe(300);
  });

  it('bounds retained history while preserving the preceding interpolation endpoint', () => {
    let history = observeCountAnimation(undefined, 0, 0)!;
    for (let time = 1_000; time <= 1_000_000; time += 1_000) {
      history = observeCountAnimation(history, time / 1_000, time)!;
    }
    expect(history.samples.length).toBeLessThanOrEqual(10);
    expect(history.samples[0]!.sampledAt).toBeLessThan(1_000_000 - COUNT_PLAYBACK_DELAY_MS);
    expect(countAnimationValueAt(history, 1_000_000)).toBe(992);
    expect(latestCountSample(history).value).toBe(1_000);
    for (let time = 1_000_001; time <= 1_001_000; time++) {
      history = observeCountAnimation(history, 1_000, time)!;
    }
    expect(history.samples.length).toBeLessThanOrEqual(64);
  });

  it('keeps the segment at playback time when later responses arrive faster than the initial response', () => {
    let history = observeCountAnimation(undefined, 100, 20_000)!;
    for (let time = 28_000; time <= 44_000; time += 8_000) {
      history = observeCountAnimation(history, 100 + time / 100, time + 20_000, { observedAt: time })!;
    }
    expect(countAnimationValueAt(history, 44_000)).toBe(260);
    expect(countAnimationValueAt(history, 48_000)).toBe(300);
    expect(countAnimationValueAt(history, 52_000)).toBe(340);
  });
});
