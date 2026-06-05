export function shouldReplaceElevationRefreshTimer(
  currentDelayMs: number | null | undefined,
  nextDelayMs: number
): boolean {
  return currentDelayMs == null || nextDelayMs < currentDelayMs;
}
