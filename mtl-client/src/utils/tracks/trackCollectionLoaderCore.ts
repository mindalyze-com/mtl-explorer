import pLimit from 'p-limit';
import type { GpsTrack } from 'x8ing-mtl-api-typescript-fetch';
import type { FilterResult } from '@/types/filter';
import {
  BACKGROUND_TRACK_PRECISION,
  DETAIL_TRACK_PRECISION,
  isCacheableTrackPrecision,
  OVERVIEW_PRECISION,
  TRACK_LOAD_BATCH_CONCURRENCY,
  TRACK_LOAD_BATCH_SIZE,
} from '@/utils/tracks/trackConstants';
import { buildTrackLoadResult } from '@/utils/tracks/trackFeatureAdapter';
import { describeError, startStartupTimer } from '@/utils/startupDiagnostics';
import { logTrackCache, trackCacheElapsedMs, trackCacheNow } from '@/utils/tracks/trackCacheLog';
import type {
  CachedTrack,
  ActiveTrackFilterRequest,
  TrackBatchPayload,
  TrackCollectionLoadOptions,
  TrackFilterApplyOptions,
  TrackFilterResultWithRequest,
  TrackGeometryEntry,
  TrackLoadResult,
  TrackCollectionPageCallback,
  TrackPrecisionResult,
} from '@/utils/tracks/trackTypes';

interface CacheComparisonResult {
  cachedTracks: Map<number, GpsTrack>;
  cachedGeometry: Map<number, number[][]>;
  staleOrMissingIds: number[];
  invalidGeometryTrackIds: number[];
}

export interface TrackCacheGateway {
  clear(): Promise<void>;
  isPopulated(): Promise<boolean>;
  saveTracks(tracks: Map<number, GpsTrack>): Promise<void>;
  saveGeometry(entries: TrackGeometryEntry[]): Promise<void>;
  saveTrackBatch(tracks: Map<number, GpsTrack>, geometryEntries: TrackGeometryEntry[]): Promise<void>;
  deleteGeometryForTracks(trackIds: number[]): Promise<void>;
  loadTrackRecords(trackIds: number[]): Promise<Map<number, CachedTrack>>;
  loadAllTracks(): Promise<Map<number, GpsTrack>>;
  loadTrack(trackId: number): Promise<GpsTrack | null>;
  loadGeometry(trackId: number, precision: number): Promise<number[][] | null>;
  loadGeometryForTracksAtPrecision(trackIds: number[], precision: number): Promise<Map<number, number[][]>>;
  loadBestGeometry(): Promise<Map<number, { coordinates: number[][]; precision: number }>>;
}

export interface TrackCollectionLoaderDeps {
  cache: TrackCacheGateway;
  fetchFilteredTrackIds(signal?: AbortSignal): Promise<FilterResult>;
  loadActiveFilterRequest?(): Promise<ActiveTrackFilterRequest>;
  fetchTrackBatch(args: {
    precision: number;
    trackIds: number[];
    filterRequest?: ActiveTrackFilterRequest;
    signal?: AbortSignal;
  }): Promise<TrackBatchPayload>;
  fetchDetailTrack(args: {
    trackId: number;
    precision: number;
    signal?: AbortSignal;
  }): Promise<{ coordinates: number[][]; gpsTrack: GpsTrack }>;
}

interface BulkLoadRecord {
  precision: number;
  promise: Promise<TrackLoadResult>;
  pageCallbacks: Set<TrackCollectionPageCallback>;
}

function trackMapFromRecords(records: Map<number, CachedTrack>): Map<number, GpsTrack> {
  const tracks = new Map<number, GpsTrack>();
  for (const [trackId, rec] of records) {
    tracks.set(trackId, rec.gpsTrack);
  }
  return tracks;
}

function throwIfAborted(signal?: AbortSignal): void {
  if (signal?.aborted) throw new DOMException('Aborted', 'AbortError');
}

async function waitForPromiseOrAbort<T>(promise: Promise<T>, signal?: AbortSignal): Promise<T> {
  if (!signal) return promise;
  throwIfAborted(signal);
  return new Promise<T>((resolve, reject) => {
    const abort = () => reject(new DOMException('Aborted', 'AbortError'));
    signal.addEventListener('abort', abort, { once: true });
    promise.then(
      (value) => {
        signal.removeEventListener('abort', abort);
        resolve(value);
      },
      (error) => {
        signal.removeEventListener('abort', abort);
        reject(error);
      }
    );
  });
}

