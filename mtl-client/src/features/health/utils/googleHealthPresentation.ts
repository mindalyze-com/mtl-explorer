import type { GHStatusResponse } from '../api/googleHealthApi';
import { formatHealthDateTime } from './healthDateTime';

export const GOOGLE_HEALTH_FEATURE_LABEL = 'Google Health (beta)';
export const GOOGLE_HEALTH_BETA_NOTICE = 'Still in development. Errors and sync interruptions may occur.';
const UNREADABLE_AUTHORIZATION_REASON = 'saved authorization cannot be read';

export type GoogleHealthStatusPresentation = {
  label: string;
  className: string;
  icon: string;
};

export function googleHealthReauthorizationExplanation(status: GHStatusResponse | null | undefined) {
  if (
    status?.configurationState !== 'CONFIGURED' ||
    status.connectionState === 'DISCONNECTED' ||
    (status.authorizationAction !== 'REAUTHORIZE' && status.connectionState !== 'RECONNECT_REQUIRED')
  )
    return null;

  const recovery = 'Sync is paused. Reauthorize to resume it. Stored records and routes are kept.';
  if (status.lastError?.includes(UNREADABLE_AUTHORIZATION_REASON)) {
    return {
      heading: 'Google Health needs reauthorization.',
      detail: `MTL Explorer could not read the saved Google authorization. A new authorization is needed. ${recovery}`,
    };
  }
  const expiry = status.refreshTokenExpiresAt;
  if (expiry && expiry.getTime() <= Date.now()) {
    return {
      heading: 'Google Health authorization has expired.',
      detail: `Google reported that authorization expired on ${formatHealthDateTime(expiry)}. In Testing mode, Google limits access to seven days. Account authorization needs renewing. Your Google Cloud and server setup can stay in place. ${recovery}`,
    };
  }
  return {
    heading: 'Google Health needs reauthorization.',
    detail: `The saved authorization is no longer usable. Access may have expired or been revoked. In Testing mode, Google expires access after seven days. ${recovery}`,
  };
}

export function hasPendingGoogleHealthHistory(status: GHStatusResponse | null | undefined): boolean {
  if (status?.synchronization?.streams?.length)
    return status.synchronization.streams.some((stream) => (stream.pendingDays ?? 0) > 0);
  return status?.syncStates?.some((state) => state.backfillComplete === false) === true;
}

export function googleHealthStatusPresentation(
  status: GHStatusResponse | null,
  options: { loading?: boolean; syncing?: boolean; connectionOnly?: boolean; syntheticData?: boolean } = {}
): GoogleHealthStatusPresentation {
  if (options.syntheticData) return { label: 'Synthetic demo data', className: '', icon: 'bi bi-info-circle' };
  if (!status) {
    return options.loading
      ? { label: 'Loading', className: 'admin-status-pill--loading', icon: 'pi pi-spin pi-spinner' }
      : { label: 'Unavailable', className: '', icon: 'bi bi-circle' };
  }
  if (status.configurationState === 'DISABLED') return neutralStatus('Disabled');
  if (status.configurationState === 'MISSING_CONFIGURATION') {
    return warningStatus('Setup required', 'bi bi-exclamation-circle');
  }
  if (!options.connectionOnly && (options.syncing || ['QUEUED', 'RUNNING'].includes(status.job?.state ?? ''))) {
    return { label: 'Syncing', className: 'admin-status-pill--loading', icon: 'pi pi-spin pi-spinner' };
  }
  if (status.authorizationAction === 'EXPIRING_SOON') {
    return warningStatus('Reauthorize soon', 'bi bi-calendar-week');
  }
  if (status.authorizationAction === 'REAUTHORIZE') {
    return warningStatus('Reauthorization required', 'bi bi-calendar-week');
  }
  if (status.connectionState === 'CONNECTED') {
    if (
      !options.connectionOnly &&
      (status.job?.statistics?.budgetLimited ||
        (status.routeSummary?.failedCount ?? 0) > 0 ||
        status.syncStates?.some((state) => Boolean(state.lastError)) ||
        status.synchronization?.streams?.some((stream) => (stream.failures?.length ?? 0) > 0 || stream.budgetLimited) ||
        ['FAILED', 'COMPLETED_WITH_ERRORS'].includes(status.job?.state ?? ''))
    ) {
      return warningStatus('Sync needs attention', 'bi bi-exclamation-circle');
    }
    if (
      !options.connectionOnly &&
      status.synchronization?.streams?.some((stream) => stream.fullPass?.state === 'ACTIVE')
    ) {
      return { label: 'Recent refresh pending', className: '', icon: 'bi bi-arrow-repeat' };
    }
    if (!options.connectionOnly && hasPendingGoogleHealthHistory(status)) {
      return { label: 'History pending', className: '', icon: 'bi bi-clock-history' };
    }
    return { label: 'Connected', className: 'admin-status-pill--success', icon: 'bi bi-check-circle' };
  }
  if (status.connectionState === 'RECONNECT_REQUIRED') {
    return {
      label: 'Reconnect required',
      className: 'admin-status-pill--error',
      icon: 'bi bi-exclamation-circle',
    };
  }
  return neutralStatus('Disconnected');
}

function neutralStatus(label: string): GoogleHealthStatusPresentation {
  return { label, className: '', icon: 'bi bi-circle' };
}

function warningStatus(label: string, icon: string): GoogleHealthStatusPresentation {
  return { label, className: 'admin-status-pill--warning', icon };
}
