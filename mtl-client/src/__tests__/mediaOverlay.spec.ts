import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MediaOverlay } from '@/layers/MediaOverlay';
import { getMediaInBounds } from '@/repositories/mediaRepository';
import type { MediaBoundsPoint } from '@/repositories/mediaRepository';

const mediaRepositoryMock = vi.hoisted(() => ({
  getMediaInBounds: vi.fn(),
}));

const maplibreMock = vi.hoisted(() => {
  type LngLatLike = [number, number];

  class MockLngLatBounds {
    private readonly sw: { lng: number; lat: number };
    private readonly ne: { lng: number; lat: number };

    constructor(sw: LngLatLike, ne: LngLatLike) {
      assertValidLatitude(sw[1]);
      assertValidLatitude(ne[1]);
      this.sw = { lng: sw[0], lat: sw[1] };
      this.ne = { lng: ne[0], lat: ne[1] };
    }

    getSouthWest() {
      return this.sw;
    }

    getNorthEast() {
      return this.ne;
    }

    contains(point: { lng: number; lat: number }) {
      return (
        point.lng >= this.sw.lng && point.lng <= this.ne.lng && point.lat >= this.sw.lat && point.lat <= this.ne.lat
      );
    }
  }

  function assertValidLatitude(latitude: number): void {
    if (latitude < -90 || latitude > 90) {
      throw new Error('Invalid LngLat latitude value: must be between -90 and 90');
    }
  }

  return { LngLatBounds: MockLngLatBounds };
});

vi.mock('maplibre-gl', () => maplibreMock);

vi.mock('@/repositories/mediaRepository', () => mediaRepositoryMock);

