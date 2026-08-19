import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import type { QueryResultEntry } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/QueryResultEntry';
import FilterTrackReviewSheet from '@/components/filter/FilterTrackReviewSheet.vue';

const BottomSheetStub = {
  name: 'BottomSheet',
  props: ['modelValue', 'title', 'detents'],
  emits: ['update:modelValue'],
  template: '<div><slot /></div>',
};

const TrackBrowserViewStub = {
  name: 'TrackBrowserView',
  props: ['tracks'],
  emits: ['select-track', 'open-details'],
  template: '<div data-test="track-browser-view">{{ tracks.length }} tracks</div>',
};

function reviewEntries(): QueryResultEntry[] {
  return [
    {
      id: 11,
      group: 'WALKING',
      gpsTrack: { trackName: 'Morning walk', startDate: new Date(2024, 0, 1) },
    },
    {
      id: 12,
      group: 'BICYCLE',
      gpsTrack: { id: 12, trackName: 'Evening ride', startDate: new Date(2024, 0, 2) },
    },
  ];
}

describe('FilterTrackReviewSheet', () => {
  it('uses the shared modern track browser and preserves result IDs', async () => {
    const wrapper = mount(FilterTrackReviewSheet, {
      props: { modelValue: true, entries: reviewEntries() },
      global: { stubs: { BottomSheet: BottomSheetStub, TrackBrowserView: TrackBrowserViewStub } },
    });
    const browser = wrapper.findComponent(TrackBrowserViewStub);
    const tracks = browser.props('tracks');

    expect(wrapper.get('[data-test="track-browser-view"]').text()).toBe('2 tracks');
    expect(tracks.map((track: { id?: number }) => track.id)).toEqual([11, 12]);
    expect(wrapper.findComponent(BottomSheetStub).props('detents')).toEqual([
      { height: 'min(88vh, 46rem)' },
      { height: '95vh' },
    ]);

    browser.vm.$emit('select-track', 11);
    browser.vm.$emit('open-details', 12);
    expect(wrapper.emitted('select-track')).toEqual([[11]]);
    expect(wrapper.emitted('open-details')).toEqual([[12]]);
  });

  it('shows a single loading state before the shared browser is ready', () => {
    const wrapper = mount(FilterTrackReviewSheet, {
      props: { modelValue: true, loading: true, entries: [] },
      global: { stubs: { BottomSheet: BottomSheetStub, TrackBrowserView: TrackBrowserViewStub } },
    });

    expect(wrapper.get('.filter-review__loading').text()).toContain('Loading track details');
    expect(wrapper.findComponent(TrackBrowserViewStub).exists()).toBe(false);
  });

  it('keeps saved rows visible while refreshing and offers retry after an error', async () => {
    const wrapper = mount(FilterTrackReviewSheet, {
      props: { modelValue: true, loading: true, entries: reviewEntries() },
      global: { stubs: { BottomSheet: BottomSheetStub, TrackBrowserView: TrackBrowserViewStub } },
    });

    expect(wrapper.get('.filter-review__loading').text()).toContain('Refreshing track details');
    expect(wrapper.get('[data-test="track-browser-view"]').text()).toBe('2 tracks');

    await wrapper.setProps({ loading: false, error: 'Tracks could not be refreshed. Showing saved results.' });
    expect(wrapper.get('.filter-review__error').text()).toContain('Showing saved results');
    await wrapper.get('.filter-review__error button').trigger('click');
    expect(wrapper.emitted('retry')).toEqual([[]]);
  });
});
