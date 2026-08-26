<template>
  <div>
    <BottomSheet v-model="showMenu" :detents="statsDetents" @closed="onSheetClosed">
      <template #title>
        <div class="stats-header-nav">
          <i class="bi bi-graph-up stats-sheet-icon"></i>
          <div class="stats-header-tabs">
            <button
              class="stats-header-tab sheet-header-tab"
              :class="{ 'sheet-header-tab--active': activeTab === 'overview' }"
              @pointerdown.stop
              @click="activeTab = 'overview'"
            >
              Overview
            </button>
            <button
              class="stats-header-tab sheet-header-tab"
              :class="{ 'sheet-header-tab--active': activeTab === 'stats' }"
              @pointerdown.stop
              @click="activeTab = 'stats'"
            >
              Trends
            </button>
            <button
              class="stats-header-tab sheet-header-tab"
              :class="{ 'sheet-header-tab--active': activeTab === 'tracks' }"
              @pointerdown.stop
              @click="activeTab = 'tracks'"
            >
              Tracks
            </button>
          </div>
        </div>
      </template>
      <div v-if="active" class="statistics-root">
        <div v-if="statisticsError" class="statistics-refresh-state" role="alert" data-test="statistics-refresh-error">
          <span>{{ statisticsErrorMessage }}</span>
          <button type="button" @click="retryStatistics">Retry</button>
        </div>
        <Tabs v-model:value="activeTab" class="sheet-scroll-tabs">
          <TabPanels>
            <!-- ── Tab 1: Overview ── -->
            <TabPanel value="overview">
              <StatisticsOverview
                :tracks="statisticsTracks"
                :tracks-count="tracksCount"
                :unfiltered-total="unfilteredTotal"
                :filter-revision="filterStore.trackSetRevision"
                :filter-request="filterStore.activeFilterRequest"
                :retry-revision="overviewRetryRevision"
                :indexed-media-count="indexedMediaCount"
                :indexed-photo-count="indexedPhotoCount"
                :indexed-video-count="indexedVideoCount"
                @open-details="emit('open-details', $event)"
                @open-filter="emit('open-filter')"
                @open-media="showMediaTrends"
                @track-updated="onOverviewTrackUpdated"
                @view-all-tracks="showNewestTracks"
                @view-highlight-exclusions="showHighlightExclusions"
              />
            </TabPanel>

            <!-- ── Tab 2: Track Log ── -->
            <TabPanel value="tracks">
              <div class="tracks-tab">
                <TrackBrowserView
                  ref="trackBrowserView"
                  :tracks="trackBrowserSourceTracks"
                  :selected-track-id="selectedTrackId ?? null"
                  :reset-key="trackBrowserResetKey"
                  @select-track="emit('select-track', $event)"
                  @open-details="emit('open-details', $event)"
                >
                  <template #toolbar>
                    <TrackBrowserQuickViews v-model="trackQuickView" :options="trackQuickViewOptions" />
                  </template>
                </TrackBrowserView>
              </div>
            </TabPanel>

            <!-- ── Tab 3: Trends ── -->
            <TabPanel value="stats">
              <div class="statistics-holder">
                <!-- ── Controls ── -->
                <div class="stats-controls">
                  <div class="stats-controls__row">
                    <div class="stats-controls__select-wrap">
                      <i class="bi bi-calendar3 stats-controls__icon"></i>
                      <Select
                        v-model="selectedGrouping"
                        :options="statisticGroupings"
                        option-label="name"
                        option-value="code"
                        placeholder="Aggregation level"
                        class="stats-select"
                        append-to="body"
                        @change="fetchStatistics"
                      />
                    </div>
                    <div v-if="availableSubUnits.length > 0" class="stats-controls__select-wrap">
                      <i class="bi bi-funnel stats-controls__icon"></i>
                      <Select
                        v-model="selectedSubUnit"
                        :options="availableSubUnits"
                        placeholder="All sub-units"
                        class="stats-select"
                        append-to="body"
                        show-clear
                      />
                    </div>
                    <!-- ── Table / Charts toggle ── -->
                    <div class="stats-view-toggle view-toggle">
                      <button
                        :class="[
                          'toggle-btn view-toggle-button',
                          { 'view-toggle-button--active': statsView === 'table' },
                        ]"
                        @click="statsView = 'table'"
                      >
                        <i class="bi bi-table"></i> Table
                      </button>
                      <button
                        :class="[
                          'toggle-btn view-toggle-button',
                          { 'view-toggle-button--active': statsView === 'charts' },
                        ]"
                        @click="statsView = 'charts'"
                      >
                        <i class="bi bi-bar-chart-line"></i> Charts
                      </button>
                    </div>
                  </div>
                </div>

                <!-- ── Aggregate summary tiles ── -->
                <div v-if="filteredStatisticData.length > 0" class="stats-summary">
                  <div class="stat-tile">
                    <i class="bi bi-bar-chart-line stat-tile__icon" style="color: var(--accent)"></i>
                    <div class="stat-tile__value">{{ summaryStats.periods }}</div>
                    <div class="stat-tile__label">
                      Periods
                      <button class="info-btn" aria-label="About periods" @click.stop="showInfo($event, INFO_PERIODS)">
                        <i class="bi bi-info-circle"></i>
                      </button>
                    </div>
                  </div>
                  <div class="stat-tile">
                    <i class="bi bi-pin-map stat-tile__icon" style="color: var(--chart-series-1)"></i>
                    <div class="stat-tile__value">{{ summaryStats.tracks }}</div>
                    <div class="stat-tile__label">
                      Tracks
                      <button class="info-btn" aria-label="About tracks" @click.stop="showInfo($event, INFO_TRACKS)">
                        <i class="bi bi-info-circle"></i>
                      </button>
                    </div>
                  </div>
                  <div class="stat-tile">
                    <i class="bi bi-signpost-split stat-tile__icon" style="color: var(--chart-series-2)"></i>
                    <div v-tooltip.top="{ value: summaryStats.distanceFull, showDelay: 400 }" class="stat-tile__value">
                      {{ summaryStats.distance }}
                    </div>
                    <div class="stat-tile__label">
                      Total Dist.
                      <button
                        class="info-btn"
                        aria-label="About distance"
                        @click.stop="showInfo($event, INFO_DISTANCE)"
                      >
                        <i class="bi bi-info-circle"></i>
                      </button>
                    </div>
                  </div>
                  <div class="stat-tile">
                    <i class="bi bi-clock stat-tile__icon" style="color: var(--info)"></i>
                    <div v-tooltip.top="{ value: summaryStats.durationFull, showDelay: 400 }" class="stat-tile__value">
                      {{ summaryStats.duration }}
                    </div>
                    <div class="stat-tile__label">
                      Total Time
                      <button
                        class="info-btn"
                        aria-label="About duration"
                        @click.stop="showInfo($event, INFO_DURATION)"
                      >
                        <i class="bi bi-info-circle"></i>
                      </button>
                    </div>
                  </div>
                  <div v-if="summaryStats.hasEnergy" class="stat-tile">
                    <i class="bi bi-lightning-charge stat-tile__icon" style="color: var(--chart-series-3)"></i>
                    <div class="stat-tile__value">{{ summaryStats.energy }}</div>
                    <div class="stat-tile__label">
                      Total Energy
                      <button class="info-btn" aria-label="About energy" @click.stop="showInfo($event, INFO_ENERGY)">
                        <i class="bi bi-info-circle"></i>
                      </button>
                    </div>
                  </div>
                </div>

                <!-- ── Data table ── -->
                <div v-if="statsView === 'table'" class="table-section">
                  <div class="table-scroll-x">
                    <DataTable
                      :value="trendTableRows"
                      column-resize-mode="fit"
                      responsive-layout="scroll"
                      table-style="min-width: 10rem"
                      class="p-datatable-sm statistics-table"
                    >
                      <Column field="groupBy" header="Period" :sortable="true" style="min-width: 8rem" />
                      <Column
                        field="daysWithActivities"
                        header="Days"
                        :sortable="true"
                        header-class="number-column"
                        class="number-column"
                        style="min-width: 5rem"
                      />
                      <Column
                        field="numberOfTracks"
                        header="Tracks"
                        :sortable="true"
                        header-class="number-column"
                        class="number-column"
                        style="min-width: 5rem"
                      />
                      <Column
                        field="imageCount"
                        header="Photos"
                        :sortable="true"
                        header-class="number-column"
                        class="number-column"
                        style="min-width: 5rem"
                      >
                        <template #body="slotProps">
                          {{ formatMediaCount(slotProps.data.imageCount) }}
                        </template>
                      </Column>
                      <Column
                        field="videoCount"
                        header="Videos"
                        :sortable="true"
                        header-class="number-column"
                        class="number-column"
                        style="min-width: 5rem"
                      >
                        <template #body="slotProps">
                          {{ formatMediaCount(slotProps.data.videoCount) }}
                        </template>
                      </Column>
                      <Column
                        v-for="column in smartValueColumns"
                        :key="column.field"
                        :field="column.field"
                        :header="column.header"
                        :sortable="true"
                        header-class="number-column"
                        class="number-column"
                        style="min-width: 8rem"
                      >
                        <template #body="slotProps">
                          <span
                            v-tooltip.top="{
                              value: smartValueTooltip(slotProps.data, column),
                              showDelay: 400,
                            }"
                          >
                            {{ formatSmartValue(slotProps.data, column) }}
                          </span>
                        </template>
                      </Column>
                      <Column
                        field="energyNetTotalWhSum"
                        :sortable="true"
                        header-class="number-column"
                        class="number-column"
                        style="min-width: 7rem"
                      >
                        <template #header>
                          <span>Energy</span>
                          <button
                            class="info-btn info-btn--col"
                            aria-label="About energy"
                            @click.stop="showInfo($event, INFO_ENERGY)"
                          >
                            <i class="bi bi-info-circle"></i>
                          </button>
                        </template>
                        <template #body="slotProps">
                          {{ formatEnergy(slotProps.data.energyNetTotalWhSum) }}
                        </template>
                      </Column>
                      <Column
                        field="powerWattsAvgMed"
                        :sortable="true"
                        header-class="number-column"
                        class="number-column"
                        style="min-width: 7rem"
                      >
                        <template #header>
                          <span>Avg Power</span>
                          <button
                            class="info-btn info-btn--col"
                            aria-label="About Average Power"
                            @click.stop="showInfo($event, INFO_AVG_POWER)"
                          >
                            <i class="bi bi-info-circle"></i>
                          </button>
                        </template>
                        <template #body="slotProps">
                          {{ formatPower(slotProps.data.powerWattsAvgMed) }}
                        </template>
                      </Column>
                      <Column
                        field="normalizedPowerMed"
                        :sortable="true"
                        header-class="number-column"
                        class="number-column"
                        style="min-width: 7rem"
                      >
                        <template #header>
                          <span>NP</span>
                          <button
                            class="info-btn info-btn--col"
                            aria-label="About Normalized Power"
                            @click.stop="showInfo($event, INFO_NORMALIZED_POWER)"
                          >
                            <i class="bi bi-info-circle"></i>
                          </button>
                        </template>
                        <template #body="slotProps">
                          {{ formatPower(slotProps.data.normalizedPowerMed) }}
                        </template>
                      </Column>
                      <Column
                        field="intensityIndexAvg"
                        :sortable="true"
                        header-class="number-column"
                        class="number-column"
                        style="min-width: 7rem"
                      >
                        <template #header>
                          <span>Intensity</span>
                          <button
                            class="info-btn info-btn--col"
                            aria-label="About Intensity Index"
                            @click.stop="showInfo($event, INFO_INTENSITY_INDEX)"
                          >
                            <i class="bi bi-info-circle"></i>
                          </button>
                        </template>
                        <template #body="slotProps">
                          {{
                            slotProps.data.intensityIndexAvg != null && slotProps.data.intensityIndexAvg > 0
                              ? slotProps.data.intensityIndexAvg.toFixed(2)
                              : ''
                          }}
                        </template>
                      </Column>
                      <Column
                        field="trainingLoadPerRideAvg"
                        :sortable="true"
                        header-class="number-column"
                        class="number-column"
                        style="min-width: 8rem"
                      >
                        <template #header>
                          <span>Training Load</span>
                          <button
                            class="info-btn info-btn--col"
                            aria-label="About Training Load"
                            @click.stop="showInfo($event, INFO_TRAINING_LOAD)"
                          >
                            <i class="bi bi-info-circle"></i>
                          </button>
                        </template>
                        <template #body="slotProps">
                          {{
                            slotProps.data.trainingLoadPerRideAvg != null && slotProps.data.trainingLoadPerRideAvg > 0
                              ? Math.round(slotProps.data.trainingLoadPerRideAvg)
                              : ''
                          }}
                        </template>
                      </Column>
                    </DataTable>
                  </div>
                </div>

                <!-- ── Charts (inline) ── -->
                <div v-if="statsView === 'charts'" class="charts-scroll">
                  <div v-if="trendChartPeriods.length > 0" class="chart-card">
                    <div class="chart-header chart-section-header" style="--chart-header-accent: var(--chart-series-1)">
                      <i class="bi bi-clock" style="color: var(--chart-series-1)"></i> Duration
                    </div>
                    <highcharts ref="chartDuration" :options="chartOptionsDuration" class="stat-chart" />
                  </div>
                  <div v-if="trendChartPeriods.length > 0" class="chart-card">
                    <div class="chart-header chart-section-header" style="--chart-header-accent: var(--chart-series-2)">
                      <i class="bi bi-signpost-split" style="color: var(--chart-series-2)"></i> Distance
                    </div>
                    <highcharts ref="chartDistance" :options="chartOptionsDistance" class="stat-chart" />
                  </div>
                  <div v-if="trendChartPeriods.length > 0" class="chart-card">
                    <div class="chart-header chart-section-header" style="--chart-header-accent: var(--info)">
                      <i class="bi bi-bar-chart-line" style="color: var(--info)"></i> Activity
                    </div>
                    <highcharts ref="chartActivity" :options="chartOptionsActivity" class="stat-chart" />
                  </div>
                  <div v-if="chartMetricAvailability.hasEnergy" class="chart-card">
                    <div class="chart-header chart-section-header" style="--chart-header-accent: var(--chart-series-3)">
                      <i class="bi bi-lightning-charge" style="color: var(--chart-series-3)"></i> Energy
                      <button
                        class="info-btn info-btn--header"
                        aria-label="About energy"
                        @click.stop="showInfo($event, INFO_ENERGY)"
                      >
                        <i class="bi bi-info-circle"></i>
                      </button>
                    </div>
                    <highcharts ref="chartEnergy" :options="chartOptionsEnergy" class="stat-chart" />
                  </div>
                  <div v-if="chartMetricAvailability.hasFitness" class="chart-card">
                    <div class="chart-header chart-section-header" style="--chart-header-accent: var(--error)">
                      <i class="bi bi-speedometer2" style="color: var(--error)"></i> Intensity Index
                      <button
                        class="info-btn info-btn--header"
                        aria-label="About Intensity Index"
                        @click.stop="showInfo($event, INFO_INTENSITY_INDEX)"
                      >
                        <i class="bi bi-info-circle"></i>
                      </button>
                    </div>
                    <highcharts ref="chartIntensityIndex" :options="chartOptionsIntensityIndex" class="stat-chart" />
                  </div>
                  <div v-if="chartMetricAvailability.hasFitness" class="chart-card">
                    <div
                      class="chart-header chart-section-header"
                      style="--chart-header-accent: var(--accent-text-light)"
                    >
                      <i class="bi bi-heart-pulse" style="color: var(--accent-text-light)"></i> Training Load
                      <button
                        class="info-btn info-btn--header"
                        aria-label="About Training Load"
                        @click.stop="showInfo($event, INFO_TRAINING_LOAD)"
                      >
                        <i class="bi bi-info-circle"></i>
                      </button>
                    </div>
                    <highcharts ref="chartTrainingLoad" :options="chartOptionsTrainingLoad" class="stat-chart" />
                  </div>
                  <div v-if="trendChartPeriods.length > 0" class="chart-card">
                    <div class="chart-header chart-section-header" style="--chart-header-accent: var(--success)">
                      <i class="bi bi-compass" style="color: var(--success)"></i> Exploration
                      <button
                        class="info-btn info-btn--header"
                        aria-label="About exploration"
                        @click.stop="showInfo($event, INFO_EXPLORATION)"
                      >
                        <i class="bi bi-info-circle"></i>
                      </button>
                    </div>
                    <highcharts
                      v-if="chartMetricAvailability.hasExploration"
                      ref="chartExploration"
                      :options="chartOptionsExploration"
                      class="stat-chart"
                    />
                    <div v-else class="chart-pending">
                      <i class="bi bi-hourglass-split"></i>
                      <span>Exploration data is being calculated in the background.</span>
                    </div>
                  </div>
                  <div ref="mediaTrendCard" class="chart-card media-trend-card">
                    <div
                      class="chart-header chart-section-header media-trend-header"
                      style="--chart-header-accent: var(--chart-series-3)"
                    >
                      <div class="media-trend-header__title">
                        <i class="bi bi-images" style="color: var(--chart-series-3)"></i>
                        <span>Media</span>
                      </div>
                      <div class="media-trend-mode" aria-label="Media scope">
                        <button
                          v-tooltip.top="{
                            value: MEDIA_SCOPE_ALL_TOOLTIP,
                            showDelay: MEDIA_SCOPE_TOOLTIP_DELAY_MS,
                          }"
                          type="button"
                          :class="{ 'media-trend-mode__button--active': mediaTrendScope === MEDIA_SCOPE_ALL }"
                          :aria-pressed="mediaTrendScope === MEDIA_SCOPE_ALL"
                          :aria-describedby="
                            focusedMediaTrendScope === MEDIA_SCOPE_ALL ? MEDIA_SCOPE_FOCUS_TOOLTIP_ID : undefined
                          "
                          @focus="focusedMediaTrendScope = MEDIA_SCOPE_ALL"
                          @blur="clearFocusedMediaTrendScope(MEDIA_SCOPE_ALL)"
                          @click="setMediaTrendScope(MEDIA_SCOPE_ALL)"
                        >
                          All indexed
                        </button>
                        <button
                          v-tooltip.top="{
                            value: MEDIA_SCOPE_MATCHED_TOOLTIP,
                            showDelay: MEDIA_SCOPE_TOOLTIP_DELAY_MS,
                          }"
                          type="button"
                          :class="{ 'media-trend-mode__button--active': mediaTrendScope === MEDIA_SCOPE_MATCHED }"
                          :aria-pressed="mediaTrendScope === MEDIA_SCOPE_MATCHED"
                          :aria-describedby="
                            focusedMediaTrendScope === MEDIA_SCOPE_MATCHED ? MEDIA_SCOPE_FOCUS_TOOLTIP_ID : undefined
                          "
                          @focus="focusedMediaTrendScope = MEDIA_SCOPE_MATCHED"
                          @blur="clearFocusedMediaTrendScope(MEDIA_SCOPE_MATCHED)"
                          @click="setMediaTrendScope(MEDIA_SCOPE_MATCHED)"
                        >
                          Track related
                        </button>
                      </div>
                      <div
                        v-if="focusedMediaScopeTooltip"
                        :id="MEDIA_SCOPE_FOCUS_TOOLTIP_ID"
                        class="media-trend-focus-tooltip"
                        role="tooltip"
                      >
                        {{ focusedMediaScopeTooltip }}
                      </div>
                    </div>
                    <p class="media-trend-notice">{{ mediaScopeHelp }}</p>
                    <div
                      v-if="mediaTrendLoading && mediaTrendBuckets.length === 0"
                      class="media-trend-state"
                      role="status"
                    >
                      <i class="pi pi-spin pi-spinner" aria-hidden="true"></i>
                      <span>Loading media trends…</span>
                    </div>
                    <div
                      v-else-if="mediaTrendError && mediaTrendBuckets.length === 0"
                      class="media-trend-state"
                      role="alert"
                    >
                      <span>Media trends could not be loaded.</span>
                      <button type="button" @click="fetchStatistics">Retry</button>
                    </div>
                    <div v-else-if="trendChartPeriods.length === 0" class="media-trend-state">
                      <i class="bi bi-images" aria-hidden="true"></i>
                      <span>No media or activity periods available.</span>
                    </div>
                    <highcharts
                      v-else
                      ref="chartMedia"
                      :options="chartOptionsMedia"
                      class="stat-chart"
                      data-test="media-trend-chart"
                    />
                    <div v-if="undatedMediaBucket" class="media-trend-badges">
                      <button
                        v-if="undatedMediaBucket"
                        type="button"
                        class="media-trend-badge media-trend-undated"
                        @click="openMediaBucket(undatedMediaBucket)"
                      >
                        <span>Undated media</span>
                        <strong>{{ mediaBucketCount(undatedMediaBucket).toLocaleString() }}</strong>
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            </TabPanel>
          </TabPanels>
        </Tabs>
      </div>

      <!-- ── Shared info popover ── -->
      <Popover ref="infoPopover" class="stat-info-popover" append-to="body">
        <p class="stat-info-text">{{ currentInfoText }}</p>
      </Popover>
    </BottomSheet>

    <MediaTrendMosaic
      v-model="mediaMosaicVisible"
      :bucket="selectedMediaBucket"
      :grouping="mediaGrouping"
      :scope="mediaTrendScope"
      :track-ids="resolvedTrendTrackIds"
      @open-activity="emit('open-track-photos', $event)"
      @open-media-on-map="emit('open-media-on-map', $event)"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, markRaw, nextTick, ref, shallowRef, watch, type Ref, type ShallowRef } from 'vue';
