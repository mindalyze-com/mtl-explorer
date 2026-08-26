import { flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import CompatibleVideoPlayer from '@/components/map/CompatibleVideoPlayer.vue';
import type { VideoTranscodeSession } from '@/repositories/mediaRepository';

const repositoryMock = vi.hoisted(() => ({
  create: vi.fn(),
  get: vi.fn(),
  cancel: vi.fn(),
}));

const hlsMock = vi.hoisted(() => ({
  supported: true,
  instances: [] as Array<{
    handlers: Map<string, (...args: unknown[]) => void>;
    config: { xhrSetup?: (xhr: XMLHttpRequest) => void };
    attachMedia: ReturnType<typeof vi.fn>;
    loadSource: ReturnType<typeof vi.fn>;
    destroy: ReturnType<typeof vi.fn>;
    startLoad: ReturnType<typeof vi.fn>;
    recoverMediaError: ReturnType<typeof vi.fn>;
  }>,
}));

vi.mock('@/repositories/mediaRepository', () => ({
  VIDEO_TRANSCODE_QUALITY_OPTIONS: [
    { value: 'AUTO', label: 'Auto' },
    { value: 'P480', label: '480p' },
    { value: 'P720', label: '720p' },
    { value: 'P1080', label: '1080p' },
  ],
  createVideoTranscodeSession: repositoryMock.create,
  getVideoTranscodeSession: repositoryMock.get,
  cancelVideoTranscodeSession: repositoryMock.cancel,
  videoTranscodePlaylistUrl: (url: string) => `/mtl/${url}`,
}));

vi.mock('hls.js', () => {
  class MockHls {
    static isSupported() {
      return hlsMock.supported;
    }

    handlers = new Map<string, (...args: unknown[]) => void>();
    attachMedia = vi.fn();
    loadSource = vi.fn();
    destroy = vi.fn();
    startLoad = vi.fn();
    recoverMediaError = vi.fn();
    config: { xhrSetup?: (xhr: XMLHttpRequest) => void };

    constructor(config: { xhrSetup?: (xhr: XMLHttpRequest) => void }) {
      this.config = config;
      hlsMock.instances.push(this);
    }

    on(event: string, handler: (...args: unknown[]) => void) {
      this.handlers.set(event, handler);
    }
  }

  return {
    default: MockHls,
    ErrorTypes: { NETWORK_ERROR: 'networkError', MEDIA_ERROR: 'mediaError' },
    Events: { MEDIA_ATTACHED: 'mediaAttached', MANIFEST_PARSED: 'manifestParsed', ERROR: 'error' },
  };
});

const wrappers: VueWrapper[] = [];

function transcodeSession(overrides: Partial<VideoTranscodeSession> = {}): VideoTranscodeSession {
  return {
    sessionId: 'session-1',
    mediaId: 17,
    quality: 'AUTO',
    state: 'STARTING',
    playlistUrl: 'api/media/transcode-sessions/session-1/playlist.m3u8',
    playlistReady: false,
    encodedSeconds: 0,
    sourceDurationSeconds: 120,
    transcodeSpeed: 0,
    bytesWritten: 0,
    message: undefined,
    reused: false,
    ...overrides,
  };
}

function mountPlayer(): VueWrapper {
  const wrapper = mount(CompatibleVideoPlayer, {
    props: {
      mediaId: 17,
      src: '/mtl/api/media/get/17/content',
      poster: '/mtl/api/media/get/17/content?maxSize=1600',
      label: 'clip.mov',
    },
    attachTo: document.body,
  });
  wrappers.push(wrapper);
  return wrapper;
}

function setMediaError(wrapper: VueWrapper, code: number): void {
  Object.defineProperty(wrapper.get('video').element, 'error', {
    configurable: true,
    value: { code },
  });
}

async function reportUnsupported(wrapper: VueWrapper): Promise<void> {
  setMediaError(wrapper, 4);
  await wrapper.get('video').trigger('error');
}

function buttonWithText(wrapper: VueWrapper, text: string) {
  const button = wrapper.findAll('button').find((candidate) => candidate.text().includes(text));
  if (!button) throw new Error(`Button not found: ${text}`);
  return button;
}

beforeEach(() => {
  repositoryMock.create.mockReset();
  repositoryMock.get.mockReset();
  repositoryMock.cancel.mockReset().mockResolvedValue(undefined);
  hlsMock.instances.splice(0);
  hlsMock.supported = true;
  vi.spyOn(HTMLMediaElement.prototype, 'canPlayType').mockReturnValue('probably');
  vi.spyOn(HTMLMediaElement.prototype, 'load').mockImplementation(() => undefined);
  vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue();
});

afterEach(() => {
  for (const wrapper of wrappers.splice(0)) wrapper.unmount();
  document.body.innerHTML = '';
  vi.useRealTimers();
  vi.restoreAllMocks();
});

describe('CompatibleVideoPlayer', () => {
  it.each([3, 4])('offers compatible playback for media error code %i', async (code) => {
    const wrapper = mountPlayer();
    setMediaError(wrapper, code);

    await wrapper.get('video').trigger('error');

    expect(wrapper.get('[data-test="video-transcode-panel"]').text()).toContain(
      'This browser cannot play the original video'
    );
    expect(wrapper.get('video').attributes('controls')).toBeUndefined();
    expect(wrapper.emitted('media-error')).toBeUndefined();
  });

  it('offers server quality profiles after an unsupported source instead of hiding the error', async () => {
    repositoryMock.create.mockResolvedValue(transcodeSession({ quality: 'P720' }));
    const wrapper = mountPlayer();

    await reportUnsupported(wrapper);

    expect(wrapper.get('[data-test="video-transcode-panel"]').text()).toContain(
      'This browser cannot play the original video'
    );
    expect(wrapper.findAll('[aria-label="Compatible video quality"] option').map((option) => option.text())).toEqual([
      'Auto',
      '480p',
      '720p',
      '1080p',
    ]);
    expect(repositoryMock.create).not.toHaveBeenCalled();

    await wrapper.get('[aria-label="Compatible video quality"]').setValue('P720');
    await buttonWithText(wrapper, 'Create compatible stream').trigger('click');
    await flushPromises();

    expect(repositoryMock.create).toHaveBeenCalledWith(17, 'P720', expect.any(AbortSignal));
    expect(wrapper.get('[data-test="video-transcode-panel"]').text()).toContain('Preparing compatible 720p video');
  });

  it('polls until a native HLS playlist is ready and reports live progress', async () => {
    vi.useFakeTimers();
    repositoryMock.create.mockResolvedValue(transcodeSession());
    repositoryMock.get.mockResolvedValue(
      transcodeSession({
        state: 'RUNNING',
        playlistReady: true,
        encodedSeconds: 30,
        transcodeSpeed: 0.8,
      })
    );
    const wrapper = mountPlayer();
    await reportUnsupported(wrapper);
    await buttonWithText(wrapper, 'Create compatible stream').trigger('click');
    await flushPromises();

    await vi.advanceTimersByTimeAsync(1_000);
    await flushPromises();

    expect(repositoryMock.get).toHaveBeenCalledWith('session-1', expect.any(AbortSignal));
    expect(wrapper.get('video').attributes('src')).toBe('/mtl/api/media/transcode-sessions/session-1/playlist.m3u8');
    expect(wrapper.get('[data-test="video-transcode-status"]').text()).toContain('25% · 0.8× speed');

    await wrapper.get('video').trigger('loadedmetadata');
    expect(wrapper.get('video').attributes('controls')).toBe('');
    expect(wrapper.find('.mp__video-play').exists()).toBe(true);
  });

  it('cancels an active session only when the user asks', async () => {
    repositoryMock.create.mockResolvedValue(transcodeSession());
    const wrapper = mountPlayer();
    await reportUnsupported(wrapper);
    await buttonWithText(wrapper, 'Create compatible stream').trigger('click');
    await flushPromises();

    await buttonWithText(wrapper, 'Cancel').trigger('click');
    await flushPromises();

    expect(repositoryMock.cancel).toHaveBeenCalledWith('session-1');
    expect(wrapper.get('[data-test="video-transcode-panel"]').text()).toContain('Compatible video cancelled');
  });

  it('shows server capacity errors and can retry without reopening the player', async () => {
    repositoryMock.create
      .mockRejectedValueOnce({ response: { status: 429 } })
      .mockResolvedValueOnce(transcodeSession());
    const wrapper = mountPlayer();
    await reportUnsupported(wrapper);

    await buttonWithText(wrapper, 'Create compatible stream').trigger('click');
    await flushPromises();

    expect(wrapper.get('[data-test="video-transcode-panel"]').text()).toContain(
      'The server is already preparing another video'
    );
    await buttonWithText(wrapper, 'Retry').trigger('click');
    await flushPromises();
    expect(repositoryMock.create).toHaveBeenCalledTimes(2);
    expect(wrapper.get('[data-test="video-transcode-panel"]').text()).toContain('Preparing compatible Auto video');
  });

  it('shows a failed session message and retries it', async () => {
    repositoryMock.create
      .mockResolvedValueOnce(transcodeSession({ state: 'FAILED', message: 'FFmpeg stopped.' }))
      .mockResolvedValueOnce(transcodeSession());
    const wrapper = mountPlayer();
    await reportUnsupported(wrapper);

    await buttonWithText(wrapper, 'Create compatible stream').trigger('click');
    await flushPromises();
    expect(wrapper.get('[data-test="video-transcode-panel"]').text()).toContain('FFmpeg stopped.');

    await buttonWithText(wrapper, 'Retry').trigger('click');
    await flushPromises();
    expect(repositoryMock.create).toHaveBeenCalledTimes(2);
  });

  it('attaches a completed session without continuing to poll', async () => {
    vi.useFakeTimers();
    repositoryMock.create.mockResolvedValue(
      transcodeSession({ state: 'COMPLETED', playlistReady: true, encodedSeconds: 120, transcodeSpeed: 2 })
    );
    const wrapper = mountPlayer();
    await reportUnsupported(wrapper);
    await buttonWithText(wrapper, 'Create compatible stream').trigger('click');
    await flushPromises();
    await wrapper.get('video').trigger('loadedmetadata');
    await vi.advanceTimersByTimeAsync(5_000);

    expect(wrapper.get('video').attributes('src')).toContain('playlist.m3u8');
    expect(wrapper.find('[data-test="video-transcode-status"]').exists()).toBe(false);
    expect(repositoryMock.get).not.toHaveBeenCalled();
  });

  it('cancels the old session before changing compatible quality', async () => {
    repositoryMock.create
      .mockResolvedValueOnce(transcodeSession({ state: 'RUNNING', playlistReady: true }))
      .mockResolvedValueOnce(
        transcodeSession({ sessionId: 'session-2', quality: 'P480', playlistUrl: '', state: 'STARTING' })
      );
    const wrapper = mountPlayer();
    await reportUnsupported(wrapper);
    await buttonWithText(wrapper, 'Create compatible stream').trigger('click');
    await flushPromises();

    await wrapper.get('[aria-label="Compatible video quality"]').setValue('P480');
    await flushPromises();

    expect(repositoryMock.cancel).toHaveBeenCalledWith('session-1');
    expect(repositoryMock.cancel.mock.invocationCallOrder[0]).toBeLessThan(
      repositoryMock.create.mock.invocationCallOrder[1] ?? Number.POSITIVE_INFINITY
    );
    expect(repositoryMock.create).toHaveBeenLastCalledWith(17, 'P480', expect.any(AbortSignal));
  });

  it('leaves background work alive on unmount and identifies a reused session on reconnect', async () => {
    repositoryMock.create
      .mockResolvedValueOnce(transcodeSession())
      .mockResolvedValueOnce(transcodeSession({ reused: true }));
    const first = mountPlayer();
    await reportUnsupported(first);
    await buttonWithText(first, 'Create compatible stream').trigger('click');
    await flushPromises();
    first.unmount();

    expect(repositoryMock.cancel).not.toHaveBeenCalled();

    const second = mountPlayer();
    await reportUnsupported(second);
    await buttonWithText(second, 'Create compatible stream').trigger('click');
    await flushPromises();

    expect(repositoryMock.create).toHaveBeenCalledTimes(2);
    expect(second.get('[data-test="video-transcode-panel"]').text()).toContain('Reusing prepared work');
  });

  it('uses hls.js outside browsers with native HLS and destroys it when detached', async () => {
    vi.mocked(HTMLMediaElement.prototype.canPlayType).mockReturnValue('');
    repositoryMock.create.mockResolvedValue(
      transcodeSession({ state: 'RUNNING', playlistReady: true, encodedSeconds: 5 })
    );
    const wrapper = mountPlayer();
    await reportUnsupported(wrapper);
    await buttonWithText(wrapper, 'Create compatible stream').trigger('click');
    await flushPromises();

    const instance = hlsMock.instances[0];
    expect(instance?.attachMedia).toHaveBeenCalledWith(wrapper.get('video').element);
    const xhr = new XMLHttpRequest();
    instance?.config.xhrSetup?.(xhr);
    expect(xhr.withCredentials).toBe(true);
    instance?.handlers.get('mediaAttached')?.();
    expect(instance?.loadSource).toHaveBeenCalledWith('/mtl/api/media/transcode-sessions/session-1/playlist.m3u8');

    wrapper.unmount();
    expect(instance?.destroy).toHaveBeenCalled();
    expect(repositoryMock.cancel).not.toHaveBeenCalled();
  });

  it('asks hls.js to recover fatal network and media errors', async () => {
    vi.mocked(HTMLMediaElement.prototype.canPlayType).mockReturnValue('');
    repositoryMock.create.mockResolvedValue(transcodeSession({ state: 'RUNNING', playlistReady: true }));
    const wrapper = mountPlayer();
    await reportUnsupported(wrapper);
    await buttonWithText(wrapper, 'Create compatible stream').trigger('click');
    await flushPromises();

    const instance = hlsMock.instances[0];
    instance?.handlers.get('error')?.(null, { fatal: true, type: 'networkError' });
    instance?.handlers.get('error')?.(null, { fatal: true, type: 'mediaError' });

    expect(instance?.startLoad).toHaveBeenCalledTimes(1);
    expect(instance?.recoverMediaError).toHaveBeenCalledTimes(1);
  });

  it('lets hls.js recover a compatible-stream media element error before failing playback', async () => {
    vi.mocked(HTMLMediaElement.prototype.canPlayType).mockReturnValue('');
    repositoryMock.create.mockResolvedValue(
      transcodeSession({ state: 'COMPLETED', playlistReady: true, encodedSeconds: 120 })
    );
    const wrapper = mountPlayer();
    await reportUnsupported(wrapper);
    await buttonWithText(wrapper, 'Create compatible stream').trigger('click');
    await flushPromises();

    const instance = hlsMock.instances[0];
    instance?.handlers.get('manifestParsed')?.();
    setMediaError(wrapper, 3);
    await wrapper.get('video').trigger('error');

    expect(wrapper.find('[data-test="video-transcode-panel"]').exists()).toBe(false);
    expect(instance?.destroy).not.toHaveBeenCalled();

    instance?.handlers.get('error')?.(null, { fatal: true, type: 'mediaError' });
    instance?.handlers.get('error')?.(null, { fatal: true, type: 'mediaError' });
    expect(instance?.recoverMediaError).toHaveBeenCalledTimes(2);

    instance?.handlers.get('error')?.(null, { fatal: true, type: 'mediaError' });
    await flushPromises();
    expect(wrapper.get('[data-test="video-transcode-panel"]').text()).toContain(
      'The compatible stream could not be decoded by the browser.'
    );
  });

  it('ignores a stale status response after navigation', async () => {
    vi.useFakeTimers();
    let resolveStatus!: (value: VideoTranscodeSession) => void;
    repositoryMock.create.mockResolvedValue(transcodeSession());
    repositoryMock.get.mockReturnValue(
      new Promise<VideoTranscodeSession>((resolve) => {
        resolveStatus = resolve;
      })
    );
    const wrapper = mountPlayer();
    await reportUnsupported(wrapper);
    await buttonWithText(wrapper, 'Create compatible stream').trigger('click');
    await flushPromises();
    await vi.advanceTimersByTimeAsync(1_000);

    await wrapper.setProps({ mediaId: 18, src: '/mtl/api/media/get/18/content' });
    resolveStatus(transcodeSession({ state: 'RUNNING', playlistReady: true }));
    await flushPromises();

    expect(wrapper.get('video').attributes('src')).toBe('/mtl/api/media/get/18/content');
    expect(wrapper.find('[data-test="video-transcode-status"]').exists()).toBe(false);
  });

  it('keeps network failures in the ordinary media error flow', async () => {
    const wrapper = mountPlayer();
    setMediaError(wrapper, 2);

    await wrapper.get('video').trigger('error');

    expect(wrapper.emitted('media-error')).toHaveLength(1);
    expect(wrapper.find('[data-test="video-transcode-panel"]').exists()).toBe(false);
    expect(repositoryMock.create).not.toHaveBeenCalled();
  });
});
