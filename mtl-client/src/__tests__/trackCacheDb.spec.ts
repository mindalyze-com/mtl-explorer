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
  it('invalidates the legacy cache and persists metadata plus per-precision geometry', async () => {
    await deleteDatabase(DB_NAME);

    const legacyDb = new Dexie(DB_NAME);
    legacyDb.version(6).stores({
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
    expect(await objectStoreNames(DB_NAME)).not.toEqual(
      expect.arrayContaining(['track_resolutions', 'store_meta']),
    );

    const track = makeTrack(7, 3);
    await saveTrackRecords(new Map([[7, track]]));
    await saveGeometryRecords([
      {
        trackId: 7,
        precision: OVERVIEW_PRECISION,
        coordinates: [[7, 46]],
      },
      {
        trackId: 7,
        precision: BACKGROUND_TRACK_PRECISION,
        coordinates: [[8, 47]],
      },
    ]);

    expect(await loadTrack(7)).toMatchObject({ id: 7, version: 3 });
    expect(await loadGeometry(7, OVERVIEW_PRECISION)).toEqual([[7, 46]]);
    expect(await loadGeometry(7, BACKGROUND_TRACK_PRECISION)).toEqual([[8, 47]]);
    expect((await loadBestGeometry()).get(7)).toEqual({
      precision: BACKGROUND_TRACK_PRECISION,
      coordinates: [[8, 47]],
    });
    expect(makeTrackGeometryCacheKey(7, OVERVIEW_PRECISION)).toBe('7_1000');

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
