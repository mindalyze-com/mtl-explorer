import { flushPromises, mount } from '@vue/test-utils';
import { defineComponent, nextTick } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import TrackDetailOverview from '@/components/trackdetails/TrackDetailOverview.vue';
import { GpsTrackActivityTypeEnum } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';

const mocks = vi.hoisted(() => ({
  calculateEnergyWhatIf: vi.fn(),
  downloadTrackGpx: vi.fn(),
  downloadTrackSourceFile: vi.fn(),
  saveTrackEnergyRiderWeight: vi.fn(),
}));

vi.mock('@/utils/ServiceHelper', () => ({
  calculateEnergyWhatIf: mocks.calculateEnergyWhatIf,
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
    expect(primaryValues).toHaveLength(4);
    expect(primaryValues.every(Boolean)).toBe(true);
    expect(primaryValues[1]).toBe('0m 00s');
    expect(primaryValues[2]).toMatch(/^0 (m|ft)$/);
    expect(primaryValues[3]).toMatch(/^0\.0 (km\/h|mph)$/);
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
    const wrapper = mountOverview('ride.fit');

    expect(wrapper.find('button[aria-label="Download original"]').exists()).toBe(true);
    expect(wrapper.find('button[aria-label="Download GPX"]').exists()).toBe(true);
  });

  it('hides the GPX action for native GPX sources', () => {
    const wrapper = mountOverview('ride.gpx');

    expect(wrapper.find('button[aria-label="Download original"]').exists()).toBe(true);
    expect(wrapper.find('button[aria-label="Download GPX"]').exists()).toBe(false);
  });

  it('downloads the original file and disables actions while pending', async () => {
    let resolveDownload: () => void = () => undefined;
    mocks.downloadTrackSourceFile.mockReturnValueOnce(new Promise<void>((resolve) => (resolveDownload = resolve)));
    const wrapper = mountOverview('ride.fit');

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
    const wrapper = mountOverview('ride.fit');

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
    const wrapper = mountOverview('ride.fit', toastAdd);

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
