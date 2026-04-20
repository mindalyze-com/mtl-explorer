<template>
  <Dialog
    v-model:visible="dialogVisible"
    maximizable
    modal
    header="Tracks"
    appendTo="body"
    :content-class="'content-no-scroll'"
    class="tool-dialog track-browser-dialog-shell"
    :class="{ 'track-browser-dialog-shell--mobile': isMobile }"
  >
    <div class="track-browser-dialog" :class="isMobile ? 'track-browser-dialog--mobile' : 'dialog-flex-root'">
      <TrackBrowserControls
        :query="query"
        :summary="summary"
        :total-count="totalCount"
        @update:query="query = $event"
      />

      <TrackBrowserTable
        :rows="rows"
        :selected-track-id="selectedTrackId ?? null"
        :query="query"
        :compact="isMobile"
        @select-track="onTrackSelect($event)"
        @open-details="onTrackDetails($event)"
      />
    </div>
  </Dialog>
</template>

<script setup lang="ts">
import { computed, toRef, ref, onMounted, onUnmounted } from 'vue';
import TrackBrowserControls from './TrackBrowserControls.vue';
import TrackBrowserTable from './TrackBrowserTable.vue';
import { useTrackBrowser } from './useTrackBrowser';
import type { GpsTrack } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';

const MOBILE_BREAKPOINT = 768;

const props = defineProps<{
  visible: boolean;
  tracks: GpsTrack[];
  selectedTrackId?: number | string | null;
}>();

const emit = defineEmits<{
  (event: 'update:visible', value: boolean): void;
  (event: 'select-track', value: number | string): void;
  (event: 'open-details', value: number | string): void;
}>();

const windowWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1024);
function onResize() { windowWidth.value = window.innerWidth; }
onMounted(() => window.addEventListener('resize', onResize));
onUnmounted(() => window.removeEventListener('resize', onResize));

const isMobile = computed(() => windowWidth.value < MOBILE_BREAKPOINT);

const dialogVisible = computed({
  get: () => props.visible,
  set: (value: boolean) => emit('update:visible', value),
});

const {
  query,
  rows,
  summary,
  totalCount,
} = useTrackBrowser(toRef(props, 'tracks'));

function onTrackSelect(id: number | string) {
  emit('select-track', id);
  if (isMobile.value) dialogVisible.value = false;
}

function onTrackDetails(id: number | string) {
  emit('open-details', id);
  if (isMobile.value) dialogVisible.value = false;
}
</script>

<style scoped>
.track-browser-dialog {
  background: transparent;
}

/* Mobile: scrollable column layout instead of fixed flex */
.track-browser-dialog--mobile {
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  overflow-x: hidden;
  -webkit-overflow-scrolling: touch;
  overscroll-behavior-y: contain;
  min-height: 0;
  flex: 1 1 auto;
}
</style>

<style>
.track-browser-dialog-shell {
  width: min(96svw, 1320px);
  height: min(92svh, 880px);
}

.track-browser-dialog-shell.p-dialog-maximized {
  width: 100svw !important;
  max-width: 100svw !important;
  height: 100svh !important;
  max-height: 100svh !important;
  margin-top: 0 !important;
}

/* Mobile: fullscreen dialog */
.track-browser-dialog-shell--mobile {
  width: 100svw !important;
  max-width: 100svw !important;
  height: 100svh !important;
  max-height: 100svh !important;
  margin: 0 !important;
  border-radius: 0 !important;
}

.track-browser-dialog-shell--mobile > .p-dialog-content {
  overflow-y: auto !important;
  -webkit-overflow-scrolling: touch;
  padding: 0 !important;
}
</style>