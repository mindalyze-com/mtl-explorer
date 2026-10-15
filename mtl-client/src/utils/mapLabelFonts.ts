type MapLabelWeight = 'regular' | 'medium';

const SWISSTOPO_GLYPHS_PREFIX = 'https://vectortiles.geo.admin.ch/fonts/';
const SWISSTOPO_LABEL_FONTS: Record<MapLabelWeight, string> = {
  regular: 'Frutiger Neue Regular',
  medium: 'Frutiger Neue Medium',
};
const DEFAULT_LABEL_FONTS: Record<MapLabelWeight, string> = {
  regular: 'Noto Sans Regular',
  medium: 'Noto Sans Medium',
};

/** Overlay fonts must be available from the active style's glyph endpoint. */
export function mapLabelFonts(glyphsUrl?: string | null, weight: MapLabelWeight = 'regular'): string[] {
  const fonts = glyphsUrl?.startsWith(SWISSTOPO_GLYPHS_PREFIX) ? SWISSTOPO_LABEL_FONTS : DEFAULT_LABEL_FONTS;
  return [fonts[weight]];
}
