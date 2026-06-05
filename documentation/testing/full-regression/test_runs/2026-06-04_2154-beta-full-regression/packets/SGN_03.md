# Packet: SGN_03

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: SGN_03
- In scope: Wrong credentials produce a clear error and keep the user on login.
- Out of scope: Other coverage IDs except where noted as shared state/evidence.

## Prerequisites

- Required previous coverage IDs or run packets: Login form available.
- Required app/data state: Current run-state and packet set for this run.
- Required browser context: As specified by the coverage action.

## Allowed Mutations

- Allowed: Submit invalid credentials in clean context and update packet/run-state.
- Not allowed: Collapse this ID into a parent row or skip required direct evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SGN_03 | Submitted username mtl with an invalid password. | A clear error appears and the app stays on the login screen. | The page stayed on /mtl/login and displayed Invalid username or password. | PASS | [assets/SGN_03-wrong-credentials-error.webp](../assets/SGN_03-wrong-credentials-error.webp); [assets/SGN_03-wrong-credentials-error.txt](../assets/SGN_03-wrong-credentials-error.txt); [assets/SGN-summary.txt](../assets/SGN-summary.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|


## Evidence Files

| File | Purpose |
|---|---|
| [assets/SGN_03-wrong-credentials-error.webp](../assets/SGN_03-wrong-credentials-error.webp) | Screenshot evidence |
| [assets/SGN_03-wrong-credentials-error.txt](../assets/SGN_03-wrong-credentials-error.txt) | Text/log evidence |
| [assets/SGN-summary.txt](../assets/SGN-summary.txt) | Text/log evidence |

## Screenshot Evidence

![assets/SGN_03-wrong-credentials-error.webp](../assets/SGN_03-wrong-credentials-error.webp)

## Timings

| Step | Timing |
|---|---:|
| Browser invalid-login check | 3 seconds |

## Handoff Notes

- Completed: This coverage ID is terminal.
- Remaining unfinished coverage: Continue with the next queue row.
- Blocked or not applicable: none.
- State left for the next packet: unchanged.
