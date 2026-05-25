<template>
  <div class="sc-container">
    <!-- Empty state: no tracks selected -->
    <div v-if="selectedTrackIds.size === 0" class="sc-empty">
      <div class="sc-empty-icon"><i class="bi bi-check2-square"></i></div>
      <h3 class="sc-empty-head">Select tracks to compare</h3>
      <p class="sc-empty-body">
        Go to the <strong>Table</strong> tab and tick the checkboxes of the tracks you want to overlay.
      </p>
      <button class="sc-empty-btn" @click="emit('goto-table')"><i class="bi bi-table"></i> Open table</button>
    </div>

    <!-- Controls -->
    <div v-else class="sc-controls">
      <div class="sc-control-row">
        <label class="sc-label">Segment</label>
        <div class="sc-chip-row sc-chip-scroll">
          <button
            v-for="seg in availableSegments"
            :key="segmentChipKey(seg)"
            class="sc-chip"
            :class="{ 'sc-chip--active': selectedSegmentKey === segmentChipKey(seg) }"
            @click="localSegment = seg.code"
          >
            {{ seg.name }}
          </button>
        </div>
      </div>

      <div v-if="!localSegment" class="sc-placeholder sc-placeholder--inline">
        <i class="bi bi-signpost-split"></i>
        <p>Pick a <strong>Segment</strong> above to start comparing.</p>
      </div>

      <div class="sc-control-row">
        <label class="sc-label">X-axis</label>
        <div class="sc-chip-row">
          <button class="sc-chip" :class="{ 'sc-chip--active': xMode === 'distance' }" @click="xMode = 'distance'">
            Distance
          </button>
          <button class="sc-chip" :class="{ 'sc-chip--active': xMode === 'time' }" @click="xMode = 'time'">Time</button>
        </div>
      </div>

      <div class="sc-control-row">
        <label class="sc-label">Metrics</label>
        <div class="sc-chip-row">
          <button
            v-for="m in availableMetrics"
            :key="m.key"
            class="sc-chip sc-chip--metric"
            :class="{ 'sc-chip--active': selectedMetrics.has(m.key) }"
            @click="toggleMetric(m.key)"
          >
            <i class="bi" :class="m.icon"></i> {{ m.label }}
          </button>
        </div>
      </div>
    </div>

    <!-- Loading / nothing loaded yet -->
    <div v-if="!loading && !hasData && selectedTrackIds.size > 0 && localSegment" class="sc-placeholder">
      <i class="bi bi-graph-up"></i>
      <p v-if="matchingCandidates.length === 0">
        None of the selected tracks cross this segment. Try a different segment or add tracks.
      </p>
      <p v-else>Preparing comparison…</p>
    </div>

    <div v-if="loading" class="sc-placeholder">
      <i class="bi bi-hourglass-split"></i>
      <p>Loading sub-tracks for {{ tracksToFetchCount }} track{{ tracksToFetchCount === 1 ? '' : 's' }}…</p>
    </div>

    <div v-if="unmatchedCount > 0 && !loading" class="sc-warning">
      <i class="bi bi-info-circle"></i>
      {{ unmatchedCount }} of {{ selectedTrackIds.size }} selected tracks don't cross this segment and were skipped.
    </div>

    <!-- Results -->
    <div v-if="hasData" class="sc-results">
      <!-- Track legend -->
      <div class="sc-legend">
        <div class="sc-legend-grid">
          <RacerCard
            v-for="(r, i) in racers"
            :key="r.trackId + '-' + i"
            :color="r.color"
            :name="r.name"
            :date-str="r.dateStr"
            :track-id="r.trackId"
            :activity-type="r.activityType"
            :highlighted="gapReferenceTrackId === r.trackId"
            :clickable="true"
            :stats="racerStats(r)"
            title="Click to use as time-gap reference"
            @click="setGapReference(r.trackId)"
            @open-details="openTrackDetails"
          />
        </div>
        <div v-if="gapReferenceTrackId != null" class="sc-legend-hint">
          <i class="bi bi-flag"></i>
          Reference: <strong>{{ referenceName }}</strong>
          <button class="sc-legend-clear" @click="gapReferenceTrackId = null">clear</button>
        </div>
      </div>

      <!-- Mini map -->
      <div class="sc-minimap-wrap">
        <MiniMap ref="minimapRef" :tracks-geo-json="mapGeoJson" :map-bounds="mapBounds" class="sc-minimap" />
      </div>

      <!-- Charts -->
      <div class="sc-charts">
        <ComparisonChart
          v-for="m in visibleMetrics"
          :key="m.key"
          :title="m.label"
          :icon="m.icon"
          :subtitle="m.subtitle || ''"
          :series="seriesByMetric[m.key] || []"
          :x-mode="xMode"
          :unit="m.unit"
          :decimals="m.decimals"
          :y-min="m.yMin"
          :y-zero-line="m.key === 'timeGap'"
          @hover-x="onHoverX"
          @hover-leave="onHoverLeave"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, inject, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import type { CrossingPointsResponse, GpsTrackDataPoint } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import ComparisonChart from '@/components/measure/ComparisonChart.vue';
