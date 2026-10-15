import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  captureWaymarkedTrailFootprint,
  clearWaymarkedTrailCache,
  identifyWaymarkedTrails,
  toWaymarkedMercator,
  waymarkedFootprintTiles,
  waymarkedRelationsAtFootprint,
  waymarkedRouteCandidate,
  WaymarkedTrailQueryTooWideError,
  type WaymarkedTrailQuery,
} from '@/components/map/waymarkedTrailProvider';
import { MapInteractionCoordinator, MapInteractionProviderError } from '@/components/map/mapInteractionCoordinator';

const EARTH_RADIUS = 6_378_137;
const ORIGIN = [5000, 5000] as const;
function lngLat(x: number, y: number) {
  return { lng: (x * 180) / (Math.PI * EARTH_RADIUS), lat: (Math.atan(Math.sinh(y / EARTH_RADIUS)) * 180) / Math.PI };
}
function query(scale = 1, origin: readonly [number, number] = ORIGIN): WaymarkedTrailQuery {
  return {
    point: { x: 0, y: 0 },
    tolerancePx: 16,
    unproject: ([x, y]) => lngLat(origin[0] + x * scale, origin[1] + y * scale),
  };
}
function way(ids: number[], coordinates: number[][]): GeoJSON.Feature<GeoJSON.LineString> {
  return {
    type: 'Feature',
    geometry: { type: 'LineString', coordinates },
    properties: { type: 'way', top_relations: ids, child_relations: [999] },
  };
}
const crossingWay = (ids: number[]) =>
  way(ids, [
    [4980, 5000],
    [5020, 5000],
  ]);
const collection = (features: GeoJSON.Feature[] = []): GeoJSON.FeatureCollection => ({
  type: 'FeatureCollection',
  features,
});
const response = (value: unknown, status = 200): Response =>
  ({ ok: status === 200, status, json: async () => value }) as Response;

function serve(
  geometry: GeoJSON.FeatureCollection,
  metadata: (id: number) => Record<string, unknown> = (id) => ({ id, name: `Route ${id}` })
) {
  const fetch = vi.fn(async (url: string) => {
    if (url.includes('/tiles/')) return response(geometry);
    const ids = new URL(url).searchParams.get('relations')!.split(',').map(Number);
    return response({ results: ids.map(metadata) });
  });
  vi.stubGlobal('fetch', fetch);
  return fetch;
}

