import type { VersionInfoDto } from 'x8ing-mtl-api-typescript-fetch';
import { formatDateAndTimeWithSeconds } from '@/utils/Utils';

export interface VersionInfoRow {
  key: string;
  label: string;
  value: string;
}

const NOT_AVAILABLE = 'N/A';
const IMAGE_VERSION_KEY = 'image-version';
const IMAGE_BUILD_KEY = 'image-build';

const KEY_LABELS: Record<string, string> = {
  brouter: 'BRouter',
  pmtiles: 'PMTiles',
  protomapsArchive: 'Protomaps archive',
  locationSearchBuild: 'Location search build',
};

export function compactVersionInfo(versionInfo: VersionInfoDto | null | undefined): string {
  if (!versionInfo) return '';
  const parts: string[] = [];
  if (versionInfo.image) {
    parts.push(`Image ${displayValue(versionInfo.image.version)}`);
    parts.push(`Built ${formatVersionDate(versionInfo.image.buildTime)}`);
  }
  parts.push(...versionMapEntries(versionInfo.components).map(([key, value]) => `${labelForKey(key)} ${value}`));
  parts.push(...versionMapEntries(versionInfo.data).map(([key, value]) => `${labelForKey(key)} ${value}`));
  return parts.join(' · ');
}

export function versionInfoRows(versionInfo: VersionInfoDto | null | undefined): VersionInfoRow[] {
  if (!versionInfo) return [];

  const rows: VersionInfoRow[] = [];
  if (versionInfo.image) {
    rows.push(
      { key: IMAGE_VERSION_KEY, label: 'Image version', value: displayValue(versionInfo.image.version) },
      { key: IMAGE_BUILD_KEY, label: 'Image built', value: formatVersionDate(versionInfo.image.buildTime) }
    );
  }
  rows.push(
    ...versionMapEntries(versionInfo?.components).map(([key, value]) => ({
      key: `component-${key}`,
      label: labelForKey(key),
      value,
    }))
  );
  rows.push(
    ...versionMapEntries(versionInfo?.data).map(([key, value]) => ({
      key: `data-${key}`,
      label: labelForKey(key),
      value,
    }))
  );
  return rows;
}

export function displayValue(value: string | null | undefined): string {
  return value?.trim() || NOT_AVAILABLE;
}

function formatVersionDate(value: string | null | undefined): string {
  const formatted = formatDateAndTimeWithSeconds(value);
  return formatted || displayValue(value);
}

function versionMapEntries(map: Record<string, string> | null | undefined): Array<[string, string]> {
  const entries: Array<[string, string]> = [];
  for (const [key, value] of Object.entries(map ?? {})) {
    const trimmed = typeof value === 'string' ? value.trim() : '';
    if (trimmed.length > 0) entries.push([key, trimmed]);
  }
  return entries;
}

function labelForKey(key: string): string {
  return KEY_LABELS[key] ?? key.replace(/([a-z])([A-Z])/g, '$1 $2').replace(/^./, (first) => first.toUpperCase());
}
