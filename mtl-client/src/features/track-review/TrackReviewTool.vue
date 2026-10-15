<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter, RouterLink } from 'vue-router';
import {
  GetTrackReviewQueueStateEnum as State,
  TrackFindingReasonEnum as Reason,
  TrackFindingSeverityEnum as Severity,
  GetTrackReviewQueueDecisionEnum as LastDecision,
  type TrackReviewQueue,
  type TrackFinding,
  type SourceReviewDetail,
  type SourceReviewItem,
  type ReviewDecisionRequest,
} from 'x8ing-mtl-api-typescript-fetch';
import BottomSheet from '@/components/ui/BottomSheet.vue';
import Button from 'primevue/button';
import SourceFindings from './SourceFindings.vue';
import SourcePathComparison from './SourcePathComparison.vue';
import SourcePointInspector from './SourcePointInspector.vue';
import SourceUsageSelect from './SourceUsageSelect.vue';
import SourceIssueReview from './SourceIssueReview.vue';
import SourceIssueStatus from './SourceIssueStatus.vue';
import SourceIssueFeedback from './SourceIssueFeedback.vue';
import SourceFileInfo from './SourceFileInfo.vue';
import { useSourceIssueStore } from '@/stores/sourceIssueStore';
import { SHARED_DEMO_READ_ONLY_MESSAGE, useDemoAccessStore } from '@/stores/demoAccessStore';
import { formatLocaleNumber } from '@/utils/Utils';
import { reviewApi, reviewError, saveDownload, stateLabel, reasonLabel, decisionLabel } from './api';

const emit = defineEmits<{
  'tool-opened': [];
  'tool-closed': [];
  'pending-count': [count: number];
  'data-changed': [];
}>();
const issueStore = useSourceIssueStore();
const demoAccess = useDemoAccessStore();
const route = useRoute(),
  router = useRouter();
const isOpen = ref(false),
  busy = ref(false),
  assessing = ref(false);
const queue = ref<TrackReviewQueue | null>(null),
  detail = ref<SourceReviewDetail | null>(null);
const issueRefreshVersion = ref(0);
const inspectedPoint = ref<number | null>(null);
const inspectedFinding = ref<TrackFinding>();
const pathComparison = ref<InstanceType<typeof SourcePathComparison> | null>(null);
function inspectFinding(finding: TrackFinding) {
  inspectedFinding.value = finding;
  inspectedPoint.value = finding.firstPointIndex ?? null;
  void pathComparison.value?.centerFinding?.(finding, finding.firstPointIndex, false);
}
const state = ref<State | ''>(State.NeedsReview),
  search = ref(''),
  offset = ref(0);
const reason = ref<Reason | ''>(''),
  severity = ref<Severity | ''>(''),
  decisionFilter = ref<LastDecision | ''>('');
const error = ref(''),
  message = ref('');
const errorNotice = ref<HTMLElement | null>(null);
watch(error, async (value) => {
  if (!value) return;
  await nextTick();
  errorNotice.value?.focus();
});
const moreFilters = ref(false);
const activeExtraFilters = computed(() => [reason.value, severity.value, decisionFilter.value].filter(Boolean).length);
const PAGE_SIZE = 50,
  COUNT_REFRESH_MS = 60_000;
let refreshTimer: ReturnType<typeof setInterval> | undefined;
let queueRequest: AbortController | null = null,
  detailRequest: AbortController | null = null;
