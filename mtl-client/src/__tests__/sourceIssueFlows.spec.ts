import { beforeEach, afterEach, expect, it, vi } from 'vitest';
import { createPinia, disposePinia, getActivePinia, setActivePinia } from 'pinia';
import { flushPromises, mount } from '@vue/test-utils';
import { reactive } from 'vue';
import SourceIssueAction from '@/features/track-review/SourceIssueAction.vue';
import SourceIssueFeedback from '@/features/track-review/SourceIssueFeedback.vue';
import SourceIssueReview from '@/features/track-review/SourceIssueReview.vue';
import SourceIssueStatus from '@/features/track-review/SourceIssueStatus.vue';
import TrackReviewTool from '@/features/track-review/TrackReviewTool.vue';
import { useSourceIssueStore } from '@/stores/sourceIssueStore';
import { useApplicationStatusStore } from '@/stores/applicationStatusStore';
import { DIAGNOSTIC_AUTH_CHANGED } from '@/utils/diagnosticApiConfiguration';
vi.mock('@/stores/demoAccessStore', () => ({
  useDemoAccessStore: () => ({ demoMode: false, writeAllowed: true, load: vi.fn() }),
  SHARED_DEMO_READ_ONLY_MESSAGE: 'Changes are unavailable in the shared demo.',
}));
const api = vi.hoisted(() => ({
  issues: vi.fn(),
  targets: vi.fn(),
  acknowledge: vi.fn(),
  reopen: vi.fn(),
  queue: vi.fn(),
  detail: vi.fn(),
  assess: vi.fn(),
}));
const navigation = vi.hoisted(() => ({ route: {} as any, push: vi.fn() }));
vi.mock('x8ing-mtl-api-typescript-fetch', async () => ({
  ...(await vi.importActual('x8ing-mtl-api-typescript-fetch')),
  SourceIssueControllerApi: class {
    getSourceIssues = api.issues;
    getSourceIssueAcknowledgementTargets = api.targets;
    acknowledgeSourceIssues = api.acknowledge;
    reopenSourceIssues = api.reopen;
  },
}));
vi.mock('@/utils/openApiClient', () => ({ getApiConfiguration: () => ({}) }));
vi.mock('@/utils/serverAdminApi', () => ({ triggerIndexerRescan: vi.fn() }));
vi.mock('@/router', () => ({ default: { push: vi.fn() } }));
vi.mock('vue-router', () => ({
  useRoute: () => navigation.route,
  useRouter: () => ({ push: navigation.push }),
  RouterLink: { template: '<a><slot /></a>' },
}));
vi.mock('@/features/track-review/api', () => ({
  reviewApi: () => ({ getTrackReviewQueue: api.queue, getSourceReview: api.detail, assessTrackSource: api.assess }),
  stateLabel: (v: string) => v,
  reasonLabel: (v: string) => v,
  decisionLabel: (v: string) => v,
  reviewError: async (e: Error) => e.message,
  saveDownload: vi.fn(),
}));
vi.mock('@/components/ui/BottomSheet.vue', () => ({
  default: {
    emits: ['back'],
    template: '<div><button data-test="review-back" @click="$emit(\'back\')">Back to queue</button><slot /></div>',
  },
}));
vi.mock('@/features/track-review/SourcePathComparison.vue', () => ({ default: { template: '<div />' } }));
vi.mock('@/features/track-review/SourcePointInspector.vue', () => ({ default: { template: '<div />' } }));
type Row = {
  issueId: number;
  sourceId: number;
  sourceRevision: string;
  index: string;
  fileName: string;
  state: string;
  message: string;
  acknowledgedAt?: Date;
};
let rows: Row[];
const row = (issueId = 1, sourceId = 1, state = 'PENDING', sourceRevision = 'sha256:a'): Row => ({
  issueId,
  sourceId,
  sourceRevision,
  index: 'GPS',
  fileName: `file-${sourceId}.gpx`,
  state,
  message: 'Parsing failed.',
});
const target = (issue: Row) => ({ issueId: issue.issueId, sourceRevision: issue.sourceRevision });
function summary() {
  const pending = new Set(rows.filter((i) => i.state === 'PENDING').map((i) => i.sourceId));
  const acknowledged = new Set(
    rows.filter((i) => i.state === 'ACKNOWLEDGED' && !pending.has(i.sourceId)).map((i) => i.sourceId)
  );
  return {
    pendingFileCount: pending.size,
    acknowledgedFileCount: acknowledged.size,
    indexes: [...new Set(rows.map((issue) => issue.index))].map((index) => ({
      index,
      pendingFileCount: new Set(
        rows.filter((issue) => issue.index === index && issue.state === 'PENDING').map((issue) => issue.sourceId)
      ).size,
      acknowledgedFileCount: new Set(
        rows
          .filter((issue) => issue.index === index && issue.state === 'ACKNOWLEDGED' && !pending.has(issue.sourceId))
          .map((issue) => issue.sourceId)
      ).size,
    })),
  };
}
function button(view: ReturnType<typeof mount>, label: string) {
  return view.findAll('button').find((b) => b.text() === label)!;
}
const sources = [1, 2, 3].map((sourceId) => ({
  sourceId,
  fileName: `file-${sourceId}.gpx`,
  sourceHash: 'a',
  state: 'NEEDS_REVIEW',
  pointCount: 1,
}));
function detail(sourceId: number) {
  return { source: sources[sourceId - 1], diagnosis: { findings: [] }, tracks: [], sections: [], totalPointCount: 1 };
}
function acknowledge({
  sourceIssueMutationRequest: { targets },
}: {
  sourceIssueMutationRequest: { targets: ReturnType<typeof target>[] };
}) {
  const changedTargets: ReturnType<typeof target>[] = [];
  let alreadyAcknowledged = 0,
    staleOrResolved = 0;
  for (const selected of targets) {
    const issue = rows.find(
      (i) =>
        i.issueId === selected.issueId &&
        i.sourceRevision === selected.sourceRevision &&
        ['PENDING', 'ACKNOWLEDGED'].includes(i.state)
    );
    if (!issue) staleOrResolved++;
    else if (issue.state === 'ACKNOWLEDGED') alreadyAcknowledged++;
    else {
      issue.state = 'ACKNOWLEDGED';
      issue.acknowledgedAt = new Date();
      changedTargets.push(selected);
    }
  }
  return {
    newlyAcknowledged: changedTargets.length,
    alreadyAcknowledged,
    staleOrResolved,
    changedTargets,
    summary: summary(),
  };
}
beforeEach(() => {
  setActivePinia(createPinia());
  Object.values(api).forEach((fn) => fn.mockReset());
  navigation.route = reactive({ name: 'review', params: {}, query: { view: 'recordings' } });
  navigation.push.mockImplementation((next) => {
    navigation.route.params = next.params ?? {};
    navigation.route.query = next.query ?? {};
    return Promise.resolve();
  });
  rows = [row(1, 1), row(2, 2), row(3, 3)];
  api.queue.mockResolvedValue({ sources, needsReviewCount: 3 });
  api.detail.mockImplementation(async ({ sourceId }) => detail(sourceId));
  api.issues.mockImplementation(async ({ sourceId, index, state, offset = 0, limit = 50 }) => {
    const selected = rows
      .filter(
        (i) =>
          (sourceId == null || i.sourceId === sourceId) &&
          (!index || i.index === index) &&
          (!state || i.state === state)
      )
      .sort((a, b) => b.issueId - a.issueId);
    return {
      total: selected.length,
      issues: selected.slice(offset, offset + limit).map((i) => ({ ...i })),
      summary: summary(),
    };
  });
  api.targets.mockImplementation(async ({ sourceId, index, state = 'PENDING' }) => {
    const selected = rows.filter(
      (i) => (sourceId == null || i.sourceId === sourceId) && (!index || i.index === index) && i.state === state
    );
    const fileCount = new Set(selected.map((i) => i.sourceId)).size;
    return { pendingFileCount: state === 'PENDING' ? fileCount : 0, targets: selected.map(target) };
  });
  api.acknowledge.mockImplementation(async (request) => acknowledge(request));
  api.reopen.mockImplementation(async ({ sourceIssueMutationRequest: { targets } }) => {
    const changedTargets = targets.filter((selected: ReturnType<typeof target>) => {
      const issue = rows.find(
        (i) =>
          i.issueId === selected.issueId && i.sourceRevision === selected.sourceRevision && i.state === 'ACKNOWLEDGED'
      );
      if (!issue) return false;
      issue.state = 'PENDING';
      delete issue.acknowledgedAt;
      return true;
    });
    return {
      reopened: changedTargets.length,
      staleOrResolved: targets.length - changedTargets.length,
      changedTargets,
      summary: summary(),
    };
  });
});
afterEach(() => {
  disposePinia(getActivePinia()!);
  vi.useRealTimers();
  localStorage.clear();
});
function mountOpenTool() {
  const view = mount(TrackReviewTool);
  view.vm.open();
  return view;
}

