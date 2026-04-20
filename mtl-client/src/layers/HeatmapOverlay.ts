import maplibregl from 'maplibre-gl';

const SOURCE_ID = 'heatmap-points';
const LAYER_ID = 'heatmap-layer';

/**
 * Heatmap overlay — extracts all GPS track coordinates from the loaded GeoJSON
 * and renders them as a density heatmap on the overlay map.
 *
 * Usage:
 *   const overlay = new HeatmapOverlay(overlayMap);
 *   overlay.show(geojson);       // show with current track data
 *   overlay.updateData(geojson); // refresh after new tracks load
 *   overlay.hide();
 *   overlay.destroy();           // cleanup on unmount
 */
export class HeatmapOverlay {
  private readonly map: maplibregl.Map;
  private visible = false;

  constructor(map: maplibregl.Map) {
    this.map = map;
  }

  isVisible(): boolean {
    return this.visible;
  }

  show(geojson: GeoJSON.FeatureCollection): void {
    if (this.visible) return;

    const points = this.extractPoints(geojson);

    if (!this.map.getSource(SOURCE_ID)) {
      this.map.addSource(SOURCE_ID, {
        type: 'geojson',
        data: { type: 'FeatureCollection', features: points },
      });
    } else {
      (this.map.getSource(SOURCE_ID) as maplibregl.GeoJSONSource).setData({
        type: 'FeatureCollection',
        features: points,
      });
    }

    if (!this.map.getLayer(LAYER_ID)) {
      // Insert below the tracks-layer so coloured tracks always render on top
      const beforeLayer = this.map.getLayer('tracks-layer') ? 'tracks-layer' : undefined;

      this.map.addLayer(
        {
          id: LAYER_ID,
          type: 'heatmap',
          source: SOURCE_ID,
          paint: {
            // Each point contributes a fixed weight — density is the key signal
            'heatmap-weight': 0.35,

            // Slightly ramp up intensity as you zoom in
            'heatmap-intensity': [
              'interpolate', ['linear'], ['zoom'],
              0, 0.4,
              8, 1.0,
              13, 1.8,
              17, 2.5,
            ],

            // Color gradient: transparent → indigo → cyan → lime → yellow → red
            'heatmap-color': [
              'interpolate',
              ['linear'],
              ['heatmap-density'],
              0,    'rgba(0,0,0,0)',
              0.1,  'rgba(99,102,241,0.5)',   // indigo-500
              0.3,  'rgba(34,211,238,0.7)',   // cyan-400
              0.5,  'rgba(163,230,53,0.8)',   // lime-400
              0.75, 'rgba(250,204,21,0.9)',   // yellow-400
              1.0,  'rgba(239,68,68,1)',      // red-500
            ],

            // Radius grows with zoom so detail is visible up close
            'heatmap-radius': [
              'interpolate', ['linear'], ['zoom'],
              0,  5,
              7,  10,
              12, 18,
              15, 28,
              18, 40,
            ],

            // Fade to transparent at very high zoom levels where individual
            // tracks are clearly visible anyway
            'heatmap-opacity': [
              'interpolate', ['linear'], ['zoom'],
              12, 0.92,
              17, 0.25,
            ],
          },
        },
        beforeLayer,
      );
    }

    this.visible = true;
  }

  hide(): void {
    if (!this.visible) return;
    if (this.map.getLayer(LAYER_ID)) this.map.removeLayer(LAYER_ID);
    if (this.map.getSource(SOURCE_ID)) this.map.removeSource(SOURCE_ID);
    this.visible = false;
  }

  /** Refresh heatmap point data after tracks have been updated (e.g. 10m load). */
  updateData(geojson: GeoJSON.FeatureCollection): void {
    if (!this.visible) return;
    const source = this.map.getSource(SOURCE_ID) as maplibregl.GeoJSONSource | undefined;
    if (!source) return;
    source.setData({
      type: 'FeatureCollection',
      features: this.extractPoints(geojson),
    });
  }

  destroy(): void {
    this.hide();
  }

  /**
   * Convert LineString track features to Point features for the heatmap source.
   * Samples every SAMPLE_RATE-th coordinate to keep the dataset manageable
   * while still representing track density accurately.
   */
  private extractPoints(geojson: GeoJSON.FeatureCollection): GeoJSON.Feature<GeoJSON.Point>[] {
    const points: GeoJSON.Feature<GeoJSON.Point>[] = [];
    // 1 in every N coords — keeps point count reasonable for large libraries
    const SAMPLE_RATE = 3;

    for (const feature of geojson.features) {
      if (feature.geometry.type === 'LineString') {
        const coords = feature.geometry.coordinates;
        for (let i = 0; i < coords.length; i += SAMPLE_RATE) {
          points.push({
            type: 'Feature',
            properties: {},
            geometry: { type: 'Point', coordinates: coords[i] },
          });
        }
      } else if (feature.geometry.type === 'Point') {
        points.push({
          type: 'Feature',
          properties: {},
          geometry: feature.geometry as GeoJSON.Point,
        });
      }
    }
    return points;
  }
}