import MiniMap from '@/components/map/MiniMap.vue';
import type { MiniMapBounds, MiniMapGeoJson } from '@/components/map/useMiniMap';
import RacerCard from '@/components/ui/RacerCard.vue';
import { fetchTrackSubTrackDetails } from '@/utils/ServiceHelper';
import { generateColors, formatDuration, formatNumber, formatDateAndTime } from '@/utils/Utils';

defineOptions({ name: 'SegmentCompare' });

type ToastService = { add: (message: Record<string, unknown>) => void };
type SegmentCode = { point1?: string; point2?: string; consolidated?: boolean; p1Visit?: number; p2Visit?: number };
type AvailableSegment = { name?: string; count?: number; code: SegmentCode };
type TrackPoint = Omit<GpsTrackDataPoint, 'pointLongLat'> & { pointLongLat?: { coordinates?: number[] } };
type SegmentGpsTrack = {
  activityType?: string;
  indexedFile?: { name?: string };
  startDate?: Date | string;
};
type SegmentCrossing = {
  gpsTrackDataPoint?: TrackPoint & { id?: number };
  triggerPoint: { name: string };
};
type SegmentCrossingsPerTrack = {
  crossings?: SegmentCrossing[];
  gpsTrack?: SegmentGpsTrack;
};
type ComparisonSeries = {
  color: string;
  dashStyle?: 'Solid' | 'Dash' | 'ShortDash';
  data: Array<[number, number | null] | [number, number | null, number]>;
  name: string;
};
type LoadedData = { trackId: number; gpsTrack?: SegmentGpsTrack; crossingEnd: SegmentCrossing; points: TrackPoint[] };
type MatchingCandidate = {
  crossingEnd: SegmentCrossing;
  fromId: number;
  gpsTrack?: SegmentGpsTrack;
  toId: number;
  trackId: number;
};
type RacerSummary = {
  trackId: number;
  color: string;
  name: string;
  dateStr: string;
  activityType: string | null;
  durationSec: number;
  distanceM: number;
  avgSpeedKmh: number;
  ascentM: number | null;
  energyWh: number | null;
  powerMaxW: number | null;
};
type MetricDef = {
  key: string;
  label: string;
  icon: string;
  unit: string;
  decimals: number;
  yMin?: number;
  subtitle?: string;
  extractY?: (point: TrackPoint, base: TrackPoint) => number | null | undefined;
  filterNull?: boolean;
  synthetic?: 'pacing' | 'timeGap';
};

const METRIC_DEFS: MetricDef[] = [
  {
    key: 'speed',
    label: 'Speed',
    icon: 'bi-speedometer2',
    unit: 'km/h',
    decimals: 1,
    yMin: 0,
    extractY: (p) => p.speedInKmhMovingWindow,
  },
  {
    key: 'altitude',
    label: 'Altitude',
    icon: 'bi-graph-up-arrow',
    unit: 'm',
    decimals: 0,
    extractY: (p) => p.pointAltitude,
  },
  {
    key: 'power',
    label: 'Est. Power',
    icon: 'bi-lightning-charge',
    unit: 'W',
    decimals: 0,
    yMin: 0,
    extractY: (p) => (p.powerWatts != null ? Math.round(p.powerWatts) : null),
    filterNull: true,
  },
  {
    key: 'energy',
    label: 'Mechanical Energy (rebased)',
    icon: 'bi-battery-charging',
    unit: 'Wh',
    decimals: 0,
    yMin: 0,
    extractY: (p, base) =>
      p.energyCumulativeWh != null ? p.energyCumulativeWh - (base.energyCumulativeWh || 0) : null,
    filterNull: true,
  },
  {
    key: 'slope',
    label: 'Slope',
    icon: 'bi-triangle',
    unit: '%',
    decimals: 1,
    extractY: (p) => p.slopePercentageInMovingWindow,
    filterNull: true,
  },
  {
    key: 'pacing',
    label: 'Pacing — time over distance',
    icon: 'bi-hourglass-split',
    unit: 's',
    decimals: 0,
    yMin: 0,
    subtitle: 'Steeper line = slower. Best for distance x-axis.',
    // Pacing is synthesized in buildSeries (y = elapsed seconds since segment start).
    synthetic: 'pacing',
  },
  {
    key: 'timeGap',
    label: 'Time gap vs. reference',
    icon: 'bi-flag',
    unit: 's',
    decimals: 0,
    subtitle: 'Click a track card to set the reference. Negative = ahead, positive = behind.',
    synthetic: 'timeGap',
  },
];

