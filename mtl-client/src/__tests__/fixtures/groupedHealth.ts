import type { GHMetricSeries, GHGroupedMetrics, GHGroupedSeries } from '@/features/health/api/googleHealthApi';
import { formatCalendarDate } from '@/features/health/utils/healthCalendarRange';

/** Synthetic chart response for component tests; production aggregation lives on the server. */
export function groupedSeries(series: GHMetricSeries): GHGroupedSeries {
  const family = series.dataType === 'sleep' ? 'SLEEP' : series.category === 'ACTIVITY' ? 'ACTIVITY' : 'MEASUREMENT';
  const days = new Map<string, NonNullable<GHMetricSeries['points']>>();
  for (const point of series.points ?? []) {
    if (!point.time) continue;
    const date = formatCalendarDate(point.time);
    days.set(date, [...(days.get(date) ?? []), point]);
  }
  return {
    ...series,
    family,
    grouping: 'DAY',
    detailedHeartRate: series.dataType === 'heart-rate',
    detailedPoints: series.dataType === 'heart-rate' ? series.points : [],
    buckets: [...days.entries()]
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([date, points]) => {
        const end = new Date(`${date}T12:00:00`);
        end.setDate(end.getDate() + 1);
        const total = points.reduce((sum, point) => sum + (point.value ?? 0), 0);
        return {
          startDate: date,
          endDate: formatCalendarDate(end),
          periodStartDate: date,
          periodEndDate: formatCalendarDate(end),
          total: family === 'MEASUREMENT' ? undefined : total,
          average: family === 'MEASUREMENT' ? total / points.length : total,
          minimum: Math.min(...points.map((point) => point.minimum ?? point.value ?? 0)),
          maximum: Math.max(...points.map((point) => point.maximum ?? point.value ?? 0)),
          minimumDate: date,
          maximumDate: date,
          availableDays: 1,
          selectedDays: 1,
          inProgress: false,
          clipped: false,
          sessions: family === 'SLEEP' ? points : [],
        };
      }),
  };
}
export function groupedResponse(series: GHMetricSeries[]): GHGroupedMetrics {
  return {
    startDate: '2026-08-01',
    endDate: '2026-08-30',
    requestedGrouping: 'AUTO',
    resolvedGrouping: 'DAY',
    timeZone: 'UTC',
    series: series.map(groupedSeries),
  };
}
