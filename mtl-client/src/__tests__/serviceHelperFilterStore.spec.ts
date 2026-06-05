import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { ActiveFilterRequest } from '@/stores/filterStore';

const mocks = vi.hoisted(() => {
  const apiClient = {
    get: vi.fn(),
    post: vi.fn(),
  };
  const getActiveFilterRequest = vi.fn();
  const useFilterStore = vi.fn(() => ({ getActiveFilterRequest }));
  const loadClientFilterConfig = vi.fn();

  return {
    apiClient,
    getActiveFilterRequest,
    useFilterStore,
    loadClientFilterConfig,
  };
});

vi.mock('@/utils/apiClient', () => ({
  apiClient: mocks.apiClient,
}));

vi.mock('@/stores/filterStore', () => ({
  useFilterStore: mocks.useFilterStore,
}));

vi.mock('@/components/filter/FilterService', () => ({
  FilterService: {
    loadClientFilterConfig: mocks.loadClientFilterConfig,
  },
}));

vi.mock('@/utils/safeLogging', () => ({
  logSanitizedError: vi.fn(),
}));

import { FilterService } from '@/components/filter/FilterService';
import {
  fetchStatistics,
  fetchTrackDetailsForCrossingPoints,
  fetchTrackIdsWithinDistanceOfPoint,
} from '@/utils/ServiceHelper';

function filterRequest(filterName: string): ActiveFilterRequest {
  return {
    filterName,
    filterParams: {
      stringParams: { ACTIVITY: 'bike' },
      dateTimeParams: { DATE_TIME_FROM: '2026-01-01T00:00:00' },
    },
  };
}

describe('ServiceHelper active filter resolution', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.apiClient.post.mockResolvedValue({ data: [] });
    mocks.getActiveFilterRequest.mockResolvedValue(filterRequest('StoreFilter'));
    mocks.useFilterStore.mockReturnValue({ getActiveFilterRequest: mocks.getActiveFilterRequest });
    mocks.loadClientFilterConfig.mockResolvedValue({
      filterInfo: { filterConfig: { filterName: 'LegacyFilter' } },
      filterParams: { stringParams: { ACTIVITY: 'run' } },
    });
  });

  it('uses the filter store for statistics loaders before FilterService fallback', async () => {
    await fetchStatistics('yyyy-MM');

    expect(mocks.useFilterStore).toHaveBeenCalledOnce();
    expect(mocks.getActiveFilterRequest).toHaveBeenCalledOnce();
    expect(FilterService.loadClientFilterConfig).not.toHaveBeenCalled();
    expect(mocks.apiClient.post).toHaveBeenCalledWith(
      'api/tracks/get-track-statistics?groupByDateFormat=yyyy-MM&filterName=StoreFilter',
      {
        ACTIVITY: 'bike',
        DATE_TIME_FROM: '2026-01-01T00:00:00',
      }
    );
  });

  it('uses the filter store for distance lookups before FilterService fallback', async () => {
    await fetchTrackIdsWithinDistanceOfPoint(7.4, 46.9, 250);

    expect(mocks.useFilterStore).toHaveBeenCalledOnce();
    expect(mocks.getActiveFilterRequest).toHaveBeenCalledOnce();
    expect(FilterService.loadClientFilterConfig).not.toHaveBeenCalled();
    expect(mocks.apiClient.post).toHaveBeenCalledWith(
      'api/tracks/get-track-ids-within-distance-of-point?filterName=StoreFilter&longitude=7.4&latitude=46.9&distanceInMeter=250',
      {
        ACTIVITY: 'bike',
        DATE_TIME_FROM: '2026-01-01T00:00:00',
      },
      { signal: undefined }
    );
  });

  it('falls back to FilterService when the filter store is unavailable', async () => {
    mocks.useFilterStore.mockImplementation(() => {
      throw new Error('no active pinia');
    });

    await fetchStatistics('yyyy');

    expect(FilterService.loadClientFilterConfig).toHaveBeenCalledOnce();
    expect(mocks.apiClient.post).toHaveBeenCalledWith(
      'api/tracks/get-track-statistics?groupByDateFormat=yyyy&filterName=LegacyFilter',
      { ACTIVITY: 'run' }
    );
  });

  it('keeps crossing point coordinates numeric after generated DTO conversion', async () => {
    mocks.apiClient.post.mockResolvedValueOnce({
      data: {
        crossings: {
          1: {
            gpsTrack: {
              id: 1,
              indexedFile: { name: 'Track 1' },
            },
            crossings: [
              {
                triggerPoint: { name: 'A', coordinate: { x: 8.5, y: 47.5 } },
                gpsTrackDataPoint: {
                  id: 101,
                  pointTimestamp: '2026-06-04T10:00:00.000Z',
                  durationSinceStart: 10,
                  distanceInMeterSinceStart: 100,
                  pointLongLat: { coordinates: [8.5, 47.5, 430] },
                },
              },
              {
                triggerPoint: { name: 'B', coordinate: { x: 8.51, y: 47.51 } },
                gpsTrackDataPoint: {
                  id: 102,
                  pointTimestamp: '2026-06-04T10:01:00.000Z',
                  durationSinceStart: 70,
                  distanceInMeterSinceStart: 600,
                  pointLongLat: { coordinates: [8.51, 47.51, 440] },
                },
                timeInSecSinceLastTriggerPoint: 60,
                distanceInMeterSinceLastTriggerPoint: 500,
              },
            ],
          },
        },
        segmentsStats: [{ point1: 'A', point2: 'B', label: 'A-B', count: 1, timeDelta: 60 }],
        triggerPoints: [{ name: 'A', coordinate: { x: 8.5, y: 47.5 } }],
        tracksPerZone: { A: 1, B: 1 },
      },
    });

    const result = await fetchTrackDetailsForCrossingPoints(
      [{ name: 'A', coordinate: { x: 8.5, y: 47.5 } }],
      30
    );

    const point = result.crossings?.['1']?.crossings?.[0]?.gpsTrackDataPoint?.pointLongLat?.coordinates;
    expect(point).toEqual([8.5, 47.5, 430]);
    expect(typeof point?.[0]).toBe('number');
  });
});
