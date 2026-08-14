import { mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it } from 'vitest';
import MeasurementUnitsSetting from '@/components/admin/MeasurementUnitsSetting.vue';
import { applyServerDefaultMeasurementSystem, useMeasurementSystem } from '@/composables/useMeasurementSystem';
import { useLocale } from '@/composables/useLocale';
import { STORAGE_KEYS } from '@/utils/appStorage';

const preference = useMeasurementSystem();
const { setLocale } = useLocale();

describe('MeasurementUnitsSetting', () => {
  beforeEach(() => {
    localStorage.clear();
    setLocale('en-US');
    preference.useDefaultMeasurementSystem();
    applyServerDefaultMeasurementSystem('METRIC');
  });

  it('shows the resolved default and its preview under Presentation', () => {
    const wrapper = mount(MeasurementUnitsSetting);

    expect(wrapper.text()).toContain('Measurement units');
    const metricButton = wrapper.findAll('button').find((button) => button.text() === 'Metric');
    expect(metricButton?.attributes('aria-pressed')).toBe('true');
    expect(wrapper.get('[data-test="measurement-system-preview"]').text()).toContain('25.0 km · 500 m · 80 kg');
    expect(wrapper.get('[data-test="measurement-system-source"]').text()).toBe('Using the server default.');
    expect(wrapper.get('[data-test="measurement-system-use-default"]').attributes('disabled')).toBeDefined();
  });

  it('saves an explicit selection and removes it with Use default', async () => {
    const wrapper = mount(MeasurementUnitsSetting);

    const imperialButton = wrapper.findAll('button').find((button) => button.text() === 'Imperial (US)');
    await imperialButton?.trigger('click');

    expect(localStorage.getItem(STORAGE_KEYS.measurementSystem)).toBe('US_CUSTOMARY');
    expect(wrapper.get('[data-test="measurement-system-preview"]').text()).toContain('15.5 mi · 1,640 ft · 176 lb');
    expect(wrapper.get('[data-test="measurement-system-source"]').text()).toBe('Using your saved preference.');

    await wrapper.get('[data-test="measurement-system-use-default"]').trigger('click');

    expect(localStorage.getItem(STORAGE_KEYS.measurementSystem)).toBeNull();
    const metricButton = wrapper.findAll('button').find((button) => button.text() === 'Metric');
    expect(metricButton?.attributes('aria-pressed')).toBe('true');
    expect(wrapper.get('[data-test="measurement-system-source"]').text()).toBe('Using the server default.');
  });
});
