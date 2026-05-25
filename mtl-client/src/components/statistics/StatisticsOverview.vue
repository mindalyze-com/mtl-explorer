<template>
  <div class="overview">
    <div v-if="overviewData && filteredCount !== totalCount" class="filter-banner" data-test="filter-banner">
      <i class="bi bi-funnel-fill filter-banner__icon"></i>
      <span
        >Showing <strong>{{ filteredCount }}</strong> of {{ totalCount }} tracks</span
      >
    </div>

    <div v-if="hasTracks" class="hero-stats">
      <div class="hero-tile" data-test="summary-tracks">
        <div class="hero-tile__icon-wrap" style="--tile-accent: var(--chart-series-1)">
          <i class="bi bi-pin-map"></i>
        </div>
        <div class="hero-tile__value">{{ summary.count }}</div>
        <div class="hero-tile__label">Tracks</div>
      </div>
      <div class="hero-tile" data-test="summary-distance">
        <div class="hero-tile__icon-wrap" style="--tile-accent: var(--chart-series-2)">
          <i class="bi bi-signpost-split"></i>
        </div>
        <div v-tooltip.top="{ value: summary.totalDistanceFull, showDelay: 400 }" class="hero-tile__value">
          {{ summary.totalDistanceFormatted }}
        </div>
        <div class="hero-tile__label">Distance</div>
      </div>
      <div class="hero-tile" data-test="summary-duration">
        <div class="hero-tile__icon-wrap" style="--tile-accent: var(--info)">
          <i class="bi bi-clock"></i>
        </div>
        <div v-tooltip.top="{ value: summary.totalDurationFull, showDelay: 400 }" class="hero-tile__value">
          {{ summary.totalDurationFormatted }}
        </div>
        <div class="hero-tile__label hero-tile__label--info">
          Duration
          <button class="info-btn" aria-label="About duration" @click.stop="showInfo($event, INFO_DURATION)">
            <i class="bi bi-info-circle"></i>
          </button>
        </div>
      </div>
      <div v-if="summary.totalEnergy > 0" class="hero-tile" data-test="summary-energy">
        <div class="hero-tile__icon-wrap" style="--tile-accent: var(--chart-series-3)">
          <i class="bi bi-lightning-charge"></i>
        </div>
        <div class="hero-tile__value">{{ summary.totalEnergyFormatted }}</div>
        <div class="hero-tile__label hero-tile__label--info">
          Energy
          <button class="info-btn" aria-label="About energy" @click.stop="showInfo($event, INFO_ENERGY)">
            <i class="bi bi-info-circle"></i>
          </button>
        </div>
      </div>
    </div>

    <div v-if="hasTracks" class="dashboard-grid">
      <section v-if="activityBreakdown.length > 0" class="overview-card activity-section" data-test="activity-section">
        <div class="section-header section-header--with-actions">
          <div class="section-header__title">
            <i class="bi bi-pie-chart"></i>
            <span>Activity Breakdown</span>
          </div>
          <div class="metric-toggle" aria-label="Activity breakdown metric">
            <button
              v-for="option in activityMetricOptions"
              :key="option.key"
              class="metric-toggle__btn"
              :class="{ 'metric-toggle__btn--active': effectiveActivityMetric === option.key }"
              :data-test="`activity-metric-${option.key}`"
              @click="selectActivityMetric(option.key)"
            >
              {{ option.label }}
            </button>
          </div>
        </div>
        <div class="activity-layout">
          <div class="activity-chart-wrap">
            <highcharts :options="donutOptions" class="donut-chart" />
          </div>
          <div class="activity-legend">
            <div
              v-for="item in activityBreakdown"
              :key="item.type"
              class="legend-row"
              :data-test="`activity-row-${item.type}`"
            >
              <span class="legend-swatch" :style="{ background: item.color }"></span>
              <i :class="item.icon" class="legend-icon" :style="{ color: item.color }"></i>
              <span class="legend-type">{{ item.label }}</span>
              <span class="legend-count">{{ item.count }}</span>
              <span v-tooltip.top="{ value: item.valueFull, showDelay: 400 }" class="legend-dist">{{
                item.valueFormatted
              }}</span>
            </div>
          </div>
        </div>
      </section>

      <section v-if="highlightRows.length > 0" class="overview-card" data-test="highlights-section">
        <div class="section-header section-header--with-actions">
          <div class="section-header__title">
            <i class="bi bi-stars"></i>
            <span>Highlights</span>
          </div>
          <button
            v-if="highlightExcludedCount > 0"
            class="section-header__note section-header__note--button"
            type="button"
            title="Show tracks excluded from highlights"
            data-test="highlight-exclusion-note"
            @click="emit('view-highlight-exclusions')"
          >
            {{ highlightExcludedCountLabel }}
          </button>
        </div>
        <div class="insight-list">
          <div v-for="row in highlightRows" :key="row.key" class="insight-row" :data-test="`highlight-${row.key}`">
            <button
              class="insight-row__main"
              :class="{
                'insight-row__main--static': !row.drilldownKey,
                'insight-row__main--active': selectedHighlightKey === row.drilldownKey,
              }"
              :disabled="!row.drilldownKey"
              :data-test="`highlight-${row.key}-main`"
              @click="selectHighlight(row.drilldownKey)"
            >
              <span class="insight-row__icon" :style="{ color: row.color }">
                <i :class="row.icon"></i>
              </span>
              <span class="insight-row__text">
                <span class="insight-row__label">{{ row.label }}</span>
                <span class="insight-row__subtitle">{{ row.subtitle }}</span>
              </span>
              <span class="insight-row__value">{{ row.value }}</span>
              <i
                v-if="row.drilldownKey"
                class="bi insight-row__arrow"
                :class="selectedHighlightKey === row.drilldownKey ? 'bi-chevron-down' : 'bi-chevron-right'"
              ></i>
            </button>
            <button
              v-if="row.info"
              class="info-btn info-btn--row"
              :aria-label="`About ${row.label}`"
              @click.stop="showInfo($event, row.info)"
            >
              <i class="bi bi-info-circle"></i>
            </button>
          </div>
        </div>
        <div v-if="selectedHighlightRows.length > 0" class="drilldown-panel" data-test="highlight-drilldown">
          <div class="drilldown-panel__header">
            <span>{{ selectedHighlightTitle }}</span>
            <button
              class="drilldown-panel__close"
              aria-label="Close highlight drilldown"
              @click="selectedHighlightKey = null"
            >
              <i class="bi bi-x-lg"></i>
            </button>
          </div>
          <div class="drilldown-list">
            <div
              v-for="row in selectedHighlightRows"
              :key="row.key"
              class="drilldown-row drilldown-row--with-action"
              :data-test="`highlight-drilldown-row-${row.rank}`"
            >
              <button
                class="drilldown-row__open"
                type="button"
                :data-test="`highlight-drilldown-open-${row.rank}`"
                @click="openTrack(row.trackId)"
              >
                <span class="drilldown-row__rank">{{ row.rank }}</span>
                <span class="drilldown-row__main">
                  <span class="drilldown-row__label">{{ row.label }}</span>
                  <span class="drilldown-row__meta">
                    <ActivityTypeBadge v-if="row.activityType" :type="row.activityType" size="xs" />
                    <span>{{ row.subtitle }}</span>
                  </span>
                </span>
                <span class="drilldown-row__stats">
                  <span>{{ row.value }}</span>
                  <small v-if="row.distanceFormatted || row.durationFormatted"
                    >{{ row.distanceFormatted }}<span v-if="row.distanceFormatted && row.durationFormatted"> - </span
                    >{{ row.durationFormatted }}</small
                  >
                </span>
                <i class="bi bi-chevron-right drilldown-row__arrow"></i>
              </button>
              <button
                class="drilldown-row__exclude"
                type="button"
                :aria-label="`Exclude ${row.label} from highlights`"
                title="Exclude from highlights"
                :disabled="savingHighlightExclusion"
                :data-test="`highlight-exclude-${row.rank}`"
                @click.stop="openHighlightExclusion($event, row)"
              >
                <i class="bi bi-shield-x"></i>
              </button>
            </div>
          </div>
        </div>
      </section>

      <section v-if="recentTracks.length > 0" class="overview-card recent-section" data-test="recent-section">
        <div class="section-header section-header--with-actions">
          <div class="section-header__title">
            <i class="bi bi-clock-history"></i>
            <span>Recent Activity</span>
          </div>
          <button class="section-header__link" data-test="recent-view-all" @click="emit('view-all-tracks')">
            View all tracks
            <i class="bi bi-arrow-right"></i>
          </button>
        </div>
        <div class="recent-list">
          <button
            v-for="track in recentTracks"
            :key="track.trackId"
            class="recent-row"
            :data-test="`recent-row-${track.trackId}`"
            @click="openTrack(track.trackId)"
          >
            <TrackShapePreview
              v-if="track.trackId != null"
              :track-id="track.trackId"
              :width="64"
              :height="44"
              class="recent-row__shape"
            />
            <span class="recent-row__main">
              <span class="recent-row__name">{{ track.displayName }}</span>
              <span class="recent-row__meta">
                <ActivityTypeBadge v-if="track.activityType" :type="track.activityType" size="xs" />
                <span>{{ formatTrackDate(track.startDate) }}</span>
              </span>
            </span>
            <span class="recent-row__stats">
              <span v-tooltip.top="{ value: formatDistanceTooltip(numberValue(track.distanceM)), showDelay: 400 }">{{
                formatDistanceSmart(numberValue(track.distanceM))
              }}</span>
              <span
                v-tooltip.top="{ value: formatDurationTooltip(numberValue(track.durationMs)), showDelay: 400 }"
                class="recent-row__duration"
                >{{ formatDurationSmart(numberValue(track.durationMs)) }}</span
              >
            </span>
            <i class="bi bi-chevron-right recent-row__arrow"></i>
          </button>
        </div>
      </section>

      <section v-if="activePeriodRows.length > 0 || visibleMilestones.length > 0" class="overview-card rhythm-section">
        <div class="section-header">
          <i class="bi bi-calendar-heart"></i>
          <span>Rhythm & Milestones</span>
        </div>

        <div v-if="activePeriodRows.length > 0" class="rhythm-block" data-test="active-periods-section">
          <div class="subsection-header">
            <span>Most Active Periods</span>
            <button
              class="info-btn"
              aria-label="About most active periods"
              data-test="active-periods-info"
              @click.stop="showInfo($event, INFO_ACTIVE_PERIODS)"
            >
              <i class="bi bi-info-circle"></i>
            </button>
          </div>
          <div class="period-list">
            <button
              v-for="row in activePeriodRows"
              :key="row.key"
              class="period-row"
              :class="{ 'period-row--active': selectedPeriodKey === row.drilldownKey }"
              :data-test="`active-period-${row.key}`"
              @click="selectPeriod(row.drilldownKey)"
            >
              <span class="period-row__icon" :style="{ color: row.color }">
                <i :class="row.icon"></i>
              </span>
              <span class="period-row__text">
                <span class="period-row__label">{{ row.label }}</span>
                <span class="period-row__subtitle">{{ row.subtitle }}</span>
              </span>
              <span class="period-row__value">
                <span>{{ row.durationFormatted }}</span>
                <small>{{ row.count }} tracks - {{ row.distanceFormatted }}</small>
              </span>
              <i
                class="bi period-row__arrow"
                :class="selectedPeriodKey === row.drilldownKey ? 'bi-chevron-down' : 'bi-chevron-right'"
              ></i>
            </button>
          </div>
          <div v-if="selectedPeriodRows.length > 0" class="drilldown-panel" data-test="period-drilldown">
            <div class="drilldown-panel__header">
              <span>{{ selectedPeriodTitle }}</span>
              <button
                class="drilldown-panel__close"
                aria-label="Close period drilldown"
                @click="selectedPeriodKey = null"
              >
                <i class="bi bi-x-lg"></i>
              </button>
            </div>
            <div class="drilldown-list">
              <div
                v-for="row in selectedPeriodRows"
                :key="row.key"
                class="drilldown-row drilldown-row--static"
                :data-test="`period-drilldown-row-${row.rank}`"
              >
                <span class="drilldown-row__rank">{{ row.rank }}</span>
                <span class="drilldown-row__main">
                  <span class="drilldown-row__label">{{ row.label }}</span>
                  <span class="drilldown-row__meta">{{ row.subtitle }}</span>
                </span>
                <span class="drilldown-row__stats">
                  <span>{{ row.value }}</span>
                  <small>{{ row.distanceFormatted }}</small>
                </span>
              </div>
            </div>
          </div>
        </div>

        <div v-if="visibleMilestones.length > 0" class="rhythm-block" data-test="milestones-section">
          <div class="subsection-header">
            <span>Milestones</span>
            <button
              class="info-btn"
              aria-label="About milestones"
              data-test="milestones-info"
              @click.stop="showInfo($event, INFO_MILESTONES)"
            >
              <i class="bi bi-info-circle"></i>
            </button>
          </div>
          <div class="milestone-list">
            <button
              v-for="row in visibleMilestones"
              :key="row.key"
              class="milestone-row"
              :data-test="`milestone-${row.key}`"
              @click="openTrack(row.trackId)"
            >
              <span class="milestone-row__icon" :style="{ color: row.color }">
                <i :class="row.icon"></i>
              </span>
              <span class="milestone-row__text">
                <span class="milestone-row__label">{{ row.label }}</span>
                <span class="milestone-row__subtitle">{{ row.subtitle }}</span>
              </span>
              <span class="milestone-row__value">{{ row.value }}</span>
            </button>
          </div>
        </div>
      </section>
    </div>

    <div v-else-if="loading" class="empty-state" data-test="overview-loading">
      <i class="pi pi-spin pi-spinner empty-state__icon"></i>
      <span>Loading statistics overview...</span>
    </div>

    <div v-else-if="loadError" class="empty-state empty-state--error" data-test="overview-error">
      <i class="bi bi-exclamation-triangle empty-state__icon"></i>
      <span>Statistics overview could not be loaded.</span>
    </div>

    <div v-else class="empty-state" data-test="overview-empty">
      <i class="bi bi-funnel empty-state__icon"></i>
      <span>No tracks match the current filters.</span>
    </div>

    <div v-if="summary.dateRangeLabel" class="date-range" data-test="date-range">
      <i class="bi bi-calendar3"></i>
      <span>{{ summary.dateRangeLabel }}</span>
    </div>

    <Popover ref="infoPopover" append-to="body">
      <p class="overview-info-text" data-test="overview-info-text">{{ currentInfoText }}</p>
    </Popover>
    <Popover ref="highlightExclusionPopover" append-to="body">
      <div class="highlight-exclusion-popover" data-test="highlight-exclusion-popover">
        <div class="highlight-exclusion-popover__title">Exclude from highlights</div>
        <div class="highlight-exclusion-popover__track">{{ pendingHighlightExclusionRow?.label }}</div>
        <Select
          :model-value="pendingHighlightExclusionReason"
          :options="exclusionReasonOptions"
          option-label="label"
          option-value="value"
          class="highlight-exclusion-popover__select"
          data-test="highlight-exclusion-reason-select"
          @update:model-value="setPendingHighlightExclusionReason"
        />
        <button
          class="highlight-exclusion-popover__action"
          type="button"
          :disabled="savingHighlightExclusion"
          data-test="highlight-exclusion-save"
          @click="saveHighlightExclusion"
        >
          <i class="bi bi-shield-x"></i>
          <span>Exclude</span>
        </button>
      </div>
    </Popover>
  </div>
