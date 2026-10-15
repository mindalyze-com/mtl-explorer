<template>
  <div class="gh-view">
    <header class="health-header gh-header">
      <div class="gh-header__copy">
        <h2 class="gh-header__title">{{ syntheticData ? 'Synthetic health data' : 'Google Health data' }}</h2>
        <p class="gh-header__subtitle">Health representations, records, and sources stored by MTL Explorer.</p>
      </div>
      <div class="admin-action-controls gh-header__actions">
        <span :class="['admin-status-pill', mirrorStatusClass]">
          <i :class="mirrorStatusIcon" aria-hidden="true" /> {{ mirrorStatusLabel }}
        </span>
        <RouterLink v-slot="{ navigate }" custom :to="GOOGLE_HEALTH_SETTINGS_ROUTE">
          <Button label="Manage" icon="bi bi-sliders" size="small" severity="secondary" @click="navigate" />
        </RouterLink>
      </div>
    </header>

    <p v-if="syntheticData" class="admin-message" role="status">
      Synthetic demo data. Tracks and daily trends share one fictional health timeline.
    </p>
    <GoogleHealthSyncNotice v-if="status" :status="status" :synthetic-data="syntheticData" show-coverage-hint />

    <div
      v-if="oauthMessage"
      :class="['admin-message', `admin-message--${oauthMessage.tone}`]"
      :role="oauthMessage.tone === 'error' ? 'alert' : 'status'"
    >
      <i :class="oauthMessage.tone === 'success' ? 'bi bi-check-circle' : 'bi bi-exclamation-circle'" />
      <span>{{ oauthMessage.text }}</span>
    </div>

    <div v-if="pageError" class="admin-message admin-message--error" role="alert">
      <i class="bi bi-exclamation-triangle" aria-hidden="true" />
      <span>{{ pageError }}</span>
      <Button label="Retry status" size="small" text @click="loadStatus(true)" />
    </div>

    <template v-if="showRecords || status">
      <section v-if="syntheticData && !showRecords" class="admin-card empty-placeholder">
        <strong>Preparing synthetic health data</strong>
        <span>View generation progress in Settings → Processing.</span>
      </section>
      <section
        v-else-if="status?.configurationState === 'DISABLED' && !showRecords"
        class="admin-card empty-placeholder"
      >
        <i class="bi bi-heart" aria-hidden="true" />
        <strong>Google Health is disabled</strong>
        <span>This optional connector is not enabled. The rest of MTL Explorer works normally.</span>
      </section>

      <section
        v-else-if="status?.configurationState === 'MISSING_CONFIGURATION' && !showRecords"
        class="admin-card empty-placeholder"
      >
        <i class="bi bi-sliders" aria-hidden="true" />
        <strong>Setup is not complete</strong>
        <span>The server needs Google OAuth credentials before it can connect.</span>
        <RouterLink v-slot="{ navigate }" custom :to="GOOGLE_HEALTH_SETTINGS_ROUTE">
          <Button label="Open setup" icon="bi bi-sliders" size="small" @click="navigate" />
        </RouterLink>
      </section>

      <section v-else-if="!showRecords" class="admin-card empty-placeholder">
        <i class="bi bi-link-45deg" aria-hidden="true" />
        <strong>Connect Google Health</strong>
        <span>Connect from Settings to create a read-only local mirror of your health data.</span>
        <RouterLink v-slot="{ navigate }" custom :to="GOOGLE_HEALTH_SETTINGS_ROUTE">
          <Button label="Open setup" icon="bi bi-link-45deg" size="small" @click="navigate" />
        </RouterLink>
      </section>

      <template v-else>
        <div v-if="mirrorNotice" class="admin-message admin-message--warning" role="status">
          <i class="bi bi-info-circle" aria-hidden="true" />
          <span>{{ mirrorNotice }}</span>
        </div>

        <section class="gh-metrics" aria-label="Health mirror summary">
          <article class="gh-metric" style="--tile-accent: var(--accent)">
            <span class="gh-metric__icon"><i class="bi bi-database" /></span>
            <div class="gh-metric__body">
              <small>Records</small>
              <strong>{{ summariesLoaded ? totalRecords.toLocaleString() : '—' }}</strong>
            </div>
          </article>
          <article class="gh-metric" style="--tile-accent: var(--chart-series-1)">
            <span class="gh-metric__icon"><i class="bi bi-grid" /></span>
            <div class="gh-metric__body">
              <small>Data types</small>
              <strong>{{ summariesLoaded ? distinctDataTypes : '—' }}</strong>
            </div>
          </article>
          <article class="gh-metric" style="--tile-accent: var(--chart-series-2)">
            <span class="gh-metric__icon"><i class="bi bi-smartwatch" /></span>
            <div class="gh-metric__body">
              <small>Sources</small>
              <strong>{{ sourcesLoaded ? sources.length : '—' }}</strong>
            </div>
          </article>
          <article v-if="!syntheticData" class="gh-metric gh-metric--sync" style="--tile-accent: var(--info)">
            <span class="gh-metric__icon"><i class="bi bi-arrow-repeat" /></span>
            <div class="gh-metric__body">
              <small>Last sync</small>
              <strong>{{ formatDate(lastSuccessfulSync, false) }}</strong>
            </div>
          </article>
        </section>

        <p v-if="status?.mirrorSnapshotAt" class="gh-filter-hint">
          Summary as of {{ formatDate(status.mirrorSnapshotAt, false) }}
        </p>

        <section class="admin-card" aria-labelledby="gh-coverage-heading">
          <div class="admin-card__header">
            <div>
              <h3 id="gh-coverage-heading">Stored data</h3>
              <p v-if="summariesLoaded">{{ summaries.length }} mirrored representations, ordered by record count.</p>
            </div>
            <div class="admin-action-controls">
              <Button
                :label="showCatalogue ? 'Hide data types' : 'Show data types'"
                :icon="showCatalogue ? 'pi pi-angle-up' : 'pi pi-angle-down'"
                size="small"
                severity="secondary"
                text
                :aria-expanded="showCatalogue"
                aria-controls="gh-coverage-content"
                @click="catalogueExpanded = !showCatalogue"
              />
              <Button
                label="Refresh"
                icon="pi pi-refresh"
                size="small"
                severity="secondary"
                :loading="loading"
                :disabled="loading"
                @click="loadPage"
              />
            </div>
          </div>

          <p v-if="summariesLoading" class="gh-filter-hint" role="status">Loading data summaries…</p>
          <div v-if="summariesError" class="admin-message admin-message--error" role="alert">
            <i class="bi bi-exclamation-triangle" aria-hidden="true" />
            <span>{{ summariesError }}</span>
            <Button label="Retry summaries" size="small" text @click="loadSummaries()" />
          </div>
          <div v-if="showCatalogue" id="gh-coverage-content">
            <template v-if="summaries.length">
              <div class="gh-table-wrap gh-coverage-table">
                <table>
                  <thead>
                    <tr>
                      <th>Type</th>
                      <th>Format</th>
                      <th class="gh-number">Records</th>
                      <th>Available range</th>
                      <th>Last sync</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="summary in visibleSummaries" :key="`${summary.dataType}:${summary.representation}`">
                      <td class="gh-primary-cell">{{ label(summary.dataType) }}</td>
                      <td>
                        <span class="selection-chip">{{ label(summary.representation) }}</span>
                      </td>
                      <td class="gh-number">{{ (summary.recordCount ?? 0).toLocaleString() }}</td>
                      <td>{{ formatHealthSummaryRange(summary) }}</td>
                      <td :class="{ 'gh-error-text': summary.lastError }">
                        {{ summary.lastError || formatDate(summary.lastSuccessAt, false) }}
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>

              <div class="gh-card-list gh-coverage-list">
                <article
                  v-for="summary in visibleSummaries"
                  :key="`mobile:${summary.dataType}:${summary.representation}`"
                  class="gh-data-card"
                >
                  <header>
                    <strong>{{ label(summary.dataType) }}</strong
                    ><span class="selection-chip">{{ label(summary.representation) }}</span>
                  </header>
                  <dl class="admin-info-list">
                    <div class="admin-info-row">
                      <dt>Records</dt>
                      <dd>{{ (summary.recordCount ?? 0).toLocaleString() }}</dd>
                    </div>
                    <div class="admin-info-row">
                      <dt>Range</dt>
                      <dd>{{ formatHealthSummaryRange(summary) }}</dd>
                    </div>
                    <div class="admin-info-row">
                      <dt>Last sync</dt>
                      <dd :class="{ 'gh-error-text': summary.lastError }">
                        {{ summary.lastError || formatDate(summary.lastSuccessAt, false) }}
                      </dd>
                    </div>
                  </dl>
                </article>
              </div>

              <div v-if="orderedSummaries.length > coveragePreviewLimit" class="gh-more-action">
                <Button
                  :label="coverageExpanded ? 'Show fewer' : `Show all ${orderedSummaries.length}`"
                  :icon="coverageExpanded ? 'pi pi-angle-up' : 'pi pi-angle-down'"
                  size="small"
                  severity="secondary"
                  text
                  @click="coverageExpanded = !coverageExpanded"
                />
              </div>
            </template>
            <div v-else-if="summariesLoaded && !summariesLoading && !summariesError" class="inline-empty-state">
              <i class="bi bi-inbox" aria-hidden="true" />
              <span>The mirror is connected but does not contain records yet. Start a sync from Settings.</span>
            </div>
          </div>
        </section>

        <section class="admin-card" aria-labelledby="gh-records-heading">
          <div class="admin-card__header gh-records-header">
            <div>
              <h3 id="gh-records-heading">Recent records</h3>
              <p>{{ recordCountLabel }}</p>
            </div>
            <div class="admin-action-controls">
              <span
                v-if="recordsLoading && records.length"
                class="admin-status-pill admin-status-pill--loading"
                aria-live="polite"
              >
                <i class="pi pi-spin pi-spinner" /> Updating
              </span>
              <Button
                class="gh-filter-toggle"
                label="Filters"
                icon="bi bi-funnel"
                size="small"
                severity="secondary"
                :badge="activeFilterCount ? String(activeFilterCount) : undefined"
                :aria-expanded="filtersExpanded"
                aria-controls="gh-record-filters"
                @click="filtersExpanded = !filtersExpanded"
              />
            </div>
          </div>

          <p v-if="sourcesLoading" class="gh-filter-hint" role="status">Loading sources…</p>
          <div v-if="sourcesError" class="admin-message admin-message--error" role="alert">
            <i class="bi bi-exclamation-triangle" aria-hidden="true" />
            <span>{{ sourcesError }}</span>
            <Button label="Retry sources" size="small" text @click="loadSources()" />
          </div>
          <form
            id="gh-record-filters"
            :class="['gh-filters', { 'gh-filters--expanded': filtersExpanded }]"
            @submit.prevent="applyFilters"
          >
            <div class="admin-form-row">
              <label class="admin-field" for="gh-data-type">
                <span id="gh-data-type-label">Data type</span>
                <Select
                  v-model="filters.dataType"
                  input-id="gh-data-type"
                  aria-labelledby="gh-data-type-label"
                  placeholder="All types"
                  :options="dataTypeOptions"
                  :loading="summariesLoading && !summariesLoaded"
                  :disabled="!summariesLoaded"
                  option-label="label"
                  option-value="value"
                  fluid
                />
              </label>
              <label class="admin-field" for="gh-representation">
                <span id="gh-representation-label">Representation</span>
                <Select
                  v-model="filters.representation"
                  input-id="gh-representation"
                  aria-labelledby="gh-representation-label"
                  placeholder="All representations"
                  :options="representationOptions"
                  :loading="summariesLoading && !summariesLoaded"
                  :disabled="!summariesLoaded"
                  option-label="label"
                  option-value="value"
                  fluid
                />
              </label>
              <label class="admin-field admin-field--small" for="gh-start">
                <span>From</span>
                <InputText id="gh-start" v-model="filters.start" type="date" :max="filters.end || undefined" />
              </label>
              <label class="admin-field admin-field--small" for="gh-end">
                <span>To</span>
                <InputText id="gh-end" v-model="filters.end" type="date" :min="filters.start || undefined" />
              </label>
              <label class="admin-field" for="gh-source">
                <span id="gh-source-label">Source</span>
                <Select
                  v-model="filters.dataSourceId"
                  input-id="gh-source"
                  aria-labelledby="gh-source-label"
                  placeholder="All sources"
                  :options="sourceOptions"
                  :loading="sourcesLoading && !sourcesLoaded"
                  :disabled="!sourcesLoaded"
                  option-label="label"
                  option-value="value"
                  fluid
                />
              </label>
              <div class="admin-action-controls gh-filter-actions">
                <Button label="Apply" icon="pi pi-check" size="small" type="submit" :loading="recordsLoading" />
                <Button label="Reset" size="small" severity="secondary" text type="button" @click="resetFilters" />
              </div>
            </div>
          </form>

          <p v-if="filtersDirty" class="gh-filter-hint" role="status">Filter changes are not applied yet.</p>
          <div v-if="recordsRefreshAvailable" class="admin-message" role="status">
            <i class="bi bi-arrow-repeat" aria-hidden="true" />
            <span>A sync completed. Refresh to view the latest records.</span>
            <Button
              label="Refresh records"
              size="small"
              severity="secondary"
              :disabled="recordsLoading"
              @click="loadRecords(false)"
            />
          </div>

          <div v-if="recordsError" class="admin-message admin-message--error" role="alert">
            <i class="bi bi-exclamation-triangle" aria-hidden="true" />
            <span>{{ recordsError }}</span>
            <Button label="Retry records" size="small" text @click="loadRecords(retryAppend)" />
          </div>

          <div v-if="recordsLoading && records.length === 0" class="inline-empty-state" aria-live="polite">
            <i class="pi pi-spin pi-spinner" aria-hidden="true" /><span>Loading records…</span>
          </div>

          <template v-else-if="records.length">
            <div class="gh-table-wrap gh-records-desktop">
              <table class="gh-records-table">
                <thead>
                  <tr>
                    <th>Date / time</th>
                    <th>Type</th>
                    <th>Value</th>
                    <th>Source</th>
                    <th>Track</th>
                    <th><span class="gh-visually-hidden">Actions</span></th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="record in records" :key="record.id">
                    <td>
                      <time :datetime="healthRecordDateTimeValue(record)">{{ formatHealthRecordTime(record) }}</time>
                    </td>
                    <td>
                      <span class="gh-primary-cell">{{ label(record.dataType) }}</span>
                      <span class="selection-chip">{{ label(record.representation) }}</span>
                    </td>
                    <td class="gh-record-value">{{ record.valueSummary || '—' }}</td>
                    <td>{{ record.sourceName || record.sourcePlatform || 'Unknown' }}</td>
                    <td>
                      <RouterLink
                        v-if="record.canonicalTrackId"
                        :to="{ name: 'track-detail', params: { id: record.canonicalTrackId } }"
                        >#{{ record.canonicalTrackId }}</RouterLink
                      >
                      <span v-else>{{ routeStateLabel(record.routeState) }}</span>
                    </td>
                    <td>
                      <Button
                        label="Details"
                        icon="pi pi-angle-right"
                        icon-pos="right"
                        size="small"
                        text
                        @click="openRecord(record.id)"
                      />
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>

            <div class="gh-card-list gh-record-list">
              <article v-for="record in records" :key="`mobile:${record.id}`" class="gh-data-card gh-record-card">
                <header>
                  <span
                    ><strong>{{ label(record.dataType) }}</strong
                    ><time :datetime="healthRecordDateTimeValue(record)">{{
                      formatHealthRecordTime(record)
                    }}</time></span
                  >
                  <strong class="gh-record-value">{{ record.valueSummary || '—' }}</strong>
                </header>
                <div class="gh-record-meta">
                  <span class="selection-chip">{{ label(record.representation) }}</span>
                  <span
                    ><i class="bi bi-smartwatch" /> {{ record.sourceName || record.sourcePlatform || 'Unknown' }}</span
                  >
                  <RouterLink
                    v-if="record.canonicalTrackId"
                    :to="{ name: 'track-detail', params: { id: record.canonicalTrackId } }"
                    >Track #{{ record.canonicalTrackId }}</RouterLink
                  >
                  <span v-else-if="record.routeState">{{ routeStateLabel(record.routeState) }}</span>
                </div>
                <div class="gh-card-action">
                  <Button
                    label="View details"
                    icon="pi pi-angle-right"
                    icon-pos="right"
                    size="small"
                    text
                    @click="openRecord(record.id)"
                  />
                </div>
              </article>
            </div>
          </template>

          <div v-else-if="!recordsError" class="inline-empty-state">
            <i class="bi bi-search" aria-hidden="true" /><span>No records match these filters.</span>
          </div>

          <div v-if="nextCursor" class="gh-more-action">
            <Button
              :label="recordsLoading ? 'Loading…' : 'Load more'"
              icon="pi pi-angle-down"
              size="small"
              severity="secondary"
              :disabled="recordsLoading"
              @click="loadRecords(true)"
            />
          </div>
        </section>
      </template>
    </template>

    <BottomSheet
      v-model="detailOpen"
      title="Record details"
      icon="bi bi-file-earmark-medical"
      :detents="[
        { id: 'comfortable', height: 'min(620px, 78vh)' },
        { id: 'large', height: '92vh' },
      ]"
      initial-detent="comfortable"
      desktop-width="compact"
      :no-backdrop="false"
    >
      <div class="gh-detail">
        <div v-if="detailLoading" class="empty-placeholder" aria-live="polite">
          <i class="pi pi-spin pi-spinner" /><strong>Loading record</strong>
        </div>
        <template v-else-if="selectedRecord">
          <header class="admin-page-header">
            <span class="admin-page-header__icon" aria-hidden="true"><i class="bi bi-activity" /></span>
            <div>
              <h2>{{ label(selectedRecord.dataType) }}</h2>
              <p>
                {{ label(selectedRecord.representation) }} · {{ selectedRecord.valueSummary || 'No value summary' }}
              </p>
            </div>
          </header>
          <dl class="admin-info-list">
            <div class="admin-info-row">
              <dt>{{ healthRecordTimeLabel(selectedRecord) }}</dt>
              <dd>{{ formatHealthRecordTime(selectedRecord) }}</dd>
            </div>
            <div v-if="selectedRecord.originalTime" class="admin-info-row">
              <dt>Original time</dt>
              <dd>{{ selectedRecord.originalTime }}</dd>
            </div>
            <div class="admin-info-row">
              <dt>Source</dt>
              <dd>{{ selectedRecord.sourceName || selectedRecord.sourcePlatform || 'Unknown' }}</dd>
            </div>
            <div v-if="selectedRecord.canonicalTrackId" class="admin-info-row">
              <dt>MTL track</dt>
              <dd>
                <RouterLink :to="{ name: 'track-detail', params: { id: selectedRecord.canonicalTrackId } }"
                  >Open track #{{ selectedRecord.canonicalTrackId }}</RouterLink
                >
              </dd>
            </div>
            <div v-if="selectedRecord.routeState" class="admin-info-row">
              <dt>Route</dt>
              <dd>{{ routeStateLabel(selectedRecord.routeState) }}</dd>
            </div>
            <div v-if="selectedRecord.routeError" class="admin-info-row">
              <dt>Route error</dt>
              <dd class="gh-error-text">{{ selectedRecord.routeError }}</dd>
            </div>
            <div class="admin-info-row">
              <dt>Record ID</dt>
              <dd>
                <code>{{ selectedRecord.id }}</code>
              </dd>
            </div>
          </dl>
          <details class="admin-disclosure">
            <summary>Stored payload</summary>
            <div class="admin-disclosure__body">
              <pre class="admin-command-output">{{ JSON.stringify(selectedRecord.payload ?? {}, null, 2) }}</pre>
            </div>
          </details>
        </template>
        <div v-else class="admin-message admin-message--error" role="alert">
          <i class="bi bi-exclamation-triangle" /><span>{{ detailError }}</span>
        </div>
      </div>
    </BottomSheet>
  </div>
