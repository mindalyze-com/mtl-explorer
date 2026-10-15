import 'fake-indexeddb/auto';
import Dexie from 'dexie';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import type { ClientLogRequest } from 'x8ing-mtl-api-typescript-fetch';
import { ClientDiagnosticStore, DIAGNOSTIC_MAX_BYTES, newClientEventId } from '@/utils/clientDiagnosticStore';
import { ClientDiagnosticDelivery } from '@/utils/clientDiagnosticDelivery';
import { DIAGNOSTIC_AUTH_CHANGED, getDiagnosticSession } from '@/utils/diagnosticApiConfiguration';

let store: ClientDiagnosticStore;
let delivery: ClientDiagnosticDelivery;
let request: ReturnType<typeof vi.fn>;
let dbName: string;
const stores: ClientDiagnosticStore[] = [];
const BASE_TIME = new Date('2026-10-01T12:00:00Z').getTime();

function login(account = 'synthetic', loginRef = 'original-session', expiresAt = Date.now() + 86_400_000) {
  const encode = (value: unknown) => btoa(JSON.stringify(value));
  localStorage.setItem(
    'mtl.jwt',
    `${encode({ alg: 'none' })}.${encode({ sub: account, client_login_ref: loginRef, exp: expiresAt / 1000 })}.signature`
  );
}
function event(message: string, time = BASE_TIME): ClientLogRequest {
  return {
    clientEventId: newClientEventId(),
    occurredAt: new Date(time),
    occurrenceCount: 1,
    originalClientLoginRef: getDiagnosticSession()?.loginRef,
    severity: 'ERROR',
    message,
    buildId: 'synthetic-build',
  };
}
function payload(index = 0) {
  return JSON.parse(request.mock.calls[index]![1].body);
}

beforeEach(() => {
  login();
  dbName = `synthetic-diagnostics-${newClientEventId()}`;
  store = new ClientDiagnosticStore(dbName);
  stores.push(store);
  delivery = new ClientDiagnosticDelivery(store);
  request = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
  vi.stubGlobal('fetch', request);
});
afterEach(async () => {
  await delivery.stop();
  for (const entry of stores.splice(0)) entry.close();
  await Dexie.delete(dbName);
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
  vi.useRealTimers();
  localStorage.clear();
});

it('persists offline events, reopens the queue a day later, and preserves FIFO IDs and occurrence time', async () => {
  const first = event('First');
  const second = event('Second', BASE_TIME + 1);
  await store.capture(first);
  await store.capture(second);
  request.mockRejectedValue(new TypeError('synthetic offline failure'));
  await delivery.drain();
  expect(request).toHaveBeenCalledTimes(1);
  expect((await store.records()).map((row) => row.status)).toEqual(['pending', 'pending']);
  store.close();
  const reopened = new ClientDiagnosticStore(dbName);
  stores.push(reopened);
  await delivery.stop();
  delivery = new ClientDiagnosticDelivery(reopened);
  vi.spyOn(Date, 'now').mockReturnValue(Date.now() + 86_400_000);
  login('synthetic', 'later-session');
  request.mockResolvedValue(new Response(null, { status: 204 }));
  await delivery.drain();
  expect(payload(1)).toMatchObject({
    message: 'First',
    clientEventId: first.clientEventId,
    occurredAt: first.occurredAt!.toISOString(),
    originalClientLoginRef: 'original-session',
  });
  expect(payload(2).message).toBe('Second');
  expect((await reopened.records()).map((row) => row.status)).toEqual(['sent', 'sent']);
  expect(request.mock.calls[1]![1].headers.Authorization).toBe(`Bearer ${localStorage.getItem('mtl.jwt')}`);
});

it('retains only the newest 250 records while offline, ordered by insertion rather than the clock', async () => {
  for (let index = 0; index < 300; index++) void store.capture(event(`Event ${index}`, BASE_TIME - index));
  const records = await store.records();
  expect(records).toHaveLength(250);
  expect(records[0]!.event.message).toBe('Event 50');
  expect(records.at(-1)!.event.message).toBe('Event 299');
  expect(request).not.toHaveBeenCalled();
});

