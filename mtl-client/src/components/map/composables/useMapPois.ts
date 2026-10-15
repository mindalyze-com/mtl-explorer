import { warnSanitized } from '@/utils/safeLogging';
import { watch } from 'vue';
import type { Map as MapLibreMap } from 'maplibre-gl';
import { useMapSettingsStore } from '@/stores/mapSettingsStore';
import { mainTileArchiveUrl, MapConfigDtoTileModeEnum, type MapConfig } from '@/utils/mapConfigService';
import { PROTOMAPS_GLYPHS_URL } from '@/utils/mapStyle';
import { mapLabelFonts } from '@/utils/mapLabelFonts';
import { buildPoiLayers, POI_LAYER_PREFIX, POI_SOURCE_ID } from '../poiLayers';
import { poiCategoryForKind } from '../poiCategories';
import { installPoiImages } from '../poiImages';
import type { Coordinates, MapPoint } from './mapControllerRuntime';

const BASEMAP_SOURCE = 'protomaps';
const BASEMAP_POI_LAYER = 'pois';
const POI_COORDINATE_ID_PRECISION = 6;

export type PoiMapCandidate = {
  id: string;
  kind: 'poi';
  sourceId: string;
  label: string;
  detail: string;
  coordinates: Coordinates;
  distancePx: number;
  payload: { kind: string; elevation?: string };
};

export type MapPoiProvider = {
  collectCandidates(point: MapPoint, radius: number): PoiMapCandidate[];
};