const DEFAULT_METRICS = ['speed', 'altitude'];
const FETCH_CONCURRENCY = 5;

const props = withDefaults(
  defineProps<{
    measureServiceResult: CrossingPointsResponse;
    consolidateVisits?: boolean;
    selectedTrackIds: Set<number>;
    selectedSegment?: SegmentCode | null;
    availableSegments?: AvailableSegment[];
  }>(),
  {
    consolidateVisits: true,
    selectedSegment: null,
    availableSegments: () => [],
  }
);

const emit = defineEmits<{
  'show-track-details': [trackId: number | string];
  'goto-table': [];
}>();

const toast = inject<ToastService>('toast');
const minimapRef = ref<{ invalidateMapSize?: () => void } | null>(null);

const localSegment = ref<SegmentCode | null>(null);
const xMode = ref<'distance' | 'time'>('distance');
const selectedMetrics = ref(new Set(DEFAULT_METRICS));
const availableMetrics = METRIC_DEFS;
const loading = ref(false);
/** Array of { trackId, gpsTrack, crossingPair, points } */
const loadedData = ref<LoadedData[]>([]);
const unmatchedCount = ref(0);
const gapReferenceTrackId = ref<number | null>(null);
const hoverX = ref<number | null>(null);
const loadKey = ref<number | null>(null);
let autoLoadTimer: ReturnType<typeof setTimeout> | null = null;

const selectedSegmentKey = computed(() => {
  return localSegment.value ? segmentChipKey({ code: localSegment.value }) : null;
});

const tracksToFetchCount = computed(() => matchingCandidates.value.length);

function asSegmentCrossingsPerTrack(value: unknown): SegmentCrossingsPerTrack {
  return value as SegmentCrossingsPerTrack;
}

/** Tracks matching selection + segment, as {trackId, gpsTrack, fromId, toId, crossingEnd}. */
const matchingCandidates = computed<MatchingCandidate[]>(() => {
  if (!props.measureServiceResult || !localSegment.value) return [];
  const seg = localSegment.value;
  const out: MatchingCandidate[] = [];
  for (const [trackId, trackCrossingsRaw] of Object.entries(props.measureServiceResult.crossings || {})) {
    const trackCrossings = asSegmentCrossingsPerTrack(trackCrossingsRaw);
    const tid = Number(trackId);
    if (!props.selectedTrackIds.has(tid)) continue;
    const countPerTP = new Map<string, number>();
    let last: SegmentCrossing | null = null;
    for (const c of trackCrossings.crossings || []) {
      const name = c.triggerPoint.name;
      countPerTP.set(name, (countPerTP.get(name) || 0) + 1);
      if (
        last != null &&
        matchesSegment(last, c, countPerTP, seg) &&
        last.gpsTrackDataPoint?.id != null &&
        c.gpsTrackDataPoint?.id != null
      ) {
        out.push({
          trackId: tid,
          gpsTrack: trackCrossings.gpsTrack,
          fromId: last.gpsTrackDataPoint.id,
          toId: c.gpsTrackDataPoint.id,
          crossingEnd: c,
        });
        // Only take the first match per track to keep the comparison unambiguous.
        break;
      }
      last = c;
    }
  }
  return out;
});

const hasData = computed(() => loadedData.value && loadedData.value.length > 0);

