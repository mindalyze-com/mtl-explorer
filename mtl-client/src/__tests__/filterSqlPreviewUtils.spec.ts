import { describe, expect, it } from 'vitest';
import type { FilterInfo } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterInfo';
import { buildSqlParamReferences, sqlForViewMode, sqlViewModeHelp } from '@/utils/filterSqlPreview';

describe('filter SQL preview utilities', () => {
  it('uses placeholders found in the resolved SQL', () => {
    const filterInfo = {
      filterConfig: {
        expression: 'SELECT * FROM ([[~/GPS_TRACK/SmartBaseFilter]])',
      },
      resolvedSQL:
        'SELECT * FROM gps_track WHERE track_name ILIKE :SEARCH_WORD AND ST_DWithin(geom, :GEO_CIRCLE_1_POINT, :GEO_CIRCLE_1_RADIUS)',
      paramsInSQL: new Set(['SEARCH_WORD', 'GEO_CIRCLE_1_POINT', 'GEO_CIRCLE_1_RADIUS', 'RAW_ONLY']),
      paramDefinitions: [
        { name: 'SEARCH_WORD', type: 'STRING', label: 'Search word' },
        { name: 'GEO_CIRCLE_1', type: 'GEO_CIRCLE', label: 'Circle' },
        { name: 'RAW_ONLY', type: 'STRING', label: 'Raw only' },
      ],
      effectiveUiMetadata: {
        metadataVersion: 2,
        paramGroups: {
          filter: { label: 'Filter', order: 10 },
          area: { label: 'Area', order: 20 },
        },
        params: {
          SEARCH_WORD: { label: 'Keyword', group: 'filter', widget: 'text', optional: true },
          GEO_CIRCLE_1: { label: 'Map circle', group: 'area', widget: 'geoCircle', optional: true },
        },
      },
    } as FilterInfo;

    const references = buildSqlParamReferences(filterInfo);

    expect(references.map((reference) => reference.name)).toEqual(['SEARCH_WORD', 'GEO_CIRCLE_1']);
    expect(references[0].placeholders).toEqual([':SEARCH_WORD']);
    expect(references[1].placeholders).toEqual([':GEO_CIRCLE_1_POINT', ':GEO_CIRCLE_1_RADIUS']);
  });

  it('ignores SQL params that have no server definition', () => {
    const filterInfo = {
      resolvedSQL: 'SELECT * FROM gps_track WHERE track_name ILIKE :UNKNOWN_PARAM',
      paramsInSQL: new Set(['UNKNOWN_PARAM']),
      paramDefinitions: [],
      effectiveUiMetadata: { metadataVersion: 2, params: {} },
    } as FilterInfo;

    const references = buildSqlParamReferences(filterInfo);

    expect(references).toEqual([]);
  });

  it('returns the selected SQL view and matching help text', () => {
    expect(sqlForViewMode('template', 'raw', 'resolved')).toBe('raw');
    expect(sqlForViewMode('resolved', 'raw', 'resolved')).toBe('resolved');
    expect(sqlViewModeHelp('template')).toContain('Stored SQL template');
    expect(sqlViewModeHelp('resolved')).toContain('Expanded by the server');
  });
});
