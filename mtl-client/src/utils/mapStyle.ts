/**
 * mapStyle.ts — Generates MapLibre GL style objects for both local (vector/PMTiles)
 * and remote raster tile modes.
 *
 * Uses @protomaps/basemaps for vector tile styling with Protomaps-flavored layers.
 */

import { layers, LIGHT, DARK, GRAYSCALE, type Flavor } from '@protomaps/basemaps';
import type { RasterDEMSourceSpecification, StyleSpecification } from 'maplibre-gl';

export type MapTheme =
  | 'light'
  | 'dark'
  | 'grayscale'
  | 'light-topo'
  | 'topo-contrast'
  | 'swisstopo'
  | 'swisstopo-color';

export const TOPO_CONTRAST_THEME: MapTheme = 'topo-contrast';

const MAP_THEME_CODES = new Set<string>([
  'light',
  'dark',
  'grayscale',
  'light-topo',
  TOPO_CONTRAST_THEME,
  'swisstopo',
  'swisstopo-color',
]);

export function normalizeMapTheme(theme: unknown, fallback: MapTheme = 'light'): MapTheme {
  if (typeof theme !== 'string') return fallback;
  const trimmedTheme = theme.trim();
  return MAP_THEME_CODES.has(trimmedTheme) ? (trimmedTheme as MapTheme) : fallback;
}

interface LocalVectorStyleOptions {
  hillshade?: boolean;
}

type StyleLayer = Omit<StyleSpecification['layers'][number], 'layout' | 'paint'> & {
  filter?: unknown;
  layout?: Record<string, unknown>;
  paint?: Record<string, unknown>;
  'source-layer'?: string;
};

export const TERRAIN_DEM_SOURCE_ID = 'terrain-dem';
export const MAPTERHORN_TERRAIN_TILEJSON_URL = 'https://tiles.mapterhorn.com/tilejson.json';
export const TERRAIN_HILLSHADE_LAYER_ID = 'terrain-hillshade';
export const TOPO_CONTRAST_URBAN_AREA_LAYER_ID = 'topo-contrast-urban-area';
export const TOPO_CONTRAST_URBAN_LANDUSE_LAYER_ID = 'topo-contrast-urban-landuse';

export function createTerrainDemSource(): RasterDEMSourceSpecification {
  return {
    type: 'raster-dem',
    url: MAPTERHORN_TERRAIN_TILEJSON_URL,
    encoding: 'terrarium',
    tileSize: 512,
    maxzoom: 15,
    attribution: 'Elevation: <a href="https://mapterhorn.com/attribution/">Mapterhorn</a>',
  };
}

const TOPO_CONTRAST_FLAVOR: Flavor = {
  ...LIGHT,
  background: '#d5d8cf',
  earth: '#ede7d7',
  park_a: '#c6ddb4',
  park_b: '#a8cf92',
  hospital: '#ead6d6',
  industrial: '#d8d4ca',
  school: '#eadfc6',
  wood_a: '#bfd8b3',
  wood_b: '#8fc784',
  pedestrian: '#efe8d2',
  scrub_a: '#d6dba8',
  scrub_b: '#becf79',
  glacier: '#f6f7f5',
  sand: '#eadba3',
  beach: '#f0dea4',
  aerodrome: '#d8dbe1',
  runway: '#d2d1d0',
  water: '#6fc4e8',
  zoo: '#b7d6b1',
  military: '#dfc3c3',
  buildings: '#c9c2b5',
  minor_service_casing: '#d1cbc0',
  minor_casing: '#c7c1b6',
  link_casing: '#c9b7a2',
  major_casing_late: '#c9ad7e',
  highway_casing_late: '#b95642',
  other: '#c9c0ad',
  minor_service: '#f6f3e8',
  minor_a: '#f8f4e8',
  minor_b: '#ffffff',
  link: '#ffe8b0',
  major_casing_early: '#c9ad7e',
  major: '#ffd36e',
  highway_casing_early: '#b95642',
  highway: '#ef8a4a',
  railway: '#6f777b',
  boundaries: '#8f8a80',
  bridges_minor_casing: '#c7c1b6',
  bridges_link_casing: '#c9b7a2',
  bridges_major_casing: '#c9ad7e',
  bridges_highway_casing: '#b95642',
  bridges_minor: '#ffffff',
  bridges_link: '#ffe8b0',
  bridges_major: '#ffd36e',
  bridges_highway: '#ef8a4a',
  roads_label_minor: '#675f58',
  roads_label_minor_halo: '#ffffff',
  roads_label_major: '#5d5146',
  roads_label_major_halo: '#fff5df',
  ocean_label: '#227fa6',
  subplace_label: '#5f5a52',
  subplace_label_halo: '#f2ead8',
  city_label: '#25211d',
  city_label_halo: '#fff6e5',
  state_label: '#665f58',
  state_label_halo: '#f4ead7',
  country_label: '#423b34',
  address_label: '#675f58',
  address_label_halo: '#ffffff',
  landcover: {
    grassland: 'rgba(203, 232, 189, 1)',
    barren: 'rgba(239, 226, 182, 1)',
    urban_area: 'rgba(221, 216, 207, 1)',
    farmland: 'rgba(217, 232, 184, 1)',
    glacier: 'rgba(248, 250, 250, 1)',
    scrub: 'rgba(217, 221, 168, 1)',
    forest: 'rgba(184, 217, 175, 1)',
  },
};

