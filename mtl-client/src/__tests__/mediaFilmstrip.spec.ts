import { flushPromises, mount } from '@vue/test-utils';
import { nextTick } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import MediaFilmstrip from '@/components/map/MediaFilmstrip.vue';
import { getMediaInfo } from '@/repositories/mediaRepository';

vi.mock('@/repositories/mediaRepository', () => ({
  getMediaInfo: vi.fn(),
  mediaContentUrl: (id: number, maxSize?: number) => `/media/${id}?maxSize=${maxSize}`,
}));

const wrappers: Array<ReturnType<typeof mount>> = [];

beforeEach(() => {
  vi.mocked(getMediaInfo)
    .mockReset()
    .mockImplementation(async (id: number) => ({
      id,
      fileName: `photo-${id}.jpg`,
      mediaKind: 'IMAGE',
    }));
});

function mountFilmstrip(props: Record<string, unknown> = {}) {
  const wrapper = mount(MediaFilmstrip, {
    props: {
      fileName: 'photo.jpg',
      mediaId: 2,
      mediaIds: [1, 2, 3],
      navIndex: 2,
      navTotal: 3,
      ...props,
    },
    attachTo: document.body,
  });
  wrappers.push(wrapper);
  return wrapper;
}

function dispatchMouse(element: EventTarget, type: string, init: { button?: number; clientX: number }) {
  const event = new Event(type, { bubbles: true, cancelable: true });
  Object.defineProperties(event, {
    button: { value: init.button ?? 0 },
    clientX: { value: init.clientX },
  });
  element.dispatchEvent(event);
}

afterEach(() => {
  for (const wrapper of wrappers.splice(0)) wrapper.unmount();
  document.body.innerHTML = '';
});

