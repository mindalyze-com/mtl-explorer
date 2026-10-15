<template>
  <article
    class="health-chart-card"
    :class="{ 'health-chart-card--expanded': expanded, 'health-chart-card--details': detailsOpen }"
    @keydown.esc="closeDetails"
  >
    <header>
      <div>
        <h3>{{ series.title }}</h3>
        <p>{{ chartDescription }}</p>
        <div class="health-chart-summary">
          <strong>{{ summaryNumber }}</strong
          ><span>{{ summaryUnit }}</span>
        </div>
        <button
          ref="detailsTrigger"
          class="health-chart-period"
          type="button"
          :aria-label="`View ${series.title} period details`"
          :aria-expanded="expanded || detailsOpen"
          @click="toggleDetails"
        >
          {{ selectedPeriodLabel }} <i class="bi bi-chevron-down" aria-hidden="true" />
        </button>
      </div>
      <button
        v-if="!expanded"
        type="button"
        class="health-chart-expand"
        :aria-label="`Expand ${series.title ?? 'health'} chart`"
        title="Expand chart"
        @click="$emit('expand', $event)"
      >
        <i class="bi bi-arrows-angle-expand" aria-hidden="true" />
      </button>
    </header>
    <div v-if="expanded" class="health-chart-modes" role="group" :aria-label="`${series.title} view`">
      <button
        v-for="option in modeOptions"
        :key="option.value"
        type="button"
        :aria-pressed="effectiveMode === option.value"
        :disabled="option.value === 'RANGE' && variationDisabled"
        :title="option.value === 'RANGE' && variationDisabled ? 'Choose week, month or year' : undefined"
        @click="$emit('update:mode', option.value)"
      >
        {{ option.label }}
      </button>
      <span v-if="variationDisabled">Choose week, month or year</span>
    </div>
    <div ref="plotElement" class="health-chart-plot" @mouseleave="previewIndex = null">
      <highcharts
        ref="chartComponent"
        :options="chartOptions"
        :deep-copy-on-update="!secondGrouping"
        :update-args="[true, true, false]"
      />
      <p v-if="secondGrouping && secondPoints.length && !windowHasReadings" class="health-chart-window-empty">
        No readings in this time window
      </p>
    </div>
    <p v-if="secondGrouping && focusTime != null" class="health-second-reading" role="status">
      <template v-if="focusReading">
        <i class="bi bi-geo" aria-hidden="true" />{{ readingTime(focusReading.time!) }} ·
        <strong>{{ formatValue(focusReading.value) }}</strong>
        <span v-if="focusReading.minimum !== focusReading.maximum">
          · Range {{ formatValue(focusReading.minimum) }}–{{ formatValue(focusReading.maximum) }}
        </span>
      </template>
      <template v-else>No reading near {{ readingTime(new Date(focusTime)) }}</template>
      <button type="button" aria-label="Clear marked time" @click="emit('update:focusTime', undefined)">
        <i class="bi bi-x-lg" aria-hidden="true" />
      </button>
    </p>
    <p v-if="maximumSessionCount > 1" class="health-sleep-legend" aria-label="Sleep session shades">
      <span><i :style="{ background: SLEEP_SESSION_COLORS[0] }" aria-hidden="true" />Longest sleep</span>
      <span><i :style="{ background: SLEEP_SESSION_COLORS[1] }" aria-hidden="true" />Additional sleep</span>
    </p>
    <p v-if="series.family === 'SLEEP'" class="health-sleep-date-note">
      Each session counts on the date it started, including sleep after midnight.
    </p>
    <section
      v-show="expanded || detailsOpen"
      ref="detailsElement"
      tabindex="-1"
      class="health-bucket-details"
      :class="{ 'health-bucket-details--floating': !expanded }"
      :aria-label="`${series.title} selected period`"
    >
      <button
        v-if="!expanded"
        type="button"
        class="health-details-close"
        aria-label="Close period details"
        @click="dismissDetails"
      >
        <i class="bi bi-x-lg" aria-hidden="true" />
      </button>
      <div class="health-bucket-navigation">
        <button
          type="button"
          :aria-label="`Previous ${series.title} period`"
          :disabled="pinnedIndex <= 0"
          @click="pin(pinnedIndex - 1)"
        >
          ‹
        </button>
        <select
          :aria-label="`${series.title} selected period`"
          :value="pinnedIndex"
          @change="pin(Number(($event.target as HTMLSelectElement).value))"
        >
          <option v-for="(bucket, index) in buckets" :key="bucket.startDate" :value="index">
            {{ bucketLabel(bucket) }}{{ bucket.inProgress ? ' · In progress' : '' }}
          </option>
        </select>
        <button
          type="button"
          :aria-label="`Next ${series.title} period`"
          :disabled="pinnedIndex >= buckets.length - 1"
          @click="pin(pinnedIndex + 1)"
        >
          ›
        </button>
      </div>
      <template v-if="detailBucket">
        <div class="health-bucket-heading">
          <strong>{{ bucketLabel(detailBucket) }}</strong>
          <span class="health-bucket-coverage"
            >{{ detailBucket.availableDays }} / {{ detailBucket.selectedDays }} days</span
          >
          <span v-if="previewIndex != null && previewIndex !== pinnedIndex">Preview · select to pin</span>
          <span v-if="detailBucket.inProgress" class="health-bucket-badge">In progress</span>
          <span v-if="detailBucket.clipped" class="health-bucket-badge">Clipped period</span>
        </div>
        <dl :class="{ 'health-bucket-values--measurement': !isCumulative }">
          <div v-if="isCumulative">
            <dt>Total</dt>
            <dd>{{ formatValue(detailBucket.total) }}</dd>
          </div>
          <div>
            <dt>{{ isCumulative ? 'Daily average' : 'Average' }}</dt>
            <dd>{{ formatValue(detailBucket.average) }}</dd>
          </div>
          <div>
            <dt>{{ isCumulative ? 'Lowest day' : 'Lowest measurement' }}</dt>
            <dd>
              {{ formatValue(detailBucket.minimum) }}<small>{{ dateLabel(detailBucket.minimumDate) }}</small>
            </dd>
          </div>
          <div>
            <dt>{{ isCumulative ? 'Highest day' : 'Highest measurement' }}</dt>
            <dd>
              {{ formatValue(detailBucket.maximum) }}<small>{{ dateLabel(detailBucket.maximumDate) }}</small>
            </dd>
          </div>
        </dl>
        <p v-if="!detailBucket.availableDays">No data in this period.</p>
        <p v-else>Available dates; device wear may be incomplete.</p>
        <Button
          v-if="canOpenDay"
          class="health-open-day"
          label="See day in detail"
          icon="bi bi-zoom-in"
          size="small"
          outlined
          @click="emit('openDay', detailBucket.startDate!)"
        />
      </template>
    </section>
  </article>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, toRaw, watch } from 'vue';
