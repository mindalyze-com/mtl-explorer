import type { GpsTrackEvent } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import { toValidDateMs } from '@/utils/Utils';

type TrackEventKeySource = Pick<GpsTrackEvent, 'id' | 'startPointIndex' | 'startTimestamp'>;

export function trackEventTimeMs(value: GpsTrackEvent['startTimestamp']): number {
  return toValidDateMs(value) ?? 0;
}

export function trackEventKey(event: TrackEventKeySource): string | number {
  return event.id ?? `${event.startPointIndex ?? 'x'}-${trackEventTimeMs(event.startTimestamp)}`;
}

export function trackEventKeysEqual(
  first: string | number | null | undefined,
  second: string | number | null | undefined
): boolean {
  return first != null && second != null && String(first) === String(second);
}

export function trackEventTypeLabel(value: string | undefined): string {
  if (!value) return 'Event';
  return value
    .toLowerCase()
    .replaceAll('_', ' ')
    .replace(/\b\w/g, (character) => character.toUpperCase());
}

export function formatTrackEventDateTime(value: GpsTrackEvent['startTimestamp']): string {
  return formatTrackEventTime(value, { dateStyle: 'medium', timeStyle: 'short' });
}

export function formatTrackEventClockTime(value: GpsTrackEvent['startTimestamp']): string {
  return formatTrackEventTime(value, { timeStyle: 'short' });
}

function formatTrackEventTime(value: GpsTrackEvent['startTimestamp'], options: Intl.DateTimeFormatOptions): string {
  const timestampMs = trackEventTimeMs(value);
  return timestampMs > 0 ? new Intl.DateTimeFormat(undefined, options).format(new Date(timestampMs)) : '';
}
