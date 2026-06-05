import { DEFAULT_LAYER_OPACITIES, DEFAULT_MAP_THEME } from '@/stores/mapSettingsStore';
import { MAP_OVERLAYS } from '@/utils/mapStyle';
import { isRemoteRasterMapTheme } from '@/components/map/mapStyleResolver';
import { TRACK_COLOR } from '@/utils/trackColors';
import {
  colorForFilterGroup,
  compareLegendEntries,
  formatFilterGroupLabel,
  gradientBucketCount,
  shouldUseCompactGradientLegend,
} from '@/utils/filterMetadata';
import type { MapControllerMethodDefinitions, MapLayerSettingsMethods } from './mapControllerRuntime';
import type { useFilterStore } from '@/stores/filterStore';
import type { MapLayerId, useMapSettingsStore } from '@/stores/mapSettingsStore';

const TRACK_OPACITY_PAINT_PROPERTIES = [
  ['tracks-layer', 'line-opacity'],
  ['tracks-highlight-layer', 'line-opacity'],
  ['tracks-highlight-dash-layer', 'line-opacity'],
  ['tracks-dot-layer', 'circle-opacity'],
  ['tracks-dot-layer', 'circle-stroke-opacity'],
  ['tracks-overview-dots', 'circle-opacity'],
  ['tracks-overview-dots', 'circle-stroke-opacity'],
  ['tracks-highlight-circle-layer', 'circle-opacity'],
] as const;