import type Highcharts from 'highcharts';
import Button from 'primevue/button';
import { useTheme } from '@/composables/useTheme';
import { observeElementResize } from '@/utils/observeElementResize';
import type { GHGroupedSeries, GHMetricBucket, GHMetricPoint } from '../api/googleHealthApi';
import { SLEEP_SESSION_COLORS } from '../utils/sleepChart';
import { formatCalendarDate } from '../utils/healthCalendarRange';
import { defaultHealthMode, type HealthChartMode, type HealthChartTimeWindow } from '../utils/healthChartPreferences';

const props = withDefaults(
  defineProps<{
    series: GHGroupedSeries;
    expanded?: boolean;
    mode?: HealthChartMode;
    selectedBucket?: string;
    timeWindow?: HealthChartTimeWindow;
    focusTime?: number;
  }>(),
  { expanded: false, mode: undefined, selectedBucket: undefined, timeWindow: undefined, focusTime: undefined }
);
const emit = defineEmits<{
  expand: [source: MouseEvent];
  'update:mode': [value: HealthChartMode];
  'update:selectedBucket': [value: string];
  'update:timeWindow': [value: HealthChartTimeWindow | undefined];
  'update:focusTime': [value: number | undefined];
  openDay: [date: string];
}>();
const COMPACT_CHART_HEIGHT = 220;
const MINUTES_PER_HOUR = 60;
const COMPACT_AXIS_LABEL_COUNT = 7;
const NARROW_AXIS_LABEL_COUNT = 5;
const NARROW_CHART_WIDTH = 430;
const MINIMUM_EXPANDED_CHART_HEIGHT = 220;
const SECOND_MS = 1_000;
const DENSE_READING_THRESHOLD = 5_000;
// A gap this many times the usual reading spacing breaks the line.
const GAP_SPACING_FACTOR = 3;
// Show reading markers once zooming leaves this few readings in view.
const MARKER_READING_LIMIT = 120;
const plotElement = ref<HTMLElement | null>(null);
const chartComponent = ref<{ chart?: Highcharts.Chart } | null>(null);
const secondGrouping = computed(() => props.series.grouping === 'SECOND');
const secondPoints = computed(() =>
  (props.series.detailedPoints ?? [])
    .filter((point): point is GHMetricPoint & { time: Date } => point.time instanceof Date && point.value != null)
    .sort((a, b) => a.time.getTime() - b.time.getTime())
);
const gapThreshold = computed(() => {
  const spacings = secondPoints.value
    .slice(1)
    .map((point, index) => point.time.getTime() - secondPoints.value[index]!.time.getTime())
    .sort((a, b) => a - b);
  const typical = spacings[Math.floor(spacings.length / 2)] ?? SECOND_MS;
  return Math.max(SECOND_MS, GAP_SPACING_FACTOR * typical);
});
const secondPointsByTime = computed(() => new Map(secondPoints.value.map((point) => [point.time.getTime(), point])));
const timeBounds = computed(() => {
  const bucket = buckets.value[0];
  return {
    start: bucket?.startDate ? new Date(`${bucket.startDate}T00:00:00`).getTime() : undefined,
    end: bucket?.endDate ? new Date(`${bucket.endDate}T00:00:00`).getTime() : undefined,
  };
});
const focusReading = computed(() => {
  const focus = props.focusTime;
  const points = secondPoints.value;
  if (focus == null || !points.length) return;
  const low = Math.min(firstReadingAtOrAfter(focus), points.length - 1);
  const nearest = [points[low - 1], points[low]]
    .filter((point) => point != null)
    .reduce((best, point) =>
      Math.abs(point.time.getTime() - focus) < Math.abs(best.time.getTime() - focus) ? point : best
    );
  return Math.abs(nearest.time.getTime() - focus) <= gapThreshold.value ? nearest : undefined;
});
const canOpenDay = computed(
  () =>
    props.series.grouping === 'DAY' &&
    props.series.family === 'MEASUREMENT' &&
    (detailBucket.value?.availableDays ?? 0) > 0
);
function exactTime(time: Date) {
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'medium' }).format(time);
}
function readingTime(time: Date) {
  return new Intl.DateTimeFormat(undefined, { timeStyle: 'medium' }).format(time);
}
function visibleWindow() {
  return props.timeWindow ?? timeBounds.value;
}
function firstReadingAtOrAfter(time: number) {
  const points = secondPoints.value;
  let low = 0;
  let high = points.length;
  while (low < high) {
    const middle = (low + high) >> 1;
    if (points[middle]!.time.getTime() < time) low = middle + 1;
    else high = middle;
  }
  return low;
}
const windowHasReadings = computed(() => {
  const { start, end } = visibleWindow();
  return start == null || end == null || firstReadingAtOrAfter(end + 1) > firstReadingAtOrAfter(start);
});
function showMarkers(start?: number, end?: number) {
  if (start == null || end == null) return secondPoints.value.length <= MARKER_READING_LIMIT;
  return firstReadingAtOrAfter(end + 1) - firstReadingAtOrAfter(start) <= MARKER_READING_LIMIT;
}
// The accessibility module overrides automatic marker thresholds, so markers follow the visible reading count.
function updateMarkers(chart: Highcharts.Chart, start: number, end: number) {
  const series = chart.get('average') as Highcharts.Series | undefined;
  const enabled = showMarkers(start, end);
  if (series && (series.options as Highcharts.SeriesLineOptions).marker?.enabled !== enabled)
    series.update({ type: 'line', marker: { enabled } }, true);
}
function recordTimeWindow(start: number, end: number) {
  if (!secondGrouping.value) return;
  const current = visibleWindow();
  if (current.start === start && current.end === end) return;
  const { start: dayStart, end: dayEnd } = timeBounds.value;
  emit('update:timeWindow', start <= dayStart! && end >= dayEnd! ? undefined : { start, end });
}
const previewIndex = ref<number | null>(null);
const detailsOpen = ref(false);
const detailsTrigger = ref<HTMLButtonElement | null>(null);
const detailsElement = ref<HTMLElement | null>(null);
function dismissDetails() {
  detailsOpen.value = false;
  detailsTrigger.value?.focus({ preventScroll: true });
}
function closeDetails(event: KeyboardEvent) {
  if (props.expanded || !detailsOpen.value) return;
  event.stopPropagation();
  event.preventDefault();
  dismissDetails();
}
async function toggleDetails() {
  if (props.expanded) {
    detailsElement.value?.focus({ preventScroll: true });
    return;
  }
  detailsOpen.value = !detailsOpen.value;
  if (detailsOpen.value) {
    await nextTick();
    detailsElement.value?.focus({ preventScroll: true });
  }
}
const { colorScheme } = useTheme();
const buckets = computed(() => props.series.buckets ?? []);
const isCumulative = computed(() => props.series.family !== 'MEASUREMENT');
const dailySleep = computed(() => props.series.family === 'SLEEP' && props.series.grouping === 'DAY');
const variationDisabled = computed(() => isCumulative.value && props.series.grouping === 'DAY');
const effectiveMode = computed(() =>
  variationDisabled.value ? 'SUMMARY' : (props.mode ?? defaultHealthMode(props.series))
);
const modeOptions = computed<{ value: HealthChartMode; label: string }[]>(() => [
  {
    value: 'SUMMARY',
    label: props.series.family === 'ACTIVITY' ? 'Totals' : dailySleep.value ? 'Sessions' : 'Average',
  },
  { value: 'RANGE', label: isCumulative.value ? 'Daily variation' : 'Average & range' },
]);
const chartDescription = computed(() => {
  if (secondGrouping.value) return props.series.dataType === 'heart-rate' ? 'One-second average' : 'Readings';
  if (props.series.detailedHeartRate) return 'Average heart rate';
  if (isCumulative.value && effectiveMode.value === 'RANGE') return 'Daily average';
  const period = {
    AUTO: 'Daily',
    SECOND: 'One-second',
    DAY: 'Daily',
    WEEK: 'Weekly',
    MONTH: 'Monthly',
    YEAR: 'Yearly',
  }[props.series.grouping ?? 'DAY'];
  return `${period} ${props.series.family === 'ACTIVITY' || dailySleep.value ? 'total' : 'average'}`;
});
const axisTitle = computed(() =>
  dailySleep.value
    ? `Daily total (${props.series.unit})`
    : props.series.family === 'ACTIVITY' && effectiveMode.value === 'SUMMARY'
      ? `Total (${props.series.unit})`
      : isCumulative.value
        ? `Daily average (${props.series.unit})`
        : `Average (${props.series.unit})`
);
const pinnedIndex = computed(() => {
  const selected = buckets.value.findIndex((bucket) => bucket.startDate === props.selectedBucket);
  return selected >= 0
    ? selected
    : Math.max(
        0,
        buckets.value.reduce((last, bucket, index) => ((bucket.availableDays ?? 0) > 0 ? index : last), -1)
      );
});
const detailBucket = computed(() => buckets.value[(props.expanded ? previewIndex.value : null) ?? pinnedIndex.value]);
const summaryBucket = computed(() => buckets.value[pinnedIndex.value]);
const summaryValue = computed(() =>
  (props.series.family === 'ACTIVITY' && effectiveMode.value === 'SUMMARY') || dailySleep.value
    ? summaryBucket.value?.total
    : summaryBucket.value?.average
);
const summaryNumber = computed(() => formatNumber(summaryValue.value));
const summaryUnit = computed(() => (summaryValue.value == null || props.series.unit === 'h' ? '' : props.series.unit));
const selectedPeriodLabel = computed(() => (summaryBucket.value ? bucketLabel(summaryBucket.value) : 'No data'));
const sessions = computed(() =>
  dailySleep.value
    ? buckets.value.map((bucket) => [...(bucket.sessions ?? [])].sort((a, b) => (b.value ?? 0) - (a.value ?? 0)))
    : []
);
const maximumSessionCount = computed(() => Math.max(0, ...sessions.value.map((day) => day.length)));
watch(
  () => props.series,
  () => {
    previewIndex.value = null;
    detailsOpen.value = false;
  }
);

