import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';

import FilterOverview from '@/components/filter/FilterOverview.vue';

const defaultProps = {
  enabled: true,
  resultText: '12 matching tracks',
  resultDetail: 'Based on the current criteria and categories.',
  showReviewAction: true,
  viewSummary: 'Smart Base Filter',
  criteriaSummary: 'No criteria',
  categoriesSummary: 'All 7 categories',
  categoriesAvailable: true,
  colorsSummary: 'No coloring · Default order',
  colorsAvailable: true,
};

describe('FilterOverview', () => {
  it('shows the active saved view and criterion as one concise chip', async () => {
    const wrapper = mount(FilterOverview, {
      props: { ...defaultProps, activeIdentity: 'Tracks by year' },
    });

    const chip = wrapper.get('[data-test="active-filter-chip"]');
    expect(chip.text()).toBe('Tracks by year');
    expect(chip.attributes('title')).toBe('Tracks by year');

    await wrapper.setProps({ activeIdentity: 'Activities by keyword · Synthetic' });
    expect(wrapper.get('[data-test="active-filter-chip"]').text()).toBe('Activities by keyword · Synthetic');

    await wrapper.setProps({ activeIdentity: '' });
    expect(wrapper.find('[data-test="active-filter-chip"]').exists()).toBe(false);
  });

  it('keeps Review tracks as a quiet footer action beside Reset filter', async () => {
    const wrapper = mount(FilterOverview, { props: defaultProps });

    expect(wrapper.find('.filter-overview__result-actions').exists()).toBe(true);
    expect(wrapper.find('.filter-overview__toggle').exists()).toBe(false);
    const modeToggle = wrapper.get('.filter-overview__mode-toggle');
    expect(modeToggle.attributes('role')).toBe('switch');
    expect(modeToggle.attributes('aria-checked')).toBe('true');
    expect(modeToggle.attributes('aria-label')).toBe('Apply filter');
    expect(modeToggle.text()).toContain('Apply filter');
    expect(wrapper.get('.filter-overview__review').text()).toContain('Review tracks');
    expect(wrapper.get('.filter-overview__reset').text()).toContain('Reset filter');

    await wrapper.get('.filter-overview__review').trigger('click');
    expect(wrapper.emitted('review')).toHaveLength(1);

    await modeToggle.trigger('click');
    expect(wrapper.emitted('update:enabled')).toEqual([[false]]);

    await wrapper.setProps({ enabled: false });
    expect(wrapper.get('.filter-overview__mode-toggle').attributes('aria-checked')).toBe('false');
    expect(wrapper.get('.filter-overview__mode-toggle').text()).toContain('Apply filter');
  });

  it('uses the result action only for recovery and replaces it with reset feedback', async () => {
    const wrapper = mount(FilterOverview, { props: { ...defaultProps, resultActionLabel: 'Retry' } });

    expect(wrapper.get('.filter-overview__primary').text()).toContain('Retry');
    expect(wrapper.find('.filter-overview__undo').exists()).toBe(false);

    await wrapper.setProps({ resetUndoAvailable: true });

    expect(wrapper.find('.filter-overview__primary').exists()).toBe(false);
    expect(wrapper.get('.filter-overview__undo').text()).toContain('Filter reset.');
    expect(wrapper.get('.filter-overview__undo button').text()).toBe('Undo');
    expect(wrapper.get('.filter-overview__mode-toggle').text()).toContain('Apply filter');
  });

  it('keeps an empty detail line in the layout', async () => {
    const wrapper = mount(FilterOverview, { props: { ...defaultProps, resultDetail: '' } });
    const detail = wrapper.get('.filter-overview__result-detail');

    expect(detail.attributes('aria-hidden')).toBe('true');
    expect(detail.element.textContent).toContain('\u00a0');
  });
});
