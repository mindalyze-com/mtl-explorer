import { flushPromises, mount } from '@vue/test-utils';
import { defineComponent, type PropType } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  CROSSFADE_MS,
  FULL_PREVIEW_MAX_SIZE,
  HIGH_RESOLUTION_IDLE_DELAY_MS,
  INITIAL_PREVIEW_MAX_SIZE,
  NEXT_FULL_RESOLUTION_PREFETCH_IDLE_DELAY_MS,
  NEXT_PREVIEW_PREFETCH_IDLE_DELAY_MS,
  VIDEO_POSTER_MAX_SIZE,
  useMediaPreview,
} from '@/components/map/useMediaPreview';

const mocks = vi.hoisted(() => ({
  getMediaInfo: vi.fn(),
}));

vi.mock('@/repositories/mediaRepository', () => ({
  getMediaInfo: mocks.getMediaInfo,
  mediaContentUrl: (id: number, maxSize?: number) =>
    maxSize == null ? `/media/${id}` : `/media/${id}?maxSize=${maxSize}`,
}));

const PreviewHarness = defineComponent({
  props: {
    mediaId: { type: Number as PropType<number | null>, default: null },
    prefetchIds: { type: Array as PropType<(number | null)[]>, default: () => [] },
    timeSource: { type: String as PropType<string | null>, default: null },
    appliedCameraOffsetSeconds: { type: Number as PropType<number | null>, default: null },
  },
  setup(props) {
    return useMediaPreview(props);
  },
  template:
    '<div data-test="preview-state" :data-src="displayUrl" :data-media-url="mediaUrl" :data-poster-url="posterUrl" :data-capture-source="captureTimeSource" :data-video="String(isVideo)" :data-full-resolution="String(isHighResolutionPending)" :data-cross-fading="String(isCrossFading)" :data-swap-pending="String(isSwapPending)" />',
});

function mediaInfo(id: number) {
  return {
    id,
    mediaKind: 'IMAGE' as const,
    fileName: `photo-${id}.jpg`,
    folderPath: '/photos',
    fileExtension: 'jpg',
  };
}

