import { warnSanitized } from '@/utils/safeLogging';
/**
 * mapConfigService.ts — Fetches and caches the server-side map configuration.
 *
 * The config tells the client whether to use local vector tiles (PMTiles from docker-maps)
 * or remote raster tiles from configured providers.
 */

import { apiClient } from '@/utils/apiClient';
import { startStartupTimer, startupLog } from '@/utils/startupDiagnostics';
import { readJsonStorage, STORAGE_KEYS, writeJsonStorage } from '@/utils/appStorage';
import { DEFAULT_RASTER_ATTRIBUTION, DEFAULT_RASTER_TILE_URL } from '@/utils/rasterMapDefaults';
import { STARTUP_REQUEST_TIMEOUT_MS } from '@/utils/startupTiming';
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
  /** True when the server config request failed and this is a recoverable fallback. */
  configLoadFailed?: boolean;
};

const DEFAULT_CONFIG: MapConfig = {
  tileMode: MapConfigDtoTileModeEnum.Local,
  tileBaseUrl: '',
  tilesetName: 'planet',
  lowzoomTilesetName: 'world-lowzoom',
  remoteRasterStyles: {
    light: {
      url: DEFAULT_RASTER_TILE_URL,
      attribution: DEFAULT_RASTER_ATTRIBUTION,
    },
    'light-topo': {
      url: 'https://tile.opentopomap.org/{z}/{x}/{y}.png',
      attribution:
        'Map data: © <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors, SRTM | ' +
        'Map style: © <a href="https://opentopomap.org">OpenTopoMap</a> ' +
        '(<a href="https://creativecommons.org/licenses/by-sa/3.0/">CC-BY-SA</a>)',
    },
    dark: {
      url: DEFAULT_RASTER_TILE_URL,
      attribution: DEFAULT_RASTER_ATTRIBUTION,
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
        timeout: STARTUP_REQUEST_TIMEOUT_MS,
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
      warnSanitized('Failed to fetch map config, falling back to offline / remote raster mode:', e, {
        topic1: 'application',
        topic2: 'mapConfigService',
        topic3: 'fetchMapConfig',
      });
      // Try to restore last-known-good config from localStorage
      const stored = readJsonStorage<MapConfig | null>(STORAGE_KEY, null);
      if (stored) {
        const fallback: MapConfig = { ...stored, offline: true, configLoadFailed: true };
        startupLog('mapconfig', 'Restored map config from localStorage', {
          tileMode: fallback.tileMode,
          tileBaseUrl: fallback.tileBaseUrl,
        });
        console.log('Restored map config from localStorage (offline mode)');
        // Intentionally NOT memoized into cachedConfig so a subsequent
        // caller (or background retry) can still fetch the real config.
        return fallback;
      }
      const fallback: MapConfig = {
        ...DEFAULT_CONFIG,
        tileMode: MapConfigDtoTileModeEnum.Remote,
        offline: true,
        configLoadFailed: true,
      };
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
