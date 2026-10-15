import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import SourceUsageSelect from '@/features/track-review/SourceUsageSelect.vue';

const checked = (wrapper: ReturnType<typeof mount>) => wrapper.find<HTMLInputElement>('input:checked');

describe('source map and statistics use', () => {
  it.each(['INCLUDE', 'EXCLUDE_MAP', 'EXCLUDE_STATISTICS', 'EXCLUDE'])(
    'saves %s only after submitting',
    async (decision) => {
      const wrapper = mount(SourceUsageSelect, { props: { state: 'NEEDS_REVIEW' } });
      await wrapper.get(`input[value="${decision}"]`).setValue();
      expect(wrapper.emitted('save')).toBeUndefined();
      await wrapper.get('form').trigger('submit');
      expect(wrapper.emitted('save')).toEqual([[decision]]);
    }
  );
  it('shows the persisted choice and supports reversing it', async () => {
    const wrapper = mount(SourceUsageSelect, { props: { state: 'EXCLUDED_MAP' } });
    expect(checked(wrapper).element.value).toBe('EXCLUDE_MAP');
    expect(wrapper.get('.source-usage__option--selected').text()).toBe('Statistics only');
    await wrapper.get('input[value="INCLUDE"]').setValue();
    await wrapper.get('form').trigger('submit');
    expect(wrapper.emitted('save')).toEqual([['INCLUDE']]);
    await wrapper.setProps({ state: 'EXCLUDED_STATISTICS' });
    expect(checked(wrapper).element.value).toBe('EXCLUDE_STATISTICS');
  });
  it('does not imply inclusion for mixed sections or allow inclusion without observations', () => {
    const wrapper = mount(SourceUsageSelect, { props: { state: 'MIXED', canInclude: false } });
    expect(checked(wrapper).exists()).toBe(false);
    expect(wrapper.get('input[value="INCLUDE"]').attributes('disabled')).toBeDefined();
    expect(wrapper.get('button').attributes('disabled')).toBeDefined();
  });
});

it('disables an unavailable saved inclusion until the user chooses a valid setting', async () => {
  const view = mount(SourceUsageSelect, { props: { decision: 'INCLUDE', canInclude: false } });
  expect(view.get('button').attributes('disabled')).toBeDefined();
  await view.get('form').trigger('submit');
  expect(view.emitted('save')).toBeUndefined();
  await view.get('input[value="EXCLUDE"]').setValue();
  expect(view.get('button').attributes('disabled')).toBeUndefined();
  await view.get('form').trigger('submit');
  expect(view.emitted('save')).toEqual([['EXCLUDE']]);
  view.unmount();
});
