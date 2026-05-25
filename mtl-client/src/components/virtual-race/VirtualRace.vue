<template>
  <div class="virtual-race-container">
    <!-- Controls -->
    <div class="vr-controls">
      <div class="vr-segment-row">
        <label class="vr-label">Segment</label>
        <div class="vr-chip-scroll">
          <button
            v-for="seg in availableSegments"
            :key="segmentChipKey(seg)"
            class="vr-chip"
            :class="{ 'vr-chip--active': selectedSegmentKey === segmentChipKey(seg) }"
            @click="selectedSegment = seg.code"
          >
            {{ seg.name }}
          </button>
        </div>
      </div>
      <div class="vr-control-row">
        <span class="vr-label">Speed</span>
        <Slider v-model="playbackSpeedSelector" :min="0" :max="100" class="vr-speed-slider"></Slider>
        <span class="vr-speed-info">{{ speedInfoDisplay }}</span>
      </div>
    </div>

    <!-- Map -->
    <div v-show="showMinimap" class="vr-map-wrapper">
      <MiniMap
        ref="minimapRef"
        :tracks-geo-json="raceGeoJson"
        :map-bounds="mapBounds"
        :highlighted-track-index="hoveredRacerIndex"
        class="vr-minimap"
        @hover-racer="hoveredRacerIndex = $event"
        @leave-racer="hoveredRacerIndex = null"
      ></MiniMap>
      <!-- Racer count pill — top right -->
      <span v-if="selectedSegment && selectedSegmentCount != null" class="vr-map-racer-pill">
        <i class="bi bi-people-fill"></i> {{ selectedSegmentCount }} racers
      </span>
      <!-- Play / Reset — bottom center -->
      <div class="vr-map-playback">
        <Button class="vr-start-btn" :disabled="!selectedSegment || isPreviewLoading" @click="onPlayPause">
          <i :class="isRunning ? 'bi bi-pause-fill' : 'bi bi-play-fill'"></i>
        </Button>
        <Button class="vr-reset-btn" :disabled="!hasStarted" severity="secondary" @click="onReset">
          <i class="bi bi-arrow-counterclockwise"></i>
        </Button>
      </div>
    </div>

    <!-- Racers legend -->
    <div v-if="matchingCrossings != null && matchingCrossings.length > 0" class="vr-legend">
      <div class="vr-legend-grid">
        <RacerCard
          v-for="(entry, rank) in sortedRacers"
          :key="entry.originalIndex"
          :color="simulationColors[entry.originalIndex]"
          :name="entry.crossing.gpsTrack.indexedFile.name"
          :date-str="formatTrackDate(entry.crossing.gpsTrack.startDate)"
          :track-id="entry.crossing.gpsTrack.id"
          :activity-type="entry.crossing.gpsTrack.activityType || null"
          :rank="rank + 1"
          :highlighted="hoveredRacerIndex === entry.originalIndex"
          :stats="racerStats(entry)"
          @mouseenter="hoveredRacerIndex = entry.originalIndex"
          @mouseleave="hoveredRacerIndex = null"
          @open-details="openTrackDetails"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue';
import type { CrossingPointsResponse, GpsTrackDataPoint } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import { fetchTrackSubTrackDetails } from '@/utils/ServiceHelper';
import MiniMap from '@/components/map/MiniMap.vue';
import RacerCard from '@/components/ui/RacerCard.vue';
import { generateColors, formatDateAndTime, formatDuration, formatNumber, formatDistance } from '@/utils/Utils';

defineOptions({ name: 'VirtualRace' });

