const STORAGE_KEY_PREFIX = 'mtl.';

export const STORAGE_KEYS = {
  jwt: 'mtl.jwt',
  colorScheme: 'mtl.color-scheme',
  locale: 'mtl.locale',
  mapSettings: 'mtl.map.settings',
  trackDetailsPreferences: 'mtl.track-details.preferences',
  mapConfigCache: 'mtl.map.config-cache',
  backgroundsDisplayed: 'mtl.backgrounds.displayed',
  backgroundCacheVersion: 'mtl.backgrounds.cache-version',
  startupCrashGuard: 'mtl.startup.crash-guard',
  dataFreshnessAppliedToken: 'mtl.data-freshness.applied-token',
  dataFreshnessDismissedToken: 'mtl.data-freshness.dismissed-token',
  dataFreshnessDismissedExpiresAt: 'mtl.data-freshness.dismissed-expires-at',
  clientFilterConfig: 'mtl.filter.client-config',
} as const satisfies Record<string, `mtl.${string}`>;

export type AppStorageKey = (typeof STORAGE_KEYS)[keyof typeof STORAGE_KEYS];

const REGISTERED_STORAGE_KEYS = Object.values(STORAGE_KEYS) as AppStorageKey[];
const REGISTERED_STORAGE_KEY_SET = new Set<string>(REGISTERED_STORAGE_KEYS);

validateRegisteredStorageKeys();

export function getRegisteredStorageKeys(): readonly AppStorageKey[] {
  return [...REGISTERED_STORAGE_KEYS];
}

export function readStorage(key: AppStorageKey): string | null {
  assertRegisteredStorageKey(key);
  if (typeof localStorage === 'undefined') return null;
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

export function writeStorage(key: AppStorageKey, value: string): void {
  assertRegisteredStorageKey(key);
  if (typeof localStorage === 'undefined') return;
  try {
    localStorage.setItem(key, value);
  } catch {
    /* ignore quota / privacy-mode failures */
  }
}

export function removeStorage(key: AppStorageKey): void {
  assertRegisteredStorageKey(key);
  if (typeof localStorage === 'undefined') return;
  try {
    localStorage.removeItem(key);
  } catch {
    /* ignore */
  }
}

export function clearStorage(): void {
  if (typeof localStorage === 'undefined') return;
  try {
    localStorage.clear();
  } catch {
    /* ignore */
  }
}

export function readJsonStorage<T>(key: AppStorageKey, fallback: T, validate?: (value: unknown) => T): T {
  const raw = readStorage(key);
  if (raw == null) return fallback;
  try {
    const parsed = JSON.parse(raw) as unknown;
    return validate ? validate(parsed) : (parsed as T);
  } catch {
    return fallback;
  }
}

export function writeJsonStorage<T>(key: AppStorageKey, value: T): void {
  writeStorage(key, JSON.stringify(value));
}

function validateRegisteredStorageKeys(): void {
  for (const key of REGISTERED_STORAGE_KEYS) {
    if (!key.startsWith(STORAGE_KEY_PREFIX)) {
      throw new Error(`Storage key "${key}" must start with "${STORAGE_KEY_PREFIX}".`);
    }
  }
}

function assertRegisteredStorageKey(key: AppStorageKey): void {
  if (!REGISTERED_STORAGE_KEY_SET.has(key) || !key.startsWith(STORAGE_KEY_PREFIX)) {
    throw new Error(`Storage key "${key}" is not registered.`);
  }
}
