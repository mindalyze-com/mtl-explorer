import { describe, expect, it } from 'vitest';
import { mapLabelFonts } from '@/utils/mapLabelFonts';
import { PROTOMAPS_GLYPHS_URL } from '@/utils/mapStyle';

const SWISSTOPO_GLYPHS_URL = 'https://vectortiles.geo.admin.ch/fonts/{fontstack}/{range}.pbf';

describe('map overlay fonts', () => {
  it('uses fonts served by the Swiss Topo glyph endpoint for both label weights', () => {
    expect(mapLabelFonts(SWISSTOPO_GLYPHS_URL)).toEqual(['Frutiger Neue Regular']);
    expect(mapLabelFonts(SWISSTOPO_GLYPHS_URL, 'medium')).toEqual(['Frutiger Neue Medium']);
  });

  it('retains Noto fonts for OSM and raster fallback styles', () => {
    expect(mapLabelFonts(PROTOMAPS_GLYPHS_URL)).toEqual(['Noto Sans Regular']);
    expect(mapLabelFonts(PROTOMAPS_GLYPHS_URL, 'medium')).toEqual(['Noto Sans Medium']);
    expect(mapLabelFonts()).toEqual(['Noto Sans Regular']);
  });
});
