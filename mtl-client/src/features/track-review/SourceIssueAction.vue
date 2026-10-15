<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import Button from 'primevue/button';
import type { SourceIssueTarget, SourceIssueTargetsSnapshot } from 'x8ing-mtl-api-typescript-fetch';
import { useSourceIssueStore } from '@/stores/sourceIssueStore';
import { SHARED_DEMO_READ_ONLY_MESSAGE, useDemoAccessStore } from '@/stores/demoAccessStore';
import { reviewError } from './api';
import SourceIssueFeedback from './SourceIssueFeedback.vue';

const props = withDefaults(
  defineProps<{
    index?: string;
    sourceId?: number;
    targets?: SourceIssueTarget[];
    label?: string;
    disabled?: boolean;
    severity?: 'primary' | 'secondary';
    showFeedback?: boolean;
  }>(),
  {
    index: undefined,
    sourceId: undefined,
    targets: undefined,
    label: undefined,
    severity: 'secondary',
    showFeedback: true,
  }
);
const emit = defineEmits<{ acknowledged: [count: number]; saving: [active: boolean] }>();
const store = useSourceIssueStore();
const demoAccess = useDemoAccessStore();
onMounted(() => void demoAccess.load());
const snapshot = ref<SourceIssueTargetsSnapshot | null>(null);
const preparing = ref(false);
const error = ref('');
let generation = 0;
onBeforeUnmount(() => generation++);
watch(
  () => [props.index, props.sourceId, store.sessionRevision],
  () => {
    generation++;
    snapshot.value = null;
    error.value = '';
    preparing.value = false;
  }
);

async function save(targets: SourceIssueTarget[]) {
  if (store.busy || props.disabled || !demoAccess.writeAllowed || !targets.length) return;
  const current = generation;
  error.value = '';
  const selection = targets.map((target) => ({ ...target }));
  emit('saving', true);
  try {
    const result = await store.save(selection);
    if (current !== generation) return;
    snapshot.value = null;
    const saved = (result.newlyAcknowledged ?? 0) + (result.alreadyAcknowledged ?? 0);
    if (saved) emit('acknowledged', saved);
  } catch (failure) {
    const text = await reviewError(failure);
    if (current === generation) error.value = text;
  } finally {
    if (current === generation) emit('saving', false);
  }
}
async function acknowledge() {
  if (store.busy || preparing.value || props.disabled || !demoAccess.writeAllowed) return;
  if (props.targets) return save(props.targets);
  const current = ++generation;
  preparing.value = true;
  error.value = '';
  try {
    const result = await store.prepare({ index: props.index, sourceId: props.sourceId });
    if (current === generation) {
      snapshot.value = result;
      if (!result.targets?.length) error.value = 'Nothing left to acknowledge. Refresh the current issues.';
    }
  } catch (failure) {
    const text = await reviewError(failure);
    if (current === generation) error.value = text;
  } finally {
    if (current === generation) preparing.value = false;
  }
}
</script>

<template>
  <div class="issue-action">
    <template v-if="snapshot?.targets?.length">
      <span role="status">
        {{ snapshot.pendingFileCount }}
        {{ snapshot.pendingFileCount === 1 ? 'file' : 'files' }} · {{ snapshot.targets.length }}
        {{ snapshot.targets.length === 1 ? 'issue' : 'issues' }} selected
      </span>
      <Button
        label="Confirm acknowledgement"
        size="small"
        :disabled="store.busy || disabled || !demoAccess.writeAllowed"
        @click="save(snapshot.targets)"
      />
      <Button label="Cancel" size="small" severity="secondary" :disabled="store.busy" @click="snapshot = null" />
    </template>
    <Button
      v-else
      :label="label ?? (sourceId ? 'Acknowledge' : 'Acknowledge all')"
      size="small"
      :severity="severity === 'primary' ? undefined : 'secondary'"
      :loading="preparing || store.busy"
      :disabled="store.busy || disabled || !demoAccess.writeAllowed || (targets != null && !targets.length)"
      :title="demoAccess.demoMode === true ? SHARED_DEMO_READ_ONLY_MESSAGE : undefined"
      @click="acknowledge"
    />
    <p v-if="error" role="alert">{{ error }}</p>
    <SourceIssueFeedback v-if="showFeedback" />
  </div>
</template>

<style scoped>
.issue-action {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
}
.issue-action p {
  width: 100%;
  margin: 0;
  color: var(--error);
}
</style>
