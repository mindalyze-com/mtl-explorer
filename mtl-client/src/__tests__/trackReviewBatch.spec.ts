import { createPinia, setActivePinia } from 'pinia';
import { flushPromises, mount } from '@vue/test-utils';
import { reactive } from 'vue';
import { beforeEach, expect, it, vi } from 'vitest';
import { GetTrackReviewQueueStateEnum as State } from 'x8ing-mtl-api-typescript-fetch';
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
  assessTrackSource: vi.fn(),
  getSourceReview: vi.fn(),
  decideTrackSourceReview: vi.fn(),
}));
const navigation = vi.hoisted(() => ({
  route: { name: 'review', params: {}, query: {} as Record<string, string> },
  push: vi.fn(),
}));
vi.mock('@/features/track-review/api', () => ({
  reviewApi: () => api,
  stateLabel: (state: string) => state,
  reasonLabel: (reason: string) => reason,
  decisionLabel: (decision: string) => decision,
  reviewError: async (error: unknown) => String(error),
  saveDownload: vi.fn(),
}));
vi.mock('vue-router', () => ({
  useRoute: () => navigation.route,
  useRouter: () => ({ push: navigation.push }),
  RouterLink: { template: '<a><slot /></a>' },
}));
vi.mock('@/components/ui/BottomSheet.vue', () => ({ default: { template: '<div><slot /></div>' } }));
vi.mock('@/features/track-review/SourceIssueReview.vue', () => ({ default: { template: '<div />' } }));
vi.mock('@/features/track-review/SourceIssueStatus.vue', () => ({
  default: {
    props: ['hasNext', 'sourceId'],
    emits: ['acknowledged'],
    template:
      '<button data-test="issue-status" :data-has-next="String(hasNext)" @click="$emit(\'acknowledged\', sourceId)" />',
  },
}));
vi.mock('@/features/track-review/ReviewMap.vue', () => ({ default: { template: '<div />' } }));
vi.mock('@/features/track-review/SourcePathComparison.vue', () => ({ default: { template: '<div />' } }));

beforeEach(() => {
  setActivePinia(createPinia());
  vi.clearAllMocks();
  navigation.route = reactive({ name: 'review', params: {}, query: { view: 'recordings' } });
});

it('finds an uploaded file across all states, including nonblocking findings', async () => {
  navigation.route.query = { file: 'uploaded-track.gpx' };
  api.getTrackReviewQueue.mockResolvedValue({ needsReviewCount: 0, sources: [] });
  const wrapper = mount(TrackReviewTool, { global: { stubs: { Button: true } } });
  await flushPromises();
  expect(api.getTrackReviewQueue.mock.lastCall?.[0]).toMatchObject({ search: 'uploaded-track.gpx', state: undefined });
  expect(wrapper.get('input[type=search]').element.value).toBe('uploaded-track.gpx');
  expect(wrapper.text()).toContain('Indexing may take a moment');
  wrapper.unmount();
});

it('shows known import evidence and section decisions when source comparison exceeds review limits', async () => {
  const detail = {
    source: {
      sourceId: 1,
      sourceHash: 'synthetic-large-revision',
      fileName: 'synthetic-large.gpx',
      state: State.NeedsReview,
      reasons: ['ASSESSMENT_LIMIT', 'TIME_ESTIMATED'],
      pointCount: 400,
    },
    totalPointCount: 400,
    tracks: [
      { trackId: 10, sectionIndex: 0 },
      { trackId: 11, sectionIndex: 1 },
    ],
    sections: [
      { sectionIndex: 0, name: 'Estimated section', pointCount: 200, state: State.NeedsReview },
      { sectionIndex: 1, name: 'Healthy section', pointCount: 200, state: State.NotAssessed },
    ],
    diagnosis: { findings: [], sections: [] },
  };
  api.getTrackReviewQueue.mockResolvedValue({ needsReviewCount: 1, sources: [detail.source] });
  api.getSourceReview.mockResolvedValue(detail);
  const wrapper = mount(TrackReviewTool, {
    global: {
      stubs: {
        Button: { props: ['label', 'disabled'], template: '<button :disabled="disabled">{{ label }}</button>' },
      },
    },
  });
  await flushPromises();
  expect(wrapper.text()).toContain('400 observations reported by import');
  await wrapper.get('.review-sources button').trigger('click');
  await flushPromises();
  expect(wrapper.text()).toContain('400 observations reported by import');
  expect(wrapper.text()).toContain('How to fix this file');
  expect(wrapper.find('input[type="file"]').exists()).toBe(false);
  expect(wrapper.findAll('.review-sections article')).toHaveLength(2);
  expect(wrapper.findAll('.review-sections .source-usage')).toHaveLength(2);
  expect(
    wrapper
      .findAll('button')
      .find((button) => button.text() === 'Download original source')
      ?.attributes('disabled')
  ).toBeUndefined();
  wrapper.unmount();
});

