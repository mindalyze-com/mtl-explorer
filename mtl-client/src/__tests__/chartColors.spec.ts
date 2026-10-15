import { describe, expect, it } from 'vitest';
import { generateColors } from '@/utils/Utils';

const rgb = (color: string) => [1, 3, 5].map((offset) => parseInt(color.slice(offset, offset + 2), 16));

describe('comparison colors', () => {
  it.each([2, 3, 4, 5, 6])('separates every pair in a small selection of %i series', (count) => {
    const colors = generateColors(count).map(rgb);
    for (let i = 0; i < colors.length; i++)
      for (let j = i + 1; j < colors.length; j++) {
        const distance = Math.hypot(...colors[i].map((channel, index) => channel - colors[j][index]));
        expect(distance).toBeGreaterThan(count <= 4 ? 150 : 70);
      }
  });

  it('keeps small selections stable when more series are added', () => {
    const larger = generateColors(250);
    for (const count of [1, 2, 3, 4, 8]) expect(generateColors(count)).toEqual(larger.slice(0, count));
  });

  it.each([0, 1, 8, 9, 10, 12, 250])('supports %i series without repeating colors', (count) => {
    const colors = generateColors(count);
    expect(colors).toHaveLength(count);
    expect(new Set(colors).size).toBe(count);
    for (const color of colors) expect(color).toMatch(/^#[0-9a-f]{6}$/i);
  });
});
