# Packet: TBS_13

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: TBS_13.
- In scope: Statistics Overview filter-summary activation with pointer and keyboard on desktop and mobile.
- Out of scope: Planner.

## Prerequisites

- Required previous coverage IDs or run packets: TBS_12.
- Required app/data state: exact WALKING filter showing one of twelve tracks.
- Required browser context: Statistics Overview at desktop and 390×844.

## Allowed Mutations

- Allowed: activate summary, change viewport, run a native-button keyboard control check, restore filter and viewport.
- Not allowed: infer an app defect when the harness cannot activate control buttons.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| TBS_13 | Activated Showing 1 of 12 with pointer on desktop/mobile; attempted keyboard activation and a native tab control check. | Pointer and keyboard both open Filter directly on both widths. | Pointer opened the correct Filter on both widths. Keyboard could not be evaluated: every supported key path also failed the native Trends control check. | BLOCKED | [activation log](../assets/TBS_13-summary-activation.txt), [mobile summary](../assets/TBS_13-mobile-summary.webp) |

## Issues

No product issue assigned because the keyboard control path was blocked by the browser harness.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TBS_13-summary-activation.txt](../assets/TBS_13-summary-activation.txt) | Pointer results, accessible state, keyboard attempts, control check, and cleanup. |
| [assets/TBS_13-mobile-summary.webp](../assets/TBS_13-mobile-summary.webp) | Narrow Overview summary after the blocked keyboard attempt. |

## Screenshot Evidence

The compact WebP shows the correctly sized mobile summary; pointer behavior is recorded exactly.

## Timings

| Step | Timing |
|---|---:|
| Pointer open | < 1 s each |
| Each keyboard attempt | < 1 s |

## Handoff Notes

- Completed: TBS_13 is terminal `BLOCKED`; pointer passed, keyboard attribution was impossible with this browser surface.
- Remaining unfinished coverage: PLN_01 onward.
- Blocked or not applicable: keyboard activation only.
- State left for the next packet: desktop Filter open; All categories; twelve tracks; viewport restored.
