<template>
  <div class="tool-container">
    <TrackDetailMiniMap
      :gps-track-id="gpsTrackId"
      :track-events="trackEvents"
      :track-coordinates="miniMapCoordinates"
      :selected-event-key="selectedTrackEventKey"
      @select-event="onTrackEventSelected"
    />

    <Tabs :value="activeTab" @update:value="onTabChange">
      <TabList>
        <Tab value="0">Overview</Tab>
        <Tab value="1">Graphs</Tab>
        <Tab value="2">Quality</Tab>
        <Tab value="3">Related</Tab>
        <Tab value="4">Events</Tab>
      </TabList>
      <TabPanels>
        <TabPanel value="0">
          <TrackDetailOverview :gps-track="gpsTrack ?? undefined" :track-details="trackDetails" />
        </TabPanel>

        <TabPanel value="1">
          <div class="graphs-panel" :style="graphHeightStyle">
            <div class="graphs-toolbar">
              <div class="graphs-toolbar-section graphs-axis-section">
                <span class="graphs-toolbar-label">X Axis</span>
                <div class="graphs-toggle">
                  <button
                    :class="['toggle-btn', { 'toggle-btn--active': xMode === 'time' }]"
                    @click="setXModeValue('time')"
                  >
                    <i class="bi bi-clock"></i> Time
                  </button>
                  <button
                    :class="['toggle-btn', { 'toggle-btn--active': xMode === 'distance' }]"
                    @click="setXModeValue('distance')"
                  >
                    <i class="bi bi-signpost-split"></i> Distance
                  </button>
                </div>
              </div>

              <div class="graphs-toolbar-section graphs-range-band-section">
                <span class="graphs-toolbar-label">Detail</span>
                <div class="graphs-toggle graphs-toggle--single">
                  <button
                    type="button"
                    data-test="range-toggle"
                    :class="['toggle-btn', { 'toggle-btn--active': showRangeBand }]"
                    :aria-pressed="showRangeBand"
                    title="Show min/max range"
                    @click="toggleRangeBand"
                  >
                    <i class="bi bi-bar-chart-line"></i> Range
                  </button>
                </div>
              </div>

              <div class="graphs-toolbar-section graphs-range-section">
                <span class="graphs-toolbar-label">
                  Points
                  <span class="graphs-toolbar-value">{{ chartPointCount }}</span>
                </span>
                <div class="graphs-slider-shell">
                  <button
                    class="graphs-slider-icon-btn"
                    type="button"
                    :disabled="chartPointCount <= CHART_POINT_COUNT_MIN"
                    aria-label="Load fewer chart points"
                    title="Load fewer chart points"
                    @click="nudgeChartPointCount(-CHART_POINT_SLIDER_NUDGE_STEP)"
                  >
                    <i class="bi bi-dash-lg"></i>
                  </button>
                  <Slider
                    v-model="chartPointSliderValue"
                    :min="CHART_POINT_SLIDER_MIN"
                    :max="CHART_POINT_SLIDER_MAX"
                    :step="CHART_POINT_SLIDER_STEP"
                    class="graphs-count-slider"
                    aria-label="Adjust chart point count"
                    @change="onChartPointCountInput"
                    @slideend="onChartPointCountSlideEnd"
                  />
                  <button
                    class="graphs-slider-icon-btn"
                    type="button"
                    :disabled="chartPointCount >= CHART_POINT_COUNT_MAX"
                    aria-label="Load more chart points"
                    title="Load more chart points"
                    @click="nudgeChartPointCount(CHART_POINT_SLIDER_NUDGE_STEP)"
                  >
                    <i class="bi bi-plus-lg"></i>
                  </button>
                </div>
              </div>

              <div class="graphs-toolbar-section graphs-range-section">
                <span class="graphs-toolbar-label">Height</span>
                <div class="graphs-slider-shell">
                  <button
                    class="graphs-slider-icon-btn"
                    type="button"
                    :disabled="graphHeightPx <= GRAPH_HEIGHT_MIN"
                    aria-label="Make graphs smaller"
                    title="Make graphs smaller"
                    @click="nudgeGraphHeight(-GRAPH_HEIGHT_STEP)"
                  >
                    <i class="bi bi-arrows-collapse-vertical"></i>
                  </button>
                  <Slider
                    v-model="graphHeightPx"
                    :min="GRAPH_HEIGHT_MIN"
                    :max="GRAPH_HEIGHT_MAX"
                    :step="GRAPH_HEIGHT_STEP"
                    class="graphs-height-slider"
                    aria-label="Adjust graph height"
                    @change="onGraphHeightCommit"
                  />
                  <button
                    class="graphs-slider-icon-btn"
                    type="button"
                    :disabled="graphHeightPx >= GRAPH_HEIGHT_MAX"
                    aria-label="Make graphs bigger"
                    title="Make graphs bigger"
                    @click="nudgeGraphHeight(GRAPH_HEIGHT_STEP)"
                  >
                    <i class="bi bi-arrows-expand-vertical"></i>
                  </button>
                </div>
              </div>
            </div>
            <TrackGraph
              :config="trackGraphConfigs.speed"
              :track-details="trackDetails"
              :x-mode="xMode"
              :sync-enabled="isGraphsTabActive"
              :show-range="showRangeBand"
            />
            <TrackGraph
              :config="trackGraphConfigs.elevation"
              :track-details="trackDetails"
              :x-mode="xMode"
              :sync-enabled="isGraphsTabActive"
              :show-range="showRangeBand"
            />
            <TrackGraph
              :config="trackGraphConfigs.elevationGain"
              :track-details="trackDetails"
              :x-mode="xMode"
              :sync-enabled="isGraphsTabActive"
              :show-range="showRangeBand"
            />
            <TrackGraph
              v-if="xMode === 'time'"
              :config="trackGraphConfigs.distance"
              :track-details="trackDetails"
              :x-mode="xMode"
              :sync-enabled="isGraphsTabActive"
              :show-range="showRangeBand"
            />
            <TrackGraph
              :config="trackGraphConfigs.energy"
              :track-details="trackDetails"
              :x-mode="xMode"
              :sync-enabled="isGraphsTabActive"
              :show-range="showRangeBand"
            />
            <TrackGraph
              :config="trackGraphConfigs.power"
              :track-details="trackDetails"
              :x-mode="xMode"
              :sync-enabled="isGraphsTabActive"
              :show-range="showRangeBand"
            />
          </div>
        </TabPanel>

        <TabPanel value="2">
          <TrackDetailQuality
            :gps-track="gpsTrack ?? undefined"
            @navigate-track="navigateToTrack"
            @track-updated="onTrackUpdated"
          />
        </TabPanel>

        <TabPanel value="3">
          <TrackDetailRelated
            :related-tracks="relatedTracks ?? undefined"
            :gps-track="gpsTrack ?? undefined"
            :is-loading="isLoading"
            @navigate-track="navigateToTrack"
          />
        </TabPanel>

        <TabPanel value="4">
          <TrackDetailEvents
            :events="trackEvents"
            :selected-event-key="selectedTrackEventKey"
            @select-event="onTrackEventSelected"
          />
        </TabPanel>
      </TabPanels>
    </Tabs>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import {
  fetchTrackDetails,
  fetchTrackPointsForRenderedShape,
  getRelatedTracks,
  type ChartPoint,
} from '@/utils/ServiceHelper';
import {
  clampTrackDetailsChartPointSliderValue,
  roundToNiceTrackDetailsChartPointCount,
  TRACK_DETAILS_CHART_POINT_SLIDER_MAX,
  TRACK_DETAILS_CHART_POINT_SLIDER_MIN,
  TRACK_DETAILS_CHART_POINT_SLIDER_STEP,
  TRACK_DETAILS_CHART_POINTS_DEFAULT,
  TRACK_DETAILS_CHART_POINTS_MAX,
  TRACK_DETAILS_CHART_POINTS_MIN,
  trackDetailsChartPointCountToSliderValue,
  trackDetailsChartPointSliderValueToCount,
} from '@/utils/trackDetailsChartPointSettings';
import { XMode } from '@/utils/chartSeriesAdapter';
import { useTrackMapSync, type TrackPoint } from '@/composables/useTrackMapSync';
import type { GpsTrackDataPoint } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import { useChartSync } from '@/composables/useChartSync';
import type {
  GpsTrack,
  GpsTrackData,
  GpsTrackEvent,
  RelatedTracks,
} from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import TrackGraph from '@/components/trackdetails/TrackGraph.vue';
import { trackGraphConfigs } from '@/components/trackdetails/trackGraphConfigs';
import TrackDetailOverview from '@/components/trackdetails/TrackDetailOverview.vue';
import TrackDetailQuality from '@/components/trackdetails/TrackDetailQuality.vue';
import TrackDetailRelated from '@/components/trackdetails/TrackDetailRelated.vue';
import TrackDetailMiniMap from '@/components/trackdetails/TrackDetailMiniMap.vue';
import TrackDetailEvents from '@/components/trackdetails/TrackDetailEvents.vue';
import { USER_PREFS_KEYS, migrateLegacyKeys, readPref, writePref } from '@/utils/userPrefs';
import { DETAIL_TRACK_PRECISION } from '@/utils/tracks/trackConstants';
import { fetchDetailTrackAtPrecision } from '@/utils/tracks/trackCollectionLoader';
import type { TrackPrecisionResult } from '@/utils/tracks/trackTypes';

