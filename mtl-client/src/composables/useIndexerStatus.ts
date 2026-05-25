import { ref, computed, onMounted, onUnmounted } from 'vue';
import {
  getAdminOperationalTasks,
  getIndexerStatus,
  getJobStatus,
  type AdminOperationalTask,
  type IndexSummary,
  type JobSummary,
} from '@/utils/serverAdminApi';

// ── Module-level singleton: shared across all consumers ──────────────────────
// Polling starts when the first consumer mounts, stops when the last unmounts.

const summaries = ref<IndexSummary[]>([]);
const jobSummaries = ref<JobSummary[]>([]);
const operationalTasks = ref<AdminOperationalTask[]>([]);
const lastRefreshed = ref('');
const isIndexing = computed(() => summaries.value.some((s) => s.pending > 0));
const isJobPending = computed(() => jobSummaries.value.some((s) => s.pending > 0));
const isOperationalTaskActive = computed(() => operationalTasks.value.some((s) => s.active));

let consumerCount = 0;
let timerId: ReturnType<typeof setTimeout> | null = null;
let _pollWarnShown = false;

const POLL_INTERVAL_ACTIVE_MS = 5_000; // 5 s while indexing / jobs / operational tasks are active
const POLL_INTERVAL_IDLE_MS = 60_000; // 60 s when nothing is happening

function currentInterval() {
  return isIndexing.value || isJobPending.value || isOperationalTaskActive.value
    ? POLL_INTERVAL_ACTIVE_MS
    : POLL_INTERVAL_IDLE_MS;
}

function scheduleNext() {
  if (consumerCount <= 0 || timerId !== null) return;
  timerId = setTimeout(() => {
    timerId = null;
    void poll();
  }, currentInterval());
}

async function poll() {
  await refresh();
  scheduleNext();
}

async function refresh() {
  try {
    const [indexData, jobData, operationalData] = await Promise.all([
      getIndexerStatus(),
      getJobStatus(),
      getAdminOperationalTasks(),
    ]);
    summaries.value = indexData;
    jobSummaries.value = jobData;
    operationalTasks.value = operationalData;
    lastRefreshed.value = new Date().toLocaleTimeString(undefined, {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    });
  } catch (err) {
    if (!_pollWarnShown) {
      console.warn('[MTL] Indexer/job status polling failed — server may be unreachable or blocked:', err);
      _pollWarnShown = true;
    }
  }
}

function startPolling() {
  void refresh().finally(scheduleNext);
}

function stopPolling() {
  if (timerId !== null) {
    clearTimeout(timerId);
    timerId = null;
  }
}

// ── Composable ───────────────────────────────────────────────────────────────

export function useIndexerStatus() {
  onMounted(() => {
    consumerCount++;
    if (consumerCount === 1) startPolling();
  });

  onUnmounted(() => {
    consumerCount = Math.max(0, consumerCount - 1);
    if (consumerCount === 0) stopPolling();
  });

  return {
    summaries,
    jobSummaries,
    operationalTasks,
    lastRefreshed,
    isIndexing,
    isJobPending,
    isOperationalTaskActive,
    refresh,
  };
}
