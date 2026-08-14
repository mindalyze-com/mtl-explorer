import { markRaw } from 'vue';
import { fetchTrackPointsForRenderedShape, fetchTrackCanonicalPoints } from '@/utils/ServiceHelper';
import {
  formatDateAndTimeWithSeconds,
  formatDistance,
  formatElevation,
  formatSpeed,
  formatVerticalRate,
} from '@/utils/Utils';
import { bearing } from '@/components/map/mapGeometry';
import {
  createTrackPointPopup,
  updateTrackPointPopupContent,
  type TrackPointPopupRow,
} from '@/components/map/trackPointPopup';
import {
  nearestByNumericValue,
  numericRangeForItems,
  projectClickToTrackLine,
  valueAtFraction,
} from '@/components/map/trackLineHitTest';
import { OVERVIEW_PRECISION } from '@/utils/tracks/trackConstants';
import type {
  Coordinates,
  MapCenter,
  MapControllerMethodDefinitions,
  MapControllerRuntime,
  TrackFeature,
  TrackPointLayerMethods,
} from './mapControllerRuntime';
import type { GpsTrackDataPoint } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import { VIZ_BLUE_COLOR, VIZ_LIGHT_COLOR } from '@/utils/visualizationColors';

const TRACK_POINTS_MIN_ZOOM = 16;
const TRACK_POINT_ARROW_ICON_SIZE = 24;
const TRACK_POINT_ARROW_COLOR = VIZ_BLUE_COLOR;
const TRACK_POINT_ARROW_BACKGROUND_COLOR = VIZ_LIGHT_COLOR;
const DEFAULT_DEVICE_PIXEL_RATIO = 1;
const TRACK_LINE_CLICK_TOLERANCE_PX = 12;
const TRACK_LINE_CLICK_TOLERANCE_METERS = 120;

