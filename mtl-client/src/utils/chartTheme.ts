import type Highcharts from 'highcharts'
import { formatDurationSmart } from '@/utils/Utils'

/**
 * Shared Highcharts theme builder.
 * Reads chart tokens from <html> at call time and returns
 * a fully styled options object.  Each chart component calls buildChartOptions()
 * from data() and then only mutates series[0].data in load().
 */

export interface ChartThemeConfig {
  /** Legend / tooltip series name */
  seriesName: string
  /** 6-char hex, e.g. '#6366f1' */
  seriesColor: string
  /** Unit appended to y-axis labels and tooltip, e.g. 'm', 'km/h' */
  unit?: string
  /** Tooltip decimal places (default 1) */
  decimals?: number
  /** Hard y-axis minimum (use 0 for speed, power etc.) */
  yMin?: number
  /** Connect null data points (default false) */
  connectNulls?: boolean
  /** X-axis mode: 'time' (default) or 'distance' */
  xMode?: 'time' | 'distance'
}

function hexToRgba(hex: string, alpha: number): string {
  const r = parseInt(hex.slice(1, 3), 16)
  const g = parseInt(hex.slice(3, 5), 16)
  const b = parseInt(hex.slice(5, 7), 16)
  return `rgba(${r},${g},${b},${alpha})`
}

/** Compact tick label — no trailing .0, k-suffix above 1000 */
function compactNum(v: number): string {
  if (v === 0) return '0'
  if (Math.abs(v) >= 1000) return (v / 1000).toFixed(v % 1000 === 0 ? 0 : 1) + 'k'
  if (Math.abs(v) >= 10)  return Math.round(v).toString()
  return parseFloat(v.toFixed(1)).toString()
}

export function buildChartOptions(config: ChartThemeConfig): Highcharts.Options {
  const styles = getComputedStyle(document.documentElement)
  const token = (name: string) => styles.getPropertyValue(name).trim()
  const textColor = token('--chart-text')
  const gridColor = token('--chart-grid')
  const tooltipBg = token('--chart-tooltip-bg')
  const tooltipText = token('--chart-tooltip-text')
  const borderColor = token('--border-default')
  const c = config.seriesColor
  const isDistance = config.xMode === 'distance'

  return {
    chart: {
      type: 'area',
      backgroundColor: 'transparent',
      spacing: [4, 4, 10, 4],
      style: {
        fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif",
      },
    },
    responsive: {
      rules: [{
        condition: { maxWidth: 500 },
        chartOptions: { chart: { spacing: [2, 0, 6, 0] } },
      }],
    },
    title:   { text: undefined },
    credits: { enabled: false },
    legend:  { enabled: false },
    xAxis: {
      type: 'linear',
      crosshair: {
        width: 1,
        color: borderColor,
        dashStyle: 'Dash',
      },
      labels: {
        style: { color: textColor, fontSize: '12px' },
        formatter(this: any) {
          if (isDistance) {
            return parseFloat((this.value as number).toFixed(1)) + '\u202fkm'
          }
          return formatDurationSmart(this.value as number, (this as any).axis?.max as number)
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
        style: { color: textColor, fontSize: '12px' },
        formatter(this: any) {
          const n = compactNum(this.value as number)
          return (this.isLast && config.unit) ? n + '\u202f' + config.unit : n
        },
      },
      ...(config.yMin !== undefined ? { min: config.yMin } : {}),
    },
    tooltip: {
      backgroundColor: tooltipBg,
      borderColor:     borderColor,
      borderRadius:    8,
      borderWidth:     1,
      shadow:          false,
      style:           { color: tooltipText, fontSize: '12px' },
      useHTML:         true,
      formatter(this: any) {
        if (this.y == null) return false as any
        const point = this.point as any
        const ts = point?.ts as number | undefined
        const timeOfDay = ts != null
          ? new Date(ts).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
          : ''
        const decimals = config.decimals !== undefined ? config.decimals : 1
        const val = (this.y as number).toFixed(decimals)
        const unit = config.unit ? `\u202f${config.unit}` : ''
        if (isDistance) {
          const km = (this.x as number).toFixed(1)
          const timeStr = timeOfDay ? ` · ${timeOfDay}` : ''
          return `<span style="font-size:10px">${km}\u202fkm${timeStr}</span><br/><b>${val}${unit}</b>`
        } else {
          const maxX = (this.series as any).xAxis?.max as number ?? (this.x as number)
          const elapsed = formatDurationSmart(this.x as number, maxX)
          const timeStr = timeOfDay ? ` · ${timeOfDay}` : ''
          return `<span style="font-size:10px">${elapsed}${timeStr}</span><br/><b>${val}${unit}</b>`
        }
      },
    },
    plotOptions: {
      area: {
        lineWidth: 2,
        color:     c,
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
        connectNulls: config.connectNulls ?? false,
      },
    },
    series: [{
      name: config.seriesName,
      data: [],
    }],
  }
}