export class TrackCollectionLoader {
  private readonly bulkInFlight = new Map<string, BulkLoadRecord>();
  private readonly bulkWaitByPrecision = new Map<number, Set<Promise<void>>>();
  private readonly detailInFlight = new Map<string, Promise<TrackPrecisionResult>>();

  constructor(private readonly deps: TrackCollectionLoaderDeps) {}

  private registerBulkLoad(key: string, record: BulkLoadRecord): void {
    this.bulkInFlight.set(key, record);
    void record.promise
      .finally(() => {
        if (this.bulkInFlight.get(key) === record) {
          this.bulkInFlight.delete(key);
        }
      })
      .catch(() => {});
  }

  private registerBulkWait(precision: number, promise: Promise<unknown>): void {
    if (!isCacheableTrackPrecision(precision)) return;
    const bulkDone = promise.then(
      () => {},
      () => {}
    );
    let precisionWaits = this.bulkWaitByPrecision.get(precision);
    if (!precisionWaits) {
      precisionWaits = new Set();
      this.bulkWaitByPrecision.set(precision, precisionWaits);
    }
    precisionWaits.add(bulkDone);
    void bulkDone
      .finally(() => {
        precisionWaits.delete(bulkDone);
        if (precisionWaits.size === 0) {
          this.bulkWaitByPrecision.delete(precision);
        }
      })
      .catch(() => {});
  }

  private getBulkWaitForPrecision(precision: number): Promise<void> | null {
    const waits = this.bulkWaitByPrecision.get(precision);
    if (!waits || waits.size === 0) return null;
    return Promise.all([...waits]).then(() => {});
  }

  private async resolveActiveFilterRequest(filterResult: FilterResult): Promise<ActiveTrackFilterRequest | undefined> {
    const resultWithRequest = filterResult as TrackFilterResultWithRequest;
    return resultWithRequest.activeFilterRequest ?? this.deps.loadActiveFilterRequest?.();
  }

  private makeBulkLoadKey(
    precision: number,
    filterResult: FilterResult,
    filterRequest: ActiveTrackFilterRequest | undefined
  ): string {
    return stableStringify({
      precision,
      versionSignature: versionMapSignature(filterResult.trackVersions),
      standardFilterCount: filterResult.standardFilterCount,
      filterRequest,
    });
  }

  private async loadBestGeometryForTrackIds(
    trackIds: number[],
    signal?: AbortSignal
  ): Promise<{
    tracksById: Map<number, GpsTrack>;
    geometryByTrackId: Map<number, number[][]>;
    precisionByTrackId: Map<number, number>;
  }> {
    throwIfAborted(signal);
    const [trackRecords, overviewGeometry, backgroundGeometry] = await Promise.all([
      this.deps.cache.loadTrackRecords(trackIds),
      this.deps.cache.loadGeometryForTracksAtPrecision(trackIds, OVERVIEW_PRECISION),
      this.deps.cache.loadGeometryForTracksAtPrecision(trackIds, BACKGROUND_TRACK_PRECISION),
    ]);
    throwIfAborted(signal);
    const tracksById = trackMapFromRecords(trackRecords);
    const geometryByTrackId = new Map<number, number[][]>();
    const precisionByTrackId = new Map<number, number>();

    for (const [trackId, coordinates] of overviewGeometry) {
      geometryByTrackId.set(trackId, coordinates);
      precisionByTrackId.set(trackId, OVERVIEW_PRECISION);
    }
    for (const [trackId, coordinates] of backgroundGeometry) {
      geometryByTrackId.set(trackId, coordinates);
      precisionByTrackId.set(trackId, BACKGROUND_TRACK_PRECISION);
    }

    return { tracksById, geometryByTrackId, precisionByTrackId };
  }

  private makePageCallback(options: TrackCollectionLoadOptions): TrackCollectionPageCallback | null {
    if (!options.onPage) return null;
    return (page) => {
      if (options.signal?.aborted) return;
      return options.onPage!(page);
    };
  }