let detailGeneration = 0;
function invalidateDetailOperation() {
  detailRequest?.abort();
  busy.value = false;
  return ++detailGeneration;
}
const sourceId = computed(() => detail.value?.source?.sourceId);
const mode = ref<'issues' | 'recordings'>('issues');
const pendingIssueViewCount = ref<number | null>(null);
const issuesBadgeCount = computed(() =>
  mode.value === 'issues' ? pendingIssueViewCount.value : issueStore.pendingFileCount
);
// Snapshot of the list the file was opened from; kept stable while acknowledging moves files out of it.
const navigationIds = ref<number[]>([]);
const navigationPosition = computed(() => {
  const position = sourceId.value == null ? -1 : navigationIds.value.indexOf(sourceId.value);
  return navigationIds.value.length > 1 ? position : -1;
});
const nextId = computed(() =>
  navigationPosition.value >= 0 ? navigationIds.value[navigationPosition.value + 1] : undefined
);
const issueIndex = computed(() => (typeof route.query?.index === 'string' ? route.query.index : undefined));
const issueSource = computed(() => Number(route.query?.source) || undefined);
watch(
  () =>
    [
      route.name,
      route.query?.view,
      route.query?.file,
      route.query?.state,
      route.query?.index,
      route.query?.source,
    ] as const,
  ([routeName, view, fileName, requestedState, index, source], previous) => {
    if (routeName !== 'review') return;
    if (view === 'recordings' || fileName || requestedState) mode.value = 'recordings';
    else if (view === 'issues' || index || source || previous?.[0] !== 'review') mode.value = 'issues';
  },
  { immediate: true }
);
const requestedFile = computed(() => (typeof route.query?.file === 'string' ? route.query.file : ''));
const uploadedFileFilter = computed(
  () => !!requestedFile.value && search.value === requestedFile.value && !state.value && !activeExtraFilters.value
);
watch(
  () => [route.name, route.query?.file, route.query?.state] as const,
  ([routeName, fileName, requestedState]) => {
    if (routeName !== 'review') return;
    if (typeof fileName === 'string' && fileName) {
      search.value = fileName;
      state.value = '';
    } else if (typeof requestedState === 'string' && Object.values(State).includes(requestedState as State)) {
      search.value = '';
      state.value = requestedState as State;
    } else return;
    reason.value = '';
    severity.value = '';
    decisionFilter.value = '';
    offset.value = 0;
    if (isOpen.value) void loadQueue();
  },
  { immediate: true }
);
const assessmentLimited = computed(() => detail.value?.source?.reasons?.includes('ASSESSMENT_LIMIT') ?? false);
const canInclude = computed(
  () => !!detail.value?.totalPointCount || (assessmentLimited.value && !!detail.value?.tracks?.length)
);
const canDecideSections = computed(
  () => !!detail.value?.tracks?.length && detail.value.tracks.every((track) => track.sectionIndex != null)
);
const UNKNOWN_OBSERVATION_REASONS = ['SOURCE_UNREADABLE', 'CONVERSION_FAILED', 'PARSE_FAILED'];
function observationCount(source: SourceReviewItem | undefined) {
  if (!source || source.reasons?.some((reason) => UNKNOWN_OBSERVATION_REASONS.includes(reason)))
    return 'Observation count unavailable';
  if (source.reasons?.includes('ASSESSMENT_LIMIT'))
    return source.pointCount ? `${source.pointCount} observations reported by import` : 'Observation count unavailable';
  if (source.state === State.NotAssessed) return 'Observation count unavailable';
  return `${source.pointCount ?? 0} original observations`;
}
const choices = Object.values(State);
const savedUseDecision = computed(() => {
  const sections = detail.value?.sections ?? [];
  const first = sections[0]?.useDecision;
  return first && sections.every((section) => section.useDecision === first) ? first : undefined;
});
const hasProcessingCorrections = computed(
  () =>
    detail.value?.diagnosis?.findings?.some((finding) =>
      ['POSITION_CORRECTION', 'ELEVATION_CORRECTION'].includes(finding.reason ?? '')
    ) ?? false
);