type SegmentCode = { point1?: string; point2?: string; consolidated?: boolean; p1Visit?: number; p2Visit?: number };
type TrackPoint = Omit<GpsTrackDataPoint, 'pointLongLat'> & { pointLongLat?: { coordinates?: number[] } };
type SegmentOption = { name?: string; count?: number; code: SegmentCode };
type RaceTriggerPoint = { coordinate: { x: number; y: number }; name: string };
type RaceCrossing = {
  gpsTrackDataPoint?: TrackPoint & { id?: number };
  timeInSecSinceLastTriggerPoint?: number;
  distanceInMeterSinceLastTriggerPoint?: number;
  avgSpeedSinceLastTriggerPoint?: number;
  triggerPoint: RaceTriggerPoint;
};
type RaceGpsTrack = {
  activityType?: string | null;
  id: number;
  indexedFile: { name: string };
  startDate?: Date | string;
};
type RaceCrossingsPerTrack = {
  crossings: RaceCrossing[];
  gpsTrack: RaceGpsTrack;
};
type MatchingCrossing = { crossings: [RaceCrossing, RaceCrossing]; gpsTrack: RaceGpsTrack };
type RaceGeoJson = GeoJSON.FeatureCollection<GeoJSON.Geometry, Record<string, unknown>>;
type MapBounds = [[number, number], [number, number]];

const props = withDefaults(
  defineProps<{
    measureServiceResult?: CrossingPointsResponse | null;
    consolidateVisits?: boolean;
    initialSegment?: SegmentCode | null;
    selectedTrackIds?: Set<number>;
  }>(),
  {
    measureServiceResult: null,
    consolidateVisits: true,
    initialSegment: null,
    selectedTrackIds: () => new Set<number>(),
  }
);

const emit = defineEmits<{
  'show-track-details': [trackId: number | string];
}>();

const minimapRef = ref<{ invalidateMapSize?: () => void } | null>(null);

const selectedSegment = ref<SegmentCode | null>(null);
const matchingCrossings = ref<MatchingCrossing[] | null>(null);
const playbackSpeedSelector = ref(49);
const avgSegmentDurationSec = ref(0);
const showMinimap = ref(false);
const trackDetailDataResults = ref<TrackPoint[][] | null>(null);
const simulationStartRealtime = ref(-1);
const simulationColors = ref<string[]>([]);
const raceGeoJson = ref<RaceGeoJson | null>(null);
const racerTrails = ref<Array<Array<[number, number]>>>([]);
const animationTimerId = shallowRef<ReturnType<typeof setInterval> | null>(null);
const isPaused = ref(false);
const pausedElapsedRealMs = ref(0);
const isPreviewLoading = ref(false);
const prepareToken = ref(0);
const triggerPointsInvolved = ref(new Map<string, RaceTriggerPoint>());
const mapBounds = ref<MapBounds>([
  [8.505778, 47.5605],
  [8.525778, 47.5705],
]);
const hoveredRacerIndex = ref<number | null>(null);

// Derives speed multiplier from desired animation duration.
// Slider pos 0 = 60s (slowest), pos 100 = 1s (fastest).
function playbackSpeed() {
  if (!avgSegmentDurationSec.value || avgSegmentDurationSec.value <= 0) return 1;
  const animDurationSec = Math.exp((1 - playbackSpeedSelector.value / 100) * Math.log(60));
  return Math.max(1, avgSegmentDurationSec.value / animDurationSec);
}

const availableSegments = computed<SegmentOption[]>(() => {
  if (!props.measureServiceResult) return [];

  if (props.consolidateVisits !== false) {
    return (props.measureServiceResult.segmentsStats || []).map((segment) => ({
      name: segment.label,
      count: selectedSegmentCounts.value.get(segment.point1 + '||' + segment.point2) ?? 0,
      code: { point1: segment.point1, point2: segment.point2, consolidated: true },
    }));
  }

  // Unconsolidated: discover all numbered visit pairs from raw crossing data (filtered by selection)
  const segmentMap = new Map<string, SegmentOption>();
  for (const [trackId, trackCrossingsRaw] of Object.entries(props.measureServiceResult.crossings || {})) {
    const trackCrossings = asRaceCrossingsPerTrack(trackCrossingsRaw);
    const tid = Number(trackId);
    if (!props.selectedTrackIds.has(tid)) continue;
    const countPerTP = new Map<string, number>();
    let lastCrossing: RaceCrossing | null = null;
    for (const crossing of trackCrossings.crossings) {
      const name = crossing.triggerPoint.name;
      countPerTP.set(name, (countPerTP.get(name) || 0) + 1);
      if (lastCrossing != null) {
        const p1 = lastCrossing.triggerPoint.name;
        const p1v = countPerTP.get(p1);
        const p2 = name;
        const p2v = countPerTP.get(name);
        const key = p1 + p1v + '-' + p2 + p2v;
        if (!segmentMap.has(key)) {
          segmentMap.set(key, {
            name: p1 + p1v + ' - ' + p2 + p2v,
            count: 0,
            code: { point1: p1, p1Visit: p1v, point2: p2, p2Visit: p2v, consolidated: false },
          });
        }
        const segment = segmentMap.get(key)!;
        segment.count = (segment.count || 0) + 1;
      }
      lastCrossing = crossing;
    }
  }
  return Array.from(segmentMap.values());
});

