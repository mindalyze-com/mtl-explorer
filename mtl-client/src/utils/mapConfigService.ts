/**
 * mapConfigService.ts — Fetches and caches the server-side map configuration.
 *
 * The config tells the client whether to use local vector tiles (PMTiles from docker-maps)
 * or remote raster tiles (OSM from the internet).
 */

import { apiClient } from '@/utils/apiClient';
import { describeError, startStartupTimer, startupLog } from '@/utils/startupDiagnostics';
import { USER_PREFS_KEYS } from '@/utils/userPrefs';

const backendUrl = import.meta.env.VITE_BACKEND_URL;

export interface MapConfig {
  tileMode: 'local' | 'remote';
  tileBaseUrl: string;
  tilesetName: string;
  lowzoomTilesetName: string;
  remoteTileUrl: string;
  /** Present on server response; used for initial map viewport. */
  initialCenterLng?: number;
  initialCenterLat?: number;
  initialZoom?: number;
  /** Bounding box of demo tile area [west, south, east, north]. Null in production. */
  demoAreaBbox?: number[];
  /** Maximum zoom level available in demo tiles. Null in production. */
  demoAreaMaxZoom?: number;
  /** True when the backend planner feature flag is enabled. */
  plannerEnabled?: boolean;
  /** Routing profiles exposed by the backend planner feature. */
  plannerProfiles?: string[];
  /** True when config was restored from localStorage because the server was unreachable. */
  offline?: boolean;
}

const DEFAULT_CONFIG: MapConfig = {
  tileMode: 'local',
  tileBaseUrl: '',
  tilesetName: 'planet',
  lowzoomTilesetName: 'world-lowzoom',
  remoteTileUrl: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
  plannerEnabled: false,
  plannerProfiles: [],
};

const STORAGE_KEY = USER_PREFS_KEYS.mapConfigCache;

let cachedConfig: MapConfig | null = null;
/**
 * Shared promise for an in-flight request. Callers that arrive while a
 * previous call is still running (e.g. LoginView warm-up + Map.vue mount)
 * share the same HTTP request instead of issuing a second one.
 */
let inFlight: Promise<MapConfig> | null = null;

/**
 * Fetch map configuration from the server. Caches the result for the session.
 * On failure, restores the last-known-good config from localStorage (preserving
 * initialCenter / tileBaseUrl for offline use). Falls back to remote raster if
 * nothing is stored.
 */
export async function fetchMapConfig(): Promise<MapConfig> {
  if (cachedConfig) {
    startupLog('mapconfig', 'Using cached map config', {
      tileMode: cachedConfig.tileMode,
      offline: cachedConfig.offline ?? false,
    });
    return cachedConfig;
  }
  if (inFlight) {
    startupLog('mapconfig', 'Reusing in-flight map config request');
    return inFlight;
  }

  const timer = startStartupTimer('mapconfig', 'Fetching map config');

  inFlight = (async () => {
    try {
      const resp = await apiClient.get<MapConfig>('api/map/config', {
        // Generous ceiling: this endpoint is a trivial in-memory DTO. A tight
        // budget only triggers false failures when heavy parallel requests
        // (e.g. first-login bulk track downloads) are hogging connections.
        timeout: 15000,
      });
      cachedConfig = resp.data;
      timer.success('Map config fetched', {
        tileMode: cachedConfig.tileMode,
        tileBaseUrl: cachedConfig.tileBaseUrl,
        tilesetName: cachedConfig.tilesetName,
        lowzoomTilesetName: cachedConfig.lowzoomTilesetName,
      });
      // Persist so we can restore initialCenter etc. when offline
      try { localStorage.setItem(STORAGE_KEY, JSON.stringify(cachedConfig)); } catch { /* quota */ }
      return cachedConfig;
    } catch (e) {
      timer.warn('Map config fetch failed; falling back', describeError(e));
      console.warn('Failed to fetch map config, falling back to offline / remote raster mode:', e);
      // Try to restore last-known-good config from localStorage
      try {
        const stored = localStorage.getItem(STORAGE_KEY);
        if (stored) {
          const fallback: MapConfig = { ...JSON.parse(stored), offline: true };
          startupLog('mapconfig', 'Restored map config from localStorage', {
            tileMode: fallback.tileMode,
            tileBaseUrl: fallback.tileBaseUrl,
          });
          console.log('Restored map config from localStorage (offline mode)');
          // Intentionally NOT memoized into cachedConfig so a subsequent
          // caller (or background retry) can still fetch the real config.
          return fallback;
        }
      } catch { /* corrupt JSON */ }
      const fallback: MapConfig = { ...DEFAULT_CONFIG, tileMode: 'remote', offline: true };
      startupLog('mapconfig', 'Using built-in remote fallback config', {
        tileMode: fallback.tileMode,
        remoteTileUrl: fallback.remoteTileUrl,
      });
      return fallback;
    } finally {
      inFlight = null;
    }
  })();

  return inFlight;
}

/** Reset the cached config (e.g. on logout). */
export function clearMapConfigCache(): void {
  cachedConfig = null;
  inFlight = null;
}