</template>

<script setup lang="ts">
import { computed, inject, markRaw, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import {
  formatDate,
  formatDateAndTime,
  formatDistanceSmart,
  formatDurationSmart,
  formatDurationTooltip,
  formatDistanceTooltip,
  formatLocaleNumber,
  formatNumber,
} from '@/utils/Utils';
import { fetchStatisticsOverview, updateTrackStatisticsExclusion } from '@/utils/ServiceHelper';
import {
  StatisticsExclusionUpdateRequestHighlightExclusionReasonEnum as ExclusionReasonEnum,
  type ActivityBreakdown as OverviewActivityBreakdown,
  type StatisticsExclusionUpdateRequestHighlightExclusionReasonEnum,
  type GpsTrack,
  type PeriodDistribution as OverviewPeriodDistribution,
  type PeriodRow as OverviewPeriodRow,
  type StatisticsOverviewResponseDto,
  type TrackRanking as OverviewTrackRanking,
  type TrackRef as OverviewTrackRef,
} from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import TrackShapePreview from '@/components/ui/TrackShapePreview.vue';
import ActivityTypeBadge, {
  ACTIVITY_COLORS,
  ACTIVITY_FALLBACK_COLOR,
  ACTIVITY_ICONS,
} from '@/components/ui/ActivityTypeBadge.vue';

const MILESTONE_LIMIT = 8;
const DRILLDOWN_PREVIEW_LIMIT = 100;
const HIGHLIGHT_EXCLUSION_DEFAULT_REASON = ExclusionReasonEnum.GpsNoise;

const INFO_DURATION =
  'Duration uses moving time when the server has it. If moving time is missing, MTL Explorer falls back to elapsed start-to-end time.';
const INFO_ENERGY =
  'Energy is estimated external mechanical work from GPS-derived physics. It is not metabolic calorie burn and not measured power-sensor data.';
const INFO_30S_SPEED =
  'Fastest 30 s speed is the highest trailing 30-second average speed available on a track, which is less noisy than a single GPS point spike.';
const INFO_30S_POWER =
  'Peak 30 s power is estimated from GPS-derived mechanical power over a trailing 30-second window. It is not a power-meter measurement.';
const INFO_QUICKEST_ASCENT =
  'Quickest ascent ranks tracks by their highest GPS-derived moving-window elevation gain rate. It uses existing point metrics from the filtered result set.';
const INFO_ACTIVE_PERIODS =
  'Most active periods are selected by total moving time using server-side filtered statistics. Ties use track count first, then distance.';
const INFO_MILESTONES =
  'Milestones are calculated server-side only from the tracks currently included by the active filter.';

type InfoPopover = {
  toggle: (event: Event) => void;
  hide?: () => void;
};
type ToastService = {
  add: (message: { severity: string; summary: string; detail?: string; life?: number }) => void;
};
type ExclusionReason = StatisticsExclusionUpdateRequestHighlightExclusionReasonEnum;
type ExclusionReasonOption = {
  label: string;
  value: ExclusionReason;
};
type ActivityMetric = 'tracks' | 'distance' | 'duration' | 'energy';
type ActivityMetricOption = {
  key: ActivityMetric;
  label: string;
};
type ActivityBreakdownRow = {
  type: string;
  label: string;
  count: number;
  distanceM: number;
  durationMs: number;
  energyWh: number;
  metricValue: number;
  valueFormatted: string;
  valueFull: string;
  color: string;
  icon: string;
};
type DetailRow = {
  key: string;
  label: string;
  subtitle: string;
  value: string;
  icon: string;
  color: string;
  trackId?: number;
  info?: string;
  drilldownKey?: string;
  drilldownCount?: number;
};
type PeriodViewRow = {
  key: string;
  label: string;
  subtitle: string;
  durationFormatted: string;
  distanceFormatted: string;
  count: number;
  icon: string;
  color: string;
  drilldownKey?: string;
};
type TrackView = {
  trackId: number;
  displayName: string;
  activityType?: string;
  startDate?: Date;
  distanceM: number;
  durationMs: number;
  ascentM: number;
  energyWh: number;
  speed30Kmh: number;
  power30W: number;
};
type DrilldownRow = {
  key: string;
  trackId?: number;
  label: string;
  subtitle: string;
  value: string;
  rank: number;
  activityType?: string;
  distanceFormatted?: string;
  durationFormatted?: string;
};

const props = defineProps<{
  tracks?: GpsTrack[];
  tracksCount?: number;
  unfilteredTotal?: number;
}>();

const emit = defineEmits<{
  (event: 'open-details', id: number | string): void;
  (event: 'view-all-tracks'): void;
  (event: 'view-highlight-exclusions'): void;
}>();

const infoPopover = ref<InfoPopover | null>(null);
const highlightExclusionPopover = ref<InfoPopover | null>(null);
const currentInfoText = ref('');
const activityMetric = ref<ActivityMetric>('tracks');
const selectedHighlightKey = ref<string | null>(null);
const selectedPeriodKey = ref<string | null>(null);
const overviewData = ref<StatisticsOverviewResponseDto | null>(null);
const loading = ref(false);
const loadError = ref(false);
const activeRequest = ref<AbortController | null>(null);
const pendingHighlightExclusionRow = ref<DrilldownRow | null>(null);
const pendingHighlightExclusionReason = ref<ExclusionReason>(HIGHLIGHT_EXCLUSION_DEFAULT_REASON);
const savingHighlightExclusion = ref(false);
const toast = inject<ToastService>('toast', { add: () => undefined });
let requestSerial = 0;

const totalCount = computed(() => props.unfilteredTotal ?? filteredCount.value);
const filteredCount = computed(() => Math.round(numberValue(overviewData.value?.summary?.trackCount)));
const hasTracks = computed(() => filteredCount.value > 0);
const hasEnergy = computed(() => numberValue(overviewData.value?.summary?.energyWh) > 0);
const highlightExcludedCount = computed(() =>
  Math.round(numberValue(overviewData.value?.exclusionSummary?.highlightExcludedTrackCount))
);
const highlightExcludedCountLabel = computed(() => {
  const count = highlightExcludedCount.value;
  return `${formatLocaleNumber(count)} ${count === 1 ? 'track' : 'tracks'} excluded`;
});
const exclusionReasonOptions: ExclusionReasonOption[] = [
  { label: 'GPS noise', value: ExclusionReasonEnum.GpsNoise },
  { label: 'Wrong activity', value: ExclusionReasonEnum.WrongActivity },
  { label: 'Import artifact', value: ExclusionReasonEnum.ImportArtifact },
  { label: 'Other', value: ExclusionReasonEnum.Other },
];
const tracksById = computed(() => {
  const map = new Map<number, GpsTrack>();
  for (const track of props.tracks ?? []) {
    const id = Number(track.id);
    if (Number.isFinite(id)) map.set(id, track);
  }
  return map;
});

const activityMetricOptions = computed<ActivityMetricOption[]>(() => {
  const options: ActivityMetricOption[] = [
    { key: 'tracks', label: 'Tracks' },
    { key: 'distance', label: 'Distance' },
    { key: 'duration', label: 'Duration' },
  ];
  if (hasEnergy.value) options.push({ key: 'energy', label: 'Energy' });
  return options;
});

const effectiveActivityMetric = computed<ActivityMetric>(() => {
  if (activityMetric.value === 'energy' && !hasEnergy.value) return 'tracks';
  return activityMetric.value;
});

const summary = computed(() => {
  const data = overviewData.value?.summary;
  const totalDistanceMeters = numberValue(data?.distanceM);
  const totalDurationMillis = numberValue(data?.durationMs);
  const totalEnergy = numberValue(data?.energyWh);
  const oldestStart = parseDate(data?.oldestStart);
  const newestStart = parseDate(data?.newestStart);

  return {
    count: filteredCount.value,
    totalDistanceFormatted: formatDistanceSmart(totalDistanceMeters),
    totalDistanceFull: formatDistanceTooltip(totalDistanceMeters),
    totalDurationFormatted: formatDurationSmart(totalDurationMillis),
    totalDurationFull: formatDurationTooltip(totalDurationMillis),
    totalEnergy,
    totalEnergyFormatted: formatEnergy(totalEnergy),
    dateRangeLabel: oldestStart && newestStart ? `${formatDate(oldestStart)} - ${formatDate(newestStart)}` : '',
  };
});

const activityBreakdown = computed<ActivityBreakdownRow[]>(() => {
  const rows = overviewData.value?.activityBreakdown ?? [];
  const metric = effectiveActivityMetric.value;
  const maxMetricValue = Math.max(0, ...rows.map((row) => activityMetricValue(row, metric)));

  return rows
    .map((row) => {
      const type = row.activityType || 'UNKNOWN';
      const metricValue = activityMetricValue(row, metric);
      return {
        type,
        label: formatActivityLabel(type),
        count: Math.round(numberValue(row.trackCount)),
        distanceM: numberValue(row.distanceM),
        durationMs: numberValue(row.durationMs),
        energyWh: numberValue(row.energyWh),
        metricValue,
        valueFormatted: formatActivityMetric(metricValue, metric, maxMetricValue),
        valueFull: formatActivityMetricTooltip(metricValue, metric),
        color: ACTIVITY_COLORS[type] || ACTIVITY_FALLBACK_COLOR,
        icon: ACTIVITY_ICONS[type] || 'bi bi-activity',
      };
    })
    .sort((a, b) => b.metricValue - a.metricValue || b.count - a.count || a.label.localeCompare(b.label));
});

const donutOptions = computed(() => {
  const styles = getComputedStyle(document.documentElement);
  const token = (name: string) => styles.getPropertyValue(name).trim();
  const tooltipBg = token('--chart-tooltip-bg');
  const tooltipText = token('--chart-tooltip-text');
  const borderColor = token('--border-default');
  const sliceBorderColor = token('--surface-glass-heavy');
  const metricLabel = activityMetricOptions.value.find((option) => option.key === effectiveActivityMetric.value)?.label;

  return markRaw({
    chart: {
      type: 'pie',
      backgroundColor: 'transparent',
      spacing: [0, 0, 0, 0],
      height: 180,
      style: { fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif" },
    },
    title: { text: null },
    credits: { enabled: false },
    tooltip: {
      backgroundColor: tooltipBg,
      borderColor,
      borderRadius: 8,
      borderWidth: 1,
      shadow: false,
      style: { color: tooltipText, fontSize: '12px' },
      formatter(this: { point: { name: string; custom?: { formatted?: string } }; percentage?: number }) {
        const formatted = this.point.custom?.formatted ?? '';
        const pct = this.percentage != null ? ` (${this.percentage.toFixed(1)}%)` : '';
        return `<b>${this.point.name}</b><br/>${metricLabel}: ${formatted}${pct}`;
      },
    },
    plotOptions: {
      pie: {
        innerSize: '58%',
        borderWidth: 2,
        borderColor: sliceBorderColor,
        dataLabels: { enabled: false },
        states: { hover: { brightness: 0.05 } },
        cursor: 'default',
      },
    },
    series: [
      {
        name: metricLabel,
        data: activityBreakdown.value.map((item) => ({
          name: item.label,
          y: Math.max(0, item.metricValue),
          color: item.color,
          custom: { formatted: item.valueFormatted },
        })),
      },
    ],
    accessibility: { enabled: false },
  });
});

const trackRankings = computed<OverviewTrackRanking[]>(() => overviewData.value?.trackRankings ?? []);

const trackRankingByKey = computed(() => {
  const rankings = new Map<string, OverviewTrackRanking>();
  for (const ranking of trackRankings.value) {
    if (ranking.rowKey) rankings.set(ranking.rowKey, ranking);
  }
  return rankings;
});

const highlightRows = computed<DetailRow[]>(() => {
  const rows = trackRankings.value.map(toHighlightRow).filter((row): row is DetailRow => row != null);
  const commonActivity = toCommonActivityRow();
  if (commonActivity) rows.push(commonActivity);
  return rows;
});

const recentTracks = computed(() =>
  [...(overviewData.value?.recentActivities ?? [])]
    .sort((a, b) => numberValue(a.sortOrder) - numberValue(b.sortOrder))
    .map(toTrackView)
    .filter((track): track is TrackView => track != null)
);

const activePeriodRows = computed<PeriodViewRow[]>(() =>
  (overviewData.value?.activePeriods ?? []).map(toPeriodRow).filter((row): row is PeriodViewRow => row != null)
);

const periodDistributionByKey = computed(() => {
  const distributions = new Map<string, OverviewPeriodDistribution>();
  for (const distribution of overviewData.value?.periodDistributions ?? []) {
    if (distribution.periodType) distributions.set(distribution.periodType, distribution);
  }
  return distributions;
});

const milestoneRows = computed<DetailRow[]>(() =>
  (overviewData.value?.milestones ?? []).map(toMilestoneRow).filter((row): row is DetailRow => row != null)
);

const visibleMilestones = computed(() => {
  const rows = milestoneRows.value;
  if (rows.length <= MILESTONE_LIMIT) return rows;

  const first = rows.find((row) => row.key === 'first-activity');
  const latest = rows.find((row) => row.key === 'latest-activity');
  const energy = rows.find((row) => row.key === 'energy-1000');
  const distanceRows = rows.filter((row) => row.key.startsWith('distance-')).slice(-2);
  const ascentRows = rows.filter((row) => row.key.startsWith('ascent-')).slice(-2);
  return [first, latest, ...distanceRows, ...ascentRows, energy].filter((row): row is DetailRow => row != null);
});

const selectedHighlightRows = computed<DrilldownRow[]>(() => {
  const key = selectedHighlightKey.value;
  if (!key) return [];
  return (
    trackRankingByKey.value
      .get(key)
      ?.rows?.slice(0, DRILLDOWN_PREVIEW_LIMIT)
      .map((row) => toRankingDrilldownRow(key, row))
      .filter((row): row is DrilldownRow => row != null) ?? []
  );
});

const selectedHighlightTitle = computed(() => highlightMeta(selectedHighlightKey.value ?? '')?.label ?? '');

const selectedPeriodRows = computed<DrilldownRow[]>(() => {
  const key = selectedPeriodKey.value;
  if (!key) return [];
  return (
    periodDistributionByKey.value
      .get(key)
      ?.rows?.slice(0, DRILLDOWN_PREVIEW_LIMIT)
      .map(toPeriodDrilldownRow)
      .filter((row): row is DrilldownRow => row != null) ?? []
  );
});

const selectedPeriodTitle = computed(() => periodMeta(selectedPeriodKey.value ?? '')?.label ?? '');

onMounted(() => {
  void loadOverview();
});

onBeforeUnmount(() => {
  activeRequest.value?.abort();
});

watch(
  () => props.tracks,
  () => {
    void loadOverview();
  },
  { deep: false }
);

async function loadOverview() {
  const requestId = ++requestSerial;
  activeRequest.value?.abort();
  const controller = new AbortController();
  activeRequest.value = controller;
  loading.value = true;
  loadError.value = false;

  try {
    const data = await fetchStatisticsOverview(controller.signal);
    if (requestId !== requestSerial || controller.signal.aborted) return;
    overviewData.value = data;
    selectedHighlightKey.value = null;
    selectedPeriodKey.value = null;
  } catch {
    if (controller.signal.aborted) return;
    loadError.value = true;
    overviewData.value = null;
  } finally {
    if (requestId === requestSerial) loading.value = false;
  }
}

function showInfo(event: Event, text: string) {
  currentInfoText.value = text;
  infoPopover.value?.toggle(event);
}

function selectActivityMetric(metric: ActivityMetric) {
  if (metric === 'energy' && !hasEnergy.value) return;
  activityMetric.value = metric;
}

function openTrack(trackId: number | undefined) {
  if (trackId == null) return;
  emit('open-details', trackId);
}

function selectHighlight(key: string | undefined) {
  if (!key) return;
  selectedHighlightKey.value = selectedHighlightKey.value === key ? null : key;
}

function openHighlightExclusion(event: Event, row: DrilldownRow) {
  pendingHighlightExclusionRow.value = row;
  pendingHighlightExclusionReason.value = HIGHLIGHT_EXCLUSION_DEFAULT_REASON;
  highlightExclusionPopover.value?.toggle(event);
}

function setPendingHighlightExclusionReason(value: ExclusionReason) {
  pendingHighlightExclusionReason.value = value;
}

async function saveHighlightExclusion() {
  const row = pendingHighlightExclusionRow.value;
  if (row?.trackId == null) return;
  savingHighlightExclusion.value = true;

  try {
    await updateTrackStatisticsExclusion(row.trackId, {
      highlightExclusionReason: pendingHighlightExclusionReason.value,
    });
    highlightExclusionPopover.value?.hide?.();
    pendingHighlightExclusionRow.value = null;
    await loadOverview();
    toast.add({ severity: 'success', summary: 'Track excluded from highlights', life: 2200 });
  } catch (error) {
    console.warn('[statistics-overview] failed to exclude track from highlights', { trackId: row.trackId, error });
    toast.add({
      severity: 'error',
      summary: 'Exclusion failed',
      detail: 'Could not exclude this track from highlights.',
      life: 4000,
    });
  } finally {
    savingHighlightExclusion.value = false;
  }
}

function selectPeriod(key: string | undefined) {
  if (!key) return;
  selectedPeriodKey.value = selectedPeriodKey.value === key ? null : key;
}

function toHighlightRow(ranking: OverviewTrackRanking): DetailRow | null {
  const key = ranking.rowKey ?? '';
  const meta = highlightMeta(key);
  if (!meta) return null;
  const row = ranking.rows?.[0];
  if (!row) return null;
  const track = toTrackView(row);
  return {
    key,
    label: meta.label,
    subtitle: track?.displayName ?? fallbackTrackName(row.trackId),
    value: highlightValue(key, numberValue(row.value)),
    icon: meta.icon,
    color: meta.color,
    trackId: row.trackId,
    info: meta.info,
    drilldownKey: key,
    drilldownCount: ranking.rows?.length ?? 0,
  };
}

function toCommonActivityRow(): DetailRow | null {
  const row = [...(overviewData.value?.activityBreakdown ?? [])].sort(
    (a, b) =>
      numberValue(b.trackCount) - numberValue(a.trackCount) ||
      numberValue(b.distanceM) - numberValue(a.distanceM) ||
      formatActivityLabel(a.activityType || 'UNKNOWN').localeCompare(formatActivityLabel(b.activityType || 'UNKNOWN'))
  )[0];
  if (!row) return null;
  const activityType = row.activityType || 'UNKNOWN';
  return {
    key: 'common-activity',
    label: 'Most common activity',
    subtitle: formatActivityLabel(activityType),
    value: `${formatLocaleNumber(Math.round(numberValue(row.trackCount)))} tracks`,
    icon: ACTIVITY_ICONS[activityType] || 'bi bi-activity',
    color: ACTIVITY_COLORS[activityType] || ACTIVITY_FALLBACK_COLOR,
  };
}

function toPeriodRow(row: OverviewPeriodRow): PeriodViewRow | null {
  const key = row.periodType ?? '';
  const meta = periodMeta(key);
  if (!meta) return null;
  return {
    key,
    label: meta.label,
    subtitle: row.label || row.periodKey || '',
    durationFormatted: formatDurationSmart(numberValue(row.durationMs)),
    distanceFormatted: formatDistanceSmart(numberValue(row.distanceM)),
    count: Math.round(numberValue(row.trackCount)),
    icon: meta.icon,
    color: meta.color,
    drilldownKey: key,
  };
}

function toMilestoneRow(row: OverviewTrackRef): DetailRow | null {
  const key = row.rowKey ?? '';
  const meta = milestoneMeta(key);
  if (!meta) return null;
  const track = toTrackView(row);
  return {
    key,
    label: meta.label,
    subtitle: track?.displayName ?? fallbackTrackName(row.trackId),
    value: milestoneValue(key, row, track),
    icon: meta.icon,
    color: meta.color,
    trackId: row.trackId,
  };
}

function highlightMeta(key: string): { label: string; icon: string; color: string; info?: string } | null {
  switch (key) {
    case 'longest-distance':
      return { label: 'Longest track', icon: 'bi bi-signpost-split', color: 'var(--chart-series-2)' };
    case 'longest-duration':
      return { label: 'Longest duration', icon: 'bi bi-clock', color: 'var(--info)' };
    case 'biggest-ascent':
      return { label: 'Biggest ascent', icon: 'bi bi-arrow-up-right', color: 'var(--warning-text)' };
    case 'quickest-ascent':
      return {
        label: 'Quickest ascent',
        icon: 'bi bi-graph-up-arrow',
        color: 'var(--success)',
        info: INFO_QUICKEST_ASCENT,
      };
    case 'most-energy':
      return {
        label: 'Most energy',
        icon: 'bi bi-lightning-charge',
        color: 'var(--chart-series-3)',
        info: INFO_ENERGY,
      };
    case 'fastest-speed':
      return { label: 'Fastest 30 s speed', icon: 'bi bi-speedometer2', color: 'var(--error)', info: INFO_30S_SPEED };
    case 'peak-power':
      return { label: 'Peak 30 s power', icon: 'bi bi-activity', color: 'var(--success)', info: INFO_30S_POWER };
    case 'common-activity':
      return { label: 'Most common activity', icon: 'bi bi-activity', color: 'var(--success)' };
    default:
      return null;
  }
}

function periodMeta(key: string): { label: string; icon: string; color: string } | null {
  switch (key) {
    case 'day':
      return { label: 'Most active day', icon: 'bi bi-calendar-day', color: 'var(--chart-series-1)' };
    case 'week':
      return { label: 'Most active week', icon: 'bi bi-calendar-week', color: 'var(--chart-series-2)' };
    case 'month':
      return { label: 'Most active month', icon: 'bi bi-calendar3', color: 'var(--chart-series-3)' };
    case 'weekday':
      return { label: 'Most active weekday', icon: 'bi bi-calendar-heart', color: 'var(--success)' };
    default:
      return null;
  }
}

function milestoneMeta(key: string): { label: string; icon: string; color: string } | null {
  if (key === 'first-activity')
    return { label: 'First activity', icon: 'bi bi-sunrise', color: 'var(--chart-series-1)' };
  if (key === 'latest-activity') return { label: 'Latest activity', icon: 'bi bi-clock-history', color: 'var(--info)' };
  if (key === 'distance-10000')
    return { label: 'First 10 km track', icon: 'bi bi-signpost-split', color: 'var(--chart-series-2)' };
  if (key === 'distance-25000')
    return { label: 'First 25 km track', icon: 'bi bi-signpost-split', color: 'var(--chart-series-2)' };
  if (key === 'distance-50000')
    return { label: 'First 50 km track', icon: 'bi bi-signpost-split', color: 'var(--chart-series-2)' };
  if (key === 'distance-100000')
    return { label: 'First 100 km track', icon: 'bi bi-signpost-split', color: 'var(--chart-series-2)' };
  if (key === 'ascent-500')
    return { label: 'First 500 m ascent', icon: 'bi bi-arrow-up-right', color: 'var(--warning-text)' };
  if (key === 'ascent-1000')
    return { label: 'First 1000 m ascent', icon: 'bi bi-arrow-up-right', color: 'var(--warning-text)' };
  if (key === 'ascent-2000')
    return { label: 'First 2000 m ascent', icon: 'bi bi-arrow-up-right', color: 'var(--warning-text)' };
  if (key === 'energy-1000')
    return { label: 'First 1000 Wh activity', icon: 'bi bi-lightning-charge', color: 'var(--chart-series-3)' };
  return null;
}

function highlightValue(key: string, value: number): string {
  if (key === 'longest-distance') return formatDistanceSmart(value);
  if (key === 'longest-duration') return formatDurationSmart(value);
  if (key === 'biggest-ascent') return formatMeters(value);
  if (key === 'quickest-ascent') return `${formatLocaleNumber(Math.round(value))} m/h`;
  if (key === 'most-energy') return formatEnergy(value);
  if (key === 'fastest-speed') return `${formatNumber(value, 1)} km/h`;
  if (key === 'peak-power') return `${formatLocaleNumber(Math.round(value))} W`;
  return '';
}

function milestoneValue(key: string, row: OverviewTrackRef, track: TrackView | null): string {
  if (key === 'first-activity' || key === 'latest-activity') return formatTrackDate(track?.startDate);
  if (key.startsWith('distance-')) return formatDistanceSmart(numberValue(row.value ?? track?.distanceM));
  if (key.startsWith('ascent-')) return formatMeters(numberValue(row.value ?? track?.ascentM));
  if (key === 'energy-1000') return formatEnergy(numberValue(row.value ?? track?.energyWh));
  return '';
}

function toRankingDrilldownRow(key: string, row: OverviewTrackRef): DrilldownRow | null {
  const track = toTrackView(row);
  if (!track) return null;
  return {
    key: `${key}-${row.trackId}`,
    trackId: track.trackId,
    rank: Math.round(numberValue(row.sortOrder)),
    label: track.displayName,
    subtitle: formatTrackDate(track.startDate),
    activityType: track.activityType,
    value: highlightValue(key, numberValue(row.value)),
    distanceFormatted: formatDistanceSmart(track.distanceM),
    durationFormatted: formatDurationSmart(track.durationMs),
  };
}

function toPeriodDrilldownRow(row: OverviewPeriodRow): DrilldownRow | null {
  const rank = Math.round(numberValue(row.sortOrder));
  return {
    key: `${row.periodType}-${row.periodKey}`,
    rank,
    label: row.label || row.periodKey || '',
    subtitle: `${Math.round(numberValue(row.trackCount))} tracks`,
    value: formatDurationSmart(numberValue(row.durationMs)),
    distanceFormatted: formatDistanceSmart(numberValue(row.distanceM)),
  };
}

function toTrackView(row: OverviewTrackRef): TrackView | null {
  const trackId = Number(row.trackId);
  if (!Number.isFinite(trackId)) return null;
  const track = tracksById.value.get(trackId);
  if (!track) {
    return {
      trackId,
      displayName: fallbackTrackName(trackId),
      distanceM: 0,
      durationMs: 0,
      ascentM: 0,
      energyWh: 0,
      speed30Kmh: 0,
      power30W: 0,
    };
  }
  return {
    trackId,
    displayName: displayName(track),
    activityType: track.activityType,
    startDate: parseDate(track.startDate) ?? undefined,
    distanceM: numberValue(track.trackLengthInMeter),
    durationMs: trackDurationMs(track),
    ascentM: numberValue(track.ascentInMeter),
    energyWh: numberValue(track.energyNetTotalWh),
    speed30Kmh: numberValue(track.speedInKmh30sMax),
    power30W: numberValue(track.powerWatts30sMax),
  };
}

function trackDurationMs(track: GpsTrack): number {
  const movingSeconds = numberValue(track.trackDurationInMotionSecs);
  if (movingSeconds > 0) return movingSeconds * 1000;
  const start = parseDate(track.startDate);
  const end = parseDate(track.endDate);
  return start && end ? Math.max(0, end.getTime() - start.getTime()) : 0;
}

function activityMetricValue(data: OverviewActivityBreakdown, metric: ActivityMetric): number {
  if (metric === 'distance') return numberValue(data.distanceM);
  if (metric === 'duration') return numberValue(data.durationMs);
  if (metric === 'energy') return numberValue(data.energyWh);
  return numberValue(data.trackCount);
}

function formatActivityMetric(value: number, metric: ActivityMetric, maxValue: number): string {
  if (metric === 'distance') return formatDistanceSmart(value, maxValue);
  if (metric === 'duration') return formatDurationSmart(value, maxValue);
  if (metric === 'energy') return formatEnergy(value);
  return formatLocaleNumber(Math.round(value));
}

function formatActivityMetricTooltip(value: number, metric: ActivityMetric): string {
  if (metric === 'distance') return formatDistanceTooltip(value);
  if (metric === 'duration') return formatDurationTooltip(value);
  if (metric === 'energy') return formatEnergy(value);
  return `${formatLocaleNumber(Math.round(value))} tracks`;
}

function formatEnergy(value: number): string {
  return `${formatLocaleNumber(Math.round(value))} Wh`;
}

function formatMeters(value: number): string {
  return `${formatLocaleNumber(Math.round(value))} m`;
}

function formatTrackDate(value: Date | string | number | null | undefined): string {
  return formatDateAndTime(value);
}

function displayName(track: GpsTrack): string {
  return (
    cleanText(track.trackName) ||
    cleanText(track.metaName) ||
    cleanText(track.trackDescription) ||
    cleanText(track.metaDescription) ||
    fallbackTrackName(track.id)
  );
}

function fallbackTrackName(trackId: number | string | undefined): string {
  return `Track ${trackId ?? ''}`.trim();
}

function cleanText(value: string | undefined | null): string {
  return String(value || '').trim();
}

function formatActivityLabel(type: string): string {
  return type
    .toLowerCase()
    .split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
}

function parseDate(value: Date | string | number | undefined | null): Date | null {
  if (!value) return null;
  const date = value instanceof Date ? value : new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

function numberValue(value: unknown): number {
  const numberValue = Number(value);
  return Number.isFinite(numberValue) ? numberValue : 0;
}
</script>

<style scoped>
.overview {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
  padding: 0 1rem 1rem;
}

.filter-banner {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 0.75rem;
  border-radius: 8px;
  background: var(--accent-bg);
  border: 1px solid var(--accent-subtle);
  font-size: var(--text-sm-size);
  color: var(--accent-text);
}

.filter-banner__icon {
  font-size: var(--text-xs-size);
  opacity: 0.7;
}

.hero-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0.75rem;
}

.hero-tile {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.35rem;
  min-width: 0;
  padding: 1rem 0.65rem 0.9rem;
  border-radius: 8px;
  background: transparent;
  border: 1px solid var(--border-default);
  transition:
    background 0.15s,
    border-color 0.15s;
}

.hero-tile:hover {
  background: var(--surface-hover);
  border-color: var(--border-medium);
}

.hero-tile__icon-wrap {
  width: 2.2rem;
  height: 2.2rem;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: color-mix(in srgb, var(--tile-accent) 12%, transparent);
  color: var(--tile-accent);
  font-size: var(--text-base-size);
}

.hero-tile__value {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--text-lg-size);
  font-weight: 750;
  color: var(--text-primary);
  line-height: var(--text-lg-lh);
}

.hero-tile__label {
  font-size: var(--text-2xs-size);
  font-weight: 650;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--text-muted);
  display: flex;
  align-items: center;
  gap: 0;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 0.85rem;
}

