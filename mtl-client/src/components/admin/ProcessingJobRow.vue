<template>
  <ProcessingTaskRow
    v-model:expanded="expanded"
    :label="jobPresentation(j).label"
    :icon="jobPresentation(j).icon"
    :state="jobState(j)"
    :needs-attention="jobAttentionCount(j) > 0"
    :status-label="jobStatus(j)"
    :explanation="jobPresentation(j).explanation"
    :progress="j.progressKnown !== false && j.total > 0 ? j.progressPercent : null"
    :segments="segments"
    :segments-text="segmentsText"
    :indeterminate="j.progressKnown === false && j.state === 'RUNNING'"
    :progress-label="
      j.progressKnown === false
        ? waitsForWork || j.state === 'WAITING'
          ? 'Not started'
          : 'Preparing'
        : j.total > 0
          ? 'Complete'
          : `No ${jobUnit(j)}`
    "
  >
    <template #summary>
      <template v-if="j.job === 'demoHealth'">Synthetic demo data · </template>
      <span v-if="j.progressKnown === false">{{
        j.job === 'demoHealth'
          ? j.state === 'WAITING'
            ? healthWaitSummary
            : 'Preparing health records.'
          : waitsForWork
            ? `No ${jobUnit(j)} to process yet.`
            : 'Preparing background work.'
      }}</span>
      <template v-else>
        {{ formatCount(j.done) }}
        of {{ formatCount(j.total) }} {{ jobUnit(j) }} processed
        <template v-if="j.failed">
          ·
          {{ formatCount(j.failed) }} failed
        </template>
      </template>
      <template v-if="j.unavailable">
        ·
        {{ formatCount(j.unavailable) }}
        unavailable
      </template>
      <span v-if="j.progressKnown !== false && breakdown.length" class="processing-task__counts processing-breakdown">
        <template v-for="(bucket, index) in breakdown" :key="bucket.key">
          <span v-if="index" aria-hidden="true"> · </span>
          <span class="processing-breakdown__item"
            ><span :class="['processing-swatch', `processing-swatch--${bucket.key}`]" aria-hidden="true" />{{
              formatCount(bucket.value)
            }}
            {{ bucket.key }}</span
          >
        </template>
      </span>
    </template>
    <template v-if="hasNotice" #notice>
      <span v-if="rowNotice.after.length" class="processing-blocker">
        Starts after
        <template v-for="(blocker, index) in rowNotice.after" :key="blockerKey(blocker)">
          <template v-if="index"> and </template>
          <button type="button" @click="revealBlocker(blocker)">{{ targetLabel(blocker) }}</button> </template
        >.
      </span>
      <span v-for="(blocker, index) in rowNotice.waits" :key="blockerKey(blocker)" class="processing-blocker">
        {{ waitLeadIn(blocker, j, itemWaitCount) }}
        <button v-if="blockerTarget(blocker)" type="button" @click="revealBlocker(blocker)">
          {{ targetLabel(blocker) }}</button
        >.<template v-if="index === rowNotice.waits.length - 1"> Resumes automatically.</template>
      </span>
      <span v-if="rowNotice.queuedBehind" class="processing-blocker">
        Queued behind
        <button
          v-if="blockerTarget(rowNotice.queuedBehind)"
          type="button"
          @click="revealBlocker(rowNotice.queuedBehind)"
        >
          {{ targetLabel(rowNotice.queuedBehind) }}</button
        ><template v-else>other processing</template>.
      </span>
      <span v-if="schedule">{{ schedule }}</span>
      <ProcessingHoldingUp v-if="holdingUp.length" :jobs="holdingUp" :target-label="targetLabel" @reveal="revealRow" />
      <span
        v-for="blocker in rowNotice.userActions"
        :key="blockerKey(blocker)"
        class="processing-blocker processing-blocker--action"
      >
        <i class="bi bi-exclamation-triangle" aria-hidden="true" />
        {{ userActionText(blocker, j) }}
        <template v-if="blocker.reason === 'MISSING_ACTIVITY'">
          Find them in <RouterLink :to="{ name: 'stats' }">Statistics</RouterLink> › Tracks › No activity.
        </template>
        <template v-else-if="blocker.reason === 'MISSING_CALCULATION'">Open track details to review.</template>
      </span>
    </template>
    <div class="processing-detail-heading"><h4>Current work</h4></div>
    <p class="processing-detail-copy">{{ jobPresentation(j).explanation }}</p>
    <ProcessingJobDependencies
      :id="j.job ?? ''"
      kind="JOB"
      :blocked-by="j.blockedBy"
      :waiting-dependents="holdingUp"
      :dependencies="dependencies ?? []"
      :target-label="targetLabel"
      @reveal="revealRow"
    />
    <dl v-if="j.progressKnown !== false" class="processing-facts">
      <div>
        <dt>Completed</dt>
        <dd>{{ formatCount(j.done) }}</dd>
      </div>
      <div>
        <dt>Pending</dt>
        <dd>
          {{ formatCount(j.pending) }}
        </dd>
      </div>
      <div v-if="jobAttentionCount(j)">
        <dt>Need attention</dt>
        <dd>{{ formatCount(jobAttentionCount(j)) }}</dd>
      </div>
      <div v-if="j.unavailable">
        <dt>Unavailable</dt>
        <dd>{{ formatCount(j.unavailable) }}</dd>
      </div>
    </dl>
    <dl v-if="j.unavailableReasons && (j.unavailable || jobAttentionCount(j))" class="processing-detail-lines">
      <div v-if="j.unavailableReasons.noDataPoints">
        <dt>No GPS points</dt>
        <dd>{{ formatCount(j.unavailableReasons.noDataPoints) }}<span>The source has no usable GPS points.</span></dd>
      </div>
      <div v-if="j.unavailableReasons.unsupportedTiming">
        <dt>Source timing</dt>
        <dd>
          {{ formatCount(j.unavailableReasons.unsupportedTiming) }}<span>Timing is unreliable or estimated.</span>
        </dd>
      </div>
      <div v-if="j.unavailableReasons.missingActivity">
        <dt>Missing activity</dt>
        <dd>
          {{ formatCount(j.unavailableReasons.missingActivity)
          }}<span>Select an activity in track details to calculate energy.</span>
        </dd>
      </div>
      <div v-if="j.unavailableReasons.missingCalculation">
        <dt>Missing estimate</dt>
        <dd>
          {{ formatCount(j.unavailableReasons.missingCalculation)
          }}<span>Open track details to review the missing energy estimate.</span>
        </dd>
      </div>
    </dl>
    <p v-if="j.unavailable && !j.unavailableReasons" class="processing-detail-note">
      {{ formatCount(j.unavailable) }} unavailable. {{ unavailableExplanation(j.job) }}
    </p>
    <p v-if="j.failed" class="processing-detail-warning">
      {{ formatCount(j.failed) }} calculations failed. Processing has continued for other {{ jobUnit(j) }}.
      <RouterLink :to="{ name: 'admin', params: { section: 'logs' } }">Review error diagnostics</RouterLink>
    </p>
    <details v-if="j.progressKnown !== false" class="processing-technical">
      <summary>Technical details</summary>
      <dl>
        <dt>Total {{ jobUnit(j) }}</dt>
        <dd>{{ formatCount(j.total) }}</dd>
        <dt>Failed</dt>
        <dd>{{ formatCount(j.failed) }}</dd>
        <dt>Unavailable</dt>
        <dd>
          {{ formatCount(j.unavailable) }}
        </dd>
      </dl>
    </details>
  </ProcessingTaskRow>