export function buildArchiveTrackPointPopupRows(
  lngLat: MapCenter | Coordinates,
  point: GpsTrackDataPoint,
  canonical?: GpsTrackDataPoint | null
): TrackPointPopupRow[] {
  const fmt = (v: unknown, decimals = 1) => {
    const number = finiteNumber(v);
    return number == null ? '—' : number.toFixed(decimals);
  };
  const fmtWithUnit = (v: unknown, unit: string, decimals = 1) => {
    const value = fmt(v, decimals);
    return value === '—' ? value : `${value} ${unit}`;
  };
  const fmtMeasurement = (v: unknown, formatter: (value: number) => string | undefined) => {
    const number = finiteNumber(v);
    return number == null ? '—' : (formatter(number) ?? '—');
  };
  const fmtTime = (v: unknown) => {
    if (!v) return '—';
    const d = new Date(v as string | number | Date);
    return formatDateAndTimeWithSeconds(d);
  };
  const fmtDuration = (secs: number | null | undefined) => {
    if (secs == null) return '—';
    const h = Math.floor(secs / 3600);
    const m = Math.floor((secs % 3600) / 60);
    const s = Math.round(secs % 60);
    return h > 0 ? `${h}h ${m}m ${s}s` : m > 0 ? `${m}m ${s}s` : `${s}s`;
  };

  const fallbackLngLat = toLngLatParts(lngLat);
  const lat = point.pointLongLat?.coordinates?.[1] ?? fallbackLngLat.lat;
  const lng = point.pointLongLat?.coordinates?.[0] ?? fallbackLngLat.lng;
  const metrics = (canonical ?? {}) as Partial<GpsTrackDataPoint> & Record<string, number | string | null | undefined>;
  const rows: TrackPointPopupRow[] = [
    { label: 'Point', value: `${(point.pointIndex ?? 0) + 1} / ${(point.pointIndexMax ?? 0) + 1}` },
    { label: 'Time', value: fmtTime(point.pointTimestamp) },
    { label: 'Lat / Lng', value: `${fmt(lat, 6)} / ${fmt(lng, 6)}` },
    { label: 'Altitude', value: fmtMeasurement(point.pointAltitude, (value) => formatElevation(value, 1)) },
    { label: 'Speed', value: fmtMeasurement(metrics.speedInKmhMovingWindow, (value) => formatSpeed(value, 1)) },
    {
      label: 'Dist from start',
      value: fmtMeasurement(metrics.distanceInMeterSinceStart, (value) => formatDistance(value, 2)),
    },
    {
      label: 'Dist prev point',
      value: fmtMeasurement(metrics.distanceInMeterBetweenPoints, (value) => formatDistance(value, 1)),
    },
    { label: 'Time prev point', value: fmtWithUnit(metrics.durationBetweenPointsInSec, 's', 1) },
    { label: 'Duration', value: fmtDuration(metrics.durationSinceStart as number | null | undefined) },
    { label: 'Ascent', value: fmtMeasurement(metrics.ascentInMeterSinceStart, (value) => formatElevation(value, 0)) },
    { label: 'Descent', value: fmtMeasurement(metrics.descentInMeterSinceStart, (value) => formatElevation(value, 0)) },
    { label: 'Slope', value: fmtWithUnit(metrics.slopePercentageInMovingWindow, '%', 1) },
    {
      label: 'Elev gain/h',
      value: fmtMeasurement(metrics.elevationGainPerHourMovingWindow, (value) => formatVerticalRate(value, 0)),
    },
    {
      label: 'Elev loss/h',
      value: fmtMeasurement(metrics.elevationLossPerHourMovingWindow, (value) => formatVerticalRate(value, 0)),
    },
  ];

  if (metrics.energyTotalWh != null) {
    rows.push({ label: 'Est. energy (seg)', value: `${fmt(metrics.energyTotalWh, 2)} Wh` });
    rows.push({ label: 'Est. energy (cum)', value: `${fmt(metrics.energyCumulativeWh, 1)} Wh` });
    rows.push({ label: 'Est. power', value: `${fmt(metrics.powerWatts, 0)} W` });
  }

  return rows;
}

export function createArrowImage(
  size = TRACK_POINT_ARROW_ICON_SIZE,
  color = TRACK_POINT_ARROW_COLOR,
  bgColor = TRACK_POINT_ARROW_BACKGROUND_COLOR
) {
  const canvas = document.createElement('canvas');
  const ratio =
    Number.isFinite(window.devicePixelRatio) && window.devicePixelRatio > 0
      ? window.devicePixelRatio
      : DEFAULT_DEVICE_PIXEL_RATIO;
  const px = Math.max(1, Math.round(size * ratio));
  const pixelRatio = px / size;
  canvas.width = px;
  canvas.height = px;
  const ctx = canvas.getContext('2d')!;
  ctx.scale(pixelRatio, pixelRatio);

  const cx = size / 2;
  const cy = size / 2;
  const r = size / 2 - 1;

  ctx.beginPath();
  ctx.arc(cx, cy, r, 0, Math.PI * 2);
  ctx.fillStyle = bgColor;
  ctx.fill();
  ctx.strokeStyle = color;
  ctx.lineWidth = 1.5;
  ctx.stroke();

  const arrowH = size * 0.38;
  const arrowW = size * 0.36;
  const top = cy - arrowH * 0.55;

  ctx.beginPath();
  ctx.moveTo(cx, top);
  ctx.lineTo(cx + arrowW / 2, top + arrowH);
  ctx.lineTo(cx, top + arrowH * 0.6);
  ctx.lineTo(cx - arrowW / 2, top + arrowH);
  ctx.closePath();
  ctx.fillStyle = color;
  ctx.fill();

  const imgData = ctx.getImageData(0, 0, px, px);
  return { width: px, height: px, data: imgData.data, pixelRatio };
}