</template>

<script setup lang="ts">
import { storeToRefs } from 'pinia';
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue';
import { RouterLink, useRoute } from 'vue-router';
import Button from 'primevue/button';
import InputText from 'primevue/inputtext';
import Select from 'primevue/select';
import BottomSheet from '@/components/ui/BottomSheet.vue';
import '@/components/admin/adminCenter.css';
import { useMediaQuery } from '@/composables/useMediaQuery';
import {
  APPLICATION_STATUS_SECTION,
  useApplicationStatusStore,
  type ApplicationStatusPollingConsumer,
} from '@/stores/applicationStatusStore';
import {
  googleHealthApi,
  type GHDataSourceSummary,
  type GHRecordResponse,
  type GHTypeSummary,
} from '../../api/googleHealthApi';
import GoogleHealthSyncNotice from '../../components/GoogleHealthSyncNotice.vue';
import { GOOGLE_HEALTH_SETTINGS_ROUTE } from '../../utils/googleHealthNavigation';
import { googleHealthStatusPresentation } from '../../utils/googleHealthPresentation';
import { STATUS_POLL_INTERVAL_MS } from '@/utils/statusPolling';
import {
  formatHealthDateTime,
  formatHealthRecordTime,
  formatHealthSummaryRange,
  healthRecordDateTimeValue,
  healthRecordTimeLabel,
} from '../../utils/healthDateTime';

