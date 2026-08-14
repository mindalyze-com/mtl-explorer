const EARTH_RADIUS_METERS = 6_371_000;
const DEFAULT_CIRCLE_POINT_COUNT = 64;

export function createGeoJsonCircle(
  lng: number,
  lat: number,
  radiusMeters: number,
  points = DEFAULT_CIRCLE_POINT_COUNT
): GeoJSON.FeatureCollection<GeoJSON.Polygon> {
  const coordinates: Array<[number, number]> = [];
  const latRadians = (lat * Math.PI) / 180;
  const lngRadians = (lng * Math.PI) / 180;

  for (let index = 0; index <= points; index += 1) {
    const angle = (index / points) * 2 * Math.PI;
    const latitudeOffset = (radiusMeters / EARTH_RADIUS_METERS) * Math.cos(angle);
    const longitudeOffset = (radiusMeters / (EARTH_RADIUS_METERS * Math.cos(latRadians))) * Math.sin(angle);
    coordinates.push([
      ((lngRadians + longitudeOffset) * 180) / Math.PI,
      ((latRadians + latitudeOffset) * 180) / Math.PI,
    ]);
  }

  return {
    type: 'FeatureCollection',
    features: [
      {
        type: 'Feature',
        geometry: { type: 'Polygon', coordinates: [coordinates] },
        properties: {},
      },
    ],
  };
}
