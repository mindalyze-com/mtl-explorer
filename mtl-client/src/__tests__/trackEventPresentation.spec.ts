import { describe, expect, it } from 'vitest';
import { useLocale } from '@/composables/useLocale';
import {
  formatTrackEventDate,
  formatTrackEventDuration,
  formatTrackEventTimeRange,
  trackEventDurationSeconds,
} from '@/utils/trackEvents';

describe('event interval formatting', () => {
  it('uses the configured application locale for event dates', () => {
    const locale = useLocale();
    const previous = locale.formatLocale.value;
    const date = new Date(2026, 9, 12, 11, 15);
    try {
      locale.setLocale('de-CH');
      expect(formatTrackEventDate(date)).toBe(new Intl.DateTimeFormat('de-CH', { dateStyle: 'medium' }).format(date));
    } finally {
      locale.setLocale(previous);
    }
  });
  it('uses recorded endpoints when an explicit duration is absent', () => {
    const event = { startTimestamp: new Date(2026, 9, 12, 11, 15), endTimestamp: new Date(2026, 9, 12, 12, 33) };
    expect(trackEventDurationSeconds(event)).toBe(4680);
    expect(formatTrackEventTimeRange(event)).toBe('11:15–12:33');
    expect(formatTrackEventTimeRange(event, true)).toBe(`${formatTrackEventDate(event.startTimestamp)} · 11:15–12:33`);
  });
  it('keeps midnight and multi-day ranges unambiguous', () => {
    const event = { startTimestamp: new Date(2026, 9, 12, 23, 50), endTimestamp: new Date(2026, 9, 13, 0, 20) };
    expect(formatTrackEventTimeRange(event)).toBe(`23:50–${formatTrackEventDate(event.endTimestamp)} · 00:20`);
    expect(formatTrackEventDuration(trackEventDurationSeconds(event))).toBe('30 min');
  });
  it('does not invent a duration for missing or reversed endpoints', () => {
    const start = new Date(2026, 9, 12, 10, 0);
    expect(trackEventDurationSeconds({ startTimestamp: start })).toBe(0);
    expect(trackEventDurationSeconds({ startTimestamp: start, endTimestamp: new Date(2026, 9, 12, 9, 0) })).toBe(0);
    expect(formatTrackEventTimeRange({ startTimestamp: start })).toBe('10:00');
    expect(formatTrackEventTimeRange({ startTimestamp: start, durationInSec: 120 })).toBe('10:00–10:02');
    expect(formatTrackEventDuration(Number.NaN)).toBe('');
  });
  it('rounds only the display duration and omits seconds', () => {
    expect(formatTrackEventDuration(59)).toBe('<1 min');
    expect(formatTrackEventDuration(90)).toBe('2 min');
    expect(formatTrackEventDuration(3599)).toBe('1 h');
    expect(formatTrackEventDuration(4680)).toBe('1 h 18 min');
  });
  it('rounds clock times to minute precision without changing the recorded duration', () => {
    const event = { startTimestamp: new Date(2026, 9, 12, 13, 4), endTimestamp: new Date(2026, 9, 12, 13, 48, 45) };
    expect(formatTrackEventTimeRange(event)).toBe('13:04–13:49');
    expect(trackEventDurationSeconds(event)).toBe(2685);
    const midnight = {
      startTimestamp: new Date(2026, 9, 12, 23, 59, 10),
      endTimestamp: new Date(2026, 9, 12, 23, 59, 45),
    };
    expect(formatTrackEventTimeRange(midnight)).toBe(`23:59–${formatTrackEventDate(new Date(2026, 9, 13))} · 00:00`);
  });
});
