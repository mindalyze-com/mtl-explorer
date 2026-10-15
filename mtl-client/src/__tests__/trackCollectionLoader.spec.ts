import { describe, expect, it, vi } from 'vitest';
import type { GpsTrack } from 'x8ing-mtl-api-typescript-fetch';
import type { FilterResult } from '@/types/filter';
import {
  createTrackCollectionLoader as createLoader,
  type TrackCollectionLoaderDeps,
  type TrackCacheGateway,
} from '@/utils/tracks/trackCollectionLoaderCore';
import { BACKGROUND_TRACK_PRECISION, DETAIL_TRACK_PRECISION, OVERVIEW_PRECISION } from '@/utils/tracks/trackConstants';
import type {
  ActiveTrackFilterRequest,
  CachedGeometry,
  CachedTrack,
  TrackBatchPayload,
  TrackGeometryEntry,
  TrackGeometry,
} from '@/utils/tracks/trackTypes';
import { makeTrackGeometryCacheKey, selectBestGeometry } from '@/utils/tracks/trackCacheDb';

function createTrackCollectionLoader(
  deps: Omit<TrackCollectionLoaderDeps, 'fetchArchiveTrackIds'> &
    Partial<Pick<TrackCollectionLoaderDeps, 'fetchArchiveTrackIds'>>
) {
  return createLoader({
    fetchArchiveTrackIds: async () => new Set(await deps.cache.loadTrackIds()),
    ...deps,
  });
}

function makeTrack(id: number, version = 1): GpsTrack {
  return {
    id,
    version,
    centerLng: 7,
    centerLat: 46,
    trackLengthInMeter: 1000,
    trackName: `Track ${id}`,
  } as GpsTrack;
}

function makeFilterResult(ids: number[], version = 1): FilterResult {
  return {
    trackVersions: new Map(ids.map((id) => [id, version])),
    filterGroups: new Map(),
    standardFilterCount: ids.length,
  };
}

function makeBatchPayload(precision: number, trackIds: number[], version = 1): TrackBatchPayload {
  const tracksById = new Map<number, GpsTrack>();
  const geometryByTrackId = new Map<number, number[][]>();
  for (const id of trackIds) {
    tracksById.set(id, makeTrack(id, version));
    geometryByTrackId.set(id, [
      [7, 46],
      [7 + id / 100000, 46 + precision / 100000],
    ]);
  }
  return {
    tracksById,
    geometryByTrackId,
    filterGroups: new Map(),
    standardFilterCount: trackIds.length,
  };
}

function makeDateFilterRequest(isoDate: string): ActiveTrackFilterRequest {
  return {
    filterName: 'DateFilter',
    filterParams: {
      dateTimeParams: { DATE_TIME_FROM: new Date(isoDate) },
    } as unknown as ActiveTrackFilterRequest['filterParams'],
  };
}

async function waitUntil(assertion: () => void): Promise<void> {
  let lastError: unknown;
  for (let attempt = 0; attempt < 20; attempt++) {
    try {
      assertion();
      return;
    } catch (error) {
      lastError = error;
      await new Promise((resolve) => setTimeout(resolve, 0));
    }
  }
  throw lastError;
}

class MemoryTrackCache implements TrackCacheGateway {
  readonly tracks = new Map<number, CachedTrack>();
  readonly geometry = new Map<string, CachedGeometry>();
  loadBestGeometryCalls = 0;

  async clear(): Promise<void> {
    this.tracks.clear();
    this.geometry.clear();
  }

  async isPopulated(): Promise<boolean> {
    return this.tracks.size > 0;
  }

  async saveTracks(tracks: Map<number, GpsTrack>): Promise<void> {
    const now = Date.now();
    for (const [trackId, gpsTrack] of tracks) {
      this.tracks.set(trackId, {
        trackId,
        gpsTrack,
        entityVersion: gpsTrack.version ?? 0,
        updatedAt: now,
      });
    }
  }

  async saveGeometry(
    entries: (Omit<TrackGeometryEntry, 'recordingBreakBeforePointIndexes'> & Partial<TrackGeometry>)[]
  ): Promise<void> {
    const now = Date.now();
    for (const entry of entries) {
      this.geometry.set(makeTrackGeometryCacheKey(entry.trackId, entry.precision), {
        cacheKey: makeTrackGeometryCacheKey(entry.trackId, entry.precision),
        trackId: entry.trackId,
        precision: entry.precision,
        coordinates: entry.coordinates,
        recordingBreakBeforePointIndexes: entry.recordingBreakBeforePointIndexes ?? [],
        updatedAt: now,
      });
    }
  }

  async saveTrackBatch(
    tracks: Map<number, GpsTrack>,
    entries: (Omit<TrackGeometryEntry, 'recordingBreakBeforePointIndexes'> & Partial<TrackGeometry>)[]
  ): Promise<void> {
    await this.saveTracks(tracks);
    await this.saveGeometry(entries);
  }

  async deleteGeometryForTracks(trackIds: number[]): Promise<void> {
    const ids = new Set(trackIds);
    for (const [key, record] of this.geometry) {
      if (ids.has(record.trackId)) {
        this.geometry.delete(key);
      }
    }
  }

  async deleteTracks(trackIds: number[], stillCurrent = () => true): Promise<void> {
    if (!stillCurrent()) return;
    for (const id of trackIds) this.tracks.delete(id);
    await this.deleteGeometryForTracks(trackIds);
  }

