import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import FilterCatalog from '@/components/filter/FilterCatalog.vue';
import FilterViewSheet from '@/components/filter/FilterViewSheet.vue';
import type { FilterInfo } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterInfo';

const year: FilterInfo = { filterConfig: { id: 1, displayName: 'Tracks by year', filterName: 'Years' } };
const activity: FilterInfo = { filterConfig: { id: 2, displayName: 'Activity types', filterName: 'Activities' } };
const groups = [
  { label: 'Time', displayOrder: 1, items: [year] },
  { label: 'Activity', displayOrder: 2, items: [activity] },
];

describe('filter view discovery', () => {
  it('keeps the staged selection visible during search and makes empty search recoverable', async () => {
    const wrapper = mount(FilterCatalog, { props: { groups, selectedFilterInfo: year } });
    expect(wrapper.get('.filter-catalog-row--active').attributes('aria-pressed')).toBe('true');
    await wrapper.get('input').setValue('unmatched');
    expect(wrapper.get('.filter-catalog__selection').text()).toContain('Tracks by year');
    expect(wrapper.get('.filter-catalog__total').text()).toBe('0 of 2 views');
    expect(wrapper.get('.empty-placeholder').text()).toContain('No views found');
    await wrapper.get('[aria-label="Clear view search"]').trigger('click');
    expect(wrapper.findAll('.filter-catalog-row')).toHaveLength(2);
  });

  it('explains which populated criteria will be removed before a view is applied', async () => {
    const datedView: FilterInfo = { ...activity, paramDefinitions: [{ name: 'DATE_TIME_FROM', type: 'DATE_TIME' }] };
    const wrapper = mount(FilterViewSheet, {
      props: {
        modelValue: true,
        groups,
        selectedFilterInfo: datedView,
        filterParams: { dateTimeParams: { DATE_TIME_FROM: '2026-01-01 00:00:00' } },
      },
      global: { stubs: { BottomSheet: { template: '<section><slot /><slot name="footer" /></section>' } } },
    });
    wrapper.findComponent(FilterCatalog).vm.$emit('select-filter', year);
    await wrapper.vm.$nextTick();
    expect(wrapper.get('.filter-sheet-feedback').text()).toBe('Changing to this view clears: Date range.');
    expect(wrapper.emitted('apply')).toBeUndefined();
  });

  it('discards an unapplied selection and old search when reopening', async () => {
    const wrapper = mount(FilterViewSheet, {
      props: { modelValue: true, groups, selectedFilterInfo: year },
      global: { stubs: { BottomSheet: { template: '<section><slot /><slot name="footer" /></section>' } } },
    });
    await wrapper.get('input').setValue('Activity');
    await wrapper.get('.filter-catalog-row').trigger('click');
    expect(wrapper.get('.filter-catalog__selection').text()).toContain('Activity types');
    await wrapper.get('.filter-sheet-actions__cancel').trigger('click');
    expect(wrapper.emitted('apply')).toBeUndefined();
    await wrapper.setProps({ modelValue: false });
    await wrapper.setProps({ modelValue: true });
    expect(wrapper.findAll('.filter-catalog-row')).toHaveLength(2);
    expect(wrapper.get('.filter-catalog-row--active').text()).toContain('Tracks by year');
  });
});
