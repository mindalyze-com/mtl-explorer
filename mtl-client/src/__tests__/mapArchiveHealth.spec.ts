import { afterEach, describe, expect, it, vi } from 'vitest';
import { probeVectorMapArchive } from '@/utils/mapArchiveHealth';

function syntheticArchive(tileType = 1): Uint8Array {
  const bytes = new Uint8Array(128);
  bytes.set(new TextEncoder().encode('PMTiles'));
  const view = new DataView(bytes.buffer);
  view.setUint8(7, 3);
  view.setBigUint64(8, 127n, true);
  view.setBigUint64(16, 1n, true);
  view.setUint8(97, 1);
  view.setUint8(98, 1);
  view.setUint8(99, tileType);
  return bytes;
}

describe('fresh vector archive health check', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('validates PMTiles bytes using an uncached, bounded range request', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(syntheticArchive(), { status: 206 }));
    vi.stubGlobal('fetch', fetchMock);
    await probeVectorMapArchive('/synthetic.pmtiles', new AbortController().signal);
    const [, options] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(options.cache).toBe('no-store');
    expect((options.headers as Headers).get('Range')).toBe('bytes=0-16383');
    expect(options.signal).toBeInstanceOf(AbortSignal);
  });

  it('rejects a successful HTTP response containing invalid archive data', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(new Uint8Array(128), { status: 206 })));
    await expect(probeVectorMapArchive('/invalid.pmtiles', new AbortController().signal)).rejects.toThrow(
      'Wrong magic'
    );
  });

  it('rejects a raster archive so it cannot falsely restore vector', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(syntheticArchive(2), { status: 206 })));
    await expect(probeVectorMapArchive('/raster.pmtiles', new AbortController().signal)).rejects.toThrow(
      'vector tiles'
    );
  });

  it('preserves request cancellation when the map is removed', async () => {
    const controller = new AbortController();
    controller.abort();
    vi.stubGlobal(
      'fetch',
      vi.fn((_url, options: RequestInit) => {
        expect(options.signal?.aborted).toBe(true);
        return Promise.reject(new DOMException('Aborted', 'AbortError'));
      })
    );
    await expect(probeVectorMapArchive('/cancelled.pmtiles', controller.signal)).rejects.toMatchObject({
      name: 'AbortError',
    });
  });
});
