export function lowerBoundClampedIndex<T>(items: readonly T[], target: number, valueOf: (item: T) => number): number {
  return boundClampedIndex(items, target, valueOf, false);
}

export function upperBoundClampedIndex<T>(items: readonly T[], target: number, valueOf: (item: T) => number): number {
  return boundClampedIndex(items, target, valueOf, true);
}

function boundClampedIndex<T>(
  items: readonly T[],
  target: number,
  valueOf: (item: T) => number,
  includeEqual: boolean
): number {
  let low = 0;
  let high = items.length - 1;
  while (low < high) {
    const middle = (low + high) >>> 1;
    const value = valueOf(items[middle]);
    if (value < target || (includeEqual && value === target)) low = middle + 1;
    else high = middle;
  }
  return low;
}

export function nearestSortedIndex<T>(
  items: readonly T[],
  target: number,
  valueOf: (item: T) => number,
  preferEarlierOnTie = false
): number {
  let index = lowerBoundClampedIndex(items, target, valueOf);
  if (index === 0) return index;

  const previousDistance = Math.abs(valueOf(items[index - 1]) - target);
  const currentDistance = Math.abs(valueOf(items[index]) - target);
  if (previousDistance < currentDistance || (preferEarlierOnTie && previousDistance === currentDistance)) {
    index -= 1;
  }
  return index;
}
