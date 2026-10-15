import Dexie, { type Table } from 'dexie';
import type { ClientLogRequest } from 'x8ing-mtl-api-typescript-fetch';
import { backendBasePath } from '@/utils/apiBase';
import { getDiagnosticSession, type DiagnosticSession } from '@/utils/diagnosticApiConfiguration';

export const CLIENT_DIAGNOSTIC_DB = 'mtl_client_diagnostics';
export const DIAGNOSTIC_MAX_RECORDS = 250;
export const DIAGNOSTIC_MAX_BYTES = 4 * 1024 * 1024;
export const DIAGNOSTIC_WINDOW_MS = 60_000;
export const DIAGNOSTIC_MAX_ATTEMPTS = 100;
const MAX_REQUEST_BYTES = 128 * 1024;
const MESSAGE_CHARS = 4096;
const DETAIL_CHARS = 16384;
const STACK_CHARS = 65536;
const TOPIC_CHARS = 255;
const BUILD_CHARS = 128;
const CONTEXT_BYTES = 8192;
const RETRY_BASE_MS = 1000;
const RETRY_MAX_EXPONENT = 6;
const MAX_OCCURRENCES = 2_147_483_647;
const LEASE_MS = 15_000;
export type DiagnosticStatus = 'pending' | 'sent' | 'rejected';

export interface DiagnosticRecord {
  sequence: number;
  event: ClientLogRequest;
  scope: string;
  account: string | null;
  fingerprint: string;
  status: DiagnosticStatus;
  frozen: boolean;
  bytes: number;
  attempts: number;
  sentAt?: number;
  rejection?: string;
}

interface DeliveryState {
  key: string;
  attempts: number[];
  failures: number;
  nextAttemptAt: number;
  leaseOwner?: string;
  leaseUntil?: number;
  authBlocked?: boolean;
  blockedLoginRef?: string | null;
}

class DiagnosticDatabase extends Dexie {
  records!: Table<DiagnosticRecord, number>;
  state!: Table<DeliveryState, string>;
  constructor(name: string) {
    super(name);
    this.version(1).stores({ records: 'sequence,&event.clientEventId,status', state: 'key' });
  }
}

interface Snapshot {
  records: DiagnosticRecord[];
  state: DeliveryState;
}

function bytes(event: ClientLogRequest): number {
  return new TextEncoder().encode(JSON.stringify(event)).byteLength;
}

function content(event: ClientLogRequest): string {
  const { clientEventId: _id, occurredAt: _at, lastOccurredAt: _last, occurrenceCount: _count, ...content } = event;
  return JSON.stringify(content);
}

export { createDiagnosticUuid as newClientEventId } from '@/utils/random';

function fingerprint(event: ClientLogRequest): string {
  let hash = 2166136261;
  for (const char of content(event)) hash = Math.imul(hash ^ char.charCodeAt(0), 16777619);
  return (hash >>> 0).toString(16);
}

function trim(records: DiagnosticRecord[]): DiagnosticRecord[] {
  let total = records.reduce((sum, record) => sum + record.bytes, 0);
  let first = 0;
  while (records.length - first > DIAGNOSTIC_MAX_RECORDS || total > DIAGNOSTIC_MAX_BYTES) {
    total -= records[first++]!.bytes;
  }
  return records.slice(first);
}

function merge(records: DiagnosticRecord[], incoming: DiagnosticRecord): DiagnosticRecord[] {
  if (records.some((record) => record.event.clientEventId === incoming.event.clientEventId)) return records;
  const reversedMatch = [...records].reverse().findIndex((record) => {
    const gap =
      incoming.event.occurredAt!.getTime() - (record.event.lastOccurredAt ?? record.event.occurredAt)!.getTime();
    return (
      record.status === 'pending' &&
      !record.frozen &&
      record.scope === incoming.scope &&
      record.account?.toLowerCase() === incoming.account?.toLowerCase() &&
      record.fingerprint === incoming.fingerprint &&
      content(record.event) === content(incoming.event) &&
      gap >= 0 &&
      gap < DIAGNOSTIC_WINDOW_MS
    );
  });
  const match = reversedMatch < 0 ? -1 : records.length - 1 - reversedMatch;
  if (match >= 0) {
    const original = records[match]!;
    const event = {
      ...original.event,
      lastOccurredAt: incoming.event.lastOccurredAt ?? incoming.event.occurredAt,
      occurrenceCount: Math.min(
        MAX_OCCURRENCES,
        (original.event.occurrenceCount ?? 1) + (incoming.event.occurrenceCount ?? 1)
      ),
    };
    records[match] = { ...original, event, bytes: bytes(event) };
  } else {
    records.push({ ...incoming, sequence: (records.at(-1)?.sequence ?? 0) + 1 });
  }
  return trim(records);
}

