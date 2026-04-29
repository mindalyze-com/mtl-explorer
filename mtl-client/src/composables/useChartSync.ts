/**
 * Synchronized crosshair for multiple Highcharts charts.
 * When the pointer moves over any registered chart, all other registered charts
 * show a crosshair and tooltip at the same x-position (Grafana-style).
 *
 * Also bridges to trackCursorSync so the mini-map marker follows chart hover/click.
 *
 * Usage:
 *   const { registerChart, unregisterChart, syncMouseMove, syncMouseLeave, syncClick } = useChartSync()
 *   // in mounted(): registerChart(chartInstance)
 *   // on chart container mousemove/touchmove: syncMouseMove(e, chartInstance)
 *   // on chart container mouseleave/touchend: syncMouseLeave()
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

import { useTrackCursorSync, type TrackPoint } from '@/composables/trackCursorSync';
import type Highcharts from 'highcharts';

type ChartSyncMoveEvent = MouseEvent | PointerEvent | TouchEvent;
type PrimaryChartInputEvent = MouseEvent | PointerEvent | Touch;

// Module-level registry so all chart components on the page share state.
const registeredCharts = new Set<Highcharts.Chart>();

// Track last-hovered points so we can clear their state on leave.
let lastHoveredPoints: Highcharts.Point[] = [];

const PASSIVE_TOUCH_LISTENER: AddEventListenerOptions = { passive: true };

const cursor = useTrackCursorSync();

export function getPrimaryChartInputEvent(e: ChartSyncMoveEvent): PrimaryChartInputEvent | null {
  return 'touches' in e
    ? e.touches[0] ?? e.changedTouches[0] ?? null
    : e;
}

export function useChartSync() {
  function setXMode(mode: 'time' | 'distance'): void {
    cursor.setXMode(mode);
  }

  function registerChart(chart: Highcharts.Chart): void {
    registeredCharts.add(chart);
  }

  function unregisterChart(chart: Highcharts.Chart): void {
    registeredCharts.delete(chart);
  }

  function bindChart(chart: Highcharts.Chart): () => void {
    const container = chart.container;
    const onMove = (e: MouseEvent | TouchEvent) => syncMouseMove(e, chart);
    const onLeave = () => syncMouseLeave();
    const onClick = (e: MouseEvent) => syncClick(e, chart);

    registerChart(chart);
    container.addEventListener('mousemove', onMove);
    container.addEventListener('mouseleave', onLeave);
    container.addEventListener('touchstart', onMove, PASSIVE_TOUCH_LISTENER);
    container.addEventListener('touchmove', onMove, PASSIVE_TOUCH_LISTENER);
    container.addEventListener('touchend', onLeave, PASSIVE_TOUCH_LISTENER);
    container.addEventListener('touchcancel', onLeave, PASSIVE_TOUCH_LISTENER);
    container.addEventListener('click', onClick);

    return () => {
      container.removeEventListener('mousemove', onMove);
      container.removeEventListener('mouseleave', onLeave);
      container.removeEventListener('touchstart', onMove);
      container.removeEventListener('touchmove', onMove);
      container.removeEventListener('touchend', onLeave);
      container.removeEventListener('touchcancel', onLeave);
      container.removeEventListener('click', onClick);
      unregisterChart(chart);
    };
  }

  function normalizeChartEvent(e: ChartSyncMoveEvent, chart: Highcharts.Chart): Highcharts.PointerEventObject | null {
    const sourceEvent = getPrimaryChartInputEvent(e);
    if (!sourceEvent) return null;
    return (chart as any).pointer.normalize(sourceEvent);
  }

  /**
   * Called on mouse/touch move of the source chart. Syncs tooltip + crosshair on all
   * other registered charts at the same x-position, and updates the map hover marker.
   */
  function syncMouseMove(e: ChartSyncMoveEvent, sourceChart: Highcharts.Chart): void {
    let chartX: number | null = null;      // elapsed ms or distance, depending on current x-mode
    let absoluteTs: number | null = null;  // absolute ms timestamp — used for time mode map sync

    if (sourceChart.series?.length) {
      const event = normalizeChartEvent(e, sourceChart);
      const point = event ? sourceChart.series[0].searchPoint(event, true) : null;
      if (point) {
        chartX = point.x;
        absoluteTs = (point as any).ts ?? null;
      }
    }

    // Bridge to map sync
    if (chartX != null) {
      showChartsAtXValue(chartX);
      cursor.setHoverByChartPoint(chartX, absoluteTs, 'chart');
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
    cursor.clearHover();
  }

  /**
   * Called on click of a chart. Pins the position so it persists after mouse leaves.
   */
  function syncClick(e: MouseEvent, sourceChart: Highcharts.Chart): void {
    if (!sourceChart.series?.length) return;
    const event = (sourceChart as any).pointer.normalize(e);
    const point = sourceChart.series[0].searchPoint(event, true);
    if (!point) return;
    cursor.setPinnedByChartPoint(point.x, (point as any).ts ?? null, 'chart');
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
  function showChartsAtPoint(tp: Pick<TrackPoint, 'timestamp' | 'distanceKm'>): void {
    showChartsAtXValue(cursor.chartXForPoint(tp));
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

  return {
    bindChart,
    registerChart,
    unregisterChart,
    syncMouseMove,
    syncMouseLeave,
    syncClick,
    showChartsAtTimestamp,
    showChartsAtPoint,
    clearChartCrosshairs,
    setXMode,
  };
}
