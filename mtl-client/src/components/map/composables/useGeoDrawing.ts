import { markRaw } from 'vue';
import { GeoDrawingOverlay } from '@/layers/GeoDrawingOverlay';
import type { DrawnShape } from '@/layers/GeoDrawingOverlay';
import type { GeoDrawingMethods, MapControllerMethodDefinitions } from './mapControllerRuntime';

export function useGeoDrawing(_deps: Record<string, never> = {}): MapControllerMethodDefinitions<GeoDrawingMethods> {
  const methods: MapControllerMethodDefinitions<GeoDrawingMethods> = {
    // ── Geo drawing ──
    onStartGeoDrawing(paramDef) {
      if (!this.overlayMap) return;
      // Create overlay if needed
      if (!this.geoDrawingOverlay) {
        this.geoDrawingOverlay = markRaw(new GeoDrawingOverlay(this.overlayMap));
      }
      this.geoDrawingParamDef = paramDef;
      this.geoDrawPointCount = 0;

      // Render any existing shapes from the filter so the user sees them while drawing
      this.renderExistingGeoShapes();

      // Close the filter sheet so the user can interact with the map
      if (this.$refs.filterTool?.close) this.$refs.filterTool.close();

      // Map from param type to drawing mode
      const modeMap = {
        GEO_CIRCLE: 'circle',
        GEO_RECTANGLE: 'rectangle',
        GEO_POLYGON: 'polygon',
      } as const;
      const mode =
        paramDef.type && paramDef.type in modeMap ? modeMap[paramDef.type as keyof typeof modeMap] : undefined;
      if (!mode) return;
      const onDrawingComplete = (shape: DrawnShape) => {
        // Forward to FilterTool → CustomFilter (store first so renderExisting picks it up)
        const filterTool = this.$refs.filterTool;
        filterTool?.onGeoDrawingComplete?.(paramDef, shape);
        this.geoDrawingParamDef = null;
        this.geoDrawPointCount = 0;
        // Re-render all shapes (including the newly drawn one)
        this.renderExistingGeoShapes();
        // Reopen filter sheet to show the result
        if (filterTool?.toggle) filterTool.toggle();
      };
      const onStateChange = () => {
        this.geoDrawPointCount = this.geoDrawingOverlay?.getPointCount() ?? 0;
      };
      this.geoDrawingOverlay.startDrawing(mode, onDrawingComplete, onStateChange);
    },

    onGeoDrawUndo() {
      if (this.geoDrawingOverlay) {
        this.geoDrawingOverlay.undoLastPoint();
      }
    },

    onGeoDrawFinish() {
      if (this.geoDrawingOverlay) {
        this.geoDrawingOverlay.finishPolygon();
      }
    },

    onGeoDrawCancel() {
      if (this.geoDrawingOverlay) {
        this.geoDrawingOverlay.cancelDrawing();
      }
      this.geoDrawingParamDef = null;
      this.geoDrawPointCount = 0;
    },

    onClearGeoShape(_paramDef) {
      // Clear drawn shapes from the map overlay
      if (this.geoDrawingOverlay) {
        this.geoDrawingOverlay.clearAll();
      }
    },

    /** Render all existing geo shapes from the filter so they're visible on the map. */
    renderExistingGeoShapes() {
      if (!this.geoDrawingOverlay) return;
      this.geoDrawingOverlay.clearAll();
      const filterTool = this.$refs.filterTool;
      if (!filterTool?.getGeoShapes) return;
      const shapes = filterTool.getGeoShapes();
      const labels = shapes.labels ?? {};
      for (const [key, circle] of Object.entries(shapes.circles)) {
        if (circle) this.geoDrawingOverlay.renderCircle(circle, undefined, labels[key]);
      }
      for (const [key, rect] of Object.entries(shapes.rectangles)) {
        if (rect) this.geoDrawingOverlay.renderRectangle(rect, undefined, labels[key]);
      }
      for (const [key, polygon] of Object.entries(shapes.polygons)) {
        if (polygon) this.geoDrawingOverlay.renderPolygon(polygon, undefined, labels[key]);
      }
    },
  };
  return methods;
}
