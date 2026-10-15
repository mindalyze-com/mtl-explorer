import { warnRequestFailure } from '@/utils/safeLogging';
import { computed, onBeforeUnmount, ref, watch, type Ref } from 'vue';
import pLimit from 'p-limit';
import type {
  CrossingPointsResponseDto,
  SegmentAttemptIdentity,
  SegmentAttemptSummary,
  SegmentCoreAnalysis,
  SegmentHealthAnalysis,
  SegmentComparison,
  GpsTrack,
} from 'x8ing-mtl-api-typescript-fetch';
import {
  fetchSegmentAnalysis,
  fetchSegmentHealth,
  fetchSegmentComparison,
  type SegmentGraphSettings,
} from './segmentAnalysisApi';
import { TRACK_DETAILS_CHART_POINTS_DEFAULT } from '@/utils/trackDetailsChartPointSettings';

const REQUEST_CONCURRENCY = 4;
const GRAPH_SETTINGS_DEBOUNCE_MS = 350;
export type Attempt = SegmentAttemptSummary & {
  identity: SegmentAttemptIdentity &
    Required<
      Pick<
        SegmentAttemptIdentity,
        'reference' | 'trackId' | 'revision' | 'fromZone' | 'toZone' | 'fromVisit' | 'toVisit'
      >
    >;
};
export type AttemptData = {
  attempt: Attempt;
  track?: GpsTrack;
  core?: SegmentCoreAnalysis;
  health?: SegmentHealthAnalysis;
  comparison?: SegmentComparison;
  loading: boolean;
  healthLoading: boolean;
  comparisonLoading: boolean;
  error?: string;
  healthError?: string;
  comparisonError?: string;
  healthRequest: number;
};
export function isAttempt(attempt: SegmentAttemptSummary): attempt is Attempt {
  const i = attempt.identity;
  return (
    !!i?.reference &&
    i.trackId != null &&
    !!i.revision &&
    !!i.fromZone &&
    !!i.toZone &&
    i.fromVisit != null &&
    i.toVisit != null
  );
}
export function identityKey(identity?: SegmentAttemptIdentity): string {
  if (!identity) return '';
  return JSON.stringify([
    identity.trackId,
    identity.revision,
    identity.zoneRevision,
    identity.fromZone,
    identity.fromVisit,
    identity.toZone,
    identity.toVisit,
    identity.start,
    identity.end,
    identity.startPosition,
    identity.endPosition,
    identity.startCoordinate,
    identity.endCoordinate,
    identity.timingStatus,
  ]);
}
export function assertAttemptIdentity(expected: SegmentAttemptIdentity, actual?: SegmentAttemptIdentity) {
  if (expected.reference !== actual?.reference || identityKey(expected) !== identityKey(actual))
    throw new Error('The response belongs to another attempt. Refresh the results.');
}
export async function runSegmentRequests<T>(entries: T[], work: (entry: T) => Promise<void>) {
  let next = 0;
  await Promise.all(
    Array.from({ length: Math.min(REQUEST_CONCURRENCY, entries.length) }, async () => {
      while (next < entries.length) await work(entries[next++]);
    })
  );
}
function message(error: unknown) {
  const status = (error as { response?: { status?: number } })?.response?.status;
  return status === 410
    ? 'This attempt expired or the track changed. Refresh the segment results.'
    : error instanceof Error && error.message.includes('another attempt')
      ? error.message
      : 'Could not load this result. Retry.';
}