/** Per-track summary rows + stable colors. */
const racers = computed<RacerSummary[]>(() => {
  if (!hasData.value) return [];
  const ids = loadedData.value.map((d) => d.trackId);
  const palette = generateColors(ids.length);
  return loadedData.value.map((d, i) => {
    const first = d.points[0];
    const last = d.points[d.points.length - 1];
    const durationSec = (last.durationSinceStart || 0) - (first.durationSinceStart || 0);
    const distanceM = (last.distanceInMeterSinceStart || 0) - (first.distanceInMeterSinceStart || 0);
    const avgSpeedKmh = durationSec > 0 ? (distanceM / durationSec) * 3.6 : 0;
    let ascentM = null;
    if (last.ascentInMeterSinceStart != null && first.ascentInMeterSinceStart != null) {
      ascentM = last.ascentInMeterSinceStart - first.ascentInMeterSinceStart;
    }
    let energyWh = null;
    if (last.energyCumulativeWh != null && first.energyCumulativeWh != null) {
      energyWh = last.energyCumulativeWh - first.energyCumulativeWh;
    }
    let powerMaxW = null;
    for (const p of d.points) {
      if (p.powerWatts != null && (powerMaxW == null || p.powerWatts > powerMaxW)) powerMaxW = p.powerWatts;
    }
    return {
      trackId: d.trackId,
      color: palette[i],
      name: d.gpsTrack?.indexedFile?.name || 'Track ' + d.trackId,
      dateStr: d.gpsTrack?.startDate ? formatDateAndTime(d.gpsTrack.startDate) : '',
      activityType: d.gpsTrack?.activityType || null,
      durationSec,
      distanceM,
      avgSpeedKmh,
      ascentM,
      energyWh,
      powerMaxW,
    };
  });
});

const visibleMetrics = computed(() => {
  return availableMetrics.filter((m) => selectedMetrics.value.has(m.key));
});

/** seriesByMetric[metricKey] = ComparisonSeries[] */
const seriesByMetric = computed<Record<string, ComparisonSeries[]>>(() => {
  const out: Record<string, ComparisonSeries[]> = {};
  if (!hasData.value) return out;
  for (const metric of visibleMetrics.value) {
    out[metric.key] = buildSeriesForMetric(metric);
  }
  return out;
});

const referenceName = computed(() => {
  const r = racers.value.find((r) => r.trackId === gapReferenceTrackId.value);
  return r ? r.name : '';
});

/** GeoJSON feature collection with one LineString per loaded track + trigger points + hover dots. */
const mapGeoJson = computed<MiniMapGeoJson | null>(() => {
  if (!hasData.value) return null;
  const features: MiniMapGeoJson['features'] = [];

  // Trigger points for the selected segment.
  const seg = localSegment.value;
  if (seg && props.measureServiceResult?.triggerPoints) {
    const names = new Set([seg.point1, seg.point2]);
    for (const tp of props.measureServiceResult.triggerPoints) {
      if (!names.has(tp.name)) continue;
      if (tp.coordinate?.x == null || tp.coordinate?.y == null) continue;
      features.push({
        type: 'Feature',
        geometry: { type: 'Point', coordinates: [tp.coordinate.x, tp.coordinate.y] },
        properties: { type: 'trigger', label: tp.name, name: tp.name, color: '#888888' },
      });
    }
  }

  // Track lines.
  loadedData.value.forEach((d, i) => {
    const color = racers.value[i]?.color;
    const coords = [];
    for (const p of d.points) {
      if (!p.pointLongLat?.coordinates) continue;
      coords.push([p.pointLongLat.coordinates[0], p.pointLongLat.coordinates[1]]);
    }
    if (coords.length < 2) return;
    features.push({
      type: 'Feature',
      geometry: { type: 'LineString', coordinates: coords },
      properties: { type: 'track', trackIndex: i, color, trackName: racers.value[i]?.name },
    });
  });

  // Hover dots (one per track) at the current hover-x, if any.
  if (hoverX.value != null) {
    loadedData.value.forEach((d, i) => {
      const color = racers.value[i]?.color;
      const point = findPointForHoverX(d, hoverX.value as number);
      if (!point?.pointLongLat?.coordinates) return;
      features.push({
        type: 'Feature',
        geometry: {
          type: 'Point',
          coordinates: [point.pointLongLat.coordinates[0], point.pointLongLat.coordinates[1]],
        },
        properties: { type: 'racer', trackIndex: i, color, trackName: racers.value[i]?.name },
      });
    });
  }

  return { type: 'FeatureCollection', features } as MiniMapGeoJson;
});

