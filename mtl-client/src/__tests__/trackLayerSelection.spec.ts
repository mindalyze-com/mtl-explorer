import { describe, expect, it, vi } from 'vitest';
import { useMapLayerSettings } from '@/components/map/composables/useMapLayerSettings';
import { useTrackLayers } from '@/components/map/composables/useTrackLayers';

function bindTrackLayerMethods(context: Record<string, unknown>) {
  const methods = useTrackLayers();
  for (const [name, method] of Object.entries(methods)) {
    context[name] = method.bind(context);
  }
  return context as Record<string, unknown> & typeof methods;
}

function bindMapLayerSettingsMethods(context: Record<string, unknown>) {
  const methods = useMapLayerSettings({
    filterStore: {},
    mapSettingsStore: {
      setLayerOpacity: vi.fn(),
    },
  });
  for (const [name, method] of Object.entries(methods)) {
    context[name] = method.bind(context);
  }
  return context as Record<string, unknown> & typeof methods;
}

function selectedTrackFeature(): GeoJSON.Feature<GeoJSON.LineString> {
  return {
    type: 'Feature',
    properties: { id: 42 },
    geometry: {
      type: 'LineString',
      coordinates: [
        [8, 47],
        [8.001, 47.001],
      ],
    },
  };
}

function pointTrackFeature(): GeoJSON.Feature<GeoJSON.Point> {
  return {
    type: 'Feature',
    properties: { id: 7 },
    geometry: {
      type: 'Point',
      coordinates: [8.002, 47.002],
    },
  };
}

const FLAT_LINE_LAYERS = ['tracks-layer', 'tracks-highlight-layer', 'tracks-highlight-dash-layer'];
const POINT_LAYERS = ['tracks-dot-layer', 'tracks-highlight-circle-layer', 'tracks-overview-dots'];
const ALL_TRACK_LAYERS = [...FLAT_LINE_LAYERS, ...POINT_LAYERS];
const TRACK_OPACITY_CALLS = [
  ['tracks-layer', 'line-opacity'],
  ['tracks-highlight-layer', 'line-opacity'],
  ['tracks-highlight-dash-layer', 'line-opacity'],
  ['tracks-dot-layer', 'circle-opacity'],
  ['tracks-dot-layer', 'circle-stroke-opacity'],
  ['tracks-overview-dots', 'circle-opacity'],
  ['tracks-overview-dots', 'circle-stroke-opacity'],
  ['tracks-highlight-circle-layer', 'circle-opacity'],
] as const;

describe('track layer selection', () => {
  it('updates the small selected-track source instead of changing highlight filters', () => {
    const feature = selectedTrackFeature();
    const selectedSource = { setData: vi.fn() };
    const overlayMap = {
      getSource: vi.fn((sourceId: string) => (sourceId === 'selected-track' ? selectedSource : null)),
      setFilter: vi.fn(),
    };
    const context = bindTrackLayerMethods({
      overlayMap,
      gpsTrackIdToFeature: new Map([[42, feature]]),
      selectedTrackId: null,
      selectedFeature: null,
      trackReplayActive: false,
    });

    context.selectTrack(42, feature);

    expect(selectedSource.setData).toHaveBeenLastCalledWith({
      type: 'FeatureCollection',
      features: [feature],
    });
    expect(overlayMap.setFilter).not.toHaveBeenCalled();

    context.deselectTrack();

    expect(selectedSource.setData).toHaveBeenLastCalledWith({
      type: 'FeatureCollection',
      features: [],
    });
    expect(overlayMap.setFilter).not.toHaveBeenCalled();
  });
});