// Zooming is shared by every chart, so the window is applied imperatively rather than rebuilding all series.
watch(
  () => [props.timeWindow?.start, props.timeWindow?.end, timeBounds.value.start, timeBounds.value.end],
  () => {
    const axis = chartComponent.value?.chart?.xAxis[0];
    const { start, end } = visibleWindow();
    if (!secondGrouping.value || !axis || start == null || end == null) return;
    if (axis.min === start && axis.max === end) return;
    axis.chart.tooltip.hide(0);
    axis.setExtremes(start, end, true, false);
  }
);
watch(
  () => props.focusTime,
  (focus) => {
    const axis = chartComponent.value?.chart?.xAxis[0];
    if (!axis) return;
    axis.removePlotLine('focus');
    if (secondGrouping.value && focus != null) axis.addPlotLine(focusLine(focus));
  }
);
function focusLine(value: number): Highcharts.AxisPlotLinesOptions {
  const color = getComputedStyle(document.documentElement).getPropertyValue('--accent').trim() || '#5b5bd6';
  return { id: 'focus', value, width: 1.5, color, dashStyle: 'ShortDash', zIndex: 5 };
}

function pin(index: number) {
  previewIndex.value = null;
  chartComponent.value?.chart?.tooltip.hide(0);
  const bucket = buckets.value[index];
  if (bucket?.startDate) {
    emit('update:selectedBucket', bucket.startDate);
    detailsOpen.value = true;
    if (!props.expanded) void nextTick(() => detailsElement.value?.focus({ preventScroll: true }));
  }
}

