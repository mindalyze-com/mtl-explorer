# Packet: SGN_02

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: SGN_02
- In scope: Valid credentials sign-in reaches the map.
- Out of scope: Other coverage IDs except where noted as shared state/evidence.

## Prerequisites

- Required previous coverage IDs or run packets: Signed-out login screen available; README quick-start credentials mtl/change-me.
- Required app/data state: Current run-state and packet set for this run.
- Required browser context: As specified by the coverage action.

## Allowed Mutations

- Allowed: Use documented credentials in browser and update packet/run-state.
- Not allowed: Collapse this ID into a parent row or skip required direct evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SGN_02 | Filled Username mtl and Password change-me on the login form, submitted Sign In, and waited for the app map. | Valid credentials authenticate and land on the map. | Login succeeded; the URL returned to /mtl/ and the map shell loaded with 11 Tracks plus Stats/Filter/Map navigation. | PASS | [assets/SGN_02-valid-login-map.webp](../assets/SGN_02-valid-login-map.webp); [assets/SGN_02-valid-login-map.txt](../assets/SGN_02-valid-login-map.txt); [assets/SGN-summary.txt](../assets/SGN-summary.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|


## Evidence Files

| File | Purpose |
|---|---|
| [assets/SGN_02-valid-login-map.webp](../assets/SGN_02-valid-login-map.webp) | Screenshot evidence |
| [assets/SGN_02-valid-login-map.txt](../assets/SGN_02-valid-login-map.txt) | Text/log evidence |
| [assets/SGN-summary.txt](../assets/SGN-summary.txt) | Text/log evidence |

## Screenshot Evidence

![assets/SGN_02-valid-login-map.webp](../assets/SGN_02-valid-login-map.webp)

## Timings

| Step | Timing |
|---|---:|
| Browser valid-login check | 6 seconds |

## Handoff Notes

- Completed: This coverage ID is terminal.
- Remaining unfinished coverage: Continue with the next queue row.
- Blocked or not applicable: none.
- State left for the next packet: unchanged.
