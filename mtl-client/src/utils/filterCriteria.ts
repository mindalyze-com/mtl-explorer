import { endOfDay, endOfYear, startOfDay, startOfYear, subDays, subYears } from 'date-fns';
import type { FilterInfo } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterInfo';
import type { FilterParamsRequest } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterParamsRequest';
import { effectiveUiMetadata, optionalFilterParamNames } from '@/utils/filterMetadata';

export const FILTER_DATE_FROM = 'DATE_TIME_FROM';
export const FILTER_DATE_TO = 'DATE_TIME_TO';
const FILTER_YEAR_FROM = 'YEAR_FROM';
const FILTER_YEAR_TO = 'YEAR_TO';
const MIN_YEAR = 1;
const MAX_YEAR = 9999;
const RECENT_DAY_COUNT = 30;

export const FILTER_DATE_PRESETS = [
  { key: 'recent', label: 'Last 30 days' },
  { key: 'year', label: 'This year' },
  { key: 'previous-year', label: 'Last year' },
  { key: 'all', label: 'All time' },
] as const;

export type FilterDatePreset = (typeof FILTER_DATE_PRESETS)[number]['key'];

export function filterDateRange(preset: FilterDatePreset, now = new Date()): { from: Date | null; to: Date | null } {
  if (preset === 'all') return { from: null, to: null };
  if (preset === 'previous-year') {
    const previousYear = subYears(now, 1);
    return { from: startOfYear(previousYear), to: endOfYear(previousYear) };
  }
  return {
    from: preset === 'year' ? startOfYear(now) : startOfDay(subDays(now, RECENT_DAY_COUNT - 1)),
    to: endOfDay(now),
  };
}

export function filterDateValue(value: unknown): Date | null {
  const date =
    value instanceof Date ? value : typeof value === 'string' && value ? new Date(value.replace(' ', 'T')) : null;
  return date && Number.isFinite(date.getTime()) ? date : null;
}

/** Validate editable fields before resolving a filter; matching remains server-owned. */
export function filterCriteriaErrors(info?: FilterInfo, params?: FilterParamsRequest): Record<string, string> {
  const errors: Record<string, string> = {};
  const metadata = effectiveUiMetadata(info).params ?? {};
  const optional = new Set(optionalFilterParamNames(info));
  const definitions = info?.paramDefinitions ?? [];
  for (const definition of definitions) {
    const name = definition.name;
    if (!name || definition.type !== 'STRING') continue;
    const value = params?.stringParams?.[name]?.trim() ?? '';
    const label = metadata[name]?.label || definition.label || name;
    if (!value && !optional.has(name)) errors[name] = `Enter ${label.toLowerCase()}.`;
    if (value && metadata[name]?.widget === 'number' && !Number.isFinite(Number(value))) {
      errors[name] = 'Enter a valid number.';
    }
    if (
      value &&
      (name === FILTER_YEAR_FROM || name === FILTER_YEAR_TO) &&
      (!/^[0-9]+$/.test(value) ||
        value.length > String(MAX_YEAR).length ||
        Number(value) < MIN_YEAR ||
        Number(value) > MAX_YEAR)
    ) {
      errors[name] = `Enter a whole year between ${MIN_YEAR} and ${MAX_YEAR}.`;
    }
    if (value && metadata[name]?.widget === 'number' && name.includes('_MIN_')) {
      const maximumName = name.replace('_MIN_', '_MAX_');
      const maximum = params?.stringParams?.[maximumName]?.trim();
      if (definitions.some((item) => item.name === maximumName) && maximum && Number(value) > Number(maximum)) {
        errors[maximumName] = 'The maximum must be at least the minimum.';
      }
    }
  }
  const yearFrom = params?.stringParams?.[FILTER_YEAR_FROM]?.trim();
  const yearTo = params?.stringParams?.[FILTER_YEAR_TO]?.trim();
  if (
    definitions.some((item) => item.name === FILTER_YEAR_FROM) &&
    definitions.some((item) => item.name === FILTER_YEAR_TO) &&
    yearFrom &&
    yearTo &&
    !errors[FILTER_YEAR_FROM] &&
    !errors[FILTER_YEAR_TO] &&
    Number(yearFrom) > Number(yearTo)
  ) {
    errors[FILTER_YEAR_TO] = 'The to year must be on or after the from year.';
  }
  const from = filterDateValue(params?.dateTimeParams?.[FILTER_DATE_FROM]);
  const to = filterDateValue(params?.dateTimeParams?.[FILTER_DATE_TO]);
  if (from && to && from > to) errors[FILTER_DATE_TO] = 'The end must be on or after the start.';
  return errors;
}
