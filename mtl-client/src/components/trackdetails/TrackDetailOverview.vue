<template>
  <div v-if="gpsTrack && gpsTrack.indexedFile" class="overview-container">
    <!-- Track Header -->
    <div class="track-header">
      <div class="track-header__top">
        <span class="track-header__name">{{ trackDisplayName }}</span>
        <div v-if="canDownloadTrackSource" class="track-header__actions">
          <button
            type="button"
            class="track-header__action-btn"
            :disabled="activeDownload !== null"
            aria-label="Download original"
            title="Download original indexed file"
            @click="downloadOriginal"
          >
            <i :class="activeDownload === 'original' ? 'pi pi-spin pi-spinner' : 'bi bi-download'"></i>
          </button>
          <button
            v-if="canDownloadGpx"
            type="button"
            class="track-header__action-btn"
            :disabled="activeDownload !== null"
            aria-label="Download GPX"
            title="Download as GPX"
            @click="downloadGpx"
          >
            <i :class="activeDownload === 'gpx' ? 'pi pi-spin pi-spinner' : 'bi bi-file-earmark-code'"></i>
          </button>
        </div>
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
      <div v-for="n in 4" :key="n" class="skeleton-tile"></div>
    </div>

    <!-- Primary Metrics -->
    <div v-if="summaryReady" class="metrics-primary">
      <div class="metric-tile metric-tile--primary">
        <i class="bi bi-signpost-split metric-tile__icon"></i>
        <div
          v-tooltip.top="{ value: formatOptionalDistanceTooltip(gpsTrack.trackLengthInMeter), showDelay: 400 }"
          class="metric-tile__value"
        >
          {{ formatOptionalDistanceSmart(gpsTrack.trackLengthInMeter) }}
        </div>
        <div class="metric-tile__label">Distance</div>
      </div>
      <div class="metric-tile metric-tile--primary">
        <i class="bi bi-clock metric-tile__icon"></i>
        <div v-tooltip.top="{ value: formatDurationTooltip(trackDuration), showDelay: 400 }" class="metric-tile__value">
          {{ formatDurationSmart(trackDuration) }}
        </div>
        <div class="metric-tile__label">Duration</div>
      </div>
      <div class="metric-tile metric-tile--primary">
        <i class="bi bi-arrow-up metric-tile__icon" style="color: var(--success)"></i>
        <div class="metric-tile__value">{{ formatNumber(totalAscent, 0) }} m</div>
        <div class="metric-tile__label">Ascent</div>
      </div>
      <div class="metric-tile metric-tile--primary">
        <i class="bi bi-speedometer2 metric-tile__icon"></i>
        <div class="metric-tile__value">
          {{ formatNumber(avgSpeed, 1) }} <span class="metric-tile__unit">km/h</span>
        </div>
        <div class="metric-tile__label">Avg Speed</div>
      </div>
    </div>

    <!-- Secondary Metrics -->
    <div v-if="summaryReady" class="metrics-secondary">
      <!-- Timing row -->
      <div class="section-label"><i class="bi bi-stopwatch"></i> Timing</div>
      <div class="metrics-grid">
        <div class="metric-tile">
          <i class="bi bi-play-fill metric-tile__icon metric-tile__icon--sm"></i>
          <div
            v-tooltip.top="{ value: formatDurationTooltip(movingTimeMs), showDelay: 400 }"
            class="metric-tile__value metric-tile__value--sm"
          >
            {{ formatDurationSmart(movingTimeMs, trackDuration) }}
          </div>
          <div class="metric-tile__label">Moving</div>
        </div>
        <div class="metric-tile">
          <i class="bi bi-pause-fill metric-tile__icon metric-tile__icon--sm"></i>
          <div
            v-tooltip.top="{ value: formatDurationTooltip(stoppedTimeMs), showDelay: 400 }"
            class="metric-tile__value metric-tile__value--sm"
          >
            {{ formatDurationSmart(stoppedTimeMs, trackDuration) }}
          </div>
          <div class="metric-tile__label">Stopped</div>
        </div>
        <div v-if="gpsTrack.trackStopCount != null" class="metric-tile">
          <i class="bi bi-stop-circle metric-tile__icon metric-tile__icon--sm"></i>
          <div class="metric-tile__value metric-tile__value--sm">{{ gpsTrack.trackStopCount }}</div>
          <div class="metric-tile__label">Stops</div>
        </div>
        <div v-if="longestStopMs > 0" class="metric-tile">
          <i class="bi bi-hourglass-split metric-tile__icon metric-tile__icon--sm"></i>
          <div
            v-tooltip.top="{ value: formatDurationTooltip(longestStopMs), showDelay: 400 }"
            class="metric-tile__value metric-tile__value--sm"
          >
            {{ formatDurationSmart(longestStopMs, stoppedTimeMs) }}
          </div>
          <div class="metric-tile__label">Longest Stop</div>
        </div>
      </div>

      <!-- Speed row -->
      <div class="section-label"><i class="bi bi-speedometer2"></i> Speed</div>
      <div class="metrics-grid">
        <div class="metric-tile">
          <i class="bi bi-speedometer metric-tile__icon metric-tile__icon--sm"></i>
          <div class="metric-tile__value metric-tile__value--sm">
            {{ formatNumber(avgSpeed, 1) }} <span class="metric-tile__unit">km/h</span>
          </div>
          <div class="metric-tile__label">Avg Speed</div>
        </div>
        <div v-if="movingAvgSpeed > 0" class="metric-tile">
          <i class="bi bi-speedometer2 metric-tile__icon metric-tile__icon--sm" style="color: var(--success)"></i>
          <div class="metric-tile__value metric-tile__value--sm">
            {{ formatNumber(movingAvgSpeed, 1) }} <span class="metric-tile__unit">km/h</span>
          </div>
          <div class="metric-tile__label">Moving Avg</div>
        </div>
        <div v-if="maxSpeed > 0" class="metric-tile">
          <i class="bi bi-lightning metric-tile__icon metric-tile__icon--sm" style="color: var(--warning)"></i>
          <div class="metric-tile__value metric-tile__value--sm">
            {{ formatNumber(maxSpeed, 1) }} <span class="metric-tile__unit">km/h</span>
          </div>
          <div class="metric-tile__label">Max 30s Speed</div>
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
        <div v-if="maxElevationGainRate > 0" class="metric-tile">
          <i class="bi bi-arrow-up-right metric-tile__icon metric-tile__icon--sm" style="color: var(--success)"></i>
          <div class="metric-tile__value metric-tile__value--sm">
            {{ formatNumber(maxElevationGainRate, 0) }} <span class="metric-tile__unit">m/h</span>
          </div>
          <div class="metric-tile__label">Max 30s Climb</div>
        </div>
        <div v-if="maxElevationLossRate > 0" class="metric-tile">
          <i class="bi bi-arrow-down-right metric-tile__icon metric-tile__icon--sm" style="color: var(--warning)"></i>
          <div class="metric-tile__value metric-tile__value--sm">
            {{ formatNumber(maxElevationLossRate, 0) }} <span class="metric-tile__unit">m/h</span>
          </div>
          <div class="metric-tile__label">Max 30s Desc.</div>
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
          <div class="metric-tile__value metric-tile__value--sm">
            {{ formatNumber(maxAltitude - minAltitude, 0) }} m
          </div>
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
    <div v-if="summaryReady" class="energy-section">
      <div class="section-label">
        <i class="bi bi-lightning-charge"></i> Energy
        <button
          type="button"
          class="info-btn info-btn--inline"
          aria-label="About energy"
          @click.stop="showInfo($event, INFO_ENERGY)"
        >
          <i class="bi bi-info-circle"></i>
        </button>
      </div>

      <!-- Not yet calculated -->
      <div v-if="!hasEnergy" class="exploration-pending">
        <i class="pi pi-spin pi-spinner exploration-pending__spinner"></i>
        <span>Energy stats are being calculated…</span>
      </div>

      <!-- Summary row -->
      <div v-if="hasEnergy" class="metrics-grid">
        <div class="metric-tile metric-tile--energy energy-tooltip-wrapper" @click="toggleTooltip('energy')">
          <i class="bi bi-battery-half metric-tile__icon metric-tile__icon--sm" style="color: var(--accent)"></i>
          <div class="metric-tile__value metric-tile__value--sm">
            {{ formatNumber(gpsTrack.energyNetTotalWh, 1) }} Wh
          </div>
          <div class="metric-tile__label">Net Total</div>
          <div class="energy-tooltip" :class="{ 'energy-tooltip--visible': activeTooltip === 'energy' }">
            {{ whToJoules(gpsTrack.energyNetTotalWh) }} J &middot; {{ whToKcal(gpsTrack.energyNetTotalWh) }} kcal
            mechanical equivalent
          </div>
        </div>
        <div class="metric-tile metric-tile--energy energy-tooltip-wrapper" @click="toggleTooltip('avgPower')">
          <i class="bi bi-lightning metric-tile__icon metric-tile__icon--sm" style="color: var(--accent)"></i>
          <div class="metric-tile__value metric-tile__value--sm">{{ formatNumber(gpsTrack.powerWattsAvg, 0) }} W</div>
          <div class="metric-tile__label">
            Avg Power
            <button
              type="button"
              class="info-btn info-btn--inline"
              aria-label="About Average Power"
              @click.stop="showInfo($event, INFO_AVG_POWER)"
            >
              <i class="bi bi-info-circle"></i>
            </button>
          </div>
          <div class="energy-tooltip" :class="{ 'energy-tooltip--visible': activeTooltip === 'avgPower' }">
            {{ wattsToKcalH(gpsTrack.powerWattsAvg) }} kcal/h mechanical equivalent
          </div>
        </div>
        <div
          v-if="gpsTrack.powerWattsMovingAvg"
          class="metric-tile metric-tile--energy energy-tooltip-wrapper"
          @click="toggleTooltip('avgMovingPower')"
        >
          <i class="bi bi-lightning metric-tile__icon metric-tile__icon--sm" style="color: var(--accent)"></i>
          <div class="metric-tile__value metric-tile__value--sm">
            {{ formatNumber(gpsTrack.powerWattsMovingAvg, 0) }} W
          </div>
          <div class="metric-tile__label">
            Avg Moving Power
            <button
              type="button"
              class="info-btn info-btn--inline"
              aria-label="About Average Moving Power"
              @click.stop="showInfo($event, INFO_AVG_MOVING_POWER)"
            >
              <i class="bi bi-info-circle"></i>
            </button>
          </div>
          <div class="energy-tooltip" :class="{ 'energy-tooltip--visible': activeTooltip === 'avgMovingPower' }">
            {{ wattsToKcalH(gpsTrack.powerWattsMovingAvg) }} kcal/h mechanical equivalent &middot; excl. breaks
          </div>
        </div>
        <div
          v-if="gpsTrack.powerWatts30sMax != null"
          class="metric-tile metric-tile--energy energy-tooltip-wrapper"
          @click="toggleTooltip('maxPower')"
        >
          <i class="bi bi-lightning-fill metric-tile__icon metric-tile__icon--sm" style="color: var(--accent)"></i>
          <div class="metric-tile__value metric-tile__value--sm">{{ formatNumber(peakPowerWatts, 0) }} W</div>
          <div class="metric-tile__label">
            Max 30s Power
            <button
              type="button"
              class="info-btn info-btn--inline"
              aria-label="About Max 30 second Power"
              @click.stop="showInfo($event, INFO_MAX_POWER)"
            >
              <i class="bi bi-info-circle"></i>
            </button>
          </div>
          <div class="energy-tooltip" :class="{ 'energy-tooltip--visible': activeTooltip === 'maxPower' }">
            {{ wattsToKcalH(peakPowerWatts) }} kcal/h mechanical equivalent
          </div>
        </div>
        <div v-if="gpsTrack.energyWeightKgUsed" class="metric-tile metric-tile--energy">
          <i class="bi bi-person metric-tile__icon metric-tile__icon--sm" style="color: var(--accent)"></i>
          <div class="metric-tile__value metric-tile__value--sm">
            {{ formatNumber(gpsTrack.energyWeightKgUsed, 1) }} kg
          </div>
          <div class="metric-tile__label">Mass Used</div>
        </div>
      </div>

      <!-- Detailed breakdown -->
      <div v-if="hasEnergy" class="energy-breakdown-label">Breakdown</div>
      <div v-if="hasEnergy" class="metrics-grid">
        <div v-if="gpsTrack.energyGravitationalTotalWh != null" class="metric-tile metric-tile--energy">
          <div class="metric-tile__value metric-tile__value--sm">
            {{ formatNumber(gpsTrack.energyGravitationalTotalWh, 1) }} Wh
          </div>
          <div class="metric-tile__label">Gravitational ↑</div>
        </div>
        <div v-if="gpsTrack.energyGravitationalDescentWh != null" class="metric-tile metric-tile--energy">
          <div class="metric-tile__value metric-tile__value--sm">
            {{ formatNumber(gpsTrack.energyGravitationalDescentWh, 1) }} Wh
          </div>
          <div class="metric-tile__label">Gravitational ↓</div>
        </div>
        <div v-if="gpsTrack.energyAeroDragTotalWh != null" class="metric-tile metric-tile--energy">
          <div class="metric-tile__value metric-tile__value--sm">
            {{ formatNumber(gpsTrack.energyAeroDragTotalWh, 1) }} Wh
          </div>
          <div class="metric-tile__label">Aero Drag</div>
        </div>
        <div v-if="gpsTrack.energyRollingResistanceTotalWh != null" class="metric-tile metric-tile--energy">
          <div class="metric-tile__value metric-tile__value--sm">
            {{ formatNumber(gpsTrack.energyRollingResistanceTotalWh, 1) }} Wh
          </div>
          <div class="metric-tile__label">Rolling Resist.</div>
        </div>
        <div v-if="gpsTrack.energyKineticPositiveTotalWh != null" class="metric-tile metric-tile--energy">
          <div class="metric-tile__value metric-tile__value--sm">
            {{ formatNumber(gpsTrack.energyKineticPositiveTotalWh, 1) }} Wh
          </div>
          <div class="metric-tile__label">Kinetic (pos.)</div>
        </div>
      </div>
    </div>

    <!-- Fitness Section (Normalized Power) -->
    <div v-if="summaryReady && gpsTrack.normalizedPowerWatts" class="energy-section">
      <div class="section-label"><i class="bi bi-heart-pulse"></i> Fitness</div>
      <div class="metrics-grid">
        <div class="metric-tile metric-tile--energy">
          <i class="bi bi-activity metric-tile__icon metric-tile__icon--sm" style="color: var(--accent)"></i>
          <div class="metric-tile__value metric-tile__value--sm">
            {{ formatNumber(gpsTrack.normalizedPowerWatts, 0) }} W
          </div>
          <div class="metric-tile__label">
            Normalized Power
            <button
              type="button"
              class="info-btn info-btn--inline"
              aria-label="About Normalized Power"
              @click.stop="showInfo($event, INFO_NORMALIZED_POWER)"
            >
              <i class="bi bi-info-circle"></i>
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Exploration Score -->
    <div class="exploration-section">
      <div class="section-label">
        <i class="bi bi-compass"></i> Exploration
        <button
          v-tooltip.top="{
            value: 'How much of this track covered new ground vs. routes taken before. Tap for details.',
            showDelay: 300,
          }"
          class="exploration-info-btn"
          :class="{ 'exploration-info-btn--active': showExplorationInfo }"
          aria-label="Exploration score explanation"
          @click="showExplorationInfo = !showExplorationInfo"
        >
          <i class="bi bi-info-circle"></i>
        </button>
      </div>

      <!-- Calculated: show metric tiles -->
      <div v-if="explorationIsCalculated" class="metrics-grid">
        <div class="metric-tile">
          <i class="bi bi-map metric-tile__icon metric-tile__icon--sm" style="color: var(--accent)"></i>
          <div class="metric-tile__value metric-tile__value--sm">
            {{ formatNumber((gpsTrack.explorationScore ?? 0) * 100, 1) }}%
          </div>
          <div
            v-tooltip.top="{
              value:
                'Share of this track that was further than 25 m from any track recorded before it. Reflects exploration at the time of recording — tracks added later that cover the same area are not taken into account.',
              showDelay: 300,
            }"
            class="metric-tile__label"
          >
            New Territory
          </div>
        </div>
        <div class="metric-tile">
          <i class="bi bi-signpost-split metric-tile__icon metric-tile__icon--sm" style="color: var(--success)"></i>
          <div class="metric-tile__value metric-tile__value--sm">
            {{ formatDistanceSmart((gpsTrack.explorationScore ?? 0) * (gpsTrack.trackLengthInMeter ?? 0)) }}
          </div>
          <div
            v-tooltip.top="{ value: 'Distance on segments you have never ridden/walked before', showDelay: 300 }"
            class="metric-tile__label"
          >
            Novel Distance
          </div>
        </div>
        <div class="metric-tile">
          <i class="bi bi-arrow-repeat metric-tile__icon metric-tile__icon--sm" style="color: var(--warning)"></i>
          <div class="metric-tile__value metric-tile__value--sm">
            {{ formatDistanceSmart((1 - (gpsTrack.explorationScore ?? 0)) * (gpsTrack.trackLengthInMeter ?? 0)) }}
          </div>
          <div
            v-tooltip.top="{ value: 'Distance on segments you have covered before (within 25 m)', showDelay: 300 }"
            class="metric-tile__label"
          >
            Known Distance
          </div>
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
          <span
            >Compares this track to <strong>all your earlier tracks</strong>. A segment counts as <em>known</em> if
            you've passed within <strong>25 m</strong> of it on a previous activity.</span
          >
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
        <div class="info-row">
          <span class="info-key">TrackID</span>
          <span class="info-val info-val--copy">
            <input
              class="info-copy-field"
              type="text"
              readonly
              :value="trackIdText"
              aria-label="TrackID"
              @focus="selectCopyField"
              @click="selectCopyField"
            />
            <button
              type="button"
              class="info-copy-btn"
              :disabled="!trackIdText"
              :aria-label="trackIdCopied ? 'TrackID copied' : 'Copy TrackID'"
              :title="trackIdCopied ? 'TrackID copied' : 'Copy TrackID'"
              @click="copyTrackId"
            >
              <i :class="trackIdCopied ? 'bi bi-clipboard-check' : 'bi bi-copy'"></i>
            </button>
          </span>
        </div>
        <div v-if="trackIdCopyError" class="info-row info-row--error">
          <span class="info-key"></span><span class="info-val info-copy-error">{{ trackIdCopyError }}</span>
        </div>
        <div v-if="gpsTrack.trackName" class="info-row">
          <span class="info-key">Track Name</span><span class="info-val">{{ gpsTrack.trackName }}</span>
        </div>
        <div class="info-row">
          <span class="info-key">Track Description</span
          ><span class="info-val">{{ gpsTrack.trackDescription || gpsTrack.metaDescription || '—' }}</span>
        </div>
        <div v-if="gpsTrack.metaName" class="info-row">
          <span class="info-key">Meta Name</span><span class="info-val">{{ gpsTrack.metaName }}</span>
        </div>
        <div v-if="gpsTrack.activityType" class="info-row">
          <span class="info-key">Activity</span
          ><span class="info-val"><ActivityTypeBadge :type="gpsTrack.activityType" size="xs" /></span>
        </div>
        <div v-if="gpsTrack.activityTypeSourceDetails" class="info-row">
          <span class="info-key">Activity Source</span
          ><span class="info-val">{{ gpsTrack.activityTypeSourceDetails }}</span>
        </div>
        <div v-if="gpsTrack.creator" class="info-row">
          <span class="info-key">Creator</span><span class="info-val">{{ gpsTrack.creator }}</span>
        </div>
        <div v-if="gpsTrack.startDate" class="info-row">
          <span class="info-key">Start</span
          ><span class="info-val">{{ formatDateAndTime(new Date(gpsTrack.startDate)) }}</span>
        </div>
        <div v-if="gpsTrack.endDate" class="info-row">
          <span class="info-key">End</span
          ><span class="info-val">{{ formatDateAndTime(new Date(gpsTrack.endDate)) }}</span>
        </div>
        <div class="info-row">
          <span class="info-key">File</span><span class="info-val">{{ gpsTrack.indexedFile.name }}</span>
        </div>
        <div class="info-row">
          <span class="info-key">Path</span
          ><span class="info-val info-val--mono">{{
            gpsTrack.indexedFile.fullPath ||
            gpsTrack.indexedFile.basePath + '/' + gpsTrack.indexedFile.name ||
            gpsTrack.indexedFile.path ||
            '—'
          }}</span>
        </div>
      </div>
    </details>

    <Popover ref="infoPopover" append-to="body">
      <div class="track-detail-info-text">
        <p v-for="(paragraph, paragraphIndex) in currentInfoContent" :key="paragraphIndex">
          <template v-for="(segment, segmentIndex) in paragraph" :key="segmentIndex">
            <strong v-if="segment.strong">{{ segment.text }}</strong>
            <span v-else>{{ segment.text }}</span>
          </template>
        </p>
      </div>
    </Popover>
  </div>
