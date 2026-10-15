import { describe, expect, it } from 'vitest';
import { localDateBoundary } from '@/features/health/utils/dateBoundary';

describe('Google Health date filters', () => {
  it('uses local midnight and an exclusive next-day end boundary', () => {
    const start = localDateBoundary('2026-08-28', false);
    const end = localDateBoundary('2026-08-28', true);

    expect(start?.getFullYear()).toBe(2026);
    expect(start?.getMonth()).toBe(7);
    expect(start?.getDate()).toBe(28);
    expect(start?.getHours()).toBe(0);
    expect(end?.getDate()).toBe(29);
    expect(end?.getHours()).toBe(0);
    expect(end?.getMinutes()).toBe(0);
  });

  it('omits empty date filters', () => {
    expect(localDateBoundary('', false)).toBeUndefined();
  });
});
