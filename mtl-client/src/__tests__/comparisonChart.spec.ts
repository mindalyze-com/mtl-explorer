import { mount } from '@vue/test-utils';
import { defineComponent, nextTick } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type Highcharts from 'highcharts';
import ComparisonChart from '@/components/measure/ComparisonChart.vue';
import { useMeasurementSystem } from '@/composables/useMeasurementSystem';

const HighchartsStub = defineComponent({
  props: { options: { type: Object, required: true } },
  setup() {
    return {
      chart: {
        container: document.createElement('div'),
        xAxis: [
          {
            addPlotLine: vi.fn(),
            removePlotLine: vi.fn(),
            addPlotBand: vi.fn(),
            removePlotBand: vi.fn(),
            setExtremes: vi.fn(),
            toValue: vi.fn(() => 0.72),
          },
        ],
        plotLeft: 80,
        plotTop: 0,
        pointer: { normalize: (event: MouseEvent) => ({ chartX: event.clientX, chartY: event.clientY }) },
        isInsidePlot: vi.fn(() => true),
      },
    };
  },
  template: '<div />',
});
beforeEach(() => {
  useMeasurementSystem().setMeasurementSystem('METRIC');
  vi.stubGlobal(
    'ResizeObserver',
    class {
      observe() {}
      disconnect() {}
    }
  );
});
afterEach(() => vi.unstubAllGlobals());

