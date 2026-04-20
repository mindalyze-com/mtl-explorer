<template>
  <div class="overview-container" v-if="gpsTrack && gpsTrack.indexedFile">

    <!-- Track Header -->
    <div class="track-header">
      <div class="track-header__top">
        <span class="track-header__name">{{ trackDisplayName }}</span>
        <ActivityTypeBadge :type="gpsTrack.activityType" size="sm" />
      </div>
      <div class="track-header__meta">
        <span v-if="gpsTrack.startDate">
          <i class="bi bi-calendar3"></i>
          {{ formatDateAndTime(new Date(gpsTrack.startDate)) }}
        </span>
      </div>
      <div v-if="trackDescription" class="track-header__desc">
        <i class="bi bi-card-text"></i> {{ trackDescription }}
      </div>
    </div>

    <!-- Loading skeleton -->
    <div v-if="!summaryReady" class="skeleton-grid">
      <div class="skeleton-tile" v-for="n in 4" :key="n"></div>
    </div>

    <!-- Primary Metrics -->
    <div class="metrics-primary" v-if="summaryReady">
      <div class="metric-tile metric-tile--primary">
        <i class="bi bi-signpost-split metric-tile__icon"></i>
        <div class="metric-tile__value"
             v-tooltip.top="{ value: formatDistanceTooltip(gpsTrack.trackLengthInMeter), showDelay: 400 }">{{ formatDistanceSmart(gpsTrack.trackLengthInMeter) }}</div>
        <div class="metric-tile__label">Distance</div>
      </div>
      <div class="metric-tile metric-tile--primary">
        <i class="bi bi-clock metric-tile__icon"></i>
        <div class="metric-tile__value"
             v-tooltip.top="{ value: formatDurationTooltip(trackDuration), showDelay: 400 }">{{ formatDurationSmart(trackDuration) }}</div>
        <div class="metric-tile__label">Duration</div>
      </div>
      <div class="metric-tile metric-tile--primary">
        <i class="bi bi-arrow-up metric-tile__icon" style="color: var(--success)"></i>
        <div class="metric-tile__value">{{ formatNumber(totalAscent, 0) }} m</div>
        <div class="metric-tile__label">Ascent</div>
      </div>
      <div class="metric-tile metric-tile--primary">
        <i class="bi bi-speedometer2 metric-tile__icon"></i>
        <div class="metric-tile__value">{{ formatNumber(avgSpeed, 1) }} <span class="metric-tile__unit">km/h</span></div>
        <div class="metric-tile__label">Avg Speed</div>
      </div>
    </div>

    <!-- Secondary Metrics -->
    <div class="metrics-secondary" v-if="summaryReady">
      <!-- Timing row -->
      <div class="section-label"><i class="bi bi-stopwatch"></i> Timing</div>
      <div class="metrics-grid">
        <div class="metric-tile">
          <i class="bi bi-play-fill metric-tile__icon metric-tile__icon--sm"></i>
          <div class="metric-tile__value metric-tile__value--sm"
               v-tooltip.top="{ value: formatDurationTooltip(movingTimeMs), showDelay: 400 }">{{ formatDurationSmart(movingTimeMs, trackDuration) }}</div>
          <div class="metric-tile__label">Moving</div>
        </div>
        <div class="metric-tile">
          <i class="bi bi-pause-fill metric-tile__icon metric-tile__icon--sm"></i>
          <div class="metric-tile__value metric-tile__value--sm"
               v-tooltip.top="{ value: formatDurationTooltip(stoppedTimeMs), showDelay: 400 }">{{ formatDurationSmart(stoppedTimeMs, trackDuration) }}</div>
          <div class="metric-tile__label">Stopped</div>
        </div>
        <div class="metric-tile" v-if="gpsTrack.trackStopCount != null">
          <i class="bi bi-stop-circle metric-tile__icon metric-tile__icon--sm"></i>
          <div class="metric-tile__value metric-tile__value--sm">{{ gpsTrack.trackStopCount }}</div>
          <div class="metric-tile__label">Stops</div>
        </div>
        <div class="metric-tile" v-if="longestStopMs > 0">
          <i class="bi bi-hourglass-split metric-tile__icon metric-tile__icon--sm"></i>
          <div class="metric-tile__value metric-tile__value--sm"
               v-tooltip.top="{ value: formatDurationTooltip(longestStopMs), showDelay: 400 }">{{ formatDurationSmart(longestStopMs, stoppedTimeMs) }}</div>
          <div class="metric-tile__label">Longest Stop</div>
        </div>
      </div>

      <!-- Speed row -->
      <div class="section-label"><i class="bi bi-speedometer2"></i> Speed</div>
      <div class="metrics-grid">
        <div class="metric-tile">
          <i class="bi bi-speedometer metric-tile__icon metric-tile__icon--sm"></i>
          <div class="metric-tile__value metric-tile__value--sm">{{ formatNumber(avgSpeed, 1) }} <span class="metric-tile__unit">km/h</span></div>
          <div class="metric-tile__label">Avg Speed</div>
        </div>
        <div class="metric-tile" v-if="movingAvgSpeed > 0">
          <i class="bi bi-speedometer2 metric-tile__icon metric-tile__icon--sm" style="color: var(--success)"></i>
          <div class="metric-tile__value metric-tile__value--sm">{{ formatNumber(movingAvgSpeed, 1) }} <span class="metric-tile__unit">km/h</span></div>
          <div class="metric-tile__label">Moving Avg</div>
        </div>
        <div class="metric-tile" v-if="maxSpeed > 0">
          <i class="bi bi-lightning metric-tile__icon metric-tile__icon--sm" style="color: var(--warning)"></i>
          <div class="metric-tile__value metric-tile__value--sm">{{ formatNumber(maxSpeed, 1) }} <span class="metric-tile__unit">km/h</span></div>
          <div class="metric-tile__label">Max Speed</div>
        </div>
      </div>

      <!-- Altitude row -->
      <div class="section-label"><i class="bi bi-triangle"></i> Elevation</div>
      <div class="metrics-grid">
        <div class="metric-tile">
          <i class="bi bi-arrow-up metric-tile__icon metric-tile__icon--sm" style="color: var(--success)"></i>
          <div class="metric-tile__value metric-tile__value--sm">{{ formatNumber(totalAscent, 0) }} m</div>
          <div class="metric-tile__label">Ascent</div>
        </div>
        <div class="metric-tile">
          <i class="bi bi-arrow-down metric-tile__icon metric-tile__icon--sm" style="color: var(--warning)"></i>
          <div class="metric-tile__value metric-tile__value--sm">{{ formatNumber(Math.abs(totalDescent), 0) }} m</div>
          <div class="metric-tile__label">Descent</div>
        </div>
        <div class="metric-tile">
          <div class="metric-tile__value metric-tile__value--sm">{{ formatNumber(minAltitude, 0) }} m</div>
          <div class="metric-tile__label">Min Alt.</div>
        </div>
        <div class="metric-tile">
          <div class="metric-tile__value metric-tile__value--sm">{{ formatNumber(maxAltitude, 0) }} m</div>
          <div class="metric-tile__label">Max Alt.</div>
        </div>
        <div class="metric-tile">
          <div class="metric-tile__value metric-tile__value--sm">{{ formatNumber(maxAltitude - minAltitude, 0) }} m</div>
          <div class="metric-tile__label">Range</div>
        </div>
        <div class="metric-tile">
          <div class="metric-tile__value metric-tile__value--sm">{{ formatNumber(maxSlope, 1) }}%</div>
          <div class="metric-tile__label">Max Slope</div>
        </div>
        <div class="metric-tile">
          <div class="metric-tile__value metric-tile__value--sm">{{ formatNumber(minSlope, 1) }}%</div>
          <div class="metric-tile__label">Max Desc. Slope</div>
        </div>
      </div>
    </div>

    <!-- Energy Section -->
    <div class="energy-section" v-if="summaryReady">
      <div class="section-label"><i class="bi bi-lightning-charge"></i> Energy</div>

      <!-- Not yet calculated -->
      <div v-if="!hasEnergy" class="exploration-pending">
        <i class="pi pi-spin pi-spinner exploration-pending__spinner"></i>
        <span>Energy stats are being calculated…</span>
      </div>

      <!-- Summary row -->
      <div class="metrics-grid" v-if="hasEnergy">
        <div class="metric-tile metric-tile--energy energy-tooltip-wrapper" @click="toggleTooltip('energy')">
          <i class="bi bi-battery-half metric-tile__icon metric-tile__icon--sm" style="color: var(--accent)"></i>
          <div class="metric-tile__value metric-tile__value--sm">{{ formatNumber(gpsTrack.energyNetTotalWh, 1) }} Wh</div>
          <div class="metric-tile__label">Net Total</div>
          <div class="energy-tooltip" :class="{ 'energy-tooltip--visible': activeTooltip === 'energy' }">
            {{ whToJoules(gpsTrack.energyNetTotalWh) }} J &middot; {{ whToKcal(gpsTrack.energyNetTotalWh) }} kcal
          </div>
        </div>
        <div class="metric-tile metric-tile--energy energy-tooltip-wrapper" @click="toggleTooltip('avgPower')">
          <i class="bi bi-lightning metric-tile__icon metric-tile__icon--sm" style="color: var(--accent)"></i>
          <div class="metric-tile__value metric-tile__value--sm">{{ formatNumber(gpsTrack.powerWattsAvg, 0) }} W</div>
          <div class="metric-tile__label">Avg Power</div>
          <div class="energy-tooltip" :class="{ 'energy-tooltip--visible': activeTooltip === 'avgPower' }">
            {{ wattsToKcalH(gpsTrack.powerWattsAvg) }} kcal/h
          </div>
        </div>
        <div class="metric-tile metric-tile--energy energy-tooltip-wrapper" v-if="gpsTrack.powerWattsMovingAvg" @click="toggleTooltip('avgMovingPower')">
          <i class="bi bi-lightning metric-tile__icon metric-tile__icon--sm" style="color: var(--accent)"></i>
          <div class="metric-tile__value metric-tile__value--sm">{{ formatNumber(gpsTrack.powerWattsMovingAvg, 0) }} W</div>
          <div class="metric-tile__label">Avg Moving Power</div>
          <div class="energy-tooltip" :class="{ 'energy-tooltip--visible': activeTooltip === 'avgMovingPower' }">
            {{ wattsToKcalH(gpsTrack.powerWattsMovingAvg) }} kcal/h &middot; excl. breaks
          </div>
        </div>
        <div class="metric-tile metric-tile--energy energy-tooltip-wrapper" @click="toggleTooltip('maxPower')">
          <i class="bi bi-lightning-fill metric-tile__icon metric-tile__icon--sm" style="color: var(--accent)"></i>
          <div class="metric-tile__value metric-tile__value--sm">{{ formatNumber(gpsTrack.powerWattsMax, 0) }} W</div>
          <div class="metric-tile__label">Max Power</div>
          <div class="energy-tooltip" :class="{ 'energy-tooltip--visible': activeTooltip === 'maxPower' }">
            {{ wattsToKcalH(gpsTrack.powerWattsMax) }} kcal/h
          </div>
        </div>
        <div class="metric-tile metric-tile--energy" v-if="gpsTrack.energyWeightKgUsed">
          <i class="bi bi-person metric-tile__icon metric-tile__icon--sm" style="color: var(--accent)"></i>
          <div class="metric-tile__value metric-tile__value--sm">{{ formatNumber(gpsTrack.energyWeightKgUsed, 1) }} kg</div>
          <div class="metric-tile__label">Weight Used</div>
        </div>
      </div>

      <!-- Detailed breakdown -->
      <div class="energy-breakdown-label" v-if="hasEnergy">Breakdown</div>
      <div class="metrics-grid" v-if="hasEnergy">
        <div class="metric-tile metric-tile--energy" v-if="gpsTrack.energyGravitationalTotalWh != null">
          <div class="metric-tile__value metric-tile__value--sm">{{ formatNumber(gpsTrack.energyGravitationalTotalWh, 1) }} Wh</div>
          <div class="metric-tile__label">Gravitational ↑</div>
        </div>
        <div class="metric-tile metric-tile--energy" v-if="gpsTrack.energyGravitationalDescentWh != null">
          <div class="metric-tile__value metric-tile__value--sm">{{ formatNumber(gpsTrack.energyGravitationalDescentWh, 1) }} Wh</div>
          <div class="metric-tile__label">Gravitational ↓</div>
        </div>
        <div class="metric-tile metric-tile--energy" v-if="gpsTrack.energyAeroDragTotalWh != null">
          <div class="metric-tile__value metric-tile__value--sm">{{ formatNumber(gpsTrack.energyAeroDragTotalWh, 1) }} Wh</div>
          <div class="metric-tile__label">Aero Drag</div>
        </div>
        <div class="metric-tile metric-tile--energy" v-if="gpsTrack.energyRollingResistanceTotalWh != null">
          <div class="metric-tile__value metric-tile__value--sm">{{ formatNumber(gpsTrack.energyRollingResistanceTotalWh, 1) }} Wh</div>
          <div class="metric-tile__label">Rolling Resist.</div>
        </div>
        <div class="metric-tile metric-tile--energy" v-if="gpsTrack.energyKineticPositiveTotalWh != null">
          <div class="metric-tile__value metric-tile__value--sm">{{ formatNumber(gpsTrack.energyKineticPositiveTotalWh, 1) }} Wh</div>
          <div class="metric-tile__label">Kinetic (pos.)</div>
        </div>
      </div>
    </div>

    <!-- Exploration Score -->
    <div class="exploration-section">
      <div class="section-label">
        <i class="bi bi-compass"></i> Exploration
        <button
          class="exploration-info-btn"
          :class="{ 'exploration-info-btn--active': showExplorationInfo }"
          v-tooltip.top="{ value: 'How much of this track covered new ground vs. routes taken before. Tap for details.', showDelay: 300 }"
          @click="showExplorationInfo = !showExplorationInfo"
          aria-label="Exploration score explanation"
        ><i class="bi bi-info-circle"></i></button>
      </div>

      <!-- Calculated: show metric tiles -->
      <div v-if="explorationIsCalculated" class="metrics-grid">
        <div class="metric-tile">
          <i class="bi bi-map metric-tile__icon metric-tile__icon--sm" style="color: var(--accent)"></i>
          <div class="metric-tile__value metric-tile__value--sm">{{ formatNumber(gpsTrack.explorationScore * 100, 1) }}%</div>
          <div class="metric-tile__label"
               v-tooltip.top="{ value: 'Share of this track that was further than 25 m from any track recorded before it. Reflects exploration at the time of recording — tracks added later that cover the same area are not taken into account.', showDelay: 300 }">New Territory</div>
        </div>
        <div class="metric-tile">
          <i class="bi bi-signpost-split metric-tile__icon metric-tile__icon--sm" style="color: var(--success)"></i>
          <div class="metric-tile__value metric-tile__value--sm">{{ formatDistanceSmart((gpsTrack.explorationScore ?? 0) * (gpsTrack.trackLengthInMeter ?? 0)) }}</div>
          <div class="metric-tile__label"
               v-tooltip.top="{ value: 'Distance on segments you have never ridden/walked before', showDelay: 300 }">Novel Distance</div>
        </div>
        <div class="metric-tile">
          <i class="bi bi-arrow-repeat metric-tile__icon metric-tile__icon--sm" style="color: var(--warning)"></i>
          <div class="metric-tile__value metric-tile__value--sm">{{ formatDistanceSmart((1 - (gpsTrack.explorationScore ?? 0)) * (gpsTrack.trackLengthInMeter ?? 0)) }}</div>
          <div class="metric-tile__label"
               v-tooltip.top="{ value: 'Distance on segments you have covered before (within 25 m)', showDelay: 300 }">Known Distance</div>
        </div>
      </div>

      <!-- Pending: being calculated -->
      <div v-else-if="explorationIsPending" class="exploration-pending">
        <i class="pi pi-spin pi-spinner exploration-pending__spinner"></i>
        <span>Exploration score is being calculated…</span>
      </div>

      <!-- Not scheduled / unavailable -->
      <div v-else class="exploration-unavailable">
        <i class="bi bi-dash-circle exploration-unavailable__icon"></i>
        <span>Exploration score not available for this track.</span>
      </div>

      <div v-if="showExplorationInfo" class="exploration-info-panel">
        <div class="exploration-info-panel__row">
          <i class="bi bi-compass exploration-info-panel__icon"></i>
          <span>Compares this track to <strong>all your earlier tracks</strong>. A segment counts as <em>known</em> if you've passed within <strong>25 m</strong> of it on a previous activity.</span>
        </div>
        <div class="exploration-info-panel__legend">
          <span class="exploration-legend-dot exploration-legend-dot--new"></span>
          <span><strong>New Territory</strong> — ground never covered before (or not within 25 m)</span>
        </div>
        <div class="exploration-info-panel__legend">
          <span class="exploration-legend-dot exploration-legend-dot--known"></span>
          <span><strong>Known Distance</strong> — route you've already covered on a prior activity</span>
        </div>
      </div>
    </div>

    <!-- Collapsible Track Details -->
    <details class="info-drawer">
      <summary class="info-drawer__summary">
        <i class="bi bi-info-circle"></i> Track Details
        <i class="bi bi-chevron-down info-drawer__chevron"></i>
      </summary>
      <div class="info-list">
        <div class="info-row"><span class="info-key">ID</span><span class="info-val">{{ gpsTrack.id }}</span></div>
        <div class="info-row" v-if="gpsTrack.trackName"><span class="info-key">Track Name</span><span class="info-val">{{ gpsTrack.trackName }}</span></div>
        <div class="info-row"><span class="info-key">Track Description</span><span class="info-val">{{ gpsTrack.trackDescription || gpsTrack.metaDescription || '—' }}</span></div>
        <div class="info-row" v-if="gpsTrack.metaName"><span class="info-key">Meta Name</span><span class="info-val">{{ gpsTrack.metaName }}</span></div>
        <div class="info-row" v-if="gpsTrack.activityType"><span class="info-key">Activity</span><span class="info-val"><ActivityTypeBadge :type="gpsTrack.activityType" size="xs" /></span></div>
        <div class="info-row" v-if="gpsTrack.activityTypeSourceDetails"><span class="info-key">Activity Source</span><span class="info-val">{{ gpsTrack.activityTypeSourceDetails }}</span></div>
        <div class="info-row" v-if="gpsTrack.creator"><span class="info-key">Creator</span><span class="info-val">{{ gpsTrack.creator }}</span></div>
        <div class="info-row" v-if="gpsTrack.startDate"><span class="info-key">Start</span><span class="info-val">{{ formatDateAndTime(new Date(gpsTrack.startDate)) }}</span></div>
        <div class="info-row" v-if="gpsTrack.endDate"><span class="info-key">End</span><span class="info-val">{{ formatDateAndTime(new Date(gpsTrack.endDate)) }}</span></div>
        <div class="info-row"><span class="info-key">File</span><span class="info-val">{{ gpsTrack.indexedFile.name }}</span></div>
        <div class="info-row"><span class="info-key">Path</span><span class="info-val info-val--mono">{{ gpsTrack.indexedFile.fullPath || gpsTrack.indexedFile.basePath + '/' + gpsTrack.indexedFile.name || gpsTrack.indexedFile.path || '—' }}</span></div>
      </div>
    </details>

  </div>
