<template>
  <BottomSheet
    v-model="updates.detailsVisible"
    title="Updates"
    icon="bi bi-arrow-up-circle"
    :detents="[
      { id: 'comfortable', height: 'min(610px, 85vh)' },
      { id: 'large', height: '94vh' },
    ]"
    initial-detent="comfortable"
    sheet-class="sheet--solid-over-map"
  >
    <ReleaseUpdateDetails />
  </BottomSheet>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { useToast } from 'primevue/usetoast';
import type { ToastMessage } from '@/types/ui';
import BottomSheet from '@/components/ui/BottomSheet.vue';
import ReleaseUpdateDetails from '@/components/info/ReleaseUpdateDetails.vue';
import { useReleaseUpdatesStore } from '@/stores/releaseUpdatesStore';
import { isAuthenticated } from '@/utils/auth';

const NOTICE_START_DELAY_MS = 2500;
const BROWSER_UPDATED_NOTICE_LIFE_MS = 5000;
const props = defineProps<{ browserUpdateAvailable: boolean; browserJustUpdated?: boolean }>();
const emit = defineEmits<{ (event: 'reload'): void; (event: 'later'): void }>();
const toast = useToast();
const updates = useReleaseUpdatesStore();
const route = useRoute();
const ready = ref(false);
const browserUpdated = ref(props.browserJustUpdated ?? false);
const authenticated = computed(() => route.name !== 'login' && isAuthenticated());
const noticeKind = computed(() => {
  if (!ready.value) return null;
  if (props.browserUpdateAvailable) return 'browser';
  if (authenticated.value && updates.installedNoticeVisible) return 'installed';
  if (browserUpdated.value) return 'browser-updated';
  return authenticated.value && updates.noticeVisible ? 'available' : null;
});
const noticeVersion = computed(() =>
  noticeKind.value === 'installed'
    ? updates.status?.installedReleaseVersion
    : noticeKind.value === 'available'
      ? updates.status?.latestReleaseVersion
      : undefined
);
let activeNotice: ToastMessage | undefined;

function removeNotice() {
  if (activeNotice) toast.remove(activeNotice);
  activeNotice = undefined;
}

watch([noticeKind, noticeVersion], ([kind, version]) => {
  removeNotice();
  if (!kind) return;
  if (kind === 'installed') browserUpdated.value = false;
  if (kind === 'browser-updated') {
    activeNotice = {
      severity: 'success',
      summary: 'MTL Explorer updated',
      detail: 'You are using the updated interface.',
      life: BROWSER_UPDATED_NOTICE_LIFE_MS,
      onClose: () => (browserUpdated.value = false),
    };
    toast.add(activeNotice);
    return;
  }
  const browser = kind === 'browser';
  const installed = kind === 'installed';
  activeNotice = {
    severity: installed ? 'success' : 'info',
    summary: browser
      ? 'MTL Explorer update ready'
      : installed
        ? `Updated to MTL Explorer ${version}`
        : `MTL Explorer ${version} is available`,
    detail: browser
      ? 'Reload when you are ready to use the updated interface.'
      : installed
        ? 'See what changed in this release.'
        : 'Review the changes and update when convenient.',
    icon: browser || !installed ? 'bi bi-arrow-up-circle' : undefined,
    closable: false,
    actions: browser
      ? [
          { label: 'Reload', run: () => emit('reload'), primary: true },
          { label: 'Later', run: () => emit('later') },
        ]
      : [
          { label: installed ? 'What’s new' : 'View update', run: () => updates.openDetails(), primary: true },
          { label: 'Dismiss', run: dismiss },
        ],
  };
  toast.add(activeNotice);
  if (kind === 'available') updates.markNoticeShown();
});

let stopChecks: (() => void) | undefined;
let timer: ReturnType<typeof setTimeout> | undefined;
function initialize() {
  stopChecks?.();
  if (authenticated.value) stopChecks = updates.startChecks();
  else {
    updates.noticeVisible = false;
    updates.detailsVisible = false;
    updates.installedNoticeVisible = false;
  }
}
watch(authenticated, initialize);
onMounted(() => {
  initialize();
  timer = setTimeout(() => (ready.value = true), NOTICE_START_DELAY_MS);
});
onBeforeUnmount(() => {
  stopChecks?.();
  clearTimeout(timer);
  removeNotice();
});
function dismiss() {
  updates.dismissNotice();
  updates.installedNoticeVisible = false;
}
</script>
