import { mount } from '@vue/test-utils';
import { defineComponent, nextTick } from 'vue';
import { describe, expect, it, vi } from 'vitest';
import TrackBrowserTable from '@/components/track-browser/TrackBrowserTable.vue';
import TrackDetailOverview from '@/components/trackdetails/TrackDetailOverview.vue';

function popoverStub(toggle: ReturnType<typeof vi.fn>) {
  return defineComponent({
    name: 'Popover',
    methods: { toggle },
    template: '<div data-test="popover"><slot /></div>',
  });
}

const ActivityTypeBadgeStub = defineComponent({
  name: 'ActivityTypeBadge',
  template: '<span data-test="activity-badge" />',
});

describe('mobile-friendly info popovers', () => {
  it('opens Track Detail energy explanations via click instead of hover tooltip', async () => {
    const toggle = vi.fn();
    const wrapper = mount(TrackDetailOverview, {
      props: {
        gpsTrack: {
          id: 1,
          trackName: 'Morning Ride',
          indexedFile: { name: 'ride.gpx', path: 'ride.gpx' },
          startDate: new Date('2026-01-01T08:00:00Z'),
          endDate: new Date('2026-01-01T09:00:00Z'),
          trackLengthInMeter: 25_000,
          ascentInMeter: 400,
          descentInMeter: -350,
          energyNetTotalWh: 180,
          powerWattsAvg: 120,
          powerWatts30sMax: 260,
          explorationStatus: 'CALCULATED',
          explorationScore: 0.42,
        },
        trackDetails: [{ x: 0, y: 0 }],
      },
      global: {
        directives: { tooltip: {} },
        stubs: {
          ActivityTypeBadge: ActivityTypeBadgeStub,
          Popover: popoverStub(toggle),
        },
      },
    });
    await nextTick();

    await wrapper.find('button[aria-label="About Average Power"]').trigger('click');

    expect(toggle).toHaveBeenCalledOnce();
    const infoText = (
      wrapper.vm as unknown as { currentInfoContent: Array<Array<{ text: string }>> }
    ).currentInfoContent
      .flat()
      .map((segment) => segment.text)
      .join('');
    expect(infoText).toContain('average external mechanical power');
    expect(wrapper.find('[data-test="popover"]').html()).toContain('average external mechanical power');
  });

  it('opens Track Browser mobile energy info without opening the track', async () => {
    const toggle = vi.fn();
    const wrapper = mount(TrackBrowserTable, {
      props: {
        compact: true,
        query: '',
        selectedTrackId: null,
        rows: [
          {
            id: 1,
            displayName: 'Morning Ride',
            startDate: new Date('2026-01-01T08:00:00Z'),
            trackLengthInMeter: 25_000,
            durationMillis: 3_600_000,
            energyNetTotalWh: 180,
          },
        ],
      },
      global: {
        directives: { tooltip: {} },
        stubs: {
          ActivityTypeBadge: ActivityTypeBadgeStub,
          Button: true,
          Popover: popoverStub(toggle),
          TrackShapePreview: defineComponent({ template: '<span data-test="shape" />' }),
        },
      },
    });

    await wrapper.find('button[aria-label="About energy"]').trigger('click');

    expect(toggle).toHaveBeenCalledOnce();
    expect(wrapper.emitted('open-details')).toBeUndefined();
    expect(wrapper.find('[data-test="popover"]').text()).toContain('not metabolic calorie burn');
  });
});