const THEME_FLAVORS: Partial<Record<MapTheme, Flavor>> = {
  light: LIGHT,
  'light-topo': LIGHT,
  [TOPO_CONTRAST_THEME]: TOPO_CONTRAST_FLAVOR,
  dark: DARK,
  grayscale: GRAYSCALE,
};

const HILLSHADE_THEMES = new Set<MapTheme>(['light-topo', TOPO_CONTRAST_THEME]);

const TOPO_CONTRAST_URBAN_AREA_FILL_COLOR = '#e2d0c3';
const TOPO_CONTRAST_URBAN_AREA_OUTLINE_COLOR = 'rgba(166, 126, 99, 0.42)';
const TOPO_CONTRAST_URBAN_LANDUSE_KINDS = ['residential', 'commercial', 'retail', 'industrial'] as const;
const TOPO_CONTRAST_URBAN_LANDUSE_FILL_COLOR = '#e7cdbc';
const TOPO_CONTRAST_URBAN_LANDUSE_OUTLINE_COLOR = 'rgba(163, 112, 84, 0.5)';
const TOPO_CONTRAST_URBAN_AREA_OPACITY = [
  'interpolate',
  ['linear'],
  ['zoom'],
  5,
  0.34,
  7,
  0.56,
  9,
  0.62,
  13,
  0.34,
  15,
  0.18,
];
const TOPO_CONTRAST_URBAN_LANDUSE_OPACITY = ['interpolate', ['linear'], ['zoom'], 7, 0.26, 10, 0.5, 12, 0.56, 15, 0.36];

const TOPO_CONTRAST_CITY_LABEL_TEXT_SIZE = [
  'interpolate',
  ['linear'],
  ['zoom'],
  2,
  ['case', ['<', ['get', 'population_rank'], 13], 9, ['>=', ['get', 'population_rank'], 13], 14, 0],
  4,
  ['case', ['<', ['get', 'population_rank'], 13], 11, ['>=', ['get', 'population_rank'], 13], 16, 0],
  6,
  ['case', ['<', ['get', 'population_rank'], 12], 12, ['>=', ['get', 'population_rank'], 12], 18, 0],
  8,
  ['case', ['<', ['get', 'population_rank'], 11], 12, ['>=', ['get', 'population_rank'], 11], 19, 0],
  10,
  ['case', ['<', ['get', 'population_rank'], 9], 13, ['>=', ['get', 'population_rank'], 9], 21, 0],
  15,
  ['case', ['<', ['get', 'population_rank'], 8], 13, ['>=', ['get', 'population_rank'], 8], 23, 0],
];

const TOPO_CONTRAST_CITY_LABEL_FONT = [
  'case',
  ['<=', ['get', 'min_zoom'], 8],
  ['literal', ['Noto Sans Medium']],
  ['literal', ['Noto Sans Regular']],
];

const TOPO_CONTRAST_CITY_LABEL_PADDING = ['interpolate', ['linear'], ['zoom'], 5, 2, 8, 5, 12, 8];
const TOPO_CONTRAST_BUILDING_OPACITY = 0.64;
const TOPO_CONTRAST_BUILDING_OUTLINE = '#a99f91';
const TOPO_CONTRAST_CITY_ICON_SIZE = 0.85;
const TOPO_CONTRAST_CITY_HALO_BLUR = 0.2;
const TOPO_CONTRAST_CITY_HALO_WIDTH = 1.6;
const TOPO_CONTRAST_SUBPLACE_HALO_BLUR = 0.15;
const TOPO_CONTRAST_SUBPLACE_HALO_WIDTH = 1.2;

