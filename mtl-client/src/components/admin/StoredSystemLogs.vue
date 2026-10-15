<template>
  <div class="stored-logs">
    <form class="log-filters" @submit.prevent="refresh">
      <label
        >Source<select v-model="filters.source">
          <option value="">All</option>
          <option>CLIENT</option>
          <option>SERVER</option>
        </select></label
      >
      <label
        >Severity<select v-model="filters.severity">
          <option value="">All</option>
          <option>DEBUG</option>
          <option>INFO</option>
          <option>WARN</option>
          <option>ERROR</option>
        </select></label
      >
      <label v-for="topic in topicKeys" :key="topic"
        >{{ topicLabels[topic] }}<input v-model="filters[topic]" :maxlength="TOPIC_LENGTH"
      /></label>
      <label>From<input v-model="filters.from" type="datetime-local" /></label>
      <label>To<input v-model="filters.to" type="datetime-local" /></label>
      <Button type="submit" label="Refresh" icon="pi pi-refresh" size="small" :loading="loading" :disabled="loading" />
    </form>
    <p v-if="disabled" role="status">Stored log viewer is disabled for this instance.</p>
    <p v-else-if="error" role="alert">{{ error }}</p>
    <p v-else-if="loading" role="status">Loading stored events…</p>
    <template v-else>
      <p v-if="!result?.items?.length">No stored events match these filters.</p>
      <ul v-else class="event-list" aria-label="Stored events">
        <li v-for="event in result.items" :key="event.id">
          <button class="event-row" type="button" @click="openEvent(event.id)">
            <span class="event-meta"
              >{{ formatDate(event.occurredAt ?? event.createDate) }} · {{ event.source ?? 'Unspecified' }} ·
              {{ event.severity ?? 'Unspecified' }}</span
            >
            <span>{{ [event.topic1, event.topic2, event.topic3].filter(Boolean).join(' / ') || 'No topics' }}</span>
            <strong>{{ event.message }}</strong>
            <span class="event-meta">Build: {{ event.buildId || 'Unspecified' }}</span>
          </button>
        </li>
      </ul>
      <nav class="log-pagination" aria-label="Stored event pages">
        <Button label="Previous" size="small" severity="secondary" :disabled="page === 0" @click="load(page - 1)" />
        <span
          >Page {{ page + 1 }} of {{ Math.max(1, result?.totalPages ?? 0) }} ·
          {{ result?.totalElements ?? 0 }} events</span
        >
        <Button
          label="Next"
          size="small"
          severity="secondary"
          :disabled="page + 1 >= (result?.totalPages ?? 0)"
          @click="load(page + 1)"
        />
      </nav>
    </template>
    <BottomSheet
      v-model="detailsOpen"
      title="Stored event"
      icon="bi bi-journal-text"
      :no-backdrop="false"
      :detents="detailDetents"
      initial-detent="expanded"
    >
      <p v-if="detailLoading" role="status">Loading event…</p>
      <div v-else-if="detailError" role="alert">
        <p>{{ detailError }}</p>
        <Button label="Retry" size="small" @click="openEvent(selectedId)" />
      </div>
      <div v-else-if="detail" class="event-details">
        <p>
          {{ formatDate(detail.event?.occurredAt ?? detail.event?.createDate) }} ·
          {{ detail.event?.source ?? 'Unspecified' }} ·
          {{ detail.event?.severity ?? 'Unspecified' }}
        </p>
        <p>
          {{
            [detail.event?.topic1, detail.event?.topic2, detail.event?.topic3].filter(Boolean).join(' / ') ||
            'No topics'
          }}
        </p>
        <h3>{{ detail.event?.message }}</h3>
        <p>Build: {{ detail.event?.buildId || 'Unspecified' }}</p>
        <p>Received: {{ formatDate(detail.event?.createDate) }}</p>
        <p v-if="(detail.event?.occurrenceCount ?? 1) > 1">
          Occurrences: {{ detail.event?.occurrenceCount }} · Last occurred:
          {{ formatDate(detail.event?.lastOccurredAt) }}
        </p>
        <p v-if="detail.originalClientLoginRef">Original login reference: {{ detail.originalClientLoginRef }}</p>
        <p>Login reference: {{ detail.clientLoginRef || 'Unspecified' }}</p>
        <h4>Stack trace</h4>
        <pre>{{ detail.stackTrace ?? 'No stack trace' }}</pre>
        <h4>Detail</h4>
        <pre>{{ detail.detail ?? 'No detail' }}</pre>
        <h4>Context</h4>
        <pre>{{ detail.context == null ? 'No context' : JSON.stringify(detail.context, null, 2) }}</pre>
      </div>
    </BottomSheet>
  </div>
</template>

<script setup lang="ts">
import { warnRequestFailure } from '@/utils/safeLogging';
import { onBeforeUnmount, reactive, ref, watch } from 'vue';
import Button from 'primevue/button';
import {
  SystemLogControllerApi,
  ResponseError,
  type SystemLogPage,
  type SystemLogDetail,
  type SystemLogSummary,
} from 'x8ing-mtl-api-typescript-fetch';
import BottomSheet from '@/components/ui/BottomSheet.vue';
import { getApiConfiguration } from '@/utils/openApiClient';

