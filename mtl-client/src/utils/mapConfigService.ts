/**
 * mapConfigService.ts — Fetches and caches the server-side map configuration.
 *
 * The config tells the client whether to use local vector tiles (PMTiles from docker-maps)
 * or remote raster tiles from configured providers.
 */

import { apiClient } from '@/utils/apiClient';
import { describeError, startStartupTimer, startupLog } from '@/utils/startupDiagnostics';
import { readJsonStorage, STORAGE_KEYS, writeJsonStorage } from '@/utils/appStorage';
import {
  MapConfigDtoTileModeEnum,
  MapConfigDtoTileSourceEnum,
  type MapConfigDto,
} from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/MapConfigDto';
import type { MapBoundsDto } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/MapBoundsDto';
import type { MapRasterStyleDto } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/MapRasterStyleDto';

export { MapConfigDtoTileModeEnum, MapConfigDtoTileSourceEnum };

export type MapTileMode = MapConfigDtoTileModeEnum;
export type MapTileSource = MapConfigDtoTileSourceEnum;

export type MapConfig = MapConfigDto & {
  tileMode: MapTileMode;
  tileBaseUrl: string;
  tileArchiveUrl?: string;
  tilesetName: string;
  lowzoomTilesetName: string;
  lowzoomArchiveUrl?: string;
  tileSource?: MapTileSource;
  archiveId?: string;
  remoteRasterStyles: Record<string, MapRasterStyleDto>;
  /** Initial map viewport bounds from explicit config, stored tracks, or server default. */
  initialBounds?: MapBoundsDto;
  /** Legacy bounded-map metadata, normally omitted. */
  demoAreaBbox?: number[];
  /** Legacy bounded-map metadata, normally omitted. */
  demoAreaMaxZoom?: number;
  /** True when the backend planner feature flag is enabled. */
  plannerEnabled?: boolean;
  /** Routing profiles exposed by the backend planner feature. */
  plannerProfiles?: string[];
  /** True when config was restored from localStorage because the server was unreachable. */
  offline?: boolean;
};

const DEFAULT_CONFIG: MapConfig = {
  tileMode: MapConfigDtoTileModeEnum.Local,
  tileBaseUrl: '',
  tilesetName: 'planet',
  lowzoomTilesetName: 'world-lowzoom',
  remoteRasterStyles: {
    light: {
      url: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
      attribution: '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
    },
    'light-topo': {
      url: 'https://tile.opentopomap.org/{z}/{x}/{y}.png',
      attribution:
        'Map data: © <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors, SRTM | ' +
        'Map style: © <a href="https://opentopomap.org">OpenTopoMap</a> ' +
        '(<a href="https://creativecommons.org/licenses/by-sa/3.0/">CC-BY-SA</a>)',
    },
    dark: {
      url: 'https://basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png',
      attribution:
        '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors ' +
        '© <a href="https://carto.com/attributions">CARTO</a>',
    },
  },
  plannerEnabled: false,
  plannerProfiles: [],
};

const STORAGE_KEY = STORAGE_KEYS.mapConfigCache;

let cachedConfig: MapConfig | null = null;
/**
 * Shared promise for an in-flight request. Callers that arrive while a
 * previous call is still running (e.g. LoginView warm-up + Map.vue mount)
 * share the same HTTP request instead of issuing a second one.
 */
let inFlight: Promise<MapConfig> | null = null;

/**
 * Fetch map configuration from the server. Caches the result for the session.
 * On failure, restores the last-known-good config from localStorage. Falls back
 * to remote raster if nothing is stored.
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
        tileArchiveUrl: cachedConfig.tileArchiveUrl,
        tileSource: cachedConfig.tileSource,
        archiveId: cachedConfig.archiveId,
        tilesetName: cachedConfig.tilesetName,
        lowzoomTilesetName: cachedConfig.lowzoomTilesetName,
      });
      // Persist so we can restore the last known map viewport/tile config when offline.
      writeJsonStorage(STORAGE_KEY, cachedConfig);
      return cachedConfig;
    } catch (e) {
      timer.warn('Map config fetch failed; falling back', describeError(e));
      console.warn('Failed to fetch map config, falling back to offline / remote raster mode:', e);
      // Try to restore last-known-good config from localStorage
      const stored = readJsonStorage<MapConfig | null>(STORAGE_KEY, null);
      if (stored) {
        const fallback: MapConfig = { ...stored, offline: true };
        startupLog('mapconfig', 'Restored map config from localStorage', {
          tileMode: fallback.tileMode,
          tileBaseUrl: fallback.tileBaseUrl,
        });
        console.log('Restored map config from localStorage (offline mode)');
        // Intentionally NOT memoized into cachedConfig so a subsequent
        // caller (or background retry) can still fetch the real config.
        return fallback;
      }
      const fallback: MapConfig = { ...DEFAULT_CONFIG, tileMode: 'remote', offline: true };
      startupLog('mapconfig', 'Using built-in remote fallback config', {
        tileMode: fallback.tileMode,
        remoteRasterStyles: Object.keys(fallback.remoteRasterStyles),
      });
      return fallback;
    } finally {
      inFlight = null;
    }
  })();

  return inFlight;
}

export function mainTileArchiveUrl(config: MapConfig): string {
  return config.tileArchiveUrl || `${config.tileBaseUrl}/${config.tilesetName}.pmtiles`;
}

export function lowzoomTileArchiveUrl(config: MapConfig): string {
  return config.lowzoomArchiveUrl || `${config.tileBaseUrl}/${config.lowzoomTilesetName}.pmtiles`;
}

/** Reset the cached config (e.g. on logout). */
export function clearMapConfigCache(): void {
  cachedConfig = null;
  inFlight = null;
}
