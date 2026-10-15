import { describe, expect, it } from 'vitest';
import { miniMapDiamondIcon } from '@/components/map/miniMapDiamondIcon';
import { TRACK_COLOR } from '@/utils/trackColors';

function pixel(icon: ReturnType<typeof miniMapDiamondIcon>, x: number, y: number) {
  const offset = (Math.floor(y) * icon.width + Math.floor(x)) * 4;
  return Array.from(icon.data.slice(offset, offset + 4));
}

describe('mini-map reference diamond', () => {
  it('draws a white center, a colored border on the axes, and transparent corners', () => {
    const icon = miniMapDiamondIcon('#2563eb');
    const center = icon.width / 2;
    expect(pixel(icon, center, center)).toEqual([255, 255, 255, 255]);
    // Just inside the top vertex lies on the border.
    expect(pixel(icon, center, center - icon.width / 2 + 12)).toEqual([37, 99, 235, 255]);
    expect(pixel(icon, 0, 0)[3]).toBe(0);
  });

  it('falls back to the track color for colors it cannot parse', () => {
    const icon = miniMapDiamondIcon('red');
    const center = icon.width / 2;
    const [r, g, b] = [1, 3, 5].map((start) => parseInt(TRACK_COLOR.slice(start, start + 2), 16));
    expect(pixel(icon, center, center - icon.width / 2 + 12)).toEqual([r, g, b, 255]);
  });
});