const RECORD_PAGE_SIZE = 50;
const COVERAGE_PREVIEW_LIMIT = 8;
const MOBILE_COVERAGE_PREVIEW_LIMIT = 4;
const MOBILE_BREAKPOINT_PX = 768;
const props = withDefaults(defineProps<{ active?: boolean; syntheticData?: boolean }>(), {
  active: true,
  syntheticData: false,
});
const route = useRoute();
const isMobile = useMediaQuery(`(max-width: ${MOBILE_BREAKPOINT_PX}px)`);
const applicationStatusStore = useApplicationStatusStore();
const { googleHealth: status } = storeToRefs(applicationStatusStore);
const loading = ref(false);
const pageError = ref('');
const statusLoading = ref(false);
const summariesLoading = ref(false);
const summariesLoaded = ref(false);
const summariesError = ref('');
const sourcesLoading = ref(false);
const sourcesLoaded = ref(false);
const sourcesError = ref('');
const recordsError = ref('');
const retryAppend = ref(false);
const summaries = ref<GHTypeSummary[]>([]);
const sources = ref<GHDataSourceSummary[]>([]);
const records = ref<GHRecordResponse[]>([]);
const nextCursor = ref<string>();
const recordsLoading = ref(false);
const coverageExpanded = ref(false);
const catalogueExpanded = ref<boolean>();
const showCatalogue = computed(() => catalogueExpanded.value ?? !isMobile.value);
const filtersExpanded = ref(false);
const selectedRecordId = ref('');
const selectedRecord = ref<GHRecordResponse | null>(null);
const detailLoading = ref(false);
const detailError = ref('');
const emptyFilters = () => ({ dataType: '', representation: '', start: '', end: '', dataSourceId: '' });
const filters = reactive(emptyFilters());
const appliedFilters = ref(emptyFilters());
const appliedTimeZone = ref(Intl.DateTimeFormat().resolvedOptions().timeZone);
const recordsRefreshAvailable = ref(false);
let recordsRequestId = 0;
let catalogueRequestId = 0;
let statusRequestId = 0;
let summariesRequestId = 0;
let sourcesRequestId = 0;
let detailRequestId = 0;
let loadedPages = 0;
let syncRefreshGeneration = 0;
let disposed = false;
let statusPollingConsumer: ApplicationStatusPollingConsumer | null = null;
let postSyncRefresh: Promise<void> | null = null;

