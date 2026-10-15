import type { BuildInfoResponse, ImageVersionInfoDto, ServicesStatusDto } from 'x8ing-mtl-api-typescript-fetch';

export interface ClientVersionInfo {
  version?: string | null;
  imageVersion?: string | null;
  revision?: string | null;
  buildTime?: string | null;
  mode: 'Browser' | 'PWA';
}

const UNAVAILABLE = 'Unavailable';
// Deliberately enumerate exportable fields. Status messages, source URLs, paths,
// identifiers, configuration and arbitrary metadata must never enter this report.
const SIDECARS = [
  {
    key: 'map',
    label: 'Map service',
    components: [['pmtiles', 'PMTiles']],
    data: [['protomapsArchive', 'Protomaps archive']],
  },
  { key: 'planner', label: 'Routing service', components: [['brouter', 'BRouter']], data: [] },
  {
    key: 'locationSearch',
    label: 'Location search',
    components: [
      ['python', 'Python'],
      ['sqlite', 'SQLite'],
      ['apsw', 'APSW'],
    ],
    data: [
      ['sourceBuildDate', 'Dataset built'],
      ['schemaVersion', 'Dataset schema'],
    ],
  },
] as const;

function value(input: string | null | undefined): string {
  return input?.trim().replace(/[\r\n\t]+/g, ' ') || UNAVAILABLE;
}

function imageLines(image?: ImageVersionInfoDto | null): string[] {
  return [
    `Image version: ${value(image?.version)}`,
    `Git revision: ${value(image?.revision)}`,
    `Image built: ${value(image?.buildTime)}`,
  ];
}

export function buildSupportVersionReport(
  server: BuildInfoResponse | null,
  client: ClientVersionInfo,
  services: ServicesStatusDto | null | undefined
): string {
  const lines = [
    'MTL Explorer — all versions',
    '',
    'Browser client',
    `Version: ${value(client.version)}`,
    `Image version: ${value(client.imageVersion)}`,
    `Git revision: ${value(client.revision)}`,
    `Built: ${value(client.buildTime)}`,
    `Mode: ${client.mode}`,
    '',
    'Application server',
    `Version: ${value(server?.version)}`,
    `Built: ${value(server?.buildTime)}`,
    ...imageLines(server?.image),
    `Java: ${value(server?.runtime?.java)}`,
    '',
    'Database',
    `PostgreSQL: ${value(server?.runtime?.postgresql)}`,
    `PostGIS: ${value(server?.runtime?.postgis)}`,
  ];
  for (const sidecar of SIDECARS) {
    const info = services?.[sidecar.key]?.versionInfo;
    lines.push('', sidecar.label, ...imageLines(info?.image));
    for (const [key, label] of sidecar.components) lines.push(`${label}: ${value(info?.components?.[key])}`);
    for (const [key, label] of sidecar.data) lines.push(`${label}: ${value(info?.data?.[key])}`);
  }
  return lines.join('\n');
}