/** One POI overlay for the main map, shared with planning and restored after style changes. */
export function useMapPois(
  mapValue: () => MapLibreMap | undefined,
  configValue: () => MapConfig | null,
  canSelect: () => boolean
): MapPoiProvider {
  const settings = useMapSettingsStore();
  let activeMap: MapLibreMap | undefined;
  watch(
    mapValue,
    (map, _previous, onCleanup) => {
      if (!map) return;
      activeMap = map;
      let disposed = false;
      let generation = 0;
      const ownLayers = () => map.getStyle()?.layers?.filter((layer) => layer.id.startsWith(POI_LAYER_PREFIX)) ?? [];

      const applySettings = () => {
        for (const layer of ownLayers()) {
          const id = layer.id.slice(POI_LAYER_PREFIX.length);
          map.setLayoutProperty(
            layer.id,
            'visibility',
            settings.poiEnabled && settings.poiCategories[id] !== false ? 'visible' : 'none'
          );
          map.setPaintProperty(layer.id, 'icon-opacity', settings.poiOpacity / 100);
          map.setPaintProperty(layer.id, 'text-opacity', settings.poiOpacity / 100);
        }
      };

      const install = async () => {
        const currentGeneration = ++generation;
        try {
          const config = configValue();
          const hasBaseSource = Boolean(map.getSource(BASEMAP_SOURCE));
          // Raster fallback without a usable archive remains a working map.
          if (
            !hasBaseSource &&
            (!config || config.tileMode !== MapConfigDtoTileModeEnum.Local || config.configLoadFailed)
          )
            return;
          const source = hasBaseSource ? BASEMAP_SOURCE : POI_SOURCE_ID;
          if (!(await installPoiImages(map, () => !disposed && generation === currentGeneration))) return;
          if (!map.getSource(source) && config) {
            map.addSource(source, {
              type: 'vector',
              url: `pmtiles://${mainTileArchiveUrl(config)}`,
              attribution: '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
            });
          }
          if (!map.getGlyphs()) map.setGlyphs(PROTOMAPS_GLYPHS_URL);
          if (hasBaseSource && map.getLayer(BASEMAP_POI_LAYER))
            map.setLayoutProperty(BASEMAP_POI_LAYER, 'visibility', 'none');
          const before = map
            .getStyle()
            .layers?.find((layer) => ['tracks-layer', 'heatmap-layer', 'media-clusters'].includes(layer.id))?.id;
          // Earlier categories receive collision priority, since later style layers are placed first.
          for (const layer of buildPoiLayers(
            source,
            settings.poiEnabled,
            settings.poiCategories,
            settings.poiOpacity,
            settings.theme === 'dark',
            mapLabelFonts(map.getGlyphs())
          ).reverse()) {
            if (!map.getLayer(layer.id)) map.addLayer(layer, before);
          }
          applySettings();
        } catch (error) {
          if (!disposed)
            warnSanitized('Could not prepare points of interest on the map', error, {
              topic1: 'map',
              topic2: 'useMapPois',
              topic3: 'install',
            });
        }
      };

      map.on('style.load', install);
      if (map.isStyleLoaded()) void install();
      const stopSettings = watch(
        () => [settings.poiEnabled, settings.poiCategories, settings.poiOpacity],
        applySettings,
        { deep: true }
      );
      onCleanup(() => {
        disposed = true;
        if (activeMap === map) activeMap = undefined;
        stopSettings();
        map.off('style.load', install);
      });
    },
    { flush: 'sync', immediate: true }
  );

  return {
    collectCandidates(point, radius) {
      const map = activeMap;
      if (!map || map !== mapValue() || !canSelect() || !settings.poiEnabled || settings.poiOpacity <= 0) return [];
      const zoom = map.getZoom();
      const ids = (map.getStyle()?.layers ?? [])
        .filter(
          (layer) =>
            layer.id.startsWith(POI_LAYER_PREFIX) &&
            settings.poiCategories[layer.id.slice(POI_LAYER_PREFIX.length)] !== false &&
            layer.layout?.visibility !== 'none' &&
            zoom >= (layer.minzoom ?? 0) &&
            (layer.maxzoom == null || zoom < layer.maxzoom)
        )
        .map((layer) => layer.id);
      if (!ids.length) return [];
      const candidates = new Map<string, PoiMapCandidate>();
      for (const feature of map.queryRenderedFeatures(
        [
          [point.x - radius, point.y - radius],
          [point.x + radius, point.y + radius],
        ],
        { layers: ids }
      )) {
        if (feature.geometry.type !== 'Point') continue;
        const coordinates = feature.geometry.coordinates.slice(0, 2) as Coordinates;
        if (coordinates.length !== 2 || !coordinates.every(Number.isFinite)) continue;
        const kind = String(feature.properties.kind ?? '');
        const category = poiCategoryForKind(kind);
        if (!category || settings.poiCategories[category.id] === false || zoom < category.minZoom) continue;
        const label = String(feature.properties.name || category.label);
        const sourceId = feature.source;
        const sourceLayer = feature.sourceLayer ?? 'pois';
        const featureIdentity = feature.id ?? feature.properties.osm_id;
        const longitude = ((((coordinates[0] + 180) % 360) + 360) % 360) - 180;
        const fallbackIdentity = JSON.stringify([
          longitude.toFixed(POI_COORDINATE_ID_PRECISION),
          coordinates[1].toFixed(POI_COORDINATE_ID_PRECISION),
          kind,
          label,
        ]);
        const id = `poi:${sourceId}:${sourceLayer}:${featureIdentity ?? fallbackIdentity}`;
        const projected = map.project(coordinates);
        const distancePx = Math.hypot(projected.x - point.x, projected.y - point.y);
        const previous = candidates.get(id);
        if (previous && previous.distancePx <= distancePx) continue;
        const elevation = feature.properties.elevation == null ? undefined : String(feature.properties.elevation);
        candidates.set(id, {
          id,
          kind: 'poi',
          sourceId,
          label,
          detail: kind.replaceAll('_', ' ').replaceAll(';', ' · '),
          coordinates,
          distancePx,
          payload: { kind, elevation },
        });
      }
      return [...candidates.values()].sort((a, b) => a.distancePx - b.distancePx || a.id.localeCompare(b.id));
    },
  };
}
