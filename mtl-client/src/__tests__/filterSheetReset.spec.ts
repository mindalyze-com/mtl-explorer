import { mount } from '@vue/test-utils';
import { defineComponent } from 'vue';
import { describe, expect, it } from 'vitest';
import type { FilterInfo } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterInfo';
import type { FilterParamsRequest } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterParamsRequest';

import { ColorPalette } from '@/components/filter/ColorPalette';
import FilterCategoriesSheet from '@/components/filter/FilterCategoriesSheet.vue';
import FilterColoringSheet from '@/components/filter/FilterColoringSheet.vue';
import FilterCriteriaSheet from '@/components/filter/FilterCriteriaSheet.vue';
import FilterViewSheet from '@/components/filter/FilterViewSheet.vue';

const BottomSheetStub = defineComponent({
  name: 'BottomSheet',
  template:
    '<section><header><slot name="header-actions" /></header><slot /><footer><slot name="footer" /></footer></section>',
});

const FilterCatalogStub = defineComponent({
  name: 'FilterCatalog',
  emits: ['select-filter'],
  template: '<div data-test="filter-catalog"></div>',
});

const FilterDetailPanelStub = defineComponent({
  name: 'FilterDetailPanel',
  emits: ['set-date-time-param', 'set-date-range', 'set-string-param', 'start-geo-drawing', 'clear-geo-shape'],
  template: '<div data-test="criteria-panel"></div>',
});

const FilterResultGroupSelectorStub = defineComponent({
  name: 'FilterResultGroupSelector',
  template: '<div data-test="category-selector"></div>',
});

const SelectStub = defineComponent({
  name: 'Select',
  template: '<div data-test="select"></div>',
});

