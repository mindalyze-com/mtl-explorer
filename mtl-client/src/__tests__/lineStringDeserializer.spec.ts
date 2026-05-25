import { describe, expect, it, vi } from 'vitest';
import { extractCoordinates } from '@/utils/lineStringDeserializer';

type ExtractCoordinatesInput = Parameters<typeof extractCoordinates>[0];

describe('extractCoordinates', () => {
  it('returns [] for null/undefined input', () => {
    expect(extractCoordinates(null)).toEqual([]);
    expect(extractCoordinates(undefined)).toEqual([]);
    expect(extractCoordinates([])).toEqual([]);
  });

  it('handles bare-array shape from custom LineStringSerializer', () => {
    const data = [
      {
        track: [
          [1, 2, 100],
          [3, 4, 200],
          [5, 6],
        ],
      } as unknown as NonNullable<ExtractCoordinatesInput>[number],
    ];
    expect(extractCoordinates(data)).toEqual([
      [1, 2],
      [3, 4],
      [5, 6],
    ]);
  });

  it('handles {coordinates: [{x, y}, ...]} shape from generated OpenAPI client', () => {
    const data = [
      {
        track: {
          coordinates: [
            { x: 10, y: 20 },
            { x: 30, y: 40 },
          ],
        },
      } as unknown as NonNullable<ExtractCoordinatesInput>[number],
    ];
    expect(extractCoordinates(data)).toEqual([
      [10, 20],
      [30, 40],
    ]);
  });

  it('handles {coordinates: [[lng, lat], ...]} shape from cached IndexedDB payloads', () => {
    const data = [
      {
        track: {
          coordinates: [
            [7, 8],
            [9, 10],
          ],
        },
      } as unknown as NonNullable<ExtractCoordinatesInput>[number],
    ];
    expect(extractCoordinates(data)).toEqual([
      [7, 8],
      [9, 10],
    ]);
  });

  it('flattens coordinates across multiple GpsTrackData entries', () => {
    const data = [
      { track: [[1, 1]] } as unknown as NonNullable<ExtractCoordinatesInput>[number],
      { track: { coordinates: [{ x: 2, y: 2 }] } } as unknown as NonNullable<ExtractCoordinatesInput>[number],
    ];
    expect(extractCoordinates(data)).toEqual([
      [1, 1],
      [2, 2],
    ]);
  });

  it('skips entries with null track and entries with unknown shape (warning)', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const data = [
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      { track: null } as any,
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      null as any,
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      { track: 'unexpected-string' } as any,
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      { track: [[42, 43]] } as any,
    ];
    expect(extractCoordinates(data)).toEqual([[42, 43]]);
    expect(warn).toHaveBeenCalledTimes(1);
    warn.mockRestore();
  });

  it('ignores invalid coordinate tuples in bare-array shape', () => {
    const data = [
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      { track: [[1, 2], 'bad', [3], null, [Number.NaN, 4], [5, Infinity], ['6', 7], [4, 5]] } as any,
    ];
    expect(extractCoordinates(data)).toEqual([
      [1, 2],
      [4, 5],
    ]);
  });

  it('ignores non-finite object coordinates from generated LineString shapes', () => {
    const data = [
      {
        track: {
          coordinates: [
            { x: 8, y: 47 },
            { x: Number.POSITIVE_INFINITY, y: 47.1 },
            { x: 8.2, y: Number.NaN },
            { x: '8.3', y: 47.3 },
          ],
        },
      } as unknown as NonNullable<ExtractCoordinatesInput>[number],
    ];
    expect(extractCoordinates(data)).toEqual([[8, 47]]);
  });
});
