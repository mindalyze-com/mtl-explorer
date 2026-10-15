import { enableAutoUnmount, mount, RouterLinkStub } from '@vue/test-utils';
import { defineComponent, nextTick } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useMeasurementSystem } from '@/composables/useMeasurementSystem';
import { useTheme } from '@/composables/useTheme';
import { useLocale } from '@/composables/useLocale';

const measurementPreference = useMeasurementSystem();
enableAutoUnmount(afterEach);
import StatisticsOverview from '@/components/statistics/StatisticsOverview.vue';
import LatestActivityPreview from '@/components/statistics/LatestActivityPreview.vue';
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
    measurementSystem: 'METRIC',
    summary: {
      trackCount: 2,
      distanceM: 125_000,
      durationMs: 28_800_000,
      ascentM: 1500,
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
    firstActivity: { sortOrder: 10, rowKey: 'first-activity', trackId: 11, value: 0 },
    latestActivity: { sortOrder: 20, rowKey: 'latest-activity', trackId: 12, value: 0 },
    milestones: [
      {
        sortOrder: 4,
        dimension: 'DISTANCE',
        trackId: 11,
        thresholdM: 100_000,
        achievedM: 100_000,
      },
    ],
    exclusionSummary: {
      highlightExcludedTrackCount: 0,
      statisticsExcludedTrackCount: 0,
    },
    ...overrides,
  };
}