export function useTrackPointLayer(
  _deps: Record<string, never> = {}
): MapControllerMethodDefinitions<TrackPointLayerMethods> {
  const methods: MapControllerMethodDefinitions<TrackPointLayerMethods> = {
    closeTrackPointPopup() {
      this.trackPointsPopup?.remove();
      this.trackPointsPopup = null;
      this.trackPointsPopupContext = null;
    },

    detachTrackPointLayerHandlers() {
      if (!this.overlayMap || !this.trackPointLayerHandlers) return;
      const handlers = this.trackPointLayerHandlers;
      this.overlayMap.off('click', 'track-points-layer', handlers.click);
      this.overlayMap.off('mouseenter', 'track-points-layer', handlers.mouseenter);
      this.overlayMap.off('mouseleave', 'track-points-layer', handlers.mouseleave);
      this.trackPointLayerHandlers = null;
    },

    attachTrackPointLayerHandlers() {
      const overlayMap = this.overlayMap;
      if (!overlayMap) return;
      this.detachTrackPointLayerHandlers();
      const handlers: NonNullable<MapControllerRuntime['trackPointLayerHandlers']> = {
        click: (e) => {
          if (!e.features || e.features.length === 0) return;
          e.originalEvent.stopPropagation();
          const f = e.features[0];
          const trackId = Number(f.properties?.trackId);
          const pointIndex = Number(f.properties?.pointIndex);
          if (!Number.isFinite(trackId) || !Number.isFinite(pointIndex)) return;
          this.showTrackPointPopup(e.lngLat, trackId, pointIndex);
        },
        mouseenter: () => {
          overlayMap.getCanvas().style.cursor = 'pointer';
        },
        mouseleave: () => {
          overlayMap.getCanvas().style.cursor = '';
        },
      };
      overlayMap.on('click', 'track-points-layer', handlers.click);
      overlayMap.on('mouseenter', 'track-points-layer', handlers.mouseenter);
      overlayMap.on('mouseleave', 'track-points-layer', handlers.mouseleave);
      this.trackPointLayerHandlers = handlers;
    },

    // ─── Individual GPS track points ─────────────────────────────────

    /** Rebuild the track-points GeoJSON source from visible track coordinates. */
    updateTrackPointsSource() {
      // Extract reactive refs to local vars once to avoid repeated Vue proxy access
      const overlayMap = this.overlayMap;
      const geojson = this.geojson;
      if (!overlayMap || !geojson) return;
      const zoom = overlayMap.getZoom();

      // Clear if disabled or below minimum zoom
      if (!this.trackPointsVisible || zoom < TRACK_POINTS_MIN_ZOOM) {
        const src = overlayMap.getSource('track-points');
        if (src)
          (src as unknown as { setData: (data: unknown) => void }).setData({
            type: 'FeatureCollection',
            features: [],
          });
        return;
      }
      const trackPrecisions = this.trackPrecisions;
      const gpsTracksById = this.gpsTracksById;

      // Build viewport bounds for filtering
      const mapBounds = overlayMap.getBounds();
      const sw = mapBounds.getSouthWest();
      const ne = mapBounds.getNorthEast();
      const latPad = (ne.lat - sw.lat) * 0.1;
      const lngPad = (ne.lng - sw.lng) * 0.1;
      const minLat = sw.lat - latPad,
        maxLat = ne.lat + latPad;
      const minLng = sw.lng - lngPad,
        maxLng = ne.lng + lngPad;
      const features = geojson.features;
      const pointFeatures: Array<Record<string, unknown>> = [];
      for (let f = 0; f < features.length; f++) {
        const feature = features[f];
        const coords = lineCoordinatesFromFeature(feature);
        if (!coords.length) continue;
        const trackId = Number(feature.properties?.id);
        if (!Number.isFinite(trackId)) continue;

        // Include tracks at 10m or better precision (arrows are useful at any detail level)
        const precision = trackPrecisions.get(trackId) ?? OVERVIEW_PRECISION;
        if (precision > 10) continue;

        // Track-level bbox check: skip tracks entirely outside the viewport
        const track = gpsTracksById.get(trackId);
        if (
          track?.bboxMinLat != null &&
          track.bboxMaxLat != null &&
          track.bboxMinLng != null &&
          track.bboxMaxLng != null
        ) {
          if (
            track.bboxMaxLat < minLat ||
            track.bboxMinLat > maxLat ||
            track.bboxMaxLng < minLng ||
            track.bboxMinLng > maxLng
          )
            continue;
        }
        for (let i = 0; i < coords.length; i++) {
          const [lng, lat] = coords[i];
          // Viewport filter
          if (lat < minLat || lat > maxLat || lng < minLng || lng > maxLng) continue;

          // Compute bearing from this point toward the next (last point uses incoming bearing)
          let deg = 0;
          if (i < coords.length - 1) {
            deg = bearing(lng, lat, coords[i + 1][0], coords[i + 1][1]);
          } else if (i > 0) {
            deg = bearing(coords[i - 1][0], coords[i - 1][1], lng, lat);
          }
          pointFeatures.push({
            type: 'Feature',
            geometry: {
              type: 'Point',
              coordinates: [lng, lat],
            },
            properties: {
              trackId,
              pointIndex: i,
              bearing: Math.round(deg),
            },
          });
        }
      }
      const src = overlayMap.getSource('track-points');
      if (src)
        (src as unknown as { setData: (data: unknown) => void }).setData({
          type: 'FeatureCollection',
          features: pointFeatures,
        });
    },

    /** Show a popup with full data for a specific track point. */
    async showTrackPointPopup(lngLat, trackId, pointIndex) {
      // Close any existing popup
      this.closeTrackPointPopup();

      // The track-points layer is emitted by updateTrackPointsSource() from
      // the SIMPLIFIED_SHAPE LineString currently loaded for this track, and
      // `pointIndex` is the array index into that coordinate list. Under the
      // canonical-metric-LOD architecture the simplified variant carries
      // GEOMETRY ONLY (lat/lng/alt/timestamp/canonicalPointIndex); all
      // derived per-point metrics (speed/slope/energy/…) live on the
      // canonical RAW_OUTLIER_CLEANED stream. So we:
      //   1. resolve the clicked SIMPLIFIED_SHAPE vertex by pointIndex
      //      (geometry + canonicalPointIndex back-pointer)
      //   2. look up the matching canonical point by canonicalPointIndex
      //      and read metric fields off that row.
      const precision = this.trackPrecisions.get(trackId) ?? OVERVIEW_PRECISION;
      const popupData = await loadTrackPointPopupData(this, trackId, precision);
      if (!popupData) return;
      const { details, canonicalPoints } = popupData;
      const point = details.find((p) => p.pointIndex === pointIndex);
      if (!point) {
        console.warn(`Point index ${pointIndex} not found in details for track ${trackId} at precision ${precision}m`);
        return;
      }

      // Resolve the canonical metric row via the SIMPLIFIED_SHAPE back-pointer.
      // Fallback: nearest-by-pointIndex if back-pointer missing (very early
      // vertices or unmatched timestamps).
      const canonical = canonicalPointForRenderedPoint(point, canonicalPoints);

      this.renderTrackPointPopup(lngLat, trackId, point, canonical);
    },

    async showTrackLinePointPopup(e, trackId) {
      const overlayMap = this.overlayMap;
      if (!overlayMap || !this.trackPointsVisible || overlayMap.getZoom() < TRACK_POINTS_MIN_ZOOM) {
        return false;
      }

      const feature = this.gpsTrackIdToFeature?.get?.(Number(trackId));
      const coordinates = lineCoordinatesFromFeature(feature);
      if (coordinates.length < 2) return false;

      const projection = projectClickToTrackLine({
        map: overlayMap,
        clickPoint: e.point,
        lngLat: e.lngLat,
        coordinates,
        pixelTolerance: TRACK_LINE_CLICK_TOLERANCE_PX,
        meterTolerance: TRACK_LINE_CLICK_TOLERANCE_METERS,
      });
      if (!projection) return false;

      this.closeTrackPointPopup();

      const precision = this.trackPrecisions.get(trackId) ?? OVERVIEW_PRECISION;
      const popupData = await loadTrackPointPopupData(this, trackId, precision);
      if (!popupData) return false;

      const point = projectedPopupPoint(
        popupData.details,
        popupData.canonicalPoints,
        projection.anchor,
        projection.fraction
      );
      if (!point) return false;

      this.renderTrackPointPopup(
        projection.anchor,
        trackId,
        point,
        canonicalPointForRenderedPoint(point, popupData.canonicalPoints)
      );
      return true;
    },

    renderTrackPointPopup(lngLat, trackId, point, canonical) {
      if (!this.overlayMap) return;
      this.trackPointsPopupContext = markRaw({ lngLat, trackId, point, canonical });
      this.trackPointsPopup = markRaw(
        createTrackPointPopup({
          map: this.overlayMap,
          lngLat,
          title: `Track #${trackId}`,
          rows: buildArchiveTrackPointPopupRows(lngLat, point, canonical),
        })
      );
    },

    refreshTrackPointPopupMeasurementLabels() {
      const context = this.trackPointsPopupContext;
      if (!this.trackPointsPopup || !context) return;
      updateTrackPointPopupContent(
        this.trackPointsPopup,
        `Track #${context.trackId}`,
        buildArchiveTrackPointPopupRows(context.lngLat, context.point, context.canonical)
      );
    },
  };
  return methods;
}

