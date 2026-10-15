import { warnRequestFailure } from '@/utils/safeLogging';
import { defineStore } from 'pinia';
import { computed, onScopeDispose, reactive, ref, shallowRef } from 'vue';
import {
  ApplicationStatusControllerApi,
  GetApplicationStatusIncludeEnum,
  type ApplicationStatusResponseDto,
  type DataFreshnessResponseDto,
  type GHStatusResponse,
  type ProcessingStatusDto,
  type ServicesStatusDto,
  MapConfigDtoTileModeEnum,
} from 'x8ing-mtl-api-typescript-fetch';
import { getApiConfiguration } from '@/utils/openApiClient';
import { STATUS_POLL_INTERVAL_MS } from '@/utils/statusPolling';
import { STARTUP_REQUEST_TIMEOUT_MS } from '@/utils/startupTiming';
import { buildAdminOperationalTasks } from '@/utils/adminOperationalTasks';
import {
  GOOGLE_HEALTH_COUNT_KEY,
  observeCountAnimation,
  processingCountKey,
  type CountAnimation,
  type CountAnimationOptions,
} from '@/utils/countAnimation';

export const APPLICATION_STATUS_SECTION = {
  FRESHNESS: GetApplicationStatusIncludeEnum.Freshness,
  PROCESSING: GetApplicationStatusIncludeEnum.Processing,
  SERVICES: GetApplicationStatusIncludeEnum.Services,
  GOOGLE_HEALTH: GetApplicationStatusIncludeEnum.GoogleHealth,
} as const;

export type ApplicationStatusSection = (typeof APPLICATION_STATUS_SECTION)[keyof typeof APPLICATION_STATUS_SECTION];
/** One poll contains every demanded section, with a minimum eight-second cadence. */
export type ApplicationStatusPollingDemand = Partial<Record<ApplicationStatusSection, number>>;

export interface ApplicationStatusSectionState {
  lastAttemptAt: number | null;
  lastSuccessAt: number | null;
  lastError: string | null;
  healthy: boolean;
  loading: boolean;
}

export interface ApplicationStatusPollingConsumer {
  update(demand: ApplicationStatusPollingDemand): void;
  dispose(): void;
}

export interface ApplicationStatusRefreshOptions {
  immediate?: boolean;
}

export type ApplicationStatusPollingMode = 'idle' | 'scheduled' | 'polling' | 'paused' | 'backoff';

const ALL_SECTIONS = Object.values(APPLICATION_STATUS_SECTION);
const INITIAL_NETWORK_BACKOFF_MS = STATUS_POLL_INTERVAL_MS;
const MAX_NETWORK_BACKOFF_MS = 30_000;
const MAX_TIMER_DELAY_MS = 2_147_483_647;
const INDEXER_COUNT_FIELDS = ['completed', 'pending', 'failed', 'removed', 'excluded', 'total'] as const;
const JOB_COUNT_FIELDS = ['done', 'pending', 'failed', 'unavailable', 'total'] as const;

function createSectionState(): ApplicationStatusSectionState {
  return {
    lastAttemptAt: null,
    lastSuccessAt: null,
    lastError: null,
    healthy: true,
    loading: false,
  };
}

function createSectionStates(): Record<ApplicationStatusSection, ApplicationStatusSectionState> {
  return {
    [APPLICATION_STATUS_SECTION.FRESHNESS]: createSectionState(),
    [APPLICATION_STATUS_SECTION.PROCESSING]: createSectionState(),
    [APPLICATION_STATUS_SECTION.SERVICES]: createSectionState(),
    [APPLICATION_STATUS_SECTION.GOOGLE_HEALTH]: createSectionState(),
  };
}

function errorMessage(error: unknown): string {
  return error instanceof Error && error.message ? error.message : 'Status request failed.';
}

