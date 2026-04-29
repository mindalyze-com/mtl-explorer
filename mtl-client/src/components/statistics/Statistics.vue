<template>
  <div>
    <BottomSheet v-model="showMenu" :detents="statsDetents"
                 @closed="onSheetClosed">
      <template #title>
        <div class="stats-header-nav">
          <i class="bi bi-graph-up stats-sheet-icon"></i>
          <div class="stats-header-tabs">
            <button class="stats-header-tab" :class="{ 'stats-header-tab--active': activeTab === 'overview' }" @pointerdown.stop @click="activeTab = 'overview'">Overview</button>
            <button class="stats-header-tab" :class="{ 'stats-header-tab--active': activeTab === 'stats' }" @pointerdown.stop @click="activeTab = 'stats'">Trends</button>
            <button class="stats-header-tab" :class="{ 'stats-header-tab--active': activeTab === 'tracks' }" @pointerdown.stop @click="activeTab = 'tracks'">Tracks</button>
          </div>
        </div>
      </template>
      <div v-if="active" class="statistics-root">
        <Tabs v-model:value="activeTab">
          <TabPanels>

          <!-- ── Tab 1: Overview ── -->
          <TabPanel value="overview">
            <StatisticsOverview
              :tracks="tracks"
              :tracks-count="tracksCount"
              :unfiltered-total="unfilteredTotal"
              @open-details="$emit('open-details', $event)"
            />
          </TabPanel>

          <!-- ── Tab 2: Track Log ── -->
          <TabPanel value="tracks">
            <div class="tracks-tab">
              <TrackBrowserControls
                :query="trackQuery"
                :summary="trackFilterSummary"
                :total-count="trackTotalCount"
                @update:query="trackQuery = $event"
              />
              <TrackBrowserTable
                :rows="trackRows"
                :selected-track-id="selectedTrackId ?? null"
                :query="trackQuery"
                :compact="isMobile"
                @select-track="$emit('select-track', $event)"
                @open-details="$emit('open-details', $event)"
              />
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
                    optionLabel="name"
                    option-value="code"
                    placeholder="Aggregation level"
                    class="stats-select"
                    @change="fetchStatistics"
                    appendTo="body"
                />
              </div>
              <div v-if="availableSubUnits.length > 0" class="stats-controls__select-wrap">
                <i class="bi bi-funnel stats-controls__icon"></i>
                <Select
                    v-model="selectedSubUnit"
                    :options="availableSubUnits"
                    placeholder="All sub-units"
                    class="stats-select"
                    appendTo="body"
                    showClear
                />
              </div>
              <!-- ── Table / Charts toggle ── -->
              <div class="stats-view-toggle">
                <button :class="['toggle-btn', { 'toggle-btn--active': statsView === 'table' }]" @click="statsView = 'table'">
                  <i class="bi bi-table"></i> Table
                </button>
                <button :class="['toggle-btn', { 'toggle-btn--active': statsView === 'charts' }]" @click="statsView = 'charts'">
                  <i class="bi bi-bar-chart-line"></i> Charts
                </button>
              </div>
            </div>
          </div>

          <!-- ── Aggregate summary tiles ── -->
          <div class="stats-summary" v-if="filteredStatisticData.length > 0">
            <div class="stat-tile">
              <i class="bi bi-bar-chart-line stat-tile__icon" style="color: var(--accent)"></i>
              <div class="stat-tile__value">{{ summaryStats.periods }}</div>
              <div class="stat-tile__label">Periods
                <button class="info-btn" @click.stop="showInfo($event, INFO_PERIODS)" aria-label="About periods"><i class="bi bi-info-circle"></i></button>
              </div>
            </div>
            <div class="stat-tile">
              <i class="bi bi-pin-map stat-tile__icon" style="color: var(--chart-series-1)"></i>
              <div class="stat-tile__value">{{ summaryStats.tracks }}</div>
              <div class="stat-tile__label">Tracks
                <button class="info-btn" @click.stop="showInfo($event, INFO_TRACKS)" aria-label="About tracks"><i class="bi bi-info-circle"></i></button>
              </div>
            </div>
            <div class="stat-tile">
              <i class="bi bi-signpost-split stat-tile__icon" style="color: var(--chart-series-2)"></i>
              <div class="stat-tile__value"
                   v-tooltip.top="{ value: summaryStats.distanceFull, showDelay: 400 }">{{ summaryStats.distance }}</div>
              <div class="stat-tile__label">Total Dist.
                <button class="info-btn" @click.stop="showInfo($event, INFO_DISTANCE)" aria-label="About distance"><i class="bi bi-info-circle"></i></button>
              </div>
            </div>
            <div class="stat-tile">
              <i class="bi bi-clock stat-tile__icon" style="color: var(--info)"></i>
              <div class="stat-tile__value"
                   v-tooltip.top="{ value: summaryStats.durationFull, showDelay: 400 }">{{ summaryStats.duration }}</div>
              <div class="stat-tile__label">Total Time
                <button class="info-btn" @click.stop="showInfo($event, INFO_DURATION)" aria-label="About duration"><i class="bi bi-info-circle"></i></button>
              </div>
            </div>
            <div class="stat-tile" v-if="summaryStats.hasEnergy">
              <i class="bi bi-lightning-charge stat-tile__icon" style="color: var(--chart-series-3)"></i>
              <div class="stat-tile__value">{{ summaryStats.energy }}</div>
              <div class="stat-tile__label">Total Energy
                <button class="info-btn" @click.stop="showInfo($event, INFO_ENERGY)" aria-label="About energy"><i class="bi bi-info-circle"></i></button>
              </div>
            </div>
          </div>

          <!-- ── Data table ── -->
          <div class="table-section" v-if="statsView === 'table'">
            <div class="table-scroll-x">
              <DataTable
                  :value="filteredStatisticData"
                  columnResizeMode="fit"
                  responsiveLayout="scroll"
                  tableStyle="min-width: 10rem"
                  class="p-datatable-sm statistics-table"
              >
                <Column field="groupBy" header="Period" :sortable="true" style="min-width: 8rem" />
                <Column field="daysWithActivities" header="Days" :sortable="true" headerClass="number-column"
                        class="number-column" style="min-width: 5rem" />
                <Column field="numberOfTracks" header="Tracks" :sortable="true" headerClass="number-column"
                        class="number-column" style="min-width: 5rem" />
                <Column field="totalTrackDurationSecs" header="Duration" :sortable="true"
                        headerClass="number-column" class="number-column" style="min-width: 8rem">
                  <template #body="slotProps">
                    <span v-tooltip.top="{ value: formatDurationTooltip(slotProps.data.totalTrackDurationSecs * 1000), showDelay: 400 }">
                      {{ formatDurationSmart(slotProps.data.totalTrackDurationSecs * 1000, durColMaxMs) }}
                    </span>
                  </template>
                </Column>
                <Column field="trackDurationSecsMed" header="Avg Duration" :sortable="true"
                        headerClass="number-column" class="number-column" style="min-width: 8rem">
                  <template #body="slotProps">
                    <span v-tooltip.top="{ value: formatDurationTooltip(slotProps.data.trackDurationSecsMed * 1000), showDelay: 400 }">
                      {{ formatDurationSmart(slotProps.data.trackDurationSecsMed * 1000, durColMaxMs) }}
                    </span>
                  </template>
                </Column>
                <Column field="trackLengthInMeterSum" header="Distance" :sortable="true"
                        headerClass="number-column" class="number-column" style="min-width: 8rem">
                  <template #body="slotProps">
                    <span v-tooltip.top="{ value: formatDistanceTooltip(slotProps.data.trackLengthInMeterSum), showDelay: 400 }">
                      {{ formatDistanceSmart(slotProps.data.trackLengthInMeterSum, distColMaxM) }}
                    </span>
                  </template>
                </Column>
                <Column field="trackLengthInMeterMed" header="Avg Dist." :sortable="true"
                        headerClass="number-column" class="number-column" style="min-width: 8rem">
                  <template #body="slotProps">
                    <span v-tooltip.top="{ value: formatDistanceTooltip(slotProps.data.trackLengthInMeterMed), showDelay: 400 }">
                      {{ formatDistanceSmart(slotProps.data.trackLengthInMeterMed, distColMaxM) }}
                    </span>
                  </template>
                </Column>
                <Column field="energyNetTotalWhSum" :sortable="true"
                        headerClass="number-column" class="number-column" style="min-width: 7rem">
                  <template #header>
                    <span>Energy</span>
                    <button class="info-btn info-btn--col" @click.stop="showInfo($event, INFO_ENERGY)" aria-label="About energy"><i class="bi bi-info-circle"></i></button>
                  </template>
                  <template #body="slotProps">
                    {{ formatEnergy(slotProps.data.energyNetTotalWhSum) }}
                  </template>
                </Column>
                <Column field="powerWattsAvgMed" :sortable="true"
                        headerClass="number-column" class="number-column" style="min-width: 7rem">
                  <template #header>
                    <span>Avg Power</span>
                    <button class="info-btn info-btn--col" @click.stop="showInfo($event, INFO_AVG_POWER)" aria-label="About Average Power"><i class="bi bi-info-circle"></i></button>
                  </template>
                  <template #body="slotProps">
                    {{ formatPower(slotProps.data.powerWattsAvgMed) }}
                  </template>
                </Column>
                <Column field="normalizedPowerMed" :sortable="true"
                        headerClass="number-column" class="number-column" style="min-width: 7rem">
                  <template #header>
                    <span>NP</span>
                    <button class="info-btn info-btn--col" @click.stop="showInfo($event, INFO_NORMALIZED_POWER)" aria-label="About Normalized Power"><i class="bi bi-info-circle"></i></button>
                  </template>
                  <template #body="slotProps">
                    {{ formatPower(slotProps.data.normalizedPowerMed) }}
                  </template>
                </Column>
                <Column field="intensityIndexAvg" :sortable="true"
                        headerClass="number-column" class="number-column" style="min-width: 7rem">
                  <template #header>
                    <span>Intensity</span>
                    <button class="info-btn info-btn--col" @click.stop="showInfo($event, INFO_INTENSITY_INDEX)" aria-label="About Intensity Index"><i class="bi bi-info-circle"></i></button>
                  </template>
                  <template #body="slotProps">
                    {{ slotProps.data.intensityIndexAvg != null && slotProps.data.intensityIndexAvg > 0 ? slotProps.data.intensityIndexAvg.toFixed(2) : '' }}
                  </template>
                </Column>
                <Column field="trainingLoadPerRideAvg" :sortable="true"
                        headerClass="number-column" class="number-column" style="min-width: 8rem">
                  <template #header>
                    <span>Training Load</span>
                    <button class="info-btn info-btn--col" @click.stop="showInfo($event, INFO_TRAINING_LOAD)" aria-label="About Training Load"><i class="bi bi-info-circle"></i></button>
                  </template>
                  <template #body="slotProps">
                    {{ slotProps.data.trainingLoadPerRideAvg != null && slotProps.data.trainingLoadPerRideAvg > 0 ? Math.round(slotProps.data.trainingLoadPerRideAvg) : '' }}
                  </template>
                </Column>
              </DataTable>
            </div>
          </div>

          <!-- ── Charts (inline) ── -->
          <div class="charts-scroll" v-if="statsView === 'charts' && filteredStatisticData.length > 0">
            <div class="chart-card">
              <div class="chart-header" style="--chart-header-accent: var(--chart-series-1)">
                <i class="bi bi-clock" style="color: var(--chart-series-1)"></i> Duration
              </div>
              <highcharts ref="chartDuration" :options="chartOptionsDuration" class="stat-chart" />
            </div>
            <div class="chart-card">
              <div class="chart-header" style="--chart-header-accent: var(--chart-series-2)">
                <i class="bi bi-signpost-split" style="color: var(--chart-series-2)"></i> Distance
              </div>
              <highcharts ref="chartDistance" :options="chartOptionsDistance" class="stat-chart" />
            </div>
            <div class="chart-card">
              <div class="chart-header" style="--chart-header-accent: var(--info)">
                <i class="bi bi-bar-chart-line" style="color: var(--info)"></i> Activity
              </div>
              <highcharts ref="chartActivity" :options="chartOptionsActivity" class="stat-chart" />
            </div>
            <div class="chart-card" v-if="summaryStats.hasEnergy">
              <div class="chart-header" style="--chart-header-accent: var(--chart-series-3)">
                <i class="bi bi-lightning-charge" style="color: var(--chart-series-3)"></i> Energy
                <button class="info-btn info-btn--header" @click.stop="showInfo($event, INFO_ENERGY)" aria-label="About energy"><i class="bi bi-info-circle"></i></button>
              </div>
              <highcharts ref="chartEnergy" :options="chartOptionsEnergy" class="stat-chart" />
            </div>
            <div class="chart-card" v-if="summaryStats.hasFitness">
              <div class="chart-header" style="--chart-header-accent: var(--error)">
                <i class="bi bi-speedometer2" style="color: var(--error)"></i> Intensity Index
                <button class="info-btn info-btn--header" @click.stop="showInfo($event, INFO_INTENSITY_INDEX)" aria-label="About Intensity Index"><i class="bi bi-info-circle"></i></button>
              </div>
              <highcharts ref="chartIntensityIndex" :options="chartOptionsIntensityIndex" class="stat-chart" />
            </div>
            <div class="chart-card" v-if="summaryStats.hasFitness">
              <div class="chart-header" style="--chart-header-accent: var(--accent-text-light)">
                <i class="bi bi-heart-pulse" style="color: var(--accent-text-light)"></i> Training Load
                <button class="info-btn info-btn--header" @click.stop="showInfo($event, INFO_TRAINING_LOAD)" aria-label="About Training Load"><i class="bi bi-info-circle"></i></button>
              </div>
              <highcharts ref="chartTrainingLoad" :options="chartOptionsTrainingLoad" class="stat-chart" />
            </div>
            <div class="chart-card">
              <div class="chart-header" style="--chart-header-accent: var(--success)">
                <i class="bi bi-compass" style="color: var(--success)"></i> Exploration
                <button class="info-btn info-btn--header" @click.stop="showInfo($event, INFO_EXPLORATION)" aria-label="About exploration"><i class="bi bi-info-circle"></i></button>
              </div>
              <highcharts v-if="summaryStats.hasExploration" ref="chartExploration" :options="chartOptionsExploration" class="stat-chart" />
              <div v-else class="chart-pending">
                <i class="bi bi-hourglass-split"></i>
                <span>Exploration data is being calculated in the background.</span>
              </div>
            </div>
          </div>
        </div>
          </TabPanel>

          </TabPanels>
        </Tabs>
      </div>

      <!-- ── Shared info popover ── -->
      <Popover ref="infoPopover" class="stat-info-popover" appendTo="body">
        <p class="stat-info-text">{{ currentInfoText }}</p>
      </Popover>
    </BottomSheet>
  </div>