const detailOpen = computed({ get: () => Boolean(selectedRecordId.value), set: (open) => !open && closeRecord() });
const oauthMessage = computed(() => {
  if (route.query.googleHealth === 'connected')
    return { tone: 'success', text: 'Google Health is connected. The first sync has started.' };
  if (route.query.googleHealth === 'error')
    return { tone: 'error', text: 'Google authorization did not complete. Check the setup and try again.' };
  return null;
});
const totalRecords = computed(() => summaries.value.reduce((total, item) => total + (item.recordCount ?? 0), 0));
const hasLocalMirror = computed(
  () => records.value.length > 0 || summaries.value.some((item) => (item.recordCount ?? 0) > 0)
);
const showRecords = computed(
  () =>
    hasLocalMirror.value ||
    status.value?.connectionState === 'CONNECTED' ||
    Boolean(recordsError.value) ||
    (props.active && (!status.value || !summariesLoaded.value || recordsLoading.value))
);
const distinctDataTypes = computed(() => new Set(summaries.value.map((item) => item.dataType).filter(Boolean)).size);
const orderedSummaries = computed(() =>
  [...summaries.value].sort(
    (left, right) =>
      (right.recordCount ?? 0) - (left.recordCount ?? 0) || label(left.dataType).localeCompare(label(right.dataType))
  )
);
const coveragePreviewLimit = computed(() => (isMobile.value ? MOBILE_COVERAGE_PREVIEW_LIMIT : COVERAGE_PREVIEW_LIMIT));
const visibleSummaries = computed(() =>
  coverageExpanded.value ? orderedSummaries.value : orderedSummaries.value.slice(0, coveragePreviewLimit.value)
);
const dataTypes = computed(() =>
  Array.from(
    new Set(summaries.value.map((item) => item.dataType).filter((value): value is string => Boolean(value)))
  ).sort((left, right) => label(left).localeCompare(label(right)))
);
const representations = computed(() =>
  Array.from(
    new Set(
      summaries.value
        .filter((item) => !filters.dataType || item.dataType === filters.dataType)
        .map((item) => item.representation)
        .filter((value): value is string => Boolean(value))
    )
  ).sort((left, right) => label(left).localeCompare(label(right)))
);
const dataTypeOptions = computed(() => [
  { label: 'All types', value: '' },
  ...dataTypes.value.map((value) => ({ label: label(value), value })),
]);
const representationOptions = computed(() => [
  { label: 'All representations', value: '' },
  ...representations.value.map((value) => ({ label: label(value), value })),
]);
const sourceOptions = computed(() => [
  { label: 'All sources', value: '' },
  ...sources.value.map((source) => ({ label: sourceLabel(source), value: String(source.id) })),
]);
const lastSuccessfulSync = computed(() => {
  const dates = (status.value?.syncStates ?? [])
    .map((item) => item.lastSuccessAt)
    .filter((value): value is Date => value instanceof Date);
  return dates.length ? new Date(Math.max(...dates.map((date) => date.getTime()))) : undefined;
});
const activeFilterCount = computed(() => Object.values(appliedFilters.value).filter(Boolean).length);
const filtersDirty = computed(() => JSON.stringify(filters) !== JSON.stringify(appliedFilters.value));
const recordCountLabel = computed(() => {
  const filterLabel = activeFilterCount.value
    ? ` · ${activeFilterCount.value} applied ${activeFilterCount.value === 1 ? 'filter' : 'filters'}`
    : '';
  if (recordsLoading.value && !records.value.length) return `Loading the newest matching records.${filterLabel}`;
  if (!records.value.length)
    return activeFilterCount.value ? `No matching records.${filterLabel}` : 'No records stored yet.';
  return `${records.value.length.toLocaleString()} ${records.value.length === 1 ? 'record' : 'records'}${nextCursor.value ? ' loaded so far' : ''}${filterLabel}`;
});

