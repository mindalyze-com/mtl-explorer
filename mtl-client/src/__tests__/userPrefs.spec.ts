import { describe, it, expect, beforeEach } from 'vitest';
import { nextTick } from 'vue';
import {
  USER_PREFS_KEYS,
  readPref,
  writePref,
  migrateLegacyKeys,
  _resetMigrationGuardForTests,
} from '@/utils/userPrefs';
import {
  useLocalStorage,
  STRING_SERIALIZER,
  NUMBER_SERIALIZER,
  BOOLEAN_SERIALIZER,
} from '@/composables/useLocalStorage';

describe('userPrefs + useLocalStorage', () => {
  beforeEach(() => {
    localStorage.clear();
    _resetMigrationGuardForTests();
  });

  it('readPref returns null when the key is absent', () => {
    expect(readPref(USER_PREFS_KEYS.colorScheme)).toBeNull();
  });

  it('writePref / readPref round-trip', () => {
    writePref(USER_PREFS_KEYS.colorScheme, 'dark');
    expect(readPref(USER_PREFS_KEYS.colorScheme)).toBe('dark');
  });

  it('migrateLegacyKeys copies old keys to new namespaced ones (idempotent)', () => {
    // Seed a legacy entry; the registry maps 'mtl-color-scheme' → mtl.color-scheme.
    localStorage.setItem('mtl-color-scheme', 'dark');
    migrateLegacyKeys();
    expect(localStorage.getItem(USER_PREFS_KEYS.colorScheme)).toBe('dark');
    // Calling again shouldn't throw or duplicate work.
    migrateLegacyKeys();
    expect(localStorage.getItem(USER_PREFS_KEYS.colorScheme)).toBe('dark');
  });

  it('useLocalStorage returns default when key absent', () => {
    const r = useLocalStorage(USER_PREFS_KEYS.trackGraphHeight, 240, {
      serializer: NUMBER_SERIALIZER,
    });
    expect(r.value).toBe(240);
  });

  it('useLocalStorage persists writes to localStorage', async () => {
    const r = useLocalStorage(USER_PREFS_KEYS.trackGraphHeight, 240, {
      serializer: NUMBER_SERIALIZER,
    });
    r.value = 320;
    await nextTick();
    expect(localStorage.getItem(USER_PREFS_KEYS.trackGraphHeight)).toBe('320');
  });

  it('useLocalStorage with BOOLEAN_SERIALIZER round-trips', async () => {
    localStorage.setItem(USER_PREFS_KEYS.mapLegendCollapsed, 'true');
    const r = useLocalStorage(USER_PREFS_KEYS.mapLegendCollapsed, false, {
      serializer: BOOLEAN_SERIALIZER,
    });
    expect(r.value).toBe(true);
    r.value = false;
    await nextTick();
    expect(localStorage.getItem(USER_PREFS_KEYS.mapLegendCollapsed)).toBe('false');
  });

  it('useLocalStorage with STRING_SERIALIZER does NOT add JSON quotes', async () => {
    const r = useLocalStorage(USER_PREFS_KEYS.colorScheme, 'light', {
      serializer: STRING_SERIALIZER,
    });
    r.value = 'dark';
    await nextTick();
    // Critical: must be the bare string 'dark', not JSON-encoded '"dark"'.
    expect(localStorage.getItem(USER_PREFS_KEYS.colorScheme)).toBe('dark');
  });

  it('validate hook is applied on read', () => {
    localStorage.setItem(USER_PREFS_KEYS.trackGraphHeight, '99999');
    const r = useLocalStorage(USER_PREFS_KEYS.trackGraphHeight, 240, {
      serializer: NUMBER_SERIALIZER,
      validate: (v) => Math.min(640, Math.max(100, v)),
    });
    expect(r.value).toBe(640);
  });
});
