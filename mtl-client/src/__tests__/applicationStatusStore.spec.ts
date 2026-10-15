import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { defineComponent, h, nextTick } from 'vue';
import { mount, RouterLinkStub } from '@vue/test-utils';
import AnimatedCount from '@/components/common/AnimatedCount.vue';
import IndexerStatusTab from '@/components/admin/IndexerStatusTab.vue';
import GoogleHealthSyncProgress from '@/features/health/components/GoogleHealthSyncProgress.vue';
import {
  countAnimationValueAt,
  GOOGLE_HEALTH_COUNT_KEY,
  latestCountSample,
  processingCountKey,
} from '@/utils/countAnimation';
import { STARTUP_REQUEST_TIMEOUT_MS } from '@/utils/startupTiming';
import type { ApplicationStatusResponseDto, JobSnapshot } from 'x8ing-mtl-api-typescript-fetch';
import {
  APPLICATION_STATUS_SECTION,
  useApplicationStatusStore,
  type ApplicationStatusSection,
} from '@/stores/applicationStatusStore';

const getApplicationStatusMock = vi.hoisted(() => vi.fn());

vi.mock('x8ing-mtl-api-typescript-fetch', async () => {
  const actual = await vi.importActual<typeof import('x8ing-mtl-api-typescript-fetch')>(
    'x8ing-mtl-api-typescript-fetch'
  );
  return {
    ...actual,
    ApplicationStatusControllerApi: class {
      getApplicationStatus = getApplicationStatusMock;
    },
  };
});

function response(
  sections: ApplicationStatusSection[],
  values: Partial<ApplicationStatusResponseDto> = {}
): ApplicationStatusResponseDto {
  return {
    generatedAt: new Date(Date.now() - 10),
    included: new Set(sections),
    ...values,
  } as ApplicationStatusResponseDto;
}

async function refreshOnNextTick(
  store: ReturnType<typeof useApplicationStatusStore>,
  sections: ApplicationStatusSection[]
) {
  const request = store.refresh(sections);
  await vi.advanceTimersByTimeAsync(Math.max(0, (store.nextPollAt ?? Date.now()) - Date.now()));
  await request;
}

function mockAllSections() {
  getApplicationStatusMock.mockImplementation(({ include }: { include: ApplicationStatusSection[] }) =>
    Promise.resolve(
      response(include, {
        freshness: { freshnessToken: 'fresh-1' },
        processing: { indexers: [], jobs: [] },
        services: {},
        googleHealth: {},
      })
    )
  );
}

