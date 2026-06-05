import { mount, type VueWrapper } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import DateTimeParam from '@/components/filter/DateTimeParam.vue';

function mountDateTimeParam(modelValue: Date | null = null) {
  return mount(DateTimeParam, {
    props: {
      id: 'DATE_TIME_FROM',
      label: 'From',
      modelValue,
    },
  });
}

function lastEmittedValue(wrapper: VueWrapper): Date | null {
  const emitted = wrapper.emitted('update:modelValue') ?? [];
  return emitted[emitted.length - 1]?.[0] as Date | null;
}

describe('DateTimeParam', () => {
  it('renders the current date and minute without seconds', () => {
    const wrapper = mountDateTimeParam(new Date(2026, 4, 27, 8, 45, 33));

    expect((wrapper.find('input[type="date"]').element as HTMLInputElement).value).toBe('2026-05-27');
    expect((wrapper.find('input[type="time"]').element as HTMLInputElement).value).toBe('08:45');
  });

  it('emits a date immediately when the date field changes', async () => {
    const wrapper = mountDateTimeParam();
    const dateInput = wrapper.find('input[type="date"]');

    (dateInput.element as HTMLInputElement).value = '2026-05-27';
    await dateInput.trigger('change');

    const emitted = lastEmittedValue(wrapper);
    expect(emitted).toBeInstanceOf(Date);
    expect(emitted?.getFullYear()).toBe(2026);
    expect(emitted?.getMonth()).toBe(4);
    expect(emitted?.getDate()).toBe(27);
    expect(emitted?.getHours()).toBe(0);
    expect(emitted?.getMinutes()).toBe(0);
  });

  it('preserves the selected time when the date changes', async () => {
    const wrapper = mountDateTimeParam(new Date(2026, 0, 1, 13, 20));
    const dateInput = wrapper.find('input[type="date"]');

    (dateInput.element as HTMLInputElement).value = '2026-06-03';
    await dateInput.trigger('change');

    const emitted = lastEmittedValue(wrapper);
    expect(emitted?.getFullYear()).toBe(2026);
    expect(emitted?.getMonth()).toBe(5);
    expect(emitted?.getDate()).toBe(3);
    expect(emitted?.getHours()).toBe(13);
    expect(emitted?.getMinutes()).toBe(20);
  });

  it('emits an updated time for the selected date', async () => {
    const wrapper = mountDateTimeParam(new Date(2026, 4, 27, 8, 45));
    const timeInput = wrapper.find('input[type="time"]');

    (timeInput.element as HTMLInputElement).value = '21:05';
    await timeInput.trigger('change');

    const emitted = lastEmittedValue(wrapper);
    expect(emitted?.getFullYear()).toBe(2026);
    expect(emitted?.getMonth()).toBe(4);
    expect(emitted?.getDate()).toBe(27);
    expect(emitted?.getHours()).toBe(21);
    expect(emitted?.getMinutes()).toBe(5);
  });

  it('emits null when cleared', async () => {
    const wrapper = mountDateTimeParam(new Date(2026, 4, 27, 8, 45));

    await wrapper.find('button').trigger('click');

    expect(lastEmittedValue(wrapper)).toBeNull();
  });
});
