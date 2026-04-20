<template>

  <div class="virtual-race-container">

    <!-- Controls -->
    <div class="vr-controls">
      <div class="vr-control-row">
        <label class="vr-label">Segment</label>
        <Select v-model="selectedSegment" :options="availableSegments" optionLabel="name"
                option-value="code"
                placeholder="select segment"
                class="vr-segment-select">
          <template #option="slotProps">
            <div>{{ slotProps.option.name }}&nbsp;&nbsp;({{ slotProps.option.count }} tracks)</div>
          </template>
        </Select>
      </div>
      <div class="vr-control-row">
        <label class="vr-label"><i class="bi bi-speedometer2"></i> {{ playbackSpeedDisplay }}</label>
        <Slider :min="1" :max="100" v-model="playbackSpeedSelector" class="vr-speed-slider"></Slider>
        <span v-if="estimatedDurationDisplay" class="vr-est-duration" v-tooltip.top="'Estimated avg. animation time'">~{{ estimatedDurationDisplay }}</span>
        <Button @click="onStart" class="vr-start-btn" :disabled="!selectedSegment">
          <i class="bi bi-play-fill"></i> Start
        </Button>
      </div>
    </div>

    <!-- Map -->
    <MiniMap
        ref="minimapRef"
        v-show="showMinimap"
        :tracks-geo-json="raceGeoJson"
        :map-bounds="mapBounds"
        class="vr-minimap"></MiniMap>

    <!-- Racers legend -->
    <div v-if="matchingCrossings != null && matchingCrossings.length > 0" class="vr-legend">
      <div class="vr-legend-header">
        <i class="bi bi-people-fill"></i> Racers
        <span class="vr-legend-count">{{ matchingCrossings.length }}</span>
      </div>
      <div class="vr-legend-grid">
        <div v-for="(crossing, index) in matchingCrossings" :key="index"
             class="vr-racer-card"
             @click="openTrackDetails(crossing.gpsTrack.id)">
          <div class="vr-racer-main">
            <span class="vr-racer-swatch" :style="{ backgroundColor: simulationColors[index] }"></span>
            <div class="vr-racer-info">
              <span class="vr-racer-name">{{ crossing.gpsTrack.indexedFile.name }}</span>
              <span class="vr-racer-date">{{ formatTrackDate(crossing.gpsTrack.startDate) }}</span>
            </div>
          </div>
          <div class="vr-racer-stats">
            <span v-if="crossing.crossings[1]?.timeInSecSinceLastTriggerPoint" class="vr-stat">
              <i class="bi bi-stopwatch"></i> {{ formatSegmentDuration(crossing.crossings[1].timeInSecSinceLastTriggerPoint) }}
            </span>
            <span v-if="crossing.crossings[1]?.avgSpeedSinceLastTriggerPoint" class="vr-stat">
              <i class="bi bi-speedometer"></i> {{ formatNumber(crossing.crossings[1].avgSpeedSinceLastTriggerPoint, 1) }} km/h
            </span>
            <span v-if="crossing.crossings[1]?.distanceInMeterSinceLastTriggerPoint" class="vr-stat">
              <i class="bi bi-signpost-split"></i> {{ formatDistance(crossing.crossings[1].distanceInMeterSinceLastTriggerPoint) }}
            </span>
          </div>
        </div>
      </div>
    </div>

  </div>

</template>

<script>

import { defineComponent, inject } from "vue";
import { fetchTrackSubTrackDetails } from "@/utils/ServiceHelper";
import MiniMap from "@/components/map/MiniMap.vue";
import { generateColors, formatDateAndTime, formatDuration, formatNumber, formatDistance } from "@/utils/Utils";


