import { flushPromises, mount } from '@vue/test-utils';
import { computed, defineComponent, nextTick, ref } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import MediaFilmstrip from '@/components/map/MediaFilmstrip.vue';
import MediaPreview from '@/components/map/MediaPreview.vue';
import MediaViewerViewToggle from '@/components/map/MediaViewerViewToggle.vue';
import BottomSheet from '@/components/ui/BottomSheet.vue';
import { IMMERSIVE_CONTROLS_IDLE_MS } from '@/components/ui/useImmersiveSheetControls';
import { saveMediaTimeCorrections } from '@/repositories/mediaRepository';

const previewMockState = vi.hoisted(() => ({
  fileName: 'photo.jpg',
  isVideo: false,
  mediaUrl: '/preview.jpg',
  posterUrl: undefined as string | undefined,
  hasLoadError: false,
  retryLoad: vi.fn(),
}));

vi.mock('@/components/map/useMediaPreview', () => ({
  useMediaPreview: (props: {
    mediaId: number | null;
    canGoPrev?: boolean;
    canGoNext?: boolean;
    navIndex?: number;
    navTotal?: number;
  }) => ({
    activeMediaId: computed(() => props.mediaId),
    isSwapPending: ref(false),
    isCrossFading: ref(false),
    isHighResolutionPending: ref(true),
    backSrc: ref(null),
    displayUrl: ref('/preview.jpg'),
    mediaUrl: computed(() => previewMockState.mediaUrl),
    posterUrl: computed(() => previewMockState.posterUrl),
    downloadUrl: ref('/original.jpg'),
    fileName: computed(() => previewMockState.fileName),
    filePath: ref('/photos/photo.jpg'),
    date: ref('01/01/2026, 14:35'),
    captureTimeSource: ref('Embedded GPS time'),
    camera: ref('Example Camera'),
    fileSummary: ref('JPEG · 4.8 MB'),
    dimensions: ref('4032 × 3024 · 12.2 MP'),
    exposure: ref('26 mm eq. · ƒ/1.8 · 1/250 s · ISO 50'),
    lens: ref('Example 6.9mm f/1.8'),
    videoDetails: ref('1:24 · 3840 × 2160 · 30 fps'),
    codecs: ref('HEVC · AAC'),
    modified: ref('18/08/2026, 10:15'),
    gpsAltitude: ref('488 m'),
    isVideo: computed(() => previewMockState.isVideo),
    hasActiveMedia: computed(() => props.mediaId != null),
    showInitialLoading: ref(false),
    showNavigation: computed(() => (props.navTotal ?? 0) > 1),
    canGoPrev: computed(() => props.canGoPrev ?? false),
    canGoNext: computed(() => props.canGoNext ?? false),
    hasLoadError: computed(() => previewMockState.hasLoadError),
    loadError: ref(''),
    retryLoad: previewMockState.retryLoad,
    onMediaError: vi.fn(),
  }),
}));

vi.mock('@/repositories/mediaRepository', () => ({
  mediaContentUrl: (id: number, maxSize?: number) => `/media/${id}?maxSize=${maxSize}`,
  saveMediaTimeCorrections: vi.fn(),
}));

const wrappers: Array<ReturnType<typeof mount>> = [];
const MediaLocationMiniMapStub = defineComponent({
  name: 'MediaLocationMiniMap',
  props: {
    latitude: Number,
    longitude: Number,
    positionSource: String,
    positionEstimated: Boolean,
    trackCoordinates: Array,
    overviewBounds: Array,
  },
  template: '<div data-test="media-location-mini-map"></div>',
});

function mountPreview(props: Record<string, unknown> = {}) {
  const wrapper = mount(MediaPreview, {
    props: {
      mediaId: 2,
      canGoPrev: true,
      canGoNext: true,
      navIndex: 2,
      navTotal: 3,
      mediaIds: [1, 2, 3],
      ...props,
    },
    attachTo: document.body,
    global: {
      stubs: { MediaLocationMiniMap: MediaLocationMiniMapStub },
    },
  });
  wrappers.push(wrapper);
  return wrapper;
}

function dispatchPointer(
  element: Element,
  type: string,
  init: { button?: number; pointerId: number; clientX: number; clientY: number }
) {
  const event = new Event(type, { bubbles: true, cancelable: true });
  Object.defineProperties(event, {
    button: { value: init.button ?? 0 },
    pointerId: { value: init.pointerId },
    clientX: { value: init.clientX },
    clientY: { value: init.clientY },
  });
  element.dispatchEvent(event);
}