it('refreshes dependent views only after a decision is saved, and keeps failed decisions local', async () => {
  const detail = {
    source: { sourceId: 1, sourceHash: 'synthetic-revision', fileName: 'synthetic.gpx', state: State.NeedsReview },
    totalPointCount: 60,
    tracks: [{ trackId: 10 }],
    sections: [],
    diagnosis: { findings: [], sections: [] },
  };
  api.getTrackReviewQueue.mockResolvedValue({ needsReviewCount: 1, sources: [detail.source] });
  api.getSourceReview.mockResolvedValue(detail);
  let finish!: (value: typeof detail) => void;
  api.decideTrackSourceReview.mockImplementationOnce(
    () =>
      new Promise((resolve) => {
        finish = resolve;
      })
  );
  const wrapper = mount(TrackReviewTool, {
    global: {
      stubs: {
        Button: {
          props: ['label', 'disabled'],
          emits: ['click'],
          template: '<button :disabled="disabled" @click="$emit(\'click\')">{{ label }}</button>',
        },
      },
    },
  });
  await flushPromises();
  await wrapper.get('.review-sources button').trigger('click');
  await flushPromises();
  const include = () => wrapper.findAll('button').find((button) => button.text() === 'Save use settings')!;
  await wrapper.get('.source-usage input[value="INCLUDE"]').setValue();
  await wrapper.get('.source-usage').trigger('submit');
  expect(wrapper.emitted('data-changed')).toBeUndefined();
  expect(include().attributes('disabled')).toBeDefined();
  finish({ ...detail, source: { ...detail.source, state: State.Included } });
  await flushPromises();
  expect(wrapper.emitted('data-changed')).toEqual([[]]);
  expect(wrapper.text()).toContain('Review decision saved');
  api.decideTrackSourceReview.mockRejectedValueOnce(new Error('Source changed'));
  await wrapper.get('.source-usage').trigger('submit');
  await flushPromises();
  expect(wrapper.emitted('data-changed')).toEqual([[]]);
  expect(wrapper.text()).toContain('Source changed');
  expect(wrapper.text()).not.toContain('Review decision saved');
  wrapper.unmount();
});

it('keeps section findings visible but prevents section choices for a combined imported track', async () => {
  const detail = {
    source: { sourceId: 1, fileName: 'combined.gpx', pointCount: 180 },
    totalPointCount: 180,
    tracks: [{ trackId: 10, sectionIndex: null }],
    sections: [
      { sectionIndex: 0, name: 'First section' },
      { sectionIndex: 1, name: 'Second section' },
    ],
    diagnosis: { findings: [], sections: [] },
  };
  api.getTrackReviewQueue.mockResolvedValue({ sources: [detail.source] });
  api.getSourceReview.mockResolvedValue(detail);
  const wrapper = mount(TrackReviewTool, { global: { stubs: { Button: true } } });
  await flushPromises();
  await wrapper.get('.review-sources button').trigger('click');
  await flushPromises();
  expect(wrapper.findAll('.review-sections article')).toHaveLength(2);
  expect(wrapper.findAll('.review-sections .source-usage')).toHaveLength(0);
  expect(wrapper.findAll('.source-usage')).toHaveLength(1);
  expect(wrapper.text()).toContain('Choose settings for the whole file.');
  wrapper.unmount();
});

