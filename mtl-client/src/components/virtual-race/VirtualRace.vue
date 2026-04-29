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
        <Slider :min="0" :max="100" v-model="playbackSpeedSelector" class="vr-speed-slider"></Slider>
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
          @hover-racer="hoveredRacerIndex = $event"
          @leave-racer="hoveredRacerIndex = null"
          class="vr-minimap"></MiniMap>
      <!-- Racer count pill — top right -->
      <span v-if="selectedSegment && selectedSegmentCount != null" class="vr-map-racer-pill">
        <i class="bi bi-people-fill"></i> {{ selectedSegmentCount }} racers
      </span>
      <!-- Play / Reset — bottom center -->
      <div class="vr-map-playback">
        <Button @click="onPlayPause" class="vr-start-btn" :disabled="!selectedSegment || isPreviewLoading">
          <i :class="isRunning ? 'bi bi-pause-fill' : 'bi bi-play-fill'"></i>
        </Button>
        <Button @click="onReset" class="vr-reset-btn" :disabled="!hasStarted" severity="secondary">
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
          :dateStr="formatTrackDate(entry.crossing.gpsTrack.startDate)"
          :trackId="entry.crossing.gpsTrack.id"
          :activityType="entry.crossing.gpsTrack.activityType || null"
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

<script>

import { defineComponent, inject } from "vue";
import { fetchTrackSubTrackDetails } from "@/utils/ServiceHelper";
import MiniMap from "@/components/map/MiniMap.vue";
import RacerCard from "@/components/ui/RacerCard.vue";
import { generateColors, formatDateAndTime, formatDuration, formatNumber, formatDistance } from "@/utils/Utils";