it('bounds payload bytes as well as record count', async () => {
  for (let index = 0; index < 150; index++)
    void store.capture({ ...event(`Large ${index}`), stackTrace: 'x'.repeat(40_000) });
  const records = await store.records();
  expect(records.length).toBeLessThan(150);
  expect(records.reduce((sum, row) => sum + row.bytes, 0)).toBeLessThanOrEqual(DIAGNOSTIC_MAX_BYTES);
  expect(records.at(-1)!.event.message).toBe('Large 149');
});

it('consolidates a repeated flood but freezes payloads before the first attempt', async () => {
  const first = event('Repeated');
  for (let index = 0; index < 1000; index++)
    void store.capture({ ...first, clientEventId: newClientEventId(), occurredAt: new Date(BASE_TIME + index) });
  const records = await store.records();
  expect(records).toHaveLength(1);
  expect(records[0]!.event.occurrenceCount).toBe(1000);
  expect(records[0]!.event.occurredAt).toEqual(new Date(BASE_TIME));
  expect(records[0]!.event.lastOccurredAt).toEqual(new Date(BASE_TIME + 999));
  const claimed = await store.claim(getDiagnosticSession()!, 'sender');
  await store.capture(event('Repeated', BASE_TIME + 2000));
  expect(await store.records()).toHaveLength(2);
  await store.finish(claimed!, 'sender', 'sent', getDiagnosticSession()!);
  expect((await store.records())[0]!.event.occurrenceCount).toBe(1000);
});

it('shares the persistent upload budget across reopen and defers excess records without discarding them', async () => {
  for (let index = 0; index < 110; index++) void store.capture(event(`Budget ${index}`));
  for (let run = 0; run < 5; run++) await delivery.drain();
  expect(request).toHaveBeenCalledTimes(100);
  expect((await store.records()).filter((row) => row.status === 'pending')).toHaveLength(10);
  await delivery.stop();
  store.close();
  const reopened = new ClientDiagnosticStore(dbName);
  stores.push(reopened);
  delivery = new ClientDiagnosticDelivery(reopened);
  await delivery.drain();
  expect(request).toHaveBeenCalledTimes(100);
  const now = Date.now();
  vi.spyOn(Date, 'now').mockReturnValue(now + 60_001);
  await delivery.drain();
  expect(request).toHaveBeenCalledTimes(110);
});

it('allows only one tab to send, recovers an abandoned lease, and ignores stale acknowledgments', async () => {
  await store.capture(event('Shared'));
  const other = new ClientDiagnosticStore(dbName);
  stores.push(other);
  const session = getDiagnosticSession()!;
  const first = await store.claim(session, 'first', BASE_TIME);
  expect(await other.claim(session, 'second', BASE_TIME + 1)).toBeUndefined();
  const recovered = await other.claim(session, 'second', BASE_TIME + 15_001);
  expect(recovered!.event.clientEventId).toBe(first!.event.clientEventId);
  await store.finish(first!, 'first', 'sent', session);
  expect((await other.records())[0]!.status).toBe('pending');
  await other.finish(recovered!, 'second', 'sent', session);
  expect((await store.records())[0]!.status).toBe('sent');
});

it('keeps account-owned reports queued through expiry and another account login', async () => {
  await store.capture(event('Owned'));
  login('other-account');
  await delivery.drain();
  expect(request).not.toHaveBeenCalled();
  login('synthetic', 'expired', Date.now() - 1);
  await delivery.drain();
  expect(request).not.toHaveBeenCalled();
  login('synthetic', 'fresh');
  await delivery.drain();
  expect(payload()).toMatchObject({ message: 'Owned', originalClientLoginRef: 'original-session' });
});

