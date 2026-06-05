import { flushPromises, mount } from '@vue/test-utils';
import { defineComponent, nextTick } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import TrackDetailOverview from '@/components/trackdetails/TrackDetailOverview.vue';
import {
  GpsTrackActivityTypeEnum,
  GpsTrackStatisticsExclusionReasonEnum,
} from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';

const mocks = vi.hoisted(() => ({
  calculateEnergyWhatIf: vi.fn(),
  downloadTrackGpx: vi.fn(),
  downloadTrackSourceFile: vi.fn(),
  saveTrackEnergyRiderWeight: vi.fn(),
  updateTrackActivityType: vi.fn(),
  updateTrackStatisticsExclusion: vi.fn(),
}));

vi.mock('@/utils/ServiceHelper', () => ({
  calculateEnergyWhatIf: mocks.calculateEnergyWhatIf,
  downloadTrackGpx: mocks.downloadTrackGpx,
  downloadTrackSourceFile: mocks.downloadTrackSourceFile,
  saveTrackEnergyRiderWeight: mocks.saveTrackEnergyRiderWeight,
  updateTrackActivityType: mocks.updateTrackActivityType,
  updateTrackStatisticsExclusion: mocks.updateTrackStatisticsExclusion,
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

  it('updates activity type from the overview controls', async () => {
    mocks.updateTrackActivityType.mockResolvedValueOnce({
      id: 1,
      activityType: GpsTrackActivityTypeEnum.Running,
      indexedFile: { index: 'GPS', name: 'ride.fit', path: 'ride.fit' },
    });
    const wrapper = mountOverview('ride.fit');

    await wrapper.find('[data-test="overview-activity-type-select"]').setValue(GpsTrackActivityTypeEnum.Running);
    await flushPromises();

    expect(mocks.updateTrackActivityType).toHaveBeenCalledWith(1, GpsTrackActivityTypeEnum.Running);
    expect(wrapper.emitted('track-updated')?.[0]?.[0]).toEqual(expect.objectContaining({ activityType: 'RUNNING' }));
  });

  it('updates statistics exclusion from the overview controls', async () => {
    mocks.updateTrackStatisticsExclusion.mockResolvedValueOnce({
      id: 1,
      activityType: GpsTrackActivityTypeEnum.Bicycle,
      statisticsExclusionReason: GpsTrackStatisticsExclusionReasonEnum.WrongActivity,
      indexedFile: { index: 'GPS', name: 'ride.fit', path: 'ride.fit' },
    });
    const wrapper = mountOverview('ride.fit');

    await wrapper
      .find('[data-test="overview-statistics-exclusion-select"]')
      .setValue(GpsTrackStatisticsExclusionReasonEnum.WrongActivity);
    await flushPromises();

    expect(mocks.updateTrackStatisticsExclusion).toHaveBeenCalledWith(1, {
      highlightExclusionReason: undefined,
      statisticsExclusionReason: GpsTrackStatisticsExclusionReasonEnum.WrongActivity,
    });
    expect(wrapper.emitted('track-updated')?.[0]?.[0]).toEqual(
      expect.objectContaining({ statisticsExclusionReason: 'WRONG_ACTIVITY' })
    );
  });
});