.overview-card {
  min-width: 0;
  padding: 0.9rem;
  border: 1px solid var(--border-default);
  border-radius: 8px;
  background: transparent;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  min-width: 0;
  font-size: var(--text-xs-size);
  font-weight: 750;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--text-muted);
  padding-bottom: 0.65rem;
}

.section-header--with-actions {
  justify-content: space-between;
  align-items: flex-start;
  gap: 0.75rem;
}

.section-header__title {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  min-width: 0;
}

.section-header__note {
  flex: 0 1 auto;
  min-width: 0;
  max-width: 12rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-faint);
  font-size: var(--text-2xs-size);
  line-height: var(--text-2xs-lh);
  font-weight: 650;
  letter-spacing: 0;
  text-transform: none;
}

.section-header__note--button {
  padding: 0;
  border: 0;
  background: transparent;
  cursor: pointer;
  text-align: right;
}

.section-header__note--button:hover,
.section-header__note--button:focus-visible {
  color: var(--accent-text);
  outline: none;
}

.section-header__link {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0.35rem;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--accent-text);
  cursor: pointer;
  font: inherit;
  font-size: var(--text-xs-size);
  font-weight: 700;
  text-align: right;
  white-space: nowrap;
}

.section-header__link:hover {
  color: var(--accent-muted);
}

