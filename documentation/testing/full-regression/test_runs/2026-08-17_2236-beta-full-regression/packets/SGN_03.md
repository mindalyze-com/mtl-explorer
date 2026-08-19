# Packet: SGN_03

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: SGN_03
- In scope: Invalid password behavior.
- Out of scope: Account lockout policy.

## Prerequisites

- Required previous coverage IDs or run packets: SGN_02.
- Required app/data state: Signed out; login page available.
- Required browser context: Main login tab.

## Allowed Mutations

- Allowed: Submit one deliberately invalid login.
- Not allowed: Persist invalid credentials.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SGN_03 | Submitted the valid username with a wrong password. | Clear error and remain on login. | Alert said `Invalid username or password.` and URL remained `/mtl/login` with the login controls. | PASS | [assets/SGN_03-invalid-login.txt](../assets/SGN_03-invalid-login.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SGN_03-invalid-login.txt](../assets/SGN_03-invalid-login.txt) | Invalid-login alert and final route. |

## Screenshot Evidence

Blocked by ACC_04; direct alert DOM and URL evidence is recorded.

## Timings

| Step | Timing |
|---|---:|
| Sign out and invalid-login response | About 8 s |

## Handoff Notes

- Completed: Wrong credentials show a clear error without leaving login.
- Remaining unfinished coverage: None for SGN_03.
- Blocked or not applicable: Screenshot capture blocked under ACC_04.
- State left for the next packet: Signed out on login with invalid-login alert.
