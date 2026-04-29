export const OVERVIEW_PRECISION = 1000;
export const BACKGROUND_TRACK_PRECISION = 10;
export const DETAIL_TRACK_PRECISION = 1;
export const DEGENERATE_TRACK_LENGTH_THRESHOLD_METERS = 50;

export const CACHEABLE_TRACK_PRECISIONS = [OVERVIEW_PRECISION, BACKGROUND_TRACK_PRECISION] as const;

const DEFAULT_TRACK_LOAD_BATCH_SIZE = 1000;
// Lower concurrency makes the GUI progress better reflect batch completion than 4 parallel requests.
const DEFAULT_TRACK_LOAD_BATCH_CONCURRENCY = 2;
const MIN_TRACK_LOAD_BATCH_SIZE = 1;

function parsePositiveInteger(value: string | undefined, fallback: number): number {
  if (!value) return fallback;
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed >= MIN_TRACK_LOAD_BATCH_SIZE ? parsed : fallback;
}

export const TRACK_LOAD_BATCH_SIZE = parsePositiveInteger(
  import.meta.env.VITE_TRACK_LOAD_BATCH_SIZE,
  DEFAULT_TRACK_LOAD_BATCH_SIZE
);

export const TRACK_LOAD_BATCH_CONCURRENCY = parsePositiveInteger(
  import.meta.env.VITE_TRACK_LOAD_BATCH_CONCURRENCY,
  DEFAULT_TRACK_LOAD_BATCH_CONCURRENCY
);

export function isCacheableTrackPrecision(precision: number): boolean {
  return CACHEABLE_TRACK_PRECISIONS.includes(precision as (typeof CACHEABLE_TRACK_PRECISIONS)[number]);
}
