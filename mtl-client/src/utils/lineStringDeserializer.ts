import type { GpsTrackData } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';

/**
 * Server-contract escape hatch for `LineString` track geometry.
 *
 * The mtl-server uses a custom `LineStringSerializer` (see
 * `LineStringSerializer.java`) that emits track geometry as a *bare* array
 * of coordinate tuples:
 *
 *   "track": [[lng, lat, z?, m?], [lng, lat, z?, m?], ...]
 *
 * The OpenAPI spec, on the other hand, declares the field as a JTS-style
 * `LineString` object with a `.coordinates` property of `Coordinate` objects
 * (`{x, y}`). The generated `LineStringFromJSON` deserializer therefore
 * *destroys* the geometry on responses that were produced by the bulk
 * endpoints — it tries to read `.coordinates` on what is actually a raw
 * array.
 *
 * As a workaround, the bulk endpoints (`getTracksSimplified1Raw`,
 * `getSingleTrackRaw`) bypass the generated deserializer and route their
 * track payload through this helper, which transparently handles all three
 * shapes the field can have in the wild:
 *
 *   1. Bare array            — server custom serializer (live API).
 *   2. `{coordinates: [{x, y}, ...]}` — OpenAPI generated objects (tests / mocks).
 *   3. `{coordinates: [[lng, lat], ...]}` — older cached payloads in IndexedDB.
 *
 * See repo memory `openapi-linestring-deserialization-bug.md` for context;
 * the long-term fix is to make the server emit GeoJSON-shaped `LineString`
 * so the generated deserializer just works.
 */
export function extractCoordinates(
  trackDataList: GpsTrackData[] | undefined | null
): number[][] {
  if (!trackDataList) return [];
  const out: number[][] = [];
  for (const td of trackDataList) {
    if (td == null || td.track == null) continue;
    const track = td.track as unknown;

    // Case 1: bare array from custom server serializer.
    if (Array.isArray(track)) {
      for (const c of track as unknown[]) {
        if (Array.isArray(c) && c.length >= 2) {
          out.push([c[0] as number, c[1] as number]);
        }
      }
      continue;
    }

    // Case 2 + 3: object with `.coordinates` array.
    const obj = track as { coordinates?: unknown };
    if (Array.isArray(obj.coordinates)) {
      for (const c of obj.coordinates as unknown[]) {
        if (c == null) continue;
        if (Array.isArray(c)) {
          out.push([c[0] as number, c[1] as number]);
        } else {
          const xy = c as { x?: number; y?: number };
          if (xy.x != null && xy.y != null) out.push([xy.x, xy.y]);
        }
      }
      continue;
    }

    console.warn(
      `Track data: unexpected track format (type=${typeof track}, isArray=${Array.isArray(
        track
      )}), skipping`
    );
  }
  return out;
}
