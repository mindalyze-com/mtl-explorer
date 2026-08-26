import { describe, expect, it } from 'vitest';
import { formatActiveFilterIdentity } from '@/utils/activeFilterIdentity';
import type { FilterInfo } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterInfo';

describe('active filter identity', () => {
  it('uses the public filter view name when no string criterion is active', () => {
    const filterInfo = {
      filterConfig: { displayName: 'Tracks by year', filterName: 'TracksByYear' },
    } as FilterInfo;

    expect(formatActiveFilterIdentity(filterInfo, {})).toBe('Tracks by year');
  });

  it('adds the first nonblank string criterion in definition order', () => {
    const filterInfo = {
      filterConfig: { displayName: 'Activities by keyword', filterName: 'KeywordSearch' },
      paramDefinitions: [{ name: 'IGNORED_BLANK' }, { name: 'SEARCH_WORD' }, { name: 'LATER_WORD' }],
    } as FilterInfo;

    expect(
      formatActiveFilterIdentity(filterInfo, {
        stringParams: {
          LATER_WORD: 'Later',
          IGNORED_BLANK: '   ',
          SEARCH_WORD: '  Synthetic\nactivity  ',
        },
      })
    ).toBe('Activities by keyword · Synthetic activity');
  });

  it('falls back to the technical filter name and ignores non-string values', () => {
    const filterInfo = { filterConfig: { filterName: 'CustomTracks' } } as FilterInfo;

    expect(
      formatActiveFilterIdentity(filterInfo, {
        stringParams: { FIRST: 42 as unknown as string, SECOND: 'Visible' },
      })
    ).toBe('CustomTracks · Visible');
  });

  it('formats numeric filter criteria in the selected measurement system', () => {
    const filterInfo = {
      filterConfig: { displayName: 'Tracks by distance (gradient)', filterName: 'TracksByDistanceGradient' },
      paramDefinitions: [{ name: 'DISTANCE_MAX_KM' }],
      effectiveUiMetadata: {
        params: {
          DISTANCE_MAX_KM: { widget: 'number', unit: 'km' },
        },
      },
    } as FilterInfo;
    const filterParams = { stringParams: { DISTANCE_MAX_KM: '16.09344' } };

    expect(formatActiveFilterIdentity(filterInfo, filterParams, 'US_CUSTOMARY', 'de-DE')).toBe(
      'Tracks by distance (gradient) · 10 mi'
    );
    expect(formatActiveFilterIdentity(filterInfo, filterParams, 'METRIC', 'de-DE')).toBe(
      'Tracks by distance (gradient) · 16,09 km'
    );
  });
});
