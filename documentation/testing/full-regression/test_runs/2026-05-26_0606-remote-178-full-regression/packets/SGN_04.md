# Packet: SGN_04

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: SGN_04
- In scope: demo-credentials banner applicability.
- Out of scope: normal login behavior.

## Prerequisites

- Required previous coverage IDs or run packets: SGN_01.
- Required app/data state: app running.
- Required browser context: unsigned desktop browser context.

## Allowed Mutations

- Allowed: read demo status and open login screen.
- Not allowed: change demo configuration.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SGN_04 | Checked `/api/auth/demo-status` and the login screen. | If demo mode is active, the login screen shows demo credentials. | Demo mode is not active (`"demoMode": false`), so a demo credentials banner is not applicable to this quick-install run. | NOT APPLICABLE | `assets/SGN_04-demo-status.txt`, `assets/SGN_04-login-no-demo-banner.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/SGN_04-demo-status.txt | Demo mode API response. |
| assets/SGN_04-login-no-demo-banner.webp | Login screen in non-demo mode. |

## Timings

| Step | Timing |
|---|---:|
| Demo status check | <1s |

## Handoff Notes

- Completed: SGN_04 terminal NOT APPLICABLE.
- Remaining unfinished coverage: continue with SGN_05.
- Blocked or not applicable: demo mode is disabled in this run.
- State left for the next packet: no server data changed.
