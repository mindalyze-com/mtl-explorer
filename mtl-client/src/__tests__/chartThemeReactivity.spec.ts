import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Component } from 'vue';
import HighchartsVue from 'highcharts-vue';
import Highcharts from '@/bootstrap/highcharts';
import TrackGraph from '@/components/trackdetails/TrackGraph.vue';
import ComparisonChart from '@/components/measure/ComparisonChart.vue';
import ElevationProfile from '@/planner/components/ElevationProfile.vue';
import { useTheme } from '@/composables/useTheme';

describe('mounted chart theme changes', () => {
  let style: HTMLStyleElement;
  beforeEach(async () => {
    vi.stubGlobal(
      'ResizeObserver',
      class {
        observe() {}
        disconnect() {}
      }
    );
    style = document.createElement('style');
    style.textContent = `
      :root { --chart-text: #64748b; --text-muted: #64748b; --chart-grid: #eeeeee;
        --chart-tooltip-bg: #ffffff; --chart-tooltip-text: #334155; }
      :root[data-theme="dark"] { --chart-text: #94a3b8; --text-muted: #94a3b8;
        --chart-grid: #222222; --chart-tooltip-bg: #0f172a; --chart-tooltip-text: #e2e8f0; }
    `;
    document.head.append(style);
    useTheme().setScheme('light');
    await flushPromises();
  });
  afterEach(async () => {
    style.remove();
    useTheme().setScheme('light');
    await flushPromises();
    vi.unstubAllGlobals();
  });

  const cases: Array<[string, Component, Record<string, unknown>]> = [
    [
      'track details',
      TrackGraph,
      {
        syncEnabled: false,
        xMode: 'distance',
        config: {
          icon: 'bi-graph-up',
          title: 'Elevation',
          seriesName: 'Elevation',
          seriesColor: '#6366f1',
          extractY: (point: { pointAltitude: number }) => point.pointAltitude,
        },
        trackDetails: [
          {
            pointIndex: 0,
            pointTimestamp: new Date('2026-01-01T00:00:00Z'),
            distanceInMeterSinceStart: 0,
            pointAltitude: 100,
          },
          {
            pointIndex: 1,
            pointTimestamp: new Date('2026-01-01T00:01:00Z'),
            distanceInMeterSinceStart: 100,
            pointAltitude: 120,
          },
        ],
      },
    ],
    [
      'comparison',
      ComparisonChart,
      {
        title: 'Elevation',
        xMax: 1,
        linkedRange: { min: 0.1, max: 0.9, axis: 'distance' },
        series: [
          {
            id: 'synthetic',
            name: 'Synthetic track',
            color: '#6366f1',
            data: [
              [0, 100],
              [0.5, null],
              [1, 120],
            ],
          },
        ],
      },
    ],
    [
      'planner elevation',
      ElevationProfile,
      {
        coordinates: [
          [8, 47, 100],
          [8.001, 47, Number.NaN],
          [8.002, 47, 0],
        ],
        totalDistanceM: 200,
      },
    ],
  ];

  it.each(cases)('recolors %s in place and preserves its data and range', async (_name, component, props) => {
    const wrapper = mount(component, {
      props,
      global: { plugins: [[HighchartsVue, { highcharts: Highcharts }]] },
    });
    try {
      await flushPromises();
      const container = wrapper.get('[data-highcharts-chart]').element;
      const chart = Highcharts.charts.find((item) => item?.renderTo === container)!;
      const content = () => ({
        series: chart.series.map((series) => series.options.data),
        min: chart.xAxis[0].userMin,
        max: chart.xAxis[0].userMax,
      });
      const baseline = structuredClone(content());
      for (const scheme of ['dark', 'light'] as const) {
        useTheme().setScheme(scheme);
        await flushPromises();
        const tokens = getComputedStyle(document.documentElement);
        expect(Highcharts.charts.find((item) => item?.renderTo === container)).toBe(chart);
        expect(chart.xAxis[0].options.labels?.style?.color).toBe(tokens.getPropertyValue('--chart-text').trim());
        expect(chart.yAxis[0].options.gridLineColor).toBe(tokens.getPropertyValue('--chart-grid').trim());
        if (chart.options.tooltip?.enabled !== false) {
          expect(chart.options.tooltip?.backgroundColor).toBe(tokens.getPropertyValue('--chart-tooltip-bg').trim());
          expect(chart.options.tooltip?.style?.color).toBe(tokens.getPropertyValue('--chart-tooltip-text').trim());
        }
        expect(content()).toEqual(baseline);
      }
    } finally {
      wrapper.unmount();
    }
  });
});
