<template>
  <section
    :class="detail ? 'admin-card gh-admin-card' : 'admin-action-row gh-connection-row'"
    aria-labelledby="google-health-admin-heading"
  >
    <template v-if="!detail">
      <div class="admin-action-copy">
        <h3 id="google-health-admin-heading" class="admin-action-label">{{ GOOGLE_HEALTH_FEATURE_LABEL }}</h3>
        <span class="admin-action-hint" role="status">{{ connectionSummary }}</span>
        <GoogleHealthSyncNotice
          v-if="demoMode === false || syntheticData"
          :status="status"
          :synthetic-data="syntheticData"
        />
        <span v-if="displayError" class="admin-action-hint" role="alert">{{ displayError }}</span>
      </div>
      <div class="admin-action-controls">
        <Button v-if="demoMode === false && loadError" label="Retry" size="small" @click="loadStatus" />
        <Button
          v-if="demoMode === false && status && (authorizationNeedsAction || status.connectionState !== 'CONNECTED')"
          :label="authorizationNeedsAction ? 'Reauthorize' : 'Start setup'"
          size="small"
          :loading="setupLoading"
          :disabled="Boolean(busyAction) || setupLoading"
          @click="openPrimaryAction"
        />
        <RouterLink v-if="demoMode !== null" v-slot="{ navigate }" custom :to="GOOGLE_HEALTH_SETTINGS_ROUTE">
          <Button :label="demoMode ? 'Learn more' : 'Manage'" size="small" severity="secondary" @click="navigate" />
        </RouterLink>
      </div>
    </template>
    <template v-else>
      <div class="admin-card__header">
        <div class="gh-admin-card__title">
          <span class="filter-section-icon" aria-hidden="true"><i class="bi bi-heart-pulse" /></span>
          <div>
            <h3 id="google-health-admin-heading">{{ GOOGLE_HEALTH_FEATURE_LABEL }}</h3>
            <p>{{ GOOGLE_HEALTH_BETA_NOTICE }}</p>
          </div>
        </div>
        <span :class="['admin-status-pill', statusClass]"><i :class="statusIcon" /> {{ statusLabel }}</span>
      </div>

      <p v-if="demoMode === false" class="admin-action-hint">Requires your own Google Cloud developer project.</p>

      <div v-if="demoMode === null" class="admin-message" aria-live="polite">
        <i class="pi pi-spin pi-spinner" aria-hidden="true" />
        <span>Checking Google Health availability…</span>
      </div>
      <template v-else-if="demoMode">
        <div class="admin-message" role="status">
          <i class="bi bi-info-circle" aria-hidden="true" />
          <span>
            <strong>{{ syntheticData ? 'Synthetic demo data' : 'Preview only in demo mode.' }}</strong>
            <p v-if="syntheticData">
              Open Health to browse generated daily records and track examples. Settings → Processing shows generation
              progress.
            </p>
            Google Health is available in MTL Explorer, but this public demo cannot connect to, access, or store data
            from a Google account.
          </span>
        </div>
        <div class="gh-admin-card__access" aria-label="Supported Google Health data">
          <span class="admin-action-hint">Available data</span>
          <div>
            <span class="selection-chip">Activity and fitness</span>
            <span class="selection-chip">Health metrics</span>
            <span class="selection-chip">Sleep</span>
            <span class="selection-chip">Exercise routes</span>
          </div>
        </div>
        <div class="admin-action-row gh-admin-card__primary-actions">
          <div class="admin-action-copy">
            <span class="admin-action-label">Google account connection</span>
            <span class="admin-action-hint">Connections and synchronization are unavailable in demo mode.</span>
          </div>
          <Button label="Unavailable in demo" icon="bi bi-lock" size="small" severity="secondary" disabled />
        </div>
      </template>
      <div v-else-if="loading && !status" class="admin-message">
        <i class="pi pi-spin pi-spinner" /><span>Loading Google Health status…</span>
      </div>
      <div v-else-if="loadError && !status" class="admin-message admin-message--error">
        <i class="bi bi-exclamation-triangle" /><span>{{ loadError }}</span>
        <Button label="Retry" size="small" text @click="loadStatus()" />
      </div>

      <template v-else-if="status">
        <div v-if="status.configurationState === 'MISSING_CONFIGURATION'" class="admin-message admin-message--warning">
          <i class="bi bi-sliders" aria-hidden="true" />
          <span>
            <strong>Server setup is required.</strong>
            The setup assistant guides you through Google Cloud and server configuration.
          </span>
          <Button label="Start setup" size="small" @click="openSetup" />
        </div>
        <div v-else-if="status.configurationState === 'DISABLED'" class="admin-message">
          <i class="bi bi-circle" aria-hidden="true" /><span>Google Health is disabled for this installation.</span>
        </div>
        <div v-else-if="authorizationNeedsAction" class="admin-message admin-message--warning" role="alert">
          <i class="bi bi-calendar-week" aria-hidden="true" />
          <span>
            <strong>{{ authorizationActionHeading }}</strong>
            {{ authorizationActionDetail }}
          </span>
          <Button
            label="Reauthorize"
            size="small"
            :loading="setupLoading"
            :disabled="setupLoading || !setupInfo"
            @click="openReauthorization"
          />
        </div>

        <dl class="admin-info-list gh-admin-card__facts">
          <div class="admin-info-row">
            <dt>Last successful sync</dt>
            <dd>{{ formatDate(lastSuccessfulSync) }}</dd>
          </div>
          <div class="admin-info-row">
            <dt>Next regular sync</dt>
            <dd>{{ formatDate(status.nextRegularSync) }}</dd>
          </div>
          <div class="admin-info-row">
            <dt>Authorization expires</dt>
            <dd>
              {{ status.refreshTokenExpiresAt ? formatDate(status.refreshTokenExpiresAt) : 'Not reported by Google' }}
            </dd>
          </div>
        </dl>

        <GoogleHealthSyncNotice :status="status" :show-authorization="false" always />

        <div v-if="displayError" class="admin-message admin-message--error" role="alert">
          <i class="bi bi-exclamation-triangle" /><span>{{ displayError }}</span>
        </div>
        <div v-else-if="actionMessage" class="admin-message admin-message--success" role="status">
          <i class="bi bi-check-circle" /><span>{{ actionMessage }}</span>
        </div>

        <div class="admin-action-row gh-admin-card__primary-actions">
          <div class="admin-action-copy">
            <span class="admin-action-label">Health data</span>
            <span class="admin-action-hint">
              {{
                status.configurationState !== 'CONFIGURED'
                  ? 'Local records remain available. Sync needs an enabled, configured connector.'
                  : status.connectionState === 'CONNECTED'
                    ? 'Browse the local mirror or request new records.'
                    : 'Connect with read-only Google scopes.'
              }}
            </span>
          </div>
          <div class="admin-action-controls">
            <RouterLink v-if="hasLocalMirrorAccess" v-slot="{ navigate }" custom :to="{ name: 'health' }">
              <Button label="Open Health" icon="bi bi-heart-pulse" size="small" @click="navigate" />
            </RouterLink>
            <Button
              v-if="status.connectionState !== 'CONNECTED' && !authorizationNeedsAction"
              :label="setupActionLabel"
              icon="bi bi-list-check"
              size="small"
              :severity="hasLocalMirrorAccess ? 'secondary' : undefined"
              :loading="setupLoading"
              :disabled="Boolean(busyAction) || jobRunning"
              @click="openPrimaryAction"
            />
            <Button
              label="Sync now"
              icon="pi pi-refresh"
              size="small"
              severity="secondary"
              :loading="busyAction === 'sync'"
              :disabled="!canSync"
              @click="runAction('sync', 'Sync requested.', googleHealthApi.syncNow)"
            />
          </div>
        </div>

        <details v-if="grantedCategories.length" class="admin-disclosure">
          <summary>Read access</summary>
          <div class="admin-disclosure__body gh-admin-card__access" aria-label="Granted data categories">
            <div>
              <span v-for="category in grantedCategories" :key="category" class="selection-chip">{{ category }}</span>
            </div>
          </div>
        </details>

        <details class="admin-disclosure gh-admin-card__settings">
          <summary>Advanced</summary>
          <div class="admin-disclosure__body">
            <div class="admin-action-list">
              <div class="admin-action-row">
                <div class="admin-action-copy">
                  <span class="admin-action-label">Server setup</span
                  ><span class="admin-action-hint">Review Google Cloud and server configuration.</span>
                </div>
                <Button
                  label="Review setup"
                  size="small"
                  severity="secondary"
                  :disabled="Boolean(busyAction)"
                  @click="openSetup"
                />
              </div>
              <div class="admin-action-row">
                <div class="admin-action-copy">
                  <span class="admin-action-label">Reconnect</span>
                  <span class="admin-action-hint"
                    >Repeat Google authorization to restore access or change granted categories.</span
                  >
                </div>
                <Button
                  label="Reconnect"
                  icon="pi pi-link"
                  size="small"
                  severity="secondary"
                  :loading="busyAction === 'connect'"
                  :disabled="
                    status.configurationState !== 'CONFIGURED' ||
                    setupLoading ||
                    !setupInfo ||
                    Boolean(busyAction) ||
                    jobRunning
                  "
                  @click="openReauthorization"
                />
              </div>
              <div class="admin-action-row">
                <div class="admin-action-copy">
                  <span class="admin-action-label">Reload history</span>
                  <span class="admin-action-hint"
                    >Recheck all configured days. Recent sync continues while history reloads. Published routes are
                    kept.</span
                  >
                </div>
                <Button
                  label="Start"
                  icon="pi pi-history"
                  size="small"
                  severity="secondary"
                  :loading="busyAction === 'reconcile'"
                  :disabled="!canReload"
                  @click="runAction('reconcile', 'History reload requested.', googleHealthApi.reconcileAll)"
                />
              </div>
              <div class="admin-action-row">
                <div class="admin-action-copy">
                  <span class="admin-action-label">Reload a date range</span>
                  <span class="admin-action-hint"
                    >Use native whole days. The end day is excluded. Existing records stay until each check
                    completes.</span
                  >
                  <div class="gh-reload-range">
                    <label>Start day <input v-model="reloadStart" type="date" /></label>
                    <label>End day, excluded <input v-model="reloadEnd" type="date" /></label>
                  </div>
                </div>
                <Button
                  label="Reload"
                  icon="pi pi-history"
                  size="small"
                  severity="secondary"
                  :disabled="!canReload || !reloadStart || !reloadEnd || reloadStart >= reloadEnd"
                  :loading="busyAction === 'reload-range'"
                  @click="reloadSelectedRange"
                />
              </div>
              <div class="admin-action-row">
                <div class="admin-action-copy">
                  <span class="admin-action-label">Retry failed ranges</span>
                  <span class="admin-action-hint">Release skipped and parked ranges for a fresh attempt.</span>
                </div>
                <Button
                  label="Retry"
                  icon="pi pi-refresh"
                  size="small"
                  severity="secondary"
                  :disabled="!canReload"
                  :loading="busyAction === 'retry-ranges'"
                  @click="runAction('retry-ranges', 'Failed ranges released.', googleHealthApi.retryFailed)"
                />
              </div>
              <div class="admin-action-row">
                <div class="admin-action-copy">
                  <span class="admin-action-label">Disconnect Google</span>
                  <span class="admin-action-hint"
                    >Stop future syncs. Records already stored in MTL Explorer are kept.</span
                  >
                </div>
                <Button
                  label="Disconnect"
                  icon="pi pi-sign-out"
                  size="small"
                  severity="secondary"
                  :loading="busyAction === 'disconnect'"
                  :disabled="status.connectionState === 'DISCONNECTED' || Boolean(busyAction)"
                  @click="runAction('disconnect', 'Google Health disconnected.', googleHealthApi.disconnect)"
                />
              </div>
            </div>

            <section class="admin-card admin-card--danger gh-admin-card__danger" aria-labelledby="gh-delete-heading">
              <div class="admin-card__header">
                <div>
                  <h4 id="gh-delete-heading">Delete local mirror</h4>
                  <p v-if="status.connectionState === 'DISCONNECTED'">
                    Permanently removes {{ status.mirrorSummary?.recordCount?.toLocaleString() ?? 'all' }} mirrored
                    records and all Google Health-managed route files and tracks. Other imported tracks and the original
                    data in Google Health are kept.
                  </p>
                  <p v-else>Disconnect Google Health first so deleted records cannot be synchronized again.</p>
                </div>
              </div>
              <div class="admin-form-row">
                <label class="admin-field" for="gh-delete-confirmation">
                  <span class="admin-action-hint">Enter DELETE to confirm.</span>
                  <InputText
                    id="gh-delete-confirmation"
                    v-model="deleteConfirmation"
                    autocomplete="off"
                    :disabled="status.connectionState !== 'DISCONNECTED'"
                    placeholder="DELETE"
                  />
                </label>
                <Button
                  label="Delete local mirror"
                  icon="pi pi-trash"
                  size="small"
                  severity="danger"
                  :loading="busyAction === 'delete'"
                  :disabled="
                    status.connectionState !== 'DISCONNECTED' ||
                    deleteConfirmation !== 'DELETE' ||
                    Boolean(busyAction) ||
                    jobRunning
                  "
                  @click="deleteMirror"
                />
              </div>
            </section>
          </div>
        </details>
      </template>
    </template>

    <GoogleHealthSetupDialog
      v-if="demoMode === false"
      v-model:visible="setupVisible"
      :count-animation="applicationStatusStore.countAnimations.get(GOOGLE_HEALTH_COUNT_KEY) ?? null"
      :setup="setupInfo"
      :status="status"
      :refreshing="setupLoading"
      :connecting="busyAction === 'connect'"
      :completing="busyAction === 'complete'"
      :oauth-result="oauthResult"
      :oauth-message="oauthResultMessage"
      :setup-error="setupError"
      @refresh="refreshSetup"
      @connect="connect(false)"
      @complete="completeConnection"
      @open-health="openHealth"
    />
    <GoogleHealthReauthorizationDialog
      v-if="demoMode === false"
      v-model:visible="reauthorizationVisible"
      :setup="setupInfo"
      :status="status"
      :connecting="busyAction === 'connect'"
      :completing="busyAction === 'complete'"
      :error="setupError || oauthResultMessage"
      :result="oauthResult"
      @connect="connect(true)"
      @complete="completeConnection"
    />
  </section>