async function loadQueue() {
  queueRequest?.abort();
  const request = new AbortController();
  queueRequest = request;
  try {
    const result = await reviewApi().getTrackReviewQueue(
      {
        state: state.value || undefined,
        search: search.value,
        reason: reason.value || undefined,
        severity: severity.value || undefined,
        decision: decisionFilter.value || undefined,
        offset: offset.value,
        limit: PAGE_SIZE,
      },
      { signal: request.signal }
    );
    if (!request.signal.aborted) {
      queue.value = result;
      emit('pending-count', issueStore.pendingFileCount);
    }
  } catch (failure) {
    if (!request.signal.aborted && isOpen.value) error.value = await reviewError(failure);
  }
}
async function loadDetail(id: number) {
  const generation = invalidateDetailOperation();
  const request = new AbortController();
  detailRequest = request;
  busy.value = true;
  error.value = '';
  message.value = '';
  try {
    const result = await reviewApi().getSourceReview({ sourceId: id }, { signal: request.signal });
    if (generation === detailGeneration) {
      detail.value = result;
      issueRefreshVersion.value++;
      inspectedPoint.value = null;
      inspectedFinding.value = undefined;
    }
  } catch (failure) {
    const text = await reviewError(failure);
    if (generation === detailGeneration) error.value = text;
  } finally {
    if (generation === detailGeneration) busy.value = false;
  }
}
function open() {
  isOpen.value = true;
  emit('tool-opened');
  void loadQueue();
  const id = Number(route.params.sourceId);
  if (Number.isFinite(id) && id > 0) void loadDetail(id);
}
function close() {
  invalidateDetailOperation();
  isOpen.value = false;

  assessing.value = false;
}
function toggle() {
  if (isOpen.value) close();
  else open();
}
function back() {
  invalidateDetailOperation();
  detail.value = null;
  message.value = '';
  error.value = '';
  void router.push({ name: 'review', query: { ...route.query } });
}
function select(id: number | undefined, listIds?: number[]) {
  if (issueStore.busy) return;
  if (id != null) {
    if (listIds) navigationIds.value = listIds;
    void router.push({ name: 'review', params: { sourceId: id }, query: { ...route.query } });
    void loadDetail(id);
  }
}
function step(delta: number) {
  const id = navigationIds.value[navigationPosition.value + delta];
  if (navigationPosition.value >= 0 && id != null) select(id);
}
function onAcknowledged(id: number) {
  if (id !== sourceId.value || Number(route.params.sourceId) !== id || route.name !== 'review' || issueStore.busy)
    return;
  const name = detail.value?.source?.fileName ?? 'File';
  if (nextId.value == null) return;
  select(nextId.value);
  message.value = `${name} acknowledged.`;
}
watch(
  () => [route.name, route.params.sourceId] as const,
  ([, value]) => {
    if (route.name !== 'review') {
      invalidateDetailOperation();
      return;
    }
    const id = Number(value);
    if (Number.isFinite(id) && id > 0 && id !== sourceId.value) void loadDetail(id);
    else if (!value) {
      invalidateDetailOperation();
      detail.value = null;
      message.value = '';
      error.value = '';

      inspectedPoint.value = null;
      busy.value = false;
    }
  }
);
watch(
  () => issueStore.revision,
  () => {
    void loadQueue();
    if (sourceId.value) void loadDetail(sourceId.value);
  }
);
watch([state, reason, severity, decisionFilter], () => {
  offset.value = 0;
  void loadQueue();
});
onMounted(() => {
  void demoAccess.load();
  void loadQueue();
  refreshTimer = setInterval(() => {
    if (!document.hidden) void loadQueue();
  }, COUNT_REFRESH_MS);
});
onBeforeUnmount(() => {
  clearInterval(refreshTimer);
  queueRequest?.abort();
  invalidateDetailOperation();
  assessing.value = false;
});
defineExpose({
  open,
  close,
  toggle,
  getNavigationState: () => ({ sourceId: sourceId.value }),
  restoreNavigationState: (saved?: { sourceId?: number }) => {
    if (saved?.sourceId) void loadDetail(saved.sourceId);
  },
});

