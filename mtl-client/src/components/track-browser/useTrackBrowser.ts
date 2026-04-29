import { computed, ref, type Ref } from 'vue';
import { formatDate, formatDateAndTime, formatDistanceSmart, formatDurationSmart, formatNumber } from '@/utils/Utils';
import type {
  TrackBrowserSummary,
  TrackRowViewModel,
} from './trackBrowser.types';
import type { GpsTrack } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';

const SEARCHABLE_TEXT_SEPARATOR = ' ';
const ISO_DATE_LENGTH = 10;
const METERS_PER_KILOMETER = 1000;
const MILLIS_PER_SECOND = 1000;
const MILLIS_PER_MINUTE = 60000;
const MILLIS_PER_HOUR = 3600000;
const PERCENT_MULTIPLIER = 100;
const SPEED_FRACTION_DIGITS = 1;
const ENERGY_FRACTION_DIGITS = 0;
const EXPLORATION_FRACTION_DIGITS = 1;

function cleanText(value: unknown): string {
  return String(value || '').trim();
}

function normalizeSearchText(values: unknown[]): string {
  return values
    .map(cleanText)
    .filter(Boolean)
    .join(SEARCHABLE_TEXT_SEPARATOR)
    .toLocaleLowerCase();
}

function toFiniteNumber(value: unknown): number | null {
  const numberValue = Number(value);
  return Number.isFinite(numberValue) ? numberValue : null;
}

function dateSearchValues(value: Date | null): string[] {
  if (!value) return [];
  return [
    formatDate(value),
    formatDateAndTime(value),
    value.toISOString(),
    value.toISOString().slice(0, ISO_DATE_LENGTH),
  ];
}

function distanceSearchValues(meters: number): string[] {
  return [
    String(meters),
    formatDistanceSmart(meters),
    `${meters} m`,
    String(meters / METERS_PER_KILOMETER),
    `${meters / METERS_PER_KILOMETER} km`,
  ];
}

function durationSearchValues(millis: number): string[] {
  const totalMinutes = Math.floor(millis / MILLIS_PER_MINUTE);
  const totalHours = Math.floor(millis / MILLIS_PER_HOUR);
  return [
    String(millis),
    formatDurationSmart(millis),
    `${Math.floor(millis / MILLIS_PER_SECOND)} s`,
    `${totalMinutes} min`,
    `${totalHours} h`,
  ];
}

function formattedMetricSearchValues(value: unknown, digits: number, unit = ''): string[] {
  const numberValue = toFiniteNumber(value);
  if (numberValue == null) return [];
  const formatted = formatNumber(numberValue, digits);
  return [
    String(numberValue),
    formatted,
    unit ? `${formatted} ${unit}` : '',
  ];
}

function buildTrackSearchText(row: Omit<TrackRowViewModel, 'searchText'>): string {
  const startDate = row.startDate instanceof Date ? row.startDate : null;
  const createDate = row.createDate instanceof Date ? row.createDate : null;
  const distanceMeters = toFiniteNumber(row.trackLengthInMeter) ?? 0;
  const energyWh = formattedMetricSearchValues(row.energyNetTotalWh, ENERGY_FRACTION_DIGITS, 'Wh');
  const explorationScore = toFiniteNumber(row.explorationScore);

  return normalizeSearchText([
    row.displayName,
    row.trackName,
    row.metaName,
    row.trackDescription,
    row.metaDescription,
    row.activityType,
    row.creator,
    row.indexedFile?.name,
    row.indexedFile?.fullPath,
    row.indexedFile?.basePath,
    row.indexedFile?.path,
    String(row.id),
    ...dateSearchValues(startDate),
    ...dateSearchValues(createDate),
    ...distanceSearchValues(distanceMeters),
    ...durationSearchValues(row.durationMillis),
    ...formattedMetricSearchValues(row.avgSpeedKmh, SPEED_FRACTION_DIGITS),
    ...energyWh,
    row.explorationStatus,
    explorationScore == null ? '' : formatNumber(explorationScore * PERCENT_MULTIPLIER, EXPLORATION_FRACTION_DIGITS),
    explorationScore == null ? '' : `${formatNumber(explorationScore * PERCENT_MULTIPLIER, EXPLORATION_FRACTION_DIGITS)}%`,
  ]);
}

