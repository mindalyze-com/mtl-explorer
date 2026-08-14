<template>
  <div v-if="hasData" class="elev-profile">
    <highcharts ref="chartRef" :options="chartOptions" class="elev-profile__chart" />
  </div>
  <div v-else class="elev-profile elev-profile--empty">
    <span class="elev-profile__placeholder"
      ><i class="bi bi-graph-up"></i> Elevation profile appears once a route is computed.</span
    >
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import type Highcharts from 'highcharts';
import { ROUTE_LINE_COLOR } from '@/planner/constants/PlannerConstants';
import { buildChartOptions } from '@/utils/chartTheme';
import { useMeasurementSystem } from '@/composables/useMeasurementSystem';
import { elevationDisplayValue, longDistanceDisplayValue, MEASUREMENT_DISPLAY_PROFILES } from '@/utils/units';
import { haversineDistance } from '@/components/map/mapGeometry';

const props = defineProps<{
  /** Route polyline as [lng, lat, elevationM] triples. */
  coordinates: [number, number, number][];
  /** Authoritative total distance from the server (avoids haversine underestimate). */
  totalDistanceM?: number;
  /** Passed from parent; unused here but kept to avoid prop warnings. */
  ascentM?: number;
  descentM?: number;
}>();

const emit = defineEmits<{
  (e: 'hover', point: { lng: number; lat: number; elevationM: number; distanceM: number } | null): void;
}>();
const { measurementSystem } = useMeasurementSystem();

// ── Data model ────────────────────────────────────────────────────

interface Sample {
  distanceM: number;
  elevationM: number;
  lng: number;
  lat: number;
}

const samples = computed<Sample[]>(() => {
  const coords = props.coordinates;
  if (!coords || coords.length < 2) return [];
  const out: Sample[] = [];
  let cum = 0;
  out.push({ distanceM: 0, elevationM: coords[0][2] || 0, lng: coords[0][0], lat: coords[0][1] });
  for (let i = 1; i < coords.length; i++) {
    cum += haversineDistance(coords[i - 1][1], coords[i - 1][0], coords[i][1], coords[i][0]);
    out.push({ distanceM: cum, elevationM: coords[i][2] || 0, lng: coords[i][0], lat: coords[i][1] });
  }
  const authTotal = props.totalDistanceM;
  if (authTotal && authTotal > 0 && cum > 0 && cum !== authTotal) {
    const scale = authTotal / cum;
    for (const s of out) s.distanceM *= scale;
  }
  return out;
});

const hasData = computed(() => samples.value.length >= 2);

function gradeAt(arr: Sample[], idx: number): number {
  const prev = arr[Math.max(0, idx - 1)];
  const next = arr[Math.min(arr.length - 1, idx + 1)];
  const dDist = next.distanceM - prev.distanceM;
  const dEle = next.elevationM - prev.elevationM;
  return dDist > 0 ? (dEle / dDist) * 100 : 0;
}

const seriesData = computed(() =>
  samples.value.map((s, i) => ({
    x: s.distanceM / 1000,
    y: s.elevationM,
    lng: s.lng,
    lat: s.lat,
    grade: gradeAt(samples.value, i),
  }))
);

// ── Chart ─────────────────────────────────────────────────────────

const chartRef = ref<{ chart: Highcharts.Chart } | null>(null);

function buildOptions(): Highcharts.Options {
  const styles = getComputedStyle(document.documentElement);
  const token = (name: string) => styles.getPropertyValue(name).trim();
  const textColor = token('--text-muted');
  const ascentColor = token('--warning-text');
  const descentColor = token('--accent');
  const c = ROUTE_LINE_COLOR;
  const baseOptions = buildChartOptions({
    seriesName: 'Elevation',
    seriesColor: c,
    measurementDimension: 'elevation',
    xMode: 'distance',
    textColorToken: '--text-muted',
    axisLabelFontSize: '11px',
    animation: false,
    spacing: [4, 2, 8, 2],
    responsive: false,
  });
  return {
    ...baseOptions,
    yAxis: {
      ...(baseOptions.yAxis as Highcharts.YAxisOptions),
      labels: {
        ...((baseOptions.yAxis as Highcharts.YAxisOptions).labels ?? {}),
        formatter(this: Highcharts.AxisLabelsFormatterContextObject) {
          const elevation = elevationDisplayValue(this.value as number, measurementSystem.value);
          const unit = MEASUREMENT_DISPLAY_PROFILES[measurementSystem.value].elevation;
          return Math.round(elevation) + (this.isLast ? `\u202f${unit}` : '');
        },
      },
    },
    tooltip: {
      ...(baseOptions.tooltip as Highcharts.TooltipOptions),
      formatter(this: Highcharts.Point) {
        const pt = this as Highcharts.Point & { grade?: number };
        const distance = longDistanceDisplayValue((this.x as number) * 1000, measurementSystem.value).toFixed(2);
        const distanceUnit = MEASUREMENT_DISPLAY_PROFILES[measurementSystem.value].longDistance;
        const elevation = Math.round(elevationDisplayValue(this.y as number, measurementSystem.value));
        const elevationUnit = MEASUREMENT_DISPLAY_PROFILES[measurementSystem.value].elevation;
        const grade = pt.grade ?? 0;
        const gradeColor = grade > 0 ? ascentColor : grade < 0 ? descentColor : textColor;
        const gradeStr = (grade > 0 ? '+' : '') + grade.toFixed(1) + '%';
        return (
          `<span style="font-size:10px;color:${textColor}">${distance}\u202f${distanceUnit}</span><br/>` +
          `<b>${elevation}\u202f${elevationUnit}</b>&nbsp;<span style="color:${gradeColor}">${gradeStr}</span>`
        );
      },
    },
    plotOptions: {
      area: {
        ...(baseOptions.plotOptions?.area as Highcharts.PlotAreaOptions),
        point: {
          events: {
            mouseOver(this: Highcharts.Point) {
              const pt = this as Highcharts.Point & { lng?: number; lat?: number };
              if (pt.lng != null && pt.lat != null) {
                emit('hover', {
                  lng: pt.lng,
                  lat: pt.lat,
                  elevationM: this.y ?? 0,
                  distanceM: (this.x ?? 0) * 1000,
                });
              }
            },
            mouseOut() {
              emit('hover', null);
            },
          },
        },
      },
    },
    series: [
      {
        type: 'area',
        name: 'Elevation',
        data: [],
      },
    ],
  };
}

const chartOptions = ref<Highcharts.Options>(buildOptions());

watch(measurementSystem, () => {
  const nextOptions = buildOptions();
  (nextOptions as { series: Array<{ data?: unknown }> }).series[0].data = seriesData.value;
  chartOptions.value = nextOptions;
});

// Feed data into the chart whenever the route changes.
watch(
  seriesData,
  (data) => {
    const chart = chartRef.value?.chart;
    if (chart) {
      chart.series[0].setData(data as Highcharts.PointOptionsType[], true, false);
    } else {
      // Chart not yet mounted — pre-populate so it renders correctly on first mount.
      (chartOptions.value as { series: Array<{ data: unknown }> }).series[0].data = data;
    }
  },
  { immediate: true }
);
</script>

<style scoped>
.elev-profile {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 0;
}
.elev-profile__chart {
  flex: 1 1 auto;
  min-height: 5rem;
}
.elev-profile--empty {
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
  color: var(--text-muted);
  flex: 0 0 auto;
}
.elev-profile__placeholder {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
}
</style>