</template>

<script setup lang="ts">
import { storeToRefs } from 'pinia';
import { computed, ref, watch } from 'vue';
import { RouterLink, useRoute, useRouter } from 'vue-router';
import Button from 'primevue/button';
import InputText from 'primevue/inputtext';
import { googleHealthApi, type GHSetupResponse } from '../../api/googleHealthApi';
import GoogleHealthSyncNotice from '../../components/GoogleHealthSyncNotice.vue';
import { GOOGLE_HEALTH_REAUTHORIZATION_QUERY, GOOGLE_HEALTH_SETTINGS_ROUTE } from '../../utils/googleHealthNavigation';
import {
  GOOGLE_HEALTH_BETA_NOTICE,
  GOOGLE_HEALTH_FEATURE_LABEL,
  googleHealthStatusPresentation,
  googleHealthReauthorizationExplanation,
} from '../../utils/googleHealthPresentation';
import { formatHealthDateTime as formatDate } from '../../utils/healthDateTime';
import GoogleHealthReauthorizationDialog from './GoogleHealthReauthorizationDialog.vue';
import GoogleHealthSetupDialog from './GoogleHealthSetupDialog.vue';
import { APPLICATION_STATUS_SECTION, useApplicationStatusStore } from '@/stores/applicationStatusStore';
import { GOOGLE_HEALTH_COUNT_KEY } from '@/utils/countAnimation';

