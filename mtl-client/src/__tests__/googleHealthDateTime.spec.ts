import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  formatHealthDateTime,
  formatHealthRecordTime,
  formatHealthSummaryRange,
  healthRecordDateTimeValue,
  healthRecordTimeLabel,
} from '@/features/health/utils/healthDateTime';

describe('Google Health date-time formatting', () => {
  afterEach(() => vi.unstubAllEnvs());
  it('preserves short status timestamps, exact record timestamps, and empty labels', () => {
    const date = new Date('2026-09-03T12:34:56Z');
    for (const timeStyle of ['short', 'long'] as const) {
      expect(formatHealthDateTime(date, timeStyle)).toBe(
        new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle }).format(date)
      );
    }
    expect(formatHealthDateTime()).toBe('Never');
    expect(formatHealthDateTime(undefined, 'short', 'never')).toBe('never');
  });

  it.each(['America/Los_Angeles', 'Europe/Zurich', 'Pacific/Kiritimati'])(
    'preserves provider calendar dates and civil clocks in %s',
    (timeZone) => {
      vi.stubEnv('TZ', timeZone);
      const dateRecord = {
        temporalKind: 'CALENDAR_DATE' as const,
        civilDate: '2026-08-02',
        recordTime: new Date('2026-08-02T00:00:00Z'),
      };
      const civilRecord = {
        temporalKind: 'CIVIL' as const,
        civilTime: '2026-08-02T00:30:00.123456',
        recordTime: new Date('2026-08-02T00:30:00Z'),
      };
      expect(formatHealthRecordTime(dateRecord)).toBe(
        new Intl.DateTimeFormat(undefined, {
          dateStyle: 'medium',
          timeZone: 'UTC',
        }).format(new Date('2026-08-02T00:00:00Z'))
      );
      expect(formatHealthRecordTime(civilRecord)).toBe(
        new Intl.DateTimeFormat(undefined, {
          dateStyle: 'medium',
          timeStyle: 'medium',
          timeZone: 'UTC',
        }).format(new Date('2026-08-02T00:30:00Z'))
      );
      expect(healthRecordDateTimeValue(dateRecord)).toBe('2026-08-02');
      expect(healthRecordDateTimeValue(civilRecord)).toBe('2026-08-02T00:30:00.123456');
      expect(healthRecordTimeLabel(dateRecord)).toBe('Calendar date');
      expect(
        formatHealthSummaryRange({
          temporalKind: 'CALENDAR_DATE',
          earliestCivilDate: '2026-08-02',
          latestCivilDate: '2026-08-02',
        })
      ).toBe(`${formatHealthRecordTime(dateRecord)} – ${formatHealthRecordTime(dateRecord)}`);
      expect(healthRecordTimeLabel(civilRecord)).toBe('Local time');
      const physicalRecord = { temporalKind: 'PHYSICAL' as const, recordTime: dateRecord.recordTime };
      expect(formatHealthRecordTime(physicalRecord)).toBe(formatHealthDateTime(dateRecord.recordTime, 'long'));
      expect(healthRecordDateTimeValue(physicalRecord)).toBe('2026-08-02T00:00:00.000Z');
      expect(healthRecordTimeLabel(physicalRecord)).toBe('Exact time');
    }
  );
});