describe('MediaFilmstrip', () => {
  it('uses compact Nearby copy on phones while preserving the desktop label', () => {
    const wrapper = mountFilmstrip();
    const toggle = wrapper.get('[aria-controls="nearby-photos-strip"]');

    expect(wrapper.get('.mp__filmstrip-label-full').text()).toBe('Nearby media');
    expect(wrapper.get('.mp__filmstrip-label-compact').text()).toBe('Nearby');
    expect(toggle.attributes('aria-label')).toBe('Collapse nearby media, item 2 of 3');
  });

  it('keeps a large collection bounded and emits direct selection', async () => {
    const mediaIds = Array.from({ length: 100_000 }, (_, index) => index + 1);
    const wrapper = mountFilmstrip({ mediaId: 50_000, navIndex: 50_000, navTotal: 100_000, mediaIds });
    await nextTick();

    expect(wrapper.findAll('.mp__filmstrip-item')).toHaveLength(12);
    const toggle = wrapper.get('[aria-controls="nearby-photos-strip"]');
    expect(toggle.attributes('aria-expanded')).toBe('true');
    expect(toggle.attributes('aria-label')).toBe('Collapse nearby media, item 50000 of 100000');
    expect(toggle.get('i').classes()).toContain('bi-chevron-down');
    expect(toggle.text()).toContain('50000/100000');
    await toggle.trigger('click');
    expect(toggle.attributes('aria-expanded')).toBe('false');
    expect(toggle.get('i').classes()).toContain('bi-chevron-up');
    expect(wrapper.get('#nearby-photos-strip').attributes('style')).toContain('display: none');
    await toggle.trigger('click');

    await wrapper.get('[data-media-id="50001"]').trigger('click');
    expect(wrapper.emitted('select')).toEqual([[50_001]]);

    const filmstrip = wrapper.get('#nearby-photos-strip');
    Object.defineProperty(filmstrip.element, 'scrollWidth', { configurable: true, value: 19_200 });
    Object.defineProperty(filmstrip.element, 'clientWidth', { configurable: true, value: 600 });
    Object.defineProperty(filmstrip.element, 'scrollLeft', { configurable: true, value: 18_600, writable: true });
    await filmstrip.trigger('scroll');
    await nextTick();
    expect(wrapper.findAll('.mp__filmstrip-item').length).toBeLessThanOrEqual(16);
    expect(wrapper.get('.mp__filmstrip-item').attributes('data-media-id')).toBe('190');

    (filmstrip.element as HTMLElement).scrollLeft = 0;
    await filmstrip.trigger('scroll');
    await nextTick();
    expect(wrapper.get('.mp__filmstrip-item').attributes('data-media-id')).toBe('1');
  });

  it('requests adjacent server pages at collection boundaries', async () => {
    const wrapper = mountFilmstrip({
      mediaId: 300,
      navIndex: 300,
      navTotal: 1_000,
      mediaIds: Array.from({ length: 200 }, (_, index) => index + 201),
      mediaOffset: 200,
    });
    const filmstrip = wrapper.get('#nearby-photos-strip');
    Object.defineProperty(filmstrip.element, 'scrollWidth', { configurable: true, value: 19_200 });
    Object.defineProperty(filmstrip.element, 'clientWidth', { configurable: true, value: 600 });
    Object.defineProperty(filmstrip.element, 'scrollLeft', { configurable: true, value: 18_600, writable: true });

    await filmstrip.trigger('scroll');
    expect(wrapper.emitted('request-page')).toEqual([[1]]);

    await wrapper.setProps({
      mediaId: 500,
      navIndex: 500,
      mediaIds: Array.from({ length: 200 }, (_, index) => index + 401),
      mediaOffset: 400,
    });
    (filmstrip.element as HTMLElement).scrollLeft = 0;
    await filmstrip.trigger('scroll');

    expect(wrapper.emitted('request-page')).toEqual([[1], [-1]]);
  });

  it('changes the virtual window incrementally while scrolling', async () => {
    const wrapper = mountFilmstrip({
      mediaId: 100,
      navIndex: 100,
      navTotal: 1_000,
      mediaIds: Array.from({ length: 200 }, (_, index) => index + 1),
    });
    await nextTick();
    const filmstrip = wrapper.get('#nearby-photos-strip');
    Object.defineProperty(filmstrip.element, 'clientWidth', { configurable: true, value: 600 });
    Object.defineProperty(filmstrip.element, 'scrollLeft', {
      configurable: true,
      value: 9_456,
      writable: true,
    });
    await filmstrip.trigger('scroll');
    const before = wrapper.findAll('.mp__filmstrip-item').map((item) => item.attributes('data-media-id'));

    (filmstrip.element as HTMLElement).scrollLeft += 96;
    await filmstrip.trigger('scroll');

    const after = wrapper.findAll('.mp__filmstrip-item').map((item) => item.attributes('data-media-id'));
    expect(after).toHaveLength(before.length);
    expect(after.filter((id) => before.includes(id))).toHaveLength(before.length - 1);
    expect(Number(after[0])).toBe(Number(before[0]) + 1);
  });

  it('drags horizontally with a mouse without selecting or dragging an image', async () => {
    const wrapper = mountFilmstrip();
    await nextTick();
    const filmstrip = wrapper.get('#nearby-photos-strip');
    Object.defineProperty(filmstrip.element, 'scrollLeft', { configurable: true, value: 0, writable: true });

    dispatchMouse(filmstrip.element, 'mousedown', { clientX: 500 });
    dispatchMouse(window, 'mousemove', { clientX: 300 });
    dispatchMouse(window, 'mouseup', { clientX: 300 });
    await nextTick();

    expect((filmstrip.element as HTMLElement).scrollLeft).toBe(200);
    expect(wrapper.get('.mp__filmstrip-item img').attributes('draggable')).toBe('false');
    await wrapper.get('[data-media-id="2"]').trigger('click');
    expect(wrapper.emitted('select')).toBeUndefined();
    await wrapper.get('[data-media-id="2"]').trigger('click');
    expect(wrapper.emitted('select')).toEqual([[2]]);
  });

  it('filters invalid and duplicate media identifiers', () => {
    const wrapper = mountFilmstrip({
      mediaId: 2,
      mediaIds: [0, 1, 1, -4, 2, Number.NaN, 3.5],
      navTotal: 2,
    });

    expect(wrapper.findAll('.mp__filmstrip-item')).toHaveLength(2);
    expect(wrapper.get('[data-media-id="1"] img').attributes('src')).toBe('/media/1?maxSize=192');
    expect(wrapper.get('[data-media-id="2"]').attributes('aria-current')).toBe('true');
  });

  it('marks video thumbnails without replacing the generated poster', () => {
    const wrapper = mountFilmstrip({ videoMediaIds: [2, 3] });

    expect(wrapper.find('[data-media-id="1"] .mp__filmstrip-video').exists()).toBe(false);
    expect(wrapper.get('[data-media-id="2"] .mp__filmstrip-video').exists()).toBe(true);
    expect(wrapper.get('[data-media-id="2"]').attributes('aria-label')).toBe('Current video');
    expect(wrapper.get('[data-media-id="3"]').attributes('aria-label')).toBe('Open video 3');
    expect(wrapper.get('[data-media-id="2"] img').attributes('src')).toBe('/media/2?maxSize=192');
  });

  it('resolves video kinds for visible map items when the parent only knows identifiers', async () => {
    vi.mocked(getMediaInfo).mockImplementation(async (id: number) => ({
      id,
      fileName: id === 3 ? 'clip.mp4' : `photo-${id}.jpg`,
      mediaKind: id === 3 ? 'VIDEO' : 'IMAGE',
    }));

    const wrapper = mountFilmstrip({ mediaId: 1, currentMediaIsVideo: false, videoMediaIds: [] });
    await flushPromises();

    expect(wrapper.get('[data-media-id="3"] .mp__filmstrip-video').exists()).toBe(true);
    expect(wrapper.get('[data-media-id="3"]').attributes('aria-label')).toBe('Open video 3');
  });
});
