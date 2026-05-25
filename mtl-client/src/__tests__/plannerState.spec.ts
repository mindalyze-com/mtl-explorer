import { describe, expect, it } from 'vitest';
import { usePlannerState } from '@/planner/composables/usePlannerState';

describe('usePlannerState', () => {
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
});