</template>

<script setup lang="ts">
import { computed, inject, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import {
  formatDateAndTime,
  formatNumber,
  formatDistanceSmart,
  formatDurationSmart,
  formatDistanceTooltip,
  formatDurationTooltip,
} from '@/utils/Utils';
import type { ChartPoint } from '@/utils/chartSeriesAdapter';
import ActivityTypeBadge from '@/components/ui/ActivityTypeBadge.vue';
import type { GpsTrack } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import { downloadTrackGpx as downloadTrackGpxFile, downloadTrackSourceFile } from '@/utils/ServiceHelper';

type TrackDetailInfoPopover = {
  toggle: (event: Event) => void;
};

type InfoSegment = {
  text: string;
  strong?: boolean;
};
type InfoContent = InfoSegment[][];
type TrackDownloadKind = 'original' | 'gpx';
type ToastService = {
  add: (message: { severity: string; summary: string; detail?: string; life?: number }) => void;
};

const GPS_INDEX_NAME = 'GPS';
const GPX_FILE_EXTENSION = '.gpx';
const UNAVAILABLE_INDEXER_STATUSES = new Set(['REMOVED', 'EXCLUDED']);
const COPY_STATUS_RESET_MS = 1800;

defineOptions({
  name: 'TrackDetailOverview',
});

const props = withDefaults(
  defineProps<{
    gpsTrack?: GpsTrack | null;
    trackDetails: ChartPoint[];
  }>(),
  {
    gpsTrack: null,
  }
);

const gpsTrack = computed(() => props.gpsTrack);
const infoPopover = ref<TrackDetailInfoPopover | null>(null);
const summaryReady = ref(false);
const activeTooltip = ref<string | null>(null);
const currentInfoContent = ref<InfoContent>([]);
const showExplorationInfo = ref(false);
const activeDownload = ref<TrackDownloadKind | null>(null);
const trackIdCopied = ref(false);
const trackIdCopyError = ref('');
const toast = inject<ToastService>('toast', { add: () => undefined });
let trackIdCopyResetTimer: number | null = null;

function infoText(text: string): InfoContent {
  return [[{ text }]];
}

const INFO_ENERGY = infoText(
  'Estimated external mechanical work from GPS-derived physics: climbing, drag, rolling/friction, and acceleration. It is not metabolic calorie burn and not measured power-sensor data.'
);
const INFO_AVG_POWER = infoText(
  'Estimated average external mechanical power from the same energy model. Treat it as a physics estimate, not as a recorded power-meter value.'
);
const INFO_AVG_MOVING_POWER = infoText(
  'Estimated external mechanical power while moving, excluding detected stops. It is model-derived from GPS and elevation data.'
);
const INFO_MAX_POWER = infoText(
  'Highest trailing 30 s average of estimated external mechanical power. This is more stable than raw per-segment GPS-derived power spikes.'
);
const INFO_NORMALIZED_POWER: InfoContent = [
  [
    {
      text: 'Normalized Power (NP): a variability-weighted value computed from estimated mechanical power over a 30 s rolling window. It is useful for comparing effort patterns, but it is not a power-meter measurement.',
    },
  ],
  [
    { text: 'Also known as: ' },
    { text: 'Weighted Average Power', strong: true },
    { text: ' (Strava), ' },
    { text: 'Normalized Power / NP', strong: true },
    { text: ' (Garmin, TrainingPeaks), ' },
    { text: 'xPower / IsoPower', strong: true },
    { text: ' (GoldenCheetah).' },
  ],
];

const trackDuration = computed(() => {
  if (props.gpsTrack?.startDate && props.gpsTrack?.endDate) {
    return new Date(props.gpsTrack.endDate).getTime() - new Date(props.gpsTrack.startDate).getTime();
  }
  return 0;
});

/**
 * Moving time in ms, sourced from the server's {@code trackDurationInMotionSecs}
 * (computed once at ingest from the outlier-cleaned point stream). The client
 * must not recompute this from simplified track variants — those drop points
 * and lack per-point speeds, which used to produce wildly wrong totals.
 */
const movingTimeMs = computed(() => {
  const secs = props.gpsTrack?.trackDurationInMotionSecs;
  return secs != null ? secs * 1000 : 0;
});

/**
 * Detected-stopped time in ms: contiguous runs of ≥ 30 s under 0.5 km/h,
 * again sourced from the server ({@code trackDurationStoppedSecs}). This
 * excludes "gap" time where GPS simply stopped recording, which is why
 * moving + stopped can be less than trackDuration.
 */
const stoppedTimeMs = computed(() => {
  const secs = props.gpsTrack?.trackDurationStoppedSecs;
  return secs != null ? secs * 1000 : 0;
});

const hasEnergy = computed(() => props.gpsTrack?.energyNetTotalWh != null && props.gpsTrack.energyNetTotalWh !== 0);

const peakPowerWatts = computed(() => props.gpsTrack?.powerWatts30sMax ?? 0);
const totalAscent = computed(() => props.gpsTrack?.ascentInMeter ?? 0);
const totalDescent = computed(() => props.gpsTrack?.descentInMeter ?? 0);
const minAltitude = computed(() => props.gpsTrack?.minAltitude ?? 0);
const maxAltitude = computed(() => props.gpsTrack?.maxAltitude ?? 0);

const avgSpeed = computed(() => {
  const dist = props.gpsTrack?.trackLengthInMeter;
  const seconds = trackDuration.value / 1000;
  if (dist != null && seconds > 0) {
    return (dist / seconds) * 3.6;
  }
  return 0;
});

const maxSpeed = computed(() => props.gpsTrack?.speedInKmh30sMax ?? 0);
const maxElevationGainRate = computed(() => props.gpsTrack?.elevationGainPerHour30sMax ?? 0);
const maxElevationLossRate = computed(() => props.gpsTrack?.elevationLossPerHour30sMax ?? 0);
const maxSlope = computed(() => props.gpsTrack?.slopePercentageMax ?? 0);
const minSlope = computed(() => props.gpsTrack?.slopePercentageMin ?? 0);

const trackDisplayName = computed(
  () =>
    props.gpsTrack?.trackName ||
    props.gpsTrack?.metaName ||
    props.gpsTrack?.indexedFile?.name ||
    `Track #${props.gpsTrack?.id}`
);

const trackDescription = computed(() => props.gpsTrack?.trackDescription || props.gpsTrack?.metaDescription || '');

const trackIdText = computed(() => (props.gpsTrack?.id == null ? '' : String(props.gpsTrack.id)));

const sourceFileName = computed(() => props.gpsTrack?.indexedFile?.name || trackDisplayName.value || 'track-source');

const sourceFileIsGpx = computed(() => sourceFileName.value.toLowerCase().endsWith(GPX_FILE_EXTENSION));

const canDownloadTrackSource = computed(() => {
  const track = props.gpsTrack;
  const indexedFile = track?.indexedFile;
  if (!track?.id || !indexedFile) return false;
  if (indexedFile.index !== GPS_INDEX_NAME) return false;
  return !UNAVAILABLE_INDEXER_STATUSES.has(indexedFile.indexerStatus ?? '');
});

const canDownloadGpx = computed(() => canDownloadTrackSource.value && !sourceFileIsGpx.value);

const explorationIsCalculated = computed(
  () => props.gpsTrack?.explorationStatus === 'CALCULATED' && props.gpsTrack?.explorationScore != null
);

const explorationIsPending = computed(() =>
  ['SCHEDULED', 'IN_PROGRESS', 'NEEDS_RECALCULATION'].includes(props.gpsTrack?.explorationStatus ?? '')
);

const movingAvgSpeed = computed(() => {
  const dist = props.gpsTrack?.trackLengthInMeter;
  const secs = props.gpsTrack?.trackDurationInMotionSecs;
  if (dist != null && secs != null && secs > 0) {
    return (dist / secs) * 3.6; // m/s -> km/h
  }
  return 0;
});

const longestStopMs = computed(() => {
  const secs = props.gpsTrack?.trackLongestStopSecs;
  return secs != null ? secs * 1000 : 0;
});

onMounted(() => {
  if (props.trackDetails.length > 0) {
    computeSummary(props.trackDetails);
  }
});

onBeforeUnmount(() => {
  if (trackIdCopyResetTimer != null) {
    window.clearTimeout(trackIdCopyResetTimer);
  }
});

watch(
  () => props.trackDetails,
  (details) => {
    if (details.length > 0) {
      computeSummary(details);
    }
  }
);

function whToJoules(wh: number | null | undefined): string {
  return formatNumber((wh ?? 0) * 3600, 0);
}

function formatOptionalDistanceSmart(meters: number | null | undefined): string {
  return meters == null ? '—' : formatDistanceSmart(meters);
}

function formatOptionalDistanceTooltip(meters: number | null | undefined): string {
  return meters == null ? '—' : formatDistanceTooltip(meters);
}

function whToKcal(wh: number | null | undefined): string {
  return formatNumber((wh ?? 0) * 0.8604, 0);
}

function wattsToKcalH(w: number | null | undefined, decimals = 1): string {
  return formatNumber((w ?? 0) * 0.8604, decimals);
}

async function downloadOriginal(): Promise<void> {
  await runTrackDownload('original');
}

async function downloadGpx(): Promise<void> {
  await runTrackDownload('gpx');
}

async function copyTrackId(): Promise<void> {
  const id = trackIdText.value;
  if (!id) return;

  try {
    trackIdCopyError.value = '';
    await writeTextToClipboard(id);
    trackIdCopied.value = true;
    if (trackIdCopyResetTimer != null) {
      window.clearTimeout(trackIdCopyResetTimer);
    }
    trackIdCopyResetTimer = window.setTimeout(() => {
      trackIdCopied.value = false;
      trackIdCopyResetTimer = null;
    }, COPY_STATUS_RESET_MS);
  } catch (copyError) {
    console.warn('[track-details] failed to copy TrackID', copyError);
    trackIdCopyError.value = 'Could not copy TrackID.';
  }
}

async function writeTextToClipboard(text: string): Promise<void> {
  let clipboardError: unknown = null;
  if (navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(text);
      return;
    } catch (error) {
      clipboardError = error;
    }
  }

  try {
    copyTextWithFallback(text);
  } catch (fallbackError) {
    throw clipboardError ?? fallbackError;
  }
}

