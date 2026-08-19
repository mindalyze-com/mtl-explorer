<template>
  <BottomSheet
    v-model="isOpen"
    title="Admin"
    icon="bi bi-gear"
    :detents="[
      { id: 'comfortable', height: 'min(710px, 82vh)' },
      { id: 'large', height: '92vh' },
    ]"
    initial-detent="comfortable"
    sheet-class="admin-center-sheet"
    @closed="onSheetClosed"
  >
    <div class="admin-center-root">
      <div class="admin-center-layout">
        <aside class="admin-sidebar" aria-label="Admin sections">
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
        </aside>

        <main class="admin-content">
          <div ref="contentScroller" class="admin-content-scroll">
            <button
              v-if="activeSection !== 'overview'"
              type="button"
              class="admin-mobile-back"
              @click="navigateTo('overview')"
            >
              <i class="bi bi-arrow-left" /> Back to overview
            </button>

            <AdminOverview
              v-show="activeSection === 'overview'"
              :cards="overviewCards"
              :badges="sectionBadges"
              :refreshing="overviewRefreshing"
              @navigate="navigateTo"
              @refresh="refreshOverview(true)"
              @show-about="showAbout = true"
            />
            <AdminImportSyncSection
              v-show="activeSection === 'imports'"
              :active="isOpen && activeSection === 'imports'"
            />
            <AdminProcessingSection v-show="activeSection === 'processing'" />
            <AdminDataStatusSection v-show="activeSection === 'data-status'" @refresh-data="onRefreshFreshnessData" />
            <AdminMaintenanceSection
              v-show="activeSection === 'maintenance'"
              :tool-status="toolStatus"
              :tool-status-loading="toolStatusLoading"
              :tool-status-error="toolStatusError"
              @reload-tracks="onReloadTracks"
              @refresh-tool-status="loadToolStatus"
            />
            <AdminServerLogSection v-show="activeSection === 'logs'" :active="isOpen && activeSection === 'logs'" />
            <AdminSystemInformationSection
              v-show="activeSection === 'system'"
              :server-build="serverBuild"
              :operational-tasks="operationalTasks"
              :loading="serverBuildLoading"
              :error="serverBuildError"
            />
            <AdminPreferencesSection v-show="activeSection === 'preferences'" />
            <AdminSessionSection v-show="activeSection === 'session'" />
          </div>
        </main>
      </div>
    </div>
  </BottomSheet>

  <AboutView v-if="showAbout" embedded :viewport-centered="false" @closed="showAbout = false" />
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent, nextTick, onBeforeUnmount, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import BottomSheet from '@/components/ui/BottomSheet.vue';
import AdminOverview from '@/components/admin/AdminOverview.vue';
import AdminImportSyncSection from '@/components/admin/AdminImportSyncSection.vue';
import AdminProcessingSection from '@/components/admin/AdminProcessingSection.vue';
import AdminDataStatusSection from '@/components/admin/AdminDataStatusSection.vue';
import AdminMaintenanceSection from '@/components/admin/AdminMaintenanceSection.vue';
import AdminServerLogSection from '@/components/admin/AdminServerLogSection.vue';
import AdminSystemInformationSection from '@/components/admin/AdminSystemInformationSection.vue';
import AdminPreferencesSection from '@/components/admin/AdminPreferencesSection.vue';
import AdminSessionSection from '@/components/admin/AdminSessionSection.vue';
import {
  ADMIN_SECTION_GROUPS,
  parseAdminSection,
  sectionsForGroup,
  type AdminOverviewStatus,
  type AdminSectionId,
} from '@/components/admin/adminSections';
import { useIndexerStatus } from '@/composables/useIndexerStatus';
import { useDataFreshnessStore } from '@/stores/dataFreshnessStore';
import { getGarminToolStatus, getServerBuildInfo, type BuildInfo, type GarminToolStatus } from '@/utils/ServiceHelper';
import '@/components/admin/adminCenter.css';