watch(
  [() => props.expanded, plotElement],
  ([expanded, element], _previous, onCleanup) => {
    if (!element || typeof ResizeObserver === 'undefined') return;
    const stopObserving = observeElementResize(element, (entry) => {
      const chart = chartComponent.value?.chart;
      if (!chart || !entry) return;
      const width = Math.floor(entry.contentRect.width);
      const height = expanded
        ? Math.max(MINIMUM_EXPANDED_CHART_HEIGHT, Math.floor(entry.contentRect.height))
        : COMPACT_CHART_HEIGHT;
      if (width > 0 && (width !== chart.chartWidth || height !== chart.chartHeight))
        chart.setSize(width, height, false);
    });
    onCleanup(stopObserving);
  },
  { flush: 'post' }
);

const chartOptions = computed<Highcharts.Options>(() => {
  // Re-read CSS chart tokens when the app changes its theme.
  void colorScheme.value;
  const styles = getComputedStyle(document.documentElement);
  const token = (name: string, fallback: string) => styles.getPropertyValue(name).trim() || fallback;
  const color = seriesColor(props.series);
  const textColor = token('--chart-text', '#64748b');
  const gridColor = token('--chart-grid', '#e2e8f0');
  const series: Highcharts.SeriesOptionsType[] = [];
  // Read untracked: window and focus changes are applied imperatively without rebuilding every series.
  const rawProps = toRaw(props);
  const detail = props.series.detailedHeartRate || secondGrouping.value;
  const rangeMode = effectiveMode.value === 'RANGE';
  const events: Highcharts.PointEventsOptionsObject = {
    mouseOver() {
      previewIndex.value = this.options.custom?.bucketIndex ?? null;
    },
    mouseOut() {
      previewIndex.value = null;
    },
    click() {
      if (secondGrouping.value) {
        emit('update:focusTime', this.x);
        return;
      }
      pin(this.options.custom?.bucketIndex ?? pinnedIndex.value);
    },
  };
  if (secondGrouping.value) {
    const points = secondPoints.value;
    const gap = gapThreshold.value;
    const isolated = points.filter((point, index) => {
      const previous = points[index - 1];
      const next = points[index + 1];
      return (
        (!previous || point.time.getTime() - previous.time.getTime() > gap) &&
        (!next || next.time.getTime() - point.time.getTime() > gap)
      );
    });
    // A gap much longer than the usual reading spacing breaks the line.
    const line = points.flatMap((point, index) => {
      const previous = points[index - 1];
      return previous && point.time.getTime() - previous.time.getTime() > gap
        ? [
            [previous.time.getTime() + SECOND_MS, null],
            [point.time.getTime(), point.value!],
          ]
        : [[point.time.getTime(), point.value!]];
    });
    const ranges = rangeMode
      ? points.filter((point) => point.minimum != null && point.maximum != null && point.minimum !== point.maximum)
      : [];
    if (ranges.length)
      series.push({
        id: 'range',
        type: 'errorbar',
        name: 'Observed range',
        color,
        stemWidth: 1.5,
        whiskerLength: 6,
        enableMouseTracking: false,
        zIndex: 0,
        data: ranges.map((point) => [point.time.getTime(), point.minimum!, point.maximum!]),
      });
    series.push({
      id: 'average',
      type: 'line',
      name: 'Average',
      color,
      lineWidth: 1.5,
      zIndex: 1,
      // Markers appear once zooming spreads the readings apart.
      marker: {
        radius: 2.5,
        enabled: showMarkers(
          rawProps.timeWindow?.start ?? timeBounds.value.start,
          rawProps.timeWindow?.end ?? timeBounds.value.end
        ),
      },
      boostThreshold: DENSE_READING_THRESHOLD,
      data: line,
    });
    if (isolated.length)
      series.push({
        id: 'isolated-readings',
        type: 'scatter',
        name: 'Average',
        color,
        zIndex: 2,
        marker: { enabled: true, radius: 2.5 },
        boostThreshold: DENSE_READING_THRESHOLD,
        data: isolated.map((point) => [point.time.getTime(), point.value!]),
      });
  } else if (detail) {
    const points = (props.series.detailedPoints ?? []).filter(
      (point): point is GHMetricPoint & { time: Date } => point.time instanceof Date
    );
    for (const bucket of buckets.value) {
      if (!bucket.availableDays) points.push({ time: new Date(`${bucket.startDate}T00:00:00`) });
    }
    points.sort((a, b) => a.time.getTime() - b.time.getTime());
    if (rangeMode)
      series.push({
        id: 'range',
        type: 'arearange',
        name: 'Observed range',
        color,
        fillOpacity: 0.12,
        lineWidth: 0,
        marker: { enabled: false },
        enableMouseTracking: false,
        zIndex: 0,
        data: points.map((point) => [
          point.time.getTime(),
          point.minimum != null && point.minimum !== point.maximum ? point.minimum : null,
          point.maximum != null && point.minimum !== point.maximum ? point.maximum : null,
        ]),
      });
    series.push({
      id: 'average',
      type: 'spline',
      name: 'Average',
      color,
      lineWidth: 2,
      zIndex: 1,
      marker: { enabled: points.length < 40, radius: 2.5 },
      data: points.map((point) => ({
        x: point.time.getTime(),
        y: point.value ?? null,
        custom: {
          minimum: point.minimum,
          maximum: point.maximum,
          bucketIndex: buckets.value.findIndex((bucket) => bucket.startDate === formatCalendarDate(point.time)),
        },
      })),
    });
  } else if (dailySleep.value) {
    for (let index = 0; index < maximumSessionCount.value; index++)
      series.push({
        id: `session-${index}`,
        type: 'column',
        name: index === 0 ? 'Longest sleep' : `Additional sleep ${index}`,
        color:
          index === 0
            ? SLEEP_SESSION_COLORS[0]
            : SLEEP_SESSION_COLORS[1 + ((index - 1) % (SLEEP_SESSION_COLORS.length - 1))],
        data: buckets.value.map((_bucket, bucketIndex) => ({
          x: bucketIndex,
          y: sessions.value[bucketIndex]?.[index]?.value ?? null,
          custom: { bucketIndex, sessionTime: sessions.value[bucketIndex]?.[index]?.time },
        })),
        stacking: 'normal',
        borderWidth: 1,
        borderColor: token('--surface-sheet-solid', '#ffffff'),
        borderRadius: { radius: 3, scope: 'stack' },
      });
  } else {
    const variation = rangeMode && isCumulative.value;
    if (rangeMode)
      series.push({
        id: 'range',
        type: 'errorbar',
        name: variation ? 'Daily low–high' : 'Observed low–high',
        color,
        stemWidth: 1.5,
        whiskerLength: '40%',
        enableMouseTracking: false,
        zIndex: 0,
        data: buckets.value.map((bucket, index) => [
          index,
          bucket.minimum != null &&
          bucket.minimum !== bucket.maximum &&
          (!isCumulative.value || (bucket.availableDays ?? 0) > 1)
            ? bucket.minimum
            : null,
          bucket.maximum != null &&
          bucket.minimum !== bucket.maximum &&
          (!isCumulative.value || (bucket.availableDays ?? 0) > 1)
            ? bucket.maximum
            : null,
        ]),
      });
    const type = variation ? 'scatter' : isCumulative.value ? 'column' : 'line';
    series.push({
      id: 'value',
      type,
      name: axisTitle.value,
      color,
      zIndex: 1,
      marker: { enabled: true, radius: variation ? 4 : 3 },
      data: buckets.value.map((bucket, index) => ({
        x: index,
        y: (props.series.family === 'ACTIVITY' && !rangeMode ? bucket.total : bucket.average) ?? null,
        custom: { bucketIndex: index },
      })),
    });
  }
  const currentIndex = detail ? undefined : pinnedIndex.value;
  const chartFontSize = token('--text-xs-size', '0.75rem');
  return {
    time: { timezone: Intl.DateTimeFormat().resolvedOptions().timeZone },
    chart: {
      height: props.expanded ? null : COMPACT_CHART_HEIGHT,
      backgroundColor: 'transparent',
      spacing: [12, 10, 4, 8],
      animation: false,
      zooming: secondGrouping.value ? { type: 'x', pinchType: 'x' } : {},
      panning: { enabled: secondGrouping.value, type: 'x' },
      panKey: secondGrouping.value ? 'shift' : undefined,
      events: secondGrouping.value
        ? {
            // Every chart shares one time window, so a drag selection updates that window instead of this chart.
            selection(event) {
              const axis = event.xAxis?.[0];
              const { start, end } = timeBounds.value;
              if (axis)
                recordTimeWindow(
                  Math.floor(axis.min / SECOND_MS) * SECOND_MS,
                  Math.ceil(axis.max / SECOND_MS) * SECOND_MS
                );
              else if (start != null && end != null) recordTimeWindow(start, end);
              return false;
            },
            click(event) {
              const value = (event as Highcharts.PointerEventObject & { xAxis?: { value: number }[] }).xAxis?.[0]
                ?.value;
              if (value != null) emit('update:focusTime', Math.round(value / SECOND_MS) * SECOND_MS);
            },
          }
        : {},
    },
    title: { text: undefined },
    credits: { enabled: false },
    legend: { enabled: false },
    accessibility: {
      description: secondGrouping.value
        ? `${props.series.title} through the day. Use the time window controls above the charts, or drag or pinch to zoom. Select a point to mark that time on every chart.`
        : `${props.series.title} over time. Select a point or use View ${props.series.title} period details to review each group.`,
    },
    xAxis: {
      type: detail ? 'datetime' : 'category',
      min: secondGrouping.value ? (rawProps.timeWindow?.start ?? timeBounds.value.start) : undefined,
      max: secondGrouping.value ? (rawProps.timeWindow?.end ?? timeBounds.value.end) : undefined,
      minRange: secondGrouping.value ? SECOND_MS : undefined,
      events: {
        afterSetExtremes(event) {
          if (secondGrouping.value) updateMarkers(this.chart, event.min, event.max);
          recordTimeWindow(event.min, event.max);
        },
      },
      startOnTick: false,
      endOnTick: false,
      dateTimeLabelFormats: secondGrouping.value
        ? { second: '%H:%M:%S', minute: '%H:%M', hour: '%H:%M', day: '%H:%M' }
        : {},
      categories: detail ? undefined : buckets.value.map((bucket) => axisDateLabel(bucket.startDate)),
      lineColor: gridColor,
      tickColor: 'transparent',
      labels: {
        autoRotation: [0],
        step: detail ? undefined : Math.max(1, Math.ceil(buckets.value.length / COMPACT_AXIS_LABEL_COUNT)),
        style: { color: textColor, fontSize: chartFontSize },
      },
      crosshair: secondGrouping.value ? { color: gridColor, width: 1 } : false,
      plotLines: secondGrouping.value && rawProps.focusTime != null ? [focusLine(rawProps.focusTime)] : [],
      plotBands:
        currentIndex == null
          ? []
          : [{ from: currentIndex - 0.45, to: currentIndex + 0.45, color: token('--surface-hover', '#f1f5f9') }],
    },
    yAxis: {
      min: isCumulative.value ? 0 : null,
      max: null,
      reversedStacks: dailySleep.value ? false : undefined,
      title: {
        text: props.expanded ? axisTitle.value : undefined,
        style: { color: textColor, fontSize: chartFontSize },
      },
      gridLineColor: gridColor,
      labels: {
        style: { color: textColor, fontSize: chartFontSize },
        formatter() {
          const value = Number(this.value);
          if (props.series.unit === 'h') return `${value}h`;
          return new Intl.NumberFormat(undefined, { notation: 'compact', maximumFractionDigits: 1 }).format(value);
        },
      },
    },
    tooltip: {
      shared: false,
      useHTML: true,
      backgroundColor: token('--chart-tooltip-bg', '#ffffff'),
      borderColor: token('--border-default', '#d9e0e7'),
      borderRadius: 8,
      shadow: true,
      style: { color: token('--chart-tooltip-text', '#172033'), fontSize: chartFontSize },
      formatter(this: Highcharts.Point) {
        if (secondGrouping.value) {
          const point = secondPointsByTime.value.get(this.x);
          const range =
            rangeMode && point?.minimum != null && point.maximum != null && point.minimum !== point.maximum
              ? `<br/>Range ${formatValue(point.minimum)}–${formatValue(point.maximum)}`
              : '';
          return `${exactTime(new Date(this.x))}<br/>Average <b>${formatValue(this.y)}</b>${range}`;
        }
        const bucket = buckets.value[this.options.custom?.bucketIndex];
        const date = detail
          ? new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(this.x))
          : bucket
            ? bucketLabel(bucket)
            : '';
        const sessionTime = this.options.custom?.sessionTime;
        const session =
          sessionTime instanceof Date
            ? `<br/>${this.series.name} · ${new Intl.DateTimeFormat(undefined, { timeStyle: 'short' }).format(sessionTime)}`
            : '';
        const pointRange =
          detail && rangeMode && this.options.custom?.minimum != null && this.options.custom?.maximum != null
            ? `<br/>Range ${formatValue(this.options.custom.minimum)}–${formatValue(this.options.custom.maximum)}`
            : '';
        const total = dailySleep.value && bucket ? `<br/>Daily total: <b>${formatValue(bucket.total)}</b>` : '';
        if (!bucket) return `${date}<br/><b>${formatValue(this.y)}</b>`;
        const row = (label: string, value: string) => `<div><span>${label}</span><b>${value}</b></div>`;
        return `<div class="health-chart-tooltip"><div class="health-chart-tooltip__heading"><b>${bucketLabel(bucket)}</b><span>${bucket.availableDays ?? 0} / ${bucket.selectedDays ?? 0} days</span></div>
          <div class="health-chart-tooltip__values">${isCumulative.value ? row('Total', formatValue(bucket.total)) : ''}
          ${row(isCumulative.value ? 'Daily average' : 'Average', formatValue(bucket.average))}
          ${row(isCumulative.value ? 'Lowest day' : 'Lowest measurement', `${formatValue(bucket.minimum)}${bucket.minimumDate ? ' · ' + dateLabel(bucket.minimumDate, true) : ''}`)}
          ${row(isCumulative.value ? 'Highest day' : 'Highest measurement', `${formatValue(bucket.maximum)}${bucket.maximumDate ? ' · ' + dateLabel(bucket.maximumDate, true) : ''}`)}</div>
          ${detail || session ? `<p>${date}${session}<br/>${formatValue(this.y)}${pointRange}${total}</p>` : ''}
          ${bucket.inProgress || bucket.clipped ? `<p>${bucket.inProgress ? 'In progress' : ''}${bucket.inProgress && bucket.clipped ? ' · ' : ''}${bucket.clipped ? 'Partial period' : ''}</p>` : ''}
          </div>`;
      },
    },
    plotOptions: {
      series: {
        animation: false,
        connectNulls: false,
        cursor: 'pointer',
        point: { events },
        states: { inactive: { opacity: 1 } },
      },
      column: { borderWidth: 0, borderRadius: 3, groupPadding: 0.08, pointPadding: 0.06 },
      spline: { states: { hover: { lineWidthPlus: 0 } } },
    },
    responsive: {
      rules: [
        {
          condition: { maxWidth: NARROW_CHART_WIDTH },
          chartOptions: {
            xAxis: {
              labels: {
                step: detail ? undefined : Math.max(1, Math.ceil(buckets.value.length / NARROW_AXIS_LABEL_COUNT)),
              },
            },
          },
        },
      ],
    },
    series,
  };
});

