<template>
  <div class="health-overview-screen">
    <div v-show="!expandedSeries" ref="overviewElement" class="health-overview">
      <header v-if="demoMode || !hasLocalMirror" class="health-header">
        <div class="health-header__copy">
          <h2 class="health-header__title">Health overview</h2>
          <p class="health-header__subtitle">
            {{
              syntheticData
                ? 'Generated activity, sleep, body, and vital-sign data.'
                : 'Activity, sleep, body, and vital-sign data from Google Health.'
            }}
          </p>
        </div>
        <div class="admin-action-controls health-header__actions">
          <span v-if="status || demoMode" :class="['admin-status-pill', mirrorStatusClass]">
            <i :class="mirrorStatusIcon" aria-hidden="true" /> {{ mirrorStatusLabel }}
          </span>
          <RouterLink v-slot="{ navigate }" custom :to="GOOGLE_HEALTH_SETTINGS_ROUTE">
            <Button
              :label="demoMode ? 'Integration details' : 'Manage'"
              icon="bi bi-sliders"
              size="small"
              severity="secondary"
              @click="navigate"
            />
          </RouterLink>
        </div>
      </header>

      <p v-if="syntheticData" class="admin-message" role="status">
        Synthetic demo data. Tracks and daily trends share one fictional health timeline.
      </p>
      <GoogleHealthSyncNotice
        v-if="!previewOnly && status"
        :status="status"
        :synthetic-data="syntheticData"
        show-coverage-hint
      />

      <div
        v-if="oauthMessage"
        :class="['admin-message', `admin-message--${oauthMessage.tone}`]"
        :role="oauthMessage.tone === 'error' ? 'alert' : 'status'"
      >
        <i :class="oauthMessage.tone === 'success' ? 'bi bi-check-circle' : 'bi bi-exclamation-circle'" />
        <span>{{ oauthMessage.text }}</span>
      </div>

      <section v-if="previewOnly" class="admin-card empty-placeholder health-demo-preview">
        <i class="bi bi-heart-pulse" aria-hidden="true" />
        <strong>Google Health preview</strong>
        <span>
          Google Health is available in MTL Explorer, but connections are disabled in this public demo. The demo cannot
          access or store data from a Google account.
        </span>
        <div class="health-demo-preview__categories" aria-label="Supported Google Health data">
          <span class="selection-chip">Activity and fitness</span>
          <span class="selection-chip">Health metrics</span>
          <span class="selection-chip">Sleep</span>
          <span class="selection-chip">Exercise routes</span>
        </div>
      </section>

      <template v-if="!previewOnly">
        <section v-if="syntheticData && !showCharts" class="admin-card empty-placeholder">
          <i class="pi pi-spin pi-spinner" />
          <strong>Preparing synthetic health data</strong>
          <span>Records will appear as background generation completes. View progress in Settings → Processing.</span>
        </section>
        <section
          v-else-if="status?.configurationState === 'DISABLED' && !showCharts"
          class="admin-card empty-placeholder"
        >
          <i class="bi bi-heart" aria-hidden="true" />
          <strong>Google Health is disabled</strong>
          <span>This optional connector is not enabled. The rest of MTL Explorer works normally.</span>
        </section>

        <section
          v-else-if="status?.configurationState === 'MISSING_CONFIGURATION' && !showCharts"
          class="admin-card empty-placeholder"
        >
          <i class="bi bi-sliders" aria-hidden="true" />
          <strong>Setup is not complete</strong>
          <span>The server needs Google OAuth credentials before it can connect.</span>
          <RouterLink v-slot="{ navigate }" custom :to="GOOGLE_HEALTH_SETTINGS_ROUTE">
            <Button label="Open setup" icon="bi bi-sliders" size="small" @click="navigate" />
          </RouterLink>
        </section>

        <section v-else-if="!showCharts" class="admin-card empty-placeholder">
          <i class="bi bi-link-45deg" aria-hidden="true" />
          <strong>Connect Google Health</strong>
          <span>Connect from Settings to create a read-only local mirror of your health data.</span>
          <RouterLink v-slot="{ navigate }" custom :to="GOOGLE_HEALTH_SETTINGS_ROUTE">
            <Button label="Open setup" icon="bi bi-link-45deg" size="small" @click="navigate" />
          </RouterLink>
        </section>

        <template v-else>
          <section ref="trendsElement" class="health-trends" aria-label="Health trends">
            <div class="health-toolbar">
              <HealthGroupingControl v-model="selectedGrouping" :resolved="committedMetrics?.resolvedGrouping" />
              <div class="health-date-control">
                <HealthDateRangeControl
                  v-if="selectedGrouping !== 'SECOND'"
                  :start-date="selectedDates.startDate"
                  :end-date="selectedInclusiveEnd"
                  :preset="selectedRange"
                  :label="dateRangeLabel"
                  @preset="selectPresetRange"
                  @custom="applyCustomRange"
                />
                <div v-else class="health-second-date">
                  <Button
                    aria-label="Previous day"
                    icon="bi bi-chevron-left"
                    size="small"
                    severity="secondary"
                    @click="moveSecondDay(-1)"
                  />
                  <InputText
                    id="health-second-date"
                    aria-label="Day"
                    :model-value="selectedDay"
                    :max="today"
                    type="date"
                    @update:model-value="changeSecondDay"
                  />
                  <Button
                    aria-label="Next day"
                    icon="bi bi-chevron-right"
                    size="small"
                    severity="secondary"
                    :disabled="selectedDay >= today"
                    @click="moveSecondDay(1)"
                  />
                </div>
              </div>
              <div class="health-categories" role="group" aria-label="Metric category">
                <button
                  v-for="option in categoryDefinitions"
                  :key="option.value"
                  type="button"
                  :class="{ 'health-categories__active': selectedCategory === option.value }"
                  :aria-pressed="selectedCategory === option.value"
                  @click="selectedCategory = option.value"
                >
                  <i :class="option.icon" aria-hidden="true" /> {{ option.label }}
                </button>
              </div>
            </div>
            <HealthTimeWindowControl
              v-if="selectedGrouping === 'SECOND'"
              v-model="intradayWindow"
              :day="selectedDay"
              :focus-time="intradayFocus"
            />
            <div class="health-metrics-status" role="status">
              <p class="health-displayed-period" :title="displayedPeriodLabel">Showing {{ displayedPeriodLabel }}</p>
              <span :class="['health-loading', { 'health-loading--idle': !metricsLoading }]">
                <i class="pi pi-spin pi-spinner" aria-hidden="true" /> Updating charts…
              </span>
            </div>
            <div v-if="metricsError" class="admin-message admin-message--error" role="alert">
              <i class="bi bi-exclamation-triangle" aria-hidden="true" />
              <span>{{ metricsError }}</span>
              <Button label="Retry charts" size="small" text @click="loadMetrics" />
            </div>

            <div v-if="visibleSeries.length" class="health-chart-grid" :aria-busy="metricsLoading">
              <HealthMetricChart
                v-for="series in visibleSeries"
                :key="series.dataType"
                :series="series"
                :mode="chartMode(series)"
                :selected-bucket="selectedBuckets[series.dataType ?? '']"
                :time-window="intradayWindow"
                :focus-time="intradayFocus"
                @update:mode="setChartMode(series, $event)"
                @update:selected-bucket="selectedBuckets[series.dataType ?? ''] = $event"
                @update:time-window="intradayWindow = $event"
                @update:focus-time="intradayFocus = $event"
                @open-day="openDay"
                @expand="expandChart(series, $event)"
              />
            </div>
            <div v-else-if="!metricsLoading && !metricsError" class="admin-card inline-empty-state health-empty-charts">
              <i class="bi bi-graph-up" aria-hidden="true" />
              <span>{{
                selectedGrouping === 'SECOND'
                  ? 'No readings are available for this day and category. Choose another day or a coarser grouping.'
                  : !status && !syntheticData
                    ? 'No chartable values were found for this period and category. Choose another period or category, or open Manage Google Health.'
                    : 'No chartable values were found in this period. Try a longer period or open Data details.'
              }}</span>
            </div>
          </section>

          <div v-if="mirrorNotice" class="admin-message admin-message--warning" role="status">
            <i class="bi bi-info-circle" aria-hidden="true" />
            <span>{{ mirrorNotice }}</span>
          </div>

          <details class="health-latest" :open="latestExpanded" @toggle="toggleLatestValues">
            <summary>Latest values <span>Most recent measurements, independent of the chart period</span></summary>
            <div class="health-section-heading">
              <div>
                <h3 id="health-latest-heading">Latest available values</h3>
              </div>
              <span
                v-if="summariesLoading || latestLoading"
                class="admin-status-pill admin-status-pill--loading"
                aria-live="polite"
              >
                <i class="pi pi-spin pi-spinner" /> Updating latest values
              </span>
            </div>
            <div v-if="summariesError" class="admin-message admin-message--error" role="alert">
              <i class="bi bi-exclamation-triangle" aria-hidden="true" />
              <span>{{ summariesError }}</span>
              <Button label="Retry summaries" size="small" text @click="loadSummaries()" />
            </div>
            <div v-if="latestError" class="admin-message admin-message--error" role="alert">
              <i class="bi bi-exclamation-triangle" aria-hidden="true" />
              <span>{{ latestError }}</span>
              <Button label="Retry latest values" size="small" text @click="loadLatestValues" />
            </div>
            <div v-if="summarySeries.length" class="health-summaries" aria-label="Latest health values">
              <HealthMetricSummaryCard v-for="series in summarySeries" :key="series.dataType" :series="series" />
            </div>
            <p
              v-else-if="summariesLoaded && !summariesLoading && !summariesError && !latestLoading && !latestError"
              class="health-latest__empty"
            >
              No latest values available.
            </p>
          </details>

          <div class="health-footer">
            <span v-if="status || syntheticData" :class="['admin-status-pill', mirrorStatusClass]"
              ><i :class="mirrorStatusIcon" aria-hidden="true" /> {{ mirrorStatusLabel }}</span
            >
            <RouterLink :to="GOOGLE_HEALTH_SETTINGS_ROUTE">Manage Google Health</RouterLink>
            <button type="button" @click="emit('open-data-details')">
              Open data details <i class="pi pi-angle-right" aria-hidden="true" />
            </button>
          </div>

          <p v-if="!syntheticData && lastSuccessfulSync" class="health-sync-note">
            <i class="bi bi-arrow-repeat" aria-hidden="true" />
            Last successful sync {{ formatDate(lastSuccessfulSync) }}
          </p>
        </template>
      </template>
    </div>
    <section
      v-if="expandedSeries"
      class="health-expanded-chart"
      :aria-label="`${expandedSeries.title} expanded chart`"
      @keydown.esc.stop.prevent="collapseChart"
    >
      <div class="health-expanded-chart__toolbar">
        <button ref="backButton" type="button" class="health-chart-back" @click="collapseChart">
          <i class="bi bi-arrow-left" aria-hidden="true" /> Back to charts
        </button>
        <HealthGroupingControl v-model="selectedGrouping" :resolved="committedMetrics?.resolvedGrouping" />
      </div>
      <HealthTimeWindowControl
        v-if="selectedGrouping === 'SECOND'"
        v-model="intradayWindow"
        :day="selectedDay"
        :focus-time="intradayFocus"
      />
      <div class="health-metrics-status" role="status">
        <p class="health-displayed-period" :title="displayedPeriodLabel">Showing {{ displayedPeriodLabel }}</p>
        <span :class="['health-loading', { 'health-loading--idle': !metricsLoading }]">
          <i class="pi pi-spin pi-spinner" aria-hidden="true" /> Updating charts…
        </span>
      </div>
      <div v-if="metricsError" class="admin-message admin-message--error" role="alert">
        <span>{{ metricsError }}</span>
        <Button label="Retry charts" size="small" text @click="loadMetrics" />
      </div>
      <HealthMetricChart
        :series="expandedSeries"
        :aria-busy="metricsLoading"
        expanded
        :mode="chartMode(expandedSeries)"
        :selected-bucket="selectedBuckets[expandedSeries.dataType ?? '']"
        :time-window="intradayWindow"
        :focus-time="intradayFocus"
        @update:mode="setChartMode(expandedSeries, $event)"
        @update:selected-bucket="selectedBuckets[expandedSeries.dataType ?? ''] = $event"
        @update:time-window="intradayWindow = $event"
        @update:focus-time="intradayFocus = $event"
        @open-day="openDay"
      />
    </section>
  </div>
