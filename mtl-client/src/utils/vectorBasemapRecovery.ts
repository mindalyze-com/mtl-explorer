import { warnSanitized } from '@/utils/safeLogging';
import type { Map as MapLibreMap, VectorTileSource } from 'maplibre-gl';
import { isAbortLikeError } from '@/utils/errors';
import { probeVectorMapArchive } from '@/utils/mapArchiveHealth';

const VECTOR_SOURCE_ID = 'protomaps';
const ARCHIVE_CHECK_DELAYS_MS = [0, 1000, 3000];
const RECOVERY_INTERVAL_MS = 30_000;

type MapError = { sourceId?: unknown; error?: { message?: string }; tile?: unknown };

interface RecoveryOptions {
  map: MapLibreMap;
  archiveUrl: string;
  isCurrent: () => boolean;
  fallbackApplied?: boolean;
  onUnavailable: () => void | Promise<void>;
  onRestored: () => void | Promise<void>;
  onAuthFailure?: () => void;
}

/** A tile error is not evidence that the whole basemap is unavailable. */
export class VectorBasemapRecovery {
  private readonly controller = new AbortController();
  private checking = false;
  private fallbackApplied: boolean;
  private lastHealthyCheck = Number.NEGATIVE_INFINITY;
  private recoveryTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(private readonly options: RecoveryOptions) {
    this.fallbackApplied = options.fallbackApplied === true;
    if (this.fallbackApplied) this.scheduleRecovery();
  }

  handleError(event: MapError): boolean {
    if (
      !this.isCurrent() ||
      this.fallbackApplied ||
      event.sourceId !== VECTOR_SOURCE_ID ||
      isAbortLikeError(event.error) ||
      event.error?.message?.includes('Map archive changed; refreshing map config.')
    )
      return false;
    if (this.checking || Date.now() - this.lastHealthyCheck < RECOVERY_INTERVAL_MS) return true;
    this.checking = true;
    void this.confirmAvailability(event);
    return true;
  }

  dispose(): void {
    this.controller.abort();
    if (this.recoveryTimer !== null) clearTimeout(this.recoveryTimer);
    this.recoveryTimer = null;
  }

  private isCurrent(): boolean {
    return !this.controller.signal.aborted && this.options.isCurrent();
  }

  private async confirmAvailability(event: MapError): Promise<void> {
    try {
      for (const delay of ARCHIVE_CHECK_DELAYS_MS) {
        if (delay) await this.wait(delay);
        if (!this.isCurrent()) return;
        try {
          await probeVectorMapArchive(this.options.archiveUrl, this.controller.signal);
        } catch (error) {
          if (!this.isCurrent()) return;
          if (this.handleSpecialFailure(error)) return;
          warnSanitized('[MTL] Vector archive availability check failed.', error, {
            topic1: 'application',
            topic2: 'vectorBasemapRecovery',
            topic3: 'confirmAvailability',
          });
          continue;
        }
        if (!this.isCurrent()) return;
        this.lastHealthyCheck = Date.now();
        warnSanitized(
          '[MTL] Vector tile error; archive is healthy. Keeping vector basemap and retrying.',
          event.error,
          { topic1: 'application', topic2: 'vectorBasemapRecovery', topic3: 'confirmAvailability' }
        );
        // Retry once per check window; a broken individual tile must not cause a reload loop.
        if (event.tile) this.options.map.refreshTiles(VECTOR_SOURCE_ID);
        else {
          const source = this.options.map.getSource<VectorTileSource>(VECTOR_SOURCE_ID);
          if (source?.url) source.setUrl(source.url);
        }
        return;
      }
      if (!this.isCurrent()) return;
      this.fallbackApplied = true;
      warnSanitized('Vector map unavailable; using fallback basemap', event.error, {
        topic1: 'map',
        topic2: 'vectorBasemapRecovery',
        topic3: 'fallback',
      });
      await this.options.onUnavailable();
      if (this.isCurrent()) this.scheduleRecovery();
    } catch (error) {
      if (this.isCurrent())
        warnSanitized('[MTL] Vector basemap recovery failed.', error, {
          topic1: 'application',
          topic2: 'vectorBasemapRecovery',
          topic3: 'confirmAvailability',
        });
    } finally {
      this.checking = false;
    }
  }

  private handleSpecialFailure(error: unknown): boolean {
    const message = error instanceof Error ? error.message : String(error);
    if (message.includes('401')) {
      this.options.onAuthFailure?.();
      return true;
    }
    // The source dispatches the existing archive-change event; let that refresh its configuration.
    return message.includes('Map archive changed; refreshing map config.');
  }

  private scheduleRecovery(): void {
    this.recoveryTimer = setTimeout(() => {
      this.recoveryTimer = null;
      void this.restoreWhenAvailable();
    }, RECOVERY_INTERVAL_MS);
  }

  private async restoreWhenAvailable(): Promise<void> {
    if (!this.isCurrent()) return;
    try {
      await probeVectorMapArchive(this.options.archiveUrl, this.controller.signal);
      if (!this.isCurrent()) return;
      await this.options.onRestored();
      this.fallbackApplied = false;
    } catch (error) {
      if (!this.isCurrent() || this.handleSpecialFailure(error)) return;
      warnSanitized('[MTL] Vector archive still unavailable; will retry recovery.', error, {
        topic1: 'application',
        topic2: 'vectorBasemapRecovery',
        topic3: 'restoreWhenAvailable',
      });
      this.scheduleRecovery();
    }
  }

  private wait(delay: number): Promise<void> {
    return new Promise((resolve) => {
      const onAbort = () => {
        clearTimeout(timer);
        resolve();
      };
      const timer = setTimeout(() => {
        this.controller.signal.removeEventListener('abort', onAbort);
        resolve();
      }, delay);
      this.controller.signal.addEventListener('abort', onAbort, { once: true });
    });
  }
}
