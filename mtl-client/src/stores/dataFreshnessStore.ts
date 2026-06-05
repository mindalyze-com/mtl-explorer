import { defineStore } from 'pinia';
import { computed, ref, shallowRef } from 'vue';
import { getDataFreshness, type DataFreshnessResponseDto } from '@/utils/serverAdminApi';
import {
  clearAppliedDataFreshnessToken,
  clearDismissedDataFreshness,
  getAppliedDataFreshnessToken,
  getDismissedDataFreshness,
  setAppliedDataFreshnessToken,
  setDismissedDataFreshness,
} from '@/utils/dataFreshnessStorage';

const MILLISECONDS_PER_MINUTE = 60 * 1000;
const DATA_FRESHNESS_DISMISS_MINUTES = 5;
export const DATA_FRESHNESS_DISMISS_MS = DATA_FRESHNESS_DISMISS_MINUTES * MILLISECONDS_PER_MINUTE;

export const useDataFreshnessStore = defineStore('dataFreshness', () => {
  const currentFreshness = shallowRef<DataFreshnessResponseDto | null>(null);
  const lastChecked = ref('');
  const isFreshnessPollingHealthy = ref(true);
  const appliedToken = ref(getAppliedDataFreshnessToken() ?? '');
  const dismissedToken = ref<string | null>(null);
  const dismissedExpiresAt = ref(0);
  const reloading = ref(false);

  let dismissTimer: ReturnType<typeof setTimeout> | null = null;
  let pollWarnShown = false;

  const serverFreshnessToken = computed(() => currentFreshness.value?.freshnessToken ?? '');
  const isOutOfSync = computed(() =>
    Boolean(serverFreshnessToken.value && appliedToken.value && serverFreshnessToken.value !== appliedToken.value)
  );
  const activeDismissedToken = computed(() => (dismissedExpiresAt.value > Date.now() ? dismissedToken.value : null));

  function hydrateFromStorage(): void {
    appliedToken.value = getAppliedDataFreshnessToken() ?? '';
    hydrateDismissal();
  }

  async function refresh(): Promise<DataFreshnessResponseDto | null> {
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
      if (!pollWarnShown) {
        console.warn('[MTL] Data freshness polling failed — server may be unreachable or blocked:', err);
        pollWarnShown = true;
      }
      return null;
    }
  }

  function markAppliedToken(token: string): void {
    if (!token) return;
    setAppliedDataFreshnessToken(token);
    appliedToken.value = token;
    clearDismissal();
  }

  function clearAppliedToken(): void {
    clearAppliedDataFreshnessToken();
    appliedToken.value = '';
  }

  function dismissToken(token: string, durationMs = DATA_FRESHNESS_DISMISS_MS, nowMs = Date.now()): void {
    const dismissed = setDismissedDataFreshness(token, durationMs, nowMs);
    if (!dismissed) {
      dismissedToken.value = null;
      dismissedExpiresAt.value = 0;
      clearDismissTimer();
      return;
    }

    dismissedToken.value = dismissed.token;
    dismissedExpiresAt.value = dismissed.expiresAt;
    scheduleDismissTimer();
  }

  function hydrateDismissal(nowMs = Date.now()): void {
    const dismissed = getDismissedDataFreshness(nowMs);
    dismissedToken.value = dismissed?.token ?? null;
    dismissedExpiresAt.value = dismissed?.expiresAt ?? 0;
    scheduleDismissTimer();
  }

  function clearDismissal(): void {
    clearDismissedDataFreshness();
    dismissedToken.value = null;
    dismissedExpiresAt.value = 0;
    clearDismissTimer();
  }

  function setReloading(value: boolean): void {
    reloading.value = value;
  }

  function shouldShowBanner(initialLoadDone: boolean, suppressForAutoFreshen = false): boolean {
    if (!initialLoadDone || reloading.value || suppressForAutoFreshen) return false;
    if (!isOutOfSync.value) return false;
    return activeDismissedToken.value === null;
  }

  function scheduleDismissTimer(): void {
    clearDismissTimer();
    const token = dismissedToken.value;
    const expiresAt = dismissedExpiresAt.value;
    if (!token || !Number.isFinite(expiresAt)) return;

    const delayMs = expiresAt - Date.now();
    if (delayMs <= 0) {
      clearDismissal();
      return;
    }

    dismissTimer = setTimeout(() => {
      if (dismissedToken.value === token && dismissedExpiresAt.value === expiresAt) {
        clearDismissal();
      }
    }, delayMs);
  }

  function clearDismissTimer(): void {
    if (dismissTimer === null) return;
    clearTimeout(dismissTimer);
    dismissTimer = null;
  }

  hydrateFromStorage();

  return {
    currentFreshness,
    serverFreshnessToken,
    lastChecked,
    isFreshnessPollingHealthy,
    appliedToken,
    dismissedToken,
    dismissedExpiresAt,
    reloading,
    isOutOfSync,
    activeDismissedToken,
    hydrateFromStorage,
    refresh,
    markAppliedToken,
    clearAppliedToken,
    dismissToken,
    hydrateDismissal,
    clearDismissal,
    setReloading,
    shouldShowBanner,
  };
});
