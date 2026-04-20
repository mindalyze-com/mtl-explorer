import maplibregl from 'maplibre-gl';

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

const SHAPE_COLOR = '#6366f1';
const SHAPE_FILL_OPACITY = 0.15;
const SHAPE_LINE_WIDTH = 2;
const SHAPE_LINE_OPACITY = 0.7;

/**
 * Manages drawing geo shapes (circle, rectangle, polygon) on a MapLibre map.
 * Follows the same source/layer pattern as MeasureBetweenPoints and HeatmapOverlay.
 */
export class GeoDrawingOverlay {
  private readonly map: maplibregl.Map;
  private sourceIds: string[] = [];
  private layerIds: string[] = [];
  private shapeCounter = 0;

  // Drawing state
  private drawingMode: GeoShapeType | null = null;
  private drawingCallback: ((shape: DrawnShape) => void) | null = null;
  private onStateChange: (() => void) | null = null;

  // Circle drawing state
  private circleCenter: maplibregl.LngLat | null = null;
  private circleDragHandler: ((e: maplibregl.MapMouseEvent) => void) | null = null;
  private circleClickHandler: ((e: maplibregl.MapMouseEvent) => void) | null = null;

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
      if (this.circleDragHandler) { this.map.off('mousemove', this.circleDragHandler); this.circleDragHandler = null; }
      this.removePreviewLayers();
      this.onStateChange?.();
    } else if (this.drawingMode === 'rectangle' && this.rectFirstCorner) {
      this.rectFirstCorner = null;
      if (this.rectMoveHandler) { this.map.off('mousemove', this.rectMoveHandler); this.rectMoveHandler = null; }
      this.removePreviewLayers();
      this.onStateChange?.();
    }
  }

  /** Finish polygon drawing (programmatic — e.g. from a "Finish" button). */
  finishPolygon(): void {
    if (this.drawingMode !== 'polygon' || this.polygonPoints.length < 3) return;
    const coords: [number, number][] = this.polygonPoints.map(p => [p.lng, p.lat]);
    const shape: DrawnPolygon = { coordinates: coords };
    this.finishDrawing(shape);
  }

  /**
   * Cancel any in-progress drawing without finalizing.
   */
  cancelDrawing(): void {
    this.removeDrawingHandlers();
    this.removePreviewLayers();
    this.drawingMode = null;
    this.drawingCallback = null;
    this.onStateChange = null;
    try { this.map.getCanvas().style.cursor = ''; } catch { /* map may be destroyed */ }
  }

  /**
   * Render a finalized circle shape on the map.
   */
  renderCircle(circle: DrawnCircle, color: string = SHAPE_COLOR, name?: string): string {
    const id = `geo-shape-${this.shapeCounter++}`;
    const geoJson = this.createGeoJsonCircle(circle.lng, circle.lat, circle.radiusM);

    this.addFillAndOutline(id, geoJson, color);
    const labelParts: string[] = [];
    if (name) labelParts.push(name);
    labelParts.push(formatRadius(circle.radiusM));
    this.addCenterLabel(id, circle.lng, circle.lat, labelParts.join('\n'));
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
      new maplibregl.LngLat(rect.maxLng, centerLat),
    );
    const heightM = this.distanceInMeters(
      new maplibregl.LngLat(centerLng, rect.minLat),
      new maplibregl.LngLat(centerLng, rect.maxLat),
    );
    const labelParts: string[] = [];
    if (name) labelParts.push(name);
    labelParts.push(`${formatRadius(widthM)} × ${formatRadius(heightM)}`);
    this.addCenterLabel(id, centerLng, centerLat, labelParts.join('\n'));
    return id;
  }

  /**
   * Render a finalized polygon shape on the map.
   */
  renderPolygon(polygon: DrawnPolygon, color: string = SHAPE_COLOR, name?: string): string {
    const id = `geo-shape-${this.shapeCounter++}`;
    const coords = [...polygon.coordinates];
    // Close polygon
    if (coords.length > 0 && (coords[0][0] !== coords[coords.length - 1][0] || coords[0][1] !== coords[coords.length - 1][1])) {
      coords.push(coords[0]);
    }
    const geoJson: GeoJSON.FeatureCollection = {
      type: 'FeatureCollection',
      features: [{
        type: 'Feature',
        geometry: { type: 'Polygon', coordinates: [coords] },
        properties: {},
      }],
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
        const b = new maplibregl.LngLat(rawCoords[(i + 1) % rawCoords.length][0], rawCoords[(i + 1) % rawCoords.length][1]);
        perimeterM += this.distanceInMeters(a, b);
      }
      const labelParts: string[] = [];
      if (name) labelParts.push(name);
      labelParts.push(`${rawCoords.length} pts, ${formatRadius(perimeterM)} perimeter`);
      this.addCenterLabel(id, centroidLng, centroidLat, labelParts.join('\n'));
    }
    return id;
  }

  /**
   * Remove a specific rendered shape by its id.
   */
  removeShape(shapeId: string): void {
    const layerSuffixes = ['-fill', '-outline', '-label'];
    for (const suffix of layerSuffixes) {
      const layerId = shapeId + suffix;
      try {
        if (this.map.getLayer(layerId)) {
          this.map.removeLayer(layerId);
          this.layerIds = this.layerIds.filter(id => id !== layerId);
        }
      } catch { /* map may be destroyed */ }
    }
    const sourceSuffixes = ['', '-label'];
    for (const suffix of sourceSuffixes) {
      const sourceId = shapeId + suffix;
      try {
        if (this.map.getSource(sourceId)) {
          this.map.removeSource(sourceId);
          this.sourceIds = this.sourceIds.filter(id => id !== sourceId);
        }
      } catch { /* map may be destroyed */ }
    }
  }

  /**
   * Remove all rendered shapes from the map.
   */
  clearAll(): void {
    for (const layerId of [...this.layerIds]) {
      try { if (this.map.getLayer(layerId)) this.map.removeLayer(layerId); } catch { /* map may be destroyed */ }
    }
    for (const sourceId of [...this.sourceIds]) {
      try { if (this.map.getSource(sourceId)) this.map.removeSource(sourceId); } catch { /* map may be destroyed */ }
    }
    this.layerIds = [];
    this.sourceIds = [];
    this.shapeCounter = 0;
  }

  destroy(): void {
    this.cancelDrawing();
    this.clearAll();
  }

  // ── Circle Drawing ──

  private startCircleDrawing(): void {
    this.circleCenter = null;

    this.circleClickHandler = (e: maplibregl.MapMouseEvent) => {
      if (!this.circleCenter) {
        // First click: set center
        this.circleCenter = e.lngLat;
        // Show a preview circle at 100m initially
        const previewId = `geo-preview-circle`;
        this.rectPreviewSourceId = previewId; // reuse field for cleanup
        const geoJson = this.createGeoJsonCircle(e.lngLat.lng, e.lngLat.lat, 100);
        this.map.addSource(previewId, { type: 'geojson', data: geoJson });
        this.map.addLayer({
          id: previewId + '-fill', type: 'fill', source: previewId,
          paint: { 'fill-color': SHAPE_COLOR, 'fill-opacity': SHAPE_FILL_OPACITY },
        });
        this.map.addLayer({
          id: previewId + '-outline', type: 'line', source: previewId,
          paint: { 'line-color': SHAPE_COLOR, 'line-width': SHAPE_LINE_WIDTH, 'line-opacity': SHAPE_LINE_OPACITY },
        });

        // Add a live radius label at center
        const labelId = `geo-preview-circle-label`;
        this.map.addSource(labelId, {
          type: 'geojson',
          data: { type: 'FeatureCollection', features: [{ type: 'Feature', geometry: { type: 'Point', coordinates: [e.lngLat.lng, e.lngLat.lat] }, properties: { label: '100 m' } }] },
        });
        this.map.addLayer({
          id: labelId, type: 'symbol', source: labelId,
          layout: { 'text-field': ['get', 'label'], 'text-size': 13, 'text-font': ['Noto Sans Medium'], 'text-anchor': 'center', 'text-allow-overlap': true },
          paint: { 'text-color': SHAPE_COLOR, 'text-halo-color': 'rgba(255,255,255,0.9)', 'text-halo-width': 2 },
        });

        // Listen for mouse move to update radius + label
        this.circleDragHandler = (moveEvent: maplibregl.MapMouseEvent) => {
          const radiusM = this.distanceInMeters(this.circleCenter!, moveEvent.lngLat);
          const source = this.map.getSource(previewId) as maplibregl.GeoJSONSource;
          if (source) {
            source.setData(this.createGeoJsonCircle(this.circleCenter!.lng, this.circleCenter!.lat, radiusM));
          }
          const labelSource = this.map.getSource(labelId) as maplibregl.GeoJSONSource;
          if (labelSource) {
            labelSource.setData({
              type: 'FeatureCollection',
              features: [{ type: 'Feature', geometry: { type: 'Point', coordinates: [this.circleCenter!.lng, this.circleCenter!.lat] }, properties: { label: formatRadius(radiusM) } }],
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

  private startRectangleDrawing(): void {
    this.rectFirstCorner = null;

    this.rectClickHandler = (e: maplibregl.MapMouseEvent) => {
      if (!this.rectFirstCorner) {
        // First click: set corner
        this.rectFirstCorner = e.lngLat;
        const previewId = `geo-preview-rect`;
        this.rectPreviewSourceId = previewId;

        const emptyGeoJson: GeoJSON.FeatureCollection = {
          type: 'FeatureCollection', features: [],
        };
        this.map.addSource(previewId, { type: 'geojson', data: emptyGeoJson });
        this.map.addLayer({
          id: previewId + '-fill', type: 'fill', source: previewId,
          paint: { 'fill-color': SHAPE_COLOR, 'fill-opacity': SHAPE_FILL_OPACITY },
        });
        this.map.addLayer({
          id: previewId + '-outline', type: 'line', source: previewId,
          paint: { 'line-color': SHAPE_COLOR, 'line-width': SHAPE_LINE_WIDTH, 'line-opacity': SHAPE_LINE_OPACITY },
        });

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

    const emptyGeoJson: GeoJSON.FeatureCollection = {
      type: 'FeatureCollection', features: [],
    };
    this.map.addSource(previewId, { type: 'geojson', data: emptyGeoJson });
    this.map.addLayer({
      id: previewId + '-fill', type: 'fill', source: previewId,
      paint: { 'fill-color': SHAPE_COLOR, 'fill-opacity': SHAPE_FILL_OPACITY },
    });
    this.map.addLayer({
      id: previewId + '-outline', type: 'line', source: previewId,
      paint: { 'line-color': SHAPE_COLOR, 'line-width': SHAPE_LINE_WIDTH, 'line-opacity': SHAPE_LINE_OPACITY, 'line-dasharray': [2, 2] },
    });

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
      if (this.polygonPoints.length >= 3) {
        const coords: [number, number][] = this.polygonPoints.map(p => [p.lng, p.lat]);
        const shape: DrawnPolygon = { coordinates: coords };
        this.finishDrawing(shape);
      }
    };

    this.map.on('click', this.polygonClickHandler);
    this.map.on('mousemove', this.polygonMoveHandler);
    this.map.on('dblclick', this.polygonDblClickHandler);
  }

  private updatePolygonPreview(previewId: string, cursorPos: maplibregl.LngLat | null): void {
    const coords: [number, number][] = this.polygonPoints.map(p => [p.lng, p.lat]);
    if (cursorPos) {
      coords.push([cursorPos.lng, cursorPos.lat]);
    }
    // Close the polygon for preview
    if (coords.length > 0) {
      coords.push(coords[0]);
    }

    const geoJson: GeoJSON.FeatureCollection = coords.length >= 4
      ? {
        type: 'FeatureCollection',
        features: [{
          type: 'Feature',
          geometry: { type: 'Polygon', coordinates: [coords] },
          properties: {},
        }],
      }
      : {
        type: 'FeatureCollection',
        features: coords.length >= 2
          ? [{
            type: 'Feature',
            geometry: { type: 'LineString', coordinates: coords.slice(0, -1) }, // don't close as line
            properties: {},
          }]
          : [],
      };

    const source = this.map.getSource(previewId) as maplibregl.GeoJSONSource;
    if (source) source.setData(geoJson);
  }

  // ── Helpers ──

  private finishDrawing(shape: DrawnShape): void {
    const callback = this.drawingCallback;
    this.removeDrawingHandlers();
    this.removePreviewLayers();
    this.drawingMode = null;
    this.drawingCallback = null;
    this.onStateChange = null;
    try { this.map.getCanvas().style.cursor = ''; } catch { /* map may be destroyed */ }
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
    this.rectFirstCorner = null;
    this.polygonPoints = [];
  }

  private removePreviewLayers(): void {
    const previewIds = ['geo-preview-circle', 'geo-preview-rect', 'geo-preview-polygon'];
    for (const id of previewIds) {
      for (const suffix of ['-fill', '-outline']) {
        try { if (this.map.getLayer(id + suffix)) this.map.removeLayer(id + suffix); } catch { /* map may be destroyed */ }
      }
      try { if (this.map.getSource(id)) this.map.removeSource(id); } catch { /* map may be destroyed */ }
    }
    // Remove preview labels (e.g. live circle radius)
    const labelIds = ['geo-preview-circle-label'];
    for (const id of labelIds) {
      try { if (this.map.getLayer(id)) this.map.removeLayer(id); } catch { /* map may be destroyed */ }
      try { if (this.map.getSource(id)) this.map.removeSource(id); } catch { /* map may be destroyed */ }
    }
    this.rectPreviewSourceId = null;
    this.polygonPreviewSourceId = null;
  }

  private addFillAndOutline(id: string, geoJson: GeoJSON.FeatureCollection, color: string): void {
    this.map.addSource(id, { type: 'geojson', data: geoJson });
    this.sourceIds.push(id);

    this.map.addLayer({
      id: id + '-fill', type: 'fill', source: id,
      paint: { 'fill-color': color, 'fill-opacity': SHAPE_FILL_OPACITY },
    });
    this.layerIds.push(id + '-fill');

    this.map.addLayer({
      id: id + '-outline', type: 'line', source: id,
      paint: { 'line-color': color, 'line-width': SHAPE_LINE_WIDTH, 'line-opacity': SHAPE_LINE_OPACITY },
    });
    this.layerIds.push(id + '-outline');
  }

  private addCenterLabel(baseId: string, lng: number, lat: number, text: string): void {
    const sourceId = baseId + '-label';
    this.map.addSource(sourceId, {
      type: 'geojson',
      data: {
        type: 'FeatureCollection',
        features: [{
          type: 'Feature',
          geometry: { type: 'Point', coordinates: [lng, lat] },
          properties: { label: text },
        }],
      },
    });
    this.sourceIds.push(sourceId);

    this.map.addLayer({
      id: sourceId, type: 'symbol', source: sourceId,
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
    });
    this.layerIds.push(sourceId);
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
      features: [{
        type: 'Feature',
        geometry: {
          type: 'Polygon',
          coordinates: [[
            [rect.minLng, rect.minLat],
            [rect.maxLng, rect.minLat],
            [rect.maxLng, rect.maxLat],
            [rect.minLng, rect.maxLat],
            [rect.minLng, rect.minLat], // close
          ]],
        },
        properties: {},
      }],
    };
  }

  /**
   * Create a GeoJSON circle approximated as a 64-point polygon.
   * Same algorithm as MeasureBetweenPoints.vue.
   */
  private createGeoJsonCircle(lng: number, lat: number, radiusMeters: number, points = 64): GeoJSON.FeatureCollection {
    const coords: [number, number][] = [];
    const earthRadius = 6371000;
    const latRad = lat * Math.PI / 180;
    const lngRad = lng * Math.PI / 180;
    for (let i = 0; i <= points; i++) {
      const angle = (i / points) * 2 * Math.PI;
      const dLat = (radiusMeters / earthRadius) * Math.cos(angle);
      const dLng = (radiusMeters / (earthRadius * Math.cos(latRad))) * Math.sin(angle);
      coords.push([
        (lngRad + dLng) * 180 / Math.PI,
        (latRad + dLat) * 180 / Math.PI,
      ]);
    }
    return {
      type: 'FeatureCollection',
      features: [{
        type: 'Feature',
        geometry: { type: 'Polygon', coordinates: [coords] },
        properties: {},
      }],
    };
  }

  /**
   * Calculate distance in meters between two lat/lng points using the Haversine formula.
   */
  private distanceInMeters(a: maplibregl.LngLat, b: maplibregl.LngLat): number {
    const R = 6371000;
    const dLat = (b.lat - a.lat) * Math.PI / 180;
    const dLng = (b.lng - a.lng) * Math.PI / 180;
    const sinDLat = Math.sin(dLat / 2);
    const sinDLng = Math.sin(dLng / 2);
    const aVal = sinDLat * sinDLat + Math.cos(a.lat * Math.PI / 180) * Math.cos(b.lat * Math.PI / 180) * sinDLng * sinDLng;
    return R * 2 * Math.atan2(Math.sqrt(aVal), Math.sqrt(1 - aVal));
  }
}

function formatRadius(meters: number): string {
  if (meters >= 1000) {
    return `${(meters / 1000).toFixed(1)} km`;
  }
  return `${Math.round(meters)} m`;
}
