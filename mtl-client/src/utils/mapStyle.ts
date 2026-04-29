/**
 * mapStyle.ts — Generates MapLibre GL style objects for both local (vector/PMTiles)
 * and remote (raster/OSM) tile modes.
 *
 * Uses @protomaps/basemaps for vector tile styling with Protomaps-flavored layers.
 */

import { layers, LIGHT, DARK, GRAYSCALE, type Flavor } from '@protomaps/basemaps';
import type { StyleSpecification } from 'maplibre-gl';

export type MapTheme = 'light' | 'dark' | 'grayscale' | 'light-topo' | 'swisstopo' | 'swisstopo-color';

type ProtomapsTheme = 'light' | 'dark' | 'grayscale';

interface LocalVectorStyleOptions {
  hillshade?: boolean;
}

// Maps topo-enhanced themes to their underlying protomaps flavor
const TOPO_BASE: Partial<Record<MapTheme, ProtomapsTheme>> = {
  'light-topo': 'light',
};

const THEME_FLAVORS: Record<ProtomapsTheme, Flavor> = {
  light: LIGHT,
  dark: DARK,
  grayscale: GRAYSCALE,
};

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
  const firstSymbolIdx = existingLayers.findIndex(l => l.type === 'symbol');
  const insertAt = firstSymbolIdx >= 0 ? firstSymbolIdx : existingLayers.length;

  return {
    ...style,
    sources: {
      ...style.sources,
      'terrain-dem': {
        type: 'raster-dem',
        tiles: ['https://tiles.mapterhorn.com/{z}/{x}/{y}.webp'],
        tileSize: 512,
        encoding: 'terrarium',
        maxzoom: 15,
        attribution: 'Elevation: <a href="https://mapterhorn.com/attribution/">Mapterhorn</a>',
      },
    },
    layers: [
      ...existingLayers.slice(0, insertAt),
      {
        id: 'terrain-hillshade',
        type: 'hillshade',
        source: 'terrain-dem',
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

/**
 * Build a MapLibre style for local vector PMTiles served from the companion map server.
 *
 * @param tileBaseUrl      Base URL of the tile server (e.g. "/mtl/api/map-proxy/demo")
 * @param tilesetName      Name of the PMTiles file without extension (e.g. "planet")
 * @param theme            Visual theme
 * @param glyphsUrl        Optional custom glyphs URL. Defaults to Protomaps CDN.
 */
export function buildLocalVectorStyle(
  tileBaseUrl: string,
  tilesetName: string,
  theme: MapTheme = 'light',
  glyphsUrl?: string,
  options: LocalVectorStyleOptions = {},
): StyleSpecification {
  const sourceName = 'protomaps';
  const baseTheme: ProtomapsTheme = TOPO_BASE[theme] ?? (theme as ProtomapsTheme);
  const flavor = THEME_FLAVORS[baseTheme] ?? LIGHT;
  const enableHillshade = options.hillshade ?? true;

  const style: StyleSpecification = {
    version: 8,
    glyphs: glyphsUrl ?? 'https://protomaps.github.io/basemaps-assets/fonts/{fontstack}/{range}.pbf',
    sprite: 'https://protomaps.github.io/basemaps-assets/sprites/v4/light',
    sources: {
      [sourceName]: {
        type: 'vector',
        url: `pmtiles://${tileBaseUrl}/${tilesetName}.pmtiles`,
        attribution: '© <a href="https://openstreetmap.org">OpenStreetMap</a>',
      },
    },
    layers: layers(sourceName, flavor, { lang: 'en' }),
  } as StyleSpecification;

  return enableHillshade && theme in TOPO_BASE ? addHillshade(style) : style;
}

/**
 * Build a MapLibre style using remote raster tiles (e.g. OSM tile servers).
 *
 * @param remoteTileUrl  URL template with {z}, {x}, {y} placeholders
 */
export function buildRemoteRasterStyle(remoteTileUrl: string): StyleSpecification {
  return {
    version: 8,
    sources: {
      'raster-tiles': {
        type: 'raster',
        tiles: [remoteTileUrl],
        tileSize: 256,
        attribution: '© <a href="https://openstreetmap.org">OpenStreetMap</a> contributors',
      },
    },
    layers: [
      {
        id: 'raster-layer',
        type: 'raster',
        source: 'raster-tiles',
        minzoom: 0,
        maxzoom: 19,
      },
    ],
  } as StyleSpecification;
}

/**
 * Build a simple raster style for maximally lightweight mini-maps when the map config
 * is not yet loaded or we want a guaranteed-working fallback.
 */
export function buildFallbackRasterStyle(): StyleSpecification {
  return buildRemoteRasterStyle('https://tile.openstreetmap.org/{z}/{x}/{y}.png');
}

/**
 * URL of the official swisstopo Light Base Map vector style (includes hillshading).
 * Can be passed directly as a MapLibre style since it's a valid StyleSpecification URL.
 */
export const SWISSTOPO_STYLE_URL =
  'https://vectortiles.geo.admin.ch/styles/ch.swisstopo.lightbasemap.vt/style.json';

/**
 * Higher-contrast swisstopo Base Map style: saturated landcover greens, color-coded roads.
 */
export const SWISSTOPO_COLOR_STYLE_URL =
  'https://vectortiles.geo.admin.ch/styles/ch.swisstopo.basemap.vt/style.json';

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
