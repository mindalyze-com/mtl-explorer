import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  SERVICE_WORKER_UPDATE_CHECK_COOLDOWN_MS,
  SERVICE_WORKER_UPDATE_CHECK_INTERVAL_MS,
  startServiceWorkerUpdateChecks,
} from '@/utils/serviceWorkerUpdateChecks';

describe('startServiceWorkerUpdateChecks', () => {
  let visibilityState: DocumentVisibilityState;

  beforeEach(() => {
    vi.useFakeTimers();
    visibilityState = 'visible';
    vi.spyOn(document, 'visibilityState', 'get').mockImplementation(() => visibilityState);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('checks immediately and at the configured interval', async () => {
    const update = vi.fn().mockResolvedValue(undefined);
    const stop = startServiceWorkerUpdateChecks({ update });

    expect(update).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(SERVICE_WORKER_UPDATE_CHECK_INTERVAL_MS);
    expect(update).toHaveBeenCalledTimes(2);

    stop();
    await vi.advanceTimersByTimeAsync(SERVICE_WORKER_UPDATE_CHECK_INTERVAL_MS);
    expect(update).toHaveBeenCalledTimes(2);
  });

  it('checks when the app becomes visible or focused without duplicate requests', async () => {
    let now = 1_000;
    const update = vi.fn().mockResolvedValue(undefined);
    const stop = startServiceWorkerUpdateChecks({ update }, { now: () => now });
    await Promise.resolve();

    visibilityState = 'hidden';
    document.dispatchEvent(new Event('visibilitychange'));
    expect(update).toHaveBeenCalledTimes(1);

    now += SERVICE_WORKER_UPDATE_CHECK_COOLDOWN_MS;
    visibilityState = 'visible';
    document.dispatchEvent(new Event('visibilitychange'));
    window.dispatchEvent(new Event('focus'));
    expect(update).toHaveBeenCalledTimes(2);

    stop();
  });

  it('checks immediately when the network returns', async () => {
    const update = vi.fn().mockResolvedValue(undefined);
    const stop = startServiceWorkerUpdateChecks({ update });
    await Promise.resolve();

    window.dispatchEvent(new Event('online'));
    expect(update).toHaveBeenCalledTimes(2);

    stop();
  });

  it('does not overlap update requests', async () => {
    let finishUpdate: (() => void) | undefined;
    const update = vi.fn(
      () =>
        new Promise<void>((resolve) => {
          finishUpdate = resolve;
        })
    );
    const stop = startServiceWorkerUpdateChecks({ update });

    window.dispatchEvent(new Event('online'));
    await vi.advanceTimersByTimeAsync(SERVICE_WORKER_UPDATE_CHECK_INTERVAL_MS);
    expect(update).toHaveBeenCalledTimes(1);

    finishUpdate?.();
    await Promise.resolve();
    stop();
  });
});
