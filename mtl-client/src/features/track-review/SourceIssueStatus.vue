<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import Button from 'primevue/button';
import {
  GetSourceIssueAcknowledgementTargetsStateEnum as TargetState,
  type SourceIssue,
} from 'x8ing-mtl-api-typescript-fetch';
import { sourceIssueApi, useSourceIssueStore } from '@/stores/sourceIssueStore';
import { SHARED_DEMO_READ_ONLY_MESSAGE, useDemoAccessStore } from '@/stores/demoAccessStore';
import SourceIssueAction from './SourceIssueAction.vue';
import { reviewError } from './api';
import { plural } from './sourceIssuePresentation';

const props = defineProps<{
  sourceId: number;
  sourceHash?: string;
  disabled?: boolean;
  hasNext?: boolean;
  refreshVersion?: number;
}>();
const emit = defineEmits<{
  acknowledged: [sourceId: number];
  refresh: [sourceId: number];
  reassess: [sourceId: number];
}>();
const store = useSourceIssueStore();
const demoAccess = useDemoAccessStore();
onMounted(() => void demoAccess.load());
const issues = ref<SourceIssue[]>([]);
const error = ref('');
const loading = ref(false);
const reopening = ref(false);
const acknowledging = ref(false);
let processingAcknowledgement = false;
const diagnosisStale = ref(false);
let displayedSignature: string | undefined;
let requestedSignature: string | undefined;
const PAGE_SIZE = 100;
let generation = 0;
let latestLoad: { sourceId: number; session: number; promise: Promise<boolean> } | undefined;
onBeforeUnmount(() => generation++);
const pending = computed(() => issues.value.filter((issue) => issue.state === 'PENDING'));
const acknowledged = computed(() => issues.value.filter((issue) => issue.state === 'ACKNOWLEDGED'));
const pendingTargets = computed(() =>
  pending.value.map((issue) => ({ issueId: issue.issueId, sourceRevision: issue.sourceRevision }))
);
const acknowledgedAt = computed(
  () =>
    acknowledged.value
      .map((issue) => issue.acknowledgedAt)
      .filter((date): date is Date => !!date)
      .sort((a, b) => b.getTime() - a.getTime())[0]
);

function load() {
  const current = ++generation;
  const id = props.sourceId;
  const session = store.sessionRevision;
  const promise = readIssues(current, id, session);
  latestLoad = { sourceId: id, session, promise };
  return promise;
}
async function readIssues(current: number, id: number, session: number) {
  const isCurrent = () => current === generation && id === props.sourceId && session === store.sessionRevision;
  loading.value = true;
  error.value = '';
  try {
    const result = new Map<number, SourceIssue>();
    let offset = 0;
    while (isCurrent()) {
      const page = await sourceIssueApi().getSourceIssues({ sourceId: id, offset, limit: PAGE_SIZE });
      if (!isCurrent()) return false;
      for (const issue of page.issues ?? []) {
        if (issue.issueId != null && (issue.state === 'PENDING' || issue.state === 'ACKNOWLEDGED'))
          result.set(issue.issueId, issue);
      }
      offset += PAGE_SIZE;
      // Active issues sort before resolved history; the status only needs current reviewable issues.
      if (
        offset >= (page.total ?? 0) ||
        !page.issues?.length ||
        page.issues.some((issue) => issue.state === 'RESOLVED')
      )
        break;
    }
    if (!isCurrent()) return false;
    issues.value = [...result.values()];
    const signature = [...result.values()]
      .map((issue) => `${issue.issueId}:${issue.sourceRevision}`)
      .sort()
      .join('|');
    const revisionMatches =
      !props.sourceHash ||
      props.sourceHash === 'unreadable' ||
      issues.value.every((issue) => issue.sourceRevision === `sha256:${props.sourceHash}`);
    const findingsMatch = displayedSignature == null || displayedSignature === signature;
    diagnosisStale.value = !revisionMatches || !findingsMatch;
    if (!diagnosisStale.value) displayedSignature = signature;
    else if (requestedSignature !== signature) {
      requestedSignature = signature;
      emit('refresh', id);
    }
    return true;
  } catch (failure) {
    const text = await reviewError(failure);
    if (isCurrent()) error.value = text;
    return false;
  } finally {
    if (isCurrent()) loading.value = false;
  }
}
watch(
  () => [props.sourceId, store.sessionRevision],
  () => {
    issues.value = [];
    acknowledging.value = false;
    processingAcknowledgement = false;
    requestedSignature = undefined;
  }
);
watch(
  () => [props.sourceId, props.sourceHash, props.refreshVersion, store.sessionRevision],
  () => {
    displayedSignature = undefined;
    diagnosisStale.value = false;
  }
);
watch(() => [props.sourceId, props.sourceHash, props.refreshVersion, store.sessionRevision], load, { immediate: true });
watch(
  () => [store.revision, store.pendingFileCount, store.acknowledgedFileCount],
  () => {
    if (!acknowledging.value) void load();
  }
);
function onSaving(active: boolean) {
  if (active) acknowledging.value = true;
  else if (!processingAcknowledgement) {
    acknowledging.value = false;
    void load();
  }
}
async function onAcknowledged() {
  const id = props.sourceId;
  const session = store.sessionRevision;
  processingAcknowledgement = true;
  try {
    void load();
    // A diagnosis refresh can replace this verification read. Follow the latest read for
    // this source and session, rather than treating the superseded read as a failure.
    while (id === props.sourceId && session === store.sessionRevision) {
      const request = latestLoad;
      if (!request || request.sourceId !== id || request.session !== session) return;
      const loaded = await request.promise;
      if (id !== props.sourceId || session !== store.sessionRevision) return;
      if (request !== latestLoad) continue;
      if (loaded && !pending.value.length && !diagnosisStale.value) emit('acknowledged', id);
      return;
    }
  } finally {
    if (id === props.sourceId && session === store.sessionRevision) {
      processingAcknowledgement = false;
      acknowledging.value = false;
    }
  }
}
async function reopen() {
  if (
    !demoAccess.writeAllowed ||
    store.busy ||
    props.disabled ||
    diagnosisStale.value ||
    reopening.value ||
    !acknowledged.value.length
  )
    return;
  const id = props.sourceId;
  const session = store.sessionRevision;
  reopening.value = true;
  error.value = '';
  try {
    const snapshot = await store.prepare({ sourceId: id }, TargetState.Acknowledged);
    if (id !== props.sourceId || session !== store.sessionRevision) return;
    if (snapshot.targets.length) await store.save(snapshot.targets, true);
    else await load();
  } catch (failure) {
    const text = await reviewError(failure);
    if (id === props.sourceId && session === store.sessionRevision) error.value = text;
  } finally {
    reopening.value = false;
  }
}
</script>