function toTrackRow(track: GpsTrack): TrackRowViewModel {
  // ServiceHelper already coerces date strings → Date objects during bulk load,
  // so we can use them directly here.
  const startDate = track.startDate instanceof Date ? track.startDate : null;
  const endDate   = track.endDate   instanceof Date ? track.endDate   : null;
  const createDate = track.createDate instanceof Date ? track.createDate : null;

  const motionSecs = track.trackDurationInMotionSecs != null ? Number(track.trackDurationInMotionSecs) : null;
  const elapsedMillis = startDate && endDate ? Math.max(0, endDate.getTime() - startDate.getTime()) : 0;
  const durationMillis = motionSecs != null ? motionSecs * 1000 : elapsedMillis;

  const distanceMeters = Number(track.trackLengthInMeter || 0);
  const avgSpeedKmh = durationMillis > 0 ? (distanceMeters / METERS_PER_KILOMETER) / (durationMillis / MILLIS_PER_HOUR) : null;

  const trackName = cleanText(track.trackName);
  const metaName = cleanText(track.metaName);
  const trackDescription = cleanText(track.trackDescription);
  const metaDescription = cleanText(track.metaDescription);

  const row = {
    ...track,
    displayName: trackName || metaName || trackDescription || metaDescription || `Track ${track.id}`,
    durationMillis,
    avgSpeedKmh,
    startDateMs:  startDate?.getTime()  ?? -1,
    createDateMs: createDate?.getTime() ?? -1,
  };
  return {
    ...row,
    searchText: buildTrackSearchText(row),
  };
}

export function useTrackBrowser(tracks: Ref<GpsTrack[]>) {
  const query = ref('');

  const normalizedTracks = computed(() => tracks.value.map(toTrackRow));

  const filteredTracks = computed(() => {
    const search = query.value.trim().toLocaleLowerCase();
    if (!search) return normalizedTracks.value;
    return normalizedTracks.value.filter((row) => {
      return row.searchText.includes(search);
    });
  });

  // Default: newest first — DataTable columns allow the user to re-sort from here
  const rows = computed(() =>
    [...filteredTracks.value].sort((a, b) => b.startDateMs - a.startDateMs)
  );

  const summary = computed<TrackBrowserSummary>(() => {
    const items = filteredTracks.value;
    const count = items.length;
    const totalDistanceMeters = items.reduce((sum, row) => sum + Number(row.trackLengthInMeter || 0), 0);
    const totalDurationMillis = items.reduce((sum, row) => sum + row.durationMillis, 0);
    const newest = [...items].sort((a, b) => b.startDateMs - a.startDateMs)[0];
    const datedItems = items.filter((row) => row.startDate);
    const minDate = [...datedItems].sort((a, b) => a.startDateMs - b.startDateMs)[0]?.startDate ?? null;
    const maxDate = newest?.startDate ?? null;
    return {
      count,
      totalDistanceMeters,
      totalDurationMillis,
      newestTrackLabel: newest?.displayName || 'No tracks',
      newestTrackDateLabel: newest?.startDate instanceof Date ? formatDateAndTime(newest.startDate) : '',
      dateRangeLabel: minDate && maxDate
        ? `${formatDate(minDate instanceof Date ? minDate : new Date(minDate))} – ${formatDate(maxDate instanceof Date ? maxDate : new Date(maxDate))}`
        : 'No date range',
    };
  });

  const totalCount = computed(() => normalizedTracks.value.length);

  return { query, rows, summary, totalCount };
}