function imageResponse(contents: string): Response {
  return {
    ok: true,
    status: 200,
    blob: async () => new Blob([contents], { type: 'image/jpeg' }),
  } as Response;
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

describe('useMediaPreview loading', () => {
  let blobIndex = 0;

  beforeEach(() => {
    vi.useFakeTimers();
    blobIndex = 0;
    mocks.getMediaInfo.mockReset();
    mocks.getMediaInfo.mockImplementation(async (id: number) => mediaInfo(id));
    vi.spyOn(URL, 'createObjectURL').mockImplementation(() => `blob:preview-${++blobIndex}`);
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    vi.stubGlobal(
      'Image',
      class {
        decoding = 'auto';
        src = '';
        decode = vi.fn(async () => undefined);
      }
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  it('formats a saved camera correction with non-zero seconds as a valid compact hour label', () => {
    const wrapper = mount(PreviewHarness, {
      props: { timeSource: 'EXIF_DATE_TAKEN', appliedCameraOffsetSeconds: 3_603 },
    });

    expect(wrapper.get('[data-test="preview-state"]').attributes('data-capture-source')).toBe(
      'Camera clock · +1h correction'
    );
    wrapper.unmount();
  });

  it('shows the full-resolution status only while the full image is actually loading', async () => {
    const fullResponse = deferred<Response>();
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(imageResponse('initial'))
      .mockReturnValueOnce(fullResponse.promise);
    vi.stubGlobal('fetch', fetchMock);

    const wrapper = mount(PreviewHarness, { props: { mediaId: 7 } });
    await flushPromises();

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0][0]).toBe(`/media/7?maxSize=${INITIAL_PREVIEW_MAX_SIZE}`);
    expect(wrapper.get('[data-test="preview-state"]').attributes()).toMatchObject({
      'data-src': 'blob:preview-1',
      'data-full-resolution': 'false',
    });

    await vi.advanceTimersByTimeAsync(HIGH_RESOLUTION_IDLE_DELAY_MS);
    await flushPromises();

    expect(fetchMock.mock.calls[1][0]).toBe(`/media/7?maxSize=${FULL_PREVIEW_MAX_SIZE}`);
    expect(wrapper.get('[data-test="preview-state"]').attributes('data-full-resolution')).toBe('true');

    fullResponse.resolve(imageResponse('full'));
    await flushPromises();

    expect(wrapper.get('[data-test="preview-state"]').attributes()).toMatchObject({
      'data-src': 'blob:preview-2',
      'data-full-resolution': 'false',
      'data-cross-fading': 'false',
    });
    wrapper.unmount();
  });

  it('navigates with the decoded full image without another metadata or content request', async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(imageResponse('image'));
    vi.stubGlobal('fetch', fetchMock);

    const wrapper = mount(PreviewHarness, { props: { mediaId: 7, prefetchIds: [8] } });
    await flushPromises();
    await vi.advanceTimersByTimeAsync(HIGH_RESOLUTION_IDLE_DELAY_MS);
    await flushPromises();
    await vi.advanceTimersByTimeAsync(NEXT_PREVIEW_PREFETCH_IDLE_DELAY_MS);
    await flushPromises();
    await vi.advanceTimersByTimeAsync(NEXT_FULL_RESOLUTION_PREFETCH_IDLE_DELAY_MS);
    await flushPromises();

    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      `/media/7?maxSize=${INITIAL_PREVIEW_MAX_SIZE}`,
      `/media/7?maxSize=${FULL_PREVIEW_MAX_SIZE}`,
      `/media/8?maxSize=${INITIAL_PREVIEW_MAX_SIZE}`,
      `/media/8?maxSize=${FULL_PREVIEW_MAX_SIZE}`,
    ]);
    expect(mocks.getMediaInfo).toHaveBeenCalledTimes(2);

    await wrapper.setProps({ mediaId: 8, prefetchIds: [7] });
    await flushPromises();

    expect(fetchMock).toHaveBeenCalledTimes(4);
    expect(mocks.getMediaInfo).toHaveBeenCalledTimes(2);
    expect(wrapper.get('[data-test="preview-state"]').attributes()).toMatchObject({
      'data-src': 'blob:preview-4',
      'data-full-resolution': 'false',
      'data-cross-fading': 'true',
    });
    wrapper.unmount();
  });

  it('promotes an in-flight full-resolution prefetch instead of starting a duplicate request', async () => {
    const prefetchedFullResponse = deferred<Response>();
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(imageResponse('current-initial'))
      .mockResolvedValueOnce(imageResponse('current-full'))
      .mockResolvedValueOnce(imageResponse('next-initial'))
      .mockReturnValueOnce(prefetchedFullResponse.promise);
    vi.stubGlobal('fetch', fetchMock);

    const wrapper = mount(PreviewHarness, { props: { mediaId: 7, prefetchIds: [8] } });
    await flushPromises();
    await vi.advanceTimersByTimeAsync(HIGH_RESOLUTION_IDLE_DELAY_MS);
    await flushPromises();
    await vi.advanceTimersByTimeAsync(NEXT_PREVIEW_PREFETCH_IDLE_DELAY_MS);
    await flushPromises();
    await vi.advanceTimersByTimeAsync(NEXT_FULL_RESOLUTION_PREFETCH_IDLE_DELAY_MS);
    await flushPromises();
    expect(fetchMock).toHaveBeenCalledTimes(4);

    await wrapper.setProps({ mediaId: 8, prefetchIds: [7] });
    await flushPromises();

    expect(fetchMock).toHaveBeenCalledTimes(4);
    expect(wrapper.get('[data-test="preview-state"]').attributes()).toMatchObject({
      'data-src': 'blob:preview-3',
      'data-full-resolution': 'true',
    });

    prefetchedFullResponse.resolve(imageResponse('next-full'));
    await flushPromises();
    await vi.advanceTimersByTimeAsync(CROSSFADE_MS);
    await flushPromises();

    expect(fetchMock).toHaveBeenCalledTimes(4);
    expect(wrapper.get('[data-test="preview-state"]').attributes()).toMatchObject({
      'data-src': 'blob:preview-4',
      'data-full-resolution': 'false',
      'data-cross-fading': 'false',
    });
    wrapper.unmount();
  });

  it('keeps the current photo visible until the next photo has decoded', async () => {
    const nextDecode = deferred<void>();
    let decodeCount = 0;
    vi.stubGlobal(
      'Image',
      class {
        decoding = 'auto';
        src = '';
        decode = vi.fn(() => (++decodeCount === 1 ? Promise.resolve() : nextDecode.promise));
      }
    );
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(imageResponse('image'));
    vi.stubGlobal('fetch', fetchMock);

    const wrapper = mount(PreviewHarness, { props: { mediaId: 1 } });
    await flushPromises();
    expect(wrapper.get('[data-test="preview-state"]').attributes('data-src')).toBe('blob:preview-1');

    await wrapper.setProps({ mediaId: 2 });
    await flushPromises();

    expect(wrapper.get('[data-test="preview-state"]').attributes()).toMatchObject({
      'data-src': 'blob:preview-1',
      'data-swap-pending': 'true',
      'data-cross-fading': 'false',
    });

    nextDecode.resolve();
    await flushPromises();

    expect(wrapper.get('[data-test="preview-state"]').attributes()).toMatchObject({
      'data-src': 'blob:preview-2',
      'data-cross-fading': 'true',
    });
    wrapper.unmount();
  });

  it('does not prefetch video content as an image preview', async () => {
    mocks.getMediaInfo.mockImplementation(async (id: number) => ({
      ...mediaInfo(id),
      mediaKind: id === 8 ? ('VIDEO' as const) : ('IMAGE' as const),
      fileName: id === 8 ? 'video-8.mp4' : `photo-${id}.jpg`,
      fileExtension: id === 8 ? 'mp4' : 'jpg',
    }));
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(imageResponse('image'));
    vi.stubGlobal('fetch', fetchMock);

    const wrapper = mount(PreviewHarness, { props: { mediaId: 7, prefetchIds: [8] } });
    await flushPromises();
    await vi.advanceTimersByTimeAsync(HIGH_RESOLUTION_IDLE_DELAY_MS);
    await flushPromises();
    await vi.advanceTimersByTimeAsync(NEXT_PREVIEW_PREFETCH_IDLE_DELAY_MS);
    await flushPromises();

    expect(mocks.getMediaInfo).toHaveBeenCalledWith(8, expect.any(AbortSignal));
    expect(fetchMock).toHaveBeenCalledTimes(2);
    wrapper.unmount();
  });

  it.each(['mp4', 'mov'])(
    'uses the original stream and a generated poster for an active %s video',
    async (extension) => {
      const fileName = `video-8.${extension}`;
      mocks.getMediaInfo.mockResolvedValue({
        ...mediaInfo(8),
        mediaKind: 'VIDEO',
        fileName,
        fileExtension: extension,
      });
      const fetchMock = vi.fn<typeof fetch>();
      vi.stubGlobal('fetch', fetchMock);

      const wrapper = mount(PreviewHarness, { props: { mediaId: 8 } });
      await flushPromises();

      expect(wrapper.get('[data-test="preview-state"]').attributes()).toMatchObject({
        'data-video': 'true',
        'data-media-url': '/media/8',
        'data-poster-url': `/media/8?maxSize=${VIDEO_POSTER_MAX_SIZE}`,
      });
      expect(fetchMock).not.toHaveBeenCalled();
      wrapper.unmount();
    }
  );

  it('aborts a stale decoded request when navigation moves to a newer photo', async () => {
    type PendingRequest = {
      url: string;
      signal: AbortSignal;
      resolve: (response: Response) => void;
    };
    const pendingRequests: PendingRequest[] = [];
    const fetchMock = vi.fn<typeof fetch>().mockImplementation((input, init) => {
      const signal = init?.signal as AbortSignal;
      return new Promise<Response>((resolve, reject) => {
        signal.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')), { once: true });
        pendingRequests.push({ url: String(input), signal, resolve });
      });
    });
    vi.stubGlobal('fetch', fetchMock);

    const wrapper = mount(PreviewHarness, { props: { mediaId: 1 } });
    await flushPromises();
    await wrapper.setProps({ mediaId: 2 });
    await flushPromises();

    expect(pendingRequests.map((request) => request.url)).toEqual([
      `/media/1?maxSize=${INITIAL_PREVIEW_MAX_SIZE}`,
      `/media/2?maxSize=${INITIAL_PREVIEW_MAX_SIZE}`,
    ]);
    expect(pendingRequests[0].signal.aborted).toBe(true);

    pendingRequests[1].resolve(imageResponse('second'));
    await flushPromises();
    expect(wrapper.get('[data-test="preview-state"]').attributes('data-src')).toBe('blob:preview-1');
    wrapper.unmount();
  });

  it('aborts the previous full-resolution request when the user moves on', async () => {
    const previousFull = deferred<Response>();
    let previousFullSignal: AbortSignal | null = null;
    const fetchMock = vi.fn<typeof fetch>().mockImplementation((input, init) => {
      const url = String(input);
      if (url === `/media/1?maxSize=${FULL_PREVIEW_MAX_SIZE}`) {
        previousFullSignal = init?.signal as AbortSignal;
        previousFullSignal.addEventListener(
          'abort',
          () => previousFull.reject(new DOMException('Aborted', 'AbortError')),
          { once: true }
        );
        return previousFull.promise;
      }
      return Promise.resolve(imageResponse(url));
    });
    vi.stubGlobal('fetch', fetchMock);

    const wrapper = mount(PreviewHarness, { props: { mediaId: 1 } });
    await flushPromises();
    await vi.advanceTimersByTimeAsync(HIGH_RESOLUTION_IDLE_DELAY_MS);
    await flushPromises();
    expect(previousFullSignal?.aborted).toBe(false);

    await wrapper.setProps({ mediaId: 2 });
    await flushPromises();

    expect(previousFullSignal?.aborted).toBe(true);
    expect(wrapper.get('[data-test="preview-state"]').attributes('data-src')).toBe('blob:preview-2');
    wrapper.unmount();
  });
});
