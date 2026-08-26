import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { defineComponent, nextTick } from 'vue';
import TrackDetails from '@/components/trackdetails/TrackDetails.vue';
import { MetricKey, XMode, type ChartPoint, type TrackChartSeries } from '@/utils/chartSeriesAdapter';
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
  getMediaByTrack: vi.fn(),
  saveMediaTimeCorrections: vi.fn(),
  setManualMediaLocation: vi.fn(),
  clearManualMediaLocation: vi.fn(),
  saveTrackEnergyRiderWeight: vi.fn(),
  updateTrackActivityType: vi.fn(),
  setXMode: vi.fn(),
  beginMiniMapResize: vi.fn(),
  updateMiniMapResize: vi.fn(),
  commitMiniMapResize: vi.fn(),
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

vi.mock('@/repositories/mediaRepository', () => ({
  getMediaByTrack: mocks.getMediaByTrack,
  saveMediaTimeCorrections: mocks.saveMediaTimeCorrections,
  setManualMediaLocation: mocks.setManualMediaLocation,
  clearManualMediaLocation: mocks.clearManualMediaLocation,
  mediaContentUrl: (id: number) => `/media/${id}`,
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
      <button data-test="tab-photos" @click="$emit('update:value', '4')">Photos</button>
      <button data-test="tab-events" @click="$emit('update:value', '5')">Events</button>
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
    trackMedia: Array,
    mediaInteractionEnabled: Boolean,
    selectedMediaId: Number,
    highlightedMediaId: Number,
  },
  emits: ['select-event', 'select-media', 'clear-selection', 'start-3d-replay'],
  setup(_props, { expose }) {
    expose({
      beginMiniMapResize: mocks.beginMiniMapResize,
      updateMiniMapResize: mocks.updateMiniMapResize,
      commitMiniMapResize: mocks.commitMiniMapResize,
    });
  },
  template: `
    <div
      data-test="mini-map"
      :data-selected="selectedEventKey ?? ''"
      :data-highlighted-media="highlightedMediaId ?? ''"
      :data-media-interaction-enabled="String(mediaInteractionEnabled)"
      :data-replay-enabled="String(replayEnabled)"
    >
      <button data-test="mini-select-event" @click="$emit('select-event', 7)">Select event</button>
      <button data-test="mini-clear-event" @click="$emit('select-event', null)">Clear event</button>
      <button data-test="mini-select-media" @click="$emit('select-media', 42)">Select photo</button>
      <button data-test="mini-clear-selection" @click="$emit('clear-selection')">Clear selection</button>
      <button data-test="mini-start-replay" @click="$emit('start-3d-replay')">3D Replay</button>
    </div>
  `,
});

function dispatchPointer(
  target: EventTarget,
  type: string,
  init: { pointerId?: number; pointerType?: string; clientX: number; clientY: number }
): PointerEvent {
  const event = new MouseEvent(type, {
    bubbles: true,
    cancelable: true,
    button: 0,
    clientX: init.clientX,
    clientY: init.clientY,
  }) as PointerEvent;
  Object.defineProperty(event, 'pointerId', { value: init.pointerId ?? 1 });
  Object.defineProperty(event, 'pointerType', { value: init.pointerType ?? 'mouse' });
  target.dispatchEvent(event);
  return event;
}

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
    '<div data-test="track-graph" :data-config-title="config && config.title" :data-point-count="trackDetails.length" :data-sync-enabled="String(syncEnabled)" :data-show-range="String(showRange)" />',
});

