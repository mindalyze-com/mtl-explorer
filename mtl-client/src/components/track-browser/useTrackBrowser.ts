import { computed, ref, type Ref } from 'vue';
import { formatDate, formatDateAndTime } from '@/utils/Utils';
import type {
  TrackBrowserSummary,
  TrackRowViewModel,
} from './trackBrowser.types';
import type { GpsTrack } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';

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
  const avgSpeedKmh = durationMillis > 0 ? (distanceMeters / 1000) / (durationMillis / 3600000) : null;

  const trackName = String(track.trackName || '').trim();
  const trackDescription = String(track.trackDescription || '').trim();

  return {
    ...track,
    displayName: trackName || trackDescription || `Track ${track.id}`,
    durationMillis,
    avgSpeedKmh,
    startDateMs:  startDate?.getTime()  ?? -1,
    createDateMs: createDate?.getTime() ?? -1,
  };
}

export function useTrackBrowser(tracks: Ref<GpsTrack[]>) {
  const query = ref('');

  const normalizedTracks = computed(() => tracks.value.map(toTrackRow));

  const filteredTracks = computed(() => {
    const search = query.value.trim().toLocaleLowerCase();
    if (!search) return normalizedTracks.value;
    return normalizedTracks.value.filter((row) => {
      const haystack = [
        row.displayName,
        row.trackDescription,
        row.activityType,
        row.creator,
        row.indexedFile?.name,
        String(row.id),
      ]
        .join(' ')
        .toLocaleLowerCase();
      return haystack.includes(search);
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
