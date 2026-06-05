import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import {
  getAdminOperationalTasks,
  getIndexerStatus,
  getJobStatus,
  type AdminOperationalTask,
  type IndexSummary,
  type JobSummary,
} from '@/utils/serverAdminApi';
import { useIndexerStatusStore } from '@/stores/indexerStatusStore';

vi.mock('@/utils/serverAdminApi', () => ({
  getAdminOperationalTasks: vi.fn(),
  getIndexerStatus: vi.fn(),
  getJobStatus: vi.fn(),
}));

const getAdminOperationalTasksMock = vi.mocked(getAdminOperationalTasks);
const getIndexerStatusMock = vi.mocked(getIndexerStatus);
const getJobStatusMock = vi.mocked(getJobStatus);

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

function operationalTask(active: boolean): AdminOperationalTask {
  return {
    id: 'vector-map-tiles',
    label: 'Vector Map Tiles',
    state: active ? 'running' : 'done',
    statusLabel: active ? 'downloading' : 'ready',
    active,
    indeterminate: false,
    progressPercent: active ? 50 : 100,
    detail: '',
    metric: '',
    versionInfo: null,
  };
}

describe('useIndexerStatusStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    getAdminOperationalTasksMock.mockReset();
    getIndexerStatusMock.mockReset();
    getJobStatusMock.mockReset();
  });

  it('refreshes indexer, job, and operational task statuses through the store', async () => {
    getIndexerStatusMock.mockResolvedValueOnce([indexSummary(2)]);
    getJobStatusMock.mockResolvedValueOnce([jobSummary(0)]);
    getAdminOperationalTasksMock.mockResolvedValueOnce([operationalTask(true)]);
    const store = useIndexerStatusStore();

    await store.refresh();

    expect(store.summaries).toHaveLength(1);
    expect(store.jobSummaries).toHaveLength(1);
    expect(store.operationalTasks).toHaveLength(1);
    expect(store.lastRefreshed).not.toBe('');
    expect(store.isIndexerStatusPollingHealthy).toBe(true);
    expect(store.isIndexing).toBe(true);
    expect(store.isJobPending).toBe(false);
    expect(store.isOperationalTaskActive).toBe(true);
  });

  it('marks polling unhealthy on refresh failure and recovers after a later success', async () => {
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    getIndexerStatusMock.mockRejectedValueOnce(new Error('server down')).mockResolvedValueOnce([indexSummary(0)]);
    getJobStatusMock.mockResolvedValueOnce([jobSummary(0)]).mockResolvedValueOnce([jobSummary(1)]);
    getAdminOperationalTasksMock.mockResolvedValueOnce([]).mockResolvedValueOnce([]);
    const store = useIndexerStatusStore();

    await store.refresh();

    expect(store.isIndexerStatusPollingHealthy).toBe(false);
    expect(warnSpy).toHaveBeenCalledTimes(1);

    await store.refresh();

    expect(store.isIndexerStatusPollingHealthy).toBe(true);
    expect(store.isIndexing).toBe(false);
    expect(store.isJobPending).toBe(true);
    expect(warnSpy).toHaveBeenCalledTimes(1);
    warnSpy.mockRestore();
  });
});