const mapBounds = computed<MiniMapBounds | null>(() => {
  if (!hasData.value) return null;
  let latMin = Number.POSITIVE_INFINITY,
    latMax = Number.NEGATIVE_INFINITY;
  let lngMin = Number.POSITIVE_INFINITY,
    lngMax = Number.NEGATIVE_INFINITY;
  for (const d of loadedData.value) {
    for (const p of d.points) {
      const c = p.pointLongLat?.coordinates;
      if (!c) continue;
      if (c[0] < lngMin) lngMin = c[0];
      if (c[0] > lngMax) lngMax = c[0];
      if (c[1] < latMin) latMin = c[1];
      if (c[1] > latMax) latMax = c[1];
    }
  }
  if (!Number.isFinite(latMin)) return null;
  const dLat = Math.max((latMax - latMin) * 0.15, 0.0005);
  const dLng = Math.max((lngMax - lngMin) * 0.15, 0.0005);
  return [
    [lngMin - dLng, latMin - dLat],
    [lngMax + dLng, latMax + dLat],
  ];
});

function segmentChipKey(seg: AvailableSegment) {
  const code = seg.code;
  if (!code) return '';
  if (code.consolidated === false) {
    return String(code.point1) + String(code.p1Visit) + '-' + String(code.point2) + String(code.p2Visit);
  }
  return String(code.point1) + '||' + String(code.point2);
}

function scheduleAutoLoad(delayMs = 300) {
  if (autoLoadTimer) clearTimeout(autoLoadTimer);
  autoLoadTimer = setTimeout(() => {
    autoLoadTimer = null;
    if (!localSegment.value) return;
    if (props.selectedTrackIds.size === 0) return;
    onLoad();
  }, delayMs);
}

function matchesSegment(
  prev: SegmentCrossing,
  curr: SegmentCrossing,
  countPerTP: Map<string, number>,
  seg: SegmentCode
) {
  if (seg.point1 !== prev.triggerPoint?.name) return false;
  if (seg.point2 !== curr.triggerPoint?.name) return false;
  if (seg.consolidated === false) {
    if (seg.p1Visit !== countPerTP.get(prev.triggerPoint.name)) return false;
    if (seg.p2Visit !== countPerTP.get(curr.triggerPoint.name)) return false;
  }
  return true;
}

function toggleMetric(key: string) {
  const next = new Set(selectedMetrics.value);
  if (next.has(key)) next.delete(key);
  else next.add(key);
  // Always keep at least one metric visible.
  if (next.size === 0) next.add(key);
  selectedMetrics.value = next;
}

function setGapReference(trackId: number) {
  gapReferenceTrackId.value = gapReferenceTrackId.value === trackId ? null : trackId;
  // Auto-show the time-gap chart when a reference is picked.
  if (gapReferenceTrackId.value != null && !selectedMetrics.value.has('timeGap')) {
    const next = new Set(selectedMetrics.value);
    next.add('timeGap');
    selectedMetrics.value = next;
  }
}

function openTrackDetails(id: number | string) {
  emit('show-track-details', id);
}

function racerStats(r: RacerSummary) {
  const stats = [
    { icon: 'bi-stopwatch', text: formatDuration(r.durationSec * 1000), title: 'Segment duration' },
    { icon: 'bi-speedometer', text: formatNumber(r.avgSpeedKmh, 1) + ' km/h', title: 'Average speed' },
    { icon: 'bi-signpost-split', text: formatNumber(r.distanceM / 1000, 2) + ' km', title: 'Segment distance' },
  ];
  if (r.ascentM != null) stats.push({ icon: 'bi-arrow-up', text: formatNumber(r.ascentM, 0) + ' m', title: 'Ascent' });
  if (r.energyWh != null)
    stats.push({
      icon: 'bi-battery-charging',
      text: formatNumber(r.energyWh, 0) + ' Wh',
      title: 'Estimated mechanical energy',
    });
  if (r.powerMaxW != null)
    stats.push({
      icon: 'bi-lightning-charge',
      text: formatNumber(r.powerMaxW, 0) + ' W',
      title: 'Estimated max power',
    });
  return stats;
}

function onHoverX(x: number) {
  // Round a touch to reduce reactive churn; GeoJSON source is rebuilt on change.
  hoverX.value = x;
}

function onHoverLeave() {
  hoverX.value = null;
}

/** Find the first point in a loaded track whose current x-value >= hoverX. */
function findPointForHoverX(d: LoadedData, currentHoverX: number) {
  if (!d || !d.points || d.points.length === 0) return null;
  const first = d.points[0];
  const baseDist = first.distanceInMeterSinceStart || 0;
  const baseTime = first.durationSinceStart || 0;
  // Use x-mode matching what the charts are displaying. For 'timeGap' and 'pacing'
  // charts, x is still distance or time (synthesized metrics only change y), so this works.
  for (const p of d.points) {
    const x = xFor(p, baseDist, baseTime);
    if (x >= currentHoverX) return p;
  }
  return d.points[d.points.length - 1];
}

