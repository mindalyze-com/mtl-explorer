import { readFileSync } from 'node:fs';
import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import MediaCollectionChooser from '@/components/map/MediaCollectionChooser.vue';
import type { MediaOverlaySelection } from '@/layers/MediaOverlay';

function selection(overrides: Partial<MediaOverlaySelection> = {}): MediaOverlaySelection {
  return {
    selectedMediaId: 10,
    mediaIds: [10],
    mediaPoints: [{ id: 10, lat: 47.45, lng: 7.55 }],
    totalMediaCount: 1,
    clusterId: null,
    offset: 0,
    kind: 'location',
    viewportMediaPoints: [
      { id: 10, lat: 47.45, lng: 7.55 },
      { id: 11, lat: 47.46, lng: 7.56 },
    ],
    clickPoint: { x: 75, y: 60 },
    clickLngLat: { lng: 7.55, lat: 47.45 },
    ...overrides,
  };
}

describe('MediaCollectionChooser', () => {
  it('uses a content-fitted, width-limited desktop sheet', () => {
    const source = readFileSync('src/components/map/Map2DRenderer.vue', 'utf8');
    const chooserSource = readFileSync('src/components/map/MediaCollectionChooser.vue', 'utf8');
    const chooserSection = source.match(
      /<!-- ─── Media collection chooser ─── -->[\s\S]*?<!-- ─── Media photo bottom sheet ─── -->/
    )?.[0];

    expect(chooserSection).toBeDefined();
    expect(chooserSection).toContain('fit-content-initial');
    expect(chooserSection).toContain('sheet-class="media-collection-sheet"');
    expect(chooserSection).toContain('desktop-width="compact"');
    expect(chooserSection).not.toContain("height: '62vh'");
    expect(chooserSource).toContain('--media-collection-bottom-buffer: 0.75rem;');
  });

  it('offers the clicked photo and the current map view for a single marker', async () => {
    const wrapper = mount(MediaCollectionChooser, { props: { selection: selection() } });

    expect(wrapper.get('[data-test="media-collection-primary"]').text()).toContain('This photo');
    expect(wrapper.get('[data-test="media-collection-primary"]').text()).toContain('1 photo');
    expect(wrapper.get('[data-test="media-collection-viewport"]').text()).toContain('Current map view');
    expect(wrapper.get('[data-test="media-collection-viewport"]').text()).toContain('2 photos');

    await wrapper.get('[data-test="media-collection-viewport"]').trigger('click');
    expect(wrapper.emitted('choose-viewport')).toHaveLength(1);
  });

  it('labels a cluster explicitly', () => {
    const wrapper = mount(MediaCollectionChooser, {
      props: {
        selection: selection({
          kind: 'cluster',
          mediaIds: [10, 11],
          mediaPoints: [
            { id: 10, lat: 47.45, lng: 7.55 },
            { id: 11, lat: 47.46, lng: 7.56 },
          ],
          totalMediaCount: 28,
          clusterId: 17,
        }),
      },
    });

    expect(wrapper.get('[data-test="media-collection-primary"]').text()).toContain('This cluster');
    expect(wrapper.get('[data-test="media-collection-primary"]').text()).toContain('28 photos');
  });

  it('delegates nearby activities to the shared GPS track flow', async () => {
    const wrapper = mount(MediaCollectionChooser, {
      props: {
        selection: selection(),
        trackCount: 2,
      },
    });

    const activities = wrapper.get('[data-test="media-collection-activities"]');
    expect(activities.text()).toContain('Photos along a GPS track');
    expect(activities.text()).toContain('Open photos linked to a GPS activity that passes this location.');

    await activities.trigger('click');
    expect(wrapper.emitted('open-activities')).toHaveLength(1);
  });

  it('hides a duplicate map-view choice when it contains only the clicked location', () => {
    const wrapper = mount(MediaCollectionChooser, {
      props: {
        selection: selection({ viewportMediaPoints: [{ id: 10, lat: 47.45, lng: 7.55 }] }),
      },
    });

    expect(wrapper.find('[data-test="media-collection-viewport"]').exists()).toBe(false);
  });
});
