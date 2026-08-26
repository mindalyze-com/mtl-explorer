import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, shallowMount } from '@vue/test-utils';
import type { VueWrapper } from '@vue/test-utils';
import type { FilterInfo } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterInfo';
import { STORAGE_KEYS } from '@/utils/appStorage';
import { reactive } from 'vue';

const yearsFilter: FilterInfo = {
  filterConfig: {
    id: 1,
    filterName: 'Years',
    filterDomain: 'GPS_TRACK',
    displayName: 'Years',
    expression: 'select * from gps_track',
  },
  paramDefinitions: [{ name: 'ACTIVITY', type: 'STRING' }],
  effectiveUiMetadata: {
    metadataVersion: 2,
    params: { ACTIVITY: { optional: true } },
  },
};
const activitiesFilter: FilterInfo = {
  filterConfig: {
    id: 2,
    filterName: 'Activities',
    filterDomain: 'GPS_TRACK',
    displayName: 'Activities',
  },
  paramDefinitions: [],
};
const smartBaseFilter: FilterInfo = {
  filterConfig: {
    id: 3,
    filterName: 'SmartBaseFilter',
    filterDomain: 'GPS_TRACK',
    displayName: 'Smart Base Filter',
  },
  paramDefinitions: [],
};
const trackPickerFilter: FilterInfo = {
  filterConfig: {
    id: 5,
    filterName: 'TracksBySelection',
    filterDomain: 'GPS_TRACK',
    displayName: 'Tracks by selection',
  },
  paramDefinitions: [{ name: 'TRACK_IDS', type: 'STRING' }],
  effectiveUiMetadata: {
    metadataVersion: 2,
    params: {
      TRACK_IDS: {
        optional: true,
        widget: 'trackPicker',
        optionsSource: {
          type: 'originFilterResult',
          resolvedFilterRef: '/GPS_TRACK/SmartBaseFilter',
        },
      },
    },
  },
};
const geoFilter: FilterInfo = {
  filterConfig: {
    id: 4,
    filterName: 'GeoFilter',
    filterDomain: 'GPS_TRACK',
    displayName: 'Geo Filter',
  },
  paramDefinitions: [
    { name: 'GEO_CIRCLE_1', type: 'GEO_CIRCLE' },
    { name: 'GEO_RECTANGLE_1', type: 'GEO_RECTANGLE' },
    { name: 'GEO_POLYGON_1', type: 'GEO_POLYGON' },
  ],
};

const mocks = vi.hoisted(() => ({
  activeIdentity: 'Tracks by year',
  ensureLoaded: vi.fn(),
  save: vi.fn(),
  applyResolvedFilter: vi.fn(),
  fetchFilters: vi.fn(),
  fetchResolveFilter: vi.fn(),
  store: null as null | Record<string, unknown>,
}));

vi.mock('@/stores/filterStore', () => ({
  useFilterStore: () => mocks.store,
}));

vi.mock('@/utils/ServiceHelper', () => ({
  fetchFilters: mocks.fetchFilters,
  fetchResolveFilter: mocks.fetchResolveFilter,
  fetchFilterInfo: vi.fn(),
  getServerBuildInfo: vi.fn(),
}));

vi.mock('@/components/filter/ColorPalette', () => {
  class ColorPalette {
    id?: number;
    pLabel = 'No coloring';
    pDescription = '';
    pColors: string[] = [];

    static of(source?: Partial<ColorPalette> | null): ColorPalette {
      return Object.assign(new ColorPalette(), source ?? {});
    }

    static async fetch(): Promise<ColorPalette[]> {
      return [];
    }

    isEmptyColorPalette(): boolean {
      return this.pColors.length === 0;
    }

    reset(): void {}

    getColorForGroup(): string {
      return '#64748b';
    }

    getColorForGroupAtIndex(): string {
      return '#64748b';
    }
  }
  return { ColorPalette };
});

import CustomFilter from '@/components/filter/CustomFilter.vue';

