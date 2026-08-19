import { storeToRefs } from 'pinia';
import { onMounted, onUnmounted } from 'vue';
import { useIndexerStatusStore } from '@/stores/indexerStatusStore';
import { STATUS_POLL_INTERVAL_MS } from '@/utils/statusPolling';

let consumerCount = 0;
let visibleStatusConsumerCount = 0;
let timerId: ReturnType<typeof setTimeout> | null = null;

const POLL_INTERVAL_IDLE_MS = 60_000;

function currentInterval() {
  const store = useIndexerStatusStore();
  if (visibleStatusConsumerCount > 0) {
    return STATUS_POLL_INTERVAL_MS;
  }
  return store.isIndexing || store.isJobPending || store.isOperationalTaskActive
    ? STATUS_POLL_INTERVAL_MS
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
    visibleStatusConsumerCount += enabled ? 1 : -1;
    visibleStatusConsumerCount = Math.max(0, visibleStatusConsumerCount);
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