export default defineComponent({
  name: 'VirtualRace',
  components: { MiniMap, RacerCard },
  props: {
    measureServiceResult: { type: Object, default: null },
    consolidateVisits: { type: Boolean, default: true },
    initialSegment: { type: Object, default: null },
    selectedTrackIds: { type: Object, default: () => new Set() },
  },
  emits: ['show-track-details'],
  data() {
    return {
      selectedSegment: null,
      matchingCrossings: null,
      playbackSpeedSelector: 49,
      avgSegmentDurationSec: 0,
      showMinimap: false,
      trackDetailDataResults: null,
      simulationStartRealtime: -1,
      simulationColors: [],
      raceGeoJson: null,
      racerTrails: [],
      animationTimerId: null,
      isPaused: false,
      pausedElapsedRealMs: 0,
      isPreviewLoading: false,
      _prepareToken: 0,
      triggerPointsInvolved: new Map(),
      mapBounds: [[8.505778, 47.5605], [8.525778, 47.5705]],
      hoveredRacerIndex: null,
      // Derives speed multiplier from desired animation duration.
      // Slider pos 0 = 60s (slowest), pos 100 = 1s (fastest).
      playbackSpeed() {
        if (!this.avgSegmentDurationSec || this.avgSegmentDurationSec <= 0) return 1;
        const animDurationSec = Math.exp(
          (1 - this.playbackSpeedSelector / 100) * Math.log(60)
        );
        return Math.max(1, this.avgSegmentDurationSec / animDurationSec);
      },
    }
  },
  computed: {
    availableSegments() {
      if (!this.measureServiceResult) return [];

      if (this.consolidateVisits !== false) {
        return this.measureServiceResult.segmentsStats.map(segment => ({
          name: segment.label,
          count: this.selectedSegmentCounts.get(segment.point1 + '||' + segment.point2) ?? 0,
          code: { point1: segment.point1, point2: segment.point2, consolidated: true },
        }));
      }

      // Unconsolidated: discover all numbered visit pairs from raw crossing data (filtered by selection)
      const segmentMap = new Map();
      for (const [trackId, trackCrossings] of Object.entries(this.measureServiceResult.crossings)) {
        const tid = Number(trackId);
        if (!this.selectedTrackIds.has(tid)) continue;
        const countPerTP = new Map();
        let lastCrossing = null;
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
            segmentMap.get(key).count++;
          }
          lastCrossing = crossing;
        }
      }
      return Array.from(segmentMap.values());
    },

    // Selected-track-filtered count per consolidated segment key (point1||point2).
    selectedSegmentCounts() {
      if (!this.measureServiceResult) return new Map();
      const counts = new Map();
      for (const [trackId, crossings] of Object.entries(this.measureServiceResult.crossings)) {
        const tid = Number(trackId);
        if (!this.selectedTrackIds.has(tid)) continue;
        let lastCrossing = null;
        for (const crossing of crossings.crossings) {
          if (lastCrossing != null) {
            const key = lastCrossing.triggerPoint.name + '||' + crossing.triggerPoint.name;
            counts.set(key, (counts.get(key) || 0) + 1);
          }
          lastCrossing = crossing;
        }
      }
      return counts;
    },

    selectedSegmentKey() {
      if (!this.selectedSegment) return null;
      const code = this.selectedSegment;
      if (code.consolidated === false) {
        return code.point1 + code.p1Visit + '-' + code.point2 + code.p2Visit;
      }
      return code.point1 + '||' + code.point2;
    },
    selectedSegmentCount() {
      if (!this.selectedSegment) return null;
      const key = this.selectedSegmentKey;
      const seg = this.availableSegments.find(s => this.segmentChipKey(s) === key);
      return seg?.count ?? null;
    },
    // Right-side slider label: always in seconds, with derived multiplier when segment data loaded.
    speedInfoDisplay() {
      const secs = Math.max(1, Math.round(Math.exp((1 - this.playbackSpeedSelector / 100) * Math.log(60))));
      if (!this.avgSegmentDurationSec || this.avgSegmentDurationSec <= 0) {
        return secs + 's';
      }
      const speed = this.playbackSpeed();
      if (speed >= 1.5) {
        return Math.round(speed) + 'x · ' + secs + 's';
      }
      return secs + 's';
    },
    /**
     * Legend display order: fastest arrival first. We keep the original index
     * (needed because simulationColors / raceGeoJson features are keyed by
     * insertion order) and sort only the presentation.
     */
    isRunning() {
      return this.animationTimerId != null;
    },
    hasStarted() {
      return this.trackDetailDataResults != null;
    },
    sortedRacers() {
      if (!this.matchingCrossings) return [];
      const entries = this.matchingCrossings.map((crossing, originalIndex) => ({
        crossing,
        originalIndex,
        durationSec: crossing.crossings?.[1]?.timeInSecSinceLastTriggerPoint ?? Number.POSITIVE_INFINITY,
      }));
      entries.sort((a, b) => a.durationSec - b.durationSec);
      return entries;
    },
  },
  setup() {
    return {
      toast: inject("toast"),
    };
  },
  watch: {
    selectedSegment() {
      this.autoSelectSpeed();
      this.preparePreview();
    },
    selectedTrackIds() {
      // Selection changed outside — refresh preview for new subset.
      this.autoSelectSpeed();
      this.preparePreview();
    },
    consolidateVisits() {
      this.selectedSegment = null;
      this.$nextTick(() => {
        if (this.availableSegments && this.availableSegments.length > 0) {
          this.selectedSegment = this.availableSegments[0].code;
        }
      });
    },
  },
  mounted() {
    if (this.initialSegment) {
      this.selectedSegment = this.initialSegment;
    } else if (this.availableSegments && this.availableSegments.length > 0) {
      this.selectedSegment = this.availableSegments[0].code;
    }
    // preparePreview() will be triggered by the selectedSegment watcher above.
  },
  beforeUnmount() {
    this.stopAnimation();
  },
  methods: {

    segmentChipKey(seg) {
      const code = seg.code;
      if (!code) return '';
      if (code.consolidated === false) {
        return code.point1 + code.p1Visit + '-' + code.point2 + code.p2Visit;
      }
      return code.point1 + '||' + code.point2;
    },

    _matchesSelectedSegment(lastCrossing, crossing, countPerTP) {
      const seg = this.selectedSegment;
      if (!seg) return false;
      if (seg.point1 !== lastCrossing.triggerPoint?.name) return false;
      if (seg.point2 !== crossing.triggerPoint?.name) return false;
      if (seg.consolidated === false) {
        if (seg.p1Visit !== countPerTP.get(lastCrossing.triggerPoint.name)) return false;
        if (seg.p2Visit !== countPerTP.get(crossing.triggerPoint.name)) return false;
      }
      return true;
    },

    autoSelectSpeed() {
      const TARGET_SECONDS = 12;
      if (!this.selectedSegment || !this.measureServiceResult) {
        this.avgSegmentDurationSec = 0;
        return;
      }
      let totalDuration = 0;
      let count = 0;
      for (const [trackId, crossings] of Object.entries(this.measureServiceResult.crossings)) {
        const tid = Number(trackId);
        if (!this.selectedTrackIds.has(tid)) continue;
        const countPerTP = new Map();
        let lastCrossing = null;
        for (const crossing of crossings.crossings) {
          const name = crossing.triggerPoint.name;
          countPerTP.set(name, (countPerTP.get(name) || 0) + 1);
          if (lastCrossing != null &&
              this._matchesSelectedSegment(lastCrossing, crossing, countPerTP) &&
              crossing.timeInSecSinceLastTriggerPoint) {
            totalDuration += crossing.timeInSecSinceLastTriggerPoint;
            count++;
          }
          lastCrossing = crossing;
        }
      }
      if (count > 0) {
        this.avgSegmentDurationSec = totalDuration / count;
        // Set slider so animation runs for TARGET_SECONDS.
        // Inverse of: secs = exp((1 - pos/100) * log(60))
        //   → pos = 100 * (1 - log(secs) / log(60))
        const pos = 100 * (1 - Math.log(TARGET_SECONDS) / Math.log(60));
        this.playbackSpeedSelector = Math.round(Math.max(0, Math.min(100, pos)));
      } else {
        this.avgSegmentDurationSec = 0;
      }
    },

    async preparePreview() {
      if (!this.selectedSegment || !this.measureServiceResult) {
        this.stopAnimation();
        this.matchingCrossings = null;
        this.trackDetailDataResults = null;
        this.raceGeoJson = null;
        this.showMinimap = false;
        return;
      }

      this.stopAnimation();
      this.isPaused = false;
      this.pausedElapsedRealMs = 0;
      this.simulationStartRealtime = -1;
      this.isPreviewLoading = true;

      const token = ++this._prepareToken;
      const activeTrackIds = new Set(this.selectedTrackIds);

      const matchingCrossings = [];
      const fetchPromises = [];
      const triggerPointsInvolved = new Map();

      for (let [trackId, crossings] of Object.entries(this.measureServiceResult.crossings)) {
        const tid = Number(trackId);
        if (!activeTrackIds.has(tid)) continue;
        const countPerTP = new Map();
        let lastCrossing = null;
        for (let crossing of crossings.crossings) {
          const name = crossing.triggerPoint.name;
          countPerTP.set(name, (countPerTP.get(name) || 0) + 1);
          if (
              lastCrossing != null &&
              this._matchesSelectedSegment(lastCrossing, crossing, countPerTP) &&
              lastCrossing.gpsTrackDataPoint?.id != null &&
              crossing.gpsTrackDataPoint?.id != null
          ) {
            matchingCrossings.push({ crossings: [lastCrossing, crossing], gpsTrack: crossings.gpsTrack });
            triggerPointsInvolved.set(crossing.triggerPoint.name, crossing.triggerPoint);
            triggerPointsInvolved.set(lastCrossing.triggerPoint.name, lastCrossing.triggerPoint);
            fetchPromises.push(fetchTrackSubTrackDetails(
              lastCrossing.gpsTrackDataPoint.id,
              crossing.gpsTrackDataPoint.id
            ));
          }
          lastCrossing = crossing;
        }
      }

      const results = await Promise.all(fetchPromises);
      const adjustedResults = results.map((track, i) =>
        this.withVirtualEndpoints(track, matchingCrossings[i]?.crossings)
      );

      if (token !== this._prepareToken) return; // stale — newer prepare in flight

      this.isPreviewLoading = false;

      if (!adjustedResults || adjustedResults.length === 0) {
        this.matchingCrossings = null;
        this.trackDetailDataResults = null;
        this.raceGeoJson = null;
        return;
      }

      this.matchingCrossings = matchingCrossings;
      this.trackDetailDataResults = adjustedResults;
      this.triggerPointsInvolved = triggerPointsInvolved;

      // Assign one color per unique track
      const uniqueTrackIds = [...new Set(matchingCrossings.map(mc => mc.gpsTrack.id))];
      const trackColorPalette = generateColors(uniqueTrackIds.length);
      const trackIdToColor = new Map(uniqueTrackIds.map((id, i) => [id, trackColorPalette[i]]));
      this.simulationColors = matchingCrossings.map(mc => trackIdToColor.get(mc.gpsTrack.id));

      this.racerTrails = adjustedResults.map(() => []);

      // Compute map bounds
      let cLatMin = Number.MAX_VALUE, cLatMax = -Number.MAX_VALUE;
      let cLongMin = Number.MAX_VALUE, cLongMax = -Number.MAX_VALUE;
      for (let track of adjustedResults) {
        for (let point of track) {
          const lng = point.pointLongLat.coordinates[0];
          const lat = point.pointLongLat.coordinates[1];
          if (lat < cLatMin) cLatMin = lat;
          if (lat > cLatMax) cLatMax = lat;
          if (lng > cLongMax) cLongMax = lng;
          if (lng < cLongMin) cLongMin = lng;
        }
      }
      const dLat = (cLatMax - cLatMin) * 0.3;
      const dLong = (cLongMax - cLongMin) * 0.3;
      this.mapBounds = [[cLongMin - dLong, cLatMin - dLat], [cLongMax + dLong, cLatMax + dLat]];

      // Show racers at their start positions
      this.buildRaceGeoJson(triggerPointsInvolved);

      this.showMinimap = true;
      this.$nextTick(() => {
        if (this.$refs.minimapRef?.invalidateMapSize) {
          this.$refs.minimapRef.invalidateMapSize();
        }
      });
    },

    withVirtualEndpoints(track, crossingPair) {
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

      const inner = track.filter(point => {
        const pointDuration = point?.durationSinceStart;
        if (typeof pointDuration !== 'number') {
          return true;
        }
        return pointDuration > startDuration && pointDuration < endDuration;
      });

      return [start, ...inner, end];
    },

    buildRaceGeoJson(triggerPointsInvolved) {
      const features = [];

      // Trigger point circles with labels
      triggerPointsInvolved.forEach((triggerPoint) => {
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
      for (let track of this.trackDetailDataResults) {
        trackIndex++;
        const simulationPoint = track[0];
        const color = this.simulationColors[trackIndex];
        const trackName = this.matchingCrossings[trackIndex]?.gpsTrack?.indexedFile?.name || ('Track ' + trackIndex);
        features.push({
          type: 'Feature',
          geometry: {
            type: 'Point',
            coordinates: [simulationPoint.pointLongLat.coordinates[0], simulationPoint.pointLongLat.coordinates[1]],
          },
          properties: { type: 'racer', trackIndex, color, trackName },
        });
      }

      this.raceGeoJson = { type: 'FeatureCollection', features };
    },

    animateRace() {
      let timeSinceStartInSeconds = (new Date().getTime() - this.simulationStartRealtime) / 1000;
      let timeSinceStartSimulationInSeconds = timeSinceStartInSeconds * this.playbackSpeed();

      let foundAtLeastOne = false;
      const features = [];

      // Keep trigger points (they don't move)
      if (this.raceGeoJson) {
        for (const f of this.raceGeoJson.features) {
          if (f.properties.type === 'trigger') features.push(f);
        }
      }

      let trackIndex = -1;
      for (let track of this.trackDetailDataResults) {
        trackIndex++;
        let trackStartTime = track[0].durationSinceStart;
        const color = this.simulationColors[trackIndex];

        let simulationPoint = null;
        for (let point of track) {
          let trackTimeSinceStartInSeconds = point.durationSinceStart - trackStartTime;
          if (trackTimeSinceStartInSeconds > timeSinceStartSimulationInSeconds) {
            foundAtLeastOne = true;
            simulationPoint = point;
            break;
          }
        }

        if (simulationPoint == null) {
          simulationPoint = track[track.length - 1];
        }

        const coord = [simulationPoint.pointLongLat.coordinates[0], simulationPoint.pointLongLat.coordinates[1]];

        // Append to trail (avoid duplicates)
        const trail = this.racerTrails[trackIndex];
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
            trackName: this.matchingCrossings[trackIndex]?.gpsTrack?.indexedFile?.name || ('Track ' + trackIndex),
          },
        });
      }

      this.raceGeoJson = { type: 'FeatureCollection', features };

      if (!foundAtLeastOne) {
        this.stopAnimation();
      }
    },

    onPlayPause() {
      if (this.isRunning) {
        // Pause: record how much real time has elapsed so resume can offset correctly.
        this.pausedElapsedRealMs = new Date().getTime() - this.simulationStartRealtime;
        this.stopAnimation();
        this.isPaused = true;
      } else if (this.isPaused) {
        // Resume: shift simulationStartRealtime forward by the pause duration.
        this.simulationStartRealtime = new Date().getTime() - this.pausedElapsedRealMs;
        this.isPaused = false;
        this.animationTimerId = setInterval(this.animateRace, 33);
      } else {
        // Fresh start — data already preloaded by preparePreview().
        if (!this.trackDetailDataResults || this.trackDetailDataResults.length === 0) return;
        this.racerTrails = this.trackDetailDataResults.map(() => []);
        this.buildRaceGeoJson(this.triggerPointsInvolved);
        this.simulationStartRealtime = new Date().getTime();
        this.animationTimerId = setInterval(this.animateRace, 33);
      }
    },

    onReset() {
      this.stopAnimation();
      this.isPaused = false;
      this.pausedElapsedRealMs = 0;
      this.simulationStartRealtime = -1;
      // Restore racers to their start positions without re-fetching.
      if (this.trackDetailDataResults && this.trackDetailDataResults.length > 0) {
        this.racerTrails = this.trackDetailDataResults.map(() => []);
        this.buildRaceGeoJson(this.triggerPointsInvolved);
      }
    },

    stopAnimation() {
      if (this.animationTimerId) {
        clearInterval(this.animationTimerId);
        this.animationTimerId = null;
      }
    },

    openTrackDetails(id) {
      this.$emit('show-track-details', id);
    },

    formatTrackDate(date) {
      return formatDateAndTime(date);
    },

    racerStats(entry) {
      const crossing = entry.crossing.crossings[1];
      const stats = [];
      if (crossing?.timeInSecSinceLastTriggerPoint) {
        stats.push({ icon: 'bi-stopwatch', text: formatDuration(crossing.timeInSecSinceLastTriggerPoint * 1000) });
      }
      if (crossing?.avgSpeedSinceLastTriggerPoint) {
        stats.push({ icon: 'bi-speedometer', text: formatNumber(crossing.avgSpeedSinceLastTriggerPoint, 1) + ' km/h' });
      }
      if (crossing?.distanceInMeterSinceLastTriggerPoint) {
        stats.push({ icon: 'bi-signpost-split', text: formatDistance(crossing.distanceInMeterSinceLastTriggerPoint) });
      }
      return stats;
    },

    formatNumber,
    formatDistance,
  },
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
  transition: background 0.15s, color 0.15s, border-color 0.15s;
}

.vr-chip:hover { color: var(--text-secondary); background: var(--surface-hover); }

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
