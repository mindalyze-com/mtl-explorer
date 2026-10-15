import { mapTargetKey, type MapInteractionSourceState, type MapTargetCandidate } from './mapInteractionDecision';

export const MAP_TARGET_CATEGORY_PAGE_SIZE = 20;
export const MAP_TARGET_OVERVIEW_ROW_BUDGET = 10;
export const MAP_TARGET_CATEGORY_PREVIEW_LIMIT = 3;

type CategoryDescriptor = { key: string; label: string; shortLabel: string; icon: string };
export type MapTargetCategory = CategoryDescriptor & {
  candidates: MapTargetCandidate[];
  sources: MapInteractionSourceState[];
};

const CATEGORIES: Record<string, CategoryDescriptor> = {
  track: { key: 'track', label: 'Your GPS tracks', shortLabel: 'GPS tracks', icon: 'bi-signpost-split' },
  'track-point': { key: 'track-point', label: 'Track points', shortLabel: 'Points', icon: 'bi-geo-alt' },
  photo: { key: 'photo', label: 'Photos', shortLabel: 'Photos', icon: 'bi-images' },
  poi: { key: 'poi', label: 'Places', shortLabel: 'Places', icon: 'bi-pin-map' },
  waymarked: { key: 'waymarked', label: 'Waymarked routes', shortLabel: 'Waymarked', icon: 'bi-signpost-split' },
  swiss: { key: 'swiss', label: 'Swiss routes', shortLabel: 'Swiss routes', icon: 'bi-map' },
};
const CATEGORY_ORDER = Object.keys(CATEGORIES);

function externalCategory(source: string, label: string): CategoryDescriptor {
  if (source.startsWith('waymarked:')) return CATEGORIES.waymarked;
  if (source === 'routes' || source.startsWith('ch.')) return CATEGORIES.swiss;
  return { key: `source:${source}`, label, shortLabel: label, icon: 'bi-map' };
}

/** Presentation groups only; original candidate identities and provider registrations stay intact. */
export function buildMapTargetCategories(
  candidates: readonly MapTargetCandidate[],
  sources: readonly MapInteractionSourceState[]
): MapTargetCategory[] {
  const groups = new Map<string, MapTargetCategory>();
  function group(descriptor: CategoryDescriptor): MapTargetCategory {
    let current = groups.get(descriptor.key);
    if (!current) {
      current = { ...descriptor, candidates: [], sources: [] };
      groups.set(descriptor.key, current);
    }
    return current;
  }
  for (const candidate of candidates) {
    const descriptor =
      candidate.kind === 'overlay'
        ? externalCategory(candidate.source, candidate.sourceLabel || 'Trail overlay')
        : CATEGORIES[candidate.kind];
    group(descriptor).candidates.push(candidate);
  }
  for (const source of sources) {
    const descriptor =
      source.kind === 'tracks'
        ? CATEGORIES.track
        : source.id === 'photos'
          ? CATEGORIES.photo
          : source.id === 'points'
            ? CATEGORIES['track-point']
            : source.id === 'pois'
              ? CATEGORIES.poi
              : externalCategory(source.id, source.label);
    group(descriptor).sources.push(source);
  }
  return [...groups.values()].sort((a, b) => {
    const left = CATEGORY_ORDER.indexOf(a.key);
    const right = CATEGORY_ORDER.indexOf(b.key);
    return (left < 0 ? CATEGORY_ORDER.length : left) - (right < 0 ? CATEGORY_ORDER.length : right);
  });
}

export function mapCategoryCount(category: MapTargetCategory): string {
  const pending = mapCategoryPending(category);
  if (category.candidates.length) return `${category.candidates.length.toLocaleString()}${pending ? '+' : ''}`;
  if (pending) return '…';
  return category.sources.some((source) => source.status !== 'complete') ? '—' : '0';
}

export function mapCategoryPending(category: MapTargetCategory): boolean {
  return category.sources.some((source) => source.status === 'pending');
}

export function mapCategoryHasFailure(category: MapTargetCategory): boolean {
  return category.sources.some((source) => source.status !== 'complete' && source.status !== 'pending');
}

export function mapCategoryPreview(category: MapTargetCategory): string {
  if (category.candidates.length) {
    if (category.key === 'photo') {
      if (category.candidates.length > 1) return `${category.candidates.length.toLocaleString()} collections`;
      const count = category.candidates.reduce(
        (total, candidate) => total + (candidate.kind === 'photo' ? candidate.count : 0),
        0
      );
      return `${category.candidates.length.toLocaleString()} ${category.candidates.length === 1 ? 'collection' : 'collections'} · ${count.toLocaleString()} ${count === 1 ? 'photo' : 'photos'}`;
    }
    return category.candidates[0].label;
  }
  if (category.sources.some((source) => source.status === 'pending')) return 'Searching…';
  if (category.sources.some((source) => source.status === 'failed' || source.status === 'timed-out'))
    return 'Lookup unavailable';
  if (category.sources.some((source) => source.status === 'cancelled')) return 'Lookup cancelled';
  return 'No matches at this location';
}

