import type { GpsTrack } from 'x8ing-mtl-api-typescript-fetch';

export interface TrackBrowserSummary {
  count: number;
  totalDistanceMeters: number;
  totalDurationMillis: number;
  newestTrackLabel: string;
  newestTrackDateLabel: string;
  dateRangeLabel: string;
}

/**
 * GpsTrack enriched with a handful of derived display fields.
 * All raw track data is accessed directly via GpsTrack fields — no duplication.
 */
export interface TrackRowViewModel extends GpsTrack {
  /** trackName || trackDescription || 'Track N' */
  displayName: string;
  /** Motion-based duration in ms (falls back to endDate − startDate) */
  durationMillis: number;
  avgSpeedKmh: number | null;
  /** startDate.getTime() pre-computed for fast sorting; -1 when absent */
  startDateMs: number;
  /** createDate.getTime() pre-computed for fast sorting; -1 when absent */
  createDateMs: number;
}

/** @deprecated Use TrackRowViewModel */
export type TrackBrowserTrackRow = TrackRowViewModel;

/** Named preset / quick-view identifier for the track browser. */
export type TrackBrowserPreset = string;

/** SelectButton-compatible option shape used by TrackBrowserQuickViews. */
export interface TrackBrowserOption<T = TrackBrowserPreset> {
  label: string;
  value: T;
}
