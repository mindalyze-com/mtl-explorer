import { enableAutoUnmount, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import HighchartsVue from 'highcharts-vue';
import PrimeVue from 'primevue/config';
import Highcharts from '@/bootstrap/highcharts';
import HealthMetricChart from '@/features/health/components/HealthMetricChart.vue';
import type { GHGroupedSeries } from '@/features/health/api/googleHealthApi';

enableAutoUnmount(afterEach);
// jsdom does not resolve rem sizes for SVG axis labels.
beforeEach(() => {
  document.documentElement.style.setProperty('--text-xs-size', '12px');
  vi.spyOn(Highcharts.SVGElement.prototype, 'getBBox').mockReturnValue({ x: 0, y: 0, width: 40, height: 14 });
});
afterEach(() => {
  document.documentElement.style.removeProperty('--text-xs-size');
  vi.restoreAllMocks();
});

describe('heart rate chart rendering', () => {
  it('renders one-second averages and ranges, breaks long gaps, and marks the selected time', async () => {
    const start = new Date('2026-10-14T12:00:10Z').getTime();
    const series: GHGroupedSeries = {
      dataType: 'heart-rate',
      title: 'Heart rate',
      family: 'MEASUREMENT',
      grouping: 'SECOND',
      detailedHeartRate: true,
      unit: 'bpm',
      decimals: 0,
      buckets: [{ startDate: '2026-10-14', endDate: '2026-10-15', availableDays: 1, selectedDays: 1, average: 70 }],
      detailedPoints: [
        ...Array.from({ length: 10 }, (_, index) => ({
          time: new Date(start + index * 1_000),
          value: 70 + index,
          minimum: index ? 70 + index : 60,
          maximum: index ? 70 + index : 80,
        })),
        { time: new Date(start + 30_000), value: 75, minimum: 75, maximum: 75 },
      ],
    };
    const wrapper = mount(HealthMetricChart, {
      props: { series },
      global: { plugins: [PrimeVue, [HighchartsVue, { highcharts: Highcharts }]] },
    });
    const chart = Highcharts.charts.find((item) => item?.renderTo === wrapper.get('[data-highcharts-chart]').element)!;
    expect(chart.series.map((item) => item.type)).toEqual(['errorbar', 'line', 'scatter']);
    const average = chart.series[1]!;
    expect(average.points.map((point) => point.y)).toEqual([70, 71, 72, 73, 74, 75, 76, 77, 78, 79, null, 75]);
    expect(average.options.marker?.enabled).toBe(true);
    expect(chart.series[0]!.points[0]).toMatchObject({ low: 60, high: 80 });
    expect(chart.series[2]!.points.map((point) => point.y)).toEqual([75]);
    average.points[0]!.firePointEvent('click');
    expect(wrapper.emitted('update:focusTime')?.at(-1)).toEqual([start]);
    await wrapper.setProps({ focusTime: start + 400 });
    expect(wrapper.get('.health-second-reading').text()).toContain('70');
    expect(wrapper.get('.health-second-reading').text()).toContain('60');
    expect(wrapper.findAll('.highcharts-plot-line')).toHaveLength(1);
    await wrapper.get('button[aria-label="Clear marked time"]').trigger('click');
    expect(wrapper.emitted('update:focusTime')?.at(-1)).toEqual([undefined]);
    await wrapper.setProps({ focusTime: undefined });
    expect(wrapper.findAll('.highcharts-plot-line')).toHaveLength(0);
    const tooltip = String(chart.options.tooltip!.formatter!.call(average.points[0]!, chart.tooltip));
    expect(tooltip).toContain(':10');
    expect(tooltip).toContain('Average');
    expect(tooltip).toContain('Range');
    expect(chart.xAxis[0]!.options.minRange).toBe(1_000);
  });

  it('follows the shared time window and reports graph zoom without zooming alone', async () => {
    const start = new Date('2026-10-13T12:00:00').getTime();
    const end = new Date('2026-10-13T12:15:00').getTime();
    const wrapper = mount(HealthMetricChart, {
      props: {
        timeWindow: { start, end },
        series: {
          title: 'Heart rate',
          family: 'MEASUREMENT',
          grouping: 'SECOND',
          buckets: [{ startDate: '2026-10-13', endDate: '2026-10-14', availableDays: 1 }],
          detailedPoints: [
            { time: new Date('2026-10-13T00:00:00'), value: 70 },
            { time: new Date('2026-10-13T12:05:00'), value: 75 },
          ],
        },
      },
      global: { plugins: [PrimeVue, [HighchartsVue, { highcharts: Highcharts }]] },
    });
    const chart = Highcharts.charts.find((item) => item?.renderTo === wrapper.get('[data-highcharts-chart]').element)!;
    expect(chart.xAxis[0]!.min).toBe(start);
    expect(chart.xAxis[0]!.max).toBe(end);
    expect(wrapper.find('.health-chart-window-empty').exists()).toBe(false);
    Highcharts.fireEvent(chart, 'selection', {
      xAxis: [{ axis: chart.xAxis[0], min: start + 12_300, max: start + 71_600 }],
    });
    expect(wrapper.emitted('update:timeWindow')?.at(-1)).toEqual([{ start: start + 12_000, end: start + 72_000 }]);
    expect(chart.xAxis[0]!.min).toBe(start);
    Highcharts.fireEvent(chart, 'selection', { resetSelection: true });
    expect(wrapper.emitted('update:timeWindow')?.at(-1)).toEqual([undefined]);
    await wrapper.setProps({ timeWindow: { start: start + 3_600_000, end: end + 3_600_000 } });
    expect(chart.xAxis[0]!.min).toBe(start + 3_600_000);
    expect(chart.xAxis[0]!.max).toBe(end + 3_600_000);
    expect(wrapper.get('.health-chart-window-empty').text()).toBe('No readings in this time window');
  });

  it('connects evenly spaced sparse readings and marks only isolated ones', () => {
    const start = new Date('2026-10-14T12:00:00Z').getTime();
    const wrapper = mount(HealthMetricChart, {
      props: {
        series: {
          dataType: 'heart-rate',
          title: 'Heart rate',
          family: 'MEASUREMENT',
          grouping: 'SECOND',
          detailedHeartRate: true,
          unit: 'bpm',
          decimals: 0,
          buckets: [{ startDate: '2026-10-14', endDate: '2026-10-15', availableDays: 1 }],
          detailedPoints: [
            ...Array.from({ length: 45 }, (_, index) => ({
              time: new Date(start + index * 60_000),
              value: 70 + (index % 3),
            })),
            { time: new Date(start + 3 * 3_600_000), value: 90 },
          ],
        },
      },
      global: { plugins: [PrimeVue, [HighchartsVue, { highcharts: Highcharts }]] },
    });
    const chart = Highcharts.charts.find((item) => item?.renderTo === wrapper.get('[data-highcharts-chart]').element)!;
    const line = chart.series.find((series) => series.type === 'line')!;
    expect(line.points.filter((point) => point.y == null)).toHaveLength(1);
    expect(line.points.filter((point) => point.y != null)).toHaveLength(46);
    const isolated = chart.series.find((series) => series.type === 'scatter')!;
    expect(isolated.points.map((point) => point.y)).toEqual([90]);
  });

  it('renders the range and average with the application chart modules and updates the period', async () => {
    const series: GHGroupedSeries = {
      dataType: 'heart-rate',
      title: 'Heart rate',
      family: 'MEASUREMENT',
      grouping: 'DAY',
      detailedHeartRate: true,
      unit: 'bpm',
      decimals: 0,
      detailedPoints: [
        { time: new Date('2026-08-01T12:00:00Z'), value: 70, minimum: 50, maximum: 100 },
        { time: new Date('2026-08-02T12:00:00Z'), value: 75, minimum: 55, maximum: 110 },
      ],
    };
    const wrapper = mount(HealthMetricChart, {
      props: { series },
      global: { plugins: [PrimeVue, [HighchartsVue, { highcharts: Highcharts }]] },
    });
    const container = wrapper.get('[data-highcharts-chart]').element;
    const chart = Highcharts.charts.find((item) => item?.renderTo === container)!;

    expect(chart.series.map((item) => item.type)).toEqual(['arearange', 'spline']);
    expect(chart.series[0]!.points.map((point) => [point.low, point.high])).toEqual([
      [50, 100],
      [55, 110],
    ]);
    expect(chart.series[1]!.points.map((point) => point.y)).toEqual([70, 75]);
    expect(wrapper.find('.highcharts-arearange-series').exists()).toBe(true);
    expect(wrapper.find('.highcharts-spline-series').exists()).toBe(true);
    expect(wrapper.find('[role="region"][aria-label]').exists()).toBe(true);

    await wrapper.setProps({ series: { ...series, detailedPoints: series.detailedPoints!.slice(1) } });

    expect(chart.series.every((item) => item.points.length === 1)).toBe(true);
    expect(chart.series[0]!.points[0]).toMatchObject({ low: 55, high: 110 });
    expect(chart.series[1]!.points[0]!.y).toBe(75);
  });
  it.each(['SUMMARY', 'RANGE'] as const)('shows a gap for an empty calendar date in %s mode', (mode) => {
    const wrapper = mount(HealthMetricChart, {
      props: {
        mode,
        series: {
          dataType: 'heart-rate',
          title: 'Heart rate',
          family: 'MEASUREMENT',
          grouping: 'DAY',
          detailedHeartRate: true,
          buckets: [{ startDate: '2026-08-02', endDate: '2026-08-03', availableDays: 0 }],
          detailedPoints: [
            { time: new Date('2026-08-01T12:00:00'), value: 70, minimum: 60, maximum: 80 },
            { time: new Date('2026-08-03T12:00:00'), value: 75, minimum: 65, maximum: 85 },
          ],
        },
      },
      global: { plugins: [PrimeVue, [HighchartsVue, { highcharts: Highcharts }]] },
    });
    const chart = Highcharts.charts.find((item) => item?.renderTo === wrapper.get('[data-highcharts-chart]').element)!;
    expect(chart.series.find((series) => series.type === 'spline')!.points.map((point) => point.y)).toEqual([
      70,
      null,
      75,
    ]);
    expect(chart.series.map((series) => series.type)).toEqual(
      mode === 'SUMMARY' ? ['spline'] : ['arearange', 'spline']
    );
  });
});
