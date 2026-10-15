<template>
  <section
    :class="[
      'planner-summary',
      `planner-summary--${density}`,
      {
        'planner-summary--wide': wide,
        'planner-summary--updating': updating,
        'planner-summary--profile-hidden': !profileVisible,
        'planner-summary--essential': essentialActionsOnly,
        'planner-summary--empty-profile': contentSized && !result,
      },
    ]"
    :style="summaryStyle"
    aria-label="Route summary"
    :aria-busy="updating ? 'true' : undefined"
  >
    <!-- Expanded view: one controls area, followed by totals. The shared chart fills the rest. -->
    <div v-show="density === 'full'" class="planner-summary__expanded">
      <div ref="controlsEl" class="planner-summary__controls"><slot name="controls" /></div>
      <div v-if="viewportTooLarge || error" ref="fullNoticesEl" class="planner-summary__notices">
        <div v-if="viewportTooLarge" class="inline-notice planner-summary__notice planner-summary__notice--warn">
          <i class="bi bi-zoom-in" aria-hidden="true"></i>
          <span>Zoom in to start planning. Visible span must be under ~{{ formatDistanceSmart(planningSpanM) }}.</span>
        </div>
        <div v-if="error" class="inline-notice planner-summary__notice planner-summary__notice--error" role="alert">
          <i class="bi bi-exclamation-circle" aria-hidden="true"></i><span>{{ error }}</span>
          <PrimeButton v-if="canRetry" label="Retry" text :disabled="retryDisabled" @click="emit('retry')" />
        </div>
      </div>
      <div v-show="showStats" ref="fullRowEl" class="planner-summary__full-totals" aria-label="Route totals">
        <template v-if="showStats && result">
          <LiveStatsBar :stats="result.stats" :density="statsDensity" />
        </template>
        <p v-else class="planner-summary__hint">Route totals appear once a route is computed.</p>
        <p class="planner-summary__status" role="status">
          <template v-if="statusText"
            ><i :class="updating ? 'bi bi-arrow-repeat' : 'bi bi-clock-history'" aria-hidden="true"></i
            >{{ statusText }}</template
          >
        </p>
      </div>
    </div>
    <!-- Reduced view has its own markup and essential actions, outside the scrolling tools. -->
    <div
      v-if="viewportTooLarge || error"
      v-show="density !== 'full'"
      ref="reducedNoticesEl"
      class="planner-summary__notices planner-summary__reduced-notices"
    >
      <div v-if="viewportTooLarge" class="inline-notice planner-summary__notice planner-summary__notice--warn">
        <i class="bi bi-zoom-in" aria-hidden="true"></i>
        <span>Zoom in to start planning. Visible span must be under ~{{ formatDistanceSmart(planningSpanM) }}.</span>
      </div>
      <div v-if="error" class="inline-notice planner-summary__notice planner-summary__notice--error" role="alert">
        <i class="bi bi-exclamation-circle" aria-hidden="true"></i><span>{{ error }}</span>
        <PrimeButton v-if="canRetry" label="Retry" text :disabled="retryDisabled" @click="emit('retry')" />
      </div>
    </div>
    <div v-show="density !== 'full'" ref="reducedRowEl" class="planner-summary__row planner-summary__compact">
      <div class="planner-summary__totals">
        <template v-if="showStats && result">
          <LiveStatsBar :stats="result.stats" :density="'reduced'" />
          <p class="planner-summary__status" role="status">
            <template v-if="statusText"
              ><i :class="updating ? 'bi bi-arrow-repeat' : 'bi bi-clock-history'" aria-hidden="true"></i
              >{{ statusText }}</template
            >
          </p>
        </template>
        <p v-else class="planner-summary__hint">{{ hint }}</p>
      </div>
      <div class="planner-summary__actions" role="group" aria-label="Edit history">
        <PrimeButton
          v-if="selectionAction"
          severity="secondary"
          outlined
          class="planner-summary__button"
          :aria-label="selectionAction"
          :title="selectionAction"
          @click="emit('remove-selection')"
          ><i class="bi bi-trash" aria-hidden="true"
        /></PrimeButton>
        <PrimeButton
          severity="secondary"
          outlined
          class="planner-summary__button"
          :disabled="!canUndo"
          title="Undo (Ctrl/⌘ Z)"
          aria-label="Undo"
          @click="emit('undo')"
          ><i class="bi bi-arrow-counterclockwise" aria-hidden="true"
        /></PrimeButton>
      </div>
    </div>
    <!-- A single chart survives changes between the expanded and pinned hosts. -->
    <div
      v-show="profileVisible"
      id="planner-summary-profile"
      class="planner-summary__profile"
      data-sheet-native-gesture
    >
      <ElevationProfile
        v-if="result"
        :coordinates="result.coordinates"
        :total-distance-m="result.stats.distanceM"
        :ascent-m="result.stats.ascentM"
        :descent-m="result.stats.descentM"
        @hover="emit('hover', $event)"
      />
      <p v-else ref="profilePlaceholderEl" class="planner-summary__placeholder">
        <i class="bi bi-graph-up" aria-hidden="true"></i> Elevation profile appears once a route is computed.
      </p>
    </div>
  </section>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import PrimeButton from 'primevue/button';
