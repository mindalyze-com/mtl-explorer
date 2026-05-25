<template>
  <div class="tool-container">
    <h3 class="chart-title">Elevation</h3>
    <highcharts :options="chartOptions" class="chart"></highcharts>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import type { GpsTrackDataPoint } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/GpsTrackDataPoint';
import { fetchTrackDetails } from '@/utils/ServiceHelper';

// TODO: REFACTOR? Create one chart and instruct what to display?

defineOptions({
  name: 'TrackDetailElevationGraph',
});

const props = defineProps<{
  gpsTrackId: number;
}>();

const gpsTrackDetails = ref<GpsTrackDataPoint[]>([]);
const chartOptions = ref({
  title: {
    text: null,
  },
  xAxis: {
    type: 'datetime',
    title: {
      text: 'Time',
    },
  },
  yAxis: {
    title: {
      text: 'Elevation (m)',
    },
  },
  series: [
    {
      name: 'Elevation',
      data: [] as Array<[Date | undefined, number | undefined]>, // The data will be filled in dynamically
    },
  ],
});

onMounted(() => {
  load(props.gpsTrackId);
});

async function load(gpsTrackId: number) {
  gpsTrackDetails.value = (await fetchTrackDetails(gpsTrackId)) as GpsTrackDataPoint[];

  const chartData = gpsTrackDetails.value.map(
    (item) => [item.pointTimestamp, item.pointAltitude] as [Date | undefined, number | undefined]
  );

  chartOptions.value.series[0].data = chartData;
}
</script>

<style scoped>
.tool-container {
  display: flex;
  flex-direction: column;
  width: 100%;
  min-height: 400px;
  flex: 1 1 auto;
  align-items: stretch;
}

.chart {
  flex: 1 1 auto;
  width: 100%;
  min-height: 400px;
}

.chart-title {
  margin: 0 0 0.75rem 0;
  font-weight: 600;
}

/* Mobile responsive */
@media screen and (max-width: 768px) {
  .tool-container {
    min-height: 300px;
  }

  .chart {
    min-height: 300px;
  }
}
</style>