import {
  formatDistanceSmart as formatDistanceSmartUtil,
  formatDistance,
  formatDurationSmart as formatDurationSmartUtil,
  formatDurationTooltip as formatDurationTooltipUtil,
  formatDistanceTooltip as formatDistanceTooltipUtil,
  formatLocaleNumber,
} from '@/utils/Utils';
import { fetchStatisticsForTrackIds, resolveStatisticsTrackIds } from '@/utils/ServiceHelper';
import BottomSheet from '@/components/ui/BottomSheet.vue';
import MediaTrendMosaic from '@/components/statistics/MediaTrendMosaic.vue';
import StatisticsOverview from '@/components/statistics/StatisticsOverview.vue';
import TrackBrowserQuickViews from '@/components/track-browser/TrackBrowserQuickViews.vue';
import TrackBrowserView from '@/components/track-browser/TrackBrowserView.vue';
import { useFilterStore, type ActiveFilterRequest } from '@/stores/filterStore';
import type { TrackBrowserOption, TrackBrowserPreset } from '@/components/track-browser/trackBrowser.types';
import {
  MediaTrendRequestGroupingEnum,
  MediaTrendRequestScopeEnum,
  type GpsTrack,
  type GpsTrackStatistics,
  type MediaTrendBucketDto,
  type MediaTrendRequestGroupingEnum as MediaGrouping,
  type MediaTrendRequestScopeEnum as MediaScope,
} from 'x8ing-mtl-api-typescript-fetch';
import type Highcharts from 'highcharts';
import { compactNum, hexToRgba } from '@/utils/chartTheme';
import { useMeasurementSystem } from '@/composables/useMeasurementSystem';
import { useMediaQuery } from '@/composables/useMediaQuery';
import { getMediaTrends } from '@/repositories/mediaRepository';
import { isAbortLikeError } from '@/utils/errors';