it('keeps the Issues badge on the displayed list snapshot while processing counts advance', async () => {
  navigation.route.query = { view: 'issues' };
  const status = useApplicationStatusStore();
  status.processing = { indexers: [{ index: 'GPS', pendingIssueFileCount: 57 }] };
  const view = mountOpenTool();
  await flushPromises();
  const badge = () => view.get('.review-modes .review-badge').text();
  const pending = () => view.get('.source-issues__states .source-issues__count').text();
  expect(badge()).toBe('3');
  expect(pending()).toBe('3');
  let complete!: (value: object) => void;
  api.issues.mockImplementationOnce(
    () =>
      new Promise((resolve) => {
        complete = resolve;
      })
  );
  status.processing = { indexers: [{ index: 'GPS', pendingIssueFileCount: 60 }] };
  await flushPromises();
  expect(badge()).toBe('3');
  expect(pending()).toBe('3');
  rows.push(row(4, 4));
  complete({ total: rows.length, issues: rows, summary: summary() });
  await flushPromises();
  expect(badge()).toBe('4');
  expect(pending()).toBe('4');
  view.unmount();
});

it('uses the same index scope for the Issues badge and its To review count', async () => {
  navigation.route.query = { view: 'issues', index: 'GPS' };
  rows.push({ ...row(4, 4), index: 'MEDIA' });
  const view = mountOpenTool();
  await flushPromises();
  expect(view.get('.review-modes .review-badge').text()).toBe('3');
  expect(view.get('.source-issues__states .source-issues__count').text()).toBe('3');
  view.unmount();
});
async function openFirst() {
  const view = mountOpenTool();
  await flushPromises();
  await view.findAll('.review-sources button')[0]!.trigger('click');
  await flushPromises();
  expect(view.get('h2').text()).toBe('file-1.gpx');
  return view;
}
it('submits the displayed per-file revision and leaves its unseen replacement pending', async () => {
  rows = [row()];
  const displayed = target(rows[0]!);
  const view = mount(SourceIssueReview);
  await flushPromises();
  rows = [row(9, 1, 'PENDING', 'sha256:b')];
  await button(view, 'Acknowledge').trigger('click');
  await flushPromises();
  expect(api.targets).not.toHaveBeenCalled();
  expect(api.acknowledge).toHaveBeenCalledExactlyOnceWith({ sourceIssueMutationRequest: { targets: [displayed] } });
  expect(rows[0]!.state).toBe('PENDING');
  expect(view.text()).toContain('These issues changed or were resolved.');
  view.unmount();
});
it('keeps bulk success and Undo visible after every pending row disappears', async () => {
  rows = [row()];
  const view = mount(SourceIssueReview);
  await flushPromises();
  await button(view, 'Acknowledge all').trigger('click');
  await flushPromises();
  await button(view, 'Confirm acknowledgement').trigger('click');
  await flushPromises();
  expect(view.findAll('.source-issues__file')).toHaveLength(0);
  expect(view.text()).toContain('Acknowledged 1 issue.');
  expect(view.text()).toContain('No issues are waiting for review.');
  expect(view.text()).not.toContain('All files were imported');
  view.unmount();
  const nextView = mount(SourceIssueFeedback);
  await button(nextView, 'Undo').trigger('click');
  await flushPromises();
  expect(rows[0]!.state).toBe('PENDING');
  expect(nextView.text()).toContain('Acknowledgement undone.');
  nextView.unmount();
});
it('reopens every acknowledged finding of a file across issue pages in one write', async () => {
  rows = Array.from({ length: 51 }, (_, i) => row(i + 1, 1, 'ACKNOWLEDGED'));
  const view = mount(SourceIssueReview);
  await flushPromises();
  await view.findAll('.source-issues__states button')[1]!.trigger('click');
  await flushPromises();
  await button(view, 'Reopen').trigger('click');
  await flushPromises();
  expect(api.targets).toHaveBeenCalledExactlyOnceWith({ sourceId: 1, state: 'ACKNOWLEDGED' });
  expect(api.reopen).toHaveBeenCalledTimes(1);
  expect(api.reopen.mock.calls[0]![0].sourceIssueMutationRequest.targets).toHaveLength(51);
  expect(rows.every((i) => i.state === 'PENDING')).toBe(true);
  view.unmount();
});
it('loads active findings beyond the first hundred before offering the whole-file action', async () => {
  rows = [row(), ...Array.from({ length: 100 }, (_, i) => row(i + 2, 1, 'INFORMATION'))];
  const view = mount(SourceIssueStatus, { props: { sourceId: 1 } });
  await flushPromises();
  expect(api.issues.mock.calls.map(([r]) => r.offset)).toEqual([0, 100]);
  expect(view.text()).toContain('1 open issue');
  await button(view, 'Acknowledge').trigger('click');
  await flushPromises();
  expect(api.acknowledge.mock.calls[0]![0].sourceIssueMutationRequest.targets).toEqual([target(rows[0]!)]);
  view.unmount();
});
it('refreshes the opened file after reassessment discovers a new issue', async () => {
  rows = [];
  const view = await openFirst();
  expect(view.findAll('button').some((b) => b.text().startsWith('Acknowledge'))).toBe(false);
  api.assess.mockImplementation(async () => {
    rows = [row()];
    return {
      ...detail(1),
      diagnosis: { findings: [{ reason: 'POSITION_JUMP', severity: 'REVIEW', message: 'New position problem.' }] },
    };
  });
  await button(view, 'Reassess current file').trigger('click');
  await flushPromises();
  expect(view.text()).toContain('New position problem.');
  expect(button(view, 'Acknowledge & next').exists()).toBe(true);
  view.unmount();
});
it.each(['PENDING', 'ACKNOWLEDGED'])(
  'reassesses an unreadable source with an old diagnosis and restores its %s action',
  async (state) => {
    rows = [row(1, 1, state), row(4, 1, state, 'unreadable:episode')];
    const view = await openFirst();
    await flushPromises();
    const label = state === 'PENDING' ? 'Acknowledge & next' : 'Reopen';
    expect(button(view, label).attributes('disabled')).toBeDefined();
    expect(view.text()).toContain('Reassess its diagnosis before acknowledging.');
    // The automatic reload still returns the old stored hash. It must not reassess on its own.
    expect(api.detail.mock.calls.length).toBeGreaterThan(1);
    expect(api.assess).not.toHaveBeenCalled();
    const currentDetail = {
      ...detail(1),
      source: { ...sources[0], sourceHash: 'unreadable' },
      diagnosis: {
        findings: [{ reason: 'SOURCE_UNREADABLE', severity: 'REVIEW', message: 'The current file is unreadable.' }],
      },
    };
    api.assess.mockResolvedValue(currentDetail);
    api.detail.mockImplementation(async ({ sourceId }) => (sourceId === 1 ? currentDetail : detail(sourceId)));
    await button(view, 'Reassess diagnosis').trigger('click');
    await flushPromises();
    expect(api.assess).toHaveBeenCalledExactlyOnceWith({ sourceId: 1 });
    expect(view.text()).toContain('The current file is unreadable.');
    expect(view.text()).not.toContain('Reassess its diagnosis before acknowledging.');
    expect(button(view, label).attributes('disabled')).toBeUndefined();
    const displayedTargets = rows.map(target);
    await button(view, label).trigger('click');
    await flushPromises();
    const mutation = state === 'PENDING' ? api.acknowledge : api.reopen;
    expect(mutation).toHaveBeenCalledTimes(1);
    const savedTargets = mutation.mock.calls[0]![0].sourceIssueMutationRequest.targets;
    expect(savedTargets).toHaveLength(displayedTargets.length);
    expect(savedTargets).toEqual(expect.arrayContaining(displayedTargets));
    expect(rows.every((issue) => issue.state === (state === 'PENDING' ? 'ACKNOWLEDGED' : 'PENDING'))).toBe(true);
    view.unmount();
  }
);
it('keeps the unreadable-source action blocked and reassessment retryable after a failed reassessment', async () => {
  rows = [row(4, 1, 'PENDING', 'unreadable:episode')];
  const view = await openFirst();
  await flushPromises();
  api.assess.mockRejectedValue(new Error('Reassessment failed.'));
  await button(view, 'Reassess diagnosis').trigger('click');
  await flushPromises();
  expect(view.text()).toContain('Reassessment failed.');
  expect(button(view, 'Acknowledge & next').attributes('disabled')).toBeDefined();
  expect(button(view, 'Reassess diagnosis').attributes('disabled')).toBeUndefined();
  expect(api.assess).toHaveBeenCalledTimes(1);
  expect(api.acknowledge).not.toHaveBeenCalled();
  expect(rows[0]!.state).toBe('PENDING');
  view.unmount();
});
it('refreshes the status when a poll discovers or resolves an issue of the same source', async () => {
  rows = [];
  const status = useApplicationStatusStore();
  status.processing = { indexers: [{ index: 'GPS', pendingIssueFileCount: 0, acknowledgedIssueFileCount: 0 }] };
  const view = mount(SourceIssueStatus, { props: { sourceId: 1 } });
  await flushPromises();
  rows = [row()];
  status.processing = { indexers: [{ index: 'GPS', pendingIssueFileCount: 1, acknowledgedIssueFileCount: 0 }] };
  await flushPromises();
  expect(view.text()).toContain('1 open issue');
  rows = [];
  status.processing = { indexers: [{ index: 'GPS', pendingIssueFileCount: 0, acknowledgedIssueFileCount: 0 }] };
  await flushPromises();
  expect(view.findAll('button')).toHaveLength(0);
  view.unmount();
});
it('disables paging during a write and ignores its delayed result after an external navigation', async () => {
  let finish!: () => void;
  api.acknowledge.mockImplementation(
    (request) =>
      new Promise((resolve) => {
        finish = () => resolve(acknowledge(request));
      })
  );
  const view = await openFirst();
  await button(view, 'Acknowledge & next').trigger('click');
  await flushPromises();
  expect(view.get('[aria-label="Next file"]').attributes('disabled')).toBeDefined();
  navigation.route.params = { sourceId: '2' };
  await flushPromises();
  expect(view.get('h2').text()).toBe('file-2.gpx');
  finish();
  await flushPromises();
  expect(view.get('h2').text()).toBe('file-2.gpx');
  expect(rows.find((i) => i.sourceId === 2)!.state).toBe('PENDING');
  expect(api.acknowledge.mock.calls[0]![0].sourceIssueMutationRequest.targets).toEqual([
    { issueId: 1, sourceRevision: 'sha256:a' },
  ]);
  view.unmount();
});
it('advances only after the current file has no pending issues', async () => {
  const view = await openFirst();
  await button(view, 'Acknowledge & next').trigger('click');
  await flushPromises();
  expect(view.get('h2').text()).toBe('file-2.gpx');
  expect(rows[0]!.state).toBe('ACKNOWLEDGED');
  view.unmount();
});
it('retains the current file and feedback when one saved target becomes stale', async () => {
  rows.push(row(11, 1));
  let finish!: () => void;
  api.acknowledge.mockImplementation(
    (request) =>
      new Promise((resolve) => {
        finish = () => {
          rows = rows.filter((i) => i.issueId !== 11);
          rows.push(row(21, 1));
          resolve(acknowledge(request));
        };
      })
  );
  const view = await openFirst();
  await button(view, 'Acknowledge & next').trigger('click');
  await flushPromises();
  finish();
  await flushPromises();
  expect(view.get('h2').text()).toBe('file-1.gpx');
  expect(view.text()).toContain('1 changed issue left open.');
  expect(view.text()).toContain('1 open issue');
  view.unmount();
});
it('keeps failed Undo retryable after its expiry time and expires successful feedback', async () => {
  vi.useFakeTimers();
  rows = [row()];
  const store = useSourceIssueStore();
  await store.save([target(rows[0]!)]);
  api.reopen.mockRejectedValueOnce(new Error('Undo could not be saved.'));
  await store.undo();
  await vi.advanceTimersByTimeAsync(20_000);
  expect(store.undoTargets).toHaveLength(1);
  expect(store.feedbackError).toBe('Undo could not be saved.');
  expect(rows[0]!.state).toBe('ACKNOWLEDGED');
  await store.undo();
  expect(rows[0]!.state).toBe('PENDING');
  expect(store.feedbackMessage).toBe('Acknowledgement undone.');
  await vi.advanceTimersByTimeAsync(8_000);
  expect(store.feedbackMessage).toBe('');
  expect(store.undoTargets).toHaveLength(0);
});
it('clears prepared selections and persistent Undo when sign-in changes', async () => {
  rows = [row()];
  const view = mount(SourceIssueAction);
  await button(view, 'Acknowledge all').trigger('click');
  await flushPromises();
  expect(button(view, 'Confirm acknowledgement').exists()).toBe(true);
  window.dispatchEvent(new Event(DIAGNOSTIC_AUTH_CHANGED));
  await flushPromises();
  expect(view.findAll('button').some((b) => b.text() === 'Confirm acknowledgement')).toBe(false);
  const store = useSourceIssueStore();
  await store.save([target(rows[0]!)]);
  window.dispatchEvent(new Event(DIAGNOSTIC_AUTH_CHANGED));
  expect(store.undoTargets).toHaveLength(0);
  expect(store.feedbackMessage).toBe('');
  view.unmount();
});