it('offers the original after failed conversion and does not describe unreadable observations as zero', async () => {
  const detail = {
    source: { sourceId: 1, fileName: 'failed.kml', pointCount: 0, reasons: ['CONVERSION_FAILED'] },
    totalPointCount: 0,
    canDownloadGpx: false,
    previewUnavailableReason: 'The server could not convert this source to GPX.',
  };
  api.getTrackReviewQueue.mockResolvedValue({ sources: [detail.source] });
  api.getSourceReview.mockResolvedValue(detail);
  const wrapper = mount(TrackReviewTool, {
    global: { stubs: { Button: { props: ['label'], template: '<button>{{ label }}</button>' } } },
  });
  await flushPromises();
  expect(wrapper.text()).toContain('Observation count unavailable');
  await wrapper.get('.review-sources button').trigger('click');
  await flushPromises();
  expect(wrapper.text()).toContain('Observation count unavailable');
  expect(wrapper.text()).not.toContain('0 original observations');
  expect(wrapper.text()).toContain('The server could not convert');
  expect(wrapper.text()).toContain('Download the original source and open it in a tool that supports its format.');
  expect(wrapper.findAll('button').some((button) => button.text() === 'Download GPX')).toBe(false);
  expect(wrapper.findAll('button').some((button) => button.text() === 'Download original source')).toBe(true);
  wrapper.unmount();
});

it('sends the selected finding, severity, and latest-decision filters to the server', async () => {
  api.getTrackReviewQueue.mockResolvedValue({ needsReviewCount: 2, sources: [] });
  const wrapper = mount(TrackReviewTool, { global: { stubs: { Button: true } } });
  await flushPromises();
  const selects = wrapper.findAll('select');
  await selects[0]!.setValue('');
  await selects[1]!.setValue('INCONSISTENT_TIME_DOMAIN');
  await selects[2]!.setValue('REVIEW');
  await selects[3]!.setValue('LATER');
  await flushPromises();
  expect(api.getTrackReviewQueue.mock.lastCall?.[0]).toMatchObject({
    state: undefined,
    reason: 'INCONSISTENT_TIME_DOMAIN',
    severity: 'REVIEW',
    decision: 'LATER',
    offset: 0,
  });
  expect(wrapper.text()).toContain('New problems may still need review.');
  wrapper.unmount();
});

it('finishes a batch when more than one page of files remains unassessed because of review limits', async () => {
  const sources = Array.from({ length: 52 }, (_, index) => ({ sourceId: index + 1, state: State.NotAssessed }));
  const visited = new Set<number>();
  api.getTrackReviewQueue.mockImplementation(async ({ state, offset = 0, limit = 50 }) => ({
    needsReviewCount: 0,
    excludedCount: 0,
    unassessedCount: sources.length,
    totalSourceCount: 52,
    sources: state === State.NotAssessed ? sources.slice(offset, offset + limit) : [],
  }));
  api.assessTrackSource.mockImplementation(async ({ sourceId }) => {
    if (visited.has(sourceId)) throw new Error('The same limited file was assessed twice in one batch.');
    visited.add(sourceId);
    if (sourceId === 52)
      sources.splice(
        sources.findIndex((source) => source.sourceId === sourceId),
        1
      );
    return { source: { sourceId, state: sourceId === 52 ? State.Included : State.NotAssessed } };
  });
  const wrapper = mount(TrackReviewTool, {
    global: {
      stubs: {
        Button: {
          props: ['label', 'disabled'],
          emits: ['click'],
          template: '<button :disabled="disabled" @click="$emit(\'click\')">{{ label }}</button>',
        },
      },
    },
  });
  await flushPromises();
  await wrapper
    .findAll('button')
    .find((button) => button.text() === 'Assess unassessed sources')!
    .trigger('click');
  await flushPromises();
  expect(visited.size).toBe(52);
  expect(api.assessTrackSource).toHaveBeenCalledTimes(52);
  expect(wrapper.emitted('data-changed')).toEqual([[]]);
  expect(wrapper.text()).toContain('Checked 52 source files; 51 remain unassessed because of review limits.');
  expect(wrapper.text()).not.toContain('Stop after this source');
  const offsets = api.getTrackReviewQueue.mock.calls
    .filter(([request]) => request.state === State.NotAssessed)
    .map(([request]) => request.offset);
  expect(offsets).toEqual([0, 50, 51]);
  wrapper.unmount();
});

