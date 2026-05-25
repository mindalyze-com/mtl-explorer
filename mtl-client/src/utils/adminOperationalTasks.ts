import {
  LocationSearchControllerApi,
  MapConfigDtoTileModeEnum,
  MapServerStatusControllerApi,
  PlannerControllerApi,
  type LocationSearchStatusDto,
  type MapConfigDto,
  type MapServerStatusDto,
  type VersionInfoDto,
} from 'x8ing-mtl-api-typescript-fetch';
import type { SidecarStatus } from '@/planner/types';
import { getApiConfiguration } from '@/utils/openApiClient';

export type AdminOperationalTaskState = 'running' | 'done' | 'warning' | 'disabled';

export interface AdminOperationalTask {
  id: string;
  label: string;
  state: AdminOperationalTaskState;
  statusLabel: string;
  active: boolean;
  indeterminate: boolean;
  progressPercent: number | null;
  detail: string;
  metric: string;
  versionInfo?: VersionInfoDto | null;
}

export interface AdminOperationalTaskSources {
  mapConfig?: MapConfigDto | null;
  mapStatus?: MapServerStatusDto | null;
  locationSearchStatus?: LocationSearchStatusDto | null;
  brouterStatus?: SidecarStatus | null;
}

const TASK_VECTOR_MAP = 'vector-map-tiles';
const TASK_LOCATION_SEARCH = 'location-search';
const TASK_ROUTING_SEGMENTS = 'routing-segments';
const STATUS_READY = 'ready';
const STATUS_DISABLED = 'disabled';
const STATUS_UNAVAILABLE = 'unavailable';
const STATUS_UNREACHABLE = 'unreachable';
const STATUS_ERROR = 'error';
const MAP_PHASE_DOWNLOADING = 'downloading';
const BYTE_UNITS = ['B', 'KB', 'MB', 'GB', 'TB'];

export async function getAdminOperationalTasks(): Promise<AdminOperationalTask[]> {
  const mapApi = new MapServerStatusControllerApi(getApiConfiguration());
  const locationSearchApi = new LocationSearchControllerApi(getApiConfiguration());

  const [mapConfigResult, mapStatusResult, locationSearchResult] = await Promise.allSettled([
    mapApi.getMapConfig(),
    mapApi.getMapServerStatus(),
    locationSearchApi.getStatus(),
  ]);

  const mapConfig = fulfilledValue(mapConfigResult);
  const mapStatus = fulfilledValue(mapStatusResult);
  const locationSearchStatus = fulfilledValue(locationSearchResult);
  const brouterStatus = mapConfig?.plannerEnabled === true ? await fetchPlannerStatus() : null;

  return buildAdminOperationalTasks({
    mapConfig,
    mapStatus,
    locationSearchStatus,
    brouterStatus,
  });
}

export function buildAdminOperationalTasks(sources: AdminOperationalTaskSources): AdminOperationalTask[] {
  const tasks = [
    normalizeMapTask(sources.mapStatus, sources.mapConfig),
    normalizeLocationSearchTask(sources.locationSearchStatus),
  ];
  if (sources.mapConfig?.plannerEnabled === true) {
    tasks.push(normalizeBRouterTask(sources.brouterStatus));
  }
  return tasks;
}

export function normalizeMapTask(
  status: MapServerStatusDto | null | undefined,
  config?: MapConfigDto | null
): AdminOperationalTask {
  if (config?.tileMode && config.tileMode !== MapConfigDtoTileModeEnum.Local) {
    return task(TASK_VECTOR_MAP, 'Vector Map Tiles', 'disabled', 'disabled', {
      detail: 'Remote raster map mode is active.',
      metric: 'Local tiles off',
    });
  }

  if (!status) {
    return task(TASK_VECTOR_MAP, 'Vector Map Tiles', 'warning', 'unavailable', {
      detail: 'Map status is not reachable.',
    });
  }

  const phase = normalizePhase(status.phase, status.ready);
  if (status.ready === true) {
    return task(TASK_VECTOR_MAP, 'Vector Map Tiles', 'done', 'done', {
      progressPercent: 100,
      detail: status.message || mapPhaseDetail(phase),
      metric: mapReadyMetric(status),
      versionInfo: status.versionInfo ?? null,
    });
  }

  if (isWarningPhase(phase)) {
    return task(TASK_VECTOR_MAP, 'Vector Map Tiles', 'warning', 'attention', {
      detail: status.message || mapPhaseDetail(phase),
      metric: status.archiveId ? `Archive ${status.archiveId}` : '',
      versionInfo: status.versionInfo ?? null,
    });
  }

  const hasDownloadTotal = finitePositive(status.downloadTotal);
  const determinateDownload = phase === MAP_PHASE_DOWNLOADING && hasDownloadTotal;
  const progressPercent = determinateDownload ? clampPercent(status.downloadPct) : null;

  return task(TASK_VECTOR_MAP, 'Vector Map Tiles', 'running', runningLabelForPhase(phase), {
    indeterminate: !determinateDownload,
    progressPercent,
    detail: status.message || mapPhaseDetail(phase),
    metric: determinateDownload
      ? formatBytesProgress(status.downloadBytes, status.downloadTotal)
      : mapReadyMetric(status),
    versionInfo: status.versionInfo ?? null,
  });
}

