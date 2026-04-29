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
    cum += haversine(coords[i - 1][1], coords[i - 1][0], coords[i][1], coords[i][0]);
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
  const gridColor = token('--chart-grid');
  const tooltipBg = token('--chart-tooltip-bg');
  const tooltipText = token('--chart-tooltip-text');
  const borderColor = token('--border-default');
  const ascentColor = token('--warning-text');
  const descentColor = token('--accent');
  const c = ROUTE_LINE_COLOR;
  const hexToRgba = (hex: string, a: number) => {
    const r = parseInt(hex.slice(1, 3), 16);
    const g = parseInt(hex.slice(3, 5), 16);
    const b = parseInt(hex.slice(5, 7), 16);
    return `rgba(${r},${g},${b},${a})`;
  };

  return {
    chart: {
      type: 'area',
      backgroundColor: 'transparent',
      spacing: [4, 2, 8, 2],
      animation: false,
      style: {
        fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif",
      },
    },
    title: { text: undefined },
    credits: { enabled: false },
    legend: { enabled: false },
    xAxis: {
      type: 'linear',
      crosshair: {
        width: 1,
        color: borderColor,
        dashStyle: 'Dash',
      },
      labels: {
        style: { color: textColor, fontSize: '11px' },
        formatter(this: Highcharts.AxisLabelsFormatterContextObject) {
          return parseFloat((this.value as number).toFixed(1)) + '\u202fkm';
        },
      },
      lineColor: gridColor,
      tickColor: 'transparent',
      title: { text: undefined },
    },
    yAxis: {
      gridLineColor: gridColor,
      title: { text: undefined },
      labels: {
        style: { color: textColor, fontSize: '11px' },
        formatter(this: Highcharts.AxisLabelsFormatterContextObject) {
          return Math.round(this.value as number) + (this.isLast ? '\u202fm' : '');
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
      useHTML: true,
      formatter(this: Highcharts.Point) {
        const pt = this as Highcharts.Point & { grade?: number };
        const km = (this.x as number).toFixed(2);
        const ele = Math.round(this.y as number);
        const grade = pt.grade ?? 0;
        const gradeColor = grade > 0 ? ascentColor : grade < 0 ? descentColor : textColor;
        const gradeStr = (grade > 0 ? '+' : '') + grade.toFixed(1) + '%';
        return (
          `<span style="font-size:10px;color:${textColor}">${km}\u202fkm</span><br/>` +
          `<b>${ele}\u202fm</b>&nbsp;<span style="color:${gradeColor}">${gradeStr}</span>`
        );
      },
    },
    plotOptions: {
      area: {
        lineWidth: 2,
        color: c,
        fillColor: {
          linearGradient: { x1: 0, y1: 0, x2: 0, y2: 1 },
          stops: [
            [0, hexToRgba(c, 0.28)],
            [1, hexToRgba(c, 0.00)],
          ],
        },
        threshold: null,
        marker: {
          enabled: false,
          states: { hover: { enabled: true, radius: 3, lineWidth: 0 } },
        },
        states: { hover: { lineWidthPlus: 0 } },
        connectNulls: false,
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

// Feed data into the chart whenever the route changes.
watch(seriesData, (data) => {
  const chart = chartRef.value?.chart;
  if (chart) {
    chart.series[0].setData(data as Highcharts.PointOptionsType[], true, false);
  } else {
    // Chart not yet mounted — pre-populate so it renders correctly on first mount.
    (chartOptions.value as { series: Array<{ data: unknown }> }).series[0].data = data;
  }
}, { immediate: true });

// ── Helpers ───────────────────────────────────────────────────────

function haversine(lat1: number, lng1: number, lat2: number, lng2: number): number {
  const R = 6_371_000;
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLng = ((lng2 - lng1) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos((lat1 * Math.PI) / 180) * Math.cos((lat2 * Math.PI) / 180) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}
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
