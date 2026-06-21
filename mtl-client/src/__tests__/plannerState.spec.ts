import { afterEach, describe, expect, it, vi } from 'vitest';
import { usePlannerState } from '@/planner/composables/usePlannerState';
import { computeRoute } from '@/planner/repositories/plannerRepository';

vi.mock('@/planner/repositories/plannerRepository', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/planner/repositories/plannerRepository')>();
  return {
    ...actual,
    computeRoute: vi.fn(),
    prewarmForBbox: vi.fn(),
  };
});

const computeRouteMock = vi.mocked(computeRoute);

describe('usePlannerState', () => {
  afterEach(() => {
    vi.clearAllMocks();
    vi.useRealTimers();
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

  it('ignores small elevation wiggles when deriving stats for a loaded saved plan', () => {
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
        [8.004, 47.004, 101.3],
        [8.005, 47.005, 98.0],
      ],
    });

    expect(planner.stats.value.ascentM).toBeCloseTo(2.5);
    expect(planner.stats.value.descentM).toBeCloseTo(3.3);
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

    expect(planner.lastError.value).toBe(
      'Route unavailable for these points. Move waypoints onto routable roads or trails, or try again later.'
    );
    expect(planner.lastError.value).not.toBe('routing-unavailable');
  });

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
});