const REAUTHORIZATION_SESSION_KEY = 'mtl.google-health-reauthorizing';
const props = withDefaults(
  defineProps<{ active: boolean; demoMode?: boolean | null; detail?: boolean; syntheticData?: boolean }>(),
  {
    demoMode: null,
    detail: false,
    syntheticData: false,
  }
);
const route = useRoute();
const router = useRouter();
const applicationStatusStore = useApplicationStatusStore();
const { googleHealth: status } = storeToRefs(applicationStatusStore);
const setupInfo = ref<GHSetupResponse | null>(null);
const loading = computed(() => applicationStatusStore.sectionStates[APPLICATION_STATUS_SECTION.GOOGLE_HEALTH].loading);
const setupLoading = ref(false);
const loadError = computed(
  () => applicationStatusStore.sectionStates[APPLICATION_STATUS_SECTION.GOOGLE_HEALTH].lastError ?? ''
);
const setupError = ref('');
const actionError = ref('');
const actionMessage = ref('');
const busyAction = ref('');
const deleteConfirmation = ref('');
const reloadStart = ref('');
const reloadEnd = ref('');
const setupVisible = ref(false);
const reauthorizationVisible = ref(false);
const oauthResult = ref('');
const oauthResultMessage = ref('');

const jobRunning = computed(() => ['QUEUED', 'RUNNING'].includes(status.value?.job?.state ?? ''));
const canSync = computed(
  () =>
    props.demoMode === false &&
    status.value?.configurationState === 'CONFIGURED' &&
    status.value?.connectionState === 'CONNECTED' &&
    !busyAction.value &&
    !jobRunning.value
);
const canReload = computed(
  () =>
    props.demoMode === false &&
    status.value?.configurationState === 'CONFIGURED' &&
    status.value?.connectionState === 'CONNECTED' &&
    !busyAction.value
);
const lastSuccessfulSync = computed(() => {
  const values = [
    ...(status.value?.syncStates ?? []).map((state) => state.lastSuccessAt),
    ...(status.value?.synchronization?.streams ?? []).map((stream) => stream.lastFastCompletedAt),
  ].filter((value): value is Date => value instanceof Date);
  return values.length ? new Date(Math.max(...values.map((value) => value.getTime()))) : undefined;
});
const displayError = computed(() => actionError.value || setupError.value || loadError.value || '');
const grantedCategories = computed(() => {
  const names: Record<string, string> = {
    activityAndFitness: 'Activity and fitness',
    healthMetricsAndMeasurements: 'Health metrics',
    sleep: 'Sleep',
    location: 'Location',
  };
  return Object.entries(status.value?.grantedCategories ?? {})
    .filter(([, granted]) => granted)
    .map(([key]) => names[key] ?? key);
});
const statusPresentation = computed(() => {
  if (props.demoMode === true && !props.syntheticData)
    return { label: 'Demo preview', className: '', icon: 'bi bi-heart-pulse' };
  return googleHealthStatusPresentation(status.value, {
    loading: loading.value,
    connectionOnly: true,
    syntheticData: props.syntheticData,
  });
});
const statusLabel = computed(() => statusPresentation.value.label);
const statusClass = computed(() => statusPresentation.value.className);
const statusIcon = computed(() => statusPresentation.value.icon);
const connectionSummary = computed(() => {
  if (props.demoMode === null) return 'Checking availability…';
  if (props.demoMode)
    return props.syntheticData ? 'Synthetic demo data · No Google account connected' : 'Preview only in demo mode';
  if (loading.value && !status.value) return 'Loading connection status…';
  if (!status.value) return 'Connection status unavailable';
  if (authorizationNeedsAction.value) return statusLabel.value;
  if (status.value.configurationState === 'DISABLED') return 'Disabled for this installation';
  if (status.value.configurationState === 'MISSING_CONFIGURATION') return 'Server setup required';
  if (status.value.connectionState !== 'CONNECTED') return 'Not connected';
  return lastSuccessfulSync.value
    ? `Connected · Last synced ${formatDate(lastSuccessfulSync.value)}`
    : 'Connected · Waiting for the first sync';
});
const hasLocalMirrorAccess = computed(
  () => status.value?.connectionState === 'CONNECTED' || (status.value?.mirrorSummary?.recordCount ?? 0) > 0
);
const setupActionLabel = computed(() => {
  if (authorizationNeedsAction.value) return 'Reauthorize';
  if (status.value?.connectionState === 'CONNECTED') return 'Review setup';
  if (setupInfo.value?.configurationState === 'MISSING_CONFIGURATION') return 'Continue setup';
  return 'Set up connection';
});
const authorizationNeedsAction = computed(
  () =>
    status.value?.configurationState === 'CONFIGURED' &&
    (status.value.authorizationAction === 'REAUTHORIZE' ||
      status.value.authorizationAction === 'EXPIRING_SOON' ||
      status.value.connectionState === 'RECONNECT_REQUIRED')
);
const authorizationExplanation = computed(() => googleHealthReauthorizationExplanation(status.value));
const authorizationActionHeading = computed(
  () => authorizationExplanation.value?.heading ?? 'Google Health authorization expires soon.'
);
const authorizationActionDetail = computed(() => {
  const expiry = status.value?.refreshTokenExpiresAt;
  if (status.value?.authorizationAction === 'EXPIRING_SOON' && expiry)
    return `Google reported that access expires on ${formatDate(expiry)}. Confirm access again before then to keep syncing.`;
  return authorizationExplanation.value?.detail ?? 'Confirm access again before authorization expires.';
});

