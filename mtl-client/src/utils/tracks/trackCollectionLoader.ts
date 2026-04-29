import {
  clearTrackCacheDb,
  deleteGeometryForTracks,
  isTrackCacheDbPopulated,
  loadAllTracks,
  loadBestGeometry,
  loadGeometry,
  loadGeometryForTracksAtPrecision,
  loadTrack,
  loadTrackRecords,
  saveTrackBatchRecords,
  saveGeometryRecords,
  saveTrackRecords,
} from '@/utils/tracks/trackCacheDb';
import {
  fetchDetailTrack,
  fetchFilteredTrackIds,
  fetchTrackBatch,
  loadActiveFilterRequest,
} from '@/utils/tracks/trackApi';
import { createTrackCollectionLoader, type TrackCacheGateway } from '@/utils/tracks/trackCollectionLoaderCore';
import type {
  TrackCollectionLoadOptions,
  TrackFilterApplyOptions,
  TrackLoadResult,
  TrackPrecisionResult,
} from '@/utils/tracks/trackTypes';

export type {
  TrackCacheGateway,
  TrackCollectionLoader,
  TrackCollectionLoaderDeps,
} from '@/utils/tracks/trackCollectionLoaderCore';

const cacheGateway: TrackCacheGateway = {
  clear: clearTrackCacheDb,
  isPopulated: isTrackCacheDbPopulated,
  saveTracks: saveTrackRecords,
  saveGeometry: saveGeometryRecords,
  saveTrackBatch: saveTrackBatchRecords,
  deleteGeometryForTracks,
  loadTrackRecords,
  loadAllTracks,
  loadTrack,
  loadGeometry,
  loadGeometryForTracksAtPrecision,
  loadBestGeometry,
};

export const trackCollectionLoader = createTrackCollectionLoader({
  cache: cacheGateway,
  fetchFilteredTrackIds,
  loadActiveFilterRequest,
  fetchTrackBatch,
  fetchDetailTrack,
});

export function loadTrackCollectionPaged(
  precision: number,
  options?: TrackCollectionLoadOptions
): Promise<TrackLoadResult> {
  return trackCollectionLoader.loadCollectionPaged(precision, options);
}

export function applyTrackFilter(options?: TrackFilterApplyOptions): Promise<TrackLoadResult> {
  return trackCollectionLoader.applyFilter(options);
}

export function loadCachedTrackCollection(): Promise<TrackLoadResult | null> {
  return trackCollectionLoader.loadFromCache();
}

export function readBestCachedTrackShape(
  trackId: number,
  options?: { signal?: AbortSignal }
): Promise<TrackPrecisionResult | null> {
  return trackCollectionLoader.readBestCachedTrackShape(trackId, options);
}

export function fetchDetailTrackAtPrecision(
  trackId: number,
  precision: number,
  signal?: AbortSignal
): Promise<TrackPrecisionResult> {
  return trackCollectionLoader.fetchDetailTrackAtPrecision(trackId, precision, signal);
}

export function clearTrackCache(): Promise<void> {
  return trackCollectionLoader.clearCache();
}

export function isTrackCachePopulated(): Promise<boolean> {
  return trackCollectionLoader.isCachePopulated();
}
