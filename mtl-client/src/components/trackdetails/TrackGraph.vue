<template>
  <div class="chart-card">
    <div class="chart-header">
      <i class="bi" :class="config.icon"></i>
      {{ config.title }}
    </div>
    <highcharts ref="highchartsEl" :options="chartOptions" class="chart"></highcharts>
  </div>
</template>

<script lang="ts">
import { defineComponent, inject, type PropType } from 'vue';
import { useChartSync } from '@/composables/useChartSync';
import { buildChartOptions } from '@/utils/chartTheme';
import type { GpsTrackDataPoint } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import type Highcharts from 'highcharts';
import type { TrackGraphConfig } from './trackGraphConfigs';

/**
 * Generic per-metric track-detail chart.
 *
 * Replaces TrackDetailDistance/Elevation/ElevationGain/Energy/Power/SpeedGraph.vue.
 * Each call site supplies a {@link TrackGraphConfig} from `trackGraphConfigs.ts`
 * that describes the icon, header, color, units, and y-extractor.
 */
export default defineComponent({
  name: 'TrackGraph',
  props: {
    trackDetails: {
      type: Array as PropType<GpsTrackDataPoint[]>,
      required: true,
    },
    config: {
      type: Object as PropType<TrackGraphConfig>,
      required: true,
    },
    xMode: {
      type: String as PropType<'time' | 'distance'>,
      default: 'time',
    },
  },
  data(): { chartOptions: Highcharts.Options } {
    return {
      chartOptions: buildChartOptions({ ...this.config, xMode: this.xMode }),
    };
  },
  setup() {
    const toast = inject('toast');
    const { registerChart, unregisterChart, syncMouseMove, syncMouseLeave, syncClick } = useChartSync();
    return { toast, registerChart, unregisterChart, syncMouseMove, syncMouseLeave, syncClick };
  },
  mounted() {
    if (this.trackDetails.length > 0) {
      this.updateChart();
    }
    this.$nextTick(() => {
      const chart = (this.$refs.highchartsEl as { chart?: Highcharts.Chart } | undefined)?.chart;
      if (chart) {
        (this as unknown as { _chartInstance: Highcharts.Chart })._chartInstance = chart;
        this.registerChart(chart);
        chart.container.addEventListener('mousemove', (e: MouseEvent) => this.syncMouseMove(e, chart));
        chart.container.addEventListener('mouseleave', () => this.syncMouseLeave());
        chart.container.addEventListener('click', (e: MouseEvent) => this.syncClick(e, chart));
      }
    });
  },
  beforeUnmount() {
    const inst = (this as unknown as { _chartInstance?: Highcharts.Chart })._chartInstance;
    if (inst) {
      this.unregisterChart(inst);
    }
  },
  watch: {
    trackDetails() {
      this.updateChart();
    },
    xMode() {
      this.chartOptions = buildChartOptions({ ...this.config, xMode: this.xMode });
      this.updateChart();
    },
    config: {
      deep: true,
      handler() {
        this.chartOptions = buildChartOptions({ ...this.config, xMode: this.xMode });
        this.updateChart();
      },
    },
  },
  methods: {
    updateChart() {
      const startTs = this.trackDetails.length > 0 ? toMillis(this.trackDetails[0].pointTimestamp) : 0;
      const isDistance = this.xMode === 'distance';
      const extractY = this.config.extractY;
      const filterNulls = this.config.filterNullY === true;

      const data: Array<{ x: number; y: number | null | undefined; ts: number }> = [];
      for (const item of this.trackDetails) {
        const y = extractY(item);
        if (filterNulls && (y === null || y === undefined)) continue;
        const absTs = toMillis(item.pointTimestamp);
        const x = isDistance ? (item.distanceInMeterSinceStart ?? 0) / 1000 : absTs - startTs;
        data.push({ x, y, ts: absTs });
      }

      (this.chartOptions as Highcharts.Options & { series: Array<{ data: unknown[] }> }).series[0].data = data;
    },
  },
});

function toMillis(ts: GpsTrackDataPoint['pointTimestamp']): number {
  if (!ts) return 0;
  if (typeof ts === 'string') return new Date(ts).getTime();
  return (ts as Date).getTime?.() ?? 0;
}
</script>

<style scoped>
.chart-card {
  display: flex;
  flex-direction: column;
  width: 100%;
  padding-bottom: 1rem;
  border-bottom: 1px solid var(--border-subtle);
}

.chart-card:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.chart-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.75rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--text-secondary);
  padding: 1.25rem 1rem 0.1rem;
}

.chart-header i {
  font-size: 0.85rem;
}

.chart {
  width: 100%;
  height: var(--track-detail-graph-height, 220px);
  min-height: var(--track-detail-graph-height, 220px);
}
</style>
