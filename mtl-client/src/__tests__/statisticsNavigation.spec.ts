import { enableAutoUnmount, shallowMount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { defineComponent, nextTick, ref } from 'vue';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import type { GpsTrack } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import Statistics from '@/components/statistics/Statistics.vue';

enableAutoUnmount(afterEach);

const PassthroughStub = defineComponent({
  template: '<div><slot name="title" /><slot /></div>',
});

const StatisticsOverviewStub = defineComponent({
  name: 'StatisticsOverview',
  emits: ['track-updated', 'view-highlight-exclusions'],
  template: '<div data-test="statistics-overview" />',
});

const TrackBrowserViewStub = defineComponent({
  name: 'TrackBrowserView',
  props: {
    tracks: { type: Array, default: () => [] },
    resetKey: { type: Number, default: 0 },
  },
  setup() {
    const query = ref('');
    function getNavigationState() {
      return { query: query.value };
    }
    function restoreNavigationState(state: unknown) {
      if (state && typeof state === 'object' && 'query' in state && typeof state.query === 'string') {
        query.value = state.query;
      }
    }
    return { getNavigationState, query, restoreNavigationState };
  },
  template: '<div data-test="track-browser-view">{{ query }} · {{ tracks.length }}</div>',
});

const tracks: GpsTrack[] = [
  { id: 11, trackName: 'Activity.fit' },
  { id: 12, trackName: 'Morning ride' },
];

function mountStatistics() {
  const wrapper = shallowMount(Statistics, {
    props: { tracks },
    global: {
      stubs: {
        BottomSheet: PassthroughStub,
        Popover: PassthroughStub,
        StatisticsOverview: StatisticsOverviewStub,
        TabPanel: PassthroughStub,
        TabPanels: PassthroughStub,
        Tabs: PassthroughStub,
        TrackBrowserQuickViews: true,
        TrackBrowserView: TrackBrowserViewStub,
      },
    },
  });
  (wrapper.vm as unknown as { active: boolean }).active = true;
  return wrapper;
}

describe('Statistics navigation state', () => {
  beforeEach(() => setActivePinia(createPinia()));

  it('restores the originating Tracks search after the panel is recreated', async () => {
    const wrapper = mountStatistics();
    await nextTick();
    const statistics = wrapper.vm as unknown as {
      active: boolean;
      getNavigationState: () => unknown;
      restoreNavigationState: (state: unknown) => void;
    };

    statistics.restoreNavigationState({
      tab: 'tracks',
      trackQuickView: 'all',
      trackBrowserState: { query: 'Activity.fit' },
    });
    await nextTick();
    await nextTick();
    const navigation = statistics.getNavigationState();
    expect(navigation).toEqual({
      tab: 'tracks',
      trackQuickView: 'all',
      trackBrowserState: { query: 'Activity.fit' },
    });

    statistics.active = false;
    await nextTick();
    statistics.active = true;
    await nextTick();
    statistics.restoreNavigationState(navigation);
    await nextTick();
    await nextTick();

    expect(wrapper.get('[data-test="track-browser-view"]').text()).toContain('Activity.fit · 2');
  });

  it('shows a newly excluded highlight in the Excluded view without reloading tracks', async () => {
    const wrapper = mountStatistics();
    await nextTick();
    const overview = wrapper.findComponent(StatisticsOverviewStub);

    overview.vm.$emit('track-updated', { id: 11, highlightExclusionReason: 'GPS_NOISE' } as GpsTrack);
    overview.vm.$emit('view-highlight-exclusions');
    await nextTick();

    const browser = wrapper.findComponent(TrackBrowserViewStub);
    expect(browser.props('tracks')).toEqual([
      expect.objectContaining({
        id: 11,
        trackName: 'Activity.fit',
        highlightExclusionReason: 'GPS_NOISE',
      }),
    ]);
  });
});
