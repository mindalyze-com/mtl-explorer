import type { GpsTrack } from 'x8ing-mtl-api-typescript-fetch';
import type { FilterResult } from '@/types/filter';
import type { FilterParamsRequest } from '@/components/filter/FilterService';

export interface CachedTrack {
  trackId: number;
  gpsTrack: GpsTrack;
  entityVersion: number;
  updatedAt: number;
}

export interface TrackGeometry {
  coordinates: number[][];
  // Indexes belong to these coordinates, never to metadata from another precision.
  recordingBreakBeforePointIndexes: number[];
}

export interface CachedGeometry extends TrackGeometry {
  cacheKey: string;
  trackId: number;
  precision: number;
  updatedAt: number;
}

export interface TrackGeometryEntry extends TrackGeometry {
  trackId: number;
  precision: number;
}

export interface TrackBatchPayload {
  tracksById: Map<number, GpsTrack>;
  geometryByTrackId: Map<number, number[][]>;
  filterGroups: Map<number, string>;
  standardFilterCount: number;
}

export interface ActiveTrackFilterRequest {
  filterName: string;
  filterParams: FilterParamsRequest | undefined;
}

export interface TrackFilterResultWithRequest extends FilterResult {
  activeFilterRequest?: ActiveTrackFilterRequest;
}

export interface TrackLoadResult {
  geojson: GeoJSON.FeatureCollection;
  gpsTracksById: Map<number, GpsTrack>;
  gpsTrackIdToFeature: Map<number, GeoJSON.Feature>;
  trackPrecisions: Map<number, number>;
  standardFilterCount: number;
  filterResult?: FilterResult;
  offline?: boolean;
}

export interface TrackPrecisionResult extends TrackGeometry {
  gpsTrack: GpsTrack;
  fromCache: boolean;
}

export interface TrackCollectionPageCallback {
  (page: TrackLoadResult): void | Promise<void>;
}

export interface TrackCollectionLoadOptions {
  signal?: AbortSignal;
  filterResult?: FilterResult;
  onPage?: TrackCollectionPageCallback;
  pageSize?: number;
  requestConcurrency?: number;
}

export interface TrackFilterApplyOptions {
  signal?: AbortSignal;
  filterResult?: FilterResult;
}
