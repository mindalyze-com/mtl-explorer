# Packet: SGN_03

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: SGN_03
- In scope: Submit invalid credentials and verify the error and remaining login state.
- Out of scope: Valid credential and sign-out flows, covered by SGN_02 and SGN_05.

## Prerequisites

- Required previous coverage IDs or run packets: SGN_02.
- Required app/data state: Server healthy; browser signed out through the visible Session action.
- Required browser context: Login page in the in-app browser.

## Allowed Mutations

- Allowed: Enter an intentionally invalid password and submit Sign In.
- Not allowed: Store credential values in run artifacts.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SGN_03 | Submit the normal username with an intentionally invalid password. | A clear error appears and the browser remains on login. | `Invalid username or password.` appeared in an alert after 1.081 s; the URL remained `/mtl/login`, and the settled page retained only the login controls. | PASS | [assets/SGN_03-invalid-login.txt](../assets/SGN_03-invalid-login.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SGN_03-invalid-login.txt](../assets/SGN_03-invalid-login.txt) | Invalid-login action, timing, alert text, URL, and settled state without credential values. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible alert and login state are recorded in linked evidence.

## Timings

| Step | Timing |
|---|---:|
| Submit to visible error | 1.081 s |

## Handoff Notes

- Completed: Invalid-credential error and remaining signed-out login state.
- Remaining unfinished coverage: None for SGN_03.
- Blocked or not applicable: None.
- State left for the next packet: Browser signed out at `/mtl/login`.
