<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { RouterLink } from 'vue-router';
import Button from 'primevue/button';
import {
  GetSourceIssuesStateEnum as State,
  GetSourceIssueAcknowledgementTargetsStateEnum as TargetState,
  type SourceIssuePage,
  type SourceIssue,
} from 'x8ing-mtl-api-typescript-fetch';
import { sourceIssueApi, useSourceIssueStore } from '@/stores/sourceIssueStore';
import { triggerIndexerRescan } from '@/utils/serverAdminApi';
import SourceIssueAction from './SourceIssueAction.vue';
import SourceIssueFeedback from './SourceIssueFeedback.vue';
import { reviewError } from './api';
import { SHARED_DEMO_READ_ONLY_MESSAGE, useDemoAccessStore } from '@/stores/demoAccessStore';
import { formatLocaleNumber } from '@/utils/Utils';

const props = defineProps<{ sourceId?: number; index?: string; disabled?: boolean }>();
const emit = defineEmits<{
  'pending-count': [count: number];
  'view-pending-count': [count: number | null];
  open: [sourceId: number, sourceIds: number[]];
}>();
const store = useSourceIssueStore();
const demoAccess = useDemoAccessStore();
onMounted(() => void demoAccess.load());
const page = ref<SourceIssuePage | null>(null);
const state = ref<State>(State.Pending);
const offset = ref(0);
const loading = ref(false);
const retrying = ref(false);
const reopening = ref(false);
const error = ref('');
const message = ref('');
const PAGE_SIZE = 50;
const STATES = [
  { value: State.Pending, label: 'To review' },
  { value: State.Acknowledged, label: 'Acknowledged' },
  { value: State.Information, label: 'Information' },
  { value: State.Resolved, label: 'Resolved' },
];
let generation = 0;
onBeforeUnmount(() => generation++);
const scope = () => [props.sourceId, props.index, state.value, store.sessionRevision].join(':');
const groups = computed(() => {
  const result = new Map<number, SourceIssue[]>();
  for (const issue of page.value?.issues ?? []) {
    if (issue.sourceId == null) continue;
    const values = result.get(issue.sourceId) ?? [];
    values.push(issue);
    result.set(issue.sourceId, values);
  }
  return [...result.entries()];
});
const gpsSourceIds = computed(() => groups.value.filter(([, issues]) => issues[0]?.index === 'GPS').map(([id]) => id));
const scopeLabel = computed(() =>
  props.sourceId ? 'one file' : props.index ? (props.index === 'GPS' ? 'track files' : 'media files') : ''
);

function stateCount(value: State) {
  if (props.sourceId) return undefined;
  const summary = props.index
    ? page.value?.summary?.indexes?.find((entry) => entry.index === props.index)
    : page.value?.summary;
  if (value === State.Pending) return summary?.pendingFileCount;
  if (value === State.Acknowledged) return summary?.acknowledgedFileCount;
  return undefined;
}
const hasPendingIssues = computed(
  () =>
    !!page.value &&
    !error.value &&
    state.value === State.Pending &&
    (props.sourceId ? (page.value.total ?? 0) > PAGE_SIZE : (stateCount(State.Pending) ?? 0) > 0)
);
function targets(issues: SourceIssue[]) {
  return issues
    .filter((issue) => issue.state === 'PENDING' && issue.issueId != null && issue.sourceRevision)
    .map((issue) => ({ issueId: issue.issueId, sourceRevision: issue.sourceRevision }));
}
function acknowledgementLabel(id: number) {
  const values = page.value?.issues ?? [];
  const continued =
    (offset.value > 0 && values[0]?.sourceId === id) ||
    (offset.value + PAGE_SIZE < (page.value?.total ?? 0) && values.at(-1)?.sourceId === id);
  return continued ? 'Acknowledge shown issues' : 'Acknowledge';
}
function messages(issues: SourceIssue[]) {
  return [...new Set(issues.map((issue) => issue.message?.trim()).filter(Boolean))];
}

async function load() {
  const current = ++generation;
  loading.value = true;
  error.value = '';
  try {
    const result = await sourceIssueApi().getSourceIssues({
      index: props.index,
      sourceId: props.sourceId,
      state: state.value,
      offset: offset.value,
      limit: PAGE_SIZE,
    });
    if (current === generation) {
      page.value = result;
      emit('pending-count', result.summary?.pendingFileCount ?? 0);
      emit('view-pending-count', stateCount(State.Pending) ?? null);
    }
  } catch (failure) {
    const text = await reviewError(failure);
    if (current === generation) error.value = text;
  } finally {
    if (current === generation) loading.value = false;
  }
}
watch(
  () =>
    [
      props.sourceId,
      props.index,
      state.value,
      store.revision,
      store.pendingFileCount,
      store.acknowledgedFileCount,
      store.sessionRevision,
    ] as const,
  (current, previous) => {
    if (
      !previous ||
      current[0] !== previous[0] ||
      current[1] !== previous[1] ||
      current[2] !== previous[2] ||
      current[6] !== previous[6]
    ) {
      page.value = null;
      emit('view-pending-count', null);
      message.value = '';
    }
    offset.value = 0;
    void load();
  },
  { immediate: true }
);
watch(offset, load);

