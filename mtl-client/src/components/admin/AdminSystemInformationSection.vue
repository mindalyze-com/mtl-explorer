<template>
  <div class="admin-page">
    <AdminSectionHeader
      title="System information"
      description="Build, runtime, and external component versions for this installation."
      icon="bi bi-info-circle"
    />

    <div v-if="loading" class="admin-message"><i class="pi pi-spin pi-spinner" /> Loading build information…</div>
    <div v-else-if="error" class="admin-message admin-message--error">
      <i class="pi pi-exclamation-triangle" /> {{ error }}
    </div>

    <AllVersionsReport :report="supportVersionReport" :loading="loading || servicesLoading" />

    <div class="admin-grid">
      <section class="admin-card" aria-labelledby="admin-server-build-heading">
        <div class="admin-card__header">
          <div>
            <h3 id="admin-server-build-heading">Server</h3>
            <p>Backend and container build.</p>
          </div>
        </div>
        <dl class="admin-info-list">
          <div class="admin-info-row admin-info-row--image" data-test="system-info-server-image-version">
            <dt>Image version</dt>
            <dd>
              <BuildVersionInfo :version="serverImageVersion" :revision="serverBuild?.image?.revision" />
            </dd>
          </div>
          <div class="admin-info-row" data-test="system-info-server-built">
            <dt>Built</dt>
            <dd>
              <code>{{ serverBuiltAt }}</code>
            </dd>
          </div>
          <div class="admin-info-row">
            <dt>Server ID</dt>
            <dd>
              <code>{{ serverBuild?.serverId ?? unavailable }}</code>
            </dd>
          </div>
        </dl>
      </section>

      <section class="admin-card" aria-labelledby="admin-client-build-heading">
        <div class="admin-card__header">
          <div>
            <h3 id="admin-client-build-heading">Client</h3>
            <p>Browser build and runtime mode.</p>
          </div>
        </div>
        <dl class="admin-info-list">
          <div class="admin-info-row admin-info-row--image" data-test="system-info-client-image-version">
            <dt>Image version</dt>
            <dd>
              <BuildVersionInfo :version="clientImageVersion" :revision="clientImageRevision" />
            </dd>
          </div>
          <div class="admin-info-row" data-test="system-info-client-built">
            <dt>Built</dt>
            <dd>
              <code>{{ clientBuiltAt }}</code>
            </dd>
          </div>
          <div v-if="clientStatus" class="admin-info-row" data-test="system-info-client-status">
            <dt>Status</dt>
            <dd class="admin-client-status">
              <span
                :class="[
                  'admin-status-pill',
                  clientStatus === 'outdated' ? 'admin-status-pill--warning' : 'admin-status-pill--success',
                ]"
              >
                <i :class="clientStatus === 'outdated' ? 'pi pi-exclamation-triangle' : 'pi pi-check'" />
                {{ clientStatus === 'outdated' ? `Outdated, server runs ${serverImageVersion}` : 'Up to date' }}
              </span>
              <Button
                v-if="clientStatus === 'outdated'"
                label="Reload"
                icon="pi pi-refresh"
                size="small"
                data-test="system-info-client-reload"
                @click="reloadClient"
              />
            </dd>
          </div>
          <div class="admin-info-row">
            <dt>Running as</dt>
            <dd>
              <code>{{ isPwaMode ? 'PWA (installed)' : 'Browser' }}</code>
            </dd>
          </div>
          <div class="admin-info-row">
            <dt>ClientLoginRef</dt>
            <dd>
              <code>{{ clientLoginRef || unavailable }}</code>
            </dd>
          </div>
        </dl>
      </section>
    </div>

    <section class="admin-card" aria-labelledby="admin-runtime-heading">
      <div class="admin-card__header">
        <div><h3 id="admin-runtime-heading">Runtime and database</h3></div>
      </div>
      <dl class="admin-info-list">
        <div v-for="row in runtimeRows" :key="row.label" class="admin-info-row">
          <dt>{{ row.label }}</dt>
          <dd>
            <code>{{ row.value }}</code>
          </dd>
        </div>
      </dl>
    </section>

    <section class="admin-card" aria-labelledby="admin-external-components-heading">
      <div class="admin-card__header">
        <div>
          <h3 id="admin-external-components-heading">External components</h3>
          <p>Image, tool, and data versions reported by map, routing, and search services.</p>
        </div>
      </div>
      <div v-if="externalGroups.length" class="admin-grid">
        <article
          v-for="group in externalGroups"
          :key="group.key"
          :class="['admin-external-card', `admin-external-card--${group.state}`]"
        >
          <header>
            <i :class="group.icon" />
            <span
              ><strong>{{ group.label }}</strong
              ><small>{{ group.statusLabel }}</small></span
            >
          </header>
          <dl class="admin-info-list">
            <div v-for="row in group.rows" :key="row.key" class="admin-info-row">
              <dt>{{ row.label }}</dt>
              <dd>
                <code>{{ row.value }}</code>
              </dd>
            </div>
          </dl>
        </article>
      </div>
      <div v-else class="admin-message">
        <i class="pi pi-info-circle" /> No component version metadata is available.
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import Button from 'primevue/button';
import AdminSectionHeader from '@/components/admin/AdminSectionHeader.vue';
import AllVersionsReport from '@/components/admin/AllVersionsReport.vue';
import BuildVersionInfo from '@/components/ui/BuildVersionInfo.vue';
import { useReleaseUpdatesStore } from '@/stores/releaseUpdatesStore';
import { getClientLoginRef } from '@/utils/auth';
import {
  formatClientBuildTime,
  formatClientImageVersion,
  getClientBuildTime,
  getClientImageRevision,
  getClientImageVersion,
  isClientImageOutdated,
} from '@/utils/clientBuildInfo';
import { buildSupportVersionReport } from '@/utils/supportVersions';
import { formatDateAndTimeWithSeconds } from '@/utils/Utils';
import { displayValue, versionInfoRows, type VersionInfoRow } from '@/utils/versionInfo';
import type { AdminOperationalTask, BuildInfo } from '@/utils/ServiceHelper';
import type { ServicesStatusDto } from 'x8ing-mtl-api-typescript-fetch';

