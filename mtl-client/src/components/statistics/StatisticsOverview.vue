<template>
  <div class="overview">

    <!-- ── Filter banner ── -->
    <div class="filter-banner" v-if="tracksCount != null && tracksCount !== totalCount">
      <i class="bi bi-funnel-fill filter-banner__icon"></i>
      <span>Showing <strong>{{ tracksCount }}</strong> of {{ totalCount }} tracks</span>
    </div>

    <!-- ── Hero stats ── -->
    <div class="hero-stats">
      <div class="hero-tile">
        <div class="hero-tile__icon-wrap" style="--tile-accent: var(--chart-series-1)">
          <i class="bi bi-pin-map"></i>
        </div>
        <div class="hero-tile__value">{{ summary.count }}</div>
        <div class="hero-tile__label">Tracks</div>
      </div>
      <div class="hero-tile">
        <div class="hero-tile__icon-wrap" style="--tile-accent: var(--chart-series-2)">
          <i class="bi bi-signpost-split"></i>
        </div>
        <div class="hero-tile__value"
             v-tooltip.top="{ value: summary.totalDistanceFull, showDelay: 400 }">{{ summary.totalDistanceFormatted }}</div>
        <div class="hero-tile__label">Distance</div>
      </div>
      <div class="hero-tile">
        <div class="hero-tile__icon-wrap" style="--tile-accent: var(--info)">
          <i class="bi bi-clock"></i>
        </div>
        <div class="hero-tile__value"
             v-tooltip.top="{ value: summary.totalDurationFull, showDelay: 400 }">{{ summary.totalDurationFormatted }}</div>
        <div class="hero-tile__label">Duration</div>
      </div>
      <div class="hero-tile" v-if="summary.totalEnergy > 0">
        <div class="hero-tile__icon-wrap" style="--tile-accent: var(--chart-series-3)">
          <i class="bi bi-lightning-charge"></i>
        </div>
        <div class="hero-tile__value">{{ summary.totalEnergyFormatted }}</div>
        <div class="hero-tile__label hero-tile__label--info">
          Energy
          <button class="info-btn" @click.stop="showEnergyInfo($event)" aria-label="About energy"><i class="bi bi-info-circle"></i></button>
        </div>
      </div>
    </div>

    <!-- ── Activity breakdown donut ── -->
    <div class="activity-section" v-if="activityBreakdown.length > 0">
      <div class="section-header">
        <i class="bi bi-pie-chart"></i>
        <span>Activity Breakdown</span>
      </div>
      <div class="activity-layout">
        <div class="activity-chart-wrap">
          <highcharts :options="donutOptions" class="donut-chart" />
        </div>
        <div class="activity-legend">
          <div class="legend-row" v-for="item in activityBreakdown" :key="item.type">
            <span class="legend-swatch" :style="{ background: item.color }"></span>
            <i :class="item.icon" class="legend-icon" :style="{ color: item.color }"></i>
            <span class="legend-type">{{ item.label }}</span>
            <span class="legend-count">{{ item.count }}</span>
            <span class="legend-dist"
                  v-tooltip.top="{ value: item.distanceFull, showDelay: 400 }">{{ item.distanceFormatted }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- ── Latest activity card ── -->
    <div class="latest-section" v-if="latestTrack">
      <div class="section-header">
        <i class="bi bi-clock-history"></i>
        <span>Latest Activity</span>
      </div>
      <button class="latest-card" @click="latestTrack && latestTrack.id !== undefined && $emit('open-details', latestTrack.id)">
        <TrackShapePreview :trackId="latestTrack.id!" :width="72" :height="52" class="latest-card__shape" />
        <div class="latest-card__left">
          <div class="latest-card__name">{{ latestTrack.name }}</div>
          <div class="latest-card__meta">
            <ActivityTypeBadge v-if="latestTrack.activityType" :type="latestTrack.activityType" size="xs" />
            <span class="latest-card__date">{{ latestTrack.dateLabel }}</span>
          </div>
        </div>
        <div class="latest-card__stats">
          <span class="latest-card__stat"
                v-tooltip.top="{ value: latestTrack.distanceFull, showDelay: 400 }">{{ latestTrack.distanceFormatted }}</span>
          <span class="latest-card__stat latest-card__stat--muted"
                v-tooltip.top="{ value: latestTrack.durationFull, showDelay: 400 }">{{ latestTrack.durationFormatted }}</span>
        </div>
        <i class="bi bi-chevron-right latest-card__arrow"></i>
      </button>
    </div>

    <!-- ── Date range footer ── -->
    <div class="date-range" v-if="summary.dateRangeLabel">
      <i class="bi bi-calendar3"></i>
      <span>{{ summary.dateRangeLabel }}</span>
    </div>

    <!-- ── Energy info popover ── -->
    <Popover ref="energyInfoPopover" appendTo="body">
      <p class="overview-info-text">Physical mechanical energy output, measured from power-sensor data (Wh). This is not an estimate of calorie or metabolic energy — it reflects actual and precise power data recorded by your device.</p>
    </Popover>
  </div>
</template>

<script setup lang="ts">
import { computed, markRaw, ref } from 'vue';
import { formatDistance, formatDuration, formatDate, formatDateAndTime, formatDistanceSmart, formatDurationSmart, formatDurationTooltip, formatDistanceTooltip, formatLocaleNumber } from '@/utils/Utils';
import type { GpsTrack } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import TrackShapePreview from '@/components/ui/TrackShapePreview.vue';
import ActivityTypeBadge, { ACTIVITY_COLORS, ACTIVITY_FALLBACK_COLOR, ACTIVITY_ICONS, activityIconFor } from '@/components/ui/ActivityTypeBadge.vue';

const energyInfoPopover = ref<any>(null);
function showEnergyInfo(event: Event) {
  energyInfoPopover.value?.toggle(event);
}

const props = defineProps<{
  tracks?: GpsTrack[];
  tracksCount?: number;
  unfilteredTotal?: number;
}>();

const emit = defineEmits<{
  (event: 'open-details', id: number | string): void;
}>();

const totalCount = computed(() => props.unfilteredTotal ?? (props.tracks ?? []).length);

const summary = computed(() => {
  const items = props.tracks ?? [];
  const count = items.length;
  const totalDistanceMeters = items.reduce((sum: number, t: GpsTrack) => sum + Number(t?.trackLengthInMeter || 0), 0);
  const totalEnergy = items.reduce((sum: number, t: GpsTrack) => sum + Number(t?.energyNetTotalWh || 0), 0);

  const parsed = items.map((t: GpsTrack) => {
    const s = t?.startDate ? new Date(t.startDate) : null;
    const motionSecs = t?.trackDurationInMotionSecs != null ? Number(t.trackDurationInMotionSecs) : null;
    const e = t?.endDate ? new Date(t.endDate) : null;
    const elapsedMillis = s && e ? Math.max(0, e.getTime() - s.getTime()) : 0;
    const dur = motionSecs != null ? motionSecs * 1000 : elapsedMillis;
    return { start: s, dur };
  });

  const totalDurationMillis = parsed.reduce((sum: number, p) => sum + p.dur, 0);
  const datedItems = parsed.filter((p) => p.start && !Number.isNaN((p.start as Date).getTime()));
  const sorted = [...datedItems].sort((a, b) => b.start!.getTime() - a.start!.getTime());
  const newestStart: Date | null = sorted[0]?.start ?? null;
  const oldestStart: Date | null = [...datedItems].sort((a, b) => a.start!.getTime() - b.start!.getTime())[0]?.start ?? null;

  return {
    count,
    totalDistanceFormatted: formatDistanceSmart(totalDistanceMeters),
    totalDistanceFull:      formatDistanceTooltip(totalDistanceMeters),
    totalDurationFormatted: formatDurationSmart(totalDurationMillis),
    totalDurationFull:      formatDurationTooltip(totalDurationMillis),
    totalEnergy,
    totalEnergyFormatted: formatLocaleNumber(Math.round(totalEnergy)) + ' Wh',
    dateRangeLabel: oldestStart && newestStart
      ? `${formatDate(oldestStart)} – ${formatDate(newestStart)}`
      : '',
  };
});

const latestTrack = computed(() => {
  const items = props.tracks ?? [];
  if (items.length === 0) return null;
  const withDates = items
    .filter((t: GpsTrack) => t?.startDate)
    .sort((a: GpsTrack, b: GpsTrack) => new Date(b.startDate!).getTime() - new Date(a.startDate!).getTime());
  const t = withDates[0];
  if (!t) return null;
  const start = new Date(t.startDate!);
  const end = t.endDate ? new Date(t.endDate) : null;
  const motionSecs = t.trackDurationInMotionSecs != null ? Number(t.trackDurationInMotionSecs) : null;
  const elapsedMs = end ? Math.max(0, end.getTime() - start.getTime()) : 0;
  const durMs = motionSecs != null ? motionSecs * 1000 : elapsedMs;
  const distM = Number(t.trackLengthInMeter || 0);
  return {
    id: t.id,
    name: String(t.trackName || t.trackDescription || `Track ${t.id}`).trim(),
    activityType: t.activityType || '',
    dateLabel: formatDateAndTime(start),
    distanceFormatted: formatDistanceSmart(distM),
    distanceFull:      formatDistanceTooltip(distM),
    durationFormatted: formatDurationSmart(durMs),
    durationFull:      formatDurationTooltip(durMs),
  };
});

const activityBreakdown = computed(() => {
  const items = props.tracks ?? [];
  const groups: Record<string, { count: number; distanceM: number }> = {};
  for (const t of items) {
    const type = String(t?.activityType || 'UNKNOWN');
    if (!groups[type]) groups[type] = { count: 0, distanceM: 0 };
    groups[type].count++;
    groups[type].distanceM += Number(t?.trackLengthInMeter || 0);
  }
  const maxDistM = Math.max(0, ...Object.values(groups).map(g => g.distanceM));
  return Object.entries(groups)
    .sort((a, b) => b[1].count - a[1].count)
    .map(([type, data]) => ({
      type,
      label: type.charAt(0) + type.slice(1).toLowerCase(),
      count: data.count,
      distanceM: data.distanceM,
      distanceFormatted: formatDistanceSmart(data.distanceM, maxDistM),
      distanceFull:      formatDistanceTooltip(data.distanceM),
      color: ACTIVITY_COLORS[type] || ACTIVITY_FALLBACK_COLOR,
      icon: ACTIVITY_ICONS[type] || 'bi bi-activity',
    }));
});

const donutOptions = computed(() => {
  const isDark = document.documentElement.getAttribute('data-theme') === 'dark';
  const tooltipBg = isDark ? 'rgba(15,23,42,0.97)' : 'rgba(255,255,255,0.97)';
  const tooltipText = isDark ? '#e2e8f0' : '#334155';
  const borderColor = isDark ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.08)';

  return markRaw({
    chart: {
      type: 'pie',
      backgroundColor: 'transparent',
      spacing: [0, 0, 0, 0],
      height: 170,
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
      pointFormat: '<b>{point.name}</b>: {point.y} tracks ({point.percentage:.1f}%)',
    },
    plotOptions: {
      pie: {
        innerSize: '58%',
        borderWidth: 2,
        borderColor: isDark ? 'rgba(15,23,42,1)' : 'rgba(255,255,255,1)',
        dataLabels: { enabled: false },
        states: { hover: { brightness: 0.05 } },
        cursor: 'default',
      },
    },
    series: [{
      name: 'Activities',
      data: activityBreakdown.value.map(item => ({
        name: item.label,
        y: item.count,
        color: item.color,
      })),
    }],
    accessibility: { enabled: false },
  });
});
</script>