it('clears an earlier upload search and finding filters when opening a contextual queue link', async () => {
  navigation.route.query = { file: 'uploaded-track.gpx' };
  api.getTrackReviewQueue.mockResolvedValue({ needsReviewCount: 2, sources: [] });
  const wrapper = mount(TrackReviewTool, { global: { stubs: { Button: true } } });
  await flushPromises();
  await wrapper.findAll('select')[1]!.setValue('INCONSISTENT_TIME_DOMAIN');
  await flushPromises();
  navigation.route.name = 'filter';
  navigation.route.query = {};
  await flushPromises();
  navigation.route.name = 'review';
  navigation.route.query = { state: 'NEEDS_REVIEW' };
  await flushPromises();
  expect(api.getTrackReviewQueue.mock.lastCall?.[0]).toMatchObject({
    search: '',
    state: State.NeedsReview,
    reason: undefined,
    offset: 0,
  });
  expect(wrapper.get('input[type=search]').element.value).toBe('');
  navigation.route.query = { state: 'EXCLUDED' };
  await flushPromises();
  expect(api.getTrackReviewQueue.mock.lastCall?.[0].state).toBe(State.Excluded);
  wrapper.unmount();
});

it('returns to the queue after leaving a source detail for another tool', async () => {
  const source = { sourceId: 99, fileName: 'synthetic.gpx', state: State.NeedsReview };
  api.getTrackReviewQueue.mockResolvedValue({ needsReviewCount: 1, sources: [source] });
  api.getSourceReview.mockResolvedValue({
    source,
    totalPointCount: 60,
    tracks: [],
    sections: [],
    diagnosis: { findings: [] },
  });
  const wrapper = mount(TrackReviewTool, { global: { stubs: { Button: true } } });
  await flushPromises();
  await wrapper.get('.review-sources button').trigger('click');
  await flushPromises();
  expect(wrapper.text()).toContain('Map and statistics');
  navigation.route.name = 'filter';
  await flushPromises();
  navigation.route.name = 'review';
  navigation.route.query = { state: 'NEEDS_REVIEW' };
  await flushPromises();
  expect(wrapper.find('.review-sources').exists()).toBe(true);
  expect(wrapper.text()).not.toContain('Map and statistics');
  wrapper.unmount();
});

it('opens the issue list by default and moves through the opened list after acknowledging', async () => {
  navigation.route.query = {};
  navigation.push.mockImplementation((location) => {
    navigation.route.params = location.params ?? {};
    return Promise.resolve();
  });
  const sources = [1, 2, 3].map((id) => ({ sourceId: id, fileName: `s${id}.gpx`, state: State.NeedsReview }));
  api.getTrackReviewQueue.mockResolvedValue({ needsReviewCount: 3, sources });
  api.getSourceReview.mockImplementation(async ({ sourceId }) => ({
    source: sources[sourceId - 1],
    totalPointCount: 1,
    tracks: [],
    sections: [],
    diagnosis: { findings: [] },
  }));
  const wrapper = mount(TrackReviewTool, {
    global: {
      stubs: {
        Button: {
          props: ['label'],
          emits: ['click'],
          template: '<button @click="$emit(\'click\')">{{ label }}</button>',
        },
      },
    },
  });
  await flushPromises();
  expect(wrapper.find('.review-sources').exists()).toBe(false);
  await wrapper
    .findAll('.review-modes button')
    .find((button) => button.text() === 'GPS recordings')!
    .trigger('click');
  await wrapper.findAll('.review-sources button')[0]!.trigger('click');
  await flushPromises();
  expect(wrapper.get('.review-pager').text()).toContain('1 of 3');
  expect(wrapper.get('[data-test=issue-status]').attributes('data-has-next')).toBe('true');
  await wrapper.get('[data-test=issue-status]').trigger('click');
  await flushPromises();
  expect(wrapper.get('h2').text()).toBe('s2.gpx');
  expect(wrapper.get('.review-pager').text()).toContain('2 of 3');
  await wrapper.get('[aria-label="Previous file"]').trigger('click');
  await flushPromises();
  expect(wrapper.get('h2').text()).toBe('s1.gpx');
  wrapper.unmount();
  navigation.push.mockReset();
});
