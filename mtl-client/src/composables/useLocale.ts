import { ref, watch } from 'vue'
import { USER_PREFS_KEYS, migrateLegacyKeys } from '@/utils/userPrefs'

const STORAGE_KEY = USER_PREFS_KEYS.locale

/**
 * Well-known locale presets the user can choose from.
 * The value is a BCP 47 locale tag used for Intl / toLocaleString formatting.
 * Language of the UI is NOT affected — only number & date presentation changes.
 */
export const LOCALE_PRESETS = [
  { label: 'Browser default', value: '' },
  { label: 'de-CH  (31.12.2025, 1\'234.56)', value: 'de-CH' },
  { label: 'de-DE  (31.12.2025, 1.234,56)', value: 'de-DE' },
  { label: 'en-US  (12/31/2025, 1,234.56)', value: 'en-US' },
  { label: 'en-GB  (31/12/2025, 1,234.56)', value: 'en-GB' },
  { label: 'fr-CH  (31.12.2025, 1 234,56)', value: 'fr-CH' },
  { label: 'fr-FR  (31/12/2025, 1 234,56)', value: 'fr-FR' },
  { label: 'it-CH  (31.12.2025, 1\'234.56)', value: 'it-CH' },
] as const

/** Maps IANA timezone identifiers to ISO 3166-1 alpha-2 country codes. */
const TZ_COUNTRY_MAP: Record<string, string> = {
  'Europe/Zurich':       'CH',
  'Europe/Berlin':       'DE',
  'Europe/Busingen':     'DE',
  'Europe/Paris':        'FR',
  'Europe/London':       'GB',
  'Europe/Dublin':       'IE',
  'America/New_York':    'US',
  'America/Chicago':     'US',
  'America/Denver':      'US',
  'America/Los_Angeles': 'US',
  'America/Phoenix':     'US',
  'America/Anchorage':   'US',
  'Pacific/Honolulu':    'US',
  'America/Toronto':     'CA',
  'America/Vancouver':   'CA',
  'Australia/Sydney':    'AU',
  'Australia/Melbourne': 'AU',
  'Pacific/Auckland':    'NZ',
}

export interface LocaleDetection {
  /** Matched preset value, or '' if nothing matched. */
  value: string
  /** The browser's primary language tag, e.g. "de-CH". */
  browserLang: string
  /** IANA timezone, e.g. "Europe/Zurich". */
  timezone: string
}

/**
 * Detects the best matching locale preset from browser language + timezone.
 * Language alone is ambiguous (de-CH vs de-DE); combining with timezone resolves it.
 */
export function detectBestLocale(): LocaleDetection {
  const tz = Intl.DateTimeFormat().resolvedOptions().timeZone
  const browserLang = (navigator.languages?.[0] ?? navigator.language ?? '').trim()
  const parts = browserLang.split('-')
  const baseLang = parts[0].toLowerCase()
  const browserRegion = parts[1]?.toUpperCase()
  const tzCountry = TZ_COUNTRY_MAP[tz]

  const presetValues = LOCALE_PRESETS.filter(p => p.value).map(p => p.value as string)
  // Candidates in priority order: TZ-inferred country first, then browser region
  const candidates: string[] = []
  if (tzCountry) candidates.push(`${baseLang}-${tzCountry}`)
  if (browserRegion && browserRegion !== tzCountry) candidates.push(`${baseLang}-${browserRegion}`)

  for (const candidate of candidates) {
    const match = presetValues.find(v => v.toLowerCase() === candidate.toLowerCase())
    if (match) return { value: match, browserLang, timezone: tz }
  }
  return { value: '', browserLang, timezone: tz }
}

function getInitialLocale(): string {
  migrateLegacyKeys()
  const saved = localStorage.getItem(STORAGE_KEY)
  if (saved !== null) return saved
  // First visit: auto-detect but do NOT persist — let applyServerDefaultLocale()
  // take precedence if the server provides a default.
  return detectBestLocale().value
}

// Module-level singleton — all callers share one reactive instance
const formatLocale = ref<string>(getInitialLocale())

watch(formatLocale, (next) => {
  // Always persist — even '' means "user explicitly chose browser default"
  // and must not be overridden by the server default on next visit.
  localStorage.setItem(STORAGE_KEY, next)
})

/**
 * Returns the effective BCP 47 locale tag to use for formatting.
 * If the user hasn't overridden, returns `undefined` which makes
 * `Intl` / `toLocaleString` fall back to the browser default.
 */
export function getFormatLocale(): string | undefined {
  return formatLocale.value || undefined
}

export function useLocale() {
  return {
    /** Reactive ref — empty string means "browser default". */
    formatLocale,

    /** Resolved locale suitable for Intl APIs (undefined = browser default). */
    getFormatLocale,

    setLocale(locale: string): void {
      formatLocale.value = locale
    },
  }
}

/**
 * Apply a server-suggested default locale.
 * Only takes effect when the user has not yet chosen a locale explicitly
 * (i.e. nothing is stored in localStorage).
 */
export function applyServerDefaultLocale(serverDefault: string | null | undefined): void {
  if (!serverDefault) return
  const saved = localStorage.getItem(STORAGE_KEY)
  if (saved === null) {
    // No explicit user choice — use the server suggestion
    localStorage.setItem(STORAGE_KEY, serverDefault)
    formatLocale.value = serverDefault
  }
}
