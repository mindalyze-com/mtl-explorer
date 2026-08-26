import { describe, expect, it } from 'vitest';
import {
  collectStyleAttributions,
  isRemoteRasterMapTheme,
  resolveConfiguredMapStyle,
} from '@/components/map/mapStyleResolver';
import { MapConfigDtoTileModeEnum, type MapConfig } from '@/utils/mapConfigService';
import {
  DEFAULT_RASTER_ATTRIBUTION,
  HILLSHADE_DEM_SOURCE_ID,
  TERRAIN_DEM_SOURCE_ID,
  TERRAIN_HILLSHADE_LAYER_ID,
  TOPO_CONTRAST_THEME,
  TOPO_CONTRAST_URBAN_AREA_LAYER_ID,
  TOPO_CONTRAST_URBAN_LANDUSE_LAYER_ID,
} from '@/utils/mapStyle';
import type { StyleSpecification } from 'maplibre-gl';

type TestRasterStyle = StyleSpecification & {
  sources: {
    'raster-tiles': { tiles: string[]; attribution?: string };
    [HILLSHADE_DEM_SOURCE_ID]?: unknown;
    [TERRAIN_DEM_SOURCE_ID]?: unknown;
  };
  layers: Array<{ id: string; paint?: Record<string, number> }>;
};

type TestVectorStyle = StyleSpecification & {
  layers: Array<
    StyleSpecification['layers'][number] & {
      layout?: Record<string, unknown>;
      paint?: Record<string, unknown>;
    }
  >;
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

  it('builds the local OSM Topo Contrast vector style with hillshade and stronger city map emphasis', () => {
    const resolved = resolveConfiguredMapStyle({
      config: mapConfig(),
      theme: TOPO_CONTRAST_THEME,
      localTilesReady: true,
    });

    const style = resolved.style as TestVectorStyle;
    const highwayLayer = style.layers.find((layer) => layer.id === 'roads_highway');
    const landcoverLayer = style.layers.find((layer) => layer.id === 'landcover');
    const urbanAreaLayer = style.layers.find((layer) => layer.id === TOPO_CONTRAST_URBAN_AREA_LAYER_ID);
    const urbanLanduseLayer = style.layers.find((layer) => layer.id === TOPO_CONTRAST_URBAN_LANDUSE_LAYER_ID);
    const buildingLayer = style.layers.find((layer) => layer.id === 'buildings');
    const cityLayer = style.layers.find((layer) => layer.id === 'places_locality');
    const landcoverLayerIndex = style.layers.findIndex((layer) => layer.id === 'landcover');
    const urbanAreaLayerIndex = style.layers.findIndex((layer) => layer.id === TOPO_CONTRAST_URBAN_AREA_LAYER_ID);
    const urbanLanduseLayerIndex = style.layers.findIndex((layer) => layer.id === TOPO_CONTRAST_URBAN_LANDUSE_LAYER_ID);
    const parkLayerIndex = style.layers.findIndex((layer) => layer.id === 'landuse_park');
    const hillshadeLayerIndex = style.layers.findIndex((layer) => layer.id === TERRAIN_HILLSHADE_LAYER_ID);
    expect(resolved.styleMode).toBe('local-vector');
    expect(style.layers.some((layer) => layer.id === TERRAIN_HILLSHADE_LAYER_ID)).toBe(true);
    expect(highwayLayer?.paint?.['line-color']).toBe('#ef8a4a');
    expect(cityLayer?.paint?.['text-color']).toBe('#25211d');
    expect(urbanAreaLayer?.type).toBe('fill');
    expect(urbanAreaLayer?.source).toBe(landcoverLayer?.source);
    expect(urbanAreaLayer?.['source-layer']).toBe(landcoverLayer?.['source-layer']);
    expect(urbanAreaLayer?.filter).toEqual(['==', ['get', 'kind'], 'urban_area']);
    expect(urbanAreaLayer?.paint?.['fill-color']).toBe('#e2d0c3');
    expect(urbanAreaLayer?.paint?.['fill-outline-color']).toBe('rgba(166, 126, 99, 0.42)');
    expect(countZoomExpressions(urbanAreaLayer?.paint?.['fill-opacity'])).toBe(1);
    expect(urbanLanduseLayer?.type).toBe('fill');
    expect(urbanLanduseLayer?.source).toBe(landcoverLayer?.source);
    expect(urbanLanduseLayer?.['source-layer']).toBe('landuse');
    expect(urbanLanduseLayer?.filter).toEqual(['in', 'kind', 'residential', 'commercial', 'retail', 'industrial']);
    expect(urbanLanduseLayer?.paint?.['fill-color']).toBe('#e7cdbc');
    expect(urbanLanduseLayer?.paint?.['fill-outline-color']).toBe('rgba(163, 112, 84, 0.5)');
    expect(countZoomExpressions(urbanLanduseLayer?.paint?.['fill-opacity'])).toBe(1);
    expect(landcoverLayerIndex).toBeGreaterThanOrEqual(0);
    expect(urbanAreaLayerIndex).toBe(landcoverLayerIndex + 1);
    expect(urbanLanduseLayerIndex).toBe(landcoverLayerIndex + 2);
    expect(urbanLanduseLayerIndex).toBeLessThan(parkLayerIndex);
    expect(urbanAreaLayerIndex).toBeLessThan(hillshadeLayerIndex);
    expect(buildingLayer?.paint?.['fill-opacity']).toBe(0.64);
    expect(cityLayer?.layout?.['text-size']).toEqual(expect.arrayContaining(['interpolate', ['linear'], ['zoom']]));
    expect(JSON.stringify(cityLayer?.layout?.['text-size'])).toContain('23');
    expect(JSON.stringify(cityLayer?.layout?.['text-font'])).toContain('Noto Sans Medium');
    expect(cityLayer?.paint?.['text-halo-width']).toBe(1.6);
  });

  it('keeps local OSM Topo Contrast expressions compatible with MapLibre zoom expression rules', () => {
    const resolved = resolveConfiguredMapStyle({
      config: mapConfig(),
      theme: TOPO_CONTRAST_THEME,
      localTilesReady: true,
    });

    const style = resolved.style as TestVectorStyle;
    const invalidExpressions = style.layers.flatMap((layer) => {
      const scopedEntries = [
        ...Object.entries(layer.layout ?? {}).map(([property, value]) => [`layout.${property}`, value] as const),
        ...Object.entries(layer.paint ?? {}).map(([property, value]) => [`paint.${property}`, value] as const),
      ];

      return scopedEntries
        .filter(([, value]) => countZoomExpressions(value) > 1)
        .map(([property]) => `${layer.id}.${property}`);
    });

    expect(invalidExpressions).toEqual([]);
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
    expect(style.sources[HILLSHADE_DEM_SOURCE_ID]).toBeTruthy();
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
    expect(isRemoteRasterMapTheme(TOPO_CONTRAST_THEME)).toBe(false);
    expect(isRemoteRasterMapTheme('swisstopo')).toBe(false);
  });
});

function countZoomExpressions(value: unknown): number {
  if (!Array.isArray(value)) return 0;
  const [operator, , input] = value;
  const current =
    (operator === 'interpolate' || operator === 'step') && Array.isArray(input) && input[0] === 'zoom' ? 1 : 0;
  return current + value.reduce((count, item) => count + countZoomExpressions(item), 0);
}