async function assess() {
  if (!demoAccess.writeAllowed || sourceId.value == null || busy.value || issueStore.busy) return;
  const id = sourceId.value;
  const generation = invalidateDetailOperation();
  busy.value = true;
  error.value = '';
  message.value = '';
  try {
    const result = await reviewApi().assessTrackSource({ sourceId: id });
    if (generation === detailGeneration) {
      detail.value = result;
      issueRefreshVersion.value++;
    }
    emit('data-changed');
    await loadQueue();
  } catch (failure) {
    const text = await reviewError(failure);
    if (generation === detailGeneration) error.value = text;
  } finally {
    if (generation === detailGeneration) busy.value = false;
  }
}
async function assessUnassessed() {
  if (!demoAccess.writeAllowed || assessing.value) return;
  assessing.value = true;
  error.value = '';
  let completed = 0,
    retainedOffset = 0;
  try {
    while (assessing.value) {
      const page = await reviewApi().getTrackReviewQueue({
        state: State.NotAssessed,
        offset: retainedOffset,
        limit: PAGE_SIZE,
      });
      if (!page.sources?.length) break;
      for (const source of page.sources) {
        if (!assessing.value || source.sourceId == null) break;
        const result = await reviewApi().assessTrackSource({ sourceId: source.sourceId });
        if (result.source?.state === State.NotAssessed) retainedOffset++;
        completed++;
        message.value = `Checked ${completed} source ${completed === 1 ? 'file' : 'files'}${retainedOffset ? `; ${retainedOffset} ${retainedOffset === 1 ? 'remains' : 'remain'} unassessed because of review limits` : ''}.`;
      }
      await loadQueue();
    }
  } catch (failure) {
    error.value = await reviewError(failure);
  } finally {
    assessing.value = false;
    if (completed > 0) {
      issueRefreshVersion.value++;
      emit('data-changed');
    }
    await loadQueue();
  }
}
async function decide(decision: ReviewDecisionRequest['decision'], sectionIndex?: number) {
  if (!demoAccess.writeAllowed || sourceId.value == null || busy.value || issueStore.busy) return;
  const id = sourceId.value;
  const sourceHash = detail.value?.source?.sourceHash;
  const generation = invalidateDetailOperation();
  busy.value = true;
  error.value = '';
  message.value = '';
  try {
    const result = await reviewApi().decideTrackSourceReview({
      sourceId: id,
      reviewDecisionRequest: { sourceHash, sectionIndex, decision },
    });
    if (generation === detailGeneration) {
      detail.value = result;
      message.value = 'Review decision saved. The source file was not changed.';
    }
    emit('data-changed');
    await loadQueue();
  } catch (failure) {
    const text = await reviewError(failure);
    if (generation === detailGeneration) error.value = text;
  } finally {
    if (generation === detailGeneration) busy.value = false;
  }
}
async function downloadOriginal() {
  if (sourceId.value == null) return;
  const id = sourceId.value;
  const fileName = detail.value?.source?.fileName ?? 'original-source.gpx';
  const generation = detailGeneration;
  error.value = '';
  try {
    saveDownload(await reviewApi().downloadReviewedOriginal({ sourceId: id }), fileName);
  } catch (failure) {
    const text = await reviewError(failure);
    if (generation === detailGeneration) error.value = text;
  }
}
async function downloadGpx() {
  if (sourceId.value == null) return;
  const id = sourceId.value;
  const fileName = (detail.value?.source?.fileName ?? 'recording').replace(/\.[^.]+$/, '') + '.gpx';
  const generation = detailGeneration;
  error.value = '';
  try {
    saveDownload(await reviewApi().downloadReviewedGpx({ sourceId: id }), fileName);
  } catch (failure) {
    const text = await reviewError(failure);
    if (generation === detailGeneration) error.value = text;
  }
}
</script>

