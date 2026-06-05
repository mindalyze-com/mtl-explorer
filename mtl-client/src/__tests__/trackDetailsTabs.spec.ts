import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { defineComponent, nextTick } from 'vue';
import TrackDetails from '@/components/trackdetails/TrackDetails.vue';
import { MetricKey, XMode, type TrackChartSeries } from '@/utils/chartSeriesAdapter';
import { GpsTrackActivityTypeEnum, type GpsTrack } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import {
  roundToNiceTrackDetailsChartPointCount,
  TRACK_DETAILS_CHART_POINTS_DEFAULT,
  trackDetailsChartPointCountToSliderValue,
  trackDetailsChartPointSliderValueToCount,
} from '@/utils/trackDetailsChartPointSettings';
import { STORAGE_KEYS } from '@/utils/appStorage';

const mocks = vi.hoisted(() => ({
  calculateEnergyWhatIf: vi.fn(),
  clearChartInteraction: vi.fn(),
  fetchDetailTrackAtPrecision: vi.fn(),
  fetchTrackDetails: vi.fn(),
  fetchTrackPointsForRenderedShape: vi.fn(),
  getRelatedTracks: vi.fn(),
  saveTrackEnergyRiderWeight: vi.fn(),
  updateTrackActivityType: vi.fn(),
  setXMode: vi.fn(),
}));

vi.mock('@/utils/ServiceHelper', () => ({
  calculateEnergyWhatIf: mocks.calculateEnergyWhatIf,
  fetchTrackDetails: mocks.fetchTrackDetails,
  fetchTrackPointsForRenderedShape: mocks.fetchTrackPointsForRenderedShape,
  getRelatedTracks: mocks.getRelatedTracks,
  saveTrackEnergyRiderWeight: mocks.saveTrackEnergyRiderWeight,
  updateTrackActivityType: mocks.updateTrackActivityType,
}));

vi.mock('@/utils/tracks/trackCollectionLoader', () => ({
  fetchDetailTrackAtPrecision: mocks.fetchDetailTrackAtPrecision,
}));

vi.mock('@/composables/useChartSync', () => ({
  useChartSync: () => ({
    clearChartInteraction: mocks.clearChartInteraction,
    setXMode: mocks.setXMode,
  }),
}));

const TabsStub = defineComponent({
  name: 'Tabs',
  props: {
    value: { type: [String, Number], default: '0' },
  },
  emits: ['update:value'],
  template: `
    <div data-test="tabs" :data-value="value">
      <button data-test="tab-overview" @click="$emit('update:value', '0')">Overview</button>
      <button data-test="tab-graphs" @click="$emit('update:value', '1')">Graphs</button>
      <button data-test="tab-events" @click="$emit('update:value', '4')">Events</button>
      <slot />
    </div>
  `,
});

const PassthroughStub = defineComponent({
  template: '<div><slot /></div>',
});

const TrackDetailMiniMapStub = defineComponent({
  name: 'TrackDetailMiniMap',
  props: {
    gpsTrackId: Number,
    replayEnabled: Boolean,
    selectedEventKey: [String, Number],
    trackCoordinates: Array,
    trackEvents: Array,
  },
  emits: ['select-event', 'start-3d-replay'],
  template: `
    <div data-test="mini-map" :data-selected="selectedEventKey ?? ''" :data-replay-enabled="String(replayEnabled)">
      <button data-test="mini-select-event" @click="$emit('select-event', 7)">Select event</button>
      <button data-test="mini-clear-event" @click="$emit('select-event', null)">Clear event</button>
      <button data-test="mini-start-replay" @click="$emit('start-3d-replay')">3D Replay</button>
    </div>
  `,
});

const TrackGraphStub = defineComponent({
  name: 'TrackGraph',
  props: {
    config: Object,
    showRange: Boolean,
    syncEnabled: Boolean,
    trackDetails: Array,
    xMode: String,
  },
  template:
    '<div data-test="track-graph" :data-config-title="config && config.title" :data-sync-enabled="String(syncEnabled)" :data-show-range="String(showRange)" />',
});

function mockTrack(overrides: Partial<GpsTrack> = {}): GpsTrack {
  return {
    id: 1,
    trackName: 'Test Track',
    activityType: GpsTrackActivityTypeEnum.Walking,
    gpsTracksData: [
      {
        gpsTrackEvents: [
          {
            id: 7,
            eventType: 'STOP',
            startTimestamp: '2026-01-01T10:00:00Z',
            durationInSec: 120,
            startPointLongLat: { coordinates: [8.5, 47.4] },
          },
        ],
      },
    ],
    ...overrides,
  } as GpsTrack;
}

