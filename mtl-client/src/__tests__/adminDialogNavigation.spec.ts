import { ref } from 'vue';
import { createPinia } from 'pinia';
import { flushPromises, shallowMount } from '@vue/test-utils';
import { createMemoryHistory, createRouter } from 'vue-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import AdminDialog from '@/components/admin/AdminDialog.vue';

vi.mock('@/composables/useIndexerStatus', () => ({
  useIndexerStatus: () => ({
    summaries: ref([]),
    jobSummaries: ref([]),
    operationalTasks: ref([]),
    isIndexerStatusPollingHealthy: ref(true),
    isIndexing: ref(false),
    isJobPending: ref(false),
    isOperationalTaskActive: ref(false),
    refresh: vi.fn(async () => undefined),
    setFastPolling: vi.fn(),
  }),
}));

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: { template: '<div />' } },
      { path: '/admin/:section?', name: 'admin', component: { template: '<div />' } },
      { path: '/about', name: 'about', component: { template: '<div />' } },
    ],
  });
}

describe('AdminDialog navigation', () => {
  beforeEach(() => vi.clearAllMocks());

  it('uses one sheet and selects the section from the route', async () => {
    const router = createTestRouter();
    await router.push('/admin/processing');
    await router.isReady();
    const wrapper = shallowMount(AdminDialog, {
      global: {
        plugins: [createPinia(), router],
        stubs: {
          BottomSheet: {
            name: 'BottomSheetStub',
            props: { noScrollHint: Boolean },
            template: '<div class="bottom-sheet-stub"><slot /></div>',
          },
        },
      },
    });
    await flushPromises();

    expect(wrapper.findAll('.bottom-sheet-stub')).toHaveLength(1);
    expect(wrapper.getComponent({ name: 'BottomSheetStub' }).props('noScrollHint')).toBe(false);
    const current = wrapper.findAll('.admin-nav-item').find((item) => item.attributes('aria-current') === 'page');
    expect(current?.text()).toContain('Processing');
  });

  it('updates the URL from navigation and normalizes unknown sections', async () => {
    const router = createTestRouter();
    await router.push('/admin');
    await router.isReady();
    const wrapper = shallowMount(AdminDialog, {
      global: {
        plugins: [createPinia(), router],
        stubs: {
          BottomSheet: { template: '<div><slot /></div>' },
        },
      },
    });

    const preferences = wrapper.findAll('.admin-nav-item').find((item) => item.text().includes('Preferences'));
    await preferences?.trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/admin/preferences');

    await router.push('/admin/not-a-section');
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/admin');
  });

  it('starts each selected section at the top of the content pane', async () => {
    const router = createTestRouter();
    await router.push('/admin');
    await router.isReady();
    const wrapper = shallowMount(AdminDialog, {
      global: {
        plugins: [createPinia(), router],
        stubs: {
          BottomSheet: { template: '<div><slot /></div>' },
        },
      },
    });
    const content = wrapper.get('.admin-content-scroll');
    content.element.scrollTop = 180;

    const preferences = wrapper.findAll('.admin-nav-item').find((item) => item.text().includes('Preferences'));
    await preferences?.trigger('click');
    await flushPromises();

    expect(content.element.scrollTop).toBe(0);
  });

  it('returns a directly opened Admin section to the map when the sheet closes', async () => {
    const router = createTestRouter();
    await router.push('/admin/data-status');
    await router.isReady();
    const wrapper = shallowMount(AdminDialog, {
      global: {
        plugins: [createPinia(), router],
        stubs: {
          BottomSheet: {
            name: 'BottomSheetStub',
            emits: ['closed'],
            template: '<div><button data-test="close-sheet" @click="$emit(\'closed\')">Close</button><slot /></div>',
          },
        },
      },
    });

    await wrapper.get('[data-test="close-sheet"]').trigger('click');
    await flushPromises();

    expect(router.currentRoute.value.fullPath).toBe('/');
    expect(wrapper.emitted('tool-closed')).toHaveLength(1);
  });

  it('opens About over Admin without changing the route', async () => {
    const router = createTestRouter();
    await router.push('/admin');
    await router.isReady();
    const wrapper = shallowMount(AdminDialog, {
      global: {
        plugins: [createPinia(), router],
        stubs: {
          BottomSheet: { template: '<div><slot /></div>' },
          AboutView: {
            name: 'AboutView',
            props: { embedded: Boolean, viewportCentered: Boolean },
            emits: ['closed'],
            template: '<div class="about-view-stub" />',
          },
        },
      },
    });

    const about = wrapper.findAll('.admin-nav-item').find((item) => item.text().includes('About & credits'));
    await about?.trigger('click');
    await flushPromises();

    expect(router.currentRoute.value.fullPath).toBe('/admin');
    expect(wrapper.getComponent({ name: 'AboutView' }).props('embedded')).toBe(true);
    expect(wrapper.getComponent({ name: 'AboutView' }).props('viewportCentered')).toBe(false);

    wrapper.getComponent({ name: 'AboutView' }).vm.$emit('closed');
    await flushPromises();

    expect(wrapper.find('.about-view-stub').exists()).toBe(false);
    expect(router.currentRoute.value.fullPath).toBe('/admin');
  });
});
