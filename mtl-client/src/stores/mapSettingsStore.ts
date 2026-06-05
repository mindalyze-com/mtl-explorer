import { defineStore } from 'pinia';
import { computed, ref } from 'vue';
import { readJsonStorage, STORAGE_KEYS, writeJsonStorage } from '@/utils/appStorage';
import { sanitizeTerrainExaggeration, TERRAIN_EXAGGERATION_DEFAULT } from '@/components/map/terrainMode';

const OVERLAY_LAYER_IDS = [
  'wanderland',
  'veloland',
  'mountainbikeland',
  'wanderwege',
  'wmt-hiking',
  'wmt-cycling',
  'wmt-mtb',
] as const;

export type MapOverlayLayerId = (typeof OVERLAY_LAYER_IDS)[number];
export type MapCoreLayerId = 'basemap' | 'terrain' | 'tracks' | 'media' | 'trackpoints' | 'heatmap';
export type MapLayerId = MapCoreLayerId | MapOverlayLayerId;
export type MapSourceMode = 'auto' | 'remote';

const OVERLAY_LAYER_ID_SET = new Set<string>(OVERLAY_LAYER_IDS);
const MAP_SOURCE_MODES = new Set<MapSourceMode>(['auto', 'remote']);
const REMOTE_RASTER_MAP_THEMES = new Set<string>(['light', 'light-topo', 'dark', 'grayscale']);

export const DEFAULT_MAP_THEME = 'light-topo';
export const DEFAULT_MAP_SOURCE_MODE: MapSourceMode = 'auto';

export const DEFAULT_LAYER_OPACITIES: Record<string, number> = {
  basemap: 100,
  terrain: 100,
  tracks: 100,
  media: 100,
  trackpoints: 100,
  heatmap: 100,
  wanderland: 100,
  veloland: 100,
  mountainbikeland: 100,
  wanderwege: 100,
  'wmt-hiking': 100,
  'wmt-cycling': 100,
  'wmt-mtb': 100,
};

export type MapLayerState = {
  enabled: boolean;
  opacity: number;
};

type PersistedMapSettings = {
  theme?: unknown;
  mapSourceMode?: unknown;
  legendCollapsed?: unknown;
  basemapEnabled?: unknown;
  terrainEnabled?: unknown;
  terrainExaggeration?: unknown;
  tracksEnabled?: unknown;
  trackPointsVisible?: unknown;
  heatmapVisible?: unknown;
  activeOverlays?: unknown;
  layerOpacities?: unknown;
};

