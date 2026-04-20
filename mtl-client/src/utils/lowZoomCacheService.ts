/**
 * lowZoomCacheService.ts — Downloads and caches the low-zoom PMTiles extract
 * in the browser for offline map display at zoom levels 0–8.
 *
 * The file is stored in Cache Storage when available (secure contexts), with an
 * IndexedDB fallback for non-secure contexts (e.g. http://192.168.x.x dev access).
 *
 * Flow:
 * 1. On app start, check if the low-zoom file is already cached.
 * 2. If not, download it from the map server and store it.
 * 3. When offline, loadLowZoomFromCache() loads the cached blob into an in-memory
 *    PMTiles instance that can be registered with the Protocol — bypassing fetch().
 */

import { PMTiles } from 'pmtiles';

const CACHE_NAME = 'mtl-lowzoom-tiles';

// ---------------------------------------------------------------------------
// IndexedDB fallback (works in non-secure / plain HTTP contexts)
// ---------------------------------------------------------------------------
const IDB_NAME = 'mtl-lowzoom-cache';
const IDB_VERSION = 1;
const IDB_STORE = 'blobs';

function idbOpen(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(IDB_NAME, IDB_VERSION);
    req.onerror = () => reject(req.error ?? new Error('Failed to open lowzoom IDB'));
    req.onsuccess = () => resolve(req.result);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(IDB_STORE)) {
        db.createObjectStore(IDB_STORE);
      }
    };
  });
}

async function idbGet(key: string): Promise<ArrayBuffer | null> {
  const db = await idbOpen();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(IDB_STORE, 'readonly');
    const req = tx.objectStore(IDB_STORE).get(key);
    req.onsuccess = () => resolve(req.result ?? null);
    req.onerror = () => reject(req.error);
  });
}

async function idbPut(key: string, data: ArrayBuffer): Promise<void> {
  const db = await idbOpen();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(IDB_STORE, 'readwrite');
    tx.objectStore(IDB_STORE).put(data, key);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}

async function idbClear(): Promise<void> {
  try {
    const db = await idbOpen();
    return new Promise((resolve, reject) => {
      const tx = db.transaction(IDB_STORE, 'readwrite');
      tx.objectStore(IDB_STORE).clear();
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
  } catch {
    // DB may not exist yet — that's fine
  }
}

/** True when the Cache Storage API is available (requires secure context). */
const hasCacheStorage = 'caches' in window;

/**
 * Ensure the low-zoom PMTiles file is available in the browser cache.
 * Downloads it from the tile server if not already cached.
 *
 * @param tileBaseUrl      Base URL of the tile server (e.g. "/mtl/api/map-proxy/demo")
 * @param lowzoomTileset   Tileset name without .pmtiles (e.g. "world-lowzoom")
 * @returns The URL from which the file can be loaded (either original or cache)
 */
export async function ensureLowZoomCached(
  tileBaseUrl: string,
  lowzoomTileset: string,
): Promise<string> {
  const fileUrl = `${tileBaseUrl}/${lowzoomTileset}.pmtiles`;

  // ---- Preferred: Cache Storage (secure contexts) ----
  if (hasCacheStorage) {
    try {
      const cache = await caches.open(CACHE_NAME);
      const existing = await cache.match(fileUrl);

      if (existing) {
        console.log('Low-zoom PMTiles already cached (CacheStorage)');
        return fileUrl;
      }

      console.log(`Downloading low-zoom PMTiles from ${fileUrl} …`);
      const response = await fetch(fileUrl, { credentials: 'same-origin' });

      if (!response.ok) {
        console.warn(`Failed to download low-zoom tiles: ${response.status} ${response.statusText}`);
        return fileUrl;
      }

      await cache.put(fileUrl, response);
      console.log('Low-zoom PMTiles cached successfully (CacheStorage)');
      return fileUrl;
    } catch (e) {
      console.warn('Failed to cache low-zoom tiles (CacheStorage):', e);
      return fileUrl;
    }
  }

  // ---- Fallback: IndexedDB (works in non-secure contexts) ----
  try {
    const existing = await idbGet(fileUrl);
    if (existing) {
      console.log('Low-zoom PMTiles already cached (IndexedDB)');
      return fileUrl;
    }

    console.log(`Downloading low-zoom PMTiles from ${fileUrl} … (IndexedDB fallback)`);
    const response = await fetch(fileUrl, { credentials: 'same-origin' });

    if (!response.ok) {
      console.warn(`Failed to download low-zoom tiles: ${response.status} ${response.statusText}`);
      return fileUrl;
    }

    const buffer = await response.arrayBuffer();
    await idbPut(fileUrl, buffer);
    console.log(`Low-zoom PMTiles cached successfully (IndexedDB, ${(buffer.byteLength / 1024 / 1024).toFixed(1)} MB)`);
    return fileUrl;
  } catch (e) {
    console.warn('Failed to cache low-zoom tiles (IndexedDB):', e);
    return fileUrl;
  }
}

/** Clear the cached low-zoom tiles (both CacheStorage and IndexedDB). */
export async function clearLowZoomCache(): Promise<void> {
  if (hasCacheStorage) {
    await caches.delete(CACHE_NAME);
  }
  await idbClear();
}

/**
 * Load the cached low-zoom PMTiles blob and wrap it in an in-memory PMTiles
 * instance. This bypasses fetch() entirely, so it works when the network is
 * unavailable (e.g. DevTools offline or real connectivity loss).
 *
 * Tries CacheStorage first, then IndexedDB.
 *
 * The returned PMTiles instance can be registered on the Protocol via
 * `protocol.add(pmtiles)` so that MapLibre's `pmtiles://` URL resolution
 * reads tiles from memory instead of the network.
 *
 * Returns null if no cached blob is found.
 */
export async function loadLowZoomFromCache(
  tileBaseUrl: string,
  lowzoomTileset: string,
): Promise<PMTiles | null> {
  const fileUrl = `${tileBaseUrl}/${lowzoomTileset}.pmtiles`;

  let buffer: ArrayBuffer | null = null;
  let backend = '';

  // ---- Try CacheStorage first ----
  if (hasCacheStorage) {
    try {
      const cache = await caches.open(CACHE_NAME);
      const response = await cache.match(fileUrl);
      if (response) {
        buffer = await response.arrayBuffer();
        backend = 'CacheStorage';
      }
    } catch (e) {
      console.warn('Failed to read low-zoom tiles from CacheStorage:', e);
    }
  }

  // ---- Fallback: IndexedDB ----
  if (!buffer) {
    try {
      buffer = await idbGet(fileUrl);
      if (buffer) backend = 'IndexedDB';
    } catch (e) {
      console.warn('Failed to read low-zoom tiles from IndexedDB:', e);
    }
  }

  if (!buffer) {
    console.log('No cached low-zoom PMTiles found for offline use');
    return null;
  }

  console.log(`Loaded low-zoom PMTiles from ${backend} (${(buffer.byteLength / 1024 / 1024).toFixed(1)} MB)`);

  // Create an in-memory source that satisfies the PMTiles source interface.
  // PMTiles reads tiles via getBytes(offset, length) range requests — we
  // simply slice the ArrayBuffer instead of going to the network.
  const source = {
    getKey: () => fileUrl,
    getBytes: async (offset: number, length: number) => ({
      data: buffer!.slice(offset, offset + length),
    }),
  };

  return new PMTiles(source as any);
}
