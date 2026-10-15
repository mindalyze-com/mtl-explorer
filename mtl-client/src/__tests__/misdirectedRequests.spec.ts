import axios, { AxiosError, type AxiosAdapter } from 'axios';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TracksControllerApi } from 'x8ing-mtl-api-typescript-fetch';
import { apiClient } from '@/utils/apiClient';
import { clearToken } from '@/utils/auth';
import { getApiConfiguration } from '@/utils/openApiClient';
import { getMediaTrendItems, getMediaTrends } from '@/repositories/mediaRepository';

describe('bounded misdirected-read recovery', () => {
  beforeEach(() => clearToken());
  afterEach(() => vi.unstubAllGlobals());

  it('retries the generated statistics ID query once after 421', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(new Response(null, { status: 421 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ trackVersions: {} }), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);
    await new TracksControllerApi(getApiConfiguration()).getTracksSimplified1({ mode: 'ids' });
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[1]![0]).toBe(fetchMock.mock.calls[0]![0]);
    expect(fetchMock.mock.calls[1]![1]).toMatchObject({ cache: 'no-store' });
  });

  it('propagates a persistent generated 421 after one retry', async () => {
    const fetchMock = vi.fn().mockImplementation(async () => new Response(null, { status: 421 }));
    vi.stubGlobal('fetch', fetchMock);
    await expect(
      new TracksControllerApi(getApiConfiguration()).getTracksSimplified1({ mode: 'ids' })
    ).rejects.toMatchObject({ response: { status: 421 } });
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('does not replay a canceled generated request', async () => {
    const controller = new AbortController();
    const fetchMock = vi.fn().mockImplementation(async () => {
      controller.abort();
      return new Response(null, { status: 421 });
    });
    vi.stubGlobal('fetch', fetchMock);
    await expect(
      new TracksControllerApi(getApiConfiguration()).getTracksSimplified1(
        { mode: 'ids' },
        { signal: controller.signal }
      )
    ).rejects.toMatchObject({ response: { status: 421 } });
    expect(fetchMock).toHaveBeenCalledOnce();
  });

  it.each(['api/tracks/get-track-overview', 'api/tracks/get-track-statistics?groupByDateFormat=YYYY-Q'])(
    'retries the Axios read %s once and preserves its body',
    async (path) => {
      let attempts = 0;
      const adapter: AxiosAdapter = vi.fn(async (config) => {
        attempts += 1;
        const response = {
          config,
          data: { recovered: true },
          headers: {},
          status: attempts === 1 ? 421 : 200,
          statusText: '',
        };
        if (attempts === 1)
          throw new AxiosError('Misdirected', axios.AxiosError.ERR_BAD_RESPONSE, config, undefined, response);
        return response;
      });
      const response = await apiClient.post(path, [1], { adapter });
      expect(response.data).toEqual({ recovered: true });
      expect(adapter).toHaveBeenCalledTimes(2);
      expect(vi.mocked(adapter).mock.calls.map(([config]) => config.data)).toEqual(['[1]', '[1]']);
    }
  );

  it.each([
    ['trends', () => getMediaTrends({ grouping: 'QUARTER', scope: 'ALL_INDEXED', trackIds: [1] })],
    [
      'trends/items',
      () =>
        getMediaTrendItems({
          grouping: 'QUARTER',
          scope: 'ALL_INDEXED',
          trackIds: [1],
          bucketKey: '2026-Q1',
          page: 1,
        }),
    ],
  ] as const)('retries the generated media %s read once and preserves its body', async (path, load) => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(new Response(null, { status: 421 }))
      .mockResolvedValueOnce(new Response('{}', { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);
    await load();
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[0]![0]).toContain(`/api/media/${path}`);
    expect(fetchMock.mock.calls[1]![0]).toBe(fetchMock.mock.calls[0]![0]);
    expect(fetchMock.mock.calls[1]![1]).toMatchObject({
      method: 'POST',
      body: fetchMock.mock.calls[0]![1].body,
      cache: 'no-store',
    });
    expect(JSON.parse(fetchMock.mock.calls[1]![1].body)).toMatchObject({
      grouping: 'QUARTER',
      scope: 'ALL_INDEXED',
      trackIds: [1],
    });
  });

  it.each(['api/tracks/get-track-overview', 'api/tracks/get-track-statistics'])(
    'stops after one retry of a persistent Axios 421 for %s',
    async (path) => {
      const adapter: AxiosAdapter = vi.fn(async (config) => {
        throw new AxiosError('Misdirected', axios.AxiosError.ERR_BAD_RESPONSE, config, undefined, {
          config,
          data: {},
          headers: {},
          status: 421,
          statusText: '',
        });
      });
      await expect(apiClient.post(path, [1], { adapter })).rejects.toMatchObject({
        response: { status: 421 },
      });
      expect(adapter).toHaveBeenCalledTimes(2);
    }
  );

  it.each(['/api/auth/login', '/api/tracks/import', '/api/config', '/api/tracks/1'])(
    'never replays mutation POST %s after 421',
    async (path) => {
      const adapter: AxiosAdapter = vi.fn(async (config) => {
        throw new AxiosError('Misdirected', axios.AxiosError.ERR_BAD_RESPONSE, config, undefined, {
          config,
          data: {},
          headers: {},
          status: 421,
          statusText: '',
        });
      });
      await expect(apiClient.post(path, {}, { adapter })).rejects.toMatchObject({ response: { status: 421 } });
      expect(adapter).toHaveBeenCalledOnce();
    }
  );
});
