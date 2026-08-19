import { beforeEach, describe, expect, it } from 'vitest';
import {
  clearDataFreshnessSnooze,
  getDataFreshnessSnoozedUntil,
  setDataFreshnessSnooze,
} from '@/utils/dataFreshnessStorage';

describe('dataFreshnessStorage', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('keeps one snooze timestamp until its expiry', () => {
    const snoozedUntil = setDataFreshnessSnooze(30_000, 1_000);

    expect(snoozedUntil).toBe(31_000);
    expect(getDataFreshnessSnoozedUntil(30_999)).toBe(snoozedUntil);
    expect(getDataFreshnessSnoozedUntil(31_000)).toBe(0);
  });

  it('clears the snooze timestamp on invalid input', () => {
    setDataFreshnessSnooze(30_000, 1_000);

    expect(setDataFreshnessSnooze(0, 2_000)).toBe(0);
    expect(getDataFreshnessSnoozedUntil(2_000)).toBe(0);
  });

  it('clears the snooze timestamp explicitly', () => {
    setDataFreshnessSnooze(30_000, 1_000);

    clearDataFreshnessSnooze();

    expect(getDataFreshnessSnoozedUntil(2_000)).toBe(0);
  });
});
