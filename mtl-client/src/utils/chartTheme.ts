import type Highcharts from 'highcharts';
import { formatDurationSmart } from '@/utils/Utils';
import { getMeasurementSystem } from '@/composables/useMeasurementSystem';
import {
  elevationDisplayValue,
  longDistanceDisplayValue,
  MEASUREMENT_DISPLAY_PROFILES,
  speedDisplayValue,
  verticalRateDisplayValue,
} from '@/utils/units';

export type ChartMeasurementDimension = 'longDistance' | 'elevation' | 'speed' | 'verticalRate';

/**
 * Shared Highcharts theme builder.
 * Reads chart tokens from <html> at call time and returns
 * a fully styled options object.  Each chart component calls buildChartOptions()
 * from data() and then only mutates series[0].data in load().
 */

export interface ChartThemeConfig {
  /** Legend / tooltip series name */
  seriesName: string;
  /** 6-char hex, e.g. '#6366f1' */
  seriesColor: string;
  /** Unit appended to y-axis labels and tooltip, e.g. 'm', 'km/h' */
  unit?: string;
  /** Canonical measurement represented by y-values. */
  measurementDimension?: ChartMeasurementDimension;
  /** Tooltip decimal places (default 1) */
  decimals?: number;
  /** Hard y-axis minimum (use 0 for speed, power etc.) */
  yMin?: number;
  /** Connect null data points (default false) */
  connectNulls?: boolean;
  /** Optional tooltip label for a min/max range band. */
  rangeTooltipLabel?: string;
  /** X-axis mode: 'time' (default) or 'distance' */
  xMode?: 'time' | 'distance';
  /** CSS token used for axis labels. */
  textColorToken?: string;
  /** Axis-label font size (default 12px). */
  axisLabelFontSize?: string;
  /** Chart type (default area). */
  chartType?: Highcharts.ChartOptions['type'];
  /** Fixed chart height. */
  height?: number;
  /** Chart spacing override. */
  spacing?: [number, number, number, number];
  /** Highcharts animation setting. */
  animation?: boolean;
  /** Apply the default small-screen spacing rule (default true). */
  responsive?: boolean;
}

export function hexToRgba(hex: string, alpha: number): string {
  const normalized = hex.replace('#', '');
  if (!/^[0-9a-f]{6}$/i.test(normalized)) return hex;
  const r = parseInt(normalized.slice(0, 2), 16);
  const g = parseInt(normalized.slice(2, 4), 16);
  const b = parseInt(normalized.slice(4, 6), 16);
  return `rgba(${r},${g},${b},${alpha})`;
}

/** Compact tick label — no trailing .0, k-suffix above 1000 */
export function compactNum(v: number): string {
  if (v === 0) return '0';
  if (Math.abs(v) >= 1000) return (v / 1000).toFixed(v % 1000 === 0 ? 0 : 1) + 'k';
  if (Math.abs(v) >= 10) return Math.round(v).toString();
  return parseFloat(v.toFixed(1)).toString();
}

