import * as maplibregl from 'maplibre-gl';
import { formatRadius } from '@/utils/Utils';
import { createGeoJsonCircle } from '@/utils/geoJson';
import { VIZ_ACCENT_COLOR } from '@/utils/visualizationColors';

export type GeoShapeType = 'circle' | 'rectangle' | 'polygon';

export interface DrawnCircle {
  lat: number;
  lng: number;
  radiusM: number;
}

export interface DrawnRectangle {
  minLat: number;
  maxLat: number;
  minLng: number;
  maxLng: number;
}

export interface DrawnPolygon {
  coordinates: [number, number][]; // [lng, lat]
}

export type DrawnShape = DrawnCircle | DrawnRectangle | DrawnPolygon;

const SHAPE_COLOR = VIZ_ACCENT_COLOR;
const SHAPE_FILL_OPACITY = 0.15;
const SHAPE_LINE_WIDTH = 2;
const SHAPE_LINE_OPACITY = 0.7;
const CIRCLE_PREVIEW_RADIUS_M = 100;
const CIRCLE_PREVIEW_LABEL_SOURCE_ID = 'geo-preview-circle-label';

function measurementLabelLayer(sourceId: string): maplibregl.SymbolLayerSpecification {
  return {
    id: sourceId,
    type: 'symbol',
    source: sourceId,
    layout: {
      'text-field': ['get', 'label'],
      'text-size': 13,
      'text-font': ['Noto Sans Medium'],
      'text-anchor': 'center',
      'text-allow-overlap': true,
    },
    paint: {
      'text-color': SHAPE_COLOR,
      'text-halo-color': 'rgba(255,255,255,0.9)',
      'text-halo-width': 2,
    },
  };
}

type MeasurementLabel = {
  lng: number;
  lat: number;
  text: () => string;
};

/**
 * Manages drawing geo shapes (circle, rectangle, polygon) on a MapLibre map.
 * Follows the same source/layer pattern as MeasureBetweenPoints and HeatmapOverlay.
 */
export class GeoDrawingOverlay {
  private readonly map: maplibregl.Map;
  private sourceIds: string[] = [];
  private layerIds: string[] = [];
  private shapeCounter = 0;
  private measurementLabels = new Map<string, MeasurementLabel>();

  // Drawing state
  private drawingMode: GeoShapeType | null = null;
  private drawingCallback: ((shape: DrawnShape) => void) | null = null;
  private onStateChange: (() => void) | null = null;

  // Circle drawing state
  private circleCenter: maplibregl.LngLat | null = null;
  private circleDragHandler: ((e: maplibregl.MapMouseEvent) => void) | null = null;
  private circleClickHandler: ((e: maplibregl.MapMouseEvent) => void) | null = null;
  private circlePreviewRadiusM: number | null = null;

  // Rectangle drawing state
  private rectFirstCorner: maplibregl.LngLat | null = null;
  private rectMoveHandler: ((e: maplibregl.MapMouseEvent) => void) | null = null;
  private rectClickHandler: ((e: maplibregl.MapMouseEvent) => void) | null = null;
  private rectPreviewSourceId: string | null = null;

  // Polygon drawing state
  private polygonPoints: maplibregl.LngLat[] = [];
  private polygonClickHandler: ((e: maplibregl.MapMouseEvent) => void) | null = null;
  private polygonMoveHandler: ((e: maplibregl.MapMouseEvent) => void) | null = null;
  private polygonDblClickHandler: ((e: maplibregl.MapMouseEvent) => void) | null = null;
  private polygonPreviewSourceId: string | null = null;

  constructor(map: maplibregl.Map) {
    this.map = map;
  }

  isDrawing(): boolean {
    return this.drawingMode !== null;
  }

  /**
   * Start drawing a shape. The callback fires when the shape is finalized.
   * @param onStateChange optional callback fired whenever the internal drawing state changes (point added/removed)
   */
  startDrawing(type: GeoShapeType, callback: (shape: DrawnShape) => void, onStateChange?: () => void): void {
    this.cancelDrawing();
    this.drawingMode = type;
    this.drawingCallback = callback;
    this.onStateChange = onStateChange ?? null;
    this.map.getCanvas().style.cursor = 'crosshair';

    switch (type) {
      case 'circle':
        this.startCircleDrawing();
        break;
      case 'rectangle':
        this.startRectangleDrawing();
        break;
      case 'polygon':
        this.startPolygonDrawing();
        break;
    }
  }

