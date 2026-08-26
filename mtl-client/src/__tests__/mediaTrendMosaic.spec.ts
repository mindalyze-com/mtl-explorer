import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils';
import { defineComponent } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  MediaTrendItemsRequestGroupingEnum,
  MediaTrendItemsRequestKindEnum,
  MediaTrendItemsRequestScopeEnum,
} from 'x8ing-mtl-api-typescript-fetch';
import MediaTrendMosaic from '@/components/statistics/MediaTrendMosaic.vue';
import { getMediaTrendItems } from '@/repositories/mediaRepository';

vi.mock('@/repositories/mediaRepository', () => ({
  getMediaTrendItems: vi.fn(),
  mediaContentUrl: (id: number, size?: number) => `/media/${id}?size=${size ?? ''}`,
}));

enableAutoUnmount(afterEach);

const BottomSheetStub = defineComponent({
  name: 'BottomSheet',
  props: {
    modelValue: { type: Boolean, default: false },
    title: { type: String, default: '' },
    zIndex: { type: Number, default: 0 },
  },
  emits: ['update:modelValue', 'closed'],
  template:
    '<section v-if="modelValue" class="bottom-sheet-stub" :data-title="title" :data-z-index="zIndex"><slot name="header-actions" /><slot /></section>',
});

const MediaPreviewStub = defineComponent({
  name: 'MediaPreview',
  props: {
    mediaId: Number,
    navIndex: Number,
    navTotal: Number,
    mediaIds: Array,
    timeSource: String,
    appliedCameraOffsetSeconds: Number,
    positionUnknown: Boolean,
  },
  emits: ['open-on-map', 'time-correction-cleared'],
  template:
    '<div data-test="media-preview-stub">{{ mediaId }} · {{ navIndex }}/{{ navTotal }}<button data-test="open-on-map" @click="$emit(\'open-on-map\')">Map</button></div>',
});

const bucket = {
  bucketKey: '2026-08',
  label: '2026-08',
  subGroup: '08',
  undated: false,
  imageCount: 61,
  videoCount: 1,
};

function page(items: Array<Record<string, unknown>>, totalItems = items.length) {
  return { items, page: 0, pageSize: 60, totalItems, totalPages: Math.ceil(totalItems / 60) };
}

function mountMosaic() {
  return mount(MediaTrendMosaic, {
    props: {
      modelValue: true,
      bucket,
      grouping: MediaTrendItemsRequestGroupingEnum.Month,
      scope: MediaTrendItemsRequestScopeEnum.MatchedActivities,
      trackIds: [11, 12],
    },
    global: {
      stubs: {
        BottomSheet: BottomSheetStub,
        MediaPreview: MediaPreviewStub,
      },
    },
  });
}

