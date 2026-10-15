import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { StatusRequestPausedError } from '@/utils/statusPolling';

const { getMapServerStatusMock } = vi.hoisted(() => ({
  getMapServerStatusMock: vi.fn(),
}));

vi.mock('x8ing-mtl-api-typescript-fetch', () => ({
  MapConfigDtoTileModeEnum: {
    Local: 'local',
    Remote: 'remote',
  },
  MapServerStatusDtoTileSourceEnum: {
    Local: 'local',
    Public: 'public',
  },
  MapServerStatusControllerApi: class {
    getMapServerStatus = getMapServerStatusMock;
  },
}));

vi.mock('@/utils/openApiClient', () => ({
  getApiConfiguration: vi.fn(() => ({})),
}));

import {
  fetchMapStatus,
  invalidateMapStatus,
  MAP_STATUS_POLL_INTERVAL_MS,
  shouldPollMapStatus,
  shouldTryVectorTiles,
} from '@/utils/mapStatusService';

describe('mapStatusService', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    getMapServerStatusMock.mockReset();
    invalidateMapStatus();
  });

  afterEach(() => {
    invalidateMapStatus();
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  it('coalesces overlapping callers into one network request', async () => {
    let resolveStatus: ((value: { phase: string; ready: boolean }) => void) | undefined;
    getMapServerStatusMock.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveStatus = resolve;
        })
    );

    const first = fetchMapStatus();
    const second = fetchMapStatus();

    expect(getMapServerStatusMock).toHaveBeenCalledTimes(1);
    resolveStatus?.({ phase: 'downloading', ready: false });
    await expect(Promise.all([first, second])).resolves.toEqual([
      { phase: 'downloading', ready: false },
      { phase: 'downloading', ready: false },
    ]);
  });

  it('allows a twenty-second startup status probe without aborting', async () => {
    let requestSignal: AbortSignal | undefined;
    getMapServerStatusMock.mockImplementationOnce(({ signal }: { signal: AbortSignal }) => {
      requestSignal = signal;
      return new Promise((resolve) => setTimeout(() => resolve({ phase: 'ready', ready: true }), 20_000));
    });

    const pending = fetchMapStatus();
    await vi.advanceTimersByTimeAsync(20_000);

    await expect(pending).resolves.toMatchObject({ ready: true });
    expect(requestSignal?.aborted).toBe(false);
  });

  it('aborts an unresponsive status probe at sixty seconds', async () => {
    const aborted = new DOMException('Status probe timed out', 'AbortError');
    let requestSignal: AbortSignal | undefined;
    getMapServerStatusMock.mockImplementationOnce(({ signal }: { signal: AbortSignal }) => {
      requestSignal = signal;
      return new Promise((_resolve, reject) => signal.addEventListener('abort', () => reject(aborted)));
    });

    const pending = fetchMapStatus();
    const rejected = expect(pending).rejects.toBe(aborted);
    await vi.advanceTimersByTimeAsync(59_999);
    expect(requestSignal?.aborted).toBe(false);

    await vi.advanceTimersByTimeAsync(1);
    await rejected;
    expect(requestSignal?.aborted).toBe(true);
  });

  it('allows at most one automatic request per ten-second window', async () => {
    getMapServerStatusMock.mockResolvedValue({ phase: 'downloading', ready: false });

    await fetchMapStatus();
    await vi.advanceTimersByTimeAsync(MAP_STATUS_POLL_INTERVAL_MS - 1);
    await fetchMapStatus();
    expect(getMapServerStatusMock).toHaveBeenCalledTimes(1);

    await vi.advanceTimersByTimeAsync(1);
    await fetchMapStatus();
    expect(getMapServerStatusMock).toHaveBeenCalledTimes(2);
  });

  it('lets an explicit refresh bypass the cached interval', async () => {
    getMapServerStatusMock.mockResolvedValue({ phase: 'ready', ready: true, tileSource: 'public' });

    await fetchMapStatus();
    await fetchMapStatus({ force: true });

    expect(getMapServerStatusMock).toHaveBeenCalledTimes(2);
  });

  it('backs failed automatic requests off from ten to thirty seconds', async () => {
    const failure = new Error('status unavailable');
    getMapServerStatusMock
      .mockRejectedValueOnce(failure)
      .mockRejectedValueOnce(failure)
      .mockResolvedValue({ phase: 'ready', ready: true, tileSource: 'public' });

    await expect(fetchMapStatus()).rejects.toBe(failure);
    await vi.advanceTimersByTimeAsync(MAP_STATUS_POLL_INTERVAL_MS - 1);
    await expect(fetchMapStatus()).rejects.toBe(failure);
    expect(getMapServerStatusMock).toHaveBeenCalledTimes(1);

    await vi.advanceTimersByTimeAsync(1);
    await expect(fetchMapStatus()).rejects.toBe(failure);
    expect(getMapServerStatusMock).toHaveBeenCalledTimes(2);

    await vi.advanceTimersByTimeAsync(MAP_STATUS_POLL_INTERVAL_MS * 2 - 1);
    await expect(fetchMapStatus()).rejects.toBe(failure);
    expect(getMapServerStatusMock).toHaveBeenCalledTimes(2);

    await vi.advanceTimersByTimeAsync(1);
    await expect(fetchMapStatus()).resolves.toMatchObject({ ready: true });
    expect(getMapServerStatusMock).toHaveBeenCalledTimes(3);
  });

  it('does not make automatic requests while the page is hidden', async () => {
    let visibilityState: DocumentVisibilityState = 'visible';
    vi.spyOn(document, 'visibilityState', 'get').mockImplementation(() => visibilityState);
    getMapServerStatusMock.mockResolvedValue({ phase: 'ready', ready: true, tileSource: 'public' });

    await fetchMapStatus();
    visibilityState = 'hidden';
    await vi.advanceTimersByTimeAsync(MAP_STATUS_POLL_INTERVAL_MS);
    await fetchMapStatus();
    expect(getMapServerStatusMock).toHaveBeenCalledTimes(1);

    visibilityState = 'visible';
    await fetchMapStatus();
    expect(getMapServerStatusMock).toHaveBeenCalledTimes(2);
  });

  it('identifies an uncached hidden-page pause and fetches normally when visible', async () => {
    let visibilityState: DocumentVisibilityState = 'hidden';
    vi.spyOn(document, 'visibilityState', 'get').mockImplementation(() => visibilityState);
    getMapServerStatusMock.mockResolvedValue({ phase: 'ready', ready: true });
    await expect(fetchMapStatus()).rejects.toBeInstanceOf(StatusRequestPausedError);
    expect(getMapServerStatusMock).not.toHaveBeenCalled();
    visibilityState = 'visible';
    await expect(fetchMapStatus()).resolves.toMatchObject({ ready: true });
    expect(getMapServerStatusMock).toHaveBeenCalledOnce();
  });

  it('invalidates cached status after a source or archive change', async () => {
    getMapServerStatusMock
      .mockResolvedValueOnce({ phase: 'ready', ready: true, tileSource: 'public', archiveId: 'public' })
      .mockResolvedValueOnce({ phase: 'ready', ready: true, tileSource: 'local', archiveId: 'local' });

    await expect(fetchMapStatus()).resolves.toMatchObject({ archiveId: 'public' });
    invalidateMapStatus();
    await expect(fetchMapStatus()).resolves.toMatchObject({ archiveId: 'local' });

    expect(getMapServerStatusMock).toHaveBeenCalledTimes(2);
  });
});