const GRAPH_HEIGHT_STORAGE_KEY = USER_PREFS_KEYS.trackGraphHeight;
const GRAPH_RANGE_BAND_STORAGE_KEY = USER_PREFS_KEYS.trackGraphRangeBand;
const CHART_POINT_COUNT_STORAGE_KEY = USER_PREFS_KEYS.trackChartPointCount;
const GRAPH_HEIGHT_MIN = 100;
const GRAPH_HEIGHT_MAX = 640;
const GRAPH_HEIGHT_STEP = 10;
const GRAPH_HEIGHT_DEFAULT = 240;
const CHART_POINT_COUNT_MIN = TRACK_DETAILS_CHART_POINTS_MIN;
const CHART_POINT_COUNT_MAX = TRACK_DETAILS_CHART_POINTS_MAX;
const CHART_POINT_COUNT_DEFAULT = TRACK_DETAILS_CHART_POINTS_DEFAULT;
const CHART_POINT_SLIDER_MIN = TRACK_DETAILS_CHART_POINT_SLIDER_MIN;
const CHART_POINT_SLIDER_MAX = TRACK_DETAILS_CHART_POINT_SLIDER_MAX;
const CHART_POINT_SLIDER_STEP = TRACK_DETAILS_CHART_POINT_SLIDER_STEP;
const CHART_POINT_SLIDER_NUDGE_STEP = 1;
const TRACK_DETAIL_TAB_OVERVIEW = '0';
const TRACK_DETAIL_TAB_GRAPHS = '1';
const TRACK_DETAIL_TAB_EVENTS = '4';
const TRACK_DETAIL_TABS = new Set(['0', '1', '2', '3', '4']);
type TrackDetailTab = '0' | '1' | '2' | '3' | '4';
type SliderValue = number | number[];
type SliderSlideEndEvent = { value: SliderValue };

