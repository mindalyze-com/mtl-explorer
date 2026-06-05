# Packet: SGN_07

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: SGN_07
- In scope: Startup failure offers retry instead of leaving a frozen splash.
- Out of scope: Other coverage IDs except where noted as shared state/evidence.

## Prerequisites

- Required previous coverage IDs or run packets: Authenticated context available; browser route interception can safely simulate API failure without changing server state.
- Required app/data state: Current run-state and packet set for this run.
- Required browser context: As specified by the coverage action.

## Allowed Mutations

- Allowed: Fulfill startup API calls with synthetic 503 responses in the browser context and update packet/run-state.
- Not allowed: Collapse this ID into a parent row or skip required direct evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SGN_07 | Loaded the app with non-auth /mtl/api calls fulfilled as synthetic 503 failures. | On startup failure, the user sees a retry path instead of a frozen splash. | The app displayed Unable to load tracks -- no server connection and no cached data available, with a visible Retry action; the shell remained interactive rather than frozen. | PASS | [assets/SGN_07-startup-failure-retry.webp](../assets/SGN_07-startup-failure-retry.webp); [assets/SGN_07-startup-failure-retry.txt](../assets/SGN_07-startup-failure-retry.txt); [assets/SGN-summary.txt](../assets/SGN-summary.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|


## Evidence Files

| File | Purpose |
|---|---|
| [assets/SGN_07-startup-failure-retry.webp](../assets/SGN_07-startup-failure-retry.webp) | Screenshot evidence |
| [assets/SGN_07-startup-failure-retry.txt](../assets/SGN_07-startup-failure-retry.txt) | Text/log evidence |
| [assets/SGN-summary.txt](../assets/SGN-summary.txt) | Text/log evidence |

## Screenshot Evidence

![assets/SGN_07-startup-failure-retry.webp](../assets/SGN_07-startup-failure-retry.webp)

## Timings

| Step | Timing |
|---|---:|
| Browser startup-failure simulation | 5 seconds |

## Handoff Notes

- Completed: This coverage ID is terminal.
- Remaining unfinished coverage: Continue with the next queue row.
- Blocked or not applicable: none.
- State left for the next packet: unchanged.
