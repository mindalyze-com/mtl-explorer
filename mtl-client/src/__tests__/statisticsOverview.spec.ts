import { mount } from '@vue/test-utils';
import { defineComponent, nextTick } from 'vue';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import StatisticsOverview from '@/components/statistics/StatisticsOverview.vue';
import { fetchStatisticsOverview, updateTrackStatisticsExclusion } from '@/utils/ServiceHelper';
import {
  StatisticsExclusionUpdateRequestHighlightExclusionReasonEnum as ExclusionReasonEnum,
  type GpsTrack,
  type StatisticsOverviewResponseDto,
} from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';

vi.mock('@/utils/ServiceHelper', () => ({
  fetchStatisticsOverview: vi.fn(),
  updateTrackStatisticsExclusion: vi.fn(),
}));

const fetchStatisticsOverviewMock = vi.mocked(fetchStatisticsOverview);
const updateTrackStatisticsExclusionMock = vi.mocked(updateTrackStatisticsExclusion);

function popoverStub(toggle: ReturnType<typeof vi.fn>) {
  return defineComponent({
    name: 'Popover',
    methods: { toggle },
    template: '<div data-test="popover"><slot /></div>',
  });
}

const ActivityTypeBadgeStub = defineComponent({
  name: 'ActivityTypeBadge',
  props: {
    type: { type: String, default: '' },
  },
  template: '<span data-test="activity-badge">{{ type }}</span>',
});

const TrackShapePreviewStub = defineComponent({
  name: 'TrackShapePreview',
  props: {
    trackId: { type: [Number, String], default: null },
  },
  template: '<span data-test="shape">{{ trackId }}</span>',
});

const HighchartsStub = defineComponent({
  name: 'Highcharts',
  props: {
    options: { type: Object, default: () => ({}) },
  },
  template: '<div data-test="highcharts"></div>',
});

const SelectStub = defineComponent({
  name: 'Select',
  props: {
    modelValue: { type: String, default: null },
    options: { type: Array, default: () => [] },
    optionLabel: { type: String, default: '' },
    optionValue: { type: String, default: '' },
  },
  emits: ['update:modelValue'],
  methods: {
    optionLabelOf(option: Record<string, unknown>) {
      return this.optionLabel ? option[this.optionLabel] : option;
    },
    optionValueOf(option: Record<string, unknown>) {
      return this.optionValue ? option[this.optionValue] : option;
    },
    onChange(event: Event) {
      const value = (event.target as HTMLSelectElement).value;
      this.$emit('update:modelValue', value || null);
    },
  },
  template: `
    <select v-bind="$attrs" :value="modelValue ?? ''" @change="onChange">
      <option
        v-for="option in options"
        :key="String(optionValueOf(option))"
        :value="optionValueOf(option) ?? ''"
      >
        {{ optionLabelOf(option) }}
      </option>
    </select>
  `,
});

const flush = async () => {
  await nextTick();
  await Promise.resolve();
  await Promise.resolve();
  await nextTick();
};

function overview(overrides: Partial<StatisticsOverviewResponseDto> = {}): StatisticsOverviewResponseDto {
  return {
    summary: {
      trackCount: 2,
      distanceM: 125_000,
      durationMs: 28_800_000,
      energyWh: 1_800,
      oldestStart: new Date(2026, 0, 1, 10),
      newestStart: new Date(2026, 0, 3, 12),
    },
    activityBreakdown: [
      { activityType: 'BICYCLE', trackCount: 1, distanceM: 100_000, durationMs: 18_000_000, energyWh: 1_500 },
      { activityType: 'WALKING', trackCount: 1, distanceM: 25_000, durationMs: 10_800_000, energyWh: 300 },
    ],
    trackRankings: [
      {
        rowKey: 'longest-distance',
        rows: [
          { sortOrder: 1, rowKey: 'longest-distance', trackId: 11, value: 100_000 },
          { sortOrder: 2, rowKey: 'longest-distance', trackId: 12, value: 25_000 },
        ],
      },
      {
        rowKey: 'quickest-ascent',
        rows: [{ sortOrder: 1, rowKey: 'quickest-ascent', trackId: 11, value: 1200 }],
      },
    ],
    recentActivities: [{ sortOrder: 1, rowKey: 'recent', trackId: 12, value: 0 }],
    activePeriods: [
      {
        sortOrder: 40,
        periodType: 'weekday',
        periodKey: '6',
        label: 'Saturday',
        trackCount: 2,
        distanceM: 125_000,
        durationMs: 28_800_000,
      },
    ],
    periodDistributions: [
      {
        periodType: 'weekday',
        rows: [
          {
            sortOrder: 1,
            periodType: 'weekday',
            periodKey: '6',
            label: 'Saturday',
            trackCount: 2,
            distanceM: 125_000,
            durationMs: 28_800_000,
          },
          {
            sortOrder: 2,
            periodType: 'weekday',
            periodKey: '7',
            label: 'Sunday',
            trackCount: 1,
            distanceM: 25_000,
            durationMs: 10_800_000,
          },
        ],
      },
    ],
    milestones: [
      { sortOrder: 10, rowKey: 'first-activity', trackId: 11, value: 0 },
      { sortOrder: 60, rowKey: 'distance-100000', trackId: 11, value: 100_000 },
    ],
    exclusionSummary: {
      highlightExcludedTrackCount: 0,
      statisticsExcludedTrackCount: 0,
    },
    ...overrides,
  };
}

