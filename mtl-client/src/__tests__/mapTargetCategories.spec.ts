import { describe, expect, it } from 'vitest';
import { mapTargetKey, type MapTargetCandidate } from '@/components/map/mapInteractionDecision';
import {
  allocateMapOverviewPreviews,
  buildMapTargetCategories,
  MAP_TARGET_CATEGORY_PREVIEW_LIMIT,
  MAP_TARGET_OVERVIEW_ROW_BUDGET,
  mapCategoryCount,
  mapCategoryHasFailure,
  mapCategoryPending,
  mapCategoryPreview,
  compareMapCategoryCandidates,
  rankMapCategoryCandidates,
  type MapTargetCategory,
} from '@/components/map/mapTargetCategories';

const track = (id: number): Extract<MapTargetCandidate, { kind: 'track' }> => ({
  id: String(id),
  kind: 'track',
  source: 'tracks',
  label: `Track ${id}`,
  trackId: id,
});

describe('GPS candidate internal distance ranking', () => {
  it('ranks physical GPS distances first and uses canonical recency for ties', () => {
    const oldNear = { ...track(1), distanceMeters: 2, distancePx: 1, recordedAt: Date.UTC(2026, 8, 1) };
    const recentNear = { ...track(2), distanceMeters: 2, distancePx: 99, recordedAt: Date.UTC(2026, 9, 1) };
    const far = { ...track(3), distanceMeters: 8, distancePx: 0, recordedAt: Date.UTC(2026, 9, 11) };
    const unknown = { ...track(4), distancePx: 0, recordedAt: Date.UTC(2026, 9, 12) };
    expect(rankMapCategoryCandidates([far, unknown, oldNear, recentNear]).map((candidate) => candidate.id)).toEqual([
      '2',
      '1',
      '3',
      '4',
    ]);
  });

  it('uses screen-distance and recency fallback for GPS peers without physical facts', () => {
    const first = { ...track(1), distancePx: 2, recordedAt: 1 };
    const second = { ...track(2), distancePx: 2, recordedAt: 2 };
    const missing = { ...track(3), distanceMeters: Number.NaN, recordedAt: 3 };
    expect(rankMapCategoryCandidates([missing, first, second]).map((candidate) => candidate.id)).toEqual([
      '2',
      '1',
      '3',
    ]);
  });

  it('uses canonical recency when every GPS distance is unavailable', () => {
    const first = { ...track(5), recordedAt: Date.UTC(2025, 0, 1) };
    const second = { ...track(6), recordedAt: Date.UTC(2026, 0, 1) };
    expect(rankMapCategoryCandidates([first, second]).map((candidate) => candidate.id)).toEqual(['6', '5']);
  });

  it('never compares GPS meters against another kind screen pixels', () => {
    const gps = { ...track(1), distanceMeters: 1, distancePx: 999 };
    const media = { ...photo, distancePx: 0 };
    expect(compareMapCategoryCandidates(gps, media)).toBe(0);
    expect(compareMapCategoryCandidates(media, gps)).toBe(0);
    const overlay = { ...route(1), distancePx: 20 };
    expect(rankMapCategoryCandidates([overlay, media]).map((candidate) => candidate.kind)).toEqual([
      'photo',
      'overlay',
    ]);
  });
});
const route = (id: number): MapTargetCandidate => ({
  id: String(id),
  kind: 'overlay',
  source: 'waymarked:hiking',
  label: `Route ${id}`,
  route: { name: `Route ${id}`, type: 'Hiking', icon: 'bi-map' },
});
const photo: MapTargetCandidate = {
  id: '1',
  kind: 'photo',
  source: 'media',
  label: 'Photos',
  count: 3,
  mediaId: 1,
};
function overviewRowCount(categories: MapTargetCategory[], previews: Record<string, string[]>) {
  return categories.reduce(
    (total, category) =>
      total +
      previews[category.key].length +
      (category.candidates.length > previews[category.key].length ? 1 : 0) +
      (mapCategoryPending(category) || mapCategoryHasFailure(category) ? 1 : 0),
    0
  );
}

