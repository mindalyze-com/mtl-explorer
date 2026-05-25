import { USER_PREFS_KEYS, migrateLegacyKeys, readPref, removePref, writePref, type UserPrefKey } from '@/utils/userPrefs';

export const DEFAULT_MAP_THEME = 'light-topo';
const LEGACY_WAS_AUTO_DIMMED_KEY = 'mtl-wasAutoDimmed';
const LEGACY_AUTO_DIM_BASEMAP_OPACITY = 60;

export const DEFAULT_LAYER_OPACITIES: Record<string, number> = {
  basemap: 100,
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

export function useMapPreferences() {
  migrateLegacyKeys();

  function readTheme(): string {
    return readPref(USER_PREFS_KEYS.mapTheme) ?? DEFAULT_MAP_THEME;
  }

  function writeTheme(theme: string): void {
    writePref(USER_PREFS_KEYS.mapTheme, theme);
  }

  function readLegendCollapsed(): boolean {
    return readPref(USER_PREFS_KEYS.mapLegendCollapsed) === 'true';
  }

  function writeLegendCollapsed(collapsed: boolean): void {
    writePref(USER_PREFS_KEYS.mapLegendCollapsed, String(collapsed));
  }

  function readActiveOverlays(): string[] {
    return readJsonPref<string[]>(USER_PREFS_KEYS.mapActiveOverlays, []);
  }

  function writeActiveOverlays(overlays: string[]): void {
    writePref(USER_PREFS_KEYS.mapActiveOverlays, JSON.stringify(overlays));
  }

  function readLayerOpacities(): Partial<Record<string, number>> {
    return readJsonPref<Partial<Record<string, number>>>(USER_PREFS_KEYS.mapLayerOpacities, {});
  }

  function writeLayerOpacities(opacities: Record<string, number>): void {
    writePref(USER_PREFS_KEYS.mapLayerOpacities, JSON.stringify(opacities));
  }

  function readBasemapEnabled(): boolean | null {
    return readBooleanPref(USER_PREFS_KEYS.mapBasemapEnabled);
  }

  function writeBasemapEnabled(enabled: boolean): void {
    writePref(USER_PREFS_KEYS.mapBasemapEnabled, String(enabled));
  }

  function readTracksEnabled(): boolean | null {
    return readBooleanPref(USER_PREFS_KEYS.mapTracksEnabled);
  }

  function writeTracksEnabled(enabled: boolean): void {
    writePref(USER_PREFS_KEYS.mapTracksEnabled, String(enabled));
  }

  function readTrackPointsVisible(): boolean | null {
    return readBooleanPref(USER_PREFS_KEYS.mapTrackPointsVisible);
  }

  function writeTrackPointsVisible(visible: boolean): void {
    writePref(USER_PREFS_KEYS.mapTrackPointsVisible, String(visible));
  }

  function readHeatmapVisible(): boolean | null {
    return readBooleanPref(USER_PREFS_KEYS.mapHeatmapVisible);
  }

  function writeHeatmapVisible(visible: boolean): void {
    writePref(USER_PREFS_KEYS.mapHeatmapVisible, String(visible));
  }

  function readWasAutoDimmed(): boolean | null {
    const saved = readBooleanPref(USER_PREFS_KEYS.mapWasAutoDimmed);
    if (saved !== null) return saved;
    try {
      const legacy = localStorage.getItem(LEGACY_WAS_AUTO_DIMMED_KEY);
      return legacy === null ? null : legacy !== 'false';
    } catch {
      return null;
    }
  }

  function clearWasAutoDimmed(): void {
    removePref(USER_PREFS_KEYS.mapWasAutoDimmed);
    try {
      localStorage.removeItem(LEGACY_WAS_AUTO_DIMMED_KEY);
    } catch {
      /* ignore */
    }
  }

  function consumeLegacyAutoDimPreference(layerOpacities: Record<string, number>): boolean {
    const savedAutoDim = readWasAutoDimmed();
    if (savedAutoDim === null) return false;

    let changed = false;
    if (savedAutoDim && layerOpacities.basemap === LEGACY_AUTO_DIM_BASEMAP_OPACITY) {
      layerOpacities.basemap = DEFAULT_LAYER_OPACITIES.basemap;
      changed = true;
    }
    clearWasAutoDimmed();
    return changed;
  }

  function clearLegacyAutoDimPrefs(): void {
    try {
      localStorage.removeItem('mtl-mapEnhanced');
      localStorage.removeItem('mtl-dimOpacity');
      localStorage.removeItem('mtl-grayscaleAmount');
    } catch {
      /* ignore */
    }
  }

  function hasLegacyAutoDimPrefs(): boolean {
    try {
      return localStorage.getItem('mtl-mapEnhanced') !== null;
    } catch {
      return false;
    }
  }

  return {
    readTheme,
    writeTheme,
    readLegendCollapsed,
    writeLegendCollapsed,
    readActiveOverlays,
    writeActiveOverlays,
    readLayerOpacities,
    writeLayerOpacities,
    readBasemapEnabled,
    writeBasemapEnabled,
    readTracksEnabled,
    writeTracksEnabled,
    readTrackPointsVisible,
    writeTrackPointsVisible,
    readHeatmapVisible,
    writeHeatmapVisible,
    readWasAutoDimmed,
    clearWasAutoDimmed,
    consumeLegacyAutoDimPreference,
    hasLegacyAutoDimPrefs,
    clearLegacyAutoDimPrefs,
  };
}

function readJsonPref<T>(key: UserPrefKey, fallback: T): T {
  const raw = readPref(key);
  if (raw == null) return fallback;
  try {
    return JSON.parse(raw) as T;
  } catch {
    return fallback;
  }
}

function readBooleanPref(key: UserPrefKey): boolean | null {
  const raw = readPref(key);
  if (raw == null) return null;
  return raw !== 'false';
}