</template>

<script setup lang="ts">
import { storeToRefs } from 'pinia';
import { computed, nextTick, onBeforeUnmount, ref, shallowRef, watch } from 'vue';
import { RouterLink, useRoute } from 'vue-router';
import Button from 'primevue/button';
import InputText from 'primevue/inputtext';
import HealthDateRangeControl from '../../components/HealthDateRangeControl.vue';
import '@/components/admin/adminCenter.css';
import { useApplicationStatusStore } from '@/stores/applicationStatusStore';
import HealthMetricChart from '../../components/HealthMetricChart.vue';
import HealthGroupingControl from '../../components/HealthGroupingControl.vue';
import HealthTimeWindowControl from '../../components/HealthTimeWindowControl.vue';
import HealthMetricSummaryCard from '../../components/HealthMetricSummaryCard.vue';
import GoogleHealthSyncNotice from '../../components/GoogleHealthSyncNotice.vue';
import { GOOGLE_HEALTH_SETTINGS_ROUTE } from '../../utils/googleHealthNavigation';
import {
  googleHealthApi,
  type GHMetricSeries,
  type GHTypeSummary,
  type GHGroupedSeries,
  type GHGroupedMetrics,
} from '../../api/googleHealthApi';
import { localDateBoundary } from '../../utils/dateBoundary';
import {
  healthCalendarRange,
  customHealthCalendarRange,
  formatCalendarDate,
  HEALTH_DATE_SHORTCUTS,
  type HealthPreset,
} from '../../utils/healthCalendarRange';
import {
  readHealthGrouping,
  saveHealthGrouping,
  readHealthModes,
  saveHealthModes,
  defaultHealthMode,
  type HealthChartMode,
  type HealthChartTimeWindow,
} from '../../utils/healthChartPreferences';
import { googleHealthStatusPresentation } from '../../utils/googleHealthPresentation';
import { formatHealthDateTime } from '../../utils/healthDateTime';