const mirrorStatusPresentation = computed(() =>
  googleHealthStatusPresentation(status.value, { loading: statusLoading.value, syntheticData: props.syntheticData })
);
const mirrorStatusLabel = computed(() => mirrorStatusPresentation.value.label);
const mirrorStatusClass = computed(() => mirrorStatusPresentation.value.className);
const mirrorStatusIcon = computed(() => mirrorStatusPresentation.value.icon);
const mirrorNotice = computed(() => {
  if (props.syntheticData || !status.value) return '';
  if (status.value?.configurationState === 'DISABLED')
    return 'Google Health is disabled. Stored local records remain available and synchronization is stopped.';
  if (status.value?.configurationState === 'MISSING_CONFIGURATION')
    return 'Google Health setup is incomplete. Stored local records remain available, but synchronization is stopped.';
  if (status.value?.connectionState !== 'CONNECTED')
    return 'Google Health is disconnected. Stored local records remain available and synchronization is stopped.';
  return '';
});

watch(
  () => props.active,
  (active) => {
    if (active) void loadPage();
    else invalidateRequests();
  },
  { immediate: true }
);
watch(() => props.active, updateStatusPolling, { immediate: true });
watch(
  () => [status.value?.job?.id, status.value?.job?.state] as const,
  ([jobId, state], previous) => {
    const [previousJobId, previousState] = previous ?? [];
    if (
      props.active &&
      jobId &&
      previousJobId &&
      ['QUEUED', 'RUNNING'].includes(previousState ?? '') &&
      (jobId !== previousJobId || !['QUEUED', 'RUNNING'].includes(state ?? ''))
    ) {
      syncRefreshGeneration++;
      void refreshAfterSync();
    }
  }
);
watch(
  () => filters.dataType,
  () => {
    if (filters.representation && !representations.value.includes(filters.representation)) filters.representation = '';
  }
);

