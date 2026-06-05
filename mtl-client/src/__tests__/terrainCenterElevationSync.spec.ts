import { describe, expect, it, vi } from 'vitest';
import { useTerrainMode } from '@/components/map/composables/useTerrainMode';

function resolver() {
  const methods = useTerrainMode({ mapSettingsStore: {} });
  return methods.resolveOverlayCenterElevation as (this: unknown, center?: unknown) => number | null;
}

describe('resolveOverlayCenterElevation', () => {
  it('returns null when terrain is disabled', () => {
    const ctx = {
      terrainEnabled: false,
      overlayMap: { getCenterElevation: () => 235, queryTerrainElevation: () => 235 },
    };
    expect(resolver().call(ctx)).toBeNull();
  });

  it('mirrors the overlay rendered centre elevation instead of querying fresh terrain', () => {
    // Overlay is mid-pan: its camera elevation is frozen near 0 while the DEM
    // reports a 235 m summit. Mirroring the rendered 0 keeps the base camera in
    // lockstep so draped tracks never sink "underground".
    const queryTerrainElevation = vi.fn(() => 235);
    const ctx = {
      terrainEnabled: true,
      overlayMap: {
        getCenterElevation: () => 0,
        queryTerrainElevation,
        getCenter: () => ({ lng: -8.3, lat: 41.2 }),
      },
    };
    expect(resolver().call(ctx)).toBe(0);
    expect(queryTerrainElevation).not.toHaveBeenCalled();
  });

  it('mirrors the settled overlay elevation once the camera clamps to terrain', () => {
    const ctx = {
      terrainEnabled: true,
      overlayMap: {
        getCenterElevation: () => 235,
        queryTerrainElevation: () => 235,
        getCenter: () => ({ lng: -8.3, lat: 41.2 }),
      },
    };
    expect(resolver().call(ctx)).toBe(235);
  });

  it('falls back to a terrain query when getCenterElevation is unavailable', () => {
    const ctx = {
      terrainEnabled: true,
      overlayMap: {
        getCenterElevation: () => undefined,
        queryTerrainElevation: () => 180,
        getCenter: () => ({ lng: -8.3, lat: 41.2 }),
      },
    };
    expect(resolver().call(ctx)).toBe(180);
  });
});