const DARK_RASTER_PAINT = {
  'raster-brightness-min': 0,
  'raster-brightness-max': 0.55,
  'raster-contrast': 0.25,
  'raster-saturation': -0.75,
} as const;

const GRAYSCALE_RASTER_PAINT = {
  'raster-saturation': -1,
} as const;

const RASTER_PAINT_BY_THEME: Partial<Record<MapTheme, typeof DARK_RASTER_PAINT | typeof GRAYSCALE_RASTER_PAINT>> = {
  dark: DARK_RASTER_PAINT,
  grayscale: GRAYSCALE_RASTER_PAINT,
};

export const DEFAULT_RASTER_ATTRIBUTION =
  '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors';

/**
 * Enriches a StyleSpecification with a hillshade layer using free Terrarium DEM tiles.
 * The hillshade is inserted just before the first symbol/label layer so it sits on top of
 * all fill + line layers (where its transparent flat-area pixels let the map show through)
 * while labels remain fully readable above it.
 */
function addHillshade(style: StyleSpecification): StyleSpecification {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const existingLayers = style.layers as any[];
  // Place hillshade above all fill/polygon layers but below labels/symbols
  const firstSymbolIdx = existingLayers.findIndex((l) => l.type === 'symbol');
  const insertAt = firstSymbolIdx >= 0 ? firstSymbolIdx : existingLayers.length;

  return {
    ...style,
    sources: {
      ...style.sources,
      [TERRAIN_DEM_SOURCE_ID]: createTerrainDemSource(),
    },
    layers: [
      ...existingLayers.slice(0, insertAt),
      {
        id: TERRAIN_HILLSHADE_LAYER_ID,
        type: 'hillshade',
        source: TERRAIN_DEM_SOURCE_ID,
        paint: {
          'hillshade-shadow-color': '#535344',
          'hillshade-highlight-color': '#FFFFFF',
          'hillshade-exaggeration': 0.5,
        },
      },
      ...existingLayers.slice(insertAt),
    ],
  } as StyleSpecification;
}

function createTopoContrastUrbanAreaLayer(landcoverLayer: StyleLayer): StyleLayer {
  return {
    ...landcoverLayer,
    id: TOPO_CONTRAST_URBAN_AREA_LAYER_ID,
    filter: ['==', ['get', 'kind'], 'urban_area'],
    paint: {
      'fill-color': TOPO_CONTRAST_URBAN_AREA_FILL_COLOR,
      'fill-opacity': TOPO_CONTRAST_URBAN_AREA_OPACITY,
      'fill-outline-color': TOPO_CONTRAST_URBAN_AREA_OUTLINE_COLOR,
    },
  };
}

function createTopoContrastUrbanLanduseLayer(landcoverLayer: StyleLayer): StyleLayer {
  return {
    ...landcoverLayer,
    id: TOPO_CONTRAST_URBAN_LANDUSE_LAYER_ID,
    'source-layer': 'landuse',
    filter: ['in', 'kind', ...TOPO_CONTRAST_URBAN_LANDUSE_KINDS],
    paint: {
      'fill-color': TOPO_CONTRAST_URBAN_LANDUSE_FILL_COLOR,
      'fill-opacity': TOPO_CONTRAST_URBAN_LANDUSE_OPACITY,
      'fill-outline-color': TOPO_CONTRAST_URBAN_LANDUSE_OUTLINE_COLOR,
    },
  };
}

function enhanceTopoContrastLayers(styleLayers: StyleSpecification['layers']): StyleSpecification['layers'] {
  const enhancedLayers: StyleLayer[] = [];

  for (const layer of styleLayers) {
    const styleLayer = layer as StyleLayer;

    if (layer.id === 'landcover') {
      enhancedLayers.push(
        styleLayer,
        createTopoContrastUrbanAreaLayer(styleLayer),
        createTopoContrastUrbanLanduseLayer(styleLayer)
      );
      continue;
    }

    if (layer.id === 'buildings') {
      enhancedLayers.push({
        ...styleLayer,
        paint: {
          ...(styleLayer.paint ?? {}),
          'fill-opacity': TOPO_CONTRAST_BUILDING_OPACITY,
          'fill-outline-color': TOPO_CONTRAST_BUILDING_OUTLINE,
        },
      });
      continue;
    }

    if (layer.id === 'places_locality') {
      enhancedLayers.push({
        ...styleLayer,
        layout: {
          ...(styleLayer.layout ?? {}),
          'icon-size': TOPO_CONTRAST_CITY_ICON_SIZE,
          'text-font': TOPO_CONTRAST_CITY_LABEL_FONT,
          'text-padding': TOPO_CONTRAST_CITY_LABEL_PADDING,
          'text-size': TOPO_CONTRAST_CITY_LABEL_TEXT_SIZE,
        },
        paint: {
          ...(styleLayer.paint ?? {}),
          'text-halo-blur': TOPO_CONTRAST_CITY_HALO_BLUR,
          'text-halo-width': TOPO_CONTRAST_CITY_HALO_WIDTH,
        },
      });
      continue;
    }

    if (layer.id === 'places_subplace') {
      enhancedLayers.push({
        ...styleLayer,
        paint: {
          ...(styleLayer.paint ?? {}),
          'text-halo-blur': TOPO_CONTRAST_SUBPLACE_HALO_BLUR,
          'text-halo-width': TOPO_CONTRAST_SUBPLACE_HALO_WIDTH,
        },
      });
      continue;
    }

    enhancedLayers.push(styleLayer);
  }

  return enhancedLayers as StyleSpecification['layers'];
}

