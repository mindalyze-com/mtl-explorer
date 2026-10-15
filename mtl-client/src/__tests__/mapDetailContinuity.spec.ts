import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { GpsTrack } from 'x8ing-mtl-api-typescript-fetch';
import { useMapDataLoading } from '@/components/map/composables/useMapDataLoading';
import { useTrackPointLayer } from '@/components/map/composables/useTrackPointLayer';
import { buildTrackFeature } from '@/utils/tracks/trackFeatureAdapter';
import { BACKGROUND_TRACK_PRECISION, DETAIL_TRACK_PRECISION } from '@/utils/tracks/trackConstants';

const { fetchDetail } = vi.hoisted(() => ({ fetchDetail: vi.fn() }));
vi.mock('@/layers/GeoDrawingOverlay', () => ({ GeoDrawingOverlay: vi.fn() }));
vi.mock('@/utils/ServiceHelper', () => ({
  checkServerAuth: vi.fn(),
  fetchTrackCanonicalPoints: vi.fn(),
  fetchTrackPointsForRenderedShape: vi.fn(),
}));
vi.mock('@/utils/tracks/trackCollectionLoader', () => ({
  applyTrackFilter: vi.fn(),
  clearTrackCache: vi.fn(),
  fetchDetailTrackAtPrecision: fetchDetail,
  isTrackCachePopulated: vi.fn(),
  loadCachedTrackCollection: vi.fn(),
  loadTrackCollectionPaged: vi.fn(),
}));

async function upgrade(feature: GeoJSON.Feature, gpsTrack: GpsTrack, coordinates: number[][]) {
  fetchDetail.mockResolvedValue({ gpsTrack, coordinates, fromCache: false });
  const rendered: GeoJSON.Geometry[] = [];
  const setPointData = vi.fn();
  const pointMethods = useTrackPointLayer();
  const context = {
    geojson: { type: 'FeatureCollection', features: [feature] },
    trackPointsVisible: true,
    overlayMap: {
      getZoom: () => 18,
      getBounds: () => ({ getSouthWest: () => ({ lng: -180, lat: -90 }), getNorthEast: () => ({ lng: 180, lat: 90 }) }),
      getSource: () => ({ setData: setPointData }),
    },
    gpsTrackIdToFeature: new Map([[7, feature]]),
    gpsTracksById: new Map(),
    trackPrecisions: new Map([[7, BACKGROUND_TRACK_PRECISION]]),
    updateTracksSource: () => rendered.push(structuredClone(feature.geometry)),
    updateTrackPointsSource() {
      pointMethods.updateTrackPointsSource.call(context as never);
    },
    publishGpsTrackMetadataChanges: vi.fn(),
  };
  const methods = useMapDataLoading({ filterStore: {}, freshnessStore: {} });
  await methods.processDetailQueue.call(context as never, [7], DETAIL_TRACK_PRECISION, new AbortController().signal);
  expect(context.trackPrecisions.get(7)).toBe(DETAIL_TRACK_PRECISION);
  expect(context.gpsTrackIdToFeature.get(7)).toBe(feature);
  expect(rendered.length).toBeGreaterThan(0);
  const points = setPointData.mock.lastCall?.[0].features as GeoJSON.Feature<GeoJSON.Point>[];
  expect(points.map((point) => point.geometry.coordinates)).toEqual(coordinates.map((point) => point.slice(0, 2)));
  expect(points.map((point) => point.properties?.pointIndex)).toEqual(coordinates.map((_, index) => index));
  return rendered;
}

describe('main-map detail continuity', () => {
  beforeEach(() => vi.clearAllMocks());

  it('upgrades a short overview point without joining isolated recording spans', async () => {
    const coordinates = [
      [7.4471, 46.9478],
      [7.44745, 46.94805],
      [7.4477, 46.9482],
      [7.448, 46.94845],
      [7.44835, 46.94875],
      [7.4487, 46.9491],
    ];
    const track: GpsTrack = {
      id: 7,
      trackLengthInMeter: 38.5,
      centerLng: 7.447926,
      centerLat: 46.948419,
      gpsTracksData: [{ numberOfPhysicalPoints: 6, recordingBreakBeforePointIndexes: [2, 3, 4, 5] }],
    };
    const feature = buildTrackFeature(track, coordinates, 'WALKING');
    expect(feature.geometry.type).toBe('Point');
    const rendered = await upgrade(feature, track, coordinates);
    for (const geometry of rendered)
      expect(geometry).toEqual({
        type: 'MultiLineString',
        coordinates: [coordinates.slice(0, 2)],
      });
    expect(feature.properties?.filterGroup).toBe('WALKING');
  });

  it('rebuilds disconnected lines using the detail response break indexes', async () => {
    const coarse = [
      [8, 47],
      [8.01, 47],
      [8.2, 47],
      [8.21, 47],
    ];
    const track: GpsTrack = {
      id: 7,
      trackLengthInMeter: 150,
      gpsTracksData: [{ numberOfPhysicalPoints: 4, recordingBreakBeforePointIndexes: [2] }],
    };
    const feature = buildTrackFeature(track, coarse);
    const detailed = [coarse[0]!, [8.005, 47], coarse[1]!, coarse[2]!, [8.205, 47], coarse[3]!];
    const rendered = await upgrade(
      feature,
      {
        ...track,
        gpsTracksData: [{ numberOfPhysicalPoints: 6, recordingBreakBeforePointIndexes: [3] }],
      },
      detailed
    );
    for (const geometry of rendered)
      expect(geometry).toEqual({
        type: 'MultiLineString',
        coordinates: [detailed.slice(0, 3), detailed.slice(3)],
      });
  });

  it('still expands short continuous activities from a point into a detailed line', async () => {
    const coordinates = [
      [8, 47],
      [8.0001, 47],
    ];
    const track: GpsTrack = { id: 7, trackLengthInMeter: 10, centerLng: 8, centerLat: 47 };
    const feature = buildTrackFeature(track, coordinates);
    const rendered = await upgrade(feature, track, coordinates);
    for (const geometry of rendered) expect(geometry).toEqual({ type: 'LineString', coordinates });
  });

  it('retains a valid point when detail has only one observation', async () => {
    const coordinates = [[8, 47]];
    const track: GpsTrack = { id: 7, trackLengthInMeter: 0, centerLng: 8, centerLat: 47 };
    const feature = buildTrackFeature(track, coordinates);
    const rendered = await upgrade(feature, track, coordinates);
    for (const geometry of rendered) expect(geometry).toEqual({ type: 'Point', coordinates: [8, 47] });
  });
});
