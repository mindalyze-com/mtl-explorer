import { storeToRefs } from 'pinia';
import { onMounted, onUnmounted } from 'vue';
import { useIndexerStatusStore } from '@/stores/indexerStatusStore';

let consumerCount = 0;
let fastPollingConsumerCount = 0;
let timerId: ReturnType<typeof setTimeout> | null = null;

const POLL_INTERVAL_ACTIVE_MS = 5_000; // 5 s while indexing / jobs / operational tasks are active
const POLL_INTERVAL_IDLE_MS = 60_000; // 60 s when nothing is happening
const POLL_INTERVAL_VISIBLE_STATUS_MS = 1_000; // 1 s while a status surface is visible

function currentInterval() {
  const store = useIndexerStatusStore();
  if (fastPollingConsumerCount > 0) {
    return POLL_INTERVAL_VISIBLE_STATUS_MS;
  }
  return store.isIndexing || store.isJobPending || store.isOperationalTaskActive
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
  if (consumerCount > 0) {
    scheduleNext();
  }
}

async function refresh() {
  return useIndexerStatusStore().refresh();
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

function rescheduleNext() {
  stopPolling();
  scheduleNext();
}

// ── Composable ───────────────────────────────────────────────────────────────

export function useIndexerStatus() {
  const store = useIndexerStatusStore();
  const {
    summaries,
    jobSummaries,
    operationalTasks,
    lastRefreshed,
    isIndexerStatusPollingHealthy,
    isIndexing,
    isJobPending,
    isOperationalTaskActive,
  } = storeToRefs(store);
  let fastPollingEnabled = false;

  function setFastPolling(enabled: boolean) {
    if (fastPollingEnabled === enabled) return;
    fastPollingEnabled = enabled;
    fastPollingConsumerCount += enabled ? 1 : -1;
    fastPollingConsumerCount = Math.max(0, fastPollingConsumerCount);
    rescheduleNext();
  }

  onMounted(() => {
    consumerCount++;
    if (consumerCount === 1) startPolling();
  });

  onUnmounted(() => {
    setFastPolling(false);
    consumerCount = Math.max(0, consumerCount - 1);
    if (consumerCount === 0) stopPolling();
  });

  return {
    summaries,
    jobSummaries,
    operationalTasks,
    lastRefreshed,
    isIndexerStatusPollingHealthy,
    isIndexing,
    isJobPending,
    isOperationalTaskActive,
    refresh: store.refresh,
    setFastPolling,
  };
}