  /** Number of points placed so far (polygon) or steps done (circle/rect). */
  getPointCount(): number {
    if (this.drawingMode === 'polygon') return this.polygonPoints.length;
    if (this.drawingMode === 'circle') return this.circleCenter ? 1 : 0;
    if (this.drawingMode === 'rectangle') return this.rectFirstCorner ? 1 : 0;
    return 0;
  }

  getDrawingMode(): GeoShapeType | null {
    return this.drawingMode;
  }

  /** Whether the user can undo the last placed point. */
  canUndo(): boolean {
    if (this.drawingMode === 'polygon') return this.polygonPoints.length > 0;
    if (this.drawingMode === 'circle') return this.circleCenter != null;
    if (this.drawingMode === 'rectangle') return this.rectFirstCorner != null;
    return false;
  }

  /** Whether the current shape can be finalized. */
  canFinish(): boolean {
    if (this.drawingMode === 'polygon') return this.polygonPoints.length >= 3;
    // circle and rectangle auto-finish on second click
    return false;
  }

  /** Undo the last placed point / reset the first click. */
  undoLastPoint(): void {
    if (this.drawingMode === 'polygon' && this.polygonPoints.length > 0) {
      this.polygonPoints.pop();
      if (this.polygonPreviewSourceId) this.updatePolygonPreview(this.polygonPreviewSourceId, null);
      this.onStateChange?.();
    } else if (this.drawingMode === 'circle' && this.circleCenter) {
      this.circleCenter = null;
      if (this.circleDragHandler) {
        this.map.off('mousemove', this.circleDragHandler);
        this.circleDragHandler = null;
      }
      this.removePreviewLayers();
      this.onStateChange?.();
    } else if (this.drawingMode === 'rectangle' && this.rectFirstCorner) {
      this.rectFirstCorner = null;
      if (this.rectMoveHandler) {
        this.map.off('mousemove', this.rectMoveHandler);
        this.rectMoveHandler = null;
      }
      this.removePreviewLayers();
      this.onStateChange?.();
    }
  }

  /** Finish polygon drawing (programmatic — e.g. from a "Finish" button). */
  finishPolygon(): void {
    this.finishCurrentPolygon();
  }

  /**
   * Cancel any in-progress drawing without finalizing.
   */
  cancelDrawing(): void {
    this.resetDrawingSession();
  }

  private resetDrawingSession(): void {
    this.removeDrawingHandlers();
    this.removePreviewLayers();
    this.drawingMode = null;
    this.drawingCallback = null;
    this.onStateChange = null;
    try {
      this.map.getCanvas().style.cursor = '';
    } catch {
      /* map may be destroyed */
    }
  }

  /**
   * Render a finalized circle shape on the map.
   */
  renderCircle(circle: DrawnCircle, color: string = SHAPE_COLOR, name?: string): string {
    const id = `geo-shape-${this.shapeCounter++}`;
    const geoJson = createGeoJsonCircle(circle.lng, circle.lat, circle.radiusM);

    this.addFillAndOutline(id, geoJson, color);
    this.addCenterLabel(id, circle.lng, circle.lat, () => this.measurementLabel(name, formatRadius(circle.radiusM)));
    return id;
  }

  /**
   * Render a finalized rectangle shape on the map.
   */
  renderRectangle(rect: DrawnRectangle, color: string = SHAPE_COLOR, name?: string): string {
    const id = `geo-shape-${this.shapeCounter++}`;
    const geoJson = this.createGeoJsonRectangle(rect);

    this.addFillAndOutline(id, geoJson, color);
    const centerLng = (rect.minLng + rect.maxLng) / 2;
    const centerLat = (rect.minLat + rect.maxLat) / 2;
    const widthM = this.distanceInMeters(
      new maplibregl.LngLat(rect.minLng, centerLat),
      new maplibregl.LngLat(rect.maxLng, centerLat)
    );
    const heightM = this.distanceInMeters(
      new maplibregl.LngLat(centerLng, rect.minLat),
      new maplibregl.LngLat(centerLng, rect.maxLat)
    );
    this.addCenterLabel(id, centerLng, centerLat, () =>
      this.measurementLabel(name, `${formatRadius(widthM)} × ${formatRadius(heightM)}`)
    );
    return id;
  }

