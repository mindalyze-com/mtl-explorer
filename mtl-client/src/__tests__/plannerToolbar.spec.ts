import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import PlannerToolbar from '@/planner/components/PlannerToolbar.vue';

describe('Planner routing profiles', () => {
  it('selects the pedestrian profile for Hiking and identifies trekking as cycling', async () => {
    const wrapper = mount(PlannerToolbar, {
      props: { profile: 'fastbike', profiles: ['trekking', 'fastbike', 'hiking-mountain', 'car-eco'] },
      global: {
        stubs: {
          Select: {
            props: ['modelValue', 'options'],
            emits: ['update:modelValue'],
            template:
              '<select :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value)"><option v-for="option in options" :value="option.value">{{ option.label }}</option></select>',
          },
        },
      },
    });
    const hiking = wrapper.findAll('option').find((option) => option.text() === 'Hiking')!;
    expect(hiking.attributes('value')).toBe('hiking-mountain');
    expect(wrapper.get('option[value="trekking"]').text()).toBe('Touring bike');
    await wrapper.get('select').setValue(hiking.attributes('value'));
    expect(wrapper.emitted('profile-changed')).toEqual([['hiking-mountain']]);
  });
});
