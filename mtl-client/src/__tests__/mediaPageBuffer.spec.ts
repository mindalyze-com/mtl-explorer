import { describe, expect, it } from 'vitest';
import { mergeAdjacentMediaPage } from '@/components/map/mediaPageBuffer';

describe('media page buffer', () => {
  it('keeps adjacent pages in global order', () => {
    const right = mergeAdjacentMediaPage([1, 2], 0, [3, 4], 2, 2, 1);
    expect(right).toEqual({ items: [1, 2, 3, 4], offset: 0 });

    const left = mergeAdjacentMediaPage([3, 4], 2, [1, 2], 0, 2, -1);
    expect(left).toEqual({ items: [1, 2, 3, 4], offset: 0 });
  });

  it('keeps only three pages near the movement direction', () => {
    const right = mergeAdjacentMediaPage([1, 2, 3, 4, 5, 6], 0, [7, 8], 6, 2, 1);
    expect(right).toEqual({ items: [3, 4, 5, 6, 7, 8], offset: 2 });

    const left = mergeAdjacentMediaPage([3, 4, 5, 6, 7, 8], 2, [1, 2], 0, 2, -1);
    expect(left).toEqual({ items: [1, 2, 3, 4, 5, 6], offset: 0 });
  });

  it('replaces the buffer when pages are not adjacent', () => {
    expect(mergeAdjacentMediaPage([1, 2], 0, [7, 8], 6, 2, 1)).toEqual({ items: [7, 8], offset: 6 });
  });
});
