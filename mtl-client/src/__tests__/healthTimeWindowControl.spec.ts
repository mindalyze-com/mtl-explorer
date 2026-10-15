import { enableAutoUnmount, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it } from 'vitest';
import PrimeVue from 'primevue/config';
import HealthTimeWindowControl from '@/features/health/components/HealthTimeWindowControl.vue';

enableAutoUnmount(afterEach);

const DAY = '2026-10-14';
const at = (time: string) => new Date(`${DAY}T${time}`).getTime();

function mountControl(props: Partial<InstanceType<typeof HealthTimeWindowControl>['$props']> = {}) {
  return mount(HealthTimeWindowControl, { props: { day: DAY, ...props }, global: { plugins: [PrimeVue] } });
}
function preset(wrapper: ReturnType<typeof mountControl>, label: string) {
  return wrapper.findAll('[aria-label="Time window"] button').find((button) => button.text() === label)!;
}

describe('HealthTimeWindowControl', () => {
  it('shows the full day and centers presets on the marked time', async () => {
    const wrapper = mountControl({ focusTime: at('09:12:04') });
    expect(wrapper.get('button[aria-label="Set exact time range"]').text()).toContain('00:00:00 – 24:00:00');
    expect(preset(wrapper, 'Full day').attributes('aria-pressed')).toBe('true');
    expect(wrapper.get('button[aria-label="Earlier"]').attributes('disabled')).toBeDefined();
    await preset(wrapper, '15 min').trigger('click');
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([{ start: at('09:04:34'), end: at('09:19:34') }]);
  });

  it('keeps presets within the day and pans by the window width', async () => {
    const wrapper = mountControl({ modelValue: { start: at('23:30:00'), end: at('23:45:00') } });
    expect(preset(wrapper, '15 min').attributes('aria-pressed')).toBe('true');
    await preset(wrapper, '1 h').trigger('click');
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([
      { start: at('23:00:00'), end: new Date(`2026-10-15T00:00:00`).getTime() },
    ]);
    await wrapper.get('button[aria-label="Earlier"]').trigger('click');
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([{ start: at('23:15:00'), end: at('23:30:00') }]);
    await wrapper.get('button[aria-label="Later"]').trigger('click');
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([
      { start: at('23:45:00'), end: new Date(`2026-10-15T00:00:00`).getTime() },
    ]);
    await preset(wrapper, 'Full day').trigger('click');
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([undefined]);
  });

  it('applies an exact time range and rejects an inverted one', async () => {
    const wrapper = mountControl();
    await wrapper.get('button[aria-label="Set exact time range"]').trigger('click');
    await wrapper.get('input[aria-label="From time"]').setValue('12:00');
    await wrapper.get('input[aria-label="To time"]').setValue('11:00');
    expect(wrapper.get('[role="alert"]').text()).toBe('To must be after From.');
    expect(wrapper.get('form button[type="submit"]').attributes('disabled')).toBeDefined();
    await wrapper.get('input[aria-label="To time"]').setValue('12:15');
    await wrapper.get('form').trigger('submit');
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([{ start: at('12:00:00'), end: at('12:15:00') }]);
    expect(wrapper.find('form').exists()).toBe(false);
  });
});
