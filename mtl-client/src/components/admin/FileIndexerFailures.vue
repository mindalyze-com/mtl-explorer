<script setup lang="ts">
import { warnRequestFailure } from '@/utils/safeLogging';
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { RouterLink } from 'vue-router';
import Button from 'primevue/button';
import { useSourceIssueStore } from '@/stores/sourceIssueStore';
import { getIndexerFileFailures, type FileFailuresDto } from '@/utils/serverAdminApi';

const props = defineProps<{ index: string; count: number; outcome?: 'FAILED' | 'SKIPPED'; refreshKey: number }>();
const issueStore = useSourceIssueStore();
const outcomeLabel = computed(() => (props.outcome === 'SKIPPED' ? 'skipped' : 'failed'));
const PAGE_SIZE = 25;
const expanded = ref(false);
const loading = ref(false);
const error = ref('');
const result = ref<FileFailuresDto | null>(null);
const page = ref(0);
let request: AbortController | null = null;
const contentId = computed(() => `file-failures-${props.index}-${props.outcome ?? 'FAILED'}`);
const hasNextPage = computed(() => (page.value + 1) * PAGE_SIZE < (result.value?.total ?? 0));

async function load(nextPage = 0) {
  request?.abort();
  const current = new AbortController();
  request = current;
  page.value = nextPage;
  loading.value = true;
  error.value = '';
  result.value = null;
  try {
    const response = await getIndexerFileFailures(props.index, nextPage, PAGE_SIZE, current.signal, props.outcome);
    if (!current.signal.aborted) {
      const lastPage = Math.max(0, Math.ceil((response.total ?? 0) / PAGE_SIZE) - 1);
      if (nextPage > lastPage) await load(lastPage);
      else result.value = response;
    }
  } catch (failure) {
    if (!current.signal.aborted) {
      error.value = 'Files could not be loaded. Try again.';
      warnRequestFailure('Indexer failures could not be loaded', failure, {
        topic1: 'admin',
        topic2: 'FileIndexerFailures',
        topic3: 'load',
      });
    }
  } finally {
    if (request === current) loading.value = false;
  }
}

function toggle() {
  expanded.value = !expanded.value;
  if (expanded.value && !result.value && !loading.value) void load(page.value);
}

watch(
  () => [props.index, props.count, props.outcome, props.refreshKey, issueStore.revision],
  ([index], [previousIndex]) => {
    request?.abort();
    loading.value = false;
    result.value = null;
    error.value = '';
    if (index !== previousIndex) page.value = 0;
    if (expanded.value) void load(page.value);
  }
);
onBeforeUnmount(() => request?.abort());
</script>

<template>
  <div class="file-failures" :class="{ 'file-failures--information': outcome === 'SKIPPED' }">
    <Button
      :label="`${expanded ? 'Hide' : 'Show'} ${count} ${outcomeLabel} ${count === 1 ? 'file' : 'files'}`"
      :icon="expanded ? 'pi pi-chevron-up' : 'pi pi-chevron-down'"
      severity="secondary"
      size="small"
      :aria-expanded="expanded"
      :aria-controls="contentId"
      @click="toggle"
    />
    <div v-if="expanded" :id="contentId" class="file-failures__content" :aria-busy="loading">
      <p v-if="loading" role="status">Loading files…</p>
      <template v-else-if="error">
        <p role="alert">{{ error }}</p>
        <Button label="Try again" size="small" severity="secondary" @click="load(page)" />
      </template>
      <template v-else-if="result">
        <ul
          v-if="result.files?.length"
          class="file-failures__list"
          tabindex="0"
          :aria-label="`${index} ${outcomeLabel} files`"
        >
          <li v-for="file in result.files" :key="file.id">
            <strong>{{ file.name }}</strong>
            <span class="file-failures__folder">Folder: {{ file.path || '(root)' }}</span>
            <span>{{ file.message?.trim() || 'No error message was saved for this file.' }}</span>
            <RouterLink
              v-if="index === 'GPS' && file.id != null"
              :to="{ name: 'review', params: { sourceId: file.id } }"
            >
              Open file diagnosis
            </RouterLink>
            <RouterLink v-else :to="{ name: 'review', query: { source: file.id } }">Review file issues</RouterLink>
          </li>
        </ul>
        <p v-else>No {{ outcomeLabel }} files remain on this page. Refresh to update the counts.</p>
        <div v-if="page > 0 || hasNextPage" class="file-failures__pages">
          <Button label="Previous" size="small" severity="secondary" :disabled="page === 0" @click="load(page - 1)" />
          <span>Page {{ page + 1 }} · {{ result.total }} {{ outcomeLabel }} files</span>
          <Button label="Next" size="small" severity="secondary" :disabled="!hasNextPage" @click="load(page + 1)" />
        </div>
        <p v-if="result.files?.length && outcome !== 'SKIPPED'">
          Check the source files and the errors above. After correcting them,
          <RouterLink :to="{ name: 'admin', params: { section: 'maintenance' } }"
            >rescan {{ index === 'GPS' ? 'GPS' : 'media' }} files in Maintenance</RouterLink
          >.
        </p>
      </template>
    </div>
  </div>
</template>

<style scoped>
.file-failures__content {
  display: grid;
  gap: 0.75rem;
  margin-top: 0.75rem;
  font-size: var(--text-sm-size);
  color: var(--text-secondary);
}
.file-failures__content p {
  margin: 0;
}
.file-failures__content a {
  color: var(--accent-text);
}
.file-failures__list {
  display: grid;
  gap: 0.75rem;
  list-style: none;
  margin: 0;
  padding: 0;
  max-height: 22rem;
  overflow: auto;
}
.file-failures__list:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}
.file-failures__list li {
  display: grid;
  gap: 0.25rem;
  padding-left: 0.75rem;
  border-left: 2px solid var(--warning);
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}
.file-failures--information .file-failures__list li {
  border-left-color: var(--border-subtle);
}
.file-failures__list strong {
  color: var(--text-primary);
}
.file-failures__folder {
  color: var(--text-muted);
}
.file-failures__pages {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
}
</style>