  /**
   * Render a finalized polygon shape on the map.
   */
  renderPolygon(polygon: DrawnPolygon, color: string = SHAPE_COLOR, name?: string): string {
    const id = `geo-shape-${this.shapeCounter++}`;
    const coords = [...polygon.coordinates];
    // Close polygon
    if (
      coords.length > 0 &&
      (coords[0][0] !== coords[coords.length - 1][0] || coords[0][1] !== coords[coords.length - 1][1])
    ) {
      coords.push(coords[0]);
    }
    const geoJson: GeoJSON.FeatureCollection = {
      type: 'FeatureCollection',
      features: [
        {
          type: 'Feature',
          geometry: { type: 'Polygon', coordinates: [coords] },
          properties: {},
        },
      ],
    };

    this.addFillAndOutline(id, geoJson, color);
    // Compute centroid and perimeter for label
    const rawCoords = polygon.coordinates;
    if (rawCoords.length >= 3) {
      const centroidLng = rawCoords.reduce((s, c) => s + c[0], 0) / rawCoords.length;
      const centroidLat = rawCoords.reduce((s, c) => s + c[1], 0) / rawCoords.length;
      let perimeterM = 0;
      for (let i = 0; i < rawCoords.length; i++) {
        const a = new maplibregl.LngLat(rawCoords[i][0], rawCoords[i][1]);
        const b = new maplibregl.LngLat(
          rawCoords[(i + 1) % rawCoords.length][0],
          rawCoords[(i + 1) % rawCoords.length][1]
        );
        perimeterM += this.distanceInMeters(a, b);
      }
      this.addCenterLabel(id, centroidLng, centroidLat, () =>
        this.measurementLabel(name, `${rawCoords.length} pts, ${formatRadius(perimeterM)} perimeter`)
      );
    }
    return id;
  }

  /**
   * Remove a specific rendered shape by its id.
   */
  removeShape(shapeId: string): void {
    this.measurementLabels.delete(shapeId);
    const layerSuffixes = ['-fill', '-outline', '-label'];
    for (const suffix of layerSuffixes) {
      const layerId = shapeId + suffix;
      try {
        if (this.map.getLayer(layerId)) {
          this.map.removeLayer(layerId);
          this.layerIds = this.layerIds.filter((id) => id !== layerId);
        }
      } catch {
        /* map may be destroyed */
      }
    }
    const sourceSuffixes = ['', '-label'];
    for (const suffix of sourceSuffixes) {
      const sourceId = shapeId + suffix;
      try {
        if (this.map.getSource(sourceId)) {
          this.map.removeSource(sourceId);
          this.sourceIds = this.sourceIds.filter((id) => id !== sourceId);
        }
      } catch {
        /* map may be destroyed */
      }
    }
  }

  /**
   * Remove all rendered shapes from the map.
   */
  clearAll(): void {
    for (const layerId of [...this.layerIds]) {
      try {
        if (this.map.getLayer(layerId)) this.map.removeLayer(layerId);
      } catch {
        /* map may be destroyed */
      }
    }
    for (const sourceId of [...this.sourceIds]) {
      try {
        if (this.map.getSource(sourceId)) this.map.removeSource(sourceId);
      } catch {
        /* map may be destroyed */
      }
    }
    this.layerIds = [];
    this.sourceIds = [];
    this.measurementLabels.clear();
    this.shapeCounter = 0;
  }

  refreshMeasurementLabels(): void {
    for (const [baseId, label] of this.measurementLabels) {
      this.updateLabelSource(baseId + '-label', label.lng, label.lat, label.text());
    }
    if (this.circleCenter && this.circlePreviewRadiusM != null) {
      this.updateLabelSource(
        CIRCLE_PREVIEW_LABEL_SOURCE_ID,
        this.circleCenter.lng,
        this.circleCenter.lat,
        formatRadius(this.circlePreviewRadiusM)
      );
    }
  }

  destroy(): void {
    this.cancelDrawing();
    this.clearAll();
  }

  // ── Circle Drawing ──

