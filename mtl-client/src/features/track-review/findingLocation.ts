import type { TrackFinding, TrackObservation } from 'x8ing-mtl-api-typescript-fetch';

export const FINDING_CONTEXT_POINTS = 5;
export const FINDING_MAP_SPAN_METRES = 50;

export interface FindingMapFocus {
  finding: TrackFinding;
  points: TrackObservation[];
  pointIndex: number;
}

// Grouped findings report the first occurrence; the last index can belong to a separate issue.
export function findingPointRange(finding: TrackFinding) {
  const first = finding.firstPointIndex;
  if (first == null) return null;
  let before = first,
    after = first;
  if (finding.reason === 'RECORDING_GAP') after = first + 1;
  else if ((finding.reason === 'TIME_BACKWARD' || finding.reason === 'TIME_DUPLICATE') && first > 0) before = first - 1;
  else if (
    finding.reason === 'POSITION_JUMP' &&
    (finding.affectedCount ?? 1) === 1 &&
    finding.lastPointIndex != null &&
    finding.lastPointIndex > first
  )
    after = finding.lastPointIndex;
  return { before, after };
}

export function belongsToFindingSection(point: TrackObservation, finding: TrackFinding) {
  return finding.sectionIndex == null || finding.sectionIndex < 0 || point.sectionIndex === finding.sectionIndex;
}

export function hasMapPosition(point: TrackObservation) {
  return (
    point.latitude != null &&
    point.longitude != null &&
    Number.isFinite(point.latitude) &&
    Number.isFinite(point.longitude) &&
    Math.abs(point.latitude) <= 90 &&
    Math.abs(point.longitude) <= 180
  );
}
