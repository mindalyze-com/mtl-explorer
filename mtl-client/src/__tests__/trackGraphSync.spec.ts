import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { defineComponent, h, nextTick } from 'vue';
import TrackGraph from '@/components/trackdetails/TrackGraph.vue';
import type { TrackGraphConfig } from '@/components/trackdetails/trackGraphConfigs';
import type { ChartPoint, MetricKey } from '@/utils/chartSeriesAdapter';

const chartSyncMocks = vi.hoisted(() => ({
  bindChart: vi.fn(),
  chart: {
    container: document.createElement('div'),
  },
  cleanup: vi.fn(),
  setChartXMode: vi.fn(),
}));

vi.mock('@/composables/useChartSync', () => ({
  useChartSync: () => ({
    bindChart: chartSyncMocks.bindChart,
    setChartXMode: chartSyncMocks.setChartXMode,
  }),
}));

const HighchartsStub = defineComponent({
  name: 'Highcharts',
  setup(_props, { expose }) {
    expose({ chart: chartSyncMocks.chart });
    return () => h('div', { 'data-test': 'highcharts' });
  },
});

const config: TrackGraphConfig = {
  icon: 'bi-graph-up',
  title: 'Elevation',
  seriesName: 'Elevation',
  seriesColor: '#6366f1',
  extractY: () => 1,
};

const rangeConfig: TrackGraphConfig = {
  ...config,
  rangeMetricKey: 'ALTITUDE_M',
  extractY: (point) => point.pointAltitude,
};

const ALTITUDE_METRIC_KEY: MetricKey = 'ALTITUDE_M';

function chartPoint(overrides: Partial<ChartPoint> = {}): ChartPoint {
  return {
    pointIndex: 1,
    pointTimestamp: new Date('2026-01-01T10:00:00Z'),
    distanceInMeterSinceStart: 1000,
    metricStats: {
      [ALTITUDE_METRIC_KEY]: {
        avg: 110,
        min: 100,
        max: 130,
        sampleCount: 4,
      },
    },
    pointAltitude: 110,
    speedInKmhWindow: null,
    elevationGainPerHourWindow: null,
    elevationLossPerHourWindow: null,
    powerWattsWindow: null,
    energyCumulativeWh: null,
    ...overrides,
  };
}

async function mountGraph(
  syncEnabled: boolean,
  options: {
    graphConfig?: TrackGraphConfig;
    showRange?: boolean;
    trackDetails?: ChartPoint[];
  } = {}
) {
  const wrapper = mount(TrackGraph, {
    props: {
      config: options.graphConfig ?? config,
      syncEnabled,
      showRange: options.showRange ?? false,
      trackDetails: options.trackDetails ?? [],
      xMode: 'time',
    },
    global: {
      components: {
        highcharts: HighchartsStub,
      },
    },
  });

  await nextTick();
  await flushPromises();
  await nextTick();
  return wrapper;
}

describe('TrackGraph chart sync binding', () => {
  beforeEach(() => {
    chartSyncMocks.bindChart.mockReset();
    chartSyncMocks.cleanup.mockReset();
    chartSyncMocks.setChartXMode.mockReset();
    chartSyncMocks.bindChart.mockReturnValue(chartSyncMocks.cleanup);
  });

  it('does not bind chart sync while disabled', async () => {
    await mountGraph(false);

    expect(chartSyncMocks.bindChart).not.toHaveBeenCalled();
  });

  it('unbinds chart sync when disabled and rebinds when enabled again', async () => {
    const wrapper = await mountGraph(true);

    expect(chartSyncMocks.bindChart).toHaveBeenCalledTimes(1);
    expect(chartSyncMocks.setChartXMode).toHaveBeenCalledWith(chartSyncMocks.chart, 'time');
    expect(chartSyncMocks.bindChart).toHaveBeenCalledWith(chartSyncMocks.chart, 'time');

    await wrapper.setProps({ syncEnabled: false });
    await nextTick();

    expect(chartSyncMocks.cleanup).toHaveBeenCalledTimes(1);

    await wrapper.setProps({ syncEnabled: true });
    await nextTick();

    expect(chartSyncMocks.bindChart).toHaveBeenCalledTimes(2);
  });
});

describe('TrackGraph range band rendering', () => {
  beforeEach(() => {
    chartSyncMocks.bindChart.mockReset();
    chartSyncMocks.cleanup.mockReset();
    chartSyncMocks.setChartXMode.mockReset();
    chartSyncMocks.bindChart.mockReturnValue(chartSyncMocks.cleanup);
  });

  it('renders only the average series when range is off', async () => {
    const wrapper = await mountGraph(false, {
      graphConfig: rangeConfig,
      showRange: false,
      trackDetails: [chartPoint()],
    });

    const series = (wrapper.vm as unknown as { chartOptions: { series: unknown[] } }).chartOptions.series;

    expect(series).toHaveLength(1);
  });

  it('renders average plus arearange series when range is enabled for a ranged metric', async () => {
    const wrapper = await mountGraph(false, {
      graphConfig: rangeConfig,
      showRange: true,
      trackDetails: [chartPoint()],
    });

    const series = (wrapper.vm as unknown as { chartOptions: { series: Array<{ type?: string; data?: unknown[] }> } })
      .chartOptions.series;

    expect(series).toHaveLength(2);
    expect(series[0].type).toBe('line');
    expect(series[1].type).toBe('arearange');
  });

  it('keeps single-series rendering when range is enabled for a metric without range config', async () => {
    const wrapper = await mountGraph(false, {
      graphConfig: config,
      showRange: true,
      trackDetails: [chartPoint()],
    });

    const series = (wrapper.vm as unknown as { chartOptions: { series: unknown[] } }).chartOptions.series;

    expect(series).toHaveLength(1);
  });

  it('adds range low and high values to average points for tooltip rendering', async () => {
    const wrapper = await mountGraph(false, {
      graphConfig: rangeConfig,
      showRange: true,
      trackDetails: [chartPoint()],
    });

    const series = (
      wrapper.vm as unknown as { chartOptions: { series: Array<{ data?: Array<Record<string, unknown>> }> } }
    ).chartOptions.series;

    expect(series[0].data?.[0]).toMatchObject({
      y: 110,
      rangeLow: 100,
      rangeHigh: 130,
    });
  });
});
