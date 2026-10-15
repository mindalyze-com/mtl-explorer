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

  it('uses Swiss Topo fonts for media cluster counts', async () => {
    const map = createMapMock({
      getSouthWest: () => ({ lng: 7.5, lat: 47.4 }),
      getNorthEast: () => ({ lng: 7.6, lat: 47.5 }),
    });
    mapMockMethod(map, 'getGlyphs').mockReturnValue('https://vectortiles.geo.admin.ch/fonts/{fontstack}/{range}.pbf');
    const overlay = new MediaOverlay(map, vi.fn());
    await overlay.show();
    expect(mapMockMethod(map, 'addLayer')).toHaveBeenCalledWith(
      expect.objectContaining({
        id: 'media-cluster-count',
        layout: expect.objectContaining({ 'text-font': ['Frutiger Neue Regular'] }),
      })
    );
    overlay.hide();
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

  it('clears cached markers on filter changes and ignores the superseded response', async () => {
    const map = createMapMock({
      getSouthWest: () => ({ lng: 7.5, lat: 47.4 }),
      getNorthEast: () => ({ lng: 7.6, lat: 47.5 }),
    });
    const onPointsUpdated = vi.fn();
    const overlay = new MediaOverlay(map, vi.fn(), onPointsUpdated);
    await overlay.show();
    let resolveOld!: (points: MediaBoundsPoint[]) => void;
    vi.mocked(getMediaInBounds).mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveOld = resolve;
        })
    );
    const oldRefresh = overlay.refresh();
    const oldSignal = vi.mocked(getMediaInBounds).mock.calls.at(-1)?.[4];
    vi.mocked(getMediaInBounds).mockResolvedValueOnce([]);
    const filteredRefresh = overlay.refresh(true);
    expect(overlay.getLoadedPoints()).toEqual([]);
    expect(onPointsUpdated).toHaveBeenLastCalledWith([]);
    expect(oldSignal?.aborted).toBe(true);
    await filteredRefresh;
    resolveOld([{ id: 99, lat: 47.48, lng: 7.52 }]);
    await oldRefresh;
    expect(overlay.getLoadedPoints()).toEqual([]);
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

  it('collects one counted cluster target before leaves load and leaves presentation to the coordinator', async () => {
    const map = createMapMock(localBounds(), [photoFeature(2), photoFeature(3), photoFeature(2)]);
    const onMediaSelect = vi.fn();
    const overlay = new MediaOverlay(map, onMediaSelect);
    await overlay.show();
    mapMockMethod(map, 'unproject').mockReturnValue({ lng: 7.525, lat: 47.485 });
    const cluster = clusterFeature(17, 3);
    mapMockMethod(map, 'queryRenderedFeatures').mockReturnValue([cluster, cluster]);

    const candidates = overlay.collectCandidates({ x: 100, y: 80 }, 6);
    expect(candidates).toHaveLength(1);
    expect(candidates[0]).toMatchObject({ kind: 'cluster', clusterId: 17, totalMediaCount: 3, mediaPoints: [] });
    const source = mapMockMethod(map, 'getSource')('media-points');
    expect(source.getClusterLeaves).not.toHaveBeenCalled();
    expect(mapMockMethod(map, 'queryRenderedFeatures')).toHaveBeenCalledWith(
      [
        [94, 74],
        [106, 86],
      ],
      { layers: ['media-clusters', 'media-unclustered'] }
    );
    expect(mapMockMethod(map, 'on').mock.calls.some(([event]) => event === 'click')).toBe(false);

    const selection = await overlay.selectCandidate(candidates[0], () => true);
    expect(selection).toMatchObject({
      selectedMediaId: 2,
      mediaIds: [2, 3],
      totalMediaCount: 3,
      clusterId: 17,
      kind: 'cluster',
      clickPoint: { x: 100, y: 80 },
      clickLngLat: { lng: 7.525, lat: 47.485 },
    });
    expect(source.getClusterLeaves).toHaveBeenCalledWith(17, 200, 0);
    expect(onMediaSelect).not.toHaveBeenCalled();
    expect(mapMockMethod(map, 'easeTo')).not.toHaveBeenCalled();
  });

  it('deduplicates tile copies, groups colocated photos, and keeps nearby overlapping targets separate', async () => {
    const map = createMapMock(localBounds());
    vi.mocked(getMediaInBounds).mockResolvedValue([
      { id: 9, lat: 47.45, lng: 7.55 },
      { id: 10, lat: 47.45, lng: 7.55 },
      { id: 15, lat: 47.451, lng: 7.55 },
      { id: 11, lat: 47.4, lng: 7.5 },
      { id: 12, lat: 47.5, lng: 7.6 },
      { id: 13, lat: 47.45, lng: 7.4999 },
      { id: 14, lat: 47.45, lng: 7.6001 },
    ]);
    const overlay = new MediaOverlay(map, vi.fn());
    await overlay.show();
    mapMockMethod(map, 'unproject').mockReturnValue({ lng: 7.55, lat: 47.45 });
    mapMockMethod(map, 'queryRenderedFeatures').mockReturnValue([
      { properties: { mediaId: '9' } },
      { properties: { mediaId: 10 } },
      { properties: { mediaId: 15 } },
      { properties: { mediaId: 9 } },
    ]);

    const candidates = overlay.collectCandidates({ x: 75, y: 60 });
    expect(candidates).toHaveLength(2);
    const candidate = candidates.find((target) => target.totalMediaCount === 2)!;
    expect(candidate.key).toBe('media:9,10');
    const selection = await overlay.selectCandidate(candidate, () => true);
    expect(selection).toMatchObject({
      selectedMediaId: 9,
      mediaIds: [9, 10],
      mediaPoints: [
        { id: 9, lat: 47.45, lng: 7.55 },
        { id: 10, lat: 47.45, lng: 7.55 },
      ],
      totalMediaCount: 2,
      clusterId: null,
      kind: 'location',
      clickPoint: { x: 75, y: 60 },
      clickLngLat: { lng: 7.55, lat: 47.45 },
    });
    expect(selection?.viewportMediaPoints.map((point) => point.id).sort((left, right) => left - right)).toEqual([
      9, 10, 11, 12, 15,
    ]);
  });

  it('ignores photo interactions while a map tool owns clicks and restores hover afterward', async () => {
    const map = createMapMock(localBounds());
    let interactionEnabled = false;
    const overlay = new MediaOverlay(map, vi.fn(), undefined, () => interactionEnabled);
    await overlay.show();
    mapMockMethod(map, 'queryRenderedFeatures').mockReturnValue([photoFeature(1)]);
    const mediaMouseEnter = layerHandler(map, 'mouseenter', 'media-unclustered');

    expect(overlay.collectCandidates({ x: 75, y: 60 })).toEqual([]);
    mediaMouseEnter({});
    expect(mapMockMethod(map, 'getCanvas')().style.cursor).toBe('');

    interactionEnabled = true;
    expect(overlay.collectCandidates({ x: 75, y: 60 })).toHaveLength(1);
    mediaMouseEnter({});
    expect(mapMockMethod(map, 'getCanvas')().style.cursor).toBe('pointer');
  });

  it('ignores delayed cluster leaves when another interaction or selection takes over', async () => {
    const { map, overlay, candidate, leaves } = await pendingClusterSelection();
    let current = true;
    const selection = overlay.selectCandidate(candidate, () => current);
    current = false;
    leaves.resolve([photoFeature(2)]);
    expect(await selection).toBeNull();
    expect(mapMockMethod(map, 'easeTo')).not.toHaveBeenCalled();
  });

  it('ignores delayed cluster leaves after a media source is replaced', async () => {
    const { overlay, candidate, leaves } = await pendingClusterSelection();
    const selection = overlay.selectCandidate(candidate, () => true);
    overlay.hide();
    await overlay.show();
    leaves.resolve([photoFeature(2)]);
    expect(await selection).toBeNull();
  });

  it('ignores delayed cluster leaves after media scope or filters invalidate the source', async () => {
    const { overlay, candidate, leaves } = await pendingClusterSelection();
    const selection = overlay.selectCandidate(candidate, () => true);
    await overlay.refresh(true);
    leaves.resolve([photoFeature(2)]);
    expect(await selection).toBeNull();
  });

  it('ignores delayed cluster leaves when a tool takes ownership', async () => {
    const map = createMapMock(localBounds());
    let enabled = true;
    const overlay = new MediaOverlay(map, vi.fn(), undefined, () => enabled);
    await overlay.show();
    mapMockMethod(map, 'queryRenderedFeatures').mockReturnValue([clusterFeature(17, 3)]);
    const leaves = deferred<GeoJSON.Feature<GeoJSON.Point>[]>();
    mapMockMethod(map, 'getSource')('media-points').getClusterLeaves.mockReturnValueOnce(leaves.promise);
    const selection = overlay.selectCandidate(overlay.collectCandidates({ x: 100, y: 80 })[0], () => true);
    enabled = false;
    leaves.resolve([photoFeature(2)]);
    expect(await selection).toBeNull();
  });

  it('preserves selected cluster loading and the original viewport collection across ordinary panning', async () => {
    vi.useFakeTimers();
    const { map, overlay, candidate, leaves } = await pendingClusterSelection();
    const selection = overlay.selectCandidate(candidate, () => true);
    mapMockMethod(map, 'getBounds').mockReturnValue({
      getSouthWest: () => ({ lng: -74.1, lat: 40.7 }),
      getNorthEast: () => ({ lng: -73.9, lat: 40.8 }),
    });
    vi.mocked(getMediaInBounds).mockResolvedValueOnce([{ id: 99, lat: 40.75, lng: -74 }]);
    mapHandler(map, 'moveend')();
    await vi.runAllTimersAsync();
    leaves.resolve([photoFeature(2)]);
    const resolved = await selection;
    expect(resolved?.selectedMediaId).toBe(2);
    expect(resolved?.viewportMediaPoints.map((point) => point.id)).toEqual([1, 2]);
    expect(resolved?.clickPoint).toEqual({ x: 100, y: 80 });
    expect(resolved?.viewportMediaPoints.some((point) => point.id === 99)).toBe(false);
  });

  it('does not start leaf resolution for an already dismissed interaction', async () => {
    const { map, overlay, candidate } = await pendingClusterSelection();
    expect(await overlay.selectCandidate(candidate, () => false)).toBeNull();
    expect(mapMockMethod(map, 'getSource')('media-points').getClusterLeaves).not.toHaveBeenCalled();
  });

  it('retains unselected cluster IDs and paginated leaves across map-data refreshes until release', async () => {
    vi.useFakeTimers();
    const map = createMapMock(localBounds());
    const overlay = new MediaOverlay(map, vi.fn());
    await overlay.show();
    mapMockMethod(map, 'queryRenderedFeatures').mockReturnValue([clusterFeature(17, 201)]);
    const candidate = overlay.collectCandidates({ x: 100, y: 80 })[0];
    expect(candidate.viewportBounds).toEqual([
      [7.5, 47.4],
      [7.6, 47.5],
    ]);
    const source = mapMockMethod(map, 'getSource')('media-points');
    let treeReplaced = false;
    source.setData.mockClear().mockImplementation(() => {
      treeReplaced = true;
    });
    source.getClusterLeaves.mockImplementation((_id: number, _limit: number, offset: number) =>
      Promise.resolve(
        treeReplaced ? [photoFeature(99)] : offset === 0 ? [photoFeature(2), photoFeature(3)] : [photoFeature(4)]
      )
    );
    mapMockMethod(map, 'getBounds').mockReturnValue({
      getSouthWest: () => ({ lng: -74.1, lat: 40.7 }),
      getNorthEast: () => ({ lng: -73.9, lat: 40.8 }),
    });
    vi.mocked(getMediaInBounds).mockResolvedValueOnce([{ id: 99, lat: 40.75, lng: -74 }]);
    mapHandler(map, 'moveend')();
    await vi.runAllTimersAsync();
    expect(source.setData).not.toHaveBeenCalled();
    expect((await overlay.selectCandidate(candidate, () => true))?.mediaIds).toEqual([2, 3]);
    expect((await overlay.getClusterPage(17, 200, 200, 201)).mediaPoints.map((point) => point.id)).toEqual([4]);
    expect(source.getClusterLeaves).toHaveBeenLastCalledWith(17, 200, 200);

    const latestPoints = [{ id: 100, lat: 35.7, lng: 139.75 }];
    mapMockMethod(map, 'getBounds').mockReturnValue({
      getSouthWest: () => ({ lng: 139.65, lat: 35.65 }),
      getNorthEast: () => ({ lng: 139.85, lat: 35.75 }),
    });
    vi.mocked(getMediaInBounds).mockResolvedValueOnce(latestPoints);
    mapHandler(map, 'moveend')();
    await vi.runAllTimersAsync();
    expect(source.setData).not.toHaveBeenCalled();
    overlay.releaseCandidateContext();
    await flushMicrotasks();
    expect(source.setData).toHaveBeenCalledExactlyOnceWith({
      type: 'FeatureCollection',
      features: [
        {
          type: 'Feature',
          geometry: { type: 'Point', coordinates: [139.75, 35.7] },
          properties: { mediaId: 100 },
        },
      ],
    });
    expect(overlay.getLoadedPoints()).toEqual(latestPoints);
    expect(await overlay.selectCandidate(candidate, () => true)).toBeNull();
  });

  it('retains the existing tree when a new cluster click immediately follows release', async () => {
    const map = createMapMock(localBounds());
    const overlay = new MediaOverlay(map, vi.fn());
    await overlay.show();
    mapMockMethod(map, 'queryRenderedFeatures').mockReturnValue([clusterFeature(17, 3)]);
    const first = overlay.collectCandidates({ x: 100, y: 80 })[0];
    const source = mapMockMethod(map, 'getSource')('media-points');
    source.setData.mockClear();
    vi.mocked(getMediaInBounds).mockResolvedValueOnce([{ id: 99, lat: 47.45, lng: 7.55 }]);
    await overlay.refresh();
    overlay.releaseCandidateContext();
    const next = overlay.collectCandidates({ x: 110, y: 90 })[0];
    await flushMicrotasks();
    expect(source.setData).not.toHaveBeenCalled();
    expect(await overlay.selectCandidate(first, () => true)).toBeNull();
    source.getClusterLeaves.mockResolvedValueOnce([photoFeature(2)]);
    expect((await overlay.selectCandidate(next, () => true))?.selectedMediaId).toBe(2);
    overlay.releaseCandidateContext();
    await flushMicrotasks();
    expect(source.setData).toHaveBeenCalledOnce();
  });

  it('discards queued viewport publication when filters invalidate a retained cluster context', async () => {
    const map = createMapMock(localBounds());
    const overlay = new MediaOverlay(map, vi.fn());
    await overlay.show();
    mapMockMethod(map, 'queryRenderedFeatures').mockReturnValue([clusterFeature(17, 3)]);
    overlay.collectCandidates({ x: 100, y: 80 });
    vi.mocked(getMediaInBounds).mockResolvedValueOnce([{ id: 99, lat: 47.45, lng: 7.55 }]);
    await overlay.refresh();
    overlay.releaseCandidateContext();
    vi.mocked(getMediaInBounds).mockResolvedValueOnce([]);
    await overlay.refresh(true);
    await flushMicrotasks();
    expect(overlay.getLoadedPoints()).toEqual([]);
    const published = mapMockMethod(map, 'getSource')('media-points').setData.mock.calls;
    expect(
      published.some(([data]: [GeoJSON.FeatureCollection]) =>
        data.features.some((feature) => feature.properties?.mediaId === 99)
      )
    ).toBe(false);
  });

  it('skips stale cluster symbols while the public setData promise is pending', async () => {
    const map = createMapMock(localBounds());
    const overlay = new MediaOverlay(map, vi.fn());
    await overlay.show();
    const publication = deferred<void>();
    mapMockMethod(map, 'getSource')('media-points').setData.mockReturnValueOnce(publication.promise);
    await overlay.refresh();
    mapMockMethod(map, 'queryRenderedFeatures').mockReturnValue([clusterFeature(17, 3)]);
    expect(overlay.collectCandidates({ x: 100, y: 80 })).toEqual([]);
    publication.resolve();
    await flushMicrotasks();
    expect(overlay.collectCandidates({ x: 100, y: 80 })).toHaveLength(1);
  });

  it('continues publishing viewport updates when a click contains only unclustered photos', async () => {
    const map = createMapMock(localBounds());
    const overlay = new MediaOverlay(map, vi.fn());
    await overlay.show();
    mapMockMethod(map, 'queryRenderedFeatures').mockReturnValue([photoFeature(1)]);
    expect(overlay.collectCandidates({ x: 100, y: 80 })).toHaveLength(1);
    const updated = [{ id: 99, lat: 47.45, lng: 7.55 }];
    vi.mocked(getMediaInBounds).mockResolvedValueOnce(updated);
    await overlay.refresh();
    expect(overlay.getLoadedPoints()).toEqual(updated);
  });

  it('opens the captured viewport from a cluster without requesting any cluster leaves', async () => {
    const map = createMapMock(localBounds());
    const original = [
      { id: 1, lat: 47.4, lng: 7.5 },
      { id: 2, lat: 47.48, lng: 7.52 },
      { id: 3, lat: 47.48, lng: 7.7 },
    ];
    vi.mocked(getMediaInBounds).mockResolvedValueOnce(original);
    const overlay = new MediaOverlay(map, vi.fn());
    await overlay.show();
    mapMockMethod(map, 'queryRenderedFeatures').mockReturnValue([clusterFeature(17, 250)]);
    const candidate = overlay.collectCandidates({ x: 100, y: 80 })[0];
    const selection = await overlay.selectCandidate(candidate, () => true, 'viewport');
    expect(selection).toMatchObject({
      selectedMediaId: 2,
      mediaIds: [1, 2],
      totalMediaCount: 2,
      clusterId: null,
      kind: 'location',
      offset: 0,
    });
    expect(selection?.viewportMediaPoints.map((point) => point.id)).toEqual([2, 1]);
    expect(mapMockMethod(map, 'getSource')('media-points').getClusterLeaves).not.toHaveBeenCalled();
  });

  it('keeps viewport membership captured at tap after a camera-only source refresh and excludes an outside photo hit', async () => {
    const map = createMapMock(localBounds());
    const original = [{ id: 1, lat: 47.48, lng: 7.52 }];
    vi.mocked(getMediaInBounds).mockResolvedValueOnce(original);
    const overlay = new MediaOverlay(map, vi.fn());
    await overlay.show();
    const outside = { ...photoFeature(7), geometry: { type: 'Point', coordinates: [7.61, 47.48] } };
    mapMockMethod(map, 'queryRenderedFeatures').mockReturnValue([outside]);
    const candidate = overlay.collectCandidates({ x: 100, y: 80 })[0];
    vi.mocked(getMediaInBounds).mockResolvedValueOnce([{ id: 99, lat: 40.75, lng: -74 }]);
    await overlay.refresh();
    expect(overlay.getLoadedPoints().map((point) => point.id)).toEqual([99]);
    const selection = await overlay.selectCandidate(candidate, () => true, 'viewport');
    expect(selection?.mediaIds).toEqual([1]);
    expect(selection?.viewportMediaPoints).toEqual(original);
    expect(selection?.viewportBounds).toEqual([
      [7.5, 47.4],
      [7.6, 47.5],
    ]);
    expect(selection?.selectedMediaId).toBe(1);
  });

  it.each(['filters', 'release', 'source replacement'])(
    'rejects captured viewport selection after %s invalidates its context',
    async (invalidation) => {
      const map = createMapMock(localBounds());
      const overlay = new MediaOverlay(map, vi.fn());
      await overlay.show();
      mapMockMethod(map, 'queryRenderedFeatures').mockReturnValue([photoFeature(1)]);
      const candidate = overlay.collectCandidates({ x: 100, y: 80 })[0];
      if (invalidation === 'filters') await overlay.refresh(true);
      if (invalidation === 'release') overlay.releaseCandidateContext();
      if (invalidation === 'source replacement') {
        overlay.hide();
        await overlay.show();
      }
      expect(await overlay.selectCandidate(candidate, () => true, 'viewport')).toBeNull();
    }
  );

  it('keeps cluster resolution failures distinct from an empty collection', async () => {
    const { map, overlay, candidate } = await pendingClusterSelection();
    const error = new Error('cluster unavailable');
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    mapMockMethod(map, 'getSource')('media-points').getClusterLeaves.mockReset().mockRejectedValueOnce(error);
    await expect(overlay.selectCandidate(candidate, () => true)).rejects.toBe(error);
    expect(consoleError).toHaveBeenCalled();
    consoleError.mockRestore();
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
    getGlyphs: vi.fn(),
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
    unproject: vi.fn(() => ({ lng: 7.52, lat: 47.48 })),
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

function localBounds() {
  return {
    getSouthWest: () => ({ lng: 7.5, lat: 47.4 }),
    getNorthEast: () => ({ lng: 7.6, lat: 47.5 }),
  };
}

function photoFeature(id: number): GeoJSON.Feature<GeoJSON.Point> {
  return {
    type: 'Feature',
    geometry: { type: 'Point', coordinates: [7.52, 47.48] },
    properties: { mediaId: id },
  };
}

function clusterFeature(clusterId: number, count: number): GeoJSON.Feature<GeoJSON.Point> {
  return {
    type: 'Feature',
    geometry: { type: 'Point', coordinates: [7.525, 47.485] },
    properties: { cluster_id: clusterId, point_count: count },
  };
}

async function pendingClusterSelection() {
  const map = createMapMock(localBounds());
  const overlay = new MediaOverlay(map, vi.fn());
  await overlay.show();
  mapMockMethod(map, 'queryRenderedFeatures').mockReturnValue([clusterFeature(17, 3)]);
  const leaves = deferred<GeoJSON.Feature<GeoJSON.Point>[]>();
  mapMockMethod(map, 'getSource')('media-points').getClusterLeaves.mockReturnValueOnce(leaves.promise);
  const candidate = overlay.collectCandidates({ x: 100, y: 80 })[0];
  return { map, overlay, candidate, leaves };
}