beforeEach(() => clearWaymarkedTrailCache());
afterEach(() => {
  clearWaymarkedTrailCache();
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

describe('Waymarked Trails captured screen matching', () => {
  it('converts longitude/latitude into the API Mercator coordinates', () => {
    expect(toWaymarkedMercator({ lng: 0, lat: 0 })[0]).toBe(0);
    expect(toWaymarkedMercator(lngLat(5000, 6000))[0]).toBeCloseTo(5000, 6);
    expect(toWaymarkedMercator(lngLat(5000, 6000))[1]).toBeCloseTo(6000, 6);
    expect(() => toWaymarkedMercator({ lng: NaN, lat: 0 })).toThrow();
    expect(toWaymarkedMercator({ lng: 0, lat: 90 })[1]).toBeCloseTo(Math.PI * EARTH_RADIUS, 4);
  });

  it('identifies nearby line segments, including crossings whose vertices lie outside the tolerance', () => {
    const footprint = captureWaymarkedTrailFootprint(query());
    const geometry = collection([
      crossingWay([1, 2]),
      way(
        [3],
        [
          [5000, 5015],
          [5004, 5015],
        ]
      ),
      way(
        [4],
        [
          [4980, 5017],
          [5020, 5017],
        ]
      ),
      way(
        [5],
        [
          [4999, 4999],
          [5001, 5001],
        ]
      ),
    ]);
    expect(waymarkedRelationsAtFootprint(geometry, footprint)).toEqual([1, 2, 3, 5]);
  });

  it('includes the actual pointer boundary with a bounded subpixel approximation', () => {
    const footprint = captureWaymarkedTrailFootprint(query());
    expect(
      waymarkedRelationsAtFootprint(
        collection([
          way(
            [1],
            [
              [4980, 5016],
              [5020, 5016],
            ]
          ),
          way(
            [2],
            [
              [4980, 5016.1],
              [5020, 5016.1],
            ]
          ),
        ]),
        footprint
      )
    ).toEqual([1]);
  });

  it('handles multilines without joining separate parts or identifying guideposts', () => {
    const geometry = collection([
      {
        type: 'Feature',
        properties: { type: 'way', top_relations: [7] },
        geometry: {
          type: 'MultiLineString',
          coordinates: [
            [
              [4970, 5000],
              [4980, 5000],
            ],
            [
              [5020, 5000],
              [5030, 5000],
            ],
          ],
        },
      },
      {
        type: 'Feature',
        properties: { type: 'way', top_relations: [6] },
        geometry: {
          type: 'MultiLineString',
          coordinates: [
            [
              [4980, 5000],
              [5020, 5000],
            ],
          ],
        },
      },
      {
        type: 'Feature',
        properties: { type: 'guidepost', top_relations: [8] },
        geometry: { type: 'Point', coordinates: [5000, 5000] },
      },
      crossingWay([2, 2, -1, 0, NaN]),
    ]);
    expect(waymarkedRelationsAtFootprint(geometry, captureWaymarkedTrailFootprint(query()))).toEqual([2, 6]);
  });

  it('captures a pitched/rotated public unprojection before any camera change', () => {
    let cameraScale = 1;
    const projectedQuery: WaymarkedTrailQuery = {
      point: { x: 0, y: 0 },
      tolerancePx: 16,
      unproject: ([x, y]) =>
        lngLat(5000 + ((x - y) * cameraScale) / (1 + y / 800), 5000 + ((x + y) * 2 * cameraScale) / (1 + y / 800)),
    };
    const footprint = captureWaymarkedTrailFootprint(projectedQuery);
    const screenLine = [
      [-20, 0],
      [20, 0],
    ].map(([x, y]) => toWaymarkedMercator(projectedQuery.unproject([x!, y!])));
    cameraScale = 100;
    expect(waymarkedRelationsAtFootprint(collection([way([12], screenLine)]), footprint)).toEqual([12]);
    expect(footprint.bounds[2] - footprint.bounds[0]).toBeLessThan(100);
  });

  it('requests all adjacent tiles at click boundaries and wraps the antimeridian', () => {
    expect(waymarkedFootprintTiles(captureWaymarkedTrailFootprint(query(1, [0, 0])))).toHaveLength(4);
    const crossing = query(1, [Math.PI * EARTH_RADIUS, 5000]);
    const tiles = waymarkedFootprintTiles(captureWaymarkedTrailFootprint(crossing));
    expect(tiles.map((tile) => tile.x).sort((left, right) => left - right)).toEqual([0, 4095]);
    const footprint = captureWaymarkedTrailFootprint(crossing);
    expect(
      waymarkedRelationsAtFootprint(
        collection([
          way(
            [18],
            [
              [-Math.PI * EARTH_RADIUS, 4980],
              [-Math.PI * EARTH_RADIUS, 5020],
            ]
          ),
        ]),
        footprint
      )
    ).toEqual([18]);
  });

  it('rejects a broad footprint explicitly instead of downloading or truncating thousands of tiles', () => {
    expect(() => waymarkedFootprintTiles(captureWaymarkedTrailFootprint(query(100_000)))).toThrow(
      WaymarkedTrailQueryTooWideError
    );
    expect(new WaymarkedTrailQueryTooWideError().displayMessage).toBe('Zoom in to identify Waymarked Trails routes');
  });
});

describe('Waymarked Trails candidate identity and metadata', () => {
  it('uses name, reference, itinerary, symbol, and relation ID fallbacks', () => {
    expect(waymarkedRouteCandidate('hiking', 1, { name: ' Ridge walk ', ref: 'H1' }).label).toBe('Ridge walk');
    expect(waymarkedRouteCandidate('cycling', 2, { ref: ' 22 ' }).label).toBe('22');
    expect(waymarkedRouteCandidate('hiking', 10670744, { itinerary: ['Town A', 'Town B'] }).label).toBe(
      'Town A → Town B'
    );
    expect(waymarkedRouteCandidate('mtb', 3, { symbol_description: 'Blue triangle' }).label).toBe('Blue triangle');
    expect(waymarkedRouteCandidate('hiking', 4).label).toBe('Route #4');
  });

  it('preserves reference/itinerary/network and a validated relation-specific details link', () => {
    const candidate = waymarkedRouteCandidate('mtb', 33, {
      name: 'Woodland loop',
      ref: 'M3',
      itinerary: ['A', '', 'B'],
      group: 'REG',
    });
    expect(candidate.kind).toBe('overlay');
    if (candidate.kind !== 'overlay') throw new Error('Expected route candidate');
    expect(candidate.source).toBe('waymarked:mtb');
    expect(candidate.route).toMatchObject({
      reference: 'M3',
      itinerary: 'A → B',
      network: 'Regional',
      detailsUrl: 'https://mtb.waymarkedtrails.org/#route?id=33&type=relation',
    });
  });

  it.each([
    ['INT', 'International'],
    ['NAT', 'National'],
    ['REG', 'Regional'],
    ['LOC', 'Local'],
  ])('uses a readable label for documented network group %s', (group, label) => {
    const candidate = waymarkedRouteCandidate('hiking', 1, { group });
    if (candidate.kind !== 'overlay') throw new Error('Expected route candidate');
    expect(candidate.route.network).toBe(label);
  });

  it('omits the AL1 rendering group rather than presenting it as a route network', () => {
    const candidate = waymarkedRouteCandidate('hiking', 10670744, { itinerary: ['Town A', 'Town B'], group: 'AL1' });
    if (candidate.kind !== 'overlay') throw new Error('Expected route candidate');
    expect(candidate.route.network).toBeUndefined();
    expect(candidate.detail).toBeUndefined();
    expect(candidate.label).toBe('Town A → Town B');
  });

  it('does not confuse the same relation supplied by different activity layers', () => {
    expect(waymarkedRouteCandidate('hiking', 8).source).not.toBe(waymarkedRouteCandidate('cycling', 8).source);
  });
});

describe('Waymarked Trails independent cancellable provider', () => {
  it('deduplicates tile/way copies and fetches details only for true hits', async () => {
    const fetch = serve(
      collection([
        crossingWay([1, 2]),
        crossingWay([1]),
        way(
          [3],
          [
            [4980, 5030],
            [5020, 5030],
          ]
        ),
      ])
    );
    const candidates = await identifyWaymarkedTrails('hiking', query(), new AbortController().signal);
    expect(candidates.map((candidate) => candidate.id)).toEqual(['1', '2']);
    expect(fetch.mock.calls[1]?.[0]).toBe('https://hiking.waymarkedtrails.org/api/v1/list/by_ids?relations=1,2');
    expect(fetch.mock.calls.some(([url]) => url.includes('/by_area'))).toBe(false);
  });

  it('preserves every match beyond the area API cap, using metadata batches without a target limit', async () => {
    const ids = Array.from({ length: 205 }, (_, index) => index + 1);
    const fetch = serve(collection([crossingWay(ids)]));
    const candidates = await identifyWaymarkedTrails('cycling', query(), new AbortController().signal);
    expect(candidates).toHaveLength(205);
    const batches = fetch.mock.calls.filter(([url]) => url.includes('/by_ids'));
    expect(batches).toHaveLength(3);
    expect(batches.map(([url]) => new URL(url).searchParams.get('relations')!.split(',').length)).toEqual([
      100, 100, 5,
    ]);
    expect(candidates[204]?.id).toBe('205');
  });

  it('keeps a geometry-confirmed relation accessible if metadata omits it', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) =>
        response(url.includes('/tiles/') ? collection([crossingWay([44])]) : { results: [] })
      )
    );
    const candidates = await identifyWaymarkedTrails('hiking', query(), new AbortController().signal);
    expect(candidates[0]?.label).toBe('Route #44');
  });

  it('returns completed empty results without doing metadata requests', async () => {
    const fetch = serve(collection());
    expect(await identifyWaymarkedTrails('mtb', query(), new AbortController().signal)).toEqual([]);
    expect(fetch).toHaveBeenCalledTimes(1);
  });

  it('captures the original spatial footprint while a request ignores camera changes', async () => {
    let finish!: (value: Response) => void;
    const pending = new Promise<Response>((resolve) => {
      finish = resolve;
    });
    const fetch = vi.fn((url: string) =>
      url.includes('/tiles/') ? pending : Promise.resolve(response({ results: [{ id: 8, name: 'Original route' }] }))
    );
    vi.stubGlobal('fetch', fetch);
    let offset = 0;
    const click = query();
    const original = click.unproject;
    click.unproject = ([x, y]) => original([x + offset, y]);
    const operation = identifyWaymarkedTrails('hiking', click, new AbortController().signal);
    offset = 10_000;
    finish(response(collection([crossingWay([8])])));
    expect((await operation).map((candidate) => candidate.id)).toEqual(['8']);
  });

  it('checks cancellation before dispatch and after an operation ignoring abort resolves', async () => {
    const controller = new AbortController();
    let finish!: (value: Response) => void;
    const fetch = vi.fn(
      () =>
        new Promise<Response>((resolve) => {
          finish = resolve;
        })
    );
    vi.stubGlobal('fetch', fetch);
    const operation = identifyWaymarkedTrails('hiking', query(), controller.signal);
    controller.abort();
    finish(response(collection([crossingWay([1])])));
    await expect(operation).rejects.toMatchObject({ name: 'AbortError' });
    expect(fetch).toHaveBeenCalledTimes(1);
    await expect(identifyWaymarkedTrails('hiking', query(), controller.signal)).rejects.toMatchObject({
      name: 'AbortError',
    });
    expect(fetch).toHaveBeenCalledTimes(1);
  });

  it('bounds/cache-isolates completed tiles by activity and expires cached geometry', async () => {
    vi.useFakeTimers();
    const fetch = serve(collection([crossingWay([5])]));
    const signal = new AbortController().signal;
    await identifyWaymarkedTrails('hiking', query(), signal);
    await identifyWaymarkedTrails('hiking', query(), signal);
    expect(fetch.mock.calls.filter(([url]) => url.includes('/tiles/'))).toHaveLength(1);
    await identifyWaymarkedTrails('cycling', query(), signal);
    expect(fetch.mock.calls.filter(([url]) => url.includes('/tiles/'))).toHaveLength(2);
    vi.advanceTimersByTime(5 * 60_000 + 1);
    await identifyWaymarkedTrails('hiking', query(), signal);
    expect(fetch.mock.calls.filter(([url]) => url.includes('/tiles/'))).toHaveLength(3);
  });

  it('does not couple two in-flight requests to the first interaction abort signal', async () => {
    const finish: Array<(value: Response) => void> = [];
    const fetch = vi.fn((url: string) =>
      url.includes('/tiles/')
        ? new Promise<Response>((resolve) => {
            finish.push(resolve);
          })
        : Promise.resolve(response({ results: [{ id: 6, name: 'Route 6' }] }))
    );
    vi.stubGlobal('fetch', fetch);
    const firstController = new AbortController();
    const first = identifyWaymarkedTrails('hiking', query(), firstController.signal);
    const second = identifyWaymarkedTrails('hiking', query(), new AbortController().signal);
    firstController.abort();
    finish[0]!(response(collection([crossingWay([6])])));
    finish[1]!(response(collection([crossingWay([6])])));
    await expect(first).rejects.toMatchObject({ name: 'AbortError' });
    expect(await second).toHaveLength(1);
    expect(finish).toHaveLength(2);
  });

  it('evicts old tiles once the bounded cache fills instead of retaining every explored location', async () => {
    const fetch = serve(collection());
    const signal = new AbortController().signal;
    for (let index = 0; index < 33; index++)
      await identifyWaymarkedTrails('hiking', query(1, [5000 + index * 10_000, 5000]), signal);
    const count = fetch.mock.calls.length;
    await identifyWaymarkedTrails('hiking', query(1, [325000, 5000]), signal);
    expect(fetch).toHaveBeenCalledTimes(count);
    await identifyWaymarkedTrails('hiking', query(), signal);
    expect(fetch).toHaveBeenCalledTimes(count + 1);
  });

  it('checks cancellation after an already cached tile too', async () => {
    const fetch = serve(collection([crossingWay([5])]));
    await identifyWaymarkedTrails('hiking', query(), new AbortController().signal);
    const controller = new AbortController();
    const operation = identifyWaymarkedTrails('hiking', query(), controller.signal);
    controller.abort();
    await expect(operation).rejects.toMatchObject({ name: 'AbortError' });
    expect(fetch).toHaveBeenCalledTimes(2);
  });

  it('reports tile and metadata failures instead of converting them to zero hits', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => response({}, 503))
    );
    await expect(identifyWaymarkedTrails('hiking', query(), new AbortController().signal)).rejects.toThrow('503');
    clearWaymarkedTrailCache();
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => response(url.includes('/tiles/') ? collection([crossingWay([1])]) : {}))
    );
    await expect(identifyWaymarkedTrails('hiking', query(), new AbortController().signal)).rejects.toThrow(
      'Invalid Waymarked Trails route response'
    );
  });

  it('retains broad-query guidance in the chooser without fetching', async () => {
    const fetch = serve(collection());
    const coordinator = new MapInteractionCoordinator({ onChange: vi.fn(), onProviderError: vi.fn() });
    coordinator.start(
      {
        lngLat: { lng: 0, lat: 0 },
        point: { x: 0, y: 0 },
        inputKind: 'pointer',
        trackSetRevision: 1,
        mediaScope: 'allIndexed',
        eligibility: { overlays: true },
      },
      [],
      [
        {
          id: 'waymarked',
          label: 'Waymarked Trails',
          kind: 'overlay',
          run: async (_context, signal) => ({
            candidates: await identifyWaymarkedTrails('hiking', query(100_000), signal),
          }),
        },
      ]
    );
    await Promise.resolve();
    await Promise.resolve();
    await Promise.resolve();
    expect(coordinator.state?.sources[0]).toMatchObject({
      status: 'failed',
      message: 'Zoom in to identify Waymarked Trails routes',
    });
    expect(coordinator.state?.presentation).toBe('chooser');
    expect(fetch).not.toHaveBeenCalled();
  });
});

