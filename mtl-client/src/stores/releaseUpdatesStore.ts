import { warnSanitized } from '@/utils/safeLogging';
import { defineStore } from 'pinia';
import { computed, ref } from 'vue';
import { ReleaseUpdateControllerApi, type ReleaseUpdateResponse } from 'x8ing-mtl-api-typescript-fetch';
import { getApiConfiguration } from '@/utils/openApiClient';
import { getAuthenticatedUsername, isAuthenticated } from '@/utils/auth';
import { readJsonStorage, writeJsonStorage, STORAGE_KEYS } from '@/utils/appStorage';

const CLIENT_CHECK_INTERVAL_MS = 60 * 60 * 1000;
const CLIENT_CHECK_COOLDOWN_MS = 60 * 1000;
const PENDING_CHECK_POLL_MS = 1000;
const PENDING_CHECK_POLL_LIMIT = 30;
const REQUEST_TIMEOUT_MS = 35_000;

interface UpdatePreferences {
  noticed?: string;
  skipped?: string;
  installedBuild?: string;
  installedComponentKey?: string;
  pendingRelease?: string;
  completedRelease?: string;
}
type Preferences = Record<string, UpdatePreferences>;

function readPreferences(): Preferences {
  return readJsonStorage(STORAGE_KEYS.releaseUpdates, {}, (value) => {
    if (!value || typeof value !== 'object' || Array.isArray(value)) return {};
    return Object.fromEntries(
      Object.entries(value)
        .filter(([, entry]) => entry && typeof entry === 'object')
        .map(([key, entry]) => [
          key,
          Object.fromEntries(
            Object.entries(entry).filter(
              ([field, content]) =>
                [
                  'noticed',
                  'skipped',
                  'installedBuild',
                  'installedComponentKey',
                  'pendingRelease',
                  'completedRelease',
                ].includes(field) && typeof content === 'string'
            )
          ),
        ])
    );
  });
}

