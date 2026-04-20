/**
 * Synchronized crosshair for multiple Highcharts charts.
 * When the mouse moves over any registered chart, all other registered charts
 * show a crosshair and tooltip at the same x-position (Grafana-style).
 *
 * Also bridges to useTrackMapSync so the mini-map marker follows chart hover/click.
 *
 * Usage:
 *   const { registerChart, unregisterChart, syncMouseMove, syncMouseLeave, syncClick } = useChartSync()
 *   // in mounted(): registerChart(chartInstance)
 *   // on chart container mousemove: syncMouseMove(e, chartInstance)
 *   // on chart container mouseleave: syncMouseLeave()
 *   // on chart container click: syncClick(e, chartInstance)
 *   // in beforeUnmount(): unregisterChart(chartInstance)
 *
 * ── Sync pitfalls (lessons learned) ──────────────────────────────────────────
 *
 * 1. Distance mode: map → chart sync broke in distance mode.
 *    showChartsAtTimestamp() always passed a timestamp value, but in distance
 *    mode the chart x-axis uses kilometres, so the binary search matched the
 *    wrong point. Fix: showChartsAtPoint() picks timestamp or distanceKm based
 *    on currentXMode before calling showChartsAtXValue().
 *
 * 2. Chart point marker (circle) not shown during map hover.
 *    tooltip.refresh() + drawCrosshair() move the tooltip and crosshair line,
 *    but do NOT light up the per-point circle marker.  You must also call
 *    point.setState('hover') explicitly.  The chartTheme already configures
 *    marker.states.hover.enabled = true, but that only activates when the
 *    state is set programmatically here.
 *    Remember to call point.setState('') (tracked in lastHoveredPoints) when
 *    the hover ends, otherwise the circle stays lit indefinitely.
 *
 * 3. Map mouseout did not clear chart crosshairs.
 *    Without an explicit clearChartCrosshairs() call on MapLibre's 'mouseout'
 *    event, the chart tooltip + crosshair lingered after the cursor left the map.
 */

import {useTrackMapSync} from '@/composables/useTrackMapSync';
import type Highcharts from 'highcharts';

// Module-level registry so all chart components on the page share state.
const registeredCharts = new Set<Highcharts.Chart>();

// Track last-hovered points so we can clear their state on leave.
let lastHoveredPoints: Highcharts.Point[] = [];

const {findPointByTimestamp, findPointByDistance, setHoverPoint, setHoverByTimestamp, setPinnedPoint, getStartTs} = useTrackMapSync();

// Current x-axis mode — shared across all chart components.
let currentXMode: 'time' | 'distance' = 'time';

