import { formatLocaleNumber } from '@/utils/Utils';
import type { JobSummary } from '@/utils/serverAdminApi';
import { GOOGLE_HEALTH_FEATURE_LABEL } from '@/features/health/utils/googleHealthPresentation';

export type ProcessingRowState =
  'running' | 'queued' | 'retrying' | 'waiting' | 'ready' | 'complete' | 'attention' | 'disabled' | 'unavailable';
export type ProcessingBlocker = NonNullable<JobSummary['blockedBy']>[number];
const STATE_LABELS: Record<string, string> = {
  RUNNING: 'Running',
  QUEUED: 'Queued',
  RETRYING: 'Retrying',
  WAITING: 'Waiting',
  COMPLETE: 'Complete',
  DISABLED: 'Off',
};
export function processingState(job: JobSummary): ProcessingRowState {
  // Finished work can still need the user; that is the state worth showing.
  if (job.state === 'COMPLETE') return attentionCount(job) > 0 ? 'attention' : 'complete';
  if (job.state) return job.state.toLowerCase() as ProcessingRowState;
  if (job.pending > 0) return 'running';
  if (attentionCount(job) > 0) return 'attention';
  return job.unavailable ? 'complete' : 'ready';
}
export function processingLabel(job: JobSummary): string {
  if (job.state === 'COMPLETE' && attentionCount(job) > 0) return 'Needs attention';
  if (job.state) return STATE_LABELS[job.state] ?? 'Unavailable';
  return job.pending > 0
    ? 'Running'
    : attentionCount(job) > 0
      ? 'Needs attention'
      : job.unavailable
        ? 'Complete'
        : 'Ready';
}
export function attentionCount(job: JobSummary): number {
  return job.attentionRequired ?? (job.failed ?? 0) + (job.job === 'activityType' ? 0 : (job.unavailable ?? 0));
}
export function processingIcon(state: ProcessingRowState): string {
  if (state === 'running') return 'bi bi-arrow-repeat';
  if (state === 'queued') return 'bi bi-hourglass-split';
  if (state === 'retrying') return 'bi bi-arrow-clockwise';
  if (state === 'ready' || state === 'complete') return 'bi bi-check2';
  if (state === 'attention' || state === 'unavailable') return 'bi bi-exclamation-triangle';
  if (state === 'disabled') return 'bi bi-dash-circle';
  return 'bi bi-clock';
}
export function blockerTarget(blocker: ProcessingBlocker): string | null {
  if (blocker.kind === 'INDEX' || blocker.kind === 'JOB') return `${blocker.kind}:${blocker.id}`;
  if (blocker.kind === 'LOCK' && blocker.heldBy) return `JOB:${blocker.heldBy}`;
  return null;
}

/** Rows waiting for the same prerequisite share one explanation above the list. */
const SHARED_WAIT_MIN_ROWS = 2;

