import { STATUS_POLL_INTERVAL_MS } from './statusPolling';

/** A stable delay buffers status samples without assuming their actual cadence. */
export const COUNT_PLAYBACK_DELAY_MS = STATUS_POLL_INTERVAL_MS;
export const COUNTER_RENDER_INTERVAL_MS = 400;
const MAX_COUNT_HISTORY_SAMPLES = 64;

export const GOOGLE_HEALTH_COUNT_KEY = 'google-health:records-processed';

export interface CountSample {
  readonly sampledAt: number;
  readonly value: number;
}

export interface CountAnimation {
  /** Always contains at least one sample, in the client clock's time coordinates. */
  readonly samples: readonly CountSample[];
  readonly resetKey?: unknown;
}

export interface CountAnimationOptions {
  resetKey?: unknown;
  resetOnDecrease?: boolean;
  animate?: boolean;
  /** Receipt time bounds pruning when the server clock mapping puts samples ahead of now. */
  observedAt?: number;
}

export function processingCountKey(scope: 'indexer' | 'job' | 'task', id: string | undefined, metric: string): string {
  return `${scope}:${id ?? ''}:${metric}`;
}

export function latestCountSample(animation: CountAnimation): CountSample {
  return animation.samples[animation.samples.length - 1]!;
}

/** Interpolate the recorded timeline at a fixed delay behind the local clock. */
export function countAnimationValueAt(animation: CountAnimation, now: number): number {
  const simulationTime = now - COUNT_PLAYBACK_DELAY_MS;
  let previous = animation.samples[0]!;
  if (simulationTime <= previous.sampledAt) return previous.value;
  for (let index = 1; index < animation.samples.length; index++) {
    const next = animation.samples[index]!;
    if (simulationTime < next.sampledAt) {
      const fraction = (simulationTime - previous.sampledAt) / (next.sampledAt - previous.sampledAt);
      return Math.floor(previous.value + (next.value - previous.value) * fraction);
    }
    previous = next;
  }
  return previous.value;
}

export function countAnimationHasPendingChanges(animation: CountAnimation, now: number): boolean {
  const simulationTime = now - COUNT_PLAYBACK_DELAY_MS;
  const current = countAnimationValueAt(animation, now);
  return animation.samples.some((sample) => sample.sampledAt > simulationTime && sample.value !== current);
}

export function observeCountAnimation(
  previous: CountAnimation | undefined,
  value: number | null | undefined,
  sampledAt: number,
  options: CountAnimationOptions = {}
): CountAnimation | undefined {
  if (typeof value !== 'number' || !Number.isFinite(value) || !Number.isFinite(sampledAt)) return undefined;
  const next = Math.max(0, Math.floor(value));
  const sameSeries = previous !== undefined && previous.resetKey === options.resetKey;
  const latest = previous && latestCountSample(previous);
  if (latest && sampledAt < latest.sampledAt) return previous;
  let samples: CountSample[] = [{ sampledAt, value: next }];
  if (previous && sameSeries && !(options.resetOnDecrease && next < latest!.value) && options.animate !== false) {
    // A repeated timestamp replaces that observation; an older response is ignored.
    samples = [...previous.samples.filter((sample) => sample.sampledAt < sampledAt), ...samples];
    const cutoff = Math.min(sampledAt, options.observedAt ?? sampledAt) - COUNT_PLAYBACK_DELAY_MS;
    const firstBuffered = samples.findIndex((sample) => sample.sampledAt >= cutoff);
    // Keep the preceding endpoint, including when the newest sample follows a long gap.
    samples = samples.slice(Math.max(0, firstBuffered - 1, samples.length - MAX_COUNT_HISTORY_SAMPLES));
  }
  return {
    samples,
    resetKey: options.resetKey,
  };
}