function selectCopyField(event: Event): void {
  const target = event.target as HTMLInputElement | null;
  target?.select?.();
}

function copyTextWithFallback(text: string): void {
  const textArea = document.createElement('textarea');
  textArea.value = text;
  textArea.setAttribute('readonly', '');
  textArea.style.position = 'fixed';
  textArea.style.opacity = '0';
  document.body.appendChild(textArea);
  textArea.select();
  const copied = document.execCommand('copy');
  document.body.removeChild(textArea);
  if (!copied) {
    throw new Error('Browser copy command failed');
  }
}

async function runTrackDownload(kind: TrackDownloadKind): Promise<void> {
  const trackId = props.gpsTrack?.id;
  if (trackId == null || activeDownload.value !== null) return;
  activeDownload.value = kind;
  try {
    if (kind === 'original') {
      await downloadTrackSourceFile(trackId, sourceFileName.value);
    } else {
      await downloadTrackGpxFile(trackId, sourceFileName.value);
    }
  } catch (error) {
    console.warn('[track-details] failed to download track file', { trackId, kind, error });
    toast.add({
      severity: 'error',
      summary: 'Download failed',
      detail: kind === 'original' ? 'Could not download the original indexed file.' : 'Could not download GPX.',
      life: 4000,
    });
  } finally {
    activeDownload.value = null;
  }
}

