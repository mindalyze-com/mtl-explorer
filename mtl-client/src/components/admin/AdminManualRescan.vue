<template>
  <div class="admin-action-list">
    <p v-if="demoMode === true" class="admin-action-hint" role="status">
      Manual rescans are unavailable in this shared demo.
    </p>
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
          :disabled="loadingIndex !== null || demoMode !== false"
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
          :loading="loadingIndex === 'MEDIA'"
          :disabled="loadingIndex !== null || demoMode !== false"
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
import { logSanitizedError } from '@/utils/safeLogging';

import { ref } from 'vue';
import Button from 'primevue/button';
import { triggerIndexerRescan } from '@/utils/serverAdminApi';
import { useIndexerStatus } from '@/composables/useIndexerStatus';

defineOptions({ name: 'AdminManualRescan' });
const props = withDefaults(defineProps<{ demoMode?: boolean | null }>(), { demoMode: false });

const { refresh } = useIndexerStatus();
const loadingIndex = ref<'GPS' | 'MEDIA' | null>(null);
const message = ref('');
const error = ref('');

async function triggerRescan(index: 'GPS' | 'MEDIA') {
  if (props.demoMode !== false) return;
  loadingIndex.value = index;
  message.value = '';
  error.value = '';
  try {
    const response = await triggerIndexerRescan(index);
    message.value = response.message ?? `${index} rescan request sent.`;
    await refresh();
  } catch (rescanError) {
    logSanitizedError('[MTL] Manual index rescan failed:', rescanError, {
      topic1: 'administration',
      topic2: 'AdminManualRescan',
      topic3: 'triggerRescan',
    });
    error.value = rescanError instanceof Error ? rescanError.message : 'Rescan request failed.';
  } finally {
    loadingIndex.value = null;
  }
}
</script>
