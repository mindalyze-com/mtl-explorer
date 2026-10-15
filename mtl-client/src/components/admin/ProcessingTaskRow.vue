<template>
  <article
    :class="['processing-task', `processing-task--${state}`, { 'processing-task--has-attention': hasAttention }]"
  >
    <div class="processing-task__summary">
      <span class="processing-task__icon"><i :class="icon" aria-hidden="true" /></span>
      <div class="processing-task__copy">
        <div class="processing-task__name">
          <span>{{ label }}</span>
          <button
            v-if="explanation"
            type="button"
            class="processing-task__info"
            :aria-label="`About ${label}`"
            :aria-expanded="infoOpen"
            :aria-controls="infoOpen ? infoId : undefined"
            aria-haspopup="dialog"
            @click="infoPopover?.toggle($event)"
          >
            <i class="bi bi-info-circle" aria-hidden="true" />
          </button>
        </div>
        <div class="processing-task__caption">
          <slot name="summary">{{ summary }}</slot>
        </div>
      </div>
      <div class="processing-task__meter">
        <div class="processing-task__measure">
          <span class="processing-task__status"><i :class="statusIcon" aria-hidden="true" />{{ statusLabel }}</span>
          <span
            v-if="needsAttention && state !== 'attention' && state !== 'unavailable'"
            class="processing-task__attention"
          >
            <i class="bi bi-exclamation-triangle" aria-hidden="true" /> Needs attention
          </span>
          <span class="processing-task__value">
            <span v-if="progressScope">{{ progressScope }}</span>
            <span>{{ measuredProgress !== null ? `${measuredProgress}%` : progressLabel }}</span>
          </span>
        </div>
        <div
          v-if="measuredProgress !== null && segments"
          class="processing-task__track processing-task__track--segmented"
          role="progressbar"
          aria-valuemin="0"
          aria-valuemax="100"
          :aria-valuenow="measuredProgress"
          :aria-valuetext="segmentsText || `${measuredProgress}%`"
          :aria-label="[label, progressScope].filter(Boolean).join(' · ')"
        >
          <span class="processing-segment processing-segment--done" :style="segmentStyle(measuredProgress)" />
          <span class="processing-segment processing-segment--ready" :style="segmentStyle(segments.ready)" />
          <span class="processing-segment processing-segment--retrying" :style="segmentStyle(segments.retrying)" />
          <span class="processing-segment processing-segment--waiting" :style="segmentStyle(segments.waiting)" />
        </div>
        <progress
          v-else-if="measuredProgress !== null"
          :value="measuredProgress"
          max="100"
          :aria-label="[label, progressScope].filter(Boolean).join(' · ')"
        />
        <div
          v-else-if="indeterminate"
          class="processing-task__track processing-task__track--indeterminate"
          role="progressbar"
          :aria-label="[label, progressScope].filter(Boolean).join(' · ')"
          aria-valuetext="In progress; total work is unknown"
        >
          <span />
        </div>
        <div v-else class="processing-task__track" aria-hidden="true">
          <span v-if="state === 'ready' || state === 'complete'" />
        </div>
      </div>
      <button
        v-if="$slots.default"
        type="button"
        class="processing-task__toggle"
        :aria-label="`${expanded ? 'Hide' : 'Show'} ${label} details`"
        :aria-expanded="expanded"
        :aria-controls="detailsId"
        @click="expanded = !expanded"
      >
        Details <i :class="expanded ? 'bi bi-chevron-up' : 'bi bi-chevron-down'" aria-hidden="true" />
      </button>
    </div>
    <div v-if="$slots.notice" class="processing-task__notice"><slot name="notice" /></div>
    <div v-if="$slots.default" v-show="expanded" :id="detailsId" class="processing-task__details">
      <slot />
    </div>
    <Popover
      v-if="explanation"
      :id="infoId"
      ref="infoPopover"
      append-to="body"
      :aria-label="`About ${label}`"
      @show="infoOpen = true"
      @hide="infoOpen = false"
    >
      <div class="processing-task-about">
        <strong>{{ label }}</strong>
        <p>{{ explanation }}</p>
      </div>
    </Popover>
  </article>
</template>

<script setup lang="ts">
import { computed, ref, useId } from 'vue';
import Popover from 'primevue/popover';
import './processingTasks.css';
import { processingIcon, type ProcessingRowState, type ProcessingSegments } from '@/utils/processingJobPresentation';

const props = withDefaults(
  defineProps<{
    label: string;
    icon: string;
    state: ProcessingRowState;
    statusLabel: string;
    summary?: string;
    explanation?: string;
    progress?: number | null;
    indeterminate?: boolean;
    progressScope?: string;
    progressLabel?: string;
    needsAttention?: boolean;
    /** Outstanding work split into shares of the total; the remainder after progress. */
    segments?: ProcessingSegments | null;
    segmentsText?: string;
  }>(),
  {
    summary: '',
    explanation: '',
    progress: null,
    indeterminate: false,
    progressScope: '',
    progressLabel: '',
    needsAttention: false,
    segments: null,
    segmentsText: '',
  }
);
/** Keeps a small share visible as a sliver instead of rounding it away. */
const MIN_VISIBLE_SEGMENT = '2px';
function segmentStyle(share: number) {
  return share > 0 ? { flexBasis: `max(${share}%, ${MIN_VISIBLE_SEGMENT})` } : { display: 'none' };
}
const expanded = defineModel<boolean>('expanded', { default: false });
const detailsId = useId();
const infoId = useId();
const infoOpen = ref(false);
const infoPopover = ref<InstanceType<typeof Popover> | null>(null);
const measuredProgress = computed(() =>
  props.progress !== null && Number.isFinite(props.progress) ? Math.max(0, Math.min(100, props.progress)) : null
);
const hasAttention = computed(
  () => props.needsAttention || props.state === 'attention' || props.state === 'unavailable'
);
const statusIcon = computed(() => processingIcon(props.state));
</script>
