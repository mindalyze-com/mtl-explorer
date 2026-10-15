import { afterEach, describe, expect, it } from 'vitest';
import { enableAutoUnmount, mount } from '@vue/test-utils';
import HighchartsVue from 'highcharts-vue';
import Highcharts from '@/bootstrap/highcharts';
import HealthMetricChart from '@/features/health/components/HealthMetricChart.vue';
import type { GHGroupedSeries } from '@/features/health/api/googleHealthApi';
import { healthCalendarRange } from '@/features/health/utils/healthCalendarRange';
import {
  readHealthGrouping,
  readHealthModes,
  saveHealthGrouping,
  saveHealthModes,
} from '@/features/health/utils/healthChartPreferences';

enableAutoUnmount(afterEach);
const fixture: GHGroupedSeries = {
  dataType: 'steps',
  title: 'Steps',
  unit: 'steps',
  family: 'ACTIVITY',
  category: 'ACTIVITY',
  decimals: 0,
  grouping: 'WEEK',
  buckets: [
    {
      startDate: '2026-08-24',
      endDate: '2026-08-31',
      total: 42000,
      average: 7000,
      minimum: 0,
      maximum: 14000,
      minimumDate: '2026-08-24',
      maximumDate: '2026-08-28',
      availableDays: 6,
      selectedDays: 7,
    },
    {
      startDate: '2026-08-31',
      endDate: '2026-09-05',
      total: 20000,
      average: 5000,
      minimum: 2000,
      maximum: 9000,
      minimumDate: '2026-09-04',
      maximumDate: '2026-09-01',
      availableDays: 4,
      selectedDays: 5,
      inProgress: true,
      clipped: true,
    },
    { startDate: '2026-09-05', endDate: '2026-09-07', availableDays: 0, selectedDays: 2 },
  ],
};
function chartFor(series: GHGroupedSeries = fixture) {
  const wrapper = mount(HealthMetricChart, {
    attachTo: document.body,
    props: { series, expanded: true },
    global: { plugins: [[HighchartsVue, { highcharts: Highcharts }]] },
  });
  const container = wrapper.get('[data-highcharts-chart]').element;
  const chart = Highcharts.charts.find((item) => item?.renderTo === container)!;
  return { wrapper, chart };
}

