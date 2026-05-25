import { describe, expect, it } from 'vitest';
import type { VersionInfoDto } from 'x8ing-mtl-api-typescript-fetch';
import { compactVersionInfo, versionInfoRows } from '@/utils/versionInfo';

describe('version info formatting', () => {
  it('does not create placeholder rows when sidecar metadata is missing', () => {
    expect(versionInfoRows(null)).toEqual([]);
    expect(versionInfoRows(undefined)).toEqual([]);
    expect(compactVersionInfo(null)).toBe('');
  });

  it('keeps image, component, and data rows together without repeating the owner label', () => {
    const versionInfo: VersionInfoDto = {
      image: {
        version: '1.62',
        buildTime: '2026-05-24T12:34:56Z',
      },
      components: {
        pmtiles: '1.30.2',
      },
      data: {
        protomapsArchive: '20260524',
      },
    };

    expect(versionInfoRows(versionInfo).map((row) => row.label)).toEqual([
      'Image version',
      'Image built',
      'PMTiles',
      'Protomaps archive',
    ]);
    expect(versionInfoRows(versionInfo).map((row) => row.value)).toContain('1.30.2');
  });
});