</template>

<script lang="ts">
import { defineComponent, inject, markRaw, ref, computed, onMounted, onUnmounted, type PropType } from "vue";
import { formatDateAndTime, formatDuration, formatDistance as formatDistanceUtil, formatDistanceSmart, formatDurationSmart, formatDurationTooltip, formatDistanceTooltip, formatLocaleNumber } from "@/utils/Utils";
import { fetchStatistics } from "@/utils/ServiceHelper";
import BottomSheet from "@/components/ui/BottomSheet.vue";
import StatisticsOverview from "@/components/statistics/StatisticsOverview.vue";
import TrackBrowserControls from "@/components/track-browser/TrackBrowserControls.vue";
import TrackBrowserTable from "@/components/track-browser/TrackBrowserTable.vue";
import { useTrackBrowser } from "@/components/track-browser/useTrackBrowser";
import type { GpsTrack, GpsTrackStatistics } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import type Highcharts from 'highcharts';

/** Compact number formatter for y-axis tick labels — no unit, no excess decimals */
function compactNum(v: number): string {
  if (v === 0) return '0';
  if (Math.abs(v) >= 1000) return (v / 1000).toFixed(v % 1000 === 0 ? 0 : 1) + 'k';
  if (Math.abs(v) >= 10)  return Math.round(v).toString();
  return parseFloat(v.toFixed(1)).toString();
}

