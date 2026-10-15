import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import type { GpsTrack } from 'x8ing-mtl-api-typescript-fetch';
import SourceFileInfo from '@/features/track-review/SourceFileInfo.vue';
import ReviewMap from '@/features/track-review/ReviewMap.vue';

const mocks = vi.hoisted(() => ({ track: vi.fn(), resize: vi.fn() }));
vi.mock('x8ing-mtl-api-typescript-fetch', async (original) => ({
  ...(await original()),
  TracksControllerApi: class {
    getSingleTrack = mocks.track;
  },
}));
vi.mock('@/utils/openApiClient', () => ({ getApiConfiguration: () => ({}) }));
vi.mock('@/components/map/MiniMap.vue', () => ({
  default: {
    methods: { invalidateMapSize: mocks.resize },
    template: '<div />',
  },
}));
vi.mock('@/features/track-review/api', () => ({ reviewError: async () => 'Synthetic failure', reasonLabel: String }));

enableAutoUnmount(afterEach);
beforeEach(() => vi.resetAllMocks());

it('shows GPS metadata with a description that can be expanded and collapsed', async () => {
  const date = new Date('2026-01-02T10:00:00Z');
  mocks.track.mockResolvedValue({
    trackName: 'Synthetic GPS track',
    startDate: date,
    trackDescription: 'A long synthetic description for checking the expandable file information.',
  });
  const wrapper = mount(SourceFileInfo, { props: { active: true, tracks: [{ trackId: 1, name: 'Fallback' }] } });
  await flushPromises();
  expect(wrapper.text()).toContain('Synthetic GPS track');
  expect(wrapper.text()).toContain(date.toLocaleString());
  expect(mocks.track).toHaveBeenCalledWith(
    { gpsTrackId: 1, includeGeometry: false },
    expect.objectContaining({ cache: 'no-cache' })
  );
  const description = wrapper.get('.source-file-info__description');
  expect(description.attributes('aria-expanded')).toBe('false');
  await description.trigger('click');
  expect(description.attributes('aria-expanded')).toBe('true');
  await description.trigger('click');
  expect(description.attributes('aria-expanded')).toBe('false');
});

it('does not refetch metadata when the same track IDs arrive in a new array', async () => {
  mocks.track.mockResolvedValue({ trackName: 'Current track' });
  const wrapper = mount(SourceFileInfo, { props: { active: true, tracks: [{ trackId: 1 }] } });
  await flushPromises();
  await wrapper.setProps({ tracks: [{ trackId: 1, name: 'Current track' }] });
  await flushPromises();
  expect(mocks.track).toHaveBeenCalledOnce();
  await wrapper.setProps({ active: false });
  await wrapper.setProps({ active: true });
  await flushPromises();
  expect(mocks.track).toHaveBeenCalledTimes(2);
});

it('keeps the map responsive until resizing and starts keyboard changes from its measured height', async () => {
  let height = 220;
  const rect = vi
    .spyOn(HTMLElement.prototype, 'getBoundingClientRect')
    .mockImplementation(() => ({ height }) as DOMRect);
  try {
    const wrapper = mount(ReviewMap, {
      props: { points: [{ longitude: 8, latitude: 47 }], color: 'red', label: 'Original' },
    });
    await flushPromises();
    const canvas = wrapper.get('.review-map__canvas');
    expect(canvas.attributes('style')).toBeUndefined();
    height = 190;
    await wrapper.get('[role="separator"]').trigger('keydown', { key: 'ArrowDown' });
    expect(canvas.attributes('style')).toContain('height: 210px');
  } finally {
    rect.mockRestore();
  }
});

it('ignores metadata from another file and cancels reads when the review closes', async () => {
  let finish!: (track: GpsTrack) => void;
  mocks.track.mockReturnValueOnce(new Promise<GpsTrack>((resolve) => (finish = resolve)));
  mocks.track.mockResolvedValue({ metaName: 'Current track', metaDescription: 'Metadata description' });
  const wrapper = mount(SourceFileInfo, { props: { active: true, tracks: [{ trackId: 1 }] } });
  await flushPromises();
  const firstSignal = mocks.track.mock.calls[0][1].signal as AbortSignal;
  await wrapper.setProps({ tracks: [{ trackId: 2 }] });
  await flushPromises();
  finish({ trackName: 'Previous track' });
  await flushPromises();
  expect(firstSignal.aborted).toBe(true);
  expect(wrapper.text()).toContain('Current track');
  expect(wrapper.text()).toContain('Metadata description');
  expect(wrapper.text()).not.toContain('Previous track');
  expect(wrapper.text()).toContain('Unavailable');
  const currentSignal = mocks.track.mock.calls[1][1].signal as AbortSignal;
  await wrapper.setProps({ active: false });
  expect(currentSignal.aborted).toBe(true);
  await wrapper.setProps({ tracks: [{ trackId: 3 }] });
  expect(mocks.track).toHaveBeenCalledTimes(2);
});

it('keeps known names when a metadata request fails and supports retry', async () => {
  mocks.track.mockRejectedValueOnce(new Error('Synthetic failure'));
  mocks.track.mockResolvedValue({ trackName: 'Retried track', startDate: new Date('invalid') });
  const wrapper = mount(SourceFileInfo, { props: { active: true, tracks: [{ trackId: 1, name: 'Known name' }] } });
  await flushPromises();
  expect(wrapper.text()).toContain('Known name');
  expect(wrapper.get('[role="alert"]').text()).toContain('could not be loaded');
  await wrapper.get('button').trigger('click');
  await flushPromises();
  expect(wrapper.find('[role="alert"]').exists()).toBe(false);
  expect(wrapper.text()).toContain('Retried track');
  expect(wrapper.text()).toContain('Unavailable');
});

function pointer(target: EventTarget, type: string, y: number, pointerType: string) {
  const event = new MouseEvent(type, { bubbles: true, cancelable: true, button: 0, clientX: 100, clientY: y });
  Object.defineProperties(event, { pointerId: { value: 1 }, pointerType: { value: pointerType } });
  target.dispatchEvent(event);
}

it.each(['mouse', 'touch', 'pen'])('resizes the map with %s and keeps the released height', async (pointerType) => {
  const wrapper = mount(ReviewMap, {
    props: { points: [{ longitude: 8, latitude: 47 }], color: 'red', label: 'Original' },
  });
  await flushPromises();
  const handle = wrapper.get('[role="separator"]');
  pointer(handle.element, 'pointerdown', 200, pointerType);
  pointer(window, 'pointermove', 320, pointerType);
  pointer(window, 'pointerup', 320, pointerType);
  await handle.trigger('click');
  expect(handle.attributes('aria-valuenow')).toBe('400');
  expect(wrapper.get('.review-map__canvas').attributes('style')).toContain('height: 400px');
  expect(mocks.resize).toHaveBeenCalled();
  await handle.trigger('keydown', { key: 'Home' });
  expect(handle.attributes('aria-valuenow')).toBe('120');
  await handle.trigger('keydown', { key: 'ArrowUp' });
  expect(handle.attributes('aria-valuenow')).toBe('120');
  await handle.trigger('keydown', { key: 'End' });
  expect(handle.attributes('aria-valuenow')).toBe('600');
  await handle.trigger('keydown', { key: 'ArrowDown' });
  expect(handle.attributes('aria-valuenow')).toBe('600');
  await handle.trigger('keydown', { key: 'Enter' });
  expect(handle.attributes('aria-valuenow')).toBe('120');
});