const props = withDefaults(defineProps<{ active?: boolean; demoMode?: boolean; syntheticData?: boolean }>(), {
  active: true,
  demoMode: false,
  syntheticData: false,
});
const previewOnly = computed(() => props.demoMode && !props.syntheticData);
const emit = defineEmits<{ (event: 'open-data-details'): void }>();
const route = useRoute();
const applicationStatusStore = useApplicationStatusStore();
const { googleHealth: status } = storeToRefs(applicationStatusStore);
const metricsLoading = ref(false);
const latestLoading = ref(false);
const latestExpanded = ref(false);
const latestLoaded = ref(false);
const summariesLoading = ref(false);
const summariesLoaded = ref(false);
const summariesError = ref('');
const metricsError = ref('');
const latestError = ref('');
const summaries = ref<GHTypeSummary[]>([]);
// Responses are replaced as a whole; avoid proxying every second of a day's readings.
const committedMetrics = shallowRef<GHGroupedMetrics>();
const committedPeriodLabel = ref('');
const metricSeries = computed(() => committedMetrics.value?.series ?? []);
const selectedGrouping = ref(readHealthGrouping());
const today = ref(formatCalendarDate(new Date()));
const selectedDay = ref(today.value);
let midnightTimer: ReturnType<typeof setTimeout> | undefined;
function refreshToday() {
  today.value = formatCalendarDate(new Date());
  clearTimeout(midnightTimer);
  const nextMidnight = new Date();
  nextMidnight.setHours(24, 0, 0, 0);
  midnightTimer = setTimeout(refreshToday, nextMidnight.getTime() - Date.now());
}
const chartModes = ref(readHealthModes());
const selectedBuckets = ref<Record<string, string>>({});
// Intraday charts share one time window and one marked time so readings can be compared.
const intradayWindow = ref<HealthChartTimeWindow>();
const intradayFocus = ref<number>();
const trendsElement = ref<HTMLElement | null>(null);
const displayedPeriodLabel = computed(() => committedPeriodLabel.value || periodLabel.value);
function chartMode(series: GHGroupedSeries): HealthChartMode {
  return chartModes.value[series.dataType ?? ''] ?? defaultHealthMode(series);
}
function setChartMode(series: GHGroupedSeries, mode: HealthChartMode) {
  chartModes.value[series.dataType ?? ''] = mode;
  saveHealthModes(chartModes.value);
}
const latestMetricSeries = ref<GHMetricSeries[]>([]);
const selectedRange = ref<RangeId>('30D');
const appliedCustomRange = ref<{ start: string; end: string } | null>(null);
const selectedCategory = ref<CategoryFilter>('ALL');
const expandedDataType = ref<string | null>(null);
const overviewElement = ref<HTMLElement | null>(null);
const backButton = ref<HTMLButtonElement | null>(null);
let expandButton: HTMLElement | null = null;
let overviewScrollTop = 0;
let metricsRequestId = 0;
let latestRequestId = 0;
let catalogueRequestId = 0;
let summariesRequestId = 0;
let disposed = false;
let postSyncRefresh: Promise<void> | null = null;

