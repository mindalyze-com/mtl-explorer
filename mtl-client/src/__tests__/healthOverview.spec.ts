import { groupedResponse } from './fixtures/groupedHealth';
import { enableAutoUnmount, flushPromises as flushVuePromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia } from 'pinia';

enableAutoUnmount(afterEach);
afterEach(() => vi.useRealTimers());

async function flushPromises() {
  await vi.advanceTimersByTimeAsync(0);
  await flushVuePromises();
}

const healthApiMock = vi.hoisted(() => ({
  status: vi.fn(),
  summaries: vi.fn(),
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

import HealthOverviewScreen from '@/features/health/screens/overview/HealthOverviewScreen.vue';
import HealthDateRangeControl from '@/features/health/components/HealthDateRangeControl.vue';
import HealthMetricChart from '@/features/health/components/HealthMetricChart.vue';
import { APPLICATION_STATUS_SECTION, useApplicationStatusStore } from '@/stores/applicationStatusStore';

const ButtonStub = {
  props: ['label', 'disabled'],
  emits: ['click'],
  template: '<button type="button" :disabled="disabled" @click="$emit(\'click\')">{{ label }}</button>',
};

const InputTextStub = {
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template: `<input :value="modelValue" @input="$emit('update:modelValue', $event.target.value)" />`,
};

const RouterLinkStub = {
  methods: { navigate() {} },
  template: '<slot :navigate="navigate" />',
};

const HighchartsStub = {
  name: 'Highcharts',
  props: ['options'],
  template: '<div class="highcharts-stub" />',
};

function mountOverview(attachTo?: HTMLElement, pinia = createPinia()) {
  return mount(HealthOverviewScreen, {
    attachTo,
    props: { active: true },
    global: {
      plugins: [pinia],
      stubs: {
        HealthDateRangeControl: {
          props: ['label', 'preset'],
          template: '<button class="date-range-stub">{{ label }}</button>',
        },
        Button: ButtonStub,
        InputText: InputTextStub,
        RouterLink: RouterLinkStub,
        highcharts: HighchartsStub,
      },
    },
  });
}

async function toggleLatest(wrapper: ReturnType<typeof mountOverview>, open = true) {
  const details = wrapper.get<HTMLDetailsElement>('.health-latest');
  details.element.open = open;
  await details.trigger('toggle');
  await flushPromises();
}

async function preloadStatus(pinia = createPinia()) {
  useApplicationStatusStore(pinia).googleHealth = await healthApiMock.status();
  healthApiMock.status.mockClear();
  return pinia;
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((onResolve) => {
    resolve = onResolve;
  });
  return { promise, resolve };
}

describe('HealthOverviewScreen', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.useFakeTimers();
    Object.values(healthApiMock).forEach((mockFunction) => mockFunction.mockReset());
    healthApiMock.status.mockResolvedValue({
      configurationState: 'CONFIGURED',
      connectionState: 'CONNECTED',
      syncStates: [{ dataType: 'steps', lastSuccessAt: new Date('2026-08-29T05:00:00Z') }],
      job: { state: 'IDLE' },
    });
    healthApiMock.summaries.mockResolvedValue([
      { dataType: 'steps', representation: 'DAILY_ROLLUP', recordCount: 30 },
      { dataType: 'sleep', representation: 'RAW', recordCount: 8 },
      { dataType: 'heart-rate', representation: 'HEART_RATE_SAMPLE', recordCount: 120 },
    ]);
    const metrics = [
      {
        dataType: 'steps',
        title: 'Steps',
        category: 'ACTIVITY',
        unit: 'steps',
        chartType: 'COLUMN',
        decimals: 0,
        points: [{ time: new Date('2026-08-28T00:00:00Z'), value: 8240 }],
      },
      {
        dataType: 'heart-rate',
        title: 'Heart rate',
        category: 'HEART_AND_VITALS',
        unit: 'bpm',
        chartType: 'RANGE',
        decimals: 0,
        points: [{ time: new Date('2026-08-28T12:00:00Z'), value: 72, minimum: 58, maximum: 108 }],
      },
      {
        dataType: 'sleep',
        title: 'Sleep',
        category: 'SLEEP',
        unit: 'h',
        chartType: 'COLUMN',
        decimals: 1,
        points: [{ time: new Date('2026-08-28T06:00:00Z'), value: 7.4 }],
      },
    ];
    healthApiMock.groupedMetrics.mockResolvedValue(groupedResponse(metrics));
    healthApiMock.latestMetrics.mockResolvedValue(metrics);
  });

  it('updates the intraday date limit at midnight and after reactivation', async () => {
    vi.setSystemTime(new Date(2026, 9, 14, 23, 59, 59));
    localStorage.setItem('mtl.health.grouping', 'SECOND');
    const wrapper = mountOverview();
    await flushPromises();
    expect(wrapper.get('#health-second-date').attributes('max')).toBe('2026-10-14');
    expect(wrapper.get('button[aria-label="Next day"]').attributes('disabled')).toBeDefined();
    await vi.advanceTimersByTimeAsync(2_000);
    expect(wrapper.get('#health-second-date').attributes('max')).toBe('2026-10-15');
    expect(wrapper.get('button[aria-label="Next day"]').attributes('disabled')).toBeUndefined();
    await wrapper.setProps({ active: false });
    expect(vi.getTimerCount()).toBe(0);
    vi.setSystemTime(new Date(2026, 9, 16, 12));
    await wrapper.setProps({ active: true });
    await flushPromises();
    expect(wrapper.get('#health-second-date').attributes('max')).toBe('2026-10-16');
    wrapper.unmount();
    expect(vi.getTimerCount()).toBe(0);
  });

  it('requests seconds through the same grouped API and restores the calendar range', async () => {
    const wrapper = mountOverview();
    await flushPromises();
    const calendarRequest = healthApiMock.groupedMetrics.mock.calls[0]!;
    const second = wrapper.findAll('.health-grouping-options button').find((button) => button.text() === 'Intraday')!;
    await second.trigger('click');
    await flushPromises();
    expect(healthApiMock.groupedMetrics).toHaveBeenLastCalledWith(
      expect.any(String),
      expect.any(String),
      'SECOND',
      Intl.DateTimeFormat().resolvedOptions().timeZone
    );
    const selected = String(
      wrapper.get('#health-second-date').element.getAttribute('value') ??
        (wrapper.get('#health-second-date').element as HTMLInputElement).value
    );
    await wrapper.get('button[aria-label="Previous day"]').trigger('click');
    await flushPromises();
    expect(healthApiMock.groupedMetrics.mock.calls.at(-1)![0]).not.toBe(selected);
    await wrapper
      .findAll('.health-grouping-options button')
      .find((button) => button.text() === 'Day')!
      .trigger('click');
    await flushPromises();
    expect(healthApiMock.groupedMetrics).toHaveBeenLastCalledWith(
      calendarRequest[0],
      calendarRequest[1],
      'DAY',
      calendarRequest[3]
    );
    expect(wrapper.find('#health-second-date').exists()).toBe(false);
  });

  it('shares one time window and marked time across charts and resets them for another day', async () => {
    localStorage.setItem('mtl.health.grouping', 'SECOND');
    const intradaySeries = (dataType: string, title: string, startDate: string, endDate: string) => ({
      dataType,
      title,
      family: 'MEASUREMENT' as const,
      grouping: 'SECOND' as const,
      buckets: [{ startDate, endDate, availableDays: 1 }],
      detailedPoints: [{ time: new Date(`${startDate}T12:00:00`), value: 70 }],
    });
    healthApiMock.groupedMetrics.mockImplementation(async (startDate, endDate) => ({
      startDate,
      endDate,
      requestedGrouping: 'SECOND',
      resolvedGrouping: 'SECOND',
      series: [
        intradaySeries('heart-rate', 'Heart rate', startDate, endDate),
        intradaySeries('weight', 'Weight', startDate, endDate),
      ],
    }));
    const wrapper = mountOverview();
    await flushPromises();
    const day = healthApiMock.groupedMetrics.mock.calls[0]![0];
    const timeWindow = {
      start: new Date(`${day}T12:00:00`).getTime(),
      end: new Date(`${day}T12:15:00`).getTime(),
    };
    const focusTime = new Date(`${day}T12:05:00`).getTime();
    const [heartRate, weight] = wrapper.findAllComponents(HealthMetricChart);
    heartRate!.vm.$emit('update:timeWindow', timeWindow);
    weight!.vm.$emit('update:focusTime', focusTime);
    await flushPromises();
    for (const chart of wrapper.findAllComponents(HealthMetricChart)) {
      expect(chart.props('timeWindow')).toEqual(timeWindow);
      expect(chart.props('focusTime')).toBe(focusTime);
    }
    expect(wrapper.get('button[aria-label="Set exact time range"]').text()).toContain('12:00:00 – 12:15:00');
    const requestCount = healthApiMock.groupedMetrics.mock.calls.length;
    await wrapper.get('button[aria-label="Expand Heart rate chart"]').trigger('click');
    await flushPromises();
    const expanded = wrapper.get('.health-expanded-chart');
    expect(expanded.getComponent(HealthMetricChart).props('timeWindow')).toEqual(timeWindow);
    expect(expanded.find('button[aria-label="Set exact time range"]').exists()).toBe(true);
    await wrapper.get('.health-chart-back').trigger('click');
    await flushPromises();
    expect(wrapper.getComponent(HealthMetricChart).props('timeWindow')).toEqual(timeWindow);
    expect(healthApiMock.groupedMetrics).toHaveBeenCalledTimes(requestCount);
    await wrapper.get('button[aria-label="Previous day"]').trigger('click');
    await flushPromises();
    expect(wrapper.getComponent(HealthMetricChart).props('timeWindow')).toBeUndefined();
    expect(wrapper.getComponent(HealthMetricChart).props('focusTime')).toBeUndefined();
  });

  it('opens a day from a daily chart in intraday detail', async () => {
    // jsdom does not implement scrolling.
    Element.prototype.scrollIntoView = vi.fn();
    const wrapper = mountOverview();
    await flushPromises();
    wrapper.getComponent(HealthMetricChart).vm.$emit('openDay', '2026-08-27');
    await flushPromises();
    expect(healthApiMock.groupedMetrics).toHaveBeenLastCalledWith(
      '2026-08-27',
      '2026-08-28',
      'SECOND',
      Intl.DateTimeFormat().resolvedOptions().timeZone
    );
    expect((wrapper.get('#health-second-date').element as HTMLInputElement).value).toBe('2026-08-27');
  });

  it('ignores an older summary retry after a newer retry completes', async () => {
    healthApiMock.summaries.mockRejectedValueOnce(new Error('Summaries unavailable.'));
    const wrapper = mountOverview();
    await flushPromises();
    await toggleLatest(wrapper);
    const oldSummary = deferred<unknown[]>();
    const newSummary = deferred<unknown[]>();
    healthApiMock.summaries.mockReturnValueOnce(oldSummary.promise).mockReturnValueOnce(newSummary.promise);
    const retry = wrapper.findAll('button').find((button) => button.text() === 'Retry summaries')!.element;
    retry.click();
    retry.click();
    await flushPromises();
    const summaries = [{ dataType: 'steps', representation: 'DAILY_ROLLUP', recordCount: 3 }];
    newSummary.resolve(summaries);
    await flushPromises();
    expect(healthApiMock.latestMetrics).toHaveBeenCalledWith(summaries);

    oldSummary.resolve([{ dataType: 'weight', representation: 'RAW', recordCount: 99 }]);
    await flushPromises();
    expect(healthApiMock.latestMetrics).toHaveBeenCalledOnce();
    expect(wrapper.text()).not.toContain('Summaries unavailable.');
  });

  it('links pending history to Processing with a short coverage notice', async () => {
    healthApiMock.status.mockResolvedValue({
      configurationState: 'CONFIGURED',
      connectionState: 'CONNECTED',
      syncStates: [
        {
          dataType: 'heart-rate',
          representation: 'HEART_RATE_SAMPLE',
          oldestSyncedTime: new Date('2026-06-25T00:00:00Z'),
          backfillComplete: false,
        },
      ],
      job: { state: 'COMPLETED' },
    });
    const wrapper = mountOverview(undefined, await preloadStatus());
    await flushPromises();

    expect(wrapper.get('.gh-sync-notice').text()).toContain('History import pending');
    expect(wrapper.text()).toContain('Older data may be missing.');
    expect(wrapper.find('[data-testid="google-health-sync-progress"]').exists()).toBe(false);
    expect(wrapper.get('.health-footer .admin-status-pill').text()).toContain('History pending');
    wrapper.unmount();
  });

  it('loads only charts until Latest values is expanded and reuses the loaded values', async () => {
    const pendingSummaries = deferred<unknown[]>();
    healthApiMock.status.mockReturnValue(new Promise(() => {}));
    healthApiMock.summaries.mockReturnValueOnce(pendingSummaries.promise);
    const wrapper = mountOverview();
    await flushPromises();
    expect(wrapper.findAll('.health-chart-card')).toHaveLength(3);
    expect(wrapper.text()).not.toContain('Updating latest values');
    expect(wrapper.text()).not.toContain('Loading data summaries');
    expect(wrapper.text()).not.toContain('Google Health is disconnected');
    expect(wrapper.find('.admin-status-pill').exists()).toBe(false);
    await vi.advanceTimersByTimeAsync(32_000);
    expect(healthApiMock.status).not.toHaveBeenCalled();
    expect(healthApiMock.summaries).not.toHaveBeenCalled();
    expect(healthApiMock.latestMetrics).not.toHaveBeenCalled();

    await toggleLatest(wrapper);
    expect(wrapper.text()).toContain('Updating latest values');
    const loadedSummaries = [{ dataType: 'steps', representation: 'DAILY_ROLLUP', recordCount: 30 }];
    pendingSummaries.resolve(loadedSummaries);
    await flushPromises();
    expect(wrapper.get('.health-summaries').text()).toContain('8,240');
    expect(healthApiMock.latestMetrics).toHaveBeenCalledWith(loadedSummaries);
    await toggleLatest(wrapper, false);
    await toggleLatest(wrapper);
    expect(healthApiMock.summaries).toHaveBeenCalledOnce();
    expect(healthApiMock.latestMetrics).toHaveBeenCalledOnce();
    expect(healthApiMock.groupedMetrics).toHaveBeenCalledOnce();
    expect(healthApiMock.status).not.toHaveBeenCalled();
  });

  it('keeps a summary failure local and retries it without reloading charts', async () => {
    healthApiMock.summaries.mockRejectedValueOnce(new Error('Summary unavailable'));
    const wrapper = mountOverview();
    await toggleLatest(wrapper);
    expect(wrapper.findAll('.health-chart-card')).toHaveLength(3);
    expect(wrapper.get('.health-latest').text()).toContain('Summary unavailable');
    expect(healthApiMock.latestMetrics).not.toHaveBeenCalled();
    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Retry summaries')!
      .trigger('click');
    await flushPromises();
    expect(wrapper.get('.health-summaries').text()).toContain('8,240');
    expect(wrapper.text()).not.toContain('Summary unavailable');
    expect(healthApiMock.groupedMetrics).toHaveBeenCalledOnce();
    expect(healthApiMock.summaries).toHaveBeenCalledTimes(2);
    expect(healthApiMock.status).not.toHaveBeenCalled();
  });

  it('does not request status or expose status errors owned by another screen', async () => {
    const pinia = createPinia();
    useApplicationStatusStore(pinia).sectionStates[APPLICATION_STATUS_SECTION.GOOGLE_HEALTH].lastError =
      'Status unavailable';
    healthApiMock.status.mockRejectedValue(new Error('Status unavailable'));
    const wrapper = mountOverview(undefined, pinia);
    await flushPromises();
    expect(wrapper.findAll('.health-chart-card')).toHaveLength(3);
    expect(wrapper.find('[role="alert"]').exists()).toBe(false);
    expect(wrapper.text()).not.toContain('Retry status');
    expect(healthApiMock.status).not.toHaveBeenCalled();
  });

  it('discards pending latest requests when closed and keeps a reopened Overview chart-only', async () => {
    const oldSummaries = deferred<unknown[]>();
    healthApiMock.summaries.mockReturnValueOnce(oldSummaries.promise);
    const wrapper = mountOverview();
    await flushPromises();
    await toggleLatest(wrapper);
    await wrapper.setProps({ active: false });
    await wrapper.setProps({ active: true });
    await flushPromises();
    oldSummaries.resolve([{ dataType: 'weight', representation: 'RAW', recordCount: 99 }]);
    await flushPromises();
    expect(wrapper.get('.health-latest').element.open).toBe(false);
    expect(healthApiMock.summaries).toHaveBeenCalledOnce();
    expect(healthApiMock.latestMetrics).not.toHaveBeenCalled();
    expect(healthApiMock.status).not.toHaveBeenCalled();
    await toggleLatest(wrapper);
    expect(healthApiMock.latestMetrics).toHaveBeenCalledOnce();
    expect(healthApiMock.latestMetrics.mock.calls[0]![0]).not.toContainEqual(
      expect.objectContaining({ dataType: 'weight' })
    );
  });

  it('does not start a latest-values read if its summaries finish after the section closes', async () => {
    const pendingSummaries = deferred<unknown[]>();
    healthApiMock.summaries.mockReturnValueOnce(pendingSummaries.promise);
    const wrapper = mountOverview();
    await flushPromises();
    await toggleLatest(wrapper);
    await toggleLatest(wrapper, false);
    pendingSummaries.resolve([{ dataType: 'weight', representation: 'RAW', recordCount: 99 }]);
    await flushPromises();
    expect(healthApiMock.latestMetrics).not.toHaveBeenCalled();
    expect(wrapper.text()).not.toContain('Updating latest values');
    await toggleLatest(wrapper);
    expect(healthApiMock.summaries).toHaveBeenCalledTimes(2);
    expect(healthApiMock.latestMetrics).toHaveBeenCalledOnce();
  });

  it('shows an empty latest-values section without reading metrics when the catalogue is empty', async () => {
    healthApiMock.summaries.mockResolvedValue([]);
    const wrapper = mountOverview();
    await flushPromises();
    await toggleLatest(wrapper);
    expect(wrapper.get('.health-latest').text()).toContain('No latest values available');
    expect(healthApiMock.latestMetrics).not.toHaveBeenCalled();
  });

  it('loads chart series as the main view and keeps data details secondary', async () => {
    const wrapper = mountOverview();
    await flushPromises();

    expect(wrapper.get('.health-toolbar').exists()).toBe(true);
    expect(wrapper.get('.health-latest').element.tagName).toBe('DETAILS');
    expect(wrapper.get('.health-latest').attributes('open')).toBeUndefined();
    expect(wrapper.text()).toContain('Steps');
    expect(wrapper.text()).toContain('Heart rate');
    expect(wrapper.text()).toContain('Sleep');
    expect(wrapper.findAll('.highcharts-stub')).toHaveLength(3);
    expect(wrapper.getComponent(HighchartsStub).props('options').accessibility.description).toContain(
      'Steps over time'
    );
    expect(wrapper.text()).toContain('Open data details');
    expect(healthApiMock.groupedMetrics).toHaveBeenCalledOnce();
    expect(healthApiMock.groupedMetrics).toHaveBeenCalledWith(
      expect.any(String),
      expect.any(String),
      'AUTO',
      expect.any(String)
    );

    await wrapper
      .findAll('button')
      .find((candidate) => candidate.text() === 'Open data details')!
      .trigger('click');
    expect(wrapper.emitted('open-data-details')).toHaveLength(1);
  });

  it('keeps an empty chart period usable without requesting connector status or catalogue counts', async () => {
    healthApiMock.groupedMetrics.mockResolvedValue(groupedResponse([]));
    const wrapper = mountOverview();
    await flushPromises();
    expect(wrapper.get('.health-toolbar').exists()).toBe(true);
    expect(wrapper.get('.health-empty-charts').text()).toContain('period and category');
    expect(wrapper.get('.health-empty-charts').text()).toContain('Manage Google Health');
    expect(wrapper.get('.health-empty-charts').text()).not.toContain('set it up');
    expect(healthApiMock.status).not.toHaveBeenCalled();
    expect(healthApiMock.summaries).not.toHaveBeenCalled();
  });

  it('keeps a cached disconnected mirror accessible in an empty chart period without querying totals', async () => {
    const pinia = createPinia();
    useApplicationStatusStore(pinia).googleHealth = {
      configurationState: 'CONFIGURED',
      connectionState: 'DISCONNECTED',
      mirrorSummary: { recordCount: 100 },
    };
    healthApiMock.groupedMetrics.mockResolvedValue(groupedResponse([]));
    const wrapper = mountOverview(undefined, pinia);
    await flushPromises();
    expect(wrapper.get('.health-toolbar').exists()).toBe(true);
    expect(wrapper.get('.health-empty-charts').text()).toContain('No chartable values');
    expect(wrapper.text()).toContain('Stored local data remains available');
    await toggleLatest(wrapper);
    expect(wrapper.get('.health-summaries').text()).toContain('8,240');
    expect(healthApiMock.status).not.toHaveBeenCalled();
  });

  it('discards a latest-values response that finishes after the section closes', async () => {
    const oldLatest = deferred<unknown[]>();
    healthApiMock.latestMetrics.mockReturnValueOnce(oldLatest.promise);
    const wrapper = mountOverview();
    await flushPromises();
    await toggleLatest(wrapper);
    await toggleLatest(wrapper, false);
    await toggleLatest(wrapper);
    const newValues = wrapper.get('.health-summaries').text();
    oldLatest.resolve([{ dataType: 'weight', title: 'Stale latest values', points: [] }]);
    await flushPromises();
    expect(wrapper.get('.health-summaries').text()).toBe(newValues);
    expect(wrapper.text()).not.toContain('Stale latest values');
    expect(healthApiMock.latestMetrics).toHaveBeenCalledTimes(2);
  });

  it('does not add status polling for running syncs or reopened Overview panels', async () => {
    healthApiMock.status.mockResolvedValue({
      configurationState: 'CONFIGURED',
      connectionState: 'CONNECTED',
      syncStates: [],
      job: { id: 'active-sync', state: 'RUNNING', recordsProcessed: 400_000 },
    });
    const pinia = await preloadStatus();
    const first = mountOverview(undefined, pinia);
    await flushPromises();
    await vi.advanceTimersByTimeAsync(1_000);
    await first.setProps({ active: false });
    await first.setProps({ active: true });
    const second = mountOverview(undefined, pinia);
    await flushPromises();
    await vi.advanceTimersByTimeAsync(32_000);
    expect(healthApiMock.status).not.toHaveBeenCalled();
    expect(healthApiMock.summaries).not.toHaveBeenCalled();
    first.unmount();
    second.unmount();
  });

  it('changes period and metric category without loading raw records', async () => {
    const wrapper = mountOverview();
    await flushPromises();

    wrapper.getComponent(HealthDateRangeControl).vm.$emit('preset', '7D');
    await wrapper.vm.$nextTick();
    await flushPromises();
    expect(healthApiMock.groupedMetrics).toHaveBeenCalledTimes(2);
    const [start, end] = healthApiMock.groupedMetrics.mock.calls.at(-1)!;
    expect(new Date(end).getTime() - new Date(start).getTime()).toBe(7 * 24 * 60 * 60 * 1000);

    await wrapper
      .findAll('button')
      .find((candidate) => candidate.text().includes('Heart & vitals'))!
      .trigger('click');
    expect(wrapper.find('.health-chart-grid').text()).toContain('Heart rate');
    expect(wrapper.find('.health-chart-grid').text()).not.toContain('Steps');
    expect(Object.keys(healthApiMock)).toEqual(['status', 'summaries', 'groupedMetrics', 'latestMetrics']);
  });

  it('keeps the latest cards fixed while Trends changes to a historical or empty period', async () => {
    const wrapper = mountOverview();
    await toggleLatest(wrapper);
    const latestText = wrapper.get('.health-summaries').text();
    expect(wrapper.get('.health-trends .date-range-stub').exists()).toBe(true);
    expect(wrapper.find('.health-latest .date-range-stub').exists()).toBe(false);

    healthApiMock.groupedMetrics.mockResolvedValue(
      groupedResponse([
        {
          dataType: 'steps',
          title: 'Steps',
          category: 'ACTIVITY',
          unit: 'steps',
          chartType: 'COLUMN',
          decimals: 0,
          points: [{ time: new Date('2020-01-01T00:00:00Z'), value: 123 }],
        },
      ])
    );
    wrapper.getComponent(HealthDateRangeControl).vm.$emit('custom', { start: '2020-01-01', end: '2020-01-02' });
    await flushPromises();

    expect(wrapper.get('.health-summaries').text()).toBe(latestText);
    expect(wrapper.get('.health-trends').text()).toContain('123');
    expect(wrapper.get('.health-trends .date-range-stub').text()).toContain('2020');
    expect(healthApiMock.latestMetrics).toHaveBeenCalledOnce();

    healthApiMock.groupedMetrics.mockResolvedValue(groupedResponse([]));
    wrapper.getComponent(HealthDateRangeControl).vm.$emit('preset', '7D');
    await wrapper.vm.$nextTick();
    await flushPromises();
    expect(wrapper.get('.health-summaries').text()).toBe(latestText);
    expect(wrapper.get('.health-empty-charts').text()).toContain('No chartable values');
    expect(healthApiMock.latestMetrics).toHaveBeenCalledOnce();
  });

  it('retries latest values independently when their request fails', async () => {
    healthApiMock.latestMetrics.mockRejectedValueOnce(new Error('Latest values unavailable.'));
    const wrapper = mountOverview();
    await toggleLatest(wrapper);
    expect(wrapper.get('.health-latest').text()).toContain('Latest values unavailable.');
    expect(wrapper.findAll('.health-chart-card')).toHaveLength(3);

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Retry latest values')!
      .trigger('click');
    await flushPromises();
    expect(wrapper.get('.health-summaries').text()).toContain('8,240');
    expect(wrapper.get('.health-latest').text()).not.toContain('Latest values unavailable.');
    expect(healthApiMock.latestMetrics).toHaveBeenCalledTimes(2);
    expect(healthApiMock.groupedMetrics).toHaveBeenCalledOnce();
  });

  it('expands a chart and restores the period, category, scroll position, and focus on Escape', async () => {
    const wrapper = mountOverview(document.body);
    await flushPromises();
    wrapper.getComponent(HealthDateRangeControl).vm.$emit('preset', '7D');
    await wrapper.vm.$nextTick();
    await flushPromises();
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Heart & vitals'))!
      .trigger('click');
    const overview = wrapper.get<HTMLElement>('.health-overview');
    overview.element.scrollTop = 320;
    const expand = wrapper.get('button[aria-label="Expand Heart rate chart"]');
    const requestCount = healthApiMock.groupedMetrics.mock.calls.length;

    await expand.trigger('click');

    expect(overview.isVisible()).toBe(false);
    const expanded = wrapper.get('.health-expanded-chart');
    expect(expanded.text()).toContain('Last 7 days');
    expect(expanded.getComponent(HighchartsStub).props('options').chart.height).toBeNull();
    expect(expanded.find('.health-chart-expand').exists()).toBe(false);
    expect(document.activeElement).toBe(expanded.get('button').element);

    await expanded.get('button').trigger('keydown', { key: 'Escape' });

    expect(wrapper.find('.health-expanded-chart').exists()).toBe(false);
    expect(overview.isVisible()).toBe(true);
    expect(overview.element.scrollTop).toBe(320);
    expect(document.activeElement).toBe(expand.element);
    expect(wrapper.getComponent(HealthDateRangeControl).props('preset')).toBe('7D');
    expect(wrapper.get('.health-categories__active').text()).toContain('Heart & vitals');
    expect(healthApiMock.groupedMetrics).toHaveBeenCalledTimes(requestCount);
  });

  it('expands any metric and returns with the back button or when the tool closes', async () => {
    const wrapper = mountOverview();
    await flushPromises();

    for (const title of ['Steps', 'Sleep', 'Heart rate']) {
      await wrapper.get(`button[aria-label="Expand ${title} chart"]`).trigger('click');
      expect(wrapper.get('.health-expanded-chart h3').text()).toBe(title);
      await wrapper.get('.health-chart-back').trigger('click');
      expect(wrapper.find('.health-expanded-chart').exists()).toBe(false);
    }

    await wrapper.get('button[aria-label="Expand Sleep chart"]').trigger('click');
    await wrapper.setProps({ active: false });
    expect(wrapper.find('.health-expanded-chart').exists()).toBe(false);
  });

  it('loads an inclusive custom date range beyond one year', async () => {
    const wrapper = mountOverview();
    await flushPromises();

    wrapper.getComponent(HealthDateRangeControl).vm.$emit('custom', { start: '2021-01-01', end: '2026-01-01' });
    await flushPromises();

    expect(healthApiMock.groupedMetrics).toHaveBeenCalledTimes(2);
    const [start, end] = healthApiMock.groupedMetrics.mock.calls.at(-1)!;
    expect(new Date(`${start}T00:00:00`).getFullYear()).toBe(2021);
    expect(new Date(`${start}T00:00:00`).getMonth()).toBe(0);
    expect(new Date(`${start}T00:00:00`).getDate()).toBe(1);
    expect(new Date(`${end}T00:00:00`).getFullYear()).toBe(2026);
    expect(new Date(`${end}T00:00:00`).getMonth()).toBe(0);
    expect(new Date(`${end}T00:00:00`).getDate()).toBe(2);
    expect(wrapper.text()).toContain('2021');
    expect(wrapper.text()).toContain('2026');
  });

  it('keeps mode changes local and preserves pinned selection through expansion', async () => {
    const response = groupedResponse([
      {
        dataType: 'steps',
        title: 'Steps',
        category: 'ACTIVITY',
        unit: 'steps',
        decimals: 0,
        points: [
          { time: new Date('2026-08-27T12:00:00'), value: 100 },
          { time: new Date('2026-08-28T12:00:00'), value: 200 },
        ],
      },
    ]);
    response.resolvedGrouping = 'WEEK';
    response.series![0]!.grouping = 'WEEK';
    healthApiMock.groupedMetrics.mockResolvedValue(response);
    const wrapper = mountOverview();
    await flushPromises();
    await wrapper.get('.health-chart-expand').trigger('click');
    await wrapper.get('.health-expanded-chart .health-bucket-navigation select').setValue('0');
    const details = wrapper.get('.health-expanded-chart .health-bucket-details').text();
    await wrapper
      .findAll('.health-expanded-chart .health-chart-modes button')
      .find((button) => button.text() === 'Daily variation')!
      .trigger('click');
    expect(wrapper.get('.health-expanded-chart .health-bucket-details').text()).toBe(details);
    expect(localStorage.getItem('mtl.health.chartModes')).toContain('RANGE');
    await wrapper.get('.health-chart-back').trigger('click');
    await wrapper.get('.health-chart-expand').trigger('click');
    expect(wrapper.get('.health-expanded-chart .health-bucket-navigation select').element.value).toBe('0');
    expect(wrapper.get('.health-expanded-chart .health-chart-modes [aria-pressed="true"]').text()).toBe(
      'Daily variation'
    );
    expect(healthApiMock.groupedMetrics).toHaveBeenCalledOnce();
  });

  it('commits labels with data and discards late responses after date or grouping changes', async () => {
    const wrapper = mountOverview();
    await flushPromises();
    let resolveOld!: (value: unknown) => void;
    let resolveNew!: (value: unknown) => void;
    healthApiMock.groupedMetrics
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveOld = resolve;
          })
      )
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveNew = resolve;
          })
      );
    wrapper.getComponent(HealthDateRangeControl).vm.$emit('preset', '7D');
    await wrapper.vm.$nextTick();
    wrapper.getComponent(HealthDateRangeControl).vm.$emit('preset', '1Y');
    await wrapper.vm.$nextTick();
    expect(wrapper.get('.health-displayed-period').text()).toBe('Showing Last 30 days');
    resolveNew({ ...groupedResponse([]), startDate: '2025-08-30', endDate: '2026-08-30', resolvedGrouping: 'MONTH' });
    await flushPromises();
    expect(wrapper.get('.health-displayed-period').text()).toBe('Showing Last 12 months');
    expect(wrapper.getComponent(HealthDateRangeControl).props('preset')).toBe('1Y');
    resolveOld(groupedResponse([{ dataType: 'weight', title: 'Stale metric', points: [] }]));
    await flushPromises();
    expect(wrapper.get('.health-displayed-period').text()).toBe('Showing Last 12 months');
    expect(wrapper.getComponent(HealthDateRangeControl).props('preset')).toBe('1Y');
    expect(wrapper.text()).not.toContain('Stale metric');
    await wrapper.get('.health-grouping-control button:last-child').trigger('click');
    await flushPromises();
    expect(healthApiMock.groupedMetrics.mock.calls.at(-1)?.[2]).toBe('YEAR');
    expect(localStorage.getItem('mtl.health.grouping')).toBe('YEAR');
  });

  it('shows the disabled state after an independent chart read confirms an empty mirror', async () => {
    healthApiMock.status.mockResolvedValue({
      configurationState: 'DISABLED',
      connectionState: 'DISCONNECTED',
      syncStates: [],
      job: { state: 'IDLE' },
    });
    healthApiMock.summaries.mockResolvedValue([]);
    healthApiMock.groupedMetrics.mockResolvedValue(groupedResponse([]));

    const wrapper = mountOverview(undefined, await preloadStatus());
    await flushPromises();

    expect(wrapper.text()).toContain('Google Health is disabled');
    expect(healthApiMock.groupedMetrics).toHaveBeenCalledOnce();
    expect(healthApiMock.latestMetrics).not.toHaveBeenCalled();
  });

  it('loads generated demo charts and labels them as synthetic', async () => {
    healthApiMock.status.mockResolvedValue({
      syntheticData: true,
      configurationState: 'DISABLED',
      connectionState: 'DISCONNECTED',
      mirrorSummary: { recordCount: 10 },
      job: { state: 'IDLE' },
      syncStates: [],
    });
    const wrapper = mountOverview();
    await wrapper.setProps({ demoMode: true, syntheticData: true });
    await flushPromises();
    expect(wrapper.text()).toContain('Synthetic demo data.');
    expect(wrapper.text()).toContain('fictional health timeline');
    expect(healthApiMock.groupedMetrics).toHaveBeenCalled();
    expect(wrapper.text()).not.toContain('Google Health is disabled');
  });

  it('shows the demo preview without loading or exposing health data', async () => {
    const wrapper = mount(HealthOverviewScreen, {
      props: { active: true, demoMode: true },
      global: {
        plugins: [createPinia()],
        stubs: {
          HealthDateRangeControl: {
            props: ['label', 'preset'],
            template: '<button class="date-range-stub">{{ label }}</button>',
          },
          Button: ButtonStub,
          InputText: InputTextStub,
          RouterLink: RouterLinkStub,
          highcharts: HighchartsStub,
        },
      },
    });
    await flushPromises();

    expect(wrapper.text()).toContain('Demo preview');
    expect(wrapper.text()).toContain('Google Health preview');
    expect(wrapper.text()).toContain('connections are disabled in this public demo');
    expect(wrapper.text()).toContain('cannot access or store data from a Google account');
    expect(wrapper.text()).not.toContain('Open data details');
    expect(healthApiMock.status).not.toHaveBeenCalled();
    expect(healthApiMock.summaries).not.toHaveBeenCalled();
    expect(healthApiMock.groupedMetrics).not.toHaveBeenCalled();
    expect(healthApiMock.latestMetrics).not.toHaveBeenCalled();
  });

  it('keeps a chart failure local and retries only the chart request', async () => {
    healthApiMock.groupedMetrics.mockRejectedValueOnce(new Error('Chart summaries are unavailable.'));
    const wrapper = mountOverview(undefined, await preloadStatus());
    await flushPromises();

    const statusPill = wrapper.get('.admin-status-pill');
    expect(statusPill.text()).toContain('Connected');
    expect(statusPill.classes()).toContain('admin-status-pill--success');
    expect(wrapper.text()).toContain('Chart summaries are unavailable.');
    expect(wrapper.text()).not.toContain('No chartable values were found');

    await wrapper
      .findAll('button')
      .find((candidate) => candidate.text() === 'Retry charts')!
      .trigger('click');
    await flushPromises();

    expect(healthApiMock.groupedMetrics).toHaveBeenCalledTimes(2);
    expect(healthApiMock.status).not.toHaveBeenCalled();
    expect(healthApiMock.summaries).not.toHaveBeenCalled();
    expect(wrapper.text()).not.toContain('Chart summaries are unavailable.');
  });

  it.each(['completed', 'continued'])(
    'refreshes charts when shared status changes after a pass is %s, leaving collapsed latest values unloaded',
    async (transition) => {
      const pinia = createPinia();
      const store = useApplicationStatusStore(pinia);
      store.googleHealth = {
        configurationState: 'CONFIGURED',
        connectionState: 'CONNECTED',
        syncStates: [],
        job: { id: 'active-sync', state: 'RUNNING' },
      };
      const wrapper = mountOverview(undefined, pinia);
      await flushPromises();
      store.googleHealth = {
        ...store.googleHealth,
        job: {
          id: transition === 'continued' ? 'next-pass' : 'active-sync',
          state: transition === 'continued' ? 'RUNNING' : 'COMPLETED',
        },
      };
      await flushPromises();
      expect(healthApiMock.groupedMetrics).toHaveBeenCalledTimes(2);
      expect(healthApiMock.summaries).not.toHaveBeenCalled();
      expect(healthApiMock.latestMetrics).not.toHaveBeenCalled();
      expect(healthApiMock.status).not.toHaveBeenCalled();
      wrapper.unmount();
    }
  );

  it('refreshes expanded latest values on shared sync updates, and defers a closed section until reopened', async () => {
    const pinia = createPinia();
    const store = useApplicationStatusStore(pinia);
    store.googleHealth = { job: { id: 'first-pass', state: 'RUNNING' } };
    const wrapper = mountOverview(undefined, pinia);
    await flushPromises();
    await toggleLatest(wrapper);
    store.googleHealth = { job: { id: 'second-pass', state: 'RUNNING' } };
    await flushPromises();
    expect(healthApiMock.summaries).toHaveBeenCalledTimes(2);
    expect(healthApiMock.latestMetrics).toHaveBeenCalledTimes(2);
    await toggleLatest(wrapper, false);
    store.googleHealth = { job: { id: 'second-pass', state: 'COMPLETED' } };
    await flushPromises();
    expect(healthApiMock.summaries).toHaveBeenCalledTimes(2);
    expect(healthApiMock.latestMetrics).toHaveBeenCalledTimes(2);
    await toggleLatest(wrapper);
    expect(healthApiMock.summaries).toHaveBeenCalledTimes(3);
    expect(healthApiMock.latestMetrics).toHaveBeenCalledTimes(3);
    expect(healthApiMock.status).not.toHaveBeenCalled();
  });
});
