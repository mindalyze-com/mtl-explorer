import type { ExpressionSpecification, SymbolLayerSpecification } from 'maplibre-gl';
import { mapLabelFonts } from '@/utils/mapLabelFonts';
import { POI_CATEGORIES, type PoiCategorySettings } from './poiCategories';

export const POI_LAYER_PREFIX = 'mtl-poi-';
export const POI_SOURCE_ID = 'mtl-poi-source';
export const POI_SOURCE_LAYER = 'pois';
export const POI_LABEL_ZOOM = 16;
export const POI_ICON_SIZE = 22;
export const POI_ICON_PIXEL_RATIO = 2;
const POI_ICON_PADDING = 7;
const POI_TEXT_PADDING = 3;
const POI_DEFAULT_IMPORTANCE = 17;
const POI_TEXT_SIZE = 11;
const POI_TEXT_MAX_WIDTH = 10;
const POI_TEXT_RADIAL_OFFSET = 1.25;
const POI_TEXT_HALO_WIDTH = 1.5;
const PERCENT_SCALE = 100;

export function buildPoiLayers(
  source: string,
  enabled: boolean,
  categories: PoiCategorySettings,
  opacity: number,
  dark = false,
  fontStack = mapLabelFonts()
): SymbolLayerSpecification[] {
  return POI_CATEGORIES.map((category, index) => ({
    id: `${POI_LAYER_PREFIX}${category.id}`,
    type: 'symbol',
    source,
    'source-layer': POI_SOURCE_LAYER,
    minzoom: category.minZoom,
    filter: ['in', ['get', 'kind'], ['literal', [...category.kinds]]],
    layout: {
      visibility: enabled && categories[category.id] !== false ? 'visible' : 'none',
      'icon-image': `${POI_LAYER_PREFIX}${category.id}`,
      'icon-size': 1,
      'icon-padding': POI_ICON_PADDING,
      'icon-allow-overlap': false,
      'icon-ignore-placement': false,
      'text-optional': true,
      'text-field': [
        'step',
        ['zoom'],
        '',
        POI_LABEL_ZOOM,
        ['coalesce', ['get', 'name'], ''],
      ] as ExpressionSpecification,
      'text-font': fontStack,
      'text-size': POI_TEXT_SIZE,
      'text-max-width': POI_TEXT_MAX_WIDTH,
      'text-variable-anchor': ['left', 'right', 'top', 'bottom'],
      'text-radial-offset': POI_TEXT_RADIAL_OFFSET,
      'text-padding': POI_TEXT_PADDING,
      'symbol-sort-key': ['+', index, ['/', ['coalesce', ['get', 'min_zoom'], POI_DEFAULT_IMPORTANCE], PERCENT_SCALE]],
    },
    paint: {
      'icon-opacity': opacity / PERCENT_SCALE,
      'text-opacity': opacity / PERCENT_SCALE,
      'text-color': dark ? '#f5f3ee' : '#423d36',
      'text-halo-color': dark ? '#25292b' : '#fffdf8',
      'text-halo-width': POI_TEXT_HALO_WIDTH,
    },
  }));
}
