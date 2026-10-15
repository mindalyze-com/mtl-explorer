import type { GHMetricSeries, GHTypeSummary } from '../api/googleHealthApi';

const SUMMARY_METRICS = [
  { dataType: 'steps', representations: ['DAILY_ROLLUP'] },
  { dataType: 'sleep', representations: ['RAW'] },
  { dataType: 'daily-resting-heart-rate', representations: ['RAW'] },
  { dataType: 'heart-rate', representations: ['HEART_RATE_SAMPLE', 'DAILY_ROLLUP'] },
  { dataType: 'weight', representations: ['RAW'] },
] as const;

type ReadMetrics = (start: Date, end: Date) => Promise<GHMetricSeries[]>;

/** Read only the local days containing the latest summary values, sharing requests for equal dates. */
export async function loadLatestHealthMetrics(
  summaries: GHTypeSummary[],
  readMetrics: ReadMetrics
): Promise<GHMetricSeries[]> {
  const days = new Map<number, { start: Date; end: Date; dataTypes: Set<string> }>();
  for (const metric of SUMMARY_METRICS) {
    const representations: readonly string[] = metric.representations;
    const latestTimes = summaries
      .filter(
        (summary) =>
          summary.dataType === metric.dataType &&
          representations.includes(summary.representation ?? '') &&
          (summary.recordCount ?? 0) > 0
      )
      .map((summary) => summary.latestRecord?.getTime())
      .filter((time): time is number => time != null && Number.isFinite(time));
    if (!latestTimes.length) continue;

    const start = new Date(Math.max(...latestTimes));
    start.setHours(0, 0, 0, 0);
    let day = days.get(start.getTime());
    if (!day) {
      const end = new Date(start);
      end.setDate(end.getDate() + 1);
      day = { start, end, dataTypes: new Set() };
      days.set(start.getTime(), day);
    }
    day.dataTypes.add(metric.dataType);
  }

  const results = (
    await Promise.all(
      [...days.values()].map(async ({ start, end, dataTypes }) =>
        (await readMetrics(start, end)).filter((series) => dataTypes.has(series.dataType ?? ''))
      )
    )
  ).flat();
  return SUMMARY_METRICS.flatMap((metric) => results.filter((series) => series.dataType === metric.dataType));
}