// Selected-track-filtered count per consolidated segment key (point1||point2).
const selectedSegmentCounts = computed(() => {
  if (!props.measureServiceResult) return new Map<string, number>();
  const counts = new Map<string, number>();
  for (const [trackId, crossingsRaw] of Object.entries(props.measureServiceResult.crossings || {})) {
    const crossings = asRaceCrossingsPerTrack(crossingsRaw);
    const tid = Number(trackId);
    if (!props.selectedTrackIds.has(tid)) continue;
    let lastCrossing: RaceCrossing | null = null;
    for (const crossing of crossings.crossings) {
      if (lastCrossing != null) {
        const key = lastCrossing.triggerPoint.name + '||' + crossing.triggerPoint.name;
        counts.set(key, (counts.get(key) || 0) + 1);
      }
      lastCrossing = crossing;
    }
  }
  return counts;
});

const selectedSegmentKey = computed(() => {
  if (!selectedSegment.value) return null;
  const code = selectedSegment.value;
  if (code.consolidated === false) {
    return String(code.point1) + String(code.p1Visit) + '-' + String(code.point2) + String(code.p2Visit);
  }
  return String(code.point1) + '||' + String(code.point2);
});

const selectedSegmentCount = computed(() => {
  if (!selectedSegment.value) return null;
  const key = selectedSegmentKey.value;
  const seg = availableSegments.value.find((s) => segmentChipKey(s) === key);
  return seg?.count ?? null;
});

// Right-side slider label: always in seconds, with derived multiplier when segment data loaded.
const speedInfoDisplay = computed(() => {
  const secs = Math.max(1, Math.round(Math.exp((1 - playbackSpeedSelector.value / 100) * Math.log(60))));
  if (!avgSegmentDurationSec.value || avgSegmentDurationSec.value <= 0) {
    return secs + 's';
  }
  const speed = playbackSpeed();
  if (speed >= 1.5) {
    return Math.round(speed) + 'x · ' + secs + 's';
  }
  return secs + 's';
});

/**
 * Legend display order: fastest arrival first. We keep the original index
 * (needed because simulationColors / raceGeoJson features are keyed by
 * insertion order) and sort only the presentation.
 */
const isRunning = computed(() => animationTimerId.value != null);
const hasStarted = computed(() => trackDetailDataResults.value != null);
const sortedRacers = computed(() => {
  if (!matchingCrossings.value) return [];
  const entries = matchingCrossings.value.map((crossing, originalIndex) => ({
    crossing,
    originalIndex,
    durationSec: crossing.crossings?.[1]?.timeInSecSinceLastTriggerPoint ?? Number.POSITIVE_INFINITY,
  }));
  entries.sort((a, b) => a.durationSec - b.durationSec);
  return entries;
});

function segmentChipKey(seg: SegmentOption) {
  const code = seg.code;
  if (!code) return '';
  if (code.consolidated === false) {
    return String(code.point1) + String(code.p1Visit) + '-' + String(code.point2) + String(code.p2Visit);
  }
  return String(code.point1) + '||' + String(code.point2);
}

