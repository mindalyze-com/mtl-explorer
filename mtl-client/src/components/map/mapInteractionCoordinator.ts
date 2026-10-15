import type { TrackProcessingStatusDto } from 'x8ing-mtl-api-typescript-fetch';
import {
  decideMapInteraction,
  mapTargetKey,
  mergeMapTargets,
  type MapInteractionInputKind,
  type MapInteractionPresentation,
  type MapInteractionSourceState,
  type MapTargetCandidate,
} from './mapInteractionDecision';

export const MAP_IDENTIFICATION_TIMEOUT_MS = 15_000;

/** Providers opt into showing a known, safe message alongside unavailable status. */
export class MapInteractionProviderError extends Error {
  constructor(
    message: string,
    readonly displayMessage: string = message
  ) {
    super(message);
    this.name = 'MapInteractionProviderError';
  }
}

export type MapInteractionContext = {
  lngLat: { lng: number; lat: number };
  point: { x: number; y: number };
  inputKind: MapInteractionInputKind;
  trackSetRevision: number;
  mediaScope: string;
  eligibility: Readonly<Record<string, boolean | string | number>>;
};

export type MapInteractionProviderResult = {
  candidates: MapTargetCandidate[];
  /** Supply only an incomplete or failed-indexing notice, rather than a ready status. */
  processing?: TrackProcessingStatusDto | null;
};

export type MapInteractionProvider = {
  id: string;
  label: string;
  kind: 'tracks' | 'overlay';
  run: (
    context: MapInteractionContext,
    signal: AbortSignal,
    publish?: (result: MapInteractionProviderResult) => void
  ) => Promise<MapInteractionProviderResult>;
  timeoutMs?: number;
};

export type MapInteractionConsumer = {
  id: string;
  label?: string;
  isEligible?: (context: MapInteractionContext) => boolean;
} & (
  | { kind: 'local'; collect: (context: MapInteractionContext) => readonly MapTargetCandidate[] }
  | { kind: 'async'; provider: MapInteractionProvider }
);

export type MapInteractionState = {
  id: number;
  context: MapInteractionContext;
  candidates: MapTargetCandidate[];
  sources: MapInteractionSourceState[];
  selected: MapTargetCandidate | null;
  presentation: MapInteractionPresentation;
  processing: TrackProcessingStatusDto | null;
};

type CoordinatorOptions = {
  onChange: (state: MapInteractionState | null) => void;
  onSelect?: (candidate: MapTargetCandidate, state: MapInteractionState) => void;
  onEnd?: (id: number) => void;
  onProviderError?: (error: unknown, source: MapInteractionSourceState, context: MapInteractionContext) => void;
  timeoutMs?: number;
};

type ProviderRequest = {
  controller: AbortController;
  timer: ReturnType<typeof setTimeout> | null;
};

/** One accepted exploration click owns its sources, targets, and visible presentation. */
export class MapInteractionCoordinator {
  private nextId = 0;
  private current: MapInteractionState | null = null;
  private requests = new Map<string, ProviderRequest>();
  private discoveryCancelled = false;
  private consumers = new Map<string, MapInteractionConsumer>();

  constructor(private readonly options: CoordinatorOptions) {}

  get state(): MapInteractionState | null {
    return this.current;
  }

  isCurrent(id: number): boolean {
    return this.current?.id === id;
  }

  /** Map components register only candidate collection; selection remains in this dispatcher. */
  registerConsumer(consumer: MapInteractionConsumer): () => void {
    this.end();
    this.consumers.set(consumer.id, consumer);
    return () => {
      if (this.consumers.get(consumer.id) !== consumer) return;
      this.end();
      this.consumers.delete(consumer.id);
    };
  }