type RangeId = HealthPreset | 'CUSTOM';
type CategoryFilter = 'ALL' | 'ACTIVITY' | 'HEART_AND_VITALS' | 'SLEEP' | 'BODY';

const categoryDefinitions = [
  { value: 'ALL' as const, label: 'All', icon: 'bi bi-grid' },
  { value: 'ACTIVITY' as const, label: 'Activity', icon: 'bi bi-person-walking' },
  { value: 'HEART_AND_VITALS' as const, label: 'Heart & vitals', icon: 'bi bi-heart-pulse' },
  { value: 'SLEEP' as const, label: 'Sleep', icon: 'bi bi-moon-stars' },
  { value: 'BODY' as const, label: 'Body', icon: 'bi bi-person' },
];
const MAX_SUMMARY_CARDS = 4;

const oauthMessage = computed(() => {
  if (props.demoMode) return null;
  if (route.query.googleHealth === 'connected')
    return { tone: 'success', text: 'Google Health is connected. The first sync has started.' };
  if (route.query.googleHealth === 'error')
    return { tone: 'error', text: 'Google authorization did not complete. Check the setup and try again.' };
  return null;
});
const hasLocalMirror = computed(
  () =>
    metricSeries.value.length > 0 ||
    (status.value?.mirrorSummary?.recordCount ?? 0) > 0 ||
    summaries.value.some((item) => (item.recordCount ?? 0) > 0)
);
const showCharts = computed(
  () =>
    hasLocalMirror.value ||
    status.value?.connectionState === 'CONNECTED' ||
    Boolean(metricsError.value) ||
    (props.active && (!committedMetrics.value || metricsLoading.value || (!props.syntheticData && !status.value)))
);
const periodLabel = computed(() => {
  if (selectedGrouping.value === 'SECOND')
    return new Intl.DateTimeFormat(undefined, {
      weekday: 'short',
      day: 'numeric',
      month: 'short',
      year: 'numeric',
    }).format(localDateBoundary(selectedDay.value, false));
  if (selectedRange.value === 'CUSTOM' && appliedCustomRange.value) {
    return formatCustomPeriod(appliedCustomRange.value.start, appliedCustomRange.value.end);
  }
  return HEALTH_DATE_SHORTCUTS.find((option) => option.id === selectedRange.value)?.label ?? 'Selected period';
});
const selectedDates = computed(() =>
  selectedGrouping.value === 'SECOND'
    ? customHealthCalendarRange(selectedDay.value, selectedDay.value)
    : selectedRange.value === 'CUSTOM' && appliedCustomRange.value
      ? customHealthCalendarRange(appliedCustomRange.value.start, appliedCustomRange.value.end)
      : healthCalendarRange(selectedRange.value === 'CUSTOM' ? '30D' : selectedRange.value)
);
const selectedInclusiveEnd = computed(() => {
  const end = localDateBoundary(selectedDates.value.endDate, false)!;
  end.setDate(end.getDate() - 1);
  return formatCalendarDate(end);
});
const dateRangeLabel = computed(() => formatCustomPeriod(selectedDates.value.startDate, selectedInclusiveEnd.value));
const summarySeries = computed(() => latestMetricSeries.value.slice(0, MAX_SUMMARY_CARDS));
const visibleSeries = computed(() =>
  selectedCategory.value === 'ALL'
    ? metricSeries.value
    : metricSeries.value.filter((series) => series.category === selectedCategory.value)
);
const expandedSeries = computed(() => metricSeries.value.find((series) => series.dataType === expandedDataType.value));

