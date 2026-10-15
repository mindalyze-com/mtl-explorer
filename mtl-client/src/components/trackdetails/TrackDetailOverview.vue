<template>
  <div v-if="gpsTrack && gpsTrack.indexedFile" class="overview-container">
    <TrackDescription
      v-if="trackDescription"
      :key="gpsTrack.id"
      :text="trackDescription"
      data-test="track-description"
    />

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
          data-test="track-distance-value"
          class="metric-tile__value"
        >
          {{ formatOptionalDistanceSmart(gpsTrack.trackLengthInMeter) }}
        </div>
        <div class="metric-tile__label">Distance</div>
      </div>
      <div
        v-if="!gpsTrack.timingUnreliable && gpsTrack.startDate && gpsTrack.endDate && trackDuration >= 0"
        class="metric-tile metric-tile--primary"
      >
        <i class="bi bi-clock metric-tile__icon"></i>
        <div
          v-tooltip.top="{ value: formatDurationTooltip(trackDuration), showDelay: 400 }"
          data-test="track-duration-value"
          class="metric-tile__value"
        >
          {{ gpsTrack.timingUnreliable ? '—' : formatDurationSmart(trackDuration) }}
        </div>
        <div class="metric-tile__label">Duration</div>
      </div>
      <div v-if="totalAscent != null" class="metric-tile metric-tile--primary">
        <i class="bi bi-arrow-up metric-tile__icon" style="color: var(--success)"></i>
        <div class="metric-tile__value">{{ formatOptionalElevation(totalAscent) }}</div>
        <div class="metric-tile__label">Ascent</div>
      </div>
      <div
        v-if="!gpsTrack.timingUnreliable && gpsTrack.trackLengthInMeter != null && trackDuration > 0"
        class="metric-tile metric-tile--primary"
      >
        <i class="bi bi-speedometer2 metric-tile__icon"></i>
        <div class="metric-tile__value">
          {{ gpsTrack.timingUnreliable ? '—' : formatSpeed(avgSpeed, 1) }}
        </div>
        <div class="metric-tile__label">Overall avg speed</div>
      </div>
    </div>

    <div v-if="summaryReady" class="overview-layout">
      <!-- Secondary Metrics -->
      <div class="overview-sections">
        <section v-if="!gpsTrack.timingUnreliable" class="overview-section overview-section--timing">
          <!-- Timing row -->
          <h3 class="section-label"><i class="bi bi-stopwatch"></i> Timing</h3>
          <div class="metrics-grid">
            <div class="metric-tile">
              <i class="bi bi-play-fill metric-tile__icon metric-tile__icon--sm"></i>
              <div
                v-tooltip.top="{ value: formatDurationTooltip(movingTimeMs), showDelay: 400 }"
                class="metric-tile__value metric-tile__value--sm"
              >
                {{
                  gpsTrack.trackDurationInMotionSecs == null ? '—' : formatDurationSmart(movingTimeMs, trackDuration)
                }}
              </div>
              <div class="metric-tile__label">Moving</div>
            </div>
            <div class="metric-tile">
              <i class="bi bi-pause-fill metric-tile__icon metric-tile__icon--sm"></i>
              <div
                v-tooltip.top="{ value: formatDurationTooltip(stoppedTimeMs), showDelay: 400 }"
                class="metric-tile__value metric-tile__value--sm"
              >
                {{
                  gpsTrack.trackDurationStoppedSecs == null ? '—' : formatDurationSmart(stoppedTimeMs, trackDuration)
                }}
              </div>
              <div class="metric-tile__label">Stopped</div>
            </div>
            <div v-if="(gpsTrack.trackDurationUnknownSecs ?? 0) > 0" class="metric-tile">
              <i class="bi bi-question-circle metric-tile__icon metric-tile__icon--sm"></i>
              <div class="metric-tile__value metric-tile__value--sm">
                {{ formatDurationSmart(gpsTrack.trackDurationUnknownSecs! * 1000, trackDuration) }}
              </div>
              <div class="metric-tile__label">Movement unknown</div>
            </div>
            <div v-if="gpsTrack.trackStopCount != null" class="metric-tile">
              <i class="bi bi-stop-circle metric-tile__icon metric-tile__icon--sm"></i>
              <div class="metric-tile__value metric-tile__value--sm">{{ gpsTrack.trackStopCount }}</div>
              <div class="metric-tile__label">Breaks ≥ 1 min</div>
            </div>
            <div v-if="longestStopMs > 0" class="metric-tile">
              <i class="bi bi-hourglass-split metric-tile__icon metric-tile__icon--sm"></i>
              <div
                v-tooltip.top="{ value: formatDurationTooltip(longestStopMs), showDelay: 400 }"
                class="metric-tile__value metric-tile__value--sm"
              >
                {{ formatDurationSmart(longestStopMs, stoppedTimeMs) }}
              </div>
              <div class="metric-tile__label">Longest stop</div>
            </div>
          </div>

          <details
            class="info-drawer overview-disclosure"
            :open="!!gpsTrack.motionAnalysisStatus && gpsTrack.motionAnalysisStatus !== 'COMPLETE'"
          >
            <summary class="info-drawer__summary">
              How pauses are counted<i class="bi bi-chevron-down info-drawer__chevron" aria-hidden="true"></i>
            </summary>
            <p class="motion-explanation">
              <template v-if="gpsTrack.motionAnalysisStatus && gpsTrack.motionAnalysisStatus !== 'COMPLETE'">
                Moving and stopped time are unavailable because timestamps are missing or wrong.
              </template>
              <template v-else>
                Stopped time includes short pauses. Breaks show pauses of at least one minute.
                <span v-if="(gpsTrack.trackDurationShortStoppedSecs ?? 0) > 0">
                  Short pauses: {{ formatDurationSmart(gpsTrack.trackDurationShortStoppedSecs! * 1000) }}.
                </span>
                <span v-if="(gpsTrack.trackDurationUnknownSecs ?? 0) > 0">
                  Unknown time has insufficient evidence for movement or a pause.
                </span>
              </template>
            </p>
          </details>
        </section>
        <section v-if="!gpsTrack.timingUnreliable && (movingAvgSpeed > 0 || maxSpeed > 0)" class="overview-section">
          <!-- Speed row -->
          <h3 class="section-label"><i class="bi bi-speedometer2"></i> Speed</h3>
          <div class="metrics-grid">
            <div v-if="movingAvgSpeed > 0" class="metric-tile">
              <i class="bi bi-speedometer2 metric-tile__icon metric-tile__icon--sm" style="color: var(--success)"></i>
              <div class="metric-tile__value metric-tile__value--sm">
                {{ formatSpeed(movingAvgSpeed, 1) }}
              </div>
              <div class="metric-tile__label">Moving average</div>
            </div>
            <div v-if="maxSpeed > 0" class="metric-tile">
              <i class="bi bi-lightning metric-tile__icon metric-tile__icon--sm" style="color: var(--warning)"></i>
              <div class="metric-tile__value metric-tile__value--sm">
                {{ formatSpeed(maxSpeed, 1) }}
              </div>
              <div class="metric-tile__label">Max 30s speed</div>
            </div>
          </div>
          <p class="section-note">Moving average excludes stops; overall average includes them.</p>
        </section>
        <section class="overview-section overview-section--elevation">
          <!-- Altitude row -->
          <h3 class="section-label"><i class="bi bi-triangle"></i> Elevation</h3>
          <p v-if="!hasElevation" class="elevation-explanation">Elevation data is unavailable for this track.</p>
          <div v-if="hasElevation" class="metrics-grid">
            <div class="metric-tile">
              <i class="bi bi-arrow-up metric-tile__icon metric-tile__icon--sm" style="color: var(--success)"></i>
              <div class="metric-tile__value metric-tile__value--sm">{{ formatOptionalElevation(totalAscent) }}</div>
              <div class="metric-tile__label">Ascent</div>
            </div>
            <div class="metric-tile">
              <div class="metric-tile__value metric-tile__value--sm">{{ formatOptionalElevation(minAltitude) }}</div>
              <div class="metric-tile__label">Lowest point</div>
            </div>
            <div class="metric-tile">
              <div class="metric-tile__value metric-tile__value--sm">{{ formatOptionalElevation(maxAltitude) }}</div>
              <div class="metric-tile__label">Highest point</div>
            </div>
          </div>
          <details v-if="hasElevation" class="info-drawer overview-disclosure">
            <summary class="info-drawer__summary">
              Elevation details
              <i class="bi bi-chevron-down info-drawer__chevron" aria-hidden="true"></i>
            </summary>
            <div class="metrics-grid">
              <div class="metric-tile">
                <i class="bi bi-arrow-down metric-tile__icon metric-tile__icon--sm" style="color: var(--warning)"></i>
                <div class="metric-tile__value metric-tile__value--sm">{{ formatOptionalElevation(totalDescent) }}</div>
                <div class="metric-tile__label">Descent</div>
              </div>
              <div v-if="maxElevationGainRate > 0" class="metric-tile">
                <i
                  class="bi bi-arrow-up-right metric-tile__icon metric-tile__icon--sm"
                  style="color: var(--success)"
                ></i>
                <div class="metric-tile__value metric-tile__value--sm">
                  {{ formatVerticalRate(maxElevationGainRate) }}
                </div>
                <div class="metric-tile__label">Max 30s climb</div>
              </div>
              <div v-if="maxElevationLossRate > 0" class="metric-tile">
                <i
                  class="bi bi-arrow-down-right metric-tile__icon metric-tile__icon--sm"
                  style="color: var(--warning)"
                ></i>
                <div class="metric-tile__value metric-tile__value--sm">
                  {{ formatVerticalRate(maxElevationLossRate) }}
                </div>
                <div class="metric-tile__label">Max 30s descent</div>
              </div>
              <div class="metric-tile">
                <div class="metric-tile__value metric-tile__value--sm">
                  {{ formatOptionalElevation(altitudeRange) }}
                </div>
                <div class="metric-tile__label">Range</div>
              </div>
              <div class="metric-tile">
                <div class="metric-tile__value metric-tile__value--sm">{{ formatOptionalSlope(maxSlope) }}</div>
                <div class="metric-tile__label">Steepest climb</div>
              </div>
              <div class="metric-tile">
                <div class="metric-tile__value metric-tile__value--sm">{{ formatOptionalSlope(minSlope) }}</div>
                <div class="metric-tile__label">Steepest descent</div>
              </div>
            </div>
          </details>
        </section>
      </div>

      <div class="overview-insights">
        <!-- Exploration Score -->
        <div class="overview-section exploration-section">
          <h3 class="section-label">
            <i class="bi bi-compass"></i> Exploration
            <button
              v-tooltip.top="{
                value: 'How much of this track covered new ground vs. routes taken before. Tap for details.',
                showDelay: 300,
              }"
              class="exploration-info-btn"
              :class="{ 'exploration-info-btn--active': showExplorationInfo }"
              type="button"
              :aria-expanded="showExplorationInfo"
              aria-label="Exploration score explanation"
              @click="showExplorationInfo = !showExplorationInfo"
            >
              <i class="bi bi-info-circle"></i>
            </button>
          </h3>

          <!-- Calculated: show metric tiles -->
          <div v-if="explorationIsCalculated" class="metrics-grid">
            <div class="metric-tile">
              <i class="bi bi-map metric-tile__icon metric-tile__icon--sm" style="color: var(--accent)"></i>
              <div class="metric-tile__value metric-tile__value--sm">
                {{ formatNumber((gpsTrack.explorationScore ?? 0) * 100, 1) }}%
              </div>
              <div
                v-tooltip.top="{
                  value: `Share of this track that was further than ${formatDistance(EXPLORATION_CORRIDOR_WIDTH_M, 0)} from any track recorded before it. Reflects exploration at the time of recording — tracks added later that cover the same area are not taken into account.`,
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
                v-tooltip.top="{
                  value: `Distance on segments you have covered before (within ${formatDistance(EXPLORATION_CORRIDOR_WIDTH_M, 0)})`,
                  showDelay: 300,
                }"
                class="metric-tile__label"
              >
                Known Distance
              </div>
            </div>
          </div>

          <meter
            v-if="explorationIsCalculated"
            class="exploration-meter"
            min="0"
            max="1"
            :value="gpsTrack.explorationScore ?? 0"
            aria-label="Share of new territory"
          ></meter>

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
                you've passed within <strong>{{ formatDistance(EXPLORATION_CORRIDOR_WIDTH_M, 0) }}</strong> of it on a
                previous activity.</span
              >
            </div>
            <div class="exploration-info-panel__legend">
              <span class="exploration-legend-dot exploration-legend-dot--new"></span>
              <span
                ><strong>New Territory</strong> — ground never covered before (or not within
                {{ formatDistance(EXPLORATION_CORRIDOR_WIDTH_M, 0) }})</span
              >
            </div>
            <div class="exploration-info-panel__legend">
              <span class="exploration-legend-dot exploration-legend-dot--known"></span>
              <span><strong>Known Distance</strong> — route you've already covered on a prior activity</span>
            </div>
          </div>
        </div>

        <!-- Fitness Section -->
        <div
          v-if="summaryReady && !gpsTrack.timingUnreliable && trackHealth?.available"
          class="overview-section health-section"
          data-test="track-health-summary"
        >
          <div class="health-section__heading">
            <h3 class="section-label">
              <i class="bi bi-heart-pulse"></i> Heart rate
              <button
                v-if="trackHealth?.available"
                type="button"
                class="info-btn info-btn--inline"
                aria-label="About heart rate statistics"
                @click.stop="showInfo($event, INFO_HEART_RATE)"
              >
                <i class="bi bi-info-circle"></i>
              </button>
            </h3>
            <p v-if="syntheticHealthData" class="track-data-note">Synthetic demo heart rate</p>
          </div>
          <div class="metrics-grid">
            <div v-if="trackHealth?.heartRate?.averageBpm != null" class="metric-tile metric-tile--health">
              <i class="bi bi-heart-pulse metric-tile__icon metric-tile__icon--sm"></i>
              <div class="metric-tile__value metric-tile__value--sm">
                {{ formatNumber(trackHealth.heartRate.averageBpm, 0) }} bpm
              </div>
              <div class="metric-tile__label">Average heart rate</div>
            </div>
            <div v-if="trackHealth?.heartRate" class="metric-tile metric-tile--health" data-test="moving-heart-rate">
              <i class="bi bi-heart-pulse metric-tile__icon metric-tile__icon--sm"></i>
              <div class="metric-tile__value metric-tile__value--sm">
                {{
                  trackHealth.heartRate.movingAverageBpm != null
                    ? `${formatNumber(trackHealth.heartRate.movingAverageBpm, 0)} bpm`
                    : '—'
                }}
              </div>
              <div class="metric-tile__label">Average while moving</div>
              <div v-if="trackHealth.heartRate.movingAverageBpm == null" class="metric-tile__sub">
                No matching moving samples
              </div>
            </div>
            <div v-if="trackHealth?.heartRate?.maximumBpm != null" class="metric-tile metric-tile--health">
              <i class="bi bi-arrow-up-circle metric-tile__icon metric-tile__icon--sm"></i>
              <div class="metric-tile__value metric-tile__value--sm">
                {{ formatNumber(trackHealth.heartRate.maximumBpm, 0) }} bpm
              </div>
              <div class="metric-tile__label">Maximum heart rate</div>
            </div>
          </div>
          <details class="info-drawer overview-disclosure">
            <summary class="info-drawer__summary">
              Heart rate details<i class="bi bi-chevron-down info-drawer__chevron" aria-hidden="true"></i>
            </summary>
            <div class="metrics-grid">
              <div v-if="trackHealth?.heartRate?.minimumBpm != null" class="metric-tile metric-tile--health">
                <i class="bi bi-arrow-down-circle metric-tile__icon metric-tile__icon--sm"></i>
                <div class="metric-tile__value metric-tile__value--sm">
                  {{ formatNumber(trackHealth.heartRate.minimumBpm, 0) }} bpm
                </div>
                <div class="metric-tile__label">Minimum heart rate</div>
              </div>
              <div v-if="trackHealth?.heartRate?.sampleCount != null" class="metric-tile metric-tile--health">
                <i class="bi bi-activity metric-tile__icon metric-tile__icon--sm"></i>
                <div class="metric-tile__value metric-tile__value--sm">
                  {{ formatNumber(trackHealth.heartRate.sampleCount, 0) }}
                </div>
                <div class="metric-tile__label">Heart rate samples</div>
              </div>
            </div>
            <p v-if="trackHealth?.available" class="health-attribution">
              {{
                syntheticHealthData
                  ? 'Synthetic samples from the shared demo timeline.'
                  : 'Google Health samples recorded during this track.'
              }}
              Minimum, maximum and overall average include breaks.
            </p>
          </details>
        </div>

        <!-- Energy Section -->
        <p v-if="summaryReady && gpsTrack.timingUnreliable">
          This file's timestamps can't be used. Duration, speed, power, calories, replay, and heart rate are
          unavailable.
        </p>
        <div
          v-if="summaryReady && !gpsTrack.timingUnreliable"
          class="overview-section energy-section"
          data-test="energy-section"
        >
          <h3 class="section-label">
            <i class="bi bi-lightning-charge"></i> Energy
            <button
              type="button"
              class="info-btn info-btn--inline"
              aria-label="About energy"
              @click.stop="showInfo($event, INFO_ENERGY)"
            >
              <i class="bi bi-info-circle"></i>
            </button>
          </h3>

          <div
            v-if="energyCalculationMessage || !hasEnergy"
            class="exploration-pending"
            data-test="energy-calculation-status"
          >
            <i v-if="energyCalculationPending" class="pi pi-spin pi-spinner exploration-pending__spinner"></i>
            <span>{{ energyCalculationMessage || 'Checking energy calculation status…' }}</span>
          </div>

          <div v-if="hasEnergy" class="energy-summary" data-test="energy-summary">
            <div class="metric-tile">
              <div class="energy-value-row">
                <strong
                  v-if="gpsTrack.metabolicEnergy?.activeKcal != null"
                  class="metric-tile__value metric-tile__value--sm"
                  data-test="active-calories"
                >
                  {{ formatNumber(gpsTrack.metabolicEnergy.activeKcal, 0) }} kcal
                </strong>
                <strong v-else class="energy-summary__status" data-test="calorie-unavailable">
                  {{ energyCalculationPending ? 'Pending' : 'Unavailable' }}
                </strong>
                <span v-if="gpsTrack.metabolicEnergy?.coverage === 'PARTIAL'" class="coverage-badge">Partial</span>
              </div>
              <span class="metric-tile__label">Estimated active calories</span>
            </div>
            <div class="metric-tile">
              <strong class="metric-tile__value metric-tile__value--sm" data-test="mechanical-work"
                >{{ formatNumber(gpsTrack.energyNetTotalWh, 1) }} Wh</strong
              >
              <span class="metric-tile__label">{{ mechanicalWorkLabel }}</span>
            </div>
          </div>
          <p
            v-if="hasEnergy && gpsTrack.metabolicEnergy?.coverage === 'PARTIAL'"
            class="energy-coverage"
            data-test="calorie-partial"
          >
            Partial estimate · {{ formatDurationSmart((gpsTrack.metabolicEnergy.coveredSeconds ?? 0) * 1000) }} covered,
            {{ formatDurationSmart((gpsTrack.metabolicEnergy.uncoveredSeconds ?? 0) * 1000) }} excluded<span
              v-if="gpsTrack.metabolicEnergy.unknownDuration"
              >; additional duration unknown</span
            >
          </p>

          <details
            v-if="hasEnergy || gpsTrack.normalizedPowerWatts != null"
            :key="gpsTrack.id"
            class="info-drawer overview-disclosure energy-details"
            data-test="energy-details"
          >
            <summary class="info-drawer__summary">
              Energy details
              <i class="bi bi-chevron-down info-drawer__chevron" aria-hidden="true"></i>
            </summary>
            <div class="energy-details__content">
              <button
                type="button"
                class="energy-adjust-trigger"
                data-test="energy-adjust-trigger"
                aria-label="Adjust body weight"
                title="Adjust body weight"
                @click.stop="openEnergyAdjustDialog"
              >
                <i class="bi bi-pencil-square" aria-hidden="true"></i>
                Adjust body weight
              </button>
              <MetabolicEnergyDetails :energy="gpsTrack.metabolicEnergy" :pending="energyCalculationPending" />

              <div class="metrics-grid">
                <div v-if="gpsTrack.normalizedPowerWatts" class="metric-tile metric-tile--energy">
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

                <div
                  v-if="hasEnergy"
                  class="metric-tile metric-tile--energy energy-tooltip-wrapper"
                  @click="toggleTooltip('avgPower')"
                >
                  <i class="bi bi-lightning metric-tile__icon metric-tile__icon--sm" style="color: var(--accent)"></i>
                  <div class="metric-tile__value metric-tile__value--sm">
                    {{ formatNumber(gpsTrack.powerWattsAvg, 0) }} W
                  </div>
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
                    Average estimated mechanical power
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
                  <div
                    class="energy-tooltip"
                    :class="{ 'energy-tooltip--visible': activeTooltip === 'avgMovingPower' }"
                  >
                    Average estimated mechanical power while moving
                  </div>
                </div>
                <div
                  v-if="gpsTrack.powerWatts30sMax != null"
                  class="metric-tile metric-tile--energy energy-tooltip-wrapper"
                  @click="toggleTooltip('maxPower')"
                >
                  <i
                    class="bi bi-lightning-fill metric-tile__icon metric-tile__icon--sm"
                    style="color: var(--accent)"
                  ></i>
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
                    Highest 30-second average of estimated mechanical power
                  </div>
                </div>
              </div>

              <!-- Detailed breakdown -->
              <details v-if="hasEnergy" class="info-drawer overview-disclosure energy-breakdown">
                <summary class="info-drawer__summary">
                  Mechanical breakdown
                  <i class="bi bi-chevron-down info-drawer__chevron" aria-hidden="true"></i>
                </summary>
                <p class="section-note">{{ whToJoules(gpsTrack.energyNetTotalWh) }} J of estimated mechanical work</p>
                <div class="metrics-grid">
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
              </details>
            </div>
          </details>
        </div>
      </div>
    </div>

    <!-- Source metadata -->
    <details class="info-drawer">
      <summary class="info-drawer__summary">
        <i class="bi bi-file-earmark-text"></i> Source details
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
              @focus="selectInputText"
              @click="selectInputText"
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
        <div class="info-row">
          <span class="info-key">Track Description</span><span class="info-val">{{ trackDescription || '—' }}</span>
        </div>
        <div v-if="gpsTrack.metaName" class="info-row">
          <span class="info-key">Meta Name</span><span class="info-val">{{ gpsTrack.metaName }}</span>
        </div>
        <div v-if="gpsTrack.activityTypeSourceDetails" class="info-row">
          <span class="info-key">Activity Source</span
          ><span class="info-val">{{ gpsTrack.activityTypeSourceDetails }}</span>
        </div>
        <div v-if="gpsTrack.creator" class="info-row">
          <span class="info-key">Creator</span><span class="info-val">{{ gpsTrack.creator }}</span>
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

    <Dialog
      v-model:visible="energyAdjustVisible"
      modal
      append-to="body"
      header="Adjust body weight"
      class="energy-adjust-dialog"
      :style="{ width: 'min(92vw, 32rem)' }"
    >
      <form class="energy-adjust" data-test="energy-adjust-dialog" @submit.prevent="saveEnergyRiderWeight">
        <div class="energy-adjust__baseline">
          <div>
            <span>Current body weight</span>
            <strong>{{
              energyWhatIfResult?.baselineRiderWeightKg != null
                ? formatMass(energyWhatIfResult.baselineRiderWeightKg, 1)
                : '—'
            }}</strong>
          </div>
          <div>
            <span>Mechanical model mass</span>
            <strong>{{
              energyWhatIfResult?.baselineWeightKgUsed != null
                ? formatMass(energyWhatIfResult.baselineWeightKgUsed, 1)
                : gpsTrack.energyWeightKgUsed != null
                  ? formatMass(gpsTrack.energyWeightKgUsed, 1)
                  : '—'
            }}</strong>
          </div>
        </div>

        <div class="energy-adjust__field">
          <div class="energy-adjust__label-row">
            <label for="energy-rider-weight">Body weight</label>
            <span>{{ formatMass(energyAdjustWeightKg, 1) }}</span>
          </div>

          <div class="energy-adjust__stepper">
            <button
              type="button"
              class="energy-adjust__step-btn"
              :disabled="energySaveLoading || energyAdjustWeightKg <= ENERGY_WEIGHT_MIN_KG"
              aria-label="Decrease body weight"
              @click="nudgeEnergyAdjustWeight(-ENERGY_WEIGHT_STEP_DISPLAY)"
            >
              <i class="bi bi-dash-lg"></i>
            </button>
            <div class="energy-adjust__input-wrap">
              <input
                id="energy-rider-weight"
                v-model.number="energyAdjustWeightDisplay"
                type="number"
                :min="energyWeightMinDisplay"
                :max="energyWeightMaxDisplay"
                :step="ENERGY_WEIGHT_STEP_DISPLAY"
                inputmode="decimal"
                class="energy-adjust__input"
                data-test="energy-rider-weight-input"
                :disabled="energySaveLoading"
                @input="queueEnergyWhatIf"
                @change="commitEnergyAdjustWeight()"
                @blur="commitEnergyAdjustWeight()"
              />
              <span>{{ energyWeightDisplayUnit }}</span>
            </div>
            <button
              type="button"
              class="energy-adjust__step-btn"
              :disabled="energySaveLoading || energyAdjustWeightKg >= ENERGY_WEIGHT_MAX_KG"
              aria-label="Increase body weight"
              @click="nudgeEnergyAdjustWeight(ENERGY_WEIGHT_STEP_DISPLAY)"
            >
              <i class="bi bi-plus-lg"></i>
            </button>
          </div>

          <input
            v-model.number="energyAdjustWeightDisplay"
            type="range"
            :min="energyWeightMinDisplay"
            :max="energyWeightMaxDisplay"
            :step="ENERGY_WEIGHT_STEP_DISPLAY"
            class="energy-adjust__range"
            aria-label="Body weight"
            :disabled="energySaveLoading"
            @input="queueEnergyWhatIf"
          />
          <div class="energy-adjust__scale" aria-hidden="true">
            <span>{{ formatMass(ENERGY_WEIGHT_MIN_KG, 0) }}</span>
            <span>{{ formatMass(ENERGY_WEIGHT_MAX_KG, 0) }}</span>
          </div>
        </div>

        <div v-if="energyWhatIfError" class="energy-adjust__error" data-test="energy-adjust-error">
          {{ energyWhatIfError }}
        </div>

        <div v-if="energyWhatIfLoading && !energyWhatIfResult" class="energy-adjust__loading">
          <i class="pi pi-spin pi-spinner"></i>
          <span>Calculating</span>
        </div>

        <div
          v-if="energyWhatIfResult"
          class="energy-adjust__results"
          :class="{ 'energy-adjust__results--updating': energyWhatIfLoading }"
          data-test="energy-adjust-results"
        >
          <div class="energy-adjust__result energy-adjust__result--primary">
            <span>{{ mechanicalWorkLabel }}</span>
            <strong>{{ formatNumber(energyWhatIfResult.adjustedSummary?.netEnergyTotalWh, 1) }} Wh</strong>
          </div>
          <div
            v-if="energyWhatIfResult.adjustedSummary?.metabolicEnergy?.activeKcal != null"
            class="energy-adjust__result"
            data-test="calorie-preview"
          >
            <span>Estimated active calories</span>
            <strong>{{ formatNumber(energyWhatIfResult.adjustedSummary.metabolicEnergy.activeKcal, 0) }} kcal</strong>
            <span v-if="energyWhatIfResult.activeCaloriesDelta != null">
              {{ energyWhatIfResult.activeCaloriesDelta > 0 ? '+' : ''
              }}{{ formatNumber(energyWhatIfResult.activeCaloriesDelta, 0) }} kcal change
            </span>
            <span v-if="energyWhatIfResult.adjustedSummary.metabolicEnergy.coverage === 'PARTIAL'"
              >Covered intervals only</span
            >
          </div>
          <MetabolicEnergyDetails
            v-if="energyWhatIfResult.adjustedSummary?.metabolicEnergy?.coverage !== 'COMPLETE'"
            :energy="energyWhatIfResult.adjustedSummary?.metabolicEnergy"
          />
          <div class="energy-adjust__result" :class="energyWhatIfDeltaTone">
            <span>Mechanical change</span>
            <strong>{{ formatSignedWh(energyWhatIfResult.deltaSummary?.netEnergyTotalWh) }}</strong>
          </div>
          <div class="energy-adjust__result">
            <span>Avg power</span>
            <strong>{{ formatNumber(energyWhatIfResult.adjustedSummary?.powerWattsAvg, 0) }} W</strong>
          </div>
        </div>

        <div class="energy-adjust__actions">
          <button
            type="button"
            class="energy-adjust__action energy-adjust__action--secondary"
            :disabled="energySaveLoading || energyWhatIfResult?.baselineRiderWeightKg == null"
            @click="resetEnergyAdjustWeight"
          >
            Reset
          </button>
          <button
            type="submit"
            class="energy-adjust__action energy-adjust__action--primary"
            data-test="energy-adjust-save"
            :disabled="energySaveLoading || !energyAdjustWeightValid || !energyWeightChanged"
          >
            <i v-if="energySaveLoading" class="pi pi-spin pi-spinner"></i>
            <span>{{ energySaveLoading ? 'Saving' : 'Save' }}</span>
          </button>
        </div>
      </form>
    </Dialog>

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
import { warnSanitized } from '@/utils/safeLogging';

