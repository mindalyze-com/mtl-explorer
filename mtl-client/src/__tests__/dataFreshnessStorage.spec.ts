import { beforeEach, describe, expect, it } from 'vitest';
import {
  clearDismissedDataFreshness,
  getDismissedDataFreshness,
  setDismissedDataFreshness,
} from '@/utils/dataFreshnessStorage';

describe('dataFreshnessStorage', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('keeps a dismissed freshness token until its expiry', () => {
    const dismissed = setDismissedDataFreshness('server-token-1', 30_000, 1_000);

    expect(dismissed).toEqual({ token: 'server-token-1', expiresAt: 31_000 });
    expect(getDismissedDataFreshness(30_999)).toEqual(dismissed);
    expect(getDismissedDataFreshness(31_000)).toBeNull();
  });

  it('clears dismissed freshness state on invalid input', () => {
    setDismissedDataFreshness('server-token-1', 30_000, 1_000);

    expect(setDismissedDataFreshness('', 30_000, 2_000)).toBeNull();
    expect(getDismissedDataFreshness(2_000)).toBeNull();
  });

  it('clears dismissed freshness state explicitly', () => {
    setDismissedDataFreshness('server-token-1', 30_000, 1_000);

    clearDismissedDataFreshness();

    expect(getDismissedDataFreshness(2_000)).toBeNull();
  });
});
