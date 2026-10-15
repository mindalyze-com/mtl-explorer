import { readFileSync } from 'node:fs';
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it } from 'vitest';
import MediaCollectionChooser from '@/components/map/MediaCollectionChooser.vue';
import type { MediaOverlaySelection } from '@/layers/MediaOverlay';

enableAutoUnmount(afterEach);

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
    expect(chooserSource).not.toContain('min-height: min(26rem');
  });

  it('offers the clicked photo and the current map view for a single marker', async () => {
    const wrapper = mount(MediaCollectionChooser, { props: { selection: selection() } });

    expect(wrapper.get('[data-test="media-collection-primary"]').text()).toContain('This photo');
    expect(wrapper.get('[data-test="media-collection-primary"]').text()).toContain('1 photo');
    expect(wrapper.get('[data-test="media-collection-viewport"]').text()).toContain('Map view at tap');
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
    expect(activities.text()).toContain('Nearby GPS tracks');
    expect(activities.text()).toContain('Open linked photos, or track details when no photos are matched.');

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

  it.each(['location', 'cluster'] as const)(
    'deduplicates an entirely known %s by photo IDs instead of their order',
    (kind) => {
      const points = [
        { id: 10, lat: 47.45, lng: 7.55 },
        { id: 11, lat: 47.46, lng: 7.56 },
      ];
      const wrapper = mount(MediaCollectionChooser, {
        props: {
          selection: selection({
            kind,
            mediaIds: [10, 11],
            mediaPoints: points,
            totalMediaCount: 2,
            viewportMediaPoints: [...points].reverse(),
          }),
        },
      });
      expect(wrapper.find('[data-test="media-collection-viewport"]').exists()).toBe(false);
      expect(wrapper.get('[data-test="media-collection-primary"]').text()).toContain('2 photos');
    }
  );

  it('keeps the viewport choice when a cluster is only partly loaded, even when its loaded IDs match', () => {
    const points = [
      { id: 10, lat: 47.45, lng: 7.55 },
      { id: 11, lat: 47.46, lng: 7.56 },
    ];
    const wrapper = mount(MediaCollectionChooser, {
      props: {
        selection: selection({
          kind: 'cluster',
          mediaIds: [10, 11],
          mediaPoints: points,
          totalMediaCount: 28,
          viewportMediaPoints: points,
        }),
      },
    });
    expect(wrapper.get('[data-test="media-collection-primary"]').text()).toContain('28 photos');
    expect(wrapper.get('[data-test="media-collection-viewport"]').text()).toContain('2 photos');
  });

  it('keeps usable photo choices beside a failed lookup and offers an accessible retry', async () => {
    const wrapper = mount(MediaCollectionChooser, {
      props: { selection: selection(), tracksError: 'Nearby GPS tracks are unavailable.' },
      attachTo: document.body,
    });
    expect(wrapper.get('[data-test="media-collection-error"]').attributes('role')).toBe('status');
    expect(wrapper.get('[data-test="media-collection-primary"]').attributes('disabled')).toBeUndefined();
    expect(wrapper.get('[data-test="media-collection-viewport"]').attributes('disabled')).toBeUndefined();
    await wrapper.get('[data-test="media-collection-retry"]').trigger('click');
    await flushPromises();
    expect(wrapper.emitted('retry-activities')).toEqual([[]]);
    expect(document.activeElement).toBe(wrapper.get('[data-test="media-collection-primary"]').element);
    await wrapper.setProps({ tracksError: null, tracksLoading: true });
    expect(wrapper.find('[data-test="media-collection-error"]').exists()).toBe(false);
    expect(wrapper.get('[data-test="media-collection-activities"]').attributes('disabled')).toBeDefined();
    expect(wrapper.get('[data-test="media-collection-activities"]').text()).toContain('Loading…');
  });
});
