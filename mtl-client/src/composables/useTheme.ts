import { ref, computed, watch } from 'vue';
import { readStorage, STORAGE_KEYS, writeStorage } from '@/utils/appStorage';

export type ColorScheme = 'dark' | 'light';

const STORAGE_KEY = STORAGE_KEYS.colorScheme;

function getInitialScheme(): ColorScheme {
  const stored = readStorage(STORAGE_KEY);
  if (stored === 'dark' || stored === 'light') return stored as ColorScheme;
  return 'light';
}

// Module-level singleton — all callers share one reactive instance
const colorScheme = ref<ColorScheme>(getInitialScheme());

function applyToDocument(scheme: ColorScheme): void {
  document.documentElement.setAttribute('data-theme', scheme);
}

// Apply immediately when the module is first imported (before Vue mounts)
applyToDocument(colorScheme.value);

watch(colorScheme, (next) => {
  applyToDocument(next);
  writeStorage(STORAGE_KEY, next);
});

export function useTheme() {
  return {
    colorScheme,
    isDark: computed(() => colorScheme.value === 'dark'),
    setScheme(scheme: ColorScheme): void {
      colorScheme.value = scheme;
    },
    toggleScheme(): void {
      colorScheme.value = colorScheme.value === 'dark' ? 'light' : 'dark';
    },
  };
}