function toggleTooltip(id: string) {
  activeTooltip.value = activeTooltip.value === id ? null : id;
}

function showInfo(event: Event, content: InfoContent) {
  currentInfoContent.value = content;
  infoPopover.value?.toggle(event);
}

function computeSummary(details: ChartPoint[]) {
  if (!details || details.length === 0) return;
  summaryReady.value = true;
}
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
  font-size: var(--text-lg-size);
  font-weight: 700;
  color: var(--text-primary);
  flex: 1 1 auto;
  line-height: var(--text-lg-lh);
  word-break: break-word;
}

.track-header__actions {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
}

.track-header__action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2rem;
  height: 2rem;
  border: 1px solid var(--border-default);
  border-radius: 8px;
  background: var(--surface-elevated);
  color: var(--text-secondary);
  cursor: pointer;
  transition:
    background-color 0.15s ease,
    border-color 0.15s ease,
    color 0.15s ease;
}

.track-header__action-btn:hover:not(:disabled) {
  background: var(--surface-hover);
  border-color: var(--accent);
  color: var(--accent);
}

.track-header__action-btn:disabled {
  cursor: progress;
  opacity: 0.6;
}

.track-header__action-btn i {
  font-size: var(--text-sm-size);
  line-height: 1;
}

.track-header__meta {
  display: flex;
  align-items: baseline;
  gap: 0.75rem;
  margin-top: 0.35rem;
  font-size: var(--text-sm-size);
  color: var(--text-muted);
  flex-wrap: wrap;
}

