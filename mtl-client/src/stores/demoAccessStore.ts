import { computed, onScopeDispose, ref } from 'vue';
import { defineStore } from 'pinia';
import { getDemoStatus } from '@/utils/serverAdminApi';
import { warnRequestFailure } from '@/utils/safeLogging';

export const SHARED_DEMO_READ_ONLY_MESSAGE = 'Import and review changes are unavailable in this shared demo.';

const RETRY_DELAYS_MS = [5000, 15000, 60000];

/** Settings and review controls share the last confirmed installation status. */
export const useDemoAccessStore = defineStore('demoAccess', () => {
  const demoMode = ref<boolean | null>(null);
  const syntheticHealthData = ref(false);
  const writeAllowed = computed(() => demoMode.value === false);
  const loading = ref(false);
  const error = ref('');
  let request: Promise<void> | undefined;
  let retryTimer: ReturnType<typeof setTimeout> | undefined;
  let failures = 0;
  let disposed = false;

  function load(force = false): Promise<void> {
    if (disposed || (!force && demoMode.value !== null && !error.value)) return Promise.resolve();
    if (request) return request;
    clearTimeout(retryTimer);
    loading.value = true;
    request = getDemoStatus({ throwOnFailure: true })
      .then((status) => {
        if (disposed) return;
        demoMode.value = status.demoMode;
        syntheticHealthData.value = status.demoMode && status.syntheticHealthData === true;
        error.value = '';
        failures = 0;
      })
      .catch((failure: unknown) => {
        if (disposed) return;
        error.value = 'Write access could not be checked.';
        const delay = RETRY_DELAYS_MS[Math.min(failures++, RETRY_DELAYS_MS.length - 1)];
        retryTimer = setTimeout(() => void load(true), delay);
        warnRequestFailure('Installation write access could not be checked', failure, {
          topic1: 'application',
          topic2: 'demoAccessStore',
          topic3: 'load',
        });
      })
      .finally(() => {
        loading.value = false;
        request = undefined;
      });
    return request;
  }

  function retryWhenOnline() {
    if (error.value) void load();
  }
  if (typeof window !== 'undefined') window.addEventListener('online', retryWhenOnline);
  onScopeDispose(() => {
    disposed = true;
    clearTimeout(retryTimer);
    if (typeof window !== 'undefined') window.removeEventListener('online', retryWhenOnline);
  });

  return { demoMode, syntheticHealthData, writeAllowed, loading, error, load };
});
