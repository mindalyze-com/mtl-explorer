export type LatLng = [number, number];

// Optional bbox for Switzerland; adjust to your main operating area if needed
const SWISS_BBOX = { minLat: 45.7, maxLat: 47.9, minLng: 5.7, maxLng: 10.7 };

function inWorldRange(lat: number, lng: number) {
  return lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180;
}

function inBbox(lat: number, lng: number) {
  return lat >= SWISS_BBOX.minLat && lat <= SWISS_BBOX.maxLat &&
         lng >= SWISS_BBOX.minLng && lng <= SWISS_BBOX.maxLng;
}

/**
 * Accepts values from server (possibly wrong order) and returns [lat, lng] (EPSG:4326).
 * Heuristics:
 *  - If (lat,lng) is in world range and (lng,lat) is not → keep.
 *  - If both are valid, prefer the one falling into SWISS_BBOX.
 *  - Else if only swapped is valid → swap.
 *  - Else throw to surface the data issue.
 */
export function normalizeToLatLng(a: number, b: number): LatLng {
  const asLatLng: LatLng = [a, b];
  const asLngLat: LatLng = [b, a];

  const asLatLngValid = inWorldRange(asLatLng[0], asLatLng[1]);
  const asLngLatValid = inWorldRange(asLngLat[0], asLngLat[1]);

  if (asLatLngValid && !asLngLatValid) return asLatLng;
  if (!asLatLngValid && asLngLatValid) return asLngLat;

  if (asLatLngValid && asLngLatValid) {
    const asLatLngSwiss = inBbox(asLatLng[0], asLatLng[1]);
    const asLngLatSwiss = inBbox(asLngLat[0], asLngLat[1]);
    if (asLatLngSwiss && !asLngLatSwiss) return asLatLng;
    if (!asLngLatSwiss && asLngLatSwiss) return asLngLat;
    return asLatLng; // both plausible → default to (lat,lng)
  }

  throw new Error(`Invalid coordinates from server: (${a}, ${b})`);
}