import { useDemoStatus } from '@/composables/useDemoStatus';
const { syntheticHealthData } = useDemoStatus();
import { computed, inject, onBeforeUnmount, ref, watch } from 'vue';
import {
  formatDateAndTime,
  formatNumber,
  formatDistanceSmart,
  formatDistance,
  formatDurationSmart,
  formatDistanceTooltip,
  formatDurationTooltip,
  formatElevation,
  formatMass,
  formatSpeed,
  formatVerticalRate,
  metersPerSecondToKilometersPerHour,
} from '@/utils/Utils';
import type { ChartPoint } from '@/utils/chartSeriesAdapter';
import MetabolicEnergyDetails from './MetabolicEnergyDetails.vue';
import TrackDescription from './TrackDescription.vue';
import {
  type EnergyWhatIfResponse,
  type GpsTrack,
  type TrackHealthResponse,
} from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import { calculateEnergyWhatIf, saveTrackEnergyRiderWeight } from '@/utils/ServiceHelper';
import { ENERGY_CALCULATION_STATE, useEnergyCalculationStatus } from '@/composables/useEnergyCalculationStatus';
import { useAsyncState } from '@/composables/useAsyncState';
import { useMeasurementSystem } from '@/composables/useMeasurementSystem';
import { massCanonicalValue, massDisplayValue, MEASUREMENT_DISPLAY_PROFILES } from '@/utils/units';
import { selectInputText, writeTextToClipboard } from '@/utils/clipboard';
import type { ToastService } from '@/types/ui';

