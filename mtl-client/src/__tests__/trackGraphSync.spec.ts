import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { defineComponent, h, nextTick } from 'vue';
import TrackGraph from '@/components/trackdetails/TrackGraph.vue';
import { trackGraphConfigs, type TrackGraphConfig } from '@/components/trackdetails/trackGraphConfigs';
import type { ChartPoint, MetricKey } from '@/utils/chartSeriesAdapter';
import type Highcharts from 'highcharts';
import HighchartsVue from 'highcharts-vue';
import ApplicationCharts from '@/bootstrap/highcharts';

const chartSyncMocks = vi.hoisted(() => ({
  bindChart: vi.fn(),
  chart: {
    container: document.createElement('div'),
  },
  cleanup: vi.fn(),
  setChartXMode: vi.fn(),
  syncPointHover: vi.fn(),
}));

vi.mock('@/composables/useChartSync', () => ({
  useChartSync: () => ({
    bindChart: chartSyncMocks.bindChart,
    setChartXMode: chartSyncMocks.setChartXMode,
    syncPointHover: chartSyncMocks.syncPointHover,
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

const extractedRangeConfig: TrackGraphConfig = {
  ...config,
  extractY: (point) => point.heartRateBpm,
  extractRange: (point) =>
    point.heartRateMinimumBpm == null || point.heartRateMaximumBpm == null
      ? null
      : { low: point.heartRateMinimumBpm, high: point.heartRateMaximumBpm },
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
    speedBucketAvgKmh: null,
    elevationGainPerHourWindow: null,
    elevationLossPerHourWindow: null,
    powerWattsWindow: null,
    energyCumulativeWh: null,
    ...overrides,
  };
}

function firstSeriesPointMouseOver(wrapper: ReturnType<typeof mount>) {
  const series = (
    wrapper.vm as unknown as {
      chartOptions: {
        series: Array<{
          point?: {
            events?: {
              mouseOver?: (this: Highcharts.Point) => void;
            };
          };
        }>;
      };
    }
  ).chartOptions.series;
  return series[0].point?.events?.mouseOver;
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
    chartSyncMocks.syncPointHover.mockReset();
    chartSyncMocks.bindChart.mockReturnValue(chartSyncMocks.cleanup);
  });

  it('does not bind chart sync while disabled', async () => {
    await mountGraph(false);

    expect(chartSyncMocks.bindChart).not.toHaveBeenCalled();
  });

  it('lets one-finger touch gestures scroll instead of enabling native tooltip tracking', async () => {
    const wrapper = await mountGraph(true);
    const options = (wrapper.vm as unknown as { chartOptions: Highcharts.Options }).chartOptions;

    expect(options.tooltip?.followTouchMove).toBe(false);
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

  it('bridges native Highcharts point hover into track cursor sync', async () => {
    const wrapper = await mountGraph(true, { trackDetails: [chartPoint()] });
    const point = { x: 10 } as Highcharts.Point;

    firstSeriesPointMouseOver(wrapper)?.call(point);

    expect(chartSyncMocks.syncPointHover).toHaveBeenCalledWith(point);
  });

  it('does not bridge native point hover while chart sync is disabled', async () => {
    const wrapper = await mountGraph(false, { trackDetails: [chartPoint()] });
    const point = { x: 10 } as Highcharts.Point;

    firstSeriesPointMouseOver(wrapper)?.call(point);

    expect(chartSyncMocks.syncPointHover).not.toHaveBeenCalled();
  });
});

describe('TrackGraph range band rendering', () => {
  beforeEach(() => {
    chartSyncMocks.bindChart.mockReset();
    chartSyncMocks.cleanup.mockReset();
    chartSyncMocks.setChartXMode.mockReset();
    chartSyncMocks.syncPointHover.mockReset();
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

  it('renders ranges supplied by a non-GPS series extractor', async () => {
    const wrapper = await mountGraph(false, {
      graphConfig: extractedRangeConfig,
      showRange: true,
      trackDetails: [chartPoint({ heartRateBpm: 120, heartRateMinimumBpm: 91, heartRateMaximumBpm: 146 })],
    });

    const series = (
      wrapper.vm as unknown as { chartOptions: { series: Array<{ data?: Array<Record<string, unknown>> }> } }
    ).chartOptions.series;

    expect(series[0].data?.[0]).toMatchObject({ y: 120, rangeLow: 91, rangeHigh: 146 });
    expect(series[1].data?.[0]).toMatchObject({ low: 91, high: 146 });
  });

  it('keeps partial heart-rate coverage on the full track axis in both modes', async () => {
    const wrapper = await mountGraph(false, {
      graphConfig: extractedRangeConfig,
      showRange: true,
      trackDetails: [chartPoint({ heartRateBpm: 120, heartRateMinimumBpm: 91, heartRateMaximumBpm: 146 })],
    });
    await wrapper.setProps({ xStartTimestamp: new Date('2026-01-01T09:59:00Z'), xMax: 180_000 });

    const options = () => (wrapper.vm as unknown as { chartOptions: Highcharts.Options }).chartOptions;
    const series = () => options().series as Array<{ data: Array<{ x: number; ts: number }> }>;
    expect(options().xAxis).toMatchObject({ min: 0, max: 180_000, minPadding: 0, maxPadding: 0 });
    expect(series()[0].data[0].x).toBe(60_000);
    expect(series()[1].data[0].x).toBe(60_000);
    const margins = [options().chart?.marginLeft, options().chart?.marginRight];

    await wrapper.setProps({ xMode: 'distance', xMax: 5 });
    expect(options().xAxis).toMatchObject({ min: 0, max: 5 });
    expect(series()[0].data[0].x).toBe(1);
    expect(series()[1].data[0].x).toBe(1);
    expect(series()[0].data[0].ts).toBe(new Date('2026-01-01T10:00:00Z').getTime());

    await wrapper.setProps({ showRange: false });
    expect(options().xAxis).toMatchObject({ min: 0, max: 5 });
    expect([options().chart?.marginLeft, options().chart?.marginRight]).toEqual(margins);
  });

  it('normalizes malformed range data without changing the center line', async () => {
    const wrapper = await mountGraph(false, {
      graphConfig: rangeConfig,
      showRange: true,
      trackDetails: [
        chartPoint({
          metricStats: {
            [ALTITUDE_METRIC_KEY]: {
              avg: 110,
              min: 130,
              max: 100,
              sampleCount: 4,
            },
          },
        }),
      ],
    });

    const series = (
      wrapper.vm as unknown as { chartOptions: { series: Array<{ data?: Array<Record<string, unknown>> }> } }
    ).chartOptions.series;

    expect(series[0].data?.[0]).toMatchObject({
      y: 110,
      rangeLow: 100,
      rangeHigh: 130,
    });
    expect(series[1].data?.[0]).toMatchObject({
      low: 100,
      high: 130,
    });
  });
});

describe('TrackGraph recording-time discontinuities', () => {
  const timestamps = [100, 200, 50, 150, 0, 100];
  const details = timestamps.map((seconds, pointIndex) =>
    chartPoint({
      pointIndex,
      pointTimestamp: new Date(Date.UTC(2026, 0, 1) + seconds * 1000),
      distanceInMeterSinceStart: pointIndex * 100,
      speedInKmhWindow: pointIndex === 2 ? null : 10,
      elevationGainPerHourWindow: pointIndex === 2 ? null : 100,
      energyCumulativeWh: pointIndex,
      metricStats: {
        ALTITUDE_M: { min: 100, max: 130, avg: 110 },
        SPEED_WINDOW_KMH: { min: 8, max: 12, avg: 10 },
        ELEVATION_GAIN_PER_HOUR_WINDOW: { min: 90, max: 120, avg: 100 },
      },
    })
  );
  type RenderedSeries = {
    type: string;
    data: Array<{ x: number; ts: number; canonicalPointIndex: number }>;
    enableMouseTracking?: boolean;
  };
  const seriesOf = (wrapper: ReturnType<typeof mount>) =>
    (wrapper.vm as unknown as { chartOptions: { series: RenderedSeries[] } }).chartOptions.series;

  it.each([
    ['elevation', trackGraphConfigs.elevation],
    ['speed with missing metrics', trackGraphConfigs.speed],
    ['gain with missing metrics', trackGraphConfigs.elevationGain],
    ['distance', trackGraphConfigs.distance],
    ['energy', trackGraphConfigs.energy],
  ])('keeps backwards time intervals disconnected for %s with range on and off', async (_name, graphConfig) => {
    const wrapper = await mountGraph(false, { graphConfig, showRange: true, trackDetails: details });
    for (const showRange of [true, false]) {
      await wrapper.setProps({ showRange });
      const series = seriesOf(wrapper);
      const lines = series.filter((entry) => entry.enableMouseTracking !== false);
      expect(lines).toHaveLength(3);
      expect(lines.map((entry) => entry.data.map((p) => p.canonicalPointIndex))).toEqual([
        [0, 1],
        graphConfig.filterNullY && graphConfig !== trackGraphConfigs.energy ? [3] : [2, 3],
        [4, 5],
      ]);
      for (const entry of series) {
        expect(entry.data.map((p) => p.x)).toEqual(entry.data.map((p) => p.x).toSorted((a, b) => a - b));
        for (const p of entry.data) {
          expect(p.ts).toBe(details[p.canonicalPointIndex].pointTimestamp.getTime());
        }
      }
      if (showRange && graphConfig.rangeMetricKey) {
        expect(
          series
            .filter((entry) => entry.type === 'arearange')
            .map((entry) => entry.data.map((p) => p.canonicalPointIndex))
        ).toEqual(lines.map((entry) => entry.data.map((p) => p.canonicalPointIndex)));
      }
    }

    await wrapper.setProps({ xMode: 'distance', showRange: true });
    expect(seriesOf(wrapper).filter((entry) => entry.enableMouseTracking !== false)).toHaveLength(1);
    await wrapper.setProps({ xMode: 'time' });
    expect(seriesOf(wrapper).filter((entry) => entry.enableMouseTracking !== false)).toHaveLength(3);
    await wrapper.setProps({ trackDetails: details.slice(0, 2) });
    expect(seriesOf(wrapper).filter((entry) => entry.enableMouseTracking !== false)).toHaveLength(1);
    wrapper.unmount();
  });

  it('preserves full points and map hover for a track above the boost threshold', async () => {
    const trackDetails = Array.from({ length: 5_001 }, (_, pointIndex) =>
      chartPoint({ pointIndex, pointTimestamp: new Date(Date.UTC(2026, 0, 1) + pointIndex * 1000) })
    );
    const wrapper = mount(TrackGraph, {
      props: { config: trackGraphConfigs.elevation, trackDetails, showRange: false, syncEnabled: false },
      global: { plugins: [[HighchartsVue, { highcharts: ApplicationCharts }]] },
    });
    await flushPromises();
    const chart = ApplicationCharts.charts.find(
      (item) => item?.renderTo === wrapper.get('[data-highcharts-chart]').element
    )!;
    const { useChartSync } =
      await vi.importActual<typeof import('@/composables/useChartSync')>('@/composables/useChartSync');
    const sync = useChartSync();
    vi.spyOn(chart.container, 'getBoundingClientRect').mockReturnValue({
      width: 600,
      height: 300,
      top: 0,
      left: 0,
      right: 600,
      bottom: 300,
    } as DOMRect);
    const refresh = vi.spyOn(chart.tooltip, 'refresh');
    sync.registerChart(chart);
    try {
      const series = chart.series[0]!;
      expect(series.options.boostThreshold).toBe(0);
      expect(series.points).toHaveLength(5_001);
      expect(series.points[2_500]).toMatchObject({ canonicalPointIndex: 2_500, setState: expect.any(Function) });
      expect((series.options as Highcharts.SeriesAreaOptions).fillColor).toHaveProperty('linearGradient');
      sync.showChartsAtTimestamp(2_500_000);
      expect(refresh).toHaveBeenCalledWith(series.points[2_500]);
    } finally {
      sync.clearChartCrosshairs();
      sync.unregisterChart(chart);
      wrapper.unmount();
    }
  });

  it('updates real Highcharts sections without leaving stale lines or range fills', async () => {
    const wrapper = mount(TrackGraph, {
      props: { config: trackGraphConfigs.elevation, trackDetails: details, showRange: true, syncEnabled: false },
      global: { plugins: [[HighchartsVue, { highcharts: ApplicationCharts }]] },
    });
    await flushPromises();
    const chart = ApplicationCharts.charts.find(
      (item) => item?.renderTo === wrapper.get('[data-highcharts-chart]').element
    )!;
    try {
      expect(chart.series.map((entry) => entry.type)).toEqual([
        'line',
        'arearange',
        'line',
        'arearange',
        'line',
        'arearange',
      ]);
      expect(chart.series.map((entry) => entry.points.length)).toEqual([2, 2, 2, 2, 2, 2]);
      for (const entry of chart.series) {
        expect(entry.points.map((p) => p.x)).toEqual(entry.points.map((p) => p.x).toSorted((a, b) => a - b));
      }
      await wrapper.setProps({ xMode: 'distance' });
      expect(chart.series.map((entry) => entry.points.length)).toEqual([6, 6]);
      await wrapper.setProps({ showRange: false, xMode: 'time' });
      expect(chart.series.map((entry) => entry.type)).toEqual(['area', 'area', 'area']);
      expect(chart.series.map((entry) => entry.points.length)).toEqual([2, 2, 2]);
      await wrapper.setProps({ trackDetails: details.slice(0, 2), showRange: true });
      expect(chart.series.map((entry) => entry.type)).toEqual(['line', 'arearange']);
      expect(chart.series.map((entry) => entry.points.length)).toEqual([2, 2]);
    } finally {
      wrapper.unmount();
    }
  });
});
