import { describe, expect, it, vi } from 'vitest';
import { extractCoordinates } from '@/utils/lineStringDeserializer';

describe('extractCoordinates', () => {
  it('returns [] for null/undefined input', () => {
    expect(extractCoordinates(null)).toEqual([]);
    expect(extractCoordinates(undefined)).toEqual([]);
    expect(extractCoordinates([])).toEqual([]);
  });

  it('handles bare-array shape from custom LineStringSerializer', () => {
    const data = [
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      { track: [[1, 2, 100], [3, 4, 200], [5, 6]] } as any,
    ];
    expect(extractCoordinates(data)).toEqual([
      [1, 2],
      [3, 4],
      [5, 6],
    ]);
  });

  it('handles {coordinates: [{x, y}, ...]} shape from generated OpenAPI client', () => {
    const data = [
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      { track: { coordinates: [{ x: 10, y: 20 }, { x: 30, y: 40 }] } } as any,
    ];
    expect(extractCoordinates(data)).toEqual([
      [10, 20],
      [30, 40],
    ]);
  });

  it('handles {coordinates: [[lng, lat], ...]} shape from cached IndexedDB payloads', () => {
    const data = [
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      { track: { coordinates: [[7, 8], [9, 10]] } } as any,
    ];
    expect(extractCoordinates(data)).toEqual([
      [7, 8],
      [9, 10],
    ]);
  });

  it('flattens coordinates across multiple GpsTrackData entries', () => {
    const data = [
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      { track: [[1, 1]] } as any,
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      { track: { coordinates: [{ x: 2, y: 2 }] } } as any,
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
      { track: [[1, 2], 'bad', [3], null, [4, 5]] } as any,
    ];
    expect(extractCoordinates(data)).toEqual([
      [1, 2],
      [4, 5],
    ]);
  });
});
