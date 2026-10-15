import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import TrackEnergyValue from '@/components/track-browser/TrackEnergyValue.vue';
import { ENERGY_STATUS_CACHE, type EnergyStatusCache } from '@/composables/useEnergyCalculationStatus';

const api = vi.hoisted(() => ({ status: vi.fn(), refresh: vi.fn() }));
vi.mock('@/utils/ServiceHelper', () => ({
  getEnergyCalculationStatus: api.status,
  refreshTrackEnergy: api.refresh,
}));
const classifiedTrack = { id: 1, activityType: 'WALKING' as const, activityTypeSource: 'AUTO_GUESS' as const };

describe('Track energy table values', () => {
  it('backs off failed checks, stops automatic retries, and allows a manual retry', async () => {
    api.status.mockRejectedValue(new Error('Offline'));
    const wrapper = mount(TrackEnergyValue, { props: { track: classifiedTrack } });
    await flushPromises();
    for (const delay of [5000, 15000, 60000]) {
      await vi.advanceTimersByTimeAsync(delay - 1);
      const before = api.status.mock.calls.length;
      await vi.advanceTimersByTimeAsync(1);
      await flushPromises();
      expect(api.status).toHaveBeenCalledTimes(before + 1);
    }
    expect(api.status).toHaveBeenCalledTimes(4);
    expect(vi.getTimerCount()).toBe(0);
    api.status.mockResolvedValue({ state: 'UNAVAILABLE', message: 'Unsupported activity.' });
    await wrapper.get('button').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toBe('Unavailable');
    expect(api.status).toHaveBeenCalledTimes(5);
    wrapper.unmount();
  });

  it('reuses a terminal result across virtual-row remounts and checks a new version', async () => {
    api.status.mockResolvedValue({ state: 'UNAVAILABLE', message: 'Unsupported activity.' });
    const cache: EnergyStatusCache = new Map();
    const options = {
      props: { track: { ...classifiedTrack, version: 1 } },
      global: { provide: { [ENERGY_STATUS_CACHE as symbol]: cache } },
    };
    const first = mount(TrackEnergyValue, options);
    await flushPromises();
    first.unmount();
    const second = mount(TrackEnergyValue, options);
    await flushPromises();
    expect(second.text()).toBe('Unavailable');
    expect(api.status).toHaveBeenCalledOnce();
    await second.setProps({ track: { ...classifiedTrack, version: 2 } });
    await flushPromises();
    expect(api.status).toHaveBeenCalledTimes(2);
    second.unmount();
  });
  it('replays cached metadata only for stale rows and does not re-emit an applied version on remount', async () => {
    const stale = { id: 1, version: 1 };
    const updated = { id: 1, version: 2, activityTypeSource: 'FAILED' as const };
    const cache: EnergyStatusCache = new Map();
    const global = { provide: { [ENERGY_STATUS_CACHE as symbol]: cache } };
    api.status.mockResolvedValue({ state: 'UNAVAILABLE', message: 'Select an activity.' });
    api.refresh.mockResolvedValue(updated);
    const first = mount(TrackEnergyValue, { props: { track: stale }, global });
    await flushPromises();
    expect(first.emitted('track-updated')).toEqual([[updated]]);
    first.unmount();

    const applied = mount(TrackEnergyValue, { props: { track: { ...updated } }, global });
    await flushPromises();
    expect(applied.text()).toBe('Unavailable');
    expect(applied.emitted('track-updated')).toBeUndefined();
    applied.unmount();

    const outdated = mount(TrackEnergyValue, { props: { track: stale }, global });
    await flushPromises();
    expect(outdated.emitted('track-updated')).toEqual([[updated]]);
    expect(api.status).toHaveBeenCalledOnce();
    expect(api.refresh).toHaveBeenCalledOnce();
    outdated.unmount();
  });

  it('refreshes unclassified rows after the shared queue finishes without a parent reload', async () => {
    api.status.mockResolvedValue({ state: 'CALCULATED' });
    const updated = { ...classifiedTrack, version: 2, energyNetTotalWh: 120 };
    api.refresh.mockResolvedValue(updated);
    const wrapper = mount(TrackEnergyValue, { props: { track: { id: 1, version: 1 }, processingPending: true } });
    expect(api.status).not.toHaveBeenCalled();
    await wrapper.setProps({ processingPending: false });
    await flushPromises();
    expect(wrapper.text()).toBe('120 Wh');
    expect(wrapper.emitted('track-updated')).toEqual([[updated]]);
    wrapper.unmount();
  });

  it('refreshes classification even when it finishes without supported energy', async () => {
    api.status.mockResolvedValue({ state: 'UNAVAILABLE', message: 'Select an activity to calculate energy.' });
    api.refresh.mockResolvedValue({ id: 1, version: 2, activityTypeSource: 'FAILED' });
    const wrapper = mount(TrackEnergyValue, { props: { track: { id: 1, version: 1 } } });
    await flushPromises();
    expect(wrapper.text()).toBe('Unavailable');
    expect(wrapper.emitted('track-updated')?.[0]?.[0]).toMatchObject({ activityTypeSource: 'FAILED' });
    expect(vi.getTimerCount()).toBe(0);
    wrapper.unmount();
  });

  beforeEach(() => {
    vi.useFakeTimers();
    vi.resetAllMocks();
    vi.stubGlobal('IntersectionObserver', undefined);
  });
  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('keeps zero as a calculated value without making a status request', () => {
    const wrapper = mount(TrackEnergyValue, { props: { track: { ...classifiedTrack, energyNetTotalWh: 0 } } });
    expect(wrapper.text()).toBe('0 Wh');
    expect(api.status).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it('waits for classification and the shared queue before checking an individual outcome', async () => {
    api.status.mockResolvedValue({ state: 'UNAVAILABLE', message: 'Energy is unavailable for this track.' });
    const wrapper = mount(TrackEnergyValue, { props: { track: { id: 1 }, processingPending: true } });
    expect(wrapper.text()).toBe('Pending');
    expect(wrapper.get('[aria-label]').attributes('aria-label')).toContain('activity classification');
    await wrapper.setProps({ track: classifiedTrack });
    expect(wrapper.text()).toBe('Processing…');
    expect(api.status).not.toHaveBeenCalled();
    await wrapper.setProps({ processingPending: false });
    await flushPromises();
    expect(wrapper.text()).toBe('Unavailable');
    expect(api.status).toHaveBeenCalledOnce();
    expect(vi.getTimerCount()).toBe(0);
    wrapper.unmount();
  });

  it('shows retrying status, then the calculated result, and stops polling', async () => {
    api.status
      .mockResolvedValueOnce({ state: 'RETRYING', message: 'A retry is scheduled.' })
      .mockResolvedValue({ state: 'CALCULATED' });
    api.refresh.mockResolvedValue({ ...classifiedTrack, energyNetTotalWh: 120 });
    const wrapper = mount(TrackEnergyValue, { props: { track: classifiedTrack } });
    await flushPromises();
    expect(wrapper.text()).toBe('Retrying');
    await vi.advanceTimersByTimeAsync(5000);
    await flushPromises();
    expect(wrapper.text()).toBe('120 Wh');
    expect(vi.getTimerCount()).toBe(0);
    wrapper.unmount();
  });

  it('does not query unreliable timing and separates request errors from unavailable energy', async () => {
    const wrapper = mount(TrackEnergyValue, { props: { track: { ...classifiedTrack, timingUnreliable: true } } });
    expect(wrapper.text()).toBe('Unavailable');
    expect(api.status).not.toHaveBeenCalled();
    api.status.mockRejectedValue(new Error('Offline'));
    await wrapper.setProps({ track: classifiedTrack });
    await flushPromises();
    expect(wrapper.text()).toContain('Status unavailable');
    wrapper.unmount();
    expect(vi.getTimerCount()).toBe(0);
  });

  it('loads only intersecting cells and pauses polling when they leave the viewport', async () => {
    let notify!: IntersectionObserverCallback;
    const disconnect = vi.fn();
    vi.stubGlobal(
      'IntersectionObserver',
      class {
        constructor(callback: IntersectionObserverCallback) {
          notify = callback;
        }
        observe() {}
        disconnect = disconnect;
      }
    );
    api.status.mockResolvedValue({ state: 'PENDING', message: 'Queued.' });
    const wrapper = mount(TrackEnergyValue, { props: { track: classifiedTrack } });
    expect(api.status).not.toHaveBeenCalled();
    notify([{ isIntersecting: true }] as IntersectionObserverEntry[], {} as IntersectionObserver);
    await flushPromises();
    expect(api.status).toHaveBeenCalledOnce();
    expect(vi.getTimerCount()).toBe(1);
    notify([{ isIntersecting: false }] as IntersectionObserverEntry[], {} as IntersectionObserver);
    await flushPromises();
    expect(vi.getTimerCount()).toBe(0);
    await vi.advanceTimersByTimeAsync(10_000);
    expect(api.status).toHaveBeenCalledOnce();
    wrapper.unmount();
    expect(disconnect).toHaveBeenCalledOnce();
  });

  it('shares an in-flight status lookup for the same track', async () => {
    let finish!: (value: object) => void;
    api.status.mockReturnValue(new Promise((resolve) => (finish = resolve)));
    const first = mount(TrackEnergyValue, { props: { track: classifiedTrack } });
    const second = mount(TrackEnergyValue, { props: { track: classifiedTrack } });
    expect(api.status).toHaveBeenCalledOnce();
    finish({ state: 'UNAVAILABLE' });
    await flushPromises();
    expect(first.text()).toBe('Unavailable');
    expect(second.text()).toBe('Unavailable');
    first.unmount();
    second.unmount();
  });
});
