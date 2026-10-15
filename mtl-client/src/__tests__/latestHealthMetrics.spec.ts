import { describe, expect, it, vi } from 'vitest';
import { loadLatestHealthMetrics } from '@/features/health/utils/latestHealthMetrics';
import type { GHTypeSummary } from '@/features/health/api/googleHealthApi';

const summary = (dataType: string, representation: string, latestRecord: Date): GHTypeSummary => ({
  dataType,
  representation,
  latestRecord,
  recordCount: 1,
});

describe('latest health values', () => {
  it('shares requests for one local day and retains all sleep sessions for its daily total', async () => {
    const day = new Date(2026, 8, 4);
    const sleep = {
      dataType: 'sleep',
      points: [
        { time: new Date(2026, 8, 4, 1), value: 7 },
        { time: new Date(2026, 8, 4, 14), value: 0.5 },
      ],
    };
    const steps = { dataType: 'steps', points: [{ time: day, value: 1000 }] };
    const read = vi.fn().mockResolvedValue([sleep, steps, { dataType: 'distance' }]);

    const values = await loadLatestHealthMetrics(
      [summary('sleep', 'RAW', new Date(2026, 8, 4, 14)), summary('steps', 'DAILY_ROLLUP', day)],
      read
    );

    expect(read).toHaveBeenCalledExactlyOnceWith(day, new Date(2026, 8, 5));
    expect(values).toEqual([steps, sleep]);
  });

  it('loads an old latest measurement independently and ignores unrelated values returned for its day', async () => {
    const oldDay = new Date(2020, 0, 2);
    const recentDay = new Date(2026, 8, 4);
    const read = vi
      .fn()
      .mockImplementation(async (start: Date) =>
        start.getTime() === oldDay.getTime()
          ? [{ dataType: 'weight', points: [{ time: oldDay, value: 70 }] }, { dataType: 'steps' }]
          : [{ dataType: 'steps', points: [{ time: recentDay, value: 1000 }] }, { dataType: 'weight' }]
      );
    const values = await loadLatestHealthMetrics(
      [summary('weight', 'RAW', oldDay), summary('steps', 'DAILY_ROLLUP', recentDay)],
      read
    );

    expect(read).toHaveBeenCalledTimes(2);
    expect(read).toHaveBeenCalledWith(oldDay, new Date(2020, 0, 3));
    expect(values.map((series) => [series.dataType, series.points?.[0]?.value])).toEqual([
      ['steps', 1000],
      ['weight', 70],
    ]);
  });

  it('uses chartable representations and the most recent heart rate source across a DST boundary', async () => {
    const day = new Date(2026, 2, 29);
    const read = vi.fn().mockResolvedValue([{ dataType: 'heart-rate' }, { dataType: 'steps' }]);
    await loadLatestHealthMetrics(
      [
        summary('steps', 'RAW', new Date(2026, 3, 1)),
        summary('steps', 'DAILY_ROLLUP', day),
        summary('heart-rate', 'HEART_RATE_SAMPLE', new Date(2026, 2, 29, 23)),
        summary('heart-rate', 'DAILY_ROLLUP', new Date(2026, 2, 28)),
      ],
      read
    );
    expect(read).toHaveBeenCalledExactlyOnceWith(day, new Date(2026, 2, 30));
  });

  it('does not invent a recent window for missing or invalid latest timestamps', async () => {
    const read = vi.fn();
    expect(
      await loadLatestHealthMetrics(
        [
          { dataType: 'steps', representation: 'DAILY_ROLLUP', recordCount: 1 },
          summary('sleep', 'RAW', new Date('invalid')),
          { ...summary('weight', 'RAW', new Date()), recordCount: 0 },
        ],
        read
      )
    ).toEqual([]);
    expect(read).not.toHaveBeenCalled();
  });
});
