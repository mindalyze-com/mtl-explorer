import { createPinia, setActivePinia } from 'pinia';
import { mount } from '@vue/test-utils';
import { ref } from 'vue';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import IndexerStatusTab from '@/components/admin/IndexerStatusTab.vue';
import { observeCountAnimation, processingCountKey, type CountAnimation } from '@/utils/countAnimation';

const state = vi.hoisted(() => ({
  jobs: [] as Array<Record<string, unknown>>,
  indexes: [] as Array<Record<string, unknown>>,
  updateIndexes: (_indexes: Array<Record<string, unknown>>) => {},
}));
vi.mock('@/composables/useIndexerStatus', () => ({
  useIndexerStatus: () => {
    const summaries = ref(state.indexes);
    state.updateIndexes = (indexes) => {
      summaries.value = indexes;
    };
    const animations = new Map<string, CountAnimation>();
    for (const [scope, summaries] of [
      ['indexer', state.indexes],
      ['job', state.jobs],
    ] as const) {
      for (const summary of summaries) {
        for (const [field, value] of Object.entries(summary)) {
          if (typeof value !== 'number') continue;
          const animation = observeCountAnimation(undefined, value, Date.now());
          const id = summary[scope === 'indexer' ? 'index' : 'job'] as string;
          if (animation) animations.set(processingCountKey(scope, id, field), animation);
        }
      }
    }
    return {
      summaries,
      jobSummaries: ref(state.jobs),
      operationalTasks: ref([]),
      countAnimations: ref(animations),
      lastRefreshed: ref(''),
      refresh: vi.fn(),
    };
  },
}));