async function onLoad() {
  const candidates = matchingCandidates.value;
  unmatchedCount.value = props.selectedTrackIds.size - candidates.length;
  if (candidates.length === 0) {
    loadedData.value = [];
    // No toast here: this runs auto-reactively and would otherwise spam on
    // every segment / track-selection change. The placeholder UI explains.
    return;
  }
  loading.value = true;
  const currentLoadKey = Date.now();
  loadKey.value = currentLoadKey;
  try {
    const results = await fetchInBatches(candidates, FETCH_CONCURRENCY);
    // Guard against stale responses if user kicked a second load before the first finished.
    if (loadKey.value !== currentLoadKey) return;
    loadedData.value = results;
    // Default gap reference to the fastest track (shortest segment duration).
    if (gapReferenceTrackId.value == null && racers.value.length > 0) {
      const fastest = racers.value.slice().sort((a, b) => a.durationSec - b.durationSec)[0];
      gapReferenceTrackId.value = fastest.trackId;
    }
    // The MiniMap is v-if'd on hasData; give it a tick to mount, then force a resize
    // so MapLibre picks up the container size.
    nextTick(() => {
      setTimeout(() => {
        if (minimapRef.value?.invalidateMapSize) minimapRef.value.invalidateMapSize();
      }, 50);
    });
  } catch (e) {
    console.error('SegmentCompare load failed', e);
    toast?.add({
      severity: 'error',
      summary: 'Load failed',
      detail: 'Could not fetch sub-track data. See console.',
      life: 4000,
    });
  } finally {
    if (loadKey.value === currentLoadKey) loading.value = false;
  }
}

async function fetchInBatches(candidates: MatchingCandidate[], batchSize: number) {
  const results: LoadedData[] = [];
  for (let i = 0; i < candidates.length; i += batchSize) {
    const batch = candidates.slice(i, i + batchSize);
    const batchResults = await Promise.all(
      batch.map(async (c) => {
        const points = (await fetchTrackSubTrackDetails(c.fromId, c.toId)) as TrackPoint[];
        return { trackId: c.trackId, gpsTrack: c.gpsTrack, crossingEnd: c.crossingEnd, points };
      })
    );
    for (const r of batchResults) {
      if (r.points && r.points.length >= 2) results.push(r);
    }
  }
  return results;
}

function buildSeriesForMetric(metric: MetricDef) {
  const series: ComparisonSeries[] = [];
  const refTrackId = gapReferenceTrackId.value;
  // Build reference pacing curve once (x in km → cumulative seconds) for timeGap.
  let refCurve: Array<{ xKm: number; tSec: number }> | null = null;
  if (metric.synthetic === 'timeGap' && refTrackId != null) {
    const refData = loadedData.value.find((d) => d.trackId === refTrackId);
    if (refData) refCurve = buildPacingLookup(refData);
  }
  loadedData.value.forEach((d, idx) => {
    const racer = racers.value[idx];
    const color = racer.color;
    const first = d.points[0];
    const baseDist = first.distanceInMeterSinceStart || 0;
    const baseTime = first.durationSinceStart || 0;

    if (metric.synthetic === 'pacing') {
      const data: ComparisonSeries['data'] = [];
      for (const p of d.points) {
        const ySec = (p.durationSinceStart || 0) - baseTime;
        const ts = p.pointTimestamp ? toMs(p.pointTimestamp) : undefined;
        data.push(ts != null ? [xFor(p, baseDist, baseTime), ySec, ts] : [xFor(p, baseDist, baseTime), ySec]);
      }
      series.push({ name: racer.name, color, data });
      return;
    }

    if (metric.synthetic === 'timeGap') {
      if (!refCurve || d.trackId === refTrackId) {
        // Reference track is flat at zero across its own distance range.
        if (d.trackId === refTrackId) {
          const data: ComparisonSeries['data'] = [];
          for (const p of d.points) {
            data.push([xFor(p, baseDist, baseTime), 0]);
          }
          series.push({ name: racer.name + ' (ref)', color, dashStyle: 'ShortDash', data });
        }
        return;
      }
      const data: ComparisonSeries['data'] = [];
      for (const p of d.points) {
        const xKm = ((p.distanceInMeterSinceStart || 0) - baseDist) / 1000;
        const elapsed = (p.durationSinceStart || 0) - baseTime;
        const refElapsed = interpRefTime(refCurve, xKm);
        if (refElapsed == null) continue;
        const gap = elapsed - refElapsed; // +: behind, -: ahead
        data.push([xFor(p, baseDist, baseTime), gap]);
      }
      series.push({ name: racer.name, color, data });
      return;
    }

    // Regular per-point metric.
    const data: ComparisonSeries['data'] = [];
    for (const p of d.points) {
      let y = metric.extractY ? metric.extractY(p, first) : null;
      if (metric.filterNull && (y == null || Number.isNaN(y))) continue;
      if (y == null) y = null;
      const ts = p.pointTimestamp ? toMs(p.pointTimestamp) : undefined;
      const x = xFor(p, baseDist, baseTime);
      data.push(ts != null ? [x, y, ts] : [x, y]);
    }
    series.push({ name: racer.name, color, data });
  });
  return series;
}

