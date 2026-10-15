<template>
  <div class="analysis-workspace">
    <div class="workspace-context">
      <button v-if="state.view === 'comparison'" class="context-back" @click="backToResults">← Back to results</button>
      <span class="context-summary">
        <strong>{{ state.view === 'comparison' ? activeSegment?.label : zoneSummary }}</strong>
        <small v-if="state.view === 'results'">{{ contextDetail }}</small>
      </span>
      <button class="context-action" title="Edit zones" aria-label="Edit zones" @click="emit('edit-zones')">
        <i class="bi bi-pencil" aria-hidden="true"></i><span>Edit zones</span>
      </button>
      <button
        class="context-action"
        :disabled="refreshing"
        :title="refreshing ? 'Refreshing…' : 'Refresh results'"
        :aria-label="refreshing ? 'Refreshing…' : 'Refresh results'"
        @click="emit('refresh-results')"
      >
        <i class="bi bi-arrow-clockwise" :class="{ 'context-spin': refreshing }" aria-hidden="true"></i
        ><span>{{ refreshing ? 'Refreshing…' : 'Refresh' }}</span>
      </button>
    </div>
    <p v-if="state.discoveryError || state.filtersChanged" role="status" class="workspace-warning">
      {{ state.discoveryError || 'Updating results for the current filters…' }}
      <Button v-if="state.discoveryError" size="small" outlined @click="emit('refresh-results')">Retry results</Button>
    </p>
    <p v-if="state.staleSelections.length" role="status" class="workspace-warning">
      {{ state.staleSelections.join('; ') }}. These attempts were removed from selection.
    </p>
    <div
      v-show="state.view === 'results'"
      ref="resultsScroll"
      class="discovery-scroll"
      data-sheet-fit-target
      @scroll="state.scrollTop = ($event.target as HTMLElement).scrollTop"
    >
      <div class="discovery-controls">
        <label
          >Segment<select v-model="state.segment">
            <option v-for="segment in session.segments.value" :key="segment.key" :value="segment.key">
              {{ segment.label }}
            </option>
            <option :value="ALL_SEGMENTS">All segments</option>
          </select></label
        >
        <label
          >Metric<select v-model="state.metric">
            <option v-for="metric in METRICS" :key="metric.key" :value="metric.key">{{ metric.label }}</option>
          </select></label
        >
      </div>
      <p v-if="!session.segments.value.length">
        No tracks pass through these zones. Edit the zones or change your filter.
      </p>
      <div v-else class="discovery-layout">
        <DiscoveryTrend
          class="discovery-trend-column"
          :title="trendCopy.title"
          :description="trendDescription"
          :series="trendSeries"
          :focused="state.focusedRow"
          :axis-label="axisLabel"
          :display-value="displayValue"
          @focus="focusPoint"
        />
        <div class="discovery-results-column">
          <div class="results-heading">
            <h3>
              Results <small>{{ rows.length }}</small>
            </h3>
            <label
              >Results grouping<select v-model="state.grouping">
                <option value="average">Average per track</option>
                <option value="attempts">Individual attempts</option>
              </select></label
            >
            <label
              >Sort<select v-model="state.sort">
                <option value="newest">Newest first</option>
                <option value="metricAsc">{{ metricLabel }}: low to high</option>
                <option value="metricDesc">{{ metricLabel }}: high to low</option>
              </select></label
            >
          </div>
          <p v-if="state.segment === ALL_SEGMENTS" class="selection-guidance">
            Choose a segment in the table to select attempts for comparison or replay.
          </p>
          <p v-if="state.metric === 'heartRate'" class="workspace-note">
            <span v-if="syntheticHealthData">Synthetic demo heart rate. </span>
            Heart rate is weighted by covered time. {{ tableHealthLoading ? 'Loading heart rate…' : '' }}
          </p>
          <div v-for="id in healthFailures" :key="id" role="status">
            Track {{ id }}: heart rate could not be loaded.
            <Button size="small" outlined @click="loadTableHealth(id)">Retry heart rate</Button>
          </div>
          <div v-if="state.segment === ALL_SEGMENTS" class="segment-matrix">
            <table>
              <thead>
                <tr>
                  <th>Track</th>
                  <th v-for="segment in session.segments.value" :key="segment.key">
                    <button @click="state.segment = segment.key">{{ segment.label }}</button>
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="track in matrixTracks" :key="track.id">
                  <th>{{ track.name }}</th>
                  <td v-for="segment in session.segments.value" :key="segment.key">
                    <button @click="focusCell(track.id, segment.key)">{{ matrixValue(track.id, segment.key) }}</button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <div v-else class="result-list" aria-label="Segment results">
            <article
              v-for="row in sortedRows"
              :key="row.key"
              :data-row-key="row.key"
              class="result-row"
              :class="{ 'result-row--focused': state.focusedRow === row.key }"
            >
              <div class="result-header">
                <label class="result-selection">
                  <input
                    type="checkbox"
                    :checked="row.attempts.length > 0 && row.attempts.every(isSelected)"
                    :indeterminate="row.attempts.some(isSelected) && !row.attempts.every(isSelected)"
                    :disabled="!row.attempts.length"
                    :aria-label="rowSelectionLabel(row)"
                    @change="selectRowAttempts(row, ($event.target as HTMLInputElement).checked)"
                  />
                </label>
                <button class="result-main" :aria-expanded="state.expanded.includes(row.key)" @click="focusRow(row)">
                  <span class="result-date">{{ formatDateCompact(row.date) }}</span>
                  <span class="result-name">
                    <span class="result-title" :title="row.name">{{ row.name }}</span>
                    <span class="result-metadata">
                      <ActivityTypeBadge v-if="row.activityType" :type="row.activityType" size="xs" />
                      <span v-else>Activity type unavailable</span>
                      <span>{{
                        row.attempt
                          ? visitLabel(row.attempt)
                          : `${row.attempts.length} ${row.attempts.length === 1 ? 'visit' : 'visits'} · average`
                      }}</span>
                    </span>
                    <span v-if="row.summary && excludedVisits(row)" class="result-valid-counts">
                      {{ excludedVisits(row) }} incomplete {{ excludedVisits(row) === 1 ? 'visit' : 'visits' }} excluded
                      from performance averages
                    </span>
                    <span v-if="row.summary" class="result-valid-counts">
                      Valid visits: elapsed time {{ row.summary.elapsedCount ?? 'unavailable' }} · distance
                      {{ row.summary.distanceCount ?? 'unavailable' }} · speed
                      {{ row.summary.speedCount ?? 'unavailable' }}
                    </span>
                    <span v-if="row.description" class="result-description" :title="row.description">{{
                      row.description
                    }}</span>
                  </span>
                  <strong>{{ formatValue(row.value) }}</strong>
                  <span aria-hidden="true">{{ state.expanded.includes(row.key) ? '⌃' : '⌄' }}</span>
                </button>
              </div>
              <div v-if="state.expanded.includes(row.key)" class="attempt-list">
                <p v-if="row.description" class="attempt-description">{{ row.description }}</p>
                <div v-for="attempt in row.attempts" :key="attempt.identity.reference" class="attempt-row">
                  <label
                    ><input
                      type="checkbox"
                      :checked="isSelected(attempt)"
                      :aria-label="`Select ${row.name} ${visitLabel(attempt)}`"
                      @change="session.toggleAttempt(attempt)"
                    /><span
                      >{{ visitLabel(attempt) }}<small>{{ formatValue(attemptValue(attempt)) }}</small></span
                    ></label
                  >
                  <button @click="inspect(attempt)">Details</button>
                  <small v-if="attempt.identity.timingStatus !== 'AVAILABLE'"
                    >Timing unavailable · {{ readable(attempt.identity.timingStatus) }}</small
                  >
                  <small v-if="hasRecordingGap(attempt)"
                    >Recording gap; endpoint elapsed time excluded from performance averages</small
                  >
                  <small v-else-if="!performanceSupported(attempt)"
                    >Incomplete distance; excluded from performance averages</small
                  >
                  <small v-if="stopCount(attempt)"
                    >{{ stopCount(attempt) }}
                    {{
                      attempt.identity.timingStatus === 'AVAILABLE'
                        ? 'breaks included in elapsed time'
                        : 'observed breaks'
                    }}</small
                  >
                </div>
                <button @click="emit('show-track-details', row.trackId)">Open full track</button>
              </div>
            </article>
          </div>
        </div>
      </div>
    </div>
    <div
      v-if="state.view === 'results' && activeSegment && segmentAttempts.length"
      class="selection-bar"
      role="region"
      aria-label="Attempt selection and comparison"
    >
      <span class="selection-status" aria-live="polite">
        <template v-if="session.selected.value.length">
          <strong
            >{{ session.selected.value.length }}
            {{ session.selected.value.length === 1 ? 'attempt' : 'attempts' }} selected</strong
          >
          <small>{{ activeSegment.label }}</small>
        </template>
        <template v-else>
          <strong>Choose attempts to compare or replay</strong>
          <small>{{
            state.grouping === 'average'
              ? 'Tick a result to select its visits, or open it to pick visits individually.'
              : `Tick the passes through ${activeSegment.label} you want to compare.`
          }}</small>
        </template>
      </span>
      <div class="selection-actions">
        <button
          v-if="session.selected.value.length < segmentAttempts.length"
          class="select-all"
          @click="session.selectAll"
        >
          Select all {{ segmentAttempts.length }}
        </button>
        <button
          v-if="session.selected.value.length"
          aria-label="Clear selected attempts"
          @click="session.clearSelection"
        >
          Clear
        </button>
        <template v-if="session.selected.value.length">
          <Button
            size="small"
            outlined
            :disabled="!canReplay"
            :title="canReplay ? undefined : 'Replay needs timing for every selected attempt'"
            @click="openComparison('replay')"
          >
            Replay
          </Button>
          <Button class="primary-action" size="small" @click="openComparison('charts')">
            {{
              session.selected.value.length > 1
                ? `Compare ${session.selected.value.length} attempts`
                : 'Inspect attempt'
            }}
          </Button>
        </template>
      </div>
    </div>
    <SegmentCompare
      v-if="state.view === 'comparison'"
      :map="map"
      :measure-service-result="measureServiceResult"
      :selected-track-ids="selectedTrackIds"
      :selected-attempts="session.selected.value"
      :selected-segment="selectedSegmentCode"
      :session="session"
      :reference-attempt="state.references[state.segment]"
      @goto-table="backToResults"
      @show-track-details="emit('show-track-details', $event)"
      @refresh-results="emit('refresh-results')"
    />
  </div>
