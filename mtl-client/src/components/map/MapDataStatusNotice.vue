<template>
  <Teleport to="body">
    <div
      v-if="status || selection || selectionLoading || feedbackMessage || feedbackError"
      ref="notice"
      class="map-data-status"
      :class="{ 'map-data-status--attention': !status || status.kind === 'attention' }"
      :style="{ top: `${top}px`, left: `${center}px`, maxWidth: `${maxWidth}px` }"
      @keydown.esc.stop="minimize()"
    >
      <span class="map-data-status__announcement" role="status">{{ status?.label }}</span>
      <template v-if="status">
        <button
          ref="chip"
          type="button"
          class="map-data-status__chip"
          :aria-expanded="expanded"
          :aria-controls="detailsId"
          @click="expanded ? minimize() : (expanded = true)"
        >
          <i :class="chipIcon" aria-hidden="true"></i>
          <span>{{ status.label }}</span>
          <i :class="expanded ? 'bi bi-chevron-up' : 'bi bi-chevron-down'" aria-hidden="true"></i>
        </button>
        <div v-if="expanded" :id="detailsId" class="map-data-status__details">
          <template v-if="status.kind === 'attention'">
            <p v-if="status.issueBreakdown" class="map-data-status__breakdown">{{ status.issueBreakdown }}</p>
            <p class="map-data-status__muted">{{ status.explanation }}</p>
          </template>
          <template v-else>
            <p>{{ status.explanation }}</p>
            <ul v-if="status.preparing || status.outOfSync || status.failedFiles">
              <li v-if="status.preparing">
                {{ statusUnconfirmed ? 'Last known: preparation was running.' : 'Preparation is still running.' }}
              </li>
              <li v-if="status.outOfSync">This map has earlier data.</li>
              <li v-if="status.failedFiles">
                {{ statusUnconfirmed ? 'Last known: ' : '' }}{{ status.failedFiles }}
                {{ status.failedFiles === 1 ? 'file needs' : 'files need' }} review.
              </li>
            </ul>
          </template>
          <div v-if="!selection && !selectionLoading" class="map-data-status__actions">
            <button
              v-if="status.kind !== 'attention'"
              type="button"
              class="map-data-status__primary"
              :disabled="reloading"
              @click="emit('reload')"
            >
              <i class="bi bi-arrow-clockwise" aria-hidden="true"></i>
              {{ reloading ? 'Reloading…' : 'Reload map' }}
            </button>
            <RouterLink
              v-if="status.failedFiles"
              :to="{ name: 'review' }"
              :class="{ 'map-data-status__primary': status.kind === 'attention' }"
              >Review</RouterLink
            >
            <RouterLink
              v-if="status.kind !== 'attention'"
              :to="{ name: 'admin', params: { section: status.detailsSection } }"
            >
              {{ status.detailsSection === 'processing' ? 'Processing details' : 'Data status' }}
            </RouterLink>
            <button
              v-if="status.kind === 'attention'"
              type="button"
              class="map-data-status__dismiss"
              :disabled="!demoAccess.writeAllowed || issueStore.busy || selectionLoading"
              @click="dismiss"
            >
              Dismiss
            </button>
            <span v-if="status.kind === 'attention' && demoAccess.demoMode === true" class="map-data-status__muted">
              {{ SHARED_DEMO_READ_ONLY_MESSAGE }}
            </span>
            <template v-if="demoAccess.error">
              <span class="map-data-status__muted" role="status">{{ demoAccess.error }}</span>
              <button type="button" :disabled="demoAccess.loading" @click="retryAccess">Retry access check</button>
            </template>
          </div>
        </div>
      </template>
      <div
        v-if="selection || selectionLoading"
        class="map-data-status__confirmation"
        role="group"
        :aria-labelledby="confirmationId"
      >
        <p v-if="selectionLoading" :id="confirmationId" role="status">Loading current issues…</p>
        <p v-else-if="selection?.targets?.length" :id="confirmationId">
          Dismiss {{ plural(selection.targets.length, 'issue') }} in
          {{ plural(selection.pendingFileCount ?? 0, 'file') }}?
        </p>
        <p v-else :id="confirmationId" role="status">Nothing left to dismiss.</p>
        <div class="map-data-status__actions">
          <button
            v-if="selection?.targets?.length"
            ref="confirmButton"
            type="button"
            class="map-data-status__primary"
            :disabled="!demoAccess.writeAllowed || issueStore.busy"
            @click="confirmDismiss"
          >
            {{ issueStore.busy ? 'Dismissing…' : 'Confirm' }}
          </button>
          <button ref="cancelButton" type="button" :disabled="issueStore.busy" @click="cancelSelection()">
            Cancel
          </button>
        </div>
      </div>
      <div
        v-if="feedbackMessage || feedbackError"
        class="map-data-status__undo"
        :class="{ 'map-data-status__undo--error': feedbackError }"
        :role="feedbackError ? 'alert' : 'status'"
      >
        <i :class="feedbackError ? 'bi bi-exclamation-triangle' : 'bi bi-check2-circle'" aria-hidden="true"></i>
        <span>{{ feedbackError || feedbackMessage }}</span>
        <button
          v-if="issueStore.undoTargets.length"
          type="button"
          :disabled="!demoAccess.writeAllowed || issueStore.busy"
          @click="undo"
        >
          {{ feedbackError ? 'Retry Undo' : 'Undo' }}
        </button>
        <button type="button" :disabled="issueStore.busy" aria-label="Dismiss message" @click="dismissFeedback">
          ×
        </button>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue';
