import { mount } from '@vue/test-utils';
import type { VueWrapper } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import { nextTick } from 'vue';
import MtlSlider from '@/components/ui/MtlSlider.vue';

function mockTrackGeometry(wrapper: VueWrapper, left = 0, width = 100) {
  const track = wrapper.find('.mtl-slider__track');
  vi.spyOn(track.element, 'getBoundingClientRect').mockReturnValue({
    bottom: 10,
    height: 10,
    left,
    right: left + width,
    top: 0,
    width,
    x: left,
    y: 0,
    toJSON: () => ({}),
  } as DOMRect);
}

function pointerEvent(type: string, clientX: number): PointerEvent {
  return new MouseEvent(type, {
    bubbles: true,
    cancelable: true,
    clientX,
  }) as PointerEvent;
}

function emittedValues(wrapper: VueWrapper, eventName = 'update:modelValue') {
  return (wrapper.emitted(eventName) ?? []).map(([value]) => value);
}

async function dispatchPointer(wrapper: VueWrapper, selector: string, type: string, clientX: number) {
  wrapper.find(selector).element.dispatchEvent(pointerEvent(type, clientX));
  await nextTick();
}

describe('MtlSlider', () => {
  it('starts a single-value drag from the full rail', async () => {
    const wrapper = mount(MtlSlider, {
      props: { modelValue: 20, min: 0, max: 100 },
    });
    mockTrackGeometry(wrapper);

    await dispatchPointer(wrapper, '.mtl-slider', 'pointerdown', 75);

    expect(emittedValues(wrapper)).toEqual([75]);
    expect(emittedValues(wrapper, 'change')).toEqual([75]);
  });

  it('continues dragging after pointer movement and emits slideend shape', async () => {
    const wrapper = mount(MtlSlider, {
      props: { modelValue: 20, min: 0, max: 100 },
    });
    mockTrackGeometry(wrapper);

    await dispatchPointer(wrapper, '.mtl-slider', 'pointerdown', 10);
    window.dispatchEvent(pointerEvent('pointermove', 80));
    window.dispatchEvent(pointerEvent('pointerup', 80));

    expect(emittedValues(wrapper)).toEqual([10, 80]);
    expect(wrapper.emitted('slideend')?.[0][0]).toMatchObject({ value: 80 });
    expect((wrapper.emitted('slideend')?.[0][0] as { originalEvent: Event }).originalEvent.type).toBe('pointerup');
  });

  it('rounds to step values and clamps to the configured range', async () => {
    const wrapper = mount(MtlSlider, {
      props: { modelValue: 0, min: 0, max: 10, step: 2 },
    });
    mockTrackGeometry(wrapper);

    await dispatchPointer(wrapper, '.mtl-slider', 'pointerdown', 37);
    window.dispatchEvent(pointerEvent('pointermove', 200));

    expect(emittedValues(wrapper)).toEqual([4, 10]);
  });

  it('supports keyboard adjustment from the handle', async () => {
    const wrapper = mount(MtlSlider, {
      props: { modelValue: 5, min: 0, max: 10, step: 1, ariaLabel: 'Test slider' },
    });

    await wrapper.find('.mtl-slider__handle').trigger('keydown', { code: 'ArrowRight' });
    await wrapper.setProps({ modelValue: 6 });
    await wrapper.find('.mtl-slider__handle').trigger('keydown', { code: 'Home' });

    expect(emittedValues(wrapper)).toEqual([6, 0]);
    expect(wrapper.find('.mtl-slider__handle').attributes('role')).toBe('slider');
    expect(wrapper.find('.mtl-slider__handle').attributes('aria-label')).toBe('Test slider');
  });

  it('keeps range values ordered while dragging the nearest handle', async () => {
    const wrapper = mount(MtlSlider, {
      props: { modelValue: [20, 80], min: 0, max: 100, range: true },
    });
    mockTrackGeometry(wrapper);

    await dispatchPointer(wrapper, '.mtl-slider', 'pointerdown', 72);
    window.dispatchEvent(pointerEvent('pointermove', 10));

    expect(emittedValues(wrapper)).toEqual([
      [20, 72],
      [20, 20],
    ]);
  });

  it('exposes human-readable values for single and range sliders', async () => {
    const wrapper = mount(MtlSlider, {
      props: { modelValue: 50, min: 0, max: 100, ariaValueText: 'Balanced speed' },
    });

    expect(wrapper.find('.mtl-slider__handle').attributes('aria-valuetext')).toBe('Balanced speed');

    await wrapper.setProps({
      modelValue: [10, 90],
      range: true,
      ariaValueText: ['June 2007', 'May 2026'],
    });

    const handles = wrapper.findAll('.mtl-slider__handle');
    expect(handles[0].attributes('aria-valuetext')).toBe('June 2007');
    expect(handles[1].attributes('aria-valuetext')).toBe('May 2026');
  });

  it('does not emit updates while disabled', async () => {
    const wrapper = mount(MtlSlider, {
      props: { modelValue: 20, min: 0, max: 100, disabled: true },
    });
    mockTrackGeometry(wrapper);

    await dispatchPointer(wrapper, '.mtl-slider', 'pointerdown', 80);
    await wrapper.find('.mtl-slider__handle').trigger('keydown', { code: 'ArrowRight' });

    expect(wrapper.emitted('update:modelValue')).toBeUndefined();
    expect(wrapper.emitted('change')).toBeUndefined();
    expect(wrapper.emitted('slideend')).toBeUndefined();
  });
});
