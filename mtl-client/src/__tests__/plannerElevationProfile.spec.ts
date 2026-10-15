import { enableAutoUnmount, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it } from 'vitest';
import HighchartsVue from 'highcharts-vue';
import Highcharts from '@/bootstrap/highcharts';
import ElevationProfile from '@/planner/components/ElevationProfile.vue';
import { useMeasurementSystem } from '@/composables/useMeasurementSystem';
import { longDistanceDisplayValue } from '@/utils/units';

enableAutoUnmount(afterEach);
afterEach(() => useMeasurementSystem().useDefaultMeasurementSystem());

describe.each(['METRIC', 'US_CUSTOMARY'] as const)('planner distance labels in %s', (system) => {
  it.each([73.2, 732, 7_320, 73_200].flatMap((distance) => [375, 900].map((width) => ({ distance, width }))))(
    'distinguishes ticks for a $distance m route in a $width px chart',
    ({ distance, width }) => {
      useMeasurementSystem().setMeasurementSystem(system);
      const wrapper = mount(ElevationProfile, {
        props: {
          coordinates: [
            [8, 47, 100],
            [8.01, 47, 110],
          ],
          totalDistanceM: distance,
        },
        global: { plugins: [[HighchartsVue, { highcharts: Highcharts }]] },
      });
      const container = wrapper.get('[data-highcharts-chart]').element;
      const chart = Highcharts.charts.find((item) => item?.renderTo === container)!;
      chart.setSize(width, 180, false);
      const axis = chart.xAxis[0];
      const ticks = axis.tickPositions!.filter((position) => axis.ticks[position]?.label);
      const labels = ticks.map((position) => axis.ticks[position].label!.textStr);
      expect(labels.length).toBeGreaterThan(2);
      expect(new Set(labels).size).toBe(labels.length);
      const values = labels.map((label) => Number.parseFloat(String(label)));
      for (let i = 0; i < values.length; i++) {
        expect(labels[i]).toContain(system === 'METRIC' ? 'km' : 'mi');
        const actualDistance = longDistanceDisplayValue(ticks[i] * 1000, system);
        const spacing = longDistanceDisplayValue((ticks[1] - ticks[0]) * 1000, system);
        expect(Math.abs(values[i] - actualDistance)).toBeLessThan(spacing / 2);
        if (i > 0) expect(values[i]).toBeGreaterThan(values[i - 1]);
      }
      // Labels must not change the canonical kilometre series used by map hover.
      expect(chart.series[0].points.at(-1)!.x).toBeCloseTo(distance / 1000);
    }
  );
});

describe('planner elevation profile', () => {
  it('keeps measured heights and renders missing elevation as a gap, including genuine sea level', () => {
    const wrapper = mount(ElevationProfile, {
      props: {
        coordinates: [
          [8, 47, 100],
          [8.001, 47, Number.NaN],
          [8.002, 47, 0],
        ],
        totalDistanceM: 200,
      },
      global: { plugins: [[HighchartsVue, { highcharts: Highcharts }]] },
    });
    const container = wrapper.get('[data-highcharts-chart]').element;
    const chart = Highcharts.charts.find((item) => item?.renderTo === container)!;
    expect(chart.series[0]!.points.map((point) => point.y)).toEqual([100, null, 0]);
    expect(chart.series[0]!.points.at(-1)!.x).toBeCloseTo(0.2);
  });

  it('publishes a geographic position when a real chart point is hovered and clears it on mouse out', () => {
    const wrapper = mount(ElevationProfile, {
      props: {
        coordinates: [
          [8, 47, 100],
          [8.001, 47.001, 110],
        ],
        totalDistanceM: 200,
      },
      global: { plugins: [[HighchartsVue, { highcharts: Highcharts }]] },
    });
    const container = wrapper.get('[data-highcharts-chart]').element;
    const chart = Highcharts.charts.find((item) => item?.renderTo === container)!;
    const point = chart.series[0].points[1];
    point.onMouseOver();
    expect(wrapper.emitted('hover')?.at(-1)).toEqual([{ lng: 8.001, lat: 47.001, elevationM: 110, distanceM: 200 }]);
    point.onMouseOut();
    expect(wrapper.emitted('hover')?.at(-1)).toEqual([null]);
  });

  it('shows the placeholder when no heights are available', () => {
    const wrapper = mount(ElevationProfile, {
      props: {
        coordinates: [
          [8, 47, Number.NaN],
          [8.001, 47, Number.NaN],
        ],
      },
      global: { plugins: [[HighchartsVue, { highcharts: Highcharts }]] },
    });
    expect(wrapper.find('[data-highcharts-chart]').exists()).toBe(false);
    expect(wrapper.find('.elev-profile__placeholder').exists()).toBe(true);
  });
});
