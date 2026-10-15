import type { GHRecordResponse, GHTypeSummary } from 'x8ing-mtl-api-typescript-fetch';

export function formatHealthDateTime(
  value?: Date,
  timeStyle: 'short' | 'long' = 'short',
  emptyLabel = 'Never'
): string {
  return value ? new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle }).format(value) : emptyLabel;
}

/** Calendar and civil values describe Google's local clock, independent of the browser's time zone. */
export function formatHealthRecordTime(
  record: Pick<GHRecordResponse, 'temporalKind' | 'civilDate' | 'civilTime' | 'recordTime'>
): string {
  if (record.temporalKind === 'CALENDAR_DATE') {
    return record.civilDate
      ? new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeZone: 'UTC' }).format(
          new Date(`${record.civilDate}T00:00:00Z`)
        )
      : '—';
  }
  if (record.temporalKind === 'CIVIL') {
    return record.civilTime
      ? new Intl.DateTimeFormat(undefined, {
          dateStyle: 'medium',
          timeStyle: 'medium',
          timeZone: 'UTC',
        }).format(new Date(`${record.civilTime}Z`))
      : '—';
  }
  return formatHealthDateTime(record.recordTime, 'long', '—');
}

export function healthRecordDateTimeValue(record: GHRecordResponse): string | undefined {
  if (record.temporalKind === 'CALENDAR_DATE') return record.civilDate;
  if (record.temporalKind === 'CIVIL') return record.civilTime;
  return record.recordTime?.toISOString();
}

export function healthRecordTimeLabel(record: GHRecordResponse): string {
  if (record.temporalKind === 'CALENDAR_DATE') return 'Calendar date';
  if (record.temporalKind === 'CIVIL') return 'Local time';
  return 'Exact time';
}

export function formatHealthSummaryRange(summary: GHTypeSummary): string {
  const { temporalKind } = summary;
  if (temporalKind === 'CALENDAR_DATE') {
    if (!summary.earliestCivilDate && !summary.latestCivilDate) return '—';
    const date = (civilDate?: string) => (civilDate ? formatHealthRecordTime({ temporalKind, civilDate }) : '…');
    return `${date(summary.earliestCivilDate)} – ${date(summary.latestCivilDate)}`;
  }
  if (temporalKind === 'CIVIL') {
    if (!summary.earliestCivilTime && !summary.latestCivilTime) return '—';
    const formatter = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeZone: 'UTC' });
    const time = (civilTime?: string) => (civilTime ? formatter.format(new Date(`${civilTime}Z`)) : '…');
    return `${time(summary.earliestCivilTime)} – ${time(summary.latestCivilTime)}`;
  }
  if (!summary.earliestRecord && !summary.latestRecord) return '—';
  const formatter = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' });
  return `${summary.earliestRecord ? formatter.format(summary.earliestRecord) : '…'} – ${summary.latestRecord ? formatter.format(summary.latestRecord) : '…'}`;
}
