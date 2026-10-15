import 'fake-indexeddb/auto';
import Dexie from 'dexie';
import { describe, expect, it, vi } from 'vitest';
import type { GpsTrack } from 'x8ing-mtl-api-typescript-fetch';
import { BACKGROUND_TRACK_PRECISION, OVERVIEW_PRECISION } from '@/utils/tracks/trackConstants';

const DB_NAME = 'mtl_db';

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

function deleteDatabase(name: string): Promise<void> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.deleteDatabase(name);
    request.onsuccess = () => resolve();
    request.onerror = () => reject(request.error);
    request.onblocked = () => reject(new Error(`Deleting ${name} was blocked`));
  });
}

function objectStoreNames(name: string): Promise<string[]> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(name);
    request.onsuccess = () => {
      const database = request.result;
      const names = Array.from(database.objectStoreNames);
      database.close();
      resolve(names);
    };
    request.onerror = () => reject(request.error);
  });
}

describe('trackCacheDb', () => {
  it('invalidates every geometry precision on metadata revision and retains unchanged geometry', async () => {
    await deleteDatabase(DB_NAME);
    vi.resetModules();
    const db = await import('@/utils/tracks/trackCacheDb');
    const geometry = [OVERVIEW_PRECISION, BACKGROUND_TRACK_PRECISION].map((precision) => ({
      trackId: 7,
      precision,
      coordinates: [[7, 46]],
      recordingBreakBeforePointIndexes: [],
    }));
    await db.saveTrackBatchRecords(new Map([[7, makeTrack(7, 1)]]), geometry);
    await db.saveTrackRecords(new Map([[7, { ...makeTrack(7, 1), energyNetTotalWh: 10 }]]));
    expect((await db.loadBestGeometry()).size).toBe(1);
    expect(await db.loadGeometry(7, OVERVIEW_PRECISION)).not.toBeNull();
    await db.saveTrackRecords(new Map([[7, { ...makeTrack(7, 2), energyNetTotalWh: 20 }]]));
    expect(await db.loadTrack(7)).toMatchObject({ version: 2, energyNetTotalWh: 20 });
    expect(await db.loadBestGeometry()).toEqual(new Map());
    await db.saveTrackBatchRecords(new Map([[7, makeTrack(7, 2)]]), geometry);
    await db.saveTrackBatchRecords(new Map([[7, makeTrack(7, 3)]]), [geometry[0]!]);
    expect(await db.loadGeometry(7, OVERVIEW_PRECISION)).not.toBeNull();
    expect(await db.loadGeometry(7, BACKGROUND_TRACK_PRECISION)).toBeNull();
    await db.clearTrackCacheDb();
  });

  it('removes deleted metadata and all geometry while preserving other tracks', async () => {
    await deleteDatabase(DB_NAME);
    vi.resetModules();
    const db = await import('@/utils/tracks/trackCacheDb');
    await db.saveTrackBatchRecords(
      new Map([
        [1, makeTrack(1)],
        [2, makeTrack(2)],
      ]),
      [1, 2].flatMap((trackId) =>
        [OVERVIEW_PRECISION, BACKGROUND_TRACK_PRECISION].map((precision) => ({
          trackId,
          precision,
          coordinates: [[7, 46]],
          recordingBreakBeforePointIndexes: [],
        }))
      )
    );
    await db.deleteTrackRecords([1], () => false);
    expect(await db.loadTrack(1)).not.toBeNull();
    await db.deleteTrackRecords([1]);
    expect(await db.loadTrack(1)).toBeNull();
    expect(await db.loadTrackIds()).toEqual([2]);
    expect([...(await db.loadAllTracks())].map(([id]) => id)).toEqual([2]);
    expect([...(await db.loadBestGeometry())].map(([id]) => id)).toEqual([2]);
    await db.clearTrackCacheDb();
  });

  it.each([6, 7, 8])('invalidates cache %s and persists metadata plus per-precision geometry', async (version) => {
    await deleteDatabase(DB_NAME);

    const legacyDb = new Dexie(DB_NAME);
    legacyDb.version(version).stores({
      tracks: 'trackId, entityVersion, updatedAt',
      track_geometry: 'cacheKey, trackId, precision, updatedAt',
      track_resolutions: 'cacheKey, trackId',
      store_meta: 'key',
    });
    await legacyDb.table('tracks').put({
      trackId: 42,
      gpsTrack: makeTrack(42),
      entityVersion: 1,
      updatedAt: 1,
    });
    await legacyDb.table('track_geometry').put({
      cacheKey: `${42}_${OVERVIEW_PRECISION}`,
      trackId: 42,
      precision: OVERVIEW_PRECISION,
      coordinates: [[7, 46]],
      updatedAt: 1,
    });
    legacyDb.close();

    vi.resetModules();
    const {
      clearTrackCacheDb,
      loadAllTrackRecords,
      loadBestGeometry,
      loadGeometry,
      loadTrack,
      makeTrackGeometryCacheKey,
      saveGeometryRecords,
      saveTrackRecords,
    } = await import('@/utils/tracks/trackCacheDb');

    expect(await loadAllTrackRecords()).toEqual(new Map());
    expect(await loadBestGeometry()).toEqual(new Map());
    expect(await objectStoreNames(DB_NAME)).not.toEqual(expect.arrayContaining(['track_resolutions', 'store_meta']));

    const track = makeTrack(7, 3);
    await saveTrackRecords(new Map([[7, track]]));
    await saveGeometryRecords([
      {
        trackId: 7,
        precision: OVERVIEW_PRECISION,
        coordinates: [[7, 46]],
        recordingBreakBeforePointIndexes: [],
      },
      {
        trackId: 7,
        precision: BACKGROUND_TRACK_PRECISION,
        coordinates: [[8, 47]],
        recordingBreakBeforePointIndexes: [],
      },
    ]);

    expect(await loadTrack(7)).toMatchObject({ id: 7, version: 3 });
    expect(await loadGeometry(7, OVERVIEW_PRECISION)).toEqual({
      coordinates: [[7, 46]],
      recordingBreakBeforePointIndexes: [],
    });
    expect(await loadGeometry(7, BACKGROUND_TRACK_PRECISION)).toEqual({
      coordinates: [[8, 47]],
      recordingBreakBeforePointIndexes: [],
    });
    expect((await loadBestGeometry()).get(7)).toEqual({
      precision: BACKGROUND_TRACK_PRECISION,
      coordinates: [[8, 47]],
      recordingBreakBeforePointIndexes: [],
    });
    expect(makeTrackGeometryCacheKey(7, OVERVIEW_PRECISION)).toBe('7_1000');

    await clearTrackCacheDb();
  });

  it('persists recording breaks with coordinates through every geometry read', async () => {
    await deleteDatabase(DB_NAME);
    vi.resetModules();
    const {
      saveTrackBatchRecords,
      loadGeometry,
      loadGeometryForTracksAtPrecision,
      loadBestGeometry,
      clearTrackCacheDb,
    } = await import('@/utils/tracks/trackCacheDb');
    const overview = {
      coordinates: [
        [7, 46],
        [7.01, 46],
        [8, 47],
        [8.01, 47],
      ],
      recordingBreakBeforePointIndexes: [2],
    };
    const background = {
      coordinates: [
        [7, 46],
        [7.005, 46],
        [7.01, 46],
        [8, 47],
        [8.005, 47],
        [8.01, 47],
      ],
      recordingBreakBeforePointIndexes: [3],
    };
    for (const [precision, geometry] of [
      [BACKGROUND_TRACK_PRECISION, background],
      [OVERVIEW_PRECISION, overview],
    ] as const) {
      const track = {
        ...makeTrack(7),
        gpsTracksData: [
          {
            numberOfPhysicalPoints: geometry.coordinates.length,
            recordingBreakBeforePointIndexes: geometry.recordingBreakBeforePointIndexes,
          },
        ],
      };
      await saveTrackBatchRecords(new Map([[7, track]]), [{ trackId: 7, precision, ...geometry }]);
      expect(await loadGeometry(7, precision)).toEqual(geometry);
    }
    expect(await loadGeometryForTracksAtPrecision([7], BACKGROUND_TRACK_PRECISION)).toEqual(new Map([[7, background]]));
    expect(await loadGeometryForTracksAtPrecision([7], OVERVIEW_PRECISION)).toEqual(new Map([[7, overview]]));
    expect(await loadBestGeometry()).toEqual(new Map([[7, { ...background, precision: BACKGROUND_TRACK_PRECISION }]]));
    await clearTrackCacheDb();
  });

  it('does not write stale save data after a concurrent manual clear', async () => {
    await deleteDatabase(DB_NAME);
    vi.resetModules();
    const { clearTrackCacheDb, loadTrack, saveTrackRecords } = await import('@/utils/tracks/trackCacheDb');

    const savePromise = saveTrackRecords(new Map([[99, makeTrack(99)]]));
    const clearPromise = clearTrackCacheDb();

    await Promise.all([savePromise, clearPromise]);

    expect(await loadTrack(99)).toBeNull();
  });
});
