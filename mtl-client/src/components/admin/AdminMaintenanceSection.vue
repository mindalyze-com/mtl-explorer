<template>
  <div class="admin-page">
    <AdminSectionHeader
      title="Maintenance"
      description="Reload local data, rescan source folders, and manage export helpers."
      icon="bi bi-wrench-adjustable"
    />

    <section class="admin-card" aria-labelledby="admin-local-data-heading">
      <div class="admin-card__header">
        <div>
          <h3 id="admin-local-data-heading">Local data</h3>
          <p>Refresh browser data after server-side changes.</p>
        </div>
      </div>
      <div class="admin-action-row">
        <div class="admin-action-copy">
          <span class="admin-action-label">Reload tracks</span>
          <span class="admin-action-hint">Clear the local track cache and load current data from the server.</span>
        </div>
        <div class="admin-action-controls">
          <span v-if="reloadLoading" class="admin-status-pill admin-status-pill--loading">
            <i class="pi pi-spin pi-spinner" /> Reloading…
          </span>
          <span v-else-if="reloadError" class="admin-status-pill admin-status-pill--error">{{ reloadError }}</span>
          <span v-else-if="reloadSuccess" class="admin-status-pill admin-status-pill--success">
            <i class="pi pi-check" /> Done
          </span>
          <Button label="Reload" icon="pi pi-refresh" size="small" :disabled="reloadLoading" @click="reloadTracks" />
        </div>
      </div>
    </section>

    <section class="admin-card" aria-labelledby="admin-rescan-heading">
      <div class="admin-card__header">
        <div>
          <h3 id="admin-rescan-heading">Manual rescan</h3>
          <p>Queue a new directory scan when file watching did not detect a change.</p>
        </div>
      </div>
      <AdminManualRescan />
      <details class="admin-disclosure admin-maintenance-help">
        <summary>When is a rescan needed?</summary>
        <div class="admin-disclosure__body">
          <p class="admin-card__hint">
            Docker Desktop on Windows with WSL2 can expose copied files inside the container without sending Linux file
            events. Run a rescan after copying files through a Windows-mounted folder when indexing does not start.
          </p>
        </div>
      </details>
    </section>

    <details class="admin-disclosure" :open="Boolean(toolStatusError)">
      <summary>Advanced tools · {{ readyToolCount }}/2 ready</summary>
      <div class="admin-disclosure__body admin-advanced-tools">
        <div v-if="toolStatusLoading" class="admin-message">
          <i class="pi pi-spin pi-spinner" /> Loading helper status…
        </div>
        <div v-else-if="toolStatusError" class="admin-message admin-message--error">
          <i class="pi pi-exclamation-triangle" /> {{ toolStatusError }}
        </div>
        <template v-else>
          <section class="admin-tool-block" aria-labelledby="admin-gcexport-heading">
            <div class="admin-tool-block__header">
              <div>
                <h3 id="admin-gcexport-heading">gcexport</h3>
                <span :class="['admin-tool-state', { 'admin-tool-state--ready': toolStatus.gcexportVenvPresent }]">
                  {{ toolStatus.gcexportVenvPresent ? 'Ready' : 'Missing' }}
                </span>
              </div>
              <a href="https://github.com/pe-st/garmin-connect-export/tags" target="_blank" rel="noopener noreferrer">
                Releases <i class="bi bi-box-arrow-up-right" />
              </a>
            </div>
            <div class="admin-form-row">
              <InputText v-model="gcexportVersion" class="admin-field" placeholder="Version, for example v4.6.2" />
              <Button
                label="Install"
                icon="pi pi-download"
                size="small"
                :disabled="helperLoading || !gcexportVersion.trim()"
                @click="installGcexportVersion"
              />
            </div>
          </section>

          <section class="admin-tool-block" aria-labelledby="admin-fit-export-heading">
            <div class="admin-tool-block__header">
              <div>
                <h3 id="admin-fit-export-heading">fit-export</h3>
                <span :class="['admin-tool-state', { 'admin-tool-state--ready': toolStatus.fitExportVenvPresent }]">
                  {{ toolStatus.fitExportVenvPresent ? 'Ready' : 'Missing' }}
                </span>
              </div>
            </div>
            <div class="admin-form-row">
              <InputText v-model="fitProfile" class="admin-field admin-field--small" placeholder="Profile" />
              <InputText v-model="fitPackages" class="admin-field" placeholder="Packages" />
              <Button
                label="Install"
                icon="pi pi-download"
                size="small"
                :disabled="helperLoading || !fitProfile.trim() || !fitPackages.trim()"
                @click="installFitExportProfile"
              />
            </div>
          </section>
        </template>

        <div v-if="helperLoading || helperOutput || helperError" class="admin-helper-output">
          <span v-if="helperLoading" class="admin-status-pill admin-status-pill--loading">
            <i class="pi pi-spin pi-spinner" /> Running…
          </span>
          <span v-else-if="helperError" class="admin-status-pill admin-status-pill--error">{{ helperError }}</span>
          <span v-else-if="helperSuccess" class="admin-status-pill admin-status-pill--success">
            <i class="pi pi-check" /> Done
          </span>
          <pre class="admin-command-output">{{ helperOutput || helperError }}</pre>
        </div>
      </div>
    </details>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import Button from 'primevue/button';