defineOptions({ name: 'AdminSystemInformationSection' });

const TASK_ID_VECTOR_MAP = 'vector-map-tiles';
const TASK_ID_LOCATION_SEARCH = 'location-search';
const TASK_ID_ROUTING_SEGMENTS = 'routing-segments';
const GROUP_FALLBACK_ORDER = 99;
const GROUP_ORDER: Record<string, number> = {
  [TASK_ID_VECTOR_MAP]: 0,
  [TASK_ID_ROUTING_SEGMENTS]: 1,
  [TASK_ID_LOCATION_SEARCH]: 2,
};
const unavailable = 'Unavailable';

type ExternalGroup = {
  key: string;
  label: string;
  state: AdminOperationalTask['state'];
  statusLabel: string;
  icon: string;
  rows: VersionInfoRow[];
};

const props = defineProps<{
  serverBuild: BuildInfo | null;
  operationalTasks: AdminOperationalTask[];
  loading: boolean;
  error: string;
  servicesLoading?: boolean;
  servicesUnavailable?: boolean;
  services?: ServicesStatusDto | null;
}>();

const clientVersion = typeof __APP_PKG_VERSION__ !== 'undefined' ? __APP_PKG_VERSION__ : null;
const clientImageVersion = formatClientImageVersion();
const clientImageRevision = getClientImageRevision();
const clientBuiltAt = formatClientBuildTime();
const clientLoginRef = computed(() => getClientLoginRef());
// The server application build; images predating build-info only carry the image build time.
const serverBuiltAt = computed(() => {
  const buildTime = props.serverBuild?.buildTime || props.serverBuild?.image?.buildTime;
  if (!buildTime) return unavailable;
  return formatDateAndTimeWithSeconds(buildTime) || displayValue(buildTime);
});
const serverImageVersion = computed(() =>
  props.serverBuild?.image ? displayValue(props.serverBuild.image.version) : unavailable
);
// Only release builds can be compared; a failed server read must not mark the client up to date.
const clientStatus = computed<'outdated' | 'current' | null>(() => {
  const serverImage = props.error ? null : props.serverBuild?.image;
  if (!getClientImageVersion() || !serverImage?.version) return null;
  return isClientImageOutdated(serverImage) ? 'outdated' : 'current';
});
const releaseUpdates = useReleaseUpdatesStore();
const isPwaMode = computed(() => {
  if (typeof window === 'undefined') return false;
  return (
    window.matchMedia('(display-mode: standalone)').matches ||
    (window.navigator as Navigator & { standalone?: boolean }).standalone === true
  );
});
const runtimeRows = computed(() => [
  { label: 'Java', value: props.serverBuild?.runtime?.java || unavailable },
  { label: 'PostgreSQL', value: props.serverBuild?.runtime?.postgresql || unavailable },
  { label: 'PostGIS', value: props.serverBuild?.runtime?.postgis || unavailable },
]);
const supportVersionReport = computed(() =>
  buildSupportVersionReport(
    props.error ? null : props.serverBuild,
    {
      version: clientVersion,
      imageVersion: import.meta.env.VITE_APP_VERSION,
      revision: import.meta.env.VITE_APP_REVISION,
      buildTime: getClientBuildTime(),
      mode: isPwaMode.value ? 'PWA' : 'Browser',
    },
    props.servicesUnavailable ? null : props.services
  )
);
const externalGroups = computed<ExternalGroup[]>(() =>
  props.operationalTasks
    .map((task) => {
      const rows = versionInfoRows(task.versionInfo);
      if (!rows.length) return null;
      return {
        key: task.id,
        label: task.label,
        state: task.state,
        statusLabel: task.statusLabel,
        icon: externalIcon(task.id),
        rows,
      };
    })
    .filter((group): group is ExternalGroup => group !== null)
    .sort(
      (left, right) =>
        (GROUP_ORDER[left.key] ?? GROUP_FALLBACK_ORDER) - (GROUP_ORDER[right.key] ?? GROUP_FALLBACK_ORDER)
    )
);