export const useReleaseUpdatesStore = defineStore('releaseUpdates', () => {
  const status = ref<ReleaseUpdateResponse | null>(null);
  const preferences = ref<Preferences>(readPreferences());
  const loading = ref(false);
  const detailsVisible = ref(false);
  const noticeVisible = ref(false);
  const installedNoticeVisible = ref(false);
  const requestFailed = ref(false);
  const browserUpdateAvailable = ref(false);
  let reloadBrowserAction: (() => void) | undefined;
  const scope = computed(() => `${status.value?.installationId ?? ''}:${getAuthenticatedUsername() ?? ''}`);
  const releaseKey = computed(
    () => `${status.value?.channel}:${status.value?.latestReleaseVersion}:${status.value?.latestBuildId}`
  );
  const preference = computed(() => preferences.value[scope.value] ?? {});
  const updateAvailable = computed(() => status.value?.status === 'UPDATE_AVAILABLE');
  const skipped = computed(() => preference.value.skipped === releaseKey.value);
  const hasUpdate = computed(() => updateAvailable.value && !skipped.value);
  let lastAttempt = Number.NEGATIVE_INFINITY;
  let inFlight: Promise<void> | null = null;

  function save(value: UpdatePreferences) {
    preferences.value = { ...preferences.value, [scope.value]: { ...preference.value, ...value } };
    writeJsonStorage(STORAGE_KEYS.releaseUpdates, preferences.value);
  }

  function applyStatus(value: ReleaseUpdateResponse) {
    status.value = value;
    preferences.value = readPreferences();
    if (!value.installationId) return;
    const installedKey = value.installedComponentKey || value.installedBuildId;
    const previousBuild = preference.value.installedComponentKey || preference.value.installedBuild;
    if (
      previousBuild &&
      installedKey &&
      previousBuild !== installedKey &&
      value.status === 'UP_TO_DATE' &&
      !value.checkFailed &&
      preference.value.pendingRelease === releaseKey.value
    )
      save({ completedRelease: releaseKey.value });
    if (value.installedBuildId) save({ installedBuild: value.installedBuildId });
    if (installedKey) save({ installedComponentKey: installedKey });
    if (value.status === 'UPDATE_AVAILABLE') save({ pendingRelease: releaseKey.value });
    else if (value.status === 'UP_TO_DATE' && !value.checkFailed) save({ pendingRelease: '' });
    installedNoticeVisible.value =
      value.status === 'UP_TO_DATE' && !value.checkFailed && preference.value.completedRelease === releaseKey.value;
    if (hasUpdate.value && preference.value.noticed !== releaseKey.value) noticeVisible.value = true;
    if (!hasUpdate.value) noticeVisible.value = false;
  }

  function dismissNotice() {
    if (status.value) save({ noticed: releaseKey.value, completedRelease: '' });
    noticeVisible.value = false;
    installedNoticeVisible.value = false;
  }

  function markNoticeShown() {
    if (status.value) save({ noticed: releaseKey.value });
  }

  function setBrowserUpdate(available: boolean, reload: () => void) {
    browserUpdateAvailable.value = available;
    reloadBrowserAction = reload;
  }

  function reloadBrowser() {
    reloadBrowserAction?.();
  }

  function openDetails() {
    dismissNotice();
    installedNoticeVisible.value = false;
    detailsVisible.value = true;
  }

  function skipVersion() {
    save({ noticed: releaseKey.value, skipped: releaseKey.value });
    noticeVisible.value = false;
  }

  function remindAgain() {
    save({ skipped: '' });
  }

  async function refresh(force = false): Promise<void> {
    if (!isAuthenticated()) return;
    if (inFlight) return inFlight;
    if (!force && Date.now() - lastAttempt < CLIENT_CHECK_COOLDOWN_MS) return;
    lastAttempt = Date.now();
    loading.value = true;
    inFlight = (async () => {
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
      try {
        const api = new ReleaseUpdateControllerApi(getApiConfiguration());
        let value = await api.getUpdateStatus({ check: force }, { signal: controller.signal });
        for (let attempt = 0; value.checking && attempt < PENDING_CHECK_POLL_LIMIT; attempt++) {
          await new Promise((resolve) => setTimeout(resolve, PENDING_CHECK_POLL_MS));
          if (!isAuthenticated()) return;
          value = await api.getUpdateStatus({ check: false }, { signal: controller.signal });
        }
        requestFailed.value = false;
        applyStatus(value);
      } catch (error) {
        requestFailed.value = true;
        warnSanitized('Release update status unavailable', error, {
          topic1: 'application',
          topic2: 'releaseUpdatesStore',
          topic3: 'refresh',
        });
      } finally {
        clearTimeout(timeout);
        loading.value = false;
        inFlight = null;
      }
    })();
    return inFlight;
  }

  function startChecks(): () => void {
    const checkVisible = () => {
      if (document.visibilityState === 'visible') void refresh();
    };
    const synchronize = (event: StorageEvent) => {
      if (event.key === STORAGE_KEYS.releaseUpdates) {
        preferences.value = readPreferences();
        if (preference.value.noticed === releaseKey.value || skipped.value) noticeVisible.value = false;
        if (preference.value.completedRelease !== releaseKey.value) installedNoticeVisible.value = false;
      }
    };
    void refresh();
    const interval = window.setInterval(checkVisible, CLIENT_CHECK_INTERVAL_MS);
    window.addEventListener('focus', checkVisible);
    window.addEventListener('online', checkVisible);
    window.addEventListener('storage', synchronize);
    document.addEventListener('visibilitychange', checkVisible);
    return () => {
      window.clearInterval(interval);
      window.removeEventListener('focus', checkVisible);
      window.removeEventListener('online', checkVisible);
      window.removeEventListener('storage', synchronize);
      document.removeEventListener('visibilitychange', checkVisible);
    };
  }

  return {
    status,
    loading,
    detailsVisible,
    noticeVisible,
    installedNoticeVisible,
    requestFailed,
    hasUpdate,
    updateAvailable,
    skipped,
    applyStatus,
    dismissNotice,
    openDetails,
    skipVersion,
    remindAgain,
    refresh,
    startChecks,
    markNoticeShown,
    browserUpdateAvailable,
    setBrowserUpdate,
    reloadBrowser,
  };
});
