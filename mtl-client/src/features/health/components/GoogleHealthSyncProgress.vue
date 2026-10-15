<template>
  <span class="gh-sync-progress__announcement" role="status" aria-live="polite" aria-atomic="true">
    {{ syncAnnouncement }}
  </span>
  <ProcessingTaskRow
    v-if="presentation === 'row'"
    v-model:expanded="expanded"
    :label="GOOGLE_HEALTH_FEATURE_LABEL"
    icon="bi bi-heart-pulse"
    :state="rowState"
    :needs-attention="needsAttention"
    :status-label="reauthorization ? 'Paused' : error ? 'Unavailable' : badgeLabel"
    :summary="rowSummary"
    explanation="Imports health records and exercise routes from your connected Google account. Recent records are refreshed in scheduled cycles while older history is checked over time."
    :progress="jobHasMeasuredProgress && !pauseReason ? jobProgress : null"
    :indeterminate="jobRunning && !pauseReason && !jobHasMeasuredProgress"
    :progress-scope="jobHasMeasuredProgress ? 'Current pass' : ''"
    :progress-label="pauseReason ? 'Paused' : jobRunning ? 'In progress' : rowState === 'ready' ? 'Up to date' : '—'"
    data-testid="google-health-sync-progress"
  >
    <template #summary>
      {{ rowSummary }}
      <span v-if="jobRunning && job?.recordsProcessed !== undefined" class="processing-task__counts">
        <AnimatedCount
          :value="job.recordsProcessed"
          :sample="job"
          :reset-key="job.syncId"
          :animation="countAnimation"
          reset-on-decrease
        />
        records processed this cycle
      </span>
    </template>
    <template v-if="reauthorization" #notice>
      <span>{{ reauthorization.heading }}</span>
      <RouterLink :to="GOOGLE_HEALTH_REAUTHORIZATION_ROUTE">Reauthorize Google Health</RouterLink>
    </template>
    <div class="gh-processing-details" data-testid="google-health-sync-details">
      <div class="processing-detail-heading">
        <h4>Current work</h4>
        <RouterLink :to="GOOGLE_HEALTH_SETTINGS_ROUTE">Manage connection</RouterLink>
      </div>
      <p class="processing-detail-copy">{{ currentWorkDescription }}</p>
      <dl class="processing-facts">
        <div v-if="jobRunning && job?.recordsProcessed !== undefined">
          <dt>Processed this cycle</dt>
          <dd>
            <AnimatedCount
              :value="job.recordsProcessed"
              :sample="job"
              :reset-key="job.syncId"
              :animation="countAnimation"
              reset-on-decrease
            />
            <small>records</small>
          </dd>
        </div>
        <div v-if="hasMirrorSnapshot">
          <dt>Stored locally</dt>
          <dd>{{ formatCount(storedRecordCount) }} <small>unique records</small></dd>
        </div>
        <div v-if="jobHasMeasuredProgress">
          <dt>Current pass</dt>
          <dd>
            {{ completedUnits }} <small>of {{ totalUnits }} steps</small>
          </dd>
        </div>
        <div v-else-if="elapsedLabel && jobRunning">
          <dt>Elapsed</dt>
          <dd>{{ elapsedLabel }}</dd>
        </div>
        <div v-else-if="pendingDayChecks">
          <dt>History remaining</dt>
          <dd>{{ formatCount(pendingDayChecks) }} <small>day checks</small></dd>
        </div>
      </dl>
      <dl class="processing-detail-lines">
        <div v-if="windowLabel">
          <dt>Current window</dt>
          <dd>
            {{ windowLabel
            }}<span>{{ formatCount(currentWindowImported) }} {{ recordLabel }} processed in this window</span>
          </dd>
        </div>
        <div v-if="laneStreams.length" aria-label="Recent data refresh">
          <dt>Recent data</dt>
          <dd>
            {{ recentSummary }}<span v-if="fastCycleSummary">{{ fastCycleSummary }}</span
            ><span v-if="nextFullDue">Next full refresh: {{ formatDateTime(nextFullDue) }}</span>
          </dd>
        </div>
        <div v-if="laneStreams.length || historyDetail" aria-label="Historical checks">
          <dt>History</dt>
          <dd>
            {{ laneStreams.length ? historySummary : historyDetail
            }}<span v-if="contiguousCoverage"
              >All data types finalized back to {{ formatNativeTime(contiguousCoverage, 'DATE') }}</span
            ><span v-if="parkedDays">{{ formatCount(parkedDays) }} day checks parked</span
            ><span v-if="historyPending">{{ continuationDetail }}</span
            ><span v-if="heartRateCoverage && historyPending"
              >Heart-rate samples checked back to {{ heartRateCoverage }}.</span
            >
          </dd>
        </div>
        <div v-if="hasRouteStatus" aria-label="Exercise routes">
          <dt>Routes</dt>
          <dd>
            {{ routeSummary
            }}<span v-if="retainedRoutes">{{ formatCount(retainedRoutes) }} published routes retained</span
            ><span v-if="status?.routeSummary?.failedCount">Use Retry failed ranges in Manage connection.</span>
          </dd>
        </div>
        <div v-if="storedRange">
          <dt>Stored range</dt>
          <dd>{{ storedRange }}</dd>
        </div>
      </dl>
      <p v-if="jobHasMeasuredProgress" class="processing-detail-note">
        This percentage measures the current pass. Step sizes vary; it does not measure the entire history import.
      </p>
      <p v-if="error" class="processing-detail-warning">
        Could not refresh status: {{ error }}. Showing the last received status.
      </p>
      <p v-if="reauthorization" class="processing-detail-warning">{{ reauthorization.detail }}</p>
      <p v-else-if="pauseReason" class="processing-detail-warning">{{ pauseReason }}</p>
      <p v-if="capacityLimited" class="processing-detail-warning">
        Recent coverage is incomplete; this cycle reached its time budget.
        <span v-if="!pauseReason && !reauthorization">Pending work continues in later cycles.</span>
      </p>
      <p v-if="latestError && !error" class="processing-detail-warning">{{ latestError }}</p>
      <details v-if="failedRanges.length" class="processing-technical">
        <summary>Ranges needing another attempt ({{ formatCount(failedRanges.length) }})</summary>
        <p class="processing-detail-note">
          Imported data is kept. Waiting parts are retried automatically. Skipped parts need Retry failed ranges.
        </p>
        <p v-if="failedRangeReason" class="processing-detail-warning">{{ failedRangeReason }}</p>
        <ul class="gh-processing-ranges" aria-label="Ranges awaiting retry">
          <li v-for="range in failedRanges" :key="range.key">
            <strong>{{ range.label }}</strong
            ><span v-if="range.window">{{ range.window }}</span
            ><span v-if="range.error">{{ range.error }}</span
            ><span v-if="range.nextRetryAt">Next retry: {{ formatDateTime(range.nextRetryAt) }}</span>
          </li>
        </ul>
      </details>
      <details v-if="laneStreams.length" class="processing-technical">
        <summary>Data type details ({{ laneStreams.length }})</summary>
        <div class="gh-processing-stream-tools">
          <label
            ><span class="gh-sync-progress__announcement">Find a data type</span
            ><input v-model="streamSearch" type="search" placeholder="Find a data type" aria-label="Find a data type"
          /></label>
          <label v-if="attentionStreamCount"
            ><input v-model="attentionOnly" type="checkbox" /> Needs attention ({{ attentionStreamCount }})</label
          >
        </div>
        <ul class="gh-processing-streams" aria-label="Synchronization by data type">
          <li v-for="stream in filteredStreams" :key="`${stream.dataType}:${stream.representation}`">
            <GoogleHealthStreamProgress :stream="stream" />
          </li>
        </ul>
        <p v-if="!filteredStreams.length" class="processing-detail-note">No data types match this filter.</p>
      </details>
      <details v-if="hasSyncDiagnostics" class="processing-technical">
        <summary>Technical details</summary>
        <dl :aria-label="job?.statistics ? 'Current cycle record counts' : 'Sync diagnostics'">
          <template v-if="job?.statistics"
            ><dt>Read from Google</dt>
            <dd>{{ formatCount(job.statistics.fetched ?? 0) }}</dd>
            <dt>Content updated</dt>
            <dd>{{ formatCount(job.statistics.changed ?? 0) }}</dd>
            <dt>Records removed</dt>
            <dd>{{ formatCount(job.statistics.deleted ?? 0) }}</dd></template
          >
          <template v-if="status?.mirrorSnapshotAt"
            ><dt>Stored data snapshot</dt>
            <dd>{{ formatDateTime(status.mirrorSnapshotAt) }}</dd></template
          >
          <template v-if="status?.synchronization?.snapshotAt"
            ><dt>Coverage snapshot</dt>
            <dd>{{ formatDateTime(status.synchronization.snapshotAt) }}</dd></template
          >
          <template v-if="elapsedLabel && jobRunning"
            ><dt>Elapsed</dt>
            <dd>{{ elapsedLabel }}</dd></template
          >
          <template v-if="status?.nextRegularSync"
            ><dt>Next scheduled check</dt>
            <dd>{{ formatDateTime(status.nextRegularSync) }}</dd></template
          >
        </dl>
        <p v-if="jobRunning && job?.recordsProcessed !== undefined" class="processing-detail-note">
          This count is for the current sync only. Overall totals are shown separately.
        </p>
        <p v-if="retainedRoutes" class="processing-detail-note">
          {{ formatCount(retainedRoutes) }} routes are no longer in Google Health but are kept here.
        </p>
      </details>
    </div>
  </ProcessingTaskRow>
  <div
    v-else-if="visible"
    :class="['gh-sync-progress', { 'gh-sync-progress--compact': compact, 'gh-sync-progress--expanded': expanded }]"
    data-testid="google-health-sync-progress"
  >
    <div class="gh-sync-progress__header">
      <div>
        <strong>{{ title }}</strong>
        <span>{{ summary }}</span>
      </div>
      <span :class="['gh-sync-progress__badge', `gh-sync-progress__badge--${tone}`]">
        <i :class="badgeIcon" aria-hidden="true" /> {{ badgeLabel }}
      </span>
      <strong v-if="jobHasMeasuredProgress" class="gh-sync-progress__percent"> {{ jobProgress }}% </strong>
      <button
        v-if="hasDetails"
        type="button"
        class="view-toggle-button gh-sync-progress__toggle"
        :aria-expanded="expanded"
        :aria-controls="detailsId"
        :aria-label="expanded ? 'Hide sync details' : 'Show sync details'"
        @click="expanded = !expanded"
      >
        <span class="gh-sync-progress__toggle-label">Details</span>
        <i :class="expanded ? 'bi bi-chevron-up' : 'bi bi-chevron-down'" aria-hidden="true" />
      </button>
    </div>

    <progress
      v-if="jobRunning && !pauseReason"
      :value="jobHasMeasuredProgress ? jobProgress : undefined"
      max="100"
      :aria-label="
        jobHasMeasuredProgress ? `${jobProgress}% of steps in the current pass complete` : 'Sync in progress'
      "
    >
      {{ jobHasMeasuredProgress ? `${jobProgress}%` : 'Cycle in progress' }}
    </progress>

    <div v-if="jobRunning" class="gh-sync-progress__facts">
      <span v-if="job?.recordsProcessed !== undefined" class="gh-sync-progress__processed">
        <i class="bi bi-database-check" aria-hidden="true" />
        <AnimatedCount
          :value="job.recordsProcessed"
          :sample="job"
          :reset-key="job.syncId"
          :animation="countAnimation"
          reset-on-decrease
        />
        records processed
      </span>
      <span v-if="jobHasMeasuredProgress" class="gh-sync-progress__pass">
        Current pass: {{ completedUnits }} of {{ totalUnits }} steps
      </span>
    </div>

    <p v-if="error" class="gh-sync-progress__notice" role="status">
      Could not refresh status: {{ error }}. Showing the last received status.
    </p>
    <p v-if="pauseReason" class="gh-sync-progress__notice" role="status">{{ pauseReason }}</p>
    <p v-else-if="capacityLimited" class="gh-sync-progress__notice">
      This cycle reached its time budget. Recent coverage is incomplete; pending work continues in later cycles.
    </p>

    <div v-if="laneStreams.length" class="gh-sync-progress__overview" aria-label="Google Health sync overview">
      <section class="gh-sync-progress__lane" aria-label="Recent data refresh">
        <h4><i class="bi bi-arrow-repeat" aria-hidden="true" /> Recent</h4>
        <strong>{{ recentSummary }}</strong>
        <span v-if="fastCycleSummary">{{ fastCycleSummary }}</span>
        <span v-if="nextFullDue">Next full refresh: {{ formatDateTime(nextFullDue) }}</span>
      </section>
      <section class="gh-sync-progress__lane" aria-label="Historical checks">
        <h4><i class="bi bi-clock-history" aria-hidden="true" /> History</h4>
        <strong>{{ historySummary }}</strong>
        <span v-if="contiguousCoverage"
          >All data types finalized back to {{ formatNativeTime(contiguousCoverage, 'DATE') }}</span
        >
        <span v-if="parkedDays">{{ formatCount(parkedDays) }} day checks parked</span>
      </section>
      <section v-if="hasRouteStatus" class="gh-sync-progress__lane" aria-label="Exercise routes">
        <h4><i class="bi bi-signpost" aria-hidden="true" /> Routes</h4>
        <strong>{{ routeSummary }}</strong>
        <span v-if="status?.routeSummary?.failedCount">Use Retry failed ranges in Manage connection.</span>
        <span v-if="retainedRoutes">{{ formatCount(retainedRoutes) }} published routes retained</span>
      </section>
    </div>
    <p v-if="failedRanges.length" class="gh-sync-progress__note">
      {{ formatCount(failedRanges.length) }} {{ failedRanges.length === 1 ? 'range needs' : 'ranges need' }} another
      attempt. Imported data is kept. Waiting parts are retried automatically. Skipped parts need Retry failed ranges.
    </p>
    <ul
      v-if="!laneStreams.length && failedRanges.length"
      class="gh-sync-progress__failed-ranges"
      aria-label="Google Health ranges awaiting another attempt"
    >
      <li v-for="range in failedRanges" :key="range.key">
        <span>{{ range.label }}</span
        ><time v-if="range.window">{{ range.window }}</time>
      </li>
    </ul>

    <div
      v-if="hasDetails"
      v-show="expanded"
      :id="detailsId"
      class="gh-sync-progress__details"
      data-testid="google-health-sync-details"
    >
      <div v-if="jobRunning" class="gh-sync-progress__facts">
        <span v-if="elapsedLabel"><i class="bi bi-clock" aria-hidden="true" /> Elapsed: {{ elapsedLabel }}</span>
        <span v-if="jobHasMeasuredProgress">Step sizes vary</span>
        <span v-if="windowLabel">
          <i class="bi bi-calendar-range" aria-hidden="true" /> Current window: {{ windowLabel }}
        </span>
        <span v-if="currentDataType">
          <i class="bi bi-database-down" aria-hidden="true" />
          {{ formatCount(currentWindowImported) }} {{ recordLabel }} processed in this window
        </span>
      </div>
      <dl v-if="job?.statistics" class="gh-sync-progress__cycle-counts" aria-label="Current cycle record counts">
        <div>
          <dt>Read from Google</dt>
          <dd>{{ formatCount(job.statistics.fetched ?? 0) }}</dd>
        </div>
        <div>
          <dt>Content updated</dt>
          <dd>{{ formatCount(job.statistics.changed ?? 0) }}</dd>
        </div>
        <div>
          <dt>Mirror records removed</dt>
          <dd>{{ formatCount(job.statistics.deleted ?? 0) }}</dd>
        </div>
      </dl>
      <p v-if="jobRunning && job?.recordsProcessed !== undefined" class="gh-sync-progress__note">
        This count is for the current sync only. Overall totals are shown separately.
      </p>
      <div v-if="hasMirrorSnapshot" class="gh-sync-progress__stored" data-testid="google-health-stored-summary">
        <span><i class="bi bi-database-check" aria-hidden="true" /> Stored locally</span>
        <strong>{{ formatCount(storedRecordCount) }} unique records</strong>
        <span v-if="storedRange">{{ storedRange }}</span>
        <span v-if="status?.mirrorSnapshotAt">Snapshot: {{ formatDateTime(status.mirrorSnapshotAt) }}</span>
      </div>
      <div v-if="laneStreams.length" class="gh-sync-progress__lanes">
        <div class="gh-sync-progress__stream-tools">
          <h4>Data type details</h4>
          <label class="gh-sync-progress__search"
            ><span class="gh-sync-progress__announcement">Find a data type</span>
            <input v-model="streamSearch" type="search" placeholder="Find a data type" aria-label="Find a data type" />
          </label>
          <label v-if="attentionStreamCount" class="gh-sync-progress__filter">
            <input v-model="attentionOnly" type="checkbox" /> Needs attention ({{ attentionStreamCount }})
          </label>
        </div>
        <ul aria-label="Synchronization by stream">
          <li v-for="stream in filteredStreams" :key="`${stream.dataType}:${stream.representation}`">
            <GoogleHealthStreamProgress :stream="stream" />
          </li>
        </ul>
        <p v-if="!filteredStreams.length" class="gh-sync-progress__note">No data types match this filter.</p>
        <p v-if="status?.synchronization?.snapshotAt" class="gh-sync-progress__note">
          Coverage snapshot: {{ formatDateTime(status.synchronization.snapshotAt) }}
        </p>
      </div>
      <p v-if="status?.routeSummary?.retainedCount" class="gh-sync-progress__note">
        {{ status.routeSummary.retainedCount }} routes are no longer in Google Health but are kept here.
      </p>
      <p v-if="!laneStreams.length && historyDetail" class="gh-sync-progress__note">
        {{ historyDetail }}
      </p>
      <p v-if="heartRateCoverage && historyPending" class="gh-sync-progress__note">
        Heart-rate samples checked back to {{ heartRateCoverage }}. Older tracks may not have heart rate yet.
      </p>
      <p v-if="historyPending" class="gh-sync-progress__note">
        {{ continuationDetail }}
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref, useId, watch } from 'vue';
import { RouterLink } from 'vue-router';
import ProcessingTaskRow from '@/components/admin/ProcessingTaskRow.vue';
import { GOOGLE_HEALTH_FEATURE_LABEL, googleHealthReauthorizationExplanation } from '../utils/googleHealthPresentation';
import { GOOGLE_HEALTH_SETTINGS_ROUTE, GOOGLE_HEALTH_REAUTHORIZATION_ROUTE } from '../utils/googleHealthNavigation';
import { formatCompactDurationSeconds } from '@/utils/Utils';
import AnimatedCount from '@/components/common/AnimatedCount.vue';
import type { CountAnimation } from '@/utils/countAnimation';
import type { GHStatusResponse } from '../api/googleHealthApi';
import { hasPendingGoogleHealthHistory } from '../utils/googleHealthPresentation';
import GoogleHealthStreamProgress from './GoogleHealthStreamProgress.vue';
import {
  formatWorkState,
  formatDataType,
  formatRepresentation,
  formatWindow,
  formatNativeTime,
  formatDateTime,
  formatDate,
  formatCount,
} from '../utils/googleHealthSyncFormatting';