type TrackLoadedPayload = {
  id: GpsTrack['id'];
  name: string;
  description: string;
  activityType: GpsTrack['activityType'];
};

function normalizeTrackDetailTab(value: string | number): TrackDetailTab {
  const tab = String(value);
  return TRACK_DETAIL_TABS.has(tab) ? (tab as TrackDetailTab) : TRACK_DETAIL_TAB_OVERVIEW;
}

function clampGraphHeight(value: number): number {
  return Math.min(GRAPH_HEIGHT_MAX, Math.max(GRAPH_HEIGHT_MIN, value));
}

function loadGraphHeight(): number {
  try {
    migrateLegacyKeys();
    const value = localStorage.getItem(GRAPH_HEIGHT_STORAGE_KEY);
    if (value === 'compact') {
      return GRAPH_HEIGHT_MIN;
    }
    if (value === 'normal') {
      return GRAPH_HEIGHT_DEFAULT;
    }
    if (value === 'tall') {
      return GRAPH_HEIGHT_MAX;
    }

    const parsed = Number.parseInt(value ?? '', 10);
    if (Number.isFinite(parsed)) {
      return clampGraphHeight(parsed);
    }
  } catch {
    // Ignore localStorage access failures and fall back to the default height.
  }
  return GRAPH_HEIGHT_DEFAULT;
}

function loadChartPointCount(): number {
  const raw = readPref(CHART_POINT_COUNT_STORAGE_KEY);
  if (raw == null) {
    return CHART_POINT_COUNT_DEFAULT;
  }

  const parsed = Number.parseInt(raw, 10);
  const isStoredCountValid =
    Number.isFinite(parsed) && parsed >= CHART_POINT_COUNT_MIN && parsed <= CHART_POINT_COUNT_MAX;
  const nextCount = isStoredCountValid ? roundToNiceTrackDetailsChartPointCount(parsed) : CHART_POINT_COUNT_DEFAULT;
  if (String(nextCount) !== raw) {
    writePref(CHART_POINT_COUNT_STORAGE_KEY, String(nextCount));
  }
  return nextCount;
}