type TrackDetailInfoPopover = {
  toggle: (event: Event) => void;
};

type InfoSegment = {
  text: string;
  strong?: boolean;
};
type InfoContent = InfoSegment[][];
const EXPLORATION_CORRIDOR_WIDTH_M = 25;
const COPY_STATUS_RESET_MS = 1800;
const ENERGY_WEIGHT_DEFAULT_KG = 75;
const ENERGY_WEIGHT_MIN_KG = 35;
const ENERGY_WEIGHT_MAX_KG = 180;
const ENERGY_WEIGHT_STEP_DISPLAY = 1;
const ENERGY_PREVIEW_DEBOUNCE_MS = 250;
const ENERGY_DELTA_CHANGED_EPSILON_WH = 0.05;
const ENERGY_WEIGHT_CHANGED_EPSILON_KG = 0.05;

defineOptions({
  name: 'TrackDetailOverview',
});

const props = withDefaults(
  defineProps<{
    gpsTrack?: GpsTrack | null;
    trackDetails: ChartPoint[];
    trackHealth?: TrackHealthResponse | null;
  }>(),
  {
    gpsTrack: null,
    trackHealth: null,
  }
);

const emit = defineEmits<{
  'track-updated': [track: GpsTrack];
}>();

const gpsTrack = computed(() => props.gpsTrack);
const trackHealth = computed(() => props.trackHealth);
const infoPopover = ref<TrackDetailInfoPopover | null>(null);
const summaryReady = computed(() => gpsTrack.value != null);
const activeTooltip = ref<string | null>(null);
const currentInfoContent = ref<InfoContent>([]);
const showExplorationInfo = ref(false);
const trackIdCopied = ref(false);
const trackIdCopyError = ref('');
const energyAdjustVisible = ref(false);
const energyAdjustWeightKg = ref(ENERGY_WEIGHT_DEFAULT_KG);
const { measurementSystem } = useMeasurementSystem();
const energyAdjustWeightDisplay = computed({
  get: () => Number(massDisplayValue(energyAdjustWeightKg.value, measurementSystem.value).toFixed(1)),
  set: (value: number) => {
    energyAdjustWeightKg.value = clampEnergyWeight(massCanonicalValue(Number(value), measurementSystem.value));
  },
});
const energyWeightDisplayUnit = computed(() => MEASUREMENT_DISPLAY_PROFILES[measurementSystem.value].mass);
const energyWeightMinDisplay = computed(() =>
  Number(massDisplayValue(ENERGY_WEIGHT_MIN_KG, measurementSystem.value).toFixed(1))
);
const energyWeightMaxDisplay = computed(() =>
  Number(massDisplayValue(ENERGY_WEIGHT_MAX_KG, measurementSystem.value).toFixed(1))
);
const { loading: energyWhatIfLoading, error: energyWhatIfError } = useAsyncState('');
const energyWhatIfResult = ref<EnergyWhatIfResponse | null>(null);
const energySaveLoading = ref(false);
const toast = inject<ToastService>('toast', { add: () => undefined });
let trackIdCopyResetTimer: number | null = null;
let energyWhatIfSerial = 0;
let energyPreviewDebounceTimer: ReturnType<typeof window.setTimeout> | null = null;