const props = withDefaults(
  defineProps<{
    status?: GHStatusResponse | null;
    error?: string;
    compact?: boolean;
    presentation?: 'card' | 'row';
    showHistory?: boolean;
    countAnimation?: CountAnimation | null;
  }>(),
  { status: null, error: '', compact: false, presentation: 'card', showHistory: false, countAnimation: undefined }
);

const job = computed(() => props.status?.job);
const SYNC_ANNOUNCEMENTS: Record<string, string> = {
  QUEUED: 'Google Health sync queued.',
  RUNNING: 'Google Health sync running.',
  COMPLETED: 'Google Health sync completed.',
  COMPLETED_WITH_ERRORS: 'Google Health sync completed with errors.',
  CANCELLED: 'Google Health sync cancelled.',
  FAILED: 'Google Health sync failed.',
};
const syncAnnouncement = computed(() => {
  if (hasLaneStatus.value && ['COMPLETED', 'COMPLETED_WITH_ERRORS'].includes(job.value?.state ?? '')) {
    if (pauseReason.value) return 'Google Health requests are paused.';
    if (capacityLimited.value) return 'This sync cycle finished with incomplete recent coverage.';
    if (recentPending.value || historyPending.value) return 'This sync cycle finished. More checks are pending.';
    return 'This sync cycle finished.';
  }
  return SYNC_ANNOUNCEMENTS[job.value?.state ?? ''] ?? '';
});
const MILLISECONDS_PER_SECOND = 1_000;
const expanded = ref(false);
const detailsId = useId();
const now = ref(Date.now());
let clockTimer: ReturnType<typeof setInterval> | undefined;
const elapsedLabel = computed(() => {
  const startedAt = job.value?.startedAt;
  if (!(startedAt instanceof Date) || !Number.isFinite(startedAt.getTime())) return '';
  return formatCompactDurationSeconds(Math.max(0, now.value - startedAt.getTime()) / MILLISECONDS_PER_SECOND);
});
const jobRunning = computed(() => ['QUEUED', 'RUNNING'].includes(job.value?.state ?? ''));
const jobFailed = computed(() => ['FAILED', 'COMPLETED_WITH_ERRORS'].includes(job.value?.state ?? ''));
const totalUnits = computed(() => job.value?.totalUnits ?? 0);
const completedUnits = computed(() => job.value?.completedUnits ?? 0);
const currentWindowImported = computed(() => job.value?.currentWindowImported ?? 0);