import InputText from 'primevue/inputtext';
import AdminSectionHeader from '@/components/admin/AdminSectionHeader.vue';
import AdminManualRescan from '@/components/admin/AdminManualRescan.vue';
import { clearTrackCache } from '@/utils/tracks/trackCollectionLoader';
import { installFitExport, installGcexport, type GarminToolStatus } from '@/utils/ServiceHelper';
import { useDataFreshnessStore } from '@/stores/dataFreshnessStore';
import { useAsyncState } from '@/composables/useAsyncState';

defineOptions({ name: 'AdminMaintenanceSection' });

const props = defineProps<{
  toolStatus: GarminToolStatus;
  toolStatusLoading: boolean;
  toolStatusError: string;
}>();

const emit = defineEmits<{
  (event: 'reload-tracks', done: (success?: boolean, message?: string) => void): void;
  (event: 'refresh-tool-status'): void;
}>();

const dataFreshnessStore = useDataFreshnessStore();
const { loading: helperLoading, error: helperError } = useAsyncState('');
const helperOutput = ref('');
const helperSuccess = ref(false);
const reloadLoading = ref(false);
const reloadError = ref('');
const reloadSuccess = ref(false);
const gcexportVersion = ref('');
const fitProfile = ref('');
const fitPackages = ref('');

const readyToolCount = computed(
  () => [props.toolStatus.gcexportVenvPresent, props.toolStatus.fitExportVenvPresent].filter(Boolean).length
);

watch(
  () => props.toolStatus,
  (status) => {
    gcexportVersion.value = status.gcexportConfiguredVersion;
    fitProfile.value = status.fitExportConfiguredProfile;
    fitPackages.value = status.fitExportConfiguredPackages;
  },
  { immediate: true, deep: true }
);

async function reloadTracks() {
  reloadLoading.value = true;
  reloadError.value = '';
  reloadSuccess.value = false;
  try {
    await clearTrackCache();
    dataFreshnessStore.clearAppliedToken();
    emit('reload-tracks', (success = true, message = '') => {
      reloadSuccess.value = success;
      reloadError.value = success ? '' : message || 'Failed to reload tracks.';
      reloadLoading.value = false;
    });
  } catch (reloadFailure) {
    reloadError.value = reloadFailure instanceof Error ? reloadFailure.message : 'Failed to reload tracks.';
    reloadLoading.value = false;
  }
}

async function installGcexportVersion() {
  await runHelperAction(() => installGcexport(gcexportVersion.value.trim()));
}

async function installFitExportProfile() {
  await runHelperAction(() => installFitExport(fitProfile.value.trim(), fitPackages.value.trim()));
}

async function runHelperAction(action: () => Promise<string>) {
  helperLoading.value = true;
  helperError.value = '';
  helperOutput.value = '';
  helperSuccess.value = false;
  try {
    helperOutput.value = await action();
    helperSuccess.value = true;
  } catch (actionError) {
    const installError = actionError as Error & { installLog?: string | null };
    helperError.value = installError.message || 'Helper installation failed.';
    helperOutput.value = installError.installLog ?? '';
  } finally {
    helperLoading.value = false;
    emit('refresh-tool-status');
  }
}
</script>

<style scoped>
.admin-maintenance-help {
  margin-top: 0.8rem;
  background: transparent;
}

.admin-advanced-tools {
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
}

.admin-tool-block {
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
  padding-top: 0.85rem;
  border-top: 1px solid var(--border-subtle);
}

.admin-tool-block:first-of-type {
  padding-top: 0;
  border-top: 0;
}

.admin-tool-block__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}

.admin-tool-block__header > div {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.admin-tool-block__header h3 {
  margin: 0;
  color: var(--text-primary);
  font-size: var(--text-sm-size);
}

.admin-tool-block__header a {
  color: var(--accent-text);
  font-size: var(--text-xs-size);
  text-decoration: none;
}

.admin-tool-state {
  padding: 0.15rem 0.4rem;
  border-radius: 999px;
  background: var(--warning-bg);
  color: var(--warning-text);
  font-size: var(--text-2xs-size);
  font-weight: 700;
  text-transform: uppercase;
}

.admin-tool-state--ready {
  background: var(--success-bg);
  color: var(--success);
}

.admin-helper-output {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}
</style>