  async loadTrackRecords(trackIds: number[]): Promise<Map<number, CachedTrack>> {
    const result = new Map<number, CachedTrack>();
    for (const trackId of trackIds) {
      const record = this.tracks.get(trackId);
      if (record) result.set(trackId, record);
    }
    return result;
  }

  async loadAllTracks(): Promise<Map<number, GpsTrack>> {
    return new Map([...this.tracks].map(([trackId, record]) => [trackId, record.gpsTrack]));
  }

  async loadTrackIds(): Promise<number[]> {
    return [...this.tracks.keys()];
  }

  async loadTrack(trackId: number): Promise<GpsTrack | null> {
    return this.tracks.get(trackId)?.gpsTrack ?? null;
  }

  async loadGeometry(trackId: number, precision: number): Promise<TrackGeometry | null> {
    const record = this.geometry.get(makeTrackGeometryCacheKey(trackId, precision));
    return record
      ? {
          coordinates: record.coordinates,
          recordingBreakBeforePointIndexes: record.recordingBreakBeforePointIndexes,
        }
      : null;
  }

  async loadGeometryForTracksAtPrecision(trackIds: number[], precision: number): Promise<Map<number, TrackGeometry>> {
    const ids = new Set(trackIds);
    const result = new Map<number, TrackGeometry>();
    for (const record of this.geometry.values()) {
      if (record.precision === precision && ids.has(record.trackId)) {
        result.set(record.trackId, (await this.loadGeometry(record.trackId, precision))!);
      }
    }
    return result;
  }

  async loadBestGeometry(): Promise<Map<number, TrackGeometry & { precision: number }>> {
    this.loadBestGeometryCalls++;
    return selectBestGeometry([...this.geometry.values()]);
  }
}