export function normalizeLocationSearchTask(
  status: LocationSearchStatusDto | null | undefined
): AdminOperationalTask {
  if (!status) {
    return task(TASK_LOCATION_SEARCH, 'Location Search', 'warning', 'unavailable', {
      detail: 'Location search status is not reachable.',
    });
  }

  const phase = normalizePhase(status.phase, status.ready);
  if (phase === STATUS_DISABLED) {
    return task(TASK_LOCATION_SEARCH, 'Location Search', 'disabled', 'disabled', {
      detail: status.message || 'Location search is disabled.',
      metric: 'Search off',
      versionInfo: status.versionInfo ?? null,
    });
  }

  if (status.ready === true) {
    return task(TASK_LOCATION_SEARCH, 'Location Search', 'done', 'done', {
      progressPercent: 100,
      detail: status.message || 'GeoNames location search ready.',
      metric: locationSearchReadyMetric(status),
      versionInfo: status.versionInfo ?? null,
    });
  }

  if (isWarningPhase(phase)) {
    return task(TASK_LOCATION_SEARCH, 'Location Search', 'warning', warningLabelForPhase(phase), {
      detail: status.message || 'Location search needs attention.',
      metric: locationSearchReadyMetric(status),
      versionInfo: status.versionInfo ?? null,
    });
  }

  return task(TASK_LOCATION_SEARCH, 'Location Search', 'running', runningLabelForPhase(phase), {
    indeterminate: true,
    progressPercent: null,
    detail: status.message || locationSearchPhaseDetail(phase),
    metric: locationSearchReadyMetric(status),
    versionInfo: status.versionInfo ?? null,
  });
}

export function normalizeBRouterTask(status: SidecarStatus | null | undefined): AdminOperationalTask {
  if (!status) {
    return task(TASK_ROUTING_SEGMENTS, 'Routing Segments', 'warning', 'unavailable', {
      detail: 'Routing status is not reachable.',
    });
  }

  if (!status.available) {
    return task(TASK_ROUTING_SEGMENTS, 'Routing Segments', 'warning', 'unavailable', {
      detail: `BRouter sidecar unavailable${status.reason ? `: ${status.reason}` : '.'}`,
      versionInfo: status.versionInfo ?? null,
    });
  }

  if (!status.brouterRunning) {
    return task(TASK_ROUTING_SEGMENTS, 'Routing Segments', 'warning', 'stopped', {
      detail: 'BRouter is not running.',
      metric: brouterReadyMetric(status),
      versionInfo: status.versionInfo ?? null,
    });
  }

  const queued = nonNegativeInt(status.segmentsQueued);
  const inProgress = status.segmentsInProgress?.length ?? 0;
  if (queued > 0 || inProgress > 0) {
    return task(TASK_ROUTING_SEGMENTS, 'Routing Segments', 'running', 'preparing', {
      indeterminate: true,
      detail: 'Preparing routing data for route calculation.',
      metric: brouterProgressMetric(status),
      versionInfo: status.versionInfo ?? null,
    });
  }

  return task(TASK_ROUTING_SEGMENTS, 'Routing Segments', 'done', 'ready', {
    progressPercent: 100,
    detail: 'Routing segments are ready for planned routes.',
    metric: brouterReadyMetric(status),
    versionInfo: status.versionInfo ?? null,
  });
}

async function fetchPlannerStatus(): Promise<SidecarStatus> {
  try {
    return await new PlannerControllerApi(getApiConfiguration()).status();
  } catch (error: unknown) {
    return {
      available: false,
      reason: error instanceof Error ? error.message : 'Routing status is not reachable.',
    };
  }
}

function task(
  id: string,
  label: string,
  state: AdminOperationalTaskState,
  statusLabel: string,
  options: Partial<Pick<AdminOperationalTask, 'indeterminate' | 'progressPercent' | 'detail' | 'metric' | 'versionInfo'>>
): AdminOperationalTask {
  return {
    id,
    label,
    state,
    statusLabel,
    active: state === 'running',
    indeterminate: options.indeterminate ?? false,
    progressPercent: options.progressPercent ?? null,
    detail: options.detail ?? '',
    metric: options.metric ?? '',
    versionInfo: options.versionInfo ?? null,
  };
}

function fulfilledValue<T>(result: PromiseSettledResult<T>): T | null {
  return result.status === 'fulfilled' ? result.value : null;
}

