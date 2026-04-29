import Dexie, { type Table } from 'dexie';
import type { GpsTrack } from 'x8ing-mtl-api-typescript-fetch';
import { logTrackCache, trackCacheElapsedMs, trackCacheNow } from '@/utils/tracks/trackCacheLog';
import type { CachedGeometry, CachedTrack, TrackGeometryEntry } from '@/utils/tracks/trackTypes';

const DB_NAME = 'mtl_db';
const STORE_TRACKS = 'tracks';
const STORE_GEOMETRY = 'track_geometry';
const LEGACY_STORES = ['track_resolutions', 'store_meta'] as const;

// One-time invalidation for the rewritten cache subsystem.
const DB_VERSION = 7;

class TrackCacheDatabase extends Dexie {
  constructor() {
    super(DB_NAME);
    this.version(DB_VERSION)
      .stores({
        [STORE_TRACKS]: 'trackId, entityVersion, updatedAt',
        [STORE_GEOMETRY]: 'cacheKey, trackId, precision, updatedAt',
        [LEGACY_STORES[0]]: null,
        [LEGACY_STORES[1]]: null,
      })
      .upgrade((tx) =>
        Promise.all([tx.table(STORE_TRACKS).clear(), tx.table(STORE_GEOMETRY).clear()]).then(() => {
          logTrackCache('IndexedDB upgraded: old track cache invalidated', { dbVersion: DB_VERSION });
        })
      );
  }

  get tracks(): Table<CachedTrack, number> {
    return this.table(STORE_TRACKS);
  }

  get geometry(): Table<CachedGeometry, string> {
    return this.table(STORE_GEOMETRY);
  }
}

let db = new TrackCacheDatabase();
let cacheGeneration = 0;
let cacheResetInFlight: Promise<void> | null = null;
let cacheMutationTail: Promise<void> = Promise.resolve();

export function makeTrackGeometryCacheKey(trackId: number, precision: number): string {
  return `${trackId}_${precision}`;
}

async function waitForCacheReset(): Promise<void> {
  if (cacheResetInFlight) {
    await cacheResetInFlight;
  }
}

async function enqueueCacheMutation<T>(operation: () => Promise<T>): Promise<T> {
  const previousMutation = cacheMutationTail.catch(() => {});
  let releaseMutation!: () => void;
  const currentMutation = previousMutation.then(
    () =>
      new Promise<void>((resolve) => {
        releaseMutation = resolve;
      })
  );
  cacheMutationTail = currentMutation;

  await previousMutation;
  try {
    return await operation();
  } finally {
    releaseMutation();
  }
}

export async function saveTrackRecords(tracks: Map<number, GpsTrack>): Promise<void> {
  if (tracks.size === 0) return;
  const generation = cacheGeneration;
  await waitForCacheReset();
  await enqueueCacheMutation(async () => {
    if (generation !== cacheGeneration) return;
    const targetDb = db;
    try {
      const now = Date.now();
      const records = [...tracks].map(([trackId, gpsTrack]) => ({
        trackId,
        gpsTrack,
        entityVersion: gpsTrack.version ?? 0,
        updatedAt: now,
      }));
      if (generation !== cacheGeneration || targetDb !== db) return;
      await targetDb.tracks.bulkPut(records);
    } catch (e) {
      if (generation !== cacheGeneration || targetDb !== db) return;
      console.error('Failed to save tracks to IndexedDB:', e);
    }
  });
}

export async function saveGeometryRecords(entries: TrackGeometryEntry[]): Promise<void> {
  if (entries.length === 0) return;
  const generation = cacheGeneration;
  await waitForCacheReset();
  await enqueueCacheMutation(async () => {
    if (generation !== cacheGeneration) return;
    const targetDb = db;
    try {
      const now = Date.now();
      const records = buildGeometryRecords(entries, now);
      if (generation !== cacheGeneration || targetDb !== db) return;
      await targetDb.geometry.bulkPut(records);
    } catch (e) {
      if (generation !== cacheGeneration || targetDb !== db) return;
      console.error('Failed to save geometry to IndexedDB:', e);
    }
  });
}

function buildGeometryRecords(entries: TrackGeometryEntry[], updatedAt: number): CachedGeometry[] {
  return entries.map((entry) => ({
    cacheKey: makeTrackGeometryCacheKey(entry.trackId, entry.precision),
    trackId: entry.trackId,
    precision: entry.precision,
    coordinates: entry.coordinates,
    updatedAt,
  }));
}

export async function saveTrackBatchRecords(
  tracks: Map<number, GpsTrack>,
  geometryEntries: TrackGeometryEntry[]
): Promise<void> {
  if (tracks.size === 0 && geometryEntries.length === 0) return;
  const generation = cacheGeneration;
  await waitForCacheReset();
  await enqueueCacheMutation(async () => {
    if (generation !== cacheGeneration) return;
    const targetDb = db;
    try {
      const now = Date.now();
      const trackRecords = [...tracks].map(([trackId, gpsTrack]) => ({
        trackId,
        gpsTrack,
        entityVersion: gpsTrack.version ?? 0,
        updatedAt: now,
      }));
      const geometryRecords = buildGeometryRecords(geometryEntries, now);
      if (generation !== cacheGeneration || targetDb !== db) return;
      await targetDb.transaction('rw', targetDb.tracks, targetDb.geometry, async () => {
        if (trackRecords.length > 0) await targetDb.tracks.bulkPut(trackRecords);
        if (geometryRecords.length > 0) await targetDb.geometry.bulkPut(geometryRecords);
      });
    } catch (e) {
      if (generation !== cacheGeneration || targetDb !== db) return;
      console.error('Failed to save track batch to IndexedDB:', e);
    }
  });
}