const MOBILE_STATS_BP = 768;
const CHART_REFLOW_DELAY_MS = 80;

type StatsView = 'table' | 'charts';
type StatisticsTab = 'overview' | 'stats' | 'tracks';
type StatisticsNavigationState = {
  tab: StatisticsTab;
  trackQuickView: TrackBrowserPreset;
  trackBrowserState?: unknown;
};
type TrackBrowserViewRef = {
  getNavigationState: () => unknown;
  restoreNavigationState: (state: unknown) => void;
};
type ChartComponent = { chart?: Highcharts.Chart };
type ExtendedGpsTrackStatistics = GpsTrackStatistics & {
  explorationScoreAvg?: number;
  intensityIndexAvg?: number;
  normalizedPowerMed?: number;
  trainingLoadPerRideAvg?: number;
};
type TrendChartPeriod = {
  key: string;
  label: string;
  statistics: ExtendedGpsTrackStatistics | null;
  media: MediaTrendBucketDto | null;
};
type TrendTableRow = ExtendedGpsTrackStatistics & {
  imageCount: number;
  videoCount: number;
  undatedMedia?: boolean;
};
type SmartValueColumn = {
  field: 'totalTrackDurationSecs' | 'trackDurationSecsMed' | 'trackLengthInMeterSum' | 'trackLengthInMeterMed';
  header: string;
  kind: 'duration' | 'distance';
};
type MutableChartOptions = Highcharts.Options & {
  xAxis: Highcharts.XAxisOptions & { categories?: string[] };
  series: Array<{ data?: number[] }>;
};
type MutableMediaChartOptions = Highcharts.Options & {
  xAxis: Highcharts.XAxisOptions & { categories?: string[] };
  series: Array<{ data?: number[] }>;
};
type StatisticsTooltipContext = Highcharts.Point & { category?: string };
type MediaTooltipContext = Highcharts.Point & { points?: Highcharts.Point[] };
type Emits = {
  (event: 'tool-opened'): void;
  (event: 'tool-closed'): void;
  (event: 'select-track', trackId: number | string): void;
  (event: 'open-details', trackId: number | string): void;
  (event: 'open-track-photos', trackId: number): void;
  (event: 'open-media-on-map', target: { id: number; lat: number; lng: number }): void;
  (event: 'open-filter'): void;
};

defineOptions({ name: 'Statistics' });

const props = withDefaults(
  defineProps<{
    tracks?: GpsTrack[];
    tracksCount?: number;
    unfilteredTotal?: number;
    selectedTrackId?: number | string | null;
  }>(),
  {
    tracks: () => [],
    tracksCount: undefined,
    unfilteredTotal: undefined,
    selectedTrackId: null,
  }
);

const emit = defineEmits<Emits>();
const filterStore = useFilterStore();
const { measurementSystem } = useMeasurementSystem();

function cssToken(name: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim();
}

