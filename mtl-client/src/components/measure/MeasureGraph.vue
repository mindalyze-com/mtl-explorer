<template>
  <div ref="chartContainer" class="chart-container">
    <highcharts ref="highchartsComponent" :options="chartOptions" class="chart"></highcharts>
  </div>

</template>

<script>
import {defineComponent, inject} from "vue";
import {formatNumber} from "@/utils/Utils";

export default defineComponent({
  name: 'MeasureGraph',
  components: {},
  props: ['graphSeriesData'],
  data() {
    return {
      resizeObserver: null,
      chartOptions: {
        chart: {
          type: 'line',
          height: null,
          spacing: [16, 12, 16, 12],
          backgroundColor: 'transparent',
        },
        credits: { enabled: false },
        title: {text: ''},
        legend: {
          enabled: true,
          align: 'left',
          verticalAlign: 'top',
          itemStyle: {
            color: 'var(--text-secondary)',
            fontWeight: '500'
          },
          itemHoverStyle: {
            color: 'var(--text-primary)'
          }
        },
        xAxis: {
          type: 'datetime',
          lineColor: 'var(--border-default)',
          tickColor: 'var(--border-default)',
          labels: {
            style: {
              color: 'var(--chart-text)',
              fontSize: '12px'
            }
          }
        },
        yAxis: {
          title: {
            text: 'Speed (km/h)',
            style: {
              color: 'var(--text-secondary)',
              fontWeight: '600'
            }
          },
          gridLineColor: 'var(--chart-grid)',
          labels: {
            style: {
              color: 'var(--chart-text)',
              fontSize: '12px'
            },
            formatter: function () {
              return this.value;
            }
          }
        },
        tooltip: {
          backgroundColor: 'var(--chart-tooltip-bg)',
          borderColor: 'var(--border-default)',
          borderRadius: 14,
          shadow: false,
          style: {
            color: 'var(--chart-tooltip-text)'
          },
          formatter: function () {
            return 'Track segment speed was in average <b>' + formatNumber(this.y, 3) + ' km/h</b>';
          }
        },
        plotOptions: {
          line: {
            lineWidth: 2.25,
            marker: {
              enabled: false,
              states: {
                hover: {
                  enabled: true,
                  radius: 4
                }
              }
            },
            states: {
              hover: {
                lineWidthPlus: 0
              }
            }
          },
          series: {
            animation: false
          }
        },
        series: this.graphSeriesData,
        accessibility: {
          enabled: false
        },
        responsive: {
          rules: [
            {
              condition: { maxWidth: 720 },
              chartOptions: {
                chart: { spacing: [12, 8, 12, 8] },
                legend: {
                  align: 'left',
                  verticalAlign: 'bottom'
                }
              }
            },
            {
              condition: { maxWidth: 500 },
              chartOptions: {
                chart: { spacing: [8, 4, 10, 4] },
                yAxis: {
                  title: { text: null }
                }
              }
            }
          ]
        }
      }
    }
  },
  setup() {
    return {
      toast: inject("toast"),
    };
  },
  mounted() {
    this.resizeObserver = new ResizeObserver((entries) => {
      const rect = entries[0]?.contentRect;
      if (!rect) return;
      const chart = this.$refs.highchartsComponent?.chart;
      if (chart && rect.width > 0 && rect.height > 0) {
        chart.setSize(rect.width, rect.height, false);
      } else {
        this.reflowChart();
      }
    });

    if (this.$refs.chartContainer) {
      this.resizeObserver.observe(this.$refs.chartContainer);
    }
    this.$nextTick(() => this.reflowChart());
  },
  watch: {
    graphSeriesData(newData) {
      this.chartOptions.series = newData;
      this.reflowChart();
    },
  },
  beforeUnmount() {
    if (this.resizeObserver) {
      this.resizeObserver.disconnect();
    }
  },
  methods: {
    reflowChart() {
      if (this.$refs.highchartsComponent && this.$refs.highchartsComponent.chart) {
        this.$refs.highchartsComponent.chart.reflow();
      }
    }
  },
});
</script>

<style scoped>

.chart-container {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 0;
  width: 100%;
  overflow: hidden;
}

.chart {
  flex: 1 1 auto;
  width: 100%;
  min-height: min(320px, 48svh);
  overflow: hidden;
}

@media screen and (max-width: 768px) {
  .chart {
    min-height: min(260px, 44svh);
  }
}

</style>