function finiteNumber(value: unknown): number | null {
  if (value == null || value === '') return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

async function loadTrackPointPopupData(
  controller: MapControllerRuntime,
  trackId: number,
  precision: number
): Promise<{ details: GpsTrackDataPoint[]; canonicalPoints: GpsTrackDataPoint[] } | null> {
  const cacheKey = `${trackId}|${precision}`;

  let details = controller.trackPointsDetailsCache.get(cacheKey);
  if (!details) {
    try {
      details = await fetchTrackPointsForRenderedShape(trackId, precision);
      controller.trackPointsDetailsCache.set(cacheKey, details);
    } catch (e) {
      console.warn('Failed to fetch simplified point details for track', trackId, e);
      return null;
    }
  }

  let canonicalPoints = controller.trackPointsCanonicalCache.get(trackId);
  if (!canonicalPoints) {
    try {
      canonicalPoints = await fetchTrackCanonicalPoints(trackId);
      controller.trackPointsCanonicalCache.set(trackId, canonicalPoints);
    } catch (e) {
      console.warn('Failed to fetch canonical points for track', trackId, e);
      canonicalPoints = [];
    }
  }

  return { details, canonicalPoints };
}

function canonicalPointForRenderedPoint(
  point: GpsTrackDataPoint,
  canonicalPoints: GpsTrackDataPoint[]
): GpsTrackDataPoint | null {
  const canonicalIndex = point.canonicalPointIndex;
  if (canonicalIndex != null) {
    const exact = canonicalPoints.find((p) => p.pointIndex === canonicalIndex);
    if (exact) return exact;
    return nearestByNumericValue(canonicalPoints, canonicalIndex, (p) => p.pointIndex);
  }
  return point.pointIndex != null
    ? nearestByNumericValue(canonicalPoints, point.pointIndex, (p) => p.pointIndex)
    : null;
}

function projectedPopupPoint(
  details: GpsTrackDataPoint[],
  canonicalPoints: GpsTrackDataPoint[],
  anchor: Coordinates,
  fraction: number
): GpsTrackDataPoint | null {
  const renderedPoint = renderedPointForTrackFraction(details, fraction);
  const canonicalPoint = canonicalPointForTrackFraction(details, canonicalPoints, fraction);
  const basePoint = renderedPoint ?? canonicalPoint;
  if (!basePoint) return null;

  return {
    ...basePoint,
    pointLongLat: {
      type: 'Point',
      coordinates: anchor,
    },
    pointTimestamp: canonicalPoint?.pointTimestamp ?? basePoint.pointTimestamp,
    pointAltitude: canonicalPoint?.pointAltitude ?? basePoint.pointAltitude,
    distanceInMeterSinceStart: canonicalPoint?.distanceInMeterSinceStart ?? basePoint.distanceInMeterSinceStart,
    pointIndex: canonicalPoint?.pointIndex ?? basePoint.pointIndex,
    pointIndexMax: canonicalPoint?.pointIndexMax ?? basePoint.pointIndexMax,
    canonicalPointIndex: canonicalPoint?.pointIndex ?? basePoint.canonicalPointIndex,
  } as GpsTrackDataPoint;
}

function renderedPointForTrackFraction(details: GpsTrackDataPoint[], fraction: number): GpsTrackDataPoint | null {
  const distanceRange = numericRangeForItems(details, (point) => point.distanceInMeterSinceStart);
  if (distanceRange && distanceRange.max > distanceRange.min) {
    return nearestByNumericValue(
      details,
      valueAtFraction(distanceRange, fraction),
      (point) => point.distanceInMeterSinceStart
    );
  }

  const canonicalRange = numericRangeForItems(details, (point) => point.canonicalPointIndex);
  if (canonicalRange && canonicalRange.max > canonicalRange.min) {
    return nearestByNumericValue(
      details,
      valueAtFraction(canonicalRange, fraction),
      (point) => point.canonicalPointIndex
    );
  }

  const pointIndexRange = numericRangeForItems(details, (point) => point.pointIndex);
  return pointIndexRange
    ? nearestByNumericValue(details, valueAtFraction(pointIndexRange, fraction), (point) => point.pointIndex)
    : (details[0] ?? null);
}

function canonicalPointForTrackFraction(
  details: GpsTrackDataPoint[],
  canonicalPoints: GpsTrackDataPoint[],
  fraction: number
): GpsTrackDataPoint | null {
  if (!canonicalPoints?.length) return null;

  const simplifiedCanonicalRange = numericRangeForItems(details, (point) => point.canonicalPointIndex);
  if (simplifiedCanonicalRange && simplifiedCanonicalRange.max > simplifiedCanonicalRange.min) {
    return nearestByNumericValue(
      canonicalPoints,
      valueAtFraction(simplifiedCanonicalRange, fraction),
      (point) => point.pointIndex
    );
  }

  const canonicalRange = numericRangeForItems(canonicalPoints, (point) => point.pointIndex);
  return canonicalRange
    ? nearestByNumericValue(canonicalPoints, valueAtFraction(canonicalRange, fraction), (point) => point.pointIndex)
    : null;
}

function lineCoordinatesFromFeature(feature: TrackFeature | null | undefined): Coordinates[] {
  const geometry = feature?.geometry as { type?: string; coordinates?: unknown[] } | undefined;
  if (!geometry || geometry.type !== 'LineString') return [];
  return (geometry.coordinates ?? [])
    .map((coordinate: unknown) => {
      const tuple = coordinate as Array<unknown> | undefined;
      if (!tuple) return null;
      const lng = Number(tuple[0]);
      const lat = Number(tuple[1]);
      return Number.isFinite(lng) && Number.isFinite(lat) ? ([lng, lat] as Coordinates) : null;
    })
    .filter((coordinate): coordinate is Coordinates => coordinate != null);
}

function toLngLatParts(lngLat: MapCenter | Coordinates): MapCenter {
  return Array.isArray(lngLat) ? { lng: lngLat[0], lat: lngLat[1] } : lngLat;
}
