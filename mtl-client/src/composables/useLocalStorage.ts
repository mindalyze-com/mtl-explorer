import { ref, watch, type Ref } from 'vue';
import { migrateLegacyKeys, type UserPrefKey } from '@/utils/userPrefs';

/**
 * A reactive ref that's synced two-way with localStorage.
 *
 * - Reads the initial value (running legacy-key migration on first call).
 * - Writes back on every mutation.
 * - Listens to `storage` events so other tabs / windows stay in sync.
 *
 * The serializer defaults to JSON — pass an explicit one for primitives where
 * `JSON.stringify('foo')` adding quotes would corrupt legacy data.
 */
export interface LocalStorageSerializer<T> {
  read: (raw: string) => T;
  write: (value: T) => string;
}

export const STRING_SERIALIZER: LocalStorageSerializer<string> = {
  read: (raw) => raw,
  write: (value) => value,
};

export const NUMBER_SERIALIZER: LocalStorageSerializer<number> = {
  read: (raw) => {
    const parsed = Number.parseFloat(raw);
    return Number.isFinite(parsed) ? parsed : 0;
  },
  write: (value) => String(value),
};

export const BOOLEAN_SERIALIZER: LocalStorageSerializer<boolean> = {
  read: (raw) => raw === 'true',
  write: (value) => String(value),
};

export function jsonSerializer<T>(): LocalStorageSerializer<T> {
  return {
    read: (raw) => JSON.parse(raw) as T,
    write: (value) => JSON.stringify(value),
  };
}

export interface UseLocalStorageOptions<T> {
  serializer?: LocalStorageSerializer<T>;
  /** Listen for cross-tab changes via the `storage` event. Default: true. */
  syncAcrossTabs?: boolean;
  /** Coerce / sanitize a parsed value before assigning to the ref. */
  validate?: (value: T) => T;
}

/**
 * Reactive {@link Ref} backed by `localStorage[key]`.
 *
 * @param key Must be one from `USER_PREFS_KEYS` (compile-time enforced).
 * @param defaultValue Used when the key is absent or fails to deserialize.
 */
export function useLocalStorage<T>(
  key: UserPrefKey,
  defaultValue: T,
  options: UseLocalStorageOptions<T> = {}
): Ref<T> {
  migrateLegacyKeys();
  const serializer = options.serializer ?? (jsonSerializer<T>() as LocalStorageSerializer<T>);
  const validate = options.validate ?? ((v: T) => v);

  function read(): T {
    if (typeof localStorage === 'undefined') return defaultValue;
    try {
      const raw = localStorage.getItem(key);
      if (raw === null) return defaultValue;
      return validate(serializer.read(raw));
    } catch {
      return defaultValue;
    }
  }

  const stored = ref(read()) as Ref<T>;

  watch(
    stored,
    (next) => {
      try {
        localStorage.setItem(key, serializer.write(next));
      } catch {
        /* quota / privacy-mode */
      }
    },
    { deep: true }
  );

  if (options.syncAcrossTabs !== false && typeof window !== 'undefined') {
    window.addEventListener('storage', (event) => {
      if (event.key !== key) return;
      stored.value = event.newValue === null ? defaultValue : (() => {
        try {
          return validate(serializer.read(event.newValue!));
        } catch {
          return defaultValue;
        }
      })();
    });
  }

  return stored;
}
