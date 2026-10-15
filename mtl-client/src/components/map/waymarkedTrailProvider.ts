import type { MapTargetCandidate } from './mapInteractionDecision';
import { MapInteractionProviderError } from './mapInteractionCoordinator';

export type WaymarkedTrailActivity = 'hiking' | 'cycling' | 'mtb';
type Coordinate = [number, number];
export type WaymarkedTrailQuery = {
  point: { x: number; y: number };
  tolerancePx: number;
  unproject: (point: Coordinate) => { lng: number; lat: number };
};
export type WaymarkedTrailFootprint = {
  polygon: Coordinate[];
  bounds: [number, number, number, number];
  center: Coordinate;
};
type GeometryCollection = GeoJSON.FeatureCollection;
type RouteMetadata = {
  id?: unknown;
  name?: unknown;
  ref?: unknown;
  itinerary?: unknown;
  group?: unknown;
  symbol_description?: unknown;
};

const EARTH_RADIUS_METERS = 6_378_137;
const MERCATOR_MAX_LATITUDE = 85.0511287798066;
// The API defines geometry tiles only at zoom 12, using this Mercator half-world value.
const WAYMARKED_HALF_WORLD_METERS = 20_037_508.34;
const WAYMARKED_TILE_ZOOM = 12;
const WAYMARKED_TILE_COUNT = 2 ** WAYMARKED_TILE_ZOOM;
const WAYMARKED_TILE_WIDTH_METERS = (WAYMARKED_HALF_WORLD_METERS * 2) / WAYMARKED_TILE_COUNT;
const WAYMARKED_FOOTPRINT_SAMPLES = 64;
const WAYMARKED_MAX_QUERY_TILES = 16;
const WAYMARKED_METADATA_BATCH_SIZE = 100;
const WAYMARKED_CACHE_MAX_TILES = 32;
const WAYMARKED_CACHE_TTL_MS = 5 * 60_000;
const GEOMETRY_EPSILON = 1e-8;
const ACTIVITY_LABELS: Record<WaymarkedTrailActivity, string> = {
  hiking: 'Hiking',
  cycling: 'Cycling',
  mtb: 'Mountain biking',
};
// Public backend Network enum; other group values can be internal rendering classes.
const NETWORK_LABELS: Readonly<Record<string, string>> = {
  INT: 'International',
  NAT: 'National',
  REG: 'Regional',
  LOC: 'Local',
};
const tileCache = new Map<string, { value: GeometryCollection; expires: number }>();

export class WaymarkedTrailQueryTooWideError extends MapInteractionProviderError {
  constructor() {
    super(
      'Waymarked Trails identification footprint spans too many geometry tiles',
      'Zoom in to identify Waymarked Trails routes'
    );
    this.name = 'WaymarkedTrailQueryTooWideError';
  }
}

export function clearWaymarkedTrailCache(): void {
  tileCache.clear();
}

export function toWaymarkedMercator(lngLat: { lng: number; lat: number }): Coordinate {
  if (!Number.isFinite(lngLat.lng) || !Number.isFinite(lngLat.lat)) throw new Error('Invalid map coordinates');
  const latitude = Math.max(-MERCATOR_MAX_LATITUDE, Math.min(MERCATOR_MAX_LATITUDE, lngLat.lat));
  return [
    (lngLat.lng * Math.PI * EARTH_RADIUS_METERS) / 180,
    EARTH_RADIUS_METERS * Math.log(Math.tan(Math.PI / 4 + (latitude * Math.PI) / 360)),
  ];
}

function longitudeNear(longitude: number, anchor: number): number {
  return longitude + Math.round((anchor - longitude) / 360) * 360;
}

/** Capture the public map unprojection before awaiting requests or changing the camera. */
export function captureWaymarkedTrailFootprint(query: WaymarkedTrailQuery): WaymarkedTrailFootprint {
  if (!Number.isFinite(query.tolerancePx) || query.tolerancePx <= 0) throw new Error('Invalid map hit tolerance');
  const centerLngLat = query.unproject([query.point.x, query.point.y]);
  const polygon: Coordinate[] = [];
  // Circumscribe the tolerance circle: approximation adds at most 0.03 px at touch tolerance.
  const radius = query.tolerancePx / Math.cos(Math.PI / WAYMARKED_FOOTPRINT_SAMPLES);
  for (let index = 0; index < WAYMARKED_FOOTPRINT_SAMPLES; index++) {
    const angle = (index * Math.PI * 2) / WAYMARKED_FOOTPRINT_SAMPLES;
    const lngLat = query.unproject([
      query.point.x + Math.cos(angle) * radius,
      query.point.y + Math.sin(angle) * radius,
    ]);
    polygon.push(toWaymarkedMercator({ lng: longitudeNear(lngLat.lng, centerLngLat.lng), lat: lngLat.lat }));
  }
  return {
    polygon,
    center: toWaymarkedMercator(centerLngLat),
    bounds: [
      Math.min(...polygon.map(([x]) => x)),
      Math.min(...polygon.map(([, y]) => y)),
      Math.max(...polygon.map(([x]) => x)),
      Math.max(...polygon.map(([, y]) => y)),
    ],
  };
}