describe('safe provider display messages', () => {
  const context = {
    lngLat: { lng: 0, lat: 0 },
    point: { x: 0, y: 0 },
    inputKind: 'pointer' as const,
    trackSetRevision: 1,
    mediaScope: 'allIndexed',
    eligibility: {},
  };
  it('shows opted-in guidance for synchronous collector failures', () => {
    const coordinator = new MapInteractionCoordinator({ onChange: vi.fn(), onProviderError: vi.fn() });
    coordinator.registerConsumer({
      id: 'source',
      label: 'Layer',
      kind: 'local',
      collect: () => {
        throw new MapInteractionProviderError('Internal context', 'Zoom in to inspect this layer');
      },
    });
    coordinator.start(context);
    expect(coordinator.state?.sources[0].message).toBe('Zoom in to inspect this layer');
  });

  it('does not expose arbitrary caught error text to the interaction surface', async () => {
    const coordinator = new MapInteractionCoordinator({ onChange: vi.fn(), onProviderError: vi.fn() });
    coordinator.start(
      context,
      [],
      [
        {
          id: 'source',
          label: 'Layer',
          kind: 'overlay',
          run: async () => {
            throw new Error('Raw server exception with internal URL');
          },
        },
      ]
    );
    await Promise.resolve();
    await Promise.resolve();
    expect(coordinator.state?.sources[0].status).toBe('failed');
    expect(coordinator.state?.sources[0].message).toBeUndefined();
  });
});