  private async compareCacheWithServer(
    trackIds: number[],
    serverVersions: Map<number, number>,
    precision: number
  ): Promise<CacheComparisonResult> {
    const [cachedTrackRecords, cachedGeometry] = await Promise.all([
      this.deps.cache.loadTrackRecords(trackIds),
      this.deps.cache.loadGeometryForTracksAtPrecision(trackIds, precision),
    ]);
    const cachedTracks = trackMapFromRecords(cachedTrackRecords);
    const staleOrMissingIds: number[] = [];
    const invalidGeometryTrackIds: number[] = [];

    for (const trackId of trackIds) {
      const cached = cachedTrackRecords.get(trackId);
      const hasServerVersion = serverVersions.has(trackId);
      const serverVersion = serverVersions.get(trackId);
      if (!cached || !hasServerVersion || cached.entityVersion !== serverVersion) {
        staleOrMissingIds.push(trackId);
        invalidGeometryTrackIds.push(trackId);
      } else if (!cachedGeometry.has(trackId)) {
        staleOrMissingIds.push(trackId);
      }
    }

    return {
      cachedTracks,
      cachedGeometry,
      staleOrMissingIds,
      invalidGeometryTrackIds,
    };
  }

  async loadCollectionPaged(precision: number, options: TrackCollectionLoadOptions = {}): Promise<TrackLoadResult> {
    if (!isCacheableTrackPrecision(precision)) {
      throw new Error(`Precision ${precision}m is not a cacheable collection precision`);
    }

    const loadPromise = this.loadCollectionPagedDeduped(precision, options);
    this.registerBulkWait(precision, loadPromise);
    return loadPromise;
  }

  private async loadCollectionPagedDeduped(
    precision: number,
    options: TrackCollectionLoadOptions
  ): Promise<TrackLoadResult> {
    throwIfAborted(options.signal);
    const filterResult = options.filterResult ?? (await this.deps.fetchFilteredTrackIds(options.signal));
    const filterRequest = await this.resolveActiveFilterRequest(filterResult);
    const loadKey = this.makeBulkLoadKey(precision, filterResult, filterRequest);
    const existingLoad = this.bulkInFlight.get(loadKey);
    const pageCallback = this.makePageCallback(options);

    if (existingLoad) {
      // Late subscribers share the final result and only receive pages emitted after they join.
      if (pageCallback) existingLoad.pageCallbacks.add(pageCallback);
      return existingLoad.promise;
    }

    const pageCallbacks = new Set<TrackCollectionPageCallback>();
    if (pageCallback) pageCallbacks.add(pageCallback);
    const loadPromise = this.loadCollectionPagedInternal(
      precision,
      { ...options, filterResult, onPage: undefined },
      filterRequest,
      pageCallbacks
    );
    this.registerBulkLoad(loadKey, { precision, promise: loadPromise, pageCallbacks });
    return loadPromise;
  }

