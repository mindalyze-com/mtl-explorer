<template>
  <div
    ref="workspaceScroll"
    class="sc-workspace"
    data-sheet-fit-target
    :class="{ 'sc-workspace--inspecting': !!focusedEntry }"
  >
    <p v-if="syntheticHealthData" class="workspace-note" role="status">
      Synthetic demo heart rate. Values come from one shared fictional timeline.
    </p>
    <div class="sc-working">
      <details class="sc-roster">
        <summary>
          {{ data.length }} {{ data.length === 1 ? 'attempt' : 'attempts' }} · Reference:
          {{ referenceName || 'Unavailable' }}
        </summary>
        <div v-for="entry in data" :key="entry.attempt.identity.reference" class="sc-roster-entry">
          <button @click="focused = entry.attempt.identity.reference">
            <SegmentReferenceMark v-if="reference === entry.attempt.identity.reference" :color="color(entry)" />
            <span v-else :style="{ color: color(entry) }" aria-hidden="true">●</span>
            <ActivityTypeBadge :type="entry.track?.activityType" size="xs" />
            {{ trackName(entry.track, entry.attempt.identity.trackId) }} · {{ visitLabel(entry.attempt) }}
          </button>
          <button :aria-label="'Remove ' + visitLabel(entry.attempt)" @click="removeAttempt(entry)">Remove</button>
        </div>
        <label
          >Reference attempt<select
            :value="reference ?? ''"
            @change="chooseReference(($event.target as HTMLSelectElement).value)"
          >
            <option value="" disabled>Unavailable</option>
            <option
              v-for="entry in validReferences"
              :key="entry.attempt.identity.reference"
              :value="entry.attempt.identity.reference"
            >
              {{ trackName(entry.track, entry.attempt.identity.trackId) }} · {{ visitLabel(entry.attempt) }}
            </option>
          </select></label
        >
        <Button size="small" outlined @click="emit('goto-table')">Choose more attempts</Button>
      </details>
      <div class="sc-toolbar">
        <div class="sc-view-toggle" role="group" aria-label="Comparison view">
          <button :aria-pressed="view === 'charts'" @click="view = 'charts'">Charts</button
          ><button
            :aria-pressed="view === 'replay'"
            :disabled="!canReplay"
            :title="canReplay ? undefined : SEGMENT_REPLAY_UNAVAILABLE_HINT"
            @click="openReplay()"
          >
            Replay
          </button>
        </div>
        <button
          v-if="view === 'charts'"
          class="sc-ghost-toggle"
          :aria-pressed="ghostsEnabled"
          title="Show where each attempt was when the reference reached this distance"
          @click="ghostsEnabled = !ghostsEnabled"
        >
          <i class="bi bi-transparency" aria-hidden="true" /> Ghosts
        </button>
        <label v-if="view === 'charts'"
          >X-axis<select v-model="xMode">
            <option v-for="axis in AXES" :key="axis" :value="axis">
              {{ axis === 'distance' ? 'Distance traveled' : 'Elapsed time' }}
            </option>
          </select></label
        >
        <label v-if="view === 'charts'" class="sc-add-chart"
          ><span class="sc-visually-hidden">Add chart</span
          ><select
            value=""
            aria-label="Add chart"
            @change="
              toggleMetric(($event.target as HTMLSelectElement).value);
              ($event.target as HTMLSelectElement).value = '';
            "
          >
            <option value="" disabled>+ Add chart</option>
            <option
              v-for="metric in METRICS.filter((m) => !selectedMetrics.has(m.key))"
              :key="metric.key"
              :value="metric.key"
            >
              {{ metric.label }}
            </option>
          </select></label
        >
        <button
          v-if="view === 'charts'"
          class="sc-graph-settings-toggle"
          :aria-expanded="graphSettingsOpen"
          aria-controls="comparison-graph-settings"
          @click="graphSettingsOpen = !graphSettingsOpen"
        >
          <i class="bi bi-sliders2" aria-hidden="true" /> Graph settings
        </button>
      </div>
      <div v-if="view === 'charts' && graphSettingsOpen" id="comparison-graph-settings" class="sc-graph-settings">
        <GraphPointCountControl v-model="chartPointCount" />
        <div class="sc-graph-height">
          <span
            >Height <strong>{{ graphHeightPx }} px</strong></span
          >
          <div class="sc-graph-height-slider">
            <button
              type="button"
              :disabled="graphHeightPx <= TRACK_DETAIL_GRAPH_HEIGHT_MIN"
              aria-label="Make graphs smaller"
              @click="graphPreferences.setGraphHeight(graphHeightPx - TRACK_DETAIL_GRAPH_HEIGHT_STEP)"
            >
              <i class="bi bi-arrows-collapse-vertical" aria-hidden="true" />
            </button>
            <MtlSlider
              :model-value="graphHeightPx"
              :min="TRACK_DETAIL_GRAPH_HEIGHT_MIN"
              :max="TRACK_DETAIL_GRAPH_HEIGHT_MAX"
              :step="TRACK_DETAIL_GRAPH_HEIGHT_STEP"
              aria-label="Adjust graph height"
              :aria-value-text="`${graphHeightPx} pixels`"
              @change="setGraphHeight"
            />
            <button
              type="button"
              :disabled="graphHeightPx >= TRACK_DETAIL_GRAPH_HEIGHT_MAX"
              aria-label="Make graphs bigger"
              @click="graphPreferences.setGraphHeight(graphHeightPx + TRACK_DETAIL_GRAPH_HEIGHT_STEP)"
            >
              <i class="bi bi-arrows-expand-vertical" aria-hidden="true" />
            </button>
          </div>
        </div>
      </div>
      <div
        v-for="entry in data.filter((d) => d.loading || d.error)"
        :key="entry.attempt.identity.reference"
        role="status"
        class="sc-request-error"
      >
        {{ trackName(entry.track, entry.attempt.identity.trackId) }} · {{ visitLabel(entry.attempt) }}:
        {{ entry.loading ? 'Loading attempt…' : entry.error }}
        <template v-if="entry.error"
          ><button @click="loadCore(entry)">Retry attempt</button
          ><button @click="emit('refresh-results')">Refresh results</button></template
        >
      </div>
      <div
        v-for="entry in data.filter((d) => d.healthError)"
        :key="'health-' + entry.attempt.identity.reference"
        role="status"
        class="sc-request-error"
      >
        {{ trackName(entry.track, entry.attempt.identity.trackId) }} · {{ visitLabel(entry.attempt) }}:
        {{ entry.healthError }} <Button size="small" outlined @click="loadHealth(entry)">Retry health</Button>
      </div>
      <div v-if="view === 'charts'" class="sc-analysis-layout">
        <div ref="mobileMapTarget" class="sc-mobile-map" />
        <div ref="chartRegion" class="sc-chart-region" tabindex="-1">
          <div class="sc-chart-tools">
            <p class="sc-warning">
              {{
                positionPinned
                  ? 'Position pinned. Click, tap, or swipe across a chart to move it.'
                  : 'Move over a chart or swipe across it to inspect. Click or tap to pin.'
              }}
            </p>
            <button v-if="hover" class="sc-view-values" @click="revealValues">View values</button>
          </div>
          <div class="sc-charts">
            <div v-for="metric in visibleMetrics" :key="metric.key">
              <ComparisonChart
                :title="metric.label"
                :subtitle="metric.subtitle ?? ''"
                :series="seriesByMetric[metric.key] ?? []"
                :x-mode="metricAxis(metric.key)"
                :x-max="axisMaxima[metricAxis(metric.key)]"
                :unit="metric.unit"
                :measurement-dimension="metric.dimension"
                :decimals="metric.decimals ?? 1"
                :y-zero-line="DISTANCE_ONLY_METRICS.has(metric.key)"
                :height="graphHeightPx"
                :active-attempt="activeAttempt"
                :reference-attempt="reference"
                :ghosts="
                  metricAxis(metric.key) === 'distance' && !DISTANCE_ONLY_METRICS.has(metric.key) ? chartGhosts : []
                "
                :linked-hover="hover"
                @hover-x="previewPosition"
                @scrub-position="pinPosition($event)"
                @pin-position="pinPosition($event, true)"
              >
                <template #actions>
                  <button
                    v-if="visibleMetrics.length > 1"
                    class="sc-remove-chart"
                    :aria-label="'Remove ' + metric.label + ' chart'"
                    @click="toggleMetric(metric.key)"
                  >
                    <i class="bi bi-x-lg" aria-hidden="true" />
                  </button>
                </template>
              </ComparisonChart>
              <p v-if="metric.key === 'gap' && comparisonsLoading" class="sc-warning" role="status">
                Loading time comparisons… {{ completedComparisons }} of {{ data.length }} ready.
              </p>
              <p v-if="available.length && !hasMetricValues(metric.key)" class="sc-warning">
                No supported data for this metric.
              </p>
            </div>
          </div>
        </div>
        <aside ref="inspectionRail" class="sc-inspection-rail">
          <SegmentInspectionPanel
            :entries="data"
            :colors="palette"
            :reference="reference"
            :metrics="inspectionMetrics"
            :series="seriesByMetric"
            :position="hover"
            :pinned="positionPinned"
            :active-attempt="activeAttempt"
            :axis="inspectionAxis"
            :min="inspectionRange.min"
            :max="inspectionRange.max"
            :map-geo-json="mapGeoJson"
            :map-bounds="mapBounds"
            :map-target="compactLayout ? mobileMapTarget : null"
            :locator-label="locatorLabel"
            :locator-reference-color="locatorEntry && locatorEntry === referenceEntry ? color(locatorEntry) : null"
            :locator-status="locatorStatus"
            :ghosts="ghostSummaries"
            :replay-unavailable-reason="replayHereUnavailableReason"
            @pin-position="pinPosition($event)"
            @update:pinned="positionPinned = $event"
            @update:active-attempt="activeAttempt = $event"
            @back-to-charts="revealCharts"
            @open-replay="openReplayHere"
          />
        </aside>
      </div>
      <SegmentReplay v-else :entries="data" :colors="palette" :initial-elapsed-seconds="replayInitialElapsedSeconds" />
      <div class="sc-summary-heading">
        <h3>Comparison</h3>
        <button :aria-expanded="columnsOpen" @click="columnsOpen = !columnsOpen">Summary metrics</button>
      </div>
      <div v-if="columnsOpen" class="sc-column-chooser">
        <label v-for="column in SUMMARY_COLUMNS" :key="column"
          ><input v-model="extraColumns" type="checkbox" :value="column" />{{ column }}</label
        >
      </div>
      <div class="sc-summary" role="table" aria-label="Attempt comparison">
        <div v-for="entry in data" :key="entry.attempt.identity.reference" class="sc-summary-row" role="row">
          <button class="sc-summary-identity" @click="focused = entry.attempt.identity.reference">
            <SegmentReferenceMark v-if="reference === entry.attempt.identity.reference" :color="color(entry)" />
            <span v-else :style="{ color: color(entry) }" aria-hidden="true">●</span>
            <ActivityTypeBadge :type="entry.track?.activityType" size="xs" />
            {{ trackName(entry.track, entry.attempt.identity.trackId) }} · {{ visitLabel(entry.attempt)
            }}<small v-if="reference === entry.attempt.identity.reference">Reference attempt</small>
          </button>
          <span class="sc-summary-start" role="cell"
            ><small>Track start</small>{{ formatDateAndTime(entry.track?.startDate) || 'Unavailable' }}</span
          >
          <span role="cell"><small>Elapsed time</small>{{ duration(entry.attempt.elapsedSeconds) }}</span>
          <span role="cell"
            ><small>Average speed</small
            >{{
              entry.attempt.averageSpeedKmh == null ? 'Unavailable' : formatSpeed(entry.attempt.averageSpeedKmh, 1)
            }}</span
          >
          <span role="cell"
            ><small>Distance</small
            >{{
              entry.attempt.distanceMeters == null ? 'Unavailable' : formatDistanceSmart(entry.attempt.distanceMeters)
            }}</span
          >
          <span role="cell"
            ><small>Compared with reference</small>
            <SegmentTimeDifference
              :seconds="entry.comparison?.finishDifferenceSeconds"
              :loading="entry.comparisonLoading"
              :reference="reference === entry.attempt.identity.reference"
            />
          </span>
          <span v-for="column in extraColumns" :key="column" role="cell"
            ><small>{{ column }}</small
            >{{ summaryValue(entry, column) }}</span
          >
          <p v-if="entry.comparisonError" role="status">
            {{ entry.comparisonError }} <Button size="small" outlined @click="compare">Retry comparison</Button>
          </p>
          <p v-if="entry.attempt.availability?.distance?.reason === 'RECORDING_GAP'">
            Recorded path is incomplete. Distance and speed are unavailable.
          </p>
          <p v-if="entry.comparison?.unavailableReason">{{ readable(entry.comparison.unavailableReason) }}</p>
        </div>
      </div>
      <p class="sc-warning">
        Faster and slower compare the total time with the reference attempt, including stops. The same distance can be
        at a different place on the ground.
      </p>
    </div>
    <aside v-if="focusedEntry" class="sc-inspector" aria-label="Attempt details">
      <button class="sc-inspector-back" @click="focused = null">
        ← Back to {{ view === 'charts' ? 'charts' : 'replay' }}
      </button>
      <h3>{{ trackName(focusedEntry.track, focusedEntry.attempt.identity.trackId) }}</h3>
      <p>
        <ActivityTypeBadge :type="focusedEntry.track?.activityType" size="xs" /> {{ visitLabel(focusedEntry.attempt) }}
      </p>
      <MiniMap :tracks-geo-json="mapGeoJson" :map-bounds="mapBounds" class="sc-inspector-map" />
      <dl>
        <dt>Track start</dt>
        <dd>{{ formatDateAndTime(focusedEntry.track?.startDate) || 'Unavailable' }}</dd>
        <template v-for="stat in focusedEntry.core ? stats(focusedEntry) : []" :key="stat.title"
          ><dt>{{ stat.title }}</dt>
          <dd>{{ stat.text }}</dd></template
        >
        <dt>Breaks</dt>
        <dd>{{ summaryValue(focusedEntry, 'Breaks') }}</dd>
        <dt>Stopped time</dt>
        <dd>{{ summaryValue(focusedEntry, 'Stopped time') }}</dd>
        <dt>Longest break</dt>
        <dd>{{ duration(stopNotes(focusedEntry)?.longestStopSec) }}</dd>
      </dl>
      <p>Stopped time includes short pauses. Breaks are detected pauses lasting at least one minute.</p>
      <p v-if="stopNotes(focusedEntry)?.coverageComplete === false">
        Stop measurements cover supported observations only.
      </p>
      <p v-if="focusedEntry.healthLoading" role="status">Loading heart rate…</p>
      <p v-if="focusedEntry.healthError" role="status">
        {{ focusedEntry.healthError }}
        <Button size="small" outlined @click="loadHealth(focusedEntry)">Retry health</Button>
      </p>
      <p v-if="focusedEntry.health?.availability">
        Heart rate: {{ readable(focusedEntry.health.availability.state) }} ·
        {{ summaryValue(focusedEntry, 'HR coverage') }} coverage
      </p>
      <SegmentCardiacCost
        :summary="focusedEntry.health?.cardiacCost"
        :loading="focusedEntry.healthLoading"
        :load-failed="!!focusedEntry.healthError"
        @retry="loadHealth(focusedEntry)"
      />
      <dl>
        <template v-for="(availability, metric) in focusedEntry.core?.summary?.availability" :key="metric"
          ><dt>{{ metric }}</dt>
          <dd>
            {{ readable(availability.state)
            }}<template v-if="availability.reason"> · {{ readable(availability.reason) }}</template>
          </dd></template
        >
      </dl>
      <p v-if="focusedEntry.attempt.identity.timingStatus !== 'AVAILABLE'">
        Timing: {{ readable(focusedEntry.attempt.identity.timingStatus) }}
      </p>
      <button @click="emit('show-track-details', focusedEntry.attempt.identity.trackId)">Open full track</button>
    </aside>
  </div>