it('blocks a newly polled source revision until its displayed diagnosis is refreshed', async () => {
  rows = [row(1, 1, 'ACKNOWLEDGED')];
  const status = useApplicationStatusStore();
  status.processing = { indexers: [{ index: 'GPS', pendingIssueFileCount: 0, acknowledgedIssueFileCount: 1 }] };
  const view = mount(SourceIssueStatus, { props: { sourceId: 1, sourceHash: 'a', refreshVersion: 1 } });
  await flushPromises();
  rows = [row(9, 1, 'PENDING', 'sha256:b')];
  status.processing = { indexers: [{ index: 'GPS', pendingIssueFileCount: 1, acknowledgedIssueFileCount: 0 }] };
  await flushPromises();
  expect(view.text()).toContain('The file or its findings changed.');
  expect(view.emitted('refresh')).toEqual([[1]]);
  expect(button(view, 'Acknowledge').attributes('disabled')).toBeDefined();
  await button(view, 'Acknowledge').trigger('click');
  expect(api.acknowledge).not.toHaveBeenCalled();
  // A stale detail response cannot trigger an automatic refresh loop or unlock the replacement.
  await view.setProps({ refreshVersion: 2 });
  await flushPromises();
  expect(view.emitted('refresh')).toHaveLength(1);
  expect(button(view, 'Acknowledge').attributes('disabled')).toBeDefined();
  await view.setProps({ sourceHash: 'b', refreshVersion: 3 });
  await flushPromises();
  expect(button(view, 'Acknowledge').attributes('disabled')).toBeUndefined();
  await button(view, 'Acknowledge').trigger('click');
  await flushPromises();
  expect(api.acknowledge).toHaveBeenCalledExactlyOnceWith({
    sourceIssueMutationRequest: { targets: [{ issueId: 9, sourceRevision: 'sha256:b' }] },
  });
  view.unmount();
});