describe('comparison chart sparse samples', () => {
  it('shares focused keyboard samples and pins them while pointer positions remain continuous', async () => {
    const wrapper = mount(ComparisonChart, {
      props: {
        title: 'Speed',
        xMax: 1,
        series: [{ id: 'synthetic', name: 'Synthetic track', color: '#ff0000', data: [[0.5, 8]] }],
      },
      global: { stubs: { highcharts: HighchartsStub } },
    });
    await nextTick();
    const stub = wrapper.findComponent(HighchartsStub);
    const chart = (stub.vm as unknown as { chart: Highcharts.Chart }).chart;
    const options = () => stub.props('options') as Highcharts.Options;
    const point = { x: 0.5, series: { chart } } as Highcharts.Point;
    const events = () => options().plotOptions!.series!.point!.events!;
    events().mouseOver!.call(point, {} as Highcharts.PointMouseOverEventObject);
    expect(wrapper.emitted('hover-x')?.at(-1)).toEqual([{ x: 0.5, axis: 'distance' }]);
    events().click!.call(point, { chartX: 150 } as Highcharts.PointClickEventObject);
    expect(wrapper.emitted('pin-position')?.at(-1)).toEqual([{ x: 0.5, axis: 'distance' }]);
    const keyboardClick = new MouseEvent('click', { clientX: 150, clientY: 80, detail: 0 });
    Object.assign(keyboardClick, { chartX: 150 });
    events().click!.call(point, keyboardClick as Highcharts.PointClickEventObject);
    expect(wrapper.emitted('pin-position')?.at(-1)).toEqual([{ x: 0.5, axis: 'distance' }]);
    const click = new MouseEvent('click', { clientX: 150, clientY: 80, detail: 1 });
    Object.assign(click, { chartX: 150 });
    events().click!.call(point, click as Highcharts.PointClickEventObject);
    expect(wrapper.emitted('pin-position')?.at(-1)).toEqual([{ x: 0.72, axis: 'distance' }]);
    chart.container.dispatchEvent(new MouseEvent('mousemove', { clientX: 150, clientY: 80 }));
    expect(wrapper.emitted('hover-x')?.at(-1)).toEqual([{ x: 0.72, axis: 'distance' }]);
    await wrapper.setProps({ xMode: 'time', xMax: 60000 });
    point.x = 30000;
    events().mouseOver!.call(point, {} as Highcharts.PointMouseOverEventObject);
    events().click!.call(point, {} as Highcharts.PointClickEventObject);
    expect(wrapper.emitted('hover-x')?.at(-1)).toEqual([{ x: 30000, axis: 'time' }]);
    expect(wrapper.emitted('pin-position')?.at(-1)).toEqual([{ x: 30000, axis: 'time' }]);
    wrapper.unmount();
  });
  it('scrubs with one finger, allows vertical scrolling, and leaves multi-touch out of chart inspection', async () => {
    const onScrubPosition = vi.fn();
    const wrapper = mount(ComparisonChart, {
      props: { title: 'Speed', series: [], xMax: 1, onScrubPosition },
      global: { stubs: { highcharts: HighchartsStub } },
    });
    await nextTick();
    const chart = (wrapper.findComponent(HighchartsStub).vm as unknown as { chart: Highcharts.Chart }).chart;
    const sendTouch = (type: string, coordinates: number[][]) => {
      const event = new Event(type, { bubbles: true, cancelable: true });
      Object.defineProperty(event, 'touches', {
        value: coordinates.map(([clientX, clientY]) => ({ clientX, clientY })),
      });
      chart.container.dispatchEvent(event);
      return event;
    };
    const highchartsTouch = vi.fn();
    chart.container.addEventListener('touchmove', highchartsTouch);
    sendTouch('touchstart', [[100, 80]]);
    const scrub = sendTouch('touchmove', [[160, 85]]);
    expect(scrub.defaultPrevented).toBe(true);
    expect(wrapper.emitted('scrub-position')).toHaveLength(2);
    expect(wrapper.emitted('scrub-position')?.at(-1)).toEqual([{ x: 0.72, axis: 'distance' }]);
    expect(highchartsTouch).not.toHaveBeenCalled();
    expect(sendTouch('touchend', []).defaultPrevented).toBe(true);
    expect(wrapper.emitted('pin-position')).toBeUndefined();
    sendTouch('touchstart', [[100, 80]]);
    const scroll = sendTouch('touchmove', [[101, 140]]);
    expect(scroll.defaultPrevented).toBe(false);
    expect(wrapper.emitted('scrub-position')).toHaveLength(3);
    expect(sendTouch('touchend', []).defaultPrevented).toBe(false);
    sendTouch('touchstart', [
      [100, 80],
      [160, 80],
    ]);
    const pinch = sendTouch('touchmove', [
      [80, 80],
      [180, 80],
    ]);
    expect(pinch.defaultPrevented).toBe(false);
    expect(highchartsTouch).toHaveBeenCalledOnce();
    expect(wrapper.emitted('scrub-position')).toHaveLength(3);
    wrapper.unmount();
    sendTouch('touchstart', [[100, 80]]);
    expect(onScrubPosition).toHaveBeenCalledTimes(3);
  });
  it('keeps segment extents and displays isolated samples without connecting gaps', async () => {
    const wrapper = mount(ComparisonChart, {
      props: {
        title: 'Heart rate',
        unit: 'bpm',
        decimals: 0,
        xMax: 2,
        series: [
          {
            id: 'synthetic-1',
            name: 'Synthetic track',
            color: '#ff0000',
            data: [
              [0.5, 120],
              [1, null],
              [1.5, 140],
            ],
          },
        ],
      },
      global: { stubs: { highcharts: HighchartsStub } },
    });
    const options = () => wrapper.findComponent(HighchartsStub).props('options') as Highcharts.Options;
    expect(wrapper.find('[aria-label="Track legend"]').exists()).toBe(false);
    expect(options().tooltip).toMatchObject({ enabled: false });
    expect(options().plotOptions?.series).toMatchObject({ enableMouseTracking: true });
    expect((options().yAxis as Highcharts.YAxisOptions).plotLines ?? []).toHaveLength(0);
    expect(options().xAxis).toMatchObject({ min: 0, max: 2, title: { text: 'Distance traveled' } });
    const formatter = (options().xAxis as Highcharts.XAxisOptions).labels!.formatter!;
    for (const step of [0.25, 0.05, 0.025]) {
      const label = formatter.call({
        value: step,
        axis: { tickPositions: [0, step] },
      } as Highcharts.AxisLabelsFormatterContextObject);
      expect(label).toBe(`${step}\u202fkm`);
    }
    expect(options().series?.[0]).toMatchObject({
      connectNulls: false,
      data: [{ x: 0.5, y: 120, marker: { enabled: true } }, [1, null], { x: 1.5, y: 140, marker: { enabled: true } }],
    });
    await wrapper.setProps({ linkedHover: { x: 1.9, axis: 'distance' } });
    await wrapper.setProps({ xMax: 60000, xMode: 'time' });
    expect(options().xAxis).toMatchObject({ min: 0, max: 60000, title: { text: 'Elapsed time' } });
    wrapper.unmount();
  });
  it('keeps the full range and exact linked cursors through chart changes without snapping to sparse samples', async () => {
    const wrapper = mount(ComparisonChart, {
      props: {
        title: 'Heart rate',
        xMax: 1.3,
        series: [
          {
            id: 'synthetic-1',
            name: 'Synthetic track',
            color: '#ff0000',
            data: [
              [0.5, 120],
              [0.9, 130],
            ],
          },
        ],
        linkedHover: { x: 0.72, axis: 'distance' },
      },
      global: { stubs: { highcharts: HighchartsStub } },
    });
    const stub = wrapper.findComponent(HighchartsStub);
    const chart = (stub.vm as unknown as { chart: Highcharts.Chart }).chart;
    const options = () => stub.props('options') as Highcharts.Options;
    expect(options().chart).toMatchObject({
      marginLeft: 80,
      marginRight: 24,
      zooming: { type: undefined, pinchType: undefined, singleTouch: false, mouseWheel: { enabled: false } },
      panning: { enabled: false },
    });
    expect(options().xAxis).toMatchObject({
      min: 0,
      max: 1.3,
      minPadding: 0,
      maxPadding: 0,
      minRange: 0,
      crosshair: false,
      zoomEnabled: false,
    });
    expect(options().yAxis).toMatchObject({ zoomEnabled: false });
    options().chart!.events!.render!.call(chart, new Event('render'));
    expect(chart.xAxis[0].addPlotLine).toHaveBeenLastCalledWith(expect.objectContaining({ value: 0.72 }));
    await wrapper.setProps({ unit: 'bpm' });
    expect(options().xAxis).toMatchObject({ min: 0, max: 1.3 });
    options().chart!.events!.render!.call(chart, new Event('render'));
    expect(chart.xAxis[0].addPlotLine).toHaveBeenLastCalledWith(expect.objectContaining({ value: 0.72 }));
    expect(options().chart!.events!.selection).toBeUndefined();
    await wrapper.setProps({ xMax: 1.5 });
    expect(chart.xAxis[0].setExtremes).toHaveBeenLastCalledWith(0, 1.5, true, false);
    vi.mocked(chart.xAxis[0].addPlotLine).mockClear();
    await wrapper.setProps({ linkedHover: { x: 720, axis: 'time' } });
    expect(chart.xAxis[0].removePlotLine).toHaveBeenCalledWith('segment-cursor');
    expect(chart.xAxis[0].addPlotLine).not.toHaveBeenCalled();
    chart.container.dispatchEvent(new MouseEvent('mousemove', { clientX: 150, clientY: 80 }));
    expect(wrapper.emitted('hover-x')?.at(-1)).toEqual([{ x: 0.72, axis: 'distance' }]);
    options().chart!.events!.click!.call(chart, { chartX: 150, chartY: 80 } as Highcharts.PointerEventObject);
    expect(wrapper.emitted('pin-position')?.at(-1)).toEqual([{ x: 0.72, axis: 'distance' }]);
    await wrapper.setProps({ activeAttempt: 'synthetic-1' });
    expect(options().series?.[0]).toMatchObject({ id: 'synthetic-1', lineWidth: 3, opacity: 1 });
    await wrapper.setProps({ activeAttempt: 'another-attempt' });
    expect(options().series?.[0]).toMatchObject({ lineWidth: 2, opacity: 0.25 });
    vi.mocked(chart.isInsidePlot).mockReturnValue(false);
    chart.container.dispatchEvent(new MouseEvent('mousemove', { clientX: 10, clientY: 80 }));
    expect(wrapper.emitted('hover-x')).toHaveLength(1);
    wrapper.unmount();
  });
});
