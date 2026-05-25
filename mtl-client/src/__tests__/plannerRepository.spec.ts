import { afterEach, describe, expect, it, vi } from 'vitest';
import { deletePlannedTrack } from '@/planner/repositories/plannerRepository';

describe('plannerRepository', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('treats a no-content delete response as success', async () => {
    const fetchMock = vi.fn(async () => new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(deletePlannedTrack(42)).resolves.toBeUndefined();

    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/api/planner/plans/42'),
      expect.objectContaining({ method: 'DELETE' })
    );
  });
});
