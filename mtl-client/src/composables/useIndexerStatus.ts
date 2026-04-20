import { ref, computed, onMounted, onUnmounted } from 'vue';
import { getIndexerStatus, getJobStatus, type IndexSummary, type JobSummary } from '@/utils/ServiceHelper';

// ── Module-level singleton: shared across all consumers ──────────────────────
// Polling starts when the first consumer mounts, stops when the last unmounts.

const summaries = ref<IndexSummary[]>([]);
const jobSummaries = ref<JobSummary[]>([]);
const lastRefreshed = ref('');
const isIndexing = computed(() => summaries.value.some(s => s.pending > 0));
const isJobPending = computed(() => jobSummaries.value.some(s => s.pending > 0));

let consumerCount = 0;
let timerId: ReturnType<typeof setTimeout> | null = null;
let _pollWarnShown = false;

const POLL_INTERVAL_ACTIVE_MS = 5_000;   // 5 s while indexing / jobs pending
const POLL_INTERVAL_IDLE_MS   = 60_000;  // 30 s when nothing is happening

function currentInterval() {
  return (isIndexing.value || isJobPending.value)
    ? POLL_INTERVAL_ACTIVE_MS
    : POLL_INTERVAL_IDLE_MS;
}

function scheduleNext() {
  timerId = setTimeout(poll, currentInterval());
}

async function poll() {
  await refresh();
  scheduleNext();
}

async function refresh() {
  try {
    const [indexData, jobData] = await Promise.all([
      getIndexerStatus(),
      getJobStatus(),
    ]);
    summaries.value = indexData;
    jobSummaries.value = jobData;
    lastRefreshed.value = new Date().toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  } catch (err) {
    if (!_pollWarnShown) {
      console.warn('[MTL] Indexer/job status polling failed — server may be unreachable or blocked:', err);
      _pollWarnShown = true;
    }
  }
}

function startPolling() {
  refresh(); // Immediate first load
  scheduleNext();
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
    if (consumerCount === 0) startPolling();
    consumerCount++;
  });

  onUnmounted(() => {
    consumerCount--;
    if (consumerCount === 0) stopPolling();
  });

  return { summaries, jobSummaries, lastRefreshed, isIndexing, isJobPending, refresh };
}