describe('track source draping', () => {
  function buildContext(overrides: Record<string, unknown> = {}) {
    const lineFeature = selectedTrackFeature();
    const pointFeature = pointTrackFeature();
    const trackSource = { setData: vi.fn() };
    const overviewSource = { setData: vi.fn() };
    const selectedSource = { setData: vi.fn() };
    const overlayMap = {
      getLayer: vi.fn((layerId: string) => (ALL_TRACK_LAYERS.includes(layerId) ? true : null)),
      getSource: vi.fn((sourceId: string) => {
        if (sourceId === 'tracks') return trackSource;
        if (sourceId === 'tracks-overview') return overviewSource;
        if (sourceId === 'selected-track') return selectedSource;
        return null;
      }),
      addSource: vi.fn(),
      addLayer: vi.fn(),
      removeLayer: vi.fn(),
      getZoom: vi.fn(() => 10),
      setFilter: vi.fn(),
      setLayoutProperty: vi.fn(),
      setPaintProperty: vi.fn(),
    };
    const geojson: GeoJSON.FeatureCollection = {
      type: 'FeatureCollection',
      features: [lineFeature, pointFeature],
    };
    const context = bindTrackLayerMethods({
      overlayMap,
      geojson,
      gpsTrackIdToFeature: new Map([[42, lineFeature]]),
      selectedTrackId: 42,
      selectedFeature: lineFeature,
      trackReplayActive: false,
      hiddenGroups: new Set(),
      tracksEnabled: true,
      terrainEnabled: false,
      flatTrackSourceDirty: false,
      heatmapOverlay: null,
      layerOpacities: { tracks: 100 },
      applyLayerOpacity: vi.fn(),
      applyGroupFilter: vi.fn(),
      ...overrides,
    });
    return { context, overlayMap, trackSource, geojson, lineFeature, pointFeature };
  }

  it('feeds the full geojson into the flat tracks source in 2D', () => {
    const { context, trackSource, geojson } = buildContext({ terrainEnabled: false });

    context.updateTracksSource();

    expect(trackSource.setData).toHaveBeenLastCalledWith(geojson);
  });

  it('feeds the full geojson into the flat tracks source in 3D terrain mode', () => {
    const { context, trackSource, geojson, overlayMap } = buildContext({ terrainEnabled: true });

    context.updateTracksSource();

    // The same full archive is draped over terrain — no viewport reduction.
    expect(trackSource.setData).toHaveBeenLastCalledWith(geojson);
    for (const id of ALL_TRACK_LAYERS) {
      expect(overlayMap.setLayoutProperty).toHaveBeenCalledWith(id, 'visibility', 'visible');
    }
  });

  // Performance guard: MapLibre drapes the same simplified-geometry `tracks` source
  // over terrain. A worst-case benchmark (≈15k tracks / ≈400k vertices, whole archive
  // in view, terrain on, pitched, rotating + panning) held a locked 60 FPS. The cheap
  // cost depends on terrain mode reusing the SAME bounded source as 2D rather than
  // spinning up a separate, heavier (e.g. raw-geometry) terrain-only track path.
  it('reuses the same tracks source in terrain mode without a separate track path', () => {
    const { context, trackSource, geojson, overlayMap } = buildContext({ terrainEnabled: true });

    context.updateTracksSource();

    // Geometry is pushed only into the shared 'tracks' source...
    expect(overlayMap.getSource).toHaveBeenCalledWith('tracks');
    expect(trackSource.setData).toHaveBeenLastCalledWith(geojson);
    // ...and no terrain-specific track source or layer is created.
    expect(overlayMap.addSource).not.toHaveBeenCalled();
    expect(overlayMap.addLayer).not.toHaveBeenCalled();
  });

  it('feeds identical geometry to the tracks source in 2D and terrain mode', () => {
    const flat = buildContext({ terrainEnabled: false });
    flat.context.updateTracksSource();
    const terrain = buildContext({ terrainEnabled: true });
    terrain.context.updateTracksSource();

    expect(flat.trackSource.setData).toHaveBeenLastCalledWith(flat.geojson);
    expect(terrain.trackSource.setData).toHaveBeenLastCalledWith(terrain.geojson);
    expect(terrain.geojson.features).toEqual(flat.geojson.features);
  });

  it('hides all track layers when tracks are disabled, regardless of terrain', () => {
    const { context, overlayMap } = buildContext({ terrainEnabled: true, tracksEnabled: false });

    context.applyTracksVisibility();

    for (const id of ALL_TRACK_LAYERS) {
      expect(overlayMap.setLayoutProperty).toHaveBeenCalledWith(id, 'visibility', 'none');
    }
  });

  it('applies track opacity to every rendered track layer', () => {
    const overlayMap = {
      getLayer: vi.fn((layerId: string) => (ALL_TRACK_LAYERS.includes(layerId) ? { id: layerId } : null)),
      setPaintProperty: vi.fn(),
    };
    const context = bindMapLayerSettingsMethods({
      overlayMap,
      layerOpacities: { tracks: 42 },
      _applyHillshade: vi.fn(),
    });

    context.applyLayerOpacity('tracks');

    for (const [layerId, paintProperty] of TRACK_OPACITY_CALLS) {
      expect(overlayMap.setPaintProperty).toHaveBeenCalledWith(layerId, paintProperty, 0.42);
    }
  });

  it('reapplies saved track opacity after rebuilding track layers', async () => {
    const layers = new Set<string>();
    const sources = new Map<string, { setData: ReturnType<typeof vi.fn> }>();
    const overlayMap = {
      addImage: vi.fn(),
      addLayer: vi.fn((layer: { id: string }) => {
        layers.add(layer.id);
      }),
      addSource: vi.fn((sourceId: string) => {
        sources.set(sourceId, { setData: vi.fn() });
      }),
      getLayer: vi.fn((layerId: string) => (layers.has(layerId) ? { id: layerId } : null)),
      getSource: vi.fn((sourceId: string) => sources.get(sourceId) ?? null),
      getZoom: vi.fn(() => 10),
      hasImage: vi.fn(() => true),
      removeLayer: vi.fn((layerId: string) => {
        layers.delete(layerId);
      }),
      removeSource: vi.fn((sourceId: string) => {
        sources.delete(sourceId);
      }),
      setFilter: vi.fn(),
      setLayoutProperty: vi.fn(),
      setPaintProperty: vi.fn(),
    };
    const lineFeature = selectedTrackFeature();
    const pointFeature = pointTrackFeature();
    const context = bindTrackLayerMethods({
      overlayMap,
      geojson: {
        type: 'FeatureCollection',
        features: [lineFeature, pointFeature],
      },
      gpsTrackIdToFeature: new Map([[42, lineFeature]]),
      selectedTrackId: null,
      selectedFeature: null,
      trackReplayActive: false,
      hiddenGroups: new Set(),
      tracksEnabled: true,
      flatTrackSourceDirty: false,
      heatmapOverlay: null,
      heatmapVisible: false,
      layerOpacities: { tracks: 33 },
      legendEntries: [],
      mapThemeSelected: 'light-topo',
      attachTrackPointLayerHandlers: vi.fn(),
      detachTrackPointLayerHandlers: vi.fn(),
      scheduleDetailCheck: vi.fn(),
      _applyHillshade: vi.fn(),
    });
    bindMapLayerSettingsMethods(context);
    context.resolveTrackLineColor = vi.fn().mockResolvedValue('#3366ff');

    await context.addTracksToMap();

    for (const [layerId, paintProperty] of TRACK_OPACITY_CALLS) {
      expect(overlayMap.setPaintProperty).toHaveBeenCalledWith(layerId, paintProperty, 0.33);
    }
  });
});
