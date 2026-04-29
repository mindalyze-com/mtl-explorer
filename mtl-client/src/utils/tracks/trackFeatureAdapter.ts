import type { GpsTrack } from 'x8ing-mtl-api-typescript-fetch';
import type { FilterResult } from '@/types/filter';
import { DEGENERATE_TRACK_LENGTH_THRESHOLD_METERS, OVERVIEW_PRECISION } from '@/utils/tracks/trackConstants';
import type { TrackLoadResult } from '@/utils/tracks/trackTypes';

export function lineStringCoordinates(geometry: GeoJSON.Geometry | null | undefined): number[][] {
  return geometry?.type === 'LineString' ? geometry.coordinates : [];
}

export function buildTrackFeature(
  track: GpsTrack,
  coordinates: number[][],
  filterGroup?: string,
  trackId = track.id
): GeoJSON.Feature {
  const isDegenerate =
    ((track.trackLengthInMeter ?? 0) < DEGENERATE_TRACK_LENGTH_THRESHOLD_METERS || coordinates.length === 0) &&
    track.centerLng != null &&
    track.centerLat != null;

  const geometry: GeoJSON.Geometry = isDegenerate
    ? { type: 'Point', coordinates: [track.centerLng!, track.centerLat!] }
    : { type: 'LineString', coordinates };

  return {
    type: 'Feature',
    properties: {
      id: trackId,
      fileName: track.indexedFile?.name,
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

export function buildTrackLoadResult(args: {
  trackIds: number[];
  tracksById: Map<number, GpsTrack>;
  geometryByTrackId: Map<number, number[][]>;
  defaultPrecision?: number;
  precisionByTrackId?: Map<number, number>;
  filterResult?: FilterResult;
  standardFilterCount: number;
  offline?: boolean;
}): TrackLoadResult {
  const gpsTracksById = new Map<number, GpsTrack>();
  const gpsTrackIdToFeature = new Map<number, GeoJSON.Feature>();
  const trackPrecisions = new Map<number, number>();
  const features: GeoJSON.Feature[] = [];
  const defaultPrecision = args.defaultPrecision ?? OVERVIEW_PRECISION;

  for (const trackId of args.trackIds) {
    const track = args.tracksById.get(trackId);
    if (!track) continue;

    const coordinates = args.geometryByTrackId.get(trackId) ?? [];
    const filterGroup = args.filterResult?.filterGroups.get(trackId);
    const feature = buildTrackFeature(track, coordinates, filterGroup, trackId);
    const precision = args.precisionByTrackId?.get(trackId) ?? defaultPrecision;

    gpsTracksById.set(trackId, track);
    gpsTrackIdToFeature.set(trackId, feature);
    trackPrecisions.set(trackId, precision);
    features.push(feature);
  }

  return {
    geojson: { type: 'FeatureCollection', features },
    gpsTracksById,
    gpsTrackIdToFeature,
    trackPrecisions,
    standardFilterCount: args.standardFilterCount,
    filterResult: args.filterResult,
    offline: args.offline,
  };
}