async function loadPage() {
  const requestId = catalogueRequestId;
  loading.value = true;
  try {
    // Record paging does not depend on full-history summary counts.
    await Promise.all([loadRecords(false), loadStatus(), loadSummaries(), loadSources()]);
  } finally {
    if (ownsCatalogueRequest(requestId)) loading.value = false;
  }
}

async function loadStatus(force = false) {
  const requestId = ++statusRequestId;
  const catalogueId = catalogueRequestId;
  const ownsRequest = () => ownsCatalogueRequest(catalogueId) && requestId === statusRequestId;
  statusLoading.value = true;
  pageError.value = '';
  try {
    if (force) await applicationStatusStore.force(APPLICATION_STATUS_SECTION.GOOGLE_HEALTH);
    else if (!status.value) await applicationStatusStore.refresh([APPLICATION_STATUS_SECTION.GOOGLE_HEALTH]);
    if (ownsRequest())
      pageError.value = applicationStatusStore.sectionStates[APPLICATION_STATUS_SECTION.GOOGLE_HEALTH].lastError ?? '';
  } catch (loadError) {
    if (ownsRequest())
      pageError.value = loadError instanceof Error ? loadError.message : 'Could not load Google Health status.';
  } finally {
    if (ownsRequest()) statusLoading.value = false;
  }
}

async function loadSummaries() {
  const requestId = ++summariesRequestId;
  const catalogueId = catalogueRequestId;
  const ownsRequest = () => ownsCatalogueRequest(catalogueId) && requestId === summariesRequestId;
  summariesLoading.value = true;
  summariesError.value = '';
  try {
    const response = await googleHealthApi.summaries();
    if (ownsRequest()) {
      summaries.value = response;
      summariesLoaded.value = true;
    }
  } catch (loadError) {
    if (ownsRequest())
      summariesError.value = loadError instanceof Error ? loadError.message : 'Could not load data summaries.';
  } finally {
    if (ownsRequest()) summariesLoading.value = false;
  }
}

async function loadSources() {
  const requestId = ++sourcesRequestId;
  const catalogueId = catalogueRequestId;
  const ownsRequest = () => ownsCatalogueRequest(catalogueId) && requestId === sourcesRequestId;
  sourcesLoading.value = true;
  sourcesError.value = '';
  try {
    const response = await googleHealthApi.sources();
    if (ownsRequest()) {
      sources.value = response;
      sourcesLoaded.value = true;
    }
  } catch (loadError) {
    if (ownsRequest()) sourcesError.value = loadError instanceof Error ? loadError.message : 'Could not load sources.';
  } finally {
    if (ownsRequest()) sourcesLoading.value = false;
  }
}

function ownsCatalogueRequest(requestId: number): boolean {
  return !disposed && props.active && requestId === catalogueRequestId;
}

function updateStatusPolling(active: boolean) {
  if (!active) {
    statusPollingConsumer?.dispose();
    statusPollingConsumer = null;
    return;
  }
  const demand = {
    [APPLICATION_STATUS_SECTION.GOOGLE_HEALTH]: STATUS_POLL_INTERVAL_MS,
  };
  if (statusPollingConsumer) statusPollingConsumer.update(demand);
  else statusPollingConsumer = applicationStatusStore.registerPollingConsumer(demand);
}

function refreshAfterSync(): Promise<void> {
  if (postSyncRefresh) return postSyncRefresh;
  const requestId = catalogueRequestId;
  // Keep loaded pages and in-flight actions intact; refresh an idle first page immediately.
  const preserveRecords = loadedPages > 1 || recordsLoading.value;
  if (preserveRecords) recordsRefreshAvailable.value = true;
  const refresh = Promise.all([
    loadSummaries(),
    loadSources(),
    preserveRecords ? Promise.resolve() : loadRecords(false),
  ])
    .then(() => undefined)
    .finally(() => {
      if (postSyncRefresh === refresh) postSyncRefresh = null;
      if (ownsCatalogueRequest(requestId)) loading.value = false;
    });
  postSyncRefresh = refresh;
  return refresh;
}

