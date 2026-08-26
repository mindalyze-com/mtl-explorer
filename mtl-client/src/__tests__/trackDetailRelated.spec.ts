import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import TrackDetailRelated from '@/components/trackdetails/TrackDetailRelated.vue';

describe('TrackDetailRelated navigation', () => {
  it('exposes each related track as a native control and emits its ID', async () => {
    const wrapper = mount(TrackDetailRelated, {
      props: {
        gpsTrack: { id: 100005, trackName: 'Current track' },
        relatedTracks: {
          previousTracksInTime: [{ id: 100004, name: 'Previous track' }],
          nextTracksInTime: [{ id: 100006, name: 'Next track' }],
        },
      },
      global: {
        stubs: {
          TrackShapePreview: true,
        },
      },
    });

    const nextTrackButton = wrapper.get('button.track-card--next[data-track-id="100006"]');
    expect(nextTrackButton.attributes('type')).toBe('button');
    expect(nextTrackButton.attributes('aria-label')).toBe('Open track Next track');

    await nextTrackButton.trigger('click');

    expect(wrapper.emitted('navigate-track')).toEqual([[100006]]);
  });
});
