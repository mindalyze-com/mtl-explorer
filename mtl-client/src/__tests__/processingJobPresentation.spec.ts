import { describe, expect, it } from 'vitest';
import { JobSummaryDtoFromJSON } from 'x8ing-mtl-api-typescript-fetch';
import {
  attentionCount,
  blockerTarget,
  processingLabel,
  processingOverview,
  processingState,
  relativeTime,
  rootWaits,
  waitLeadIn,
} from '@/utils/processingJobPresentation';
import type { JobSummary } from '@/utils/serverAdminApi';

function job(value: object): JobSummary {
  return JobSummaryDtoFromJSON({
    job: 'exploration',
    pending: 120,
    done: 880,
    failed: 0,
    unavailable: 0,
    total: 1000,
    progressPercent: 88,
    ...value,
  }) as JobSummary;
}
describe('processing job presentation', () => {
  it('uses the server state even when work remains', () => {
    for (const [state, label] of [
      ['WAITING', 'Waiting'],
      ['QUEUED', 'Queued'],
      ['RETRYING', 'Retrying'],
    ] as const) {
      expect(processingState(job({ state }))).toBe(state.toLowerCase());
      expect(processingLabel(job({ state }))).toBe(label);
    }
  });
  it('keeps attention independent of a running state and supports older servers', () => {
    const report = job({ state: 'RUNNING', attentionRequired: 2 });
    expect(processingState(report)).toBe('running');
    expect(attentionCount(report)).toBe(2);
    expect(processingState(job({}))).toBe('running');
    expect(processingLabel(job({ pending: 0, failed: 2 }))).toBe('Needs attention');
  });
  it('decodes blocker references, work counts and scheduled dates through the generated client', () => {
    const report = job({
      state: 'QUEUED',
      work: { ready: 40, waiting: 120, retrying: 0 },
      nextRunAt: '2026-10-14T12:30:00Z',
      blockedBy: [{ kind: 'LOCK', id: 'track-processing', heldBy: 'duplicate', resolution: 'AUTOMATIC' }],
    });
    expect(report.work?.waiting).toBe(120);
    expect(report.nextRunAt).toBeInstanceOf(Date);
    expect(blockerTarget(report.blockedBy![0]!)).toBe('JOB:duplicate');
  });
  it('shows finished work that needs the user as attention, not as complete', () => {
    const report = job({ state: 'COMPLETE', pending: 0, attentionRequired: 3 });
    expect(processingState(report)).toBe('attention');
    expect(processingLabel(report)).toBe('Needs attention');
    expect(processingState(job({ state: 'COMPLETE', pending: 0, attentionRequired: 0 }))).toBe('complete');
  });
  it('reduces waits to their root cause', () => {
    const gps = { kind: 'INDEX', id: 'GPS', scope: 'WHOLE_JOB', resolution: 'AUTOMATIC' };
    const duplicateWhole = { kind: 'JOB', id: 'duplicate', scope: 'WHOLE_JOB', resolution: 'AUTOMATIC' };
    const duplicateItems = { ...duplicateWhole, scope: 'ITEMS' };
    const duplicate = job({ job: 'duplicate', state: 'WAITING', blockedBy: [gps] });
    const jobs = new Map([['duplicate', duplicate]]);
    // A whole-job wait makes item waits irrelevant.
    expect(rootWaits(job({ job: 'activityType', blockedBy: [gps, duplicateItems] }), jobs).map(blockerTarget)).toEqual([
      'INDEX:GPS',
    ]);
    // Duplicate detection waits for the same import, so the import is the root cause.
    expect(rootWaits(job({ blockedBy: [gps, duplicateWhole] }), jobs).map(blockerTarget)).toEqual(['INDEX:GPS']);
    // Without the import wait, duplicate detection is the cause.
    expect(rootWaits(job({ blockedBy: [duplicateWhole] }), new Map()).map(blockerTarget)).toEqual(['JOB:duplicate']);
  });
  it('summarizes a prerequisite shared by several rows once', () => {
    const gps = { kind: 'INDEX', id: 'GPS', scope: 'WHOLE_JOB', resolution: 'AUTOMATIC' };
    const media = { kind: 'INDEX', id: 'MEDIA', scope: 'WHOLE_JOB', resolution: 'AUTOMATIC' };
    const overview = processingOverview([
      job({ job: 'duplicate', state: 'WAITING', blockedBy: [gps] }),
      job({ job: 'exploration', state: 'WAITING', blockedBy: [gps] }),
      job({ job: 'mediaCorrelation', state: 'WAITING', blockedBy: [gps, media] }),
      job({ job: 'energy', state: 'COMPLETE', pending: 0 }),
    ]);
    expect(overview.shared.map(blockerTarget)).toEqual(['INDEX:GPS']);
    expect(overview.sharedJobs).toEqual(['duplicate', 'exploration', 'mediaCorrelation']);
    expect(overview.rows.get('mediaCorrelation')?.after.map(blockerTarget)).toEqual(['INDEX:GPS']);
    // A wait only one row has stays explained in that row.
    expect(overview.rows.get('mediaCorrelation')?.waits.map(blockerTarget)).toEqual(['INDEX:MEDIA']);
    expect(overview.dependents.get('INDEX:GPS')).toEqual(['duplicate', 'exploration', 'mediaCorrelation']);
    expect(overview.dependents.get('INDEX:MEDIA')).toEqual(['mediaCorrelation']);
  });
  it('describes future times relative to now and leaves due times to the caller', () => {
    const now = Date.parse('2026-10-14T12:00:00Z');
    expect(relativeTime(new Date(now + 40_000), now)).toBe('in 40 seconds');
    expect(relativeTime(new Date(now + 120_000), now)).toBe('in 2 minutes');
    expect(relativeTime(new Date(now - 5_000), now)).toBe('');
    expect(relativeTime(undefined, now)).toBe('');
  });
  it.each([
    ['tracks', 1, '1 track waits for'],
    ['tracks', 2, '2 tracks wait for'],
    ['days', 1, '1 day waits for'],
    ['work items', 1, '1 work item waits for'],
  ])('uses the right unit and verb for %s with %i waiting', (unit, count, expected) => {
    const report = job({
      unit,
      work: { ready: 0, waiting: count, retrying: 0 },
      blockedBy: [{ kind: 'JOB', id: 'duplicate', scope: 'ITEMS', resolution: 'AUTOMATIC' }],
    });
    expect(waitLeadIn(report.blockedBy![0]!, report, 1)).toBe(expected);
  });
});