/** Include adjacent tiles at a boundary and wrap longitude across the antimeridian. */
export function waymarkedFootprintTiles(footprint: WaymarkedTrailFootprint): Array<{ x: number; y: number }> {
  const [minX, minY, maxX, maxY] = footprint.bounds;
  const firstX = Math.floor((minX + WAYMARKED_HALF_WORLD_METERS) / WAYMARKED_TILE_WIDTH_METERS);
  const lastX = Math.floor((maxX + WAYMARKED_HALF_WORLD_METERS) / WAYMARKED_TILE_WIDTH_METERS);
  const firstY = Math.max(0, Math.floor((WAYMARKED_HALF_WORLD_METERS - maxY) / WAYMARKED_TILE_WIDTH_METERS));
  const lastY = Math.min(
    WAYMARKED_TILE_COUNT - 1,
    Math.floor((WAYMARKED_HALF_WORLD_METERS - minY) / WAYMARKED_TILE_WIDTH_METERS)
  );
  if ((lastX - firstX + 1) * (lastY - firstY + 1) > WAYMARKED_MAX_QUERY_TILES)
    throw new WaymarkedTrailQueryTooWideError();
  const tiles: Array<{ x: number; y: number }> = [];
  for (let x = firstX; x <= lastX; x++) {
    for (let y = firstY; y <= lastY; y++) {
      tiles.push({ x: ((x % WAYMARKED_TILE_COUNT) + WAYMARKED_TILE_COUNT) % WAYMARKED_TILE_COUNT, y });
    }
  }
  return tiles;
}

function cross(a: Coordinate, b: Coordinate, c: Coordinate): number {
  return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
}

function onSegment(a: Coordinate, b: Coordinate, point: Coordinate): boolean {
  return (
    Math.abs(cross(a, b, point)) <= GEOMETRY_EPSILON &&
    point[0] >= Math.min(a[0], b[0]) - GEOMETRY_EPSILON &&
    point[0] <= Math.max(a[0], b[0]) + GEOMETRY_EPSILON &&
    point[1] >= Math.min(a[1], b[1]) - GEOMETRY_EPSILON &&
    point[1] <= Math.max(a[1], b[1]) + GEOMETRY_EPSILON
  );
}

function segmentsIntersect(a: Coordinate, b: Coordinate, c: Coordinate, d: Coordinate): boolean {
  const first = cross(a, b, c);
  const second = cross(a, b, d);
  const third = cross(c, d, a);
  const fourth = cross(c, d, b);
  return (
    (first * second < 0 && third * fourth < 0) ||
    onSegment(a, b, c) ||
    onSegment(a, b, d) ||
    onSegment(c, d, a) ||
    onSegment(c, d, b)
  );
}

function pointInFootprint(point: Coordinate, polygon: Coordinate[]): boolean {
  let inside = false;
  for (let index = 0, previous = polygon.length - 1; index < polygon.length; previous = index++) {
    const a = polygon[previous]!;
    const b = polygon[index]!;
    if (onSegment(a, b, point)) return true;
    if (a[1] > point[1] !== b[1] > point[1] && point[0] < ((b[0] - a[0]) * (point[1] - a[1])) / (b[1] - a[1]) + a[0])
      inside = !inside;
  }
  return inside;
}

function lineHitsFootprint(coordinates: GeoJSON.Position[], footprint: WaymarkedTrailFootprint): boolean {
  const [minX, minY, maxX, maxY] = footprint.bounds;
  let previous: Coordinate | null = null;
  for (const coordinate of coordinates) {
    if (!Number.isFinite(coordinate[0]) || !Number.isFinite(coordinate[1])) {
      previous = null;
      continue;
    }
    const x =
      coordinate[0]! +
      Math.round((footprint.center[0] - coordinate[0]!) / (2 * WAYMARKED_HALF_WORLD_METERS)) *
        (2 * WAYMARKED_HALF_WORLD_METERS);
    const current: Coordinate = [x, coordinate[1]!];
    if (
      previous &&
      Math.max(previous[0], x) >= minX &&
      Math.min(previous[0], x) <= maxX &&
      Math.max(previous[1], current[1]) >= minY &&
      Math.min(previous[1], current[1]) <= maxY
    ) {
      if (
        pointInFootprint(previous, footprint.polygon) ||
        pointInFootprint(current, footprint.polygon) ||
        footprint.polygon.some((edge, index) =>
          segmentsIntersect(previous!, current, edge, footprint.polygon[(index + 1) % footprint.polygon.length]!)
        )
      )
        return true;
    }
    previous = current;
  }
  return false;
}

