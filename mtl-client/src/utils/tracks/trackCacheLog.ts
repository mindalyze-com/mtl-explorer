const LOG_PREFIX = '[track-cache]';

function nowMs(): number {
  return typeof performance !== 'undefined' ? performance.now() : Date.now();
}

export function trackCacheNow(): number {
  return nowMs();
}

export function trackCacheElapsedMs(startedAt: number): number {
  return Math.round(nowMs() - startedAt);
}

export function logTrackCache(message: string, details?: Record<string, unknown>): void {
  if (details === undefined) {
    console.info(`${LOG_PREFIX} ${message}`);
    return;
  }
  console.info(`${LOG_PREFIX} ${message}`, details);
}