function buildStatChart(
  seriesName: string,
  seriesColorToken: string,
  unit: string,
  tooltipFormatter: (v: number) => string,
  axisFormatter?: (v: number) => string
): Highcharts.Options {
  const textColor = cssToken('--chart-text');
  const gridColor = cssToken('--chart-grid');
  const tooltipBg = cssToken('--chart-tooltip-bg');
  const tooltipText = cssToken('--chart-tooltip-text');
  const borderColor = cssToken('--border-default');
  const seriesColor = cssToken(seriesColorToken);

  return markRaw({
    chart: {
      type: 'column',
      backgroundColor: 'transparent',
      spacing: [4, 10, 10, 10],
      style: { fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif" },
    },
    title: { text: undefined },
    credits: { enabled: false },
    legend: { enabled: false },
    xAxis: {
      categories: [] as string[],
      labels: { style: { color: textColor, fontSize: '12px' } },
      lineColor: gridColor,
      tickColor: 'transparent',
    },
    yAxis: {
      gridLineColor: gridColor,
      title: { text: undefined },
      labels: {
        style: { color: textColor, fontSize: '12px' },
        formatter(this: Highcharts.AxisLabelsFormatterContextObject) {
          if (axisFormatter) return axisFormatter(this.value as number);
          return this.isLast && unit
            ? compactNum(this.value as number) + '\u202f' + unit
            : compactNum(this.value as number);
        },
      },
    },
    tooltip: {
      backgroundColor: tooltipBg,
      borderColor: borderColor,
      borderRadius: 8,
      borderWidth: 1,
      shadow: false,
      style: { color: tooltipText, fontSize: '12px' },
      formatter(this: StatisticsTooltipContext) {
        return `<b>${this.category ?? this.x}</b><br/>${this.series.name}: ${tooltipFormatter(this.y as number)}`;
      },
    },
    plotOptions: {
      column: {
        color: {
          linearGradient: { x1: 0, y1: 0, x2: 0, y2: 1 },
          stops: [
            [0, hexToRgba(seriesColor, 0.85)],
            [1, hexToRgba(seriesColor, 0.65)],
          ],
        },
        borderRadius: 3,
        borderWidth: 0,
        dataLabels: { enabled: false },
        states: { hover: { brightness: 0.1 } },
      },
    },
    series: [{ name: seriesName, data: [] as number[] }],
    accessibility: { enabled: false },
    responsive: {
      rules: [{ condition: { maxWidth: 500 }, chartOptions: { chart: { spacing: [4, 2, 6, 2] } } }],
    },
  }) as unknown as Highcharts.Options;
}

function buildMediaTrendChart(): Highcharts.Options {
  const textColor = cssToken('--chart-text');
  const gridColor = cssToken('--chart-grid');
  const tooltipBg = cssToken('--chart-tooltip-bg');
  const tooltipText = cssToken('--chart-tooltip-text');
  const borderColor = cssToken('--border-default');
  const imageColor = cssToken('--chart-series-3');
  const videoColor = cssToken('--accent');

  return markRaw({
    chart: {
      type: 'column',
      backgroundColor: 'transparent',
      spacing: [4, 10, 10, 10],
      style: { fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif" },
    },
    title: { text: undefined },
    credits: { enabled: false },
    legend: {
      enabled: true,
      itemStyle: { color: textColor, fontSize: '12px', fontWeight: '500' },
      itemHoverStyle: { color: textColor },
    },
    xAxis: {
      categories: [] as string[],
      labels: { style: { color: textColor, fontSize: '12px' } },
      lineColor: gridColor,
      tickColor: 'transparent',
    },
    yAxis: {
      allowDecimals: false,
      gridLineColor: gridColor,
      min: 0,
      title: { text: undefined },
      labels: {
        style: { color: textColor, fontSize: '12px' },
        formatter(this: Highcharts.AxisLabelsFormatterContextObject) {
          return compactNum(this.value as number);
        },
      },
      stackLabels: { enabled: false },
    },
    tooltip: {
      shared: true,
      backgroundColor: tooltipBg,
      borderColor,
      borderRadius: 8,
      borderWidth: 1,
      shadow: false,
      style: { color: tooltipText, fontSize: '12px' },
      formatter(this: MediaTooltipContext) {
        const rows = (this.points ?? [])
          .map((point) => `${point.series.name}: <b>${Number(point.y ?? 0).toLocaleString()}</b>`)
          .join('<br/>');
        return `<b>${this.x ?? ''}</b><br/>${rows}`;
      },
    },
    plotOptions: {
      column: {
        stacking: 'normal',
        borderRadius: 2,
        borderWidth: 0,
        cursor: 'pointer',
        dataLabels: { enabled: false },
        states: { hover: { brightness: 0.1 } },
      },
      series: {
        point: {
          events: {
            click(this: Highcharts.Point) {
              openMediaBucketByIndex(this.index);
            },
          },
        },
      },
    },
    series: [
      { type: 'column', name: 'Photos', color: imageColor, data: [] as number[] },
      { type: 'column', name: 'Videos', color: videoColor, data: [] as number[] },
    ],
    accessibility: { enabled: false },
    responsive: {
      rules: [{ condition: { maxWidth: 500 }, chartOptions: { chart: { spacing: [4, 2, 6, 2] } } }],
    },
  });
}

const pad = (n: number) => n.toString().padStart(2, '0');
function fmtHours(hours: number): string {
  const h = Math.floor(hours);
  const m = Math.floor((hours - h) * 60);
  return `${pad(h)}h ${pad(m)}m`;
}

const INFO_PERIODS =
  'The number of time periods with recorded activity in the selected grouping (e.g. quarters or months).';
const INFO_TRACKS = 'Total number of GPS tracks across all periods.';
const INFO_DISTANCE = 'Cumulative GPS-measured distance across all periods.';
const INFO_DURATION = 'Cumulative active duration (moving time) across all periods.';
const INFO_ENERGY =
  'Estimated external mechanical work from GPS-derived physics (Wh): climbing, drag, rolling/friction, and acceleration. It is not metabolic calorie burn and not measured power-sensor data.';
const INFO_AVG_POWER =
  'Estimated average external mechanical power from the same GPS-derived energy model. Treat it as a physics estimate, not as recorded power-meter data.';
const INFO_NORMALIZED_POWER =
  'Normalized Power (NP) is computed from estimated mechanical power using a 30 s rolling window and fourth-power weighting. It is useful for comparing effort patterns, but it is not a power-meter measurement. Also known as: Weighted Average Power (Strava), Normalized Power / NP (Garmin, TrainingPeaks), xPower / IsoPower (GoldenCheetah).';
const INFO_INTENSITY_INDEX =
  'Intensity Index = estimated NP ÷ your threshold power. 1.0 ≈ all-out 1 h effort if estimated power matches your real power.';
const INFO_TRAINING_LOAD =
  'Training Load per ride = (estimated NP ÷ threshold)² × moving hours × 100. It scales duration and intensity, but inherits the limits of the estimated mechanical-power model.';
const STATISTICS_ISO_WEEK_GROUPING = 'IYYY-"W"IW';
const EXPLORATION_CORRIDOR_WIDTH_M = 25;
const INFO_EXPLORATION = computed(
  () =>
    `Average share of each track covering ground not visited before (within a ${formatDistance(EXPLORATION_CORRIDOR_WIDTH_M, 0)} grid). Calculated as a background job after indexing — may take a moment to appear.`
);

const active = ref(false);
const showMenu = ref(false);
const activeTab = ref<StatisticsTab>('overview');
const statsView = ref<StatsView>('charts');
const trackBrowserResetKey = ref(0);
const trackQuickView = ref<TrackBrowserPreset>('all');
const statisticData = ref<ExtendedGpsTrackStatistics[]>([]);
const hasLoadedStatistics = ref(false);
const statisticsError = ref('');
const overviewRetryRevision = ref(0);
const statisticsErrorMessage = computed(() =>
  hasLoadedStatistics.value
    ? 'Statistics could not be refreshed. Showing saved data.'
    : 'Statistics could not be loaded.'
);
const currentInfoText = ref('');
const selectedGrouping = ref('YYYY-"Q"Q');
const selectedSubUnit = ref<string | null>(null);
const MEDIA_SCOPE_MATCHED = MediaTrendRequestScopeEnum.MatchedActivities;
const MEDIA_SCOPE_ALL = MediaTrendRequestScopeEnum.AllIndexed;
const MEDIA_SCOPE_TOOLTIP_DELAY_MS = 350;
const MEDIA_SCOPE_FOCUS_TOOLTIP_ID = 'media-scope-focus-tooltip';
const MEDIA_SCOPE_ALL_TOOLTIP =
  'Every indexed photo and video is shown. Activity filters do not reduce the media totals.';
const MEDIA_SCOPE_MATCHED_TOOLTIP = 'Only media linked to activities in the current track filters is shown.';
const mediaTrendScope = ref<MediaScope>(MEDIA_SCOPE_ALL);
const focusedMediaTrendScope = ref<MediaScope | null>(null);
const focusedMediaScopeTooltip = computed((): string => {
  if (focusedMediaTrendScope.value === MEDIA_SCOPE_ALL) return MEDIA_SCOPE_ALL_TOOLTIP;
  if (focusedMediaTrendScope.value === MEDIA_SCOPE_MATCHED) return MEDIA_SCOPE_MATCHED_TOOLTIP;
  return '';
});
const mediaTrendBuckets = ref<MediaTrendBucketDto[]>([]);
const mediaTrendLoading = ref(false);
const mediaTrendError = ref('');
const indexedPhotoCount = ref<number | null>(null);
const indexedVideoCount = ref<number | null>(null);
const indexedMediaCount = computed(() => {
  if (indexedPhotoCount.value == null || indexedVideoCount.value == null) return null;
  return indexedPhotoCount.value + indexedVideoCount.value;
});
const resolvedTrendTrackIds = ref<number[]>([]);
const selectedMediaBucket = ref<MediaTrendBucketDto | null>(null);
const mediaMosaicVisible = ref(false);
let statisticsRequestGeneration = 0;
let statisticsAbortController: AbortController | null = null;
const statisticGroupings = [
  { name: 'YYYY (by year)', code: 'YYYY' },
  { name: 'YYYY-Q (by year and quarter)', code: 'YYYY-"Q"Q' },
  { name: 'YYYY-MM (by year and month)', code: 'YYYY-MM' },
  { name: 'YYYY-WW (by year and week)', code: STATISTICS_ISO_WEEK_GROUPING },
  { name: 'YYYY-MM-DD (by year, week and day)', code: 'YYYY-MM-DD' },
  { name: 'Total', code: 'TOTAL' },
];
const smartValueColumns: SmartValueColumn[] = [
  { field: 'totalTrackDurationSecs', header: 'Duration', kind: 'duration' },
  { field: 'trackDurationSecsMed', header: 'Avg Duration', kind: 'duration' },
  { field: 'trackLengthInMeterSum', header: 'Distance', kind: 'distance' },
  { field: 'trackLengthInMeterMed', header: 'Avg Dist.', kind: 'distance' },
];
const chartOptionsDuration = shallowRef(buildStatChart('Duration', '--chart-series-1', 'h', (v) => fmtHours(v)));
const chartOptionsDistance = shallowRef(
  buildStatChart('Distance', '--chart-series-2', '', formatDistanceSmartUtil, formatDistanceSmartUtil)
);
const chartOptionsActivity = shallowRef(buildStatChart('Tracks', '--info', '', (v) => Math.round(v).toString()));
const chartOptionsMedia = shallowRef(buildMediaTrendChart());
const chartOptionsEnergy = shallowRef(
  buildStatChart('Mechanical Energy', '--chart-series-3', 'Wh', (v) => formatLocaleNumber(Math.round(v)) + ' Wh')
);
const chartOptionsIntensityIndex = shallowRef(buildStatChart('Intensity Index', '--error', '', (v) => v.toFixed(2)));
const chartOptionsTrainingLoad = shallowRef(
  buildStatChart('Training Load', '--accent-text-light', '', (v) => v.toFixed(0))
);
const chartOptionsExploration = shallowRef(buildStatChart('Exploration', '--success', '%', (v) => v.toFixed(1) + '%'));

const chartDuration = ref<ChartComponent | null>(null);
const chartDistance = ref<ChartComponent | null>(null);
const chartActivity = ref<ChartComponent | null>(null);
const chartMedia = ref<ChartComponent | null>(null);
const mediaTrendCard = ref<HTMLElement | null>(null);
const chartEnergy = ref<ChartComponent | null>(null);
const chartIntensityIndex = ref<ChartComponent | null>(null);
const chartTrainingLoad = ref<ChartComponent | null>(null);
const chartExploration = ref<ChartComponent | null>(null);
const infoPopover = ref<{ toggle: (event: Event) => void } | null>(null);
const trackBrowserView = ref<TrackBrowserViewRef | null>(null);

const isMobile = useMediaQuery(`(max-width: ${MOBILE_STATS_BP - 1}px)`);

const trackQuickViewOptions: TrackBrowserOption<TrackBrowserPreset>[] = [
  { label: 'All', value: 'all' },
  { label: 'Excluded', value: 'highlight-exclusions' },
  { label: 'Stats excluded', value: 'statistics-exclusions' },
  { label: 'No activity', value: 'missing-activity' },
];
const trackOverrides = shallowRef<Map<number, GpsTrack>>(new Map());
const statisticsTracks = computed(() => {
  if (trackOverrides.value.size === 0) return props.tracks ?? [];

  const sourceTracks = props.tracks ?? [];
  return sourceTracks.map((track) => {
    const trackId = Number(track.id);
    if (!Number.isFinite(trackId)) return track;
    return trackOverrides.value.get(trackId) ?? track;
  });
});
const trackBrowserSourceTracks = computed(() => {
  const tracks = statisticsTracks.value;
  switch (trackQuickView.value) {
    case 'highlight-exclusions':
      return tracks.filter((track) => track.highlightExclusionReason || track.statisticsExclusionReason);
    case 'statistics-exclusions':
      return tracks.filter((track) => track.statisticsExclusionReason);
    case 'missing-activity':
      return tracks.filter((track) => !track.activityType);
    case 'all':
    default:
      return tracks;
  }
});

function onOverviewTrackUpdated(track: GpsTrack): void {
  const trackId = Number(track.id);
  if (!Number.isFinite(trackId)) return;
  const sourceTrack = statisticsTracks.value.find((candidate) => Number(candidate.id) === trackId);
  const nextOverrides = new Map(trackOverrides.value);
  nextOverrides.set(trackId, { ...sourceTrack, ...track });
  trackOverrides.value = nextOverrides;
}
function showNewestTracks() {
  activeTab.value = 'tracks';
  trackQuickView.value = 'all';
  trackBrowserResetKey.value += 1;
}

function showHighlightExclusions() {
  activeTab.value = 'tracks';
  trackQuickView.value = 'highlight-exclusions';
  trackBrowserResetKey.value += 1;
}

async function showMediaTrends(): Promise<void> {
  activeTab.value = 'stats';
  statsView.value = 'charts';
  selectedSubUnit.value = null;
  const requiresAllMedia = mediaTrendScope.value !== MEDIA_SCOPE_ALL;
  mediaTrendScope.value = MEDIA_SCOPE_ALL;
  if (requiresAllMedia) {
    await fetchStatistics();
  }
  await nextTick();
  mediaTrendCard.value?.scrollIntoView?.({ behavior: 'smooth', block: 'start' });
}

const mediaGrouping = computed<MediaGrouping>(() => {
  switch (selectedGrouping.value) {
    case 'YYYY':
      return MediaTrendRequestGroupingEnum.Year;
    case 'YYYY-"Q"Q':
      return MediaTrendRequestGroupingEnum.Quarter;
    case 'YYYY-MM':
      return MediaTrendRequestGroupingEnum.Month;
    case STATISTICS_ISO_WEEK_GROUPING:
      return MediaTrendRequestGroupingEnum.Week;
    case 'YYYY-MM-DD':
      return MediaTrendRequestGroupingEnum.Day;
    case 'TOTAL':
    default:
      return MediaTrendRequestGroupingEnum.Total;
  }
});

const availableSubUnits = computed((): string[] => {
  const subunits = new Set<string>();
  for (const d of statisticData.value) {
    if (d.subGroup) subunits.add(d.subGroup);
  }
  for (const bucket of mediaTrendBuckets.value) {
    if (bucket.subGroup) subunits.add(bucket.subGroup);
  }
  return Array.from(subunits).sort();
});
const statsDetents = computed((): { height: string }[] =>
  isMobile.value ? [{ height: '60vh' }, { height: '88vh' }] : [{ height: '88vh' }, { height: '98vh' }]
);
const filteredStatisticData = computed((): ExtendedGpsTrackStatistics[] => {
  if (!statisticData.value) return [];
  if (selectedSubUnit.value) {
    return statisticData.value.filter((d: ExtendedGpsTrackStatistics) => d.subGroup === selectedSubUnit.value);
  }
  return statisticData.value;
});
const filteredMediaTrendBuckets = computed((): MediaTrendBucketDto[] => {
  if (!selectedSubUnit.value) return mediaTrendBuckets.value;
  return mediaTrendBuckets.value.filter((bucket) => bucket.undated || bucket.subGroup === selectedSubUnit.value);
});
const undatedMediaBucket = computed(
  (): MediaTrendBucketDto | null => filteredMediaTrendBuckets.value.find((bucket) => bucket.undated) ?? null
);
const datedMediaTrendBuckets = computed((): MediaTrendBucketDto[] =>
  filteredMediaTrendBuckets.value.filter((bucket) => !bucket.undated && Boolean(bucket.bucketKey))
);
const chartMediaTrendBuckets = datedMediaTrendBuckets;
const mediaScopeHelp = computed((): string =>
  mediaTrendScope.value === MEDIA_SCOPE_MATCHED ? MEDIA_SCOPE_MATCHED_TOOLTIP : MEDIA_SCOPE_ALL_TOOLTIP
);
const trendTableRows = computed((): TrendTableRow[] => {
  const rows = new Map<string, TrendTableRow>();
  for (const statistics of filteredStatisticData.value) {
    const key = statistics.groupBy ?? '';
    if (!key) continue;
    rows.set(key, { ...statistics, imageCount: 0, videoCount: 0 });
  }
  for (const media of filteredMediaTrendBuckets.value) {
    const key = media.bucketKey ?? '';
    if (!key) continue;
    const existing = rows.get(key);
    rows.set(key, {
      ...existing,
      groupBy: existing?.groupBy ?? media.label ?? key,
      subGroup: existing?.subGroup ?? media.subGroup,
      imageCount: media.imageCount ?? 0,
      videoCount: media.videoCount ?? 0,
      undatedMedia: media.undated ?? false,
    });
  }
  return Array.from(rows.values()).sort((left, right) => {
    const leftUndated = left.undatedMedia === true;
    const rightUndated = right.undatedMedia === true;
    if (leftUndated !== rightUndated) return leftUndated ? 1 : -1;
    return (left.groupBy ?? '').localeCompare(right.groupBy ?? '');
  });
});
const trendChartPeriods = computed((): TrendChartPeriod[] => {
  const periods = new Map<string, TrendChartPeriod>();
  for (const statistics of filteredStatisticData.value) {
    const key = statistics.groupBy ?? '';
    if (!key) continue;
    periods.set(key, { key, label: key, statistics, media: null });
  }
  for (const media of chartMediaTrendBuckets.value) {
    const key = media.bucketKey ?? '';
    if (!key) continue;
    const existing = periods.get(key);
    periods.set(key, {
      key,
      label: existing?.label || media.label || key,
      statistics: existing?.statistics ?? null,
      media,
    });
  }
  return Array.from(periods.values()).sort((left, right) => left.key.localeCompare(right.key));
});
const displayedMediaTrendBuckets = computed((): MediaTrendBucketDto[] =>
  trendChartPeriods.value.map(
    (period): MediaTrendBucketDto =>
      period.media ?? {
        bucketKey: period.key,
        label: period.label,
        subGroup: period.statistics?.subGroup,
        undated: false,
        imageCount: 0,
        videoCount: 0,
      }
  )
);
const distColMaxM = computed((): number => {
  const data = filteredStatisticData.value;
  return Math.max(
    0,
    ...data.map((d: GpsTrackStatistics) => d.trackLengthInMeterSum ?? 0),
    ...data.map((d: GpsTrackStatistics) => d.trackLengthInMeterMed ?? 0)
  );
});
const durColMaxMs = computed((): number => {
  const data = filteredStatisticData.value;
  return Math.max(
    0,
    ...data.map((d: GpsTrackStatistics) => (d.totalTrackDurationSecs ?? 0) * 1000),
    ...data.map((d: GpsTrackStatistics) => (d.trackDurationSecsMed ?? 0) * 1000)
  );
});

function smartValue(row: ExtendedGpsTrackStatistics, column: SmartValueColumn): number {
  const value = Number(row[column.field]);
  return Number.isFinite(value) ? value : 0;
}

function smartValueTooltip(row: ExtendedGpsTrackStatistics, column: SmartValueColumn): string {
  const value = smartValue(row, column);
  return column.kind === 'duration' ? formatDurationTooltip(value * 1000) : formatDistanceTooltip(value);
}

function formatSmartValue(row: ExtendedGpsTrackStatistics, column: SmartValueColumn): string {
  const value = smartValue(row, column);
  return column.kind === 'duration'
    ? formatDurationSmart(value * 1000, durColMaxMs.value)
    : formatDistanceSmart(value, distColMaxM.value);
}

const summaryStats = computed(
  (): {
    periods: number;
    tracks: number;
    distance: string;
    distanceFull: string;
    duration: string;
    durationFull: string;
    energy: string;
    hasEnergy: boolean;
    hasFitness: boolean;
    hasExploration: boolean;
  } => {
    const data = filteredStatisticData.value;
    const totalTracks = data.reduce((s: number, d: GpsTrackStatistics) => s + (d.numberOfTracks ?? 0), 0);
    const totalDistM = data.reduce((s: number, d: GpsTrackStatistics) => s + (d.trackLengthInMeterSum ?? 0), 0);
    const totalDurSecs = data.reduce((s: number, d: GpsTrackStatistics) => s + (d.totalTrackDurationSecs ?? 0), 0);
    const totalEnergy = data.reduce((s: number, d: GpsTrackStatistics) => s + (d.energyNetTotalWhSum ?? 0), 0);
    const hasExploration = data.some(
      (d: ExtendedGpsTrackStatistics) => d.explorationScoreAvg != null && d.explorationScoreAvg > 0
    );
    const hasFitness = data.some(
      (d: ExtendedGpsTrackStatistics) =>
        (d.normalizedPowerMed ?? 0) > 0 || (d.intensityIndexAvg ?? 0) > 0 || (d.trainingLoadPerRideAvg ?? 0) > 0
    );
    return {
      periods: data.length,
      tracks: totalTracks,
      distance: formatDistanceSmartUtil(totalDistM),
      distanceFull: formatDistanceTooltipUtil(totalDistM),
      duration: formatDurationSmartUtil(totalDurSecs * 1000),
      durationFull: formatDurationTooltipUtil(totalDurSecs * 1000),
      energy: formatLocaleNumber(Math.round(totalEnergy)) + ' Wh',
      hasEnergy: totalEnergy > 0,
      hasFitness,
      hasExploration,
    };
  }
);
const chartMetricAvailability = computed(() => ({
  hasEnergy: statisticData.value.some((statistics) => (statistics.energyNetTotalWhSum ?? 0) > 0),
  hasFitness: statisticData.value.some(
    (statistics) =>
      (statistics.normalizedPowerMed ?? 0) > 0 ||
      (statistics.intensityIndexAvg ?? 0) > 0 ||
      (statistics.trainingLoadPerRideAvg ?? 0) > 0
  ),
  hasExploration: statisticData.value.some(
    (statistics) => statistics.explorationScoreAvg != null && statistics.explorationScoreAvg > 0
  ),
}));

watch(trendChartPeriods, (periods) => {
  updateCharts(periods);
  updateMediaChart(displayedMediaTrendBuckets.value);
});
watch(measurementSystem, () => updateCharts(trendChartPeriods.value));
watch(selectedGrouping, () => {
  selectedSubUnit.value = null;
  closeMediaMosaic();
});
watch(
  () => filterStore.trackSetRevision,
  () => {
    if (active.value) void fetchStatistics();
  }
);
watch(statsView, (newVal) => {
  if (newVal === 'charts') {
    void nextTick(() => {
      setTimeout(() => {
        updateCharts(trendChartPeriods.value);
        updateMediaChart(displayedMediaTrendBuckets.value);
        for (const chartRef of chartRefs()) {
          chartRef.value?.chart?.reflow();
        }
        requestAnimationFrame(() => syncChartMargins());
      }, CHART_REFLOW_DELAY_MS);
    });
  }
});

async function toggle() {
  showMenu.value = !showMenu.value;
  active.value = !active.value;
  if (active.value) {
    emit('tool-opened');
    await fetchStatistics();
  } else {
    cancelStatisticsRequest();
    closeMediaMosaic();
  }
}

async function open() {
  showMenu.value = true;
  if (!active.value) {
    active.value = true;
    emit('tool-opened');
    await fetchStatistics();
  }
}

function close() {
  showMenu.value = false;
  active.value = false;
  cancelStatisticsRequest();
  closeMediaMosaic();
}

function getNavigationState(): StatisticsNavigationState {
  return {
    tab: activeTab.value,
    trackQuickView: trackQuickView.value,
    trackBrowserState: trackBrowserView.value?.getNavigationState(),
  };
}

function restoreNavigationState(state: unknown) {
  if (!state || typeof state !== 'object' || !('tab' in state)) return;
  const tab = state.tab;
  if (tab === 'overview' || tab === 'stats' || tab === 'tracks') {
    activeTab.value = tab;
  }
  if (
    'trackQuickView' in state &&
    typeof state.trackQuickView === 'string' &&
    trackQuickViewOptions.some((option) => option.value === state.trackQuickView)
  ) {
    trackQuickView.value = state.trackQuickView;
  }
  if ('trackBrowserState' in state) {
    void nextTick(() => trackBrowserView.value?.restoreNavigationState(state.trackBrowserState));
  }
}

function onSheetClosed() {
  active.value = false;
  cancelStatisticsRequest();
  closeMediaMosaic();
  emit('tool-closed');
}

function cancelStatisticsRequest(): void {
  statisticsRequestGeneration++;
  statisticsAbortController?.abort();
  statisticsAbortController = null;
  mediaTrendLoading.value = false;
}

async function fetchStatistics() {
  const generation = ++statisticsRequestGeneration;
  const requestedMediaScope = mediaTrendScope.value;
  statisticsAbortController?.abort();
  const controller = new AbortController();
  statisticsAbortController = controller;
  mediaTrendLoading.value = true;
  statisticsError.value = '';
  mediaTrendError.value = '';
  mediaTrendBuckets.value = [];
  closeMediaMosaic();

  try {
    const filterRequest = await currentFilterRequest();
    const trackIds = await resolveStatisticsTrackIds(filterRequest, controller.signal);
    if (generation !== statisticsRequestGeneration || controller.signal.aborted) return;
    resolvedTrendTrackIds.value = trackIds;

    const [statisticsResult, mediaResult] = await Promise.allSettled([
      fetchStatisticsForTrackIds(selectedGrouping.value, trackIds, controller.signal),
      getMediaTrends(
        {
          grouping: mediaGrouping.value,
          scope: requestedMediaScope,
          trackIds: requestedMediaScope === MEDIA_SCOPE_MATCHED ? trackIds : undefined,
        },
        controller.signal
      ),
    ]);
    if (generation !== statisticsRequestGeneration || controller.signal.aborted) return;

    if (statisticsResult.status === 'fulfilled') {
      statisticData.value = statisticsResult.value ?? [];
      hasLoadedStatistics.value = true;
    } else if (!isAbortLikeError(statisticsResult.reason, controller.signal)) {
      statisticsError.value =
        statisticsResult.reason instanceof Error ? statisticsResult.reason.message : String(statisticsResult.reason);
    }
    if (mediaResult.status === 'fulfilled') {
      mediaTrendBuckets.value = mediaResult.value.buckets ?? [];
      if (requestedMediaScope === MEDIA_SCOPE_ALL) {
        indexedPhotoCount.value = mediaTrendBuckets.value.reduce(
          (total, bucket) => total + (bucket.imageCount ?? 0),
          0
        );
        indexedVideoCount.value = mediaTrendBuckets.value.reduce(
          (total, bucket) => total + (bucket.videoCount ?? 0),
          0
        );
      }
    } else if (!isAbortLikeError(mediaResult.reason, controller.signal)) {
      mediaTrendBuckets.value = [];
      mediaTrendError.value =
        mediaResult.reason instanceof Error ? mediaResult.reason.message : String(mediaResult.reason);
    }
  } catch (error) {
    if (generation !== statisticsRequestGeneration || isAbortLikeError(error, controller.signal)) return;
    statisticsError.value = error instanceof Error ? error.message : String(error);
    mediaTrendBuckets.value = [];
    mediaTrendError.value = error instanceof Error ? error.message : String(error);
  } finally {
    if (generation === statisticsRequestGeneration) mediaTrendLoading.value = false;
  }
}

async function retryStatistics(): Promise<void> {
  overviewRetryRevision.value++;
  await fetchStatistics();
}

function setMediaTrendScope(scope: MediaScope): void {
  if (mediaTrendScope.value === scope) return;
  mediaTrendScope.value = scope;
  closeMediaMosaic();
  void fetchStatistics();
}

function clearFocusedMediaTrendScope(scope: MediaScope): void {
  if (focusedMediaTrendScope.value === scope) focusedMediaTrendScope.value = null;
}

function closeMediaMosaic(): void {
  mediaMosaicVisible.value = false;
  selectedMediaBucket.value = null;
}

async function currentFilterRequest(): Promise<ActiveFilterRequest> {
  return filterStore.activeFilterRequest ?? (await filterStore.getActiveFilterRequest());
}

function chartRefs(): Array<Ref<ChartComponent | null>> {
  return [
    chartDuration,
    chartDistance,
    chartActivity,
    chartEnergy,
    chartIntensityIndex,
    chartTrainingLoad,
    chartExploration,
    chartMedia,
  ];
}

function updateMediaChart(buckets: MediaTrendBucketDto[]): void {
  const options = chartOptionsMedia.value as MutableMediaChartOptions;
  options.xAxis.categories = buckets.map((bucket) => bucket.label ?? '');
  options.series[0].data = buckets.map((bucket) => bucket.imageCount ?? 0);
  options.series[1].data = buckets.map((bucket) => bucket.videoCount ?? 0);
  if (chartMedia.value?.chart) chartMedia.value.chart.update(options, true, true);
  void nextTick(() => requestAnimationFrame(() => syncChartMargins()));
}

function openMediaBucketByIndex(index: number): void {
  const bucket = displayedMediaTrendBuckets.value[index];
  if (!bucket?.bucketKey || mediaBucketCount(bucket) === 0) return;
  openMediaBucket(bucket);
}

function openMediaBucket(bucket: MediaTrendBucketDto): void {
  selectedMediaBucket.value = bucket;
  mediaMosaicVisible.value = true;
}

function mediaBucketCount(bucket: MediaTrendBucketDto): number {
  return (bucket.imageCount ?? 0) + (bucket.videoCount ?? 0);
}

function setChart(
  chartRef: Ref<ChartComponent | null>,
  optsRef: ShallowRef<Highcharts.Options>,
  categories: string[],
  values: number[]
) {
  const opts = optsRef.value as MutableChartOptions;
  opts.xAxis.categories = categories;
  opts.series[0].data = values;
  const cmp = chartRef.value;
  if (cmp?.chart) cmp.chart.update(opts, true, true);
}

function updateCharts(periods: TrendChartPeriod[]) {
  const categories = periods.map((period) => period.label);
  const data = periods.map((period) => period.statistics);

  setChart(
    chartDuration,
    chartOptionsDuration,
    categories,
    data.map((o) => parseFloat(((o?.totalTrackDurationSecs ?? 0) / 3600).toFixed(2)))
  );
  setChart(
    chartDistance,
    chartOptionsDistance,
    categories,
    data.map((o) => o?.trackLengthInMeterSum ?? 0)
  );
  setChart(
    chartActivity,
    chartOptionsActivity,
    categories,
    data.map((o) => o?.numberOfTracks ?? 0)
  );
  setChart(
    chartEnergy,
    chartOptionsEnergy,
    categories,
    data.map((o) => o?.energyNetTotalWhSum ?? 0)
  );
  setChart(
    chartIntensityIndex,
    chartOptionsIntensityIndex,
    categories,
    data.map((o) => parseFloat((o?.intensityIndexAvg ?? 0).toFixed(3)))
  );
  setChart(
    chartTrainingLoad,
    chartOptionsTrainingLoad,
    categories,
    data.map((o) => parseFloat((o?.trainingLoadPerRideAvg ?? 0).toFixed(1)))
  );
  setChart(
    chartExploration,
    chartOptionsExploration,
    categories,
    data.map((o) => parseFloat(((o?.explorationScoreAvg ?? 0) * 100).toFixed(1)))
  );

  // After Highcharts has re-rendered, read each chart's plotLeft and
  // apply the widest one to all charts so plot areas align perfectly.
  void nextTick(() => requestAnimationFrame(() => syncChartMargins()));
}

function syncChartMargins() {
  const charts = chartRefs()
    .map((r) => r.value?.chart)
    .filter((chart): chart is Highcharts.Chart => Boolean(chart));
  if (charts.length === 0) return;
  const maxLeft = Math.max(...charts.map((c) => c.plotLeft as number));
  charts.forEach((c) => {
    if (c.plotLeft !== maxLeft) {
      c.update({ chart: { marginLeft: maxLeft } }, true, false);
    }
  });
}

function formatDurationSmart(millis: number, maxMillis?: number) {
  return formatDurationSmartUtil(millis, maxMillis);
}
function formatDurationTooltip(millis: number) {
  return formatDurationTooltipUtil(millis);
}
function formatDistanceSmart(meters: number, maxMeters?: number) {
  return formatDistanceSmartUtil(meters, maxMeters);
}
function formatDistanceTooltip(meters: number) {
  return formatDistanceTooltipUtil(meters);
}
function formatEnergy(value: number | null | undefined) {
  if (value == null || value === 0) return '';
  return formatLocaleNumber(Math.round(value)) + ' Wh';
}
function formatPower(value: number | null | undefined) {
  if (value == null || value === 0) return '';
  return Math.round(value) + ' W';
}
function formatMediaCount(value: number | null | undefined): string {
  return Math.max(0, Math.round(value ?? 0)).toLocaleString();
}
function showInfo(event: Event, text: string) {
  currentInfoText.value = text;
  infoPopover.value?.toggle(event);
}

defineExpose({
  active,
  open,
  toggle,
  close,
  getNavigationState,
  restoreNavigationState,
  fetchStatistics,
});
</script>

<style scoped>
/*
 * IMPORTANT: .statistics-root is the wrapper div required for :deep() selectors to work
 * inside a BottomSheet (which uses <Teleport to="body">). Without this wrapper,
 * scoped selectors like :deep(.p-tabs) have no ancestor carrying data-v-xxx
 * and won't match. See BottomSheet.vue comment about the neutral body contract.
 */

/* ── Layout roots ── */
.statistics-root {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 0;
  width: 100%;
  height: 100%;
  overflow: hidden;
}

/* stats-sheet-icon: muted identity mark, non-interactive */
.stats-header-nav {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-width: 0;
}

.stats-sheet-icon {
  font-size: var(--text-base-size);
  color: var(--text-muted);
  flex-shrink: 0;
}

.stats-header-tabs {
  display: flex;
  gap: 0.15rem;
  min-width: 0;
}

:deep(.p-tabpanel) {
  padding-top: 0.5rem;
}

.tracks-tab {
  display: flex;
  min-height: 0;
  flex: 1 1 auto;
  padding: 0;
  margin-top: -0.5rem;
}

.statistics-holder {
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  min-height: 0;
  gap: 0.5rem;
  padding: 0;
}

.statistics-refresh-state {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  margin: 0 1rem;
  padding: 0.55rem 0.7rem;
  border: 1px solid var(--warning-border, var(--border-default));
  border-radius: 0.5rem;
  background: var(--warning-soft, var(--surface-elevated));
  color: var(--text-primary);
  font-size: var(--text-sm-size);
}

.statistics-refresh-state button {
  flex: 0 0 auto;
  border: 1px solid var(--border-default);
  border-radius: 999px;
  background: var(--surface-elevated);
  color: var(--accent);
  cursor: pointer;
  font: inherit;
  padding: 0.35rem 0.65rem;
}

/* ── Controls bar ── */
.stats-controls {
  padding: 0 1rem;
}
.stats-controls__row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}
.stats-controls__select-wrap {
  position: relative;
  display: flex;
  align-items: center;
  flex: 1 1 180px;
  min-width: 0;
}
.stats-controls__icon {
  position: absolute;
  left: 0.65rem;
  z-index: 1;
  color: var(--text-muted);
  font-size: var(--text-sm-size);
  pointer-events: none;
}
.stats-select {
  width: 100%;
}
/* nudge the PrimeVue Select input text to the right of the icon */
.stats-controls__select-wrap :deep(.p-select-label) {
  padding-left: 2rem;
}

/* ── Summary tiles ── */
.stats-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  padding: 0 1rem;
}
.stat-tile {
  flex: 1 1 70px;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  background: transparent;
  border: 1px solid var(--border-default);
  border-radius: 10px;
  padding: 0.55rem 0.4rem 0.45rem;
  gap: 0.15rem;
  transition: background 0.15s;
}