function infoText(text: string): InfoContent {
  return [[{ text }]];
}

const INFO_ENERGY = infoText(
  'Work is the estimated effort of moving: climbing, air and rolling resistance, and speeding up. Calories are estimated separately for each activity type. Both are estimates, not sensor measurements.'
);
const INFO_AVG_POWER = infoText(
  'Estimated average power, calculated from the GPS track. It is an estimate, not a power-meter value.'
);
const INFO_AVG_MOVING_POWER = infoText('Estimated average power while moving. Stops and unclear periods are left out.');
const INFO_MAX_POWER = infoText('Highest estimated average power over 30 seconds. More reliable than a single spike.');
const INFO_HEART_RATE = infoText(
  'Minimum and maximum are the lowest and highest heart rate during the track, including breaks. The moving average leaves out stops, short pauses, and GPS gaps. Missing heart-rate data is not filled in.'
);
const INFO_NORMALIZED_POWER: InfoContent = [
  [
    {
      text: 'Normalized Power (NP): estimated power that gives hard efforts more weight, so uneven efforts compare fairly with steady ones. Not measured by a power meter.',
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

/** Server motion timeline total, including short supported pauses and excluding unknown time. */
const stoppedTimeMs = computed(() => {
  const secs = props.gpsTrack?.trackDurationStoppedSecs;
  return secs != null ? secs * 1000 : 0;
});

const { pending: energyCalculationPending, message: energyCalculationMessage } =
  inject(ENERGY_CALCULATION_STATE, null) ??
  useEnergyCalculationStatus(gpsTrack, (track) => emit('track-updated', track));
const mechanicalWorkLabel = computed(() =>
  ['CAR', 'MOTORBIKING', 'AIRPLANE', 'SUPER_SONIC'].includes(props.gpsTrack?.activityType ?? '')
    ? 'Vehicle work'
    : 'External work'
);
const hasEnergy = computed(() => props.gpsTrack?.energyNetTotalWh != null);

const peakPowerWatts = computed(() => props.gpsTrack?.powerWatts30sMax ?? 0);
const energyAdjustWeightValid = computed(() => {
  const weightKg = Number(energyAdjustWeightKg.value);
  return Number.isFinite(weightKg) && weightKg >= ENERGY_WEIGHT_MIN_KG && weightKg <= ENERGY_WEIGHT_MAX_KG;
});
const energyWeightChanged = computed(
  () =>
    energyWhatIfResult.value?.baselineRiderWeightKg != null &&
    Math.abs(energyAdjustWeightKg.value - energyWhatIfResult.value.baselineRiderWeightKg) >
      ENERGY_WEIGHT_CHANGED_EPSILON_KG
);
const energyWhatIfDeltaTone = computed(() => {
  const deltaWh = Number(energyWhatIfResult.value?.deltaSummary?.netEnergyTotalWh ?? 0);
  if (deltaWh > ENERGY_DELTA_CHANGED_EPSILON_WH) return 'energy-adjust__result--positive';
  if (deltaWh < -ENERGY_DELTA_CHANGED_EPSILON_WH) return 'energy-adjust__result--negative';
  return 'energy-adjust__result--neutral';
});
const minAltitude = computed(() => finiteOrNull(props.gpsTrack?.minAltitude));
const maxAltitude = computed(() => finiteOrNull(props.gpsTrack?.maxAltitude));
const hasElevation = computed(() => minAltitude.value != null || maxAltitude.value != null);
const altitudeRange = computed(() =>
  minAltitude.value == null || maxAltitude.value == null ? null : maxAltitude.value - minAltitude.value
);
// The server uses zero totals even when the source has no elevation observations.
const totalAscent = computed(() => (hasElevation.value ? finiteOrNull(props.gpsTrack?.ascentInMeter) : null));
const totalDescent = computed(() => {
  const descent = hasElevation.value ? finiteOrNull(props.gpsTrack?.descentInMeter) : null;
  return descent == null ? null : Math.abs(descent);
});

const avgSpeed = computed(() => {
  const dist = props.gpsTrack?.trackLengthInMeter;
  const seconds = trackDuration.value / 1000;
  if (dist != null && seconds > 0) {
    return metersPerSecondToKilometersPerHour(dist / seconds);
  }
  return 0;
});

const maxSpeed = computed(() => props.gpsTrack?.speedInKmh30sMax ?? 0);
const maxElevationGainRate = computed(() => props.gpsTrack?.elevationGainPerHour30sMax ?? 0);
const maxElevationLossRate = computed(() => props.gpsTrack?.elevationLossPerHour30sMax ?? 0);
const maxSlope = computed(() => finiteOrNull(props.gpsTrack?.slopePercentageMax));
const minSlope = computed(() => finiteOrNull(props.gpsTrack?.slopePercentageMin));

const trackDescription = computed(
  () => props.gpsTrack?.trackDescription?.trim() || props.gpsTrack?.metaDescription?.trim() || ''
);

const trackIdText = computed(() => (props.gpsTrack?.id == null ? '' : String(props.gpsTrack.id)));

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
    return metersPerSecondToKilometersPerHour(dist / secs);
  }
  return 0;
});

