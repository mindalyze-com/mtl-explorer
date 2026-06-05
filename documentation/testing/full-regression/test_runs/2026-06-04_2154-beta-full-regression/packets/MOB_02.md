# Packet: MOB_02

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MOB_02
- In scope: Mobile bottom-sheet drag, snap, and close behavior.
- Out of scope: Other coverage IDs except where noted as shared state/evidence.

## Prerequisites

- Required previous coverage IDs or run packets: Previous queue rows terminal or explicitly not required.
- Required app/data state: Current run-state and packet set for this run.
- Required browser context: As specified by the coverage action.

## Allowed Mutations

- Allowed: Read-only verification and packet/run-state updates.
- Not allowed: Collapse this ID into a parent row or skip required direct evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MOB_02 | Opened Stats via a touch tap, used touch events on the sheet drag zone to expand the sheet, then used the close control. | Bottom sheets drag/snap and close correctly on mobile. | Stats sheet opened at about 506px height, expanded to about 743px after drag, and closed back to zero open sheets while the map canvas remained visible. | PASS | [assets/MOB_02-sheet-drag-snap.webp](../assets/MOB_02-sheet-drag-snap.webp); [assets/MOB_mobile-results.txt](../assets/MOB_mobile-results.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|


## Evidence Files

| File | Purpose |
|---|---|
| [assets/MOB_02-sheet-drag-snap.webp](../assets/MOB_02-sheet-drag-snap.webp) | Screenshot evidence |
| [assets/MOB_mobile-results.txt](../assets/MOB_mobile-results.txt) | Text/log evidence |

## Screenshot Evidence

![assets/MOB_02-sheet-drag-snap.webp](../assets/MOB_02-sheet-drag-snap.webp)

## Timings

| Step | Timing |
|---|---:|
| Sheet drag/close | ~25 seconds |

## Handoff Notes

- Completed: This coverage ID is terminal.
- Remaining unfinished coverage: Continue with the next queue row.
- Blocked or not applicable: none.
- State left for the next packet: unchanged.