const props = defineProps<{ active: boolean }>();
const PAGE_SIZE = 50;
const TOPIC_LENGTH = 255;
const topicKeys = ['topic1', 'topic2', 'topic3'] as const;
const topicLabels = { topic1: 'Feature / topic 1', topic2: 'Component / topic 2', topic3: 'Operation / topic 3' };
const detailDetents = [{ id: 'expanded', height: 0.9 }];
const filters = reactive({
  source: '' as NonNullable<SystemLogSummary['source']> | '',
  severity: '' as NonNullable<SystemLogSummary['severity']> | '',
  topic1: '',
  topic2: '',
  topic3: '',
  from: '',
  to: '',
});
let appliedFilters = { ...filters };
const result = ref<SystemLogPage>();
const page = ref(0);
const loading = ref(false);
const disabled = ref(false);
const error = ref('');
const detailsOpen = ref(false);
const detail = ref<SystemLogDetail>();
const detailLoading = ref(false);
const detailError = ref('');
const selectedId = ref<number>();
let listRevision = 0;
let detailRevision = 0;

function formatDate(value?: Date | null): string {
  return value ? value.toLocaleString() : 'Unspecified date';
}

async function load(nextPage = 0): Promise<void> {
  const revision = ++listRevision;
  loading.value = true;
  error.value = '';
  disabled.value = false;
  try {
    const response = await new SystemLogControllerApi(getApiConfiguration()).listSystemLogs({
      page: nextPage,
      size: PAGE_SIZE,
      source: appliedFilters.source || undefined,
      severity: appliedFilters.severity || undefined,
      topic1: appliedFilters.topic1 || undefined,
      topic2: appliedFilters.topic2 || undefined,
      topic3: appliedFilters.topic3 || undefined,
      from: appliedFilters.from ? new Date(appliedFilters.from) : undefined,
      to: appliedFilters.to ? new Date(appliedFilters.to) : undefined,
    });
    if (revision !== listRevision) return;
    result.value = response;
    page.value = nextPage;
  } catch (failure) {
    if (revision !== listRevision) return;
    disabled.value = failure instanceof ResponseError && failure.response.status === 403;
    if (!disabled.value) {
      error.value = 'Could not load stored events. Check the filters and refresh.';
      if (!(failure instanceof ResponseError) || failure.response.status >= 500)
        warnRequestFailure('Stored diagnostic list could not be loaded', failure, {
          topic1: 'admin',
          topic2: 'StoredSystemLogs',
          topic3: 'list',
        });
    }
  } finally {
    if (revision === listRevision) loading.value = false;
  }
}

function refresh(): void {
  appliedFilters = { ...filters };
  void load(0);
}

async function openEvent(id?: number): Promise<void> {
  if (id == null) return;
  selectedId.value = id;
  detailsOpen.value = true;
  detail.value = undefined;
  detailError.value = '';
  detailLoading.value = true;
  const revision = ++detailRevision;
  try {
    const response = await new SystemLogControllerApi(getApiConfiguration()).getSystemLog({ id });
    if (revision === detailRevision) detail.value = response;
  } catch (failure) {
    if (revision === detailRevision) {
      detailError.value =
        failure instanceof ResponseError && failure.response.status === 403
          ? 'Stored log viewer is disabled for this instance.'
          : 'Could not load this event.';
      if (!(failure instanceof ResponseError) || failure.response.status >= 500)
        warnRequestFailure('Stored diagnostic details could not be loaded', failure, {
          topic1: 'admin',
          topic2: 'StoredSystemLogs',
          topic3: 'detail',
        });
    }
  } finally {
    if (revision === detailRevision) detailLoading.value = false;
  }
}

watch(
  () => props.active,
  (active) => {
    if (active) refresh();
    else {
      ++listRevision;
      ++detailRevision;
      detailsOpen.value = false;
    }
  },
  { immediate: true }
);
watch(detailsOpen, (open) => {
  if (!open) ++detailRevision;
});
onBeforeUnmount(() => {
  ++listRevision;
  ++detailRevision;
});
</script>

<style scoped>
.log-filters {
  display: flex;
  flex-wrap: wrap;
  align-items: end;
  gap: 0.75rem;
  margin-bottom: 1rem;
}
.log-filters label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: var(--text-sm-size);
  flex: 1 1 9rem;
  min-width: 0;
}
.log-filters input,
.log-filters select {
  min-width: 0;
  width: 100%;
  padding: 0.5rem;
  border: 1px solid var(--p-content-border-color);
  border-radius: 0.35rem;
  background: var(--p-content-background);
  color: inherit;
}
.event-list {
  list-style: none;
  padding: 0;
  margin: 0;
}
.event-row {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  text-align: left;
  width: 100%;
  border: 0;
  border-bottom: 1px solid var(--p-content-border-color);
  padding: 0.8rem;
  background: transparent;
  color: inherit;
  cursor: pointer;
  overflow-wrap: anywhere;
}
.event-row:hover,
.event-row:focus-visible {
  background: var(--p-content-hover-background);
}
.event-meta {
  font-size: var(--text-sm-size);
  color: var(--p-text-muted-color);
}
.log-pagination {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  margin-top: 1rem;
}
.event-details {
  padding: 0.5rem;
  overflow-wrap: anywhere;
}
.event-details pre {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font-size: var(--text-sm-size);
}
</style>
