export const ADMIN_SECTION_IDS = [
  'overview',
  'imports',
  'processing',
  'data-status',
  'maintenance',
  'logs',
  'system',
  'preferences',
  'session',
] as const;

export type AdminSectionId = (typeof ADMIN_SECTION_IDS)[number];
export type AdminSectionGroupId = 'data' | 'system' | 'application';

export interface AdminSectionDefinition {
  id: AdminSectionId;
  label: string;
  description: string;
  icon: string;
  group: AdminSectionGroupId | null;
  path: string;
}

export interface AdminSectionGroup {
  id: AdminSectionGroupId;
  label: string;
}

export type AdminStatusTone = 'neutral' | 'live' | 'success' | 'warning' | 'error';

export interface AdminOverviewStatus {
  id: string;
  label: string;
  value: string;
  detail: string;
  icon: string;
  section: AdminSectionId;
  tone: AdminStatusTone;
}

export const ADMIN_SECTION_GROUPS: readonly AdminSectionGroup[] = [
  { id: 'data', label: 'Data' },
  { id: 'system', label: 'System' },
  { id: 'application', label: 'Application' },
];

export const ADMIN_SECTIONS: readonly AdminSectionDefinition[] = [
  {
    id: 'overview',
    label: 'Overview',
    description: 'System health and common tasks.',
    icon: 'bi bi-grid-1x2',
    group: null,
    path: '/admin',
  },
  {
    id: 'imports',
    label: 'Import & sync',
    description: 'Import track files or start Garmin sync.',
    icon: 'bi bi-cloud-arrow-down',
    group: 'data',
    path: '/admin/imports',
  },
  {
    id: 'processing',
    label: 'Processing',
    description: 'Indexers, background jobs, maps, and routing.',
    icon: 'bi bi-list-check',
    group: 'data',
    path: '/admin/processing',
  },
  {
    id: 'data-status',
    label: 'Data status',
    description: 'Client and server data revisions.',
    icon: 'bi bi-database-check',
    group: 'data',
    path: '/admin/data-status',
  },
  {
    id: 'maintenance',
    label: 'Maintenance',
    description: 'Reload data, rescan files, and manage helpers.',
    icon: 'bi bi-wrench-adjustable',
    group: 'system',
    path: '/admin/maintenance',
  },
  {
    id: 'logs',
    label: 'Server log',
    description: 'Recent server output and runtime errors.',
    icon: 'bi bi-terminal',
    group: 'system',
    path: '/admin/logs',
  },
  {
    id: 'system',
    label: 'System information',
    description: 'Build and external component versions.',
    icon: 'bi bi-info-circle',
    group: 'system',
    path: '/admin/system',
  },
  {
    id: 'preferences',
    label: 'Preferences',
    description: 'Appearance, locale, and measurement units.',
    icon: 'bi bi-sliders',
    group: 'application',
    path: '/admin/preferences',
  },
  {
    id: 'session',
    label: 'Session',
    description: 'Session details, sign out, and local cleanup.',
    icon: 'bi bi-person-lock',
    group: 'application',
    path: '/admin/session',
  },
];

const ADMIN_SECTION_ID_SET = new Set<string>(ADMIN_SECTION_IDS);

export function parseAdminSection(value: unknown): AdminSectionId | null {
  const candidate = Array.isArray(value) ? value[0] : value;
  if (typeof candidate !== 'string' || !ADMIN_SECTION_ID_SET.has(candidate)) return null;
  return candidate as AdminSectionId;
}

export function adminSectionPath(section: AdminSectionId): string {
  return ADMIN_SECTIONS.find((candidate) => candidate.id === section)?.path ?? '/admin';
}

export function sectionsForGroup(group: AdminSectionGroupId): readonly AdminSectionDefinition[] {
  return ADMIN_SECTIONS.filter((section) => section.group === group);
}
