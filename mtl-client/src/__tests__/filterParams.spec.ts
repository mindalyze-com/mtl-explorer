import { describe, expect, it } from 'vitest';
import type { FilterInfo } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterInfo';
import type { FilterParamsRequest } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterParamsRequest';
import type { ParamDefinition } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/ParamDefinition';
import {
  ensureFilterParamMaps,
  hasCompleteStringParamsForDefinitions,
  pruneFilterParamsForDefinitions,
} from '@/utils/filterParams';

describe('filter params', () => {
  it('ensures all editable filter param maps exist', () => {
    const params = ensureFilterParamMaps({});

    expect(params.stringParams).toEqual({});
    expect(params.dateTimeParams).toEqual({});
    expect(params.geoCircles).toEqual({});
    expect(params.geoRectangles).toEqual({});
    expect(params.geoPolygons).toEqual({});
  });

  it('drops params that do not belong to the selected filter', () => {
    const params: FilterParamsRequest = {
      stringParams: { KEEP_TEXT: 'abc', DROP_TEXT: 'def' },
      dateTimeParams: { KEEP_DATE: '2026-05-19 12:00:00', DROP_DATE: '2026-05-20 12:00:00' },
      geoCircles: { DROP_CIRCLE: { lat: 47, lng: 8, radiusM: 1000 } },
      geoRectangles: { KEEP_RECTANGLE: { minLat: 46, minLng: 7, maxLat: 47, maxLng: 8 } },
      geoPolygons: { DROP_POLYGON: { coordinates: [[47, 8]] } },
      trackIds: [1, 2, 3],
    };
    const paramDefinitions: ParamDefinition[] = [
      { name: 'KEEP_TEXT', type: 'STRING', label: 'Text' },
      { name: 'KEEP_DATE', type: 'DATE_TIME', label: 'Date' },
      { name: 'KEEP_RECTANGLE', type: 'GEO_RECTANGLE', label: 'Rectangle' },
    ];

    const pruned = pruneFilterParamsForDefinitions(params, paramDefinitions);

    expect(pruned.stringParams).toEqual({ KEEP_TEXT: 'abc' });
    expect(pruned.dateTimeParams).toEqual({ KEEP_DATE: '2026-05-19 12:00:00' });
    expect(pruned.geoCircles).toEqual({});
    expect(pruned.geoRectangles).toEqual({ KEEP_RECTANGLE: { minLat: 46, minLng: 7, maxLat: 47, maxLng: 8 } });
    expect(pruned.geoPolygons).toEqual({});
    expect(pruned.trackIds).toEqual([1, 2, 3]);
  });

  it('drops empty editable param values before sending them to the server', () => {
    const params: FilterParamsRequest = {
      stringParams: { SEARCH_WORD: '  ', KEEP_TEXT: 'abc' },
      dateTimeParams: { DATE_TIME_FROM: '', KEEP_DATE: '2026-05-19 12:00:00' },
      geoCircles: { EMPTY_CIRCLE: {}, KEEP_CIRCLE: { lat: 47, lng: 8, radiusM: 1000 } },
      geoRectangles: {
        EMPTY_RECTANGLE: { minLat: 47 },
        KEEP_RECTANGLE: { minLat: 46, minLng: 7, maxLat: 47, maxLng: 8 },
      },
      geoPolygons: {
        EMPTY_POLYGON: { coordinates: [[47, 8]] },
        KEEP_POLYGON: {
          coordinates: [
            [47, 8],
            [47.1, 8],
            [47, 8.1],
          ],
        },
      },
    };
    const paramDefinitions: ParamDefinition[] = [
      { name: 'SEARCH_WORD', type: 'STRING', label: 'Search' },
      { name: 'KEEP_TEXT', type: 'STRING', label: 'Text' },
      { name: 'DATE_TIME_FROM', type: 'DATE_TIME', label: 'From' },
      { name: 'KEEP_DATE', type: 'DATE_TIME', label: 'Date' },
      { name: 'EMPTY_CIRCLE', type: 'GEO_CIRCLE', label: 'Empty circle' },
      { name: 'KEEP_CIRCLE', type: 'GEO_CIRCLE', label: 'Circle' },
      { name: 'EMPTY_RECTANGLE', type: 'GEO_RECTANGLE', label: 'Empty rectangle' },
      { name: 'KEEP_RECTANGLE', type: 'GEO_RECTANGLE', label: 'Rectangle' },
      { name: 'EMPTY_POLYGON', type: 'GEO_POLYGON', label: 'Empty polygon' },
      { name: 'KEEP_POLYGON', type: 'GEO_POLYGON', label: 'Polygon' },
    ];

    const pruned = pruneFilterParamsForDefinitions(params, paramDefinitions);

    expect(pruned.stringParams).toEqual({ KEEP_TEXT: 'abc' });
    expect(pruned.dateTimeParams).toEqual({ KEEP_DATE: '2026-05-19 12:00:00' });
    expect(pruned.geoCircles).toEqual({ KEEP_CIRCLE: { lat: 47, lng: 8, radiusM: 1000 } });
    expect(pruned.geoRectangles).toEqual({ KEEP_RECTANGLE: { minLat: 46, minLng: 7, maxLat: 47, maxLng: 8 } });
    expect(pruned.geoPolygons).toEqual({
      KEEP_POLYGON: {
        coordinates: [
          [47, 8],
          [47.1, 8],
          [47, 8.1],
        ],
      },
    });
  });

  it('detects incomplete string filter params', () => {
    const paramDefinitions: ParamDefinition[] = [{ name: 'SEARCH_WORD', type: 'STRING', label: 'Search' }];

    expect(hasCompleteStringParamsForDefinitions({ stringParams: { SEARCH_WORD: '' } }, paramDefinitions)).toBe(false);
    expect(hasCompleteStringParamsForDefinitions({ stringParams: { SEARCH_WORD: 'Bern' } }, paramDefinitions)).toBe(
      true
    );
    expect(hasCompleteStringParamsForDefinitions({}, [{ name: 'DATE_TIME_FROM', type: 'DATE_TIME' }])).toBe(true);
  });

  it('allows blank optional string params declared in filter metadata', () => {
    const filterInfo = {
      effectiveUiMetadata: {
        metadataVersion: 2,
        params: {
          DATE_TIME_FROM: { optional: true },
          DATE_TIME_TO: { optional: true },
          DISTANCE_MIN_KM: { optional: true },
          DISTANCE_MAX_KM: { optional: true },
        },
      },
    } as FilterInfo;
    const paramDefinitions: ParamDefinition[] = [
      { name: 'DATE_TIME_FROM', type: 'DATE_TIME', label: 'From' },
      { name: 'DATE_TIME_TO', type: 'DATE_TIME', label: 'To' },
      { name: 'DISTANCE_MIN_KM', type: 'STRING', label: 'Distance Min Km' },
      { name: 'DISTANCE_MAX_KM', type: 'STRING', label: 'Distance Max Km' },
    ];

    expect(
      hasCompleteStringParamsForDefinitions(
        { stringParams: { DISTANCE_MIN_KM: '', DISTANCE_MAX_KM: '   ' } },
        paramDefinitions,
        filterInfo
      )
    ).toBe(true);
  });

  it('requires track picker params unless metadata marks them optional', () => {
    expect(
      hasCompleteStringParamsForDefinitions(
        { stringParams: { TRACK_IDS: '' } },
        [{ name: 'TRACK_IDS', type: 'STRING', label: 'Selected tracks' }]
      )
    ).toBe(false);

    const paramDefinitions: ParamDefinition[] = [
      { name: 'TRACK_IDS', type: 'STRING', label: 'Selected tracks' },
      { name: 'YEAR_FROM', type: 'STRING', label: 'Year From' },
    ];
    const filterInfo = {
      effectiveUiMetadata: {
        metadataVersion: 2,
        params: {
          TRACK_IDS: { optional: true, widget: 'trackPicker' },
          YEAR_FROM: { optional: false, widget: 'number' },
        },
      },
    } as FilterInfo;

    expect(
      hasCompleteStringParamsForDefinitions({ stringParams: { TRACK_IDS: '' } }, paramDefinitions, filterInfo)
    ).toBe(false);
    expect(
      hasCompleteStringParamsForDefinitions(
        { stringParams: { TRACK_IDS: '1,2,3', YEAR_FROM: '2026' } },
        paramDefinitions,
        filterInfo
      )
    ).toBe(true);
  });

  it('still blocks blank required string params when only date-time params are optional', () => {
    const filterInfo = {
      effectiveUiMetadata: {
        metadataVersion: 2,
        params: {
          DATE_TIME_FROM: { optional: true },
          DATE_TIME_TO: { optional: true },
          YEAR_FROM: { optional: false },
        },
      },
    } as FilterInfo;
    const paramDefinitions: ParamDefinition[] = [
      { name: 'DATE_TIME_FROM', type: 'DATE_TIME', label: 'From' },
      { name: 'YEAR_FROM', type: 'STRING', label: 'Year From' },
    ];

    expect(
      hasCompleteStringParamsForDefinitions({ stringParams: { YEAR_FROM: '' } }, paramDefinitions, filterInfo)
    ).toBe(false);
  });
});
