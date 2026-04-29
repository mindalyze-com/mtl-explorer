const APPLIED_DATA_FRESHNESS_TOKEN_KEY = 'mtl-applied-data-freshness-token';

export function getAppliedDataFreshnessToken(): string | null {
  return localStorage.getItem(APPLIED_DATA_FRESHNESS_TOKEN_KEY);
}

export function setAppliedDataFreshnessToken(token: string): void {
  localStorage.setItem(APPLIED_DATA_FRESHNESS_TOKEN_KEY, token);
}

export function clearAppliedDataFreshnessToken(): void {
  localStorage.removeItem(APPLIED_DATA_FRESHNESS_TOKEN_KEY);
}