async function loadRecords(append: boolean) {
  if (disposed || !props.active || (append && (recordsLoading.value || !nextCursor.value))) return;
  const requestId = ++recordsRequestId;
  const query = { ...appliedFilters.value };
  const cursor = append ? nextCursor.value : undefined;
  const syncGenerationAtStart = syncRefreshGeneration;
  recordsLoading.value = true;
  recordsError.value = '';
  const ownsRequest = () => !disposed && props.active && requestId === recordsRequestId;
  try {
    const response = await googleHealthApi.records({
      dataType: query.dataType || undefined,
      representation: query.representation || undefined,
      startDate: query.start || undefined,
      endDate: query.end || undefined,
      timeZone: appliedTimeZone.value,
      dataSourceId: query.dataSourceId ? Number(query.dataSourceId) : undefined,
      cursor,
      pageSize: RECORD_PAGE_SIZE,
    });
    if (!ownsRequest()) return;
    records.value = append ? [...records.value, ...(response.records ?? [])] : (response.records ?? []);
    nextCursor.value = response.nextCursor;
    loadedPages = append ? loadedPages + 1 : 1;
    if (!append) recordsRefreshAvailable.value = syncGenerationAtStart !== syncRefreshGeneration;
  } catch (loadError) {
    if (ownsRequest()) {
      retryAppend.value = append;
      recordsError.value = loadError instanceof Error ? loadError.message : 'Could not load Google Health records.';
    }
  } finally {
    if (ownsRequest()) recordsLoading.value = false;
  }
}

function applyFilters() {
  appliedFilters.value = { ...filters };
  appliedTimeZone.value = Intl.DateTimeFormat().resolvedOptions().timeZone;
  records.value = [];
  nextCursor.value = undefined;
  loadedPages = 0;
  recordsRefreshAvailable.value = false;
  filtersExpanded.value = false;
  void loadRecords(false);
}
function resetFilters() {
  Object.assign(filters, emptyFilters());
  applyFilters();
}
async function openRecord(recordId?: string) {
  if (!recordId || disposed || !props.active) return;
  const requestId = ++detailRequestId;
  selectedRecordId.value = recordId;
  selectedRecord.value = null;
  detailLoading.value = true;
  detailError.value = '';
  const ownsRequest = () => !disposed && props.active && requestId === detailRequestId;
  try {
    const record = await googleHealthApi.record(recordId);
    if (ownsRequest()) selectedRecord.value = record;
  } catch (loadError) {
    if (ownsRequest())
      detailError.value = loadError instanceof Error ? loadError.message : 'Could not load this record.';
  } finally {
    if (ownsRequest()) detailLoading.value = false;
  }
}
function closeRecord() {
  detailRequestId++;
  selectedRecordId.value = '';
  selectedRecord.value = null;
  detailError.value = '';
  detailLoading.value = false;
}
function invalidateRequests() {
  postSyncRefresh = null;
  statusLoading.value = false;
  catalogueRequestId++;
  recordsRequestId++;
  loading.value = false;
  summariesLoading.value = false;
  sourcesLoading.value = false;
  recordsLoading.value = false;
  closeRecord();
}
onBeforeUnmount(() => {
  disposed = true;
  invalidateRequests();
  statusPollingConsumer?.dispose();
});

function label(value?: string): string {
  if (!value) return '—';
  return value
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .replaceAll('-', ' ')
    .replaceAll('_', ' ')
    .replace(/\b\w/g, (part) => part.toUpperCase());
}
function formatDate(value?: Date, exact = true): string {
  return formatHealthDateTime(value, exact ? 'long' : 'short');
}

function sourceLabel(source: GHDataSourceSummary): string {
  return (
    [source.applicationName, source.deviceDisplayName, source.platform].filter(Boolean).join(' · ') ||
    `Source ${source.id}`
  );
}
function routeStateLabel(value?: string): string {
  return value ? label(value) : '—';
}
</script>

<style scoped>
.gh-filter-hint {
  margin: 0;
  color: var(--text-muted);
  font-size: var(--text-xs-size);
}

.gh-view {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
  width: 100%;
  max-width: 72rem;
  box-sizing: border-box;
  margin: 0 auto;
  padding: 0.35rem 1rem 1.4rem;
}
.gh-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding-bottom: 0.65rem;
  border-bottom: 1px solid var(--border-default);
}
.gh-header__copy {
  flex: 1 1 auto;
  min-width: 0;
}
.gh-header__title {
  margin: 0;
  color: var(--text-primary);
  font-size: var(--text-base-size);
  font-weight: 700;
  line-height: var(--text-base-lh);
}
.gh-header__subtitle {
  margin: 0.15rem 0 0;
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}
.gh-header__actions {
  margin-left: auto;
}
.gh-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0.75rem;
}
.gh-metric {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 0.75rem;
  padding: 0.85rem 0.9rem;
  border: 1px solid var(--border-default);
  border-radius: 8px;
  background: color-mix(in srgb, var(--surface-elevated) 50%, transparent);
  transition:
    background 0.15s ease,
    border-color 0.15s ease;
}
.gh-metric:hover {
  background: var(--surface-hover);
  border-color: var(--border-medium);
}
.gh-metric__icon {
  display: flex;
  width: 2.25rem;
  height: 2.25rem;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: color-mix(in srgb, var(--tile-accent, var(--accent)) 12%, transparent);
  color: var(--tile-accent, var(--accent-text));
  font-size: var(--text-base-size);
}
.gh-metric__body {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 0.08rem;
}
.gh-metric small {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  font-weight: 650;
  line-height: var(--text-xs-lh);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}