export default defineComponent({
  name: 'VirtualRace',
  components: { MiniMap },
  props: ["measureServiceResult", "consolidateVisits"],
  emits: ['show-track-details'],
  data() {
    return {
      selectedSegment: null,
      matchingCrossings: null,
      playbackSpeedSelector: 40,
      avgSegmentDurationSec: 0,
      showMinimap: false,
      trackDetailDataResults: null,
      simulationStartRealtime: -1,
      simulationColors: [],
      raceGeoJson: null,
      racerTrails: [],
      animationTimerId: null,
      mapBounds: [[8.505778, 47.5605], [8.525778, 47.5705]],
      playbackSpeed() {
        let val = Math.round(Math.pow(1.1, this.playbackSpeedSelector));
        if (val > 10000) {
          return Math.round(val / 1000) * 1000;
        } else if (val > 1000) {
          return Math.round(val / 100) * 100;
        } else if (val > 100) {
          return Math.round(val / 10) * 10;
        } else {
          return val;
        }
      },
    }
  },
  computed: {
    availableSegments() {
      if (!this.measureServiceResult) return [];

      if (this.consolidateVisits !== false) {
        return this.measureServiceResult.segmentsStats.map(segment => ({
          name: segment.label,
          count: segment.count,
          code: { point1: segment.point1, point2: segment.point2, consolidated: true },
        }));
      }

      // Unconsolidated: discover all numbered visit pairs from raw crossing data
      const segmentMap = new Map();
      for (const [, trackCrossings] of Object.entries(this.measureServiceResult.crossings)) {
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

    playbackSpeedDisplay() {
      return this.playbackSpeed() + 'x';
    },
    estimatedDurationDisplay() {
      if (!this.avgSegmentDurationSec || this.avgSegmentDurationSec <= 0) return null;
      let secs = this.avgSegmentDurationSec / this.playbackSpeed();
      if (secs < 1) return '<1s';
      if (secs < 60) return Math.round(secs) + 's';
      let m = Math.floor(secs / 60);
      let s = Math.round(secs % 60);
      return m + 'm ' + (s > 0 ? s + 's' : '');
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
    if (this.availableSegments && this.availableSegments.length > 0) {
      this.selectedSegment = this.availableSegments[0].code;
    }
  },
  beforeUnmount() {
    this.stopAnimation();
  },
  methods: {

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
      const TARGET_SECONDS = 8;
      if (!this.selectedSegment || !this.measureServiceResult) {
        this.avgSegmentDurationSec = 0;
        return;
      }
      let totalDuration = 0;
      let count = 0;
      for (const [, crossings] of Object.entries(this.measureServiceResult.crossings)) {
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
        let targetSpeed = this.avgSegmentDurationSec / TARGET_SECONDS;
        if (targetSpeed < 1) targetSpeed = 1;
        let selector = Math.log(targetSpeed) / Math.log(1.1);
        this.playbackSpeedSelector = Math.round(Math.max(1, Math.min(100, selector)));
      } else {
        this.avgSegmentDurationSec = 0;
      }
    },

    async onStart() {
      this.showMinimap = true;
      setTimeout(() => {
        if (this.$refs.minimapRef?.invalidateMapSize) {
          this.$refs.minimapRef.invalidateMapSize();
        }
      }, 100);

      this.stopAnimation(); // in case we left something

      this.matchingCrossings = [];
      let fetchTrackDetailDataPromises = [];
      let triggerPointsInvolved = new Map();

      // now remove all the crossing which don't match
      for (let [trackId, crossings] of Object.entries(this.measureServiceResult.crossings)) {
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
            this.matchingCrossings.push({ crossings: [lastCrossing, crossing], gpsTrack: crossings.gpsTrack });

            let triggerPoint = crossing.triggerPoint;
            let lastTriggerPoint = lastCrossing.triggerPoint;
            triggerPointsInvolved.set(triggerPoint.name, triggerPoint);
            triggerPointsInvolved.set(lastTriggerPoint.name, lastTriggerPoint);

            let trackDataPointFrom = lastCrossing.gpsTrackDataPoint.id;
            let trackDataPointTo = crossing.gpsTrackDataPoint.id;
            fetchTrackDetailDataPromises.push(fetchTrackSubTrackDetails(trackDataPointFrom, trackDataPointTo));
          }

          lastCrossing = crossing;
        }
      }

      this.trackDetailDataResults = await Promise.all(fetchTrackDetailDataPromises);

      if (this.trackDetailDataResults == null || this.trackDetailDataResults.length <= 0) {
        this.toast.add({ severity: 'warning', summary: 'Warning', detail: 'No tracks found to animate', life: 2000 });
        return;
      }

      this.simulationStartRealtime = new Date().getTime();

      // Assign one color per unique track — racers from the same track share a color
      const uniqueTrackIds = [...new Set(this.matchingCrossings.map(mc => mc.gpsTrack.id))];
      const trackColorPalette = generateColors(uniqueTrackIds.length);
      const trackIdToColor = new Map(uniqueTrackIds.map((id, i) => [id, trackColorPalette[i]]));
      this.simulationColors = this.matchingCrossings.map(mc => trackIdToColor.get(mc.gpsTrack.id));

      this.racerTrails = this.trackDetailDataResults.map(() => []);

      // find the map bounds
      let cLatMin = Number.MAX_VALUE;
      let cLatMax = -Number.MAX_VALUE;
      let cLongMin = Number.MAX_VALUE;
      let cLongMax = -Number.MAX_VALUE;

      for (let track of this.trackDetailDataResults) {
        for (let point of track) {
          let lng = point.pointLongLat.coordinates[0];
          let lat = point.pointLongLat.coordinates[1];
          if (lat < cLatMin) cLatMin = lat;
          if (lat > cLatMax) cLatMax = lat;
          if (lng > cLongMax) cLongMax = lng;
          if (lng < cLongMin) cLongMin = lng;
        }
      }

      let dLat = (cLatMax - cLatMin) * 0.3;
      let dLong = (cLongMax - cLongMin) * 0.3;
      cLatMin -= dLat;
      cLatMax += dLat;
      cLongMin -= dLong;
      cLongMax += dLong;

      // MapLibre bounds: [[swLng, swLat], [neLng, neLat]]
      this.mapBounds = [[cLongMin, cLatMin], [cLongMax, cLatMax]];

      // Build initial GeoJSON with trigger points and race markers
      this.buildRaceGeoJson(triggerPointsInvolved);

      this.animationTimerId = setInterval(this.animateRace, 33);
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

    formatSegmentDuration(seconds) {
      return formatDuration(seconds * 1000);
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
  gap: 16px;
  height: 100%;
  flex-grow: 1;
}

.p-dialog-maximized .virtual-race-container {
  height: initial;
}

/* ── Controls ── */
.vr-controls {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.vr-control-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.vr-label {
  font-size: 0.85rem;
  color: var(--text-secondary);
  white-space: nowrap;
  min-width: 5rem;
}

.vr-segment-select {
  flex: 1;
  min-width: 0;
}

.vr-speed-slider {
  flex: 1;
  min-width: 6rem;
  max-width: 14rem;
}

.vr-start-btn {
  white-space: nowrap;
}

.vr-est-duration {
  font-size: 0.78rem;
  color: var(--text-muted);
  white-space: nowrap;
  min-width: 3rem;
  text-align: center;
  cursor: default;
}

/* ── Mini map ── */
.vr-minimap {
  min-height: min(350px, 50svh);
  flex-grow: 1;
  height: 100%;
  width: 100%;
  border-radius: 8px;
  overflow: hidden;
}

.p-dialog-maximized .vr-minimap {
  height: 75vh;
}

/* ── Legend ── */
.vr-legend {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.vr-legend-header {
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--text-secondary);
  display: flex;
  align-items: center;
  gap: 6px;
}

.vr-legend-count {
  background: var(--surface-glass, rgba(255,255,255,0.08));
  border-radius: 10px;
  padding: 1px 8px;
  font-size: 0.75rem;
  font-weight: 500;
}

.vr-legend-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 8px;
}

.vr-racer-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--surface-glass, rgba(255,255,255,0.04));
  border: 1px solid var(--border-medium, rgba(255,255,255,0.08));
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
}

.vr-racer-card:hover {
  background: var(--surface-hover, rgba(255,255,255,0.08));
  border-color: var(--border-light, rgba(255,255,255,0.16));
}

.vr-racer-main {
  display: flex;
  align-items: center;
  gap: 10px;
}

.vr-racer-swatch {
  display: block;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  flex-shrink: 0;
  box-shadow: 0 0 0 2px rgba(255,255,255,0.15);
}

.vr-racer-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.vr-racer-name {
  font-size: 0.85rem;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.vr-racer-date {
  font-size: 0.75rem;
  color: var(--text-secondary);
}

.vr-racer-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding-left: 24px;
}

.vr-stat {
  font-size: 0.75rem;
  color: var(--text-secondary);
  display: flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
}

.vr-stat i {
  font-size: 0.7rem;
  opacity: 0.7;
}

@media (pointer: coarse) {
  .vr-speed-slider {
    min-width: 0;
    max-width: none;
  }
}

</style>
