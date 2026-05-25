import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { defineComponent, nextTick } from 'vue';
import TrackDetails from '@/components/trackdetails/TrackDetails.vue';
import { XMode } from '@/utils/chartSeriesAdapter';
import {
  roundToNiceTrackDetailsChartPointCount,
  TRACK_DETAILS_CHART_POINTS_DEFAULT,
  trackDetailsChartPointCountToSliderValue,
  trackDetailsChartPointSliderValueToCount,
} from '@/utils/trackDetailsChartPointSettings';
import { USER_PREFS_KEYS } from '@/utils/userPrefs';

const mocks = vi.hoisted(() => ({
  clearChartInteraction: vi.fn(),
  fetchDetailTrackAtPrecision: vi.fn(),
  fetchTrackDetails: vi.fn(),
  fetchTrackPointsForRenderedShape: vi.fn(),
  getRelatedTracks: vi.fn(),
  setXMode: vi.fn(),
}));

vi.mock('@/utils/ServiceHelper', () => ({
  fetchTrackDetails: mocks.fetchTrackDetails,
  fetchTrackPointsForRenderedShape: mocks.fetchTrackPointsForRenderedShape,
  getRelatedTracks: mocks.getRelatedTracks,
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
    selectedEventKey: [String, Number],
    trackCoordinates: Array,
    trackEvents: Array,
  },
  emits: ['select-event'],
  template: `
    <div data-test="mini-map" :data-selected="selectedEventKey ?? ''">
      <button data-test="mini-select-event" @click="$emit('select-event', 7)">Select event</button>
      <button data-test="mini-clear-event" @click="$emit('select-event', null)">Clear event</button>
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
    '<div data-test="track-graph" :data-sync-enabled="String(syncEnabled)" :data-show-range="String(showRange)" />',
});

function mockTrack() {
  return {
    id: 1,
    trackName: 'Test Track',
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
  };
}

async function mountTrackDetails() {
  mocks.fetchDetailTrackAtPrecision.mockResolvedValue({
    coordinates: [
      [8.4, 47.3],
      [8.5, 47.4],
    ],
    gpsTrack: mockTrack(),
    fromCache: false,
  });
  mocks.fetchTrackDetails.mockResolvedValue([]);
  mocks.fetchTrackPointsForRenderedShape.mockResolvedValue([]);
  mocks.getRelatedTracks.mockResolvedValue({});

  const wrapper = mount(TrackDetails, {
    props: { gpsTrackId: 1 },
    global: {
      stubs: {
        Slider: true,
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
  });

  it('loads chart details with the default chart point count', async () => {
    await mountTrackDetails();

    expect(mocks.fetchTrackDetails).toHaveBeenCalledWith(1, XMode.Time, TRACK_DETAILS_CHART_POINTS_DEFAULT);
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

    expect(localStorage.getItem(USER_PREFS_KEYS.trackGraphRangeBand)).toBe('false');
    expect((wrapper.vm as unknown as { showRangeBand: boolean }).showRangeBand).toBe(false);
    expect(wrapper.find('[data-test="range-toggle"]').attributes('aria-pressed')).toBe('false');
    expect(
      wrapper.findAll('[data-test="track-graph"]').every((graph) => graph.attributes('data-show-range') === 'false')
    ).toBe(true);
  });

  it('loads an existing disabled range band preference', async () => {
    localStorage.setItem(USER_PREFS_KEYS.trackGraphRangeBand, 'false');

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

    expect(localStorage.getItem(USER_PREFS_KEYS.trackChartPointCount)).toBe('1200');
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
    expect(localStorage.getItem(USER_PREFS_KEYS.trackChartPointCount)).toBe(String(expectedPointCount));
    expect(mocks.fetchTrackDetails).toHaveBeenCalledWith(1, XMode.Time, expectedPointCount);
  });

  it('resets an old below-minimum stored chart point count to the default', async () => {
    localStorage.setItem(USER_PREFS_KEYS.trackChartPointCount, '1');

    await mountTrackDetails();

    expect(localStorage.getItem(USER_PREFS_KEYS.trackChartPointCount)).toBe('350');
    expect(mocks.fetchTrackDetails).toHaveBeenCalledWith(1, XMode.Time, TRACK_DETAILS_CHART_POINTS_DEFAULT);
  });

  it('rounds old oddly precise stored chart point counts to nice counts', async () => {
    localStorage.setItem(USER_PREFS_KEYS.trackChartPointCount, '769');
    const expectedPointCount = roundToNiceTrackDetailsChartPointCount(769);

    await mountTrackDetails();

    expect(localStorage.getItem(USER_PREFS_KEYS.trackChartPointCount)).toBe(String(expectedPointCount));
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
});
