import { ClientLogControllerApi, ResponseError } from 'x8ing-mtl-api-typescript-fetch';
import { clientDiagnosticStore, newClientEventId, type ClientDiagnosticStore } from '@/utils/clientDiagnosticStore';
import {
  DIAGNOSTIC_AUTH_CHANGED,
  getDiagnosticApiConfiguration,
  getDiagnosticSession,
} from '@/utils/diagnosticApiConfiguration';
import { STORAGE_KEYS } from '@/utils/appStorage';

const DELIVERY_INTERVAL_MS = 1000;
const UPLOAD_TIMEOUT_MS = 5000;
const MAX_PER_RUN = 25;

function retryAfter(response: Response): number {
  const value = response.headers.get('Retry-After');
  if (!value) return 0;
  const seconds = Number(value);
  return Math.max(0, Number.isFinite(seconds) ? seconds * 1000 : Date.parse(value) - Date.now()) || 0;
}

export class ClientDiagnosticDelivery {
  private owner = newClientEventId();
  private timer?: ReturnType<typeof setTimeout>;
  private running?: Promise<void>;
  private controller?: AbortController;
  private started = false;
  private stopped = false;
  private lifecycleRevision = 0;

  constructor(private store: ClientDiagnosticStore = clientDiagnosticStore) {}

  start(): void {
    if (this.started) return;
    this.lifecycleRevision += 1;
    this.started = true;
    this.stopped = false;
    window.addEventListener('online', this.wake);
    window.addEventListener('storage', this.storageChanged);
    window.addEventListener(DIAGNOSTIC_AUTH_CHANGED, this.wake);
    document.addEventListener('visibilitychange', this.visible);
    this.wake();
  }

  private visible = (): void => {
    if (document.visibilityState === 'visible') this.wake();
  };
  private storageChanged = (event: StorageEvent): void => {
    if (event.key === null) {
      // localStorage.clear() is the application's forget-everything action.
      void this.forgetInThisTab().catch(() => {});
    } else if (event.key === STORAGE_KEYS.jwt) this.wake();
  };
  private async forgetInThisTab(): Promise<void> {
    await this.stop();
    const revision = this.lifecycleRevision;
    await this.store.clear();
    if (revision !== this.lifecycleRevision) return;
    this.store.resume();
    this.start();
  }
  private wake = (): void => {
    if (this.running || this.stopped) return;
    clearTimeout(this.timer);
    void this.drain().finally(() => {
      if (this.started && !this.stopped) this.timer = setTimeout(this.wake, DELIVERY_INTERVAL_MS);
    });
  };

  drain(): Promise<void> {
    if (this.running) return this.running;
    this.running = this.run()
      .catch(() => {
        /* Diagnostics cannot affect the application or recursively report. */
      })
      .finally(() => {
        this.running = undefined;
      });
    return this.running;
  }

  private async run(): Promise<void> {
    for (let index = 0; index < MAX_PER_RUN && !this.stopped; index++) {
      const session = getDiagnosticSession();
      const configuration = getDiagnosticApiConfiguration(session);
      if (!session || !configuration) return;
      const record = await this.store.claim(session, this.owner);
      if (!record || this.stopped) return;
      // Credentials may have changed while the storage transaction was pending.
      if (getDiagnosticSession()?.token !== session.token) {
        await this.store.finish(record, this.owner, 'retry', session);
        return;
      }
      this.controller = new AbortController();
      const timeout = setTimeout(() => this.controller?.abort(), UPLOAD_TIMEOUT_MS);
      let outcome: 'sent' | 'rejected' | 'retry' | 'auth' = 'sent';
      let delay = 0;
      try {
        await new ClientLogControllerApi(configuration).submitClientLog(
          { clientLogRequest: record.event },
          { signal: this.controller.signal }
        );
      } catch (error) {
        const status = error instanceof ResponseError ? error.response.status : 0;
        outcome =
          status === 401
            ? 'auth'
            : status >= 400 && status < 500 && status !== 408 && status !== 429
              ? 'rejected'
              : 'retry';
        if (error instanceof ResponseError) delay = retryAfter(error.response);
      } finally {
        clearTimeout(timeout);
        this.controller = undefined;
      }
      if (this.stopped) return;
      await this.store.finish(record, this.owner, outcome, session, delay);
      if (outcome === 'retry' || outcome === 'auth') return;
    }
  }

  async stop(): Promise<void> {
    this.lifecycleRevision += 1;
    this.stopped = true;
    this.started = false;
    clearTimeout(this.timer);
    window.removeEventListener('online', this.wake);
    window.removeEventListener('storage', this.storageChanged);
    window.removeEventListener(DIAGNOSTIC_AUTH_CHANGED, this.wake);
    document.removeEventListener('visibilitychange', this.visible);
    this.controller?.abort();
    await this.running;
  }
}

const delivery = new ClientDiagnosticDelivery();
export function startClientLogDelivery(): void {
  clientDiagnosticStore.resume();
  delivery.start();
}
export async function forgetClientDiagnostics(): Promise<void> {
  await delivery.stop();
  await clientDiagnosticStore.clear();
}
