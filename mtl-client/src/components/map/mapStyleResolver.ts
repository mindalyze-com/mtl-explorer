import { mainTileArchiveUrl, MapConfigDtoTileModeEnum, type MapConfig } from '@/utils/mapConfigService';
import type { StyleSpecification } from 'maplibre-gl';
import {
  buildFallbackRasterStyle,
  buildLocalVectorStyleFromArchiveUrl,
  buildRemoteRasterStyle,
  SWISSTOPO_COLOR_STYLE_URL,
  SWISSTOPO_STYLE_URL,
  type MapTheme,
} from '@/utils/mapStyle';

export interface ResolvedMapStyle {
  style: string | StyleSpecification;
  styleMode: string;
}

interface ResolveMapStyleOptions {
  config: MapConfig;
  theme: string;
  localTilesReady?: boolean;
}

function isUsableTileTemplate(value: unknown): value is string {
  return typeof value === 'string' && value.includes('{z}') && value.includes('{x}') && value.includes('{y}');
}

export function resolveConfiguredMapStyle(options: ResolveMapStyleOptions): ResolvedMapStyle {
  const { config, theme } = options;
  if (theme === 'swisstopo') {
    return { style: SWISSTOPO_STYLE_URL, styleMode: 'swisstopo' };
  }
  if (theme === 'swisstopo-color') {
    return { style: SWISSTOPO_COLOR_STYLE_URL, styleMode: 'swisstopo-color' };
  }
  if (config.tileMode === MapConfigDtoTileModeEnum.Local && options.localTilesReady !== false) {
    return {
      style: buildLocalVectorStyleFromArchiveUrl(mainTileArchiveUrl(config), theme as MapTheme),
      styleMode: 'local-vector',
    };
  }
  const rasterTheme = theme as MapTheme;
  if (!isUsableTileTemplate(config.remoteTileUrl)) {
    return {
      style: buildFallbackRasterStyle(rasterTheme),
      styleMode: 'fallback-raster',
    };
  }
  return {
    style: buildRemoteRasterStyle(config.remoteTileUrl, rasterTheme),
    styleMode: 'remote-raster',
  };
}
