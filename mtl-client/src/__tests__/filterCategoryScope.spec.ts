import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';

import FilterCategoryQuickSelect from '@/components/filter/FilterCategoryQuickSelect.vue';
import FilterOverview from '@/components/filter/FilterOverview.vue';
import FilterScopeHelp from '@/components/filter/FilterScopeHelp.vue';

const overviewProps = {
  enabled: true,
  resultText: '12 matching tracks',
  resultDetail: 'Based on the current criteria and categories.',
  viewSummary: 'Smart Base Filter',
  criteriaSummary: 'No criteria',
  categoriesSummary: 'All 7 categories',
  categoriesAvailable: true,
  colorsSummary: 'No coloring · Default order',
  colorsAvailable: true,
};

describe('filter scope guidance', () => {
  it('keeps the explanation with the current result and collapses it for mobile', async () => {
    const wrapper = mount(FilterOverview, { props: overviewProps });
    const result = wrapper.get('.filter-overview__result');
    const link = wrapper.get('.filter-overview__scope-link');

    expect(result.find('.filter-overview__scope-link').exists()).toBe(true);
    expect(link.get('.filter-overview__scope-title').text()).toBe('How this result works');
    expect(link.get('.filter-overview__scope-title-mobile').text()).toBe('Filters apply everywhere.');
    expect(link.get('small').text()).toBe('The current result is used throughout MTL Explorer.');
    expect(link.get('.filter-overview__scope-read').text()).toContain('Read more');

    await link.trigger('click');
    expect(wrapper.emitted('open-scope-help')).toHaveLength(1);
  });

  it('organizes the full explanation as a dedicated page', async () => {
    const wrapper = mount(FilterScopeHelp, { props: { firstVisit: true } });
    const guidance = wrapper.get('[data-test="filter-scope-help"]');

    expect(guidance.text()).toContain('Your filter shapes everything');
    expect(guidance.text()).toContain('Every view adapts');
    expect(guidance.text()).toContain('statistics, milestones, and trends become walking-only');
    expect(guidance.text()).toContain('Map colors reveal patterns');
    expect(guidance.text()).toContain('custom SQL filter');
    expect(guidance.findAll('.filter-scope-help__section')).toHaveLength(3);
    expect(wrapper.get('.filter-scope-help__important').text()).toBe('Important');
    expect(wrapper.get('.filter-scope-help__done').text()).toBe('Got it');

    await wrapper.get('.filter-scope-help__done').trigger('click');
    expect(wrapper.emitted('done')).toHaveLength(1);

    await wrapper.setProps({ firstVisit: false });
    expect(wrapper.find('.filter-scope-help__important').exists()).toBe(false);
    expect(wrapper.get('.filter-scope-help__done').text()).toBe('Back to Filter');
  });

  it('uses result-oriented copy and inclusion controls for category selection', async () => {
    const wrapper = mount(FilterCategoryQuickSelect, {
      props: {
        availableGroups: [{ key: { value: 'On foot' }, count: 4 }],
        selection: { includedGroups: [] },
      },
    });
    const categoryState = wrapper.get('.category-chip:not(.category-chip--all) .category-chip__state');

    expect(wrapper.get('.category-quick__title').text()).toBe('Included categories');
    expect(wrapper.get('.category-quick__help').text()).toBe('Choose which categories remain in the current result.');
    expect(categoryState.classes()).toContain('bi-circle');
    expect(categoryState.classes()).not.toContain('bi-eye-slash');

    await wrapper.setProps({ selection: undefined });
    expect(wrapper.get('.category-chip:not(.category-chip--all) .category-chip__state').classes()).toContain(
      'bi-check-circle-fill'
    );
  });
});
