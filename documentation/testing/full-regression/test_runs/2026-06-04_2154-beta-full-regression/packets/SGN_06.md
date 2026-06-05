# Packet: SGN_06

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: SGN_06
- In scope: Splash screen appears during startup and disappears after map/tracks load.
- Out of scope: Other coverage IDs except where noted as shared state/evidence.

## Prerequisites

- Required previous coverage IDs or run packets: Authenticated context available.
- Required app/data state: Current run-state and packet set for this run.
- Required browser context: As specified by the coverage action.

## Allowed Mutations

- Allowed: Delay one startup API response in browser context, capture splash and loaded states, update packet/run-state.
- Not allowed: Collapse this ID into a parent row or skip required direct evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SGN_06 | Loaded the app in an authenticated context while delaying the tracks API, captured the early startup view, then waited for normal map load. | The splash screen with logo/background/message displays during startup and disappears once map and tracks load. | Early startup showed the MTL loading view with LOADING YOUR TRAILS; after loading completed, the map displayed 11 Tracks and the loading message was gone. | PASS | [assets/SGN_06-splash-start.webp](../assets/SGN_06-splash-start.webp); [assets/SGN_06-splash-start.txt](../assets/SGN_06-splash-start.txt); [assets/SGN_06-splash-gone-map-loaded.webp](../assets/SGN_06-splash-gone-map-loaded.webp); [assets/SGN_06-splash-gone-map-loaded.txt](../assets/SGN_06-splash-gone-map-loaded.txt); [assets/SGN-summary.txt](../assets/SGN-summary.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|


## Evidence Files

| File | Purpose |
|---|---|
| [assets/SGN_06-splash-start.webp](../assets/SGN_06-splash-start.webp) | Screenshot evidence |
| [assets/SGN_06-splash-start.txt](../assets/SGN_06-splash-start.txt) | Text/log evidence |
| [assets/SGN_06-splash-gone-map-loaded.webp](../assets/SGN_06-splash-gone-map-loaded.webp) | Screenshot evidence |
| [assets/SGN_06-splash-gone-map-loaded.txt](../assets/SGN_06-splash-gone-map-loaded.txt) | Text/log evidence |
| [assets/SGN-summary.txt](../assets/SGN-summary.txt) | Text/log evidence |

## Screenshot Evidence

![assets/SGN_06-splash-start.webp](../assets/SGN_06-splash-start.webp)
![assets/SGN_06-splash-gone-map-loaded.webp](../assets/SGN_06-splash-gone-map-loaded.webp)

## Timings

| Step | Timing |
|---|---:|
| Browser splash/load check | 7 seconds |

## Handoff Notes

- Completed: This coverage ID is terminal.
- Remaining unfinished coverage: Continue with the next queue row.
- Blocked or not applicable: none.
- State left for the next packet: unchanged.
