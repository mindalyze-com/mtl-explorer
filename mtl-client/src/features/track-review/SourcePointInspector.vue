<script setup lang="ts">
import { computed, ref, watch, onBeforeUnmount, nextTick, useId } from 'vue';
import type { ReviewPointPage, TrackFinding, TrackObservation } from 'x8ing-mtl-api-typescript-fetch';
import Button from 'primevue/button';
import Popover from 'primevue/popover';
import { getFormatLocale } from '@/composables/useLocale';
import { formatDateShort } from '@/utils/Utils';
import { reviewApi, reviewError, reasonLabel } from './api';
import { findingPointRange, belongsToFindingSection } from './findingLocation';
const props = withDefaults(
  defineProps<{ sourceId: number; sourceHash: string; pointIndex: number; finding?: TrackFinding; active?: boolean }>(),
  { finding: undefined, active: true }
);
defineEmits<{ close: [] }>();
const PAGE_SIZE = 30,
  CONTEXT_POINTS = 3;
const SOURCE_TIME_ZONE = 'UTC';
const findingHighlight = computed(() => {
  const finding = props.finding;
  const range = finding && findingPointRange(finding);
  if (!finding || !range) return null;
  const { before, after } = range;
  return {
    before,
    after,
    transition: before !== after,
    pointsLabel: before === after ? `Point ${after + 1}` : `Points ${before + 1} → ${after + 1}`,
    needsCheck: finding.severity !== 'INFORMATION',
    label: finding.reason ? reasonLabel(finding.reason) : 'Selected finding',
  };
});
function isHighlighted(point: TrackObservation) {
  const highlight = findingHighlight.value;
  return (
    !!highlight &&
    !!props.finding &&
    belongsToFindingSection(point, props.finding) &&
    (point.pointIndex === highlight.before || point.pointIndex === highlight.after)
  );
}
function formatPointTime(timestamp: Date) {
  return timestamp.toLocaleTimeString(getFormatLocale(), {
    timeZone: SOURCE_TIME_ZONE,
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23',
    fractionalSecondDigits: timestamp.getUTCMilliseconds() ? 3 : undefined,
  });
}
const inspector = ref<HTMLElement | null>(null);
const explanationId = useId();
const explanationPopover = ref<InstanceType<typeof Popover> | null>(null);
const explanationOpen = ref(false);
const explanationPoint = ref<number | null>(null);
const explanationClose = ref<HTMLButtonElement | null>(null);
let explanationTrigger: HTMLButtonElement | null = null;
function toggleExplanation(event: MouseEvent, pointIndex: number | undefined) {
  explanationPoint.value = pointIndex ?? null;
  explanationTrigger = event.currentTarget as HTMLButtonElement;
  explanationPopover.value?.toggle(event);
}
function closeExplanation() {
  explanationPopover.value?.hide();
  explanationTrigger?.focus({ preventScroll: true });
}
async function onExplanationShow() {
  if (!props.active) {
    explanationPopover.value?.hide();
    return;
  }
  explanationOpen.value = true;
  await nextTick();
  if (props.active && explanationOpen.value) explanationClose.value?.focus({ preventScroll: true });
}
function onExplanationHide() {
  explanationOpen.value = false;
  const overlay = document.getElementById(explanationId);
  if (
    props.active &&
    explanationTrigger?.isConnected &&
    (document.activeElement === document.body || overlay?.contains(document.activeElement))
  )
    explanationTrigger.focus({ preventScroll: true });
}
watch(
  () => props.active,
  (active) => {
    if (!active) explanationPopover.value?.hide();
  }
);
watch(
  () => props.finding,
  () => explanationPopover.value?.hide()
);
const page = ref<ReviewPointPage | null>(null),
  loading = ref(false),
  error = ref('');
