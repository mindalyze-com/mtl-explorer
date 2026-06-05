import { describe, expect, it } from 'vitest';
import {
  collectStyleAttributions,
  isRemoteRasterMapTheme,
  resolveConfiguredMapStyle,
} from '@/components/map/mapStyleResolver';
import { MapConfigDtoTileModeEnum, type MapConfig } from '@/utils/mapConfigService';
import { DEFAULT_RASTER_ATTRIBUTION, TERRAIN_DEM_SOURCE_ID, TERRAIN_HILLSHADE_LAYER_ID } from '@/utils/mapStyle';
import type { StyleSpecification } from 'maplibre-gl';

type TestRasterStyle = StyleSpecification & {
  sources: {
    'raster-tiles': { tiles: string[]; attribution?: string };
    [TERRAIN_DEM_SOURCE_ID]?: unknown;
  };
  layers: Array<{ id: string; paint?: Record<string, number> }>;
};

function mapConfig(overrides: Partial<MapConfig> = {}): MapConfig {
  return {
    tileMode: MapConfigDtoTileModeEnum.Local,
    tileBaseUrl: '/mtl/api/map-proxy/prod',
    tilesetName: 'planet',
    lowzoomTilesetName: 'world-lowzoom',
    remoteRasterStyles: {
      light: {
        url: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
        attribution: '© OpenStreetMap contributors',
      },
      'light-topo': {
        url: 'https://tile.opentopomap.org/{z}/{x}/{y}.png',
        attribution: '© OpenTopoMap',
      },
      dark: {
        url: 'https://basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png',
        attribution: '© CARTO',
      },
    },
    ...overrides,
  };
}

