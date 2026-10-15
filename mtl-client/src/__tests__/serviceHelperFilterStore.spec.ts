import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AxiosError } from 'axios';
import { ResponseError } from 'x8ing-mtl-api-typescript-fetch';
import type { ActiveFilterRequest } from '@/stores/filterStore';

const mocks = vi.hoisted(() => {
  const apiClient = {
    get: vi.fn(),
    post: vi.fn(),
  };
  const tracksApi = {
    getTracksSimplified1: vi.fn(),
    getCrossingPoints1: vi.fn(),
    getTrackMediaOptionsWithinDistanceOfPoint: vi.fn(),
    getTrackIdsWithinDistanceOfPoint: vi.fn(),
  };
  const getActiveFilterRequest = vi.fn();
  const filtersApi = { getFilters: vi.fn() };
  const configsApi = { get: vi.fn() };
  const useFilterStore = vi.fn(() => ({ getActiveFilterRequest }));
  const loadClientFilterConfig = vi.fn();
  const loadActiveFilterRequest = vi.fn(async () => {
    try {
      return await useFilterStore().getActiveFilterRequest();
    } catch {
      const config = await loadClientFilterConfig();
      return {
        filterName: config.filterInfo?.filterConfig?.filterName ?? '',
        filterParams: config.filterParams,
      };
    }
  });

  return {
    apiClient,
    tracksApi,
    filtersApi,
    configsApi,
    getActiveFilterRequest,
    useFilterStore,
    loadClientFilterConfig,
    loadActiveFilterRequest,
  };
});

vi.mock('@/utils/apiClient', () => ({
  apiClient: mocks.apiClient,
}));

vi.mock('x8ing-mtl-api-typescript-fetch', async (importOriginal) => {
  const actual = await importOriginal<typeof import('x8ing-mtl-api-typescript-fetch')>();
  return {
    ...actual,
    TracksControllerApi: vi.fn(function () {
      return mocks.tracksApi;
    }),
    FilterControllerApi: vi.fn(function () {
      return mocks.filtersApi;
    }),
    ConfigControllerApi: vi.fn(function () {
      return mocks.configsApi;
    }),
    EnergyControllerApi: vi.fn(function () {
      return {};
    }),
  };
});

vi.mock('@/utils/openApiClient', () => ({
  getApiConfiguration: vi.fn(() => ({})),
}));