function loadGraphRangeBand(): boolean {
  const raw = readPref(GRAPH_RANGE_BAND_STORAGE_KEY);
  return raw == null ? true : raw === 'true';
}

function sliderValueToNumber(value: SliderValue | SliderSlideEndEvent): number {
  const raw = typeof value === 'object' && !Array.isArray(value) && 'value' in value ? value.value : value;
  return Array.isArray(raw) ? raw[0] : raw;
}

function nextDistinctChartPointSliderValue(
  currentSliderValue: number,
  currentPointCount: number,
  direction: number
): number {
  const sliderStep = direction < 0 ? -CHART_POINT_SLIDER_STEP : CHART_POINT_SLIDER_STEP;
  let nextSliderValue = clampTrackDetailsChartPointSliderValue(currentSliderValue + sliderStep);

  while (
    nextSliderValue !== currentSliderValue &&
    trackDetailsChartPointSliderValueToCount(nextSliderValue) === currentPointCount
  ) {
    const candidateSliderValue = clampTrackDetailsChartPointSliderValue(nextSliderValue + sliderStep);
    if (candidateSliderValue === nextSliderValue) {
      break;
    }
    nextSliderValue = candidateSliderValue;
  }

  return nextSliderValue;
}

function settle<T>(promise: Promise<T>): Promise<PromiseSettledResult<T>> {
  return promise.then(
    (value) => ({ status: 'fulfilled' as const, value }),
    (reason) => ({ status: 'rejected' as const, reason })
  );
}

function unwrapSettled<T>(result: PromiseSettledResult<T>): T {
  if (result.status === 'rejected') {
    throw result.reason;
  }
  return result.value;
}

defineOptions({
  name: 'TrackDetails',
});

const props = defineProps<{
  gpsTrackId: number;
}>();

const emit = defineEmits<{
  'track-loaded': [payload: TrackLoadedPayload];
}>();

const initialChartPointCount = loadChartPointCount();
const gpsTrackId = ref(props.gpsTrackId);
const gpsTrack = ref<GpsTrack | null>(null);
const relatedTracks = ref<RelatedTracks | null>(null);
const trackDetails = ref<ChartPoint[]>([]);
const trackEvents = ref<GpsTrackEvent[]>([]);
const miniMapCoordinates = ref<number[][]>([]);
const renderedShapePoints = ref<GpsTrackDataPoint[]>([]);
const selectedTrackEventKey = ref<string | number | null>(null);
const activeTab = ref<TrackDetailTab>(TRACK_DETAIL_TAB_OVERVIEW);
const xMode = ref<'time' | 'distance'>('time');
const chartPointCount = ref(initialChartPointCount);
const chartPointSliderValue = ref(trackDetailsChartPointCountToSliderValue(initialChartPointCount));
const graphHeightPx = ref(loadGraphHeight());
const showRangeBand = ref(loadGraphRangeBand());
const isLoading = ref(false);
let graphHeightReflowFrame: number | null = null;
let chartPointReloadTimer: number | null = null;
let loadGeneration = 0;
let chartReloadGeneration = 0;

const { setTrackPoints, clearAll } = useTrackMapSync();
const { setXMode, clearChartInteraction } = useChartSync();

const graphHeightStyle = computed<Record<string, string>>(() => ({
  '--track-detail-graph-height': `${graphHeightPx.value}px`,
}));

const isGraphsTabActive = computed(() => activeTab.value === TRACK_DETAIL_TAB_GRAPHS);

watch(
  () => props.gpsTrackId,
  (newId) => {
    gpsTrackId.value = newId;
    void load(newId);
  }
);

onMounted(() => {
  console.log('mounted');
  setXMode(xMode.value);
  void load(gpsTrackId.value);
});

onBeforeUnmount(() => {
  if (graphHeightReflowFrame !== null) {
    window.cancelAnimationFrame(graphHeightReflowFrame);
    graphHeightReflowFrame = null;
  }
  clearScheduledChartPointReload();
  clearAll();
});

