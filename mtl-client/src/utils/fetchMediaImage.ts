export const MEDIA_BUSY_RETRY_DELAY_MS = 1_000;
export const MAX_MEDIA_BUSY_RETRIES = 30;
// Allow both server conversion attempts (30 seconds each) and response transfer.
export const MEDIA_IMAGE_REQUEST_TIMEOUT_MS = 90_000;
const MAX_RETRY_DELAY_MS = 2_000;
// A preview can contend with a thumbnail's full conversion window.
export const MAX_MEDIA_PREVIEW_BUSY_RETRIES = Math.ceil(MEDIA_IMAGE_REQUEST_TIMEOUT_MS / MEDIA_BUSY_RETRY_DELAY_MS);

type ImageRequestPriority = 'preview' | 'thumbnail';
type WaitingRequest = { priority: ImageRequestPriority; start: () => void };
const waitingRequests: WaitingRequest[] = [];
const activeRequests: Record<ImageRequestPriority, number> = { preview: 0, thumbnail: 0 };
const REQUEST_LIMITS: Record<ImageRequestPriority, number> = { preview: 1, thumbnail: 1 };
let previewsInProgress = 0;

function startWaitingRequests() {
  for (let index = 0; index < waitingRequests.length;) {
    const request = waitingRequests[index]!;
    if (
      activeRequests[request.priority] >= REQUEST_LIMITS[request.priority] ||
      (request.priority === 'thumbnail' && previewsInProgress > 0)
    ) {
      index++;
      continue;
    }
    waitingRequests.splice(index, 1);
    request.start();
  }
}

/** Waiting for a slot does not consume retries. Aborted thumbnails never start a request. */
function acquireImageRequest(signal: AbortSignal, priority: ImageRequestPriority): Promise<() => void> {
  signal.throwIfAborted();
  return new Promise((resolve, reject) => {
    const onAbort = () => {
      const index = waitingRequests.indexOf(request);
      if (index >= 0) waitingRequests.splice(index, 1);
      reject(signal.reason);
    };
    const request: WaitingRequest = {
      priority,
      start() {
        signal.removeEventListener('abort', onAbort);
        activeRequests[priority]++;
        resolve(() => {
          activeRequests[priority]--;
          startWaitingRequests();
        });
      },
    };
    signal.addEventListener('abort', onAbort, { once: true });
    waitingRequests.push(request);
    startWaitingRequests();
  });
}

/** Release the queue promptly on navigation, even if a transport settles late. */
function abortable<T>(request: Promise<T>, signal: AbortSignal): Promise<T> {
  return new Promise((resolve, reject) => {
    const onAbort = () => reject(signal.reason);
    signal.addEventListener('abort', onAbort, { once: true });
    if (signal.aborted) onAbort();
    request.then(
      (value) => {
        signal.removeEventListener('abort', onAbort);
        if (signal.aborted) reject(signal.reason);
        else resolve(value);
      },
      (error) => {
        signal.removeEventListener('abort', onAbort);
        reject(error);
      }
    );
  });
}

/** A conversion can outlive an aborted request for the previously selected photo. */
export async function fetchMediaImage(
  url: string,
  signal: AbortSignal,
  priority: ImageRequestPriority = 'preview'
): Promise<Blob> {
  if (priority === 'preview') previewsInProgress++;
  const maxBusyRetries = priority === 'preview' ? MAX_MEDIA_PREVIEW_BUSY_RETRIES : MAX_MEDIA_BUSY_RETRIES;
  try {
    for (let attempt = 0; ; attempt++) {
      signal.throwIfAborted();
      const release = await acquireImageRequest(signal, priority);
      const controller = new AbortController();
      const requestSignal = AbortSignal.any([signal, controller.signal]);
      const timeout = setTimeout(
        () => controller.abort(new DOMException('Image request timed out. Please try again.', 'TimeoutError')),
        MEDIA_IMAGE_REQUEST_TIMEOUT_MS
      );
      let delayMs = MEDIA_BUSY_RETRY_DELAY_MS;
      try {
        requestSignal.throwIfAborted();
        const response = await abortable(
          fetch(url, { signal: requestSignal }).then((response) => {
            if (requestSignal.aborted) {
              void response.body?.cancel().catch(() => undefined);
              requestSignal.throwIfAborted();
            }
            return response;
          }),
          requestSignal
        );
        if (response.status === 503) {
          await abortable(response.body?.cancel() ?? Promise.resolve(), requestSignal);
          if (attempt >= maxBusyRetries) {
            throw new Error('Media processing is still busy. Please try again shortly.');
          }
          const retryAfterMs = Number(response.headers?.get('Retry-After')) * 1_000;
          delayMs =
            Number.isFinite(retryAfterMs) && retryAfterMs > 0
              ? Math.max(MEDIA_BUSY_RETRY_DELAY_MS, Math.min(retryAfterMs, MAX_RETRY_DELAY_MS))
              : MEDIA_BUSY_RETRY_DELAY_MS;
        } else {
          if (!response.ok) throw new Error(`Failed to fetch image: ${response.status}`);
          const blob = await abortable(response.blob(), requestSignal);
          if (blob.size === 0) throw new Error('Image response was empty');
          requestSignal.throwIfAborted();
          return blob;
        }
      } finally {
        clearTimeout(timeout);
        release();
      }
      // Other previews may proceed; thumbnail retries wait until all previews settle.
      await waitForRetry(delayMs, signal);
    }
  } finally {
    if (priority === 'preview') {
      previewsInProgress--;
      startWaitingRequests();
    }
  }
}

function waitForRetry(delayMs: number, signal: AbortSignal): Promise<void> {
  signal.throwIfAborted();
  return new Promise((resolve, reject) => {
    const onAbort = () => {
      clearTimeout(timer);
      reject(signal.reason);
    };
    const timer = setTimeout(() => {
      signal.removeEventListener('abort', onAbort);
      resolve();
    }, delayMs);
    signal.addEventListener('abort', onAbort, { once: true });
  });
}