const TrackDetailPhotosStub = defineComponent({
  name: 'TrackDetailPhotos',
  props: {
    highlightedMediaId: Number,
    pageSize: Number,
    thumbnailsEnabled: Boolean,
  },
  emits: [
    'select-media',
    'highlight-media',
    'apply-offset',
    'save-time-correction',
    'save-manual-location',
    'clear-manual-location',
    'change-page',
    'change-page-size',
    'retry',
  ],
  template: `
    <div data-test="track-detail-photos">
      <button data-test="photo-highlight" @mouseenter="$emit('highlight-media', 42)">
        Highlight photo
      </button>
    </div>
  `,
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

function mockChartPoint(pointIndex: number): ChartPoint {
  return {
    pointIndex,
    pointTimestamp: new Date(`2026-01-01T10:00:${String(pointIndex).padStart(2, '0')}Z`),
    distanceInMeterSinceStart: pointIndex * 10,
    metricStats: {},
    pointAltitude: pointIndex,
    speedInKmhWindow: pointIndex,
    speedBucketAvgKmh: null,
    elevationGainPerHourWindow: null,
    elevationLossPerHourWindow: null,
    powerWattsWindow: null,
    energyCumulativeWh: null,
  };
}

async function mountTrackDetails(
  options: {
    chartSeries?: TrackChartSeries;
    detailTrackError?: unknown;
    chartDetailsError?: unknown;
    relatedTracksError?: unknown;
    trackMedia?: Array<Record<string, unknown>>;
    trackMediaPage?: {
      items: Array<Record<string, unknown>>;
      page: number;
      pageSize: number;
      totalItems: number;
      totalPages: number;
    };
    initialTab?: 'overview' | 'photos';
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
  mocks.getMediaByTrack.mockResolvedValue(
    options.trackMediaPage ?? {
      items: options.trackMedia ?? [],
      page: 0,
      pageSize: 100,
      totalItems: options.trackMedia?.length ?? 0,
      totalPages: options.trackMedia?.length ? 1 : 0,
    }
  );
  mocks.saveMediaTimeCorrections.mockResolvedValue(undefined);
  mocks.setManualMediaLocation.mockResolvedValue(undefined);
  mocks.clearManualMediaLocation.mockResolvedValue(undefined);

  const wrapper = mount(TrackDetails, {
    props: { gpsTrackId: 1, initialTab: options.initialTab },
    global: {
      stubs: {
        MtlSlider: true,
        BottomSheet: PassthroughStub,
        MediaPreview: true,
        Tab: PassthroughStub,
        TabList: PassthroughStub,
        TabPanel: PassthroughStub,
        TabPanels: PassthroughStub,
        Tabs: TabsStub,
        TrackDetailEvents: true,
        TrackDetailMiniMap: TrackDetailMiniMapStub,
        TrackDetailOverview: true,
        TrackDetailPhotos: TrackDetailPhotosStub,
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

  it('keeps tab-row taps intact while tracking resize drags outside the narrow zone', async () => {
    const wrapper = await mountTrackDetails();
    const tabResizeZone = wrapper.get('[data-test="track-detail-tab-resize-zone"]');
    const overviewTab = wrapper.get('[data-test="tab-overview"]');

    dispatchPointer(overviewTab.element, 'pointerdown', { clientX: 100, clientY: 100 });
    dispatchPointer(overviewTab.element, 'pointerup', { clientX: 100, clientY: 100 });

    expect(mocks.beginMiniMapResize).not.toHaveBeenCalled();
    expect(mocks.updateMiniMapResize).not.toHaveBeenCalled();
    expect(mocks.commitMiniMapResize).not.toHaveBeenCalled();

    dispatchPointer(tabResizeZone.element, 'pointerdown', {
      pointerId: 2,
      pointerType: 'touch',
      clientX: 100,
      clientY: 100,
    });
    const move = dispatchPointer(window, 'pointermove', {
      pointerId: 2,
      pointerType: 'touch',
      clientX: 102,
      clientY: 130,
    });
    dispatchPointer(window, 'pointerup', {
      pointerId: 2,
      pointerType: 'touch',
      clientX: 102,
      clientY: 130,
    });

    expect(move.defaultPrevented).toBe(true);
    expect(mocks.beginMiniMapResize).toHaveBeenCalledOnce();
    expect(mocks.updateMiniMapResize).toHaveBeenCalledWith(30);
    expect(mocks.commitMiniMapResize).toHaveBeenCalledOnce();
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
    expect(wrapper.get('[data-test="track-detail-load-error"]').text()).toContain('Back');

    await wrapper.get('[data-test="track-detail-back"]').trigger('click');
    expect(wrapper.emitted('back')).toEqual([[]]);

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

  it('keeps graph tuning controls collapsed until the mobile tuning button opens them', async () => {
    const wrapper = await mountTrackDetails();
    const tuningButton = wrapper.get('[data-test="graph-tuning-toggle"]');

    expect(tuningButton.attributes('aria-expanded')).toBe('false');
    expect(wrapper.get('.graphs-toolbar').classes()).not.toContain('graphs-toolbar--tuning-open');

    await tuningButton.trigger('click');
    await nextTick();

    expect(tuningButton.attributes('aria-expanded')).toBe('true');
    expect(wrapper.get('.graphs-toolbar').classes()).toContain('graphs-toolbar--tuning-open');
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
    const initialPoints = Array.from({ length: 10 }, (_, index) => mockChartPoint(index));
    const wrapper = await mountTrackDetails({ chartSeries: mockTrackChartSeries({ points: initialPoints }) });
    mocks.fetchTrackDetails.mockClear();
    mocks.fetchTrackDetails.mockResolvedValueOnce(
      mockTrackChartSeries({ points: [mockChartPoint(0), mockChartPoint(1), mockChartPoint(2)] })
    );

    await (
      wrapper.vm as unknown as { onChartPointCountSlideEnd: (event: { value: number }) => Promise<void> }
    ).onChartPointCountSlideEnd({ value: trackDetailsChartPointCountToSliderValue(1200) });
    await flushPromises();

    expect(readStoredTrackPreferences().chartPointCount).toBe(1200);
    expect(mocks.fetchTrackDetails).toHaveBeenCalledWith(1, XMode.Time, 1200);
    expect(wrapper.find('[data-test="track-graph"]').attributes('data-point-count')).toBe('3');
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

  it('lazy-loads activity photos only after the Photos tab is selected', async () => {
    const wrapper = await mountTrackDetails();

    expect(mocks.getMediaByTrack).not.toHaveBeenCalled();

    await wrapper.find('[data-test="tab-photos"]').trigger('click');
    await flushPromises();

    expect(mocks.getMediaByTrack).toHaveBeenCalledWith(1, 0, 0, 100, expect.any(AbortSignal));
    expect(wrapper.getComponent(TrackDetailPhotosStub).props('pageSize')).toBe(100);
  });

  it('loads a bounded 200-item page when the user selects the maximum activity-media page size', async () => {
    const wrapper = await mountTrackDetails({ initialTab: 'photos' });
    mocks.getMediaByTrack.mockClear();
    mocks.getMediaByTrack.mockResolvedValueOnce({
      items: Array.from({ length: 200 }, (_, index) => ({ id: index + 1 })),
      page: 0,
      pageSize: 200,
      totalItems: 100_000,
      totalPages: 500,
    });

    await (
      wrapper.vm as unknown as { onTrackMediaPageSizeChanged: (pageSize: number) => Promise<void> }
    ).onTrackMediaPageSizeChanged(200);

    expect(mocks.getMediaByTrack).toHaveBeenCalledWith(1, 0, 0, 200, expect.any(AbortSignal));
    expect(wrapper.getComponent(TrackDetailPhotosStub).props('pageSize')).toBe(200);
    expect((wrapper.vm as unknown as { trackMedia: Array<{ id: number }> }).trackMedia).toHaveLength(200);
  });

  it('cancels the activity-photo page request when leaving Photos', async () => {
    const wrapper = await mountTrackDetails();
    let requestSignal: AbortSignal | null = null;
    mocks.getMediaByTrack.mockImplementationOnce(
      (_trackId, _offsetSeconds, _page, _pageSize, signal: AbortSignal) =>
        new Promise((_resolve, reject) => {
          requestSignal = signal;
          signal.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')), { once: true });
        })
    );

    await wrapper.find('[data-test="tab-photos"]').trigger('click');
    await nextTick();
    expect(requestSignal?.aborted).toBe(false);

    await wrapper.find('[data-test="tab-overview"]').trigger('click');
    await flushPromises();

    expect(requestSignal?.aborted).toBe(true);
    expect((wrapper.vm as unknown as { trackMediaLoading: boolean }).trackMediaLoading).toBe(false);

    await wrapper.find('[data-test="tab-photos"]').trigger('click');
    await flushPromises();
    expect(mocks.getMediaByTrack).toHaveBeenCalledTimes(2);
  });

  it('opens directly on Photos when requested by the parent flow', async () => {
    const wrapper = await mountTrackDetails({ initialTab: 'photos' });

    expect(wrapper.get('[data-test="tabs"]').attributes('data-value')).toBe('4');
    expect(mocks.getMediaByTrack).toHaveBeenCalledWith(1, 0, 0, 100, expect.any(AbortSignal));
  });

  it('opens mini-map media only from Media and keeps it inert on Graphs', async () => {
    const wrapper = await mountTrackDetails({
      initialTab: 'photos',
      trackMedia: [{ id: 42, fileName: 'activity.mp4', mediaKind: 'VIDEO', resolvedLat: 47.4, resolvedLng: 8.5 }],
    });
    const miniMap = wrapper.get('[data-test="mini-map"]');

    expect(miniMap.attributes('data-media-interaction-enabled')).toBe('true');
    await wrapper.get('[data-test="mini-select-media"]').trigger('click');
    expect((wrapper.vm as unknown as { mediaPreviewVisible: boolean }).mediaPreviewVisible).toBe(true);

    (wrapper.vm as unknown as { mediaPreviewVisible: boolean }).mediaPreviewVisible = false;
    await wrapper.get('[data-test="tab-graphs"]').trigger('click');
    expect(miniMap.attributes('data-media-interaction-enabled')).toBe('false');

    await wrapper.get('[data-test="mini-select-media"]').trigger('click');
    expect((wrapper.vm as unknown as { activeTab: string }).activeTab).toBe('1');
    expect((wrapper.vm as unknown as { mediaPreviewVisible: boolean }).mediaPreviewVisible).toBe(false);
  });

  it('sends the selected photo coordinates to the main map flow', async () => {
    const wrapper = await mountTrackDetails({
      initialTab: 'photos',
      trackMedia: [{ id: 42, resolvedLat: 47.5605, resolvedLng: 8.505778 }],
    });

    (
      wrapper.vm as unknown as {
        onTrackMediaSelected: (mediaId: number) => void;
        openSelectedTrackMediaOnMap: () => void;
      }
    ).onTrackMediaSelected(42);
    (
      wrapper.vm as unknown as {
        openSelectedTrackMediaOnMap: () => void;
      }
    ).openSelectedTrackMediaOnMap();

    expect(wrapper.emitted('open-media-on-map')).toEqual([[{ id: 42, lat: 47.5605, lng: 8.505778 }]]);
  });

  it('pauses activity thumbnail sources while the photo viewer is open', async () => {
    const wrapper = await mountTrackDetails({
      initialTab: 'photos',
      trackMedia: [{ id: 42, resolvedLat: 47.5605, resolvedLng: 8.505778 }],
    });
    const photos = wrapper.getComponent(TrackDetailPhotosStub);
    expect(photos.props('thumbnailsEnabled')).toBe(true);

    (
      wrapper.vm as unknown as {
        onTrackMediaSelected: (mediaId: number) => void;
      }
    ).onTrackMediaSelected(42);
    await nextTick();

    expect(photos.props('thumbnailsEnabled')).toBe(false);
  });

  it('loads the new activity when track navigation occurs while Photos remains active', async () => {
    const wrapper = await mountTrackDetails();
    await wrapper.find('[data-test="tab-photos"]').trigger('click');
    await flushPromises();

    mocks.getMediaByTrack.mockClear();
    await wrapper.setProps({ gpsTrackId: 2 });
    await flushPromises();

    expect(mocks.getMediaByTrack).toHaveBeenCalledWith(2, 0, 0, 100, expect.any(AbortSignal));
  });

  it('loads only the requested activity-photo page', async () => {
    const wrapper = await mountTrackDetails({
      trackMediaPage: { items: [{ id: 11 }], page: 0, pageSize: 100, totalItems: 101, totalPages: 2 },
    });
    await wrapper.find('[data-test="tab-photos"]').trigger('click');
    await flushPromises();
    mocks.getMediaByTrack.mockClear();
    mocks.getMediaByTrack.mockResolvedValue({
      items: [{ id: 111 }],
      page: 1,
      pageSize: 100,
      totalItems: 101,
      totalPages: 2,
    });

    await (
      wrapper.vm as unknown as {
        onTrackMediaPageChanged: (page: number) => Promise<void>;
      }
    ).onTrackMediaPageChanged(1);

    expect(mocks.getMediaByTrack).toHaveBeenCalledWith(1, 0, 1, 100, expect.any(AbortSignal));
  });

  it('loads the next activity-photo page when viewer navigation reaches the page boundary', async () => {
    const firstPage = Array.from({ length: 100 }, (_, index) => ({ id: index + 1 }));
    const wrapper = await mountTrackDetails({
      initialTab: 'photos',
      trackMediaPage: { items: firstPage, page: 0, pageSize: 100, totalItems: 101, totalPages: 2 },
    });
    (
      wrapper.vm as unknown as {
        onTrackMediaSelected: (mediaId: number) => void;
      }
    ).onTrackMediaSelected(100);
    mocks.getMediaByTrack.mockClear();
    mocks.getMediaByTrack.mockResolvedValue({
      items: [{ id: 101 }],
      page: 1,
      pageSize: 100,
      totalItems: 101,
      totalPages: 2,
    });

    await (
      wrapper.vm as unknown as {
        navigateTrackMediaRelative: (direction: -1 | 1) => Promise<void>;
      }
    ).navigateTrackMediaRelative(1);

    expect(mocks.getMediaByTrack).toHaveBeenCalledWith(1, 0, 1, 100, expect.any(AbortSignal));
    expect((wrapper.vm as unknown as { selectedTrackMediaId: number | null }).selectedTrackMediaId).toBe(101);
  });

  it('reloads Photos with the applied camera-clock offset', async () => {
    const wrapper = await mountTrackDetails();
    await wrapper.find('[data-test="tab-photos"]').trigger('click');
    await flushPromises();
    mocks.getMediaByTrack.mockClear();

    await (wrapper.vm as unknown as { onPhotoOffsetApplied: (seconds: number) => Promise<void> }).onPhotoOffsetApplied(
      3600
    );

    expect(mocks.getMediaByTrack).toHaveBeenCalledWith(1, 3600, 0, 100, expect.any(AbortSignal));
  });

  it('persists a previewed correction and returns to the baseline lookup', async () => {
    const wrapper = await mountTrackDetails({
      trackMedia: [{ id: 11, timeSource: 'EXIF_DATE_TAKEN' }],
    });
    await wrapper.find('[data-test="tab-photos"]').trigger('click');
    await flushPromises();
    mocks.getMediaByTrack.mockClear();

    await (
      wrapper.vm as unknown as {
        onSaveTimeCorrection: (ids: number[], seconds: number) => Promise<void>;
      }
    ).onSaveTimeCorrection([42], 3600);

    expect(mocks.saveMediaTimeCorrections).toHaveBeenCalledWith({ mediaIds: [42, 11], offsetSeconds: 3600 });
    expect(mocks.getMediaByTrack).toHaveBeenCalledWith(1, 0, 0, 100, expect.any(AbortSignal));
  });

  it('uses the correlated route point when resolved media coordinates are unavailable', async () => {
    const wrapper = await mountTrackDetails({
      initialTab: 'photos',
      trackMedia: [{ id: 42, positionOrigin: null, routeLat: 47.4, routeLng: 8.5 }],
    });

    (
      wrapper.vm as unknown as {
        onTrackMediaSelected: (mediaId: number) => void;
        openSelectedTrackMediaOnMap: () => void;
      }
    ).onTrackMediaSelected(42);
    await nextTick();

    const preview = wrapper.getComponent({ name: 'MediaPreview' });
    expect(preview.props('positionUnknown')).toBe(true);
    expect(preview.props('positionLat')).toBe(47.4);
    expect(preview.props('positionLng')).toBe(8.5);

    (
      wrapper.vm as unknown as {
        openSelectedTrackMediaOnMap: () => void;
      }
    ).openSelectedTrackMediaOnMap();
    expect(wrapper.emitted('open-media-on-map')).toEqual([[{ id: 42, lat: 47.4, lng: 8.5 }]]);
  });

  it('closes the viewer when clearing its correction moves the media outside the activity', async () => {
    const wrapper = await mountTrackDetails({
      initialTab: 'photos',
      trackMedia: [{ id: 42, appliedCameraOffsetSeconds: 900 }],
    });
    (
      wrapper.vm as unknown as {
        onTrackMediaSelected: (mediaId: number) => void;
      }
    ).onTrackMediaSelected(42);
    mocks.getMediaByTrack.mockClear();
    mocks.getMediaByTrack.mockResolvedValueOnce({
      items: [],
      page: 0,
      pageSize: 100,
      totalItems: 0,
      totalPages: 0,
    });

    await (
      wrapper.vm as unknown as {
        onViewerTimeCorrectionCleared: () => Promise<void>;
      }
    ).onViewerTimeCorrectionCleared();

    expect(mocks.getMediaByTrack).toHaveBeenCalledWith(1, 0, 0, 100, expect.any(AbortSignal));
    expect((wrapper.vm as unknown as { selectedTrackMediaId: number | null }).selectedTrackMediaId).toBeNull();
    expect((wrapper.vm as unknown as { mediaPreviewVisible: boolean }).mediaPreviewVisible).toBe(false);
  });

  it('persists and clears a user-assigned photo location', async () => {
    const wrapper = await mountTrackDetails();
    await wrapper.find('[data-test="tab-photos"]').trigger('click');
    await flushPromises();

    await (
      wrapper.vm as unknown as {
        onSaveManualLocation: (id: number, lat: number, lng: number, note?: string) => Promise<void>;
        onClearManualLocation: (id: number) => Promise<void>;
      }
    ).onSaveManualLocation(42, 47.4, 8.5, 'Trail');
    await (
      wrapper.vm as unknown as {
        onClearManualLocation: (id: number) => Promise<void>;
      }
    ).onClearManualLocation(42);

    expect(mocks.setManualMediaLocation).toHaveBeenCalledWith(42, {
      latitude: 47.4,
      longitude: 8.5,
      note: 'Trail',
    });
    expect(mocks.clearManualMediaLocation).toHaveBeenCalledWith(42);
  });

  it('selecting a minimap event moves to Events and stores the selected key', async () => {
    const wrapper = await mountTrackDetails();

    await wrapper.find('[data-test="mini-select-event"]').trigger('click');

    expect((wrapper.vm as unknown as { activeTab: string }).activeTab).toBe('5');
    expect((wrapper.vm as unknown as { selectedTrackEventKey: number | null }).selectedTrackEventKey).toBe(7);
    expect(wrapper.find('[data-test="mini-map"]').attributes('data-selected')).toBe('7');
  });

  it('keeps photo hover state until the mini-map clears it', async () => {
    const wrapper = await mountTrackDetails();
    const highlight = wrapper.get('[data-test="photo-highlight"]');

    await highlight.trigger('mouseenter');
    expect(wrapper.get('[data-test="mini-map"]').attributes('data-highlighted-media')).toBe('42');

    await highlight.trigger('mouseleave');
    expect(wrapper.get('[data-test="mini-map"]').attributes('data-highlighted-media')).toBe('42');

    await wrapper.get('[data-test="mini-clear-selection"]').trigger('click');
    expect(wrapper.get('[data-test="mini-map"]').attributes('data-highlighted-media')).toBe('');
  });

  it('clears photo and event selection when the mini-map background is clicked', async () => {
    const wrapper = await mountTrackDetails({
      initialTab: 'photos',
      trackMedia: [{ id: 42, resolvedLat: 47.4, resolvedLng: 8.5 }],
    });

    (
      wrapper.vm as unknown as {
        onTrackMediaSelected: (mediaId: number) => void;
      }
    ).onTrackMediaSelected(42);
    await wrapper.get('[data-test="photo-highlight"]').trigger('mouseenter');
    await wrapper.get('[data-test="mini-clear-selection"]').trigger('click');

    expect((wrapper.vm as unknown as { selectedTrackMediaId: number | null }).selectedTrackMediaId).toBeNull();
    expect((wrapper.vm as unknown as { highlightedTrackMediaId: number | null }).highlightedTrackMediaId).toBeNull();
    expect((wrapper.vm as unknown as { selectedTrackEventKey: number | null }).selectedTrackEventKey).toBeNull();
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