it.each(['sha256:a', 'unreadable:second-episode'])(
  'requires refreshed diagnosis for new semantic findings or an unreadable episode (%s)',
  async (replacement) => {
    const unreadable = replacement.startsWith('unreadable:');
    rows = [row(1, 1, 'ACKNOWLEDGED', unreadable ? 'unreadable:first-episode' : 'sha256:a')];
    const status = useApplicationStatusStore();
    status.processing = { indexers: [{ index: 'GPS', pendingIssueFileCount: 0, acknowledgedIssueFileCount: 1 }] };
    const view = mount(SourceIssueStatus, {
      props: { sourceId: 1, sourceHash: unreadable ? 'unreadable' : 'a', refreshVersion: 1 },
    });
    await flushPromises();
    rows = [row(9, 1, 'PENDING', replacement)];
    status.processing = { indexers: [{ index: 'GPS', pendingIssueFileCount: 1, acknowledgedIssueFileCount: 0 }] };
    await flushPromises();
    expect(button(view, 'Acknowledge').attributes('disabled')).toBeDefined();
    expect(view.emitted('refresh')).toEqual([[1]]);
    expect(api.acknowledge).not.toHaveBeenCalled();
    await view.setProps({ refreshVersion: 2 });
    await flushPromises();
    expect(button(view, 'Acknowledge').attributes('disabled')).toBeUndefined();
    view.unmount();
  }
);

