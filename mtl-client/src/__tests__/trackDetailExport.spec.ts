import { flushPromises, mount } from '@vue/test-utils';
import { defineComponent, nextTick } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import TrackDetailOverview from '@/components/trackdetails/TrackDetailOverview.vue';
import TrackDetailActions from '@/components/trackdetails/TrackDetailActions.vue';
import { formatDistanceSmart, formatDurationSmart, formatSpeed } from '@/utils/Utils';
import { GpsTrackActivityTypeEnum } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';

const mocks = vi.hoisted(() => ({
  calculateEnergyWhatIf: vi.fn(),
  downloadTrackGpx: vi.fn(),
  downloadTrackSourceFile: vi.fn(),
  saveTrackEnergyRiderWeight: vi.fn(),
}));

vi.mock('@/utils/ServiceHelper', () => ({
  calculateEnergyWhatIf: mocks.calculateEnergyWhatIf,
  getEnergyCalculationStatus: vi.fn().mockResolvedValue({ state: 'CALCULATED' }),
  refreshTrackEnergy: vi.fn(),
  downloadTrackGpx: mocks.downloadTrackGpx,
  downloadTrackSourceFile: mocks.downloadTrackSourceFile,
  saveTrackEnergyRiderWeight: mocks.saveTrackEnergyRiderWeight,
}));

const ActivityTypeBadgeStub = defineComponent({
  name: 'ActivityTypeBadge',
  template: '<span data-test="activity-badge" />',
});

const PopoverStub = defineComponent({
  name: 'Popover',
  template: '<div data-test="popover"><slot /></div>',
});

function mountOverview(fileName: string, toastAdd = vi.fn()) {
  return mount(TrackDetailOverview, {
    props: {
      gpsTrack: {
        id: 1,
        trackName: 'Morning Ride',
        indexedFile: {
          id: 7,
          index: 'GPS',
          name: fileName,
          path: fileName,
          indexerStatus: 'COMPLETED_WITH_SUCCESS',
        },
        startDate: new Date('2026-01-01T08:00:00Z'),
        endDate: new Date('2026-01-01T09:00:00Z'),
        trackLengthInMeter: 25_000,
        activityType: GpsTrackActivityTypeEnum.Bicycle,
      },
      trackDetails: [{ x: 0, y: 0 }],
    },
    global: {
      directives: { tooltip: {} },
      provide: {
        toast: { add: toastAdd },
      },
      stubs: {
        ActivityTypeBadge: ActivityTypeBadgeStub,
        Popover: PopoverStub,
      },
    },
  });
}

function mountActions(fileName: string, toastAdd = vi.fn()) {
  return mount(TrackDetailActions, {
    props: {
      gpsTrack: {
        id: 1,
        indexedFile: { id: 7, index: 'GPS', name: fileName, path: fileName, indexerStatus: 'COMPLETED_WITH_SUCCESS' },
      },
      replayEnabled: true,
    },
    global: { provide: { toast: { add: toastAdd } } },
  });
}