.track-header__meta i {
  font-size: var(--text-xs-size);
  margin-right: 0.25rem;
  opacity: 0.7;
}

.track-header__desc {
  font-size: var(--text-sm-size);
  color: var(--text-muted);
  font-style: italic;
  margin-top: 0.3rem;
  line-height: var(--text-sm-lh);
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
  font-size: var(--text-2xs-size);
  font-weight: 600;
  letter-spacing: 0.07em;
  text-transform: uppercase;
  color: var(--text-faint);
  padding: 0.75rem 1rem 0.35rem;
}

.section-label i {
  font-size: var(--text-xs-size);
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
  font-size: var(--text-lg-size);
  color: var(--text-muted);
  margin-bottom: 0.3rem;
}

.metric-tile__icon--sm {
  font-size: var(--text-base-size);
  margin-bottom: 0.2rem;
}

.metric-tile__value {
  font-size: var(--text-xl-size);
  font-weight: 700;
  color: var(--text-primary);
  line-height: var(--text-xl-lh);
  letter-spacing: -0.01em;
}

.metric-tile__value--sm {
  font-size: var(--text-base-size);
  font-weight: 600;
}

.metric-tile__unit {
  font-size: var(--text-sm-size);
  font-weight: 500;
  color: var(--text-muted);
}