/** No dependency on the logger: storage failures must never report themselves. */
export class ClientDiagnosticStore {
  private db: DiagnosticDatabase;
  private shadow: DiagnosticRecord[] = [];
  private states = new Map<string, DeliveryState>();
  private staging: DiagnosticRecord[] = [];
  private flushing?: Promise<void>;
  private memoryOnly = false;
  private generation = 0;
  private suspended = false;

  constructor(name = CLIENT_DIAGNOSTIC_DB) {
    this.db = this.createDatabase(name);
  }

  private createDatabase(name: string): DiagnosticDatabase {
    const db = new DiagnosticDatabase(name);
    db.on('versionchange', (event) => {
      db.close();
      if (event.newVersion === null) {
        // Another tab's forget action must also clear this tab's memory mirror.
        this.generation += 1;
        this.staging = [];
        this.shadow = [];
        this.states.clear();
        this.db = this.createDatabase(name);
      }
    });
    return db;
  }

  capture(event: ClientLogRequest, session = getDiagnosticSession(), scope = backendBasePath): Promise<void> {
    if (this.suspended) return Promise.resolve();
    let rejection: string | undefined;
    if (
      bytes(event) > MAX_REQUEST_BYTES ||
      event.message.length > MESSAGE_CHARS ||
      (event.detail?.length ?? 0) > DETAIL_CHARS ||
      (event.stackTrace?.length ?? 0) > STACK_CHARS ||
      [event.topic1, event.topic2, event.topic3].some((topic) => (topic?.length ?? 0) > TOPIC_CHARS) ||
      event.buildId.length > BUILD_CHARS ||
      new TextEncoder().encode(JSON.stringify(event.context ?? {})).byteLength > CONTEXT_BYTES
    ) {
      rejection = 'payload-too-large';
      // Retain a bounded local record of rejection, without truncating a trace for upload.
      event = {
        ...event,
        message: event.message.slice(0, MESSAGE_CHARS),
        detail: undefined,
        stackTrace: null,
        context: undefined,
        topic1: event.topic1?.slice(0, TOPIC_CHARS),
        topic2: event.topic2?.slice(0, TOPIC_CHARS),
        topic3: event.topic3?.slice(0, TOPIC_CHARS),
        buildId: event.buildId.slice(0, BUILD_CHARS),
      };
    }
    this.staging = merge(this.staging, {
      sequence: 0,
      event,
      scope,
      account: session?.account ?? null,
      fingerprint: fingerprint(event),
      status: rejection ? 'rejected' : 'pending',
      rejection,
      frozen: false,
      bytes: bytes(event),
      attempts: 0,
    });
    this.startFlush();
    return this.flushing!;
  }

  private startFlush(): void {
    if (this.flushing || this.suspended || !this.staging.length) return;
    this.flushing = this.flush(this.generation).finally(() => {
      this.flushing = undefined;
      this.startFlush();
    });
  }

  private async flush(generation: number): Promise<void> {
    // Start persistence immediately; batch captures that arrive while a transaction is running.
    await Promise.resolve();
    while (this.staging.length && generation === this.generation) {
      const batch = this.staging;
      this.staging = [];
      await this.transaction(batch[0]!.scope, (snapshot) => {
        for (const incoming of batch) snapshot.records = merge(snapshot.records, incoming);
      });
    }
  }

  async idle(): Promise<void> {
    while (this.flushing) await this.flushing;
  }

  private async transaction<T>(scope: string, operation: (snapshot: Snapshot) => T): Promise<T> {
    const generation = this.generation;
    const freshState = (): DeliveryState => ({ key: scope, attempts: [], failures: 0, nextAttemptAt: 0 });
    if (!this.memoryOnly) {
      try {
        const db = this.db;
        const committed = await db.transaction('rw', db.records, db.state, async () => {
          const before = await db.records.orderBy('sequence').toArray();
          const snapshot = { records: [...before], state: (await db.state.get(scope)) ?? freshState() };
          const previousState = JSON.stringify(snapshot.state);
          const result = operation(snapshot);
          if (generation !== this.generation) throw new Error('Diagnostic storage was cleared');
          const remaining = new Set(snapshot.records.map((record) => record.sequence));
          await db.records.bulkDelete(
            before.filter((record) => !remaining.has(record.sequence)).map((record) => record.sequence)
          );
          const previous = new Map(before.map((record) => [record.sequence, record]));
          await db.records.bulkPut(snapshot.records.filter((record) => previous.get(record.sequence) !== record));
          if (JSON.stringify(snapshot.state) !== previousState) await db.state.put(snapshot.state);
          return { result, snapshot };
        });
        // Mirror only committed data for a later runtime storage failure.
        this.shadow = committed.snapshot.records;
        this.states.set(scope, committed.snapshot.state);
        return committed.result;
      } catch {
        if (generation !== this.generation) return undefined as T;
        this.memoryOnly = true;
        this.db.close();
      }
    }
    const snapshot = { records: [...this.shadow], state: { ...(this.states.get(scope) ?? freshState()) } };
    const result = operation(snapshot);
    this.shadow = snapshot.records;
    this.states.set(scope, snapshot.state);
    return result;
  }