import LiveStatsBar from '@/planner/components/LiveStatsBar.vue';
import ElevationProfile from '@/planner/components/ElevationProfile.vue';
import type { LiveStats } from '@/planner/types';
import {
  plannerProfileHeightPx,
  plannerTextScale,
  type PlannerPanelDensity,
  type PlannerStatsDensity,
  type PlannerSummaryMetrics,
} from '@/planner/composables/usePlannerPanelLayout';
import {
  MAX_PLANNING_SPAN_KM,
  PLANNER_BASE_FONT_PX,
  PLANNER_ESSENTIAL_SUMMARY_PADDING_PX,
  PLANNER_PROFILE_PLACEHOLDER_HEIGHT_PX,
  PLANNER_SIDE_PROFILE_MIN_PX,
  PLANNER_SUMMARY_ACTIONS_PX,
  PLANNER_SUMMARY_GAP_PX,
  PLANNER_SUMMARY_PADDING_PX,
  PLANNER_SUMMARY_TOTALS_MIN_PX,
} from '@/planner/constants/PlannerConstants';
import { observeElementResize } from '@/utils/observeElementResize';
import { formatDistanceSmart } from '@/utils/Utils';

export interface PlannerRouteResult {
  stats: LiveStats;
  coordinates: [number, number, number][];
}

const props = withDefaults(
  defineProps<{
    density: PlannerPanelDensity;
    wide: boolean;
    result: PlannerRouteResult | null;
    showStats: boolean;
    hint: string;
    updating: boolean;
    error: string | null;
    canRetry: boolean;
    viewportTooLarge: boolean;
    retryDisabled: boolean;
    canUndo: boolean;
    /** Accessible name of the remove action for the current selection, shown when tools are hidden. */
    selectionAction: string | null;
    /** Resolved by the panel layout from the available height. */
    statsDensity: PlannerStatsDensity;
    profileVisible: boolean;
    essentialActionsOnly?: boolean;
    textScale?: number;
    profileRequested?: boolean;
    contentSized?: boolean;
  }>(),
  { essentialActionsOnly: false, textScale: 1, profileRequested: true, contentSized: false }
);

const emit = defineEmits<{
  (event: 'undo'): void;
  (event: 'retry'): void;
  (event: 'remove-selection'): void;
  (event: 'hover', point: { lng: number; lat: number; elevationM: number; distanceM: number } | null): void;
  (event: 'metrics', metrics: PlannerSummaryMetrics): void;
}>();

