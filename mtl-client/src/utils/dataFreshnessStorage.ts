import { readStorage, removeStorage, STORAGE_KEYS, writeStorage } from '@/utils/appStorage';

const APPLIED_DATA_FRESHNESS_TOKEN_KEY = STORAGE_KEYS.dataFreshnessAppliedToken;
const DISMISSED_DATA_FRESHNESS_TOKEN_KEY = STORAGE_KEYS.dataFreshnessDismissedToken;
const DISMISSED_DATA_FRESHNESS_EXPIRES_AT_KEY = STORAGE_KEYS.dataFreshnessDismissedExpiresAt;

export type DismissedDataFreshness = {
  token: string;
  expiresAt: number;
};

export function getAppliedDataFreshnessToken(): string | null {
  return readStorage(APPLIED_DATA_FRESHNESS_TOKEN_KEY);
}

export function setAppliedDataFreshnessToken(token: string): void {
  writeStorage(APPLIED_DATA_FRESHNESS_TOKEN_KEY, token);
}

export function clearAppliedDataFreshnessToken(): void {
  removeStorage(APPLIED_DATA_FRESHNESS_TOKEN_KEY);
}

export function getDismissedDataFreshness(nowMs = Date.now()): DismissedDataFreshness | null {
  const token = readStorage(DISMISSED_DATA_FRESHNESS_TOKEN_KEY);
  const expiresAt = Number(readStorage(DISMISSED_DATA_FRESHNESS_EXPIRES_AT_KEY));

  if (!token || !Number.isFinite(expiresAt) || expiresAt <= nowMs) {
    clearDismissedDataFreshness();
    return null;
  }

  return { token, expiresAt };
}

export function setDismissedDataFreshness(
  token: string,
  durationMs: number,
  nowMs = Date.now()
): DismissedDataFreshness | null {
  if (!token || !Number.isFinite(durationMs) || durationMs <= 0 || !Number.isFinite(nowMs)) {
    clearDismissedDataFreshness();
    return null;
  }

  const dismissed = { token, expiresAt: nowMs + durationMs };
  writeStorage(DISMISSED_DATA_FRESHNESS_TOKEN_KEY, dismissed.token);
  writeStorage(DISMISSED_DATA_FRESHNESS_EXPIRES_AT_KEY, String(dismissed.expiresAt));
  return dismissed;
}

export function clearDismissedDataFreshness(): void {
  removeStorage(DISMISSED_DATA_FRESHNESS_TOKEN_KEY);
  removeStorage(DISMISSED_DATA_FRESHNESS_EXPIRES_AT_KEY);
}
