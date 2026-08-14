<template>
  <div class="admin-page">
    <AdminSectionHeader
      title="Import & sync"
      description="Add track files directly or start the configured Garmin export."
      icon="bi bi-cloud-arrow-down"
    />

    <section class="admin-card" aria-labelledby="admin-file-import-heading">
      <div class="admin-card__header">
        <div>
          <h3 id="admin-file-import-heading">Track files</h3>
          <p>Upload supported GPS track files to the watched import directory.</p>
        </div>
      </div>
      <GpxUploadTab ref="gpxUploadTab" />
    </section>

    <section class="admin-card" aria-labelledby="admin-garmin-sync-heading">
      <div class="admin-card__header">
        <div>
          <h3 id="admin-garmin-sync-heading">Garmin sync</h3>
          <p>Pull new activities through the configured server-side export job.</p>
        </div>
      </div>
      <div class="admin-action-row">
        <div class="admin-action-copy">
          <span class="admin-action-label">Remote export</span>
          <span class="admin-action-hint">The server runs the export and indexes any new files.</span>
        </div>
        <div class="admin-action-controls">
          <span v-if="loading" class="admin-status-pill admin-status-pill--loading">
            <i class="pi pi-spin pi-spinner" /> Running…
          </span>
          <span v-else-if="error" class="admin-status-pill admin-status-pill--error">{{ error }}</span>
          <span v-else-if="success" class="admin-status-pill admin-status-pill--success">
            <i class="pi pi-check" /> Done
          </span>
          <Button label="Run" icon="pi pi-play" size="small" :disabled="loading" @click="runGarminSync" />
        </div>
      </div>
    </section>

    <details
      v-if="loading || output || error"
      class="admin-disclosure"
      :open="loading || Boolean(output) || Boolean(error)"
    >
      <summary>Command output</summary>
      <div class="admin-disclosure__body">
        <pre class="admin-command-output">{{ output || error || 'Waiting for response…' }}</pre>
      </div>
    </details>
  </div>
</template>

<script setup lang="ts">
import { nextTick, ref, watch } from 'vue';
import Button from 'primevue/button';
import AdminSectionHeader from '@/components/admin/AdminSectionHeader.vue';
import GpxUploadTab from '@/components/admin/GpxUploadTab.vue';
import { useAsyncState } from '@/composables/useAsyncState';
import { triggerGarminExport } from '@/utils/ServiceHelper';

defineOptions({ name: 'AdminImportSyncSection' });

const props = defineProps<{
  active: boolean;
}>();

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
  loading.value = true;
  error.value = '';
  output.value = '';
  success.value = false;
  try {
    output.value = await triggerGarminExport();
    success.value = true;
  } catch (runError) {
    error.value = runError instanceof Error ? runError.message : 'Garmin sync failed.';
  } finally {
    loading.value = false;
  }
}
</script>