watch(
  () => [props.active, props.demoMode, route.query[GOOGLE_HEALTH_REAUTHORIZATION_QUERY], status.value] as const,
  ([active, demoMode, reauthorize, currentStatus]) => {
    if (!active || demoMode !== false || reauthorize !== 'open' || !currentStatus) return;
    if (authorizationNeedsAction.value) openReauthorization();
    const query = { ...route.query };
    delete query[GOOGLE_HEALTH_REAUTHORIZATION_QUERY];
    void router.replace({ path: route.path, query });
  },
  { immediate: true }
);

watch(
  () => [props.active, props.demoMode] as const,
  ([active, demoMode]) => {
    if (active && demoMode !== null) {
      if (!status.value) void loadStatus();
    }
  },
  { immediate: true }
);
watch(
  () => [props.active, props.demoMode, props.detail] as const,
  ([active, demoMode, detail]) => {
    if (active && demoMode === false && detail) void loadSetupInfo();
  },
  { immediate: true }
);
watch(
  () => [props.active, props.demoMode, route.query.googleHealthSetup, route.query.googleHealth] as const,
  ([active, demoMode, setupQuery, resultQuery]) => {
    if (!active || demoMode !== false || setupQuery !== 'open') return;
    void loadSetupInfo();
    oauthResult.value = typeof resultQuery === 'string' ? resultQuery : '';
    oauthResultMessage.value = '';
    const reauthorizing = window.sessionStorage.getItem(REAUTHORIZATION_SESSION_KEY) === 'true';
    window.sessionStorage.removeItem(REAUTHORIZATION_SESSION_KEY);
    if (reauthorizing) reauthorizationVisible.value = true;
    else setupVisible.value = true;
    const query = { ...route.query };
    delete query.googleHealthSetup;
    delete query.googleHealth;
    void router.replace({ path: route.path, query });
  },
  { immediate: true }
);

