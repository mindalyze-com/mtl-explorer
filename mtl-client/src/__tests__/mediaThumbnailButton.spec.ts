import { flushPromises, mount } from '@vue/test-utils';
import { defineComponent } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import MediaThumbnailButton from '@/components/media/MediaThumbnailButton.vue';
import { fetchMediaImage, MAX_MEDIA_BUSY_RETRIES, MEDIA_BUSY_RETRY_DELAY_MS } from '@/utils/fetchMediaImage';

const Thumbnail = defineComponent({
  components: { MediaThumbnailButton },
  props: { src: { type: String, required: true }, enabled: { type: Boolean, default: true } },
  template: `<MediaThumbnailButton v-slot="{ imageProps }" :src="src" :enabled="enabled"
    aria-label="Open test photo" @click="$emit('open')">
    <img v-if="enabled" v-bind="imageProps" alt="Test photo" />
  </MediaThumbnailButton>`,
});

describe('media thumbnail recovery', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:recovered-thumbnail');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
  });
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it('keeps native lazy loading and recovers only after a load error', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(new Response(null, { status: 503 }))
      .mockResolvedValueOnce(new Response('jpeg'));
    vi.stubGlobal('fetch', fetchMock);
    const wrapper = mount(Thumbnail, { props: { src: '/photo' } });
    expect(wrapper.get('img').attributes('loading')).toBe('lazy');
    expect(fetchMock).not.toHaveBeenCalled();
    await wrapper.get('img').trigger('error');
    await flushPromises();
    expect(wrapper.get('button').attributes('aria-busy')).toBe('true');
    await vi.advanceTimersByTimeAsync(MEDIA_BUSY_RETRY_DELAY_MS);
    await flushPromises();
    expect(wrapper.get('img').attributes('src')).toBe('blob:recovered-thumbnail');
    await wrapper.get('img').trigger('load');
    expect(wrapper.text()).not.toContain('Loading');
    await wrapper.get('button').trigger('click');
    expect(wrapper.emitted('open')).toHaveLength(1);
    wrapper.unmount();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:recovered-thumbnail');
  });

  it('opens a failed thumbnail immediately and also retries its preview', async () => {
    const fetchMock = vi.fn().mockImplementation(async () => new Response(null, { status: 503 }));
    vi.stubGlobal('fetch', fetchMock);
    const wrapper = mount(Thumbnail, { props: { src: '/photo' } });
    await wrapper.get('img').trigger('error');
    await vi.advanceTimersByTimeAsync(MAX_MEDIA_BUSY_RETRIES * MEDIA_BUSY_RETRY_DELAY_MS);
    await flushPromises();
    expect(wrapper.get('button').attributes('aria-label')).toBe('Open test photo');
    expect(wrapper.text()).toContain('No preview');
    expect(fetchMock).toHaveBeenCalledTimes(MAX_MEDIA_BUSY_RETRIES + 1);
    fetchMock.mockResolvedValue(new Response('jpeg'));
    await wrapper.get('button').trigger('click');
    await flushPromises();
    expect(wrapper.emitted('open')).toHaveLength(1);
    expect(wrapper.get('img').attributes('src')).toBe('blob:recovered-thumbnail');
    await wrapper.get('img').trigger('load');
    expect(wrapper.get('button').attributes('aria-label')).toBe('Open test photo');
    wrapper.unmount();
  });

  it('cancels recovery when its source changes, is hidden, or is removed', async () => {
    const fetchMock = vi.fn().mockImplementation(async () => new Response(null, { status: 503 }));
    vi.stubGlobal('fetch', fetchMock);
    const wrapper = mount(Thumbnail, { props: { src: '/old-photo' } });
    await wrapper.get('img').trigger('error');
    await flushPromises();
    await wrapper.setProps({ src: '/new-photo' });
    await wrapper.get('img').trigger('error');
    await flushPromises();
    await wrapper.setProps({ enabled: false });
    await vi.advanceTimersByTimeAsync(MEDIA_BUSY_RETRY_DELAY_MS * 2);
    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual(['/old-photo', '/new-photo']);
    expect(wrapper.find('img').exists()).toBe(false);
    await wrapper.setProps({ enabled: true });
    await wrapper.get('img').trigger('error');
    await flushPromises();
    wrapper.unmount();
    await vi.advanceTimersByTimeAsync(MEDIA_BUSY_RETRY_DELAY_MS * 2);
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it('offers manual recovery when a fetched image cannot be decoded', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('invalid image')));
    const wrapper = mount(Thumbnail, { props: { src: '/photo' } });
    await wrapper.get('img').trigger('error');
    await flushPromises();
    await wrapper.get('img').trigger('error');
    expect(wrapper.text()).toContain('No preview');
    wrapper.unmount();
  });

  it('lets a selected preview load before a queued thumbnail recovery', async () => {
    let completeFirst!: (response: Response) => void;
    const firstResponse = new Promise<Response>((resolve) => {
      completeFirst = resolve;
    });
    const fetchMock = vi
      .fn()
      .mockReturnValueOnce(firstResponse)
      .mockImplementation(async () => new Response('jpeg'));
    vi.stubGlobal('fetch', fetchMock);
    const first = fetchMediaImage('/active', new AbortController().signal);
    const wrapper = mount(Thumbnail, { props: { src: '/thumbnail' } });
    await wrapper.get('img').trigger('error');
    const preview = fetchMediaImage('/selected-preview', new AbortController().signal);
    completeFirst(new Response('jpeg'));
    await Promise.all([first, preview]);
    await flushPromises();
    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual(['/active', '/selected-preview', '/thumbnail']);
    wrapper.unmount();
  });
});
