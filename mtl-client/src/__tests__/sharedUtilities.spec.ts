import { describe, expect, it } from 'vitest';
import { isAbortLikeError } from '@/utils/errors';
import { createGeoJsonCircle } from '@/utils/geoJson';
import { clamp01, interpolateNullableNumber, maxFiniteOrNull } from '@/utils/numbers';
import { lowerBoundClampedIndex, nearestSortedIndex, upperBoundClampedIndex } from '@/utils/sortedSearch';
import { toValidDateMs } from '@/utils/Utils';
import { trackEventKey, trackEventKeysEqual, trackEventTypeLabel } from '@/utils/trackEvents';

describe('shared utilities', () => {
  it('recognizes abort errors and nested cancellation causes', () => {
    expect(isAbortLikeError(new DOMException('Stopped', 'AbortError'))).toBe(true);
    expect(isAbortLikeError({ cause: { code: 'ERR_CANCELED' } })).toBe(true);
    expect(isAbortLikeError(new Error('Network failed'))).toBe(false);
  });

  it('finds lower, upper, and nearest indexes in sorted values', () => {
    const values = [1, 3, 3, 8];
    const valueOf = (value: number) => value;
    expect(lowerBoundClampedIndex(values, 3, valueOf)).toBe(1);
    expect(upperBoundClampedIndex(values, 3, valueOf)).toBe(3);
    expect(nearestSortedIndex(values, 7, valueOf)).toBe(3);
  });

  it('clamps and interpolates optional numbers', () => {
    expect(clamp01(Number.NaN)).toBe(0);
    expect(clamp01(2)).toBe(1);
    expect(interpolateNullableNumber(10, 20, 0.25)).toBe(12.5);
    expect(interpolateNullableNumber(null, 20, 0.25)).toBe(20);
    expect(maxFiniteOrNull([null, 2, Number.NaN, 7])).toBe(7);
  });

  it('normalizes dates and event metadata', () => {
    expect(toValidDateMs('invalid')).toBeNull();
    expect(trackEventKey({ id: 5 })).toBe(5);
    expect(trackEventKeysEqual(5, '5')).toBe(true);
    expect(trackEventTypeLabel('PHOTO_STOP')).toBe('Photo Stop');
  });

  it('creates a closed polygon for a circle', () => {
    const ring = createGeoJsonCircle(8, 47, 100, 8).features[0].geometry.coordinates[0];
    expect(ring).toHaveLength(9);
    expect(ring[0]).toEqual(ring[ring.length - 1]);
  });
});
