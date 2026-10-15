import { computed, onMounted, ref } from 'vue';
import { getDemoStatus, type DemoStatus } from '@/utils/serverAdminApi';

/** Public installation metadata, independent of Health records and their API responses. */
export function useDemoStatus() {
  const status = ref<DemoStatus>({ demoMode: false });
  onMounted(async () => {
    status.value = await getDemoStatus();
  });
  return {
    demoMode: computed(() => status.value.demoMode),
    syntheticHealthData: computed(() => status.value.demoMode && status.value.syntheticHealthData === true),
  };
}