function mountOverview(toggle = vi.fn()) {
  return mount(StatisticsOverview, {
    props: {
      tracks: [
        {
          id: 11,
          trackName: 'Filtered century',
          activityType: 'BICYCLE',
          startDate: new Date(2026, 0, 2, 10),
          endDate: new Date(2026, 0, 2, 15),
          trackDurationInMotionSecs: 18_000,
          trackLengthInMeter: 100_000,
          ascentInMeter: 1200,
          energyNetTotalWh: 1500,
          speedInKmh30sMax: 42,
          powerWatts30sMax: 320,
        },
        {
          id: 12,
          trackName: 'Filtered latest',
          activityType: 'WALKING',
          startDate: new Date(2026, 0, 3, 12),
          endDate: new Date(2026, 0, 3, 15),
          trackDurationInMotionSecs: 10_800,
          trackLengthInMeter: 25_000,
          ascentInMeter: 300,
          energyNetTotalWh: 300,
        },
      ] as GpsTrack[],
      tracksCount: 99,
      unfilteredTotal: 4,
    },
    global: {
      directives: { tooltip: {} },
      stubs: {
        ActivityTypeBadge: ActivityTypeBadgeStub,
        Popover: popoverStub(toggle),
        Select: SelectStub,
        TrackShapePreview: TrackShapePreviewStub,
        highcharts: HighchartsStub,
      },
    },
  });
}