<template>
  <div
    v-if="pending.length || acknowledged.length || error || loading"
    class="issue-status"
    :class="{ 'issue-status--pending': pending.length }"
  >
    <i :class="pending.length ? 'bi bi-info-circle' : 'bi bi-check2-circle'" aria-hidden="true"></i>
    <div class="issue-status__text">
      <strong v-if="pending.length">{{ plural(pending.length, 'open issue') }}</strong>
      <strong v-else-if="acknowledged.length">
        {{ acknowledgedAt ? `Acknowledged · ${acknowledgedAt.toLocaleDateString()}` : 'Acknowledged' }}
      </strong>
      <span v-if="pending.length">Acknowledge to clear the warning. Findings and use settings stay as they are.</span>
      <span v-if="loading" role="status">Refreshing issues…</span>
      <span v-if="diagnosisStale" role="status"
        >The file or its findings changed. Reassess its diagnosis before acknowledging.</span
      >
      <span v-if="error" role="alert">{{ error }}</span>
    </div>
    <SourceIssueAction
      v-if="pending.length || acknowledging"
      :source-id="sourceId"
      :targets="pendingTargets"
      :disabled="disabled || loading || reopening || diagnosisStale"
      :label="hasNext ? 'Acknowledge & next' : 'Acknowledge'"
      :show-feedback="false"
      severity="primary"
      @acknowledged="onAcknowledged"
      @saving="onSaving"
    />
    <Button
      v-else-if="acknowledged.length"
      label="Reopen"
      size="small"
      severity="secondary"
      text
      :disabled="!demoAccess.writeAllowed || store.busy || disabled || loading || reopening || diagnosisStale"
      :title="demoAccess.demoMode === true ? SHARED_DEMO_READ_ONLY_MESSAGE : undefined"
      @click="reopen"
    />
    <Button
      v-if="diagnosisStale"
      label="Reassess diagnosis"
      size="small"
      severity="secondary"
      text
      :disabled="!demoAccess.writeAllowed || store.busy || disabled || loading"
      :title="demoAccess.demoMode === true ? SHARED_DEMO_READ_ONLY_MESSAGE : undefined"
      @click="demoAccess.writeAllowed && emit('reassess', sourceId)"
    />
  </div>
</template>

<style scoped>
.issue-status {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem 0.75rem;
  margin: 0.75rem 0;
  padding: 0.65rem 0.8rem;
  border: 1px solid var(--border-subtle);
  border-radius: 0.6rem;
}
.issue-status--pending {
  border-color: color-mix(in srgb, var(--accent) 35%, var(--border-subtle));
  background: color-mix(in srgb, var(--accent) 6%, transparent);
}
.issue-status > i {
  color: var(--accent-text);
  font-size: 1.1rem;
}
.issue-status:not(.issue-status--pending) > i {
  color: var(--success);
}
.issue-status__text {
  display: grid;
  flex: 1 1 12rem;
  gap: 0.1rem;
}
.issue-status__text span {
  color: var(--text-secondary);
  font-size: 0.85rem;
}
</style>