describe('map status polling policy', () => {
  it('tries vector tiles when status is missing or unreachable, but respects a confirmed download', () => {
    expect(shouldTryVectorTiles()).toBe(true);
    expect(shouldTryVectorTiles({ phase: 'unreachable', ready: false })).toBe(true);
    expect(shouldTryVectorTiles({ phase: 'ready', ready: true })).toBe(true);
    expect(shouldTryVectorTiles({ phase: 'downloading', ready: false })).toBe(false);
  });

  it('polls local downloads and hosted fallback, but stops for a ready local source', () => {
    expect(
      shouldPollMapStatus({
        tileMode: 'local',
        status: { phase: 'downloading', ready: false, tileSource: 'local' },
      })
    ).toBe(true);
    expect(
      shouldPollMapStatus({
        tileMode: 'local',
        status: { phase: 'ready', ready: true, tileSource: 'public' },
      })
    ).toBe(true);
    expect(
      shouldPollMapStatus({
        tileMode: 'local',
        status: { phase: 'ready', ready: true, tileSource: 'local' },
      })
    ).toBe(false);
  });

  it('does not poll in offline or remote raster modes', () => {
    expect(shouldPollMapStatus({ tileMode: 'local', offline: true, status: { ready: false } })).toBe(false);
    expect(shouldPollMapStatus({ tileMode: 'local', remoteRasterOverride: true, status: { ready: false } })).toBe(
      false
    );
    expect(shouldPollMapStatus({ tileMode: 'remote', status: { ready: false } })).toBe(false);
  });
});
