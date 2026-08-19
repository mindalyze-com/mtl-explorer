import { readStorage, removeStorage, STORAGE_KEYS, writeStorage } from '@/utils/appStorage';

const APPLIED_DATA_FRESHNESS_TOKEN_KEY = STORAGE_KEYS.dataFreshnessAppliedToken;
const DATA_FRESHNESS_SNOOZED_UNTIL_KEY = STORAGE_KEYS.dataFreshnessSnoozedUntil;

export function getAppliedDataFreshnessToken(): string | null {
  return readStorage(APPLIED_DATA_FRESHNESS_TOKEN_KEY);
}

export function setAppliedDataFreshnessToken(token: string): void {
  writeStorage(APPLIED_DATA_FRESHNESS_TOKEN_KEY, token);
}

export function clearAppliedDataFreshnessToken(): void {
  removeStorage(APPLIED_DATA_FRESHNESS_TOKEN_KEY);
}

export function getDataFreshnessSnoozedUntil(nowMs = Date.now()): number {
  const snoozedUntil = Number(readStorage(DATA_FRESHNESS_SNOOZED_UNTIL_KEY));
  if (!Number.isFinite(snoozedUntil) || snoozedUntil <= nowMs) {
    clearDataFreshnessSnooze();
    return 0;
  }
  return snoozedUntil;
}

export function setDataFreshnessSnooze(durationMs: number, nowMs = Date.now()): number {
  if (!Number.isFinite(durationMs) || durationMs <= 0 || !Number.isFinite(nowMs)) {
    clearDataFreshnessSnooze();
    return 0;
  }

  const snoozedUntil = nowMs + durationMs;
  writeStorage(DATA_FRESHNESS_SNOOZED_UNTIL_KEY, String(snoozedUntil));
  return snoozedUntil;
}

export function clearDataFreshnessSnooze(): void {
  removeStorage(DATA_FRESHNESS_SNOOZED_UNTIL_KEY);
}
