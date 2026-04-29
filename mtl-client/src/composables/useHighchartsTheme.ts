import { watch } from 'vue';
import Highcharts from 'highcharts';
import { useTheme, type ColorScheme } from '@/composables/useTheme';

/**
 * Apply / re-apply the global Highcharts theme based on the current color
 * scheme. Extracted from main.ts (was a top-level statement / inline function).
 *
 * Calling this once at boot is sufficient — the watcher keeps the theme in
 * sync when the user toggles between light and dark.
 */
export function installHighchartsTheme(): void {
  const { colorScheme } = useTheme();
  applyHighchartsTheme(colorScheme.value);
  watch(colorScheme, applyHighchartsTheme);
}

function applyHighchartsTheme(scheme: ColorScheme): void {
  const styles = getComputedStyle(document.documentElement);
  const token = (name: string) => styles.getPropertyValue(name).trim();

  const textPrimary = token('--text-primary');
  const textSecondary = token('--text-secondary');
  const textMuted = token('--text-muted');
  const borderDefault = token('--border-default');
  const chartGrid = token('--chart-grid');
  const chartTooltipBg = token('--chart-tooltip-bg');
  const chartTooltipText = token('--chart-tooltip-text');

  Highcharts.setOptions({
    chart: {
      backgroundColor: 'transparent',
      style: { fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif' },
    },
    title: { style: { color: textMuted } },
    subtitle: { style: { color: textMuted } },
    xAxis: {
      labels: { style: { color: textMuted } },
      title: { style: { color: textMuted } },
      gridLineColor: chartGrid,
      lineColor: borderDefault,
      tickColor: borderDefault,
    },
    yAxis: {
      labels: { style: { color: textMuted } },
      title: { style: { color: textMuted } },
      gridLineColor: chartGrid,
    },
    legend: {
      itemStyle: { color: textMuted },
      itemHoverStyle: { color: textPrimary },
    },
    tooltip: {
      backgroundColor: chartTooltipBg,
      style: { color: chartTooltipText || textSecondary },
      borderColor: borderDefault,
    },
    plotOptions: {
      line: { marker: { enabled: false } },
      area: { marker: { enabled: false } },
    },
    credits: { enabled: false },
  });
}