function xFor(point: TrackPoint, baseDist: number, baseTime: number) {
  if (xMode.value === 'distance') {
    return ((point.distanceInMeterSinceStart || 0) - baseDist) / 1000; // km
  }
  return ((point.durationSinceStart || 0) - baseTime) * 1000; // ms since segment start
}

function toMs(d: Date | string | number | null | undefined) {
  if (d == null) return undefined;
  if (d instanceof Date) return d.getTime();
  const t = new Date(d).getTime();
  return Number.isNaN(t) ? undefined : t;
}

/** Build [{xKm, tSec}] sorted by xKm for a given track. */
function buildPacingLookup(d: LoadedData) {
  const first = d.points[0];
  const baseDist = first.distanceInMeterSinceStart || 0;
  const baseTime = first.durationSinceStart || 0;
  const arr = [];
  for (const p of d.points) {
    const xKm = ((p.distanceInMeterSinceStart || 0) - baseDist) / 1000;
    const tSec = (p.durationSinceStart || 0) - baseTime;
    arr.push({ xKm, tSec });
  }
  arr.sort((a, b) => a.xKm - b.xKm);
  return arr;
}

/** Linear interpolation of elapsed-seconds for a given distance (km) on the reference curve. */
function interpRefTime(refCurve: Array<{ xKm: number; tSec: number }> | null, xKm: number) {
  if (!refCurve || refCurve.length === 0) return null;
  if (xKm <= refCurve[0].xKm) return refCurve[0].tSec;
  if (xKm >= refCurve[refCurve.length - 1].xKm) return refCurve[refCurve.length - 1].tSec;
  // Binary search could be used; linear is fine for a few thousand points.
  for (let i = 1; i < refCurve.length; i++) {
    const a = refCurve[i - 1],
      b = refCurve[i];
    if (xKm >= a.xKm && xKm <= b.xKm) {
      const span = b.xKm - a.xKm;
      if (span <= 0) return a.tSec;
      const f = (xKm - a.xKm) / span;
      return a.tSec + f * (b.tSec - a.tSec);
    }
  }
  return null;
}

watch(
  () => props.selectedSegment,
  (val) => {
    // Sync initial value from parent; don't override if user already picked manually.
    if (val && !localSegment.value) localSegment.value = val;
  },
  { immediate: true }
);

watch(localSegment, () => {
  loadedData.value = [];
  gapReferenceTrackId.value = null;
  scheduleAutoLoad();
});

watch(
  () => props.selectedTrackIds,
  () => {
    scheduleAutoLoad();
  }
);

watch(
  () => props.consolidateVisits,
  () => {
    loadedData.value = [];
    gapReferenceTrackId.value = null;
  }
);

onMounted(() => {
  // First load if a segment is already picked (usually yes, parent auto-picks).
  scheduleAutoLoad(0);
});

onBeforeUnmount(() => {
  if (autoLoadTimer) clearTimeout(autoLoadTimer);
});
</script>

<style scoped>
.sc-container {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 0.5rem 0.75rem calc(1rem + var(--safe-bottom, 0px));
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  -webkit-overflow-scrolling: touch;
  overscroll-behavior-y: contain;
}