function onTrackEventSelected(key: string | number | null) {
  if (key != null && activeTab.value !== TRACK_DETAIL_TAB_EVENTS) {
    onTabChange(TRACK_DETAIL_TAB_EVENTS);
  }
  selectedTrackEventKey.value = key;
}

function triggerChartReflow() {
  if (graphHeightReflowFrame !== null) {
    return;
  }

  graphHeightReflowFrame = window.requestAnimationFrame(() => {
    graphHeightReflowFrame = null;
    nextTick(() => {
      window.dispatchEvent(new Event('resize'));
    });
  });
}

function onTabChange(value: string | number) {
  const nextTab = normalizeTrackDetailTab(value);
  const previousTab = activeTab.value;

  if (previousTab !== nextTab) {
    if (previousTab === TRACK_DETAIL_TAB_EVENTS) {
      selectedTrackEventKey.value = null;
    }
    if (previousTab === TRACK_DETAIL_TAB_GRAPHS) {
      clearChartInteraction();
    }
    activeTab.value = nextTab;
  }

  // Trigger chart reflow after the Graphs tab becomes visible (value "1")
  if (nextTab === TRACK_DETAIL_TAB_GRAPHS) {
    triggerChartReflow();
  }
}

function selectedApiXMode(): XMode {
  return xMode.value === 'distance' ? XMode.Distance : XMode.Time;
}

async function reloadChartDetailsForCurrentSettings() {
  const trackId = gpsTrackId.value;
  if (trackId == null) return;

  const currentLoadGeneration = loadGeneration;
  const reloadGeneration = ++chartReloadGeneration;
  try {
    const details = await fetchTrackDetails(trackId, selectedApiXMode(), chartPointCount.value);
    if (currentLoadGeneration !== loadGeneration || reloadGeneration !== chartReloadGeneration) return;

    const nextDetails = Array.isArray(details) ? details : [];
    trackDetails.value = nextDetails;
    setTrackPoints(buildTrackPoints(nextDetails, renderedShapePoints.value));
  } catch (err) {
    console.error('Failed to re-fetch chart series', err);
  }
  triggerChartReflow();
}

async function setXModeValue(mode: 'time' | 'distance') {
  if (xMode.value === mode) return;
  xMode.value = mode;
  setXMode(mode);
  // Re-fetch chart series so the server buckets along the new x-axis
  // (TIME vs DISTANCE) rather than re-projecting time-bucketed data.
  await reloadChartDetailsForCurrentSettings();
}

function setChartPointCountPreference(value: SliderValue | SliderSlideEndEvent): number {
  const nextSliderValue = clampTrackDetailsChartPointSliderValue(sliderValueToNumber(value));
  const nextCount = trackDetailsChartPointSliderValueToCount(nextSliderValue);
  chartPointSliderValue.value = nextSliderValue;
  chartPointCount.value = nextCount;
  writePref(CHART_POINT_COUNT_STORAGE_KEY, String(nextCount));
  return nextCount;
}

function clearScheduledChartPointReload() {
  if (chartPointReloadTimer !== null) {
    window.clearTimeout(chartPointReloadTimer);
    chartPointReloadTimer = null;
  }
}

function onChartPointCountInput(value: SliderValue) {
  setChartPointCountPreference(value);
  clearScheduledChartPointReload();
  chartPointReloadTimer = window.setTimeout(() => {
    chartPointReloadTimer = null;
    void reloadChartDetailsForCurrentSettings();
  }, 350);
}

async function onChartPointCountSlideEnd(event: SliderSlideEndEvent) {
  setChartPointCountPreference(event);
  clearScheduledChartPointReload();
  await reloadChartDetailsForCurrentSettings();
}

async function nudgeChartPointCount(delta: number) {
  setChartPointCountPreference(
    nextDistinctChartPointSliderValue(chartPointSliderValue.value, chartPointCount.value, delta)
  );
  clearScheduledChartPointReload();
  await reloadChartDetailsForCurrentSettings();
}

function toggleRangeBand() {
  showRangeBand.value = !showRangeBand.value;
  writePref(GRAPH_RANGE_BAND_STORAGE_KEY, String(showRangeBand.value));
  triggerChartReflow();
}