.subsection-header {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
  font-weight: 750;
  padding: 0.15rem 0 0.35rem;
}

.metric-toggle {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 0.3rem;
}

.metric-toggle__btn {
  border: 1px solid var(--border-default);
  border-radius: 999px;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  font: inherit;
  font-size: var(--text-2xs-size);
  line-height: var(--text-2xs-lh);
  font-weight: 650;
  padding: 0.25rem 0.55rem;
  transition:
    background 0.15s,
    border-color 0.15s,
    color 0.15s;
}

.metric-toggle__btn:hover,
.metric-toggle__btn--active {
  background: var(--accent-bg);
  border-color: var(--accent-subtle);
  color: var(--accent-text);
}

.activity-layout {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr);
  align-items: center;
  gap: 0.75rem;
}

.activity-chart-wrap {
  min-width: 0;
}

.donut-chart {
  width: 180px;
  height: 180px;
}

.activity-legend,
.insight-list,
.recent-list,
.period-list,
.milestone-list {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  min-width: 0;
}

.legend-row {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  min-width: 0;
  font-size: var(--text-sm-size);
  color: var(--text-secondary);
}

.legend-swatch {
  flex: 0 0 8px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.legend-icon {
  flex: 0 0 auto;
  font-size: var(--text-sm-size);
}

.legend-type {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 550;
}

.legend-count {
  flex: 0 0 auto;
  font-weight: 750;
  color: var(--text-primary);
  font-size: var(--text-xs-size);
}

.legend-dist {
  flex: 0 0 auto;
  min-width: 4.3rem;
  text-align: right;
  font-size: var(--text-xs-size);
  color: var(--text-muted);
}

.insight-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 0.25rem;
  border-bottom: 1px solid var(--border-subtle);
}