  private async loadCollectionPagedInternal(
    precision: number,
    options: TrackCollectionLoadOptions,
    filterRequest: ActiveTrackFilterRequest | undefined,
    pageCallbacks: Set<TrackCollectionPageCallback>
  ): Promise<TrackLoadResult> {
    const effectivePageSize = Math.max(1, options.pageSize ?? TRACK_LOAD_BATCH_SIZE);
    const effectiveConcurrency = Math.max(1, options.requestConcurrency ?? TRACK_LOAD_BATCH_CONCURRENCY);
    const timer = startStartupTimer('trackLoader', 'Paged track collection load', {
      precision,
      pageSize: effectivePageSize,
      requestConcurrency: effectiveConcurrency,
    });
    const startedAt = trackCacheNow();

    try {
      throwIfAborted(options.signal);
      const filterResult: TrackFilterResultWithRequest = {
        ...(options.filterResult! as TrackFilterResultWithRequest),
        filterGroups: new Map(options.filterResult!.filterGroups),
      };
      const serverVersions = filterResult.trackVersions;
      const filterGroups = filterResult.filterGroups;
      const trackIds = [...serverVersions.keys()];
      const comparison = await this.compareCacheWithServer(trackIds, serverVersions, precision);
      const { cachedTracks, cachedGeometry, staleOrMissingIds, invalidGeometryTrackIds } = comparison;
      const batches: number[][] = [];

      for (let start = 0; start < staleOrMissingIds.length; start += effectivePageSize) {
        batches.push(staleOrMissingIds.slice(start, start + effectivePageSize));
      }
      const cachedCount = trackIds.length - staleOrMissingIds.length;
      logTrackCache(
        staleOrMissingIds.length > 0
          ? `sync ${precision}m: fetching ${staleOrMissingIds.length}/${trackIds.length} tracks in ${batches.length} batches`
          : `sync ${precision}m: cache hit for ${trackIds.length} tracks`,
        {
          precision,
          totalTracks: trackIds.length,
          cachedTracks: cachedCount,
          staleOrMissingTracks: staleOrMissingIds.length,
          staleGeometryCleared: invalidGeometryTrackIds.length,
          batchSize: effectivePageSize,
          concurrency: batches.length === 0 ? 0 : Math.min(effectiveConcurrency, batches.length),
        }
      );

      let onPageChain = Promise.resolve();
      const emitPage = async (page: TrackLoadResult) => {
        if (pageCallbacks.size === 0) return;
        onPageChain = onPageChain.then(() =>
          Promise.all([...pageCallbacks].map((callback) => callback(page))).then(() => {})
        );
        await onPageChain;
      };

      if (batches.length > 0) {
        if (invalidGeometryTrackIds.length > 0) {
          await this.deps.cache.deleteGeometryForTracks(invalidGeometryTrackIds);
          for (const trackId of invalidGeometryTrackIds) {
            cachedGeometry.delete(trackId);
          }
        }

        const limit = pLimit(effectiveConcurrency);
        await Promise.all(
          batches.map((batchIds) =>
            limit(async () => {
              throwIfAborted(options.signal);
              const payload = await this.deps.fetchTrackBatch({
                precision,
                trackIds: batchIds,
                filterRequest,
                signal: options.signal,
              });

              await this.deps.cache.saveTrackBatch(
                payload.tracksById,
                [...payload.geometryByTrackId].map(([trackId, coordinates]) => ({
                  trackId,
                  precision,
                  coordinates,
                }))
              );

              for (const [trackId, track] of payload.tracksById) {
                cachedTracks.set(trackId, track);
              }
              for (const [trackId, coordinates] of payload.geometryByTrackId) {
                cachedGeometry.set(trackId, coordinates);
              }
              for (const [trackId, group] of payload.filterGroups) {
                filterGroups.set(trackId, group);
              }

              const pageTrackIds = [...payload.tracksById.keys()];
              const pageResult = buildTrackLoadResult({
                trackIds: pageTrackIds,
                tracksById: payload.tracksById,
                geometryByTrackId: payload.geometryByTrackId,
                defaultPrecision: precision,
                filterResult,
                standardFilterCount: filterResult.standardFilterCount,
              });
              await emitPage(pageResult);
            })
          )
        );
        await onPageChain;
      }

      timer.success('Paged track collection load completed', {
        precision,
        trackCount: trackIds.length,
        fetched: staleOrMissingIds.length,
        pageSize: effectivePageSize,
        requestConcurrency: batches.length === 0 ? 0 : Math.min(effectiveConcurrency, batches.length),
      });
      logTrackCache(`sync ${precision}m complete`, {
        precision,
        totalTracks: trackIds.length,
        fetchedTracks: staleOrMissingIds.length,
        cachedTracks: cachedCount,
        batches: batches.length,
        durationMs: trackCacheElapsedMs(startedAt),
      });

      return buildTrackLoadResult({
        trackIds,
        tracksById: cachedTracks,
        geometryByTrackId: cachedGeometry,
        defaultPrecision: precision,
        filterResult,
        standardFilterCount: filterResult.standardFilterCount,
      });
    } catch (error) {
      timer.error('Paged track collection load failed', describeError(error));
      throw error;
    }
  }

