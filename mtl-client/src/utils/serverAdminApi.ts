/**
 * Server admin / diagnostic API surface.
 *
 * This module holds the long-tail of endpoints used by the admin dialog,
 * the login bootstrap, the indexer/job status composables and the GPX
 * upload tab. Split out of the legacy ServiceHelper.ts to keep that file
 * focused on the core tracks/filters/config surface.
 *
 * Public endpoints (no auth required) use bare axios; everything else
 * goes through the shared `apiClient` (auth + 401/403 redirect baked in).
 */
import axios from 'axios';
import {
  DataFreshnessControllerApi,
  GpxUploadControllerApi,
  IndexerStatusControllerApi,
  JobStatusControllerApi,
  ServerInfoControllerApi,
  ServerLogControllerApi,
  type BuildInfoResponse,
  type DataFreshnessResponseDto,
  type GpxUploadResult,
  type GpxUploadStatus,
  type IndexerRescanResponse,
  type IndexSummaryDto,
  type JobSummaryDto,
} from 'x8ing-mtl-api-typescript-fetch';

import { apiClient } from '@/utils/apiClient';
import { getApiConfiguration } from '@/utils/openApiClient';
import { describeError, startStartupTimer } from '@/utils/startupDiagnostics';
import { apiUrl } from '@/utils/apiBase';
import { logSanitizedError } from '@/utils/safeLogging';

// ─── Build info ──────────────────────────────────────────────────────────────

export type BuildInfo = BuildInfoResponse;

function getServerInfoApi() {
  return new ServerInfoControllerApi(getApiConfiguration());
}

export async function getServerBuildInfo(): Promise<BuildInfo> {
  return getServerInfoApi().getBuild();
}

// ─── Demo status (public) ────────────────────────────────────────────────────

export interface DemoStatus {
  demoMode: boolean;
  username?: string;
  password?: string;
}

export async function getDemoStatus(): Promise<DemoStatus> {
  try {
    // Public endpoint — call without auth header by using bare axios.
    const response = await axios.get<DemoStatus>(apiUrl('api/auth/demo-status'));
    return response.data ?? { demoMode: false };
  } catch {
    return { demoMode: false };
  }
}

// ─── Garmin export ───────────────────────────────────────────────────────────

export interface GarminToolStatus {
  gcexportConfiguredVersion: string;
  gcexportVenvPresent: boolean;
  fitExportConfiguredProfile: string;
  fitExportConfiguredPackages: string;
  fitExportVenvPresent: boolean;
}

export async function triggerGarminExport(): Promise<string> {
  try {
    const response = await apiClient.get('api/garmin-export/trigger-export', {
      headers: { Accept: 'text/plain' },
      responseType: 'text',
    });
    let text: string = typeof response.data === 'string' ? response.data : String(response.data);
    text = text.replace(/\\n/g, '\n');
    return text;
  } catch (error: unknown) {
    logSanitizedError('Error triggering Garmin export:', error);
    throw new Error('Failed to trigger Garmin export: ' + (error instanceof Error ? error.message : String(error)));
  }
}

export async function getGarminToolStatus(): Promise<GarminToolStatus> {
  const response = await apiClient.get('api/garmin-export/tool-status');
  return response.data;
}

async function postInstallScript(url: string): Promise<string> {
  try {
    const response = await apiClient.post(url, null, {
      headers: { Accept: 'text/plain' },
      responseType: 'text',
    });
    const text: string = typeof response.data === 'string' ? response.data : String(response.data);
    return text.replace(/\\n/g, '\n');
  } catch (error: unknown) {
    const axiosErr = axios.isAxiosError(error) ? error : null;
    const rawBody: unknown = axiosErr?.response?.data;
    const log: string | null = typeof rawBody === 'string' && rawBody.length > 0 ? rawBody.replace(/\\n/g, '\n') : null;
    const err: Error & { installLog?: string | null } = new Error(
      log ? 'Install failed — see output below' : axiosErr?.message || 'Install failed'
    );
    err.installLog = log;
    throw err;
  }
}

export async function installGcexport(version: string): Promise<string> {
  return postInstallScript(`api/garmin-export/install-gcexport?version=${encodeURIComponent(version)}`);
}

