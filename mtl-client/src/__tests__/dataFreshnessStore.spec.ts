import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import type { DataFreshnessResponseDto } from '@/utils/serverAdminApi';
import { getDataFreshness } from '@/utils/serverAdminApi';
import {
  getDataFreshnessSnoozedUntil,
  getAppliedDataFreshnessToken,
  setDataFreshnessSnooze,
  setAppliedDataFreshnessToken,
} from '@/utils/dataFreshnessStorage';
import { DATA_FRESHNESS_SNOOZE_MS, useDataFreshnessStore } from '@/stores/dataFreshnessStore';

vi.mock('@/utils/serverAdminApi', () => ({
  getDataFreshness: vi.fn(),
}));

const getDataFreshnessMock = vi.mocked(getDataFreshness);

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
    getDataFreshnessMock.mockReset();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('hydrates the applied token and snooze timestamp from localStorage', () => {
    setAppliedDataFreshnessToken('client-token');
    const snoozedUntil = setDataFreshnessSnooze(30_000, Date.now());

    const store = useDataFreshnessStore();

    expect(store.appliedToken).toBe('client-token');
    expect(store.snoozedUntil).toBe(snoozedUntil);
  });

  it('marks a token as applied without cancelling an active snooze', () => {
    const snoozedUntil = setDataFreshnessSnooze(30_000, Date.now());
    const store = useDataFreshnessStore();

    store.markAppliedToken('server-token');

    expect(store.appliedToken).toBe('server-token');
    expect(getAppliedDataFreshnessToken()).toBe('server-token');
    expect(store.snoozedUntil).toBe(snoozedUntil);
    expect(getDataFreshnessSnoozedUntil()).toBe(snoozedUntil);
  });

  it('shows the banner only when data is stale and the snooze has expired', () => {
    vi.useFakeTimers();
    vi.setSystemTime(1_000);
    const store = useDataFreshnessStore();
    store.currentFreshness = freshness('server-token');
    store.markAppliedToken('client-token');

    expect(store.shouldShowBanner(false)).toBe(false);
    expect(store.shouldShowBanner(true)).toBe(true);

    store.snooze(1_000, 1_000);
    expect(store.shouldShowBanner(true)).toBe(false);

    store.setReloading(true);
    expect(store.shouldShowBanner(true)).toBe(false);
    store.setReloading(false);

    vi.advanceTimersByTime(1_000);
    expect(store.snoozedUntil).toBe(0);
    expect(store.shouldShowBanner(true)).toBe(true);
  });

  it('keeps one fixed snooze deadline while the server token changes repeatedly', () => {
    vi.useFakeTimers();
    vi.setSystemTime(1_000);
    const store = useDataFreshnessStore();
    store.currentFreshness = freshness('server-token-1');
    store.markAppliedToken('client-token');

    store.snooze(1_000, 1_000);
    const snoozedUntil = store.snoozedUntil;
    store.currentFreshness = freshness('server-token-2');
    store.currentFreshness = freshness('server-token-3');
    store.currentFreshness = freshness('server-token-4');

    expect(store.shouldShowBanner(true)).toBe(false);
    expect(store.snoozedUntil).toBe(snoozedUntil);

    vi.advanceTimersByTime(1_000);
    expect(store.shouldShowBanner(true)).toBe(true);
  });

  it('uses a five-minute freshness banner snooze by default', () => {
    expect(DATA_FRESHNESS_SNOOZE_MS).toBe(5 * 60 * 1000);
  });

  it('clears the applied token through the store', () => {
    const store = useDataFreshnessStore();
    store.markAppliedToken('server-token');

    store.clearAppliedToken();

    expect(store.appliedToken).toBe('');
    expect(getAppliedDataFreshnessToken()).toBeNull();
  });

  it('refreshes server freshness through the store', async () => {
    getDataFreshnessMock.mockResolvedValueOnce(freshness('server-token'));
    const store = useDataFreshnessStore();

    const result = await store.refresh();

    expect(result?.freshnessToken).toBe('server-token');
    expect(store.serverFreshnessToken).toBe('server-token');
    expect(store.lastChecked).not.toBe('');
    expect(store.isFreshnessPollingHealthy).toBe(true);
  });

  it('coalesces concurrent freshness refreshes into one server request', async () => {
    let resolveFreshness!: (value: DataFreshnessResponseDto) => void;
    getDataFreshnessMock.mockReturnValueOnce(
      new Promise<DataFreshnessResponseDto>((resolve) => {
        resolveFreshness = resolve;
      })
    );
    const store = useDataFreshnessStore();

    const firstRefresh = store.refresh();
    const secondRefresh = store.refresh();
    resolveFreshness(freshness('server-token'));

    await expect(firstRefresh).resolves.toMatchObject({ freshnessToken: 'server-token' });
    await expect(secondRefresh).resolves.toMatchObject({ freshnessToken: 'server-token' });
    expect(getDataFreshnessMock).toHaveBeenCalledTimes(1);
  });
});