describe('filter detail reset actions', () => {
  it('warns before a different view resets custom category order and preserves it on cancel', async () => {
    const current = { filterConfig: { id: 1, filterName: 'Activities' } } as FilterInfo;
    const next = { filterConfig: { id: 2, filterName: 'MainActivities' } } as FilterInfo;
    const wrapper = mount(FilterViewSheet, {
      props: { modelValue: true, groups: [], selectedFilterInfo: current, legendSortStrategy: 'COUNT_DESC' },
      global: { stubs: { BottomSheet: BottomSheetStub, FilterCatalog: FilterCatalogStub } },
    });
    expect(wrapper.find('.filter-sheet-feedback').exists()).toBe(false);
    wrapper.findComponent(FilterCatalogStub).vm.$emit('select-filter', next);
    await wrapper.vm.$nextTick();
    expect(wrapper.get('.filter-sheet-feedback').text()).toBe('Category order resets to the new view’s default.');
    await wrapper.get('.filter-sheet-actions__cancel').trigger('click');
    expect(wrapper.emitted('apply')).toBeUndefined();
    expect(wrapper.props('legendSortStrategy')).toBe('COUNT_DESC');

    await wrapper.setProps({ modelValue: false });
    await wrapper.setProps({ modelValue: true });
    expect(wrapper.find('.filter-sheet-feedback').exists()).toBe(false);
    wrapper.findComponent(FilterCatalogStub).vm.$emit('select-filter', next);
    await wrapper.vm.$nextTick();
    await wrapper.setProps({ legendSortStrategy: null });
    expect(wrapper.find('.filter-sheet-feedback').exists()).toBe(false);
    wrapper.unmount();
  });

  it('lists only incompatible active criteria before applying a staged view and preserves them on cancel', async () => {
    const dates = [
      { name: 'DATE_TIME_FROM', type: 'DATE_TIME' },
      { name: 'DATE_TIME_TO', type: 'DATE_TIME' },
    ] as FilterInfo['paramDefinitions'];
    const keywordView = {
      filterConfig: { id: 1, filterName: 'ActivitiesByKeyword' },
      paramDefinitions: [...(dates ?? []), { name: 'SEARCH_WORD', type: 'STRING' }],
      effectiveUiMetadata: { params: { SEARCH_WORD: { label: 'Keyword' } } },
    } as FilterInfo;
    const baseView = {
      filterConfig: { id: 2, filterName: 'SmartBaseFilter' },
      paramDefinitions: dates,
    } as FilterInfo;
    const filterParams: FilterParamsRequest = {
      stringParams: { SEARCH_WORD: 'repeated', UNUSED: '  ' },
      dateTimeParams: {
        DATE_TIME_FROM: new Date('2026-08-01T00:00:00Z'),
        DATE_TIME_TO: new Date('2026-08-31T23:59:00Z'),
      },
    };
    const wrapper = mount(FilterViewSheet, {
      props: { modelValue: true, groups: [], selectedFilterInfo: keywordView, filterParams },
      global: { stubs: { BottomSheet: BottomSheetStub, FilterCatalog: FilterCatalogStub } },
    });

    expect(wrapper.find('.filter-sheet-feedback').exists()).toBe(false);
    wrapper.findComponent(FilterCatalogStub).vm.$emit('select-filter', baseView);
    await wrapper.vm.$nextTick();
    expect(wrapper.get('.filter-sheet-feedback').text()).toBe('Changing to this view clears: Keyword.');
    expect(wrapper.emitted('apply')).toBeUndefined();

    await wrapper.get('.filter-sheet-actions__cancel').trigger('click');
    expect(wrapper.emitted('apply')).toBeUndefined();
    expect(filterParams.stringParams?.SEARCH_WORD).toBe('repeated');
    expect(filterParams.dateTimeParams?.DATE_TIME_FROM).toEqual(new Date('2026-08-01T00:00:00Z'));

    await wrapper.setProps({ modelValue: false });
    await wrapper.setProps({ modelValue: true });
    expect(wrapper.find('.filter-sheet-feedback').exists()).toBe(false);
    wrapper.findComponent(FilterCatalogStub).vm.$emit('select-filter', baseView);
    await wrapper.vm.$nextTick();
    await wrapper.get('.filter-sheet-actions__apply').trigger('click');
    expect(wrapper.emitted('apply')).toEqual([[baseView]]);
    wrapper.unmount();
  });

  it('keeps the off state and Apply and enable action visible in every editable sheet', () => {
    const wrappers = [
      mount(FilterViewSheet, {
        props: { modelValue: true, enabled: false, groups: [] },
        global: { stubs: { BottomSheet: BottomSheetStub, FilterCatalog: FilterCatalogStub } },
      }),
      mount(FilterCriteriaSheet, {
        props: { modelValue: true, enabled: false },
        global: { stubs: { BottomSheet: BottomSheetStub, FilterDetailPanel: FilterDetailPanelStub } },
      }),
      mount(FilterCategoriesSheet, {
        props: { modelValue: true, enabled: false },
        global: { stubs: { BottomSheet: BottomSheetStub, FilterResultGroupSelector: FilterResultGroupSelectorStub } },
      }),
      mount(FilterColoringSheet, {
        props: { modelValue: true, enabled: false },
        global: { stubs: { BottomSheet: BottomSheetStub, Select: SelectStub } },
      }),
    ];

    for (const wrapper of wrappers) {
      expect(wrapper.get('.filter-off-badge').text()).toContain('Filtering off');
      expect(wrapper.get('.filter-sheet-actions__apply').text()).toBe('Apply and enable');
      wrapper.unmount();
    }
  });

  it('confirms paused criteria before enabling them', async () => {
    const wrapper = mount(FilterCriteriaSheet, {
      props: { modelValue: true, enabled: false, filterParams: {} },
      global: { stubs: { BottomSheet: BottomSheetStub, FilterDetailPanel: FilterDetailPanelStub } },
    });

    wrapper.findComponent(FilterDetailPanelStub).vm.$emit('set-string-param', {
      name: 'search',
      value: 'Jura',
    });
    await wrapper.vm.$nextTick();
    await wrapper.get('.filter-sheet-actions__apply').trigger('click');

    expect(wrapper.text()).toContain('Make changes, then choose Apply and enable.');
    expect(wrapper.emitted('apply-and-enable')?.[0]?.[0]).toEqual({
      filterParams: expect.objectContaining({ stringParams: { search: 'Jura' } }),
      clearedGeoParams: [],
    });
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([false]);
  });

  it('applies text criteria immediately without closing the sheet', async () => {
    const wrapper = mount(FilterCriteriaSheet, {
      props: { modelValue: true, filterParams: {} },
      global: { stubs: { BottomSheet: BottomSheetStub, FilterDetailPanel: FilterDetailPanelStub } },
    });

    wrapper.findComponent(FilterDetailPanelStub).vm.$emit('set-string-param', {
      name: 'search',
      value: 'Jura',
    });
    await wrapper.vm.$nextTick();

    const update = wrapper.emitted('change')?.[0]?.[0] as {
      filterParams: FilterParamsRequest;
      clearedGeoParams: Array<{ name?: string }>;
    };
    expect(update.filterParams.stringParams).toEqual({ search: 'Jura' });
    expect(update.clearedGeoParams).toEqual([]);
    expect(wrapper.emitted('update:modelValue')).toBeUndefined();
    expect(wrapper.text()).toContain('Changes apply automatically.');
  });

  it('updates a date shortcut atomically and preserves category and track selections', async () => {
    const wrapper = mount(FilterCriteriaSheet, {
      props: {
        modelValue: true,
        filterParams: {
          stringParams: { TRACK_IDS: '1,2' },
          resultGroupSelection: { includedGroups: [{ value: 'Walking' }] },
        },
      },
      global: { stubs: { BottomSheet: BottomSheetStub, FilterDetailPanel: FilterDetailPanelStub } },
    });
    const range = { from: new Date(2026, 0, 1), to: new Date(2026, 8, 6, 23, 59, 59) };
    wrapper.findComponent(FilterDetailPanelStub).vm.$emit('set-date-range', range);
    await wrapper.vm.$nextTick();
    expect(wrapper.emitted('change')).toHaveLength(1);
    expect(wrapper.emitted('change')?.[0]?.[0]).toMatchObject({
      filterParams: {
        dateTimeParams: { DATE_TIME_FROM: range.from, DATE_TIME_TO: range.to },
        stringParams: { TRACK_IDS: '1,2' },
        resultGroupSelection: { includedGroups: [{ value: 'Walking' }] },
      },
    });
    expect(wrapper.get('.filter-sheet-actions__apply').text()).toBe('Done');
    await wrapper.get('.filter-sheet-actions__apply').trigger('click');
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([false]);
    expect(wrapper.emitted('apply-and-enable')).toBeUndefined();
  });

  it('updates staged category counts before applying and discards them on cancel', async () => {
    const wrapper = mount(FilterCategoriesSheet, {
      props: {
        modelValue: true,
        effectiveCount: 9,
        preSelectionCount: 9,
        availableGroups: [
          { key: { value: 'Walking' }, count: 4 },
          { key: { value: 'Hiking' }, count: 5 },
        ],
      },
      global: { stubs: { BottomSheet: BottomSheetStub } },
    });
    await wrapper.findAll('.result-group-selector__row input')[1].setValue(false);
    expect(wrapper.get('.result-group-selector__summary').text()).toContain('4 of 9 matching tracks');
    expect(wrapper.get('.filter-categories-sheet__selection').text()).toContain('4 tracks selected');
    expect(wrapper.emitted('apply')).toBeUndefined();
    await wrapper.get('.filter-sheet-actions__cancel').trigger('click');
    await wrapper.setProps({ modelValue: false });
    await wrapper.setProps({ modelValue: true });
    expect(wrapper.get('.result-group-selector__summary').text()).toContain('9 matching tracks');
  });

  it('resets criteria immediately and reports removed map geometry', async () => {
    const filterInfo = {
      paramDefinitions: [{ name: 'area', type: 'GEO_RECTANGLE' }],
    } as FilterInfo;
    const filterParams = {
      stringParams: { search: 'lake' },
      dateTimeParams: {},
      geoCircles: {},
      geoRectangles: { area: { north: 1, south: 0, east: 1, west: 0 } },
      geoPolygons: {},
      resultGroupSelection: { includedGroups: [{ value: 'Walking' }] },
    } as unknown as FilterParamsRequest;
    const wrapper = mount(FilterCriteriaSheet, {
      props: { modelValue: true, filterInfo, filterParams },
      global: { stubs: { BottomSheet: BottomSheetStub, FilterDetailPanel: FilterDetailPanelStub } },
    });

    const reset = wrapper.get('[aria-label="Reset criteria"]');
    expect(reset.text()).toContain('Reset');
    await reset.trigger('click');

    expect(wrapper.find('[aria-label="Reset criteria"]').exists()).toBe(false);
    const update = wrapper.emitted('change')?.[0]?.[0] as {
      filterParams: FilterParamsRequest;
      clearedGeoParams: Array<{ name?: string }>;
    };
    expect(update.filterParams.stringParams).toEqual({});
    expect(update.filterParams.geoRectangles).toEqual({});
    expect(update.filterParams.resultGroupSelection?.includedGroups).toEqual([{ value: 'Walking' }]);
    expect(update.clearedGeoParams.map((definition) => definition.name)).toEqual(['area']);
  });

  it('resets category selection to the durable all-categories state', async () => {
    const wrapper = mount(FilterCategoriesSheet, {
      props: {
        modelValue: true,
        availableGroups: [{ key: { value: 'Walking' }, count: 4 }],
        selection: { includedGroups: [{ value: 'Walking' }] },
      },
      global: {
        stubs: {
          BottomSheet: BottomSheetStub,
          FilterResultGroupSelector: FilterResultGroupSelectorStub,
        },
      },
    });

    await wrapper.get('[aria-label="Reset categories"]').trigger('click');
    expect(wrapper.find('[aria-label="Reset categories"]').exists()).toBe(false);

    await wrapper.get('.filter-sheet-actions__apply').trigger('click');
    expect(wrapper.emitted('apply')?.[0]?.[0]).toBeUndefined();
  });

  it('keeps the last unavailable selected category removable', async () => {
    const wrapper = mount(FilterCategoriesSheet, {
      props: {
        modelValue: true,
        availableGroups: [],
        selection: { includedGroups: [{ value: 'WALKING' }] },
      },
      global: { stubs: { BottomSheet: BottomSheetStub } },
    });

    const unavailableRow = wrapper.get('.result-group-selector__row--missing');
    expect(unavailableRow.text()).toContain('WALKING');
    expect(unavailableRow.text()).toContain('No matches with current parameters');
    expect((unavailableRow.get('input').element as HTMLInputElement).checked).toBe(true);

    await unavailableRow.get('input').setValue(false);
    expect(wrapper.find('.result-group-selector__row--missing').exists()).toBe(false);

    const apply = wrapper.get('.filter-sheet-actions__apply');
    expect((apply.element as HTMLButtonElement).disabled).toBe(false);
    await apply.trigger('click');
    expect(wrapper.emitted('apply')?.[0]?.[0]).toEqual({ includedGroups: [] });
  });

  it('resets map colors to the view default', async () => {
    const defaultPalette = new ColorPalette();
    defaultPalette.id = 1;
    defaultPalette.pLabel = 'Default';
    defaultPalette.pColors = ['#111111'];
    const customPalette = new ColorPalette();
    customPalette.id = 2;
    customPalette.pLabel = 'Custom';
    customPalette.pColors = ['#222222'];

    const wrapper = mount(FilterColoringSheet, {
      props: {
        modelValue: true,
        palettes: [defaultPalette, customPalette],
        palette: customPalette,
        defaultPalette,
      },
      global: { stubs: { BottomSheet: BottomSheetStub, Select: SelectStub } },
    });

    await wrapper.get('[aria-label="Reset map colors"]').trigger('click');
    expect(wrapper.find('[aria-label="Reset map colors"]').exists()).toBe(false);

    await wrapper.get('.filter-sheet-actions__apply').trigger('click');
    expect(wrapper.emitted('apply')?.[0]?.[0]).toEqual({
      palette: defaultPalette,
      legendSortStrategy: null,
    });
  });

  it('offers Reset for explicit No coloring when the view default has no palette', async () => {
    const defaultPalette = new ColorPalette();
    const wrapper = mount(FilterColoringSheet, {
      props: {
        modelValue: true,
        palette: ColorPalette.withoutColoring(),
        defaultPalette,
      },
      global: { stubs: { BottomSheet: BottomSheetStub, Select: SelectStub } },
    });

    await wrapper.get('[aria-label="Reset map colors"]').trigger('click');
    expect(wrapper.find('[aria-label="Reset map colors"]').exists()).toBe(false);
    await wrapper.get('.filter-sheet-actions__apply').trigger('click');
    expect(wrapper.emitted('apply')?.[0]?.[0]).toEqual({ palette: defaultPalette, legendSortStrategy: null });
    expect(wrapper.props('palette')?.noColoring).toBe(true);
  });
});