/** Loads exactly the session selection. Discovery, comparison and replay never infer visits. */
export function useSegmentAttempts(
  props: {
    measureServiceResult: CrossingPointsResponseDto;
    selectedAttempts: Attempt[];
    referenceAttempt?: string | null;
  },
  graphSettings?: Readonly<Ref<SegmentGraphSettings>>
) {
  const settings = () =>
    graphSettings?.value ?? { pointCount: TRACK_DETAILS_CHART_POINTS_DEFAULT, axis: 'distance' as const };
  let graphReloadTimer: ReturnType<typeof setTimeout> | undefined;
  const data = ref<AttemptData[]>([]);
  const reference = ref<string | null>(null);
  let generation = 0;
  let coreAbort = new AbortController();
  let comparisonAbort = new AbortController();
  let comparisonGeneration = 0;
  let comparisonReference: string | null = null;
  let comparisonRequests = pLimit(REQUEST_CONCURRENCY);
  const selected = computed(() =>
    props.selectedAttempts.map((attempt) => ({
      attempt,
      track: props.measureServiceResult.crossings?.[attempt.identity.trackId]?.gpsTrack,
    }))
  );
  const available = computed(() => data.value.filter((d) => d.core && !d.error));
  const validReferences = computed(() =>
    available.value.filter(
      (d) =>
        (d.core?.summary?.elapsedSeconds ?? 0) > 0 &&
        d.core?.summary?.distanceMeters != null &&
        d.attempt.identity.timingStatus === 'AVAILABLE'
    )
  );
  function validateReference() {
    // Selection order defines the default; network completion order never changes it.
    const chosen = data.value.find((d) => d.attempt.identity.reference === reference.value);
    if (chosen && chosen.attempt.identity.timingStatus === 'AVAILABLE') return;
    reference.value =
      data.value.find((d) => d.attempt.identity.timingStatus === 'AVAILABLE')?.attempt.identity.reference ?? null;
  }
  watch(
    () => props.referenceAttempt,
    (value) => {
      if (value !== undefined) reference.value = value;
    },
    { immediate: true }
  );
  async function loadHealth(entry: AttemptData) {
    const current = generation;
    const request = ++entry.healthRequest;
    entry.healthLoading = true;
    entry.healthError = undefined;
    try {
      const result = await fetchSegmentHealth(entry.attempt.identity.reference, coreAbort.signal, settings());
      if (current !== generation || request !== entry.healthRequest) return;
      assertAttemptIdentity(entry.attempt.identity, result.identity);
      entry.health = result;
    } catch (error) {
      if (current === generation && request === entry.healthRequest && !coreAbort.signal.aborted) {
        entry.healthError = message(error);
        warnRequestFailure('Segment health comparison could not be loaded', error, {
          topic1: 'measure',
          topic2: 'useSegmentAttempts',
          topic3: 'health',
        });
      }
    } finally {
      if (current === generation && request === entry.healthRequest) entry.healthLoading = false;
    }
  }
  async function loadCore(entry: AttemptData) {
    const current = generation;
    entry.loading = true;
    entry.error = undefined;
    try {
      const result = await fetchSegmentAnalysis(entry.attempt.identity.reference, coreAbort.signal, settings());
      if (current !== generation) return;
      assertAttemptIdentity(entry.attempt.identity, result.identity);
      assertAttemptIdentity(entry.attempt.identity, result.summary?.identity);
      entry.core = result;
    } catch (error) {
      if (current === generation && !coreAbort.signal.aborted) {
        entry.error = message(error);
        warnRequestFailure('Segment analysis could not be loaded', error, {
          topic1: 'measure',
          topic2: 'useSegmentAttempts',
          topic3: 'analysis',
        });
      }
    } finally {
      if (current === generation) {
        entry.loading = false;
        validateReference();
      }
    }
  }
  async function reload() {
    coreAbort.abort();
    resetComparisons();
    coreAbort = new AbortController();
    const current = ++generation;
    const oldReference = data.value.find((d) => d.attempt.identity.reference === reference.value)?.attempt.identity;
    data.value = selected.value.map((s) => ({
      ...s,
      loading: true,
      healthLoading: false,
      comparisonLoading: false,
      healthRequest: 0,
    }));
    if (oldReference)
      reference.value =
        data.value.find((d) => identityKey(d.attempt.identity) === identityKey(oldReference))?.attempt.identity
          .reference ?? null;
    validateReference();
    // Optional health is loaded only for an inspector, a health column or a heart-rate chart.
    await runSegmentRequests(data.value, (entry) => (current === generation ? loadCore(entry) : Promise.resolve()));
  }
  watch(
    () => selected.value.map((s) => s.attempt.identity.reference).join('|'),
    () => {
      void reload();
    },
    { immediate: true }
  );
  async function reloadCharts() {
    coreAbort.abort();
    coreAbort = new AbortController();
    const current = ++generation;
    const health = data.value.filter((entry) => entry.health || entry.healthLoading || entry.healthError);
    for (const entry of data.value) {
      entry.healthRequest++;
      entry.healthLoading = false;
    }
    // Keep comparison, selection and pinned positions; these do not depend on display density.
    await Promise.all([
      runSegmentRequests(data.value, (entry) => (current === generation ? loadCore(entry) : Promise.resolve())),
      runSegmentRequests(health, (entry) => (current === generation ? loadHealth(entry) : Promise.resolve())),
    ]);
  }
  if (graphSettings)
    watch(
      () => [graphSettings.value.pointCount, graphSettings.value.axis],
      () => {
        clearTimeout(graphReloadTimer);
        graphReloadTimer = setTimeout(() => {
          void reloadCharts();
        }, GRAPH_SETTINGS_DEBOUNCE_MS);
      }
    );
  function setReference(selectedReference: string) {
    if (!validReferences.value.some((d) => d.attempt.identity.reference === selectedReference)) return;
    reference.value = selectedReference;
  }
  function resetComparisons() {
    comparisonAbort.abort();
    comparisonAbort = new AbortController();
    comparisonGeneration++;
    comparisonReference = null;
    comparisonRequests = pLimit(REQUEST_CONCURRENCY);
    for (const entry of data.value) {
      entry.comparison = undefined;
      entry.comparisonError = undefined;
      entry.comparisonLoading = false;
    }
  }
  async function loadComparisons(retryErrors = false) {
    const ref = validReferences.value.find((d) => d.attempt.identity.reference === reference.value);
    if (comparisonReference !== reference.value) resetComparisons();
    comparisonReference = reference.value;
    if (!ref) return;
    const current = comparisonGeneration;
    const signal = comparisonAbort.signal;
    const pending = available.value.filter(
      (entry) => !entry.comparison && !entry.comparisonLoading && (retryErrors || !entry.comparisonError)
    );
    // Mark queued requests too, so later core responses cannot enqueue them again.
    for (const entry of pending) {
      entry.comparisonLoading = true;
      entry.comparisonError = undefined;
    }
    await Promise.all(
      pending.map((entry) =>
        comparisonRequests(async () => {
          if (current !== comparisonGeneration || signal.aborted) return;
          try {
            const result = await fetchSegmentComparison(
              entry.attempt.identity.reference,
              ref.attempt.identity.reference,
              signal
            );
            if (current !== comparisonGeneration || signal.aborted) return;
            assertAttemptIdentity(entry.attempt.identity, result.identity);
            assertAttemptIdentity(ref.attempt.identity, result.referenceIdentity);
            entry.comparison = result;
          } catch (error) {
            if (current === comparisonGeneration && !signal.aborted) {
              entry.comparisonError = message(error);
              warnRequestFailure('Segment comparison could not be loaded', error, {
                topic1: 'measure',
                topic2: 'useSegmentAttempts',
                topic3: 'comparison',
              });
            }
          } finally {
            if (current === comparisonGeneration && !signal.aborted) entry.comparisonLoading = false;
          }
        })
      )
    );
  }
  async function compare() {
    await loadComparisons(true);
  }
  watch(
    () => [reference.value, available.value.map((d) => d.attempt.identity.reference).join('|')],
    () => {
      void loadComparisons();
    }
  );
  onBeforeUnmount(() => {
    clearTimeout(graphReloadTimer);
    generation++;
    comparisonGeneration++;
    coreAbort.abort();
    comparisonAbort.abort();
  });
  return {
    data,
    available,
    validReferences,
    reference,
    setReference,
    reload,
    loadCore,
    loadHealth,
    compare,
  };
}
