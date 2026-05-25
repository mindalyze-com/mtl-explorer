import { describe, expect, it } from 'vitest';
import {
  nearestRouteLegIndexFromCandidates,
  routeLegCoordinateGroups,
  type RouteCoordinate,
} from '@/planner/utils/routeHitTesting';
import type { LegResult } from '@/planner/types';

function leg(coordinates: RouteCoordinate[]): Pick<LegResult, 'coordinates'> {
  return { coordinates };
}

const project = (coordinate: RouteCoordinate) => ({ x: coordinate[0], y: coordinate[1] });

describe('routeHitTesting', () => {
  it('keeps routed legs as separate route feature coordinate groups', () => {
    const groups = routeLegCoordinateGroups([
      leg([
        [0, 0, 0],
        [5, 0, 0],
      ]),
      leg([
        [5, 0, 0],
        [5, 5, 0],
      ]),
    ]);

    expect(groups).toEqual([
      [
        [0, 0, 0],
        [5, 0, 0],
      ],
      [
        [5, 0, 0],
        [5, 5, 0],
      ],
    ]);
  });

  it('does not invent straight route segments before routed geometry exists', () => {
    const groups = routeLegCoordinateGroups([]);

    expect(groups).toEqual([]);
  });

  it('chooses the closest candidate route leg in screen space', () => {
    const routeLegs = [
      [
        [0, 0, 0],
        [10, 0, 0],
      ],
      [
        [0, 5, 0],
        [10, 5, 0],
      ],
    ];

    const selected = nearestRouteLegIndexFromCandidates({ x: 6, y: 4.6 }, [0, 1], routeLegs, project);

    expect(selected).toBe(1);
  });

  it('rejects candidate route legs outside the final insert radius', () => {
    const routeLegs = [
      [
        [0, 0, 0],
        [10, 0, 0],
      ],
    ];

    expect(nearestRouteLegIndexFromCandidates({ x: 5, y: 7 }, [0], routeLegs, project, 8)).toBe(0);
    expect(nearestRouteLegIndexFromCandidates({ x: 5, y: 9 }, [0], routeLegs, project, 8)).toBeNull();
  });

  it('ignores duplicate and invalid candidate legs', () => {
    const routeLegs = [
      [
        [0, 0, 0],
        [10, 0, 0],
      ],
      [
        [0, 5, 0],
        [10, 5, 0],
      ],
    ];

    expect(nearestRouteLegIndexFromCandidates({ x: 6, y: 4.6 }, [99, 1, 1], routeLegs, project)).toBe(1);
    expect(nearestRouteLegIndexFromCandidates({ x: 6, y: 4.6 }, [99], routeLegs, project)).toBeNull();
  });
});