</template>
<script setup lang="ts">
import { warnSanitized } from '@/utils/safeLogging';

import { useDemoStatus } from '@/composables/useDemoStatus';
const { syntheticHealthData } = useDemoStatus();
import { computed, nextTick, onBeforeUnmount, ref, toRaw, watch } from 'vue';
import type { Map as MapLibreMap } from 'maplibre-gl';
import Button from 'primevue/button';
import type {
  CrossingPointsResponseDto,
  GpsTrack,
  SegmentConsolidatedSummary,
  SegmentTrackHeartRate,
} from 'x8ing-mtl-api-typescript-fetch';
import { formatDateCompact, formatDistanceSmart, formatDuration, formatRadius, formatSpeed } from '@/utils/Utils';
import { useMeasurementSystem } from '@/composables/useMeasurementSystem';
import { speedDisplayValue, longDistanceDisplayValue, MEASUREMENT_DISPLAY_PROFILES } from '@/utils/units';
import DiscoveryTrend, { type TrendPoint } from './DiscoveryTrend.vue';
import SegmentCompare from './SegmentCompare.vue';
import ActivityTypeBadge from '@/components/ui/ActivityTypeBadge.vue';
import { segmentTrackName, segmentStatusLabel as readable } from './segmentDisplay';
import {
  ALL_SEGMENTS,
  attemptSegment,
  segmentKey,
  useAnalysisSession,
  visitLabel,
  timingSupported,
  performanceSupported,
  type AnalysisSession,
  type DiscoveryMetric,
} from './useAnalysisSession';
import { assertAttemptIdentity, isAttempt, runSegmentRequests, type Attempt } from './useSegmentAttempts';
import { fetchSegmentTableHeartRate } from './segmentAnalysisApi';
const props = defineProps<{
  map?: MapLibreMap;
  measureServiceResult: CrossingPointsResponseDto;
  session?: AnalysisSession;
  refreshing?: boolean;
}>();
const emit = defineEmits<{
  'show-track-details': [id: number | string];
  'refresh-results': [];
  'edit-zones': [];
}>();
const session = props.session ?? useAnalysisSession();
const state = session.state;
const resultsScroll = ref<HTMLElement | null>(null);
const { measurementSystem } = useMeasurementSystem();
const METRICS: Array<{ key: DiscoveryMetric; label: string }> = [
  { key: 'speed', label: 'Speed' },
  { key: 'time', label: 'Elapsed time' },
  { key: 'distance', label: 'Distance' },
  { key: 'heartRate', label: 'Average heart rate' },
];
const metricLabel = computed(() => METRICS.find((m) => m.key === state.metric)!.label);
const TREND_COPY: Record<DiscoveryMetric, { title: string; description: string }> = {
  speed: {
    title: 'Your speed trends',
    description: 'See how your average speed on this segment changes across activities.',
  },
  time: {
    title: 'Your segment times',
    description: 'See how long this segment takes across activities, including stops.',
  },
  distance: {
    title: 'Distance over time',
    description:
      'Distance traveled on this segment across activities. Different routes or GPS recordings can change the distance.',
  },
  heartRate: {
    title: 'Your heart rate trends',
    description: 'Average heart rate on this segment across activities, using the available heart rate recordings.',
  },
};
const trendCopy = computed(() => TREND_COPY[state.metric]);
const trendDescription = computed(() => {
  const description =
    state.segment === ALL_SEGMENTS
      ? trendCopy.value.description.replaceAll('this segment', 'each segment')
      : trendCopy.value.description;
  return `${description} ${state.grouping === 'average' ? 'Each point is one activity’s average across its visits.' : 'Each point is one attempt.'}`;
});
const axisLabel = computed(() => {
  const profile = MEASUREMENT_DISPLAY_PROFILES[measurementSystem.value];
  const unit =
    state.metric === 'speed'
      ? profile.speed
      : state.metric === 'distance'
        ? profile.longDistance
        : state.metric === 'time'
          ? 's'
          : 'bpm';
  return `${metricLabel.value} (${unit})`;
});
const activeSegment = computed(() => session.segments.value.find((s) => s.key === state.segment));
const selectedSegmentCode = computed(() =>
  activeSegment.value ? { point1: activeSegment.value.from, point2: activeSegment.value.to, consolidated: true } : null
);
const selectedTrackIds = computed(() => new Set(session.selected.value.map((a) => a.identity.trackId)));
const segmentAttempts = computed(() =>
  Object.values(props.measureServiceResult.crossings ?? {})
    .flatMap((track) => (track.attempts ?? []).filter(isAttempt))
    .filter((attempt) => attemptSegment(attempt) === state.segment)
);
const canReplay = computed(() => session.selected.value.length > 0 && session.selected.value.every(timingSupported));
const trackCount = computed(() => Object.keys(props.measureServiceResult.crossings ?? {}).length);
const zoneSummary = computed(() => {
  const names = state.zones.map((zone) => zone.name).filter(Boolean);
  return names.length ? `Zones ${names.join(' · ')}` : 'Segments';
});
const contextDetail = computed(() =>
  [
    state.radius ? `${formatRadius(state.radius)} radius` : '',
    `${trackCount.value} matching ${trackCount.value === 1 ? 'track' : 'tracks'}`,
  ]
    .filter(Boolean)
    .join(' · ')
);
const tableHealth = ref<Record<number, SegmentTrackHeartRate>>({});
const tableHealthLoading = ref(false);
const healthFailures = ref<number[]>([]);
let generation = 0;
let abort = new AbortController();
async function loadTableHealth(onlyTrack?: number) {
  const current = generation,
    signal = abort.signal;
  tableHealthLoading.value = true;
  const entries = Object.entries(props.measureServiceResult.crossings ?? {}).filter(([id]) =>
    onlyTrack == null ? !tableHealth.value[Number(id)] : Number(id) === onlyTrack
  );
  await runSegmentRequests(entries, async ([id, track]) => {
    const attempts = (track.attempts ?? []).filter(isAttempt);
    if (!attempts.length || signal.aborted) return;
    try {
      const health = await fetchSegmentTableHeartRate(
        attempts.map((a) => a.identity.reference),
        signal
      );
      if (current !== generation || signal.aborted) return;
      if (health.trackId !== Number(id) || health.revision !== attempts[0].identity.revision)
        throw new Error('Source changed');
      for (const item of health.attempts ?? []) {
        const expected = attempts.find((a) => a.identity.reference === item.identity?.reference);
        if (!expected) throw new Error('Unexpected health attempt');
        assertAttemptIdentity(expected.identity, item.identity);
      }
      tableHealth.value[Number(id)] = health;
      healthFailures.value = healthFailures.value.filter((failed) => failed !== Number(id));
    } catch (error) {
      if (current === generation && !signal.aborted) {
        warnSanitized(
          'Segment heart rate summary failed',
          { trackId: id, error },
          { topic1: 'application', topic2: 'DisplayMeasureResults', topic3: 'loadTableHealth' }
        );
        healthFailures.value = [...new Set([...healthFailures.value, Number(id)])];
      }
    }
  });
  if (current === generation) tableHealthLoading.value = false;
}
watch(
  () => props.measureServiceResult,
  (result) => {
    if (toRaw(session.state.result) !== toRaw(result)) session.setResults(result);
    generation++;
    abort.abort();
    abort = new AbortController();
    tableHealth.value = {};
    healthFailures.value = [];
    tableHealthLoading.value = false;
    if (state.metric === 'heartRate') void loadTableHealth();
  },
  { immediate: true }
);
watch(
  () => state.metric,
  (metric) => {
    if (metric === 'heartRate') void loadTableHealth();
  }
);
onBeforeUnmount(() => {
  generation++;
  abort.abort();
});
function formatValue(value: number | null | undefined) {
  if (value == null || !Number.isFinite(value)) return 'Unavailable';
  switch (state.metric) {
    case 'speed':
      return formatSpeed(value, 2);
    case 'time':
      return formatDuration(value * 1000);
    case 'distance':
      return formatDistanceSmart(value);
    case 'heartRate':
      return `${Math.round(value)} bpm`;
  }
}
function displayValue(value: number) {
  if (state.metric === 'speed') return speedDisplayValue(value, measurementSystem.value);
  if (state.metric === 'distance') return longDistanceDisplayValue(value, measurementSystem.value);
  return value;
}
function attemptValue(a: Attempt) {
  switch (state.metric) {
    case 'speed':
      return a.averageSpeedKmh;
    case 'time':
      return a.elapsedSeconds;
    case 'distance':
      return a.distanceMeters;
    case 'heartRate':
      return tableHealth.value[a.identity.trackId]?.attempts?.find(
        (h) => h.identity?.reference === a.identity.reference
      )?.heartRate?.averageBpm;
  }
}
type ResultRow = {
  key: string;
  trackId: number;
  name: string;
  activityType: GpsTrack['activityType'];
  description: string;
  date: number;
  segment: string;
  value: number | null | undefined;
  attempts: Attempt[];
  attempt?: Attempt;
  summary?: SegmentConsolidatedSummary;
};
const allRows = computed<ResultRow[]>(() =>
  Object.entries(props.measureServiceResult.crossings ?? {}).flatMap<ResultRow>(([id, track]) => {
    const trackId = Number(id),
      name = segmentTrackName(track.gpsTrack, trackId);
    const activityType = track.gpsTrack?.activityType;
    const description = (
      track.gpsTrack?.trackDescription?.trim() ||
      track.gpsTrack?.metaDescription?.trim() ||
      ''
    ).replace(/\s+/g, ' ');
    const date = track.gpsTrack?.startDate ? new Date(track.gpsTrack.startDate).getTime() : NaN;
    const attempts = (track.attempts ?? []).filter(isAttempt);
    if (state.grouping === 'attempts')
      return attempts.map((a) => ({
        key: a.identity.reference,
        trackId,
        name,
        activityType,
        description,
        date: a.identity.start ? new Date(a.identity.start).getTime() : date,
        segment: attemptSegment(a),
        value: attemptValue(a),
        attempts: [a],
        attempt: a,
      }));
    return (track.segmentSummaries ?? []).map((s) => {
      const key = segmentKey(s.fromZone ?? '', s.toZone ?? '');
      const hr = (tableHealth.value[trackId]?.groups ?? track.segmentHeartRateSummaries)?.find(
        (g) => g.fromTriggerPoint === s.fromZone && g.toTriggerPoint === s.toZone
      )?.heartRate;
      const value =
        state.metric === 'speed'
          ? s.meanVisitSpeedKmh
          : state.metric === 'time'
            ? s.meanElapsedSeconds
            : state.metric === 'distance'
              ? s.meanDistanceMeters
              : hr?.averageBpm;
      return {
        key: `${id}:${key}`,
        trackId,
        name,
        activityType,
        description,
        date,
        segment: key,
        value,
        attempts: attempts.filter((a) => attemptSegment(a) === key),
        summary: s,
      };
    });
  })
);
const rows = computed(() => allRows.value.filter((r) => state.segment === ALL_SEGMENTS || r.segment === state.segment));
const sortedRows = computed(() =>
  [...rows.value].sort((a, b) => {
    const stable = a.trackId - b.trackId || a.key.localeCompare(b.key);
    if (state.sort !== 'newest') {
      if (a.value == null || b.value == null) return a.value == null ? (b.value == null ? stable : 1) : -1;
      return (a.value - b.value) * (state.sort === 'metricAsc' ? 1 : -1) || stable;
    }
    return (Number.isFinite(b.date) ? b.date : -Infinity) - (Number.isFinite(a.date) ? a.date : -Infinity) || stable;
  })
);
const trendSeries = computed(() =>
  session.segments.value
    .filter((s) => state.segment === ALL_SEGMENTS || s.key === state.segment)
    .map((s) => ({
      key: s.key,
      label: s.label,
      points: allRows.value
        .filter((r) => r.segment === s.key && (!r.attempt || performanceSupported(r.attempt)))
        .map((r) => ({
          key: r.key,
          segment: r.segment,
          date: r.date,
          value: r.value ?? null,
          label: r.name,
          formatted: formatValue(r.value),
        })),
    }))
);
const matrixTracks = computed(() =>
  Object.entries(props.measureServiceResult.crossings ?? {}).map(([id, t]) => ({
    id: Number(id),
    name: segmentTrackName(t.gpsTrack, Number(id)),
  }))
);
function matrixValue(id: number, segment: string) {
  return (
    allRows.value
      .filter((r) => r.trackId === id && r.segment === segment)
      .map((r) => `${r.attempt ? visitLabel(r.attempt) + ': ' : ''}${formatValue(r.value)}`)
      .join(' · ') || '—'
  );
}
function focusCell(id: number, segment: string) {
  state.segment = segment;
  const row = allRows.value.find((r) => r.trackId === id && r.segment === segment);
  if (row) focusRow(row);
}
function focusRow(row: ResultRow) {
  state.focusedRow = row.key;
  state.expanded = state.expanded.includes(row.key)
    ? state.expanded.filter((k) => k !== row.key)
    : [...state.expanded, row.key];
}
async function focusPoint(point: TrendPoint) {
  state.segment = point.segment;
  state.focusedRow = point.key;
  if (!state.expanded.includes(point.key)) state.expanded.push(point.key);
  await nextTick();
  const row = Array.from(resultsScroll.value?.querySelectorAll<HTMLElement>('[data-row-key]') ?? []).find(
    (e) => e.dataset.rowKey === point.key
  );
  row?.scrollIntoView({ block: 'nearest' });
}
function isSelected(a: Attempt) {
  return (state.selections[attemptSegment(a)] ?? []).some((s) => s.identity.reference === a.identity.reference);
}
function rowSelectionLabel(row: ResultRow) {
  return row.attempts.length === 1
    ? `Select ${row.name} ${visitLabel(row.attempts[0])}`
    : `Select all ${row.attempts.length} visits from ${row.name}`;
}
function selectRowAttempts(row: ResultRow, selected: boolean) {
  for (const attempt of row.attempts) if (isSelected(attempt) !== selected) session.toggleAttempt(attempt);
}
function openComparison(view: 'charts' | 'replay') {
  state.comparisonView = view;
  state.view = 'comparison';
}
function inspect(a: Attempt) {
  if (!isSelected(a)) session.toggleAttempt(a);
  state.focusedAttempt = a.identity.reference;
  openComparison('charts');
}
async function backToResults() {
  state.view = 'results';
  await nextTick();
  if (resultsScroll.value) resultsScroll.value.scrollTop = state.scrollTop;
}
function hasRecordingGap(a: Attempt) {
  return a.availability?.distance?.reason === 'RECORDING_GAP';
}
function excludedVisits(row: ResultRow) {
  return row.attempts.filter((a) => !performanceSupported(a)).length;
}
function stopCount(a: Attempt) {
  return (
    props.measureServiceResult.crossings?.[a.identity.trackId]?.crossings?.find(
      (c) => c.triggerPoint?.name === a.identity.toZone && c.visitNumber === a.identity.toVisit
    )?.segmentNotesSinceLastTriggerPoint?.stopCount ?? 0
  );
}
</script>
<style scoped>
.analysis-workspace {
  display: flex;
  flex-direction: column;
  min-height: 0;
  min-width: 0;
  height: 100%;
  color: var(--text-primary);
}
.workspace-context,
.discovery-controls,
.results-heading,
.selection-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.6rem;
}
.workspace-context {
  flex-wrap: nowrap;
  padding: 0.5rem 1rem 0.5rem;
  border-bottom: 1px solid var(--border-subtle);
}
.context-summary {
  display: flex;
  flex-direction: column;
  min-width: 0;
  margin-right: auto;
}
.context-summary strong,
.context-summary small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.context-summary small {
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
}
.analysis-workspace .context-back {
  flex: 0 0 auto;
}
.analysis-workspace .context-action {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 0.4rem;
}
.context-spin {
  animation: context-spin 0.8s linear infinite;
}
@keyframes context-spin {
  to {
    transform: rotate(360deg);
  }
}
.analysis-workspace button:where(:not(.p-button)),
.analysis-workspace select {
  font: inherit;
  color: var(--text-primary);
  background: var(--surface-card);
  border: 1px solid var(--border-default);
  border-radius: 0.4rem;
  padding: 0.45rem 0.6rem;
  cursor: pointer;
}
.analysis-workspace button:where(:not(.p-button)):disabled {
  opacity: 0.45;
  cursor: default;
}
.analysis-workspace button:where(:not(.p-button)):focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}
.discovery-scroll {
  flex: 1 1 auto;
  overflow-y: auto;
  min-height: 0;
  padding: 0 1rem 1rem;
  container-type: inline-size;
  container-name: segment-discovery;
}
.discovery-controls {
  margin: 0.6rem 0 0.8rem;
}
.discovery-controls label,
.results-heading label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: var(--text-sm-size);
  flex: 1;
  min-width: 0;
}
.discovery-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 1rem;
  align-items: start;
}
@container segment-discovery (min-width: 860px) {
  .discovery-controls label {
    flex: 0 1 16rem;
  }
  .discovery-layout {
    grid-template-columns: minmax(0, 1fr) minmax(0, 1.1fr);
  }
  .discovery-trend-column {
    position: sticky;
    top: 0;
  }
  .discovery-results-column .results-heading {
    margin-top: 0;
  }
}
.results-heading {
  margin: 0.5rem 0;
}
.results-heading h3 {
  margin: 0 auto 0 0;
  font-size: var(--text-base-size);
}
.results-heading small {
  color: var(--text-muted);
}
.result-header {
  display: grid;
  grid-template-columns: 2.5rem minmax(0, 1fr);
  align-items: center;
}
.result-selection {
  display: flex;
  align-items: center;
  justify-content: center;
  align-self: stretch;
  cursor: pointer;
}
.result-selection input {
  width: 1.2rem;
  height: 1.2rem;
  accent-color: var(--accent);
}
.result-row {
  border-bottom: 1px solid var(--border-subtle);
  border-left: 3px solid transparent;
}
.result-row--focused {
  border-left-color: var(--accent);
  background: var(--surface-hover);
}
.analysis-workspace .result-main {
  width: 100%;
  display: grid;
  grid-template-columns: 4.75rem minmax(0, 1fr) auto 1rem;
  align-items: center;
  gap: 0.65rem;
  text-align: left;
  border: 0;
  background: transparent;
  padding: 0.8rem 0.4rem;
}
.result-name {
  min-width: 0;
}
.result-title,
.result-description {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.result-valid-counts {
  display: block;
  margin-top: 0.3rem;
  font-size: var(--text-xs-size);
  color: var(--text-secondary);
}
.result-metadata {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.3rem 0.5rem;
  margin-top: 0.3rem;
  font-size: var(--text-xs-size);
  color: var(--text-secondary);
}
.result-description {
  margin-top: 0.35rem;
  color: var(--text-secondary);
  font-size: var(--text-sm-size);
}
.attempt-description {
  overflow-wrap: anywhere;
  color: var(--text-secondary);
  font-size: var(--text-sm-size);
}
.selection-guidance {
  margin: 0.5rem 0;
  font-size: var(--text-sm-size);
  color: var(--text-secondary);
}
.result-name small,
.attempt-row small {
  display: block;
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
  margin-top: 0.2rem;
}
.result-date {
  font-size: var(--text-xs-size);
  color: var(--text-secondary);
}
.attempt-list {
  padding: 0 0.65rem 0.8rem;
}
.attempt-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 0.5rem;
  padding: 0.5rem 0;
}
.attempt-row label {
  display: flex;
  align-items: center;
  gap: 0.65rem;
}
.attempt-row input {
  width: 1.15rem;
  height: 1.15rem;
}
.selection-bar {
  flex: 0 0 auto;
  flex-wrap: nowrap;
  padding: 0.6rem 1rem calc(0.6rem + env(safe-area-inset-bottom));
  border-top: 1px solid var(--border-default);
  background: var(--surface-ground);
  font-size: var(--text-sm-size);
}
.selection-status {
  min-width: 0;
  margin-right: auto;
}
.selection-status strong,
.selection-status small {
  display: block;
}
.selection-status small {
  color: var(--text-secondary);
  margin-top: 0.2rem;
}
.selection-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.4rem;
}
.workspace-note {
  font-size: var(--text-xs-size);
  color: var(--text-secondary);
}
.workspace-warning {
  margin: 0.5rem 1rem;
}
.segment-matrix {
  overflow-x: auto;
}
.segment-matrix table {
  border-collapse: collapse;
}
.segment-matrix th,
.segment-matrix td {
  padding: 0.5rem;
  min-width: 9rem;
  border-bottom: 1px solid var(--border-subtle);
}
@media (max-width: 600px) {
  .workspace-context {
    padding: 0.4rem 0.6rem;
    gap: 0.4rem;
  }
  .context-summary {
    font-size: var(--text-sm-size);
  }
  .analysis-workspace .context-action span {
    display: none;
  }
  .discovery-scroll {
    padding: 0 0.6rem 0.6rem;
  }
  .analysis-workspace .result-main {
    grid-template-columns: minmax(0, 1fr) auto 1rem;
    gap: 0.3rem;
  }
  .result-date {
    grid-column: 1 / -1;
  }
  .results-heading h3 {
    flex-basis: 100%;
  }
  .selection-bar {
    flex-wrap: wrap;
    gap: 0.45rem;
    padding: 0.5rem 0.6rem calc(0.5rem + env(safe-area-inset-bottom));
    font-size: var(--text-xs-size);
  }
  .selection-status {
    flex-basis: 100%;
  }
  .selection-actions {
    flex: 1 1 auto;
    min-width: 0;
    justify-content: flex-end;
  }
  .result-header {
    grid-template-columns: 2rem minmax(0, 1fr);
  }
  .result-description {
    font-size: var(--text-xs-size);
  }
}
</style>