.metric-tile__label {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 0.18rem;
  max-width: 100%;
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
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
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
  display: flex;
  align-items: center;
  transition: color 0.15s;
}

.exploration-info-btn:hover,
.exploration-info-btn--active {
  color: var(--accent);
}

.info-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  appearance: none;
  background: none;
  border: none;
  padding: 0;
  cursor: pointer;
  color: var(--text-faint);
  line-height: 1;
  transition: color 0.15s;
}

.info-btn:hover,
.info-btn:focus-visible {
  color: var(--accent);
  outline: none;
}

.info-btn--inline {
  width: 1rem;
  height: 1rem;
  flex: 0 0 1rem;
  font-size: var(--text-xs-size);
  margin-left: 0;
  vertical-align: middle;
}

.info-btn--inline i {
  display: block;
  line-height: 1;
}

@media (max-width: 640px) {
  .metrics-grid .metric-tile .metric-tile__label .info-btn--inline {
    position: absolute;
    top: 0.35rem;
    right: 0.35rem;
    width: 1.25rem;
    height: 1.25rem;
    flex-basis: 1.25rem;
    border-radius: 999px;
  }
}

.track-detail-info-text {
  max-width: min(280px, calc(100vw - 2rem));
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
  color: var(--text-secondary);
  margin: 0;
  padding: 0.1rem 0;
}