</template>

<script lang="ts">
import {defineComponent} from "vue";
import {formatDateAndTime, formatDistance, formatDuration, formatNumber, formatDistanceSmart, formatDurationSmart, formatDistanceTooltip, formatDurationTooltip} from "@/utils/Utils";
import type {GpsTrackDataPoint} from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import ActivityTypeBadge from '@/components/ui/ActivityTypeBadge.vue';

export default defineComponent({
  name: 'TrackDetailOverview',
  components: { ActivityTypeBadge },
  props: {
    gpsTrack: {type: Object, default: null},
    trackDetails: {type: Array as () => GpsTrackDataPoint[], required: true},
  },
  data() {
    return {
      summaryReady: false,
      totalAscent: 0,
      totalDescent: 0,
      minAltitude: 0,
      maxAltitude: 0,
      avgSpeed: 0,
      maxSpeed: 0,
      maxSlope: 0,
      minSlope: 0,
      activeTooltip: null as string | null,
      showExplorationInfo: false,
    };
  },
  computed: {
    trackDuration(): number {
      if (this.gpsTrack?.startDate && this.gpsTrack?.endDate) {
        return new Date(this.gpsTrack.endDate).getTime() - new Date(this.gpsTrack.startDate).getTime();
      }
      return 0;
    },
    /**
     * Moving time in ms, sourced from the server's {@code trackDurationInMotionSecs}
     * (computed once at ingest from the outlier-cleaned point stream). The client
     * must not recompute this from simplified track variants — those drop points
     * and lack per-point speeds, which used to produce wildly wrong totals.
     */
    movingTimeMs(): number {
      const secs = this.gpsTrack?.trackDurationInMotionSecs;
      return secs != null ? secs * 1000 : 0;
    },
    /**
     * Detected-stopped time in ms: contiguous runs of ≥ 30 s under 0.5 km/h,
     * again sourced from the server ({@code trackDurationStoppedSecs}). This
     * excludes "gap" time where GPS simply stopped recording, which is why
     * moving + stopped can be less than trackDuration.
     */
    stoppedTimeMs(): number {
      const secs = this.gpsTrack?.trackDurationStoppedSecs;
      return secs != null ? secs * 1000 : 0;
    },
    hasEnergy(): boolean {
      return this.gpsTrack?.energyNetTotalWh != null && this.gpsTrack.energyNetTotalWh !== 0;
    },
    trackDisplayName(): string {
      return this.gpsTrack?.trackName || this.gpsTrack?.metaName || this.gpsTrack?.indexedFile?.name || `Track #${this.gpsTrack?.id}`;
    },
    trackDescription(): string {
      return this.gpsTrack?.trackDescription || this.gpsTrack?.metaDescription || '';
    },
    explorationIsCalculated(): boolean {
      return this.gpsTrack?.explorationStatus === 'CALCULATED' && this.gpsTrack?.explorationScore != null;
    },
    explorationIsPending(): boolean {
      return ['SCHEDULED', 'IN_PROGRESS', 'NEEDS_RECALCULATION'].includes(this.gpsTrack?.explorationStatus ?? '');
    },
    movingAvgSpeed(): number {
      const dist = this.gpsTrack?.trackLengthInMeter;
      const secs = this.gpsTrack?.trackDurationInMotionSecs;
      if (dist != null && secs != null && secs > 0) {
        return (dist / secs) * 3.6; // m/s → km/h
      }
      return 0;
    },
    longestStopMs(): number {
      const secs = this.gpsTrack?.trackLongestStopSecs;
      return secs != null ? secs * 1000 : 0;
    },

  },
  mounted() {
    if (this.trackDetails.length > 0) {
      this.computeSummary(this.trackDetails);
    }
  },
  watch: {
    trackDetails(details: GpsTrackDataPoint[]) {
      if (details.length > 0) {
        this.computeSummary(details);
      }
    },
  },
  methods: {
    formatDistance,
    formatDuration,
    formatDateAndTime,
    formatNumber,
    formatDistanceSmart,
    formatDurationSmart,
    formatDistanceTooltip,
    formatDurationTooltip,
    whToJoules(wh: number | null | undefined): string {
      return this.formatNumber((wh ?? 0) * 3600, 0);
    },
    whToKcal(wh: number | null | undefined): string {
      return this.formatNumber((wh ?? 0) * 0.8604, 0);
    },
    wattsToKcalH(w: number | null | undefined, decimals = 1): string {
      return this.formatNumber((w ?? 0) * 0.8604, decimals);
    },
    toggleTooltip(id: string) {
      this.activeTooltip = this.activeTooltip === id ? null : id;
    },
    computeSummary(details: GpsTrackDataPoint[]) {
      if (!details || details.length === 0) return;

      const lastPoint = details[details.length - 1];
      this.totalAscent = lastPoint.ascentInMeterSinceStart ?? 0;
      this.totalDescent = lastPoint.descentInMeterSinceStart ?? 0;

      let minAlt = Infinity;
      let maxAlt = -Infinity;
      let speedSum = 0;
      let speedCount = 0;
      let maxSpeedVal = -Infinity;
      let maxSlope = -Infinity;
      let minSlope = Infinity;

      for (const p of details) {
        if (p.pointAltitude != null) {
          if (p.pointAltitude < minAlt) minAlt = p.pointAltitude;
          if (p.pointAltitude > maxAlt) maxAlt = p.pointAltitude;
        }
        if (p.speedInKmhMovingWindow != null) {
          speedSum += p.speedInKmhMovingWindow;
          speedCount++;
          if (p.speedInKmhMovingWindow > maxSpeedVal) maxSpeedVal = p.speedInKmhMovingWindow;
        }
        if (p.slopePercentageInMovingWindow != null) {
          if (p.slopePercentageInMovingWindow > maxSlope) maxSlope = p.slopePercentageInMovingWindow;
          if (p.slopePercentageInMovingWindow < minSlope) minSlope = p.slopePercentageInMovingWindow;
        }
      }

      this.minAltitude = minAlt === Infinity ? 0 : minAlt;
      this.maxAltitude = maxAlt === -Infinity ? 0 : maxAlt;
      this.avgSpeed = speedCount > 0 ? speedSum / speedCount : 0;
      this.maxSpeed = maxSpeedVal === -Infinity ? 0 : maxSpeedVal;
      this.maxSlope = maxSlope === -Infinity ? 0 : maxSlope;
      this.minSlope = minSlope === Infinity ? 0 : minSlope;
      // movingTimeMs / stoppedTimeMs are now computed props driven by the
      // server-side trackDurationInMotionSecs / trackDurationStoppedSecs
      // fields — no client-side recomputation from simplified points.
      this.summaryReady = true;
    },
  },
});
</script>

