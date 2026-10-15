import { ref } from 'vue';
import { createPinia } from 'pinia';
import { enableAutoUnmount, flushPromises, shallowMount } from '@vue/test-utils';
import { createMemoryHistory, createRouter } from 'vue-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import AdminDialog from '@/components/admin/AdminDialog.vue';

const googleHealthApiMock = vi.hoisted(() => ({ status: vi.fn() }));
const demoStatusMock = vi.hoisted(() => vi.fn());
const applicationStatusRequestMock = vi.hoisted(() => vi.fn());
const googleHealthStatusState = vi.hoisted(() => ({
  value: {
    configurationState: 'CONFIGURED',
    connectionState: 'CONNECTED',
    authorizationAction: 'NONE',
    grantedCategories: {},
    syncStates: [],
    job: { state: 'IDLE' },
  },
}));

vi.mock('@/features/health/api/googleHealthApi', () => ({
  googleHealthApi: googleHealthApiMock,
}));

vi.mock('x8ing-mtl-api-typescript-fetch', async () => {
  const actual = await vi.importActual<typeof import('x8ing-mtl-api-typescript-fetch')>(
    'x8ing-mtl-api-typescript-fetch'
  );
  return {
    ...actual,
    ApplicationStatusControllerApi: class {
      getApplicationStatus(request: { include: string[] }) {
        return applicationStatusRequestMock(request);
      }
    },
  };
});

vi.mock('@/utils/serverAdminApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/utils/serverAdminApi')>()),
  getDemoStatus: demoStatusMock,
}));

vi.mock('@/utils/ServiceHelper', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/utils/ServiceHelper')>();
  return { ...actual };
});