import { RouterLink, useRoute } from 'vue-router';
import type { SourceIssueTargetsSnapshot } from 'x8ing-mtl-api-typescript-fetch';
import { useSourceIssueStore } from '@/stores/sourceIssueStore';
import { reviewError } from '@/features/track-review/api';
import { plural } from '@/features/track-review/sourceIssuePresentation';
import type { MapDataStatus } from './mapDataStatus';
import { SHARED_DEMO_READ_ONLY_MESSAGE, useDemoAccessStore } from '@/stores/demoAccessStore';

const props = defineProps<{
  status: MapDataStatus | null;
  reloading: boolean;
  mapElement: HTMLElement | null;
  placementKey: string;
}>();
const emit = defineEmits<{ (event: 'reload'): void }>();
const issueStore = useSourceIssueStore();
const demoAccess = useDemoAccessStore();
const route = useRoute();
const expanded = ref(false);
const statusUnconfirmed = computed(() => props.status?.kind === 'unavailable' || props.status?.kind === 'offline');
const chipIcon = computed(() =>
  props.status?.kind === 'offline'
    ? 'bi bi-wifi-off'
    : props.status?.kind === 'attention'
      ? 'bi bi-info-circle'
      : 'bi bi-exclamation-triangle'
);
const chip = ref<HTMLButtonElement | null>(null);
const notice = ref<HTMLElement | null>(null);
const detailsId = useId();
const confirmationId = useId();
const confirmButton = ref<HTMLButtonElement | null>(null);
const cancelButton = ref<HTMLButtonElement | null>(null);
const selection = ref<SourceIssueTargetsSnapshot | null>(null);
const selectionLoading = ref(false);
const actionError = ref('');
const feedbackMessage = computed(() => issueStore.feedbackMessage);
const feedbackError = computed(() => actionError.value || issueStore.feedbackError);
let selectionRequest = 0;
const MAP_NOTICE_TOP = 64;
const MAP_NOTICE_GAP = 8;
const MAP_NOTICE_MAX_WIDTH = 320;
const MAP_CONTROL_CLEARANCE = 96;
const OBSTACLE_SELECTOR =
  '.mtl-top-right, .measure-map-overlay, .geo-draw-toolbar, .mtl-map-downloading, .map-config-notice';
const top = ref(MAP_NOTICE_TOP);
const center = ref(window.innerWidth / 2);
const maxWidth = ref(MAP_NOTICE_MAX_WIDTH);
let observer: ResizeObserver | null = null;
let placementFrame: number | null = null;

function minimize(restoreFocus = true, navigating = false) {
  if (issueStore.busy && !navigating) return;
  expanded.value = false;
  cancelSelection(restoreFocus);
}

function cancelSelection(restoreFocus = true) {
  selectionRequest++;
  selection.value = null;
  selectionLoading.value = false;
  if (restoreFocus) void nextTick(() => chip.value?.focus());
}

function closeOnOutsidePointer(event: PointerEvent) {
  if (!expanded.value && !selection.value && !selectionLoading.value) return;
  if (event.target instanceof Node && !notice.value?.contains(event.target)) minimize(false);
}

