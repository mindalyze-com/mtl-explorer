import { enableAutoUnmount, flushPromises, mount, shallowMount } from '@vue/test-utils';
import { defineComponent } from 'vue';
import { createPinia, setActivePinia } from 'pinia';
import { useMediaFilterStore } from '@/stores/mediaFilterStore';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import PrimeVue from 'primevue/config';
import Column from 'primevue/column';
import DataTable from 'primevue/datatable';
import Statistics from '@/components/statistics/Statistics.vue';
import TrendChartSettings from '@/components/statistics/TrendChartSettings.vue';
import { STORAGE_KEYS } from '@/utils/appStorage';
import { useMeasurementSystem } from '@/composables/useMeasurementSystem';
import { useTheme, type ColorScheme } from '@/composables/useTheme';
import type Highcharts from 'highcharts';
import { getMediaTrends } from '@/repositories/mediaRepository';
import { fetchStatisticsForTrackIds, resolveStatisticsTrackIds } from '@/utils/ServiceHelper';

const filterRequest = { filterName: 'ActiveFilter', filterParams: undefined };

vi.mock('@/stores/filterStore', () => ({
  useFilterStore: () => ({
    trackSetRevision: 0,
    activeFilterRequest: filterRequest,
    getActiveFilterRequest: vi.fn(async () => filterRequest),
  }),
}));

vi.mock('@/utils/ServiceHelper', () => ({
  fetchStatisticsForTrackIds: vi.fn(),
  resolveStatisticsTrackIds: vi.fn(),
}));

vi.mock('@/repositories/mediaRepository', () => ({
  getMediaTrends: vi.fn(),
}));

enableAutoUnmount(afterEach);
afterEach(() => useMeasurementSystem().useDefaultMeasurementSystem());

const PassthroughStub = defineComponent({
  template: '<div><slot name="title" /><slot /></div>',
});

const HighchartsStub = defineComponent({
  name: 'Highcharts',
  props: { options: { type: Object, required: true } },
  template: '<div class="highcharts-stub" />',
});

const DataTableStub = defineComponent({
  name: 'DataTable',
  props: { value: { type: Array, default: () => [] } },
  template: '<div data-test="statistics-table-stub"><slot /></div>',
});

const ColumnStub = defineComponent({
  name: 'Column',
  props: { field: String, header: String },
  template: '<div class="column-stub"><slot name="header" /></div>',
});

const MediaTrendMosaicStub = defineComponent({
  name: 'MediaTrendMosaic',
  props: {
    modelValue: Boolean,
    bucket: Object,
    grouping: String,
    scope: String,
    trackIds: Array,
  },
  emits: ['update:modelValue', 'open-activity'],
  template: '<div v-if="modelValue" data-test="media-mosaic-stub">{{ bucket?.bucketKey }}</div>',
});

const StatisticsOverviewStub = defineComponent({
  name: 'StatisticsOverview',
  props: {
    indexedMediaCount: { type: Number, default: null },
    indexedPhotoCount: { type: Number, default: null },
    indexedVideoCount: { type: Number, default: null },
    retryRevision: { type: Number, default: 0 },
  },
  template:
    '<div data-test="statistics-overview-stub">{{ indexedMediaCount }}:{{ indexedPhotoCount }}:{{ indexedVideoCount }}</div>',
});

function mountStatistics({ realTable = false } = {}) {
  const wrapper = (realTable ? mount : shallowMount)(Statistics, {
    global: {
      plugins: realTable ? [PrimeVue] : [],
      components: realTable ? { Column, DataTable } : {},
      stubs: {
        BottomSheet: PassthroughStub,
        Column: realTable ? false : ColumnStub,
        DataTable: realTable ? false : DataTableStub,
        Popover: PassthroughStub,
        Select: true,
        StatisticsOverview: StatisticsOverviewStub,
        TabPanel: PassthroughStub,
        TabPanels: PassthroughStub,
        Tabs: PassthroughStub,
        TrackBrowserQuickViews: true,
        TrackBrowserView: true,
        Highcharts: HighchartsStub,
        MediaTrendMosaic: MediaTrendMosaicStub,
        MediaScopeControl: false,
      },
      directives: { tooltip: {} },
    },
  });
  (wrapper.vm as unknown as { active: boolean }).active = true;
  return wrapper;
}

