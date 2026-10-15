import type { GHMetricPoint } from '../api/googleHealthApi';

export const SLEEP_SESSION_COLORS = ['#7267d8', '#aba3ec', '#51459f', '#c8c2f4'] as const;
export const SLEEP_DAY_MS = 24 * 60 * 60 * 1000;

type SleepPoint = GHMetricPoint & { time: Date; value: number };

export interface SleepDay {
  time: Date;
  sessions: SleepPoint[];
  total: number;
}

/** Group the existing session timestamps by local date without changing sleep durations. */
export function sleepDays(points: GHMetricPoint[] = []): SleepDay[] {
  const days = new Map<number, SleepDay>();
  for (const point of points) {
    if (
      !(point.time instanceof Date) ||
      !Number.isFinite(point.time.getTime()) ||
      point.value == null ||
      !Number.isFinite(point.value) ||
      point.value < 0
    ) {
      continue;
    }
    const time = new Date(point.time);
    time.setHours(0, 0, 0, 0);
    const key = time.getTime();
    let day = days.get(key);
    if (!day) {
      day = { time, sessions: [], total: 0 };
      days.set(key, day);
    }
    day.sessions.push(point as SleepPoint);
    day.total += point.value;
  }
  return [...days.values()]
    .sort((left, right) => left.time.getTime() - right.time.getTime())
    .map((day) => {
      day.sessions.sort((left, right) => right.value - left.value || left.time.getTime() - right.time.getTime());
      return day;
    });
}
