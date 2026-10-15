import { enableAutoUnmount, mount } from '@vue/test-utils';
import { defineComponent } from 'vue';
import { afterEach, describe, expect, it } from 'vitest';
import PlannerPoiEditor from '@/planner/components/PlannerPoiEditor.vue';
import { PLANNER_POI_ICON_OPTIONS, poiIconImageId, poiIconOption } from '@/planner/utils/plannerPoiIconTypes';
import { PointOfInterestDtoIconTypeEnum } from 'x8ing-mtl-api-typescript-fetch';
import PrimeVue from 'primevue/config';

enableAutoUnmount(afterEach);

const point = { id: 'dc77b107-a507-4ead-81ae-3e61c567d73d', lat: 47, lng: 8, name: 'Lunch', notes: 'Bring food' };
function editor(iconType?: (typeof PointOfInterestDtoIconTypeEnum)[keyof typeof PointOfInterestDtoIconTypeEnum]) {
  return mount(PlannerPoiEditor, {
    props: { point: { ...point, ...(iconType ? { iconType } : {}) } },
    global: {
      plugins: [[PrimeVue, { unstyled: true }]],
      stubs: {
        Dialog: defineComponent({ props: { visible: Boolean }, template: '<div v-if="visible"><slot /></div>' }),
        Textarea: defineComponent({
          props: { modelValue: String },
          emits: ['update:modelValue'],
          template: '<textarea :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
        }),
        Select: defineComponent({
          props: { modelValue: String, options: Array },
          emits: ['update:modelValue'],
          template:
            '<select :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value)"><option v-for="option in options" :value="option.value">{{ option.label }}</option></select>',
        }),
      },
    },
  });
}

describe('Planner POI icons', () => {
  it('offers all generated API types and falls back to the pin for a missing or unknown type', () => {
    expect(PLANNER_POI_ICON_OPTIONS.map((option) => option.value)).toEqual(
      Object.values(PointOfInterestDtoIconTypeEnum)
    );
    expect(new Set(PLANNER_POI_ICON_OPTIONS.map((option) => poiIconImageId(option.value))).size).toBe(
      PLANNER_POI_ICON_OPTIONS.length
    );
    expect(poiIconOption().value).toBe('pin');
    expect(poiIconOption('future-icon').value).toBe('pin');
  });

  it('defaults to Pin and saves the chosen type while preserving identity and notes', async () => {
    const wrapper = editor();
    expect((wrapper.get('select').element as HTMLSelectElement).value).toBe('pin');
    await wrapper.get('select').setValue('parking');
    expect(wrapper.props('point')).toEqual(point);
    await wrapper.get('form').trigger('submit');
    expect(wrapper.emitted('save')).toEqual([[{ ...point, iconType: 'parking' }]]);
  });

  it('restores a saved icon, keeps cancelled changes out of the point, and resets when opening another point', async () => {
    const wrapper = editor('viewpoint');
    expect((wrapper.get('select').element as HTMLSelectElement).value).toBe('viewpoint');
    await wrapper.get('select').setValue('water');
    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Cancel')!
      .trigger('click');
    expect(wrapper.emitted('cancel')).toEqual([[]]);
    expect(wrapper.emitted('save')).toBeUndefined();
    expect(wrapper.props('point')?.iconType).toBe('viewpoint');
    await wrapper.setProps({ point: { ...point, name: 'Another stop' } });
    expect((wrapper.get('select').element as HTMLSelectElement).value).toBe('pin');
  });
});
