export const MEDIA_PAGE_BUFFER_PAGE_COUNT = 3;

export type MediaPageBuffer<T> = {
  items: T[];
  offset: number;
};

export function mergeAdjacentMediaPage<T>(
  currentItems: T[],
  currentOffset: number,
  incomingItems: T[],
  incomingOffset: number,
  pageSize: number,
  direction: -1 | 1
): MediaPageBuffer<T> {
  if (!currentItems.length || pageSize <= 0) return { items: incomingItems, offset: incomingOffset };

  const combinedOffset = Math.min(currentOffset, incomingOffset);
  const combinedEnd = Math.max(currentOffset + currentItems.length, incomingOffset + incomingItems.length);
  const hasGap =
    direction > 0
      ? incomingOffset > currentOffset + currentItems.length
      : incomingOffset + incomingItems.length < currentOffset;
  if (hasGap) return { items: incomingItems, offset: incomingOffset };

  const combined: Array<T | undefined> = Array.from({ length: combinedEnd - combinedOffset });
  currentItems.forEach((item, index) => {
    combined[currentOffset - combinedOffset + index] = item;
  });
  incomingItems.forEach((item, index) => {
    combined[incomingOffset - combinedOffset + index] = item;
  });
  if (combined.some((item) => item === undefined)) return { items: incomingItems, offset: incomingOffset };

  const items = combined as T[];
  const maxItems = pageSize * MEDIA_PAGE_BUFFER_PAGE_COUNT;
  if (items.length <= maxItems) return { items, offset: combinedOffset };
  if (direction > 0) {
    const trimCount = items.length - maxItems;
    return { items: items.slice(trimCount), offset: combinedOffset + trimCount };
  }
  return { items: items.slice(0, maxItems), offset: combinedOffset };
}
