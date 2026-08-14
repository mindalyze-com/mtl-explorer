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

    <div class="admin-grid">
      <section class="admin-card" aria-labelledby="admin-server-build-heading">
        <div class="admin-card__header">
          <div>
            <h3 id="admin-server-build-heading">Server</h3>
            <p>Backend and container build.</p>
          </div>
        </div>
        <dl class="admin-info-list">
          <div class="admin-info-row">
            <dt>Version</dt>
            <dd>
              <code>{{ serverBuild?.version ?? unavailable }}</code>
            </dd>
          </div>
          <div class="admin-info-row">
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
          <div class="admin-info-row">
            <dt>Image</dt>
            <dd>
              <code>{{ serverImageVersion }}</code>
            </dd>
          </div>
          <div class="admin-info-row">
            <dt>Image built</dt>
            <dd>
              <code>{{ serverImageBuiltAt }}</code>
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
          <div class="admin-info-row">
            <dt>Version</dt>
            <dd>
              <code>{{ clientVersion || unavailable }}</code>
            </dd>
          </div>
          <div class="admin-info-row">
            <dt>Built</dt>
            <dd>
              <code>{{ clientBuildFormatted }}</code>
            </dd>
          </div>
          <div class="admin-info-row">
            <dt>Running as</dt>
            <dd>
              <code>{{ isPwaMode ? 'PWA (installed)' : 'Browser' }}</code>
            </dd>
          </div>
        </dl>
      </section>
    </div>

    <section class="admin-card" aria-labelledby="admin-external-components-heading">
      <div class="admin-card__header">
        <div>
          <h3 id="admin-external-components-heading">External components</h3>
          <p>Image, tool, and data versions reported by map, routing, and search services.</p>
        </div>
      </div>
      <div v-if="externalGroups.length" class="admin-external-grid">
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
import AdminSectionHeader from '@/components/admin/AdminSectionHeader.vue';
import { formatDateAndTimeWithSeconds } from '@/utils/Utils';
import { displayValue, versionInfoRows, type VersionInfoRow } from '@/utils/versionInfo';
import type { AdminOperationalTask, BuildInfo } from '@/utils/ServiceHelper';

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
}>();

const clientBuild = typeof __APP_VERSION__ !== 'undefined' ? __APP_VERSION__ : null;
const clientVersion = typeof __APP_PKG_VERSION__ !== 'undefined' ? __APP_PKG_VERSION__ : null;
const clientBuildFormatted = computed(() => formatBuildTime(clientBuild, 'dev environment'));
const serverBuiltAt = computed(() => formatBuildTime(props.serverBuild?.buildTime, unavailable));
const serverImageVersion = computed(() =>
  props.serverBuild?.image ? displayValue(props.serverBuild.image.version) : unavailable
);
const serverImageBuiltAt = computed(() =>
  props.serverBuild?.image ? formatBuildTime(props.serverBuild.image.buildTime, unavailable) : unavailable
);
const isPwaMode = computed(() => {
  if (typeof window === 'undefined') return false;
  return (
    window.matchMedia('(display-mode: standalone)').matches ||
    (window.navigator as Navigator & { standalone?: boolean }).standalone === true
  );
});
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

function formatBuildTime(value: string | null | undefined, fallback: string): string {
  if (!value) return fallback;
  const formatted = formatDateAndTimeWithSeconds(value);
  return formatted || displayValue(value);
}

function externalIcon(taskId: string): string {
  if (taskId === TASK_ID_VECTOR_MAP) return 'bi bi-map';
  if (taskId === TASK_ID_LOCATION_SEARCH) return 'bi bi-search';
  if (taskId === TASK_ID_ROUTING_SEGMENTS) return 'bi bi-signpost-split';
  return 'bi bi-box';
}
</script>

<style scoped>
.admin-external-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.65rem;
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

@media (max-width: 768px) {
  .admin-external-grid {
    grid-template-columns: 1fr;
  }
}
</style>