it('delivers persisted reports after the same account logs in with different username casing', async () => {
  login('Synthetic', 'original-session');
  const original = event('Owned before reload');
  await store.capture(original);
  await delivery.stop();
  store.close();
  const reopened = new ClientDiagnosticStore(dbName);
  stores.push(reopened);
  delivery = new ClientDiagnosticDelivery(reopened);
  login('sYnThEtIc', 'later-session');
  await delivery.drain();
  expect(request).toHaveBeenCalledTimes(1);
  expect(payload()).toMatchObject({
    clientEventId: original.clientEventId,
    originalClientLoginRef: 'original-session',
  });
  expect((await reopened.records())[0]!.status).toBe('sent');
});

it('stores pre-login diagnostics and sends them after authentication', async () => {
  localStorage.clear();
  await store.capture(event('Startup before login'));
  await delivery.drain();
  expect(request).not.toHaveBeenCalled();
  login();
  await delivery.drain();
  expect(payload().message).toBe('Startup before login');
});

it('pauses after 401 without clearing credentials and resumes after a new login', async () => {
  await store.capture(event('Unauthorized'));
  request.mockResolvedValue(new Response(null, { status: 401 }));
  const token = localStorage.getItem('mtl.jwt');
  await delivery.drain();
  await delivery.drain();
  expect(request).toHaveBeenCalledTimes(1);
  expect(localStorage.getItem('mtl.jwt')).toBe(token);
  login('synthetic', 'reauthenticated');
  request.mockResolvedValue(new Response(null, { status: 204 }));
  await delivery.drain();
  expect(request).toHaveBeenCalledTimes(2);
});

it('respects Retry-After and leaves the next FIFO record unsent until recovery', async () => {
  await store.capture(event('Throttled'));
  await store.capture(event('Later'));
  request.mockResolvedValue(new Response(null, { status: 429, headers: { 'Retry-After': '30' } }));
  const now = Date.now();
  vi.spyOn(Date, 'now').mockReturnValue(now);
  await delivery.drain();
  vi.mocked(Date.now).mockReturnValue(now + 29_999);
  await delivery.drain();
  expect(request).toHaveBeenCalledTimes(1);
  vi.mocked(Date.now).mockReturnValue(now + 30_001);
  request.mockResolvedValue(new Response(null, { status: 204 }));
  await delivery.drain();
  expect([payload(1).message, payload(2).message]).toEqual(['Throttled', 'Later']);
});

it('rejects a permanently invalid report and continues with the next record', async () => {
  await store.capture(event('Invalid'));
  await store.capture(event('Valid'));
  request.mockResolvedValueOnce(new Response(null, { status: 400 }));
  await delivery.drain();
  expect((await store.records()).map((row) => row.status)).toEqual(['rejected', 'sent']);
});

it('rejects a forbidden historical report without blocking current-session reports or clearing credentials', async () => {
  await store.capture(event('Historical report'));
  login('synthetic', 'later-session');
  await store.capture(event('Current report'));
  const token = localStorage.getItem('mtl.jwt');
  request.mockResolvedValueOnce(new Response(null, { status: 403 }));
  await delivery.drain();
  await delivery.drain();
  expect(request).toHaveBeenCalledTimes(2);
  expect([payload(0).originalClientLoginRef, payload(1).originalClientLoginRef]).toEqual([
    'original-session',
    'later-session',
  ]);
  expect((await store.records()).map((row) => row.status)).toEqual(['rejected', 'sent']);
  expect(localStorage.getItem('mtl.jwt')).toBe(token);
});

it('falls back to bounded memory when IndexedDB cannot open, without recursive logging', async () => {
  const previous = Dexie.dependencies.indexedDB;
  Dexie.dependencies.indexedDB = undefined;
  const fallback = new ClientDiagnosticStore(`${dbName}-fallback`);
  Dexie.dependencies.indexedDB = previous;
  stores.push(fallback);
  const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
  for (let index = 0; index < 300; index++) void fallback.capture(event(`Fallback ${index}`));
  expect(await fallback.records()).toHaveLength(250);
  const fallbackDelivery = new ClientDiagnosticDelivery(fallback);
  await fallbackDelivery.drain();
  await fallbackDelivery.stop();
  expect(request).toHaveBeenCalledTimes(25);
  expect(warn).not.toHaveBeenCalled();
});

