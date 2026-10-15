import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import type { DataFreshnessResponseDto } from 'x8ing-mtl-api-typescript-fetch';
import { getAppliedDataFreshnessToken, setAppliedDataFreshnessToken } from '@/utils/dataFreshnessStorage';
import { useDataFreshnessStore } from '@/stores/dataFreshnessStore';
import { APPLICATION_STATUS_SECTION, useApplicationStatusStore } from '@/stores/applicationStatusStore';
import { STATUS_POLL_INTERVAL_MS } from '@/utils/statusPolling';

const getApplicationStatusMock = vi.hoisted(() => vi.fn());
vi.mock('x8ing-mtl-api-typescript-fetch', async () => {
  const actual = await vi.importActual<typeof import('x8ing-mtl-api-typescript-fetch')>(
    'x8ing-mtl-api-typescript-fetch'
  );
  return {
    ...actual,
    ApplicationStatusControllerApi: class {
      getApplicationStatus = getApplicationStatusMock;
    },
  };
});

function freshness(token: string): DataFreshnessResponseDto {
  return {
    freshnessToken: token,
    changedAt: new Date('2026-05-27T10:00:00Z'),
    items: [],
  } as DataFreshnessResponseDto;
}

describe('useDataFreshnessStore', () => {
  beforeEach(() => {
    localStorage.clear();
    setActivePinia(createPinia());
    getApplicationStatusMock.mockReset();
  });

  afterEach(() => {
    useApplicationStatusStore().$dispose();
    vi.useRealTimers();
  });

  it('hydrates the applied token from localStorage', () => {
    setAppliedDataFreshnessToken('client-token');

    const store = useDataFreshnessStore();

    expect(store.appliedToken).toBe('client-token');
  });

  it('marks a token as applied', () => {
    const store = useDataFreshnessStore();

    store.markAppliedToken('server-token');

    expect(store.appliedToken).toBe('server-token');
    expect(getAppliedDataFreshnessToken()).toBe('server-token');
  });

  it('keeps a warm tab stale when another tab applies the new shared-cache revision', () => {
    setAppliedDataFreshnessToken('initial-data');
    const firstTab = createPinia();
    const secondTab = createPinia();
    const firstStore = useDataFreshnessStore(firstTab);
    const secondStore = useDataFreshnessStore(secondTab);
    useApplicationStatusStore(firstTab).freshness = freshness('updated-data');
    useApplicationStatusStore(secondTab).freshness = freshness('updated-data');

    firstStore.markAppliedToken('updated-data');
    secondStore.hydrateFromStorage();

    expect(firstStore.isOutOfSync).toBe(false);
    expect(getAppliedDataFreshnessToken()).toBe('updated-data');
    expect(secondStore.appliedToken).toBe('initial-data');
    expect(secondStore.isOutOfSync).toBe(true);
    expect(secondStore.shouldShowBanner(true)).toBe(true);
    secondStore.markAppliedToken('updated-data');
    expect(secondStore.isOutOfSync).toBe(false);
    useApplicationStatusStore(firstTab).$dispose();
    useApplicationStatusStore(secondTab).$dispose();
  });

  it('shows the banner only after loading and while stale data is not reloading', () => {
    vi.useFakeTimers();
    vi.setSystemTime(1_000);
    const store = useDataFreshnessStore();
    useApplicationStatusStore().freshness = freshness('server-token');
    store.markAppliedToken('client-token');

    expect(store.shouldShowBanner(false)).toBe(false);
    expect(store.shouldShowBanner(true)).toBe(true);

    store.setReloading(true);
    expect(store.shouldShowBanner(true)).toBe(false);
    store.setReloading(false);

    vi.advanceTimersByTime(1_000);
    expect(store.shouldShowBanner(true)).toBe(true);
  });

  it('clears the applied token through the store', () => {
    const store = useDataFreshnessStore();
    store.markAppliedToken('server-token');

    store.clearAppliedToken();

    expect(store.appliedToken).toBe('');
    expect(getAppliedDataFreshnessToken()).toBeNull();
  });

  it('refreshes server freshness through the store', async () => {
    getApplicationStatusMock.mockResolvedValueOnce({
      generatedAt: new Date(),
      included: new Set([APPLICATION_STATUS_SECTION.FRESHNESS]),
      freshness: freshness('server-token'),
    });
    const store = useDataFreshnessStore();

    const result = await store.refresh();

    expect(result?.freshnessToken).toBe('server-token');
    expect(store.serverFreshnessToken).toBe('server-token');
    expect(store.lastChecked).not.toBe('');
    expect(store.isFreshnessPollingHealthy).toBe(true);
  });

  it('coalesces concurrent freshness refreshes into one server request', async () => {
    let resolveFreshness!: (value: unknown) => void;
    getApplicationStatusMock.mockReturnValueOnce(
      new Promise((resolve) => {
        resolveFreshness = resolve;
      })
    );
    const store = useDataFreshnessStore();

    const firstRefresh = store.refresh();
    const secondRefresh = store.refresh();
    resolveFreshness({
      generatedAt: new Date(),
      included: new Set([APPLICATION_STATUS_SECTION.FRESHNESS]),
      freshness: freshness('server-token'),
    });

    await expect(firstRefresh).resolves.toMatchObject({ freshnessToken: 'server-token' });
    await expect(secondRefresh).resolves.toMatchObject({ freshnessToken: 'server-token' });
    expect(getApplicationStatusMock).toHaveBeenCalledTimes(1);
  });

  it('refreshes immediately between polls, shares in-flight work, and preserves the normal cadence', async () => {
    vi.useFakeTimers();
    vi.setSystemTime(1_000);
    const serverResponse = {
      included: new Set([APPLICATION_STATUS_SECTION.FRESHNESS]),
      freshness: freshness('server-token'),
    };
    getApplicationStatusMock.mockResolvedValue(serverResponse);
    const store = useDataFreshnessStore();
    const applicationStatusStore = useApplicationStatusStore();
    applicationStatusStore.registerPollingConsumer({
      [APPLICATION_STATUS_SECTION.FRESHNESS]: STATUS_POLL_INTERVAL_MS,
    });
    await vi.advanceTimersByTimeAsync(0);
    await vi.advanceTimersByTimeAsync(1_000);

    let resolveFreshness!: (value: typeof serverResponse) => void;
    getApplicationStatusMock.mockReturnValueOnce(
      new Promise((resolve) => {
        resolveFreshness = resolve;
      })
    );
    const startupRefresh = store.refresh({ immediate: true });
    expect(applicationStatusStore.nextPollAt).toBe(Date.now());
    await vi.advanceTimersByTimeAsync(0);
    const sharedRefresh = store.refresh({ immediate: true });
    expect(getApplicationStatusMock).toHaveBeenCalledTimes(2);
    resolveFreshness(serverResponse);
    await Promise.all([startupRefresh, sharedRefresh]);

    const regularRefresh = store.refresh();
    await vi.advanceTimersByTimeAsync(STATUS_POLL_INTERVAL_MS - 1);
    expect(getApplicationStatusMock).toHaveBeenCalledTimes(2);
    await vi.advanceTimersByTimeAsync(1);
    await regularRefresh;
    expect(getApplicationStatusMock).toHaveBeenCalledTimes(3);
  });

  it('keeps failure backoff when an immediate freshness check is requested', async () => {
    vi.useFakeTimers();
    vi.setSystemTime(1_000);
    getApplicationStatusMock.mockRejectedValueOnce(new Error('Network unavailable')).mockResolvedValueOnce({
      included: new Set([APPLICATION_STATUS_SECTION.FRESHNESS]),
      freshness: freshness('server-token'),
    });
    const store = useDataFreshnessStore();
    const applicationStatusStore = useApplicationStatusStore();
    applicationStatusStore.registerPollingConsumer({
      [APPLICATION_STATUS_SECTION.FRESHNESS]: STATUS_POLL_INTERVAL_MS,
    });
    await vi.advanceTimersByTimeAsync(0);
    const retryAt = applicationStatusStore.backoffUntil!;
    const startupRefresh = store.refresh({ immediate: true });

    expect(applicationStatusStore.nextPollAt).toBe(retryAt);
    await vi.advanceTimersByTimeAsync(retryAt - Date.now() - 1);
    expect(getApplicationStatusMock).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(1);
    await expect(startupRefresh).resolves.toMatchObject({ freshnessToken: 'server-token' });
    expect(getApplicationStatusMock).toHaveBeenCalledTimes(2);
    expect(store.isFreshnessPollingHealthy).toBe(true);
  });
});