describe('applicationStatusStore', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-09-03T12:00:00Z'));
    getApplicationStatusMock.mockReset();
    setActivePinia(createPinia());
  });

  afterEach(() => {
    useApplicationStatusStore().$dispose();
    vi.restoreAllMocks();
    vi.clearAllTimers();
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it('keeps cached dependency edges while polling live job states and replaces changed metadata', async () => {
    const sections = [APPLICATION_STATUS_SECTION.PROCESSING];
    const first = response(sections, {
      processing: {
        indexers: [],
        jobs: [],
        dependenciesVersion: 'v1',
        dependencies: [
          { fromKind: 'JOB', fromId: 'duplicate', toJob: 'exploration', kind: 'GATE', scope: 'WHOLE_JOB' },
        ],
      },
    });
    getApplicationStatusMock.mockResolvedValueOnce(first);
    const store = useApplicationStatusStore();
    await refreshOnNextTick(store, sections);
    getApplicationStatusMock.mockResolvedValueOnce(
      response(sections, { processing: { indexers: [], jobs: [], dependenciesVersion: 'v1' } })
    );
    await refreshOnNextTick(store, sections);
    expect(getApplicationStatusMock.mock.calls[1]?.[0].dependenciesVersion).toBe('v1');
    expect(store.processing?.dependencies).toEqual(first.processing?.dependencies);
    getApplicationStatusMock.mockResolvedValueOnce(
      response(sections, { processing: { indexers: [], jobs: [], dependenciesVersion: 'v2', dependencies: [] } })
    );
    await refreshOnNextTick(store, sections);
    expect(store.processing?.dependencies).toEqual([]);
    expect(store.processing?.dependenciesVersion).toBe('v2');
  });

  it('seeds health progress from the first active count after an idle login snapshot', async () => {
    vi.stubGlobal('matchMedia', () => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() }));
    let job: JobSnapshot = { state: 'IDLE', recordsProcessed: 0 };
    getApplicationStatusMock.mockImplementation(() =>
      Promise.resolve(response([APPLICATION_STATUS_SECTION.GOOGLE_HEALTH], { googleHealth: { job } }))
    );
    const store = useApplicationStatusStore();
    const view = mount(
      defineComponent({
        setup: () => () =>
          h(GoogleHealthSyncProgress, {
            status: store.googleHealth,
            countAnimation: store.countAnimations.get(GOOGLE_HEALTH_COUNT_KEY) ?? null,
          }),
      })
    );
    await refreshOnNextTick(store, [APPLICATION_STATUS_SECTION.GOOGLE_HEALTH]);
    expect(store.countAnimations.has(GOOGLE_HEALTH_COUNT_KEY)).toBe(false);
    await vi.advanceTimersByTimeAsync(5_000);
    job = { id: 'active-pass', state: 'RUNNING', recordsProcessed: 400_000 };
    await refreshOnNextTick(store, [APPLICATION_STATUS_SECTION.GOOGLE_HEALTH]);
    expect(view.get('.animated-count').text()).toBe('400,000');
    expect(store.countAnimations.get(GOOGLE_HEALTH_COUNT_KEY)?.samples).toHaveLength(1);

    await vi.advanceTimersByTimeAsync(8_000);
    job = { ...job, recordsProcessed: 440_000 };
    await refreshOnNextTick(store, [APPLICATION_STATUS_SECTION.GOOGLE_HEALTH]);
    expect(view.get('.animated-count').text()).toBe('400,000');
    await vi.advanceTimersByTimeAsync(4_000);
    expect(view.get('.animated-count').text()).toBe('420,000');
    view.unmount();
  });

  it.each(['indexer', 'job', 'task'] as const)(
    'does not seed a missing %s count with zero before its first reported value',
    async (scope) => {
      let count: number | undefined;
      const section = scope === 'task' ? APPLICATION_STATUS_SECTION.SERVICES : APPLICATION_STATUS_SECTION.PROCESSING;
      const key =
        scope === 'indexer'
          ? processingCountKey(scope, 'GPS', 'completed')
          : scope === 'job'
            ? processingCountKey(scope, 'activityType', 'done')
            : processingCountKey(scope, 'vector-map-tiles', 'downloaded');
      getApplicationStatusMock.mockImplementation(() =>
        Promise.resolve(
          response([section], {
            processing: {
              indexers: [{ index: 'GPS', pending: 1, completed: count }],
              jobs: [{ job: 'activityType', pending: 1, done: count }],
            },
            services: {
              tileMode: 'local',
              map: { phase: 'downloading', ready: false, downloadTotal: 1_000_000, downloadBytes: count },
            },
          })
        )
      );
      const store = useApplicationStatusStore();
      const view = mount(
        defineComponent({
          setup: () => () => h(AnimatedCount, { value: count ?? 0, animation: store.countAnimations.get(key) ?? null }),
        })
      );
      await refreshOnNextTick(store, [section]);
      expect(store.countAnimations.has(key)).toBe(false);
      expect(view.text()).toBe('—');

      await vi.advanceTimersByTimeAsync(5_000);
      count = 400_000;
      await refreshOnNextTick(store, [section]);
      expect(view.text()).toBe('400,000');
      expect(store.countAnimations.get(key)?.samples).toHaveLength(1);
      await vi.advanceTimersByTimeAsync(8_000);
      count = 440_000;
      await refreshOnNextTick(store, [section]);
      await vi.advanceTimersByTimeAsync(4_000);
      expect(view.text()).toBe('420,000');
      view.unmount();
    }
  );

  it('records timelines without mounted counters and resumes from elapsed time after remounting', async () => {
    vi.stubGlobal('matchMedia', () => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() }));
    let recordsProcessed = 299_901;
    getApplicationStatusMock.mockImplementation(() =>
      Promise.resolve(
        response([APPLICATION_STATUS_SECTION.GOOGLE_HEALTH], {
          googleHealth: { job: { id: 'first-pass', syncId: 'first-sync', state: 'RUNNING', recordsProcessed } },
        })
      )
    );
    const store = useApplicationStatusStore();
    await refreshOnNextTick(store, [APPLICATION_STATUS_SECTION.GOOGLE_HEALTH]);
    const initial = store.countAnimations.get(GOOGLE_HEALTH_COUNT_KEY);
    await vi.advanceTimersByTimeAsync(10_000);
    await refreshOnNextTick(store, [APPLICATION_STATUS_SECTION.GOOGLE_HEALTH]);
    const unchanged = store.countAnimations.get(GOOGLE_HEALTH_COUNT_KEY)!;
    expect(unchanged.samples).toHaveLength(2);
    expect(unchanged.samples[0]).toEqual(initial!.samples[0]);

    await vi.advanceTimersByTimeAsync(20_000);
    recordsProcessed = 329_901;
    await refreshOnNextTick(store, [APPLICATION_STATUS_SECTION.GOOGLE_HEALTH]);
    expect(store.countAnimations.get(GOOGLE_HEALTH_COUNT_KEY)?.samples.map((sample) => sample.value)).toEqual([
      299_901, 329_901,
    ]);
    expect(vi.getTimerCount()).toBe(0);
    await vi.advanceTimersByTimeAsync(10_000);
    const CounterView = defineComponent({
      setup: () => () =>
        h(AnimatedCount, {
          value: store.googleHealth?.job?.recordsProcessed,
          animation: store.countAnimations.get(GOOGLE_HEALTH_COUNT_KEY),
        }),
    });
    const first = mount(CounterView);
    expect(first.text()).toBe('329,901');
    first.unmount();
    expect(vi.getTimerCount()).toBe(0);

    await vi.advanceTimersByTimeAsync(5_000);
    recordsProcessed = 359_901;
    await refreshOnNextTick(store, [APPLICATION_STATUS_SECTION.GOOGLE_HEALTH]);
    expect(store.countAnimations.get(GOOGLE_HEALTH_COUNT_KEY)?.samples.map((sample) => sample.value)).toEqual([
      329_901, 359_901,
    ]);
    await vi.advanceTimersByTimeAsync(5_000);
    const resumed = mount(CounterView);
    expect(resumed.text()).toBe('353,901');
    expect(resumed.get('.animated-count').attributes('aria-label')).toBe('359,901');
    await vi.advanceTimersByTimeAsync(10_000);
    await nextTick();
    expect(resumed.text()).toBe('359,901');

    recordsProcessed = 0;
    await refreshOnNextTick(store, [APPLICATION_STATUS_SECTION.GOOGLE_HEALTH]);
    expect(resumed.text()).toBe('0');
    resumed.unmount();
  });

  it.each(['RUNNING', 'COMPLETED'] as const)(
    'resets a new sync after a %s snapshot when polling misses its initial zero',
    async (state) => {
      vi.stubGlobal('matchMedia', () => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() }));
      let job: JobSnapshot = { id: 'old-pass', syncId: 'old-sync', state, recordsProcessed: 1_000 };
      getApplicationStatusMock.mockImplementation(() =>
        Promise.resolve(response([APPLICATION_STATUS_SECTION.GOOGLE_HEALTH], { googleHealth: { job } }))
      );
      const store = useApplicationStatusStore();
      await refreshOnNextTick(store, [APPLICATION_STATUS_SECTION.GOOGLE_HEALTH]);
      await vi.advanceTimersByTimeAsync(60_000);
      job = { id: 'new-pass', syncId: 'new-sync', state: 'RUNNING', recordsProcessed: 2_000 };
      await refreshOnNextTick(store, [APPLICATION_STATUS_SECTION.GOOGLE_HEALTH]);

      expect(store.countAnimations.get(GOOGLE_HEALTH_COUNT_KEY)).toMatchObject({
        samples: [{ value: 2_000 }],
        resetKey: 'new-sync',
      });
      const view = mount(
        defineComponent({
          setup: () => () =>
            h(AnimatedCount, {
              value: store.googleHealth?.job?.recordsProcessed,
              animation: store.countAnimations.get(GOOGLE_HEALTH_COUNT_KEY),
            }),
        })
      );
      expect(view.text()).toBe('2,000');
      view.unmount();
    }
  );

  it('preserves a sync timeline when an automatic continuation changes the job ID', async () => {
    let job: JobSnapshot = { id: 'first-pass', syncId: 'shared-sync', state: 'RUNNING', recordsProcessed: 1_000 };
    getApplicationStatusMock.mockImplementation(() =>
      Promise.resolve(response([APPLICATION_STATUS_SECTION.GOOGLE_HEALTH], { googleHealth: { job } }))
    );
    const store = useApplicationStatusStore();
    await refreshOnNextTick(store, [APPLICATION_STATUS_SECTION.GOOGLE_HEALTH]);
    await vi.advanceTimersByTimeAsync(30_000);
    job = { ...job, recordsProcessed: 4_000 };
    await refreshOnNextTick(store, [APPLICATION_STATUS_SECTION.GOOGLE_HEALTH]);
    const animation = store.countAnimations.get(GOOGLE_HEALTH_COUNT_KEY);
    await vi.advanceTimersByTimeAsync(8_000);
    job = { ...job, id: 'continuation-pass' };
    await refreshOnNextTick(store, [APPLICATION_STATUS_SECTION.GOOGLE_HEALTH]);

    const continued = store.countAnimations.get(GOOGLE_HEALTH_COUNT_KEY)!;
    expect(continued.samples.slice(0, 2)).toEqual(animation!.samples);
    expect(continued.resetKey).toBe('shared-sync');
    expect(countAnimationValueAt(continued, Date.now())).toBe(4_000);
  });

  it('keeps discovered file counts moving while the processing queue is empty', async () => {
    let completed = 100;
    getApplicationStatusMock.mockImplementation(() =>
      Promise.resolve(
        response([APPLICATION_STATUS_SECTION.PROCESSING], {
          processing: { indexers: [{ index: 'GPS', completed, pending: 0, discoveryInProgress: true }] },
        })
      )
    );
    const store = useApplicationStatusStore();
    await refreshOnNextTick(store, [APPLICATION_STATUS_SECTION.PROCESSING]);
    await vi.advanceTimersByTimeAsync(8_000);
    completed = 180;
    await refreshOnNextTick(store, [APPLICATION_STATUS_SECTION.PROCESSING]);
    const animation = store.countAnimations.get(processingCountKey('indexer', 'GPS', 'completed'))!;
    expect(countAnimationValueAt(animation, Date.now())).toBe(100);
    expect(countAnimationValueAt(animation, Date.now() + 4_000)).toBe(140);
    expect(countAnimationValueAt(animation, Date.now() + 8_000)).toBe(180);
  });

  it('keeps processing timelines separate, settles finished work, and drops removed counters', async () => {
    let completed = 100;
    let pending = 2;
    getApplicationStatusMock.mockImplementation(() =>
      Promise.resolve(
        response([APPLICATION_STATUS_SECTION.PROCESSING], {
          processing: {
            indexers: [{ index: 'GPS', completed, pending }],
            jobs: [{ job: 'activityType', done: 1_000, pending: 2 }],
          },
        })
      )
    );
    const store = useApplicationStatusStore();
    await refreshOnNextTick(store, [APPLICATION_STATUS_SECTION.PROCESSING]);
    await vi.advanceTimersByTimeAsync(8_000);
    completed = 200;
    await refreshOnNextTick(store, [APPLICATION_STATUS_SECTION.PROCESSING]);
    const animation = store.countAnimations.get(processingCountKey('indexer', 'GPS', 'completed'))!;
    expect(countAnimationValueAt(animation, Date.now() + 4_000)).toBe(150);
    expect(latestCountSample(store.countAnimations.get(processingCountKey('job', 'activityType', 'done'))!).value).toBe(
      1_000
    );

    await vi.advanceTimersByTimeAsync(8_000);
    completed = 300;
    pending = 0;
    await refreshOnNextTick(store, [APPLICATION_STATUS_SECTION.PROCESSING]);
    expect(store.countAnimations.get(processingCountKey('indexer', 'GPS', 'completed'))?.samples).toHaveLength(1);
    getApplicationStatusMock.mockResolvedValueOnce(
      response([APPLICATION_STATUS_SECTION.PROCESSING], {
        processing: { indexers: [], jobs: [] },
      })
    );
    await refreshOnNextTick(store, [APPLICATION_STATUS_SECTION.PROCESSING]);
    expect(store.countAnimations.size).toBe(0);
  });

  it('records service counters during downloads and removes their timelines when the phase ends', async () => {
    let downloadBytes = 100;
    let ready = false;
    getApplicationStatusMock.mockImplementation(() =>
      Promise.resolve(
        response([APPLICATION_STATUS_SECTION.SERVICES], {
          services: {
            tileMode: 'local',
            map: { ready, phase: ready ? 'ready' : 'downloading', downloadBytes, downloadTotal: 1_000 },
          },
        })
      )
    );
    const store = useApplicationStatusStore();
    await refreshOnNextTick(store, [APPLICATION_STATUS_SECTION.SERVICES]);
    await vi.advanceTimersByTimeAsync(8_000);
    downloadBytes = 200;
    await refreshOnNextTick(store, [APPLICATION_STATUS_SECTION.SERVICES]);
    const animation = store.countAnimations.get(processingCountKey('task', 'vector-map-tiles', 'downloaded'))!;
    expect(countAnimationValueAt(animation, Date.now() + 4_000)).toBe(150);
    ready = true;
    await refreshOnNextTick(store, [APPLICATION_STATUS_SECTION.SERVICES]);
    expect([...store.countAnimations.keys()].some((key) => key.startsWith('task:vector-map-tiles:'))).toBe(false);
  });

  it('applies successful sections and records numeric timestamps', async () => {
    const generatedAt = new Date('2026-09-03T11:59:59Z');
    getApplicationStatusMock.mockResolvedValueOnce(
      response([APPLICATION_STATUS_SECTION.FRESHNESS, APPLICATION_STATUS_SECTION.PROCESSING], {
        generatedAt,
        freshness: { freshnessToken: 'fresh-1' },
        processing: { indexers: [{ index: 'GPS', pending: 2 }], jobs: [] },
      })
    );
    const store = useApplicationStatusStore();

    await refreshOnNextTick(store, [APPLICATION_STATUS_SECTION.FRESHNESS, APPLICATION_STATUS_SECTION.PROCESSING]);

    expect(store.freshness?.freshnessToken).toBe('fresh-1');
    expect(store.processing?.indexers?.[0]?.pending).toBe(2);
    expect(store.serverGeneratedAt).toBe(generatedAt.getTime());
    expect(store.sectionStates.freshness.lastAttemptAt).toBe(Date.now());
    expect(store.sectionStates.freshness.lastSuccessAt).toBe(Date.now());
    expect(store.sectionStates.freshness.healthy).toBe(true);
  });

  it('uses server sample times with one clock offset across sections despite changing response delays', async () => {
    const startedAt = Date.now();
    const serverStartedAt = startedAt - 120_000;
    const sections = [APPLICATION_STATUS_SECTION.PROCESSING, APPLICATION_STATUS_SECTION.GOOGLE_HEALTH];
    function snapshot(time: number, count: number) {
      return response(sections, {
        generatedAt: new Date(serverStartedAt + time),
        processing: { indexers: [{ index: 'GPS', completed: count, pending: 1 }] },
        googleHealth: { job: { syncId: 'cycle', state: 'RUNNING', recordsProcessed: count } },
      });
    }
    getApplicationStatusMock.mockResolvedValueOnce(snapshot(0, 100));
    const store = useApplicationStatusStore();
    await refreshOnNextTick(store, sections);
    await vi.advanceTimersByTimeAsync(12_000);
    getApplicationStatusMock.mockResolvedValueOnce(snapshot(8_000, 180));
    await refreshOnNextTick(store, sections);
    const keys = [processingCountKey('indexer', 'GPS', 'completed'), GOOGLE_HEALTH_COUNT_KEY];
    for (const key of keys) {
      const history = store.countAnimations.get(key)!;
      expect(latestCountSample(history).sampledAt).toBe(startedAt + 8_000);
      expect(countAnimationValueAt(history, Date.now())).toBe(140);
    }
    await vi.advanceTimersByTimeAsync(8_000);
    getApplicationStatusMock.mockResolvedValueOnce(snapshot(20_000, 300));
    await refreshOnNextTick(store, sections);
    for (const key of keys) {
      const history = store.countAnimations.get(key)!;
      expect(latestCountSample(history).sampledAt).toBe(startedAt + 20_000);
      expect(countAnimationValueAt(history, Date.now())).toBe(220);
    }
  });

  it.each([-300_000, 300_000])(
    'shows processing countdowns in server time with a %i ms browser clock offset',
    async (offset) => {
      const deadline = new Date(Date.now() - offset + 60_000);
      getApplicationStatusMock.mockImplementation(({ include }: { include: ApplicationStatusSection[] }) =>
        Promise.resolve(
          response(include, {
            generatedAt: new Date(Date.now() - offset),
            processing: {
              indexers: [],
              jobs: [
                { job: 'exploration', state: 'QUEUED', pending: 1, nextRunAt: deadline },
                { job: 'energy', state: 'RETRYING', pending: 1, nextRetryAt: deadline },
              ],
            },
          })
        )
      );
      const view = mount(IndexerStatusTab, { global: { stubs: { RouterLink: RouterLinkStub } } });
      try {
        await vi.advanceTimersByTimeAsync(0);
        expect(view.text()).toContain('Starts in 1 minute.');
        expect(view.text()).toContain('Next retry in 1 minute.');

        await vi.advanceTimersByTimeAsync(8_000);
        expect(view.text()).toContain('Starts in 52 seconds.');
        expect(view.text()).toContain('Next retry in 52 seconds.');

        await vi.advanceTimersByTimeAsync(56_000);
        expect(view.text()).toContain('Starts shortly.');
        expect(view.text()).toContain('Retrying shortly.');
      } finally {
        view.unmount();
      }
    }
  );

  it('uses arrival timestamps when a response has no valid server timestamp', async () => {
    const section = APPLICATION_STATUS_SECTION.PROCESSING;
    getApplicationStatusMock.mockResolvedValueOnce(
      response([section], {
        generatedAt: undefined,
        processing: { indexers: [{ index: 'GPS', completed: 100, pending: 1 }] },
      })
    );
    const store = useApplicationStatusStore();
    await refreshOnNextTick(store, [section]);
    await vi.advanceTimersByTimeAsync(8_000);
    getApplicationStatusMock.mockResolvedValueOnce(
      response([section], {
        generatedAt: new Date(Number.NaN),
        processing: { indexers: [{ index: 'GPS', completed: 180, pending: 1 }] },
      })
    );
    await refreshOnNextTick(store, [section]);
    const history = store.countAnimations.get(processingCountKey('indexer', 'GPS', 'completed'))!;
    expect(latestCountSample(history).sampledAt).toBe(Date.now());
    expect(countAnimationValueAt(history, Date.now() + 4_000)).toBe(140);
    expect(store.serverGeneratedAt).toBeNull();
  });

  it('preserves skipped and failed section values', async () => {
    getApplicationStatusMock
      .mockResolvedValueOnce(
        response([APPLICATION_STATUS_SECTION.FRESHNESS, APPLICATION_STATUS_SECTION.SERVICES], {
          freshness: { freshnessToken: 'fresh-1' },
          services: { tileMode: 'local', plannerEnabled: false },
        })
      )
      .mockResolvedValueOnce(
        response([APPLICATION_STATUS_SECTION.FRESHNESS, APPLICATION_STATUS_SECTION.PROCESSING], {
          processing: { indexers: [], jobs: [] },
          errors: [{ section: APPLICATION_STATUS_SECTION.FRESHNESS, code: 'unavailable', message: 'not ready' }],
        })
      );
    const store = useApplicationStatusStore();

    await refreshOnNextTick(store, [APPLICATION_STATUS_SECTION.FRESHNESS, APPLICATION_STATUS_SECTION.SERVICES]);
    const previousServices = store.services;
    await refreshOnNextTick(store, [APPLICATION_STATUS_SECTION.FRESHNESS, APPLICATION_STATUS_SECTION.PROCESSING]);

    expect(store.freshness?.freshnessToken).toBe('fresh-1');
    expect(store.services).toBe(previousServices);
    expect(store.sectionStates.freshness).toMatchObject({ healthy: false, lastError: 'not ready' });
    expect(store.sectionStates.processing.healthy).toBe(true);
    expect(store.sectionStates.services.healthy).toBe(true);
  });

  it('coalesces simultaneously requested sections into one request', async () => {
    getApplicationStatusMock.mockImplementation(({ include }: { include: ApplicationStatusSection[] }) =>
      Promise.resolve(
        response(include, {
          freshness: include.includes(APPLICATION_STATUS_SECTION.FRESHNESS) ? { freshnessToken: 'fresh-1' } : undefined,
          processing: include.includes(APPLICATION_STATUS_SECTION.PROCESSING) ? { indexers: [], jobs: [] } : undefined,
        })
      )
    );
    const store = useApplicationStatusStore();

    const freshnessRequest = store.refresh([APPLICATION_STATUS_SECTION.FRESHNESS]);
    const processingRequest = store.refresh([APPLICATION_STATUS_SECTION.PROCESSING]);
    await vi.advanceTimersByTimeAsync(0);
    await Promise.all([freshnessRequest, processingRequest]);

    expect(getApplicationStatusMock).toHaveBeenCalledTimes(1);
    expect(new Set(getApplicationStatusMock.mock.calls[0]?.[0].include)).toEqual(
      new Set([APPLICATION_STATUS_SECTION.FRESHNESS, APPLICATION_STATUS_SECTION.PROCESSING])
    );
  });

  it('keeps staggered consumers on one shared idle cadence', async () => {
    getApplicationStatusMock.mockImplementation(({ include }: { include: ApplicationStatusSection[] }) =>
      Promise.resolve(
        response(include, {
          freshness: include.includes(APPLICATION_STATUS_SECTION.FRESHNESS) ? { freshnessToken: 'fresh-1' } : undefined,
          processing: include.includes(APPLICATION_STATUS_SECTION.PROCESSING) ? { indexers: [], jobs: [] } : undefined,
        })
      )
    );
    const store = useApplicationStatusStore();
    store.registerPollingConsumer({ [APPLICATION_STATUS_SECTION.PROCESSING]: 60_000 });
    await vi.advanceTimersByTimeAsync(0);
    await vi.advanceTimersByTimeAsync(2_000);
    store.registerPollingConsumer({ [APPLICATION_STATUS_SECTION.FRESHNESS]: 30_000 });
    await vi.advanceTimersByTimeAsync(27_999);
    expect(getApplicationStatusMock).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(1);
    expect(getApplicationStatusMock).toHaveBeenCalledTimes(2);
    expect(new Set(getApplicationStatusMock.mock.calls.at(-1)?.[0].include)).toEqual(
      new Set([APPLICATION_STATUS_SECTION.FRESHNESS, APPLICATION_STATUS_SECTION.PROCESSING])
    );
    await vi.advanceTimersByTimeAsync(60_000);
    expect(getApplicationStatusMock).toHaveBeenCalledTimes(4);
    expect(getApplicationStatusMock.mock.calls.slice(1).every(([request]) => request.include.length === 2)).toBe(true);
  });

  it('coalesces late Settings demand and post-response freshness refreshes without a burst', async () => {
    mockAllSections();
    const store = useApplicationStatusStore();
    store.registerPollingConsumer({ freshness: 30_000, processing: 60_000 });
    await vi.advanceTimersByTimeAsync(0);
    await vi.advanceTimersByTimeAsync(100);
    const demand = { freshness: 8_000, processing: 8_000, services: 8_000, 'google-health': 8_000 };
    const settings = store.registerPollingConsumer(demand);
    const refresh = store.refresh(['freshness']);
    await vi.advanceTimersByTimeAsync(100);
    settings.update(demand);
    await vi.advanceTimersByTimeAsync(7_799);
    expect(getApplicationStatusMock).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(1);
    await refresh;
    await vi.advanceTimersByTimeAsync(32_000);
    expect(getApplicationStatusMock).toHaveBeenCalledTimes(6);
    expect(getApplicationStatusMock.mock.calls.slice(1).every(([request]) => request.include.length === 4)).toBe(true);
    settings.dispose();
    await vi.advanceTimersByTimeAsync(29_999);
    expect(getApplicationStatusMock).toHaveBeenCalledTimes(6);
    await vi.advanceTimersByTimeAsync(1);
    expect(getApplicationStatusMock.mock.calls.at(-1)?.[0].include).toEqual(['freshness', 'processing']);
  });

  it('joins a slow in-flight request that already contains the requested sections', async () => {
    let resolve!: (value: ApplicationStatusResponseDto) => void;
    getApplicationStatusMock.mockImplementation(
      () =>
        new Promise<ApplicationStatusResponseDto>((done) => {
          resolve = done;
        })
    );
    const store = useApplicationStatusStore();
    store.registerPollingConsumer({ freshness: 8_000, processing: 8_000, services: 8_000, 'google-health': 8_000 });
    await vi.advanceTimersByTimeAsync(0);
    await vi.advanceTimersByTimeAsync(10_000);
    const first = store.refresh(['freshness']);
    const second = store.refresh();
    resolve(
      response(Object.values(APPLICATION_STATUS_SECTION), {
        freshness: { freshnessToken: 'fresh-2' },
        processing: {},
        services: {},
        googleHealth: {},
      })
    );
    await Promise.all([first, second]);
    await vi.advanceTimersByTimeAsync(7_999);
    expect(getApplicationStatusMock).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(1);
    expect(getApplicationStatusMock).toHaveBeenCalledTimes(2);
  });

  it('allows only one request in flight and queues newly requested sections', async () => {
    let firstResolve!: (value: ApplicationStatusResponseDto) => void;
    let activeRequests = 0;
    let maxActiveRequests = 0;
    getApplicationStatusMock.mockImplementation(({ include }: { include: ApplicationStatusSection[] }) => {
      activeRequests += 1;
      maxActiveRequests = Math.max(maxActiveRequests, activeRequests);
      if (getApplicationStatusMock.mock.calls.length === 1) {
        return new Promise<ApplicationStatusResponseDto>((resolve) => {
          firstResolve = (value) => {
            activeRequests -= 1;
            resolve(value);
          };
        });
      }
      activeRequests -= 1;
      return Promise.resolve(response(include, { processing: { indexers: [], jobs: [] } }));
    });
    const store = useApplicationStatusStore();

    const first = store.refresh([APPLICATION_STATUS_SECTION.FRESHNESS]);
    await vi.advanceTimersByTimeAsync(0);
    const second = store.refresh([APPLICATION_STATUS_SECTION.PROCESSING]);
    expect(getApplicationStatusMock).toHaveBeenCalledTimes(1);

    firstResolve(response([APPLICATION_STATUS_SECTION.FRESHNESS], { freshness: { freshnessToken: 'fresh-1' } }));
    await vi.advanceTimersByTimeAsync(7_999);
    expect(getApplicationStatusMock).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(1);
    await Promise.all([first, second]);

    expect(getApplicationStatusMock).toHaveBeenCalledTimes(2);
    expect(maxActiveRequests).toBe(1);
  });

  it('queues forced refreshes after an in-flight response without bypassing the shared cadence', async () => {
    let resolve!: (value: ApplicationStatusResponseDto) => void;
    mockAllSections();
    getApplicationStatusMock.mockImplementationOnce(
      () =>
        new Promise<ApplicationStatusResponseDto>((done) => {
          resolve = done;
        })
    );
    const store = useApplicationStatusStore();
    store.registerPollingConsumer({ freshness: 8_000, processing: 8_000 });
    await vi.advanceTimersByTimeAsync(0);
    const forced = store.force(APPLICATION_STATUS_SECTION.FRESHNESS);
    resolve(response(['freshness', 'processing'], { freshness: { freshnessToken: 'before-change' }, processing: {} }));
    await vi.advanceTimersByTimeAsync(7_999);
    expect(getApplicationStatusMock).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(1);
    await forced;
    expect(getApplicationStatusMock).toHaveBeenCalledTimes(2);
    expect(store.freshness?.freshnessToken).toBe('fresh-1');
    expect(getApplicationStatusMock.mock.calls[1]?.[0].include).toEqual(['freshness', 'processing']);
  });

  it('runs an interactive refresh immediately instead of waiting for the shared cadence', async () => {
    mockAllSections();
    const store = useApplicationStatusStore();
    store.registerPollingConsumer({ [APPLICATION_STATUS_SECTION.SERVICES]: 8_000 });
    await vi.advanceTimersByTimeAsync(0);

    const refresh = store.refresh([APPLICATION_STATUS_SECTION.GOOGLE_HEALTH], { immediate: true });
    await vi.advanceTimersByTimeAsync(0);
    await refresh;

    expect(getApplicationStatusMock).toHaveBeenCalledTimes(2);
    expect(new Set(getApplicationStatusMock.mock.calls[1]?.[0].include)).toEqual(
      new Set([APPLICATION_STATUS_SECTION.SERVICES, APPLICATION_STATUS_SECTION.GOOGLE_HEALTH])
    );
    expect(store.googleHealth).toEqual({});
  });

  it('settles pending refresh callers and cancels their timer when disposed', async () => {
    mockAllSections();
    const store = useApplicationStatusStore();
    await refreshOnNextTick(store, ['freshness']);
    const pending = store.refresh(['freshness']);
    store.$dispose();
    await pending;
    await vi.advanceTimersByTimeAsync(60_000);
    expect(getApplicationStatusMock).toHaveBeenCalledTimes(1);
  });

  it('bounds a never-resolving request, settles joined refreshes, and retries through existing backoff', async () => {
    getApplicationStatusMock
      .mockImplementationOnce(() => new Promise<ApplicationStatusResponseDto>(() => {}))
      .mockResolvedValue(response(['freshness'], { freshness: { freshnessToken: 'fresh-next' } }));
    const store = useApplicationStatusStore();
    const consumer = store.registerPollingConsumer({ freshness: 8_000 });
    const first = store.refresh(['freshness'], { immediate: true });
    await vi.advanceTimersByTimeAsync(0);
    const signal = getApplicationStatusMock.mock.calls[0][1].signal as AbortSignal;
    const joined = store.refresh(['freshness'], { immediate: true });
    await vi.advanceTimersByTimeAsync(STARTUP_REQUEST_TIMEOUT_MS - 1);
    expect(signal.aborted).toBe(false);
    expect(store.sectionStates.freshness.loading).toBe(true);
    await vi.advanceTimersByTimeAsync(1);
    await Promise.all([first, joined]);
    expect(signal.aborted).toBe(true);
    expect(store.sectionStates.freshness.loading).toBe(false);
    expect(store.sectionStates.freshness.lastError).toBe('Application status request timed out');
    expect(store.inFlightSections).toEqual([]);
    expect(store.consecutiveNetworkFailures).toBe(1);
    expect(store.pollingMode).toBe('backoff');
    expect(getApplicationStatusMock).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(8_000);
    expect(getApplicationStatusMock).toHaveBeenCalledTimes(2);
    expect(store.freshness?.freshnessToken).toBe('fresh-next');
    expect(store.sectionStates.freshness.healthy).toBe(true);
    expect(store.consecutiveNetworkFailures).toBe(0);
    expect(store.backoffUntil).toBeNull();
    consumer.dispose();
    expect(vi.getTimerCount()).toBe(0);
  });

  it.each(['success', 'failure'])('ignores a late timed-out %s after a newer request succeeds', async (outcome) => {
    let resolveOld!: (value: ApplicationStatusResponseDto) => void;
    let rejectOld!: (error: Error) => void;
    getApplicationStatusMock
      .mockImplementationOnce(
        () =>
          new Promise<ApplicationStatusResponseDto>((resolve, reject) => {
            resolveOld = resolve;
            rejectOld = reject;
          })
      )
      .mockResolvedValue(response(['freshness'], { freshness: { freshnessToken: 'fresh-new' } }));
    const store = useApplicationStatusStore();
    const first = store.refresh(['freshness'], { immediate: true });
    await vi.advanceTimersByTimeAsync(0);
    await vi.advanceTimersByTimeAsync(STARTUP_REQUEST_TIMEOUT_MS);
    await first;
    const retry = store.refresh(['freshness'], { immediate: true });
    await vi.advanceTimersByTimeAsync(8_000);
    await retry;
    const lastSuccess = store.sectionStates.freshness.lastSuccessAt;
    const generatedAt = store.serverGeneratedAt;
    if (outcome === 'success')
      resolveOld(response(['freshness'], { generatedAt: new Date(0), freshness: { freshnessToken: 'fresh-old' } }));
    else rejectOld(new Error('Delayed old failure'));
    await vi.advanceTimersByTimeAsync(0);
    expect(store.freshness?.freshnessToken).toBe('fresh-new');
    expect(store.sectionStates.freshness.healthy).toBe(true);
    expect(store.sectionStates.freshness.lastSuccessAt).toBe(lastSuccess);
    expect(store.sectionStates.freshness.lastError).toBeNull();
    expect(store.serverGeneratedAt).toBe(generatedAt);
    expect(store.consecutiveNetworkFailures).toBe(0);
    expect(store.inFlightSections).toEqual([]);
    expect(vi.getTimerCount()).toBe(0);
  });

  it('settles active and queued callers on disposal even when transport ignores cancellation', async () => {
    let resolveOld!: (value: ApplicationStatusResponseDto) => void;
    getApplicationStatusMock.mockImplementationOnce(
      () =>
        new Promise<ApplicationStatusResponseDto>((resolve) => {
          resolveOld = resolve;
        })
    );
    const store = useApplicationStatusStore();
    const first = store.refresh(['freshness'], { immediate: true });
    await vi.advanceTimersByTimeAsync(0);
    const joined = store.refresh(['freshness']);
    const queued = store.refresh(['processing']);
    const signal = getApplicationStatusMock.mock.calls[0][1].signal as AbortSignal;
    store.$dispose();
    await Promise.all([first, joined, queued]);
    expect(signal.aborted).toBe(true);
    expect(store.inFlightSections).toEqual([]);
    expect(store.sectionStates.freshness.loading).toBe(false);
    expect(vi.getTimerCount()).toBe(0);
    resolveOld(response(['freshness'], { freshness: { freshnessToken: 'ignored' } }));
    await vi.advanceTimersByTimeAsync(STARTUP_REQUEST_TIMEOUT_MS);
    expect(store.freshness).toBeNull();
    expect(store.consecutiveNetworkFailures).toBe(0);
    expect(getApplicationStatusMock).toHaveBeenCalledOnce();
  });

  it('changes processing cadence while work is pending and cleans up consumers', async () => {
    getApplicationStatusMock
      .mockResolvedValueOnce(
        response([APPLICATION_STATUS_SECTION.PROCESSING], {
          processing: { indexers: [{ index: 'GPS', pending: 1 }], jobs: [] },
        })
      )
      .mockResolvedValue(
        response([APPLICATION_STATUS_SECTION.PROCESSING], {
          processing: { indexers: [{ index: 'GPS', pending: 0 }], jobs: [] },
        })
      );
    const store = useApplicationStatusStore();
    const consumer = store.registerPollingConsumer({ [APPLICATION_STATUS_SECTION.PROCESSING]: 60_000 });

    await vi.advanceTimersByTimeAsync(0);
    expect(getApplicationStatusMock).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(7_999);
    expect(getApplicationStatusMock).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(1);
    expect(getApplicationStatusMock).toHaveBeenCalledTimes(2);

    consumer.dispose();
    expect(store.nextPollAt).toBeNull();
    await vi.advanceTimersByTimeAsync(60_000);
    expect(getApplicationStatusMock).toHaveBeenCalledTimes(2);
  });

  it('keeps all demanded sections on an eight-second cadence even when health requests faster polling', async () => {
    getApplicationStatusMock.mockImplementation(({ include }: { include: ApplicationStatusSection[] }) =>
      Promise.resolve(
        response(include, {
          freshness: include.includes(APPLICATION_STATUS_SECTION.FRESHNESS) ? { freshnessToken: 'fresh-1' } : undefined,
          processing: include.includes(APPLICATION_STATUS_SECTION.PROCESSING) ? { indexers: [], jobs: [] } : undefined,
          services: include.includes(APPLICATION_STATUS_SECTION.SERVICES)
            ? { tileMode: 'local', plannerEnabled: false }
            : undefined,
          googleHealth: include.includes(APPLICATION_STATUS_SECTION.GOOGLE_HEALTH)
            ? {
                configurationState: 'CONFIGURED',
                connectionState: 'CONNECTED',
                job: { id: 'active-sync', state: 'RUNNING', recordsProcessed: 400_000 },
              }
            : undefined,
        })
      )
    );
    const store = useApplicationStatusStore();
    store.registerPollingConsumer({
      [APPLICATION_STATUS_SECTION.FRESHNESS]: 8_000,
      [APPLICATION_STATUS_SECTION.PROCESSING]: 8_000,
      [APPLICATION_STATUS_SECTION.SERVICES]: 8_000,
      [APPLICATION_STATUS_SECTION.GOOGLE_HEALTH]: 2_000,
    });

    await vi.advanceTimersByTimeAsync(0);
    await vi.advanceTimersByTimeAsync(7_999);
    expect(getApplicationStatusMock).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(1);
    expect(getApplicationStatusMock).toHaveBeenCalledTimes(2);
    await vi.advanceTimersByTimeAsync(24_000);

    expect(getApplicationStatusMock).toHaveBeenCalledTimes(5);
    expect(getApplicationStatusMock.mock.calls.every(([request]) => request.include.length === 4)).toBe(true);
    expect(new Set(getApplicationStatusMock.mock.calls[4]?.[0].include)).toEqual(
      new Set(Object.values(APPLICATION_STATUS_SECTION))
    );
  });

  it('pauses while hidden and refreshes overdue sections after becoming visible', async () => {
    let visibility: DocumentVisibilityState = 'visible';
    vi.spyOn(document, 'visibilityState', 'get').mockImplementation(() => visibility);
    getApplicationStatusMock.mockResolvedValue(
      response([APPLICATION_STATUS_SECTION.FRESHNESS], { freshness: { freshnessToken: 'fresh-1' } })
    );
    const store = useApplicationStatusStore();
    store.registerPollingConsumer({ [APPLICATION_STATUS_SECTION.FRESHNESS]: 30_000 });
    await vi.advanceTimersByTimeAsync(0);

    visibility = 'hidden';
    document.dispatchEvent(new Event('visibilitychange'));
    await vi.advanceTimersByTimeAsync(31_000);
    expect(getApplicationStatusMock).toHaveBeenCalledTimes(1);
    expect(store.pollingMode).toBe('paused');

    visibility = 'visible';
    document.dispatchEvent(new Event('visibilitychange'));
    await vi.advanceTimersByTimeAsync(0);
    expect(getApplicationStatusMock).toHaveBeenCalledTimes(2);
  });

  it('resumes a running job after ten minutes hidden without accelerating the following poll', async () => {
    vi.stubGlobal('matchMedia', () => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() }));
    let visibility: DocumentVisibilityState = 'visible';
    vi.spyOn(document, 'visibilityState', 'get').mockImplementation(() => visibility);
    const startedAt = Date.now();
    const section = APPLICATION_STATUS_SECTION.PROCESSING;
    const key = processingCountKey('job', 'exploration', 'done');
    getApplicationStatusMock.mockImplementation(() => {
      const done = 1_000 + (Date.now() - startedAt) / 10;
      return Promise.resolve(
        response([section], {
          processing: { jobs: [{ job: 'exploration', done, total: 100_000, pending: 100_000 - done }] },
        })
      );
    });
    const store = useApplicationStatusStore();
    store.registerPollingConsumer({ [section]: 8_000 });
    await vi.advanceTimersByTimeAsync(0);
    const view = mount(
      defineComponent({
        setup: () => () => h(AnimatedCount, { animation: store.countAnimations.get(key) ?? null }),
      })
    );
    try {
      expect(view.text()).toBe('1,000');
      visibility = 'hidden';
      document.dispatchEvent(new Event('visibilitychange'));
      await vi.advanceTimersByTimeAsync(600_000);
      expect(getApplicationStatusMock).toHaveBeenCalledTimes(1);
      visibility = 'visible';
      document.dispatchEvent(new Event('visibilitychange'));
      await vi.advanceTimersByTimeAsync(0);
      expect(view.text()).toBe('60,200');
      await vi.advanceTimersByTimeAsync(4_000);
      expect(view.text()).toBe('60,600');
      await vi.advanceTimersByTimeAsync(4_000);
      expect(view.text()).toBe('61,000');
      expect(getApplicationStatusMock).toHaveBeenCalledTimes(3);
      await vi.advanceTimersByTimeAsync(4_000);
      expect(view.text()).toBe('61,400');
    } finally {
      view.unmount();
    }
  });

  it('backs off network failures up to a bounded retry', async () => {
    getApplicationStatusMock
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValue(
        response([APPLICATION_STATUS_SECTION.FRESHNESS], { freshness: { freshnessToken: 'fresh-1' } })
      );
    const store = useApplicationStatusStore();
    store.registerPollingConsumer({ [APPLICATION_STATUS_SECTION.FRESHNESS]: 1_000 });
    await vi.advanceTimersByTimeAsync(0);

    expect(store.sectionStates.freshness.healthy).toBe(false);
    expect(store.pollingMode).toBe('backoff');
    await vi.advanceTimersByTimeAsync(7_999);
    expect(getApplicationStatusMock).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(1);
    expect(getApplicationStatusMock).toHaveBeenCalledTimes(2);
    expect(store.consecutiveNetworkFailures).toBe(0);
    expect(store.backoffUntil).toBeNull();
  });
});