describe('map target category progress and row allocation', () => {
  it('counts overlapping photo collection choices without adding their photo totals', () => {
    const cluster: MapTargetCandidate = { ...photo, id: 'cluster', label: 'This cluster', count: 5 };
    const viewport: MapTargetCandidate = { ...photo, id: 'viewport', label: 'Map view at tap', count: 20 };
    const [category] = buildMapTargetCategories([cluster, viewport], []);
    expect(mapCategoryCount(category)).toBe('2');
    expect(mapCategoryPreview(category)).toBe('2 collections');
    expect(mapCategoryPreview({ ...category, candidates: [cluster] })).toBe('1 collection · 5 photos');
  });

  it('keeps unknown pending counts and lookup failures distinct from completed zero', () => {
    const [category] = buildMapTargetCategories(
      [],
      [{ id: 'tracks', label: 'Your tracks', kind: 'tracks', status: 'pending' }]
    );
    expect(mapCategoryCount(category)).toBe('…');
    expect(mapCategoryPending(category)).toBe(true);
    expect(mapCategoryHasFailure(category)).toBe(false);
    const known = { ...category, candidates: [track(1)] };
    expect(mapCategoryCount(known)).toBe('1+');
    for (const status of ['failed', 'timed-out', 'cancelled'] as const) {
      const failed = { ...category, sources: [{ ...category.sources[0], status }] };
      expect(mapCategoryCount(failed)).toBe('—');
      expect(mapCategoryHasFailure(failed)).toBe(true);
      expect(mapCategoryPending(failed)).toBe(false);
    }
    expect(mapCategoryCount({ ...category, sources: [{ ...category.sources[0], status: 'complete' }] })).toBe('0');
  });

  it('limits all category previews to three, including retained previews', () => {
    const categories = buildMapTargetCategories(
      Array.from({ length: 8 }, (_, id) => track(id)),
      []
    );
    const oldPreviews = { track: categories[0].candidates.slice(0, 4).map(mapTargetKey) };
    expect(allocateMapOverviewPreviews(categories, oldPreviews).track).toEqual(oldPreviews.track.slice(0, 3));
    const routes = buildMapTargetCategories(
      Array.from({ length: 8 }, (_, id) => route(id)),
      []
    );
    const oldRoutes = { waymarked: routes[0].candidates.slice(0, 4).map(mapTargetKey) };
    expect(allocateMapOverviewPreviews(routes).waymarked).toHaveLength(3);
    expect(allocateMapOverviewPreviews(routes, oldRoutes).waymarked).toEqual(oldRoutes.waymarked.slice(0, 3));
  });

  it('budgets one pending skeleton independently of each category preview and more row', () => {
    const categories = buildMapTargetCategories(
      [...Array.from({ length: 19 }, (_, id) => track(id)), ...Array.from({ length: 19 }, (_, id) => route(id)), photo],
      [
        { id: 'tracks', label: 'Your tracks', kind: 'tracks', status: 'pending' },
        { id: 'waymarked:hiking', label: 'Waymarked hiking', kind: 'overlay', status: 'pending' },
      ]
    );
    const previews = allocateMapOverviewPreviews(categories);
    expect(overviewRowCount(categories, previews)).toBe(MAP_TARGET_OVERVIEW_ROW_BUDGET);
    for (const category of categories) {
      expect(previews[category.key].length).toBeGreaterThan(0);
      expect(previews[category.key].length).toBeLessThanOrEqual(MAP_TARGET_CATEGORY_PREVIEW_LIMIT);
    }
    expect(previews.photo).toEqual([mapTargetKey(photo)]);
  });

  it('retains visible preview identities as progress completes and later matches arrive', () => {
    const sources = [{ id: 'tracks', label: 'Your tracks', kind: 'tracks' as const, status: 'pending' as const }];
    const candidates = [
      ...Array.from({ length: 9 }, (_, id) => track(id)),
      ...Array.from({ length: 6 }, (_, id) => route(id)),
      photo,
    ];
    const before = buildMapTargetCategories(candidates, sources);
    const first = allocateMapOverviewPreviews(before);
    const after = buildMapTargetCategories(
      [...candidates, { ...track(99), distancePx: 0 }],
      [{ ...sources[0], status: 'complete' }]
    );
    const next = allocateMapOverviewPreviews(after, first);
    for (const category of before)
      expect(next[category.key].slice(0, first[category.key].length)).toEqual(first[category.key]);
    expect(overviewRowCount(after, next)).toBeLessThanOrEqual(MAP_TARGET_OVERVIEW_ROW_BUDGET);
  });
});