.stat-tile__icon {
  font-size: var(--text-base-size);
  line-height: var(--text-base-lh);
}
.stat-tile__value {
  font-size: var(--text-sm-size);
  font-weight: 700;
  color: var(--text-primary);
  line-height: var(--text-sm-lh);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
  text-align: center;
}
.stat-tile__label {
  font-size: var(--text-2xs-size);
  font-weight: 500;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.03em;
  white-space: nowrap;
  display: flex;
  align-items: center;
  gap: 0;
}

/* ── Table section (replaces tabs) ── */
.table-section {
  flex: 1 1 auto;
  min-height: 0;
  min-width: 0;
  overflow: auto;
  -webkit-overflow-scrolling: touch;
  touch-action: pan-x pan-y;
}

/* horizontal scroll only for wide tables */
.table-scroll-x {
  flex: 1 1 auto;
  min-width: 0;
  min-height: 0;
  overflow-x: auto;
  overflow-y: visible;
  -webkit-overflow-scrolling: touch;
  touch-action: pan-x;
}

/* ── DataTable theming ── */
/* Colors/typography handled globally in main.css; only structural overrides here */
.statistics-table :deep(.p-datatable-wrapper) {
  overflow: visible;
}
.statistics-table :deep(.p-datatable-thead) {
  position: sticky;
  top: 0;
  z-index: 2;
}
.statistics-table :deep(.number-column) {
  text-align: right;
}

