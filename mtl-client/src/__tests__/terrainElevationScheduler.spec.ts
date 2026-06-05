import { describe, expect, it } from 'vitest';
import { shouldReplaceElevationRefreshTimer } from '@/components/map/terrainElevationScheduler';

describe('terrain elevation scheduler', () => {
  it('keeps immediate move refreshes when moveend tries to debounce the same camera step', () => {
    expect(shouldReplaceElevationRefreshTimer(null, 350)).toBe(true);
    expect(shouldReplaceElevationRefreshTimer(350, 0)).toBe(true);
    expect(shouldReplaceElevationRefreshTimer(0, 350)).toBe(false);
    expect(shouldReplaceElevationRefreshTimer(250, 350)).toBe(false);
  });
});
