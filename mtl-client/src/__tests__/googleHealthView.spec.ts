import { enableAutoUnmount, flushPromises as flushVuePromises, mount, shallowMount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia } from 'pinia';
import PrimeVue from 'primevue/config';

enableAutoUnmount(afterEach);
afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

async function flushPromises() {
  await vi.advanceTimersByTimeAsync(0);
  await flushVuePromises();
}

const healthApiMock = vi.hoisted(() => ({
  status: vi.fn(),
  summaries: vi.fn(),
  sources: vi.fn(),
  records: vi.fn(),
  record: vi.fn(),
  groupedMetrics: vi.fn(),
  latestMetrics: vi.fn(),
}));

vi.mock('@/features/health/api/googleHealthApi', () => ({
  googleHealthApi: healthApiMock,
}));

vi.mock('x8ing-mtl-api-typescript-fetch', async () => {
  const actual = await vi.importActual<typeof import('x8ing-mtl-api-typescript-fetch')>(
    'x8ing-mtl-api-typescript-fetch'
  );
  return {
    ...actual,
    ApplicationStatusControllerApi: class {
      async getApplicationStatus() {
        return {
          generatedAt: new Date(),
          included: new Set(['google-health']),
          googleHealth: await healthApiMock.status(),
          errors: [],
        };
      }
    },
  };
});

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>();
  return {
    ...actual,
    useRoute: () => ({ query: {} }),
  };
});

import HealthRawDataScreen from '@/features/health/screens/raw/HealthRawDataScreen.vue';
import HealthOverviewScreen from '@/features/health/screens/overview/HealthOverviewScreen.vue';
import { APPLICATION_STATUS_SECTION, useApplicationStatusStore } from '@/stores/applicationStatusStore';

const BottomSheetStub = {
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template: `<div v-if="modelValue" class="bottom-sheet-stub"><button @click="$emit('update:modelValue', false)">Close details</button><slot /></div>`,
};

function mountView(pinia = createPinia()) {
  return mount(HealthRawDataScreen, {
    attachTo: document.body,
    global: {
      plugins: [pinia, [PrimeVue, { unstyled: true }]],
      stubs: {
        RouterLink: { template: '<a><slot /></a>' },
        BottomSheet: BottomSheetStub,
      },
    },
  });
}

type View = ReturnType<typeof mountView>;

async function chooseOption(wrapper: View, inputId: string, optionLabel: string) {
  await wrapper.get(`#${inputId}[role="combobox"]`).trigger('click');
  await flushPromises();
  const listId = wrapper.get(`#${inputId}`).attributes('aria-controls');
  const option = Array.from(document.getElementById(listId)!.querySelectorAll<HTMLElement>('[role="option"]')).find(
    (element) => element.textContent?.trim() === optionLabel
  );
  expect(option, `Option ${optionLabel} should be available`).toBeDefined();
  option!.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
  option!.click();
  await flushPromises();
  expect(wrapper.get(`#${inputId}`).text()).toBe(optionLabel);
}

async function clickButton(wrapper: View, text: string) {
  const button = wrapper.findAll('button').find((candidate) => candidate.text() === text);
  expect(button, `Button ${text} should be available`).toBeDefined();
  await button!.trigger('click');
  await flushPromises();
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: Error) => void;
  const promise = new Promise<T>((onResolve, onReject) => {
    resolve = onResolve;
    reject = onReject;
  });
  return { promise, resolve, reject };
}

const stepsRecord = { id: 'point:steps', dataType: 'steps', valueSummary: 'Steps result' };
const sleepRecord = { id: 'point:sleep', dataType: 'sleep', valueSummary: 'Sleep result' };

