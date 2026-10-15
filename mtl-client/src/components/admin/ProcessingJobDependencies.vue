<template>
  <section
    v-if="inputs.length || retriggers.length || neededBy.length || updates.length"
    class="processing-job-dependencies"
    aria-label="Dependencies"
  >
    <h4>Dependencies</h4>
    <div v-if="inputs.length" class="processing-dependency-line">
      <span>Depends on</span>
      <template v-for="(edge, index) in inputs" :key="`${edge.fromKind}:${edge.fromId}`">
        <span v-if="index" aria-hidden="true">·</span>
        <button type="button" @click="reveal(edge)">
          <i v-if="isWaiting(edge)" class="bi bi-clock" aria-hidden="true" />
          {{ label(edge)
          }}<span v-if="isWaiting(edge)" class="processing-dependency-wait">{{
            edge.scope === 'ITEMS' ? 'Some items waiting' : 'Waiting'
          }}</span>
        </button>
      </template>
    </div>
    <div v-for="edge in retriggers" :key="`${edge.fromKind}:${edge.fromId}`" class="processing-dependency-line">
      <span>Updates when</span>
      <button type="button" @click="reveal(edge)">{{ label(edge) }}</button>
      <span>changes.</span>
    </div>
    <div v-if="neededBy.length" class="processing-dependency-line">
      <span>Needed by</span>
      <template v-for="(job, index) in neededBy" :key="job">
        <span v-if="index" aria-hidden="true">·</span>
        <button type="button" @click="emit('reveal', `JOB:${job}`)">
          <i v-if="waitingDependents?.includes(job)" class="bi bi-clock" aria-hidden="true" />
          {{ jobLabel(job)
          }}<span v-if="waitingDependents?.includes(job)" class="processing-dependency-wait">Waiting</span>
        </button>
      </template>
    </div>
    <div v-if="updates.length" class="processing-dependency-line">
      <span>Changes also update</span>
      <template v-for="(job, index) in updates" :key="job">
        <span v-if="index" aria-hidden="true">·</span>
        <button type="button" @click="emit('reveal', `JOB:${job}`)">{{ jobLabel(job) }}</button>
      </template>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { ProcessingStatusDto } from 'x8ing-mtl-api-typescript-fetch';
import type { ProcessingBlocker } from '@/utils/processingJobPresentation';
type Edge = NonNullable<ProcessingStatusDto['dependencies']>[number];
const props = defineProps<{
  /** The row these dependencies belong to: a job, or an import that jobs depend on. */
  kind: 'JOB' | 'INDEX';
  id: string;
  blockedBy?: ProcessingBlocker[];
  /** Dependent jobs currently waiting for this row. */
  waitingDependents?: string[];
  dependencies: NonNullable<ProcessingStatusDto['dependencies']>;
  targetLabel: (blocker: ProcessingBlocker) => string;
}>();
const emit = defineEmits<{ reveal: [key: string] }>();
const edges = computed(() =>
  props.kind === 'JOB' ? props.dependencies.filter((edge) => edge.toJob === props.id) : []
);
const inputs = computed(() => edges.value.filter((edge) => edge.kind === 'GATE' || edge.kind === 'INPUT'));
const retriggers = computed(() => edges.value.filter((edge) => edge.kind === 'RETRIGGER'));
const outgoing = computed(() =>
  props.dependencies.filter((edge) => edge.fromKind === props.kind && edge.fromId === props.id)
);
const neededBy = computed(() => dependentJobs(outgoing.value.filter((edge) => edge.kind !== 'RETRIGGER')));
const updates = computed(() => dependentJobs(outgoing.value.filter((edge) => edge.kind === 'RETRIGGER')));
function dependentJobs(list: Edge[]) {
  return [...new Set(list.map((edge) => edge.toJob).filter((job): job is string => Boolean(job)))];
}
function label(edge: Edge) {
  return props.targetLabel({ kind: edge.fromKind, id: edge.fromId });
}
function jobLabel(job: string) {
  return props.targetLabel({ kind: 'JOB', id: job });
}
function reveal(edge: Edge) {
  emit('reveal', `${edge.fromKind}:${edge.fromId}`);
}
function isWaiting(edge: Edge) {
  return props.blockedBy?.some((blocker) => blocker.kind === edge.fromKind && blocker.id === edge.fromId);
}
</script>

<style scoped>
.processing-job-dependencies {
  margin: 0.8rem 0 1rem;
  color: var(--text-muted);
  font-size: var(--text-xs-size);
}
.processing-job-dependencies h4 {
  margin-bottom: 0.4rem;
}
.processing-dependency-line {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 0.3rem 0.5rem;
  margin: 0.3rem 0;
}
.processing-dependency-line button {
  border: 0;
  background: transparent;
  color: var(--accent-text);
  font: inherit;
  padding: 0.25rem 0;
  cursor: pointer;
  text-align: left;
}
.processing-dependency-line button:focus-visible {
  outline: 2px solid var(--accent-text);
  outline-offset: 3px;
}
.processing-dependency-wait {
  color: var(--text-muted);
  margin-left: 0.35rem;
}
</style>