describe('MediaTrendMosaic', () => {
  const getMediaTrendItemsMock = vi.mocked(getMediaTrendItems);

  beforeEach(() => {
    getMediaTrendItemsMock.mockReset();
  });

  it('loads 60 mixed media items first and pages in stable server order', async () => {
    const firstItems = Array.from({ length: 60 }, (_, index) => ({
      id: 200 - index,
      mediaKind: index === 0 ? 'VIDEO' : 'IMAGE',
      fileName: `media-${index}.jpg`,
      effectiveCapturedAt: new Date('2026-08-17T10:00:00Z'),
      trackId: 11,
    }));
    getMediaTrendItemsMock
      .mockResolvedValueOnce(page(firstItems, 61))
      .mockResolvedValueOnce(page([{ id: 100, mediaKind: 'IMAGE', fileName: 'last.jpg' }], 61));

    const wrapper = mountMosaic();
    await flushPromises();

    expect(getMediaTrendItemsMock).toHaveBeenNthCalledWith(
      1,
      expect.objectContaining({
        grouping: 'MONTH',
        scope: 'MATCHED_ACTIVITIES',
        bucketKey: '2026-08',
        kind: MediaTrendItemsRequestKindEnum.All,
        trackIds: [11, 12],
        page: 0,
        pageSize: 60,
      }),
      expect.any(AbortSignal)
    );
    expect(wrapper.findAll('.media-mosaic-card')).toHaveLength(60);

    await wrapper.get('[data-test="media-mosaic-load-more"]').trigger('click');
    await flushPromises();

    expect(getMediaTrendItemsMock).toHaveBeenNthCalledWith(
      2,
      expect.objectContaining({ page: 1, pageSize: 60 }),
      expect.any(AbortSignal)
    );
    expect(wrapper.findAll('.media-mosaic-card')).toHaveLength(61);
  });

  it('changes kind, opens the nested viewer, and links matched media to Photos', async () => {
    getMediaTrendItemsMock
      .mockResolvedValueOnce(
        page([
          { id: 21, mediaKind: 'IMAGE', fileName: 'one.jpg', trackId: 41 },
          { id: 20, mediaKind: 'VIDEO', fileName: 'two.mp4', trackId: 41 },
        ])
      )
      .mockResolvedValueOnce(page([{ id: 21, mediaKind: 'IMAGE', fileName: 'one.jpg', trackId: 41 }]));

    const wrapper = mountMosaic();
    await flushPromises();

    const photosChip = wrapper.findAll('.media-mosaic__chip').find((chip) => chip.text() === 'Photos');
    expect(photosChip).toBeDefined();
    await photosChip!.trigger('click');
    await flushPromises();
    expect(getMediaTrendItemsMock).toHaveBeenLastCalledWith(
      expect.objectContaining({ kind: MediaTrendItemsRequestKindEnum.Image, page: 0 }),
      expect.any(AbortSignal)
    );

    await wrapper.get('.media-mosaic-card__preview').trigger('click');
    await flushPromises();
    expect(wrapper.get('[data-test="media-preview-stub"]').text()).toContain('21 · 1/1');
    expect(wrapper.find('.media-mosaic-card__image-wrap img').exists()).toBe(false);
    expect(wrapper.get('.media-viewer-theme-toggle').attributes('aria-label')).toMatch(
      /^Use (light|dark) photo viewer$/
    );
    expect(wrapper.findAllComponents(BottomSheetStub).map((sheet) => sheet.props('zIndex'))).toEqual([5400, 5500]);

    await wrapper.get('.media-mosaic-card__activity').trigger('click');
    await flushPromises();
    expect(wrapper.emitted('open-activity')).toEqual([[41]]);
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([false]);
  });

  it('sends the selected media coordinates to the main map flow', async () => {
    getMediaTrendItemsMock.mockResolvedValueOnce(
      page([{ id: 21, mediaKind: 'IMAGE', fileName: 'one.jpg', resolvedLat: 47.56, resolvedLng: 8.5 }])
    );
    const wrapper = mountMosaic();
    await flushPromises();

    await wrapper.get('.media-mosaic-card__preview').trigger('click');
    await wrapper.get('[data-test="open-on-map"]').trigger('click');
    await flushPromises();

    expect(wrapper.emitted('open-media-on-map')).toEqual([[{ id: 21, lat: 47.56, lng: 8.5 }]]);
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([false]);
  });

  it('passes saved correction and unknown-position metadata to the viewer and refreshes after clear', async () => {
    const corrected = {
      id: 21,
      mediaKind: 'IMAGE',
      fileName: 'clock.jpg',
      effectiveCapturedAt: new Date('2026-08-17T11:01:03Z'),
      timeSource: 'EXIF_DATE_TAKEN',
      appliedCameraOffsetSeconds: 3_603,
    };
    getMediaTrendItemsMock
      .mockResolvedValueOnce(page([corrected]))
      .mockResolvedValueOnce(
        page([{ ...corrected, effectiveCapturedAt: new Date('2026-08-17T10:01:00Z'), appliedCameraOffsetSeconds: 0 }])
      );
    const wrapper = mountMosaic();
    await flushPromises();
    await wrapper.get('.media-mosaic-card__preview').trigger('click');

    const preview = wrapper.getComponent(MediaPreviewStub);
    expect(preview.props()).toMatchObject({
      timeSource: 'EXIF_DATE_TAKEN',
      appliedCameraOffsetSeconds: 3_603,
      positionUnknown: true,
    });

    preview.vm.$emit('time-correction-cleared', 21);
    await flushPromises();

    expect(getMediaTrendItemsMock).toHaveBeenCalledTimes(2);
    expect(wrapper.getComponent(MediaPreviewStub).props('appliedCameraOffsetSeconds')).toBe(0);
  });

  it('does not send activity IDs for all indexed undated media', async () => {
    getMediaTrendItemsMock.mockResolvedValueOnce(
      page([{ id: 9, mediaKind: 'IMAGE', fileName: 'undated.jpg', trackId: 11 }])
    );
    const wrapper = mount(MediaTrendMosaic, {
      props: {
        modelValue: true,
        bucket: { ...bucket, bucketKey: 'UNDATED', label: 'Undated', undated: true },
        grouping: MediaTrendItemsRequestGroupingEnum.Total,
        scope: MediaTrendItemsRequestScopeEnum.AllIndexed,
        trackIds: [11],
      },
      global: { stubs: { BottomSheet: BottomSheetStub, MediaPreview: MediaPreviewStub } },
    });
    await flushPromises();

    expect(getMediaTrendItemsMock).toHaveBeenCalledWith(
      expect.objectContaining({ bucketKey: 'UNDATED', scope: 'ALL_INDEXED', trackIds: undefined }),
      expect.any(AbortSignal)
    );
    expect(wrapper.get('.bottom-sheet-stub').attributes('data-title')).toBe('Undated · 62 items');
    expect(wrapper.find('.media-mosaic-card__activity').exists()).toBe(false);
  });

  it('shows a recoverable empty-page error', async () => {
    getMediaTrendItemsMock.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce(page([]));
    const wrapper = mountMosaic();
    await flushPromises();

    expect(wrapper.get('[role="alert"]').text()).toContain('Media could not be loaded.');
    await wrapper.get('[role="alert"] button').trigger('click');
    await flushPromises();

    expect(getMediaTrendItemsMock).toHaveBeenCalledTimes(2);
    expect(wrapper.text()).toContain('No media in this selection.');
  });
});
