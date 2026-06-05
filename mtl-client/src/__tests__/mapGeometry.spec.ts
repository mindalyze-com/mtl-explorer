import { describe, expect, it } from 'vitest';
import {
  buildTrackOverviewFeatures,
  collectionPrecisionForZoom,
  locationSearchTargetZoom,
  precisionForZoom,
  shortestLongitudeDelta,
  unwrapLngLatCoordinates,
} from '@/components/map/mapGeometry';
import { BACKGROUND_TRACK_PRECISION, DETAIL_TRACK_PRECISION, OVERVIEW_PRECISION } from '@/utils/tracks/trackConstants';

describe('map geometry helpers', () => {
  it('maps zoom levels to track precision tiers', () => {
    expect(precisionForZoom(5.9)).toBe(OVERVIEW_PRECISION);
    expect(precisionForZoom(6)).toBe(BACKGROUND_TRACK_PRECISION);
    expect(precisionForZoom(17)).toBe(DETAIL_TRACK_PRECISION);
  });

  it('keeps collection loads at background precision for high zoom', () => {
    expect(collectionPrecisionForZoom(17)).toBe(BACKGROUND_TRACK_PRECISION);
  });

  it('builds one overview point per drawable track feature', () => {
    const features = buildTrackOverviewFeatures({
      type: 'FeatureCollection',
      features: [
        {
          type: 'Feature',
          geometry: {
            type: 'LineString',
            coordinates: [
              [8, 47],
              [9, 48],
            ],
          },
          properties: { id: 1, filterGroup: 'A' },
        },
        {
          type: 'Feature',
          geometry: { type: 'Point', coordinates: [7, 46] },
          properties: { id: 2, filterGroup: 'B' },
        },
        {
          type: 'Feature',
          geometry: { type: 'LineString', coordinates: [] },
          properties: { id: 3 },
        },
      ],
    });

    expect(features).toHaveLength(2);
    expect(features[0].geometry.coordinates).toEqual([8, 47]);
    expect(features[0].properties).toEqual({ id: 1, filterGroup: 'A' });
    expect(features[1].geometry.coordinates).toEqual([7, 46]);
  });

  it('chooses stable location-search zoom targets', () => {
    expect(locationSearchTargetZoom({ kind: 'country' })).toBe(4.5);
    expect(locationSearchTargetZoom({ kindDetail: 'peak', sourceLayer: 'pois' })).toBe(14.5);
    expect(locationSearchTargetZoom({ minZoom: 8, maxZoom: 9 })).toBe(12);
    expect(locationSearchTargetZoom({ minZoom: 20 })).toBe(15);
    expect(locationSearchTargetZoom({})).toBe(12.5);
  });

  it('unwraps dateline-crossing coordinate sequences to the short path', () => {
    const unwrapped = unwrapLngLatCoordinates([
      [179.9, 0],
      [-179.9, 0],
      [-179.8, 0],
    ]);

    expect(unwrapped[0][0]).toBeCloseTo(179.9);
    expect(unwrapped[1][0]).toBeCloseTo(180.1);
    expect(unwrapped[2][0]).toBeCloseTo(180.2);
  });

  it('computes the shortest longitude delta across the dateline', () => {
    expect(shortestLongitudeDelta(179.9, -179.9)).toBeCloseTo(0.2);
    expect(shortestLongitudeDelta(-179.9, 179.9)).toBeCloseTo(-0.2);
  });
});
