import { describe, expect, it } from 'vitest';
import {
  ADMIN_SECTIONS,
  ADMIN_SECTION_GROUPS,
  adminSectionPath,
  parseAdminSection,
  sectionsForGroup,
} from '@/components/admin/adminSections';

describe('admin section registry', () => {
  it('keeps the decision-complete section order and routes', () => {
    expect(ADMIN_SECTIONS.map((section) => section.id)).toEqual([
      'overview',
      'preferences',
      'updates',
      'session',
      'imports',
      'processing',
      'data-status',
      'maintenance',
      'logs',
      'system',
    ]);
    expect(ADMIN_SECTIONS.map((section) => section.path)).toEqual([
      '/admin',
      '/admin/preferences',
      '/admin/updates',
      '/admin/session',
      '/admin/imports',
      '/admin/processing',
      '/admin/data-status',
      '/admin/maintenance',
      '/admin/logs',
      '/admin/system',
    ]);
  });

  it('groups non-overview sections without omissions', () => {
    const grouped = ADMIN_SECTION_GROUPS.flatMap((group) => sectionsForGroup(group.id));
    expect(grouped.map((section) => section.id)).toEqual(ADMIN_SECTIONS.slice(1).map((section) => section.id));
  });

  it('parses route params and rejects unknown sections', () => {
    expect(parseAdminSection('processing')).toBe('processing');
    expect(parseAdminSection(['session'])).toBe('session');
    expect(parseAdminSection('unknown')).toBeNull();
    expect(parseAdminSection(undefined)).toBeNull();
    expect(adminSectionPath('data-status')).toBe('/admin/data-status');
  });
});