.track-detail-info-text p {
  margin: 0;
}

.track-detail-info-text p + p {
  margin-top: 0.65rem;
}

.track-detail-info-text strong {
  color: var(--text-primary);
}

.exploration-pending,
.exploration-unavailable {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin: 0.4rem 1rem 0.6rem;
  font-size: var(--text-sm-size);
  color: var(--text-secondary);
}

.exploration-pending__spinner {
  font-size: var(--text-sm-size);
  color: var(--accent);
}

.exploration-unavailable__icon {
  font-size: var(--text-sm-size);
  opacity: 0.5;
}

.exploration-info-panel {
  margin: 0.4rem 1rem 0.6rem;
  padding: 0.65rem 0.85rem;
  border-radius: 0.5rem;
  background: var(--accent-bg);
  border: 1px solid var(--accent-subtle);
  font-size: var(--text-sm-size);
  color: var(--text-secondary);
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
  line-height: var(--text-sm-lh);
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
  font-size: var(--text-xs-size);
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
  font-size: var(--text-2xs-size);
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
  background: linear-gradient(
    90deg,
    var(--surface-elevated) 25%,
    var(--surface-hover) 50%,
    var(--surface-elevated) 75%
  );
  background-size: 200% 100%;
  animation: shimmer 1.4s infinite;
}

