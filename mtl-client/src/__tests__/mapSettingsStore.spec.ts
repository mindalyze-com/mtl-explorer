import { beforeEach, describe, expect, it } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import {
  DEFAULT_LAYER_OPACITIES,
  DEFAULT_MEDIA_VISIBLE,
  DEFAULT_MAP_SOURCE_MODE,
  DEFAULT_MAP_THEME,
  DEFAULT_REMOTE_RASTER_MAP_THEME,
  useMapSettingsStore,
} from '@/stores/mapSettingsStore';
import { STORAGE_KEYS } from '@/utils/appStorage';
import { TERRAIN_EXAGGERATION_DEFAULT } from '@/components/map/terrainMode';

describe('useMapSettingsStore', () => {
  beforeEach(() => {
    localStorage.clear();
    setActivePinia(createPinia());
  });

  it('hydrates defaults when no preferences are stored', () => {
    const store = useMapSettingsStore();

    expect(store.theme).toBe(DEFAULT_MAP_THEME);
    expect(store.mapSourceMode).toBe(DEFAULT_MAP_SOURCE_MODE);
    expect(store.basemapEnabled).toBe(true);
    expect(store.terrainEnabled).toBe(false);
    expect(store.terrainExaggeration).toBe(TERRAIN_EXAGGERATION_DEFAULT);
    expect(store.tracksEnabled).toBe(true);
    expect(store.mediaVisible).toBe(DEFAULT_MEDIA_VISIBLE);
    expect(store.trackPointsVisible).toBe(true);
    expect(store.heatmapVisible).toBe(false);
    expect(store.legendCollapsed).toBe(false);
    expect(store.activeOverlays).toEqual([]);
    expect(store.layerOpacities).toEqual(DEFAULT_LAYER_OPACITIES);
  });

  it('enables media for legacy preferences without a saved media choice', () => {
    localStorage.setItem(STORAGE_KEYS.mapSettings, JSON.stringify({ tracksEnabled: false }));

    const store = useMapSettingsStore();

    expect(store.tracksEnabled).toBe(false);
    expect(store.mediaVisible).toBe(DEFAULT_MEDIA_VISIBLE);
  });

  it('hydrates stored layer state and sanitizes unknown overlays/opacities', () => {
    localStorage.setItem(
      STORAGE_KEYS.mapSettings,
      JSON.stringify({
        theme: 'dark',
        mapSourceMode: 'remote',
        legendCollapsed: true,
        activeOverlays: ['wanderland', 'unknown'],
        layerOpacities: { basemap: 45.6, tracks: 200, unknown: 50 },
        basemapEnabled: false,
        terrainEnabled: true,
        terrainExaggeration: 1.34,
        mediaVisible: false,
        trackPointsVisible: false,
        heatmapVisible: true,
      })
    );

    const store = useMapSettingsStore();

    expect(store.theme).toBe('dark');
    expect(store.mapSourceMode).toBe('remote');
    expect(store.legendCollapsed).toBe(true);
    expect(store.basemapEnabled).toBe(false);
    expect(store.terrainEnabled).toBe(true);
    expect(store.terrainExaggeration).toBe(1.3);
    expect(store.mediaVisible).toBe(false);
    expect(store.trackPointsVisible).toBe(false);
    expect(store.heatmapVisible).toBe(true);
    expect(store.activeOverlays).toEqual(['wanderland']);
    expect(store.layerOpacities.basemap).toBe(46);
    expect(store.layerOpacities.tracks).toBe(100);
  });

  it('persists layer toggles including media visibility', () => {
    const store = useMapSettingsStore();

    expect(store.toggleLayer('basemap')).toBe(false);
    store.setMapSourceMode('remote');
    expect(store.toggleTerrainEnabled()).toBe(true);
    store.setTerrainExaggeration(1.6);
    store.setLayerEnabled('wanderland', true);
    store.setLayerEnabled('media', false);
    store.setLegendCollapsed(true);

    expect(store.basemapEnabled).toBe(false);
    expect(store.terrainEnabled).toBe(true);
    expect(store.terrainExaggeration).toBe(1.6);
    expect(store.activeOverlays).toEqual(['wanderland']);
    expect(store.mediaVisible).toBe(false);
    expect(readStoredMapSettings()).toMatchObject({
      basemapEnabled: false,
      mapSourceMode: 'remote',
      terrainEnabled: true,
      terrainExaggeration: 1.6,
      activeOverlays: ['wanderland'],
      mediaVisible: false,
      legendCollapsed: true,
    });
  });

  it('keeps the terrain mode helpers equivalent to the generic layer API', () => {
    const store = useMapSettingsStore();

    store.setTerrainEnabled(true);
    expect(store.terrainEnabled).toBe(true);
    expect(store.layerStates.terrain.enabled).toBe(true);

    store.setLayerEnabled('terrain', false);
    expect(store.terrainEnabled).toBe(false);
    expect(store.layerStates.terrain.enabled).toBe(false);

    expect(store.toggleLayer('terrain')).toBe(true);
    expect(store.terrainEnabled).toBe(true);
    expect(store.layerStates.terrain.enabled).toBe(true);
  });

  it('persists opacity changes and reset restores defaults', () => {
    const store = useMapSettingsStore();

    store.setTheme('dark');
    store.setMapSourceMode('remote');
    store.setLayerOpacity('tracks', 33);
    store.setLayerEnabled('terrain', true);
    store.setTerrainExaggeration(1.8);
    store.setLayerEnabled('media', false);
    store.setLayerEnabled('heatmap', true);
    store.setLayerEnabled('wmt-hiking', true);
    store.reset();

    expect(store.theme).toBe(DEFAULT_MAP_THEME);
    expect(store.mapSourceMode).toBe(DEFAULT_MAP_SOURCE_MODE);
    expect(store.layerOpacities).toEqual(DEFAULT_LAYER_OPACITIES);
    expect(store.terrainEnabled).toBe(false);
    expect(store.terrainExaggeration).toBe(TERRAIN_EXAGGERATION_DEFAULT);
    expect(store.mediaVisible).toBe(DEFAULT_MEDIA_VISIBLE);
    expect(store.heatmapVisible).toBe(false);
    expect(store.activeOverlays).toEqual([]);
    expect(readStoredMapSettings()).toMatchObject({
      theme: DEFAULT_MAP_THEME,
      mapSourceMode: DEFAULT_MAP_SOURCE_MODE,
      layerOpacities: DEFAULT_LAYER_OPACITIES,
      terrainEnabled: false,
      terrainExaggeration: TERRAIN_EXAGGERATION_DEFAULT,
      mediaVisible: DEFAULT_MEDIA_VISIBLE,
      heatmapVisible: false,
      activeOverlays: [],
    });
  });

  it('sanitizes unknown map source modes back to automatic', () => {
    localStorage.setItem(
      STORAGE_KEYS.mapSettings,
      JSON.stringify({
        mapSourceMode: 'local',
      })
    );

    const store = useMapSettingsStore();

    expect(store.mapSourceMode).toBe(DEFAULT_MAP_SOURCE_MODE);
  });

  it('falls back to the default map theme for old or unknown preferences', () => {
    localStorage.setItem(
      STORAGE_KEYS.mapSettings,
      JSON.stringify({
        theme: 'color-topo',
      })
    );

    const store = useMapSettingsStore();

    expect(store.theme).toBe(DEFAULT_MAP_THEME);
  });

  it('normalizes local-vector themes when remote raster source mode is active', () => {
    localStorage.setItem(
      STORAGE_KEYS.mapSettings,
      JSON.stringify({
        theme: DEFAULT_MAP_THEME,
        mapSourceMode: 'remote',
      })
    );

    const store = useMapSettingsStore();

    expect(store.mapSourceMode).toBe('remote');
    expect(store.theme).toBe(DEFAULT_REMOTE_RASTER_MAP_THEME);

    store.setTheme('swisstopo');
    expect(store.theme).toBe(DEFAULT_REMOTE_RASTER_MAP_THEME);

    store.setTheme('swisstopo-color');
    expect(store.theme).toBe(DEFAULT_REMOTE_RASTER_MAP_THEME);

    store.setTheme(DEFAULT_MAP_THEME);
    expect(store.theme).toBe(DEFAULT_REMOTE_RASTER_MAP_THEME);
  });

  it('restores the automatic OSM Topo Contrast default when leaving remote mode', () => {
    const store = useMapSettingsStore();

    store.setMapSourceMode('remote');
    expect(store.theme).toBe(DEFAULT_REMOTE_RASTER_MAP_THEME);

    store.setTheme('dark');
    expect(store.theme).toBe('dark');

    store.setMapSourceMode('auto');
    expect(store.mapSourceMode).toBe(DEFAULT_MAP_SOURCE_MODE);
    expect(store.theme).toBe(DEFAULT_MAP_THEME);
    expect(readStoredMapSettings()).toMatchObject({
      theme: DEFAULT_MAP_THEME,
      automaticTheme: DEFAULT_MAP_THEME,
      mapSourceMode: DEFAULT_MAP_SOURCE_MODE,
    });
  });

  it('preserves an explicitly selected automatic map theme across remote mode', () => {
    const store = useMapSettingsStore();

    store.setTheme('swisstopo-color');
    store.setMapSourceMode('remote');

    expect(store.theme).toBe(DEFAULT_REMOTE_RASTER_MAP_THEME);
    expect(readStoredMapSettings()).toMatchObject({
      automaticTheme: 'swisstopo-color',
      mapSourceMode: 'remote',
    });

    store.setTheme('dark');
    store.setMapSourceMode('auto');

    expect(store.theme).toBe('swisstopo-color');
  });

  it('migrates the legacy automatic remote topo fallback back to the current default', () => {
    localStorage.setItem(
      STORAGE_KEYS.mapSettings,
      JSON.stringify({
        theme: DEFAULT_REMOTE_RASTER_MAP_THEME,
        mapSourceMode: 'auto',
      })
    );

    const store = useMapSettingsStore();

    expect(store.theme).toBe(DEFAULT_MAP_THEME);
  });
});

function readStoredMapSettings(): Record<string, unknown> {
  return JSON.parse(localStorage.getItem(STORAGE_KEYS.mapSettings) ?? '{}') as Record<string, unknown>;
}
