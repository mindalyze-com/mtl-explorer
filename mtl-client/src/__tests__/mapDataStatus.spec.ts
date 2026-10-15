import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createMemoryHistory, createRouter } from 'vue-router';
import { createPinia, setActivePinia } from 'pinia';
import MapDataStatusNotice from '@/components/map/MapDataStatusNotice.vue';
import { resolveMapDataStatus, type MapDataStatus } from '@/components/map/mapDataStatus';
import { clearStorage } from '@/utils/appStorage';
import { setToken } from '@/utils/auth';
import { useSourceIssueStore } from '@/stores/sourceIssueStore';
import { SourceIssueControllerApi } from 'x8ing-mtl-api-typescript-fetch';

vi.mock('@/stores/demoAccessStore', () => ({
  useDemoAccessStore: () => ({ demoMode: false, writeAllowed: true, load: vi.fn() }),
  SHARED_DEMO_READ_ONLY_MESSAGE: 'Changes are unavailable in the shared demo.',
}));

const input = {
  loaded: true,
  offline: false,
  processing: { indexers: [{ pending: 3696, failed: 0 }], jobs: [{ pending: 438 }] },
  processingHealthy: true,
  freshnessHealthy: true,
  outOfSync: true,
};
const preparing = resolveMapDataStatus(input)!;

function signIn(username = 'local-user', loginRef = 'first-login') {
  const payload = btoa(JSON.stringify({ sub: username, client_login_ref: loginRef }));
  setToken(`header.${payload}.signature`);
}

function settledInput() {
  return {
    ...input,
    processing: { indexers: [{ index: 'MEDIA', pending: 0, failed: 2, pendingIssueFileCount: 2 }], jobs: [] },
    outOfSync: false,
  };
}

beforeEach(() => {
  clearStorage();
  setActivePinia(createPinia());
  signIn();
});

afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
  vi.useRealTimers();
  document.body.innerHTML = '';
});

describe('combined map data status', () => {
  it('keeps the preparation warning after the latest available map has loaded', () => {
    expect(resolveMapDataStatus({ ...input, outOfSync: false })?.kind).toBe('preparing');
    expect(resolveMapDataStatus({ ...input, processing: { indexers: [], jobs: [{ pending: 1 }] } })?.kind).toBe(
      'preparing'
    );
  });

  it('keeps file discovery visible before any work is queued and returns to review once it finishes', () => {
    const discovering = {
      ...input,
      outOfSync: false,
      processing: {
        indexers: [{ index: 'GPS', pending: 0, pendingIssueFileCount: 1, discoveryInProgress: true }],
        jobs: [],
      },
    };
    expect(resolveMapDataStatus(discovering)).toMatchObject({
      kind: 'preparing',
      label: 'Discovering files…',
      preparing: true,
      failedFiles: 1,
    });
    expect(resolveMapDataStatus(discovering)?.explanation).not.toContain('Your map is up to date');
    discovering.processing.indexers[0]!.discoveryInProgress = false;
    expect(resolveMapDataStatus(discovering)?.kind).toBe('attention');
    discovering.processing.indexers[0]!.pendingIssueFileCount = 0;
    expect(resolveMapDataStatus(discovering)).toBeNull();
  });

  it('requires a map update after preparation finishes and clears only when current', () => {
    const finished = { ...input, processing: { indexers: [], jobs: [] } };
    expect(resolveMapDataStatus(finished)?.kind).toBe('stale');
    expect(resolveMapDataStatus({ ...finished, outOfSync: false })).toBeNull();
    expect(resolveMapDataStatus({ ...input, loaded: false })).toBeNull();
  });

  it('retains a warning for failed files, unavailable status, and cached offline data', () => {
    const failed = {
      ...input,
      processing: { indexers: [{ pending: 0, failed: 30, pendingIssueFileCount: 30 }] },
      outOfSync: false,
    };
    expect(resolveMapDataStatus(failed)).toMatchObject({
      kind: 'attention',
      failedFiles: 30,
      detailsSection: 'processing',
      label: '30 files need review',
    });
    expect(resolveMapDataStatus({ ...failed, processingHealthy: false })?.kind).toBe('unavailable');
    expect(resolveMapDataStatus({ ...failed, freshnessHealthy: false })?.kind).toBe('unavailable');
    expect(resolveMapDataStatus({ ...input, offline: true })?.kind).toBe('offline');
  });

  it('keeps map freshness ahead of settled findings and distinguishes background calculations', () => {
    expect(resolveMapDataStatus({ ...input, processing: { indexers: [{ pending: 0, failed: 1 }] } })?.kind).toBe(
      'stale'
    );
    expect(
      resolveMapDataStatus({ ...input, processing: { indexers: [], jobs: [{ job: 'energy', pending: 1 }] } })?.label
    ).toBe('Updating calculations…');
  });
});

