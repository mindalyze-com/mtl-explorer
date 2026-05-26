# Packet: MOB_02

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MOB_02
- In scope: Mobile bottom sheet and navigation sheet open/close plus drag/snap verification.
- Out of scope: Planner-specific touch gestures, covered by MOB_04.

## Prerequisites

- Required previous coverage IDs or run packets: MOB_01
- Required app/data state: Signed in; mobile viewport active.
- Required browser context: Narrow viewport; no touch capability available.

## Allowed Mutations

- Allowed: Open/close tools and attempt sheet drags.
- Not allowed: Change server data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MOB_02 | Opened Filter sheet, measured it, attempted drag down/up gestures, closed it, and attempted navigation-sheet drags. | Bottom sheets and navigation sheet drag, snap, and close correctly. | Filter sheet opened and closed at mobile width. Pointer drag attempts did not change sheet/nav positions, and touch drag/snap cannot be verified because the runtime lacks touch input. | BLOCKED | `assets/MOB_02-mobile-sheets.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/MOB_02-mobile-sheets.txt | Mobile sheet measurements, close behavior, and touch constraint. |

## Timings

| Step | Timing |
|---|---:|
| Open, close, and drag attempts | <3 min |

## Handoff Notes

- Completed: MOB_02 terminal `BLOCKED`.
- Remaining unfinished coverage: MOB_03 through RUN_CLEANUP.
- Blocked or not applicable: Touch drag/snap verification is blocked by tooling.
- State left for the next packet: Mobile viewport active; Filter sheet closed.