beforeEach(() => {
  vi.mocked(saveMediaTimeCorrections).mockReset().mockResolvedValue(undefined);
  previewMockState.retryLoad.mockReset();
});

afterEach(() => {
  for (const wrapper of wrappers.splice(0)) wrapper.unmount();
  document.body.innerHTML = '';
  vi.restoreAllMocks();
  previewMockState.fileName = 'photo.jpg';
  previewMockState.isVideo = false;
  previewMockState.mediaUrl = '/preview.jpg';
  previewMockState.posterUrl = undefined;
  previewMockState.hasLoadError = false;
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

function mountViewer() {
  vi.stubGlobal(
    'ResizeObserver',
    class {
      observe() {}
      disconnect() {}
    }
  );
  const wrapper = mount(
    defineComponent({
      components: { BottomSheet, MediaPreview, MediaViewerViewToggle },
      setup: () => ({ open: ref(true), details: ref(true), map: ref(true), photos: ref(true) }),
      template: `
        <BottomSheet v-model="open" title="Test photo viewer" native-fullscreen fullscreen-control-labels
          immersive :detents="[{ id: 'normal', height: '400px' }]">
          <template #header-actions><MediaViewerViewToggle v-model="details" v-model:map-visible="map" v-model:photos-visible="photos" /></template>
          <MediaPreview :media-id="2" :nav-index="2" :nav-total="3" :media-ids="[1, 2, 3]"
            :can-go-prev="true" :can-go-next="true" v-model:details-visible="details" v-model:map-visible="map" :photos-visible="photos" :position-lat="47" :position-lng="8" />
        </BottomSheet>
      `,
    }),
    { attachTo: document.body, global: { stubs: { MediaLocationMiniMap: MediaLocationMiniMapStub } } }
  );
  wrappers.push(wrapper);
  return wrapper;
}

function installFullscreen(sheet: HTMLElement) {
  Object.defineProperty(document, 'fullscreenElement', { configurable: true, value: null });
  Object.defineProperty(document, 'exitFullscreen', {
    configurable: true,
    value: vi.fn(async () => {
      Object.defineProperty(document, 'fullscreenElement', { configurable: true, value: null });
      document.dispatchEvent(new Event('fullscreenchange'));
    }),
  });
  Object.defineProperty(sheet, 'requestFullscreen', {
    configurable: true,
    value: vi.fn(async () => {
      Object.defineProperty(document, 'fullscreenElement', { configurable: true, value: sheet });
      document.dispatchEvent(new Event('fullscreenchange'));
    }),
  });
}
async function togglePhotoOnly(sheet: HTMLElement) {
  sheet.querySelector<HTMLButtonElement>('.media-viewer-panel-options button')!.click();
  await nextTick();
}
describe('photo viewer presentation', () => {
  it('does not navigate photos while the View menu has keyboard focus', async () => {
    const wrapper = mountViewer();
    await flushPromises();
    const menu = document.querySelector<HTMLDetailsElement>('.media-viewer-panel-menu')!;
    const summary = menu.querySelector('summary')!;
    menu.open = true;
    summary.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft', bubbles: true }));
    expect(wrapper.getComponent(MediaPreview).emitted('prev')).toBeUndefined();
    menu.open = false;
    summary.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    expect(wrapper.getComponent(MediaPreview).emitted('next')).toHaveLength(1);
  });
  afterEach(() => {
    delete (document as Document & { fullscreenElement?: Element | null }).fullscreenElement;
    delete (document as Document & { exitFullscreen?: () => Promise<void> }).exitFullscreen;
  });
  it('preserves panel choices and restores Expand after real fullscreen', async () => {
    mountViewer();
    await flushPromises();
    const sheet = document.querySelector<HTMLElement>('.sheet')!;
    installFullscreen(sheet);
    const normalHeight = sheet.style.height;
    const expand = sheet.querySelector<HTMLButtonElement>('.sheet-fullscreen-btn')!;
    const fullscreen = sheet.querySelector<HTMLButtonElement>('.sheet-native-fullscreen-btn')!;
    expand.click();
    await nextTick();
    expect(sheet.classList.contains('sheet--fullscreen')).toBe(true);
    await togglePhotoOnly(sheet);
    expect(sheet.querySelector('[data-test="media-metadata"]')).toBeNull();
    fullscreen.click();
    await flushPromises();
    expect(sheet.requestFullscreen).toHaveBeenCalledWith({ navigationUI: 'hide' });
    expect(document.fullscreenElement).toBe(sheet);
    expect(sheet.classList.contains('sheet--native-fullscreen')).toBe(true);
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    await flushPromises();
    expect(sheet.classList.contains('sheet--native-fullscreen')).toBe(false);
    expect(sheet.classList.contains('sheet--fullscreen')).toBe(true);
    expect(sheet.querySelector('[data-test="media-metadata"]')).toBeNull();
    expand.click();
    await nextTick();
    expect(sheet.style.height).toBe(normalHeight);
    await togglePhotoOnly(sheet);
    expect(sheet.querySelector('[data-test="media-metadata"]')).not.toBeNull();
  });
  it('controls Details, Map and Photos independently and restores a custom selection', async () => {
    mountViewer();
    await flushPromises();
    const sheet = document.querySelector<HTMLElement>('.sheet')!;
    const strip = sheet.querySelector<HTMLElement>('.mp__filmstrip-section')!;
    const [details, map, photos] = sheet.querySelectorAll<HTMLInputElement>('.media-viewer-panel-options input');
    details.click();
    await nextTick();
    expect(sheet.querySelector('.mp__more-details')).toBeNull();
    expect(sheet.querySelector('[data-test="media-location-mini-map"]')).not.toBeNull();
    expect(strip.style.display).not.toBe('none');
    photos.click();
    await nextTick();
    expect(strip.style.display).toBe('none');
    await togglePhotoOnly(sheet);
    await togglePhotoOnly(sheet);
    expect(map.checked).toBe(true);
    expect(details.checked).toBe(false);
    expect(photos.checked).toBe(false);
    map.click();
    details.click();
    await nextTick();
    expect(sheet.querySelector('.mp__more-details')).not.toBeNull();
    expect(sheet.querySelector('[data-test="media-location-mini-map"]')).toBeNull();
    expect(sheet.querySelector('.mp__filmstrip-section')).toBe(strip);
  });
  it('preserves More and the filmstrip instance when panels are hidden and restored', async () => {
    mountViewer();
    await flushPromises();
    const sheet = document.querySelector<HTMLElement>('.sheet')!;
    const strip = sheet.querySelector('.mp__filmstrip-section');
    sheet.querySelector<HTMLElement>('.mp__more-details summary')!.click();
    await nextTick();
    await togglePhotoOnly(sheet);
    await togglePhotoOnly(sheet);
    expect(sheet.querySelector('.mp__filmstrip-section')).toBe(strip);
    expect(sheet.querySelector('.mp__more-details')?.hasAttribute('open')).toBe(true);
  });
  it('reveals controls at the top edge and holds the toolbar for its menu or keyboard focus', async () => {
    mountViewer();
    await flushPromises();
    const sheet = document.querySelector<HTMLElement>('.sheet')!;
    installFullscreen(sheet);
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
    sheet.querySelector<HTMLButtonElement>('.sheet-native-fullscreen-btn')!.click();
    await flushPromises();
    await vi.advanceTimersByTimeAsync(IMMERSIVE_CONTROLS_IDLE_MS);
    expect(sheet.classList.contains('sheet--controls-hidden')).toBe(true);
    expect(sheet.querySelector('[data-test="media-metadata"]')).not.toBeNull();
    sheet.dispatchEvent(new MouseEvent('pointermove', { bubbles: true, clientY: 400 }));
    await nextTick();
    expect(sheet.classList.contains('sheet--controls-hidden')).toBe(true);
    sheet.dispatchEvent(new MouseEvent('pointermove', { bubbles: true, clientY: 5 }));
    await nextTick();
    expect(sheet.classList.contains('sheet--controls-hidden')).toBe(false);
    const menu = sheet.querySelector<HTMLDetailsElement>('.media-viewer-panel-menu')!;
    menu.open = true;
    sheet.dispatchEvent(new MouseEvent('pointermove', { bubbles: true, clientY: 400 }));
    await vi.advanceTimersByTimeAsync(IMMERSIVE_CONTROLS_IDLE_MS);
    expect(sheet.classList.contains('sheet--controls-hidden')).toBe(false);
    menu.open = false;
    await vi.advanceTimersByTimeAsync(IMMERSIVE_CONTROLS_IDLE_MS);
    expect(sheet.classList.contains('sheet--controls-hidden')).toBe(true);
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true }));
    await nextTick();
    expect(sheet.classList.contains('sheet--controls-hidden')).toBe(false);
    expect(sheet.querySelector('.sheet-drag-zone')?.contains(document.activeElement)).toBe(true);
  });
  it('keeps Expand and reports unsupported fullscreen when the browser rejects the request', async () => {
    mountViewer();
    await flushPromises();
    const sheet = document.querySelector<HTMLElement>('.sheet')!;
    Object.defineProperty(sheet, 'requestFullscreen', { value: vi.fn().mockRejectedValue(new Error('Unavailable')) });
    sheet.querySelector<HTMLButtonElement>('.sheet-fullscreen-btn')!.click();
    await nextTick();
    sheet.querySelector<HTMLButtonElement>('.sheet-native-fullscreen-btn')!.click();
    await flushPromises();
    expect(sheet.classList.contains('sheet--native-fullscreen')).toBe(false);
    expect(sheet.classList.contains('sheet--fullscreen')).toBe(true);
    expect(sheet.querySelector('.sheet-fullscreen-status')?.textContent).toContain('Use Expand');
    expect(sheet.querySelector('.sheet-native-fullscreen-btn')?.textContent?.trim()).toBe('Full screen');
  });
});

