import { mount } from '@vue/test-utils';
import { defineComponent } from 'vue';
import { describe, expect, it } from 'vitest';
import TrackBrowserTable from '@/components/track-browser/TrackBrowserTable.vue';

const ActivityTypeBadgeStub = defineComponent({
  name: 'ActivityTypeBadge',
  template: '<span data-test="activity-badge" />',
});

describe('TrackBrowserTable', () => {
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