</template>
<script setup lang="ts">
import { computed } from 'vue';
import { RouterLink } from 'vue-router';
import ProcessingTaskRow from './ProcessingTaskRow.vue';
import ProcessingJobDependencies from './ProcessingJobDependencies.vue';
import ProcessingHoldingUp from './ProcessingHoldingUp.vue';
import type { ProcessingStatusDto } from 'x8ing-mtl-api-typescript-fetch';
import type { JobSummary } from '@/utils/serverAdminApi';
import { formatLocaleNumber } from '@/utils/Utils';
import {
  attentionCount as jobAttentionCount,
  processingState as jobState,
  processingLabel as jobStatus,
  blockerTarget,
  jobPresentation,
  jobUnit,
  rowNotice as noticeForJob,
  scheduleText,
  userActionText,
  waitLeadIn,
  workBreakdown,
  workSegments,
  type ProcessingBlocker,
  type ProcessingRowNotice,
} from '@/utils/processingJobPresentation';
const props = defineProps<{
  job: JobSummary;
  /** Browser time minus server time; unknown offsets use the browser clock. */
  clockOffsetMs?: number | null;
  targetLabel: (blocker: ProcessingBlocker) => string;
  dependencies?: ProcessingStatusDto['dependencies'];
  /** Notice prepared with the other jobs in view; a standalone row explains only its own waits. */
  notice?: ProcessingRowNotice;
  /** Jobs currently waiting for this one. */
  holdingUp?: string[];
}>();
const emit = defineEmits<{ reveal: [key: string] }>();
const expanded = defineModel<boolean>('expanded', { default: false });
const j = computed(() => props.job);
const rowNotice = computed(() => props.notice ?? noticeForJob(props.job));
const healthWaitSummary = computed(() => {
  const prerequisites = [...rowNotice.value.after, ...rowNotice.value.waits]
    .map(props.targetLabel)
    .filter((label, index, labels) => labels.indexOf(label) === index);
  return `Waiting for ${prerequisites.length ? prerequisites.join(' and ') : 'track processing'}.`;
});
/** A job that has no work yet because its prerequisite has not produced any. */
const waitsForWork = computed(() => j.value.job !== 'demoHealth' && j.value.state === 'WAITING' && !j.value.pending);
const holdingUp = computed(() => props.holdingUp ?? []);
const itemWaitCount = computed(() => rowNotice.value.waits.filter((blocker) => blocker.scope === 'ITEMS').length);
const schedule = computed(() =>
  scheduleText(j.value, Boolean(rowNotice.value.queuedBehind), Date.now() - (props.clockOffsetMs ?? 0))
);
const segments = computed(() => workSegments(j.value));
const breakdown = computed(() => workBreakdown(j.value));
const segmentsText = computed(() =>
  segments.value
    ? [
        `${j.value.progressPercent}% complete`,
        ...(['ready', 'waiting', 'retrying'] as const)
          .filter((key) => (j.value.work?.[key] ?? 0) > 0)
          .map((key) => `${formatCount(j.value.work?.[key])} ${key}`),
      ].join(', ')
    : ''
);
const hasNotice = computed(
  () =>
    rowNotice.value.after.length > 0 ||
    rowNotice.value.waits.length > 0 ||
    rowNotice.value.queuedBehind !== null ||
    rowNotice.value.userActions.length > 0 ||
    holdingUp.value.length > 0 ||
    schedule.value !== ''
);
const formatCount = (value: number | undefined) => formatLocaleNumber(value ?? 0);
function blockerKey(blocker: ProcessingBlocker) {
  return `${blocker.kind}:${blocker.id}:${blocker.reason}`;
}
function revealRow(key: string) {
  emit('reveal', key);
}
function revealBlocker(blocker: ProcessingBlocker) {
  const target = blockerTarget(blocker);
  if (target) revealRow(target);
}
function unavailableExplanation(job: string | undefined) {
  return job === 'activityType'
    ? 'Activity could not be determined for tracks without usable points. Processing is complete for these tracks.'
    : 'This can include unreliable timing, missing GPS points, a missing activity, or a missing calculation. Unavailable estimates do not block other processing.';
}
</script>
