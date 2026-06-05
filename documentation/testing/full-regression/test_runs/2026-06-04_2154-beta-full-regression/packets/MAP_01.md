# Packet: MAP_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MAP_01
- In scope: Base map and overlays load on first open.
- Out of scope: Other coverage IDs except where noted as shared state/evidence.

## Prerequisites

- Required previous coverage IDs or run packets: Authenticated browser context and imported dataset available.
- Required app/data state: Current run-state and packet set for this run.
- Required browser context: As specified by the coverage action.

## Allowed Mutations

- Allowed: Use existing first-open/current map evidence and update packet/run-state.
- Not allowed: Collapse this ID into a parent row or skip required direct evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_01 | Reviewed first-open and current map screenshots plus UI text after login/imports. | Base map and overlays load on first open. | The first authenticated open and current map both rendered the basemap with attribution, navigation controls, app overlays, and track overlays; no blank map state was observed. | PASS | [assets/SGN_02-valid-login-map.webp](../assets/SGN_02-valid-login-map.webp); [assets/SGN_02-valid-login-map.txt](../assets/SGN_02-valid-login-map.txt); [assets/FMT_02-map-formats.webp](../assets/FMT_02-map-formats.webp); [assets/FMT_02-map-formats.txt](../assets/FMT_02-map-formats.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|


## Evidence Files

| File | Purpose |
|---|---|
| [assets/SGN_02-valid-login-map.webp](../assets/SGN_02-valid-login-map.webp) | Screenshot evidence |
| [assets/SGN_02-valid-login-map.txt](../assets/SGN_02-valid-login-map.txt) | Text/log evidence |
| [assets/FMT_02-map-formats.webp](../assets/FMT_02-map-formats.webp) | Screenshot evidence |
| [assets/FMT_02-map-formats.txt](../assets/FMT_02-map-formats.txt) | Text/log evidence |

## Screenshot Evidence

![assets/SGN_02-valid-login-map.webp](../assets/SGN_02-valid-login-map.webp)
![assets/FMT_02-map-formats.webp](../assets/FMT_02-map-formats.webp)

## Timings

| Step | Timing |
|---|---:|
| Evidence review | <1 minute |

## Handoff Notes

- Completed: This coverage ID is terminal.
- Remaining unfinished coverage: Continue with the next queue row.
- Blocked or not applicable: none.
- State left for the next packet: unchanged.