/* ── Table / Charts toggle ── */
.stats-view-toggle {
  flex: 0 0 auto;
}
/* ── Chart cards ── */
.charts-scroll {
  width: 100%;
  display: flex;
  flex-direction: column;
  padding: 0 0 0.75rem;
}
.chart-card {
  display: flex;
  flex-direction: column;
  width: 100%;
  padding-bottom: 1rem;
}
.chart-card:last-child {
  padding-bottom: 0;
}
.chart-header {
  padding: 1.25rem 1rem 0.6rem;
}
.stat-chart {
  width: 100%;
  height: 190px;
}

.media-trend-header {
  position: relative;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.6rem 1rem;
}

.media-trend-focus-tooltip {
  position: absolute;
  z-index: 10;
  top: calc(100% - 0.2rem);
  right: 1rem;
  max-width: min(30rem, calc(100% - 2rem));
  padding: 0.45rem 0.6rem;
  border: 1px solid var(--border-default);
  border-radius: 0.4rem;
  background: var(--surface-sheet-solid);
  box-shadow: 0 0.25rem 0.75rem color-mix(in srgb, var(--text-primary) 18%, transparent);
  color: var(--text-primary);
  font-size: var(--text-xs-size);
  line-height: 1.35;
}

.media-trend-header__title {
  display: flex;
  align-items: center;
  gap: 0.35rem;
}