function mountOverview(
  toggle = vi.fn(),
  indexedMediaCount: number | null = null,
  indexedPhotoCount: number | null = null,
  indexedVideoCount: number | null = null
) {
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
      filterRevision: 0,
      indexedMediaCount,
      indexedPhotoCount,
      indexedVideoCount,
    },
    global: {
      directives: {
        tooltip: {
          mounted(element, binding) {
            element.setAttribute('data-tooltip', binding.value.value);
          },
        },
      },
      stubs: {
        RouterLink: RouterLinkStub,
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
  it('localizes calendar-day labels while preserving ISO week identifiers', async () => {
    const locale = useLocale();
    const previousLocale = locale.formatLocale.value;
    const day = {
      periodType: 'day',
      periodKey: '2022-01-16',
      label: '2022-01-16',
      trackCount: 1,
      distanceM: 1000,
      durationMs: 60_000,
    };
    const week = { ...day, periodType: 'week', periodKey: '2025-W03', label: '2025-W03' };
    fetchStatisticsOverviewMock.mockResolvedValueOnce(overview({ activePeriods: [day, week] }));
    const wrapper = mountOverview();
    try {
      locale.setLocale('en-GB');
      await flush();
      expect(wrapper.get('[data-test="active-period-day"] .period-row__subtitle').text()).toBe('16/01/2022');
      expect(wrapper.get('[data-test="active-period-week"] .period-row__subtitle').text()).toBe('2025-W03');
      locale.setLocale('de-DE');
      await nextTick();
      expect(wrapper.get('[data-test="active-period-day"] .period-row__subtitle').text()).toBe('16.01.2022');
    } finally {
      locale.setLocale(previousLocale);
    }
  });
  beforeEach(() => {
    measurementPreference.setMeasurementSystem('METRIC');
    fetchStatisticsOverviewMock.mockReset();
    updateTrackStatisticsExclusionMock.mockReset();
  });

  it('recolors the loaded activity donut without refetching or changing its slices', async () => {
    const theme = useTheme();
    theme.setScheme('light');
    await flush();
    const style = document.createElement('style');
    style.textContent = `
      :root { --chart-tooltip-bg: #ffffff; --chart-tooltip-text: #334155; --surface-glass-heavy: #ffffff; }
      :root[data-theme="dark"] { --chart-tooltip-bg: #0f172a; --chart-tooltip-text: #e2e8f0; --surface-glass-heavy: #0f172a; }
    `;
    document.head.append(style);
    fetchStatisticsOverviewMock.mockResolvedValue(overview());
    const wrapper = mountOverview();
    try {
      await flush();
      const options = () => wrapper.getComponent(HighchartsStub).props('options');
      const slices = structuredClone(options().series[0].data);
      const requestCount = fetchStatisticsOverviewMock.mock.calls.length;
      for (const scheme of ['dark', 'light'] as const) {
        theme.setScheme(scheme);
        await flush();
        const tokens = getComputedStyle(document.documentElement);
        expect(options().tooltip.backgroundColor).toBe(tokens.getPropertyValue('--chart-tooltip-bg').trim());
        expect(options().tooltip.style.color).toBe(tokens.getPropertyValue('--chart-tooltip-text').trim());
        expect(options().plotOptions.pie.borderColor).toBe(tokens.getPropertyValue('--surface-glass-heavy').trim());
        expect(options().series[0].data).toEqual(slices);
      }
      expect(fetchStatisticsOverviewMock).toHaveBeenCalledTimes(requestCount);
    } finally {
      wrapper.unmount();
      style.remove();
      theme.setScheme('light');
      await flush();
    }
  });

  it('keeps source review discoverable when every selected track is withheld', async () => {
    fetchStatisticsOverviewMock.mockResolvedValueOnce(
      overview({
        summary: { trackCount: 0, distanceM: 0, durationMs: 0 },
        exclusionSummary: { reviewWithheldTrackCount: 2, reviewPendingSourceCount: 1 },
      })
    );
    const wrapper = mountOverview();
    await flush();
    const notice = wrapper.get('[data-test="review-withheld-notice"]');
    expect(notice.text()).toContain('2 tracks in this selection are excluded');
    expect(notice.text()).toContain('1 source file linked to these tracks has unresolved issues');
    expect(notice.findComponent(RouterLinkStub).props('to')).toEqual({
      name: 'review',
      query: { state: 'NEEDS_REVIEW' },
    });
    expect(notice.findComponent(RouterLinkStub).text()).toBe('Review source files across the archive');
  });

  it('does not reconstruct estimated duration from dates in recent rows or latest activity', async () => {
    fetchStatisticsOverviewMock.mockResolvedValue(
      overview({
        latestActivity: { trackId: 12 },
        recentActivities: [
          { trackId: 12, sortOrder: 1 },
          { trackId: 11, sortOrder: 2 },
        ],
      })
    );
    const wrapper = mountOverview();
    await wrapper.setProps({
      tracks: wrapper.props('tracks').map((track) =>
        track.id === 12
          ? {
              ...track,
              timingUnreliable: true,
              timingEstimated: true,
              trackDurationInMotionSecs: undefined,
              endDate: new Date('2035-06-01T00:00:00Z'),
            }
          : track
      ),
    });
    await flush();
    expect(wrapper.get('[data-test="recent-row-12"]').text()).toContain('Time unavailable');
    const state = wrapper.emitted('latest-activity-change')?.at(-1)?.[0] as {
      track: { trackId: number; displayName: string; durationMs: number | null; distanceM: number };
      loading: boolean;
      error: boolean;
    };
    expect(state.track.durationMs).toBeNull();
    expect(state.track.distanceM).toBe(25_000);
    expect(mount(LatestActivityPreview, { props: { state } }).text()).toContain('Time unavailable');
    expect(wrapper.get('[data-test="recent-row-11"]').text()).not.toContain('Time unavailable');
  });

  it('publishes the server-selected latest activity and clears it while the filter refreshes', async () => {
    fetchStatisticsOverviewMock.mockResolvedValueOnce(overview({ latestActivity: { trackId: 11 } }));
    const wrapper = mountOverview();
    await flush();
    expect(wrapper.emitted('latest-activity-change')?.at(-1)).toEqual([
      {
        track: expect.objectContaining({
          trackId: 11,
          displayName: 'Filtered century',
          distanceM: 100_000,
          durationMs: 18_000_000,
        }),
        loading: false,
        error: false,
      },
    ]);

    let finishRefresh!: (value: StatisticsOverviewResponseDto) => void;
    fetchStatisticsOverviewMock.mockImplementationOnce(
      () => new Promise<StatisticsOverviewResponseDto>((resolve) => (finishRefresh = resolve))
    );
    await wrapper.setProps({ filterRevision: 1 });
    await flush();
    expect(wrapper.emitted('latest-activity-change')?.at(-1)).toEqual([{ track: null, loading: true, error: false }]);
    finishRefresh(overview({ latestActivity: undefined }));
    await flush();
    expect(wrapper.emitted('latest-activity-change')?.at(-1)).toEqual([{ track: null, loading: false, error: false }]);

    fetchStatisticsOverviewMock.mockRejectedValueOnce(new Error('Statistics unavailable'));
    await wrapper.setProps({ retryRevision: 1 });
    await flush();
    expect(wrapper.emitted('latest-activity-change')?.at(-1)).toEqual([{ track: null, loading: false, error: true }]);
  });

  it('renders server-filtered overview counts instead of client or unfiltered counts', async () => {
    fetchStatisticsOverviewMock.mockResolvedValueOnce(overview());

    const wrapper = mountOverview();
    await flush();

    expect(fetchStatisticsOverviewMock).toHaveBeenCalledOnce();
    expect(wrapper.find('[data-test="summary-tracks"]').text()).toContain('2');
    expect(wrapper.find('[data-test="summary-ascent"]').text()).toContain('1,500 m');
    const filterBanner = wrapper.get('[data-test="filter-banner"]');
    expect(filterBanner.element.tagName).toBe('BUTTON');
    expect(filterBanner.text()).toContain('Showing 2 of 4 tracks');
    expect(filterBanner.attributes('aria-label')).toBe('Open Filter. Showing 2 of 4 tracks');
    await filterBanner.trigger('click');
    expect(wrapper.emitted('open-filter')).toEqual([[]]);
    await filterBanner.trigger('keydown', { key: 'Enter' });
    await filterBanner.trigger('keydown', { key: ' ' });
    expect(wrapper.emitted('open-filter')).toEqual([[], [], []]);
    expect(wrapper.text()).toContain('Filtered century');
    expect(wrapper.text()).not.toContain('99');
  });

  it('shows one combined filtered-media count and opens Media trends', async () => {
    fetchStatisticsOverviewMock.mockResolvedValueOnce(overview());

    const wrapper = mountOverview(vi.fn(), 1_284, 1_200, 84);
    await flush();

    const mediaTile = wrapper.get('[data-test="summary-media"]');
    expect(mediaTile.text()).toContain('1,284');
    expect(mediaTile.text()).toContain('Media');
    expect(mediaTile.text()).not.toContain('Photos');
    expect(mediaTile.text()).not.toContain('Videos');
    expect(mediaTile.attributes('aria-label')).toContain('1,284 filtered media items');
    expect(mediaTile.attributes('data-tooltip')).toBe(
      '1,200 photos and 84 videos in the current media selection. The matching tracks define the time range.'
    );

    await mediaTile.trigger('click');

    expect(wrapper.emitted('open-media')).toEqual([[]]);
  });

  it('does not refetch when map track metadata batches change', async () => {
    fetchStatisticsOverviewMock.mockResolvedValueOnce(overview());

    const wrapper = mountOverview();
    await flush();
    await wrapper.setProps({ tracks: [{ id: 99 }] as GpsTrack[] });
    await flush();

    expect(fetchStatisticsOverviewMock).toHaveBeenCalledOnce();
  });

  it('updates from the server when the applied filter revision changes', async () => {
    fetchStatisticsOverviewMock
      .mockResolvedValueOnce(overview())
      .mockResolvedValueOnce(
        overview({ summary: { trackCount: 1, distanceM: 10_000, durationMs: 3_600_000, energyWh: 0 } })
      );

    const wrapper = mountOverview();
    await flush();
    await wrapper.setProps({ filterRevision: 1 });
    await flush();

    expect(fetchStatisticsOverviewMock).toHaveBeenCalledTimes(2);
    expect(wrapper.find('[data-test="summary-tracks"]').text()).toContain('1');
  });

  it('recovers from a load failure when the parent retries statistics', async () => {
    fetchStatisticsOverviewMock.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce(overview());

    const wrapper = mountOverview();
    await flush();

    expect(wrapper.find('[data-test="overview-error"]').exists()).toBe(true);

    await wrapper.setProps({ retryRevision: 1 });
    await flush();

    expect(fetchStatisticsOverviewMock).toHaveBeenCalledTimes(2);
    expect(wrapper.find('[data-test="overview-error"]').exists()).toBe(false);
    expect(wrapper.find('[data-test="summary-tracks"]').text()).toContain('2');
  });

  it('refetches semantic milestones when the measurement system changes', async () => {
    fetchStatisticsOverviewMock.mockResolvedValueOnce(overview()).mockResolvedValueOnce(
      overview({
        measurementSystem: 'US_CUSTOMARY',
        milestones: [
          {
            sortOrder: 5,
            dimension: 'DISTANCE',
            trackId: 12,
            thresholdM: 160_934.4,
            achievedM: 170_000,
          },
        ],
      })
    );

    const wrapper = mountOverview();
    await flush();

    expect(fetchStatisticsOverviewMock).toHaveBeenLastCalledWith('METRIC', expect.any(AbortSignal), undefined);
    expect(wrapper.text()).toContain('First 100 km track');

    measurementPreference.setMeasurementSystem('US_CUSTOMARY');
    await flush();

    expect(fetchStatisticsOverviewMock).toHaveBeenLastCalledWith('US_CUSTOMARY', expect.any(AbortSignal), undefined);
    expect(wrapper.text()).toContain('First 100 mi track');
    expect(wrapper.text()).toContain('Filtered latest');

    wrapper.unmount();
    measurementPreference.setMeasurementSystem('METRIC');
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
    expect(wrapper.emitted('track-updated')).toEqual([
      [
        expect.objectContaining({
          id: 11,
          highlightExclusionReason: ExclusionReasonEnum.GpsNoise,
        }),
      ],
    ]);

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
    expect(wrapper.find('[data-test="overview-info-text"]').text()).toContain('the most moving time');

    await wrapper.find('[data-test="milestones-info"]').trigger('click');
    expect(toggle).toHaveBeenCalledTimes(2);
    expect(wrapper.find('[data-test="overview-info-text"]').text()).toContain('current filter');
  });
});