const longestStopMs = computed(() => {
  const secs = props.gpsTrack?.trackLongestStopSecs;
  return secs != null ? secs * 1000 : 0;
});

onBeforeUnmount(() => {
  energyWhatIfSerial++;
  clearEnergyPreviewDebounce();
  if (trackIdCopyResetTimer != null) {
    window.clearTimeout(trackIdCopyResetTimer);
  }
});

watch(
  () => [props.gpsTrack?.id, props.gpsTrack?.activityType],
  () => {
    energyWhatIfSerial++;
    energyWhatIfLoading.value = false;
    energyAdjustVisible.value = false;
    energyAdjustWeightKg.value = ENERGY_WEIGHT_DEFAULT_KG;
    energyWhatIfResult.value = null;
    energyWhatIfError.value = '';
    energySaveLoading.value = false;
    clearEnergyPreviewDebounce();
  },
  { immediate: true }
);

watch(energyAdjustVisible, (visible) => {
  if (!visible) {
    energyWhatIfSerial++;
    energyWhatIfLoading.value = false;
    clearEnergyPreviewDebounce();
  }
});

function whToJoules(wh: number | null | undefined): string {
  return formatNumber((wh ?? 0) * 3600, 0);
}

function formatOptionalDistanceSmart(meters: number | null | undefined): string {
  return meters == null ? '—' : formatDistanceSmart(meters);
}

