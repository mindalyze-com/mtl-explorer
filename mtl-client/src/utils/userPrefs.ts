/**
 * Central registry of all localStorage keys used by the app.
 *
 * Replaces 49+ ad-hoc localStorage call sites scattered across components and
 * services. Goals:
 *   1. **Single source of truth** for key names — typos can't silently miss data.
 *   2. **Consistent namespacing** under the `mtl.` prefix.
 *   3. **One-time migration** from legacy unprefixed / inconsistent keys.
 *
 * Conventions:
 *   - All new keys MUST start with `mtl.` (dot-separated namespaces).
 *   - Use the {@link USER_PREFS_KEYS} constant — never hard-code a key string.
 *   - For reactive bindings, prefer {@link import('@/composables/useLocalStorage').useLocalStorage}.
 */

export const USER_PREFS_KEYS = {
  // Auth
  jwt: 'mtl.jwt',

  // Appearance
  colorScheme: 'mtl.color-scheme',
  locale: 'mtl.locale',

  // Track-details panel
  trackGraphHeight: 'mtl.track-details.graph-height',
  trackMiniMapHeight: 'mtl.track-details.minimap-height',

  // Map view (still read directly by Map.vue today; will migrate during C.1)
  mapTheme: 'mtl.map.theme',
  mapLegendCollapsed: 'mtl.map.legend-collapsed',
  mapActiveOverlays: 'mtl.map.active-overlays',
  mapLayerOpacities: 'mtl.map.layer-opacities',
  mapBasemapEnabled: 'mtl.map.basemap-enabled',
  mapTracksEnabled: 'mtl.map.tracks-enabled',
  mapTrackPointsVisible: 'mtl.map.track-points-visible',
  mapHeatmapVisible: 'mtl.map.heatmap-visible',
  mapWasAutoDimmed: 'mtl.map.was-auto-dimmed',

  // Caches / housekeeping
  mapConfigCache: 'mtl.map.config-cache',
  backgroundsDisplayed: 'mtl.backgrounds.displayed',
  backgroundCacheVersion: 'mtl.backgrounds.cache-version',
  startupCrashGuard: 'mtl.startup.crash-guard',
} as const;

export type UserPrefKey = (typeof USER_PREFS_KEYS)[keyof typeof USER_PREFS_KEYS];

/**
 * Maps **legacy** (unprefixed / inconsistent) keys to their new namespaced
 * counterparts. Run once at module load by {@link migrateLegacyKeys}.
 *
 * Add a new entry here when renaming a key so existing user data isn't lost.
 */
const LEGACY_KEY_MIGRATIONS: ReadonlyArray<readonly [string, string]> = [
  ['mtl_jwt_token', USER_PREFS_KEYS.jwt],
  ['mtl-color-scheme', USER_PREFS_KEYS.colorScheme],
  ['mtl-format-locale', USER_PREFS_KEYS.locale],
  ['mtl-track-details-graph-height', USER_PREFS_KEYS.trackGraphHeight],
  ['mtl-minimap-height', USER_PREFS_KEYS.trackMiniMapHeight],
  ['mapTheme', USER_PREFS_KEYS.mapTheme],
  ['mtl-legendCollapsed', USER_PREFS_KEYS.mapLegendCollapsed],
  ['mtl-activeOverlays', USER_PREFS_KEYS.mapActiveOverlays],
  ['mtl-layerOpacities', USER_PREFS_KEYS.mapLayerOpacities],
  ['mtl-basemapEnabled', USER_PREFS_KEYS.mapBasemapEnabled],
  ['mtl-tracksEnabled', USER_PREFS_KEYS.mapTracksEnabled],
  ['mtl-trackPointsVisible', USER_PREFS_KEYS.mapTrackPointsVisible],
  ['mtl-heatmapVisible', USER_PREFS_KEYS.mapHeatmapVisible],
  ['mtl-wasAutoDimmed', USER_PREFS_KEYS.mapWasAutoDimmed],
  ['mtl-mapConfig', USER_PREFS_KEYS.mapConfigCache],
  ['mtl-bg-displayed', USER_PREFS_KEYS.backgroundsDisplayed],
  ['mtl-bg-version', USER_PREFS_KEYS.backgroundCacheVersion],
  ['mtl.startupDiag', USER_PREFS_KEYS.startupCrashGuard],
];

let migrationRan = false;

/**
 * Copies legacy localStorage entries to their namespaced names if (and only
 * if) the new key isn't already set. Called automatically the first time
 * {@link readPref} / {@link writePref} / {@link useLocalStorage} touches storage.
 *
 * Safe to call repeatedly — guarded by a module-level flag and only runs once
 * per page load.
 */
export function migrateLegacyKeys(): void {
  if (migrationRan) return;
  migrationRan = true;
  if (typeof localStorage === 'undefined') return;
  for (const [oldKey, newKey] of LEGACY_KEY_MIGRATIONS) {
    try {
      if (localStorage.getItem(newKey) !== null) continue;
      const legacyValue = localStorage.getItem(oldKey);
      if (legacyValue !== null) {
        localStorage.setItem(newKey, legacyValue);
        // Intentionally NOT removing the legacy key yet — leaves a recovery
        // path. A second migration pass in a future release can clean up.
      }
    } catch {
      // Ignore quota / privacy-mode failures.
    }
  }
}

/** Read a string pref (after running migration). Returns null if absent. */
export function readPref(key: UserPrefKey): string | null {
  migrateLegacyKeys();
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

/** Write a string pref. Silently no-ops on quota / privacy-mode failure. */
export function writePref(key: UserPrefKey, value: string): void {
  migrateLegacyKeys();
  try {
    localStorage.setItem(key, value);
  } catch {
    /* ignore */
  }
}

/** Remove a pref. Silently no-ops on failure. */
export function removePref(key: UserPrefKey): void {
  try {
    localStorage.removeItem(key);
  } catch {
    /* ignore */
  }
}

/**
 * Test-only: re-arm the one-shot migration guard so the next call to
 * {@link migrateLegacyKeys} actually runs again. Production code must not
 * call this — migration is intended to run exactly once per page load.
 */
export function _resetMigrationGuardForTests(): void {
  migrationRan = false;
}