export const useApplicationStatusStore = defineStore('applicationStatus', () => {
  const freshness = shallowRef<DataFreshnessResponseDto | null>(null);
  const processing = shallowRef<ProcessingStatusDto | null>(null);
  const services = shallowRef<ServicesStatusDto | null>(null);
  const googleHealth = shallowRef<GHStatusResponse | null>(null);
  const countAnimations = reactive(new Map<string, CountAnimation>());
  const sectionStates = reactive(createSectionStates());
  const serverGeneratedAt = ref<number | null>(null);
  /** Browser time minus server time, shared by progress playback and job countdowns. */
  const statusClockOffsetMs = ref<number | null>(null);
  const inFlightSections = ref<ApplicationStatusSection[]>([]);
  const nextPollAt = ref<number | null>(null);
  const pollingMode = ref<ApplicationStatusPollingMode>('idle');
  const paused = ref(false);
  const backoffUntil = ref<number | null>(null);
  const consecutiveNetworkFailures = ref(0);

  const hasActiveProcessing = computed(
    () =>
      processing.value?.indexers?.some((summary) => (summary.pending ?? 0) > 0) === true ||
      processing.value?.jobs?.some((summary) => (summary.pending ?? 0) > 0) === true
  );

  let issueMutationRevision = 0;

  function applyIssueSummary(summary: import('x8ing-mtl-api-typescript-fetch').SourceIssueSummary) {
    issueMutationRevision++;
    if (!processing.value) return;
    processing.value = {
      ...processing.value,
      indexers: processing.value.indexers?.map((index) => {
        const updated = summary.indexes?.find((value) => value.index === index.index);
        return {
          ...index,
          pendingIssueFileCount: updated?.pendingFileCount ?? 0,
          acknowledgedIssueFileCount: updated?.acknowledgedFileCount ?? 0,
        };
      }),
    };
  }

  let timerId: ReturnType<typeof setTimeout> | null = null;
  let abortController: AbortController | null = null;
  let requestTimeoutId: ReturnType<typeof setTimeout> | null = null;
  let requestGeneration = 0;
  let requestPromise: Promise<void> | null = null;
  let pendingPromise: Promise<void> | null = null;
  let resolvePending: (() => void) | null = null;
  let pendingRefreshImmediate = false;
  let lastCompletedAt: number | null = null;
  let visibilityListening = false;
  let disposed = false;
  const consumers = new Map<symbol, ApplicationStatusPollingDemand>();
  const pendingSections = new Set<ApplicationStatusSection>();

  function recordCount(
    key: string,
    value: number | null | undefined,
    sampledAt: number,
    options: CountAnimationOptions = {}
  ) {
    const animation = observeCountAnimation(countAnimations.get(key), value, sampledAt, {
      ...options,
      observedAt: Date.now(),
    });
    if (animation) countAnimations.set(key, animation);
    else countAnimations.delete(key);
  }

  function recordProcessingCounts(status: ProcessingStatusDto, sampledAt: number) {
    const keys = new Set<string>();
    for (const summary of status.indexers ?? []) {
      for (const field of INDEXER_COUNT_FIELDS) {
        const key = processingCountKey('indexer', summary.index, field);
        keys.add(key);
        recordCount(key, summary[field], sampledAt, {
          animate: summary.discoveryInProgress === true || (summary.pending ?? 0) > 0,
          ...(field === 'completed'
            ? {
                resetOnDecrease: true,
                // A new total or outcome must appear together, without replaying old arithmetic.
                resetKey: JSON.stringify([
                  summary.total,
                  summary.failed,
                  summary.skipped,
                  summary.removed,
                  summary.excluded,
                  summary.discoveryInProgress,
                  summary.pendingIssueFileCount,
                  summary.acknowledgedIssueFileCount,
                  summary.operationalFailed,
                  summary.total === undefined ? null : (summary.completed ?? 0) + (summary.pending ?? 0),
                ]),
              }
            : {}),
        });
      }
    }
    for (const summary of status.jobs ?? []) {
      for (const field of JOB_COUNT_FIELDS) {
        const key = processingCountKey('job', summary.job, field);
        keys.add(key);
        recordCount(key, summary[field], sampledAt, {
          animate: (summary.pending ?? 0) > 0,
          ...(field === 'done'
            ? {
                resetOnDecrease: true,
                resetKey: JSON.stringify([
                  summary.total,
                  summary.failed,
                  summary.unavailable,
                  summary.attentionRequired,
                  summary.state,
                  summary.progressKnown,
                  summary.unit,
                  summary.work?.waiting,
                  summary.work?.retrying,
                  summary.blockedBy,
                  summary.total === undefined ? null : (summary.done ?? 0) + (summary.pending ?? 0),
                  summary.work ? (summary.work.ready ?? 0) - (summary.pending ?? 0) : null,
                ]),
              }
            : {}),
        });
      }
    }
    for (const key of countAnimations.keys()) {
      if ((key.startsWith('indexer:') || key.startsWith('job:')) && !keys.has(key)) countAnimations.delete(key);
    }
  }

  function recordServiceCounts(status: ServicesStatusDto, sampledAt: number) {
    const tasks = buildAdminOperationalTasks({
      mapConfig: {
        tileMode: status.tileMode as MapConfigDtoTileModeEnum | undefined,
        plannerEnabled: status.plannerEnabled,
      },
      mapStatus: status.map,
      locationSearchStatus: status.locationSearch,
      brouterStatus: status.planner,
    });
    const keys = new Set<string>();
    for (const task of tasks) {
      for (const count of task.metricCounts ?? []) {
        const key = processingCountKey('task', task.id, count.key);
        keys.add(key);
        recordCount(key, count.value, sampledAt, { animate: task.active });
      }
    }
    for (const key of countAnimations.keys()) {
      if (key.startsWith('task:') && !keys.has(key)) countAnimations.delete(key);
    }
  }

  function applyValue(
    section: ApplicationStatusSection,
    response: ApplicationStatusResponseDto,
    sampledAt: number
  ): boolean {
    switch (section) {
      case APPLICATION_STATUS_SECTION.FRESHNESS:
        if (!response.freshness) return false;
        freshness.value = response.freshness;
        return true;
      case APPLICATION_STATUS_SECTION.PROCESSING:
        if (!response.processing) return false;
        recordProcessingCounts(response.processing, sampledAt);
        processing.value = {
          ...response.processing,
          dependencies:
            response.processing.dependencies ??
            (response.processing.dependenciesVersion === processing.value?.dependenciesVersion
              ? processing.value?.dependencies
              : undefined),
        };
        return true;
      case APPLICATION_STATUS_SECTION.SERVICES:
        if (!response.services) return false;
        recordServiceCounts(response.services, sampledAt);
        services.value = response.services;
        return true;
      case APPLICATION_STATUS_SECTION.GOOGLE_HEALTH: {
        if (!response.googleHealth) return false;
        const job = response.googleHealth.job;
        // An idle coordinator has no sync count; its zero is not a progress sample.
        recordCount(GOOGLE_HEALTH_COUNT_KEY, job?.state === 'IDLE' ? undefined : job?.recordsProcessed, sampledAt, {
          resetKey: job?.syncId,
          resetOnDecrease: true,
          animate: ['QUEUED', 'RUNNING'].includes(job?.state ?? ''),
        });
        googleHealth.value = response.googleHealth;
        return true;
      }
    }
  }

  function hasDemand(section: ApplicationStatusSection): boolean {
    for (const demand of consumers.values()) {
      if ((demand[section] ?? 0) > 0) return true;
    }
    return false;
  }

  function effectiveInterval(): number {
    let interval: number | null = null;
    for (const demand of consumers.values()) {
      for (const candidate of Object.values(demand)) {
        if (candidate && candidate > 0) interval = interval === null ? candidate : Math.min(interval, candidate);
      }
    }
    if (pendingSections.size > 0 || (hasDemand(APPLICATION_STATUS_SECTION.PROCESSING) && hasActiveProcessing.value)) {
      interval = Math.min(interval ?? STATUS_POLL_INTERVAL_MS, STATUS_POLL_INTERVAL_MS);
    }
    return Math.max(interval ?? STATUS_POLL_INTERVAL_MS, STATUS_POLL_INTERVAL_MS);
  }

  function clearTimer(): void {
    if (timerId === null) return;
    clearTimeout(timerId);
    timerId = null;
  }

  function isPageHidden(): boolean {
    return typeof document !== 'undefined' && document.visibilityState === 'hidden';
  }

  function requestedSections(): ApplicationStatusSection[] {
    return ALL_SECTIONS.filter((section) => hasDemand(section) || pendingSections.has(section));
  }

  function schedule(): void {
    clearTimer();
    if (disposed) {
      nextPollAt.value = null;
      pollingMode.value = 'idle';
      return;
    }
    if (requestPromise !== null) {
      nextPollAt.value = null;
      pollingMode.value = 'polling';
      return;
    }
    if (isPageHidden() && pendingSections.size === 0) {
      paused.value = true;
      nextPollAt.value = null;
      pollingMode.value = 'paused';
      return;
    }

    paused.value = false;
    const now = Date.now();
    if (requestedSections().length === 0) {
      nextPollAt.value = null;
      pollingMode.value = 'idle';
      return;
    }
    // Scheduled consumers share one deadline. An interactive refresh may move
    // the next request to now, while still joining or queueing behind in-flight work.
    const nextCadenceAt = lastCompletedAt === null ? now : lastCompletedAt + effectiveInterval();
    const scheduledAt = Math.max(pendingRefreshImmediate ? now : nextCadenceAt, backoffUntil.value ?? 0);
    nextPollAt.value = scheduledAt;
    pollingMode.value = backoffUntil.value !== null && backoffUntil.value > now ? 'backoff' : 'scheduled';
    timerId = setTimeout(runScheduledPoll, Math.min(Math.max(0, scheduledAt - now), MAX_TIMER_DELAY_MS));
  }

  function runScheduledPoll(): void {
    timerId = null;
    if (disposed || (isPageHidden() && pendingSections.size === 0)) {
      schedule();
      return;
    }
    const now = Date.now();
    if (backoffUntil.value !== null && backoffUntil.value > now) {
      schedule();
      return;
    }
    const sections = requestedSections();
    if (sections.length === 0) {
      schedule();
      return;
    }
    const resolve = resolvePending;
    pendingRefreshImmediate = false;
    pendingSections.clear();
    pendingPromise = null;
    resolvePending = null;
    void executeRequest(sections).finally(() => {
      resolve?.();
      schedule();
    });
  }

  function markNetworkFailure(sections: ApplicationStatusSection[], error: unknown): void {
    warnRequestFailure('Application status could not be refreshed', error, {
      topic1: 'application',
      topic2: 'applicationStatusStore',
      topic3: 'status',
    });
    const message = errorMessage(error);
    for (const section of sections) {
      const state = sectionStates[section];
      state.healthy = false;
      state.lastError = message;
    }
    consecutiveNetworkFailures.value += 1;
    const delay = Math.min(
      INITIAL_NETWORK_BACKOFF_MS * 2 ** (consecutiveNetworkFailures.value - 1),
      MAX_NETWORK_BACKOFF_MS
    );
    backoffUntil.value = Date.now() + delay;
  }

  function applyResponse(sections: ApplicationStatusSection[], response: ApplicationStatusResponseDto): void {
    const now = Date.now();
    const generatedAt = response.generatedAt?.getTime();
    let sampledAt = now;
    if (generatedAt !== undefined && Number.isFinite(generatedAt)) {
      serverGeneratedAt.value = generatedAt;
      // Keep one clock mapping for every section. Response delays must not move playback time.
      statusClockOffsetMs.value ??= now - generatedAt;
      sampledAt = generatedAt + statusClockOffsetMs.value;
    }
    consecutiveNetworkFailures.value = 0;
    backoffUntil.value = null;
    const errors = new Map(response.errors?.map((error) => [error.section as ApplicationStatusSection, error]));
    const included = response.included ?? new Set<ApplicationStatusSection>();

    for (const section of sections) {
      const state = sectionStates[section];
      const sectionError = errors.get(section);
      if (sectionError) {
        state.healthy = false;
        state.lastError = sectionError.message ?? sectionError.code ?? 'Status section failed.';
        continue;
      }
      if (!included.has(section) || !applyValue(section, response, sampledAt)) {
        state.healthy = false;
        state.lastError = 'Status response did not contain the requested section.';
        continue;
      }
      state.lastSuccessAt = now;
      state.lastError = null;
      state.healthy = true;
    }
  }

  async function executeRequest(sections: ApplicationStatusSection[]): Promise<void> {
    const generation = ++requestGeneration;
    const isCurrent = () => !disposed && generation === requestGeneration;
    const attemptedAt = Date.now();
    const issueRevisionAtRequest = issueMutationRevision;
    for (const section of sections) {
      sectionStates[section].lastAttemptAt = attemptedAt;
      sectionStates[section].loading = true;
    }
    inFlightSections.value = sections;
    pollingMode.value = 'polling';
    const controller = new AbortController();
    abortController = controller;
    let timedOut = false;
    let rejectInterrupted!: (error: unknown) => void;
    const interrupted = new Promise<ApplicationStatusResponseDto>((_resolve, reject) => {
      rejectInterrupted = reject;
    });
    const onAbort = () => rejectInterrupted(controller.signal.reason ?? new Error('Status request cancelled'));
    controller.signal.addEventListener('abort', onAbort, { once: true });
    const timeout = setTimeout(() => {
      timedOut = true;
      controller.abort(new Error('Application status request timed out'));
    }, STARTUP_REQUEST_TIMEOUT_MS);
    requestTimeoutId = timeout;

    try {
      const operation = Promise.resolve().then(() =>
        new ApplicationStatusControllerApi(getApiConfiguration()).getApplicationStatus(
          { include: sections, dependenciesVersion: processing.value?.dependenciesVersion },
          { signal: controller.signal }
        )
      );
      requestPromise = Promise.race([operation, interrupted])
        .then((response) => {
          if (!isCurrent() || controller.signal.aborted) return;
          if (response.processing && issueRevisionAtRequest !== issueMutationRevision) {
            response.processing.indexers = response.processing.indexers?.map((index) => {
              const current = processing.value?.indexers?.find((value) => value.index === index.index);
              return {
                ...index,
                pendingIssueFileCount: current?.pendingIssueFileCount ?? 0,
                acknowledgedIssueFileCount: current?.acknowledgedIssueFileCount ?? 0,
              };
            });
          }
          applyResponse(sections, response);
        })
        .catch((error) => {
          if (!isCurrent() || (controller.signal.aborted && !timedOut)) return;
          markNetworkFailure(sections, error);
        });
      await requestPromise;
    } finally {
      clearTimeout(timeout);
      if (requestTimeoutId === timeout) requestTimeoutId = null;
      controller.signal.removeEventListener('abort', onAbort);
      if (isCurrent()) {
        requestPromise = null;
        abortController = null;
        inFlightSections.value = [];
        lastCompletedAt = Date.now();
        for (const section of sections) {
          sectionStates[section].loading = false;
        }
      }
    }
  }

  function enqueue(sections: Iterable<ApplicationStatusSection>, immediate = false): Promise<void> {
    if (disposed) return Promise.resolve();
    for (const section of sections) pendingSections.add(section);
    if (pendingSections.size === 0) return Promise.resolve();
    if (immediate) pendingRefreshImmediate = true;
    pendingPromise ??= new Promise<void>((resolve) => {
      resolvePending = resolve;
    });
    schedule();
    return pendingPromise;
  }

  function refresh(
    sections: Iterable<ApplicationStatusSection> = ALL_SECTIONS,
    options: ApplicationStatusRefreshOptions = {}
  ): Promise<void> {
    const requested = [...sections];
    if (requestPromise && requested.every((section) => inFlightSections.value.includes(section))) {
      return requestPromise;
    }
    return enqueue(requested, options.immediate);
  }

  function force(section: ApplicationStatusSection): Promise<void> {
    // Mutations require a response started after the change, but still share
    // the next tick instead of issuing a burst alongside an in-flight request.
    return enqueue([section]);
  }

  function updateConsumer(id: symbol, demand: ApplicationStatusPollingDemand): void {
    if (!consumers.has(id)) return;
    consumers.set(id, { ...demand });
    schedule();
  }

  function registerPollingConsumer(demand: ApplicationStatusPollingDemand): ApplicationStatusPollingConsumer {
    const id = Symbol('application-status-consumer');
    consumers.set(id, {});
    ensureVisibilityListener();
    updateConsumer(id, demand);
    let active = true;
    return {
      update(nextDemand) {
        if (active) updateConsumer(id, nextDemand);
      },
      dispose() {
        if (!active) return;
        active = false;
        consumers.delete(id);
        if (consumers.size === 0) removeVisibilityListener();
        schedule();
      },
    };
  }

  function handleVisibilityChange(): void {
    if (isPageHidden()) {
      schedule();
      return;
    }
    paused.value = false;
    schedule();
  }

  function ensureVisibilityListener(): void {
    if (visibilityListening || typeof document === 'undefined') return;
    document.addEventListener('visibilitychange', handleVisibilityChange);
    visibilityListening = true;
  }

  function removeVisibilityListener(): void {
    if (!visibilityListening || typeof document === 'undefined') return;
    document.removeEventListener('visibilitychange', handleVisibilityChange);
    visibilityListening = false;
  }

  function cleanup(): void {
    disposed = true;
    requestGeneration++;
    clearTimer();
    if (requestTimeoutId !== null) clearTimeout(requestTimeoutId);
    requestTimeoutId = null;
    removeVisibilityListener();
    abortController?.abort();
    abortController = null;
    requestPromise = null;
    inFlightSections.value = [];
    for (const section of ALL_SECTIONS) sectionStates[section].loading = false;
    consumers.clear();
    pendingSections.clear();
    pendingRefreshImmediate = false;
    resolvePending?.();
    resolvePending = null;
    pendingPromise = null;
  }

  onScopeDispose(cleanup);

  return {
    applyIssueSummary,
    freshness,
    processing,
    services,
    googleHealth,
    countAnimations,
    sectionStates,
    serverGeneratedAt,
    statusClockOffsetMs,
    inFlightSections,
    nextPollAt,
    pollingMode,
    paused,
    backoffUntil,
    consecutiveNetworkFailures,
    hasActiveProcessing,
    refresh,
    force,
    registerPollingConsumer,
  };
});

if (import.meta.hot) {
  // The scheduler keeps timers and polling consumers outside Pinia state.
  // Reload so a hot update cannot mix those old closures with new actions.
  import.meta.hot.accept(() => window.location.reload());
}