function formatOptionalDistanceTooltip(meters: number | null | undefined): string {
  return meters == null ? '—' : formatDistanceTooltip(meters);
}

function finiteOrNull(value: number | null | undefined): number | null {
  return value != null && Number.isFinite(value) ? value : null;
}

function formatOptionalElevation(meters: number | null): string {
  return meters == null ? '—' : formatElevation(meters);
}

function formatOptionalSlope(percent: number | null): string {
  return percent == null ? '—' : `${formatNumber(percent, 1)}%`;
}

function clampEnergyWeight(value: number): number {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) return ENERGY_WEIGHT_DEFAULT_KG;
  return Math.min(ENERGY_WEIGHT_MAX_KG, Math.max(ENERGY_WEIGHT_MIN_KG, numeric));
}

function commitEnergyAdjustWeight(queuePreview = true): void {
  energyAdjustWeightKg.value = clampEnergyWeight(energyAdjustWeightKg.value);
  if (queuePreview) queueEnergyWhatIf();
}

function nudgeEnergyAdjustWeight(deltaDisplay: number): void {
  energyAdjustWeightDisplay.value = Number(energyAdjustWeightDisplay.value) + deltaDisplay;
  queueEnergyWhatIf();
}

async function resetEnergyAdjustWeight(): Promise<void> {
  clearEnergyPreviewDebounce();
  const baselineRiderWeightKg = energyWhatIfResult.value?.baselineRiderWeightKg;
  if (baselineRiderWeightKg != null) {
    energyAdjustWeightKg.value = clampEnergyWeight(baselineRiderWeightKg);
  }
  await loadEnergyWhatIf();
}

function clearEnergyPreviewDebounce(): void {
  if (energyPreviewDebounceTimer != null) {
    window.clearTimeout(energyPreviewDebounceTimer);
    energyPreviewDebounceTimer = null;
  }
}

function queueEnergyWhatIf(): void {
  energyWhatIfSerial++;
  energyWhatIfLoading.value = false;
  clearEnergyPreviewDebounce();
  if (!energyAdjustWeightValid.value) return;

  energyPreviewDebounceTimer = window.setTimeout(() => {
    energyPreviewDebounceTimer = null;
    void loadEnergyWhatIf(energyAdjustWeightKg.value);
  }, ENERGY_PREVIEW_DEBOUNCE_MS);
}

function formatSignedWh(value: number | null | undefined): string {
  const numeric = Number(value ?? 0);
  const sign = numeric > 0 ? '+' : '';
  return `${sign}${formatNumber(numeric, 1)} Wh`;
}

function openEnergyAdjustDialog(): void {
  if (!props.gpsTrack?.id) return;
  energyAdjustVisible.value = true;
  void loadEnergyWhatIf();
}

async function saveEnergyRiderWeight(): Promise<void> {
  const trackId = props.gpsTrack?.id;
  if (trackId == null) return;

  clearEnergyPreviewDebounce();
  commitEnergyAdjustWeight(false);
  if (!energyAdjustWeightValid.value || !energyWeightChanged.value || energySaveLoading.value) return;
  const activityType = props.gpsTrack?.activityType;
  const savedWeightKg = energyAdjustWeightKg.value;

  energySaveLoading.value = true;
  energyWhatIfLoading.value = false;
  energyWhatIfError.value = '';
  const requestSerial = ++energyWhatIfSerial;

  try {
    const savedTrack = await saveTrackEnergyRiderWeight(trackId, savedWeightKg);
    if (
      requestSerial !== energyWhatIfSerial ||
      props.gpsTrack?.id !== trackId ||
      props.gpsTrack?.activityType !== activityType ||
      savedTrack.id !== trackId
    )
      return;
    emit('track-updated', savedTrack);
    energyAdjustVisible.value = false;
    toast.add({
      severity: 'success',
      summary: 'Energy saved',
      detail: `This track now uses ${formatMass(savedWeightKg, 1)}.`,
      life: 2500,
    });
  } catch (error) {
    if (requestSerial !== energyWhatIfSerial) return;
    warnSanitized(
      '[track-details] failed to save rider-weight energy',
      { trackId, error },
      { topic1: 'tracks', topic2: 'TrackDetailOverview', topic3: 'saveEnergyRiderWeight' }
    );
    energyWhatIfError.value = 'Could not save energy.';
  } finally {
    if (props.gpsTrack?.id === trackId && props.gpsTrack?.activityType === activityType)
      energySaveLoading.value = false;
  }
}

