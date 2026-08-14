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
            class="vr-chip selection-chip"
            :class="{ 'selection-chip--active': selectedSegmentKey === segmentChipKey(seg) }"
            @click="selectedSegment = seg.code"
          >
            {{ seg.name }}
          </button>
        </div>
      </div>
      <div class="vr-control-row">
        <span class="vr-label">Speed</span>
        <MtlSlider
          v-model="playbackSpeedSelector"
          :min="0"
          :max="100"
          class="vr-speed-slider"
          aria-label="Adjust race playback speed"
        />
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
      <span v-if="selectedSegment && raceRacerCount != null" class="vr-map-racer-pill">
        <i class="bi bi-people-fill"></i> {{ raceRacerCount }} racer{{ raceRacerCount === 1 ? '' : 's' }}
      </span>
      <!-- Play / Reset — bottom center -->
      <div class="vr-map-playback">
        <Button
          class="vr-start-btn"
          :disabled="!selectedSegment || isPreviewLoading || !hasStarted"
          @click="onPlayPause"
        >
          <i :class="isRunning ? 'bi bi-pause-fill' : 'bi bi-play-fill'"></i>
        </Button>
        <Button class="vr-reset-btn" :disabled="!hasStarted" severity="secondary" @click="onReset">
          <i class="bi bi-arrow-counterclockwise"></i>
        </Button>
      </div>
    </div>

    <div v-if="isPreviewLoading" class="vr-placeholder empty-placeholder">
      <i class="bi bi-hourglass-split"></i>
      <p>Loading race segment…</p>
    </div>

    <div v-else-if="previewMessage" class="vr-placeholder empty-placeholder">
      <i class="bi bi-signpost-split"></i>
      <p>{{ previewMessage }}</p>
    </div>

    <div v-if="showMinimap && skippedRacerCount > 0" class="vr-warning">
      <i class="bi bi-info-circle"></i>
      {{ skippedRacerCount }} selected racer{{ skippedRacerCount === 1 ? '' : 's' }} lacked enough segment data and
      {{ skippedRacerCount === 1 ? 'was' : 'were' }} skipped.
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
import type {
  CrossingPointsResponseDto,
  GpsTrackDataPointDto,
} from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import { fetchTrackSubTrackDetails } from '@/utils/ServiceHelper';
import MiniMap from '@/components/map/MiniMap.vue';
import RacerCard from '@/components/ui/RacerCard.vue';
import MtlSlider from '@/components/ui/MtlSlider.vue';
import {
  generateColors,
  formatDateAndTime,
  formatDuration,
  formatNumber,
  formatDistance,
  formatSpeed,
} from '@/utils/Utils';
import {
  buildVisitSegmentOptions,
  normalizeSegmentSlice,
  segmentSelectionKey,
  validTrackPointCoordinates,
  type SegmentSelectionCode,
  type SegmentSliceResult,
} from '@/components/measure/segmentSlice';
import { VIZ_BLUE_COLOR } from '@/utils/visualizationColors';

defineOptions({ name: 'VirtualRace' });

type SegmentCode = SegmentSelectionCode;
type TrackPoint = Omit<GpsTrackDataPointDto, 'pointLongLat'> & { pointLongLat?: { coordinates?: number[] } };
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
type LngLat = [number, number];
type RacerProgress = {
  elapsedSec: number;
  progressRatio: number;
  distanceM: number;
  finished: boolean;
};

const props = withDefaults(
  defineProps<{
    measureServiceResult?: CrossingPointsResponseDto | null;
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
const raceDurationsSec = ref<number[]>([]);
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
const racerProgress = ref<RacerProgress[]>([]);
const previewMessage = ref('');
const skippedRacerCount = ref(0);

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
  return buildVisitSegmentOptions(
    Object.entries(props.measureServiceResult.crossings || {})
      .filter(([trackId]) => props.selectedTrackIds.has(Number(trackId)))
      .map(([, trackCrossingsRaw]) => asRaceCrossingsPerTrack(trackCrossingsRaw).crossings)
  );
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

const raceRacerCount = computed(() => matchingCrossings.value?.length ?? selectedSegmentCount.value);

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
  if (hasStarted.value && racerProgress.value.length > 0) {
    entries.sort((a, b) => {
      const pa = racerProgress.value[a.originalIndex];
      const pb = racerProgress.value[b.originalIndex];
      if (pa?.finished && pb?.finished) return a.durationSec - b.durationSec;
      if (pa?.finished) return -1;
      if (pb?.finished) return 1;
      const progressDiff = (pb?.progressRatio ?? 0) - (pa?.progressRatio ?? 0);
      if (Math.abs(progressDiff) > 0.0001) return progressDiff;
      return a.durationSec - b.durationSec;
    });
  } else {
    entries.sort((a, b) => a.durationSec - b.durationSec);
  }
  return entries;
});

