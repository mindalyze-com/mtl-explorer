import type { GpsTrack } from 'x8ing-mtl-api-typescript-fetch';
import { fetchTracksFromServer, fetchTrackPrecise, fetchFilteredIds } from '@/utils/ServiceHelper';
import { describeError, startStartupTimer, startupLog } from '@/utils/startupDiagnostics';
import type { FilterResult } from '@/types/filter';

/**
 * TrackStore — single source of truth for all track data on the client.
 *
 * Architecture:
 * 1. On startup (after login), performs ONE full load of all tracks at overview
 *    precision. Both geometry and full metadata are cached in IndexedDB.
 * 2. For filter operations, the server returns only track IDs + entity versions.
 *    The client compares versions against its cache and selectively re-fetches
 *    only stale or missing tracks.
 * 3. IndexedDB is the single entry point for all track data. The in-memory maps
 *    are projections of the persisted store.
 */

const DB_NAME = 'mtl_db';
const STORE_TRACKS = 'tracks';         // track metadata (GpsTrack objects) + entity version
const STORE_GEOMETRY = 'track_geometry'; // geometry at different precisions
const DB_VERSION = 5; // Fresh start: no legacy stores

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export interface CachedTrack {
  /** Primary key: track ID */
  trackId: number;
  /** Full GpsTrack metadata from the server */
  gpsTrack: GpsTrack;
  /** Trigger-managed version from the server entity */
  entityVersion: number;
  /** Timestamp of last update */
  updatedAt: number;
}

export interface CachedGeometry {
  /** Composite key: "{trackId}_{precision}" */
  cacheKey: string;
  trackId: number;
  precision: number;
  /** GeoJSON [lng, lat][] coordinates */
  coordinates: number[][];
  updatedAt: number;
}

export type { FilterResult } from '@/types/filter';

/** Only these precision levels are persisted to IndexedDB. */
export const CACHEABLE_PRECISIONS = [1000, 10];

export const OVERVIEW_PRECISION = 1000;

function makeCacheKey(trackId: number, precision: number): string {
  return `${trackId}_${precision}`;
}

function isCacheable(precision: number): boolean {
  return CACHEABLE_PRECISIONS.includes(precision);
}

// ---------------------------------------------------------------------------
// DB helpers
// ---------------------------------------------------------------------------

let dbPromise: Promise<IDBDatabase> | null = null;

function getDB(): Promise<IDBDatabase> {
  if (dbPromise) return dbPromise;
  dbPromise = new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION);
    request.onerror = () => {
      dbPromise = null;
      reject(request.error || new Error('Failed to open database'));
    };
    request.onsuccess = () => resolve(request.result);
    request.onupgradeneeded = () => {
      const db = request.result;

      // Drop legacy stores from previous versions
      for (const name of ['track_resolutions', 'store_meta']) {
        if (db.objectStoreNames.contains(name)) db.deleteObjectStore(name);
      }

      if (!db.objectStoreNames.contains(STORE_TRACKS)) {
        db.createObjectStore(STORE_TRACKS, { keyPath: 'trackId' });
      }
      if (!db.objectStoreNames.contains(STORE_GEOMETRY)) {
        const geoStore = db.createObjectStore(STORE_GEOMETRY, { keyPath: 'cacheKey' });
        geoStore.createIndex('trackId', 'trackId', { unique: false });
        geoStore.createIndex('precision', 'precision', { unique: false });
      }
    };
  });
  return dbPromise;
}

// ---------------------------------------------------------------------------
// Internal persistence: Track metadata
// ---------------------------------------------------------------------------

