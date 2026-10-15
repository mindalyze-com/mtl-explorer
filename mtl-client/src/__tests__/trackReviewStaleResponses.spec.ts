import { createPinia, setActivePinia } from 'pinia';
import { flushPromises, mount } from '@vue/test-utils';
import { reactive } from 'vue';
import { beforeEach, expect, it, vi } from 'vitest';
import { GetTrackReviewQueueStateEnum as State, type SourceReviewDetail } from 'x8ing-mtl-api-typescript-fetch';
import TrackReviewTool from '@/features/track-review/TrackReviewTool.vue';

// These suites exercise normal-installation review actions.
vi.mock('@/stores/demoAccessStore', () => ({
  useDemoAccessStore: () => ({ demoMode: false, writeAllowed: true, load: vi.fn() }),
  SHARED_DEMO_READ_ONLY_MESSAGE: 'Import and review changes are unavailable in this shared demo.',
}));

vi.mock('@/utils/openApiClient', () => ({ getApiConfiguration: () => ({}) }));
vi.mock('@/router', () => ({ default: { push: vi.fn() } }));
const api = vi.hoisted(() => ({
  getTrackReviewQueue: vi.fn(),
  getSourceReview: vi.fn(),
  assessTrackSource: vi.fn(),
  decideTrackSourceReview: vi.fn(),
}));
const navigation = vi.hoisted(() => ({
  route: { name: 'review', params: {} as { sourceId?: string }, query: {} },
  push: vi.fn(),
}));
vi.mock('@/features/track-review/api', () => ({
  reviewApi: () => api,
  stateLabel: (value: string) => value,
  reasonLabel: (value: string) => value,
  decisionLabel: (value: string) => value,
  reviewError: async (error: unknown) => String(error),
  saveDownload: vi.fn(),
}));
vi.mock('vue-router', () => ({
  useRoute: () => navigation.route,
  useRouter: () => ({ push: navigation.push }),
  RouterLink: { template: '<a><slot /></a>' },
}));
vi.mock('@/components/ui/BottomSheet.vue', () => ({
  default: {
    emits: ['back'],
    template: '<div><button data-test="back" @click="$emit(\'back\')">Back</button><slot /></div>',
  },
}));
vi.mock('@/features/track-review/SourceIssueReview.vue', () => ({ default: { template: '<div />' } }));
vi.mock('@/features/track-review/SourceIssueStatus.vue', () => ({ default: { template: '<div />' } }));
vi.mock('@/features/track-review/ReviewMap.vue', () => ({ default: { template: '<div />' } }));
vi.mock('@/features/track-review/SourcePathComparison.vue', () => ({ default: { template: '<div />' } }));