describe('MediaOverlay', () => {
  beforeEach(() => {
    vi.mocked(getMediaInBounds).mockReset();
    vi.mocked(getMediaInBounds).mockResolvedValue([{ id: 1, lat: 47.48, lng: 7.52 }]);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('skips broad viewports and waits for the first bounded local viewport', async () => {
    vi.useFakeTimers();
    let bounds = {
      getSouthWest: () => ({ lng: -180, lat: -85 }),
      getNorthEast: () => ({ lng: 180, lat: 85 }),
    };
    const map = createMapMock(bounds);
    const overlay = new MediaOverlay(map, vi.fn());
    await overlay.show();

    expect(getMediaInBounds).not.toHaveBeenCalled();
    expect(overlay.getLoadedPoints()).toEqual([]);

    bounds = {
      getSouthWest: () => ({ lng: -120, lat: -40 }),
      getNorthEast: () => ({ lng: 120, lat: 70 }),
    };
    mapMockMethod(map, 'getBounds').mockImplementation(() => bounds);
    mapHandler(map, 'moveend')();
    await vi.runAllTimersAsync();

    expect(getMediaInBounds).not.toHaveBeenCalled();

    bounds = {
      getSouthWest: () => ({ lng: 7.5, lat: 47.4 }),
      getNorthEast: () => ({ lng: 7.6, lat: 47.5 }),
    };
    mapMockMethod(map, 'getBounds').mockImplementation(() => bounds);
    mapHandler(map, 'moveend')();
    await vi.runAllTimersAsync();

    expect(getMediaInBounds).toHaveBeenCalledTimes(1);
    const localFetchBounds = vi.mocked(getMediaInBounds).mock.calls[0]?.slice(0, 4) as number[];
    expect(localFetchBounds[0]).toBeCloseTo(47.2);
    expect(localFetchBounds[1]).toBeCloseTo(7.3);
    expect(localFetchBounds[2]).toBeCloseTo(47.7);
    expect(localFetchBounds[3]).toBeCloseTo(7.8);
  });

  it('keeps a local padded viewport cached across contained map moves', async () => {
    vi.useFakeTimers();
    let bounds = {
      getSouthWest: () => ({ lng: 7.5, lat: 47.4 }),
      getNorthEast: () => ({ lng: 7.6, lat: 47.5 }),
    };
    const map = createMapMock(bounds);
    const overlay = new MediaOverlay(map, vi.fn());
    await overlay.show();

    bounds = {
      getSouthWest: () => ({ lng: 7.52, lat: 47.42 }),
      getNorthEast: () => ({ lng: 7.58, lat: 47.48 }),
    };
    mapMockMethod(map, 'getBounds').mockImplementation(() => bounds);
    mapHandler(map, 'moveend')();
    await vi.runAllTimersAsync();

    expect(getMediaInBounds).toHaveBeenCalledTimes(1);
  });

  it('fetches another bounded response after moving from Bern to New York', async () => {
    vi.useFakeTimers();
    let bounds = {
      getSouthWest: () => ({ lng: 7.5, lat: 47.4 }),
      getNorthEast: () => ({ lng: 7.6, lat: 47.5 }),
    };
    const map = createMapMock(bounds);
    const overlay = new MediaOverlay(map, vi.fn());
    await overlay.show();

    bounds = {
      getSouthWest: () => ({ lng: -74.1, lat: 40.7 }),
      getNorthEast: () => ({ lng: -73.9, lat: 40.8 }),
    };
    mapMockMethod(map, 'getBounds').mockImplementation(() => bounds);
    mapHandler(map, 'moveend')();
    await vi.runAllTimersAsync();

    expect(getMediaInBounds).toHaveBeenCalledTimes(2);
    const newYorkFetchBounds = vi.mocked(getMediaInBounds).mock.calls[1]?.slice(0, 4) as number[];
    expect(newYorkFetchBounds[0]).toBeCloseTo(40.5);
    expect(newYorkFetchBounds[1]).toBeCloseTo(-74.5);
    expect(newYorkFetchBounds[2]).toBeCloseTo(41);
    expect(newYorkFetchBounds[3]).toBeCloseTo(-73.5);
  });

  it('clears local media without another request after zooming out to a broad viewport', async () => {
    vi.useFakeTimers();
    let bounds = {
      getSouthWest: () => ({ lng: 7.5, lat: 47.4 }),
      getNorthEast: () => ({ lng: 7.6, lat: 47.5 }),
    };
    const map = createMapMock(bounds);
    const onPointsUpdated = vi.fn();
    const overlay = new MediaOverlay(map, vi.fn(), onPointsUpdated);
    await overlay.show();

    bounds = {
      getSouthWest: () => ({ lng: -180, lat: -85 }),
      getNorthEast: () => ({ lng: 180, lat: 85 }),
    };
    mapMockMethod(map, 'getBounds').mockImplementation(() => bounds);
    mapHandler(map, 'moveend')();
    await vi.runAllTimersAsync();

    expect(getMediaInBounds).toHaveBeenCalledTimes(1);
    expect(overlay.getLoadedPoints()).toEqual([]);
    expect(onPointsUpdated).toHaveBeenLastCalledWith([]);
    expect(mapMockMethod(map, 'getSource')('media-points').setData).toHaveBeenLastCalledWith({
      type: 'FeatureCollection',
      features: [],
    });
  });

  it('keeps the latest local viewport when an older request resolves last', async () => {
    vi.useFakeTimers();
    let bounds = {
      getSouthWest: () => ({ lng: 7.5, lat: 47.4 }),
      getNorthEast: () => ({ lng: 7.6, lat: 47.5 }),
    };
    const map = createMapMock(bounds);
    const overlay = new MediaOverlay(map, vi.fn());
    await overlay.show();

    const newYorkResponse = deferred<MediaBoundsPoint[]>();
    const tokyoResponse = deferred<MediaBoundsPoint[]>();
    vi.mocked(getMediaInBounds)
      .mockImplementationOnce(() => newYorkResponse.promise)
      .mockImplementationOnce(() => tokyoResponse.promise);

    bounds = {
      getSouthWest: () => ({ lng: -74.1, lat: 40.7 }),
      getNorthEast: () => ({ lng: -73.9, lat: 40.8 }),
    };
    mapMockMethod(map, 'getBounds').mockImplementation(() => bounds);
    mapHandler(map, 'moveend')();
    await vi.runAllTimersAsync();

    bounds = {
      getSouthWest: () => ({ lng: 139.65, lat: 35.65 }),
      getNorthEast: () => ({ lng: 139.85, lat: 35.75 }),
    };
    mapHandler(map, 'moveend')();
    await vi.runAllTimersAsync();

    const tokyoPoints = [{ id: 3, lat: 35.7, lng: 139.75 }];
    tokyoResponse.resolve(tokyoPoints);
    await flushMicrotasks();
    expect(overlay.getLoadedPoints()).toEqual(tokyoPoints);

    newYorkResponse.resolve([{ id: 2, lat: 40.75, lng: -74 }]);
    await flushMicrotasks();
    expect(overlay.getLoadedPoints()).toEqual(tokyoPoints);
  });

  it('does not repopulate cleared media when a superseded local request resolves in a broad view', async () => {
    vi.useFakeTimers();
    let bounds = {
      getSouthWest: () => ({ lng: 7.5, lat: 47.4 }),
      getNorthEast: () => ({ lng: 7.6, lat: 47.5 }),
    };
    const map = createMapMock(bounds);
    const overlay = new MediaOverlay(map, vi.fn());
    await overlay.show();

    const newYorkResponse = deferred<MediaBoundsPoint[]>();
    vi.mocked(getMediaInBounds).mockImplementationOnce(() => newYorkResponse.promise);
    bounds = {
      getSouthWest: () => ({ lng: -74.1, lat: 40.7 }),
      getNorthEast: () => ({ lng: -73.9, lat: 40.8 }),
    };
    mapMockMethod(map, 'getBounds').mockImplementation(() => bounds);
    mapHandler(map, 'moveend')();
    await vi.runAllTimersAsync();

    bounds = {
      getSouthWest: () => ({ lng: -180, lat: -85 }),
      getNorthEast: () => ({ lng: 180, lat: 85 }),
    };
    mapHandler(map, 'moveend')();
    await vi.runAllTimersAsync();
    expect(overlay.getLoadedPoints()).toEqual([]);

    newYorkResponse.resolve([{ id: 2, lat: 40.75, lng: -74 }]);
    await flushMicrotasks();

    expect(getMediaInBounds).toHaveBeenCalledTimes(2);
    expect(overlay.getLoadedPoints()).toEqual([]);
    expect(mapMockMethod(map, 'getSource')('media-points').setData).toHaveBeenLastCalledWith({
      type: 'FeatureCollection',
      features: [],
    });
  });

  it('forces a reload when the current viewport is already cached', async () => {
    const bounds = {
      getSouthWest: () => ({ lng: 7.5, lat: 47.4 }),
      getNorthEast: () => ({ lng: 7.6, lat: 47.5 }),
    };
    const map = createMapMock(bounds);
    const onPointsUpdated = vi.fn();
    vi.mocked(getMediaInBounds)
      .mockResolvedValueOnce([{ id: 1, lat: 47.48, lng: 7.52 }])
      .mockResolvedValueOnce([{ id: 2, lat: 47.49, lng: 7.53 }]);

    const overlay = new MediaOverlay(map, vi.fn(), onPointsUpdated);
    await overlay.show();
    await overlay.refresh();

    expect(getMediaInBounds).toHaveBeenCalledTimes(2);
    expect(onPointsUpdated).toHaveBeenLastCalledWith([{ id: 2, lat: 47.49, lng: 7.53 }]);
  });

  it('rejects a forced refresh when the media request fails', async () => {
    const map = createMapMock({
      getSouthWest: () => ({ lng: 7.5, lat: 47.4 }),
      getNorthEast: () => ({ lng: 7.6, lat: 47.5 }),
    });
    const refreshError = new Error('media unavailable');
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    vi.mocked(getMediaInBounds)
      .mockResolvedValueOnce([{ id: 1, lat: 47.48, lng: 7.52 }])
      .mockRejectedValueOnce(refreshError);

    const overlay = new MediaOverlay(map, vi.fn());
    await overlay.show();

    await expect(overlay.refresh()).rejects.toBe(refreshError);
    expect(consoleError).toHaveBeenCalled();
    consoleError.mockRestore();
  });

  it('prepares a bounded cluster collection and the current map view without changing the map zoom', async () => {
    const bounds = {
      getSouthWest: () => ({ lng: 7.5, lat: 47.4 }),
      getNorthEast: () => ({ lng: 7.6, lat: 47.5 }),
    };
    const clusterLeaves: GeoJSON.Feature<GeoJSON.Point>[] = [
      { type: 'Feature', geometry: { type: 'Point', coordinates: [7.52, 47.48] }, properties: { mediaId: 2 } },
      { type: 'Feature', geometry: { type: 'Point', coordinates: [7.53, 47.49] }, properties: { mediaId: 3 } },
      { type: 'Feature', geometry: { type: 'Point', coordinates: [7.52, 47.48] }, properties: { mediaId: 2 } },
    ];
    const map = createMapMock(bounds, clusterLeaves);
    const onMediaSelect = vi.fn();
    const overlay = new MediaOverlay(map, onMediaSelect);
    await overlay.show();
    mapMockMethod(map, 'queryRenderedFeatures').mockReturnValue([
      {
        type: 'Feature',
        geometry: { type: 'Point', coordinates: [7.525, 47.485] },
        properties: { cluster_id: 17, point_count: 3 },
      },
    ]);

    layerHandler(
      map,
      'click',
      'media-clusters'
    )({
      point: { x: 100, y: 80 },
      lngLat: { lng: 7.525, lat: 47.485 },
    });

    await vi.waitFor(() =>
      expect(onMediaSelect).toHaveBeenCalledWith(
        expect.objectContaining({
          selectedMediaId: 2,
          mediaIds: [2, 3],
          totalMediaCount: 3,
          clusterId: 17,
          kind: 'cluster',
          clickPoint: { x: 100, y: 80 },
          clickLngLat: { lng: 7.525, lat: 47.485 },
        })
      )
    );
    expect(mapMockMethod(map, 'getSource')('media-points').getClusterLeaves).toHaveBeenCalledWith(17, 200, 0);
    expect(mapMockMethod(map, 'easeTo')).not.toHaveBeenCalled();
  });

  it('prepares the clicked location and current map view from an unclustered photo', async () => {
    const map = createMapMock({
      getSouthWest: () => ({ lng: 7.5, lat: 47.4 }),
      getNorthEast: () => ({ lng: 7.6, lat: 47.5 }),
    });
    const onMediaSelect = vi.fn();
    vi.mocked(getMediaInBounds).mockResolvedValue([
      { id: 9, lat: 47.45, lng: 7.55 },
      { id: 10, lat: 47.451, lng: 7.55 },
      { id: 11, lat: 47.4, lng: 7.5 },
      { id: 12, lat: 47.5, lng: 7.6 },
      { id: 13, lat: 47.45, lng: 7.4999 },
      { id: 14, lat: 47.45, lng: 7.6001 },
    ]);
    const overlay = new MediaOverlay(map, onMediaSelect);
    await overlay.show();

    layerHandler(
      map,
      'click',
      'media-unclustered'
    )({
      features: [{ properties: { mediaId: '9' } }, { properties: { mediaId: 10 } }, { properties: { mediaId: 9 } }],
      point: { x: 75, y: 60 },
      lngLat: { lng: 7.55, lat: 47.45 },
    });

    expect(onMediaSelect).toHaveBeenCalledWith(
      expect.objectContaining({
        selectedMediaId: 9,
        mediaIds: [9, 10],
        mediaPoints: [
          { id: 9, lat: 47.45, lng: 7.55 },
          { id: 10, lat: 47.451, lng: 7.55 },
        ],
        totalMediaCount: 2,
        clusterId: null,
        kind: 'location',
        viewportMediaPoints: expect.arrayContaining([
          { id: 9, lat: 47.45, lng: 7.55 },
          { id: 10, lat: 47.451, lng: 7.55 },
          { id: 11, lat: 47.4, lng: 7.5 },
          { id: 12, lat: 47.5, lng: 7.6 },
        ]),
        clickPoint: { x: 75, y: 60 },
        clickLngLat: { lng: 7.55, lat: 47.45 },
      })
    );
  });

  it('ignores photo interactions while a map tool owns clicks and restores them afterward', async () => {
    const map = createMapMock({
      getSouthWest: () => ({ lng: 7.5, lat: 47.4 }),
      getNorthEast: () => ({ lng: 7.6, lat: 47.5 }),
    });
    const onMediaSelect = vi.fn();
    let interactionEnabled = false;
    const overlay = new MediaOverlay(map, onMediaSelect, undefined, () => interactionEnabled);
    await overlay.show();
    const mediaClick = layerHandler(map, 'click', 'media-unclustered');
    const mediaMouseEnter = layerHandler(map, 'mouseenter', 'media-unclustered');
    const clickEvent = {
      features: [{ properties: { mediaId: 1 } }],
      point: { x: 75, y: 60 },
      lngLat: { lng: 7.52, lat: 47.48 },
    };

    mediaClick(clickEvent);
    mediaMouseEnter({});
    expect(onMediaSelect).not.toHaveBeenCalled();
    expect(mapMockMethod(map, 'getCanvas')().style.cursor).toBe('');

    interactionEnabled = true;
    mediaClick(clickEvent);
    mediaMouseEnter({});
    expect(onMediaSelect).toHaveBeenCalledOnce();
    expect(mapMockMethod(map, 'getCanvas')().style.cursor).toBe('pointer');
  });

  it('reduces the source to one point before focusing a large viewport', async () => {
    const map = createMapMock({
      getSouthWest: () => ({ lng: 7.5, lat: 47.4 }),
      getNorthEast: () => ({ lng: 7.6, lat: 47.5 }),
    });
    const overlay = new MediaOverlay(map, vi.fn());
    await overlay.show();

    overlay.prepareForFocus({ id: 44, lat: 47.48, lng: 7.52 });

    const setData = mapMockMethod(map, 'getSource')('media-points').setData;
    expect(setData).toHaveBeenLastCalledWith({
      type: 'FeatureCollection',
      features: [
        {
          type: 'Feature',
          geometry: { type: 'Point', coordinates: [7.52, 47.48] },
          properties: { mediaId: 44 },
        },
      ],
    });
  });
});

function createMapMock(
  bounds: {
    getSouthWest: () => { lng: number; lat: number };
    getNorthEast: () => { lng: number; lat: number };
  },
  clusterLeaves: GeoJSON.Feature<GeoJSON.Point>[] = []
) {
  const layers = new Set<string>();
  const sources = new Map<string, { setData: ReturnType<typeof vi.fn>; getClusterLeaves: ReturnType<typeof vi.fn> }>();
  const canvas = { style: { cursor: '' } };

  return {
    addSource: vi.fn((id: string) => {
      sources.set(id, {
        setData: vi.fn(),
        getClusterLeaves: vi.fn().mockResolvedValue(clusterLeaves),
      });
    }),
    addLayer: vi.fn((layer: { id: string }) => {
      layers.add(layer.id);
    }),
    getBounds: vi.fn(() => bounds),
    getCanvas: vi.fn(() => canvas),
    getLayer: vi.fn((id: string) => (layers.has(id) ? { id } : undefined)),
    getSource: vi.fn((id: string) => sources.get(id)),
    easeTo: vi.fn(),
    off: vi.fn(),
    on: vi.fn(),
    removeLayer: vi.fn((id: string) => {
      layers.delete(id);
    }),
    removeSource: vi.fn((id: string) => {
      sources.delete(id);
    }),
    queryRenderedFeatures: vi.fn(() => []),
  } as never;
}

function mapMockMethod(map: never, method: string): ReturnType<typeof vi.fn> {
  return (map as unknown as Record<string, ReturnType<typeof vi.fn>>)[method];
}

function layerHandler(map: never, eventName: string, layerId: string): (event: unknown) => void {
  const call = mapMockMethod(map, 'on').mock.calls.find(
    ([registeredEvent, registeredLayer]) => registeredEvent === eventName && registeredLayer === layerId
  );
  expect(call).toBeDefined();
  return call?.[2] as (event: unknown) => void;
}

function mapHandler(map: never, eventName: string): () => void {
  const call = mapMockMethod(map, 'on').mock.calls.find(
    ([registeredEvent, registeredLayer]) => registeredEvent === eventName && typeof registeredLayer === 'function'
  );
  expect(call).toBeDefined();
  return call?.[1] as () => void;
}

function deferred<T>(): {
  promise: Promise<T>;
  resolve: (value: T) => void;
} {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((promiseResolve) => {
    resolve = promiseResolve;
  });
  return { promise, resolve };
}

async function flushMicrotasks(): Promise<void> {
  await Promise.resolve();
  await Promise.resolve();
}