/** GPS rows use physical distance, then canonical recency; other targets use screen distance. */
export function compareMapCategoryCandidates(a: MapTargetCandidate, b: MapTargetCandidate): number {
  if (a.kind === 'track' && b.kind === 'track') {
    const validMeters = (value: number | undefined) => Number.isFinite(value) && value! >= 0;
    const useMeters = validMeters(a.distanceMeters) || validMeters(b.distanceMeters);
    const leftValue = useMeters ? a.distanceMeters : a.distancePx;
    const rightValue = useMeters ? b.distanceMeters : b.distancePx;
    const leftDistance = validMeters(leftValue) ? leftValue! : Number.POSITIVE_INFINITY;
    const rightDistance = validMeters(rightValue) ? rightValue! : Number.POSITIVE_INFINITY;
    if (leftDistance !== rightDistance) return leftDistance - rightDistance;
    const leftDate = Number.isFinite(a.recordedAt) ? a.recordedAt! : Number.NEGATIVE_INFINITY;
    const rightDate = Number.isFinite(b.recordedAt) ? b.recordedAt! : Number.NEGATIVE_INFINITY;
    return leftDate === rightDate ? 0 : rightDate - leftDate;
  }
  // Category allocation compares different kinds too. A GPS distance in meters
  // cannot be compared with another target's distance in pixels.
  if (a.kind === 'track' || b.kind === 'track') return 0;
  const leftDistance = Number.isFinite(a.distancePx) ? a.distancePx! : Number.POSITIVE_INFINITY;
  const rightDistance = Number.isFinite(b.distancePx) ? b.distancePx! : Number.POSITIVE_INFINITY;
  return leftDistance === rightDistance ? 0 : leftDistance - rightDistance;
}

export function rankMapCategoryCandidates(candidates: readonly MapTargetCandidate[]): MapTargetCandidate[] {
  return [...candidates].sort(compareMapCategoryCandidates);
}

/** Preserve existing previews; late results use free rows or stay accessible in the category list. */
export function allocateMapOverviewPreviews(
  categories: readonly MapTargetCategory[],
  previous: Readonly<Record<string, readonly string[]>> = {}
): Record<string, string[]> {
  const previews: Record<string, string[]> = {};
  for (const category of categories) {
    const available = new Set(category.candidates.map(mapTargetKey));
    previews[category.key] = (previous[category.key] ?? [])
      .filter((key) => available.has(key))
      .slice(0, MAP_TARGET_CATEGORY_PREVIEW_LIMIT);
  }
  const categoryRows = (category: MapTargetCategory, visible: number) =>
    visible +
    (category.candidates.length > visible ? 1 : 0) +
    (mapCategoryPending(category) || mapCategoryHasFailure(category) ? 1 : 0);
  // Pending and unavailable categories share the row budget with previews and summary rows.
  const rowCount = () =>
    categories.reduce((total, category) => total + categoryRows(category, previews[category.key].length), 0);
  // An unexpected registered source may need one extra row; never evict a visible target to hide it.
  const budget = Math.max(MAP_TARGET_OVERVIEW_ROW_BUDGET, rowCount());
  const appendNext = (category: MapTargetCategory): boolean => {
    const visible = previews[category.key];
    if (visible.length >= MAP_TARGET_CATEGORY_PREVIEW_LIMIT) return false;
    const candidate = category.candidates.find((item) => !visible.includes(mapTargetKey(item)));
    if (!candidate) return false;
    const additionalRows = categoryRows(category, visible.length + 1) - categoryRows(category, visible.length);
    if (rowCount() + additionalRows > budget) return false;
    visible.push(mapTargetKey(candidate));
    return true;
  };
  // Give each category a direct target before spending spare rows on another category.
  for (const category of categories) {
    if (!previews[category.key].length) appendNext(category);
  }
  while (true) {
    const next = categories
      .filter((category) => previews[category.key].length < MAP_TARGET_CATEGORY_PREVIEW_LIMIT)
      .map((category) => ({
        category,
        candidate: category.candidates.find((item) => !previews[category.key].includes(mapTargetKey(item))),
      }))
      .filter((entry) => entry.candidate)
      .sort((a, b) => compareMapCategoryCandidates(a.candidate!, b.candidate!));
    if (!next.some(({ category }) => appendNext(category))) break;
  }
  return previews;
}

export function mapCategoryMoreLabel(category: MapTargetCategory, previewCount: number): string {
  const nouns: Record<string, string> = {
    track: 'GPS tracks',
    'track-point': 'track points',
    photo: 'photo collections',
    poi: 'places',
    waymarked: 'Waymarked routes',
    swiss: 'Swiss routes',
  };
  const noun = nouns[category.key] ?? category.shortLabel;
  return previewCount
    ? `+${(category.candidates.length - previewCount).toLocaleString()} more ${noun}`
    : `${category.candidates.length.toLocaleString()} ${noun} nearby`;
}