const planningSpanM = MAX_PLANNING_SPAN_KM * 1000;
const fullRowEl = ref<HTMLElement | null>(null);
const controlsEl = ref<HTMLElement | null>(null);
const profilePlaceholderEl = ref<HTMLElement | null>(null);
const reducedRowEl = ref<HTMLElement | null>(null);
const fullNoticesEl = ref<HTMLElement | null>(null);
const reducedNoticesEl = ref<HTMLElement | null>(null);
const rowEl = computed(() => (props.density === 'full' ? fullRowEl.value : reducedRowEl.value));
const noticesEl = computed(() => (props.density === 'full' ? fullNoticesEl.value : reducedNoticesEl.value));
const statsDensity = computed(() => props.statsDensity);
const summaryStyle = computed(() => ({
  '--planner-summary-padding': `${(props.essentialActionsOnly ? PLANNER_ESSENTIAL_SUMMARY_PADDING_PX : PLANNER_SUMMARY_PADDING_PX) * props.textScale}px`,
  '--planner-summary-gap': `${PLANNER_SUMMARY_GAP_PX * props.textScale}px`,
  '--planner-profile-height': `${plannerProfileHeightPx(props.density === 'full' ? 'full' : 'compact', props.textScale)}px`,
  '--planner-totals-min': `${PLANNER_SUMMARY_TOTALS_MIN_PX / PLANNER_BASE_FONT_PX}rem`,
  '--planner-actions-width': `${PLANNER_SUMMARY_ACTIONS_PX / PLANNER_BASE_FONT_PX}rem`,
  '--planner-side-profile-min': `${PLANNER_SIDE_PROFILE_MIN_PX / PLANNER_BASE_FONT_PX}rem`,
}));
const statusText = computed(() => {
  if (props.updating) return props.result ? 'Updating' : 'Calculating';
  return props.result && props.canRetry && props.error ? 'Previous result' : '';
});

function publishMetrics() {
  const tools = controlsEl.value?.firstElementChild as HTMLElement | null;
  const gap = PLANNER_SUMMARY_GAP_PX * props.textScale;
  const noticePx = Math.round(noticesEl.value?.offsetHeight ?? 0);
  emit('metrics', {
    rowPx: Math.round(rowEl.value?.offsetHeight ?? 0),
    noticePx,
    statsDensity: props.density === 'full' ? props.statsDensity : 'reduced',
    textScale: plannerTextScale(),
    essentialActionsOnly: props.essentialActionsOnly,
    controlsPx: tools ? tools.scrollHeight + (props.showStats || noticePx ? gap : 0) : undefined,
    fullProfilePx: !props.profileRequested
      ? 0
      : props.result
        ? plannerProfileHeightPx('full', props.textScale)
        : profilePlaceholderEl.value?.offsetHeight || PLANNER_PROFILE_PLACEHOLDER_HEIGHT_PX * props.textScale,
    rowVisible: props.showStats,
  });
}

const stopObserving: Array<() => void> = [];
function observe(element: HTMLElement | null) {
  if (!element || typeof ResizeObserver === 'undefined') return;
  stopObserving.push(observeElementResize(element, publishMetrics));
}

watch(
  [
    rowEl,
    noticesEl,
    controlsEl,
    () => props.density,
    () => props.statsDensity,
    () => props.profileRequested,
    () => props.contentSized,
    () => props.showStats,
    () => props.hint,
    () => Boolean(props.result),
  ],
  ([row, notices]) => {
    while (stopObserving.length) stopObserving.pop()?.();
    observe(row);
    observe(notices);
    observe(controlsEl.value);
    if (row?.offsetHeight || (props.density === 'full' && !props.showStats)) publishMetrics();
  },
  { flush: 'post' }
);

onBeforeUnmount(() => {
  while (stopObserving.length) stopObserving.pop()?.();
});
</script>