/** Raw geometry identifies every top-level route on the hit ways; labels and guideposts do not. */
export function waymarkedRelationsAtFootprint(
  collection: GeometryCollection,
  footprint: WaymarkedTrailFootprint
): number[] {
  const relations = new Set<number>();
  for (const feature of collection.features) {
    if (feature.properties?.type !== 'way' || !Array.isArray(feature.properties.top_relations)) continue;
    const geometry = feature.geometry;
    const lines =
      geometry.type === 'LineString'
        ? [geometry.coordinates]
        : geometry.type === 'MultiLineString'
          ? geometry.coordinates
          : [];
    if (!lines.some((line) => lineHitsFootprint(line, footprint))) continue;
    for (const relation of feature.properties.top_relations) {
      if (typeof relation === 'number' && Number.isSafeInteger(relation) && relation > 0) relations.add(relation);
    }
  }
  return [...relations].sort((left, right) => left - right);
}

function text(value: unknown): string | undefined {
  return typeof value === 'string' && value.trim() ? value.trim() : undefined;
}

export function waymarkedRouteCandidate(
  activity: WaymarkedTrailActivity,
  id: number,
  metadata: RouteMetadata = {}
): MapTargetCandidate {
  const reference = text(metadata.ref);
  const itinerary = Array.isArray(metadata.itinerary)
    ? metadata.itinerary.map(text).filter(Boolean).join(' → ') || undefined
    : text(metadata.itinerary);
  const group = text(metadata.group);
  const network = group && Object.hasOwn(NETWORK_LABELS, group) ? NETWORK_LABELS[group] : undefined;
  const name = text(metadata.name) || reference || itinerary || text(metadata.symbol_description) || `Route #${id}`;
  return {
    id: String(id),
    kind: 'overlay',
    source: `waymarked:${activity}`,
    sourceLabel: `Waymarked Trails · ${ACTIVITY_LABELS[activity]}`,
    label: name,
    detail: [reference && reference !== name ? reference : null, network].filter(Boolean).join(' · ') || undefined,
    match: 'identified',
    route: {
      name,
      type: ACTIVITY_LABELS[activity],
      icon: activity === 'hiking' ? 'bi bi-signpost-2' : 'bi bi-bicycle',
      reference,
      itinerary,
      network,
      detailsUrl: `https://${activity}.waymarkedtrails.org/#route?id=${id}&type=relation`,
    },
  };
}

async function requestJson(url: string, signal: AbortSignal): Promise<unknown> {
  signal.throwIfAborted();
  const response = await fetch(url, { signal });
  signal.throwIfAborted();
  if (!response.ok) throw new Error(`Waymarked Trails identification failed (${response.status})`);
  const value: unknown = await response.json();
  signal.throwIfAborted();
  return value;
}

async function geometryTile(base: string, x: number, y: number, signal: AbortSignal): Promise<GeometryCollection> {
  signal.throwIfAborted();
  const key = `${base}/tiles/${WAYMARKED_TILE_ZOOM}/${x}/${y}.json`;
  const cached = tileCache.get(key);
  if (cached && cached.expires > Date.now()) {
    tileCache.delete(key);
    tileCache.set(key, cached);
    return cached.value;
  }
  const value = (await requestJson(key, signal)) as Partial<GeometryCollection>;
  if (value.type !== 'FeatureCollection' || !Array.isArray(value.features))
    throw new Error('Invalid Waymarked Trails geometry response');
  tileCache.delete(key);
  tileCache.set(key, { value: value as GeometryCollection, expires: Date.now() + WAYMARKED_CACHE_TTL_MS });
  while (tileCache.size > WAYMARKED_CACHE_MAX_TILES) tileCache.delete(tileCache.keys().next().value!);
  return value as GeometryCollection;
}

/** One activity settles independently; the map coordinator supplies cancellation and its timeout. */
export async function identifyWaymarkedTrails(
  activity: WaymarkedTrailActivity,
  query: WaymarkedTrailQuery,
  signal: AbortSignal
): Promise<MapTargetCandidate[]> {
  signal.throwIfAborted();
  const footprint = captureWaymarkedTrailFootprint(query);
  const tiles = waymarkedFootprintTiles(footprint);
  const base = `https://${activity}.waymarkedtrails.org/api/v1`;
  const ids = new Set<number>();
  // Small sequential requests respect the public service; completed tiles are bounded and cached.
  for (const { x, y } of tiles) {
    const collection = await geometryTile(base, x, y, signal);
    signal.throwIfAborted();
    for (const id of waymarkedRelationsAtFootprint(collection, footprint)) ids.add(id);
  }
  const sortedIds = [...ids].sort((left, right) => left - right);
  const metadata = new Map<number, RouteMetadata>();
  // by_ids has no server result cap; batching controls URL length, never the returned target count.
  for (let offset = 0; offset < sortedIds.length; offset += WAYMARKED_METADATA_BATCH_SIZE) {
    const batch = sortedIds.slice(offset, offset + WAYMARKED_METADATA_BATCH_SIZE);
    const value = (await requestJson(`${base}/list/by_ids?relations=${batch.join(',')}`, signal)) as {
      results?: RouteMetadata[];
    };
    if (!Array.isArray(value.results)) throw new Error('Invalid Waymarked Trails route response');
    for (const item of value.results) if (typeof item.id === 'number' && ids.has(item.id)) metadata.set(item.id, item);
  }
  signal.throwIfAborted();
  return sortedIds.map((id) => waymarkedRouteCandidate(activity, id, metadata.get(id)));
}