function source(id: number): SourceReviewDetail {
  return {
    source: { sourceId: id, sourceHash: `revision-${id}`, fileName: `source-${id}.gpx`, state: State.NeedsReview },
    totalPointCount: 60,
    tracks: [{ trackId: id }],
    sections: [],
    diagnosis: { findings: [], sections: [] },
  };
}
function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: Error) => void;
  const promise = new Promise<T>((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}
beforeEach(() => {
  setActivePinia(createPinia());
  vi.resetAllMocks();
  navigation.route = reactive({ name: 'review', params: {}, query: { view: 'recordings' } });
  navigation.push.mockImplementation((target) => {
    navigation.route.params = target.params ?? {};
    return Promise.resolve();
  });
  api.getTrackReviewQueue.mockResolvedValue({ sources: [source(1).source, source(2).source], needsReviewCount: 2 });
  api.getSourceReview.mockImplementation(({ sourceId }) => Promise.resolve(source(Number(sourceId))));
});
async function openFirst() {
  const wrapper = mount(TrackReviewTool, {
    global: {
      stubs: {
        Button: {
          props: ['label', 'disabled', 'loading'],
          emits: ['click'],
          template: '<button :disabled="disabled || loading" @click="$emit(\'click\')">{{ label }}</button>',
        },
      },
    },
  });
  await flushPromises();
  await wrapper.get('.review-sources button').trigger('click');
  await flushPromises();
  expect(wrapper.get('h2').text()).toBe('source-1.gpx');
  return wrapper;
}
type Wrapper = Awaited<ReturnType<typeof openFirst>>;
async function startAction(wrapper: Wrapper, action: 'assess' | 'decide') {
  if (action === 'assess') {
    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Reassess current file')!
      .trigger('click');
  } else {
    await wrapper.get('.source-usage input[value="INCLUDE"]').setValue();
    await wrapper.get('.source-usage').trigger('submit');
  }
}
async function selectSecond(wrapper: Wrapper) {
  await wrapper.get('[data-test=back]').trigger('click');
  await flushPromises();
  await wrapper.findAll('.review-sources button')[1]!.trigger('click');
  await flushPromises();
  expect(Number(navigation.route.params.sourceId)).toBe(2);
}

it.each(['assess', 'decide'] as const)(
  'ignores a delayed %s response after selecting another source',
  async (action) => {
    const pending = deferred<SourceReviewDetail>();
    api[action === 'assess' ? 'assessTrackSource' : 'decideTrackSourceReview'].mockReturnValueOnce(pending.promise);
    const wrapper = await openFirst();
    await startAction(wrapper, action);
    await selectSecond(wrapper);
    pending.resolve(source(1));
    await flushPromises();
    expect(wrapper.get('h2').text()).toBe('source-2.gpx');
    expect(Number(navigation.route.params.sourceId)).toBe(2);
    expect(wrapper.text()).not.toContain('Review decision saved');
    expect(wrapper.emitted('data-changed')).toEqual([[]]);
    // The next write must target the visible source.
    api.decideTrackSourceReview.mockResolvedValueOnce(source(2));
    await startAction(wrapper, 'decide');
    await flushPromises();
    expect(api.decideTrackSourceReview.mock.lastCall?.[0]).toMatchObject({ sourceId: 2 });
    wrapper.unmount();
  }
);

it.each(['assess', 'decide'] as const)('keeps a delayed %s error out of the new selection', async (action) => {
  const pending = deferred<SourceReviewDetail>();
  api[action === 'assess' ? 'assessTrackSource' : 'decideTrackSourceReview'].mockReturnValueOnce(pending.promise);
  const wrapper = await openFirst();
  await startAction(wrapper, action);
  await selectSecond(wrapper);
  pending.reject(new Error('Old source failed'));
  await flushPromises();
  expect(wrapper.get('h2').text()).toBe('source-2.gpx');
  expect(wrapper.find('[role=alert]').exists()).toBe(false);
  wrapper.unmount();
});

it('does not clear the busy state of a newer selection request', async () => {
  const pending = deferred<SourceReviewDetail>();
  const second = deferred<SourceReviewDetail>();
  api.assessTrackSource.mockReturnValueOnce(pending.promise);
  const wrapper = await openFirst();
  await startAction(wrapper, 'assess');
  api.getSourceReview.mockReturnValue(second.promise);
  navigation.route.params = { sourceId: '2' };
  await flushPromises();
  pending.resolve(source(1));
  await flushPromises();
  expect(
    wrapper
      .findAll('button')
      .find((button) => button.text() === 'Reassess current file')!
      .attributes('disabled')
  ).toBeDefined();
  second.resolve(source(2));
  await flushPromises();
  expect(wrapper.get('h2').text()).toBe('source-2.gpx');
  wrapper.unmount();
});

it('ignores an old response even after navigating away and back to the same source', async () => {
  const pending = deferred<SourceReviewDetail>();
  api.assessTrackSource.mockReturnValueOnce(pending.promise);
  const wrapper = await openFirst();
  await startAction(wrapper, 'assess');
  await selectSecond(wrapper);
  navigation.route.params = { sourceId: '1' };
  await flushPromises();
  pending.resolve({ ...source(1), source: { ...source(1).source, fileName: 'obsolete-response.gpx' } });
  await flushPromises();
  expect(wrapper.get('h2').text()).toBe('source-1.gpx');
  wrapper.unmount();
});