export const useMapSettingsStore = defineStore('mapSettings', () => {
  const hydrated = ref(false);
  const theme = ref(DEFAULT_MAP_THEME);
  const mapSourceMode = ref<MapSourceMode>(DEFAULT_MAP_SOURCE_MODE);
  const legendCollapsed = ref(false);
  const basemapEnabled = ref(true);
  const terrainEnabled = ref(false);
  const terrainExaggeration = ref(TERRAIN_EXAGGERATION_DEFAULT);
  const tracksEnabled = ref(true);
  const mediaVisible = ref(false);
  const trackPointsVisible = ref(true);
  const heatmapVisible = ref(false);
  const activeOverlays = ref<string[]>([]);
  const layerOpacities = ref<Record<string, number>>({ ...DEFAULT_LAYER_OPACITIES });

  const layerStates = computed<Record<string, MapLayerState>>(() => ({
    basemap: { enabled: basemapEnabled.value, opacity: layerOpacities.value.basemap },
    terrain: { enabled: terrainEnabled.value, opacity: layerOpacities.value.terrain },
    tracks: { enabled: tracksEnabled.value, opacity: layerOpacities.value.tracks },
    media: { enabled: mediaVisible.value, opacity: layerOpacities.value.media },
    trackpoints: { enabled: trackPointsVisible.value, opacity: layerOpacities.value.trackpoints },
    heatmap: { enabled: heatmapVisible.value, opacity: layerOpacities.value.heatmap },
    ...Object.fromEntries(
      OVERLAY_LAYER_IDS.map((id) => [
        id,
        { enabled: activeOverlays.value.includes(id), opacity: layerOpacities.value[id] },
      ])
    ),
  }));

  function hydrate(force = false): void {
    if (hydrated.value && !force) return;

    const stored = readJsonStorage<PersistedMapSettings>(STORAGE_KEYS.mapSettings, {}, (value) =>
      isRecord(value) ? value : {}
    );
    theme.value = sanitizeTheme(stored.theme);
    mapSourceMode.value = sanitizeMapSourceMode(stored.mapSourceMode);
    theme.value = sanitizeThemeForSourceMode(mapSourceMode.value, theme.value);
    legendCollapsed.value = sanitizeBoolean(stored.legendCollapsed, false);
    activeOverlays.value = sanitizeOverlayIds(stored.activeOverlays);
    layerOpacities.value = sanitizeLayerOpacities(stored.layerOpacities);
    basemapEnabled.value = sanitizeBoolean(stored.basemapEnabled, true);
    terrainEnabled.value = sanitizeBoolean(stored.terrainEnabled, false);
    terrainExaggeration.value = sanitizeTerrainExaggeration(stored.terrainExaggeration);
    tracksEnabled.value = sanitizeBoolean(stored.tracksEnabled, true);
    trackPointsVisible.value = sanitizeBoolean(stored.trackPointsVisible, true);
    heatmapVisible.value = sanitizeBoolean(stored.heatmapVisible, false);
    mediaVisible.value = false;

    hydrated.value = true;
  }

  function setTheme(nextTheme: string): void {
    const sanitizedTheme = sanitizeTheme(nextTheme);
    theme.value = sanitizeThemeForSourceMode(mapSourceMode.value, sanitizedTheme);
    persistPreferences();
  }

  function setMapSourceMode(nextMode: unknown): void {
    mapSourceMode.value = sanitizeMapSourceMode(nextMode);
    theme.value = sanitizeThemeForSourceMode(mapSourceMode.value, theme.value);
    persistPreferences();
  }

  function setLayerEnabled(layerId: MapLayerId, enabled: boolean): void {
    if (isOverlayLayerId(layerId)) {
      activeOverlays.value = enabled
        ? Array.from(new Set([...activeOverlays.value, layerId]))
        : activeOverlays.value.filter((id) => id !== layerId);
      persistPreferences();
      return;
    }

    switch (layerId) {
      case 'basemap':
        basemapEnabled.value = enabled;
        persistPreferences();
        break;
      case 'terrain':
        setTerrainEnabled(enabled);
        break;
      case 'tracks':
        tracksEnabled.value = enabled;
        persistPreferences();
        break;
      case 'media':
        mediaVisible.value = enabled;
        break;
      case 'trackpoints':
        trackPointsVisible.value = enabled;
        persistPreferences();
        break;
      case 'heatmap':
        heatmapVisible.value = enabled;
        persistPreferences();
        break;
    }
  }

  function toggleLayer(layerId: MapLayerId): boolean {
    const enabled = !isLayerEnabled(layerId);
    setLayerEnabled(layerId, enabled);
    return enabled;
  }

  function setTerrainEnabled(enabled: boolean): void {
    terrainEnabled.value = enabled;
    persistPreferences();
  }

  function setTerrainExaggeration(exaggeration: number): void {
    terrainExaggeration.value = sanitizeTerrainExaggeration(exaggeration);
    persistPreferences();
  }

  function toggleTerrainEnabled(): boolean {
    const enabled = !terrainEnabled.value;
    setTerrainEnabled(enabled);
    return enabled;
  }

  function setLayerOpacity(layerId: string, opacity: number): void {
    if (!isKnownLayerId(layerId)) return;
    layerOpacities.value = {
      ...layerOpacities.value,
      [layerId]: clampOpacity(opacity),
    };
    persistPreferences();
  }

  function setLegendCollapsed(collapsed: boolean): void {
    legendCollapsed.value = collapsed;
    persistPreferences();
  }

  function reset(): void {
    theme.value = DEFAULT_MAP_THEME;
    mapSourceMode.value = DEFAULT_MAP_SOURCE_MODE;
    legendCollapsed.value = false;
    basemapEnabled.value = true;
    terrainEnabled.value = false;
    terrainExaggeration.value = TERRAIN_EXAGGERATION_DEFAULT;
    tracksEnabled.value = true;
    mediaVisible.value = false;
    trackPointsVisible.value = true;
    heatmapVisible.value = false;
    activeOverlays.value = [];
    layerOpacities.value = { ...DEFAULT_LAYER_OPACITIES };

    persistPreferences();
  }

  function isLayerEnabled(layerId: MapLayerId): boolean {
    if (isOverlayLayerId(layerId)) return activeOverlays.value.includes(layerId);
    if (layerId === 'basemap') return basemapEnabled.value;
    if (layerId === 'terrain') return terrainEnabled.value;
    if (layerId === 'tracks') return tracksEnabled.value;
    if (layerId === 'media') return mediaVisible.value;
    if (layerId === 'trackpoints') return trackPointsVisible.value;
    return heatmapVisible.value;
  }

  function persistPreferences(): void {
    writeJsonStorage(STORAGE_KEYS.mapSettings, {
      theme: theme.value,
      mapSourceMode: mapSourceMode.value,
      legendCollapsed: legendCollapsed.value,
      basemapEnabled: basemapEnabled.value,
      terrainEnabled: terrainEnabled.value,
      terrainExaggeration: terrainExaggeration.value,
      tracksEnabled: tracksEnabled.value,
      trackPointsVisible: trackPointsVisible.value,
      heatmapVisible: heatmapVisible.value,
      activeOverlays: activeOverlays.value,
      layerOpacities: layerOpacities.value,
    });
  }

  hydrate();

  return {
    hydrated,
    theme,
    mapSourceMode,
    legendCollapsed,
    basemapEnabled,
    terrainEnabled,
    terrainExaggeration,
    tracksEnabled,
    mediaVisible,
    trackPointsVisible,
    heatmapVisible,
    activeOverlays,
    layerOpacities,
    layerStates,
    hydrate,
    setTheme,
    setMapSourceMode,
    setLayerEnabled,
    toggleLayer,
    setTerrainEnabled,
    setTerrainExaggeration,
    toggleTerrainEnabled,
    setLayerOpacity,
    setLegendCollapsed,
    reset,
  };
});