async function expandChart(series: GHGroupedSeries, event: MouseEvent) {
  overviewScrollTop = overviewElement.value?.scrollTop ?? 0;
  expandButton = event.currentTarget instanceof HTMLElement ? event.currentTarget : null;
  expandedDataType.value = series.dataType ?? null;
  await nextTick();
  backButton.value?.focus({ preventScroll: true });
}

async function collapseChart() {
  expandedDataType.value = null;
  await nextTick();
  if (overviewElement.value) overviewElement.value.scrollTop = overviewScrollTop;
  expandButton?.focus({ preventScroll: true });
  expandButton = null;
}
const lastSuccessfulSync = computed(() => {
  const dates = (status.value?.syncStates ?? [])
    .map((item) => item.lastSuccessAt)
    .filter((value): value is Date => value instanceof Date);
  return dates.length ? new Date(Math.max(...dates.map((date) => date.getTime()))) : undefined;
});
const mirrorStatusPresentation = computed(() =>
  previewOnly.value
    ? { label: 'Demo preview', className: '', icon: 'bi bi-heart-pulse' }
    : googleHealthStatusPresentation(status.value, { syntheticData: props.syntheticData })
);
const mirrorStatusLabel = computed(() => mirrorStatusPresentation.value.label);
const mirrorStatusClass = computed(() => mirrorStatusPresentation.value.className);
const mirrorStatusIcon = computed(() => mirrorStatusPresentation.value.icon);
const mirrorNotice = computed(() => {
  if (props.syntheticData || !status.value) return '';
  if (status.value?.configurationState === 'DISABLED')
    return 'Google Health is disabled. Stored local data remains available and synchronization is stopped.';
  if (status.value?.configurationState === 'MISSING_CONFIGURATION')
    return 'Google Health setup is incomplete. Stored local data remains available, but synchronization is stopped.';
  if (status.value?.connectionState !== 'CONNECTED')
    return 'Google Health is disconnected. Stored local data remains available and synchronization is stopped.';
  return '';
});

