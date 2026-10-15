import { describe, expect, it, vi } from 'vitest';
import { useTrackLayers } from '@/components/map/composables/useTrackLayers';
import type { MapControllerRuntime, TrackFeature } from '@/components/map/composables/mapControllerRuntime';
import type { MapInteractionState } from '@/components/map/mapInteractionCoordinator';

function feature(id: number): TrackFeature {
  return {
    type: 'Feature',
    properties: { id, filterGroup: 'visible' },
    geometry: {
      type: 'LineString',
      coordinates: [
        [8, 47],
        [8.1, 47.1],
      ],
    },
  };
}

function fixture() {
  const selected = feature(42);
  const hovered = feature(7);
  const source = { setData: vi.fn() };
  const map = {
    getSource: vi.fn((id: string) => (id === 'selected-track' ? source : null)),
    getLayer: vi.fn(() => null),
    getZoom: vi.fn(() => 10),
    fitBounds: vi.fn(),
    setFilter: vi.fn(),
    setLayoutProperty: vi.fn(),
  };
  const mapInteraction = {
    id: 1,
    presentation: 'chooser',
    candidates: [42, 7].map((trackId) => ({
      kind: 'track',
      source: 'tracks',
      id: String(trackId),
      label: `Track ${trackId}`,
      trackId,
    })),
  } as MapInteractionState;
  const runtime = Object.assign(
    {
      overlayMap: map,
      mapInteraction,
      geojson: { type: 'FeatureCollection', features: [selected, hovered] },
      gpsTrackIdToFeature: new Map([
        [42, selected],
        [7, hovered],
      ]),
      gpsTracksById: new Map(),
      selectedTrackId: 42,
      selectedFeature: selected,
      trackReplayActive: false,
      tracksEnabled: true,
      layerOpacities: { tracks: 100 },
      hiddenGroups: new Set(),
      applyLayerOpacity: vi.fn(),
      applyGroupFilter: vi.fn(),
      openTrackDetails: vi.fn(),
    },
    useTrackLayers()
  ) as unknown as MapControllerRuntime;
  return { runtime, map, source, selected, hovered };
}

describe('map GPS track preview highlighting', () => {
  it('reuses the existing highlight source and preserves the selected track without fit or detail actions', () => {
    const { runtime, map, source, selected, hovered } = fixture();
    runtime.previewTrackById(7);
    expect(source.setData).toHaveBeenLastCalledWith({ type: 'FeatureCollection', features: [selected, hovered] });
    expect(runtime.selectedTrackId).toBe(42);
    expect(runtime.selectedFeature).toBe(selected);
    expect(map.fitBounds).not.toHaveBeenCalled();
    expect(runtime.openTrackDetails).not.toHaveBeenCalled();
    expect(map.setFilter).not.toHaveBeenCalled();

    runtime.previewTrackById(null);
    expect(source.setData).toHaveBeenLastCalledWith({ type: 'FeatureCollection', features: [selected] });
    expect(runtime.selectedTrackId).toBe(42);
  });

  it('deduplicates a preview of the selected track and can preview without any selected track', () => {
    const { runtime, source, selected, hovered } = fixture();
    runtime.previewTrackById(42);
    expect(source.setData).toHaveBeenLastCalledWith({ type: 'FeatureCollection', features: [selected] });
    runtime.selectedTrackId = null;
    runtime.selectedFeature = null;
    runtime.previewTrackById(7);
    expect(source.setData).toHaveBeenLastCalledWith({ type: 'FeatureCollection', features: [hovered] });
    runtime.previewTrackById(null);
    expect(source.setData).toHaveBeenLastCalledWith({ type: 'FeatureCollection', features: [] });
  });

  it.each(['excluded', 'filtered group', 'removed', 'hidden chooser'])(
    'omits a %s preview and retains the legitimate selection',
    (reason) => {
      const { runtime, source, selected, hovered } = fixture();
      if (reason === 'excluded') hovered.properties.reviewMapExcluded = true;
      if (reason === 'filtered group') runtime.hiddenGroups.add('visible');
      if (reason === 'removed') runtime.gpsTrackIdToFeature.delete(7);
      runtime.previewTrackById(7);
      if (reason === 'hidden chooser') {
        runtime.mapInteraction!.presentation = 'target';
        runtime.updateSelectedTrackSource();
      }
      expect(source.setData).toHaveBeenLastCalledWith({ type: 'FeatureCollection', features: [selected] });
      expect(runtime.selectedTrackId).toBe(42);
    }
  );

  it('reapplies an active eligible preview after routine geometry precision refresh', () => {
    const { runtime, source, selected, hovered } = fixture();
    runtime.previewTrackById(7);
    const refreshed: TrackFeature = {
      ...hovered,
      geometry: {
        type: 'LineString',
        coordinates: [
          [8, 47],
          [8.05, 47.08],
          [8.1, 47.1],
        ],
      },
    };
    runtime.gpsTrackIdToFeature.set(7, refreshed);
    runtime.geojson = { type: 'FeatureCollection', features: [selected, refreshed] };
    runtime.updateTracksSource();
    expect(source.setData).toHaveBeenLastCalledWith({ type: 'FeatureCollection', features: [selected, refreshed] });
    expect(runtime.selectedTrackId).toBe(42);
  });

  it.each(['removed', 'excluded', 'hidden group', 'disabled tracks', 'transparent tracks', 'hidden chooser'])(
    'suppresses a %s preview during geometry refresh and preserves the legitimate selection',
    (reason) => {
      const { runtime, source, selected, hovered } = fixture();
      runtime.previewTrackById(7);
      if (reason === 'removed') runtime.gpsTrackIdToFeature.delete(7);
      if (reason === 'excluded') hovered.properties.reviewMapExcluded = true;
      if (reason === 'hidden group') {
        hovered.properties.filterGroup = 'hidden';
        runtime.hiddenGroups.add('hidden');
      }
      if (reason === 'disabled tracks') runtime.tracksEnabled = false;
      if (reason === 'transparent tracks') runtime.layerOpacities.tracks = 0;
      if (reason === 'hidden chooser') runtime.mapInteraction!.presentation = 'target';
      runtime.updateTracksSource();
      expect(source.setData).toHaveBeenLastCalledWith({ type: 'FeatureCollection', features: [selected] });
      expect(runtime.selectedTrackId).toBe(42);
    }
  );

  it('clears the temporary preview when selection or deselection is explicit', () => {
    const { runtime, source, selected } = fixture();
    runtime.previewTrackById(7);
    runtime.selectTrack(42, selected);
    expect(source.setData).toHaveBeenLastCalledWith({ type: 'FeatureCollection', features: [] });
    runtime.previewTrackById(7);
    runtime.deselectTrack();
    expect(source.setData).toHaveBeenLastCalledWith({ type: 'FeatureCollection', features: [] });
  });
});
