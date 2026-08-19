import { mount } from '@vue/test-utils';
import { defineComponent } from 'vue';
import { describe, expect, it } from 'vitest';
import LocationSearchSheet from '@/components/map/LocationSearchSheet.vue';

const BottomSheetStub = defineComponent({
  name: 'BottomSheet',
  template: '<section><slot /></section>',
});

describe('LocationSearchSheet', () => {
  it('shows a prompt when the query is empty, including after Clear search', async () => {
    const wrapper = mount(LocationSearchSheet, {
      props: { modelValue: false, mapCenter: null },
      global: { stubs: { BottomSheet: BottomSheetStub } },
    });

    expect(wrapper.get('.location-search__state').text()).toContain('Search for a city, peak, or area');

    await wrapper.get('input[type="search"]').setValue('Bern');
    await wrapper.get('[aria-label="Clear search"]').trigger('click');

    expect(wrapper.get('input[type="search"]').element).toHaveProperty('value', '');
    expect(wrapper.get('.location-search__state').text()).toContain('Search for a city, peak, or area');
  });
});