describe('TrackCollectionLoader', () => {
  it('loads filtered tracks while archive reconciliation is pending or fails without hydrating the whole cache', async () => {
    const cache = new MemoryTrackCache();
    await cache.saveTracks(new Map([[1, makeTrack(1)]]));
    const loadAllTracks = vi.spyOn(cache, 'loadAllTracks');
    let rejectSnapshot!: (error: Error) => void;
    const fetchArchiveTrackIds = vi.fn(() => new Promise<Set<number>>((_, reject) => (rejectSnapshot = reject)));
    const fetchFilteredTrackIds = vi.fn().mockResolvedValue(makeFilterResult([1]));
    const loader = createTrackCollectionLoader({
      cache,
      fetchArchiveTrackIds,
      fetchFilteredTrackIds,
      fetchTrackBatch: vi.fn(({ precision, trackIds }) => Promise.resolve(makeBatchPayload(precision, trackIds))),
      fetchDetailTrack: vi.fn(),
    });

    const result = await loader.loadCollectionPaged(OVERVIEW_PRECISION);
    expect([...result.gpsTracksById.keys()]).toEqual([1]);
    expect(fetchFilteredTrackIds).toHaveBeenCalledOnce();
    expect(loadAllTracks).not.toHaveBeenCalled();
    const reconciliation = loader.reconcileCache();
    const rejected = expect(reconciliation).rejects.toThrow('Archive unavailable');
    rejectSnapshot(new Error('Archive unavailable'));
    await rejected;
    expect(cache.tracks.has(1)).toBe(true);

    fetchArchiveTrackIds.mockRejectedValue(new Error('Archive unavailable'));
    const next = await loader.loadCollectionPaged(OVERVIEW_PRECISION);
    expect([...next.gpsTracksById.keys()]).toEqual([1]);
  });

  it('retains tracks cached while the archive snapshot is in flight', async () => {
    const cache = new MemoryTrackCache();
    await cache.saveTracks(new Map([[1, makeTrack(1)]]));
    let resolve!: (ids: Set<number>) => void;
    const fetchArchiveTrackIds = vi.fn(
      () =>
        new Promise<Set<number>>((done) => {
          resolve = done;
        })
    );
    const loader = createTrackCollectionLoader({
      cache,
      fetchArchiveTrackIds,
      fetchFilteredTrackIds: vi.fn(),
      fetchTrackBatch: vi.fn(),
      fetchDetailTrack: vi.fn(),
    });
    const reconciliation = loader.reconcileCache();
    await waitUntil(() => expect(fetchArchiveTrackIds).toHaveBeenCalledOnce());
    await cache.saveTracks(new Map([[2, makeTrack(2)]]));
    resolve(new Set());
    await reconciliation;
    expect([...(await loader.loadCachedTracks()).keys()]).toEqual([2]);
  });

  it('prunes deleted tracks for every cache reader while retaining tracks outside the active filter', async () => {
    const cache = new MemoryTrackCache();
    await cache.saveTracks(new Map([1, 2, 3].map((id) => [id, makeTrack(id)])));
    await cache.saveGeometry(
      [1, 2, 3].map((trackId) => ({
        trackId,
        precision: OVERVIEW_PRECISION,
        coordinates: [
          [7, 46],
          [8, 47],
        ],
      }))
    );
    const fetchArchiveTrackIds = vi.fn().mockResolvedValue(new Set([1, 2]));
    const loader = createTrackCollectionLoader({
      cache,
      fetchArchiveTrackIds,
      fetchFilteredTrackIds: vi.fn(),
      fetchTrackBatch: vi.fn(),
      fetchDetailTrack: vi.fn(),
    });
    const filtered = await loader.loadCollectionPaged(OVERVIEW_PRECISION, { filterResult: makeFilterResult([1]) });
    expect([...filtered.gpsTracksById.keys()]).toEqual([1]);
    expect([...(await loader.loadCachedTracks()).keys()]).toEqual([1, 2]);
    expect([...(await loader.loadFromCache())!.gpsTracksById.keys()]).toEqual([1, 2]);
    expect(await loader.readBestCachedTrackShape(3)).toBeNull();
    await loader.reconcileCache();
    expect(fetchArchiveTrackIds).toHaveBeenCalledOnce();
    loader.resetRequests();
    fetchArchiveTrackIds.mockResolvedValue(new Set());
    await loader.reconcileCache();
    expect(await loader.loadFromCache()).toBeNull();
  });

  it('does not prune on failed or superseded archive snapshots and can retry', async () => {
    const cache = new MemoryTrackCache();
    await cache.saveTracks(new Map([[1, makeTrack(1)]]));
    let resolve!: (ids: Set<number>) => void;
    const fetchArchiveTrackIds = vi
      .fn()
      .mockRejectedValueOnce(new Error('Offline'))
      .mockImplementationOnce(
        () =>
          new Promise<Set<number>>((done) => {
            resolve = done;
          })
      )
      .mockResolvedValue(new Set([1]));
    const loader = createTrackCollectionLoader({
      cache,
      fetchArchiveTrackIds,
      fetchFilteredTrackIds: vi.fn(),
      fetchTrackBatch: vi.fn(),
      fetchDetailTrack: vi.fn(),
    });
    await expect(loader.reconcileCache()).rejects.toThrow('Offline');
    expect(cache.tracks.has(1)).toBe(true);
    const old = loader.reconcileCache();
    await waitUntil(() => expect(fetchArchiveTrackIds).toHaveBeenCalledTimes(2));
    loader.resetRequests();
    resolve(new Set());
    await expect(old).rejects.toMatchObject({ name: 'AbortError' });
    expect(cache.tracks.has(1)).toBe(true);
    await loader.reconcileCache();
    expect(cache.tracks.has(1)).toBe(true);
  });

  it('reuses unchanged tracks and their finer cached geometry while fetching only revised and new tracks', async () => {
    const cache = new MemoryTrackCache();
    await cache.saveTracks(
      new Map([
        [1, makeTrack(1)],
        [2, makeTrack(2)],
      ])
    );
    for (const precision of [OVERVIEW_PRECISION, BACKGROUND_TRACK_PRECISION]) {
      await cache.saveGeometry(
        [1, 2].map((trackId) => ({
          trackId,
          precision,
          coordinates: [
            [7, 46],
            [8, 47],
          ],
        }))
      );
    }
    const unchangedOverview = cache.geometry.get(makeTrackGeometryCacheKey(1, OVERVIEW_PRECISION));
    const unchangedBackground = cache.geometry.get(makeTrackGeometryCacheKey(1, BACKGROUND_TRACK_PRECISION));
    const payload = makeBatchPayload(OVERVIEW_PRECISION, [2, 3], 2);
    payload.tracksById.get(2)!.trackName = 'Updated track';
    payload.geometryByTrackId.set(2, [
      [9, 48],
      [10, 49],
    ]);
    const fetchTrackBatch = vi.fn().mockResolvedValue(payload);
    const loader = createTrackCollectionLoader({
      cache,
      fetchFilteredTrackIds: vi.fn(),
      fetchTrackBatch,
      fetchDetailTrack: vi.fn(),
    });

    const result = await loader.loadCollectionPaged(OVERVIEW_PRECISION, {
      filterResult: {
        ...makeFilterResult([1, 2, 3]),
        trackVersions: new Map([
          [1, 1],
          [2, 2],
          [3, 2],
        ]),
      },
    });

    expect(fetchTrackBatch).toHaveBeenCalledOnce();
    expect(fetchTrackBatch.mock.calls[0]![0].trackIds).toEqual([2, 3]);
    expect(result.gpsTracksById.get(2)?.trackName).toBe('Updated track');
    expect(result.gpsTrackIdToFeature.get(2)?.geometry).toEqual({
      type: 'LineString',
      coordinates: [
        [9, 48],
        [10, 49],
      ],
    });
    expect(cache.geometry.get(makeTrackGeometryCacheKey(1, OVERVIEW_PRECISION))).toBe(unchangedOverview);
    expect(cache.geometry.get(makeTrackGeometryCacheKey(1, BACKGROUND_TRACK_PRECISION))).toBe(unchangedBackground);
    expect(await cache.loadGeometry(2, BACKGROUND_TRACK_PRECISION)).toBeNull();
  });

  it('omits stale cached metadata when a track disappears between the ID snapshot and batch response', async () => {
    const cache = new MemoryTrackCache();
    await cache.saveTracks(
      new Map([
        [1, makeTrack(1)],
        [2, makeTrack(2)],
      ])
    );
    await cache.saveGeometry(
      [1, 2].map((trackId) => ({
        trackId,
        precision: OVERVIEW_PRECISION,
        coordinates: [
          [7, 46],
          [8, 47],
        ],
      }))
    );
    const loader = createTrackCollectionLoader({
      cache,
      fetchFilteredTrackIds: vi.fn(),
      fetchTrackBatch: vi.fn().mockResolvedValue(makeBatchPayload(OVERVIEW_PRECISION, [])),
      fetchDetailTrack: vi.fn(),
    });

    const result = await loader.loadCollectionPaged(OVERVIEW_PRECISION, {
      filterResult: {
        ...makeFilterResult([1, 2]),
        trackVersions: new Map([
          [1, 1],
          [2, 2],
        ]),
      },
    });

    expect([...result.gpsTracksById.keys()]).toEqual([1]);
    expect([...result.gpsTrackIdToFeature.keys()]).toEqual([1]);
    expect(result.geojson.features).toHaveLength(1);
  });

  it.each(['refresh', 'abort', 'clear'] as const)(
    'prevents a late batch from writing or rendering after %s',
    async (action) => {
      const cache = new MemoryTrackCache();
      let resolveBatch!: (payload: TrackBatchPayload) => void;
      const fetchTrackBatch = vi.fn(
        () =>
          new Promise<TrackBatchPayload>((resolve) => {
            resolveBatch = resolve;
          })
      );
      const loader = createTrackCollectionLoader({
        cache,
        fetchFilteredTrackIds: vi.fn().mockResolvedValue(makeFilterResult([1])),
        fetchTrackBatch,
        fetchDetailTrack: vi.fn(),
      });
      const controller = new AbortController();
      const onPage = vi.fn();
      const load = loader.loadCollectionPaged(OVERVIEW_PRECISION, { signal: controller.signal, onPage });
      const rejected = expect(load).rejects.toMatchObject({ name: 'AbortError' });
      await waitUntil(() => expect(fetchTrackBatch).toHaveBeenCalledOnce());

      if (action === 'refresh') loader.resetRequests();
      else if (action === 'clear') await loader.clearCache();
      else controller.abort();
      resolveBatch(makeBatchPayload(OVERVIEW_PRECISION, [1]));
      await rejected;

      expect(cache.tracks.size).toBe(0);
      expect(cache.geometry.size).toBe(0);
      expect(onPage).not.toHaveBeenCalled();
    }
  );

  it('does not share a detail response from before a freshness refresh', async () => {
    const cache = new MemoryTrackCache();
    let resolveOld!: (payload: { coordinates: number[][]; gpsTrack: GpsTrack }) => void;
    const fetchDetailTrack = vi
      .fn()
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveOld = resolve;
          })
      )
      .mockResolvedValueOnce({ coordinates: [[9, 48]], gpsTrack: makeTrack(1, 2) });
    const loader = createTrackCollectionLoader({
      cache,
      fetchFilteredTrackIds: vi.fn(),
      fetchTrackBatch: vi.fn(),
      fetchDetailTrack,
    });

    const oldRead = loader.fetchDetailTrackAtPrecision(1, DETAIL_TRACK_PRECISION);
    loader.resetRequests();
    const freshRead = loader.fetchDetailTrackAtPrecision(1, DETAIL_TRACK_PRECISION);
    resolveOld({ coordinates: [[7, 46]], gpsTrack: makeTrack(1, 1) });

    expect((await freshRead).gpsTrack.version).toBe(2);
    expect((await oldRead).gpsTrack.version).toBe(1);
    expect(fetchDetailTrack).toHaveBeenCalledTimes(2);
  });

  it.each([
    [BACKGROUND_TRACK_PRECISION, OVERVIEW_PRECISION],
    [OVERVIEW_PRECISION, BACKGROUND_TRACK_PRECISION],
  ])('keeps recording gaps with their geometry after loading %sm then %sm', async (first, second) => {
    const cache = new MemoryTrackCache();
    const coarse = [
      [7, 46],
      [7.01, 46],
      [8, 47],
      [8.01, 47],
    ];
    const fine = [
      [7, 46],
      [7.005, 46],
      [7.01, 46],
      [8, 47],
      [8.005, 47],
      [8.01, 47],
    ];
    const payload = (precision: number): TrackBatchPayload => {
      const coordinates = precision === OVERVIEW_PRECISION ? coarse : fine;
      const track = makeTrack(1);
      track.gpsTracksData = [
        {
          numberOfPhysicalPoints: coordinates.length,
          recordingBreakBeforePointIndexes: [coordinates.length / 2],
        },
      ];
      return {
        tracksById: new Map([[1, track]]),
        geometryByTrackId: new Map([[1, coordinates]]),
        filterGroups: new Map(),
        standardFilterCount: 1,
      };
    };
    const fetchTrackBatch = vi.fn(async ({ precision }: { precision: number }) => payload(precision));
    const loader = createTrackCollectionLoader({
      cache,
      fetchFilteredTrackIds: vi.fn(async () => makeFilterResult([1])),
      fetchTrackBatch,
      fetchDetailTrack: vi.fn(),
    });
    await loader.loadCollectionPaged(first);
    await loader.loadCollectionPaged(second);
    for (const precision of [OVERVIEW_PRECISION, BACKGROUND_TRACK_PRECISION]) {
      const coordinates = precision === OVERVIEW_PRECISION ? coarse : fine;
      const result = await loader.loadCollectionPaged(precision);
      expect(result.geojson.features[0]?.geometry).toEqual({
        type: 'MultiLineString',
        coordinates: [coordinates.slice(0, coordinates.length / 2), coordinates.slice(coordinates.length / 2)],
      });
      expect(await loader.readCachedTrackAtPrecision(1, precision)).toMatchObject({
        coordinates,
        recordingBreakBeforePointIndexes: [coordinates.length / 2],
      });
    }
    for (const result of [await loader.applyFilter(), await loader.loadFromCache()]) {
      expect(result?.geojson.features[0]?.geometry).toEqual({
        type: 'MultiLineString',
        coordinates: [fine.slice(0, 3), fine.slice(3)],
      });
    }
    expect(fetchTrackBatch).toHaveBeenCalledTimes(2);
  });

  it('exposes cached track metadata without loading geometry', async () => {
    const cache = new MemoryTrackCache();
    const tracks = new Map([
      [1, makeTrack(1)],
      [2, makeTrack(2)],
    ]);
    await cache.saveTracks(tracks);
    await cache.saveGeometry([
      {
        trackId: 1,
        precision: OVERVIEW_PRECISION,
        coordinates: [
          [7, 46],
          [8, 47],
        ],
      },
    ]);
    const loader = createTrackCollectionLoader({
      cache,
      fetchFilteredTrackIds: vi.fn(),
      fetchTrackBatch: vi.fn(),
      fetchDetailTrack: vi.fn(),
    });

    const cachedTracks = await loader.loadCachedTracks();

    expect([...cachedTracks.keys()]).toEqual([1, 2]);
    expect(cache.loadBestGeometryCalls).toBe(0);
  });

  it('hydrates 10,042 overview tracks in 1,000-track batches', async () => {
    const ids = Array.from({ length: 10042 }, (_, idx) => idx + 1);
    const cache = new MemoryTrackCache();
    const fetchDetailTrack = vi.fn();
    const fetchTrackBatch = vi.fn(({ precision, trackIds }) => makeBatchPayload(precision, trackIds));
    const loader = createTrackCollectionLoader({
      cache,
      fetchFilteredTrackIds: vi.fn(() => Promise.resolve(makeFilterResult(ids))),
      fetchTrackBatch,
      fetchDetailTrack,
    });

    const result = await loader.loadCollectionPaged(OVERVIEW_PRECISION);

    expect(result.gpsTracksById.size).toBe(10042);
    expect(fetchTrackBatch).toHaveBeenCalledTimes(11);
    expect(fetchTrackBatch.mock.calls.every(([args]) => args.precision === OVERVIEW_PRECISION)).toBe(true);
    expect(fetchTrackBatch.mock.calls.every(([args]) => args.trackIds.length <= 1000)).toBe(true);
    expect(fetchDetailTrack).not.toHaveBeenCalled();
  });

  it('hydrates 10m through collection batches, never single-track detail requests', async () => {
    const ids = Array.from({ length: 2500 }, (_, idx) => idx + 1);
    const cache = new MemoryTrackCache();
    const fetchDetailTrack = vi.fn();
    const fetchTrackBatch = vi.fn(({ precision, trackIds }) => makeBatchPayload(precision, trackIds));
    const loader = createTrackCollectionLoader({
      cache,
      fetchFilteredTrackIds: vi.fn(() => Promise.resolve(makeFilterResult(ids))),
      fetchTrackBatch,
      fetchDetailTrack,
    });

    await loader.loadCollectionPaged(BACKGROUND_TRACK_PRECISION);

    expect(fetchTrackBatch).toHaveBeenCalledTimes(3);
    expect(fetchTrackBatch.mock.calls.every(([args]) => args.precision === BACKGROUND_TRACK_PRECISION)).toBe(true);
    expect(fetchDetailTrack).not.toHaveBeenCalled();
  });

  it('makes cacheable precision readers wait for an in-flight bulk load', async () => {
    const cache = new MemoryTrackCache();
    let resolveBatch!: (payload: TrackBatchPayload) => void;
    const batchPromise = new Promise<TrackBatchPayload>((resolve) => {
      resolveBatch = resolve;
    });
    const fetchDetailTrack = vi.fn();
    const loader = createTrackCollectionLoader({
      cache,
      fetchFilteredTrackIds: vi.fn(() => Promise.resolve(makeFilterResult([1]))),
      fetchTrackBatch: vi.fn(() => batchPromise),
      fetchDetailTrack,
    });

    const loadPromise = loader.loadCollectionPaged(BACKGROUND_TRACK_PRECISION);
    const readPromise = loader.readCachedTrackAtPrecision(1, BACKGROUND_TRACK_PRECISION);

    resolveBatch(makeBatchPayload(BACKGROUND_TRACK_PRECISION, [1]));
    const read = await readPromise;
    await loadPromise;

    expect(read?.fromCache).toBe(true);
    expect(read?.gpsTrack.id).toBe(1);
    expect(fetchDetailTrack).not.toHaveBeenCalled();
  });

  it('uses batch hydration even for a single stale cacheable track', async () => {
    const cache = new MemoryTrackCache();
    await cache.saveTracks(new Map([[1, makeTrack(1, 1)]]));
    await cache.saveGeometry([
      {
        trackId: 1,
        precision: OVERVIEW_PRECISION,
        coordinates: [
          [7, 46],
          [8, 47],
        ],
      },
    ]);
    const fetchDetailTrack = vi.fn();
    const fetchTrackBatch = vi.fn(({ precision, trackIds }) => makeBatchPayload(precision, trackIds, 2));
    const loader = createTrackCollectionLoader({
      cache,
      fetchFilteredTrackIds: vi.fn(() => Promise.resolve(makeFilterResult([1], 2))),
      fetchTrackBatch,
      fetchDetailTrack,
    });

    const result = await loader.loadCollectionPaged(OVERVIEW_PRECISION);

    expect(result.gpsTracksById.get(1)?.version).toBe(2);
    expect(fetchTrackBatch).toHaveBeenCalledTimes(1);
    expect(fetchTrackBatch.mock.calls[0][0].trackIds).toEqual([1]);
    expect(fetchDetailTrack).not.toHaveBeenCalled();
  });

  it('passes one pinned filter request to every batch in a bulk load', async () => {
    const ids = Array.from({ length: 1200 }, (_, idx) => idx + 1);
    const cache = new MemoryTrackCache();
    const fetchTrackBatch = vi.fn(({ precision, trackIds }) => makeBatchPayload(precision, trackIds));
    const filterRequest = {
      filterName: 'PinnedFilter',
      filterParams: { stringParams: { surface: 'trail' } },
    };
    const loader = createTrackCollectionLoader({
      cache,
      fetchFilteredTrackIds: vi.fn(() => Promise.resolve(makeFilterResult(ids))),
      loadActiveFilterRequest: vi.fn(() => Promise.resolve(filterRequest)),
      fetchTrackBatch,
      fetchDetailTrack: vi.fn(),
    });

    await loader.loadCollectionPaged(OVERVIEW_PRECISION);

    expect(fetchTrackBatch).toHaveBeenCalledTimes(2);
    expect(fetchTrackBatch.mock.calls.every(([args]) => args.filterRequest === filterRequest)).toBe(true);
  });

  it('joins compatible concurrent bulk loads', async () => {
    const ids = [1, 2, 3];
    const cache = new MemoryTrackCache();
    const filterResult = makeFilterResult(ids);
    let resolveBatch!: (payload: TrackBatchPayload) => void;
    const batchPromise = new Promise<TrackBatchPayload>((resolve) => {
      resolveBatch = resolve;
    });
    const fetchTrackBatch = vi.fn(() => batchPromise);
    const loader = createTrackCollectionLoader({
      cache,
      fetchFilteredTrackIds: vi.fn(() => Promise.resolve(filterResult)),
      fetchTrackBatch,
      fetchDetailTrack: vi.fn(),
    });

    const firstLoad = loader.loadCollectionPaged(OVERVIEW_PRECISION, { filterResult });
    const secondLoad = loader.loadCollectionPaged(OVERVIEW_PRECISION, { filterResult });
    resolveBatch(makeBatchPayload(OVERVIEW_PRECISION, ids));
    const [firstResult, secondResult] = await Promise.all([firstLoad, secondLoad]);

    expect(fetchTrackBatch).toHaveBeenCalledTimes(1);
    expect(firstResult.gpsTracksById.size).toBe(3);
    expect(secondResult.gpsTracksById.size).toBe(3);
  });

  it('joins compatible bulk loads when track version maps have different insertion order', async () => {
    const ids = [1, 2, 3];
    const cache = new MemoryTrackCache();
    const firstFilterResult = makeFilterResult(ids);
    const secondFilterResult: FilterResult = {
      ...makeFilterResult([]),
      trackVersions: new Map([
        [3, 1],
        [1, 1],
        [2, 1],
      ]),
      standardFilterCount: ids.length,
    };
    let resolveBatch!: (payload: TrackBatchPayload) => void;
    const batchPromise = new Promise<TrackBatchPayload>((resolve) => {
      resolveBatch = resolve;
    });
    const fetchTrackBatch = vi.fn(() => batchPromise);
    const loader = createTrackCollectionLoader({
      cache,
      fetchFilteredTrackIds: vi.fn(),
      fetchTrackBatch,
      fetchDetailTrack: vi.fn(),
    });

    const firstLoad = loader.loadCollectionPaged(OVERVIEW_PRECISION, { filterResult: firstFilterResult });
    const secondLoad = loader.loadCollectionPaged(OVERVIEW_PRECISION, { filterResult: secondFilterResult });
    resolveBatch(makeBatchPayload(OVERVIEW_PRECISION, ids));
    await Promise.all([firstLoad, secondLoad]);

    expect(fetchTrackBatch).toHaveBeenCalledTimes(1);
  });

  it('does not join bulk loads with distinct Date-valued filter params', async () => {
    const ids = [1];
    const cache = new MemoryTrackCache();
    const filterResult = makeFilterResult(ids);
    const batchResolvers: Array<() => void> = [];
    const fetchTrackBatch = vi.fn(
      ({ precision, trackIds }) =>
        new Promise<TrackBatchPayload>((resolve) => {
          batchResolvers.push(() => resolve(makeBatchPayload(precision, trackIds)));
        })
    );
    const loader = createTrackCollectionLoader({
      cache,
      fetchFilteredTrackIds: vi.fn(() => Promise.resolve(filterResult)),
      loadActiveFilterRequest: vi
        .fn()
        .mockResolvedValueOnce(makeDateFilterRequest('2024-01-01T00:00:00Z'))
        .mockResolvedValueOnce(makeDateFilterRequest('2024-02-01T00:00:00Z')),
      fetchTrackBatch,
      fetchDetailTrack: vi.fn(),
    });

    const firstLoad = loader.loadCollectionPaged(OVERVIEW_PRECISION, { filterResult });
    await waitUntil(() => expect(fetchTrackBatch).toHaveBeenCalledTimes(1));
    const secondLoad = loader.loadCollectionPaged(OVERVIEW_PRECISION, { filterResult });
    await waitUntil(() => expect(fetchTrackBatch).toHaveBeenCalledTimes(2));

    batchResolvers.forEach((resolve) => resolve());
    await Promise.all([firstLoad, secondLoad]);
  });

  it('treats cached tracks with missing server versions as stale', async () => {
    const cache = new MemoryTrackCache();
    cache.tracks.set(1, {
      trackId: 1,
      gpsTrack: makeTrack(1),
      entityVersion: -1,
      updatedAt: 1,
    });
    await cache.saveGeometry([
      {
        trackId: 1,
        precision: OVERVIEW_PRECISION,
        coordinates: [[7, 46]],
      },
    ]);
    const filterResult = makeFilterResult([]);
    filterResult.trackVersions = new Map([[1, undefined as unknown as number]]);
    const fetchTrackBatch = vi.fn(({ precision, trackIds }) => makeBatchPayload(precision, trackIds, 2));
    const loader = createTrackCollectionLoader({
      cache,
      fetchFilteredTrackIds: vi.fn(() => Promise.resolve(filterResult)),
      fetchTrackBatch,
      fetchDetailTrack: vi.fn(),
    });

    const result = await loader.loadCollectionPaged(OVERVIEW_PRECISION, { filterResult });

    expect(fetchTrackBatch).toHaveBeenCalledTimes(1);
    expect(fetchTrackBatch.mock.calls[0][0].trackIds).toEqual([1]);
    expect(result.gpsTracksById.get(1)?.version).toBe(2);
  });

  it('returns merged batch filter groups without mutating the caller filter result', async () => {
    const cache = new MemoryTrackCache();
    const filterResult = makeFilterResult([1]);
    filterResult.filterGroups.set(1, 'ids-group');
    const fetchTrackBatch = vi.fn(({ precision, trackIds }) => ({
      ...makeBatchPayload(precision, trackIds),
      filterGroups: new Map([[1, 'batch-group']]),
    }));
    const loader = createTrackCollectionLoader({
      cache,
      fetchFilteredTrackIds: vi.fn(() => Promise.resolve(filterResult)),
      fetchTrackBatch,
      fetchDetailTrack: vi.fn(),
    });

    const result = await loader.loadCollectionPaged(OVERVIEW_PRECISION, { filterResult });

    expect(filterResult.filterGroups.get(1)).toBe('ids-group');
    expect(result.filterResult?.filterGroups.get(1)).toBe('batch-group');
    expect(result.gpsTrackIdToFeature.get(1)?.properties?.filterGroup).toBe('batch-group');
  });

  it('lets cache readers fall back to saved data when an unrelated bulk load fails', async () => {
    const cache = new MemoryTrackCache();
    await cache.saveTracks(new Map([[1, makeTrack(1, 1)]]));
    await cache.saveGeometry([
      {
        trackId: 1,
        precision: OVERVIEW_PRECISION,
        coordinates: [
          [7, 46],
          [8, 47],
        ],
      },
    ]);
    let rejectBatch!: (error: Error) => void;
    const batchPromise = new Promise<TrackBatchPayload>((_resolve, reject) => {
      rejectBatch = reject;
    });
    const loader = createTrackCollectionLoader({
      cache,
      fetchFilteredTrackIds: vi.fn(() => Promise.resolve(makeFilterResult([1, 2]))),
      fetchTrackBatch: vi.fn(() => batchPromise),
      fetchDetailTrack: vi.fn(),
    });

    const loadPromise = loader.loadCollectionPaged(OVERVIEW_PRECISION);
    const readPromise = loader.readCachedTrackAtPrecision(1, OVERVIEW_PRECISION);
    rejectBatch(new Error('batch failed'));

    await expect(loadPromise).rejects.toThrow('batch failed');
    await expect(readPromise).resolves.toMatchObject({
      fromCache: true,
      gpsTrack: { id: 1 },
    });
  });

  it('dedupes concurrent detail requests for the same track and precision', async () => {
    const cache = new MemoryTrackCache();
    let resolveDetail!: (payload: { coordinates: number[][]; gpsTrack: GpsTrack }) => void;
    const detailPromise = new Promise<{ coordinates: number[][]; gpsTrack: GpsTrack }>((resolve) => {
      resolveDetail = resolve;
    });
    const fetchDetailTrack = vi.fn(() => detailPromise);
    const loader = createTrackCollectionLoader({
      cache,
      fetchFilteredTrackIds: vi.fn(() => Promise.resolve(makeFilterResult([]))),
      fetchTrackBatch: vi.fn(),
      fetchDetailTrack,
    });

    const firstRead = loader.fetchDetailTrackAtPrecision(7, DETAIL_TRACK_PRECISION);
    const secondRead = loader.fetchDetailTrackAtPrecision(7, DETAIL_TRACK_PRECISION);
    resolveDetail({
      coordinates: [
        [7, 46],
        [8, 47],
      ],
      gpsTrack: makeTrack(7),
    });
    const [firstResult, secondResult] = await Promise.all([firstRead, secondRead]);

    expect(fetchDetailTrack).toHaveBeenCalledTimes(1);
    expect(firstResult.coordinates).toEqual(secondResult.coordinates);
    expect(firstResult.fromCache).toBe(false);
    expect(secondResult.fromCache).toBe(false);
  });

  it('does not let the first detail caller abort the shared in-flight request', async () => {
    const cache = new MemoryTrackCache();
    let resolveDetail!: (payload: { coordinates: number[][]; gpsTrack: GpsTrack }) => void;
    const detailPromise = new Promise<{ coordinates: number[][]; gpsTrack: GpsTrack }>((resolve) => {
      resolveDetail = resolve;
    });
    const fetchDetailTrack = vi.fn(() => detailPromise);
    const loader = createTrackCollectionLoader({
      cache,
      fetchFilteredTrackIds: vi.fn(() => Promise.resolve(makeFilterResult([]))),
      fetchTrackBatch: vi.fn(),
      fetchDetailTrack,
    });
    const firstController = new AbortController();

    const firstRead = loader.fetchDetailTrackAtPrecision(7, DETAIL_TRACK_PRECISION, firstController.signal);
    firstController.abort();
    const secondRead = loader.fetchDetailTrackAtPrecision(7, DETAIL_TRACK_PRECISION);
    resolveDetail({
      coordinates: [
        [7, 46],
        [8, 47],
      ],
      gpsTrack: makeTrack(7),
    });

    await expect(firstRead).rejects.toMatchObject({ name: 'AbortError' });
    await expect(secondRead).resolves.toMatchObject({
      fromCache: false,
      gpsTrack: { id: 7 },
    });
    expect(fetchDetailTrack).toHaveBeenCalledTimes(1);
    expect(fetchDetailTrack.mock.calls[0][0].signal).toBeUndefined();
  });

  it('applies filters using targeted best-geometry reads instead of scanning all geometry', async () => {
    const cache = new MemoryTrackCache();
    await cache.saveTracks(
      new Map([
        [1, makeTrack(1)],
        [2, makeTrack(2)],
      ])
    );
    await cache.saveGeometry([
      {
        trackId: 1,
        precision: OVERVIEW_PRECISION,
        coordinates: [
          [7, 46],
          [8, 47],
        ],
      },
      {
        trackId: 1,
        precision: BACKGROUND_TRACK_PRECISION,
        coordinates: [
          [7, 46],
          [7.5, 46.5],
          [8, 47],
        ],
      },
      {
        trackId: 2,
        precision: BACKGROUND_TRACK_PRECISION,
        coordinates: [
          [9, 48],
          [10, 49],
        ],
      },
    ]);
    const loader = createTrackCollectionLoader({
      cache,
      fetchFilteredTrackIds: vi.fn(() => Promise.resolve(makeFilterResult([1]))),
      fetchTrackBatch: vi.fn(),
      fetchDetailTrack: vi.fn(),
    });

    const result = await loader.applyFilter({ filterResult: makeFilterResult([1]) });

    expect(cache.loadBestGeometryCalls).toBe(0);
    expect(result.gpsTracksById.has(1)).toBe(true);
    expect(result.gpsTracksById.has(2)).toBe(false);
    expect(result.trackPrecisions.get(1)).toBe(BACKGROUND_TRACK_PRECISION);
  });

  it('renders a successful filter response when browser cache writes are unavailable', async () => {
    const cache = new MemoryTrackCache();
    vi.spyOn(cache, 'saveTrackBatch').mockResolvedValue(undefined);
    const payload = makeBatchPayload(OVERVIEW_PRECISION, [1, 2]);
    const loader = createTrackCollectionLoader({
      cache,
      fetchFilteredTrackIds: vi.fn(),
      fetchTrackBatch: vi.fn().mockResolvedValue(payload),
      fetchDetailTrack: vi.fn(),
    });

    const result = await loader.applyFilter({ filterResult: makeFilterResult([1, 2]) });

    expect([...result.gpsTracksById.keys()]).toEqual([1, 2]);
    expect(result.geojson.features).toHaveLength(2);
    expect(result.geojson.features[0]?.geometry).toEqual({
      type: 'LineString',
      coordinates: payload.geometryByTrackId.get(1),
    });
    expect(cache.tracks.size).toBe(0);
  });

  it('keeps fresh filter data when stale browser cache records cannot be replaced', async () => {
    const cache = new MemoryTrackCache();
    await cache.saveTracks(new Map([[1, makeTrack(1, 1)]]));
    await cache.saveGeometry([
      {
        trackId: 1,
        precision: BACKGROUND_TRACK_PRECISION,
        coordinates: [
          [8, 47],
          [8.01, 47.01],
        ],
      },
    ]);
    vi.spyOn(cache, 'saveTrackBatch').mockResolvedValue(undefined);
    vi.spyOn(cache, 'deleteGeometryForTracks').mockResolvedValue(undefined);
    const payload = makeBatchPayload(OVERVIEW_PRECISION, [1], 2);
    const loader = createTrackCollectionLoader({
      cache,
      fetchFilteredTrackIds: vi.fn(),
      fetchTrackBatch: vi.fn().mockResolvedValue(payload),
      fetchDetailTrack: vi.fn(),
    });

    const result = await loader.applyFilter({ filterResult: makeFilterResult([1], 2) });

    expect(result.gpsTracksById.get(1)?.version).toBe(2);
    expect(result.trackPrecisions.get(1)).toBe(OVERVIEW_PRECISION);
    expect(result.geojson.features[0]?.geometry).toEqual({
      type: 'LineString',
      coordinates: payload.geometryByTrackId.get(1),
    });
  });

  it('selects the best cached geometry by smallest precision number', () => {
    const records: CachedGeometry[] = [
      {
        cacheKey: makeTrackGeometryCacheKey(7, OVERVIEW_PRECISION),
        trackId: 7,
        precision: OVERVIEW_PRECISION,
        coordinates: [[7, 46]],
        recordingBreakBeforePointIndexes: [],
        updatedAt: 1,
      },
      {
        cacheKey: makeTrackGeometryCacheKey(7, BACKGROUND_TRACK_PRECISION),
        trackId: 7,
        precision: BACKGROUND_TRACK_PRECISION,
        coordinates: [[8, 47]],
        recordingBreakBeforePointIndexes: [],
        updatedAt: 2,
      },
    ];

    expect(selectBestGeometry(records).get(7)).toEqual({
      precision: BACKGROUND_TRACK_PRECISION,
      coordinates: [[8, 47]],
      recordingBreakBeforePointIndexes: [],
    });
  });
});
