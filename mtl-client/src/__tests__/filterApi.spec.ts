import { beforeEach, describe, expect, it, vi } from 'vitest';

const apiClientMock = vi.hoisted(() => ({
  post: vi.fn(),
}));

vi.mock('@/utils/apiClient', () => ({
  apiClient: apiClientMock,
}));

import { fetchResolveFilter } from '@/utils/filterApi';

describe('filterApi', () => {
  beforeEach(() => {
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
      {}
    );
  });

  it('keeps preview resolution lightweight', async () => {
    await fetchResolveFilter(7, {}, false);

    expect(apiClientMock.post).toHaveBeenCalledWith(
      'api/filter/resolve/7?includeGPSTrack=false&includeGPSTrackFile=false',
      {}
    );
  });
});