/**
 * Build a MapLibre style for local vector PMTiles served from the companion map server.
 *
 * @param tileBaseUrl      Base URL of the tile server (e.g. "/mtl/api/map-proxy/prod")
 * @param tilesetName      Name of the PMTiles file without extension (e.g. "planet")
 * @param theme            Visual theme
 * @param glyphsUrl        Optional custom glyphs URL. Defaults to Protomaps CDN.
 */
export function buildLocalVectorStyle(
  tileBaseUrl: string,
  tilesetName: string,
  theme: MapTheme = 'light',
  glyphsUrl?: string,
  options: LocalVectorStyleOptions = {}
): StyleSpecification {
  return buildLocalVectorStyleFromArchiveUrl(`${tileBaseUrl}/${tilesetName}.pmtiles`, theme, glyphsUrl, options);
}

export function buildLocalVectorStyleFromArchiveUrl(
  tileArchiveUrl: string,
  theme: MapTheme = 'light',
  glyphsUrl?: string,
  options: LocalVectorStyleOptions = {}
): StyleSpecification {
  const normalizedTheme = normalizeMapTheme(theme);
  const sourceName = 'protomaps';
  const flavor = THEME_FLAVORS[normalizedTheme] ?? LIGHT;
  const enableHillshade = options.hillshade ?? true;
  const baseLayers = layers(sourceName, flavor, { lang: 'en' });

  const style: StyleSpecification = {
    version: 8,
    glyphs: glyphsUrl ?? 'https://protomaps.github.io/basemaps-assets/fonts/{fontstack}/{range}.pbf',
    sprite: 'https://protomaps.github.io/basemaps-assets/sprites/v4/light',
    sources: {
      [sourceName]: {
        type: 'vector',
        url: `pmtiles://${tileArchiveUrl}`,
        attribution: '© <a href="https://openstreetmap.org">OpenStreetMap</a>',
      },
    },
    layers: normalizedTheme === TOPO_CONTRAST_THEME ? enhanceTopoContrastLayers(baseLayers) : baseLayers,
  } as StyleSpecification;

  return enableHillshade && HILLSHADE_THEMES.has(normalizedTheme) ? addHillshade(style) : style;
}

/**
 * Build a MapLibre style using remote raster tiles.
 *
 * @param tileUrl        URL template with {z}, {x}, {y} placeholders
 * @param theme          Optional theme used for client-side raster styling
 * @param attribution    Provider attribution HTML for this raster source
 */
export function buildRemoteRasterStyle(
  tileUrl: string,
  theme?: MapTheme,
  attribution = DEFAULT_RASTER_ATTRIBUTION
): StyleSpecification {
  const normalizedTheme = theme ? normalizeMapTheme(theme) : undefined;
  const rasterPaint = normalizedTheme ? RASTER_PAINT_BY_THEME[normalizedTheme] : undefined;
  const rasterLayer = {
    id: 'raster-layer',
    type: 'raster',
    source: 'raster-tiles',
    minzoom: 0,
    maxzoom: 19,
    ...(rasterPaint ? { paint: rasterPaint } : {}),
  } as const;
  const style = {
    version: 8,
    sources: {
      'raster-tiles': {
        type: 'raster',
        tiles: [tileUrl],
        tileSize: 256,
        attribution,
      },
    },
    layers: [rasterLayer],
  } as StyleSpecification;

  return normalizedTheme && HILLSHADE_THEMES.has(normalizedTheme) ? addHillshade(style) : style;
}