describe('grouped Health chart modes', () => {
  it('keeps compact cards clear and renders the complete period tooltip with zero extrema', async () => {
    const { wrapper, chart } = chartFor();
    await wrapper.setProps({ expanded: false });
    expect(wrapper.get('.health-chart-summary').text()).toContain('20,000');
    expect(wrapper.get('.health-bucket-details').isVisible()).toBe(false);
    expect(wrapper.find('.health-chart-modes').exists()).toBe(false);
    const tooltipElement = document.createElement('div');
    const markup = chart.options.tooltip!.formatter!.call(chart.series[0]!.points[0]!, chart.tooltip);
    Highcharts.AST.setElementHTML(tooltipElement, String(markup));
    expect(tooltipElement.querySelector('.health-chart-tooltip__heading')?.textContent).toContain('6 / 7 days');
    const tooltip = tooltipElement.textContent;
    expect(tooltip).toContain('Daily average');
    expect(tooltip).toContain('7,000');
    expect(tooltip).toContain('0\u202fsteps');
    await wrapper.get('.health-chart-period').trigger('click');
    expect(wrapper.get('.health-bucket-details').isVisible()).toBe(true);
    await wrapper.get('.health-bucket-details').trigger('keydown', { key: 'Escape' });
    expect(wrapper.get('.health-bucket-details').isVisible()).toBe(false);
  });

  it.each(['DAY', 'WEEK', 'MONTH', 'YEAR'] as const)(
    'renders both modes for all metric families at %s grouping',
    async (grouping) => {
      for (const family of ['ACTIVITY', 'SLEEP', 'MEASUREMENT'] as const) {
        const { wrapper, chart } = chartFor({
          ...fixture,
          dataType: family === 'SLEEP' ? 'sleep' : 'test',
          family,
          grouping,
          buckets: fixture.buckets!.map((bucket) => ({
            ...bucket,
            sessions:
              family === 'SLEEP' && grouping === 'DAY' && bucket.total != null
                ? [{ time: new Date(`${bucket.startDate}T01:00:00`), value: 7 }]
                : [],
          })),
        });
        const before = wrapper.get('.health-bucket-details').text();
        await wrapper.setProps({ mode: 'RANGE' });
        const dailyCumulative = grouping === 'DAY' && family !== 'MEASUREMENT';
        expect(chart.series.some((series) => series.type === 'errorbar')).toBe(!dailyCumulative);
        expect(chart.yAxis[0]!.axisTitle?.textStr).toContain(
          family === 'ACTIVITY' && dailyCumulative
            ? 'Total'
            : family === 'SLEEP' && dailyCumulative
              ? 'Daily total'
              : family === 'MEASUREMENT'
                ? 'Average'
                : 'Daily average'
        );
        expect(wrapper.get('.health-bucket-details').text()).toBe(before);
        await wrapper.setProps({ mode: 'SUMMARY' });
        expect(chart.series.some((series) => series.type === 'errorbar')).toBe(false);
        wrapper.unmount();
      }
    }
  );

  it('switches totals to daily statistics without mixing scales or losing missing buckets and selection', async () => {
    const { wrapper, chart } = chartFor();
    expect(chart.series[0]!.points.map((point) => point.y)).toEqual([42000, 20000, null]);
    expect(wrapper.get('select').element.value).toBe('1');
    expect(wrapper.text()).toContain('In progress');
    expect(wrapper.text()).toContain('Clipped period');
    const before = wrapper.get('.health-bucket-details').text();
    await wrapper.setProps({ mode: 'RANGE' });
    expect(chart.series.map((series) => series.type)).toEqual(expect.arrayContaining(['errorbar', 'scatter']));
    expect(chart.series.find((series) => series.type === 'scatter')!.points.map((point) => point.y)).toEqual([
      7000,
      5000,
      null,
    ]);
    expect(chart.series.find((series) => series.type === 'errorbar')!.points[0]).toMatchObject({ low: 0, high: 14000 });
    expect(wrapper.get('.health-bucket-details').text()).toBe(before);
    await wrapper.setProps({ mode: 'SUMMARY' });
    expect(chart.series.map((series) => series.type)).toEqual(['column']);
    expect(chart.series[0]!.points[0]!.y).toBe(42000);
  });

  it('previews on hover and pins the clicked or keyboard-selected bucket', async () => {
    const { wrapper, chart } = chartFor();
    chart.series[0]!.points[0]!.firePointEvent('mouseOver');
    await wrapper.vm.$nextTick();
    expect(wrapper.text()).toContain('Preview · select to pin');
    expect(wrapper.get('select').element.value).toBe('1');
    chart.series[0]!.points[0]!.firePointEvent('click');
    expect(wrapper.emitted('update:selectedBucket')?.at(-1)).toEqual(['2026-08-24']);
    await wrapper.setProps({ selectedBucket: '2026-08-24' });
    expect(wrapper.get('select').element.value).toBe('0');
    await wrapper.get('select').setValue('2');
    expect(wrapper.emitted('update:selectedBucket')?.at(-1)).toEqual(['2026-09-05']);
  });

  it('keeps the range preference while daily variation is disabled, then restores it', async () => {
    const { wrapper, chart } = chartFor();
    await wrapper.setProps({ mode: 'RANGE', series: { ...fixture, grouping: 'DAY' } });
    expect(wrapper.get('button[title="Choose week, month or year"]').attributes('disabled')).toBeDefined();
    expect(chart.series.map((series) => series.type)).toEqual(['column']);
    await wrapper.setProps({ series: fixture });
    expect(chart.series.map((series) => series.type)).toEqual(expect.arrayContaining(['errorbar', 'scatter']));
  });

  it('labels measurement extrema and never fabricates a range for a single observation', async () => {
    const { wrapper, chart } = chartFor({
      ...fixture,
      family: 'MEASUREMENT',
      buckets: [{ ...fixture.buckets![0], minimum: 70, maximum: 70, average: 70, availableDays: 1 }],
    });
    await wrapper.setProps({ mode: 'RANGE' });
    expect(wrapper.text()).toContain('Lowest measurement');
    expect(wrapper.text()).toContain('Highest measurement');
    expect(chart.series.find((series) => series.type === 'errorbar')!.points[0]!.isNull).toBe(true);
    expect(chart.series.find((series) => series.type === 'line')!.points[0]!.y).toBe(70);
  });
});

describe('Health calendar navigation and preferences', () => {
  it('includes exactly seven or thirty local dates including today', () => {
    const now = new Date(2026, 8, 4, 13);
    expect(healthCalendarRange('7D', now)).toEqual({ startDate: '2026-08-29', endDate: '2026-09-05' });
    expect(healthCalendarRange('30D', now)).toEqual({ startDate: '2026-08-06', endDate: '2026-09-05' });
  });
  it('subtracts calendar months and years with end-of-month clamping', () => {
    expect(healthCalendarRange('6M', new Date(2024, 7, 30))).toEqual({
      startDate: '2024-02-29',
      endDate: '2024-08-31',
    });
    expect(healthCalendarRange('1Y', new Date(2024, 1, 28))).toEqual({
      startDate: '2023-02-28',
      endDate: '2024-02-29',
    });
  });
  it('validates and reloads independent local preferences', () => {
    localStorage.clear();
    expect(readHealthGrouping()).toBe('AUTO');
    saveHealthGrouping('YEAR');
    saveHealthModes({ steps: 'RANGE', weight: 'SUMMARY' });
    expect(readHealthGrouping()).toBe('YEAR');
    expect(readHealthModes()).toEqual({ steps: 'RANGE', weight: 'SUMMARY' });
    localStorage.setItem('mtl.health.chartModes', '{invalid');
    localStorage.setItem('mtl.health.grouping', 'HOUR');
    expect(readHealthModes()).toEqual({});
    expect(readHealthGrouping()).toBe('AUTO');
    localStorage.clear();
  });
});
