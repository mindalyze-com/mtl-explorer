import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import MapLegend from '@/components/map/MapLegend.vue';

function mountLegend(activeFilterIdentity = 'Tracks by year') {
  return mount(MapLegend, {
    props: {
      entries: [],
      collapsed: true,
      visibleTrackCount: 15,
      totalTrackCount: 15,
      filterActive: true,
      activeFilterIdentity,
      hiddenGroups: new Set<string>(),
    },
  });
}

describe('MapLegend active filter identity', () => {
  it('shows the named active filter with counts and opens Filter from the same control', async () => {
    const wrapper = mountLegend();
    const filterButton = wrapper.get('.mtl-card__count');

    expect(wrapper.get('.mtl-card__filter-identity').text()).toBe('Tracks by year');
    expect(wrapper.get('.mtl-card__track-count').text()).toBe('15 / 15 Tracks');
    expect(filterButton.attributes('aria-label')).toBe('Open Filter. Tracks by year. 15 / 15 Tracks');

    await filterButton.trigger('click');

    expect(wrapper.emitted('chip-click')).toEqual([[]]);
  });

  it('reacts to a changed view and criterion without changing the legend toggle', async () => {
    const wrapper = mountLegend();

    await wrapper.setProps({
      activeFilterIdentity: 'Activities by keyword · Synthetic',
      entries: [{ group: 'Walking', label: 'Walking', color: '#123456', count: 3 }],
    });

    expect(wrapper.get('.mtl-card__filter-identity').text()).toBe('Activities by keyword · Synthetic');
    expect(wrapper.get('.mtl-card__track-count').text()).toBe('15 / 15 Tracks');
    const legendToggle = wrapper.get('.mtl-card__legend-toggle');
    expect(legendToggle.text()).toContain('Map visibility');

    await legendToggle.trigger('click');

    expect(wrapper.emitted('update:collapsed')).toEqual([[false]]);
    expect(wrapper.emitted('chip-click')).toBeUndefined();
  });

  it('preserves the compact unfiltered track count', async () => {
    const wrapper = mountLegend();

    await wrapper.setProps({ filterActive: false, visibleTrackCount: 12 });

    expect(wrapper.find('.mtl-card__filter-identity').exists()).toBe(false);
    expect(wrapper.get('.mtl-card__track-count').text()).toBe('12 Tracks');
    expect(wrapper.get('.mtl-card__count').attributes('aria-label')).toBe('Open Filter. 12 Tracks');
    expect(wrapper.classes()).not.toContain('mtl-card--identified-filter');
  });
});