.insight-row:last-child {
  border-bottom: none;
}

.insight-row__main,
.recent-row,
.milestone-row {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  width: 100%;
  min-width: 0;
  padding: 0.58rem 0;
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font: inherit;
  text-align: left;
}

.insight-row__main:disabled,
.insight-row__main--static {
  cursor: default;
}

.insight-row__main--active .insight-row__label,
.period-row--active .period-row__label {
  color: var(--accent-text);
}

.insight-row__main:not(:disabled):hover .insight-row__label,
.recent-row:hover .recent-row__name,
.milestone-row:hover .milestone-row__label,
.period-row:hover .period-row__label,
.drilldown-row:not(.drilldown-row--with-action):hover .drilldown-row__label,
.drilldown-row__open:hover .drilldown-row__label {
  color: var(--accent-text);
}

.insight-row__icon,
.period-row__icon,
.milestone-row__icon {
  flex: 0 0 2rem;
  width: 2rem;
  height: 2rem;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: var(--surface-hover);
  font-size: var(--text-base-size);
}

.insight-row__text,
.recent-row__main,
.period-row__text,
.milestone-row__text {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.12rem;
}

.insight-row__label,
.recent-row__name,
.period-row__label,
.milestone-row__label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--text-sm-size);
  font-weight: 700;
  color: var(--text-primary);
  transition: color 0.15s;
}

