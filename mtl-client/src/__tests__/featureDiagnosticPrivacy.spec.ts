import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import 'fake-indexeddb/auto';
let store: (typeof import('@/utils/clientDiagnosticStore'))['clientDiagnosticStore'];
let delivery: InstanceType<(typeof import('@/utils/clientDiagnosticDelivery'))['ClientDiagnosticDelivery']>;

vi.mock('@/utils/openApiClient', async () => {
  const { Configuration } = await import('x8ing-mtl-api-typescript-fetch');
  return { getApiConfiguration: () => new Configuration({ basePath: '/mtl' }) };
});

beforeEach(async () => {
  vi.resetModules();
  localStorage.clear();
  localStorage.setItem(
    'mtl.jwt',
    `${btoa('{}')}.${btoa(JSON.stringify({ sub: 'synthetic', exp: Date.now() / 1000 + 86400, client_login_ref: 'synthetic-login' }))}.signature`
  );
  store = (await import('@/utils/clientDiagnosticStore')).clientDiagnosticStore;
  await store.clear();
  store.resume();
  delivery = new (await import('@/utils/clientDiagnosticDelivery')).ClientDiagnosticDelivery(store);
  vi.spyOn(console, 'warn').mockImplementation(() => {});
});
afterEach(async () => {
  await delivery.stop();
  await store.clear();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
  localStorage.clear();
});

it('reports a health transport failure before displaying its private response body', async () => {
  const reports: unknown[] = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url, init) => {
      if (String(url).endsWith('/api/logs/client')) {
        reports.push(JSON.parse(init.body));
        return new Response(null, { status: 204 });
      }
      return new Response(JSON.stringify({ detail: 'synthetic-private-health-response' }), {
        status: 503,
        headers: { 'Content-Type': 'application/problem+json' },
      });
    })
  );
  const { googleHealthApi } = await import('@/features/health/api/googleHealthApi');
  await expect(googleHealthApi.status()).rejects.toThrow('synthetic-private-health-response');
  await store.idle();
  await delivery.drain();
  expect(reports).toHaveLength(1);
  expect(reports[0]).toMatchObject({ topic1: 'health', topic3: 'status', context: { status: 503 } });
  expect(JSON.stringify(reports)).not.toContain('synthetic-private-health-response');
});

it('keeps expected health validation and authorization responses local', async () => {
  const fetchMock = vi.fn(
    async () =>
      new Response(JSON.stringify({ detail: 'Synthetic validation response' }), {
        status: 409,
        headers: { 'Content-Type': 'application/problem+json' },
      })
  );
  vi.stubGlobal('fetch', fetchMock);
  const { googleHealthApi } = await import('@/features/health/api/googleHealthApi');
  await expect(googleHealthApi.status()).rejects.toThrow('Synthetic validation response');
  expect(fetchMock).toHaveBeenCalledTimes(1);
  expect(console.warn).not.toHaveBeenCalled();
});

it('redacts response excerpts included by JSON decoder exceptions', async () => {
  const reports: unknown[] = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url, init) => {
      if (String(url).endsWith('/api/logs/client')) {
        reports.push(JSON.parse(init.body));
        return new Response(null, { status: 204 });
      }
      return new Response('synthetic-private-health', { status: 200, headers: { 'Content-Type': 'application/json' } });
    })
  );
  const { googleHealthApi } = await import('@/features/health/api/googleHealthApi');
  await expect(googleHealthApi.status()).rejects.toBeInstanceOf(SyntaxError);
  await store.idle();
  await delivery.drain();
  expect(reports).toHaveLength(1);
  expect(JSON.stringify(reports)).not.toContain('synthetic-private');
  expect(reports[0]).toMatchObject({
    detail: 'The response could not be decoded',
    context: { errorType: 'SyntaxError' },
  });
});

it('reports review failures without copying private source-response details', async () => {
  const reports: unknown[] = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (_url, init) => {
      reports.push(JSON.parse(init.body));
      return new Response(null, { status: 204 });
    })
  );
  const { ResponseError } = await import('x8ing-mtl-api-typescript-fetch');
  const { reviewError } = await import('@/features/track-review/api');
  const error = new ResponseError(
    new Response(JSON.stringify({ detail: 'synthetic-private-source-response' }), {
      status: 503,
      headers: { 'Content-Type': 'application/json' },
    })
  );
  expect(await reviewError(error, 'load')).toBe('synthetic-private-source-response');
  await store.idle();
  await delivery.drain();
  expect(reports).toHaveLength(1);
  expect(reports[0]).toMatchObject({ topic1: 'review', topic3: 'load', stackTrace: error.stack });
  expect(JSON.stringify(reports)).not.toContain('synthetic-private-source-response');
});

it('preserves explicit nullable and supplied stacks when reporting decoder errors', async () => {
  const reports: unknown[] = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (_url, init) => {
      reports.push(JSON.parse(init.body));
      return new Response(null, { status: 204 });
    })
  );
  const { warnRequestFailure } = await import('@/utils/safeLogging');
  const error = new SyntaxError('synthetic-private-response');
  warnRequestFailure('Decode failed', error, { stackTrace: null });
  warnRequestFailure('Decode failed', error, { stackTrace: 'Safe trace\n at original (bundle.js:1:2)' });
  await store.idle();
  await delivery.drain();
  expect(reports).toHaveLength(2);
  expect(reports[0]).toMatchObject({ stackTrace: null });
  expect(reports[1]).toMatchObject({ stackTrace: 'Safe trace\n at original (bundle.js:1:2)' });
  expect(JSON.stringify(reports)).not.toContain('synthetic-private-response');
});