watch(
  [jobRunning, () => props.status?.synchronization?.cooldownUntil],
  ([running, cooldown]) => {
    clearInterval(clockTimer);
    now.value = Date.now();
    clockTimer =
      running || (cooldown instanceof Date && cooldown.getTime() > now.value)
        ? setInterval(() => (now.value = Date.now()), MILLISECONDS_PER_SECOND)
        : undefined;
  },
  { immediate: true }
);
watch(job, () => (now.value = Date.now()));

onUnmounted(() => {
  clearInterval(clockTimer);
});
// The two-lane worker discovers/splits chunks during a cycle, so its total is not a fixed percentage target.
const hasLaneStatus = computed(() => Boolean(props.status?.synchronization?.streams?.length || job.value?.statistics));
const jobHasMeasuredProgress = computed(() => jobRunning.value && !hasLaneStatus.value && totalUnits.value > 0);
const jobProgress = computed(() =>
  totalUnits.value > 0 ? Math.min(100, Math.round((completedUnits.value / totalUnits.value) * 100)) : 0
);
const laneStreams = computed(() => props.status?.synchronization?.streams ?? []);
const streamSearch = ref('');
const attentionOnly = ref(false);
function streamNeedsAttention(stream: (typeof laneStreams.value)[number]): boolean {
  return Boolean(stream.budgetLimited || stream.failures?.length || (stream.parkedDays ?? 0) > 0);
}
const attentionStreamCount = computed(() => laneStreams.value.filter(streamNeedsAttention).length);
const filteredStreams = computed(() => {
  const query = streamSearch.value.trim().toLowerCase();
  return laneStreams.value.filter(
    (stream) =>
      (!attentionOnly.value || streamNeedsAttention(stream)) &&
      `${formatDataType(stream.dataType)} ${formatRepresentation(stream.representation)}`.toLowerCase().includes(query)
  );
});
const pendingDayChecks = computed(() => laneStreams.value.reduce((sum, stream) => sum + (stream.pendingDays ?? 0), 0));
const parkedDays = computed(() => laneStreams.value.reduce((sum, stream) => sum + (stream.parkedDays ?? 0), 0));
const activeFullStreams = computed(
  () => laneStreams.value.filter((stream) => stream.fullPass?.state === 'ACTIVE').length
);
const recentSummary = computed(() =>
  activeFullStreams.value
    ? `Full refresh pending for ${activeFullStreams.value} data ${activeFullStreams.value === 1 ? 'type' : 'types'}`
    : 'No full refresh pending'
);
const fastCycleSummary = computed(() => {
  const stats = job.value?.statistics;
  return stats?.totalFastStreams
    ? `${stats.completeFastStreams ?? 0} of ${stats.totalFastStreams} fast checks complete this cycle`
    : '';
});
const nextFullDue = computed(() => {
  const dates = laneStreams.value
    .map((stream) => stream.fullPass?.nextDueAt)
    .filter((date): date is Date => date instanceof Date);
  return dates.length ? new Date(Math.min(...dates.map((date) => date.getTime()))) : null;
});
const contiguousCoverage = computed(() => {
  const dates = laneStreams.value
    .map((stream) => stream.finalizedBackTo)
    .filter((date): date is Date => date instanceof Date);
  return dates.length === laneStreams.value.length && dates.length
    ? new Date(Math.max(...dates.map((date) => date.getTime())))
    : null;
});
const historySummary = computed(() =>
  pendingDayChecks.value
    ? `${formatCount(pendingDayChecks.value)} day checks remaining`
    : 'No historical checks pending'
);
const retainedRoutes = computed(() => props.status?.routeSummary?.retainedCount ?? 0);
const hasRouteStatus = computed(
  () =>
    (props.status?.routeSummary?.pendingCount ?? 0) > 0 ||
    (props.status?.routeSummary?.failedCount ?? 0) > 0 ||
    retainedRoutes.value > 0
);
const routeSummary = computed(() => {
  const pending = props.status?.routeSummary?.pendingCount ?? 0,
    failed = props.status?.routeSummary?.failedCount ?? 0;
  return failed
    ? `${formatCount(failed)} exports need retry`
    : pending
      ? `${formatCount(pending)} exports pending`
      : 'Published routes are kept';
});
const pauseReason = computed(() => {
  if (!hasLaneStatus.value) return '';
  if (props.status?.configurationState === 'DISABLED')
    return 'Synchronization is disabled. Stored records and routes are kept.';
  if (props.status?.configurationState === 'MISSING_CONFIGURATION')
    return 'Complete server setup in Manage connection to resume synchronization.';
  if (props.status?.connectionState && props.status.connectionState !== 'CONNECTED')
    return 'Reconnect Google Health to resume synchronization. Stored records and routes are kept.';
  const until = props.status?.synchronization?.cooldownUntil;
  return until instanceof Date && until.getTime() > now.value
    ? `Google has paused requests until ${formatDateTime(until)}. No requests run during this cooldown.`
    : '';
});
const syncStates = computed(() => props.status?.syncStates ?? []);
const storedRecordCount = computed(() => props.status?.mirrorSummary?.recordCount ?? 0);
const hasMirrorSnapshot = computed(() => props.status?.mirrorSummary?.recordCount != null);
const hasStoredRecords = computed(() => storedRecordCount.value > 0);
const storedRange = computed(() => {
  const earliest = props.status?.mirrorSummary?.earliestRecord;
  const latest = props.status?.mirrorSummary?.latestRecord;
  if (!(earliest instanceof Date) || !(latest instanceof Date)) return '';
  return `${formatDate(earliest)}–${formatDate(latest)}`;
});
type SyncState = NonNullable<GHStatusResponse['syncStates']>[number];
const failedSyncStates = computed(() => syncStates.value.filter((state) => Boolean(state.lastError)));
const failedRanges = computed(() =>
  laneStreams.value.length
    ? laneStreams.value.flatMap((stream) =>
        (stream.failures ?? []).map((failure, index) => ({
          key: `${stream.dataType}:${stream.representation}:${index}`,
          label: `${formatDataType(stream.dataType)} · ${formatWorkState(failure.state)}`,
          window: formatWindow(failure.start, failure.end),
          attempts: failure.attempts,
          nextRetryAt: failure.nextRetryAt,
          error: failure.error,
        }))
      )
    : failedSyncStates.value.map((state) => ({
        key: `${state.dataType ?? ''}:${state.representation ?? ''}`,
        label: formatSyncStateLabel(state),
        window: formatWindow(state.activeWindowStart, state.activeWindowEnd),
        attempts: undefined,
        nextRetryAt: undefined,
        error: state.lastError,
      }))
);
const failedRangeReason = computed(() => {
  const reasons = [...new Set(failedSyncStates.value.map((state) => state.lastError).filter(Boolean))];
  return reasons.length === 1 ? reasons[0] : '';
});
const hasSuccessfulData = computed(
  () => hasStoredRecords.value || syncStates.value.some((state) => state.lastSuccessAt instanceof Date)
);
const historyComplete = computed(() =>
  laneStreams.value.length
    ? laneStreams.value.every((stream) => stream.pendingDays === 0)
    : syncStates.value.length > 0 && syncStates.value.every((state) => state.backfillComplete === true)
);
const recentPending = computed(() => laneStreams.value.some((stream) => stream.fullPass?.state === 'ACTIVE'));
const capacityLimited = computed(
  () => props.status?.job?.statistics?.budgetLimited || laneStreams.value.some((stream) => stream.budgetLimited)
);
const historyPending = computed(() => hasPendingGoogleHealthHistory(props.status));
const heartRateCoverage = computed(() => {
  const date = syncStates.value.find(
    (state) => state.dataType === 'heart-rate' && state.representation === 'HEART_RATE_SAMPLE'
  )?.oldestSyncedTime;
  return date instanceof Date ? formatDate(date) : '';
});
const continuationDetail = computed(() => {
  if (props.status?.configurationState === 'DISABLED')
    return 'History import is paused while the connector is disabled.';
  if (props.status?.connectionState !== 'CONNECTED') return 'Reconnect Google Health to continue importing history.';
  if (jobRunning.value) return 'Older history advances within each scheduled cycle.';
  const next = props.status?.nextRegularSync;
  return next instanceof Date
    ? `Next scheduled check: ${new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(next)}.`
    : 'History import is waiting for the next sync.';
});
const historyCoverage = computed(() => {
  const dates = syncStates.value
    .map((state) => state.oldestSyncedTime)
    .filter((value): value is Date => value instanceof Date);
  if (!dates.length || dates.length !== syncStates.value.length) return '';
  return formatDate(new Date(Math.max(...dates.map((value) => value.getTime()))));
});
const someTypesExtendFurther = computed(() => {
  const timestamps = syncStates.value
    .map((state) => state.oldestSyncedTime)
    .filter((value): value is Date => value instanceof Date)
    .map((value) => value.getTime());
  return timestamps.length > 1 && Math.min(...timestamps) < Math.max(...timestamps);
});
const historyDetail = computed(() => {
  if (historyComplete.value) return 'All history in the configured range is imported. To get later changes, reload it.';
  if (laneStreams.value.length) return 'Recent days are checked again for changes. Older days are imported once.';
  if (historyCoverage.value) {
    const further = someTypesExtendFurther.value ? ' Some data types have already been checked further back.' : '';
    return `History import is incomplete. Every data type has been checked back to ${historyCoverage.value}.${further}`;
  }
  if (syncStates.value.length) return 'History import is incomplete.';
  return '';
});
const latestError = computed(() => {
  if (props.error) return props.error;
  if (props.status?.lastError) return props.status.lastError;
  if (failedRanges.value.length) return '';
  return jobFailed.value ? job.value?.error || '' : '';
});
const needsAttention = computed(
  () =>
    Boolean(latestError.value) ||
    failedRanges.value.length > 0 ||
    jobFailed.value ||
    capacityLimited.value ||
    (props.status?.routeSummary?.failedCount ?? 0) > 0
);
const visible = computed(
  () =>
    jobRunning.value ||
    historyPending.value ||
    recentPending.value ||
    capacityLimited.value ||
    jobFailed.value ||
    (props.showHistory &&
      (Boolean(props.error) ||
        props.status?.connectionState === 'CONNECTED' ||
        syncStates.value.length > 0 ||
        jobFailed.value))
);
const currentDataType = computed(() => formatDataType(job.value?.currentDataType));
const currentRepresentation = computed(() => formatRepresentation(job.value?.currentRepresentation));
const title = computed(() => {
  if (hasLaneStatus.value) return jobRunning.value ? 'Google Health sync' : 'Sync status';
  if (!jobRunning.value) return recentPending.value ? 'Recent refresh' : 'History coverage';
  if (job.value?.kind === 'FULL_RECONCILIATION') return 'Full Google Health reconciliation';
  return 'Google Health sync';
});
const recordLabel = computed(() => (currentWindowImported.value === 1 ? 'record' : 'records'));
const windowLabel = computed(() => {
  const start = job.value?.currentWindowStart;
  const end = job.value?.currentWindowEnd;
  if (!(start instanceof Date) || !(end instanceof Date)) return '';
  const domain = laneStreams.value.find(
    (stream) =>
      stream.dataType === job.value?.currentDataType && stream.representation === job.value?.currentRepresentation
  )?.timeDomain;
  return domain
    ? `${formatNativeTime(start, domain)}–${formatNativeTime(end, domain)}`
    : `${formatDate(start)}–${formatDate(end)}`;
});
const hasDetails = computed(
  () =>
    (jobRunning.value &&
      Boolean(
        elapsedLabel.value ||
        jobHasMeasuredProgress.value ||
        windowLabel.value ||
        currentDataType.value ||
        job.value?.recordsProcessed !== undefined
      )) ||
    hasMirrorSnapshot.value ||
    laneStreams.value.length > 0 ||
    Boolean(historyDetail.value) ||
    historyPending.value
);
const summary = computed(() => {
  if (jobRunning.value) {
    const work = [currentDataType.value, currentRepresentation.value].filter(Boolean).join(' · ');
    if (work) return work;
    return job.value?.state === 'QUEUED' ? 'Waiting to start.' : 'Preparing sync work.';
  }
  if (failedRanges.value.length) {
    const rangeLabel =
      failedRanges.value.length === 1
        ? hasLaneStatus.value
          ? 'range needs'
          : 'history range needs'
        : hasLaneStatus.value
          ? 'ranges need'
          : 'history ranges need';
    return `${failedRanges.value.length} ${rangeLabel} another attempt.`;
  }
  if (pauseReason.value) return 'Checks resume when the connection and request limits allow.';
  if (capacityLimited.value) return 'Recent coverage is incomplete; cycle capacity was limited.';
  if (recentPending.value) return 'The full recent refresh is still pending.';
  if (latestError.value) return latestError.value;
  if (jobFailed.value) return 'The latest sync finished with errors.';
  if (historyComplete.value)
    return hasLaneStatus.value
      ? 'No historical checks pending. Recent data is checked in scheduled cycles.'
      : 'The configured history is synchronized.';
  if (!syncStates.value.length && !laneStreams.value.length) return 'Waiting for the first completed sync step.';
  return 'Older history is still pending.';
});
const badgeLabel = computed(() => {
  if (pauseReason.value) return 'Paused';
  if (job.value?.state === 'QUEUED') return 'Queued';
  if (jobRunning.value) return 'Running';
  if (needsAttention.value) return 'Needs attention';
  if (recentPending.value) return 'Pending';
  if (hasLaneStatus.value && (props.status?.routeSummary?.pendingCount ?? 0) > 0) return 'Routes pending';
  if (historyComplete.value) return 'Complete';
  if (historyPending.value) return 'History pending';
  return 'Waiting';
});
const tone = computed(() => {
  if (pauseReason.value) return 'neutral';
  if (jobRunning.value) return 'live';
  if (needsAttention.value) return 'warning';
  if (historyComplete.value && !recentPending.value && !hasRouteStatus.value) return 'success';
  return 'neutral';
});
const badgeIcon = computed(() => {
  if (pauseReason.value) return 'bi bi-pause-circle';
  if (jobRunning.value) return 'pi pi-spin pi-spinner';
  if (needsAttention.value) return 'bi bi-exclamation-triangle';
  if (historyComplete.value) return 'pi pi-check';
  if (hasSuccessfulData.value) return 'bi bi-clock-history';
  return 'bi bi-hourglass';
});

