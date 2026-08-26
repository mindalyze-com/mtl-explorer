import { TERRAIN_HILLSHADE_LAYER_ID } from '@/utils/mapStyle';
import {
  disableTerrainView,
  enableTerrainView,
  terrainSourceAvailable,
  TERRAIN_CAMERA_EASE_MS,
  TERRAIN_TARGET_PITCH,
} from '@/components/map/terrainMode';
import type { MapControllerMethodDefinitions, TerrainModeMethods } from './mapControllerRuntime';

export function useTerrainMode(deps: {
  mapSettingsStore: {
    terrainEnabled: boolean;
    setTerrainEnabled: (enabled: boolean) => void;
    setTerrainExaggeration: (exaggeration: number) => void;
    setLayerEnabled: (layerId: 'terrain', enabled: boolean) => void;
  };
}): MapControllerMethodDefinitions<TerrainModeMethods> {
  const { mapSettingsStore } = deps;
  const methods: MapControllerMethodDefinitions<TerrainModeMethods> = {
    /**
     * Update terrain hillshade-exaggeration based on the basemap and tracks sliders.
     *
     * Hillshade is part of the basemap visual layer, so it should fade together
     * with the basemap slider. We also let the tracks slider dampen it slightly
     * so the terrain is less prominent when tracks are hidden.
     *
     *   basemap 100 % → 1.00 × base        (full relief)
     *   basemap   0 % → 0.00 × base        (no relief)
     *   tracks  100 % → ×  1.00            (no damping)
     *   tracks    0 % → ×  0.60            (extra soft)
     *
     * Base exaggeration is 0.55 at slider 100 %.
     */
    _applyHillshade() {
      if (!this.overlayMap || !this.overlayMap.getLayer(TERRAIN_HILLSHADE_LAYER_ID)) return;
      const basemapNorm = this.basemapEnabled ? Math.max(0, Math.min(1, this.layerOpacities.basemap / 100)) : 0;
      const tracksNorm = Math.max(0, Math.min(1, this.layerOpacities.tracks / 100));
      const tracksFactor = 0.6 + 0.4 * tracksNorm; // 0.6 … 1.0

      const exaggeration = 0.55 * basemapNorm * tracksFactor;
      this.overlayMap.setPaintProperty(TERRAIN_HILLSHADE_LAYER_ID, 'hillshade-exaggeration', exaggeration);
    },

    setTerrainModeEnabled(enabled, { animate = false } = {}) {
      const nextEnabled = Boolean(enabled);
      if (this.terrainEnabled === nextEnabled && mapSettingsStore.terrainEnabled === nextEnabled) {
        this._terrainControl?.setActive(nextEnabled);
        return;
      }
      mapSettingsStore.setTerrainEnabled(nextEnabled);
      this.syncMapSettingsFromStore();
      this.applyTerrainPreference({
        animate: nextEnabled && animate,
      });
    },

    toggleTerrainMode({ animate = true } = {}) {
      this.setTerrainModeEnabled(!this.terrainEnabled, {
        animate,
      });
    },

    onToggleTerrainMode() {
      this.toggleTerrainMode({
        animate: true,
      });
    },

    onSetTerrainModeEnabled(enabled) {
      this.setTerrainModeEnabled(enabled, {
        animate: true,
      });
    },

    onTerrainExaggerationChange(exaggeration) {
      mapSettingsStore.setTerrainExaggeration(exaggeration);
      this.syncMapSettingsFromStore();
      if (!this.terrainEnabled) return;
      this.applyTerrainPreference({
        animate: false,
      });
    },

    jumpMapCamera(view) {
      if (!this.overlayMap || !view) return;
      this.overlayMap.jumpTo(view);
      this.overlayMap.triggerRepaint();
    },

    handleTerrainUnavailable(detail, notify) {
      mapSettingsStore.setLayerEnabled('terrain', false);
      this.syncMapSettingsFromStore();
      this._terrainControl?.setActive(false);
      this.applyTracksVisibility();
      if (notify) {
        this.$toast?.add({
          severity: 'warn',
          summary: '3D terrain unavailable',
          detail,
          life: 3500,
        });
      }
    },

    applyTerrainPreference({ animate = false } = {}) {
      if (!this.overlayMap) return;
      if (!this.terrainEnabled) {
        disableTerrainView(this.overlayMap, {
          animatePitch: animate,
        });
        this._terrainControl?.setActive(false);
        this.applyTracksVisibility();
        return;
      }
      if (this.globeMode) {
        this.globeUserDisabled = true;
        this.globeMode = false;
        this.applyGlobeProjection();
      }
      const allowAddSource = this.mapConfig?.offline !== true;
      const hasOfflineSources = allowAddSource || terrainSourceAvailable(this.overlayMap);
      if (!hasOfflineSources) {
        this.handleTerrainUnavailable('Remote elevation tiles are not available while offline.', animate);
        return;
      }
      let enabled = false;
      try {
        enabled = enableTerrainView(this.overlayMap, {
          allowAddSource,
          exaggeration: this.terrainExaggeration,
        });
      } catch (e) {
        console.warn('3D terrain could not be enabled:', e);
      }
      if (!enabled) {
        disableTerrainView(this.overlayMap);
        this.handleTerrainUnavailable('Elevation tiles could not be loaded for this map style.', animate);
        return;
      }
      this._terrainControl?.setActive(true);
      this.applyTracksVisibility();
      if (animate) {
        this.overlayMap.easeTo({
          pitch: TERRAIN_TARGET_PITCH,
          duration: TERRAIN_CAMERA_EASE_MS,
        });
      }
    },
  };
  return methods;
}
