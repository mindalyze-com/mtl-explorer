export function finiteNumberOrNull(value: unknown): number | null {
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : null;
}

export function clamp01(value: number): number {
  if (!Number.isFinite(value)) return 0;
  return Math.max(0, Math.min(1, value));
}

export function lerpNumber(from: number, to: number, progress: number): number {
  return from + (to - from) * progress;
}

export function interpolationProgress(from: number, to: number, value: number): number {
  const span = Math.max(to - from, Number.EPSILON);
  return clamp01((value - from) / span);
}

export function interpolateNullableNumber(from: number | null, to: number | null, progress: number): number | null {
  if (from == null && to == null) return null;
  if (from == null) return to;
  if (to == null) return from;
  return lerpNumber(from, to, progress);
}

export function maxFiniteOrNull(values: Array<number | null | undefined>): number | null {
  let max: number | null = null;
  for (const value of values) {
    const numeric = finiteNumberOrNull(value);
    if (numeric == null) continue;
    max = max == null ? numeric : Math.max(max, numeric);
  }
  return max;
}
