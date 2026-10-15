import { afterEach, describe, expect, it, vi } from 'vitest';
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils';
import PrimeVue from 'primevue/config';
import HealthDateRangeControl from '@/features/health/components/HealthDateRangeControl.vue';
import { healthCalendarRange } from '@/features/health/utils/healthCalendarRange';

enableAutoUnmount(afterEach);
afterEach(() => vi.unstubAllGlobals());

function mountRange(mobile = false) {
  vi.stubGlobal('matchMedia', () => ({ matches: mobile, addEventListener() {}, removeEventListener() {} }));
  return mount(HealthDateRangeControl, {
    attachTo: document.body,
    props: { startDate: '2026-05-01', endDate: '2026-05-31', preset: 'CUSTOM', label: '1 May 2026 – 31 May 2026' },
    global: { plugins: [PrimeVue], stubs: { teleport: true } },
  });
}

async function customRange(mobile = false) {
  const wrapper = mountRange(mobile);
  await wrapper.get('.health-date-trigger').trigger('click');
  await wrapper.get('.health-date-custom-link').trigger('click');
  return wrapper;
}

describe('Health date range flow', () => {
  it.each([false, true])('keeps custom edits as a draft until Apply (mobile: %s)', async (mobile) => {
    const wrapper = await customRange(mobile);
    expect(wrapper.findAll('.p-datepicker-calendar')).toHaveLength(mobile ? 1 : 2);
    await wrapper.get('#health-custom-start').setValue('2021-01-01');
    await wrapper.get('#health-custom-end').setValue('2021-01-02');
    expect(wrapper.emitted('custom')).toBeUndefined();
    await wrapper.get('form').trigger('submit');
    expect(wrapper.emitted('custom')).toEqual([[{ start: '2021-01-01', end: '2021-01-02' }]]);
    expect(wrapper.get('.health-date-trigger').attributes('aria-expanded')).toBe('false');
    expect(document.activeElement).toBe(wrapper.get('.health-date-trigger').element);
  });

  it('discards cancelled edits and reopens with the applied period', async () => {
    const wrapper = await customRange();
    await wrapper.get('#health-custom-start').setValue('2021-01-01');
    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Cancel')!
      .trigger('click');
    await flushPromises();
    expect(wrapper.emitted('custom')).toBeUndefined();
    await wrapper.get('.health-date-trigger').trigger('click');
    await wrapper.get('.health-date-custom-link').trigger('click');
    expect(wrapper.get<HTMLInputElement>('#health-custom-start').element.value).toBe('2026-05-01');
  });

  it('validates reversed and future dates and lets a shortcut replace an unfinished draft', async () => {
    const wrapper = await customRange();
    await wrapper.get('#health-custom-start').setValue('2026-06-01');
    expect(wrapper.get('.health-date-error').text()).toContain('on or before');
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined();
    await wrapper.get('#health-custom-end').setValue('2099-01-01');
    expect(wrapper.get('.health-date-error').text()).toContain('future');
    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Back to shortcuts')!
      .trigger('click');
    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Last 90 days')!
      .trigger('click');
    expect(wrapper.emitted('preset')).toEqual([['90D']]);
    expect(wrapper.emitted('custom')).toBeUndefined();
  });

  it('closes only the date overlay on Escape and restores trigger focus', async () => {
    const wrapper = await customRange(true);
    const bubble = vi.fn();
    document.addEventListener('keydown', bubble);
    await wrapper.get('#health-custom-start').trigger('keydown', { key: 'Escape' });
    expect(wrapper.get('.health-date-trigger').attributes('aria-expanded')).toBe('false');
    expect(bubble).not.toHaveBeenCalled();
    document.removeEventListener('keydown', bubble);
  });

  it('uses calendar boundaries for shortcuts, including leap years and previous January', () => {
    expect(healthCalendarRange('90D', new Date(2026, 8, 4))).toEqual({
      startDate: '2026-06-07',
      endDate: '2026-09-05',
    });
    expect(healthCalendarRange('THIS_MONTH', new Date(2024, 1, 29))).toEqual({
      startDate: '2024-02-01',
      endDate: '2024-03-01',
    });
    expect(healthCalendarRange('PREVIOUS_MONTH', new Date(2024, 2, 31))).toEqual({
      startDate: '2024-02-01',
      endDate: '2024-03-01',
    });
    expect(healthCalendarRange('PREVIOUS_MONTH', new Date(2026, 0, 1))).toEqual({
      startDate: '2025-12-01',
      endDate: '2026-01-01',
    });
    expect(healthCalendarRange('THIS_YEAR', new Date(2026, 8, 4))).toEqual({
      startDate: '2026-01-01',
      endDate: '2026-09-05',
    });
  });
});
