import { afterEach, describe, expect, it, vi } from 'vitest';
import { CachingFetchSource } from '@/utils/cachingPmtilesSource';

function rangeResponse(bytes: number[]): Response {
  return new Response(new Uint8Array(bytes), {
    status: 206,
    headers: {
      'Cache-Control': 'public, max-age=2678400, immutable',
      'Content-Length': String(bytes.length),
      ETag: '"archive-etag"',
    },
  });
}

function rangeNotSatisfiableResponse(actualLength: number): Response {
  return new Response(null, {
    status: 416,
    headers: {
      'Content-Range': `bytes */${actualLength}`,
    },
  });
}

function cacheModeForCall(fetchMock: ReturnType<typeof vi.fn>, callIndex: number): RequestCache | undefined {
  return (fetchMock.mock.calls[callIndex]?.[1] as RequestInit | undefined)?.cache;
}

function rangeHeaderForCall(fetchMock: ReturnType<typeof vi.fn>, callIndex: number): string | null {
  const init = fetchMock.mock.calls[callIndex]?.[1] as RequestInit | undefined;
  return init?.headers instanceof Headers ? init.headers.get('Range') : null;
}

describe('CachingFetchSource', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('retries without HTTP cache after a force-cache range request fails', async () => {
    const fetchMock = vi
      .fn()
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
      .mockResolvedValueOnce(rangeResponse([1, 2]))
      .mockResolvedValueOnce(rangeResponse([3, 4]));
    vi.stubGlobal('fetch', fetchMock);
    const source = new CachingFetchSource('/tiles/planet.pmtiles?test=retry-fetch');

    const first = await source.getBytes(10, 2);
    const second = await source.getBytes(12, 2);

    expect(first.data.byteLength).toBe(2);
    expect(second.data.byteLength).toBe(2);
    expect(cacheModeForCall(fetchMock, 0)).toBe('force-cache');
    expect(cacheModeForCall(fetchMock, 1)).toBe('no-store');
    expect(cacheModeForCall(fetchMock, 2)).toBe('no-store');
    expect(rangeHeaderForCall(fetchMock, 0)).toBe('bytes=10-11');
    expect(rangeHeaderForCall(fetchMock, 1)).toBe('bytes=10-11');
    expect(rangeHeaderForCall(fetchMock, 2)).toBe('bytes=12-13');
  });

  it('also retries without HTTP cache when reading the cached body fails', async () => {
    const brokenCachedResponse = {
      status: 206,
      headers: new Headers({
        'Content-Length': '2',
        ETag: '"archive-etag"',
      }),
      arrayBuffer: vi.fn().mockRejectedValue(new TypeError('cache read failed')),
    } as unknown as Response;
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(brokenCachedResponse)
      .mockResolvedValueOnce(rangeResponse([5, 6]));
    vi.stubGlobal('fetch', fetchMock);
    const source = new CachingFetchSource('/tiles/planet.pmtiles?test=retry-body');

    const result = await source.getBytes(20, 2);

    expect(result.data.byteLength).toBe(2);
    expect(cacheModeForCall(fetchMock, 0)).toBe('force-cache');
    expect(cacheModeForCall(fetchMock, 1)).toBe('no-store');
    expect(rangeHeaderForCall(fetchMock, 1)).toBe('bytes=20-21');
  });

  it('does not retry aborted range requests', async () => {
    const abortError = new DOMException('The operation was aborted.', 'AbortError');
    const fetchMock = vi.fn().mockRejectedValueOnce(abortError);
    vi.stubGlobal('fetch', fetchMock);
    const source = new CachingFetchSource('/tiles/planet.pmtiles?test=abort');

    await expect(source.getBytes(30, 2)).rejects.toBe(abortError);

    expect(fetchMock).toHaveBeenCalledOnce();
    expect(cacheModeForCall(fetchMock, 0)).toBe('force-cache');
  });

  it('uses reload for the smaller-than-probe 416 retry while force-cache is enabled', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(rangeNotSatisfiableResponse(2))
      .mockResolvedValueOnce(rangeResponse([7, 8]));
    vi.stubGlobal('fetch', fetchMock);
    const source = new CachingFetchSource('/tiles/planet.pmtiles?test=small-probe');

    const result = await source.getBytes(0, 16384);

    expect(result.data.byteLength).toBe(2);
    expect(cacheModeForCall(fetchMock, 0)).toBe('force-cache');
    expect(cacheModeForCall(fetchMock, 1)).toBe('reload');
    expect(rangeHeaderForCall(fetchMock, 1)).toBe('bytes=0-1');
  });

  it('keeps the smaller-than-probe 416 retry on no-store after HTTP cache is disabled', async () => {
    const fetchMock = vi
      .fn()
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
      .mockResolvedValueOnce(rangeResponse([1]))
      .mockResolvedValueOnce(rangeNotSatisfiableResponse(2))
      .mockResolvedValueOnce(rangeResponse([7, 8]));
    vi.stubGlobal('fetch', fetchMock);
    const source = new CachingFetchSource('/tiles/planet.pmtiles?test=small-probe-no-store');

    await source.getBytes(10, 1);
    const result = await source.getBytes(0, 16384);

    expect(result.data.byteLength).toBe(2);
    expect(cacheModeForCall(fetchMock, 0)).toBe('force-cache');
    expect(cacheModeForCall(fetchMock, 1)).toBe('no-store');
    expect(cacheModeForCall(fetchMock, 2)).toBe('no-store');
    expect(cacheModeForCall(fetchMock, 3)).toBe('no-store');
    expect(rangeHeaderForCall(fetchMock, 3)).toBe('bytes=0-1');
  });

  it('does not retry non-fetch errors just because their message mentions cache', async () => {
    const error = new Error('cache quota unavailable');
    const fetchMock = vi.fn().mockRejectedValueOnce(error);
    vi.stubGlobal('fetch', fetchMock);
    const source = new CachingFetchSource('/tiles/planet.pmtiles?test=generic-cache-error');

    await expect(source.getBytes(40, 2)).rejects.toBe(error);

    expect(fetchMock).toHaveBeenCalledOnce();
  });
});
