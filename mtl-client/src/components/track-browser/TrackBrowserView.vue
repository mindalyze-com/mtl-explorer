<template>
  <div ref="rootEl" class="track-browser-view" :class="{ 'track-browser-view--compact': effectiveCompact }">
    <div v-if="$slots.toolbar" class="track-browser-view__toolbar">
      <slot name="toolbar" />
    </div>

    <TrackBrowserControls :query="query" :summary="summary" :total-count="totalCount" @update:query="query = $event" />

    <TrackBrowserTable
      :rows="rows"
      :selected-track-id="selectedTrackId ?? null"
      :query="query"
      :compact="effectiveCompact"
      :sort-reset-key="resetKey"
      @select-track="emit('select-track', $event)"
      @open-details="emit('open-details', $event)"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, toRef, watch } from 'vue';
import type { GpsTrack } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import TrackBrowserControls from './TrackBrowserControls.vue';
import TrackBrowserTable from './TrackBrowserTable.vue';
import { useTrackBrowser } from './useTrackBrowser';
import type { TrackSelectionEvents } from '@/components/filter/filterEvents';

defineOptions({ name: 'TrackBrowserView' });

const MOBILE_BREAKPOINT_PX = 768;

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

const emit = defineEmits<TrackSelectionEvents>();

const rootEl = ref<HTMLElement | null>(null);
const measuredCompact = ref(false);
const effectiveCompact = computed((): boolean => props.compact ?? measuredCompact.value);
const { query, rows, summary, totalCount } = useTrackBrowser(toRef(props, 'tracks'));
let resizeObserver: ResizeObserver | null = null;
let resizeFrame: number | null = null;
let pendingWidth = 0;

type TrackBrowserNavigationState = {
  query: string;
};

function getNavigationState(): TrackBrowserNavigationState {
  return { query: query.value };
}

function restoreNavigationState(state: unknown): void {
  if (!state || typeof state !== 'object' || !('query' in state) || typeof state.query !== 'string') return;
  query.value = state.query;
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
  display: flex;
  width: 100%;
  min-width: 0;
  min-height: 0;
  flex: 1 1 auto;
  flex-direction: column;
  overflow: hidden;
}

.track-browser-view__toolbar {
  flex: 0 0 auto;
  padding: 0.5rem var(--dlg-padding) 0;
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

  .track-browser-view__toolbar {
    padding: 0.45rem 0.75rem 0;
  }
}
</style>