@keyframes shimmer {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
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
  font-size: var(--text-xs-size);
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--text-muted);
  cursor: pointer;
  user-select: none;
  list-style: none;
  background: var(--surface-elevated);
}

.info-drawer__summary::-webkit-details-marker {
  display: none;
}

.info-drawer__summary i:first-child {
  font-size: var(--text-sm-size);
}

.info-drawer__summary .info-drawer__chevron {
  margin-left: auto;
  font-size: var(--text-xs-size);
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
  font-size: var(--text-sm-size);
  border-top: 1px solid var(--border-subtle);
}

.info-key {
  flex: 0 0 7rem;
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.info-val {
  color: var(--text-secondary);
  word-break: break-word;
  min-width: 0;
}

.info-val--copy {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 0.45rem;
  align-items: center;
  flex: 1 1 auto;
  -webkit-user-select: text;
  user-select: text;
}

.info-copy-field {
  min-width: 0;
  width: 100%;
  padding: 0.42rem 0.55rem;
  border: 1px solid var(--border-default);
  border-radius: 6px;
  background: var(--code-bg);
  color: var(--code-text);
  font-family: ui-monospace, SFMono-Regular, 'SF Mono', Menlo, monospace;
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
  cursor: text;
  -webkit-user-select: text;
  user-select: text;
}

.info-copy-field:focus {
  outline: 2px solid color-mix(in srgb, var(--accent) 52%, transparent);
  outline-offset: 1px;
}

.info-copy-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2rem;
  height: 2rem;
  border: 1px solid var(--border-default);
  border-radius: 6px;
  background: var(--surface-elevated);
  color: var(--text-secondary);
  cursor: pointer;
  transition:
    background-color 0.15s ease,
    border-color 0.15s ease,
    color 0.15s ease;
}

.info-copy-btn:hover:not(:disabled),
.info-copy-btn:focus-visible {
  background: var(--surface-hover);
  border-color: var(--accent);
  color: var(--accent);
}

.info-copy-btn:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.info-copy-btn i {
  font-size: var(--text-sm-size);
  line-height: 1;
}

.info-row--error {
  padding-top: 0;
  border-top: 0;
}

.info-copy-error {
  color: var(--error);
  font-size: var(--text-xs-size);
}

.info-val--mono {
  font-family: ui-monospace, SFMono-Regular, 'SF Mono', Menlo, monospace;
  font-size: var(--text-xs-size);
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
