<template>
  <BottomSheet
    v-model="isOpen"
    title="Settings"
    :icon="NAVIGATION_TOOL_ICONS.settings"
    :detents="[
      { id: 'comfortable', height: activeSection === 'processing' ? 'min(850px, 88vh)' : 'min(710px, 82vh)' },
      { id: 'large', height: '92vh' },
    ]"
    initial-detent="comfortable"
    :content-key="activeSection"
    sheet-class="admin-center-sheet"
    @closed="onSheetClosed"
  >
    <div class="admin-center-root">
      <div class="admin-center-layout">
        <nav class="admin-sidebar" aria-label="Settings sections">
          <button
            type="button"
            :class="['admin-nav-item', { 'admin-nav-item--active': activeSection === 'overview' }]"
            :aria-current="activeSection === 'overview' ? 'page' : undefined"
            @click="navigateTo('overview')"
          >
            <span class="admin-nav-item__icon"><i class="bi bi-grid-1x2" /></span>
            <span class="admin-nav-item__text">Overview</span>
          </button>

          <section v-for="group in sectionGroups" :key="group.id" class="admin-sidebar__group">
            <span class="admin-sidebar__label">{{ group.label }}</span>
            <button
              v-for="section in sectionsForGroup(group.id)"
              :key="section.id"
              type="button"
              :class="['admin-nav-item', { 'admin-nav-item--active': activeSection === section.id }]"
              :aria-current="activeSection === section.id ? 'page' : undefined"
              @click="navigateTo(section.id)"
            >
              <span class="admin-nav-item__icon"><i :class="section.icon" /></span>
              <span class="admin-nav-item__text">{{ section.label }}</span>
              <span
                v-if="sectionBadges[section.id]"
                :class="['admin-nav-badge', { 'admin-nav-badge--live': section.id === 'processing' && isProcessing }]"
              >
                {{ sectionBadges[section.id] }}
              </span>
            </button>
          </section>

          <div class="admin-sidebar__footer">
            <button type="button" class="admin-nav-item" @click="showAbout = true">
              <span class="admin-nav-item__icon"><i class="bi bi-book" /></span>
              <span class="admin-nav-item__text">About &amp; credits</span>
              <i class="bi bi-chevron-right" aria-hidden="true" />
            </button>
          </div>
        </nav>

        <main class="admin-content" aria-label="Settings content">
          <button
            v-if="activeSection !== 'overview' && !googleHealthDetailOpen"
            type="button"
            class="admin-mobile-back"
            @click="navigateTo('overview')"
          >
            <i class="bi bi-arrow-left" /> All settings
          </button>

          <div ref="contentScroller" class="admin-content-scroll" data-sheet-fit-target>
            <AdminOverview
              v-show="activeSection === 'overview'"
              data-admin-section="overview"
              :cards="overviewCards"
              :badges="sectionBadges"
              :refreshing="overviewRefreshing"
              @navigate="navigateTo"
              @refresh="refreshOverview(true)"
              @show-about="showAbout = true"
            />
            <AdminImportSyncSection
              v-show="activeSection === 'imports'"
              data-admin-section="imports"
              :active="isOpen && activeSection === 'imports'"
              :demo-mode="demoMode"
              :synthetic-data="syntheticHealthData"
            />
            <AdminProcessingSection
              v-show="activeSection === 'processing'"
              data-admin-section="processing"
              :google-health-status="googleHealthStatus"
              :google-health-status-error="googleHealthStatusError"
              :google-health-count-animation="
                applicationStatusStore.countAnimations.get(GOOGLE_HEALTH_COUNT_KEY) ?? null
              "
              :demo-mode="demoMode"
            />
            <AdminDataStatusSection
              v-show="activeSection === 'data-status'"
              data-admin-section="data-status"
              @refresh-data="onRefreshFreshnessData"
            />
            <AdminMaintenanceSection
              v-show="activeSection === 'maintenance'"
              :demo-mode="demoMode"
              data-admin-section="maintenance"
              :tool-status="toolStatus"
              :tool-status-loading="toolStatusLoading"
              :tool-status-error="toolStatusError"
              @reload-tracks="onReloadTracks"
              @refresh-tool-status="loadToolStatus"
            />
            <AdminServerLogSection
              v-show="activeSection === 'logs'"
              data-admin-section="logs"
              :active="isOpen && activeSection === 'logs'"
            />
            <AdminSystemInformationSection
              v-show="activeSection === 'system'"
              data-admin-section="system"
              :server-build="serverBuild"
              :operational-tasks="operationalTasks"
              :loading="serverBuildLoading"
              :error="serverBuildError"
              :services="applicationStatusStore.services"
              :services-loading="
                applicationStatusStore.sectionStates[APPLICATION_STATUS_SECTION.SERVICES].loading ||
                applicationStatusStore.sectionStates[APPLICATION_STATUS_SECTION.SERVICES].lastAttemptAt === null
              "
              :services-unavailable="!applicationStatusStore.sectionStates[APPLICATION_STATUS_SECTION.SERVICES].healthy"
            />
            <AdminPreferencesSection v-show="activeSection === 'preferences'" data-admin-section="preferences" />
            <section v-if="activeSection === 'updates'" data-admin-section="updates" class="admin-page">
              <AdminSectionHeader
                title="Updates"
                description="Release information for this installation."
                icon="bi bi-arrow-up-circle"
              />
              <ReleaseUpdateDetails />
            </section>
            <AdminSessionSection v-show="activeSection === 'session'" data-admin-section="session" />
          </div>
        </main>
      </div>
    </div>
  </BottomSheet>

  <AboutView
    v-if="showAbout"
    embedded
    :viewport-centered="false"
    @closed="showAbout = false"
    @show-system-information="showSystemInformation"
  />