function mockDetailTrackResponse() {
  return {
    coordinates: [
      [8.4, 47.3],
      [8.5, 47.4],
    ],
    gpsTrack: mockTrack(),
    fromCache: false,
  };
}

function mockTrackChartSeries(overrides: Partial<TrackChartSeries> = {}): TrackChartSeries {
  return {
    points: [],
    recommendedSpeedMetric: null,
    availableMetrics: [],
    ...overrides,
  };
}

async function mountTrackDetails(
  options: {
    chartSeries?: TrackChartSeries;
    detailTrackError?: unknown;
    chartDetailsError?: unknown;
    relatedTracksError?: unknown;
  } = {}
) {
  mocks.fetchDetailTrackAtPrecision.mockResolvedValue(mockDetailTrackResponse());
  if (options.detailTrackError) {
    mocks.fetchDetailTrackAtPrecision.mockRejectedValueOnce(options.detailTrackError);
  }

  mocks.fetchTrackDetails.mockResolvedValue(options.chartSeries ?? mockTrackChartSeries());
  if (options.chartDetailsError) {
    mocks.fetchTrackDetails.mockRejectedValueOnce(options.chartDetailsError);
  }

  mocks.fetchTrackPointsForRenderedShape.mockResolvedValue([]);

  mocks.getRelatedTracks.mockResolvedValue({});
  if (options.relatedTracksError) {
    mocks.getRelatedTracks.mockRejectedValueOnce(options.relatedTracksError);
  }

  const wrapper = mount(TrackDetails, {
    props: { gpsTrackId: 1 },
    global: {
      stubs: {
        MtlSlider: true,
        Tab: PassthroughStub,
        TabList: PassthroughStub,
        TabPanel: PassthroughStub,
        TabPanels: PassthroughStub,
        Tabs: TabsStub,
        TrackDetailEvents: true,
        TrackDetailMiniMap: TrackDetailMiniMapStub,
        TrackDetailOverview: true,
        TrackDetailQuality: true,
        TrackDetailRelated: true,
        TrackGraph: TrackGraphStub,
      },
    },
  });

  await flushPromises();
  await nextTick();
  return wrapper;
}

