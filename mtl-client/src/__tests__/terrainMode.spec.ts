import { describe, expect, it, vi } from 'vitest';
import {
  disableTerrainView,
  enableTerrainView,
  ensureTerrainSource,
  sanitizeTerrainExaggeration,
  TERRAIN_CAMERA_EASE_MS,
  TERRAIN_DISABLE_CAMERA_EASE_MS,
  TERRAIN_EXAGGERATION,
  TERRAIN_EXAGGERATION_DEFAULT,
  TERRAIN_EXAGGERATION_MAX,
  TERRAIN_EXAGGERATION_MIN,
  TERRAIN_FLAT_PITCH,
  TERRAIN_MAX_PITCH,
  TERRAIN_TARGET_PITCH,
  terrainSourceAvailable,
  type TerrainCapableMap,
} from '@/components/map/terrainMode';
import { MAPTERHORN_TERRAIN_TILEJSON_URL, TERRAIN_DEM_SOURCE_ID } from '@/utils/mapStyle';

function createMapMock(hasSource = false): TerrainCapableMap & {
  addSource: ReturnType<typeof vi.fn>;
  easeTo: ReturnType<typeof vi.fn>;
  setMaxPitch: ReturnType<typeof vi.fn>;
  setTerrain: ReturnType<typeof vi.fn>;
} {
  const sources = new Set<string>(hasSource ? [TERRAIN_DEM_SOURCE_ID] : []);
  return {
    addSource: vi.fn((id: string) => {
      sources.add(id);
    }),
    easeTo: vi.fn(),
    getSource: vi.fn((id: string) => (sources.has(id) ? { id } : undefined)),
    setMaxPitch: vi.fn(),
    setTerrain: vi.fn(),
  };
}

describe('terrainMode', () => {
  it('adds the shared Mapterhorn DEM source only when missing', () => {
    const map = createMapMock();

    expect(terrainSourceAvailable(map)).toBe(false);
    expect(ensureTerrainSource(map)).toBe(true);
    expect(terrainSourceAvailable(map)).toBe(true);
    expect(map.addSource).toHaveBeenCalledTimes(1);
    expect(map.addSource).toHaveBeenCalledWith(
      TERRAIN_DEM_SOURCE_ID,
      expect.objectContaining({
        type: 'raster-dem',
        url: MAPTERHORN_TERRAIN_TILEJSON_URL,
        encoding: 'terrarium',
        maxzoom: 15,
      })
    );

    expect(ensureTerrainSource(map)).toBe(true);
    expect(map.addSource).toHaveBeenCalledTimes(1);
  });

  it('does not add a remote DEM source when source creation is disallowed', () => {
    const map = createMapMock();

    expect(ensureTerrainSource(map, false)).toBe(false);
    expect(enableTerrainView(map, { allowAddSource: false })).toBe(false);
    expect(map.addSource).not.toHaveBeenCalled();
    expect(map.setTerrain).not.toHaveBeenCalled();
  });

  it('enables terrain, raises max pitch, and can ease the camera into 3D', () => {
    const map = createMapMock(true);

    expect(enableTerrainView(map, { animatePitch: true, exaggeration: 1.4 })).toBe(true);
    expect(map.setMaxPitch).toHaveBeenCalledWith(TERRAIN_MAX_PITCH);
    expect(map.setTerrain).toHaveBeenCalledWith({
      source: TERRAIN_DEM_SOURCE_ID,
      exaggeration: 1.4,
    });
    expect(map.easeTo).toHaveBeenCalledWith({
      pitch: TERRAIN_TARGET_PITCH,
      duration: TERRAIN_CAMERA_EASE_MS,
    });
  });

  it('disables terrain and restores a flat camera constraint', () => {
    const map = createMapMock(true);

    disableTerrainView(map, { animatePitch: true });

    expect(map.setTerrain).toHaveBeenCalledWith(null);
    expect(map.setMaxPitch).toHaveBeenCalledWith(TERRAIN_FLAT_PITCH);
    expect(map.easeTo).toHaveBeenCalledWith({
      pitch: TERRAIN_FLAT_PITCH,
      duration: TERRAIN_DISABLE_CAMERA_EASE_MS,
    });
  });

  it('sanitizes terrain exaggeration to the supported slider range', () => {
    expect(sanitizeTerrainExaggeration(undefined)).toBe(TERRAIN_EXAGGERATION_DEFAULT);
    expect(sanitizeTerrainExaggeration(99)).toBe(TERRAIN_EXAGGERATION_MAX);
    expect(sanitizeTerrainExaggeration(0.1)).toBe(TERRAIN_EXAGGERATION_MIN);
    expect(sanitizeTerrainExaggeration(1.34)).toBe(1.3);
    expect(TERRAIN_EXAGGERATION).toBe(TERRAIN_EXAGGERATION_DEFAULT);
  });
});
