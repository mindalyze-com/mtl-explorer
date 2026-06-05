import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import type { DataFreshnessResponseDto } from '@/utils/serverAdminApi';
import { getDataFreshness } from '@/utils/serverAdminApi';
import {
  getAppliedDataFreshnessToken,
  getDismissedDataFreshness,
  setAppliedDataFreshnessToken,
  setDismissedDataFreshness,
} from '@/utils/dataFreshnessStorage';
import { DATA_FRESHNESS_DISMISS_MS, useDataFreshnessStore } from '@/stores/dataFreshnessStore';

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

  it('hydrates applied and dismissed tokens from localStorage', () => {
    setAppliedDataFreshnessToken('client-token');
    setDismissedDataFreshness('server-token', 30_000, Date.now());

    const store = useDataFreshnessStore();

    expect(store.appliedToken).toBe('client-token');
    expect(store.dismissedToken).toBe('server-token');
    expect(store.dismissedExpiresAt).toBeGreaterThan(Date.now());
  });

  it('marks a token as applied and clears any stale dismissal', () => {
    setDismissedDataFreshness('old-server-token', 30_000, Date.now());
    const store = useDataFreshnessStore();

    store.markAppliedToken('server-token');

    expect(store.appliedToken).toBe('server-token');
    expect(getAppliedDataFreshnessToken()).toBe('server-token');
    expect(store.dismissedToken).toBeNull();
    expect(getDismissedDataFreshness()).toBeNull();
  });

  it('keeps banner visibility tied to server, applied, dismissed, and reload state', () => {
    vi.useFakeTimers();
    vi.setSystemTime(1_000);
    const store = useDataFreshnessStore();
    store.currentFreshness = freshness('server-token');
    store.markAppliedToken('client-token');

    expect(store.shouldShowBanner(false)).toBe(false);
    expect(store.shouldShowBanner(true)).toBe(true);

    store.dismissToken('server-token', 1_000, 1_000);
    expect(store.shouldShowBanner(true)).toBe(false);

    store.setReloading(true);
    expect(store.shouldShowBanner(true)).toBe(false);
    store.setReloading(false);

    vi.advanceTimersByTime(1_000);
    expect(store.dismissedToken).toBeNull();
    expect(store.shouldShowBanner(true)).toBe(true);
  });

  it('snoozes freshness banners even when the server token advances before expiry', () => {
    vi.useFakeTimers();
    vi.setSystemTime(1_000);
    const store = useDataFreshnessStore();
    store.currentFreshness = freshness('server-token-1');
    store.markAppliedToken('client-token');

    store.dismissToken('server-token-1', 1_000, 1_000);
    store.currentFreshness = freshness('server-token-2');

    expect(store.shouldShowBanner(true)).toBe(false);

    vi.advanceTimersByTime(1_000);
    expect(store.shouldShowBanner(true)).toBe(true);
  });

  it('uses a five-minute freshness banner snooze by default', () => {
    expect(DATA_FRESHNESS_DISMISS_MS).toBe(5 * 60 * 1000);
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
});
