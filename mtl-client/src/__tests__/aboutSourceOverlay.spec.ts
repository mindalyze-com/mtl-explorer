import { flushPromises, shallowMount } from '@vue/test-utils';
import { defineComponent } from 'vue';
import { describe, expect, it } from 'vitest';
import AboutSourceOverlay from '@/components/info/AboutSourceOverlay.vue';

const BottomSheetStub = defineComponent({
  name: 'BottomSheet',
  props: {
    modelValue: Boolean,
    title: String,
    icon: String,
    detents: Array,
    initialDetent: String,
    sheetClass: String,
  },
  emits: ['update:modelValue'],
  template:
    '<section class="bottom-sheet-stub"><button class="close-stub" @click="$emit(\'update:modelValue\', false)">Close</button><slot /></section>',
});

function mountOverlay(visible = true) {
  return shallowMount(AboutSourceOverlay, {
    props: { visible },
    global: {
      stubs: {
        BottomSheet: BottomSheetStub,
      },
    },
  });
}

describe('AboutSourceOverlay', () => {
  it('uses the standard responsive sheet and keeps source information public', () => {
    const wrapper = mountOverlay();
    const sheet = wrapper.getComponent(BottomSheetStub);

    expect(sheet.props('modelValue')).toBe(true);
    expect(sheet.props('title')).toBe('About MTL Explorer');
    expect(sheet.props('initialDetent')).toBe('comfortable');
    expect(sheet.props('sheetClass')).toContain('sheet--about-source');
    expect(wrapper.get('#about-source-title').text()).toBe('MTL Explorer');
    expect(wrapper.get('.about-source__build').text()).toContain('AGPL-3.0-or-later');
    expect(wrapper.get('.about-source__primary').attributes('href')).toBe(
      'https://github.com/mindalyze-com/mtl-explorer'
    );
    expect(wrapper.text()).not.toContain('Source must stay available to network users.');
    expect(wrapper.get('.about-source__details-trigger').element.tagName).toBe('BUTTON');
    expect(wrapper.get('.about-source__details-trigger').attributes('href')).toBeUndefined();
  });

  it('passes sheet closure back through v-model', async () => {
    const wrapper = mountOverlay();

    await wrapper.get('.close-stub').trigger('click');

    expect(wrapper.emitted('update:visible')).toEqual([[false]]);
  });

  it('opens full details over the current page and closes the full flow without route navigation', async () => {
    const wrapper = mountOverlay();

    await wrapper.get('.about-source__details-trigger').trigger('click');
    await flushPromises();

    const aboutView = wrapper.getComponent({ name: 'AboutView' });
    expect(aboutView.attributes('embedded')).toBe('');
    expect(wrapper.emitted('update:visible')).toBeUndefined();

    aboutView.vm.$emit('closed');
    await wrapper.vm.$nextTick();

    expect(wrapper.findComponent({ name: 'AboutView' }).exists()).toBe(false);
    expect(wrapper.emitted('update:visible')).toEqual([[false]]);
  });
});
