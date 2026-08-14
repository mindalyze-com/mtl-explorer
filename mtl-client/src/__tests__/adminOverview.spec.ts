import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import AdminOverview from '@/components/admin/AdminOverview.vue';
import type { AdminOverviewStatus } from '@/components/admin/adminSections';

const cards: AdminOverviewStatus[] = [
  {
    id: 'processing',
    label: 'Processing',
    value: 'Active',
    detail: '2 active or pending',
    icon: 'bi bi-list-check',
    section: 'processing',
    tone: 'live',
  },
  {
    id: 'data',
    label: 'Data',
    value: 'Reload needed',
    detail: 'Server data changed.',
    icon: 'bi bi-database-check',
    section: 'data-status',
    tone: 'warning',
  },
];

describe('AdminOverview', () => {
  it('renders live status, mobile section index, and dynamic badges', () => {
    const wrapper = mount(AdminOverview, {
      props: { cards, badges: { processing: 'Live', maintenance: '1' } },
      global: {
        stubs: {
          RouterLink: { template: '<a><slot /></a>' },
        },
      },
    });

    expect(wrapper.text()).toContain('Processing');
    expect(wrapper.text()).toContain('Reload needed');
    expect(wrapper.text()).toContain('Import & sync');
    expect(wrapper.text()).toContain('About & credits');
    expect(wrapper.findAll('.admin-nav-badge').map((badge) => badge.text())).toEqual(['Live', '1']);
  });

  it('navigates from status cards, shortcuts, and section rows', async () => {
    const wrapper = mount(AdminOverview, {
      props: { cards },
      global: {
        stubs: {
          RouterLink: { template: '<a><slot /></a>' },
        },
      },
    });

    await wrapper.find('.admin-overview-card').trigger('click');
    await wrapper.find('.admin-shortcut').trigger('click');
    await wrapper.find('.admin-mobile-section').trigger('click');
    await wrapper.find('.admin-mobile-about').trigger('click');

    expect(wrapper.emitted('navigate')).toEqual([['processing'], ['imports'], ['imports']]);
    expect(wrapper.emitted('show-about')).toEqual([[]]);
  });

  it('emits refresh without running an admin action', async () => {
    const wrapper = mount(AdminOverview, {
      props: { cards },
      global: {
        stubs: {
          RouterLink: { template: '<a><slot /></a>' },
        },
      },
    });

    await wrapper.get('button[aria-label="Refresh"]').trigger('click');
    expect(wrapper.emitted('refresh')).toHaveLength(1);
    expect(wrapper.emitted('navigate')).toBeUndefined();
  });
});
