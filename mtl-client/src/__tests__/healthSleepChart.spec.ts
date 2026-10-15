import { groupedSeries } from './fixtures/groupedHealth';
import { enableAutoUnmount, mount } from '@vue/test-utils';
import { defineComponent } from 'vue';
import { afterEach, describe, expect, it } from 'vitest';
import type Highcharts from 'highcharts';
import HealthMetricChart from '@/features/health/components/HealthMetricChart.vue';
import HealthMetricSummaryCard from '@/features/health/components/HealthMetricSummaryCard.vue';
import type { GHMetricPoint, GHMetricSeries } from '@/features/health/api/googleHealthApi';
import { sleepDays, SLEEP_SESSION_COLORS } from '@/features/health/utils/sleepChart';

enableAutoUnmount(afterEach);

const HighchartsStub = defineComponent({ props: { options: { type: Object, required: true } }, template: '<div />' });
const localTime = (day: number, hour = 0) => new Date(2026, 8, day, hour);
const session = (day: number, hour: number, value: number): GHMetricPoint => ({ time: localTime(day, hour), value });
const sleepSeries = (points: GHMetricPoint[]): GHMetricSeries => ({
  dataType: 'sleep',
  title: 'Sleep',
  category: 'SLEEP',
  chartType: 'COLUMN',
  unit: 'h',
  decimals: 1,
  points,
});

function mountChart(series: GHMetricSeries) {
  const wrapper = mount(HealthMetricChart, {
    props: { series: groupedSeries(series) },
    global: { components: { highcharts: HighchartsStub } },
  });
  const options = () => wrapper.getComponent(HighchartsStub).props('options') as Highcharts.Options;
  return { wrapper, options };
}