async function reloadSelectedRange() {
  await runAction('reload-range', 'Date range reload requested.', () =>
    googleHealthApi.reloadRange(reloadStart.value, reloadEnd.value)
  );
}

async function loadStatus() {
  await applicationStatusStore.force(APPLICATION_STATUS_SECTION.GOOGLE_HEALTH);
}
async function loadSetupInfo() {
  setupLoading.value = true;
  setupError.value = '';
  try {
    setupInfo.value = await googleHealthApi.setup();
  } catch (error) {
    setupError.value = error instanceof Error ? error.message : 'Could not load Google Health setup information.';
  } finally {
    setupLoading.value = false;
  }
}
async function refreshSetup() {
  await Promise.all([loadStatus(), loadSetupInfo()]);
}
async function runAction(name: string, successMessage: string, action: () => Promise<unknown>) {
  if (props.demoMode !== false || (name === 'sync' && !canSync.value)) return;
  busyAction.value = name;
  actionError.value = '';
  actionMessage.value = '';
  try {
    await action();
    actionMessage.value = successMessage;
    await loadStatus();
  } catch (error) {
    actionError.value = error instanceof Error ? error.message : 'The Google Health action failed.';
  } finally {
    busyAction.value = '';
  }
}
async function connect(reauthorize = false) {
  if (props.demoMode !== false || !requireConfiguredAuthorization()) return;
  busyAction.value = 'connect';
  actionError.value = '';
  setupError.value = '';
  oauthResult.value = '';
  oauthResultMessage.value = '';
  const expectsManual = setupInfo.value?.oauthMode === 'MANUAL';
  const authorizationWindow = expectsManual ? window.open('about:blank', 'mtl-google-health-oauth') : null;
  if (authorizationWindow) authorizationWindow.opener = null;
  try {
    const authorization = await googleHealthApi.connect(reauthorize);
    if (authorization.oauthMode === 'PROXY') {
      authorizationWindow?.close();
      if (reauthorize) window.sessionStorage.setItem(REAUTHORIZATION_SESSION_KEY, 'true');
      else window.sessionStorage.removeItem(REAUTHORIZATION_SESSION_KEY);
      window.location.assign(authorization.authorizationUrl);
      return;
    }
    const target = authorizationWindow ?? window.open('about:blank', 'mtl-google-health-oauth');
    if (!target) throw new Error('Allow pop-ups for MTL Explorer, then open Google consent again.');
    target.opener = null;
    target.location.replace(authorization.authorizationUrl);
  } catch (error) {
    window.sessionStorage.removeItem(REAUTHORIZATION_SESSION_KEY);
    authorizationWindow?.close();
    setupError.value = error instanceof Error ? error.message : 'Could not start Google authorization.';
  } finally {
    busyAction.value = '';
  }
}
async function completeConnection(redirectUrl: string) {
  if (props.demoMode !== false || !requireConfiguredAuthorization()) return;
  busyAction.value = 'complete';
  setupError.value = '';
  oauthResult.value = '';
  oauthResultMessage.value = '';
  try {
    const result = await googleHealthApi.completeConnection(redirectUrl);
    oauthResult.value = result.result.toLowerCase();
    oauthResultMessage.value = result.message ?? '';
    if (result.result === 'CONNECTED') await loadStatus();
  } catch (error) {
    setupError.value = error instanceof Error ? error.message : 'Could not complete Google authorization.';
  } finally {
    busyAction.value = '';
  }
}
function requireConfiguredAuthorization(): boolean {
  if (status.value?.configurationState === 'CONFIGURED') return true;
  setupError.value = 'Enable and configure Google Health on the server before authorizing access.';
  return false;
}
function openSetup() {
  if (props.demoMode !== false) return;
  oauthResult.value = '';
  oauthResultMessage.value = '';
  setupError.value = '';
  setupVisible.value = true;
  void loadSetupInfo();
}
function openReauthorization() {
  if (props.demoMode !== false) return;
  oauthResult.value = '';
  oauthResultMessage.value = '';
  setupError.value = '';
  reauthorizationVisible.value = true;
  void loadSetupInfo();
}
function openPrimaryAction() {
  if (authorizationNeedsAction.value) openReauthorization();
  else openSetup();
}
async function openHealth() {
  setupVisible.value = false;
  await router.push({ name: 'health' });
}
async function deleteMirror() {
  if (props.demoMode !== false) return;
  await runAction('delete', 'The local Google Health mirror was deleted.', () =>
    googleHealthApi.deleteMirror(deleteConfirmation.value)
  );
  if (!actionError.value) deleteConfirmation.value = '';
}
</script>

