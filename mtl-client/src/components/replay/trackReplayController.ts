import { sanitizeReplayTargetDuration } from '@/components/replay/trackReplayPath';

export type ReplayPlaybackStatus = 'idle' | 'playing' | 'paused' | 'finished';

export type ReplayPlaybackFrame = {
  progress: number;
  elapsedReplaySeconds: number;
  elapsedActivitySeconds: number;
  activityDurationSeconds: number;
  speedFactor: number;
  status: ReplayPlaybackStatus;
  targetDurationSeconds: number;
};

export type TrackReplayControllerOptions = {
  targetDurationSeconds: number;
  activityDurationSeconds?: number | null;
  onFrame: (frame: ReplayPlaybackFrame) => void;
  now?: () => number;
  requestFrame?: (callback: FrameRequestCallback) => number;
  cancelFrame?: (handle: number) => void;
};

export class TrackReplayController {
  private readonly onFrame: (frame: ReplayPlaybackFrame) => void;
  private readonly now: () => number;
  private readonly requestFrame: (callback: FrameRequestCallback) => number;
  private readonly cancelFrame: (handle: number) => void;
  private targetDurationSeconds: number;
  private activityDurationSeconds: number;
  private statusValue: ReplayPlaybackStatus = 'idle';
  private progressValue = 0;
  private animationFrameId: number | null = null;
  private startedAtMs = 0;

  constructor(options: TrackReplayControllerOptions) {
    this.onFrame = options.onFrame;
    this.targetDurationSeconds = sanitizeReplayTargetDuration(options.targetDurationSeconds);
    this.activityDurationSeconds = sanitizeActivityDuration(options.activityDurationSeconds, this.targetDurationSeconds);
    this.now = options.now ?? (() => performance.now());
    this.requestFrame = options.requestFrame ?? ((callback) => window.requestAnimationFrame(callback));
    this.cancelFrame = options.cancelFrame ?? ((handle) => window.cancelAnimationFrame(handle));
  }

  get status(): ReplayPlaybackStatus {
    return this.statusValue;
  }

  get progress(): number {
    return this.progressValue;
  }

  get targetDuration(): number {
    return this.targetDurationSeconds;
  }

  get activityDuration(): number {
    return this.activityDurationSeconds;
  }

  play(): void {
    if (this.statusValue === 'playing') return;
    if (this.statusValue === 'finished') this.progressValue = 0;
    this.statusValue = 'playing';
    this.startedAtMs = this.now() - this.progressValue * this.targetDurationSeconds * 1000;
    this.emitFrame();
    this.scheduleTick();
  }

  pause(): void {
    if (this.statusValue !== 'playing') return;
    this.cancelTick();
    this.statusValue = 'paused';
    this.emitFrame();
  }

  toggle(): void {
    if (this.statusValue === 'playing') {
      this.pause();
    } else {
      this.play();
    }
  }

  stop(): void {
    this.cancelTick();
    this.progressValue = 0;
    this.statusValue = 'idle';
    this.emitFrame();
  }

  seek(progress: number): void {
    this.progressValue = clampProgress(progress);
    if (this.statusValue === 'playing') {
      this.startedAtMs = this.now() - this.progressValue * this.targetDurationSeconds * 1000;
    } else if (this.statusValue === 'finished' && this.progressValue < 1) {
      this.statusValue = 'paused';
    }
    this.emitFrame();
  }

  setTargetDuration(seconds: number): void {
    this.targetDurationSeconds = sanitizeReplayTargetDuration(seconds);
    if (this.statusValue === 'playing') {
      this.startedAtMs = this.now() - this.progressValue * this.targetDurationSeconds * 1000;
    }
    this.emitFrame();
  }

  setActivityDuration(seconds: number | null | undefined): void {
    this.activityDurationSeconds = sanitizeActivityDuration(seconds, this.targetDurationSeconds);
    this.emitFrame();
  }

  destroy(): void {
    this.cancelTick();
  }

  private scheduleTick(): void {
    this.cancelTick();
    this.animationFrameId = this.requestFrame(() => this.tick());
  }

  private tick(): void {
    this.animationFrameId = null;
    if (this.statusValue !== 'playing') return;

    const elapsedMs = this.now() - this.startedAtMs;
    this.progressValue = clampProgress(elapsedMs / (this.targetDurationSeconds * 1000));
    if (this.progressValue >= 1) {
      this.statusValue = 'finished';
      this.emitFrame();
      return;
    }

    this.emitFrame();
    this.scheduleTick();
  }

  private emitFrame(): void {
    const speedFactor = this.activityDurationSeconds / this.targetDurationSeconds;
    this.onFrame({
      progress: this.progressValue,
      elapsedReplaySeconds: this.progressValue * this.targetDurationSeconds,
      elapsedActivitySeconds: this.progressValue * this.activityDurationSeconds,
      activityDurationSeconds: this.activityDurationSeconds,
      speedFactor,
      status: this.statusValue,
      targetDurationSeconds: this.targetDurationSeconds,
    });
  }

  private cancelTick(): void {
    if (this.animationFrameId == null) return;
    this.cancelFrame(this.animationFrameId);
    this.animationFrameId = null;
  }
}

function clampProgress(progress: number): number {
  if (!Number.isFinite(progress)) return 0;
  return Math.max(0, Math.min(1, progress));
}

function sanitizeActivityDuration(seconds: unknown, fallbackSeconds: number): number {
  const numeric = Number(seconds);
  if (Number.isFinite(numeric) && numeric > 0) return numeric;
  return fallbackSeconds;
}
