export const SHEET_FIT_MAX_EXTRA_PX = 80;
export const SHEET_FIT_VIEWPORT_FRACTION = 0.08;
export const SHEET_FIT_VIEWPORT_CEILING = 0.95;
export const SHEET_OVERFLOW_TOLERANCE_PX = 4;
export const SHEET_FIT_TARGET_ATTRIBUTE = 'data-sheet-fit-target';
export const SHEET_CONTENT_KEY_ATTRIBUTE = 'data-sheet-content-key';

/** A fit must remove the entire overflow, within the original opening allowance. */
export function boundedSheetHeight(baseline: number, current: number, overflow: number, viewport: number): number {
  if (overflow <= SHEET_OVERFLOW_TOLERANCE_PX) return current;
  const ideal = Math.ceil(current + overflow);
  const ceiling = Math.min(
    baseline + Math.min(SHEET_FIT_MAX_EXTRA_PX, viewport * SHEET_FIT_VIEWPORT_FRACTION),
    viewport * SHEET_FIT_VIEWPORT_CEILING
  );
  return ideal <= ceiling ? ideal : current;
}

/** Explicit targets survive the point where overflow disappears after fitting.
 * Nested targets take precedence over their ancestors; hidden cached sections do not participate.
 */
export function sheetFitTargets(body: HTMLElement): HTMLElement[] {
  const targets = Array.from(body.querySelectorAll<HTMLElement>(`[${SHEET_FIT_TARGET_ATTRIBUTE}]`)).filter(
    (element) => element.clientHeight > 0
  );
  const leaves = targets.filter((element) => !targets.some((other) => other !== element && element.contains(other)));
  return leaves.length ? leaves : [body];
}

/** Keep fixed chrome in place: only the shortage in the content viewport is added. */
export function sheetContentOverflow(body: HTMLElement): number {
  return Math.max(
    ...sheetFitTargets(body).map((target) => {
      let shortage = 0;
      for (let element: HTMLElement | null = target; element; element = element.parentElement) {
        const overflowY = window.getComputedStyle(element).overflowY;
        if (element === target || element === body || overflowY === 'auto' || overflowY === 'scroll') {
          shortage += Math.max(0, element.scrollHeight - element.clientHeight);
        }
        if (element === body) break;
      }
      return shortage;
    })
  );
}