// Called only on mouseup/touchend — saves and reflows without disturbing drag.
function onGraphHeightCommit(value: number | number[]) {
  const raw = Array.isArray(value) ? value[0] : value;
  const nextHeight = clampGraphHeight(raw);
  graphHeightPx.value = nextHeight;
  try {
    localStorage.setItem(GRAPH_HEIGHT_STORAGE_KEY, String(nextHeight));
  } catch {
    // Ignore localStorage access failures and keep the in-memory preference.
  }
  triggerChartReflow();
}

function nudgeGraphHeight(delta: number) {
  // Button tap — no ongoing drag, so reflow immediately.
  const nextHeight = clampGraphHeight(graphHeightPx.value + delta);
  graphHeightPx.value = nextHeight;
  try {
    localStorage.setItem(GRAPH_HEIGHT_STORAGE_KEY, String(nextHeight));
  } catch {
    // Ignore localStorage access failures and keep the in-memory preference.
  }
  triggerChartReflow();
}

function buildTrackPoints(details: ChartPoint[], simplifiedPoints: GpsTrackDataPoint[]): TrackPoint[] {
  // Resolve each chart-bucket's representative canonical-stream point
  // index to a physical lat/lng on the rendered SIMPLIFIED_SHAPE via the
  // `canonicalPointIndex` back-pointer populated at ingest (see Phase 7
  // of canonical_metric_lod_architecture.md). Each TrackPoint also carries
  // the exact x-values used by chart buckets, so cursor sync can resolve by
  // bucket identity before falling back to time/distance searches.
  if (!details.length || !simplifiedPoints.length) {
    return [];
  }

  type IndexedSimplified = {
    canonical: number;
    lat: number;
    lng: number;
    altitude: number | null;
    distanceKm: number;
    pointIndex: number;
  };

  const indexed: IndexedSimplified[] = [];
  for (const p of simplifiedPoints) {
    if (p.canonicalPointIndex == null) continue;
    const coords = p.pointLongLat?.coordinates as [number, number] | undefined;
    if (!coords || coords.length < 2) continue;
    indexed.push({
      canonical: p.canonicalPointIndex,
      lng: coords[0],
      lat: coords[1],
      altitude: p.pointAltitude ?? null,
      distanceKm: (p.distanceInMeterSinceStart ?? 0) / 1000,
      pointIndex: p.pointIndex ?? 0,
    });
  }
  if (indexed.length === 0) {
    return [];
  }
  indexed.sort((a, b) => a.canonical - b.canonical);

  const findNearest = (target: number): IndexedSimplified => {
    let lo = 0;
    let hi = indexed.length - 1;
    while (lo < hi) {
      const mid = (lo + hi) >>> 1;
      if (indexed[mid].canonical < target) {
        lo = mid + 1;
      } else {
        hi = mid;
      }
    }
    if (lo > 0 && Math.abs(indexed[lo - 1].canonical - target) <= Math.abs(indexed[lo].canonical - target)) {
      return indexed[lo - 1];
    }
    return indexed[lo];
  };

  const points: TrackPoint[] = [];
  const startTs = details.length > 0 ? details[0].pointTimestamp.getTime() : 0;
  for (const cp of details) {
    const match = findNearest(cp.pointIndex);
    const timestamp = cp.pointTimestamp.getTime();
    const distanceKm = (cp.distanceInMeterSinceStart ?? match.distanceKm * 1000) / 1000;
    points.push({
      lat: match.lat,
      lng: match.lng,
      altitude: match.altitude,
      timestamp,
      distanceKm,
      pointIndex: match.pointIndex,
      canonicalPointIndex: cp.pointIndex,
      chartX: {
        time: timestamp - startTs,
        distance: distanceKm,
      },
    });
  }
  return points;
}

function loadTrackShape(trackId: number): Promise<TrackPrecisionResult> {
  return fetchDetailTrackAtPrecision(trackId, DETAIL_TRACK_PRECISION);
}

function extractTrackEvents(track: GpsTrack): GpsTrackEvent[] {
  const trackDataList = track.gpsTracksData as GpsTrackData[] | undefined;
  const events = trackDataList?.flatMap((trackData) => trackData.gpsTrackEvents ?? []) ?? [];
  return events.map((event) => ({
    ...event,
    startTimestamp: event.startTimestamp ? new Date(event.startTimestamp) : undefined,
    endTimestamp: event.endTimestamp ? new Date(event.endTimestamp) : undefined,
    createDate: event.createDate ? new Date(event.createDate) : undefined,
    updateDate: event.updateDate ? new Date(event.updateDate) : undefined,
  }));
}