watch(
  () => props.active,
  (active) => {
    if (active) refreshToday();
    if (active && !previewOnly.value) void loadMetrics();
    if (!active) {
      clearTimeout(midnightTimer);
      invalidateRequests();
      expandedDataType.value = null;
    }
  },
  { immediate: true }
);
watch(
  () => [status.value?.job?.id, status.value?.job?.state] as const,
  ([jobId, state], previous) => {
    const [previousJobId, previousState] = previous ?? [];
    if (
      props.active &&
      !previewOnly.value &&
      jobId &&
      previousJobId &&
      ['QUEUED', 'RUNNING'].includes(previousState ?? '') &&
      (jobId !== previousJobId || !['QUEUED', 'RUNNING'].includes(state ?? ''))
    ) {
      void refreshAfterSync();
    }
  }
);
watch(
  () => status.value?.mirrorSnapshotAt,
  (next, previous) => {
    if (props.syntheticData && props.active && previous && next?.getTime() !== previous.getTime())
      void refreshAfterSync();
  }
);

watch([selectedRange, selectedGrouping, selectedDay], () => {
  saveHealthGrouping(selectedGrouping.value);
  if (props.active) void loadMetrics();
});

function ownsCatalogueRequest(requestId: number): boolean {
  return !disposed && props.active && latestExpanded.value && requestId === catalogueRequestId;
}

function toggleLatestValues(event: Event) {
  const open = (event.currentTarget as HTMLDetailsElement).open;
  if (open === latestExpanded.value) return;
  latestExpanded.value = open;
  if (!open) {
    summariesRequestId++;
    latestRequestId++;
    summariesLoading.value = false;
    latestLoading.value = false;
  } else if (!latestLoaded.value && !summariesLoading.value && !latestLoading.value) {
    void loadSummaries();
  }
}

async function loadSummaries() {
  if (!ownsCatalogueRequest(catalogueRequestId)) return;
  const requestId = ++summariesRequestId;
  const catalogueId = catalogueRequestId;
  const ownsRequest = () => ownsCatalogueRequest(catalogueId) && requestId === summariesRequestId;
  summariesLoading.value = true;
  summariesError.value = '';
  try {
    const response = await googleHealthApi.summaries();
    if (!ownsRequest()) return;
    summaries.value = response;
    summariesLoaded.value = true;
    summariesLoading.value = false;
    // Date bounds are needed only for the expanded latest-values section.
    if (response.some((item) => (item.recordCount ?? 0) > 0)) await loadLatestValues();
    else {
      latestRequestId++;
      latestLoading.value = false;
      latestMetricSeries.value = [];
      latestError.value = '';
      latestLoaded.value = true;
    }
  } catch (loadError) {
    if (ownsRequest())
      summariesError.value = loadError instanceof Error ? loadError.message : 'Could not load data summaries.';
  } finally {
    if (ownsRequest()) summariesLoading.value = false;
  }
}

function refreshAfterSync(): Promise<void> {
  if (postSyncRefresh) return postSyncRefresh;
  latestLoaded.value = false;
  const refresh = Promise.all([loadMetrics(), ...(latestExpanded.value ? [loadSummaries()] : [])])
    .then(() => undefined)
    .finally(() => {
      if (postSyncRefresh === refresh) postSyncRefresh = null;
    });
  postSyncRefresh = refresh;
  return refresh;
}