const segmentChipKey = segmentSelectionKey;

function asRaceCrossingsPerTrack(value: unknown): RaceCrossingsPerTrack {
  return value as RaceCrossingsPerTrack;
}

function matchesSelectedSegment(
  lastCrossing: RaceCrossing,
  crossing: RaceCrossing,
  lastVisit: number | null,
  currentVisit: number
) {
  const seg = selectedSegment.value;
  if (!seg) return false;
  if (seg.point1 !== lastCrossing.triggerPoint?.name) return false;
  if (seg.point2 !== crossing.triggerPoint?.name) return false;
  if (seg.consolidated === false) {
    if (seg.p1Visit !== lastVisit) return false;
    if (seg.p2Visit !== currentVisit) return false;
  }
  return true;
}

function selectedRaceSegmentMatches(): MatchingCrossing[] {
  if (!props.measureServiceResult) return [];
  const matches: MatchingCrossing[] = [];
  for (const [trackId, crossingsRaw] of Object.entries(props.measureServiceResult.crossings || {})) {
    const crossings = asRaceCrossingsPerTrack(crossingsRaw);
    if (!props.selectedTrackIds.has(Number(trackId))) continue;
    const visitsByTriggerPoint = new Map<string, number>();
    let previousCrossing: RaceCrossing | null = null;
    let previousVisit: number | null = null;
    for (const crossing of crossings.crossings) {
      const name = crossing.triggerPoint.name;
      const currentVisit = (visitsByTriggerPoint.get(name) || 0) + 1;
      visitsByTriggerPoint.set(name, currentVisit);
      if (previousCrossing && matchesSelectedSegment(previousCrossing, crossing, previousVisit, currentVisit)) {
        matches.push({ crossings: [previousCrossing, crossing], gpsTrack: crossings.gpsTrack });
      }
      previousCrossing = crossing;
      previousVisit = currentVisit;
    }
  }
  return matches;
}

