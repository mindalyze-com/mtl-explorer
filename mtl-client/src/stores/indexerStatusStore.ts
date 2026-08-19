import { defineStore } from 'pinia';
import { computed, ref, shallowRef } from 'vue';
import {
  getAdminOperationalTasks,
  getIndexerStatus,
  getJobStatus,
  type AdminOperationalTask,
  type IndexSummary,
  type JobSummary,
} from '@/utils/serverAdminApi';

export const useIndexerStatusStore = defineStore('indexerStatus', () => {
  const summaries = shallowRef<IndexSummary[]>([]);
  const jobSummaries = shallowRef<JobSummary[]>([]);
  const operationalTasks = shallowRef<AdminOperationalTask[]>([]);
  const lastRefreshed = ref('');
  const isIndexerStatusPollingHealthy = ref(true);

  let pollWarnShown = false;

  const isIndexing = computed(() => summaries.value.some((summary) => summary.pending > 0));
  const isJobPending = computed(() => jobSummaries.value.some((summary) => summary.pending > 0));
  const isOperationalTaskActive = computed(() => operationalTasks.value.some((task) => task.active));

  async function refresh(options: { forceMapStatus?: boolean } = {}): Promise<void> {
    try {
      const [indexData, jobData, operationalData] = await Promise.all([
        getIndexerStatus(),
        getJobStatus(),
        getAdminOperationalTasks(options),
      ]);
      summaries.value = indexData;
      jobSummaries.value = jobData;
      operationalTasks.value = operationalData;
      lastRefreshed.value = new Date().toLocaleTimeString(undefined, {
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
      });
      isIndexerStatusPollingHealthy.value = true;
    } catch (err) {
      isIndexerStatusPollingHealthy.value = false;
      if (!pollWarnShown) {
        console.warn('[MTL] Indexer/job status polling failed - server may be unreachable or blocked:', err);
        pollWarnShown = true;
      }
    }
  }

  return {
    summaries,
    jobSummaries,
    operationalTasks,
    lastRefreshed,
    isIndexerStatusPollingHealthy,
    isIndexing,
    isJobPending,
    isOperationalTaskActive,
    refresh,
  };
});
