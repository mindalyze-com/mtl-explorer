import {
  HealthControllerApi,
  ResponseError,
  type GetGoogleHealthRecordsRequest,
  type GHDataSourceSummary,
  type GHRecordResponse,
  type GHRecordsResponse,
  type GHSetupResponse,
  type GHStatusResponse,
  type GHGroupedMetrics,
  type GHGroupedSeries,
  type GHMetricBucket,
  type GHMetricPoint,
  type GHMetricSeries,
  type GHOAuthCompleteResponse,
  type GHOAuthStartResponse,
  type GHTypeSummary,
} from 'x8ing-mtl-api-typescript-fetch';
import { getApiConfiguration } from '@/utils/openApiClient';
import { warnRequestFailure } from '@/utils/safeLogging';
import { isAbortLikeError } from '@/utils/errors';
import { loadLatestHealthMetrics } from '../utils/latestHealthMetrics';

export type {
  GHDataSourceSummary,
  GHRecordResponse,
  GHRecordsResponse,
  GHSetupResponse,
  GHStatusResponse,
  GHGroupedMetrics,
  GHGroupedSeries,
  GHMetricBucket,
  GHMetricPoint,
  GHMetricSeries,
  GHOAuthCompleteResponse,
  GHOAuthStartResponse,
  GHTypeSummary,
};

export type GHRecordFilters = GetGoogleHealthRecordsRequest;

function api() {
  return new HealthControllerApi(getApiConfiguration());
}

function messageFromPayload(payload: unknown): string | null {
  if (typeof payload !== 'object' || payload === null) return null;
  const value = payload as Record<string, unknown>;
  for (const field of ['detail', 'message', 'title']) {
    if (typeof value[field] === 'string' && value[field].trim()) return value[field].trim();
  }
  return null;
}

async function request<T>(name: string, operation: () => Promise<T>): Promise<T> {
  try {
    return await operation();
  } catch (error) {
    if (!isAbortLikeError(error) && (!(error instanceof ResponseError) || error.response.status >= 500)) {
      // Report the original transport failure before reading private problem-response details for the UI.
      warnRequestFailure('Google Health request failed', error, {
        topic1: 'health',
        topic2: 'googleHealthApi',
        topic3: name,
        context: error instanceof ResponseError ? { status: error.response.status } : undefined,
      });
    }
    if (!(error instanceof ResponseError)) throw error;
    let message: string | null = null;
    try {
      message = messageFromPayload(await error.response.clone().json());
    } catch {
      message = null;
    }
    if (message) throw new Error(message, { cause: error });
    throw new Error(`Google Health request failed (${error.response.status}).`, { cause: error });
  }
}

export const googleHealthApi = {
  status: (): Promise<GHStatusResponse> => request('status', () => api().getGoogleHealthStatus()),
  setup: (): Promise<GHSetupResponse> => request('setup', () => api().getGoogleHealthSetup()),
  summaries: (): Promise<GHTypeSummary[]> => request('summaries', () => api().getGoogleHealthTypeSummaries()),
  sources: (): Promise<GHDataSourceSummary[]> => request('sources', () => api().getGoogleHealthDataSources()),
  metrics: (start: Date, end: Date): Promise<GHMetricSeries[]> =>
    request('metrics', () => api().getGoogleHealthMetricSeries({ start, end })),
  groupedMetrics: (
    startDate: string,
    endDate: string,
    groupBy: NonNullable<GHGroupedMetrics['requestedGrouping']>,
    timeZone: string
  ): Promise<GHGroupedMetrics> =>
    request('groupedMetrics', () => api().getGoogleHealthGroupedMetrics({ startDate, endDate, groupBy, timeZone })),
  latestMetrics: (summaries: GHTypeSummary[]): Promise<GHMetricSeries[]> =>
    loadLatestHealthMetrics(summaries, (start, end) =>
      request('latestMetrics', () => api().getGoogleHealthMetricSeries({ start, end }))
    ),
  records: (filters: GHRecordFilters = {}): Promise<GHRecordsResponse> =>
    request('records', () => api().getGoogleHealthRecords(filters)),
  record: (recordId: string): Promise<GHRecordResponse> =>
    request('record', () => api().getGoogleHealthRecord({ recordId })),
  connect: async (reauthorize = false): Promise<GHOAuthStartResponse> => {
    const result = await request('connect', () => api().startGoogleHealthOAuth({ reauthorize }));
    if (!result.authorizationUrl) {
      const error = new Error('The server did not return a Google authorization URL.');
      warnRequestFailure('Google Health authorization response is incomplete', error, {
        topic1: 'health',
        topic2: 'googleHealthApi',
        topic3: 'connect',
      });
      throw error;
    }
    return result;
  },
  completeConnection: (redirectUrl: string): Promise<GHOAuthCompleteResponse> =>
    request('completeConnection', () =>
      api().completeGoogleHealthOAuthManually({ gHOAuthCompleteRequest: { redirectUrl } })
    ),
  syncNow: () => request('syncNow', () => api().syncGoogleHealthNow()),
  reconcileAll: () => request('reconcileAll', () => api().reconcileAllGoogleHealthData()),
  reloadRange: (startDate: string, endDate: string) =>
    request('reloadRange', () =>
      api().reloadGoogleHealthRange({
        gHReloadRequest: { startDate: new Date(`${startDate}T00:00:00Z`), endDate: new Date(`${endDate}T00:00:00Z`) },
      })
    ),
  retryFailed: () => request('retryFailed', () => api().retryFailedGoogleHealthRanges()),
  disconnect: () => request('disconnect', () => api().disconnectGoogleHealth()),
  deleteMirror: (confirmation: string) =>
    request('deleteMirror', () => api().deleteGoogleHealthLocalMirror({ gHDeleteRequest: { confirmation } })),
};
