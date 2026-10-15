import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import HighchartsVue from 'highcharts-vue';
import Highcharts from '@/bootstrap/highcharts';
import TrackGraph from '@/components/trackdetails/TrackGraph.vue';
import { trackGraphConfigs } from '@/components/trackdetails/trackGraphConfigs';
import type { ChartPoint } from '@/utils/chartSeriesAdapter';

vi.mock('@/composables/useChartSync', () => ({
  useChartSync: () => ({ bindChart: vi.fn(), setChartXMode: vi.fn(), syncPointHover: vi.fn() }),
}));

function point(pointAltitude: number | null): ChartPoint {
  return {
    pointIndex: 0,
    pointTimestamp: new Date('2026-10-01T10:00:00Z'),
    distanceInMeterSinceStart: 0,
    metricStats: {},
    pointAltitude,
    speedInKmhWindow: 8,
    speedBucketAvgKmh: 8,
    elevationGainPerHourWindow: null,
    elevationLossPerHourWindow: null,
    powerWattsWindow: null,
    energyCumulativeWh: null,
  };
}

async function graph(trackDetails: ChartPoint[]) {
  const wrapper = mount(TrackGraph, {
    props: { config: trackGraphConfigs.elevation, trackDetails, syncEnabled: false },
    global: { plugins: [[HighchartsVue, { highcharts: Highcharts }]] },
  });
  await flushPromises();
  return wrapper;
}

describe('track graph unavailable measurements', () => {
  it.each([null, Number.NaN, Number.POSITIVE_INFINITY])('explains missing or unusable elevation %s', async (value) => {
    const wrapper = await graph([point(value)]);
    try {
      expect(wrapper.get('[role="status"]').text()).toBe('Elevation data is unavailable for this track.');
      expect(wrapper.get('.chart').isVisible()).toBe(false);
    } finally {
      wrapper.unmount();
    }
  });

  it.each([0, -10, 500])('keeps a usable elevation of %s plotted despite missing neighbors', async (value) => {
    const wrapper = await graph([point(null), { ...point(value), pointIndex: 1, distanceInMeterSinceStart: 10 }]);
    try {
      expect(wrapper.find('[role="status"]').exists()).toBe(false);
      expect(wrapper.get('.chart').isVisible()).toBe(true);
      expect(wrapper.find('svg.highcharts-root').exists()).toBe(true);
    } finally {
      wrapper.unmount();
    }
  });

  it('updates unavailable state across data, metric and axis changes without hiding speed', async () => {
    const wrapper = await graph([point(null)]);
    try {
      expect(wrapper.get('[role="status"]').text()).toContain('Elevation data is unavailable');
      await wrapper.setProps({ xMode: 'distance', showRange: true });
      expect(wrapper.get('[role="status"]').text()).toContain('Elevation data is unavailable');
      await wrapper.setProps({ config: trackGraphConfigs.speed });
      expect(wrapper.find('[role="status"]').exists()).toBe(false);
      expect(wrapper.get('.chart').isVisible()).toBe(true);
      await wrapper.setProps({ config: trackGraphConfigs.elevation, trackDetails: [point(0)] });
      expect(wrapper.find('[role="status"]').exists()).toBe(false);
      await wrapper.setProps({ trackDetails: [point(null)] });
      expect(wrapper.get('[role="status"]').text()).toBe('Elevation data is unavailable for this track.');
    } finally {
      wrapper.unmount();
    }
  });

  it('resizes a chart that becomes available after mounting hidden', async () => {
    const wrapper = await graph([point(null)]);
    try {
      const chart = Highcharts.charts.find((chart) => chart?.renderTo === wrapper.get('.chart').element)!;
      const reflow = vi.spyOn(chart, 'reflow');
      await wrapper.setProps({ trackDetails: [point(0)] });
      await flushPromises();
      expect(reflow).toHaveBeenCalledTimes(1);
      await wrapper.setProps({ trackDetails: [point(500)] });
      expect(reflow).toHaveBeenCalledTimes(1);
    } finally {
      wrapper.unmount();
    }
  });
  it.each(['energy', 'power'] as const)(
    'shows queued and retry states for %s, then displays calculated values',
    async (metric) => {
      const wrapper = await graph([point(null)]);
      try {
        await wrapper.setProps({
          config: trackGraphConfigs[metric],
          emptyMessage: 'Energy calculation is queued or running.',
        });
        expect(wrapper.get('[role="status"]').text()).toBe('Energy calculation is queued or running.');
        expect(wrapper.get('.chart').isVisible()).toBe(false);
        await wrapper.setProps({ emptyMessage: 'Energy calculation failed. A retry is scheduled.' });
        expect(wrapper.get('[role="status"]').text()).toContain('retry is scheduled');
        await wrapper.setProps({
          emptyMessage: '',
          trackDetails: [{ ...point(null), energyCumulativeWh: 0, powerWattsWindow: 0 }],
        });
        expect(wrapper.find('[role="status"]').exists()).toBe(false);
        expect(wrapper.get('.chart').isVisible()).toBe(true);
        await wrapper.setProps({ trackDetails: [point(null)] });
        expect(wrapper.get('[role="status"]').text()).toContain('data is unavailable for this track.');
      } finally {
        wrapper.unmount();
      }
    }
  );
});