let request: AbortController | null = null;
async function load(from: number) {
  explanationPopover.value?.hide();
  request?.abort();
  const current = new AbortController();
  request = current;
  loading.value = true;
  error.value = '';
  try {
    const result = await reviewApi().getSourceReviewPoints(
      { sourceId: props.sourceId, sourceHash: props.sourceHash, from: Math.max(0, from), limit: PAGE_SIZE },
      { signal: current.signal }
    );
    if (!current.signal.aborted) {
      page.value = result;
      await nextTick();
      inspector.value?.focus();
    }
  } catch (failure) {
    if (!current.signal.aborted) error.value = await reviewError(failure);
  } finally {
    if (request === current) loading.value = false;
  }
}
watch(
  () => [props.sourceId, props.sourceHash, props.pointIndex],
  () => {
    page.value = null;
    void load(props.pointIndex - CONTEXT_POINTS);
  },
  { immediate: true }
);
onBeforeUnmount(() => request?.abort());
</script>
<template>
  <section ref="inspector" tabindex="-1" class="point-inspector" aria-label="Original observations">
    <h3>Original observations</h3>
    <p v-if="finding" class="selected-finding">
      <strong>{{ finding.reason ? reasonLabel(finding.reason) : 'Selected finding' }}</strong>
      {{ finding.message }}
    </p>
    <p>
      Original GPS points in recorded order, with times in UTC. Red rows belong to the selected finding. Click an
      underlined point number for an explanation.
    </p>
    <p v-if="findingHighlight && (finding?.affectedCount ?? 0) > 1" class="finding-context">
      Showing the first reported {{ findingHighlight.transition ? 'transition' : 'point' }}. Other affected points may
      occur later in this section.
    </p>
    <p v-if="error" role="alert">{{ error }}</p>
    <div class="point-table" :aria-busy="loading">
      <table v-if="page">
        <thead>
          <tr>
            <th>Point</th>
            <th>Section</th>
            <th>Latitude</th>
            <th>Longitude</th>
            <th>Elevation (m)</th>
            <th>Date</th>
            <th>Time (UTC)</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="point in page.points"
            :key="point.pointIndex"
            :class="{
              selected: point.pointIndex === pointIndex,
              'finding-point': isHighlighted(point),
              'finding-point--check': isHighlighted(point) && findingHighlight?.needsCheck,
            }"
          >
            <th scope="row">
              <button
                v-if="isHighlighted(point) && findingHighlight?.needsCheck"
                type="button"
                class="point-number point-finding-link"
                :aria-label="`Explain ${findingHighlight.label} at point ${(point.pointIndex ?? 0) + 1}`"
                :aria-expanded="explanationOpen && explanationPoint === point.pointIndex"
                :aria-controls="explanationOpen && explanationPoint === point.pointIndex ? explanationId : undefined"
                aria-haspopup="dialog"
                :title="finding?.message || findingHighlight.label"
                @click="toggleExplanation($event, point.pointIndex)"
              >
                {{ (point.pointIndex ?? 0) + 1 }}
              </button>
              <span v-else class="point-number">{{ (point.pointIndex ?? 0) + 1 }}</span>
            </th>
            <td>{{ (point.sectionIndex ?? 0) + 1 }}</td>
            <td>{{ point.latitude ?? 'Missing' }}</td>
            <td>{{ point.longitude ?? 'Missing' }}</td>
            <td>{{ point.elevationMetres ?? 'Missing' }}</td>
            <td>{{ point.timestamp ? formatDateShort(point.timestamp, SOURCE_TIME_ZONE) : '—' }}</td>
            <td class="point-time" :title="point.sourceTimestamp ?? point.timestamp?.toISOString()">
              {{
                point.timestamp
                  ? formatPointTime(point.timestamp)
                  : point.sourceTimestamp
                    ? `Invalid: ${point.sourceTimestamp}`
                    : 'Missing'
              }}<span v-if="point.estimatedTime"> · Estimated</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div class="point-actions">
      <Button
        label="Previous points"
        severity="secondary"
        text
        :disabled="loading || !page?.fromPointIndex"
        @click="load((page?.fromPointIndex ?? 0) - PAGE_SIZE + CONTEXT_POINTS)"
      />
      <Button
        label="Next points"
        severity="secondary"
        text
        :disabled="
          loading || !page || (page.fromPointIndex ?? 0) + (page.points?.length ?? 0) >= (page.totalCount ?? 0)
        "
        @click="load((page?.fromPointIndex ?? 0) + PAGE_SIZE - CONTEXT_POINTS)"
      />
      <Button label="Close point inspector" severity="secondary" text @click="$emit('close')" />
    </div>
    <Popover
      v-if="findingHighlight?.needsCheck"
      :id="explanationId"
      ref="explanationPopover"
      append-to="body"
      aria-label="Finding explanation"
      @show="onExplanationShow"
      @hide="onExplanationHide"
    >
      <div class="point-finding-explanation">
        <strong>{{ findingHighlight.label }}</strong>
        <p>{{ findingHighlight.pointsLabel }}</p>
        <p v-if="finding?.message">{{ finding.message }}</p>
        <button ref="explanationClose" type="button" class="explanation-close" @click="closeExplanation">
          Close explanation
        </button>
      </div>
    </Popover>
  </section>
</template>
<style scoped>
.point-inspector {
  margin: 1rem 0;
  padding: 0.75rem;
  border: 1px solid var(--border-light);
  border-radius: 8px;
}
.point-table {
  overflow-x: auto;
  max-height: 360px;
}
table {
  border-collapse: collapse;
  font-size: 0.85rem;
  width: 100%;
}
th,
td {
  padding: 0.5rem;
  text-align: left;
  border-bottom: 1px solid var(--border-light);
  white-space: nowrap;
}
.selected:not(.finding-point--check),
.finding-point {
  background: var(--surface-hover);
}
.selected:not(.finding-point--check) {
  outline: 2px solid var(--accent);
  outline-offset: -2px;
}
.finding-point--check {
  background: var(--error-bg);
}
.finding-point--check > th {
  box-shadow: inset 3px 0 var(--error);
  color: var(--error-text);
}
.point-finding-link {
  display: inline;
  appearance: none;
  margin: 0;
  padding: 0;
  border: 0;
  border-radius: 0;
  min-width: 0;
  min-height: 0;
  background: none;
  color: inherit;
  font: inherit;
  line-height: inherit;
  letter-spacing: inherit;
  vertical-align: baseline;
  cursor: pointer;
  text-decoration: underline dotted;
  text-underline-offset: 0.18em;
}
.point-finding-link:hover {
  text-decoration-style: solid;
}
.point-finding-link:focus-visible {
  outline: 2px solid var(--error);
  outline-offset: 2px;
}
.point-finding-explanation {
  max-width: min(22rem, calc(100vw - 2rem));
  color: var(--text-primary);
  font-size: var(--text-sm-size);
}
.point-finding-explanation p {
  margin: 0.5rem 0;
}
.explanation-close {
  padding: 0.35rem 0.5rem;
  border: 1px solid var(--border-default);
  border-radius: 0.35rem;
  background: var(--surface-hover);
  color: var(--text-primary);
  font: inherit;
  cursor: pointer;
}
.finding-context {
  color: var(--text-secondary);
}
.point-time {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}
.point-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-top: 0.5rem;
}
</style>