</template>

<script setup lang="ts">
import { useDemoStatus } from '@/composables/useDemoStatus';
const { syntheticHealthData } = useDemoStatus();
import Button from 'primevue/button';
import GraphPointCountControl from '@/components/ui/GraphPointCountControl.vue';
import MtlSlider from '@/components/ui/MtlSlider.vue';
import {
  useTrackDetailsPreferencesStore,
  TRACK_DETAIL_GRAPH_HEIGHT_MIN,
  TRACK_DETAIL_GRAPH_HEIGHT_MAX,
  TRACK_DETAIL_GRAPH_HEIGHT_STEP,
} from '@/stores/trackDetailsPreferencesStore';
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { Marker, type Map as MapLibreMap } from 'maplibre-gl';
import type {
  CrossingPointsResponseDto,
  GpsTrack,
  SegmentAnalysisPoint,
  SegmentGhostPoint,
} from 'x8ing-mtl-api-typescript-fetch';
import ComparisonChart from './ComparisonChart.vue';
import SegmentInspectionPanel from './SegmentInspectionPanel.vue';
import SegmentTimeDifference from './SegmentTimeDifference.vue';
import ActivityTypeBadge from '@/components/ui/ActivityTypeBadge.vue';
import SegmentCardiacCost from './SegmentCardiacCost.vue';
import SegmentReferenceMark from './SegmentReferenceMark.vue';
import MiniMap from '@/components/map/MiniMap.vue';
import { MINI_MAP_DIAMOND_SHAPE } from '@/components/map/miniMapDiamondIcon';
import type { MiniMapBounds, MiniMapGeoJson } from '@/components/map/useMiniMap';
import SegmentReplay from './SegmentReplay.vue';
import { DEFAULT_COMPARISON_METRICS, timingSupported, type AnalysisSession } from './useAnalysisSession';
import {
  generateColors,
  formatDateAndTime,
  formatDuration,
  formatSpeed,
  formatDistanceSmart,
  formatElevation,
  formatNumber,
} from '@/utils/Utils';
import type { ChartMeasurementDimension } from '@/utils/chartTheme';
import { formatSegmentHeartRate, SEGMENT_HEART_RATE_DESCRIPTION } from './segmentHeartRateSummary';
import { segmentSelectionKey, type SegmentSelectionCode } from './segmentSlice';
import {
  segmentChartX,
  segmentGeometrySlice,
  segmentGhostAt,
  segmentHoverPosition,
  segmentHoverSample,
  segmentTrackName,
  segmentStatusLabel as readable,
  SEGMENT_REPLAY_UNAVAILABLE_HINT,
  type SegmentAxis,
  type SegmentHover,
  type SegmentChartSeries,
} from './segmentDisplay';
import { useSegmentAttempts, type Attempt, type AttemptData } from './useSegmentAttempts';
import { observeElementResize } from '@/utils/observeElementResize';
import { formatShortDistanceM } from '@/utils/units';
import { useMeasurementSystem } from '@/composables/useMeasurementSystem';