<style scoped>
.gh-reload-range {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
  margin-top: 0.5rem;
}
.gh-reload-range label {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}
.gh-reload-range input {
  color: var(--text-primary);
  background: var(--surface-elevated);
  border: 1px solid var(--border-default);
  border-radius: 4px;
  padding: 0.35rem;
}

.gh-connection-row .admin-action-copy {
  min-width: 0;
}
.gh-connection-row h3 {
  margin: 0;
}
.gh-connection-row .admin-action-hint {
  overflow-wrap: anywhere;
}
.gh-admin-card {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
}
.gh-admin-card > .admin-card__header {
  margin-bottom: 0;
}
.gh-admin-card__title {
  display: flex;
  align-items: flex-start;
  min-width: 0;
  gap: 0.65rem;
}
.gh-admin-card__facts {
  display: grid;
  grid-template-columns: 1fr;
}
.gh-admin-card__facts .admin-info-row {
  padding-inline: 0.6rem;
}
.gh-admin-card__facts .admin-info-row:first-child {
  border-top: 0;
}
.gh-admin-card__access {
  display: flex;
  align-items: center;
  gap: 0.65rem;
}
.gh-admin-card__access > div {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
}
.gh-admin-card__primary-actions {
  padding-block: 0;
  border-top: 0;
}
.gh-admin-card__settings {
  margin-top: 0.15rem;
}
.gh-admin-card__danger {
  margin-top: 0.8rem;
}
.gh-admin-card__danger .admin-field {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}
.gh-admin-card__danger :deep(.p-inputtext) {
  width: 100%;
}
.admin-message :deep(.p-button) {
  flex: 0 0 auto;
  margin-left: auto;
}
code {
  user-select: text;
  -webkit-user-select: text;
}

@media (max-width: 768px) {
  .gh-admin-card__facts {
    grid-template-columns: 1fr;
  }
  .gh-admin-card__facts .admin-info-row:nth-child(2) {
    border-top: 1px solid var(--border-subtle);
  }
  .gh-admin-card__primary-actions {
    align-items: stretch;
    flex-direction: column;
  }
  .gh-admin-card__primary-actions .admin-action-controls {
    width: 100%;
  }
  .gh-admin-card__primary-actions .admin-action-controls :deep(.p-button) {
    flex: 1 1 0;
  }
  .gh-admin-card__access {
    align-items: flex-start;
    flex-direction: column;
    gap: 0.35rem;
  }
}
</style>
