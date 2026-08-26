import { mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import TrackDetailPhotos from '@/components/trackdetails/TrackDetailPhotos.vue';

vi.mock('@/repositories/mediaRepository', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/repositories/mediaRepository')>();
  return {
    ...original,
    mediaContentUrl: (id: number, maxSize?: number) => `/media/${id}?maxSize=${maxSize}`,
  };
});

const media = [
  {
    id: 11,
    fileName: 'gps-photo.jpg',
    mediaKind: 'IMAGE',
    capturedAt: new Date('2026-01-01T10:00:00Z'),
    adjustedCapturedAt: new Date('2026-01-01T10:00:00Z'),
    cameraMake: 'Example',
    cameraModel: 'One',
    positionOrigin: 'EXIF_EMBEDDED',
    estimatedPosition: false,
    resolvedLat: 47.4,
    resolvedLng: 8.5,
    distanceInMeterSinceStart: 1250,
  },
  {
    id: 12,
    fileName: 'estimated-photo.jpg',
    mediaKind: 'IMAGE',
    capturedAt: new Date('2026-01-01T10:10:00Z'),
    adjustedCapturedAt: new Date('2026-01-01T11:10:00Z'),
    positionOrigin: 'TRACK_INTERPOLATED',
    estimatedPosition: true,
    resolvedLat: 47.41,
    resolvedLng: 8.51,
    distanceInMeterSinceStart: 2500,
  },
];

