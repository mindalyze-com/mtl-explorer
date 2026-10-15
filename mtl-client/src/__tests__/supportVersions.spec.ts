import { describe, expect, it } from 'vitest';
import { buildSupportVersionReport } from '@/utils/supportVersions';

const client = {
  version: '1.0.0',
  imageVersion: '1.500',
  revision: 'b'.repeat(40),
  buildTime: '2026-09-13T00:00:00Z',
  mode: 'Browser' as const,
};

describe('all versions report', () => {
  it('includes every component while exporting only the explicitly selected version fields', () => {
    const server = {
      version: '1.0.0-SNAPSHOT',
      buildTime: '2026-09-14T00:00:00Z',
      image: { version: '1.501', revision: 'a'.repeat(40), buildTime: '2026-09-14T01:00:00Z' },
      runtime: { java: '25.0.4', postgresql: '18.3', postgis: '3.6.2' },
      serverId: 'private-server-id',
      defaultGpsTrackFilterName: 'private-track-name',
      password: 'private-password',
      path: '/private/gpx',
    };
    const tasks = [
      {
        id: 'vector-map-tiles',
        versionInfo: {
          image: { version: '1.210' },
          components: { pmtiles: '1.30.0', privateToken: 'private-token' },
          data: { protomapsArchive: '20260913', path: '/private/maps' },
        },
      },
      {
        id: 'routing-segments',
        label: 'private-hostname',
        statusLabel: 'private-error',
        versionInfo: { image: { version: '1.100' }, components: { brouter: '1.7.8' } },
      },
      {
        id: 'location-search',
        versionInfo: {
          image: { version: '1.120' },
          components: { python: '3.13.7', sqlite: '3.50.4', apsw: '3.50.4.0' },
          data: {
            schemaVersion: '4',
            sourceBuildDate: '2026-09-12',
            sourceUrl: 'https://private.example/secret',
            rowCount: 'private-count',
          },
        },
      },
      { id: 'private-extra-task', versionInfo: { components: { secret: 'private-extra' } } },
    ];
    const report = buildSupportVersionReport(server, client, {
      tileMode: 'remote',
      map: tasks[0],
      planner: tasks[1],
      locationSearch: tasks[2],
    });
    for (const expected of [
      'Browser client',
      'Application server',
      'Database',
      'Map service',
      'Routing service',
      'Location search',
      'Image version: 1.500',
      'Image version: 1.501',
      'a'.repeat(40),
      'b'.repeat(40),
      'PostgreSQL: 18.3',
      'PostGIS: 3.6.2',
      'Java: 25.0.4',
      'PMTiles: 1.30.0',
      'BRouter: 1.7.8',
      'SQLite: 3.50.4',
      'Python: 3.13.7',
      'APSW: 3.50.4.0',
      'Protomaps archive: 20260913',
      'Dataset schema: 4',
      'Dataset built: 2026-09-12',
    ]) {
      expect(report).toContain(expected);
    }
    expect(report).not.toContain('private');
    expect(report).not.toContain('https://');
  });

  it('keeps missing and older components explicit without inferring their versions', () => {
    const report = buildSupportVersionReport({ image: { version: '1.414' } }, client, null);
    expect(report).toContain('PostgreSQL: Unavailable');
    expect(report).toContain('PostGIS: Unavailable');
    for (const label of ['Map service', 'Routing service', 'Location search']) {
      expect(report).toContain(`${label}\nImage version: Unavailable\nGit revision: Unavailable`);
    }
    expect(report).toContain('Image version: 1.414\nGit revision: Unavailable');
  });
});
