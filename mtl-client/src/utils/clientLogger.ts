import axios from 'axios';
import { ResponseError, type ClientLogRequest } from 'x8ing-mtl-api-typescript-fetch';
import { getDiagnosticSession } from '@/utils/diagnosticApiConfiguration';
import { clientDiagnosticStore, newClientEventId } from '@/utils/clientDiagnosticStore';
import { sanitizeForLog } from '@/utils/logSanitizer';

const SAFE_CONTEXT_KEYS = new Set([
  'errorType',
  'code',
  'status',
  'statusText',
  'method',
  'routeName',
  'vueInfo',
  'source',
  'line',
  'column',
  'mode',
  'packageVersion',
  'component',
  'operation',
]);

export interface ClientLogMetadata {
  topic1?: string | null;
  topic2?: string | null;
  topic3?: string | null;
  detail?: string | null;
  stackTrace?: string | null;
  context?: unknown;
}

function text(value: unknown): string | undefined {
  return typeof value === 'string' ? String(sanitizeForLog(value)) : undefined;
}

function safeContext(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object') return {};
  return Object.fromEntries(
    Object.entries(value).flatMap(([key, entry]) => {
      if (!SAFE_CONTEXT_KEYS.has(key)) return [];
      if (entry == null || ['string', 'number', 'boolean'].includes(typeof entry)) {
        return [[key, sanitizeForLog(entry)]];
      }
      return [];
    })
  );
}

function originalError(value: unknown): unknown {
  if (value && typeof value === 'object' && 'error' in value) return value.error;
  return value;
}

function logAndSubmit(
  severity: 'WARN' | 'ERROR',
  message: string,
  error?: unknown,
  metadata: ClientLogMetadata = {}
): void {
  const occurredAt = new Date();
  try {
    const original = originalError(error);
    const candidate =
      original && typeof original === 'object'
        ? (original as { name?: unknown; message?: unknown; stack?: unknown })
        : undefined;
    const stack = metadata.stackTrace === null ? null : text(metadata.stackTrace ?? candidate?.stack);
    const context = safeContext(metadata.context);
    if (original !== undefined) context.errorType = text(candidate?.name) ?? typeof original;
    if (axios.isAxiosError(original)) {
      Object.assign(
        context,
        safeContext({
          code: original.code,
          status: original.response?.status,
          statusText: original.response?.statusText,
          method: original.config?.method,
        })
      );
    } else if (original instanceof ResponseError) {
      Object.assign(
        context,
        safeContext({
          status: original.response.status,
          statusText: original.response.statusText,
        })
      );
    }
    context.mode =
      typeof window !== 'undefined' &&
      (window.matchMedia?.('(display-mode: standalone)').matches ||
        (navigator as Navigator & { standalone?: boolean }).standalone)
        ? 'PWA'
        : 'Browser';
    if (typeof __APP_PKG_VERSION__ !== 'undefined') context.packageVersion = __APP_PKG_VERSION__;
    const session = getDiagnosticSession();
    const event: ClientLogRequest = {
      clientEventId: newClientEventId(),
      occurredAt,
      occurrenceCount: 1,
      originalClientLoginRef: session?.loginRef,
      severity,
      topic1: text(metadata.topic1),
      topic2: text(metadata.topic2),
      topic3: text(metadata.topic3),
      message: text(message) ?? 'Client diagnostic',
      detail: text(
        metadata.detail ??
          candidate?.message ??
          (['string', 'number', 'boolean', 'bigint'].includes(typeof original) ? String(original) : undefined)
      ),
      stackTrace: stack,
      context,
      buildId: typeof __APP_VERSION__ !== 'undefined' ? __APP_VERSION__ : 'development',
    };
    // Emit the resolved record so explicit null cannot be overridden by console redaction.
    console[severity === 'ERROR' ? 'error' : 'warn'](event.message, event);
    void clientDiagnosticStore.capture(event, session).catch(() => {});
  } catch {
    // Includes unusual getters, circular values and synchronous transport/serialization failures.
    try {
      console[severity === 'ERROR' ? 'error' : 'warn']('[MTL] Diagnostic could not be prepared');
    } catch {
      /* Console may be unavailable. */
    }
  }
}

export const clientLogger = Object.freeze({
  error(message: string, error?: unknown, metadata?: ClientLogMetadata): void {
    logAndSubmit('ERROR', message, error, metadata);
  },
  warn(message: string, error?: unknown, metadata?: ClientLogMetadata): void {
    logAndSubmit('WARN', message, error, metadata);
  },
});