.media-trend-mode {
  display: inline-flex;
  padding: 2px;
  border: 1px solid var(--border-default);
  border-radius: 999px;
  background: var(--surface-ground);
}

.media-trend-mode button {
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  font: inherit;
  font-size: var(--text-xs-size);
  padding: 0.35rem 0.65rem;
}

.media-trend-mode .media-trend-mode__button--active {
  background: var(--surface-elevated);
  color: var(--text-primary);
  box-shadow: 0 1px 3px color-mix(in srgb, var(--text-primary) 14%, transparent);
  font-weight: 650;
}

.media-trend-notice {
  margin: 0;
  padding: 0 1rem 0.35rem;
  color: var(--text-muted);
  font-size: var(--text-xs-size);
}

.media-trend-state {
  display: flex;
  min-height: 8rem;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  color: var(--text-muted);
  font-size: var(--text-sm-size);
}

.media-trend-state button {
  border: 1px solid var(--border-default);
  border-radius: 999px;
  background: var(--surface-elevated);
  color: var(--accent);
  cursor: pointer;
  font: inherit;
  padding: 0.35rem 0.65rem;
}

.media-trend-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
  margin: 0 1rem 0.75rem;
}

.media-trend-badge {
  display: inline-flex;
  align-items: center;
  gap: 0.55rem;
  padding: 0.35rem 0.65rem;
  color: var(--text-muted);
  background: var(--surface-ground);
  border: 1px solid var(--border-default);
  border-radius: 999px;
  font: inherit;
  font-size: var(--text-xs-size);
  cursor: pointer;
}