<style scoped>
.planner-summary {
  display: flex;
  flex-direction: column;
  gap: var(--planner-summary-gap);
  padding: var(--planner-summary-padding) 1rem;
  min-width: 0;
}
.planner-summary__notices {
  display: flex;
  flex-direction: column;
  gap: var(--planner-summary-gap);
}
.planner-summary__notice {
  flex-wrap: wrap;
  border: 1px solid var(--border-default);
  color: var(--text-secondary);
  background: var(--surface-glass-light);
}
.planner-summary__notice i {
  flex-shrink: 0;
  font-size: var(--text-base-size);
}
.planner-summary__notice--warn {
  border-color: color-mix(in srgb, var(--viz-orange) 30%, var(--border-default));
  color: var(--warning-text);
  background: var(--warning-bg);
}
.planner-summary__notice--error {
  border-color: color-mix(in srgb, var(--error) 28%, var(--border-default));
  color: var(--error);
  background: var(--error-bg);
}
.planner-summary--compact .planner-summary__notice,
.planner-summary--totals .planner-summary__notice {
  flex-wrap: nowrap;
}
.planner-summary--compact .planner-summary__notice span,
.planner-summary--totals .planner-summary__notice span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.planner-summary--wide:not(.planner-summary--full):not(.planner-summary--totals):not(.planner-summary--profile-hidden) {
  display: grid;
  grid-template-columns:
    minmax(calc(var(--planner-totals-min) + 0.5rem + var(--planner-actions-width)), 2fr)
    minmax(var(--planner-side-profile-min), 3fr);
  align-items: start;
  gap: 0.75rem;
}
.planner-summary--full {
  flex: 1 1 auto;
  min-height: 0;
}
.planner-summary--full.planner-summary--profile-hidden {
  overflow-y: auto;
}
.planner-summary__expanded {
  display: flex;
  flex-direction: column;
  flex: 0 1 auto;
  min-height: 0;
  max-height: calc(100% - var(--planner-profile-height) - var(--planner-summary-gap));
  gap: var(--planner-summary-gap);
}
.planner-summary--profile-hidden .planner-summary__expanded {
  max-height: 100%;
}
.planner-summary__controls {
  display: flex;
  flex-direction: column;
  flex: 0 1 auto;
  min-height: min(100%, 3.25rem);
}
.planner-summary__full-totals {
  flex: 0 0 auto;
}
.planner-summary__reduced-notices {
  grid-column: 1 / -1;
}
.planner-summary__row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
  min-width: 0;
}
/* Actions move to their own row before the totals become narrower than their cards. */
.planner-summary__totals {
  flex: 1 1 var(--planner-totals-min);
  min-width: 0;
}
.planner-summary__totals :deep(.live-stats-bar) {
  transition: opacity 0.2s ease;
}
.planner-summary--updating :deep(.live-stats-bar) {
  opacity: 0.6;
}
.planner-summary__status {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  min-height: var(--text-2xs-lh);
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--text-2xs-size);
  line-height: var(--text-2xs-lh);
  font-weight: var(--font-semibold);
}
.planner-summary__hint {
  margin: 0;
  color: var(--text-muted);
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}
.planner-summary--compact .planner-summary__hint,
.planner-summary--totals .planner-summary__hint {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.planner-summary__actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 0.4rem;
  margin-left: auto;
}
.planner-summary__actions :deep(.p-button) {
  width: 2.75rem;
  min-height: 2.75rem;
  font-size: var(--text-sm-size);
}
.planner-summary--essential .planner-summary__totals {
  flex-basis: 0;
}
.planner-summary--essential :deep(.stat-card) {
  padding-block: 0;
  row-gap: 0;
}
.planner-summary--full .planner-summary__profile {
  flex: 1 0 var(--planner-profile-height);
  height: auto;
  min-height: var(--planner-profile-height);
}
.planner-summary--full.planner-summary--empty-profile .planner-summary__profile {
  flex: 0 0 auto;
  min-height: 0;
}
.planner-summary--full.planner-summary--empty-profile .planner-summary__expanded {
  max-height: 100%;
}
.planner-summary__profile {
  display: flex;
  flex-direction: column;
  height: var(--planner-profile-height);
  min-width: 0;
}
.planner-summary__placeholder {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  margin: 0;
  color: var(--text-muted);
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}
@media (max-width: 640px) {
  .planner-summary {
    padding-inline: 0.75rem;
  }
}
@media (prefers-reduced-motion: reduce) {
  .planner-summary__totals :deep(.live-stats-bar) {
    transition: none;
  }
}
</style>