.gh-metric strong {
  overflow: hidden;
  color: var(--text-primary);
  font-size: var(--text-xl-size);
  line-height: var(--text-xl-lh);
  font-variant-numeric: tabular-nums;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.gh-metric--sync strong {
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
  white-space: normal;
  overflow-wrap: anywhere;
}
.gh-table-wrap {
  overflow: hidden;
  border: 1px solid var(--border-default);
  border-radius: 8px;
  background: color-mix(in srgb, var(--surface-sheet-solid) 90%, transparent);
}
.gh-table-wrap table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
}
.gh-table-wrap th,
.gh-table-wrap td {
  padding: 0.6rem 0.65rem;
  border-bottom: 1px solid var(--table-border);
  text-align: left;
  vertical-align: middle;
}
.gh-table-wrap th {
  background: var(--table-header-bg);
  color: var(--table-header-text);
  font-size: var(--text-2xs-size);
  line-height: var(--text-2xs-lh);
  font-weight: 700;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}
.gh-table-wrap td {
  color: var(--table-row-text);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}
.gh-table-wrap tbody tr:last-child td {
  border-bottom: 0;
}
.gh-table-wrap tbody tr:hover td {
  background: var(--table-row-hover);
}
.gh-coverage-table th:nth-child(1) {
  width: 21%;
}
.gh-coverage-table th:nth-child(2) {
  width: 17%;
}
.gh-coverage-table th:nth-child(3) {
  width: 11%;
}
.gh-coverage-table th:nth-child(4) {
  width: 25%;
}
.gh-coverage-table th:nth-child(5) {
  width: 26%;
}
.gh-number {
  text-align: right !important;
  font-variant-numeric: tabular-nums;
}
.gh-primary-cell,
.gh-record-value {
  color: var(--text-primary);
  font-weight: 650;
}
.gh-error-text {
  color: var(--error) !important;
}
.gh-more-action {
  display: flex;
  justify-content: center;
  padding-top: 0.65rem;
}
.gh-filters {
  margin-bottom: 0.8rem;
  padding: 0.75rem;
  border: 1px solid var(--border-default);
  border-radius: 8px;
  background: var(--surface-elevated);
}
.gh-filters label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}
.gh-filters label > span {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
  font-weight: 650;
}
.gh-filters :deep(.p-inputtext) {
  width: 100%;
}
.gh-filter-actions {
  align-self: flex-end;
}
.gh-filter-toggle {
  display: none;
}
.gh-records-desktop th:nth-child(1) {
  width: 20%;
}
.gh-records-desktop th:nth-child(2) {
  width: 23%;
}
.gh-records-desktop th:nth-child(3) {
  width: 12%;
}
.gh-records-desktop th:nth-child(4) {
  width: 17%;
}
.gh-records-desktop th:nth-child(5) {
  width: 11%;
}
.gh-records-desktop th:nth-child(6) {
  width: 17%;
}
.gh-records-desktop .selection-chip {
  margin-left: 0.2rem;
}
.gh-card-list {
  display: none;
}
.gh-detail {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
  padding: 0.25rem 1rem 1.25rem;
}
.gh-detail .admin-command-output {
  max-height: 20rem;
  user-select: text;
  -webkit-user-select: text;
}
.gh-detail code {
  user-select: text;
  -webkit-user-select: text;
}
.gh-visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

@media (max-width: 980px) {
  .gh-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .gh-filter-actions {
    flex: 1 1 100%;
    justify-content: flex-end;
  }
}

@media (max-width: 768px) {
  .gh-view {
    gap: 0.7rem;
    padding-inline: 0.7rem;
  }
  .gh-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.5rem;
  }
  .gh-header__copy p {
    display: none;
  }
  .gh-header__actions {
    width: 100%;
    justify-content: space-between;
    margin-left: 0;
  }
  .gh-coverage-table,
  .gh-records-desktop {
    display: none;
  }
  .gh-card-list {
    display: flex;
    flex-direction: column;
    gap: 0.55rem;
  }
  .gh-data-card {
    padding: 0.75rem;
    border: 1px solid var(--border-default);
    border-radius: 8px;
    background: color-mix(in srgb, var(--surface-elevated) 50%, transparent);
  }
  .gh-data-card > header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 0.6rem;
  }
  .gh-data-card > header > strong,
  .gh-data-card > header > span > strong {
    color: var(--text-primary);
    font-size: var(--text-sm-size);
    line-height: var(--text-sm-lh);
  }
  .gh-record-card > header > span {
    display: flex;
    min-width: 0;
    flex-direction: column;
    gap: 0.1rem;
  }
  .gh-record-card time {
    color: var(--text-muted);
    font-size: var(--text-xs-size);
    line-height: var(--text-xs-lh);
  }
  .gh-record-meta {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 0.35rem 0.65rem;
    margin-top: 0.55rem;
    color: var(--text-muted);
    font-size: var(--text-xs-size);
    line-height: var(--text-xs-lh);
  }
  .gh-card-action {
    display: flex;
    justify-content: flex-end;
    margin-top: 0.4rem;
    padding-top: 0.35rem;
    border-top: 1px solid var(--border-subtle);
  }
  .gh-filter-toggle {
    display: inline-flex;
  }
  .gh-filters {
    display: none;
  }
  .gh-filters--expanded {
    display: block;
  }
  .gh-filters .admin-form-row {
    align-items: stretch;
    flex-direction: column;
  }
  .gh-filters .admin-field {
    flex: 0 0 auto;
  }
  .gh-filter-actions {
    width: 100%;
    flex: 0 0 auto;
    flex-direction: row;
    align-self: stretch;
  }
  .gh-filter-actions :deep(.p-button) {
    flex: 1 1 0;
  }
}

@media (max-width: 520px) {
  .gh-header__copy p {
    display: none;
  }
  .gh-header__actions .admin-status-pill {
    font-size: var(--text-2xs-size);
    line-height: var(--text-2xs-lh);
  }
  .gh-metric {
    gap: 0.5rem;
    padding: 0.65rem;
  }
  .gh-metric strong,
  .gh-metric--sync strong {
    font-size: var(--text-sm-size);
    line-height: var(--text-sm-lh);
  }
}
</style>
