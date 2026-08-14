import { parseNumericBucket } from '@/utils/filterMetadata';
import { VIZ_SLATE_COLOR } from '@/utils/visualizationColors';

export const VISIBLE_NUMERIC_GRADIENT_BAND_COUNT = 8;

export type NumericGroupEntry = {
  group: string;
  count: number;
};

export type NumericGradientBand<T extends NumericGroupEntry> = {
  key: string;
  start: number;
  end: number;
  label: string;
  title: string;
  count: number;
  entries: T[];
};

export function hasOnlyNumericBuckets(entries: NumericGroupEntry[]): boolean {
  return entries.length > 0 && entries.every((entry) => parseNumericBucket(entry.group) != null);
}

export function buildNumericGradientBands<T extends NumericGroupEntry>(
  entries: T[],
  bucketCount = 100
): NumericGradientBand<T>[] {
  const effectiveBucketCount = Math.max(1, bucketCount);
  const bandCount = Math.min(VISIBLE_NUMERIC_GRADIENT_BAND_COUNT, effectiveBucketCount);
  const entriesByBucket = new Map<number, T[]>();
  for (const entry of entries) {
    const bucket = parseNumericBucket(entry.group);
    if (bucket == null) continue;
    const bucketEntries = entriesByBucket.get(bucket) ?? [];
    bucketEntries.push(entry);
    entriesByBucket.set(bucket, bucketEntries);
  }

  const bands: NumericGradientBand<T>[] = [];
  for (let index = 0; index < bandCount; index += 1) {
    const start = Math.floor((index * effectiveBucketCount) / bandCount);
    const end = Math.max(start, Math.floor(((index + 1) * effectiveBucketCount) / bandCount) - 1);
    const bandEntries = Array.from(entriesByBucket.entries())
      .filter(([bucket]) => bucket >= start && bucket <= end)
      .flatMap(([, bucketEntries]) => bucketEntries);

    bands.push({
      key: `${start}-${end}`,
      start,
      end,
      label: `${formatNumericBucket(start, effectiveBucketCount)}-${formatNumericBucket(end, effectiveBucketCount)}`,
      title: `${formatNumericBucket(start, effectiveBucketCount)}-${formatNumericBucket(end, effectiveBucketCount)} percentile range`,
      count: bandEntries.reduce((sum, entry) => sum + entry.count, 0),
      entries: bandEntries,
    });
  }
  return bands;
}

export function colorForNumericBucket(bucket: number, bucketCount: number, colors: string[]): string {
  if (colors.length === 0) return VIZ_SLATE_COLOR;
  if (colors.length === 1 || bucketCount <= 1) return colors[0];
  const maxBucket = bucketCount - 1;
  const clampedBucket = Math.max(0, Math.min(maxBucket, bucket));
  const colorIndex = Math.round((clampedBucket / maxBucket) * (colors.length - 1));
  return colors[colorIndex] ?? colors[colors.length - 1];
}

function formatNumericBucket(bucket: number, bucketCount: number): string {
  return String(bucket).padStart(String(Math.max(bucketCount - 1, 0)).length, '0');
}
