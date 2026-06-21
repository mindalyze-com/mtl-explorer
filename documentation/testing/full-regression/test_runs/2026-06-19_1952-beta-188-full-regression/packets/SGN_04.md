# Packet: SGN_04

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: SGN_04
- In scope: Determine whether demo-mode login banner coverage applies.
- Out of scope: Enabling demo mode or changing server configuration.

## Prerequisites

- Required previous coverage IDs or run packets: SGN_03.
- Required app/data state: browser signed out at login.
- Required browser context: desktop browser.

## Allowed Mutations

- Allowed: Read demo status.
- Not allowed: Enable demo mode.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SGN_04 | Queried `/mtl/api/auth/demo-status` and reviewed current login-screen context. | If demo mode is active, login screen shows demo credentials banner. | Demo status returned `demoMode:false` with empty username/password. Demo banner path does not apply to this run. | NOT APPLICABLE | [assets/SGN_04-demo-status.txt](../assets/SGN_04-demo-status.txt); [assets/SGN_03-invalid-login.webp](../assets/SGN_03-invalid-login.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SGN_04-demo-status.txt](../assets/SGN_04-demo-status.txt) | Demo status API response. |
| [assets/SGN_03-invalid-login.webp](../assets/SGN_03-invalid-login.webp) | Login screen context from immediately preceding signed-out check. |

## Screenshot Evidence

![Login screen context](../assets/SGN_03-invalid-login.webp)

## Timings

| Step | Timing |
|---|---:|
| Demo status check | <1 min |

## Handoff Notes

- Completed: SGN_04.
- Remaining unfinished coverage: SGN_05 onward.
- Blocked or not applicable: SGN_04 is not applicable because demo mode is disabled.
- State left for the next packet: Browser is signed out at `/mtl/login`.