function autoSelectSpeed() {
  const TARGET_SECONDS = 12;
  if (!selectedSegment.value || !props.measureServiceResult) {
    avgSegmentDurationSec.value = 0;
    return;
  }
  const durations = selectedRaceSegmentMatches()
    .map((match) => match.crossings[1].timeInSecSinceLastTriggerPoint)
    .filter((duration): duration is number => Boolean(duration));
  const totalDuration = durations.reduce((sum, duration) => sum + duration, 0);
  const count = durations.length;
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
  const token = ++prepareToken.value;
  if (!selectedSegment.value || !props.measureServiceResult) {
    stopAnimation();
    clearPreparedRace();
    return;
  }

  stopAnimation();
  isPaused.value = false;
  pausedElapsedRealMs.value = 0;
  simulationStartRealtime.value = -1;
  previewMessage.value = '';
  skippedRacerCount.value = 0;
  isPreviewLoading.value = true;

  const nextMatchingCrossings: MatchingCrossing[] = [];
  const fetchPromises: Array<Promise<GpsTrackDataPointDto[]>> = [];
  for (const match of selectedRaceSegmentMatches()) {
    const [start, end] = match.crossings;
    if (start.gpsTrackDataPoint?.id == null || end.gpsTrackDataPoint?.id == null) continue;
    nextMatchingCrossings.push(match);
    fetchPromises.push(fetchTrackSubTrackDetails(start.gpsTrackDataPoint.id, end.gpsTrackDataPoint.id));
  }

  let normalizedResults: SegmentSliceResult<TrackPoint>[];
  try {
    const results = await Promise.all(fetchPromises);
    normalizedResults = results.map((track, i) =>
      normalizeSegmentSlice(track as TrackPoint[], nextMatchingCrossings[i]?.crossings, {
        requirePositiveDistanceOrGeometry: true,
        requirePositiveDuration: true,
      })
    );
  } catch (error) {
    if (token !== prepareToken.value) return;
    console.error('Virtual race preview failed:', error);
    clearPreparedRace('Could not load race segment data.');
    return;
  } finally {
    if (token === prepareToken.value) {
      isPreviewLoading.value = false;
    }
  }

  if (token !== prepareToken.value) return; // stale — newer prepare in flight

  const validMatchingCrossings: MatchingCrossing[] = [];
  const validResults: TrackPoint[][] = [];
  const validDurationsSec: number[] = [];
  const validTriggerPointsInvolved = new Map<string, RaceTriggerPoint>();
  for (let i = 0; i < normalizedResults.length; i++) {
    const crossing = nextMatchingCrossings[i];
    const segment = normalizedResults[i];
    if (!crossing || !segment.valid) continue;
    const points = segment.points;
    validMatchingCrossings.push(crossing);
    validResults.push(points);
    validDurationsSec.push(segment.durationSec || segmentDurationFromPoints(points, crossing));
    validTriggerPointsInvolved.set(crossing.crossings[0].triggerPoint.name, crossing.crossings[0].triggerPoint);
    validTriggerPointsInvolved.set(crossing.crossings[1].triggerPoint.name, crossing.crossings[1].triggerPoint);
  }

  if (validResults.length === 0) {
    clearPreparedRace('Selected tracks do not contain enough segment data to race.', normalizedResults.length);
    return;
  }

  skippedRacerCount.value = normalizedResults.length - validResults.length;
  matchingCrossings.value = validMatchingCrossings;
  trackDetailDataResults.value = validResults;
  raceDurationsSec.value = validDurationsSec;
  triggerPointsInvolved.value = validTriggerPointsInvolved;

  // Assign one color per unique track
  const uniqueTrackIds = [...new Set(validMatchingCrossings.map((mc) => mc.gpsTrack.id))];
  const trackColorPalette = generateColors(uniqueTrackIds.length);
  const trackIdToColor = new Map(uniqueTrackIds.map((id, i) => [id, trackColorPalette[i]]));
  simulationColors.value = validMatchingCrossings.map((mc) => trackIdToColor.get(mc.gpsTrack.id) || VIZ_BLUE_COLOR);

  racerTrails.value = validResults.map(() => []);
  racerProgress.value = initialRacerProgress(validResults.length);

  // Compute map bounds
  let cLatMin = Number.MAX_VALUE,
    cLatMax = -Number.MAX_VALUE;
  let cLongMin = Number.MAX_VALUE,
    cLongMax = -Number.MAX_VALUE;
  let hasBoundsCoordinate = false;
  for (const track of validResults) {
    for (const point of track) {
      const coords = pointCoordinates(point);
      if (!coords) continue;
      hasBoundsCoordinate = true;
      const lng = coords[0];
      const lat = coords[1];
      if (lat < cLatMin) cLatMin = lat;
      if (lat > cLatMax) cLatMax = lat;
      if (lng > cLongMax) cLongMax = lng;
      if (lng < cLongMin) cLongMin = lng;
    }
  }
  if (!hasBoundsCoordinate) {
    clearPreparedRace('Selected tracks do not contain enough segment data to race.', normalizedResults.length);
    return;
  }
  const dLat = (cLatMax - cLatMin) * 0.3;
  const dLong = (cLongMax - cLongMin) * 0.3;
  mapBounds.value = [
    [cLongMin - dLong, cLatMin - dLat],
    [cLongMax + dLong, cLatMax + dLat],
  ];

  // Show racers at their start positions
  buildRaceGeoJson(validTriggerPointsInvolved);

  showMinimap.value = true;
  nextTick(() => {
    if (minimapRef.value?.invalidateMapSize) {
      minimapRef.value.invalidateMapSize();
    }
  });
}

