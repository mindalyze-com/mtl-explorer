import { defineStore } from 'pinia';
import { computed } from 'vue';
import { MapConfigDtoTileModeEnum } from 'x8ing-mtl-api-typescript-fetch';
import { APPLICATION_STATUS_SECTION, useApplicationStatusStore } from '@/stores/applicationStatusStore';
import { buildAdminOperationalTasks, type AdminOperationalTask } from '@/utils/adminOperationalTasks';
import type { IndexSummary, JobSummary } from '@/utils/serverAdminApi';

export const useIndexerStatusStore = defineStore('indexerStatus', () => {
  const applicationStatusStore = useApplicationStatusStore();

  const summaries = computed<IndexSummary[]>(() =>
    (applicationStatusStore.processing?.indexers ?? []).map((summary) => ({
      ...summary,
      pending: summary.pending ?? 0,
      failed: summary.failed ?? 0,
      completed: summary.completed ?? 0,
      removed: summary.removed ?? 0,
      excluded: summary.excluded ?? 0,
      total: summary.total ?? 0,
      progressPercent: summary.progressPercent ?? 0,
    }))
  );
  const jobSummaries = computed<JobSummary[]>(() =>
    (applicationStatusStore.processing?.jobs ?? []).map((summary) => ({
      ...summary,
      pending: summary.pending ?? 0,
      done: summary.done ?? 0,
      unavailable: summary.unavailable ?? 0,
      failed: summary.failed ?? 0,
      total: summary.total ?? 0,
      progressPercent: summary.progressPercent ?? 0,
    }))
  );
  const dependencies = computed(() => applicationStatusStore.processing?.dependencies ?? []);
  const operationalTasks = computed<AdminOperationalTask[]>(() => {
    const status = applicationStatusStore.services;
    if (!status) return [];
    return buildAdminOperationalTasks({
      mapConfig: {
        tileMode: status.tileMode as MapConfigDtoTileModeEnum | undefined,
        plannerEnabled: status.plannerEnabled,
      },
      mapStatus: status.map,
      locationSearchStatus: status.locationSearch,
      brouterStatus: status.planner,
    });
  });

  const processingState = computed(() => applicationStatusStore.sectionStates[APPLICATION_STATUS_SECTION.PROCESSING]);
  const servicesState = computed(() => applicationStatusStore.sectionStates[APPLICATION_STATUS_SECTION.SERVICES]);
  const lastRefreshed = computed(() =>
    formatTime(Math.max(processingState.value.lastSuccessAt ?? 0, servicesState.value.lastSuccessAt ?? 0) || null)
  );
  const isIndexerStatusPollingHealthy = computed(() => processingState.value.healthy && servicesState.value.healthy);

  const isIndexing = computed(() =>
    summaries.value.some((summary) => summary.discoveryInProgress || summary.pending > 0)
  );
  const isJobPending = computed(() => jobSummaries.value.some((summary) => summary.pending > 0));
  const isOperationalTaskActive = computed(() => operationalTasks.value.some((task) => task.active));

  async function refresh(_options: { forceMapStatus?: boolean } = {}): Promise<void> {
    await applicationStatusStore.refresh([APPLICATION_STATUS_SECTION.PROCESSING, APPLICATION_STATUS_SECTION.SERVICES]);
  }

  return {
    summaries,
    jobSummaries,
    dependencies,
    operationalTasks,
    lastRefreshed,
    isIndexerStatusPollingHealthy,
    isIndexing,
    isJobPending,
    isOperationalTaskActive,
    refresh,
  };
});

function formatTime(timestamp: number | null): string {
  if (timestamp === null) return '';
  return new Date(timestamp).toLocaleTimeString(undefined, {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
}