.insight-row__subtitle,
.recent-row__meta,
.period-row__subtitle,
.milestone-row__subtitle {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--text-xs-size);
  color: var(--text-muted);
}

.recent-row__meta {
  display: flex;
  align-items: center;
  gap: 0.4rem;
}

.insight-row__value,
.period-row__value,
.milestone-row__value {
  flex: 0 0 auto;
  max-width: 8rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-align: right;
  font-size: var(--text-sm-size);
  font-weight: 750;
  color: var(--text-primary);
}

.period-row__value {
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
}

.period-row__value small {
  font-size: var(--text-2xs-size);
  line-height: var(--text-2xs-lh);
  font-weight: 550;
  color: var(--text-muted);
}

.insight-row__arrow,
.recent-row__arrow,
.period-row__arrow,
.drilldown-row__arrow {
  flex: 0 0 auto;
  color: var(--text-faint);
  font-size: var(--text-xs-size);
}

.recent-row,
.period-row,
.milestone-row {
  border-bottom: 1px solid var(--border-subtle);
}

.recent-row:last-child,
.period-row:last-child,
.milestone-row:last-child {
  border-bottom: 0;
}

.recent-row__shape {
  flex: 0 0 auto;
  opacity: 0.72;
}

.recent-row:hover .recent-row__shape {
  opacity: 1;
}

