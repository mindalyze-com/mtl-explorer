import { afterEach, describe, expect, it, vi } from 'vitest';

const { addProtocol, tilev4 } = vi.hoisted(() => ({ addProtocol: vi.fn(), tilev4: vi.fn() }));
vi.mock('maplibre-gl', () => ({ addProtocol }));
vi.mock('pmtiles', () => ({
  Protocol: class {
    tilev4 = tilev4;
  },
}));

import { ensureMapterhornProtocol } from '@/utils/mapterhornProtocol';

ensureMapterhornProtocol();
const handler = addProtocol.mock.calls[0][1];

afterEach(() => {
  vi.unstubAllGlobals();
  tilev4.mockReset();
});

describe('Mapterhorn terrain protocol', () => {
  it('uses archive ranges for low zoom and retains returned elevation and cache metadata', async () => {
    const data = new Uint8Array([1, 2, 3]);
    tilev4.mockResolvedValue({ data, cacheControl: 'max-age=60' });
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const controller = new AbortController();
    expect(await handler({ url: 'mtl-terrain://5/16/11', type: 'arrayBuffer' }, controller)).toEqual({
      data,
      cacheControl: 'max-age=60',
    });
    expect(tilev4).toHaveBeenCalledWith(
      {
        url: 'pmtiles://https://download.mapterhorn.com/planet.pmtiles/5/16/11.webp',
        type: 'arrayBuffer',
      },
      controller
    );
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('replaces omitted ocean tiles with a shared local sea-level tile without failed HTTP tile requests', async () => {
    const data = new Uint8Array([137, 80, 78, 71]).buffer;
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, arrayBuffer: async () => data });
    vi.stubGlobal('fetch', fetchMock);
    tilev4.mockResolvedValue({ data: null });
    for (const path of ['4/6/5', '5/14/10', '5/14/9']) {
      expect(await handler({ url: `mtl-terrain://${path}` }, new AbortController())).toEqual({ data });
    }
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('sea-level-dem.png'));
  });

  it('retains detailed terrain at higher zoom', async () => {
    const data = new Uint8Array([1]).buffer;
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      arrayBuffer: async () => data,
      headers: new Headers({ 'Cache-Control': 'max-age=60' }),
    });
    vi.stubGlobal('fetch', fetchMock);
    const controller = new AbortController();
    expect(await handler({ url: 'mtl-terrain://13/4280/2870' }, controller)).toEqual({
      data,
      cacheControl: 'max-age=60',
      expires: null,
    });
    expect(fetchMock).toHaveBeenCalledWith('https://tiles.mapterhorn.com/13/4280/2870.webp', {
      signal: controller.signal,
    });
    expect(tilev4).not.toHaveBeenCalled();
  });

  it.each([404, 401, 503])(
    'preserves HTTP %i so MapLibre can distinguish missing tiles from failures',
    async (status) => {
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status })));
      await expect(handler({ url: 'mtl-terrain://13/4280/2870' }, new AbortController())).rejects.toMatchObject({
        status,
        message: `Detailed terrain tile failed: HTTP ${status}`,
      });
      expect(tilev4).not.toHaveBeenCalled();
    }
  );

  it('preserves real provider failures and cancels completed archive responses after abort', async () => {
    tilev4.mockRejectedValueOnce(new Error('Archive unavailable'));
    await expect(handler({ url: 'mtl-terrain://4/8/5' }, new AbortController())).rejects.toThrow('Archive unavailable');
    tilev4.mockResolvedValueOnce({ data: new Uint8Array([1]) });
    const controller = new AbortController();
    controller.abort();
    await expect(handler({ url: 'mtl-terrain://4/8/5' }, controller)).rejects.toMatchObject({ name: 'AbortError' });
  });
});