function asRaceCrossingsPerTrack(value: unknown): RaceCrossingsPerTrack {
  return value as RaceCrossingsPerTrack;
}

function matchesSelectedSegment(lastCrossing: RaceCrossing, crossing: RaceCrossing, countPerTP: Map<string, number>) {
  const seg = selectedSegment.value;
  if (!seg) return false;
  if (seg.point1 !== lastCrossing.triggerPoint?.name) return false;
  if (seg.point2 !== crossing.triggerPoint?.name) return false;
  if (seg.consolidated === false) {
    if (seg.p1Visit !== countPerTP.get(lastCrossing.triggerPoint.name)) return false;
    if (seg.p2Visit !== countPerTP.get(crossing.triggerPoint.name)) return false;
  }
  return true;
}

function autoSelectSpeed() {
  const TARGET_SECONDS = 12;
  if (!selectedSegment.value || !props.measureServiceResult) {
    avgSegmentDurationSec.value = 0;
    return;
  }
  let totalDuration = 0;
  let count = 0;
  for (const [trackId, crossingsRaw] of Object.entries(props.measureServiceResult.crossings || {})) {
    const crossings = asRaceCrossingsPerTrack(crossingsRaw);
    const tid = Number(trackId);
    if (!props.selectedTrackIds.has(tid)) continue;
    const countPerTP = new Map<string, number>();
    let lastCrossing: RaceCrossing | null = null;
    for (const crossing of crossings.crossings) {
      const name = crossing.triggerPoint.name;
      countPerTP.set(name, (countPerTP.get(name) || 0) + 1);
      if (
        lastCrossing != null &&
        matchesSelectedSegment(lastCrossing, crossing, countPerTP) &&
        crossing.timeInSecSinceLastTriggerPoint
      ) {
        totalDuration += crossing.timeInSecSinceLastTriggerPoint;
        count++;
      }
      lastCrossing = crossing;
    }
  }
  if (count > 0) {
    avgSegmentDurationSec.value = totalDuration / count;
    // Set slider so animation runs for TARGET_SECONDS.
    // Inverse of: secs = exp((1 - pos/100) * log(60))
    //   → pos = 100 * (1 - log(secs) / log(60))
    const pos = 100 * (1 - Math.log(TARGET_SECONDS) / Math.log(60));
    playbackSpeedSelector.value = Math.round(Math.max(0, Math.min(100, pos)));
  } else {
    avgSegmentDurationSec.value = 0;
  }
}