function hexToRgba(hex: string, alpha: number): string {
  const h = hex.replace('#', '');
  const r = parseInt(h.substring(0, 2), 16);
  const g = parseInt(h.substring(2, 4), 16);
  const b = parseInt(h.substring(4, 6), 16);
  return `rgba(${r},${g},${b},${alpha})`;
}

function cssToken(name: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim();
}

function buildStatChart(seriesName: string, seriesColorToken: string, unit: string, tooltipFormatter: (v: number) => string): Highcharts.Options {
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
    title:   { text: undefined },
    credits: { enabled: false },
    legend:  { enabled: false },
    xAxis: {
      categories: [] as string[],
      labels: { style: { color: textColor, fontSize: '12px' } },
      lineColor: gridColor,
      tickColor: 'transparent',
    },
    yAxis: {
      gridLineColor: gridColor,
      title: { text: null },
      labels: {
        style: { color: textColor, fontSize: '12px' },
        formatter(this: any) {
          return this.isLast && unit
            ? compactNum(this.value as number) + '\u202f' + unit
            : compactNum(this.value as number);
        },
      },
    },
    tooltip: {
      backgroundColor: tooltipBg,
      borderColor:     borderColor,
      borderRadius:    8,
      borderWidth:     1,
      shadow:          false,
      style:           { color: tooltipText, fontSize: '12px' },
      formatter(this: any) { return `<b>${this.point.category ?? this.x}</b><br/>${this.series.name}: ${tooltipFormatter(this.y as number)}`; },
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

const pad = (n: number) => n.toString().padStart(2, '0');
function fmtHours(hours: number): string {
  const h = Math.floor(hours);
  const m = Math.floor((hours - h) * 60);
  return `${pad(h)}h ${pad(m)}m`;
}

const INFO_PERIODS    = 'The number of time periods with recorded activity in the selected grouping (e.g. quarters or months).';
const INFO_TRACKS     = 'Total number of GPS tracks across all periods.';
const INFO_DISTANCE   = 'Cumulative GPS-measured distance across all periods.';
const INFO_DURATION   = 'Cumulative active duration (moving time) across all periods.';
const INFO_ENERGY     = 'Estimated external mechanical work from GPS-derived physics (Wh): climbing, drag, rolling/friction, and acceleration. It is not metabolic calorie burn and not measured power-sensor data.';
const INFO_AVG_POWER  = 'Estimated average external mechanical power from the same GPS-derived energy model. Treat it as a physics estimate, not as recorded power-meter data.';
const INFO_NORMALIZED_POWER = 'Normalized Power (NP) is computed from estimated mechanical power using a 30 s rolling window and fourth-power weighting. It is useful for comparing effort patterns, but it is not a power-meter measurement. Also known as: Weighted Average Power (Strava), Normalized Power / NP (Garmin, TrainingPeaks), xPower / IsoPower (GoldenCheetah).';
const INFO_INTENSITY_INDEX  = 'Intensity Index = estimated NP ÷ your threshold power. 1.0 ≈ all-out 1 h effort if estimated power matches your real power.';
const INFO_TRAINING_LOAD    = 'Training Load per ride = (estimated NP ÷ threshold)² × moving hours × 100. It scales duration and intensity, but inherits the limits of the estimated mechanical-power model.';
const INFO_EXPLORATION = 'Average share of each track covering ground not visited before (within a 25 m grid). Calculated as a background job after indexing — may take a moment to appear.';

export default defineComponent({
  name: "Statistics",
  components: { BottomSheet, StatisticsOverview, TrackBrowserControls, TrackBrowserTable },
  props: {
    tracks: { type: Array as PropType<GpsTrack[]>, default: () => [] },
    tracksCount: { type: Number, default: undefined },
    unfilteredTotal: { type: Number, default: undefined },
    selectedTrackId: { type: [Number, String], default: null },
  },
  emits: ['tool-opened', 'tool-closed', 'select-track', 'open-details'],
  data() {
    return {
      active: false,
      showMenu: false,
      activeTab: 'overview',
      statsView: 'charts' as 'table' | 'charts',
      statisticData: [] as GpsTrackStatistics[],
      currentInfoText: '',
      INFO_PERIODS,
      INFO_TRACKS,
      INFO_DISTANCE,
      INFO_DURATION,
      INFO_ENERGY,
      INFO_AVG_POWER,
      INFO_NORMALIZED_POWER,
      INFO_INTENSITY_INDEX,
      INFO_TRAINING_LOAD,
      INFO_EXPLORATION,
      selectedGrouping: 'YYYY-"Q"Q',
      selectedSubUnit: null as string | null,
      statisticGroupings: [
        { name: "YYYY (by year)", code: "YYYY" },
        { name: "YYYY-Q (by year and quarter)", code: 'YYYY-"Q"Q' },
        { name: "YYYY-MM (by year and month)", code: "YYYY-MM" },
        { name: "YYYY-WW (by year and week)", code: "YYYY-WW" },
        { name: "YYYY-MM-DD (by year, week and day)", code: "YYYY-MM-DD" },
        { name: "Total", code: "TOTAL" },
      ],
      chartOptionsDuration: buildStatChart('Duration', '--chart-series-1', 'h',  (v) => fmtHours(v)),
      chartOptionsDistance: buildStatChart('Distance', '--chart-series-2', 'km', (v) => v.toFixed(1) + ' km'),
      chartOptionsActivity: buildStatChart('Tracks',   '--info', '',   (v) => Math.round(v).toString()),
      chartOptionsEnergy:   buildStatChart('Mechanical Energy', '--chart-series-3', 'Wh', (v) => formatLocaleNumber(Math.round(v)) + ' Wh'),
      chartOptionsIntensityIndex: buildStatChart('Intensity Index', '--error', '', (v) => v.toFixed(2)),
      chartOptionsTrainingLoad:   buildStatChart('Training Load',   '--accent-text-light', '', (v) => v.toFixed(0)),
      chartOptionsExploration: buildStatChart('Exploration', '--success', '%', (v) => v.toFixed(1) + '%'),
    };
  },
  setup(props) {
    const toast = inject("toast");

    const windowWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1024);
    function onResize() { windowWidth.value = window.innerWidth; }
    onMounted(() => window.addEventListener('resize', onResize));
    onUnmounted(() => window.removeEventListener('resize', onResize));
    const isMobile = computed(() => windowWidth.value < 768);

    const tracksRef = computed(() => (props.tracks ?? []) as GpsTrack[]);
    const {
      query: trackQuery,
      rows: trackRows,
      summary: trackFilterSummary,
      totalCount: trackTotalCount,
    } = useTrackBrowser(tracksRef);

    return { toast, isMobile, trackQuery, trackRows, trackFilterSummary, trackTotalCount };
  },
  computed: {
    availableSubUnits(): string[] {
      if (!this.statisticData) return [];
      const subunits = new Set<string>();
      for (const d of this.statisticData) {
        if (d.subGroup) subunits.add(d.subGroup);
      }
      return Array.from(subunits).sort();
    },
    statsDetents(): { height: string }[] {
      return this.isMobile
        ? [{ height: '60vh' }, { height: '88vh' }]
        : [{ height: '88vh' }, { height: '98vh' }];
    },
    filteredStatisticData(): GpsTrackStatistics[] {
      if (!this.statisticData) return [];
      if (this.selectedSubUnit) {
        return this.statisticData.filter((d: GpsTrackStatistics) => d.subGroup === this.selectedSubUnit);
      }
      return this.statisticData;
    },
    distColMaxM(): number {
      const data = this.filteredStatisticData;
      return Math.max(
        0,
        ...data.map((d: GpsTrackStatistics) => d.trackLengthInMeterSum ?? 0),
        ...data.map((d: GpsTrackStatistics) => d.trackLengthInMeterMed ?? 0),
      );
    },
    durColMaxMs(): number {
      const data = this.filteredStatisticData;
      return Math.max(
        0,
        ...data.map((d: GpsTrackStatistics) => (d.totalTrackDurationSecs ?? 0) * 1000),
        ...data.map((d: GpsTrackStatistics) => (d.trackDurationSecsMed ?? 0) * 1000),
      );
    },
    summaryStats(): { periods: number; tracks: number; distance: string; distanceFull: string; duration: string; durationFull: string; energy: string; hasEnergy: boolean; hasFitness: boolean; hasExploration: boolean } {
      const data = this.filteredStatisticData;
      const totalTracks  = data.reduce((s: number, d: GpsTrackStatistics) => s + (d.numberOfTracks ?? 0), 0);
      const totalDistM   = data.reduce((s: number, d: GpsTrackStatistics) => s + (d.trackLengthInMeterSum ?? 0), 0);
      const totalDurSecs = data.reduce((s: number, d: GpsTrackStatistics) => s + (d.totalTrackDurationSecs ?? 0), 0);
      const totalEnergy  = data.reduce((s: number, d: GpsTrackStatistics) => s + (d.energyNetTotalWhSum ?? 0), 0);
      const hasExploration = data.some((d: any) => d.explorationScoreAvg != null && d.explorationScoreAvg > 0);
      const hasFitness = data.some((d: any) => (d.normalizedPowerMed ?? 0) > 0 || (d.intensityIndexAvg ?? 0) > 0 || (d.trainingLoadPerRideAvg ?? 0) > 0);
      return {
        periods:      data.length,
        tracks:       totalTracks,
        distance:     formatDistanceSmart(totalDistM),
        distanceFull: formatDistanceTooltip(totalDistM),
        duration:     formatDurationSmart(totalDurSecs * 1000),
        durationFull: formatDurationTooltip(totalDurSecs * 1000),
        energy:       formatLocaleNumber(Math.round(totalEnergy)) + ' Wh',
        hasEnergy:    totalEnergy > 0,
        hasFitness,
        hasExploration,
      };
    },
  },
  watch: {
    filteredStatisticData: {
      handler(newData) { this.updateCharts(newData); },
    },
    selectedGrouping() {
      this.selectedSubUnit = null;
    },
    statsView(newVal: string) {
      if (newVal === 'charts') {
        this.$nextTick(() => {
          setTimeout(() => {
            this.updateCharts(this.filteredStatisticData);
            for (const ref of ['chartDuration', 'chartDistance', 'chartActivity', 'chartEnergy', 'chartIntensityIndex', 'chartTrainingLoad', 'chartExploration']) {
              const cmp: any = (this.$refs as any)[ref];
              if (cmp?.chart) cmp.chart.reflow();
            }
            requestAnimationFrame(() => this.syncChartMargins());
          }, 80);
        });
      }
    },
  },
  methods: {
    async toggle() {
      this.showMenu = !this.showMenu;
      this.active   = !this.active;
      if (this.active) {
        this.$emit('tool-opened');
        await this.fetchStatistics();
      }
    },
    close() {
      this.showMenu = false;
      this.active   = false;
    },
    onSheetClosed() {
      this.active = false;
      this.$emit('tool-closed');
    },
    async fetchStatistics() {
      const data = await fetchStatistics(this.selectedGrouping);
      this.statisticData = data ?? [];
    },
    updateCharts(data: GpsTrackStatistics[]) {
      const categories = (data ?? []).map((o: GpsTrackStatistics) => o.groupBy ?? '');

      const setChart = (ref: string, opts: any, values: number[]) => {
        opts.xAxis.categories = categories;
        opts.series[0].data   = values;
        const cmp: any = (this.$refs as any)[ref];
        if (cmp?.chart) cmp.chart.update(opts, true, true);
      };

      setChart('chartDuration', this.chartOptionsDuration,
        data.map((o: GpsTrackStatistics) => parseFloat(((o.totalTrackDurationSecs ?? 0) / 3600).toFixed(2))));
      setChart('chartDistance', this.chartOptionsDistance,
        data.map((o: GpsTrackStatistics) => parseFloat(((o.trackLengthInMeterSum ?? 0) / 1000).toFixed(2))));
      setChart('chartActivity', this.chartOptionsActivity,
        data.map((o: GpsTrackStatistics) => o.numberOfTracks ?? 0));
      setChart('chartEnergy', this.chartOptionsEnergy,
        data.map((o: GpsTrackStatistics) => o.energyNetTotalWhSum ?? 0));
      setChart('chartIntensityIndex', this.chartOptionsIntensityIndex,
        data.map((o: any) => parseFloat(((o.intensityIndexAvg ?? 0)).toFixed(3))));
      setChart('chartTrainingLoad', this.chartOptionsTrainingLoad,
        data.map((o: any) => parseFloat(((o.trainingLoadPerRideAvg ?? 0)).toFixed(1))));
      setChart('chartExploration', this.chartOptionsExploration,
        data.map((o: any) => parseFloat((((o as any).explorationScoreAvg ?? 0) * 100).toFixed(1))));

      // After Highcharts has re-rendered, read each chart's plotLeft and
      // apply the widest one to all charts so plot areas align perfectly.
      this.$nextTick(() => requestAnimationFrame(() => this.syncChartMargins()));
    },
    syncChartMargins() {
      const chartRefs = ['chartDuration', 'chartDistance', 'chartActivity', 'chartEnergy', 'chartIntensityIndex', 'chartTrainingLoad', 'chartExploration'];
      const charts = chartRefs
        .map((r) => (this.$refs as any)[r]?.chart)
        .filter(Boolean);
      if (charts.length === 0) return;
      const maxLeft = Math.max(...charts.map((c: any) => c.plotLeft as number));
      charts.forEach((c: any) => {
        if (c.plotLeft !== maxLeft) {
          c.update({ chart: { marginLeft: maxLeft } }, true, false);
        }
      });
    },
    formatDuration(duration: number) { return formatDuration(duration); },
    formatDurationSmart(millis: number, maxMillis?: number) { return formatDurationSmart(millis, maxMillis); },
    formatDurationTooltip(millis: number) { return formatDurationTooltip(millis); },
    formatDistance(distanceInMeters: number, decimals?: number) { return formatDistanceUtil(distanceInMeters, decimals); },
    formatDistanceSmart(meters: number, maxMeters?: number) { return formatDistanceSmart(meters, maxMeters); },
    formatDistanceTooltip(meters: number) { return formatDistanceTooltip(meters); },
    formatEnergy(value: number | null) {
      if (value == null || value === 0) return '';
      return formatLocaleNumber(Math.round(value)) + ' Wh';
    },
    formatPower(value: number | null) {
      if (value == null || value === 0) return '';
      return Math.round(value) + ' W';
    },
    showInfo(event: Event, text: string) {
      this.currentInfoText = text;
      (this.$refs.infoPopover as any).toggle(event);
    },
  },
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
  display: flex; flex-direction: column; flex: 1 1 auto; min-height: 0;
  width: 100%; height: 100%; overflow: hidden;
}

/* ── Tabs fill the remaining space; only panels scroll ── */
:deep(.p-tabs) {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 0;
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

.stats-header-tab {
  padding: 0.25rem 0.7rem;
  border-radius: 1rem;
  border: none;
  background: transparent;
  color: var(--text-secondary);
  font-size: var(--text-sm-size);
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
  white-space: nowrap;
  line-height: var(--text-sm-lh);
}

.stats-header-tab:not(.stats-header-tab--active):hover {
  background: var(--surface-hover);
  color: var(--text-primary);
}

.stats-header-tab--active {
  background: var(--accent-subtle);
  color: var(--accent-text);
  font-weight: 600;
}

:deep(.p-tabpanels) {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  overscroll-behavior-y: contain;
}

:deep(.p-tabpanel) {
  min-height: 0;
  padding-top: 0.5rem;
}

.tracks-tab {
  padding: 0;
  margin-top: -0.5rem;
}

.statistics-holder {
  flex: 1 1 auto; display: flex; flex-direction: column; min-height: 0; gap: .5rem;
  padding: 0;
}

/* ── Controls bar ── */
.stats-controls {
  padding: 0 1rem;
}
.stats-controls__row {
  display: flex; flex-wrap: wrap; gap: .5rem;
}
.stats-controls__select-wrap {
  position: relative; display: flex; align-items: center; flex: 1 1 180px; min-width: 0;
}
.stats-controls__icon {
  position: absolute; left: .65rem; z-index: 1;
  color: var(--text-muted); font-size: var(--text-sm-size); pointer-events: none;
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
  display: flex; flex-wrap: wrap; gap: .5rem; padding: 0 1rem;
}
.stat-tile {
  flex: 1 1 70px; min-width: 0;
  display: flex; flex-direction: column; align-items: center;
  background: transparent;
  border: 1px solid var(--border-default);
  border-radius: 10px;
  padding: .55rem .4rem .45rem;
  gap: .15rem;
  transition: background .15s;
}

.stat-tile__icon { font-size: var(--text-base-size); line-height: var(--text-base-lh); }
.stat-tile__value {
  font-size: var(--text-sm-size); font-weight: 700;
  color: var(--text-primary);
  line-height: var(--text-sm-lh);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 100%;
  text-align: center;
}
.stat-tile__label {
  font-size: var(--text-2xs-size); font-weight: 500;
  color: var(--text-muted); text-transform: uppercase; letter-spacing: .03em;
  white-space: nowrap;
  display: flex; align-items: center; gap: 0;
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
  flex: 1 1 auto; min-width: 0; min-height: 0;
  overflow-x: auto; overflow-y: visible;
  -webkit-overflow-scrolling: touch;
  touch-action: pan-x;
}

/* ── DataTable theming ── */
/* Colors/typography handled globally in main.css; only structural overrides here */
.statistics-table :deep(.p-datatable-wrapper) { overflow: visible; }
.statistics-table :deep(.p-datatable-thead) {
  position: sticky; top: 0; z-index: 2;
}
.statistics-table :deep(.number-column) { text-align: right; }

/* ── Table / Charts toggle ── */
.stats-view-toggle {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  background: var(--surface-elevated);
  border: 1px solid var(--border-default);
  border-radius: 8px;
  padding: 3px;
  gap: 2px;
}
.toggle-btn {
  display: flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.28rem 0.65rem;
  border-radius: 5px;
  border: none;
  background: transparent;
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
  white-space: nowrap;
  font-family: inherit;
}
.toggle-btn:hover { color: var(--text-secondary); background: var(--surface-hover); }
.toggle-btn--active {
  background: var(--surface-glass-heavy);
  color: var(--accent-text);
  box-shadow: var(--shadow-sm);
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
.chart-card:last-child { padding-bottom: 0; }
.chart-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: var(--text-xs-size);
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--text-secondary);
  padding: 1.25rem 1rem 0.6rem;
}
.chart-header i { font-size: var(--text-sm-size); }
.stat-chart {
  width: 100%;
  height: 190px;
}

/* ── Mobile tweaks ── */
@media (max-width: 600px) {
  /* Controls: less padding */
  .stats-controls { padding: 0 0.75rem; }

  /* Holder: tighter rhythm */
  .statistics-holder { gap: 0.5rem; }

  /* Summary → horizontal scrollable pill strip (no wrap = ~80px saved) */
  .stats-summary {
    flex-wrap: nowrap;
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
    gap: 0.4rem;
    padding: 0 0.75rem 0.2rem;
    scrollbar-width: none;
  }
  .stats-summary::-webkit-scrollbar { display: none; }

  /* Tile becomes a compact horizontal pill */
  .stat-tile {
    flex: 0 0 auto;
    flex-direction: row;
    align-items: center;
    padding: 0.3rem 0.65rem;
    gap: 0.25rem;
    border-radius: 999px;
  }
  .stat-tile__icon { font-size: var(--text-sm-size); }
  .stat-tile__value { font-size: var(--text-sm-size); }
  .stat-tile__label {
    font-size: var(--text-xs-size);
    font-weight: 500;
    text-transform: none;
    letter-spacing: 0;
  }

  /* Table: compact */
  .statistics-table { font-size: var(--text-xs-size); }
  .statistics-table :deep(th),
  .statistics-table :deep(td) { padding: .4rem .3rem; white-space: nowrap; min-width: 0 !important; width: auto !important; }

  /* Charts: shorter so 2 are visible at once */
  .stat-chart { height: 185px; }
  .chart-header { padding: 0.7rem 0.75rem 0.4rem; }
  .charts-scroll { padding-bottom: 0.25rem; }
}

@media (max-height: 500px) {
  .tool-dialog { height: 92vh; max-height: 92vh; }
}

/* ── Info icons ── */
.info-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: none;
  background: transparent;
  cursor: pointer;
  color: var(--text-faint);
  font-size: var(--text-2xs-size);
  line-height: var(--text-2xs-lh);
  transition: color 0.15s;
  vertical-align: middle;
  margin-left: 2px;
}
.info-btn:hover, .info-btn:focus-visible { color: var(--accent-muted); outline: none; }
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
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
  color: var(--text-secondary);
  margin: 0;
  padding: 0.1rem 0;
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
.chart-pending i { font-size: var(--text-base-size); opacity: 0.6; }
</style>