.recent-row__stats {
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 0.1rem;
  max-width: 5.8rem;
  font-size: var(--text-sm-size);
  font-weight: 750;
  color: var(--text-primary);
}

.recent-row__duration {
  font-size: var(--text-xs-size);
  font-weight: 550;
  color: var(--text-muted);
}

.period-row {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  width: 100%;
  min-width: 0;
  padding: 0.58rem 0;
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font: inherit;
  text-align: left;
}

.drilldown-panel {
  margin-top: 0.55rem;
  padding-top: 0.6rem;
  border-top: 1px solid var(--border-subtle);
}

.drilldown-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  padding-bottom: 0.35rem;
  font-size: var(--text-xs-size);
  font-weight: 750;
  color: var(--text-secondary);
}

.drilldown-panel__close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.7rem;
  height: 1.7rem;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--text-faint);
  cursor: pointer;
}

.drilldown-panel__close:hover {
  background: var(--surface-hover);
  color: var(--accent-text);
}

.drilldown-list {
  display: flex;
  flex-direction: column;
  min-width: 0;
  max-height: 19rem;
  overflow: auto;
}

.drilldown-row {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  width: 100%;
  min-width: 0;
  padding: 0.48rem 0;
  border: 0;
  border-bottom: 1px solid var(--border-subtle);
  background: transparent;
  color: inherit;
  cursor: pointer;
  font: inherit;
  text-align: left;
}

