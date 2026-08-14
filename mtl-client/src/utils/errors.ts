const ABORT_ERROR_NAMES = new Set(['AbortError', 'CanceledError']);
const ABORT_ERROR_CODES = new Set(['ABORT_ERR', 'ERR_CANCELED']);
const ABORT_MESSAGE_PATTERN = /\b(?:abort(?:ed)?|cancel(?:ed|led)?)\b/i;

/**
 * Recognize cancellation errors from fetch, Axios, generated clients, and
 * wrappers that preserve the original error as `cause`.
 */
export function isAbortLikeError(error: unknown, signal?: AbortSignal): boolean {
  if (signal?.aborted) return true;
  if (error == null || typeof error !== 'object') return false;

  const candidate = error as {
    name?: unknown;
    code?: unknown;
    message?: unknown;
    cause?: unknown;
    __CANCEL__?: unknown;
  };

  if (
    (typeof candidate.name === 'string' && ABORT_ERROR_NAMES.has(candidate.name)) ||
    (typeof candidate.code === 'string' && ABORT_ERROR_CODES.has(candidate.code)) ||
    candidate.__CANCEL__ === true ||
    (typeof candidate.message === 'string' && ABORT_MESSAGE_PATTERN.test(candidate.message))
  ) {
    return true;
  }

  return candidate.cause !== error && isAbortLikeError(candidate.cause);
}
