import { defineStore } from 'pinia';
import { computed, ref } from 'vue';
import type { DataFreshnessResponseDto } from 'x8ing-mtl-api-typescript-fetch';
import {
  APPLICATION_STATUS_SECTION,
  useApplicationStatusStore,
  type ApplicationStatusRefreshOptions,
} from '@/stores/applicationStatusStore';
import {
  clearAppliedDataFreshnessToken,
  getAppliedDataFreshnessToken,
  setAppliedDataFreshnessToken,
} from '@/utils/dataFreshnessStorage';

export const useDataFreshnessStore = defineStore('dataFreshness', () => {
  const applicationStatusStore = useApplicationStatusStore();
  const currentFreshness = computed(() => applicationStatusStore.freshness);
  const freshnessStatus = computed(() => applicationStatusStore.sectionStates[APPLICATION_STATUS_SECTION.FRESHNESS]);
  const lastChecked = computed(() => formatTime(freshnessStatus.value.lastSuccessAt));
  const isFreshnessPollingHealthy = computed(() => freshnessStatus.value.healthy);
  const appliedToken = ref(getAppliedDataFreshnessToken() ?? '');
  const reloading = ref(false);

  const serverFreshnessToken = computed(() => currentFreshness.value?.freshnessToken ?? '');
  const isOutOfSync = computed(() =>
    Boolean(serverFreshnessToken.value && appliedToken.value && serverFreshnessToken.value !== appliedToken.value)
  );

  function hydrateFromStorage(): void {
    // Storage tracks the shared cache revision. A warm tab must keep the revision
    // it loaded until its own data refresh finishes, even if another tab reloads.
  }

  async function refresh(options: ApplicationStatusRefreshOptions = {}): Promise<DataFreshnessResponseDto | null> {
    await applicationStatusStore.refresh([APPLICATION_STATUS_SECTION.FRESHNESS], options);
    return applicationStatusStore.freshness;
  }

  function markAppliedToken(token: string): void {
    if (!token) return;
    setAppliedDataFreshnessToken(token);
    appliedToken.value = token;
  }

  function clearAppliedToken(): void {
    clearAppliedDataFreshnessToken();
    appliedToken.value = '';
  }

  function setReloading(value: boolean): void {
    reloading.value = value;
  }

  function shouldShowBanner(initialLoadDone: boolean, suppressForAutoFreshen = false): boolean {
    if (!initialLoadDone || reloading.value || suppressForAutoFreshen) return false;
    if (!isOutOfSync.value) return false;
    return true;
  }

  hydrateFromStorage();

  return {
    currentFreshness,
    serverFreshnessToken,
    lastChecked,
    isFreshnessPollingHealthy,
    appliedToken,
    reloading,
    isOutOfSync,
    hydrateFromStorage,
    refresh,
    markAppliedToken,
    clearAppliedToken,
    setReloading,
    shouldShowBanner,
  };
});

function formatTime(timestamp: number | null): string {
  if (timestamp === null) return '';
  return new Date(timestamp).toLocaleTimeString(undefined, {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
}
