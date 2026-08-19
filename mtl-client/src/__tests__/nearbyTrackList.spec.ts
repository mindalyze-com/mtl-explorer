import { shallowMount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import NearbyTrackList from '@/components/map/NearbyTrackList.vue';

describe('NearbyTrackList', () => {
  const tracks = [
    {
      id: 42,
      name: 'Morning ride',
      displayName: 'Morning ride',
      description: '',
      activityType: 'BICYCLE',
      date: '18/08/2026',
      distanceMeters: 18.4,
      matchedMediaCount: 3,
    },
    {
      id: 43,
      name: 'Evening ride',
      displayName: 'Evening ride',
      description: '',
      activityType: 'BICYCLE',
      date: '17/08/2026',
      distanceMeters: 1240,
      matchedMediaCount: 0,
    },
  ];

  it('shows matched-photo counts and distance from the selected point', () => {
    const wrapper = shallowMount(NearbyTrackList, {
      props: { tracks, showMediaStatus: true },
    });

    expect(wrapper.text()).toContain('3 photos');
    expect(wrapper.text()).toContain('No matched photos');
    expect(wrapper.text()).toContain('18 m away');
    expect(wrapper.text()).toContain('1.2 km away');
  });

  it('emits the selected activity id', async () => {
    const wrapper = shallowMount(NearbyTrackList, {
      props: { tracks, showMediaStatus: true },
    });

    await wrapper.get('button').trigger('click');

    expect(wrapper.emitted('select')).toEqual([[42]]);
  });
});
