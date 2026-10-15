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

function recordingGapEvent(id: number): GpsTrackEvent {
  return {
    ...breakEvent(id, 0),
    eventType: 'GPS_GAP',
    label: 'Recording break',
    startPointLongLat: { coordinates: [7.45, 46.95 + id * 0.001] },
  } as GpsTrackEvent;
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

  it.each(['click', 'keydown.enter', 'keydown.space'])(
    'selects and clears a recording gap with %s',
    async (interaction) => {
      const wrapper = mount(TrackDetailEvents, { props: { events: [recordingGapEvent(1)] } });
      const row = wrapper.get('.event-row');

      expect(row.attributes('role')).toBe('button');
      expect(row.attributes('tabindex')).toBe('0');
      expect(row.attributes('aria-pressed')).toBe('false');
      await row.trigger(interaction);
      expect(wrapper.emitted('select-event')).toEqual([[1]]);

      await wrapper.setProps({ selectedEventKey: '1' });
      expect(row.attributes('aria-pressed')).toBe('true');
      expect(row.classes()).toContain('event-row--selected');
      await row.trigger(interaction);
      expect(wrapper.emitted('select-event')).toEqual([[1], [null]]);

      await wrapper.setProps({ selectedEventKey: null });
      expect(row.attributes('aria-pressed')).toBe('false');
      expect(row.classes()).not.toContain('event-row--selected');
    }
  );

  it('switches between recording gaps without counting them as stationary breaks', async () => {
    const wrapper = mount(TrackDetailEvents, {
      props: { events: [recordingGapEvent(1), recordingGapEvent(2), breakEvent(3, 120)], selectedEventKey: 1 },
    });
    const rows = wrapper.findAll('.event-row');
    await rows[1].trigger('click');
    expect(wrapper.emitted('select-event')).toEqual([[2]]);
    await wrapper.setProps({ selectedEventKey: 2 });

    expect(rows.map((row) => row.attributes('aria-pressed'))).toEqual(['false', 'true', 'false']);
    expect(wrapper.findAll('.event-row--break')).toHaveLength(1);
    expect(wrapper.get('.events-summary').text()).toBe('1 break · 2 recording gaps');
    expect(rows[0].find('.event-duration').exists()).toBe(false);
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

  it('keeps recording-gap evidence separate from observed breaks', () => {
    const wrapper = mount(TrackDetailEvents, {
      props: {
        events: [breakEvent(1, 60), recordingGapBreakEvent(2, 4_110)],
      },
    });

    expect(wrapper.get('.events-summary').text()).toBe('1 break · 1 recording gap');
    expect(wrapper.text()).toContain('Recording gap');
    expect(wrapper.text()).toContain('No recorded positions in this interval.');
    expect(wrapper.text()).not.toContain('impliedSpeed');
  });

  it('shows the date once and a compact clock range for breaks', () => {
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
      hour: '2-digit',
      minute: '2-digit',
      hourCycle: 'h23',
    }).format(startTimestamp);
    const expectedEnd = new Intl.DateTimeFormat(undefined, {
      hour: '2-digit',
      minute: '2-digit',
      hourCycle: 'h23',
    }).format(endTimestamp);

    expect(wrapper.find('.event-time').text()).toBe(`${expectedStart}–${expectedEnd}`);
    expect(wrapper.get('.event-day').text()).toBe(
      new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(startTimestamp)
    );
  });
});

describe('TrackDetailEvents motion evidence', () => {
  it('shows sampled boundary uncertainty and a recording edge without a probability', () => {
    const wrapper = mount(TrackDetailEvents, {
      props: {
        selectedEventKey: 1,
        events: [
          {
            ...breakEvent(1, 120),
            detectionEvidence: 'OBSERVED_STATIONARY',
            startBoundary: { earliestTimeS: 100, latestTimeS: 110, status: 'BRACKETED' },
            endBoundary: { earliestTimeS: 230, latestTimeS: 230, status: 'RECORDING_EDGE' },
          },
        ],
      },
    });
    expect(wrapper.text()).toMatch(/Start estimated between .*:40.* and .*:50/);
    expect(wrapper.text()).toContain('End reaches a recording gap.');
    expect(wrapper.text()).not.toContain('100%');
  });

  it('identifies a pause that touches the recorded track edge', () => {
    const wrapper = mount(TrackDetailEvents, {
      props: {
        selectedEventKey: 1,
        events: [
          {
            ...breakEvent(1, 120),
            detectionEvidence: 'OBSERVED_STATIONARY',
            startBoundary: { earliestTimeS: 0, latestTimeS: 0, status: 'TRACK_EDGE' },
          },
        ],
      },
    });
    expect(wrapper.text()).toContain('Start reaches the recorded track edge.');
  });
});

describe('TrackDetailEvents interval presentation', () => {
  it('shows ranges and proportional mini indicators for gaps and breaks on one scale', () => {
    const start = new Date(2026, 9, 12, 11, 15);
    const end = new Date(2026, 9, 12, 12, 33);
    const wrapper = mount(TrackDetailEvents, {
      props: {
        events: [
          breakEvent(1, 420),
          { ...recordingGapEvent(2), startTimestamp: start, endTimestamp: end, durationInSec: undefined },
          breakEvent(3, 2700),
        ],
      },
    });
    const rows = wrapper.findAll('.event-row');
    expect(rows.map((row) => row.get('.event-duration__value').text())).toEqual(['7 min', '1 h 18 min', '45 min']);
    expect(rows[1].get('.event-time').text()).toBe('11:15–12:33');
    const widths = rows.map((row) =>
      Number.parseFloat(row.get('.event-duration-scale__fill').attributes('style').split(':')[1])
    );
    expect(widths[0]).toBeCloseTo((420 / 4680) * 100);
    expect(widths[1]).toBe(100);
    expect(widths[2]).toBeCloseTo((2700 / 4680) * 100);
    expect(wrapper.findAll('.event-day')).toHaveLength(3);
  });

  it('shares a day heading and keeps sub-minute durations visible without seconds', () => {
    const wrapper = mount(TrackDetailEvents, { props: { events: [breakEvent(1, 20), breakEvent(2, 120)] } });
    expect(wrapper.findAll('.event-day')).toHaveLength(1);
    expect(wrapper.findAll('.event-duration__value').map((value) => value.text())).toEqual(['<1 min', '2 min']);
    expect(wrapper.get('.event-duration-scale').attributes('aria-label')).toContain('relative to the longest interval');
  });
});