export function buildChartOptions(config: ChartThemeConfig): Highcharts.Options {
  const styles = getComputedStyle(document.documentElement);
  const token = (name: string) => styles.getPropertyValue(name).trim();
  const textColor = token(config.textColorToken ?? '--chart-text');
  const gridColor = token('--chart-grid');
  const tooltipBg = token('--chart-tooltip-bg');
  const tooltipText = token('--chart-tooltip-text');
  const borderColor = token('--border-default');
  const c = config.seriesColor;
  const isDistance = config.xMode === 'distance';
  const measurementSystem = getMeasurementSystem();
  const displayUnit = config.measurementDimension
    ? chartMeasurementUnit(config.measurementDimension, measurementSystem)
    : config.unit;

  return {
    chart: {
      type: config.chartType ?? 'area',
      ...(config.height !== undefined ? { height: config.height } : {}),
      ...(config.animation !== undefined ? { animation: config.animation } : {}),
      backgroundColor: 'transparent',
      spacing: config.spacing ?? [4, 4, 10, 4],
      style: {
        fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif",
      },
    },
    ...(config.responsive === false
      ? {}
      : {
          responsive: {
            rules: [
              {
                condition: { maxWidth: 500 },
                chartOptions: { chart: { spacing: [2, 0, 6, 0] } },
              },
            ],
          },
        }),
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
        style: { color: textColor, fontSize: config.axisLabelFontSize ?? '12px' },
        formatter(this: Highcharts.AxisLabelsFormatterContextObject) {
          if (isDistance) {
            const distance = longDistanceDisplayValue((this.value as number) * 1000, measurementSystem);
            return (
              parseFloat(distance.toFixed(1)) + '\u202f' + MEASUREMENT_DISPLAY_PROFILES[measurementSystem].longDistance
            );
          }
          return formatDurationSmart(this.value as number, this.axis?.max as number);
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
        style: { color: textColor, fontSize: config.axisLabelFontSize ?? '12px' },
        formatter(this: Highcharts.AxisLabelsFormatterContextObject) {
          const value = chartMeasurementDisplayValue(
            this.value as number,
            config.measurementDimension,
            measurementSystem
          );
          const n = compactNum(value);
          return this.isLast && displayUnit ? n + '\u202f' + displayUnit : n;
        },
      },
      ...(config.yMin !== undefined ? { min: config.yMin } : {}),
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
        if (this.y == null) return false;
        const point = this as Highcharts.Point & {
          rangeHigh?: number;
          rangeLow?: number;
          ts?: number;
        };
        const ts = point?.ts as number | undefined;
        const rangeLow = typeof point?.rangeLow === 'number' ? point.rangeLow : null;
        const rangeHigh = typeof point?.rangeHigh === 'number' ? point.rangeHigh : null;
        const timeOfDay = ts != null ? new Date(ts).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '';
        const decimals = config.decimals !== undefined ? config.decimals : 1;
        const displayValue = chartMeasurementDisplayValue(
          this.y as number,
          config.measurementDimension,
          measurementSystem
        );
        const val = displayValue.toFixed(decimals);
        const unit = displayUnit ? `\u202f${displayUnit}` : '';
        const rangeLabel = config.rangeTooltipLabel ? `${config.rangeTooltipLabel}: ` : '';
        const rangeText =
          rangeLow != null && rangeHigh != null
            ? `<br/><span style="font-size:10px">${rangeLabel}min ${chartMeasurementDisplayValue(rangeLow, config.measurementDimension, measurementSystem).toFixed(decimals)}${unit} · max ${chartMeasurementDisplayValue(rangeHigh, config.measurementDimension, measurementSystem).toFixed(decimals)}${unit} · spread ${chartMeasurementDisplayValue(rangeHigh - rangeLow, config.measurementDimension, measurementSystem).toFixed(decimals)}${unit}</span>`
            : '';
        if (isDistance) {
          const distance = longDistanceDisplayValue((this.x as number) * 1000, measurementSystem).toFixed(1);
          const distanceUnit = MEASUREMENT_DISPLAY_PROFILES[measurementSystem].longDistance;
          const timeStr = timeOfDay ? ` · ${timeOfDay}` : '';
          return `<span style="font-size:10px">${distance}\u202f${distanceUnit}${timeStr}</span><br/><b>${val}${unit}</b>${rangeText}`;
        } else {
          const maxX = (this.series.xAxis?.max as number | undefined) ?? (this.x as number);
          const elapsed = formatDurationSmart(this.x as number, maxX);
          const timeStr = timeOfDay ? ` · ${timeOfDay}` : '';
          return `<span style="font-size:10px">${elapsed}${timeStr}</span><br/><b>${val}${unit}</b>${rangeText}`;
        }
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
            [1, hexToRgba(c, 0.0)],
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
    series: [
      {
        name: config.seriesName,
        data: [],
      },
    ],
  };
}

export function chartMeasurementDisplayValue(
  value: number,
  dimension: ChartMeasurementDimension | undefined,
  system: ReturnType<typeof getMeasurementSystem>
): number {
  if (dimension === 'longDistance') return longDistanceDisplayValue(value, system);
  if (dimension === 'elevation') return elevationDisplayValue(value, system);
  if (dimension === 'speed') return speedDisplayValue(value, system);
  if (dimension === 'verticalRate') return verticalRateDisplayValue(value, system);
  return value;
}

export function chartMeasurementUnit(
  dimension: ChartMeasurementDimension,
  system: ReturnType<typeof getMeasurementSystem>
): string {
  const profile = MEASUREMENT_DISPLAY_PROFILES[system];
  if (dimension === 'longDistance') return profile.longDistance;
  return profile[dimension];
}
