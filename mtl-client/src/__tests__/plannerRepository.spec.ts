import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  deletePlannedTrack,
  computeRouteStats,
  emptyStats,
  downloadPlannedTrackGpx,
  fetchSidecarStatus,
  savePlannedRoute,
  listPlannedTracks,
} from '@/planner/repositories/plannerRepository';

describe('plannerRepository', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('sends retained geometry to the generated statistics API and preserves missing heights', async () => {
    const expected = { ...emptyStats(), ascentM: 0, descentM: 0.5, legCount: 1 };
    const fetchMock = vi.fn<typeof fetch>(async () => Response.json(expected));
    vi.stubGlobal('fetch', fetchMock);
    const controller = new AbortController();
    const legs = [
      {
        coordinates: [
          [8, 47, 100],
          [8.001, 47, Number.NaN],
          [8.002, 47, 99.5],
        ] as [number, number, number][],
        distanceM: 200,
        durationSec: 60,
        ascentM: 999,
        descentM: 999,
        cached: false,
      },
    ];
    await expect(computeRouteStats(legs, controller.signal)).resolves.toEqual(expected);
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/api/planner/stats'),
      expect.objectContaining({
        method: 'POST',
        signal: controller.signal,
      })
    );
    expect(JSON.parse(fetchMock.mock.calls[0][1]?.body as string)).toEqual([
      {
        ...legs[0],
        coordinates: [
          [8, 47, 100],
          [8.001, 47],
          [8.002, 47, 99.5],
        ],
      },
    ]);
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

  it('passes POIs through the generated save and list APIs for a POI-only plan', async () => {
    const point = {
      id: 'dc77b107-a507-4ead-81ae-3e61c567d73d',
      lat: 47,
      lng: 8,
      name: 'Lunch',
      iconType: 'water' as const,
      notes: 'Meet here',
    };
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(Response.json({ id: 42, name: 'Places', distanceM: 0 }))
      .mockResolvedValueOnce(
        Response.json([{ id: 42, name: 'Places', distanceM: 0, hasRoute: false, pointOfInterestCount: 1 }])
      );
    vi.stubGlobal('fetch', fetchMock);
    await savePlannedRoute({
      name: 'Places',
      profile: 'trekking',
      coordinates: [],
      legs: [],
      stats: emptyStats(),
      waypoints: [],
      pointsOfInterest: [point],
    });
    expect(JSON.parse(fetchMock.mock.calls[0][1]?.body as string)).toMatchObject({
      coordinates: [],
      pointsOfInterest: [point],
    });
    await expect(listPlannedTracks()).resolves.toMatchObject([{ id: 42, hasRoute: false, pointOfInterestCount: 1 }]);
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
