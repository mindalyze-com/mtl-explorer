import { TERRAIN_HILLSHADE_LAYER_ID } from '@/utils/mapStyle';
import {
  disableTerrainView,
  enableTerrainView,
  terrainSourceAvailable,
  TERRAIN_CAMERA_EASE_MS,
  TERRAIN_TARGET_PITCH,
} from '@/components/map/terrainMode';
import type { MapCameraState } from '@/components/map/mapRendererTypes';
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
      if (!this.map || !this.map.getLayer(TERRAIN_HILLSHADE_LAYER_ID)) return;
      const basemapNorm = this.basemapEnabled ? Math.max(0, Math.min(1, this.layerOpacities.basemap / 100)) : 0;
      const tracksNorm = Math.max(0, Math.min(1, this.layerOpacities.tracks / 100));
      const tracksFactor = 0.6 + 0.4 * tracksNorm; // 0.6 … 1.0

      const exaggeration = 0.55 * basemapNorm * tracksFactor;
      this.map.setPaintProperty(TERRAIN_HILLSHADE_LAYER_ID, 'hillshade-exaggeration', exaggeration);
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

    overlayCameraView() {
      if (!this.overlayMap) return null;
      const center = this.overlayMap.getCenter();
      const view: MapCameraState = {
        center: [center.lng, center.lat],
        zoom: this.overlayMap.getZoom(),
        bearing: this.overlayMap.getBearing(),
        pitch: this.overlayMap.getPitch(),
      };
      const roll = this.overlayMap.getRoll?.();
      if (Number.isFinite(roll)) view.roll = roll;
      return view;
    },

    resolveOverlayCenterElevation(center) {
      if (!this.overlayMap || !this.terrainEnabled) return null;
      // Mirror the overlay map's ACTUAL rendered camera centre elevation so the
      // base map projects the ground identically and draped tracks stay glued to
      // the basemap surface.
      //
      // The overlay map (which renders the tracks) freezes / clamps its centre
      // elevation while panning and while DEM tiles stream in, so getCenterElevation
      // reflects the height the overlay is rendering with *this frame*. Querying a
      // fresh terrain height instead (queryTerrainElevation) returns the settled
      // summit height even while the overlay is still frozen near 0 - that gap
      // placed the base camera tens-to-hundreds of metres above the overlay
      // camera, so tracks appeared to sink "underground" until the meshes
      // resettled. Mirroring the rendered value keeps both cameras in lockstep.
      const currentElevation = this.overlayMap.getCenterElevation?.();
      if (Number.isFinite(currentElevation)) return currentElevation;
      // Fallback for runtimes without getCenterElevation: best-effort terrain query.
      const queriedElevation = this.overlayMap.queryTerrainElevation?.(center ?? this.overlayMap.getCenter());
      return Number.isFinite(queriedElevation) ? queriedElevation : null;
    },

    syncBaseMapToCamera(view) {
      if (!this.map || !view || this._syncingView) return;
      const nextView = {
        ...view,
      };
      const elevation = this.resolveOverlayCenterElevation(nextView.center);
      if (elevation != null && Number.isFinite(elevation)) nextView.elevation = elevation;
      this._syncingView = true;
      try {
        this.map.jumpTo(nextView);
      } finally {
        this._syncingView = false;
      }
    },

    syncBaseMapToOverlay() {
      if (!this.map || !this.overlayMap || this._syncingView) return;
      this.syncBaseMapToCamera(this.overlayCameraView());
    },

    jumpOverlayCameraAndSyncBase(view) {
      if (!this.overlayMap || !view) return;
      this.overlayMap.jumpTo(view);
      this.syncBaseMapToCamera(view);
      this.map?.triggerRepaint?.();
      this.overlayMap?.triggerRepaint?.();
    },

    setBaseMapTerrainSync(enabled) {
      this.map?.setCenterClampedToGround?.(!enabled);
      if (!enabled) this.map?.setCenterElevation?.(0);
    },

    applyTerrainPreference({ animate = false } = {}) {
      if (!this.map || !this.overlayMap) return;
      if (!this.terrainEnabled) {
        this.setBaseMapTerrainSync(false);
        disableTerrainView(this.map);
        disableTerrainView(this.overlayMap, {
          animatePitch: animate,
        });
        this._terrainControl?.setActive(false);
        this.applyTracksVisibility();
        this.syncBaseMapToOverlay();
        return;
      }
      if (this.globeMode) {
        this.globeUserDisabled = true;
        this.globeMode = false;
        this.applyGlobeProjection();
      }
      const allowAddSource = this.mapConfig?.offline !== true;
      const hasOfflineSources =
        allowAddSource || (terrainSourceAvailable(this.map) && terrainSourceAvailable(this.overlayMap));
      if (!hasOfflineSources) {
        mapSettingsStore.setLayerEnabled('terrain', false);
        this.syncMapSettingsFromStore();
        this._terrainControl?.setActive(false);
        this.applyTracksVisibility();
        if (animate) {
          this.$toast?.add({
            severity: 'warn',
            summary: '3D terrain unavailable',
            detail: 'Remote elevation tiles are not available while offline.',
            life: 3500,
          });
        }
        return;
      }
      let baseEnabled = false;
      let overlayEnabled = false;
      try {
        baseEnabled = enableTerrainView(this.map, {
          allowAddSource,
          exaggeration: this.terrainExaggeration,
        });
        overlayEnabled = enableTerrainView(this.overlayMap, {
          allowAddSource,
          exaggeration: this.terrainExaggeration,
        });
      } catch (e) {
        console.warn('3D terrain could not be enabled:', e);
      }
      if (!baseEnabled || !overlayEnabled) {
        disableTerrainView(this.map);
        disableTerrainView(this.overlayMap);
        mapSettingsStore.setLayerEnabled('terrain', false);
        this.syncMapSettingsFromStore();
        this._terrainControl?.setActive(false);
        this.applyTracksVisibility();
        if (animate) {
          this.$toast?.add({
            severity: 'warn',
            summary: '3D terrain unavailable',
            detail: 'Elevation tiles could not be loaded for this map style.',
            life: 3500,
          });
        }
        return;
      }
      this._terrainControl?.setActive(true);
      this.setBaseMapTerrainSync(true);
      this.syncBaseMapToOverlay();
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
