import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, disposePinia, getActivePinia, setActivePinia } from 'pinia';
import { flushPromises, mount } from '@vue/test-utils';
import { createMemoryHistory, createRouter } from 'vue-router';
import AdminImportSyncSection from '@/components/admin/AdminImportSyncSection.vue';
import AdminMaintenanceSection from '@/components/admin/AdminMaintenanceSection.vue';
import SourceIssueAction from '@/features/track-review/SourceIssueAction.vue';
import SourceIssueFeedback from '@/features/track-review/SourceIssueFeedback.vue';
import SourceIssueStatus from '@/features/track-review/SourceIssueStatus.vue';
import MapDataStatusNotice from '@/components/map/MapDataStatusNotice.vue';
import GpxUploadTab from '@/components/admin/GpxUploadTab.vue';
import { useSourceIssueStore } from '@/stores/sourceIssueStore';
import { getDemoStatus } from '@/utils/serverAdminApi';
import { useDemoAccessStore } from '@/stores/demoAccessStore';
import { warnRequestFailure } from '@/utils/safeLogging';
import { getGpxUploadStatus, installFitExport, installGcexport, triggerGarminExport } from '@/utils/ServiceHelper';

const { getIssues } = vi.hoisted(() => ({ getIssues: vi.fn() }));
vi.mock('x8ing-mtl-api-typescript-fetch', async (importOriginal) => ({
  ...(await importOriginal<typeof import('x8ing-mtl-api-typescript-fetch')>()),
  SourceIssueControllerApi: class {
    getSourceIssues = getIssues;
  },
}));

vi.mock('@/utils/serverAdminApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/utils/serverAdminApi')>()),
  getDemoStatus: vi.fn(),
}));
vi.mock('@/utils/ServiceHelper', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/utils/ServiceHelper')>()),
  getGpxUploadStatus: vi.fn(),
  triggerGarminExport: vi.fn(),
  installFitExport: vi.fn(),
  installGcexport: vi.fn(),
}));
vi.mock('@/composables/useIndexerStatus', () => ({ useIndexerStatus: () => ({ refresh: vi.fn() }) }));
vi.mock('@/utils/safeLogging', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/utils/safeLogging')>()),
  warnRequestFailure: vi.fn(),
}));

const buttonStub = {
  props: ['label', 'disabled'],
  template: '<button type="button" :disabled="disabled" @click="$emit(\'click\')">{{ label }}</button>',
};
const stubs = { Button: buttonStub, InputText: true, GoogleHealthAdminCard: true };

function button(view: ReturnType<typeof mount>, label: string) {
  return view.findAll('button').find((element) => element.text() === label)!;
}

beforeEach(() => {
  vi.clearAllMocks();
  setActivePinia(createPinia());
  vi.mocked(getDemoStatus).mockResolvedValue({ demoMode: true });
  vi.mocked(getGpxUploadStatus).mockResolvedValue({ available: true, message: 'Available' });
});