.drilldown-row:last-child {
  border-bottom: 0;
}

.drilldown-row--static {
  cursor: default;
}

.drilldown-row--with-action {
  gap: 0.35rem;
  cursor: default;
}

.drilldown-row__open {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 0.55rem;
  padding: 0;
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font: inherit;
  text-align: left;
}

.drilldown-row__exclude {
  flex: 0 0 auto;
  width: 1.9rem;
  height: 1.9rem;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--border-subtle);
  border-radius: 8px;
  background: transparent;
  color: var(--text-faint);
  cursor: pointer;
  transition:
    background 0.15s,
    border-color 0.15s,
    color 0.15s;
}

.drilldown-row__exclude:hover:not(:disabled),
.drilldown-row__exclude:focus-visible {
  background: var(--warning-bg);
  border-color: rgba(217, 119, 6, 0.28);
  color: var(--warning-text);
  outline: none;
}

.drilldown-row__exclude:disabled {
  cursor: wait;
  opacity: 0.55;
}

.drilldown-row__rank {
  flex: 0 0 1.75rem;
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  font-weight: 750;
  text-align: center;
}

.drilldown-row__main {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.12rem;
}

.drilldown-row__label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--text-sm-size);
  font-weight: 700;
  color: var(--text-primary);
  transition: color 0.15s;
}

.drilldown-row__meta {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-muted);
  font-size: var(--text-xs-size);
}

.drilldown-row__stats {
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 0.1rem;
  max-width: 7rem;
  color: var(--text-primary);
  font-size: var(--text-sm-size);
  font-weight: 750;
}

.drilldown-row__stats small {
  color: var(--text-muted);
  font-size: var(--text-2xs-size);
  line-height: var(--text-2xs-lh);
  font-weight: 550;
}

.rhythm-section {
  display: flex;
  flex-direction: column;
}

.rhythm-block + .rhythm-block {
  margin-top: 0.8rem;
  padding-top: 0.65rem;
  border-top: 1px solid var(--border-subtle);
}

.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  min-height: 9rem;
  border: 1px solid var(--border-default);
  border-radius: 8px;
  color: var(--text-muted);
  font-size: var(--text-sm-size);
}

.empty-state--error {
  color: var(--error);
}

.empty-state__icon {
  color: var(--text-faint);
}

.date-range {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.4rem;
  font-size: var(--text-xs-size);
  color: var(--text-muted);
  padding-top: 0.1rem;
}

.info-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  padding: 0;
  border: none;
  background: transparent;
  cursor: pointer;
  color: var(--text-faint);
  font-size: var(--text-2xs-size);
  line-height: var(--text-2xs-lh);
  margin-left: 3px;
  transition: color 0.15s;
}

.info-btn--row {
  margin-left: 0;
  width: 1.8rem;
  height: 1.8rem;
}

.info-btn:hover,
.info-btn:focus-visible {
  color: var(--accent-muted);
  outline: none;
}

.overview-info-text {
  max-width: 280px;
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
  color: var(--text-secondary);
  margin: 0;
  padding: 0.1rem 0;
}

.highlight-exclusion-popover {
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
  width: min(18rem, calc(100vw - 2rem));
  padding: 0.1rem 0;
}

.highlight-exclusion-popover__title {
  color: var(--text-primary);
  font-size: var(--text-sm-size);
  font-weight: 750;
}

.highlight-exclusion-popover__track {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-muted);
  font-size: var(--text-xs-size);
}

.highlight-exclusion-popover__select {
  width: 100%;
}

.highlight-exclusion-popover__action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.4rem;
  width: 100%;
  min-height: 2.1rem;
  padding: 0.45rem 0.7rem;
  border: 1px solid rgba(217, 119, 6, 0.32);
  border-radius: 8px;
  background: var(--warning-bg);
  color: var(--warning-text);
  cursor: pointer;
  font: inherit;
  font-size: var(--text-sm-size);
  font-weight: 750;
}

.highlight-exclusion-popover__action:hover:not(:disabled),
.highlight-exclusion-popover__action:focus-visible {
  border-color: rgba(217, 119, 6, 0.52);
  outline: none;
}

.highlight-exclusion-popover__action:disabled {
  cursor: wait;
  opacity: 0.62;
}

@media (max-width: 980px) {
  .hero-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .dashboard-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .overview {
    padding: 0 0.75rem 1rem;
  }

  .hero-stats,
  .dashboard-grid {
    grid-template-columns: 1fr;
  }

  .section-header--with-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .section-header__note {
    max-width: 100%;
  }

  .metric-toggle {
    justify-content: flex-start;
  }

  .activity-layout {
    grid-template-columns: 1fr;
    justify-items: center;
  }

  .activity-legend {
    width: 100%;
  }

  .recent-row__shape {
    display: none;
  }

  .insight-row__value,
  .period-row__value,
  .milestone-row__value,
  .recent-row__stats,
  .drilldown-row__stats {
    max-width: 5.8rem;
  }
}
</style>