async function preparePreview() {
  if (!selectedSegment.value || !props.measureServiceResult) {
    stopAnimation();
    matchingCrossings.value = null;
    trackDetailDataResults.value = null;
    raceGeoJson.value = null;
    showMinimap.value = false;
    return;
  }

  stopAnimation();
  isPaused.value = false;
  pausedElapsedRealMs.value = 0;
  simulationStartRealtime.value = -1;
  isPreviewLoading.value = true;

  const token = ++prepareToken.value;
  const activeTrackIds = new Set(props.selectedTrackIds);

  const nextMatchingCrossings: MatchingCrossing[] = [];
  const fetchPromises: Array<Promise<GpsTrackDataPoint[]>> = [];
  const nextTriggerPointsInvolved = new Map<string, RaceTriggerPoint>();

  for (const [trackId, crossingsRaw] of Object.entries(props.measureServiceResult.crossings || {})) {
    const crossings = asRaceCrossingsPerTrack(crossingsRaw);
    const tid = Number(trackId);
    if (!activeTrackIds.has(tid)) continue;
    const countPerTP = new Map<string, number>();
    let lastCrossing: RaceCrossing | null = null;
    for (const crossing of crossings.crossings) {
      const name = crossing.triggerPoint.name;
      countPerTP.set(name, (countPerTP.get(name) || 0) + 1);
      if (
        lastCrossing != null &&
        matchesSelectedSegment(lastCrossing, crossing, countPerTP) &&
        lastCrossing.gpsTrackDataPoint?.id != null &&
        crossing.gpsTrackDataPoint?.id != null
      ) {
        nextMatchingCrossings.push({ crossings: [lastCrossing, crossing], gpsTrack: crossings.gpsTrack });
        nextTriggerPointsInvolved.set(crossing.triggerPoint.name, crossing.triggerPoint);
        nextTriggerPointsInvolved.set(lastCrossing.triggerPoint.name, lastCrossing.triggerPoint);
        fetchPromises.push(fetchTrackSubTrackDetails(lastCrossing.gpsTrackDataPoint.id, crossing.gpsTrackDataPoint.id));
      }
      lastCrossing = crossing;
    }
  }

  let adjustedResults: TrackPoint[][];
  try {
    const results = await Promise.all(fetchPromises);
    adjustedResults = results.map((track, i) =>
      withVirtualEndpoints(track as TrackPoint[], nextMatchingCrossings[i]?.crossings)
    );
  } catch (error) {
    if (token !== prepareToken.value) return;
    console.error('Virtual race preview failed:', error);
    matchingCrossings.value = null;
    trackDetailDataResults.value = null;
    raceGeoJson.value = null;
    showMinimap.value = false;
    return;
  } finally {
    if (token === prepareToken.value) {
      isPreviewLoading.value = false;
    }
  }

  if (token !== prepareToken.value) return; // stale — newer prepare in flight

  if (!adjustedResults || adjustedResults.length === 0) {
    matchingCrossings.value = null;
    trackDetailDataResults.value = null;
    raceGeoJson.value = null;
    return;
  }

  matchingCrossings.value = nextMatchingCrossings;
  trackDetailDataResults.value = adjustedResults;
  triggerPointsInvolved.value = nextTriggerPointsInvolved;

  // Assign one color per unique track
  const uniqueTrackIds = [...new Set(nextMatchingCrossings.map((mc) => mc.gpsTrack.id))];
  const trackColorPalette = generateColors(uniqueTrackIds.length);
  const trackIdToColor = new Map(uniqueTrackIds.map((id, i) => [id, trackColorPalette[i]]));
  simulationColors.value = nextMatchingCrossings.map((mc) => trackIdToColor.get(mc.gpsTrack.id) || '#2563eb');

  racerTrails.value = adjustedResults.map(() => []);

  // Compute map bounds
  let cLatMin = Number.MAX_VALUE,
    cLatMax = -Number.MAX_VALUE;
  let cLongMin = Number.MAX_VALUE,
    cLongMax = -Number.MAX_VALUE;
  for (const track of adjustedResults) {
    for (const point of track) {
      const coords = point.pointLongLat?.coordinates;
      if (!coords) continue;
      const lng = coords[0];
      const lat = coords[1];
      if (lat < cLatMin) cLatMin = lat;
      if (lat > cLatMax) cLatMax = lat;
      if (lng > cLongMax) cLongMax = lng;
      if (lng < cLongMin) cLongMin = lng;
    }
  }
  const dLat = (cLatMax - cLatMin) * 0.3;
  const dLong = (cLongMax - cLongMin) * 0.3;
  mapBounds.value = [
    [cLongMin - dLong, cLatMin - dLat],
    [cLongMax + dLong, cLatMax + dLat],
  ];

  // Show racers at their start positions
  buildRaceGeoJson(nextTriggerPointsInvolved);

  showMinimap.value = true;
  nextTick(() => {
    if (minimapRef.value?.invalidateMapSize) {
      minimapRef.value.invalidateMapSize();
    }
  });
}

function withVirtualEndpoints(track: TrackPoint[], crossingPair: [RaceCrossing, RaceCrossing] | undefined) {
  if (!Array.isArray(track) || track.length === 0 || !Array.isArray(crossingPair) || crossingPair.length < 2) {
    return track;
  }

  const start = crossingPair[0]?.gpsTrackDataPoint;
  const end = crossingPair[1]?.gpsTrackDataPoint;
  if (!start || !end) return track;

  const startDuration = start?.durationSinceStart;
  const endDuration = end?.durationSinceStart;
  if (typeof startDuration !== 'number' || typeof endDuration !== 'number') {
    return track;
  }

  const inner = track.filter((point) => {
    const pointDuration = point?.durationSinceStart;
    if (typeof pointDuration !== 'number') {
      return true;
    }
    return pointDuration > startDuration && pointDuration < endDuration;
  });

  return [start as TrackPoint, ...inner, end as TrackPoint];
}

