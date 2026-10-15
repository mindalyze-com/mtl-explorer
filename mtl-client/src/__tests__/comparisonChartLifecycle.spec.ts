import { mount } from '@vue/test-utils';
import { nextTick } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import HighchartsVue from 'highcharts-vue';
import Highcharts from '@/bootstrap/highcharts';
import ComparisonChart from '@/components/measure/ComparisonChart.vue';
import type { SegmentChartSeries } from '@/components/measure/segmentDisplay';

const series = (ids: string[], ready = ids): SegmentChartSeries[] =>
  ids.map((id, index) => ({
    id,
    name: id,
    color: ['#2563eb', '#ea580c', '#16a34a'][index],
    data: ready.includes(id)
      ? [
          [0, 0],
          [1, index * 5],
        ]
      : [],
  }));

beforeEach(() => {
  document.documentElement.style.fontSize = '16px';
  vi.spyOn(Highcharts.SVGElement.prototype, 'getBBox').mockReturnValue({ x: 0, y: 0, width: 50, height: 16 });
  vi.stubGlobal(
    'ResizeObserver',
    class {
      observe() {}
      disconnect() {}
    }
  );
});
afterEach(() => {
  for (const chart of Highcharts.charts) chart?.destroy();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('comparison chart real rendering lifecycle', () => {
  it('renders and clears the linked cursor and emphasized comparison band', async () => {
    const wrapper = mount(ComparisonChart, {
      attachTo: document.body,
      props: { title: 'Speed', series: series(['a', 'b']) },
      global: { plugins: [[HighchartsVue, { highcharts: Highcharts }]] },
    });
    await nextTick();
    await wrapper.setProps({
      linkedHover: { x: 0.25, axis: 'distance' },
      ghosts: [{ seriesId: 'b', x: 0.75, emphasized: true }],
    });
    expect(wrapper.find('path.segment-cursor').exists()).toBe(true);
    expect(wrapper.find('path.highcharts-plot-band').exists()).toBe(true);

    await wrapper.setProps({ linkedHover: null });
    expect(wrapper.find('path.segment-cursor').exists()).toBe(false);
    expect(wrapper.find('path.highcharts-plot-band').exists()).toBe(false);
    wrapper.unmount();
  });

  it('keeps the full chart range after mouse dragging and wheel gestures', async () => {
    const wrapper = mount(ComparisonChart, {
      attachTo: document.body,
      props: { title: 'Speed', series: series(['a', 'b']), xMax: 1.3 },
      global: { plugins: [[HighchartsVue, { highcharts: Highcharts }]] },
    });
    await nextTick();
    const chart = Highcharts.charts.find((chart) => chart?.renderTo === wrapper.get('.cmp-chart').element)!;
    const start = { clientX: chart.plotLeft + 20, clientY: chart.plotTop + 30, bubbles: true };
    const end = { ...start, clientX: chart.plotLeft + 120 };
    chart.container.dispatchEvent(new MouseEvent('mousedown', { ...start, buttons: 1 }));
    chart.container.dispatchEvent(new MouseEvent('mousemove', { ...end, buttons: 1 }));
    expect(wrapper.find('.highcharts-selection-marker').exists()).toBe(false);
    document.dispatchEvent(new MouseEvent('mouseup', end));
    chart.container.dispatchEvent(new WheelEvent('wheel', { ...start, deltaY: -100 }));
    await nextTick();
    expect(chart.xAxis[0].getExtremes()).toMatchObject({ min: 0, max: 1.3 });
    expect(chart.resetZoomButton).toBeUndefined();
    expect(wrapper.emitted('hover-x')).toBeDefined();
    wrapper.unmount();
  });
  it('renders each selected attempt as its comparison response arrives, including after redoing a selection', async () => {
    const wrapper = mount(ComparisonChart, {
      attachTo: document.body,
      props: { title: 'Time difference', series: [], yZeroLine: true },
      global: { plugins: [[HighchartsVue, { highcharts: Highcharts }]] },
    });
    const chart = Highcharts.charts.find((chart) => chart?.renderTo === wrapper.get('.cmp-chart').element)!;
    for (const ids of [
      ['a', 'b', 'c'],
      ['b', 'c', 'd'],
      ['d', 'a', 'b'],
    ]) {
      await wrapper.setProps({ series: [] });
      for (let loaded = 0; loaded <= ids.length; loaded++) {
        await wrapper.setProps({ series: series(ids, ids.slice(0, loaded)) });
        await nextTick();
        expect(chart.series.map((entry) => entry.options.id)).toEqual(ids);
        expect(chart.series.filter((entry) => entry.points.length)).toHaveLength(loaded);
        expect(wrapper.findAll('path.highcharts-graph')).toHaveLength(loaded);
        if (loaded) {
          const reference = chart.series[0].points[0];
          const graph = wrapper.get('path.highcharts-graph').attributes('d');
          const renderedY = Number(graph.match(/^M [^ ]+ ([^ ]+)/)?.[1]);
          expect(renderedY).toBeCloseTo(reference.plotY!, 8);
        }
        for (const entry of chart.series)
          for (const point of entry.points) expect(point.plotY).toBeGreaterThanOrEqual(0);
      }
    }
    wrapper.unmount();
  });
});
