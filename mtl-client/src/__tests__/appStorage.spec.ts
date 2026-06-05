import { beforeEach, describe, expect, it } from 'vitest';
import {
  getRegisteredStorageKeys,
  readJsonStorage,
  readStorage,
  removeStorage,
  STORAGE_KEYS,
  writeJsonStorage,
  writeStorage,
  type AppStorageKey,
} from '@/utils/appStorage';

describe('appStorage gateway', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('only exposes registered mtl-prefixed keys', () => {
    expect(getRegisteredStorageKeys().length).toBeGreaterThan(0);
    expect(getRegisteredStorageKeys().every((key) => key.startsWith('mtl.'))).toBe(true);
  });

  it('rejects unregistered keys at the gateway boundary', () => {
    expect(() => readStorage('not-mtl.key' as AppStorageKey)).toThrow(/not registered/);
    expect(() => writeStorage('mtl.unregistered' as AppStorageKey, 'value')).toThrow(/not registered/);
  });

  it('round-trips string values and removes them through registered keys', () => {
    writeStorage(STORAGE_KEYS.colorScheme, 'dark');

    expect(readStorage(STORAGE_KEYS.colorScheme)).toBe('dark');

    removeStorage(STORAGE_KEYS.colorScheme);

    expect(readStorage(STORAGE_KEYS.colorScheme)).toBeNull();
  });

  it('round-trips JSON values', () => {
    writeJsonStorage(STORAGE_KEYS.mapSettings, {
      theme: 'dark',
      activeOverlays: ['wanderland'],
    });

    expect(readJsonStorage(STORAGE_KEYS.mapSettings, { theme: 'light' })).toEqual({
      theme: 'dark',
      activeOverlays: ['wanderland'],
    });
  });

  it('returns the fallback for corrupt JSON', () => {
    localStorage.setItem(STORAGE_KEYS.mapSettings, '{broken');

    expect(readJsonStorage(STORAGE_KEYS.mapSettings, { theme: 'light' })).toEqual({ theme: 'light' });
  });
});
