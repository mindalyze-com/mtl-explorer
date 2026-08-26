import type { FilterInfo } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterInfo';
import type { FilterParamsRequest } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterParamsRequest';
import { getMeasurementSystem } from '@/composables/useMeasurementSystem';
import { getFormatLocale } from '@/composables/useLocale';
import { effectiveParamMetadata } from '@/utils/filterMetadata';
import { formatFilterParamSummaryValue } from '@/utils/filterParamUnits';
import type { MeasurementSystem } from '@/utils/units';

const ACTIVE_FILTER_IDENTITY_SEPARATOR = ' · ';

function compactText(value: unknown): string {
  return typeof value === 'string' ? value.trim().replace(/\s+/g, ' ') : '';
}

function orderedStringParamNames(filterInfo?: FilterInfo | null, filterParams?: FilterParamsRequest | null): string[] {
  const stringParams = filterParams?.stringParams ?? {};
  const names: string[] = [];
  const seen = new Set<string>();

  for (const definition of filterInfo?.paramDefinitions ?? []) {
    const name = compactText(definition.name);
    if (!name || seen.has(name) || !Object.prototype.hasOwnProperty.call(stringParams, name)) continue;
    seen.add(name);
    names.push(name);
  }
  for (const name of Object.keys(stringParams)) {
    if (seen.has(name)) continue;
    seen.add(name);
    names.push(name);
  }
  return names;
}

export function formatActiveFilterIdentity(
  filterInfo?: FilterInfo | null,
  filterParams?: FilterParamsRequest | null,
  measurementSystem: MeasurementSystem = getMeasurementSystem(),
  locale: string | undefined = getFormatLocale()
): string {
  const filterConfig = filterInfo?.filterConfig;
  const viewName = compactText(filterConfig?.displayName) || compactText(filterConfig?.filterName);
  if (!viewName) return '';

  const stringParams = filterParams?.stringParams ?? {};
  for (const name of orderedStringParamNames(filterInfo, filterParams)) {
    const criterion = compactText(stringParams[name]);
    if (!criterion) continue;
    const metadata = effectiveParamMetadata(filterInfo, name);
    const summary =
      metadata?.widget === 'number'
        ? formatFilterParamSummaryValue(criterion, metadata.unit, measurementSystem, locale)
        : criterion;
    return `${viewName}${ACTIVE_FILTER_IDENTITY_SEPARATOR}${summary}`;
  }
  return viewName;
}