<style scoped>
.overview {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
  padding: 0.75rem 1rem 1.5rem;
}

/* ── Filter banner ── */
.filter-banner {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 0.75rem;
  border-radius: 10px;
  background: var(--accent-bg);
  border: 1px solid var(--accent-subtle);
  font-size: 0.82rem;
  color: var(--accent-text);
}
.filter-banner__icon {
  font-size: 0.75rem;
  opacity: 0.7;
}

/* ── Hero stat tiles ── */
.hero-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(7rem, 1fr));
  gap: 0.6rem;
}

.hero-tile {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.35rem;
  padding: 1rem 0.5rem 0.85rem;
  border-radius: 14px;
  background: var(--surface-elevated);
  border: 1px solid var(--border-default);
  transition: background 0.15s, border-color 0.15s;
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
  border-radius: 10px;
  background: color-mix(in srgb, var(--tile-accent) 12%, transparent);
  color: var(--tile-accent);
  font-size: 1.05rem;
}

.hero-tile__value {
  font-size: 1.15rem;
  font-weight: 750;
  color: var(--text-primary);
  line-height: 1.2;
  letter-spacing: -0.01em;
}

.hero-tile__label {
  font-size: 0.68rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--text-muted);
  display: flex;
  align-items: center;
  gap: 0;
}

