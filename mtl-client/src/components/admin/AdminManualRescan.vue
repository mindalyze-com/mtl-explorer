<template>
  <div class="admin-action-list">
    <div class="admin-action-row">
      <div class="admin-action-copy">
        <span class="admin-action-label">GPS files</span>
        <span class="admin-action-hint">Queue a full scan of the configured GPS directory.</span>
      </div>
      <div class="admin-action-controls">
        <Button
          label="Rescan GPS"
          icon="pi pi-map-marker"
          size="small"
          :loading="loadingIndex === 'GPS'"
          :disabled="loadingIndex !== null"
          @click="triggerRescan('GPS')"
        />
      </div>
    </div>
    <div class="admin-action-row">
      <div class="admin-action-copy">
        <span class="admin-action-label">Media files</span>
        <span class="admin-action-hint">Queue a full scan of the configured media directory.</span>
      </div>
      <div class="admin-action-controls">
        <Button
          label="Rescan Media"
          icon="pi pi-images"
          size="small"
          severity="secondary"
          :loading="loadingIndex === 'MEDIA'"
          :disabled="loadingIndex !== null"
          @click="triggerRescan('MEDIA')"
        />
      </div>
    </div>
    <div v-if="message || error" :class="['admin-message', { 'admin-message--error': error }]" role="status">
      <i :class="error ? 'pi pi-exclamation-triangle' : 'pi pi-check-circle'" />
      <span>{{ error || message }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import Button from 'primevue/button';
import { triggerIndexerRescan } from '@/utils/serverAdminApi';
import { useIndexerStatus } from '@/composables/useIndexerStatus';

defineOptions({ name: 'AdminManualRescan' });

const { refresh } = useIndexerStatus();
const loadingIndex = ref<'GPS' | 'MEDIA' | null>(null);
const message = ref('');
const error = ref('');

async function triggerRescan(index: 'GPS' | 'MEDIA') {
  loadingIndex.value = index;
  message.value = '';
  error.value = '';
  try {
    const response = await triggerIndexerRescan(index);
    message.value = response.message ?? `${index} rescan request sent.`;
    await refresh();
  } catch (rescanError) {
    console.error('[MTL] Manual index rescan failed:', rescanError);
    error.value = rescanError instanceof Error ? rescanError.message : 'Rescan request failed.';
  } finally {
    loadingIndex.value = null;
  }
}
</script>
