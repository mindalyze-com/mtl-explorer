import { storeToRefs } from 'pinia';
import { onMounted, onUnmounted } from 'vue';
import type { DataFreshnessResponseDto } from '@/utils/serverAdminApi';
import { useDataFreshnessStore } from '@/stores/dataFreshnessStore';

const DATA_FRESHNESS_POLL_INTERVAL_MS = 30_000;

let consumerCount = 0;
let timerId: ReturnType<typeof setTimeout> | null = null;

function scheduleNext() {
  if (consumerCount <= 0) return;
  if (timerId !== null) clearTimeout(timerId);
  timerId = setTimeout(poll, DATA_FRESHNESS_POLL_INTERVAL_MS);
}

async function poll() {
  await refresh();
  if (consumerCount > 0) {
    scheduleNext();
  }
}

export async function refresh(): Promise<DataFreshnessResponseDto | null> {
  return useDataFreshnessStore().refresh();
}

function startPolling() {
  void poll();
}

function stopPolling() {
  if (timerId !== null) {
    clearTimeout(timerId);
    timerId = null;
  }
}

export function useDataFreshness() {
  const store = useDataFreshnessStore();
  const { currentFreshness, serverFreshnessToken, lastChecked, isFreshnessPollingHealthy, appliedToken, reloading } =
    storeToRefs(store);

  onMounted(() => {
    consumerCount++;
    if (consumerCount === 1) startPolling();
  });

  onUnmounted(() => {
    consumerCount = Math.max(0, consumerCount - 1);
    if (consumerCount === 0) stopPolling();
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
