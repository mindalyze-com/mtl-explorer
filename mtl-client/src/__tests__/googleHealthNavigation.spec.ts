import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it } from 'vitest';
import { createPinia } from 'pinia';
import { createMemoryHistory, createRouter } from 'vue-router';
import PrimeVue from 'primevue/config';
import HealthRawDataScreen from '@/features/health/screens/raw/HealthRawDataScreen.vue';

enableAutoUnmount(afterEach);

describe('Google Health settings navigation', () => {
  it('navigates from the real Manage control to the connection settings route', async () => {
    const page = { template: '<div />' };
    const router = createRouter({
      history: createMemoryHistory('/mtl/'),
      routes: [
        { path: '/health', name: 'health', component: page },
        { path: '/admin/:section?', name: 'admin', component: page },
      ],
    });
    await router.push({ name: 'health' });
    await router.isReady();
    const wrapper = mount(HealthRawDataScreen, {
      props: { active: false },
      global: {
        plugins: [createPinia(), router, [PrimeVue, { unstyled: true }]],
        stubs: { BottomSheet: { template: '<div />' } },
      },
    });
    const manage = wrapper.findAll('button').find((button) => button.text() === 'Manage');
    expect(manage).toBeDefined();
    await manage!.trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/admin/imports?connection=google-health');
  });
});
