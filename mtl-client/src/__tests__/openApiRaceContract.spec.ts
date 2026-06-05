import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

type OpenApiSchema = {
  components?: {
    schemas?: Record<string, SchemaObject>;
  };
};

type SchemaObject = {
  type?: string;
  format?: string;
  $ref?: string;
  properties?: Record<string, SchemaObject>;
  items?: SchemaObject;
  additionalProperties?: SchemaObject | boolean;
};

const schemaPath = resolve(process.cwd(), '../mtl-api/open-api-schema/schema.json');
const openApiSchema = JSON.parse(readFileSync(schemaPath, 'utf8')) as OpenApiSchema;

describe('OpenAPI race crossing contract', () => {
  it('keeps race point coordinates as numeric arrays for the generated client', () => {
    const schemas = openApiSchema.components?.schemas ?? {};
    const crossingPointsResponse = schemas.CrossingPointsResponseDto;
    const crossingsPerTrack = schemas.CrossingsPerTrackDto;
    const crossing = schemas.CrossingDto;
    const dataPoint = schemas.GpsTrackDataPointDto;
    const geoPoint = schemas.GeoPointDto;

    expect(crossingPointsResponse?.properties?.crossings?.additionalProperties).toMatchObject({
      $ref: '#/components/schemas/CrossingsPerTrackDto',
    });
    expect(crossingsPerTrack?.properties?.crossings?.items).toMatchObject({
      $ref: '#/components/schemas/CrossingDto',
    });
    expect(crossing?.properties?.gpsTrackDataPoint).toMatchObject({
      $ref: '#/components/schemas/GpsTrackDataPointDto',
    });
    expect(dataPoint?.properties?.pointLongLat).toMatchObject({
      $ref: '#/components/schemas/GeoPointDto',
    });
    expect(geoPoint?.properties?.coordinates).toMatchObject({
      type: 'array',
      items: { type: 'number', format: 'double' },
    });
  });
});
