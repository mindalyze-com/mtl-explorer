<template>
  <div class="all-versions-action">
    <Button
      label="Copy all versions"
      icon="bi bi-copy"
      class="all-versions-copy"
      :disabled="loading"
      @click="openReport"
    />
    <span>Preview the versions included in a support request.</span>
  </div>
  <Dialog v-model:visible="visible" modal header="All versions" :style="{ width: 'min(92vw, 42rem)' }">
    <p class="all-versions-description">
      Component versions and build information only. No logs or private configuration.
    </p>
    <label class="all-versions-label" :for="reportId">Version report</label>
    <textarea :id="reportId" class="all-versions-preview" :value="snapshot" readonly rows="14" spellcheck="false" />
    <p v-if="copyError" role="alert" class="all-versions-feedback">Copy failed. Select and copy the report above.</p>
    <p role="status" class="all-versions-feedback">{{ copied ? 'All versions copied.' : '' }}</p>
    <template #footer>
      <Button label="Close" text @click="visible = false" />
      <Button
        label="Copy all versions"
        :icon="copied ? 'bi bi-check2' : 'bi bi-copy'"
        class="all-versions-copy"
        @click="copyReport"
      />
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { ref, useId } from 'vue';
import Button from 'primevue/button';
import Dialog from 'primevue/dialog';
import { writeTextToClipboard } from '@/utils/clipboard';

const props = defineProps<{ report: string; loading?: boolean }>();
const reportId = useId();
const visible = ref(false);
const snapshot = ref('');
const copied = ref(false);
const copyError = ref(false);

function openReport() {
  snapshot.value = props.report;
  copied.value = false;
  copyError.value = false;
  visible.value = true;
}

async function copyReport() {
  copied.value = false;
  copyError.value = false;
  try {
    await writeTextToClipboard(snapshot.value);
    copied.value = true;
  } catch {
    copyError.value = true;
  }
}
</script>

<style scoped>
.all-versions-copy {
  --p-button-primary-background: var(--p-blue-600);
  --p-button-primary-border-color: var(--p-blue-600);
  --p-button-primary-hover-background: var(--p-blue-700);
  --p-button-primary-hover-border-color: var(--p-blue-700);
  --p-button-primary-active-background: var(--p-blue-800);
  --p-button-primary-active-border-color: var(--p-blue-800);
  --p-button-primary-color: var(--accent-contrast);
  --p-button-primary-hover-color: var(--accent-contrast);
  --p-button-primary-active-color: var(--accent-contrast);
  --p-button-primary-focus-ring-shadow: 0 0 0 0.2rem var(--p-blue-200);
}

.all-versions-action {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.5rem 0.75rem;
  margin-bottom: 1rem;
}
.all-versions-action > span,
.all-versions-description,
.all-versions-feedback {
  color: var(--text-muted);
  font-size: var(--text-sm-size);
}
.all-versions-description {
  margin: 0 0 0.75rem;
}
.all-versions-label {
  display: block;
  margin-bottom: 0.35rem;
}
.all-versions-preview {
  box-sizing: border-box;
  width: 100%;
  max-height: 50vh;
  padding: 0.65rem;
  border: 1px solid var(--border-default);
  border-radius: 0.35rem;
  background: var(--surface-elevated);
  color: var(--text-primary);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: var(--text-xs-size);
  line-height: 1.5;
  resize: vertical;
  -webkit-user-select: text;
  user-select: text;
}
.all-versions-feedback {
  min-height: 1.3em;
  margin: 0.5rem 0 0;
}
</style>
