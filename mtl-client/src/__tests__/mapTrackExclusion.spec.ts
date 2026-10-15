import { describe, expect, it, vi } from 'vitest';
import { buildTrackFeature } from '@/utils/tracks/trackFeatureAdapter';
import { useTrackLayers, visibleMapFeatures } from '@/components/map/composables/useTrackLayers';
import { buildTrackOverviewFeatures } from '@/components/map/mapGeometry';
import { curationBadges, curationSearchValues } from '@/utils/statisticsCuration';

const normal = buildTrackFeature({ id: 1, trackLengthInMeter: 100, centerLng: 24, centerLat: 60 }, [
  [24, 60],
  [24.001, 60.001],
]);
const hidden = buildTrackFeature(
  { id: 2, trackLengthInMeter: 100, reviewMapExcluded: true, centerLng: -30, centerLat: -40 },
  [
    [-30, -40],
    [-30.1, -40.1],
  ]
);
const statsOnly = buildTrackFeature(
  { id: 3, trackLengthInMeter: 100, reviewExcluded: true, centerLng: 24, centerLat: 60 },
  [
    [24, 60],
    [24.002, 60.002],
  ]
);
const archive: GeoJSON.FeatureCollection = { type: 'FeatureCollection', features: [normal, hidden, statsOnly] };

describe('map exclusion', () => {
  it('omits hidden tracks from map geometry and overview dots while keeping statistics-only exclusions', () => {
    const visible = visibleMapFeatures(archive);
    expect(visible.features.map((f) => f.properties?.id)).toEqual([1, 3]);
    expect(buildTrackOverviewFeatures(visible).map((f) => f.properties?.id)).toEqual([1, 3]);
    expect(archive.features).toHaveLength(3);
  });
  it('does not restore a hidden line through selection', () => {
    const methods = useTrackLayers();
    const context = {
      selectedTrackId: 2,
      selectedFeature: hidden,
      gpsTrackIdToFeature: new Map([[2, hidden]]),
      trackReplayActive: false,
    };
    expect(methods.selectedTrackGeojson.call(context).features).toHaveLength(0);
    const fitContext = {
      gpsTrackIdToFeature: new Map([[2, hidden]]),
      selectTrackById: vi.fn(),
      endMapInteraction: vi.fn(),
      overlayMap: { fitBounds: vi.fn() },
    };
    methods.onTrackBrowserSelect.call(fitContext, 2);
    expect(fitContext.overlayMap.fitBounds).not.toHaveBeenCalled();
  });
  it('identifies hidden tracks in the browser without claiming their statistics are excluded', () => {
    expect(curationBadges({ reviewMapExcluded: true }).map((b) => b.key)).toEqual(['map']);
    expect(curationSearchValues({ reviewMapExcluded: true })).toContain('excluded map');
    expect(curationSearchValues({ reviewMapExcluded: true })).not.toContain('excluded statistics');
  });
});
