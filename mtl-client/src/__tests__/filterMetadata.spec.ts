import { describe, expect, it } from 'vitest';
import {
  buildFilterOptionGroups,
  colorForFilterGroup,
  compareLegendEntries,
  filterOptionGroupsByCatalogSearch,
  isSameFilterInfo,
  optionalFilterParamNames,
  shouldUseCompactGradientLegend,
} from '@/utils/filterMetadata';
import {
  FilterConfigEntityColoringStrategyEnum,
  FilterConfigEntityFilterCategoryEnum,
  FilterConfigEntityGroupSemanticsEnum,
  FilterConfigEntityLegendSortStrategyEnum,
  type FilterConfigEntity,
} from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterConfigEntity';
import type { FilterInfo } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterInfo';

function filterInfo(
  filterGroup: string,
  displayName: string,
  displayOrder?: number,
  description?: string,
  filterName?: string
): FilterInfo {
  return {
    filterConfig: {
      filterCategory: FilterConfigEntityFilterCategoryEnum.System,
      filterGroup,
      displayName,
      displayOrder,
      description,
      filterName,
      filterDomain: 'GPS_TRACK',
    },
  } as FilterInfo;
}

function palette({ colors = ['#111111', '#222222', '#333333'], domain3 = '005_COLORS', pLabel = 'Five colors' } = {}) {
  return {
    domain3,
    pLabel,
    pColors: colors,
    isEmptyColorPalette: () => false,
    getColorForGroup: () => colors[0],
    getColorForGroupAtIndex: (_group: string, colorIndex: number) => colors[colorIndex],
  };
}

