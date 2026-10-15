<script setup lang="ts">
import { warnRequestFailure } from '@/utils/safeLogging';
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { TrackQualityControllerApi, type TrackQualityReport, type QualityIssue } from 'x8ing-mtl-api-typescript-fetch';
import { getApiConfiguration } from '@/utils/openApiClient';
import MiniMap from '@/components/map/MiniMap.vue';
import type { MiniMapBounds, MiniMapGeoJson } from '@/components/map/useMiniMap';
import { ORIGINAL_TRACK_COLOR, PROCESSED_TRACK_COLOR } from '@/features/track-review/colors';

const props = defineProps<{ trackId: number }>();
const report = ref<TrackQualityReport | null>(null);
const loading = ref(false);
const expanded = ref(false);
const error = ref('');
const showRaw = ref(true);
const showProcessed = ref(true);
const selected = ref<QualityIssue | null>(null);
let request: AbortController | null = null;
const labels: Record<string, string> = {
  GEOMETRY_ONLY: 'Geometry retained; time and rates unavailable',
  TIME_MISSING: 'Timestamp missing',
  TIMING_UNRELIABLE: 'Measured timing unavailable',
  ELEVATION_MISSING: 'Altitude missing',
  POSITION_REJECTED: 'Position removed',
  POSITION_DISCONTINUITY: 'GPS transition disconnected',
  ELEVATION_REJECTED: 'Altitude excursion rejected',
  STOP_COLLAPSED: 'Observation replaced by stop anchors',
  UNKNOWN_INTERVAL: 'Connecting movement unknown',
  LARGE_POSITION_CORRECTION: 'Large position correction',
  LARGE_ELEVATION_CORRECTION: 'Large altitude correction',
  STOP_ANCHOR: 'Inferred stationary anchor',
  VERTICAL_ONLY_MOVEMENT: 'Altitude changes without horizontal movement',
  WEAK_SOURCE_FIX: 'Weak source fix',
  WEAK_VERTICAL_FIX: 'Weak altitude fix',
  ENERGY_LIMITED: 'Energy limited by power guard',
};
const label = (issue: QualityIssue) => labels[issue.reason ?? ''] ?? issue.reason ?? 'Review observation';

async function load() {
  request?.abort();
  const current = new AbortController();
  request = current;
  loading.value = true;
  error.value = '';
  expanded.value = true;
  try {
    const result = await new TrackQualityControllerApi(getApiConfiguration()).getTrackQuality(
      { trackId: props.trackId },
      { signal: current.signal }
    );
    if (!current.signal.aborted) report.value = result;
  } catch (failure) {
    if (!current.signal.aborted) {
      error.value = 'Review details could not be loaded. Try again.';
      warnRequestFailure('Track quality report could not be loaded', failure, {
        topic1: 'tracks',
        topic2: 'TrackGpsReview',
        topic3: 'quality',
      });
    }
  } finally {
    if (request === current) loading.value = false;
  }
}
watch(
  () => props.trackId,
  () => {
    request?.abort();
    report.value = null;
    expanded.value = false;
    selected.value = null;
    error.value = '';
    loading.value = false;
  }
);
onBeforeUnmount(() => request?.abort());

