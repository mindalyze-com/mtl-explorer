import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { ReleaseUpdateResponse } from 'x8ing-mtl-api-typescript-fetch';
import { STORAGE_KEYS } from '@/utils/appStorage';
import { useReleaseUpdatesStore } from '@/stores/releaseUpdatesStore';

vi.mock('@/utils/auth', () => ({ isAuthenticated: () => true, getAuthenticatedUsername: () => 'local-user' }));
vi.mock('@/utils/openApiClient', () => ({ getApiConfiguration: () => ({}) }));
const { getUpdateStatus } = vi.hoisted(() => ({ getUpdateStatus: vi.fn() }));
vi.mock('x8ing-mtl-api-typescript-fetch', () => ({
  ReleaseUpdateControllerApi: class {
    getUpdateStatus = getUpdateStatus;
  },
}));

function available(version = '0.9.0', build = '1.520'): ReleaseUpdateResponse {
  return {
    status: 'UPDATE_AVAILABLE',
    installationId: 'installation-a',
    channel: 'stable',
    installedReleaseVersion: '0.8.1',
    installedBuildId: '1.508',
    latestReleaseVersion: version,
    latestBuildId: build,
    summary: 'Published changes.',
  } as ReleaseUpdateResponse;
}

describe('release update reminders', () => {
  beforeEach(() => {
    localStorage.clear();
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  it('dismisses the notice across reloads while keeping the badge', () => {
    const store = useReleaseUpdatesStore();
    store.applyStatus(available());
    expect(store.noticeVisible).toBe(true);
    store.dismissNotice();
    expect(store.hasUpdate).toBe(true);
    setActivePinia(createPinia());
    const reopened = useReleaseUpdatesStore();
    reopened.applyStatus(available());
    expect(reopened.noticeVisible).toBe(false);
    expect(reopened.hasUpdate).toBe(true);
  });

  it('skips exactly one release and announces the next release', () => {
    const store = useReleaseUpdatesStore();
    store.applyStatus(available());
    store.skipVersion();
    store.applyStatus(available());
    expect(store.hasUpdate).toBe(false);
    expect(store.updateAvailable).toBe(true);
    store.applyStatus(available('0.10.0', '1.530'));
    expect(store.noticeVisible).toBe(true);
    expect(store.hasUpdate).toBe(true);
  });

  it('keeps preferences separate for each installation', () => {
    const store = useReleaseUpdatesStore();
    store.applyStatus(available());
    store.skipVersion();
    store.applyStatus({ ...available(), installationId: 'installation-b' });
    expect(store.hasUpdate).toBe(true);
  });

  it('clears the badge after installation and shows one completion notice', () => {
    const store = useReleaseUpdatesStore();
    store.applyStatus(available());
    store.applyStatus({
      ...available(),
      status: 'UP_TO_DATE',
      installedBuildId: '1.520',
      installedReleaseVersion: '0.9.0',
    } as ReleaseUpdateResponse);
    expect(store.hasUpdate).toBe(false);
    expect(store.noticeVisible).toBe(false);
    expect(store.installedNoticeVisible).toBe(true);
    store.dismissNotice();
    store.applyStatus({ ...available(), status: 'UP_TO_DATE', installedBuildId: '1.520' } as ReleaseUpdateResponse);
    expect(store.installedNoticeVisible).toBe(false);
  });

  it('keeps completion pending across reloads until the user opens the release or dismisses it', () => {
    const installed = {
      ...available(),
      status: 'UP_TO_DATE',
      installedBuildId: '1.520',
      installedReleaseVersion: '0.9.0',
    } as ReleaseUpdateResponse;
    const store = useReleaseUpdatesStore();
    store.applyStatus(available());
    store.applyStatus(installed);

    setActivePinia(createPinia());
    const reopened = useReleaseUpdatesStore();
    reopened.applyStatus(installed);
    expect(reopened.installedNoticeVisible).toBe(true);
    reopened.openDetails();

    setActivePinia(createPinia());
    const acknowledged = useReleaseUpdatesStore();
    acknowledged.applyStatus(installed);
    expect(acknowledged.installedNoticeVisible).toBe(false);
  });

  it('clears the completion notice when another tab dismisses it', async () => {
    const installed = {
      ...available(),
      status: 'UP_TO_DATE',
      installedBuildId: '1.520',
      installedReleaseVersion: '0.9.0',
    } as ReleaseUpdateResponse;
    const tabB = useReleaseUpdatesStore();
    tabB.applyStatus(available());
    tabB.applyStatus(installed);
    getUpdateStatus.mockResolvedValue(installed);
    const stopChecks = tabB.startChecks();
    try {
      await tabB.refresh();
      expect(tabB.installedNoticeVisible).toBe(true);

      setActivePinia(createPinia());
      const tabA = useReleaseUpdatesStore();
      tabA.applyStatus(installed);
      expect(tabA.installedNoticeVisible).toBe(true);
      window.dispatchEvent(new StorageEvent('storage', { key: STORAGE_KEYS.releaseUpdates }));
      expect(tabB.installedNoticeVisible).toBe(true);

      tabA.dismissNotice();
      window.dispatchEvent(new StorageEvent('storage', { key: STORAGE_KEYS.releaseUpdates }));
      expect(tabB.installedNoticeVisible).toBe(false);
    } finally {
      stopChecks();
    }
  });

  it('does not throw or announce anything when requests fail', async () => {
    getUpdateStatus.mockRejectedValue(new TypeError('network blocked'));
    const store = useReleaseUpdatesStore();
    await expect(store.refresh()).resolves.toBeUndefined();
    expect(store.requestFailed).toBe(true);
    expect(store.hasUpdate).toBe(false);
    expect(store.noticeVisible).toBe(false);
  });

  it('keeps a cached offer when a later request fails', async () => {
    getUpdateStatus.mockRejectedValue(new TypeError('network blocked'));
    const store = useReleaseUpdatesStore();
    store.applyStatus(available());
    store.dismissNotice();
    await expect(store.refresh(true)).resolves.toBeUndefined();
    expect(store.hasUpdate).toBe(true);
    expect(store.noticeVisible).toBe(false);
    expect(store.status?.latestReleaseVersion).toBe('0.9.0');
  });

  it('remembers a presented notice even without a dismissal', () => {
    const store = useReleaseUpdatesStore();
    store.applyStatus(available());
    store.markNoticeShown();
    expect(store.noticeVisible).toBe(true);
    setActivePinia(createPinia());
    const reopened = useReleaseUpdatesStore();
    reopened.applyStatus(available());
    expect(reopened.noticeVisible).toBe(false);
    expect(reopened.hasUpdate).toBe(true);
  });

  it('ignores malformed persisted preferences', () => {
    localStorage.setItem(STORAGE_KEYS.releaseUpdates, 'null');
    const store = useReleaseUpdatesStore();
    expect(() => store.applyStatus(available())).not.toThrow();
    expect(store.hasUpdate).toBe(true);
  });
  it('clears a component-only offer and announces completion with an unchanged server build', () => {
    const store = useReleaseUpdatesStore();
    store.applyStatus({ ...available('0.9.1', '1.508'), installedComponentKey: 'app=1.508;location-search=2.1' });
    store.skipVersion();
    store.applyStatus({
      ...available('0.9.1', '1.508'),
      status: 'UP_TO_DATE',
      installedReleaseVersion: '0.9.1',
      installedComponentKey: 'app=1.508;location-search=2.2',
    });
    expect(store.hasUpdate).toBe(false);
    expect(store.installedNoticeVisible).toBe(true);
    store.dismissNotice();
    store.applyStatus({
      ...available('0.9.1', '1.508'),
      status: 'UP_TO_DATE',
      installedComponentKey: 'app=1.508;location-search=2.2',
    });
    expect(store.installedNoticeVisible).toBe(false);
  });

  it('does not announce completion from failed checks or newly discovered versions', () => {
    const store = useReleaseUpdatesStore();
    store.applyStatus({ ...available(), status: 'UNKNOWN', installedComponentKey: 'app=1.508;location-search=?' });
    store.applyStatus({ ...available(), status: 'UP_TO_DATE', installedComponentKey: 'app=1.508;location-search=2.2' });
    expect(store.installedNoticeVisible).toBe(false);
    store.applyStatus({ ...available(), installedComponentKey: 'app=1.508;location-search=2.1' });
    store.applyStatus({
      ...available(),
      status: 'UP_TO_DATE',
      checkFailed: true,
      installedComponentKey: 'app=1.508;location-search=2.2',
    });
    expect(store.installedNoticeVisible).toBe(false);
  });
});
