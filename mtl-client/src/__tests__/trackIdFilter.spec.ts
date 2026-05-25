import { describe, expect, it } from 'vitest';
import { addTrackIdToText, formatTrackIds, parseTrackIdText, removeTrackIdFromText } from '@/utils/trackIdFilter';

describe('track ID filter param', () => {
  it('parses comma, space, and newline separated IDs', () => {
    expect(parseTrackIdText('3, 1\n2 2;bad;-5')).toEqual([1, 2, 3]);
  });

  it('formats IDs as a normalized comma-separated string', () => {
    expect(formatTrackIds([5, 2, 5, 1])).toBe('1,2,5');
  });

  it('adds and removes IDs while preserving normalization', () => {
    expect(addTrackIdToText('2,1', 3)).toBe('1,2,3');
    expect(removeTrackIdFromText('3,2,1', 2)).toBe('1,3');
  });
});