defineOptions({ name: 'SegmentCompare' });
const props = withDefaults(
  defineProps<{
    map?: MapLibreMap;
    measureServiceResult: CrossingPointsResponseDto;
    selectedTrackIds: Set<number>;
    selectedAttempts: Attempt[];
    referenceAttempt?: string | null;
    session?: AnalysisSession;
    selectedSegment?: SegmentSelectionCode | null;
    availableSegments?: Array<{ name?: string; code: SegmentSelectionCode }>;
    consolidateVisits?: boolean;
  }>(),
  {
    map: undefined,
    availableSegments: () => [],
    selectedSegment: null,
    consolidateVisits: true,
    referenceAttempt: undefined,
    session: undefined,
  }
);
const emit = defineEmits<{
  'update:selectedSegment': [segment: SegmentSelectionCode];
  'show-track-details': [id: number | string];
  'goto-table': [];
  'refresh-results': [];
}>();
const { measurementSystem } = useMeasurementSystem();
const AXES: SegmentAxis[] = ['distance', 'time'];
const localAxis = ref<SegmentAxis>('distance');
const xMode = computed({
  get: () => props.session?.state.chartAxis ?? localAxis.value,
  set: (value) => {
    localAxis.value = value;
    props.session?.updateComparison({ chartAxis: value });
  },
});
const graphSettingsOpen = ref(false);
const graphPreferences = useTrackDetailsPreferencesStore();
const graphHeightPx = computed(() => graphPreferences.graphHeightPx);
function setGraphHeight(value: number | number[]) {
  graphPreferences.setGraphHeight(Array.isArray(value) ? value[0] : value);
}
const chartPointCount = computed({
  get: () => graphPreferences.chartPointCount,
  set: (value: number) => {
    graphPreferences.setChartPointCount(value);
  },
});
const graphSettings = computed(() => ({ pointCount: chartPointCount.value, axis: xMode.value }));
const { data, available, validReferences, reference, setReference, loadCore, loadHealth, compare } = useSegmentAttempts(
  props,
  graphSettings
);
const hover = ref<SegmentHover | null>(null);
const positionPinned = ref(false);
const activeAttempt = ref<string | null>(null);
const workspaceScroll = ref<HTMLElement | null>(null);
const inspectionRail = ref<HTMLElement | null>(null);
const chartRegion = ref<HTMLElement | null>(null);
const mobileMapTarget = ref<HTMLElement | null>(null);
const compactLayout = ref(false);
const INSPECTION_RAIL_MIN_WIDTH = 840;
let stopObservingLayout: (() => void) | undefined;
onMounted(() => {
  const working = workspaceScroll.value?.querySelector('.sc-working');
  if (!working || typeof ResizeObserver === 'undefined') return;
  compactLayout.value = working.clientWidth < INSPECTION_RAIL_MIN_WIDTH;
  stopObservingLayout = observeElementResize(working, (entry) => {
    compactLayout.value = entry.contentRect.width < INSPECTION_RAIL_MIN_WIDTH;
  });
});
onBeforeUnmount(() => stopObservingLayout?.());
function previewPosition(position: SegmentHover) {
  if (!positionPinned.value) hover.value = position;
}
async function pinPosition(position: SegmentHover, reveal = false) {
  hover.value = position;
  positionPinned.value = true;
  await nextTick();
  if (
    reveal &&
    inspectionRail.value &&
    chartRegion.value &&
    inspectionRail.value.getBoundingClientRect().left < chartRegion.value.getBoundingClientRect().right
  )
    revealValues();
}
function revealValues() {
  scrollWithinWorkspace(inspectionRail.value);
  inspectionRail.value?.querySelector('h3')?.focus({ preventScroll: true });
}
function revealCharts() {
  scrollWithinWorkspace(chartRegion.value);
  chartRegion.value?.focus({ preventScroll: true });
}
function scrollWithinWorkspace(target: HTMLElement | null) {
  const scroll = workspaceScroll.value;
  if (!scroll || !target) return;
  const map = compactLayout.value ? mobileMapTarget.value : null;
  const mapHeight = map?.getBoundingClientRect().height ?? 0;
  const mapGap = map?.parentElement ? parseFloat(getComputedStyle(map.parentElement).rowGap) || 0 : 0;
  // scrollIntoView also scrolls the sheet's clipped frame, hiding the workspace navigation.
  // Keep the destination below the sticky map, whether it is expanded or collapsed.
  scroll.scrollTo({
    top: Math.max(
      0,
      scroll.scrollTop + target.getBoundingClientRect().top - scroll.getBoundingClientRect().top - mapHeight - mapGap
    ),
    behavior: 'smooth',
  });
}
// Every metric uses the selected attempts' full extent, including unsupported sample coverage.
const axisMaxima = computed(() => {
  const maxima = { distance: 0, time: 0 };
  for (const entry of data.value)
    for (const point of [entry.attempt, entry.core?.summary, ...(entry.core?.geometry ?? [])]) {
      if (!point) continue;
      for (const axis of AXES) maxima[axis] = Math.max(maxima[axis], segmentChartX(point, axis) ?? 0);
    }
  return maxima;
});
const selectedSegmentKey = computed(() =>
  props.selectedSegment ? segmentSelectionKey({ code: props.selectedSegment }) : ''
);
type Metric = {
  key: string;
  label: string;
  field?: keyof SegmentAnalysisPoint;
  dimension?: ChartMeasurementDimension;
  unit?: string;
  subtitle?: string;
  decimals?: number;
};
const METRICS: Metric[] = [
  {
    key: 'gap',
    label: 'Time difference at equal traveled distance',
    unit: 's',
    subtitle:
      'Time ahead or behind the reference at the same distance, including stops. Ends where an attempt’s data ends.',
  },
  {
    key: 'lead',
    label: 'Distance ahead at the reference’s time',
    dimension: 'shortDistance',
    decimals: 0,
    subtitle:
      'How much farther each attempt had traveled when the reference reached this distance. Negative means behind.',
  },
  { key: 'speed', label: 'Speed', field: 'speedKmh', dimension: 'speed' },
  { key: 'altitude', label: 'Altitude', field: 'altitudeMeters', dimension: 'elevation' },
  { key: 'power', label: 'Est. Power', field: 'powerWatts', unit: 'W', decimals: 0 },
  {
    key: 'energy',
    label: 'Mechanical energy',
    field: 'energyWh',
    unit: 'Wh',
    subtitle: 'Added up from the start of the segment. The line ends where energy can’t be estimated.',
  },
  { key: 'slope', label: 'Slope', field: 'slopePercent', unit: '%' },
  { key: 'elapsed', label: 'Elapsed time', field: 'elapsedSeconds', unit: 's' },
  {
    key: 'heartRate',
    label: 'Heart rate',
    unit: 'bpm',
    decimals: 0,
    subtitle: 'Recorded sample averages. Gaps indicate unsupported coverage.',
  },
];
const localMetrics = ref(new Set(DEFAULT_COMPARISON_METRICS));
const selectedMetrics = computed({
  get: () => (props.session ? new Set(props.session.state.chartMetrics) : localMetrics.value),
  set: (value) => {
    localMetrics.value = value;
    props.session?.updateComparison({ chartMetrics: [...value] });
  },
});
const visibleMetrics = computed(() => METRICS.filter((m) => selectedMetrics.value.has(m.key)));
const inspectionAxis = computed(() => hover.value?.axis ?? xMode.value);
const inspectionRange = computed(() => ({
  min: 0,
  max: axisMaxima.value[inspectionAxis.value],
}));
watch(inspectionRange, ({ min, max }) => {
  if (!hover.value) return;
  const x = Math.max(min, Math.min(max, hover.value.x));
  if (x !== hover.value.x) hover.value = { ...hover.value, x };
});
const inspectionMetrics = computed(() =>
  visibleMetrics.value.map((metric) => ({
    ...metric,
    axis: metricAxis(metric.key),
  }))
);
// Reference-relative comparisons are defined along the reference's traveled distance.
const DISTANCE_ONLY_METRICS = new Set(['gap', 'lead']);
function metricAxis(key: string): SegmentAxis {
  return DISTANCE_ONLY_METRICS.has(key) ? 'distance' : xMode.value;
}
function toggleMetric(key: string) {
  const next = new Set(selectedMetrics.value);
  if (next.has(key) && next.size > 1) next.delete(key);
  else next.add(key);
  selectedMetrics.value = next;
}
function trackName(track: GpsTrack | undefined, id: number) {
  return segmentTrackName(track, id);
}
function visitLabel(attempt: Attempt) {
  const i = attempt.identity;
  return `${i.fromZone}${i.fromVisit} → ${i.toZone}${i.toVisit}`;
}
function duration(seconds: number | null | undefined) {
  return seconds == null ? 'Time unavailable' : formatDuration(seconds * 1000);
}
const palette = computed(() => generateColors(data.value.length));
function color(entry: AttemptData) {
  return palette.value[
    Math.max(
      0,
      data.value.findIndex((d) => d.attempt.identity.reference === entry.attempt.identity.reference)
    )
  ];
}
const referenceName = computed(() => {
  const entry = data.value.find((d) => d.attempt.identity.reference === reference.value);
  return entry ? `${trackName(entry.track, entry.attempt.identity.trackId)} · ${visitLabel(entry.attempt)}` : '';
});
const comparisonsLoading = computed(() => data.value.some((entry) => entry.loading || entry.comparisonLoading));
const completedComparisons = computed(() => data.value.filter((entry) => entry.comparison).length);
function stats(entry: AttemptData) {
  const s = entry.core!.summary!;
  return [
    { icon: 'bi-stopwatch', text: duration(s.elapsedSeconds), title: 'Segment duration, including stops' },
    {
      icon: 'bi-speedometer',
      text: s.averageSpeedKmh == null ? 'Speed unavailable' : formatSpeed(s.averageSpeedKmh, 1),
      title: 'Average speed',
    },
    {
      icon: 'bi-signpost-split',
      text: s.distanceMeters == null ? 'Distance unavailable' : formatDistanceSmart(s.distanceMeters),
      title: 'Segment distance',
    },
    {
      icon: 'bi-arrow-up',
      text: s.ascentMeters == null ? 'Ascent unavailable' : formatElevation(s.ascentMeters),
      title: 'Ascent',
    },
    {
      icon: 'bi-arrow-down',
      text: s.descentMeters == null ? 'Descent unavailable' : formatElevation(s.descentMeters),
      title: 'Descent',
    },
    {
      icon: 'bi-battery-charging',
      text:
        s.energyWh == null
          ? s.availability?.energy?.state === 'PARTIAL'
            ? 'Energy incomplete'
            : 'Energy unavailable'
          : `${formatNumber(s.energyWh, 1)} Wh`,
      title: 'Estimated mechanical energy',
    },
    {
      icon: 'bi-lightning-charge',
      text: s.maximumPowerWatts == null ? 'Power unavailable' : `${formatNumber(s.maximumPowerWatts, 0)} W`,
      title: 'Maximum estimated power over available samples',
    },
    {
      icon: 'bi-heart-pulse',
      text: `Avg HR · ${formatSegmentHeartRate(entry.health?.heartRate)}`,
      title: SEGMENT_HEART_RATE_DESCRIPTION,
    },
  ];
}
const localView = ref<'charts' | 'replay'>('charts');
const view = computed({
  get: () => props.session?.state.comparisonView ?? localView.value,
  set: (value) => {
    localView.value = value;
    props.session?.updateComparison({ comparisonView: value });
  },
});
const localFocused = ref<string | null>(null);
const focused = computed({
  get: () => props.session?.state.focusedAttempt ?? localFocused.value,
  set: (value) => {
    localFocused.value = value;
    props.session?.updateComparison({ focusedAttempt: value });
  },
});
const focusedEntry = computed(() => data.value.find((d) => d.attempt.identity.reference === focused.value));
const canReplay = computed(
  () =>
    data.value.length > 0 &&
    data.value.every(
      (d) => d.core?.geometry?.length && !d.error && timingSupported(d.attempt) && d.attempt.distanceMeters != null
    ) &&
    data.value.some((d) => (d.attempt.elapsedSeconds ?? 0) > 0)
);
const replayInitialElapsedSeconds = ref(0);
const localGhosts = ref(true);
const ghostsEnabled = computed({
  get: () => props.session?.state.ghosts ?? localGhosts.value,
  set: (value) => {
    localGhosts.value = value;
    props.session?.updateComparison({ ghosts: value });
  },
});
const referenceEntry = computed(() => data.value.find((entry) => entry.attempt.identity.reference === reference.value));
// With ghosts, R stays anchored and highlighting emphasizes that attempt's ghost instead of moving the locator.
const locatorEntry = computed(() =>
  activeAttempt.value && !ghostsEnabled.value
    ? data.value.find((entry) => entry.attempt.identity.reference === activeAttempt.value)
    : referenceEntry.value
);
function locatorRole(entry: AttemptData) {
  return entry.attempt.identity.reference === reference.value ? 'Reference' : 'Highlighted attempt';
}
function locatorIdentity(entry: AttemptData | undefined) {
  if (!entry) return 'Reference unavailable';
  return `${locatorRole(entry)} · ${trackName(entry.track, entry.attempt.identity.trackId)} · ${visitLabel(entry.attempt)}`;
}
const locatorLabel = computed(() => locatorIdentity(locatorEntry.value));
const METERS_PER_KILOMETER = 1_000;
const MILLISECONDS_PER_SECOND = 1_000;
function inspectDistance(entry: AttemptData | undefined) {
  let sample: ReturnType<typeof segmentHoverSample> = null;
  const unavailable = (status: string) => ({ sample, status });
  if (!entry) return unavailable('Reference unavailable.');
  const role = locatorRole(entry);
  if (entry.loading) return unavailable(`Loading ${role.toLowerCase()} route…`);
  if (entry.error) return unavailable(`${role} route geometry unavailable.`);
  if (!hover.value || hover.value.axis !== 'distance') return { sample, status: null };
  const distance = entry.attempt.distanceMeters;
  if (distance != null && hover.value.x > distance / METERS_PER_KILOMETER)
    return unavailable(`${role} ends at ${formatDistanceSmart(distance)}.`);
  const geometry = entry.core?.geometry ?? [];
  if (!geometry.length) return unavailable(`${role} route geometry unavailable.`);
  sample = segmentHoverSample(geometry, hover.value);
  if (!sample) {
    const first = segmentChartX(geometry[0], 'distance'),
      last = segmentChartX(geometry.at(-1)!, 'distance');
    return unavailable(
      first == null || last == null || hover.value.x < first || hover.value.x > last
        ? `${role} position unavailable here.`
        : `${role} has a recording gap here.`
    );
  }
  if (!sample.position) return unavailable(`${role} position unavailable here.`);
  if (sample.elapsedSeconds == null) return unavailable(`${role} timing unavailable here.`);
  return { sample, status: null };
}
const referenceDistanceInspection = computed(() => inspectDistance(referenceEntry.value));
const locatorDistanceInspection = computed(() =>
  locatorEntry.value === referenceEntry.value ? referenceDistanceInspection.value : inspectDistance(locatorEntry.value)
);
const locatorStatus = computed(() => locatorDistanceInspection.value.status);
const replayHereElapsedSeconds = computed(() => {
  if (!hover.value) return null;
  if (hover.value.axis === 'time') return hover.value.x / MILLISECONDS_PER_SECOND;
  return referenceDistanceInspection.value.sample?.elapsedSeconds ?? null;
});
const replayHereUnavailableReason = computed(() => {
  if (!canReplay.value) return SEGMENT_REPLAY_UNAVAILABLE_HINT;
  if (!hover.value) return 'Inspect a chart position to open Replay here.';
  if (hover.value.axis === 'distance') {
    const status = referenceDistanceInspection.value.status;
    if (status) return status;
  }
  return replayHereElapsedSeconds.value == null ? 'Reference timing unavailable here.' : null;
});
function openReplay(seconds = 0) {
  if (!canReplay.value) return;
  replayInitialElapsedSeconds.value = seconds;
  view.value = 'replay';
}
function openReplayHere() {
  if (replayHereUnavailableReason.value || replayHereElapsedSeconds.value == null) return;
  openReplay(replayHereElapsedSeconds.value);
}
const columnsOpen = ref(false);
const extraColumns = ref<string[]>([]);
const SUMMARY_COLUMNS = [
  'Ascent',
  'Descent',
  'Mechanical energy',
  'Maximum power',
  'Average heart rate',
  'HR coverage',
  'Cardiac cost',
  'Breaks',
  'Stopped time',
];
function summaryValue(entry: AttemptData, column: string) {
  const summary = entry.core?.summary ?? entry.attempt;
  switch (column) {
    case 'Ascent':
      return summary.ascentMeters == null ? 'Unavailable' : formatElevation(summary.ascentMeters);
    case 'Descent':
      return summary.descentMeters == null ? 'Unavailable' : formatElevation(summary.descentMeters);
    case 'Mechanical energy':
      return summary.energyWh == null ? 'Unavailable' : `${formatNumber(summary.energyWh, 1)} Wh`;
    case 'Maximum power':
      return summary.maximumPowerWatts == null ? 'Unavailable' : `${formatNumber(summary.maximumPowerWatts, 0)} W`;
    case 'Average heart rate':
      return formatSegmentHeartRate(entry.health?.heartRate);
    case 'HR coverage':
      return entry.health?.heartRate?.coveragePercent == null
        ? 'Unavailable'
        : `${formatNumber(entry.health.heartRate.coveragePercent, 1)}%`;
    case 'Cardiac cost':
      return entry.health?.cardiacCost?.beatsPerKj == null
        ? 'Unavailable'
        : `${formatNumber(entry.health.cardiacCost.beatsPerKj, 1)} beats/kJ`;
    case 'Breaks': {
      const notes = stopNotes(entry);
      return notes ? `${notes.coverageComplete === false ? 'Observed: ' : ''}${notes.stopCount ?? 0}` : 'Unavailable';
    }
    case 'Stopped time': {
      const notes = stopNotes(entry);
      return notes
        ? `${notes.coverageComplete === false ? 'Observed: ' : ''}${duration(notes.totalStoppedSec ?? 0)}`
        : 'Unavailable';
    }
    default:
      return 'Unavailable';
  }
}
function stopNotes(entry: AttemptData) {
  const i = entry.attempt.identity;
  return props.measureServiceResult.crossings?.[i.trackId]?.crossings?.find(
    (c) => c.triggerPoint?.name === i.toZone && c.visitNumber === i.toVisit
  )?.segmentNotesSinceLastTriggerPoint;
}
function chooseReference(value: string) {
  setReference(value);
  props.session?.setReference(value);
}
function removeAttempt(entry: AttemptData) {
  props.session?.toggleAttempt(entry.attempt);
  if (!props.session?.selected.value.length) emit('goto-table');
}
watch(reference, (value) => {
  props.session?.setReference(value);
});
watch(
  () => [
    focused.value,
    selectedMetrics.value.has('heartRate'),
    extraColumns.value.join(','),
    data.value.map((d) => d.attempt.identity.reference).join('|'),
  ],
  () => {
    const needsAll =
      selectedMetrics.value.has('heartRate') ||
      extraColumns.value.some((c) => ['Average heart rate', 'HR coverage', 'Cardiac cost'].includes(c));
    for (const entry of data.value)
      if (
        (needsAll || entry.attempt.identity.reference === focused.value) &&
        !entry.health &&
        !entry.healthLoading &&
        !entry.healthError
      )
        void loadHealth(entry);
  },
  { immediate: true }
);
const seriesByMetric = computed<Record<string, SegmentChartSeries[]>>(() =>
  Object.fromEntries(
    visibleMetrics.value.map((metric) => [
      metric.key,
      available.value.map((entry) => {
        const result: SegmentChartSeries = {
          id: entry.attempt.identity.reference,
          name: `${trackName(entry.track, entry.attempt.identity.trackId)} · ${visitLabel(entry.attempt)}`,
          color: color(entry),
          data: [],
        };
        let pendingBreak = false;
        function add(x: number | null, y: unknown, breakBefore?: boolean, label?: string) {
          if (x == null) {
            pendingBreak = true;
            return;
          }
          if (breakBefore || pendingBreak) result.data.push([x, null]);
          pendingBreak = false;
          if (label) (result.pointLabels ??= [])[result.data.length] = label;
          result.data.push([x, typeof y === 'number' && Number.isFinite(y) ? y : null]);
        }
        if (metric.key === 'gap') {
          const points = entry.comparison?.points ?? [];
          for (const [index, p] of points.entries()) {
            const next = points[index + 1];
            // Label the server's paired stop limits, without repeating a note for ordinary samples.
            const note =
              p.positionMeaning === 'DEPARTURE_LIMIT'
                ? 'After stop'
                : next?.positionMeaning === 'DEPARTURE_LIMIT' && next.distanceMeters === p.distanceMeters
                  ? 'Before stop'
                  : undefined;
            add(segmentChartX(p, 'distance'), p.differenceSeconds, p.breakBefore, note);
          }
        } else if (metric.key === 'lead')
          for (const p of entry.comparison?.ghostPoints ?? [])
            add(segmentChartX({ distanceMeters: p.referenceDistanceMeters }, 'distance'), p.leadMeters, p.breakBefore);
        else if (metric.key === 'heartRate')
          for (const p of entry.health?.points ?? []) add(segmentChartX(p, xMode.value), p.averageBpm, p.breakBefore);
        else
          for (const p of entry.core?.points ?? [])
            add(
              segmentChartX(p, xMode.value),
              metric.key === 'energy' && entry.core?.summary?.availability?.energy?.state === 'UNAVAILABLE'
                ? null
                : metric.field
                  ? p[metric.field]
                  : null,
              p.breakBefore
            );
        return result;
      }),
    ])
  )
);
function hasMetricValues(key: string) {
  return seriesByMetric.value[key]?.some((s) => s.data.some((p) => p[1] != null));
}
type GhostView = {
  entry: AttemptData;
  id: string;
  color: string;
  index: number;
  point: SegmentGhostPoint | null;
  ghostKm: number | null;
  position: [number, number] | null;
};
const ghostsActive = computed(
  () => ghostsEnabled.value && view.value === 'charts' && hover.value?.axis === 'distance' && !!referenceEntry.value
);
const ghosts = computed<GhostView[]>(() => {
  if (!ghostsActive.value) return [];
  const x = hover.value!.x;
  return available.value
    .filter((entry) => entry !== referenceEntry.value)
    .map((entry) => {
      const point = segmentGhostAt(entry.comparison?.ghostPoints, x);
      const ghostKm = point?.distanceMeters == null ? null : point.distanceMeters / METERS_PER_KILOMETER;
      return {
        entry,
        id: entry.attempt.identity.reference,
        color: color(entry),
        index: data.value.indexOf(entry),
        point,
        ghostKm,
        position:
          ghostKm == null ? null : segmentHoverPosition(entry.core?.geometry ?? [], { x: ghostKm, axis: 'distance' }),
      };
    });
});
// The highlighted attempt shows its lead along the route; a single comparison needs no highlight.
const trailGhost = computed(
  () =>
    ghosts.value.find((ghost) => ghost.id === activeAttempt.value) ??
    (ghosts.value.length === 1 ? ghosts.value[0] : undefined)
);
const ghostSummaries = computed(() =>
  ghostsActive.value ? Object.fromEntries(ghosts.value.map((ghost) => [ghost.id, ghostDetail(ghost)])) : null
);
const chartGhosts = computed(() =>
  ghosts.value
    .filter((ghost) => ghost.ghostKm != null)
    .map((ghost) => ({ seriesId: ghost.id, x: ghost.ghostKm!, emphasized: ghost === trailGhost.value }))
);
function ghostDetail(ghost: GhostView) {
  const lead = ghost.point?.leadMeters;
  if (lead == null) return 'Ghost position unavailable';
  const text = formatGhostLead(lead);
  return ghost.point?.state === 'FINISHED' ? `${text} · finished` : text;
}
function formatGhostLead(lead: number) {
  const distance = formatShortDistanceM(Math.abs(lead), measurementSystem.value);
  return Math.round(lead) === 0 ? 'Level with the reference' : `${distance} ${lead > 0 ? 'ahead' : 'behind'}`;
}
type InspectionMapPoint = {
  id: string;
  position: [number, number];
  color: string;
  index: number;
  locator: boolean;
  reference: boolean;
  ghost: boolean;
  pointLabel: string;
  label: string;
  detail?: string;
};
const inspectionMapPoints = computed<InspectionMapPoint[]>(() => {
  if (view.value !== 'charts' || !hover.value) return [];
  const ghostPoints = ghosts.value.flatMap((ghost) =>
    ghost.position
      ? [
          {
            id: `ghost:${ghost.id}`,
            position: ghost.position,
            color: ghost.color,
            index: ghost.index,
            locator: false,
            reference: false,
            ghost: true,
            pointLabel: String(ghost.index + 1),
            label: `Ghost · ${trackName(ghost.entry.track, ghost.entry.attempt.identity.trackId)} · ${visitLabel(ghost.entry.attempt)}`,
            detail: ghostDetail(ghost),
          },
        ]
      : []
  );
  const entries =
    hover.value.axis === 'distance'
      ? locatorEntry.value && locatorDistanceInspection.value.sample?.position
        ? [locatorEntry.value]
        : []
      : available.value;
  return [
    ...ghostPoints,
    ...entries.flatMap((entry) => {
      const position =
        hover.value!.axis === 'distance'
          ? locatorDistanceInspection.value.sample?.position
          : segmentHoverPosition(entry.core?.geometry ?? [], hover.value!);
      const index = data.value.indexOf(entry);
      const locator = hover.value!.axis === 'distance';
      const isReference = entry === referenceEntry.value;
      return position
        ? [
            {
              id: entry.attempt.identity.reference,
              position,
              color: color(entry),
              index,
              locator,
              reference: isReference,
              ghost: false,
              pointLabel: isReference ? 'R' : String(index + 1),
              label: locator
                ? locatorIdentity(entry)
                : `${trackName(entry.track, entry.attempt.identity.trackId)} · ${visitLabel(entry.attempt)}`,
            },
          ]
        : [];
    }),
  ];
});
const GHOST_TRAIL_WIDTH_PX = 10;
const GHOST_TRAIL_OPACITY = 0.35;
const mapGeoJson = computed<MiniMapGeoJson>(() => {
  const features: MiniMapGeoJson['features'] = [];
  const trail = trailGhost.value;
  // Drawn first so the attempt's own route line stays visible on top of its lead band.
  if (trail?.ghostKm != null && hover.value)
    for (const coordinates of segmentGeometrySlice(trail.entry.core?.geometry ?? [], hover.value.x, trail.ghostKm))
      features.push({
        type: 'Feature',
        geometry: { type: 'LineString', coordinates },
        properties: {
          type: 'ghost-trail',
          color: trail.color,
          lineWidth: GHOST_TRAIL_WIDTH_PX,
          lineOpacity: GHOST_TRAIL_OPACITY,
        },
      });
  for (const entry of available.value) {
    let line: number[][] = [];
    function finish() {
      if (line.length > 1)
        features.push({
          type: 'Feature',
          geometry: { type: 'LineString', coordinates: line },
          properties: { color: color(entry), type: 'track' },
        });
      line = [];
    }
    for (const point of entry.core?.geometry ?? []) {
      if (point.breakBefore) finish();
      if (point.longitude != null && point.latitude != null) line.push([point.longitude, point.latitude]);
      else finish();
    }
    finish();
    const identity = entry.attempt.identity;
    for (const [coordinates, zone, visit] of [
      [identity.startCoordinate, identity.fromZone, identity.fromVisit],
      [identity.endCoordinate, identity.toZone, identity.toVisit],
    ] as const) {
      if (coordinates?.length === 2)
        features.push({
          type: 'Feature',
          geometry: { type: 'Point', coordinates },
          properties: {
            type: 'trigger',
            color: color(entry),
            label: zone,
            name: `${zone}${visit} · ${trackName(entry.track, identity.trackId)}`,
          },
        });
    }
  }
  for (const point of inspectionMapPoints.value)
    features.push({
      type: 'Feature',
      geometry: { type: 'Point', coordinates: point.position },
      properties: {
        type: point.ghost ? 'ghost' : point.locator ? 'locator' : 'racer',
        ...(point.reference ? { shape: MINI_MAP_DIAMOND_SHAPE } : {}),
        color: point.color,
        label: point.pointLabel,
        trackIndex: point.index,
        trackName: point.label,
        ...(point.detail ? { popupTitle: point.label, popupDetails: point.detail } : {}),
      },
    });
  return { type: 'FeatureCollection', features };
});
const mapBounds = computed<MiniMapBounds | null>(() => {
  let west = Infinity,
    south = Infinity,
    east = -Infinity,
    north = -Infinity;
  for (const entry of available.value)
    for (const p of entry.core?.geometry ?? []) {
      if (p.longitude == null || p.latitude == null || !Number.isFinite(p.longitude) || !Number.isFinite(p.latitude))
        continue;
      west = Math.min(west, p.longitude);
      east = Math.max(east, p.longitude);
      south = Math.min(south, p.latitude);
      north = Math.max(north, p.latitude);
    }
  return Number.isFinite(west)
    ? [
        [west, south],
        [east, north],
      ]
    : null;
});
const inspectionMarkers = new Map<string, Marker>();
let inspectionMap: MapLibreMap | undefined;
function clearInspectionMarkers() {
  inspectionMarkers.forEach((marker) => marker.remove());
  inspectionMarkers.clear();
}
watch([() => props.map, inspectionMapPoints], ([map, points]) => {
  if (map !== inspectionMap) clearInspectionMarkers();
  inspectionMap = map;
  if (!map) return;
  const ids = new Set(points.map((point) => point.id));
  for (const [id, marker] of inspectionMarkers) {
    if (!ids.has(id)) {
      marker.remove();
      inspectionMarkers.delete(id);
    }
  }
  for (const point of points) {
    let marker = inspectionMarkers.get(point.id);
    if (!marker) {
      const element = document.createElement('div');
      element.className = 'segment-inspection-marker';
      element.setAttribute('role', 'img');
      marker = new Marker({ element, anchor: 'center' }).setLngLat(point.position).addTo(map);
      inspectionMarkers.set(point.id, marker);
    } else marker.setLngLat(point.position);
    const element = marker.getElement();
    element.classList.toggle('segment-inspection-marker--locator', point.locator);
    element.classList.toggle('segment-inspection-marker--reference', point.reference);
    element.classList.toggle('segment-inspection-marker--ghost', point.ghost);
    if (point.reference || point.ghost) {
      const glyph = document.createElement('span');
      glyph.textContent = point.pointLabel;
      element.replaceChildren(glyph);
    } else element.replaceChildren();
    element.setAttribute('aria-label', `Inspected position: ${point.label}`);
    element.setAttribute('title', point.label);
    element.style.setProperty('--inspection-color', point.color);
  }
});
onBeforeUnmount(clearInspectionMarkers);
watch(
  () => [xMode.value, selectedSegmentKey.value, data.value.map((d) => d.attempt.identity.reference).join('|')],
  () => {
    hover.value = null;
    positionPinned.value = false;
    if (!data.value.some((entry) => entry.attempt.identity.reference === activeAttempt.value))
      activeAttempt.value = null;
  }
);
</script>
<style scoped>
:global(.segment-inspection-marker) {
  width: 16px;
  height: 16px;
  border: 3px solid white;
  border-radius: 50%;
  background: var(--inspection-color);
  box-shadow:
    0 0 0 2px var(--inspection-color),
    0 2px 6px #0008;
  pointer-events: none;
}
:global(.segment-inspection-marker--locator) {
  background: white;
  border-color: var(--inspection-color);
}
:global(.segment-inspection-marker--reference) {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
  border: 3px solid var(--inspection-color);
  border-radius: 3px;
  background: white;
  box-shadow: 0 2px 6px #0008;
  color: var(--inspection-color);
  font-size: 10px;
  font-weight: 800;
  line-height: 1;
  /* MapLibre positions markers with transform, so rotate via the individual property. */
  rotate: 45deg;
}
:global(.segment-inspection-marker--reference > span) {
  rotate: -45deg;
}
:global(.segment-inspection-marker--ghost) {
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px dashed var(--inspection-color);
  background: rgb(255 255 255 / 0.6);
  box-shadow: none;
  color: var(--inspection-color);
  font-size: 9px;
  font-weight: 800;
  line-height: 1;
}
.sc-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  min-height: 0;
  overflow-y: auto;
  gap: 1rem;
  padding: 0.3rem 1rem 1rem;
  color: var(--text-primary);
}
.sc-workspace--inspecting {
  grid-template-columns: minmax(0, 1fr) minmax(16rem, 30%);
}
.sc-working {
  min-width: 0;
  container-type: inline-size;
  container-name: segment-workspace;
}
.sc-workspace button:where(:not(.p-button)),
.sc-workspace select {
  font: inherit;
  padding: 0.4rem 0.55rem;
  border: 1px solid var(--border-default);
  border-radius: 0.4rem;
  color: var(--text-primary);
  background: var(--surface-card);
  cursor: pointer;
}
.sc-workspace button:where(:not(.p-button)):disabled {
  opacity: 0.5;
}
.sc-workspace button:where(:not(.p-button))[aria-pressed='true'] {
  border-color: var(--accent);
  color: var(--accent-text);
}
.sc-workspace button:where(:not(.p-button)):focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}
.sc-graph-settings {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 24rem), 1fr));
  gap: 0.75rem 2rem;
  padding: 0.75rem;
  margin-bottom: 0.75rem;
  border: 1px solid var(--border-subtle);
  border-radius: 0.4rem;
  font-size: var(--text-sm-size);
}
.sc-graph-height {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
}
.sc-graph-height strong {
  margin-left: 0.35rem;
  font-variant-numeric: tabular-nums;
}
.sc-graph-height-slider {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  flex: 1;
  min-width: 12rem;
  max-width: 24rem;
}
.sc-graph-height-slider .mtl-slider {
  flex: 1;
}
.sc-graph-height-slider button {
  flex: none;
}
.sc-roster {
  font-size: var(--text-sm-size);
  border-bottom: 1px solid var(--border-subtle);
  padding-bottom: 0.5rem;
}
.sc-roster summary {
  cursor: pointer;
  overflow-wrap: anywhere;
}
.sc-roster-entry {
  display: flex;
  justify-content: space-between;
  gap: 0.5rem;
  margin: 0.4rem 0;
}
.sc-roster-entry button {
  overflow-wrap: anywhere;
  text-align: left;
}
.sc-roster label {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  margin: 0.5rem 0;
}
.sc-roster select {
  min-width: 0;
  max-width: 100%;
}
.sc-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.5rem 0.75rem;
  margin: 0.6rem 0;
  font-size: var(--text-sm-size);
}
.sc-toolbar label {
  display: flex;
  align-items: center;
  gap: 0.4rem;
}
.sc-view-toggle {
  display: inline-flex;
  margin-right: auto;
}
.sc-workspace .sc-view-toggle button {
  border-radius: 0;
}
.sc-workspace .sc-view-toggle button:first-child {
  border-radius: 0.4rem 0 0 0.4rem;
}
.sc-workspace .sc-view-toggle button:last-child {
  margin-left: -1px;
  border-radius: 0 0.4rem 0.4rem 0;
}
.sc-workspace .sc-view-toggle button[aria-pressed='true'] {
  position: relative;
  background: var(--accent-bg);
}
.sc-visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}
.sc-charts > div {
  min-width: 0;
}
.sc-charts {
  display: grid;
  gap: 0.75rem;
  min-width: 0;
}
.sc-add-chart select {
  min-width: 0;
  max-width: 14rem;
}
.sc-analysis-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 1rem;
  align-items: start;
}
.sc-chart-region,
.sc-inspection-rail {
  min-width: 0;
  scroll-margin-top: 0.5rem;
}
.sc-mobile-map {
  position: sticky;
  top: 0;
  z-index: 6;
  min-width: 0;
}
.sc-mobile-map:empty {
  display: none;
}
.sc-chart-tools {
  display: flex;
  justify-content: space-between;
  gap: 0.5rem;
  align-items: center;
}
.sc-chart-tools .sc-warning {
  flex: 1;
  margin: 0 0 0.5rem;
}
.sc-remove-chart {
  margin-left: auto;
  padding: 0.2rem 0.4rem !important;
}
.sc-summary {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 0.7rem;
}
@container segment-workspace (min-width: 840px) {
  .sc-mobile-map {
    display: none;
  }
  .sc-analysis-layout {
    grid-template-columns: minmax(0, 1fr) minmax(19rem, 32%);
  }
  .sc-inspection-rail {
    position: sticky;
    top: 0.3rem;
    max-height: calc(100dvh - 180px);
    overflow-y: auto;
  }
  .sc-view-values {
    display: none;
  }
  .sc-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
.sc-summary-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.sc-summary-heading h3 {
  font-size: var(--text-base-size);
}
.sc-summary-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.5rem;
  border: 1px solid var(--border-subtle);
  border-radius: 0.6rem;
  padding: 0.7rem;
  font-size: var(--text-sm-size);
}
.sc-summary-start {
  grid-column: 1 / -1;
}
.sc-workspace .sc-summary-identity {
  grid-column: 1/-1;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.4rem;
  padding: 0;
  border: 0;
  background: transparent;
  font-weight: var(--font-semibold);
  text-align: left;
  overflow-wrap: anywhere;
}
.sc-summary-identity small {
  flex-basis: 100%;
  margin: 0;
  font-weight: normal;
}
.sc-summary small {
  display: block;
  color: var(--text-muted);
  margin-bottom: 0.2rem;
  font-size: var(--text-xs-size);
}
.sc-summary-row p {
  grid-column: 1 / -1;
  margin: 0;
}
.sc-column-chooser {
  display: flex;
  flex-wrap: wrap;
  gap: 0.7rem;
  font-size: var(--text-sm-size);
}
.sc-warning {
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
}
.sc-inspector {
  border-left: 1px solid var(--border-subtle);
  padding: 0.6rem;
  min-width: 0;
}
.sc-inspector h3 {
  overflow-wrap: anywhere;
}
.sc-inspector-map {
  height: 220px;
  width: 100%;
}
.sc-inspector dt {
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
}
.sc-inspector dd {
  margin: 0.15rem 0 0.6rem;
  font-size: var(--text-sm-size);
}
.sc-inspector-back {
  position: sticky;
  top: 0;
  z-index: 1;
}
.sc-request-error {
  margin: 0.5rem 0;
  font-size: var(--text-sm-size);
}
@media (max-width: 760px) {
  .sc-workspace {
    padding: 0.25rem 0.6rem 0.6rem;
  }
  .sc-workspace--inspecting {
    grid-template-columns: minmax(0, 1fr);
  }
  .sc-workspace--inspecting .sc-working {
    display: none;
  }
  .sc-inspector {
    border: 0;
    padding: 0;
  }
  .sc-roster summary {
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
  .sc-roster[open] summary {
    display: block;
  }
  .sc-summary-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
