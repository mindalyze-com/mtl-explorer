import { describe, expect, it, vi } from 'vitest';
import type { Map } from 'maplibre-gl';
import { HeatmapOverlay } from '@/layers/HeatmapOverlay';
import { buildTrackFeature } from '@/utils/tracks/trackFeatureAdapter';

function createOverlay() {
  const source = { setData: vi.fn() };
  const map = {
    getSource: vi.fn((): typeof source | undefined => undefined),
    getLayer: vi.fn(),
    addSource: vi.fn(),
    addLayer: vi.fn(),
  };
  return { overlay: new HeatmapOverlay(map as unknown as Map), map, source };
}

function pointCollection(coordinates: number[][]): GeoJSON.FeatureCollection<GeoJSON.Point> {
  return {
    type: 'FeatureCollection',
    features: coordinates.map((coordinate) => ({
      type: 'Feature',
      properties: {},
      geometry: { type: 'Point', coordinates: coordinate },
    })),
  };
}

describe('HeatmapOverlay', () => {
  it.each(['show', 'updateData'] as const)('includes every recorded span on %s', (operation) => {
    const { overlay, map, source } = createOverlay();
    // Fully synthetic coordinates: a recording break plus a separate GPX segment.
    const coordinates = [
      [8, 47],
      [8.001, 47],
      [8.002, 47],
      [8.003, 47],
      [9, 48],
      [9.001, 48],
      [10, 49],
      [10.001, 49],
    ];
    const feature = buildTrackFeature(
      {
        id: 42,
        trackLengthInMeter: 1000,
        gpsTracksData: [
          { numberOfPhysicalPoints: 6, recordingBreakBeforePointIndexes: [4] },
          { numberOfPhysicalPoints: 2 },
        ],
      },
      coordinates
    );
    expect(feature.geometry.type).toBe('MultiLineString');
    const data: GeoJSON.FeatureCollection = { type: 'FeatureCollection', features: [feature] };
    const expected = pointCollection([coordinates[0], coordinates[3], coordinates[4], coordinates[6]]);

    if (operation === 'updateData') {
      overlay.show({ type: 'FeatureCollection', features: [] });
      map.getSource.mockReturnValue(source);
      overlay.updateData(data);
      expect(source.setData).toHaveBeenCalledWith(expected);
    } else {
      overlay.show(data);
      expect(map.addSource).toHaveBeenCalledWith('heatmap-points', { type: 'geojson', data: expected });
    }
  });

  it('keeps continuous tracks and point activities in the density source', () => {
    const { overlay, map } = createOverlay();
    const coordinates = [
      [8, 47],
      [8.001, 47],
      [8.002, 47],
      [8.003, 47],
    ];
    overlay.show({
      type: 'FeatureCollection',
      features: [
        { type: 'Feature', properties: {}, geometry: { type: 'LineString', coordinates } },
        { type: 'Feature', properties: {}, geometry: { type: 'Point', coordinates: [9, 48] } },
      ],
    });

    expect(map.addSource).toHaveBeenCalledWith('heatmap-points', {
      type: 'geojson',
      data: pointCollection([coordinates[0], coordinates[3], [9, 48]]),
    });
  });
});
