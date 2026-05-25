import { mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import TrackDetailEvents from '@/components/trackdetails/TrackDetailEvents.vue';
import type { GpsTrackEvent } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';

function breakEvent(id: number, durationInSec: number): GpsTrackEvent {
  return {
    id,
    eventType: 'STOP',
    source: 'DETECTED',
    startPointIndex: id,
    startTimestamp: new Date(`2026-01-01T10:${id.toString().padStart(2, '0')}:00Z`),
    startDistanceInMeter: id * 1000,
    durationInSec,
  };
}

function recordingGapBreakEvent(id: number, durationInSec: number): GpsTrackEvent {
  return {
    ...breakEvent(id, durationInSec),
    description: 'Low-displacement GPS recording gap: distance=23.8m, impliedSpeed=0.02 km/h',
  };
}

describe('TrackDetailEvents selection', () => {
  beforeEach(() => {
    Element.prototype.scrollIntoView = vi.fn();
  });

  it('emits the selected break key when a break row is clicked', async () => {
    const wrapper = mount(TrackDetailEvents, {
      props: {
        events: [breakEvent(1, 60), breakEvent(2, 120)],
      },
    });

    await wrapper.find('.event-row--break').trigger('click');

    expect(wrapper.emitted('select-event')).toEqual([[1]]);
  });

  it('marks the matching break row as selected from the selected event key prop', async () => {
    const wrapper = mount(TrackDetailEvents, {
      props: {
        events: [breakEvent(1, 60), breakEvent(2, 120)],
        selectedEventKey: 2,
      },
    });

    const rows = wrapper.findAll('.event-row--break');

    expect(rows[0].classes()).not.toContain('event-row--selected');
    expect(rows[1].classes()).toContain('event-row--selected');
  });

  it('surfaces recording-gap detected stops as GPS gap breaks', () => {
    const wrapper = mount(TrackDetailEvents, {
      props: {
        events: [breakEvent(1, 60), recordingGapBreakEvent(2, 4_110)],
      },
    });

    expect(wrapper.text()).toContain('GPS gap · Longest');
    expect(wrapper.text()).toContain('Low-displacement GPS recording gap');
  });

  it('shows break start date/time and same-day end time', () => {
    const startTimestamp = new Date(2026, 0, 1, 10, 1, 0);
    const endTimestamp = new Date(2026, 0, 1, 10, 6, 0);
    const wrapper = mount(TrackDetailEvents, {
      props: {
        events: [
          {
            ...breakEvent(1, 300),
            startTimestamp,
            endTimestamp,
          },
        ],
      },
    });

    const expectedStart = new Intl.DateTimeFormat(undefined, {
      dateStyle: 'medium',
      timeStyle: 'short',
    }).format(startTimestamp);
    const expectedEnd = new Intl.DateTimeFormat(undefined, {
      timeStyle: 'short',
    }).format(endTimestamp);

    expect(wrapper.find('.event-time').text()).toBe(`${expectedStart} - ${expectedEnd}`);
  });
});
