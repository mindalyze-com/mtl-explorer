const APPLIED_DATA_FRESHNESS_TOKEN_KEY = 'mtl-applied-data-freshness-token';
const DISMISSED_DATA_FRESHNESS_TOKEN_KEY = 'mtl-dismissed-data-freshness-token';
const DISMISSED_DATA_FRESHNESS_EXPIRES_AT_KEY = 'mtl-dismissed-data-freshness-expires-at';

export type DismissedDataFreshness = {
  token: string;
  expiresAt: number;
};

export function getAppliedDataFreshnessToken(): string | null {
  return localStorage.getItem(APPLIED_DATA_FRESHNESS_TOKEN_KEY);
}

export function setAppliedDataFreshnessToken(token: string): void {
  localStorage.setItem(APPLIED_DATA_FRESHNESS_TOKEN_KEY, token);
}

export function clearAppliedDataFreshnessToken(): void {
  localStorage.removeItem(APPLIED_DATA_FRESHNESS_TOKEN_KEY);
}

export function getDismissedDataFreshness(nowMs = Date.now()): DismissedDataFreshness | null {
  const token = localStorage.getItem(DISMISSED_DATA_FRESHNESS_TOKEN_KEY);
  const expiresAt = Number(localStorage.getItem(DISMISSED_DATA_FRESHNESS_EXPIRES_AT_KEY));

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
  localStorage.setItem(DISMISSED_DATA_FRESHNESS_TOKEN_KEY, dismissed.token);
  localStorage.setItem(DISMISSED_DATA_FRESHNESS_EXPIRES_AT_KEY, String(dismissed.expiresAt));
  return dismissed;
}

export function clearDismissedDataFreshness(): void {
  localStorage.removeItem(DISMISSED_DATA_FRESHNESS_TOKEN_KEY);
  localStorage.removeItem(DISMISSED_DATA_FRESHNESS_EXPIRES_AT_KEY);
}
