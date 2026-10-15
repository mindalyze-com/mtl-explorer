import { TRACK_COLOR } from '@/utils/trackColors';

export const MINI_MAP_DIAMOND_SHAPE = 'diamond';
export const MINI_MAP_DIAMOND_ICON_PREFIX = 'minimap-diamond-';
export const MINI_MAP_DIAMOND_PIXEL_RATIO = 2;

const DIAMOND_RADIUS_PX = 12;
const DIAMOND_BORDER_PX = 3;
const DIAMOND_SHADOW_PX = 1.5;
const DIAMOND_SHADOW_OPACITY = 0.3;
const CANVAS_PADDING_PX = 3;
const FILL_RGB: [number, number, number] = [255, 255, 255];
const HEX_COLOR = /^#([0-9a-f]{3}|[0-9a-f]{6})$/i;
const MAX_CHANNEL = 255;

export function miniMapDiamondIconId(color: string): string {
  return MINI_MAP_DIAMOND_ICON_PREFIX + normalizedHex(color).slice(1);
}

/** White diamond with a colored border, rasterized directly so it needs no canvas support. */
export function miniMapDiamondIcon(color: string) {
  const ratio = MINI_MAP_DIAMOND_PIXEL_RATIO;
  const radius = DIAMOND_RADIUS_PX * ratio;
  const border = DIAMOND_BORDER_PX * ratio;
  const shadow = DIAMOND_SHADOW_PX * ratio;
  const size = 2 * (DIAMOND_RADIUS_PX + CANVAS_PADDING_PX) * ratio;
  const center = size / 2;
  const stroke = hexRgb(normalizedHex(color));
  const data = new Uint8Array(size * size * 4);
  for (let y = 0; y < size; y++)
    for (let x = 0; x < size; x++) {
      // A diamond is an L1 ball; dividing by √2 gives the perpendicular distance to its edge.
      const inside = (radius - Math.abs(x + 0.5 - center) - Math.abs(y + 0.5 - center)) / Math.SQRT2;
      const shapeAlpha = clamp(inside + 0.5);
      const shadowAlpha = clamp(inside + shadow + 0.5) * DIAMOND_SHADOW_OPACITY;
      const fillMix = clamp(inside - border + 0.5);
      const alpha = shapeAlpha + shadowAlpha * (1 - shapeAlpha);
      const offset = (y * size + x) * 4;
      for (let channel = 0; channel < 3; channel++) {
        const shapeValue = stroke[channel] + (FILL_RGB[channel] - stroke[channel]) * fillMix;
        // The shadow is black, so only the shape contributes color before un-premultiplying.
        data[offset + channel] = alpha > 0 ? Math.round((shapeValue * shapeAlpha) / alpha) : 0;
      }
      data[offset + 3] = Math.round(alpha * MAX_CHANNEL);
    }
  return { width: size, height: size, data };
}

function normalizedHex(color: string): string {
  const value = HEX_COLOR.test(color) ? color : TRACK_COLOR;
  return value.length === 4
    ? `#${value[1]}${value[1]}${value[2]}${value[2]}${value[3]}${value[3]}`.toLowerCase()
    : value.toLowerCase();
}

function hexRgb(hex: string): [number, number, number] {
  return [1, 3, 5].map((start) => parseInt(hex.slice(start, start + 2), 16)) as [number, number, number];
}

function clamp(value: number): number {
  return Math.min(1, Math.max(0, value));
}