export async function deleteGeometryForTracks(trackIds: number[]): Promise<void> {
  if (trackIds.length === 0) return;
  const generation = cacheGeneration;
  await waitForCacheReset();
  await enqueueCacheMutation(async () => {
    if (generation !== cacheGeneration) return;
    const targetDb = db;
    try {
      if (generation !== cacheGeneration || targetDb !== db) return;
      await targetDb.geometry.where('trackId').anyOf(trackIds).delete();
    } catch (e) {
      if (generation !== cacheGeneration || targetDb !== db) return;
      console.error('Failed to delete stale geometry from IndexedDB:', e);
    }
  });
}

export async function loadAllTrackRecords(): Promise<Map<number, CachedTrack>> {
  try {
    const records = await db.tracks.toArray();
    const map = new Map<number, CachedTrack>();
    for (const rec of records) {
      map.set(rec.trackId, rec);
    }
    return map;
  } catch (error) {
    console.warn('Failed to load track records from IndexedDB:', error);
    return new Map();
  }
}

export async function loadTrackRecords(trackIds: number[]): Promise<Map<number, CachedTrack>> {
  if (trackIds.length === 0) return new Map();
  try {
    const records = await db.tracks.bulkGet(trackIds);
    const map = new Map<number, CachedTrack>();
    for (const rec of records) {
      if (rec) map.set(rec.trackId, rec);
    }
    return map;
  } catch (error) {
    console.warn('Failed to load requested track records from IndexedDB:', error);
    return new Map();
  }
}

export async function loadAllTracks(): Promise<Map<number, GpsTrack>> {
  const records = await loadAllTrackRecords();
  const tracks = new Map<number, GpsTrack>();
  for (const [trackId, rec] of records) {
    tracks.set(trackId, rec.gpsTrack);
  }
  return tracks;
}

export async function loadTrack(trackId: number): Promise<GpsTrack | null> {
  try {
    return (await db.tracks.get(trackId))?.gpsTrack ?? null;
  } catch (error) {
    console.warn('Failed to load track from IndexedDB:', error);
    return null;
  }
}

export async function loadGeometry(trackId: number, precision: number): Promise<number[][] | null> {
  try {
    const record = await db.geometry.get(makeTrackGeometryCacheKey(trackId, precision));
    return record?.coordinates ?? null;
  } catch (error) {
    console.warn('Failed to load track geometry from IndexedDB:', error);
    return null;
  }
}

export async function loadGeometryForTracksAtPrecision(
  trackIds: number[],
  precision: number
): Promise<Map<number, number[][]>> {
  if (trackIds.length === 0) return new Map();
  try {
    const keys = trackIds.map((trackId) => makeTrackGeometryCacheKey(trackId, precision));
    const records = await db.geometry.bulkGet(keys);
    const map = new Map<number, number[][]>();
    for (const rec of records) {
      if (rec) map.set(rec.trackId, rec.coordinates);
    }
    return map;
  } catch (error) {
    console.warn('Failed to load requested track geometry from IndexedDB:', error);
    return new Map();
  }
}

export function selectBestGeometry(
  records: CachedGeometry[]
): Map<number, { coordinates: number[][]; precision: number }> {
  const map = new Map<number, { coordinates: number[][]; precision: number }>();
  for (const rec of records) {
    const existing = map.get(rec.trackId);
    if (!existing || rec.precision < existing.precision) {
      map.set(rec.trackId, { coordinates: rec.coordinates, precision: rec.precision });
    }
  }
  return map;
}

export async function loadBestGeometry(): Promise<Map<number, { coordinates: number[][]; precision: number }>> {
  try {
    return selectBestGeometry(await db.geometry.toArray());
  } catch (error) {
    console.warn('Failed to load best track geometry from IndexedDB:', error);
    return new Map();
  }
}

export async function clearTrackCacheDb(): Promise<void> {
  if (cacheResetInFlight) {
    await cacheResetInFlight;
    return;
  }

  const startedAt = trackCacheNow();
  logTrackCache('manual cache clear started');
  const resetPromise = enqueueCacheMutation(async () => {
    const databaseToClear = db;
    await databaseToClear.transaction('rw', databaseToClear.tracks, databaseToClear.geometry, async () => {
      await databaseToClear.tracks.clear();
      await databaseToClear.geometry.clear();
    });
    cacheGeneration += 1;
    databaseToClear.close();
    db = new TrackCacheDatabase();
    logTrackCache('manual cache clear complete', { durationMs: trackCacheElapsedMs(startedAt) });
  });

  cacheResetInFlight = resetPromise;
  try {
    await resetPromise;
  } catch (e) {
    console.error('Failed to clear track cache:', e);
    throw e;
  } finally {
    if (cacheResetInFlight === resetPromise) {
      cacheResetInFlight = null;
    }
  }
}

export async function isTrackCacheDbPopulated(): Promise<boolean> {
  try {
    return (await db.tracks.count()) > 0;
  } catch (error) {
    console.warn('Failed to read track cache population state:', error);
    return false;
  }
}