/* ── Empty state ── */
.sc-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.55rem;
  padding: 2.5rem 1.25rem;
  text-align: center;
  color: var(--text-secondary);
}
.sc-empty-icon {
  font-size: var(--text-4xl-size);
  color: var(--text-muted);
  opacity: 0.8;
}
.sc-empty-head {
  margin: 0;
  font-size: var(--text-base-size);
  font-weight: 600;
  color: var(--text-primary, var(--text-secondary));
}
.sc-empty-body {
  margin: 0;
  font-size: var(--text-sm-size);
  max-width: 28rem;
}
.sc-empty-btn {
  margin-top: 0.35rem;
  padding: 0.4rem 0.85rem;
  border-radius: 6px;
  border: 1px solid var(--border-default);
  background: var(--surface-glass);
  color: var(--text-secondary);
  font-size: var(--text-sm-size);
  font-weight: 600;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
}
.sc-empty-btn:hover {
  background: var(--surface-hover);
  color: var(--accent-text);
}

/* ── Controls ── */
.sc-controls {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  padding: 0.1rem 0.1rem 0.35rem;
  border-bottom: 1px solid var(--border-subtle);
}
.sc-control-row {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  flex-wrap: wrap;
}
.sc-chip-scroll {
  display: flex;
  gap: 0.35rem;
  overflow-x: auto;
  flex-wrap: nowrap;
  flex: 1 1 0;
  min-width: 0;
  padding-bottom: 2px;
}
.sc-chip-scroll::-webkit-scrollbar {
  height: 3px;
}
.sc-chip-scroll::-webkit-scrollbar-thumb {
  background: var(--border-default);
  border-radius: 3px;
}
.sc-label {
  font-size: var(--text-xs-size);
  font-weight: 600;
  color: var(--text-secondary);
  min-width: 4.5rem;
}
.sc-segment-select {
  min-width: 12rem;
}
.sc-load-btn {
  margin-left: auto;
}

.sc-chip-row {
  display: inline-flex;
  gap: 0.3rem;
  flex-wrap: wrap;
}
.sc-chip {
  padding: 0.3rem 0.65rem;
  border-radius: 999px;
  border: 1px solid var(--border-default);
  background: var(--surface-glass);
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  font-weight: 600;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  font-family: inherit;
  white-space: nowrap;
}
.sc-chip:hover {
  color: var(--text-secondary);
  background: var(--surface-hover);
}
.sc-chip--active {
  background: var(--accent-text);
  color: var(--text-inverse);
  border-color: var(--accent-text);
}

/* ── Placeholder ── */
.sc-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.4rem;
  padding: 1.75rem 1rem;
  text-align: center;
  color: var(--text-muted);
  font-size: var(--text-sm-size);
}
.sc-placeholder i {
  font-size: var(--text-3xl-size);
  opacity: 0.7;
}

.sc-placeholder--inline {
  padding: 0.9rem 1rem;
  border: 1px dashed var(--border-default);
  border-radius: 8px;
  background: var(--surface-glass);
}

.sc-placeholder--inline i {
  font-size: var(--text-lg-size);
}

.sc-warning {
  padding: 0.5rem 0.75rem;
  border-radius: 6px;
  background: var(--surface-glass);
  border: 1px dashed var(--border-default);
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
  display: flex;
  align-items: center;
  gap: 0.4rem;
}

/* ── Results ── */
.sc-results {
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
  min-height: 0;
}

/* Legend */
.sc-legend {
  padding: 0;
}
.sc-legend-header {
  font-size: var(--text-xs-size);
  font-weight: 700;
  color: var(--text-secondary);
  margin-bottom: 0.4rem;
  display: flex;
  align-items: center;
  gap: 0.35rem;
}
.sc-legend-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 1.15rem;
  height: 1.15rem;
  padding: 0 0.3rem;
  border-radius: 999px;
  background: var(--accent-text);
  color: var(--text-inverse);
  font-size: var(--text-2xs-size);
  font-weight: 700;
}
.sc-legend-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(230px, 1fr));
  gap: 0.4rem;
}
.sc-legend-hint {
  margin-top: 0.4rem;
  font-size: var(--text-xs-size);
  color: var(--text-muted);
  display: flex;
  align-items: center;
  gap: 0.35rem;
}
.sc-legend-clear {
  margin-left: auto;
  border: none;
  background: transparent;
  color: var(--accent-text);
  cursor: pointer;
  font-size: var(--text-xs-size);
  text-decoration: underline;
  font-family: inherit;
}
.sc-segment-count {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
}

/* Charts */
.sc-charts {
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
}

/* Mini map */
.sc-minimap-wrap {
  height: 260px;
  border: 1px solid var(--border-subtle);
  border-radius: 10px;
  overflow: hidden;
  background: var(--surface-glass);
}
.sc-minimap {
  width: 100%;
  height: 100%;
}
</style>