async function reopen(sourceId: number) {
  if (!demoAccess.writeAllowed || store.busy || props.disabled || reopening.value) return;
  const currentScope = scope();
  const current = generation;
  reopening.value = true;
  error.value = '';
  message.value = '';
  try {
    const snapshot = await store.prepare({ sourceId }, TargetState.Acknowledged);
    if (currentScope !== scope() || current !== generation) return;
    if (snapshot.targets.length) await store.save(snapshot.targets, true);
    else if (current === generation) message.value = 'Nothing left to reopen. Refresh the current issues.';
  } catch (failure) {
    const text = await reviewError(failure);
    if (currentScope === scope()) error.value = text;
  } finally {
    reopening.value = false;
  }
}
async function retry(index: string | undefined) {
  if (!demoAccess.writeAllowed || (index !== 'GPS' && index !== 'MEDIA') || retrying.value) return;
  retrying.value = true;
  const currentScope = scope();
  error.value = '';
  try {
    const result = await triggerIndexerRescan(index);
    if (currentScope === scope()) message.value = result.message ?? 'Rescan queued.';
  } catch (failure) {
    const text = await reviewError(failure);
    if (currentScope === scope()) error.value = text;
  } finally {
    retrying.value = false;
  }
}
</script>

<template>
  <section class="source-issues" aria-label="Source issues">
    <SourceIssueFeedback />
    <div class="source-issues__toolbar">
      <div class="source-issues__states" role="group" aria-label="Issue state">
        <button
          v-for="choice in STATES"
          :key="choice.value"
          type="button"
          :aria-pressed="state === choice.value"
          :disabled="store.busy || reopening"
          @click="state = choice.value"
        >
          {{ choice.label }}
          <span v-if="stateCount(choice.value)" class="source-issues__count">{{
            formatLocaleNumber(stateCount(choice.value)!)
          }}</span>
        </button>
      </div>
      <Button
        label="Refresh"
        icon="bi bi-arrow-clockwise"
        size="small"
        severity="secondary"
        text
        :disabled="store.busy || loading || reopening"
        @click="load"
      />
    </div>
    <p v-if="scopeLabel" class="source-issues__muted">
      Showing {{ scopeLabel }} only · <RouterLink :to="{ name: 'review' }">Show all files</RouterLink>
    </p>
    <p v-if="demoAccess.demoMode === true" class="source-issues__muted">{{ SHARED_DEMO_READ_ONLY_MESSAGE }}</p>
    <div v-if="hasPendingIssues" class="source-issues__bulk">
      <span>Not planning to fix these? Acknowledge them to clear the warning until something changes.</span>
      <SourceIssueAction
        :index="index"
        :source-id="sourceId"
        :label="sourceId ? 'Acknowledge file' : undefined"
        :disabled="!demoAccess.writeAllowed || disabled || reopening || loading"
        :show-feedback="false"
      />
    </div>
    <p v-if="error" role="alert" class="source-issues__error">{{ error }}</p>
    <p v-if="message" role="status" class="source-issues__muted">{{ message }}</p>
    <p v-if="loading && !page" role="status" class="source-issues__muted">Loading issues…</p>
    <div v-else-if="page && !groups.length && !error" class="source-issues__empty">
      <i class="bi bi-check2-circle" aria-hidden="true"></i>
      <strong>{{ state === State.Pending ? 'Nothing to review' : 'No files here' }}</strong>
      <span v-if="state === State.Pending">No issues are waiting for review.</span>
    </div>
    <ul v-else-if="groups.length" class="source-issues__files">
      <li v-for="[id, issues] in groups" :key="id" class="source-issues__file">
        <i :class="issues[0]?.index === 'GPS' ? 'bi bi-geo-alt' : 'bi bi-image'" aria-hidden="true"></i>
        <div class="source-issues__body">
          <strong>{{ issues[0]?.fileName }}</strong>
          <span v-for="text in messages(issues)" :key="text" class="source-issues__message">{{ text }}</span>
          <small v-if="issues[0]?.acknowledgedAt" class="source-issues__muted"
            >Acknowledged {{ issues[0].acknowledgedAt.toLocaleDateString() }}</small
          >
          <details>
            <summary>File location</summary>
            <code>{{ issues[0]?.sourcePath }}</code>
            <Button
              :label="issues[0]?.index === 'GPS' ? 'Rescan GPS files' : 'Rescan media files'"
              size="small"
              severity="secondary"
              text
              :disabled="!demoAccess.writeAllowed || retrying || disabled"
              @click="retry(issues[0]?.index)"
            />
          </details>
        </div>
        <div class="source-issues__actions">
          <SourceIssueAction
            v-if="issues.some((issue) => issue.state === 'PENDING')"
            :source-id="id"
            :targets="targets(issues)"
            :label="acknowledgementLabel(id)"
            :disabled="!demoAccess.writeAllowed || disabled || reopening || loading"
            :show-feedback="false"
          />
          <Button
            v-if="issues.some((issue) => issue.state === 'ACKNOWLEDGED')"
            label="Reopen"
            size="small"
            severity="secondary"
            text
            :disabled="!demoAccess.writeAllowed || store.busy || disabled || reopening || loading"
            @click="reopen(id)"
          />
          <Button
            v-if="issues[0]?.index === 'GPS'"
            label="Open"
            icon="bi bi-chevron-right"
            icon-pos="right"
            size="small"
            severity="secondary"
            outlined
            :disabled="store.busy || reopening || loading"
            @click="emit('open', id, gpsSourceIds)"
          />
        </div>
      </li>
    </ul>
    <div v-if="offset || (page?.total ?? 0) > PAGE_SIZE" class="source-issues__pages">
      <Button label="Previous" size="small" text :disabled="!offset || loading" @click="offset -= PAGE_SIZE" />
      <span>Page {{ offset / PAGE_SIZE + 1 }}</span>
      <Button
        label="Next"
        size="small"
        text
        :disabled="offset + PAGE_SIZE >= (page?.total ?? 0) || loading"
        @click="offset += PAGE_SIZE"
      />
    </div>
  </section>