  private startCircleDrawing(): void {
    this.circleCenter = null;
    this.circlePreviewRadiusM = null;

    this.circleClickHandler = (e: maplibregl.MapMouseEvent) => {
      if (!this.circleCenter) {
        // First click: set center
        this.circleCenter = e.lngLat;
        this.circlePreviewRadiusM = CIRCLE_PREVIEW_RADIUS_M;
        // Show a preview circle at the default radius initially.
        const previewId = `geo-preview-circle`;
        this.rectPreviewSourceId = previewId; // reuse field for cleanup
        const geoJson = createGeoJsonCircle(e.lngLat.lng, e.lngLat.lat, CIRCLE_PREVIEW_RADIUS_M);
        this.addAreaPreviewLayers(previewId, false, geoJson);

        // Add a live radius label at center
        const labelId = CIRCLE_PREVIEW_LABEL_SOURCE_ID;
        this.map.addSource(labelId, {
          type: 'geojson',
          data: {
            type: 'FeatureCollection',
            features: [
              {
                type: 'Feature',
                geometry: { type: 'Point', coordinates: [e.lngLat.lng, e.lngLat.lat] },
                properties: { label: formatRadius(CIRCLE_PREVIEW_RADIUS_M) },
              },
            ],
          },
        });
        this.map.addLayer(measurementLabelLayer(labelId));

        // Listen for mouse move to update radius + label
        this.circleDragHandler = (moveEvent: maplibregl.MapMouseEvent) => {
          const radiusM = this.distanceInMeters(this.circleCenter!, moveEvent.lngLat);
          this.circlePreviewRadiusM = radiusM;
          const source = this.map.getSource(previewId) as maplibregl.GeoJSONSource;
          if (source) {
            source.setData(createGeoJsonCircle(this.circleCenter!.lng, this.circleCenter!.lat, radiusM));
          }
          const labelSource = this.map.getSource(labelId) as maplibregl.GeoJSONSource;
          if (labelSource) {
            labelSource.setData({
              type: 'FeatureCollection',
              features: [
                {
                  type: 'Feature',
                  geometry: { type: 'Point', coordinates: [this.circleCenter!.lng, this.circleCenter!.lat] },
                  properties: { label: formatRadius(radiusM) },
                },
              ],
            });
          }
        };
        this.map.on('mousemove', this.circleDragHandler);
        this.onStateChange?.();
      } else {
        // Second click: finalize
        const radiusM = this.distanceInMeters(this.circleCenter, e.lngLat);
        const shape: DrawnCircle = {
          lat: this.circleCenter.lat,
          lng: this.circleCenter.lng,
          radiusM: Math.round(radiusM),
        };
        this.finishDrawing(shape);
      }
    };
    this.map.on('click', this.circleClickHandler);
  }

  // ── Rectangle Drawing ──

  private addAreaPreviewLayers(
    previewId: string,
    dashedOutline = false,
    data: GeoJSON.FeatureCollection = { type: 'FeatureCollection', features: [] }
  ): void {
    this.map.addSource(previewId, { type: 'geojson', data });
    this.map.addLayer({
      id: previewId + '-fill',
      type: 'fill',
      source: previewId,
      paint: { 'fill-color': SHAPE_COLOR, 'fill-opacity': SHAPE_FILL_OPACITY },
    });
    this.map.addLayer({
      id: previewId + '-outline',
      type: 'line',
      source: previewId,
      paint: {
        'line-color': SHAPE_COLOR,
        'line-width': SHAPE_LINE_WIDTH,
        'line-opacity': SHAPE_LINE_OPACITY,
        ...(dashedOutline ? { 'line-dasharray': [2, 2] } : {}),
      },
    });
  }

  private startRectangleDrawing(): void {
    this.rectFirstCorner = null;

    this.rectClickHandler = (e: maplibregl.MapMouseEvent) => {
      if (!this.rectFirstCorner) {
        // First click: set corner
        this.rectFirstCorner = e.lngLat;
        const previewId = `geo-preview-rect`;
        this.rectPreviewSourceId = previewId;

        this.addAreaPreviewLayers(previewId);

        this.rectMoveHandler = (moveEvent: maplibregl.MapMouseEvent) => {
          const rect = this.cornersToRect(this.rectFirstCorner!, moveEvent.lngLat);
          const geoJson = this.createGeoJsonRectangle(rect);
          const source = this.map.getSource(previewId) as maplibregl.GeoJSONSource;
          if (source) source.setData(geoJson);
        };
        this.map.on('mousemove', this.rectMoveHandler);
        this.onStateChange?.();
      } else {
        // Second click: finalize
        const shape = this.cornersToRect(this.rectFirstCorner, e.lngLat);
        this.finishDrawing(shape);
      }
    };
    this.map.on('click', this.rectClickHandler);
  }

  // ── Polygon Drawing ──