describe('filter metadata', () => {
  it('orders filter selector groups by their lowest display order', () => {
    const groups = buildFilterOptionGroups([
      filterInfo('Date & Time', 'Tracks by year', 3000),
      filterInfo('Activity', 'Activities by keyword', 2010),
      filterInfo('Core', 'Smart Base Filter', 1000),
      filterInfo('Activity', 'Activities by type', 2000),
    ]);

    expect(groups.map((group) => group.label)).toEqual(['Core', 'Activity', 'Date & Time']);
    expect(groups[1].items.map((filter) => filter.filterConfig?.displayName)).toEqual([
      'Activities by type',
      'Activities by keyword',
    ]);
  });

  it('filters catalog entries by display name, description, filter name, and group', () => {
    const groups = buildFilterOptionGroups([
      filterInfo('Activity', 'Activities by type', 2000, 'Group tracks by sport', 'ActivityTypes'),
      filterInfo('Activity', 'Activities by keyword', 2010, 'Search names and metadata', 'KeywordSearch'),
      filterInfo('Quality', 'Duplicate tracks', 4000, 'Possible duplicate imports', 'DuplicateTracks'),
    ]);

    expect(
      filterOptionGroupsByCatalogSearch(groups, 'keyword')[0].items.map((filter) => filter.filterConfig?.displayName)
    ).toEqual(['Activities by keyword']);
    expect(
      filterOptionGroupsByCatalogSearch(groups, 'duplicate imports')[0].items.map(
        (filter) => filter.filterConfig?.displayName
      )
    ).toEqual(['Duplicate tracks']);
    expect(
      filterOptionGroupsByCatalogSearch(groups, 'KeywordSearch')[0].items.map(
        (filter) => filter.filterConfig?.displayName
      )
    ).toEqual(['Activities by keyword']);
    expect(
      filterOptionGroupsByCatalogSearch(groups, 'quality')[0].items.map((filter) => filter.filterConfig?.displayName)
    ).toEqual(['Duplicate tracks']);
  });

  it('filters catalog entries by active group before rendering rows', () => {
    const groups = buildFilterOptionGroups([
      filterInfo('Activity', 'Activities by type', 2000),
      filterInfo('Quality', 'Duplicate tracks', 4000),
    ]);

    const visibleGroups = filterOptionGroupsByCatalogSearch(groups, '', 'Quality');

    expect(visibleGroups.map((group) => group.label)).toEqual(['Quality']);
    expect(visibleGroups[0].items.map((filter) => filter.filterConfig?.displayName)).toEqual(['Duplicate tracks']);
  });

  it('matches filter identity by id before falling back to domain and name', () => {
    const byIdA = { filterConfig: { id: 7, filterName: 'A', filterDomain: 'GPS_TRACK' } } as FilterInfo;
    const byIdB = { filterConfig: { id: 7, filterName: 'B', filterDomain: 'GPS_TRACK' } } as FilterInfo;
    const byNameA = filterInfo('Activity', 'Activities by type', 2000, undefined, 'ActivityTypes');
    const byNameB = filterInfo('Activity', 'Activities by type', 2000, undefined, 'ActivityTypes');

    expect(isSameFilterInfo(byIdA, byIdB)).toBe(true);
    expect(isSameFilterInfo(byNameA, byNameB)).toBe(true);
  });

  it('reads optional filter param names from effective UI metadata', () => {
    const filter = {
      effectiveUiMetadata: {
        metadataVersion: 2,
        params: {
          DATE_TIME_FROM: { optional: true },
          DATE_TIME_TO: { optional: true },
          SEARCH_WORD: { optional: true },
          YEAR_FROM: { optional: false },
        },
      },
    } as FilterInfo;

    expect(optionalFilterParamNames(filter)).toEqual(['DATE_TIME_FROM', 'DATE_TIME_TO', 'SEARCH_WORD']);
  });

  it('keeps the default legend in SQL result order even for sequential gradients', () => {
    const filterConfig = {
      coloringStrategy: FilterConfigEntityColoringStrategyEnum.SequentialGradient,
      groupSemantics: FilterConfigEntityGroupSemanticsEnum.NumericBucket,
      legendSortStrategy: FilterConfigEntityLegendSortStrategyEnum.NumericAsc,
    } as FilterConfigEntity;
    expect(shouldUseCompactGradientLegend(filterConfig, palette())).toBe(false);
  });

  it('uses the compact grouped legend when numeric order is explicitly selected', () => {
    const filterConfig = {
      coloringStrategy: FilterConfigEntityColoringStrategyEnum.SequentialGradient,
      groupSemantics: FilterConfigEntityGroupSemanticsEnum.NumericBucket,
      legendSortStrategy: FilterConfigEntityLegendSortStrategyEnum.NumericAsc,
    } as FilterConfigEntity;
    expect(
      shouldUseCompactGradientLegend(filterConfig, palette(), FilterConfigEntityLegendSortStrategyEnum.NumericAsc)
    ).toBe(true);
  });

  it('uses the compact grouped legend when a gradient palette is selected', () => {
    const filterConfig = {
      coloringStrategy: FilterConfigEntityColoringStrategyEnum.Categorical,
      groupSemantics: FilterConfigEntityGroupSemanticsEnum.Categorical,
      legendSortStrategy: FilterConfigEntityLegendSortStrategyEnum.LabelAsc,
    } as FilterConfigEntity;
    expect(
      shouldUseCompactGradientLegend(
        filterConfig,
        palette({ domain3: 'GRADIENT_100_CUSTOM', pLabel: 'Custom gradient' })
      )
    ).toBe(true);
  });

  it('does not force compact gradient legend when a non-numeric order is explicitly selected', () => {
    const filterConfig = {
      coloringStrategy: FilterConfigEntityColoringStrategyEnum.Categorical,
      groupSemantics: FilterConfigEntityGroupSemanticsEnum.Categorical,
      legendSortStrategy: FilterConfigEntityLegendSortStrategyEnum.LabelAsc,
    } as FilterConfigEntity;
    expect(
      shouldUseCompactGradientLegend(
        filterConfig,
        palette({ domain3: 'GRADIENT_100_CUSTOM', pLabel: 'Custom gradient' }),
        FilterConfigEntityLegendSortStrategyEnum.LabelAsc
      )
    ).toBe(false);
  });

  it('orders legend entries by track count descending', () => {
    const filterConfig = {
      legendSortStrategy: FilterConfigEntityLegendSortStrategyEnum.CountDesc,
    } as FilterConfigEntity;
    const entries = [
      { group: 'B', count: 2 },
      { group: 'A', count: 8 },
      { group: 'C', count: 8 },
    ].sort((a, b) => compareLegendEntries(a, b, filterConfig, FilterConfigEntityLegendSortStrategyEnum.CountDesc));

    expect(entries.map((entry) => entry.group)).toEqual(['A', 'C', 'B']);
  });

  it('maps sequential gradient buckets to ordered palette positions', () => {
    const filterConfig = {
      coloringStrategy: FilterConfigEntityColoringStrategyEnum.SequentialGradient,
      uiMetadata: '{"bucketCount":100}',
    } as FilterConfigEntity;
    expect(colorForFilterGroup(palette(), '99', filterConfig)).toBe('#333333');
  });

  it('maps numeric buckets to ordered palette positions when the palette is a gradient', () => {
    const filterConfig = {
      coloringStrategy: FilterConfigEntityColoringStrategyEnum.Categorical,
      uiMetadata: '{"bucketCount":100}',
    } as FilterConfigEntity;
    expect(
      colorForFilterGroup(palette({ domain3: 'GRADIENT_100_CUSTOM', pLabel: 'Custom gradient' }), '99', filterConfig)
    ).toBe('#333333');
  });

  it('keeps non-gradient palettes categorical for non-gradient filters', () => {
    const filterConfig = {
      coloringStrategy: FilterConfigEntityColoringStrategyEnum.Categorical,
      uiMetadata: '{"bucketCount":100}',
    } as FilterConfigEntity;
    expect(colorForFilterGroup(palette(), '99', filterConfig)).toBe('#111111');
  });
});