<style scoped>
/* ── Container ── */
.overview-container {
  display: flex;
  flex-direction: column;
  gap: 0;
  width: 100%;
  padding-bottom: 0.5rem;
}

/* ── Track Header ── */
.track-header {
  padding: 0.5rem 1rem 0.75rem;
  border-bottom: 1px solid var(--border-subtle);
  margin-bottom: 0.25rem;
}

.track-header__top {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.track-header__name {
  font-size: 1.1rem;
  font-weight: 700;
  color: var(--text-primary);
  flex: 1 1 auto;
  line-height: 1.3;
  word-break: break-word;
}

.track-header__meta {
  display: flex;
  align-items: baseline;
  gap: 0.75rem;
  margin-top: 0.35rem;
  font-size: 0.8rem;
  color: var(--text-muted);
  flex-wrap: wrap;
}

.track-header__meta i {
  font-size: 0.75rem;
  margin-right: 0.25rem;
  opacity: 0.7;
}

.track-header__desc {
  font-size: 0.85rem;
  color: var(--text-muted);
  font-style: italic;
  margin-top: 0.3rem;
  line-height: 1.4;
}

.track-header__desc i {
  opacity: 0.6;
  margin-right: 0.25rem;
}

/* ── Section Label ── */
.section-label {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.68rem;
  font-weight: 600;
  letter-spacing: 0.07em;
  text-transform: uppercase;
  color: var(--text-faint);
  padding: 0.75rem 1rem 0.35rem;
}

.section-label i {
  font-size: 0.75rem;
  opacity: 0.7;
}

/* ── Primary Metrics ── */
.metrics-primary {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 0;
  border-bottom: 1px solid var(--border-subtle);
}

.metrics-primary .metric-tile {
  border-right: 1px solid var(--border-subtle);
  border-radius: 0;
  padding: 1rem 0.5rem;
}

.metrics-primary .metric-tile:last-child {
  border-right: none;
}

/* ── Metric Tile (shared) ── */
.metric-tile {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  text-align: center;
  padding: 0.75rem 0.5rem;
  background: transparent;
  position: relative;
}

.metric-tile__icon {
  font-size: 1.1rem;
  color: var(--text-muted);
  margin-bottom: 0.3rem;
}

.metric-tile__icon--sm {
  font-size: 0.9rem;
  margin-bottom: 0.2rem;
}

.metric-tile__value {
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1.1;
  letter-spacing: -0.01em;
}

.metric-tile__value--sm {
  font-size: 1rem;
  font-weight: 600;
}

.metric-tile__unit {
  font-size: 0.85em;
  font-weight: 500;
  color: var(--text-muted);
}

.metric-tile__label {
  font-size: 0.7rem;
  color: var(--text-muted);
  margin-top: 0.2rem;
  letter-spacing: 0.02em;
}

/* ── Secondary grid ── */
.metrics-secondary {
  border-bottom: 1px solid var(--border-subtle);
  padding-bottom: 0.5rem;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 0;
  padding: 0 0.5rem;
}

.metrics-grid .metric-tile {
  border-radius: 8px;
  background: var(--surface-elevated);
  margin: 0.2rem;
  padding: 0.6rem 0.4rem;
}

/* ── Energy section ── */
.energy-section {
  border-bottom: 1px solid var(--border-subtle);
  padding-bottom: 0.5rem;
}

/* ── Exploration section ── */
.exploration-section {
  border-bottom: 1px solid var(--border-subtle);
  padding-bottom: 0.5rem;
}

.exploration-info-btn {
  margin-left: auto;
  background: none;
  border: none;
  padding: 0 0.1rem;
  cursor: pointer;
  color: var(--text-faint);
  font-size: 0.82rem;
  line-height: 1;
  display: flex;
  align-items: center;
  transition: color 0.15s;
}

.exploration-info-btn:hover,
.exploration-info-btn--active {
  color: var(--accent);
}

.exploration-pending,
.exploration-unavailable {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin: 0.4rem 1rem 0.6rem;
  font-size: 0.82rem;
  color: var(--text-secondary);
}

.exploration-pending__spinner {
  font-size: 0.85rem;
  color: var(--accent);
}

.exploration-unavailable__icon {
  font-size: 0.85rem;
  opacity: 0.5;
}

.exploration-info-panel {
  margin: 0.4rem 1rem 0.6rem;
  padding: 0.65rem 0.85rem;
  border-radius: 0.5rem;
  background: var(--accent-bg);
  border: 1px solid var(--accent-subtle);
  font-size: 0.8rem;
  color: var(--text-secondary);
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
  line-height: 1.45;
}

.exploration-info-panel__row {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
}

.exploration-info-panel__icon {
  flex-shrink: 0;
  margin-top: 0.1rem;
  color: var(--accent-muted);
}

.exploration-info-panel__legend {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.exploration-legend-dot {
  flex-shrink: 0;
  width: 0.65rem;
  height: 0.65rem;
  border-radius: 50%;
}

.exploration-legend-dot--new {
  background: var(--accent);
}

.exploration-legend-dot--known {
  background: var(--warning);
}

.metric-tile--energy {
  border: 1px solid var(--accent-subtle) !important;
  background: var(--accent-bg) !important;
}

/* ── Energy tooltip ── */
.energy-tooltip-wrapper {
  cursor: pointer;
}

.energy-tooltip {
  display: none;
  position: absolute;
  bottom: calc(100% + 6px);
  left: 50%;
  transform: translateX(-50%);
  background: var(--surface-glass-heavy);
  color: var(--text-primary);
  font-size: 0.72rem;
  font-weight: 400;
  padding: 4px 10px;
  border-radius: 6px;
  white-space: nowrap;
  z-index: 10;
  pointer-events: none;
  box-shadow: var(--shadow-md);
  border: 1px solid var(--border-default);
}

.energy-tooltip::after {
  content: '';
  position: absolute;
  top: 100%;
  left: 50%;
  transform: translateX(-50%);
  border: 5px solid transparent;
  border-top-color: var(--surface-glass-heavy);
}

@media (hover: hover) and (pointer: fine) {
  .energy-tooltip-wrapper:hover .energy-tooltip {
    display: block;
  }
}

.energy-tooltip--visible {
  display: block;
}

/* ── Energy breakdown sub-label ── */
.energy-breakdown-label {
  font-size: 0.67rem;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--text-faint);
  padding: 0.4rem 1rem 0.2rem;
  opacity: 0.8;
}

/* ── Loading skeleton ── */
.skeleton-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  border-bottom: 1px solid var(--border-subtle);
}

