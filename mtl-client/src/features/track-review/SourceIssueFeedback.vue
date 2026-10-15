<script setup lang="ts">
import Button from 'primevue/button';
import { onMounted } from 'vue';
import { useSourceIssueStore } from '@/stores/sourceIssueStore';
import { SHARED_DEMO_READ_ONLY_MESSAGE, useDemoAccessStore } from '@/stores/demoAccessStore';
const store = useSourceIssueStore();
const demoAccess = useDemoAccessStore();
onMounted(() => void demoAccess.load());
</script>

<template>
  <div v-if="store.feedbackMessage || store.feedbackError" class="issue-feedback">
    <span v-if="store.feedbackMessage" role="status">{{ store.feedbackMessage }}</span>
    <Button
      v-if="store.undoTargets.length"
      :label="store.feedbackError ? 'Retry Undo' : 'Undo'"
      size="small"
      severity="secondary"
      text
      :disabled="store.busy || !demoAccess.writeAllowed"
      :title="demoAccess.demoMode === true ? SHARED_DEMO_READ_ONLY_MESSAGE : undefined"
      @click="store.undo"
    />
    <Button
      label="Dismiss"
      size="small"
      severity="secondary"
      text
      :disabled="store.busy"
      @click="store.dismissFeedback"
    />
    <p v-if="store.feedbackError" role="alert">{{ store.feedbackError }}</p>
  </div>
</template>

<style scoped>
.issue-feedback {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.4rem;
  margin: 0.5rem 0;
  color: var(--text-secondary);
  font-size: var(--text-sm-size);
}
.issue-feedback p {
  width: 100%;
  margin: 0;
  color: var(--error);
}
</style>
