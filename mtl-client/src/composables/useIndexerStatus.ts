import { storeToRefs } from 'pinia';
import { onMounted, onUnmounted } from 'vue';
import { useIndexerStatusStore } from '@/stores/indexerStatusStore';
import {
  APPLICATION_STATUS_SECTION,
  type ApplicationStatusPollingConsumer,
  useApplicationStatusStore,
} from '@/stores/applicationStatusStore';
import { STATUS_POLL_INTERVAL_MS } from '@/utils/statusPolling';

const POLL_INTERVAL_IDLE_MS = 60_000;

// ── Composable ───────────────────────────────────────────────────────────────

export function useIndexerStatus() {
  const store = useIndexerStatusStore();
  const applicationStatusStore = useApplicationStatusStore();
  const {
    summaries,
    jobSummaries,
    dependencies,
    operationalTasks,
    lastRefreshed,
    isIndexerStatusPollingHealthy,
    isIndexing,
    isJobPending,
    isOperationalTaskActive,
  } = storeToRefs(store);
  const { countAnimations, statusClockOffsetMs } = storeToRefs(applicationStatusStore);
  let fastPollingEnabled = false;
  let pollingConsumer: ApplicationStatusPollingConsumer | null = null;

  function pollingDemand() {
    return fastPollingEnabled
      ? {
          [APPLICATION_STATUS_SECTION.PROCESSING]: STATUS_POLL_INTERVAL_MS,
          [APPLICATION_STATUS_SECTION.SERVICES]: STATUS_POLL_INTERVAL_MS,
        }
      : { [APPLICATION_STATUS_SECTION.PROCESSING]: POLL_INTERVAL_IDLE_MS };
  }

  function setFastPolling(enabled: boolean) {
    if (fastPollingEnabled === enabled) return;
    fastPollingEnabled = enabled;
    pollingConsumer?.update(pollingDemand());
  }

  onMounted(() => {
    pollingConsumer = applicationStatusStore.registerPollingConsumer(pollingDemand());
  });

  onUnmounted(() => {
    pollingConsumer?.dispose();
    pollingConsumer = null;
  });

  return {
    summaries,
    jobSummaries,
    dependencies,
    operationalTasks,
    countAnimations,
    statusClockOffsetMs,
    lastRefreshed,
    isIndexerStatusPollingHealthy,
    isIndexing,
    isJobPending,
    isOperationalTaskActive,
    refresh: store.refresh,
    setFastPolling,
  };
}
