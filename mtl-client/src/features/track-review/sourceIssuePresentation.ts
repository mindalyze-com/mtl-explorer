import type { SourceIssueMutationResult } from 'x8ing-mtl-api-typescript-fetch';

export function plural(count: number, singular: string, pluralForm = `${singular}s`) {
  return `${count.toLocaleString()} ${count === 1 ? singular : pluralForm}`;
}

export function indexFileLabel(index: string | undefined, count: number) {
  return plural(count, index === 'GPS' ? 'track file' : index === 'MEDIA' ? 'media file' : 'file');
}

/** "2 track files · 1 media file" for per-index pending counts. */
export function issueBreakdown(entries: Array<{ index?: string; count?: number }>) {
  return entries
    .filter((entry) => (entry.count ?? 0) > 0)
    .map((entry) => indexFileLabel(entry.index, entry.count ?? 0))
    .join(' · ');
}

export function acknowledgementMessage(result: SourceIssueMutationResult | null) {
  if (!result) return 'Nothing left to acknowledge.';
  const saved = (result.newlyAcknowledged ?? 0) + (result.alreadyAcknowledged ?? 0);
  const stale = result.staleOrResolved ?? 0;
  if (!saved) return 'These issues changed or were resolved. Review the current issues.';
  return `Acknowledged ${plural(saved, 'issue')}.${stale ? ` ${plural(stale, 'changed issue')} left open.` : ''}`;
}