// A waiting service worker must activate first, or a plain reload serves the old precached client.
function reloadClient() {
  if (releaseUpdates.browserUpdateAvailable) releaseUpdates.reloadBrowser();
  else window.location.reload();
}

function externalIcon(taskId: string): string {
  if (taskId === TASK_ID_VECTOR_MAP) return 'bi bi-map';
  if (taskId === TASK_ID_LOCATION_SEARCH) return 'bi bi-search';
  if (taskId === TASK_ID_ROUTING_SEGMENTS) return 'bi bi-signpost-split';
  return 'bi bi-box';
}
</script>

<style scoped>
.admin-info-list {
  -webkit-user-select: text;
  user-select: text;
}

.admin-info-row--image dd {
  font-size: var(--text-xs-size);
}

.admin-client-status {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 0.5rem;
}

/* Follows the stacked admin-info-row layout, which left-aligns values. */
@container admin-content (max-width: 26rem) {
  .admin-client-status {
    justify-content: flex-start;
  }
}

.admin-external-card {
  padding: 0.75rem;
  border: 1px solid var(--border-default);
  border-radius: 0.55rem;
  background: var(--surface-elevated);
}

.admin-external-card--warning {
  border-color: color-mix(in srgb, var(--warning) 42%, var(--border-default));
}

.admin-external-card header {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  margin-bottom: 0.45rem;
  color: var(--accent-text);
}

.admin-external-card header > span {
  display: flex;
  flex-direction: column;
}

.admin-external-card strong {
  color: var(--text-primary);
  font-size: var(--text-sm-size);
}

.admin-external-card small {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
}
</style>
