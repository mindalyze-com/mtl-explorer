import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fetchTrackIdsWithinDistanceOfPoint } from '@/utils/ServiceHelper';
import { TrackProcessingStatusDtoStateEnum } from 'x8ing-mtl-api-typescript-fetch';

const mocks = vi.hoisted(() => ({ request: vi.fn(), activeFilter: vi.fn(), configuration: { test: true } }));
vi.mock('@/stores/filterStore', () => ({ loadActiveFilterRequest: mocks.activeFilter }));
vi.mock('@/utils/openApiClient', () => ({ getApiConfiguration: () => mocks.configuration }));
vi.mock('x8ing-mtl-api-typescript-fetch', async (importOriginal) => ({
  ...(await importOriginal<typeof import('x8ing-mtl-api-typescript-fetch')>()),
  TracksControllerApi: class {
    getTrackIdsWithinDistanceOfPoint = mocks.request;
  },
}));

describe('generated nearby track API', () => {
  beforeEach(() => vi.clearAllMocks());

  it('sends the active filter, zoom-derived tolerance and abort signal in one request', async () => {
    const filterParams = { trackIds: [42, 43] };
    mocks.activeFilter.mockResolvedValue({ filterName: 'ActivitiesByMainTypes', filterParams });
    const response = {
      trackIds: [],
      processing: { state: TrackProcessingStatusDtoStateEnum.Running, stages: [], failedGpsFiles: 0 },
    };
    mocks.request.mockResolvedValue(response);
    const signal = new AbortController().signal;

    await expect(fetchTrackIdsWithinDistanceOfPoint(8.4, 47.5, 233, signal)).resolves.toEqual(response);

    expect(mocks.request).toHaveBeenCalledExactlyOnceWith(
      {
        longitude: 8.4,
        latitude: 47.5,
        distanceInMeter: 233,
        filterName: 'ActivitiesByMainTypes',
        filterParamsRequest: filterParams,
      },
      { signal }
    );
  });
});