async function saveTracksToDB(tracks: Map<number, GpsTrack>): Promise<void> {
  try {
    const db = await getDB();
    const now = Date.now();
    await new Promise<void>((resolve, reject) => {
      const tx = db.transaction(STORE_TRACKS, 'readwrite');
      const store = tx.objectStore(STORE_TRACKS);
      for (const [trackId, gpsTrack] of tracks) {
        const record: CachedTrack = {
          trackId,
          gpsTrack: JSON.parse(JSON.stringify(gpsTrack)),
          entityVersion: gpsTrack.version ?? 0,
          updatedAt: now,
        };
        store.put(record);
      }
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
  } catch (e) {
    console.error('Failed to save tracks to IndexedDB:', e);
  }
}

async function loadAllTracksFromDB(): Promise<Map<number, GpsTrack>> {
  try {
    const db = await getDB();
    const records: CachedTrack[] = await new Promise((resolve, reject) => {
      const tx = db.transaction(STORE_TRACKS, 'readonly');
      const req = tx.objectStore(STORE_TRACKS).getAll();
      tx.oncomplete = () => resolve(req.result ?? []);
      tx.onerror = () => reject(tx.error);
    });
    const map = new Map<number, GpsTrack>();
    for (const rec of records) {
      map.set(rec.trackId, rec.gpsTrack);
    }
    return map;
  } catch {
    return new Map();
  }
}

async function loadOneTrackFromDB(trackId: number): Promise<GpsTrack | null> {
  const record = await loadCachedTrackRecord(trackId);
  return record?.gpsTrack ?? null;
}

async function loadCachedTrackRecord(trackId: number): Promise<CachedTrack | null> {
  try {
    const db = await getDB();
    const record: CachedTrack | undefined = await new Promise((resolve, reject) => {
      const tx = db.transaction(STORE_TRACKS, 'readonly');
      const req = tx.objectStore(STORE_TRACKS).get(trackId);
      tx.oncomplete = () => resolve(req.result);
      tx.onerror = () => reject(tx.error);
    });
    return record ?? null;
  } catch {
    return null;
  }
}

/**
 * Load cached entity versions for a set of track IDs.
 * Returns a Map<trackId, cachedEntityVersion>. Missing tracks are omitted.
 */
async function loadCachedVersions(trackIds: number[]): Promise<Map<number, number>> {
  const versions = new Map<number, number>();
  if (trackIds.length === 0) return versions;
  try {
    const db = await getDB();
    const records: CachedTrack[] = await new Promise((resolve, reject) => {
      const tx = db.transaction(STORE_TRACKS, 'readonly');
      const store = tx.objectStore(STORE_TRACKS);
      const results: CachedTrack[] = [];
      let pending = trackIds.length;
      for (const id of trackIds) {
        const req = store.get(id);
        req.onsuccess = () => {
          if (req.result) results.push(req.result);
          if (--pending === 0) resolve(results);
        };
        req.onerror = () => {
          if (--pending === 0) resolve(results);
        };
      }
    });
    for (const rec of records) {
      versions.set(rec.trackId, rec.entityVersion ?? 0);
    }
  } catch {
    // non-fatal
  }
  return versions;
}

// ---------------------------------------------------------------------------
// Internal persistence: Geometry
// ---------------------------------------------------------------------------

async function saveGeometryToDB(entries: { trackId: number; precision: number; coordinates: number[][] }[]): Promise<void> {
  if (entries.length === 0) return;
  try {
    const db = await getDB();
    const now = Date.now();
    await new Promise<void>((resolve, reject) => {
      const tx = db.transaction(STORE_GEOMETRY, 'readwrite');
      const store = tx.objectStore(STORE_GEOMETRY);
      for (const entry of entries) {
        const record: CachedGeometry = {
          cacheKey: makeCacheKey(entry.trackId, entry.precision),
          trackId: entry.trackId,
          precision: entry.precision,
          coordinates: entry.coordinates,
          updatedAt: now,
        };
        store.put(JSON.parse(JSON.stringify(record)));
      }
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
  } catch (e) {
    console.error('Failed to save geometry to IndexedDB:', e);
  }
}

async function loadGeometryFromDB(trackId: number, precision: number): Promise<number[][] | null> {
  try {
    const db = await getDB();
    const record: CachedGeometry | undefined = await new Promise((resolve, reject) => {
      const tx = db.transaction(STORE_GEOMETRY, 'readonly');
      const req = tx.objectStore(STORE_GEOMETRY).get(makeCacheKey(trackId, precision));
      tx.oncomplete = () => resolve(req.result);
      tx.onerror = () => reject(tx.error);
    });
    return record?.coordinates ?? null;
  } catch {
    return null;
  }
}

async function loadBestGeometryFromDB(trackId: number): Promise<{ coordinates: number[][]; precision: number } | null> {
  try {
    const db = await getDB();
    const records: CachedGeometry[] = await new Promise((resolve, reject) => {
      const tx = db.transaction(STORE_GEOMETRY, 'readonly');
      const index = tx.objectStore(STORE_GEOMETRY).index('trackId');
      const req = index.getAll(trackId);
      tx.oncomplete = () => resolve(req.result ?? []);
      tx.onerror = () => reject(tx.error);
    });
    if (records.length === 0) return null;
    // Return finest precision (smallest number = most detailed)
    records.sort((a, b) => a.precision - b.precision);
    return { coordinates: records[0].coordinates, precision: records[0].precision };
  } catch {
    return null;
  }
}

async function loadAllGeometryAtPrecision(precision: number): Promise<Map<number, number[][]>> {
  try {
    const db = await getDB();
    const records: CachedGeometry[] = await new Promise((resolve, reject) => {
      const tx = db.transaction(STORE_GEOMETRY, 'readonly');
      const index = tx.objectStore(STORE_GEOMETRY).index('precision');
      const req = index.getAll(precision);
      tx.oncomplete = () => resolve(req.result ?? []);
      tx.onerror = () => reject(tx.error);
    });
    const map = new Map<number, number[][]>();
    for (const rec of records) {
      map.set(rec.trackId, rec.coordinates);
    }
    return map;
  } catch {
    return new Map();
  }
}

// ---------------------------------------------------------------------------
// In-flight tracking
// ---------------------------------------------------------------------------

/** Precision → Promise that resolves when that bulk fetch finishes */
const bulkInFlight = new Map<number, Promise<void>>();

/** Prefetched result promises, set from login and consumed once by Map.vue */
const prefetchedResults = new Map<number, Promise<FullLoadResult>>();

// ---------------------------------------------------------------------------
// Public types
// ---------------------------------------------------------------------------

export interface FullLoadResult {
  geojson: GeoJSON.FeatureCollection;
  gpsTracksById: Map<number, GpsTrack>;
  gpsTrackIdToFeature: Map<number, GeoJSON.Feature>;
  trackPrecisions: Map<number, number>;
  standardFilterCount: number;
  offline?: boolean;
  /** The filter result used to build this load (available when IDs-first path was taken). */
  filterResult?: FilterResult;
}

export interface FilteredLoadResult extends FullLoadResult {
  /** The filter result that was applied */
  filterResult: FilterResult;
}

// ---------------------------------------------------------------------------
// Feature building helpers
// ---------------------------------------------------------------------------

function buildFeature(
  track: GpsTrack,
  coordinates: number[][],
  filterGroup?: string,
): GeoJSON.Feature {
  const isDegenerate = ((track.trackLengthInMeter ?? 0) < 50 || coordinates.length === 0)
    && track.centerLng != null && track.centerLat != null;
  const geometry: GeoJSON.Geometry = isDegenerate
    ? { type: 'Point', coordinates: [track.centerLng!, track.centerLat!] }
    : { type: 'LineString', coordinates };

  return {
    type: 'Feature',
    properties: {
      id: track.id,
      fileName: (track as any).indexedFile?.name,
      trackName: track.trackName,
      trackDescription: track.trackDescription,
      startDate: track.startDate,
      endDate: track.endDate,
      createDate: track.createDate,
      filterGroup,
      centerLng: track.centerLng,
      centerLat: track.centerLat,
    },
    geometry,
  };
}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

export const trackStore = {

  /** Pre-computed filter result from the live-preview resolve call. If set,
   *  applyFilter() uses it directly and skips the get-simplified?mode=ids round-trip. */
  _pendingFilterResult: null as FilterResult | null,

  /** Called by CustomFilter after a successful live-preview resolve. */
  setPendingFilterResult(result: FilterResult): void {
    this._pendingFilterResult = result;
  },

  // ── Prefetch (called early from LoginView) ──

  /**
   * Start fetching all tracks at the given precision in the background.
   * Call from LoginView so the network request runs in parallel with map init.
   */
  prefetchAllTracks(precision: number): void {
    startupLog('prefetch', 'Scheduling track prefetch', { precision });
    const p = this.fetchAllTracks(precision);
    prefetchedResults.set(precision, p);
    p.catch(() => {}); // prevent unhandled rejection noise
  },

  /**
   * Consume and clear a prefetched result. Returns null if no prefetch was
   * started or it was already consumed.
   */
  consumePrefetch(precision: number): Promise<FullLoadResult> | null {
    const p = prefetchedResults.get(precision) ?? null;
    prefetchedResults.delete(precision);
    startupLog('prefetch', p ? 'Consumed prefetched tracks' : 'No prefetched tracks available', { precision });
    return p;
  },

  // ── Unified load (always IDs-first, then delta or full) ──

  /**
   * Maximum number of individually stale/missing tracks to re-fetch one-by-one.
   * When more tracks need updating, a full bulk download is more efficient.
   */
  DELTA_THRESHOLD: 20,

  /**
   * Fetch ALL tracks at the given precision using a unified IDs-first strategy:
   *
   * 1. Always call mode=ids first (lightweight — just IDs + entity versions).
   * 2. Compare each version against the local IndexedDB cache.
   * 3. If stale/missing count ≤ DELTA_THRESHOLD → fetch them individually.
   * 4. If stale/missing count > DELTA_THRESHOLD → fall back to mode=full.
   *
   * First-ever load (empty cache) naturally falls through to mode=full because
   * every track is "missing".
   */
  async fetchAllTracks(
    precision: number,
    signal?: AbortSignal,
    filterResult?: FilterResult,
  ): Promise<FullLoadResult> {
    const timer = startStartupTimer('trackStore', 'Unified track load', { precision });

    const fetchPromise = (async () => {
      try {
        // For non-cacheable precisions, always do a full load (no IDB involved)
        if (!isCacheable(precision)) {
          return await this._fullLoad(precision, signal);
        }

        // ── Step 1: Lightweight server call — IDs + versions ──
        // Reuse a previously resolved filter result (e.g. from the overview
        // load) when available — the result is precision-independent.
        if (!filterResult) {
          filterResult = await fetchFilteredIds(signal);
        }
        const serverVersions = filterResult.trackVersions;
        const trackIds = [...serverVersions.keys()];

        // ── Step 2: Load cache and compare versions ──
        const cachedTracks = await loadAllTracksFromDB();
        const cachedGeometry = await loadAllGeometryAtPrecision(precision);

        const staleOrMissingIds: number[] = [];
        for (const trackId of trackIds) {
          const cached = cachedTracks.get(trackId);
          const cachedVersion = cached?.version ?? -1;
          const serverVersion = serverVersions.get(trackId)!;
          if (!cached || cachedVersion !== serverVersion || !cachedGeometry.has(trackId)) {
            staleOrMissingIds.push(trackId);
          }
        }

        console.log(
          `[trackStore] IDs-first comparison: ${trackIds.length} server tracks, ` +
          `${cachedTracks.size} cached tracks, ${cachedGeometry.size} cached geometries @ ${precision}m, ` +
          `${staleOrMissingIds.length} stale/missing (threshold: ${this.DELTA_THRESHOLD})`,
        );

        // ── Step 3: Decide — delta sync or full load ──
        if (staleOrMissingIds.length > this.DELTA_THRESHOLD) {
          console.log(
            `[trackStore] Delta too large (${staleOrMissingIds.length} tracks > threshold ${this.DELTA_THRESHOLD}) → falling back to full load`,
          );
          return await this._fullLoad(precision, signal);
        }

        // ── Step 4: Delta sync — fetch only the few stale/missing tracks ──
        if (staleOrMissingIds.length > 0) {
          console.log(
            `[trackStore] Delta sync: re-fetching ${staleOrMissingIds.length} changed tracks individually`,
          );
          for (const trackId of staleOrMissingIds) {
            if (signal?.aborted) throw new DOMException('Aborted', 'AbortError');
            try {
              const fetched = await fetchTrackPrecise(trackId, precision, signal);
              cachedTracks.set(trackId, fetched.gpsTrack);
              cachedGeometry.set(trackId, fetched.coordinates);
              saveTracksToDB(new Map([[trackId, fetched.gpsTrack]])).catch(e => console.error(e));
              saveGeometryToDB([{ trackId, precision, coordinates: fetched.coordinates }]).catch(e => console.error(e));
            } catch (e) {
              console.warn(`[trackStore] Delta sync: failed to re-fetch track ${trackId}`, e);
            }
          }
        } else {
          console.log('[trackStore] Cache fully up-to-date — no server data needed');
        }

        // ── Step 5: Build result from (now up-to-date) cache ──
        const gpsTracksById = new Map<number, GpsTrack>();
        const gpsTrackIdToFeature = new Map<number, GeoJSON.Feature>();
        const trackPrecisions = new Map<number, number>();
        const features: GeoJSON.Feature[] = [];

        for (const trackId of trackIds) {
          const track = cachedTracks.get(trackId);
          if (!track) continue;
          const coordinates = cachedGeometry.get(trackId) ?? [];
          const filterGroup = filterResult.filterGroups.get(trackId);
          const feature = buildFeature(track, coordinates, filterGroup);
          gpsTracksById.set(trackId, track);
          gpsTrackIdToFeature.set(trackId, feature);
          trackPrecisions.set(trackId, precision);
          features.push(feature);
        }

        timer.success('Delta sync completed', {
          precision,
          trackCount: trackIds.length,
          reFetched: staleOrMissingIds.length,
        });

        return {
          geojson: { type: 'FeatureCollection', features } as GeoJSON.FeatureCollection,
          gpsTracksById,
          gpsTrackIdToFeature,
          trackPrecisions,
          standardFilterCount: filterResult.standardFilterCount,
          filterResult,
        };
      } catch (error) {
        timer.error('Unified load failed', describeError(error));
        throw error;
      }
    })();

    // Register so requestTrackAtPrecision can wait for bulk to complete
    if (isCacheable(precision)) {
      const bulkDone = fetchPromise.then(() => {}).catch(() => {});
      bulkInFlight.set(precision, bulkDone);
      bulkDone.finally(() => bulkInFlight.delete(precision));
    }

    const data = await fetchPromise;
    const trackPrecisions = data.trackPrecisions ?? new Map<number, number>();
    if (trackPrecisions.size === 0) {
      for (const trackId of data.gpsTracksById.keys()) {
        trackPrecisions.set(Number(trackId), precision);
      }
    }
    timer.success('Unified load completed', {
      precision,
      trackCount: data.gpsTracksById.size,
    });
    return { ...data, trackPrecisions };
  },

  /**
   * Internal: full bulk load via mode=full. Used when cache is empty or
   * the delta exceeds DELTA_THRESHOLD.
   */
  async _fullLoad(
    precision: number,
    signal?: AbortSignal,
  ): Promise<FullLoadResult> {
    startupLog('trackStore', 'Executing full load (mode=full)', { precision });
    const data = await fetchTracksFromServer(precision, signal);

    await saveTracksToDB(data.gpsTracksById);

    if (isCacheable(precision)) {
      const geoEntries: { trackId: number; precision: number; coordinates: number[][] }[] = [];
      for (const [trackId, feature] of data.gpsTrackIdToFeature) {
        const geom = feature.geometry;
        // GeometryCollection has no single `coordinates` field — skip (not emitted by server).
        const coordinates: number[][] = geom && 'coordinates' in geom
          ? ((geom as GeoJSON.LineString).coordinates ?? [])
          : [];
        geoEntries.push({
          trackId: Number(trackId),
          precision,
          coordinates,
        });
      }
      await saveGeometryToDB(geoEntries);
    }

    return { ...data, trackPrecisions: new Map<number, number>() };
  },

  // ── Filter: IDs-only (the new fast path) ──

  /**
   * Apply a filter and resolve matching tracks from the local cache.
   * The server returns track IDs with their entity versions. The client
   * compares each version against its cached copy and selectively re-fetches
   * only those tracks whose version has changed.
   */
  async applyFilter(signal?: AbortSignal): Promise<FilteredLoadResult> {
    const timer = startStartupTimer('trackStore', 'Applying filter (IDs + versions)');

    try {
      // 1. Get matching IDs + versions — reuse pre-computed preview result if available
      //    (populated by CustomFilter.executeLivePreview to skip a redundant server call)
      let filterResult: FilterResult;
      if (this._pendingFilterResult) {
        filterResult = this._pendingFilterResult;
        this._pendingFilterResult = null;
        console.log('[trackStore] applyFilter: using pending preview result, skipping get-simplified?mode=ids');
      } else {
        filterResult = await fetchFilteredIds(signal);
      }
      const serverVersions = filterResult.trackVersions;
      const trackIds = [...serverVersions.keys()];

      // 2. Compare versions against cached copies
      const cachedVersions = await loadCachedVersions(trackIds);
      const staleIds: number[] = [];
      const missingIds: number[] = [];
      for (const trackId of trackIds) {
        const cached = cachedVersions.get(trackId);
        if (cached === undefined) {
          missingIds.push(trackId);
        } else if (cached !== serverVersions.get(trackId)) {
          staleIds.push(trackId);
        }
      }

      // 3. Re-fetch stale + missing tracks individually
      const toFetch = [...staleIds, ...missingIds];
      if (toFetch.length > 0) {
        console.log(`Selective re-fetch: ${staleIds.length} stale, ${missingIds.length} missing`);
        for (const trackId of toFetch) {
          if (signal?.aborted) throw new DOMException('Aborted', 'AbortError');
          try {
            const fetched = await fetchTrackPrecise(trackId, OVERVIEW_PRECISION, signal);
            saveTracksToDB(new Map([[trackId, fetched.gpsTrack]])).catch(err => console.error(err));
            if (isCacheable(OVERVIEW_PRECISION)) {
              saveGeometryToDB([{ trackId, precision: OVERVIEW_PRECISION, coordinates: fetched.coordinates }]).catch(err => console.error(err));
            }
          } catch (e) {
            console.warn(`Failed to re-fetch track ${trackId} — will use stale cache or skip`, e);
          }
        }
      }

      // 4. Resolve everything from local cache (now up-to-date)
      const gpsTracksById = new Map<number, GpsTrack>();
      const gpsTrackIdToFeature = new Map<number, GeoJSON.Feature>();
      const trackPrecisions = new Map<number, number>();
      const features: GeoJSON.Feature[] = [];

      for (const trackId of trackIds) {
        const track = await loadOneTrackFromDB(trackId);
        if (!track) {
          console.warn(`Track ${trackId} still not in cache after re-fetch — skipping`);
          continue;
        }

        const geo = await loadBestGeometryFromDB(trackId);
        const coordinates = geo?.coordinates ?? [];
        const precision = geo?.precision ?? OVERVIEW_PRECISION;

        const filterGroup = filterResult.filterGroups.get(trackId);
        const feature = buildFeature(track, coordinates, filterGroup);

        gpsTracksById.set(trackId, track);
        gpsTrackIdToFeature.set(trackId, feature);
        trackPrecisions.set(trackId, precision);
        features.push(feature);
      }

      timer.success('Filter applied', {
        matchedCount: features.length,
        requestedCount: trackIds.length,
        staleFetched: staleIds.length,
        missingFetched: missingIds.length,
        standardFilterCount: filterResult.standardFilterCount,
      });

      return {
        geojson: { type: 'FeatureCollection', features },
        gpsTracksById,
        gpsTrackIdToFeature,
        trackPrecisions,
        standardFilterCount: filterResult.standardFilterCount,
        filterResult,
      };
    } catch (error) {
      timer.error('Filter (IDs + versions) failed', describeError(error));
      throw error;
    }
  },

  // ── Individual track precision (for progressive detail loading) ──

  /**
   * Get a single track at the given precision.
   * For cacheable precisions: waits for any in-flight bulk, then checks cache.
   * For 1m: always fetches fresh from server.
   */
  async requestTrackAtPrecision(
    trackId: number,
    precision: number,
    signal?: AbortSignal,
  ): Promise<{ coordinates: number[][]; gpsTrack: GpsTrack; fromCache: boolean }> {
    if (!isCacheable(precision)) {
      const { coordinates, gpsTrack } = await fetchTrackPrecise(trackId, precision, signal);
      return { coordinates, gpsTrack, fromCache: false };
    }

    // Wait for any in-flight bulk fetch that will populate the cache
    const bulk = bulkInFlight.get(precision);
    if (bulk) await bulk;

    if (signal?.aborted) throw new DOMException('Aborted', 'AbortError');

    // Check geometry cache
    const cachedCoords = await loadGeometryFromDB(trackId, precision);
    if (cachedCoords) {
      const cachedTrack = await loadOneTrackFromDB(trackId);
      if (cachedTrack) {
        return { coordinates: cachedCoords, gpsTrack: cachedTrack, fromCache: true };
      }
    }

    // Not cached — fetch from server
    const { coordinates, gpsTrack } = await fetchTrackPrecise(trackId, precision, signal);

    // Persist both metadata and geometry
    saveTracksToDB(new Map([[trackId, gpsTrack]])).catch(err => console.error(err));
    saveGeometryToDB([{ trackId, precision, coordinates }]).catch(err => console.error(err));

    return { coordinates, gpsTrack, fromCache: false };
  },

  // ── Offline fallback ──

  /**
   * Load all cached tracks from IndexedDB at their best available precision.
   * Used for offline fallback on startup.
   */
  async loadFromCache(): Promise<FullLoadResult | null> {
    const timer = startStartupTimer('trackStore', 'Loading tracks from cache');

    try {
      const allTracks = await loadAllTracksFromDB();
      if (allTracks.size === 0) {
        timer.warn('No cached tracks available');
        return null;
      }

      const gpsTracksById = new Map<number, GpsTrack>();
      const gpsTrackIdToFeature = new Map<number, GeoJSON.Feature>();
      const trackPrecisions = new Map<number, number>();
      const features: GeoJSON.Feature[] = [];

      for (const [trackId, track] of allTracks) {
        const geo = await loadBestGeometryFromDB(trackId);
        const coordinates = geo?.coordinates ?? [];
        const precision = geo?.precision ?? OVERVIEW_PRECISION;

        const feature = buildFeature(track, coordinates);

        gpsTracksById.set(trackId, track);
        gpsTrackIdToFeature.set(trackId, feature);
        trackPrecisions.set(trackId, precision);
        features.push(feature);
      }

      timer.success('Loaded from cache', { featureCount: features.length });

      return {
        geojson: { type: 'FeatureCollection', features },
        gpsTracksById,
        gpsTrackIdToFeature,
        trackPrecisions,
        standardFilterCount: allTracks.size,
        offline: true,
      };
    } catch (e) {
      timer.error('Cache load failed', describeError(e));
      return null;
    }
  },

  // ── Unified single-track accessor ──

  /**
   * Get a single track by ID — transparently version-aware.
   *
   * • If `expectedVersion` is given and the cache holds that exact version,
   *   returns instantly from IndexedDB.
   * • If the cached version differs, or the track is missing, fetches from
   *   the server, updates the cache, and returns the fresh copy.
   * • If no `expectedVersion` is given, returns the cached copy when
   *   available, otherwise fetches.
   *
   * The consumer never needs to know whether the data came from cache or
   * network.
   */
  async getTrack(
    trackId: number,
    expectedVersion?: number,
    signal?: AbortSignal,
  ): Promise<GpsTrack> {
    // Try cache first
    const cached = await loadCachedTrackRecord(trackId);
    if (cached) {
      const versionMatch = expectedVersion === undefined
        || cached.entityVersion === expectedVersion;
      if (versionMatch) return cached.gpsTrack;
    }

    // Cache miss or version mismatch — fetch from server
    const { gpsTrack } = await fetchTrackPrecise(trackId, OVERVIEW_PRECISION, signal);
    saveTracksToDB(new Map([[trackId, gpsTrack]])).catch(err =>
      console.error(`Failed to persist track ${trackId} after re-fetch`, err));
    return gpsTrack;
  },

  /**
   * Get a single track's metadata from the cache only (no network).
   * Returns null if not cached.
   */
  async getTrackFromCache(trackId: number): Promise<GpsTrack | null> {
    return loadOneTrackFromDB(trackId);
  },

  /**
   * Get all cached track metadata. Returns the full in-DB track map.
   */
  async getAllTracksFromCache(): Promise<Map<number, GpsTrack>> {
    return loadAllTracksFromDB();
  },

  // ── Cache management ──

  async clearCache(): Promise<void> {
    try {
      const db = await getDB();
      await new Promise<void>((resolve, reject) => {
        const tx = db.transaction([STORE_TRACKS, STORE_GEOMETRY], 'readwrite');
        tx.objectStore(STORE_TRACKS).clear();
        tx.objectStore(STORE_GEOMETRY).clear();
        tx.oncomplete = () => resolve();
        tx.onerror = () => reject(tx.error);
      });
      console.log('Track store cache cleared');
      dbPromise = null;
    } catch (e) {
      console.error('Failed to clear track store cache:', e);
    }
  },

  /**
   * Check if the local cache has been populated (at least one track).
   */
  async isCachePopulated(): Promise<boolean> {
    try {
      const db = await getDB();
      const count: number = await new Promise((resolve, reject) => {
        const tx = db.transaction(STORE_TRACKS, 'readonly');
        const req = tx.objectStore(STORE_TRACKS).count();
        tx.oncomplete = () => resolve(req.result ?? 0);
        tx.onerror = () => reject(tx.error);
      });
      return count > 0;
    } catch {
      return false;
    }
  },
};