describe('Track Detail original and GPX export', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('renders TrackID as a selectable readonly copy field', () => {
    const wrapper = mountOverview('ride.fit');
    const input = wrapper.find<HTMLInputElement>('input[aria-label="TrackID"]');

    expect(input.exists()).toBe(true);
    expect(input.element.readOnly).toBe(true);
    expect(input.element.value).toBe('1');
  });

  it('renders settled fallback metrics when chart points and timestamps are unavailable', () => {
    const wrapper = mount(TrackDetailOverview, {
      props: {
        gpsTrack: {
          id: 2,
          trackName: 'Untimed GeoJSON',
          indexedFile: {
            id: 8,
            index: 'GPS',
            name: 'untimed.geojson',
            path: 'untimed.geojson',
            indexerStatus: 'COMPLETED_WITH_SUCCESS',
          },
          trackLengthInMeter: 1_440,
        },
        trackDetails: [],
      },
      global: {
        directives: { tooltip: {} },
        stubs: {
          ActivityTypeBadge: ActivityTypeBadgeStub,
          Popover: PopoverStub,
        },
      },
    });

    expect(wrapper.find('.skeleton-grid').exists()).toBe(false);
    const primaryValues = wrapper.findAll('.metrics-primary .metric-tile__value').map((value) => value.text());
    expect(primaryValues).toHaveLength(1);
    expect(primaryValues[0]).toBe(formatDistanceSmart(1440));
    expect(wrapper.find('.metrics-primary').text()).not.toContain('—');
    expect(wrapper.text()).toContain('Elevation data is unavailable for this track.');
  });

  it('keeps a valid zero overall speed visible for a timed stationary track', async () => {
    const wrapper = mountOverview('stationary.gpx');
    await wrapper.setProps({
      gpsTrack: {
        ...wrapper.props('gpsTrack'),
        trackLengthInMeter: 0,
        startDate: new Date('2026-10-02T09:00:00Z'),
        endDate: new Date('2026-10-02T10:00:00Z'),
      },
    });

    const speedTile = wrapper
      .findAll('.metrics-primary .metric-tile')
      .find((tile) => tile.find('.metric-tile__label').text() === 'Overall avg speed');
    expect(speedTile?.find('.metric-tile__value').text()).toBe(formatSpeed(0, 1));
  });

  it('keeps a known zero duration visible without inventing an average speed', async () => {
    const wrapper = mountOverview('single-time.gpx');
    const timestamp = new Date('2026-10-02T09:00:00Z');
    await wrapper.setProps({
      gpsTrack: { ...wrapper.props('gpsTrack'), startDate: timestamp, endDate: timestamp },
    });

    const tiles = wrapper.findAll('.metrics-primary .metric-tile');
    const durationTile = tiles.find((tile) => tile.find('.metric-tile__label').text() === 'Duration');
    expect(durationTile?.find('.metric-tile__value').text()).toBe(formatDurationSmart(0));
    expect(tiles.some((tile) => tile.find('.metric-tile__label').text() === 'Overall avg speed')).toBe(false);
  });

  it('shows Google Health heart-rate statistics for the track interval', async () => {
    const wrapper = mountOverview('ride.fit');
    await wrapper.setProps({
      trackHealth: {
        trackId: 1,
        available: true,
        source: 'GOOGLE_HEALTH',
        heartRate: {
          sampleCount: 180,
          averageBpm: 122.4,
          movingAverageBpm: 135.6,
          movingSampleCount: 120,
          minimumBpm: 78,
          maximumBpm: 167,
          points: [],
        },
      },
    });

    const summary = wrapper.get('[data-test="track-health-summary"]');
    expect(summary.text()).toContain('122 bpm');
    expect(summary.get('[data-test="moving-heart-rate"]').text()).toContain('136 bpm');
    expect(summary.get('[data-test="moving-heart-rate"]').text()).toContain('Average while moving');
    expect(summary.text()).toContain('167 bpm');
    expect(summary.text()).toContain('78 bpm');
    expect(summary.text()).toContain('180');
    expect(summary.text()).toContain('Google Health samples recorded during this track.');
    expect(summary.text()).toContain('Minimum, maximum and overall average include breaks.');
  });

  it('does not substitute the overall average when moving samples are unavailable', async () => {
    const wrapper = mountOverview('ride.fit');
    await wrapper.setProps({
      trackHealth: {
        available: true,
        heartRate: { averageBpm: 122, movingSampleCount: 0, points: [] },
      },
    });

    const moving = wrapper.get('[data-test="moving-heart-rate"]');
    expect(moving.text()).toContain('—');
    expect(moving.text()).toContain('No matching moving samples');
    expect(moving.text()).not.toContain('122 bpm');
  });

  it('copies TrackID through the browser clipboard API', async () => {
    vi.useFakeTimers();
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText },
      configurable: true,
    });
    const wrapper = mountOverview('ride.fit');

    await wrapper.find('button[aria-label="Copy TrackID"]').trigger('click');
    await flushPromises();

    expect(writeText).toHaveBeenCalledWith('1');
    expect(wrapper.find('button[aria-label="TrackID copied"]').exists()).toBe(true);

    vi.runOnlyPendingTimers();
    await nextTick();

    expect(wrapper.find('button[aria-label="Copy TrackID"]').exists()).toBe(true);
  });

  it('falls back when the browser clipboard API rejects TrackID copy', async () => {
    vi.useFakeTimers();
    const writeText = vi.fn().mockRejectedValue(new Error('clipboard denied'));
    const execCommand = vi.fn().mockReturnValue(true);
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText },
      configurable: true,
    });
    Object.defineProperty(document, 'execCommand', {
      value: execCommand,
      configurable: true,
    });
    const wrapper = mountOverview('ride.fit');

    await wrapper.find('button[aria-label="Copy TrackID"]').trigger('click');
    await flushPromises();

    expect(writeText).toHaveBeenCalledWith('1');
    expect(execCommand).toHaveBeenCalledWith('copy');
    expect(wrapper.find('button[aria-label="TrackID copied"]').exists()).toBe(true);
  });

  it('shows original and GPX download actions for converted source formats', () => {
    const wrapper = mountActions('ride.fit');

    expect(wrapper.find('button[aria-label="Download original"]').exists()).toBe(true);
    expect(wrapper.find('button[aria-label="Download GPX"]').exists()).toBe(true);
  });

  it('hides the GPX action for native GPX sources', () => {
    const wrapper = mountActions('ride.gpx');

    expect(wrapper.find('button[aria-label="Download original"]').exists()).toBe(true);
    expect(wrapper.find('button[aria-label="Download GPX"]').exists()).toBe(false);
  });

  it('downloads the original file and disables actions while pending', async () => {
    let resolveDownload: () => void = () => undefined;
    mocks.downloadTrackSourceFile.mockReturnValueOnce(new Promise<void>((resolve) => (resolveDownload = resolve)));
    const wrapper = mountActions('ride.fit');

    await wrapper.find('button[aria-label="Download original"]').trigger('click');
    await nextTick();

    expect(mocks.downloadTrackSourceFile).toHaveBeenCalledWith(1, 'ride.fit');
    expect(wrapper.find('button[aria-label="Download original"]').attributes('disabled')).toBeDefined();
    expect(wrapper.find('button[aria-label="Download GPX"]').attributes('disabled')).toBeDefined();

    resolveDownload();
    await flushPromises();

    expect(wrapper.find('button[aria-label="Download original"]').attributes('disabled')).toBeUndefined();
  });

  it('does not carry a pending download into the next track', async () => {
    let resolveFirstDownload: () => void = () => undefined;
    let resolveSecondDownload: () => void = () => undefined;
    mocks.downloadTrackSourceFile
      .mockReturnValueOnce(new Promise<void>((resolve) => (resolveFirstDownload = resolve)))
      .mockReturnValueOnce(new Promise<void>((resolve) => (resolveSecondDownload = resolve)));
    const wrapper = mountActions('ride.fit');

    await wrapper.find('button[aria-label="Download original"]').trigger('click');
    await wrapper.setProps({
      gpsTrack: {
        ...wrapper.props('gpsTrack'),
        id: 2,
        indexedFile: {
          ...wrapper.props('gpsTrack')?.indexedFile,
          name: 'sample.igc',
          path: 'sample.igc',
        },
      },
    });

    expect(wrapper.find('button[aria-label="Download original"]').attributes('disabled')).toBeUndefined();
    expect(wrapper.find('button[aria-label="Download GPX"]').attributes('disabled')).toBeUndefined();

    await wrapper.find('button[aria-label="Download original"]').trigger('click');
    await nextTick();

    expect(mocks.downloadTrackSourceFile).toHaveBeenNthCalledWith(2, 2, 'sample.igc');

    resolveFirstDownload();
    await flushPromises();

    expect(wrapper.find('button[aria-label="Download original"]').attributes('disabled')).toBeDefined();

    resolveSecondDownload();
    await flushPromises();

    expect(wrapper.find('button[aria-label="Download original"]').attributes('disabled')).toBeUndefined();
  });

  it('shows a toast when GPX export fails', async () => {
    const toastAdd = vi.fn();
    mocks.downloadTrackGpx.mockRejectedValueOnce(new Error('failed'));
    const wrapper = mountActions('ride.fit', toastAdd);

    await wrapper.find('button[aria-label="Download GPX"]').trigger('click');
    await flushPromises();

    expect(mocks.downloadTrackGpx).toHaveBeenCalledWith(1, 'ride.fit');
    expect(toastAdd).toHaveBeenCalledWith(
      expect.objectContaining({
        severity: 'error',
        summary: 'Download failed',
        detail: 'Could not download GPX.',
      })
    );
  });
});
