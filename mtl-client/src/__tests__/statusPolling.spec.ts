import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createStatusRequestService, STATUS_POLL_INTERVAL_MS } from '@/utils/statusPolling';

describe('shared operational status requests', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(0);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('deduplicates overlapping callers and caches for eight seconds', async () => {
    let resolveRequest!: (value: { ready: boolean }) => void;
    const request = vi.fn(
      () =>
        new Promise<{ ready: boolean }>((resolve) => {
          resolveRequest = resolve;
        })
    );
    const service = createStatusRequestService({ request: () => request() });

    const first = service.fetch();
    const overlapping = service.fetch();
    expect(request).toHaveBeenCalledTimes(1);

    resolveRequest({ ready: true });
    await expect(Promise.all([first, overlapping])).resolves.toEqual([{ ready: true }, { ready: true }]);

    await vi.advanceTimersByTimeAsync(STATUS_POLL_INTERVAL_MS - 1);
    await expect(service.fetch()).resolves.toEqual({ ready: true });
    expect(request).toHaveBeenCalledTimes(1);

    await vi.advanceTimersByTimeAsync(1);
    void service.fetch();
    expect(request).toHaveBeenCalledTimes(2);
  });

  it('holds failed retries until the next interval', async () => {
    const error = new Error('offline');
    const request = vi.fn().mockRejectedValue(error);
    const service = createStatusRequestService({ request });

    await expect(service.fetch()).rejects.toBe(error);
    await expect(service.fetch()).rejects.toBe(error);
    expect(request).toHaveBeenCalledTimes(1);

    await vi.advanceTimersByTimeAsync(STATUS_POLL_INTERVAL_MS);
    await expect(service.fetch()).rejects.toBe(error);
    expect(request).toHaveBeenCalledTimes(2);
  });

  it('invalidates cached and in-flight state', async () => {
    const firstRequest = new Promise<{ value: number }>(() => undefined);
    const request = vi.fn().mockReturnValueOnce(firstRequest).mockResolvedValueOnce({ value: 2 });
    const service = createStatusRequestService({ request });

    void service.fetch();
    service.invalidate();

    await expect(service.fetch()).resolves.toEqual({ value: 2 });
    expect(request).toHaveBeenCalledTimes(2);
  });
});