describe('resolveConfiguredMapStyle', () => {
  it('uses a built-in raster fallback when the configured remote URL is missing', () => {
    const resolved = resolveConfiguredMapStyle({
      config: mapConfig({ remoteRasterStyles: {} }),
      theme: 'light',
      localTilesReady: false,
    });

    expect(resolved.styleMode).toBe('fallback-raster');
    expect(resolved.attributions).toContain(DEFAULT_RASTER_ATTRIBUTION);
    expect(JSON.stringify(resolved.style)).toContain('https://tile.openstreetmap.org/{z}/{x}/{y}.png');
  });

  it('forces configured remote raster when local vector tiles are ready', () => {
    const remoteTileUrl = 'https://tiles.example.test/{z}/{x}/{y}.png';
    const resolved = resolveConfiguredMapStyle({
      config: mapConfig({
        tileMode: MapConfigDtoTileModeEnum.Local,
        remoteRasterStyles: {
          light: { url: remoteTileUrl, attribution: '© Example Light Tiles' },
        },
      }),
      theme: 'light',
      localTilesReady: true,
      mapSourceMode: 'remote',
    });

    const style = resolved.style as TestRasterStyle;
    expect(resolved.styleMode).toBe('remote-raster');
    expect(resolved.attributions).toEqual(['© Example Light Tiles']);
    expect(style.sources['raster-tiles'].tiles).toEqual([remoteTileUrl]);
  });

  it('keeps swisstopo styles only in automatic mode', () => {
    const remoteTileUrl = 'https://tiles.example.test/{z}/{x}/{y}.png';
    const config = mapConfig({
      remoteRasterStyles: {
        light: { url: remoteTileUrl, attribution: '© Example Light Tiles' },
      },
    });

    expect(resolveConfiguredMapStyle({ config, theme: 'swisstopo' }).styleMode).toBe('swisstopo');

    const resolved = resolveConfiguredMapStyle({
      config,
      theme: 'swisstopo',
      localTilesReady: true,
      mapSourceMode: 'remote',
    });

    const style = resolved.style as TestRasterStyle;
    expect(resolved.styleMode).toBe('remote-raster');
    expect(style.sources['raster-tiles'].tiles).toEqual([remoteTileUrl]);
  });

  it('uses the configured dark remote raster style with its attribution', () => {
    const remoteTileUrl = 'https://tiles.example.test/{z}/{x}/{y}.png';
    const attribution = '© Example Dark Tiles';
    const resolved = resolveConfiguredMapStyle({
      config: mapConfig({
        tileMode: MapConfigDtoTileModeEnum.Remote,
        remoteRasterStyles: {
          light: { url: 'https://light.example.test/{z}/{x}/{y}.png', attribution: '© Example Light Tiles' },
          dark: { url: remoteTileUrl, attribution },
        },
      }),
      theme: 'dark',
      localTilesReady: false,
    });

    const style = resolved.style as TestRasterStyle;
    const darkBrightnessMax = style.layers[0].paint?.['raster-brightness-max'];
    expect(resolved.styleMode).toBe('remote-raster');
    expect(resolved.attributions).toEqual([attribution]);
    expect(style.sources['raster-tiles'].tiles).toEqual([remoteTileUrl]);
    expect(style.sources['raster-tiles'].attribution).toBe(attribution);
    expect(darkBrightnessMax).toBeDefined();
    expect(Number(darkBrightnessMax)).toBeLessThan(1);
  });

  it('uses the configured topo remote raster style and adds hillshade', () => {
    const remoteTileUrl = 'https://tiles.example.test/{z}/{x}/{y}.png';
    const attribution = '© Example Topo Tiles';
    const resolved = resolveConfiguredMapStyle({
      config: mapConfig({
        tileMode: MapConfigDtoTileModeEnum.Remote,
        remoteRasterStyles: {
          light: { url: 'https://light.example.test/{z}/{x}/{y}.png', attribution: '© Example Light Tiles' },
          'light-topo': { url: remoteTileUrl, attribution },
        },
      }),
      theme: 'light-topo',
      localTilesReady: false,
    });

    const style = resolved.style as TestRasterStyle;
    expect(resolved.styleMode).toBe('remote-raster');
    expect(resolved.attributions).toContain(attribution);
    expect(resolved.attributions).toContain('Elevation: <a href="https://mapterhorn.com/attribution/">Mapterhorn</a>');
    expect(style.sources['raster-tiles'].tiles).toEqual([remoteTileUrl]);
    expect(style.sources['raster-tiles'].attribution).toBe(attribution);
    expect(style.sources[TERRAIN_DEM_SOURCE_ID]).toBeTruthy();
    expect(style.layers.some((layer: { id: string }) => layer.id === TERRAIN_HILLSHADE_LAYER_ID)).toBe(true);
  });

  it('derives grayscale from the configured light raster style', () => {
    const remoteTileUrl = 'https://light.example.test/{z}/{x}/{y}.png';
    const resolved = resolveConfiguredMapStyle({
      config: mapConfig({
        tileMode: MapConfigDtoTileModeEnum.Remote,
        remoteRasterStyles: {
          light: { url: remoteTileUrl, attribution: '© Example Light Tiles' },
        },
      }),
      theme: 'grayscale',
      localTilesReady: false,
    });

    const style = resolved.style as TestRasterStyle;
    expect(resolved.styleMode).toBe('remote-raster');
    expect(resolved.attributions).toEqual(['© Example Light Tiles']);
    expect(style.sources['raster-tiles'].tiles).toEqual([remoteTileUrl]);
    expect(style.layers[0].paint?.['raster-saturation']).toBe(-1);
  });

  it('does not invent attribution for an incomplete configured remote raster style', () => {
    const remoteTileUrl = 'https://tiles.example.test/{z}/{x}/{y}.png';
    const resolved = resolveConfiguredMapStyle({
      config: mapConfig({
        tileMode: MapConfigDtoTileModeEnum.Remote,
        remoteRasterStyles: {
          light: { url: remoteTileUrl },
        },
      }),
      theme: 'light',
      localTilesReady: false,
    });

    const style = resolved.style as TestRasterStyle;
    expect(resolved.styleMode).toBe('remote-raster');
    expect(resolved.attributions).toEqual([]);
    expect(style.sources['raster-tiles'].tiles).toEqual([remoteTileUrl]);
    expect(style.sources['raster-tiles'].attribution).toBe('');
  });

  it('collects unique non-empty source attributions from object styles', () => {
    const style = {
      version: 8,
      sources: {
        base: { type: 'raster', tiles: ['https://a.example.test/{z}/{x}/{y}.png'], attribution: ' © Base ' },
        overlay: { type: 'raster', tiles: ['https://b.example.test/{z}/{x}/{y}.png'], attribution: '© Base' },
        empty: { type: 'geojson', data: { type: 'FeatureCollection', features: [] }, attribution: '  ' },
      },
      layers: [],
    } as unknown as StyleSpecification;

    expect(collectStyleAttributions(style)).toEqual(['© Base']);
  });

  it('reports remote raster-compatible themes for the map source setting', () => {
    expect(isRemoteRasterMapTheme('light-topo')).toBe(true);
    expect(isRemoteRasterMapTheme('swisstopo')).toBe(false);
  });
});
