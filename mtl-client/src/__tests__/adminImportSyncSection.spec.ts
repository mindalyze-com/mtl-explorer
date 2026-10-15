import { createPinia } from 'pinia';
import { flushPromises, mount } from '@vue/test-utils';
import { createMemoryHistory, createRouter } from 'vue-router';
import { describe, expect, it, vi } from 'vitest';
import AdminImportSyncSection from '@/components/admin/AdminImportSyncSection.vue';
import { useApplicationStatusStore } from '@/stores/applicationStatusStore';

vi.mock('@/utils/ServiceHelper', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/utils/ServiceHelper')>()),
  getGpxUploadStatus: vi.fn(async () => ({ available: true })),
}));
vi.mock('@/features/health/api/googleHealthApi', () => ({
  googleHealthApi: { setup: vi.fn(async () => ({ configurationState: 'CONFIGURED' })) },
}));

async function mountImports(path = '/admin/imports') {
  const pinia = createPinia();
  useApplicationStatusStore(pinia).googleHealth = {
    configurationState: 'CONFIGURED',
    connectionState: 'CONNECTED',
    job: { state: 'COMPLETED' },
    syncStates: [{ dataType: 'sleep', backfillComplete: false }],
  };
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/admin/:section?', name: 'admin', component: { template: '<div />' } },
      { path: '/health', name: 'health', component: { template: '<div />' } },
    ],
  });
  await router.push(path);
  const wrapper = mount(AdminImportSyncSection, {
    attachTo: document.body,
    props: { active: true, demoMode: false },
    global: {
      plugins: [pinia, router],
      stubs: {
        Button: {
          props: ['label', 'disabled'],
          template: '<button :disabled="disabled">{{ label }}</button>',
        },
        InputText: true,
        GoogleHealthSetupDialog: true,
        GoogleHealthReauthorizationDialog: true,
      },
    },
  });
  await flushPromises();
  return { wrapper, router };
}

describe('Import and sync connection navigation', () => {
  it('preserves a selected upload while opening and leaving connection details', async () => {
    const { wrapper, router } = await mountImports();
    const input = wrapper.get('input[type="file"]');
    Object.defineProperty(input.element, 'files', {
      value: [new File(['<gpx/>'], 'synthetic.gpx', { type: 'application/gpx+xml' })],
    });
    await input.trigger('change');
    expect(wrapper.get('.selected-file-row').text()).toContain('synthetic.gpx');

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Manage')!
      .trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/admin/imports?connection=google-health');
    expect(wrapper.get('.selected-file-row').isVisible()).toBe(false);
    expect(wrapper.get('.gh-admin-card').text()).toContain('Authorization expires');
    expect(wrapper.find('progress').exists()).toBe(false);

    await wrapper.get('.admin-connection-back').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/admin/imports');
    expect(wrapper.get('.selected-file-row').isVisible()).toBe(true);
    expect(wrapper.get('.selected-file-row').text()).toContain('synthetic.gpx');
    wrapper.unmount();
  });

  it('supports a direct connection URL and links pending history to Processing', async () => {
    const { wrapper, router } = await mountImports('/admin/imports?connection=google-health');
    expect(wrapper.get('.gh-admin-card').isVisible()).toBe(true);
    await wrapper.get('.gh-sync-notice a').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/admin/processing#google-health-processing');
    router.back();
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/admin/imports?connection=google-health');
    expect(wrapper.get('.gh-admin-card').isVisible()).toBe(true);
    wrapper.unmount();
  });
});
