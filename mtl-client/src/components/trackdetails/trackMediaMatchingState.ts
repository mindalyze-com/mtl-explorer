import type { ProcessingStatusDto } from 'x8ing-mtl-api-typescript-fetch';

export type TrackMediaMatchingState = 'checking' | 'pending' | 'complete' | 'unavailable';

/** Empty track results are final only after the import and matching queues settle. */
export function trackMediaMatchingState(
  processing: ProcessingStatusDto | null,
  healthy: boolean
): TrackMediaMatchingState {
  if (!healthy) return 'unavailable';
  if (!processing) return 'checking';
  if (
    processing.indexers?.some(
      (indexer) =>
        ['GPS', 'MEDIA'].includes(indexer.index ?? '') && (indexer.discoveryInProgress || (indexer.pending ?? 0) > 0)
    )
  )
    return 'pending';
  const matching = processing.jobs?.find((job) => job.job === 'mediaCorrelation');
  if (!matching || matching.state === 'DISABLED') return 'unavailable';
  return (matching.pending ?? 0) > 0 || ['RUNNING', 'QUEUED', 'WAITING', 'RETRYING'].includes(matching.state ?? '')
    ? 'pending'
    : 'complete';
}