export function useMapLayerSettings(deps: {
  filterStore: ReturnType<typeof useFilterStore>;
  mapSettingsStore: ReturnType<typeof useMapSettingsStore>;
}): MapControllerMethodDefinitions<MapLayerSettingsMethods> {
  const { filterStore, mapSettingsStore } = deps;
  const methods: MapControllerMethodDefinitions<MapLayerSettingsMethods> = {
    async resolveTrackLineColor() {
      const clientFilterConfig = await filterStore.ensureLoaded();
      const palette = clientFilterConfig?.palette;
      const filterInfo = clientFilterConfig?.filterInfo;
      const filterConfig = filterInfo?.filterConfig;
      const legendSortStrategy = clientFilterConfig?.legendSortStrategy;
      const hasPalette = Boolean(palette && !palette.isEmptyColorPalette());
      palette?.reset();
      if (!hasPalette) {
        this.filterActive = filterStore.isActive;
        this.legendEntries = [];
        this.legendMode = 'categorical';
        this.legendGradientColors = [];
        this.legendGradientBucketCount = 100;
        return TRACK_COLOR;
      }
      this.filterActive = true;
      const isGradientLegend = shouldUseCompactGradientLegend(filterConfig, palette, legendSortStrategy);
      this.legendMode = isGradientLegend ? 'gradient' : 'categorical';
      this.legendGradientColors = isGradientLegend ? [...(palette.pColors ?? [])] : [];
      this.legendGradientBucketCount = isGradientLegend ? gradientBucketCount(filterInfo) : 100;
      const groupCounts = new Map<string, number>();
      for (const feature of this.geojson?.features ?? []) {
        const group = typeof feature.properties?.filterGroup === 'string' ? feature.properties.filterGroup : null;
        if (!group) continue;
        groupCounts.set(group, (groupCounts.get(group) || 0) + 1);
      }
      const legendEntries = Array.from(groupCounts.entries()).map(([group, count]) => ({
        group,
        count,
      }));
      const sortedEntries = legendSortStrategy
        ? legendEntries.sort((a, b) => compareLegendEntries(a, b, filterConfig, legendSortStrategy))
        : this.orderLegendEntriesByFilterResult(
            legendEntries,
            (this.activeTrackFilterResult as { legendGroupOrder?: string[] } | null | undefined)?.legendGroupOrder
          );
      const colorMap = new Map();
      for (const entry of sortedEntries) {
        colorMap.set(entry.group, colorForFilterGroup(palette, entry.group, filterInfo));
      }
      this.colorPalette = palette;
      this.legendEntries = sortedEntries.map(({ group, count }) => ({
        group,
        label: formatFilterGroupLabel(group, filterInfo),
        color: colorMap.get(group) ?? TRACK_COLOR,
        count,
      }));
      if (colorMap.size === 0) return TRACK_COLOR;
      const matchExpr = ['match', ['get', 'filterGroup']];
      for (const [group, color] of colorMap) {
        matchExpr.push(group, color);
      }
      matchExpr.push(TRACK_COLOR);
      return matchExpr;
    },

    async updateTrackStyle() {
      if (!this.overlayMap || !this.geojson) return;
      const lineColor = await this.resolveTrackLineColor();
      if (this.overlayMap.getLayer('tracks-layer')) {
        this.overlayMap.setPaintProperty('tracks-layer', 'line-color', lineColor);
      }
      if (this.overlayMap.getLayer('tracks-dot-layer')) {
        this.overlayMap.setPaintProperty('tracks-dot-layer', 'circle-color', lineColor);
      }
      if (this.overlayMap.getLayer('tracks-overview-dots')) {
        this.overlayMap.setPaintProperty('tracks-overview-dots', 'circle-color', lineColor);
      }
    },

    orderLegendEntriesByFilterResult(entries, groupOrder) {
      if (!groupOrder?.length) return entries;
      const byGroup = new Map(entries.map((entry) => [entry.group, entry]));
      const ordered = [];
      const seen = new Set();
      for (const group of groupOrder) {
        const entry = byGroup.get(group);
        if (!entry || seen.has(group)) continue;
        ordered.push(entry);
        seen.add(group);
      }
      for (const entry of entries) {
        if (!seen.has(entry.group)) ordered.push(entry);
      }
      return ordered;
    },

    /**
     * Compute raster paint properties for a route overlay at a given slider value.
     * Mirrors the basemap's smart-fade approach:
     *   slider 100 → full color, full opacity
     *   slider  50 → desaturated, slightly washed-out, still fully opaque
     *   slider   0 → fully grey, brightened, nearly invisible
     */
    _overlayPaintForSlider(slider, hueRotate) {
      const dim = Math.max(0, Math.min(1, (100 - slider) / 100));
      return {
        'raster-opacity': 1 - dim * 0.85,
        // 1.0 → 0.15
        'raster-saturation': -dim,
        // 0   → -1 (grayscale)
        'raster-brightness-max': 1 - dim * 0.25,
        // 1.0 → 0.75
        ...(hueRotate !== undefined
          ? {
              'raster-hue-rotate': hueRotate,
            }
          : {}),
      };
    },

    /**
     * Return the id of the first existing overlay layer that route overlays
     * should render below, to keep them at the bottom of the overlay stack
     * (above transparent background, below heatmap/tracks/media).
     */
    _overlayBeforeId() {
      if (!this.overlayMap) return undefined;
      for (const id of [
        'heatmap-layer',
        'tracks-layer',
        'tracks-overview-dots',
        'media-clusters',
        'media-unclustered',
      ]) {
        if (this.overlayMap.getLayer(id)) return id;
      }
      return undefined;
    },

    /** Add all currently-active overlay layers to the overlay map. */
    applyActiveOverlays() {
      if (!this.overlayMap) return;
      const beforeId = this._overlayBeforeId();
      for (const overlay of MAP_OVERLAYS) {
        if (!this.activeOverlays.includes(overlay.id)) continue;
        if (!this.overlayMap.getSource(overlay.id)) {
          this.overlayMap.addSource(overlay.id, {
            type: 'raster',
            tiles: [overlay.url],
            tileSize: 256,
            attribution: overlay.attribution,
          });
        }
        if (!this.overlayMap.getLayer(`${overlay.id}-overlay`)) {
          this.overlayMap.addLayer(
            {
              id: `${overlay.id}-overlay`,
              type: 'raster',
              source: overlay.id,
              minzoom: 0,
              maxzoom: 22,
              paint: this._overlayPaintForSlider(
                this.layerOpacities[overlay.id] ?? 100,
                (overlay as typeof overlay & { hueRotate?: number }).hueRotate
              ),
            },
            beforeId
          );
        }
      }
    },

    /** Remove all overlay layers and sources from the overlay map. */
    removeAllOverlays() {
      if (!this.overlayMap) return;
      for (const overlay of MAP_OVERLAYS) {
        if (this.overlayMap.getLayer(`${overlay.id}-overlay`)) this.overlayMap.removeLayer(`${overlay.id}-overlay`);
        if (this.overlayMap.getSource(overlay.id)) this.overlayMap.removeSource(overlay.id);
      }
    },

    /** Reset all map settings to their defaults. */
    async onResetMapSettings() {
      if (this.mediaOverlay?.isVisible?.()) this.mediaOverlay.hide();
      if (this.heatmapOverlay?.isVisible?.()) this.heatmapOverlay.hide();
      this.removeAllOverlays();
      mapSettingsStore.reset();
      this.syncMapSettingsFromStore();
      this.showLoader = true;
      try {
        await this.initMap();
        await this.addTracksToMap();
      } finally {
        this.showLoader = false;
      }
    },

    /** Copy the Pinia-backed map settings into the controller's MapLibre runtime state. */
    syncMapSettingsFromStore() {
      this.mapThemeSelected = mapSettingsStore.theme;
      this.mapSourceMode = mapSettingsStore.mapSourceMode;
      this.basemapEnabled = mapSettingsStore.basemapEnabled;
      this.terrainEnabled = mapSettingsStore.terrainEnabled;
      this.terrainExaggeration = mapSettingsStore.terrainExaggeration;
      this.tracksEnabled = mapSettingsStore.tracksEnabled;
      this.mediaVisible = mapSettingsStore.mediaVisible;
      this.trackPointsVisible = mapSettingsStore.trackPointsVisible;
      this.heatmapVisible = mapSettingsStore.heatmapVisible;
      this.legendCollapsed = mapSettingsStore.legendCollapsed;
      this.activeOverlays = [...mapSettingsStore.activeOverlays];
      this.layerOpacities = {
        ...DEFAULT_LAYER_OPACITIES,
        ...mapSettingsStore.layerOpacities,
      } as typeof this.layerOpacities;
    },

    /** Unified handler for toggling any layer on/off. */
    async onToggleLayer(layerId) {
      switch (layerId) {
        case 'basemap':
          mapSettingsStore.toggleLayer('basemap');
          this.syncMapSettingsFromStore();
          this.applyLayerOpacity('basemap');
          break;
        case 'terrain':
          this.onToggleTerrainMode();
          break;
        case 'tracks':
          mapSettingsStore.toggleLayer('tracks');
          this.syncMapSettingsFromStore();
          this.applyTracksVisibility();
          break;
        case 'media':
          await this.onToggleMediaLayer();
          break;
        case 'trackpoints':
          this.onToggleTrackPoints();
          break;
        case 'heatmap':
          this.onToggleHeatmapLayer();
          break;
        case 'wanderland':
        case 'veloland':
        case 'mountainbikeland':
        case 'wanderwege':
        case 'wmt-hiking':
        case 'wmt-cycling':
        case 'wmt-mtb':
          this.onToggleOverlay(layerId);
          break;
      }
    },

    /** Unified handler for changing layer opacity. */
    onLayerOpacityChange(layerId, value) {
      mapSettingsStore.setLayerOpacity(layerId, value);
      this.syncMapSettingsFromStore();
      this.applyLayerOpacity(layerId);
    },

    /** Apply opacity for a specific layer to the map. */
    applyLayerOpacity(layerId) {
      const opacity = this.layerOpacities[layerId] / 100;
      switch (layerId) {
        case 'basemap':
          // CSS filter handles visual dimming via `baseMapStyle` computed.
          // Hillshade also depends on basemap dim → update it.
          this._applyHillshade();
          break;
        case 'terrain':
          break;
        case 'tracks':
          if (this.overlayMap) {
            for (const [layerName, propertyName] of TRACK_OPACITY_PAINT_PROPERTIES) {
              if (this.overlayMap.getLayer(layerName)) {
                this.overlayMap.setPaintProperty(layerName, propertyName, opacity);
              }
            }
          }
          // Hillshade also depends on tracks opacity → update it.
          this._applyHillshade();
          break;
        case 'media':
          if (this.overlayMap) {
            if (this.overlayMap.getLayer('media-clusters'))
              this.overlayMap.setPaintProperty('media-clusters', 'circle-opacity', opacity);
            if (this.overlayMap.getLayer('media-cluster-count'))
              this.overlayMap.setPaintProperty('media-cluster-count', 'text-opacity', opacity);
            if (this.overlayMap.getLayer('media-unclustered'))
              this.overlayMap.setPaintProperty('media-unclustered', 'circle-opacity', opacity);
          }
          break;
        case 'trackpoints':
          if (this.overlayMap && this.overlayMap.getLayer('track-points-layer'))
            this.overlayMap.setPaintProperty('track-points-layer', 'icon-opacity', opacity);
          break;
        case 'heatmap':
          if (this.overlayMap && this.overlayMap.getLayer('heatmap-layer'))
            this.overlayMap.setPaintProperty('heatmap-layer', 'heatmap-opacity', opacity * 0.92);
          break;
        case 'wanderland':
        case 'veloland':
        case 'mountainbikeland':
        case 'wanderwege':
        case 'wmt-hiking':
        case 'wmt-cycling':
        case 'wmt-mtb': {
          const layerName = `${layerId}-overlay`;
          if (this.overlayMap && this.overlayMap.getLayer(layerName)) {
            const overlay = MAP_OVERLAYS.find((o) => o.id === layerId);
            const paint = this._overlayPaintForSlider(
              this.layerOpacities[layerId],
              (overlay as (typeof overlay & { hueRotate?: number }) | undefined)?.hueRotate
            );
            for (const [prop, val] of Object.entries(paint)) {
              this.overlayMap.setPaintProperty(layerName, prop, val);
            }
          }
          break;
        }
      }
    },

    /** Apply all current layer opacities after map init or reload. */
    applyAllLayerOpacities() {
      for (const layerId of Object.keys(this.layerOpacities)) {
        this.applyLayerOpacity(layerId);
      }
    },

    /** Toggle a Swiss Mobility overlay on/off without reinitialising the map. */
    onToggleOverlay(overlayId) {
      const idx = this.activeOverlays.indexOf(overlayId);
      if (idx === -1) {
        const overlay = MAP_OVERLAYS.find((o) => o.id === overlayId);
        if (overlay && this.overlayMap) {
          if (!this.overlayMap.getSource(overlay.id)) {
            this.overlayMap.addSource(overlay.id, {
              type: 'raster',
              tiles: [overlay.url],
              tileSize: 256,
              attribution: overlay.attribution,
            });
          }
          if (!this.overlayMap.getLayer(`${overlay.id}-overlay`)) {
            this.overlayMap.addLayer(
              {
                id: `${overlay.id}-overlay`,
                type: 'raster',
                source: overlay.id,
                minzoom: 0,
                maxzoom: 22,
                paint: this._overlayPaintForSlider(
                  this.layerOpacities[overlayId],
                  (overlay as typeof overlay & { hueRotate?: number }).hueRotate
                ),
              },
              this._overlayBeforeId()
            );
          }
        }
        mapSettingsStore.setLayerEnabled(overlayId as MapLayerId, true);
      } else {
        if (this.overlayMap) {
          if (this.overlayMap.getLayer(`${overlayId}-overlay`)) this.overlayMap.removeLayer(`${overlayId}-overlay`);
          if (this.overlayMap.getSource(overlayId)) this.overlayMap.removeSource(overlayId);
        }
        mapSettingsStore.setLayerEnabled(overlayId as MapLayerId, false);
      }
      this.syncMapSettingsFromStore();
    },

    async onMapThemeChangeEvent(themeCode) {
      if (themeCode && typeof themeCode === 'string') {
        if (this.mapSourceMode === 'remote' && !isRemoteRasterMapTheme(themeCode)) return;
        mapSettingsStore.setTheme(themeCode);
        this.syncMapSettingsFromStore();
      }
      this.showLoader = true;
      try {
        await this.initMap();
        await this.addTracksToMap();
      } finally {
        this.showLoader = false;
      }
    },

    async onMapSourceModeChangeEvent(sourceMode) {
      const nextMode = sourceMode === 'remote' ? 'remote' : 'auto';
      mapSettingsStore.setMapSourceMode(nextMode);
      if (nextMode === 'remote' && !isRemoteRasterMapTheme(mapSettingsStore.theme)) {
        mapSettingsStore.setTheme(DEFAULT_MAP_THEME);
      }
      this.syncMapSettingsFromStore();
      this.showLoader = true;
      try {
        await this.initMap();
        await this.addTracksToMap();
      } finally {
        this.showLoader = false;
      }
    },

    onToggleTrackPoints() {
      mapSettingsStore.toggleLayer('trackpoints');
      this.syncMapSettingsFromStore();
      this.updateTrackPointsSource();
    },

    onLegendCollapsed(val) {
      mapSettingsStore.setLegendCollapsed(val);
      this.syncMapSettingsFromStore();
    },

    onHiddenGroupsChanged(groups) {
      this.hiddenGroups = groups instanceof Set ? groups : new Set(groups);
      this.applyGroupFilter();
    },

    /** Apply a Mapbox filter to hide deactivated legend groups on all track layers. */
    applyGroupFilter() {
      const hidden = this.hiddenGroups;
      this.applyTrackRenderFilters();
      // Update visible track count to reflect hidden groups
      if (this.geojson) {
        const total = this.geojson.features.length;
        if (hidden.size > 0) {
          this.visibleTrackCount = this.geojson.features.filter(
            (f) => !hidden.has(String(f.properties?.filterGroup))
          ).length;
        } else {
          this.visibleTrackCount = total;
        }
      }
    },
  };
  return methods;
}
