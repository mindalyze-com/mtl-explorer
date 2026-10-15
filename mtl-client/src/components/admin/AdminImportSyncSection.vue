<template>
  <div class="admin-page">
    <AdminSectionHeader
      v-if="!connectionDetail"
      title="Import & sync"
      description="Add track files or sync a connected service."
      icon="bi bi-cloud-arrow-down"
    />

    <RouterLink
      v-if="connectionDetail"
      class="admin-connection-back"
      :to="{ name: 'admin', params: { section: 'imports' } }"
    >
      <i class="bi bi-arrow-left" aria-hidden="true" /> Import &amp; sync
    </RouterLink>

    <section v-show="!connectionDetail" class="admin-card" aria-labelledby="admin-file-import-heading">
      <div class="admin-card__header">
        <div>
          <h3 id="admin-file-import-heading">Track files</h3>
          <p>Upload supported GPS track files to the watched import directory.</p>
        </div>
      </div>
      <GpxUploadTab ref="gpxUploadTab" compact :demo-mode="demoMode" />
    </section>

    <section :class="{ 'admin-card': !connectionDetail }" aria-label="Connected services">
      <div v-show="!connectionDetail" class="admin-card__header">
        <h3>Connected services</h3>
      </div>
      <div v-show="!connectionDetail" class="admin-action-row">
        <div class="admin-action-copy">
          <span class="admin-action-label">Garmin</span>
          <span class="admin-action-hint">{{
            demoMode === true
              ? 'Garmin sync is unavailable in this shared demo.'
              : 'Import new activities from the configured export.'
          }}</span>
        </div>
        <div class="admin-action-controls">
          <span v-if="loading" class="admin-status-pill admin-status-pill--loading">
            <i class="pi pi-spin pi-spinner" /> Running…
          </span>
          <span v-else-if="error" class="admin-status-pill admin-status-pill--error">Sync failed</span>
          <span v-else-if="success" class="admin-status-pill admin-status-pill--success">
            <i class="pi pi-check" /> Done
          </span>
          <Button
            label="Sync now"
            icon="pi pi-refresh"
            size="small"
            :disabled="loading || demoMode !== false"
            @click="runGarminSync"
          />
        </div>
      </div>
      <details
        v-if="loading || output || error"
        v-show="!connectionDetail"
        class="admin-disclosure"
        :open="loading || Boolean(error)"
      >
        <summary>Garmin command output</summary>
        <div class="admin-disclosure__body">
          <pre class="admin-command-output">{{ output || error || 'Waiting for response…' }}</pre>
        </div>
      </details>
      <GoogleHealthAdminCard
        :active="active"
        :demo-mode="demoMode"
        :detail="connectionDetail"
        :synthetic-data="syntheticData"
      />
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import { RouterLink, useRoute } from 'vue-router';
import {
  GOOGLE_HEALTH_CONNECTION_QUERY,
  GOOGLE_HEALTH_CONNECTION_ID,
} from '@/features/health/utils/googleHealthNavigation';
import Button from 'primevue/button';
import AdminSectionHeader from '@/components/admin/AdminSectionHeader.vue';
import GpxUploadTab from '@/components/admin/GpxUploadTab.vue';
import { useAsyncState } from '@/composables/useAsyncState';
import { triggerGarminExport } from '@/utils/ServiceHelper';
import GoogleHealthAdminCard from '@/features/health/screens/admin/GoogleHealthAdminCard.vue';
import { APPLICATION_STATUS_SECTION, useApplicationStatusStore } from '@/stores/applicationStatusStore';

defineOptions({ name: 'AdminImportSyncSection' });

const props = defineProps<{
  active: boolean;
  demoMode: boolean | null;
  syntheticData?: boolean;
}>();
const applicationStatusStore = useApplicationStatusStore();
const route = useRoute();
const connectionDetail = computed(() => route.query[GOOGLE_HEALTH_CONNECTION_QUERY] === GOOGLE_HEALTH_CONNECTION_ID);

type GpxUploadTabPublic = { loadStatus: () => Promise<void> | void };

const gpxUploadTab = ref<GpxUploadTabPublic | null>(null);
const { loading, error } = useAsyncState('');
const output = ref('');
const success = ref(false);

watch(
  () => props.active,
  (active) => {
    if (active) void nextTick(() => gpxUploadTab.value?.loadStatus());
  },
  { immediate: true }
);

async function runGarminSync() {
  if (props.demoMode !== false) return;
  loading.value = true;
  error.value = '';
  output.value = '';
  success.value = false;
  try {
    output.value = await triggerGarminExport();
    success.value = true;
    void applicationStatusStore.refresh([APPLICATION_STATUS_SECTION.PROCESSING, APPLICATION_STATUS_SECTION.FRESHNESS]);
  } catch (runError) {
    error.value = runError instanceof Error ? runError.message : 'Garmin sync failed.';
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.admin-connection-back {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  align-self: flex-start;
  min-height: var(--p-button-icon-only-width);
  color: var(--accent-text);
  font-size: var(--text-sm-size);
  text-underline-offset: 0.2em;
}
</style>
