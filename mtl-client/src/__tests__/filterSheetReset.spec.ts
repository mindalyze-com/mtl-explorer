import { mount } from '@vue/test-utils';
import { defineComponent } from 'vue';
import { describe, expect, it } from 'vitest';
import type { FilterInfo } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterInfo';
import type { FilterParamsRequest } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterParamsRequest';

import { ColorPalette } from '@/components/filter/ColorPalette';
import FilterCategoriesSheet from '@/components/filter/FilterCategoriesSheet.vue';
import FilterColoringSheet from '@/components/filter/FilterColoringSheet.vue';
import FilterCriteriaSheet from '@/components/filter/FilterCriteriaSheet.vue';

const BottomSheetStub = defineComponent({
  name: 'BottomSheet',
  template: '<section><slot /><footer><slot name="footer" /></footer></section>',
});

const FilterDetailPanelStub = defineComponent({
  name: 'FilterDetailPanel',
  emits: ['set-date-time-param', 'set-string-param', 'start-geo-drawing', 'clear-geo-shape'],
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
});