.skeleton-tile {
  height: 80px;
  margin: 0.75rem 0.5rem;
  border-radius: 8px;
  background: linear-gradient(90deg, var(--surface-elevated) 25%, var(--surface-hover) 50%, var(--surface-elevated) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.4s infinite;
}

@keyframes shimmer {
  0%   { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

/* ── Info Drawer ── */
.info-drawer {
  margin: 0.5rem 0.5rem 0;
  border-radius: 8px;
  border: 1px solid var(--border-default);
  overflow: hidden;
}

.info-drawer[open] .info-drawer__chevron {
  transform: rotate(180deg);
}

.info-drawer__chevron {
  transition: transform 0.2s ease;
}

.info-drawer__summary {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.6rem 0.9rem;
  font-size: 0.78rem;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--text-muted);
  cursor: pointer;
  user-select: none;
  list-style: none;
  background: var(--surface-elevated);
}

.info-drawer__summary::-webkit-details-marker { display: none; }

.info-drawer__summary i:first-child {
  font-size: 0.8rem;
}

.info-drawer__summary .info-drawer__chevron {
  margin-left: auto;
  font-size: 0.75rem;
}

.info-list {
  display: flex;
  flex-direction: column;
  padding: 0.25rem 0;
  background: var(--surface-elevated);
}

.info-row {
  display: flex;
  align-items: baseline;
  gap: 0.75rem;
  padding: 0.35rem 0.9rem;
  font-size: 0.8rem;
  border-top: 1px solid var(--border-subtle);
}

.info-key {
  flex: 0 0 7rem;
  color: var(--text-muted);
  font-size: 0.72rem;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.info-val {
  color: var(--text-secondary);
  word-break: break-word;
  min-width: 0;
}

.info-val--mono {
  font-family: ui-monospace, SFMono-Regular, 'SF Mono', Menlo, monospace;
  font-size: 0.72rem;
  opacity: 0.8;
}

/* ── Mobile ── */
@media (max-width: 480px) {
  .metrics-primary {
    grid-template-columns: repeat(2, 1fr);
  }

  .metrics-primary .metric-tile:nth-child(2) {
    border-right: none;
  }

  .metrics-primary .metric-tile:nth-child(1),
  .metrics-primary .metric-tile:nth-child(2) {
    border-bottom: 1px solid var(--border-subtle);
  }

  .skeleton-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .metrics-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}
</style>
