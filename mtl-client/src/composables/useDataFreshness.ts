import { storeToRefs } from 'pinia';
import { onMounted, onUnmounted } from 'vue';
import type { DataFreshnessResponseDto } from '@/utils/serverAdminApi';
import { useDataFreshnessStore } from '@/stores/dataFreshnessStore';
import {
  APPLICATION_STATUS_SECTION,
  type ApplicationStatusPollingConsumer,
  useApplicationStatusStore,
} from '@/stores/applicationStatusStore';

const DATA_FRESHNESS_POLL_INTERVAL_MS = 30_000;

export async function refresh(): Promise<DataFreshnessResponseDto | null> {
  return useDataFreshnessStore().refresh();
}

export function useDataFreshness() {
  const store = useDataFreshnessStore();
  const applicationStatusStore = useApplicationStatusStore();
  const { currentFreshness, serverFreshnessToken, lastChecked, isFreshnessPollingHealthy, appliedToken, reloading } =
    storeToRefs(store);
  let pollingConsumer: ApplicationStatusPollingConsumer | null = null;

  onMounted(() => {
    pollingConsumer = applicationStatusStore.registerPollingConsumer({
      [APPLICATION_STATUS_SECTION.FRESHNESS]: DATA_FRESHNESS_POLL_INTERVAL_MS,
    });
  });

  onUnmounted(() => {
    pollingConsumer?.dispose();
    pollingConsumer = null;
  });

  return {
    currentFreshness,
    serverFreshnessToken,
    lastChecked,
    refresh: store.refresh,
    isFreshnessPollingHealthy,
    appliedFreshnessToken: appliedToken,
    freshnessReloading: reloading,
    syncFreshnessStorage: store.hydrateFromStorage,
    markAppliedFreshnessToken: store.markAppliedToken,
    clearAppliedFreshnessToken: store.clearAppliedToken,
    setFreshnessReloading: store.setReloading,
    freshnessStore: store,
  };
}