async function retryAccess() {
  const trigger = document.activeElement;
  await demoAccess.load();
  await nextTick();
  if (!demoAccess.error && trigger && !trigger.isConnected && document.activeElement === document.body) {
    chip.value?.focus();
  }
}

async function dismiss() {
  if (!demoAccess.writeAllowed || issueStore.busy || selectionLoading.value) return;
  const currentRequest = ++selectionRequest;
  actionError.value = '';
  selectionLoading.value = true;
  try {
    const snapshot = await issueStore.prepare();
    if (selectionRequest !== currentRequest) return;
    selection.value = snapshot;
    selectionLoading.value = false;
    await nextTick();
    (confirmButton.value ?? cancelButton.value)?.focus();
  } catch (failure) {
    const message = await reviewError(failure);
    if (selectionRequest === currentRequest) actionError.value = message;
  } finally {
    if (selectionRequest === currentRequest) selectionLoading.value = false;
  }
}

async function confirmDismiss() {
  const targets = selection.value?.targets;
  if (!demoAccess.writeAllowed || issueStore.busy || !targets?.length) return;
  const currentRequest = selectionRequest;
  actionError.value = '';
  try {
    await issueStore.save(targets);
    if (currentRequest === selectionRequest) cancelSelection();
  } catch (failure) {
    actionError.value = await reviewError(failure);
  }
}

async function undo() {
  if (!demoAccess.writeAllowed || issueStore.busy || !issueStore.undoTargets.length) return;
  actionError.value = '';
  try {
    await issueStore.undo();
  } catch (failure) {
    actionError.value = await reviewError(failure);
  }
}

function dismissFeedback() {
  if (issueStore.busy) return;
  actionError.value = '';
  issueStore.dismissFeedback();
  void nextTick(() => chip.value?.focus());
}

function updatePlacement() {
  const map = props.mapElement?.getBoundingClientRect();
  if (!map) return;
  const width = Math.max(0, Math.min(MAP_NOTICE_MAX_WIDTH, map.width - MAP_CONTROL_CLEARANCE));
  const left = map.left + (map.width - width) / 2;
  let nextTop = map.top + MAP_NOTICE_TOP;
  const obstacles = [...document.querySelectorAll<HTMLElement>(OBSTACLE_SELECTOR)]
    .map((element) => element.getBoundingClientRect())
    .filter((rect) => rect.width > 0 && rect.height > 0 && rect.left < left + width && rect.right > left)
    .sort((a, b) => a.top - b.top);
  for (const rect of obstacles) {
    if (rect.top <= nextTop + MAP_NOTICE_GAP && rect.bottom + MAP_NOTICE_GAP > nextTop) {
      nextTop = rect.bottom + MAP_NOTICE_GAP;
    }
  }
  top.value = Math.round(nextTop);
  center.value = Math.round(map.left + map.width / 2);
  maxWidth.value = width;
}

function schedulePlacement() {
  if (placementFrame !== null) cancelAnimationFrame(placementFrame);
  placementFrame = requestAnimationFrame(() => {
    placementFrame = null;
    updatePlacement();
  });
}

async function observePlacement() {
  await nextTick();
  observer?.disconnect();
  if (props.mapElement) observer?.observe(props.mapElement);
  document.querySelectorAll(OBSTACLE_SELECTOR).forEach((element) => observer?.observe(element));
  schedulePlacement();
}

watch(
  () => [props.status, feedbackMessage.value, feedbackError.value, props.placementKey, props.mapElement],
  observePlacement,
  { flush: 'post' }
);
watch(
  () => issueStore.sessionRevision,
  () => {
    cancelSelection(false);
    actionError.value = '';
  }
);
watch(
  () => route.fullPath,
  () => minimize(false, true)
);
watch(
  () => props.placementKey,
  () => minimize(false, true)
);
watch(
  () => props.status,
  (status) => {
    if (!status) expanded.value = false;
  }
);
onMounted(() => {
  void demoAccess.load();
  observer = new ResizeObserver(schedulePlacement);
  window.addEventListener('resize', schedulePlacement);
  document.addEventListener('pointerdown', closeOnOutsidePointer, true);
  void observePlacement();
});
onBeforeUnmount(() => {
  selectionRequest++;
  observer?.disconnect();
  window.removeEventListener('resize', schedulePlacement);
  document.removeEventListener('pointerdown', closeOnOutsidePointer, true);
  if (placementFrame !== null) cancelAnimationFrame(placementFrame);
});
</script>