<template>
  <BottomSheet
    v-model="isOpen"
    title="Review"
    icon="bi bi-clipboard-check"
    :back-label="detail ? 'Back to review queue' : ''"
    :content-key="sourceId ?? 'queue'"
    :detents="[
      { id: 'compact', height: '25vh' },
      { id: 'medium', height: '50vh' },
      { id: 'review', height: 'min(960px, 92vh)' },
    ]"
    initial-detent="review"
    free-resize
    :drag-to-close="false"
    :no-backdrop="false"
    sheet-class="sheet--solid-over-map"
    @back="back"
    @closed="emit('tool-closed')"
  >
    <div class="track-review sheet-scroll-target" data-test="track-review">
      <p v-if="demoAccess.demoMode === true && (mode === 'recordings' || detail)" class="review-muted">
        {{ SHARED_DEMO_READ_ONLY_MESSAGE }}
      </p>
      <p v-if="error" ref="errorNotice" role="alert" tabindex="-1" class="review-error">{{ error }}</p>
      <p v-if="message" role="status" class="review-message">{{ message }}</p>
      <template v-if="!detail">
        <div class="review-modes" role="group" aria-label="Review view">
          <button type="button" :aria-pressed="mode === 'issues'" @click="mode = 'issues'">
            Issues
            <span v-if="issuesBadgeCount" class="review-badge">{{ formatLocaleNumber(issuesBadgeCount) }}</span>
          </button>
          <button type="button" :aria-pressed="mode === 'recordings'" @click="mode = 'recordings'">
            GPS recordings
          </button>
        </div>
        <SourceIssueReview
          v-if="mode === 'issues' && isOpen"
          :source-id="issueSource"
          :index="issueIndex"
          @pending-count="emit('pending-count', $event)"
          @view-pending-count="pendingIssueViewCount = $event"
          @open="select"
        />
        <div v-else-if="mode === 'recordings'" class="review-recordings">
          <p class="review-muted">
            Check recordings for unusual positions, gaps and timestamps, and choose whether their tracks appear on the
            map and in statistics.
          </p>
          <p v-if="uploadedFileFilter" class="review-muted">
            Showing the uploaded or saved file across all review states. Indexing may take a moment; refresh results if
            it has not appeared yet.
          </p>
          <p class="review-counts">
            All recordings:
            <strong>{{ queue?.needsReviewCount ?? 0 }}</strong>
            {{ queue?.needsReviewCount === 1 ? 'needs' : 'need' }} review · {{ queue?.excludedCount ?? 0 }} excluded ·
            {{ queue?.unassessedCount ?? 0 }} not assessed
          </p>
          <div class="review-actions">
            <template v-if="queue?.unassessedCount || assessing">
              <Button
                v-if="!assessing"
                label="Assess unassessed sources"
                size="small"
                severity="secondary"
                outlined
                title="Reads files without changing or reimporting them."
                :disabled="!demoAccess.writeAllowed"
                @click="assessUnassessed"
              />
              <Button
                v-else
                label="Stop after this source"
                size="small"
                severity="secondary"
                outlined
                @click="assessing = false"
              />
            </template>
            <RouterLink :to="{ name: 'admin', params: { section: 'imports' } }">Upload a recording</RouterLink>
            <Button
              label="Refresh"
              icon="bi bi-arrow-clockwise"
              size="small"
              severity="secondary"
              text
              @click="loadQueue"
            />
          </div>
          <form
            class="review-filters"
            @submit.prevent="
              offset = 0;
              loadQueue();
            "
          >
            <label
              >Review state<select v-model="state">
                <option value="">All states</option>
                <option v-for="choice in choices" :key="choice" :value="choice">{{ stateLabel(choice) }}</option>
              </select></label
            >
            <label>File name<input v-model="search" type="search" placeholder="Search source files" /></label
            ><Button type="submit" label="Search" severity="secondary" outlined />
            <Button
              :label="`More filters${activeExtraFilters ? ` (${activeExtraFilters})` : ''}`"
              severity="secondary"
              text
              :aria-expanded="moreFilters"
              aria-controls="review-more-filters"
              @click="moreFilters = !moreFilters"
            />
            <div v-show="moreFilters" id="review-more-filters" class="review-filters__more">
              <label
                >Finding<select v-model="reason">
                  <option value="">All findings</option>
                  <option v-for="value in Object.values(Reason)" :key="value" :value="value">
                    {{ reasonLabel(value) }}
                  </option>
                </select></label
              >
              <label
                >Severity<select v-model="severity">
                  <option value="">All severities</option>
                  <option :value="Severity.Review">Serious finding</option>
                  <option :value="Severity.Warning">Warning</option>
                  <option :value="Severity.Information">Information</option>
                </select></label
              >
              <label
                >Latest decision<select v-model="decisionFilter">
                  <option value="">Any decision</option>
                  <option v-for="choice in Object.values(LastDecision)" :key="choice" :value="choice">
                    {{ decisionLabel(choice) }}
                  </option>
                </select></label
              >
            </div>
          </form>
          <p v-if="decisionFilter" class="review-muted">
            Shows the last choice saved for this section. New problems may still need review.
          </p>
          <ul class="review-sources">
            <li v-for="source in queue?.sources" :key="source.sourceId">
              <button
                type="button"
                @click="
                  select(
                    source.sourceId,
                    (queue?.sources ?? []).flatMap((item) => (item.sourceId == null ? [] : [item.sourceId]))
                  )
                "
              >
                <span
                  ><strong>{{ source.fileName }}</strong
                  ><small
                    >{{ source.affectedTrackCount }} derived
                    {{ source.affectedTrackCount === 1 ? 'track' : 'tracks' }} ·
                    {{ observationCount(source) }}
                    · {{ source.reviewFindingCount ?? 0 }} review
                    {{ source.reviewFindingCount === 1 ? 'finding' : 'findings' }}
                    <template v-if="source.informationFindingCount">
                      · {{ source.informationFindingCount }} informational</template
                    ></small
                  ><small v-if="source.reviewReasons?.length">{{
                    source.reviewReasons.map(reasonLabel).join(' · ')
                  }}</small>
                  <small v-if="source.informationReasons?.length" class="review-muted"
                    >Information: {{ source.informationReasons.map(reasonLabel).join(' · ') }}</small
                  ></span
                ><span>{{ stateLabel(source.state) }} <i class="bi bi-chevron-right" aria-hidden="true"></i></span>
              </button>
            </li>
          </ul>
          <div v-if="queue && !queue.sources?.length" class="review-empty">
            <strong>{{
              uploadedFileFilter
                ? 'This file is not listed yet'
                : state === State.NeedsReview && !search && !activeExtraFilters
                  ? 'No recordings waiting for review'
                  : 'No matching recordings'
            }}</strong>
            <p>
              {{
                uploadedFileFilter
                  ? 'Indexing and assessment may still be running. Refresh results to check again.'
                  : state === State.NeedsReview && !search && !activeExtraFilters
                    ? 'You can check other review states or assess files that have not been checked yet.'
                    : 'Try a different file name or clear the filters.'
              }}
            </p>
            <Button
              v-if="search || activeExtraFilters"
              label="Clear filters"
              severity="secondary"
              text
              @click="
                search = '';
                reason = '';
                severity = '';
                decisionFilter = '';
                offset = 0;
                loadQueue();
              "
            />
          </div>
          <div class="review-actions">
            <Button
              label="Previous"
              severity="secondary"
              text
              :disabled="!offset"
              @click="
                offset -= PAGE_SIZE;
                loadQueue();
              "
            /><Button
              label="Next"
              severity="secondary"
              text
              :disabled="(queue?.sources?.length ?? 0) < PAGE_SIZE"
              @click="
                offset += PAGE_SIZE;
                loadQueue();
              "
            />
          </div>
        </div>
      </template>
      <template v-else>
        <header class="review-file-header">
          <div class="review-file-title">
            <h2>{{ detail.source?.fileName }}</h2>
            <p class="review-muted">
              <span class="review-state">{{ stateLabel(detail.source?.state) }}</span>
              · {{ observationCount(detail.source) }} · {{ detail.tracks?.length ?? 0 }} derived
              {{ detail.tracks?.length === 1 ? 'track' : 'tracks' }}
            </p>
          </div>
          <div v-if="navigationPosition >= 0" class="review-pager">
            <Button
              icon="bi bi-chevron-left"
              aria-label="Previous file"
              severity="secondary"
              text
              :disabled="navigationPosition === 0 || busy || issueStore.busy"
              @click="step(-1)"
            />
            <span>{{ navigationPosition + 1 }} of {{ navigationIds.length }}</span>
            <Button
              icon="bi bi-chevron-right"
              aria-label="Next file"
              severity="secondary"
              text
              :disabled="nextId == null || busy || issueStore.busy"
              @click="step(1)"
            />
          </div>
        </header>
        <SourceFileInfo
          :key="`${sourceId}-${detail.source?.sourceHash}`"
          :active="isOpen"
          :tracks="detail.tracks ?? []"
        />
        <SourceIssueFeedback />
        <SourceIssueStatus
          v-if="sourceId != null"
          :source-id="sourceId"
          :source-hash="detail.source?.sourceHash"
          :disabled="busy || issueStore.busy"
          :has-next="nextId != null"
          :refresh-version="issueRefreshVersion"
          @acknowledged="onAcknowledged"
          @refresh="loadDetail"
          @reassess="assess"
        />

        <SourcePathComparison
          v-if="!assessmentLimited && !detail.previewUnavailableReason"
          ref="pathComparison"
          :points="detail.originalPoints ?? []"
          :tracks="detail.tracks ?? []"
          :source-hash="detail.source?.sourceHash"
          :source-id="sourceId"
        />
        <p v-else-if="detail.previewUnavailableReason">{{ detail.previewUnavailableReason }}</p>
        <p v-else>This file is too large to preview. Its tracks are still available below.</p>
        <SourceFindings
          :diagnosis="detail.diagnosis"
          :inspectable="!assessmentLimited && !detail.previewUnavailableReason"
          @select="inspectFinding"
          @center="(finding, pointIndex) => pathComparison?.centerFinding(finding, pointIndex)"
        />
        <p class="review-muted">
          These checks identify possible problems. They cannot determine whether every point was recorded or recover a
          missing path.
        </p>
        <SourcePointInspector
          v-if="inspectedPoint != null && sourceId != null && detail.source?.sourceHash"
          :active="isOpen"
          :source-id="sourceId"
          :source-hash="detail.source.sourceHash"
          :point-index="inspectedPoint"
          :finding="inspectedFinding"
          @close="inspectedPoint = null"
        />
        <section v-if="detail.diagnosis" class="review-card">
          <h3>Map and statistics</h3>
          <p class="review-muted">
            Choose where tracks from this file appear. Findings stay visible, and hidden tracks remain available here
            and in the track browser.
          </p>
          <p v-if="savedUseDecision && detail.source?.state === State.NeedsReview" class="review-muted">
            Saved use settings remain active: {{ decisionLabel(savedUseDecision) }}.
          </p>
          <SourceUsageSelect
            :key="`${sourceId}-${detail.source?.sourceHash}-${detail.source?.state}`"
            :decision="savedUseDecision"
            :state="detail.source?.state"
            :disabled="!demoAccess.writeAllowed || busy || issueStore.busy"
            :can-include="canInclude"
            label="Show tracks from this file in"
            @save="decide($event)"
          />
          <p class="review-muted">Tracks left out of statistics are also left out of highlights.</p>
          <div v-if="(detail.sections?.length ?? 0) > 1" class="review-sections">
            <p v-if="!canDecideSections" class="review-muted">
              These tracks cannot be set per section. Choose settings for the whole file.
            </p>
            <article v-for="section in detail.sections" :key="section.sectionIndex">
              <strong>Section {{ (section.sectionIndex ?? 0) + 1 }} · {{ section.name }}</strong>
              <p class="review-muted">{{ stateLabel(section.state) }} · {{ section.pointCount }} observations</p>
              <SourceUsageSelect
                v-if="canDecideSections"
                :state="section.state"
                :decision="section.useDecision"
                :disabled="!demoAccess.writeAllowed || busy || issueStore.busy"
                :can-include="!!section.pointCount"
                :label="`Use section ${(section.sectionIndex ?? 0) + 1}`"
                @save="decide($event, section.sectionIndex)"
              />
            </article>
          </div>
        </section>
        <details class="review-disclosure">
          <summary>How to fix this file</summary>
          <p v-if="hasProcessingCorrections">
            These are corrections made during import. Compare the original and processed tracks above before you decide
            to edit the file.
          </p>
          <ol>
            <li v-if="detail.canDownloadGpx">
              Download the GPX and open it in a track editor. Use the findings and point numbers above to check the
              recording.
            </li>
            <li v-else>
              Download the original source and open it in a tool that supports its format. GPX download is currently
              unavailable.
            </li>
            <li>
              Save the fixed file in the GPS folder. For GPX files, replace the original. For other formats, move the
              original out of the folder and save the fixed GPX there, so both are not imported.
            </li>
            <li>Once it is imported, reassess it here. Reassessing alone does not import a new file.</li>
          </ol>
          <p v-if="detail.sourcePath">
            Source file on the server: <code class="review-source-path">{{ detail.sourcePath }}</code>
          </p>
          <div class="review-actions">
            <Button
              v-if="detail.canDownloadGpx"
              label="Download GPX"
              icon="bi bi-download"
              size="small"
              :disabled="busy || issueStore.busy"
              @click="downloadGpx"
            />
            <Button
              label="Download original source"
              size="small"
              severity="secondary"
              outlined
              :disabled="busy || issueStore.busy"
              @click="downloadOriginal"
            />
            <Button
              label="Reassess current file"
              size="small"
              severity="secondary"
              text
              :loading="busy"
              :disabled="!demoAccess.writeAllowed || busy || issueStore.busy"
              @click="assess"
            />
          </div>
        </details>
        <details v-if="detail.tracks?.length" class="review-disclosure">
          <summary>Affected tracks ({{ detail.tracks.length }})</summary>
          <ul>
            <li v-for="track in detail.tracks" :key="track.trackId">
              <RouterLink :to="{ name: 'track-detail', params: { id: track.trackId } }">{{
                track.name || `Track #${track.trackId}`
              }}</RouterLink
              ><template v-if="track.sectionIndex != null"> · Section {{ track.sectionIndex + 1 }}</template> ·
              {{ stateLabel(track.reviewStatus) }}
            </li>
          </ul>
        </details>
        <details v-if="detail.decisions?.length" class="review-disclosure">
          <summary>Decision history</summary>
          <ul>
            <li v-for="(decision, index) in detail.decisions" :key="index">
              {{ decision.decidedAt?.toLocaleString() }} · Section {{ (decision.sectionIndex ?? 0) + 1 }} ·
              {{ decisionLabel(decision.decision) }}
            </li>
          </ul>
        </details>
      </template>
    </div>
  </BottomSheet>