async function mountNotice(status: MapDataStatus | null = preparing) {
  vi.stubGlobal(
    'ResizeObserver',
    class {
      observe() {}
      disconnect() {}
    }
  );
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { name: 'review', path: '/review', component: { template: '<div />' } },
      { name: 'admin', path: '/admin/:section?', component: { template: '<div />' } },
    ],
  });
  await router.push('/');
  return mount(MapDataStatusNotice, {
    props: { status, reloading: false, mapElement: null, placementKey: '' },
    global: { plugins: [router], stubs: { Teleport: true } },
    attachTo: document.body,
  });
}

describe('map data notice interaction', () => {
  it('hides an in-flight confirmation on panel changes and preserves destination focus after saving', async () => {
    vi.useFakeTimers();
    const store = useSourceIssueStore();
    const targets = [{ issueId: 7, sourceRevision: 'sha256:a' }];
    vi.spyOn(store, 'prepare').mockResolvedValue({ pendingFileCount: 1, targets });
    let complete!: (value: object) => void;
    vi.spyOn(SourceIssueControllerApi.prototype, 'acknowledgeSourceIssues').mockImplementation(
      () =>
        new Promise((resolve) => {
          complete = resolve;
        })
    );
    const wrapper = await mountNotice(resolveMapDataStatus(settledInput()));
    await wrapper.get('.map-data-status__chip').trigger('click');
    await wrapper.get('.map-data-status__dismiss').trigger('click');
    await flushPromises();
    await wrapper.get('.map-data-status__confirmation .map-data-status__primary').trigger('click');
    await flushPromises();
    expect(store.busy).toBe(true);
    document.body.dispatchEvent(new Event('pointerdown', { bubbles: true }));
    await flushPromises();
    expect(wrapper.find('.map-data-status__confirmation').exists()).toBe(true);
    await wrapper.setProps({ placementKey: 'settings-open' });
    expect(wrapper.find('.map-data-status__confirmation').exists()).toBe(false);
    const destination = document.createElement('button');
    document.body.append(destination);
    destination.focus();
    complete({ newlyAcknowledged: 1, changedTargets: targets });
    await flushPromises();
    expect(document.activeElement).toBe(destination);
    wrapper.unmount();
  });
  it('collapses on navigation and panel changes without taking focus from the destination', async () => {
    const wrapper = await mountNotice();
    await wrapper.get('.map-data-status__chip').trigger('click');
    await wrapper.get('a').trigger('click');
    await flushPromises();
    expect(wrapper.find('.map-data-status__details').exists()).toBe(false);
    await wrapper.get('.map-data-status__chip').trigger('click');
    await wrapper.setProps({ placementKey: 'stats-open' });
    expect(wrapper.find('.map-data-status__details').exists()).toBe(false);
    await wrapper.get('.map-data-status__chip').trigger('click');
    const destination = document.createElement('button');
    document.body.append(destination);
    destination.focus();
    destination.dispatchEvent(new Event('pointerdown', { bubbles: true }));
    await flushPromises();
    expect(wrapper.find('.map-data-status__details').exists()).toBe(false);
    expect(document.activeElement).toBe(destination);
    wrapper.unmount();
  });
  it('starts compact, unfolds details, and stays minimized across processing updates and reloads', async () => {
    const wrapper = await mountNotice();
    const chip = wrapper.get('.map-data-status__chip');
    expect(chip.attributes('aria-expanded')).toBe('false');
    expect(chip.text()).toBe('Getting your data ready…');
    expect(wrapper.text()).not.toContain('inconsistent');
    await chip.trigger('click');
    expect(wrapper.text()).toContain(
      'Processing is still running, so the map, search, and statistics may not match yet.'
    );
    expect(wrapper.text()).toContain('See Processing details for progress.');
    expect(wrapper.get('a').attributes('href')).toBe('/admin/processing');
    await wrapper.get('.map-data-status__primary').trigger('click');
    expect(wrapper.emitted('reload')).toHaveLength(1);
    await wrapper.setProps({ reloading: true });
    expect(wrapper.get('.map-data-status__primary').attributes('disabled')).toBeDefined();
    expect(wrapper.text()).toContain('Reloading…');
    await chip.trigger('click');
    await wrapper.setProps({ status: { ...preparing, outOfSync: false }, reloading: false });
    expect(wrapper.find('.map-data-status__details').exists()).toBe(false);
    expect(wrapper.text()).toContain('Getting your data ready…');
    await wrapper.setProps({ status: { ...preparing, kind: 'stale', label: 'Map needs updating', preparing: false } });
    expect(chip.attributes('aria-expanded')).toBe('false');
    await wrapper.setProps({ status: null });
    expect(wrapper.find('.map-data-status').exists()).toBe(false);
    wrapper.unmount();
  });

  it('supports Escape and links a freshness-only warning to Data status', async () => {
    const wrapper = await mountNotice();
    await wrapper.setProps({ status: resolveMapDataStatus({ ...input, processing: null }) });
    await wrapper.get('.map-data-status__chip').trigger('click');
    expect(wrapper.get('a').attributes('href')).toBe('/admin/data-status');
    await wrapper.get('.map-data-status').trigger('keydown', { key: 'Escape' });
    expect(wrapper.find('.map-data-status__details').exists()).toBe(false);
    expect(document.activeElement).toBe(wrapper.get('.map-data-status__chip').element);
    wrapper.unmount();
  });

  it('labels retained processing information as last known when status checks fail', async () => {
    const wrapper = await mountNotice();
    await wrapper.setProps({ status: resolveMapDataStatus({ ...input, processingHealthy: false }) });
    await wrapper.get('.map-data-status__chip').trigger('click');
    expect(wrapper.text()).toContain('Last known: preparation was running.');
    wrapper.unmount();
  });

  it('uses server pending counts while preserving outages and raw failed outcomes', async () => {
    const settled = settledInput();
    const wrapper = await mountNotice(resolveMapDataStatus(settled));
    await wrapper.get('.map-data-status__chip').trigger('click');
    expect(wrapper.get('.map-data-status__chip i').classes()).toContain('bi-info-circle');
    expect(wrapper.text()).toContain('2 media files');
    expect(wrapper.text()).toContain('Dismiss hides this notice until new issues appear');
    expect(wrapper.find('.map-data-status__dismiss').exists()).toBe(true);
    expect(wrapper.text()).not.toContain('Processing details');
    expect(wrapper.text()).not.toContain('Reload map');
    const reviewLink = wrapper.findAll('a').find((link) => link.text() === 'Review');
    expect(reviewLink?.attributes('href')).toBe('/review');
    settled.processing.indexers[0]!.pendingIssueFileCount = 0;
    await wrapper.setProps({ status: resolveMapDataStatus(settled) });
    expect(wrapper.find('.map-data-status').exists()).toBe(false);
    expect(settled.processing.indexers[0]!.failed).toBe(2);
    await wrapper.setProps({ status: resolveMapDataStatus({ ...settled, offline: true }) });
    expect(wrapper.text()).toContain('Offline');
    await wrapper.setProps({ status: resolveMapDataStatus({ ...settled, processingHealthy: false }) });
    expect(wrapper.text()).toContain('Data status unavailable');
    settled.processing.indexers[0]!.pendingIssueFileCount = 1;
    await wrapper.setProps({ status: resolveMapDataStatus(settled) });
    expect(wrapper.text()).toContain('1 file needs review');
    wrapper.unmount();
  });

  it('confirms an immutable full selection and offers a short undo', async () => {
    vi.useFakeTimers();
    const store = useSourceIssueStore();
    const targets = [
      { issueId: 7, sourceRevision: 'sha256:a' },
      { issueId: 8, sourceRevision: 'sha256:b' },
      { issueId: 9, sourceRevision: 'sha256:b' },
    ];
    const prepare = vi.spyOn(store, 'prepare').mockResolvedValue({ pendingFileCount: 2, targets });
    const acknowledge = vi.spyOn(SourceIssueControllerApi.prototype, 'acknowledgeSourceIssues').mockResolvedValue({
      newlyAcknowledged: 3,
      alreadyAcknowledged: 0,
      staleOrResolved: 0,
      changedTargets: targets,
    });
    const reopen = vi
      .spyOn(SourceIssueControllerApi.prototype, 'reopenSourceIssues')
      .mockResolvedValue({ reopened: 3 });
    const wrapper = await mountNotice(resolveMapDataStatus(settledInput()));
    await wrapper.get('.map-data-status__chip').trigger('click');
    await wrapper.get('.map-data-status__dismiss').trigger('click');
    await flushPromises();
    expect(wrapper.get('.map-data-status__confirmation').text()).toContain('Dismiss 3 issues in 2 files?');
    expect(acknowledge).not.toHaveBeenCalled();
    await wrapper.get('.map-data-status__confirmation .map-data-status__primary').trigger('click');
    await flushPromises();
    expect(acknowledge).toHaveBeenCalledOnce();
    expect(acknowledge).toHaveBeenCalledWith({ sourceIssueMutationRequest: { targets } });
    expect(prepare).toHaveBeenCalledOnce();
    await wrapper.setProps({ status: null });
    expect(wrapper.get('.map-data-status__undo').text()).toContain('Acknowledged 3 issues.');
    await wrapper.get('.map-data-status__undo button').trigger('click');
    await flushPromises();
    expect(reopen).toHaveBeenCalledWith({ sourceIssueMutationRequest: { targets } });

    await wrapper.setProps({ status: resolveMapDataStatus(settledInput()) });
    await wrapper.get('.map-data-status__chip').trigger('click');
    await wrapper.get('.map-data-status__dismiss').trigger('click');
    await flushPromises();
    await wrapper.get('.map-data-status__confirmation .map-data-status__primary').trigger('click');
    await flushPromises();
    await wrapper.setProps({ status: null });
    expect(wrapper.find('.map-data-status__undo').exists()).toBe(true);
    await vi.advanceTimersByTimeAsync(8_000);
    expect(wrapper.findAll('button').some((button) => button.text() === 'Undo')).toBe(false);
    wrapper.unmount();
  });

  it.each(['attention', 'preparing', 'stale', 'offline', 'unavailable'] as const)(
    'keeps Undo accessible with a remaining %s warning, even when minimized',
    async (kind) => {
      const store = useSourceIssueStore();
      const targets = [{ issueId: 7, sourceRevision: 'sha256:a' }];
      vi.spyOn(store, 'prepare').mockResolvedValue({ pendingFileCount: 1, targets });
      vi.spyOn(SourceIssueControllerApi.prototype, 'acknowledgeSourceIssues').mockResolvedValue({
        newlyAcknowledged: 1,
        staleOrResolved: 1,
        changedTargets: targets,
      });
      const reopen = vi
        .spyOn(SourceIssueControllerApi.prototype, 'reopenSourceIssues')
        .mockResolvedValue({ reopened: 1 });
      const wrapper = await mountNotice(resolveMapDataStatus(settledInput()));
      await wrapper.get('.map-data-status__chip').trigger('click');
      await wrapper.get('.map-data-status__dismiss').trigger('click');
      await flushPromises();
      await wrapper.get('.map-data-status__confirmation .map-data-status__primary').trigger('click');
      await flushPromises();
      const remaining = { ...resolveMapDataStatus(settledInput())!, kind };
      await wrapper.setProps({ status: remaining });
      await wrapper.get('.map-data-status__chip').trigger('click');
      expect(wrapper.find('.map-data-status__details').exists()).toBe(false);
      expect(wrapper.get('.map-data-status__undo').text()).toContain('1 changed issue left open.');
      await wrapper.get('.map-data-status__undo button').trigger('click');
      await flushPromises();
      expect(reopen).toHaveBeenCalledWith({ sourceIssueMutationRequest: { targets } });
      expect(wrapper.get('.map-data-status__chip').text()).toContain(remaining.label);
      wrapper.unmount();
    }
  );

  it('retains exact changed targets after a failed Undo and allows retry after the normal expiry', async () => {
    vi.useFakeTimers();
    const store = useSourceIssueStore();
    const targets = [
      { issueId: 7, sourceRevision: 'sha256:a' },
      { issueId: 8, sourceRevision: 'sha256:b' },
    ];
    const changedTargets = [targets[1]!];
    vi.spyOn(store, 'prepare').mockResolvedValue({ pendingFileCount: 2, targets });
    vi.spyOn(SourceIssueControllerApi.prototype, 'acknowledgeSourceIssues').mockResolvedValue({
      newlyAcknowledged: 1,
      alreadyAcknowledged: 1,
      changedTargets,
    });
    const reopen = vi
      .spyOn(SourceIssueControllerApi.prototype, 'reopenSourceIssues')
      .mockRejectedValueOnce(new Error('Simulated Undo failure'))
      .mockResolvedValue({ reopened: 1 });
    const wrapper = await mountNotice(resolveMapDataStatus(settledInput()));
    await wrapper.get('.map-data-status__chip').trigger('click');
    await wrapper.get('.map-data-status__dismiss').trigger('click');
    await flushPromises();
    await wrapper.get('.map-data-status__confirmation .map-data-status__primary').trigger('click');
    await flushPromises();
    await wrapper.setProps({ status: null });
    await wrapper.get('.map-data-status__undo button').trigger('click');
    await flushPromises();
    expect(wrapper.get('.map-data-status__undo').attributes('role')).toBe('alert');
    expect(wrapper.get('.map-data-status__undo').classes()).toContain('map-data-status__undo--error');
    expect(wrapper.get('.map-data-status__undo').text()).toContain('Simulated Undo failure');
    expect(wrapper.get('.map-data-status__undo button').text()).toBe('Retry Undo');
    await vi.advanceTimersByTimeAsync(16_000);
    expect(wrapper.get('.map-data-status__undo button').text()).toBe('Retry Undo');
    expect(store.undoTargets).toEqual(changedTargets);
    await wrapper.get('.map-data-status__undo button').trigger('click');
    await flushPromises();
    expect(reopen).toHaveBeenCalledTimes(2);
    expect(reopen).toHaveBeenNthCalledWith(1, { sourceIssueMutationRequest: { targets: changedTargets } });
    expect(reopen).toHaveBeenNthCalledWith(2, { sourceIssueMutationRequest: { targets: changedTargets } });
    expect(wrapper.find('[role="alert"]').exists()).toBe(false);
    expect(store.undoTargets).toEqual([]);
    wrapper.unmount();
  });

  it('does not write on Cancel or replace displayed targets when live counts change', async () => {
    const store = useSourceIssueStore();
    const targets = [{ issueId: 7, sourceRevision: 'sha256:a' }];
    let resolveSnapshot!: (snapshot: { pendingFileCount: number; targets: typeof targets }) => void;
    vi.spyOn(store, 'prepare').mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveSnapshot = resolve;
        })
    );
    const acknowledge = vi.spyOn(SourceIssueControllerApi.prototype, 'acknowledgeSourceIssues').mockResolvedValue({
      newlyAcknowledged: 1,
      changedTargets: targets,
    });
    const wrapper = await mountNotice(resolveMapDataStatus(settledInput()));
    await wrapper.get('.map-data-status__chip').trigger('click');
    await wrapper.get('.map-data-status__dismiss').trigger('click');
    const changed = settledInput();
    changed.processing.indexers[0]!.pendingIssueFileCount = 10;
    await wrapper.setProps({ status: resolveMapDataStatus(changed) });
    resolveSnapshot({ pendingFileCount: 1, targets });
    await flushPromises();
    expect(wrapper.get('.map-data-status__confirmation').text()).toContain('Dismiss 1 issue in 1 file?');
    await wrapper.get('.map-data-status__confirmation button:last-child').trigger('click');
    expect(acknowledge).not.toHaveBeenCalled();
    expect(wrapper.find('.map-data-status__confirmation').exists()).toBe(false);

    await wrapper.get('.map-data-status__dismiss').trigger('click');
    resolveSnapshot({ pendingFileCount: 1, targets });
    await flushPromises();
    await wrapper.get('.map-data-status__confirmation .map-data-status__primary').trigger('click');
    await flushPromises();
    expect(acknowledge).toHaveBeenCalledWith({ sourceIssueMutationRequest: { targets } });
    wrapper.unmount();
  });

  it('ignores a snapshot arriving after Cancel and invalidates a selection when sign-in changes', async () => {
    const store = useSourceIssueStore();
    const targets = [{ issueId: 7, sourceRevision: 'sha256:a' }];
    let resolveSnapshot!: (snapshot: { pendingFileCount: number; targets: typeof targets }) => void;
    const prepare = vi
      .spyOn(store, 'prepare')
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveSnapshot = resolve;
          })
      )
      .mockResolvedValue({ pendingFileCount: 1, targets });
    const acknowledge = vi.spyOn(SourceIssueControllerApi.prototype, 'acknowledgeSourceIssues');
    const wrapper = await mountNotice(resolveMapDataStatus(settledInput()));
    await wrapper.get('.map-data-status__chip').trigger('click');
    await wrapper.get('.map-data-status__dismiss').trigger('click');
    await wrapper.get('.map-data-status__confirmation button').trigger('click');
    resolveSnapshot({ pendingFileCount: 1, targets });
    await flushPromises();
    expect(wrapper.find('.map-data-status__confirmation').exists()).toBe(false);
    expect(acknowledge).not.toHaveBeenCalled();
    await wrapper.get('.map-data-status__dismiss').trigger('click');
    await flushPromises();
    expect(wrapper.get('.map-data-status__confirmation').text()).toContain('Dismiss 1 issue in 1 file?');
    store.sessionRevision++;
    await flushPromises();
    expect(wrapper.find('.map-data-status__confirmation').exists()).toBe(false);
    expect(prepare).toHaveBeenCalledTimes(2);
    expect(acknowledge).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it('retains the confirmed selection after a failed write so a retry cannot acknowledge new arrivals', async () => {
    const store = useSourceIssueStore();
    const targets = [{ issueId: 7, sourceRevision: 'sha256:a' }];
    const prepare = vi.spyOn(store, 'prepare').mockResolvedValue({ pendingFileCount: 1, targets });
    const acknowledge = vi
      .spyOn(SourceIssueControllerApi.prototype, 'acknowledgeSourceIssues')
      .mockRejectedValueOnce(new Error('Simulated acknowledgement failure'))
      .mockResolvedValue({ newlyAcknowledged: 1, changedTargets: targets });
    const wrapper = await mountNotice(resolveMapDataStatus(settledInput()));
    await wrapper.get('.map-data-status__chip').trigger('click');
    await wrapper.get('.map-data-status__dismiss').trigger('click');
    await flushPromises();
    await wrapper.get('.map-data-status__confirmation .map-data-status__primary').trigger('click');
    await flushPromises();
    expect(wrapper.get('[role="alert"]').text()).toContain('Simulated acknowledgement failure');
    const changed = settledInput();
    changed.processing.indexers[0]!.pendingIssueFileCount = 10;
    await wrapper.setProps({ status: resolveMapDataStatus(changed) });
    expect(wrapper.get('.map-data-status__confirmation').text()).toContain('Dismiss 1 issue in 1 file?');
    await wrapper.get('.map-data-status__confirmation .map-data-status__primary').trigger('click');
    await flushPromises();
    expect(acknowledge).toHaveBeenCalledTimes(2);
    expect(acknowledge).toHaveBeenNthCalledWith(1, { sourceIssueMutationRequest: { targets } });
    expect(acknowledge).toHaveBeenNthCalledWith(2, { sourceIssueMutationRequest: { targets } });
    expect(prepare).toHaveBeenCalledOnce();
    expect(wrapper.find('[role="alert"]').exists()).toBe(false);
    wrapper.unmount();
  });

  it('places the notice below overlapping map guidance and responds when it closes', async () => {
    vi.useFakeTimers();
    const map = document.createElement('div');
    map.getBoundingClientRect = () => new DOMRect(0, 0, 390, 844);
    const guidance = document.createElement('div');
    guidance.className = 'measure-map-overlay';
    guidance.getBoundingClientRect = () => new DOMRect(7, 8, 375, 87);
    document.body.append(map, guidance);
    const wrapper = await mountNotice();
    await wrapper.setProps({ mapElement: map });
    await flushPromises();
    await vi.advanceTimersByTimeAsync(32);
    expect(wrapper.get('.map-data-status').attributes('style')).toContain('top: 103px');
    guidance.remove();
    await wrapper.setProps({ placementKey: 'closed' });
    await flushPromises();
    await vi.advanceTimersByTimeAsync(32);
    expect(wrapper.get('.map-data-status').attributes('style')).toContain('top: 64px');
    wrapper.unmount();
  });
});
