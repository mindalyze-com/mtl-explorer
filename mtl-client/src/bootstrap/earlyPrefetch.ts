import { isAuthenticated } from '@/utils/auth';
import { trackStore, OVERVIEW_PRECISION } from '@/utils/trackStore';
import { fetchMapConfig } from '@/utils/mapConfigService';
import { startupLog } from '@/utils/startupDiagnostics';

/**
 * Kick off network requests immediately for returning users so they run in
 * parallel with Vue mount, PrimeVue init, and MapLibre setup.
 *
 * Extracted from main.ts.
 */
export function startEarlyPrefetch(): void {
  if (!isAuthenticated()) return;
  startupLog('boot', 'Returning user detected — starting early prefetch');
  trackStore.prefetchAllTracks(OVERVIEW_PRECISION);
  trackStore.prefetchAllTracks(10);
  // Warm the module-level cache so initMap() gets it instantly.
  fetchMapConfig();
}