function axisDateLabel(value?: string): string {
  if (!value) return '';
  const options: Intl.DateTimeFormatOptions =
    props.series.grouping === 'YEAR'
      ? { year: 'numeric' }
      : props.series.grouping === 'MONTH'
        ? { month: 'short', year: '2-digit' }
        : {
            month: 'short',
            day: 'numeric',
            ...(buckets.value[0]?.startDate?.slice(0, 4) !== buckets.value.at(-1)?.startDate?.slice(0, 4)
              ? { year: '2-digit' as const }
              : {}),
          };
  return new Intl.DateTimeFormat(undefined, options).format(new Date(`${value}T12:00:00`));
}
function dateLabel(value?: string | null, short = false): string {
  if (!value) return '';
  return new Intl.DateTimeFormat(
    undefined,
    short ? { month: 'short', day: 'numeric' } : { dateStyle: 'medium' }
  ).format(new Date(`${value}T12:00:00`));
}
function bucketLabel(bucket: GHMetricBucket): string {
  if (props.series.grouping === 'MONTH')
    return new Intl.DateTimeFormat(undefined, { month: 'long', year: 'numeric' }).format(
      new Date(`${bucket.startDate}T12:00:00`)
    );
  if (props.series.grouping === 'YEAR') return bucket.startDate?.slice(0, 4) ?? '';
  const end = new Date(`${bucket.endDate}T12:00:00`);
  end.setDate(end.getDate() - 1);
  const last = formatCalendarDate(end);
  return bucket.startDate === last
    ? dateLabel(bucket.startDate)
    : `${dateLabel(bucket.startDate)} – ${dateLabel(last)}`;
}
function formatNumber(value?: number | null): string {
  if (value == null) return '—';
  if (props.series.unit === 'h') {
    const minutes = Math.round(value * MINUTES_PER_HOUR);
    return `${Math.floor(minutes / MINUTES_PER_HOUR)} h ${minutes % MINUTES_PER_HOUR} min`;
  }
  const decimals = props.series.decimals ?? 1;
  return new Intl.NumberFormat(undefined, { maximumFractionDigits: decimals, minimumFractionDigits: decimals }).format(
    value
  );
}
function formatValue(value?: number | null): string {
  return `${formatNumber(value)}${value != null && props.series.unit && props.series.unit !== 'h' ? `\u202f${props.series.unit}` : ''}`;
}
function seriesColor(series: GHGroupedSeries): string {
  if (series.dataType === 'sleep') return SLEEP_SESSION_COLORS[0];
  if (series.dataType?.includes('heart-rate')) return '#d84a65';
  if (series.category === 'ACTIVITY') return '#2c8b67';
  if (series.category === 'BODY') return '#3d78c5';
  return '#b56b2d';
}
</script>

