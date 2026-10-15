import { describe, expect, it } from 'vitest';
import {
  decideMapInteraction,
  mapAlternativeCount,
  mapInteractionTolerance,
  mapTargetKey,
  mergeMapTargets,
  MAP_POINTER_TOLERANCE_PX,
  MAP_TOUCH_TOLERANCE_PX,
  type MapInteractionDecisionInput,
  type MapTargetCandidate,
} from '@/components/map/mapInteractionDecision';
import { TrackProcessingStatusDtoStateEnum, type TrackProcessingStatusDto } from 'x8ing-mtl-api-typescript-fetch';

const track = (trackId: number): MapTargetCandidate => ({
  id: String(trackId),
  source: 'tracks',
  kind: 'track',
  trackId,
  label: `Track ${trackId}`,
});
const point: MapTargetCandidate = {
  id: '1:4',
  source: 'track-points',
  kind: 'track-point',
  trackId: 1,
  pointIndex: 4,
  label: 'Point 5',
};
const photo: MapTargetCandidate = { id: '4', source: 'media', kind: 'photo', mediaId: 4, count: 1, label: 'Photo' };
const poi: MapTargetCandidate = {
  id: '18',
  source: 'pois',
  kind: 'poi',
  coordinates: [8, 47],
  properties: { name: 'Spring' },
  label: 'Spring',
};
const overlay: MapTargetCandidate = {
  id: '3',
  source: 'wanderwege',
  kind: 'overlay',
  label: 'Trail',
  route: { name: 'Trail', type: 'Trail', icon: 'bi-map' },
};
const pending = { id: 'tracks', label: 'Your tracks', kind: 'tracks', status: 'pending' } as const;
const input = (
  candidates: MapTargetCandidate[],
  overrides: Partial<MapInteractionDecisionInput> = {}
): MapInteractionDecisionInput => ({
  candidates,
  sources: [],
  presentation: 'chooser',
  selected: null,
  processing: null,
  ...overrides,
});

describe('map interaction decisions', () => {
  it.each([photo, poi, point, track(1), overlay])('offers $kind in All while discovery is pending', (candidate) => {
    expect(decideMapInteraction(input([candidate], { sources: [pending] }))).toEqual({ action: 'choose' });
    expect(decideMapInteraction(input([candidate], { sources: [pending], presentation: 'chooser' }))).toEqual({
      action: 'choose',
    });
  });

  it('chooses before opening an arbitrary overlapping local symbol', () => {
    expect(decideMapInteraction(input([photo, poi], { sources: [pending] }))).toEqual({ action: 'choose' });
  });

  it('offers choices for several tracks', () => {
    expect(decideMapInteraction(input([track(1), track(2)]))).toEqual({ action: 'choose' });
  });

  it.each([photo, poi, point, track(1), overlay])('keeps a final lone $kind result in All', (candidate) => {
    expect(decideMapInteraction(input([candidate]))).toEqual({ action: 'choose' });
    expect(
      decideMapInteraction(
        input([candidate], { sources: [{ ...pending, status: 'complete' }], presentation: 'chooser' })
      )
    ).toEqual({
      action: 'choose',
    });
  });

  it('retains explicit selection and return navigation as results arrive', () => {
    expect(decideMapInteraction(input([photo, overlay], { selected: photo, presentation: 'target' }))).toEqual({
      action: 'update',
    });
    expect(decideMapInteraction(input([photo], { selected: photo, presentation: 'chooser' }))).toEqual({
      action: 'update',
    });
  });

  it('keeps All open while an empty lookup is pending or complete', () => {
    expect(decideMapInteraction(input([]))).toEqual({ action: 'choose' });
    expect(decideMapInteraction(input([], { sources: [pending] }))).toEqual({ action: 'choose' });
    expect(decideMapInteraction(input([], { sources: [{ ...pending, status: 'complete' }] }))).toEqual({
      action: 'choose',
    });
  });

  it.each(['failed', 'timed-out', 'cancelled'] as const)('retains All for an empty %s lookup', (status) => {
    expect(decideMapInteraction(input([], { sources: [{ ...pending, status }] }))).toEqual({ action: 'choose' });
  });

  it('preserves the existing indexing notice even when no track was hit', () => {
    const processing = { state: TrackProcessingStatusDtoStateEnum.Processing } as TrackProcessingStatusDto;
    expect(decideMapInteraction(input([], { processing }))).toEqual({ action: 'choose' });
    expect(decideMapInteraction(input([track(1)], { processing }))).toEqual({ action: 'choose' });
  });
});

describe('semantic map candidates and matching', () => {
  it('deduplicates rendered copies within their source without collapsing different objects or sources', () => {
    const duplicate = { ...overlay, label: 'TRAIL' };
    const anotherSource = { ...overlay, source: 'wanderland' };
    expect(mergeMapTargets([], [overlay, duplicate, anotherSource])).toHaveLength(2);
    expect(mergeMapTargets([], [track(1), track(2)])).toHaveLength(2);
  });

  it('does not turn the track-point parent into an overlap choice', () => {
    expect(mergeMapTargets([], [track(1), point])).toEqual([point]);
    expect(mergeMapTargets([point], [track(1), track(2)])).toEqual([point, track(2)]);
    expect(decideMapInteraction(input([point, track(1)]))).toEqual({ action: 'choose' });
    expect(mapAlternativeCount([point, track(1), track(2), track(2)], point)).toBe(1);
  });

  it('keeps each cluster as one counted target before its leaves are available', () => {
    const cluster: MapTargetCandidate = {
      id: 'cluster:6',
      source: 'media',
      kind: 'photo',
      clusterId: 6,
      count: 250,
      label: '250 photos',
    };
    expect(mergeMapTargets([], [cluster, { ...cluster }])).toEqual([cluster]);
    expect(mapAlternativeCount([cluster, track(1)], cluster)).toBe(1);
  });

  it.each(['media:9', 'media-cluster:17'])(
    'keeps the direct symbol collection %s before the identified viewport scope',
    (id) => {
      const primary: MapTargetCandidate = { ...photo, id, collectionScope: 'primary', match: 'symbol' };
      const viewport: MapTargetCandidate = {
        ...photo,
        id: 'media-viewport',
        collectionScope: 'viewport',
        label: 'Map view at tap',
        match: 'identified',
      };
      expect(mergeMapTargets([], [viewport, primary])).toEqual([primary, viewport]);
      expect(mergeMapTargets([], [primary, viewport, { ...viewport }])).toEqual([primary, viewport]);
    }
  );

  it('sorts each incoming batch consistently and appends without moving established rows', () => {
    const first = mergeMapTargets([], [poi, photo, point, track(2), track(1)]);
    expect(first).toEqual([point, photo, poi, track(2)]);
    expect(mergeMapTargets([overlay], [poi, photo, point])).toEqual([overlay, point, photo, poi]);
    expect(mergeMapTargets([], [poi, photo])).toEqual(mergeMapTargets([], [photo, poi]));
  });

  it('constructs collision-free identities and uses actual touch input only for touch tolerance', () => {
    expect(mapTargetKey({ ...photo, source: 'a:b', id: 'c' })).not.toBe(
      mapTargetKey({ ...photo, source: 'a', id: 'b:c' })
    );
    expect(mapInteractionTolerance('pointer')).toBe(MAP_POINTER_TOLERANCE_PX);
    expect(mapInteractionTolerance('pen')).toBe(MAP_POINTER_TOLERANCE_PX);
    expect(mapInteractionTolerance('touch')).toBe(MAP_TOUCH_TOLERANCE_PX);
  });
});
