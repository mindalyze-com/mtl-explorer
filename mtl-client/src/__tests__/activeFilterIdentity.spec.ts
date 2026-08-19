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
});
