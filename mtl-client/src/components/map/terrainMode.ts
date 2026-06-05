import type { RasterDEMSourceSpecification, TerrainSpecification } from 'maplibre-gl';
import { createTerrainDemSource, TERRAIN_DEM_SOURCE_ID } from '@/utils/mapStyle';

export const TERRAIN_EXAGGERATION = 1;
export const TERRAIN_EXAGGERATION_DEFAULT = TERRAIN_EXAGGERATION;
export const TERRAIN_EXAGGERATION_MIN = 0.5;
export const TERRAIN_EXAGGERATION_MAX = 2;
export const TERRAIN_EXAGGERATION_STEP = 0.1;
export const TERRAIN_MAX_PITCH = 85;
export const TERRAIN_TARGET_PITCH = 65;
export const TERRAIN_CAMERA_EASE_MS = 650;
export const TERRAIN_FLAT_PITCH = 0;
export const TERRAIN_DISABLE_CAMERA_EASE_MS = 450;

export type TerrainCapableMap = {
  addSource: (id: string, source: RasterDEMSourceSpecification) => unknown;
  easeTo?: (options: { duration?: number; pitch?: number }) => unknown;
  getSource: (id: string) => unknown;
  setMaxPitch?: (pitch: number) => unknown;
  setTerrain: (options: TerrainSpecification | null) => unknown;
};

export type EnableTerrainOptions = {
  allowAddSource?: boolean;
  animatePitch?: boolean;
  durationMs?: number;
  exaggeration?: number;
  targetPitch?: number;
};

export type DisableTerrainOptions = {
  animatePitch?: boolean;
  durationMs?: number;
};

export function terrainSourceAvailable(map: TerrainCapableMap | null | undefined): boolean {
  return !!map?.getSource(TERRAIN_DEM_SOURCE_ID);
}

export function ensureTerrainSource(map: TerrainCapableMap, allowAddSource = true): boolean {
  if (terrainSourceAvailable(map)) return true;
  if (!allowAddSource) return false;

  map.addSource(TERRAIN_DEM_SOURCE_ID, createTerrainDemSource());
  return true;
}

export function enableTerrainView(map: TerrainCapableMap, options: EnableTerrainOptions = {}): boolean {
  if (!ensureTerrainSource(map, options.allowAddSource ?? true)) return false;

  map.setMaxPitch?.(TERRAIN_MAX_PITCH);
  map.setTerrain({
    source: TERRAIN_DEM_SOURCE_ID,
    exaggeration: sanitizeTerrainExaggeration(options.exaggeration),
  });

  if (options.animatePitch) {
    map.easeTo?.({
      pitch: options.targetPitch ?? TERRAIN_TARGET_PITCH,
      duration: options.durationMs ?? TERRAIN_CAMERA_EASE_MS,
    });
  }

  return true;
}

export function disableTerrainView(
  map: TerrainCapableMap | null | undefined,
  options: DisableTerrainOptions = {}
): void {
  if (!map) return;

  map.setTerrain(null);
  if (options.animatePitch) {
    map.easeTo?.({
      pitch: TERRAIN_FLAT_PITCH,
      duration: options.durationMs ?? TERRAIN_DISABLE_CAMERA_EASE_MS,
    });
  }
  map.setMaxPitch?.(TERRAIN_FLAT_PITCH);
}

export function sanitizeTerrainExaggeration(value: unknown): number {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) return TERRAIN_EXAGGERATION_DEFAULT;

  const clamped = Math.max(TERRAIN_EXAGGERATION_MIN, Math.min(TERRAIN_EXAGGERATION_MAX, numeric));
  const stepped = Math.round(clamped / TERRAIN_EXAGGERATION_STEP) * TERRAIN_EXAGGERATION_STEP;
  return Number(stepped.toFixed(2));
}
