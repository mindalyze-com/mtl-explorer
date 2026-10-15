import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, shallowMount } from '@vue/test-utils';
import type { VueWrapper } from '@vue/test-utils';
import type { FilterInfo } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterInfo';
import { STORAGE_KEYS } from '@/utils/appStorage';
import { reactive } from 'vue';
import { ColorPalette } from '@/components/filter/ColorPalette';
import { FilterConfigEntityColoringStrategyEnum } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterConfigEntity';

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
  loadCachedTracks: vi.fn(),
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

vi.mock('@/utils/tracks/trackCollectionLoader', () => ({ loadCachedTracks: mocks.loadCachedTracks }));

const fetchPalettes = vi.spyOn(ColorPalette, 'fetch');

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
  props: ['modelValue', 'enabled', 'groups', 'selectedFilterInfo', 'legendSortStrategy'],
  emits: ['update:modelValue', 'apply'],
  template: '<div data-test="filter-view-sheet"></div>',
};
const FilterCriteriaSheetStub = {
  name: 'FilterCriteriaSheet',
  props: ['modelValue', 'enabled', 'filterInfo', 'filterParams', 'trackIdCandidateTracks', 'trackIdCandidatesLoading'],
  emits: ['update:modelValue', 'change', 'apply-and-enable', 'apply-and-draw'],
  template: '<div data-test="filter-criteria-sheet"></div>',
};
const FilterCategoriesSheetStub = {
  name: 'FilterCategoriesSheet',
  props: ['modelValue', 'enabled', 'selection'],
  emits: ['update:modelValue', 'apply'],
  template: '<div data-test="filter-categories-sheet"></div>',
};
const FilterColoringSheetStub = {
  name: 'FilterColoringSheet',
  props: ['modelValue', 'enabled', 'palettes', 'palette', 'defaultPalette', 'legendSortStrategy'],
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
    fetchPalettes.mockResolvedValue([]);
    mocks.loadCachedTracks.mockResolvedValue(new Map());
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

  function configureColoringViews() {
    const categorical: FilterInfo = {
      ...activitiesFilter,
      filterConfig: {
        ...activitiesFilter.filterConfig,
        coloringStrategy: FilterConfigEntityColoringStrategyEnum.Categorical,
        preferredPalette: '008_COLORS',
      },
    };
    const gradient: FilterInfo = {
      filterConfig: {
        id: 6,
        filterName: 'DistanceGradient',
        filterDomain: 'GPS_TRACK',
        displayName: 'Distance gradient',
        coloringStrategy: FilterConfigEntityColoringStrategyEnum.SequentialGradient,
        preferredPalette: 'GRADIENT_100_VIRIDIS',
      },
      paramDefinitions: [],
    };
    fetchPalettes.mockResolvedValue([
      ColorPalette.of({
        id: 10,
        domain3: '008_COLORS',
        value: JSON.stringify({ label: '8 Colors', colors: ['#123456', '#654321'] }),
      }),
      ColorPalette.of({
        id: 11,
        domain3: 'GRADIENT_100_VIRIDIS',
        value: JSON.stringify({ label: 'Gradient Viridis', colors: ['#440154', '#fde725'] }),
      }),
    ]);
    mocks.fetchFilters.mockResolvedValue([smartBaseFilter, categorical, gradient]);
    mocks.ensureLoaded.mockResolvedValue({ filterInfo: categorical, filterParams: {}, palette: {} });
    return { categorical, gradient };
  }

  it('preserves an explicit No coloring choice after serialization, reload and view changes', async () => {
    const { categorical, gradient } = configureColoringViews();
    const wrapper = mountFilter();
    await flushPromises();
    const coloring = wrapper.findComponent(FilterColoringSheetStub);
    expect(coloring.props('palette').pLabel).toBe('8 Colors');
    const noColoring = coloring.props('palettes').find((palette: ColorPalette) => palette.pLabel === 'No coloring');
    expect(noColoring).toBeDefined();
    coloring.vm.$emit('apply', { palette: noColoring, legendSortStrategy: null });
    await wrapper.vm.$nextTick();
    expect(coloring.props('palette').isEmptyColorPalette()).toBe(true);

    const saved = JSON.parse(JSON.stringify(mocks.save.mock.calls.at(-1)?.[0]));
    expect(saved.palette.noColoring).toBe(true);
    // Presentation labels are not needed to restore the stored preference.
    delete saved.palette.pLabel;
    delete saved.palette.pDescription;
    wrapper.unmount();
    mocks.ensureLoaded.mockResolvedValue(saved);
    const reloaded = mountFilter();
    await flushPromises();
    const reloadedColoring = reloaded.findComponent(FilterColoringSheetStub);
    expect(reloadedColoring.props('palette').pLabel).toBe('No coloring');
    expect(reloadedColoring.props('palette').isEmptyColorPalette()).toBe(true);
    for (const view of [gradient, categorical]) {
      reloaded.findComponent(FilterViewSheetStub).vm.$emit('apply', view);
      await reloaded.vm.$nextTick();
      expect(reloadedColoring.props('palette').pLabel).toBe('No coloring');
      expect(reloadedColoring.props('palette').isEmptyColorPalette()).toBe(true);
    }

    const overview = reloaded.findComponent(FilterOverviewStub);
    overview.vm.$emit('reset');
    await reloaded.vm.$nextTick();
    expect(overview.props('viewSummary')).toBe('Smart Base Filter');
    overview.vm.$emit('undo-reset');
    await reloaded.vm.$nextTick();
    expect(reloadedColoring.props('palette').pLabel).toBe('No coloring');
    reloaded.findComponent(FilterViewSheetStub).vm.$emit('apply', gradient);
    await reloaded.vm.$nextTick();
    expect(reloadedColoring.props('palette').isEmptyColorPalette()).toBe(true);
  });

  it('retains legacy saved No coloring choices while fresh configs still use the preferred palette', async () => {
    const { categorical } = configureColoringViews();
    mocks.ensureLoaded.mockResolvedValue({
      filterInfo: categorical,
      filterParams: {},
      palette: { pLabel: 'No coloring', pDescription: 'No coloring at all' },
    });
    const wrapper = mountFilter();
    await flushPromises();
    const coloring = wrapper.findComponent(FilterColoringSheetStub);
    expect(coloring.props('palette').pLabel).toBe('No coloring');
    expect(coloring.props('palette').isEmptyColorPalette()).toBe(true);

    wrapper.findComponent(FilterOverviewStub).vm.$emit('reset');
    await wrapper.vm.$nextTick();
    wrapper.findComponent(FilterViewSheetStub).vm.$emit('apply', categorical);
    await wrapper.vm.$nextTick();
    expect(coloring.props('palette').pLabel).toBe('8 Colors');
  });

  it('preserves No coloring when a paused draft is reloaded and resumed', async () => {
    configureColoringViews();
    mocks.fetchResolveFilter.mockResolvedValue({
      queryResult: { resultEntries: [], groupingAvailable: true, availableGroups: [] },
      trackVersions: new Map(),
      filterGroups: new Map(),
      standardFilterCount: 0,
    });
    const wrapper = mountFilter();
    await flushPromises();
    const coloring = wrapper.findComponent(FilterColoringSheetStub);
    const noColoring = coloring.props('palettes').find((palette: ColorPalette) => palette.pLabel === 'No coloring');
    coloring.vm.$emit('apply', { palette: noColoring, legendSortStrategy: null });
    await wrapper.vm.$nextTick();
    wrapper.findComponent(FilterOverviewStub).vm.$emit('update:enabled', false);
    await wrapper.vm.$nextTick();
    wrapper.unmount();

    const reloaded = mountFilter();
    await flushPromises();
    const overview = reloaded.findComponent(FilterOverviewStub);
    expect(overview.props('enabled')).toBe(false);
    expect(reloaded.findComponent(FilterColoringSheetStub).props('palette').pLabel).toBe('No coloring');
    overview.vm.$emit('update:enabled', true);
    await reloaded.vm.$nextTick();
    expect(reloaded.findComponent(FilterColoringSheetStub).props('palette').isEmptyColorPalette()).toBe(true);
  });

  it('keeps preferred defaults and switches incompatible selected palettes', async () => {
    const { categorical, gradient } = configureColoringViews();
    const wrapper = mountFilter();
    await flushPromises();
    const coloring = wrapper.findComponent(FilterColoringSheetStub);
    expect(coloring.props('palette').domain3).toBe('008_COLORS');
    wrapper.findComponent(FilterViewSheetStub).vm.$emit('apply', gradient);
    await wrapper.vm.$nextTick();
    expect(coloring.props('palette').domain3).toBe('GRADIENT_100_VIRIDIS');
    wrapper.findComponent(FilterViewSheetStub).vm.$emit('apply', categorical);
    await wrapper.vm.$nextTick();
    expect(coloring.props('palette').domain3).toBe('008_COLORS');
  });

  it('uses the next view default after resetting map colors in a view without a preferred palette', async () => {
    const { categorical } = configureColoringViews();
    mocks.ensureLoaded.mockResolvedValue({ filterInfo: smartBaseFilter, filterParams: {}, palette: {} });
    const wrapper = mountFilter();
    await flushPromises();
    const coloring = wrapper.findComponent(FilterColoringSheetStub);
    coloring.vm.$emit('apply', { palette: coloring.props('defaultPalette'), legendSortStrategy: null });
    await wrapper.vm.$nextTick();
    expect(coloring.props('palette').isEmptyColorPalette()).toBe(true);
    wrapper.findComponent(FilterViewSheetStub).vm.$emit('apply', categorical);
    await wrapper.vm.$nextTick();
    expect(coloring.props('palette').pLabel).toBe('8 Colors');
  });

  it('passes the saved category order to the view picker and resets it only for a different view', async () => {
    configureColoringViews();
    const wrapper = mountFilter();
    await flushPromises();
    const coloring = wrapper.findComponent(FilterColoringSheetStub);
    coloring.vm.$emit('apply', { palette: coloring.props('palette'), legendSortStrategy: 'COUNT_DESC' });
    await wrapper.vm.$nextTick();
    const viewSheet = wrapper.findComponent(FilterViewSheetStub);
    expect(viewSheet.props('legendSortStrategy')).toBe('COUNT_DESC');
    viewSheet.vm.$emit('apply', viewSheet.props('selectedFilterInfo'));
    await wrapper.vm.$nextTick();
    expect(viewSheet.props('legendSortStrategy')).toBe('COUNT_DESC');
    viewSheet.vm.$emit('apply', smartBaseFilter);
    await wrapper.vm.$nextTick();
    expect(viewSheet.props('legendSortStrategy')).toBeNull();
  });

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
    expect(overview.props('resultText')).toBe('Filtering is off');
    expect(overview.props('resultDetail')).toBe('Showing all tracks. Your filter setup is kept.');

    overview.vm.$emit('update:enabled', true);
    await wrapper.vm.$nextTick();
    expect(overview.props('enabled')).toBe(true);
  });

  it('enables filtering when paused configuration changes are applied', async () => {
    const wrapper = mountFilter();
    await flushPromises();

    const overview = wrapper.findComponent(FilterOverviewStub);
    const pause = async () => {
      overview.vm.$emit('update:enabled', false);
      await wrapper.vm.$nextTick();
      expect(overview.props('enabled')).toBe(false);
      expect(wrapper.findComponent(FilterViewSheetStub).props('enabled')).toBe(false);
      expect(wrapper.findComponent(FilterCriteriaSheetStub).props('enabled')).toBe(false);
      expect(wrapper.findComponent(FilterCategoriesSheetStub).props('enabled')).toBe(false);
      expect(wrapper.findComponent(FilterColoringSheetStub).props('enabled')).toBe(false);
    };

    await pause();
    wrapper.findComponent(FilterViewSheetStub).vm.$emit('apply', activitiesFilter);
    await wrapper.vm.$nextTick();
    expect(overview.props('enabled')).toBe(true);

    await pause();
    wrapper.findComponent(FilterCategoriesSheetStub).vm.$emit('apply', undefined);
    await wrapper.vm.$nextTick();
    expect(overview.props('enabled')).toBe(true);

    await pause();
    const palette = wrapper.findComponent(FilterColoringSheetStub).props('palette');
    wrapper.findComponent(FilterColoringSheetStub).vm.$emit('apply', {
      palette,
      legendSortStrategy: null,
    });
    await wrapper.vm.$nextTick();
    expect(overview.props('enabled')).toBe(true);

    await pause();
    wrapper.findComponent(FilterCriteriaSheetStub).vm.$emit('apply-and-enable', {
      filterParams: { stringParams: { ACTIVITY: 'WALKING' } },
      clearedGeoParams: [],
    });
    await wrapper.vm.$nextTick();
    expect(overview.props('enabled')).toBe(true);
    expect(localStorage.getItem(STORAGE_KEYS.filterPaused)).toBe('false');
  });

  it('opens directly on the overview on a first visit and keeps help available', async () => {
    localStorage.removeItem(STORAGE_KEYS.filterScopeHelpSeen);
    const wrapper = mountFilter(false);
    await flushPromises();

    await wrapper.setProps({ show: true });
    expect(wrapper.find('.filter-header__panel-title').text()).toBe('Filter');
    expect(wrapper.findComponent(FilterScopeHelpStub).exists()).toBe(false);

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

  it.each([
    [
      'summer noon',
      '2025-06-15T12:00:00+02:00',
      '2025-06-15T12:01:00+02:00',
      '2025-06-15T10:00:00.000Z',
      '2025-06-15T10:01:00.000Z',
    ],
    [
      'winter noon',
      '2025-01-15T12:00:00+01:00',
      '2025-01-15T12:01:00+01:00',
      '2025-01-15T11:00:00.000Z',
      '2025-01-15T11:01:00.000Z',
    ],
    [
      'New Year',
      '2025-01-01T00:00:00+01:00',
      '2025-12-31T23:59:59+01:00',
      '2024-12-31T23:00:00.000Z',
      '2025-12-31T22:59:59.000Z',
    ],
    [
      'spring DST',
      '2024-03-31T01:59:00+01:00',
      '2024-03-31T03:00:00+02:00',
      '2024-03-31T00:59:00.000Z',
      '2024-03-31T01:00:00.000Z',
    ],
    [
      'autumn DST',
      '2024-10-27T01:59:00+02:00',
      '2024-10-27T03:00:00+01:00',
      '2024-10-26T23:59:00.000Z',
      '2024-10-27T02:00:00.000Z',
    ],
  ])('sends and restores absolute date bounds for %s', async (_label, from, to, utcFrom, utcTo) => {
    const dateFilter: FilterInfo = {
      ...smartBaseFilter,
      paramDefinitions: [
        { name: 'DATE_TIME_FROM', type: 'DATE_TIME' },
        { name: 'DATE_TIME_TO', type: 'DATE_TIME' },
      ],
    };
    mocks.fetchFilters.mockResolvedValue([dateFilter]);
    mocks.ensureLoaded.mockResolvedValue({ filterInfo: dateFilter, filterParams: {}, palette: {} });
    const wrapper = mountFilter();
    await flushPromises();
    wrapper.findComponent(FilterCriteriaSheetStub).vm.$emit('change', {
      filterParams: { dateTimeParams: { DATE_TIME_FROM: new Date(from), DATE_TIME_TO: new Date(to) } },
      clearedGeoParams: [],
    });
    await vi.advanceTimersByTimeAsync(400);
    await flushPromises();
    const expectedBounds = { DATE_TIME_FROM: utcFrom, DATE_TIME_TO: utcTo };
    expect(mocks.fetchResolveFilter).toHaveBeenLastCalledWith(
      3,
      expect.objectContaining({ dateTimeParams: expectedBounds }),
      false
    );
    const saved = mocks.applyResolvedFilter.mock.calls.at(-1)?.[0];
    expect(saved.filterParams.dateTimeParams).toEqual(expectedBounds);
    wrapper.unmount();

    mocks.ensureLoaded.mockResolvedValue(saved);
    mocks.fetchResolveFilter.mockClear();
    const restored = mountFilter();
    await flushPromises();
    const restoredBounds = restored.findComponent(FilterCriteriaSheetStub).props('filterParams').dateTimeParams;
    expect(restoredBounds.DATE_TIME_FROM).toEqual(new Date(from));
    expect(restoredBounds.DATE_TIME_TO).toEqual(new Date(to));
    await vi.advanceTimersByTimeAsync(400);
    await flushPromises();
    expect(mocks.fetchResolveFilter).toHaveBeenLastCalledWith(
      3,
      expect.objectContaining({ dateTimeParams: expectedBounds }),
      false
    );
    restored.unmount();
  });

  it('keeps the last successful map for reversed dates and resolves once after correction', async () => {
    const dateFilter: FilterInfo = {
      ...smartBaseFilter,
      paramDefinitions: [
        { name: 'DATE_TIME_FROM', type: 'DATE_TIME' },
        { name: 'DATE_TIME_TO', type: 'DATE_TIME' },
      ],
    };
    const from = new Date(2026, 7, 1);
    const to = new Date(2026, 7, 31, 23, 59, 59);
    const lastResult = {
      queryResult: { resultEntries: [{ id: 1 }] },
      trackVersions: new Map([[1, 1]]),
      filterGroups: new Map(),
      standardFilterCount: 1,
    };
    mocks.store!.activeResult = lastResult;
    mocks.fetchFilters.mockResolvedValue([dateFilter]);
    mocks.ensureLoaded.mockResolvedValue({
      filterInfo: dateFilter,
      filterParams: { dateTimeParams: { DATE_TIME_FROM: from, DATE_TIME_TO: to } },
      palette: {},
    });
    mocks.fetchResolveFilter.mockResolvedValue(lastResult);
    const wrapper = mountFilter();
    await flushPromises();
    const criteria = wrapper.findComponent(FilterCriteriaSheetStub);
    criteria.vm.$emit('change', {
      filterParams: { dateTimeParams: { DATE_TIME_FROM: from, DATE_TIME_TO: new Date(2026, 6, 31) } },
      clearedGeoParams: [],
    });
    await vi.advanceTimersByTimeAsync(400);
    await flushPromises();
    expect(mocks.fetchResolveFilter).not.toHaveBeenCalled();
    expect(mocks.applyResolvedFilter).not.toHaveBeenCalled();
    expect(mocks.store!.activeResult).toEqual(lastResult);
    expect(wrapper.findComponent(FilterOverviewStub).props('resultText')).toBe('Check filter criteria');
    expect(wrapper.findComponent(FilterOverviewStub).props('resultDetail')).toBe(
      'The end must be on or after the start.'
    );

    criteria.vm.$emit('change', {
      filterParams: { dateTimeParams: { DATE_TIME_FROM: from, DATE_TIME_TO: to } },
      clearedGeoParams: [],
    });
    await vi.advanceTimersByTimeAsync(400);
    await flushPromises();
    expect(mocks.fetchResolveFilter).toHaveBeenCalledOnce();
    expect(mocks.applyResolvedFilter).toHaveBeenCalledOnce();
  });

  it('opens required criteria and keeps the prior result when a view is incomplete', async () => {
    const requiredFilter: FilterInfo = {
      ...activitiesFilter,
      paramDefinitions: [{ name: 'KEYWORD', type: 'STRING', label: 'Keyword' }],
    };
    mocks.fetchFilters.mockResolvedValue([yearsFilter, requiredFilter]);
    const wrapper = mountFilter();
    await flushPromises();
    wrapper.findComponent(FilterViewSheetStub).vm.$emit('apply', requiredFilter);
    await wrapper.vm.$nextTick();
    expect(wrapper.findComponent(FilterCriteriaSheetStub).props('modelValue')).toBe(true);
    await vi.advanceTimersByTimeAsync(400);
    expect(mocks.fetchResolveFilter).not.toHaveBeenCalled();
    expect(mocks.applyResolvedFilter).not.toHaveBeenCalled();
    expect(wrapper.findComponent(FilterOverviewStub).props('resultText')).toBe('Check filter criteria');
    expect(wrapper.findComponent(FilterOverviewStub).props('resultActionLabel')).toBe('Edit criteria');
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

  it('notifies the tool that Show on map closes the filter sheet', async () => {
    const wrapper = mountFilter();
    await flushPromises();
    wrapper.findComponent({ name: 'FilterSheetActions' }).vm.$emit('apply');
    expect(wrapper.emitted('update:show')?.at(-1)).toEqual([false]);
    expect(wrapper.emitted('closed')).toHaveLength(1);
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

    wrapper.findComponent({ name: 'FilterSheetActions' }).vm.$emit('cancel');
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

    wrapper.findComponent({ name: 'FilterSheetActions' }).vm.$emit('cancel');
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
    wrapper.findComponent(FilterOverviewStub).vm.$emit('open-criteria');
    await wrapper.vm.$nextTick();
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

  function configureTrackPicker() {
    mocks.fetchFilters.mockResolvedValue([smartBaseFilter, trackPickerFilter, yearsFilter]);
    mocks.ensureLoaded.mockResolvedValue({
      filterInfo: trackPickerFilter,
      filterParams: { stringParams: { TRACK_IDS: '11' } },
      palette: {},
    });
    mocks.fetchResolveFilter.mockResolvedValue({
      queryResult: { resultEntries: [{ id: 11, gpsTrack: { id: 11, trackName: 'Morning walk' } }] },
      trackVersions: new Map([[11, 1]]),
      filterGroups: new Map(),
      standardFilterCount: 1,
    });
  }

  it('applies the saved filter at startup without loading picker choices until criteria opens', async () => {
    configureTrackPicker();
    const wrapper = mountFilter(false);
    await flushPromises();
    await vi.advanceTimersByTimeAsync(400);
    await flushPromises();

    expect(mocks.fetchResolveFilter).toHaveBeenCalledWith(
      5,
      expect.objectContaining({ stringParams: { TRACK_IDS: '11' } }),
      false
    );
    expect(mocks.applyResolvedFilter).toHaveBeenCalled();
    expect(mocks.fetchResolveFilter.mock.calls.some((call) => call[2] === true)).toBe(false);
    await wrapper.setProps({ show: true });
    wrapper.findComponent(FilterOverviewStub).vm.$emit('open-criteria');
    await wrapper.vm.$nextTick();
    await vi.advanceTimersByTimeAsync(350);
    await flushPromises();
    expect(mocks.fetchResolveFilter).toHaveBeenCalledWith(3, {}, true);
    const criteria = wrapper.findComponent(FilterCriteriaSheetStub);
    expect(criteria.props('filterParams').stringParams.TRACK_IDS).toBe('11');
    expect(criteria.props('trackIdCandidateTracks')).toEqual([expect.objectContaining({ id: 11 })]);

    criteria.vm.$emit('update:modelValue', false);
    await wrapper.vm.$nextTick();
    wrapper.findComponent(FilterOverviewStub).vm.$emit('open-criteria');
    await wrapper.vm.$nextTick();
    await vi.advanceTimersByTimeAsync(350);
    await flushPromises();
    expect(mocks.fetchResolveFilter.mock.calls.filter((call) => call[2] === true)).toHaveLength(1);
  });

  it('cancels a scheduled picker request when criteria closes and supports filter switching', async () => {
    configureTrackPicker();
    const wrapper = mountFilter();
    await flushPromises();
    const overview = wrapper.findComponent(FilterOverviewStub);
    const criteria = wrapper.findComponent(FilterCriteriaSheetStub);
    overview.vm.$emit('open-criteria');
    await wrapper.vm.$nextTick();
    criteria.vm.$emit('update:modelValue', false);
    await wrapper.vm.$nextTick();
    await vi.advanceTimersByTimeAsync(400);
    await flushPromises();
    expect(mocks.fetchResolveFilter.mock.calls.some((call) => call[2] === true)).toBe(false);

    wrapper.findComponent(FilterViewSheetStub).vm.$emit('apply', yearsFilter);
    overview.vm.$emit('open-criteria');
    await wrapper.vm.$nextTick();
    await vi.advanceTimersByTimeAsync(400);
    expect(mocks.fetchResolveFilter.mock.calls.some((call) => call[2] === true)).toBe(false);
    wrapper.findComponent(FilterViewSheetStub).vm.$emit('apply', trackPickerFilter);
    await wrapper.vm.$nextTick();
    await vi.advanceTimersByTimeAsync(400);
    await flushPromises();
    expect(mocks.fetchResolveFilter.mock.calls.filter((call) => call[2] === true)).toHaveLength(1);
  });

  it('defers freshness refresh while criteria is closed, then refreshes on reopening', async () => {
    configureTrackPicker();
    const wrapper = mountFilter();
    await flushPromises();
    wrapper.findComponent(FilterOverviewStub).vm.$emit('open-criteria');
    await wrapper.vm.$nextTick();
    await vi.advanceTimersByTimeAsync(350);
    await flushPromises();
    const criteria = wrapper.findComponent(FilterCriteriaSheetStub);
    criteria.vm.$emit('update:modelValue', false);
    await wrapper.vm.$nextTick();
    (mocks.store as { dataFreshnessRevision: number }).dataFreshnessRevision += 1;
    await wrapper.vm.$nextTick();
    await vi.advanceTimersByTimeAsync(400);
    expect(mocks.fetchResolveFilter.mock.calls.filter((call) => call[2] === true)).toHaveLength(1);
    wrapper.findComponent(FilterOverviewStub).vm.$emit('open-criteria');
    await wrapper.vm.$nextTick();
    await vi.advanceTimersByTimeAsync(350);
    await flushPromises();
    expect(mocks.fetchResolveFilter.mock.calls.filter((call) => call[2] === true)).toHaveLength(2);
  });

  it('reuses complete cached track metadata when criteria opens', async () => {
    configureTrackPicker();
    mocks.loadCachedTracks.mockResolvedValue(new Map([[11, { id: 11, trackName: 'Cached walk' }]]));
    const wrapper = mountFilter();
    await wrapper.setProps({ totalTrackCount: 1 });
    await flushPromises();
    wrapper.findComponent(FilterOverviewStub).vm.$emit('open-criteria');
    await wrapper.vm.$nextTick();
    await vi.advanceTimersByTimeAsync(350);
    await flushPromises();
    expect(mocks.fetchResolveFilter.mock.calls.some((call) => call[2] === true)).toBe(false);
    expect(wrapper.findComponent(FilterCriteriaSheetStub).props('trackIdCandidateTracks')).toEqual([
      expect.objectContaining({ id: 11, trackName: 'Cached walk' }),
    ]);
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
    expect(overview.props('resultText')).toBe('Filtering is off');
    expect(overview.props('resultDetail')).toBe('Showing all tracks. Your filter setup is kept.');
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
