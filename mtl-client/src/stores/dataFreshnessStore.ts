import { defineStore } from 'pinia';
import { computed, ref, shallowRef } from 'vue';
import { getDataFreshness, type DataFreshnessResponseDto } from '@/utils/serverAdminApi';
import {
  clearAppliedDataFreshnessToken,
  clearDataFreshnessSnooze,
  getAppliedDataFreshnessToken,
  getDataFreshnessSnoozedUntil,
  setAppliedDataFreshnessToken,
  setDataFreshnessSnooze,
} from '@/utils/dataFreshnessStorage';

const MILLISECONDS_PER_MINUTE = 60 * 1000;
const DATA_FRESHNESS_SNOOZE_MINUTES = 5;
export const DATA_FRESHNESS_SNOOZE_MS = DATA_FRESHNESS_SNOOZE_MINUTES * MILLISECONDS_PER_MINUTE;

export const useDataFreshnessStore = defineStore('dataFreshness', () => {
  const currentFreshness = shallowRef<DataFreshnessResponseDto | null>(null);
  const lastChecked = ref('');
  const isFreshnessPollingHealthy = ref(true);
  const appliedToken = ref(getAppliedDataFreshnessToken() ?? '');
  const snoozedUntil = ref(0);
  const reloading = ref(false);

  let snoozeTimer: ReturnType<typeof setTimeout> | null = null;
  let pollWarnShown = false;
  let refreshInFlight: Promise<DataFreshnessResponseDto | null> | null = null;

  const serverFreshnessToken = computed(() => currentFreshness.value?.freshnessToken ?? '');
  const isOutOfSync = computed(() =>
    Boolean(serverFreshnessToken.value && appliedToken.value && serverFreshnessToken.value !== appliedToken.value)
  );

  function hydrateFromStorage(): void {
    appliedToken.value = getAppliedDataFreshnessToken() ?? '';
    hydrateSnooze();
  }

  function refresh(): Promise<DataFreshnessResponseDto | null> {
    if (refreshInFlight) return refreshInFlight;

    refreshInFlight = performRefresh().finally(() => {
      refreshInFlight = null;
    });
    return refreshInFlight;
  }

  async function performRefresh(): Promise<DataFreshnessResponseDto | null> {
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

  // Applying data and snoozing the warning are independent. Background syncs must not
  // cancel the user's time-based snooze.
  function markAppliedToken(token: string): void {
    if (!token) return;
    setAppliedDataFreshnessToken(token);
    appliedToken.value = token;
  }

  function clearAppliedToken(): void {
    clearAppliedDataFreshnessToken();
    appliedToken.value = '';
  }

  function snooze(durationMs = DATA_FRESHNESS_SNOOZE_MS, nowMs = Date.now()): void {
    snoozedUntil.value = setDataFreshnessSnooze(durationMs, nowMs);
    scheduleSnoozeTimer();
  }

  function hydrateSnooze(nowMs = Date.now()): void {
    snoozedUntil.value = getDataFreshnessSnoozedUntil(nowMs);
    scheduleSnoozeTimer();
  }

  function clearSnooze(): void {
    clearDataFreshnessSnooze();
    snoozedUntil.value = 0;
    clearSnoozeTimer();
  }

  function setReloading(value: boolean): void {
    reloading.value = value;
  }

  function shouldShowBanner(initialLoadDone: boolean, suppressForAutoFreshen = false): boolean {
    if (!initialLoadDone || reloading.value || suppressForAutoFreshen) return false;
    if (!isOutOfSync.value) return false;
    return snoozedUntil.value <= Date.now();
  }

  function scheduleSnoozeTimer(): void {
    clearSnoozeTimer();
    const expiresAt = snoozedUntil.value;
    if (!Number.isFinite(expiresAt) || expiresAt <= 0) return;

    const delayMs = expiresAt - Date.now();
    if (delayMs <= 0) {
      clearSnooze();
      return;
    }

    snoozeTimer = setTimeout(() => {
      if (snoozedUntil.value === expiresAt) {
        clearSnooze();
      }
    }, delayMs);
  }

  function clearSnoozeTimer(): void {
    if (snoozeTimer === null) return;
    clearTimeout(snoozeTimer);
    snoozeTimer = null;
  }

  hydrateFromStorage();

  return {
    currentFreshness,
    serverFreshnessToken,
    lastChecked,
    isFreshnessPollingHealthy,
    appliedToken,
    snoozedUntil,
    reloading,
    isOutOfSync,
    hydrateFromStorage,
    refresh,
    markAppliedToken,
    clearAppliedToken,
    snooze,
    hydrateSnooze,
    clearSnooze,
    setReloading,
    shouldShowBanner,
  };
});