export function useChartSync() {
  function setXMode(mode: 'time' | 'distance'): void {
    currentXMode = mode;
  }

  function registerChart(chart: Highcharts.Chart): void {
    registeredCharts.add(chart);
  }

  function unregisterChart(chart: Highcharts.Chart): void {
    registeredCharts.delete(chart);
  }

  /** Extract the x-value (timestamp) from a Highcharts point found via mouse event. */
  function findTimestampFromEvent(e: MouseEvent, chart: Highcharts.Chart): number | null {
    if (!chart.series?.length) return null;
    const event = (chart as any).pointer.normalize(e);
    const point = chart.series[0].searchPoint(event, true);
    return point ? point.x : null;
  }

  /**
   * Called on mousemove of the source chart. Syncs tooltip + crosshair on all
   * other registered charts at the same x-position, and updates the map hover marker.
   */
  function syncMouseMove(e: MouseEvent, sourceChart: Highcharts.Chart): void {
    let timestamp: number | null = null;   // elapsed ms (chart x-value) — used for distance mode
    let absoluteTs: number | null = null;  // absolute ms timestamp — used for time mode map sync

    registeredCharts.forEach((chart) => {
      if (chart === sourceChart) {
        // Extract x-value and absolute timestamp from the source chart point
        if (chart.series?.length) {
          const event = (chart as any).pointer.normalize(e);
          const point = chart.series[0].searchPoint(event, true);
          if (point) {
            timestamp = point.x;
            absoluteTs = (point as any).ts ?? null;
          }
        }
        return;
      }
      if (!chart.series?.length) return;

      const event = (chart as any).pointer.normalize(e);
      const point = chart.series[0].searchPoint(event, true);

      if (point) {
        chart.tooltip.refresh(point);
        chart.xAxis[0].drawCrosshair(event, point);
      }
    });

    // Bridge to map sync
    if (absoluteTs != null) {
      if (currentXMode === 'distance') {
        // Distance mode: no retry-on-race needed, resolve directly
        setHoverPoint(findPointByDistance(timestamp!));
      } else {
        // Time mode: use absolute ts (point.ts) so findPointByTimestamp matches correctly.
        setHoverByTimestamp(absoluteTs);
      }
    }
  }

  /**
   * Called on mouseleave of any chart. Clears all tooltips and crosshairs
   * and removes the hover marker (pinned stays).
   */
  function syncMouseLeave(): void {
    registeredCharts.forEach((chart) => {
      chart.tooltip.hide();
      chart.xAxis[0].hideCrosshair();
    });
    setHoverPoint(null);
  }

  /**
   * Called on click of a chart. Pins the position so it persists after mouse leaves.
   */
  function syncClick(e: MouseEvent, sourceChart: Highcharts.Chart): void {
    if (!sourceChart.series?.length) return;
    const event = (sourceChart as any).pointer.normalize(e);
    const point = sourceChart.series[0].searchPoint(event, true);
    if (!point) return;
    const trackPoint = currentXMode === 'distance'
      ? findPointByDistance(point.x)
      : findPointByTimestamp((point as any).ts ?? point.x);
    setPinnedPoint(trackPoint);
  }

  /**
   * Show crosshair + tooltip + hover marker on all charts for a given x-value.
   */
  function showChartsAtXValue(xVal: number): void {
    // Clear previous hover states
    for (const p of lastHoveredPoints) {
      try { p.setState(''); } catch { /* point may have been destroyed */ }
    }
    lastHoveredPoints = [];

    registeredCharts.forEach((chart) => {
      if (!chart.series?.length) return;
      const points = chart.series[0].points;
      if (!points?.length) return;

      // Binary search for closest point by x-value
      let lo = 0;
      let hi = points.length - 1;
      while (lo < hi) {
        const mid = (lo + hi) >> 1;
        if (points[mid].x < xVal) lo = mid + 1;
        else hi = mid;
      }
      if (lo > 0 && Math.abs(points[lo - 1].x - xVal) < Math.abs(points[lo].x - xVal)) {
        lo = lo - 1;
      }
      const point = points[lo];
      if (point) {
        point.setState('hover');
        lastHoveredPoints.push(point);
        chart.tooltip.refresh(point);
        chart.xAxis[0].drawCrosshair(undefined, point);
      }
    });
  }

  /**
   * Show crosshair + tooltip on all charts for a given TrackPoint (called from map → charts).
   * Resolves the correct x-value based on the current xMode.
   */
  function showChartsAtPoint(tp: { timestamp: number; distanceKm: number }): void {
    let xVal: number;
    if (currentXMode === 'distance') {
      xVal = tp.distanceKm;
    } else {
      // Chart x-axis uses elapsed ms from track start, not absolute timestamps.
      xVal = tp.timestamp - getStartTs();
    }
    showChartsAtXValue(xVal);
  }

  /** @deprecated use showChartsAtPoint */
  function showChartsAtTimestamp(timestamp: number): void {
    showChartsAtXValue(timestamp);
  }

  /**
   * Clear crosshairs, tooltips, and hover markers on all charts (called from map mouseout).
   */
  function clearChartCrosshairs(): void {
    for (const p of lastHoveredPoints) {
      try { p.setState(''); } catch { /* point may have been destroyed */ }
    }
    lastHoveredPoints = [];
    registeredCharts.forEach((chart) => {
      chart.tooltip.hide();
      chart.xAxis[0].hideCrosshair();
    });
  }

  return {registerChart, unregisterChart, syncMouseMove, syncMouseLeave, syncClick, showChartsAtTimestamp, showChartsAtPoint, clearChartCrosshairs, setXMode};
}
