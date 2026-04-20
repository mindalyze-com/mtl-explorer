import { ref, computed, watch } from 'vue'
import { USER_PREFS_KEYS, migrateLegacyKeys } from '@/utils/userPrefs'

export type ColorScheme = 'dark' | 'light'

const STORAGE_KEY = USER_PREFS_KEYS.colorScheme

function getInitialScheme(): ColorScheme {
  migrateLegacyKeys()
  const stored = localStorage.getItem(STORAGE_KEY)
  if (stored === 'dark' || stored === 'light') return stored as ColorScheme
  return 'light'
}

// Module-level singleton — all callers share one reactive instance
const colorScheme = ref<ColorScheme>(getInitialScheme())

function applyToDocument(scheme: ColorScheme): void {
  document.documentElement.setAttribute('data-theme', scheme)
}

// Apply immediately when the module is first imported (before Vue mounts)
applyToDocument(colorScheme.value)

watch(colorScheme, (next) => {
  applyToDocument(next)
  localStorage.setItem(STORAGE_KEY, next)
})

export function useTheme() {
  return {
    colorScheme,
    isDark: computed(() => colorScheme.value === 'dark'),
    setScheme(scheme: ColorScheme): void {
      colorScheme.value = scheme
    },
    toggleScheme(): void {
      colorScheme.value = colorScheme.value === 'dark' ? 'light' : 'dark'
    },
  }
}