const BottomSheetStub = {
  name: 'BottomSheet',
  props: ['modelValue', 'title'],
  emits: ['update:modelValue', 'closed'],
  template: '<div><slot name="title"/><slot name="header-actions"/><slot/><slot name="footer"/></div>',
};
const FilterOverviewStub = {
  name: 'FilterOverview',
  props: [
    'enabled',
    'resultText',
    'resultDetail',
    'resultActionLabel',
    'resultActionIcon',
    'showSecondaryResultAction',
    'showReviewAction',
    'resetUndoAvailable',
    'activeIdentity',
    'viewSummary',
    'criteriaSummary',
    'categoriesSummary',
    'categoriesAvailable',
    'colorsSummary',
    'colorsAvailable',
  ],
  emits: [
    'result-action',
    'secondary-result-action',
    'open-view',
    'open-criteria',
    'open-categories',
    'open-colors',
    'open-scope-help',
    'review',
    'reset',
    'undo-reset',
    'update:enabled',
  ],
  template: '<div data-test="filter-overview"></div>',
};
const FilterScopeHelpStub = {
  name: 'FilterScopeHelp',
  props: ['firstVisit'],
  emits: ['done'],
  template: '<div data-test="filter-scope-help"></div>',
};
const FilterViewSheetStub = {
  name: 'FilterViewSheet',
  props: ['modelValue', 'groups', 'selectedFilterInfo'],
  emits: ['update:modelValue', 'apply'],
  template: '<div data-test="filter-view-sheet"></div>',
};
const FilterCriteriaSheetStub = {
  name: 'FilterCriteriaSheet',
  props: ['modelValue', 'filterInfo', 'filterParams', 'trackIdCandidateTracks', 'trackIdCandidatesLoading'],
  emits: ['update:modelValue', 'change', 'apply-and-draw'],
  template: '<div data-test="filter-criteria-sheet"></div>',
};
const FilterCategoriesSheetStub = {
  name: 'FilterCategoriesSheet',
  props: ['modelValue', 'selection'],
  emits: ['update:modelValue', 'apply'],
  template: '<div data-test="filter-categories-sheet"></div>',
};
const FilterColoringSheetStub = {
  name: 'FilterColoringSheet',
  props: ['modelValue', 'palette', 'legendSortStrategy'],
  emits: ['update:modelValue', 'apply'],
  template: '<div data-test="filter-coloring-sheet"></div>',
};
const FilterTrackReviewSheetStub = {
  name: 'FilterTrackReviewSheet',
  props: ['modelValue', 'loading', 'error', 'entries'],
  emits: ['update:modelValue', 'select-track', 'open-details', 'retry'],
  template: '<div data-test="filter-track-review-sheet"></div>',
};

const filterStubs = {
  BottomSheet: BottomSheetStub,
  FilterOverview: FilterOverviewStub,
  FilterScopeHelp: FilterScopeHelpStub,
  FilterViewSheet: FilterViewSheetStub,
  FilterCriteriaSheet: FilterCriteriaSheetStub,
  FilterCategoriesSheet: FilterCategoriesSheetStub,
  FilterColoringSheet: FilterColoringSheetStub,
  FilterTrackReviewSheet: FilterTrackReviewSheetStub,
  FilterSqlPreview: true,
};
const mountedWrappers: VueWrapper[] = [];

