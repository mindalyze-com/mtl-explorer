import 'fake-indexeddb/auto';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const ARCHIVE_URL = 'https://example.test/world-lowzoom.pmtiles';
const ARCHIVE_BYTES = new Uint8Array([1, 2, 3, 4, 5, 6]);

describe('offline low-zoom archive cache', () => {
  let entries: Map<string, ArrayBuffer>;
  let cache: { match: ReturnType<typeof vi.fn>; put: ReturnType<typeof vi.fn> };
  let fetchArchive: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    vi.resetModules();
    entries = new Map();
    cache = {
      match: vi.fn(async (url: string) => (entries.has(url) ? new Response(entries.get(url)) : undefined)),
      put: vi.fn(async (url: string, response: Response) => entries.set(url, await response.arrayBuffer())),
    };
    vi.stubGlobal('caches', { open: vi.fn(async () => cache), delete: vi.fn() });
    fetchArchive = vi.fn(async () => new Response(ARCHIVE_BYTES));
    vi.stubGlobal('fetch', fetchArchive);
    vi.spyOn(console, 'log').mockImplementation(() => {});
    vi.spyOn(console, 'warn').mockImplementation(() => {});
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('downloads once, persists the complete archive, and skips download on subsequent starts', async () => {
    const { ensureLowZoomCached } = await import('@/utils/lowZoomCacheService');
    await ensureLowZoomCached(ARCHIVE_URL);
    await ensureLowZoomCached(ARCHIVE_URL);
    expect(fetchArchive).toHaveBeenCalledOnce();
    expect(fetchArchive).toHaveBeenCalledWith(ARCHIVE_URL, { credentials: 'same-origin' });
    expect(new Uint8Array(entries.get(ARCHIVE_URL)!)).toEqual(ARCHIVE_BYTES);
  });

  it('shares an in-flight download across map rebuilds', async () => {
    let finish!: (response: Response) => void;
    fetchArchive.mockReturnValueOnce(new Promise<Response>((resolve) => (finish = resolve)));
    const { ensureLowZoomCached } = await import('@/utils/lowZoomCacheService');
    const first = ensureLowZoomCached(ARCHIVE_URL);
    const second = ensureLowZoomCached(ARCHIVE_URL);
    expect(second).toBe(first);
    await vi.waitFor(() => expect(fetchArchive).toHaveBeenCalledOnce());
    finish(new Response(ARCHIVE_BYTES));
    await Promise.all([first, second]);
    expect(cache.put).toHaveBeenCalledOnce();
  });

  it('loads cached bytes for offline PMTiles reads without a network request', async () => {
    entries.set(ARCHIVE_URL, ARCHIVE_BYTES.buffer);
    fetchArchive.mockRejectedValue(new Error('Offline'));
    const { loadLowZoomFromCache } = await import('@/utils/lowZoomCacheService');
    const archive = await loadLowZoomFromCache(ARCHIVE_URL);
    expect(archive).not.toBeNull();
    const result = await archive!.source.getBytes(1, 3);
    expect(new Uint8Array(result.data)).toEqual(new Uint8Array([2, 3, 4]));
    expect(fetchArchive).not.toHaveBeenCalled();
  });

  it.each(['network', 'body', 'http'])(
    'keeps failed %s downloads out of the cache and permits retry',
    async (failure) => {
      if (failure === 'network') fetchArchive.mockRejectedValueOnce(new Error('Connection lost'));
      if (failure === 'body') {
        fetchArchive.mockResolvedValueOnce({
          ok: true,
          arrayBuffer: async () => {
            throw new Error('Body interrupted');
          },
        } as Response);
      }
      if (failure === 'http') fetchArchive.mockResolvedValueOnce(new Response('', { status: 503 }));
      const { ensureLowZoomCached } = await import('@/utils/lowZoomCacheService');
      await expect(ensureLowZoomCached(ARCHIVE_URL)).resolves.toBe(ARCHIVE_URL);
      expect(entries.has(ARCHIVE_URL)).toBe(false);
      expect(console.warn).toHaveBeenCalled();
      await ensureLowZoomCached(ARCHIVE_URL);
      expect(fetchArchive).toHaveBeenCalledTimes(2);
      expect(entries.has(ARCHIVE_URL)).toBe(true);
    }
  );

  it('preserves the IndexedDB fallback and offline reads when Cache Storage is unavailable', async () => {
    Reflect.deleteProperty(window, 'caches');
    Reflect.deleteProperty(globalThis, 'caches');
    const { ensureLowZoomCached, loadLowZoomFromCache, clearLowZoomCache } =
      await import('@/utils/lowZoomCacheService');
    await clearLowZoomCache();
    await ensureLowZoomCached(ARCHIVE_URL);
    await ensureLowZoomCached(ARCHIVE_URL);
    expect(fetchArchive).toHaveBeenCalledOnce();
    fetchArchive.mockRejectedValue(new Error('Offline'));
    const archive = await loadLowZoomFromCache(ARCHIVE_URL);
    expect(archive).not.toBeNull();
    expect(new Uint8Array((await archive!.source.getBytes(0, ARCHIVE_BYTES.length)).data)).toEqual(ARCHIVE_BYTES);
    await clearLowZoomCache();
  });
});
