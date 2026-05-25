const TRACK_ID_SEPARATOR_PATTERN = /[\s,;]+/;

export function parseTrackIdText(value: string | null | undefined): number[] {
  if (!value) return [];
  const seen = new Set<number>();
  for (const token of value.split(TRACK_ID_SEPARATOR_PATTERN)) {
    const trimmed = token.trim();
    if (!/^\d+$/.test(trimmed)) continue;
    const trackId = Number(trimmed);
    if (!Number.isSafeInteger(trackId) || trackId <= 0) continue;
    seen.add(trackId);
  }
  return [...seen].sort((a, b) => a - b);
}

export function formatTrackIds(trackIds: Iterable<number>): string {
  return [...new Set([...trackIds].filter((trackId) => Number.isSafeInteger(trackId) && trackId > 0))]
    .sort((a, b) => a - b)
    .join(',');
}

export function addTrackIdToText(value: string | null | undefined, trackId: number): string {
  return formatTrackIds([...parseTrackIdText(value), trackId]);
}

export function removeTrackIdFromText(value: string | null | undefined, trackId: number): string {
  return formatTrackIds(parseTrackIdText(value).filter((id) => id !== trackId));
}
