import type { GpsTrackEvent } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import { toValidDateMs } from '@/utils/Utils';
import { getFormatLocale } from '@/composables/useLocale';

type TrackEventKeySource = Pick<GpsTrackEvent, 'id' | 'startPointIndex' | 'startTimestamp'>;
type TrackEventInterval = Pick<GpsTrackEvent, 'startTimestamp' | 'endTimestamp' | 'durationInSec'>;

const MILLISECONDS_PER_SECOND = 1000;
const SECONDS_PER_MINUTE = 60;
const MINUTES_PER_HOUR = 60;
const MILLISECONDS_PER_MINUTE = MILLISECONDS_PER_SECOND * SECONDS_PER_MINUTE;
const RECORDING_GAP_DESCRIPTION_PREFIX = 'Low-displacement GPS recording gap';

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
  const timestampMs = trackEventTimeMs(value);
  return timestampMs > 0
    ? formatTrackEventTime(new Date(roundedDisplayTime(timestampMs)), {
        hour: '2-digit',
        minute: '2-digit',
        hourCycle: 'h23',
      })
    : '';
}

export function formatTrackEventDate(value: GpsTrackEvent['startTimestamp']): string {
  const timestampMs = trackEventTimeMs(value);
  return timestampMs > 0
    ? formatTrackEventTime(new Date(roundedDisplayTime(timestampMs)), { dateStyle: 'medium' })
    : '';
}

function roundedDisplayTime(timestampMs: number): number {
  return Math.round(timestampMs / MILLISECONDS_PER_MINUTE) * MILLISECONDS_PER_MINUTE;
}

export function trackEventEndTimeMs(event: TrackEventInterval): number {
  const startMs = trackEventTimeMs(event.startTimestamp);
  const endMs = trackEventTimeMs(event.endTimestamp);
  if (endMs > 0) return startMs > 0 && endMs < startMs ? 0 : endMs;
  const duration = event.durationInSec;
  return startMs > 0 && duration != null && Number.isFinite(duration) && duration > 0
    ? startMs + duration * MILLISECONDS_PER_SECOND
    : 0;
}

export function trackEventDurationSeconds(event: TrackEventInterval): number {
  if (event.durationInSec != null && Number.isFinite(event.durationInSec) && event.durationInSec > 0) {
    return event.durationInSec;
  }
  const startMs = trackEventTimeMs(event.startTimestamp);
  const endMs = trackEventTimeMs(event.endTimestamp);
  return startMs > 0 && endMs > startMs ? (endMs - startMs) / MILLISECONDS_PER_SECOND : 0;
}

export function formatTrackEventDuration(seconds: number): string {
  if (!Number.isFinite(seconds) || seconds <= 0) return '';
  if (seconds < SECONDS_PER_MINUTE) return '<1 min';
  const roundedMinutes = Math.round(seconds / SECONDS_PER_MINUTE);
  const hours = Math.floor(roundedMinutes / MINUTES_PER_HOUR);
  const minutes = roundedMinutes % MINUTES_PER_HOUR;
  if (hours === 0) return `${minutes} min`;
  return minutes > 0 ? `${hours} h ${minutes} min` : `${hours} h`;
}

export function formatTrackEventTimeRange(event: TrackEventInterval, includeDate = false): string {
  const startMs = trackEventTimeMs(event.startTimestamp);
  const endMs = trackEventEndTimeMs(event);
  const withDate = (value: Date): string => `${formatTrackEventDate(value)} · ${formatTrackEventClockTime(value)}`;
  if (startMs <= 0)
    return endMs > 0 ? (includeDate ? withDate(new Date(endMs)) : formatTrackEventClockTime(new Date(endMs))) : '';
  const start = new Date(roundedDisplayTime(startMs));
  const startLabel = includeDate ? withDate(start) : formatTrackEventClockTime(start);
  if (endMs <= startMs) return startLabel;
  const end = new Date(roundedDisplayTime(endMs));
  const sameDay =
    start.getFullYear() === end.getFullYear() &&
    start.getMonth() === end.getMonth() &&
    start.getDate() === end.getDate();
  return `${startLabel}–${sameDay ? formatTrackEventClockTime(end) : withDate(end)}`;
}

export function isTrackEventRecordingGap(event: GpsTrackEvent): boolean {
  return (
    event.eventType === 'GPS_GAP' ||
    event.detectionEvidence === 'RECORDING_GAP' ||
    event.description?.trim().startsWith(RECORDING_GAP_DESCRIPTION_PREFIX) === true
  );
}

export function isTrackEventBreak(event: GpsTrackEvent): boolean {
  return (event.eventType === 'STOP' || event.eventType === 'PHOTO_STOP') && !isTrackEventRecordingGap(event);
}

export function trackEventTitle(event: GpsTrackEvent, breakNumber?: number): string {
  const customLabel = event.label?.trim();
  if (event.source === 'USER' && customLabel) return customLabel;
  if (isTrackEventRecordingGap(event)) return 'Recording gap';
  if (isTrackEventBreak(event)) {
    const label = event.eventType === 'PHOTO_STOP' ? 'Photo break' : 'Break';
    return breakNumber == null ? label : `${label} ${breakNumber}`;
  }
  return customLabel || trackEventTypeLabel(event.eventType);
}

function formatTrackEventTime(value: GpsTrackEvent['startTimestamp'], options: Intl.DateTimeFormatOptions): string {
  const timestampMs = trackEventTimeMs(value);
  return timestampMs > 0 ? new Intl.DateTimeFormat(getFormatLocale(), options).format(new Date(timestampMs)) : '';
}
