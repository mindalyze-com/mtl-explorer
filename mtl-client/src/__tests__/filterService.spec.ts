import { describe, expect, it } from 'vitest';
import { FilterService } from '@/components/filter/FilterService';
import type { FilterInfo } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterInfo';

function filterInfo(filterName: string, paramDefinitions: FilterInfo['paramDefinitions'] = []): FilterInfo {
  return {
    filterConfig: {
      filterName,
      filterDomain: 'GPS_TRACK',
    },
    paramDefinitions,
  };
}

describe('FilterService active state', () => {
  it('does not treat an incomplete string-param filter as active', () => {
    expect(
      FilterService.hasActiveFilterConfig({
        filterInfo: filterInfo('KeywordSearch', [{ name: 'SEARCH_WORD', type: 'STRING', label: 'Search word' }]),
        filterParams: { stringParams: { SEARCH_WORD: '   ' } },
        palette: null,
      })
    ).toBe(false);
  });

  it('treats a filter with blank optional string params as active', () => {
    const gradientFilterInfo = filterInfo('TracksByDistanceGradient', [
      { name: 'DISTANCE_MIN_KM', type: 'STRING', label: 'Distance Min Km' },
      { name: 'DISTANCE_MAX_KM', type: 'STRING', label: 'Distance Max Km' },
    ]);
    gradientFilterInfo.effectiveUiMetadata = {
      metadataVersion: 2,
      params: {
        DISTANCE_MIN_KM: { optional: true },
        DISTANCE_MAX_KM: { optional: true },
      },
    };

    expect(
      FilterService.hasActiveFilterConfig({
        filterInfo: gradientFilterInfo,
        filterParams: { stringParams: { DISTANCE_MIN_KM: '', DISTANCE_MAX_KM: '   ' } },
        palette: null,
      })
    ).toBe(true);
  });

  it('does not treat stray string params on the standard filter as standard params', () => {
    expect(
      FilterService.isStandardFilterWithStandardParams({
        filterInfo: filterInfo('SmartBaseFilter'),
        filterParams: { stringParams: { SEARCH_WORD: 'trail' } },
      })
    ).toBe(false);
  });
});
