import { readFileSync } from 'node:fs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { STORAGE_KEYS } from '@/utils/appStorage';

const mediaPreviewSource = readFileSync('src/components/map/MediaPreview.vue', 'utf8');

describe('photo viewer theme preference', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.setAttribute('data-theme', 'dark');
    vi.resetModules();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('defaults to dark and changes only the viewer theme', async () => {
    const { useMediaViewerTheme } = await import('@/composables/useMediaViewerTheme');
    const viewerTheme = useMediaViewerTheme();

    expect(viewerTheme.mediaViewerTheme.value).toBe('dark');
    expect(viewerTheme.mediaViewerThemeClass.value).toBe('media-viewer-sheet--dark');

    viewerTheme.toggleMediaViewerTheme();

    expect(viewerTheme.mediaViewerTheme.value).toBe('light');
    expect(viewerTheme.mediaViewerThemeClass.value).toBe('media-viewer-sheet--light');
    expect(localStorage.getItem(STORAGE_KEYS.mediaViewerTheme)).toBe('light');
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
  });

  it('restores a stored light preference for later viewer screens', async () => {
    localStorage.setItem(STORAGE_KEYS.mediaViewerTheme, 'light');
    const { useMediaViewerTheme } = await import('@/composables/useMediaViewerTheme');

    expect(useMediaViewerTheme().mediaViewerTheme.value).toBe('light');
  });

  it('ignores an invalid stored preference', async () => {
    localStorage.setItem(STORAGE_KEYS.mediaViewerTheme, 'sepia');
    const { useMediaViewerTheme } = await import('@/composables/useMediaViewerTheme');

    expect(useMediaViewerTheme().mediaViewerTheme.value).toBe('dark');
  });

  it('centers the shared photo viewer header actions', () => {
    const sharedActionStyles = mediaPreviewSource.match(
      /:global\(\.media-preview-details-toggle\),\s*:global\(\.media-viewer-theme-toggle\)\s*\{([^}]*)\}/
    )?.[1];

    expect(sharedActionStyles).toBeDefined();
    expect(sharedActionStyles).toContain('justify-content: center');
  });
});