describe('MediaPreview interactions', () => {
  it('retries the current failed thumbnail without replacing its neighbors or collapsing the filmstrip', async () => {
    previewMockState.hasLoadError = true;
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(new Response(null, { status: 500 }))
      .mockResolvedValueOnce(new Response('recovered jpeg', { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:recovered-thumbnail');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    const wrapper = mountPreview();
    const thumbnail = wrapper.get('[data-media-id="2"] img');
    const neighbor = wrapper.get('[data-media-id="1"] img').element;
    const filmstrip = wrapper.get('.mp__filmstrip').element;
    await thumbnail.trigger('error');
    await flushPromises();
    expect(fetchMock).toHaveBeenCalledTimes(1);

    await wrapper.get('.mp__error-btn--primary').trigger('click');
    expect(previewMockState.retryLoad).toHaveBeenCalledTimes(1);
    const retriedThumbnail = wrapper.get('[data-media-id="2"] img');
    expect(retriedThumbnail.element).not.toBe(thumbnail.element);
    expect(wrapper.get('[data-media-id="1"] img').element).toBe(neighbor);
    expect(wrapper.get('.mp__filmstrip').element).toBe(filmstrip);
    expect(wrapper.get('[aria-controls="nearby-photos-strip"]').attributes('aria-expanded')).toBe('true');
    expect(retriedThumbnail.attributes('src')).toBe('/media/2?maxSize=192');

    await retriedThumbnail.trigger('error');
    await flushPromises();
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(retriedThumbnail.attributes('src')).toBe('blob:recovered-thumbnail');
    await wrapper.setProps({ mediaId: 3, navIndex: 3 });
    await wrapper.setProps({ mediaId: 2, navIndex: 2 });
    expect(wrapper.get('[data-media-id="2"] img').attributes('src')).toBe('blob:recovered-thumbnail');
    wrapper.unmount();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:recovered-thumbnail');
  });

  it('signals that the displayed photo is still loading at full resolution', () => {
    const wrapper = mountPreview();

    expect(wrapper.get('.mp__resolution-status').text()).toContain('Loading full resolution');
  });

  it('renders the selected video paused with native controls, a poster play button, and a filmstrip marker', () => {
    previewMockState.fileName = 'clip.mp4';
    previewMockState.isVideo = true;
    previewMockState.mediaUrl = '/video.mp4';
    previewMockState.posterUrl = '/video-poster.jpg';
    const wrapper = mountPreview();

    const video = wrapper.get('video.mp__media--video');
    expect(video.attributes()).toMatchObject({
      src: '/video.mp4',
      poster: '/video-poster.jpg',
      controls: '',
      playsinline: '',
      preload: 'metadata',
    });
    expect(video.attributes('autoplay')).toBeUndefined();
    expect(video.attributes('muted')).toBeUndefined();
    expect(wrapper.get('.mp__video-play').attributes('aria-label')).toBe('Play clip.mp4');
    expect(wrapper.find('img.mp__media--image').exists()).toBe(false);
    expect(wrapper.get('.mp__details-header').text()).toContain('Video 2 of 3');
    expect(wrapper.get('[data-media-id="2"] .mp__filmstrip-video').exists()).toBe(true);
    expect(wrapper.get('[data-media-id="2"]').attributes('aria-label')).toBe('Current video');
  });

  it('plays from the poster or central button and restores the button after the video ends', async () => {
    previewMockState.fileName = 'clip.mp4';
    previewMockState.isVideo = true;
    previewMockState.mediaUrl = '/video.mp4';
    previewMockState.posterUrl = '/video-poster.jpg';
    const play = vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue();
    const wrapper = mountPreview();
    const video = wrapper.get('video.mp__media--video');

    await video.trigger('click');
    expect(play).toHaveBeenCalledTimes(1);

    video.element.dispatchEvent(new Event('play'));
    await nextTick();
    expect(wrapper.find('.mp__video-play').exists()).toBe(false);

    video.element.dispatchEvent(new Event('ended'));
    await nextTick();
    expect(wrapper.get('.mp__video-play').attributes('aria-label')).toBe('Play clip.mp4');

    await wrapper.get('.mp__video-play').trigger('click');
    expect(play).toHaveBeenCalledTimes(2);
    expect((video.element as HTMLVideoElement).currentTime).toBe(0);
  });

  it('returns to a paused poster when media navigation selects another video', async () => {
    previewMockState.fileName = 'clip.mp4';
    previewMockState.isVideo = true;
    previewMockState.mediaUrl = '/video.mp4';
    previewMockState.posterUrl = '/video-poster.jpg';
    const wrapper = mountPreview();
    const video = wrapper.get('video.mp__media--video');

    video.element.dispatchEvent(new Event('play'));
    await nextTick();
    expect(wrapper.find('.mp__video-play').exists()).toBe(false);

    await wrapper.setProps({ mediaId: 3 });
    expect(wrapper.get('.mp__video-play').exists()).toBe(true);
  });

  it('toggles metadata and identifies estimated route positions', async () => {
    const wrapper = mountPreview({
      positionSource: 'TRACK_INTERPOLATED',
      positionEstimated: true,
      trackCoordinates: [
        [8.5, 47.55],
        [8.51, 47.57],
      ],
      positionTimeDeltaSeconds: 3,
    });

    expect(wrapper.get('[data-test="media-position-source"]').text()).toContain('Nearest track point 3s away');
    expect(wrapper.get('[data-test="media-position-source"]').text()).toContain('3s away');
    expect(wrapper.get('.mp__details-list').text()).toContain('01/01/2026, 14:35');
    expect(wrapper.get('.mp__details-list').text()).toContain('Embedded GPS time');

    const toggle = wrapper.get('[aria-label="Hide media details"]');
    await toggle.trigger('click');
    expect(wrapper.emitted('update:detailsVisible')).toEqual([[false]]);
    await wrapper.setProps({ detailsVisible: false });
    expect(wrapper.find('[data-test="media-metadata"]').exists()).toBe(false);
  });

  it('keeps technical photo metadata in a progressive disclosure', async () => {
    const wrapper = mountPreview({ positionLat: 47.5605, positionLng: 8.505778 });

    expect(wrapper.get('[data-test="media-file-summary"]').text()).toContain('JPEG · 4.8 MB');
    const moreDetails = wrapper.get('[data-test="media-more-details"]');
    expect(moreDetails.attributes('open')).toBeUndefined();

    await moreDetails.get('summary').trigger('click');

    expect(moreDetails.attributes('open')).toBe('');
    expect(wrapper.get('[data-test="media-photo-exposure"]').text()).toContain('1/250 s');
    expect(wrapper.get('[data-test="media-coordinates"]').text()).toContain('47.56050, 8.50578');
    expect(moreDetails.text()).toContain('4032 × 3024 · 12.2 MP');
    expect(moreDetails.text()).toContain('Example 6.9mm f/1.8');
    expect(moreDetails.text()).toContain('488 m');
  });

  it('keeps more details expanded when navigating between media', async () => {
    const wrapper = mountPreview();

    await wrapper.get('[data-test="media-more-details"] summary').trigger('click');
    expect(wrapper.get('[data-test="media-more-details"]').attributes('open')).toBe('');

    await wrapper.setProps({ mediaId: 3, navIndex: 3 });

    expect(wrapper.get('[data-test="media-more-details"]').attributes('open')).toBe('');
  });

  it('shows video-specific stream details instead of photo exposure', async () => {
    previewMockState.fileName = 'clip.mp4';
    previewMockState.isVideo = true;
    const wrapper = mountPreview();

    await wrapper.get('[data-test="media-more-details"] summary').trigger('click');

    expect(wrapper.get('[data-test="media-video-details"]').text()).toContain('1:24 · 3840 × 2160 · 30 fps');
    expect(wrapper.get('[data-test="media-more-details"]').text()).toContain('HEVC · AAC');
    expect(wrapper.find('[data-test="media-photo-exposure"]').exists()).toBe(false);
  });

  it('identifies user-assigned and ambiguous positions', () => {
    const wrapper = mountPreview({
      positionSource: 'USER_ASSIGNED',
      positionAmbiguous: true,
      positionTimeDeltaSeconds: 0,
    });

    expect(wrapper.get('[data-test="media-position-source"]').text()).toContain('Set by you');
    expect(wrapper.get('[data-test="media-position-source"]').text()).toContain('Multiple activities matched');
  });

  it('shows explicit unknown position metadata and clears a saved camera correction', async () => {
    const wrapper = mountPreview({
      positionUnknown: true,
      positionLat: 46.94811,
      positionLng: 7.44755,
      timeSource: 'EXIF_DATE_TAKEN',
      appliedCameraOffsetSeconds: 3_603,
    });

    expect(wrapper.get('[data-test="media-position-source"]').text()).toContain('Position unknown');
    expect(wrapper.getComponent(MediaLocationMiniMapStub).props()).toMatchObject({
      latitude: 46.94811,
      longitude: 7.44755,
    });
    await wrapper.get('.mp__clear-correction').trigger('click');
    await flushPromises();

    expect(saveMediaTimeCorrections).toHaveBeenCalledWith({ mediaIds: [2], offsetSeconds: 0 });
    expect(wrapper.emitted('time-correction-cleared')).toEqual([[2]]);
    expect(wrapper.find('.mp__clear-correction').exists()).toBe(false);
  });

  it('shows a toggleable mini map only for valid photo coordinates', async () => {
    const wrapper = mountPreview({
      positionLat: 47.5605,
      positionLng: 8.505778,
      positionSource: 'TRACK_INTERPOLATED',
      positionEstimated: true,
      trackCoordinates: [
        [8.5, 47.55],
        [8.51, 47.57],
      ],
      overviewBounds: [
        [8.4, 47.5],
        [8.6, 47.7],
      ],
    });

    const miniMap = wrapper.getComponent(MediaLocationMiniMapStub);
    expect(miniMap.props()).toMatchObject({
      latitude: 47.5605,
      longitude: 8.505778,
      positionSource: 'TRACK_INTERPOLATED',
      positionEstimated: true,
      overviewBounds: [
        [8.4, 47.5],
        [8.6, 47.7],
      ],
    });
    expect(wrapper.get('.mp__filmstrip-heading').text()).toContain('Nearby media');
    await wrapper.get('.mp__open-map-btn').trigger('click');
    expect(wrapper.emitted('open-on-map')).toHaveLength(1);

    await wrapper.get('[aria-label="Hide media details"]').trigger('click');
    await wrapper.setProps({ detailsVisible: false });
    expect(wrapper.find('[data-test="media-location-mini-map"]').exists()).toBe(false);
    await wrapper.setProps({ detailsVisible: true });
    expect(wrapper.find('[data-test="media-location-mini-map"]').exists()).toBe(true);

    const withoutCoordinates = mountPreview({ positionLat: null, positionLng: null });
    expect(withoutCoordinates.find('.mp__location-section').exists()).toBe(false);
    expect(withoutCoordinates.find('[data-test="media-location-mini-map"]').exists()).toBe(false);
  });

  it('navigates with arrow keys without stealing keys from editable controls', async () => {
    const wrapper = mountPreview();

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft', bubbles: true }));
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    expect(wrapper.emitted('prev')).toHaveLength(1);
    expect(wrapper.emitted('next')).toHaveLength(1);

    const input = document.createElement('input');
    document.body.append(input);
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    expect(wrapper.emitted('next')).toHaveLength(1);
  });

  it('forwards filmstrip selection and page requests', async () => {
    const wrapper = mountPreview();
    const filmstrip = wrapper.getComponent(MediaFilmstrip);

    filmstrip.vm.$emit('select', 3);
    filmstrip.vm.$emit('request-page', 1);
    await nextTick();

    expect(wrapper.emitted('select')).toEqual([[3]]);
    expect(wrapper.emitted('request-page')).toEqual([[1]]);
  });

  it('zooms on double click and exposes a reset control', async () => {
    const wrapper = mountPreview();
    const viewport = wrapper.get('.mp__media-wrap');
    Object.defineProperty(viewport.element, 'clientWidth', { configurable: true, value: 800 });
    Object.defineProperty(viewport.element, 'clientHeight', { configurable: true, value: 500 });
    const image = wrapper.get('.mp__media--image');
    Object.defineProperty(image.element, 'clientWidth', { configurable: true, value: 600 });
    Object.defineProperty(image.element, 'clientHeight', { configurable: true, value: 400 });
    expect(image.attributes('style')).toContain('cursor: zoom-in');

    await viewport.trigger('dblclick', { clientX: 400, clientY: 250 });
    expect(image.attributes('style')).toContain('scale(2)');
    expect(image.attributes('style')).toContain('cursor: grab');

    await wrapper.get('[aria-label="Reset zoom"]').trigger('click');
    expect(image.attributes('style')).toContain('scale(1)');
    expect(image.attributes('style')).toContain('cursor: zoom-in');
  });

  it('keeps navigation controls isolated from zoom and swipe gestures', async () => {
    const wrapper = mountPreview();
    const nextButton = wrapper.get('[aria-label="Next media"]');
    const image = wrapper.get('.mp__media--image');

    dispatchPointer(nextButton.element, 'pointerdown', { pointerId: 8, clientX: 200, clientY: 100 });
    dispatchPointer(nextButton.element, 'pointerup', { pointerId: 8, clientX: 100, clientY: 100 });
    await nextButton.trigger('dblclick', { clientX: 100, clientY: 100 });
    expect(wrapper.emitted('next')).toBeUndefined();
    expect(image.attributes('style')).toContain('scale(1)');

    await nextButton.trigger('click');
    expect(wrapper.emitted('next')).toHaveLength(1);
  });

  it('uses a horizontal touch swipe for next and previous navigation at base zoom', async () => {
    const wrapper = mountPreview();
    const viewport = wrapper.get('.mp__media-wrap');

    dispatchPointer(viewport.element, 'pointerdown', { pointerId: 1, clientX: 200, clientY: 100 });
    dispatchPointer(viewport.element, 'pointerup', { pointerId: 1, clientX: 100, clientY: 105 });
    await nextTick();
    expect(wrapper.emitted('next')).toHaveLength(1);

    dispatchPointer(viewport.element, 'pointerdown', { pointerId: 2, clientX: 100, clientY: 100 });
    dispatchPointer(viewport.element, 'pointerup', { pointerId: 2, clientX: 200, clientY: 105 });
    await nextTick();
    expect(wrapper.emitted('prev')).toHaveLength(1);
  });

  it('uses a horizontal swipe over video while preserving its native control area', async () => {
    previewMockState.fileName = 'clip.mp4';
    previewMockState.isVideo = true;
    previewMockState.mediaUrl = '/video.mp4';
    previewMockState.posterUrl = '/video-poster.jpg';
    const wrapper = mountPreview();
    const video = wrapper.get('video.mp__media--video');
    vi.spyOn(video.element, 'getBoundingClientRect').mockReturnValue({
      x: 0,
      y: 0,
      top: 0,
      right: 640,
      bottom: 400,
      left: 0,
      width: 640,
      height: 400,
      toJSON: () => undefined,
    });

    dispatchPointer(video.element, 'pointerdown', { pointerId: 3, clientX: 200, clientY: 100 });
    dispatchPointer(video.element, 'pointerup', { pointerId: 3, clientX: 100, clientY: 105 });
    await nextTick();
    expect(wrapper.emitted('next')).toHaveLength(1);

    dispatchPointer(video.element, 'pointerdown', { pointerId: 4, clientX: 200, clientY: 380 });
    dispatchPointer(video.element, 'pointerup', { pointerId: 4, clientX: 100, clientY: 380 });
    await nextTick();
    expect(wrapper.emitted('next')).toHaveLength(1);
  });
});
