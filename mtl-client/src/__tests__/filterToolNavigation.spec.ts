import { beforeEach, describe, expect, it, vi } from 'vitest';
import { shallowMount } from '@vue/test-utils';
import Filter from '@/components/filter/Filter.vue';
import CustomFilter from '@/components/filter/CustomFilter.vue';

const store = vi.hoisted(() => ({
  isActive: true,
  ensureLoaded: vi.fn().mockResolvedValue(undefined),
  refresh: vi.fn().mockResolvedValue(undefined),
}));

vi.mock('@/stores/filterStore', () => ({ useFilterStore: () => store }));

describe('filter tool navigation', () => {
  beforeEach(() => vi.clearAllMocks());

  it('leaves the filter route when the sheet closes while filtering remains enabled', () => {
    const wrapper = shallowMount(Filter);
    const sheet = wrapper.findComponent(CustomFilter);
    sheet.vm.$emit('update:show', false);
    sheet.vm.$emit('closed');
    expect(wrapper.emitted('tool-closed')).toHaveLength(1);
    expect(store.isActive).toBe(true);
    wrapper.unmount();
  });

  it('ignores a late closing transition after the sheet has reopened', () => {
    const wrapper = shallowMount(Filter);
    const sheet = wrapper.findComponent(CustomFilter);
    sheet.vm.$emit('update:show', true);
    sheet.vm.$emit('closed');
    expect(wrapper.emitted('tool-closed')).toBeUndefined();
    wrapper.unmount();
  });
});
