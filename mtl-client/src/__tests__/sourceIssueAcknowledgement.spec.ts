import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { mount, flushPromises } from '@vue/test-utils';
import SourceIssueAction from '@/features/track-review/SourceIssueAction.vue';
import SourceIssueReview from '@/features/track-review/SourceIssueReview.vue';
import { useSourceIssueStore } from '@/stores/sourceIssueStore';
import { useApplicationStatusStore, APPLICATION_STATUS_SECTION } from '@/stores/applicationStatusStore';

vi.mock('@/stores/demoAccessStore', () => ({
  useDemoAccessStore: () => ({ demoMode: false, writeAllowed: true, load: vi.fn() }),
  SHARED_DEMO_READ_ONLY_MESSAGE: 'Changes are unavailable in the shared demo.',
}));

const api = vi.hoisted(() => ({
  targets: vi.fn(),
  acknowledge: vi.fn(),
  reopen: vi.fn(),
  status: vi.fn(),
  issues: vi.fn(),
}));
vi.mock('x8ing-mtl-api-typescript-fetch', async () => ({
  ...(await vi.importActual<typeof import('x8ing-mtl-api-typescript-fetch')>('x8ing-mtl-api-typescript-fetch')),
  SourceIssueControllerApi: class {
    getSourceIssues = api.issues;
    getSourceIssueAcknowledgementTargets = api.targets;
    acknowledgeSourceIssues = api.acknowledge;
    reopenSourceIssues = api.reopen;
  },
  ApplicationStatusControllerApi: class {
    getApplicationStatus = api.status;
  },
}));
vi.mock('@/utils/openApiClient', () => ({ getApiConfiguration: () => ({}) }));
vi.mock('@/features/track-review/api', () => ({ reviewError: async (error: Error) => error.message }));

function button(view: ReturnType<typeof mount>, label: string) {
  return view.findAll('button').find((element) => element.text() === label)!;
}
const target = { issueId: 1, sourceRevision: 'sha256:example' };
const summary = {
  pendingFileCount: 0,
  acknowledgedFileCount: 60,
  indexes: [{ index: 'GPS', pendingFileCount: 0, acknowledgedFileCount: 60 }],
};