/**
 * Load all data needed to render the Track Details panel.
 *
 * DESIGN NOTE — why we use chart-series buckets (not RAW_OUTLIER_CLEANED):
 *
 * The authoritative track-level totals — total energy (energyNetTotalWh),
 * avg/max power (powerWattsAvg/Max), track length, ascent/descent, etc. —
 * are pre-computed on the GpsTrack entity at import time from the
 * RAW_OUTLIER_CLEANED variant (see EnergyService.recalculateEnergyForTrack
 * on the server). Those numbers are already full-precision and stable.
 *
 * For the chart data we use on this screen (graphs, cursor sync and
 * computeSummary values in Overview), chart-series bucketing is:
 *   • uniform over the selected x-axis, so chart spacing remains faithful
 *   • much smaller than RAW for typical 1 Hz GPS tracks
 *   • populated with precomputed derived fields such as 30 s display power
 *
 * So: lightweight render data here, authoritative aggregates from the entity.
 * If a “high precision” mode is ever needed, it should be an explicit
 * user-toggled option, not the default.
 */
async function load(trackId: number) {
  console.log('load gpsTrack details for id=' + trackId);
  // Generation guard: if the user navigates to another track while this
  // load is in flight, discard the stale result.
  const generation = ++loadGeneration;
  chartReloadGeneration++;
  isLoading.value = true;
  clearAll();
  gpsTrack.value = null;
  relatedTracks.value = null;
  trackDetails.value = [];
  trackEvents.value = [];
  miniMapCoordinates.value = [];
  renderedShapePoints.value = [];
  selectedTrackEventKey.value = null;
  try {
    const trackPromise = loadTrackShape(trackId);
    const relatedTracksPromise = settle(getRelatedTracks(trackId));
    const detailsPromise = settle(fetchTrackDetails(trackId, selectedApiXMode(), chartPointCount.value));
    // SIMPLIFIED_SHAPE per-point rows carry a `canonicalPointIndex`
    // back-pointer (Phase 7 of the canonical-LOD architecture). We use
    // it to resolve chart bucket representativePointIndex (canonical
    // stream) → SIMPLIFIED_SHAPE lat/lng for chart→map cursor sync.
    const shapePointsPromise = settle(fetchTrackPointsForRenderedShape(trackId, DETAIL_TRACK_PRECISION));

    const detailTrack = await trackPromise;
    if (generation !== loadGeneration) return;

    const track = detailTrack.gpsTrack;
    gpsTrack.value = track;
    trackEvents.value = extractTrackEvents(track);
    miniMapCoordinates.value = detailTrack.coordinates;

    emit('track-loaded', {
      id: track.id,
      name: track.trackName || track.metaName || '',
      description: track.trackDescription || track.metaDescription || '',
      activityType: track.activityType,
    });

    const details = unwrapSettled(await detailsPromise);
    if (generation !== loadGeneration) return;

    trackDetails.value = Array.isArray(details) ? details : [];

    // Wait for SIMPLIFIED_SHAPE points before publishing TrackPoints to
    // the cursor-sync store; the chart can render without them, but the
    // map cursor sync needs them to know where each chart bucket is.
    const shapePointsResult = await shapePointsPromise;
    if (generation !== loadGeneration) return;
    const shapePoints: GpsTrackDataPoint[] = shapePointsResult.status === 'fulfilled' ? shapePointsResult.value : [];
    renderedShapePoints.value = shapePoints;

    if (Array.isArray(details)) {
      setTrackPoints(buildTrackPoints(details, shapePoints));
    }

    const nextRelatedTracks = unwrapSettled(await relatedTracksPromise);
    if (generation !== loadGeneration) return;

    relatedTracks.value = nextRelatedTracks;
  } finally {
    if (generation === loadGeneration) {
      isLoading.value = false;
    }
  }
}

function navigateToTrack(trackId: number) {
  gpsTrackId.value = trackId;
  void load(trackId);
}

function onTrackUpdated(track: GpsTrack) {
  gpsTrack.value = track;
}
</script>

<style scoped>
/*
 * IMPORTANT: BottomSheet + Teleport + scoped CSS scrolling pattern.
 *
 * .tool-container is the wrapper div required for :deep() selectors to work
 * inside a BottomSheet (which uses <Teleport to="body">). Without this wrapper,
 * scoped selectors like :deep(.p-tabs) have no ancestor carrying data-v-xxx
 * and won't match. See BottomSheet.vue comment about the neutral body contract.
 */

