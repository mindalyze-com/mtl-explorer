import type { LegResult } from '@/planner/types';

export type RouteCoordinate = [number, number, number];
export type ScreenPoint = { x: number; y: number };
export type ProjectRouteCoordinate = (coordinate: RouteCoordinate) => ScreenPoint;

export function routeLegCoordinateGroups(legs: Pick<LegResult, 'coordinates'>[]): RouteCoordinate[][] {
  return legs.map((leg) => leg.coordinates).filter((coordinates) => coordinates.length >= 2);
}

export function nearestRouteLegIndexFromCandidates(
  point: ScreenPoint,
  candidateIndexes: number[],
  routeLegs: RouteCoordinate[][],
  project: ProjectRouteCoordinate,
  maxDistancePx = Number.POSITIVE_INFINITY
): number | null {
  const uniqueCandidateIndexes = Array.from(
    new Set(
      candidateIndexes.filter((legIndex) => Number.isInteger(legIndex) && legIndex >= 0 && legIndex < routeLegs.length)
    )
  );
  if (uniqueCandidateIndexes.length === 0) return null;

  let bestIndex = uniqueCandidateIndexes[0];
  let bestScore = Number.POSITIVE_INFINITY;
  for (const legIndex of uniqueCandidateIndexes) {
    const score = distanceToRouteLegSquared(point, routeLegs[legIndex], project);
    if (score < bestScore) {
      bestScore = score;
      bestIndex = legIndex;
    }
  }
  return bestScore <= maxDistancePx * maxDistancePx ? bestIndex : null;
}

export function distanceToRouteLegSquared(
  point: ScreenPoint,
  coordinates: RouteCoordinate[],
  project: ProjectRouteCoordinate
): number {
  if (coordinates.length < 2) return Number.POSITIVE_INFINITY;
  let bestScore = Number.POSITIVE_INFINITY;
  for (let index = 0; index < coordinates.length - 1; index++) {
    const a = project(coordinates[index]);
    const b = project(coordinates[index + 1]);
    const score = distanceToSegmentSquared(point.x, point.y, a.x, a.y, b.x, b.y);
    if (score < bestScore) bestScore = score;
  }
  return bestScore;
}

function distanceToSegmentSquared(px: number, py: number, ax: number, ay: number, bx: number, by: number): number {
  const abx = bx - ax;
  const aby = by - ay;
  const apx = px - ax;
  const apy = py - ay;
  const ab2 = abx * abx + aby * aby;
  const t = ab2 === 0 ? 0 : Math.max(0, Math.min(1, (apx * abx + apy * aby) / ab2));
  const cx = ax + abx * t;
  const cy = ay + aby * t;
  return (px - cx) * (px - cx) + (py - cy) * (py - cy);
}
