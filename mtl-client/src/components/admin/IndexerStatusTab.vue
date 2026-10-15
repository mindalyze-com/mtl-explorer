<template>
  <div class="indexer-tab processing-list">
    <p v-if="isIndexerStatusPollingHealthy === false" class="processing-detail-warning" role="status">
      Could not update processing status. Showing the last received data.
    </p>
    <section v-if="pendingSourceCount" class="processing-attention" aria-label="Needs your review">
      <i class="bi bi-info-circle" aria-hidden="true"></i>
      <div>
        <strong
          >{{ formatCount(pendingSourceCount) }}
          {{ pendingSourceCount === 1 ? 'file needs' : 'files need' }} review</strong
        >
        <span>{{ pendingBreakdown }}</span>
      </div>
      <RouterLink class="processing-attention__action" :to="{ name: 'review' }">Review</RouterLink>
    </section>
    <section class="processing-group" aria-label="Imports and synchronization">
      <h3>Imports &amp; sync</h3>
      <slot name="connections" />
      <ProcessingJobRow
        v-if="syntheticHealth"
        :id="rowId('JOB:demoHealth')"
        v-model:expanded="expandedRows['JOB:demoHealth']"
        :class="highlightClass('JOB:demoHealth')"
        :job="syntheticHealth"
        :clock-offset-ms="statusClockOffsetMs"
        :dependencies="dependencies"
        :notice="overview.rows.get('demoHealth')"
        :holding-up="holdingUp('JOB:demoHealth')"
        :target-label="targetLabel"
        @reveal="revealRow"
      />
      <ProcessingTaskRow
        v-for="s in summaries"
        :id="rowId(`INDEX:${s.index}`)"
        :key="s.index"
        v-model:expanded="expandedRows[`INDEX:${s.index}`]"
        :class="highlightClass(`INDEX:${s.index}`)"
        :label="indexLabel(s.index)"
        :icon="s.index === 'GPS' ? 'bi bi-file-earmark-arrow-up' : 'bi bi-images'"
        :state="
          s.discoveryInProgress
            ? 'running'
            : workState(s.pending, (s.pendingIssueFileCount ?? 0) + (s.operationalFailed ?? 0))
        "
        :needs-attention="(s.pendingIssueFileCount ?? 0) + (s.operationalFailed ?? 0) > 0"
        :status-label="
          s.discoveryInProgress
            ? 'Discovering'
            : workStatus(s.pending, (s.pendingIssueFileCount ?? 0) + (s.operationalFailed ?? 0))
        "
        :explanation="indexExplanation(s.index)"
        :progress="!s.discoveryInProgress && s.total > 0 ? s.progressPercent : null"
        :indeterminate="s.discoveryInProgress"
        :progress-label="s.discoveryInProgress ? 'Discovering files' : s.total > 0 ? 'Complete' : 'No files'"
      >
        <template #summary>
          <span v-if="s.discoveryInProgress">Discovering files. The total is still being checked.</span>
          <span :class="{ 'processing-task__counts': s.discoveryInProgress }">
            {{ formatCount(s.completed) }}
            <template v-if="!s.discoveryInProgress"> of {{ formatCount(s.total) }} </template>
            files processed
            <template v-if="s.failed">
              ·
              {{ formatCount(s.failed) }}
              failed
            </template>
            <template v-if="s.skipped"> · {{ formatCount(s.skipped) }} skipped</template>
            <template v-if="s.acknowledgedIssueFileCount">
              · {{ formatCount(s.acknowledgedIssueFileCount) }} acknowledged</template
            >
          </span>
        </template>
        <template v-if="holdingUp(`INDEX:${s.index}`).length" #notice>
          <ProcessingHoldingUp :jobs="holdingUp(`INDEX:${s.index}`)" :target-label="targetLabel" @reveal="revealRow" />
        </template>
        <div class="processing-detail-heading"><h4>Current work</h4></div>
        <p class="processing-detail-copy">{{ indexExplanation(s.index) }}</p>
        <ProcessingJobDependencies
          :id="s.index ?? ''"
          kind="INDEX"
          :waiting-dependents="holdingUp(`INDEX:${s.index}`)"
          :dependencies="dependencies ?? []"
          :target-label="targetLabel"
          @reveal="revealRow"
        />
        <dl class="processing-facts">
          <div>
            <dt>Completed</dt>
            <dd>
              {{ formatCount(s.completed) }}
            </dd>
          </div>
          <div>
            <dt>Pending</dt>
            <dd>
              {{ formatCount(s.pending) }}
            </dd>
          </div>
          <div>
            <dt>Failed</dt>
            <dd>
              {{ formatCount(s.failed) }}
            </dd>
          </div>
          <div v-if="s.skipped">
            <dt>Skipped</dt>
            <dd>{{ formatCount(s.skipped) }}</dd>
          </div>
          <div v-if="s.removed">
            <dt>Removed</dt>
            <dd>{{ formatCount(s.removed) }}</dd>
          </div>
          <div v-if="s.excluded">
            <dt>Excluded</dt>
            <dd>{{ formatCount(s.excluded) }}</dd>
          </div>
          <div>
            <dt>Total files</dt>
            <dd>{{ formatCount(s.total) }}</dd>
          </div>
        </dl>
        <p v-if="s.pendingIssueFileCount" class="processing-detail-copy">
          <RouterLink :to="{ name: 'review', query: { index: s.index } }"
            >Review {{ indexFileLabel(s.index, s.pendingIssueFileCount) }}</RouterLink
          >
        </p>
        <details class="processing-technical">
          <summary>Technical details</summary>
          <dl>
            <dt>Indexer</dt>
            <dd>{{ s.index }}</dd>
            <dt>Total files</dt>
            <dd>{{ formatCount(s.total) }}</dd>
            <dt>Removed</dt>
            <dd>
              {{ formatCount(s.removed) }}
            </dd>
            <dt>Excluded</dt>
            <dd>
              {{ formatCount(s.excluded) }}
            </dd>
          </dl>
          <FileIndexerFailures
            v-if="s.failed > 0 && s.index"
            :index="s.index"
            :count="s.failed"
            :refresh-key="refreshKey"
          />
          <FileIndexerFailures
            v-if="s.skipped && s.index"
            :index="s.index"
            :count="s.skipped"
            outcome="SKIPPED"
            :refresh-key="refreshKey"
          />
        </details>
      </ProcessingTaskRow>
    </section>

    <section v-if="trackJobs.length" class="processing-group" aria-label="Background processing">
      <h3>Background processing</h3>
      <div v-if="overview.shared.length" class="processing-shared-wait" role="status">
        <i class="bi bi-clock" aria-hidden="true" />
        <div>
          <strong
            >{{ overview.sharedJobs.length }} steps wait for
            <template v-for="(blocker, index) in overview.shared" :key="blockerTarget(blocker) ?? index">
              <template v-if="index"> and </template>
              <button type="button" @click="revealRow(blockerTarget(blocker)!)">{{ targetLabel(blocker) }}</button>
            </template></strong
          >
          <span>{{ sharedResume }}</span>
        </div>
      </div>
      <ProcessingJobRow
        v-for="j in trackJobs"
        :id="rowId(`JOB:${j.job}`)"
        :key="j.job"
        v-model:expanded="expandedRows[`JOB:${j.job}`]"
        :class="highlightClass(`JOB:${j.job}`)"
        :job="j"
        :clock-offset-ms="statusClockOffsetMs"
        :dependencies="dependencies"
        :notice="overview.rows.get(j.job ?? '')"
        :holding-up="holdingUp(`JOB:${j.job}`)"
        :target-label="targetLabel"
        @reveal="revealRow"
      />

      <p v-if="unavailableJobCount" class="processing-detail-note">
        <span v-if="unavailableJobCount > 1">The same track can affect more than one calculation. </span>
        <RouterLink :to="{ name: 'review', query: { state: 'NEEDS_REVIEW' } }">Review GPS findings</RouterLink>
      </p>
    </section>

    <section v-if="operationalTasks.length" class="processing-group" aria-label="Maps and routing">
      <h3>Maps &amp; routing</h3>
      <ProcessingTaskRow
        v-for="task in operationalTasks"
        :key="task.id"
        :label="taskPresentation(task).label"
        :icon="taskPresentation(task).icon"
        :state="operationalState(task)"
        :status-label="operationalStatus(task)"
        :summary="operationalSummary(task)"
        :explanation="taskPresentation(task).explanation"
        :progress="task.active ? task.progressPercent : null"
        :indeterminate="task.active && task.indeterminate"
        :progress-label="task.active ? 'In progress' : task.state === 'done' ? 'Available' : '—'"
      >
        <template #summary>
          {{ operationalSummary(task) }}
          <span v-if="task.metricCounts?.length" class="processing-task__counts">
            <template v-for="(count, index) in task.metricCounts" :key="count.key">
              <template v-if="index">{{ task.metricSeparator }}</template>
              <AnimatedCount
                :value="count.value"
                :sample="task"
                :animation="countAnimation('task', task.id, count.key)"
                :format="count.format === 'bytes' ? formatWholeBytes : undefined"
              />
              <template v-if="count.label">{{ ' ' + count.label }}</template>
            </template>
          </span>
          <span v-else-if="task.metric" class="processing-task__counts">{{ task.metric }}</span>
        </template>
        <div class="processing-detail-heading"><h4>Current work</h4></div>
        <p class="processing-detail-copy">{{ task.detail || taskPresentation(task).explanation }}</p>
        <dl v-if="task.metricCounts?.length" class="processing-facts">
          <div v-for="count in task.metricCounts" :key="count.key">
            <dt>{{ metricLabel(count.key, count.label) }}</dt>
            <dd>
              <AnimatedCount
                :value="count.value"
                :sample="task"
                :animation="countAnimation('task', task.id, count.key)"
                :format="count.format === 'bytes' ? formatWholeBytes : undefined"
              />
            </dd>
          </div>
        </dl>
        <p v-else-if="task.metric" class="processing-detail-copy">{{ task.metric }}</p>
        <details class="processing-technical">
          <summary>Technical details</summary>
          <dl>
            <dt>Service state</dt>
            <dd>{{ task.statusLabel }}</dd>
            <template v-if="compactVersionInfo(task.versionInfo)"
              ><dt>Version</dt>
              <dd>{{ compactVersionInfo(task.versionInfo) }}</dd></template
            >
            <template v-if="task.detail"
              ><dt>Status message</dt>
              <dd>{{ task.detail }}</dd></template
            >
          </dl>
        </details>
      </ProcessingTaskRow>
    </section>

    <div
      v-if="!summaries.length && !jobSummaries.length && !operationalTasks.length"
      class="inline-empty-state"
      role="status"
    >
      {{
        isIndexerStatusPollingHealthy === false
          ? 'Processing status is unavailable. Try Refresh.'
          : 'Loading processing status…'
      }}
    </div>
    <div class="processing-footer">
      <span
        ><i class="bi bi-info-circle" aria-hidden="true" /> Background tasks continue when you leave this screen.</span
      >
      <button type="button" :disabled="refreshing" @click="onRefresh">
        <i class="bi bi-arrow-clockwise" aria-hidden="true" /> Refresh
      </button>
      <span v-if="lastRefreshed">Updated {{ lastRefreshed }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { formatLocaleNumber } from '@/utils/Utils';
import { computed, nextTick, onBeforeUnmount, ref } from 'vue';
import ProcessingJobRow from './ProcessingJobRow.vue';
import ProcessingJobDependencies from './ProcessingJobDependencies.vue';
import ProcessingHoldingUp from './ProcessingHoldingUp.vue';
import {
  jobPresentation,
  jobUnit,
  blockerTarget,
  processingOverview,
  type ProcessingBlocker,
} from '@/utils/processingJobPresentation';
import { RouterLink } from 'vue-router';
import AnimatedCount from '@/components/common/AnimatedCount.vue';
import ProcessingTaskRow from './ProcessingTaskRow.vue';
import FileIndexerFailures from './FileIndexerFailures.vue';
import { indexFileLabel, issueBreakdown } from '@/features/track-review/sourceIssuePresentation';
import { useIndexerStatus } from '@/composables/useIndexerStatus';
import { useProcessingProgress } from '@/composables/useProcessingProgress';
import type { AdminOperationalTask } from '@/utils/serverAdminApi';
import { formatWholeBytes } from '@/utils/adminOperationalTasks';
import { compactVersionInfo } from '@/utils/versionInfo';
import { processingCountKey } from '@/utils/countAnimation';

const {
  summaries: reportedSummaries,
  jobSummaries: reportedJobSummaries,
  dependencies,
  operationalTasks,
  countAnimations,
  statusClockOffsetMs,
  lastRefreshed,
  refresh,
  isIndexerStatusPollingHealthy,
} = useIndexerStatus();
const { summaries, jobSummaries } = useProcessingProgress(reportedSummaries, reportedJobSummaries, countAnimations);
const pendingSourceCount = computed(() =>
  summaries.value.reduce((count, index) => count + (index.pendingIssueFileCount ?? 0), 0)
);
const pendingBreakdown = computed(() =>
  issueBreakdown(summaries.value.map((summary) => ({ index: summary.index, count: summary.pendingIssueFileCount })))
);
const refreshing = ref(false);
const refreshKey = ref(0);
const unavailableJobCount = computed(() => jobSummaries.value.filter((job) => (job.unavailable ?? 0) > 0).length);
const formatCount = (value: number | undefined) => formatLocaleNumber(value ?? 0);
function countAnimation(scope: 'indexer' | 'job' | 'task', id: string | undefined, metric: string) {
  return countAnimations.value.get(processingCountKey(scope, id, metric)) ?? null;
}
function workState(pending: number, findings: number) {
  return pending > 0 ? 'running' : findings > 0 ? 'attention' : 'ready';
}
function workStatus(pending: number, findings: number) {
  return pending > 0 ? 'Running' : findings > 0 ? 'Needs attention' : 'Complete';
}
const syntheticHealth = computed(() => jobSummaries.value.find((job) => job.job === 'demoHealth'));
const trackJobs = computed(() => jobSummaries.value.filter((job) => job.job !== 'demoHealth'));
const expandedRows = ref<Record<string, boolean>>({});
function rowId(key: string) {
  return `processing-${key.replace(':', '-')}`;
}
function targetLabel(blocker: ProcessingBlocker) {
  const target = blockerTarget(blocker);
  if (target?.startsWith('INDEX:')) return indexLabel(target.slice(6));
  const job = jobSummaries.value.find((job) => `JOB:${job.job}` === target);
  return job ? jobPresentation(job).label : (blocker.id ?? 'Other processing');
}
const overview = computed(() => processingOverview(jobSummaries.value, trackJobs.value));
function holdingUp(key: string) {
  return overview.value.dependents.get(key) ?? [];
}
/** Remaining work of a single shared prerequisite, so the wait has a visible size. */
const sharedRemaining = computed(() => {
  if (overview.value.shared.length !== 1) return '';
  const blocker = overview.value.shared[0]!;
  if (blocker.kind === 'INDEX') {
    const index = summaries.value.find((summary) => summary.index === blocker.id);
    if (index?.discoveryInProgress) return 'Files are still being discovered.';
    return index?.pending ? `${formatCount(index.pending)} ${index.pending === 1 ? 'file' : 'files'} left.` : '';
  }
  const job = jobSummaries.value.find((summary) => summary.job === blocker.id);
  return job?.pending ? `${formatCount(job.pending)} ${jobUnit(job, job.pending)} left.` : '';
});
const sharedResume = computed(() =>
  [
    `They start automatically when ${overview.value.shared.length > 1 ? 'these finish' : 'this finishes'}.`,
    sharedRemaining.value,
  ]
    .filter(Boolean)
    .join(' ')
);
/** Long enough to find the row after scrolling; matches the highlight animation. */
const HIGHLIGHT_DURATION_MS = 1600;
const highlightedRow = ref<string | null>(null);
let highlightTimer: ReturnType<typeof setTimeout> | undefined;
let highlightFrame: number | undefined;
let disposed = false;
function highlightClass(key: string) {
  return { 'processing-task--highlight': highlightedRow.value === key };
}
async function revealRow(key: string) {
  const row = document.getElementById(rowId(key));
  if (!row) return;
  expandedRows.value[key] = true;
  await nextTick();
  if (disposed) return;
  const control = row.querySelector<HTMLButtonElement>('.processing-task__toggle');
  row.scrollIntoView({ behavior: 'smooth', block: 'center' });
  control?.focus({ preventScroll: true });
  // Restart the highlight when the same row is revealed again.
  highlightedRow.value = null;
  clearTimeout(highlightTimer);
  if (highlightFrame !== undefined) cancelAnimationFrame(highlightFrame);
  highlightFrame = requestAnimationFrame(() => {
    highlightFrame = undefined;
    highlightedRow.value = key;
    highlightTimer = setTimeout(() => (highlightedRow.value = null), HIGHLIGHT_DURATION_MS);
  });
}
onBeforeUnmount(() => {
  disposed = true;
  clearTimeout(highlightTimer);
  if (highlightFrame !== undefined) cancelAnimationFrame(highlightFrame);
});
function indexLabel(index: string | undefined) {
  return index === 'GPS' ? 'Track files' : index === 'MEDIA' ? 'Media files' : index || 'File import';
}
function indexExplanation(index: string | undefined) {
  return index === 'GPS'
    ? 'Reads GPS recordings from your import folders and adds them to your track library. Tracks become available as their files are processed.'
    : 'Indexes photos and videos and links them to tracks using their time and location. New files are picked up automatically.';
}
const TASK_PRESENTATIONS: Record<string, { label: string; icon: string; explanation: string }> = {
  'vector-map-tiles': {
    label: 'Vector map tiles',
    icon: 'bi bi-map',
    explanation: 'Prepares the offline map data. The first download can take a long time.',
  },
  'location-search': {
    label: 'Location search',
    icon: 'bi bi-search',
    explanation: 'Prepares the place-name search. Search works once this is done.',
  },
  'routing-segments': {
    label: 'Routing data',
    icon: 'bi bi-signpost-split',
    explanation: 'Prepares the data for route planning. It is downloaded when needed.',
  },
};
function taskPresentation(task: AdminOperationalTask) {
  return (
    TASK_PRESENTATIONS[task.id] ?? {
      label: task.label,
      icon: 'bi bi-gear',
      explanation: 'Prepares data used by the application.',
    }
  );
}
function operationalState(task: AdminOperationalTask) {
  return task.state === 'running'
    ? 'running'
    : task.state === 'done'
      ? 'ready'
      : task.state === 'disabled'
        ? 'disabled'
        : task.statusLabel === 'unavailable'
          ? 'unavailable'
          : 'attention';
}
function operationalStatus(task: AdminOperationalTask) {
  return task.state === 'running'
    ? 'Running'
    : task.state === 'done'
      ? 'Ready'
      : task.state === 'disabled'
        ? 'Off'
        : task.statusLabel === 'unavailable'
          ? 'Unavailable'
          : 'Needs attention';
}
function operationalSummary(task: AdminOperationalTask) {
  if (task.state === 'disabled') return 'This service is turned off.';
  if (task.state === 'warning') return 'Service status needs attention. Open Details to review.';
  if (task.state === 'done')
    return task.id === 'location-search'
      ? 'Place search is available.'
      : task.id === 'vector-map-tiles'
        ? 'Local map data is available.'
        : task.id === 'routing-segments'
          ? 'Route planning is available.'
          : 'Service is available.';
  return task.id === 'location-search'
    ? 'Preparing the place search index.'
    : task.id === 'routing-segments'
      ? 'Preparing routing segments.'
      : task.statusLabel === 'downloading'
        ? 'Downloading map data.'
        : 'Preparing local map data.';
}
function metricLabel(key: string, label?: string) {
  const labels: Record<string, string> = {
    downloaded: 'Downloaded',
    total: 'Total',
    places: 'Places',
    populated: 'Populated places',
    terrain: 'Terrain features',
  };
  return labels[key] || label || key;
}
async function onRefresh() {
  refreshing.value = true;
  try {
    await refresh();
    refreshKey.value += 1;
  } finally {
    refreshing.value = false;
  }
}
</script>

<style scoped>
.processing-attention {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem 0.9rem;
  border: 1px solid color-mix(in srgb, var(--accent) 35%, var(--border-subtle));
  border-radius: 0.75rem;
  background: color-mix(in srgb, var(--accent) 6%, transparent);
}
.processing-attention > i {
  color: var(--accent-text);
  font-size: 1.15rem;
}
.processing-attention > div {
  display: grid;
  flex: 1;
  gap: 0.1rem;
  min-width: 0;
}
.processing-attention span {
  color: var(--text-secondary);
  font-size: var(--text-sm-size);
}
.processing-attention__action {
  display: inline-flex;
  align-items: center;
  min-height: 2.25rem;
  padding: 0.35rem 0.9rem;
  border-radius: 0.5rem;
  background: var(--accent);
  color: var(--accent-contrast);
  font-weight: 600;
  text-decoration: none;
}
.processing-attention__action:focus-visible {
  outline: 2px solid var(--accent-text);
  outline-offset: 2px;
}
.processing-shared-wait {
  display: flex;
  align-items: flex-start;
  gap: 0.65rem;
  margin: 0.2rem 0 0.35rem;
  padding: 0.6rem 0.8rem;
  border: 1px solid var(--border-default);
  border-radius: 0.65rem;
  background: color-mix(in srgb, var(--surface-elevated) 65%, var(--surface-sheet-solid));
}
.processing-shared-wait > i {
  margin-top: 0.1rem;
  color: var(--text-muted);
}
.processing-shared-wait > div {
  display: grid;
  gap: 0.1rem;
  min-width: 0;
}
.processing-shared-wait strong {
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
  font-weight: var(--font-medium);
}
.processing-shared-wait span {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}
.processing-shared-wait button {
  border: 0;
  padding: 0;
  background: transparent;
  color: var(--accent-text);
  font: inherit;
  text-decoration: underline;
  text-underline-offset: 0.2em;
  cursor: pointer;
}
.processing-shared-wait button:focus-visible {
  outline: 2px solid var(--accent-text);
  outline-offset: 3px;
}
@media (pointer: coarse) {
  .processing-attention__action {
    min-height: 44px;
  }
}
.processing-footer {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.45rem 0.8rem;
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}
.processing-footer > span:first-child {
  margin-right: auto;
}
.processing-footer button {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  min-height: 2rem;
  border: 0;
  border-radius: 0.35rem;
  padding: 0.3rem 0.4rem;
  color: var(--text-muted);
  background: transparent;
  font: inherit;
  cursor: pointer;
}
.processing-footer button:hover {
  background: var(--surface-hover);
  color: var(--accent-text);
}
.processing-footer button:focus-visible {
  outline: 2px solid var(--accent-text);
  outline-offset: 2px;
}
.processing-footer button:disabled {
  opacity: 0.6;
  cursor: default;
}
@media (pointer: coarse) {
  .processing-footer button {
    min-height: 44px;
  }
}
</style>
