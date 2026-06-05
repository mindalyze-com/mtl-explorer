# Packet: SGN_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: SGN_01
- In scope: Signed-out first-load redirect to the login screen.
- Out of scope: Other coverage IDs except where noted as shared state/evidence.

## Prerequisites

- Required previous coverage IDs or run packets: App reachable; clean browser context without saved auth.
- Required app/data state: Current run-state and packet set for this run.
- Required browser context: As specified by the coverage action.

## Allowed Mutations

- Allowed: Read-only browser navigation and packet/run-state updates.
- Not allowed: Collapse this ID into a parent row or skip required direct evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SGN_01 | Opened /mtl/ in a clean context with no auth state. | The app redirects to the login screen while signed out. | The browser landed on http://167.233.16.201:18080/mtl/login. DOM inspection found one Username field, one Password field, and one Sign In button. | PASS | [assets/SGN_01-signed-out-login.webp](../assets/SGN_01-signed-out-login.webp); [assets/SGN_01-signed-out-login.txt](../assets/SGN_01-signed-out-login.txt); [assets/SGN-login-control-summary.txt](../assets/SGN-login-control-summary.txt); [assets/SGN-summary.txt](../assets/SGN-summary.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|


## Evidence Files

| File | Purpose |
|---|---|
| [assets/SGN_01-signed-out-login.webp](../assets/SGN_01-signed-out-login.webp) | Screenshot evidence |
| [assets/SGN_01-signed-out-login.txt](../assets/SGN_01-signed-out-login.txt) | Text/log evidence |
| [assets/SGN-login-control-summary.txt](../assets/SGN-login-control-summary.txt) | Text/log evidence |
| [assets/SGN-summary.txt](../assets/SGN-summary.txt) | Text/log evidence |

## Screenshot Evidence

![assets/SGN_01-signed-out-login.webp](../assets/SGN_01-signed-out-login.webp)

## Timings

| Step | Timing |
|---|---:|
| Browser redirect check | 1 second |

## Handoff Notes

- Completed: This coverage ID is terminal.
- Remaining unfinished coverage: Continue with the next queue row.
- Blocked or not applicable: none.
- State left for the next packet: unchanged.
