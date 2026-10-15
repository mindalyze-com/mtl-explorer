import { enableAutoUnmount, shallowMount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { defineComponent, nextTick, ref } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { GpsTrack } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import Statistics from '@/components/statistics/Statistics.vue';
import TrackBrowserView from '@/components/track-browser/TrackBrowserView.vue';
import TrackBrowserControls from '@/components/track-browser/TrackBrowserControls.vue';

const DataTableStub = defineComponent({
  name: 'DataTable',
  props: { sortField: String, sortOrder: Number },
  emits: ['sort'],
  template: '<div />',
});

vi.mock('@/utils/ServiceHelper', () => ({
  fetchStatisticsForTrackIds: vi.fn(async () => []),
  resolveStatisticsTrackIds: vi.fn(async () => []),
}));
vi.mock('@/repositories/mediaRepository', () => ({
  getMediaTrends: vi.fn(async () => ({ buckets: [] })),
}));

enableAutoUnmount(afterEach);
afterEach(() => vi.restoreAllMocks());

const PassthroughStub = defineComponent({
  template: '<div><slot name="title" /><slot /></div>',
});

const BottomSheetStub = defineComponent({
  props: { backLabel: { type: String, default: '' } },
  emits: ['back'],
  template:
    '<div><button v-if="backLabel" :aria-label="backLabel" @click="$emit(\'back\')">Back</button><slot name="title" /><slot /></div>',
});

const StatisticsOverviewStub = defineComponent({
  name: 'StatisticsOverview',
  emits: ['open-media', 'track-updated', 'view-all-tracks', 'view-highlight-exclusions', 'latest-activity-change'],
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
  template: '<div data-test="track-browser-view"><slot name="toolbar" />{{ query }} · {{ tracks.length }}</div>',
});

const tracks: GpsTrack[] = [
  { id: 11, trackName: 'Activity.fit' },
  { id: 12, trackName: 'Morning ride' },
];

function mountStatistics(initialTracks = tracks, realBrowser = false) {
  const wrapper = shallowMount(Statistics, {
    attachTo: document.body,
    props: { tracks: initialTracks },
    global: {
      directives: { tooltip: {} },
      stubs: {
        BottomSheet: BottomSheetStub,
        Popover: PassthroughStub,
        StatisticsOverview: StatisticsOverviewStub,
        SectionHub: false,
        LatestActivityPreview: false,
        TrackShapePreview: true,
        TrackBrowserQuickViews: true,
        TrackBrowserView: realBrowser ? false : TrackBrowserViewStub,
        TrackBrowserTable: !realBrowser,
        TrackBrowserControls: !realBrowser,
        DataTable: DataTableStub,
        InputText: true,
        Select: true,
        Column: true,
        Button: true,
        Highcharts: true,
      },
    },
  });
  (wrapper.vm as unknown as { active: boolean }).active = true;
  return wrapper;
}

describe('Statistics navigation state', () => {
  beforeEach(() => setActivePinia(createPinia()));

  it('starts at the hub and opens each section from its full row', async () => {
    const wrapper = mountStatistics();
    await nextTick();
    expect(wrapper.get('[data-test="statistics-hub"]').isVisible()).toBe(true);
    expect(wrapper.get('section[aria-label="Overview"]').isVisible()).toBe(false);
    expect(wrapper.find('[aria-label="Back to Statistics"]').exists()).toBe(false);

    for (const [value, label] of [
      ['overview', 'Overview'],
      ['stats', 'Trends'],
      ['tracks', 'Tracks'],
    ]) {
      await wrapper.get(`[data-section="${value}"]`).trigger('click');
      await nextTick();
      expect(wrapper.get('h2').text()).toBe(label);
      expect(document.activeElement).toBe(wrapper.get('h2').element);
      expect(wrapper.get(`section[aria-label="${label}"]`).isVisible()).toBe(true);
      expect(wrapper.get('[data-test="statistics-hub"]').isVisible()).toBe(false);

      await wrapper.get('[aria-label="Back to Statistics"]').trigger('click');
      await nextTick();
      expect(wrapper.get('[data-test="statistics-hub"]').isVisible()).toBe(true);
      expect(document.activeElement).toBe(wrapper.get(`[data-section="${value}"]`).element);
    }
  });

  it('opens the latest track and keeps the Tracks search without showing a last-viewed shortcut', async () => {
    const wrapper = mountStatistics();
    await nextTick();
    wrapper.findComponent(StatisticsOverviewStub).vm.$emit('latest-activity-change', {
      track: { trackId: 12, displayName: 'Morning ride', distanceM: 12000, durationMs: 3600000 },
      loading: false,
      error: false,
    });
    await nextTick();
    await wrapper.get('[aria-label="Open track: Morning ride"]').trigger('click');
    expect(wrapper.emitted('open-details')).toEqual([[12]]);
    await wrapper.get('[data-section="tracks"]').trigger('click');
    wrapper.findComponent(TrackBrowserViewStub).vm.restoreNavigationState({ query: 'Morning' });

    const statistics = wrapper.vm as unknown as { close: () => void; open: () => Promise<void> };
    statistics.close();
    await nextTick();
    await statistics.open();
    await nextTick();
    expect(wrapper.get('[data-test="statistics-hub"]').isVisible()).toBe(true);
    expect(wrapper.find('.section-hub__footer').exists()).toBe(false);
    await wrapper.get('[data-section="tracks"]').trigger('click');
    expect(wrapper.get('[data-test="track-browser-view"]').text()).toContain('Morning');
    expect(wrapper.get('section[aria-label="Tracks"]').isVisible()).toBe(true);
  });

  it('keeps section navigation available when the latest activity is empty or unavailable', async () => {
    const wrapper = mountStatistics();
    await nextTick();
    const overview = wrapper.findComponent(StatisticsOverviewStub);
    overview.vm.$emit('latest-activity-change', { track: null, loading: false, error: false });
    await nextTick();
    expect(wrapper.get('.latest-activity-preview').text()).toContain('No activities in the current filter.');
    expect(wrapper.get('[data-section="overview"]').isVisible()).toBe(true);

    overview.vm.$emit('latest-activity-change', { track: null, loading: false, error: true });
    await nextTick();
    expect(wrapper.get('.latest-activity-preview').text()).toContain('could not be loaded');
    await wrapper.get('.latest-activity-preview__retry').trigger('click');
    expect(wrapper.findComponent(StatisticsOverviewStub).attributes('retry-revision')).toBe('1');
  });

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

  it.each([
    { compact: true, field: 'displayName', order: 1 },
    { compact: true, field: 'displayName', order: -1 },
    { compact: false, field: 'trackLengthInMeter', order: 1 },
    { compact: false, field: 'trackLengthInMeter', order: -1 },
  ])(
    'restores $field order $order after recreating the real browser (compact=$compact)',
    async ({ compact, field, order }) => {
      const width = vi.spyOn(HTMLElement.prototype, 'clientWidth', 'get').mockReturnValue(compact ? 390 : 1024);
      const frame = vi.spyOn(window, 'requestAnimationFrame').mockImplementation((callback) => {
        callback(0);
        return 1;
      });
      const wrapper = mountStatistics(
        [
          { id: 1, trackName: 'Route 10', trackLengthInMeter: 1000 },
          { id: 2, trackName: 'Route 2', trackLengthInMeter: 2000 },
          { id: 3, trackName: 'Other', trackLengthInMeter: 3000 },
        ],
        true
      );
      await nextTick();
      await wrapper.get('[data-section="tracks"]').trigger('click');
      let browser = wrapper.findComponent(TrackBrowserView);
      browser.findComponent(TrackBrowserControls).vm.$emit('update:query', 'Route');
      await nextTick();
      if (compact) {
        await browser.get('select').setValue(field);
        if (order === -1) await browser.get('.track-browser-table__sort-direction').trigger('click');
      } else {
        browser.findComponent(DataTableStub).vm.$emit('sort', { sortField: field, sortOrder: order });
        await nextTick();
      }
      const statistics = wrapper.vm as unknown as {
        getNavigationState: () => unknown;
        restoreNavigationState: (state: unknown) => void;
        close: () => void;
        open: () => Promise<void>;
      };
      const navigation = statistics.getNavigationState();
      statistics.close();
      await nextTick();
      expect(wrapper.findComponent(TrackBrowserView).exists()).toBe(false);
      await statistics.open();
      statistics.restoreNavigationState(navigation);
      await nextTick();
      await nextTick();
      browser = wrapper.findComponent(TrackBrowserView);

      expect(browser.findComponent(TrackBrowserControls).props('query')).toBe('Route');
      expect(browser.get('select').element.value).toBe(field);
      expect(browser.get('.track-browser-table__sort-direction').attributes('aria-label')).toContain(
        order === 1 ? 'Ascending order' : 'Descending order'
      );
      if (compact) {
        const names = browser.findAll('.track-browser-card__name').map((row) => row.text());
        expect(names).toEqual(order === 1 ? ['Route 2', 'Route 10'] : ['Route 10', 'Route 2']);
      } else {
        expect(browser.findComponent(DataTableStub).props()).toMatchObject({ sortField: field, sortOrder: order });
      }
      wrapper.findComponent(StatisticsOverviewStub).vm.$emit('view-all-tracks');
      await nextTick();
      expect(browser.findComponent(TrackBrowserControls).props('query')).toBe('');
      expect(browser.get('select').element.value).toBe('startDate');
      expect(browser.get('.track-browser-table__sort-direction').attributes('aria-label')).toContain(
        'Descending order'
      );
      width.mockRestore();
      frame.mockRestore();
    }
  );

  it('filters pending reviews and includes source decisions in exclusion views', async () => {
    const pending = { id: 1, reviewStatus: 'NEEDS_REVIEW', reviewExcluded: true };
    const excluded = { id: 2, reviewStatus: 'EXCLUDED', reviewExcluded: true };
    const wrapper = mountStatistics([pending, excluded, { id: 3, reviewStatus: 'INCLUDED' }]);
    await nextTick();
    await wrapper.get('[data-section="tracks"]').trigger('click');
    const quickViews = wrapper.findComponent({ name: 'TrackBrowserQuickViews' });
    quickViews.vm.$emit('update:modelValue', 'needs-review');
    await nextTick();
    expect(wrapper.findComponent(TrackBrowserViewStub).props('tracks')).toEqual([pending]);
    for (const value of ['highlight-exclusions', 'statistics-exclusions']) {
      quickViews.vm.$emit('update:modelValue', value);
      await nextTick();
      expect(wrapper.findComponent(TrackBrowserViewStub).props('tracks')).toEqual([pending, excluded]);
    }
  });

  it('keeps map-excluded recordings accessible through their own browser view', async () => {
    const hidden = { id: 1, reviewMapExcluded: true };
    const statsOnly = { id: 2, reviewExcluded: true };
    const wrapper = mountStatistics([hidden, statsOnly]);
    await nextTick();
    await wrapper.get('[data-section="tracks"]').trigger('click');
    wrapper.findComponent({ name: 'TrackBrowserQuickViews' }).vm.$emit('update:modelValue', 'map-exclusions');
    await nextTick();
    expect(wrapper.findComponent(TrackBrowserViewStub).props('tracks')).toEqual([hidden]);
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

  it('does not restore a curated track after filters remove it', async () => {
    const wrapper = mountStatistics();
    await nextTick();

    wrapper
      .findComponent(StatisticsOverviewStub)
      .vm.$emit('track-updated', { id: 11, trackName: 'Activity.fit', highlightExclusionReason: 'GPS_NOISE' });
    await nextTick();
    expect(wrapper.findComponent(TrackBrowserViewStub).props('tracks')).toHaveLength(2);

    await wrapper.setProps({ tracks: [tracks[1]] });

    expect(wrapper.findComponent(TrackBrowserViewStub).props('tracks')).toEqual([tracks[1]]);
  });

  it('removes a restored highlight from Excluded when fresh track metadata arrives', async () => {
    const wrapper = mountStatistics();
    await nextTick();
    const overview = wrapper.findComponent(StatisticsOverviewStub);
    overview.vm.$emit('track-updated', { id: 11, highlightExclusionReason: 'OTHER' });
    overview.vm.$emit('view-highlight-exclusions');
    await nextTick();
    expect(wrapper.findComponent(TrackBrowserViewStub).props('tracks')).toHaveLength(1);
    expect(wrapper.emitted('track-updated')?.[0]?.[0]).toMatchObject({ id: 11, highlightExclusionReason: 'OTHER' });

    await wrapper.setProps({ tracks: [{ ...tracks[0], highlightExclusionReason: undefined }, tracks[1]] });

    expect(wrapper.findComponent(TrackBrowserViewStub).props('tracks')).toEqual([]);
  });

  it('opens the Media trend from the overview tile', async () => {
    const wrapper = mountStatistics();
    await nextTick();

    wrapper.findComponent(StatisticsOverviewStub).vm.$emit('open-media');
    await nextTick();

    expect((wrapper.vm as unknown as { getNavigationState: () => { tab: string } }).getNavigationState().tab).toBe(
      'stats'
    );
  });
});