  async claim(
    session: DiagnosticSession,
    owner: string,
    now = Date.now(),
    scope = backendBasePath
  ): Promise<DiagnosticRecord | undefined> {
    await this.idle();
    if (this.suspended) return;
    if (!this.memoryOnly) {
      const generation = this.generation;
      try {
        // Sent records remain in local history; avoid loading their payloads on idle ticks.
        const pending = await this.db.records.where('status').equals('pending').count();
        if (generation !== this.generation || pending === 0) return;
      } catch {
        if (generation !== this.generation) return;
        this.memoryOnly = true;
        this.db.close();
      }
    }
    return this.transaction(scope, (snapshot) => {
      const state = snapshot.state;
      if (state.leaseOwner && state.leaseOwner !== owner && (state.leaseUntil ?? 0) > now) return;
      if (state.authBlocked && state.blockedLoginRef === session.loginRef) return;
      state.authBlocked = false;
      state.attempts = state.attempts.filter((at) => at <= now && at > now - DIAGNOSTIC_WINDOW_MS);
      if (state.nextAttemptAt > now || state.attempts.length >= DIAGNOSTIC_MAX_ATTEMPTS) return;
      const index = snapshot.records.findIndex(
        (record) =>
          record.status === 'pending' &&
          record.scope === scope &&
          (record.account === null || record.account.toLowerCase() === session.account.toLowerCase())
      );
      if (index < 0) return;
      const record = {
        ...snapshot.records[index]!,
        account: session.account,
        frozen: true,
        attempts: snapshot.records[index]!.attempts + 1,
      };
      snapshot.records[index] = record;
      state.leaseOwner = owner;
      state.leaseUntil = now + LEASE_MS;
      state.attempts.push(now);
      return record;
    });
  }

  async finish(
    record: DiagnosticRecord,
    owner: string,
    outcome: 'sent' | 'rejected' | 'retry' | 'auth',
    session: DiagnosticSession,
    retryAfter = 0,
    now = Date.now()
  ): Promise<void> {
    await this.transaction(record.scope, (snapshot) => {
      const state = snapshot.state;
      if (state.leaseOwner !== owner) return;
      delete state.leaseOwner;
      delete state.leaseUntil;
      const index = snapshot.records.findIndex((entry) => entry.event.clientEventId === record.event.clientEventId);
      if (outcome === 'sent' || outcome === 'rejected') {
        if (index >= 0)
          snapshot.records[index] = {
            ...snapshot.records[index]!,
            status: outcome,
            sentAt: outcome === 'sent' ? now : undefined,
            rejection: outcome === 'rejected' ? 'server-rejected' : undefined,
          };
        state.failures = 0;
        state.nextAttemptAt = 0;
      } else if (outcome === 'auth') {
        state.authBlocked = true;
        state.blockedLoginRef = session.loginRef;
      } else {
        state.failures += 1;
        state.nextAttemptAt =
          now +
          Math.max(
            retryAfter,
            Math.min(DIAGNOSTIC_WINDOW_MS, RETRY_BASE_MS * 2 ** Math.min(state.failures, RETRY_MAX_EXPONENT))
          );
      }
      snapshot.records = trim(snapshot.records);
    });
  }

  async records(): Promise<DiagnosticRecord[]> {
    await this.idle();
    return this.transaction(backendBasePath, (snapshot) => [...snapshot.records]);
  }

  async clear(): Promise<void> {
    this.suspended = true;
    this.generation += 1;
    this.staging = [];
    await this.idle();
    this.shadow = [];
    this.states.clear();
    try {
      await this.db.delete();
    } catch {
      /* Browser storage may already be unavailable. */
    }
    this.db.close();
  }

  resume(): void {
    if (this.suspended) this.db = this.createDatabase(this.db.name);
    this.suspended = false;
    this.memoryOnly = false;
  }
  close(): void {
    this.db.close();
  }
}

export const clientDiagnosticStore = new ClientDiagnosticStore();