async function loadMetrics() {
  if (disposed || !props.active || previewOnly.value) return;
  const requestId = ++metricsRequestId;
  metricsLoading.value = true;
  metricsError.value = '';
  const range = selectedDates.value;
  const requestLabel = periodLabel.value;
  const grouping = selectedGrouping.value;
  const timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;
  const samePeriod =
    committedMetrics.value?.startDate === range.startDate &&
    committedMetrics.value?.endDate === range.endDate &&
    committedMetrics.value?.requestedGrouping === grouping;
  try {
    const response = await googleHealthApi.groupedMetrics(range.startDate, range.endDate, grouping, timeZone);
    if (requestId === metricsRequestId) {
      const nextSelection: Record<string, string> = {};
      for (const series of response.series ?? []) {
        const key = series.dataType ?? '';
        const previous = samePeriod ? selectedBuckets.value[key] : undefined;
        nextSelection[key] =
          series.buckets?.find((bucket) => bucket.startDate === previous)?.startDate ??
          [...(series.buckets ?? [])].reverse().find((bucket) => (bucket.availableDays ?? 0) > 0)?.startDate ??
          '';
      }
      if (!samePeriod) {
        intradayWindow.value = undefined;
        intradayFocus.value = undefined;
      }
      committedMetrics.value = response;
      committedPeriodLabel.value = requestLabel;
      selectedBuckets.value = nextSelection;
      if (expandedDataType.value && !response.series?.some((series) => series.dataType === expandedDataType.value))
        await collapseChart();
    }
  } catch (loadError) {
    if (requestId === metricsRequestId)
      metricsError.value = loadError instanceof Error ? loadError.message : 'Could not load health charts.';
  } finally {
    if (requestId === metricsRequestId) metricsLoading.value = false;
  }
}

async function loadLatestValues() {
  if (disposed || !props.active || previewOnly.value || !latestExpanded.value) return;
  const requestId = ++latestRequestId;
  latestLoading.value = true;
  latestError.value = '';
  try {
    const response = await googleHealthApi.latestMetrics(summaries.value);
    if (requestId === latestRequestId) {
      latestMetricSeries.value = response;
      latestLoaded.value = true;
    }
  } catch (loadError) {
    if (requestId === latestRequestId) {
      latestError.value = loadError instanceof Error ? loadError.message : 'Could not load latest health values.';
    }
  } finally {
    if (requestId === latestRequestId) latestLoading.value = false;
  }
}

function changeSecondDay(value: string | undefined) {
  const date = value ? localDateBoundary(value, false) : undefined;
  if (date && Number.isFinite(date.getTime()) && formatCalendarDate(date) === value) selectedDay.value = value!;
}

function openDay(date: string) {
  selectedDay.value = date;
  selectedGrouping.value = 'SECOND';
  if (!expandedDataType.value) trendsElement.value?.scrollIntoView({ block: 'start', behavior: 'smooth' });
}

function moveSecondDay(offset: number) {
  const date = localDateBoundary(selectedDay.value, false);
  if (!date || !Number.isFinite(date.getTime())) return;
  date.setDate(date.getDate() + offset);
  selectedDay.value = formatCalendarDate(date);
}

function selectPresetRange(range: HealthPreset) {
  if (selectedRange.value === range) void loadMetrics();
  else selectedRange.value = range;
}

function applyCustomRange(range: { start: string; end: string }) {
  appliedCustomRange.value = range;
  if (selectedRange.value === 'CUSTOM') void loadMetrics();
  else selectedRange.value = 'CUSTOM';
}

function invalidateRequests() {
  postSyncRefresh = null;
  latestExpanded.value = false;
  latestLoaded.value = false;
  catalogueRequestId++;
  metricsRequestId++;
  latestRequestId++;
  summariesLoading.value = false;
  metricsLoading.value = false;
  latestLoading.value = false;
}
onBeforeUnmount(() => {
  clearTimeout(midnightTimer);
  disposed = true;
  invalidateRequests();
});

function formatCustomPeriod(start: string, end: string): string {
  const formatter = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' });
  return `${formatter.format(localDateBoundary(start, false))} – ${formatter.format(localDateBoundary(end, false))}`;
}

function formatDate(value?: Date): string {
  return formatHealthDateTime(value, 'short', 'never');
}
</script>