it('refreshes an entirely stale open-file selection without advancing or hiding its replacement', async () => {
  let finish!: () => void;
  api.acknowledge.mockImplementation(
    (request) =>
      new Promise((resolve) => {
        finish = () => {
          rows = rows.filter((issue) => issue.sourceId !== 1);
          rows.push(row(9, 1, 'PENDING', 'sha256:b'));
          api.detail.mockImplementation(async ({ sourceId }) =>
            sourceId === 1
              ? {
                  ...detail(1),
                  source: { ...sources[0], sourceHash: 'b' },
                  diagnosis: {
                    findings: [
                      { reason: 'POSITION_JUMP', severity: 'REVIEW', message: 'Replacement position problem.' },
                    ],
                  },
                }
              : detail(sourceId)
          );
          resolve(acknowledge(request));
        };
      })
  );
  const view = await openFirst();
  await button(view, 'Acknowledge & next').trigger('click');
  await flushPromises();
  finish();
  await flushPromises();
  expect(view.get('h2').text()).toBe('file-1.gpx');
  expect(view.text()).toContain('These issues changed or were resolved.');
  expect(view.text()).toContain('Replacement position problem.');
  expect(view.text()).toContain('1 open issue');
  expect(rows.find((issue) => issue.issueId === 9)!.state).toBe('PENDING');
  view.unmount();
});