function buildRaceGeoJson(activeTriggerPointsInvolved: Map<string, RaceTriggerPoint>) {
  const features: RaceGeoJson['features'] = [];

  // Trigger point circles with labels
  activeTriggerPointsInvolved.forEach((triggerPoint) => {
    features.push({
      type: 'Feature',
      geometry: {
        type: 'Point',
        coordinates: [triggerPoint.coordinate.x, triggerPoint.coordinate.y],
      },
      properties: { type: 'trigger', radius: 30, color: 'grey', label: triggerPoint.name, name: triggerPoint.name },
    });
  });

  // Race markers (initial positions) with track names
  let trackIndex = -1;
  for (const track of trackDetailDataResults.value || []) {
    trackIndex++;
    const simulationPoint = track[0];
    const color = simulationColors.value[trackIndex];
    const trackName = matchingCrossings.value?.[trackIndex]?.gpsTrack?.indexedFile?.name || 'Track ' + trackIndex;
    features.push({
      type: 'Feature',
      geometry: {
        type: 'Point',
        coordinates: [
          simulationPoint.pointLongLat?.coordinates?.[0] ?? 0,
          simulationPoint.pointLongLat?.coordinates?.[1] ?? 0,
        ],
      },
      properties: { type: 'racer', trackIndex, color, trackName },
    });
  }

  raceGeoJson.value = { type: 'FeatureCollection', features };
}

function animateRace() {
  if (!trackDetailDataResults.value) return;
  const timeSinceStartInSeconds = (new Date().getTime() - simulationStartRealtime.value) / 1000;
  const timeSinceStartSimulationInSeconds = timeSinceStartInSeconds * playbackSpeed();

  let foundAtLeastOne = false;
  const features: RaceGeoJson['features'] = [];

  // Keep trigger points (they don't move)
  if (raceGeoJson.value) {
    for (const f of raceGeoJson.value.features) {
      if (f.properties.type === 'trigger') features.push(f);
    }
  }

  let trackIndex = -1;
  for (const track of trackDetailDataResults.value) {
    trackIndex++;
    const trackStartTime = track[0].durationSinceStart || 0;
    const color = simulationColors.value[trackIndex];

    let simulationPoint: TrackPoint | null = null;
    for (const point of track) {
      const trackTimeSinceStartInSeconds = (point.durationSinceStart || 0) - trackStartTime;
      if (trackTimeSinceStartInSeconds > timeSinceStartSimulationInSeconds) {
        foundAtLeastOne = true;
        simulationPoint = point;
        break;
      }
    }

    if (simulationPoint == null) {
      simulationPoint = track[track.length - 1];
    }

    const coord: [number, number] = [
      simulationPoint.pointLongLat?.coordinates?.[0] || 0,
      simulationPoint.pointLongLat?.coordinates?.[1] || 0,
    ];

    // Append to trail (avoid duplicates)
    const trail = racerTrails.value[trackIndex];
    const last = trail.length ? trail[trail.length - 1] : null;
    if (!last || last[0] !== coord[0] || last[1] !== coord[1]) {
      trail.push(coord);
    }

    // Trail line
    if (trail.length >= 2) {
      features.push({
        type: 'Feature',
        geometry: { type: 'LineString', coordinates: trail },
        properties: { type: 'trail', trackIndex, color },
      });
    }

    // Racer dot
    features.push({
      type: 'Feature',
      geometry: { type: 'Point', coordinates: coord },
      properties: {
        type: 'racer',
        trackIndex,
        color,
        trackName: matchingCrossings.value?.[trackIndex]?.gpsTrack?.indexedFile?.name || 'Track ' + trackIndex,
      },
    });
  }

  raceGeoJson.value = { type: 'FeatureCollection', features };

  if (!foundAtLeastOne) {
    stopAnimation();
  }
}

