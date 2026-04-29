import { computed, onMounted, onUnmounted, ref } from 'vue';
import { getDataFreshness, type DataFreshnessResponseDto } from '@/utils/serverAdminApi';

const DATA_FRESHNESS_POLL_INTERVAL_MS = 30_000;

const currentFreshness = ref<DataFreshnessResponseDto | null>(null);
const lastChecked = ref('');
const isFreshnessPollingHealthy = ref(true);
const serverFreshnessToken = computed(() => currentFreshness.value?.freshnessToken ?? '');

let consumerCount = 0;
let timerId: ReturnType<typeof setTimeout> | null = null;
let _pollWarnShown = false;

function scheduleNext() {
  timerId = setTimeout(poll, DATA_FRESHNESS_POLL_INTERVAL_MS);
}

async function poll() {
  await refresh();
  if (consumerCount > 0) {
    scheduleNext();
  }
}

export async function refresh(): Promise<DataFreshnessResponseDto | null> {
  try {
    const data = await getDataFreshness();
    currentFreshness.value = data;
    lastChecked.value = new Date().toLocaleTimeString(undefined, {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    });
    isFreshnessPollingHealthy.value = true;
    return data;
  } catch (err) {
    isFreshnessPollingHealthy.value = false;
    if (!_pollWarnShown) {
      console.warn('[MTL] Data freshness polling failed — server may be unreachable or blocked:', err);
      _pollWarnShown = true;
    }
    return null;
  }
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
  onMounted(() => {
    if (consumerCount === 0) startPolling();
    consumerCount++;
  });

  onUnmounted(() => {
    consumerCount--;
    if (consumerCount === 0) stopPolling();
  });

  return {
    currentFreshness,
    serverFreshnessToken,
    lastChecked,
    refresh,
    isFreshnessPollingHealthy,
  };
}
