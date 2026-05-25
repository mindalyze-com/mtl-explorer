import type { FilterParamsRequest } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterParamsRequest';
import type { FilterInfo } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterInfo';
import type { ParamDefinition } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/ParamDefinition';
import { optionalFilterParamNames } from '@/utils/filterMetadata';

type FilterParamMapKey = 'stringParams' | 'dateTimeParams' | 'geoCircles' | 'geoRectangles' | 'geoPolygons';

const FILTER_PARAM_MAP_KEYS: FilterParamMapKey[] = [
  'stringParams',
  'dateTimeParams',
  'geoCircles',
  'geoRectangles',
  'geoPolygons',
];

export function ensureFilterParamMaps(params: FilterParamsRequest | null | undefined): FilterParamsRequest {
  const normalized = params ?? {};
  normalized.stringParams ??= {};
  normalized.dateTimeParams ??= {};
  normalized.geoCircles ??= {};
  normalized.geoRectangles ??= {};
  normalized.geoPolygons ??= {};
  return normalized;
}

function isBlankString(value: unknown): boolean {
  return typeof value !== 'string' || value.trim().length === 0;
}

function isFiniteNumber(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value);
}

function isEmptyGeoCircle(value: unknown): boolean {
  const circle = value as { lat?: unknown; lng?: unknown; radiusM?: unknown } | null | undefined;
  return (
    !circle ||
    !isFiniteNumber(circle.lat) ||
    !isFiniteNumber(circle.lng) ||
    !isFiniteNumber(circle.radiusM) ||
    circle.radiusM <= 0
  );
}

function isEmptyGeoRectangle(value: unknown): boolean {
  const rectangle = value as
    | { minLat?: unknown; minLng?: unknown; maxLat?: unknown; maxLng?: unknown }
    | null
    | undefined;
  return (
    !rectangle ||
    !isFiniteNumber(rectangle.minLat) ||
    !isFiniteNumber(rectangle.minLng) ||
    !isFiniteNumber(rectangle.maxLat) ||
    !isFiniteNumber(rectangle.maxLng) ||
    rectangle.minLat === rectangle.maxLat ||
    rectangle.minLng === rectangle.maxLng
  );
}

function isEmptyGeoPolygon(value: unknown): boolean {
  const polygon = value as { coordinates?: unknown } | null | undefined;
  return !polygon || !Array.isArray(polygon.coordinates) || polygon.coordinates.length < 3;
}

function isEmptyFilterParamValue(mapKey: FilterParamMapKey, value: unknown): boolean {
  if (mapKey === 'stringParams' || mapKey === 'dateTimeParams') return isBlankString(value);
  if (mapKey === 'geoCircles') return isEmptyGeoCircle(value);
  if (mapKey === 'geoRectangles') return isEmptyGeoRectangle(value);
  return isEmptyGeoPolygon(value);
}

export function pruneFilterParamsForDefinitions(
  params: FilterParamsRequest | null | undefined,
  paramDefinitions: ParamDefinition[]
): FilterParamsRequest {
  const normalized = ensureFilterParamMaps(params);
  const validParams = new Set(
    paramDefinitions.map((paramDefinition) => paramDefinition.name).filter((name): name is string => Boolean(name))
  );

  for (const mapKey of FILTER_PARAM_MAP_KEYS) {
    const paramMap = normalized[mapKey];
    if (!paramMap) continue;
    for (const key of Object.keys(paramMap)) {
      if (!validParams.has(key) || isEmptyFilterParamValue(mapKey, paramMap[key])) delete paramMap[key];
    }
  }

  return normalized;
}

export function hasCompleteStringParamsForDefinitions(
  params: FilterParamsRequest | null | undefined,
  paramDefinitions: ParamDefinition[],
  filterInfo?: FilterInfo | null
): boolean {
  const optionalParams = new Set(optionalFilterParamNames(filterInfo));
  const stringParamNames = paramDefinitions
    .filter((paramDefinition) => paramDefinition.type === 'STRING')
    .map((paramDefinition) => paramDefinition.name)
    .filter((name): name is string => typeof name === 'string' && name.length > 0 && !optionalParams.has(name));
  if (stringParamNames.length === 0) return true;

  const normalized = ensureFilterParamMaps(params);
  return stringParamNames.every((name) => !isBlankString(normalized.stringParams?.[name]));
}
