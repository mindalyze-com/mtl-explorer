import { describe, expect, it } from 'vitest';
import { resolveConfiguredMapStyle } from '@/components/map/mapStyleResolver';
import { MapConfigDtoTileModeEnum, type MapConfig } from '@/utils/mapConfigService';

function mapConfig(overrides: Partial<MapConfig> = {}): MapConfig {
  return {
    tileMode: MapConfigDtoTileModeEnum.Local,
    tileBaseUrl: '/mtl/api/map-proxy/prod',
    tilesetName: 'planet',
    lowzoomTilesetName: 'world-lowzoom',
    remoteTileUrl: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
    ...overrides,
  };
}

describe('resolveConfiguredMapStyle', () => {
  it('uses a built-in raster fallback when the configured remote URL is missing', () => {
    const resolved = resolveConfiguredMapStyle({
      config: mapConfig({ remoteTileUrl: undefined as unknown as string }),
      theme: 'light',
      localTilesReady: false,
    });

    expect(resolved.styleMode).toBe('fallback-raster');
    expect(JSON.stringify(resolved.style)).toContain('https://tile.openstreetmap.org/{z}/{x}/{y}.png');
  });
});