</template>

<script setup lang="ts">
import { formatLocaleNumber } from '@/utils/Utils';
import { warnRequestFailure } from '@/utils/safeLogging';
import { storeToRefs } from 'pinia';
import { computed, defineAsyncComponent, nextTick, onBeforeUnmount, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import BottomSheet from '@/components/ui/BottomSheet.vue';
import { NAVIGATION_TOOL_ICONS } from '@/components/ui/navigationToolIcons';
import AdminOverview from '@/components/admin/AdminOverview.vue';
import AdminImportSyncSection from '@/components/admin/AdminImportSyncSection.vue';
import AdminProcessingSection from '@/components/admin/AdminProcessingSection.vue';
import AdminDataStatusSection from '@/components/admin/AdminDataStatusSection.vue';
import AdminMaintenanceSection from '@/components/admin/AdminMaintenanceSection.vue';
import AdminServerLogSection from '@/components/admin/AdminServerLogSection.vue';
import AdminSystemInformationSection from '@/components/admin/AdminSystemInformationSection.vue';
import AdminPreferencesSection from '@/components/admin/AdminPreferencesSection.vue';
import AdminSessionSection from '@/components/admin/AdminSessionSection.vue';
import AdminSectionHeader from '@/components/admin/AdminSectionHeader.vue';
import ReleaseUpdateDetails from '@/components/info/ReleaseUpdateDetails.vue';
import { useReleaseUpdatesStore } from '@/stores/releaseUpdatesStore';
import {
  ADMIN_SECTION_GROUPS,
  parseAdminSection,
  sectionsForGroup,
  type AdminOverviewStatus,
  type AdminSectionId,
} from '@/components/admin/adminSections';
import { useIndexerStatus } from '@/composables/useIndexerStatus';
import { useDataFreshnessStore } from '@/stores/dataFreshnessStore';
import { useDemoAccessStore } from '@/stores/demoAccessStore';
import {
  APPLICATION_STATUS_SECTION,
  type ApplicationStatusPollingConsumer,
  useApplicationStatusStore,
} from '@/stores/applicationStatusStore';
import { getGarminToolStatus, getServerBuildInfo, type BuildInfo, type GarminToolStatus } from '@/utils/ServiceHelper';
import '@/components/admin/adminCenter.css';
import {
  GOOGLE_HEALTH_CONNECTION_QUERY,
  GOOGLE_HEALTH_CONNECTION_ID,
  GOOGLE_HEALTH_PROCESSING_ID,
} from '@/features/health/utils/googleHealthNavigation';
import { hasPendingGoogleHealthHistory } from '@/features/health/utils/googleHealthPresentation';
import { GOOGLE_HEALTH_COUNT_KEY } from '@/utils/countAnimation';
import { STATUS_POLL_INTERVAL_MS } from '@/utils/statusPolling';

defineOptions({ name: 'AdminDialog' });

const emit = defineEmits<{
  (event: 'tool-opened'): void;
  (event: 'tool-closed'): void;
  (event: 'reload-tracks', done: (success?: boolean, message?: string) => void): void;
  (event: 'refresh-freshness-data', done: (success?: boolean) => void): void;
}>();

const SYNTHETIC_HEALTH_JOB_ID = 'demoHealth';
const EMPTY_TOOL_STATUS: GarminToolStatus = {
  gcexportConfiguredVersion: '…',
  gcexportVenvPresent: false,
  fitExportConfiguredProfile: '…',
  fitExportConfiguredPackages: '…',
  fitExportVenvPresent: false,
};
const AboutView = defineAsyncComponent(() => import('@/views/AboutView.vue'));
const route = useRoute();
const router = useRouter();
const dataFreshnessStore = useDataFreshnessStore();
const releaseUpdates = useReleaseUpdatesStore();
const applicationStatusStore = useApplicationStatusStore();
const { googleHealth: googleHealthStatus } = storeToRefs(applicationStatusStore);
const {
  summaries,
  jobSummaries,
  operationalTasks,
  isIndexerStatusPollingHealthy,
  isIndexing,
  isJobPending,
  isOperationalTaskActive,
  setFastPolling,
} = useIndexerStatus();

const isOpen = ref(false);
const showAbout = ref(false);
const activeSection = ref<AdminSectionId>('overview');
const googleHealthDetailOpen = computed(
  () => activeSection.value === 'imports' && route.query[GOOGLE_HEALTH_CONNECTION_QUERY] === GOOGLE_HEALTH_CONNECTION_ID
);
const contentScroller = ref<HTMLElement | null>(null);
const toolStatus = ref<GarminToolStatus>({ ...EMPTY_TOOL_STATUS });
const toolStatusLoading = ref(false);
const toolStatusError = ref('');
const serverBuild = ref<BuildInfo | null>(null);
const serverBuildLoading = ref(false);
const serverBuildError = ref('');
const overviewRefreshing = ref(false);
const demoAccess = useDemoAccessStore();
const { demoMode, syntheticHealthData } = storeToRefs(demoAccess);
const sectionGroups = ADMIN_SECTION_GROUPS;
let settingsStatusConsumer: ApplicationStatusPollingConsumer | null = null;

const googleHealthStatusError = computed(
  () => applicationStatusStore.sectionStates[APPLICATION_STATUS_SECTION.GOOGLE_HEALTH].lastError ?? ''
);

const googleHealthJobRunning = computed(() =>
  ['QUEUED', 'RUNNING'].includes(googleHealthStatus.value?.job?.state ?? '')
);
const googleHealthHistoryPending = computed(
  () =>
    demoMode.value === false &&
    googleHealthStatus.value?.configurationState === 'CONFIGURED' &&
    googleHealthStatus.value?.connectionState === 'CONNECTED' &&
    hasPendingGoogleHealthHistory(googleHealthStatus.value)
);
const isProcessing = computed(
  () => isIndexing.value || isJobPending.value || isOperationalTaskActive.value || googleHealthJobRunning.value
);
const pendingWorkCount = computed(
  () =>
    summaries.value.reduce((total, summary) => total + summary.pending, 0) +
    jobSummaries.value.reduce((total, summary) => total + summary.pending, 0) +
    operationalTasks.value.filter((task) => task.active).length +
    (googleHealthJobRunning.value || googleHealthHistoryPending.value ? 1 : 0)
);
const serviceWarningCount = computed(() => operationalTasks.value.filter((task) => task.state === 'warning').length);
const activeServiceCount = computed(() => operationalTasks.value.filter((task) => task.active).length);
const readyServiceCount = computed(
  () => operationalTasks.value.filter((task) => task.state === 'done' || task.state === 'running').length
);
const readyToolCount = computed(
  () => [toolStatus.value.gcexportVenvPresent, toolStatus.value.fitExportVenvPresent].filter(Boolean).length
);
const missingToolCount = computed(() => 2 - readyToolCount.value);

const sectionBadges = computed<Partial<Record<AdminSectionId, string>>>(() => {
  const badges: Partial<Record<AdminSectionId, string>> = {};
  if (releaseUpdates.hasUpdate) badges.updates = 'Available';
  else if (releaseUpdates.browserUpdateAvailable) badges.updates = 'Reload';
  if (!isIndexerStatusPollingHealthy.value) badges.processing = '!';
  else if (isProcessing.value) badges.processing = 'Live';
  else if (pendingWorkCount.value > 0) badges.processing = 'Pending';
  if (!dataFreshnessStore.isFreshnessPollingHealthy) badges['data-status'] = '!';
  else if (dataFreshnessStore.isOutOfSync) badges['data-status'] = 'Stale';
  if (toolStatusError.value) badges.maintenance = '!';
  else if (!toolStatusLoading.value && missingToolCount.value > 0) badges.maintenance = String(missingToolCount.value);
  if (
    demoMode.value === false &&
    (googleHealthStatusError.value ||
      googleHealthStatus.value?.configurationState === 'MISSING_CONFIGURATION' ||
      googleHealthStatus.value?.authorizationAction === 'REAUTHORIZE' ||
      googleHealthStatus.value?.authorizationAction === 'EXPIRING_SOON')
  )
    badges.imports = '!';
  return badges;
});

const overviewCards = computed<AdminOverviewStatus[]>(() => [
  processingOverview.value,
  dataOverview.value,
  servicesOverview.value,
  googleHealthOverview.value,
  helpersOverview.value,
]);

const processingOverview = computed<AdminOverviewStatus>(() => {
  if (!applicationStatusStore.sectionStates[APPLICATION_STATUS_SECTION.PROCESSING].healthy) {
    return {
      id: 'processing',
      label: 'Processing',
      value: 'Unavailable',
      detail: 'Indexer and job status could not be refreshed.',
      icon: 'bi bi-list-check',
      section: 'processing',
      tone: 'error',
    };
  }
  return {
    id: 'processing',
    label: 'Processing',
    value: isProcessing.value ? 'Active' : pendingWorkCount.value > 0 ? 'Pending' : 'Idle',
    detail:
      pendingWorkCount.value > 0
        ? `${formatLocaleNumber(pendingWorkCount.value)} active or pending`
        : 'No background work is active.',
    icon: 'bi bi-list-check',
    section: 'processing',
    tone: isProcessing.value ? 'live' : pendingWorkCount.value > 0 ? 'neutral' : 'success',
  };
});

const dataOverview = computed<AdminOverviewStatus>(() => {
  if (!dataFreshnessStore.isFreshnessPollingHealthy) {
    return {
      id: 'data',
      label: 'Data',
      value: 'Unavailable',
      detail: 'The server revision check failed.',
      icon: 'bi bi-database-check',
      section: 'data-status',
      tone: 'error',
    };
  }
  if (!dataFreshnessStore.currentFreshness) {
    return {
      id: 'data',
      label: 'Data',
      value: 'Checking',
      detail: 'Loading the current server revision.',
      icon: 'bi bi-database-check',
      section: 'data-status',
      tone: 'neutral',
    };
  }
  return {
    id: 'data',
    label: 'Data',
    value: dataFreshnessStore.isOutOfSync ? 'Reload needed' : 'Current',
    detail: dataFreshnessStore.isOutOfSync
      ? 'Server data changed after this view loaded.'
      : 'Browser data matches the applied revision.',
    icon: 'bi bi-database-check',
    section: 'data-status',
    tone: dataFreshnessStore.isOutOfSync ? 'warning' : 'success',
  };
});

const servicesOverview = computed<AdminOverviewStatus>(() => {
  const total = operationalTasks.value.length;
  if (!applicationStatusStore.sectionStates[APPLICATION_STATUS_SECTION.SERVICES].healthy) {
    return {
      id: 'services',
      label: 'Services',
      value: 'Unavailable',
      detail: 'Map, routing, and search status could not be refreshed.',
      icon: 'bi bi-boxes',
      section: 'processing',
      tone: 'error',
    };
  }
  return {
    id: 'services',
    label: 'Services',
    value: total ? `${readyServiceCount.value}/${total} available` : 'No status',
    detail: serviceWarningCount.value
      ? `${serviceWarningCount.value} service warning${serviceWarningCount.value === 1 ? '' : 's'}.`
      : activeServiceCount.value
        ? `${activeServiceCount.value} service task${activeServiceCount.value === 1 ? '' : 's'} active.`
        : 'No service warnings.',
    icon: 'bi bi-boxes',
    section: 'processing',
    tone: serviceWarningCount.value ? 'warning' : activeServiceCount.value ? 'live' : 'success',
  };
});

const helpersOverview = computed<AdminOverviewStatus>(() => {
  if (toolStatusError.value) {
    return {
      id: 'helpers',
      label: 'Helper tools',
      value: 'Unavailable',
      detail: toolStatusError.value,
      icon: 'bi bi-wrench-adjustable',
      section: 'maintenance',
      tone: 'error',
    };
  }
  return {
    id: 'helpers',
    label: 'Helper tools',
    value: toolStatusLoading.value ? 'Checking' : `${readyToolCount.value}/2 ready`,
    detail: toolStatusLoading.value
      ? 'Loading helper installation status.'
      : missingToolCount.value
        ? `${missingToolCount.value} helper ${missingToolCount.value === 1 ? 'environment is' : 'environments are'} missing.`
        : 'Both export helpers are ready.',
    icon: 'bi bi-wrench-adjustable',
    section: 'maintenance',
    tone: toolStatusLoading.value ? 'neutral' : missingToolCount.value ? 'warning' : 'success',
  };
});

const googleHealthOverview = computed<AdminOverviewStatus>(() => {
  const status = googleHealthStatus.value;
  if (demoMode.value === true && !syntheticHealthData.value) {
    return {
      id: 'google-health',
      label: 'Google Health',
      value: 'Demo preview',
      detail: 'Connections are unavailable in this public demo.',
      icon: 'bi bi-heart-pulse',
      section: 'imports',
      tone: 'neutral',
    };
  }
  if (googleHealthStatusError.value) {
    return {
      id: 'google-health',
      label: 'Google Health',
      value: 'Unavailable',
      detail: googleHealthStatusError.value,
      icon: 'bi bi-heart-pulse',
      section: 'imports',
      tone: 'error',
    };
  }
  if (!status) {
    return {
      id: 'google-health',
      label: 'Google Health',
      value: 'Checking',
      detail: 'Loading connection and authorization status.',
      icon: 'bi bi-heart-pulse',
      section: 'imports',
      tone: 'neutral',
    };
  }
  if (syntheticHealthData.value) {
    const card: AdminOverviewStatus = {
      id: 'google-health',
      label: 'Google Health',
      value: 'Checking',
      detail: 'Loading synthetic health generation status.',
      icon: 'bi bi-heart-pulse',
      section: 'processing',
      tone: 'neutral',
    };
    if (!applicationStatusStore.sectionStates[APPLICATION_STATUS_SECTION.PROCESSING].healthy) {
      return {
        ...card,
        value: 'Unavailable',
        detail: 'Health generation status could not be refreshed.',
        tone: 'error',
      };
    }
    const generation = jobSummaries.value.find((job) => job.job === SYNTHETIC_HEALTH_JOB_ID);
    if (!generation) return card;
    if (generation.failed > 0 || generation.unavailable > 0) {
      return {
        ...card,
        value: 'Generation needs attention',
        detail: 'Some synthetic health data could not be generated. Open Processing for details.',
        tone: 'warning',
      };
    }
    if (generation.pending > 0) {
      return {
        ...card,
        value: 'Preparing synthetic health data',
        detail: 'Generation is still pending. Open Processing to view progress.',
        tone: 'live',
      };
    }
    return generation.done > 0
      ? {
          ...card,
          value: 'Synthetic demo data',
          detail: 'Generated health records and track examples are available in Health.',
        }
      : { ...card, value: 'No health data', detail: 'No synthetic health data has been generated.' };
  }
  if (status.configurationState === 'DISABLED') {
    return {
      id: 'google-health',
      label: 'Google Health',
      value: 'Disabled',
      detail: 'The connector is disabled for this installation.',
      icon: 'bi bi-heart-pulse',
      section: 'imports',
      tone: 'neutral',
    };
  }
  if (status.configurationState === 'MISSING_CONFIGURATION') {
    return {
      id: 'google-health',
      label: 'Google Health',
      value: 'Setup required',
      detail: 'Add the Desktop OAuth client values to start the connection.',
      icon: 'bi bi-heart-pulse',
      section: 'imports',
      tone: 'warning',
    };
  }
  if (['QUEUED', 'RUNNING'].includes(status.job?.state ?? '')) {
    const total = status.job?.totalUnits ?? 0;
    const completed = status.job?.completedUnits ?? 0;
    return {
      id: 'google-health',
      label: 'Google Health',
      value: status.job?.state === 'QUEUED' ? 'Queued' : 'Syncing',
      detail:
        total > 0
          ? `Current pass: ${completed} of ${total} steps. History may still be pending.`
          : 'Preparing sync work.',
      icon: 'bi bi-heart-pulse',
      section: 'processing',
      tone: 'live',
    };
  }
  if (status.authorizationAction === 'REAUTHORIZE' || status.authorizationAction === 'EXPIRING_SOON') {
    const expiry = status.refreshTokenExpiresAt
      ? new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(status.refreshTokenExpiresAt)
      : '';
    return {
      id: 'google-health',
      label: 'Google Health',
      value: status.authorizationAction === 'EXPIRING_SOON' ? 'Expires soon' : 'Reauthorize',
      detail: expiry
        ? `Testing-mode access expires on ${expiry}.`
        : 'Confirm Google access again to resume regular sync.',
      icon: 'bi bi-heart-pulse',
      section: 'imports',
      tone: 'warning',
    };
  }
  if (status.connectionState === 'CONNECTED' && hasPendingGoogleHealthHistory(status)) {
    const needsRetry =
      status.syncStates?.some((state) => Boolean(state.lastError)) ||
      ['FAILED', 'COMPLETED_WITH_ERRORS'].includes(status.job?.state ?? '');
    return {
      id: 'google-health',
      label: 'Google Health',
      value: needsRetry ? 'Sync needs attention' : 'History pending',
      detail: 'Older health data is not fully imported. Open processing for coverage and sync status.',
      icon: 'bi bi-clock-history',
      section: 'processing',
      tone: needsRetry ? 'warning' : 'neutral',
    };
  }
  return {
    id: 'google-health',
    label: 'Google Health',
    value: status.connectionState === 'CONNECTED' ? 'Connected' : 'Not connected',
    detail:
      status.connectionState === 'CONNECTED'
        ? 'Read-only background sync is available.'
        : 'Open Import & sync to connect Google Health.',
    icon: 'bi bi-heart-pulse',
    section: 'imports',
    tone: status.connectionState === 'CONNECTED' ? 'success' : 'neutral',
  };
});

watch(
  () => [route.name, route.params.section, route.query[GOOGLE_HEALTH_CONNECTION_QUERY], route.hash],
  ([routeName, routeSection]) => {
    if (routeName !== 'admin') return;
    const parsed = parseAdminSection(routeSection);
    if (routeSection != null && parsed === null) {
      activeSection.value = 'overview';
      resetContentScroll();
      void router.replace({ name: 'admin' });
      return;
    }
    activeSection.value = parsed ?? 'overview';
    resetContentScroll();
  },
  { immediate: true }
);

watch(isOpen, (opened) => {
  setFastPolling(opened);
  if (opened) {
    settingsStatusConsumer = applicationStatusStore.registerPollingConsumer(settingsStatusDemand());
    void refreshOverview(false);
  } else {
    settingsStatusConsumer?.dispose();
    settingsStatusConsumer = null;
  }
});
watch(demoMode, updateSettingsStatusDemand);

onBeforeUnmount(() => {
  settingsStatusConsumer?.dispose();
  settingsStatusConsumer = null;
  setFastPolling(false);
});

function navigateTo(section: AdminSectionId) {
  if (
    activeSection.value === section &&
    route.name === 'admin' &&
    !route.query[GOOGLE_HEALTH_CONNECTION_QUERY] &&
    !route.hash
  )
    return;
  const location =
    section === 'overview' ? { name: 'admin' as const } : { name: 'admin' as const, params: { section } };
  void router.push(location);
}

function showSystemInformation() {
  showAbout.value = false;
  navigateTo('system');
}

function resetContentScroll() {
  void nextTick(() => {
    const scroller = contentScroller.value;
    if (!scroller) return;
    scroller.scrollTop = 0;
    const target =
      route.hash === `#${GOOGLE_HEALTH_PROCESSING_ID}`
        ? scroller.querySelector<HTMLElement>(`#${GOOGLE_HEALTH_PROCESSING_ID} h3`)
        : googleHealthDetailOpen.value
          ? scroller.querySelector<HTMLElement>('.gh-admin-card h3')
          : scroller.querySelector<HTMLElement>(`[data-admin-section="${activeSection.value}"] h2`);
    if (target) {
      target.setAttribute('tabindex', '-1');
      target.focus({ preventScroll: true });
    }
  });
}

async function refreshOverview(refreshStatus = true) {
  if (overviewRefreshing.value) return;
  overviewRefreshing.value = true;
  await Promise.allSettled([
    ...(refreshStatus ? [applicationStatusStore.refresh(Object.values(APPLICATION_STATUS_SECTION))] : []),
    loadToolStatus(),
    loadServerBuild(),
    loadGoogleHealthAvailability(),
  ]);
  overviewRefreshing.value = false;
}

async function loadGoogleHealthAvailability() {
  await loadDemoMode();
  updateSettingsStatusDemand();
}

async function loadDemoMode() {
  await demoAccess.load(true);
}

function settingsStatusDemand() {
  return {
    [APPLICATION_STATUS_SECTION.FRESHNESS]: STATUS_POLL_INTERVAL_MS,
    [APPLICATION_STATUS_SECTION.PROCESSING]: STATUS_POLL_INTERVAL_MS,
    [APPLICATION_STATUS_SECTION.SERVICES]: STATUS_POLL_INTERVAL_MS,
    ...(demoMode.value !== true || syntheticHealthData.value
      ? {
          [APPLICATION_STATUS_SECTION.GOOGLE_HEALTH]: STATUS_POLL_INTERVAL_MS,
        }
      : {}),
  };
}

function updateSettingsStatusDemand() {
  settingsStatusConsumer?.update(settingsStatusDemand());
}

async function loadToolStatus() {
  toolStatusLoading.value = true;
  toolStatusError.value = '';
  try {
    toolStatus.value = await getGarminToolStatus();
  } catch (error) {
    toolStatusError.value = errorMessage(error, 'Failed to load helper status.');
    warnRequestFailure('Garmin helper status could not be loaded', error, {
      topic1: 'admin',
      topic2: 'AdminDialog',
      topic3: 'helper-status',
    });
  } finally {
    toolStatusLoading.value = false;
  }
}

async function loadServerBuild() {
  serverBuildLoading.value = true;
  serverBuildError.value = '';
  try {
    serverBuild.value = await getServerBuildInfo();
  } catch (error) {
    serverBuildError.value = errorMessage(error, 'Failed to load server build information.');
    warnRequestFailure('Server build information could not be loaded', error, {
      topic1: 'admin',
      topic2: 'AdminDialog',
      topic3: 'server-build',
    });
  } finally {
    serverBuildLoading.value = false;
  }
}

function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback;
}

function toggle() {
  isOpen.value = !isOpen.value;
  if (isOpen.value) emit('tool-opened');
}

function open() {
  if (isOpen.value) return;
  isOpen.value = true;
  emit('tool-opened');
}

function close() {
  isOpen.value = false;
}

async function onSheetClosed() {
  isOpen.value = false;
  if (route.name === 'admin') {
    await router.replace({ name: 'home' }).catch(() => undefined);
  }
  emit('tool-closed');
}

function onReloadTracks(done: (success?: boolean, message?: string) => void) {
  emit('reload-tracks', done);
}

function onRefreshFreshnessData(done: (success?: boolean) => void) {
  emit('refresh-freshness-data', done);
}

defineExpose({ open, toggle, close });
</script>
