import type { RawMediaPoint } from "@/repositories/mediaRepository";
import { normalizeToLatLng } from "@/utils/normalizeLatLng";

/**
 * Create a GeoJSON FeatureCollection from raw media points.
 * This replaces the old Leaflet L.layerGroup-based approach.
 * The returned GeoJSON can be added as a MapLibre source.
 */
export function createMediaGeoJson(points: RawMediaPoint[]): GeoJSON.FeatureCollection {
  return {
    type: 'FeatureCollection',
    features: points.map(p => {
      const [lat, lng] = normalizeToLatLng(p.exifGpsLocationLat, p.exifGpsLocationLong);
      return {
        type: 'Feature' as const,
        geometry: {
          type: 'Point' as const,
          coordinates: [lng, lat],
        },
        properties: {
          mediaId: p.id,
          title: (p as any).title || (p as any).fileName || '',
        },
      };
    }),
  };
}
