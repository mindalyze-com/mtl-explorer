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
  const dark = scheme === 'dark';
  Highcharts.setOptions({
    chart: {
      backgroundColor: 'transparent',
      style: { fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif' },
    },
    title: { style: { color: dark ? '#94a3b8' : '#334155' } },
    subtitle: { style: { color: '#64748b' } },
    xAxis: {
      labels: { style: { color: dark ? '#94a3b8' : '#475569' } },
      title: { style: { color: dark ? '#94a3b8' : '#475569' } },
      gridLineColor: dark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.06)',
      lineColor: dark ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.1)',
      tickColor: dark ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.1)',
    },
    yAxis: {
      labels: { style: { color: dark ? '#94a3b8' : '#475569' } },
      title: { style: { color: dark ? '#94a3b8' : '#475569' } },
      gridLineColor: dark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.06)',
    },
    legend: {
      itemStyle: { color: dark ? '#94a3b8' : '#475569' },
      itemHoverStyle: { color: dark ? '#e2e8f0' : '#1e293b' },
    },
    tooltip: {
      backgroundColor: dark ? 'rgba(15,23,42,0.95)' : 'rgba(255,255,255,0.97)',
      style: { color: dark ? '#e2e8f0' : '#334155' },
      borderColor: dark ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.1)',
    },
    plotOptions: {
      series: { color: '#6366f1' },
      line: { marker: { enabled: false } },
      area: { marker: { enabled: false } },
    },
    credits: { enabled: false },
  });
}