function clearPreparedRace(message = '', skippedCount = 0) {
  matchingCrossings.value = null;
  trackDetailDataResults.value = null;
  raceDurationsSec.value = [];
  raceGeoJson.value = null;
  racerProgress.value = [];
  previewMessage.value = message;
  skippedRacerCount.value = skippedCount;
  showMinimap.value = false;
}

function segmentDurationFromPoints(track: TrackPoint[], crossing: MatchingCrossing) {
  const first = track[0];
  const last = track[track.length - 1];
  const startDuration = Number(first?.durationSinceStart);
  const endDuration = Number(last?.durationSinceStart);
  if (Number.isFinite(startDuration) && Number.isFinite(endDuration) && endDuration > startDuration) {
    return endDuration - startDuration;
  }
  return crossing.crossings?.[1]?.timeInSecSinceLastTriggerPoint ?? 0;
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
    const coords = pointCoordinates(simulationPoint);
    if (!coords) continue;
    const color = simulationColors.value[trackIndex];
    const trackName = matchingCrossings.value?.[trackIndex]?.gpsTrack?.indexedFile?.name || 'Track ' + trackIndex;
    features.push({
      type: 'Feature',
      geometry: {
        type: 'Point',
        coordinates: coords,
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
  const nextProgress: RacerProgress[] = [];

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
    const segmentDurationSec = segmentDurationSeconds(trackIndex);
    const color = simulationColors.value[trackIndex];

    let simulationPoint: TrackPoint | null = track[0];
    let elapsedSec = 0;
    if (timeSinceStartSimulationInSeconds <= 0) {
      foundAtLeastOne = true;
    } else {
      for (let i = 1; i < track.length; i++) {
        const previousPoint = track[i - 1];
        const point = track[i];
        const previousTimeSinceStartInSeconds = (previousPoint.durationSinceStart || 0) - trackStartTime;
        const trackTimeSinceStartInSeconds = (point.durationSinceStart || 0) - trackStartTime;
        if (trackTimeSinceStartInSeconds > timeSinceStartSimulationInSeconds) {
          const spanSec = trackTimeSinceStartInSeconds - previousTimeSinceStartInSeconds;
          const factor =
            spanSec > 0 ? (timeSinceStartSimulationInSeconds - previousTimeSinceStartInSeconds) / spanSec : 1;
          foundAtLeastOne = true;
          simulationPoint = interpolateTrackPoint(previousPoint, point, factor);
          elapsedSec = Math.max(0, timeSinceStartSimulationInSeconds);
          break;
        }
      }
    }

    if (simulationPoint == null || timeSinceStartSimulationInSeconds >= segmentDurationSec) {
      simulationPoint = track[track.length - 1];
      elapsedSec = segmentDurationSec;
    }

    if (timeSinceStartSimulationInSeconds < segmentDurationSec) {
      foundAtLeastOne = true;
    }

    const coord = pointCoordinates(simulationPoint);
    if (!coord) {
      nextProgress[trackIndex] = progressForSimulationPoint(trackIndex, track, simulationPoint, elapsedSec);
      continue;
    }

    // Append to trail (avoid duplicates)
    const trail = racerTrails.value[trackIndex];
    const last = trail.length ? trail[trail.length - 1] : null;
    if (!last || last[0] !== coord[0] || last[1] !== coord[1]) {
      trail.push(coord);
    }

    nextProgress[trackIndex] = progressForSimulationPoint(trackIndex, track, simulationPoint, elapsedSec);

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
  racerProgress.value = nextProgress;

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
    racerProgress.value = initialRacerProgress(trackDetailDataResults.value.length);
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
    racerProgress.value = initialRacerProgress(trackDetailDataResults.value.length);
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

function initialRacerProgress(count: number): RacerProgress[] {
  return Array.from({ length: count }, () => ({ elapsedSec: 0, progressRatio: 0, distanceM: 0, finished: false }));
}

function segmentDurationSeconds(trackIndex: number): number {
  const normalizedDuration = raceDurationsSec.value[trackIndex];
  if (normalizedDuration && normalizedDuration > 0) return normalizedDuration;
  return matchingCrossings.value?.[trackIndex]?.crossings?.[1]?.timeInSecSinceLastTriggerPoint ?? 0;
}

function interpolateTrackPoint(from: TrackPoint, to: TrackPoint, factor: number): TrackPoint {
  const clampedFactor = Math.max(0, Math.min(1, factor));
  const fromCoords = pointCoordinates(from);
  const toCoords = pointCoordinates(to);
  const interpolated = { ...to };

  if (fromCoords && toCoords) {
    interpolated.pointLongLat = {
      ...to.pointLongLat,
      coordinates: [
        fromCoords[0] + (toCoords[0] - fromCoords[0]) * clampedFactor,
        fromCoords[1] + (toCoords[1] - fromCoords[1]) * clampedFactor,
      ],
    };
  }

  interpolated.durationSinceStart = interpolateNumber(from.durationSinceStart, to.durationSinceStart, clampedFactor);
  interpolated.distanceInMeterSinceStart = interpolateNumber(
    from.distanceInMeterSinceStart,
    to.distanceInMeterSinceStart,
    clampedFactor
  );
  return interpolated;
}

const pointCoordinates = validTrackPointCoordinates;

function interpolateNumber(
  from: number | null | undefined,
  to: number | null | undefined,
  factor: number
): number | undefined {
  if (typeof from !== 'number' || typeof to !== 'number' || !Number.isFinite(from) || !Number.isFinite(to)) {
    if (typeof to === 'number') return to;
    if (typeof from === 'number') return from;
    return undefined;
  }
  return from + (to - from) * factor;
}

function progressForSimulationPoint(
  trackIndex: number,
  track: TrackPoint[],
  point: TrackPoint,
  elapsedSec: number
): RacerProgress {
  const durationSec = segmentDurationSeconds(trackIndex);
  const firstDistance = Number(track[0]?.distanceInMeterSinceStart ?? 0);
  const currentDistance = Number(point.distanceInMeterSinceStart ?? firstDistance);
  const fallbackDistance = matchingCrossings.value?.[trackIndex]?.crossings?.[1]?.distanceInMeterSinceLastTriggerPoint;
  const progressRatio = durationSec > 0 ? Math.min(1, Math.max(0, elapsedSec / durationSec)) : 0;
  const distanceM =
    Number.isFinite(currentDistance) && currentDistance >= firstDistance
      ? currentDistance - firstDistance
      : Number(fallbackDistance ?? 0) * progressRatio;
  return {
    elapsedSec,
    progressRatio,
    distanceM,
    finished: durationSec > 0 && progressRatio >= 1,
  };
}

function racerStats(entry: {
  crossing: MatchingCrossing;
  originalIndex?: number;
}): Array<{ icon: string; text: string }> {
  const crossing = entry.crossing.crossings[1];
  const stats = [];
  const progress = entry.originalIndex == null ? null : racerProgress.value[entry.originalIndex];
  if (progress && hasStarted.value) {
    stats.push({
      icon: progress.finished ? 'bi-flag-fill' : 'bi-flag',
      text: `${formatNumber(progress.progressRatio * 100, 0)}%`,
    });
    if (progress.distanceM > 0) {
      stats.push({ icon: 'bi-signpost-split', text: formatDistance(progress.distanceM) ?? '' });
    }
  }
  if (crossing?.timeInSecSinceLastTriggerPoint) {
    stats.push({ icon: 'bi-stopwatch', text: formatDuration(crossing.timeInSecSinceLastTriggerPoint * 1000) });
  }
  if (crossing?.avgSpeedSinceLastTriggerPoint) {
    stats.push({ icon: 'bi-speedometer', text: formatSpeed(crossing.avgSpeedSinceLastTriggerPoint, 1) });
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
  prepareToken.value++;
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
  --mtl-slider-track-height-default: 4px;
  --mtl-slider-track-height-coarse: 8px;
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

/* ── Placeholder ── */
.vr-warning {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.5rem 0.75rem;
  border-radius: 6px;
  background: var(--surface-glass);
  color: var(--text-muted);
  font-size: var(--text-sm-size);
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