describe('track job failures', () => {
  it('keeps file totals and every outcome on the latest snapshot as import counts change', async () => {
    state.jobs = [];
    const index = (completed: number, pending: number) => ({
      index: 'GPS',
      total: 15_000,
      completed,
      pending,
      failed: 3,
      skipped: 2,
      removed: 1,
      excluded: 1,
      progressPercent: 40,
    });
    state.indexes = [index(5_693, 9_300)];
    const wrapper = mount(IndexerStatusTab, {
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' }, FileIndexerFailures: true } },
    });
    try {
      for (const completed of [5_693, 5_733, 5_700]) {
        const pending = 15_000 - completed - 7;
        state.updateIndexes([index(completed, pending)]);
        await wrapper.vm.$nextTick();
        const facts = wrapper.get('.processing-facts');
        const values = Object.fromEntries(
          facts
            .findAll(':scope > div')
            .map((row) => [row.get('dt').text(), Number(row.get('dd').text().replaceAll(',', ''))])
        );
        expect(values.Completed).toBe(completed);
        expect(values.Pending).toBe(pending);
        expect(
          values.Completed + values.Pending + values.Failed + values.Skipped + values.Removed + values.Excluded
        ).toBe(values['Total files']);
        expect(wrapper.get('.processing-task__caption').text().replace(/\s+/g, ' ')).toContain(
          `${completed.toLocaleString('en-US')} of 15,000`
        );
      }
    } finally {
      wrapper.unmount();
    }
  });
  beforeEach(() => {
    setActivePinia(createPinia());
    state.indexes = [];
    state.jobs = [
      { job: 'duplicate', label: 'Duplicate Finder', total: 5, pending: 0, done: 3, failed: 2, progressPercent: 100 },
    ];
  });

  it('shows finished failures separately without a running indicator', () => {
    const wrapper = mount(IndexerStatusTab, {
      global: { stubs: { ProgressBar: true, RouterLink: { template: '<a><slot /></a>' } } },
    });
    expect(wrapper.get('.processing-task__status').text()).toBe('Needs attention');
    expect(wrapper.text()).toContain('2 failed');
    expect(wrapper.get('.processing-technical').text()).toContain('Total tracks5');
    expect(wrapper.get('.processing-facts').text()).toContain('Completed3');
    expect(wrapper.get('.processing-task__status').text()).not.toBe('Running');
    expect(wrapper.get('progress').attributes('value')).toBe('100');
    expect(wrapper.get('.processing-detail-warning').text()).toContain('2 calculations failed');
  });

  it('keeps running while later tracks remain pending and shows earlier failures', () => {
    state.jobs[0]!.pending = 5;
    state.jobs[0]!.total = 10;
    state.jobs[0]!.progressPercent = 50;
    const wrapper = mount(IndexerStatusTab, {
      global: { stubs: { ProgressBar: true, RouterLink: { template: '<a><slot /></a>' } } },
    });
    expect(wrapper.get('.processing-task__status').text()).toBe('Running');
    expect(wrapper.get('.processing-task').classes()).toContain('processing-task--has-attention');
    expect(wrapper.get('progress').attributes('value')).toBe('50');
    expect(wrapper.text()).toContain('2 failed');
    expect(wrapper.text()).not.toContain('finished');
  });

  it.each([0, 1])('reports %i failed files while indexing remains at 50%', (failed) => {
    state.jobs = [];
    state.indexes = [
      {
        index: 'GPS',
        total: 1_000,
        completed: 500 - failed,
        pending: 500,
        failed,
        pendingIssueFileCount: failed,
        progressPercent: 50,
      },
    ];
    const wrapper = mount(IndexerStatusTab, {
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
    });
    expect(wrapper.get('.processing-task__status').text()).toBe('Running');
    expect(wrapper.get('progress').attributes('value')).toBe('50');
    expect(wrapper.get('.processing-task').classes().includes('processing-task--has-attention')).toBe(failed > 0);
    wrapper.unmount();
  });

  it.each([1, 2])('keeps demo health outside the shared banner for %i background jobs', (count) => {
    const blocker = { kind: 'INDEX', id: 'GPS', scope: 'WHOLE_JOB', resolution: 'AUTOMATIC' };
    state.jobs = [...['duplicate', 'exploration'].slice(0, count), 'demoHealth'].map((job) => ({
      job,
      total: 1,
      pending: 1,
      done: 0,
      state: 'WAITING',
      blockedBy: [blocker],
    }));
    state.indexes = [{ index: 'GPS', total: 2, completed: 1, pending: 1, progressPercent: 50 }];
    const wrapper = mount(IndexerStatusTab);
    try {
      const imports = wrapper.get('[aria-label="Imports and synchronization"]');
      expect(imports.get('#processing-JOB-demoHealth .processing-task__notice').text()).toBe(
        'Waiting for Track files. Resumes automatically.'
      );
      const background = wrapper.get('[aria-label="Background processing"]');
      if (count === 1) {
        expect(background.find('.processing-shared-wait').exists()).toBe(false);
        expect(background.get('.processing-task__notice').text()).toBe(
          'Waiting for Track files. Resumes automatically.'
        );
      } else {
        expect(background.get('.processing-shared-wait').text()).toContain('2 steps wait for Track files');
        expect(background.get('.processing-shared-wait').text()).toContain('1 file left.');
        expect(imports.get('#processing-INDEX-GPS .processing-task__notice').text()).toBe('Holding up 3 steps.');
      }
    } finally {
      wrapper.unmount();
    }
  });

  it('opens linked tasks before focusing them and cancels a pending highlight on unmount', async () => {
    state.jobs = [
      { job: 'duplicate', total: 1, pending: 1, done: 0, state: 'RUNNING' },
      {
        job: 'exploration',
        total: 1,
        pending: 1,
        done: 0,
        state: 'WAITING',
        blockedBy: [{ kind: 'JOB', id: 'duplicate', scope: 'WHOLE_JOB', resolution: 'AUTOMATIC' }],
      },
    ];
    const frame = vi.spyOn(window, 'requestAnimationFrame').mockReturnValue(123);
    const cancelFrame = vi.spyOn(window, 'cancelAnimationFrame');
    const wrapper = mount(IndexerStatusTab, { attachTo: document.body });
    try {
      const duplicate = wrapper.get('#processing-JOB-duplicate');
      const exploration = wrapper.get('#processing-JOB-exploration');
      duplicate.element.scrollIntoView = vi.fn();
      exploration.element.scrollIntoView = vi.fn();
      expect(duplicate.get('.processing-task__details').isVisible()).toBe(false);
      await exploration.get('.processing-task__notice button').trigger('click');
      expect(duplicate.get('.processing-task__details').isVisible()).toBe(true);
      expect(document.activeElement).toBe(duplicate.get('.processing-task__toggle').element);
      await duplicate.get('.processing-task__notice button').trigger('click');
      expect(exploration.get('.processing-task__details').isVisible()).toBe(true);
      expect(document.activeElement).toBe(exploration.get('.processing-task__toggle').element);
      expect(frame).toHaveBeenCalled();
      cancelFrame.mockClear();
      wrapper.unmount();
      expect(cancelFrame).toHaveBeenCalledWith(123);
    } finally {
      if (wrapper.exists()) wrapper.unmount();
      vi.restoreAllMocks();
    }
  });

  it.each([0, 1])('colors running energy work by actionable findings: %i', (attentionRequired) => {
    state.jobs = [
      {
        job: 'energy',
        total: 10,
        done: 4,
        pending: 5,
        failed: 0,
        unavailable: 1,
        attentionRequired,
        progressPercent: 50,
      },
    ];
    const wrapper = mount(IndexerStatusTab, {
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
    });
    expect(wrapper.get('.processing-task__status').text()).toBe('Running');
    expect(wrapper.get('progress').attributes('value')).toBe('50');
    expect(wrapper.get('.processing-task').classes().includes('processing-task--has-attention')).toBe(
      attentionRequired > 0
    );
    wrapper.unmount();
  });

  it.each([
    { total: 27_099, pending: 27_099, progressPercent: 0 },
    { total: 0, pending: 0, progressPercent: 100 },
  ])('describes mixed media and track matching work consistently: $total items', (counts) => {
    state.jobs = [{ job: 'mediaCorrelation', ...counts, done: 0, failed: 0 }];
    const wrapper = mount(IndexerStatusTab, {
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
    });
    expect(wrapper.get('.processing-task__name').text()).toBe('Media location matching');
    expect(wrapper.get('.processing-task__caption').text().replace(/\s+/g, ' ')).toBe(
      `0 of ${counts.total.toLocaleString()} work items processed`
    );
    expect(wrapper.get('.processing-technical').text()).toContain(`Total work items${counts.total.toLocaleString()}`);
    expect(wrapper.get('.processing-detail-copy').text()).toContain('media files and track updates');
    expect(wrapper.text()).not.toContain('Total tracks');
    expect(wrapper.text()).not.toContain('photos processed');
    if (!counts.total) expect(wrapper.get('.processing-task__value').text()).toBe('No work items');
    wrapper.unmount();
  });

  it('shows unavailable source analysis as informational completed work', () => {
    state.jobs = [
      {
        job: 'activityType',
        label: 'Activity Classifier',
        total: 5,
        pending: 0,
        done: 3,
        failed: 0,
        unavailable: 2,
        progressPercent: 100,
      },
    ];
    const wrapper = mount(IndexerStatusTab, {
      global: { stubs: { ProgressBar: true, RouterLink: { template: '<a><slot /></a>' } } },
    });
    expect(wrapper.get('.processing-task__status').text()).toBe('Complete');
    expect(wrapper.text()).toContain('100%');
    expect(wrapper.text()).toContain('2 unavailable');
    expect(wrapper.text()).toContain('without usable points');
    expect(wrapper.text()).not.toContain('calculations failed');
    expect(wrapper.find('.processing-detail-warning').exists()).toBe(false);
    expect(wrapper.get('.processing-detail-note').text()).toContain('2 unavailable');
    expect(wrapper.get('.processing-task--complete').exists()).toBe(true);
    expect(wrapper.get('progress').attributes('value')).toBe('100');
    expect(wrapper.get('.processing-facts').text()).not.toContain('Need attention');
  });

  it('shows discovery without a final percentage while the total is growing', () => {
    state.jobs = [];
    state.indexes = [
      { index: 'GPS', total: 2, pending: 0, completed: 2, failed: 0, progressPercent: 100, discoveryInProgress: true },
    ];
    const wrapper = mount(IndexerStatusTab, {
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
    });
    expect(wrapper.get('.processing-task__status').text()).toBe('Discovering');
    expect(wrapper.text()).toContain('Discovering files. The total is still being checked.');
    expect(wrapper.text()).not.toContain('100%');
    expect(wrapper.find('progress').exists()).toBe(false);
    expect(wrapper.find('.processing-task__track--indeterminate').exists()).toBe(true);
  });

  it('keeps media findings separate from completion and exposes diagnostics', () => {
    state.jobs = [];
    state.indexes = [
      {
        index: 'MEDIA',
        total: 10,
        pending: 0,
        completed: 8,
        failed: 2,
        pendingIssueFileCount: 2,
        removed: 0,
        excluded: 0,
        progressPercent: 100,
      },
    ];
    const wrapper = mount(IndexerStatusTab, {
      global: { stubs: { ProgressBar: true, RouterLink: { template: '<a><slot /></a>' } } },
    });
    expect(wrapper.get('.processing-task__status').text()).toBe('Needs attention');
    expect(wrapper.text()).toContain('100%');
    expect(wrapper.text()).toContain('2 failed');
    expect(wrapper.text()).toContain('Show 2 failed files');
    expect(wrapper.get('.processing-technical').text()).toContain('Show 2 failed files');
    expect(wrapper.get('.processing-attention').text()).toContain('2 files need review');
    expect(wrapper.get('.processing-attention').text()).toContain('2 media files');
    expect(wrapper.text()).not.toContain('Acknowledge');
    expect(wrapper.text()).not.toContain('Review file diagnostics');
    expect(wrapper.text()).not.toContain('finished with errors');
  });

  it('shows remaining energy work independently of completed imports', () => {
    state.jobs = [
      {
        job: 'energy',
        label: 'Energy calculation',
        total: 10,
        pending: 2,
        done: 7,
        failed: 0,
        unavailable: 1,
        progressPercent: 80,
      },
    ];
    const wrapper = mount(IndexerStatusTab, {
      global: { stubs: { ProgressBar: true, RouterLink: { template: '<a><slot /></a>' } } },
    });
    expect(wrapper.text()).toContain('Energy calculation');
    expect(wrapper.get('.processing-task__status').text()).toBe('Running');
    expect(wrapper.text()).toContain('1 unavailable');
    expect(wrapper.text()).toContain('unreliable timing');
  });

  it('shows missing energy calculations as attention instead of hiding completed imports', () => {
    state.jobs = [
      {
        job: 'energy',
        label: 'Energy calculation',
        total: 4,
        pending: 0,
        done: 2,
        failed: 0,
        unavailable: 2,
        progressPercent: 100,
      },
    ];
    const wrapper = mount(IndexerStatusTab, {
      global: { stubs: { ProgressBar: true, RouterLink: { template: '<a><slot /></a>' } } },
    });
    expect(wrapper.get('.processing-task__status').text()).toBe('Needs attention');
    expect(wrapper.text()).toContain('2 unavailable');
    expect(wrapper.text()).toContain('missing calculation');
    expect(wrapper.text()).not.toContain('2 of 2 tracks processed');
  });

  it.each([
    { attentionRequired: 0, reasons: { noDataPoints: 1, unsupportedTiming: 1 }, status: 'Complete' },
    { attentionRequired: 1, reasons: { unsupportedTiming: 1, missingActivity: 1 }, status: 'Needs attention' },
    { attentionRequired: 1, reasons: { noDataPoints: 1, missingCalculation: 1 }, status: 'Needs attention' },
  ])('uses server reason counts for completed energy work: $status', ({ attentionRequired, reasons, status }) => {
    state.jobs = [
      {
        job: 'energy',
        total: 5,
        pending: 0,
        done: 3,
        failed: 0,
        unavailable: 2,
        attentionRequired,
        unavailableReasons: reasons,
        progressPercent: 100,
      },
    ];
    const wrapper = mount(IndexerStatusTab, {
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
    });
    expect(wrapper.get('.processing-task__status').text()).toBe(status);
    expect(wrapper.get('progress').attributes('value')).toBe('100');
    expect(wrapper.text()).toContain('2 unavailable');
    const details = wrapper.get('.processing-detail-lines').text();
    if ('noDataPoints' in reasons) expect(details).toContain('no usable GPS points');
    if ('unsupportedTiming' in reasons) expect(details).toContain('unreliable or estimated');
    if ('missingActivity' in reasons) expect(details).toContain('Select an activity');
    if ('missingCalculation' in reasons) expect(details).toContain('review the missing energy estimate');
    if (!attentionRequired) expect(wrapper.get('.processing-facts').text()).not.toContain('Need attention');
    wrapper.unmount();
  });

  it('keeps software failures actionable alongside informational missing points', () => {
    state.jobs = [
      {
        job: 'activityType',
        total: 5,
        pending: 0,
        done: 2,
        failed: 1,
        unavailable: 2,
        attentionRequired: 1,
        unavailableReasons: { noDataPoints: 2 },
        progressPercent: 100,
      },
    ];
    const wrapper = mount(IndexerStatusTab, {
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
    });
    expect(wrapper.get('.processing-task__status').text()).toBe('Needs attention');
    expect(wrapper.get('.processing-facts').text()).toContain('Need attention1');
    expect(wrapper.get('.processing-facts').text()).toContain('Unavailable2');
    expect(wrapper.get('.processing-detail-warning').text()).toContain('1 calculations failed');
    wrapper.unmount();
  });

  it('uses one GPS review link while retaining distinct calculation reasons', () => {
    state.jobs = [
      {
        job: 'activityType',
        label: 'Activity Classifier',
        total: 5,
        pending: 0,
        done: 3,
        failed: 0,
        unavailable: 2,
        progressPercent: 100,
      },
      {
        job: 'energy',
        label: 'Energy calculation',
        total: 5,
        pending: 0,
        done: 3,
        failed: 0,
        unavailable: 2,
        progressPercent: 100,
      },
    ];
    const wrapper = mount(IndexerStatusTab, {
      global: { stubs: { ProgressBar: true, RouterLink: { template: '<a><slot /></a>' } } },
    });
    expect(wrapper.findAll('a').filter((link) => link.text() === 'Review GPS findings')).toHaveLength(1);
    expect(wrapper.text()).toContain('without usable points');
    expect(wrapper.text()).toContain('unreliable timing');
    expect(wrapper.text()).toContain('The same track can affect more than one calculation.');
  });
});
