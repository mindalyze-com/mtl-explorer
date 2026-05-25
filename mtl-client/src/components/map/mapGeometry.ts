import { BACKGROUND_TRACK_PRECISION, DETAIL_TRACK_PRECISION, OVERVIEW_PRECISION } from '@/utils/tracks/trackConstants';

export const LOCATION_SEARCH_ZOOM_OFFSET = 4;
export const LOCATION_SEARCH_TARGET_ZOOM_MIN = 4;
export const LOCATION_SEARCH_TARGET_ZOOM_MAX = 15;
export const LOCATION_SEARCH_POI_ZOOM = 14.5;
export const LOCATION_SEARCH_AREA_ZOOM = 12.5;
export const LOCATION_SEARCH_REGION_ZOOM = 7.5;
export const LOCATION_SEARCH_COUNTRY_ZOOM = 4.5;

export function precisionForZoom(zoom: number): number {
  if (zoom >= 17) return DETAIL_TRACK_PRECISION;
  if (zoom >= 6) return BACKGROUND_TRACK_PRECISION;
  return OVERVIEW_PRECISION;
}

export function collectionPrecisionForZoom(zoom: number): number {
  const precision = precisionForZoom(zoom);
  return precision === DETAIL_TRACK_PRECISION ? BACKGROUND_TRACK_PRECISION : precision;
}

export function isSameOrBetterPrecision(incomingPrecision: number, currentPrecision: number): boolean {
  return incomingPrecision <= currentPrecision;
}

export function buildTrackOverviewFeatures(
  geojson: GeoJSON.FeatureCollection | null | undefined
): GeoJSON.Feature<GeoJSON.Point>[] {
  return (geojson?.features ?? [])
    .filter((feature) => {
      if (!feature.geometry) return false;
      if (feature.geometry.type === 'LineString') return feature.geometry.coordinates.length > 0;
      return feature.geometry.type === 'Point';
    })
    .map((feature) => {
      const geometry = feature.geometry as GeoJSON.LineString | GeoJSON.Point;
      const startCoord = geometry.type === 'LineString' ? geometry.coordinates[0] : geometry.coordinates;
      return {
        type: 'Feature',
        geometry: { type: 'Point', coordinates: startCoord },
        properties: {
          id: feature.properties?.id,
          filterGroup: feature.properties?.filterGroup,
        },
      };
    });
}

/** Haversine distance in meters between two [lat, lng] points. */
export function haversineDistance(lat1: number, lng1: number, lat2: number, lng2: number): number {
  const earthRadiusMeters = 6371000;
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLng = ((lng2 - lng1) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos((lat1 * Math.PI) / 180) * Math.cos((lat2 * Math.PI) / 180) * Math.sin(dLng / 2) ** 2;
  return earthRadiusMeters * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

/**
 * Forward bearing in degrees, where 0 is north and values increase clockwise.
 */
export function bearing(lng1: number, lat1: number, lng2: number, lat2: number): number {
  const toRad = Math.PI / 180;
  const dLng = (lng2 - lng1) * toRad;
  const lat1R = lat1 * toRad;
  const lat2R = lat2 * toRad;
  const y = Math.sin(dLng) * Math.cos(lat2R);
  const x = Math.cos(lat1R) * Math.sin(lat2R) - Math.sin(lat1R) * Math.cos(lat2R) * Math.cos(dLng);
  return ((Math.atan2(y, x) * 180) / Math.PI + 360) % 360;
}

export interface LocationSearchZoomResult {
  kind?: string | null;
  kindDetail?: string | null;
  sourceLayer?: string | null;
  minZoom?: number | null;
  maxZoom?: number | null;
}

export function locationSearchTargetZoom(result: LocationSearchZoomResult | null | undefined): number {
  const kind = String(result?.kindDetail || result?.kind || '').toLowerCase();
  if (kind === 'country') return LOCATION_SEARCH_COUNTRY_ZOOM;
  if (kind === 'region') return LOCATION_SEARCH_REGION_ZOOM;
  if (kind === 'park' || kind === 'national_park' || kind === 'nature_reserve') return LOCATION_SEARCH_AREA_ZOOM;
  if (
    result?.sourceLayer === 'pois' ||
    ['peak', 'mountain', 'pass', 'ridge', 'hill', 'viewpoint', 'alpine_hut', 'wilderness_hut', 'shelter', 'camp_site'].includes(kind)
  ) {
    return LOCATION_SEARCH_POI_ZOOM;
  }

  const minZoom = Number(result?.minZoom);
  const maxZoom = Number(result?.maxZoom);
  const zoomFromMin = Number.isFinite(minZoom) ? minZoom + LOCATION_SEARCH_ZOOM_OFFSET : Number.NaN;
  const zoomFromMax = Number.isFinite(maxZoom) ? maxZoom : Number.NaN;
  const zoomCandidates = [zoomFromMin, zoomFromMax].filter(Number.isFinite);
  if (zoomCandidates.length === 0) {
    return LOCATION_SEARCH_AREA_ZOOM;
  }
  const zoomTarget = Math.max(...zoomCandidates);
  return Math.max(LOCATION_SEARCH_TARGET_ZOOM_MIN, Math.min(LOCATION_SEARCH_TARGET_ZOOM_MAX, zoomTarget));
}
