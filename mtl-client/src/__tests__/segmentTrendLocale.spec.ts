import { mount } from '@vue/test-utils';
import { defineComponent } from 'vue';
import { expect, it } from 'vitest';
import DiscoveryTrend from '@/components/measure/DiscoveryTrend.vue';
import { useLocale } from '@/composables/useLocale';

it('uses local tick boundaries and formats sub-day ticks distinctly even across several days', () => {
  const locale = useLocale();
  const previousLocale = locale.formatLocale.value;
  const Chart = defineComponent({ props: { options: { type: Object, required: true } }, template: '<div />' });
  const wrapper = mount(DiscoveryTrend, {
    props: {
      title: 'Speed',
      description: '',
      series: [],
      focused: '',
      axisLabel: 'km/h',
      displayValue: (value: number) => value,
    },
    global: { components: { highcharts: Chart } },
  });
  const options = wrapper.getComponent(Chart).props('options');
  const timestamp = new Date(2026, 0, 16, 15, 33, 35).getTime();
  const label = (unitName: string, value = timestamp) =>
    options.xAxis.labels.formatter.call({
      value,
      axis: { tickPositions: Object.assign([], { info: { unitName } }), min: timestamp, max: timestamp + 3 * 86400000 },
    });
  try {
    expect(options.time.timezone).toBe(Intl.DateTimeFormat().resolvedOptions().timeZone);
    locale.setLocale('en-GB');
    expect(label('second')).toBe('15:33:35');
    expect(label('day')).toBe('16/01/26');
    expect(label('hour')).not.toBe(label('hour', timestamp + 6 * 3600000));
    expect(label('hour')).toContain('15:33');
    locale.setLocale('en-US');
    expect(label('second')).toBe('03:33:35 PM');
  } finally {
    locale.setLocale(previousLocale);
    wrapper.unmount();
  }
});
