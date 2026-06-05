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
 * 2. Chart point markers must be managed per chart.
 *    tooltip.refresh() + drawCrosshair() move the tooltip and crosshair line,
 *    but do NOT light up the per-point circle marker.  Calling
 *    point.setState('hover') does that, but the previous point for the same
 *    chart must be cleared first; otherwise hover circles can stay behind.
 *
 * 3. Map mouseout did not clear chart crosshairs.
 *    Without an explicit clearChartCrosshairs() call on MapLibre's 'mouseout'
 *    event, the chart tooltip + crosshair lingered after the cursor left the map.
 */

import {
  chartXForTrackPoint,
  useTrackCursorSync,
  type TrackCursorXMode,
  type TrackPoint,
} from '@/composables/trackCursorSync';
import type Highcharts from 'highcharts';

type ChartSyncMoveEvent = MouseEvent | PointerEvent | TouchEvent;
type PrimaryChartInputEvent = MouseEvent | PointerEvent | Touch;
type ChartPointer = {
  normalize: (event: PrimaryChartInputEvent | TouchEvent) => Highcharts.PointerEventObject;
};
type ChartWithTrackSyncMeta = Highcharts.Chart & {
  mtlTrackSyncXMode?: TrackCursorXMode;
};
type ChartWithNativeHoverPoints = Highcharts.Chart & {
  hoverPoint?: Highcharts.Point;
  hoverPoints?: Highcharts.Point[];
};
type TrackSyncPoint = Highcharts.Point & {
  canonicalPointIndex?: number;
  ts?: number;
};
type PointWithState = Highcharts.Point & {
  state?: string;
};

// Module-level registry so all chart components on the page share state.
const registeredCharts = new Set<Highcharts.Chart>();

// Track last-hovered points so we can clear their state on move/leave.
const lastHoveredPoints = new Set<Highcharts.Point>();
const lastShownPointByChart = new WeakMap<Highcharts.Chart, Highcharts.Point>();

const PASSIVE_TOUCH_LISTENER: AddEventListenerOptions = { passive: true };

const cursor = useTrackCursorSync();

