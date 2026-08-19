import { computed, ref } from 'vue';
import { readStorage, STORAGE_KEYS, writeStorage } from '@/utils/appStorage';

export type MediaViewerTheme = 'dark' | 'light';

const DEFAULT_MEDIA_VIEWER_THEME: MediaViewerTheme = 'dark';

function initialMediaViewerTheme(): MediaViewerTheme {
  const stored = readStorage(STORAGE_KEYS.mediaViewerTheme);
  return stored === 'light' || stored === 'dark' ? stored : DEFAULT_MEDIA_VIEWER_THEME;
}

const mediaViewerTheme = ref<MediaViewerTheme>(initialMediaViewerTheme());

export function useMediaViewerTheme() {
  return {
    mediaViewerTheme,
    isMediaViewerDark: computed(() => mediaViewerTheme.value === 'dark'),
    mediaViewerThemeClass: computed(() => `media-viewer-sheet--${mediaViewerTheme.value}`),
    setMediaViewerTheme(theme: MediaViewerTheme): void {
      mediaViewerTheme.value = theme;
      writeStorage(STORAGE_KEYS.mediaViewerTheme, theme);
    },
    toggleMediaViewerTheme(): void {
      const nextTheme: MediaViewerTheme = mediaViewerTheme.value === 'dark' ? 'light' : 'dark';
      mediaViewerTheme.value = nextTheme;
      writeStorage(STORAGE_KEYS.mediaViewerTheme, nextTheme);
    },
  };
}
