import { describe, expect, it, vi } from 'vitest';
import { TrackReplayController, type ReplayPlaybackFrame } from '@/components/replay/trackReplayController';

describe('TrackReplayController', () => {
  it('plays according to the selected target duration', () => {
    let now = 0;
    let raf: FrameRequestCallback | null = null;
    const frames: ReplayPlaybackFrame[] = [];
    const controller = new TrackReplayController({
      targetDurationSeconds: 10,
      now: () => now,
      requestFrame: (callback) => {
        raf = callback;
        return 1;
      },
      cancelFrame: vi.fn(),
      onFrame: (frame) => frames.push(frame),
    });

    controller.play();
    expect(frames.at(-1)).toMatchObject({ progress: 0, status: 'playing' });

    now = 5_000;
    raf?.(now);
    expect(frames.at(-1)?.progress).toBeCloseTo(0.5);
    expect(frames.at(-1)?.elapsedReplaySeconds).toBeCloseTo(5);
    expect(frames.at(-1)?.elapsedActivitySeconds).toBeCloseTo(5);
    expect(frames.at(-1)?.speedFactor).toBeCloseTo(1);

    now = 10_000;
    raf?.(now);
    expect(frames.at(-1)).toMatchObject({ progress: 1, status: 'finished' });
  });

  it('scales activity elapsed time by the computed acceleration factor', () => {
    let now = 0;
    let raf: FrameRequestCallback | null = null;
    const frames: ReplayPlaybackFrame[] = [];
    const controller = new TrackReplayController({
      targetDurationSeconds: 10,
      activityDurationSeconds: 57,
      now: () => now,
      requestFrame: (callback) => {
        raf = callback;
        return 1;
      },
      cancelFrame: vi.fn(),
      onFrame: (frame) => frames.push(frame),
    });

    controller.play();
    now = 5_000;
    raf?.(now);

    expect(frames.at(-1)?.progress).toBeCloseTo(0.5);
    expect(frames.at(-1)?.elapsedReplaySeconds).toBeCloseTo(5);
    expect(frames.at(-1)?.elapsedActivitySeconds).toBeCloseTo(28.5);
    expect(frames.at(-1)?.activityDurationSeconds).toBeCloseTo(57);
    expect(frames.at(-1)?.speedFactor).toBeCloseTo(5.7);
  });

  it('preserves progress when duration changes during playback', () => {
    let now = 0;
    let raf: FrameRequestCallback | null = null;
    const frames: ReplayPlaybackFrame[] = [];
    const controller = new TrackReplayController({
      targetDurationSeconds: 10,
      now: () => now,
      requestFrame: (callback) => {
        raf = callback;
        return 1;
      },
      cancelFrame: vi.fn(),
      onFrame: (frame) => frames.push(frame),
    });

    controller.play();
    now = 5_000;
    raf?.(now);
    controller.setTargetDuration(20);
    expect(frames.at(-1)).toMatchObject({ progress: 0.5, targetDurationSeconds: 20 });
    expect(frames.at(-1)?.elapsedActivitySeconds).toBeCloseTo(5);

    now = 10_000;
    raf?.(now);
    expect(frames.at(-1)?.progress).toBeCloseTo(0.75);
  });

  it('supports pause, seek, and stop cleanup', () => {
    const cancelFrame = vi.fn();
    const frames: ReplayPlaybackFrame[] = [];
    const controller = new TrackReplayController({
      targetDurationSeconds: 30,
      requestFrame: () => 7,
      cancelFrame,
      onFrame: (frame) => frames.push(frame),
    });

    controller.play();
    controller.pause();
    expect(cancelFrame).toHaveBeenCalledWith(7);
    expect(frames.at(-1)).toMatchObject({ status: 'paused' });

    controller.seek(0.25);
    expect(frames.at(-1)).toMatchObject({ progress: 0.25, status: 'paused' });

    controller.stop();
    expect(frames.at(-1)).toMatchObject({ progress: 0, status: 'idle' });
  });
});