  start(
    context: MapInteractionContext,
    localCandidates: readonly MapTargetCandidate[] = [],
    providers: readonly MapInteractionProvider[] = []
  ): number {
    this.end();
    const id = ++this.nextId;
    this.discoveryCancelled = false;
    // Capture the click context; later map/filter changes cannot move its anchor.
    const capturedContext: MapInteractionContext = {
      ...context,
      lngLat: { ...context.lngLat },
      point: { ...context.point },
      eligibility: { ...context.eligibility },
    };
    const candidates = [...localCandidates];
    const registeredProviders = [...providers];
    const unavailable: Array<{ error: unknown; source: MapInteractionSourceState }> = [];
    const completedLocalSources: MapInteractionSourceState[] = [];
    for (const consumer of this.consumers.values()) {
      try {
        if (consumer.isEligible && !consumer.isEligible(capturedContext)) continue;
        if (consumer.kind === 'local') {
          candidates.push(...consumer.collect(capturedContext));
          completedLocalSources.push({
            id: consumer.id,
            label: consumer.label ?? consumer.id,
            kind: 'local',
            status: 'complete',
          });
        } else registeredProviders.push(consumer.provider);
      } catch (error) {
        const source: MapInteractionSourceState =
          consumer.kind === 'local'
            ? { id: consumer.id, label: consumer.label ?? consumer.id, kind: 'local', status: 'failed' }
            : {
                id: consumer.provider.id,
                label: consumer.provider.label,
                kind: consumer.provider.kind,
                status: 'failed',
              };
        if (error instanceof MapInteractionProviderError) source.message = error.displayMessage;
        unavailable.push({ error, source });
      }
    }
    // Provider identity is fixed for the click. Accidental duplicate registration never dispatches twice.
    const activeProviders = [...new Map(registeredProviders.map((provider) => [provider.id, provider])).values()];
    const sources = new Map(unavailable.map(({ source }) => [source.id, source]));
    for (const source of completedLocalSources) {
      if (!sources.has(source.id)) sources.set(source.id, source);
    }
    // If local collection and a provider share an identity, the active request owns its status.
    for (const { id, label, kind } of activeProviders) {
      sources.set(id, { id, label, kind, status: 'pending' });
    }
    this.current = {
      id,
      context: capturedContext,
      candidates: mergeMapTargets([], candidates),
      sources: [...sources.values()],
      selected: null,
      presentation: 'chooser',
      processing: null,
    };
    for (const { error, source } of unavailable) {
      if (!this.isCurrent(id)) return id;
      this.options.onProviderError?.(error, source, capturedContext);
    }
    this.publishDecision();
    if (!this.isCurrent(id)) return id;
    for (const provider of activeProviders) {
      if (!this.isCurrent(id) || this.discoveryCancelled) break;
      this.startProvider(id, provider, capturedContext);
    }
    return id;
  }

  select(key: string): boolean {
    const state = this.current;
    const candidate = state?.candidates.find((target) => mapTargetKey(target) === key);
    if (!state || !candidate) return false;
    if (state.presentation === 'chooser' && state.selected && mapTargetKey(state.selected) === key) return this.back();
    const selectedState: MapInteractionState = { ...state, selected: candidate, presentation: 'target' };
    this.current = selectedState;
    this.options.onChange(selectedState);
    if (this.current === selectedState) this.options.onSelect?.(candidate, selectedState);
    return true;
  }

  showChooser(): boolean {
    if (!this.current) return false;
    this.current = { ...this.current, presentation: 'chooser' };
    this.options.onChange(this.current);
    return true;
  }

  back(): boolean {
    if (!this.current?.selected || this.current.presentation !== 'chooser') return false;
    this.current = { ...this.current, presentation: 'target' };
    // Restoring selected content does not dispatch a second detail or cluster lookup.
    this.options.onChange(this.current);
    return true;
  }

  /** Manual map movement stops discovery, retaining the selection and original location. */
  cancelDiscovery(): void {
    if (!this.current || this.discoveryCancelled) return;
    this.discoveryCancelled = true;
    this.abortRequests();
    this.current = {
      ...this.current,
      sources: this.current.sources.map((source) =>
        source.status === 'pending' ? { ...source, status: 'cancelled' } : source
      ),
    };
    this.options.onChange(this.current);
  }

  /** expectedId scopes gesture cleanup so it cannot clear a newer click. */
  end(expectedId?: number): void {
    if (!this.current || (expectedId !== undefined && !this.isCurrent(expectedId))) return;
    const id = this.current.id;
    this.current = null;
    this.discoveryCancelled = true;
    this.abortRequests();
    this.options.onChange(null);
    this.options.onEnd?.(id);
  }

