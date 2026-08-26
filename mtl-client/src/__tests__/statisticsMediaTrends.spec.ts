import { enableAutoUnmount, flushPromises, shallowMount } from '@vue/test-utils';
import { defineComponent } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import Statistics from '@/components/statistics/Statistics.vue';
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

function mountStatistics() {
  const wrapper = shallowMount(Statistics, {
    global: {
      stubs: {
        BottomSheet: PassthroughStub,
        Column: ColumnStub,
        DataTable: DataTableStub,
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
    vi.clearAllMocks();
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

  it('defaults to All indexed with visible scope help and zero-fills the common chart timeline', async () => {
    const wrapper = mountStatistics();
    await (wrapper.vm as unknown as { fetchStatistics: () => Promise<void> }).fetchStatistics();
    await flushPromises();

    expect(resolveStatisticsTrackIdsMock).toHaveBeenCalledWith(filterRequest, expect.any(AbortSignal));
    expect(fetchStatisticsForTrackIdsMock).toHaveBeenCalledWith('YYYY-"Q"Q', [7, 8], expect.any(AbortSignal));
    expect(getMediaTrendsMock).toHaveBeenCalledWith(
      { grouping: 'QUARTER', scope: 'ALL_INDEXED', trackIds: undefined },
      expect.any(AbortSignal)
    );
    expect(
      wrapper
        .findAll('.media-trend-mode button')
        .find((button) => button.text() === 'All indexed')
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
    expect(wrapper.findAll('.media-trend-mode button').map((button) => button.text())).toEqual([
      'All indexed',
      'Track related',
    ]);
    expect(wrapper.text()).toContain('Activity filters do not reduce the media totals.');
    expect(wrapper.get('.media-trend-undated').text()).toContain('Undated media1');
    expect(wrapper.get('[data-test="statistics-overview-stub"]').text()).toBe('7:5:2');
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

  it('keeps the full indexed-media history on the shared timeline and in totals', async () => {
    getMediaTrendsMock.mockResolvedValueOnce({
      scope: 'ALL_INDEXED',
      buckets: [
        { bucketKey: '1920-Q1', label: '1920-Q1', subGroup: 'Q1', imageCount: 3, videoCount: 0 },
        { bucketKey: '2026-Q3', label: '2026-Q3', subGroup: 'Q3', imageCount: 4, videoCount: 2 },
        { bucketKey: '9999-Q4', label: '9999-Q4', subGroup: 'Q4', imageCount: 1, videoCount: 4 },
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
    expect(mediaChart!.props('options').xAxis.categories).toEqual(['1920-Q1', '2026-Q2', '2026-Q3', '9999-Q4']);
    expect(durationChart!.props('options').xAxis.categories).toEqual(mediaChart!.props('options').xAxis.categories);
    expect(durationChart!.props('options').series[0].data).toEqual([0, 0.5, 1, 0]);
    expect(wrapper.get('[data-test="statistics-overview-stub"]').text()).toBe('15:9:6');

    (wrapper.vm as unknown as { statsView: 'table' | 'charts' }).statsView = 'table';
    await flushPromises();
    const tableRows = wrapper.findComponent(DataTableStub).props('value') as Array<{
      groupBy?: string;
      imageCount: number;
      videoCount: number;
    }>;
    expect(tableRows.map((row) => [row.groupBy, row.imageCount, row.videoCount])).toEqual([
      ['1920-Q1', 3, 0],
      ['2026-Q2', 0, 0],
      ['2026-Q3', 4, 2],
      ['9999-Q4', 1, 4],
      ['Undated', 1, 0],
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
      { grouping: 'WEEK', scope: 'ALL_INDEXED', trackIds: undefined },
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
      .findAll('.media-trend-mode button')
      .find((button) => button.text() === 'Track related');
    expect(trackRelatedButton).toBeDefined();
    await trackRelatedButton!.trigger('click');
    await flushPromises();

    expect(getMediaTrendsMock).toHaveBeenLastCalledWith(
      { grouping: 'QUARTER', scope: 'MATCHED_ACTIVITIES', trackIds: [7, 8] },
      expect.any(AbortSignal)
    );
    expect(wrapper.text()).toContain('Only media linked to activities in the current track filters is shown.');
    expect(wrapper.findComponent(MediaTrendMosaicStub).props('scope')).toBe('MATCHED_ACTIVITIES');
    expect(wrapper.get('[data-test="statistics-overview-stub"]').text()).toBe('7:5:2');

    getMediaTrendsMock.mockResolvedValueOnce({ scope: 'ALL_INDEXED', buckets: [] });
    const allIndexedButton = wrapper
      .findAll('.media-trend-mode button')
      .find((button) => button.text() === 'All indexed');
    await allIndexedButton!.trigger('click');
    await flushPromises();

    expect(getMediaTrendsMock).toHaveBeenLastCalledWith(
      { grouping: 'QUARTER', scope: 'ALL_INDEXED', trackIds: undefined },
      expect.any(AbortSignal)
    );
    expect(wrapper.findComponent(MediaTrendMosaicStub).props('scope')).toBe('ALL_INDEXED');
    expect(wrapper.text()).toContain('Activity filters do not reduce the media totals.');
  });

  it('shows the media timeline explanation on keyboard focus', async () => {
    const wrapper = mountStatistics();
    await (wrapper.vm as unknown as { fetchStatistics: () => Promise<void> }).fetchStatistics();
    await flushPromises();

    const allIndexedButton = wrapper
      .findAll('.media-trend-mode button')
      .find((button) => button.text() === 'All indexed');
    await allIndexedButton!.trigger('focus');

    const allIndexedTooltip = wrapper.get('[role="tooltip"]');
    expect(allIndexedButton!.attributes('aria-describedby')).toBe(allIndexedTooltip.attributes('id'));
    expect(allIndexedTooltip.text()).toBe(
      'Every indexed photo and video is shown. Activity filters do not reduce the media totals.'
    );

    await allIndexedButton!.trigger('blur');
    expect(wrapper.find('[role="tooltip"]').exists()).toBe(false);

    const trackRelatedButton = wrapper
      .findAll('.media-trend-mode button')
      .find((button) => button.text() === 'Track related');
    await trackRelatedButton!.trigger('focus');

    expect(wrapper.get('[role="tooltip"]').text()).toBe(
      'Only media linked to activities in the current track filters is shown.'
    );
  });

  it('keeps Media last and opens dated or undated media without opening an empty period', async () => {
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

    await wrapper.get('.media-trend-undated').trigger('click');
    await flushPromises();
    expect(wrapper.get('[data-test="media-mosaic-stub"]').text()).toBe('UNDATED');
  });

  it('ignores a stale media response after the scope changes', async () => {
    let resolveFirstRequest:
      | ((value: { scope: 'ALL_INDEXED'; buckets: Array<Record<string, unknown>> }) => void)
      | undefined;
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
      .findAll('.media-trend-mode button')
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
