# Packet: SGN_03

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: SGN_03
- In scope: invalid credential handling on the login screen.
- Out of scope: valid login and logout.

## Prerequisites

- Required previous coverage IDs or run packets: SGN_01.
- Required app/data state: app running.
- Required browser context: fresh unsigned desktop browser context.

## Allowed Mutations

- Allowed: submit a deliberately wrong password.
- Not allowed: modify app data or server configuration.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SGN_03 | Submitted username `mtl` with an invalid password. | A clear error appears and the browser remains on the login screen. | The page stayed at `/mtl/login` and displayed `Invalid username or password.` | PASS | `assets/SGN_03-wrong-credentials.webp`, `assets/SGN_03-wrong-credentials.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/SGN_03-wrong-credentials.webp | Wrong-password error on login screen. |
| assets/SGN_03-wrong-credentials.txt | URL and visible text summary. |

## Timings

| Step | Timing |
|---|---:|
| Wrong-password response | 2.5s |

## Handoff Notes

- Completed: SGN_03 terminal PASS.
- Remaining unfinished coverage: continue with SGN_04.
- Blocked or not applicable: none.
- State left for the next packet: no server data changed.
