import { computed, readonly, ref } from 'vue';
import { readStorage, removeStorage, STORAGE_KEYS, writeStorage } from '@/utils/appStorage';
import { isMeasurementSystem, type MeasurementSystem } from '@/utils/units';
import { countryForTimezone } from '@/utils/timezones';

export type MeasurementPreferenceSource = 'explicit' | 'server' | 'browser';

export interface MeasurementSystemDetectionInput {
  languages?: readonly string[];
  timezone?: string;
}

const explicitMeasurementSystem = ref<MeasurementSystem | null>(readExplicitPreference());
const serverDefaultMeasurementSystem = ref<MeasurementSystem | null>(null);
const detectedMeasurementSystem = ref<MeasurementSystem>(detectMeasurementSystem());

const measurementSystem = computed<MeasurementSystem>(
  () => explicitMeasurementSystem.value ?? serverDefaultMeasurementSystem.value ?? detectedMeasurementSystem.value
);
const measurementPreferenceSource = computed<MeasurementPreferenceSource>(() => {
  if (explicitMeasurementSystem.value) return 'explicit';
  return serverDefaultMeasurementSystem.value ? 'server' : 'browser';
});
const readonlyExplicitMeasurementSystem = readonly(explicitMeasurementSystem);

export function detectMeasurementSystem(input: MeasurementSystemDetectionInput = {}): MeasurementSystem {
  const languages = input.languages ?? browserLanguages();
  const timezone = input.timezone ?? browserTimezone();
  const primaryRegion = localeRegion(languages[0] ?? '');
  const localeRegionCandidate = primaryRegion ?? firstKnownRegion(languages.slice(1));

  if (localeRegionCandidate === 'US') return 'US_CUSTOMARY';
  if (countryForTimezone(timezone) === 'US') return 'US_CUSTOMARY';
  return 'METRIC';
}

export function applyServerDefaultMeasurementSystem(value: unknown): void {
  serverDefaultMeasurementSystem.value = isMeasurementSystem(value) ? value : null;
}

export function useMeasurementSystem() {
  return {
    explicitMeasurementSystem: readonlyExplicitMeasurementSystem,
    measurementSystem,
    measurementPreferenceSource,
    setMeasurementSystem(system: MeasurementSystem): void {
      explicitMeasurementSystem.value = system;
      writeStorage(STORAGE_KEYS.measurementSystem, system);
    },
    useDefaultMeasurementSystem(): void {
      explicitMeasurementSystem.value = null;
      removeStorage(STORAGE_KEYS.measurementSystem);
    },
  };
}

/** Returns the effective system while registering reactive reads in Vue callers. */
export function getMeasurementSystem(): MeasurementSystem {
  return measurementSystem.value;
}

function readExplicitPreference(): MeasurementSystem | null {
  const stored = readStorage(STORAGE_KEYS.measurementSystem);
  return isMeasurementSystem(stored) ? stored : null;
}

function browserLanguages(): readonly string[] {
  if (typeof navigator === 'undefined') return [];
  if (navigator.languages?.length) return navigator.languages;
  return navigator.language ? [navigator.language] : [];
}

function browserTimezone(): string {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone ?? '';
  } catch {
    return '';
  }
}

function localeRegion(language: string): string | undefined {
  try {
    return new Intl.Locale(language).region;
  } catch {
    return language.match(/[-_]([A-Za-z]{2})(?:$|-)/)?.[1]?.toUpperCase();
  }
}

function firstKnownRegion(languages: readonly string[]): string | undefined {
  for (const language of languages) {
    const region = localeRegion(language);
    if (region) return region;
  }
  return undefined;
}
