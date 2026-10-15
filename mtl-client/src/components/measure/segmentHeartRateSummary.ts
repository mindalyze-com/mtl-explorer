import type { SegmentHeartRateSummary } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import { formatNumber } from '@/utils/Utils';

export function formatSegmentHeartRate(summary?: SegmentHeartRateSummary): string {
  if (summary?.averageBpm == null) return '—';
  const value = `${formatNumber(summary.averageBpm, 0)} bpm`;
  return (summary.coveragePercent ?? 0) < 100
    ? `${value} · ${formatNumber(summary.coveragePercent ?? 0, 1)}% coverage`
    : value;
}

export const SEGMENT_HEART_RATE_DESCRIPTION =
  'Average heart rate over the whole segment, including stops. GPS gaps and missing heart rate are left out.';