const reauthorization = computed(() => googleHealthReauthorizationExplanation(props.status));
const rowState = computed(() => {
  if (props.error) return 'unavailable';
  if (pauseReason.value || reauthorization.value) return 'waiting';
  if (jobRunning.value) return 'running';
  if (needsAttention.value) return 'attention';
  return badgeLabel.value === 'Complete' ? 'ready' : 'waiting';
});
const rowSummary = computed(() => {
  if (reauthorization.value) return 'Synchronization is paused until authorization is renewed.';
  if (props.error) return 'Could not update sync status. Showing the last received data.';
  if (pauseReason.value) return pauseReason.value;
  if (failedRanges.value.length) return `${formatCount(failedRanges.value.length)} ranges need another attempt.`;
  if (needsAttention.value && jobRunning.value) return 'Sync is running; some checks need attention.';
  return summary.value;
});
const currentWorkDescription = computed(() => {
  if (!jobRunning.value || pauseReason.value || reauthorization.value) return summary.value;
  const records = [currentDataType.value, currentRepresentation.value].filter(Boolean).join(' ').toLowerCase();
  return records ? `Importing ${records} from Google Health.` : 'Preparing the next group of health records.';
});
const hasSyncDiagnostics = computed(() =>
  Boolean(
    job.value?.statistics ||
    props.status?.mirrorSnapshotAt ||
    props.status?.synchronization?.snapshotAt ||
    (elapsedLabel.value && jobRunning.value) ||
    props.status?.nextRegularSync ||
    retainedRoutes.value
  )
);

