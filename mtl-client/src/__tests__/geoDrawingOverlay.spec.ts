import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useMeasurementSystem } from '@/composables/useMeasurementSystem';

vi.mock('maplibre-gl', () => ({
  LngLat: class {
    constructor(
      public lng: number,
      public lat: number
    ) {}
  },
}));

import { GeoDrawingOverlay } from '@/layers/GeoDrawingOverlay';

type GeoJsonSource = {
  data: GeoJSON.FeatureCollection;
  setData: ReturnType<typeof vi.fn>;
};

function createMapMock() {
  const sources = new Map<string, GeoJsonSource>();
  return {
    sources,
    map: {
      addSource: vi.fn((id: string, source: { data: GeoJSON.FeatureCollection }) => {
        const stored: GeoJsonSource = {
          data: source.data,
          setData: vi.fn((data: GeoJSON.FeatureCollection) => {
            stored.data = data;
          }),
        };
        sources.set(id, stored);
      }),
      addLayer: vi.fn(),
      getSource: vi.fn((id: string) => sources.get(id)),
      getLayer: vi.fn(),
      removeLayer: vi.fn(),
      removeSource: vi.fn((id: string) => sources.delete(id)),
      getCanvas: vi.fn(() => document.createElement('canvas')),
      on: vi.fn(),
      off: vi.fn(),
    },
  };
}

function sourceLabel(source: GeoJsonSource | undefined): string | undefined {
  return source?.data.features[0]?.properties?.label as string | undefined;
}

describe('GeoDrawingOverlay measurement labels', () => {
  const measurementPreference = useMeasurementSystem();

  beforeEach(() => {
    measurementPreference.setMeasurementSystem('METRIC');
  });

  it('refreshes finalized shape labels when the measurement system changes', () => {
    const { map, sources } = createMapMock();
    const overlay = new GeoDrawingOverlay(map as never);
    overlay.renderCircle({ lat: 47.3, lng: 8.5, radiusM: 1609.344 }, undefined, 'Search radius');

    const labelSource = sources.get('geo-shape-0-label');
    expect(sourceLabel(labelSource)).toBe('Search radius\n1.6 km');

    measurementPreference.setMeasurementSystem('US_CUSTOMARY');
    overlay.refreshMeasurementLabels();

    expect(labelSource?.setData).toHaveBeenCalledTimes(1);
    expect(sourceLabel(labelSource)).toBe('Search radius\n1.0 mi');
  });
});
