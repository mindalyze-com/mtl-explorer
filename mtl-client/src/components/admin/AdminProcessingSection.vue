<template>
  <div class="admin-page admin-processing">
    <AdminSectionHeader
      title="Processing"
      description="Imports, background jobs, maps, and routing."
      icon="bi bi-list-check"
    />
    <IndexerStatusTab>
      <template v-if="showGoogleHealth" #connections>
        <div :id="GOOGLE_HEALTH_PROCESSING_ID">
          <GoogleHealthSyncProgress
            :status="googleHealthStatus"
            :error="googleHealthStatusError"
            :count-animation="googleHealthCountAnimation"
            presentation="row"
            show-history
          />
        </div>
      </template>
    </IndexerStatusTab>
  </div>
</template>
<script setup lang="ts">
import { computed } from 'vue';
import { GOOGLE_HEALTH_PROCESSING_ID } from '@/features/health/utils/googleHealthNavigation';
import AdminSectionHeader from '@/components/admin/AdminSectionHeader.vue';
import IndexerStatusTab from '@/components/admin/IndexerStatusTab.vue';
import type { GHStatusResponse } from '@/features/health/api/googleHealthApi';
import GoogleHealthSyncProgress from '@/features/health/components/GoogleHealthSyncProgress.vue';
import type { CountAnimation } from '@/utils/countAnimation';

defineOptions({ name: 'AdminProcessingSection' });
const props = withDefaults(
  defineProps<{
    googleHealthStatus?: GHStatusResponse | null;
    googleHealthStatusError?: string;
    googleHealthCountAnimation?: CountAnimation | null;
    demoMode?: boolean | null;
  }>(),
  { googleHealthStatus: null, googleHealthStatusError: '', demoMode: null, googleHealthCountAnimation: undefined }
);
const showGoogleHealth = computed(() => {
  if (props.demoMode !== false) return false;
  const status = props.googleHealthStatus;
  return (
    Boolean(props.googleHealthStatusError) ||
    status?.connectionState === 'CONNECTED' ||
    status?.connectionState === 'RECONNECT_REQUIRED' ||
    status?.authorizationAction === 'REAUTHORIZE' ||
    (status?.syncStates?.length ?? 0) > 0 ||
    (status?.synchronization?.streams?.length ?? 0) > 0 ||
    !['', 'IDLE'].includes(status?.job?.state ?? '')
  );
});
</script>

<style scoped>
.admin-processing {
  gap: 0.65rem;
}
</style>
