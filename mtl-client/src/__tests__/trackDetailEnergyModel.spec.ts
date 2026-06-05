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

const DialogStub = defineComponent({
  name: 'Dialog',
  props: {
    visible: { type: Boolean, default: false },
  },
  emits: ['update:visible'],
  template: '<div v-if="visible" data-test="dialog"><slot /></div>',
});

const PopoverStub = defineComponent({
  name: 'Popover',
  template: '<div data-test="popover"><slot /></div>',
});

function energyResponse(totalWh: number, deltaWh: number, riderWeightKg: number) {
  return {
    gpsTrackId: 1,
    baselineRiderWeightKg: 75,
    requestedRiderWeightKg: riderWeightKg,
    baselineWeightKgUsed: 85,
    adjustedWeightKgUsed: riderWeightKg + 10,
    baselineSummary: {
      netEnergyTotalWh: 230,
      powerWattsAvg: 128,
      weightKgUsed: 85,
    },
    adjustedSummary: {
      netEnergyTotalWh: totalWh,
      powerWattsAvg: 136,
      weightKgUsed: riderWeightKg + 10,
    },
    deltaSummary: {
      netEnergyTotalWh: deltaWh,
      powerWattsAvg: 8,
      weightKgUsed: riderWeightKg - 75,
    },
  };
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((promiseResolve, promiseReject) => {
    resolve = promiseResolve;
    reject = promiseReject;
  });
  return { promise, resolve, reject };
}

function baseTrack() {
  return {
    id: 1,
    trackName: 'Morning Ride',
    indexedFile: {
      id: 7,
      index: 'GPS',
      name: 'ride.gpx',
      path: 'ride.gpx',
      indexerStatus: 'COMPLETED_WITH_SUCCESS',
    },
    activityType: GpsTrackActivityTypeEnum.Bicycle,
    startDate: new Date('2026-01-01T08:00:00Z'),
    endDate: new Date('2026-01-01T09:00:00Z'),
    trackLengthInMeter: 25_000,
    ascentInMeter: 400,
    descentInMeter: -350,
    energyNetTotalWh: 230,
    energyWeightKgUsed: 85,
    energyGravitationalTotalWh: 100,
    energyRollingResistanceTotalWh: 50,
    energyKineticPositiveTotalWh: 20,
    powerWattsAvg: 120,
    powerWatts30sMax: 260,
  };
}

function mountEnergyOverview() {
  return mount(TrackDetailOverview, {
    props: {
      gpsTrack: baseTrack(),
      trackDetails: [{ x: 0, y: 0 }],
    },
    global: {
      directives: { tooltip: {} },
      stubs: {
        ActivityTypeBadge: ActivityTypeBadgeStub,
        Dialog: DialogStub,
        Popover: PopoverStub,
      },
    },
  });
}

describe('Track Detail energy adjustment', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('opens a compact adjustment dialog and previews rider-weight changes through the server', async () => {
    vi.useFakeTimers();
    mocks.calculateEnergyWhatIf.mockResolvedValueOnce(energyResponse(230, 0, 75));
    const wrapper = mountEnergyOverview();
    await nextTick();

    expect(wrapper.find('[data-test="energy-adjust-trigger"]').exists()).toBe(true);
    expect(wrapper.find('[data-test="energy-model-card"]').exists()).toBe(false);
    expect(wrapper.text()).not.toContain('Energy model');
    expect(wrapper.text()).not.toContain('Mass Used');

    await wrapper.find('[data-test="energy-adjust-trigger"]').trigger('click');
    await flushPromises();
    await nextTick();

    expect(mocks.calculateEnergyWhatIf).toHaveBeenNthCalledWith(1, 1, undefined);
    expect(wrapper.find<HTMLInputElement>('[data-test="energy-rider-weight-input"]').element.value).toBe('75');

    mocks.calculateEnergyWhatIf.mockResolvedValueOnce(energyResponse(242.5, 12.5, 82));
    await wrapper.find('[data-test="energy-rider-weight-input"]').setValue('82');
    await vi.advanceTimersByTimeAsync(250);
    await flushPromises();
    await nextTick();

    expect(mocks.calculateEnergyWhatIf).toHaveBeenNthCalledWith(2, 1, 82);
    expect(wrapper.find('[data-test="energy-adjust-results"]').text()).toContain('242.5 Wh');
    expect(wrapper.find('[data-test="energy-adjust-results"]').text()).toContain('+12.5 Wh');
    expect(wrapper.find('[data-test="energy-adjust-results"]').text()).toContain('136 W');
  });

  it('keeps the slider stable while stale previews are still returning', async () => {
    vi.useFakeTimers();
    const preview82 = deferred<ReturnType<typeof energyResponse>>();
    const preview83 = deferred<ReturnType<typeof energyResponse>>();
    mocks.calculateEnergyWhatIf
      .mockResolvedValueOnce(energyResponse(230, 0, 75))
      .mockReturnValueOnce(preview82.promise)
      .mockReturnValueOnce(preview83.promise);
    const wrapper = mountEnergyOverview();
    await nextTick();

    await wrapper.find('[data-test="energy-adjust-trigger"]').trigger('click');
    await flushPromises();
    await nextTick();

    await wrapper.find('[data-test="energy-rider-weight-input"]').setValue('82');
    await vi.advanceTimersByTimeAsync(250);
    await flushPromises();
    expect(mocks.calculateEnergyWhatIf).toHaveBeenNthCalledWith(2, 1, 82);

    await wrapper.find('[data-test="energy-rider-weight-input"]').setValue('83');
    preview82.resolve(energyResponse(242.5, 12.5, 82));
    await flushPromises();
    await nextTick();

    expect(wrapper.find<HTMLInputElement>('[data-test="energy-rider-weight-input"]').element.value).toBe('83');
    expect(wrapper.find('[data-test="energy-adjust-results"]').text()).not.toContain('242.5 Wh');

    await vi.advanceTimersByTimeAsync(250);
    await flushPromises();
    expect(mocks.calculateEnergyWhatIf).toHaveBeenNthCalledWith(3, 1, 83);

    preview83.resolve(energyResponse(244, 14, 83));
    await flushPromises();
    await nextTick();

    expect(wrapper.find('[data-test="energy-adjust-results"]').text()).toContain('244.0 Wh');
    expect(wrapper.find<HTMLInputElement>('[data-test="energy-rider-weight-input"]').element.value).toBe('83');
  });

  it('saves the adjusted rider weight for the current track only', async () => {
    mocks.calculateEnergyWhatIf.mockResolvedValueOnce(energyResponse(230, 0, 75));
    mocks.saveTrackEnergyRiderWeight.mockResolvedValueOnce({
      ...baseTrack(),
      energyNetTotalWh: 242.5,
      energyWeightKgUsed: 92,
    });
    const wrapper = mountEnergyOverview();
    await nextTick();

    await wrapper.find('[data-test="energy-adjust-trigger"]').trigger('click');
    await flushPromises();
    await nextTick();

    await wrapper.find('[data-test="energy-rider-weight-input"]').setValue('82');
    await wrapper.find('form[data-test="energy-adjust-dialog"]').trigger('submit');
    await flushPromises();
    await nextTick();

    expect(mocks.saveTrackEnergyRiderWeight).toHaveBeenCalledWith(1, 82);
    expect(wrapper.emitted('track-updated')?.[0]?.[0]).toMatchObject({
      id: 1,
      energyNetTotalWh: 242.5,
      energyWeightKgUsed: 92,
    });
  });
});