  async applyFilter(options: TrackFilterApplyOptions = {}): Promise<TrackLoadResult> {
    const timer = startStartupTimer('trackLoader', 'Applying filter from cache');
    const startedAt = trackCacheNow();
    try {
      const filterResult = options.filterResult ?? (await this.deps.fetchFilteredTrackIds(options.signal));
      const overviewResult = await this.loadCollectionPaged(OVERVIEW_PRECISION, {
        signal: options.signal,
        filterResult,
      });
      const effectiveFilterResult = overviewResult.filterResult ?? filterResult;
      const trackIds = [...effectiveFilterResult.trackVersions.keys()];

      const { tracksById, geometryByTrackId, precisionByTrackId } = await this.loadBestGeometryForTrackIds(
        trackIds,
        options.signal
      );

      const result = buildTrackLoadResult({
        trackIds,
        tracksById,
        geometryByTrackId,
        precisionByTrackId,
        defaultPrecision: OVERVIEW_PRECISION,
        filterResult: effectiveFilterResult,
        standardFilterCount: effectiveFilterResult.standardFilterCount,
      });
      timer.success('Filter applied from cache', {
        matchedCount: result.gpsTracksById.size,
        requestedCount: trackIds.length,
      });
      logTrackCache('filter applied from cache', {
        requestedTracks: trackIds.length,
        renderedTracks: result.gpsTracksById.size,
        standardFilterCount: result.standardFilterCount,
        durationMs: trackCacheElapsedMs(startedAt),
      });
      return result;
    } catch (error) {
      timer.error('Filter apply failed', describeError(error));
      throw error;
    }
  }

  async loadFromCache(): Promise<TrackLoadResult | null> {
    const timer = startStartupTimer('trackLoader', 'Loading tracks from cache');
    const startedAt = trackCacheNow();
    try {
      const [tracksById, bestGeometry] = await Promise.all([
        this.deps.cache.loadAllTracks(),
        this.deps.cache.loadBestGeometry(),
      ]);
      if (tracksById.size === 0) {
        timer.warn('No cached tracks available');
        logTrackCache('cache read: empty', { durationMs: trackCacheElapsedMs(startedAt) });
        return null;
      }

      const geometryByTrackId = new Map<number, number[][]>();
      const precisionByTrackId = new Map<number, number>();
      for (const [trackId, geo] of bestGeometry) {
        geometryByTrackId.set(trackId, geo.coordinates);
        precisionByTrackId.set(trackId, geo.precision);
      }

      const result = buildTrackLoadResult({
        trackIds: [...tracksById.keys()],
        tracksById,
        geometryByTrackId,
        precisionByTrackId,
        defaultPrecision: OVERVIEW_PRECISION,
        standardFilterCount: tracksById.size,
        offline: true,
      });
      timer.success('Loaded tracks from cache', { featureCount: result.geojson.features.length });
      logTrackCache('cache read complete', {
        tracks: tracksById.size,
        geometries: bestGeometry.size,
        renderedTracks: result.geojson.features.length,
        durationMs: trackCacheElapsedMs(startedAt),
      });
      return result;
    } catch (error) {
      timer.error('Cache load failed', describeError(error));
      return null;
    }
  }

  async readCachedTrackAtPrecision(
    trackId: number,
    precision: number,
    options: { signal?: AbortSignal; waitForBulk?: boolean } = {}
  ): Promise<TrackPrecisionResult | null> {
    if (options.waitForBulk !== false && isCacheableTrackPrecision(precision)) {
      const bulk = this.getBulkWaitForPrecision(precision);
      if (bulk) await waitForPromiseOrAbort(bulk, options.signal);
    }
    throwIfAborted(options.signal);

    const [coordinates, gpsTrack] = await Promise.all([
      this.deps.cache.loadGeometry(trackId, precision),
      this.deps.cache.loadTrack(trackId),
    ]);

    if (!coordinates || !gpsTrack) return null;
    return { coordinates, gpsTrack, fromCache: true };
  }

  async readBestCachedTrackShape(
    trackId: number,
    options: { signal?: AbortSignal } = {}
  ): Promise<TrackPrecisionResult | null> {
    return (
      (await this.readCachedTrackAtPrecision(trackId, BACKGROUND_TRACK_PRECISION, {
        signal: options.signal,
        waitForBulk: false,
      })) ??
      (await this.readCachedTrackAtPrecision(trackId, OVERVIEW_PRECISION, {
        signal: options.signal,
        waitForBulk: true,
      }))
    );
  }

