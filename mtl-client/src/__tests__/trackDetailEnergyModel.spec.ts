import { flushPromises, mount } from '@vue/test-utils';
import { defineComponent, nextTick } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import TrackDetailOverview from '@/components/trackdetails/TrackDetailOverview.vue';
import { GpsTrackActivityTypeEnum } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import { useMeasurementSystem } from '@/composables/useMeasurementSystem';

const measurementPreference = useMeasurementSystem();

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
  getEnergyCalculationStatus: vi.fn().mockResolvedValue({ state: 'CALCULATED' }),
  refreshTrackEnergy: vi.fn(),
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
      metabolicEnergy: { activeKcal: 840, totalKcal: 900, coverage: 'COMPLETE' },
      powerWattsAvg: 136,
      weightKgUsed: riderWeightKg + 10,
    },
    activeCaloriesDelta: 40,
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
    measurementPreference.setMeasurementSystem('METRIC');
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('keeps the summary visible while details start closed, and resets disclosure on another track', async () => {
    const wrapper = mountEnergyOverview();
    const disclosure = wrapper.get<HTMLDetailsElement>('[data-test="energy-details"]');
    expect(disclosure.element.open).toBe(false);
    expect(wrapper.get('[data-test="energy-summary"]').text()).toContain('230.0 Wh');
    expect(wrapper.get('[data-test="energy-summary"]').find('details').exists()).toBe(false);
    expect(disclosure.get('[data-test="energy-adjust-trigger"]').exists()).toBe(true);
    expect(disclosure.get('[data-test="metabolic-energy"]').exists()).toBe(true);
    disclosure.element.open = true;
    await wrapper.setProps({ gpsTrack: { ...baseTrack(), id: 2 } });
    expect(wrapper.get<HTMLDetailsElement>('[data-test="energy-details"]').element.open).toBe(false);
  });

  it('keeps partial coverage and valid zero calories in the compact summary', async () => {
    const wrapper = mountEnergyOverview();
    await wrapper.setProps({
      gpsTrack: {
        ...baseTrack(),
        metabolicEnergy: { activeKcal: 0, totalKcal: 10, coverage: 'PARTIAL', coveredSeconds: 600 },
      },
    });
    expect(wrapper.get('[data-test="active-calories"]').text()).toContain('0 kcal');
    expect(wrapper.get('[data-test="calorie-partial"]').text()).toContain('10m 00s covered');
    expect(wrapper.get('[data-test="calorie-partial"]').element.closest('details')).toBeNull();
  });

  it('distinguishes pending and unavailable calories in the compact summary', async () => {
    const wrapper = mountEnergyOverview();
    await flushPromises();
    expect(wrapper.get('[data-test="energy-summary"]').text()).toContain('Unavailable');
    await wrapper.setProps({ gpsTrack: { ...baseTrack(), metabolicEnergy: { coverage: 'UNAVAILABLE' } } });
    expect(wrapper.get('[data-test="energy-summary"]').text()).toContain('Unavailable');
    expect(wrapper.find('[data-test="active-calories"]').exists()).toBe(false);
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
    expect(wrapper.get('[data-test="calorie-preview"]').text()).toContain('840 kcal');
    expect(wrapper.get('[data-test="calorie-preview"]').text()).toContain('+40 kcal change');
  });

  it('explains an unavailable calorie estimate in the weight preview', async () => {
    const response = energyResponse(230, 0, 75);
    mocks.calculateEnergyWhatIf.mockResolvedValueOnce({
      ...response,
      adjustedSummary: {
        ...response.adjustedSummary,
        metabolicEnergy: {
          coverage: 'UNAVAILABLE',
          bodyWeightKg: 75,
          coveredSeconds: 0,
          uncoveredSeconds: 600,
          exclusionReasons: ['INCONSISTENT_ENERGY_ESTIMATES'],
        },
      },
    });
    const wrapper = mountEnergyOverview();
    await wrapper.find('[data-test="energy-adjust-trigger"]').trigger('click');
    await flushPromises();
    expect(wrapper.get('[data-test="energy-adjust-results"]').text()).toContain('Calorie estimate unavailable');
    expect(wrapper.get('[data-test="energy-adjust-results"]').text()).toContain(
      'estimated work is higher than their calorie estimate'
    );
  });

  it('shows calories for a track whose mechanical work is zero', async () => {
    const wrapper = mountEnergyOverview();
    await wrapper.setProps({
      gpsTrack: {
        ...baseTrack(),
        energyNetTotalWh: 0,
        metabolicEnergy: { activeKcal: 45, totalKcal: 65, coverage: 'COMPLETE', bodyWeightKg: 75 },
      },
    });
    expect(wrapper.get('[data-test="active-calories"]').text()).toContain('45 kcal');
    expect(wrapper.text()).not.toContain('Energy stats are being calculated');
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

  it('shows pounds but sends canonical kilograms to the energy API', async () => {
    vi.useFakeTimers();
    measurementPreference.setMeasurementSystem('US_CUSTOMARY');
    mocks.calculateEnergyWhatIf
      .mockResolvedValueOnce(energyResponse(230, 0, 75))
      .mockResolvedValueOnce(energyResponse(242.5, 12.5, 81.6466266));
    const wrapper = mountEnergyOverview();
    await nextTick();

    await wrapper.find('[data-test="energy-adjust-trigger"]').trigger('click');
    await flushPromises();
    await nextTick();

    expect(wrapper.find<HTMLInputElement>('[data-test="energy-rider-weight-input"]').element.value).toBe('165.3');
    expect(wrapper.find('[data-test="energy-adjust-dialog"]').text()).toContain('lb');

    await wrapper.find('[data-test="energy-rider-weight-input"]').setValue('180');
    await vi.advanceTimersByTimeAsync(250);
    await flushPromises();

    const requestedKilograms = mocks.calculateEnergyWhatIf.mock.calls[1]?.[1];
    expect(requestedKilograms).toBeCloseTo(81.6466266, 7);
  });
  it.each(['track', 'activity'])('discards a preview after the %s changes', async (change) => {
    const request = deferred<ReturnType<typeof energyResponse>>();
    mocks.calculateEnergyWhatIf.mockReturnValueOnce(request.promise);
    const wrapper = mountEnergyOverview();
    await wrapper.get('[data-test="energy-adjust-trigger"]').trigger('click');
    await wrapper.setProps({
      gpsTrack:
        change === 'track' ? { ...baseTrack(), id: 2 } : { ...baseTrack(), activityType: GpsTrackActivityTypeEnum.Car },
    });
    request.resolve(energyResponse(999, 769, 82));
    await flushPromises();
    expect(wrapper.find('[data-test="energy-adjust-dialog"]').exists()).toBe(false);
    expect(wrapper.text()).not.toContain('999.0 Wh');
    wrapper.unmount();
  });

  it('disables and ignores unchanged weight saves', async () => {
    mocks.calculateEnergyWhatIf.mockResolvedValueOnce(energyResponse(230, 0, 75));
    const wrapper = mountEnergyOverview();
    await wrapper.get('[data-test="energy-adjust-trigger"]').trigger('click');
    await flushPromises();
    expect(wrapper.get<HTMLButtonElement>('[data-test="energy-adjust-save"]').element.disabled).toBe(true);
    await wrapper.get('form[data-test="energy-adjust-dialog"]').trigger('submit');
    expect(mocks.saveTrackEnergyRiderWeight).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it('does not apply a save response to another track', async () => {
    const saving = deferred<ReturnType<typeof baseTrack>>();
    mocks.calculateEnergyWhatIf.mockResolvedValueOnce(energyResponse(230, 0, 75));
    mocks.saveTrackEnergyRiderWeight.mockReturnValueOnce(saving.promise);
    const wrapper = mountEnergyOverview();
    await wrapper.get('[data-test="energy-adjust-trigger"]').trigger('click');
    await flushPromises();
    await wrapper.get('[data-test="energy-rider-weight-input"]').setValue('82');
    await wrapper.get('form[data-test="energy-adjust-dialog"]').trigger('submit');
    await wrapper.setProps({ gpsTrack: { ...baseTrack(), id: 2 } });
    saving.resolve({ ...baseTrack(), energyNetTotalWh: 999 });
    await flushPromises();
    expect(wrapper.emitted('track-updated')).toBeUndefined();
    wrapper.unmount();
  });
  it('keeps an edit made while the initial weight lookup is returning', async () => {
    vi.useFakeTimers();
    const initial = deferred<ReturnType<typeof energyResponse>>();
    mocks.calculateEnergyWhatIf
      .mockReturnValueOnce(initial.promise)
      .mockResolvedValueOnce(energyResponse(242.5, 12.5, 82));
    const wrapper = mountEnergyOverview();
    await wrapper.get('[data-test="energy-adjust-trigger"]').trigger('click');
    await wrapper.get('[data-test="energy-rider-weight-input"]').setValue('82');
    initial.resolve(energyResponse(230, 0, 75));
    await flushPromises();
    expect(wrapper.get<HTMLInputElement>('[data-test="energy-rider-weight-input"]').element.value).toBe('82');
    await vi.advanceTimersByTimeAsync(250);
    await flushPromises();
    expect(wrapper.get('[data-test="energy-adjust-results"]').text()).toContain('242.5 Wh');
    wrapper.unmount();
  });
});