type SourceCoordinates = NonNullable<TrackQualityReport['rawCoordinates']>;
function paths(coordinates: SourceCoordinates, breaks: number[] | undefined): number[][][] {
  const result: number[][][] = [];
  const boundaries = new Set(breaks ?? []);
  let part: number[][] = [];
  coordinates.forEach((point, index) => {
    if (boundaries.has(index)) {
      if (part.length > 0) result.push(part);
      part = [];
    }
    if (typeof point[0] === 'number' && typeof point[1] === 'number') part.push([point[0], point[1]]);
  });
  if (part.length > 0) result.push(part);
  return result;
}
const focusCoordinate = computed(() => {
  const issue = selected.value;
  if (!issue || !report.value) return null;
  const coordinate =
    issue.rawPointIndex != null
      ? report.value.rawCoordinates?.[issue.rawPointIndex]
      : issue.canonicalPointIndex != null
        ? report.value.canonicalCoordinates?.[issue.canonicalPointIndex]
        : null;
  return coordinate && typeof coordinate[0] === 'number' && typeof coordinate[1] === 'number'
    ? [coordinate[0], coordinate[1]]
    : null;
});
const selectedSource = computed(() =>
  selected.value?.rawPointIndex != null ? report.value?.observationEvidence?.[selected.value.rawPointIndex] : null
);
const geojson = computed<MiniMapGeoJson>(() => {
  const features: GeoJSON.Feature<GeoJSON.Geometry, Record<string, unknown>>[] = [];
  const add = (coordinates: SourceCoordinates | undefined, breaks: number[] | undefined, color: string) => {
    for (const part of paths(coordinates ?? [], breaks))
      features.push({
        type: 'Feature',
        properties: { color },
        geometry: part.every((point) => point[0] === part[0]![0] && point[1] === part[0]![1])
          ? { type: 'Point', coordinates: part[0]! }
          : { type: 'LineString', coordinates: part },
      });
  };
  if (showRaw.value) add(report.value?.rawCoordinates, report.value?.rawBreakIndexes, ORIGINAL_TRACK_COLOR);
  if (showProcessed.value)
    add(report.value?.canonicalCoordinates, report.value?.canonicalBreakIndexes, PROCESSED_TRACK_COLOR);
  if (focusCoordinate.value)
    features.push({
      type: 'Feature',
      properties: { color: '#d87918', label: label(selected.value!) },
      geometry: { type: 'Point', coordinates: focusCoordinate.value.slice(0, 2) },
    });
  return { type: 'FeatureCollection', features };
});
const bounds = computed<MiniMapBounds | null>(() => {
  let points = report.value?.canonicalCoordinates ?? [];
  const issue = selected.value;
  if (issue && focusCoordinate.value) {
    const preceding = report.value?.observationEvidence
      ?.slice(0, (issue.rawPointIndex ?? -1) + 1)
      .reverse()
      .find((point) => point.canonicalPointIndex != null);
    const index = issue.canonicalPointIndex ?? preceding?.canonicalPointIndex ?? 0;
    points = [...points.slice(Math.max(0, index - 5), index + 6), focusCoordinate.value];
  }
  if (!points.length) return null;
  let minX = Infinity,
    maxX = -Infinity,
    minY = Infinity,
    maxY = -Infinity;
  for (const point of points) {
    const x = point[0],
      y = point[1];
    if (x == null || y == null) continue;
    minX = Math.min(minX, x);
    maxX = Math.max(maxX, x);
    minY = Math.min(minY, y);
    maxY = Math.max(maxY, y);
  }
  return Number.isFinite(minX)
    ? [
        [minX - 0.0002, minY - 0.0002],
        [maxX + 0.0002, maxY + 0.0002],
      ]
    : null;
});
const quantity = (value: number | undefined, unit: string) => (value == null ? '—' : `${value.toFixed(1)} ${unit}`);
</script>