it('clears pending and fallback reports on forget and can be used again after login', async () => {
  await store.capture(event('Forgotten'));
  await store.clear();
  expect(await store.records()).toHaveLength(0);
  store.resume();
  await store.capture(event('New'));
  await delivery.drain();
  expect(payload().message).toBe('New');
});

it('retains the committed queue in memory after a runtime storage failure', async () => {
  await store.capture(event('Already persisted'));
  const transaction = vi.spyOn(Dexie.prototype, 'transaction').mockImplementation(() => {
    throw new Error('Synthetic storage failure');
  });
  await store.capture(event('Memory after failure'));
  transaction.mockRestore();
  await delivery.drain();
  expect([payload(0).message, payload(1).message]).toEqual(['Already persisted', 'Memory after failure']);
  expect((await store.records()).map((row) => row.status)).toEqual(['sent', 'sent']);
});

it('creates diagnostic IDs even when browser crypto is missing or throws, without recursive warnings', () => {
  const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
  for (const crypto of [
    undefined,
    {},
    {
      randomUUID() {
        throw new Error('Blocked');
      },
      getRandomValues() {
        throw new Error('Blocked');
      },
    },
  ]) {
    vi.stubGlobal('crypto', crypto);
    expect(newClientEventId()).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
  }
  expect(warn).not.toHaveBeenCalled();
});

it('clears another tab memory mirror when the shared database is forgotten', async () => {
  await store.capture(event('Forgotten across tabs'));
  const other = new ClientDiagnosticStore(dbName);
  stores.push(other);
  expect(await other.records()).toHaveLength(1);
  await store.clear();
  expect(await other.records()).toHaveLength(0);
  await other.capture(event('New after forget'));
  expect((await other.records()).map((row) => row.event.message)).toEqual(['New after forget']);
});

it('clears a memory fallback in another tab on the forget storage event', async () => {
  const previous = Dexie.dependencies.indexedDB;
  Dexie.dependencies.indexedDB = undefined;
  const fallback = new ClientDiagnosticStore(`${dbName}-memory-forget`);
  Dexie.dependencies.indexedDB = previous;
  stores.push(fallback);
  await delivery.stop();
  delivery = new ClientDiagnosticDelivery(fallback);
  localStorage.clear();
  delivery.start();
  await fallback.capture(event('Memory report to forget'));
  window.dispatchEvent(new StorageEvent('storage', { key: null }));
  await vi.waitFor(async () => expect(await fallback.records()).toHaveLength(0));
});

it('delivers newly captured reports on its regular timer without a connectivity or login event', async () => {
  vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
  delivery.start();
  await delivery.drain();
  await store.capture(event('Timer delivery'));
  expect(request).not.toHaveBeenCalled();
  await vi.advanceTimersByTimeAsync(999);
  expect(request).not.toHaveBeenCalled();
  await vi.advanceTimersByTimeAsync(1);
  await delivery.drain();
  expect(payload().message).toBe('Timer delivery');
});

it('uses the timer to deliver later captures and wakes after authentication, without overlapping runs', async () => {
  delivery.start();
  await delivery.drain();
  await store.capture(event('Timed'));
  vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
  // A login wake-up also drains captures made after the initial startup run.
  window.dispatchEvent(new Event(DIAGNOSTIC_AUTH_CHANGED));
  await delivery.drain();
  expect(request).toHaveBeenCalledTimes(1);
  let resolve: (response: Response) => void = () => {};
  request.mockImplementation(
    () =>
      new Promise<Response>((done) => {
        resolve = done;
      })
  );
  await store.capture(event('In flight'));
  const first = delivery.drain();
  await vi.waitFor(() => expect(request).toHaveBeenCalledTimes(2));
  expect(delivery.drain()).toBe(first);
  window.dispatchEvent(new Event('online'));
  resolve(new Response(null, { status: 204 }));
  await first;
});