describe('daily sleep presentation', () => {
  it('groups local calendar days, sorts longest first, and does not mutate the input', () => {
    const points = [session(2, 14, 0.5), session(1, 23, 6), session(2, 1, 7), session(2, 12, 1)];
    const original = points.map((point) => ({ ...point }));
    const days = sleepDays(points);

    expect(days.map((day) => day.time)).toEqual([localTime(1), localTime(2)]);
    expect(days.map((day) => day.total)).toEqual([6, 8.5]);
    expect(days[1]!.sessions.map((point) => point.value)).toEqual([7, 1, 0.5]);
    expect(points).toEqual(original);
  });

  it('keeps equal-duration sessions separate and ordered by time', () => {
    const days = sleepDays([session(2, 16, 1), session(2, 12, 1), session(2, 1, 7)]);
    expect(days[0]!.sessions.map((point) => point.time)).toEqual([localTime(2, 1), localTime(2, 12), localTime(2, 16)]);
    expect(days[0]!.total).toBe(9);
  });

  it('uses local date boundaries across daylight-saving changes', () => {
    const points = [
      { time: new Date(2026, 2, 28, 23), value: 6 },
      { time: new Date(2026, 2, 29, 1), value: 7 },
      { time: new Date(2026, 2, 29, 14), value: 0.5 },
      { time: new Date(2026, 2, 30, 1), value: 8 },
    ];
    expect(sleepDays(points).map((day) => [day.time.getDate(), day.time.getHours(), day.total])).toEqual([
      [28, 0, 6],
      [29, 0, 7.5],
      [30, 0, 8],
    ]);
  });

  it('ignores invalid timestamps and durations without inventing zero totals', () => {
    expect(
      sleepDays([
        { value: 7 },
        { time: new Date('invalid'), value: 1 },
        { time: localTime(2) },
        session(2, 12, Number.NaN),
        session(2, 12, Number.POSITIVE_INFINITY),
        session(2, 12, -1),
      ])
    ).toEqual([]);
  });

  it('stacks a full day at one x coordinate and preserves missing days and sessions', () => {
    const { wrapper, options } = mountChart(sleepSeries([session(1, 1, 7), session(1, 14, 1), session(3, 1, 8)]));
    const series = options().series as Highcharts.SeriesColumnOptions[];

    expect(series).toHaveLength(2);
    expect(series[0]).toMatchObject({
      type: 'column',
      stacking: 'normal',
      name: 'Longest sleep',
      color: SLEEP_SESSION_COLORS[0],
      data: [
        { x: 0, y: 7 },
        { x: 1, y: 8 },
      ],
    });
    expect(series[1]).toMatchObject({
      name: 'Additional sleep 1',
      color: SLEEP_SESSION_COLORS[1],
      data: [
        { x: 0, y: 1 },
        { x: 1, y: null },
      ],
    });
    expect(options().yAxis).toMatchObject({ min: 0, reversedStacks: false });
    expect(options().time).toHaveProperty('timezone', Intl.DateTimeFormat().resolvedOptions().timeZone);
    expect(options().xAxis).toMatchObject({ type: 'category' });
    expect(wrapper.get('header p').text()).toBe('Daily total');
    expect(wrapper.get('.health-bucket-details').text()).toContain('8 h 0 min');
    expect(wrapper.get('[aria-label="Sleep session shades"]').text()).toContain('Additional sleep');
  });

  it('keeps every session, including equal timestamps and more sessions than available shades', () => {
    const points = [session(1, 1, 7), ...Array.from({ length: 6 }, () => session(1, 14, 0.5))];
    const { options } = mountChart(sleepSeries(points));
    const series = options().series as Highcharts.SeriesColumnOptions[];
    expect(series).toHaveLength(7);
    expect(series.slice(0, 4).map((item) => item.color)).toEqual(SLEEP_SESSION_COLORS);
    for (let index = 1; index < series.length; index++) {
      expect(series[index]!.color).not.toBe(series[index - 1]!.color);
    }
    expect(series.every((item) => item.borderWidth === 1)).toBe(true);
    expect(series[6]!.data![0]).toMatchObject({ y: 0.5, custom: { bucketIndex: 0 } });
  });

  it('shows the selected session duration and start time, not the first session at that day', () => {
    const { options } = mountChart(sleepSeries([session(1, 1, 7), session(1, 14, 0.5)]));
    const series = options().series as Highcharts.SeriesColumnOptions[];
    const point = series[1]!.data![0] as Highcharts.PointOptionsObject;
    const context = { x: point.x, y: point.y, options: point, series: { name: series[1]!.name } } as Highcharts.Point;
    const tooltip = options().tooltip!.formatter!.call(context, {} as Highcharts.Tooltip);
    const time = new Intl.DateTimeFormat(undefined, { timeStyle: 'short' }).format(localTime(1, 14));

    expect(tooltip).toContain(`Additional sleep 1 · ${time}`);
    expect(tooltip).toContain('0 h 30 min');
    expect(tooltip).toContain('Daily average');
    expect(tooltip).toContain('1 / 1 days');
  });

  it('updates the chart and summary to the latest daily total when naps arrive', async () => {
    const initial = sleepSeries([session(1, 1, 7)]);
    const { wrapper, options } = mountChart(initial);
    const summary = mount(HealthMetricSummaryCard, { props: { series: initial } });
    expect(wrapper.find('.health-sleep-legend').exists()).toBe(false);
    const updated = sleepSeries([session(1, 1, 7), session(1, 14, 0.5)]);
    await wrapper.setProps({ series: groupedSeries(updated) });
    await summary.setProps({ series: updated });

    expect(options().series).toHaveLength(2);
    expect(wrapper.get('.health-bucket-details').text()).toContain('7 h 30 min');
    expect(summary.get('strong').text()).toContain('7.5\u202fh');
    expect(summary.text()).toContain('Latest daily total');
    expect(wrapper.find('.health-sleep-legend').exists()).toBe(true);
  });

  it('handles empty sleep data without a fabricated value or legend', () => {
    const series = sleepSeries([]);
    const { wrapper, options } = mountChart(series);
    const summary = mount(HealthMetricSummaryCard, { props: { series } });
    expect(options().series).toEqual([]);
    expect(wrapper.find('.health-sleep-legend').exists()).toBe(false);
    expect(summary.text()).toContain('No recent value');
    expect(wrapper.find('.health-sleep-legend').exists()).toBe(false);
  });

  it('keeps latest non-sleep cards independent of grouped daily totals', () => {
    const series: GHMetricSeries = {
      ...sleepSeries([session(1, 1, 50), session(1, 14, 70)]),
      dataType: 'steps',
      category: 'ACTIVITY',
      title: 'Steps',
      unit: 'steps',
      decimals: 0,
    };
    const { wrapper, options } = mountChart(series);
    const summary = mount(HealthMetricSummaryCard, { props: { series } });
    expect(options().series).toHaveLength(1);
    expect(options().series![0]).toMatchObject({ data: [{ x: 0, y: 120 }] });
    expect(wrapper.get('.health-bucket-details').text()).toContain('120');
    expect(summary.get('strong').text()).toBe('70\u202fsteps');
  });
});
