import { clientLogger, type ClientLogMetadata } from '@/utils/clientLogger';
import { isAbortLikeError } from '@/utils/errors';

export { sanitizeForLog } from '@/utils/logSanitizer';
export { clientLogger, type ClientLogMetadata } from '@/utils/clientLogger';

function metadata(context: unknown): ClientLogMetadata {
  try {
    if (context && typeof context === 'object') {
      const value = context as ClientLogMetadata;
      // Preserve existing helper context arguments as well as the standard event metadata.
      return { ...value, context: value.context ?? context };
    }
    return { context };
  } catch {
    return {};
  }
}

export function logSanitizedError(message: string, error?: unknown, context?: unknown): void {
  clientLogger.error(message, error, metadata(context));
}

export function warnSanitized(message: string, error?: unknown, context?: unknown): void {
  clientLogger.warn(message, error, metadata(context));
}

export function warnRequestFailure(message: string, error?: unknown, context?: unknown): void {
  try {
    const status =
      error && typeof error === 'object' ? (error as { response?: { status?: number } }).response?.status : undefined;
    if (isAbortLikeError(error) || status === 401 || status === 403) return;
  } catch {
    // Unusual error shapes are handled by the central logger.
  }
  if (error instanceof SyntaxError) {
    // JSON decoders can include response contents in the error's message and stack header.
    const frames = error.stack
      ?.split('\n')
      .filter(
        (line) => /^\s+at .*(?:\(.+\)|:\d+(?::\d+)?)\s*$/.test(line) || /^[^\s]*@[^\s]+:\d+(?::\d+)?\s*$/.test(line)
      );
    const fields = metadata(context);
    warnSanitized(message, error, {
      ...fields,
      detail: 'The response could not be decoded',
      stackTrace:
        fields.stackTrace !== undefined
          ? fields.stackTrace
          : frames?.length
            ? `SyntaxError: <redacted response detail>\n${frames.join('\n')}`
            : null,
    });
    return;
  }
  warnSanitized(message, error, context);
}
