import type { TrackProcessingStatusDto } from 'x8ing-mtl-api-typescript-fetch';
import type { SwissMobilityRoute } from './composables/mapControllerRuntime';

export type MapInteractionInputKind = 'pointer' | 'touch' | 'pen';
export const MAP_POINTER_TOLERANCE_PX = 16;
export const MAP_TOUCH_TOLERANCE_PX = 24;

export function mapInteractionTolerance(inputKind: MapInteractionInputKind): number {
  return inputKind === 'touch' ? MAP_TOUCH_TOLERANCE_PX : MAP_POINTER_TOLERANCE_PX;
}

type MapTargetBase = {
  /** Stable semantic identity within the source, independent of tile or rendered layer. */
  id: string;
  source: string;
  sourceLabel?: string;
  label: string;
  detail?: string;
  match?: 'symbol' | 'line' | 'identified';
  distancePx?: number;
  /** Canonical recording time in epoch milliseconds; never a parsed display date. */
  recordedAt?: number;
};

export type MapTargetCandidate = MapTargetBase &
  (
    | {
        kind: 'track';
        trackId: number;
        activityType?: string;
        description?: string;
        matchedMediaCount?: number;
        distanceMeters?: number;
        /** Full track length, distinct from distance to the clicked location. */
        lengthMeters?: number;
        ascentMeters?: number;
      }
    | { kind: 'track-point'; trackId: number; pointIndex: number }
    | {
        kind: 'photo';
        mediaId?: number;
        clusterId?: number;
        count: number;
        collectionScope?: 'primary' | 'viewport';
        viewportBounds?: [[number, number], [number, number]];
        sourceGeneration?: number;
      }
    | { kind: 'poi'; coordinates: [number, number]; properties: Record<string, unknown> }
    | { kind: 'overlay'; route: SwissMobilityRoute }
  );

export type MapInteractionPresentation = 'chooser' | 'target';
export type MapInteractionSourceStatus = 'pending' | 'complete' | 'failed' | 'timed-out' | 'cancelled';
export type MapInteractionSourceState = {
  id: string;
  label: string;
  kind: 'tracks' | 'overlay' | 'local';
  status: MapInteractionSourceStatus;
  /** Deliberate, safe guidance supplied by a provider; never a raw remote error. */
  message?: string;
};

export type MapInteractionDecisionInput = {
  candidates: readonly MapTargetCandidate[];
  sources: readonly MapInteractionSourceState[];
  selected: MapTargetCandidate | null;
  presentation: MapInteractionPresentation;
  /** Set only when the existing track processing notice needs to be shown. */
  processing: TrackProcessingStatusDto | null;
};

export type MapInteractionDecision = { action: 'choose' | 'update' };

export function mapTargetKey(candidate: MapTargetCandidate): string {
  return JSON.stringify([candidate.source, candidate.kind, candidate.id]);
}

const KIND_ORDER: Record<MapTargetCandidate['kind'], number> = {
  'track-point': 0,
  photo: 1,
  poi: 2,
  track: 3,
  overlay: 4,
};
const MATCH_ORDER = { symbol: 0, line: 1, identified: 2 } as const;

function compareCandidates(left: MapTargetCandidate, right: MapTargetCandidate): number {
  const leftMatch =
    MATCH_ORDER[left.match ?? (left.kind === 'track' ? 'line' : left.kind === 'overlay' ? 'identified' : 'symbol')];
  const rightMatch =
    MATCH_ORDER[right.match ?? (right.kind === 'track' ? 'line' : right.kind === 'overlay' ? 'identified' : 'symbol')];
  return (
    leftMatch - rightMatch ||
    KIND_ORDER[left.kind] - KIND_ORDER[right.kind] ||
    (left.distancePx ?? 0) - (right.distancePx ?? 0) ||
    mapTargetKey(left).localeCompare(mapTargetKey(right))
  );
}

/** Append each source's results deterministically without moving already visible rows. */
export function mergeMapTargets(
  current: readonly MapTargetCandidate[],
  incoming: readonly MapTargetCandidate[]
): MapTargetCandidate[] {
  const keys = new Set(current.map(mapTargetKey));
  const merged = [...current];
  for (const candidate of [...incoming].sort(compareCandidates)) {
    const key = mapTargetKey(candidate);
    if (keys.has(key)) continue;
    keys.add(key);
    merged.push(candidate);
  }
  const parentTracks = new Set(
    merged.filter((candidate) => candidate.kind === 'track-point').map((candidate) => candidate.trackId)
  );
  // A point's parent track is an action of that point, not an overlapping target.
  return merged.filter((candidate) => candidate.kind !== 'track' || !parentTracks.has(candidate.trackId));
}

export function mapAlternativeCount(
  candidates: readonly MapTargetCandidate[],
  selected: MapTargetCandidate | null
): number {
  const selectedKey = selected ? mapTargetKey(selected) : null;
  return mergeMapTargets([], candidates).filter((candidate) => mapTargetKey(candidate) !== selectedKey).length;
}

/** Every click starts in All; only explicit selection opens a target. */
export function decideMapInteraction(input: MapInteractionDecisionInput): MapInteractionDecision {
  return { action: input.selected ? 'update' : 'choose' };
}