vi.mock('@/stores/filterStore', () => ({
  useFilterStore: mocks.useFilterStore,
  loadActiveFilterRequest: mocks.loadActiveFilterRequest,
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
import { logSanitizedError } from '@/utils/safeLogging';
import {
  fetchStatistics,
  fetchStatisticsOverview,
  fetchTrackDetailsForCrossingPoints,
  fetchTrackIdsWithinDistanceOfPoint,
  fetchTrackMediaOptionsWithinDistanceOfPoint,
  getRelatedTracks,
  fetchFilters,
  getConfig,
} from '@/utils/ServiceHelper';

function filterRequest(filterName: string, resolvedTrackIds: number[] | undefined = [101, 102]): ActiveFilterRequest {
  return {
    filterName,
    resolvedTrackIds,
    filterParams: {
      stringParams: { ACTIVITY: 'bike' },
      dateTimeParams: { DATE_TIME_FROM: '2026-01-01T00:00:00' },
      resultGroupSelection: { includedGroups: [{ value: 'WALKING' }] },
    },
  };
}

describe('ServiceHelper active filter resolution', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.apiClient.post.mockResolvedValue({ data: [] });
    mocks.tracksApi.getTracksSimplified1.mockResolvedValue({
      trackVersions: { 21: 1, 22: 1 },
      standardFilterCount: 2,
    });
    mocks.getActiveFilterRequest.mockResolvedValue(filterRequest('StoreFilter'));
    mocks.useFilterStore.mockReturnValue({ getActiveFilterRequest: mocks.getActiveFilterRequest });
    mocks.loadClientFilterConfig.mockResolvedValue({
      filterInfo: { filterConfig: { filterName: 'LegacyFilter' } },
      filterParams: {
        stringParams: { ACTIVITY: 'run' },
        geoRectangles: { GEO_RECTANGLE_1: { minLat: 46, minLng: 7, maxLat: 47, maxLng: 8 } },
      },
    });
  });

  it('uses the filter store for statistics loaders before FilterService fallback', async () => {
    await fetchStatistics('yyyy-MM');

    expect(mocks.useFilterStore).toHaveBeenCalledOnce();
    expect(mocks.getActiveFilterRequest).toHaveBeenCalledOnce();
    expect(FilterService.loadClientFilterConfig).not.toHaveBeenCalled();
    expect(mocks.tracksApi.getTracksSimplified1).not.toHaveBeenCalled();
    expect(mocks.apiClient.post).toHaveBeenCalledWith(
      'api/tracks/get-track-statistics?groupByDateFormat=yyyy-MM',
      [101, 102],
      { signal: undefined }
    );
  });

  it('sends the measurement system for semantic overview milestones', async () => {
    mocks.apiClient.post.mockResolvedValueOnce({ data: { measurementSystem: 'US_CUSTOMARY', milestones: [] } });

    await fetchStatisticsOverview('US_CUSTOMARY');

    expect(mocks.apiClient.post).toHaveBeenCalledWith(
      'api/tracks/get-track-overview?measurementSystem=US_CUSTOMARY',
      [101, 102],
      { signal: undefined }
    );
  });

  it('identifies the failed overview step without recording filter values', async () => {
    const failure = new Error('Response returned an error code');
    mocks.tracksApi.getTracksSimplified1.mockRejectedValueOnce(failure);
    await expect(
      fetchStatisticsOverview('METRIC', undefined, { ...filterRequest('PrivateFilter'), resolvedTrackIds: undefined })
    ).rejects.toBe(failure);
    expect(logSanitizedError).toHaveBeenLastCalledWith('Error fetching statistics overview:', failure, {
      topic1: 'application',
      topic2: 'ServiceHelper',
      topic3: 'fetchStatisticsOverview',
      context: { operation: 'resolve-statistics-track-ids' },
    });
    expect(mocks.apiClient.post).not.toHaveBeenCalled();
  });

  it.each([
    { name: 'filters', method: mocks.filtersApi.getFilters, call: () => fetchFilters() },
    { name: 'configuration', method: mocks.configsApi.get, call: () => getConfig() },
  ])('preserves generated API errors when loading $name', async ({ method, call }) => {
    const failure = new ResponseError(new Response('synthetic-body', { status: 503 }), 'Transport failed');
    failure.stack = 'ResponseError: Transport failed\n at original (bundle.js:2:3)';
    method.mockRejectedValueOnce(failure);
    await expect(call()).rejects.toBe(failure);
    expect(logSanitizedError).toHaveBeenLastCalledWith(expect.any(String), failure, expect.any(Object));
    expect(failure.response.status).toBe(503);
    expect(failure.response.bodyUsed).toBe(false);
  });

  it('preserves Axios cancellation metadata through track helpers', async () => {
    const failure = new AxiosError('Synthetic cancellation', 'ERR_CANCELED');
    failure.stack = 'AxiosError: Synthetic cancellation\n at original (bundle.js:3:4)';
    mocks.apiClient.post.mockRejectedValueOnce(failure);
    await expect(getRelatedTracks(42)).rejects.toBe(failure);
    expect(failure.code).toBe('ERR_CANCELED');
  });

  it('preserves non-Error rejection values rather than replacing them with strings', async () => {
    const failure = { code: 'SYNTHETIC_TRANSPORT', response: { status: 503 } };
    mocks.configsApi.get.mockRejectedValueOnce(failure);
    await expect(getConfig()).rejects.toBe(failure);
  });

  it('preserves structured non-Error failures from statistics overview loading', async () => {
    const failure = { code: 'SYNTHETIC_TRANSPORT', response: { status: 503 } };
    mocks.apiClient.post.mockRejectedValueOnce(failure);
    await expect(fetchStatisticsOverview()).rejects.toBe(failure);
  });

  it('uses the filter store for distance lookups before FilterService fallback', async () => {
    await fetchTrackIdsWithinDistanceOfPoint(7.4, 46.9, 250);

    expect(mocks.useFilterStore).toHaveBeenCalledOnce();
    expect(mocks.getActiveFilterRequest).toHaveBeenCalledOnce();
    expect(FilterService.loadClientFilterConfig).not.toHaveBeenCalled();
    expect(mocks.tracksApi.getTrackIdsWithinDistanceOfPoint).toHaveBeenCalledWith(
      {
        longitude: 7.4,
        latitude: 46.9,
        distanceInMeter: 250,
        filterName: 'StoreFilter',
        filterParamsRequest: {
          stringParams: { ACTIVITY: 'bike' },
          dateTimeParams: { DATE_TIME_FROM: '2026-01-01T00:00:00' },
          resultGroupSelection: { includedGroups: [{ value: 'WALKING' }] },
        },
      },
      { signal: undefined }
    );
  });

  it('uses the generated client for nearby track media counts and distances', async () => {
    mocks.tracksApi.getTrackMediaOptionsWithinDistanceOfPoint.mockResolvedValueOnce([
      { trackId: 42, distanceMeters: 18, matchedMediaCount: 3 },
    ]);

    await expect(fetchTrackMediaOptionsWithinDistanceOfPoint(7.4, 46.9, 250)).resolves.toEqual([
      { trackId: 42, distanceMeters: 18, matchedMediaCount: 3 },
    ]);
    expect(mocks.tracksApi.getTrackMediaOptionsWithinDistanceOfPoint).toHaveBeenCalledWith(
      {
        longitude: 7.4,
        latitude: 46.9,
        distanceInMeter: 250,
        filterName: 'StoreFilter',
        filterParamsRequest: {
          stringParams: { ACTIVITY: 'bike' },
          dateTimeParams: { DATE_TIME_FROM: '2026-01-01T00:00:00' },
          resultGroupSelection: { includedGroups: [{ value: 'WALKING' }] },
        },
      },
      { signal: undefined }
    );
  });

  it('sends the typed selection for related-track resolution', async () => {
    mocks.apiClient.post.mockResolvedValueOnce({ data: {} });

    await getRelatedTracks(55);

    expect(mocks.apiClient.post).toHaveBeenCalledWith('api/tracks/related/55?filterName=StoreFilter', {
      stringParams: { ACTIVITY: 'bike' },
      dateTimeParams: { DATE_TIME_FROM: '2026-01-01T00:00:00' },
      resultGroupSelection: { includedGroups: [{ value: 'WALKING' }] },
    });
  });

  it('falls back to FilterService when the filter store is unavailable', async () => {
    mocks.useFilterStore.mockImplementation(() => {
      throw new Error('no active pinia');
    });

    await fetchStatistics('yyyy');

    expect(FilterService.loadClientFilterConfig).toHaveBeenCalledOnce();
    expect(mocks.tracksApi.getTracksSimplified1).toHaveBeenCalledWith(
      {
        mode: 'ids',
        filterName: 'LegacyFilter',
        filterParamsRequest: {
          stringParams: { ACTIVITY: 'run' },
          geoRectangles: { GEO_RECTANGLE_1: { minLat: 46, minLng: 7, maxLat: 47, maxLng: 8 } },
        },
      },
      { signal: undefined }
    );
    expect(mocks.apiClient.post).toHaveBeenCalledWith(
      'api/tracks/get-track-statistics?groupByDateFormat=yyyy',
      [21, 22],
      { signal: undefined }
    );
  });

  it('uses the generated crossing API and preserves numeric coordinates', async () => {
    mocks.tracksApi.getCrossingPoints1.mockResolvedValueOnce({
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
    });

    const result = await fetchTrackDetailsForCrossingPoints([{ name: 'A', coordinate: { x: 8.5, y: 47.5 } }], 30);

    const point = result.crossings?.['1']?.crossings?.[0]?.gpsTrackDataPoint?.pointLongLat?.coordinates;
    expect(point).toEqual([8.5, 47.5, 430]);
    expect(typeof point?.[0]).toBe('number');
    expect(mocks.tracksApi.getCrossingPoints1).toHaveBeenCalledWith(
      {
        includeHealth: false,
        crossingPointsRequest: expect.objectContaining({
          filter: {
            filterName: 'StoreFilter',
            params: expect.objectContaining({ resultGroupSelection: { includedGroups: [{ value: 'WALKING' }] } }),
          },
        }),
      },
      { signal: undefined }
    );
  });
});