function onPlayPause() {
  if (isRunning.value) {
    // Pause: record how much real time has elapsed so resume can offset correctly.
    pausedElapsedRealMs.value = new Date().getTime() - simulationStartRealtime.value;
    stopAnimation();
    isPaused.value = true;
  } else if (isPaused.value) {
    // Resume: shift simulationStartRealtime forward by the pause duration.
    simulationStartRealtime.value = new Date().getTime() - pausedElapsedRealMs.value;
    isPaused.value = false;
    animationTimerId.value = setInterval(animateRace, 33);
  } else {
    // Fresh start — data already preloaded by preparePreview().
    if (!trackDetailDataResults.value || trackDetailDataResults.value.length === 0) return;
    racerTrails.value = trackDetailDataResults.value.map(() => []);
    buildRaceGeoJson(triggerPointsInvolved.value);
    simulationStartRealtime.value = new Date().getTime();
    animationTimerId.value = setInterval(animateRace, 33);
  }
}

function onReset() {
  stopAnimation();
  isPaused.value = false;
  pausedElapsedRealMs.value = 0;
  simulationStartRealtime.value = -1;
  // Restore racers to their start positions without re-fetching.
  if (trackDetailDataResults.value && trackDetailDataResults.value.length > 0) {
    racerTrails.value = trackDetailDataResults.value.map(() => []);
    buildRaceGeoJson(triggerPointsInvolved.value);
  }
}

function stopAnimation() {
  if (animationTimerId.value) {
    clearInterval(animationTimerId.value);
    animationTimerId.value = null;
  }
}

function openTrackDetails(id: number | string) {
  emit('show-track-details', id);
}

function formatTrackDate(date: Date | string | number | null | undefined) {
  return formatDateAndTime(date);
}

function racerStats(entry: { crossing: MatchingCrossing }): Array<{ icon: string; text: string }> {
  const crossing = entry.crossing.crossings[1];
  const stats = [];
  if (crossing?.timeInSecSinceLastTriggerPoint) {
    stats.push({ icon: 'bi-stopwatch', text: formatDuration(crossing.timeInSecSinceLastTriggerPoint * 1000) });
  }
  if (crossing?.avgSpeedSinceLastTriggerPoint) {
    stats.push({ icon: 'bi-speedometer', text: formatNumber(crossing.avgSpeedSinceLastTriggerPoint, 1) + ' km/h' });
  }
  if (crossing?.distanceInMeterSinceLastTriggerPoint) {
    stats.push({
      icon: 'bi-signpost-split',
      text: formatDistance(crossing.distanceInMeterSinceLastTriggerPoint) ?? '',
    });
  }
  return stats;
}

watch(selectedSegment, () => {
  autoSelectSpeed();
  preparePreview();
});

watch(
  () => props.selectedTrackIds,
  () => {
    // Selection changed outside — refresh preview for new subset.
    autoSelectSpeed();
    preparePreview();
  }
);

watch(
  () => props.consolidateVisits,
  () => {
    selectedSegment.value = null;
    nextTick(() => {
      if (availableSegments.value && availableSegments.value.length > 0) {
        selectedSegment.value = availableSegments.value[0].code;
      }
    });
  }
);

onMounted(() => {
  if (props.initialSegment) {
    selectedSegment.value = props.initialSegment;
  } else if (availableSegments.value && availableSegments.value.length > 0) {
    selectedSegment.value = availableSegments.value[0].code;
  }
  // preparePreview() will be triggered by the selectedSegment watcher above.
});

onBeforeUnmount(() => {
  stopAnimation();
});
</script>

<style scoped>
.virtual-race-container {
  display: flex;
  flex-direction: column;
  gap: 10px;
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  -webkit-overflow-scrolling: touch;
  overscroll-behavior-y: contain;
  padding: 0.35rem 0.75rem calc(0.75rem + var(--safe-bottom, 0px));
}