</template>
<style scoped>
.track-review {
  padding: 1rem 1.25rem 2rem;
  color: var(--text-primary);
}
h2 {
  font-size: 1.15rem;
  overflow-wrap: anywhere;
}
h3 {
  font-size: 1rem;
  margin: 1.25rem 0 0.5rem;
}
.review-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem;
  margin: 0.85rem 0;
}
.review-muted {
  color: var(--text-secondary);
  font-size: 0.85rem;
}
.review-filters {
  display: flex;
  align-items: end;
  gap: 0.75rem;
  flex-wrap: wrap;
  margin: 1rem 0;
}
.review-filters__more {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  width: 100%;
}
.review-filters label {
  display: flex;
  flex: 1;
  min-width: 150px;
  flex-direction: column;
  gap: 0.4rem;
}
input,
select {
  padding: 0.6rem;
  min-height: 42px;
  color: var(--text-primary);
  background: var(--surface-card);
  border: 1px solid var(--border-medium);
  border-radius: 6px;
}
.review-sources {
  list-style: none;
  padding: 0;
}
.review-sources button {
  width: 100%;
  display: flex;
  justify-content: space-between;
  text-align: left;
  align-items: center;
  gap: 1rem;
  padding: 1rem 0.5rem;
  color: var(--text-primary);
  background: transparent;
  border: 0;
  border-bottom: 1px solid var(--border-light);
  cursor: pointer;
}
.review-sources button:hover {
  background: var(--surface-hover);
}
.review-sources strong {
  overflow-wrap: anywhere;
}
.review-sources small {
  display: block;
  color: var(--text-secondary);
  margin-top: 0.3rem;
}
.review-sources button > span:last-child {
  flex-shrink: 0;
  font-size: 0.85rem;
}
.review-error {
  padding: 0.75rem;
  border: 1px solid #b91c1c;
  color: #b91c1c;
  border-radius: 6px;
}
.review-modes {
  display: inline-flex;
  gap: 0.25rem;
  margin-bottom: 1rem;
  padding: 0.25rem;
  border-radius: 999px;
  background: var(--surface-hover);
}
.review-modes button {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  min-height: 36px;
  padding: 0.3rem 0.9rem;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: var(--text-secondary);
  font: inherit;
  font-size: 0.9rem;
  cursor: pointer;
}
.review-modes button[aria-pressed='true'] {
  background: var(--surface-sheet-solid);
  color: var(--text-primary);
  font-weight: 600;
  box-shadow: var(--shadow-sm);
}
.review-badge {
  min-width: 1.4rem;
  padding: 0 0.4rem;
  border-radius: 999px;
  background: var(--accent);
  color: var(--accent-contrast);
  font-size: 0.75rem;
  text-align: center;
}
.review-message {
  color: var(--text-secondary);
}
.review-counts {
  margin: 0.5rem 0 0;
}
.review-file-header {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.5rem 1rem;
}
.review-file-title {
  min-width: 0;
}
.review-file-title h2 {
  margin: 0 0 0.25rem;
}
.review-file-title p {
  margin: 0;
}
.review-state {
  font-weight: 600;
  color: var(--text-primary);
}
.review-pager {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  color: var(--text-secondary);
  font-size: 0.85rem;
  white-space: nowrap;
}
.review-card {
  margin: 1.25rem 0;
  padding: 0.25rem 1rem 0.75rem;
  border: 1px solid var(--border-default);
  border-radius: 8px;
}
.review-card h3 {
  margin-top: 0.85rem;
}
.review-disclosure {
  border-top: 1px solid var(--border-default);
  padding: 0.25rem 0;
}
.review-disclosure summary {
  display: flex;
  align-items: center;
  min-height: 44px;
  font-weight: 600;
  cursor: pointer;
}
.review-disclosure ol {
  padding-left: 1.25rem;
  line-height: 1.5;
}
.review-empty {
  padding: 1.25rem;
  border: 1px solid var(--border-default);
  border-radius: 8px;
}
.review-empty p {
  color: var(--text-secondary);
}
.track-review button:focus-visible,
summary:focus-visible,
input:focus-visible,
select:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 3px;
}
.review-sections article {
  padding: 0.75rem;
  border: 1px solid var(--border-light);
  border-radius: 8px;
  margin: 0.5rem 0;
}
.review-source-path {
  display: block;
  overflow-wrap: anywhere;
  margin-top: 0.4rem;
}
@media (max-width: 600px) {
  .track-review {
    padding: 0.75rem;
  }
  .review-sources button {
    align-items: start;
    flex-direction: column;
    gap: 0.4rem;
  }
}
</style>
