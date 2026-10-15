import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AxiosError } from 'axios';

const apiClientMock = vi.hoisted(() => ({
  post: vi.fn(),
}));

vi.mock('@/utils/apiClient', () => ({
  apiClient: apiClientMock,
}));

import { fetchResolveFilter } from '@/utils/filterApi';
vi.mock('@/utils/safeLogging', () => ({ logSanitizedError: vi.fn() }));
const browserTimeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;

describe('filterApi', () => {
  beforeEach(() => {
    localStorage.clear();
    apiClientMock.post.mockReset();
    apiClientMock.post.mockResolvedValue({
      data: {
        resultEntries: [],
        trackVersions: {},
        filterGroups: {},
        standardFilterCount: 0,
      },
    });
  });

  it('requests indexed-file metadata with full tracks for Review search', async () => {
    await fetchResolveFilter(7, {}, true);

    expect(apiClientMock.post).toHaveBeenCalledWith(
      'api/filter/resolve/7?includeGPSTrack=true&includeGPSTrackFile=true',
      { timeZone: browserTimeZone }
    );
  });

  it('keeps preview resolution lightweight', async () => {
    await fetchResolveFilter(7, {}, false);

    expect(apiClientMock.post).toHaveBeenCalledWith(
      'api/filter/resolve/7?includeGPSTrack=false&includeGPSTrackFile=false',
      { timeZone: browserTimeZone }
    );
  });

  it('preserves the original transport error, stack and HTTP response', async () => {
    const failure = new AxiosError('Synthetic failure', 'ERR_BAD_RESPONSE', undefined, undefined, {
      status: 503,
    } as never);
    failure.stack = 'AxiosError: Synthetic failure\n at original (bundle.js:4:5)';
    apiClientMock.post.mockRejectedValueOnce(failure);
    await expect(fetchResolveFilter(7, {})).rejects.toBe(failure);
    expect(failure.response?.status).toBe(503);
  });

  it('ignores a saved Exercise toggle selection while preserving normal filter criteria', async () => {
    localStorage.setItem('mtl.google-health.track-view-scope', 'GOOGLE_HEALTH_EXERCISE');
    const params = { stringParams: { SEARCH_WORD: 'ridge' } };

    await fetchResolveFilter(7, params);

    expect(apiClientMock.post).toHaveBeenCalledWith(
      'api/filter/resolve/7?includeGPSTrack=false&includeGPSTrackFile=false',
      { ...params, timeZone: browserTimeZone }
    );
    expect(params).toEqual({ stringParams: { SEARCH_WORD: 'ridge' } });
  });
});