/* ── Section headers ── */
.section-header {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  font-size: 0.72rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--text-muted);
  padding-bottom: 0.4rem;
}

/* ── Activity breakdown ── */
.activity-section {
  display: flex;
  flex-direction: column;
}

.activity-layout {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.activity-chart-wrap {
  flex: 0 0 170px;
  min-width: 0;
}
.donut-chart {
  width: 170px;
  height: 170px;
}

.activity-legend {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.legend-row {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.8rem;
  color: var(--text-secondary);
  min-width: 0;
}

.legend-swatch {
  flex: 0 0 8px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.legend-icon {
  font-size: 0.85rem;
  flex: 0 0 auto;
}

.legend-type {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
}

.legend-count {
  flex: 0 0 auto;
  font-weight: 700;
  color: var(--text-primary);
  font-size: 0.78rem;
}

.legend-dist {
  flex: 0 0 auto;
  font-size: 0.72rem;
  color: var(--text-muted);
  min-width: 3.8rem;
  text-align: right;
}

/* ── Latest activity card ── */
.latest-card {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  width: 100%;
  padding: 0.85rem 1rem;
  border-radius: 14px;
  background: var(--surface-elevated);
  border: 1px solid var(--border-default);
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s, transform 0.1s;
  text-align: left;
  font: inherit;
  color: inherit;
}
.latest-card:hover {
  background: var(--surface-hover);
  border-color: var(--border-medium);
  transform: translateY(-1px);
}
.latest-card:active {
  transform: translateY(0);
}

.latest-card__left {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.latest-card__shape {
  flex-shrink: 0;
  opacity: 0.7;
}

.latest-card:hover .latest-card__shape {
  opacity: 1;
}

.latest-card__name {
  font-size: 0.92rem;
  font-weight: 650;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.latest-card__meta {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.78rem;
  color: var(--text-muted);
}

.latest-card__date {
  white-space: nowrap;
}

.latest-card__stats {
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 0.1rem;
}

.latest-card__stat {
  font-size: 0.85rem;
  font-weight: 650;
  color: var(--text-primary);
  white-space: nowrap;
}
.latest-card__stat--muted {
  font-weight: 500;
  color: var(--text-muted);
  font-size: 0.78rem;
}

.latest-card__arrow {
  flex: 0 0 auto;
  font-size: 0.8rem;
  color: var(--text-faint);
}

/* ── Date range footer ── */
.date-range {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.4rem;
  font-size: 0.78rem;
  color: var(--text-muted);
  padding-top: 0.25rem;
}

/* ── Responsive ── */
@media (max-width: 420px) {
  .activity-layout {
    flex-direction: column;
  }
  .activity-chart-wrap {
    flex: 0 0 auto;
  }
  .hero-stats {
    grid-template-columns: repeat(2, 1fr);
  }
}

/* ── Info button ── */
.info-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: none;
  background: transparent;
  cursor: pointer;
  color: var(--text-faint);
  font-size: 0.62rem;
  line-height: 1;
  margin-left: 3px;
  transition: color 0.15s;
}
.info-btn:hover, .info-btn:focus-visible { color: var(--accent-muted); outline: none; }

.overview-info-text {
  max-width: 240px;
  font-size: 0.78rem;
  line-height: 1.5;
  color: var(--text-secondary);
  margin: 0;
  padding: 0.1rem 0;
}
</style>
