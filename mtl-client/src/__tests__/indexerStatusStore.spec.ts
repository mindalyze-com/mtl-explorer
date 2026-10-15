import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import type { IndexSummary, JobSummary } from '@/utils/serverAdminApi';
import { useIndexerStatusStore } from '@/stores/indexerStatusStore';
import { APPLICATION_STATUS_SECTION, useApplicationStatusStore } from '@/stores/applicationStatusStore';
import { JobSummaryDtoFromJSON } from 'x8ing-mtl-api-typescript-fetch';

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

function indexSummary(pending: number): IndexSummary {
  return {
    pending,
    failed: 0,
    completed: 1,
    total: pending + 1,
    progressPercent: pending > 0 ? 50 : 100,
  } as IndexSummary;
}

function jobSummary(pending: number): JobSummary {
  return {
    pending,
    done: 1,
    total: pending + 1,
    progressPercent: pending > 0 ? 50 : 100,
  } as JobSummary;
}

function aggregateStatus(indexPending: number, jobPending: number, mapActive: boolean) {
  return {
    generatedAt: new Date(),
    included: new Set([APPLICATION_STATUS_SECTION.PROCESSING, APPLICATION_STATUS_SECTION.SERVICES]),
    processing: { indexers: [indexSummary(indexPending)], jobs: [jobSummary(jobPending)] },
    services: {
      tileMode: 'local',
      plannerEnabled: false,
      map: { ready: !mapActive, phase: mapActive ? 'downloading' : 'ready' },
      locationSearch: { ready: true, phase: 'ready' },
    },
  };
}

describe('useIndexerStatusStore', () => {
  afterEach(() => {
    useApplicationStatusStore().$dispose();
    vi.clearAllTimers();
    vi.useRealTimers();
  });

  beforeEach(() => {
    vi.useFakeTimers();
    setActivePinia(createPinia());
    getApplicationStatusMock.mockReset();
  });

  it('refreshes indexer, job, and operational task statuses through the store', async () => {
    getApplicationStatusMock.mockResolvedValueOnce(aggregateStatus(2, 0, true));
    const store = useIndexerStatusStore();

    await refreshOnNextTick(store);

    expect(store.summaries).toHaveLength(1);
    expect(store.jobSummaries).toHaveLength(1);
    expect(store.operationalTasks).toHaveLength(3);
    expect(store.operationalTasks[2]).toMatchObject({
      label: 'Routing Segments',
      state: 'disabled',
      detail: 'Route planning is disabled.',
      active: false,
    });
    expect(store.lastRefreshed).not.toBe('');
    expect(store.isIndexerStatusPollingHealthy).toBe(true);
    expect(store.isIndexing).toBe(true);
    expect(store.isJobPending).toBe(false);
    expect(store.isOperationalTaskActive).toBe(true);
  });

  it('keeps discovery active before any committed import is pending', async () => {
    const status = aggregateStatus(0, 0, false);
    Object.assign(status.processing.indexers[0]!, { discoveryInProgress: true });
    getApplicationStatusMock.mockResolvedValueOnce(status);
    const store = useIndexerStatusStore();
    await refreshOnNextTick(store);
    expect(store.isIndexing).toBe(true);
  });

  it('retains terminal job failures without keeping processing active', async () => {
    const status = aggregateStatus(0, 0, false);
    status.processing.jobs[0]!.failed = 2;
    getApplicationStatusMock.mockResolvedValueOnce(status);
    const store = useIndexerStatusStore();
    await refreshOnNextTick(store);
    expect(store.jobSummaries[0]!.failed).toBe(2);
    expect(store.isJobPending).toBe(false);
  });

  it('retains informational reason counts decoded by the generated API client', async () => {
    const status = aggregateStatus(0, 0, false);
    status.processing.jobs[0] = JobSummaryDtoFromJSON({
      job: 'energy',
      pending: 0,
      done: 1,
      failed: 0,
      unavailable: 2,
      attentionRequired: 0,
      unavailableReasons: { noDataPoints: 1, unsupportedTiming: 1, missingActivity: 0, missingCalculation: 0 },
      total: 3,
      progressPercent: 100,
    }) as JobSummary;
    getApplicationStatusMock.mockResolvedValueOnce(status);
    const store = useIndexerStatusStore();
    await refreshOnNextTick(store);
    expect(store.jobSummaries[0]).toMatchObject({
      attentionRequired: 0,
      unavailable: 2,
      unavailableReasons: { noDataPoints: 1, unsupportedTiming: 1, missingActivity: 0, missingCalculation: 0 },
    });
    expect(store.isJobPending).toBe(false);
  });

  it('marks polling unhealthy on refresh failure and recovers after a later success', async () => {
    getApplicationStatusMock
      .mockRejectedValueOnce(new Error('server down'))
      .mockResolvedValueOnce(aggregateStatus(0, 1, false));
    const store = useIndexerStatusStore();

    await refreshOnNextTick(store);

    expect(store.isIndexerStatusPollingHealthy).toBe(false);

    await refreshOnNextTick(store);

    expect(store.isIndexerStatusPollingHealthy).toBe(true);
    expect(store.isIndexing).toBe(false);
    expect(store.isJobPending).toBe(true);
  });
});

async function refreshOnNextTick(store: ReturnType<typeof useIndexerStatusStore>) {
  const request = store.refresh();
  await vi.advanceTimersByTimeAsync(Math.max(0, (useApplicationStatusStore().nextPollAt ?? Date.now()) - Date.now()));
  await request;
}