it('joins a superseding same-file verification read and advances exactly once when the latest read is clean', async () => {
  const view = await openFirst();
  const readIssues = api.issues.getMockImplementation()!;
  let completeDiagnosis!: (value: ReturnType<typeof detail>) => void;
  api.detail.mockImplementation(({ sourceId }) =>
    sourceId === 1
      ? new Promise((resolve) => {
          completeDiagnosis = resolve;
        })
      : Promise.resolve(detail(sourceId))
  );
  let verificationReads = 0;
  let completeOriginal!: (value: unknown) => void;
  let completeLatest!: (value: unknown) => void;
  api.issues.mockImplementation((request) => {
    if (request.sourceId === 1 && rows[0]!.state === 'ACKNOWLEDGED') {
      verificationReads++;
      return new Promise((resolve) => {
        if (verificationReads === 1) completeOriginal = resolve;
        else completeLatest = resolve;
      });
    }
    return readIssues(request);
  });
  await button(view, 'Acknowledge & next').trigger('click');
  await flushPromises();
  expect(verificationReads).toBe(1);
  completeDiagnosis(detail(1));
  await flushPromises();
  expect(verificationReads).toBe(2);
  const clean = await readIssues({ sourceId: 1, offset: 0, limit: 100 });
  completeOriginal(clean);
  await flushPromises();
  expect(view.get('h2').text()).toBe('file-1.gpx');
  expect(view.findComponent(SourceIssueStatus).emitted('acknowledged')).toBeUndefined();
  completeLatest(clean);
  await flushPromises();
  expect(view.get('h2').text()).toBe('file-2.gpx');
  expect(view.findComponent(SourceIssueStatus).emitted('acknowledged')).toEqual([[1]]);
  expect(api.acknowledge).toHaveBeenCalledTimes(1);
  view.unmount();
});

it('does not override browser navigation while the next file diagnosis is still loading', async () => {
  const view = await openFirst();
  const readIssues = api.issues.getMockImplementation()!;
  let completeVerification!: (value: unknown) => void;
  api.issues.mockImplementation((request) => {
    if (request.sourceId === 1 && rows[0]!.state === 'ACKNOWLEDGED') {
      return new Promise((resolve) => {
        completeVerification = resolve;
      });
    }
    return readIssues(request);
  });
  let completeDestination!: (value: ReturnType<typeof detail>) => void;
  api.detail.mockImplementation(({ sourceId }) =>
    sourceId === 3
      ? new Promise((resolve) => {
          completeDestination = resolve;
        })
      : Promise.resolve(detail(sourceId))
  );
  await button(view, 'Acknowledge & next').trigger('click');
  await flushPromises();
  navigation.route.params.sourceId = '3';
  await flushPromises();
  const requestedNavigation = [...navigation.push.mock.calls];
  expect(view.get('h2').text()).toBe('file-1.gpx');
  completeVerification(await readIssues({ sourceId: 1, offset: 0, limit: 100 }));
  await flushPromises();
  expect(navigation.push.mock.calls).toEqual(requestedNavigation);
  completeDestination(detail(3));
  await flushPromises();
  expect(view.get('h2').text()).toBe('file-3.gpx');
  expect(rows.find((issue) => issue.sourceId === 2)!.state).toBe('PENDING');
  view.unmount();
});