  async fetchDetailTrackAtPrecision(
    trackId: number,
    precision: number,
    signal?: AbortSignal
  ): Promise<TrackPrecisionResult> {
    if (isCacheableTrackPrecision(precision)) {
      throw new Error(`Precision ${precision}m is cacheable and must be loaded through collection batches`);
    }
    if (precision !== DETAIL_TRACK_PRECISION) {
      throw new Error(`Precision ${precision}m is not an allowed per-track detail precision`);
    }

    const detailKey = `${trackId}_${precision}`;
    const existing = this.detailInFlight.get(detailKey);
    if (existing) {
      return waitForPromiseOrAbort(existing, signal);
    }

    // The shared request intentionally has no caller AbortSignal; each caller can abort its own wait
    // without cancelling the network request for other callers sharing the same detail fetch.
    const promise = this.deps
      .fetchDetailTrack({ trackId, precision })
      .then(({ coordinates, gpsTrack }) => ({ coordinates, gpsTrack, fromCache: false }));
    this.detailInFlight.set(detailKey, promise);
    void promise
      .finally(() => {
        if (this.detailInFlight.get(detailKey) === promise) {
          this.detailInFlight.delete(detailKey);
        }
      })
      .catch(() => {});
    return waitForPromiseOrAbort(promise, signal);
  }

  async clearCache(): Promise<void> {
    // In-flight HTTP requests are not cancelled here; cache-generation guards in the DB layer
    // keep any late writes from repopulating a manually cleared cache.
    this.bulkInFlight.clear();
    this.bulkWaitByPrecision.clear();
    this.detailInFlight.clear();
    await this.deps.cache.clear();
  }

  isCachePopulated(): Promise<boolean> {
    return this.deps.cache.isPopulated();
  }
}

export function createTrackCollectionLoader(deps: TrackCollectionLoaderDeps): TrackCollectionLoader {
  return new TrackCollectionLoader(deps);
}

function stableStringify(value: unknown): string {
  return JSON.stringify(toStableSerializable(value));
}

function toStableSerializable(value: unknown): unknown {
  if (typeof value === 'undefined') return { __type: 'Undefined' };
  if (value === null) return null;
  if (typeof value !== 'object') {
    if (typeof value === 'bigint') return `${value.toString()}n`;
    if (typeof value === 'number' && !Number.isFinite(value)) return { __type: 'Number', value: String(value) };
    if (typeof value === 'function' || typeof value === 'symbol') {
      return { __type: typeof value, value: String(value) };
    }
    return value;
  }
  if (value instanceof Date) {
    return Number.isNaN(value.getTime()) ? { __type: 'Date', value: 'Invalid Date' } : value.toISOString();
  }
  if (value instanceof Map) {
    return {
      __type: 'Map',
      entries: [...value.entries()]
        .map(([key, entryValue]) => [toStableSerializable(key), toStableSerializable(entryValue)])
        .sort(([leftKey], [rightKey]) => stableSortKey(leftKey).localeCompare(stableSortKey(rightKey))),
    };
  }
  if (value instanceof Set) {
    return {
      __type: 'Set',
      values: [...value.values()]
        .map((entry) => toStableSerializable(entry))
        .sort((left, right) => stableSortKey(left).localeCompare(stableSortKey(right))),
    };
  }
  if (Array.isArray(value)) {
    return value.map((entry) => toStableSerializable(entry));
  }

  const record = value as Record<string, unknown>;
  return Object.fromEntries(
    Object.keys(record)
      .sort()
      .map((key) => [key, toStableSerializable(record[key])])
  );
}

function stableSortKey(value: unknown): string {
  return JSON.stringify(value) ?? String(value);
}

function versionMapSignature(trackVersions: Map<number, number>): string {
  let hash = 2166136261;
  const sortedVersions = [...trackVersions].sort(([leftTrackId], [rightTrackId]) => leftTrackId - rightTrackId);
  for (const [trackId, version] of sortedVersions) {
    hash ^= trackId;
    hash = Math.imul(hash, 16777619);
    hash ^= version;
    hash = Math.imul(hash, 16777619);
  }
  return `${trackVersions.size}:${hash >>> 0}`;
}