function formatSyncStateLabel(state: SyncState): string {
  if (state.dataType === 'heart-rate' && state.representation === 'HEART_RATE_SAMPLE') return 'Heart-rate samples';
  if (state.dataType === 'heart-rate' && state.representation === 'DAILY_ROLLUP') return 'Daily heart rate';
  return formatDataType(state.dataType);
}
</script>

<style scoped>
.gh-sync-progress__overview {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 13rem), 1fr));
  gap: 0.55rem;
}
.gh-sync-progress__lane {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  padding: 0.65rem;
  border: 1px solid var(--border-subtle);
  border-radius: 6px;
  background: var(--surface-elevated);
}
.gh-sync-progress__lane h4,
.gh-sync-progress__stream-tools h4 {
  margin: 0;
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
  color: var(--text-primary);
}
.gh-sync-progress__lane strong {
  font-size: var(--text-sm-size);
  font-weight: 500;
  overflow-wrap: anywhere;
}
.gh-sync-progress__lane span {
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
  color: var(--text-muted);
  overflow-wrap: anywhere;
}
.gh-sync-progress__notice {
  margin: 0;
  padding: 0.55rem 0.65rem;
  border-radius: 6px;
  background: var(--warning-bg);
  color: var(--warning-text);
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
  overflow-wrap: anywhere;
}
.gh-sync-progress__cycle-counts {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 9rem), 1fr));
  gap: 0.4rem;
  margin: 0;
}
.gh-sync-progress__cycle-counts div {
  padding: 0.45rem 0.6rem;
  background: var(--surface-elevated);
  border-radius: 6px;
}
.gh-sync-progress__cycle-counts dt {
  font-size: var(--text-xs-size);
  color: var(--text-muted);
}
.gh-sync-progress__cycle-counts dd {
  margin: 0.15rem 0 0;
  font-size: var(--text-sm-size);
  font-variant-numeric: tabular-nums;
}
.gh-sync-progress__stream-tools {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.6rem;
}
.gh-sync-progress__stream-tools h4 {
  flex: 1 1 10rem;
}
.gh-sync-progress__search {
  flex: 1 1 12rem;
  min-width: 0;
}
.gh-sync-progress__search input {
  width: 100%;
  min-width: 0;
  padding: 0.45rem 0.6rem;
  color: var(--text-primary);
  background: var(--surface-elevated);
  border: 1px solid var(--border-default);
  border-radius: 5px;
  font: inherit;
  font-size: var(--text-sm-size);
}
.gh-sync-progress__filter {
  display: flex;
  align-items: center;
  gap: 0.3rem;
  font-size: var(--text-xs-size);
  color: var(--text-secondary);
}