  private startPolygonDrawing(): void {
    this.polygonPoints = [];

    const previewId = `geo-preview-polygon`;
    this.polygonPreviewSourceId = previewId;

    this.addAreaPreviewLayers(previewId, true);

    // Track timing to suppress click events that are part of a double-click
    let lastClickTime = 0;
    const DBLCLICK_THRESHOLD = 350; // ms

    this.polygonClickHandler = (e: maplibregl.MapMouseEvent) => {
      const now = performance.now();
      if (now - lastClickTime < DBLCLICK_THRESHOLD) {
        // Second click of a double-click — ignore (dblclick handler will fire)
        return;
      }
      lastClickTime = now;
      // Defer the actual add slightly so we can detect if a dblclick follows
      setTimeout(() => {
        if (this.drawingMode !== 'polygon') return; // already finalized by dblclick
        this.polygonPoints.push(e.lngLat);
        this.updatePolygonPreview(previewId, null);
        this.onStateChange?.();
      }, DBLCLICK_THRESHOLD);
    };

    this.polygonMoveHandler = (e: maplibregl.MapMouseEvent) => {
      if (this.polygonPoints.length > 0) {
        this.updatePolygonPreview(previewId, e.lngLat);
      }
    };

    this.polygonDblClickHandler = (e: maplibregl.MapMouseEvent) => {
      e.preventDefault();
      this.finishCurrentPolygon();
    };

    this.map.on('click', this.polygonClickHandler);
    this.map.on('mousemove', this.polygonMoveHandler);
    this.map.on('dblclick', this.polygonDblClickHandler);
  }

  private updatePolygonPreview(previewId: string, cursorPos: maplibregl.LngLat | null): void {
    const coords: [number, number][] = this.polygonPoints.map((p) => [p.lng, p.lat]);
    if (cursorPos) {
      coords.push([cursorPos.lng, cursorPos.lat]);
    }
    // Close the polygon for preview
    if (coords.length > 0) {
      coords.push(coords[0]);
    }

    const geoJson: GeoJSON.FeatureCollection =
      coords.length >= 4
        ? {
            type: 'FeatureCollection',
            features: [
              {
                type: 'Feature',
                geometry: { type: 'Polygon', coordinates: [coords] },
                properties: {},
              },
            ],
          }
        : {
            type: 'FeatureCollection',
            features:
              coords.length >= 2
                ? [
                    {
                      type: 'Feature',
                      geometry: { type: 'LineString', coordinates: coords.slice(0, -1) }, // don't close as line
                      properties: {},
                    },
                  ]
                : [],
          };

    const source = this.map.getSource(previewId) as maplibregl.GeoJSONSource;
    if (source) source.setData(geoJson);
  }

  // ── Helpers ──

  private finishCurrentPolygon(): void {
    if (this.drawingMode !== 'polygon' || this.polygonPoints.length < 3) return;
    const coordinates: [number, number][] = this.polygonPoints.map((point) => [point.lng, point.lat]);
    this.finishDrawing({ coordinates });
  }

  private finishDrawing(shape: DrawnShape): void {
    const callback = this.drawingCallback;
    this.resetDrawingSession();
    if (callback) callback(shape);
  }

  private removeDrawingHandlers(): void {
    if (this.circleClickHandler) {
      this.map.off('click', this.circleClickHandler);
      this.circleClickHandler = null;
    }
    if (this.circleDragHandler) {
      this.map.off('mousemove', this.circleDragHandler);
      this.circleDragHandler = null;
    }
    if (this.rectClickHandler) {
      this.map.off('click', this.rectClickHandler);
      this.rectClickHandler = null;
    }
    if (this.rectMoveHandler) {
      this.map.off('mousemove', this.rectMoveHandler);
      this.rectMoveHandler = null;
    }
    if (this.polygonClickHandler) {
      this.map.off('click', this.polygonClickHandler);
      this.polygonClickHandler = null;
    }
    if (this.polygonMoveHandler) {
      this.map.off('mousemove', this.polygonMoveHandler);
      this.polygonMoveHandler = null;
    }
    if (this.polygonDblClickHandler) {
      this.map.off('dblclick', this.polygonDblClickHandler);
      this.polygonDblClickHandler = null;
    }
    this.circleCenter = null;
    this.circlePreviewRadiusM = null;
    this.rectFirstCorner = null;
    this.polygonPoints = [];
  }

