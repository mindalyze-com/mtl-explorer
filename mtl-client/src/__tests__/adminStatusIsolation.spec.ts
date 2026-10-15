import { createPinia } from 'pinia';
import { flushPromises, shallowMount } from '@vue/test-utils';
import { createMemoryHistory, createRouter } from 'vue-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ApplicationStatusResponseDto } from 'x8ing-mtl-api-typescript-fetch';
import AdminDialog from '@/components/admin/AdminDialog.vue';
import AdminOverview from '@/components/admin/AdminOverview.vue';
import { useApplicationStatusStore, type ApplicationStatusSection } from '@/stores/applicationStatusStore';
import type { AdminOverviewStatus } from '@/components/admin/adminSections';

const statusRequest = vi.hoisted(() => vi.fn());
const demoStatusRequest = vi.hoisted(() => vi.fn());

vi.mock('x8ing-mtl-api-typescript-fetch', async (importOriginal) => {
  const actual = await importOriginal<typeof import('x8ing-mtl-api-typescript-fetch')>();
  return {
    ...actual,
    ApplicationStatusControllerApi: class {
      getApplicationStatus = statusRequest;
    },
  };
});

vi.mock('@/utils/serverAdminApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/utils/serverAdminApi')>()),
  getDemoStatus: demoStatusRequest,
}));

vi.mock('@/utils/ServiceHelper', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/utils/ServiceHelper')>();
  return {
    ...actual,
    getGarminToolStatus: async () => ({ gcexportVenvPresent: true, fitExportVenvPresent: true }),
    getServerBuildInfo: async () => ({ version: 'test' }),
  };
});

function statusResponse(include: ApplicationStatusSection[], failedSection?: ApplicationStatusSection) {
  const response: ApplicationStatusResponseDto = {
    generatedAt: new Date(),
    included: new Set(include),
    freshness: { freshnessToken: 'test-revision', items: [] },
    processing: { indexers: [], jobs: [] },
    services: {
      tileMode: 'local',
      plannerEnabled: true,
      map: { ready: true, phase: 'ready' },
      locationSearch: { ready: true, phase: 'ready' },
      planner: { available: true, brouterRunning: true },
    },
    googleHealth: { configurationState: 'DISABLED', connectionState: 'DISCONNECTED' },
  };
  if (failedSection) {
    const key = failedSection === 'google-health' ? 'googleHealth' : failedSection;
    delete response[key];
    response.errors = [{ section: failedSection, code: 'TEST_UNAVAILABLE', message: 'Section unavailable.' }];
  }
  return response;
}

async function mountSettings() {
  const pinia = createPinia();
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: { template: '<div />' } },
      { path: '/admin/:section?', name: 'admin', component: { template: '<div />' } },
    ],
  });
  await router.push('/admin');
  await router.isReady();
  const wrapper = shallowMount(AdminDialog, {
    global: {
      plugins: [pinia, router],
      stubs: { BottomSheet: { template: '<div><slot /></div>' }, AdminOverview: false },
    },
  });
  wrapper.vm.open();
  await flushPromises();
  await vi.advanceTimersByTimeAsync(0);
  const store = useApplicationStatusStore(pinia);
  const card = (id: string) =>
    (wrapper.getComponent(AdminOverview).props('cards') as AdminOverviewStatus[]).find((entry) => entry.id === id);
  const refresh = async () => {
    wrapper.getComponent(AdminOverview).vm.$emit('refresh');
    await flushPromises();
    await vi.advanceTimersByTimeAsync(Math.max(0, (store.nextPollAt ?? Date.now()) - Date.now()));
    await flushPromises();
  };
  return { wrapper, store, card, refresh };
}