<style scoped>
.health-overview-screen {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}
.health-overview {
  display: flex;
  width: 100%;
  max-width: 72rem;
  box-sizing: border-box;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  gap: 1rem;
  margin: 0 auto;
  padding: 0.35rem 1rem 1.4rem;
}
.health-expanded-chart {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  min-height: 0;
  gap: 0.65rem;
  padding: 0.35rem 1rem 0.25rem;
  overflow: hidden;
}
.health-expanded-chart__toolbar {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 0.5rem;
  color: var(--text-muted);
  font-size: var(--text-xs-size);
}
.health-chart-back {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  min-height: 2.25rem;
  padding: 0.35rem 0.65rem;
  border: 1px solid var(--border-default);
  border-radius: 6px;
  background: transparent;
  color: var(--text-primary);
  font: inherit;
  cursor: pointer;
}
.health-chart-back:hover {
  background: var(--surface-hover);
}
.health-chart-back:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}
.health-demo-preview {
  text-align: center;
}
.health-demo-preview__categories {
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 0.45rem;
}
.health-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding-bottom: 0.65rem;
  border-bottom: 1px solid var(--border-default);
}
.health-header__copy {
  min-width: 0;
  flex: 1 1 auto;
}
.health-header__title {
  margin: 0;
  color: var(--text-primary);
  font-size: var(--text-base-size);
  font-weight: 700;
  line-height: var(--text-base-lh);
}
.health-header__subtitle {
  margin: 0.15rem 0 0;
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}
.health-header__actions {
  margin-left: auto;
}
.health-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.75rem;
}
.health-second-date {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
.health-date-control {
  margin-right: auto;
}
.health-trends {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}
.health-metrics-status {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  flex: 0 0 auto;
  align-items: center;
  gap: 0.75rem;
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}
.health-displayed-period {
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.health-loading {
  display: flex;
  gap: 0.5rem;
  align-items: center;
  white-space: nowrap;
}
.health-loading--idle {
  /* Reserve the loading message's space across requests, including on narrow screens. */
  visibility: hidden;
}
.health-categories {
  display: flex;
  border: 1px solid var(--border-default);
  border-radius: 8px;
  overflow: hidden;
}
.health-categories button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.4rem;
  min-height: 2.5rem;
  padding: 0.4rem 0.65rem;
  border: 0;
  border-right: 1px solid var(--border-default);
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  font: inherit;
  font-size: var(--text-xs-size);
  white-space: nowrap;
}
.health-categories button:last-child {
  border-right: 0;
}
.health-categories button:hover {
  background: var(--surface-hover);
}
.health-categories button:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: -2px;
}
.health-categories__active {
  background: var(--accent-bg) !important;
  color: var(--accent-text) !important;
}
.health-categories i {
  margin: 0;
}
.health-latest {
  border-top: 1px solid var(--border-default);
  padding-top: 0.85rem;
}
.health-latest summary {
  color: var(--text-secondary);
  font-size: var(--text-sm-size);
  cursor: pointer;
}
.health-latest summary span {
  margin-left: 0.5rem;
  font-size: var(--text-xs-size);
  color: var(--text-muted);
}
.health-section-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.health-section-heading h3 {
  font-size: var(--text-sm-size);
}
.health-summaries {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0.75rem;
}
.health-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 0.75rem;
  font-size: var(--text-xs-size);
}
.health-footer > span {
  margin-right: auto;
}
.health-footer a,
.health-footer button {
  color: var(--text-muted);
}
.health-footer button {
  border: 0;
  background: transparent;
  font: inherit;
  cursor: pointer;
  padding: 0.5rem 0;
}
.health-chart-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.75rem;
}
.health-empty-charts {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  min-height: 8rem;
  padding: 1.5rem;
  border: 1px dashed var(--border-medium);
  border-radius: 8px;
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
  text-align: center;
}
.health-empty-charts i {
  font-size: var(--text-xl-size);
  color: var(--text-faint);
}
.health-sync-note {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.35rem;
  margin: 0;
  color: var(--text-faint);
  font-size: var(--text-2xs-size);
  line-height: var(--text-2xs-lh);
  text-align: center;
}
.health-sync-note i {
  margin-right: 0;
}

@media (max-width: 1150px) and (min-width: 701px) {
  .health-categories {
    margin-left: auto;
  }
}
@media (max-width: 700px) {
  .health-overview,
  .health-expanded-chart {
    padding-inline: 0.75rem;
  }
  .health-toolbar {
    display: grid;
    grid-template-columns: minmax(0, 1fr);
    gap: 0.75rem;
  }
  .health-categories {
    grid-row: 1;
    width: 100%;
  }
  .health-categories button {
    flex: 1;
    min-height: 2.75rem;
    padding-inline: 0.35rem;
  }
  .health-categories i {
    display: none;
  }
  .health-date-control {
    grid-row: 2;
    width: 100%;
  }
  .health-date-control :deep(.health-date-trigger) {
    width: 100%;
    min-height: 2.75rem;
  }
  .health-chart-grid {
    grid-template-columns: 1fr;
  }
  .health-summaries {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .health-latest summary span {
    display: block;
    margin: 0.35rem 0 0;
  }
  .health-header {
    flex-wrap: wrap;
  }
  .health-header__subtitle {
    display: none;
  }
}
</style>