<style scoped>
.map-data-status {
  position: fixed;
  z-index: var(--z-freshness-banner);
  width: max-content;
  max-width: min(20rem, calc(100% - 6rem));
  transform: translateX(-50%);
  border: 1px solid color-mix(in srgb, var(--warning) 48%, var(--border-subtle));
  border-radius: 1.4rem;
  background: var(--surface-sheet-solid);
  color: var(--text-primary);
  box-shadow: var(--shadow-md);
  font-size: 0.82rem;
}
.map-data-status:has(.map-data-status__details, .map-data-status__confirmation, .map-data-status__undo) {
  width: min(20rem, calc(100% - 6rem));
  border-radius: 0.85rem;
}
.map-data-status__chip {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.55rem;
  width: 100%;
  min-height: 44px;
  padding: 0.5rem 0.8rem;
  border: 0;
  border-radius: inherit;
  background: transparent;
  color: inherit;
  font: inherit;
  font-weight: 600;
  cursor: pointer;
}
.map-data-status__chip > i:first-child {
  color: var(--warning);
}
.map-data-status--attention {
  border-color: var(--border-subtle);
}
.map-data-status--attention .map-data-status__chip > i:first-child {
  color: var(--accent);
}
.map-data-status__chip > i:last-child {
  font-size: 0.65rem;
}
.map-data-status__details {
  max-height: calc(100dvh - v-bind('top + "px"') - 7rem);
  overflow-y: auto;
  padding: 0 0.85rem 0.65rem;
  border-top: 1px solid var(--border-subtle);
  line-height: 1.5;
}
.map-data-status__details p {
  margin: 0.75rem 0;
}
.map-data-status__details .map-data-status__breakdown {
  margin-bottom: 0.25rem;
  font-weight: 600;
}
.map-data-status__details .map-data-status__muted {
  margin-top: 0;
  color: var(--text-secondary);
}
.map-data-status__error {
  color: var(--error);
}
.map-data-status__details ul {
  margin: 0 0 0.75rem;
  padding-left: 1.1rem;
  color: var(--text-secondary);
}
.map-data-status__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.35rem 0.6rem;
}
.map-data-status__actions button,
.map-data-status__actions a {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  min-height: 44px;
  padding: 0.4rem 0.6rem;
  border: 0;
  border-radius: 0.5rem;
  font: inherit;
  cursor: pointer;
}
.map-data-status__actions .map-data-status__primary {
  background: var(--accent);
  color: var(--accent-contrast);
}
.map-data-status__primary:disabled {
  opacity: 0.65;
  cursor: wait;
}
.map-data-status__actions a:not(.map-data-status__primary) {
  color: var(--accent);
  text-decoration: none;
}
.map-data-status__dismiss {
  margin-left: auto;
  background: transparent;
  color: var(--text-secondary);
}
.map-data-status__dismiss:hover:not(:disabled) {
  background: var(--surface-hover);
}
.map-data-status__confirmation {
  padding: 0.65rem 0.85rem;
  border-top: 1px solid var(--border-subtle);
  line-height: 1.5;
}
.map-data-status__confirmation p {
  margin: 0 0 0.5rem;
}
.map-data-status__undo {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  min-height: 44px;
  padding: 0.25rem 0.4rem 0.25rem 0.8rem;
  font-weight: 600;
  border-top: 1px solid var(--border-subtle);
}
.map-data-status__undo > i {
  color: var(--success);
}
.map-data-status__undo--error,
.map-data-status__undo--error > i {
  color: var(--error);
}
.map-data-status__undo span {
  flex: 1;
  min-width: 0;
  overflow-wrap: anywhere;
}
.map-data-status__undo button {
  min-height: 44px;
  padding: 0.3rem 0.6rem;
  border: 0;
  border-radius: 1rem;
  background: transparent;
  color: var(--accent);
  font: inherit;
  cursor: pointer;
  white-space: nowrap;
}
.map-data-status button:focus-visible,
.map-data-status a:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}
.map-data-status__announcement {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}
</style>