.media-trend-badge:hover,
.media-trend-badge:focus-visible {
  color: var(--text-primary);
  background: var(--surface-elevated);
  border-color: var(--border-hover);
}

.media-trend-badge:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

.media-trend-badge strong {
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
}

/* ── Mobile tweaks ── */
@media (max-width: 600px) {
  /* Controls: less padding */
  .stats-controls {
    padding: 0 0.75rem;
  }

  /* Holder: tighter rhythm */
  .statistics-holder {
    gap: 0.5rem;
  }

  /* Summary → horizontal scrollable pill strip (no wrap = ~80px saved) */
  .stats-summary {
    flex-wrap: nowrap;
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
    gap: 0.4rem;
    padding: 0 0.75rem 0.2rem;
    scrollbar-width: none;
  }
  .stats-summary::-webkit-scrollbar {
    display: none;
  }

  /* Tile becomes a compact horizontal pill */
  .stat-tile {
    flex: 0 0 auto;
    flex-direction: row;
    align-items: center;
    padding: 0.3rem 0.65rem;
    gap: 0.25rem;
    border-radius: 999px;
  }
  .stat-tile__icon {
    font-size: var(--text-sm-size);
  }
  .stat-tile__value {
    font-size: var(--text-sm-size);
  }
  .stat-tile__label {
    font-size: var(--text-xs-size);
    font-weight: 500;
    text-transform: none;
    letter-spacing: 0;
  }

  /* Table: compact */
  .statistics-table {
    font-size: var(--text-xs-size);
  }
  .statistics-table :deep(th),
  .statistics-table :deep(td) {
    padding: 0.4rem 0.3rem;
    white-space: nowrap;
    min-width: 0 !important;
    width: auto !important;
  }

  /* Charts: shorter so 2 are visible at once */
  .stat-chart {
    height: 185px;
  }
  .chart-header {
    padding: 0.7rem 0.75rem 0.4rem;
  }
  .media-trend-header {
    align-items: flex-start;
    flex-direction: column;
  }
  .media-trend-mode {
    width: 100%;
  }
  .media-trend-mode button {
    flex: 1 1 0;
    padding-inline: 0.55rem;
    white-space: nowrap;
  }
  .media-trend-notice {
    padding-inline: 0.75rem;
  }
  .media-trend-badges {
    margin-inline: 0.75rem;
  }
  .charts-scroll {
    padding-bottom: 0.25rem;
  }
}

@media (max-height: 500px) {
  .tool-dialog {
    height: 92vh;
    max-height: 92vh;
  }
}

/* ── Info icons ── */
.info-btn {
  vertical-align: middle;
  margin-left: 2px;
}
.info-btn--header {
  font-size: var(--text-xs-size);
  margin-left: 4px;
}
.info-btn--col {
  font-size: var(--text-2xs-size);
  margin-left: 2px;
}

/* Info popover content */
.stat-info-text {
  max-width: 240px;
}

/* ── Exploration pending state ── */
.chart-pending {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  height: 90px;
  color: var(--text-faint);
  font-size: var(--text-xs-size);
  font-style: italic;
  border: 1px dashed var(--border-default);
  border-radius: 8px;
  margin: 0 1rem 0.5rem;
}
.chart-pending i {
  font-size: var(--text-base-size);
  opacity: 0.6;
}
</style>
