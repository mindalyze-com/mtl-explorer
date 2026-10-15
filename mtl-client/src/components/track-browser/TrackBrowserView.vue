<template>
  <div
    ref="rootEl"
    data-sheet-fit-target
    class="track-browser-view"
    :class="{ 'track-browser-view--compact': effectiveCompact }"
  >
    <div class="track-browser-view__header">
      <div v-if="$slots.toolbar" class="track-browser-view__toolbar">
        <slot name="toolbar" />
      </div>
      <TrackBrowserControls :query="query" @update:query="query = $event" />
    </div>

    <TrackBrowserTable
      ref="table"
      :rows="rows"
      :selected-track-id="selectedTrackId ?? null"
      :query="query"
      :compact="effectiveCompact"
      :sort-reset-key="resetKey"
      @select-track="emit('select-track', $event)"
      @open-details="emit('open-details', $event)"
      @track-updated="onTrackUpdated"
    >
      <template #summary>
        <TrackBrowserSummaryBar compact :summary="summary" :query="query" :total-count="totalCount" />
      </template>
    </TrackBrowserTable>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import type { GpsTrack } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import TrackBrowserControls from './TrackBrowserControls.vue';
import TrackBrowserTable from './TrackBrowserTable.vue';
import TrackBrowserSummaryBar from './TrackBrowserSummaryBar.vue';
import { useTrackBrowser } from './useTrackBrowser';
import { useMediaQuery } from '@/composables/useMediaQuery';
import type { TrackSelectionEvents } from '@/components/filter/filterEvents';

defineOptions({ name: 'TrackBrowserView' });

const MOBILE_BREAKPOINT_PX = 768;
const LANDSCAPE_MAX_WIDTH_PX = 1023;
const LANDSCAPE_MAX_HEIGHT_PX = 500;

const props = withDefaults(
  defineProps<{
    tracks: GpsTrack[];
    selectedTrackId?: number | string | null;
    compact?: boolean | null;
    resetKey?: number;
  }>(),
  {
    selectedTrackId: null,
    compact: null,
    resetKey: 0,
  }
);

const emit = defineEmits<TrackSelectionEvents & { 'track-updated': [track: GpsTrack] }>();

const rootEl = ref<HTMLElement | null>(null);
const table = ref<InstanceType<typeof TrackBrowserTable> | null>(null);
const measuredCompact = ref(false);
const shortLandscape = useMediaQuery(
  `(max-width: ${LANDSCAPE_MAX_WIDTH_PX}px) and (max-height: ${LANDSCAPE_MAX_HEIGHT_PX}px)`
);
const effectiveCompact = computed((): boolean => props.compact ?? (measuredCompact.value || shortLandscape.value));
const trackUpdates = ref(new Map<number, GpsTrack>());
const currentTracks = computed(() => props.tracks.map((track) => trackUpdates.value.get(Number(track.id)) ?? track));
const { query, rows, summary, totalCount } = useTrackBrowser(currentTracks);

function onTrackUpdated(track: GpsTrack) {
  const original = currentTracks.value.find((entry) => entry.id === track.id);
  if (track.id == null || !original || (original.version ?? 0) > (track.version ?? 0)) return;
  const updated = { ...original, ...track };
  trackUpdates.value.set(track.id, updated);
  emit('track-updated', updated);
}

watch(
  () => props.tracks,
  (tracks) => {
    for (const [id, updated] of trackUpdates.value) {
      const incoming = tracks.find((track) => track.id === id);
      if (!incoming || (incoming.version != null && incoming.version >= (updated.version ?? 0))) {
        trackUpdates.value.delete(id);
      }
    }
  }
);
let resizeObserver: ResizeObserver | null = null;
let resizeFrame: number | null = null;
let pendingWidth = 0;

type TrackBrowserNavigationState = {
  query: string;
  sort?: ReturnType<InstanceType<typeof TrackBrowserTable>['getSortState']>;
};

function getNavigationState(): TrackBrowserNavigationState {
  return { query: query.value, sort: table.value?.getSortState() };
}

function restoreNavigationState(state: unknown): void {
  if (!state || typeof state !== 'object' || !('query' in state) || typeof state.query !== 'string') return;
  query.value = state.query;
  if ('sort' in state) table.value?.restoreSortState(state.sort);
}

function scheduleCompactMode(width: number): void {
  if (width <= 0) return;
  pendingWidth = width;
  if (resizeFrame != null) return;
  resizeFrame = window.requestAnimationFrame(() => {
    resizeFrame = null;
    const compact = pendingWidth < MOBILE_BREAKPOINT_PX;
    if (measuredCompact.value !== compact) measuredCompact.value = compact;
  });
}

watch(
  () => props.resetKey,
  () => {
    query.value = '';
  }
);

onMounted(() => {
  const root = rootEl.value;
  if (!root || props.compact != null) return;
  scheduleCompactMode(root.clientWidth);
  if (typeof ResizeObserver === 'undefined') return;
  resizeObserver = new ResizeObserver((entries) => scheduleCompactMode(entries[0]?.contentRect.width ?? 0));
  resizeObserver.observe(root);
});

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  if (resizeFrame != null) window.cancelAnimationFrame(resizeFrame);
});

defineExpose({ getNavigationState, restoreNavigationState });
</script>

<style scoped>
.track-browser-view {
  --track-browser-gutter: var(--dlg-padding);
  container: track-browser / inline-size;
  display: flex;
  width: 100%;
  min-width: 0;
  min-height: 0;
  flex: 1 1 auto;
  flex-direction: column;
  overflow: hidden;
}

.track-browser-view__header {
  display: flex;
  flex: 0 0 auto;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem 1.5rem;
  padding: 0.5rem var(--track-browser-gutter) 0;
}

.track-browser-view__toolbar {
  min-width: 0;
}

.track-browser-view--compact {
  --track-browser-gutter: 0.75rem;
}

@container track-browser (max-width: 600px) {
  .track-browser-view__header {
    flex-direction: column;
    align-items: stretch;
  }

  .track-browser-controls {
    flex-basis: auto;
    max-width: none;
  }
}

.track-browser-view--compact {
  overflow-y: auto;
  overflow-x: hidden;
  overscroll-behavior-y: contain;
  -webkit-overflow-scrolling: touch;
}

@media screen and (max-width: 768px) {
  .track-browser-view {
    overflow-y: auto;
    overflow-x: hidden;
    overscroll-behavior-y: contain;
    -webkit-overflow-scrolling: touch;
  }
}
</style>
