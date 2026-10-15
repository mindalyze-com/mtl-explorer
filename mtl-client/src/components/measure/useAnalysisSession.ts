import { computed, reactive } from 'vue';
import type { CrossingPointsResponseDto, TriggerPoint } from 'x8ing-mtl-api-typescript-fetch';
import { identityKey, isAttempt, type Attempt } from './useSegmentAttempts';

export type DiscoveryMetric = 'speed' | 'time' | 'distance' | 'heartRate';
export const DEFAULT_COMPARISON_METRICS = ['gap', 'speed'];
export const ALL_SEGMENTS = '*';
export const segmentKey = (from: string, to: string) => JSON.stringify([from, to]);
export const visitLabel = (a: Attempt) =>
  `${a.identity.fromZone}${a.identity.fromVisit} → ${a.identity.toZone}${a.identity.toVisit}`;
export const timingSupported = (a: Attempt) => a.identity.timingStatus === 'AVAILABLE' && (a.elapsedSeconds ?? 0) > 0;
export const performanceSupported = (a: Attempt) =>
  a.availability?.performance ? a.availability.performance.state === 'AVAILABLE' : a.distanceMeters != null;
export const attemptSegment = (a: Attempt) => segmentKey(a.identity.fromZone, a.identity.toZone);
export function orderedSegments(result: CrossingPointsResponseDto | null) {
  const pairs = new Map<string, { key: string; from: string; to: string; label: string }>();
  for (const track of Object.values(result?.crossings ?? {})) {
    for (const a of (track.attempts ?? []).filter(isAttempt)) {
      const { fromZone: from, toZone: to } = a.identity;
      pairs.set(segmentKey(from, to), { key: segmentKey(from, to), from, to, label: `${from} → ${to}` });
    }
  }
  const zones = result?.triggerPoints?.map((z) => z.name ?? '') ?? [];
  if (!zones.length) zones.push(...[...new Set([...pairs.values()].flatMap((p) => [p.from, p.to]))].sort());
  const rank = (p: { from: string; to: string }) => {
    const from = zones.indexOf(p.from),
      to = zones.indexOf(p.to);
    if (to === from + 1) return from;
    if (from === to + 1) return zones.length + to;
    return 2 * zones.length + from * zones.length + to;
  };
  return [...pairs.values()].sort((a, b) => rank(a) - rank(b) || a.key.localeCompare(b.key));
}

/** One owner survives discovery, comparison, replay, zone editing and full-track navigation. */
export function useAnalysisSession() {
  const state = reactive({
    zones: [] as TriggerPoint[],
    radius: 0,
    result: null as CrossingPointsResponseDto | null,
    segment: '',
    metric: 'speed' as DiscoveryMetric,
    grouping: 'average' as 'average' | 'attempts',
    sort: 'newest' as 'newest' | 'metricAsc' | 'metricDesc',
    selections: {} as Record<string, Attempt[]>,
    references: {} as Record<string, string | null>,
    view: 'results' as 'results' | 'comparison',
    comparisonView: 'charts' as 'charts' | 'replay',
    focusedRow: '',
    expanded: [] as string[],
    scrollTop: 0,
    focusedAttempt: null as string | null,
    chartMetrics: [...DEFAULT_COMPARISON_METRICS],
    chartAxis: 'distance' as 'distance' | 'time',
    ghosts: true,
    discoveryError: '',
    filtersChanged: false,
    staleSelections: [] as string[],
  });
  const segments = computed(() => orderedSegments(state.result));
  const selected = computed(() => state.selections[state.segment] ?? []);
  function setResults(result: CrossingPointsResponseDto) {
    const all = Object.values(result.crossings ?? {}).flatMap((t) => (t.attempts ?? []).filter(isAttempt));
    state.staleSelections = [];
    for (const [key, previous] of Object.entries(state.selections)) {
      const oldReference = previous.find((a) => a.identity.reference === state.references[key]);
      state.selections[key] = previous.flatMap((old) => {
        const current = all.find((a) => identityKey(a.identity) === identityKey(old.identity));
        if (!current)
          state.staleSelections.push(`Track ${old.identity.trackId} · ${visitLabel(old)}: source changed or expired`);
        return current ? [current] : [];
      });
      state.references[key] =
        state.selections[key].find(
          (a) => oldReference && identityKey(a.identity) === identityKey(oldReference.identity)
        )?.identity.reference ?? null;
      ensureReference(key);
    }
    state.result = result;
    state.discoveryError = '';
    state.filtersChanged = false;
    if (state.segment !== ALL_SEGMENTS && !segments.value.some((s) => s.key === state.segment))
      state.segment = segments.value[0]?.key ?? '';
  }
  function ensureReference(key: string) {
    const attempts = state.selections[key] ?? [];
    if (!attempts.some((a) => a.identity.reference === state.references[key] && timingSupported(a)))
      state.references[key] = attempts.find((a) => timingSupported(a))?.identity.reference ?? null;
  }
  function toggleAttempt(a: Attempt) {
    state.staleSelections = [];
    const key = attemptSegment(a),
      current = state.selections[key] ?? [];
    state.selections[key] = current.some((s) => identityKey(s.identity) === identityKey(a.identity))
      ? current.filter((s) => identityKey(s.identity) !== identityKey(a.identity))
      : [...current, a];
    ensureReference(key);
  }
  function selectAll() {
    state.staleSelections = [];
    state.selections[state.segment] = Object.values(state.result?.crossings ?? {})
      .flatMap((t) => (t.attempts ?? []).filter(isAttempt))
      .filter((a) => attemptSegment(a) === state.segment);
    ensureReference(state.segment);
  }
  function clearSelection() {
    state.staleSelections = [];
    state.selections[state.segment] = [];
    state.references[state.segment] = null;
  }
  function updateComparison(
    patch: Partial<Pick<typeof state, 'comparisonView' | 'focusedAttempt' | 'chartAxis' | 'chartMetrics' | 'ghosts'>>
  ) {
    Object.assign(state, patch);
  }
  function setReference(reference: string | null) {
    state.references[state.segment] = reference;
  }
  return {
    state,
    segments,
    selected,
    setResults,
    toggleAttempt,
    selectAll,
    clearSelection,
    updateComparison,
    setReference,
  };
}
export type AnalysisSession = ReturnType<typeof useAnalysisSession>;
