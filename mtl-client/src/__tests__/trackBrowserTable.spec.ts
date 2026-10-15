import { flushPromises, mount } from '@vue/test-utils';
import { defineComponent } from 'vue';
import { createMemoryHistory, createRouter } from 'vue-router';
import { describe, expect, it, vi } from 'vitest';
import TrackBrowserTable from '@/components/track-browser/TrackBrowserTable.vue';
vi.mock('@/composables/useIndexerStatus', () => ({
  useIndexerStatus: () => ({ jobSummaries: { value: [] } }),
}));

const ActivityTypeBadgeStub = defineComponent({
  name: 'ActivityTypeBadge',
  template: '<span data-test="activity-badge" />',
});

describe('TrackBrowserTable', () => {
  it.each([false, true])('shows pending and unavailable activity states (compact: %s)', (compact) => {
    for (const activityTypeSource of [undefined, 'FAILED'] as const) {
      const row = {
        id: 1,
        displayName: 'Synthetic route',
        durationMillis: null,
        startDateMs: 0,
        createDateMs: 0,
        avgSpeedKmh: null,
        searchText: '',
        activityTypeSource,
      };
      const wrapper = mount(TrackBrowserTable, {
        props: { compact, query: '', selectedTrackId: null, rows: [row] },
        global: {
          directives: { tooltip: {} },
          stubs: {
            Button: true,
            Popover: true,
            TrackShapePreview: true,
            TrackEnergyValue: true,
            DataTable: defineComponent({ template: '<div><slot /></div>' }),
            Column: defineComponent({
              setup: () => ({ row }),
              template: '<div><slot name="body" :data="row" /></div>',
            }),
          },
        },
      });
      expect(wrapper.get('.track-browser-table__state').text()).toContain(
        activityTypeSource ? 'Unavailable' : 'Pending'
      );
      wrapper.unmount();
    }
  });
  it.each([false, true])('opens description text but keeps More/Less in the list (compact: %s)', async (compact) => {
    const description = 'A long synthetic route description. '.repeat(12);
    const row = {
      id: 1,
      displayName: 'Synthetic route',
      trackDescription: description,
      durationMillis: 0,
      startDateMs: 0,
      createDateMs: 0,
      avgSpeedKmh: null,
      searchText: '',
    };
    const width = vi.spyOn(HTMLElement.prototype, 'clientWidth', 'get').mockImplementation(function () {
      return this.classList.contains('track-description__measurement') ? 200 : 0;
    });
    const textWidth = vi.spyOn(HTMLElement.prototype, 'scrollWidth', 'get').mockImplementation(function () {
      return this.classList.contains('track-description__measurement') ? 800 : 0;
    });
    const wrapper = mount(TrackBrowserTable, {
      props: { compact, query: '', selectedTrackId: null, rows: [row] },
      global: {
        directives: { tooltip: {} },
        stubs: {
          ActivityTypeBadge: ActivityTypeBadgeStub,
          Button: true,
          Popover: true,
          TrackShapePreview: true,
          DataTable: defineComponent({
            emits: ['row-click'],
            setup: () => ({ row }),
            template: '<div @click="$emit(\'row-click\', { data: row, originalEvent: $event })"><slot /></div>',
          }),
          Column: defineComponent({
            setup: () => ({ row }),
            template: '<div><slot name="body" :data="row" /></div>',
          }),
        },
      },
      attachTo: document.body,
    });
    try {
      await flushPromises();
      const toggle = wrapper.get('.track-description__toggle');
      expect(toggle.attributes('aria-expanded')).toBe('false');
      await toggle.trigger('click');
      expect(toggle.attributes('aria-expanded')).toBe('true');
      expect(wrapper.get('.track-description__text').text()).toBe(description.trim());
      expect(wrapper.emitted('open-details')).toBeUndefined();
      await toggle.trigger('click');
      expect(toggle.attributes('aria-expanded')).toBe('false');
      expect(wrapper.emitted('open-details')).toBeUndefined();

      await wrapper.get('.track-description__text').trigger('click');
      expect(wrapper.emitted('open-details')).toEqual([[1]]);
      await toggle.trigger('click');
      expect(toggle.attributes('aria-expanded')).toBe('true');
      expect(wrapper.emitted('open-details')).toEqual([[1]]);
      await wrapper.get('.track-description__text').trigger('click');
      expect(wrapper.emitted('open-details')).toEqual([[1], [1]]);
      await toggle.trigger('click');
      expect(toggle.attributes('aria-expanded')).toBe('false');
      expect(wrapper.emitted('open-details')).toEqual([[1], [1]]);

      await wrapper.get(compact ? '.track-browser-card__name' : '.track-browser-table__name').trigger('click');
      expect(wrapper.emitted('open-details')).toEqual([[1], [1], [1]]);

      const selection = window.getSelection()!;
      const range = document.createRange();
      range.selectNodeContents(wrapper.get('.track-description__text').element);
      selection.addRange(range);
      const clickDescription = () =>
        wrapper
          .get('.track-description__text')
          .element.dispatchEvent(new MouseEvent('click', { bubbles: true, detail: 1 }));
      clickDescription();
      expect(wrapper.emitted('open-details')).toEqual([[1], [1], [1]]);
      selection.removeAllRanges();
      clickDescription();
      expect(wrapper.emitted('open-details')).toEqual([[1], [1], [1], [1]]);

      const outside = document.createElement('p');
      outside.textContent = 'Unrelated selected text';
      document.body.append(outside);
      range.selectNodeContents(outside);
      selection.addRange(range);
      try {
        clickDescription();
        expect(wrapper.emitted('open-details')).toEqual([[1], [1], [1], [1], [1]]);
      } finally {
        outside.remove();
      }
    } finally {
      window.getSelection()?.removeAllRanges();
      wrapper.unmount();
      width.mockRestore();
      textWidth.mockRestore();
    }
  });

  it('opens the source from a review badge without also opening track details', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', name: 'home', component: { template: '<div />' } },
        { path: '/review/:sourceId', name: 'review', component: { template: '<div />' } },
      ],
    });
    await router.push('/');
    const wrapper = mount(TrackBrowserTable, {
      props: {
        compact: true,
        query: '',
        selectedTrackId: null,
        rows: [
          {
            id: 10,
            indexedFile: { id: 99 },
            displayName: 'Synthetic clock error',
            durationMillis: null,
            startDateMs: 0,
            createDateMs: 0,
            avgSpeedKmh: null,
            searchText: '',
            reviewStatus: 'NEEDS_REVIEW',
          },
        ],
      },
      global: {
        plugins: [router],
        directives: { tooltip: {} },
        stubs: {
          ActivityTypeBadge: ActivityTypeBadgeStub,
          Button: true,
          Popover: true,
          TrackShapePreview: true,
        },
      },
    });
    await wrapper.get('a[data-test="curation-badge-review"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.params.sourceId).toBe('99');
    expect(wrapper.emitted('open-details')).toBeUndefined();
    expect(wrapper.emitted('select-track')).toBeUndefined();
    wrapper.unmount();
  });

  it('shows source review status and unavailable timing on compact track cards', () => {
    const wrapper = mount(TrackBrowserTable, {
      props: {
        compact: true,
        query: '',
        selectedTrackId: null,
        rows: [
          {
            id: 1,
            displayName: 'Synthetic clock error',
            durationMillis: null,
            startDateMs: 0,
            createDateMs: 0,
            avgSpeedKmh: null,
            searchText: '',
            timingUnreliable: true,
            reviewExcluded: true,
            reviewStatus: 'NEEDS_REVIEW',
          },
        ],
      },
      global: {
        directives: { tooltip: {} },
        stubs: { ActivityTypeBadge: ActivityTypeBadgeStub, Button: true, Popover: true, TrackShapePreview: true },
      },
    });
    expect(wrapper.get('[data-test="curation-badge-review"]').text()).toBe('Needs review');
    expect(wrapper.get('[data-test="curation-badge-timing"]').text()).toBe('Timing unavailable');
    expect(wrapper.get('.track-browser-card__meta').text()).toContain('Time unavailable');
  });

  it('keeps toolbar and column sorting in sync across desktop and cards', async () => {
    const DataTableStub = defineComponent({
      name: 'DataTable',
      props: { sortField: String, sortOrder: Number, first: Number },
      emits: ['sort', 'page'],
      template: '<div />',
    });
    const wrapper = mount(TrackBrowserTable, {
      props: {
        compact: true,
        query: '',
        selectedTrackId: null,
        rows: [
          { id: 1, displayName: 'Route 10', trackLengthInMeter: 1000 },
          { id: 2, displayName: 'Route 2', trackLengthInMeter: 2000 },
          { id: 3, displayName: 'Route 3', trackLengthInMeter: undefined },
        ].map((row) => ({
          ...row,
          durationMillis: 0,
          startDateMs: 0,
          createDateMs: 0,
          avgSpeedKmh: null,
          searchText: '',
        })),
      },
      global: {
        directives: { tooltip: {} },
        stubs: { DataTable: DataTableStub, Column: true, Button: true, Popover: true, TrackShapePreview: true },
      },
    });
    const names = () => wrapper.findAll('.track-browser-card__name').map((name) => name.text());
    await wrapper.get('select').setValue('displayName');
    expect(names()).toEqual(['Route 2', 'Route 3', 'Route 10']);
    await wrapper.get('.track-browser-table__sort-direction').trigger('click');
    expect(names()).toEqual(['Route 10', 'Route 3', 'Route 2']);

    await wrapper.setProps({ compact: false });
    const table = wrapper.findComponent(DataTableStub);
    expect(table.props('sortField')).toBe('displayName');
    expect(table.props('sortOrder')).toBe(-1);
    table.vm.$emit('page', { first: 25, rows: 25 });
    table.vm.$emit('sort', { sortField: 'trackLengthInMeter', sortOrder: 1 });
    await wrapper.vm.$nextTick();
    expect(table.props('first')).toBe(0);
    expect(wrapper.get('select').element.value).toBe('trackLengthInMeter');
    await wrapper.setProps({ compact: true });
    expect(names()).toEqual(['Route 10', 'Route 2', 'Route 3']);
    await wrapper.get('.track-browser-table__sort-direction').trigger('click');
    expect(names()).toEqual(['Route 2', 'Route 10', 'Route 3']);

    await wrapper.get('.track-browser-card__name').trigger('click');
    await wrapper.get('.track-browser-card__map').trigger('click');
    expect(wrapper.emitted('open-details')).toEqual([[2]]);
    expect(wrapper.emitted('select-track')).toEqual([[2]]);
    await wrapper.setProps({ sortResetKey: 1 });
    expect(wrapper.get('select').element.value).toBe('startDate');
    expect(wrapper.get('.track-browser-table__sort-direction').attributes('aria-label')).toContain('Descending order');
  });

  it('renders an explicit placeholder for an unavailable average speed', () => {
    const wrapper = mount(TrackBrowserTable, {
      props: {
        compact: true,
        query: '',
        selectedTrackId: null,
        rows: [],
      },
      global: {
        directives: { tooltip: {} },
        stubs: {
          ActivityTypeBadge: ActivityTypeBadgeStub,
          Button: true,
          Popover: defineComponent({ template: '<div><slot /></div>' }),
          TrackShapePreview: defineComponent({ template: '<span data-test="shape" />' }),
        },
      },
    });

    const component = wrapper.vm as unknown as { formatSpeed: (value: number | null) => string };
    expect(component.formatSpeed(null)).toBe('—');
  });

  it('renders compact curation badges for excluded tracks', () => {
    const wrapper = mount(TrackBrowserTable, {
      props: {
        compact: true,
        query: '',
        selectedTrackId: null,
        rows: [
          {
            id: 1,
            displayName: 'Noisy ride',
            durationMillis: 3_600_000,
            startDateMs: 1,
            createDateMs: 1,
            avgSpeedKmh: 20,
            searchText: '',
            highlightExclusionReason: 'GPS_NOISE',
            statisticsExclusionReason: 'IMPORT_ARTIFACT',
          },
        ],
      },
      global: {
        directives: { tooltip: {} },
        stubs: {
          ActivityTypeBadge: ActivityTypeBadgeStub,
          Button: true,
          Popover: defineComponent({ template: '<div><slot /></div>' }),
          TrackShapePreview: defineComponent({ template: '<span data-test="shape" />' }),
        },
      },
    });

    expect(wrapper.find('[data-test="curation-badge-highlight"]').text()).toBe('Highlights: GPS noise');
    expect(wrapper.find('[data-test="curation-badge-statistics"]').text()).toBe('Statistics: Import artifact');
  });
});