<style scoped>
.health-chart-card {
  position: relative;
  min-width: 0;
  background: var(--surface-sheet-solid);
  border: 1px solid var(--border-default);
  border-radius: 8px;
}
.health-chart-card--expanded {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  min-height: 0;
  overflow-y: auto;
}
.health-chart-card--expanded .health-chart-plot {
  flex: 1 0 220px;
  min-height: 220px;
}
.health-chart-card > header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 1rem 1rem 0.35rem;
}
.health-chart-card h3 {
  margin: 0;
  color: var(--text-primary);
  font-size: var(--text-base-size);
  line-height: var(--text-base-lh);
}
.health-chart-card p {
  margin: 0.2rem 0 0;
  color: var(--text-muted);
  font-size: var(--text-2xs-size);
  line-height: var(--text-2xs-lh);
}
.health-chart-expand {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 2.25rem;
  height: 2.25rem;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
}
.health-chart-expand:hover {
  color: var(--text-primary);
  background: var(--surface-hover);
}
.health-chart-card button:focus-visible,
.health-chart-card input:focus-visible,
.health-chart-card select:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}
.health-chart-modes {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.3rem;
  padding: 0.65rem 0.9rem 0;
  flex-shrink: 0;
}
.health-chart-modes button {
  min-height: 2.25rem;
  padding: 0.3rem 0.55rem;
  border: 1px solid var(--border-default);
  border-radius: 6px;
  background: transparent;
  color: var(--text-secondary);
  font: inherit;
  font-size: var(--text-2xs-size);
  cursor: pointer;
}
.health-chart-modes button[aria-pressed='true'] {
  background: var(--accent-bg);
  color: var(--accent-text);
  border-color: var(--accent-subtle);
}
.health-chart-modes button:disabled {
  opacity: 0.55;
  cursor: default;
}
.health-chart-modes > span {
  color: var(--text-muted);
  font-size: var(--text-2xs-size);
}
.health-chart-plot {
  position: relative;
  min-width: 0;
}
.health-chart-card .health-chart-window-empty {
  position: absolute;
  inset: 40% 1rem auto;
  margin: 0;
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  text-align: center;
  pointer-events: none;
}
/* The shared time window controls replace the per-chart reset button. */
.health-chart-plot :deep(.highcharts-reset-zoom) {
  display: none;
}
.health-chart-card .health-second-reading {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.35rem;
  margin: 0;
  padding: 0 1rem 0.6rem;
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
  font-variant-numeric: tabular-nums;
}
.health-second-reading strong {
  color: var(--text-primary);
}
.health-second-reading button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.75rem;
  height: 1.75rem;
  margin-left: auto;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
}
.health-second-reading button:hover {
  background: var(--surface-hover);
  color: var(--text-primary);
}
.health-open-day {
  margin-top: 0.6rem;
}
.health-chart-card :deep(.highcharts-container) {
  width: 100% !important;
}
.health-sleep-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem 1rem;
  padding: 0 0.9rem 0.6rem;
  flex-shrink: 0;
}
.health-sleep-legend span {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
}
.health-sleep-legend i {
  width: 0.6rem;
  height: 0.6rem;
  border-radius: 2px;
}
.health-sleep-date-note {
  padding: 0 0.9rem 0.6rem;
  color: var(--text-muted);
  font-size: var(--text-2xs-size);
}
.health-bucket-details {
  display: flex;
  flex-direction: column;
  padding: 0.85rem 1rem;
  border-top: 1px solid var(--border-default);
  flex-shrink: 0;
}
.health-bucket-navigation {
  display: flex;
  gap: 0.4rem;
  order: 3;
  padding-top: 0.75rem;
}
.health-bucket-navigation select {
  flex: 1 1 auto;
  width: 0;
  min-width: 0;
  padding: 0.3rem;
}
.health-bucket-navigation button {
  width: 2.5rem;
  flex: 0 0 auto;
  cursor: pointer;
  font-size: 1.2rem;
}
.health-bucket-navigation button:disabled {
  opacity: 0.4;
  cursor: default;
}
.health-bucket-navigation select,
.health-bucket-navigation button {
  min-height: 2.5rem;
  border: 1px solid var(--border-default);
  border-radius: 6px;
  background: var(--surface-elevated);
  color: var(--text-primary);
}
.health-bucket-navigation select {
  font: inherit;
  font-size: var(--text-xs-size);
}
.health-bucket-heading {
  display: flex;
  align-items: center;
  align-content: center;
  flex-wrap: wrap;
  /* Preview badges must not resize the plot underneath the pointer. */
  min-height: 1.75rem;
  gap: 0.35rem 0.5rem;
  margin-top: 0;
  color: var(--text-secondary);
  font-size: var(--text-2xs-size);
}
.health-bucket-heading strong {
  color: var(--text-primary);
}
.health-bucket-coverage {
  margin-left: auto;
}
.health-bucket-details--floating .health-bucket-heading {
  padding-right: 1.4rem;
}
.health-bucket-values--measurement > div:first-child {
  grid-column: 1 / -1;
}
.health-bucket-badge {
  padding: 0.1rem 0.35rem;
  border-radius: 4px;
  background: var(--surface-hover);
}
.health-bucket-details dl {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.6rem;
  margin: 0.65rem 0;
}
.health-bucket-details dt {
  color: var(--text-muted);
  font-size: var(--text-2xs-size);
}
.health-bucket-details dd {
  margin: 0.2rem 0 0;
  color: var(--text-primary);
  font-size: var(--text-xs-size);
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
}
.health-bucket-details small {
  display: block;
  min-height: 1.25em;
  margin-top: 0.15rem;
  color: var(--text-muted);
  font-size: var(--text-2xs-size);
}
@media (max-width: 380px) {
  .health-bucket-details dl {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
.health-chart-summary {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 0.3rem;
  margin-top: 0.35rem;
  color: var(--text-primary);
  font-size: var(--text-sm-size);
}
.health-chart-summary strong {
  font-size: var(--text-xl-size);
  line-height: var(--text-xl-lh);
  font-weight: 650;
  font-variant-numeric: tabular-nums;
}
.health-chart-period {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  margin-top: 0.25rem;
  min-height: 1.7rem;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text-muted);
  font: inherit;
  font-size: var(--text-xs-size);
  cursor: pointer;
}
.health-chart-period:hover {
  color: var(--accent-text);
}
.health-chart-period i {
  font-size: var(--text-2xs-size);
}
.health-chart-card--details {
  z-index: 2;
}
.health-bucket-details--floating {
  position: absolute;
  z-index: 3;
  top: 6.5rem;
  left: 0.75rem;
  right: 0.75rem;
  max-width: 25rem;
  border: 1px solid var(--border-default);
  border-radius: 10px;
  background: var(--surface-sheet-solid);
  box-shadow: var(--shadow-lg);
}
.health-details-close {
  position: absolute;
  right: 0.4rem;
  top: 0.4rem;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: -0.35rem -0.4rem 0.35rem auto;
  width: 2rem;
  height: 2rem;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
}
@media (max-width: 700px) {
  .health-chart-period {
    min-height: 2rem;
  }
}
</style>
<style>
.health-chart-tooltip {
  width: min(19rem, calc(100vw - 5rem));
  white-space: normal;
  padding: 0.35rem;
  font-family: var(--font-family-ui);
}
.health-chart-tooltip__heading {
  display: flex;
  justify-content: space-between;
  gap: 0.5rem;
  padding-bottom: 0.65rem;
  border-bottom: 1px solid var(--border-default);
}
.health-chart-tooltip__heading > span {
  color: var(--text-muted);
  white-space: nowrap;
}
.health-chart-tooltip__values {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.75rem;
  margin-top: 0.65rem;
}
.health-chart-tooltip__values span {
  display: block;
  color: var(--text-muted);
  margin-bottom: 0.2rem;
}
.health-chart-tooltip__values b {
  font-weight: 600;
}
.health-chart-tooltip p {
  margin: 0.5rem 0 0;
  color: var(--text-muted);
}
</style>