describe('StatisticsOverview', () => {
  beforeEach(() => {
    fetchStatisticsOverviewMock.mockReset();
    updateTrackStatisticsExclusionMock.mockReset();
  });

  it('renders server-filtered overview counts instead of client or unfiltered counts', async () => {
    fetchStatisticsOverviewMock.mockResolvedValueOnce(overview());

    const wrapper = mountOverview();
    await flush();

    expect(fetchStatisticsOverviewMock).toHaveBeenCalledOnce();
    expect(wrapper.find('[data-test="summary-tracks"]').text()).toContain('2');
    expect(wrapper.find('[data-test="filter-banner"]').text()).toContain('Showing 2 of 4 tracks');
    expect(wrapper.text()).toContain('Filtered century');
    expect(wrapper.text()).not.toContain('99');
  });

  it('updates from the server when the filtered track set changes', async () => {
    fetchStatisticsOverviewMock
      .mockResolvedValueOnce(overview())
      .mockResolvedValueOnce(
        overview({ summary: { trackCount: 1, distanceM: 10_000, durationMs: 3_600_000, energyWh: 0 } })
      );

    const wrapper = mountOverview();
    await flush();
    await wrapper.setProps({ tracks: [{ id: 99 }] as GpsTrack[] });
    await flush();

    expect(fetchStatisticsOverviewMock).toHaveBeenCalledTimes(2);
    expect(wrapper.find('[data-test="summary-tracks"]').text()).toContain('1');
  });

  it('toggles activity breakdown metrics and hides energy when the server summary has no energy', async () => {
    fetchStatisticsOverviewMock.mockResolvedValueOnce(
      overview({
        summary: { trackCount: 2, distanceM: 30_000, durationMs: 10_800_000, energyWh: 0 },
        activityBreakdown: [
          { activityType: 'BICYCLE', trackCount: 1, distanceM: 20_000, durationMs: 3_600_000, energyWh: 0 },
          { activityType: 'WALKING', trackCount: 1, distanceM: 10_000, durationMs: 7_200_000, energyWh: 0 },
        ],
      })
    );

    const wrapper = mountOverview();
    await flush();

    expect(wrapper.find('[data-test="activity-metric-energy"]').exists()).toBe(false);

    await wrapper.find('[data-test="activity-metric-duration"]').trigger('click');

    expect(wrapper.find('[data-test="activity-row-WALKING"]').text()).toContain('2h 00m');
  });

  it('opens highlight drilldowns and emits detail navigation from ranking and recent rows', async () => {
    fetchStatisticsOverviewMock.mockResolvedValueOnce(overview());

    const wrapper = mountOverview();
    await flush();

    await wrapper.find('[data-test="highlight-longest-distance-main"]').trigger('click');
    await nextTick();
    expect(wrapper.find('[data-test="highlight-drilldown"]').text()).toContain('Filtered century');

    await wrapper.find('[data-test="highlight-drilldown-open-1"]').trigger('click');
    await wrapper.find('[data-test="recent-row-12"]').trigger('click');

    expect(wrapper.emitted('open-details')).toEqual([[11], [12]]);
  });

  it('shows the number of tracks excluded from highlights', async () => {
    fetchStatisticsOverviewMock.mockResolvedValueOnce(
      overview({
        exclusionSummary: {
          highlightExcludedTrackCount: 3,
          statisticsExcludedTrackCount: 1,
        },
      })
    );

    const wrapper = mountOverview();
    await flush();

    expect(wrapper.find('[data-test="highlight-exclusion-note"]').text()).toBe('3 tracks excluded');

    await wrapper.find('[data-test="highlight-exclusion-note"]').trigger('click');

    expect(wrapper.emitted('view-highlight-exclusions')).toEqual([[]]);
  });

  it('excludes a highlight drilldown track and reloads winners', async () => {
    fetchStatisticsOverviewMock.mockResolvedValueOnce(overview()).mockResolvedValueOnce(
      overview({
        trackRankings: [
          {
            rowKey: 'longest-distance',
            rows: [{ sortOrder: 1, rowKey: 'longest-distance', trackId: 12, value: 25_000 }],
          },
        ],
        exclusionSummary: {
          highlightExcludedTrackCount: 1,
          statisticsExcludedTrackCount: 0,
        },
      })
    );
    updateTrackStatisticsExclusionMock.mockResolvedValueOnce({
      id: 11,
      highlightExclusionReason: ExclusionReasonEnum.GpsNoise,
    } as GpsTrack);

    const wrapper = mountOverview();
    await flush();

    await wrapper.find('[data-test="highlight-longest-distance-main"]').trigger('click');
    await nextTick();
    expect(wrapper.find('[data-test="highlight-drilldown"]').text()).toContain('Filtered century');

    await wrapper.find('[data-test="highlight-exclude-1"]').trigger('click');
    expect((wrapper.find('[data-test="highlight-exclusion-reason-select"]').element as HTMLSelectElement).value).toBe(
      ExclusionReasonEnum.GpsNoise
    );

    await wrapper.find('[data-test="highlight-exclusion-save"]').trigger('click');
    await flush();

    expect(updateTrackStatisticsExclusionMock).toHaveBeenCalledWith(11, {
      highlightExclusionReason: ExclusionReasonEnum.GpsNoise,
    });
    expect(fetchStatisticsOverviewMock).toHaveBeenCalledTimes(2);
    expect(wrapper.find('[data-test="highlight-exclusion-note"]').text()).toBe('1 track excluded');

    await wrapper.find('[data-test="highlight-longest-distance-main"]').trigger('click');
    await nextTick();
    expect(wrapper.find('[data-test="highlight-drilldown"]').text()).not.toContain('Filtered century');
    expect(wrapper.find('[data-test="highlight-drilldown"]').text()).toContain('Filtered latest');
  });

  it('shows the top 100 rows in highlight drilldowns', async () => {
    const rankingRows = Array.from({ length: 101 }, (_, index) => ({
      sortOrder: index + 1,
      rowKey: 'longest-distance',
      trackId: 1000 + index,
      value: 200_000 - index,
    }));
    fetchStatisticsOverviewMock.mockResolvedValueOnce(
      overview({
        trackRankings: [{ rowKey: 'longest-distance', rows: rankingRows }],
        recentActivities: [],
      })
    );

    const wrapper = mountOverview();
    await flush();

    await wrapper.find('[data-test="highlight-longest-distance-main"]').trigger('click');
    await nextTick();

    expect(wrapper.find('[data-test="highlight-drilldown-row-100"]').exists()).toBe(true);
    expect(wrapper.find('[data-test="highlight-drilldown-row-101"]').exists()).toBe(false);
  });

  it('emits view-all-tracks from the recent activity link', async () => {
    fetchStatisticsOverviewMock.mockResolvedValueOnce(overview());

    const wrapper = mountOverview();
    await flush();

    await wrapper.find('[data-test="recent-view-all"]').trigger('click');

    expect(wrapper.emitted('view-all-tracks')).toEqual([[]]);
  });

  it('opens period drilldowns from server-provided distributions', async () => {
    fetchStatisticsOverviewMock.mockResolvedValueOnce(overview());

    const wrapper = mountOverview();
    await flush();

    await wrapper.find('[data-test="active-period-weekday"]').trigger('click');
    await nextTick();

    expect(wrapper.find('[data-test="period-drilldown"]').text()).toContain('Saturday');
    expect(wrapper.find('[data-test="period-drilldown"]').text()).toContain('Sunday');
  });

  it('shows mobile-safe info popovers for period and milestone explanations', async () => {
    const toggle = vi.fn();
    fetchStatisticsOverviewMock.mockResolvedValueOnce(overview());

    const wrapper = mountOverview(toggle);
    await flush();

    await wrapper.find('[data-test="active-periods-info"]').trigger('click');
    expect(toggle).toHaveBeenCalledOnce();
    expect(wrapper.find('[data-test="overview-info-text"]').text()).toContain('total moving time');

    await wrapper.find('[data-test="milestones-info"]').trigger('click');
    expect(toggle).toHaveBeenCalledTimes(2);
    expect(wrapper.find('[data-test="overview-info-text"]').text()).toContain('active filter');
  });
});
