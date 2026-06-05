import type { MapControllerMethodDefinitions, MediaAndHeatmapMethods } from './mapControllerRuntime';

export function useMediaAndHeatmap(deps: {
  mapSettingsStore: { setLayerEnabled: (layerId: 'media' | 'heatmap', enabled: boolean) => void };
}): MapControllerMethodDefinitions<MediaAndHeatmapMethods> {
  const { mapSettingsStore } = deps;
  const methods: MapControllerMethodDefinitions<MediaAndHeatmapMethods> = {
    async onToggleMediaLayer() {
      if (!this.mediaOverlay || this.mediaBusy) return;
      this.mediaBusy = true;
      try {
        if (this.mediaOverlay.isVisible()) {
          this.mediaOverlay.hide();
          mapSettingsStore.setLayerEnabled('media', false);
        } else {
          await this.mediaOverlay.show();
          mapSettingsStore.setLayerEnabled('media', true);
        }
        this.syncMapSettingsFromStore();
      } finally {
        this.mediaBusy = false;
      }
    },

    onToggleHeatmapLayer() {
      if (!this.heatmapOverlay || !this.geojson) return;
      if (this.heatmapOverlay.isVisible()) {
        this.heatmapOverlay.hide();
        mapSettingsStore.setLayerEnabled('heatmap', false);
      } else {
        this.heatmapOverlay.show(this.geojson);
        this.applyLayerOpacity('heatmap');
        mapSettingsStore.setLayerEnabled('heatmap', true);
      }
      this.syncMapSettingsFromStore();
    },

    navigateMediaTo(id) {
      if (id != null) this.mediaSheetMediaId = id;
    },

    closeMediaSheet() {
      this.mediaSheetMediaId = null;
      this.mediaNavList = [];
    },

    _buildMediaNavList(originId) {
      if (!this.mediaLoadedPoints.length) {
        this.mediaNavList = [];
        return;
      }
      // Limit navigation to photos within the current visible viewport
      const viewBounds = this.overlayMap?.getBounds();
      const visiblePoints = viewBounds
        ? this.mediaLoadedPoints.filter(
            (p) =>
              p.lat >= viewBounds.getSouth() &&
              p.lat <= viewBounds.getNorth() &&
              p.lng >= viewBounds.getWest() &&
              p.lng <= viewBounds.getEast()
          )
        : [...this.mediaLoadedPoints];
      const origin = visiblePoints.find((p) => p.id === originId) ?? visiblePoints[0];
      if (!origin) {
        this.mediaNavList = visiblePoints;
        return;
      }
      const dist = (p: (typeof visiblePoints)[number]) => {
        const dlat = p.lat - origin.lat,
          dlng = p.lng - origin.lng;
        return dlat * dlat + dlng * dlng;
      };
      this.mediaNavList = visiblePoints.sort((a, b) => dist(a) - dist(b));
    },
  };
  return methods;
}