.tool-container {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  flex: 1 1 auto;
  width: 100%;
  min-height: 0;
  overflow: hidden;
}

/* Tabs fill the remaining space below the mini-map.
   TabList stays pinned; only TabPanels scroll. */
:deep(.p-tabs) {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 0;
}

:deep(.p-tablist) {
  flex: 0 0 auto;
  z-index: 2;
  background: var(--surface-glass);
  backdrop-filter: var(--blur-standard);
  -webkit-backdrop-filter: var(--blur-standard);
}

:deep(.p-tabpanels) {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  overscroll-behavior-y: contain;
}

:deep(.p-tabpanel) {
  min-height: 0;
}

.graphs-toolbar {
  display: grid;
  grid-template-columns: minmax(13rem, 0.85fr) minmax(9rem, 0.45fr) minmax(17rem, 1fr) minmax(17rem, 1fr);
  align-items: stretch;
  gap: 0;
  margin: 0.75rem 0 0;
}

.graphs-toolbar-section {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 0.55rem;
  min-width: 0;
  padding: 0.75rem 1.25rem;
}

.graphs-axis-section {
  min-width: 0;
}

.graphs-range-section {
  min-width: 0;
}

.graphs-range-band-section {
  min-width: 0;
}

.graphs-toolbar-label {
  display: inline-flex;
  align-items: baseline;
  gap: 0.35rem;
  font-size: var(--text-2xs-size);
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text-muted);
  white-space: nowrap;
}

.graphs-toolbar-value {
  color: var(--text-secondary);
  font-variant-numeric: tabular-nums;
  letter-spacing: 0;
}

.graphs-toggle {
  display: flex;
  width: 100%;
  min-width: 0;
  background: var(--surface-elevated);
  border: 1px solid var(--border-default);
  border-radius: 8px;
  padding: 3px;
  gap: 2px;
}

.graphs-toggle--single .toggle-btn {
  flex: 1 1 auto;
}

.toggle-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1 1 0;
  min-width: 0;
  gap: 0.3rem;
  padding: 0.28rem 0.65rem;
  border: none;
  background: none;
  border-radius: 5px;
  font-size: var(--text-xs-size);
  font-weight: 600;
  color: var(--text-secondary);
  cursor: pointer;
  transition:
    background 0.15s,
    color 0.15s;
}

.toggle-btn:hover {
  background: var(--surface-glass);
}

.toggle-btn--active {
  background: var(--surface-glass-heavy);
  color: var(--accent-text);
  box-shadow: var(--shadow-sm);
}

.graphs-slider-shell {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  width: 100%;
  min-width: 0;
  padding: 0.18rem 0.3rem;
  background: var(--surface-elevated);
  border: 1px solid var(--border-default);
  border-radius: 999px;
}

.graphs-slider-icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.9rem;
  height: 1.9rem;
  border: none;
  border-radius: 999px;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  transition:
    background 0.15s,
    color 0.15s,
    opacity 0.15s;
}

.graphs-slider-icon-btn:hover:not(:disabled) {
  background: var(--surface-glass);
  color: var(--accent-text);
}

.graphs-slider-icon-btn:disabled {
  opacity: 0.45;
  cursor: default;
}

.graphs-height-slider,
.graphs-count-slider {
  flex: 1 1 auto;
  min-width: 0;
}

.graphs-slider-shell :deep(.p-slider) {
  background: var(--slider-track);
  border-radius: 999px;
}

.graphs-slider-shell :deep(.p-slider-range) {
  background: var(--slider-gradient);
}

.graphs-slider-shell :deep(.p-slider-handle) {
  box-shadow: 0 0 0 0 var(--accent-bg);
  transition: box-shadow 0.15s ease;
}

.graphs-slider-shell :deep(.p-slider-handle:hover),
.graphs-slider-shell :deep(.p-slider-handle:focus-visible) {
  box-shadow: 0 0 0 6px var(--accent-bg);
}

@media (max-width: 820px) {
  .graphs-toolbar {
    grid-template-columns: 1fr;
    margin-inline: 0;
  }

  .graphs-toolbar-section {
    padding: 0.75rem;
  }

  .graphs-toolbar-label {
    min-width: 0;
  }

  .graphs-slider-shell {
    min-width: 0;
  }
}
</style>