function normalizePhase(phase: string | undefined, ready: boolean | undefined): string {
  if (phase && phase.trim()) return phase.trim().toLowerCase();
  return ready ? STATUS_READY : 'unknown';
}

function isWarningPhase(phase: string): boolean {
  return phase === STATUS_UNAVAILABLE || phase === STATUS_UNREACHABLE || phase === STATUS_ERROR;
}

function warningLabelForPhase(phase: string): string {
  return phase === STATUS_UNAVAILABLE || phase === STATUS_UNREACHABLE ? 'unavailable' : 'attention';
}

function runningLabelForPhase(phase: string): string {
  if (phase === MAP_PHASE_DOWNLOADING) return 'downloading';
  if (phase === 'extracting' || phase === 'area-extract') return 'extracting';
  if (phase === 'optimizing') return 'optimizing';
  if (phase === 'importing') return 'importing';
  if (phase === 'queued') return 'queued';
  return 'running';
}

function mapPhaseDetail(phase: string): string {
  if (phase === MAP_PHASE_DOWNLOADING) return 'Downloading vector map tiles.';
  if (phase === 'extracting') return 'Extracting low-zoom vector tiles.';
  if (phase === 'area-extract') return 'Extracting bounded vector map tiles.';
  if (phase === 'waiting-kickoff') return 'Waiting for map processing kickoff.';
  if (phase === STATUS_UNREACHABLE) return 'Map server is not reachable.';
  if (phase === STATUS_ERROR) return 'Map preparation needs attention.';
  return 'Preparing vector map tiles.';
}

function locationSearchPhaseDetail(phase: string): string {
  if (phase === STATUS_UNREACHABLE) return 'Location search sidecar is not reachable.';
  if (phase === STATUS_ERROR) return 'Location search needs attention.';
  if (phase === STATUS_UNAVAILABLE) return 'GeoNames search database is unavailable.';
  return 'Preparing GeoNames location search.';
}

function mapReadyMetric(status: MapServerStatusDto): string {
  if (status.tileSource === 'public') return 'Hosted map service';
  return status.archiveId ? `Archive ${status.archiveId}` : '';
}

function locationSearchReadyMetric(status: LocationSearchStatusDto): string {
  const rowCount = status.rowCount;
  if (finitePositive(rowCount)) {
    const terrain = nonNegativeInt(status.terrainCount);
    const places = nonNegativeInt(status.populatedPlaceCount);
    if (terrain > 0 && places > 0) return `${Math.round(rowCount)} places (${places} populated, ${terrain} terrain)`;
    return `${Math.round(rowCount)} places`;
  }
  return status.sourceAttribution || '';
}

function progressMetric(current: number | undefined, total: number | undefined, unit: string | undefined): string {
  if (!finitePositive(total)) return '';
  const safeCurrent = nonNegativeInt(current);
  const safeTotal = nonNegativeInt(total);
  const suffix = unit && unit.trim() ? ` ${unit.trim()}` : '';
  return `${safeCurrent} / ${safeTotal}${suffix}`;
}

function brouterProgressMetric(status: SidecarStatus): string {
  const queued = nonNegativeInt(status.segmentsQueued);
  const inProgress = status.segmentsInProgress?.length ?? 0;
  const parts = [];
  if (queued > 0) parts.push(`${queued} queued`);
  if (inProgress > 0) parts.push(`${inProgress} downloading`);
  return parts.join(', ');
}

function brouterReadyMetric(status: SidecarStatus): string {
  const onDisk = nonNegativeInt(status.segmentsOnDisk);
  return onDisk > 0 ? `${onDisk} on disk` : '';
}

function formatBytesProgress(current: number | undefined, total: number | undefined): string {
  if (!finitePositive(total)) return '';
  return `${formatBytes(nonNegativeInt(current))} / ${formatBytes(nonNegativeInt(total))}`;
}

function formatBytes(value: number): string {
  let scaled = Math.max(0, value);
  let unitIndex = 0;
  while (scaled >= 1024 && unitIndex < BYTE_UNITS.length - 1) {
    scaled /= 1024;
    unitIndex++;
  }
  return `${scaled >= 10 || unitIndex === 0 ? Math.round(scaled) : scaled.toFixed(1)} ${BYTE_UNITS[unitIndex]}`;
}

function clampPercent(value: number | undefined): number {
  if (!Number.isFinite(value)) return 0;
  return Math.max(0, Math.min(100, Math.round(value ?? 0)));
}

function finitePositive(value: number | undefined): value is number {
  return Number.isFinite(value) && (value ?? 0) > 0;
}

function nonNegativeInt(value: number | undefined): number {
  return Number.isFinite(value) ? Math.max(0, Math.round(value ?? 0)) : 0;
}