async function loadEnergyWhatIf(riderWeightKg?: number): Promise<void> {
  const trackId = props.gpsTrack?.id;
  if (trackId == null) return;

  const activityType = props.gpsTrack?.activityType;
  const requestedWeightKg = riderWeightKg;
  const requestSerial = ++energyWhatIfSerial;
  energyWhatIfLoading.value = true;
  energyWhatIfError.value = '';

  try {
    const result = await calculateEnergyWhatIf(trackId, requestedWeightKg);
    if (
      requestSerial !== energyWhatIfSerial ||
      props.gpsTrack?.id !== trackId ||
      props.gpsTrack?.activityType !== activityType ||
      result.gpsTrackId !== trackId
    )
      return;
    if (
      requestedWeightKg != null &&
      Math.abs(requestedWeightKg - Number(energyAdjustWeightKg.value)) > ENERGY_WEIGHT_CHANGED_EPSILON_KG
    ) {
      return;
    }
    energyWhatIfResult.value = result;

    const responseWeightKg = result.requestedRiderWeightKg ?? result.baselineRiderWeightKg;
    if (requestedWeightKg == null && responseWeightKg != null) {
      energyAdjustWeightKg.value = clampEnergyWeight(responseWeightKg);
    }
  } catch (error) {
    if (requestSerial !== energyWhatIfSerial) return;
    warnSanitized(
      '[track-details] failed to calculate energy what-if',
      { trackId, riderWeightKg, error },
      { topic1: 'tracks', topic2: 'TrackDetailOverview', topic3: 'loadEnergyWhatIf' }
    );
    energyWhatIfError.value = 'Could not calculate energy.';
  } finally {
    if (requestSerial === energyWhatIfSerial) energyWhatIfLoading.value = false;
  }
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
    warnSanitized('[track-details] failed to copy TrackID', copyError, {
      topic1: 'tracks',
      topic2: 'TrackDetailOverview',
      topic3: 'copyTrackId',
    });
    trackIdCopyError.value = 'Could not copy TrackID.';
  }
}

function toggleTooltip(id: string) {
  activeTooltip.value = activeTooltip.value === id ? null : id;
}

function showInfo(event: Event, content: InfoContent) {
  currentInfoContent.value = content;
  infoPopover.value?.toggle(event);
}
</script>

<style scoped>
.overview-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
  max-width: 1120px;
  margin-inline: auto;
  padding: 20px;
}
.metrics-primary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  background: color-mix(in srgb, var(--accent) 4%, var(--surface-card));
  border: 1px solid var(--accent-subtle);
  border-radius: 12px;
  overflow: hidden;
}
.metric-tile {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: flex-start;
  min-width: 0;
  position: relative;
}
.metrics-primary .metric-tile {
  padding: 16px 20px;
  border-right: 1px solid var(--accent-subtle);
}
.metrics-primary .metric-tile:last-child {
  border-right: 0;
}
.metric-tile__icon {
  font-size: 16px;
  color: var(--accent-muted);
  margin-bottom: 8px;
}
.metrics-grid .metric-tile__icon {
  display: none;
}
.metric-tile__value {
  color: var(--text-primary);
  font-size: 28px;
  font-weight: 700;
  line-height: 1.25;
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
}
.metric-tile__value--sm {
  font-size: 16px;
  font-weight: 600;
  line-height: 24px;
  letter-spacing: 0;
}
.metric-tile__label {
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 4px;
  color: var(--text-muted);
  font-size: 13px;
  line-height: 1.4;
}
.metric-tile__sub {
  margin-top: 4px;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.4;
}
.overview-layout {
  display: grid;
  gap: 16px;
  align-items: start;
}
.overview-sections,
.overview-insights {
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 16px;
}
.overview-section {
  min-width: 0;
  padding: 16px;
  background: var(--surface-card);
  border: 1px solid var(--border-default);
  border-radius: 12px;
}
.overview-container .section-label {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0;
  margin: 0 0 16px;
  color: var(--text-primary);
  font-size: 16px;
  font-weight: 600;
  line-height: 24px;
}
.overview-container .section-label > i {
  color: var(--text-muted);
  font-size: 16px;
  opacity: 1;
}
.metrics-grid,
.energy-summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 16px 12px;
  padding: 0;
}
.overview-section--timing .metrics-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
.metrics-grid .metric-tile {
  padding: 0;
  margin: 0;
  background: transparent;
}
.section-note,
.motion-explanation,
.elevation-explanation,
.health-attribution,
.energy-coverage {
  margin: 12px 0 0;
  font-size: 13px;
  line-height: 1.5;
  color: var(--text-muted);
}
.health-section__heading {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 4px 12px;
  margin-bottom: 16px;
}
.overview-container .health-section__heading .section-label {
  margin: 0;
}
.elevation-explanation {
  margin: 0;
}
.overview-container .info-drawer {
  margin: 12px 0 0;
  border: 0;
  border-top: 1px solid var(--border-subtle);
  border-radius: 0;
  overflow: visible;
}
.overview-container > .info-drawer {
  margin: 0;
  padding: 0 16px;
  border: 1px solid var(--border-default);
  border-radius: 12px;
}
.overview-container .info-drawer__summary {
  min-height: 44px;
  padding: 8px 0;
  background: transparent;
  color: var(--text-muted);
  font-size: 13px;
  font-weight: 400;
  line-height: 20px;
}
.overview-container .info-drawer[open] > .info-drawer__summary {
  color: var(--text-secondary);
}
.overview-container .info-drawer__summary:hover {
  color: var(--accent-text);
}
.overview-container .info-drawer .metrics-grid {
  padding-block: 8px;
}
.overview-container .info-list {
  padding: 0 0 8px;
  background: transparent;
}
.overview-container .info-row {
  padding: 8px 0;
}
.energy-value-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.coverage-badge {
  display: inline-flex;
  padding: 2px 6px;
  border-radius: 4px;
  background: var(--warning-bg);
  color: var(--warning-text);
  font-size: 12px;
  font-weight: 500;
  line-height: 16px;
}
.energy-summary__status {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-muted);
}
.energy-details__content {
  padding-block: 8px;
}
.energy-details__content .metabolic-energy {
  margin: 8px 0 16px;
}
.exploration-meter {
  display: block;
  width: 100%;
  height: 8px;
  margin-top: 16px;
  border: 0;
  border-radius: 4px;
  background: var(--surface-hover);
}
.exploration-meter::-webkit-meter-bar {
  background: var(--surface-hover);
  border: 0;
  border-radius: 4px;
}
.exploration-meter::-webkit-meter-optimum-value {
  background: var(--accent);
  border-radius: 4px;
}
.exploration-meter::-moz-meter-bar {
  background: var(--accent);
}

