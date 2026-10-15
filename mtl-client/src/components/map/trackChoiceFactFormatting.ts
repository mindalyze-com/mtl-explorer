import { formatDate, formatDistanceSmart, formatElevation, toValidDate } from '@/utils/Utils';

export type TrackChoiceFact = {
  key: 'date' | 'length' | 'ascent';
  icon: string;
  label: string;
  value: string;
};

export type TrackChoiceFactsInput = {
  recordedAt?: number;
  fallbackDate?: string;
  lengthMeters?: number;
  ascentMeters?: number;
};

/** Format canonical route facts at render time so locale/unit changes stay reactive. */
export function formatTrackChoiceFacts(input: TrackChoiceFactsInput): TrackChoiceFact[] {
  const facts: TrackChoiceFact[] = [];
  const date = input.recordedAt == null ? null : toValidDate(input.recordedAt);
  const dateText = date ? formatDate(date) : input.fallbackDate;
  if (dateText && dateText !== 'No date')
    facts.push({ key: 'date', icon: 'bi-calendar3', label: 'Date', value: dateText });
  if (validMeasurement(input.lengthMeters))
    facts.push({
      key: 'length',
      icon: 'bi-arrow-left-right',
      label: 'Distance',
      value: formatDistanceSmart(input.lengthMeters),
    });
  if (validMeasurement(input.ascentMeters))
    facts.push({
      key: 'ascent',
      icon: 'bi-graph-up-arrow',
      label: 'Ascent',
      value: formatElevation(input.ascentMeters),
    });
  return facts;
}

function validMeasurement(value: number | undefined): value is number {
  return value != null && Number.isFinite(value) && value >= 0;
}
