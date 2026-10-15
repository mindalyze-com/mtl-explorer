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
  it('renders live status, section index, and dynamic badges', () => {
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

  it('navigates from status and section rows', async () => {
    const wrapper = mount(AdminOverview, {
      props: { cards },
      global: {
        stubs: {
          RouterLink: { template: '<a><slot /></a>' },
        },
      },
    });

    await wrapper.find('.settings-row--stacked-value').trigger('click');
    await wrapper.find('.admin-section-index .settings-row').trigger('click');
    await wrapper.find('.admin-section-index > button').trigger('click');

    expect(wrapper.emitted('navigate')).toEqual([['processing'], ['preferences']]);
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