describe('Settings status section isolation', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    localStorage.clear();
    statusRequest.mockReset();
    demoStatusRequest.mockResolvedValue({ demoMode: false });
    statusRequest.mockImplementation(({ include }) => Promise.resolve(statusResponse(include)));
  });

  afterEach(() => {
    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it.each<ApplicationStatusSection>(['processing', 'services', 'freshness', 'google-health'])(
    'keeps healthy summary rows available when only %s fails and recovers on refresh',
    async (failedSection) => {
      const { wrapper, store, card, refresh } = await mountSettings();
      try {
        expect(card('processing')?.value).toBe('Idle');
        expect(card('services')?.value).toBe('3/3 available');
        const initialCards = wrapper.getComponent(AdminOverview).props('cards') as AdminOverviewStatus[];
        const failedCard = failedSection === 'freshness' ? 'data' : failedSection;

        statusRequest.mockImplementation(({ include }) => Promise.resolve(statusResponse(include, failedSection)));
        await refresh();

        expect(card(failedCard)).toMatchObject({ value: 'Unavailable', tone: 'error' });
        for (const previous of initialCards.filter((entry) => entry.id !== failedCard)) {
          expect(card(previous.id)).toEqual(previous);
        }
        if (failedSection !== 'services') expect(wrapper.text()).toContain('3/3 available');
        expect(store.sectionStates[failedSection].healthy).toBe(false);

        statusRequest.mockImplementation(({ include }) => Promise.resolve(statusResponse(include)));
        await refresh();
        expect(wrapper.getComponent(AdminOverview).props('cards')).toEqual(initialCards);
        expect(store.sectionStates[failedSection].healthy).toBe(true);
      } finally {
        wrapper.unmount();
        store.$dispose();
      }
    }
  );

  it('marks both summaries unavailable when the whole status request fails', async () => {
    const { wrapper, store, card, refresh } = await mountSettings();
    try {
      statusRequest.mockRejectedValue(new Error('Network unavailable.'));
      await refresh();
      expect(card('processing')).toMatchObject({ value: 'Unavailable', tone: 'error' });
      expect(card('services')).toMatchObject({ value: 'Unavailable', tone: 'error' });
    } finally {
      wrapper.unmount();
      store.$dispose();
    }
  });

  it('reports synthetic health preparation until generation completes and handles unavailable progress', async () => {
    demoStatusRequest.mockResolvedValue({ demoMode: true, syntheticHealthData: true });
    let pending = 1;
    let processingUnavailable = false;
    statusRequest.mockImplementation(({ include }) => {
      const response = statusResponse(include, processingUnavailable ? 'processing' : undefined);
      if (response.processing) {
        response.processing.jobs = [{ job: 'demoHealth', total: 1, pending, done: 1 - pending, failed: 0 }];
      }
      return Promise.resolve(response);
    });
    const { wrapper, store, card, refresh } = await mountSettings();
    try {
      expect(card('google-health')).toMatchObject({
        value: 'Preparing synthetic health data',
        tone: 'live',
        section: 'processing',
      });
      expect(wrapper.text()).not.toContain('are available in Health');

      pending = 0;
      await refresh();
      expect(card('google-health')).toMatchObject({ value: 'Synthetic demo data', tone: 'neutral' });
      expect(wrapper.text()).toContain('are available in Health');

      processingUnavailable = true;
      await refresh();
      expect(card('google-health')).toMatchObject({ value: 'Unavailable', tone: 'error' });
      expect(wrapper.text()).not.toContain('are available in Health');

      processingUnavailable = false;
      await refresh();
      expect(card('google-health')?.value).toBe('Synthetic demo data');
    } finally {
      wrapper.unmount();
      store.$dispose();
    }
  });

  it('does not infer generated health records from the demo feature flag when progress is missing', async () => {
    demoStatusRequest.mockResolvedValue({ demoMode: true, syntheticHealthData: true });
    const { wrapper, store, card } = await mountSettings();
    try {
      expect(card('google-health')).toMatchObject({ value: 'Checking', tone: 'neutral' });
      expect(wrapper.text()).not.toContain('are available in Health');
    } finally {
      wrapper.unmount();
      store.$dispose();
    }
  });
});