.gh-sync-progress__lanes ul {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  gap: 0.6rem;
}
.gh-sync-progress__lanes li {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.gh-sync-progress__announcement {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
  border: 0;
}

.gh-sync-progress {
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
  padding: 0.8rem;
  border: 1px solid var(--border-default);
  border-radius: 8px;
  background: color-mix(in srgb, var(--surface-elevated) 45%, transparent);
}

.gh-sync-progress--compact {
  padding: 0.65rem 0.75rem;
}

.gh-sync-progress__header {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 0.5rem;
}

.gh-sync-progress__header > div {
  display: flex;
  min-width: 0;
  flex: 1 1 auto;
  flex-direction: column;
  gap: 0.1rem;
}

.gh-sync-progress__header strong,
.gh-sync-progress__header span {
  overflow-wrap: anywhere;
}

.gh-sync-progress__header strong {
  color: var(--text-primary);
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}

.gh-sync-progress__header > div > span {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}

.gh-sync-progress__badge {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 0.25rem;
  padding: 0.15rem 0.45rem;
  border-radius: 999px;
  font-size: var(--text-2xs-size);
  line-height: var(--text-2xs-lh);
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.gh-sync-progress__badge--live {
  background: var(--warning-bg);
  color: var(--warning-text);
}

.gh-sync-progress__badge--success {
  background: var(--success-bg);
  color: var(--success);
}

.gh-sync-progress__badge--warning {
  background: var(--warning-bg);
  color: var(--warning-text);
}

.gh-sync-progress__badge--neutral {
  background: var(--surface-hover);
  color: var(--text-muted);
}

.gh-sync-progress__toggle {
  flex: 0 0 auto;
  color: var(--accent-text);
}

.gh-sync-progress__percent {
  flex: 0 0 auto;
  font-variant-numeric: tabular-nums;
}

.gh-sync-progress__processed {
  color: var(--text-primary);
  font-size: var(--text-sm-size);
  font-weight: 600;
}

.gh-sync-progress__pass {
  margin-left: auto;
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
  font-variant-numeric: tabular-nums;
}

.gh-sync-progress__details {
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
  padding-top: 0.55rem;
  border-top: 1px solid var(--border-subtle);
}

.gh-sync-progress progress {
  width: 100%;
  height: 0.4rem;
  overflow: hidden;
  border: 0;
  border-radius: 999px;
  accent-color: var(--accent);
}

.gh-sync-progress__facts {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem 0.85rem;
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}

.gh-sync-progress__facts span {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
}

.gh-sync-progress__stored {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.25rem 0.65rem;
  padding: 0.5rem 0.6rem;
  border-radius: 6px;
  background: var(--surface-elevated);
  border: 1px solid var(--border-subtle);
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}

.gh-sync-progress__stored span:first-child {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
}

.gh-sync-progress__stored strong {
  color: var(--text-primary);
}

.gh-sync-progress__failed-ranges {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.gh-sync-progress__failed-ranges li {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 0.2rem 0.75rem;
  color: var(--text-primary);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}

.gh-sync-progress__failed-ranges time {
  color: var(--text-muted);
}

.gh-sync-progress__note {
  margin: 0;
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}

@media (max-width: 560px) {
  .gh-sync-progress__header {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .gh-sync-progress__header > div {
    flex-basis: 100%;
  }

  .gh-sync-progress__toggle {
    margin-left: auto;
  }

  .gh-sync-progress__toggle-label {
    display: none;
  }
}
</style>

<style scoped>
.gh-processing-details {
  min-width: 0;
}
.gh-processing-ranges,
.gh-processing-streams {
  padding: 0;
  margin: 0.6rem 0 0;
  list-style: none;
  display: grid;
  gap: 0.5rem;
}
.gh-processing-ranges li {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  padding: 0.6rem 0;
  border-bottom: 1px solid var(--border-default);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}
.gh-processing-ranges strong {
  font-weight: var(--font-medium);
}
.gh-processing-ranges span {
  color: var(--text-muted);
}
.gh-processing-stream-tools {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem 1rem;
  margin: 0.5rem 0;
  font-size: var(--text-xs-size);
}
.gh-processing-stream-tools > label:first-child {
  flex: 1 1 12rem;
}
.gh-processing-stream-tools input[type='search'] {
  width: 100%;
  border: 1px solid var(--border-default);
  border-radius: 0.4rem;
  background: var(--surface-sheet-solid);
  color: var(--text-primary);
  padding: 0.45rem 0.6rem;
  font: inherit;
  font-size: var(--text-sm-size);
}
</style>
