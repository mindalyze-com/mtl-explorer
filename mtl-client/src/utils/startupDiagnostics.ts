import { USER_PREFS_KEYS } from '@/utils/userPrefs';

const STORAGE_KEY = USER_PREFS_KEYS.startupCrashGuard;
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
  try {
    return window.localStorage.getItem(STORAGE_KEY) === '1';
  } catch {
    return false;
  }
}

function persistEnabled(enabled: boolean): void {
  diagnosticsEnabledCache = enabled;
  if (!hasWindow()) return;
  try {
    if (enabled) {
      window.localStorage.setItem(STORAGE_KEY, '1');
    } else {
      window.localStorage.removeItem(STORAGE_KEY);
    }
  } catch {
    // Ignore localStorage access failures.
  }
}

function mergeDetails(details: unknown, extra: Record<string, unknown>): unknown {
  if (details === undefined) return extra;
  if (details && typeof details === 'object' && !Array.isArray(details)) {
    return { ...(details as Record<string, unknown>), ...extra };
  }
  return { value: details, ...extra };
}

function emit(method: ConsoleMethod, stage: string, message: string, details?: unknown): void {
  if (!isStartupDiagnosticsEnabled()) return;

  const prefix = `[startup:${stage}] +${elapsedSince(appBootStartedAt)}ms ${message}`;
  if (details === undefined) {
    console[method](prefix);
    return;
  }
  console[method](prefix, details);
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

  const isPwa = window.matchMedia('(display-mode: standalone)').matches ||
    (window.navigator as unknown as { standalone?: boolean }).standalone === true;
  const clientVersion = typeof __APP_PKG_VERSION__ !== 'undefined' ? __APP_PKG_VERSION__ : 'unknown';
  const clientBuild = typeof __APP_VERSION__ !== 'undefined' ? __APP_VERSION__ : 'unknown';
  console.log(`[mtl] v${clientVersion} | built: ${clientBuild} | ${isPwa ? 'PWA (installed)' : 'Browser'}`);

  if (!isStartupDiagnosticsEnabled()) return;

  window.addEventListener('error', (event) => {
    startupError('window', 'Unhandled window error', {
      message: event.message,
      source: event.filename,
      line: event.lineno,
      column: event.colno,
    });
  });

  window.addEventListener('unhandledrejection', (event) => {
    startupError('window', 'Unhandled promise rejection', describeError(event.reason));
  });

  startupLog('boot', 'Startup diagnostics enabled', {
    clientVersion,
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

export function startupWarn(stage: string, message: string, details?: unknown): void {
  emit('warn', stage, message, details);
}

export function startupError(stage: string, message: string, details?: unknown): void {
  emit('error', stage, message, details);
}

export function startStartupTimer(stage: string, message: string, details?: unknown) {
  const startedAt = nowMs();
  startupLog(stage, `${message} started`, details);

  return {
    success(resultMessage: string, resultDetails?: unknown) {
      startupLog(stage, resultMessage, mergeDetails(resultDetails, { durationMs: elapsedSince(startedAt) }));
    },
    warn(resultMessage: string, resultDetails?: unknown) {
      startupWarn(stage, resultMessage, mergeDetails(resultDetails, { durationMs: elapsedSince(startedAt) }));
    },
    error(resultMessage: string, resultDetails?: unknown) {
      startupError(stage, resultMessage, mergeDetails(resultDetails, { durationMs: elapsedSince(startedAt) }));
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