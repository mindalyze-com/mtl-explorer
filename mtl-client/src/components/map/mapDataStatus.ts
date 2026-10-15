import type { ProcessingStatusDto } from 'x8ing-mtl-api-typescript-fetch';
import { issueBreakdown } from '@/features/track-review/sourceIssuePresentation';

export interface MapDataStatus {
  kind: 'preparing' | 'stale' | 'attention' | 'unavailable' | 'offline';
  label: string;
  explanation: string;
  preparing: boolean;
  outOfSync: boolean;
  failedFiles: number;
  issueBreakdown: string;
  detailsSection: 'processing' | 'data-status';
}

export function resolveMapDataStatus(input: {
  loaded: boolean;
  offline: boolean;
  processing: ProcessingStatusDto | null;
  processingHealthy: boolean;
  freshnessHealthy: boolean;
  outOfSync: boolean;
}): MapDataStatus | null {
  if (!input.loaded) return null;
  const discovering = (input.processing?.indexers ?? []).some((summary) => summary.discoveryInProgress);
  const importing = discovering || (input.processing?.indexers ?? []).some((summary) => (summary.pending ?? 0) > 0);
  const preparing = importing || (input.processing?.jobs ?? []).some((summary) => (summary.pending ?? 0) > 0);
  const failedFiles = (input.processing?.indexers ?? []).reduce(
    (total, summary) => total + (summary.pendingIssueFileCount ?? 0),
    0
  );
  const common = {
    preparing,
    outOfSync: input.outOfSync,
    failedFiles,
    issueBreakdown: issueBreakdown(
      (input.processing?.indexers ?? []).map((summary) => ({
        index: summary.index,
        count: summary.pendingIssueFileCount,
      }))
    ),
    detailsSection: preparing || failedFiles > 0 ? ('processing' as const) : ('data-status' as const),
  };
  if (input.offline) {
    return {
      ...common,
      kind: 'offline',
      label: 'Offline · cached map',
      explanation: 'Showing saved tracks. MTL Explorer cannot check for newer data right now.',
    };
  }
  if (!input.processingHealthy || !input.freshnessHealthy) {
    return {
      ...common,
      detailsSection: !input.processingHealthy ? 'processing' : common.detailsSection,
      kind: 'unavailable',
      label: 'Data status unavailable',
      explanation: 'Data status could not be checked. The map, search, and statistics may not match.',
    };
  }
  if (preparing) {
    return {
      ...common,
      kind: 'preparing',
      label: discovering ? 'Discovering files…' : importing ? 'Getting your data ready…' : 'Updating calculations…',
      explanation:
        'Processing is still running, so the map, search, and statistics may not match yet. Reload to see the latest data. See Processing details for progress.',
    };
  }
  if (input.outOfSync) {
    return {
      ...common,
      kind: 'stale',
      label: 'Map needs updating',
      explanation: 'Tracks, photos, or settings changed. Reload to see the latest data.',
    };
  }
  const operationalFailed =
    (input.processing?.indexers ?? []).reduce((count, index) => count + (index.operationalFailed ?? 0), 0) +
    (input.processing?.jobs ?? []).reduce((count, job) => count + (job.failed ?? 0), 0);
  if (operationalFailed > 0) {
    return {
      ...common,
      kind: 'unavailable',
      detailsSection: 'processing',
      label: 'Processing needs attention',
      explanation: 'Some processing operations failed. Review Processing details and recovery actions.',
    };
  }
  if (failedFiles > 0) {
    return {
      ...common,
      kind: 'attention',
      label: `${failedFiles} ${failedFiles === 1 ? 'file needs' : 'files need'} review`,
      explanation:
        'Your map is up to date. Some files could not be imported or have GPS findings. Dismiss hides this notice until new issues appear.',
    };
  }
  return null;
}
