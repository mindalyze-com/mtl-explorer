import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import { beforeEach, describe, expect, it } from 'vitest';
import { STORAGE_KEYS } from '@/utils/appStorage';

const themeScript = readFileSync('../documentation/tools/website/guide-theme.js', 'utf8');

function openGuide(storage: Pick<Storage, 'getItem'> = localStorage): EventTarget {
  const guideWindow = new EventTarget();
  runInNewContext(themeScript, { document, localStorage: storage, window: guideWindow });
  return guideWindow;
}

describe('installed guide theme', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.removeAttribute('data-theme');
  });

  it.each(['dark', 'light'])('opens immediately in the saved %s app theme', (theme) => {
    localStorage.setItem(STORAGE_KEYS.colorScheme, theme);
    localStorage.setItem(STORAGE_KEYS.mediaViewerTheme, theme === 'dark' ? 'light' : 'dark');
    openGuide();
    expect(document.documentElement.dataset.theme).toBe(theme);
    expect(localStorage.getItem(STORAGE_KEYS.colorScheme)).toBe(theme);
  });

  it.each([null, 'sepia'])('matches the app default for preference %s', (theme) => {
    if (theme !== null) localStorage.setItem(STORAGE_KEYS.colorScheme, theme);
    openGuide();
    expect(document.documentElement.dataset.theme).toBe('light');
    expect(localStorage.getItem(STORAGE_KEYS.colorScheme)).toBe(theme);
  });

  it('opens when browser storage is blocked', () => {
    expect(() =>
      openGuide({
        getItem() {
          throw new Error('Storage blocked');
        },
      })
    ).not.toThrow();
    expect(document.documentElement.dataset.theme).toBe('light');
  });

  it('follows app theme changes and storage clearing in another tab', () => {
    const guide = openGuide();
    localStorage.setItem(STORAGE_KEYS.colorScheme, 'dark');
    guide.dispatchEvent(new StorageEvent('storage', { key: STORAGE_KEYS.colorScheme }));
    expect(document.documentElement.dataset.theme).toBe('dark');

    localStorage.clear();
    guide.dispatchEvent(new StorageEvent('storage', { key: null }));
    expect(document.documentElement.dataset.theme).toBe('light');
  });

  it.each(['focus', 'pageshow'])('refreshes the preference on %s', (event) => {
    const guide = openGuide();
    localStorage.setItem(STORAGE_KEYS.colorScheme, 'dark');
    guide.dispatchEvent(new Event(event));
    expect(document.documentElement.dataset.theme).toBe('dark');
  });

  it('ignores separate photo viewer preference changes', () => {
    const guide = openGuide();
    localStorage.setItem(STORAGE_KEYS.mediaViewerTheme, 'dark');
    guide.dispatchEvent(new StorageEvent('storage', { key: STORAGE_KEYS.mediaViewerTheme }));
    expect(document.documentElement.dataset.theme).toBe('light');
  });
});
