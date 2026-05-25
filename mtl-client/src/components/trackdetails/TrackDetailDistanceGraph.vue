<template>
  <div class="tool-container">
    <h3 class="chart-title">Distance over Time</h3>
    <highcharts :options="chartOptions" class="chart"></highcharts>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import type { GpsTrackDataPoint } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/GpsTrackDataPoint';
import { fetchTrackDetails } from '@/utils/ServiceHelper';

defineOptions({
  name: 'TrackDetailDistanceGraph',
});

const props = defineProps<{
  gpsTrackId: number;
}>();

const chartOptions = ref({
  title: { text: null },
  xAxis: {
    type: 'datetime',
    title: { text: 'Time' },
  },
  yAxis: {
    title: { text: 'Distance (km)' },
  },
  series: [
    {
      name: 'Distance',
      data: [] as Array<[Date | undefined, number]>,
    },
  ],
});

onMounted(() => {
  load();
});

async function load() {
  const details = (await fetchTrackDetails(props.gpsTrackId)) as GpsTrackDataPoint[];
  const chartData = details.map(
    (item) => [item.pointTimestamp, (item.distanceInMeterSinceStart ?? 0) / 1000] as [Date | undefined, number]
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

@media screen and (max-width: 768px) {
  .tool-container {
    min-height: 300px;
  }
  .chart {
    min-height: 300px;
  }
}
</style>
