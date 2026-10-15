import { computed, onScopeDispose, ref, watch, type Ref } from 'vue';
import type { IndexSummary, JobSummary } from '@/utils/serverAdminApi';
import {
  COUNT_PLAYBACK_DELAY_MS,
  COUNTER_RENDER_INTERVAL_MS,
  countAnimationHasPendingChanges,
  countAnimationValueAt,
  latestCountSample,
  processingCountKey,
  type CountAnimation,
} from '@/utils/countAnimation';

const PROGRESS_DECIMAL_SCALE = 100;

/** Play each row from one completed count, keeping its pending count and meter in step. */
export function useProcessingProgress(
  indexes: Ref<IndexSummary[]>,
  jobs: Ref<JobSummary[]>,
  animations: Ref<Map<string, CountAnimation>>
) {
  const now = ref(Date.now());
  const motion = typeof window.matchMedia === 'function' ? window.matchMedia('(prefers-reduced-motion: reduce)') : null;
  const reducedMotion = ref(motion?.matches ?? false);
  const visible = ref(document.visibilityState !== 'hidden');
  let timer: ReturnType<typeof setTimeout> | undefined;
  let skipHistoryBefore = -Infinity;
  const timelines = computed(() => [
    ...indexes.value.map((index) => animations.value.get(processingCountKey('indexer', index.index, 'completed'))),
    ...jobs.value.map((job) => animations.value.get(processingCountKey('job', job.job, 'done'))),
  ]);

  function tick() {
    clearTimeout(timer);
    now.value = Date.now();
    if (reducedMotion.value) {
      for (const timeline of timelines.value) {
        if (timeline) skipHistoryBefore = Math.max(skipHistoryBefore, latestCountSample(timeline).sampledAt);
      }
    } else if (
      visible.value &&
      timelines.value.some((timeline) => timeline && countAnimationHasPendingChanges(timeline, evaluationTime()))
    ) {
      timer = setTimeout(tick, COUNTER_RENDER_INTERVAL_MS);
    }
  }
  function evaluationTime() {
    return Math.max(now.value, skipHistoryBefore + COUNT_PLAYBACK_DELAY_MS);
  }
  function completedCount(scope: 'indexer' | 'job', id: string | undefined, metric: string, latest: number) {
    const timeline = animations.value.get(processingCountKey(scope, id, metric));
    return !timeline || latestCountSample(timeline).value !== latest || reducedMotion.value
      ? latest
      : Math.min(latest, countAnimationValueAt(timeline, evaluationTime()));
  }
  function progress(percent: number, buffered: number, total: number) {
    // Move only the completed share; the server owns the reported endpoint.
    return buffered && total > 0
      ? Math.max(0, Math.round((percent - (buffered * 100) / total) * PROGRESS_DECIMAL_SCALE) / PROGRESS_DECIMAL_SCALE)
      : percent;
  }
  const summaries = computed(() =>
    indexes.value.map((index) => {
      if (index.discoveryInProgress || index.total <= 0) return index;
      const completed = completedCount('indexer', index.index, 'completed', index.completed);
      const buffered = index.completed - completed;
      return {
        ...index,
        completed,
        pending: index.pending + buffered,
        progressPercent: progress(index.progressPercent, buffered, index.total),
      };
    })
  );
  const jobSummaries = computed(() =>
    jobs.value.map((job) => {
      if (job.progressKnown === false || job.total <= 0) return job;
      const done = completedCount('job', job.job, 'done', job.done);
      const buffered = job.done - done;
      return {
        ...job,
        done,
        pending: job.pending + buffered,
        progressPercent: progress(job.progressPercent, buffered, job.total),
        work: job.work ? { ...job.work, ready: (job.work.ready ?? 0) + buffered } : undefined,
      };
    })
  );
  function motionChanged() {
    reducedMotion.value = motion?.matches ?? false;
  }
  function visibilityChanged() {
    visible.value = document.visibilityState !== 'hidden';
  }
  motion?.addEventListener('change', motionChanged);
  document.addEventListener('visibilitychange', visibilityChanged);
  watch([timelines, reducedMotion, visible], tick, { immediate: true });
  onScopeDispose(() => {
    clearTimeout(timer);
    motion?.removeEventListener('change', motionChanged);
    document.removeEventListener('visibilitychange', visibilityChanged);
  });
  return { summaries, jobSummaries };
}
