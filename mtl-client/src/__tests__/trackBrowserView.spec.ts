import { mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import type { GpsTrack } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import TrackBrowserView from '@/components/track-browser/TrackBrowserView.vue';
import TrackBrowserSummaryBar from '@/components/track-browser/TrackBrowserSummaryBar.vue';

const TrackBrowserControlsStub = {
  name: 'TrackBrowserControls',
  props: ['query'],
  emits: ['update:query'],
  template: '<div data-test="track-browser-controls">{{ query }}</div>',
};

const TrackBrowserTableStub = {
  name: 'TrackBrowserTable',
  props: ['rows', 'selectedTrackId', 'query', 'compact', 'sortResetKey'],
  emits: ['select-track', 'open-details', 'track-updated'],
  methods: {
    getSortState: () => ({ field: 'startDate', order: -1 }),
    restoreSortState: () => {},
  },
  template: '<div data-test="track-browser-table">{{ query }} · {{ rows.length }}<slot name="summary" /></div>',
};

const tracks: GpsTrack[] = [
  { id: 1, trackName: 'Morning walk', startDate: new Date(2024, 0, 1), trackLengthInMeter: 1_000 },
  { id: 2, trackName: 'Evening ride', startDate: new Date(2024, 0, 2), trackLengthInMeter: 2_000 },
];

describe('TrackBrowserView', () => {
  it('updates searchable and sortable rows and preserves refreshes across stale parent rebuilds', async () => {
    const original = { ...tracks[0]!, version: 1 };
    const wrapper = mount(TrackBrowserView, {
      props: { tracks: [original], compact: true },
      global: { stubs: { TrackBrowserControls: TrackBrowserControlsStub, TrackBrowserTable: TrackBrowserTableStub } },
    });
    const table = wrapper.getComponent(TrackBrowserTableStub);
    table.vm.$emit('track-updated', {
      id: 1,
      version: 2,
      energyNetTotalWh: 120,
      activityType: 'WALKING',
      activityTypeSource: 'AUTO_GUESS',
    });
    await wrapper.vm.$nextTick();
    expect(table.props('rows')[0]).toMatchObject({ energyNetTotalWh: 120, activityType: 'WALKING' });
    expect(table.props('rows')[0].searchText).toContain('120');
    expect(wrapper.emitted('track-updated')?.[0]?.[0]).toMatchObject({ id: 1, version: 2, energyNetTotalWh: 120 });
    await wrapper.setProps({ tracks: [{ ...original }] });
    expect(table.props('rows')[0].energyNetTotalWh).toBe(120);
    await wrapper.setProps({ tracks: [{ ...original, version: 3, energyNetTotalWh: 200 }] });
    expect(table.props('rows')[0].energyNetTotalWh).toBe(200);
    wrapper.unmount();
  });

  it('owns the shared search, summary, table, and interaction wiring', async () => {
    const wrapper = mount(TrackBrowserView, {
      props: { tracks, compact: true, selectedTrackId: 2 },
      slots: { toolbar: '<div data-test="toolbar">Views</div>' },
      global: {
        directives: { tooltip: {} },
        stubs: {
          TrackBrowserControls: TrackBrowserControlsStub,
          TrackBrowserTable: TrackBrowserTableStub,
        },
      },
    });
    const controls = wrapper.findComponent(TrackBrowserControlsStub);
    const table = wrapper.findComponent(TrackBrowserTableStub);

    expect(wrapper.get('[data-test="toolbar"]').text()).toBe('Views');
    expect(wrapper.findComponent(TrackBrowserSummaryBar).props('totalCount')).toBe(2);
    expect(table.props('compact')).toBe(true);
    expect(table.props('selectedTrackId')).toBe(2);

    controls.vm.$emit('update:query', 'ride');
    await wrapper.vm.$nextTick();
    expect(table.props('query')).toBe('ride');
    expect(table.props('rows')).toHaveLength(1);

    const navigation = (
      wrapper.vm as unknown as {
        getNavigationState: () => unknown;
        restoreNavigationState: (state: unknown) => void;
      }
    ).getNavigationState();
    await wrapper.setProps({ resetKey: 1 });
    expect(table.props('query')).toBe('');
    (
      wrapper.vm as unknown as {
        restoreNavigationState: (state: unknown) => void;
      }
    ).restoreNavigationState(navigation);
    await wrapper.vm.$nextTick();
    expect(table.props('query')).toBe('ride');
    expect(table.props('rows')).toHaveLength(1);

    table.vm.$emit('select-track', 2);
    table.vm.$emit('open-details', 2);
    expect(wrapper.emitted('select-track')).toEqual([[2]]);
    expect(wrapper.emitted('open-details')).toEqual([[2]]);

    await wrapper.setProps({ resetKey: 2 });
    expect(table.props('query')).toBe('');
    expect(table.props('rows')).toHaveLength(2);
  });

  it('switches to the card view when its container becomes narrow', async () => {
    let resizeCallback: ResizeObserverCallback | undefined;
    const frames: FrameRequestCallback[] = [];
    vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => {
      frames.push(callback);
      return frames.length;
    });
    vi.stubGlobal('cancelAnimationFrame', vi.fn());
    vi.stubGlobal(
      'ResizeObserver',
      class {
        constructor(callback: ResizeObserverCallback) {
          resizeCallback = callback;
        }

        observe() {}
        disconnect() {}
      }
    );

    const wrapper = mount(TrackBrowserView, {
      props: { tracks },
      global: {
        directives: { tooltip: {} },
        stubs: {
          TrackBrowserControls: TrackBrowserControlsStub,
          TrackBrowserTable: TrackBrowserTableStub,
        },
      },
    });
    const table = wrapper.findComponent(TrackBrowserTableStub);

    expect(table.props('compact')).toBe(false);
    resizeCallback?.([{ contentRect: { width: 390 } } as ResizeObserverEntry], {} as ResizeObserver);
    frames.shift()?.(0);
    await wrapper.vm.$nextTick();
    expect(table.props('compact')).toBe(true);

    wrapper.unmount();
    vi.unstubAllGlobals();
  });
});