</template>

<style scoped>
.source-issues {
  display: grid;
  gap: 0.75rem;
}
.source-issues p {
  margin: 0;
  overflow-wrap: anywhere;
}
.source-issues__toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}
.source-issues__states {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
}
.source-issues__states button {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  min-height: 36px;
  padding: 0.3rem 0.75rem;
  border: 1px solid var(--border-medium);
  border-radius: 999px;
  background: transparent;
  color: var(--text-secondary);
  font: inherit;
  font-size: 0.85rem;
  cursor: pointer;
}
.source-issues__states button[aria-pressed='true'] {
  border-color: var(--accent);
  background: color-mix(in srgb, var(--accent) 12%, transparent);
  color: var(--text-primary);
  font-weight: 600;
}
.source-issues__count {
  min-width: 1.4rem;
  padding: 0 0.4rem;
  border-radius: 999px;
  background: var(--accent);
  color: var(--accent-contrast);
  font-size: 0.75rem;
  text-align: center;
}
.source-issues__muted {
  color: var(--text-secondary);
  font-size: 0.85rem;
}
.source-issues__error {
  color: var(--error);
}
.source-issues__bulk {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem 1rem;
  padding: 0.6rem 0.8rem;
  border-radius: 0.6rem;
  background: var(--surface-hover);
  color: var(--text-secondary);
  font-size: 0.85rem;
}
.source-issues__empty {
  display: grid;
  justify-items: center;
  gap: 0.3rem;
  padding: 2rem 1rem;
  text-align: center;
  color: var(--text-secondary);
}
.source-issues__empty i {
  font-size: 1.6rem;
  color: var(--success);
}
.source-issues__empty strong {
  color: var(--text-primary);
}
.source-issues__files {
  display: grid;
  margin: 0;
  padding: 0;
  list-style: none;
}
.source-issues__file {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: start;
  gap: 0.75rem;
  padding: 0.8rem 0.25rem;
  border-bottom: 1px solid var(--border-subtle);
}
.source-issues__file > i {
  margin-top: 0.15rem;
  color: var(--text-secondary);
}
.source-issues__body {
  display: grid;
  gap: 0.2rem;
  min-width: 0;
}
.source-issues__body strong {
  overflow-wrap: anywhere;
}
.source-issues__message {
  color: var(--text-secondary);
  font-size: 0.85rem;
}
.source-issues__body details {
  color: var(--text-secondary);
  font-size: 0.8rem;
}
.source-issues__body summary {
  width: fit-content;
  cursor: pointer;
}
.source-issues__body code {
  display: block;
  margin: 0.3rem 0;
  overflow-wrap: anywhere;
}
.source-issues__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  align-items: center;
  gap: 0.4rem;
}
.source-issues__pages {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
}
.source-issues button:focus-visible,
.source-issues summary:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}
@media (max-width: 600px) {
  .source-issues__file {
    grid-template-columns: auto minmax(0, 1fr);
  }
  .source-issues__actions {
    grid-column: 2;
    justify-content: flex-start;
  }
}
</style>
