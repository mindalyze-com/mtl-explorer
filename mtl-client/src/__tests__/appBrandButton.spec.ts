import { enableAutoUnmount, flushPromises, shallowMount } from '@vue/test-utils';
import { defineComponent } from 'vue';
import { createMemoryHistory, createRouter } from 'vue-router';
import { afterEach, expect, it } from 'vitest';
import AppBrandButton from '@/components/info/AppBrandButton.vue';

enableAutoUnmount(afterEach);

it('closes About when opening another section and keeps it closed on return', async () => {
  const Page = defineComponent({ template: '<div />' });
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: Page },
      { path: '/stats', component: Page },
    ],
  });
  await router.push('/');
  const wrapper = shallowMount(AppBrandButton, { global: { plugins: [router] } });
  const overlay = () => wrapper.getComponent({ name: 'AboutSourceOverlay' });
  await wrapper.get('button').trigger('click');
  expect(overlay().props('visible')).toBe(true);
  await router.push('/stats');
  await flushPromises();
  expect(overlay().props('visible')).toBe(false);
  await router.push('/');
  await flushPromises();
  expect(overlay().props('visible')).toBe(false);
  await wrapper.get('button').trigger('click');
  expect(overlay().props('visible')).toBe(true);
});