it.each(['initial load', 'filter change'])('does not claim issues are clear after a failed %s', async (stage) => {
  if (stage === 'initial load') api.issues.mockRejectedValueOnce(new Error('Issues could not be loaded.'));
  const view = mount(SourceIssueReview);
  await flushPromises();
  if (stage === 'filter change') {
    expect(view.findAll('.source-issues__file')).toHaveLength(3);
    api.issues.mockRejectedValueOnce(new Error('Issues could not be loaded.'));
    await view.findAll('.source-issues__states button')[1]!.trigger('click');
    await flushPromises();
  }
  expect(view.text()).toContain('Issues could not be loaded.');
  expect(view.find('.source-issues__empty').exists()).toBe(false);
  expect(view.findAll('.source-issues__file')).toHaveLength(0);
  expect(view.text()).not.toContain('Nothing to review');
  expect(view.text()).not.toContain('No issues are waiting for review.');
  await button(view, 'Refresh').trigger('click');
  await flushPromises();
  expect(view.find('[role="alert"]').exists()).toBe(false);
  view.unmount();
});

it('shows only index-scoped counts and offers no bulk action for an empty index', async () => {
  const librarySummary = {
    pendingFileCount: 4,
    acknowledgedFileCount: 3,
    indexes: [{ index: 'GPS', pendingFileCount: 4, acknowledgedFileCount: 3 }],
  };
  api.issues.mockResolvedValue({ total: 0, issues: [], summary: librarySummary });
  const view = mount(SourceIssueReview, { props: { index: 'MEDIA' } });
  await flushPromises();
  expect(view.text()).toContain('Showing media files only');
  expect(view.findAll('.source-issues__count')).toHaveLength(0);
  expect(view.find('.source-issues__bulk').exists()).toBe(false);
  const pendingFiles = [1, 2, 3, 4].map((id) => row(id, id));
  api.issues.mockResolvedValue({ total: 4, issues: pendingFiles, summary: librarySummary });
  await view.setProps({ index: 'GPS' });
  await flushPromises();
  expect(view.findAll('.source-issues__count').map((count) => count.text())).toEqual(['4', '3']);
  expect(view.find('.source-issues__bulk').exists()).toBe(true);
  view.unmount();
});

it('does not attach whole-library badges or bulk actions to an empty single-file view', async () => {
  const view = mount(SourceIssueReview, { props: { sourceId: 42 } });
  await flushPromises();
  expect(view.text()).toContain('Showing one file only');
  expect(view.findAll('.source-issues__count')).toHaveLength(0);
  expect(view.find('.source-issues__bulk').exists()).toBe(false);
  view.unmount();
});

it('offers a labelled Retry Undo after a failed Undo and completes the retry', async () => {
  const store = useSourceIssueStore();
  await store.save([target(rows[0]!)]);
  const view = mount(SourceIssueFeedback);
  api.reopen.mockRejectedValueOnce(new Error('Undo could not be saved.'));
  await button(view, 'Undo').trigger('click');
  await flushPromises();
  expect(view.text()).toContain('Undo could not be saved.');
  expect(button(view, 'Retry Undo').exists()).toBe(true);
  expect(rows[0]!.state).toBe('ACKNOWLEDGED');
  await button(view, 'Retry Undo').trigger('click');
  await flushPromises();
  expect(view.text()).toContain('Acknowledgement undone.');
  expect(view.findAll('button').some((control) => control.text() === 'Retry Undo')).toBe(false);
  expect(rows[0]!.state).toBe('PENDING');
  view.unmount();
});

it('opens all source issues when returning through a plain Review link after using GPS recordings', async () => {
  rows = [{ ...row(8, 8), index: 'MEDIA', fileName: 'broken-photo.jpg' }];
  api.queue.mockResolvedValue({ sources: [], needsReviewCount: 0 });
  const view = mountOpenTool();
  await flushPromises();
  expect(view.find('.review-recordings').exists()).toBe(true);
  navigation.route.name = 'filter';
  navigation.route.query = {};
  await flushPromises();
  navigation.route.name = 'review';
  await flushPromises();
  expect(view.find('.review-recordings').exists()).toBe(false);
  expect(view.findComponent(SourceIssueReview).exists()).toBe(true);
  expect(view.text()).toContain('broken-photo.jpg');
  view.unmount();
});

it('preserves the GPS view when returning from its detail within Review', async () => {
  const view = await openFirst();
  navigation.route.params = {};
  navigation.route.query = {};
  await flushPromises();
  expect(view.find('.review-recordings').exists()).toBe(true);
  expect(view.findComponent(SourceIssueReview).exists()).toBe(false);
  expect(view.findAll('.review-sources button')).toHaveLength(3);
  view.unmount();
});

it('keeps the GPS scope through opening a file, acknowledging next, and returning to its queue', async () => {
  navigation.route.query = { index: 'GPS' };
  rows.push({ ...row(8, 8), index: 'MEDIA', fileName: 'unrelated-photo.jpg' });
  const view = mountOpenTool();
  await flushPromises();
  expect(view.findAll('.source-issues__file')).toHaveLength(3);
  expect(view.text()).not.toContain('unrelated-photo.jpg');
  await button(view, 'Open').trigger('click');
  await flushPromises();
  expect(view.get('h2').text()).toBe('file-3.gpx');
  expect(navigation.push.mock.lastCall?.[0].query).toEqual({ index: 'GPS' });
  await button(view, 'Acknowledge & next').trigger('click');
  await flushPromises();
  expect(view.get('h2').text()).toBe('file-2.gpx');
  expect(navigation.route.query).toEqual({ index: 'GPS' });
  await view.get('[data-test="review-back"]').trigger('click');
  await flushPromises();
  expect(navigation.push.mock.lastCall?.[0].query).toEqual({ index: 'GPS' });
  expect(view.findComponent(SourceIssueReview).props('index')).toBe('GPS');
  expect(view.findAll('.source-issues__file')).toHaveLength(2);
  expect(view.text()).not.toContain('unrelated-photo.jpg');
  view.unmount();
});