.p-dialog-maximized .virtual-race-container {
  height: initial;
}

/* ── Controls ── */
.vr-controls {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.vr-control-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: nowrap;
}

.vr-label {
  font-size: var(--text-sm-size);
  color: var(--text-secondary);
  white-space: nowrap;
  min-width: 3.5rem;
}

.vr-segment-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.vr-chip-scroll {
  display: flex;
  gap: 0.3rem;
  overflow-x: auto;
  overflow-y: visible;
  flex: 1 1 0;
  min-width: 0;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
  padding-bottom: 2px; /* prevent clipping of chip border */
}

.vr-chip-scroll::-webkit-scrollbar {
  display: none;
}

.vr-chip {
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
  transition:
    background 0.15s,
    color 0.15s,
    border-color 0.15s;
}

.vr-chip:hover {
  color: var(--text-secondary);
  background: var(--surface-hover);
}

.vr-chip--active {
  background: var(--accent-text);
  color: var(--text-inverse);
  border-color: var(--accent-text);
}

.vr-racer-pill {
  font-size: var(--text-xs-size);
  font-weight: 600;
  color: var(--accent-text);
  background: color-mix(in srgb, var(--accent-text) 12%, transparent);
  border: 1px solid color-mix(in srgb, var(--accent-text) 30%, transparent);
  border-radius: 999px;
  padding: 0.2rem 0.55rem;
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
}

.vr-speed-slider {
  flex: 1 1 0;
  min-width: 4rem;
  max-width: 14rem;
}

.vr-start-btn,
.vr-reset-btn {
  white-space: nowrap;
  flex-shrink: 0;
  width: 2.25rem !important;
  height: 2.25rem !important;
  padding: 0 !important;
  min-width: unset !important;
}

:deep(.vr-start-btn),
:deep(.vr-reset-btn) {
  width: 2.25rem;
  height: 2.25rem;
  padding: 0;
  min-width: unset;
}

.vr-speed-info {
  font-size: var(--text-sm-size);
  font-weight: 500;
  color: var(--text-secondary);
  white-space: nowrap;
  min-width: 5rem;
  text-align: right;
  font-variant-numeric: tabular-nums;
  cursor: default;
  letter-spacing: 0.01em;
}

/* ── Map wrapper + overlays ── */
.vr-map-wrapper {
  position: relative;
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: min(260px, 38svh);
}

.vr-map-racer-pill {
  position: absolute;
  top: 0.6rem;
  right: 0.6rem;
  z-index: 10;
  font-size: var(--text-xs-size);
  font-weight: 600;
  color: var(--accent-text);
  background: var(--surface-glass-heavy);
  border: 1px solid color-mix(in srgb, var(--accent-text) 30%, transparent);
  border-radius: 999px;
  padding: 0.2rem 0.6rem;
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  backdrop-filter: blur(4px);
  pointer-events: none;
}

.vr-map-playback {
  position: absolute;
  bottom: 0.75rem;
  left: 50%;
  transform: translateX(-50%);
  z-index: 10;
  display: flex;
  gap: 8px;
}

/* ── Mini map ── */
.vr-minimap {
  flex: 1 1 auto;
  min-height: 0;
  width: 100%;
  border-radius: 8px;
  overflow: hidden;
}

/* ── Legend ── */
.vr-legend {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.vr-legend-header {
  font-size: var(--text-sm-size);
  font-weight: 600;
  color: var(--text-secondary);
  display: flex;
  align-items: center;
  gap: 6px;
}

.vr-legend-count {
  background: var(--surface-glass);
  border-radius: 10px;
  padding: 1px 8px;
  font-size: var(--text-xs-size);
  font-weight: 500;
}

.vr-legend-hint {
  margin-left: auto;
  font-size: var(--text-xs-size);
  font-weight: 500;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.vr-legend-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 8px;
}

@media (pointer: coarse) {
  .vr-speed-slider {
    min-width: 0;
    max-width: none;
  }
}
</style>
