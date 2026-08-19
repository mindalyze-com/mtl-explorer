/** Shared cadence for recurring operational status requests. */
export const STATUS_POLL_INTERVAL_MS = 8_000;

export interface StatusRequestOptions {
  force?: boolean;
}

export interface StatusRequestService<T> {
  fetch(options?: StatusRequestOptions): Promise<T>;
  invalidate(): void;
}

interface StatusRequestServiceConfig<T> {
  request: (signal: AbortSignal) => Promise<T>;
  intervalMs?: number;
  requestTimeoutMs?: number;
  maxRetryDelayMs?: number;
  pauseWhen?: () => boolean;
  pausedErrorMessage?: string;
}

interface InFlightStatusRequest<T> {
  abortController: AbortController;
  generation: number;
  promise: Promise<T>;
}

/**
 * Creates one cached request path for a status endpoint.
 *
 * All consumers share the in-flight request, cached response, minimum network
 * interval, failure backoff, and invalidation state.
 */
export function createStatusRequestService<T>(config: StatusRequestServiceConfig<T>): StatusRequestService<T> {
  const intervalMs = config.intervalMs ?? STATUS_POLL_INTERVAL_MS;
  const maxRetryDelayMs = config.maxRetryDelayMs ?? intervalMs;
  let cachedStatus: T | undefined;
  let hasCachedStatus = false;
  let inFlight: InFlightStatusRequest<T> | null = null;
  let nextAutomaticRequestAt = 0;
  let consecutiveFailures = 0;
  let lastRequestError: unknown;
  let requestGeneration = 0;

  return {
    async fetch(options: StatusRequestOptions = {}): Promise<T> {
      if (inFlight) return inFlight.promise;

      if (!options.force) {
        if (config.pauseWhen?.()) {
          if (hasCachedStatus) return cachedStatus as T;
          throw new Error(config.pausedErrorMessage ?? 'Status refresh is paused.');
        }

        if (Date.now() < nextAutomaticRequestAt) {
          if (lastRequestError) throw lastRequestError;
          if (hasCachedStatus) return cachedStatus as T;
        }
      }

      const requestStartedAt = Date.now();
      const abortController = new AbortController();
      const generation = ++requestGeneration;
      const timeoutId =
        config.requestTimeoutMs == null
          ? null
          : globalThis.setTimeout(() => abortController.abort(), config.requestTimeoutMs);
      const promise = config
        .request(abortController.signal)
        .then((status) => {
          if (generation === requestGeneration) {
            cachedStatus = status;
            hasCachedStatus = true;
            consecutiveFailures = 0;
            lastRequestError = undefined;
            nextAutomaticRequestAt = requestStartedAt + intervalMs;
          }
          return status;
        })
        .catch((error: unknown) => {
          if (generation === requestGeneration) {
            consecutiveFailures += 1;
            lastRequestError = error;
            const retryDelay = Math.min(intervalMs * consecutiveFailures, maxRetryDelayMs);
            nextAutomaticRequestAt = Date.now() + retryDelay;
          }
          throw error;
        })
        .finally(() => {
          if (timeoutId !== null) globalThis.clearTimeout(timeoutId);
          if (inFlight?.generation === generation) {
            inFlight = null;
          }
        });

      inFlight = { abortController, generation, promise };
      return promise;
    },

    invalidate(): void {
      requestGeneration += 1;
      inFlight?.abortController.abort();
      inFlight = null;
      cachedStatus = undefined;
      hasCachedStatus = false;
      nextAutomaticRequestAt = 0;
      consecutiveFailures = 0;
      lastRequestError = undefined;
    },
  };
}
