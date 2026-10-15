import { readStorage, removeStorage, STORAGE_KEYS, writeStorage } from '@/utils/appStorage';

const APPLIED_DATA_FRESHNESS_TOKEN_KEY = STORAGE_KEYS.dataFreshnessAppliedToken;

export function getAppliedDataFreshnessToken(): string | null {
  return readStorage(APPLIED_DATA_FRESHNESS_TOKEN_KEY);
}

export function setAppliedDataFreshnessToken(token: string): void {
  writeStorage(APPLIED_DATA_FRESHNESS_TOKEN_KEY, token);
}

export function clearAppliedDataFreshnessToken(): void {
  removeStorage(APPLIED_DATA_FRESHNESS_TOKEN_KEY);
}
