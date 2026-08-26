import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  deletePlannedTrack,
  downloadPlannedTrackGpx,
  fetchSidecarStatus,
} from '@/planner/repositories/plannerRepository';

describe('plannerRepository', () => {
  afterEach(() => {
    vi.restoreAllMocks();
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

  it('bypasses the sidecar status cache when refresh is forced', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(Response.json({ available: false, reason: 'BRouter is starting' }))
      .mockResolvedValueOnce(Response.json({ available: true, brouterRunning: true }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(fetchSidecarStatus()).resolves.toMatchObject({ available: false });
    await expect(fetchSidecarStatus()).resolves.toMatchObject({ available: false });
    expect(fetchMock).toHaveBeenCalledTimes(1);

    await expect(fetchSidecarStatus({ force: true })).resolves.toMatchObject({
      available: true,
      brouterRunning: true,
    });
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('starts a native GPX download synchronously with a safe filename', async () => {
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const download = downloadPlannedTrackGpx(42, ' Morning / route ');

    expect(click).toHaveBeenCalledOnce();
    const anchor = click.mock.instances[0] as HTMLAnchorElement;
    expect(anchor.href).toContain('/api/planner/plans/42/gpx');
    expect(anchor.download).toBe('Morning - route.gpx');
    expect(anchor.isConnected).toBe(false);
    expect(fetchMock).not.toHaveBeenCalled();
    await expect(download).resolves.toBeUndefined();
  });
});