function isUserAction(blocker: ProcessingBlocker): boolean {
  return blocker.kind === 'USER' || blocker.resolution === 'USER_ACTION';
}
function isAutomaticWait(blocker: ProcessingBlocker): boolean {
  return !isUserAction(blocker) && blocker.kind !== 'LOCK';
}
function blocksWholeJob(blocker: ProcessingBlocker): boolean {
  return blocker.scope !== 'ITEMS';
}
function uniqueTargets(blockers: ProcessingBlocker[]): ProcessingBlocker[] {
  const seen = new Set<string>();
  return blockers.filter((blocker) => {
    const key = blockerTarget(blocker) ?? `${blocker.kind}:${blocker.reason}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

/**
 * The automatic waits that explain why a job is not progressing. A whole-job wait makes item waits irrelevant,
 * and a prerequisite job that itself waits for another listed prerequisite is not the root cause.
 */
export function rootWaits(job: JobSummary, jobs: ReadonlyMap<string, JobSummary> = new Map()): ProcessingBlocker[] {
  const waits = uniqueTargets((job.blockedBy ?? []).filter(isAutomaticWait));
  const whole = waits.filter(blocksWholeJob);
  const candidates = whole.length ? whole : waits;
  const targets = new Set(candidates.map(blockerTarget));
  return candidates.filter((blocker) => {
    if (blocker.kind !== 'JOB') return true;
    const upstream = jobs.get(blocker.id ?? '');
    return !(upstream?.blockedBy ?? []).some((other) => isAutomaticWait(other) && targets.has(blockerTarget(other)));
  });
}

export interface ProcessingRowNotice {
  /** Whole-job waits explained by the shared summary; the row only points to them. */
  after: ProcessingBlocker[];
  /** Waits this row explains itself. */
  waits: ProcessingBlocker[];
  queuedBehind: ProcessingBlocker | null;
  userActions: ProcessingBlocker[];
}
export interface ProcessingOverview {
  /** Prerequisites that hold up several rows at once. */
  shared: ProcessingBlocker[];
  /** Jobs whose waits are explained by the shared summary. */
  sharedJobs: string[];
  rows: ReadonlyMap<string, ProcessingRowNotice>;
  /** Row key (`INDEX:GPS`, `JOB:duplicate`) to the jobs currently waiting for it. */
  dependents: ReadonlyMap<string, string[]>;
}

export function rowNotice(
  job: JobSummary,
  jobs: ReadonlyMap<string, JobSummary> = new Map(),
  shared: ReadonlySet<string> = new Set()
): ProcessingRowNotice {
  const roots = rootWaits(job, jobs);
  const isShared = (blocker: ProcessingBlocker) => blocksWholeJob(blocker) && shared.has(blockerTarget(blocker) ?? '');
  return {
    after: roots.filter(isShared),
    waits: roots.filter((blocker) => !isShared(blocker)),
    queuedBehind: (job.blockedBy ?? []).find((blocker) => blocker.kind === 'LOCK') ?? null,
    userActions: uniqueTargets((job.blockedBy ?? []).filter(isUserAction)),
  };
}

/** Keep shared explanations within one section while retaining all reverse dependencies. */
export function processingOverview(
  jobs: readonly JobSummary[],
  sharedGroup: readonly JobSummary[] = jobs
): ProcessingOverview {
  const byJob = new Map(jobs.map((job) => [job.job ?? '', job]));
  const sharedGroupIds = new Set(sharedGroup.map((job) => job.job));
  const waiting = new Map<string, { blocker: ProcessingBlocker; jobs: string[]; wholeJobs: number }>();
  for (const job of jobs) {
    for (const blocker of rootWaits(job, byJob)) {
      const key = blockerTarget(blocker);
      if (!key) continue;
      const entry = waiting.get(key) ?? { blocker, jobs: [], wholeJobs: 0 };
      entry.jobs.push(job.job ?? '');
      if (sharedGroupIds.has(job.job) && blocksWholeJob(blocker)) entry.wholeJobs += 1;
      waiting.set(key, entry);
    }
  }
  const shared = [...waiting].filter(([, entry]) => entry.wholeJobs >= SHARED_WAIT_MIN_ROWS);
  const sharedKeys = new Set(shared.map(([key]) => key));
  const rows = new Map(
    jobs.map((job) => [job.job ?? '', rowNotice(job, byJob, sharedGroupIds.has(job.job) ? sharedKeys : undefined)])
  );
  return {
    shared: shared.map(([, entry]) => entry.blocker),
    sharedJobs: [...rows].filter(([, notice]) => notice.after.length).map(([job]) => job),
    rows,
    dependents: new Map([...waiting].map(([key, entry]) => [key, entry.jobs])),
  };
}

/** Lead-in before the linked prerequisite; item waits name their count when it is unambiguous. */
export function waitLeadIn(blocker: ProcessingBlocker, job: JobSummary, itemWaits: number): string {
  if (blocksWholeJob(blocker)) return 'Waiting for';
  const count = job.work?.waiting ?? 0;
  return itemWaits === 1 && count > 0
    ? `${formatLocaleNumber(count)} ${jobUnit(job, count)} ${count === 1 ? 'waits' : 'wait'} for`
    : 'Some items wait for';
}

export function userActionText(blocker: ProcessingBlocker, job: JobSummary): string {
  const reasons = job.unavailableReasons;
  if (blocker.reason === 'MISSING_ACTIVITY') {
    const count = reasons?.missingActivity ?? 0;
    return count
      ? `${formatLocaleNumber(count)} ${count === 1 ? 'track needs' : 'tracks need'} an activity.`
      : 'Some tracks need an activity.';
  }
  if (blocker.reason === 'MISSING_CALCULATION') {
    const count = reasons?.missingCalculation ?? 0;
    return `${count ? formatLocaleNumber(count) : 'Some'} ${count === 1 ? 'track has' : 'tracks have'} no energy estimate.`;
  }
  return 'Some items need your review.';
}

const RELATIVE_TIME = new Intl.RelativeTimeFormat('en', { numeric: 'always', style: 'long' });
const RELATIVE_TIME_STEPS: [Intl.RelativeTimeFormatUnit, number][] = [
  ['second', 60],
  ['minute', 60],
  ['hour', 24],
];
/** A future time relative to now ("in 2 minutes"); empty when it is due or unknown. */
export function relativeTime(value: Date | undefined, now = Date.now()): string {
  if (!(value instanceof Date) || !Number.isFinite(value.getTime())) return '';
  let amount = Math.round((value.getTime() - now) / 1000);
  if (amount <= 0) return '';
  for (const [unit, size] of RELATIVE_TIME_STEPS) {
    if (amount < size) return RELATIVE_TIME.format(amount, unit);
    amount = Math.round(amount / size);
  }
  return RELATIVE_TIME.format(amount, 'day');
}
/** When work that is ready or retrying will move next. Lock waits have no schedule to show. */
export function scheduleText(job: JobSummary, queuedBehindLock: boolean, now = Date.now()): string {
  if (job.state === 'RETRYING' && job.nextRetryAt) {
    const when = relativeTime(job.nextRetryAt, now);
    return when ? `Next retry ${when}.` : 'Retrying shortly.';
  }
  if (job.state === 'QUEUED' && !queuedBehindLock && job.nextRunAt) {
    const when = relativeTime(job.nextRunAt, now);
    return when ? `Starts ${when}.` : 'Starts shortly.';
  }
  return '';
}

/** Share of all work per outstanding bucket, for the segmented meter. */
export interface ProcessingSegments {
  ready: number;
  waiting: number;
  retrying: number;
}
export function workSegments(job: JobSummary): ProcessingSegments | null {
  if (!job.work || job.progressKnown === false || job.total <= 0 || job.pending <= 0) return null;
  const share = (value: number | undefined) => (Math.max(0, value ?? 0) * 100) / job.total;
  return { ready: share(job.work.ready), waiting: share(job.work.waiting), retrying: share(job.work.retrying) };
}
/** Outstanding buckets with work, shown as a legend only when the work is mixed. */
export function workBreakdown(job: JobSummary): { key: keyof ProcessingSegments; value: number }[] {
  if (!job.work || job.pending <= 0) return [];
  const buckets = (['ready', 'waiting', 'retrying'] as const)
    .map((key) => ({ key, value: job.work?.[key] ?? 0 }))
    .filter((bucket) => bucket.value > 0);
  return buckets.length > 1 ? buckets : [];
}

const JOB_PRESENTATIONS: Record<string, { label: string; icon: string; explanation: string }> = {
  // Synthetic data uses the feature's own name so the demo reads like a real connection; the caption marks it.
  demoHealth: {
    label: GOOGLE_HEALTH_FEATURE_LABEL,
    icon: 'bi bi-heart-pulse',
    explanation: 'Creates synthetic health records for the demo after imports and energy calculations finish.',
  },
  duplicate: {
    label: 'Duplicate detection',
    icon: 'bi bi-copy',
    explanation: 'Compares imported recordings to find possible duplicate tracks. You can check matches in Review.',
  },
  activityType: {
    label: 'Activity classification',
    icon: 'bi bi-person-walking',
    explanation:
      'Determines the activity of each track, such as walking or cycling. Activities you set yourself are kept.',
  },
  energy: {
    label: 'Energy calculation',
    icon: 'bi bi-lightning-charge',
    explanation:
      'Estimates energy used for each track from its activity and track data. Missing or unsupported estimates are shown as unavailable.',
  },
  exploration: {
    label: 'Exploration score',
    icon: 'bi bi-compass',
    explanation: 'Calculates how much of a recording explores new ground compared with earlier tracks.',
  },
  mediaCorrelation: {
    label: 'Media location matching',
    icon: 'bi bi-pin-map',
    explanation:
      'Matches photos and videos to tracks using when and where they were taken. The count includes media files and track updates.',
  },
};
export function jobPresentation(job: JobSummary) {
  return (
    JOB_PRESENTATIONS[job.job ?? ''] ?? {
      label: job.label || 'Track processing',
      icon: 'bi bi-gear',
      explanation: 'Processes imported tracks in the background.',
    }
  );
}
const SINGULAR_JOB_UNITS: Record<string, string> = { tracks: 'track', days: 'day', 'work items': 'work item' };
export function jobUnit(job: JobSummary, count?: number) {
  // Media correlation queues both media files and track updates.
  const unit =
    job.unit || (job.job === 'mediaCorrelation' ? 'work items' : job.job === 'demoHealth' ? 'days' : 'tracks');
  return count === 1 ? (SINGULAR_JOB_UNITS[unit] ?? unit) : unit;
}