export function getPrimaryChartInputEvent(e: ChartSyncMoveEvent): PrimaryChartInputEvent | null {
  return 'touches' in e ? (e.touches[0] ?? e.changedTouches[0] ?? null) : e;
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
    lastShownPointByChart.delete(chart);
  }

  function setChartXMode(chart: Highcharts.Chart, mode: TrackCursorXMode): void {
    (chart as ChartWithTrackSyncMeta).mtlTrackSyncXMode = mode;
  }

  function getChartXMode(chart: Highcharts.Chart): TrackCursorXMode {
    return (chart as ChartWithTrackSyncMeta).mtlTrackSyncXMode ?? cursor.getXMode();
  }

  function bindChart(chart: Highcharts.Chart, xMode: TrackCursorXMode = cursor.getXMode()): () => void {
    const container = chart.container;
    const onMove = (e: MouseEvent | TouchEvent) => syncMouseMove(e, chart);
    const onLeave = () => syncMouseLeave();
    const onClick = (e: MouseEvent) => syncClick(e, chart);

    setChartXMode(chart, xMode);
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
    return (chart.pointer as ChartPointer).normalize(sourceEvent);
  }

  /**
   * Called on mouse/touch move of the source chart. Syncs tooltip + crosshair on all
   * other registered charts at the same x-position, and updates the map hover marker.
   */
  function syncMouseMove(e: ChartSyncMoveEvent, sourceChart: Highcharts.Chart): void {
    let chartX: number | null = null; // elapsed ms or distance, depending on current x-mode
    let absoluteTs: number | null = null; // absolute ms timestamp — used for time mode map sync
    let canonicalPointIndex: number | null = null;

    if (sourceChart.series?.length) {
      const event = normalizeChartEvent(e, sourceChart);
      const point = event ? sourceChart.series[0].searchPoint(event, true) : null;
      if (point) {
        const syncPoint = point as TrackSyncPoint;
        chartX = point.x;
        absoluteTs = syncPoint.ts ?? null;
        canonicalPointIndex = syncPoint.canonicalPointIndex ?? null;
      }
    }

    // Bridge to map sync
    if (chartX != null) {
      showChartsAtXValue(chartX);
      cursor.setHoverByChartPoint(chartX, absoluteTs, 'chart', canonicalPointIndex);
    }
  }

  /**
   * Called on mouseleave of any chart. Clears all tooltips and crosshairs
   * and removes the hover marker (pinned stays).
   */
  function syncMouseLeave(): void {
    clearAllHoveredPoints();
    registeredCharts.forEach((chart) => {
      chart.tooltip.hide(0);
      chart.xAxis[0].hideCrosshair();
    });
    cursor.clearHover();
  }

  /**
   * Called on click of a chart. Pins the position so it persists after mouse leaves.
   */
  function syncClick(e: MouseEvent, sourceChart: Highcharts.Chart): void {
    if (!sourceChart.series?.length) return;
    const event = sourceChart.pointer.normalize(e);
    const point = sourceChart.series[0].searchPoint(event, true);
    if (!point) return;
    const syncPoint = point as TrackSyncPoint;
    cursor.setPinnedByChartPoint(point.x, syncPoint.ts ?? null, 'chart', syncPoint.canonicalPointIndex ?? null);
  }

  function collectNativeHoverPoints(points: Set<Highcharts.Point>): void {
    registeredCharts.forEach((chart) => {
      const hoverChart = chart as ChartWithNativeHoverPoints;
      if (hoverChart.hoverPoint) {
        points.add(hoverChart.hoverPoint);
      }
      hoverChart.hoverPoints?.forEach((point) => points.add(point));
    });
  }

  function collectSeriesHoverStatePoints(points: Set<Highcharts.Point>): void {
    registeredCharts.forEach((chart) => {
      chart.series?.forEach((series) => {
        series.points?.forEach((point) => {
          if ((point as PointWithState).state === 'hover') {
            points.add(point);
          }
        });
      });
    });
  }

  function clearHoveredPoints(points: Set<Highcharts.Point>): void {
    for (const p of points) {
      try {
        p.setState('');
      } catch {
        /* point may have been destroyed */
      }
    }
  }

  function clearShownPointForChart(chart: Highcharts.Chart): void {
    const previousPoint = lastShownPointByChart.get(chart);
    if (!previousPoint) return;

    lastShownPointByChart.delete(chart);
    lastHoveredPoints.delete(previousPoint);
    clearHoveredPoints(new Set([previousPoint]));
  }

  function clearAllHoveredPoints(): void {
    const pointsToClear = new Set(lastHoveredPoints);
    collectNativeHoverPoints(pointsToClear);
    collectSeriesHoverStatePoints(pointsToClear);
    lastHoveredPoints.clear();
    registeredCharts.forEach((chart) => lastShownPointByChart.delete(chart));
    clearHoveredPoints(pointsToClear);
  }

  function isChartVisible(chart: Highcharts.Chart): boolean {
    const rect = chart.container?.getBoundingClientRect?.();
    if (!rect) return true;
    if (rect.width <= 0 || rect.height <= 0) return false;

    const view = chart.container.ownerDocument?.defaultView ?? window;
    return rect.bottom > 0 && rect.right > 0 && rect.top < view.innerHeight && rect.left < view.innerWidth;
  }

  function showChartAtXValue(chart: Highcharts.Chart, xVal: number): void {
    if (!chart.series?.length) return;
    if (!isChartVisible(chart)) {
      clearShownPointForChart(chart);
      return;
    }

    const points = chart.series[0].points;
    if (!points?.length) {
      clearShownPointForChart(chart);
      return;
    }

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
    if (!point) {
      clearShownPointForChart(chart);
      return;
    }

    if (lastShownPointByChart.get(chart) === point) return;

    clearShownPointForChart(chart);
    point.setState('hover');
    lastHoveredPoints.add(point);
    lastShownPointByChart.set(chart, point);
    chart.tooltip.refresh(point);
    chart.xAxis[0].drawCrosshair(undefined, point);
  }

  /**
   * Show crosshair + tooltip + hover marker on all charts for a given x-value.
   */
  function showChartsAtXValue(xVal: number): void {
    registeredCharts.forEach((chart) => {
      showChartAtXValue(chart, xVal);
    });
  }

  /**
   * Show crosshair + tooltip on all charts for a given TrackPoint (called from map → charts).
   * Resolves the correct x-value based on the current xMode.
   */
  function showChartsAtPoint(tp: Pick<TrackPoint, 'timestamp' | 'distanceKm' | 'chartX'>): void {
    registeredCharts.forEach((chart) => {
      const xMode = getChartXMode(chart);
      showChartAtXValue(chart, chartXForTrackPoint(tp, xMode, cursor.getStartTs()));
    });
  }

  /** @deprecated use showChartsAtPoint */
  function showChartsAtTimestamp(timestamp: number): void {
    showChartsAtXValue(timestamp);
  }

  /**
   * Clear crosshairs, tooltips, and hover markers on all charts (called from map mouseout).
   */
  function clearChartCrosshairs(): void {
    clearAllHoveredPoints();
    registeredCharts.forEach((chart) => {
      lastShownPointByChart.delete(chart);
      chart.tooltip.hide(0);
      chart.xAxis[0].hideCrosshair();
    });
  }

  function clearChartInteraction(): void {
    clearChartCrosshairs();
    cursor.clearHoverBySource('chart');
    cursor.clearPinnedBySource('chart');
  }

  return {
    bindChart,
    registerChart,
    unregisterChart,
    setChartXMode,
    syncMouseMove,
    syncMouseLeave,
    syncClick,
    showChartsAtTimestamp,
    showChartsAtPoint,
    clearChartCrosshairs,
    clearChartInteraction,
    setXMode,
  };
}