describe('TrackDetails tab-scoped interactions', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    setActivePinia(createPinia());
  });

  it('loads chart details with the default chart point count', async () => {
    await mountTrackDetails();

    expect(mocks.fetchTrackDetails).toHaveBeenCalledWith(1, XMode.Time, TRACK_DETAILS_CHART_POINTS_DEFAULT);
  });

  it('uses bucket-average speed graph config when recommended by the server', async () => {
    const wrapper = await mountTrackDetails({
      chartSeries: mockTrackChartSeries({
        recommendedSpeedMetric: MetricKey.SpeedBucketAvgKmh,
        availableMetrics: [MetricKey.SpeedBucketAvgKmh],
      }),
    });

    expect(wrapper.find('[data-test="track-graph"]').attributes('data-config-title')).toBe('Speed (bucket avg)');
  });

  it('bubbles in-panel track navigation to the route owner', async () => {
    const wrapper = await mountTrackDetails();
    mocks.fetchDetailTrackAtPrecision.mockClear();

    (wrapper.vm as unknown as { navigateToTrack: (trackId: number) => void }).navigateToTrack(2);
    await nextTick();

    expect(wrapper.emitted('navigate-track')).toEqual([[2]]);
    expect(mocks.fetchDetailTrackAtPrecision).not.toHaveBeenCalled();
  });

  it('refreshes parent-facing track metadata after an in-panel activity update', async () => {
    const wrapper = await mountTrackDetails();

    expect(wrapper.emitted('track-loaded')?.[0]?.[0]).toMatchObject({
      id: 1,
      activityType: GpsTrackActivityTypeEnum.Walking,
    });

    (wrapper.vm as unknown as { onTrackUpdated: (track: GpsTrack) => void }).onTrackUpdated(
      mockTrack({ activityType: GpsTrackActivityTypeEnum.Bicycle })
    );
    await nextTick();

    expect(wrapper.emitted('track-loaded')?.at(-1)?.[0]).toEqual({
      id: 1,
      name: 'Test Track',
      description: '',
      activityType: GpsTrackActivityTypeEnum.Bicycle,
    });
  });

  it('shows actionable recovery when the required track load fails', async () => {
    const wrapper = await mountTrackDetails({ detailTrackError: new Error('track detail request failed') });

    expect(wrapper.find('[data-test="tabs"]').exists()).toBe(false);
    expect(wrapper.get('[data-test="track-detail-load-error"]').text()).toContain('Track details could not be loaded');
    expect(wrapper.get('[data-test="track-detail-load-error"]').text()).toContain('Retry');
    expect(wrapper.get('[data-test="track-detail-load-error"]').text()).toContain('Back to map');

    await wrapper.get('[data-test="track-detail-back"]').trigger('click');
    expect(wrapper.emitted('back-to-map')).toEqual([[]]);

    await wrapper.get('[data-test="track-detail-retry"]').trigger('click');
    await flushPromises();
    await nextTick();

    expect(wrapper.find('[data-test="track-detail-load-error"]').exists()).toBe(false);
    expect(wrapper.find('[data-test="tabs"]').exists()).toBe(true);
    expect(mocks.fetchDetailTrackAtPrecision).toHaveBeenCalledTimes(2);
  });

  it('keeps the panel usable when chart or related-track requests fail', async () => {
    const wrapper = await mountTrackDetails({
      chartDetailsError: new Error('chart request failed'),
      relatedTracksError: new Error('related request failed'),
    });

    expect(wrapper.find('[data-test="track-detail-load-error"]').exists()).toBe(false);
    expect(wrapper.find('[data-test="tabs"]').exists()).toBe(true);
    expect(wrapper.get('[data-test="track-detail-chart-error"]').text()).toContain('Track graphs could not be loaded');
    expect(wrapper.get('[data-test="track-detail-related-error"]').text()).toContain(
      'Related tracks could not be loaded'
    );
  });

  it('keeps range bands enabled by default', async () => {
    const wrapper = await mountTrackDetails();

    expect((wrapper.vm as unknown as { showRangeBand: boolean }).showRangeBand).toBe(true);
    expect(wrapper.find('[data-test="range-toggle"]').attributes('aria-pressed')).toBe('true');
    expect(
      wrapper.findAll('[data-test="track-graph"]').every((graph) => graph.attributes('data-show-range') === 'true')
    ).toBe(true);
  });

  it('persists range band preference and passes it to graphs', async () => {
    const wrapper = await mountTrackDetails();

    await wrapper.find('[data-test="range-toggle"]').trigger('click');
    await nextTick();

    expect(readStoredTrackPreferences().showRangeBand).toBe(false);
    expect((wrapper.vm as unknown as { showRangeBand: boolean }).showRangeBand).toBe(false);
    expect(wrapper.find('[data-test="range-toggle"]').attributes('aria-pressed')).toBe('false');
    expect(
      wrapper.findAll('[data-test="track-graph"]').every((graph) => graph.attributes('data-show-range') === 'false')
    ).toBe(true);
  });

  it('loads an existing disabled range band preference', async () => {
    localStorage.setItem(STORAGE_KEYS.trackDetailsPreferences, JSON.stringify({ showRangeBand: false }));

    const wrapper = await mountTrackDetails();

    expect((wrapper.vm as unknown as { showRangeBand: boolean }).showRangeBand).toBe(false);
    expect(
      wrapper.findAll('[data-test="track-graph"]').every((graph) => graph.attributes('data-show-range') === 'false')
    ).toBe(true);
  });

  it('persists chart point count and reloads chart details on commit', async () => {
    const wrapper = await mountTrackDetails();
    mocks.fetchTrackDetails.mockClear();

    await (
      wrapper.vm as unknown as { onChartPointCountSlideEnd: (event: { value: number }) => Promise<void> }
    ).onChartPointCountSlideEnd({ value: trackDetailsChartPointCountToSliderValue(1200) });
    await flushPromises();

    expect(readStoredTrackPreferences().chartPointCount).toBe(1200);
    expect(mocks.fetchTrackDetails).toHaveBeenCalledWith(1, XMode.Time, 1200);
  });

  it('rounds chart point slider commits to nice counts', async () => {
    const wrapper = await mountTrackDetails();
    mocks.fetchTrackDetails.mockClear();
    const sliderValue = 150;
    const expectedPointCount = trackDetailsChartPointSliderValueToCount(sliderValue);

    await (
      wrapper.vm as unknown as { onChartPointCountSlideEnd: (event: { value: number }) => Promise<void> }
    ).onChartPointCountSlideEnd({ value: sliderValue });
    await flushPromises();

    expect(expectedPointCount).not.toBe(769);
    expect(readStoredTrackPreferences().chartPointCount).toBe(expectedPointCount);
    expect(mocks.fetchTrackDetails).toHaveBeenCalledWith(1, XMode.Time, expectedPointCount);
  });

  it('resets an old below-minimum stored chart point count to the default', async () => {
    localStorage.setItem(STORAGE_KEYS.trackDetailsPreferences, JSON.stringify({ chartPointCount: 1 }));

    await mountTrackDetails();

    expect(readStoredTrackPreferences().chartPointCount).toBe(TRACK_DETAILS_CHART_POINTS_DEFAULT);
    expect(mocks.fetchTrackDetails).toHaveBeenCalledWith(1, XMode.Time, TRACK_DETAILS_CHART_POINTS_DEFAULT);
  });

  it('rounds old oddly precise stored chart point counts to nice counts', async () => {
    localStorage.setItem(STORAGE_KEYS.trackDetailsPreferences, JSON.stringify({ chartPointCount: 769 }));
    const expectedPointCount = roundToNiceTrackDetailsChartPointCount(769);

    await mountTrackDetails();

    expect(readStoredTrackPreferences().chartPointCount).toBe(expectedPointCount);
    expect(mocks.fetchTrackDetails).toHaveBeenCalledWith(1, XMode.Time, expectedPointCount);
  });

  it('selecting a minimap event moves to Events and stores the selected key', async () => {
    const wrapper = await mountTrackDetails();

    await wrapper.find('[data-test="mini-select-event"]').trigger('click');

    expect((wrapper.vm as unknown as { activeTab: string }).activeTab).toBe('4');
    expect((wrapper.vm as unknown as { selectedTrackEventKey: number | null }).selectedTrackEventKey).toBe(7);
    expect(wrapper.find('[data-test="mini-map"]').attributes('data-selected')).toBe('7');
  });

  it('clears event selection when leaving Events for Graphs', async () => {
    const wrapper = await mountTrackDetails();

    await wrapper.find('[data-test="mini-select-event"]').trigger('click');
    expect((wrapper.vm as unknown as { selectedTrackEventKey: number | null }).selectedTrackEventKey).toBe(7);

    await wrapper.find('[data-test="tab-graphs"]').trigger('click');

    expect((wrapper.vm as unknown as { activeTab: string }).activeTab).toBe('1');
    expect((wrapper.vm as unknown as { selectedTrackEventKey: number | null }).selectedTrackEventKey).toBeNull();
    expect(wrapper.find('[data-test="mini-map"]').attributes('data-selected')).toBe('');
  });

  it('clears chart interaction when leaving Graphs', async () => {
    const wrapper = await mountTrackDetails();

    await wrapper.find('[data-test="tab-graphs"]').trigger('click');
    expect(mocks.clearChartInteraction).not.toHaveBeenCalled();
    expect(wrapper.find('[data-test="track-graph"]').attributes('data-sync-enabled')).toBe('true');

    await wrapper.find('[data-test="tab-overview"]').trigger('click');

    expect(mocks.clearChartInteraction).toHaveBeenCalledTimes(1);
    expect(wrapper.find('[data-test="track-graph"]').attributes('data-sync-enabled')).toBe('false');
  });

  it('emits the loaded detail shape when starting 3D replay', async () => {
    const wrapper = await mountTrackDetails();
    mocks.fetchTrackDetails.mockClear();

    expect(wrapper.find('[data-test="mini-map"]').attributes('data-replay-enabled')).toBe('true');

    await wrapper.find('[data-test="mini-start-replay"]').trigger('click');
    await flushPromises();

    const events = wrapper.emitted('start-3d-replay');
    expect(mocks.fetchTrackDetails).toHaveBeenCalledWith(1, XMode.Time, 1000);
    expect(events).toHaveLength(1);
    expect(events?.[0]?.[0]).toMatchObject({
      trackId: 1,
      coordinates: [
        [8.4, 47.3],
        [8.5, 47.4],
      ],
      gpsTrack: { id: 1, trackName: 'Test Track' },
    });
  });
});

function readStoredTrackPreferences(): Record<string, unknown> {
  return JSON.parse(localStorage.getItem(STORAGE_KEYS.trackDetailsPreferences) ?? '{}') as Record<string, unknown>;
}
