# Packet: MOB_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MOB_01
- In scope: Narrow mobile viewport with touch input enabled.
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
| MOB_01 | Loaded MTL Explorer in a 390x844 mobile Chromium context with hasTouch=true and deviceScaleFactor=2, then captured the mobile map/navigation layout. | The app is usable at narrow mobile width with touch input enabled. | Mobile context reported 390x844 viewport, DPR 2, maxTouchPoints 1, canvas present, and mobile nav-sheet tools visible for Stats, Filter, Planner, Map, Animate, Segments, GPS, and Admin. | PASS | [assets/MOB_01-mobile-map.webp](../assets/MOB_01-mobile-map.webp); [assets/MOB_mobile-results.txt](../assets/MOB_mobile-results.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|


## Evidence Files

| File | Purpose |
|---|---|
| [assets/MOB_01-mobile-map.webp](../assets/MOB_01-mobile-map.webp) | Screenshot evidence |
| [assets/MOB_mobile-results.txt](../assets/MOB_mobile-results.txt) | Text/log evidence |

## Screenshot Evidence

![assets/MOB_01-mobile-map.webp](../assets/MOB_01-mobile-map.webp)

## Timings

| Step | Timing |
|---|---:|
| Mobile load | ~15 seconds |

## Handoff Notes

- Completed: This coverage ID is terminal.
- Remaining unfinished coverage: Continue with the next queue row.
- Blocked or not applicable: none.
- State left for the next packet: unchanged.
