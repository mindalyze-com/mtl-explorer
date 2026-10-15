import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import TrackShapePreview from '@/components/ui/TrackShapePreview.vue';

const { cached, detail } = vi.hoisted(() => ({ cached: vi.fn(), detail: vi.fn() }));
vi.mock('@/utils/tracks/trackCollectionLoader', () => ({
  readBestCachedTrackShape: cached,
  fetchDetailTrackAtPrecision: detail,
}));
const loop = [
  [8.5, 47.3],
  [8.51, 47.3],
  [8.51, 47.31],
  [8.5, 47.3],
];
const collapsed = [
  [8.5, 47.3],
  [8.5, 47.3],
];
const shape = (coordinates: number[][]) => ({ coordinates, gpsTrack: { id: 1 }, fromCache: true });
let intersect: () => void;
const disconnect = vi.fn();

describe('track shape previews', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.stubGlobal(
      'IntersectionObserver',
      class {
        constructor(private callback: IntersectionObserverCallback) {}
        observe() {
          intersect = () =>
            this.callback(
              [{ isIntersecting: true } as IntersectionObserverEntry],
              this as unknown as IntersectionObserver
            );
          intersect();
        }
        disconnect() {
          disconnect();
        }
      }
    );
  });
  afterEach(() => vi.unstubAllGlobals());

  it('recovers a closed loop collapsed by coarse simplification using detail geometry', async () => {
    cached.mockResolvedValue(shape(collapsed));
    detail.mockResolvedValue(shape(loop));
    const wrapper = mount(TrackShapePreview, { props: { trackId: 1 } });
    await flushPromises();
    expect(detail).toHaveBeenCalledWith(1, 1, expect.any(AbortSignal));
    const path = wrapper.get('path').attributes('d');
    expect(path).not.toMatch(/NaN|Infinity/);
    expect(path.match(/L/g)?.length).toBeGreaterThan(1);
    wrapper.unmount();
  });

  it('uses already useful cached geometry without a detail request', async () => {
    cached.mockResolvedValue(shape(loop));
    const wrapper = mount(TrackShapePreview, { props: { trackId: 1 } });
    await flushPromises();
    expect(wrapper.get('path').attributes('d')).not.toMatch(/NaN|Infinity/);
    expect(detail).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it('fetches and renders a visible preview on a cache miss', async () => {
    cached.mockResolvedValue(null);
    detail.mockResolvedValue(shape(loop));
    const wrapper = mount(TrackShapePreview, { props: { trackId: 1 } });
    await flushPromises();
    expect(detail).toHaveBeenCalledWith(1, 1, expect.any(AbortSignal));
    expect(wrapper.get('path').attributes('d')).not.toMatch(/NaN|Infinity/);
    wrapper.unmount();
  });

  it('uses cached recording breaks even when shared metadata has different indexes', async () => {
    cached.mockResolvedValue({
      ...shape(loop),
      gpsTrack: { id: 1, gpsTracksData: [{ recordingBreakBeforePointIndexes: [3] }] },
      recordingBreakBeforePointIndexes: [2],
    });
    const wrapper = mount(TrackShapePreview, { props: { trackId: 1 } });
    await flushPromises();
    const path = wrapper.get('path').attributes('d');
    expect(path.match(/M/g)).toHaveLength(2);
    expect(path.match(/L/g)).toHaveLength(2);
    expect(detail).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it('retries a failed request when the preview becomes visible again', async () => {
    const warning = vi.spyOn(console, 'warn').mockImplementation(() => {});
    cached.mockResolvedValue(null);
    detail.mockRejectedValueOnce(new Error('Temporarily unavailable')).mockResolvedValueOnce(shape(loop));
    const wrapper = mount(TrackShapePreview, { props: { trackId: 1 } });
    await flushPromises();
    expect(wrapper.find('path').exists()).toBe(false);
    expect(disconnect).not.toHaveBeenCalled();
    intersect();
    await flushPromises();
    expect(wrapper.get('path').attributes('d')).not.toMatch(/NaN|Infinity/);
    expect(detail).toHaveBeenCalledTimes(2);
    expect(disconnect).toHaveBeenCalledOnce();
    wrapper.unmount();
    warning.mockRestore();
  });

  it('deduplicates visibility notifications and aborts an unmounted cache-miss request', async () => {
    cached.mockResolvedValue(null);
    let resolveDetail!: (value: ReturnType<typeof shape>) => void;
    detail.mockReturnValue(
      new Promise((resolve) => {
        resolveDetail = resolve;
      })
    );
    const wrapper = mount(TrackShapePreview, { props: { trackId: 1 } });
    intersect();
    await flushPromises();
    intersect();
    expect(cached).toHaveBeenCalledOnce();
    expect(detail).toHaveBeenCalledOnce();
    wrapper.unmount();
    expect(detail.mock.calls[0]?.[2].aborted).toBe(true);
    resolveDetail(shape(loop));
    await flushPromises();
  });

  it('does not emit an invalid SVG for a genuinely stationary track', async () => {
    cached.mockResolvedValue(shape(collapsed));
    detail.mockResolvedValue(shape(collapsed));
    const wrapper = mount(TrackShapePreview, { props: { trackId: 1 } });
    await flushPromises();
    expect(wrapper.find('path').exists()).toBe(false);
    wrapper.unmount();
  });

  it('does not overwrite a reused row with a late detail response for the previous track', async () => {
    cached.mockResolvedValueOnce(null).mockResolvedValueOnce(shape(loop));
    let resolveDetail!: (value: ReturnType<typeof shape>) => void;
    detail.mockReturnValue(
      new Promise((resolve) => {
        resolveDetail = resolve;
      })
    );
    const wrapper = mount(TrackShapePreview, { props: { trackId: 1 } });
    await flushPromises();
    await wrapper.setProps({ trackId: 2 });
    await flushPromises();
    const currentPath = wrapper.get('path').attributes('d');
    resolveDetail(
      shape([
        [0, 0],
        [1, 1],
      ])
    );
    await flushPromises();
    expect(wrapper.get('path').attributes('d')).toBe(currentPath);
    wrapper.unmount();
  });
});
