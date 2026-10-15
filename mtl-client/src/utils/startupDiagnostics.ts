import { clientLogger } from '@/utils/clientLogger';
import { readStorage, removeStorage, STORAGE_KEYS, writeStorage } from '@/utils/appStorage';

const STORAGE_KEY = STORAGE_KEYS.startupCrashGuard;
const QUERY_PARAM = 'startupDiag';

const appBootStartedAt = typeof performance !== 'undefined' ? performance.now() : Date.now();

let diagnosticsEnabledCache: boolean | null = null;
let diagnosticsInitialized = false;

type ConsoleMethod = 'log' | 'warn' | 'error';

function nowMs(): number {
  return typeof performance !== 'undefined' ? performance.now() : Date.now();
}

function elapsedSince(startedAt: number): number {
  return Math.round(nowMs() - startedAt);
}

function normalizeToggle(value: string | null): boolean | undefined {
  if (value == null) return undefined;
  const normalized = value.trim().toLowerCase();
  if (['1', 'true', 'yes', 'on'].includes(normalized)) return true;
  if (['0', 'false', 'no', 'off'].includes(normalized)) return false;
  return undefined;
}

function hasWindow(): boolean {
  return typeof window !== 'undefined';
}

function readStoredEnabled(): boolean {
  if (!hasWindow()) return false;
  return readStorage(STORAGE_KEY) === '1';
}

function persistEnabled(enabled: boolean): void {
  diagnosticsEnabledCache = enabled;
  if (!hasWindow()) return;
  if (enabled) {
    writeStorage(STORAGE_KEY, '1');
  } else {
    removeStorage(STORAGE_KEY);
  }
}

function mergeDetails(details: unknown, extra: Record<string, unknown>): unknown {
  if (details === undefined) return extra;
  if (details && typeof details === 'object' && !Array.isArray(details)) {
    return details instanceof Error
      ? { error: details, ...extra }
      : { ...(details as Record<string, unknown>), ...extra };
  }
  return { value: details, ...extra };
}

function emit(method: ConsoleMethod, stage: string, message: string, details?: unknown, error?: unknown): void {
  if (method === 'log' && !isStartupDiagnosticsEnabled()) return;

  const prefix = `[startup:${stage}] +${elapsedSince(appBootStartedAt)}ms ${message}`;
  if (method !== 'log') {
    clientLogger[method === 'error' ? 'error' : 'warn'](prefix, error, {
      topic1: 'startup',
      topic2: stage,
      context: details,
    });
    return;
  }
  if (details === undefined) {
    console.log(prefix);
    return;
  }
  console.log(prefix, details);
}

export function isStartupDiagnosticsEnabled(): boolean {
  if (diagnosticsEnabledCache != null) return diagnosticsEnabledCache;
  diagnosticsEnabledCache = readStoredEnabled();
  return diagnosticsEnabledCache;
}

export function initializeStartupDiagnostics(): void {
  if (!hasWindow() || diagnosticsInitialized) return;
  diagnosticsInitialized = true;

  const queryValue = normalizeToggle(new URLSearchParams(window.location.search).get(QUERY_PARAM));
  if (queryValue !== undefined) {
    persistEnabled(queryValue);
  } else {
    diagnosticsEnabledCache = readStoredEnabled();
  }

  const isPwa =
    window.matchMedia('(display-mode: standalone)').matches ||
    (window.navigator as unknown as { standalone?: boolean }).standalone === true;
  const clientVersion = typeof __APP_PKG_VERSION__ !== 'undefined' ? __APP_PKG_VERSION__ : 'unknown';
  const clientImageVersion = (import.meta.env.VITE_APP_VERSION as string | undefined)?.trim() || 'development';
  const clientBuild = typeof __APP_VERSION__ !== 'undefined' ? __APP_VERSION__ : 'unknown';
  console.log(
    `[mtl] image version ${clientImageVersion} | built: ${clientBuild} | ${isPwa ? 'PWA (installed)' : 'Browser'}`
  );

  if (!isStartupDiagnosticsEnabled()) return;

  startupLog('boot', 'Startup diagnostics enabled', {
    clientVersion,
    clientImageVersion,
    clientBuild,
    runningAs: isPwa ? 'PWA (installed)' : 'Browser',
    queryParam: `?${QUERY_PARAM}=1`,
    disableQueryParam: `?${QUERY_PARAM}=0`,
    storageKey: STORAGE_KEY,
    path: window.location.pathname,
    search: window.location.search,
  });
}

export function startupLog(stage: string, message: string, details?: unknown): void {
  emit('log', stage, message, details);
}

export function startupWarn(stage: string, message: string, error?: unknown, context?: unknown): void {
  emit('warn', stage, message, context, error);
}

export function startupError(stage: string, message: string, error?: unknown, context?: unknown): void {
  emit('error', stage, message, context, error);
}

export function startStartupTimer(stage: string, message: string, details?: unknown) {
  const startedAt = nowMs();
  startupLog(stage, `${message} started`, details);

  return {
    success(resultMessage: string, resultDetails?: unknown) {
      startupLog(stage, resultMessage, mergeDetails(resultDetails, { durationMs: elapsedSince(startedAt) }));
    },
    warn(resultMessage: string, error?: unknown, context?: unknown) {
      startupWarn(stage, resultMessage, error, mergeDetails(context, { durationMs: elapsedSince(startedAt) }));
    },
    error(resultMessage: string, error?: unknown, context?: unknown) {
      startupError(stage, resultMessage, error, mergeDetails(context, { durationMs: elapsedSince(startedAt) }));
    },
    elapsedMs() {
      return elapsedSince(startedAt);
    },
  };
}

export function describeError(error: unknown): Record<string, unknown> {
  const candidate = error as {
    name?: string;
    message?: string;
    code?: string;
    response?: { status?: number; statusText?: string };
    request?: unknown;
  };

  const status = candidate?.response?.status;
  const code = candidate?.code;
  const name = candidate?.name;
  const message = candidate?.message;

  let kind = 'unknown';
  if (name === 'AbortError' || name === 'CanceledError' || code === 'ERR_CANCELED') {
    kind = 'aborted';
  } else if (code === 'ECONNABORTED' || (typeof message === 'string' && message.toLowerCase().includes('timeout'))) {
    kind = 'timeout';
  } else if (typeof status === 'number') {
    kind = `http-${status}`;
  } else if (candidate?.request && !candidate?.response) {
    kind = 'network-no-response';
  } else if (error instanceof Error) {
    kind = 'error';
  }

  return {
    kind,
    name,
    message,
    code,
    status,
    statusText: candidate?.response?.statusText,
  };
}
