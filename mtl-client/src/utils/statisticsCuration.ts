import type { GpsTrack } from 'x8ing-mtl-api-typescript-fetch';

export const HIGHLIGHT_EXCLUSIONS_TRACK_BROWSER_QUERY = 'excluded highlights';

type CurationTrack = Pick<
  GpsTrack,
  | 'highlightExclusionReason'
  | 'statisticsExclusionReason'
  | 'reviewStatus'
  | 'reviewExcluded'
  | 'reviewMapExcluded'
  | 'timingUnreliable'
  | 'timingEstimated'
>;

export type CurationBadge = {
  key: 'highlight' | 'statistics' | 'review' | 'timing' | 'map';
  label: string;
  title: string;
};

const REASON_LABELS: Record<string, string> = {
  GPS_NOISE: 'GPS noise',
  WRONG_ACTIVITY: 'Wrong activity',
  IMPORT_ARTIFACT: 'Import artifact',
  OTHER: 'Other',
};

export function formatCurationReason(value: unknown): string {
  const key = String(value || '');
  return REASON_LABELS[key] ?? key;
}

export function curationBadges(track: CurationTrack): CurationBadge[] {
  const badges: CurationBadge[] = [];
  if (track.reviewStatus === 'NEEDS_REVIEW' || track.reviewExcluded) {
    badges.push({
      key: 'review',
      label: track.reviewStatus === 'NEEDS_REVIEW' ? 'Needs review' : 'Source excluded',
      title: track.reviewExcluded
        ? 'Excluded from statistics and highlights in Review.'
        : 'Review source findings and choose map and statistics use.',
    });
  }
  if (track.reviewMapExcluded)
    badges.push({
      key: 'map',
      label: 'Map excluded',
      title: 'Hidden from the main map. Still available in Review and on inspection maps.',
    });
  if (track.timingUnreliable || track.timingEstimated) {
    badges.push({
      key: 'timing',
      label: track.timingEstimated ? 'Estimated timing' : 'Timing unavailable',
      title: 'Time, speed and energy measurements are unavailable. Original observations remain available for review.',
    });
  }
  if (track.highlightExclusionReason) {
    const reason = formatCurationReason(track.highlightExclusionReason);
    badges.push({
      key: 'highlight',
      label: `Highlights: ${reason}`,
      title: `Excluded from highlights: ${reason}`,
    });
  }
  if (track.statisticsExclusionReason) {
    const reason = formatCurationReason(track.statisticsExclusionReason);
    badges.push({
      key: 'statistics',
      label: `Statistics: ${reason}`,
      title: `Excluded from statistics and highlights: ${reason}`,
    });
  }
  return badges;
}

export function curationSearchValues(track: CurationTrack): string[] {
  const values: string[] = [];
  for (const badge of curationBadges(track)) values.push(badge.label);
  if (track.reviewMapExcluded) values.push('excluded map', 'map exclusion');
  if (track.reviewExcluded) values.push('source review', 'excluded statistics', 'excluded highlights');
  const highlightReason = track.highlightExclusionReason;
  const statisticsReason = track.statisticsExclusionReason;

  if (highlightReason || statisticsReason) {
    values.push(
      'curation',
      'statistics curation',
      'excluded',
      'excluded highlights',
      'highlights excluded',
      'highlight exclusion'
    );
  }

  if (highlightReason) {
    const reason = formatCurationReason(highlightReason);
    values.push('highlight only', `highlights ${reason}`, `highlight ${reason}`, reason, String(highlightReason));
  }

  if (statisticsReason) {
    const reason = formatCurationReason(statisticsReason);
    values.push(
      'excluded statistics',
      'statistics excluded',
      'statistics exclusion',
      `statistics ${reason}`,
      reason,
      String(statisticsReason)
    );
  }

  return values;
}