function isOverlayLayerId(layerId: string): layerId is MapOverlayLayerId {
  return OVERLAY_LAYER_ID_SET.has(layerId);
}

function sanitizeOverlayIds(overlays: unknown): string[] {
  return Array.from(new Set((Array.isArray(overlays) ? overlays : []).filter((id) => isOverlayLayerId(id))));
}

function sanitizeLayerOpacities(opacities: unknown): Record<string, number> {
  const nextOpacities = { ...DEFAULT_LAYER_OPACITIES };
  if (!isRecord(opacities)) return nextOpacities;

  for (const [key, value] of Object.entries(opacities)) {
    if (isKnownLayerId(key)) {
      nextOpacities[key] = clampOpacity(value);
    }
  }
  return nextOpacities;
}

function sanitizeTheme(value: unknown): string {
  return typeof value === 'string' && value.trim() ? value : DEFAULT_MAP_THEME;
}

function sanitizeMapSourceMode(value: unknown): MapSourceMode {
  return typeof value === 'string' && MAP_SOURCE_MODES.has(value as MapSourceMode)
    ? (value as MapSourceMode)
    : DEFAULT_MAP_SOURCE_MODE;
}

function isRemoteRasterMapTheme(value: string): boolean {
  return REMOTE_RASTER_MAP_THEMES.has(value);
}

function sanitizeThemeForSourceMode(sourceMode: MapSourceMode, value: string): string {
  return sourceMode === 'remote' && !isRemoteRasterMapTheme(value) ? DEFAULT_MAP_THEME : value;
}

function sanitizeBoolean(value: unknown, fallback: boolean): boolean {
  return typeof value === 'boolean' ? value : fallback;
}

function isKnownLayerId(layerId: string): boolean {
  return Object.prototype.hasOwnProperty.call(DEFAULT_LAYER_OPACITIES, layerId);
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value != null && typeof value === 'object' && !Array.isArray(value);
}

function clampOpacity(value: unknown): number {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) return 100;
  return Math.max(0, Math.min(100, Math.round(numeric)));
}