describe('HealthRawDataScreen', () => {
  it('labels generated records and omits the account sync tile', async () => {
    const wrapper = mountView();
    await wrapper.setProps({ syntheticData: true });
    await flushPromises();
    expect(wrapper.text()).toContain('Synthetic demo data.');
    expect(wrapper.find('.gh-metric--sync').exists()).toBe(false);
  });

  beforeEach(() => {
    vi.useFakeTimers();
    vi.stubGlobal(
      'matchMedia',
      vi.fn((query: string) => ({
        matches: false,
        media: query,
        onchange: null,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        addListener: vi.fn(),
        removeListener: vi.fn(),
        dispatchEvent: vi.fn(),
      }))
    );
    Object.values(healthApiMock).forEach((mockFunction) => mockFunction.mockReset());
    healthApiMock.status.mockResolvedValue({
      configurationState: 'CONFIGURED',
      connectionState: 'CONNECTED',
      syncStates: [],
      job: { state: 'IDLE' },
    });
    healthApiMock.summaries.mockResolvedValue([
      { dataType: 'steps', representation: 'DAILY_ROLLUP', recordCount: 2 },
      { dataType: 'sleep', representation: 'RAW', recordCount: 1 },
    ]);
    healthApiMock.sources.mockResolvedValue([{ id: 7, applicationName: 'Test watch' }]);
    healthApiMock.records.mockResolvedValue({ records: [], nextCursor: undefined });
    healthApiMock.groupedMetrics.mockResolvedValue({ series: [] });
    healthApiMock.latestMetrics.mockResolvedValue([]);
  });

  it('shows records while status, summaries and sources are still loading', async () => {
    const pendingStatus = deferred<unknown>();
    const pendingSummaries = deferred<unknown[]>();
    const pendingSources = deferred<unknown[]>();
    healthApiMock.status.mockReturnValueOnce(pendingStatus.promise);
    healthApiMock.summaries.mockReturnValueOnce(pendingSummaries.promise);
    healthApiMock.sources.mockReturnValueOnce(pendingSources.promise);
    healthApiMock.records.mockResolvedValueOnce({ records: [stepsRecord] });
    const wrapper = mountView();
    await flushPromises();

    expect(wrapper.get('.gh-records-table').text()).toContain('Steps result');
    expect(wrapper.text()).toContain('Loading data summaries…');
    expect(wrapper.text()).toContain('Loading sources…');
    expect(
      wrapper
        .findAll('.gh-metric strong')
        .slice(0, 3)
        .map((item) => item.text())
    ).toEqual(['—', '—', '—']);
    expect(wrapper.get('#gh-data-type').attributes('aria-disabled')).toBe('true');
    expect(wrapper.get('#gh-source').attributes('aria-disabled')).toBe('true');
    expect(wrapper.text()).not.toContain('Google Health is disconnected');

    pendingSummaries.resolve([{ dataType: 'steps', representation: 'DAILY_ROLLUP', recordCount: 2 }]);
    await flushPromises();
    expect(
      wrapper
        .findAll('.gh-metric strong')
        .slice(0, 3)
        .map((item) => item.text())
    ).toEqual(['2', '1', '—']);
    expect(wrapper.get('#gh-data-type').attributes('aria-disabled')).toBe('false');
    expect(wrapper.text()).toContain('Loading sources…');
    pendingSources.resolve([{ id: 7, applicationName: 'Test watch' }]);
    await flushPromises();
    expect(wrapper.get('#gh-source').attributes('aria-disabled')).toBe('false');
    expect(wrapper.text()).not.toContain('Loading sources…');
    pendingStatus.resolve({ configurationState: 'CONFIGURED', connectionState: 'CONNECTED', syncStates: [] });
    await flushPromises();
    expect(healthApiMock.records).toHaveBeenCalledOnce();
  });

  it.each([
    ['summaries', 'Retry summaries'],
    ['sources', 'Retry sources'],
  ] as const)('keeps a %s failure local and retries it without reloading records', async (endpoint, retryLabel) => {
    healthApiMock[endpoint].mockRejectedValueOnce(new Error(`${endpoint} unavailable`));
    healthApiMock.records.mockResolvedValueOnce({ records: [stepsRecord] });
    const wrapper = mountView();
    await flushPromises();
    expect(wrapper.get('.gh-records-table').text()).toContain('Steps result');
    expect(wrapper.text()).toContain(`${endpoint} unavailable`);
    await clickButton(wrapper, retryLabel);
    expect(wrapper.text()).not.toContain(`${endpoint} unavailable`);
    expect(healthApiMock[endpoint]).toHaveBeenCalledTimes(2);
    expect(healthApiMock.records).toHaveBeenCalledOnce();
    expect(healthApiMock[endpoint === 'summaries' ? 'sources' : 'summaries']).toHaveBeenCalledOnce();
  });

  it('keeps records visible when the status request fails', async () => {
    healthApiMock.status.mockRejectedValueOnce(new Error('Status unavailable'));
    healthApiMock.records.mockResolvedValueOnce({ records: [stepsRecord] });
    const wrapper = mountView();
    await flushPromises();
    expect(wrapper.get('.gh-records-table').text()).toContain('Steps result');
    expect(wrapper.find('[role="alert"]').exists()).toBe(true);
    await clickButton(wrapper, 'Retry status');
    await vi.advanceTimersByTimeAsync(8_000);
    await flushPromises();
    expect(wrapper.find('[role="alert"]').exists()).toBe(false);
    expect(healthApiMock.records).toHaveBeenCalledOnce();
    expect(healthApiMock.status).toHaveBeenCalledTimes(2);
  });

  it('ignores summaries and sources from a previous opening', async () => {
    const oldSummaries = deferred<unknown[]>();
    const oldSources = deferred<unknown[]>();
    healthApiMock.summaries.mockReturnValueOnce(oldSummaries.promise);
    healthApiMock.sources.mockReturnValueOnce(oldSources.promise);
    healthApiMock.records.mockResolvedValue({ records: [stepsRecord] });
    const wrapper = mountView();
    await flushPromises();
    await wrapper.setProps({ active: false });
    await wrapper.setProps({ active: true });
    await flushPromises();
    oldSummaries.resolve([{ dataType: 'weight', representation: 'RAW', recordCount: 99 }]);
    oldSources.resolve([{ id: 99, applicationName: 'Obsolete source' }]);
    await flushPromises();
    expect(wrapper.get('.gh-coverage-table').text()).not.toContain('99');
    expect(
      wrapper
        .findAll('.gh-metric strong')
        .slice(0, 3)
        .map((item) => item.text())
    ).toEqual(['3', '2', '1']);
    expect(wrapper.text()).not.toContain('Obsolete source');
  });

  it('shows an inert disabled state without asking for setup', async () => {
    healthApiMock.status.mockResolvedValue({
      configurationState: 'DISABLED',
      connectionState: 'DISCONNECTED',
      syncStates: [],
      job: { state: 'IDLE' },
    });
    healthApiMock.summaries.mockResolvedValue([]);

    const wrapper = mountView();
    await flushPromises();

    expect(wrapper.text()).toContain('Google Health is disabled');
    expect(wrapper.text()).toContain('optional connector');
    expect(wrapper.text()).not.toContain('setup is not complete');
  });

  it('keeps retained local records browsable after disconnect', async () => {
    healthApiMock.status.mockResolvedValue({
      configurationState: 'CONFIGURED',
      connectionState: 'DISCONNECTED',
      syncStates: [],
      job: { state: 'IDLE' },
    });
    healthApiMock.summaries.mockResolvedValue([
      {
        dataType: 'sleep',
        representation: 'RAW',
        recordCount: 1,
        earliestRecord: new Date('2026-08-27T22:00:00Z'),
        latestRecord: new Date('2026-08-28T06:00:00Z'),
      },
    ]);
    healthApiMock.records.mockResolvedValue({
      records: [
        {
          id: 'point:1',
          dataType: 'sleep',
          representation: 'RAW',
          recordTime: new Date('2026-08-27T22:00:00Z'),
        },
      ],
      nextCursor: undefined,
    });

    const wrapper = mountView();
    await flushPromises();

    expect(wrapper.text()).toContain('Google Health is disconnected. Stored local records remain available');
    expect(wrapper.text()).toContain('Stored data');
    expect(wrapper.text()).toContain('Sleep');
    expect(healthApiMock.summaries).toHaveBeenCalledOnce();
    expect(healthApiMock.records).toHaveBeenCalledOnce();
  });

  it('applies all browser filters, paginates, and opens redacted record details on demand', async () => {
    healthApiMock.status.mockResolvedValue({
      configurationState: 'CONFIGURED',
      connectionState: 'CONNECTED',
      syncStates: [],
      job: { state: 'IDLE' },
    });
    healthApiMock.summaries.mockResolvedValue([
      { dataType: 'steps', representation: 'DAILY_ROLLUP', recordCount: 2 },
      { dataType: 'heart-rate', representation: 'HEART_RATE_SAMPLE', recordCount: 1 },
    ]);
    healthApiMock.sources.mockResolvedValue([
      { id: 7, applicationName: 'Google Fit', deviceDisplayName: 'Watch', platform: 'ANDROID', recordCount: 3 },
    ]);
    healthApiMock.records.mockResolvedValueOnce({ records: [], nextCursor: undefined });

    const wrapper = mountView();
    await flushPromises();

    expect(wrapper.text()).toContain('Heart Rate');
    expect(wrapper.text()).not.toContain('Heart-Rate');

    healthApiMock.records.mockResolvedValueOnce({
      records: [
        {
          id: 'point:2',
          dataType: 'steps',
          representation: 'DAILY_ROLLUP',
          recordTime: new Date('2026-08-27T00:00:00Z'),
          valueSummary: '0',
          sourceName: 'Watch',
        },
      ],
      nextCursor: 'cursor-2',
    });
    await chooseOption(wrapper, 'gh-data-type', 'Steps');
    await chooseOption(wrapper, 'gh-representation', 'DAILY ROLLUP');
    await chooseOption(wrapper, 'gh-source', 'Google Fit · Watch · ANDROID');
    const dates = wrapper.findAll('input[type="date"]');
    await dates[0].setValue('2026-08-01');
    await dates[1].setValue('2026-08-27');
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(healthApiMock.records).toHaveBeenLastCalledWith({
      dataType: 'steps',
      representation: 'DAILY_ROLLUP',
      startDate: '2026-08-01',
      endDate: '2026-08-27',
      timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone,
      dataSourceId: 7,
      cursor: undefined,
      pageSize: 50,
    });
    expect(wrapper.text()).toContain('0');

    healthApiMock.records.mockResolvedValueOnce({
      records: [
        {
          id: 'point:1',
          dataType: 'steps',
          representation: 'DAILY_ROLLUP',
          recordTime: new Date('2026-08-26T00:00:00Z'),
        },
      ],
      nextCursor: undefined,
    });
    await wrapper
      .findAll('button')
      .find((candidate) => candidate.text() === 'Load more')!
      .trigger('click');
    await flushPromises();
    expect(healthApiMock.records).toHaveBeenLastCalledWith(expect.objectContaining({ cursor: 'cursor-2' }));
    expect(wrapper.findAll('.gh-records-table tbody tr')).toHaveLength(2);

    healthApiMock.record.mockResolvedValue({
      id: 'point:2',
      dataType: 'steps',
      representation: 'DAILY_ROLLUP',
      recordTime: new Date('2026-08-27T00:00:00Z'),
      originalTime: '2026-08-27T00:00:00.123456789Z',
      payload: { steps: { count: '0' } },
    });
    await wrapper
      .findAll('button')
      .find((candidate) => candidate.text() === 'Details')!
      .trigger('click');
    await flushPromises();

    expect(healthApiMock.record).toHaveBeenCalledWith('point:2');
    expect(wrapper.text()).toContain('2026-08-27T00:00:00.123456789Z');
    expect(wrapper.text()).toContain('"count": "0"');
  });

  it('keeps a record-list failure local and retries only the records', async () => {
    healthApiMock.status.mockResolvedValue({
      configurationState: 'CONFIGURED',
      connectionState: 'CONNECTED',
      syncStates: [],
      job: { state: 'IDLE' },
    });
    healthApiMock.summaries.mockResolvedValue([{ dataType: 'steps', representation: 'DAILY_ROLLUP', recordCount: 1 }]);
    healthApiMock.records.mockRejectedValueOnce(new Error('Recent records are unavailable.'));
    const wrapper = mountView();
    await flushPromises();

    const statusPill = wrapper.get('.admin-status-pill');
    expect(statusPill.text()).toContain('Connected');
    expect(statusPill.classes()).toContain('admin-status-pill--success');
    expect(wrapper.text()).toContain('Recent records are unavailable.');
    expect(wrapper.text()).not.toContain('No records match these filters.');

    await wrapper
      .findAll('button')
      .find((candidate) => candidate.text() === 'Retry records')!
      .trigger('click');
    await flushPromises();

    expect(healthApiMock.records).toHaveBeenCalledTimes(2);
    expect(healthApiMock.status).toHaveBeenCalledOnce();
    expect(healthApiMock.summaries).toHaveBeenCalledOnce();
    expect(healthApiMock.sources).toHaveBeenCalledOnce();
    expect(wrapper.text()).not.toContain('Recent records are unavailable.');
  });

  it('finishes a pending status retry independently of a sync refresh', async () => {
    const pinia = createPinia();
    const store = useApplicationStatusStore(pinia);
    const pendingStatus = deferred<void>();
    store.googleHealth = {
      configurationState: 'CONFIGURED',
      connectionState: 'CONNECTED',
      syncStates: [],
      job: { id: 'active-sync', state: 'RUNNING' },
    };
    store.sectionStates[APPLICATION_STATUS_SECTION.GOOGLE_HEALTH].lastError = 'Status needs retry.';
    healthApiMock.status.mockReturnValue(new Promise(() => {}));
    const wrapper = mountView(pinia);
    await flushPromises();
    vi.spyOn(store, 'force').mockReturnValue(pendingStatus.promise);
    await clickButton(wrapper, 'Retry status');

    store.googleHealth = { ...store.googleHealth, job: { id: 'active-sync', state: 'COMPLETED' } };
    await flushPromises();
    expect(healthApiMock.summaries).toHaveBeenCalledTimes(2);
    expect(healthApiMock.sources).toHaveBeenCalledTimes(2);
    store.googleHealth = null;
    store.sectionStates[APPLICATION_STATUS_SECTION.GOOGLE_HEALTH].lastError = 'Status unavailable after sync.';
    await flushPromises();
    expect(wrapper.get('.admin-status-pill').text()).toBe('Loading');

    pendingStatus.resolve();
    await flushPromises();
    expect(wrapper.text()).toContain('Status unavailable after sync.');
    expect(wrapper.get('.admin-status-pill').text()).toBe('Unavailable');
  });

  it.each(['completed', 'continued'])('refreshes the catalogue and records after a pass is %s', async (transition) => {
    const jobId = 'b5281867-8085-43bd-9b45-d18a98b82c9d';
    healthApiMock.status.mockResolvedValue({
      configurationState: 'CONFIGURED',
      connectionState: 'CONNECTED',
      syncStates: [],
      job: { id: jobId, state: 'RUNNING' },
    });
    healthApiMock.summaries.mockResolvedValue([{ dataType: 'steps', representation: 'DAILY_ROLLUP', recordCount: 1 }]);
    const wrapper = mountView();
    await flushPromises();

    healthApiMock.status.mockResolvedValue({
      configurationState: 'CONFIGURED',
      connectionState: 'CONNECTED',
      syncStates: [],
      job: {
        id: transition === 'continued' ? 'next-pass' : jobId,
        state: transition === 'continued' ? 'RUNNING' : 'COMPLETED',
      },
    });
    const refresh = useApplicationStatusStore().force(APPLICATION_STATUS_SECTION.GOOGLE_HEALTH);
    await vi.advanceTimersByTimeAsync(8_000);
    await refresh;
    await flushPromises();

    expect(healthApiMock.summaries).toHaveBeenCalledTimes(2);
    expect(healthApiMock.sources).toHaveBeenCalledTimes(2);
    expect(healthApiMock.records).toHaveBeenCalledTimes(2);
    wrapper.unmount();
  });

  it('shares active sync polling across the records and overview screens without opening bursts', async () => {
    healthApiMock.status.mockResolvedValue({
      configurationState: 'CONFIGURED',
      connectionState: 'CONNECTED',
      syncStates: [],
      job: { id: 'active-sync', state: 'RUNNING', recordsProcessed: 400_000 },
    });
    const pinia = createPinia();
    const records = mountView(pinia);
    await flushPromises();
    expect(healthApiMock.status).toHaveBeenCalledTimes(1);

    await vi.advanceTimersByTimeAsync(1_000);
    const overview = shallowMount(HealthOverviewScreen, { props: { active: true }, global: { plugins: [pinia] } });
    await flushPromises();
    await vi.advanceTimersByTimeAsync(1_000);
    await records.setProps({ active: false });
    await records.setProps({ active: true });
    await flushPromises();
    expect(healthApiMock.status).toHaveBeenCalledTimes(1);

    await vi.advanceTimersByTimeAsync(5_999);
    expect(healthApiMock.status).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(1);
    expect(healthApiMock.status).toHaveBeenCalledTimes(2);
    await vi.advanceTimersByTimeAsync(24_000);
    expect(healthApiMock.status).toHaveBeenCalledTimes(5);
    records.unmount();
    overview.unmount();
  });

  it('labels the actual combobox elements before and after keyboard selection', async () => {
    const wrapper = mountView();
    await flushPromises();
    for (const [id, name, placeholder] of [
      ['gh-data-type', 'Data type', 'All types'],
      ['gh-representation', 'Representation', 'All representations'],
      ['gh-source', 'Source', 'All sources'],
    ]) {
      const control = wrapper.get(`#${id}[role="combobox"]`);
      const labelId = control.attributes('aria-labelledby');
      expect(wrapper.get(`#${labelId}`).text()).toBe(name);
      expect(control.text()).toBe(placeholder);
      expect(control.attributes('tabindex')).toBe('0');
      await control.trigger('keydown', { code: 'ArrowDown', key: 'ArrowDown' });
      await flushPromises();
      expect(control.attributes('aria-expanded')).toBe('true');
      await control.trigger('keydown', { code: 'End', key: 'End' });
      await control.trigger('keydown', { code: 'Enter', key: 'Enter' });
      await flushPromises();
      expect(control.text()).not.toBe(placeholder);
      expect(control.attributes('aria-labelledby')).toBe(labelId);
      expect(wrapper.get(`#${labelId}`).text()).toBe(name);
    }
  });

  it('keeps all unapplied fields out of pagination and manual refresh', async () => {
    healthApiMock.records.mockResolvedValue({ records: [stepsRecord], nextCursor: 'unfiltered-next' });
    const wrapper = mountView();
    await flushPromises();
    await chooseOption(wrapper, 'gh-data-type', 'Sleep');
    await chooseOption(wrapper, 'gh-representation', 'RAW');
    await chooseOption(wrapper, 'gh-source', 'Test watch');
    await wrapper.get('#gh-start').setValue('2026-08-01');
    await wrapper.get('#gh-end').setValue('2026-08-27');
    expect(wrapper.text()).toContain('Filter changes are not applied yet.');
    expect(wrapper.text()).not.toContain('5 applied filters');
    const unfilteredQuery = {
      dataType: undefined,
      representation: undefined,
      dataSourceId: undefined,
      startDate: undefined,
      endDate: undefined,
    };
    await clickButton(wrapper, 'Load more');
    expect(healthApiMock.records).toHaveBeenLastCalledWith(
      expect.objectContaining({
        ...unfilteredQuery,
        cursor: 'unfiltered-next',
      })
    );
    await clickButton(wrapper, 'Refresh');
    expect(healthApiMock.records).toHaveBeenLastCalledWith(
      expect.objectContaining({
        ...unfilteredQuery,
        cursor: undefined,
      })
    );
    healthApiMock.records.mockResolvedValueOnce({ records: [sleepRecord] });
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    expect(healthApiMock.records).toHaveBeenLastCalledWith(
      expect.objectContaining({
        dataType: 'sleep',
        representation: 'RAW',
        dataSourceId: 7,
        startDate: '2026-08-01',
        endDate: '2026-08-27',
        cursor: undefined,
      })
    );
    expect(wrapper.text()).toContain('5 applied filters');
    expect(wrapper.text()).not.toContain('Filter changes are not applied yet.');
    expect(wrapper.get('.gh-records-table').text()).not.toContain('Steps result');
  });

  it.each(['resolve', 'reject'] as const)('ignores an old filtered %s after Reset completes', async (outcome) => {
    const wrapper = mountView();
    await flushPromises();
    const oldRequest = deferred<{ records: (typeof sleepRecord)[] }>();
    healthApiMock.records.mockReturnValueOnce(oldRequest.promise);
    await chooseOption(wrapper, 'gh-data-type', 'Sleep');
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    healthApiMock.records.mockResolvedValueOnce({ records: [stepsRecord] });
    await clickButton(wrapper, 'Reset');
    if (outcome === 'resolve') oldRequest.resolve({ records: [sleepRecord] });
    else oldRequest.reject(new Error('Obsolete filtered error'));
    await flushPromises();
    expect(wrapper.get('.gh-records-table').text()).toContain('Steps result');
    expect(wrapper.text()).not.toContain('Sleep result');
    expect(wrapper.text()).not.toContain('Obsolete filtered error');
    expect(wrapper.find('.admin-status-pill--loading').exists()).toBe(false);
  });

  it('ignores an old pagination result after applying a new query', async () => {
    healthApiMock.records.mockResolvedValueOnce({ records: [stepsRecord], nextCursor: 'steps-next' });
    const wrapper = mountView();
    await flushPromises();
    const oldPage = deferred<{ records: (typeof stepsRecord)[]; nextCursor: string }>();
    healthApiMock.records.mockReturnValueOnce(oldPage.promise);
    await clickButton(wrapper, 'Load more');
    await chooseOption(wrapper, 'gh-data-type', 'Sleep');
    healthApiMock.records.mockResolvedValueOnce({ records: [sleepRecord] });
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    oldPage.resolve({ records: [{ ...stepsRecord, id: 'point:old-page' }], nextCursor: 'stale-cursor' });
    await flushPromises();
    expect(wrapper.get('.gh-records-table').text()).toContain('Sleep result');
    expect(wrapper.text()).not.toContain('Steps result');
    expect(wrapper.text()).not.toContain('Load more');
  });

  it('does not let a slow catalogue refresh replace a newer filter request', async () => {
    const wrapper = mountView();
    await flushPromises();
    const oldCatalogue = deferred<unknown[]>();
    healthApiMock.summaries.mockReturnValueOnce(oldCatalogue.promise);
    await clickButton(wrapper, 'Refresh');
    await chooseOption(wrapper, 'gh-data-type', 'Sleep');
    healthApiMock.records.mockResolvedValueOnce({ records: [sleepRecord] });
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    const recordsCalls = healthApiMock.records.mock.calls.length;
    oldCatalogue.resolve([{ dataType: 'sleep', representation: 'RAW', recordCount: 1 }]);
    await flushPromises();
    expect(healthApiMock.records).toHaveBeenCalledTimes(recordsCalls);
    expect(wrapper.get('.gh-records-table').text()).toContain('Sleep result');
  });

  it.each(['resolve', 'reject'] as const)('ignores a closed detail %s after another record opens', async (outcome) => {
    healthApiMock.records.mockResolvedValueOnce({ records: [stepsRecord, sleepRecord] });
    const wrapper = mountView();
    await flushPromises();
    const oldDetail = deferred<typeof stepsRecord>();
    healthApiMock.record.mockReturnValueOnce(oldDetail.promise).mockResolvedValueOnce(sleepRecord);
    await clickButton(wrapper, 'Details');
    await clickButton(wrapper, 'Close details');
    await wrapper.findAll('.gh-records-table button')[1].trigger('click');
    await flushPromises();
    if (outcome === 'resolve') oldDetail.resolve(stepsRecord);
    else oldDetail.reject(new Error('Obsolete detail error'));
    await flushPromises();
    const detail = wrapper.get('.bottom-sheet-stub');
    expect(detail.text()).toContain('Sleep result');
    expect(detail.text()).not.toContain('Steps result');
    expect(detail.text()).not.toContain('Obsolete detail error');
    await clickButton(wrapper, 'Close details');
    expect(wrapper.find('.bottom-sheet-stub').exists()).toBe(false);
  });

  it('invalidates list and detail requests while inactive and on unmount', async () => {
    healthApiMock.records.mockResolvedValueOnce({ records: [stepsRecord] });
    const wrapper = mountView();
    await flushPromises();
    const oldDetail = deferred<typeof stepsRecord>();
    healthApiMock.record.mockReturnValueOnce(oldDetail.promise);
    await clickButton(wrapper, 'Details');
    const oldList = deferred<{ records: (typeof stepsRecord)[] }>();
    healthApiMock.records.mockReturnValueOnce(oldList.promise);
    await clickButton(wrapper, 'Reset');
    await wrapper.setProps({ active: false });
    oldDetail.resolve(stepsRecord);
    oldList.resolve({ records: [stepsRecord] });
    await flushPromises();
    expect(wrapper.find('.bottom-sheet-stub').exists()).toBe(false);
    expect(wrapper.find('.gh-records-table').exists()).toBe(false);
    healthApiMock.records.mockResolvedValueOnce({ records: [sleepRecord] });
    await wrapper.setProps({ active: true });
    await flushPromises();
    expect(wrapper.get('.gh-records-table').text()).toContain('Sleep result');
    const afterUnmount = deferred<typeof sleepRecord>();
    healthApiMock.record.mockReturnValueOnce(afterUnmount.promise);
    await clickButton(wrapper, 'Details');
    wrapper.unmount();
    afterUnmount.resolve(sleepRecord);
    await flushPromises();
    expect(document.querySelector('.bottom-sheet-stub')).toBeNull();
  });

  it('preserves loaded pages when sync completes and refreshes only on request', async () => {
    healthApiMock.status.mockResolvedValue({
      configurationState: 'CONFIGURED',
      connectionState: 'CONNECTED',
      job: { id: 'pass-1', state: 'RUNNING' },
      syncStates: [],
    });
    healthApiMock.records
      .mockResolvedValueOnce({ records: [stepsRecord], nextCursor: 'page-2' })
      .mockResolvedValueOnce({ records: [sleepRecord] });
    const wrapper = mountView();
    await flushPromises();
    await clickButton(wrapper, 'Load more');
    await chooseOption(wrapper, 'gh-data-type', 'Sleep');
    healthApiMock.status.mockResolvedValue({
      configurationState: 'CONFIGURED',
      connectionState: 'CONNECTED',
      job: { id: 'pass-1', state: 'COMPLETED' },
      syncStates: [],
    });
    const refresh = useApplicationStatusStore().force(APPLICATION_STATUS_SECTION.GOOGLE_HEALTH);
    await vi.advanceTimersByTimeAsync(8_000);
    await refresh;
    await flushPromises();
    expect(healthApiMock.records).toHaveBeenCalledTimes(2);
    expect(wrapper.findAll('.gh-records-table tbody tr')).toHaveLength(2);
    expect(wrapper.text()).toContain('Refresh to view the latest records.');
    await clickButton(wrapper, 'Refresh records');
    expect(healthApiMock.records).toHaveBeenLastCalledWith(
      expect.objectContaining({ cursor: undefined, dataType: undefined })
    );
  });

  it('renders provider calendar dates and safe route errors in detail', async () => {
    const record = {
      ...stepsRecord,
      temporalKind: 'CALENDAR_DATE',
      civilDate: '2026-08-02',
      recordTime: new Date('2026-08-02T00:00:00Z'),
      routeState: 'FAILED',
      routeError: 'Route export is unavailable. Retry sync.',
    };
    healthApiMock.records.mockResolvedValueOnce({ records: [record] });
    healthApiMock.record.mockResolvedValueOnce(record);
    healthApiMock.summaries.mockResolvedValueOnce([
      {
        dataType: 'steps',
        representation: 'DAILY_ROLLUP',
        recordCount: 1,
        temporalKind: 'CALENDAR_DATE',
        earliestCivilDate: '2026-08-02',
        latestCivilDate: '2026-08-02',
      },
    ]);
    const wrapper = mountView();
    await flushPromises();
    const time = wrapper.get('.gh-records-table time');
    expect(time.attributes('datetime')).toBe('2026-08-02');
    expect(wrapper.get('.gh-coverage-table').text()).toContain(`${time.text()} – ${time.text()}`);
    expect(time.text()).toBe(
      new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeZone: 'UTC' }).format(record.recordTime)
    );
    await clickButton(wrapper, 'Details');
    expect(wrapper.get('.bottom-sheet-stub').text()).toContain('Calendar date');
    expect(wrapper.get('.bottom-sheet-stub').text()).toContain('Route export is unavailable. Retry sync.');
  });

  it('retains the refresh notice when an older first-page request finishes after a sync', async () => {
    healthApiMock.status.mockResolvedValue({
      configurationState: 'CONFIGURED',
      connectionState: 'CONNECTED',
      job: { id: 'pass-1', state: 'RUNNING' },
      syncStates: [],
    });
    const wrapper = mountView();
    await flushPromises();
    const oldList = deferred<{ records: (typeof stepsRecord)[] }>();
    healthApiMock.records.mockReturnValueOnce(oldList.promise);
    await clickButton(wrapper, 'Reset');
    healthApiMock.status.mockResolvedValue({
      configurationState: 'CONFIGURED',
      connectionState: 'CONNECTED',
      job: { id: 'pass-1', state: 'COMPLETED' },
      syncStates: [],
    });
    const refresh = useApplicationStatusStore().force(APPLICATION_STATUS_SECTION.GOOGLE_HEALTH);
    await vi.advanceTimersByTimeAsync(8_000);
    await refresh;
    await flushPromises();
    oldList.resolve({ records: [stepsRecord] });
    await flushPromises();
    expect(wrapper.text()).toContain('Refresh to view the latest records.');
    expect(wrapper.get('.gh-records-table').text()).toContain('Steps result');
  });

  it('retries a failed next page using the applied query and retained cursor', async () => {
    healthApiMock.records.mockResolvedValueOnce({ records: [stepsRecord], nextCursor: 'page-2' });
    const wrapper = mountView();
    await flushPromises();
    await chooseOption(wrapper, 'gh-data-type', 'Sleep');
    healthApiMock.records.mockRejectedValueOnce(new Error('The next page is unavailable.'));
    await clickButton(wrapper, 'Load more');
    expect(wrapper.get('.gh-records-table').text()).toContain('Steps result');
    healthApiMock.records.mockResolvedValueOnce({ records: [{ ...stepsRecord, id: 'point:older' }] });
    await clickButton(wrapper, 'Retry records');
    expect(healthApiMock.records).toHaveBeenLastCalledWith(
      expect.objectContaining({
        cursor: 'page-2',
        dataType: undefined,
      })
    );
    expect(wrapper.findAll('.gh-records-table tbody tr')).toHaveLength(2);
    expect(wrapper.text()).not.toContain('The next page is unavailable.');
  });

  it('keeps a long catalogue collapsed on mobile while records remain available', async () => {
    vi.mocked(window.matchMedia).mockImplementation((query: string) => ({
      matches: query.includes('max-width: 768px'),
      media: query,
      onchange: null,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      addListener: vi.fn(),
      removeListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }));
    healthApiMock.summaries.mockResolvedValueOnce(
      Array.from({ length: 35 }, (_, index) => ({
        dataType: `synthetic-type-${index}`,
        representation: 'RAW',
        recordCount: index + 1,
      }))
    );
    healthApiMock.records.mockResolvedValueOnce({ records: [stepsRecord] });
    const wrapper = mountView();
    await flushPromises();
    expect(wrapper.find('#gh-coverage-content').exists()).toBe(false);
    expect(wrapper.get('.gh-record-list').text()).toContain('Steps result');
    const toggle = wrapper.findAll('button').find((candidate) => candidate.text() === 'Show data types')!;
    expect(toggle.attributes('aria-expanded')).toBe('false');
    await clickButton(wrapper, 'Show data types');
    expect(wrapper.find('#gh-coverage-content').exists()).toBe(true);
    expect(wrapper.findAll('.gh-coverage-list .gh-data-card')).toHaveLength(4);
    await clickButton(wrapper, 'Hide data types');
    expect(wrapper.find('#gh-coverage-content').exists()).toBe(false);
  });

  it('shows summary freshness independently of sync progress', async () => {
    const mirrorSnapshotAt = new Date('2026-09-05T10:00:00Z');
    healthApiMock.status.mockResolvedValueOnce({
      configurationState: 'CONFIGURED',
      connectionState: 'CONNECTED',
      mirrorSnapshotAt,
      job: { state: 'RUNNING' },
      syncStates: [],
    });
    const wrapper = mountView();
    await flushPromises();
    expect(wrapper.text()).toContain(
      `Summary as of ${new Intl.DateTimeFormat(undefined, {
        dateStyle: 'medium',
        timeStyle: 'short',
      }).format(mirrorSnapshotAt)}`
    );
  });
});