  private startProvider(id: number, provider: MapInteractionProvider, context: MapInteractionContext): void {
    const controller = new AbortController();
    const request: ProviderRequest = { controller, timer: null };
    this.requests.set(provider.id, request);
    request.timer = setTimeout(
      () => {
        if (!this.canApply(id, provider.id, request)) return;
        controller.abort();
        this.settleFailure(id, provider.id, request, 'timed-out', new Error('Map identification timed out'));
      },
      provider.timeoutMs ?? this.options.timeoutMs ?? MAP_IDENTIFICATION_TIMEOUT_MS
    );
    // Promise chaining starts providers independently and handles synchronous throws too.
    let operation: Promise<MapInteractionProviderResult>;
    try {
      operation = provider.run(context, controller.signal, (result) => {
        this.applyProviderResult(id, provider.id, request, result, false);
      });
    } catch (error) {
      this.settleFailure(id, provider.id, request, 'failed', error);
      return;
    }
    void operation.then(
      (result) => {
        this.applyProviderResult(id, provider.id, request, result, true);
      },
      (error: unknown) => {
        if (!this.canApply(id, provider.id, request)) return;
        this.settleFailure(id, provider.id, request, 'failed', error);
      }
    );
  }

  private applyProviderResult(
    id: number,
    sourceId: string,
    request: ProviderRequest,
    result: MapInteractionProviderResult,
    complete: boolean
  ): void {
    if (!this.canApply(id, sourceId, request)) return;
    if (complete) this.releaseRequest(sourceId, request);
    const state = this.current!;
    const refinements = new Map(result.candidates.map((candidate) => [mapTargetKey(candidate), candidate]));
    const updated = state.candidates.map((candidate) => {
      const refinement = refinements.get(mapTargetKey(candidate));
      return refinement ? ({ ...candidate, ...refinement } as MapTargetCandidate) : candidate;
    });
    this.current = {
      ...state,
      candidates: mergeMapTargets(updated, result.candidates),
      sources: complete
        ? state.sources.map((source) => (source.id === sourceId ? { ...source, status: 'complete' } : source))
        : state.sources,
      processing: result.processing ?? state.processing,
    };
    this.options.onChange(this.current);
  }

  private canApply(id: number, sourceId: string, request: ProviderRequest): boolean {
    return (
      this.isCurrent(id) &&
      !this.discoveryCancelled &&
      !request.controller.signal.aborted &&
      this.requests.get(sourceId) === request
    );
  }

  private settleFailure(
    id: number,
    sourceId: string,
    request: ProviderRequest,
    status: 'failed' | 'timed-out',
    error: unknown
  ): void {
    if (!this.isCurrent(id) || this.discoveryCancelled || this.requests.get(sourceId) !== request) return;
    this.releaseRequest(sourceId, request);
    const state = this.current!;
    const source = state.sources.find((source) => source.id === sourceId)!;
    const failedSource = { ...source, status };
    if (error instanceof MapInteractionProviderError) failedSource.message = error.displayMessage;
    this.current = {
      ...state,
      sources: state.sources.map((item) => (item.id === sourceId ? failedSource : item)),
    };
    this.options.onProviderError?.(error, failedSource, state.context);
    if (this.isCurrent(id)) this.publishDecision();
  }

  private publishDecision(): void {
    const state = this.current;
    if (!state) return;
    const decision = decideMapInteraction(state);
    if (decision.action === 'choose') this.current = { ...state, presentation: 'chooser' };
    this.options.onChange(this.current);
  }

  private releaseRequest(sourceId: string, request: ProviderRequest): void {
    if (request.timer !== null) clearTimeout(request.timer);
    request.timer = null;
    if (this.requests.get(sourceId) === request) this.requests.delete(sourceId);
  }

  private abortRequests(): void {
    for (const [sourceId, request] of this.requests) {
      request.controller.abort();
      this.releaseRequest(sourceId, request);
    }
  }
}
