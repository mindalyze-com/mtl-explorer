import { describe, expect, it } from 'vitest';
import type { FilterInfo } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterInfo';
import { filterCriteriaErrors, filterDateRange } from '@/utils/filterCriteria';

const filterInfo: FilterInfo = {
  paramDefinitions: [
    { name: 'SEARCH_WORD', type: 'STRING', label: 'Keyword' },
    { name: 'DISTANCE_MIN_KM', type: 'STRING' },
    { name: 'DISTANCE_MAX_KM', type: 'STRING' },
  ],
  effectiveUiMetadata: {
    params: {
      SEARCH_WORD: { optional: false },
      DISTANCE_MIN_KM: { widget: 'number', optional: true },
      DISTANCE_MAX_KM: { widget: 'number', optional: true },
    },
  },
};

describe('filter criteria validation', () => {
  const yearInfo: FilterInfo = {
    paramDefinitions: [
      { name: 'YEAR_FROM', type: 'STRING' },
      { name: 'YEAR_TO', type: 'STRING' },
    ],
    effectiveUiMetadata: {
      params: {
        YEAR_FROM: { widget: 'number', optional: true },
        YEAR_TO: { widget: 'number', optional: true },
      },
    },
  };

  it('allows open, equal and ordered whole-year bounds', () => {
    for (const years of [
      {},
      { YEAR_FROM: '2025' },
      { YEAR_TO: '2025' },
      { YEAR_FROM: ' 2025 ', YEAR_TO: '2025' },
      { YEAR_FROM: '1', YEAR_TO: '9999' },
    ]) {
      expect(filterCriteriaErrors(yearInfo, { stringParams: years })).toEqual({});
    }
  });

  it.each(['2025.5', '1e3', '+2025', 'NaN', '0', '-1', '10000', '999999999999999'])(
    'rejects invalid year %s before resolving',
    (value) => {
      expect(filterCriteriaErrors(yearInfo, { stringParams: { YEAR_FROM: value, YEAR_TO: value } })).toEqual({
        YEAR_FROM: 'Enter a whole year between 1 and 9999.',
        YEAR_TO: 'Enter a whole year between 1 and 9999.',
      });
    }
  );

  it('rejects reversed year bounds', () => {
    expect(filterCriteriaErrors(yearInfo, { stringParams: { YEAR_FROM: '2026', YEAR_TO: '2025' } })).toEqual({
      YEAR_TO: 'The to year must be on or after the from year.',
    });
  });
  it('distinguishes missing required fields from unrestricted optional bounds', () => {
    expect(filterCriteriaErrors(filterInfo, {})).toEqual({ SEARCH_WORD: 'Enter keyword.' });
    expect(filterCriteriaErrors(filterInfo, { stringParams: { SEARCH_WORD: 'lake' } })).toEqual({});
  });

  it('flags reversed numeric bounds without rejecting a valid zero', () => {
    expect(
      filterCriteriaErrors(filterInfo, {
        stringParams: { SEARCH_WORD: 'lake', DISTANCE_MIN_KM: '20', DISTANCE_MAX_KM: '10' },
      })
    ).toEqual({ DISTANCE_MAX_KM: 'The maximum must be at least the minimum.' });
    expect(
      filterCriteriaErrors(filterInfo, {
        stringParams: { SEARCH_WORD: 'lake', DISTANCE_MIN_KM: '0', DISTANCE_MAX_KM: '0' },
      })
    ).toEqual({});
  });

  it('compares local dates and times and allows open-ended ranges', () => {
    expect(
      filterCriteriaErrors(undefined, {
        dateTimeParams: {
          DATE_TIME_FROM: '2026-09-06 12:00:00',
          DATE_TIME_TO: '2026-09-06 11:00:00',
        },
      })
    ).toEqual({ DATE_TIME_TO: 'The end must be on or after the start.' });
    expect(filterCriteriaErrors(undefined, { dateTimeParams: { DATE_TIME_FROM: '2026-09-06 12:00:00' } })).toEqual({});
  });
});

describe('filter date shortcuts', () => {
  const now = new Date(2026, 0, 10, 14, 30);
  it('includes thirty calendar days across a year boundary and the full final day', () => {
    const range = filterDateRange('recent', now);
    expect(range.from).toEqual(new Date(2025, 11, 12));
    expect(range.to).toEqual(new Date(2026, 0, 10, 23, 59, 59, 999));
  });
  it('uses local calendar boundaries for the current and previous year', () => {
    expect(filterDateRange('year', now).from).toEqual(new Date(2026, 0, 1));
    expect(filterDateRange('previous-year', now)).toEqual({
      from: new Date(2025, 0, 1),
      to: new Date(2025, 11, 31, 23, 59, 59, 999),
    });
    expect(filterDateRange('all', now)).toEqual({ from: null, to: null });
  });
});