describe('TrackDetailPhotos', () => {
  it('renders a lazy timeline with visible position provenance', () => {
    const wrapper = mount(TrackDetailPhotos, { props: { media, selectedMediaId: 12 } });

    const cards = wrapper.findAll('.photo-card');
    expect(cards).toHaveLength(2);
    expect(cards[0].text()).toContain('GPS');
    expect(cards[1].text()).toContain('Estimated');
    expect(cards[1].attributes('aria-current')).toBe('true');
    expect(wrapper.findAll('.photo-card__thumb').every((image) => image.attributes('loading') === 'lazy')).toBe(true);
  });

  it('removes thumbnail requests while the photo viewer is open', async () => {
    const wrapper = mount(TrackDetailPhotos, { props: { media } });

    expect(wrapper.findAll('.photo-card__thumb')).toHaveLength(2);
    await wrapper.setProps({ thumbnailsEnabled: false });
    expect(wrapper.find('.photo-card__thumb').exists()).toBe(false);
    expect(wrapper.findAll('.photo-card')).toHaveLength(2);
  });

  it('marks videos in the activity timeline and mini rail', () => {
    const video = { ...media[0], id: 13, fileName: 'gps-video.mp4', mediaKind: 'VIDEO' };
    const wrapper = mount(TrackDetailPhotos, { props: { media: [video] } });

    expect(wrapper.get('.photo-card__video').attributes('aria-label')).toBe('Video');
    expect(wrapper.get('.photo-card__video .bi-play-fill').exists()).toBe(true);
    expect(wrapper.get('.photo-card__dot .bi-camera-video-fill').exists()).toBe(true);
    expect(wrapper.get('.photo-card__thumb').attributes('src')).toBe('/media/13?maxSize=480');
  });

  it('keeps rare correction controls behind one progressive disclosure', async () => {
    const wrapper = mount(TrackDetailPhotos, { props: { media } });
    const toolsToggle = wrapper.get('[data-test="photo-tools-toggle"]');

    expect(toolsToggle.text()).toContain('Photo tools');
    expect(toolsToggle.attributes('aria-expanded')).toBe('false');
    expect(wrapper.get('[data-test="photo-tools"]').attributes()).toMatchObject({
      'aria-label': 'Photo tools',
      style: expect.stringContaining('display: none'),
    });
    expect(wrapper.find('.photo-card__actions').exists()).toBe(false);
    expect(wrapper.text()).not.toContain('Example One');

    await toolsToggle.trigger('click');
    expect(toolsToggle.attributes('aria-expanded')).toBe('true');
    expect(wrapper.get('[data-test="photo-tools"]').attributes('style')).not.toContain('display: none');
    expect(wrapper.get('[data-test="photo-offset-hours"]').exists()).toBe(true);
    expect(wrapper.find('.photo-card__actions').exists()).toBe(false);

    await wrapper.get('[data-test="photo-location-mode-toggle"]').trigger('click');
    expect(wrapper.findAll('.photo-card__edit')).toHaveLength(2);

    await toolsToggle.trigger('click');
    expect(wrapper.find('.photo-card__actions').exists()).toBe(false);
  });

  it('selects a photo from the timeline', async () => {
    const wrapper = mount(TrackDetailPhotos, { props: { media } });

    await wrapper.find('[data-media-id="12"]').trigger('click');

    expect(wrapper.emitted('select-media')).toEqual([[12]]);
  });

  it('keeps the matching map photo highlighted after a timeline item is hovered or focused', async () => {
    const wrapper = mount(TrackDetailPhotos, { props: { media } });
    const items = wrapper.findAll('.photo-timeline__item');

    await items[1].trigger('mouseenter');
    await items[1].trigger('mouseleave');
    await items[0].trigger('focusin');
    await items[0].trigger('focusout');

    expect(wrapper.emitted('highlight-media')).toEqual([[12], [11]]);

    await wrapper.setProps({ highlightedMediaId: 11 });
    expect(wrapper.find('[data-media-id="11"]').classes()).toContain('photo-card--highlighted');
  });

  it('shows the server total and emits bounded page changes', async () => {
    const wrapper = mount(TrackDetailPhotos, {
      props: { media, page: 1, pageSize: 2, totalItems: 7, totalPages: 4 },
    });

    expect(wrapper.get('.photos-toolbar__count').text()).toBe('7 items');
    expect(wrapper.get('[data-test="photo-page-range"]').text()).toBe('3–4 of 7');
    expect(wrapper.findAll('[data-test="photo-page-size"] option').map((option) => option.attributes('value'))).toEqual(
      ['100', '200']
    );
    await wrapper.get('[data-test="photo-page-previous"]').trigger('click');
    await wrapper.get('[data-test="photo-page-next"]').trigger('click');
    await wrapper.get('[data-test="photo-page-last"]').trigger('click');
    await wrapper.get('[data-test="photo-page-size"]').setValue('200');

    expect(wrapper.emitted('change-page')).toEqual([[0], [2], [3]]);
    expect(wrapper.emitted('change-page-size')).toEqual([[200]]);
  });

  it('applies and resets a signed camera-clock offset in seconds', async () => {
    const wrapper = mount(TrackDetailPhotos, { props: { media, offsetSeconds: 0 } });
    await wrapper.get('[data-test="photo-tools-toggle"]').trigger('click');
    const input = wrapper.get('[data-test="photo-offset-hours"]');

    await input.setValue('-1.5');
    await wrapper.get('form').trigger('submit');
    expect(wrapper.emitted('apply-offset')).toEqual([[-5400]]);

    await wrapper.setProps({ offsetSeconds: -5400 });
    const reset = wrapper.findAll('button').find((button) => button.text() === 'Reset');
    await reset?.trigger('click');
    expect(wrapper.emitted('apply-offset')?.at(-1)).toEqual([0]);
  });

  it('keeps a camera offset as a preview until the user saves it', async () => {
    const previewMedia = media.map((item) => ({
      ...item,
      preview: true,
      timeSource: 'EXIF_DATE_TAKEN',
    }));
    const wrapper = mount(TrackDetailPhotos, {
      props: { media: previewMedia, offsetSeconds: 3600 },
    });

    expect(wrapper.get('[data-test="photo-offset-preview"]').text()).toContain('Unsaved preview');
    await wrapper.get('[data-test="save-time-correction"]').trigger('click');

    expect(wrapper.emitted('save-time-correction')).toEqual([[[11, 12], 3600]]);
  });

  it('keeps manual provenance and controls during a camera preview', async () => {
    const manualPreview = {
      ...media[1],
      preview: true,
      timeSource: 'EXIF_DATE_TAKEN',
      positionOrigin: 'USER_ASSIGNED',
      manualLat: 47.5,
      manualLng: 8.6,
      resolvedLat: 47.5,
      resolvedLng: 8.6,
    };
    const wrapper = mount(TrackDetailPhotos, { props: { media: [manualPreview], offsetSeconds: 3600 } });

    expect(wrapper.get('.photo-card__badge').text()).toBe('Set by you');
    await wrapper.get('[data-test="photo-location-mode-toggle"]').trigger('click');
    expect(wrapper.get('.photo-card__edit').text()).toContain('Edit location');
    await wrapper.get('.photo-card__edit').trigger('click');
    expect(wrapper.get('[data-test="clear-manual-location"]').exists()).toBe(true);
  });

  it('uses a neutral label for missing provenance and formats second-level offsets cleanly', () => {
    const unknown = {
      ...media[1],
      positionOrigin: undefined,
      estimatedPosition: false,
      appliedCameraOffsetSeconds: 3603,
      preview: false,
    };
    const wrapper = mount(TrackDetailPhotos, { props: { media: [unknown] } });

    expect(wrapper.get('.photo-card__badge').text()).toBe('Position unknown');
    expect(wrapper.get('[data-test="saved-time-correction"]').text()).toContain('+1h');
    expect(wrapper.get('[data-test="saved-time-correction"]').text()).not.toContain('+1.h');
  });

  it('saves a user location without replacing the preserved evidence', async () => {
    const wrapper = mount(TrackDetailPhotos, { props: { media } });

    await wrapper.get('[data-test="photo-tools-toggle"]').trigger('click');
    await wrapper.get('[data-test="photo-location-mode-toggle"]').trigger('click');
    await wrapper.findAll('.photo-card__edit')[1].trigger('click');
    const editor = wrapper.get('[data-test="photo-location-editor"]');
    const coordinateInputs = editor.findAll('input[type="number"]');
    await coordinateInputs[0].setValue('47.42');
    await coordinateInputs[1].setValue('8.52');
    await editor.get('input[type="text"]').setValue('Trail junction');
    await editor.trigger('submit');

    expect(wrapper.emitted('save-manual-location')).toEqual([[12, 47.42, 8.52, 'Trail junction']]);
  });

  it('can clear saved clock and user location overrides', async () => {
    const overridden = {
      ...media[1],
      positionOrigin: 'USER_ASSIGNED',
      manualLat: 47.5,
      manualLng: 8.6,
      appliedCameraOffsetSeconds: 3600,
    };
    const wrapper = mount(TrackDetailPhotos, { props: { media: [overridden] } });

    await wrapper.get('[data-test="photo-tools-toggle"]').trigger('click');
    await wrapper.get('[data-test="clear-time-correction"]').trigger('click');
    await wrapper.get('[data-test="photo-location-mode-toggle"]').trigger('click');
    await wrapper.get('.photo-card__edit').trigger('click');
    await wrapper.get('[data-test="clear-manual-location"]').trigger('click');

    expect(wrapper.emitted('save-time-correction')).toEqual([[[12], 0]]);
    expect(wrapper.emitted('clear-manual-location')).toEqual([[12]]);
  });

  it('explains the empty state and offers retry after a load failure', async () => {
    const empty = mount(TrackDetailPhotos);
    expect(empty.get('[data-test="track-photos-empty"]').text()).toContain('camera clock was wrong');
    expect(empty.get('[data-test="empty-photo-tools"]').text()).toBe('Open Photo tools');
    await empty.get('[data-test="empty-photo-tools"]').trigger('click');
    expect(empty.get('[data-test="photo-tools-toggle"]').attributes('aria-expanded')).toBe('true');

    const failed = mount(TrackDetailPhotos, { props: { error: 'Activity photos could not be loaded.' } });
    await failed.get('[data-test="track-photos-error"] button').trigger('click');
    expect(failed.emitted('retry')).toEqual([[]]);
  });
});
