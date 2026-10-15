import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AxiosError } from 'axios';
import 'fake-indexeddb/auto';

let clientLogger: (typeof import('@/utils/clientLogger'))['clientLogger'];
let request: ReturnType<typeof vi.fn>;
let store: (typeof import('@/utils/clientDiagnosticStore'))['clientDiagnosticStore'];
let delivery: InstanceType<(typeof import('@/utils/clientDiagnosticDelivery'))['ClientDiagnosticDelivery']>;
function token(loginRef = 'original-login') {
  return `${btoa('{"alg":"none"}')}.${btoa(JSON.stringify({ sub: 'synthetic', exp: Date.now() / 1000 + 86400, client_login_ref: loginRef }))}.signature`;
}

beforeEach(async () => {
  vi.resetModules();
  localStorage.clear();
  localStorage.setItem('mtl.jwt', token());
  vi.stubGlobal('__APP_VERSION__', 'synthetic-build');
  vi.stubGlobal('__APP_PKG_VERSION__', 'synthetic-package');
  vi.spyOn(console, 'error').mockImplementation(() => {});
  vi.spyOn(console, 'warn').mockImplementation(() => {});
  request = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
  vi.stubGlobal('fetch', request);
  clientLogger = (await import('@/utils/clientLogger')).clientLogger;
  store = (await import('@/utils/clientDiagnosticStore')).clientDiagnosticStore;
  await store.clear();
  store.resume();
  delivery = new (await import('@/utils/clientDiagnosticDelivery')).ClientDiagnosticDelivery(store);
});
afterEach(async () => {
  await delivery.stop();
  await store.clear();
  vi.useRealTimers();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

function payload(index = 0) {
  return JSON.parse(request.mock.calls[index]![1].body);
}
const settle = async () => {
  await store.idle();
  await delivery.drain();
};

describe('central client diagnostic delivery', async () => {
  it('reports startup problems when detailed startup diagnostics are disabled', async () => {
    vi.spyOn(console, 'log').mockImplementation(() => {});
    const { startStartupTimer, startupLog, isStartupDiagnosticsEnabled } = await import('@/utils/startupDiagnostics');
    expect(isStartupDiagnosticsEnabled()).toBe(false);
    startupLog('map', 'Local verbose detail');
    const timer = startStartupTimer('map', 'Initialize map');
    const error = new Error('Synthetic map problem');
    timer.error('Map initialization failed', error);
    timer.warn('Map load is delayed');
    await settle();
    expect(console.log).not.toHaveBeenCalled();
    expect(request).toHaveBeenCalledTimes(2);
    expect(payload(0)).toMatchObject({ topic1: 'startup', topic2: 'map', stackTrace: error.stack });
    expect(payload(1).context).not.toHaveProperty('errorType');
    expect(payload(1).stackTrace).toBeUndefined();
  });

  it('keeps normal startup and sign-in transitions informational', async () => {
    localStorage.setItem('mtl.startup.crash-guard', '1');
    vi.spyOn(console, 'log').mockImplementation(() => {});
    const { startStartupTimer, startupLog } = await import('@/utils/startupDiagnostics');
    startupLog('boot', 'Returning user detected', { status: 'starting' });
    startStartupTimer('auth', 'Checking sign-in').success('Sign-in required', { status: 401 });
    startStartupTimer('map', 'Loading map').success('Map ready');
    await settle();
    expect(console.log).toHaveBeenCalled();
    expect(console.warn).not.toHaveBeenCalled();
    expect(console.error).not.toHaveBeenCalled();
    expect(request).not.toHaveBeenCalled();
    expect(await store.records()).toHaveLength(0);
  });

  it('keeps startup context separate from exceptions in direct and timed reports', async () => {
    const { startStartupTimer, startupWarn, startupError } = await import('@/utils/startupDiagnostics');
    startupWarn('map', 'Map load is delayed', undefined, { status: 503, operation: 'load' });
    startupError('map', 'Map is unavailable', undefined, { operation: 'initialize' });
    startStartupTimer('tracks', 'Loading tracks').warn('Tracks are delayed', undefined, { status: 503 });
    await settle();
    expect(request).toHaveBeenCalledTimes(3);
    for (let index = 0; index < 3; index++) {
      expect(payload(index).context).not.toHaveProperty('errorType');
      expect(payload(index).detail).toBeUndefined();
      expect(payload(index).stackTrace).toBeUndefined();
    }
    expect(payload(0).context).toMatchObject({ status: 503, operation: 'load' });
    expect(payload(1).context).toMatchObject({ operation: 'initialize' });
    expect(payload(2).context).toMatchObject({ status: 503 });
  });

  it('preserves generated API errors and explicit context through startup timers', async () => {
    const { ResponseError } = await import('x8ing-mtl-api-typescript-fetch');
    const { startStartupTimer } = await import('@/utils/startupDiagnostics');
    const error = new ResponseError(new Response('synthetic-private-body', { status: 503 }), 'Transport failed');
    error.stack = 'ResponseError: Transport failed\n at original (bundle.js:8:2)';
    startStartupTimer('map', 'Initializing map').error('Map initialization failed', error, { operation: 'load' });
    await settle();
    expect(payload()).toMatchObject({
      detail: 'Transport failed',
      stackTrace: error.stack,
      context: { errorType: 'ResponseError', status: 503, operation: 'load' },
    });
    expect(error.response.bodyUsed).toBe(false);
    expect(JSON.stringify(payload())).not.toContain('synthetic-private-body');
  });

  it('reports map render and graphics problems while ignoring cancellation and archive transitions', async () => {
    const { reportMapError, reportMapContextLoss } = await import('@/utils/mapDiagnostics');
    const error = new Error('Synthetic tile failure');
    error.stack = 'Error: synthetic\n at render (bundle.js:1:7)';
    reportMapError({ error }, 'Map3DRenderer');
    reportMapContextLoss('useMiniMap');
    reportMapError({ error: new DOMException('Aborted', 'AbortError') }, 'useMiniMap');
    reportMapError({ error: new Error('Map archive changed; refreshing map config.') }, 'useMiniMap');
    await settle();
    expect(request).toHaveBeenCalledTimes(2);
    expect(payload(0)).toMatchObject({ topic1: 'map', topic2: 'Map3DRenderer', stackTrace: error.stack });
    expect(payload(1)).toMatchObject({ topic2: 'useMiniMap', topic3: 'webgl-context-lost' });
    expect(payload(1).stackTrace).toBeUndefined();
  });

  it('keeps expected cancellation and authentication failures out of request-failure reporting', async () => {
    const { warnRequestFailure } = await import('@/utils/safeLogging');
    warnRequestFailure('Cancelled', new DOMException('Aborted', 'AbortError'));
    warnRequestFailure('Expired', { response: { status: 401 } });
    warnRequestFailure('Disabled', { response: { status: 403 } });
    const error = new Error('Unexpected response');
    warnRequestFailure('Failed', error, { topic1: 'statistics' });
    await settle();
    expect(request).toHaveBeenCalledTimes(1);
    expect(payload()).toMatchObject({ topic1: 'statistics', stackTrace: error.stack });
  });
  it('persists six repeated errors as one counted report and preserves original and minified stack frames', async () => {
    const error = new Error('synthetic');
    error.stack = 'Error: synthetic\n    at a (https://example.test/app.js:1:99)\n    at b (app.js:2:7)';
    for (let index = 0; index < 6; index++) clientLogger.error('Failed', error, { topic1: 'media' });
    await settle();
    expect(request).toHaveBeenCalledTimes(1);
    expect(payload().occurrenceCount).toBe(6);
    await settle();
    expect(payload()).toMatchObject({
      stackTrace: error.stack,
      severity: 'ERROR',
      topic1: 'media',
      buildId: 'synthetic-build',
    });
    expect(request.mock.calls[0]![1].headers.Authorization).toBe(`Bearer ${localStorage.getItem('mtl.jwt')}`);
    await settle();
  });

  it('keeps local console output for a flood and persists repetition counts', async () => {
    const { logSanitizedError, warnSanitized } = await import('@/utils/safeLogging');
    for (let index = 0; index < 10_000; index++) {
      if (index % 2) logSanitizedError('Synthetic error');
      else warnSanitized('Synthetic warning');
    }
    expect(console.error).toHaveBeenCalledTimes(5_000);
    expect(console.warn).toHaveBeenCalledTimes(5_000);
    expect(request).not.toHaveBeenCalled();
    await settle();
    expect(request).toHaveBeenCalledTimes(2);
    expect([payload(0).occurrenceCount, payload(1).occurrenceCount]).toEqual([5000, 5000]);
  });

  it('resolves omitted, undefined, null and explicit stacks consistently locally and remotely', async () => {
    const error = new Error('password=synthetic-secret');
    error.stack = 'Error: password=synthetic-secret\n at a (app.js:2:3)';
    clientLogger.error('Failed', error);
    clientLogger.warn('Failed', error, { stackTrace: undefined });
    clientLogger.error('Failed', error, { stackTrace: null });
    clientLogger.warn('Failed', error, { stackTrace: 'Trace\n at minified (app.js:1:1)' });
    await settle();
    expect(payload(0).stackTrace).toBe('Error: password=<redacted>\n at a (app.js:2:3)');
    await settle();
    expect(payload(1).stackTrace).toBe(payload(0).stackTrace);
    await settle();
    expect(payload(2).stackTrace).toBeNull();
    expect(vi.mocked(console.error).mock.calls[1]![1]).toMatchObject({ stackTrace: null });
    await settle();
    expect(payload(3).stackTrace).toContain('\n at minified');
  });

  it('preserves Axios stacks while excluding request bodies, private state and circular context', async () => {
    const error = new AxiosError('Network token=synthetic-secret', 'ERR_NETWORK', {
      method: 'get',
      headers: {},
    } as never);
    error.stack = 'AxiosError: token=synthetic-secret\n at xhr (bundle.js:9:2)';
    const context: Record<string, unknown> = {
      operation: 'load',
      trackId: 'private-track',
      health: 'private-health',
      body: 'private-body',
    };
    context.circular = context;
    clientLogger.warn('HTTP failure', error, { context });
    await settle();
    expect(payload()).toMatchObject({
      stackTrace: 'AxiosError: token=<redacted>\n at xhr (bundle.js:9:2)',
      context: { method: 'get', errorType: 'AxiosError', operation: 'load' },
    });
    expect(JSON.stringify(payload())).not.toContain('private-');
  });

  it.each([
    { status: 404, statusText: 'Not Found' },
    { status: 503, statusText: 'Service Unavailable' },
  ])('includes generated API status $status in local and uploaded diagnostics', async ({ status, statusText }) => {
    const { ResponseError } = await import('x8ing-mtl-api-typescript-fetch');
    const { warnRequestFailure } = await import('@/utils/safeLogging');
    const response = new Response('synthetic-private-response-body', { status, statusText });
    const error = new ResponseError(response, 'Response returned an error code');
    warnRequestFailure('API request failed', error, { topic1: 'tracks' });
    await settle();
    expect(request).toHaveBeenCalledTimes(1);
    expect(payload()).toMatchObject({
      topic1: 'tracks',
      stackTrace: error.stack,
      context: { errorType: 'ResponseError', status, statusText },
    });
    expect(vi.mocked(console.warn).mock.calls[0]![1]).toMatchObject({ context: { status, statusText } });
    expect(response.bodyUsed).toBe(false);
    expect(JSON.stringify(payload())).not.toContain('synthetic-private-response-body');
  });

  it('keeps message-only and non-Error rejections eligible without fabricating stacks', async () => {
    clientLogger.warn('Warning only');
    clientLogger.error('Error only');
    clientLogger.error('Rejected', 'token=synthetic-secret');
    clientLogger.error('Rejected', 42);
    await settle();
    expect(request).toHaveBeenCalledTimes(4);
    await settle();
    expect(payload(0).stackTrace).toBeUndefined();
    await settle();
    expect(payload(1).stackTrace).toBeUndefined();
    await settle();
    expect(payload(2).detail).toBe('token=<redacted>');
  });

  it('redacts URL credentials and relative URL queries while preserving stack locations', async () => {
    clientLogger.error('Failed at https://synthetic-user:synthetic-password@example.test/api', undefined, {
      stackTrace: 'Trace\n at x (/mtl/app.js?search=synthetic-private:7:3)',
      detail: 'Request /mtl/api/filter?search=synthetic-private',
    });
    await settle();
    expect(payload().message).toBe('Failed at https://<redacted>@example.test/api');
    expect(payload().stackTrace).toBe('Trace\n at x (/mtl/app.js?<redacted>:7:3)');
    expect(payload().detail).toBe('Request /mtl/api/filter?<redacted>');
    expect(JSON.stringify(vi.mocked(console.error).mock.calls)).not.toContain('synthetic-private');
    expect(JSON.stringify(payload())).not.toContain('synthetic-password');
  });

  it('retains diagnostics before authentication without consuming the upload budget', async () => {
    localStorage.clear();
    for (let index = 0; index < 120; index++) clientLogger.warn('Before login');
    await settle();
    expect(request).not.toHaveBeenCalled();
    localStorage.setItem('mtl.jwt', token('fresh-login'));
    await settle();
    expect(request).toHaveBeenCalledTimes(1);
    expect(payload()).toMatchObject({ message: 'Before login', occurrenceCount: 120 });
  });

  it('aborts a stalled upload after five seconds and contains preparation failures', async () => {
    request.mockImplementation(
      (_url, init) =>
        new Promise((_resolve, reject) => {
          init.signal.addEventListener('abort', () => reject(new Error('timeout')));
        })
    );
    clientLogger.error('Stalled');
    await store.idle();
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
    const sending = delivery.drain();
    await vi.waitFor(() => expect(request).toHaveBeenCalledTimes(1));
    await vi.advanceTimersByTimeAsync(5_000);
    await sending;
    expect(request.mock.calls[0]![1].signal.aborted).toBe(true);
    const bad = Object.defineProperty({}, 'stack', {
      get() {
        throw new Error('serialization failure');
      },
    });
    expect(() => clientLogger.error('Bad value', bad)).not.toThrow();
    expect(request).toHaveBeenCalledTimes(1);
  });

  it('routes Vue, browser and original rejection errors through one registered handler set', async () => {
    const { installGlobalErrorHandlers } = await import('@/bootstrap/globalErrorHandlers');
    const app = { config: {} } as never;
    installGlobalErrorHandlers(app);
    installGlobalErrorHandlers(app);
    const error = new Error('synthetic');
    error.stack = 'Error: synthetic\n at original (app.js:1:2)';
    (app as { config: { errorHandler: (e: unknown, instance: unknown, info: string) => void } }).config.errorHandler(
      error,
      null,
      'render'
    );
    window.dispatchEvent(
      new ErrorEvent('error', {
        message: 'Synthetic browser failure',
        error,
        filename: 'https://example.test/app.js?token=private',
        lineno: 1,
        colno: 2,
      })
    );
    const rejection = new Event('unhandledrejection');
    Object.defineProperty(rejection, 'reason', { value: error });
    window.dispatchEvent(rejection);
    await settle();
    expect(request).toHaveBeenCalledTimes(3);
    for (let index = 0; index < 3; index++) expect(payload(index).stackTrace).toBe(error.stack);
    await settle();
    expect(payload(1).context.source).toBe('app.js');
  });

  it('keeps original Error and Axios stacks and metadata through startup timer reports', async () => {
    localStorage.setItem('mtl.startup.crash-guard', '1');
    vi.spyOn(console, 'log').mockImplementation(() => {});
    const { startStartupTimer } = await import('@/utils/startupDiagnostics');
    const error = new AxiosError('Synthetic failure', 'ERR_NETWORK', { method: 'get' } as never);
    error.stack = 'AxiosError: synthetic\n at request (bundle.js:8:2)';
    const timer = startStartupTimer('verification', 'Synthetic operation');
    timer.error('Failed', error);
    timer.warn('Failed', error);
    await settle();
    expect(request).toHaveBeenCalledTimes(2);
    for (let index = 0; index < 2; index++) {
      expect(payload(index)).toMatchObject({
        stackTrace: error.stack,
        context: { errorType: 'AxiosError', code: 'ERR_NETWORK', method: 'get' },
      });
    }
  });
});

it('preserves Error and Axios stacks in the sanitizer and console wrapper', async () => {
  const { sanitizeForLog } = await import('@/utils/safeLogging');
  const error = new Error('Bearer synthetic-secret');
  error.stack = 'Error: Bearer synthetic-secret\n at x (https://example.test/app.js?token=secret:4:5)';
  const clean = sanitizeForLog(error) as { message: string; stack: string };
  expect(clean.message).toBe('Bearer <redacted>');
  expect(clean.stack).toContain('\n at x (https://example.test/app.js?<redacted>:4:5)');
  expect(sanitizeForLog(new AxiosError('token=secret'))).toHaveProperty('stack');
  expect(sanitizeForLog('{"password":"synthetic private secret"}')).toBe('{"password=<redacted>}');
});