vi.mock('@/composables/useIndexerStatus', () => ({
  useIndexerStatus: () => ({
    summaries: ref([]),
    jobSummaries: ref([]),
    operationalTasks: ref([]),
    countAnimations: ref(new Map()),
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

enableAutoUnmount(afterEach);

afterEach(() => {
  vi.clearAllTimers();
  vi.useRealTimers();
});

describe('AdminDialog navigation', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.clearAllMocks();
    demoStatusMock.mockResolvedValue({ demoMode: false });
    googleHealthStatusState.value = {
      configurationState: 'CONFIGURED',
      connectionState: 'CONNECTED',
      authorizationAction: 'NONE',
      grantedCategories: {},
      syncStates: [],
      job: { state: 'IDLE' },
    };
    applicationStatusRequestMock.mockImplementation(({ include }: { include: string[] }) => {
      const includes = new Set(include);
      return Promise.resolve({
        generatedAt: new Date(),
        included: includes,
        freshness: includes.has('freshness') ? { freshnessToken: 'fresh-1', items: [] } : undefined,
        processing: includes.has('processing') ? { indexers: [], jobs: [] } : undefined,
        services: includes.has('services')
          ? { tileMode: 'local', plannerEnabled: false, map: { ready: true }, locationSearch: { ready: true } }
          : undefined,
        googleHealth: includes.has('google-health') ? googleHealthStatusState.value : undefined,
      });
    });
  });

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
    await vi.advanceTimersByTimeAsync(0);

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
    await vi.advanceTimersByTimeAsync(0);
    expect(router.currentRoute.value.fullPath).toBe('/admin/preferences');

    await router.push('/admin/not-a-section');
    await flushPromises();
    await vi.advanceTimersByTimeAsync(0);
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
    await vi.advanceTimersByTimeAsync(0);

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
    await vi.advanceTimersByTimeAsync(0);

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
    await vi.advanceTimersByTimeAsync(0);

    expect(router.currentRoute.value.fullPath).toBe('/admin');
    expect(wrapper.getComponent({ name: 'AboutView' }).props('embedded')).toBe(true);
    expect(wrapper.getComponent({ name: 'AboutView' }).props('viewportCentered')).toBe(false);

    wrapper.getComponent({ name: 'AboutView' }).vm.$emit('closed');
    await flushPromises();
    await vi.advanceTimersByTimeAsync(0);

    expect(wrapper.find('.about-view-stub').exists()).toBe(false);
    expect(router.currentRoute.value.fullPath).toBe('/admin');
  });

  it('surfaces Google Health reauthorization on the overview and Import & sync navigation', async () => {
    googleHealthStatusState.value = {
      configurationState: 'CONFIGURED',
      connectionState: 'RECONNECT_REQUIRED',
      authorizationAction: 'REAUTHORIZE',
      grantedCategories: {},
      syncStates: [{ dataType: 'sleep' }],
      job: { state: 'IDLE' },
    };
    const router = createTestRouter();
    await router.push('/admin');
    await router.isReady();
    const wrapper = shallowMount(AdminDialog, {
      global: {
        plugins: [createPinia(), router],
        stubs: { BottomSheet: { template: '<div><slot /></div>' } },
      },
    });

    wrapper.vm.open();
    await flushPromises();
    await vi.advanceTimersByTimeAsync(0);

    const overview = wrapper.getComponent({ name: 'AdminOverview' });
    expect(overview.props('cards')).toContainEqual(
      expect.objectContaining({ id: 'google-health', value: 'Reauthorize', tone: 'warning', section: 'imports' })
    );
    const imports = wrapper.findAll('.admin-nav-item').find((item) => item.text().includes('Import & sync'));
    expect(imports?.text()).toContain('!');
  });

  it('includes an active Google Health sync in Processing status', async () => {
    googleHealthStatusState.value = {
      configurationState: 'CONFIGURED',
      connectionState: 'CONNECTED',
      authorizationAction: 'NONE',
      grantedCategories: { sleep: true },
      syncStates: [],
      job: { state: 'RUNNING', totalUnits: 80, completedUnits: 20 },
    };
    const router = createTestRouter();
    await router.push('/admin');
    await router.isReady();
    const wrapper = shallowMount(AdminDialog, {
      global: {
        plugins: [createPinia(), router],
        stubs: { BottomSheet: { template: '<div><slot /></div>' } },
      },
    });

    wrapper.vm.open();
    await flushPromises();
    await vi.advanceTimersByTimeAsync(0);

    const overview = wrapper.getComponent({ name: 'AdminOverview' });
    expect(overview.props('cards')).toContainEqual(
      expect.objectContaining({ id: 'google-health', value: 'Syncing', tone: 'live', section: 'processing' })
    );
    expect(wrapper.getComponent({ name: 'AdminProcessingSection' }).props('googleHealthStatus')).toEqual(
      expect.objectContaining({ job: expect.objectContaining({ state: 'RUNNING' }) })
    );
    const processing = wrapper.findAll('.admin-nav-item').find((item) => item.text().includes('Processing'));
    expect(processing?.text()).toContain('Live');
  });

  it('keeps pending health history visible in the overview and Processing navigation between passes', async () => {
    googleHealthStatusState.value = {
      configurationState: 'CONFIGURED',
      connectionState: 'CONNECTED',
      syncStates: [{ dataType: 'heart-rate', backfillComplete: false }],
      job: { state: 'COMPLETED', totalUnits: 140, completedUnits: 140 },
    };
    const router = createTestRouter();
    await router.push('/admin');
    await router.isReady();
    const wrapper = shallowMount(AdminDialog, {
      global: {
        plugins: [createPinia(), router],
        stubs: { BottomSheet: { template: '<div><slot /></div>' } },
      },
    });
    wrapper.vm.open();
    await flushPromises();
    await vi.advanceTimersByTimeAsync(0);

    const cards = wrapper.getComponent({ name: 'AdminOverview' }).props('cards');
    expect(cards).toContainEqual(
      expect.objectContaining({ id: 'google-health', value: 'History pending', tone: 'neutral' })
    );
    expect(cards).toContainEqual(
      expect.objectContaining({ id: 'processing', value: 'Pending', detail: '1 active or pending' })
    );
    const processing = wrapper.findAll('.admin-nav-item').find((item) => item.text().includes('Processing'));
    expect(processing?.text()).toContain('Pending');
    expect(processing?.text()).not.toContain('Live');
    wrapper.unmount();
  });

  it('presents Google Health as a demo preview without a warning badge', async () => {
    demoStatusMock.mockResolvedValue({ demoMode: true });
    const router = createTestRouter();
    await router.push('/admin');
    await router.isReady();
    const wrapper = shallowMount(AdminDialog, {
      global: {
        plugins: [createPinia(), router],
        stubs: { BottomSheet: { template: '<div><slot /></div>' } },
      },
    });

    wrapper.vm.open();
    await flushPromises();
    await vi.advanceTimersByTimeAsync(0);

    const overview = wrapper.getComponent({ name: 'AdminOverview' });
    expect(overview.props('cards')).toContainEqual(
      expect.objectContaining({ id: 'google-health', value: 'Demo preview', tone: 'neutral', section: 'imports' })
    );
    expect(wrapper.getComponent({ name: 'AdminImportSyncSection' }).props('demoMode')).toBe(true);
    const imports = wrapper.findAll('.admin-nav-item').find((item) => item.text().includes('Import & sync'));
    expect(imports?.text()).not.toContain('!');
    expect(googleHealthApiMock.status).not.toHaveBeenCalled();
  });

  it('keeps confirmed Settings access when a later overview refresh fails', async () => {
    const router = createTestRouter();
    await router.push('/admin');
    const wrapper = shallowMount(AdminDialog, {
      global: { plugins: [createPinia(), router], stubs: { BottomSheet: { template: '<div><slot /></div>' } } },
    });
    wrapper.vm.open();
    await flushPromises();
    expect(wrapper.getComponent({ name: 'AdminImportSyncSection' }).props('demoMode')).toBe(false);
    demoStatusMock.mockRejectedValueOnce(new Error('Temporary outage'));
    wrapper.getComponent({ name: 'AdminOverview' }).vm.$emit('refresh');
    await flushPromises();
    expect(wrapper.getComponent({ name: 'AdminImportSyncSection' }).props('demoMode')).toBe(false);
  });

  it('keeps writes disabled after a failed installation probe while continuing aggregate reads', async () => {
    demoStatusMock.mockRejectedValue(new Error('Demo status is unavailable.'));
    const router = createTestRouter();
    await router.push('/admin');
    await router.isReady();
    const wrapper = shallowMount(AdminDialog, {
      global: {
        plugins: [createPinia(), router],
        stubs: { BottomSheet: { template: '<div><slot /></div>' } },
      },
    });

    wrapper.vm.open();
    await flushPromises();
    await vi.advanceTimersByTimeAsync(0);

    expect(wrapper.getComponent({ name: 'AdminImportSyncSection' }).props('demoMode')).toBeNull();
    expect(applicationStatusRequestMock).toHaveBeenCalled();
    expect(googleHealthApiMock.status).not.toHaveBeenCalled();
  });

  it.each(['IDLE', 'QUEUED', 'RUNNING'])(
    'uses one aggregate status request every eight seconds when health is %s',
    async (state) => {
      googleHealthStatusState.value.job.state = state;
      vi.useFakeTimers();
      try {
        const router = createTestRouter();
        await router.push('/');
        await router.isReady();
        const wrapper = shallowMount(AdminDialog, {
          global: {
            plugins: [createPinia(), router],
            stubs: { BottomSheet: { template: '<div><slot /></div>' } },
          },
        });

        wrapper.vm.open();
        await vi.advanceTimersByTimeAsync(0);
        await flushPromises();
        await vi.advanceTimersByTimeAsync(0);
        expect(applicationStatusRequestMock).toHaveBeenCalledTimes(1);

        await vi.advanceTimersByTimeAsync(7_999);
        expect(applicationStatusRequestMock).toHaveBeenCalledTimes(1);
        await vi.advanceTimersByTimeAsync(1);
        expect(applicationStatusRequestMock).toHaveBeenCalledTimes(2);
        expect(applicationStatusRequestMock.mock.calls.every(([request]) => request.include.length === 4)).toBe(true);
        expect(googleHealthApiMock.status).not.toHaveBeenCalled();
        wrapper.unmount();
      } finally {
        vi.clearAllTimers();
        vi.useRealTimers();
      }
    }
  );
});
