import { mainTileArchiveUrl, MapConfigDtoTileModeEnum, type MapConfig } from '@/utils/mapConfigService';
import type { MapSourceMode } from '@/stores/mapSettingsStore';
import type { StyleSpecification } from 'maplibre-gl';
import {
  buildFallbackRasterStyle,
  buildLocalVectorStyleFromArchiveUrl,
  buildRemoteRasterStyle,
  normalizeMapTheme,
  SWISSTOPO_COLOR_STYLE_URL,
  SWISSTOPO_STYLE_URL,
  TOPO_CONTRAST_THEME,
  type MapTheme,
} from '@/utils/mapStyle';

export interface ResolvedMapStyle {
  style: string | StyleSpecification;
  styleMode: string;
  attributions: string[];
}

interface ResolveMapStyleOptions {
  config: MapConfig;
  theme: string;
  localTilesReady?: boolean;
  mapSourceMode?: MapSourceMode;
}

const REMOTE_RASTER_THEME_CODES = new Set<string>(['light', 'light-topo', 'dark', 'grayscale']);

function isUsableTileTemplate(value: unknown): value is string {
  return typeof value === 'string' && value.includes('{z}') && value.includes('{x}') && value.includes('{y}');
}

export function isRemoteRasterMapTheme(theme: string): boolean {
  return REMOTE_RASTER_THEME_CODES.has(theme);
}

function remoteRasterThemeKey(theme: MapTheme): string {
  if (theme === TOPO_CONTRAST_THEME) return 'light-topo';
  return theme === 'grayscale' ? 'light' : theme;
}

function configuredRemoteRasterStyle(config: MapConfig, theme: string) {
  const rasterTheme = theme as MapTheme;
  return config.remoteRasterStyles?.[remoteRasterThemeKey(rasterTheme)] ?? config.remoteRasterStyles?.light;
}

export function collectStyleAttributions(style: string | StyleSpecification): string[] {
  if (typeof style === 'string') return [];
  const attributions = new Set<string>();
  for (const source of Object.values(style.sources ?? {})) {
    const sourceAttribution = (source as { attribution?: unknown }).attribution;
    const attribution = typeof sourceAttribution === 'string' ? sourceAttribution.trim() : '';
    if (attribution) attributions.add(attribution);
  }
  return [...attributions];
}

export function resolveConfiguredMapStyle(options: ResolveMapStyleOptions): ResolvedMapStyle {
  const { config } = options;
  const theme = normalizeMapTheme(options.theme);
  const forceRemoteRaster = options.mapSourceMode === 'remote';
  if (!forceRemoteRaster && theme === 'swisstopo') {
    return { style: SWISSTOPO_STYLE_URL, styleMode: 'swisstopo', attributions: [] };
  }
  if (!forceRemoteRaster && theme === 'swisstopo-color') {
    return { style: SWISSTOPO_COLOR_STYLE_URL, styleMode: 'swisstopo-color', attributions: [] };
  }
  if (!forceRemoteRaster && config.tileMode === MapConfigDtoTileModeEnum.Local && options.localTilesReady !== false) {
    const style = buildLocalVectorStyleFromArchiveUrl(mainTileArchiveUrl(config), theme);
    return {
      style,
      styleMode: 'local-vector',
      attributions: collectStyleAttributions(style),
    };
  }
  const rasterStyle = configuredRemoteRasterStyle(config, theme);
  if (!isUsableTileTemplate(rasterStyle?.url)) {
    const style = buildFallbackRasterStyle(theme);
    return {
      style,
      styleMode: 'fallback-raster',
      attributions: collectStyleAttributions(style),
    };
  }
  const style = buildRemoteRasterStyle(rasterStyle.url, theme, rasterStyle.attribution ?? '');
  return {
    style,
    styleMode: 'remote-raster',
    attributions: collectStyleAttributions(style),
  };
}
