import { beforeEach, describe, expect, it } from 'vitest';
import { DEFAULT_LAYER_OPACITIES, DEFAULT_MAP_THEME, useMapPreferences } from '@/components/map/useMapPreferences';
import { USER_PREFS_KEYS, _resetMigrationGuardForTests } from '@/utils/userPrefs';

describe('useMapPreferences', () => {
  beforeEach(() => {
    localStorage.clear();
    _resetMigrationGuardForTests();
  });

  it('uses defaults when no map preferences are stored', () => {
    const prefs = useMapPreferences();

    expect(prefs.readTheme()).toBe(DEFAULT_MAP_THEME);
    expect(prefs.readLegendCollapsed()).toBe(false);
    expect(prefs.readActiveOverlays()).toEqual([]);
    expect(prefs.readLayerOpacities()).toEqual({});
  });

  it('reads migrated legacy map keys through the centralized registry', () => {
    localStorage.setItem('mapTheme', 'dark');
    localStorage.setItem('mtl-activeOverlays', JSON.stringify(['wanderland']));
    localStorage.setItem('mtl-layerOpacities', JSON.stringify({ basemap: 60 }));

    const prefs = useMapPreferences();

    expect(prefs.readTheme()).toBe('dark');
    expect(prefs.readActiveOverlays()).toEqual(['wanderland']);
    expect(prefs.readLayerOpacities()).toEqual({ basemap: 60 });
    expect(localStorage.getItem(USER_PREFS_KEYS.mapTheme)).toBe('dark');
  });

  it('persists layer states under namespaced keys', () => {
    const prefs = useMapPreferences();
    prefs.writeLayerOpacities({ ...DEFAULT_LAYER_OPACITIES, basemap: 55 });
    prefs.writeBasemapEnabled(false);
    prefs.writeTrackPointsVisible(false);
    prefs.writeHeatmapVisible(true);

    expect(JSON.parse(localStorage.getItem(USER_PREFS_KEYS.mapLayerOpacities) ?? '{}').basemap).toBe(55);
    expect(localStorage.getItem(USER_PREFS_KEYS.mapBasemapEnabled)).toBe('false');
    expect(localStorage.getItem(USER_PREFS_KEYS.mapTrackPointsVisible)).toBe('false');
    expect(localStorage.getItem(USER_PREFS_KEYS.mapHeatmapVisible)).toBe('true');
  });

  it('reads and clears legacy auto-dim flags without re-migrating them', () => {
    localStorage.setItem('mtl-wasAutoDimmed', 'true');

    const prefs = useMapPreferences();
    expect(prefs.readWasAutoDimmed()).toBe(true);
    expect(localStorage.getItem(USER_PREFS_KEYS.mapWasAutoDimmed)).toBeNull();

    localStorage.setItem(USER_PREFS_KEYS.mapWasAutoDimmed, 'true');

    prefs.clearWasAutoDimmed();
    _resetMigrationGuardForTests();

    expect(prefs.readWasAutoDimmed()).toBeNull();
    expect(localStorage.getItem('mtl-wasAutoDimmed')).toBeNull();
  });

  it('restores the old automatic basemap opacity once and clears the auto-dim flag', () => {
    localStorage.setItem(USER_PREFS_KEYS.mapWasAutoDimmed, 'true');
    const prefs = useMapPreferences();
    const opacities = { ...DEFAULT_LAYER_OPACITIES, basemap: 60 };

    expect(prefs.consumeLegacyAutoDimPreference(opacities)).toBe(true);

    expect(opacities.basemap).toBe(DEFAULT_LAYER_OPACITIES.basemap);
    expect(prefs.readWasAutoDimmed()).toBeNull();
  });

  it('keeps manually changed basemap opacity while clearing the auto-dim flag', () => {
    localStorage.setItem(USER_PREFS_KEYS.mapWasAutoDimmed, 'true');
    const prefs = useMapPreferences();
    const opacities = { ...DEFAULT_LAYER_OPACITIES, basemap: 55 };

    expect(prefs.consumeLegacyAutoDimPreference(opacities)).toBe(false);

    expect(opacities.basemap).toBe(55);
    expect(prefs.readWasAutoDimmed()).toBeNull();
  });
});
