import {
  MapConfigDtoTileModeEnum,
  MapServerStatusControllerApi,
  MapServerStatusDtoTileSourceEnum,
  type MapServerStatusDto,
} from 'x8ing-mtl-api-typescript-fetch';
import { getApiConfiguration } from '@/utils/openApiClient';
import { createStatusRequestService, STATUS_POLL_INTERVAL_MS, type StatusRequestOptions } from '@/utils/statusPolling';

export const MAP_STATUS_POLL_INTERVAL_MS = STATUS_POLL_INTERVAL_MS;

const MAP_STATUS_REQUEST_TIMEOUT_MS = 8_000;
const MAP_STATUS_MAX_RETRY_DELAY_MS = 30_000;

export type MapStatusRequestOptions = StatusRequestOptions;

export interface MapStatusPollingContext {
  tileMode?: string;
  offline?: boolean;
  remoteRasterOverride?: boolean;
  status?: MapServerStatusDto | null;
}

const mapStatusRequests = createStatusRequestService<MapServerStatusDto>({
  request: (signal) => new MapServerStatusControllerApi(getApiConfiguration()).getMapServerStatus({ signal }),
  intervalMs: MAP_STATUS_POLL_INTERVAL_MS,
  requestTimeoutMs: MAP_STATUS_REQUEST_TIMEOUT_MS,
  maxRetryDelayMs: MAP_STATUS_MAX_RETRY_DELAY_MS,
  pauseWhen: isDocumentHidden,
  pausedErrorMessage: 'Map status refresh is paused while the page is hidden.',
});

export async function fetchMapStatus(options: MapStatusRequestOptions = {}): Promise<MapServerStatusDto> {
  return mapStatusRequests.fetch(options);
}

export function invalidateMapStatus(): void {
  mapStatusRequests.invalidate();
}

export function shouldPollMapStatus(context: MapStatusPollingContext): boolean {
  if (context.offline || context.remoteRasterOverride || context.tileMode !== MapConfigDtoTileModeEnum.Local) {
    return false;
  }

  return context.status?.ready !== true || context.status.tileSource === MapServerStatusDtoTileSourceEnum.Public;
}

function isDocumentHidden(): boolean {
  return typeof document !== 'undefined' && document.visibilityState === 'hidden';
}
