import { computed, onBeforeUnmount, ref, watch, type ComputedRef, type InjectionKey, type Ref } from 'vue';
import type { EnergyCalculationStatus, GpsTrack } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import { getEnergyCalculationStatus, refreshTrackEnergy } from '@/utils/ServiceHelper';
import { logSanitizedError } from '@/utils/safeLogging';

type EnergyCalculationState = {
  status: Ref<EnergyCalculationStatus | null>;
  pending: ComputedRef<boolean>;
  message: ComputedRef<string>;
};

export const ENERGY_CALCULATION_STATE: InjectionKey<EnergyCalculationState> = Symbol('energyCalculationState');
const STATUS_POLL_INTERVAL_MS = 5000;
const ERROR_RETRY_DELAYS_MS = [5000, 15000, 60000];

type CachedEnergyStatus = {
  status: EnergyCalculationStatus | null;
  error: string;
  failures: number;
  refreshNeeded: boolean;
  retryAt?: number;
  updatedTrack?: GpsTrack;
};
export type EnergyStatusCache = Map<string, CachedEnergyStatus>;
export const ENERGY_STATUS_CACHE: InjectionKey<EnergyStatusCache> = Symbol('energyStatusCache');
const inFlightRefreshes = new Map<number, Promise<GpsTrack>>();
const inFlightStatuses = new Map<number, Promise<EnergyCalculationStatus>>();

function sharedStatus(trackId: number): Promise<EnergyCalculationStatus> {
  const existing = inFlightStatuses.get(trackId);
  if (existing) return existing;
  const request = getEnergyCalculationStatus(trackId).finally(() => inFlightStatuses.delete(trackId));
  inFlightStatuses.set(trackId, request);
  return request;
}

function sharedRefresh(trackId: number): Promise<GpsTrack> {
  const existing = inFlightRefreshes.get(trackId);
  if (existing) return existing;
  const request = refreshTrackEnergy(trackId).finally(() => inFlightRefreshes.delete(trackId));
  inFlightRefreshes.set(trackId, request);
  return request;
}

function cacheKey(track: GpsTrack): string {
  return `${track.id}:${track.version ?? 0}`;
}

export function useEnergyCalculationStatus(
  track: Ref<GpsTrack | null | undefined>,
  onUpdated: (track: GpsTrack) => void,
  options: { cache?: EnergyStatusCache; maxErrorRetries?: number } = {}
) {
  const cache = options.cache ?? new Map<string, CachedEnergyStatus>();
  const status = ref<EnergyCalculationStatus | null>(null);
  const error = ref('');
  let serial = 0;
  let refreshNeeded = false;
  let failures = 0;
  let timer: ReturnType<typeof setTimeout> | undefined;
  const pending = computed(() => status.value?.state === 'PENDING' || status.value?.state === 'RETRYING');
  const message = computed(() => error.value || status.value?.message || '');
  const canRetry = computed(() => !!error.value);

  function invalidate() {
    serial++;
    refreshNeeded = false;
    clearTimeout(timer);
    timer = undefined;
  }

  async function load(requestSerial: number, current: GpsTrack) {
    const trackId = current.id;
    if (requestSerial !== serial || trackId == null) return;
    let updatedTrack: GpsTrack | undefined;
    try {
      const result = await sharedStatus(trackId);
      if (requestSerial !== serial) return;
      const wasPending = pending.value;
      status.value = result;
      error.value = '';
      refreshNeeded ||= wasPending || current.energyNetTotalWh == null;
      const classificationFinished =
        result.state === 'UNAVAILABLE' && current.activityTypeSource == null && current.energyNetTotalWh == null;
      if ((result.state === 'CALCULATED' && refreshNeeded) || classificationFinished) {
        updatedTrack = await sharedRefresh(trackId);
        if (requestSerial !== serial || updatedTrack.id !== trackId) return;
        refreshNeeded = false;
      }
      failures = 0;
    } catch (cause) {
      if (requestSerial !== serial) return;
      failures++;
      logSanitizedError('Could not load energy calculation status:', cause, {
        topic1: 'application',
        topic2: 'useEnergyCalculationStatus',
        topic3: 'load',
      });
      error.value = 'Could not load energy calculation status.';
    }
    if (requestSerial !== serial) return;
    const retryDelay = error.value
      ? failures <= (options.maxErrorRetries ?? Infinity)
        ? ERROR_RETRY_DELAYS_MS[Math.min(failures - 1, ERROR_RETRY_DELAYS_MS.length - 1)]
        : undefined
      : pending.value
        ? STATUS_POLL_INTERVAL_MS
        : undefined;
    const entry: CachedEnergyStatus = {
      status: status.value,
      error: error.value,
      failures,
      refreshNeeded,
      updatedTrack,
      retryAt: retryDelay == null ? undefined : Date.now() + retryDelay,
    };
    cache.set(cacheKey(current), entry);
    if (updatedTrack) {
      cache.set(cacheKey(updatedTrack), entry);
      onUpdated(updatedTrack);
    }
    if (retryDelay != null) timer = setTimeout(() => void load(requestSerial, current), retryDelay);
  }

  function retry() {
    invalidate();
    error.value = '';
    failures = 0;
    if (track.value?.id != null) {
      cache.delete(cacheKey(track.value));
      void load(serial, track.value);
    }
  }

  watch(
    () =>
      [
        track.value?.id,
        track.value?.version,
        track.value?.activityType,
        track.value?.activityTypeSource,
        track.value?.timingUnreliable,
      ].join(':'),
    () => {
      invalidate();
      status.value = null;
      error.value = '';
      failures = 0;
      const current = track.value;
      if (current?.id == null || current.timingUnreliable) return;
      const cached = cache.get(cacheKey(current));
      if (cached) {
        status.value = cached.status;
        error.value = cached.error;
        failures = cached.failures;
        refreshNeeded = cached.refreshNeeded;
        const updated = cached.updatedTrack;
        if (
          updated &&
          updated !== current &&
          (updated.version == null || current.version == null || updated.version > current.version)
        ) {
          onUpdated(updated);
        }
        if (cached.retryAt == null) return;
        const requestSerial = serial;
        timer = setTimeout(() => void load(requestSerial, current), Math.max(0, cached.retryAt - Date.now()));
      } else void load(serial, current);
    },
    { immediate: true }
  );
  onBeforeUnmount(invalidate);
  return { status, pending, message, canRetry, retry };
}