  private removePreviewLayers(): void {
    const previewIds = ['geo-preview-circle', 'geo-preview-rect', 'geo-preview-polygon'];
    for (const id of previewIds) {
      for (const suffix of ['-fill', '-outline']) {
        try {
          if (this.map.getLayer(id + suffix)) this.map.removeLayer(id + suffix);
        } catch {
          /* map may be destroyed */
        }
      }
      try {
        if (this.map.getSource(id)) this.map.removeSource(id);
      } catch {
        /* map may be destroyed */
      }
    }
    // Remove preview labels (e.g. live circle radius)
    const labelIds = [CIRCLE_PREVIEW_LABEL_SOURCE_ID];
    for (const id of labelIds) {
      try {
        if (this.map.getLayer(id)) this.map.removeLayer(id);
      } catch {
        /* map may be destroyed */
      }
      try {
        if (this.map.getSource(id)) this.map.removeSource(id);
      } catch {
        /* map may be destroyed */
      }
    }
    this.rectPreviewSourceId = null;
    this.polygonPreviewSourceId = null;
    this.circlePreviewRadiusM = null;
  }

  private addFillAndOutline(id: string, geoJson: GeoJSON.FeatureCollection, color: string): void {
    this.map.addSource(id, { type: 'geojson', data: geoJson });
    this.sourceIds.push(id);

    this.map.addLayer({
      id: id + '-fill',
      type: 'fill',
      source: id,
      paint: { 'fill-color': color, 'fill-opacity': SHAPE_FILL_OPACITY },
    });
    this.layerIds.push(id + '-fill');

    this.map.addLayer({
      id: id + '-outline',
      type: 'line',
      source: id,
      paint: { 'line-color': color, 'line-width': SHAPE_LINE_WIDTH, 'line-opacity': SHAPE_LINE_OPACITY },
    });
    this.layerIds.push(id + '-outline');
  }

  private addCenterLabel(baseId: string, lng: number, lat: number, text: () => string): void {
    const sourceId = baseId + '-label';
    const measurementLabel = { lng, lat, text };
    this.measurementLabels.set(baseId, measurementLabel);
    this.map.addSource(sourceId, {
      type: 'geojson',
      data: this.labelGeoJson(lng, lat, text()),
    });
    this.sourceIds.push(sourceId);

    this.map.addLayer(measurementLabelLayer(sourceId));
    this.layerIds.push(sourceId);
  }

  private updateLabelSource(sourceId: string, lng: number, lat: number, text: string): void {
    const source = this.map.getSource(sourceId) as maplibregl.GeoJSONSource | undefined;
    source?.setData(this.labelGeoJson(lng, lat, text));
  }

  private labelGeoJson(lng: number, lat: number, text: string): GeoJSON.FeatureCollection {
    return {
      type: 'FeatureCollection',
      features: [
        {
          type: 'Feature',
          geometry: { type: 'Point', coordinates: [lng, lat] },
          properties: { label: text },
        },
      ],
    };
  }

  private measurementLabel(name: string | undefined, measurement: string): string {
    return name ? `${name}\n${measurement}` : measurement;
  }

  private cornersToRect(a: maplibregl.LngLat, b: maplibregl.LngLat): DrawnRectangle {
    return {
      minLat: Math.min(a.lat, b.lat),
      maxLat: Math.max(a.lat, b.lat),
      minLng: Math.min(a.lng, b.lng),
      maxLng: Math.max(a.lng, b.lng),
    };
  }

  private createGeoJsonRectangle(rect: DrawnRectangle): GeoJSON.FeatureCollection {
    return {
      type: 'FeatureCollection',
      features: [
        {
          type: 'Feature',
          geometry: {
            type: 'Polygon',
            coordinates: [
              [
                [rect.minLng, rect.minLat],
                [rect.maxLng, rect.minLat],
                [rect.maxLng, rect.maxLat],
                [rect.minLng, rect.maxLat],
                [rect.minLng, rect.minLat], // close
              ],
            ],
          },
          properties: {},
        },
      ],
    };
  }

  /**
   * Calculate distance in meters between two lat/lng points using the Haversine formula.
   */
  private distanceInMeters(a: maplibregl.LngLat, b: maplibregl.LngLat): number {
    const R = 6371000;
    const dLat = ((b.lat - a.lat) * Math.PI) / 180;
    const dLng = ((b.lng - a.lng) * Math.PI) / 180;
    const sinDLat = Math.sin(dLat / 2);
    const sinDLng = Math.sin(dLng / 2);
    const aVal =
      sinDLat * sinDLat + Math.cos((a.lat * Math.PI) / 180) * Math.cos((b.lat * Math.PI) / 180) * sinDLng * sinDLng;
    return R * 2 * Math.atan2(Math.sqrt(aVal), Math.sqrt(1 - aVal));
  }
}