afterEach(() => {
  disposePinia(getActivePinia()!);
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

describe('shared demo write controls', () => {
  it('offers upload access retry after an initial failure instead of a permanent spinner', async () => {
    vi.mocked(getDemoStatus).mockRejectedValueOnce(new Error('Unavailable')).mockResolvedValue({ demoMode: false });
    const access = useDemoAccessStore();
    await access.load();
    const view = mount(GpxUploadTab, { props: { demoMode: null }, global: { stubs: { Button: buttonStub } } });
    expect(view.text()).toContain('Could not check write access.');
    expect(view.text()).not.toContain('Checking upload access');
    await button(view, 'Retry').trigger('click');
    await flushPromises();
    expect(access.writeAllowed).toBe(true);
    view.unmount();
  });
  it('backs off server failures without dropping confirmed access or synthetic-data status', async () => {
    vi.useFakeTimers();
    vi.mocked(getDemoStatus).mockResolvedValueOnce({ demoMode: false });
    const access = useDemoAccessStore();
    await access.load();
    vi.mocked(getDemoStatus).mockRejectedValue(new Error('Server unavailable'));
    await access.load(true);
    expect(access.writeAllowed).toBe(true);
    for (const delay of [5000, 15000, 60000]) {
      const before = vi.mocked(getDemoStatus).mock.calls.length;
      await vi.advanceTimersByTimeAsync(delay - 1);
      expect(getDemoStatus).toHaveBeenCalledTimes(before);
      await vi.advanceTimersByTimeAsync(1);
      expect(getDemoStatus).toHaveBeenCalledTimes(before + 1);
      expect(access.writeAllowed).toBe(true);
    }
    vi.mocked(getDemoStatus).mockResolvedValue({ demoMode: true, syntheticHealthData: true });
    await vi.advanceTimersByTimeAsync(60000);
    expect(access.syntheticHealthData).toBe(true);
    expect(access.writeAllowed).toBe(false);
    expect(access.error).toBe('');
    expect(vi.getTimerCount()).toBe(0);
  });
  it('retries a failed access probe on reconnection and removes its listener on disposal', async () => {
    vi.mocked(getDemoStatus).mockRejectedValueOnce(new Error('Offline')).mockResolvedValue({ demoMode: false });
    const access = useDemoAccessStore();
    await access.load();
    expect(access.error).toBe('Write access could not be checked.');
    window.dispatchEvent(new Event('online'));
    await flushPromises();
    expect(access.writeAllowed).toBe(true);
    expect(access.error).toBe('');
    expect(getDemoStatus).toHaveBeenCalledTimes(2);
    access.demoMode = null;
    access.error = 'Offline';
    access.$dispose();
    window.dispatchEvent(new Event('online'));
    expect(getDemoStatus).toHaveBeenCalledTimes(2);
  });

  it('lets the mounted map notice retry access without opening another review component', async () => {
    vi.mocked(getDemoStatus).mockRejectedValueOnce(new Error('Offline')).mockResolvedValue({ demoMode: false });
    vi.stubGlobal(
      'ResizeObserver',
      class {
        observe() {}
        disconnect() {}
      }
    );
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/', component: { template: '<div />' } }],
    });
    await router.push('/');
    const view = mount(MapDataStatusNotice, {
      props: {
        status: {
          kind: 'attention',
          label: 'Files need review',
          explanation: '',
          failedFiles: 1,
          detailsSection: 'processing',
          preparing: false,
          outOfSync: false,
        },
        reloading: false,
        mapElement: null,
        placementKey: '',
      },
      global: { plugins: [router], stubs: { Teleport: true, RouterLink: true } },
    });
    await flushPromises();
    await view.get('.map-data-status__chip').trigger('click');
    expect(view.get('.map-data-status__dismiss').attributes('disabled')).toBeDefined();
    expect(view.text()).toContain('Write access could not be checked.');
    await button(view, 'Retry access check').trigger('click');
    await flushPromises();
    expect(view.get('.map-data-status__dismiss').attributes('disabled')).toBeUndefined();
    expect(view.text()).not.toContain('Write access could not be checked.');
    view.unmount();
  });

  it('keeps import views available without offering uploads or Garmin sync', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/admin/:section?', name: 'admin', component: { template: '<div />' } }],
    });
    await router.push('/admin/imports');
    const view = mount(AdminImportSyncSection, {
      props: { active: true, demoMode: true },
      global: { plugins: [router], stubs },
    });
    await flushPromises();

    expect(view.text()).toContain('Track uploads are unavailable in this shared demo.');
    expect(view.text()).not.toContain('remount it with write access');
    expect(view.find('.drop-zone').exists()).toBe(false);
    expect(button(view, 'Sync now').attributes('disabled')).toBeDefined();
    await button(view, 'Sync now').trigger('click');
    expect(triggerGarminExport).not.toHaveBeenCalled();
    view.unmount();
  });

  it('disables server maintenance actions while keeping local reload available', async () => {
    const view = mount(AdminMaintenanceSection, {
      props: {
        demoMode: true,
        toolStatusLoading: false,
        toolStatusError: '',
        toolStatus: {
          gcexportConfiguredVersion: 'v4.6.2',
          gcexportVenvPresent: true,
          fitExportConfiguredProfile: 'default',
          fitExportConfiguredPackages: 'fit-export',
          fitExportVenvPresent: true,
        },
      },
      global: { stubs },
    });

    expect(view.text()).toContain('Manual rescans are unavailable in this shared demo.');
    expect(view.text()).toContain('Garmin helper installation is unavailable in this shared demo.');
    expect(button(view, 'Reload').attributes('disabled')).toBeUndefined();
    for (const label of ['Rescan GPS', 'Rescan Media', 'Install']) {
      for (const control of view.findAll('button').filter((element) => element.text() === label)) {
        expect(control.attributes('disabled')).toBeDefined();
        await control.trigger('click');
      }
    }
    expect(installGcexport).not.toHaveBeenCalled();
    expect(installFitExport).not.toHaveBeenCalled();
    view.unmount();
  });

  it('blocks acknowledgements and undo but leaves feedback dismissal available', async () => {
    const issues = useSourceIssueStore();
    const save = vi.spyOn(issues, 'save');
    const undo = vi.spyOn(issues, 'undo');
    const action = mount(SourceIssueAction, {
      props: { targets: [{ issueId: 17, sourceRevision: 'synthetic' }], showFeedback: false },
      global: { stubs },
    });
    await flushPromises();
    expect(button(action, 'Acknowledge all').attributes('disabled')).toBeDefined();
    await button(action, 'Acknowledge all').trigger('click');
    expect(save).not.toHaveBeenCalled();

    issues.feedbackMessage = 'Acknowledged one issue.';
    issues.undoTargets = [{ issueId: 17, sourceRevision: 'synthetic' }];
    const feedback = mount(SourceIssueFeedback, { global: { stubs } });
    await flushPromises();
    expect(button(feedback, 'Undo').attributes('disabled')).toBeDefined();
    await button(feedback, 'Undo').trigger('click');
    expect(undo).not.toHaveBeenCalled();
    await button(feedback, 'Dismiss').trigger('click');
    expect(issues.feedbackMessage).toBe('');
    action.unmount();
    feedback.unmount();
  });

  it('reuses one demo-status request and permits writes on normal installations', async () => {
    vi.mocked(getDemoStatus).mockResolvedValue({ demoMode: false });
    const access = useDemoAccessStore();
    expect(access.writeAllowed).toBe(false);
    await Promise.all([access.load(), access.load()]);
    await access.load();
    expect(getDemoStatus).toHaveBeenCalledTimes(1);
    expect(access.writeAllowed).toBe(true);
  });

  it('allows file issue status reads while disabling reopen and reassessment', async () => {
    const issues = useSourceIssueStore();
    const prepare = vi.spyOn(issues, 'prepare');
    for (const state of ['ACKNOWLEDGED', 'PENDING']) {
      getIssues.mockResolvedValue({
        total: 1,
        issues: [{ issueId: 17, sourceId: 1, sourceRevision: 'sha256:synthetic', state }],
      });
      const view = mount(SourceIssueStatus, {
        props: { sourceId: 1, sourceHash: state === 'ACKNOWLEDGED' ? 'synthetic' : 'changed' },
        global: { stubs },
      });
      await flushPromises();
      const control = button(view, state === 'ACKNOWLEDGED' ? 'Reopen' : 'Reassess diagnosis');
      expect(control.attributes('disabled')).toBeDefined();
      await control.trigger('click');
      expect(view.emitted('reassess')).toBeUndefined();
      view.unmount();
    }
    expect(getIssues).toHaveBeenCalledTimes(2);
    expect(prepare).not.toHaveBeenCalled();
  });

  it('keeps failed access checks read-only and retries on the next entry', async () => {
    const failure = new Error('Synthetic network failure');
    vi.mocked(getDemoStatus).mockRejectedValueOnce(failure).mockResolvedValueOnce({ demoMode: false });
    const access = useDemoAccessStore();

    await expect(access.load()).resolves.toBeUndefined();
    expect(access.demoMode).toBeNull();
    expect(access.writeAllowed).toBe(false);
    expect(warnRequestFailure).toHaveBeenCalledWith(
      'Installation write access could not be checked',
      failure,
      expect.any(Object)
    );
    await access.load();
    expect(getDemoStatus).toHaveBeenCalledTimes(2);
    expect(getDemoStatus).toHaveBeenLastCalledWith({ throwOnFailure: true });
    expect(access.writeAllowed).toBe(true);
  });
});