export async function installFitExport(profile: string, packages: string): Promise<string> {
  return postInstallScript(
    `api/garmin-export/install-fit-export?profile=${encodeURIComponent(profile)}&packages=${encodeURIComponent(packages)}`
  );
}

// ─── GPX Upload ──────────────────────────────────────────────────────────────

export type { GpxUploadStatus, GpxUploadResult };

function getGpxUploadApi() {
  return new GpxUploadControllerApi(getApiConfiguration());
}

export async function getGpxUploadStatus(): Promise<GpxUploadStatus> {
  return getGpxUploadApi().getUploadStatus();
}

export async function uploadGpxFile(file: File): Promise<GpxUploadResult> {
  return getGpxUploadApi().uploadGpxFile({ file });
}

// ─── Server Log ──────────────────────────────────────────────────────────────

function getServerLogApi() {
  return new ServerLogControllerApi(getApiConfiguration());
}

export async function getServerLog(lines: number = 200): Promise<string> {
  return getServerLogApi().getServerLog({ lines });
}

// ─── Indexer / Job Status ────────────────────────────────────────────────────

export type { DataFreshnessResponseDto, IndexerRescanResponse, IndexSummaryDto, JobSummaryDto };
export { getAdminOperationalTasks } from '@/utils/adminOperationalTasks';
export type { AdminOperationalTask } from '@/utils/adminOperationalTasks';
// Local aliases used by the composable/UI. Unlike the generated DTOs (whose
// fields are all optional per OpenAPI defaults), these types mark the numeric
// counters as required — the server always returns them, and the fetch helpers
// normalize any missing values to 0 before handing data to the UI.
export type IndexSummary = Required<
  Pick<IndexSummaryDto, 'pending' | 'failed' | 'completed' | 'total' | 'progressPercent'>
> &
  IndexSummaryDto;
export type JobSummary = Required<Pick<JobSummaryDto, 'pending' | 'done' | 'total' | 'progressPercent'>> &
  JobSummaryDto;

function getIndexerStatusApi() {
  return new IndexerStatusControllerApi(getApiConfiguration());
}

function getJobStatusApi() {
  return new JobStatusControllerApi(getApiConfiguration());
}

function getDataFreshnessApi() {
  return new DataFreshnessControllerApi(getApiConfiguration());
}

export async function getIndexerStatus(): Promise<IndexSummary[]> {
  const dtos = await getIndexerStatusApi().getIndexerStatus();
  return dtos.map((s) => ({
    ...s,
    pending: s.pending ?? 0,
    failed: s.failed ?? 0,
    completed: s.completed ?? 0,
    total: s.total ?? 0,
    progressPercent: s.progressPercent ?? 0,
  }));
}

export async function triggerIndexerRescan(index: 'GPS' | 'MEDIA'): Promise<IndexerRescanResponse> {
  return getIndexerStatusApi().triggerIndexerRescan({ index });
}

export async function getJobStatus(): Promise<JobSummary[]> {
  const dtos = await getJobStatusApi().getJobStatus();
  return dtos.map((j) => ({
    ...j,
    pending: j.pending ?? 0,
    done: j.done ?? 0,
    total: j.total ?? 0,
    progressPercent: j.progressPercent ?? 0,
  }));
}

export async function getDataFreshness(): Promise<DataFreshnessResponseDto> {
  return getDataFreshnessApi().getDataFreshness();
}

// ─── Auth probe ──────────────────────────────────────────────────────────────

export type AuthCheckResult = 'ok' | 'auth-error' | 'network-error';

/**
 * Lightweight auth probe: hits the build-info endpoint to verify the JWT is still valid.
 * Returns 'ok' if the server responds successfully, 'auth-error' for 401/403, or
 * 'network-error' when the server is unreachable.
 */
export async function checkServerAuth(): Promise<AuthCheckResult> {
  const timer = startStartupTimer('auth', 'Checking server auth');
  try {
    await apiClient.get('api/info/build', {
      responseType: 'text',
      timeout: 10000,
    });
    timer.success('Auth probe succeeded');
    return 'ok';
  } catch (error: unknown) {
    if (axios.isAxiosError(error) && error.response) {
      const status = error.response.status;
      if (status === 401 || status === 403) {
        timer.warn('Auth probe reported expired credentials', { status });
        return 'auth-error';
      }
    }
    timer.warn('Auth probe failed due to network issue', describeError(error));
    return 'network-error';
  }
}