.exploration-info-btn {
  margin-left: 0;
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
  margin: 12px 0 0;
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
  margin: 12px 0 0;
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
  border: 0;
  background: var(--surface-elevated);
}

.metric-tile--health {
  border: 0;
  background: var(--surface-elevated);
}

.metric-tile--health .metric-tile__icon {
  color: #e11d48;
}

.energy-adjust-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.4rem;
  min-height: 2.75rem;
  margin: 0 0.5rem;
  padding: 0.5rem;
  font-size: var(--text-sm-size);
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  transition:
    background-color 0.15s ease,
    color 0.15s ease;
}

.energy-adjust-trigger:hover,
.energy-adjust-trigger:focus-visible {
  background: var(--surface-hover);
  color: var(--text-secondary);
  outline: none;
}

.energy-adjust-trigger i {
  font-size: var(--text-xs-size);
  line-height: 1;
}

.energy-adjust {
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
  min-width: 0;
}

.energy-adjust__baseline {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.55rem;
}

.energy-adjust__baseline div,
.energy-adjust__result {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 0.65rem 0.75rem;
  border: 1px solid var(--border-subtle);
  border-radius: 8px;
  background: var(--surface-elevated);
}

.energy-adjust__baseline span,
.energy-adjust__result span {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}

.energy-adjust__baseline strong,
.energy-adjust__result strong {
  margin-top: 0.2rem;
  color: var(--text-primary);
  font-size: var(--text-base-size);
  line-height: var(--text-base-lh);
  font-weight: 700;
  overflow-wrap: anywhere;
}

.energy-adjust__field {
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
  min-width: 0;
}

.energy-adjust__label-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.75rem;
}

.energy-adjust__label-row label {
  font-size: var(--text-xs-size);
  font-weight: 700;
  color: var(--text-muted);
  text-transform: uppercase;
}

.energy-adjust__label-row span {
  font-size: var(--text-xs-size);
  font-weight: 700;
  color: var(--text-secondary);
  white-space: nowrap;
}

.energy-adjust__stepper {
  display: grid;
  grid-template-columns: 2.35rem minmax(0, 1fr) 2.35rem;
  gap: 0.45rem;
  align-items: center;
}

.energy-adjust__step-btn,
.energy-adjust__action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--border-default);
  border-radius: 8px;
  cursor: pointer;
  transition:
    background-color 0.15s ease,
    border-color 0.15s ease,
    color 0.15s ease,
    opacity 0.15s ease;
}

.energy-adjust__step-btn {
  width: 2.35rem;
  height: 2.35rem;
  background: var(--surface-card);
  color: var(--text-secondary);
}

.energy-adjust__step-btn:hover:not(:disabled),
.energy-adjust__step-btn:focus-visible {
  border-color: var(--accent);
  color: var(--accent);
  background: var(--surface-hover);
  outline: none;
}

.energy-adjust__step-btn:disabled {
  cursor: default;
  opacity: 0.45;
}

.energy-adjust__input-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.35rem;
  min-width: 0;
  min-height: 2.35rem;
  padding: 0 0.5rem;
  border: 1px solid var(--border-medium);
  border-radius: 8px;
  background: var(--surface-input, var(--surface-card));
  color: var(--text-secondary);
}

.energy-adjust__input {
  width: 4.4rem;
  border: none;
  background: transparent;
  color: var(--text-primary);
  font-size: var(--text-base-size);
  font-weight: 700;
  text-align: center;
  outline: none;
  appearance: textfield;
}

.energy-adjust__input::-webkit-outer-spin-button,
.energy-adjust__input::-webkit-inner-spin-button {
  margin: 0;
  appearance: none;
}

.energy-adjust__range {
  width: 100%;
  accent-color: var(--accent);
  cursor: pointer;
}

.energy-adjust__range:disabled {
  cursor: default;
  opacity: 0.55;
}

.energy-adjust__scale {
  display: flex;
  justify-content: space-between;
  gap: 0.75rem;
  color: var(--text-faint);
  font-size: var(--text-2xs-size);
  line-height: var(--text-2xs-lh);
}

.energy-adjust__error {
  padding: 0.55rem 0.7rem;
  border: 1px solid color-mix(in srgb, var(--error) 35%, transparent);
  border-radius: 8px;
  background: color-mix(in srgb, var(--error) 8%, transparent);
  color: var(--error);
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}

.energy-adjust__loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.45rem;
  min-height: 4.5rem;
  color: var(--text-muted);
  font-size: var(--text-sm-size);
}

.energy-adjust__results {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.55rem;
  min-width: 0;
  transition: opacity 0.12s ease;
}

.energy-adjust__results--updating {
  opacity: 0.72;
}

.energy-adjust__result {
  justify-content: center;
  min-height: 4.8rem;
}

.energy-adjust__result--primary {
  border-color: var(--accent-subtle);
  background: var(--accent-bg);
}

.energy-adjust__result--positive strong {
  color: var(--warning);
}

.energy-adjust__result--negative strong {
  color: var(--success);
}

.energy-adjust__result--neutral strong {
  color: var(--text-primary);
}

.energy-adjust__actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.55rem;
  flex-wrap: wrap;
}

.energy-adjust__action {
  gap: 0.35rem;
  min-height: 2.35rem;
  padding: 0.4rem 0.85rem;
  font-size: var(--text-sm-size);
  font-weight: 700;
}

.energy-adjust__action--secondary {
  background: var(--surface-card);
  color: var(--text-secondary);
}

.energy-adjust__action--primary {
  border-color: var(--accent);
  background: var(--accent);
  color: var(--accent-contrast);
}

.energy-adjust__action:hover:not(:disabled),
.energy-adjust__action:focus-visible {
  filter: brightness(0.98);
  outline: none;
}

.energy-adjust__action:disabled {
  cursor: default;
  opacity: 0.55;
}

/* ── Energy tooltip ── */
.energy-tooltip-wrapper {
  cursor: pointer;
  transition:
    border-color 0.15s ease,
    background-color 0.15s ease,
    transform 0.12s ease;
}

.energy-tooltip-wrapper:hover {
  border-color: var(--accent) !important;
}

.energy-tooltip-wrapper:active {
  transform: scale(0.985);
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

.info-list {
  background: var(--surface-elevated);
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
  width: 44px;
  height: 44px;
  border: 1px solid var(--border-default);
  background: var(--surface-card);
  color: var(--text-secondary);
  cursor: pointer;
  border-radius: 6px;
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

/* ── Mobile ── */
@media (max-width: 760px) {
  .energy-adjust__results {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 480px) {
  .energy-adjust__baseline,
  .energy-adjust__results {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .energy-adjust__result--primary {
    grid-column: 1 / -1;
  }
}

@container track-details (max-width: 480px) {
  .skeleton-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@container track-details (min-width: 820px) {
  .overview-layout {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@container track-details (max-width: 480px) {
  .overview-container {
    padding: 16px;
  }
  .metrics-primary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .metrics-primary .metric-tile {
    padding: 16px;
  }
  .metrics-primary .metric-tile:nth-child(2n) {
    border-right: 0;
  }
  .metrics-primary .metric-tile:nth-child(n + 3) {
    border-top: 1px solid var(--accent-subtle);
  }
  .metrics-primary .metric-tile:last-child:nth-child(odd) {
    grid-column: 1 / -1;
    border-right: 0;
  }
  .metrics-primary .metric-tile__icon {
    display: none;
  }
  .metrics-primary .metric-tile__value {
    font-size: 24px;
  }
}
.info-btn {
  min-width: 24px;
  min-height: 24px;
}
</style>
