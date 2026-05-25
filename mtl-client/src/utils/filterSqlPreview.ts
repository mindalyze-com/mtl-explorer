import type { FilterInfo } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterInfo';
import type { ParamDefinition } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/ParamDefinition';
import { effectiveUiMetadata, type FilterParamGroupMetadata } from '@/utils/filterMetadata';

const TRACK_PICKER_WIDGET = 'trackPicker';

export type SqlViewMode = 'template' | 'resolved';

export type SqlParamReference = {
  name: string;
  label: string;
  placeholders: string[];
  detail: string;
};

type NamedParamDefinition = ParamDefinition & { name: string };

export function sqlForViewMode(mode: SqlViewMode, rawSQL: string, resolvedSQL: string): string {
  return mode === 'resolved' ? resolvedSQL : rawSQL;
}

export function sqlViewModeHelp(mode: SqlViewMode): string {
  return mode === 'resolved'
    ? 'Expanded by the server. Placeholders are still bound at execution time.'
    : 'Stored SQL template. Template fragments and placeholders are shown as written.';
}

export function buildSqlParamReferences(filterInfo?: FilterInfo | null): SqlParamReference[] {
  const resolvedSQL = filterInfo?.resolvedSQL ?? '';
  const sqlParamNames = new Set(
    Array.from(filterInfo?.paramsInSQL ?? []).filter((name) => hasPlaceholder(resolvedSQL, name))
  );
  const metadata = effectiveUiMetadata(filterInfo);

  return (filterInfo?.paramDefinitions ?? [])
    .filter((definition): definition is NamedParamDefinition => Boolean(definition.name))
    .map((definition) => {
      const placeholders = matchingPlaceholders(definition.name, sqlParamNames);
      if (placeholders.length === 0) return null;

      const paramMetadata = metadata.params?.[definition.name];
      const widget = String(paramMetadata?.widget || widgetForDefinition(definition));
      const type = typeLabel(definition, widget, paramMetadata?.unit);
      const group = groupLabel(paramMetadata?.group, metadata.paramGroups);
      const optional = paramMetadata?.optional === true;

      return {
        name: definition.name,
        label: String(paramMetadata?.label || definition.label || definition.name),
        placeholders,
        detail: [type, optional ? 'Optional' : 'Required', group].filter(Boolean).join(' - '),
      };
    })
    .filter((reference): reference is SqlParamReference => reference != null);
}

function matchingPlaceholders(name: string, sqlParamNames: Set<string>): string[] {
  return Array.from(sqlParamNames)
    .filter((sqlName) => sqlName === name || sqlName.startsWith(`${name}_`))
    .map((sqlName) => `:${sqlName}`);
}

function hasPlaceholder(sql: string, name: string): boolean {
  const escapedName = name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  return new RegExp(`(?<!:):${escapedName}(?![A-Za-z0-9_])`).test(sql);
}

function typeLabel(definition: NamedParamDefinition, widget: string, unit?: string): string {
  if (widget === TRACK_PICKER_WIDGET) return 'Track picker';
  if (widget === 'number') return unit ? `Number ${unit}` : 'Number';
  return (
    {
      DATE_TIME: 'Date and time',
      GEO_CIRCLE: 'Circle',
      GEO_RECTANGLE: 'Rectangle',
      GEO_POLYGON: 'Polygon',
      STRING: 'Text',
    }[definition.type ?? 'STRING'] ?? 'Parameter'
  );
}

function groupLabel(groupKey?: string, groups: Record<string, FilterParamGroupMetadata> = {}): string {
  if (!groupKey) return 'Parameters';
  return String(groups?.[groupKey]?.label || groupKey);
}

function widgetForDefinition(definition: NamedParamDefinition): string {
  if (definition.type === 'DATE_TIME') return 'dateTime';
  if (definition.type === 'GEO_CIRCLE') return 'geoCircle';
  if (definition.type === 'GEO_RECTANGLE') return 'geoRectangle';
  if (definition.type === 'GEO_POLYGON') return 'geoPolygon';
  return 'text';
}