/**
 * Build a simple raster style for maximally lightweight mini-maps when the map config
 * is not yet loaded or we want a guaranteed-working fallback.
 */
export function buildFallbackRasterStyle(theme?: MapTheme): StyleSpecification {
  return buildRemoteRasterStyle('https://tile.openstreetmap.org/{z}/{x}/{y}.png', theme, DEFAULT_RASTER_ATTRIBUTION);
}

/**
 * URL of the official swisstopo Light Base Map vector style (includes hillshading).
 * Can be passed directly as a MapLibre style since it's a valid StyleSpecification URL.
 */
export const SWISSTOPO_STYLE_URL = 'https://vectortiles.geo.admin.ch/styles/ch.swisstopo.lightbasemap.vt/style.json';

/**
 * Higher-contrast swisstopo Base Map style: saturated landcover greens, color-coded roads.
 */
export const SWISSTOPO_COLOR_STYLE_URL = 'https://vectortiles.geo.admin.ch/styles/ch.swisstopo.basemap.vt/style.json';

/**
 * Swiss Mobility overlay tile URLs (XYZ raster tiles with transparency).
 * Switzerland only — these can be toggled independently of the base map theme.
 */
export const SWISS_OVERLAYS = [
  {
    id: 'wanderland',
    label: 'Hiking routes',
    icon: 'bi bi-signpost-2',
    url: 'https://wmts.geo.admin.ch/1.0.0/ch.astra.wanderland/default/current/3857/{z}/{x}/{y}.png',
    attribution: '© <a href="https://www.schweizmobil.ch">SchweizMobil</a>',
  },
  {
    id: 'veloland',
    label: 'Bike routes',
    icon: 'bi bi-bicycle',
    url: 'https://wmts.geo.admin.ch/1.0.0/ch.astra.veloland/default/current/3857/{z}/{x}/{y}.png',
    attribution: '© <a href="https://www.schweizmobil.ch">SchweizMobil</a>',
    hueRotate: -180,
  },
  {
    id: 'mountainbikeland',
    label: 'Mountainbike routes',
    icon: 'bi bi-bicycle',
    url: 'https://wmts.geo.admin.ch/1.0.0/ch.astra.mountainbikeland/default/current/3857/{z}/{x}/{y}.png',
    attribution: '© <a href="https://www.schweizmobil.ch">SchweizMobil</a>',
  },
  {
    id: 'wanderwege',
    label: 'Hiking trails (all)',
    icon: 'bi bi-signpost',
    url: 'https://wmts.geo.admin.ch/1.0.0/ch.swisstopo.swisstlm3d-wanderwege/default/current/3857/{z}/{x}/{y}.png',
    attribution: '© <a href="https://www.swisstopo.admin.ch">swisstopo</a>',
  },
];

/**
 * Waymarked Trails overlays — worldwide hiking, cycling, and MTB routes from OSM.
 * Tiles are pre-styled by importance: international routes visible from zoom ~5,
 * national ~7, regional ~9, local ~12. Free public tiles; fair-use policy applies.
 */
export const WAYMARKED_OVERLAYS = [
  {
    id: 'wmt-hiking',
    label: 'Hiking (worldwide)',
    icon: 'bi bi-signpost-2',
    url: 'https://tile.waymarkedtrails.org/hiking/{z}/{x}/{y}.png',
    attribution: '© <a href="https://waymarkedtrails.org">Waymarked Trails</a>',
  },
  {
    id: 'wmt-cycling',
    label: 'Cycling (worldwide)',
    icon: 'bi bi-bicycle',
    url: 'https://tile.waymarkedtrails.org/cycling/{z}/{x}/{y}.png',
    attribution: '© <a href="https://waymarkedtrails.org">Waymarked Trails</a>',
  },
  {
    id: 'wmt-mtb',
    label: 'MTB (worldwide)',
    icon: 'bi bi-bicycle',
    url: 'https://tile.waymarkedtrails.org/mtb/{z}/{x}/{y}.png',
    attribution: '© <a href="https://waymarkedtrails.org">Waymarked Trails</a>',
  },
];

/** All map overlays — Swiss + worldwide. Iterated by Map.vue for add/remove/toggle. */
export const MAP_OVERLAYS = [...SWISS_OVERLAYS, ...WAYMARKED_OVERLAYS];

/** @deprecated Use SWISS_OVERLAYS instead. Kept for backward compatibility. */
export const SWISS_MOBILITY_OVERLAYS = SWISS_OVERLAYS;
