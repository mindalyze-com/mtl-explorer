import type { GpsTrack } from 'x8ing-mtl-api-typescript-fetch';
import type { FilterResult } from '@/types/filter';
import { DEGENERATE_TRACK_LENGTH_THRESHOLD_METERS, OVERVIEW_PRECISION } from '@/utils/tracks/trackConstants';
import type { TrackGeometry, TrackLoadResult } from '@/utils/tracks/trackTypes';

// Point markers need every observation, including singleton spans omitted from
// line geometry. Keep these references out of the GeoJSON sent to MapLibre.
const recordedCoordinatesByGeometry = new WeakMap<GeoJSON.Geometry, number[][]>();

export function recordedTrackCoordinates(geometry: GeoJSON.Geometry): number[][] | undefined {
  return recordedCoordinatesByGeometry.get(geometry);
}

export function lineStringCoordinates(geometry: GeoJSON.Geometry | null | undefined): number[][] {
  if (geometry?.type === 'MultiLineString') return geometry.coordinates.flat();
  return geometry?.type === 'LineString' ? geometry.coordinates : [];
}

export function trackRecordingBreakIndexes(track: GpsTrack): number[] {
  const breakIndexes = new Set<number>();
  let offset = 0;
  for (const data of track.gpsTracksData ?? []) {
    if (offset > 0) breakIndexes.add(offset);
    for (const index of data.recordingBreakBeforePointIndexes ?? []) breakIndexes.add(offset + index);
    offset += data.numberOfPhysicalPoints ?? (Array.isArray(data.track) ? data.track.length : 0);
  }
  return [...breakIndexes];
}

export function buildTrackGeometry(
  track: GpsTrack,
  coordinates: number[][],
  {
    collapseShortTracks = true,
    recordingBreakBeforePointIndexes = trackRecordingBreakIndexes(track),
  }: { collapseShortTracks?: boolean; recordingBreakBeforePointIndexes?: number[] } = {}
): GeoJSON.Geometry {
  const isDegenerate =
    ((collapseShortTracks && (track.trackLengthInMeter ?? 0) < DEGENERATE_TRACK_LENGTH_THRESHOLD_METERS) ||
      coordinates.length < 2) &&
    track.centerLng != null &&
    track.centerLat != null;

  const breakIndexes = new Set(recordingBreakBeforePointIndexes);
  const spans: number[][][] = [];
  let span: number[][] = [];
  coordinates.forEach((coordinate, index) => {
    if (breakIndexes.has(index)) {
      if (span.length > 1) spans.push(span);
      span = [];
    }
    span.push(coordinate);
  });
  if (span.length > 1) spans.push(span);
  const geometry: GeoJSON.Geometry = isDegenerate
    ? { type: 'Point', coordinates: [track.centerLng!, track.centerLat!] }
    : breakIndexes.size > 0
      ? { type: 'MultiLineString', coordinates: spans }
      : { type: 'LineString', coordinates };
  recordedCoordinatesByGeometry.set(geometry, coordinates);
  return geometry;
}

export function buildTrackFeature(
  track: GpsTrack,
  coordinates: number[][],
  filterGroup?: string,
  trackId = track.id,
  recordingBreakBeforePointIndexes?: number[]
): GeoJSON.Feature {
  return {
    type: 'Feature',
    properties: {
      id: trackId,
      reviewMapExcluded: track.reviewMapExcluded ?? false,
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
    geometry: buildTrackGeometry(track, coordinates, { recordingBreakBeforePointIndexes }),
  };
}

export function buildTrackLoadResult(args: {
  trackIds: number[];
  tracksById: Map<number, GpsTrack>;
  geometryByTrackId: Map<number, TrackGeometry>;
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

    const geometry = args.geometryByTrackId.get(trackId);
    const filterGroup = args.filterResult?.filterGroups.get(trackId);
    const feature = buildTrackFeature(
      track,
      geometry?.coordinates ?? [],
      filterGroup,
      trackId,
      geometry?.recordingBreakBeforePointIndexes ?? []
    );
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