describe('CustomFilter overview navigation', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.clearAllMocks();
    mocks.activeIdentity = 'Tracks by year';
    mocks.store = reactive({
      get activeIdentity() {
        return mocks.activeIdentity;
      },
      activeResult: null,
      dataFreshnessRevision: 0,
      ensureLoaded: mocks.ensureLoaded,
      save: mocks.save,
      applyResolvedFilter: mocks.applyResolvedFilter,
    });
    localStorage.clear();
    localStorage.setItem(STORAGE_KEYS.filterScopeHelpSeen, 'true');
    mocks.fetchFilters.mockResolvedValue([smartBaseFilter, yearsFilter, activitiesFilter]);
    mocks.ensureLoaded.mockResolvedValue({
      filterInfo: yearsFilter,
      filterParams: {
        resultGroupSelection: { includedGroups: [{ value: '2025' }] },
      },
      palette: {},
    });
  });

  afterEach(() => {
    while (mountedWrappers.length) mountedWrappers.pop()?.unmount();
    vi.useRealTimers();
  });

  function mountFilter(show = true): VueWrapper {
    const wrapper = shallowMount(CustomFilter, {
      props: { show },
      global: { stubs: filterStubs },
    });
    mountedWrappers.push(wrapper);
    return wrapper;
  }

  it('shows a central overview and applies a selected view through its detail sheet', async () => {
    const wrapper = mountFilter(false);
    await flushPromises();

    expect(wrapper.find('.filter-header__panel-title').text()).toBe('Filter');
    const overview = wrapper.findComponent(FilterOverviewStub);
    const viewSheet = wrapper.findComponent(FilterViewSheetStub);
    expect(overview.props('viewSummary')).toBe('Years');
    expect(overview.props('criteriaSummary')).toBe('No criteria');
    expect(overview.props('categoriesAvailable')).toBe(false);
    expect(viewSheet.props('modelValue')).toBe(false);

    overview.vm.$emit('open-view');
    await wrapper.vm.$nextTick();
    expect(viewSheet.props('modelValue')).toBe(true);

    viewSheet.vm.$emit('apply', activitiesFilter);
    await wrapper.vm.$nextTick();
    expect(overview.props('viewSummary')).toBe('Activities');
    const criteriaParams = wrapper.findComponent(FilterCriteriaSheetStub).props('filterParams') as {
      resultGroupSelection?: unknown;
    };
    expect(criteriaParams.resultGroupSelection).toBeUndefined();
  });

  it('keeps category management available when only a saved unavailable category remains', async () => {
    const wrapper = mountFilter();
    await flushPromises();

    const store = mocks.store as { activeResult: unknown };
    store.activeResult = {
      queryResult: {
        resultEntries: [],
        groupingAvailable: true,
        availableGroups: [],
      },
      trackVersions: new Map(),
      filterGroups: new Map(),
      standardFilterCount: 0,
    };
    await wrapper.vm.$nextTick();

    const overview = wrapper.findComponent(FilterOverviewStub);
    expect(overview.props('categoriesAvailable')).toBe(true);
    expect(overview.props('categoriesSummary')).toBe('0 of 0 categories · 1 unavailable');
    overview.vm.$emit('open-categories');
    await wrapper.vm.$nextTick();
    expect(wrapper.findComponent(FilterCategoriesSheetStub).props('modelValue')).toBe(true);
  });

  it('restores the selected filter chip identity when the Filter sheet reopens', async () => {
    const wrapper = mountFilter(false);
    await flushPromises();

    expect(wrapper.findComponent(FilterOverviewStub).props('activeIdentity')).toBe('Years');

    await wrapper.setProps({ show: true });
    await wrapper.setProps({ show: false });
    await wrapper.setProps({ show: true });

    expect(wrapper.findComponent(FilterOverviewStub).props('activeIdentity')).toBe('Years');
  });

  it('shows the enabled persisted Smart Base Filter as the active filter chip', async () => {
    mocks.ensureLoaded.mockResolvedValue({
      filterInfo: smartBaseFilter,
      filterParams: {},
      palette: {},
    });
    mocks.activeIdentity = '';

    const wrapper = mountFilter(false);
    await flushPromises();

    expect(wrapper.findComponent(FilterOverviewStub).props('activeIdentity')).toBe('Smart Base Filter');
  });

  it('controls pause and resume from the Current result action', async () => {
    const wrapper = mountFilter();
    await flushPromises();

    const overview = wrapper.findComponent(FilterOverviewStub);
    expect(wrapper.find('.filter-header__toggle').exists()).toBe(false);
    expect(overview.props('enabled')).toBe(true);

    overview.vm.$emit('update:enabled', false);
    await wrapper.vm.$nextTick();
    expect(overview.props('enabled')).toBe(false);
    expect(overview.props('resultText')).toBe('Filter paused');
    expect(overview.props('resultDetail')).toBe('All tracks are shown. Your filter setup is kept.');

    overview.vm.$emit('update:enabled', true);
    await wrapper.vm.$nextTick();
    expect(overview.props('enabled')).toBe(true);
  });

  it('shows the explanation page once and keeps it available from the overview', async () => {
    localStorage.removeItem(STORAGE_KEYS.filterScopeHelpSeen);
    const wrapper = mountFilter(false);
    await flushPromises();

    await wrapper.setProps({ show: true });
    expect(wrapper.find('.filter-header__panel-title').text()).toBe('How filters work');
    expect(wrapper.findComponent(FilterScopeHelpStub).props('firstVisit')).toBe(true);
    expect(localStorage.getItem(STORAGE_KEYS.filterScopeHelpSeen)).toBe('true');

    wrapper.findComponent(FilterScopeHelpStub).vm.$emit('done');
    await wrapper.vm.$nextTick();
    expect(wrapper.find('.filter-header__panel-title').text()).toBe('Filter');

    wrapper.findComponent(FilterOverviewStub).vm.$emit('open-scope-help');
    await wrapper.vm.$nextTick();
    expect(wrapper.find('.filter-header__panel-title').text()).toBe('How filters work');
    expect(wrapper.findComponent(FilterScopeHelpStub).props('firstVisit')).toBe(false);

    await wrapper.get('.filter-header__back').trigger('click');
    expect(wrapper.find('.filter-header__panel-title').text()).toBe('Filter');
  });

  it('keeps the category selection when the active view is applied again', async () => {
    const wrapper = mountFilter();
    await flushPromises();

    wrapper.findComponent(FilterViewSheetStub).vm.$emit('apply', yearsFilter);
    await wrapper.vm.$nextTick();
    const criteriaParams = wrapper.findComponent(FilterCriteriaSheetStub).props('filterParams') as {
      resultGroupSelection?: unknown;
    };
    expect(criteriaParams.resultGroupSelection).toEqual({
      includedGroups: [{ value: '2025' }],
    });
  });

  it('applies live criteria through the debounced preview and updates the overview summary', async () => {
    mocks.fetchResolveFilter.mockResolvedValue({
      queryResult: { resultEntries: [{ id: 1 }] },
      trackVersions: new Map([[1, 1]]),
      filterGroups: new Map(),
      standardFilterCount: 1,
    });
    const wrapper = mountFilter();
    await flushPromises();

    wrapper.findComponent(FilterCriteriaSheetStub).vm.$emit('change', {
      filterParams: {
        stringParams: { ACTIVITY: 'WALKING' },
        resultGroupSelection: { includedGroups: [{ value: '2025' }] },
      },
      clearedGeoParams: [],
    });
    await wrapper.vm.$nextTick();

    expect(wrapper.findComponent(FilterOverviewStub).props('criteriaSummary')).toBe('1 criterion active');
    const criteriaParams = wrapper.findComponent(FilterCriteriaSheetStub).props('filterParams') as {
      stringParams?: Record<string, string>;
      resultGroupSelection?: unknown;
    };
    expect(criteriaParams.stringParams).toEqual({ ACTIVITY: 'WALKING' });
    expect(criteriaParams.resultGroupSelection).toEqual({ includedGroups: [{ value: '2025' }] });

    expect(mocks.fetchResolveFilter).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(400);
    await flushPromises();
    expect(mocks.fetchResolveFilter).toHaveBeenCalledWith(
      1,
      expect.objectContaining({ stringParams: { ACTIVITY: 'WALKING' } }),
      false
    );
    expect(mocks.applyResolvedFilter).toHaveBeenCalledOnce();
  });

  it('persists completed geo shapes before the debounced preview resolves', async () => {
    mocks.fetchFilters.mockResolvedValue([smartBaseFilter, geoFilter]);
    mocks.ensureLoaded.mockResolvedValue({
      filterInfo: geoFilter,
      filterParams: {},
      palette: {},
    });
    const wrapper = mountFilter();
    await flushPromises();

    const exposed = wrapper.vm as unknown as {
      onGeoDrawingComplete: (paramDef: FilterInfo['paramDefinitions'][number], shape: unknown) => void;
    };
    exposed.onGeoDrawingComplete(geoFilter.paramDefinitions![0], { lat: 47, lng: 8, radiusM: 1000 });
    exposed.onGeoDrawingComplete(geoFilter.paramDefinitions![1], {
      minLat: 46,
      minLng: 7,
      maxLat: 47,
      maxLng: 8,
    });
    exposed.onGeoDrawingComplete(geoFilter.paramDefinitions![2], {
      coordinates: [
        [7, 46],
        [8, 46],
        [8, 47],
      ],
    });

    expect(mocks.applyResolvedFilter).not.toHaveBeenCalled();
    expect(mocks.save).toHaveBeenCalledTimes(3);
    expect(mocks.save).toHaveBeenLastCalledWith(
      expect.objectContaining({
        filterParams: expect.objectContaining({
          geoCircles: { GEO_CIRCLE_1: { lat: 47, lng: 8, radiusM: 1000 } },
          geoRectangles: { GEO_RECTANGLE_1: { minLat: 46, minLng: 7, maxLat: 47, maxLng: 8 } },
          geoPolygons: {
            GEO_POLYGON_1: {
              coordinates: [
                [7, 46],
                [8, 46],
                [8, 47],
              ],
            },
          },
        }),
      }),
      { trackSetChanged: false }
    );
  });

  it('closes open detail sheets when the main filter sheet closes', async () => {
    const wrapper = mountFilter();
    await flushPromises();

    wrapper.findComponent(FilterOverviewStub).vm.$emit('open-criteria');
    await wrapper.vm.$nextTick();
    expect(wrapper.findComponent(FilterCriteriaSheetStub).props('modelValue')).toBe(true);

    await wrapper.setProps({ show: false });
    expect(wrapper.findComponent(FilterCriteriaSheetStub).props('modelValue')).toBe(false);
  });

  it('restores Review tracks after Track Details temporarily closes the filter', async () => {
    mocks.fetchResolveFilter.mockResolvedValue({
      queryResult: { resultEntries: [{ id: 11, gpsTrack: { id: 11, trackName: 'Morning walk' } }] },
      trackVersions: new Map([[11, 1]]),
      filterGroups: new Map(),
      standardFilterCount: 1,
    });
    const wrapper = mountFilter();
    await flushPromises();

    wrapper.findComponent(FilterOverviewStub).vm.$emit('review');
    await flushPromises();
    const reviewSheet = wrapper.findComponent(FilterTrackReviewSheetStub);
    expect(reviewSheet.props('modelValue')).toBe(true);

    const navigation = (wrapper.vm as unknown as { getNavigationState: () => unknown }).getNavigationState();
    await wrapper.setProps({ show: false });
    expect(reviewSheet.props('modelValue')).toBe(false);

    await wrapper.setProps({ show: true });
    (wrapper.vm as unknown as { restoreNavigationState: (state: unknown) => void }).restoreNavigationState(navigation);
    await wrapper.vm.$nextTick();
    expect(reviewSheet.props('modelValue')).toBe(true);
  });

  it('refreshes an open Review without dropping its last good rows', async () => {
    const initialPreview = {
      queryResult: { resultEntries: [{ id: 11 }] },
      trackVersions: new Map([[11, 1]]),
      filterGroups: new Map(),
      standardFilterCount: 1,
    };
    const initialDetails = {
      ...initialPreview,
      queryResult: { resultEntries: [{ id: 11, gpsTrack: { id: 11, trackName: 'Morning walk' } }] },
    };
    const refreshedDetails = {
      queryResult: { resultEntries: [{ id: 12, gpsTrack: { id: 12, trackName: 'Evening ride' } }] },
      trackVersions: new Map([[12, 1]]),
      filterGroups: new Map(),
      standardFilterCount: 1,
    };
    let resolveRefresh!: (result: typeof refreshedDetails) => void;
    mocks.fetchResolveFilter
      .mockResolvedValueOnce(initialPreview)
      .mockResolvedValueOnce(initialDetails)
      .mockReturnValueOnce(
        new Promise<typeof refreshedDetails>((resolve) => {
          resolveRefresh = resolve;
        })
      );
    const wrapper = mountFilter();
    await flushPromises();
    await vi.advanceTimersByTimeAsync(400);
    await flushPromises();

    wrapper.findComponent(FilterOverviewStub).vm.$emit('review');
    await flushPromises();
    const reviewSheet = wrapper.findComponent(FilterTrackReviewSheetStub);
    expect(reviewSheet.props('entries')).toEqual(initialDetails.queryResult.resultEntries);

    const store = mocks.store as { activeResult: unknown };
    store.activeResult = {
      queryResult: { resultEntries: [{ id: 12 }] },
      trackVersions: new Map([[12, 1]]),
      filterGroups: new Map(),
      standardFilterCount: 1,
    };
    await wrapper.vm.$nextTick();

    expect(reviewSheet.props('loading')).toBe(true);
    expect(reviewSheet.props('entries')).toEqual(initialDetails.queryResult.resultEntries);

    resolveRefresh(refreshedDetails);
    await flushPromises();

    expect(reviewSheet.props('loading')).toBe(false);
    expect(reviewSheet.props('entries')).toEqual(refreshedDetails.queryResult.resultEntries);
  });

  it('refreshes track picker candidates when server data freshness changes', async () => {
    mocks.fetchFilters.mockResolvedValue([smartBaseFilter, trackPickerFilter]);
    mocks.ensureLoaded.mockResolvedValue({
      filterInfo: trackPickerFilter,
      filterParams: {},
      palette: {},
    });
    const initialCandidates = {
      queryResult: {
        resultEntries: [
          { id: 11, gpsTrack: { id: 11, trackName: 'Morning walk' } },
          { id: 12, gpsTrack: { id: 12, trackName: 'Deleted ride' } },
        ],
      },
      trackVersions: new Map(),
      filterGroups: new Map(),
      standardFilterCount: 2,
    };
    const refreshedCandidates = {
      ...initialCandidates,
      queryResult: {
        resultEntries: [{ id: 11, gpsTrack: { id: 11, trackName: 'Morning walk' } }],
      },
      standardFilterCount: 1,
    };
    let candidateLoadCount = 0;
    mocks.fetchResolveFilter.mockImplementation((_filterId, _params, loadGpsDetails) => {
      if (!loadGpsDetails) {
        return Promise.resolve({
          queryResult: { resultEntries: [] },
          trackVersions: new Map(),
          filterGroups: new Map(),
          standardFilterCount: 0,
        });
      }
      candidateLoadCount += 1;
      return Promise.resolve(candidateLoadCount === 1 ? initialCandidates : refreshedCandidates);
    });

    const wrapper = mountFilter();
    await flushPromises();
    await vi.advanceTimersByTimeAsync(350);
    await flushPromises();

    const criteriaSheet = wrapper.findComponent(FilterCriteriaSheetStub);
    expect(criteriaSheet.props('trackIdCandidateTracks')).toEqual([
      expect.objectContaining({ id: 11 }),
      expect.objectContaining({ id: 12 }),
    ]);

    const store = mocks.store as { dataFreshnessRevision: number };
    store.dataFreshnessRevision += 1;
    await wrapper.vm.$nextTick();
    await vi.advanceTimersByTimeAsync(350);
    await flushPromises();

    expect(criteriaSheet.props('trackIdCandidateTracks')).toEqual([expect.objectContaining({ id: 11 })]);
    expect(mocks.fetchResolveFilter).toHaveBeenCalledWith(3, {}, true);
    expect(mocks.fetchResolveFilter.mock.calls.filter((call) => call[2] === true)).toHaveLength(2);
  });

  it('refreshes paused result metadata without applying the draft to the map', async () => {
    localStorage.setItem('mtl.filter.paused', 'true');
    localStorage.setItem(
      'mtl.filter.paused-draft',
      JSON.stringify({ filterInfo: yearsFilter, filterParams: {}, palette: {} })
    );
    mocks.fetchResolveFilter.mockResolvedValue({
      queryResult: {
        resultEntries: [{ id: 1, group: '2025' }],
        groupingAvailable: true,
        availableGroups: [{ key: { value: '2025' }, count: 1 }],
      },
      trackVersions: new Map([[1, 1]]),
      filterGroups: new Map([[1, '2025']]),
      standardFilterCount: 1,
    });

    const wrapper = mountFilter();
    await flushPromises();

    expect(mocks.fetchResolveFilter).toHaveBeenCalledWith(1, expect.any(Object), false);
    expect(mocks.applyResolvedFilter).not.toHaveBeenCalled();
    const overview = wrapper.findComponent(FilterOverviewStub);
    expect(overview.props('enabled')).toBe(false);
    expect(overview.props('resultText')).toBe('Filter paused');
    expect(overview.props('resultDetail')).toBe('All tracks are shown. Your filter setup is kept.');
  });

  it('dismisses the filter action menu on outside pointer input or Escape', async () => {
    const wrapper = mountFilter();
    await flushPromises();

    await wrapper.find('.filter-header-action').trigger('click');
    expect(wrapper.find('.filter-overflow__menu').exists()).toBe(true);

    document.body.dispatchEvent(new Event('pointerdown', { bubbles: true }));
    await wrapper.vm.$nextTick();
    expect(wrapper.find('.filter-overflow__menu').exists()).toBe(false);

    await wrapper.find('.filter-header-action').trigger('click');
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    await wrapper.vm.$nextTick();
    expect(wrapper.find('.filter-overflow__menu').exists()).toBe(false);
  });

  it('keeps reset feedback inline and restores the prior filter through Undo', async () => {
    const wrapper = mountFilter();
    await flushPromises();

    const overview = wrapper.findComponent(FilterOverviewStub);
    overview.vm.$emit('reset');
    await wrapper.vm.$nextTick();
    expect(overview.props('resetUndoAvailable')).toBe(true);
    expect(overview.props('viewSummary')).toBe('Smart Base Filter');

    document.body.dispatchEvent(new Event('pointerdown', { bubbles: true }));
    await wrapper.vm.$nextTick();
    expect(overview.props('resetUndoAvailable')).toBe(true);

    overview.vm.$emit('undo-reset');
    await wrapper.vm.$nextTick();
    expect(overview.props('resetUndoAvailable')).toBe(false);
    expect(overview.props('viewSummary')).toBe('Years');
  });
});