describe('source issue acknowledgement', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    Object.values(api).forEach((mock) => mock.mockReset());
  });

  it('refreshes the review list when processing discovers an issue and supports refreshing unchanged counts', async () => {
    const status = useApplicationStatusStore();
    status.processing = { indexers: [{ index: 'MEDIA', pendingIssueFileCount: 0, acknowledgedIssueFileCount: 0 }] };
    const empty = { pendingFileCount: 0, acknowledgedFileCount: 0, indexes: [] };
    const issue = {
      issueId: 1,
      sourceId: 42,
      sourceRevision: 'sha256:a',
      index: 'MEDIA',
      fileName: 'photo.jpg',
      state: 'PENDING',
      message: 'Damaged source.',
    };
    api.issues.mockResolvedValueOnce({ total: 0, issues: [], summary: empty });
    api.issues.mockResolvedValueOnce({ total: 1, issues: [issue], summary: { ...empty, pendingFileCount: 1 } });
    api.issues.mockResolvedValueOnce({
      total: 1,
      issues: [{ ...issue, sourceRevision: 'sha256:b', message: 'Changed source.' }],
      summary: { ...empty, pendingFileCount: 1 },
    });
    const view = mount(SourceIssueReview, { global: { stubs: { RouterLink: true } } });
    await flushPromises();
    expect(view.text()).toContain('Nothing to review');

    status.processing = { indexers: [{ index: 'MEDIA', pendingIssueFileCount: 1, acknowledgedIssueFileCount: 0 }] };
    await flushPromises();
    expect(view.get('.source-issues__count').text()).toBe('1');
    expect(view.text()).toContain('Damaged source.');
    await button(view, 'Refresh').trigger('click');
    await flushPromises();
    expect(view.text()).toContain('Changed source.');
    expect(api.issues).toHaveBeenCalledTimes(3);
    view.unmount();
  });

  it('confirms the complete server snapshot without expanding it and can undo only new acknowledgements', async () => {
    const targets = Array.from({ length: 120 }, (_, index) => ({ ...target, issueId: index + 1 }));
    api.targets.mockResolvedValue({ pendingFileCount: 60, targets });
    api.acknowledge.mockResolvedValue({
      newlyAcknowledged: 119,
      alreadyAcknowledged: 1,
      changedTargets: targets.slice(1),
      staleOrResolved: 0,
      summary,
    });
    api.reopen.mockResolvedValue({ reopened: 119, summary });
    const view = mount(SourceIssueAction);
    await button(view, 'Acknowledge all').trigger('click');
    await flushPromises();
    expect(api.acknowledge).not.toHaveBeenCalled();
    expect(view.text()).toContain('60 files · 120 issues selected');
    api.targets.mockResolvedValue({ targets: [...targets, { ...target, issueId: 121 }] });
    await button(view, 'Confirm acknowledgement').trigger('click');
    await flushPromises();
    expect(api.acknowledge).toHaveBeenCalledExactlyOnceWith({ sourceIssueMutationRequest: { targets } });
    expect(view.text()).toContain('Acknowledged 120 issues.');
    expect(view.emitted('acknowledged')).toEqual([[120]]);
    expect(useSourceIssueStore().revision).toBe(1);
    await button(view, 'Undo').trigger('click');
    await flushPromises();
    expect(api.reopen).toHaveBeenCalledExactlyOnceWith({ sourceIssueMutationRequest: { targets: targets.slice(1) } });
    expect(view.text()).toContain('Acknowledgement undone.');
    view.unmount();
  });

  it('keeps warnings after a failed save and does not retry automatically', async () => {
    const status = useApplicationStatusStore();
    status.processing = { indexers: [{ index: 'GPS', pendingIssueFileCount: 1, failed: 1 }] };
    api.targets.mockResolvedValue({ pendingFileCount: 1, targets: [target] });
    api.acknowledge.mockRejectedValue(new Error('Save failed.'));
    const view = mount(SourceIssueAction, { props: { targets: [target] } });
    await button(view, 'Acknowledge all').trigger('click');
    await flushPromises();
    expect(view.get('[role="alert"]').text()).toBe('Save failed.');
    expect(status.processing?.indexers?.[0]?.pendingIssueFileCount).toBe(1);
    expect(useSourceIssueStore().revision).toBe(0);
    expect(useSourceIssueStore().busy).toBe(false);
    expect(api.acknowledge).toHaveBeenCalledTimes(1);
    view.unmount();
  });

  it('reports an entirely stale selection without claiming success', async () => {
    api.targets.mockResolvedValue({ pendingFileCount: 1, targets: [target] });
    api.acknowledge.mockResolvedValue({
      newlyAcknowledged: 0,
      alreadyAcknowledged: 0,
      staleOrResolved: 1,
      summary: { ...summary, pendingFileCount: 1 },
    });
    const view = mount(SourceIssueAction, { props: { targets: [target] } });
    await button(view, 'Acknowledge all').trigger('click');
    await flushPromises();
    expect(view.text()).toContain('These issues changed or were resolved.');
    expect(view.text()).not.toContain('Acknowledged');
    expect(view.text()).not.toContain('Undo');
    view.unmount();
  });

  it('serializes opposite actions while a write is pending', async () => {
    let complete!: (value: object) => void;
    api.acknowledge.mockImplementation(
      () =>
        new Promise((resolve) => {
          complete = resolve;
        })
    );
    const store = useSourceIssueStore();
    const saving = store.save([target]);
    expect(store.busy).toBe(true);
    await expect(store.save([target], true)).rejects.toThrow('already being saved');
    expect(api.reopen).not.toHaveBeenCalled();
    complete({ summary });
    await saving;
    expect(store.busy).toBe(false);
  });

  it('prevents an older status response restoring pending warnings after a confirmed write', async () => {
    let complete!: (value: object) => void;
    const status = useApplicationStatusStore();
    status.processing = { indexers: [{ index: 'GPS', pendingIssueFileCount: 60, failed: 60, pending: 3 }] };
    api.status.mockImplementation(
      () =>
        new Promise((resolve) => {
          complete = resolve;
        })
    );
    vi.useFakeTimers();
    const polling = status.refresh([APPLICATION_STATUS_SECTION.PROCESSING], { immediate: true });
    await vi.advanceTimersByTimeAsync(0);
    status.applyIssueSummary(summary);
    complete({
      included: new Set([APPLICATION_STATUS_SECTION.PROCESSING]),
      processing: { indexers: [{ index: 'GPS', pendingIssueFileCount: 60, failed: 60, pending: 2 }] },
    });
    await polling;
    expect(status.processing?.indexers?.[0]?.pendingIssueFileCount).toBe(0);
    expect(status.processing?.indexers?.[0]?.failed).toBe(60);
    expect(status.processing?.indexers?.[0]?.pending).toBe(2);
    status.$dispose();
    vi.useRealTimers();
  });
});