<template>
  <section class="gps-review" data-test="gps-review">
    <button
      type="button"
      class="review-button"
      :disabled="loading"
      :aria-expanded="expanded"
      aria-controls="track-gps-review-content"
      @click="report ? (expanded = !expanded) : load()"
    >
      <i class="bi bi-map" aria-hidden="true"></i>
      <span class="review-button__text">
        <strong>{{ loading ? 'Loading review…' : 'Review GPS observations' }}</strong>
        <span>Compare the original recording with the processed path.</span>
      </span>
      <i :class="expanded ? 'bi bi-chevron-up' : 'bi bi-chevron-down'" aria-hidden="true"></i>
    </button>
    <p v-if="error" role="alert">{{ error }}</p>
    <div v-if="report && expanded" id="track-gps-review-content" class="review-content">
      <p>
        Original observations and the processed path. Gaps remain disconnected. Decisions describe processing; a smooth
        path can still contain GPS drift.
      </p>
      <div class="review-controls">
        <label><input v-model="showRaw" type="checkbox" /> Original (red)</label>
        <label><input v-model="showProcessed" type="checkbox" /> Processed (blue)</label>
        <button type="button" @click="selected = null">Show whole track</button>
      </div>
      <div class="review-map"><MiniMap :tracks-geo-json="geojson" :map-bounds="bounds" /></div>
      <p v-if="!report.processingSettings?.assessment">
        No cleanup details were saved for this import. Re-import the file to get them.
      </p>
      <p v-else>
        {{ report.processingSettings.algorithm }} · {{ report.totalIssueCount ?? 0 }} review observations. Ordered by
        possible distance, altitude, or time impact; this is not a confidence score.
      </p>
      <div v-if="selected" class="selected-issue" aria-live="polite">
        <strong>{{ label(selected) }}</strong>
        <span
          >Position: {{ quantity(selected.horizontalMetres, 'm') }} · Altitude:
          {{ quantity(selected.verticalMetres, 'm') }} · Time: {{ quantity(selected.durationSeconds, 's') }}</span
        >
        <span v-if="selectedSource"
          >Source point {{ (selectedSource.sourcePointIndex ?? 0) + 1 }} · Section
          {{ (selectedSource.sourceRecordingSection ?? 0) + 1 }} · Fix {{ selectedSource.fix ?? '—' }} · Satellites
          {{ selectedSource.satellites ?? '—' }} · HDOP {{ selectedSource.hdop ?? '—' }} · VDOP
          {{ selectedSource.vdop ?? '—' }}</span
        >
      </div>
      <div v-if="report.issues?.length" class="review-issues">
        <button
          v-for="(issue, index) in report.issues"
          :key="index"
          type="button"
          :aria-pressed="selected === issue"
          @click="selected = issue"
        >
          <span>{{ label(issue) }}</span>
          <span>{{
            issue.rawPointIndex != null
              ? `Source point ${(report.observationEvidence?.[issue.rawPointIndex]?.sourcePointIndex ?? issue.rawPointIndex) + 1}`
              : `Processed point ${(issue.canonicalPointIndex ?? 0) + 1}`
          }}</span>
        </button>
      </div>
      <p v-else-if="report.processingSettings?.assessment">
        No problems found. This does not guarantee that every point is correct.
      </p>
      <p v-if="(report.totalIssueCount ?? 0) > (report.issues?.length ?? 0)">
        Showing the {{ report.issues?.length }} highest-impact observations.
      </p>
    </div>
  </section>
</template>

<style scoped>
.gps-review {
  display: grid;
  gap: 0.75rem;
  padding: 0;
  overflow: hidden;
  border: 1px solid var(--border-default);
  border-radius: 0.5rem;
}
.gps-review p {
  margin: 0;
  color: var(--text-secondary);
  font-size: 0.85rem;
}
.review-controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 1rem;
  font-size: 0.85rem;
}
.review-controls label {
  display: flex;
  gap: 0.4rem;
  align-items: center;
}
.review-map {
  height: clamp(200px, 30vh, 320px);
  overflow: hidden;
  border-radius: 0.4rem;
}
.review-button,
.review-controls button,
.review-issues button {
  background: var(--surface-card);
  color: var(--text-primary);
  border: 1px solid var(--border-default);
  border-radius: 0.3rem;
  padding: 0.6rem 0.75rem;
  min-height: 2.75rem;
  cursor: pointer;
  text-align: left;
}
.review-issues {
  max-height: 260px;
  overflow: auto;
  display: grid;
  gap: 0.3rem;
}
.review-issues button {
  display: flex;
  justify-content: space-between;
  gap: 0.8rem;
  font-size: 0.8rem;
}
.review-issues button[aria-pressed='true'] {
  border-color: #2678c7;
}
.selected-issue {
  display: grid;
  gap: 0.4rem;
  font-size: 0.85rem;
}
.review-button {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 0.75rem;
  border: 0;
  background: var(--surface-elevated);
}
.review-button__text {
  display: grid;
  flex: 1;
  gap: 0.2rem;
}
.review-button__text strong {
  font-size: var(--text-sm-size);
  font-weight: 600;
}
.review-button__text > span {
  font-size: var(--text-xs-size);
  color: var(--text-muted);
  line-height: 1.5;
}
.review-content {
  display: grid;
  gap: 0.75rem;
  padding: 0 0.75rem 0.75rem;
}
.review-controls label {
  min-height: 2.75rem;
}
.review-controls {
  gap: 0.25rem 0.75rem;
}
.gps-review > p {
  padding: 0 0.75rem 0.75rem;
}
.gps-review button:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: -2px;
}
@container track-details (max-width: 480px) {
  .review-issues button {
    flex-direction: column;
    gap: 0.2rem;
  }
  .review-issues button > span:last-child {
    color: var(--text-muted);
  }
}
</style>