describe('Statistics media trends', () => {
  const resolveStatisticsTrackIdsMock = vi.mocked(resolveStatisticsTrackIds);
  const fetchStatisticsForTrackIdsMock = vi.mocked(fetchStatisticsForTrackIds);
  const getMediaTrendsMock = vi.mocked(getMediaTrends);

  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
    localStorage.removeItem(STORAGE_KEYS.trendChartPreferences);
    resolveStatisticsTrackIdsMock.mockResolvedValue([7, 8]);
    fetchStatisticsForTrackIdsMock.mockResolvedValue([
      { groupBy: '2026-Q2', subGroup: 'Q2', numberOfTracks: 1, totalTrackDurationSecs: 1800 },
      { groupBy: '2026-Q3', subGroup: 'Q3', numberOfTracks: 2, totalTrackDurationSecs: 3600 },
    ]);
    getMediaTrendsMock.mockResolvedValue({
      scope: 'ALL_INDEXED',
      buckets: [
        { bucketKey: '2026-Q3', label: '2026-Q3', subGroup: 'Q3', imageCount: 4, videoCount: 2 },
        { bucketKey: 'UNDATED', label: 'Undated', undated: true, imageCount: 1, videoCount: 0 },
      ],
    });
  });

  it('shows an empty trend state without a calculation notice and recovers when categories are restored', async () => {
    resolveStatisticsTrackIdsMock.mockResolvedValue([]);
    fetchStatisticsForTrackIdsMock.mockResolvedValue([]);
    getMediaTrendsMock.mockResolvedValue({ scope: 'ALL_INDEXED', buckets: [] });
    const wrapper = mountStatistics();
    const statistics = wrapper.vm as unknown as {
      activeTab: string;
      fetchStatistics: () => Promise<void>;
    };
    statistics.activeTab = 'stats';
    await statistics.fetchStatistics();
    await flushPromises();

    expect(getMediaTrendsMock).toHaveBeenCalledWith(
      { grouping: 'QUARTER', scope: 'ALL_INDEXED', trackIds: [] },
      expect.any(AbortSignal)
    );
    expect(wrapper.text()).toContain('No media or activity periods available.');
    expect(wrapper.text()).not.toContain('being calculated');
    expect(wrapper.find('[data-chart-id="exploration"]').exists()).toBe(false);
    expect(wrapper.find('.bi-hourglass-split').exists()).toBe(false);

    resolveStatisticsTrackIdsMock.mockResolvedValue([7, 8]);
    fetchStatisticsForTrackIdsMock.mockResolvedValue([
      { groupBy: '2026-Q3', subGroup: 'Q3', numberOfTracks: 2, explorationScoreAvg: 0.25 },
    ]);
    getMediaTrendsMock.mockResolvedValue({
      scope: 'ALL_INDEXED',
      buckets: [{ bucketKey: '2026-Q3', label: '2026-Q3', imageCount: 2, videoCount: 0 }],
    });
    await statistics.fetchStatistics();
    await flushPromises();
    const recoveredExploration = wrapper.get('[data-chart-id="exploration"]');
    expect(recoveredExploration.findComponent(HighchartsStub).props('options').series[0].data).toEqual([25]);
    expect(wrapper.text()).not.toContain('No media or activity periods available.');
    expect(
      wrapper.get('[data-chart-id="media"]').findComponent(HighchartsStub).props('options').series[0].data
    ).toEqual([2]);
  });

  it('keeps the exploration pending notice for matching activities without calculated scores', async () => {
    const wrapper = mountStatistics();
    const statistics = wrapper.vm as unknown as {
      activeTab: string;
      fetchStatistics: () => Promise<void>;
    };
    statistics.activeTab = 'stats';
    await statistics.fetchStatistics();
    await flushPromises();

    const exploration = wrapper.get('[data-chart-id="exploration"]');
    expect(exploration.text()).toContain('Exploration data is being calculated in the background.');
    expect(exploration.find('.bi-hourglass-split').exists()).toBe(true);
    expect(exploration.text()).not.toContain('No tracks match');
  });

  it.each<ColorScheme>(['light', 'dark'])(
    'recolors visible and hidden charts after leaving %s without refetching or changing data',
    async (initialTheme) => {
      const style = document.createElement('style');
      style.textContent = `
        :root { --chart-text: #64748b; --chart-grid: #eeeeee; --chart-tooltip-bg: #ffffff;
          --chart-tooltip-text: #334155; --border-default: #dddddd; --chart-series-1: #6366f1; }
        :root[data-theme="dark"] { --chart-text: #94a3b8; --chart-grid: #222222;
          --chart-tooltip-bg: #0f172a; --chart-tooltip-text: #e2e8f0; --border-default: #444444;
          --chart-series-1: #818cf8; }
      `;
      document.head.append(style);
      const theme = useTheme();
      theme.setScheme(initialTheme);
      await flushPromises();
      fetchStatisticsForTrackIdsMock.mockResolvedValue([
        {
          groupBy: '2026-Q3',
          subGroup: 'Q3',
          numberOfTracks: 2,
          totalTrackDurationSecs: 3600,
          energyNetTotalWhSum: 120,
          normalizedPowerMed: 100,
          intensityIndexAvg: 1.1,
          trainingLoadPerRideAvg: 40,
          explorationScoreAvg: 20,
        },
      ]);
      const wrapper = mountStatistics();
      const vm = wrapper.vm as unknown as {
        activeTab: string;
        statsView: string;
        fetchStatistics: () => Promise<void>;
      };
      try {
        vm.activeTab = 'stats';
        vm.statsView = 'charts';
        await vm.fetchStatistics();
        await flushPromises();
        const options = () => wrapper.findAllComponents(HighchartsStub).map((chart) => chart.props('options'));
        const content = () =>
          options().map((chart) => ({
            height: chart.chart.height,
            categories: [...chart.xAxis.categories],
            series: chart.series.map((series: { name: string; data: unknown[] }) => ({
              name: series.name,
              data: [...series.data],
            })),
            tooltip: chart.tooltip.formatter.call({ category: '2026-Q3', y: 2, series: { name: 'Test' } }),
          }));
        const baseline = content();
        expect(baseline).toHaveLength(8);
        const requestCount = fetchStatisticsForTrackIdsMock.mock.calls.length;
        const mediaRequestCount = getMediaTrendsMock.mock.calls.length;
        const checkTheme = () => {
          const tokens = getComputedStyle(document.documentElement);
          for (const chart of options()) {
            expect(chart.xAxis.labels.style.color).toBe(tokens.getPropertyValue('--chart-text').trim());
            expect(chart.yAxis.gridLineColor).toBe(tokens.getPropertyValue('--chart-grid').trim());
            expect(chart.tooltip.backgroundColor).toBe(tokens.getPropertyValue('--chart-tooltip-bg').trim());
            expect(chart.tooltip.style.color).toBe(tokens.getPropertyValue('--chart-tooltip-text').trim());
          }
          const gradient = options()[0].plotOptions.column.color as Highcharts.GradientColorObject;
          expect(gradient.stops[0][1]).toBe(theme.isDark.value ? 'rgba(129,140,248,0.85)' : 'rgba(99,102,241,0.85)');
          expect(content()).toEqual(baseline);
        };

        theme.setScheme(initialTheme === 'light' ? 'dark' : 'light');
        await flushPromises();
        checkTheme();
        vm.statsView = 'table';
        await flushPromises();
        theme.setScheme(initialTheme);
        await flushPromises();
        vm.statsView = 'charts';
        await flushPromises();
        checkTheme();
        expect(fetchStatisticsForTrackIdsMock).toHaveBeenCalledTimes(requestCount);
        expect(getMediaTrendsMock).toHaveBeenCalledTimes(mediaRequestCount);
      } finally {
        wrapper.unmount();
        style.remove();
        theme.setScheme('light');
        await flushPromises();
      }
    }
  );

  it('shows and sorts period averages using the server means when medians differ', async () => {
    useMeasurementSystem().setMeasurementSystem('METRIC');
    fetchStatisticsForTrackIdsMock.mockResolvedValueOnce([
      {
        groupBy: '2026-Q1',
        subGroup: 'Q1',
        numberOfTracks: 3,
        totalTrackDurationSecs: 45_000,
        trackDurationSecsAvg: 15_000,
        trackDurationSecsMed: 7_200,
        trackLengthInMeterSum: 90_000,
        trackLengthInMeterAvg: 30_000,
        trackLengthInMeterMed: 10_000,
      },
      {
        groupBy: '2026-Q2',
        subGroup: 'Q2',
        numberOfTracks: 3,
        totalTrackDurationSecs: 36_000,
        trackDurationSecsAvg: 12_000,
        trackDurationSecsMed: 10_800,
        trackLengthInMeterSum: 60_000,
        trackLengthInMeterAvg: 20_000,
        trackLengthInMeterMed: 15_000,
      },
    ]);
    getMediaTrendsMock.mockResolvedValueOnce({ scope: 'ALL_INDEXED', buckets: [] });
    const wrapper = mountStatistics({ realTable: true });
    const vm = wrapper.vm as unknown as {
      activeTab: string;
      statsView: string;
      fetchStatistics: () => Promise<void>;
    };
    vm.activeTab = 'stats';
    vm.statsView = 'table';
    await vm.fetchStatistics();
    await flushPromises();

    const headers = wrapper.findAll('th');
    const period = headers.find((header) => header.text() === 'Period')!;
    expect(period.attributes('data-p-frozen-column')).toBe('true');
    expect(
      wrapper.findAll('tbody tr').every((row) => row.findAll('td')[0].attributes('data-p-frozen-column') === 'true')
    ).toBe(true);
    await period.trigger('click');
    expect(wrapper.findAll('tbody tr').map((row) => row.findAll('td')[0].text())).toEqual(['2026-Q1', '2026-Q2']);
    const distanceIndex = headers.findIndex((header) => header.text() === 'Avg Dist.');
    const durationIndex = headers.findIndex((header) => header.text() === 'Avg Duration');
    expect(distanceIndex).toBeGreaterThan(-1);
    expect(durationIndex).toBeGreaterThan(-1);
    const firstRow = wrapper.findAll('tbody tr')[0].findAll('td');
    expect(firstRow[distanceIndex].text()).toBe('30.0 km');
    expect(firstRow[durationIndex].text()).toBe('4h 10m');

    await headers[distanceIndex].trigger('click');
    expect(wrapper.findAll('tbody tr').map((row) => row.findAll('td')[0].text())).toEqual(['2026-Q2', '2026-Q1']);
    await headers[durationIndex].trigger('click');
    expect(wrapper.findAll('tbody tr').map((row) => row.findAll('td')[0].text())).toEqual(['2026-Q2', '2026-Q1']);
  });

  it('preserves short average durations when period totals span multiple days', async () => {
    fetchStatisticsForTrackIdsMock.mockResolvedValueOnce([
      {
        groupBy: '2026-Q1',
        numberOfTracks: 234,
        totalTrackDurationSecs: 154_440,
        trackDurationSecsAvg: 660,
      },
      {
        groupBy: '2026-Q2',
        numberOfTracks: 34,
        totalTrackDurationSecs: 22_440,
        trackDurationSecsAvg: 660,
      },
    ]);
    getMediaTrendsMock.mockResolvedValueOnce({ scope: 'ALL_INDEXED', buckets: [] });
    const wrapper = mountStatistics({ realTable: true });
    const vm = wrapper.vm as unknown as {
      activeTab: string;
      statsView: string;
      fetchStatistics: () => Promise<void>;
    };
    vm.activeTab = 'stats';
    vm.statsView = 'table';
    await vm.fetchStatistics();
    await flushPromises();

    const headers = wrapper.findAll('th');
    const durationIndex = headers.findIndex((header) => header.text() === 'Duration');
    const averageIndex = headers.findIndex((header) => header.text() === 'Avg Duration');
    const rows = wrapper.findAll('tbody tr').map((row) => row.findAll('td'));
    expect(rows.map((cells) => cells[durationIndex].text())).toEqual(['1d 18h', '0d 06h']);
    expect(rows.map((cells) => cells[averageIndex].text())).toEqual(['11m 00s', '11m 00s']);
  });

  it('applies chart height and DOM order without fetching and restores them after remounting', async () => {
    const wrapper = mountStatistics();
    (wrapper.vm as unknown as { activeTab: string }).activeTab = 'stats';
    await (wrapper.vm as unknown as { fetchStatistics: () => Promise<void> }).fetchStatistics();
    await flushPromises();
    const settings = wrapper.getComponent(TrendChartSettings);
    const fetchCount = fetchStatisticsForTrackIdsMock.mock.calls.length;
    settings.vm.$emit('height', 360);
    settings.vm.$emit('move', 'distance', -1);
    await flushPromises();
    expect(
      wrapper
        .findAll('.chart-card')
        .map((card) => card.attributes('data-chart-id'))
        .slice(0, 2)
    ).toEqual(['distance', 'duration']);
    expect(
      wrapper.findAllComponents(HighchartsStub).every((chart) => chart.props('options').chart.height === 360)
    ).toBe(true);
    expect(fetchStatisticsForTrackIdsMock).toHaveBeenCalledTimes(fetchCount);

    (wrapper.vm as unknown as { statsView: string }).statsView = 'table';
    await flushPromises();
    expect(wrapper.findComponent(TrendChartSettings).exists()).toBe(false);
    (wrapper.vm as unknown as { statsView: string }).statsView = 'charts';
    await flushPromises();
    expect(
      wrapper.findAllComponents(HighchartsStub).every((chart) => chart.props('options').chart.height === 360)
    ).toBe(true);
    wrapper.unmount();

    const restored = mountStatistics();
    (restored.vm as unknown as { activeTab: string }).activeTab = 'stats';
    await (restored.vm as unknown as { fetchStatistics: () => Promise<void> }).fetchStatistics();
    await flushPromises();
    expect(restored.getComponent(TrendChartSettings).props('height')).toBe(360);
    expect(restored.findAll('.chart-card')[0].attributes('data-chart-id')).toBe('distance');
    restored.getComponent(TrendChartSettings).vm.$emit('reset');
    await flushPromises();
    expect(restored.getComponent(TrendChartSettings).props('isDefault')).toBe(true);
    expect(restored.findAll('.chart-card')[0].attributes('data-chart-id')).toBe('duration');
  });

  it('defaults to All media with visible scope help and zero-fills the common chart timeline', async () => {
    const wrapper = mountStatistics();
    await (wrapper.vm as unknown as { fetchStatistics: () => Promise<void> }).fetchStatistics();
    await flushPromises();

    expect(resolveStatisticsTrackIdsMock).toHaveBeenCalledWith(filterRequest, expect.any(AbortSignal));
    expect(fetchStatisticsForTrackIdsMock).toHaveBeenCalledWith('YYYY-"Q"Q', [7, 8], expect.any(AbortSignal));
    expect(getMediaTrendsMock).toHaveBeenCalledWith(
      { grouping: 'QUARTER', scope: 'ALL_INDEXED', trackIds: [7, 8] },
      expect.any(AbortSignal)
    );
    expect(
      wrapper
        .findAll('.media-scope-control button')
        .find((button) => button.text() === 'All media')
        ?.attributes()
    ).toMatchObject({ 'aria-pressed': 'true' });

    const charts = wrapper.findAllComponents(HighchartsStub);
    const mediaChart = charts.find((chart) => {
      const series = chart.props('options')?.series;
      return series?.[0]?.name === 'Photos';
    });
    const durationChart = charts.find((chart) => chart.props('options')?.series?.[0]?.name === 'Duration');
    expect(mediaChart).toBeDefined();
    expect(mediaChart!.props('options').plotOptions.column.stacking).toBe('normal');
    expect(mediaChart!.props('options').xAxis.categories).toEqual(['2026-Q2', '2026-Q3']);
    expect(durationChart!.props('options').xAxis.categories).toEqual(mediaChart!.props('options').xAxis.categories);
    expect(mediaChart!.props('options').series.map((series: { data: number[] }) => series.data)).toEqual([
      [0, 4],
      [0, 2],
    ]);
    expect(wrapper.findAll('.media-scope-control button').map((button) => button.text())).toEqual([
      'All media',
      'Track related',
    ]);
    expect(wrapper.text()).toContain('Includes media without a track link.');
    expect(wrapper.find('.media-trend-undated').exists()).toBe(false);
    expect(wrapper.get('[data-test="statistics-overview-stub"]').text()).toBe('6:4:2');
  });

  it('retains every activity chart with zero values for a media-only sub-unit', async () => {
    fetchStatisticsForTrackIdsMock.mockResolvedValueOnce([
      {
        groupBy: '2026-Q1',
        subGroup: 'Q1',
        numberOfTracks: 1,
        totalTrackDurationSecs: 1800,
        trackLengthInMeterSum: 10_000,
        energyNetTotalWhSum: 400,
        normalizedPowerMed: 180,
        intensityIndexAvg: 0.8,
        trainingLoadPerRideAvg: 45,
        explorationScoreAvg: 0.5,
      },
    ]);
    getMediaTrendsMock.mockResolvedValueOnce({
      scope: 'ALL_INDEXED',
      buckets: [{ bucketKey: '2026-Q3', label: '2026-Q3', subGroup: 'Q3', imageCount: 4, videoCount: 2 }],
    });
    const wrapper = mountStatistics();
    await (wrapper.vm as unknown as { fetchStatistics: () => Promise<void> }).fetchStatistics();
    await flushPromises();

    (wrapper.vm as unknown as { selectedSubUnit: string | null }).selectedSubUnit = 'Q3';
    await flushPromises();

    expect(wrapper.findAll('.chart-card').map((card) => card.text())).toEqual([
      'Duration',
      'Distance',
      'Activity',
      'Energy',
      'Intensity Index',
      'Training Load',
      'Exploration',
      expect.stringContaining('Media'),
    ]);
    const charts = wrapper.findAllComponents(HighchartsStub);
    expect(charts).toHaveLength(8);
    for (const chart of charts) {
      expect(chart.props('options').xAxis.categories).toEqual(['2026-Q3']);
    }
    for (const chart of charts.filter((candidate) => candidate.props('options').series[0].name !== 'Photos')) {
      expect(chart.props('options').series[0].data).toEqual([0]);
    }
  });

  it('shows zero activity counts in media-only table periods and preserves matching activity counts', async () => {
    fetchStatisticsForTrackIdsMock.mockResolvedValueOnce([
      { groupBy: '2026-Q2', subGroup: 'Q2', daysWithActivities: 2, numberOfTracks: 3 },
    ]);
    getMediaTrendsMock.mockResolvedValueOnce({
      scope: 'ALL_INDEXED',
      buckets: [
        { bucketKey: '2026-Q2', label: '2026-Q2', subGroup: 'Q2', imageCount: 4, videoCount: 0 },
        { bucketKey: '2026-Q3', label: '2026-Q3', subGroup: 'Q3', imageCount: 1, videoCount: 0 },
      ],
    });
    const wrapper = mountStatistics({ realTable: true });
    const statistics = wrapper.vm as unknown as {
      activeTab: string;
      statsView: string;
      selectedSubUnit: string | null;
      fetchStatistics: () => Promise<void>;
    };
    statistics.activeTab = 'stats';
    statistics.statsView = 'table';
    await statistics.fetchStatistics();
    await flushPromises();

    const counts = () =>
      wrapper.findAll('tbody tr').map((row) =>
        row
          .findAll('td')
          .slice(0, 5)
          .map((cell) => cell.text())
      );
    expect(counts()).toEqual([
      ['2026-Q2', '2', '3', '4', '0'],
      ['2026-Q3', '0', '0', '1', '0'],
    ]);

    statistics.selectedSubUnit = 'Q3';
    await flushPromises();
    expect(counts()).toEqual([['2026-Q3', '0', '0', '1', '0']]);
  });

  it('uses the server-filtered media range for the shared timeline, totals and table', async () => {
    getMediaTrendsMock.mockResolvedValueOnce({
      scope: 'ALL_INDEXED',
      buckets: [
        { bucketKey: '2026-Q3', label: '2026-Q3', subGroup: 'Q3', imageCount: 4, videoCount: 2 },
        { bucketKey: 'UNDATED', label: 'Undated', undated: true, imageCount: 1, videoCount: 0 },
      ],
    });
    const wrapper = mountStatistics();
    await (wrapper.vm as unknown as { fetchStatistics: () => Promise<void> }).fetchStatistics();
    await flushPromises();

    const mediaChart = wrapper.findAllComponents(HighchartsStub).find((chart) => {
      return chart.props('options')?.series?.[0]?.name === 'Photos';
    });
    const durationChart = wrapper.findAllComponents(HighchartsStub).find((chart) => {
      return chart.props('options')?.series?.[0]?.name === 'Duration';
    });
    expect(mediaChart!.props('options').xAxis.categories).toEqual(['2026-Q2', '2026-Q3']);
    expect(durationChart!.props('options').xAxis.categories).toEqual(mediaChart!.props('options').xAxis.categories);
    expect(durationChart!.props('options').series[0].data).toEqual([0.5, 1]);
    expect(wrapper.get('[data-test="statistics-overview-stub"]').text()).toBe('6:4:2');

    (wrapper.vm as unknown as { statsView: 'table' | 'charts' }).statsView = 'table';
    await flushPromises();
    const tableRows = wrapper.findComponent(DataTableStub).props('value') as Array<{
      groupBy?: string;
      imageCount: number;
      videoCount: number;
    }>;
    expect(tableRows.map((row) => [row.groupBy, row.imageCount, row.videoCount])).toEqual([
      ['2026-Q2', 0, 0],
      ['2026-Q3', 4, 2],
    ]);
    expect(wrapper.findAllComponents(ColumnStub).map((column) => column.props('field'))).toEqual(
      expect.arrayContaining(['imageCount', 'videoCount'])
    );

    expect(getMediaTrendsMock).toHaveBeenCalledTimes(1);
  });

  it('uses the same ISO week key for activity and media periods', async () => {
    fetchStatisticsForTrackIdsMock.mockResolvedValueOnce([
      { groupBy: '2027-W01', subGroup: 'W01', numberOfTracks: 1, totalTrackDurationSecs: 1800 },
    ]);
    getMediaTrendsMock.mockResolvedValueOnce({
      scope: 'ALL_INDEXED',
      buckets: [{ bucketKey: '2027-W01', label: '2027-W01', subGroup: 'W01', imageCount: 2, videoCount: 1 }],
    });
    const wrapper = mountStatistics();
    (wrapper.vm as unknown as { selectedGrouping: string }).selectedGrouping = 'IYYY-"W"IW';

    await (wrapper.vm as unknown as { fetchStatistics: () => Promise<void> }).fetchStatistics();
    await flushPromises();

    expect(fetchStatisticsForTrackIdsMock).toHaveBeenCalledWith('IYYY-"W"IW', [7, 8], expect.any(AbortSignal));
    expect(getMediaTrendsMock).toHaveBeenCalledWith(
      { grouping: 'WEEK', scope: 'ALL_INDEXED', trackIds: [7, 8] },
      expect.any(AbortSignal)
    );
    const mediaChart = wrapper.findAllComponents(HighchartsStub).find((chart) => {
      return chart.props('options')?.series?.[0]?.name === 'Photos';
    });
    expect(mediaChart!.props('options').xAxis.categories).toEqual(['2027-W01']);
    expect(mediaChart!.props('options').series.map((series: { data: number[] }) => series.data)).toEqual([[2], [1]]);
  });

  it('switches to Track related media, explains the scope, and sends track IDs', async () => {
    const wrapper = mountStatistics();
    await (wrapper.vm as unknown as { fetchStatistics: () => Promise<void> }).fetchStatistics();
    await flushPromises();
    getMediaTrendsMock.mockResolvedValueOnce({ scope: 'MATCHED_ACTIVITIES', buckets: [] });

    const trackRelatedButton = wrapper
      .findAll('.media-scope-control button')
      .find((button) => button.text() === 'Track related');
    expect(trackRelatedButton).toBeDefined();
    await trackRelatedButton!.trigger('click');
    await flushPromises();

    expect(getMediaTrendsMock).toHaveBeenLastCalledWith(
      { grouping: 'QUARTER', scope: 'MATCHED_ACTIVITIES', trackIds: [7, 8] },
      expect.any(AbortSignal)
    );
    expect(wrapper.text()).toContain('Only media linked to matching tracks.');
    expect(wrapper.findComponent(MediaTrendMosaicStub).props('scope')).toBe('MATCHED_ACTIVITIES');
    expect(wrapper.get('[data-test="statistics-overview-stub"]').text()).toBe('0:0:0');

    getMediaTrendsMock.mockResolvedValueOnce({ scope: 'ALL_INDEXED', buckets: [] });
    const allIndexedButton = wrapper
      .findAll('.media-scope-control button')
      .find((button) => button.text() === 'All media');
    await allIndexedButton!.trigger('click');
    await flushPromises();

    expect(getMediaTrendsMock).toHaveBeenLastCalledWith(
      { grouping: 'QUARTER', scope: 'ALL_INDEXED', trackIds: [7, 8] },
      expect.any(AbortSignal)
    );
    expect(wrapper.findComponent(MediaTrendMosaicStub).props('scope')).toBe('ALL_INDEXED');
    expect(wrapper.text()).toContain('Includes media without a track link.');
  });

  it('makes the range explanation visible and accessible at the top of Trends', async () => {
    const wrapper = mountStatistics();
    await flushPromises();
    const control = wrapper.get('.media-scope-control');
    expect(control.text()).toContain('From the first matching track’s start to the last matching track’s end.');
    expect(control.text()).toContain('Undated media is excluded.');
    for (const button of control.findAll('button')) {
      expect(button.attributes('aria-describedby')).toBe(control.get('p').attributes('id'));
    }
    expect(wrapper.html().indexOf('media-scope-control')).toBeLessThan(wrapper.html().indexOf('chart-card'));
  });

  it('updates totals when the shared map media choice changes and preserves it when opening Trends', async () => {
    const wrapper = mountStatistics();
    await (wrapper.vm as unknown as { fetchStatistics: () => Promise<void> }).fetchStatistics();
    getMediaTrendsMock.mockResolvedValueOnce({ scope: 'MATCHED_ACTIVITIES', buckets: [] });
    useMediaFilterStore().scope = 'MATCHED_ACTIVITIES';
    await flushPromises();
    expect(wrapper.get('[data-test="statistics-overview-stub"]').text()).toBe('0:0:0');
    await (wrapper.vm as unknown as { showMediaTrends: () => Promise<void> }).showMediaTrends();
    expect(useMediaFilterStore().scope).toBe('MATCHED_ACTIVITIES');
  });

  it('keeps Media last and opens dated media without opening an empty period', async () => {
    const wrapper = mountStatistics();
    await (wrapper.vm as unknown as { fetchStatistics: () => Promise<void> }).fetchStatistics();
    await flushPromises();

    expect(wrapper.findAll('.chart-card').at(-1)?.text()).toContain('Media');

    const mediaChart = wrapper.findAllComponents(HighchartsStub).find((chart) => {
      return chart.props('options')?.series?.[0]?.name === 'Photos';
    });
    const click = mediaChart!.props('options').plotOptions.series.point.events.click;
    click.call({ index: 0 });
    await flushPromises();
    expect(wrapper.find('[data-test="media-mosaic-stub"]').exists()).toBe(false);

    click.call({ index: 1 });
    await flushPromises();

    expect(wrapper.get('[data-test="media-mosaic-stub"]').text()).toBe('2026-Q3');
    expect(wrapper.findComponent(MediaTrendMosaicStub).props('trackIds')).toEqual([7, 8]);

    expect(wrapper.find('.media-trend-undated').exists()).toBe(false);
  });

  it('ignores a stale media response after the scope changes', async () => {
    let resolveFirstRequest:
      ((value: { scope: 'ALL_INDEXED'; buckets: Array<Record<string, unknown>> }) => void) | undefined;
    getMediaTrendsMock
      .mockReset()
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveFirstRequest = resolve;
          })
      )
      .mockResolvedValueOnce({
        scope: 'MATCHED_ACTIVITIES',
        buckets: [{ bucketKey: '2026-Q3', label: 'Current', imageCount: 9, videoCount: 1 }],
      });
    const wrapper = mountStatistics();
    const firstFetch = (wrapper.vm as unknown as { fetchStatistics: () => Promise<void> }).fetchStatistics();
    await flushPromises();

    const trackRelatedButton = wrapper
      .findAll('.media-scope-control button')
      .find((button) => button.text() === 'Track related');
    await trackRelatedButton!.trigger('click');
    await flushPromises();

    resolveFirstRequest?.({
      scope: 'ALL_INDEXED',
      buckets: [{ bucketKey: '2026-Q3', label: 'Stale', imageCount: 99, videoCount: 99 }],
    });
    await firstFetch;
    await flushPromises();

    const mediaChart = wrapper.findAllComponents(HighchartsStub).find((chart) => {
      return chart.props('options')?.series?.[0]?.name === 'Photos';
    });
    expect(mediaChart!.props('options').xAxis.categories).toEqual(['2026-Q2', 'Current']);
    expect(mediaChart!.props('options').series.map((series: { data: number[] }) => series.data)).toEqual([
      [0, 9],
      [0, 1],
    ]);
  });

  it('keeps saved statistics visible and offers Retry after a refresh fails', async () => {
    const wrapper = mountStatistics();
    const statistics = wrapper.vm as unknown as {
      fetchStatistics: () => Promise<void>;
      statisticData: Array<Record<string, unknown>>;
    };
    await statistics.fetchStatistics();
    await flushPromises();
    const savedStatistics = [...statistics.statisticData];
    (wrapper.vm as unknown as { activeTab: 'overview' | 'stats' | 'tracks' }).activeTab = 'tracks';

    fetchStatisticsForTrackIdsMock.mockRejectedValueOnce(new Error('offline'));
    await statistics.fetchStatistics();
    await flushPromises();

    expect(statistics.statisticData).toEqual(savedStatistics);
    const warning = wrapper.get('.statistics-root > [data-test="statistics-refresh-error"]');
    expect(warning.text()).toContain('Statistics could not be refreshed. Showing saved data.');
    expect(warning.get('button').text()).toBe('Retry');

    fetchStatisticsForTrackIdsMock.mockResolvedValueOnce([
      { groupBy: '2026-Q4', subGroup: 'Q4', numberOfTracks: 3, totalTrackDurationSecs: 5400 },
    ]);
    await warning.get('button').trigger('click');
    await flushPromises();

    expect(wrapper.find('[data-test="statistics-refresh-error"]').exists()).toBe(false);
    expect(statistics.statisticData).toEqual([expect.objectContaining({ groupBy: '2026-Q4', numberOfTracks: 3 })]);
  });

  it('signals the overview when Retry reloads after an initial connectivity failure', async () => {
    fetchStatisticsForTrackIdsMock.mockRejectedValueOnce(new Error('offline'));
    const wrapper = mountStatistics();

    await (wrapper.vm as unknown as { fetchStatistics: () => Promise<void> }).fetchStatistics();
    await flushPromises();

    const warning = wrapper.get('.statistics-root > [data-test="statistics-refresh-error"]');
    expect(warning.text()).toContain('Statistics could not be loaded.');
    expect(wrapper.findComponent(StatisticsOverviewStub).props('retryRevision')).toBe(0);

    fetchStatisticsForTrackIdsMock.mockResolvedValueOnce([]);
    await warning.get('button').trigger('click');
    await flushPromises();

    expect(wrapper.find('[data-test="statistics-refresh-error"]').exists()).toBe(false);
    expect(wrapper.findComponent(StatisticsOverviewStub).props('retryRevision')).toBe(1);
  });

  it('recognizes an empty successful result as saved data after a later refresh fails', async () => {
    fetchStatisticsForTrackIdsMock.mockResolvedValueOnce([]);
    const wrapper = mountStatistics();
    const statistics = wrapper.vm as unknown as { fetchStatistics: () => Promise<void> };

    await statistics.fetchStatistics();
    await flushPromises();

    fetchStatisticsForTrackIdsMock.mockRejectedValueOnce(new Error('offline'));
    await statistics.fetchStatistics();
    await flushPromises();

    expect(wrapper.get('[data-test="statistics-refresh-error"]').text()).toContain(
      'Statistics could not be refreshed. Showing saved data.'
    );
  });
});