defineOptions({ name: 'AdminDialog' });

const emit = defineEmits<{
  (event: 'tool-opened'): void;
  (event: 'tool-closed'): void;
  (event: 'reload-tracks', done: (success?: boolean, message?: string) => void): void;
  (event: 'refresh-freshness-data', done: (success?: boolean) => void): void;
}>();

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
const {
  summaries,
  jobSummaries,
  operationalTasks,
  isIndexerStatusPollingHealthy,
  isIndexing,
  isJobPending,
  isOperationalTaskActive,
  refresh: refreshIndexerStatus,
  setFastPolling,
} = useIndexerStatus();

const isOpen = ref(false);
const showAbout = ref(false);
const activeSection = ref<AdminSectionId>('overview');
const contentScroller = ref<HTMLElement | null>(null);
const toolStatus = ref<GarminToolStatus>({ ...EMPTY_TOOL_STATUS });
const toolStatusLoading = ref(false);
const toolStatusError = ref('');
const serverBuild = ref<BuildInfo | null>(null);
const serverBuildLoading = ref(false);
const serverBuildError = ref('');
const overviewRefreshing = ref(false);
const sectionGroups = ADMIN_SECTION_GROUPS;

const isProcessing = computed(() => isIndexing.value || isJobPending.value || isOperationalTaskActive.value);
const pendingWorkCount = computed(
  () =>
    summaries.value.reduce((total, summary) => total + summary.pending, 0) +
    jobSummaries.value.reduce((total, summary) => total + summary.pending, 0) +
    operationalTasks.value.filter((task) => task.active).length
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
  if (!isIndexerStatusPollingHealthy.value) badges.processing = '!';
  else if (isProcessing.value) badges.processing = 'Live';
  if (!dataFreshnessStore.isFreshnessPollingHealthy) badges['data-status'] = '!';
  else if (dataFreshnessStore.isOutOfSync) badges['data-status'] = 'Stale';
  if (toolStatusError.value) badges.maintenance = '!';
  else if (!toolStatusLoading.value && missingToolCount.value > 0) badges.maintenance = String(missingToolCount.value);
  return badges;
});

const overviewCards = computed<AdminOverviewStatus[]>(() => [
  processingOverview.value,
  dataOverview.value,
  servicesOverview.value,
  helpersOverview.value,
]);

const processingOverview = computed<AdminOverviewStatus>(() => {
  if (!isIndexerStatusPollingHealthy.value) {
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
    value: isProcessing.value ? 'Active' : 'Idle',
    detail: isProcessing.value ? `${pendingWorkCount.value} active or pending` : 'No background work is active.',
    icon: 'bi bi-list-check',
    section: 'processing',
    tone: isProcessing.value ? 'live' : 'success',
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
  if (!isIndexerStatusPollingHealthy.value) {
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

watch(
  () => [route.name, route.params.section],
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
  if (opened) void refreshOverview();
});

onBeforeUnmount(() => setFastPolling(false));

function navigateTo(section: AdminSectionId) {
  if (activeSection.value === section && route.name === 'admin') return;
  const location =
    section === 'overview' ? { name: 'admin' as const } : { name: 'admin' as const, params: { section } };
  void router.push(location);
}

function resetContentScroll() {
  void nextTick(() => {
    if (contentScroller.value) contentScroller.value.scrollTop = 0;
  });
}

async function refreshOverview(forceMapStatus = false) {
  if (overviewRefreshing.value) return;
  overviewRefreshing.value = true;
  await Promise.allSettled([
    refreshIndexerStatus({ forceMapStatus }),
    dataFreshnessStore.refresh(),
    loadToolStatus(),
    loadServerBuild(),
  ]);
  overviewRefreshing.value = false;
}

async function loadToolStatus() {
  toolStatusLoading.value = true;
  toolStatusError.value = '';
  try {
    toolStatus.value = await getGarminToolStatus();
  } catch (error) {
    toolStatusError.value = errorMessage(error, 'Failed to load helper status.');
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