it('keeps a single-file issue scope when opening its GPS diagnosis and returning', async () => {
  navigation.route.query = { source: '2', view: 'issues' };
  const view = mountOpenTool();
  await flushPromises();
  await button(view, 'Open').trigger('click');
  await flushPromises();
  expect(view.get('h2').text()).toBe('file-2.gpx');
  expect(navigation.route.query).toEqual({ source: '2', view: 'issues' });
  await view.get('[data-test="review-back"]').trigger('click');
  await flushPromises();
  expect(view.findComponent(SourceIssueReview).props('sourceId')).toBe(2);
  expect(view.findAll('.source-issues__file')).toHaveLength(1);
  expect(view.text()).toContain('file-2.gpx');
  view.unmount();
});

it('keeps an uploaded-file query and its recording filter when opening and returning', async () => {
  navigation.route.query = { file: 'file-1.gpx' };
  const view = mountOpenTool();
  await flushPromises();
  await view.findAll('.review-sources button')[0]!.trigger('click');
  await flushPromises();
  expect(navigation.route.query).toEqual({ file: 'file-1.gpx' });
  await view.get('[data-test="review-back"]').trigger('click');
  await flushPromises();
  expect(navigation.route.query).toEqual({ file: 'file-1.gpx' });
  expect(view.find('.review-recordings').exists()).toBe(true);
  expect(view.get<HTMLInputElement>('input[type="search"]').element.value).toBe('file-1.gpx');
  view.unmount();
});

it.each([
  ['overall', {}],
  ['one-file', { source: '8' }],
])('resets Information on a fresh %s entry and preserves shared feedback', async (_entry, query) => {
  navigation.route.query = {};
  rows = [row(1, 1), { ...row(8, 8), index: 'MEDIA', fileName: 'pending-photo.jpg' }];
  const view = mountOpenTool();
  await flushPromises();
  await view.findAll('.source-issues__states button')[2]!.trigger('click');
  await flushPromises();
  expect(view.findAll('.source-issues__states button')[2]!.attributes('aria-pressed')).toBe('true');
  view.vm.close();
  await flushPromises();
  expect(view.findComponent(SourceIssueReview).exists()).toBe(false);
  const readsWhileClosed = api.issues.mock.calls.length;
  await useSourceIssueStore().save([target(rows[0]!)]);
  await flushPromises();
  expect(api.issues.mock.calls).toHaveLength(readsWhileClosed);
  navigation.route.name = 'admin';
  navigation.route.query = {};
  await flushPromises();
  navigation.route.name = 'review';
  navigation.route.query = query;
  view.vm.open();
  await flushPromises();
  expect(view.findAll('.source-issues__states button')[0]!.attributes('aria-pressed')).toBe('true');
  expect(view.text()).toContain('pending-photo.jpg');
  expect(view.text()).toContain('Acknowledged 1 issue.');
  expect(button(view, 'Undo').exists()).toBe(true);
  view.unmount();
});

it('offers one direct acknowledgement when a single-file scope fits on one page', async () => {
  rows = [row(1, 1)];
  const view = mount(SourceIssueReview, { props: { sourceId: 1 } });
  await flushPromises();
  expect(view.find('.source-issues__bulk').exists()).toBe(false);
  expect(view.findAll('button').filter((control) => control.text() === 'Acknowledge')).toHaveLength(1);
  await button(view, 'Acknowledge').trigger('click');
  await flushPromises();
  expect(api.targets).not.toHaveBeenCalled();
  expect(api.acknowledge).toHaveBeenCalledTimes(1);
  view.unmount();
});

it('retains the full-file snapshot action for a single-file scope spanning issue pages', async () => {
  rows = Array.from({ length: 51 }, (_, index) => row(index + 1, 1));
  const view = mount(SourceIssueReview, { props: { sourceId: 1 } });
  await flushPromises();
  expect(view.find('.source-issues__bulk').exists()).toBe(true);
  expect(button(view, 'Acknowledge shown issues').exists()).toBe(true);
  await button(view, 'Acknowledge file').trigger('click');
  await flushPromises();
  expect(view.text()).toContain('1 file · 51 issues selected');
  await button(view, 'Confirm acknowledgement').trigger('click');
  await flushPromises();
  expect(api.acknowledge).toHaveBeenCalledTimes(1);
  expect(api.acknowledge.mock.calls[0]![0].sourceIssueMutationRequest.targets).toHaveLength(51);
  expect(rows.every((issue) => issue.state === 'ACKNOWLEDGED')).toBe(true);
  view.unmount();
});
