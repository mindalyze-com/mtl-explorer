import { afterEach, describe, expect, it, vi } from 'vitest';
import { flushPromises } from '@vue/test-utils';
import { usePlannerState } from '@/planner/composables/usePlannerState';
import { computeRoute, computeRouteStats, emptyStats } from '@/planner/repositories/plannerRepository';

vi.mock('@/planner/repositories/plannerRepository', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/planner/repositories/plannerRepository')>();
  return {
    ...actual,
    computeRoute: vi.fn(),
    computeRouteStats: vi.fn(),
    prewarmForBbox: vi.fn(),
  };
});

const computeRouteMock = vi.mocked(computeRoute);
const computeRouteStatsMock = vi.mocked(computeRouteStats);

describe('usePlannerState', () => {
  afterEach(() => {
    vi.resetAllMocks();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it('creates distinct valid POI UUIDs on HTTP origins without randomUUID and preserves them through history', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    vi.stubGlobal('crypto', { getRandomValues: crypto.getRandomValues.bind(crypto) });
    const planner = usePlannerState();
    const first = planner.addPointOfInterest(47, 8)!;
    const second = planner.addPointOfInterest(47.01, 8.01)!;
    const uuidV4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
    expect(first.id).toMatch(uuidV4);
    expect(second.id).toMatch(uuidV4);
    expect(first.id).not.toBe(second.id);
    expect(warn).toHaveBeenCalledTimes(2);
    planner.undo();
    expect(planner.pointsOfInterest.value).toEqual([first]);
    planner.redo();
    expect(planner.pointsOfInterest.value).toEqual([first, second]);
    planner.dispose();
  });

  it('keeps POI edits in document history without rerouting a saved route', () => {
    vi.useFakeTimers();
    const planner = usePlannerState();
    planner.loadPlan({
      waypoints: [
        { lat: 47, lng: 8 },
        { lat: 47.01, lng: 8.01 },
      ],
      coordinates: [
        [8, 47, 100],
        [8.01, 47.01, 110],
      ],
    });
    const geometry = JSON.stringify(planner.routeCoordinates.value);
    const point = planner.addPointOfInterest(47.03, 8.03, { name: 'Waterfall', notes: 'Visit later' })!;
    planner.movePointOfInterest(point.id, 47.04, 8.04);
    planner.updatePointOfInterest({ ...planner.pointsOfInterest.value[0], name: 'Picnic spot' });
    expect(planner.pristineLoaded.value).toBe(true);
    expect(JSON.stringify(planner.routeCoordinates.value)).toBe(geometry);
    expect(computeRouteMock).not.toHaveBeenCalled();
    planner.undo();
    expect(planner.pointsOfInterest.value[0].name).toBe('Waterfall');
    planner.undo();
    expect(planner.pointsOfInterest.value[0].lat).toBe(47.03);
    planner.undo();
    expect(planner.pointsOfInterest.value).toEqual([]);
    planner.redo();
    expect(planner.pointsOfInterest.value[0].id).toBe(point.id);
    expect(JSON.stringify(planner.routeCoordinates.value)).toBe(geometry);
    planner.dispose();
  });

  it('restores stable POI ids and replaces the whole map when loading another plan', () => {
    const planner = usePlannerState();
    const point = { id: '6a3b3c50-dfa7-4d7b-b641-6d8d459c21ec', lat: 47, lng: 8, name: 'Waterfall', notes: 'Later' };
    planner.loadPlan({ pointsOfInterest: [point] });
    expect(planner.pointsOfInterest.value).toEqual([point]);
    expect(planner.routeCoordinates.value).toEqual([]);
    expect(planner.routeReady.value).toBe(false);
    planner.removePointOfInterest(point.id);
    planner.undo();
    expect(planner.pointsOfInterest.value).toEqual([point]);
    planner.loadPlan({ pointsOfInterest: [] });
    expect(planner.pointsOfInterest.value).toEqual([]);
    expect(planner.canUndo.value).toBe(false);
    planner.dispose();
  });

  it('keeps points of interest when clearing only the route and enforces the POI cap', () => {
    vi.useFakeTimers();
    const planner = usePlannerState();
    planner.setViewport(47, 8, 47.1, 8.1);
    planner.addWaypoint(47, 8);
    const point = planner.addPointOfInterest(47.01, 8.01)!;
    planner.setMaxPointsOfInterest(1);
    expect(planner.addPointOfInterest(47.02, 8.02)).toBeNull();
    planner.clearAll();
    expect(planner.waypoints.value).toEqual([]);
    expect(planner.pointsOfInterest.value).toEqual([point]);
    planner.dispose();
  });

  it('loads saved BRouter legs and stats without deriving ascent from the flattened polyline', () => {
    const planner = usePlannerState();

    planner.loadPlan({
      profile: 'trekking',
      distanceM: 1_000,
      waypoints: [
        { lat: 47, lng: 8 },
        { lat: 47.004, lng: 8.004 },
      ],
      coordinates: [
        [8, 47, 100],
        [8.001, 47.001, 101.5],
        [8.002, 47.002, 100.7],
        [8.003, 47.003, 103.2],
      ],
      legs: [
        {
          coordinates: [
            [8, 47, 100],
            [8.001, 47.001, 101.5],
            [8.002, 47.002, 100.7],
            [8.003, 47.003, 103.2],
          ],
          distanceM: 1_234,
          ascentM: 42,
          descentM: 7,
          durationSec: 900,
          cached: false,
        },
      ],
      stats: {
        distanceM: 1_234,
        ascentM: 42,
        descentM: 7,
        durationSec: 900,
        legCount: 1,
        anyLegCached: false,
      },
    });

    expect(planner.legs.value).toHaveLength(1);
    expect(planner.stats.value.ascentM).toBe(42);
    expect(planner.stats.value.descentM).toBe(7);
    expect(planner.stats.value.durationSec).toBe(900);
  });

  it('uses server statistics for geometry-only saved plans', () => {
    const planner = usePlannerState();

    planner.loadPlan({
      profile: 'trekking',
      distanceM: 1_000,
      stats: { distanceM: 1_000, ascentM: 3.2, descentM: 5.2, durationSec: 600, legCount: 1 },
      waypoints: [
        { lat: 47, lng: 8 },
        { lat: 47.004, lng: 8.004 },
      ],
      coordinates: [
        [8, 47, 100],
        [8.001, 47.001, 101.5],
        [8.002, 47.002, 100.7],
        [8.003, 47.003, 103.2],
        [8.004, 47.004, 101.3],
        [8.005, 47.005, 98.0],
      ],
    });

    expect(planner.stats.value.ascentM).toBeCloseTo(3.2);
    expect(planner.stats.value.descentM).toBeCloseTo(5.2);
    expect(planner.stats.value.durationSec).toBe(600);
    expect(computeRouteStatsMock).not.toHaveBeenCalled();
  });

  it('returns the inserted waypoint so route-leg drags can keep it selected', () => {
    vi.useFakeTimers();
    const planner = usePlannerState();
    planner.setViewport(47, 8, 47.1, 8.1);
    planner.addWaypoint(47, 8);
    planner.addWaypoint(47.02, 8.02);

    const waypoint = planner.insertWaypoint(0, 47.01, 8.01);

    expect(waypoint).toBeTruthy();
    expect(planner.waypoints.value.map((w) => w.id)).toContain(waypoint?.id);
    expect(planner.waypoints.value[1]).toEqual(waypoint);
  });

  it('rejects impossible insertion indexes', () => {
    vi.useFakeTimers();
    const planner = usePlannerState();
    planner.setViewport(47, 8, 47.1, 8.1);
    planner.addWaypoint(47, 8);
    planner.addWaypoint(47.02, 8.02);
    const originalWaypoints = planner.waypoints.value.map((waypoint) => waypoint.id);

    expect(planner.insertWaypoint(-1, 47.01, 8.01)).toBeNull();
    expect(planner.insertWaypoint(2, 47.01, 8.01)).toBeNull();
    expect(planner.insertWaypoint(0.5, 47.01, 8.01)).toBeNull();
    expect(planner.waypoints.value.map((waypoint) => waypoint.id)).toEqual(originalWaypoints);
  });

  it('maps routing-unavailable route errors to user-facing copy', async () => {
    vi.useFakeTimers();
    computeRouteMock.mockRejectedValueOnce({
      response: { data: { error: 'routing-unavailable', detail: 'BRouter call failed' } },
    });
    const planner = usePlannerState();
    planner.setViewport(47, 8, 47.1, 8.1);
    planner.addWaypoint(47, 8);
    planner.addWaypoint(47.02, 8.02);

    await planner.recomputeNow();

    expect(planner.lastError.value).toBe('Routing service is unavailable. Check the BRouter status, then retry.');
    expect(planner.lastError.value).not.toBe('routing-unavailable');
  });

  it.each(['routing-unavailable', 'routing-failed', 'routing-storage-unavailable', 'routing-timeout'])(
    'preserves the server reason for %s without losing the draft',
    async (error) => {
      vi.useFakeTimers();
      const detail =
        'Routing data storage is missing on the server. BRouter: segment directory /segments4 does not exist';
      computeRouteMock.mockRejectedValueOnce({ response: { data: { error, detail } } });
      const planner = usePlannerState();
      planner.setViewport(47, 8, 47.1, 8.1);
      planner.addWaypoint(47, 8);
      planner.addWaypoint(47.02, 8.02);
      await planner.recomputeNow();
      expect(planner.lastError.value).toBe(detail);
      expect(planner.waypoints.value).toHaveLength(2);
      expect(planner.routeReady.value).toBe(false);
      planner.dispose();
    }
  );

  it('keeps segment-downloading route errors actionable while auto-retry is pending', async () => {
    vi.useFakeTimers();
    const infoSpy = vi.spyOn(console, 'info').mockImplementation(() => {});
    computeRouteMock.mockRejectedValueOnce({
      response: {
        data: {
          error: 'segment-downloading',
          detail: 'Routing data for this area is being downloaded. Please retry in about 30 seconds.',
        },
      },
    });
    const planner = usePlannerState();
    planner.setViewport(47, 8, 47.1, 8.1);
    planner.addWaypoint(47, 8);
    planner.addWaypoint(47.02, 8.02);

    await planner.recomputeNow();

    expect(planner.lastError.value).toBe(
      'Routing data for this area is being downloaded. Please retry in about 30 seconds. (auto-retry 1/6)'
    );
    expect(planner.lastError.value).not.toBe('segment-downloading');
    infoSpy.mockRestore();
  });

  it('clears segment-downloading route errors when clearing the route', async () => {
    vi.useFakeTimers();
    const infoSpy = vi.spyOn(console, 'info').mockImplementation(() => {});
    computeRouteMock.mockRejectedValueOnce({
      response: {
        data: {
          error: 'segment-downloading',
          detail: 'Routing data for this area is being downloaded. Please retry in about 30 seconds.',
        },
      },
    });
    const planner = usePlannerState();
    planner.setViewport(47, 8, 47.1, 8.1);
    planner.addWaypoint(47, 8);
    planner.addWaypoint(47.02, 8.02);
    await planner.recomputeNow();

    planner.clearAll();

    expect(planner.waypoints.value).toEqual([]);
    expect(planner.lastError.value).toBeNull();
    expect(planner.computing.value).toBe(false);
    infoSpy.mockRestore();
  });
  function loadedRoute() {
    const state = usePlannerState();
    const coordinates = [
      [8, 47, 100],
      [8.01, 47.01, 130],
      [8.02, 47.02, 120],
    ];
    state.loadPlan({
      profile: 'trekking',
      coordinates,
      waypoints: coordinates.map(([lng, lat]) => ({ lng, lat })),
      legs: [
        { coordinates: coordinates.slice(0, 2), distanceM: 900, ascentM: 30, descentM: 0, durationSec: 600 },
        { coordinates: coordinates.slice(1), distanceM: 800, ascentM: 0, descentM: 10, durationSec: 500 },
      ],
      stats: { distanceM: 1700, ascentM: 30, descentM: 10, durationSec: 1100, legCount: 2 },
    });
    return state;
  }

  it('reverses through the server and restores the exact saved geometry and profile with undo/redo', () => {
    vi.useFakeTimers();
    const state = loadedRoute();
    const coordinates = state.routeCoordinates.value.map((point) => [...point]);
    state.reverseRoute();
    expect(state.waypoints.value.map((point) => point.lng)).toEqual([8.02, 8.01, 8]);
    expect(state.routeReady.value).toBe(false);
    expect(state.computing.value).toBe(true);
    state.undo();
    expect(state.waypoints.value.map((point) => point.lng)).toEqual([8, 8.01, 8.02]);
    expect(state.routeCoordinates.value).toEqual(coordinates);
    expect(state.pristineLoaded.value).toBe(true);
    expect(state.routeReady.value).toBe(true);
    state.setProfile('fastbike');
    state.undo();
    expect(state.profile.value).toBe('trekking');
    expect(state.routeCoordinates.value).toEqual(coordinates);
    state.redo();
    expect(state.profile.value).toBe('fastbike');
    expect(state.routeReady.value).toBe(false);
    state.dispose();
  });

  it.each(['before', 'after'] as const)('trims %s a point using server statistics and supports undo', async (side) => {
    computeRouteStatsMock.mockResolvedValueOnce({
      ...emptyStats(),
      distanceM: side === 'before' ? 800 : 900,
      ascentM: side === 'before' ? 0 : 30,
      descentM: side === 'before' ? 10 : 0,
      legCount: 1,
    });
    const state = loadedRoute();
    const original = state.routeCoordinates.value.map((point) => [...point]);
    state.trimRoute(state.waypoints.value[1].id, side);
    expect(state.routeReady.value).toBe(false);
    await flushPromises();
    expect(state.waypoints.value).toHaveLength(2);
    expect(state.legs.value).toHaveLength(1);
    expect(state.stats.value.distanceM).toBe(side === 'before' ? 800 : 900);
    expect(state.stats.value.ascentM).toBe(side === 'before' ? 0 : 30);
    expect(state.routeReady.value).toBe(true);
    expect(computeRouteMock).not.toHaveBeenCalled();
    expect(computeRouteStatsMock).toHaveBeenCalledWith(state.legs.value, expect.any(AbortSignal));
    state.undo();
    expect(state.routeCoordinates.value).toEqual(original);
    expect(state.stats.value.distanceM).toBe(1700);
    state.dispose();
  });

  it('does not trim into an empty or single-point route', () => {
    const state = loadedRoute();
    state.trimRoute(state.waypoints.value[0].id, 'after');
    state.trimRoute(state.waypoints.value[2].id, 'before');
    state.trimRoute('missing', 'before');
    expect(state.waypoints.value).toHaveLength(3);
    expect(state.canUndo.value).toBe(false);
    state.dispose();
  });

  it('requests missing saved statistics without re-routing the saved geometry', async () => {
    computeRouteStatsMock.mockResolvedValueOnce({ ...emptyStats(), ascentM: 0, descentM: 0.5, legCount: 1 });
    const state = usePlannerState();
    const coordinates = [
      [8, 47, 100],
      [8.001, 47, 99.5],
    ];
    state.loadPlan({
      coordinates,
      waypoints: [
        { lng: 8, lat: 47 },
        { lng: 8.001, lat: 47 },
      ],
    });
    expect(state.routeReady.value).toBe(false);
    await flushPromises();
    expect(state.stats.value.descentM).toBe(0.5);
    expect(state.routeCoordinates.value).toEqual(coordinates);
    expect(state.routeReady.value).toBe(true);
    expect(state.pristineLoaded.value).toBe(true);
    expect(computeRouteMock).not.toHaveBeenCalled();
    state.dispose();
  });

  it('retries failed trimmed statistics without changing retained geometry', async () => {
    computeRouteStatsMock.mockRejectedValueOnce(new Error('offline'));
    const state = loadedRoute();
    state.trimRoute(state.waypoints.value[1].id, 'before');
    const retained = state.routeCoordinates.value;
    await flushPromises();
    expect(state.routeReady.value).toBe(false);
    expect(state.lastError.value).toContain('statistics');
    computeRouteStatsMock.mockResolvedValueOnce({ ...emptyStats(), distanceM: 800, descentM: 10, legCount: 1 });
    await state.recomputeNow();
    expect(state.routeCoordinates.value).toEqual(retained);
    expect(state.routeReady.value).toBe(true);
    expect(computeRouteMock).not.toHaveBeenCalled();
    state.dispose();
  });

  it.each(['undo', 'clearAll'] as const)('ignores late trimmed statistics after %s', async (action) => {
    let resolve!: (value: Awaited<ReturnType<typeof computeRouteStats>>) => void;
    computeRouteStatsMock.mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done;
        })
    );
    const state = loadedRoute();
    state.trimRoute(state.waypoints.value[1].id, 'before');
    state[action]();
    resolve({ ...emptyStats(), distanceM: 800, descentM: 999, legCount: 1 });
    await flushPromises();
    expect(state.stats.value.distanceM).toBe(action === 'undo' ? 1700 : 0);
    expect(state.stats.value.descentM).not.toBe(999);
    expect(computeRouteMock).not.toHaveBeenCalled();
    state.dispose();
  });

  it('restarts statistics after redoing a trim whose first request was cancelled', async () => {
    computeRouteStatsMock.mockImplementationOnce(() => new Promise(() => {}));
    const state = loadedRoute();
    state.trimRoute(state.waypoints.value[1].id, 'before');
    state.undo();
    computeRouteStatsMock.mockResolvedValueOnce({ ...emptyStats(), distanceM: 800, descentM: 10, legCount: 1 });
    state.redo();
    await flushPromises();
    expect(state.routeReady.value).toBe(true);
    expect(state.stats.value.distanceM).toBe(800);
    expect(computeRouteStatsMock).toHaveBeenCalledTimes(2);
    expect(computeRouteMock).not.toHaveBeenCalled();
    state.dispose();
  });

  it('invalidates pending routes immediately, including when only one point remains', async () => {
    vi.useFakeTimers();
    const state = loadedRoute();
    let resolve!: (value: Awaited<ReturnType<typeof computeRoute>>) => void;
    computeRouteMock.mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done;
        })
    );
    const request = state.recomputeNow();
    state.removeWaypoint(state.waypoints.value[2].id);
    state.removeWaypoint(state.waypoints.value[1].id);
    resolve({
      legs: [
        {
          coordinates: [
            [8, 47, 100],
            [8.02, 47.02, 120],
          ],
          distanceM: 1700,
          ascentM: 30,
          descentM: 10,
          durationSec: 1000,
          cached: false,
        },
      ],
      stats: { distanceM: 1700, ascentM: 30, descentM: 10, durationSec: 1000, legCount: 1, anyLegCached: false },
    });
    await request;
    expect(state.routeCoordinates.value).toEqual([]);
    expect(state.routeReady.value).toBe(false);
    expect(state.computing.value).toBe(false);
    state.dispose();
  });

  it('loading a saved plan cancels the old draft debounce and in-flight route', async () => {
    vi.useFakeTimers();
    const state = loadedRoute();
    let resolve!: (value: Awaited<ReturnType<typeof computeRoute>>) => void;
    computeRouteMock.mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done;
        })
    );
    const request = state.recomputeNow();
    state.reverseRoute();
    state.loadPlan({
      profile: 'fastbike',
      waypoints: [
        { lat: 46, lng: 7 },
        { lat: 46.1, lng: 7.1 },
      ],
      coordinates: [
        [7, 46, 100],
        [7.1, 46.1, 200],
      ],
      distanceM: 3000,
      stats: { distanceM: 3000, ascentM: 100, descentM: 0, legCount: 1 },
    });
    resolve({
      legs: [],
      stats: { distanceM: 0, ascentM: 0, descentM: 0, durationSec: 0, legCount: 0, anyLegCached: false },
    });
    await request;
    await vi.advanceTimersByTimeAsync(200);
    expect(computeRouteMock).toHaveBeenCalledTimes(1);
    expect(state.routeReady.value).toBe(true);
    expect(state.profile.value).toBe('fastbike');
    expect(state.routeCoordinates.value[0]).toEqual([7, 46, 100]);
    state.dispose();
  });
});
