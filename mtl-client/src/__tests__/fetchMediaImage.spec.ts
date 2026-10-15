import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  fetchMediaImage,
  MAX_MEDIA_BUSY_RETRIES,
  MAX_MEDIA_PREVIEW_BUSY_RETRIES,
  MEDIA_BUSY_RETRY_DELAY_MS,
  MEDIA_IMAGE_REQUEST_TIMEOUT_MS,
} from '@/utils/fetchMediaImage';

describe('media image contention', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it('waits through a busy converter and returns the recovered image', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(new Response(null, { status: 503, headers: { 'Retry-After': '2' } }))
      .mockResolvedValueOnce(new Response(null, { status: 503 }))
      .mockResolvedValueOnce(new Response('jpeg', { headers: { 'Content-Type': 'image/jpeg' } }));
    vi.stubGlobal('fetch', fetchMock);
    const request = fetchMediaImage('/photo', new AbortController().signal);
    await vi.advanceTimersByTimeAsync(1_999);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(1 + MEDIA_BUSY_RETRY_DELAY_MS);
    expect(await (await request).text()).toBe('jpeg');
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it('cancels a pending retry when navigation aborts the request', async () => {
    const fetchMock = vi.fn().mockImplementation(async () => new Response(null, { status: 503 }));
    vi.stubGlobal('fetch', fetchMock);
    const controller = new AbortController();
    const result = fetchMediaImage('/photo', controller.signal).catch((error) => error);
    await vi.advanceTimersByTimeAsync(0);
    controller.abort();
    expect(await result).toMatchObject({ name: 'AbortError' });
    await vi.advanceTimersByTimeAsync(10_000);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it.each(['preview', 'thumbnail'] as const)(
    'ends repeated busy %s responses with a recoverable message',
    async (priority) => {
      const fetchMock = vi.fn().mockImplementation(async () => new Response(null, { status: 503 }));
      vi.stubGlobal('fetch', fetchMock);
      const result = fetchMediaImage('/photo', new AbortController().signal, priority).catch((error) => error);
      const retries = priority === 'preview' ? MAX_MEDIA_PREVIEW_BUSY_RETRIES : MAX_MEDIA_BUSY_RETRIES;
      await vi.advanceTimersByTimeAsync(retries * MEDIA_BUSY_RETRY_DELAY_MS);
      expect(await result).toHaveProperty('message', 'Media processing is still busy. Please try again shortly.');
      expect(fetchMock).toHaveBeenCalledTimes(retries + 1);
    }
  );

  it.each([401, 404, 422, 500])('does not retry a non-busy HTTP %i response', async (status) => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status }));
    vi.stubGlobal('fetch', fetchMock);
    await expect(fetchMediaImage('/photo', new AbortController().signal)).rejects.toThrow(String(status));
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('loads a 120-photo burst through one converter without spending retries in the queue', async () => {
    let active = 0;
    let maximum = 0;
    const fetchMock = vi.fn(async () => {
      active++;
      maximum = Math.max(maximum, active);
      await new Promise((resolve) => setTimeout(resolve, 650));
      active--;
      return new Response('jpeg');
    });
    vi.stubGlobal('fetch', fetchMock);
    const burst = Promise.all(
      Array.from({ length: 120 }, (_, i) => fetchMediaImage(`/photo/${i}`, new AbortController().signal))
    );
    await vi.advanceTimersByTimeAsync(120 * 650);
    expect(await burst).toHaveLength(120);
    expect(maximum).toBe(1);
    expect(fetchMock).toHaveBeenCalledTimes(120);
  });

  it('holds the slot until the response body is read and removes canceled queued requests', async () => {
    let completeBody!: (blob: Blob) => void;
    const body = new Promise<Blob>((resolve) => {
      completeBody = resolve;
    });
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce({ ok: true, status: 200, blob: () => body })
      .mockResolvedValue(new Response('next'));
    vi.stubGlobal('fetch', fetchMock);
    const first = fetchMediaImage('/first', new AbortController().signal);
    const canceled = new AbortController();
    const skipped = fetchMediaImage('/canceled', canceled.signal).catch((error) => error);
    const last = fetchMediaImage('/last', new AbortController().signal);
    await vi.advanceTimersByTimeAsync(0);
    canceled.abort();
    expect(await skipped).toMatchObject({ name: 'AbortError' });
    expect(fetchMock).toHaveBeenCalledTimes(1);
    completeBody(new Blob(['first']));
    await Promise.all([first, last]);
    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual(['/first', '/last']);
  });

  it('loads previews before queued thumbnails and keeps thumbnail requests in order', async () => {
    let completeFirst!: (response: Response) => void;
    const firstResponse = new Promise<Response>((resolve) => {
      completeFirst = resolve;
    });
    const fetchMock = vi
      .fn()
      .mockReturnValueOnce(firstResponse)
      .mockImplementation(async () => new Response('jpeg'));
    vi.stubGlobal('fetch', fetchMock);
    const first = fetchMediaImage('/first-thumbnail', new AbortController().signal, 'thumbnail');
    const second = fetchMediaImage('/second-thumbnail', new AbortController().signal, 'thumbnail');
    const third = fetchMediaImage('/third-thumbnail', new AbortController().signal, 'thumbnail');
    const preview = fetchMediaImage('/selected-preview', new AbortController().signal);
    await vi.advanceTimersByTimeAsync(0);
    expect(await (await preview).text()).toBe('jpeg');
    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual(['/first-thumbnail', '/selected-preview']);
    completeFirst(new Response('jpeg'));
    await Promise.all([first, second, third, preview]);
    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      '/first-thumbnail',
      '/selected-preview',
      '/second-thumbnail',
      '/third-thumbnail',
    ]);
  });

  it('reserves converter priority across a long preview retry wait', async () => {
    let converterBusy = true;
    const fetchMock = vi.fn(async (url: string) => {
      if (url === '/slow-thumbnail') {
        await new Promise((resolve) => setTimeout(resolve, 60_000));
        converterBusy = false;
        return new Response('thumbnail');
      }
      if (converterBusy) return new Response(null, { status: 503 });
      return new Response('jpeg');
    });
    vi.stubGlobal('fetch', fetchMock);
    const first = fetchMediaImage('/slow-thumbnail', new AbortController().signal, 'thumbnail');
    const preview = fetchMediaImage('/preview', new AbortController().signal);
    const queued = fetchMediaImage('/queued-thumbnail', new AbortController().signal, 'thumbnail');
    await vi.advanceTimersByTimeAsync(59_000);
    expect(fetchMock.mock.calls.some(([url]) => url === '/queued-thumbnail')).toBe(false);
    await vi.advanceTimersByTimeAsync(1_000);
    await Promise.all([first, preview, queued]);
    expect(fetchMock.mock.calls.at(-1)?.[0]).toBe('/queued-thumbnail');
  });

  it('keeps a thumbnail queued during preview Retry-After and releases it on cancellation', async () => {
    const fetchMock = vi.fn(async (url: string) => {
      if (url === '/busy-preview') return new Response(null, { status: 503 });
      return new Response('jpeg');
    });
    vi.stubGlobal('fetch', fetchMock);
    const controller = new AbortController();
    const preview = fetchMediaImage('/busy-preview', controller.signal).catch((error) => error);
    const thumbnail = fetchMediaImage('/thumbnail', new AbortController().signal, 'thumbnail');
    await vi.advanceTimersByTimeAsync(500);
    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual(['/busy-preview']);
    controller.abort();
    expect(await preview).toMatchObject({ name: 'AbortError' });
    expect(await (await thumbnail).text()).toBe('jpeg');
  });

  it('does not leave thumbnails blocked after an already-aborted preview', async () => {
    const fetchMock = vi.fn(async () => new Response('jpeg'));
    vi.stubGlobal('fetch', fetchMock);
    const controller = new AbortController();
    controller.abort();
    await expect(fetchMediaImage('/aborted-preview', controller.signal)).rejects.toMatchObject({ name: 'AbortError' });
    await fetchMediaImage('/thumbnail', new AbortController().signal, 'thumbnail');
    expect(fetchMock.mock.calls).toHaveLength(1);
  });

  it.each(['headers', 'body'])('releases a stalled %s request and aborts its transport', async (phase) => {
    let completeResponse!: (response: Response) => void;
    let completeBody!: (blob: Blob) => void;
    const response = new Promise<Response>((resolve) => {
      completeResponse = resolve;
    });
    const body = new Promise<Blob>((resolve) => {
      completeBody = resolve;
    });
    const fetchMock = vi
      .fn()
      .mockReturnValueOnce(
        phase === 'headers' ? response : Promise.resolve({ ok: true, status: 200, blob: () => body })
      )
      .mockResolvedValue(new Response('next'));
    vi.stubGlobal('fetch', fetchMock);
    const stalled = fetchMediaImage('/stalled', new AbortController().signal).catch((error) => error);
    const next = fetchMediaImage('/next', new AbortController().signal);
    await vi.advanceTimersByTimeAsync(MEDIA_IMAGE_REQUEST_TIMEOUT_MS - 1);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(1);
    expect(await stalled).toMatchObject({ name: 'TimeoutError' });
    expect(fetchMock.mock.calls[0]![1].signal.aborted).toBe(true);
    expect(await (await next).text()).toBe('next');
    completeResponse(new Response('late'));
    completeBody(new Blob(['late']));
    await vi.advanceTimersByTimeAsync(0);
    expect(vi.getTimerCount()).toBe(0);
  });

  it('starts each request timeout after its queue wait', async () => {
    const responses: ((response: Response) => void)[] = [];
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(() => new Promise<Response>((resolve) => responses.push(resolve)))
    );
    const first = fetchMediaImage('/first', new AbortController().signal);
    const queued = fetchMediaImage('/queued', new AbortController().signal);
    await vi.advanceTimersByTimeAsync(MEDIA_IMAGE_REQUEST_TIMEOUT_MS - 1);
    responses[0]!(new Response('first'));
    await first;
    await vi.advanceTimersByTimeAsync(MEDIA_IMAGE_REQUEST_TIMEOUT_MS - 1);
    responses[1]!(new Response('queued'));
    expect(await (await queued).text()).toBe('queued');
    expect(vi.getTimerCount()).toBe(0);
  });

  it('releases an aborted active request even when the transport response arrives late', async () => {
    let completeFirst!: (response: Response) => void;
    const late = new Promise<Response>((resolve) => {
      completeFirst = resolve;
    });
    const fetchMock = vi.fn().mockReturnValueOnce(late).mockResolvedValue(new Response('next'));
    vi.stubGlobal('fetch', fetchMock);
    const controller = new AbortController();
    const first = fetchMediaImage('/first', controller.signal).catch((error) => error);
    const next = fetchMediaImage('/next', new AbortController().signal);
    await vi.advanceTimersByTimeAsync(0);
    controller.abort();
    expect(await first).toMatchObject({ name: 'AbortError' });
    expect(await (await next).text()).toBe('next');
    completeFirst(new Response('late'));
    await vi.advanceTimersByTimeAsync(0);
  });

  it('lets other photos proceed while a busy request waits for Retry-After', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(new Response(null, { status: 503 }))
      .mockImplementation(async () => new Response('jpeg'));
    vi.stubGlobal('fetch', fetchMock);
    const busy = fetchMediaImage('/busy', new AbortController().signal);
    const ready = fetchMediaImage('/ready', new AbortController().signal);
    await vi.advanceTimersByTimeAsync(0);
    expect(await (await ready).text()).toBe('jpeg');
    await vi.advanceTimersByTimeAsync(MEDIA_BUSY_RETRY_DELAY_MS);
    await busy;
    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual(['/busy', '/ready', '/busy']);
  });
});
