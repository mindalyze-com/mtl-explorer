import { flushPromises, mount } from '@vue/test-utils';
import { computed, defineComponent } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useEnergyCalculationStatus } from '@/composables/useEnergyCalculationStatus';
import type { GpsTrack } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';

const mocks = vi.hoisted(() => ({ status: vi.fn(), refresh: vi.fn() }));
vi.mock('@/utils/ServiceHelper', () => ({
  getEnergyCalculationStatus: mocks.status,
  refreshTrackEnergy: mocks.refresh,
}));
const Harness = defineComponent({
  props: { trackId: { type: Number, default: 1 }, energyWh: { type: Number, default: 230 } },
  emits: ['updated'],
  setup(props, { emit }) {
    const track = computed<GpsTrack>(() => ({ id: props.trackId, energyNetTotalWh: props.energyWh }));
    return useEnergyCalculationStatus(track, (updated) => emit('updated', updated));
  },
  template: '<div>{{ status?.state }} {{ message }}</div>',
});
function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((complete) => {
    resolve = complete;
  });
  return { resolve, promise };
}

describe('Energy calculation status', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.useFakeTimers();
  });
  afterEach(() => vi.useRealTimers());
  it('shows failed retries and refreshes the track when calculation completes', async () => {
    mocks.status
      .mockResolvedValueOnce({ state: 'RETRYING', message: 'Calculation failed. Retry scheduled.' })
      .mockResolvedValueOnce({ state: 'CALCULATED' });
    mocks.refresh.mockResolvedValue({ id: 1, energyNetTotalWh: 0 });
    const wrapper = mount(Harness);
    await flushPromises();
    expect(wrapper.text()).toContain('Retry scheduled');
    await vi.advanceTimersByTimeAsync(5000);
    await flushPromises();
    expect(wrapper.emitted('updated')?.[0]?.[0]).toMatchObject({ id: 1, energyNetTotalWh: 0 });
    expect(vi.getTimerCount()).toBe(0);
    wrapper.unmount();
  });
  it('keeps polling a track that waits for activity classification and refreshes it once calculated', async () => {
    mocks.status
      .mockResolvedValueOnce({ state: 'PENDING', message: 'Waiting for activity classification.' })
      .mockResolvedValueOnce({ state: 'PENDING', message: 'Energy calculation is queued or running.' })
      .mockResolvedValueOnce({ state: 'CALCULATED' });
    mocks.refresh.mockResolvedValue({ id: 1, energyNetTotalWh: 120 });
    const wrapper = mount(Harness);
    await flushPromises();
    expect(wrapper.text()).toContain('Waiting for activity classification');
    await vi.advanceTimersByTimeAsync(5000);
    await flushPromises();
    expect(wrapper.text()).toContain('queued or running');
    await vi.advanceTimersByTimeAsync(5000);
    await flushPromises();
    expect(wrapper.emitted('updated')?.[0]?.[0]).toMatchObject({ id: 1, energyNetTotalWh: 120 });
    expect(vi.getTimerCount()).toBe(0);
    wrapper.unmount();
  });
  it('shows terminal unavailable states without continuous polling', async () => {
    mocks.status.mockResolvedValue({ state: 'UNAVAILABLE', message: 'Select an activity.' });
    const wrapper = mount(Harness);
    await flushPromises();
    expect(wrapper.text()).toContain('Select an activity');
    expect(vi.getTimerCount()).toBe(0);
    wrapper.unmount();
  });
  it('discards stale status after navigation', async () => {
    const oldStatus = deferred<object>();
    mocks.status
      .mockReturnValueOnce(oldStatus.promise)
      .mockResolvedValueOnce({ state: 'UNAVAILABLE', message: 'Second track unavailable' });
    const wrapper = mount(Harness);
    await wrapper.setProps({ trackId: 2 });
    await flushPromises();
    oldStatus.resolve({ state: 'PENDING', message: 'Old track pending' });
    await flushPromises();
    expect(wrapper.text()).toContain('Second track unavailable');
    expect(wrapper.text()).not.toContain('Old track');
    expect(mocks.refresh).not.toHaveBeenCalled();
    wrapper.unmount();
  });
  it('reports status request failures and cancels retries on unmount', async () => {
    mocks.status.mockRejectedValue(new Error('Offline'));
    const wrapper = mount(Harness);
    await flushPromises();
    expect(wrapper.text()).toContain('Could not load energy calculation status');
    expect(vi.getTimerCount()).toBe(1);
    wrapper.unmount();
    expect(vi.getTimerCount()).toBe(0);
  });
  it('retries a failed refresh after completed calculation even with an old summary', async () => {
    mocks.status.mockResolvedValueOnce({ state: 'PENDING' }).mockResolvedValue({ state: 'CALCULATED' });
    mocks.refresh
      .mockRejectedValueOnce(new Error('Refresh offline'))
      .mockResolvedValueOnce({ id: 1, energyNetTotalWh: 250 });
    const wrapper = mount(Harness);
    await flushPromises();
    await vi.advanceTimersByTimeAsync(5000);
    await flushPromises();
    expect(wrapper.text()).toContain('Could not load energy calculation status');
    await vi.advanceTimersByTimeAsync(5000);
    await flushPromises();
    expect(mocks.refresh).toHaveBeenCalledTimes(2);
    expect(wrapper.emitted('updated')?.[0]?.[0]).toMatchObject({ id: 1, energyNetTotalWh: 250 });
    wrapper.unmount();
  });

  it('discards a completed refresh for a track that is no longer selected', async () => {
    const refreshing = deferred<GpsTrack>();
    mocks.status
      .mockResolvedValueOnce({ state: 'PENDING' })
      .mockResolvedValueOnce({ state: 'CALCULATED' })
      .mockResolvedValueOnce({ state: 'UNAVAILABLE' });
    mocks.refresh.mockReturnValueOnce(refreshing.promise);
    const wrapper = mount(Harness);
    await flushPromises();
    await vi.advanceTimersByTimeAsync(5000);
    await wrapper.setProps({ trackId: 2 });
    refreshing.resolve({ id: 1, energyNetTotalWh: 250 });
    await flushPromises();
    expect(wrapper.emitted('updated')).toBeUndefined();
    wrapper.unmount();
  });
});
